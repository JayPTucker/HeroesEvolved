package com.jayptucker.heroesevolved.sonic;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.effects.PrivateGlow;
import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import com.jayptucker.heroesevolved.invisibility.InvisibilityService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sonic Resonance's skill tree, built around the kit rather than stat bonuses. Three branches:
 * <ul>
 *   <li><b>Resonant Breaker</b> (the voice as force): Braced Voice, Focused Shriek, Feedback Loop, Shatter Note →
 *   Wider Shatter → Resonant Breaker.</li>
 *   <li><b>Echo Cartographer</b> (listening and luring): Perfect Pitch, Dissonant Wave, Siren Song, Echo Map →
 *   Deep Echo → Cartographer.</li>
 *   <li><b>Harmonist</b> (holding the line): Sympathetic Strings, Resonant Guard, Sustain, Quiet Zone →
 *   Long Silence → Harmonist.</li>
 * </ul>
 * The passive nodes are read where the kit runs ({@link SonicService}); this class holds the three branch actives
 * and the three techniques. Node ids are kept from the older tree, so purchases and prerequisites carry over.
 */
public final class SonicSkills {
    static final String WIDER_SHATTER = "sonic_resonance_wider_shatter", RESONANT_BREAKER = "sonic_resonance_resonant_breaker",
            DEEP_ECHO = "sonic_resonance_deep_echo", CARTOGRAPHER = "sonic_resonance_cartographer",
            LONG_SILENCE = "sonic_resonance_long_silence", HARMONIST = "sonic_resonance_harmonist";
    public static final int GUARD_RADIUS = 7, QUIET_RADIUS = 6, DISSONANCE_RADIUS = 8;

    private record Field(ServerPlayer owner, ServerLevel level, Vec3 center, long start, long until) {}
    private static final Map<UUID, Field> GUARDS = new HashMap<>(), QUIETS = new HashMap<>();
    /** Mobs inside a Quiet Zone, with when it last held them: they can't pick a target. */
    private static final Map<UUID, Long> HUSHED = new HashMap<>();

    private SonicSkills() {}

    private static LivingEntity shriekTarget(ServerPlayer p) {
        return Targeting.livingEntityInSight(p, 40, t -> SonicService.canHit(p, t));
    }

    // ------------------------------------------------------------------
    // The three branch actives
    // ------------------------------------------------------------------

    public static boolean canUse(AbilityUseContext c, int branch) {
        return branch != 0 || shriekTarget(c.player()) != null;
    }

    public static boolean skill(AbilityUseContext c, int branch) {
        ServerPlayer p = c.player();
        return switch (branch) {
            case 0 -> focusedShriek(p, c.abilityLevel());
            case 1 -> dissonantWave(p);
            default -> resonantGuard(p);
        };
    }

    /**
     * Focused Shriek: a hand cupped at the mouth and the whole voice through one point, up to 40 blocks: 9 + level
     * damage and a shove, and the target is left Ringing.
     */
    static boolean focusedShriek(ServerPlayer p, int tier) {
        LivingEntity target = shriekTarget(p);
        if (target == null) return false;
        Vec3 aim = p.getLookAngle();
        SonicService.hit(p, target, 9 + tier);
        SonicService.ring(p, target);
        SonicService.push(target, target.getDeltaMovement().add(aim.scale(1.1)).add(0, .15, 0));
        SonicService.addResonance(p, 4);
        SonicGeometry.show(p.serverLevel(), Piece.SONIC_SHRIEK, SonicGeometry.mouth(p, 1), target.getBoundingBox().getCenter(), 1, p);
        ArmPosePayload.show(p, ArmPose.SONIC_CUP, 14);
        SonicService.sound(p.serverLevel(), p.getEyePosition(), com.jayptucker.heroesevolved.sounds.ModSounds.SONIC_CRY.get(), 0.9F, 1.5F);
        SonicService.sound(p.serverLevel(), target.position(), SoundEvents.BELL_BLOCK, 0.8F, 2.0F);
        return true;
    }

    /**
     * Dissonant Wave: a howl of clashing notes. Visible enemies within 8 blocks take 3 damage, Weakness II and a strong
     * slow. Anything drawn to your Note takes it twice and is thrown into the Note.
     */
    static boolean dissonantWave(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 note = SonicService.notePosition(p);
        int struck = 0;
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(DISSONANCE_RADIUS),
                t -> SonicService.canHit(p, t) && t.distanceToSqr(p) <= DISSONANCE_RADIUS * DISSONANCE_RADIUS && p.hasLineOfSight(t))) {
            if (++struck > 32) break;
            boolean drawn = note != null && SonicService.luredBy(t, p);
            SonicService.hit(p, t, drawn ? 6 : 3);
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2));
            if (drawn) {
                Vec3 in = note.subtract(t.position()).multiply(1, 0, 1);
                if (in.lengthSqr() > 1e-4) SonicService.push(t, in.normalize().scale(Math.min(1.2, in.length() * .3)).add(0, .3, 0));
            }
        }
        if (struck > 0) SonicService.addResonance(p, Math.min(12, struck * 3));
        SonicGeometry.show(level, Piece.SONIC_DISSONANCE, p.position(), p.position(), DISSONANCE_RADIUS, p);
        ArmPosePayload.show(p, ArmPose.SONIC_HOWL, 16);
        SonicService.voice(p, true);
        SonicService.sound(level, p.getEyePosition(), SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 1.2F, 0.53F);
        SonicService.sound(level, p.getEyePosition(), SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 1.2F, 0.56F);
        return true;
    }

    /**
     * Resonant Guard: arms crossed, a low hum. Resistance for 5 seconds (7.5 with Sustain) and a shell of standing waves
     * 7 blocks across that turns back incoming shots; each one builds 4 Resonance.
     */
    static boolean resonantGuard(ServerPlayer p) {
        int ticks = SonicService.has(p, SonicService.SUSTAIN) ? 150 : 100;
        long now = SonicService.now(p);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 0));
        Field guard = new Field(p, p.serverLevel(), p.position(), now, now + ticks);
        GUARDS.put(p.getUUID(), guard);
        turnShots(guard);
        SonicGeometry.show(p.serverLevel(), Piece.SONIC_GUARD, p.position(), p.position(), GUARD_RADIUS, null);
        ArmPosePayload.show(p, ArmPose.SONIC_HUM, 20);
        SonicService.sound(p.serverLevel(), p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 0.6F);
        return true;
    }

    public static boolean guarding(ServerPlayer p) { return GUARDS.containsKey(p.getUUID()); }

    // ------------------------------------------------------------------
    // The techniques (Shatter Note, Echo Map, Quiet Zone)
    // ------------------------------------------------------------------

    public static boolean technique(ServerPlayer p, String mechanic) {
        return switch (mechanic) {
            case "shatter_note" -> shatterNote(p);
            case "echo_map" -> echoMap(p);
            case "quiet_zone" -> quietZone(p);
            default -> false;
        };
    }

    /**
     * Shatter Note: a finger-snap at a point up to 24 blocks away. Glass, ice and leaves around it shatter (stone, dirt and
     * wood too with Resonant Breaker), foes there take 4 and are left Ringing. With Wider Shatter, your own Note there
     * breaks harmonic for free.
     */
    static boolean shatterNote(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        BlockHitResult aim = level.clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(24)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        if (aim.getType() != HitResult.Type.BLOCK) return false;
        boolean wider = SonicService.has(p, WIDER_SHATTER), breaker = SonicService.has(p, RESONANT_BREAKER);
        int half = wider ? 2 : 1;
        float hardness = breaker ? 3F : 0.6F;
        int broken = 0;
        for (BlockPos pos : BlockPos.betweenClosed(aim.getBlockPos().offset(-half, -half, -half), aim.getBlockPos().offset(half, half, half))) {
            var state = level.getBlockState(pos);
            float speed = state.getDestroySpeed(level, pos);
            if (state.isAir() || speed < 0 || speed > hardness || state.hasBlockEntity() || !WorldMutationPolicy.canEdit(p, level, pos)) continue;
            level.destroyBlock(pos, true, p);
            if (++broken >= 64) break;
        }
        Vec3 point = aim.getLocation();
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(half + 1.5), t -> SonicService.canHit(p, t))) {
            boolean wasRinging = SonicService.ringing(t);
            SonicService.hit(p, t, 4);
            if (breaker && wasRinging) t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 6));
            SonicService.ring(p, t);
        }
        if (wider) SonicService.breakNoteNear(p, point, half + 2.5);
        SonicGeometry.show(level, Piece.SONIC_SHATTER, SonicGeometry.hand(p, 1, .6), point, half, p);
        ArmPosePayload.show(p, ArmPose.SONIC_SNAP, 12);
        SonicService.sound(level, point, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2F, 1.5F);
        SonicService.sound(level, p.getEyePosition(), SoundEvents.NOTE_BLOCK_HAT.value(), 1.0F, 1.6F);
        return true;
    }

    /**
     * Echo Map: a hand to the ear and a sonar sweep only you see. Ores within 12 blocks (20 with Deep Echo) glimmer for
     * you; Deep Echo also leaves every enemy it passes Ringing, and Cartographer outlines every creature within 32
     * blocks and exposes the unseen.
     */
    static boolean echoMap(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        boolean deep = SonicService.has(p, DEEP_ECHO);
        int radius = deep ? 20 : 12, shown = 0;
        BlockPos origin = p.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))) {
            var state = level.getBlockState(pos);
            if (!state.is(Tags.Blocks.ORES) && !state.is(ModBlocks.HELIXITE_ORE.get()) && !state.is(ModBlocks.DEEPSLATE_HELIXITE_ORE.get())) continue;
            level.sendParticles(p, ParticleTypes.GLOW, true, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 3, .2, .2, .2, 0);
            if (++shown >= 96) break;
        }
        if (deep) for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(radius),
                t -> SonicService.canHit(p, t) && t.distanceToSqr(p) <= radius * radius)) SonicService.ring(p, t);
        if (SonicService.has(p, CARTOGRAPHER)) {
            InvisibilityService.revealAround(level, p.position(), 32, 200);
            for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(32), t -> t != p)) PrivateGlow.glow(p, t, 160);
        }
        p.displayClientMessage(Component.translatable("technique.heroesevolved.echo_map", shown), true);
        PacketDistributor.sendToPlayer(p, new SetPiecePayload(Piece.SONIC_ECHO.ordinal(), p.position(), p.position(), radius, -1,
                p.getRandom().nextInt(), 0));
        ArmPosePayload.show(p, ArmPose.SONIC_LISTEN, 24);
        SonicService.sound(level, p.position(), SoundEvents.SCULK_CLICKING, 1.0F, 0.8F);
        return true;
    }

    /**
     * Quiet Zone: a finger to the lips and a field of silence 6 blocks across for ten seconds (fifteen with Long Silence).
     * Mobs inside lose their targets and can't pick new ones; with Harmonist, friends inside slowly heal.
     */
    static boolean quietZone(ServerPlayer p) {
        long now = SonicService.now(p);
        int ticks = SonicService.has(p, LONG_SILENCE) ? 300 : 200;
        QUIETS.put(p.getUUID(), new Field(p, p.serverLevel(), p.position(), now, now + ticks));
        SonicGeometry.show(p.serverLevel(), Piece.SONIC_QUIET, p.position(), p.position(), QUIET_RADIUS, null);
        ArmPosePayload.show(p, ArmPose.SONIC_HUSH, 30);
        SonicService.sound(p.serverLevel(), p.position(), SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.3F, 2.0F);
        return true;
    }

    /** Whether a mob is held by a Quiet Zone and can't pick a target. */
    public static boolean hushed(Entity mob) { return HUSHED.containsKey(mob.getUUID()); }

    // ------------------------------------------------------------------

    static void tick(MinecraftServer server, long now) {
        for (var entry : List.copyOf(GUARDS.entrySet())) {
            Field f = entry.getValue();
            if (now >= f.until || f.owner.isRemoved() || f.owner.level() != f.level) { GUARDS.remove(entry.getKey()); continue; }
            turnShots(f);
            if ((now - f.start) % 10 == 0) SonicGeometry.show(f.level, Piece.SONIC_GUARD, f.center, f.center, GUARD_RADIUS, null);
        }
        HUSHED.values().removeIf(until -> until < now);
        for (var entry : List.copyOf(QUIETS.entrySet())) {
            Field f = entry.getValue();
            if (now >= f.until || f.owner.isRemoved() || f.owner.level() != f.level) { QUIETS.remove(entry.getKey()); continue; }
            if ((now - f.start) % 10 == 0) SonicGeometry.show(f.level, Piece.SONIC_QUIET, f.center, f.center, QUIET_RADIUS, null);
            if (now % 5 != 0) continue;
            AABB area = new AABB(f.center, f.center).inflate(QUIET_RADIUS);
            for (Mob mob : f.level.getEntitiesOfClass(Mob.class, area, m -> SonicService.canHit(f.owner, m)
                    && m.distanceToSqr(f.center) <= QUIET_RADIUS * QUIET_RADIUS)) {
                mob.setTarget(null);
                HUSHED.put(mob.getUUID(), now + 6);
            }
            if (SonicService.has(f.owner, HARMONIST) && now % 40 == 0)
                for (ServerPlayer ally : f.level.getEntitiesOfClass(ServerPlayer.class, area,
                        a -> (a == f.owner || f.owner.isAlliedTo(a)) && a.distanceToSqr(f.center) <= QUIET_RADIUS * QUIET_RADIUS)) ally.heal(1);
        }
    }

    /** Turns every shot heading into a Guard back out the way it came; each one is the singer's now and builds Resonance. */
    private static void turnShots(Field f) {
        int turned = 0;
        for (Projectile shot : f.level.getEntitiesOfClass(Projectile.class, new AABB(f.center, f.center).inflate(GUARD_RADIUS),
                s -> s.getOwner() != f.owner && s.position().distanceToSqr(f.center) <= GUARD_RADIUS * GUARD_RADIUS)) {
            Vec3 away = shot.position().subtract(f.center).normalize();
            if (shot.getDeltaMovement().dot(away) >= 0) continue;
            if (++turned > 32) break;
            SonicService.push(shot, away.scale(Math.max(.8, shot.getDeltaMovement().length())));
            shot.setOwner(f.owner);
            SonicService.addResonance(f.owner, 4);
            SonicService.sound(f.level, shot.position(), SoundEvents.AMETHYST_BLOCK_HIT, 1.0F, 1.4F);
        }
    }

    static void forget(ServerPlayer p) {
        GUARDS.remove(p.getUUID());
        QUIETS.remove(p.getUUID());
    }

    static void clear() {
        GUARDS.clear(); QUIETS.clear(); HUSHED.clear();
    }
}
