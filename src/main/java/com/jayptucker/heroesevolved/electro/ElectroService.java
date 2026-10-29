package com.jayptucker.heroesevolved.electro;

import com.jayptucker.heroesevolved.ability.AbilityRanges;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
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
    private static final int STREAM_TICKS = 80, STRIKE_DELAY = 16, SURGE_TICKS = 30, SCAR_TICKS = 60;
    private static final int ELECTRIC_TINT = 0x5555AAFF;
    private static final int ORB_LIFETIME_TICKS = AbilityRanges.ranged(50);

    private static final class Charge { int value; long lastGain; int tier; }
    private static final class Hold {
        final ServerPlayer player; final int level; final long started; final ResourceKey<Level> dimension;
        boolean streaming; LivingEntity target;
        Hold(ServerPlayer player, int level, long started) {
            this.player = player; this.level = level; this.started = started; this.dimension = player.level().dimension();
        }
    }
    private record Strike(UUID owner, ServerLevel level, Vec3 at, long due, int powerLevel, boolean water) {}
    private record Surge(ServerPlayer player, int level, long releaseAt, ResourceKey<Level> dimension) {}
    private record Scar(UUID owner, ServerLevel level, Vec3 start, Vec3 end, long until, int powerLevel, Set<UUID> struck) {}

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
        if (value >= CHARGED && player.tickCount % 4 == 0)
            AbilityEffects.send(player.serverLevel(), Kind.ELECTRIC_AURA, player.position(), player.position().add(0, 1, 0), value >= MAX_CHARGE ? 2 : 1);
        ambientCrackle(player, now);
    }

    /** Stepping into water with charge in you: it all goes out at once, through the water and through you. */
    private static void waterDischarge(ServerPlayer player) {
        int spent = drainCharge(player);
        ServerLevel level = player.serverLevel();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(5),
                entity -> hostile(player, entity) && entity.isInWaterOrBubble())) {
            drawArc(level, player.position().add(0, 0.5, 0), entity.getBoundingBox().getCenter(), level.getRandom());
            shock(player, entity, 2 + spent / 12F);
        }
        AbilityEffects.send(level, Kind.ELECTRIC_IMPACT, player.position().add(0, 0.6, 0), player.position(), 4);
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

    /** The Chain Lightning key went down. A quick release fires the bolt; holding on becomes Live Current. */
    public static void press(ServerPlayer player, int powerLevel) {
        HOLDS.put(player.getUUID(), new Hold(player, powerLevel, player.serverLevel().getGameTime()));
    }

    /** The key came up: a tap fires Chain Lightning, a hold ends the stream. */
    public static void release(ServerPlayer player) {
        Hold hold = HOLDS.remove(player.getUUID());
        if (hold == null) return;
        if (hold.streaming) endStream(player);
        else chainLightning(player, hold.level);
    }

    /** Shift + key while streaming: stop at once. */
    public static boolean stopStream(ServerPlayer player) {
        Hold hold = HOLDS.remove(player.getUUID());
        if (hold == null) return false;
        if (hold.streaming) endStream(player);
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
        boolean charged = charge(player) >= CHARGED;
        LivingEntity target = Targeting.livingEntityInSight(player, 16.0D, entity -> hostile(player, entity));
        boolean over = target != null && spendOvercharge(player);

        if (target == null) {
            BlockHitResult hit = level.clip(new ClipContext(player.getEyePosition(),
                    player.getEyePosition().add(player.getLookAngle().scale(AbilityRanges.ranged(16.0D))),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            AbilityEffects.send(level, Kind.ELECTRIC_ARC, hand, hit.getLocation(), 1.0D);
            if (hit.getType() == HitResult.Type.BLOCK) {
                AbilityEffects.send(level, Kind.ELECTRIC_IMPACT, hit.getLocation(), hit.getLocation(), 1.2D);
                energize(level, hit.getBlockPos());
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
                AbilityEffects.send(level, Kind.ELECTRIC_ARC, from, to, over ? 1.7D : hop == 0 ? 1.25D : 1.0D);
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
        ArmPosePayload.show(player, ArmPose.ONE_HAND, 8);
        backfireIfWet(player, powerLevel);
        MasteryService.markPowerUse(player, ModAbilities.ELECTROKINESIS_ID);
    }

    private static void tickHold(Hold hold, long now) {
        ServerPlayer player = hold.player;
        if (!player.isAlive() || player.isRemoved() || !PowerRules.canAct(player)
                || !hold.dimension.equals(player.level().dimension())) {
            HOLDS.remove(player.getUUID());
            if (hold.streaming) endStream(player);
            return;
        }
        long held = now - hold.started;
        if (!hold.streaming) {
            if (held < TAP_TICKS) return;
            hold.streaming = true;
            ArmPosePayload.show(player, ArmPose.ONE_HAND, STREAM_TICKS + 10);
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
        if (target != null && (!target.isAlive() || target.distanceTo(player) > range + 1
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
        AbilityEffects.send(level, Kind.ELECTRIC_STREAM, hand, end, charge(player) >= CHARGED ? 1.5 : 1.0);
        if (!damageTick) return;
        if (target != null) {
            shock(player, target, 1.5F + hold.level * 0.3F, true);
            // Current locks the muscles: the target seizes and twitches.
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 4, false, false));
            if (target instanceof Mob mob) mob.getNavigation().stop();
            RandomSource random = level.getRandom();
            setVelocity(target, target.getDeltaMovement().multiply(0.2, 1, 0.2)
                    .add((random.nextDouble() - 0.5) * 0.12, 0, (random.nextDouble() - 0.5) * 0.12));
        } else if (hit.getType() == HitResult.Type.BLOCK && level.getGameTime() % 10 == 0) {
            energize(level, hit.getBlockPos());
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
        if (!PlayerEnergyService.tryConsume(player, STATIC_TOUCH_ENERGY_COST)) return 0.0F;
        MasteryService.awardEnergySpent(player, STATIC_TOUCH_ENERGY_COST);
        ServerLevel level = player.serverLevel();
        drawArc(level, handPosition(player), target.getBoundingBox().getCenter(), level.getRandom());
        AbilityEffects.send(level, Kind.ELECTRIC_IMPACT, target.getBoundingBox().getCenter(), target.position(), 0.8D);
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
    public static void ballLightning(ServerPlayer player, int powerLevel) {
        boolean over = spendOvercharge(player);
        ORBS.add(new Orb(player.getUUID(), player.serverLevel(), handPosition(player),
                player.getLookAngle().normalize().scale(0.55D), powerLevel, over));
        sound(player, SoundEvents.BREEZE_SHOOT, 0.8F, 1.8F);
        sound(player, SoundEvents.BEACON_POWER_SELECT, 0.4F, 2.0F);
        backfireIfWet(player, powerLevel);
        MasteryService.markPowerUse(player, ModAbilities.ELECTROKINESIS_ID);
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

    /** @return true once the orb has burst */
    private static boolean tickOrb(ServerPlayer owner, Orb orb) {
        ServerLevel level = orb.level;
        orb.ageTicks++;
        // It curves gently toward the nearest foe ahead of it.
        Vec3 heading = orb.velocity.normalize();
        LivingEntity prey = level.getEntitiesOfClass(LivingEntity.class, new AABB(orb.position, orb.position).inflate(6.0D),
                        entity -> hostile(owner, entity) && entity.getBoundingBox().getCenter().subtract(orb.position).normalize().dot(heading) > 0.3)
                .stream().min(Comparator.comparingDouble(entity -> entity.distanceToSqr(orb.position))).orElse(null);
        if (prey != null) {
            Vec3 toward = prey.getBoundingBox().getCenter().subtract(orb.position).normalize();
            orb.velocity = heading.scale(0.85D).add(toward.scale(0.15D)).normalize().scale(orb.velocity.length());
        }
        Vec3 next = orb.position.add(orb.velocity);

        BlockHitResult blockHit = level.clip(new ClipContext(orb.position, next,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        LivingEntity touched = level.getEntitiesOfClass(LivingEntity.class, new AABB(next, next).inflate(0.6D),
                entity -> hostile(owner, entity)).stream().findFirst().orElse(null);
        if (blockHit.getType() != HitResult.Type.MISS || touched != null || orb.ageTicks >= ORB_LIFETIME_TICKS) {
            burst(owner, orb, blockHit.getType() != HitResult.Type.MISS ? blockHit.getLocation() : next);
            return true;
        }

        orb.position = next;
        if (orb.ageTicks % 2 == 0)
            AbilityEffects.send(level, Kind.ELECTRIC_ORB, next, next.add(orb.velocity), orb.empowered ? 0.6D : 0.42D);
        if (orb.ageTicks % 4 == 0) {
            List<LivingEntity> nearest = level.getEntitiesOfClass(LivingEntity.class, new AABB(next, next).inflate(4.0D),
                            entity -> hostile(owner, entity))
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
        double radius = orb.empowered ? 4.5D : 3.0D;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                entity -> hostile(owner, entity))) {
            drawArc(level, at, entity.getBoundingBox().getCenter(), level.getRandom());
            shock(owner, entity, 5.0F + orb.powerLevel + (orb.empowered ? 3.0F : 0.0F));
        }
        AbilityEffects.send(level, Kind.ELECTRIC_IMPACT, at, at.add(0.0D, 1.0D, 0.0D), radius);
        flashLight(level, at, 4);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.9F, 1.3F);
        if (orb.empowered) level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    private static final class Orb {
        private final UUID ownerId;
        private final ServerLevel level;
        private final int powerLevel;
        private final boolean empowered;
        private Vec3 velocity;
        private Vec3 position;
        private int ageTicks;

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
        AbilityEffects.send(level, Kind.ELECTRIC_TELEGRAPH, impact, impact.add(0, 1, 0), 3.5D);
        if (over) {
            RandomSource random = level.getRandom();
            for (int i = 1; i <= 2; i++) {
                double angle = random.nextDouble() * Math.PI * 2, distance = 2.5 + random.nextDouble() * 2;
                Vec3 at = ground(level, impact.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance));
                STRIKES.add(new Strike(player.getUUID(), level, at, now + STRIKE_DELAY + i * 6, powerLevel, water));
                AbilityEffects.send(level, Kind.ELECTRIC_TELEGRAPH, at, at.add(0, 1, 0), 2.5D);
            }
        }
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0F, 1.4F);
        ArmPosePayload.show(player, ArmPose.ARMS_RAISED, STRIKE_DELAY + 6);
        MasteryService.markPowerUse(player, ModAbilities.ELECTROKINESIS_ID);
        return true;
    }

    public static int pendingStrikes(ServerPlayer player) {
        return (int) STRIKES.stream().filter(strike -> strike.owner.equals(player.getUUID())).count();
    }

    private static void land(Strike strike, ServerPlayer owner) {
        ServerLevel level = strike.level;
        Vec3 impact = strike.at;
        LightningBolt bolt = reactionBolt(level, impact, owner);
        AbilityEffects.send(level, Kind.ELECTRIC_SKY_BOLT, impact, impact.add(0, 28, 0), 1.0D);
        thunder(level, impact, 1.0F);
        float damage = 4.0F + strike.powerLevel * 2.0F;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(impact, impact).inflate(3.5D),
                entity -> entity != owner && entity.isAlive())) {
            if (TemporalEntityEchoService.isReplaying(entity)) TemporalEntityEchoService.disturb(entity, owner);
            if (!hostile(owner, entity)) continue;
            // Vanilla's lightning reactions: charged creepers, witches, zombified piglins and the rest.
            if (bolt != null) entity.thunderHit(level, bolt);
            shock(owner, entity, damage);
            setVelocity(entity, entity.getDeltaMovement().add(0, 0.35, 0));
        }
        if (strike.water) {
            // Water carries it: everyone standing or swimming in water nearby is shocked too.
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(impact, impact).inflate(8.0D),
                    entity -> hostile(owner, entity) && entity.isInWaterOrBubble())) {
                AbilityEffects.send(level, Kind.ELECTRIC_IMPACT, entity.position().add(0, 0.5, 0), entity.position(), 1.5D);
                shock(owner, entity, damage * 0.6F);
            }
        }
        energize(level, BlockPos.containing(impact.subtract(0, 0.1, 0)));
        flashLight(level, impact.add(0, 1, 0), 5);
        CameraShakePayload.sendAround(level, impact, 24.0D, 1.4F, 10);
        ScreenTintPayload.sendAround(level, impact, 24.0D, 0x40FFFFFF, 5, ScreenTintPayload.Style.FLASH);
    }

    // ------------------------------------------------------------------
    // Ride the Lightning
    // ------------------------------------------------------------------

    /** Elle becomes a streak of current and reappears down the line, leaving a live scar on the ground. */
    public static void rideTheLightning(ServerPlayer player, int powerLevel) {
        ServerLevel level = player.serverLevel();
        boolean over = spendOvercharge(player);
        Vec3 direction = player.getLookAngle().multiply(1.0D, 0.35D, 1.0D).normalize();
        double maximumDistance = AbilityRanges.ranged(10.0D + powerLevel) * (over ? 1.6D : 1.0D);
        Vec3 start = player.position();
        Vec3 end = start;
        // Walk forward, following the ground: when the next step would clip something, keep the
        // current height, then try stepping up a ledge, and stop only at a real wall.
        AABB body = player.getBoundingBox();
        for (double distance = 0.5D; distance <= maximumDistance; distance += 0.5D) {
            Vec3 candidate = end.add(direction.scale(0.5D));
            Vec3 chosen = null;
            for (Vec3 option : new Vec3[] {candidate, new Vec3(candidate.x, end.y, candidate.z),
                    new Vec3(candidate.x, end.y + 0.6D, candidate.z), new Vec3(candidate.x, end.y + 1.1D, candidate.z)}) {
                if (level.noCollision(player, body.move(option.subtract(start)))) { chosen = option; break; }
            }
            if (chosen == null) break;
            end = chosen;
        }
        if (end.distanceToSqr(start) < 1.0D) return;

        float damage = 4.0F + powerLevel;
        Set<UUID> struck = new HashSet<>();
        AABB path = new AABB(start, end).inflate(1.2D);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, path, entity -> hostile(player, entity))) {
            shock(player, entity, damage);
            struck.add(entity.getUUID());
        }
        AbilityEffects.send(level, Kind.ELECTRIC_DASH, start, end, 1.0D);
        sound(player, SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.7F, 1.6F);
        player.teleportTo(end.x, end.y, end.z);
        player.resetFallDistance();
        level.playSound(null, end.x, end.y, end.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F, 1.4F);
        ScreenTintPayload.send(player, 0x40AACCFF, 6, ScreenTintPayload.Style.FLASH);
        flashLight(level, end.add(0, 1, 0), 4);
        SCARS.add(new Scar(player.getUUID(), level, ground(level, start), ground(level, end), level.getGameTime() + SCAR_TICKS, powerLevel, struck));
        MasteryService.markPowerUse(player, ModAbilities.ELECTROKINESIS_ID);
    }

    private static void tickScar(Scar scar, ServerPlayer owner, long now) {
        if (now % 10 == 0) AbilityEffects.send(scar.level, Kind.ELECTRIC_SCAR, scar.start, scar.end, 1.0D);
        if (now % 2 != 0) return;
        Vec3 line = scar.end.subtract(scar.start);
        double lengthSqr = Math.max(1.0E-4, line.lengthSqr());
        for (LivingEntity entity : scar.level.getEntitiesOfClass(LivingEntity.class, new AABB(scar.start, scar.end).inflate(1.0D, 1.5D, 1.0D),
                entity -> hostile(owner, entity) && !scar.struck.contains(entity.getUUID()))) {
            double t = Math.clamp(entity.position().subtract(scar.start).dot(line) / lengthSqr, 0, 1);
            Vec3 nearest = scar.start.add(line.scale(t));
            if (entity.position().distanceToSqr(nearest) > 0.9 * 0.9 + 1.2 * 1.2) continue;
            scar.struck.add(entity.getUUID());
            AbilityEffects.send(scar.level, Kind.ELECTRIC_IMPACT, entity.position().add(0, 0.3, 0), entity.position(), 1.0D);
            shock(owner, entity, 2.0F + scar.powerLevel);
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
        }
    }

    // ------------------------------------------------------------------
    // Storm Surge
    // ------------------------------------------------------------------

    public static boolean isSurging(ServerPlayer player) {
        return SURGES.containsKey(player.getUUID());
    }

    /** Gathers the storm for a moment and a half, lifting Elle off her feet. Shift cancels. */
    public static boolean startSurge(ServerPlayer player, int powerLevel) {
        SURGES.put(player.getUUID(), new Surge(player, powerLevel, player.serverLevel().getGameTime() + SURGE_TICKS, player.level().dimension()));
        ArmPosePayload.show(player, ArmPose.ARMS_WIDE, SURGE_TICKS + 12);
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
        ServerLevel level = player.serverLevel();
        boolean storm = level.isRainingAt(player.blockPosition());
        double radius = storm ? 16.0D : 10.0D;
        float damage = 8.0F + powerLevel + drainCharge(player) / 10.0F;
        Vec3 center = player.position().add(0.0D, 1.0D, 0.0D);
        RandomSource random = level.getRandom();

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                        entity -> hostile(player, entity) && entity.distanceToSqr(player) <= radius * radius)
                .stream().sorted(Comparator.comparingDouble(player::distanceToSqr)).limit(24).toList();
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        for (LivingEntity target : targets) {
            AbilityEffects.send(level, Kind.ELECTRIC_ARC, center, target.getBoundingBox().getCenter(), 1.4D);
            if (target instanceof Creeper creeper && bolt != null) creeper.thunderHit(level, bolt);
            shock(player, target, damage);
            Vec3 away = target.position().subtract(player.position()).multiply(1.0D, 0.0D, 1.0D);
            if (away.lengthSqr() > 0.01D) setVelocity(target, away.normalize().scale(1.2D).add(0.0D, 0.45D, 0.0D));
        }
        // A crown of strikes around her.
        for (int strike = 0; strike < (storm ? 6 : 3); strike++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = 3.0D + random.nextDouble() * (radius - 3.0D);
            Vec3 at = ground(level, player.position().add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance));
            AbilityEffects.send(level, Kind.ELECTRIC_SKY_BOLT, at, at.add(0, 24, 0), 0.8D);
        }
        AbilityEffects.send(level, Kind.ELECTRIC_NOVA, player.position(), player.position().add(0, 1, 0), radius);
        flashLight(level, center, 6);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 2.0F, 1.0F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0F, 1.2F);
        CameraShakePayload.sendAround(level, center, radius * 2.0D, 2.0F, 16);
        ScreenTintPayload.sendAround(level, center, radius * 1.5D, 0x80FFFFFF, 10, ScreenTintPayload.Style.FLASH);
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
            ArmPosePayload.show(player, ArmPose.ARMS_WIDE, 12);
            stormSurge(player, surge.level);
            return;
        }
        if (remaining % 2 == 0) AbilityEffects.send(player.serverLevel(), Kind.ELECTRIC_SURGE_CHARGE, player.position(),
                player.position().add(0, 1, 0), 1 - remaining / (double) SURGE_TICKS);
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
        AbilityEffects.send(player.serverLevel(), Kind.ELECTRIC_AURA, player.position(), player.position().add(0, 1, 0), 2);
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
                entity -> entity != player && entity.isAlive())) {
            drawArc(level, center, entity.getBoundingBox().getCenter(), level.getRandom());
            entity.hurt(backfire(player), 3.0F);
        }
        AbilityEffects.send(level, Kind.ELECTRIC_NOVA, player.position(), player.position().add(0, 1, 0), 5.0D);
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
            if (owner == null || owner.level() != orb.level || tickOrb(owner, orb)) iterator.remove();
        }
        for (Strike strike : List.copyOf(STRIKES)) {
            if (strike.level.getGameTime() < strike.due) continue;
            STRIKES.remove(strike);
            ServerPlayer owner = server.getPlayerList().getPlayer(strike.owner);
            if (owner != null && owner.level() == strike.level) land(strike, owner);
        }
        for (Scar scar : List.copyOf(SCARS)) {
            ServerPlayer owner = server.getPlayerList().getPlayer(scar.owner);
            long time = scar.level.getGameTime();
            if (owner == null || time >= scar.until) { SCARS.remove(scar); continue; }
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
        SURGES.remove(id);
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

    private static boolean hostile(ServerPlayer player, LivingEntity target) {
        return ExpansionSkills.hostile(player, target);
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
     * redstone pulse and copper bulbs toggle. Nothing is broken or placed.
     */
    private static void energize(ServerLevel level, BlockPos pos) {
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
        BlockHitResult hit = level.clip(new ClipContext(at.add(0, 4, 0), at.add(0, -6, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContextHolder.EMPTY));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : at;
    }

    private static LivingEntity nearestUnstruck(ServerLevel level, LivingEntity from, ServerPlayer caster, Set<UUID> struck, double range) {
        return level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(range),
                        entity -> hostile(caster, entity) && !struck.contains(entity.getUUID()))
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

    /** A jagged arc of current between two points. */
    public static void drawArc(ServerLevel level, Vec3 from, Vec3 to, RandomSource random) {
        if (from.distanceToSqr(to) < 0.0001D) return;
        AbilityEffects.send(level, Kind.ELECTRIC_ARC, from, to, 1.0D);
    }

    private static Vec3 handPosition(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
        return player.getEyePosition().add(look.scale(0.5D)).add(right.scale(0.3D)).add(0.0D, -0.35D, 0.0D);
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
