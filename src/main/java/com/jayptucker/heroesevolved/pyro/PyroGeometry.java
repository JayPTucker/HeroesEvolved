package com.jayptucker.heroesevolved.pyro;

import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Shared fire shapes and anchors for gameplay and rendering. */
public final class PyroGeometry {
    public static final double BREATH_SLOPE = .51;
    public static final int STORM_WARNING = 12, COLLAPSE_WARNING = 8, SUN_FLIGHT = 9, MINE_WARNING = 8;
    public static final float BLUE_SCALE = 32;

    private PyroGeometry() {}

    public static Vec3 castingHand(Player player, float partial) {
        double side = player.getMainArm() == HumanoidArm.LEFT ? 1 : -1;
        double yaw = Math.toRadians(Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot));
        Vec3 look = player.getViewVector(partial);
        return player.getPosition(partial).add(Math.cos(yaw) * side * .3125,
                player.isCrouching() ? 1.17 : 1.375, Math.sin(yaw) * side * .3125).add(look.scale(.64));
    }

    public static Vec3 breathOrigin(Player player, float partial) {
        return player.getEyePosition(partial).add(0, -.17, 0).add(player.getViewVector(partial).scale(.34));
    }

    public static void show(ServerLevel level, Piece piece, Vec3 start, Vec3 end, double size, boolean blue, Entity bound) {
        SetPiecePayload.send(level, piece, start, end, (float) size + (blue ? BLUE_SCALE : 0), bound);
    }

    static void showPrivate(ServerPlayer player, Piece piece, Vec3 at, double size, boolean blue) {
        PacketDistributor.sendToPlayer(player, new SetPiecePayload(piece.ordinal(), at, at,
                (float) size + (blue ? BLUE_SCALE : 0), -1, player.getRandom().nextInt(), 0));
    }
}
