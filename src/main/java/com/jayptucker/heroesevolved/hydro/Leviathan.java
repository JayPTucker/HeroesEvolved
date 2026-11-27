package com.jayptucker.heroesevolved.hydro;

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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Leviathan (Hydrokinesis, Tides signature): the bender opens a pool at a spot up to 20 blocks away and a serpent of
 * water rises out of it ({@link #RISE} ticks). It strikes three times, each at the nearest foe within
 * {@link #REACH} blocks of the pool: it rears back over the pool for {@link #WINDUP} ticks while a ring of water marks
 * the spot it will hit (fixed when it rears, so stepping off it dodges), then lunges: 4 + tier/2 to everyone within
 * {@link #BITE} blocks of the spot, dragged toward the pool and slowed, and it puts out fires. Then it dives back down.
 * A second press sends it down after the strike in progress. {@code HydroArt.leviathan} draws the rise, each strike and
 * the dive from one serpent shape, so the pieces chain.
 */
public final class Leviathan {
    public static final String ID = "leviathan";
    public static final int RISE = 12, STRIKE = 18, WINDUP = 10, LUNGE = 3, STRIKES = 3, DIVE = 12;
    public static final double REACH = 10, BITE = 1.8, HEAD = 3.4;

    private Leviathan() {}

    /** When strike {@code i} begins, from the cast. */
    public static int strikeAt(int i) { return RISE + i * STRIKE; }

    public static boolean canUse(ServerPlayer p) { return true; }

    public static boolean activate(ServerPlayer p, int tier) {
        BlockPos aim = ExpansionSkills.aim(p);
        Vec3 pool = new Vec3(aim.getX() + .5, aim.getY() + 1, aim.getZ() + .5);
        if (!p.serverLevel().hasChunkAt(aim) || pool.distanceToSqr(p.position()) > 20 * 20) return false;
        Run run = new Run(p, tier, pool);
        if (!Signatures.start(ID, run)) return false;
        Vec3 face = p.position().subtract(pool).multiply(1, 0, 1);
        run.facing = face.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : face.normalize();
        SetPiecePayload.send(run.level, Piece.HYDRO_LEVIATHAN_RISE, pool, pool.add(run.facing.scale(4)), 1, null);
        ArmPosePayload.show(p, ArmPose.WATER_RAISE, RISE);
        run.level.playSound(null, aim, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 2, .5F);
        run.level.playSound(null, aim, SoundEvents.ELDER_GUARDIAN_AMBIENT, SoundSource.PLAYERS, 1, .7F);
        return true;
    }

    static final class Run extends Signatures.Run {
        final Vec3 pool;
        Vec3 facing = new Vec3(0, 0, 1), spot;
        int strike = -1, bitten;
        boolean diving;
        int diveAt = -1;

        Run(ServerPlayer player, int tier, Vec3 pool) { super(player, tier); this.pool = pool; }

        @Override protected boolean tick() {
            if (diveAt >= 0) return age < diveAt + DIVE;
            int next = strike + 1;
            if (age == strikeAt(next)) {
                if (diving || next >= STRIKES) { dive(); return true; }
                rear(next);
            }
            if (strike >= 0 && spot != null && age == strikeAt(strike) + WINDUP + LUNGE) bite();
            return true;
        }

        @Override protected boolean press() {
            if (diving || diveAt >= 0) return false;
            diving = true;
            return true;
        }

        private void rear(int i) {
            strike = i;
            List<LivingEntity> near = Signatures.foes(player, pool.add(0, 1, 0), REACH, false);
            LivingEntity target = near.isEmpty() ? null : near.getFirst();
            spot = target != null ? target.position() : pool.add(facing.scale(3));
            Vec3 flat = spot.subtract(pool).multiply(1, 0, 1);
            if (flat.lengthSqr() > 1e-4) facing = flat.normalize();
            SetPiecePayload.sendSeeded(level, Piece.HYDRO_LEVIATHAN_STRIKE, pool, spot, (float) BITE, null, 0, i);
            ArmPosePayload.show(player, ArmPose.WATER_WHIP, WINDUP + LUNGE);
            level.playSound(null, BlockPos.containing(pool), SoundEvents.DROWNED_AMBIENT_WATER, SoundSource.PLAYERS, 1.5F, .5F);
        }

        private void bite() {
            float damage = 4 + tier / 2F;
            for (LivingEntity foe : Signatures.foes(player, spot.add(0, .8, 0), BITE + .6, false)) {
                if (Math.abs(foe.getY() - spot.y) > 2.5) continue;
                Signatures.hit(foe, player.damageSources().indirectMagic(player, player), damage);
                if (foe.isOnFire()) foe.clearFire();
                Vec3 in = pool.subtract(foe.position()).multiply(1, 0, 1);
                if (in.lengthSqr() > 1) Signatures.fling(foe, in.normalize().scale(.6).add(0, .35, 0));
                foe.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), player);
                bitten++;
            }
            level.playSound(null, BlockPos.containing(spot), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 2, .7F);
            level.playSound(null, BlockPos.containing(spot), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.5F, .8F);
            CameraShakePayload.sendAround(level, spot, 8, .5F, 6);
        }

        private void dive() {
            diveAt = age;
            SetPiecePayload.send(level, Piece.HYDRO_LEVIATHAN_DIVE, pool, pool.add(facing.scale(4)), 1, null);
            level.playSound(null, BlockPos.containing(pool), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 2, .6F);
        }
    }

    /** For tests: how many bites this caster's Leviathan has landed (-1 when none is up). */
    public static int bitten(ServerPlayer p) {
        Run run = Signatures.get(ID, p);
        return run == null ? -1 : run.bitten;
    }
}
