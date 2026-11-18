package com.jayptucker.heroesevolved.hydro;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.combat.StyleMotion;
import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.flight.FlightSkillService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.HydroStatePayload;
import com.jayptucker.heroesevolved.network.SpeedCyclonePayload;
import com.jayptucker.heroesevolved.progression.EvolutionTrials;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.speed.TornadoWind;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hydrokinesis: water bent like the sea's own child bends it, and fought with like a waterbender.
 *
 * <p>A hydrokinetic carries water, seen circling their waist (the <b>reservoir</b>, 0 to 100). It fills in water and
 * rain and near open water, and dries away in the Nether. Every move spends some; with too little carried, the move pulls
 * its water thin out of the air instead: weaker, paler, and a little more tiring. Standing in water strengthens every
 * move, heals, and lets them breathe and swim like the sea's own.</p>
 *
 * <ul>
 *   <li>Water Whip: a lash cracked out along the aim; with Shift, it snares the first foe and hauls them in.</li>
 *   <li>Crashing Wave: a wave raised and rolled forward, carrying whatever it catches and putting out fire.</li>
 *   <li>Octopus Form: a shell of water with eight arms that lash at anything near and swallow shots; press again to
 *   throw every arm out at once.</li>
 *   <li>Wave Rider: surf a cresting wave across land and water; press again to leap off the crest.</li>
 *   <li>Maelstrom: a whirlpool opens where you aim and rises as a waterspout, steered by your look, that drags foes
 *   in and drowns them, then comes down as a Deluge.</li>
 * </ul>
 */
public final class HydroService {
    /** State flags sent to watchers so they draw what lasts as long as a state does. */
    public static final int OCTOPUS = 1, RIDING = 2, SPOUT = 4, LEAPING = 8, STORM = 16;
    private static final ResourceLocation RIDE_STEP = ResourceLocation.fromNamespaceAndPath("heroesevolved", "hydro_ride_step");

    /** What a move got when it drew its water: how hard it hits, and whether it was pulled thin from the air. */
    public record Draw(float power, boolean thin) {}

    static final class Tide {
        ServerPlayer player;
        float water;
        int synced = -100, syncedFlags = -1;
        long lastSync, spiritReady;
        Octopus octopus;
        Ride ride;
        Storm storm;
    }
    static final class Octopus { long start, until, nextLash; int level; }
    static final class Ride {
        long start, until, leapAt = -1;
        int level;
        double vy;
        boolean onWater;
        Vec3 dir;
        final Set<UUID> struck = new HashSet<>();
    }
    static final class Storm {
        Vec3 center;
        long start;
        int level;
        boolean thin;
        ResourceKey<Level> dimension;
        final Set<UUID> caught = new HashSet<>();
    }
    /** A wave rolling over the ground. */
    static final class Wave {
        ServerPlayer owner;
        ServerLevel level;
        Vec3 start, dir;
        long born;
        double halfWidth, height, reach;
        int travel;
        float damage;
        boolean riptide;
        Piece piece;
        final Set<UUID> caught = new HashSet<>();
    }

    private static final Map<UUID, Tide> TIDES = new HashMap<>();
    private static final List<Wave> WAVES = new ArrayList<>();
    private static int powerDamageDepth;

    private HydroService() {}

    // ------------------------------------------------------------------
    // Who bends, and the reservoir
    // ------------------------------------------------------------------

    public static boolean isHydrokinetic(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower()
                .filter(entry -> entry.getKey().equals(ModAbilities.HYDROKINESIS_ID) && entry.getValue().isUnlocked())
                .isPresent() && !EclipseService.arePowersSuppressed(player);
    }

    /** True while one of this power's own hits is landing, so passives that react to blows don't stack on top. */
    public static boolean isDealingPowerDamage() { return powerDamageDepth > 0; }

    static Tide tide(ServerPlayer player) {
        Tide tide = TIDES.computeIfAbsent(player.getUUID(), id -> new Tide());
        tide.player = player;
        return tide;
    }

    private static long now(ServerPlayer player) { return player.getServer().overworld().getGameTime(); }

    public static int water(ServerPlayer player) {
        Tide tide = TIDES.get(player.getUUID());
        return tide == null ? 0 : (int) tide.water;
    }

    public static int capacity(ServerPlayer player) {
        return SkillTreeService.has(player, "hydro_2_2") ? HydroGeometry.SKIN_WATER : HydroGeometry.WATER;
    }

    public static void fill(ServerPlayer player, float amount) {
        Tide tide = tide(player);
        float before = tide.water;
        tide.water = Math.clamp(tide.water + amount, 0, capacity(player));
        if (before < capacity(player) && tide.water >= capacity(player) && amount > 0 && amount < capacity(player))
            player.displayClientMessage(Component.translatable("hydro.heroesevolved.full").withStyle(ChatFormatting.AQUA), true);
    }

    /**
     * Takes a move's water. With too little carried, the rest is pulled thin from the air: the move is weaker, drawn
     * paler, and the effort costs a little stamina. Standing in water or rain strengthens it either way.
     */
    public static Draw draw(ServerPlayer player, int amount) {
        Tide tide = tide(player);
        float boost = boost(player);
        if (player.isInWaterOrBubble() || tide.water >= amount || com.jayptucker.heroesevolved.amplify.Amplification.surged(player)) {
            if (!player.isInWaterOrBubble() && !com.jayptucker.heroesevolved.amplify.Amplification.surged(player)) tide.water -= amount;
            return new Draw(boost, false);
        }
        tide.water = 0;
        PlayerEnergyService.consumeUpTo(player, Math.max(1, amount / 4));
        return new Draw(.7F * boost, true);
    }

    /** Health a second in water and in rain (doubled by Sea's Blessing). */
    static final float WATER_HEAL = .75F, RAIN_HEAL = .5F;
    /** Extra stamina recovery a second in water and in rain. */
    static final double WATER_STAMINA = 2, RAIN_STAMINA = 1;

    /** Water and rain feed a hydrokinetic's stamina, as a storm feeds an electrokinetic's. */
    public static double seaEnergyBonus(ServerPlayer player) {
        if (!isHydrokinetic(player) || com.jayptucker.heroesevolved.ability.service.Elements.dried(player)) return 0;
        return player.isInWaterOrBubble() ? WATER_STAMINA : player.isInWaterRainOrBubble() ? RAIN_STAMINA : 0;
    }

    /** The sea strengthens its own: a quarter more in water, a tenth more in rain. */
    public static float boost(ServerPlayer player) {
        return player.isInWaterOrBubble() || com.jayptucker.heroesevolved.amplify.Amplification.surged(player) ? 1.25F : player.isInWaterRainOrBubble() ? 1.1F : 1;
    }

    /** Water needs the power to answer and somewhere it can exist: the Nether boils it all away. */
    public static boolean canBend(ServerPlayer player) {
        return PowerRules.canAct(player) && !com.jayptucker.heroesevolved.ability.service.Elements.dried(player);
    }

    public static boolean canStartCast(ServerPlayer player) {
        return canBend(player) && !storming(player);
    }

    public static boolean octopus(ServerPlayer player) {
        Tide tide = TIDES.get(player.getUUID());
        return tide != null && tide.octopus != null;
    }

    public static boolean riding(ServerPlayer player) {
        Tide tide = TIDES.get(player.getUUID());
        return tide != null && tide.ride != null;
    }

    public static boolean storming(ServerPlayer player) {
        Tide tide = TIDES.get(player.getUUID());
        return tide != null && tide.storm != null;
    }

    public static int waves(ServerPlayer player) {
        return (int) WAVES.stream().filter(w -> w.owner == player).count();
    }

    // ------------------------------------------------------------------
    // Shared helpers: who is hit, and how water hits
    // ------------------------------------------------------------------

    public static boolean hostile(ServerPlayer player, LivingEntity target) {
        return !(target instanceof AbstractVillager) && FlightSkillService.hostile(player, target);
    }

    public static boolean friend(ServerPlayer player, LivingEntity target) {
        return FlightSkillService.isFriend(player, target) || target instanceof AbstractVillager;
    }

    static List<LivingEntity> enemies(ServerPlayer player, Vec3 center, double radius) {
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                        target -> target != player && hostile(player, target) && target.position().distanceToSqr(center) <= radius * radius)
                .stream().limit(32).toList();
    }

    /** Water passes through water: only solid blocks give cover. */
    static boolean exposed(ServerPlayer owner, Vec3 from, Vec3 to) {
        return owner.serverLevel().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, owner)).getType() == HitResult.Type.MISS;
    }

    /** One hit of water: it lands, and it drenches. */
    public static void hit(ServerPlayer player, LivingEntity target, float damage) {
        if (!hostile(player, target) || !target.isAlive()) return;
        powerDamageDepth++;
        try {
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), damage);
        } finally {
            powerDamageDepth--;
        }
        soak(target, 120);
    }

    public static void soak(LivingEntity target, int ticks) {
        target.addEffect(new MobEffectInstance(ModItems.SOAKED, ticks, 0, false, true, true));
        if (target.isOnFire()) target.clearFire();
    }

    public static boolean soaked(LivingEntity target) { return target.hasEffect(ModItems.SOAKED); }

    static void push(Entity target, Vec3 velocity) { StyleMotion.push(target, velocity); }

    /** Water poured over the world where it lands: fire goes out, lava hardens. Only where the player may build. */
    static void douse(ServerPlayer player, Vec3 at, int radius) {
        ServerLevel level = player.serverLevel();
        BlockPos center = BlockPos.containing(at);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -1, -radius), center.offset(radius, 1, radius))) {
            BlockState state = level.getBlockState(pos);
            BlockState into = state.is(BlockTags.FIRE) ? Blocks.AIR.defaultBlockState()
                    : level.getFluidState(pos).is(FluidTags.LAVA)
                    ? (level.getFluidState(pos).isSource() ? Blocks.OBSIDIAN : Blocks.COBBLESTONE).defaultBlockState() : null;
            if (into == null || !level.mayInteract(player, pos)) continue;
            if (WorldMutationPolicy.place(player, level, pos.immutable(), into))
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, .5F, 1.4F);
        }
    }

    static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void used(ServerPlayer player) { MasteryService.markPowerUse(player, ModAbilities.HYDROKINESIS_ID); }

    // ------------------------------------------------------------------
    // Water Whip
    // ------------------------------------------------------------------

    /**
     * A whip of water cracked out from the hand to the first foe in a narrow tube along the aim. With Shift it snares
     * them instead, coils round their waist and hauls them in. Undertow (the skill) drags a cracked foe a block closer
     * and lets every whip bite a Soaked foe harder.
     */
    public static boolean whip(AbilityUseContext c) {
        ServerPlayer player = c.player();
        ServerLevel level = player.serverLevel();
        boolean snare = c.modifierHeld();
        Draw draw = draw(player, 8);
        int lvl = Math.max(1, c.abilityLevel());
        Vec3 eye = player.getEyePosition(), aim = player.getLookAngle().normalize();
        double reach = HydroGeometry.WHIP_REACH * (HydroSkills.throughWall(player, eye, aim) ? 1.3 : 1);
        var hit = level.clip(new ClipContext(eye, eye.add(aim.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double length = eye.distanceTo(hit.getLocation());
        LivingEntity target = firstInTube(player, eye, aim, length, HydroGeometry.WHIP_WIDTH * (HydroSkills.throughWall(player, eye, aim) ? 2 : 1));
        Vec3 end = target != null ? target.getBoundingBox().getCenter() : hit.getLocation();
        if (target != null) {
            float damage = (4 + lvl) * draw.power() * (snare ? .6F : 1);
            boolean undertow = SkillTreeService.has(player, "hydro_0_0");
            if (undertow && soaked(target)) damage *= 1.25F;
            hit(player, target, damage);
            if (snare) {
                Vec3 toward = player.position().subtract(target.position());
                double distance = toward.length();
                push(target, toward.normalize().scale(Math.min(1.7, .3 + distance * .2)).add(0, .38, 0));
                HydroGeometry.show(level, Piece.HYDRO_SNARE, HydroGeometry.hand(player, 1, true), target.position(),
                        target.getBbHeight(), draw.thin(), target);
            } else {
                Vec3 shove = aim.scale(undertow ? -.35 : .5).add(0, .18, 0);
                push(target, shove);
            }
        } else if (hit.getType() == HitResult.Type.BLOCK) {
            douse(player, hit.getLocation(), 1);
        }
        HydroGeometry.show(level, Piece.HYDRO_WHIP, HydroGeometry.hand(player, 1, true), end,
                (target != null ? 1 : .6) + (snare ? 4 : 0), draw.thin(), player);
        ArmPosePayload.show(player, snare ? ArmPose.WATER_SNARE : ArmPose.WATER_WHIP, snare ? 12 : 10);
        sound(player, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, .55F, 1.5F);
        sound(player, SoundEvents.TRIDENT_THROW.value(), .8F, snare ? .9F : 1.35F);
        if (target != null) sound(level, end, SoundEvents.TRIDENT_HIT, .7F, 1.4F);
        used(player);
        return true;
    }

    /** The nearest foe (exposed, alive) whose body lies within {@code width} of the aim line, out to {@code length}. */
    static LivingEntity firstInTube(ServerPlayer player, Vec3 eye, Vec3 aim, double length, double width) {
        LivingEntity target = null;
        double nearest = Double.MAX_VALUE;
        for (LivingEntity candidate : enemies(player, eye, length + 1)) {
            Vec3 center = candidate.getBoundingBox().getCenter(), delta = center.subtract(eye);
            double along = delta.dot(aim), reach = width + candidate.getBbWidth() * .5;
            if (along < 0 || along > length + .3 || along >= nearest
                    || delta.subtract(aim.scale(along)).lengthSqr() > reach * reach || !exposed(player, eye, center)) continue;
            target = candidate;
            nearest = along;
        }
        return target;
    }

    // ------------------------------------------------------------------
    // Crashing Wave
    // ------------------------------------------------------------------

    /** A wave raised from the ground at your feet and rolled forward along it. Tsunami Heart doubles it in water. */
    public static boolean crashingWave(AbilityUseContext c) {
        ServerPlayer player = c.player();
        Draw draw = draw(player, 25);
        boolean heart = SkillTreeService.has(player, "hydro_tsunami_heart") && player.isInWaterOrBubble();
        double halfWidth = HydroGeometry.WAVE_HALF_WIDTH * (heart ? 2 : 1) * (draw.thin() ? .8 : 1);
        roll(player, HydroGeometry.level(player.getLookAngle(), player.getYRot()), Piece.HYDRO_WAVE, HydroGeometry.WAVE_REACH,
                halfWidth, (5 + Math.max(1, c.abilityLevel())) * draw.power(), draw.thin(), player.position());
        ArmPosePayload.show(player, ArmPose.WATER_HEAVE, 14);
        sound(player, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.1F, .55F);
        sound(player, SoundEvents.GENERIC_SPLASH, 1, .7F);
        used(player);
        return true;
    }

    /** Starts a wave of {@code piece} rolling from just in front of {@code from} along {@code dir}. */
    static void roll(ServerPlayer player, Vec3 dir, Piece piece, double reach, double halfWidth, float damage, boolean thin, Vec3 from) {
        ServerLevel level = player.serverLevel();
        // The wave rises far enough ahead that its back starts in front of the bender, however big it is.
        Vec3 ahead = from.add(dir.scale(1.1 + halfWidth * .5));
        Vec3 floor = HydroGeometry.ground(level, ahead, 2, 4);
        Wave wave = new Wave();
        wave.owner = player;
        wave.level = level;
        wave.start = floor != null ? floor : new Vec3(ahead.x, Math.floor(from.y), ahead.z);
        wave.dir = dir;
        wave.born = level.getServer().overworld().getGameTime();
        wave.halfWidth = halfWidth;
        wave.height = halfWidth * 1.05;
        wave.reach = reach;
        wave.piece = piece;
        wave.travel = HydroGeometry.waveTravel(piece);
        wave.damage = damage;
        wave.riptide = SkillTreeService.has(player, "hydro_0_2");
        WAVES.add(wave);
        HydroGeometry.show(level, piece, wave.start, wave.start.add(dir.scale(reach)), halfWidth, thin, null);
    }

    /** One tick of a wave. False once it has broken. */
    private static boolean tickWave(Wave w, long now) {
        if (w.owner.isRemoved() || w.owner.serverLevel() != w.level) return false;
        float age = now - w.born;
        int end = HydroGeometry.WAVE_RISE + w.travel;
        if (age > end + 2) {
            // It breaks: anything Riptide carried to the end is slammed down there.
            if (w.riptide) for (UUID id : w.caught)
                if (w.level.getEntity(id) instanceof LivingEntity carried && carried.isAlive()) {
                    hit(w.owner, carried, 3);
                    push(carried, new Vec3(0, -.7, 0));
                }
            Vec3 last = w.start.add(w.dir.scale(w.reach));
            HydroGeometry.show(w.level, Piece.HYDRO_SPLASH, new Vec3(last.x, HydroGeometry.waveFloor(w.level, w.start, w.dir, w.reach), last.z),
                    last, w.halfWidth * .9, null);
            return false;
        }
        float rise = HydroGeometry.waveRise(age, w.travel);
        if (rise < .25F) return true;
        double distance = HydroGeometry.waveFront(age, w.reach, w.travel);
        double floor = HydroGeometry.waveFloor(w.level, w.start, w.dir, distance);
        Vec3 base = new Vec3(w.start.x + w.dir.x * distance, floor, w.start.z + w.dir.z * distance);
        Vec3 right = new Vec3(-w.dir.z, 0, w.dir.x);
        double height = w.height * rise;
        AABB box = new AABB(base, base).inflate(w.halfWidth + 1.5, 0, w.halfWidth + 1.5).expandTowards(0, height + 1, 0).move(0, -1, 0);
        for (Entity e : w.level.getEntities(w.owner, box, e -> e.isAlive() && !e.isSpectator())) {
            Vec3 rel = e.position().subtract(base);
            double along = rel.dot(w.dir), across = rel.dot(right);
            if (Math.abs(across) > w.halfWidth + e.getBbWidth() * .5 || along < -1.6 || along > 1.1) continue;
            if (e instanceof Projectile shot) {
                if (shot.getOwner() == w.owner || shot.getOwner() instanceof LivingEntity shooter && friend(w.owner, shooter)) continue;
                HydroGeometry.show(w.level, Piece.HYDRO_SPLASH, shot.position(), shot.position().add(w.dir), .6, null);
                shot.discard();
                continue;
            }
            if (!(e instanceof LivingEntity living) || !hostile(w.owner, living)
                    || !exposed(w.owner, base.add(0, height * .5, 0), living.getBoundingBox().getCenter())) continue;
            double resist = Math.max(.3, 1 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) * .6);
            if (w.caught.add(living.getUUID())) {
                hit(w.owner, living, w.damage);
                EvolutionTrials.advance(w.owner, ModAbilities.HYDROKINESIS_ID);
                push(living, w.dir.scale(1.05 * resist).add(0, .42 * resist, 0));
            } else if (w.riptide) {
                // Riptide carries what it caught along with the front, all the way to the end.
                push(living, w.dir.scale(.95 * resist).add(0, living.onGround() ? .2 : .02, 0));
                living.fallDistance = 0;
            }
        }
        if ((now - w.born) % 2 == 0) {
            for (int i = -1; i <= 1; i++) douse(w.owner, base.add(right.scale(i * w.halfWidth * .7)).add(0, .5, 0), 0);
            HydroSkills.waveOver(w.owner, base, w.dir, w.halfWidth);
        }
        if ((now - w.born) % 5 == 0) sound(w.level, base, SoundEvents.PLAYER_SPLASH, .7F, .6F);
        return true;
    }

    // ------------------------------------------------------------------
    // Octopus Form
    // ------------------------------------------------------------------

    public static boolean startOctopus(AbilityUseContext c) {
        ServerPlayer player = c.player();
        Tide tide = tide(player);
        if (tide.octopus != null || tide.ride != null) return false;
        Draw draw = draw(player, 20);
        Octopus octopus = new Octopus();
        long now = now(player);
        octopus.start = now;
        octopus.until = now + (draw.thin() ? HydroGeometry.OCTOPUS_TICKS / 2 : HydroGeometry.OCTOPUS_TICKS);
        octopus.nextLash = now + 6;
        octopus.level = Math.max(1, c.abilityLevel());
        tide.octopus = octopus;
        ArmPosePayload.show(player, ArmPose.WATER_OCTOPUS, 12);
        sound(player, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1, .7F);
        sound(player, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1, .8F);
        sync(player, true);
        used(player);
        return true;
    }

    /** The second press: every arm thrown out at once, and the form ends. Shift falls through to a quiet end. */
    public static boolean octopusBurst(AbilityUseContext c) {
        ServerPlayer player = c.player();
        Tide tide = TIDES.get(player.getUUID());
        if (tide == null || tide.octopus == null || c.modifierHeld()) return false;
        int lvl = tide.octopus.level;
        Vec3 feet = player.position();
        double radius = HydroGeometry.OCTOPUS_BURST;
        float damage = (6 + lvl) * boost(player);
        for (LivingEntity target : enemies(player, feet.add(0, 1, 0), radius + 1)) {
            if (!exposed(player, feet.add(0, 1.2, 0), target.getBoundingBox().getCenter())) continue;
            hit(player, target, damage);
            Vec3 out = target.position().subtract(feet).multiply(1, 0, 1);
            out = out.lengthSqr() < 1e-4 ? HydroGeometry.level(player.getLookAngle(), player.getYRot()) : out.normalize();
            push(target, out.scale(1.1).add(0, .45, 0));
        }
        HydroGeometry.show(player.serverLevel(), Piece.HYDRO_BURST, feet, feet, radius, player);
        ArmPosePayload.show(player, ArmPose.WATER_BURST, 12);
        sound(player, SoundEvents.GENERIC_SPLASH, 1.3F, .55F);
        sound(player, SoundEvents.TRIDENT_RIPTIDE_3.value(), .9F, .8F);
        CameraShakePayload.sendAround(player.serverLevel(), feet, 14, .2F, 6);
        endOctopus(player);
        return true;
    }

    public static boolean endOctopus(ServerPlayer player) {
        Tide tide = TIDES.get(player.getUUID());
        if (tide == null || tide.octopus == null) return false;
        tide.octopus = null;
        ArmPosePayload.clear(player);
        sound(player, SoundEvents.PLAYER_SPLASH, .8F, 1.2F);
        sync(player, true);
        return true;
    }

    private static void tickOctopus(ServerPlayer player, Tide tide, long now) {
        Octopus octopus = tide.octopus;
        if (now >= octopus.until || !canBend(player) || tide.water <= 0 && !player.isInWaterOrBubble()) {
            endOctopus(player);
            return;
        }
        if ((now - octopus.start) % 5 == 0 && !player.isInWaterOrBubble()) tide.water = Math.max(0, tide.water - 1);
        if ((now - octopus.start) % 10 == 0) ArmPosePayload.show(player, ArmPose.WATER_OCTOPUS, 12);
        Vec3 chest = HydroGeometry.chest(player.position());
        boolean deep = SkillTreeService.has(player, "hydro_1_2") && player.isInWaterOrBubble();
        double reach = HydroGeometry.OCTOPUS_REACH * (deep ? 2 : 1);
        // Two arms lash out at the nearest foes every few ticks.
        if (now >= octopus.nextLash) {
            octopus.nextLash = now + HydroGeometry.LASH_EVERY;
            List<LivingEntity> near = new ArrayList<>(enemies(player, chest, reach + 1));
            near.removeIf(e -> !exposed(player, chest, e.getBoundingBox().getCenter()));
            near.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
            for (int i = 0; i < Math.min(2, near.size()); i++) {
                LivingEntity target = near.get(i);
                hit(player, target, (2.5F + .5F * octopus.level) * boost(player));
                Vec3 out = target.position().subtract(player.position()).multiply(1, 0, 1);
                push(target, (out.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : out.normalize()).scale(.55).add(0, .25, 0));
                Vec3 root = chest.add(target.getBoundingBox().getCenter().subtract(chest).normalize().scale(.9));
                HydroGeometry.show(player.serverLevel(), Piece.HYDRO_TENDRIL, root, target.getBoundingBox().getCenter(), 1, player);
                sound(player, SoundEvents.TRIDENT_THROW.value(), .5F, 1.6F);
            }
        }
        // Shots that come near are swallowed (or, with Return Current, flung back).
        for (Projectile shot : player.serverLevel().getEntitiesOfClass(Projectile.class,
                new AABB(chest, chest).inflate(HydroGeometry.OCTOPUS_CATCH))) {
            if (shot.getOwner() == player || shot.getOwner() instanceof LivingEntity shooter && friend(player, shooter)) continue;
            HydroSkills.swallow(player, shot);
        }
    }

    // ------------------------------------------------------------------
    // Wave Rider
    // ------------------------------------------------------------------

    public static boolean startRide(AbilityUseContext c) {
        ServerPlayer player = c.player();
        Tide tide = tide(player);
        if (tide.ride != null || tide.octopus != null || player.isPassenger() || player.isFallFlying()) return false;
        Draw draw = draw(player, 15);
        Ride ride = new Ride();
        long now = now(player);
        ride.start = now;
        ride.level = Math.max(1, c.abilityLevel());
        boolean sea = SkillTreeService.has(player, "hydro_1_2") && onWater(player);
        ride.until = now + (int) (HydroGeometry.RIDE_TICKS * (draw.thin() ? .6 : 1) * (sea ? 2 : 1));
        ride.dir = HydroGeometry.level(player.getLookAngle(), player.getYRot());
        tide.ride = ride;
        var step = player.getAttribute(Attributes.STEP_HEIGHT);
        if (step != null) step.addOrUpdateTransientModifier(new AttributeModifier(RIDE_STEP, .65, AttributeModifier.Operation.ADD_VALUE));
        HydroGeometry.show(player.serverLevel(), Piece.HYDRO_SPLASH, player.position(), player.position().add(ride.dir), 1.6, null);
        ArmPosePayload.show(player, ArmPose.WATER_SURF, 12);
        sound(player, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1, .8F);
        sync(player, true);
        used(player);
        return true;
    }

    /** The second press: leap off the crest, high and forward. The landing splashes everyone near. */
    public static boolean leap(AbilityUseContext c) {
        ServerPlayer player = c.player();
        Tide tide = TIDES.get(player.getUUID());
        if (tide == null || tide.ride == null || tide.ride.leapAt >= 0 || c.modifierHeld()) return false;
        Ride ride = tide.ride;
        ride.leapAt = now(player);
        ride.dir = HydroGeometry.level(player.getLookAngle(), player.getYRot());
        push(player, ride.dir.scale(.95).add(0, 1.2, 0));
        StyleMotion.cushion(player, 80);
        HydroGeometry.show(player.serverLevel(), Piece.HYDRO_VAULT, player.position(), player.position().add(ride.dir), 1.4, player);
        ArmPosePayload.show(player, ArmPose.WATER_LEAP, 16);
        sound(player, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1, 1.1F);
        sync(player, true);
        return true;
    }

    public static boolean endRide(ServerPlayer player) {
        Tide tide = TIDES.get(player.getUUID());
        if (tide == null || tide.ride == null) return false;
        tide.ride = null;
        var step = player.getAttribute(Attributes.STEP_HEIGHT);
        if (step != null) step.removeModifier(RIDE_STEP);
        ArmPosePayload.clear(player);
        sync(player, true);
        return true;
    }

    private static boolean onWater(ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        return player.isInWater() || player.level().getFluidState(feet.below()).is(FluidTags.WATER)
                || player.level().getFluidState(feet).is(FluidTags.WATER);
    }

    private static void tickRide(ServerPlayer player, Tide tide, long now) {
        Ride ride = tide.ride;
        long age = now - ride.start;
        if (!canBend(player) || player.isPassenger()) { endRide(player); return; }
        if (ride.leapAt >= 0) {
            long air = now - ride.leapAt;
            if (air > 3 && (player.onGround() || player.isInWater()) || air > 40) {
                land(player, ride);
                endRide(player);
            }
            return;
        }
        if (now >= ride.until) { endRide(player); return; }
        if (age % 10 == 0) ArmPosePayload.show(player, ArmPose.WATER_SURF, 12);
        Vec3 look = HydroGeometry.level(player.getLookAngle(), player.getYRot());
        // Steering turns smoothly toward the look, like a board carving.
        ride.dir = ride.dir.lerp(look, .22).normalize();
        ride.onWater = onWater(player);
        boolean sea = SkillTreeService.has(player, "hydro_1_2");
        double speed = ride.onWater ? HydroGeometry.RIDE_WATER_SPEED * (sea ? 1.5 : 1) : HydroGeometry.RIDE_SPEED;
        if (player.isInWater()) ride.vy = .12;
        else if (player.onGround()) ride.vy = 0;
        else ride.vy = (ride.vy - .08) * .98;
        push(player, ride.dir.scale(speed).add(0, ride.vy, 0));
        player.resetFallDistance();
        if (SkillTreeService.has(player, "hydro_endless_tide")) fill(player, .5F);
        // Anyone in the way is knocked aside and soaked, once a ride.
        Vec3 right = new Vec3(-ride.dir.z, 0, ride.dir.x);
        for (LivingEntity target : enemies(player, player.position().add(ride.dir), 1.8)) {
            if (!ride.struck.add(target.getUUID())) continue;
            hit(player, target, (3 + .4F * ride.level) * boost(player));
            double side = target.position().subtract(player.position()).dot(right) >= 0 ? 1 : -1;
            push(target, right.scale(side * .85).add(ride.dir.scale(.3)).add(0, .32, 0));
        }
        if (age % 6 == 0) sound(player, SoundEvents.PLAYER_SWIM, .6F, .7F);
    }

    /** A leap off the crest comes down in a splash; with Endless Tide, a Crashing Wave rolls on in the riding direction. */
    private static void land(ServerPlayer player, Ride ride) {
        Vec3 feet = player.position();
        float damage = (4 + .5F * ride.level) * boost(player);
        for (LivingEntity target : enemies(player, feet, 3.2)) {
            if (Math.abs(target.getY() - feet.y) > 2) continue;
            hit(player, target, damage);
            Vec3 out = target.position().subtract(feet).multiply(1, 0, 1);
            push(target, (out.lengthSqr() < 1e-4 ? ride.dir : out.normalize()).scale(.7).add(0, .38, 0));
        }
        HydroGeometry.show(player.serverLevel(), Piece.HYDRO_SPLASH, feet, feet.add(ride.dir), 3.2, null);
        sound(player, SoundEvents.GENERIC_SPLASH, 1.2F, .8F);
        CameraShakePayload.sendAround(player.serverLevel(), feet, 10, .14F, 4);
        if (SkillTreeService.has(player, "hydro_endless_tide"))
            roll(player, ride.dir, Piece.HYDRO_WAVE, HydroGeometry.WAVE_REACH, HydroGeometry.WAVE_HALF_WIDTH,
                    (5 + ride.level) * boost(player), false, feet);
    }

    // ------------------------------------------------------------------
    // Maelstrom
    // ------------------------------------------------------------------

    public static boolean startMaelstrom(AbilityUseContext c) {
        ServerPlayer player = c.player();
        Tide tide = tide(player);
        if (tide.storm != null) return false;
        Vec3 point = aimGround(player);
        if (point == null) {
            player.displayClientMessage(Component.translatable("hydro.heroesevolved.no_ground"), true);
            return false;
        }
        Draw draw = draw(player, 40);
        Storm storm = new Storm();
        storm.center = point;
        storm.start = now(player);
        storm.level = Math.max(1, c.abilityLevel());
        storm.thin = draw.thin();
        storm.dimension = player.level().dimension();
        tide.storm = storm;
        HydroGeometry.show(player.serverLevel(), Piece.HYDRO_WHIRLPOOL, point, point, HydroGeometry.SPOUT_RING, draw.thin(), null);
        ArmPosePayload.show(player, ArmPose.WATER_CALL, 12);
        sound(player.serverLevel(), point, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.6F, .6F);
        sound(player, SoundEvents.CONDUIT_ACTIVATE, .9F, .7F);
        sync(player, true);
        used(player);
        return true;
    }

    /** Where the bender is looking, on the ground, within reach. */
    static Vec3 aimGround(ServerPlayer player) {
        Vec3 eye = player.getEyePosition(), aim = player.getLookAngle();
        var hit = player.serverLevel().clip(new ClipContext(eye, eye.add(aim.scale(HydroGeometry.SPOUT_REACH)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        Vec3 point = hit.getLocation();
        return HydroGeometry.ground(player.serverLevel(), point.add(0, .5, 0), 3, 24);
    }

    /** Shift: bring the waterspout down now, as a Deluge. */
    public static boolean endMaelstrom(ServerPlayer player) {
        Tide tide = TIDES.get(player.getUUID());
        if (tide == null || tide.storm == null) return false;
        Storm storm = tide.storm;
        tide.storm = null;
        long built = now(player) - storm.start - HydroGeometry.SPOUT_WINDUP;
        if (built >= 0 && player.level().dimension().equals(storm.dimension)) deluge(player, storm, (int) built);
        ArmPosePayload.clear(player);
        sync(player, true);
        return true;
    }

    private static void tickStorm(ServerPlayer player, Tide tide, long now) {
        Storm storm = tide.storm;
        long age = now - storm.start;
        if (!player.level().dimension().equals(storm.dimension) || !canBend(player)) {
            tide.storm = null;
            sync(player, true);
            return;
        }
        if (age % 10 == 0) ArmPosePayload.show(player, ArmPose.WATER_CALL, 12);
        if (age < HydroGeometry.SPOUT_WINDUP) {
            // The whirlpool opens: it already tugs at the ground's edges, but nothing is caught yet.
            if (age % 10 == 0) sound(player.serverLevel(), storm.center, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 1.4F, .7F);
            return;
        }
        int built = (int) (age - HydroGeometry.SPOUT_WINDUP);
        // The spout drifts toward where the bender looks, along the ground.
        Vec3 aim = aimGround(player);
        if (aim != null) {
            Vec3 toward = aim.subtract(storm.center).multiply(1, 0, 1);
            double distance = toward.length();
            if (distance > .2) {
                Vec3 next = storm.center.add(toward.scale(Math.min(.18, distance) / distance));
                Vec3 landed = HydroGeometry.ground(player.serverLevel(), next, 3, 12);
                if (landed != null) storm.center = landed;
            }
        }
        if (built % 2 == 0) SpeedCyclonePayload.send(player.serverLevel(), player, storm.center, HydroGeometry.SPOUT_RING,
                built, built, false, true, SpeedCyclonePayload.HYDRO);
        float damage = (2 + .3F * storm.level) * (storm.thin ? .7F : 1);
        TornadoWind.blowTempest(player, player.serverLevel(), storm.center, built, HydroGeometry.SPOUT_RING,
                HydroGeometry.SPOUT_PULL, damage, true, storm.caught, now);
        for (UUID id : storm.caught)
            if (player.serverLevel().getEntity(id) instanceof LivingEntity caught && caught.isAlive() && built % 20 == 0) soak(caught, 120);
        if (built % 20 == 0) sound(player.serverLevel(), storm.center, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.8F, .5F);
        if (built >= HydroGeometry.SPOUT_TICKS) endMaelstrom(player);
    }

    /** The spout comes down: everything it held is slammed to the ground in a crash of water that runs outward. */
    private static void deluge(ServerPlayer player, Storm storm, int built) {
        ServerLevel level = player.serverLevel();
        Vec3 center = storm.center;
        float radius = HydroGeometry.SPOUT_RING + 2;
        float damage = (8 + storm.level) * (storm.thin ? .7F : 1);
        Set<UUID> struck = new HashSet<>();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(radius + 1, 0, radius + 1).expandTowards(0, 30, 0).move(0, -2, 0),
                e -> e != player && hostile(player, e))) {
            boolean held = storm.caught.contains(target.getUUID());
            if (!held && (target.position().subtract(center).horizontalDistance() > radius
                    || !TornadoWind.exposed(player, center, target))) continue;
            struck.add(target.getUUID());
            hit(player, target, damage);
            Vec3 out = target.position().subtract(center).multiply(1, 0, 1);
            out = out.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : out.normalize();
            push(target, out.scale(.6).add(0, held && target.getY() > center.y + 2 ? -1.3 : .3, 0));
        }
        SpeedCyclonePayload.send(level, player, center, HydroGeometry.SPOUT_RING, built, built, true, true, SpeedCyclonePayload.HYDRO);
        HydroGeometry.show(level, Piece.HYDRO_DELUGE, center, center, radius, storm.thin, null);
        sound(level, center, SoundEvents.GENERIC_SPLASH, 2, .5F);
        sound(level, center, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 2, .4F);
        CameraShakePayload.sendAround(level, center, radius * 3, .55F, 10);
        douse(player, center, 3);
        if (SkillTreeService.has(player, "hydro_tsunami_heart")) {
            Vec3 forward = HydroGeometry.level(player.getLookAngle(), player.getYRot());
            for (int i = 0; i < 4; i++) {
                double angle = i * Math.PI / 2;
                Vec3 dir = new Vec3(forward.x * Math.cos(angle) - forward.z * Math.sin(angle), 0,
                        forward.x * Math.sin(angle) + forward.z * Math.cos(angle));
                roll(player, dir, Piece.HYDRO_WAVE, 10, HydroGeometry.WAVE_HALF_WIDTH, (5 + storm.level) * .8F, storm.thin, center);
            }
        }
    }

    // ------------------------------------------------------------------
    // Passives: the sea's own child
    // ------------------------------------------------------------------

    /** Every tick for an awakened hydrokinetic: the reservoir fills or dries, and water heals and carries them. */
    public static void playerTick(ServerPlayer player) {
        Tide tide = tide(player);
        long now = now(player);
        if (player.tickCount % 10 == 0) {
            boolean inWater = player.isInWaterOrBubble(), rain = !inWater && player.isInWaterRainOrBubble();
            boolean blessing = SkillTreeService.has(player, "hydro_2_0");
            float gain = inWater ? 5 : rain ? 2.5F : 0;
            if (gain == 0 && player.tickCount % 20 == 0 && nearWater(player)) gain = 2;
            if (player.level().dimensionType().ultraWarm()) gain = -.5F;
            if (gain != 0) fill(player, gain);
            if (inWater) {
                player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, true, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 60, 0, true, false, true));
                if (SkillTreeService.has(player, "hydro_1_2") && player.isEyeInFluid(FluidTags.WATER))
                    player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, true));
            }
            // Water and rain mend a hydrokinetic: health each second (Sea's Blessing doubles it).
            float heal = inWater ? WATER_HEAL : rain ? RAIN_HEAL : 0;
            if (blessing) heal *= 2;
            if (heal > 0 && player.tickCount % 20 == 0 && player.getHealth() < player.getMaxHealth()) player.heal(heal);
            if (inWater && blessing && player.tickCount % 20 == 0) {
                for (MobEffectInstance effect : List.copyOf(player.getActiveEffects()))
                    if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                        player.removeEffect(effect.getEffect());
                        break;
                    }
            }
        }
        sync(player, false);
    }

    private static boolean nearWater(ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 1, 2)))
            if (player.level().getFluidState(pos).is(FluidTags.WATER)) return true;
        return false;
    }

    /** Octopus Form's water takes a third off every blow (falls and magic included). */
    public static float shelter(ServerPlayer player, float amount) {
        return octopus(player) ? amount * .65F : amount;
    }

    /** Spirit Water (the capstone): a killing blow turned aside by the water carried, once every five minutes. */
    public static float spiritWater(ServerPlayer player, float damage) {
        if (!SkillTreeService.has(player, "hydro_spirit_water")) return damage;
        Tide tide = tide(player);
        long now = now(player);
        float health = player.getHealth() + player.getAbsorptionAmount();
        if (damage < health || tide.water < 30 || now < tide.spiritReady) return damage;
        tide.water = 0;
        tide.spiritReady = now + 6000;
        HydroGeometry.show(player.serverLevel(), Piece.HYDRO_HEAL, player.position(), player.position(), 2, player);
        sound(player, SoundEvents.CONDUIT_ACTIVATE, 1, 1.4F);
        player.displayClientMessage(Component.translatable("hydro.heroesevolved.spirit_water").withStyle(ChatFormatting.AQUA), true);
        sync(player, true);
        return Math.max(0, health - 8);
    }

    // ------------------------------------------------------------------
    // Telling watchers, ticking, and cleanup
    // ------------------------------------------------------------------

    private static int flags(ServerPlayer player, Tide tide) {
        int flags = 0;
        if (tide.octopus != null) flags |= OCTOPUS;
        if (tide.ride != null) flags |= tide.ride.leapAt >= 0 ? RIDING | LEAPING : RIDING;
        if (tide.storm != null) flags |= STORM;
        if (HydroSkills.spouting(player)) flags |= SPOUT;
        return flags;
    }

    /** Tells the bender and everyone watching them how much water they carry and what they are doing with it. */
    public static void sync(ServerPlayer player, boolean force) {
        Tide tide = tide(player);
        // Someone who no longer bends water (a respec, a revoked power) shows none, whatever their old reservoir held.
        boolean bends = isHydrokinetic(player) || com.jayptucker.heroesevolved.mimicry.MimicryService.isBorrowingHydrokinesis(player);
        int water = bends ? (int) tide.water : 0, flags = bends ? flags(player, tide) : 0;
        long now = now(player);
        if (!force && flags == tide.syncedFlags && Math.abs(water - tide.synced) < 4 && now - tide.lastSync < 100) return;
        tide.synced = water;
        tide.syncedFlags = flags;
        tide.lastSync = now;
        if (player.connection == null) return;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new HydroStatePayload(player.getId(), water, capacity(player), flags));
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        // Held moves tick here, not with the player, so they run the same for every player the server holds.
        for (Tide tide : List.copyOf(TIDES.values())) {
            ServerPlayer player = tide.player;
            if (player == null || !player.isAlive()) continue;
            if (tide.octopus != null) tickOctopus(player, tide, now);
            if (tide.ride != null) tickRide(player, tide, now);
            if (tide.storm != null) tickStorm(player, tide, now);
        }
        WAVES.removeIf(wave -> !tickWave(wave, now));
        HydroSkills.tick(server, now);
    }

    /** Death, logout, a new dimension or losing the power: everything running stops. Water Skin wakes full. */
    public static void forget(ServerPlayer player) {
        Tide tide = TIDES.get(player.getUUID());
        if (tide != null) {
            if (tide.ride != null) endRide(player);
            tide.octopus = null;
            tide.storm = null;
        }
        WAVES.removeIf(w -> w.owner == player);
        HydroSkills.forget(player);
        if (tide != null && player.isAlive()) sync(player, true);
    }

    /**
     * Their powers changed (a new power, a revoked one, a mimic switching sets). {@link #forget} on revoke runs while the
     * power is still theirs, so its sync still carries the water; this one runs after, and tells every viewer to stop
     * drawing it if they no longer bend water. Held moves end too, or the octopus arms stay on a body that can't use them.
     */
    public static void powerChanged(ServerPlayer player) {
        Tide tide = TIDES.get(player.getUUID());
        if (tide == null || !player.isAlive()) return;
        if (!isHydrokinetic(player) && !com.jayptucker.heroesevolved.mimicry.MimicryService.isBorrowingHydrokinesis(player)) {
            if (tide.ride != null) endRide(player);
            tide.octopus = null;
            tide.storm = null;
        }
        sync(player, true);
    }

    /** Logout: everything stops and the reservoir is forgotten (it refills). */
    public static void leave(ServerPlayer player) {
        forget(player);
        TIDES.remove(player.getUUID());
    }

    public static void respawned(ServerPlayer player) {
        Tide tide = tide(player);
        tide.water = SkillTreeService.has(player, "hydro_2_2") ? capacity(player) : 0;
        sync(player, true);
    }

    public static void clear() {
        TIDES.clear();
        WAVES.clear();
        HydroSkills.clear();
    }

    /** The awakening: the water rises round the bender and their ward, and throws back everything that was on them. */
    public static void awaken(ServerPlayer player, LivingEntity ward, List<LivingEntity> threats) {
        fill(player, capacity(player));
        Vec3 at = ward.position();
        HydroGeometry.show(player.serverLevel(), Piece.HYDRO_AWAKEN, player.position(), at, 1, player);
        for (LivingEntity threat : threats) {
            if (!threat.isAlive() || !hostile(player, threat)) continue;
            hit(player, threat, 6);
            Vec3 out = threat.position().subtract(at).multiply(1, 0, 1);
            push(threat, (out.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : out.normalize()).scale(1.3).add(0, .5, 0));
        }
        ArmPosePayload.show(player, ArmPose.WATER_CALL, 30);
        sound(player, SoundEvents.GENERIC_SPLASH, 1.5F, .6F);
        sound(player, SoundEvents.CONDUIT_ACTIVATE, 1, .9F);
        CameraShakePayload.sendAround(player.serverLevel(), at, 16, .3F, 8);
        sync(player, true);
    }
}
