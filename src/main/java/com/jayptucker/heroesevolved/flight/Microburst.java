package com.jayptucker.heroesevolved.flight;

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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Microburst (Flight, Stormbreaker signature): a hand swept down from overhead, and the sky answers. A lid of cloud
 * gathers {@link #CLOUD} blocks over the spot she points at while a ring of wind marks the ground ({@link #SLAM} ticks:
 * the warning, fixed where she pointed); then the column slams down. Everyone inside the ring takes 6 + tier and is
 * pinned flat for two seconds; anything flying there is driven into the ground. A moment later the air spills out along
 * the ground as a curling outflow and throws whoever stands just beyond the ring away. {@code FlightArt.microburst} reads
 * these numbers.
 */
public final class Microburst {
    public static final String ID = "microburst";
    /** Ticks from the cast to the slam, then to the outflow, and how long the drawing lasts. */
    public static final int SLAM = 20, OUTFLOW = 25, LIFE = 50;
    public static final double RADIUS = 6, OUTER = 10, CLOUD = 14;

    private Microburst() {}

    public static boolean canUse(ServerPlayer p) { return true; }

    public static boolean activate(ServerPlayer p, int tier) {
        BlockPos aim = ExpansionSkills.aim(p);
        if (!p.serverLevel().hasChunkAt(aim)) return false;
        Vec3 centre = new Vec3(aim.getX() + .5, aim.getY() + 1, aim.getZ() + .5);
        if (centre.distanceToSqr(p.position()) > 28 * 28) return false;
        Run run = new Run(p, tier, centre);
        if (!Signatures.start(ID, run)) return false;
        ArmPosePayload.show(p, ArmPose.ARMS_RAISED, SLAM - 2);
        SetPiecePayload.sendSeeded(run.level, Piece.FLIGHT_MICROBURST, centre, centre.add(0, CLOUD, 0), (float) RADIUS, null, 0, p.getId());
        run.level.playSound(null, aim, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 1.2F, .5F);
        return true;
    }

    static final class Run extends Signatures.Run {
        final Vec3 centre;
        final Set<UUID> pinned = new HashSet<>();

        Run(ServerPlayer player, int tier, Vec3 centre) { super(player, tier); this.centre = centre; }

        @Override protected boolean tick() {
            if (age == SLAM - 6) ArmPosePayload.show(player, ArmPose.FLIGHT_CLAP, 10);
            if (age == SLAM) slam();
            if (age == OUTFLOW) outflow();
            return age < OUTFLOW;
        }

        private boolean under(LivingEntity t, double radius) {
            double dx = t.getX() - centre.x, dz = t.getZ() - centre.z;
            return dx * dx + dz * dz <= radius * radius && t.getY() > centre.y - 3 && t.getY() < centre.y + CLOUD;
        }

        /** The column lands: everyone under it is hurt and pinned; anything in the air is driven down. */
        private void slam() {
            float damage = 6 + tier;
            for (LivingEntity foe : Signatures.foes(player, centre.add(0, CLOUD / 2, 0), CLOUD, false)) {
                if (!under(foe, RADIUS)) continue;
                Signatures.hit(foe, player.damageSources().explosion(player, player), damage);
                Signatures.fling(foe, new Vec3(foe.getDeltaMovement().x * .2, foe.onGround() ? -.4 : -2.2, foe.getDeltaMovement().z * .2));
                foe.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3), player);
                foe.resetFallDistance();
                pinned.add(foe.getUUID());
            }
            BlockPos at = BlockPos.containing(centre);
            level.playSound(null, at, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 1.4F);
            level.playSound(null, at, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 2, .6F);
            CameraShakePayload.sendAround(level, centre, OUTER * 2, 1.1F, 12);
        }

        /** The air spills out along the ground: whoever stands just past the ring is thrown away from it. */
        private void outflow() {
            for (LivingEntity foe : Signatures.foes(player, centre.add(0, 1, 0), OUTER, false)) {
                if (pinned.contains(foe.getUUID()) || !under(foe, OUTER) || foe.getY() > centre.y + 4) continue;
                Vec3 out = foe.position().subtract(centre).multiply(1, 0, 1);
                if (out.lengthSqr() < 1e-4) continue;
                Signatures.fling(foe, out.normalize().scale(1.3).add(0, .35, 0));
            }
            level.playSound(null, BlockPos.containing(centre), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.4F, 1.1F);
        }
    }

    /** For tests: how many this caster's Microburst pinned (-1 when none is running). */
    public static int pinned(ServerPlayer p) {
        Run run = Signatures.get(ID, p);
        return run == null ? -1 : run.pinned.size();
    }
}
