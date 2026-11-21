package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.ClientOverloads;
import com.jayptucker.heroesevolved.client.effects.intro.CloudMesh;
import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.village.overload.OverloadGeometry;
import com.jayptucker.heroesevolved.village.overload.Storm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.UP;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.hash;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.mix;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.rgb;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.smooth;
import static net.minecraft.util.Mth.PI;
import static net.minecraft.util.Mth.TWO_PI;
import static net.minecraft.util.Mth.cos;
import static net.minecraft.util.Mth.sin;

/**
 * Overloads' storms ({@code village/overload}), drawn live from {@link ClientOverloads} every frame, plus their three set
 * pieces. Each storm is its power's own look, grown to the size of weather:
 * <ul>
 *   <li><b>Wildfire:</b> Pyrokinesis's ember flame. A column of fire round them, a ring of flame tongues at the front's
 *   radius ({@link OverloadGeometry#front}) following the ground, smoke rolling off it and a column of smoke over them,
 *   ash falling. Each ember arcs out and lands in a ring that fills before it bursts.</li>
 *   <li><b>Supercell:</b> the shared tornado ({@code SpeedArt}, style STORM) under a wide, slowly turning deck of dark
 *   cloud with lightning in it, and a line of dust on the ground showing where it goes next.</li>
 *   <li><b>Blizzard:</b> Cryokinesis's ice. A spire of shards round them, snow driven sideways round the camera and
 *   swirling at the ground, and a frost ring at its edge. Hidden in the whiteout, except to those who see them.</li>
 * </ul>
 * Whoever is calming them is joined to them by a thread of warm light. The break's warning fills a ring at its reach.
 */
final class OverloadArt {
    /** Calm: a warm gold thread. */
    private static final float[] CALM = rgb(0xFFE3A0), CALM_CORE = rgb(0xFFF8E6);
    /** Smoke and ash. */
    private static final float[] SMOKE = rgb(0x3B3532), SMOKE_LIT = rgb(0x6E625A), ASH = rgb(0x8A8580);
    /** The supercell's deck and dust; its lightning. */
    private static final float[] DECK = rgb(0x3C4442), DECK_LIT = rgb(0x5E6A66), DUST = rgb(0x7A6A58), WIND = rgb(0xC9D3CE),
            FLASH = rgb(0xE6EEFF);
    /** Cryokinesis's ice. */
    private static final float[] ICE_SHADOW = rgb(0x709DB2), ICE = rgb(0xA6D3E5), MIST = rgb(0xDCEBF1), WHITE = rgb(0xF4FAFC),
            SNOW = rgb(0xEEF5FA);

    private OverloadArt() {}

    static boolean live() { return ClientOverloads.any(); }

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }
    private static boolean detail() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.ALL; }

    /** The ground's height under a column, or {@code fallback} if it isn't loaded. */
    private static double ground(Canvas c, double x, double z, double fallback) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!c.level.hasChunk(bx >> 4, bz >> 4)) return fallback;
        int y = c.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        return Math.abs(y - fallback) > 12 ? fallback : y;
    }

    static void drawLive(Canvas c) {
        long now = c.level.getGameTime();
        Vec3 camera = c.camera();
        for (ClientOverloads.View v : ClientOverloads.views()) {
            if (v.storm == null) continue;
            Vec3 eye = v.at(c.partial);
            if (eye.distanceToSqr(camera) > 256 * 256) continue;
            float f = v.fade(now, c.partial);
            if (f <= 0.003F) continue;
            switch (v.storm) {
                case WILDFIRE -> wildfire(c, v, eye, f, now);
                case SUPERCELL -> supercell(c, v, eye, f, now);
                case BLIZZARD -> blizzard(c, v, eye, f, now);
            }
            threads(c, v, eye, f);
            if (v.breaking()) warning(c, v, eye, f, now);
        }
    }

    // ------------------------------------------------------------------
    // Every storm
    // ------------------------------------------------------------------

    /** A thread of warm light from each helper to them, thicker the calmer they are, a bead running along it. */
    private static void threads(Canvas c, ClientOverloads.View v, Vec3 eye, float f) {
        if (!c.glow() || v.helpers.length == 0) return;
        Vec3 heart = eye.add(0, 1.1, 0);
        float k = 0.3F + 0.7F * v.calm, time = c.time();
        for (int id : v.helpers) {
            Entity e = c.level.getEntity(id);
            if (e == null) continue;
            Vec3 from = e.getPosition(c.partial).add(0, e.getBbHeight() * 0.62, 0);
            c.line(from, heart, 0.16F * k, CALM, f * 0.25F, f * 0.25F);
            c.line(from, heart, 0.05F * k, CALM, f * 0.75F, f * 0.75F);
            c.line(from, heart, 0.016F, CALM_CORE, f, f);
            float u = (time * 0.05F + hash(id, 1)) % 1;
            c.glow(from.lerp(heart, u), 0.22F + 0.2F * v.calm, CALM, f * 0.7F);
        }
        c.glow(heart, 0.6F + 1.4F * v.calm, CALM, f * 0.35F * k);
        // Rings of warm light settling round them, slower and closer the calmer they are.
        for (int i = 0; i < 3; i++) {
            float phase = (time * (0.03F - 0.015F * v.calm) + i / 3F) % 1;
            float r = 1.0F + (2.4F - 1.2F * v.calm) * (1 - phase);
            c.ring(eye.add(0, 2.4 - 1.8 * phase, 0), UP, r, 0.05F, 0, TWO_PI, i == 0 ? CALM_CORE : CALM, f * 0.6F * phase * k);
        }
    }

    /** The break coming: a ring at its reach filling over the warning, with a pulse closing on it. */
    private static void warning(Canvas c, ClientOverloads.View v, Vec3 eye, float f, long now) {
        if (!c.glow() && !c.soft()) return;
        float k = Mth.clamp(v.stageAge(now, c.partial) / OverloadGeometry.BREAK_WARNING, 0, 1);
        float reach = switch (v.storm) {
            case WILDFIRE -> OverloadGeometry.FIRESTORM;
            case SUPERCELL -> OverloadGeometry.pull(1) + 6;
            case BLIZZARD -> OverloadGeometry.reach(1) + 8;
        };
        float[][] fire = PyroArt.emberColors();
        // A body in the storm's dark colour, blended normally so it reads at noon on grass; a light rim over it.
        float[] body = v.storm == Storm.WILDFIRE ? fire[0] : v.storm == Storm.BLIZZARD ? ICE_SHADOW : DECK;
        float[] rim = v.storm == Storm.WILDFIRE ? fire[1] : WHITE;
        Vec3 at = new Vec3(eye.x, ground(c, eye.x, eye.z, eye.y) + 0.08, eye.z);
        Vec3[] path = null;
        if (v.storm == Storm.SUPERCELL) {
            // Touchdown's path: through the bell and out the far side.
            int n = 24;
            path = new Vec3[n + 1];
            for (int i = 0; i <= n; i++) {
                Vec3 p = eye.lerp(v.target, i / (float) n);
                path[i] = new Vec3(p.x, ground(c, p.x, p.z, eye.y) + 0.1, p.z);
            }
        }
        if (c.soft()) {
            // About a block wide: it has to read from across a village.
            c.ring(at, UP, reach, 0.4F, 0, TWO_PI, body, f * 0.45F);
            c.ring(at, UP, reach, 0.9F, -PI / 2, -PI / 2 + TWO_PI * k, body, f * 0.85F);
            if (path != null) c.ribbon(path, 1.4F, body, f * 0.55F * k, f * 0.15F);
            return;
        }
        c.ring(at, UP, reach, 0.12F, 0, TWO_PI, rim, f * 0.35F);
        c.ring(at, UP, reach, 0.22F, -PI / 2, -PI / 2 + TWO_PI * k, rim, f * 0.85F);
        float pulse = (c.time() * 0.06F) % 1;
        c.ring(at, UP, reach * (0.35F + 0.65F * pulse), 0.1F, 0, TWO_PI, rim, f * 0.4F * (1 - pulse) * (0.4F + 0.6F * k));
        if (path != null) c.ribbon(path, 0.25F, WHITE, f * 0.7F * k, f * 0.2F);
    }

    // ------------------------------------------------------------------
    // The wildfire
    // ------------------------------------------------------------------

    private static void wildfire(Canvas c, ClientOverloads.View v, Vec3 eye, float f, long now) {
        if (c.solid()) { ash(c, v, eye, f); return; }
        float power = v.power, time = c.time();
        float R = v.reach();
        boolean breaking = v.breaking();
        float flare = breaking ? 1 + Mth.clamp(v.stageAge(now, c.partial) / OverloadGeometry.BREAK_WARNING, 0, 1) * 0.8F : 1;
        int seed = v.entity * 13;
        // The column of fire round them.
        int tongues = minimal() ? 5 : 9;
        for (int i = 0; i < tongues; i++) {
            float a = i * TWO_PI / tongues + time * 0.05F + hash(seed, i);
            float r = OverloadGeometry.PILLAR_RADIUS * (0.4F + 0.6F * hash(seed, i + 30));
            Vec3 base = eye.add(cos(a) * r, 0.1, sin(a) * r);
            float h = (2.2F + 3.8F * power) * (0.75F + 0.4F * hash(seed, i + 60)) * flare;
            Vec3 tip = base.add(cos(a) * 0.3, h, sin(a) * 0.3);
            PyroArt.emberFlame(c, base, tip, 0.34F + 0.18F * power, time * 0.32F + i * 1.7F, f * (0.75F + 0.25F * power));
        }
        if (c.glow()) {
            float[][] fire = PyroArt.emberColors();
            c.glow(eye.add(0, 1.2, 0), (1.6F + 2.4F * power) * flare, fire[1], f * 0.3F);
            c.glow(eye.add(0, 1.0, 0), 0.9F + power, fire[2], f * 0.45F);
        }
        // The front: a wall of flame tongues round the ring, on the ground, two deep.
        int n = Math.min(minimal() ? 48 : 150, 24 + (int) (R * (minimal() ? 1.6F : 4.2F)));
        for (int i = 0; i < n; i++) {
            float a = i * TWO_PI / n + hash(seed, i + 100) * 0.08F;
            float r = R + (hash(seed, i + 130) - 0.5F) * OverloadGeometry.FRONT_DEPTH;
            double x = eye.x + cos(a) * r, z = eye.z + sin(a) * r;
            Vec3 base = new Vec3(x, ground(c, x, z, eye.y) + 0.05, z);
            float h = (1.4F + 3.4F * power) * (0.55F + 0.75F * hash(seed, i + 160)) * flare;
            float lean = 0.3F + 0.4F * power;
            Vec3 tip = base.add(cos(a) * lean, h, sin(a) * lean);
            PyroArt.emberFlame(c, base, tip, 0.4F + 0.32F * power, time * 0.3F + i * 2.3F, f * (0.7F + 0.3F * power));
        }
        if (c.glow()) {
            // Embers glowing along the foot of the front.
            float[][] fire = PyroArt.emberColors();
            Vec3 at = new Vec3(eye.x, ground(c, eye.x, eye.z, eye.y) + 0.1, eye.z);
            c.ring(at, UP, R, 0.9F, 0, TWO_PI, fire[0], f * 0.22F * flare);
            c.ring(at, UP, R, 0.3F, 0, TWO_PI, fire[2], f * 0.35F * flare);
        }
        if (c.soft()) {
            // Smoke boiling off the front: low, dark and close-packed, glowing from beneath, rising and thinning.
            CloudMesh.Shade low = Shades.smoke(c, SMOKE, 0.45F * power), high = Shades.smoke(c, mix(SMOKE, SMOKE_LIT, 0.35F), 0.1F);
            int puffs = minimal() ? 18 : detail() ? 72 : 44;
            for (int i = 0; i < puffs; i++) {
                float cycle = 110, u = ((time + hash(seed, i + 200) * cycle) % cycle) / cycle;
                float a = (i + hash(seed, i + 230)) * TWO_PI / puffs + u * 0.25F;
                float r = R + 0.5F + u * 2.5F;
                double x = eye.x + cos(a) * r, z = eye.z + sin(a) * r;
                float s = (1.8F + 3.6F * u) * (0.75F + 0.5F * power);
                float alpha = f * 0.78F * power * smooth(0, 0.15F, u) * (1 - smooth(0.6F, 1, u));
                c.cloud(new Vec3(x, ground(c, x, z, eye.y) + 1.5 + u * 12, z), s, s * 0.75F, s, i, a, alpha, u < 0.4F ? low : high);
            }
            // The column over them, leaning with the wind, that can be seen from far off.
            CloudMesh.Shade column = Shades.smoke(c, mix(SMOKE, SMOKE_LIT, 0.25F), 0);
            int stack = minimal() ? 8 : 22;
            for (int i = 0; i < stack; i++) {
                float u = i / (float) stack, drift = time * 0.015F + i * 1.7F;
                float s = (3 + 8 * u) * (0.6F + 0.5F * power) * (0.8F + 0.4F * hash(seed, i + 300));
                Vec3 at = eye.add(sin(drift) * (1 + u * 3) + u * u * 14, 6 + u * 52, cos(drift) * (1 + u * 3));
                c.cloud(at, s * 1.1F, s * 0.8F, s * 1.1F, i + 50, drift, f * 0.8F * power * (1 - u * 0.45F), column);
            }
        }
    }

    /** Ash drifting down round the camera while you are near the fire. */
    private static void ash(Canvas c, ClientOverloads.View v, Vec3 eye, float f) {
        if (minimal()) return;
        Vec3 cam = c.camera();
        double d = Math.sqrt(cam.subtract(eye).horizontalDistanceSqr());
        float near = 1 - smooth(v.reach() * 0.6F, v.reach() + 20, (float) d);
        if (near <= 0) return;
        float time = c.time();
        int n = detail() ? 70 : 40;
        for (int i = 0; i < n; i++) {
            float fall = 18;
            double ox = cam.x - 12 + ((hash(i, 1) * 24 + time * 0.03F * hash(i, 4)) % 24);
            double oz = cam.z - 12 + ((hash(i, 2) * 24 + time * 0.02F) % 24);
            double oy = cam.y + 10 - ((hash(i, 3) * fall + time * 0.06F) % fall);
            float s = 0.04F + 0.05F * hash(i, 5);
            c.box(new Vec3(ox, oy, oz), s, s * 0.3F, s, time * 0.1F + i, time * 0.07F + i, 0, ASH, near * f * v.power);
        }
    }

    // ------------------------------------------------------------------
    // The supercell
    // ------------------------------------------------------------------

    private static void supercell(Canvas c, ClientOverloads.View v, Vec3 eye, float f, long now) {
        float power = v.power, time = c.time();
        int seed = v.entity * 7;
        double groundY = ground(c, eye.x, eye.z, eye.y - OverloadGeometry.LIFT);
        float grow = Mth.clamp(v.age(now, c.partial) / 80F, 0, 1);
        // The deck hangs where the funnel flares into it, however tall the funnel has grown.
        float scale = v.scale(now, c.partial);
        double deckY = groundY + Math.max(OverloadGeometry.DECK_HEIGHT,
                OverloadGeometry.height(v.age(now, c.partial), scale) * 0.92F);
        if (c.soft()) {
            // The deck: a wide, slowly turning lid of dark cloud, lower and heavier near the funnel.
            CloudMesh.Shade deck = Shades.smoke(c, DECK, 0), lit = Shades.smoke(c, DECK_LIT, 0);
            int rings = minimal() ? 2 : 4;
            for (int ring = 0; ring < rings; ring++) {
                float rr = OverloadGeometry.DECK_RADIUS * (0.2F + 0.8F * (ring + 0.5F) / rings) * (0.6F + 0.4F * grow) * (0.8F + 0.2F * scale);
                int puffs = 10 + ring * 6;
                for (int i = 0; i < puffs; i++) {
                    float a = i * TWO_PI / puffs + time * (0.004F - ring * 0.0006F) + hash(seed + ring, i);
                    float s = (5 + 3 * hash(seed + ring, i + 20)) * (0.8F + 0.2F * ring);
                    double y = deckY - (rings - ring) * 1.2 + hash(seed + ring, i + 40) * 2;
                    c.cloud(new Vec3(eye.x + cos(a) * rr, y, eye.z + sin(a) * rr), s * 1.4F, s * 0.4F, s * 1.4F, ring * 31 + i, a,
                            f * grow * (0.45F + 0.25F * power), ring % 2 == 0 ? deck : lit);
                }
            }
            // Where it goes next: dust kicked up along the ground toward its next stop.
            if (v.growing()) {
                Vec3 to = v.target.subtract(eye).multiply(1, 0, 1);
                double len = Math.min(to.length(), 14);
                if (len > 1) {
                    Vec3 dir = to.normalize();
                    CloudMesh.Shade dust = Shades.smoke(c, DUST, 0);
                    int n = 7;
                    for (int i = 1; i <= n; i++) {
                        float u = i / (float) n, flow = (time * 0.04F + u) % 1;
                        Vec3 p = eye.add(dir.scale(len * u));
                        double y = ground(c, p.x, p.z, groundY);
                        float s = 0.6F + 0.5F * hash(seed, i + 70);
                        c.cloud(new Vec3(p.x, y + 0.3, p.z), s * 1.4F, s * 0.35F, s * 1.4F, i + 90, flow * TWO_PI, f * 0.32F * (1 - u * 0.6F), dust);
                    }
                }
            }
        }
        if (c.glow()) {
            // The still eye: a soft light round them.
            c.glow(eye.add(0, 1, 0), 1.4F, WIND, f * 0.18F);
            // Lightning in the deck now and then.
            int flick = (int) (time / 5);
            if (hash(seed, flick) < 0.18F + 0.2F * power) {
                float a0 = hash(seed, flick + 1) * TWO_PI, r0 = OverloadGeometry.DECK_RADIUS * 0.6F * hash(seed, flick + 2);
                float a1 = a0 + 0.6F + hash(seed, flick + 3), r1 = r0 + 6 + 10 * hash(seed, flick + 4);
                double y = deckY - 2;
                Vec3 p0 = new Vec3(eye.x + cos(a0) * r0, y, eye.z + sin(a0) * r0), p1 = new Vec3(eye.x + cos(a1) * r1, y - 1, eye.z + sin(a1) * r1);
                float k = 1 - (time % 5) / 5;
                c.bolt(p0, p1, seed + flick, 1.2F, 0.12F, FLASH, f * 0.9F * k);
                c.glow(p0.lerp(p1, 0.5), 8, FLASH, f * 0.12F * k);
            }
        }
    }

    // ------------------------------------------------------------------
    // The blizzard
    // ------------------------------------------------------------------

    private static void blizzard(Canvas c, ClientOverloads.View v, Vec3 eye, float f, long now) {
        float power = v.power, time = c.time();
        int seed = v.entity * 19;
        Vec3 cam = c.camera();
        double d = Math.sqrt(cam.subtract(eye).horizontalDistanceSqr());
        float R = v.reach();
        // In the whiteout its light shows only close by, unless you see them (a telepath, a sonic, a cryokinetic).
        float shown = v.sees ? 1 : 1 - smooth(9, 18, (float) d);
        // The spire of ice round them.
        float spire = OverloadGeometry.spire(v.progress) * (v.burning() || v.dying() ? Math.max(0.2F, power) : 1);
        if ((c.soft() || c.glow()) && shown > 0.01F) {
            int shards = minimal() ? 6 : 10;
            for (int i = 0; i < shards; i++) {
                float a = i * TWO_PI / shards + hash(seed, i) * 0.4F;
                float r = 0.9F + 0.5F * hash(seed, i + 10);
                float h = spire * (0.45F + 0.55F * hash(seed, i + 20));
                Vec3 base = eye.add(cos(a) * r, 0, sin(a) * r);
                Vec3 tip = eye.add(cos(a) * (r * 0.35F), h, sin(a) * (r * 0.35F));
                if (c.soft()) c.shard(base, tip, 0.32F + 0.12F * hash(seed, i + 30), 5, a, i % 3 == 0 ? MIST : ICE, f * shown * 0.62F);
                else c.line(base.lerp(tip, 0.1), tip, 0.03F, WHITE, f * shown * 0.6F, 0);
            }
            if (c.glow()) {
                c.glow(eye.add(0, spire * 0.5, 0), 1.2F + spire * 0.35F, ICE, f * shown * 0.3F);
                c.glow(eye.add(0, 1, 0), 0.8F, WHITE, f * shown * 0.5F);
                if (v.sees && d > 9) {
                    // Seen through the snow: their light, and rings of it going out from them like a heartbeat.
                    c.glow(eye.add(0, 1.2, 0), 2.2F, ICE, f * 0.5F);
                    for (int i = 0; i < 2; i++) {
                        float ping = (time * 0.025F + i * 0.5F) % 1;
                        c.ring(eye.add(0, 1.2, 0), UP, 1 + 7 * ping, 0.12F, 0, TWO_PI, WHITE, f * 0.8F * (1 - ping));
                    }
                }
            }
        }
        double groundY = ground(c, eye.x, eye.z, eye.y);
        if (c.glow()) {
            // Its edge: a frost ring on the ground.
            c.ring(new Vec3(eye.x, groundY + 0.07, eye.z), UP, R, 0.35F, 0, TWO_PI, ICE, f * 0.18F * power);
        }
        if (!c.soft()) return;
        // Seen from outside: a wall of driven snow round its edge, a low cap of snow cloud over it, and curtains of
        // snow falling between.
        CloudMesh.Shade snowCloud = Shades.smoke(c, WHITE, 0), capCloud = Shades.smoke(c, mix(ICE_SHADOW, MIST, 0.55F), 0);
        int wall = minimal() ? 18 : detail() ? 54 : 34;
        for (int i = 0; i < wall; i++) {
            float a = (i + hash(seed, i + 600)) * TWO_PI / wall - time * 0.012F;
            float r = R * (0.82F + 0.18F * hash(seed, i + 620));
            double x = eye.x + cos(a) * r, z = eye.z + sin(a) * r;
            float s = (2.2F + 2.6F * hash(seed, i + 640)) * (0.6F + 0.4F * power);
            c.cloud(new Vec3(x, ground(c, x, z, groundY) + s * 0.4 + hash(seed, i + 660) * 3, z), s * 1.3F, s * 0.8F, s * 1.3F,
                    i + 700, a, f * (0.45F + 0.3F * power), snowCloud);
        }
        int cap = minimal() ? 10 : 26;
        float capHeight = 16 + 6 * power;
        for (int i = 0; i < cap; i++) {
            float a = hash(seed, i + 800) * TWO_PI - time * 0.004F, r = R * 0.85F * (float) Math.sqrt(hash(seed, i + 820));
            float s = 4 + 3 * hash(seed, i + 840);
            c.cloud(new Vec3(eye.x + cos(a) * r, groundY + capHeight + hash(seed, i + 860) * 3, eye.z + sin(a) * r),
                    s * 1.5F, s * 0.45F, s * 1.5F, i + 900, a, f * 0.55F * power, capCloud);
        }
        int curtains = minimal() ? 30 : detail() ? 140 : 90;
        for (int i = 0; i < curtains; i++) {
            float a = hash(seed, i + 1000) * TWO_PI - time * 0.01F, r = R * 0.9F * (float) Math.sqrt(hash(seed, i + 1020));
            double x = eye.x + cos(a) * r, z = eye.z + sin(a) * r, y0 = ground(c, x, z, groundY);
            Vec3 top = new Vec3(x, y0 + capHeight, z), bottom = new Vec3(x + cos(a + 1.6F) * 4, y0, z + sin(a + 1.6F) * 4);
            c.line(top, bottom, 0.18F + 0.12F * hash(seed, i + 1040), SNOW, f * 0.04F * power, f * 0.16F * power);
        }
        // Snow swirling round the eye at the ground.
        int bands = minimal() ? 4 : 8;
        for (int k = 0; k < bands; k++) {
            int n = 18;
            Vec3[] pts = new Vec3[n + 1];
            float r0 = 2 + hash(seed, k + 40) * R * 0.5F, a0 = hash(seed, k + 50) * TWO_PI - time * 0.06F;
            for (int i = 0; i <= n; i++) {
                float u = i / (float) n, a = a0 + u * 1.8F, r = r0 * (1 - 0.3F * u);
                double x = eye.x + cos(a) * r, z = eye.z + sin(a) * r;
                pts[i] = new Vec3(x, ground(c, x, z, eye.y) + 0.4 + u * 1.6, z);
            }
            c.ribbon(pts, 0.6F + power * 0.6F, SNOW, 0, f * 0.35F * power);
        }
        // Snow driven sideways round the camera, while you are in it.
        if (d > R + 6) return;
        float inside = 1 - smooth(R * 0.8F, R + 6, (float) d);
        Vec3 radial = cam.subtract(eye).multiply(1, 0, 1);
        Vec3 out = radial.lengthSqr() < 1 ? new Vec3(1, 0, 0) : radial.normalize();
        Vec3 wind = new Vec3(-out.z, 0, out.x).scale(0.85).add(out.scale(-0.35)).normalize();
        int streaks = minimal() ? 40 : detail() ? 160 : 100;
        float box = 14;
        for (int i = 0; i < streaks; i++) {
            float speed = 0.5F + 0.4F * hash(i, 9);
            float along = (hash(i, 1) * box * 2 + time * speed) % (box * 2) - box;
            double sx = cam.x + wind.x * along + (hash(i, 2) - 0.5) * box * 2 * Math.abs(wind.z);
            double sz = cam.z + wind.z * along + (hash(i, 3) - 0.5) * box * 2 * Math.abs(wind.x);
            double sy = cam.y - 3 + hash(i, 4) * 9 - ((time * 0.08F * hash(i, 5)) % 3);
            Vec3 a = new Vec3(sx, sy, sz), b = a.add(wind.scale(0.5 + power * 0.9)).add(0, -0.15, 0);
            if (a.distanceToSqr(cam) < 0.8) continue;
            c.line(a, b, 0.025F + 0.02F * hash(i, 6), SNOW, f * inside * 0.55F, f * inside * 0.15F);
        }
    }

    // ------------------------------------------------------------------
    // The set pieces
    // ------------------------------------------------------------------

    static void draw(Canvas c, Active a, float t) {
        switch (a.piece()) {
            case OVERLOAD_EMBER -> ember(c, a, t);
            case OVERLOAD_FIRESTORM -> firestorm(c, a, t);
            case OVERLOAD_DEEP_FREEZE -> deepFreeze(c, a, t);
            default -> {}
        }
    }

    /**
     * An ember: it arcs from over them to where it will land while its ring fills on the ground ({@link
     * OverloadGeometry#EMBER_WARNING}), then a spot fire burns there for {@link OverloadGeometry#EMBER_BURN} and dies down.
     */
    private static void ember(Canvas c, Active a, float t) {
        if (c.solid()) return;
        Vec3 from = a.origin(), to = a.target();
        float warn = OverloadGeometry.EMBER_WARNING, burn = OverloadGeometry.EMBER_BURN, r = Math.max(0.5F, a.scale());
        float[][] fire = PyroArt.emberColors();
        int seed = a.seed();
        if (t < warn) {
            float k = t / warn, in = Mth.clamp(t / 3, 0, 1);
            Vec3 at = from.lerp(to, k).add(0, sin(k * PI) * (4 + from.distanceTo(to) * 0.15), 0);
            if (c.glow()) {
                c.glow(at, 0.5F, fire[1], 0.8F * in);
                c.glow(at, 0.22F, fire[2], in);
                c.ring(to, UP, r, 0.08F, 0, TWO_PI, fire[1], 0.35F * in);
                c.ring(to, UP, r, 0.16F, -PI / 2, -PI / 2 + TWO_PI * k, fire[2], 0.85F * in);
                c.glow(to.add(0, 0.1, 0), r * 0.8F, fire[0], 0.2F * k);
            } else {
                // A short trail of flame behind it.
                Vec3 back = from.lerp(to, Math.max(0, k - 0.06F)).add(0, sin(Math.max(0, k - 0.06F) * PI) * (4 + from.distanceTo(to) * 0.15), 0);
                PyroArt.emberFlame(c, at, back, 0.22F, t * 0.6F, in);
            }
            return;
        }
        float b = t - warn, f = Mth.clamp(b / 3, 0, 1) * Mth.clamp((burn - b) / 20, 0, 1);
        if (f <= 0) return;
        for (int i = 0; i < 6; i++) {
            float ang = i * TWO_PI / 6 + hash(seed, i);
            float rr = r * 0.7F * hash(seed, i + 10);
            Vec3 base = to.add(cos(ang) * rr, 0.02, sin(ang) * rr);
            Vec3 tip = base.add(0, 0.9 + 1.2 * hash(seed, i + 20), 0);
            PyroArt.emberFlame(c, base, tip, 0.26F, t * 0.35F + i * 1.9F, f);
        }
        if (c.glow()) c.glow(to.add(0, 0.5, 0), r * 1.2F, fire[1], f * 0.3F);
    }

    /** The Firestorm: a wall of fire rushing out to its reach, then dying down. */
    private static void firestorm(Canvas c, Active a, float t) {
        if (c.solid()) return;
        Vec3 o = a.origin();
        float reach = a.scale(), life = a.piece().ticks;
        float k = 1 - (float) Math.pow(1 - Mth.clamp(t / 14, 0, 1), 3), f = Mth.clamp((life - t) / 16, 0, 1);
        float R = 2 + (reach - 2) * k;
        int seed = a.seed(), n = minimal() ? 40 : 90;
        for (int i = 0; i < n; i++) {
            float ang = i * TWO_PI / n + hash(seed, i) * 0.05F;
            double x = o.x + cos(ang) * R, z = o.z + sin(ang) * R;
            Vec3 base = new Vec3(x, ground(c, x, z, o.y) + 0.05, z);
            Vec3 tip = base.add(cos(ang) * 0.8, 3 + 3 * hash(seed, i + 40), sin(ang) * 0.8);
            PyroArt.emberFlame(c, base, tip, 0.5F, t * 0.4F + i * 1.3F, f);
        }
        if (c.glow()) {
            float[][] fire = PyroArt.emberColors();
            c.ring(o.add(0, 0.2, 0), UP, R, 1.4F, 0, TWO_PI, fire[1], f * 0.3F);
            c.ring(o.add(0, 0.2, 0), UP, R, 0.4F, 0, TWO_PI, fire[2], f * 0.6F);
            c.glow(o.add(0, 2, 0), 6 + R * 0.4F, fire[0], f * 0.25F * (1 - k * 0.5F));
        }
    }

    /** The Deep Freeze: a white wave rolling out to its reach, frost thrown up along its edge. */
    private static void deepFreeze(Canvas c, Active a, float t) {
        if (c.solid()) return;
        Vec3 o = a.origin();
        float reach = a.scale(), life = a.piece().ticks;
        float k = 1 - (float) Math.pow(1 - Mth.clamp(t / 18, 0, 1), 3), f = Mth.clamp((life - t) / 18, 0, 1);
        float R = 1 + (reach - 1) * k;
        Vec3 at = o.add(0, 0.1, 0);
        if (c.glow()) {
            c.ring(at, UP, R, 1.6F, 0, TWO_PI, ICE, f * 0.35F);
            c.ring(at, UP, R, 0.4F, 0, TWO_PI, WHITE, f * 0.8F);
            c.glow(o.add(0, 1.5, 0), 4 + R * 0.25F, MIST, f * 0.3F * (1 - k * 0.6F));
            return;
        }
        int seed = a.seed(), n = minimal() ? 24 : 60;
        for (int i = 0; i < n; i++) {
            float ang = i * TWO_PI / n + hash(seed, i) * 0.1F;
            double x = o.x + cos(ang) * R, z = o.z + sin(ang) * R;
            Vec3 base = new Vec3(x, ground(c, x, z, o.y), z);
            Vec3 tip = base.add(cos(ang) * 0.6, 0.8 + 1.6 * hash(seed, i + 30), sin(ang) * 0.6);
            c.shard(base, tip, 0.22F, 4, ang, i % 2 == 0 ? ICE : ICE_SHADOW, f * 0.75F);
        }
    }
}
