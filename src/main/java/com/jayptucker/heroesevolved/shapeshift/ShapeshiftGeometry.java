package com.jayptucker.heroesevolved.shapeshift;

import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Numbers the server and {@code ShapeArt} share, so the plates and the body agree. A morph is one timeline from 0 to 1:
 * the plates close over the old body up to {@link #COVERED}, the body underneath is swapped at {@link #SWAP} while
 * the shell eases to its new size, and from {@link #SHED} the plates flip open and fall away from the feet up.
 */
public final class ShapeshiftGeometry {
    public static final int MORPH_TICKS = 18, BIG_MORPH_TICKS = 26;
    public static final float COVERED = .42F, SWAP = .5F, SHED = .58F;

    private ShapeshiftGeometry() {}

    /** How long a change between these two bodies takes: big bodies take longer to cover. */
    public static int morphTicks(EntityDimensions from, EntityDimensions to) {
        float height = Math.max(from.height(), to.height()), width = Math.max(from.width(), to.width());
        return height > 2.2F || width > 1.2F ? BIG_MORPH_TICKS : MORPH_TICKS;
    }

    /** Whether a form keeps hands: people, villagers, illagers, piglins, zombies, skeletons, endermen, witches and vexes. */
    public static boolean handed(Entity e) {
        return e instanceof Player || e instanceof AbstractVillager || e instanceof AbstractIllager || e instanceof AbstractPiglin
                || e instanceof Zombie || e instanceof AbstractSkeleton || e instanceof EnderMan || e instanceof Witch || e instanceof Vex;
    }

    /** How far Lunge reaches, before Natural Weapons. */
    public static final double LUNGE_REACH = 4;
    /** How long a zombie's grab holds, and the warden's and the guardian's windups. */
    public static final int GRAB_TICKS = 40, SONIC_WINDUP = 10, GAZE_WINDUP = 20;
    public static final double SONIC_RANGE = 15, GAZE_RANGE = 14;

    /**
     * The main arm's shoulder joint (1.375 up, 1.17 crouching, 0.3125 out from the body's yaw), for anything that leaves
     * the hand: a lengthening arm has to start where the arm is.
     */
    public static Vec3 shoulder(Player player, float partial) {
        float yaw = net.minecraft.util.Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot) * net.minecraft.util.Mth.DEG_TO_RAD;
        double side = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1;
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        return player.getPosition(partial).add(0, player.isCrouching() ? 1.17 : 1.375, 0).add(right.scale(.3125 * side));
    }

    /** Sends a piece to one viewer only. */
    public static void showTo(ServerPlayer viewer, Piece piece, Vec3 from, Vec3 to, float scale, Entity bound) {
        PacketDistributor.sendToPlayer(viewer, new SetPiecePayload(piece.ordinal(), from, to, scale,
                bound == null ? -1 : bound.getId(), viewer.getRandom().nextInt(), 0));
    }
}
