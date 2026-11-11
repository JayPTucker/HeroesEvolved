package com.jayptucker.heroesevolved.client.effects.intro;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.time.intro.IntroBlast;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * The people of Odessa, run back. Every villager in the vision is followed from the moment the visitor
 * arrives. When time stops they hold where they are, mid-stride, and while it runs back they run back too,
 * along the very paths they took, with violet trails behind them the way Rewrite the Moment draws a path
 * through time: in step with the wave while it goes home, then all the way back past the sirens to where
 * they stood talking, browsing and walking before anyone screamed or ran. Those the dust had taken come
 * back out of it as it passes them. The real villagers are hidden meanwhile and stand-ins drawn in their
 * place, on this client only.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
final class CrowdRewind {
    /** How far back the crowd goes: a little before the sirens started and everyone ran. */
    static final int BEFORE_PANIC = TimeIntroService.SIREN_TICK - 10;
    private static final int MOST = 1400;

    /** One person's path: a sample per tick since the visitor arrived (x, y, z, body yaw, head yaw). */
    private static final class Track {
        final float[] samples = new float[MOST * 5];
        int first = -1, last = -1;
        VillagerData data;
        Villager puppet;
        float lastX, lastZ;

        void record(int tick, Villager v) {
            if (tick < 0 || tick >= MOST) return;
            if (first < 0) first = tick;
            // Fill any gap (out of sight for a moment) with where they were last seen.
            for (int t = Math.max(first, last + 1); t < tick; t++) System.arraycopy(samples, last * 5, samples, t * 5, 5);
            int i = tick * 5;
            samples[i] = (float) v.getX(); samples[i + 1] = (float) v.getY(); samples[i + 2] = (float) v.getZ();
            samples[i + 3] = v.yBodyRot; samples[i + 4] = v.yHeadRot;
            last = tick;
            data = v.getVillagerData();
        }

        boolean present(float clock) { return first >= 0 && clock >= first && clock <= last + 0.5F; }

        /** Where they were at {@code clock} (in ticks since arrival), between samples. */
        float[] at(float clock) {
            float c = Mth.clamp(clock, first, last);
            int a = (int) Math.floor(c), b = Math.min(last, a + 1);
            float k = c - a;
            float[] out = new float[5];
            for (int j = 0; j < 5; j++) {
                float va = samples[a * 5 + j], vb = samples[b * 5 + j];
                out[j] = j >= 3 ? va + Mth.wrapDegrees(vb - va) * k : Mth.lerp(k, va, vb);
            }
            return out;
        }
    }

    private static final Map<Integer, Track> TRACKS = new HashMap<>();
    /** True while the stand-ins are being drawn, so hiding the real villagers doesn't hide them too. */
    private static boolean drawing;

    private CrowdRewind() {}

    static void reset() { TRACKS.clear(); }

    private static boolean running() { return IntroScene.active && IntroScene.vision && IntroScene.arrival >= 0; }

    /** Each client tick before time stops: where everyone is. */
    static void record(Minecraft mc) {
        if (!running() || IntroScene.stopped() || mc.level == null) return;
        int tick = (int) (mc.level.getGameTime() - IntroScene.arrival);
        for (Entity e : mc.level.entitiesForRendering())
            if (e instanceof Villager v) TRACKS.computeIfAbsent(v.getId(), id -> new Track()).record(tick, v);
    }

    /**
     * The crowd's own clock, in ticks since arrival: held while time is stopped, then run back with the scene's
     * clock while the wave goes home, and on from the flash back to before the sirens in the rewind's last moments.
     */
    static float clock(float partial) {
        float det = IntroScene.detonation >= 0 ? IntroScene.detonation - IntroScene.arrival : TimeIntroService.DETONATION_TICK;
        float t = IntroScene.rewinding(partial) ? IntroScene.t(partial) : IntroScene.frozenAt;
        if (t >= 0) return det + t;
        float k = Mth.clamp(t / IntroScene.REWOUND, 0, 1);
        return det - k * (det - BEFORE_PANIC);
    }

    /** Hides the real villagers once time has stopped: their stand-ins are drawn instead. */
    @SubscribeEvent public static void hide(RenderLivingEvent.Pre<?, ?> event) {
        if (!drawing && event.getEntity() instanceof Villager && running() && IntroScene.stopped() && !TRACKS.isEmpty()) event.setCanceled(true);
    }

    /** Each client tick while stopped: the stand-ins' legs move as fast as they are carried. */
    static void tick(Minecraft mc) {
        if (!running() || !IntroScene.stopped() || mc.level == null) return;
        float clock = clock(0);
        for (Track track : TRACKS.values()) {
            if (track.puppet == null || !track.present(clock)) continue;
            float[] p = track.at(clock);
            float moved = Mth.sqrt((p[0] - track.lastX) * (p[0] - track.lastX) + (p[2] - track.lastZ) * (p[2] - track.lastZ));
            track.lastX = p[0]; track.lastZ = p[2];
            track.puppet.walkAnimation.update(IntroScene.rewinding(0) ? Math.min(1, moved * 3) : 0, 0.4F);
        }
    }

    /** The stand-ins, where the crowd's clock says everyone was. */
    static void render(Vec3 camera, float partial, PoseStack pose, MultiBufferSource.BufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        if (!running() || !IntroScene.stopped() || mc.level == null || TRACKS.isEmpty()) return;
        float clock = clock(partial);
        var dispatcher = mc.getEntityRenderDispatcher();
        drawing = true;
        try {
        for (Track track : TRACKS.values()) {
            if (!track.present(clock)) continue;
            float[] p = track.at(clock);
            double dx = p[0] - camera.x, dy = p[1] - camera.y, dz = p[2] - camera.z;
            if (dx * dx + dy * dy + dz * dz > 128 * 128) continue;
            if (track.puppet == null) {
                track.puppet = new Villager(EntityType.VILLAGER, mc.level);
                if (track.data != null) track.puppet.setVillagerData(track.data);
                track.lastX = p[0]; track.lastZ = p[2];
            }
            Villager v = track.puppet;
            v.setPos(p[0], p[1], p[2]);
            v.xo = p[0]; v.yo = p[1]; v.zo = p[2];
            v.setYRot(p[3]); v.yRotO = p[3];
            v.yBodyRot = v.yBodyRotO = p[3];
            v.yHeadRot = v.yHeadRotO = p[4];
            pose.pushPose();
            pose.translate(dx, dy, dz);
            dispatcher.render(v, 0, 0, 0, p[3], partial, pose, buffers, dispatcher.getPackedLightCoords(v, partial));
            pose.popPose();
        }
        } finally { drawing = false; }
        buffers.endBatch();
    }

    private static final float[] DEEP = {0.36F, 0.25F, 0.82F}, VIOLET = {0.6F, 0.49F, 1}, PALE = {0.96F, 0.92F, 1};

    /** While they run back: a violet trail behind each, along where they just were in reverse. */
    static void trails(Vec3 camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (!running() || !IntroScene.rewinding(partial) || mc.level == null || TRACKS.isEmpty()) return;
        float clock = clock(partial);
        // How much crowd-time a trail covers: longer while they move fast.
        float since = IntroScene.now(partial) - IntroScene.freeze - IntroBlast.FREEZE_TICKS;
        float span = Mth.clamp(6 + since * 0.8F, 6, 40);
        float fade = Mth.clamp(since / 4, 0, 1) * Mth.clamp((IntroBlast.REWIND_TICKS - since) / 6, 0, 1);
        if (fade <= 0.01F) return;
        Matrix4f m = new Matrix4f();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        try {
            BufferBuilder out = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            for (Track track : TRACKS.values()) {
                if (!track.present(clock)) continue;
                float[] head = track.at(clock);
                if ((head[0] - camera.x) * (head[0] - camera.x) + (head[2] - camera.z) * (head[2] - camera.z) > 96 * 96) continue;
                int n = 10;
                float[][] pts = new float[n + 1][];
                for (int i = 0; i <= n; i++) {
                    float[] q = track.at(Math.min(track.last, clock + span * i / n));
                    pts[i] = new float[] {(float) (q[0] - camera.x), (float) (q[1] + 1.0 - camera.y), (float) (q[2] - camera.z)};
                }
                ribbon(out, m, pts, 0.32F, DEEP, 0.35F * fade);
                ribbon(out, m, pts, 0.12F, VIOLET, 0.8F * fade);
                ribbon(out, m, pts, 0.04F, PALE, 0.9F * fade);
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

    /** A soft band through camera-relative points, facing the camera, bright at the first point and gone by the last. */
    private static void ribbon(BufferBuilder out, Matrix4f m, float[][] p, float width, float[] c, float alpha) {
        int n = p.length;
        float[][] side = new float[n][];
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
            float tx = p[b][0] - p[a][0], ty = p[b][1] - p[a][1], tz = p[b][2] - p[a][2];
            float sx = ty * p[i][2] - tz * p[i][1], sy = tz * p[i][0] - tx * p[i][2], sz = tx * p[i][1] - ty * p[i][0];
            float l = Mth.sqrt(sx * sx + sy * sy + sz * sz);
            side[i] = l < 1e-5F ? new float[] {width, 0, 0} : new float[] {sx / l * width, sy / l * width, sz / l * width};
        }
        for (int i = 0; i + 1 < n; i++) {
            float a0 = alpha * (1 - i / (float) (n - 1)), a1 = alpha * (1 - (i + 1) / (float) (n - 1));
            if (a0 <= 0.003F) continue;
            for (int s = -1; s <= 1; s += 2) {
                float[] e0 = {p[i][0] + side[i][0] * s, p[i][1] + side[i][1] * s, p[i][2] + side[i][2] * s};
                float[] e1 = {p[i + 1][0] + side[i + 1][0] * s, p[i + 1][1] + side[i + 1][1] * s, p[i + 1][2] + side[i + 1][2] * s};
                vertex(out, m, p[i], c, a0); vertex(out, m, e0, c, 0); vertex(out, m, e1, c, 0);
                vertex(out, m, p[i], c, a0); vertex(out, m, e1, c, 0); vertex(out, m, p[i + 1], c, a1);
            }
        }
    }

    private static void vertex(BufferBuilder out, Matrix4f m, float[] p, float[] c, float a) {
        out.addVertex(m, p[0], p[1], p[2]).setColor(c[0], c[1], c[2], a);
    }
}
