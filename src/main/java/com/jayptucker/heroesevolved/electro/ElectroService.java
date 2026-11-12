package com.jayptucker.heroesevolved.electro;

import com.jayptucker.heroesevolved.ability.AbilityRanges;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.flight.FlightSkillService;
import com.jayptucker.heroesevolved.grid.GridDisruption;
import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;
import static com.jayptucker.heroesevolved.electro.ElectroGeometry.*;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.time.TemporalEntityEchoService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Electrokinesis, after Elle Bishop. Static builds in her as she fights and sprints (Charge, 0 to
 * 100): at 50 she is Charged and her lightning leaps farther; at 100 she is Overcharged and her next
 * power is empowered, spending it all. Water bleeds the charge straight back into her.
 *
 * <ul>
 *   <li>Chain Lightning: tap for a forked bolt that leaps between foes; hold for Live Current, a
 *   continuous stream that locks the target's muscles.</li>
 *   <li>Ball Lightning: a homing orb that lashes what it passes; Shift bursts it early.</li>
 *   <li>Thunderstrike: mark a spot, and lightning strikes it a moment later; it electrifies water.</li>
 *   <li>Ride the Lightning: become a bolt, reappear down the line, and leave a live scar behind.</li>
 *   <li>Storm Surge: gather the storm, then release it at everything around you.</li>
 * </ul>
 */
public final class ElectroService {
    public static final int MAX_CHARGE = 100, CHARGED = 50;
    public static final int STATIC_TOUCH_UNLOCK_LEVEL = 2;
    public static final int STATIC_TOUCH_ENERGY_COST = 4;
    /** Held longer than this, Chain Lightning becomes Live Current. */
    public static final int TAP_TICKS = 6;
    private static final int STREAM_TICKS = 80;
    private static final int ELECTRIC_TINT = 0x5555AAFF;
    private static final int ORB_LIFETIME_TICKS = AbilityRanges.ranged(50);

    private static final class Charge { int value; long lastGain; int tier; }
    private static final class Hold {
        final ServerPlayer player; final int level; final long started; final ResourceKey<Level> dimension;
        boolean streaming, empowered; LivingEntity target;
        Hold(ServerPlayer player, int level, long started) {
            this.player = player; this.level = level; this.started = started; this.dimension = player.level().dimension();
        }
    }
    private record Strike(UUID owner, ServerLevel level, Vec3 at, long due, int powerLevel, boolean water) {}
    private record Surge(ServerPlayer player, int level, long releaseAt, ResourceKey<Level> dimension, double radius) {}
    private record Scar(UUID owner, ServerLevel level, List<Vec3> path, AABB bounds, long until, int powerLevel, Set<UUID> struck) {}

    private static final Map<UUID, Charge> CHARGE = new HashMap<>();
    private static final Map<UUID, Hold> HOLDS = new HashMap<>();
    private static final List<Orb> ORBS = new ArrayList<>();
    private static final List<Strike> STRIKES = new ArrayList<>();
    private static final Map<UUID, Surge> SURGES = new HashMap<>();
    private static final List<Scar> SCARS = new ArrayList<>();
    /** Light blocks placed for a lightning flash, and when each goes dark again. */
    private static final Map<ServerLevel, Map<BlockPos, Long>> LIGHTS = new HashMap<>();

    private static int powerDamageDepth;

    private ElectroService() {
    }

    public static boolean isDealingPowerDamage() {
        return powerDamageDepth > 0;
    }

    // ------------------------------------------------------------------
    // Charge
    // ------------------------------------------------------------------

    public static int charge(ServerPlayer player) {
        Charge charge = CHARGE.get(player.getUUID());
        return charge == null ? 0 : charge.value;
    }

    public static void addCharge(ServerPlayer player, int amount) {
        Charge charge = CHARGE.computeIfAbsent(player.getUUID(), ignored -> new Charge());
        charge.value = Math.clamp(charge.value + amount, 0, MAX_CHARGE);
        if (amount > 0) charge.lastGain = player.serverLevel().getGameTime();
        announce(player, charge);
    }

    /** Overcharge (the skill) and drinking in lightning fill it at once. */
    public static void fillCharge(ServerPlayer player) {
        addCharge(player, MAX_CHARGE);
    }

    /** Spends a full charge if there is one: the power being cast is empowered. */
    public static boolean spendOvercharge(ServerPlayer player) {
        Charge charge = CHARGE.get(player.getUUID());
        if (charge == null || charge.value < MAX_CHARGE) return false;
        charge.value = 0;
        announce(player, charge);
        player.displayClientMessage(Component.literal("Overcharged!").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), true);
        com.jayptucker.heroesevolved.progression.EvolutionTrials.advance(player, ModAbilities.ELECTROKINESIS_ID);
        return true;
    }

    /** Spends whatever charge there is; returns how much. */
    public static int drainCharge(ServerPlayer player) {
        Charge charge = CHARGE.get(player.getUUID());
        if (charge == null || charge.value == 0) return 0;
        int spent = charge.value;
        charge.value = 0;
        announce(player, charge);
        return spent;
    }

    private static void announce(ServerPlayer player, Charge charge) {
        int tier = charge.value >= MAX_CHARGE ? 2 : charge.value >= CHARGED ? 1 : 0;
        if (tier == charge.tier) return;
        boolean rising = tier > charge.tier;
        charge.tier = tier;
        if (!rising) return;
        if (tier == 2) {
            player.displayClientMessage(Component.literal("OVERCHARGED: your next power is empowered.")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), true);
            sound(player, SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.8F);
        } else {
            player.displayClientMessage(Component.literal("Charged: your lightning leaps farther.")
                    .withStyle(ChatFormatting.AQUA), true);
            sound(player, SoundEvents.COPPER_BULB_TURN_ON, 0.8F, 1.2F);
        }
    }

    /** Every tick for an awakened electrokinetic: static from sprinting and storms, decay, water, the visible aura. */
    public static void playerTick(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        if (player.tickCount % 20 == 0) {
            if (player.isSprinting()) addCharge(player, 2);
            if (stormEnergyBonus(player) > 0) addCharge(player, 3);
            Charge charge = CHARGE.get(player.getUUID());
            if (charge != null && charge.value > 0 && now - charge.lastGain > 160) addCharge(player, -3);
        }
        if (player.isInWaterOrBubble() && charge(player) >= 20) waterDischarge(player);
        int value = charge(player);
        if (value >= CHARGED && player.tickCount % 6 == 0)
            show(player.serverLevel(), Piece.ELECTRO_AURA, player.position(), player.position(), value >= MAX_CHARGE ? 2 : 1, player);
        ambientCrackle(player, now);
    }

    /** Stepping into water with charge in you: it all goes out at once, through the water and through you. */
    private static void waterDischarge(ServerPlayer player) {
        int spent = drainCharge(player);
        ServerLevel level = player.serverLevel();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(5),
                entity -> hostile(player, entity) && entity.isInWaterOrBubble() && player.hasLineOfSight(entity))) {
            drawArc(level, player.position().add(0, 0.5, 0), entity.getBoundingBox().getCenter(), level.getRandom());
            shock(player, entity, 2 + spent / 12F);
        }
        show(level, Piece.ELECTRO_PULSE, player.position(), player.position(), 4, null);
        player.hurt(backfire(player), 1 + spent / 25F);
        sound(player, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.9F, 1.2F);
        player.displayClientMessage(Component.literal("Your charge bleeds out into the water, and through you!")
                .withStyle(ChatFormatting.RED), true);
    }

    // ------------------------------------------------------------------
    // Chain Lightning and Live Current
    // ------------------------------------------------------------------

    public static boolean isHolding(ServerPlayer player) {
        return HOLDS.containsKey(player.getUUID());
    }

    public static boolean canStartCast(ServerPlayer player) {
        return PowerRules.canAct(player) && !isHolding(player) && !isSurging(player) && !BlackoutService.winding(player);
    }

    /** The Chain Lightning key went down. A quick release fires the bolt; holding on becomes Live Current. */
    public static void press(ServerPlayer player, int powerLevel) {
        if (!canStartCast(player)) return;
        HOLDS.put(player.getUUID(), new Hold(player, powerLevel, player.serverLevel().getGameTime()));
        ArmPosePayload.show(player, ArmPose.ELECTRO_DRAW, TAP_TICKS + 1);
        Vec3 palm = handPosition(player);
        show(player.serverLevel(), Piece.ELECTRO_DRAW, palm, palm, 1, player);
    }

    /** The key came up: a tap fires Chain Lightning, a hold ends the stream. */
    public static void release(ServerPlayer player) {
        Hold hold = HOLDS.remove(player.getUUID());
        if (hold == null) return;
        if (hold.streaming) endStream(player);
        else if (PowerRules.canAct(player) && hold.dimension.equals(player.level().dimension())) chainLightning(player, hold.level);
    }

    /** Shift + key while streaming: stop at once. */
    public static boolean stopStream(ServerPlayer player) {
        Hold hold = HOLDS.remove(player.getUUID());
        if (hold == null) return false;
        if (hold.streaming) endStream(player);
        else ArmPosePayload.clear(player);
        return true;
    }

    private static void endStream(ServerPlayer player) {
        ArmPosePayload.clear(player);
        sound(player, SoundEvents.BEACON_DEACTIVATE, 0.5F, 2.0F);
    }

    /** A forked bolt from the fingertips that leaps between foes. Charged adds a leap; Overcharged adds two and hits harder. */
    public static void chainLightning(ServerPlayer player, int powerLevel) {
        ServerLevel level = player.serverLevel();
        Vec3 hand = handPosition(player);
        Vec3 eye = player.getEyePosition(), aim = player.getLookAngle();
        double reach = AbilityRanges.ranged(16.0D);
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(aim.scale(reach)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        boolean charged = charge(player) >= CHARGED;
        LivingEntity target = Targeting.livingEntityInSight(player, 16.0D, entity -> hostile(player, entity));
        double limit = Math.min(eye.distanceTo(hit.getLocation()), target == null ? reach : eye.distanceTo(target.getBoundingBox().getCenter()));
        Orb relay = aimedOrb(player, eye, aim, limit);
        boolean over = (target != null || relay != null) && spendOvercharge(player);

        if (relay != null) {
            relay.relayAt = level.getGameTime() + ORB_PRIME;
            show(level, Piece.ELECTRO_ARC, hand, relay.position, over ? 1.7 : 1.25, player);
            show(level, Piece.ELECTRO_PRIME, relay.position, relay.position, .81, null);
            level.playSound(null, relay.position.x, relay.position.y, relay.position.z,
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, .8F, 1.65F);
        } else if (target == null) {
            show(level, Piece.ELECTRO_ARC, hand, hit.getLocation(), 1, player);
            if (hit.getType() == HitResult.Type.BLOCK) {
                show(level, Piece.ELECTRO_PULSE, hit.getLocation(), hit.getLocation(), 1.2, null, 2);
                energize(level, hit.getBlockPos(), player, COMMON.gridLightningCharge.get());
            }
            flashLight(level, hit.getLocation(), 3);
        } else {
            float damage = (4.0F + powerLevel * 1.5F) * (over ? 1.4F : 1.0F);
            int jumps = powerLevel - 1 + (charged ? 1 : 0) + (over ? 2 : 0);
            Set<UUID> struck = new HashSet<>();
            Vec3 from = hand;
            LivingEntity current = target;
            for (int hop = 0; current != null && hop <= jumps; hop++) {
                Vec3 to = current.getBoundingBox().getCenter();
                show(level, Piece.ELECTRO_ARC, from, to, over ? 1.7D : hop == 0 ? 1.25D : 1.0D,
                        hop == 0 ? player : null, hop);
                shock(player, current, damage);
                struck.add(current.getUUID());
                from = to;
                damage *= 0.75F;
                current = nearestUnstruck(level, current, player, struck, AbilityRanges.ranged(5.0D));
            }
            flashLight(level, target.getBoundingBox().getCenter(), 3);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6F, over ? 1.2F : 1.7F);
        sound(player, SoundEvents.COPPER_BULB_TURN_ON, 0.7F, 1.6F);
        ArmPosePayload.show(player, ArmPose.ELECTRO_CAST, 12);
        backfireIfWet(player, powerLevel);
        MasteryService.markPowerUse(player, ModAbilities.ELECTROKINESIS_ID);
    }

    private static void tickHold(Hold hold, long now) {
        ServerPlayer player = hold.player;
        if (!player.isAlive() || player.isRemoved() || !PowerRules.canAct(player)
                || !hold.dimension.equals(player.level().dimension())) {
            HOLDS.remove(player.getUUID());
            endStream(player);
            return;
        }
        long held = now - hold.started;
        if (!hold.streaming) {
            if (held < TAP_TICKS) return;
            hold.streaming = true;
            if (!player.isInWaterOrBubble()) hold.empowered = spendOvercharge(player);
            ArmPosePayload.show(player, ArmPose.ELECTRO_STREAM, STREAM_TICKS + 10);
            sound(player, SoundEvents.GUARDIAN_ATTACK, 0.7F, 1.6F);
        }
        if (held > STREAM_TICKS + TAP_TICKS || player.isInWaterOrBubble()) {
            if (player.isInWaterOrBubble()) backfireIfWet(player, hold.level);
            HOLDS.remove(player.getUUID());
            endStream(player);
            return;
        }
        if (now % 4 == 0) {
            if (!PlayerEnergyService.tryConsume(player, 3)) { HOLDS.remove(player.getUUID()); endStream(player); return; }
            MasteryService.awardEnergySpent(player, 3);
        }
        if (now % 2 == 0) streamPulse(hold, now % 4 == 0);
    }

    /** Live Current: a continuous arc to whatever is under the crosshair. Targets seize up while it holds them. */
    private static void streamPulse(Hold hold, boolean damageTick) {
        ServerPlayer player = hold.player;
        ServerLevel level = player.serverLevel();
        Vec3 hand = handPosition(player), eye = player.getEyePosition(), aim = player.getLookAngle();
        double range = AbilityRanges.ranged(7.0D);
        // The stream sticks to its target while it stays roughly under the crosshair.
        LivingEntity target = hold.target;
        if (target != null && (!hostile(player, target) || target.distanceTo(player) > range + 1
                || target.getBoundingBox().getCenter().subtract(eye).normalize().dot(aim) < 0.9 || !player.hasLineOfSight(target))) target = null;
        if (target == null) target = Targeting.livingEntityInSight(player, 7.0D, entity -> hostile(player, entity));
        hold.target = target;

        Vec3 end;
        BlockHitResult hit = null;
        if (target != null) end = target.getBoundingBox().getCenter();
        else {
            hit = level.clip(new ClipContext(eye, eye.add(aim.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            end = hit.getLocation();
        }
        show(level, Piece.ELECTRO_CURRENT, hand, end, hold.empowered ? 1.55 : charge(player) >= CHARGED ? 1.25 : 1, player);
        if (!damageTick) return;
        if (target != null) {
            shock(player, target, (1.5F + hold.level * .3F) * (hold.empowered ? 1.25F : 1), true);
            // Current locks the muscles: the target seizes and twitches.
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 4, false, false));
            if (target instanceof Mob mob) mob.getNavigation().stop();
            RandomSource random = level.getRandom();
            setVelocity(target, target.getDeltaMovement().multiply(0.2, 1, 0.2)
                    .add((random.nextDouble() - 0.5) * 0.12, 0, (random.nextDouble() - 0.5) * 0.12));
        } else if (hit.getType() == HitResult.Type.BLOCK) {
            // Held on a machine, Live Current is a charger: half a bolt's worth on every pulse, five a second.
            GridDisruption.charge(player, level, hit.getBlockPos(), COMMON.gridLightningCharge.get() / 2);
            if (level.getGameTime() % 10 == 0) energize(level, hit.getBlockPos(), player, 0);
        }
        flashLight(level, end, 3);
        level.playSound(null, end.x, end.y, end.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
                0.3F, 1.6F + level.getRandom().nextFloat() * 0.4F);
    }

    // ------------------------------------------------------------------
    // Static Touch (Level 2 passive)
    // ------------------------------------------------------------------

    /**
     * Elle's touch carries a charge. Each melee hit she can pay for shocks and briefly stuns its target.
     *
     * @return the extra damage to add, or 0 if there was no energy for it
     */
    public static float staticTouch(ServerPlayer player, LivingEntity target, int powerLevel) {
        if (!hostile(player, target) || !PlayerEnergyService.tryConsume(player, STATIC_TOUCH_ENERGY_COST)) return 0.0F;
        MasteryService.awardEnergySpent(player, STATIC_TOUCH_ENERGY_COST);
        ServerLevel level = player.serverLevel();
        drawArc(level, handPosition(player), target.getBoundingBox().getCenter(), level.getRandom());
        show(level, Piece.ELECTRO_PULSE, target.position(), target.position(), .8, null);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.4F, 2.0F);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3));
        addCharge(player, 3);
        float bonus = 3.0F + powerLevel + (charge(player) >= CHARGED ? 2.0F : 0.0F);
        return target.isInWaterRainOrBubble() ? bonus * 1.5F : bonus;
    }

    // ------------------------------------------------------------------
    // Ball Lightning
    // ------------------------------------------------------------------

    /** A crackling sphere that drifts forward, curving toward foes and lashing them, then bursts. */
    public static boolean ballLightning(ServerPlayer player, int powerLevel) {
        if (!canStartCast(player)) return false;
        boolean over = spendOvercharge(player);
        Vec3 eye = player.getEyePosition(), palm = handPosition(player);
        // A palm pressed into a wall cannot spawn its orb on the far side.
        Vec3 start = visible(player, eye, palm) ? palm : eye;
        Orb orb = new Orb(player.getUUID(), player.serverLevel(), start,
                player.getLookAngle().normalize().scale(.55), powerLevel, over);
        ORBS.add(orb);
        show(orb.level, Piece.ELECTRO_DRAW, palm, palm, over ? 1.45 : 1.1, player);
        show(orb.level, Piece.ELECTRO_ORB, start, start.add(orb.velocity), over ? .68 : .47, null);
        ArmPosePayload.show(player, ArmPose.ELECTRO_CAST, 12);
        sound(player, SoundEvents.BREEZE_SHOOT, 0.8F, 1.8F);
        sound(player, SoundEvents.BEACON_POWER_SELECT, 0.4F, 2.0F);
        backfireIfWet(player, powerLevel);
        MasteryService.markPowerUse(player, ModAbilities.ELECTROKINESIS_ID);
        return true;
    }

    /** Shift + key: every orb you have bursts where it is. */
    public static boolean detonate(ServerPlayer player) {
        boolean any = false;
        for (Iterator<Orb> iterator = ORBS.iterator(); iterator.hasNext(); ) {
            Orb orb = iterator.next();
            if (!orb.ownerId.equals(player.getUUID())) continue;
            burst(player, orb, orb.position);
            iterator.remove();
            any = true;
        }
        return any;
    }

    public static boolean hasOrb(ServerPlayer player) {
        return ORBS.stream().anyMatch(orb -> orb.ownerId.equals(player.getUUID()));
    }

    private static Orb aimedOrb(ServerPlayer player, Vec3 eye, Vec3 aim, double reach) {
        Orb nearest = null;
        double distance = reach;
        for (Orb orb : ORBS) {
            if (!orb.ownerId.equals(player.getUUID()) || orb.level != player.level() || orb.relayAt != 0) continue;
            Vec3 delta = orb.position.subtract(eye);
            double along = delta.dot(aim);
            if (along <= 0 || along > distance || delta.subtract(aim.scale(along)).lengthSqr() > .80 * .80
                    || !visible(player, eye, orb.position)) continue;
            nearest = orb;
            distance = along;
        }
        return nearest;
    }

    /** @return true once the orb has burst */
    private static boolean tickOrb(ServerPlayer owner, Orb orb) {
        ServerLevel level = orb.level;
        if (orb.relayAt != 0) {
            if (level.getGameTime() < orb.relayAt) return false;
            burst(owner, orb, orb.position);
            return true;
        }
        orb.ageTicks++;
        // It curves gently toward the nearest foe ahead of it.
        Vec3 heading = orb.velocity.normalize();
        LivingEntity prey = level.getEntitiesOfClass(LivingEntity.class, new AABB(orb.position, orb.position).inflate(6.0D),
                        entity -> hostile(owner, entity) && entity.distanceToSqr(orb.position) <= 36
                                && entity.getBoundingBox().getCenter().subtract(orb.position).normalize().dot(heading) > .3
                                && visible(owner, orb.position, entity.getBoundingBox().getCenter()))
                .stream().min(Comparator.comparingDouble(entity -> entity.distanceToSqr(orb.position))).orElse(null);
        if (prey != null) {
            Vec3 toward = prey.getBoundingBox().getCenter().subtract(orb.position).normalize();
            orb.velocity = heading.scale(0.85D).add(toward.scale(0.15D)).normalize().scale(orb.velocity.length());
        }
        Vec3 next = orb.position.add(orb.velocity);
        if (!level.hasChunkAt(BlockPos.containing(next)) || !level.getWorldBorder().isWithinBounds(BlockPos.containing(next))) return true;

        BlockHitResult blockHit = level.clip(new ClipContext(orb.position, next,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        LivingEntity touched = level.getEntitiesOfClass(LivingEntity.class, new AABB(next, next).inflate(0.6D),
                entity -> hostile(owner, entity) && visible(owner, orb.position, entity.getBoundingBox().getCenter()))
                .stream().findFirst().orElse(null);
        if (blockHit.getType() != HitResult.Type.MISS || touched != null || orb.ageTicks >= ORB_LIFETIME_TICKS) {
            Vec3 impact = blockHit.getType() != HitResult.Type.MISS
                    ? blockHit.getLocation().subtract(orb.velocity.normalize().scale(.035)) : next;
            burst(owner, orb, impact);
            return true;
        }

        orb.position = next;
        if (orb.ageTicks % 2 == 0)
            show(level, Piece.ELECTRO_ORB, next, next.add(orb.velocity), orb.empowered ? .68 : .47, null);
        if (orb.ageTicks % 4 == 0) {
            List<LivingEntity> nearest = level.getEntitiesOfClass(LivingEntity.class, new AABB(next, next).inflate(4.0D),
                            entity -> hostile(owner, entity) && entity.distanceToSqr(next) <= 16
                                    && visible(owner, next, entity.getBoundingBox().getCenter()))
                    .stream().sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(next)))
                    .limit(orb.empowered ? 2 : 1).toList();
            for (LivingEntity entity : nearest) {
                drawArc(level, next, entity.getBoundingBox().getCenter(), level.getRandom());
                shock(owner, entity, 3.0F + orb.powerLevel);
            }
            if (!nearest.isEmpty()) flashLight(level, next, 2);
        }
        if (orb.ageTicks % 10 == 0)
            level.playSound(null, next.x, next.y, next.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.3F, 2.0F);
        return false;
    }

    private static void burst(ServerPlayer owner, Orb orb, Vec3 at) {
        ServerLevel level = orb.level;
        boolean empowered = orb.empowered || orb.relayAt != 0;
        double radius = empowered ? 4.5 : 3;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                entity -> hostile(owner, entity) && at.distanceToSqr(entity.getBoundingBox().getCenter()) <= radius * radius
                        && visible(owner, at, entity.getBoundingBox().getCenter()))) {
            drawArc(level, at, entity.getBoundingBox().getCenter(), level.getRandom());
            shock(owner, entity, 5.0F + orb.powerLevel + (empowered ? 3.0F : 0.0F));
        }
        // The burst lashes into any machine it pops beside, too.
        for (BlockPos machine : GridDisruption.chargeArea(owner, level, BlockPos.containing(at), (int) Math.ceil(radius),
                COMMON.gridLightningCharge.get() * 2, 4))
            drawArc(level, at, Vec3.atCenterOf(machine), level.getRandom());
        show(level, Piece.ELECTRO_PULSE, at, at, radius, null);
        flashLight(level, at, 4);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.9F, 1.3F);
        if (empowered) level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    private static final class Orb {
        private final UUID ownerId;
        private final ServerLevel level;
        private final int powerLevel;
        private final boolean empowered;
        private Vec3 velocity;
        private Vec3 position;
        private int ageTicks;
        private long relayAt;

        private Orb(UUID ownerId, ServerLevel level, Vec3 position, Vec3 velocity, int powerLevel, boolean empowered) {
            this.ownerId = ownerId;
            this.level = level;
            this.position = position;
            this.velocity = velocity;
            this.powerLevel = powerLevel;
            this.empowered = empowered;
        }
    }

    // ------------------------------------------------------------------
    // Thunderstrike
    // ------------------------------------------------------------------

    /**
     * Marks the spot Elle points at; lightning strikes it {@value #STRIKE_DELAY} ticks later. It behaves
     * like real lightning for whatever it hits (creepers charge, pigs turn) but never sets the land alight.
     * Overcharged, two more strikes follow nearby. Striking water electrifies everyone in it.
     */
    public static boolean thunderstrike(ServerPlayer player, int powerLevel) {
        if (!canStartCast(player)) return false;
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(AbilityRanges.ranged(48.0D))),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            player.displayClientMessage(Component.literal("There is nothing there to strike."), true);
            return false;
        }
        boolean over = spendOvercharge(player);
        Vec3 impact = hit.getLocation();
        long now = level.getGameTime();
        boolean water = level.getFluidState(hit.getBlockPos()).is(net.minecraft.tags.FluidTags.WATER);
        STRIKES.add(new Strike(player.getUUID(), level, impact, now + STRIKE_DELAY, powerLevel, water));
        show(level, Piece.ELECTRO_WARN, impact, impact, STRIKE_RADIUS, null);
        if (over) {
            RandomSource random = level.getRandom();
            for (int i = 1; i <= 2; i++) {
                double angle = random.nextDouble() * Math.PI * 2, distance = 2.5 + random.nextDouble() * 2;
                Vec3 at = ground(level, impact.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance));
                boolean secondaryWater = level.getFluidState(BlockPos.containing(at.add(0, -.1, 0)))
                        .is(net.minecraft.tags.FluidTags.WATER);
                STRIKES.add(new Strike(player.getUUID(), level, at, now + STRIKE_DELAY + i * 6, powerLevel, secondaryWater));
                show(level, Piece.ELECTRO_WARN, at, at, STRIKE_RADIUS, null, i * 6);
            }
        }
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0F, 1.4F);
        ArmPosePayload.show(player, ArmPose.ELECTRO_CALL, STRIKE_DELAY + 6);
        MasteryService.markPowerUse(player, ModAbilities.ELECTROKINESIS_ID);
        return true;
    }

    public static int pendingStrikes(ServerPlayer player) {
        return (int) STRIKES.stream().filter(strike -> strike.owner.equals(player.getUUID())).count();
    }

    private static void land(Strike strike, ServerPlayer owner) {
        ServerLevel level = strike.level;
        Vec3 impact = strike.at;
        Vec3 contact = impact.add(0, .08, 0);
        LightningBolt bolt = reactionBolt(level, impact, owner);
        skyBolt(owner, impact, 28, 1, 0);
        thunder(level, impact, 1.0F);
        float damage = 4.0F + strike.powerLevel * 2.0F;
        Set<UUID> hit = new HashSet<>();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(impact, impact).inflate(STRIKE_RADIUS),
                entity -> entity != owner && entity.isAlive() && entity.position().distanceToSqr(impact) <= STRIKE_RADIUS * STRIKE_RADIUS
                        && visible(owner, contact, entity.getBoundingBox().getCenter()))) {
            if (TemporalEntityEchoService.isReplaying(entity)) TemporalEntityEchoService.disturb(entity, owner);
            if (!hostile(owner, entity)) continue;
            // Vanilla's lightning reactions: charged creepers, witches, zombified piglins and the rest.
            if (bolt != null) entity.thunderHit(level, bolt);
            shock(owner, entity, damage);
            hit.add(entity.getUUID());
            setVelocity(entity, entity.getDeltaMovement().add(0, 0.35, 0));
        }
        if (strike.water) {
            // Water carries it: everyone standing or swimming in water nearby is shocked too.
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(impact, impact).inflate(8.0D),
                    entity -> hostile(owner, entity) && entity.isInWaterOrBubble() && !hit.contains(entity.getUUID())
                            && entity.position().distanceToSqr(impact) <= 64 && visible(owner, contact, entity.getBoundingBox().getCenter()))) {
                show(level, Piece.ELECTRO_PULSE, entity.position(), entity.position(), 1.5, null);
                shock(owner, entity, damage * 0.6F);
            }
        }
        // A real strike is a lot of current: ten bolts' worth into whatever it lands on.
        energize(level, BlockPos.containing(impact.subtract(0, 0.1, 0)), owner, COMMON.gridLightningCharge.get() * 10);
        flashLight(level, impact.add(0, 1, 0), 5);
        CameraShakePayload.sendAround(level, impact, 24.0D, 1.4F, 10);
        ScreenTintPayload.sendAround(level, impact, 24.0D, 0x40FFFFFF, 5, ScreenTintPayload.Style.FLASH);
    }

    // ------------------------------------------------------------------
    // Ride the Lightning
    // ------------------------------------------------------------------

    /** Elle becomes a streak of current and reappears down the line, leaving a live scar on the ground. */
    public static boolean rideTheLightning(ServerPlayer player, int powerLevel) {
        if (player.isPassenger() || !canStartCast(player)) return false;
        ServerLevel level = player.serverLevel();
        boolean over = charge(player) >= MAX_CHARGE;
        Vec3 direction = Vec3.directionFromRotation(0, player.getYRot());
        double maximumDistance = AbilityRanges.ranged(10 + powerLevel) * (over ? 1.6 : 1);
        Vec3 start = player.position();
        Vec3 end = start;
        List<Vec3> route = new ArrayList<>();
        route.add(start);
        Vec3 step = direction.scale(.5);
        for (double distance = .5; distance <= maximumDistance; distance += .5) {
            Vec3 next = end.add(step);
            if (!clearLeg(player, end, next)) {
                Vec3 ledge = null;
                for (double rise : new double[]{.6, 1.05}) {
                    Vec3 lifted = end.add(0, rise, 0);
                    if (clearLeg(player, end, lifted) && clearLeg(player, lifted, lifted.add(step))) {
                        ledge = lifted;
                        break;
                    }
                }
                if (ledge == null) break;
                appendPoint(route, ledge);
                next = ledge.add(step);
            }
            appendPoint(route, next);
            end = next;
        }
        if (end.distanceToSqr(start) < 1) return false;
        if (over) spendOvercharge(player);

        float damage = 4.0F + powerLevel;
        Set<UUID> struck = new HashSet<>();
        AABB bounds = bounds(route);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, bounds.inflate(RIDE_RADIUS, 1.5, RIDE_RADIUS),
                entity -> hostile(player, entity) && touchesRoute(player, entity, route, RIDE_RADIUS))) {
            shock(player, entity, damage);
            struck.add(entity.getUUID());
        }
        double walked = 0;
        for (int i = 1; i < route.size(); i++) {
            Vec3 from = route.get(i - 1), to = route.get(i);
            show(level, Piece.ELECTRO_RIDE, from, to, over ? 1.34 : 1, null, (int) (4 * walked / maximumDistance));
            walked += from.distanceTo(to);
        }
        List<Vec3> scar = new ArrayList<>();
        scar.add(ground(level, start, .04, 4));
        for (int i = 1; i < route.size(); i++) {
            Vec3 from = route.get(i - 1), to = route.get(i);
            int samples = Math.max(1, (int) Math.ceil(from.distanceTo(to) / .75));
            for (int sample = 1; sample <= samples; sample++)
                appendPoint(scar, ground(level, from.lerp(to, sample / (double) samples), .04, 4));
        }
        for (int i = 1; i < scar.size(); i++)
            show(level, Piece.ELECTRO_SCAR, scar.get(i - 1), scar.get(i), over ? 1.35 : 1, null);
        sound(player, SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.7F, 1.6F);
        player.teleportTo(end.x, end.y, end.z);
        player.resetFallDistance();
        level.playSound(null, end.x, end.y, end.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F, 1.4F);
        ScreenTintPayload.send(player, 0x40AACCFF, 6, ScreenTintPayload.Style.FLASH);
        flashLight(level, end.add(0, 1, 0), 4);
        SCARS.add(new Scar(player.getUUID(), level, scar, bounds(scar), level.getGameTime() + SCAR_TICKS, powerLevel, struck));
        MasteryService.markPowerUse(player, ModAbilities.ELECTROKINESIS_ID);
        return true;
    }

    private static void tickScar(Scar scar, ServerPlayer owner, long now) {
        if (now % 2 != 0) return;
        for (LivingEntity entity : scar.level.getEntitiesOfClass(LivingEntity.class, scar.bounds.inflate(SCAR_RADIUS, 1.5, SCAR_RADIUS),
                entity -> hostile(owner, entity) && !scar.struck.contains(entity.getUUID())
                        && touchesRoute(owner, entity, scar.path, SCAR_RADIUS))) {
            scar.struck.add(entity.getUUID());
            show(scar.level, Piece.ELECTRO_PULSE, entity.position(), entity.position(), 1, null);
            shock(owner, entity, 2.0F + scar.powerLevel);
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
        }
    }

    private static boolean clearLeg(ServerPlayer player, Vec3 from, Vec3 to) {
        return SpeedService.sweptFrom(player, from, to.subtract(from), point -> EntityCarryService.canTravelTo(player, point), true)
                .distanceToSqr(to) < 1e-6;
    }

    /** Only turns and changes of height need a waypoint on the wire. */
    private static void appendPoint(List<Vec3> route, Vec3 next) {
        int size = route.size();
        Vec3 last = route.getLast();
        if (next.distanceToSqr(last) < 1e-6) return;
        if (size > 1 && last.subtract(route.get(size - 2)).normalize().dot(next.subtract(last).normalize()) > .9999)
            route.set(size - 1, next);
        else route.add(next);
    }

    private static AABB bounds(List<Vec3> path) {
        AABB box = new AABB(path.getFirst(), path.getFirst());
        for (Vec3 point : path) box = box.minmax(new AABB(point, point));
        return box;
    }

    private static boolean touchesRoute(ServerPlayer owner, LivingEntity target, List<Vec3> path, double radius) {
        Vec3 feet = target.position();
        for (int i = 1; i < path.size(); i++) {
            Vec3 start = path.get(i - 1), delta = path.get(i).subtract(start);
            double fraction = Math.clamp(feet.subtract(start).dot(delta) / Math.max(1e-6, delta.lengthSqr()), 0, 1);
            Vec3 point = start.add(delta.scale(fraction));
            if (feet.subtract(point).horizontalDistanceSqr() <= radius * radius && Math.abs(feet.y - point.y) <= 1.25
                    && visible(owner, point.add(0, .55, 0), target.getBoundingBox().getCenter())) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Storm Surge
    // ------------------------------------------------------------------

    public static boolean isSurging(ServerPlayer player) {
        return SURGES.containsKey(player.getUUID());
    }

    /** Gathers the storm for a moment and a half, lifting Elle off her feet. Shift cancels. */
    public static boolean startSurge(ServerPlayer player, int powerLevel) {
        if (!canStartCast(player)) return false;
        ServerLevel level = player.serverLevel();
        double radius = surgeRadius(player);
        SURGES.put(player.getUUID(), new Surge(player, powerLevel, level.getGameTime() + SURGE_TICKS, level.dimension(), radius));
        Vec3 floor = ground(level, player.position(), .04, 4);
        show(level, Piece.ELECTRO_FIELD, floor, floor, radius, player);
        show(level, Piece.ELECTRO_COIL, player.position(), player.position(), .15, player);
        ArmPosePayload.show(player, ArmPose.ELECTRO_COIL, SURGE_TICKS + 2);
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SURGE_TICKS + 30, 0, false, false));
        setVelocity(player, player.getDeltaMovement().add(0, 0.42, 0));
        sound(player, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.5F);
        ScreenTintPayload.send(player, ELECTRIC_TINT, SURGE_TICKS, ScreenTintPayload.Style.VIGNETTE);
        return true;
    }

    public static boolean cancelSurge(ServerPlayer player) {
        if (SURGES.remove(player.getUUID()) == null) return false;
        ArmPosePayload.clear(player);
        player.removeEffect(MobEffects.SLOW_FALLING);
        sound(player, SoundEvents.BEACON_DEACTIVATE, 0.8F, 1.4F);
        return true;
    }

    /**
     * Everything Elle has, released at once: every Charge point adds to it. A storm overhead
     * nearly doubles its reach. Creepers caught in it become charged.
     */
    public static void stormSurge(ServerPlayer player, int powerLevel) {
        stormSurge(player, powerLevel, surgeRadius(player));
    }

    private static double surgeRadius(ServerPlayer player) {
        return stormEnergyBonus(player) > 0 ? 16 : 10;
    }

    private static void stormSurge(ServerPlayer player, int powerLevel, double radius) {
        ServerLevel level = player.serverLevel();
        float damage = 8.0F + powerLevel + drainCharge(player) / 10.0F;
        Vec3 center = chest(player.position());
        RandomSource random = level.getRandom();

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                        entity -> hostile(player, entity) && entity.distanceToSqr(player) <= radius * radius
                                && visible(player, center, entity.getBoundingBox().getCenter()))
                .stream().sorted(Comparator.comparingDouble(player::distanceToSqr)).limit(24).toList();
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        for (LivingEntity target : targets) {
            show(level, Piece.ELECTRO_ARC, center, target.getBoundingBox().getCenter(), 1.4, null);
            if (target instanceof Creeper creeper && bolt != null) creeper.thunderHit(level, bolt);
            shock(player, target, damage);
            Vec3 away = target.position().subtract(player.position()).multiply(1.0D, 0.0D, 1.0D);
            if (away.lengthSqr() > 0.01D) setVelocity(target, away.normalize().scale(1.2D).add(0.0D, 0.45D, 0.0D));
        }
        // A crown of strikes around her.
        int strikes = radius > 10 ? 6 : 3;
        double rotation = random.nextDouble() * Math.PI * 2;
        for (int strike = 0; strike < strikes; strike++) {
            double angle = rotation + strike * Math.PI * 2 / strikes;
            double distance = 3.0D + random.nextDouble() * (radius - 3.0D);
            Vec3 at = ground(level, player.position().add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance));
            skyBolt(player, at, 24, .8, strike * 2);
        }
        Vec3 floor = ground(level, player.position(), .04, 6);
        show(level, Piece.ELECTRO_PULSE, floor, floor, radius, null);
        // A power surge in the literal sense: every machine in reach takes five bolts' worth, and an arc shows it.
        for (BlockPos machine : GridDisruption.chargeArea(player, level, player.blockPosition(), (int) radius,
                COMMON.gridLightningCharge.get() * 5, 16))
            show(level, Piece.ELECTRO_ARC, center, Vec3.atCenterOf(machine), 1.2, null);
        flashLight(level, center, 6);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 2.0F, 1.0F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0F, 1.2F);
        CameraShakePayload.sendAround(level, center, radius * 2.0D, 2.0F, 16);
        ScreenTintPayload.sendAround(level, center, radius * 1.5D, 0x49BBD6FF, 7, ScreenTintPayload.Style.FLASH);
        if (player.isInWaterOrBubble()) {
            // Discharging this much power in water is nearly suicidal.
            player.hurt(backfire(player), 8.0F);
            player.displayClientMessage(Component.literal("The water drives the surge straight back into you!")
                    .withStyle(ChatFormatting.RED), true);
        }
        MasteryService.markPowerUse(player, ModAbilities.ELECTROKINESIS_ID);
    }

    private static void tickSurge(Surge surge, long now) {
        ServerPlayer player = surge.player;
        if (!player.isAlive() || player.isRemoved() || !PowerRules.canAct(player) || !surge.dimension.equals(player.level().dimension())) {
            cancelSurge(player);
            return;
        }
        long remaining = surge.releaseAt - now;
        if (remaining <= 0) {
            SURGES.remove(player.getUUID());
            ArmPosePayload.show(player, ArmPose.ELECTRO_RELEASE, 12);
            stormSurge(player, surge.level, surge.radius);
            return;
        }
        if (remaining % 4 == 0) show(player.serverLevel(), Piece.ELECTRO_COIL, player.position(), player.position(),
                1 - remaining / (double) SURGE_TICKS, player);
        if (remaining % 8 == 0) sound(player, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.9F, 1.0F + (SURGE_TICKS - remaining) / 30F);
        addCharge(player, 1);
    }

    // ------------------------------------------------------------------
    // Passives and overexertion
    // ------------------------------------------------------------------

    /**
     * Lightning cannot hurt Elle; it recharges her and fills her charge.
     *
     * @return true when the lightning damage should be cancelled
     */
    public static boolean absorbLightning(ServerPlayer player) {
        PlayerEnergyService.restore(player, 30);
        fillCharge(player);
        show(player.serverLevel(), Piece.ELECTRO_AURA, player.position(), player.position(), 2, player);
        player.displayClientMessage(Component.literal("You drink in the lightning.").withStyle(ChatFormatting.AQUA), true);
        ScreenTintPayload.send(player, ELECTRIC_TINT, 30, ScreenTintPayload.Style.VIGNETTE);
        return true;
    }

    /** Thunderstorms charge Elle faster: bonus energy per second. */
    public static int stormEnergyBonus(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        return level.isThundering() && level.isRainingAt(player.blockPosition()) ? 2 : 0;
    }

    /** Overexertion: Elle loses control and the current lashes out at everyone. */
    public static void uncontrolledDischarge(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position().add(0.0D, 1.0D, 0.0D);
        drainCharge(player);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(5.0D),
                entity -> hostile(player, entity) && entity.distanceToSqr(player) <= 25
                        && visible(player, center, entity.getBoundingBox().getCenter()))) {
            drawArc(level, center, entity.getBoundingBox().getCenter(), level.getRandom());
            entity.hurt(backfire(player), 3.0F);
        }
        show(level, Piece.ELECTRO_PULSE, player.position(), player.position(), 5, null);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200, 1));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 0.6F);
        player.sendSystemMessage(Component.literal("You lose control of the current!").withStyle(ChatFormatting.RED));
    }

    /** Faint static around Elle while a storm is feeding her. */
    public static void ambientCrackle(ServerPlayer player, long gameTime) {
        if (gameTime % 10 == 0 && stormEnergyBonus(player) > 0) {
            player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    player.getX(), player.getY() + 1.0D, player.getZ(), 3, 0.35D, 0.6D, 0.35D, 0.15D);
        }
    }

    // ------------------------------------------------------------------
    // Tick and lifecycle
    // ------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (Hold hold : List.copyOf(HOLDS.values())) tickHold(hold, hold.player.serverLevel().getGameTime());
        for (Surge surge : List.copyOf(SURGES.values())) tickSurge(surge, surge.player.serverLevel().getGameTime());

        for (Iterator<Orb> iterator = ORBS.iterator(); iterator.hasNext(); ) {
            Orb orb = iterator.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(orb.ownerId);
            if (owner == null || !owner.isAlive() || owner.level() != orb.level || tickOrb(owner, orb)) iterator.remove();
        }
        for (Strike strike : List.copyOf(STRIKES)) {
            if (strike.level.getGameTime() < strike.due) continue;
            STRIKES.remove(strike);
            ServerPlayer owner = server.getPlayerList().getPlayer(strike.owner);
            if (owner != null && owner.isAlive() && owner.level() == strike.level && strike.level.hasChunkAt(BlockPos.containing(strike.at))) land(strike, owner);
        }
        for (Scar scar : List.copyOf(SCARS)) {
            ServerPlayer owner = server.getPlayerList().getPlayer(scar.owner);
            long time = scar.level.getGameTime();
            if (owner == null || !owner.isAlive() || owner.level() != scar.level || time >= scar.until) { SCARS.remove(scar); continue; }
            tickScar(scar, owner, time);
        }
        for (var entry : LIGHTS.entrySet()) {
            ServerLevel level = entry.getKey();
            entry.getValue().entrySet().removeIf(light -> {
                if (level.getGameTime() < light.getValue()) return false;
                if (level.isLoaded(light.getKey()) && level.getBlockState(light.getKey()).is(Blocks.LIGHT))
                    level.setBlock(light.getKey(), Blocks.AIR.defaultBlockState(), 3);
                return true;
            });
        }
        LIGHTS.values().removeIf(Map::isEmpty);
    }

    /** Ends everything this player has running (logout, death, a new dimension, losing the power). */
    public static void forget(ServerPlayer player) {
        UUID id = player.getUUID();
        if (HOLDS.remove(id) != null) ArmPosePayload.clear(player);
        cancelSurge(player);
        BlackoutService.forget(player);
        ORBS.removeIf(orb -> orb.ownerId.equals(id));
        STRIKES.removeIf(strike -> strike.owner.equals(id));
        SCARS.removeIf(scar -> scar.owner.equals(id));
        CHARGE.remove(id);
    }

    /** Server stopping: put out every lightning flash and drop all state. */
    public static void clear() {
        LIGHTS.forEach((level, lights) -> lights.keySet().forEach(pos -> {
            if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.LIGHT)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }));
        LIGHTS.clear();
        CHARGE.clear(); HOLDS.clear(); ORBS.clear(); STRIKES.clear(); SURGES.clear(); SCARS.clear();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    public static boolean hostile(ServerPlayer player, LivingEntity target) {
        return FlightSkillService.hostile(player, target);
    }

    private static boolean visible(ServerPlayer player, Vec3 from, Vec3 to) {
        return player.serverLevel().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    private static void shock(ServerPlayer player, LivingEntity target, float damage) {
        shock(player, target, damage, false);
    }

    /** One electrokinetic hit. Wet targets take half again as much. Streams ignore the usual hit cooldown. */
    private static void shock(ServerPlayer player, LivingEntity target, float damage, boolean continuous) {
        if (!target.isAlive()) return;
        com.jayptucker.heroesevolved.expansion.PowerReactions.electric(player, target);
        float amount = target.isInWaterRainOrBubble() ? damage * 1.5F : damage;
        // Power damage uses the player-attack type for aggro and kill credit; the depth counter
        // stops Static Touch from treating it as a punch.
        powerDamageDepth++;
        try {
            if (continuous) target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), amount);
        } finally {
            powerDamageDepth--;
        }
        addCharge(player, continuous ? 2 : 3);
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, target.getX(),
                    target.getY() + target.getBbHeight() / 2, target.getZ(), continuous ? 3 : 10, 0.25D, 0.35D, 0.25D, 0.3D);
        }
    }

    /**
     * Your own current coming back through water. Not lightning damage: lightning feeds an
     * electrokinetic, and this must not.
     */
    private static net.minecraft.world.damagesource.DamageSource backfire(ServerPlayer player) {
        return player.damageSources().magic();
    }

    private static void backfireIfWet(ServerPlayer player, int powerLevel) {
        if (player.isInWaterOrBubble()) {
            player.hurt(backfire(player), 1.0F + powerLevel);
            player.displayClientMessage(Component.literal("The water carries the current back into you!")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    /**
     * Current reaching a machine works it, the way a real strike would: a lightning rod gives off a
     * redstone pulse and copper bulbs toggle, and anything that stores Forge Energy (a Mekanism cube, a
     * Thermal cell, any FE machine) is charged by {@code fe}. Nothing is broken or placed.
     */
    private static void energize(ServerLevel level, BlockPos pos, ServerPlayer owner, int fe) {
        for (BlockPos near : new BlockPos[] {pos, pos.above(), pos.below()}) {
            if (GridDisruption.charge(owner, level, near, fe) > 0) return;
        }
        for (BlockPos near : new BlockPos[] {pos, pos.above(), pos.below()}) {
            BlockState state = level.getBlockState(near);
            if (state.getBlock() instanceof LightningRodBlock rod) {
                rod.onLightningStrike(state, level, near);
                return;
            }
            if (state.getBlock() instanceof CopperBulbBlock) {
                level.setBlock(near, state.cycle(CopperBulbBlock.LIT), 3);
                level.playSound(null, near, state.getValue(CopperBulbBlock.LIT) ? SoundEvents.COPPER_BULB_TURN_OFF : SoundEvents.COPPER_BULB_TURN_ON,
                        SoundSource.BLOCKS, 1.0F, 1.0F);
                return;
            }
        }
    }

    /**
     * A real flash: an invisible light block for a few ticks where the lightning is, so a strike lights
     * up a dark cave or the bunker. Only ever placed in empty air, and always removed again.
     */
    private static void flashLight(ServerLevel level, Vec3 at, int ticks) {
        BlockPos pos = BlockPos.containing(at);
        if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) return;
        Map<BlockPos, Long> lights = LIGHTS.computeIfAbsent(level, ignored -> new HashMap<>());
        if (lights.size() > 64) return;
        BlockState state = level.getBlockState(pos);
        long until = level.getGameTime() + ticks;
        if (lights.containsKey(pos)) { lights.put(pos, Math.max(lights.get(pos), until)); return; }
        if (!state.isAir()) return;
        level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 3);
        lights.put(pos.immutable(), until);
    }

    /** The first standing surface at or below a point (within a few blocks), for strikes that land beside the aim. */
    private static Vec3 ground(ServerLevel level, Vec3 at) {
        return ground(level, at, 4, 6);
    }

    private static Vec3 ground(ServerLevel level, Vec3 at, double up, double down) {
        BlockHitResult hit = level.clip(new ClipContext(at.add(0, up, 0), at.add(0, -down, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContextHolder.EMPTY));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : at;
    }

    private static LivingEntity nearestUnstruck(ServerLevel level, LivingEntity from, ServerPlayer caster, Set<UUID> struck, double range) {
        return level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(range),
                        entity -> hostile(caster, entity) && !struck.contains(entity.getUUID()) && from.distanceToSqr(entity) <= range * range
                                && visible(caster, from.getBoundingBox().getCenter(), entity.getBoundingBox().getCenter()))
                .stream().min(Comparator.comparingDouble(from::distanceToSqr)).orElse(null);
    }

    /**
     * A lightning bolt that is never added to the world: it only lets vanilla's lightning reactions run
     * (charged creepers, witches, zombified piglins). The strike itself is our own branched bolt, so
     * nothing ever catches fire from the terrain.
     */
    private static LightningBolt reactionBolt(ServerLevel level, Vec3 position, ServerPlayer cause) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(position.x, position.y, position.z);
            bolt.setVisualOnly(true);
            bolt.setCause(cause);
        }
        return bolt;
    }

    /** The crack and the rolling thunder of a strike, heard from far away like vanilla lightning. */
    private static void thunder(ServerLevel level, Vec3 at, float volume) {
        RandomSource random = level.getRandom();
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 8.0F * volume, 0.8F + random.nextFloat() * 0.2F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 2.0F * volume, 0.5F + random.nextFloat() * 0.2F);
    }

    private static void skyBolt(ServerPlayer owner, Vec3 impact, double height, double size, int delay) {
        ServerLevel level = owner.serverLevel();
        Vec3 floor = impact.add(0, .085, 0);
        Vec3 sky = level.clip(new ClipContext(floor, floor.add(0, height, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, owner)).getLocation();
        show(level, Piece.ELECTRO_STRIKE, floor, sky, size, null, delay);
    }

    /** A jagged arc of current between two points. */
    public static void drawArc(ServerLevel level, Vec3 from, Vec3 to, RandomSource random) {
        if (from.distanceToSqr(to) < 0.0001D) return;
        show(level, Piece.ELECTRO_ARC, from, to, 1, null);
    }

    private static Vec3 handPosition(ServerPlayer player) {
        return hand(player, 1, .68);
    }

    private static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void setVelocity(Entity entity, Vec3 velocity) {
        entity.setDeltaMovement(velocity);
        if (entity instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
        else entity.hurtMarked = true;
    }

    /** ClipContext needs an entity or a collision context; ground probes have neither. */
    private static final class CollisionContextHolder {
        static final net.minecraft.world.phys.shapes.CollisionContext EMPTY = net.minecraft.world.phys.shapes.CollisionContext.empty();
    }
}
