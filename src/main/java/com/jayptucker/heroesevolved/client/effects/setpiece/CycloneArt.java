package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.UP;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.hash;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.rgb;
import static net.minecraft.util.Mth.TWO_PI;
import static net.minecraft.util.Mth.cos;
import static net.minecraft.util.Mth.sin;

/**
 * A slow Cyclone's vacuum: the air dragged out of the ring. Pale pressure rings collapse inward at several heights, dust
 * and motes are sucked toward the eye, and a faint dome of thinned air hangs over it. Re-sent every ten ticks as a
 * twelve-tick piece that fades in and out over two, so the copies hand over seamlessly.
 */
final class CycloneArt {
    private static final float[] AIR = rgb(0xDCE6EC), PALE = rgb(0xF4F8FA), DUSK = rgb(0x6C7884), GOLD = rgb(0xFFC23A);

    private CycloneArt() {}

    static void vacuum(Canvas c, Active a, float t) {
        float f = Canvas.fade(t, a.piece().ticks, 2, 2);
        if (f <= 0) return;
        Vec3 o = a.origin();
        float r = a.scale(), now = (c.level == null ? t : c.level.getGameTime() % 24000L) + c.partial;
        int seed = (int) Math.floor(o.x * 11 + o.z * 5);
        if (c.soft()) {
            c.disc(o.add(0, 0.04, 0), UP, r, DUSK, 0.12F * f, 0.02F * f);
            c.shell(o, r, r * 0.55F, r, Mth.HALF_PI, AIR, 0.14F * f, 0.03F, Canvas.PLAIN, now * 0.02F);
        }
        if (c.glow()) {
            // Rings of thinning air closing on the eye, one after another, at three heights.
            for (int k = 0; k < 6; k++) {
                float phase = (now * 0.035F + k / 6F) % 1;
                float rr = r * (1 - phase) + 0.4F, h = 0.2F + (k % 3) * 0.75F;
                c.ring(o.add(0, h, 0), UP, rr, 0.06F + 0.05F * phase, 0, TWO_PI, k % 2 == 0 ? PALE : AIR, 0.5F * phase * f);
            }
            // Motes sucked in from the edge, spiralling a little with the run.
            for (int i = 0; i < 16; i++) {
                float cycle = (now * 0.03F + hash(seed, i)) % 1;
                float ang = hash(seed, i + 20) * TWO_PI + cycle * 1.2F, d = r * (1 - cycle);
                Vec3 p = o.add(cos(ang) * d, 0.2 + hash(seed, i + 40) * 1.8, sin(ang) * d);
                c.glow(p, 0.08F, i % 5 == 0 ? GOLD : PALE, 0.7F * f * Mth.sin(cycle * Mth.PI));
            }
        }
    }
}
