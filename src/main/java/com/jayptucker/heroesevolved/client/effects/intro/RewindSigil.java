package com.jayptucker.heroesevolved.client.effects.intro;

import com.jayptucker.heroesevolved.time.intro.IntroBlast;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The power at work. While time holds and runs back at the end of the vision, whoever has Time
 * Manipulation stands on a clock of light: the gold-and-violet clockwork their power draws whenever
 * they use it. A dial opens under their feet with its hands at 6:14, inside a turning bezel and
 * outer arcs; a thread of light rises from each hour mark; bands circle their body. While time holds,
 * so does the clock, but for its second hand straining to tick. When time runs back the clock runs
 * back with it, driven by the scene's own clock: hands, bezel and bands all turn backwards, rings of
 * light close in on them from outside like the shock wave going home, and motes stream into them. At
 * the end the whole clock draws into them, just before they are taken home. Everyone in the vision
 * sees it on whoever's power it is.
 */
final class RewindSigil {
    /** The dial's radius, in blocks. */
    private static final float RADIUS = 3.0F;
    /** Ticks for the dial to snap open, and for it to draw in at the end of the rewind. */
    private static final float OPEN = 8, CLOSE = 12;
    /** Clock time wound back per tick of the scene rewound: the minute hand turns about four times. */
    private static final float SECONDS_PER_TICK = 130;
    /** Motes of light hanging around them, then streaming in. */
    private static final int MOTES = 36;
    private static final float[] GOLD = rgb(0xFFD27A), VIOLET = rgb(0x9A7CFF), DEEP = rgb(0x5B3FD1), PALE = rgb(0xF4EBFF);

    /** The entity ids of the visitors whose power it is. */
    private static List<Integer> holders = List.of();
    /** Which way each faced when time stopped: twelve o'clock is straight ahead of them. */
    private static final Map<Integer, Float> FACING = new HashMap<>();

    private RewindSigil() {}

    static void begin(List<Integer> players) {
        holders = List.copyOf(players);
        FACING.clear();
    }

    static void reset() {
        holders = List.of();
        FACING.clear();
    }

    /** How far through the ending this frame is. */
    private record Phase(float since, float alpha, float open, float close, float back, boolean rewinding) {
        static Phase at(float partial) {
            // In whole ticks first, so an old world's large game time loses no precision.
            var level = Minecraft.getInstance().level;
            float since = level == null ? 0 : (level.getGameTime() - IntroScene.freeze) + partial;
            float end = IntroBlast.FREEZE_TICKS + IntroBlast.REWIND_TICKS;
            float alpha = Mth.clamp(since / 2, 0, 1) * Mth.clamp(1 - (since - end) / 4, 0, 1);
            float open = backOut(Mth.clamp(since / OPEN, 0, 1));
            float close = Mth.clamp((since - (end - CLOSE)) / CLOSE, 0, 1);
            // How many ticks of the scene have been wound back so far: what turns everything.
            float back = Math.max(0, IntroScene.frozenAt - IntroScene.t(partial));
            return new Phase(since, alpha, open, close, back, since >= IntroBlast.FREEZE_TICKS);
        }
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    static void render(Vec3 camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (holders.isEmpty() || !IntroScene.stopped() || mc.level == null) return;
        Phase phase = Phase.at(partial);
        if (phase.alpha() <= 0.002F) return;
        Matrix4f matrix = new Matrix4f();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        try {
            BufferBuilder out = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            for (int id : holders) {
                Entity holder = mc.level.getEntity(id);
                if (holder == null || holder.distanceToSqr(camera) > 96 * 96) continue;
                Vec3 feet = holder.getPosition(partial);
                float yaw = FACING.computeIfAbsent(id, k -> holder.getYRot());
                double ground = ground(mc, holder, feet);
                boolean inside = holder == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
                new Dial(out, matrix, camera, feet.x, ground, feet.z, yaw).draw(phase, inside, id);
            }
            MeshData mesh = out.build();
            if (mesh != null) BufferUploader.drawWithShader(mesh);
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    /** The ground under someone who may have been caught mid-jump when time stopped. */
    private static double ground(Minecraft mc, Entity holder, Vec3 feet) {
        Vec3 from = feet.add(0, 0.05, 0);
        var hit = mc.level.clip(new ClipContext(from, from.subtract(0, 6, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, holder));
        return hit.getType() == HitResult.Type.MISS ? feet.y : hit.getLocation().y;
    }

    /** One holder's clock: everything is laid out on the ground from where they stand, twelve o'clock ahead. */
    private static final class Dial {
        private final BufferBuilder out;
        private final Matrix4f m;
        private final Vec3 cam;
        /** The centre, relative to the camera, a hair above the ground. */
        private final float x, y, z;
        /** Ahead and to the right of the holder: the clock's twelve and three. */
        private final float fx, fz, rx, rz;

        Dial(BufferBuilder out, Matrix4f m, Vec3 cam, double x, double ground, double z, float yaw) {
            this.out = out;
            this.m = m;
            this.cam = cam;
            this.x = (float) (x - cam.x);
            this.y = (float) (ground + 0.035 - cam.y);
            this.z = (float) (z - cam.z);
            float rad = yaw * Mth.DEG_TO_RAD;
            fx = -Mth.sin(rad); fz = Mth.cos(rad);
            rx = -Mth.cos(rad); rz = -Mth.sin(rad);
        }

        void draw(Phase p, boolean inside, int seed) {
            float a = p.alpha();
            float shrink = 1 - 0.85F * p.close() * p.close();
            float r = RADIUS * Math.max(0.05F, p.open()) * shrink;
            float spin = p.back();
            // Seen from inside, close up and looking down, the soft glows would wash everything violet.
            float haze = inside ? 0.5F : 1;

            // A pool of violet light under them.
            disc(r * 1.05F, DEEP, 0.22F * haze * a);
            disc(r * 0.4F, VIOLET, 0.14F * haze * a);

            // The rim of the dial and its glow.
            arc(r, 0.07F, 0, Mth.TWO_PI, GOLD, 0.95F * a);
            arc(r, 0.34F, 0, Mth.TWO_PI, VIOLET, 0.28F * haze * a);
            arc(r * 0.86F, 0.035F, 0, Mth.TWO_PI, GOLD, 0.7F * a);
            // Twelve hour marks, every third longer.
            for (int i = 0; i < 12; i++) {
                boolean major = i % 3 == 0;
                float at = i * Mth.TWO_PI / 12, w = major ? 0.075F : 0.045F;
                bar(at, r * (major ? 0.64F : 0.74F), r * 0.84F, w, w, major ? PALE : GOLD, 0.95F * a);
            }

            // The bezel: sixty minute marks between two rings, turning back with the scene.
            float bezel = -spin * 0.045F;
            arc(r * 1.07F, 0.03F, 0, Mth.TWO_PI, VIOLET, 0.6F * a);
            arc(r * 1.25F, 0.03F, 0, Mth.TWO_PI, VIOLET, 0.6F * a);
            for (int i = 0; i < 60; i++) {
                boolean five = i % 5 == 0;
                bar(bezel + i * Mth.TWO_PI / 60, r * (five ? 1.09F : 1.13F), r * (five ? 1.23F : 1.19F),
                        five ? 0.04F : 0.022F, five ? 0.04F : 0.022F, PALE, (five ? 0.85F : 0.5F) * a);
            }
            // Three long arcs outside it, turning back faster.
            float arcs = -spin * 0.09F;
            for (int k = 0; k < 3; k++) {
                float from = arcs + k * Mth.TWO_PI / 3;
                arc(r * 1.45F, 0.05F, from, from + 1.25F, GOLD, 0.75F * a);
                arc(r * 1.45F, 0.26F, from, from + 1.25F, VIOLET, 0.2F * haze * a);
            }
            // A small gear at the centre, turning the other way.
            float gear = spin * 0.08F;
            arc(r * 0.3F, 0.03F, 0, Mth.TWO_PI, PALE, 0.6F * a);
            for (int i = 0; i < 12; i++) bar(gear + i * Mth.TWO_PI / 12, r * 0.22F, r * 0.3F, 0.03F, 0.03F, GOLD, 0.7F * a);

            // The hands, at 6:14 when time stopped, then running back. The second hand strains to tick while time holds.
            float hours = 6 + 14 / 60F - spin * SECONDS_PER_TICK / 3600;
            float second;
            if (p.rewinding()) second = -spin * 0.55F;
            else {
                float beat = (p.since() % 20) / 20;
                second = beat < 0.25F ? Mth.sin(beat / 0.25F * Mth.PI) * 0.08F : 0;
            }
            hand(Mth.TWO_PI * hours / 12, r * 0.46F, r * 0.08F, 0.13F, GOLD, a);
            hand(Mth.TWO_PI * hours, r * 0.72F, r * 0.1F, 0.09F, GOLD, a);
            hand(second, r * 0.8F, r * 0.18F, 0.035F, PALE, a);
            disc(0.2F, PALE, 0.9F * a);

            // Snapping open: a ring of light thrown out from them.
            if (p.since() < 10) {
                float k = p.since() / 10;
                arc(RADIUS * 1.7F * (1 - (1 - k) * (1 - k)), 0.14F, 0, Mth.TWO_PI, PALE, 0.9F * (1 - k) * Mth.clamp(p.since() / 1.5F, 0, 1));
            }

            // A thread of light rising from every hour; while time runs back its bands run down.
            float rise = Mth.clamp((p.since() - 2) / 10, 0, 1) * shrink;
            float bands = spin * 0.09F;
            for (int i = 0; i < 12; i++) {
                boolean major = i % 3 == 0;
                float at = i * Mth.TWO_PI / 12;
                pillar(px(at, r * 0.86F), pz(at, r * 0.86F), (major ? 1.7F : 1.1F) * rise, major ? 0.07F : 0.05F,
                        major ? PALE : GOLD, 0.8F * a, bands + i * 0.37F);
            }

            // Bands around their body: one at their feet, broken arcs at the waist, and, seen from outside,
            // a tilted ring precessing round them like a gyroscope.
            float body = -spin * 0.12F, fold = 1 - 0.5F * p.close();
            // From inside, only the band at their feet: the others pass just under the camera and would smear across the view.
            hoop(0.12F, 0.95F * fold, 0, Mth.TWO_PI, 0, 0, 0.06F, GOLD, 0.8F * a);
            if (!inside) {
                for (int k = 0; k < 4; k++) {
                    float from = body + k * Mth.HALF_PI;
                    hoop(0.85F, 0.8F * fold, from, from + 1.05F, 0, 0, 0.05F, PALE, 0.75F * a);
                }
                float precess = seed * 1.7F - spin * 0.05F + p.since() * 0.01F;
                for (int k = 0; k < 3; k++) {
                    float from = body * 1.3F + k * Mth.TWO_PI / 3;
                    hoop(1.2F, 1.05F * fold, from, from + 1.6F, 0.45F, precess, 0.045F, GOLD, 0.7F * a);
                }
            }

            // Running back: rings of light close in on them from outside, the wave going home.
            if (p.rewinding()) {
                float strength = Mth.clamp(spin / 6, 0, 1) * (1 - p.close());
                for (int k = 0; k < 3; k++) {
                    float frac = fract(spin / 16 + k / 3F);
                    float rr = RADIUS * 1.35F + (1 - frac) * RADIUS * 2.6F;
                    float ra = a * frac * frac * 0.75F * strength;
                    arc(rr, 0.12F, 0, Mth.TWO_PI, VIOLET, ra);
                    arc(rr, 0.035F, 0, Mth.TWO_PI, PALE, ra);
                }
            }

            motes(p, a, spin);

            // The last moments: everything draws in to a column of light where they stand.
            if (p.close() > 0 && !inside) pillar(0, 0, 2.6F, 0.5F * p.close(), PALE, p.close() * a, 0);
        }

        /**
         * Motes of light around them. While time holds they hang perfectly still; as it runs back each one
         * spirals in to their chest, trailing a short streak, and is replaced from outside.
         */
        private void motes(Phase p, float a, float spin) {
            float appear = Mth.clamp((p.since() - 3) / 10, 0, 1) * (1 - p.close());
            if (appear <= 0) return;
            for (int i = 0; i < MOTES; i++) {
                float start = fract(i * 0.618034F), angle0 = i * 2.39996F, h0 = 0.25F + fract(i * 0.4142F) * 2.1F;
                float u = fract(start + spin / 14);
                float alpha = a * appear * Mth.clamp((1 - u) * 6, 0, 1) * Mth.clamp(u * 5 + 0.25F, 0, 1);
                if (alpha <= 0.01F) continue;
                float[] head = mote(u, angle0, h0), tail = mote(Math.max(0, u - 0.06F), angle0, h0);
                if (p.rewinding() && spin > 0.5F)
                    ribbon(new float[] {tail[0], head[0]}, new float[] {tail[1], head[1]}, new float[] {tail[2], head[2]},
                            new float[] {0, 0.6F * alpha}, 0.025F, GOLD);
                glint(head[0], head[1], head[2], 0.15F, GOLD, 0.3F * alpha);
                glint(head[0], head[1], head[2], 0.08F, PALE, alpha);
            }
        }

        /** Where a mote is, {@code u} of the way in from the outside: it spirals backwards and rises or sinks to their chest. */
        private float[] mote(float u, float angle0, float h0) {
            float radius = Mth.lerp(u, RADIUS * 1.25F, 0.2F), angle = angle0 - u * 1.6F, h = Mth.lerp(u * u, h0, 1.1F);
            return new float[] {px(angle, radius), y + h, pz(angle, radius)};
        }

        /** A small soft spark facing the camera. */
        private void glint(float gx, float gy, float gz, float size, float[] c, float alpha) {
            float l = Mth.sqrt(gx * gx + gy * gy + gz * gz) + 1e-4F;
            float dx = gx / l, dy = gy / l, dz = gz / l;
            float ux = 0, uy = 1, uz = 0;
            if (Math.abs(dy) > 0.95F) { ux = 1; uy = 0; }
            float ax = uy * dz - uz * dy, ay = uz * dx - ux * dz, az = ux * dy - uy * dx;
            float al = Mth.sqrt(ax * ax + ay * ay + az * az);
            ax /= al; ay /= al; az /= al;
            float bx = dy * az - dz * ay, by = dz * ax - dx * az, bz = dx * ay - dy * ax;
            int sides = 8;
            for (int i = 0; i < sides; i++) {
                float a0 = i * Mth.TWO_PI / sides, a1 = (i + 1) * Mth.TWO_PI / sides;
                vertex(gx, gy, gz, c, alpha);
                vertex(gx + (ax * Mth.cos(a0) + bx * Mth.sin(a0)) * size, gy + (ay * Mth.cos(a0) + by * Mth.sin(a0)) * size,
                        gz + (az * Mth.cos(a0) + bz * Mth.sin(a0)) * size, c, 0);
                vertex(gx + (ax * Mth.cos(a1) + bx * Mth.sin(a1)) * size, gy + (ay * Mth.cos(a1) + by * Mth.sin(a1)) * size,
                        gz + (az * Mth.cos(a1) + bz * Mth.sin(a1)) * size, c, 0);
            }
        }

        // ---- the clock's plane

        private float px(float angle, float radius) { return x + (fx * Mth.cos(angle) + rx * Mth.sin(angle)) * radius; }
        private float pz(float angle, float radius) { return z + (fz * Mth.cos(angle) + rz * Mth.sin(angle)) * radius; }

        /** A soft band of light along a circle on the ground, from one clock angle to another. */
        void arc(float radius, float width, float from, float to, float[] c, float alpha) {
            if (alpha <= 0.003F || radius <= 0) return;
            int n = Math.max(4, (int) Math.ceil(Math.abs(to - from) / Mth.TWO_PI * 72));
            for (int i = 0; i < n; i++) {
                float a0 = from + (to - from) * i / n, a1 = from + (to - from) * (i + 1) / n;
                float in = Math.max(0, radius - width), outR = radius + width;
                quad(px(a0, in), y, pz(a0, in), 0, px(a0, radius), y, pz(a0, radius), alpha,
                        px(a1, radius), y, pz(a1, radius), alpha, px(a1, in), y, pz(a1, in), 0, c);
                quad(px(a0, radius), y, pz(a0, radius), alpha, px(a0, outR), y, pz(a0, outR), 0,
                        px(a1, outR), y, pz(a1, outR), 0, px(a1, radius), y, pz(a1, radius), alpha, c);
            }
        }

        /** A soft radial bar on the ground along one clock angle, {@code w0} wide at {@code r0} and {@code w1} at {@code r1}. */
        void bar(float angle, float r0, float r1, float w0, float w1, float[] c, float alpha) {
            if (alpha <= 0.003F) return;
            float tx = -fx * Mth.sin(angle) + rx * Mth.cos(angle), tz = -fz * Mth.sin(angle) + rz * Mth.cos(angle);
            float x0 = px(angle, r0), z0 = pz(angle, r0), x1 = px(angle, r1), z1 = pz(angle, r1);
            quad(x0 - tx * w0, y, z0 - tz * w0, 0, x0, y, z0, alpha, x1, y, z1, alpha, x1 - tx * w1, y, z1 - tz * w1, 0, c);
            quad(x0, y, z0, alpha, x0 + tx * w0, y, z0 + tz * w0, 0, x1 + tx * w1, y, z1 + tz * w1, 0, x1, y, z1, alpha, c);
        }

        /** A clock hand: a tapering bar from behind the pivot to its tip, in a violet glow. */
        void hand(float angle, float length, float tail, float width, float[] c, float alpha) {
            bar(angle, -tail, length, width * 3, width * 0.6F, VIOLET, 0.3F * alpha);
            bar(angle, -tail, length, width, width * 0.25F, c, alpha);
        }

        /** A pool of light on the ground, brightest in the middle. */
        void disc(float radius, float[] c, float alpha) {
            if (alpha <= 0.003F) return;
            int n = 32;
            for (int i = 0; i < n; i++) {
                float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
                vertex(x, y, z, c, alpha);
                vertex(px(a0, radius), y, pz(a0, radius), c, 0);
                vertex(px(a1, radius), y, pz(a1, radius), c, 0);
            }
        }

        // ---- standing up from it

        /** A thread of light rising from the ground, facing the camera, fading upward, its bands moving with {@code bands}. */
        void pillar(float bx, float bz, float height, float width, float[] c, float alpha, float bands) {
            if (alpha <= 0.003F || height <= 0.02F) return;
            int n = 10;
            float[] px = new float[n + 1], py = new float[n + 1], pz = new float[n + 1], pa = new float[n + 1];
            for (int i = 0; i <= n; i++) {
                float k = i / (float) n;
                px[i] = bx; py[i] = y + height * k; pz[i] = bz;
                pa[i] = alpha * (1 - k) * (0.55F + 0.45F * Mth.sin(Mth.TWO_PI * (k * height * 1.3F + bands)));
            }
            ribbon(px, py, pz, pa, width, c);
        }

        /**
         * Part of a ring around the holder at {@code height}, from one angle to another, as a ribbon facing the
         * camera; {@code tilt} leans the ring over, towards the clock angle {@code toward}.
         */
        void hoop(float height, float radius, float from, float to, float tilt, float toward, float width, float[] c, float alpha) {
            if (alpha <= 0.003F || radius <= 0) return;
            int n = Math.max(4, (int) Math.ceil(Math.abs(to - from) / Mth.TWO_PI * 48));
            float ux = fx * Mth.cos(toward) + rx * Mth.sin(toward), uz = fz * Mth.cos(toward) + rz * Mth.sin(toward);
            float[] px = new float[n + 1], py = new float[n + 1], pz = new float[n + 1], pa = new float[n + 1];
            for (int i = 0; i <= n; i++) {
                float angle = from + (to - from) * i / n;
                float ox = (fx * Mth.cos(angle) + rx * Mth.sin(angle)) * radius, oz = (fz * Mth.cos(angle) + rz * Mth.sin(angle)) * radius;
                // Leaning: turned about the level line across {@code toward}, its near side lifts and its far side drops.
                float s = ox * ux + oz * uz, pull = s * (1 - Mth.cos(tilt));
                px[i] = x + ox - ux * pull; py[i] = y + height + s * Mth.sin(tilt); pz[i] = z + oz - uz * pull;
                // The ends of an arc fade, so broken rings read as pieces of clockwork.
                float k = i / (float) n;
                pa[i] = alpha * (to - from >= Mth.TWO_PI - 1e-3F ? 1 : Mth.clamp(Math.min(k, 1 - k) * 6, 0, 1));
            }
            ribbon(px, py, pz, pa, width, c);
        }

        /** A soft band along a line through these points, turned to face the camera. */
        void ribbon(float[] px, float[] py, float[] pz, float[] pa, float width, float[] c) {
            int n = px.length;
            float[] sx = new float[n], sy = new float[n], sz = new float[n];
            for (int i = 0; i < n; i++) {
                int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
                float tx = px[b] - px[a], ty = py[b] - py[a], tz = pz[b] - pz[a];
                // The points are already relative to the camera, so each is also the line of sight to it.
                float vx = px[i], vy = py[i], vz = pz[i];
                float cx = ty * vz - tz * vy, cy = tz * vx - tx * vz, cz = tx * vy - ty * vx;
                float l = Mth.sqrt(cx * cx + cy * cy + cz * cz);
                if (l < 1e-5F) { cx = 1; cy = 0; cz = 0; l = 1; }
                sx[i] = cx / l * width; sy[i] = cy / l * width; sz[i] = cz / l * width;
            }
            for (int i = 0; i + 1 < n; i++) {
                int j = i + 1;
                quad(px[i] - sx[i], py[i] - sy[i], pz[i] - sz[i], 0, px[i], py[i], pz[i], pa[i],
                        px[j], py[j], pz[j], pa[j], px[j] - sx[j], py[j] - sy[j], pz[j] - sz[j], 0, c);
                quad(px[i], py[i], pz[i], pa[i], px[i] + sx[i], py[i] + sy[i], pz[i] + sz[i], 0,
                        px[j] + sx[j], py[j] + sy[j], pz[j] + sz[j], 0, px[j], py[j], pz[j], pa[j], c);
            }
        }

        private void quad(float x0, float y0, float z0, float a0, float x1, float y1, float z1, float a1,
                          float x2, float y2, float z2, float a2, float x3, float y3, float z3, float a3, float[] c) {
            vertex(x0, y0, z0, c, a0); vertex(x1, y1, z1, c, a1); vertex(x2, y2, z2, c, a2);
            vertex(x0, y0, z0, c, a0); vertex(x2, y2, z2, c, a2); vertex(x3, y3, z3, c, a3);
        }

        private void vertex(float vx, float vy, float vz, float[] c, float alpha) {
            out.addVertex(m, vx, vy, vz).setColor(c[0], c[1], c[2], Math.min(1, alpha));
        }
    }

    // ------------------------------------------------------------------

    private static float[] rgb(int color) {
        return new float[] {(color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F};
    }

    private static float fract(float v) { return v - (float) Math.floor(v); }

    /** Ease out with a small overshoot, so the dial snaps open. */
    private static float backOut(float k) {
        float c1 = 1.70158F, c3 = c1 + 1;
        float u = k - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }
}
