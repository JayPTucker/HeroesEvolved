package com.jayptucker.heroesevolved.radiation;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.expansion.Signatures;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Fission Star (Induced Radioactivity, Meltdown signature): Ted cups his hands and a white-hot core grows between them
 * for {@link #CHARGE} ticks, then he throws it. It drifts out slowly ({@link #SPEED} blocks a tick, a ring on the ground
 * under it showing what it will cover) and goes off when it touches a wall, the ground or a foe, after
 * {@link #FLIGHT} ticks, or on a second press: everyone within {@link #BLAST} blocks with a clear line to it takes
 * 8 + tier, is dosed with radiation sickness, and the ground there stays contaminated for five seconds.
 */
public final class FissionStar {
    public static final String ID = "fission_star";
    public static final int CHARGE = 30, FLIGHT = 30, ZONE_TICKS = 100;
    public static final double SPEED = .9, BLAST = 5, ZONE = 4;

    private FissionStar() {}

    public static boolean canUse(ServerPlayer p) { return true; }

    public static boolean activate(ServerPlayer p, int tier) {
        Run run = new Run(p, tier);
        if (!Signatures.start(ID, run)) return false;
        ArmPosePayload.show(p, ArmPose.RAD_CUP, CHARGE);
        SetPiecePayload.send(run.level, Piece.RAD_STAR_CHARGE, p.position(), p.getEyePosition(), CHARGE, p);
        run.level.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, .5F);
        return true;
    }

    static final class Run extends Signatures.Run {
        Vec3 core, velocity;
        int flown;
        boolean gone;

        Run(ServerPlayer player, int tier) { super(player, tier); }

        @Override protected boolean tick() {
            if (age < CHARGE) {
                if (age % 10 == 0) level.playSound(null, player.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.5F, .6F + age / 40F);
                return true;
            }
            if (age == CHARGE) throwCore();
            return fly();
        }

        private void throwCore() {
            Vec3 look = player.getLookAngle();
            core = player.getEyePosition().add(look.scale(.9)).add(0, -.25, 0);
            velocity = look.scale(SPEED);
            ArmPosePayload.show(player, ArmPose.RAD_POINT, 10);
            level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1, .5F);
        }

        /** One tick of flight; false once it has gone off. */
        private boolean fly() {
            Vec3 next = core.add(velocity);
            BlockHitResult wall = level.clip(new ClipContext(core, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (wall.getType() == HitResult.Type.BLOCK) { detonate(wall.getLocation().subtract(velocity.normalize().scale(.3))); return false; }
            AABB sweep = new AABB(core, next).inflate(.6);
            for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, sweep, t -> com.jayptucker.heroesevolved.expansion.ExpansionSkills.hostile(player, t))) {
                if (t.getBoundingBox().inflate(.4).clip(core, next).isPresent() || t.getBoundingBox().inflate(.4).contains(next)) {
                    detonate(t.getBoundingBox().getCenter());
                    return false;
                }
            }
            core = next;
            flown++;
            if (flown % 2 == 1) SetPiecePayload.send(level, Piece.RAD_STAR, core, core.add(velocity.scale(2)), (float) BLAST, null);
            if (flown >= FLIGHT) { detonate(core); return false; }
            return true;
        }

        @Override protected boolean press() {
            if (core == null || gone) return false;
            detonate(core);
            Signatures.end(ID, player);
            return true;
        }

        void detonate(Vec3 at) {
            if (gone) return;
            gone = true;
            float damage = 8 + tier;
            for (LivingEntity foe : Signatures.foes(player, at, BLAST, true)) {
                Signatures.hit(foe, player.damageSources().explosion(player, player), damage);
                RadiationService.irradiate(foe, player, 2, 120);
                Vec3 out = foe.position().subtract(at);
                Signatures.fling(foe, (out.lengthSqr() < 1e-4 ? Vec3.ZERO : out.normalize().scale(.7)).add(0, .4, 0));
            }
            Vec3 ground = groundUnder(at);
            if (ground != null && ground.distanceToSqr(at) < 6 * 6) RadiationService.contaminate(player, ground, ZONE, ZONE_TICKS);
            SetPiecePayload.send(level, Piece.RAD_STAR_BLAST, at, ground == null ? at : ground, (float) BLAST, null);
            level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.4F, .6F);
            level.playSound(null, BlockPos.containing(at), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1, .5F);
            CameraShakePayload.sendAround(level, at, BLAST * 4, 1.4F, 16);
        }

        private Vec3 groundUnder(Vec3 at) {
            BlockHitResult down = level.clip(new ClipContext(at, at.add(0, -8, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            return down.getType() == HitResult.Type.BLOCK ? down.getLocation() : null;
        }
    }

    /** For tests: where this caster's core is now (null while charging or once gone). */
    public static Vec3 core(ServerPlayer p) {
        Run run = Signatures.get(ID, p);
        return run == null || run.gone ? null : run.core;
    }
}
