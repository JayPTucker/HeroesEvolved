package com.jayptucker.heroesevolved.cryo;

import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

/** The visual link between the caster's palms and the ice a move creates. */
public final class CryoCastEffects {
    private CryoCastEffects() {}

    public static void spray(ServerPlayer caster, Vec3 target, float size) {
        Vec3 eye = caster.getEyePosition();
        Vec3 contact = caster.serverLevel().clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster)).getLocation();
        SetPiecePayload.send(caster.serverLevel(), Piece.CRYO_CAST, eye, contact, size, caster);
    }

    public static void burst(ServerPlayer caster, Vec3 target, float size) {
        spray(caster, target, size);
        caster.serverLevel().playSound(null, caster.blockPosition(), SoundEvents.POWDER_SNOW_BREAK,
                SoundSource.PLAYERS, .8F, 1.15F);
    }

    /** Self-centered ice starts in front of the hands and curls back around the caster. */
    public static Vec3 localFrost(ServerPlayer caster, double distance, double height) {
        Vec3 forward = Vec3.directionFromRotation(0, caster.getYRot());
        return caster.position().add(forward.scale(distance)).add(0, height, 0);
    }
}
