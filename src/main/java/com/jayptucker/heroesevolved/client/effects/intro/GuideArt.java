package com.jayptucker.heroesevolved.client.effects.intro;

import com.jayptucker.heroesevolved.client.effects.setpiece.Canvas;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The Last Day's clairvoyance: a thread of light at waist height along the way the server worked out for this player
 * ({@code story/prologue/LastDayGuide}), as if they could already see where they'll walk. It starts a couple of steps
 * ahead (never across their own eyes), is brightest near them and thins away along the route, with slow pulses running
 * along it toward the goal so it reads as a direction; where it ends, it gathers into a soft glow. It fades in when a new
 * way is sent and out when it's cleared.
 */
public final class GuideArt {
    /** Halo, body and core: a pale, cool light, like a memory of something that hasn't happened yet. */
    private static final float[] HALO = Canvas.rgb(0x4FA6E0), BODY = Canvas.rgb(0xA8E4FF), CORE = Canvas.rgb(0xF4FCFF);
    private static List<Vec3> route = List.of(), fading = List.of();
    private static long changed = -1000;
    /** Waist height over the floor, how far along the route it is drawn, where it starts from the player. */
    private static final double WAIST = 0.85, REACH = 64, START = 2.2;

    private GuideArt() {}

    /**
     * A new way from the server (empty clears it). The same goal from a new start (the player walked on) replaces it in
     * place; a new goal fades the old thread out and the new one in.
     */
    public static void route(List<Vec3> points) {
        Minecraft mc = Minecraft.getInstance();
        List<Vec3> next = smooth(points);
        boolean sameGoal = !route.isEmpty() && !next.isEmpty() && route.getLast().distanceToSqr(next.getLast()) < 2.5;
        if (sameGoal) { route = next; return; }
        fading = route;
        route = next;
        changed = mc.level == null ? 0 : mc.level.getGameTime();
    }

    public static boolean live() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && TimeIntroService.isCity(mc.level) && (!route.isEmpty() || !fading.isEmpty());
    }

    public static void reset() { route = fading = List.of(); }

    /** Rounds the corners a little (Chaikin, twice), so the thread curves through a turn rather than snapping. */
    private static List<Vec3> smooth(List<Vec3> in) {
        List<Vec3> pts = new ArrayList<>(in);
        for (int pass = 0; pass < 2 && pts.size() > 2; pass++) {
            List<Vec3> next = new ArrayList<>();
            next.add(pts.getFirst());
            for (int i = 0; i < pts.size() - 1; i++) {
                Vec3 a = pts.get(i), b = pts.get(i + 1);
                // Keep climbs crisp: a stair's corner rounded would cut through the steps.
                if (Math.abs(a.y - b.y) > 0.2) { next.add(b); continue; }
                next.add(a.scale(0.75).add(b.scale(0.25)));
                next.add(a.scale(0.25).add(b.scale(0.75)));
            }
            next.add(pts.getLast());
            pts = next;
        }
        return pts;
    }

    public static void drawLive(Canvas c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !live()) return;
        float age = mc.level.getGameTime() + c.partial() - changed;
        float in = Mth.clamp(age / 12, 0, 1), out = 1 - Mth.clamp(age / 10, 0, 1);
        Vec3 me = mc.player.getPosition(c.partial());
        if (!fading.isEmpty() && out > 0) thread(c, fading, me, out, age);
        if (!route.isEmpty()) thread(c, route, me, in, age);
    }

    /** The thread along {@code pts}, from where it passes nearest the player onward. */
    private static void thread(Canvas c, List<Vec3> pts, Vec3 me, float strength, float age) {
        if (pts.size() < 2 || strength <= 0) return;
        // Start from the point of the route nearest the player, then a couple of steps further on.
        int nearest = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < pts.size(); i++) {
            double d = pts.get(i).distanceToSqr(me);
            if (d < best) { best = d; nearest = i; }
        }
        List<Vec3> line = new ArrayList<>();
        List<Double> along = new ArrayList<>();
        double walked = 0;
        Vec3 last = null;
        for (int i = nearest; i < pts.size() && walked < REACH + START; i++) {
            Vec3 p = pts.get(i).add(0, WAIST, 0);
            if (last != null) walked += p.distanceTo(last);
            // Fine steps along each leg, so the thread's light can fade smoothly along it.
            if (last != null) {
                double leg = p.distanceTo(last);
                int steps = Math.max(1, (int) (leg / 1.5));
                for (int k = 1; k < steps; k++) {
                    line.add(last.lerp(p, k / (double) steps));
                    along.add(walked - leg + leg * k / steps);
                }
            }
            line.add(p);
            along.add(walked);
            last = p;
        }
        if (line.size() < 2) return;
        boolean ends = nearest + 1 >= pts.size() || walked < REACH + START;
        float now = (Minecraft.getInstance().level.getGameTime() + c.partial()) * 0.35F;
        int n = line.size();
        Vec3[] points = line.toArray(new Vec3[0]);
        float[] halo = new float[n], body = new float[n], core = new float[n];
        float[] wHalo = new float[n], wBody = new float[n], wCore = new float[n];
        for (int i = 0; i < n; i++) {
            double d = along.get(i);
            // Faint right at your feet, full a couple of steps on, thinning away into the distance.
            float near = (float) Mth.clamp((d - START * 0.4) / START, 0, 1);
            float far = (float) Mth.clamp(1 - (d - 18) / (REACH - 18), 0, 1);
            float a = strength * near * (0.35F + 0.65F * far);
            // Slow pulses running toward the goal.
            float pulse = 0.5F + 0.5F * Mth.sin((float) (d * 0.45 - now));
            halo[i] = 0.32F * a;
            body[i] = (0.65F + 0.3F * pulse) * a;
            core[i] = (0.85F + 0.15F * pulse) * a;
            float thin = 0.6F + 0.4F * far;
            wHalo[i] = 0.5F * thin;
            wBody[i] = 0.13F * thin;
            wCore[i] = 0.045F * thin;
        }
        if (c.glow()) {
            c.ribbon(points, wHalo, HALO, halo);
            c.ribbon(points, wBody, BODY, body);
            c.ribbon(points, wCore, CORE, core);
            if (ends) {
                Vec3 end = points[n - 1];
                float breathe = 0.8F + 0.2F * Mth.sin(now * 0.6F);
                c.glow(end, 1.1F * breathe, BODY, 0.55F * strength);
                c.glow(end, 0.4F, CORE, 0.8F * strength);
                c.ring(end.add(0, -WAIST + 0.06, 0), Canvas.UP, 0.7F + 0.15F * Mth.sin(now), 0.08F, 0, Mth.TWO_PI, BODY, 0.6F * strength);
            }
        }
    }
}
