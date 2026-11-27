package com.jayptucker.heroesevolved.quake;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.expansion.Signatures;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * Epicenter (Quake, Faultwright signature): Daisy drives both fists into the ground. Eight faults race out from the spot
 * to a ring ten blocks wide and glow while the plates load ({@link #HEAVE} ticks: the warning, fixed where she struck);
 * then every plate heaves at once, throwing everyone standing in the ring for 6 + tier, and a moment later the ground
 * drops back and drags them toward the centre, staggered. The art ({@code QuakeArt.epicenter}) reads these numbers.
 */
public final class Epicenter {
    public static final String ID = "epicenter";
    /** Ticks from the strike to the heave, then to the drop, and how long the drawing lasts. */
    public static final int HEAVE = 24, DROP = 36, LIFE = 54;
    public static final double RADIUS = 10;
    /** How many faults run out from the centre. */
    public static final int SEAMS = 8;

    private Epicenter() {}

    /** The ground she would strike: the first solid floor within six blocks below her feet. */
    static Vec3 ground(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos feet = p.blockPosition();
        for (int dy = 0; dy <= 6; dy++) {
            BlockPos floor = feet.below(dy + 1);
            if (!level.hasChunkAt(floor)) return null;
            if (level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return new Vec3(p.getX(), floor.getY() + 1, p.getZ());
        }
        return null;
    }

    public static boolean canUse(ServerPlayer p) {
        if (ground(p) != null) return true;
        p.displayClientMessage(Component.literal("Epicenter needs ground under you.").withStyle(ChatFormatting.GRAY), true);
        return false;
    }

    public static boolean activate(ServerPlayer p, int tier) {
        Vec3 centre = ground(p);
        if (centre == null) return false;
        Run run = new Run(p, tier, centre);
        if (!Signatures.start(ID, run)) return false;
        ArmPosePayload.show(p, ArmPose.QUAKE_PUNCH_DOWN, HEAVE);
        SetPiecePayload.sendSeeded(run.level, Piece.QUAKE_EPICENTER, centre, centre.add(0, 1, 0), (float) RADIUS, null, 0, p.getId());
        run.level.playSound(null, BlockPos.containing(centre), SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 1.4F, .7F);
        CameraShakePayload.sendAround(run.level, centre, RADIUS * 2, .5F, HEAVE);
        return true;
    }

    static final class Run extends Signatures.Run {
        final Vec3 centre;
        final Set<UUID> thrown = new HashSet<>();

        Run(ServerPlayer player, int tier, Vec3 centre) { super(player, tier); this.centre = centre; }

        @Override protected boolean tick() {
            if (age == HEAVE) heave();
            if (age == DROP) drop();
            return age < DROP;
        }

        /** Everyone standing in the ring is thrown up and out by the plate under them. */
        private void heave() {
            float damage = 6 + tier;
            for (LivingEntity foe : Signatures.foes(player, centre.add(0, 1, 0), RADIUS, false)) {
                if (Math.abs(foe.getY() - centre.y) > 2.5 || !standing(foe)) continue;
                Signatures.hit(foe, player.damageSources().explosion(player, player), damage);
                Vec3 out = foe.position().subtract(centre).multiply(1, 0, 1);
                out = out.lengthSqr() < 1e-4 ? Vec3.ZERO : out.normalize().scale(.35);
                Signatures.fling(foe, out.add(0, .85, 0));
                thrown.add(foe.getUUID());
            }
            level.playSound(null, BlockPos.containing(centre), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.6F, .55F);
            level.playSound(null, BlockPos.containing(centre), SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 2, .5F);
            CameraShakePayload.sendAround(level, centre, RADIUS * 2.5, 1.4F, 14);
            ArmPosePayload.show(player, ArmPose.QUAKE_UPHEAVAL, 12);
        }

        /** The plates drop back: whoever is still in the ring is dragged toward the centre and staggered. */
        private void drop() {
            for (LivingEntity foe : Signatures.foes(player, centre.add(0, 1, 0), RADIUS + 1, false)) {
                if (foe.getY() - centre.y > 6) continue;
                Vec3 in = centre.subtract(foe.position()).multiply(1, 0, 1);
                if (in.lengthSqr() > 1) Signatures.fling(foe, foe.getDeltaMovement().add(in.normalize().scale(.55)).add(0, -.3, 0));
                foe.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), player);
            }
            level.playSound(null, BlockPos.containing(centre), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1, .5F);
            CameraShakePayload.sendAround(level, centre, RADIUS * 2, .8F, 8);
        }

        private boolean standing(LivingEntity foe) {
            if (foe.onGround()) return true;
            BlockPos below = foe.blockPosition().below();
            return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
        }
    }

    /** For tests: whoever this caster's Epicenter has thrown. */
    public static int thrown(ServerPlayer p) {
        Run run = Signatures.get(ID, p);
        return run == null ? -1 : run.thrown.size();
    }
}
