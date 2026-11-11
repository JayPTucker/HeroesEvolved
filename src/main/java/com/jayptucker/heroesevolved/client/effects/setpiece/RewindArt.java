package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.RewindTravelClient;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.hash;

/**
 * Trips back through the past: behind each traveller, the path they have just been carried back along is a
 * violet trail (the rewind's own colour, as the vision's crowd ran back), with a white thread through it and
 * glints of the moments passed. From your own trip you see it stretching away ahead of you, the way you came.
 */
final class RewindArt {
    private static final float[] DEEP = {0.36F, 0.25F, 0.82F}, VIOLET = {0.6F, 0.49F, 1}, PALE = {0.96F, 0.92F, 1}, GOLD = {1, 0.78F, 0.25F};
    private static final int TRAIL = 16;

    private RewindArt() {}

    static boolean live() { return !RewindTravelClient.trips().isEmpty(); }

    static void drawLive(Canvas c) {
        if (!c.glow() && !c.soft()) return;
        long now = RewindTravelClient.clock();
        for (RewindTravelClient.Trip trip : RewindTravelClient.trips()) {
            if (!trip.here()) continue;
            float index = trip.index(now, c.partial);
            float fade = Mth.clamp((trip.count() - 1 - index) / 4F + 0.25F, 0, 1);
            int first = Math.max(0, (int) index - TRAIL);
            int n = (int) Math.ceil(index) - first + 1;
            if (n < 2) continue;
            for (int strand = 0; strand < 3; strand++) {
                double[] x = new double[n], y = new double[n], z = new double[n];
                float[] a = new float[n], w = new float[n];
                for (int i = 0; i < n; i++) {
                    float at = Math.min(index, first + i);
                    Vec3 p = trip.at(at);
                    float q = i / (float) (n - 1), twist = at * 0.7F + strand * 2.1F;
                    // Faded where it passes the camera, so a traveller's own view is not striped by it.
                    double dx = p.x - c.cx, dy = p.y + 1 - c.cy, dz = p.z - c.cz;
                    float clear = Mth.clamp(((float) Math.sqrt(dx * dx + dy * dy + dz * dz) - 1.2F) / 2F, 0, 1);
                    x[i] = p.x + Mth.sin(twist) * 0.35; y[i] = p.y + 0.5 + strand * 0.45 + Mth.cos(twist) * 0.15; z[i] = p.z + Mth.cos(twist) * 0.35;
                    a[i] = (strand == 1 ? 0.9F : 0.6F) * fade * Mth.sin(q * Mth.PI * 0.5F + 0.15F) * clear;
                    w[i] = strand == 1 ? 0.05F : 0.18F;
                }
                if (c.glow()) c.ribbon(x, y, z, a, w, strand == 1 ? PALE : VIOLET);
                else if (strand != 1) c.ribbon(x, y, z, a, w, DEEP);
            }
            if (c.glow()) for (int g = 0; g < 8; g++) {
                float at = index - hash(trip.entity, g) * TRAIL;
                if (at < 0) continue;
                Vec3 p = trip.at(at).add((hash(trip.entity, g + 10) - 0.5) * 1.4, 0.3 + hash(trip.entity, g + 20) * 1.6, (hash(trip.entity, g + 30) - 0.5) * 1.4);
                c.glow(p, 0.15F, g % 3 == 0 ? GOLD : PALE, 0.7F * fade);
            }
        }
    }
}
