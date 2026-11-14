package com.jayptucker.heroesevolved.client.effects.intro;

import com.jayptucker.heroesevolved.time.intro.IntroBlast;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Ted Sprague's light, the fireball, the mushroom cloud, the wall of dust and the burning sky, drawn
 * as real geometry: lumpy spheres lit by the low western sun, the sky and their own heat, fogged by
 * distance like the city around them. Positions come from {@link IntroBlast}, so the wall is where
 * the server says it is.
 */
final class BlastRenderer {
    private static final ImprovedNoise NOISE = new ImprovedNoise(RandomSource.create(614L));
    /** Toward the sun: low in the west, a little south. */
    private static final float LX, LY, LZ;
    static {
        float x = -0.80F, y = 0.42F, z = 0.30F, l = Mth.sqrt(x * x + y * y + z * z);
        LX = x / l; LY = y / l; LZ = z / l;
    }
    private static final float CX = (float) IntroBlast.CENTER.x, CY = (float) IntroBlast.CENTER.y, CZ = (float) IntroBlast.CENTER.z;

    private BlastRenderer() {}

    // ------------------------------------------------------------------
    // Light and colour
    // ------------------------------------------------------------------

    /** Glowing colour for a temperature from 0 (cold) to 1 (white hot). */
    static void heat(float h, float[] out) {
        if (h > 0.75F) { float k = (h - 0.75F) / 0.25F; set(out, 1, Mth.lerp(k, 0.82F, 0.98F), Mth.lerp(k, 0.42F, 0.9F)); }
        else if (h > 0.45F) { float k = (h - 0.45F) / 0.3F; set(out, 1, Mth.lerp(k, 0.45F, 0.82F), Mth.lerp(k, 0.1F, 0.42F)); }
        else if (h > 0.15F) { float k = (h - 0.15F) / 0.3F; set(out, Mth.lerp(k, 0.55F, 1), Mth.lerp(k, 0.1F, 0.45F), Mth.lerp(k, 0.03F, 0.1F)); }
        else { float k = h / 0.15F; set(out, 0.55F * k, 0.1F * k, 0.03F * k); }
    }

    private static void set(float[] out, float r, float g, float b) { out[0] = r; out[1] = g; out[2] = b; }

    /** The haze everything far away fades into, now. */
    static void fog(float t, float[] out) {
        if (t < 0) { set(out, 0.62F, 0.47F, 0.39F); return; }
        if (t < 5) { float k = t / 5; set(out, Mth.lerp(k, 1, 0.98F), Mth.lerp(k, 0.98F, 0.8F), Mth.lerp(k, 0.94F, 0.52F)); return; }
        if (t < 40) { float k = (t - 5) / 35; set(out, Mth.lerp(k, 0.98F, 0.6F), Mth.lerp(k, 0.8F, 0.44F), Mth.lerp(k, 0.52F, 0.34F)); return; }
        float k = Mth.clamp((t - 40) / 80, 0, 1);
        set(out, Mth.lerp(k, 0.6F, 0.44F), Mth.lerp(k, 0.44F, 0.35F), Mth.lerp(k, 0.34F, 0.3F));
    }

    // ------------------------------------------------------------------
    // The air: one fog for the city and for everything drawn here
    // ------------------------------------------------------------------

    /** This frame's fog: where it starts and ends, and its colour. Thickens to dust as the wall comes. */
    static float fogStart = 120, fogEnd = 200, dusty;
    static final float[] FOG = new float[3];

    /** How far the wall still is from someone this far from ground zero (negative once it has passed). */
    static double gap(float t, double distance) { return t <= 0 ? Double.MAX_VALUE : distance - IntroBlast.front(t); }

    /** Works out this frame's fog for a camera {@code distance} from ground zero. */
    static void atmosphere(float t, double distance, float vanillaEnd) {
        fog(t, FOG);
        if (IntroScene.prologue) {
            // The north burns white as he brightens, and the haze flares with every surge.
            float g = glowStrength(t);
            FOG[0] = Mth.lerp(g * 0.5F, FOG[0], 1.0F);
            FOG[1] = Mth.lerp(g * 0.5F, FOG[1], 0.86F);
            FOG[2] = Mth.lerp(g * 0.5F, FOG[2], 0.62F);
        }
        float end = Math.min(200, vanillaEnd);
        float start = end * 0.55F;
        double gap = gap(t, distance);
        if (gap < 90) {
            // The air ahead of the wall is already full of dust.
            float near = (float) Mth.clamp(1 - gap / 90, 0, 1);
            end = Math.min(end, (float) (16 + Math.max(gap, 0) * 0.95));
            start = Mth.lerp(near, start, 2);
            FOG[0] = Mth.lerp(near * 0.85F, FOG[0], 0.42F);
            FOG[1] = Mth.lerp(near * 0.85F, FOG[1], 0.33F);
            FOG[2] = Mth.lerp(near * 0.85F, FOG[2], 0.26F);
        }
        dusty = gap < 90 ? (float) Mth.clamp(1 - gap / 90, 0, 1) : 0;
        fogStart = start;
        fogEnd = Math.max(end, start + 1);
    }

    private static final class Light implements CloudMesh.Shade {
        float t, heat, dust, tint = 1, camX, camY, camZ;
        final float[] fog = new float[3], glow = new float[3];
        boolean wall;

        @Override public void color(float nx, float ny, float nz, float bump, float wx, float wy, float wz, float[] rgb) {
            float dx = wx - camX, dy = wy - camY, dz = wz - camZ;
            float distance = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            float mottle = 1;
            if (wall && distance < 70) mottle = 0.8F + 0.36F * (float) (NOISE.noise(wx * 0.32, wy * 0.32, wz * 0.32) * 0.5 + 0.5);
            float ar = Mth.lerp(dust, 0.56F, 0.44F) * tint * mottle, ag = Mth.lerp(dust, 0.50F, 0.38F) * tint * mottle,
                    ab = Mth.lerp(dust, 0.46F, 0.33F) * tint * mottle;
            float sun = Math.max(0, (nx * LX + ny * LY + nz * LZ + 0.2F) / 1.2F);
            float sky = 0.24F + 0.2F * ny;
            float cavity = Mth.clamp(0.78F + bump * 1.1F, 0.42F, 1.18F);
            float lr = (0.34F * sky + 1.1F * sun) * cavity, lg = (0.32F * sky + 0.76F * sun) * cavity, lb = (0.38F * sky + 0.5F * sun) * cavity;
            // Lit from below by the fire for a long while after the flash.
            float under = Math.max(0, -ny) * 0.8F + 0.08F;
            float fire = (float) Math.exp(-t / 90) * under * (wall ? 0.28F : 1);
            float h = Mth.clamp(heat * (0.7F + 0.55F * Math.max(0, -ny) + bump * 0.9F), 0, 1);
            heat(h, glow);
            float r = ar * lr + fire * 0.95F, g = ag * lg + fire * 0.42F, b = ab * lb + fire * 0.12F;
            r = r * (1 - h * 0.85F) + glow[0] * h * 1.3F;
            g = g * (1 - h * 0.85F) + glow[1] * h * 1.3F;
            b = b * (1 - h * 0.85F) + glow[2] * h * 1.3F;
            if (t < 6) { float k = 1 - t / 6; r = Mth.lerp(k, r, 1); g = Mth.lerp(k, g, 0.97F); b = Mth.lerp(k, b, 0.9F); }
            // Far things fade as the city does; near ones too, once the air is full of dust.
            float haze = Math.max(Mth.clamp((distance - 60) / 460, 0, 0.55F), Mth.clamp((distance - fogStart) / (fogEnd - fogStart), 0, 0.97F) * dusty);
            rgb[0] = Mth.clamp(Mth.lerp(haze, r, fog[0]), 0, 1);
            rgb[1] = Mth.clamp(Mth.lerp(haze, g, fog[1]), 0, 1);
            rgb[2] = Mth.clamp(Mth.lerp(haze, b, fog[2]), 0, 1);
        }
    }

    private static final Light LIGHT = new Light();
    /** What the camera can see this frame; billows outside it are not built at all. */
    private static net.minecraft.client.renderer.culling.Frustum frustum;

    // ------------------------------------------------------------------
    // Passes
    // ------------------------------------------------------------------

    /** The sky, then solid smoke and dust, depth-tested against the city. */
    static void opaque(Vec3 camera, float t, net.minecraft.client.renderer.culling.Frustum view) {
        frustum = view;
        Matrix4f matrix = new Matrix4f();
        LIGHT.t = t;
        LIGHT.camX = (float) camera.x; LIGHT.camY = (float) camera.y; LIGHT.camZ = (float) camera.z;
        System.arraycopy(FOG, 0, LIGHT.fog, 0, 3);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            sky(matrix, t);
            if (t <= 0) return;
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            cloud(buffer, matrix, camera, t);
            wall(buffer, matrix, camera, t);
            draw(buffer);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
        }
    }

    private static final net.minecraft.resources.ResourceLocation SMOKE = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.jayptucker.heroesevolved.HeroesEvolved.MOD_ID, "textures/effects/nuclear_smoke.png");
    private record Sprite(float x, float y, float z, float size, float spin, float r, float g, float b, float a, double distance) {}

    /**
     * Soft smoke over the near face of the wall and drifting just ahead of it, so up close it rolls
     * like dust instead of standing like clay. Sorted back to front; turned by the clock, so it runs
     * backwards with everything else.
     */
    static void veil(Vec3 cam, float t, org.joml.Quaternionf rotation) {
        float radius = IntroBlast.front(t);
        double camAngle = Math.atan2(cam.z - CZ, cam.x - CX), camRadius = Math.hypot(cam.x - CX, cam.z - CZ);
        if (t < 3 || camRadius - radius > 130 || radius > 480) return;
        LIGHT.wall = true; LIGHT.heat = 0; LIGHT.dust = 1; LIGHT.tint = 1;
        LIGHT.camX = (float) cam.x; LIGHT.camY = (float) cam.y; LIGHT.camZ = (float) cam.z;
        System.arraycopy(FOG, 0, LIGHT.fog, 0, 3);
        java.util.List<Sprite> sprites = new java.util.ArrayList<>();
        float[] rgb = new float[3];
        int around = 300;
        int centre = (int) Math.round(camAngle / (Mth.TWO_PI / around));
        int span = (int) Math.ceil(75 / (radius * Mth.TWO_PI / around)) + 1;
        float grow = Mth.clamp(t / 25, 0.35F, 1);
        for (int row = 0; row < 7; row++)
            for (int d = -span; d <= span; d++) {
                int i = Math.floorMod(centre + d, around);
                float theta = (i + (row % 2) * 0.5F + hash(i + row * 71, 51) * 0.4F) * Mth.TWO_PI / around;
                float c = Mth.cos(theta), s = Mth.sin(theta);
                float h = wallHeight(t, theta, radius);
                float frac = 0.04F + row * 0.15F;
                // Just in front of the face; low rows further ahead, as dust thrown off the foot of the wall.
                float ahead = (row == 0 ? 5 : 2.5F) + hash(i + row * 71, 53) * 3;
                float face = row < 4 ? 0.06F * h : row == 4 ? 0 : -0.1F * h * (row - 4);
                float r = radius + face + ahead;
                float x = CX + c * r, y = CY - 1 + h * frac, z = CZ + s * r;
                double distance = Math.sqrt((x - cam.x) * (x - cam.x) + (y - cam.y) * (y - cam.y) + (z - cam.z) * (z - cam.z));
                if (distance > 90) continue;
                LIGHT.color(c * 0.8F, 0.45F, s * 0.8F, 0, x, y, z, rgb);
                float size = (4 + h * 0.1F + hash(i + row * 71, 55) * 5) * grow;
                float spin = hash(i + row * 71, 57) * Mth.TWO_PI + t * 0.025F * (hash(i, 59) > 0.5F ? 1 : -1);
                // Never right on the eye: a sprite fades out as it comes within a few blocks.
                float alpha = 0.72F * (float) Mth.clamp((distance - size * 0.6 - 3) / 10, 0, 1);
                if (alpha > 0.02F) sprites.add(new Sprite(x, y, z, size, spin, rgb[0], rgb[1], rgb[2], alpha, distance));
            }
        if (sprites.isEmpty()) return;
        sprites.sort(java.util.Comparator.comparingDouble(Sprite::distance).reversed());
        org.joml.Vector3f right = new org.joml.Vector3f(1, 0, 0).rotate(rotation), up = new org.joml.Vector3f(0, 1, 0).rotate(rotation);
        Matrix4f m = new Matrix4f();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, SMOKE);
        try {
            BufferBuilder out = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (Sprite p : sprites) {
                float cx = (float) (p.x - cam.x), cy = (float) (p.y - cam.y), cz = (float) (p.z - cam.z);
                float co = Mth.cos(p.spin) * p.size, si = Mth.sin(p.spin) * p.size;
                // The quad's corners, turned in the view plane.
                float ax = right.x() * co + up.x() * si, ay = right.y() * co + up.y() * si, az = right.z() * co + up.z() * si;
                float bx = -right.x() * si + up.x() * co, by = -right.y() * si + up.y() * co, bz = -right.z() * si + up.z() * co;
                out.addVertex(m, cx - ax - bx, cy - ay - by, cz - az - bz).setUv(0, 1).setColor(p.r, p.g, p.b, p.a);
                out.addVertex(m, cx + ax - bx, cy + ay - by, cz + az - bz).setUv(1, 1).setColor(p.r, p.g, p.b, p.a);
                out.addVertex(m, cx + ax + bx, cy + ay + by, cz + az + bz).setUv(1, 0).setColor(p.r, p.g, p.b, p.a);
                out.addVertex(m, cx - ax + bx, cy - ay + by, cz - az + bz).setUv(0, 0).setColor(p.r, p.g, p.b, p.a);
            }
            draw(out);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    /** Light: Sprague's glow, the flash, the condensation shell and the shimmer ahead of the wave. */
    static void glowing(Vec3 camera, float t) {
        Matrix4f matrix = new Matrix4f();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            BufferBuilder additive = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            glow(additive, matrix, camera, t);
            if (t > 0) shimmer(additive, matrix, camera, t);
            draw(additive);
            RenderSystem.defaultBlendFunc();
            if (t > 2 && t < 40) {
                BufferBuilder shell = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
                condensation(shell, matrix, camera, t);
                draw(shell);
            }
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static void draw(BufferBuilder buffer) {
        MeshData mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    // ------------------------------------------------------------------
    // The sky
    // ------------------------------------------------------------------

    /**
     * A dome around the camera, behind everything (only bare sky passes its depth test): dusk violet
     * overhead before the light, white at the flash, then burning orange going to brown.
     */
    private static void sky(Matrix4f matrix, float t) {
        float alpha;
        float zr, zg, zb;
        if (t < 0) { alpha = 0.35F; zr = 0.32F; zg = 0.25F; zb = 0.44F; }
        else if (t < 6) { float k = t / 6; alpha = Mth.lerp(k, 1, 0.75F); zr = Mth.lerp(k, 1, 0.55F); zg = Mth.lerp(k, 0.97F, 0.32F); zb = Mth.lerp(k, 0.92F, 0.2F); }
        else { float k = Mth.clamp((t - 6) / 90, 0, 1); alpha = Mth.lerp(k, 0.75F, 0.8F); zr = Mth.lerp(k, 0.5F, 0.24F); zg = Mth.lerp(k, 0.32F, 0.15F); zb = Mth.lerp(k, 0.24F, 0.14F); }
        float[] fog = FOG.clone();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        final float r0 = zr, g0 = zg, b0 = zb;
        CloudMesh.Shade gradient = (nx, ny, nz, bump, wx, wy, wz, rgb) -> {
            float up = Mth.clamp(ny * 1.6F, 0, 1);
            rgb[0] = Mth.lerp(up, fog[0], r0); rgb[1] = Mth.lerp(up, fog[1], g0); rgb[2] = Mth.lerp(up, fog[2], b0);
        };
        BufferBuilder dome = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        CloudMesh.puff(dome, matrix, CloudMesh.FINE, 0, 0, 0, 420, 420, 420, 0, 0, 0, alpha, 0, 0, 0, gradient);
        draw(dome);
    }

    // ------------------------------------------------------------------
    // The cloud
    // ------------------------------------------------------------------

    private static float smooth(float a, float b, float x) {
        float k = Mth.clamp((x - a) / (b - a), 0, 1);
        return k * k * (3 - 2 * k);
    }

    private static float hash(int i, int salt) {
        int h = i * 374761393 + salt * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535F;
    }

    private static void billow(BufferBuilder out, Matrix4f m, Vec3 cam, float x, float y, float z,
                               float rx, float ry, float rz, int variant, float spin, float heat, float dust, double reach) {
        if (rx < 0.2F) return;
        LIGHT.tint = 0.84F + 0.3F * hash(variant, 77);
        double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > reach) return;
        if (frustum != null && !frustum.isVisible(new net.minecraft.world.phys.AABB(x - rx * 1.5, y - ry * 1.5, z - rz * 1.5, x + rx * 1.5, y + ry * 1.5, z + rz * 1.5))) return;
        LIGHT.heat = heat;
        LIGHT.dust = dust;
        CloudMesh.puff(out, m, CloudMesh.detail(rx, distance), (float) dx, (float) dy, (float) dz, rx, ry, rz, variant, spin, 1, 1, x, y, z, LIGHT);
    }

    static float fireballRadius(float t) { return IntroBlast.FIREBALL * (1 - (float) Math.exp(-t / 6)); }
    static float ballY(float t) { return CY + fireballRadius(t) * 0.72F + Math.max(0, t - 8) * 0.55F; }

    /** The fireball rising and rolling over into a mushroom cap on a stem, over a skirt of dust. */
    private static void cloud(BufferBuilder out, Matrix4f m, Vec3 cam, float t) {
        LIGHT.wall = false;
        float fireball = IntroBlast.FIREBALL * (1 - (float) Math.exp(-t / 6));
        float s = smooth(16, 110, t);
        float lift = Math.max(0, t - 8);
        float ballY = CY + fireball * 0.72F + lift * 0.55F;
        float capY = Math.min(CY + 215, CY + fireball * 0.72F + lift * 0.86F);
        float major = Math.min(78, 20 + t * 0.32F) * s, minor = Math.min(34, 14 + t * 0.12F);
        int n = 30;
        for (int i = 0; i < n; i++) {
            // Fibonacci points on the fireball, which drift onto a ring rolling outward and down.
            float k = (i + 0.5F) / n, phi = (float) Math.acos(1 - 2 * k), theta = i * 2.39996F;
            float bx = Mth.sin(phi) * Mth.cos(theta), by = Mth.cos(phi), bz = Mth.sin(phi) * Mth.sin(theta);
            float ring = i * Mth.TWO_PI / n;
            float roll = hash(i, 3) * Mth.TWO_PI - t * 0.03F;
            float rr = major + minor * Mth.cos(roll);
            float px = Mth.lerp(s, bx * fireball * 0.45F, Mth.cos(ring) * rr);
            float py = Mth.lerp(s, ballY + by * fireball * 0.45F, capY + minor * 0.72F * Mth.sin(roll));
            float pz = Mth.lerp(s, bz * fireball * 0.45F, Mth.sin(ring) * rr);
            float size = Mth.lerp(s, fireball * 0.58F, minor * 0.98F) * (0.85F + 0.3F * hash(i, 5));
            float inner = 0.5F - 0.5F * Mth.cos(roll);
            float heat = (float) Math.exp(-t / 50) * (1 - s * 0.55F) + s * (float) Math.exp(-t / 85) * 0.55F * inner;
            billow(out, m, cam, CX + px, py, CZ + pz, size, size * 0.9F, size, i, t * 0.008F + i, heat, 0, 700);
        }
        // The crown over the ring.
        for (int i = 0; i < 13 && s > 0.05F; i++) {
            float a = i * Mth.TWO_PI / 12 + 0.3F;
            float r = i == 12 ? 0 : major * 0.52F;
            float size = minor * (i == 12 ? 1.2F : 0.98F) * s;
            billow(out, m, cam, CX + Mth.cos(a) * r, capY + minor * 0.95F, CZ + Mth.sin(a) * r, size, size * 0.82F, size,
                    40 + i, -t * 0.006F, (float) Math.exp(-t / 55) * 0.25F, 0.08F, 700);
        }
        // The stem: dust drawn up from the ground into the bottom of the cap.
        if (t > 10) {
            float bottom = capY - minor * 0.7F, grow = smooth(10, 60, t);
            int stems = 12;
            for (int i = 0; i < stems; i++) {
                float f = (i + 0.5F) / stems;
                float y = CY + 3 + (bottom - CY - 3) * f * grow;
                float r = (7 + Math.min(t, 140) * 0.075F) * (1.25F - 0.5F * Mth.sin(f * Mth.PI)) * (0.6F + 0.4F * grow);
                float sway = Mth.sin(i * 1.7F + t * 0.02F) * 2.2F;
                billow(out, m, cam, CX + sway, y, CZ - sway * 0.6F, r, r * 1.3F, r, 60 + i, t * 0.012F * (i % 2 == 0 ? 1 : -1),
                        0.4F * (float) Math.exp(-t / 34) * f, 0.55F, 700);
            }
        }
        // The base surge: dust thrown up all around ground zero.
        if (t > 3) {
            float reach = Math.min(IntroBlast.front(t) * 0.55F, 64);
            for (int i = 0; i < 20; i++) {
                float a = i * Mth.TWO_PI / 20 + hash(i, 9);
                float r = reach * (0.5F + 0.5F * hash(i, 11));
                float size = 7 + Math.min(t, 90) * 0.11F + hash(i, 13) * 4;
                billow(out, m, cam, CX + Mth.cos(a) * r, CY + size * 0.5F, CZ + Mth.sin(a) * r, size, size * 0.7F, size,
                        80 + i, t * 0.02F, 0.14F * (float) Math.exp(-t / 28), 1, 700);
            }
        }
    }

    // ------------------------------------------------------------------
    // The wall of dust
    // ------------------------------------------------------------------

    /** The wall's profile, front face to the back of the crest: offset from the front in heights, and fraction of the height. */
    private static final float[][] PROFILE = {{0.04F, 0}, {0.07F, 0.1F}, {0.08F, 0.24F}, {0.07F, 0.4F}, {0.04F, 0.56F}, {-0.01F, 0.71F},
            {-0.07F, 0.84F}, {-0.15F, 0.94F}, {-0.25F, 1}, {-0.37F, 0.96F}, {-0.52F, 0.84F}, {-0.7F, 0.66F}, {-0.9F, 0.46F}};
    /** Approximate normals for each profile row: outward and up. */
    private static final float[][] NORMAL = {{1, 0.05F}, {1, 0.08F}, {1, 0.1F}, {0.97F, 0.2F}, {0.9F, 0.4F}, {0.75F, 0.65F},
            {0.5F, 0.86F}, {0.25F, 0.97F}, {0, 1}, {-0.2F, 0.98F}, {-0.35F, 0.94F}, {-0.45F, 0.89F}, {-0.5F, 0.86F}};

    /** How tall the wall stands at an angle around ground zero. */
    static float wallHeight(float t, float theta, float radius) {
        float base = 20 + 20 * Mth.clamp(t / 60, 0, 1);
        return base * (0.74F + 0.52F * (float) (NOISE.noise(theta * radius / 30, t * 0.01, 3.7) * 0.5 + 0.5));
    }

    private static void wall(BufferBuilder out, Matrix4f m, Vec3 cam, float t) {
        float radius = IntroBlast.front(t);
        if (t < 2 || radius > 480) return;
        LIGHT.wall = true;
        LIGHT.heat = 0;
        LIGHT.dust = 1;
        // The ring, coarse all the way round except the stretch closest to the camera ...
        int segments = 360, rows = PROFILE.length;
        float step = Mth.TWO_PI / segments;
        double camAngle = Math.atan2(cam.z - CZ, cam.x - CX), camRadius = Math.hypot(cam.x - CX, cam.z - CZ);
        boolean near = camRadius - radius < 110 && camRadius - radius > -30;
        int centre = (int) Math.round(camAngle / step), half = near ? (int) Math.ceil(48 / (radius * step)) : -1;
        sheet(out, m, cam, t, radius, 0, step, segments, rows - 1, false, centre, half);
        // ... which is drawn finely there instead, rough to arm's length.
        if (near) {
            int fine = Math.max(8, (int) Math.ceil((2 * half + 1) * step * radius / 0.8F));
            sheet(out, m, cam, t, radius, (centre - half) * step, (2 * half + 1) * step / fine, fine, (rows - 1) * 3, true, 0, -1);
        }
        // Billows rolling up the face and over the crest, and low ones tumbling along the ground ahead.
        float grow = Mth.clamp(t / 25, 0.35F, 1);
        // Height, size and centre offset from the front, all as fractions of the wall's height: they bulge a little ahead of it, never far.
        float[][] rows2 = {{0.18F, 0.17F, -0.02F}, {0.42F, 0.2F, -0.04F}, {0.66F, 0.21F, -0.1F}, {0.9F, 0.26F, -0.22F}};
        int around = 120;
        for (int row = 0; row < rows2.length; row++)
            for (int i = 0; i < around; i++) {
                float theta = (i + (row % 2) * 0.5F) * Mth.TWO_PI / around + hash(i + row * 997, 21) * 0.01F;
                float c = Mth.cos(theta), s = Mth.sin(theta);
                float h = wallHeight(t, theta, radius);
                float size = h * rows2[row][1] * (0.8F + 0.4F * hash(i + row * 997, 23)) * grow;
                float r = radius + rows2[row][2] * h;
                float spin = (row % 2 == 0 ? 1 : -1) * t * 0.05F + i;
                float bx = CX + c * r, by = CY - 1 + h * rows2[row][0], bz = CZ + s * r;
                // Up close the big billows give way to the small ones, which hold their shape better at arm's length.
                if (Math.abs(bx - cam.x) + Math.abs(bz - cam.z) < 45 && Math.hypot(cam.x - CX, cam.z - CZ) - radius < 110) continue;
                billow(out, m, cam, bx, by, bz, size, size * 0.85F, size, 100 + i + row * 37, spin, 0, 1, 230);
            }
        // Close up, the face breaks into many small rolling billows. Their angles are fixed, so they
        // ride outward with the wall instead of sliding along it.
        int detailCount = 260;
        if (camRadius - radius < 110) {
            float[][] detail = {{0.07F, 0.11F, 0.03F}, {0.19F, 0.12F, 0.04F}, {0.31F, 0.12F, 0.03F}, {0.43F, 0.13F, 0.01F},
                    {0.55F, 0.13F, -0.02F}, {0.67F, 0.14F, -0.06F}, {0.78F, 0.14F, -0.11F}, {0.88F, 0.15F, -0.18F}};
            int nearest = (int) Math.round(camAngle / (Mth.TWO_PI / detailCount));
            int span = (int) Math.ceil(80 / (radius * Mth.TWO_PI / detailCount)) + 1;
            for (int row = 0; row < detail.length; row++)
                for (int d = -span; d <= span; d++) {
                    int i = Math.floorMod(nearest + d, detailCount);
                    float theta = (i + (row % 2) * 0.5F + hash(i + row * 131, 41) * 0.35F) * Mth.TWO_PI / detailCount;
                    float c = Mth.cos(theta), s = Mth.sin(theta);
                    float h = wallHeight(t, theta, radius);
                    float size = h * detail[row][1] * (0.75F + 0.5F * hash(i + row * 131, 43)) * grow;
                    float r = radius + detail[row][2] * h;
                    float y = CY - 1 + h * (detail[row][0] + (hash(i + row * 131, 47) - 0.5F) * 0.05F);
                    float spin = (row % 2 == 0 ? -1 : 1) * t * 0.07F + i * 0.7F;
                    billow(out, m, cam, CX + c * r, y, CZ + s * r, size, size * 0.85F, size, 500 + i + row * 53, spin, 0, 1, 120);
                }
        }
        for (int i = 0; i < around; i++) {
            float theta = i * Mth.TWO_PI / around + 0.013F;
            float c = Mth.cos(theta), s = Mth.sin(theta);
            float low = (3 + hash(i, 27) * 3) * grow;
            billow(out, m, cam, CX + c * (radius + 2), CY + low * 0.45F, CZ + s * (radius + 2), low * 1.4F, low, low * 1.4F,
                    300 + i, -t * 0.09F - i, 0, 1, 160);
        }
    }

    /**
     * A stretch of the wall's surface: {@code columns} steps of {@code dTheta} from {@code theta0},
     * {@code rowSteps} steps up the profile. Rough sheets add fine noise that fades out toward their
     * sides, so they meet the coarse ring without a step. Quads whose column lies within
     * {@code skipHalf} of {@code skipCentre} (counted round the ring) are left out.
     */
    private static void sheet(BufferBuilder out, Matrix4f m, Vec3 cam, float t, float radius, float theta0, float dTheta,
                              int columns, int rowSteps, boolean rough, int skipCentre, int skipHalf) {
        int rows = rowSteps + 1, last = PROFILE.length - 1;
        float[] xs = new float[(columns + 1) * rows], ys = new float[xs.length], zs = new float[xs.length];
        float[] rs = new float[xs.length], gs = new float[xs.length], bs = new float[xs.length];
        float[] rgb = new float[3];
        for (int j = 0; j <= columns; j++) {
            float theta = theta0 + j * dTheta, c = Mth.cos(theta), s = Mth.sin(theta);
            float h = wallHeight(t, theta, radius);
            float weight = rough ? 1 - Math.abs(2F * j / columns - 1) : 0;
            for (int k = 0; k < rows; k++) {
                float f = k * (float) last / rowSteps;
                int k0 = Math.min((int) f, last - 1);
                float u = f - k0;
                float offset = Mth.lerp(u, PROFILE[k0][0], PROFILE[k0 + 1][0]), frac = Mth.lerp(u, PROFILE[k0][1], PROFILE[k0 + 1][1]);
                float out0 = Mth.lerp(u, NORMAL[k0][0], NORMAL[k0 + 1][0]), up = Mth.lerp(u, NORMAL[k0][1], NORMAL[k0 + 1][1]);
                float y = CY - 1 + frac * h;
                double wobble = NOISE.noise(c * radius * 0.07, y * 0.09, s * radius * 0.07 + t * 0.025);
                float r = radius + offset * h + (float) wobble * 3.2F;
                y += (float) NOISE.noise(c * radius * 0.05 + 11, y * 0.07, s * radius * 0.05 - t * 0.02) * 3 * frac;
                if (weight > 0) {
                    r += (float) NOISE.noise(c * radius * 0.32 + 5, y * 0.32, s * radius * 0.32 + t * 0.04) * 1.7F * weight;
                    y += (float) NOISE.noise(c * radius * 0.28 - 7, y * 0.28, s * radius * 0.28 - t * 0.03) * 1.1F * weight * frac;
                }
                float wx = CX + c * r, wz = CZ + s * r;
                int i = j * rows + k;
                xs[i] = (float) (wx - cam.x); ys[i] = (float) (y - cam.y); zs[i] = (float) (wz - cam.z);
                float l = Mth.sqrt(out0 * out0 + up * up) + 1e-5F;
                float nx = c * out0 / l, ny = up / l, nz = s * out0 / l;
                // Mottled: darker and paler patches of dust across the face.
                LIGHT.tint = 0.8F + 0.32F * (float) (NOISE.noise(c * radius * 0.16 + 40, y * 0.2, s * radius * 0.16 + t * 0.05) * 0.5 + 0.5);
                LIGHT.color(nx, ny, nz, (float) wobble * 0.3F, wx, y, wz, rgb);
                // The crest glows from the fire behind it; the foot is in its own shadow.
                float rim = Math.max(0, ny) * (float) Math.exp(-t / 95) * 0.36F;
                float foot = Mth.clamp(0.6F + f * 0.1F, 0.6F, 1);
                rs[i] = Mth.clamp(rgb[0] * foot + rim, 0, 1); gs[i] = Mth.clamp(rgb[1] * foot + rim * 0.45F, 0, 1); bs[i] = Mth.clamp(rgb[2] * foot + rim * 0.12F, 0, 1);
            }
        }
        for (int j = 0; j < columns; j++) {
            if (skipHalf >= 0 && Math.floorMod(j - skipCentre + skipHalf, columns) <= 2 * skipHalf) continue;
            for (int k = 0; k + 1 < rows; k++) {
                int a = j * rows + k, b = (j + 1) * rows + k, c = a + 1, d = b + 1;
                vertex(out, m, xs, ys, zs, rs, gs, bs, a); vertex(out, m, xs, ys, zs, rs, gs, bs, b); vertex(out, m, xs, ys, zs, rs, gs, bs, d);
                vertex(out, m, xs, ys, zs, rs, gs, bs, a); vertex(out, m, xs, ys, zs, rs, gs, bs, d); vertex(out, m, xs, ys, zs, rs, gs, bs, c);
            }
        }
    }

    private static void vertex(BufferBuilder out, Matrix4f m, float[] x, float[] y, float[] z, float[] r, float[] g, float[] b, int i) {
        out.addVertex(m, x[i], y[i], z[i]).setColor(r[i], g[i], b[i], 1);
    }

    // ------------------------------------------------------------------
    // Light
    // ------------------------------------------------------------------

    /** How bright Sprague is: nothing much on arrival, unbearable at 6:14, gone a moment after. */
    static float glowStrength(float t) {
        if (IntroScene.prologue) {
            float since = IntroScene.sinceLight(0);
            return since < 0 ? 0 : com.jayptucker.heroesevolved.story.prologue.PrologueTimeline.glow(since, IntroScene.run);
        }
        int lead = IntroScene.lead();
        float start = IntroScene.vision ? TimeIntroService.GLOW_TICK - 120 : 0;
        if (t < 0) return Mth.clamp((t + lead - start) / (lead - start), 0.05F, 1);
        return Mth.clamp(1 - t / 5, 0, 1);
    }

    private static void glow(BufferBuilder out, Matrix4f m, Vec3 cam, float t) {
        float g = glowStrength(t);
        if (g <= 0) return;
        float pulse = 1 + 0.18F * Mth.sin((float) (IntroScene.now(0) * 0.31)) * (t < 0 ? 1 : 0);
        float x = (float) (CX - cam.x), y = (float) (CY + 1.6F + g * 3 - cam.y), z = (float) (CZ - cam.z);
        // A halo you can see over the rooftops, and a hot core.
        fan(out, m, x, y, z, (8 + 46 * g * g) * pulse, 1, 0.9F, 0.7F, 0.55F * g);
        fan(out, m, x, y, z, (3 + 12 * g) * pulse, 1, 0.98F, 0.9F, 0.9F * g);
        if (t > 0) fan(out, m, x, y + 14, z, 80 + t * 26, 1, 0.95F, 0.85F, Mth.clamp(1 - t / 6, 0, 1));
        if (t > 0) {
            // The fireball burns through the haze: an orange halo around it for its first few seconds.
            float halo = 0.65F * (float) Math.exp(-t / 35);
            if (halo > 0.02F) fan(out, m, x, (float) (ballY(t) - cam.y), z, fireballRadius(t) * 2.4F, 1, 0.72F, 0.38F, halo);
        }
        // A pillar of light rising from him.
        float height = 24 + 100 * g;
        float dx = (float) (cam.x - CX), dz = (float) (cam.z - CZ), l = Mth.sqrt(dx * dx + dz * dz) + 1e-4F;
        float rx = -dz / l, rz = dx / l, w = 0.8F + 3.2F * g;
        float a = 0.45F * g * g;
        out.addVertex(m, x - rx * w, y - 2, z - rz * w).setColor(1, 0.92F, 0.75F, a);
        out.addVertex(m, x + rx * w, y - 2, z + rz * w).setColor(1, 0.92F, 0.75F, a);
        out.addVertex(m, x + rx * w * 0.3F, y + height, z + rz * w * 0.3F).setColor(1, 0.92F, 0.75F, 0);
        out.addVertex(m, x - rx * w, y - 2, z - rz * w).setColor(1, 0.92F, 0.75F, a);
        out.addVertex(m, x + rx * w * 0.3F, y + height, z + rz * w * 0.3F).setColor(1, 0.92F, 0.75F, 0);
        out.addVertex(m, x - rx * w * 0.3F, y + height, z - rz * w * 0.3F).setColor(1, 0.92F, 0.75F, 0);
    }

    /** A camera-facing disc, bright in the middle and fading to nothing at the rim. */
    private static void fan(BufferBuilder out, Matrix4f m, float x, float y, float z, float radius,
                            float r, float g, float b, float alpha) {
        float dx = -x, dy = -y, dz = -z, l = Mth.sqrt(dx * dx + dy * dy + dz * dz) + 1e-4F;
        dx /= l; dy /= l; dz /= l;
        float ux = 0, uy = 1, uz = 0;
        if (Math.abs(dy) > 0.95F) { ux = 1; uy = 0; }
        float ax = uy * dz - uz * dy, ay = uz * dx - ux * dz, az = ux * dy - uy * dx;
        float al = Mth.sqrt(ax * ax + ay * ay + az * az);
        ax /= al; ay /= al; az /= al;
        float bx = dy * az - dz * ay, by = dz * ax - dx * az, bz = dx * ay - dy * ax;
        int sides = 24;
        for (int i = 0; i < sides; i++) {
            float a0 = i * Mth.TWO_PI / sides, a1 = (i + 1) * Mth.TWO_PI / sides;
            out.addVertex(m, x, y, z).setColor(r, g, b, alpha);
            out.addVertex(m, x + (ax * Mth.cos(a0) + bx * Mth.sin(a0)) * radius, y + (ay * Mth.cos(a0) + by * Mth.sin(a0)) * radius,
                    z + (az * Mth.cos(a0) + bz * Mth.sin(a0)) * radius).setColor(r, g * 0.8F, b * 0.6F, 0);
            out.addVertex(m, x + (ax * Mth.cos(a1) + bx * Mth.sin(a1)) * radius, y + (ay * Mth.cos(a1) + by * Mth.sin(a1)) * radius,
                    z + (az * Mth.cos(a1) + bz * Mth.sin(a1)) * radius).setColor(r, g * 0.8F, b * 0.6F, 0);
        }
    }

    /** The shock front itself, a faint bright band just ahead of the dust. */
    private static void shimmer(BufferBuilder out, Matrix4f m, Vec3 cam, float t) {
        float radius = IntroBlast.front(t) + 4;
        if (radius > 480) return;
        int segments = 200;
        float alpha = 0.14F * Mth.clamp(t / 6, 0, 1);
        for (int j = 0; j < segments; j++) {
            float a0 = j * Mth.TWO_PI / segments, a1 = (j + 1) * Mth.TWO_PI / segments;
            float h0 = wallHeight(t, a0, radius) + 20, h1 = wallHeight(t, a1, radius) + 20;
            float x0 = (float) (CX + Mth.cos(a0) * radius - cam.x), z0 = (float) (CZ + Mth.sin(a0) * radius - cam.z);
            float x1 = (float) (CX + Mth.cos(a1) * radius - cam.x), z1 = (float) (CZ + Mth.sin(a1) * radius - cam.z);
            float yb = (float) (CY - 1 - cam.y);
            out.addVertex(m, x0, yb, z0).setColor(1, 0.95F, 0.85F, alpha);
            out.addVertex(m, x1, yb, z1).setColor(1, 0.95F, 0.85F, alpha);
            out.addVertex(m, x1, yb + h1, z1).setColor(1, 0.95F, 0.85F, 0);
            out.addVertex(m, x0, yb, z0).setColor(1, 0.95F, 0.85F, alpha);
            out.addVertex(m, x1, yb + h1, z1).setColor(1, 0.95F, 0.85F, 0);
            out.addVertex(m, x0, yb + h0, z0).setColor(1, 0.95F, 0.85F, 0);
        }
    }

    /** For a breath after the flash, a pale dome of condensed air races out with the shock. */
    private static void condensation(BufferBuilder out, Matrix4f m, Vec3 cam, float t) {
        float radius = IntroBlast.front(t) * 0.97F;
        float alpha = 0.26F * smooth(2, 6, t) * (1 - smooth(16, 40, t));
        if (alpha <= 0.01F) return;
        CloudMesh.Shade white = (nx, ny, nz, bump, wx, wy, wz, rgb) -> {
            float rim = 1 - Math.abs(nx * 0.3F + ny * 0.3F);
            rgb[0] = 0.97F * rim; rgb[1] = 0.96F * rim; rgb[2] = 0.93F * rim;
        };
        CloudMesh.puff(out, m, CloudMesh.FINE, (float) (CX - cam.x), (float) (CY - cam.y), (float) (CZ - cam.z),
                radius, radius * 0.7F, radius, 3, 0, 0.04F, alpha, CX, CY, CZ, white);
    }
}
