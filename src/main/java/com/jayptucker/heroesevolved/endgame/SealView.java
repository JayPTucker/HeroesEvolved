package com.jayptucker.heroesevolved.endgame;

import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * What a client needs to pose the Living Seal and draw its locks: the move it is in and how far (opening pose, signal,
 * strike, end pose, window), its state, and the four knots. Common-side, like {@link EndView}.
 *
 * @param action  the move being made ({@link SealGeometry.Move} ordinal), or -1
 * @param age     ticks since that move's windup began
 * @param windup  that cast's windup (a feint's is longer)
 * @param flags   {@link #FEINT}, {@link #SHATTERED}, {@link #SLUMPED}, {@link #STAGGERED}, {@link #AIRBORNE}, {@link #STAR}, {@link #FALLING}
 * @param crash   ticks of crash left (it lies stunned)
 * @param stance  cracks in its stance (0 to 2)
 * @param feeding the knot it drinks from, or -1
 * @param drag    how close it is to being dragged out of the sky (0 to 1)
 * @param knots   each altar's knot: 0 none, 1 warding, 2 dormant, 3 feeding
 * @param health  each knot's health share
 * @param at      each knot's position
 */
public record SealView(int action, int age, int windup, int flags, int crash, int stance, int feeding, float drag,
                       List<Integer> knots, List<Float> health, List<Vec3> at) {
    public static final int FEINT = 1, SHATTERED = 2, SLUMPED = 4, STAGGERED = 8, AIRBORNE = 16, STAR = 32, FALLING = 64;
    public static final int NONE = 0, WARDING = 1, DORMANT = 2, FEEDING = 3;
    public static final SealView EMPTY = new SealView(-1, 0, 0, 0, 0, 0, -1, 0, List.of(), List.of(), List.of());
    public SealGeometry.Move move() { return SealGeometry.Move.byId(action); }
    public boolean has(int flag) { return (flags & flag) != 0; }
    public int warding() { int n = 0; for (int k : knots) if (k == WARDING) n++; return n; }
}
