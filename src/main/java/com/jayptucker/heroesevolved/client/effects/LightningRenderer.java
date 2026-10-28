package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws lightning the way vanilla draws its own bolts: untextured quads in the additive lightning
 * render type, so overlapping layers add up to a glow instead of stacking into a grey ribbon. Each
 * stroke is one continuous camera-facing ribbon along a polyline, tapering from start to end.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class LightningRenderer {
    private static final int MAX_STROKES = 1200;
    /** {@code soft} strokes fade from a bright centre line to nothing at their edges: a glow, not a slab. */
    private record Stroke(Vec3[] points, float startWidth, float endWidth, float r, float g, float b, float alpha,
                          int born, int delay, int life, boolean flicker, boolean soft) {}
    private static final List<Stroke> STROKES = new ArrayList<>();
    private static MultiBufferSource.BufferSource buffers;
    private static ClientLevel world;
    private static int clock;

    private LightningRenderer() {}

    /** Adds one stroke along {@code points}. Width is the half-width in blocks. */
    static void stroke(List<Vec3> points, float startWidth, float endWidth, int color, float alpha, int life, int delay) {
        stroke(points, startWidth, endWidth, color, alpha, life, delay, true);
    }

    static void stroke(List<Vec3> points, float startWidth, float endWidth, int color, float alpha, int life, int delay, boolean soft) {
        if (points.size() < 2 || STROKES.size() >= MAX_STROKES) return;
        STROKES.add(new Stroke(points.toArray(Vec3[]::new), startWidth, endWidth, ((color >> 16) & 255) / 255F,
                ((color >> 8) & 255) / 255F, (color & 255) / 255F, alpha, clock, delay, Math.max(1, life), life > 3, soft));
    }

    /** A four-pointed glint: crossed thin ribbons, for the bright point where current lands or leaves. */
    static void star(Vec3 center, float size, int color, float alpha, int life, int delay) {
        for (int i = 0; i < 3; i++) {
            double angle = i * Math.PI / 3;
            Vec3 arm = new Vec3(Math.cos(angle), Math.sin(angle * 2) * 0.6, Math.sin(angle)).normalize().scale(size);
            stroke(List.of(center.subtract(arm), center, center.add(arm)), size * 0.035F, size * 0.035F, color, alpha, life, delay);
        }
        stroke(List.of(center.add(0, -size * 0.6, 0), center.add(0, size * 0.6, 0)), size * 0.06F, size * 0.06F, color, alpha * 0.6F, life, delay);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != world) { STROKES.clear(); world = level; }
        if (Minecraft.getInstance().isPaused()) return;
        clock++;
        STROKES.removeIf(s -> clock - s.born - s.delay >= s.life);
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || STROKES.isEmpty() || world == null) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(512 * 1024));
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        VertexConsumer out = buffers.getBuffer(RenderType.lightning());
        for (Stroke s : STROKES) {
            float age = clock - s.born - s.delay + partial;
            if (age < 0) continue;
            float progress = Math.min(1, age / s.life);
            float fade = (1 - progress) * (1 - progress * 0.5F);
            // Long-lived strokes (a held stream) shimmer from frame to frame.
            if (s.flicker) fade *= 0.75F + 0.25F * (float) Math.abs(Math.sin(age * 7.3 + s.points[0].x * 13));
            float alpha = s.alpha * fade;
            if (alpha < 0.01F || s.points[0].distanceToSqr(camera) > 200 * 200 && s.points[s.points.length - 1].distanceToSqr(camera) > 200 * 200) continue;
            ribbon(out, s, camera, alpha);
        }
        buffers.endBatch(RenderType.lightning());
    }

    private static void ribbon(VertexConsumer out, Stroke s, Vec3 camera, float alpha) {
        Vec3[] p = s.points;
        Vec3 previousLeft = null, previousMiddle = null, previousRight = null;
        for (int i = 0; i < p.length; i++) {
            // Each point's sideways offset uses its local direction, so neighbouring quads share an edge.
            Vec3 tangent = p[Math.min(p.length - 1, i + 1)].subtract(p[Math.max(0, i - 1)]);
            Vec3 across = tangent.cross(camera.subtract(p[i]));
            if (across.lengthSqr() < 1.0E-8) across = tangent.cross(new Vec3(0, 1, 0));
            if (across.lengthSqr() < 1.0E-8) continue;
            float width = s.startWidth + (s.endWidth - s.startWidth) * i / (float) (p.length - 1);
            // Thin out right in front of the camera, so a bolt leaving your own hand stays a bolt, not a wall.
            width *= (float) Math.clamp(p[i].distanceTo(camera) / 3.0, 0.15, 1.0);
            across = across.normalize().scale(width);
            Vec3 middle = p[i].subtract(camera), left = middle.subtract(across), right = middle.add(across);
            if (previousLeft != null) {
                if (s.soft) {
                    // Bright along the middle, transparent at both edges.
                    vertex(out, previousLeft, s, 0); vertex(out, previousMiddle, s, alpha); vertex(out, middle, s, alpha); vertex(out, left, s, 0);
                    vertex(out, previousMiddle, s, alpha); vertex(out, previousRight, s, 0); vertex(out, right, s, 0); vertex(out, middle, s, alpha);
                } else {
                    vertex(out, previousLeft, s, alpha); vertex(out, previousRight, s, alpha); vertex(out, right, s, alpha); vertex(out, left, s, alpha);
                }
            }
            previousLeft = left; previousMiddle = middle; previousRight = right;
        }
    }

    private static void vertex(VertexConsumer out, Vec3 at, Stroke s, float alpha) {
        out.addVertex((float) at.x, (float) at.y, (float) at.z).setColor(s.r, s.g, s.b, alpha);
    }
}
