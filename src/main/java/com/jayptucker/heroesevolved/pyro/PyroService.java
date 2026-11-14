package com.jayptucker.heroesevolved.pyro;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.expansion.FacilityService;
import com.jayptucker.heroesevolved.flight.FlightService;
import com.jayptucker.heroesevolved.flight.FlightSkillService;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.time.TemporalSnapshotService;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;

/**
 * Pyrokinesis, inspired by Meredith and Flint Gordon. Heat builds while the
 * power is used and cools quickly; at the blue threshold the flames burn hotter.
 *
 * <p>World fire is real vanilla fire placed through {@link WorldMutationPolicy},
 * so protection, build permission and doFireTick all apply. Crouching while
 * casting contains the flames: creatures still burn, but no blocks catch.
 * Every search and placement has a fixed bound.</p>
 */
public final class PyroService {
    public static final ResourceLocation PALM_ID = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "pyro_flame_palm");
    public static final int MAX_HEAT = 100;
    private static final int BLUE_HEAT = 80, WHITE_HEAT = 60;
    private static final float BLUE_MULTIPLIER = 1.5F;

    private static final class Heat { int value; long lastGain; boolean blue; }
    private record Channel(ServerPlayer player, int level, long until, ResourceKey<Level> dimension) {}
    private record Charge(ServerPlayer player, int level, long releaseAt, ResourceKey<Level> dimension,
                          Vec3 center, boolean contained) {}
    private record FallingSun(ServerPlayer owner, ServerLevel level, Vec3 center, double radius,
                              float damage, boolean blue, boolean contained, long impactAt) {}
    private record Spear(ServerPlayer player, int level, long releaseAt, ResourceKey<Level> dimension) {}
    private static final class Ring {
        final ServerPlayer owner;
        final ServerLevel level;
        final Vec3 center;
        final long opens, until;
        final boolean contained;
        long fedUntil;

        Ring(ServerPlayer owner, Vec3 center, long now, boolean contained) {
            this.owner = owner;
            this.level = owner.serverLevel();
            this.center = center;
            this.opens = now + PyroGeometry.STORM_WARNING;
            this.until = opens + 160;
            this.contained = contained;
        }

        double radius(long now) { return now < fedUntil ? 5.5 : 4; }
    }
    private record Collapse(ServerPlayer owner, ServerLevel level, Vec3 center, double radius,
                            float damage, boolean blue, long releaseAt) {}
    private record Mantle(ServerPlayer owner, ServerLevel level, long until) {}
    private record Lash(ServerPlayer owner, LivingEntity target, ServerLevel level, long until) {}
    private record Lance(ServerPlayer owner, ServerLevel level, Vec3 start, Vec3 end, long until) {}
    private record Mine(ServerPlayer owner, ServerLevel level, Vec3 position, boolean contained, long until) {}

    private static final Map<UUID, Heat> HEAT = new HashMap<>();
    private static final Map<UUID, Spear> SPEARS = new HashMap<>();
    private static final Map<UUID, Channel> PALMS = new HashMap<>();
    private static final Map<UUID, Charge> INFERNOS = new HashMap<>();
    private static final Map<UUID, FallingSun> SUNS = new HashMap<>();
    private static final Map<UUID, Collapse> COLLAPSES = new HashMap<>();
    private static final Map<UUID, Ring> RINGS = new HashMap<>();
    private static final Map<UUID, Lash> LASHES = new HashMap<>();
    private static final Map<UUID, Lance> LANCES = new HashMap<>();
    private static final Map<Mine, Long> WARNINGS = new HashMap<>();
    private static final Map<UUID, Mantle> MANTLES = new HashMap<>();
    private static final Map<UUID, List<Mine>> MINES = new HashMap<>();
    private static final Map<UUID, Long> CUSHIONS = new HashMap<>();
    private static int powerDamageDepth;

    private PyroService() {}

    // ------------------------------------------------------------------
    // Ownership, heat and shared helpers
    // ------------------------------------------------------------------

    public static boolean isPyrokinetic(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower()
                .filter(entry -> entry.getKey().equals(ModAbilities.PYROKINESIS_ID) && entry.getValue().isUnlocked())
                .isPresent() && !EclipseService.arePowersSuppressed(player);
    }

    private static boolean canSustainFire(ServerPlayer player) {
        return isPyrokinetic(player)
                || com.jayptucker.heroesevolved.mimicry.MimicryService.isBorrowingPyrokinesis(player);
    }

    /** True while one of this power's own hits is landing, so passives do not stack on top. */
    public static boolean isDealingPowerDamage() { return powerDamageDepth > 0; }

    public static int heat(ServerPlayer player) {
        Heat heat = HEAT.get(player.getUUID());
        return heat == null ? 0 : heat.value;
    }

    public static boolean isBlue(ServerPlayer player) {
        return heat(player) >= (SkillTreeService.has(player, "pyrokinesis_white_heat") ? WHITE_HEAT : BLUE_HEAT);
    }

    public static void addHeat(ServerPlayer player, int amount) {
        if (amount <= 0) return;
        Heat heat = HEAT.computeIfAbsent(player.getUUID(), ignored -> new Heat());
        heat.value = Math.min(MAX_HEAT, heat.value + amount);
        heat.lastGain = player.getServer().overworld().getGameTime();
        announce(player, heat);
    }

    public static boolean spendHeat(ServerPlayer player, int amount) {
        if (amount < 0 || heat(player) < amount) return false;
        Heat state = HEAT.get(player.getUUID());
        if (state == null) return amount == 0;
        state.value -= amount;
        announce(player, state);
        return true;
    }

    public static void quench(ServerPlayer player) {
        Heat heat = HEAT.get(player.getUUID());
        if (heat == null || heat.value == 0) return;
        heat.value = 0;
        announce(player, heat);
    }

    /** Frost puts out active flames and returns the Heat the caster had stored. */
    public static int freezeFlames(ServerPlayer player) {
        if (!canSustainFire(player)) return 0;
        int absorbed = heat(player);
        quench(player);
        stopFlames(player, true);
        if (absorbed > 0) PyroFlightService.sync(player);
        return absorbed;
    }

    private static void announce(ServerPlayer player, Heat heat) {
        boolean blue = isBlue(player);
        if (blue == heat.blue) return;
        heat.blue = blue;
        player.displayClientMessage(Component.translatable(blue ? "pyro.heroesevolved.blue" : "pyro.heroesevolved.cooled")
                .withStyle(blue ? ChatFormatting.AQUA : ChatFormatting.GOLD), true);
        if (blue) sound(player, SoundEvents.SOUL_ESCAPE.value(), 1.2F, 0.8F);
    }

    static float scaled(ServerPlayer player, float damage) {
        return isBlue(player) ? damage * BLUE_MULTIPLIER : damage;
    }

    public static boolean friendly(ServerPlayer player, LivingEntity target) {
        return FlightSkillService.isFriend(player, target) || target instanceof net.minecraft.world.entity.npc.AbstractVillager;
    }

    public static boolean hostile(ServerPlayer player, LivingEntity target) {
        return !friendly(player, target) && FlightSkillService.hostile(player, target);
    }

    static DamageSource fire(ServerPlayer player) {
        // An attributed fire source: armor still applies, and fire-immune creatures resist it.
        return new DamageSource(player.serverLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.IN_FIRE), player);
    }

    /** One pyrokinetic hit: a thermal-shock check, attributed fire damage, then ignition. */
    public static void burn(ServerPlayer player, LivingEntity target, float damage, int fireSeconds) {
        if (!hostile(player, target)) return;
        powerDamageDepth++;
        try {
            com.jayptucker.heroesevolved.expansion.PowerReactions.thermal(player, target);
            target.invulnerableTime = 0;
            target.hurt(fire(player), damage);
        } finally {
            powerDamageDepth--;
        }
        if (fireSeconds > 0 && target.isAlive()) target.igniteForSeconds(fireSeconds);
    }

    static List<LivingEntity> enemies(ServerPlayer player, Vec3 center, double radius) {
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                        target -> hostile(player, target) && target.position().distanceToSqr(center) <= radius * radius)
                .stream().limit(32).toList();
    }

    private static boolean visibleFrom(ServerLevel level, Vec3 center, LivingEntity target) {
        return level.clip(new ClipContext(center.add(0, 0.5, 0), target.getBoundingBox().getCenter(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target)).getType() == HitResult.Type.MISS;
    }

    static boolean exposed(ServerPlayer owner, Vec3 from, Vec3 to) {
        return owner.serverLevel().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, owner)).getType() == HitResult.Type.MISS;
    }

    private static boolean hostileShot(ServerPlayer owner, Projectile shot) {
        return shot.getOwner() != owner && (!(shot.getOwner() instanceof LivingEntity shooter) || hostile(owner, shooter));
    }

    /** Only one held cast owns the arms at a time. Breath remains compatible with jet flight. */
    public static boolean canStartCast(ServerPlayer player) {
        return PowerRules.canAct(player) && !player.isInWaterOrBubble() && !PyroFlightService.isDiving(player)
                && !isChanneling(player) && !isChargingSpear(player) && !isCharging(player);
    }

    private static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    static void push(LivingEntity target, Vec3 velocity) {
        target.setDeltaMovement(velocity);
        target.hurtMarked = true;
        if (target instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
    }

    // ------------------------------------------------------------------
    // Real fire
    // ------------------------------------------------------------------

    /** Fire is never placed in story dimensions, which have their own mission-owned terrain. */
    public static boolean worldFireAllowed(ServerPlayer player, ServerLevel level) {
        return COMMON.pyroWorldFire.get() && !PyroFlightService.isContainingFire(player) && !TimeIntroService.isCity(level)
                && !TemporalSnapshotService.isTemporalDimension(level) && !level.dimension().equals(FacilityService.DIMENSION);
    }

    /** Lights the open space at or above {@code pos}. Returns true if a fire block was placed. */
    public static boolean ignite(ServerPlayer player, ServerLevel level, BlockPos pos, boolean blue) {
        if (!worldFireAllowed(player, level)) return false;
        for (BlockPos candidate : new BlockPos[] {pos, pos.above()}) {
            if (!level.hasChunkAt(candidate) || !level.getBlockState(candidate).isAir()) continue;
            BlockState naturalFire = BaseFireBlock.getState(level, candidate);
            if (!naturalFire.canSurvive(level, candidate)) continue;
            return WorldMutationPolicy.place(player, level, candidate,
                    blue ? Blocks.SOUL_FIRE.defaultBlockState() : naturalFire);
        }
        return false;
    }

    /** The first open block above solid ground near a column, within two blocks up or down. */
    private static BlockPos surface(ServerLevel level, BlockPos around) {
        for (int dy = 2; dy >= -2; dy--) {
            BlockPos pos = around.offset(0, dy, 0);
            if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP))
                return pos;
        }
        return null;
    }

    private static int igniteCircle(ServerPlayer player, Vec3 center, double radius, int points, boolean blue) {
        if (!worldFireAllowed(player, player.serverLevel())) return 0;
        int lit = 0;
        for (int i = 0; i < points; i++) {
            double angle = i * Math.PI * 2 / points;
            BlockPos pos = surface(player.serverLevel(), BlockPos.containing(center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius)));
            if (pos != null && ignite(player, player.serverLevel(), pos, blue)) lit++;
        }
        return lit;
    }

    // ------------------------------------------------------------------
    // Core actions
    // ------------------------------------------------------------------

    /** Level 1: a fire bolt that burns the first creature or lights the first surface it reaches. */
    public static boolean flare(ServerPlayer player, int powerLevel) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        boolean blue = isBlue(player);
        LivingEntity target = Targeting.livingEntityInSight(player, 16, t -> hostile(player, t)
                && exposed(player, eye, t.getBoundingBox().getCenter()));
        Vec3 end;
        if (target != null) {
            end = target.getBoundingBox().getCenter();
            boolean detonate = target.isOnFire() && heat(player) >= 20;
            float damage = scaled(player, 4 + powerLevel);
            burn(player, target, damage, 4);
            if (detonate) {
                spendHeat(player, 20);
                for (LivingEntity nearby : enemies(player, end, 2.5)) {
                    if (nearby != target && target.hasLineOfSight(nearby)) burn(player, nearby, damage * 0.6F, 3);
                }
                PyroGeometry.show(level, Piece.PYRO_BURST, target.position(), target.position(), 2.5, blue, null);
                sound(player, SoundEvents.FIRECHARGE_USE, 0.8F, 0.7F);
                if (SkillTreeService.has(player, "pyrokinesis_0_2")) addHeat(player, 8);
            }
        } else {
            var hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(32)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
            end = hit.getLocation();
            if (hit.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = hit.getBlockPos();
                if (quenching(level, pos)) steam(player, level, Vec3.atCenterOf(pos));
                else ignite(player, level, pos.relative(hit.getDirection()), blue);
            }
        }
        PyroGeometry.show(level, Piece.PYRO_SPEAR, PyroGeometry.castingHand(player, 1), end, 1, blue, null);
        sound(player, SoundEvents.BLAZE_SHOOT, 0.9F, 1.1F);
        ArmPosePayload.show(player, ArmPose.FIRE_RELEASE, 12);
        addHeat(player, 12 + (target != null && SkillTreeService.has(player, "pyrokinesis_0_0") ? 4 : 0));
        return true;
    }

    public static boolean isChargingSpear(ServerPlayer player) { return SPEARS.containsKey(player.getUUID()); }

    public static boolean startSpear(ServerPlayer player, int tier) {
        if (!canStartCast(player)) return false;
        SPEARS.put(player.getUUID(), new Spear(player, tier,
                player.getServer().overworld().getGameTime() + 20, player.level().dimension()));
        ArmPosePayload.show(player, ArmPose.FIRE_FORGE, 20);
        Vec3 palm = PyroGeometry.castingHand(player, 1);
        PyroGeometry.show(player.serverLevel(), Piece.PYRO_FORGE, palm, palm, .15, isBlue(player), player);
        sound(player, SoundEvents.FIRECHARGE_USE, .35F, .75F);
        return true;
    }

    public static boolean cancelSpear(ServerPlayer player) {
        if (SPEARS.remove(player.getUUID()) == null) return false;
        ArmPosePayload.clear(player);
        return true;
    }

    /** A tap remains a fast bolt; a deliberate hold pierces a bounded line of enemies. */
    public static boolean releaseSpear(ServerPlayer player) {
        Spear spear = SPEARS.remove(player.getUUID());
        if (spear == null) return false;
        ArmPosePayload.clear(player);
        if (!PowerRules.canAct(player) || !canSustainFire(player) || player.isInWaterOrBubble()
                || !spear.dimension.equals(player.level().dimension())) return false;
        double progress = Math.clamp((20 - (spear.releaseAt - player.getServer().overworld().getGameTime())) / 20.0, 0, 1);
        if (progress < 0.25) return flare(player, spear.level);
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition(), aim = player.getLookAngle().normalize();
        var hit = level.clip(new ClipContext(eye, eye.add(aim.scale(32)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        double reach = eye.distanceTo(hit.getLocation());
        boolean blue = isBlue(player), fueled = progress >= 0.9 && heat(player) >= 20;
        float damage = scaled(player, (float) ((4 + spear.level) * (1 + progress) + (fueled ? 4 : 0)));
        if (fueled) spendHeat(player, 20);
        int struck = 0;
        boolean detonated = false;
        for (LivingEntity target : enemies(player, eye, 33)) {
            Vec3 delta = target.getBoundingBox().getCenter().subtract(eye);
            double along = delta.dot(aim);
            double width = (fueled ? 1.1 : 0.55) + target.getBbWidth() * 0.5;
            if (along < 0 || along > reach || delta.subtract(aim.scale(along)).lengthSqr() > width * width
                    || !exposed(player, eye, target.getBoundingBox().getCenter())) continue;
            boolean burning = target.isOnFire();
            burn(player, target, damage, 5);
            push(target, aim.scale(0.35).add(0, 0.12, 0));
            if (fueled && burning) {
                detonated = true;
                for (LivingEntity nearby : enemies(player, target.position(), 2.5)) {
                    if (nearby != target && target.hasLineOfSight(nearby)) burn(player, nearby, damage * 0.3F, 3);
                }
                PyroGeometry.show(level, Piece.PYRO_BURST, target.position(), target.position(), 2.5, blue, null);
            }
            if (++struck >= 8) break;
        }
        if (hit.getType() == HitResult.Type.BLOCK) {
            if (quenching(level, hit.getBlockPos())) steam(player, level, hit.getLocation());
            else ignite(player, level, hit.getBlockPos().relative(hit.getDirection()), blue);
        }
        if (progress >= .9) lightMinesAlong(player, eye, hit.getLocation());
        PyroGeometry.show(level, Piece.PYRO_SPEAR, PyroGeometry.castingHand(player, 1), hit.getLocation(), 1.75, blue, null);
        sound(player, SoundEvents.BLAZE_SHOOT, 1.3F, 0.65F);
        ArmPosePayload.show(player, ArmPose.FIRE_RELEASE, 12);
        addHeat(player, 12 + (struck > 0 && SkillTreeService.has(player, "pyrokinesis_0_0") ? 4 : 0)
                + (detonated && SkillTreeService.has(player, "pyrokinesis_0_2") ? 8 : 0));
        return true;
    }

    public static boolean isChanneling(ServerPlayer player) { return PALMS.containsKey(player.getUUID()); }

    /** Level 1: a held flamethrower. Release, Shift, or empty stamina ends it; five seconds at most. */
    public static boolean startPalm(ServerPlayer player, int powerLevel) {
        if (!canStartCast(player)) return false;
        PALMS.put(player.getUUID(), new Channel(player, powerLevel,
                player.getServer().overworld().getGameTime() + 100, player.level().dimension()));
        ArmPosePayload.show(player, ArmPose.FIRE_BREATH, 100);
        sound(player, SoundEvents.FIRECHARGE_USE, 1, 0.8F);
        return true;
    }

    public static boolean stopPalm(ServerPlayer player) {
        if (PALMS.remove(player.getUUID()) == null) return false;
        ArmPosePayload.clear(player);
        return true;
    }

    private static void palmPulse(Channel channel, long now) {
        ServerPlayer player = channel.player;
        ServerLevel level = player.serverLevel();
        Vec3 eye = PyroGeometry.breathOrigin(player, 1), aim = player.getLookAngle().normalize();
        double reach = 6 + heat(player) / 25.0;
        var hit = level.clip(new ClipContext(eye, eye.add(aim.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        double length = hit.getType() == HitResult.Type.BLOCK ? eye.distanceTo(hit.getLocation()) : reach;
        boolean blue = isBlue(player);
        int affected = 0;
        for (LivingEntity target : enemies(player, eye, reach + 0.5)) {
            Vec3 delta = target.getBoundingBox().getCenter().subtract(eye);
            double along = delta.dot(aim);
            double width = along * PyroGeometry.BREATH_SLOPE + target.getBbWidth() * .5;
            if (along < 0 || along > length + 0.3 || delta.subtract(aim.scale(along)).lengthSqr() > width * width
                    || !exposed(player, eye, target.getBoundingBox().getCenter())) continue;
            if (++affected > 12) break;
            burn(player, target, scaled(player, 1.5F + channel.level * 0.3F), 3);
            push(target, aim.scale(blue ? 0.38 : 0.18).add(0, 0.06, 0));
        }
        if (hit.getType() == HitResult.Type.BLOCK && now % 10 == 0) {
            BlockPos pos = hit.getBlockPos();
            if (quenching(level, pos)) steam(player, level, Vec3.atCenterOf(pos));
            else ignite(player, level, pos.relative(hit.getDirection()), blue);
        }
        Vec3 end = eye.add(aim.scale(length));
        PyroGeometry.show(level, Piece.PYRO_BREATH, eye, end, 1, blue, player);
        Ring ring = RINGS.get(player.getUUID());
        if (ring != null && now >= ring.opens && ring.level == level
                && exposed(player, eye, ring.center.add(0, .7, 0))) {
            Vec3 delta = ring.center.add(0, .7, 0).subtract(eye);
            double along = delta.dot(aim);
            if (along > 0 && along <= length + 2 && delta.subtract(aim.scale(along)).lengthSqr() < 9)
                ring.fedUntil = now + (blue && SkillTreeService.has(player, "pyrokinesis_1_2") ? 48 : 24);
        }
        if (now % 10 == 0) sound(player, SoundEvents.BLAZE_BURN, 0.8F, 0.9F);
        addHeat(player, 3 + (affected > 0 && SkillTreeService.has(player, "pyrokinesis_1_0") ? 2 : 0));
    }

    public static boolean hasRing(ServerPlayer player) { return RINGS.containsKey(player.getUUID()); }

    /** A kick whose fire sweeps across the caster's own open Firestorm fans it, the same as a Breath pulse. */
    static boolean fan(ServerPlayer player, Vec3 from, double reach) {
        Ring ring = RINGS.get(player.getUUID());
        long now = player.getServer().overworld().getGameTime();
        if (ring == null || now < ring.opens || ring.level != player.level()) return false;
        if (ring.center.subtract(from).horizontalDistance() > reach + ring.radius(now)
                || Math.abs(ring.center.y - from.y) > 3) return false;
        ring.fedUntil = Math.max(ring.fedUntil, now + (isBlue(player) && SkillTreeService.has(player, "pyrokinesis_1_2") ? 48 : 24));
        return true;
    }

    static boolean isFed(ServerPlayer player) {
        Ring ring = RINGS.get(player.getUUID());
        return ring != null && player.getServer().overworld().getGameTime() < ring.fedUntil;
    }

    private static Vec3 groundAim(ServerPlayer player, double reach) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        var hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(reach)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP) return hit.getLocation();
        int down = Math.max(0, player.getBlockY() - level.getMinBuildHeight());
        Vec3 below = FlightService.ground(level, player.position(), 0, down);
        if (below != null && player.getY() - below.y < 4) return below;
        Vec3 ahead = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(reach * .55));
        Vec3 ground = FlightService.ground(level, ahead, 0, down);
        return ground != null ? ground : below != null ? below : player.position();
    }

    /** Level 2: warn on the ground before raising a wall that can catch enemies and shots. */
    public static boolean ringOfFire(ServerPlayer player, int powerLevel) {
        if (!canStartCast(player) || COLLAPSES.containsKey(player.getUUID())) return false;
        Vec3 center = groundAim(player, 16);
        Ring ring = new Ring(player, center, player.getServer().overworld().getGameTime(), PyroFlightService.isContainingFire(player));
        RINGS.put(player.getUUID(), ring);
        PyroGeometry.show(ring.level, Piece.PYRO_FIRESTORM_PRIME, center, center, 4, isBlue(player), null);
        sound(player, SoundEvents.FIRECHARGE_USE, 1.2F, 0.6F);
        ArmPosePayload.show(player, ArmPose.FIRE_SWEEP, 18);
        addHeat(player, 15);
        return true;
    }

    /** Shift collapses the existing fire circle, trading its protection for a finishing blast. */
    public static boolean collapseRing(ServerPlayer player, int tier) {
        Ring ring = RINGS.remove(player.getUUID());
        if (ring == null) return false;
        if (ring.level != player.level() || !PowerRules.canAct(player)) return true;
        long now = player.getServer().overworld().getGameTime();
        boolean fueled = heat(player) >= 20;
        boolean blue = isBlue(player);
        double radius = ring.radius(now);
        float damage = scaled(player, 4 + tier + (fueled ? 4 : 0) + (radius > 4 ? 3 : 0));
        if (fueled) spendHeat(player, 20);
        COLLAPSES.put(player.getUUID(), new Collapse(player, ring.level, ring.center, radius, damage, blue,
                now + PyroGeometry.COLLAPSE_WARNING));
        PyroGeometry.show(ring.level, Piece.PYRO_COLLAPSE, ring.center, ring.center, radius, blue, null);
        if (!isChanneling(player) && !isChargingSpear(player) && !isCharging(player))
            ArmPosePayload.show(player, ArmPose.FIRE_RELEASE, 12);
        sound(player, SoundEvents.FIRECHARGE_USE, 1.2F, 0.5F);
        return true;
    }

    /** Level 3: a fire-propelled leap. The landing is cushioned; the launch point scorches attackers. */
    public static boolean backdraft(ServerPlayer player, int powerLevel) {
        boolean pillar = SkillTreeService.has(player, "pyrokinesis_pillar_of_flame");
        boolean fueled = heat(player) >= 20;
        boolean blue = isBlue(player);
        Vec3 look = player.getLookAngle();
        Vec3 velocity = new Vec3(look.x, 0, look.z).normalize().scale(1.2 + powerLevel * 0.12 + (fueled ? 0.6 : 0))
                .add(0, (pillar ? 1.35 : 0.75) + Math.max(0, look.y) * 0.5, 0);
        Vec3 start = player.position();
        for (LivingEntity target : enemies(player, start, fueled ? 5 : 3)) burn(player, target, scaled(player, fueled ? 6 : 3), 3);
        if (fueled) {
            spendHeat(player, 20);
            AbilityEffects.send(player.serverLevel(), Kind.FIRE_NOVA, start, start.add(0, 1, 0), 5);
        }
        BlockPos launch = surface(player.serverLevel(), player.blockPosition());
        if (launch != null) ignite(player, player.serverLevel(), launch, blue);
        player.setDeltaMovement(velocity);
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.resetFallDistance();
        if (pillar) player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
        CUSHIONS.put(player.getUUID(), player.getServer().overworld().getGameTime() + 200);
        AbilityEffects.send(player.serverLevel(), Kind.FIRE_STREAM, start, start.subtract(velocity.scale(2)), 1.5);
        sound(player, SoundEvents.FIRECHARGE_USE, 1, 1.2F);
        addHeat(player, 8);
        return true;
    }

    public static boolean cushion(ServerPlayer player) {
        Long until = CUSHIONS.remove(player.getUUID());
        return until != null && player.getServer().overworld().getGameTime() <= until;
    }

    public static boolean isCharging(ServerPlayer player) { return INFERNOS.containsKey(player.getUUID()); }

    /** Level 5: a three-second charge, then a fire nova. Shift cancels while charging. */
    public static boolean startInferno(ServerPlayer player, int powerLevel) {
        if (!canStartCast(player) || SUNS.containsKey(player.getUUID())) return false;
        LivingEntity target = Targeting.livingEntityInSight(player, 12, t -> hostile(player, t)
                && exposed(player, player.getEyePosition(), t.getBoundingBox().getCenter()));
        Vec3 center = target != null ? target.position() : groundAim(player, 24);
        INFERNOS.put(player.getUUID(), new Charge(player, powerLevel,
                player.getServer().overworld().getGameTime() + 60, player.level().dimension(), center,
                PyroFlightService.isContainingFire(player)));
        PyroGeometry.show(player.serverLevel(), Piece.PYRO_SUN_CHARGE, center.add(0, 6, 0), center, .25,
                isBlue(player) || SkillTreeService.has(player, "pyrokinesis_flints_fury"), null);
        ArmPosePayload.show(player, ArmPose.FIRE_SUN, 60);
        sound(player, SoundEvents.BLAZE_AMBIENT, 1.4F, 0.5F);
        return true;
    }

    public static boolean cancelInferno(ServerPlayer player) {
        if (INFERNOS.remove(player.getUUID()) == null) return false;
        ArmPosePayload.clear(player);
        player.displayClientMessage(Component.translatable("pyro.heroesevolved.inferno_cancelled"), true);
        return true;
    }

    public static boolean releaseInferno(ServerPlayer player) {
        Charge charge = INFERNOS.remove(player.getUUID());
        if (charge == null) return false;
        if (!PowerRules.canAct(player) || !charge.dimension.equals(player.level().dimension())
                || player.isInWaterOrBubble() || !player.isAlive() || !canSustainFire(player)) {
            ArmPosePayload.clear(player);
            return false;
        }
        releaseInferno(charge);
        return true;
    }

    private static void releaseInferno(Charge charge) {
        ServerPlayer player = charge.player;
        ServerLevel level = player.serverLevel();
        boolean fury = SkillTreeService.has(player, "pyrokinesis_flints_fury");
        boolean blue = fury || isBlue(player);
        float chargePower = (float) Math.clamp(
                (60 - (charge.releaseAt - player.getServer().overworld().getGameTime())) / 60.0, 0.25, 1);
        double radius = 4 + 6 * chargePower + (fury ? 3 : 0);
        Vec3 center = charge.center;
        float peak = ((blue ? 18 : 12) + charge.level * 2) * chargePower + heat(player) * 0.05F;
        quench(player);
        SUNS.put(player.getUUID(), new FallingSun(player, level, center, radius, peak, blue,
                charge.contained || PyroFlightService.isContainingFire(player),
                player.getServer().overworld().getGameTime() + PyroGeometry.SUN_FLIGHT));
        PyroGeometry.show(level, Piece.PYRO_SUN_DROP, center.add(0, 6, 0), center, radius, blue, null);
        level.playSound(null, center.x, center.y + 6, center.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.8F, .52F);
        ArmPosePayload.show(player, ArmPose.FIRE_RELEASE, 16);
        if (blue) player.displayClientMessage(Component.translatable("pyro.heroesevolved.blue_inferno").withStyle(ChatFormatting.AQUA), true);
    }

    private static void landSun(FallingSun sun) {
        ServerPlayer player = sun.owner;
        ServerLevel level = sun.level;
        Vec3 center = sun.center;
        double radius = sun.radius;
        for (LivingEntity target : enemies(player, center, radius)) {
            if (!visibleFrom(level, center, target)) continue;
            double falloff = 1 - 0.5 * target.position().distanceTo(center) / radius;
            burn(player, target, (float) (sun.damage * falloff), 8);
            push(target, target.position().subtract(center).multiply(1, 0, 1).normalize().scale(sun.blue ? 1.3 : .9).add(0, .35, 0));
        }
        if (!sun.contained && worldFireAllowed(player, level)) {
            for (int i = 0; i < 48; i++) {
                double angle = player.getRandom().nextDouble() * Math.PI * 2, distance = 2 + player.getRandom().nextDouble() * (radius - 2);
                BlockPos pos = surface(level, BlockPos.containing(center.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance)));
                if (pos != null) ignite(player, level, pos, sun.blue);
            }
        }
        PyroGeometry.show(level, Piece.PYRO_BURST, center, center, radius, sun.blue, null);
        CameraShakePayload.sendAround(level, center, radius * 4, sun.blue ? .85F : .65F, 18);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 2.6F, sun.blue ? .58F : .72F);
    }

    /** First awakening: the flames escape all at once in a ring of real fire. */
    public static void awaken(ServerPlayer player) {
        awaken(player, player.position());
    }

    /** The awakening's ring of fire, raised round {@code center}: the owner, or an ally they woke to save. */
    public static void awaken(ServerPlayer player, Vec3 center) {
        for (LivingEntity target : enemies(player, center, 8)) {
            burn(player, target, 6, 5);
            push(target, target.position().subtract(center).multiply(1, 0, 1).normalize().scale(1.1).add(0, 0.3, 0));
        }
        boolean blue = isBlue(player);
        igniteCircle(player, center, 5, 20, blue);
        igniteCircle(player, center, 8, 28, blue);
        PyroGeometry.show(player.serverLevel(), Piece.PYRO_BURST, center, center, 8, blue, null);
        sound(player, SoundEvents.BLAZE_SHOOT, 2, 0.6F);
        sound(player, SoundEvents.GENERIC_EXPLODE.value(), 1.4F, 1.1F);
    }

    /** Overexertion: the heat turns inward. */
    public static void overheat(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120));
        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 1));
        player.causeFoodExhaustion(6);
        quench(player);
    }

    /** Water, ice and snow turn fire into steam instead of catching. */
    static boolean quenching(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.getFluidState().isEmpty() || state.is(BlockTags.ICE) || state.is(BlockTags.SNOW);
    }

    /** Fire meeting water or ice becomes a short blinding steam cloud. */
    public static void steam(ServerPlayer player, ServerLevel level, Vec3 at) {
        com.jayptucker.heroesevolved.expansion.PowerReactions.steamVeil(player, level, at);
    }

    // ------------------------------------------------------------------
    // Skill tree: three opening techniques and three named techniques
    // ------------------------------------------------------------------

    public static boolean skill(AbilityUseContext context, int branch) {
        ServerPlayer player = context.player();
        if (!canStartCast(player)) return false;
        int level = context.abilityLevel();
        switch (branch) {
            case 0 -> {
                LivingEntity target = Targeting.livingEntityInSight(player, 20, t -> hostile(player, t));
                if (target == null) return false;
                burn(player, target, scaled(player, 6 + level), 5);
                push(target, player.position().subtract(target.position()).multiply(1, 0, 1).normalize().scale(0.45).add(0, 0.15, 0));
                LASHES.put(player.getUUID(), new Lash(player, target, player.serverLevel(),
                        player.getServer().overworld().getGameTime() + 8));
                showLash(player, target);
                ArmPosePayload.show(player, ArmPose.FIRE_LASH, 16);
                sound(player, SoundEvents.FIRECHARGE_USE, 0.9F, 1.4F);
                addHeat(player, 10);
            }
            case 1 -> {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 160, 0));
                MANTLES.put(player.getUUID(), new Mantle(player, player.serverLevel(), player.getServer().overworld().getGameTime() + 160));
                PyroGeometry.show(player.serverLevel(), Piece.PYRO_MANTLE, player.position(), player.position(), 2.55, isBlue(player), player);
                ArmPosePayload.show(player, ArmPose.FIRE_SWEEP, 18);
                sound(player, SoundEvents.FIRECHARGE_USE, 0.9F, 0.7F);
                addHeat(player, 6);
            }
            case 2 -> {
                LivingEntity friend = Targeting.livingEntityInSight(player, 4,
                        t -> t != player && friendly(player, t)
                                && (t.getHealth() < t.getMaxHealth() || t.isOnFire()
                                || t.getActiveEffects().stream().anyMatch(e -> !e.getEffect().value().isBeneficial())));
                LivingEntity recipient = friend == null ? player : friend;
                for (MobEffectInstance effect : List.copyOf(recipient.getActiveEffects()))
                    if (!effect.getEffect().value().isBeneficial()) recipient.removeEffect(effect.getEffect());
                recipient.heal(4 + level);
                recipient.clearFire();
                if (spendHeat(player, SkillTreeService.has(player, "pyrokinesis_2_2") ? 10 : 20))
                    recipient.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0));
                PyroGeometry.show(player.serverLevel(), Piece.PYRO_CAUTERIZE, recipient.position(), recipient.getEyePosition(), 1.4, false, recipient);
                if (recipient != player) PyroGeometry.show(player.serverLevel(), Piece.PYRO_SHEPHERD,
                        PyroGeometry.castingHand(player, 1), recipient.getBoundingBox().getCenter(), 1, false, null);
                ArmPosePayload.show(player, ArmPose.FIRE_CAUTERIZE, 24);
                sound(player, SoundEvents.FIRE_EXTINGUISH, 0.8F, 1.3F);
            }
            default -> { return false; }
        }
        return true;
    }

    private static void showLash(ServerPlayer player, LivingEntity target) {
        PyroGeometry.show(player.serverLevel(), Piece.PYRO_LASH, PyroGeometry.castingHand(player, 1),
                target.getBoundingBox().getCenter(), 1, isBlue(player), player);
    }

    public static boolean isLashing(ServerPlayer player) { return LASHES.containsKey(player.getUUID()); }
    public static boolean hasMantle(ServerPlayer player) { return MANTLES.containsKey(player.getUUID()); }
    public static boolean isLancing(ServerPlayer player) { return LANCES.containsKey(player.getUUID()); }
    public static int mineCount(ServerPlayer player) { return MINES.getOrDefault(player.getUUID(), List.of()).size(); }

    public static boolean technique(AbilityUseContext context, String mechanic) {
        ServerPlayer player = context.player();
        if (!canStartCast(player)) return false;
        return switch (mechanic) {
            case "ember_mine" -> plantMine(player, context.abilityLevel());
            case "blue_lance" -> blueLance(player, context.abilityLevel());
            case "fire_shepherd" -> fireShepherd(player);
            default -> false;
        };
    }

    private static boolean plantMine(ServerPlayer player, int powerLevel) {
        BlockPos aimed = ExpansionSkills.aim(player);
        BlockPos pos = surface(player.serverLevel(), aimed.above());
        if (pos == null) {
            player.displayClientMessage(Component.translatable("pyro.heroesevolved.no_ground"), true);
            return false;
        }
        List<Mine> mines = MINES.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>());
        int limit = SkillTreeService.has(player, "pyrokinesis_long_fuse") ? 3 : 2;
        while (mines.size() >= limit) WARNINGS.remove(mines.removeFirst());
        Vec3 position = Vec3.atBottomCenterOf(pos);
        mines.add(new Mine(player, player.serverLevel(), position, PyroFlightService.isContainingFire(player),
                player.getServer().overworld().getGameTime() + 20 * 40));
        PyroGeometry.showPrivate(player, Piece.PYRO_MINE_ARM, position, .6, isBlue(player));
        ArmPosePayload.show(player, ArmPose.FIRE_SWEEP, 18);
        sound(player, SoundEvents.FLINTANDSTEEL_USE, 0.7F, 0.8F);
        addHeat(player, 6);
        return true;
    }

    private static double mineRadius(ServerPlayer player) {
        return SkillTreeService.has(player, "pyrokinesis_long_fuse") ? 4 : 2.5;
    }

    private static boolean armed(Mine mine, long now) { return now >= mine.until - 20 * 40 + 8; }

    private static void warn(Mine mine, long now) {
        if (!armed(mine, now) || WARNINGS.putIfAbsent(mine, now + PyroGeometry.MINE_WARNING) != null) return;
        PyroGeometry.show(mine.level, Piece.PYRO_MINE_WARN, mine.position, mine.position,
                mineRadius(mine.owner), isBlue(mine.owner), null);
        mine.level.playSound(null, mine.position.x, mine.position.y, mine.position.z,
                SoundEvents.CREEPER_PRIMED, SoundSource.PLAYERS, .55F, 1.5F);
    }

    private static void lightMinesAlong(ServerPlayer player, Vec3 start, Vec3 end) {
        long now = player.getServer().overworld().getGameTime();
        Vec3 line = end.subtract(start);
        double lengthSquared = line.lengthSqr();
        if (lengthSquared < .01) return;
        for (Mine mine : MINES.getOrDefault(player.getUUID(), List.of())) {
            if (mine.level != player.serverLevel()) continue;
            Vec3 ember = mine.position.add(0, .22, 0);
            double progress = ember.subtract(start).dot(line) / lengthSquared;
            if (progress > 0 && progress <= 1 && start.lerp(end, progress).distanceToSqr(ember) <= 4
                    && exposed(player, start, ember)) warn(mine, now);
        }
    }

    private static void erupt(Mine mine, List<Mine> remaining, long now) {
        ServerPlayer player = mine.owner;
        double radius = mineRadius(player);
        int level = com.jayptucker.heroesevolved.progression.PlayerProgressionService.getLevel(player);
        boolean blue = isBlue(player);
        WARNINGS.remove(mine);
        for (LivingEntity target : enemies(player, mine.position, radius)) {
            if (visibleFrom(mine.level, mine.position, target)) burn(player, target, scaled(player, 6 + level), 5);
        }
        PyroGeometry.show(mine.level, Piece.PYRO_BURST, mine.position, mine.position, radius, blue, null);
        mine.level.playSound(null, mine.position.x, mine.position.y, mine.position.z, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 1, 1.3F);
        if (!SkillTreeService.has(player, "pyrokinesis_wildfire")) return;
        if (!mine.contained) igniteCircle(player, mine.position, radius, 8, blue);
        for (Mine other : remaining) {
            Vec3 from = mine.position.add(0, .3, 0), to = other.position.add(0, .3, 0);
            if (other.level == mine.level && from.distanceToSqr(to) <= 64 && exposed(player, from, to)) warn(other, now);
        }
    }

    private static boolean blueLance(ServerPlayer player, int powerLevel) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition(), aim = player.getLookAngle().normalize();
        var hit = level.clip(new ClipContext(eye, eye.add(aim.scale(24)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        double length = hit.getType() == HitResult.Type.BLOCK ? eye.distanceTo(hit.getLocation()) : 24;
        int struck = 0;
        for (LivingEntity target : enemies(player, eye, length + 1)) {
            Vec3 delta = target.getBoundingBox().getCenter().subtract(eye);
            double along = delta.dot(aim);
            if (along < 0 || along > length + 0.5 || delta.subtract(aim.scale(along)).length() > 0.9 + target.getBbWidth() / 2
                    || !exposed(player, eye, target.getBoundingBox().getCenter())) continue;
            if (++struck > 8) break;
            burn(player, target, (8 + powerLevel) * BLUE_MULTIPLIER, 6);
        }
        if (hit.getType() == HitResult.Type.BLOCK) {
            if (quenching(level, hit.getBlockPos())) steam(player, level, hit.getLocation());
            else ignite(player, level, hit.getBlockPos().relative(hit.getDirection()), true);
        }
        Vec3 end = eye.add(aim.scale(length));
        LANCES.put(player.getUUID(), new Lance(player, level, eye, end, player.getServer().overworld().getGameTime() + 13));
        lightMinesAlong(player, eye, end);
        PyroGeometry.show(level, Piece.PYRO_LANCE, PyroGeometry.castingHand(player, 1), end, 1.4, true, null);
        ArmPosePayload.show(player, ArmPose.FIRE_RELEASE, 12);
        sound(player, SoundEvents.SOUL_ESCAPE.value(), 1.4F, 0.6F);
        sound(player, SoundEvents.BLAZE_SHOOT, 1, 0.7F);
        addHeat(player, 15);
        return true;
    }

    /** Draws nearby fire into your hands, putting it out. Restores stamina and health; the firefighter's technique. */
    private static boolean fireShepherd(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();
        int drawn = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -4, -8), origin.offset(8, 4, 8))) {
            if (drawn >= 32) break;
            if (!level.getBlockState(pos).is(BlockTags.FIRE) || !WorldMutationPolicy.canEdit(player, level, pos)) continue;
            level.removeBlock(pos, false);
            drawn++;
            if (drawn % 4 == 1) PyroGeometry.show(level, Piece.PYRO_SHEPHERD,
                    Vec3.atCenterOf(pos), PyroGeometry.castingHand(player, 1), .6, isBlue(player), player);
        }
        if (drawn > 0) {
            PlayerEnergyService.restore(player, Math.min(40, drawn * 2));
            player.heal(drawn * 0.5F);
            addHeat(player, Math.min(40, drawn * 2));
            sound(player, SoundEvents.FIRE_EXTINGUISH, 1, 0.8F);
        }
        boolean thaw = SkillTreeService.has(player, "pyrokinesis_thaw");
        int rescued = 0;
        for (LivingEntity ally : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(5),
                ally -> friendly(player, ally) && ally.distanceToSqr(player) <= 25).stream().limit(32).toList()) {
            if (!player.hasLineOfSight(ally)) continue;
            if (ally.isOnFire()) {
                ally.clearFire();
                ally.heal(2);
                if (++rescued <= 8) PyroGeometry.show(level, Piece.PYRO_SHEPHERD,
                        ally.getBoundingBox().getCenter(), PyroGeometry.castingHand(player, 1), .65, false, player);
            }
            if (thaw && (CryoService.isFrozen(ally) || ally.getTicksFrozen() > 0 || ally.hasEffect(MobEffects.MOVEMENT_SLOWDOWN))) {
                if (CryoService.isFrozen(ally)) CryoService.releaseEntity(ally);
                ally.setTicksFrozen(0);
                ally.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                AbilityEffects.send(level, Kind.STEAM, ally.position(), ally.getEyePosition(), 1);
            }
        }
        if (SkillTreeService.has(player, "pyrokinesis_2_2")) addHeat(player, Math.min(18, rescued * 6));
        if (drawn == 0 && rescued > 0) sound(player, SoundEvents.FIRE_EXTINGUISH, .8F, 1.1F);
        player.displayClientMessage(Component.translatable("pyro.heroesevolved.shepherd", drawn), true);
        ArmPosePayload.show(player, ArmPose.FIRE_SHEPHERD, 24);
        return true;
    }

    // ------------------------------------------------------------------
    // Tick and cleanup
    // ------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();

        for (FallingSun sun : List.copyOf(SUNS.values())) {
            if (!canMaintain(sun.owner, sun.level) || !sun.level.hasChunkAt(BlockPos.containing(sun.center))) {
                SUNS.remove(sun.owner.getUUID());
            } else if (now >= sun.impactAt) {
                SUNS.remove(sun.owner.getUUID());
                landSun(sun);
            }
        }
        for (Collapse collapse : List.copyOf(COLLAPSES.values())) {
            ServerPlayer owner = collapse.owner;
            if (!canMaintain(owner, collapse.level)) {
                COLLAPSES.remove(owner.getUUID());
                continue;
            }
            if (now < collapse.releaseAt) continue;
            COLLAPSES.remove(owner.getUUID());
            for (LivingEntity target : enemies(owner, collapse.center, collapse.radius)) {
                if (!visibleFrom(collapse.level, collapse.center, target)) continue;
                burn(owner, target, collapse.damage, 5);
                push(target, target.getDeltaMovement().add(0, .35, 0));
            }
            PyroGeometry.show(collapse.level, Piece.PYRO_BURST, collapse.center, collapse.center,
                    collapse.radius, collapse.blue, null);
            collapse.level.playSound(null, collapse.center.x, collapse.center.y, collapse.center.z,
                    SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.15F, .64F);
            CameraShakePayload.sendAround(collapse.level, collapse.center, collapse.radius * 3, .36F, 9);
        }

        LASHES.values().removeIf(lash -> now >= lash.until || !PowerRules.canAct(lash.owner)
                || !canSustainFire(lash.owner) || lash.owner.level() != lash.level
                || lash.target.level() != lash.level || !hostile(lash.owner, lash.target) || !lash.owner.hasLineOfSight(lash.target));
        for (Lash lash : LASHES.values()) {
            Vec3 delta = lash.owner.position().subtract(lash.target.position()).multiply(1, 0, 1);
            if (delta.lengthSqr() > 9) push(lash.target, delta.normalize().scale(.4).add(0, .05, 0));
            if (now % 3 == 0) showLash(lash.owner, lash.target);
        }
        LANCES.values().removeIf(lance -> now >= lance.until || !PowerRules.canAct(lance.owner)
                || !canSustainFire(lance.owner) || lance.owner.level() != lance.level || lance.owner.isRemoved());
        if (now % 4 == 0) for (Lance lance : LANCES.values()) {
            var contact = lance.level.clip(new ClipContext(lance.start, lance.end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.ANY, lance.owner));
            Vec3 aim = lance.end.subtract(lance.start).normalize();
            double reach = lance.start.distanceTo(contact.getLocation());
            int struck = 0;
            for (LivingEntity target : enemies(lance.owner, lance.start, reach + 1)) {
                Vec3 delta = target.getBoundingBox().getCenter().subtract(lance.start);
                double along = delta.dot(aim);
                if (along < 0 || along > reach || delta.subtract(aim.scale(along)).lengthSqr() > 1.1) continue;
                if (!exposed(lance.owner, lance.start, target.getBoundingBox().getCenter())) continue;
                burn(lance.owner, target, 2.5F, 3);
                if (++struck >= 8) break;
            }
            PyroGeometry.show(lance.level, Piece.PYRO_LANCE, lance.start, contact.getLocation(), 1, true, null);
        }

        for (Spear spear : List.copyOf(SPEARS.values())) {
            ServerPlayer player = spear.player;
            if (!PowerRules.canAct(player) || !canSustainFire(player) || player.isRemoved()
                    || player.isInWaterOrBubble() || !spear.dimension.equals(player.level().dimension())) {
                cancelSpear(player);
                continue;
            }
            long remaining = spear.releaseAt - now;
            if (remaining <= 0) { releaseSpear(player); continue; }
            if (remaining % 4 == 0) {
                Vec3 palm = PyroGeometry.castingHand(player, 1);
                PyroGeometry.show(player.serverLevel(), Piece.PYRO_FORGE, palm, palm,
                        1 - remaining / 20.0, isBlue(player), player);
            }
        }

        for (Channel channel : List.copyOf(PALMS.values())) {
            ServerPlayer player = channel.player;
            if (!player.isAlive() || player.isRemoved() || now >= channel.until || !PowerRules.canAct(player)
                    || !channel.dimension.equals(player.level().dimension()) || !canSustainFire(player) || player.isInWaterOrBubble()) {
                stopPalm(player);
                continue;
            }
            if (now % 5 != 0) continue;
            if (!PlayerEnergyService.tryConsume(player, 3)) { stopPalm(player); continue; }
            MasteryService.awardEnergySpent(player, 3);
            palmPulse(channel, now);
        }

        for (Charge charge : List.copyOf(INFERNOS.values())) {
            ServerPlayer player = charge.player;
            if (!player.isAlive() || player.isRemoved() || !PowerRules.canAct(player) || !canSustainFire(player)
                    || !charge.dimension.equals(player.level().dimension()) || player.isInWaterOrBubble()) {
                INFERNOS.remove(player.getUUID());
                ArmPosePayload.clear(player);
                continue;
            }
            long remaining = charge.releaseAt - now;
            if (remaining <= 0) {
                INFERNOS.remove(player.getUUID());
                releaseInferno(charge);
                continue;
            }
            if (remaining % 4 == 0) PyroGeometry.show(player.serverLevel(), Piece.PYRO_SUN_CHARGE,
                    charge.center.add(0, 6, 0), charge.center, Math.max(.25, 1 - remaining / 60.0),
                    isBlue(player) || SkillTreeService.has(player, "pyrokinesis_flints_fury"), null);
        }

        RINGS.values().removeIf(ring -> now >= ring.until || !canMaintain(ring.owner, ring.level)
                || !ring.level.hasChunkAt(BlockPos.containing(ring.center)));
        for (Ring ring : RINGS.values()) {
            ServerPlayer owner = ring.owner;
            long age = now - ring.opens;
            if (age < 0) continue;
            double radius = ring.radius(now);
            if (age == 0) {
                if (!ring.contained) igniteCircle(owner, ring.center, radius, 24, isBlue(owner));
                ring.level.playSound(null, ring.center.x, ring.center.y, ring.center.z,
                        SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.3F, .65F);
            }
            if (age % 8 == 0) PyroGeometry.show(ring.level, Piece.PYRO_FIRESTORM, ring.center, ring.center,
                    radius, isBlue(owner), null);
            if (age % 5 != 0) continue;
            boolean fed = radius > 4;
            for (LivingEntity target : ring.level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(ring.center, ring.center).inflate(radius, 3, radius), target -> hostile(owner, target))
                    .stream().limit(24).toList()) {
                double distance = target.position().subtract(ring.center).horizontalDistance();
                if (distance > radius) continue;
                if (!visibleFrom(ring.level, ring.center, target)) continue;
                if (age % (fed ? 10 : 20) == 0) burn(owner, target, fed ? 3 : 2, 4);
                if (target.isOnFire() && distance > 0.8) {
                    Vec3 inward = ring.center.subtract(target.position()).multiply(1, 0, 1).normalize();
                    push(target, inward.scale(fed ? .35 : .24).add(0, 0.06, 0));
                }
            }
            eatProjectiles(owner, ring.center.add(0, 1, 0), radius, false);
            com.jayptucker.heroesevolved.expansion.PowerReactions.firestorm(owner, ring.level, ring.center, radius);
        }

        MANTLES.values().removeIf(mantle -> now >= mantle.until || !PowerRules.canAct(mantle.owner)
                || !canSustainFire(mantle.owner) || mantle.owner.level() != mantle.level || mantle.owner.isRemoved());
        if (now % 5 == 0) {
            for (Mantle mantle : MANTLES.values()) {
                ServerPlayer owner = mantle.owner;
                addHeat(owner, Math.min(8, eatProjectiles(owner, owner.getEyePosition(), 3, true) * 2));
                PyroGeometry.show(owner.serverLevel(), Piece.PYRO_MANTLE, owner.position(), owner.position(),
                        2.55, isBlue(owner), owner);
                if (now % 20 == 0) {
                    for (LivingEntity target : enemies(owner, owner.position(), 2.5))
                        if (visibleFrom(owner.serverLevel(), owner.position(), target)) burn(owner, target, 1.5F, 3);
                }
            }
        }

        for (var entry : List.copyOf(MINES.entrySet())) {
            List<Mine> mines = entry.getValue();
            mines.removeIf(mine -> now >= mine.until || !PowerRules.canAct(mine.owner) || !canSustainFire(mine.owner)
                    || mine.owner.level() != mine.level || mine.owner.isRemoved());
            for (Mine mine : List.copyOf(mines)) {
                if (!mines.contains(mine)) continue;
                if (!armed(mine, now)) continue;
                Long warning = WARNINGS.get(mine);
                if (warning != null) {
                    if (now >= warning) { mines.remove(mine); erupt(mine, mines, now); }
                    continue;
                }
                boolean triggered = !mine.level.getEntitiesOfClass(LivingEntity.class, new AABB(mine.position, mine.position).inflate(1.6, 1.5, 1.6),
                        target -> hostile(mine.owner, target) && visibleFrom(mine.level, mine.position, target)).isEmpty();
                if (triggered) {
                    warn(mine, now);
                } else if (now % 40 == 0) {
                    PyroGeometry.showPrivate(mine.owner, Piece.PYRO_MINE_ARM, mine.position, .6, isBlue(mine.owner));
                }
            }
            if (mines.isEmpty()) MINES.remove(entry.getKey());
        }

        WARNINGS.keySet().removeIf(mine -> !MINES.getOrDefault(mine.owner.getUUID(), List.of()).contains(mine));

        // Heat cools after three seconds without use: one point every half second.
        if (now % 10 == 0) {
            for (var entry : List.copyOf(HEAT.entrySet())) {
                Heat heat = entry.getValue();
                if (now - heat.lastGain < 60) continue;
                heat.value = Math.max(0, heat.value - 1);
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null) announce(player, heat);
                if (heat.value == 0) HEAT.remove(entry.getKey());
            }
        }
        CUSHIONS.values().removeIf(until -> until < now);
    }

    private static boolean canMaintain(ServerPlayer owner, ServerLevel level) {
        return owner.level() == level && owner.isAlive() && !owner.isRemoved() && PowerRules.canAct(owner) && canSustainFire(owner);
    }

    private static int eatProjectiles(ServerPlayer owner, Vec3 center, double radius, boolean spherical) {
        ServerLevel level = owner.serverLevel();
        int burned = 0;
        for (Projectile shot : level.getEntitiesOfClass(Projectile.class,
                new AABB(center, center).inflate(radius, spherical ? radius : 2, radius), shot -> hostileShot(owner, shot))) {
            Vec3 delta = shot.position().subtract(center);
            if ((spherical ? delta.lengthSqr() : delta.horizontalDistanceSqr()) > radius * radius
                    || !exposed(owner, center, shot.position())) continue;
            if (++burned <= 4) PyroGeometry.show(level, Piece.PYRO_FORGE, shot.position(), shot.position(), .12, isBlue(owner), null);
            shot.discard();
            if (burned == 16) break;
        }
        return burned;
    }

    private static void stopFlames(ServerPlayer player, boolean safeLanding) {
        UUID id = player.getUUID();
        stopPalm(player);
        cancelSpear(player);
        PhoenixService.forget(player);
        PyroFlightService.stop(player, safeLanding);
        if (INFERNOS.remove(id) != null) ArmPosePayload.clear(player);
        SUNS.remove(id);
        RINGS.remove(id);
        COLLAPSES.remove(id);
        MANTLES.remove(id);
        LASHES.remove(id);
        LANCES.remove(id);
    }

    public static void forget(ServerPlayer player) {
        UUID id = player.getUUID();
        stopFlames(player, false);
        PyroCombat.forget(player);
        HEAT.remove(id);
        WARNINGS.keySet().removeIf(mine -> mine.owner == player);
        MINES.remove(id);
        CUSHIONS.remove(id);
    }

    public static void clear() {
        PyroFlightService.clear();
        PyroCombat.clear();
        HEAT.clear();
        SPEARS.clear();
        PALMS.clear();
        INFERNOS.clear();
        SUNS.clear();
        RINGS.clear();
        COLLAPSES.clear();
        MANTLES.clear();
        LASHES.clear();
        LANCES.clear();
        WARNINGS.clear();
        MINES.clear();
        CUSHIONS.clear();
        powerDamageDepth = 0;
    }
}
