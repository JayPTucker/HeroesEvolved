package com.jayptucker.heroesevolved.trailer;

import com.jayptucker.heroesevolved.expansion.Signatures;
import com.jayptucker.heroesevolved.uitest.PreviewQuiet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A power's signature set piece (run by {@code expansion/Signatures}), staged as the signature preview stages it
 * ({@code uitest/SignaturesPreview}: the same places, foes and beats, in the shot frame of {@link Stage}) and filmed
 * with a slow arc through that preview's camera, slow motion on its impact.
 */
final class SignatureShot extends Shot {
    /** Ticks of the shot before the signature is cast, so the camera is already moving when it starts. */
    static final int LEAD = 8;

    private final ResourceLocation power;
    private final String node, mechanic;
    private final Consumer<ServerPlayer> setup;
    private final Predicate<ServerPlayer> activate;
    private final Vec3 eye, look;
    private final int impact, press;
    private final double slowFrom, slowTo, low;
    /** Distance and field of view of the arc: framed tighter than the preview's wide still. */
    private double from = 1.15, to = .95, fov0 = 58, fov1 = 50, spread = 22;

    /**
     * @param eye      where the signature preview's camera stood (shot frame), the arc's midpoint
     * @param look     what it looked at (shot frame)
     * @param impact   the beat (ticks after the cast) the music hits and slow motion centres on
     * @param slowFrom slow motion from this many ticks after the cast
     * @param slowTo   until this many
     * @param press    ticks after the cast of the second press, or -1
     */
    SignatureShot(String name, ResourceLocation power, String node, long time, int after,
                  Consumer<ServerPlayer> setup, Predicate<ServerPlayer> activate, Vec3 eye, Vec3 look,
                  int impact, double slowFrom, double slowTo, double low, int press, String mechanic) {
        super(name, LEAD + after, time);
        this.power = power;
        this.node = node;
        this.setup = setup;
        this.activate = activate;
        this.eye = eye;
        this.look = look;
        this.impact = impact;
        this.slowFrom = slowFrom;
        this.slowTo = slowTo;
        this.low = low;
        this.press = press;
        this.mechanic = mechanic;
    }

    /** A different framing: distance from {@code from} to {@code to} of the preview's, field of view, swing. */
    SignatureShot frame(double from, double to, double fov0, double fov1, double spread) {
        this.from = from;
        this.to = to;
        this.fov0 = fov0;
        this.fov1 = fov1;
        this.spread = spread;
        return this;
    }

    @Override void cast(ServerPlayer p) {
        TrailerPreview.grant(p, power, node, -1);
        setup.accept(p);
    }

    @Override FilmCamera.Move camera() {
        Vec3 e = Stage.at(eye.x, eye.y, eye.z).add(0, Stage.lift, 0), l = Stage.at(look.x, look.y, look.z);
        return arc(length, e, l, spread, from, to, fov0, fov1);
    }

    @Override double speed(double tick) { return slow(tick, LEAD + slowFrom, LEAD + slowTo, low); }

    @Override void act(ServerPlayer p, int tick) {
        int t = tick - LEAD;
        if (t == 0) {
            PreviewQuiet.check("TRAILER_SIGNATURE", activate.test(p), name + " cast");
            Stage.mark("cast");
        }
        if (t == impact) Stage.mark("impact");
        if (press >= 0 && t == press) PreviewQuiet.check("TRAILER_SIGNATURE", Signatures.press(p, mechanic), name + " second press");
    }
}
