package com.jayptucker.heroesevolved.regeneration;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.BodyStatePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.time.TemporalParadoxService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Claire Bennet's body. Heavy blade hits and explosions take her limbs, which grow back from the bone
 * out: a stump, then bone, then muscle, then skin. And she cannot be killed: a lethal blow puts her
 * down instead, lying on the ground for a while (longer at low level) before her body repairs itself
 * and she gets back up. The void, /kill, paradoxes, Sylar and a body drained of its blood still kill.
 */
public final class BodyService {
    public enum Limb {
        RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG;

        public boolean arm() { return this == RIGHT_ARM || this == LEFT_ARM; }
        public boolean right() { return this == RIGHT_ARM || this == RIGHT_LEG; }
    }

    /** A missing limb: a stump until {@code growStart}, then it grows back over {@code growTicks}. */
    private static final class Wound {
        long growStart; int growTicks; int stage;
        Wound(long growStart, int growTicks) { this.growStart = growStart; this.growTicks = growTicks; }
        float progress(long now) { return now < growStart ? -1 : Math.min(1, (now - growStart) / (float) growTicks); }
        long end() { return growStart + growTicks; }
    }

    private static final class Body {
        final EnumMap<Limb, Wound> limbs = new EnumMap<>(Limb.class);
        long downedAt = -1, downedUntil = -1, giveUpPressed = -10;
        int giveUp;
        boolean downed() { return downedUntil >= 0; }
    }

    /** A blade hit of at least this much damage takes a limb; so does an explosion. */
    public static final float SEVER_DAMAGE = 7.0F;
    public static final int STUMP_TICKS = 20, GIVE_UP_AFTER = 100, GIVE_UP_HOLD = 60;
    private static final ResourceLocation LEG_SPEED = id("severed_leg_speed"), LEG_JUMP = id("severed_leg_jump"),
            DOWN_SPEED = id("downed_speed"), DOWN_JUMP = id("downed_jump");
    private static final String SAVED_DOWN = HeroesEvolved.MOD_ID + ":downed_remaining";
    private static final Map<UUID, Body> BODIES = new HashMap<>();

    private BodyService() {}

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, path); }

    private static Body body(ServerPlayer player) { return BODIES.computeIfAbsent(player.getUUID(), ignored -> new Body()); }

    // ------------------------------------------------------------------
    // Limbs
    // ------------------------------------------------------------------

    public static boolean missing(ServerPlayer player, Limb limb) {
        Body body = BODIES.get(player.getUUID());
        return body != null && body.limbs.containsKey(limb);
    }

    public static boolean missingAny(ServerPlayer player) {
        Body body = BODIES.get(player.getUUID());
        return body != null && !body.limbs.isEmpty();
    }

    /** The arm that holds {@code hand}. */
    public static Limb arm(ServerPlayer player, InteractionHand hand) {
        HumanoidArm side = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        return side == HumanoidArm.RIGHT ? Limb.RIGHT_ARM : Limb.LEFT_ARM;
    }

    public static boolean canUseHand(ServerPlayer player, InteractionHand hand) {
        return !missing(player, arm(player, hand)) && !isDowned(player);
    }

    /** How long a limb takes to grow back: 12 seconds at Level 1 down to 4 at Level 5. */
    public static int growTicks(int powerLevel) {
        return 20 * Math.max(4, 14 - 2 * Math.max(1, powerLevel));
    }

    /** Would this hit take a limb? A heavy blade or an explosion. */
    public static boolean severs(DamageSource source, float damage) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return damage >= SEVER_DAMAGE;
        if (damage < SEVER_DAMAGE || !(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker) return false;
        var weapon = attacker.getMainHandItem();
        return weapon.is(ItemTags.SWORDS) || weapon.is(ItemTags.AXES);
    }

    /** The intact limb nearest the hit: the side it came from, an arm if it came from above the hips. */
    public static Limb limbFor(ServerPlayer player, DamageSource source) {
        List<Limb> intact = new ArrayList<>();
        for (Limb limb : Limb.values()) if (!missing(player, limb)) intact.add(limb);
        if (intact.isEmpty()) return null;
        Vec3 from = source.getSourcePosition();
        if (from == null || source.is(DamageTypeTags.IS_EXPLOSION))
            return intact.get(player.getRandom().nextInt(intact.size()));
        Vec3 forward = Vec3.directionFromRotation(0, player.yBodyRot);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        boolean rightSide = from.subtract(player.position()).dot(right) >= 0;
        boolean high = from.y > player.getY() + 0.9;
        Limb preferred = high ? (rightSide ? Limb.RIGHT_ARM : Limb.LEFT_ARM) : (rightSide ? Limb.RIGHT_LEG : Limb.LEFT_LEG);
        if (intact.contains(preferred)) return preferred;
        for (Limb limb : intact) if (limb.arm() == preferred.arm()) return limb;
        return intact.getFirst();
    }

    /** Takes a limb. It stays a stump for a second, then grows back. Returns false if it was already gone. */
    public static boolean sever(ServerPlayer player, Limb limb, int powerLevel) {
        Body body = body(player);
        if (limb == null || body.limbs.containsKey(limb)) return false;
        long now = player.serverLevel().getGameTime();
        int grow = growTicks(powerLevel);
        // Stored Regrowth makes the limb come back faster.
        if (RegenerationService.spendRegrowth(player, 10) == 10) grow = (int) (grow * 0.6F);
        long start = now + STUMP_TICKS;
        if (body.downed()) start = Math.max(start, body.downedUntil - 20 - grow);
        body.limbs.put(limb, new Wound(start, grow));
        ServerLevel level = player.serverLevel();
        Vec3 stump = stumpPosition(player, limb);
        AbilityEffects.send(level, Kind.REGEN_SEVER, stump, stump.add(stump.subtract(player.position().add(0, stump.y - player.getY(), 0)).normalize()), 1.0D);
        RegenerationService.bleed(player, 8);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.2F, 0.7F);
        player.displayClientMessage(Component.literal(limb.arm() ? "Your arm is gone. It will grow back." : "Your leg is gone. It will grow back.")
                .withStyle(ChatFormatting.RED), true);
        if (limb.arm() && !canUseHand(player, InteractionHand.MAIN_HAND)) player.stopUsingItem();
        handicaps(player);
        sync(player);
        return true;
    }

    /** Rapid Mending: every missing limb grows back in a second. */
    public static void regrowAll(ServerPlayer player) {
        Body body = BODIES.get(player.getUUID());
        if (body == null || body.limbs.isEmpty()) return;
        long now = player.serverLevel().getGameTime();
        for (Wound wound : body.limbs.values()) {
            float progress = wound.progress(now);
            wound.growTicks = 20;
            wound.growStart = now - (long) (Math.max(0, progress) * 20);
        }
        sync(player);
    }

    private static Vec3 stumpPosition(ServerPlayer player, Limb limb) {
        Vec3 forward = Vec3.directionFromRotation(0, player.yBodyRot);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        double side = limb.arm() ? 0.38 : 0.12;
        return player.position().add(right.scale(limb.right() ? side : -side)).add(0, limb.arm() ? 1.35 : 0.72, 0);
    }

    /** A missing leg slows you and stops you sprinting; both gone, you can barely drag yourself. */
    private static void handicaps(ServerPlayer player) {
        int legs = (missing(player, Limb.RIGHT_LEG) ? 1 : 0) + (missing(player, Limb.LEFT_LEG) ? 1 : 0);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        var jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (speed != null) {
            speed.removeModifier(LEG_SPEED);
            if (legs > 0) speed.addTransientModifier(new AttributeModifier(LEG_SPEED, legs == 2 ? -0.8 : -0.4, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        if (jump != null) {
            jump.removeModifier(LEG_JUMP);
            if (legs > 0) jump.addTransientModifier(new AttributeModifier(LEG_JUMP, legs == 2 ? -0.9 : -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        if (legs > 0) player.setSprinting(false);
    }

    // ------------------------------------------------------------------
    // Downed, never dead
    // ------------------------------------------------------------------

    public static boolean isDowned(ServerPlayer player) {
        Body body = BODIES.get(player.getUUID());
        return body != null && body.downed();
    }

    /** Ticks left on the ground, or 0 if standing. */
    public static int downedRemaining(ServerPlayer player) {
        Body body = BODIES.get(player.getUUID());
        return body == null || !body.downed() ? 0 : (int) Math.max(0, body.downedUntil - player.serverLevel().getGameTime());
    }

    /** How long she stays down: 90 seconds at Level 1, 12 at Level 5. Stored Regrowth cuts up to 40% off. */
    public static int recoveryTicks(int powerLevel, int regrowth) {
        int seconds = switch (Math.clamp(powerLevel, 1, 5)) { case 1 -> 90; case 2 -> 60; case 3 -> 40; case 4 -> 25; default -> 12; };
        return (int) (20 * seconds * (1 - 0.4F * Math.clamp(regrowth, 0, 100) / 100F));
    }

    /** The only things that still kill her: the void and /kill, a paradox, Sylar, and a body drained of its blood. */
    public static boolean trulyKills(ServerPlayer player, DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypes.GENERIC_KILL)
                || source.is(TemporalParadoxService.TEMPORAL_PARADOX_DAMAGE)
                || source.getEntity() instanceof com.jayptucker.heroesevolved.story.sylar.SylarEntity
                || player.hasEffect(ModItems.DRAINED);
    }

    /** A lethal blow: she goes down instead of dying. Returns false if this blow really kills her. */
    public static boolean goDown(ServerPlayer player, int powerLevel, DamageSource source) {
        if (trulyKills(player, source)) return false;
        Body body = body(player);
        if (body.downed()) { player.setHealth(1.0F); return true; }
        int regrowth = RegenerationService.regrowth(player);
        int ticks = recoveryTicks(powerLevel, regrowth);
        RegenerationService.spendRegrowth(player, regrowth);
        down(player, ticks);
        RegenerationService.bleed(player, 12);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.sendSystemMessage(Component.literal("You're down. Your body is already repairing itself. ("
                + ticks / 20 + "s)").withStyle(ChatFormatting.DARK_RED));
        return true;
    }

    private static void down(ServerPlayer player, int ticks) {
        Body body = body(player);
        long now = player.serverLevel().getGameTime();
        body.downedAt = now;
        body.downedUntil = now + ticks;
        body.giveUp = 0;
        player.setHealth(1.0F);
        player.clearFire();
        player.stopRiding();
        player.stopUsingItem();
        player.resetFallDistance();
        player.setSprinting(false);
        player.setForcedPose(Pose.SLEEPING);
        // Limbs grow back while she lies there, finishing just before she gets up.
        for (Wound wound : body.limbs.values()) wound.growStart = Math.max(wound.growStart, body.downedUntil - 20 - wound.growTicks);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        var jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (speed != null) speed.addOrUpdateTransientModifier(new AttributeModifier(DOWN_SPEED, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        if (jump != null) jump.addOrUpdateTransientModifier(new AttributeModifier(DOWN_JUMP, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        forgetHer(player);
        ScreenTintPayload.send(player, 0x90200000, ticks, ScreenTintPayload.Style.HOLD);
        sync(player);
    }

    /** Everything hunting her thinks she's dead and walks away. */
    private static void forgetHer(ServerPlayer player) {
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48), mob -> mob.getTarget() == player)) {
            mob.setTarget(null);
            if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            mob.getNavigation().stop();
        }
    }

    /** Gets her back up: limbs whole, some health back, and out of whatever hazard she fell in. */
    private static void standUp(ServerPlayer player, boolean gaveUp) {
        Body body = body(player);
        body.downedAt = body.downedUntil = -1;
        body.giveUp = 0;
        body.limbs.clear();
        player.setForcedPose(null);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        var jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (speed != null) speed.removeModifier(DOWN_SPEED);
        if (jump != null) jump.removeModifier(DOWN_JUMP);
        handicaps(player);
        if (gaveUp) {
            DimensionTransition home = player.findRespawnPositionAndUseSpawnBlock(false, DimensionTransition.DO_NOTHING);
            player.teleportTo(home.newLevel(), home.pos().x, home.pos().y, home.pos().z, home.yRot(), home.xRot());
            player.setHealth(player.getMaxHealth());
            player.displayClientMessage(Component.literal("You wake up at home, whole again.").withStyle(ChatFormatting.RED), false);
        } else {
            moveToSafety(player);
            player.setHealth(Math.max(player.getHealth(), 6.0F));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 5, 1));
            player.sendSystemMessage(Component.literal("You gasp back to life.").withStyle(ChatFormatting.DARK_RED));
        }
        player.clearFire();
        player.setAirSupply(player.getMaxAirSupply());
        ServerLevel level = player.serverLevel();
        AbilityEffects.send(level, Kind.REGEN_REVIVE, player.position(), player.position().add(0, 1, 0), 2.0D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5F, 1.0F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BONE_BLOCK_PLACE, SoundSource.PLAYERS, 1.2F, 0.6F);
        ScreenTintPayload.send(player, 0xB0200000, 0, ScreenTintPayload.Style.HOLD);
        ScreenTintPayload.send(player, 0xC0700000, 20, ScreenTintPayload.Style.FLASH);
        sync(player);
    }

    /** True if standing up here would only kill her again. */
    static boolean hazardous(ServerPlayer player) {
        return player.isInLava() || player.isEyeInFluid(FluidTags.WATER) || player.isInWall() || player.isOnFire()
                || player.getY() < player.level().getMinBuildHeight() + 1;
    }

    /** Moves her to the nearest dry, solid, open spot within twelve blocks, or home if there is none. */
    static void moveToSafety(ServerPlayer player) {
        if (!hazardous(player)) return;
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-12, -6, -12), origin.offset(12, 6, 12))) {
            double distance = pos.distSqr(origin);
            if (distance >= bestDistance || !safe(level, pos)) continue;
            best = pos.immutable();
            bestDistance = distance;
        }
        if (best != null) player.teleportTo(level, best.getX() + 0.5, best.getY(), best.getZ() + 0.5, player.getYRot(), player.getXRot());
        else {
            DimensionTransition home = player.findRespawnPositionAndUseSpawnBlock(false, DimensionTransition.DO_NOTHING);
            player.teleportTo(home.newLevel(), home.pos().x, home.pos().y, home.pos().z, home.yRot(), home.xRot());
        }
    }

    private static boolean safe(ServerLevel level, BlockPos feet) {
        if (!level.isLoaded(feet)) return false;
        BlockState below = level.getBlockState(feet.below()), at = level.getBlockState(feet), head = level.getBlockState(feet.above());
        return below.isFaceSturdy(level, feet.below(), Direction.UP) && below.getFluidState().isEmpty()
                && at.getCollisionShape(level, feet).isEmpty() && head.getCollisionShape(level, feet.above()).isEmpty()
                && at.getFluidState().isEmpty() && head.getFluidState().isEmpty()
                && !at.is(net.minecraft.tags.BlockTags.FIRE) && !below.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK);
    }

    // ------------------------------------------------------------------
    // Tick, sync and lifecycle
    // ------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        for (UUID id : List.copyOf(BODIES.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            Body body = BODIES.get(id);
            if (player == null) { BODIES.remove(id); continue; }
            long now = player.serverLevel().getGameTime();
            if (body.downed()) tickDowned(player, body, now);
            boolean changed = false;
            for (var entry : List.copyOf(body.limbs.entrySet())) {
                Wound wound = entry.getValue();
                float progress = wound.progress(now);
                int stage = progress < 0 ? 0 : progress < 0.35F ? 1 : progress < 0.7F ? 2 : progress < 1 ? 3 : 4;
                if (stage != wound.stage) {
                    wound.stage = stage;
                    Vec3 at = stumpPosition(player, entry.getKey());
                    var sound = switch (stage) {
                        case 1 -> SoundEvents.BONE_BLOCK_PLACE;      // bone pushes out
                        case 2 -> SoundEvents.SLIME_SQUISH;          // muscle wraps it
                        case 3 -> SoundEvents.HONEY_BLOCK_SLIDE;     // skin closes over
                        default -> SoundEvents.BONE_BLOCK_STEP;      // it's whole
                    };
                    if (stage > 0) player.serverLevel().playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, 0.8F, stage == 1 ? 0.6F : 1.0F);
                }
                if (progress >= 1) { body.limbs.remove(entry.getKey()); changed = true; }
            }
            if (changed) { handicaps(player); sync(player); }
            if (!body.downed() && body.limbs.isEmpty()) BODIES.remove(id);
            else if (!body.limbs.isEmpty() && (missing(player, Limb.LEFT_LEG) || missing(player, Limb.RIGHT_LEG))) player.setSprinting(false);
        }
    }

    private static void tickDowned(ServerPlayer player, Body body, long now) {
        player.setAirSupply(player.getMaxAirSupply());
        player.clearFire();
        player.setHealth(Math.max(1.0F, player.getHealth()));
        if (now % 40 == 0) forgetHer(player);
        // Hold Shift to give up and wake at home, so nobody is ever stuck.
        boolean holding = player.isShiftKeyDown() || now - body.giveUpPressed <= 2;
        if (now - body.downedAt >= GIVE_UP_AFTER && holding) {
            if (++body.giveUp >= GIVE_UP_HOLD) { standUp(player, true); return; }
        } else body.giveUp = 0;
        if (now >= body.downedUntil) standUp(player, false);
    }

    /** The client reports the give-up key held down, once a tick. */
    public static void giveUpHeld(ServerPlayer player) {
        Body body = BODIES.get(player.getUUID());
        if (body != null && body.downed()) body.giveUpPressed = player.serverLevel().getGameTime();
    }

    public static int giveUpProgress(ServerPlayer player) {
        Body body = BODIES.get(player.getUUID());
        return body == null ? 0 : body.giveUp;
    }

    public static boolean canGiveUp(ServerPlayer player) {
        Body body = BODIES.get(player.getUUID());
        return body != null && body.downed() && player.serverLevel().getGameTime() - body.downedAt >= GIVE_UP_AFTER;
    }

    public static BodyStatePayload state(ServerPlayer player) {
        Body body = BODIES.get(player.getUUID());
        long now = player.serverLevel().getGameTime();
        int[] starts = new int[4], grows = new int[4];
        if (body != null) for (var entry : body.limbs.entrySet()) {
            starts[entry.getKey().ordinal()] = (int) (entry.getValue().growStart - now);
            grows[entry.getKey().ordinal()] = entry.getValue().growTicks;
        }
        return new BodyStatePayload(player.getId(), player.getUUID(), body != null && body.downed(), starts, grows);
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, state(player));
    }

    /** Logging out on the ground does not skip the wait: the rest of it is kept for next time. */
    public static void logout(ServerPlayer player) {
        int remaining = downedRemaining(player);
        player.getPersistentData().putInt(SAVED_DOWN, remaining);
        forget(player);
    }

    public static void login(ServerPlayer player) {
        int remaining = player.getPersistentData().getInt(SAVED_DOWN);
        player.getPersistentData().remove(SAVED_DOWN);
        if (remaining > 0) down(player, remaining);
    }

    /** Drops all state, stands her up without ceremony (death, power removed, dimension change). */
    public static void forget(ServerPlayer player) {
        Body body = BODIES.remove(player.getUUID());
        if (body == null) return;
        player.setForcedPose(null);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        var jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (speed != null) { speed.removeModifier(LEG_SPEED); speed.removeModifier(DOWN_SPEED); }
        if (jump != null) { jump.removeModifier(LEG_JUMP); jump.removeModifier(DOWN_JUMP); }
        if (body.downed()) ScreenTintPayload.send(player, 0xB0200000, 0, ScreenTintPayload.Style.HOLD);
        sync(player);
    }

    public static void clear() { BODIES.clear(); }

    /** For tests: how far along a missing limb is (-1 a stump, 0..1 growing), or 2 if it isn't missing. */
    public static float progress(ServerPlayer player, Limb limb) {
        Body body = BODIES.get(player.getUUID());
        Wound wound = body == null ? null : body.limbs.get(limb);
        return wound == null ? 2 : wound.progress(player.serverLevel().getGameTime());
    }

    /** Is there anything about this body worth sending to someone who just started watching? */
    public static boolean hasState(ServerPlayer player) { return BODIES.containsKey(player.getUUID()); }
}
