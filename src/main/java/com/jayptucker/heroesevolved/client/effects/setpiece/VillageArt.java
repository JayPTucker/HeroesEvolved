package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.UP;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.hash;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.rgb;
import static net.minecraft.util.Mth.TWO_PI;
import static net.minecraft.util.Mth.cos;
import static net.minecraft.util.Mth.sin;

/**
 * A village's meltdown ({@code village/VillagerMeltdown}): the villager the Eclipse woke a power in, glowing hotter as
 * his minute runs out, and the column of light over the safe point his village is carried to. Both are re-sent every
 * ten ticks and live twelve, fading in and out over two, so the copies overlap into one steady picture.
 */
final class VillageArt {
    private static final float[] EMBER = rgb(0xFF4A0C), HEAT = rgb(0xFF9330), WHITE_HOT = rgb(0xFFF2D2),
            COLD = rgb(0x9FE6FF), SAFE = rgb(0x9CF5B0), PALE = rgb(0xEAFFF0);

    private VillageArt() {}

    static void draw(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float life = a.piece().ticks, f = Mth.clamp(t / 2, 0, 1) * Mth.clamp((life - t) / 2, 0, 1);
        if (f <= 0) return;
        // A clock that runs on across the re-sent copies, so pulses and turns don't restart every ten ticks.
        float clock = c.level != null ? (c.level.getGameTime() % 24000) + c.partial : t;
        switch (a.piece()) {
            case VILLAGE_MELTDOWN -> meltdown(c, a, f, clock);
            case VILLAGE_SAFE -> safe(c, a, f, clock);
            default -> {}
        }
    }

    /**
     * The villager: a warm light under his skin that swells and quickens as it builds, cracks of white along him, rings
     * of heat drawing in, and at the end a pillar of light. While something holds it in, a cold ring closes round him.
     */
    private static void meltdown(Canvas c, Active a, float f, float clock) {
        float scale = a.scale();
        boolean held = scale >= 2;
        float k = Mth.clamp(held ? scale - 2 : scale, 0, 1);
        Entity e = a.bound(c);
        Vec3 feet = e != null ? e.getPosition(c.partial) : a.origin();
        Vec3 body = feet.add(0, 1.0, 0);
        int seed = a.seed() >>> 4;
        float rate = 0.25F + 0.9F * k;
        float pulse = 1 + (0.12F + 0.2F * k) * sin(clock * rate);
        c.glow(body, (0.9F + 3.2F * k * k) * pulse, HEAT, f * (0.22F + 0.35F * k));
        c.glow(body, (0.45F + 1.1F * k) * pulse, WHITE_HOT, f * (0.55F + 0.35F * k));
        // Cracks of light over him, more of them as it builds.
        int cracks = 2 + Math.round(6 * k);
        for (int i = 0; i < cracks; i++) {
            float ang = hash(seed, i) * TWO_PI + clock * 0.02F;
            Vec3 p0 = feet.add(cos(ang) * 0.32, 0.2 + hash(seed, i + 10) * 1.5, sin(ang) * 0.32);
            Vec3 p1 = p0.add(cos(ang + 0.6F) * 0.12, (hash(seed, i + 20) - 0.5) * 0.7, sin(ang + 0.6F) * 0.12);
            c.bolt(p0, p1, seed + i + (int) (clock / 3), 0.15F, 0.03F + 0.03F * k, i % 2 == 0 ? WHITE_HOT : HEAT, f * (0.5F + 0.4F * k));
        }
        // Rings of heat drawn in toward him, quicker as it comes.
        for (int i = 0; i < 3; i++) {
            float phase = (clock * (0.02F + 0.06F * k) + i / 3F) % 1;
            float r = (2.5F + 3 * k) * (1 - phase) + 0.4F;
            c.ring(body, UP, r, 0.05F + 0.06F * k, 0, TWO_PI, i == 0 ? WHITE_HOT : HEAT, f * 0.6F * phase * (0.35F + 0.65F * k));
        }
        // The ground scorching under him.
        c.ring(feet.add(0, 0.06, 0), UP, 0.8F + 2.2F * k, 0.12F, 0, TWO_PI, EMBER, f * 0.45F * k);
        // The last stretch: a pillar of light climbs out of him.
        float pillar = Mth.clamp((k - 0.75F) / 0.25F, 0, 1);
        if (pillar > 0) c.line(body.add(0, -0.8, 0), body.add(0, 4 + 26 * pillar, 0), 0.15F + 0.6F * pillar, WHITE_HOT, f * 0.55F * pillar, 0);
        if (held) {
            // Held: a cold band closes round him and the heat falls back.
            float spin = clock * 0.15F;
            c.ring(body, UP, 1.3F, 0.1F, spin, spin + TWO_PI * 0.8F, COLD, f * 0.7F);
            c.ring(body.add(0, 0.5, 0), UP, 1.1F, 0.06F, -spin, -spin + TWO_PI * 0.6F, COLD, f * 0.5F);
            c.glow(body, 1.6F, COLD, f * 0.25F);
        }
    }

    /**
     * The safe point: a soft green column of light that can be seen across the village, a ring on the ground the size of
     * the safe area, and dashes turning slowly round it.
     */
    private static void safe(Canvas c, Active a, float f, float clock) {
        Vec3 at = a.origin().add(0, 0.08, 0);
        float r = Math.max(1, a.scale());
        float breathe = 0.85F + 0.15F * sin(clock * 0.12F);
        c.line(at, at.add(0, 48, 0), 0.9F * breathe, SAFE, f * 0.35F, 0);
        c.line(at, at.add(0, 30, 0), 0.3F, PALE, f * 0.55F, 0);
        c.glow(at.add(0, 0.6, 0), 2.2F * breathe, SAFE, f * 0.3F);
        c.ring(at, UP, r, 0.14F, 0, TWO_PI, SAFE, f * 0.55F);
        int dashes = 12;
        for (int i = 0; i < dashes; i++) {
            float from = clock * 0.01F + i * TWO_PI / dashes;
            c.ring(at.add(0, 0.05, 0), UP, r + 0.5F, 0.1F, from, from + TWO_PI / dashes * 0.45F, PALE, f * 0.6F);
        }
    }
}
