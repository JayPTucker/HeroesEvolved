package com.jayptucker.heroesevolved.mimicry;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * What everyone sees of an empath at work (drawn by the client's {@code MimicArt}). A power's colour index rides in the
 * piece's scale: its whole part is the index, an attunement's progress is the fraction, and a touch adds 32.
 */
final class MimicVisuals {
    static final int TOUCH = 32;

    private MimicVisuals() {}

    static Vec3 chest(LivingEntity entity) { return entity.position().add(0, entity.getBbHeight() * .62, 0); }

    /** Gold threads drawing tight between the mimic and someone with a power, as attunement builds. */
    static void attuning(ServerPlayer mimic, LivingEntity source, ResourceLocation power, float progress) {
        float scale = MimicColors.index(power.getPath()) + Math.clamp(progress, 0, .95F);
        SetPiecePayload.send(mimic.serverLevel(), Piece.MIMIC_ATTUNE, chest(source), chest(mimic), scale, mimic);
        mimic.serverLevel().playSound(null, mimic.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS,
                .25F + .35F * progress, 1.3F + .4F * progress);
    }

    /** The moment a set is taken: a pulse rushes down the thread and the power's colour blooms round the mimic. */
    static void imprint(ServerPlayer mimic, LivingEntity source, ResourceLocation power, boolean touched) {
        float scale = MimicColors.index(power.getPath()) + (touched ? TOUCH : 0);
        SetPiecePayload.send(mimic.serverLevel(), Piece.MIMIC_IMPRINT, chest(source), chest(mimic), scale, mimic);
        if (touched) ArmPosePayload.show(mimic, ArmPose.MIMIC_TOUCH, 12);
        mimic.serverLevel().playSound(null, mimic.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1, .7F);
        mimic.serverLevel().playSound(null, mimic.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, .7F, 1.6F);
    }

    /** Switching sets: the old colour drawn in to the chest, the new one opening out from it. */
    static void shift(ServerPlayer mimic, ResourceLocation power) {
        SetPiecePayload.send(mimic.serverLevel(), Piece.MIMIC_SHIFT, chest(mimic), chest(mimic),
                MimicColors.index(power.getPath()), mimic);
        ArmPosePayload.show(mimic, ArmPose.MIMIC_SHIFT, 12);
        mimic.serverLevel().playSound(null, mimic.blockPosition(), SoundEvents.AMETHYST_CLUSTER_STEP, SoundSource.PLAYERS, 1, 1.4F);
        mimic.serverLevel().playSound(null, mimic.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, .5F, 1.8F);
    }
}
