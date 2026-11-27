package com.jayptucker.heroesevolved.sonic;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
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
import net.minecraft.world.phys.Vec3;

/**
 * Standing Wave (Sonic Resonance, Resonant Breaker signature): Emma sings one note at a spot up to 24 blocks away and
 * holds it until the air there rings by itself, a column of sound with still nodes and swelling bellies. Every second
 * ({@link #PERIOD} ticks) it pulses: foes within {@link #RADIUS} blocks take 1 + tier/4 and are drawn into it, the
 * pulses swelling each time. On the fifth beat, or a second press, the note breaks: 6 + tier to everyone in it, thrown
 * out. {@code SonicArt.standing} and {@code SonicArt.breakWave} read these numbers; the column is re-sent each pulse.
 */
public final class StandingWave {
    public static final String ID = "standing_wave";
    public static final int PERIOD = 20, BEATS = 5;
    public static final double RADIUS = 6, HEIGHT = 6;

    private StandingWave() {}

    public static boolean canUse(ServerPlayer p) { return true; }

    public static boolean activate(ServerPlayer p, int tier) {
        BlockPos aim = ExpansionSkills.aim(p);
        Vec3 base = new Vec3(aim.getX() + .5, aim.getY() + 1, aim.getZ() + .5);
        if (!p.serverLevel().hasChunkAt(aim) || base.distanceToSqr(p.position()) > 24 * 24) return false;
        Run run = new Run(p, tier, base);
        if (!Signatures.start(ID, run)) return false;
        run.show(0);
        ArmPosePayload.show(p, ArmPose.SONIC_SING, PERIOD);
        run.level.playSound(null, aim, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2, .7F);
        return true;
    }

    static final class Run extends Signatures.Run {
        final Vec3 base;
        int beat;
        boolean broken;

        Run(ServerPlayer player, int tier, Vec3 base) { super(player, tier); this.base = base; }

        void show(int beat) {
            SetPiecePayload.sendSeeded(level, Piece.SONIC_STANDING, base, base.add(0, HEIGHT, 0), (float) RADIUS, null, 0, beat);
        }

        private boolean inside(LivingEntity t) {
            double dx = t.getX() - base.x, dz = t.getZ() - base.z;
            return dx * dx + dz * dz <= RADIUS * RADIUS && t.getY() > base.y - 2 && t.getY() < base.y + HEIGHT;
        }

        @Override protected boolean tick() {
            if (age % PERIOD != 0) return true;
            beat++;
            if (beat >= BEATS) { shatter(); return false; }
            pulse();
            show(beat);
            if (beat % 2 == 0) ArmPosePayload.show(player, ArmPose.SONIC_SING, PERIOD * 2);
            return true;
        }

        @Override protected boolean press() {
            if (broken) return false;
            shatter();
            Signatures.end(ID, player);
            return true;
        }

        /** One beat: foes in the column are hurt and drawn in toward it. */
        private void pulse() {
            float damage = 1 + tier / 4F;
            for (LivingEntity foe : Signatures.foes(player, base.add(0, HEIGHT / 2, 0), RADIUS + HEIGHT, false)) {
                if (!inside(foe)) continue;
                Signatures.hit(foe, player.damageSources().indirectMagic(player, player), damage);
                Vec3 in = base.subtract(foe.position()).multiply(1, 0, 1);
                if (in.lengthSqr() > .5) Signatures.fling(foe, foe.getDeltaMovement().scale(.3).add(in.normalize().scale(.3 + .06 * beat)));
            }
            level.playSound(null, BlockPos.containing(base), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 2, .5F + beat * .15F);
            level.playSound(null, BlockPos.containing(base), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2, .6F + beat * .2F);
        }

        /** The note breaks: everyone in the column is hurt and thrown out. */
        void shatter() {
            if (broken) return;
            broken = true;
            float damage = 6 + tier;
            for (LivingEntity foe : Signatures.foes(player, base.add(0, HEIGHT / 2, 0), RADIUS + HEIGHT, false)) {
                if (!inside(foe)) continue;
                Signatures.hit(foe, player.damageSources().indirectMagic(player, player), damage);
                Vec3 out = foe.position().subtract(base).multiply(1, 0, 1);
                Signatures.fling(foe, (out.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : out.normalize()).scale(1.1).add(0, .45, 0));
            }
            SetPiecePayload.sendSeeded(level, Piece.SONIC_STANDING_BREAK, base, base.add(0, HEIGHT, 0), (float) RADIUS, null, 0, beat);
            level.playSound(null, BlockPos.containing(base), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.5F, .6F);
            level.playSound(null, BlockPos.containing(base), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2F, 1.3F);
            CameraShakePayload.sendAround(level, base, RADIUS * 3, 1, 10);
            ArmPosePayload.show(player, ArmPose.SONIC_RELEASE, 12);
        }
    }

    /** For tests: how many beats this caster's wave has sounded (-1 when none is running). */
    public static int beat(ServerPlayer p) {
        Run run = Signatures.get(ID, p);
        return run == null ? -1 : run.beat;
    }
}
