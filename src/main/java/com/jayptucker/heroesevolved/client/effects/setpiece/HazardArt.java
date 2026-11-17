package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.radiation.MeltdownField;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.UP;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.hash;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.mix;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.rgb;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.smooth;
import static net.minecraft.util.Mth.TWO_PI;
import static net.minecraft.util.Mth.cos;
import static net.minecraft.util.Mth.sin;

/**
 * A meltdown's hazards, in Induced Radioactivity's own colours (heat orange going white-hot, sickness yellow-green), and
 * bodies burning up. A shock wave is a pressure front: a dome of heat haze racing out over a hot ring on the ground,
 * with a sickly ring behind it and dust thrown up where it passes. The near-field's edge is a dashed ring of heat and
 * green on a faintly glowing floor. Burning bodies come apart in cubes: a few peel off a living one; a dead one breaks
 * apart from the head down, each cube glowing white-hot, drifting up on the heat and cooling to ash.
 */
public final class HazardArt {
    private static final float[] EMBER = rgb(0xFF4A0C), HEAT = rgb(0xFF9330), WHITE_HOT = rgb(0xFFF2D2),
            TOXIC = rgb(0xC4F23C), CHAR = rgb(0x2A1410), ASH = {0.3F, 0.28F, 0.26F}, DUST = {0.62F, 0.56F, 0.48F},
            HAZE = rgb(0xFFD7A8);
    private static final float[][] GRIT = {{0.42F, 0.3F, 0.2F}, {0.5F, 0.5F, 0.5F}, {0.36F, 0.36F, 0.34F}};

    private HazardArt() {}

    static void draw(Canvas c, Active a, float t) {
        switch (a.piece()) {
            case RAD_SHOCK -> shock(c, a, t);
            case RAD_FIELD -> field(c, a, t);
            case RAD_FLAKE -> flake(c, a, t);
            case RAD_DISINTEGRATE -> disintegrate(c, a, t);
            default -> { }
        }
    }

    private static float out(float x) { x = Mth.clamp(x, 0, 1); return 1 - (1 - x) * (1 - x) * (1 - x); }

    // ------------------------------------------------------------------
    // A shock wave
    // ------------------------------------------------------------------

    private static void shock(Canvas c, Active a, float t) {
        Vec3 o = a.origin(), g = o.add(0, 0.06, 0);
        float r = a.scale(), s = (float) Mth.clamp(a.target().y - o.y, 0.05, 1), life = a.piece().ticks;
        float front = r * out(t / MeltdownField.WAVE_TICKS), fade = 1 - smooth(MeltdownField.WAVE_TICKS - 1, life, t);
        int seed = a.seed();
        if (c.glow()) {
            if (t < 3) c.glow(o.add(0, 1, 0), 1.5F + 3 * s, WHITE_HOT, 0.8F * (1 - t / 3) * s);
            c.ring(g, UP, front, 0.15F + 0.35F * s, 0, TWO_PI, WHITE_HOT, 0.75F * fade * (0.4F + 0.6F * s));
            c.ring(g, UP, front * 1.03F, 0.5F + 0.9F * s, 0, TWO_PI, HEAT, 0.35F * fade);
            if (t > 1) c.ring(g.add(0, 0.02, 0), UP, front * 0.88F, 0.25F, 0, TWO_PI, TOXIC, 0.45F * fade);
            // Heat cracks thrown out over the ground by the strongest waves.
            int cracks = Math.round(4 * s);
            for (int i = 0; i < cracks; i++) {
                float ang = (i + hash(seed, i)) * TWO_PI / Math.max(1, cracks), len = front * (0.4F + 0.4F * hash(seed, i + 9));
                c.bolt(g, g.add(cos(ang) * len, 0, sin(ang) * len), seed + i, 0.25F, 0.03F, EMBER, 0.6F * fade);
            }
        }
        if (c.soft()) {
            // The pressure front itself: a dome of heat haze, brightest where you see its edge.
            float dome = 1 - smooth(MeltdownField.WAVE_TICKS * 0.6F, MeltdownField.WAVE_TICKS + 3, t);
            c.shell(o, front, front * (0.3F + 0.3F * s), front, Mth.HALF_PI, HAZE, 0.22F * s * dome, 0.04F, Canvas.PLAIN, 0);
            int dust = 8 + Math.round(14 * s);
            for (int i = 0; i < dust; i++) {
                float ang = (i + hash(seed, i + 30) * 0.6F) * TWO_PI / dust;
                float size = (0.35F + 0.4F * hash(seed, i + 31)) * (0.5F + s) * (0.6F + 0.6F * smooth(0, 8, t));
                Vec3 at = o.add(cos(ang) * front, size * 0.4F + t * 0.02F, sin(ang) * front);
                c.cloud(at, size * 1.3F, size * 0.6F, size * 1.3F, 40 + i, ang + t * 0.02F,
                        0.3F * s * smooth(0, 2, t) * fade, Shades.smoke(c, DUST, 0));
            }
        }
        if (c.solid() && s > 0.3F) {
            for (int i = 0; i < Math.round(8 * s); i++) {
                float ang = hash(seed, i + 70) * TWO_PI, d = front * (0.6F + 0.4F * hash(seed, i + 71));
                float y = Math.max(0.05F, 0.2F + t * 0.18F - t * t * 0.02F);
                c.box(o.add(cos(ang) * d, y, sin(ang) * d), 0.06F, 0.05F, 0.06F, i, t * 0.4F, i, GRIT[i % GRIT.length], fade > 0.05F ? 1 : 0);
            }
        }
    }

    // ------------------------------------------------------------------
    // The near-field's edge
    // ------------------------------------------------------------------

    private static void field(Canvas c, Active a, float t) {
        float f = Canvas.fade(t, a.piece().ticks, 2, 2);
        if (f <= 0) return;
        Vec3 o = a.origin().add(0, 0.07, 0);
        float r = a.scale(), now = (c.level == null ? t : c.level.getGameTime() % 24000L) + c.partial;
        int seed = (int) Math.floor(o.x * 13 + o.z * 7);
        if (c.soft()) c.disc(o.add(0, -0.03, 0), UP, r, EMBER, 0.10F * f, 0.03F * f);
        if (c.glow()) {
            int dashes = 24;
            for (int k = 0; k < dashes; k++) {
                float from = now * 0.02F + k * TWO_PI / dashes;
                c.ring(o, UP, r, 0.12F, from, from + TWO_PI / dashes * 0.6F, k % 2 == 0 ? HEAT : TOXIC, 0.6F * f);
            }
            c.ring(o, UP, r * 0.97F, 0.4F, 0, TWO_PI, EMBER, 0.18F * f);
            for (int i = 0; i < 10; i++) {
                float cycle = (now * 0.04F + hash(seed, i)) % 1;
                float ang = hash(seed, i + 10) * TWO_PI, d = r * (float) Math.sqrt(hash(seed, i + 20));
                c.glow(o.add(cos(ang) * d, cycle * 2.6F, sin(ang) * d), 0.12F, i % 3 == 0 ? TOXIC : HEAT, 0.7F * f * sin(cycle * Mth.PI));
            }
        }
    }

    // ------------------------------------------------------------------
    // Cubes peeling off a burning body
    // ------------------------------------------------------------------

    private static void flake(Canvas c, Active a, float t) {
        Entity e = a.bound(c);
        Vec3 base = a.origin();
        float h = e != null ? e.getBbHeight() : (float) Math.max(0.5, a.target().y - base.y), w = e != null ? e.getBbWidth() : 0.6F;
        float k = a.scale();
        int seed = a.seed(), n = 3 + Math.round(9 * k);
        for (int i = 0; i < n; i++) {
            float age = t - hash(seed, i) * 8;
            if (age < 0) continue;
            float heat = 1 - Mth.clamp(age / 14, 0, 1), size = (0.05F + 0.04F * hash(seed, i + 1)) * (1 - Mth.clamp(age / 18, 0, 1));
            if (size <= 0.004F) continue;
            float ang = hash(seed, i + 2) * TWO_PI, swirl = age * 0.1F + ang;
            Vec3 at = base.add(cos(ang) * (w * 0.5 + age * 0.02), hash(seed, i + 3) * h + age * (0.03 + 0.03 * hash(seed, i + 4)),
                    sin(ang) * (w * 0.5 + age * 0.02)).add(cos(swirl) * 0.05, 0, sin(swirl) * 0.05);
            if (c.solid()) c.box(at, size, size, size, age * 0.2F + i, age * 0.15F, i, mix(ASH, mix(EMBER, HEAT, heat), heat), 1);
            if (c.glow() && heat > 0.05F) c.glow(at, size * 3, HEAT, 0.5F * heat * k);
        }
    }

    // ------------------------------------------------------------------
    // A body burning away
    // ------------------------------------------------------------------

    private static final Map<Integer, Long> DISSOLVING = new HashMap<>();

    /** Is this body burning away (or burnt away and still dead), so its corpse is not drawn? */
    public static boolean disintegrating(Entity entity) {
        Long until = DISSOLVING.get(entity.getId());
        var level = Minecraft.getInstance().level;
        if (until == null || level == null) return false;
        if (entity instanceof LivingEntity living && living.isDeadOrDying()) return true;
        if (level.getGameTime() > until) { DISSOLVING.remove(entity.getId()); return false; }
        return true;
    }

    /** A humanoid's parts, in blocks (width, height, depth, and the centre's x and y) for a body two blocks tall, facing +z. */
    private static final float[][] HUMANOID = {
            {0.5F, 0.5F, 0.5F, 0, 1.75F}, {0.5F, 0.75F, 0.25F, 0, 1.125F},
            {0.25F, 0.75F, 0.25F, 0.375F, 1.125F}, {0.25F, 0.75F, 0.25F, -0.375F, 1.125F},
            {0.25F, 0.75F, 0.25F, 0.125F, 0.375F}, {0.25F, 0.75F, 0.25F, -0.125F, 0.375F}};

    private static void disintegrate(Canvas c, Active a, float t) {
        if (a.entity() >= 0 && c.level != null) {
            DISSOLVING.put(a.entity(), Math.max(DISSOLVING.getOrDefault(a.entity(), 0L), a.start() + a.piece().ticks));
            if (DISSOLVING.size() > 128) DISSOLVING.clear();
        }
        Vec3 o = a.origin(), dir = a.facing(), side = new Vec3(dir.z, 0, -dir.x);
        float h = a.scale(), w = (float) a.target().subtract(o).horizontalDistance();
        boolean humanoid = h > 1.3F && h < 2.4F && w < 0.9F;
        float[][] parts = humanoid ? HUMANOID : new float[][] {{w, h, w, 0, h / 2}};
        float scaleY = humanoid ? h / 2 : 1;
        float cube = humanoid ? 0.125F : Math.max(0.125F, Math.max(w, h) / 12);
        int seed = a.seed(), i = 0;
        float life = a.piece().ticks;
        for (float[] part : parts) {
            int nx = Math.max(1, Math.round(part[0] / cube)), ny = Math.max(1, Math.round(part[1] * scaleY / cube)), nz = Math.max(1, Math.round(part[2] / cube));
            for (int x = 0; x < nx; x++) for (int y = 0; y < ny; y++) for (int z = 0; z < nz; z++, i++) {
                if (i > 700) break;
                float lx = part[3] - part[0] / 2 + (x + 0.5F) * part[0] / nx;
                float ly = (part[4] - part[1] / 2) * scaleY + (y + 0.5F) * part[1] * scaleY / ny;
                float lz = -part[2] / 2 + (z + 0.5F) * part[2] / nz;
                // The head goes first, the feet last.
                float release = 6 + (1 - ly / Math.max(0.5F, h)) * 34 + hash(seed, i) * 10;
                float flown = t - release;
                Vec3 home = o.add(side.scale(lx)).add(0, ly, 0).add(dir.scale(lz));
                Vec3 at;
                float heat, size;
                if (flown < 0) {
                    at = home;
                    heat = 1;
                    size = cube * 0.5F;
                } else {
                    Vec3 outward = new Vec3(lx, 0, lz).lengthSqr() < 1e-4 ? side : side.scale(lx).add(dir.scale(lz)).normalize();
                    float swirl = flown * 0.08F + hash(seed, i + 300) * TWO_PI;
                    at = home.add(outward.scale(flown * (0.02 + 0.02 * hash(seed, i + 100))))
                            .add(cos(swirl) * flown * 0.009, flown * (0.03 + 0.03 * hash(seed, i + 200)), sin(swirl) * flown * 0.009);
                    heat = 1 - Mth.clamp(flown / 30, 0, 1);
                    size = cube * 0.5F * (1 - Mth.clamp(flown / 40, 0, 1));
                }
                if (size <= 0.004F) continue;
                if (c.solid()) {
                    // Still in the body's shape: charred, with the heat breaking through as it goes.
                    float[] col = flown < 0 ? mix(CHAR, EMBER, 0.35F + 0.65F * smooth(0, 6, t)) : mix(ASH, mix(EMBER, HEAT, heat), heat);
                    c.box(at, size, size, size, Math.max(0, flown) * 0.2F + i, Math.max(0, flown) * 0.15F, i, col, 1);
                }
                if (c.glow() && heat > 0.05F && i % 2 == 0) c.glow(at, size * 4, flown < 0 ? EMBER : HEAT, 0.35F * heat);
            }
        }
        Vec3 body = o.add(0, h * 0.55, 0);
        if (c.glow()) {
            float flash = 1 - smooth(0, 10, t);
            if (flash > 0) c.glow(body, h * 1.2F, WHITE_HOT, 0.6F * flash);
            c.glow(body, h * 0.9F, HEAT, 0.3F * (1 - smooth(10, 40, t)));
        }
        if (c.soft()) {
            var ash = Shades.smoke(c, ASH, 0);
            for (int k = 0; k < 6; k++) {
                float start = 10 + k * 6;
                if (t < start) continue;
                float age = t - start, k2 = Mth.clamp(age / 30, 0, 1);
                Vec3 at = body.add((hash(seed, k + 500) - 0.5) * w, age * 0.05 + hash(seed, k + 510) * h * 0.4, (hash(seed, k + 520) - 0.5) * w);
                c.cloud(at, 0.3F + k2 * 0.6F, 0.25F + k2 * 0.45F, 0.3F + k2 * 0.6F, k, age * 0.03F,
                        0.4F * (1 - k2) * Mth.clamp((life - t) / 10, 0, 1), ash);
            }
        }
    }
}
