package com.jayptucker.heroesevolved.amplify;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.network.AmpStatePayload;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The amplifier's side of Power Amplification (Ando Masahashi's red lightning): Charge, the five keys, the hands that
 * spark on their own, and everything the tree and the classes do with them ({@link AmpSkills} holds the tree's actives).
 *
 * <p>Charge (0 to 100) is red energy held in the hands. It drifts up to 25 on its own, grows when anyone uses a power
 * within 16 blocks (friend or foe) and fastest while a ball of it is held between cupped hands, and falls back toward 25
 * when nothing has happened near for ten seconds. Red Lightning, Van Full and an Overcharge spend it. Above 80 the hands
 * spark on their own.</p>
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class AmpService {
    /** State flags sent to watchers: a ball held, hands sparking on their own, showing off, a Thousand Times gathering. */
    public static final int CHARGING = 1, SPARKING = 2, SHOWING = 4, GATHERING = 8;
    public static final int MAX_CHARGE = 100, REST = 25, OVERCHARGE_COST = 50, HOLD_TICKS = 40, BEAM_HOLD = 16, AUTO_FIRE = 80;
    public static final double REACH = 3.5, PARTNER_REACH = 16, THOUSAND_RADIUS = 10, SPARKS_AT = 80;
    public static final int THOUSAND_WINDUP = 40, THOUSAND_LOCK = 400, JUMPSTART_TICKS = 600;
    private static final String PARTNER = HeroesEvolved.MOD_ID + "_amp_partner";
    static final String GUN_ROUNDS = HeroesEvolved.MOD_ID + "_amp_rounds";
    private static final ResourceLocation MOUNT_BOOST = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "amp_jumpstart_mount");

    static final class Amp {
        final ServerPlayer player;
        float charge = REST;
        long lastFelt = -1000, pressStart = -1, gatherStart = -1, thousandAt = -1, lockUntil = -1, nextSpark, cannonAt = -1;
        LivingEntity pressTarget;
        float windupDamage;
        boolean showing, primed;
        int sentCharge = -100, sentFlags = -1;
        Vec3 partnerLast;
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> partnerLevel;
        final Map<UUID, Long> impressed = new HashMap<>();

        Amp(ServerPlayer player) { this.player = player; }
    }

    private record Hot(ServerLevel level, BlockPos pos, long until) {}
    private record Mount(LivingEntity mount, long until) {}

    private static final Map<UUID, Amp> AMPS = new HashMap<>();
    private static final List<Hot> FURNACES = new ArrayList<>();
    private static final List<Mount> MOUNTS = new ArrayList<>();
    private static final Set<UUID> FOLLOWING = new HashSet<>();

    private AmpService() {}

    static long now(ServerPlayer p) { return p.serverLevel().getGameTime(); }

    static Amp amp(ServerPlayer p) {
        Amp a = AMPS.get(p.getUUID());
        if (a == null || a.player != p) { a = new Amp(p); AMPS.put(p.getUUID(), a); }
        return a;
    }

    public static boolean isAmplifier(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower()
                .filter(e -> e.getKey().equals(ModAbilities.AMPLIFICATION_ID) && e.getValue().isUnlocked()).isPresent();
    }

    // ------------------------------------------------------------------
    // Charge
    // ------------------------------------------------------------------

    public static int charge(ServerPlayer p) { Amp a = AMPS.get(p.getUUID()); return a == null ? REST : Math.round(a.charge); }

    public static void addCharge(ServerPlayer p, float amount) {
        Amp a = amp(p);
        a.charge = Math.clamp(a.charge + amount, 0, MAX_CHARGE);
        if (amount > 0) a.lastFelt = now(p);
        sync(a, false);
    }

    /** Spends up to {@code amount} Charge; returns how much was spent. */
    static float spend(ServerPlayer p, float amount) {
        Amp a = amp(p);
        float spent = Math.min(a.charge, Math.max(0, amount));
        a.charge -= spent;
        sync(a, false);
        return spent;
    }

    /** A power was used near: every amplifier within 16 blocks (not the one who used it) feels it. */
    static void felt(ServerLevel level, LivingEntity user, int amount) {
        if (AMPS.isEmpty()) return;
        for (Amp a : AMPS.values()) {
            ServerPlayer p = a.player;
            if (p == user || p.isRemoved() || p.serverLevel() != level || p.distanceToSqr(user) > 16 * 16) continue;
            if (!isAmplifier(p)) continue;
            a.charge = Math.min(MAX_CHARGE, a.charge + amount);
            a.lastFelt = level.getGameTime();
        }
    }

    // ------------------------------------------------------------------
    // Who is an ally
    // ------------------------------------------------------------------

    /** A body red lightning can amplify for {@code p}: a friend, their pet, or a friendly gifted villager. Never {@code p}. */
    public static boolean ally(ServerPlayer p, LivingEntity e) {
        if (e == p || !e.isAlive() || e.isSpectator() || e instanceof ArmorStand) return false;
        if (GiftedMobs.isGood(e)) return true;
        if (e instanceof Enemy && !(e instanceof net.minecraft.world.entity.OwnableEntity)) return false;
        return StyleOps.isFriend(p, e);
    }

    /** The ally {@code p} is reaching for: their partner across a room (with Partner), or anyone at arm's reach. */
    static LivingEntity aimAlly(ServerPlayer p) {
        LivingEntity partner = partner(p);
        if (partner != null && AmpSkills.has(p, AmpSkills.PARTNER) && partner.level() == p.level()
                && partner.distanceToSqr(p) <= PARTNER_REACH * PARTNER_REACH) {
            LivingEntity seen = Targeting.livingEntityInRange(p, PARTNER_REACH, ClipContext.Fluid.NONE, e -> e == partner);
            if (seen != null) return seen;
        }
        return Targeting.livingEntityInRange(p, REACH, ClipContext.Fluid.NONE, e -> ally(p, e));
    }

    /** The partner {@code p} has bonded with (Partner), if they are in this world. */
    static LivingEntity partner(ServerPlayer p) {
        var kept = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!kept.hasUUID(PARTNER)) return null;
        Entity e = p.serverLevel().getEntity(kept.getUUID(PARTNER));
        if (e == null && p.getServer() != null) e = p.getServer().getPlayerList().getPlayer(kept.getUUID(PARTNER));
        return e instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    public static void bond(ServerPlayer p, LivingEntity partner) {
        var kept = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        kept.putUUID(PARTNER, partner.getUUID());
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, kept);
    }

    static boolean isPartner(ServerPlayer p, LivingEntity e) {
        var kept = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return kept.hasUUID(PARTNER) && kept.getUUID(PARTNER).equals(e.getUUID());
    }

    static boolean following(ServerPlayer p) { return FOLLOWING.contains(p.getUUID()); }

    // ------------------------------------------------------------------
    // Supercharge (slot 1)
    // ------------------------------------------------------------------

    public static boolean canCast(ServerPlayer p) { return PowerRules.canAct(p); }

    public static boolean canSupercharge(AbilityUseContext c) {
        ServerPlayer p = c.player();
        if (!PowerRules.canAct(p)) return false;
        if (now(p) < amp(p).lockUntil) return explain(p, "spent");
        if (aimAlly(p) == null) return explain(p, "no_ally");
        return true;
    }

    /** The press: a zap leaves the hand. A release before two seconds is a Surge; held, an Overcharge. */
    public static boolean supercharge(AbilityUseContext c) {
        ServerPlayer p = c.player();
        LivingEntity target = aimAlly(p);
        if (target == null) return false;
        Amp a = amp(p);
        a.pressTarget = target;
        a.pressStart = now(p);
        ArmPosePayload.show(p, ArmPose.AMP_ZAP, HOLD_TICKS + 4);
        zap(p, target, .6F);
        return true;
    }

    public static void releaseSupercharge(AbilityUseContext c) {
        Amp a = AMPS.get(c.player().getUUID());
        if (a == null || a.pressTarget == null) return;
        LivingEntity target = a.pressTarget;
        a.pressTarget = null;
        a.pressStart = -1;
        surge(c.player(), target);
    }

    /** A Surge from {@code p} to {@code target} (tests call this directly). */
    public static boolean surge(ServerPlayer p, LivingEntity target) {
        if (target == p || !target.isAlive()) return false;
        Amplification.surge(p, target, Amplification.SURGE_TICKS);
        com.jayptucker.heroesevolved.progression.EvolutionTrials.advance(p, ModAbilities.AMPLIFICATION_ID);
        zap(p, target, 1);
        sound(p, SoundEvents.BEACON_POWER_SELECT, .7F, 1.8F);
        if (AmpSkills.has(p, AmpSkills.SECOND_WIND)) amp(p).primed = true;
        ArmPosePayload.show(p, ArmPose.AMP_ZAP, 8);
        return true;
    }

    /** An Overcharge from {@code p}: spends 50 Charge, or falls back to a Surge without it. */
    public static boolean overcharge(ServerPlayer p, LivingEntity target) {
        if (target == p || !target.isAlive()) return false;
        if (amp(p).charge < OVERCHARGE_COST) {
            explain(p, "low_charge");
            return surge(p, target);
        }
        spend(p, OVERCHARGE_COST);
        Amplification.overcharge(p, target, false);
        zap(p, target, 1.6F);
        if (AmpSkills.has(p, AmpSkills.SECOND_WIND)) amp(p).primed = true;
        sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, .7F, 1.6F);
        ArmPosePayload.show(p, ArmPose.AMP_ZAP, 12);
        return true;
    }

    static void zap(ServerPlayer p, LivingEntity target, float strength) {
        SetPiecePayload.send(p.serverLevel(), Piece.AMP_ZAP, AmpGeometry.hand(p), target.getBoundingBox().getCenter(),
                strength, p, target.getId());
    }

    // ------------------------------------------------------------------
    // Red Lightning (slot 2)
    // ------------------------------------------------------------------

    /** The press: a ball of red energy starts to grow between cupped hands. */
    public static boolean gather(AbilityUseContext c) {
        Amp a = amp(c.player());
        a.gatherStart = now(c.player());
        ArmPosePayload.show(c.player(), ArmPose.AMP_CUP, 20);
        sound(c.player(), SoundEvents.BEACON_AMBIENT, .7F, 1.7F);
        sync(a, true);
        return true;
    }

    public static void releaseLightning(AbilityUseContext c) { fire(c.player()); }

    /** Lets the ball go: a bolt after a short hold, a beam after a long one. */
    static void fire(ServerPlayer p) {
        Amp a = AMPS.get(p.getUUID());
        if (a == null || a.gatherStart < 0) return;
        long held = now(p) - a.gatherStart;
        a.gatherStart = -1;
        sync(a, true);
        boolean primed = a.primed;
        a.primed = false;
        if (held >= BEAM_HOLD) beam(p, primed);
        else bolt(p, primed);
    }

    static int tier(ServerPlayer p) { return Math.max(1, com.jayptucker.heroesevolved.progression.PlayerProgressionService.getLevel(p)); }

    static double boltRange(ServerPlayer p) {
        return 6 + 2 * (tier(p) - 1) + (AmpSkills.has(p, AmpSkills.BIGGER_BALL) ? 4 : 0);
    }

    /** A thrown bolt: the first foe along the aim, knocked down and stunned. Spends up to 20 Charge for more. */
    public static boolean bolt(ServerPlayer p, boolean primed) {
        float spent = primed ? 20 : spend(p, 20);
        float damage = 4 + tier(p) + spent / 20F * 3;
        if (AmpSkills.has(p, AmpSkills.SECOND_COMING)) damage *= 2;
        Vec3 eye = p.getEyePosition(), aim = p.getLookAngle().normalize();
        double range = boltRange(p);
        Vec3 end = StyleHits.reach(p, eye, aim, range);
        burnThrough(p, eye, end);
        LivingEntity hit = StyleHits.firstInTube(p, eye, aim, eye.distanceTo(end), .6, false);
        Vec3 to = hit != null ? hit.getBoundingBox().getCenter() : end;
        SetPiecePayload.send(p.serverLevel(), Piece.AMP_BOLT, AmpGeometry.hand(p), to, 1 + spent / 20F, p, p.getRandom().nextInt());
        ArmPosePayload.show(p, ArmPose.AMP_THROW, 8);
        sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, .55F, 1.9F);
        if (hit != null) {
            redHit(p, hit, damage, 24);
            StyleHits.shove(hit, aim.scale(.6).add(0, .25, 0));
        }
        return true;
    }

    /** A held beam through everything in a line: spends all Charge for more. */
    public static boolean beam(ServerPlayer p, boolean primed) {
        float spent = primed ? Math.max(amp(p).charge, 50) : spend(p, MAX_CHARGE);
        float damage = 6 + 1.5F * tier(p) + spent / 10F;
        boolean lethal = AmpSkills.has(p, AmpSkills.SECOND_COMING);
        if (lethal) damage *= 2;
        Vec3 eye = p.getEyePosition(), aim = p.getLookAngle().normalize();
        double range = boltRange(p) + 4;
        Vec3 end = StyleHits.reach(p, eye, aim, range);
        burnThrough(p, eye, end);
        double length = eye.distanceTo(end);
        for (LivingEntity e : line(p, eye, aim, length, .9, lethal)) {
            redHit(p, e, damage, 40);
            StyleHits.shove(e, aim.scale(1.1).add(0, .3, 0));
        }
        SetPiecePayload.send(p.serverLevel(), Piece.AMP_BEAM, AmpGeometry.hand(p), end, 1 + spent / 40F, p, p.getRandom().nextInt());
        ArmPosePayload.show(p, ArmPose.AMP_BEAM, 12);
        sound(p, SoundEvents.TRIDENT_THUNDER.value(), .8F, 1.4F);
        CameraShakePayload.sendAround(p.serverLevel(), p.position(), 16, .12F + spent / 600F, 5);
        return true;
    }

    /** Everything a beam passes through (foes; with The Second Coming, supercharged friends too). */
    static List<LivingEntity> line(ServerPlayer p, Vec3 eye, Vec3 aim, double length, double width, boolean friendsToo) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye.add(aim.scale(length))).inflate(width + 1),
                e -> e != p && e.isAlive() && (StyleOps.isFoe(p, e) || friendsToo && Amplification.surged(e)))) {
            Vec3 d = e.getBoundingBox().getCenter().subtract(eye);
            double along = d.dot(aim);
            if (along < 0 || along > length) continue;
            if (d.subtract(aim.scale(along)).length() > width + e.getBbWidth() * .5) continue;
            out.add(e);
        }
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        return out;
    }

    /**
     * A red lightning hit: through armor, knocking the target down and stunning it. It knocks out rather than kills
     * (leaving half a heart) unless the amplifier has stopped holding back (The Second Coming).
     */
    static void redHit(ServerPlayer p, LivingEntity target, float damage, int stun) {
        boolean lethal = AmpSkills.has(p, AmpSkills.SECOND_COMING);
        if (AmpSkills.has(p, AmpSkills.BURN_THROUGH) && target instanceof ServerPlayer shielded
                && com.jayptucker.heroesevolved.invisibility.ForceFieldService.shielding(shielded)) damage *= 2;
        if (!lethal) damage = Math.min(damage, target.getHealth() - 1);
        if (damage > 0) StyleHits.hurt(target, p.damageSources().indirectMagic(p, p), damage);
        stun(target, stun);
    }

    /** Burn Through: red lightning breaks the constructs in its path (ice walls, force barriers, bridges). */
    static void burnThrough(ServerPlayer p, Vec3 from, Vec3 to) {
        if (!AmpSkills.has(p, AmpSkills.BURN_THROUGH)) return;
        ServerLevel level = p.serverLevel();
        double length = from.distanceTo(to) + 1.5;
        Vec3 step = to.subtract(from).normalize().scale(.5);
        Vec3 at = from;
        for (double d = 0; d <= length; d += .5, at = at.add(step)) {
            BlockPos pos = BlockPos.containing(at);
            if (com.jayptucker.heroesevolved.cryo.TemporaryBlocks.isTemporary(level, pos))
                com.jayptucker.heroesevolved.cryo.TemporaryBlocks.restore(level, pos, p);
        }
    }

    /** Knocked down and dazed: slow, weak, and a creature forgets what it was doing. */
    public static void stun(LivingEntity target, int ticks) {
        if (ticks <= 0 || !target.isAlive()) return;
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 4, false, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1, false, false, true));
        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
    }

    // ------------------------------------------------------------------
    // Shock Therapy (slot 3)
    // ------------------------------------------------------------------

    public static boolean shockTherapy(AbilityUseContext c) {
        ServerPlayer p = c.player();
        double reach = AmpSkills.has(p, AmpSkills.STEADY_HANDS) ? 4 : 2.5;
        LivingEntity target = Targeting.livingEntityInRange(p, reach, ClipContext.Fluid.NONE, e -> ally(p, e));
        if (target == null) target = p;
        shock(p, target);
        return true;
    }

    /**
     * Shock Therapy's jolt: clears mind control (a Suggestion, a possession, fright and confusion), stuns and slows, and
     * brings a downed Regenerator straight back up. With Steady Hands it also clears a dampener's hold and fire.
     */
    public static int shock(ServerPlayer p, LivingEntity target) {
        int cleared = 0;
        for (var effect : List.of(MobEffects.CONFUSION, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.BLINDNESS, MobEffects.DARKNESS,
                MobEffects.WEAKNESS, MobEffects.DIG_SLOWDOWN))
            if (target.removeEffect(effect)) cleared++;
        if (target instanceof Mob mob && com.jayptucker.heroesevolved.telepathy.SuggestionService.release(mob)) cleared++;
        if (com.jayptucker.heroesevolved.shapeshift.ShapeshiftTricks.calm(target)) cleared++;
        if (target instanceof ServerPlayer friend) {
            if (com.jayptucker.heroesevolved.regeneration.BodyService.revive(friend)) cleared++;
            com.jayptucker.heroesevolved.telepathy.PossessionService.forget(friend);
        }
        if (AmpSkills.has(p, AmpSkills.STEADY_HANDS)) {
            if (target.removeEffect(ModItems.DAMPENED)) cleared++;
            if (target.isOnFire()) { target.clearFire(); cleared++; }
        }
        SetPiecePayload.send(p.serverLevel(), Piece.AMP_SHOCK, target.position(), target.getEyePosition(),
                Math.max(.6F, target.getBbHeight()), target, p.getId());
        ArmPosePayload.show(p, target == p ? ArmPose.AMP_TEMPLES : ArmPose.AMP_ZAP, 14);
        sound(p, target.position(), SoundEvents.BEACON_DEACTIVATE, .8F, 1.9F);
        if (target instanceof ServerPlayer friend && friend != p)
            friend.displayClientMessage(Component.translatable("amp.heroesevolved.shocked").withStyle(ChatFormatting.RED), true);
        return cleared;
    }

    // ------------------------------------------------------------------
    // Jumpstart (slot 4)
    // ------------------------------------------------------------------

    public static boolean canJumpstart(AbilityUseContext c) {
        ServerPlayer p = c.player();
        if (!PowerRules.canAct(p)) return false;
        if (jumpable(p, aimBlock(p, 5)) || p.getVehicle() instanceof LivingEntity || AmpSkills.has(p, AmpSkills.MECHANIC) && gun(p.getMainHandItem()))
            return true;
        return explain(p, "nothing_to_start");
    }

    public static boolean jumpstart(AbilityUseContext c) {
        ServerPlayer p = c.player();
        int ticks = AmpSkills.has(p, AmpSkills.MECHANIC) ? JUMPSTART_TICKS * 2 : JUMPSTART_TICKS;
        BlockPos pos = aimBlock(p, 5);
        if (jumpable(p, pos)) return jumpBlock(p, pos, ticks);
        if (p.getVehicle() instanceof LivingEntity mount) {
            boostMount(mount, ticks / 3);
            SetPiecePayload.send(p.serverLevel(), Piece.AMP_JUMP, AmpGeometry.hand(p), mount.getBoundingBox().getCenter(), 1, p);
            ArmPosePayload.show(p, ArmPose.AMP_PALM, 12);
            sound(p, SoundEvents.HORSE_ANGRY, .8F, 1.2F);
            return true;
        }
        if (AmpSkills.has(p, AmpSkills.MECHANIC) && gun(p.getMainHandItem())) {
            CustomData.update(DataComponents.CUSTOM_DATA, p.getMainHandItem(), tag -> tag.putInt(GUN_ROUNDS, 30));
            SetPiecePayload.send(p.serverLevel(), Piece.AMP_JUMP, AmpGeometry.hand(p), AmpGeometry.hand(p).add(p.getLookAngle().scale(.4)), .6F, p);
            ArmPosePayload.show(p, ArmPose.AMP_PALM, 12);
            sound(p, SoundEvents.BEACON_POWER_SELECT, .8F, 2F);
            return true;
        }
        return false;
    }

    static BlockPos aimBlock(ServerPlayer p, double reach) {
        BlockHitResult hit = p.serverLevel().clip(new ClipContext(p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(reach)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
    }

    /** Whether Jumpstart can wake this block: a door, gate, trapdoor, lever, button, furnace or energy store. */
    static boolean jumpable(ServerPlayer p, BlockPos pos) {
        if (pos == null || !p.mayInteract(p.serverLevel(), pos)) return false;
        ServerLevel level = p.serverLevel();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof LeverBlock || state.getBlock() instanceof ButtonBlock) return true;
        if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity) return true;
        return level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null) != null;
    }

    /** Wakes one block: doors and gates swing, levers and buttons fire, furnaces run four times as fast, batteries fill. */
    public static boolean jumpBlock(ServerPlayer p, BlockPos pos, int ticks) {
        ServerLevel level = p.serverLevel();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock door) {
            door.setOpen(p, level, state, pos, !state.getValue(BlockStateProperties.OPEN));
        } else if (state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock) {
            boolean open = !state.getValue(BlockStateProperties.OPEN);
            level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, open), 10);
            level.playSound(null, pos, open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1, 1);
        } else if (state.getBlock() instanceof LeverBlock lever) {
            lever.pull(state, level, pos, p);
        } else if (state.getBlock() instanceof ButtonBlock button) {
            button.press(state, level, pos, p);
        } else if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity) {
            FURNACES.removeIf(h -> h.level == level && h.pos.equals(pos));
            FURNACES.add(new Hot(level, pos.immutable(), level.getGameTime() + ticks));
        } else {
            var storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
            if (storage == null) return false;
            storage.receiveEnergy(4000, false);
        }
        SetPiecePayload.send(level, Piece.AMP_JUMP, AmpGeometry.hand(p), Vec3.atCenterOf(pos), 1, p);
        ArmPosePayload.show(p, ArmPose.AMP_PALM, 12);
        sound(p, Vec3.atCenterOf(pos), SoundEvents.BEACON_POWER_SELECT, .9F, 1.6F);
        return true;
    }

    /** Jumpstart's machines, without a hand on them (Lights Back On, Technopathy's overcharge): furnaces and batteries near. */
    static int jumpMachines(ServerPlayer p, Vec3 center, int radius, int ticks) {
        ServerLevel level = p.serverLevel();
        int started = 0;
        BlockPos base = BlockPos.containing(center);
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-radius, -radius / 2, -radius), base.offset(radius, radius / 2, radius))) {
            if (started >= 48 || !level.isLoaded(pos)) continue;
            if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity) {
                BlockPos at = pos.immutable();
                FURNACES.removeIf(h -> h.level == level && h.pos.equals(at));
                FURNACES.add(new Hot(level, at, level.getGameTime() + ticks));
                started++;
            } else if (level.getBlockEntity(pos) != null) {
                var storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
                if (storage != null) { storage.receiveEnergy(4000, false); started++; }
            }
        }
        return started;
    }

    static boolean jumpstarted(ServerLevel level, BlockPos pos) {
        return FURNACES.stream().anyMatch(h -> h.level == level && h.pos.equals(pos));
    }

    private static void boostMount(LivingEntity mount, int ticks) {
        var speed = mount.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.addOrUpdateTransientModifier(new AttributeModifier(MOUNT_BOOST, 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        MOUNTS.removeIf(m -> m.mount == mount);
        MOUNTS.add(new Mount(mount, mount.level().getGameTime() + ticks));
    }

    /** A TaCZ gun (Mechanic charges one). */
    static boolean gun(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("tacz");
    }

    // ------------------------------------------------------------------
    // A Thousand Times (slot 5)
    // ------------------------------------------------------------------

    public static boolean thousandTimes(AbilityUseContext c) {
        ServerPlayer p = c.player();
        Amp a = amp(p);
        a.thousandAt = now(p) + THOUSAND_WINDUP;
        a.windupDamage = 0;
        ArmPosePayload.show(p, ArmPose.AMP_RAISE, THOUSAND_WINDUP + 4);
        SetPiecePayload.send(p.serverLevel(), Piece.AMP_GATHER, p.position(), p.position().add(0, 3, 0), (float) THOUSAND_RADIUS, p);
        sound(p, SoundEvents.BEACON_ACTIVATE, 1.2F, 1.5F);
        sync(a, true);
        return true;
    }

    /** The release: every ally within ten blocks overcharged at once, with no backlash. Then the hands are spent. */
    public static int thousandRelease(ServerPlayer p) {
        Amp a = amp(p);
        a.thousandAt = -1;
        int count = 0;
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(THOUSAND_RADIUS),
                e -> ally(p, e) && e.distanceToSqr(p) <= THOUSAND_RADIUS * THOUSAND_RADIUS)) {
            Amplification.overcharge(p, e, true);
            zap(p, e, 1.8F);
            count++;
        }
        a.charge = 0;
        a.lockUntil = now(p) + THOUSAND_LOCK;
        SetPiecePayload.send(p.serverLevel(), Piece.AMP_WAVE, p.position(), p.position().add(0, 1, 0), (float) THOUSAND_RADIUS, p);
        sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2F, 1.5F);
        CameraShakePayload.sendAround(p.serverLevel(), p.position(), 24, .25F, 8);
        p.displayClientMessage(Component.translatable("amp.heroesevolved.thousand", count).withStyle(ChatFormatting.RED), true);
        sync(a, true);
        return count;
    }

    // ------------------------------------------------------------------
    // Misfires (Overcharge the Enemy)
    // ------------------------------------------------------------------

    /** An overloaded body's move goes wild: it hurts itself, and its power lashes at whatever stands near it. */
    static void misfire(ServerLevel level, LivingEntity body) {
        body.invulnerableTime = 0;
        body.hurt(body.damageSources().magic(), 4);
        for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, body.getBoundingBox().inflate(3),
                e -> e != body && e.isAlive() && !(e instanceof Player player && (player.isCreative() || player.isSpectator())))) {
            if (body instanceof Mob mob && GiftedMobs.of(mob) != null && com.jayptucker.heroesevolved.gifted.MobMoves.foe(GiftedMobs.of(mob), near))
                continue;
            if (body instanceof Player) continue;
            near.invulnerableTime = 0;
            near.hurt(body.damageSources().magic(), 3);
        }
        SetPiecePayload.send(level, Piece.AMP_WILD, body.position(), body.getEyePosition(), Math.max(.6F, body.getBbHeight()), body);
        level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, .5F, 1.8F);
        if (body instanceof ServerPlayer player)
            player.displayClientMessage(Component.translatable("amp.heroesevolved.misfire").withStyle(ChatFormatting.DARK_RED), true);
    }

    // ------------------------------------------------------------------
    // The awakening
    // ------------------------------------------------------------------

    /**
     * The power wakes: with someone else in danger, a hand on their shoulder and their power overcharges, with no
     * backlash, to save them. Alone, red sparks burst out of both hands with no control, throwing every threat back.
     */
    public static void awaken(ServerPlayer p, LivingEntity saved, List<LivingEntity> threats) {
        if (saved != null && saved != p && saved.isAlive()) {
            Amplification.overcharge(p, saved, true);
            zap(p, saved, 2);
        }
        for (LivingEntity t : threats) {
            if (!t.isAlive()) continue;
            t.invulnerableTime = 0;
            t.hurt(p.damageSources().indirectMagic(p, p), 4);
            StyleOps.shove(t, p.position(), 1.2, .45);
            stun(t, 40);
        }
        amp(p).charge = MAX_CHARGE;
        SetPiecePayload.send(p.serverLevel(), Piece.AMP_AWAKEN, p.position(), p.position().add(0, 2, 0), 6, p);
        ArmPosePayload.show(p, ArmPose.AMP_RAISE, 24);
        sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 1F, 1.6F);
        CameraShakePayload.sendAround(p.serverLevel(), p.position(), 24, .3F, 8);
    }

    // ------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        if (!FURNACES.isEmpty()) {
            for (Hot hot : List.copyOf(FURNACES)) {
                if (hot.level.getGameTime() >= hot.until || !hot.level.isLoaded(hot.pos)
                        || !(hot.level.getBlockEntity(hot.pos) instanceof AbstractFurnaceBlockEntity furnace)) {
                    FURNACES.remove(hot);
                    continue;
                }
                BlockState state = hot.level.getBlockState(hot.pos);
                for (int i = 0; i < 3; i++) AbstractFurnaceBlockEntity.serverTick(hot.level, hot.pos, state, furnace);
            }
        }
        if (!MOUNTS.isEmpty()) {
            for (Mount m : List.copyOf(MOUNTS)) {
                if (m.mount.isRemoved() || m.mount.level().getGameTime() >= m.until) {
                    var speed = m.mount.getAttribute(Attributes.MOVEMENT_SPEED);
                    if (speed != null) speed.removeModifier(MOUNT_BOOST);
                    MOUNTS.remove(m);
                }
            }
        }
        FOLLOWING.clear();
        for (Amp a : List.copyOf(AMPS.values())) {
            ServerPlayer p = a.player;
            if (p.isRemoved() && !(p instanceof net.neoforged.neoforge.common.util.FakePlayer)) { AMPS.remove(p.getUUID()); continue; }
            try {
                step(a);
            } catch (RuntimeException broken) {
                HeroesEvolved.LOGGER.error("Power Amplification failed for {}", p.getName().getString(), broken);
            }
        }
    }

    private static void step(Amp a) {
        ServerPlayer p = a.player;
        long now = now(p);
        // A held Supercharge becomes an Overcharge at two seconds (or a plain Surge without the Charge for one).
        if (a.pressTarget != null && now - a.pressStart >= HOLD_TICKS) {
            LivingEntity target = a.pressTarget;
            a.pressTarget = null;
            a.pressStart = -1;
            if (target.isAlive() && target.level() == p.level() && target.distanceToSqr(p) <= 20 * 20) overcharge(p, target);
        }
        // The ball between cupped hands grows, and lets go on its own after four seconds.
        if (a.gatherStart >= 0) {
            boolean big = AmpSkills.has(p, AmpSkills.BIGGER_BALL) && a.charge >= 50;
            a.charge = Math.min(MAX_CHARGE, a.charge + (big ? 1F : .5F));
            if ((now - a.gatherStart) % 10 == 0) ArmPosePayload.show(p, ArmPose.AMP_CUP, 20);
            if (now - a.gatherStart >= AUTO_FIRE || !PowerRules.canAct(p)) fire(p);
        }
        // A Thousand Times gathers, then lets go.
        if (a.thousandAt >= 0) {
            if ((a.thousandAt - now) % 10 == 0) ArmPosePayload.show(p, ArmPose.AMP_RAISE, 14);
            if (!PowerRules.canAct(p)) { a.thousandAt = -1; sync(a, true); }
            else if (now >= a.thousandAt) thousandRelease(p);
        }
        AmpSkills.tick(p, a, now);
        // Charge drifts toward rest: up when there is little, down when nothing has happened near for ten seconds.
        if (a.gatherStart < 0) {
            if (a.charge < REST) a.charge = Math.min(REST, a.charge + .05F);
            else if (a.charge > REST && now - a.lastFelt > 200) a.charge = Math.max(REST, a.charge - .025F);
        }
        // Too much Charge: the hands spark on their own (harmless, but it gives you away).
        if (a.charge > SPARKS_AT && !AmpSkills.has(p, AmpSkills.GROUNDED) && now >= a.nextSpark && isAmplifier(p)) {
            a.nextSpark = now + 40 + p.getRandom().nextInt(40);
            LivingEntity near = p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(4), e -> e != p && e.isAlive())
                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(p))).orElse(null);
            Vec3 to = near != null ? near.getBoundingBox().getCenter()
                    : p.position().add(p.getRandom().nextGaussian() * 1.6, .2, p.getRandom().nextGaussian() * 1.6);
            SetPiecePayload.send(p.serverLevel(), Piece.AMP_ZAP, AmpGeometry.hand(p), to, .35F, p, near != null ? near.getId() : -1);
            sound(p, SoundEvents.BEACON_POWER_SELECT, .35F, 2F);
        }
        if (now % 2 == 0) sync(a, false);
    }

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    /** Grounding and Opposite Current on the amplifier; a Thousand Times broken by a heavy blow. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void incoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Amp a = AMPS.get(p.getUUID());
        if (a == null) return;
        float amount = event.getAmount();
        Entity attacker = event.getSource().getEntity();
        if (AmpSkills.has(p, AmpSkills.OPPOSITE_CURRENT) && a.charge >= 20 && (event.getSource().is(DamageTypeTags.IS_LIGHTNING)
                || attacker instanceof com.jayptucker.heroesevolved.story.elle.ElleEntity
                || attacker instanceof ServerPlayer shooter && electro(shooter)
                || attacker instanceof LivingEntity mob && ModAbilities.ELECTROKINESIS_ID.equals(GiftedMobs.powerOf(mob)))) {
            float halved = amount * .5F;
            a.charge = Math.min(MAX_CHARGE, a.charge + halved * 3);
            amount -= halved;
        }
        if (AmpSkills.has(p, AmpSkills.GROUNDED) && attacker instanceof LivingEntity source && powered(source)) {
            float grounded = amount * .2F;
            a.charge = Math.min(MAX_CHARGE, a.charge + grounded * 4);
            amount -= grounded;
        }
        if (amount != event.getAmount()) event.setAmount(amount);
        if (a.thousandAt >= 0) {
            a.windupDamage += amount;
            if (a.windupDamage >= 6) {
                a.thousandAt = -1;
                p.displayClientMessage(Component.translatable("amp.heroesevolved.thousand_broken").withStyle(ChatFormatting.RED), true);
                ArmPosePayload.clear(p);
                sync(a, true);
            }
        }
    }

    private static boolean electro(ServerPlayer p) {
        return PlayerAbilityService.getData(p).assignedPower().filter(e -> e.getKey().equals(ModAbilities.ELECTROKINESIS_ID)).isPresent();
    }

    /** Whether a body carries a power: a player with one, a gifted mob, or a story fighter. */
    public static boolean powered(LivingEntity e) {
        if (e instanceof ServerPlayer player) return PlayerAbilityService.getData(player).assignedPower().filter(x -> x.getValue().isUnlocked()).isPresent();
        return GiftedMobs.isGifted(e) || e instanceof com.jayptucker.heroesevolved.story.sylar.SylarEntity
                || e instanceof com.jayptucker.heroesevolved.story.elle.ElleEntity;
    }

    @SubscribeEvent
    public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer p && event.getEntity() instanceof ServerPlayer viewer) {
            Amp a = AMPS.get(p.getUUID());
            if (a != null) PacketDistributor.sendToPlayer(viewer, new AmpStatePayload(p.getId(), Math.round(a.charge), flags(a)));
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && isAmplifier(p)) sync(amp(p), true);
    }

    /**
     * A respawned player keeps their entity id, so watchers still hold the dead body's hands (a ball, sparks): start
     * over and tell them.
     */
    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Amp old = AMPS.get(p.getUUID());
        if (old != null && old.player != p) AMPS.remove(p.getUUID());
        if (isAmplifier(p)) sync(amp(p), true);
        else if (old != null) cleared(p);
    }

    /** A new world's client knows nothing of the hands yet. */
    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && AMPS.containsKey(p.getUUID())) sync(amp(p), true);
    }

    /** Tells everyone watching that {@code p} holds no Charge at all any more (the client forgets their hands). */
    private static void cleared(ServerPlayer p) {
        if (p.connection != null) PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new AmpStatePayload(p.getId(), -1, 0));
    }

    static int flags(Amp a) {
        return (a.gatherStart >= 0 ? CHARGING : 0) | (a.charge > SPARKS_AT ? SPARKING : 0) | (a.showing ? SHOWING : 0)
                | (a.thousandAt >= 0 ? GATHERING : 0);
    }

    static void sync(Amp a, boolean force) {
        ServerPlayer p = a.player;
        if (p.connection == null) return;
        int charge = Math.round(a.charge), flags = flags(a);
        if (!force && flags == a.sentFlags && Math.abs(charge - a.sentCharge) < 2 && !(charge == 0 && a.sentCharge != 0)
                && !(charge == MAX_CHARGE && a.sentCharge != MAX_CHARGE)) return;
        a.sentCharge = charge;
        a.sentFlags = flags;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new AmpStatePayload(p.getId(), charge, flags));
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    static boolean explain(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable("amp.heroesevolved." + key).withStyle(ChatFormatting.RED), true);
        return false;
    }

    static void sound(ServerPlayer p, SoundEvent sound, float volume, float pitch) { sound(p, p.position(), sound, volume, pitch); }

    static void sound(ServerPlayer p, Vec3 at, SoundEvent sound, float volume, float pitch) {
        p.serverLevel().playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** Villagers who saw Sparks for Show lately, so each is impressed once in a while. */
    static boolean impress(Amp a, Villager villager, long now) {
        Long last = a.impressed.get(villager.getUUID());
        if (last != null && now - last < 12000) return false;
        a.impressed.put(villager.getUUID(), now);
        villager.getGossips().add(a.player.getUUID(), GossipType.MINOR_POSITIVE, 20);
        return true;
    }

    static void following(ServerPlayer p, boolean on) { if (on) FOLLOWING.add(p.getUUID()); }

    public static void forget(ServerPlayer p) {
        if (AMPS.remove(p.getUUID()) != null && !p.isRemoved()) cleared(p);
        AmpSkills.forget(p);
    }

    static void clear() {
        AMPS.clear();
        FURNACES.clear();
        for (Mount m : MOUNTS) {
            var speed = m.mount.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) speed.removeModifier(MOUNT_BOOST);
        }
        MOUNTS.clear();
        AmpSkills.clear();
    }
}
