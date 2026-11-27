package com.jayptucker.heroesevolved.hydro;

import com.jayptucker.heroesevolved.ability.ProjectileRanges;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Hydrokinesis's shared numbers and shapes. The server moves water by these and {@code HydroArt} draws it by the same
 * ones, so a wave hits where it is seen and a whip cracks where it is drawn.
 */
public final class HydroGeometry {
    private HydroGeometry() {}

    // ------------------------------------------------------------------
    // The reservoir
    // ------------------------------------------------------------------

    /** How much water a hydrokinetic carries, and with Water Skin. */
    public static final int WATER = 100, SKIN_WATER = 150;
    /** Scale added to a piece whose water was pulled thin from the air (too little carried): drawn thinner and paler. */
    public static final float THIN = 32;

    // ------------------------------------------------------------------
    // The kit
    // ------------------------------------------------------------------

    /** Water Whip: reach, the tube a target is found in, and the ticks the whip takes to unroll to its tip. */
    public static final double WHIP_REACH = 10, WHIP_WIDTH = .6;
    public static final float WHIP_FLIGHT = 4;
    /** Crashing Wave: how far it rolls, half its width and its crest's height (both scale with a Tsunami). */
    public static final double WAVE_REACH = ProjectileRanges.WATER_WAVE, WAVE_HALF_WIDTH = 2.5, WAVE_HEIGHT = 2.6;
    /** A wave rises for WAVE_RISE ticks, rolls for the rest of its piece's length less WAVE_BREAK, then breaks. */
    public static final int WAVE_RISE = 4, WAVE_BREAK = 8;
    /** Octopus Form: how long it can last, its tentacles' reach, where shots are swallowed, and how many arms. */
    public static final int OCTOPUS_TICKS = 200, ARMS = 8, LASH_EVERY = 8;
    public static final double OCTOPUS_REACH = 5, OCTOPUS_CATCH = 3.2, OCTOPUS_BURST = 6;
    /** Wave Rider: how long a ride lasts, and its speeds on land and on water. */
    public static final int RIDE_TICKS = 120;
    public static final double RIDE_SPEED = .8, RIDE_WATER_SPEED = 1.25;
    /** Maelstrom: the whirlpool's wind-up, the waterspout's life, its ring and how far its pull reaches. */
    public static final int SPOUT_WINDUP = 30, SPOUT_TICKS = 160;
    public static final float SPOUT_RING = 6.5F;
    public static final double SPOUT_PULL = 14, SPOUT_REACH = 28;

    // ------------------------------------------------------------------
    // Waterbending (the fighting style)
    // ------------------------------------------------------------------

    /** A lash's reach and the ticks it takes to unroll. */
    public static final double LASH_REACH = 7;
    public static final float LASH_FLIGHT = 3;
    /** Ice Daggers: how far they fly, how many, the angle between them, and their flight time. */
    public static final double DAGGER_REACH = ProjectileRanges.WATER_DAGGERS;
    public static final int DAGGERS = 5;
    public static final double DAGGER_SPREAD = 6;
    public static final float DAGGER_FLIGHT = 4;
    /** Water Wheel's radius; a full turn takes WHEEL_SPIN ticks. */
    public static final double WHEEL_RADIUS = 4;
    public static final float WHEEL_SPIN = 8;
    /** A Flow Step pushes this long; a Geyser Vault's column reaches this far and flips the body over FLIP ticks. */
    public static final int STEP_TICKS = 4, VAULT_FLIP = 12;
    public static final double VAULT_RADIUS = 2.2;
    /** A Geyser (the skill) marks its spot this long before it bursts, and reaches this far round it. */
    public static final int GEYSER_DELAY = 14;
    public static final double GEYSER_RADIUS = 2.4;

    // ------------------------------------------------------------------
    // The body
    // ------------------------------------------------------------------

    /** A hand reaching along the look: {@code main} for the main arm, otherwise the other one. */
    public static Vec3 hand(Player player, float partial, boolean main) {
        double side = (player.getMainArm() == HumanoidArm.LEFT) == main ? 1 : -1;
        double yaw = Math.toRadians(Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot));
        Vec3 look = player.getViewVector(partial);
        return player.getPosition(partial).add(Math.cos(yaw) * side * .3125,
                player.isCrouching() ? 1.17 : 1.375, Math.sin(yaw) * side * .3125).add(look.scale(.64));
    }

    /** Waist height, where carried water circles and the Water Wheel is swung. */
    public static Vec3 waist(Player player, float partial) {
        return player.getPosition(partial).add(0, player.isCrouching() ? .6 : .9, 0);
    }

    public static Vec3 chest(Vec3 feet) { return feet.add(0, 1.2, 0); }

    /** The horizontal aim, falling back to the body's facing when looking straight up or down. */
    public static Vec3 level(Vec3 look, float yaw) {
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() > 1e-4) return flat.normalize();
        double radians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians), 0, Math.cos(radians));
    }

    // ------------------------------------------------------------------
    // Shapes shared by gameplay and drawing
    // ------------------------------------------------------------------

    /** How many ticks a wave of this piece rolls for. */
    public static int waveTravel(Piece piece) { return piece.ticks - WAVE_RISE - WAVE_BREAK; }

    /** How far a wave's front has rolled {@code age} ticks in, rolling {@code reach} in {@code travel} ticks. */
    public static double waveFront(float age, double reach, int travel) {
        float k = Mth.clamp((age - WAVE_RISE) / travel, 0, 1);
        // It surges off the hands and slows a little as it reaches its end.
        return reach * (.25 * k * (2 - k) + .75 * k);
    }

    /** How tall the wave stands at this age: rising, holding, then collapsing as it breaks. */
    public static float waveRise(float age, int travel) {
        float rise = smooth(age / WAVE_RISE), fall = smooth((age - WAVE_RISE - travel) / WAVE_BREAK);
        return rise * (1 - fall);
    }

    /**
     * The surface under {@code at}: the top of the first block a body could stand on (or water) found from {@code up}
     * blocks above to {@code down} below, or null if there is none. Both sides call it with the same blocks.
     */
    public static Vec3 ground(BlockGetter level, Vec3 at, int up, int down) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(at.x, at.y + up, at.z).mutable();
        for (int i = 0; i <= up + down; i++, pos.move(net.minecraft.core.Direction.DOWN)) {
            if (level.isOutsideBuildHeight(pos)) return null;
            BlockPos below = pos.below();
            boolean floor = !level.getBlockState(below).getCollisionShape(level, below).isEmpty() || !level.getFluidState(below).isEmpty();
            boolean open = level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
            if (floor && open) return new Vec3(at.x, pos.getY(), at.z);
        }
        return null;
    }

    /** The surface a wave stands on at {@code distance} along its path, followed from its start a block at a time. */
    public static double waveFloor(BlockGetter level, Vec3 start, Vec3 dir, double distance) {
        double y = start.y;
        for (double d = 1; d <= distance + .01; d += 1) {
            Vec3 found = ground(level, new Vec3(start.x + dir.x * d, y, start.z + dir.z * d), 2, 4);
            if (found != null) y = found.y;
        }
        return y;
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    // ------------------------------------------------------------------
    // Sending pieces
    // ------------------------------------------------------------------

    public static void show(ServerLevel level, Piece piece, Vec3 from, Vec3 to, double size, Entity bound) {
        SetPiecePayload.send(level, piece, from, to, (float) size, bound);
    }

    public static void show(ServerLevel level, Piece piece, Vec3 from, Vec3 to, double size, boolean thin, Entity bound) {
        SetPiecePayload.send(level, piece, from, to, (float) size + (thin ? THIN : 0), bound);
    }

    /** A piece for one viewer only. */
    public static void showTo(ServerPlayer viewer, Piece piece, Vec3 from, Vec3 to, double size, Entity bound) {
        PacketDistributor.sendToPlayer(viewer, new SetPiecePayload(piece.ordinal(), from, to, (float) size,
                bound == null ? -1 : bound.getId(), viewer.getRandom().nextInt(), 0));
    }
}
