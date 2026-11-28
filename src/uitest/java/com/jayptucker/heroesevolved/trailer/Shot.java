package com.jayptucker.heroesevolved.trailer;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * One shot of the trailer (see {@code docs/trailer/storyboard.md}): what is in it, how the camera moves, how fast it
 * plays, and what happens on each of its ticks. {@link TrailerPreview} films them in turn on the same stage.
 *
 * <p>Ticks count from the roll. {@link #act} runs on the server's ticks (moves, presses, markers), {@link #watch} on the
 * client's (turning the fighter); the runner keeps the two clocks in lockstep.</p>
 */
abstract class Shot {
    /** The shot's name: its frames go to {@code film/<name>/}. */
    final String name;
    /** Its length in game ticks, and the time of day it is filmed at. */
    final int length;
    final long time;

    Shot(String name, int length, long time) {
        this.name = name;
        this.length = length;
        this.time = time;
    }

    /** Server, just before the roll: the fighter's power and place, and every actor in the scene. */
    abstract void cast(ServerPlayer p);

    /** Client, at the roll: the camera's move, in world coordinates ({@link Stage#at}). */
    abstract FilmCamera.Move camera();

    /** What the move is carried along with, or null for a fixed move. The camera also keeps this in sight. */
    Supplier<Vec3> follow(Minecraft mc) { return () -> mc.player == null ? null : mc.player.position(); }

    /** How quickly the camera keeps up with {@link #follow} (0 holds the move where it was planned). */
    double followRate() { return 0; }

    /** The nearest the camera may come to what it looks at. */
    double near() { return 0; }

    /** Playback speed at shot tick {@code tick}: 1 is real time, 0.35 slow motion. */
    double speed(double tick) { return 1; }

    /** Server, on each tick of the shot. */
    void act(ServerPlayer p, int tick) {}

    /** Client, on each tick of the shot. */
    void watch(Minecraft mc, int tick) {}

    /** {@code low} between {@code from} and {@code to}, easing in and out over three ticks; 1 elsewhere. */
    static double slow(double tick, double from, double to, double low) {
        double edge = 3;
        double in = Mth.clamp((tick - (from - edge)) / edge, 0, 1), out = Mth.clamp(((to + edge) - tick) / edge, 0, 1);
        double k = Math.min(in, out);
        k = k * k * (3 - 2 * k);
        return Mth.lerp(k, 1, low);
    }

    /**
     * A slow arc round {@code look} that passes through {@code eye} halfway: swinging {@code spread} degrees, pushing in
     * from {@code from} to {@code to} times the distance, the field of view narrowing from {@code fov0} to {@code fov1}.
     */
    static FilmCamera.Move arc(int ticks, Vec3 eye, Vec3 look, double spread, double from, double to, double fov0, double fov1) {
        Vec3 v = eye.subtract(look);
        double r = Math.hypot(v.x, v.z), a = Math.toDegrees(Math.atan2(v.z, v.x));
        return FilmCamera.Move.orbit(ticks, FilmCamera.Ease.BOTH, look, look, a - spread / 2, a + spread / 2,
                r * from, r * to, v.y * from, v.y * to, fov0, fov1, 0);
    }
}
