package com.jayptucker.heroesevolved.quake;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.network.StrainPayload;
import com.jayptucker.heroesevolved.progression.EvolutionTrials;
import com.jayptucker.heroesevolved.progression.MasteryService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Daisy Johnson's side of Quake: what the power costs her body, and the two things she learned to do
 * that a blast can't.
 * <ul>
 *   <li><b>Strain</b> (0 to 100): every quake builds it. The more strained she is, the harder she hits,
 *   up to half again as hard. At 80 her arms shake; at 100 the bones in her arms crack, her powers lock
 *   for a few seconds and she is left weak. Rest brings it down; crouching still brings it down faster.</li>
 *   <li><b>Resonate</b>: she finds one body's own frequency and shakes it from the inside. The inner ear
 *   goes, so it staggers and can't aim; its armour rattles apart; its bones ache.</li>
 *   <li><b>Fault Line</b>: a punch into the ground that opens a crack racing out ahead of her, erupting
 *   under every enemy on its way.</li>
 *   <li><b>Tremor Sense</b> (passive): crouch still for a second and she feels every heartbeat around her
 *   through the walls.</li>
 * </ul>
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class QuakeKit {
    public static final int MAX_STRAIN = 100, PUSHING = 50, BREAKING = 80;
    public static final int SHOCKWAVE_STRAIN = 8, RESONATE_STRAIN = 12, FAULT_STRAIN = 16, BLAST_STRAIN = 3, EARTHQUAKE_STRAIN = 35;
    /** How long a fracture locks her powers, and what Strain she is left with afterwards. */
    public static final int FRACTURE_TICKS = 160, AFTER_FRACTURE = 40;
    private static final int REST_DELAY = 40, STILL_TICKS = 20, SENSE_COOLDOWN = 120;

    private static final class Body {
        final ServerPlayer player;
        int strain, sent = -1, stillTicks;
        long lastUse, fracturedUntil, senseReady;
        boolean sentFractured;
        Vec3 last;
        Body(ServerPlayer player) { this.player = player; last = player.position(); }
    }

    private record Resonance(ServerPlayer owner, LivingEntity target, long until, float damage, Set<EquipmentSlot> rattled, boolean jumped) {}

    private static final class Fissure {
        final ServerPlayer owner;
        final ServerLevel level;
        final Vec3 direction;
        final double length, launch;
        final float damage;
        final Set<UUID> struck = new HashSet<>();
        Vec3 at;
        double travelled;
        Fissure(ServerPlayer owner, Vec3 start, Vec3 direction, double length, float damage, double launch) {
            this.owner = owner; this.level = owner.serverLevel(); this.at = start; this.direction = direction;
            this.length = length; this.damage = damage; this.launch = launch;
        }
    }

    private static final Map<UUID, Body> BODIES = new HashMap<>();
    private static final Map<UUID, Resonance> RESONATING = new HashMap<>();
    private static final List<Fissure> FISSURES = new ArrayList<>();

    private QuakeKit() {}

    private static long now(ServerPlayer p) { return p.getServer().overworld().getGameTime(); }

    private static boolean isQuake(ServerPlayer p) {
        return PlayerAbilityService.getData(p).assignedPower()
                .filter(e -> e.getKey().equals(ModAbilities.QUAKE_ID) && e.getValue().isUnlocked()).isPresent()
                && !EclipseService.arePowersSuppressed(p);
    }

    private static Body body(ServerPlayer p) { return BODIES.computeIfAbsent(p.getUUID(), id -> new Body(p)); }

    // ------------------------------------------------------------------
    // Strain
    // ------------------------------------------------------------------

    public static int strain(ServerPlayer p) { Body b = BODIES.get(p.getUUID()); return b == null ? 0 : b.strain; }

    /** How hard her powers hit: 1 at rest, up to 1.5 at the edge of breaking. */
    public static float power(ServerPlayer p) { return 1 + strain(p) / 200F; }

    public static boolean fractured(ServerPlayer p) {
        Body b = BODIES.get(p.getUUID());
        return b != null && now(p) < b.fracturedUntil;
    }

    /** Adds Strain for one use of a power. Past 100, the bones in her arms crack. */
    public static void strain(ServerPlayer p, int amount) {
        if (amount <= 0) return;
        Body b = body(p);
        b.lastUse = now(p);
        b.strain = Math.min(MAX_STRAIN, b.strain + amount);
        if (b.strain >= MAX_STRAIN) fracture(p, b);
        sync(p, b);
    }

    /** Bleeds Strain off (Kinetic Sink): never below zero, and it doesn't count as using a power. */
    public static void relieve(ServerPlayer p, int amount) {
        Body b = BODIES.get(p.getUUID());
        if (b == null || amount <= 0) return;
        b.strain = Math.max(0, b.strain - amount);
        sync(p, b);
    }

    /** Sets Strain directly (tests and the preview). */
    public static void setStrain(ServerPlayer p, int strain) {
        Body b = body(p);
        b.strain = Math.clamp(strain, 0, MAX_STRAIN);
        b.lastUse = now(p);
        sync(p, b);
    }

    private static void fracture(ServerPlayer p, Body b) {
        b.fracturedUntil = now(p) + FRACTURE_TICKS;
        b.strain = AFTER_FRACTURE;
        QuakeService.stopQuakeBlast(p, false);
        QuakeService.stopEarthquake(p);
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, FRACTURE_TICKS + 100, 1));
        p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, FRACTURE_TICKS + 100, 1));
        p.hurt(p.damageSources().generic(), 3);
        ServerLevel level = p.serverLevel();
        AbilityEffects.send(level, Kind.QUAKE_FRACTURE, p.position(), p.position().add(p.getLookAngle()), 1);
        level.playSound(null, p.blockPosition(), SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 1, 0.5F);
        level.playSound(null, p.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 0.6F);
        ArmPosePayload.show(p, ArmPose.QUAKE_FRACTURED, 40);
        p.displayClientMessage(Component.translatable("quake.heroesevolved.fractured").withStyle(ChatFormatting.DARK_RED), false);
    }

    /** One of her quakes landed. Hits taken at the edge of breaking count toward the Evolved trial. */
    public static void landed(ServerPlayer p, LivingEntity target) {
        if (strain(p) >= BREAKING && target != p) EvolutionTrials.advance(p, ModAbilities.QUAKE_ID);
    }

    private static void sync(ServerPlayer p, Body b) {
        boolean fractured = now(p) < b.fracturedUntil;
        if (b.strain == b.sent && fractured == b.sentFractured) return;
        b.sent = b.strain; b.sentFractured = fractured;
        StrainPayload.send(p, b.strain, fractured);
    }

    // ------------------------------------------------------------------
    // Resonate
    // ------------------------------------------------------------------

    public static boolean resonating(LivingEntity target) { return RESONATING.containsKey(target.getUUID()); }

    /** Level 2, Resonate: find one body's frequency and shake it apart from the inside for three seconds. */
    public static boolean resonate(ServerPlayer p, int tier) {
        LivingEntity target = Targeting.livingEntityInSight(p, 16, t -> ExpansionSkills.hostile(p, t));
        if (target == null) return false;
        float damage = (1.5F + tier * 0.5F) * power(p);
        RESONATING.put(target.getUUID(), new Resonance(p, target, now(p) + 60, damage, new HashSet<>(), false));
        ArmPosePayload.show(p, ArmPose.QUAKE_TUNE, 60);
        ServerLevel level = p.serverLevel();
        AbilityEffects.send(level, Kind.QUAKE_RESONATE, hand(p), target.getBoundingBox().getCenter(), 1);
        level.playSound(null, target.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.8F, 1.8F);
        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1, 0.5F);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
        if (target instanceof ServerPlayer other) {
            other.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0));
            other.displayClientMessage(Component.translatable("quake.heroesevolved.resonated").withStyle(ChatFormatting.RED), true);
        }
        MasteryService.markPowerUse(p, ModAbilities.QUAKE_ID);
        strain(p, RESONATE_STRAIN);
        return true;
    }

    private static void tickResonance(long now) {
        for (Resonance r : List.copyOf(RESONATING.values())) {
            LivingEntity t = r.target;
            if (!t.isAlive() || t.isRemoved() || now >= r.until || t.level() != r.owner.level()) {
                RESONATING.remove(t.getUUID());
                // Sympathetic Frequency: a resonance that runs its course jumps once to the nearest enemy.
                if (now >= r.until && !r.jumped && t.level() == r.owner.level() && SkillTreeService.has(r.owner, QuakeSkills.SYMPATHETIC))
                    jump(r, now);
                continue;
            }
            ServerLevel level = (ServerLevel) t.level();
            long age = 60 - (r.until - now);
            if (age % 4 == 0)
                AbilityEffects.send(level, Kind.QUAKE_RESONATE, t.getBoundingBox().getCenter(), t.getBoundingBox().getCenter(), t.getBbHeight());
            if (age % 10 == 5) {
                // Bones: a deep ache armour does nothing for.
                t.invulnerableTime = 0;
                if (t.hurt(r.owner.damageSources().indirectMagic(r.owner, r.owner), r.damage)) landed(r.owner, t);
                // Armour: every worn piece shakes itself apart a little.
                for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    var worn = t.getItemBySlot(slot);
                    if (!worn.isEmpty() && worn.isDamageableItem()) {
                        worn.hurtAndBreak(4, t, slot);
                        r.rattled.add(slot);
                    }
                }
                level.playSound(null, t.blockPosition(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.5F, 0.6F);
            }
            // The inner ear: it staggers about and can't keep its footing or its aim.
            if (t instanceof Mob mob && age % 6 == 0) {
                mob.getNavigation().stop();
                mob.setTarget(null);
                Vec3 lurch = new Vec3(t.getRandom().nextDouble() - 0.5, 0.05, t.getRandom().nextDouble() - 0.5).scale(0.45);
                QuakeService.setVelocity(mob, t.getDeltaMovement().add(lurch));
            }
        }
    }

    private static void jump(Resonance r, long now) {
        LivingEntity from = r.target;
        LivingEntity next = from.level().getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(6),
                        t -> t != from && !RESONATING.containsKey(t.getUUID()) && ExpansionSkills.hostile(r.owner, t))
                .stream().min(java.util.Comparator.comparingDouble(from::distanceToSqr)).orElse(null);
        if (next == null) return;
        RESONATING.put(next.getUUID(), new Resonance(r.owner, next, now + 30, r.damage, new HashSet<>(), true));
        AbilityEffects.send((ServerLevel) from.level(), Kind.QUAKE_RESONATE, from.getBoundingBox().getCenter(), next.getBoundingBox().getCenter(), 1);
        from.level().playSound(null, next.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1, 0.7F);
    }

    /** Whether {@code p} has a resonance running on anyone. */
    public static boolean resonatingAny(ServerPlayer p) {
        for (Resonance r : RESONATING.values()) if (r.owner == p) return true;
        return false;
    }

    /**
     * Resonate's follow-up, Shatter: close the hand and the frequency collapses at once. Everything it had left to do
     * lands in one blow, half again as hard, the armour takes a heavy rattle and the body is thrown away from her.
     * Free; works on every resonance she has running.
     */
    public static boolean collapse(ServerPlayer p) {
        boolean any = false;
        long now = now(p);
        for (Resonance r : List.copyOf(RESONATING.values())) {
            if (r.owner != p) continue;
            any = true;
            collapse(r, now, 1.0);
        }
        if (any) ArmPosePayload.show(p, ArmPose.QUAKE_CLENCH, 12);
        return any;
    }

    private static void collapse(Resonance r, long now, double launch) {
        LivingEntity t = r.target;
        RESONATING.remove(t.getUUID());
        ServerLevel level = (ServerLevel) t.level();
        int pulses = Math.max(1, (int) ((r.until - now) / 10));
        float burst = r.damage * pulses * 1.5F + 2;
        t.invulnerableTime = 0;
        if (t.hurt(r.owner.damageSources().indirectMagic(r.owner, r.owner), burst)) landed(r.owner, t);
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            var worn = t.getItemBySlot(slot);
            if (!worn.isEmpty() && worn.isDamageableItem()) worn.hurtAndBreak(12, t, slot);
        }
        Vec3 away = t.position().subtract(r.owner.position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : away.normalize();
        QuakeService.setVelocity(t, away.scale(0.9 * launch).add(0, 0.45 * launch, 0));
        Vec3 mid = t.getBoundingBox().getCenter();
        QuakeGeometry.show(level, Piece.QUAKE_SHATTER, mid, mid.add(away), t.getBbHeight(), null);
        level.playSound(null, t.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.2F, 0.6F);
        level.playSound(null, t.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.5F, 1.8F);
    }

    // ------------------------------------------------------------------
    // Fault Line
    // ------------------------------------------------------------------

    /**
     * Fault Line's follow-up, Upheaval: while the crack is still racing out, press again and both palms sweep up: the
     * whole rest of its line erupts at once, throwing everything on it higher. Free.
     */
    public static boolean upheave(ServerPlayer p) {
        Fissure found = null;
        for (Fissure candidate : FISSURES) if (candidate.owner == p) found = candidate;
        if (found == null) return false;
        Fissure f = found;
        FISSURES.remove(f);
        ServerLevel level = f.level;
        Vec3 at = f.at;
        for (double d = 0; f.travelled + d < f.length; d += 1.5) {
            Vec3 next = at.add(f.direction.scale(1.5));
            BlockPos ground = QuakeService.findSurface(level, BlockPos.containing(next).getX(), BlockPos.containing(next).getZ(),
                    BlockPos.containing(at).getY() - 1, deepRoots(p) ? 6 : 3);
            if (ground == null || ground.getY() + 1 - at.y > climb(p)) break;
            at = new Vec3(next.x, ground.getY() + 1, next.z);
            AbilityEffects.send(level, Kind.QUAKE_ERUPT, at, at.add(0, 1, 0), 1.3);
            QuakeService.burstDebris(level, ground, new Vec3(0, 1, 0), 2, 0.6);
            for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(1.5, 2, 1.5),
                    t -> ExpansionSkills.hostile(p, t) && !f.struck.contains(t.getUUID()))) {
                f.struck.add(t.getUUID());
                t.invulnerableTime = 0;
                if (t.hurt(p.damageSources().playerAttack(p), f.damage * 0.8F)) landed(p, t);
                Resonance ringing = RESONATING.get(t.getUUID());
                boolean live = ringing != null && ringing.owner == p && SkillTreeService.has(p, QuakeSkills.LIVE_FAULT);
                if (live) collapse(ringing, level.getGameTime(), 1.4);
                QuakeService.setVelocity(t, new Vec3(0, f.launch * 1.35 * (live ? 2 : 1), 0));
            }
        }
        ArmPosePayload.show(p, ArmPose.QUAKE_UPHEAVAL, 14);
        level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 0.5F);
        CameraShakePayload.sendAround(level, at, 18, 1.4F, 12);
        return true;
    }

    private static boolean deepRoots(ServerPlayer p) { return SkillTreeService.has(p, QuakeSkills.DEEP_ROOTS); }

    /** How high a step the crack can climb: a half block, or a full one with Deep Roots. */
    private static double climb(ServerPlayer p) { return deepRoots(p) ? 2.2 : 1.2; }

    /**
     * Level 3, Fault Line: a punch into the ground that opens a crack racing ahead of her, erupting
     * under every enemy along it. Aimed straight down or thrown from the air, it is the old Seismic Slam.
     */
    public static boolean faultLine(ServerPlayer p, int tier) {
        if (!p.onGround() || p.getXRot() > 60) {
            QuakeService.slam(p, tier);
            strain(p, FAULT_STRAIN);
            return true;
        }
        Vec3 look = p.getLookAngle();
        Vec3 direction = new Vec3(look.x, 0, look.z);
        if (direction.lengthSqr() < 1.0E-4) { QuakeService.slam(p, tier); strain(p, FAULT_STRAIN); return true; }
        direction = direction.normalize();
        FISSURES.add(new Fissure(p, p.position().add(direction.scale(0.8)), direction, 10 + 2 * tier,
                (5 + tier) * power(p), 0.75 + tier * 0.05));
        ServerLevel level = p.serverLevel();
        ArmPosePayload.show(p, ArmPose.QUAKE_PUNCH_DOWN, 12);
        BlockPos under = BlockPos.containing(p.position()).below();
        QuakeService.burstDebris(level, under, new Vec3(0, 1, 0), 4, 0.5);
        AbilityEffects.send(level, Kind.QUAKE_ERUPT, p.position().add(direction), p.position().add(direction).add(0, 1, 0), 1.2);
        level.playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7F, 0.5F);
        level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6F, 0.6F);
        CameraShakePayload.sendAround(level, p.position(), 14, 1.0F, 10);
        MasteryService.markPowerUse(p, ModAbilities.QUAKE_ID);
        strain(p, FAULT_STRAIN);
        return true;
    }

    public static boolean faulting() { return !FISSURES.isEmpty(); }

    private static void tickFissures() {
        for (Fissure f : List.copyOf(FISSURES)) {
            boolean done = false;
            for (int step = 0; step < 2 && !done; step++) {
                Vec3 next = f.at.add(f.direction.scale(0.9));
                BlockPos ground = QuakeService.findSurface(f.level, BlockPos.containing(next).getX(), BlockPos.containing(next).getZ(),
                        BlockPos.containing(f.at).getY() - 1, deepRoots(f.owner) ? 6 : 3);
                // A wall stops it (the crack bursts against it); so does a drop into nothing. Deep Roots climbs a step.
                if (ground == null || ground.getY() + 1 - f.at.y > climb(f.owner)) {
                    BlockPos face = BlockPos.containing(next.add(0, 0.5, 0));
                    QuakeService.burstDebris(f.level, face, f.direction.reverse(), 5, 0.5);
                    AbilityEffects.send(f.level, Kind.QUAKE_ERUPT, f.at, f.at.add(0, 1, 0), 1.4);
                    done = true;
                    break;
                }
                Vec3 point = new Vec3(next.x, ground.getY() + 1, next.z);
                AbilityEffects.send(f.level, Kind.QUAKE_FISSURE, f.at, point, 1);
                f.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, f.level.getBlockState(ground)),
                        point.x, point.y + 0.05, point.z, 6, 0.25, 0.05, 0.25, 0.12);
                if (f.travelled % 3 < 0.9) f.level.playSound(null, ground, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 0.8F, 0.6F);
                f.at = point;
                f.travelled += 0.9;
                for (LivingEntity t : f.level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(point, point).inflate(1.4, 2, 1.4),
                        t -> ExpansionSkills.hostile(f.owner, t) && !f.struck.contains(t.getUUID()))) {
                    f.struck.add(t.getUUID());
                    t.invulnerableTime = 0;
                    if (t.hurt(f.owner.damageSources().playerAttack(f.owner), f.damage)) landed(f.owner, t);
                    // Live Fault: a crack running under her own resonance breaks it, and throws them twice as high.
                    Resonance ringing = RESONATING.get(t.getUUID());
                    boolean live = ringing != null && ringing.owner == f.owner && SkillTreeService.has(f.owner, QuakeSkills.LIVE_FAULT);
                    if (live) collapse(ringing, f.level.getGameTime(), 1.4);
                    QuakeService.setVelocity(t, f.direction.scale(0.25).add(0, f.launch * (live ? 2 : 1), 0));
                    QuakeService.burstDebris(f.level, ground, new Vec3(0, 1, 0), 3, 0.55);
                    AbilityEffects.send(f.level, Kind.QUAKE_ERUPT, point, point.add(0, 1, 0), 1.2);
                    f.level.playSound(null, t.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5F, 0.8F);
                }
                if (f.travelled >= f.length) done = true;
            }
            if (done) FISSURES.remove(f);
        }
    }

    // ------------------------------------------------------------------
    // Tremor Sense, tick and lifecycle
    // ------------------------------------------------------------------

    private static Vec3 hand(ServerPlayer p) {
        return p.getEyePosition().add(p.getLookAngle().scale(0.6)).subtract(0, 0.3, 0);
    }

    /** Crouched and still, she listens: every heartbeat within 20 blocks, through the walls. */
    private static void tickStillness(ServerPlayer p, Body b, long now) {
        boolean still = p.isShiftKeyDown() && p.onGround() && p.position().distanceToSqr(b.last) < 1.0E-4;
        b.last = p.position();
        b.stillTicks = still ? b.stillTicks + 1 : 0;
        if (b.stillTicks == STILL_TICKS && now >= b.senseReady) {
            b.senseReady = now + SENSE_COOLDOWN;
            QuakeService.tremorPulse(p, 100, 20);
            ArmPosePayload.show(p, ArmPose.QUAKE_FEEL, 24);
            heartbeats(p, 20);
        } else if (still && b.stillTicks > STILL_TICKS && (b.stillTicks - STILL_TICKS) % 20 == 0 && QuakeService.hasTremorSense(p)) {
            heartbeats(p, 20);
        }
    }

    /** A pulse at every heart she can feel, seen only by her: red where one is hostile or hurt. */
    static void heartbeats(ServerPlayer p, double radius) {
        for (LivingEntity t : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(radius),
                t -> t != p && t.isAlive() && !t.isSpectator() && t.distanceToSqr(p) <= radius * radius)) {
            boolean danger = t instanceof Enemy || t.getHealth() < t.getMaxHealth() * 0.5F;
            QuakeSkills.felt(p, t);
            Vec3 heart = t.position().add(0, t.getBbHeight() * 0.62, 0);
            PacketDistributor.sendToPlayer(p, new AbilityEffectPayload(Kind.QUAKE_HEARTBEAT, heart, heart.add(0, 1, 0),
                    danger ? 1.2F : 0.8F));
        }
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (Body b : List.copyOf(BODIES.values())) {
            ServerPlayer p = b.player;
            if (p.isRemoved() || !isQuake(p)) { BODIES.remove(p.getUUID()); continue; }
            tickStillness(p, b, now);
            // Rest brings Strain down: slowly on the move, faster crouched and still.
            if (b.strain > 0 && now - b.lastUse > REST_DELAY && now % (b.stillTicks > 0 ? 4 : 10) == 0) b.strain--;
            if (b.strain >= BREAKING && now % 5 == 0 && PowerRules.canAct(p))
                AbilityEffects.send(p.serverLevel(), Kind.QUAKE_STRAIN, p.position(), p.position().add(p.getLookAngle()), b.strain / 100F);
            sync(p, b);
        }
        tickResonance(now);
        tickFissures();
    }

    /** Starts tracking a Quake's body (Strain and stillness) the first tick she has the power. */
    public static void track(ServerPlayer p) { if (isQuake(p)) body(p); }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) if (!BODIES.containsKey(p.getUUID())) track(p);
        tick(event.getServer());
    }

    public static void forget(ServerPlayer p) {
        BODIES.remove(p.getUUID());
        FISSURES.removeIf(f -> f.owner == p);
        RESONATING.values().removeIf(r -> r.owner == p);
        QuakeSkills.forget(p);
        StrainPayload.send(p, 0, false);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) forget(p);
    }

    @SubscribeEvent public static void death(LivingDeathEvent event) {
        RESONATING.remove(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer p) { Body b = BODIES.get(p.getUUID()); if (b != null) { b.strain = 0; b.fracturedUntil = 0; sync(p, b); } }
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { BODIES.clear(); RESONATING.clear(); FISSURES.clear(); }
}
