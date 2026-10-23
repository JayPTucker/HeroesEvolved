package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.TimeIntroPayload;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import com.jayptucker.heroesevolved.gui.TimeIntroCaption;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

/** Scene geometry remains visible even with Minimal particles and low entity distance. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class TimeIntroClient {
    private static Siren siren;
    private static BlastSound blastSound;
    private static long detonation = -1;
    private static boolean active;

    private TimeIntroClient() {}

    public static void cue(int stage) {
        Minecraft mc = Minecraft.getInstance();
        if (stage == TimeIntroPayload.CANCEL) { clear(); TimeIntroCaption.clear(); return; }
        if (stage == TimeIntroPayload.RETURN) {
            clear();
            TimeIntroCaption.show(stage);
            return;
        }
        if (stage == TimeIntroPayload.ARRIVAL) {
            clear();
            active = true;
            siren = new Siren();
            mc.getSoundManager().play(siren);
            TimeIntroCaption.show(stage);
        } else if (stage == TimeIntroPayload.DETONATION && mc.level != null) {
            detonation = mc.level.getGameTime();
            TimeIntroCaption.show(stage);
        } else if (stage == TimeIntroPayload.BLAST_SOUND && active) {
            if (blastSound != null) mc.getSoundManager().stop(blastSound);
            blastSound = new BlastSound();
            mc.getSoundManager().play(blastSound);
        }
    }

    private static void clear() {
        if (siren != null) Minecraft.getInstance().getSoundManager().stop(siren);
        if (blastSound != null) Minecraft.getInstance().getSoundManager().stop(blastSound);
        siren = null;
        blastSound = null;
        active = false;
        detonation = -1;
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        TimeIntroCaption.clear();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (active && (mc.level == null || !TimeIntroService.isCity(mc.level))) clear();
    }

    @SubscribeEvent public static void fogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !TimeIntroService.isCity(mc.level)) return;
        event.setRed(0.46F);
        event.setGreen(0.36F);
        event.setBlue(0.30F);
    }

    @SubscribeEvent public static void fog(ViewportEvent.RenderFog event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !TimeIntroService.isCity(mc.level)) return;
        float end = Math.min(192, event.getFarPlaneDistance());
        event.setFarPlaneDistance(end);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), end * 0.65F));
        event.setCanceled(true);
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || mc.level == null
                || !TimeIntroService.isCity(mc.level) || detonation < 0) return;
        float ticks = mc.level.getGameTime() - detonation
                + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        if (ticks < 0 || ticks > 180) return;
        float seconds = ticks / 20.0F;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poses = new PoseStack();
        poses.mulPose(event.getModelViewMatrix());
        poses.translate(TimeIntroService.BLAST.x - camera.x,
                TimeIntroService.BLAST.y - camera.y, TimeIntroService.BLAST.z - camera.z);
        Matrix4f matrix = poses.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR);
        float growth = Mth.clamp(seconds / 2.8F, 0, 1);
        // An incandescent core, a rising stem, and rolling lobes form a readable mushroom cloud.
        sphere(buffer, matrix, 0, 10 + growth * 13, 0, 3 + growth * 22,
                3 + growth * 21, 3 + growth * 22, 1, 0.64F, 0.16F, 0.98F, 0);
        for (int i = 0; i < 10; i++) {
            float rise = Math.min(1, seconds / (0.35F + i * 0.15F));
            float y = 5 + i * 5.6F * rise;
            float radius = (7 + 2 * (float) Math.sin(i * 1.9)) * growth + 1;
            float heat = Math.max(0, 1 - seconds / 9 - i * 0.04F);
            sphere(buffer, matrix, (float) Math.sin(i * 2.4) * 2, y, 0,
                    radius, 7 * growth + 1, radius, 0.28F + heat * 0.55F,
                    0.23F + heat * 0.19F, 0.20F, 0.98F, i);
        }
        float capY = 32 + Math.min(seconds, 6.5F) * 5;
        for (int i = 0; i < 28; i++) {
            double angle = i * Math.PI * 2 / 14;
            float ring = i < 14 ? 22 : 11;
            float rolling = (float) Math.sin(seconds * 1.4 + i * 2.3);
            float heat = Mth.clamp(1 - seconds * 0.09F - (i % 4) * 0.12F, 0, 1);
            sphere(buffer, matrix, (float) Math.cos(angle) * ring * growth,
                    capY + (i < 14 ? rolling * 3 : 9), (float) Math.sin(angle) * ring * growth,
                    (12 + rolling * 2) * growth + 0.3F, (9 + rolling) * growth + 0.3F,
                    (12 + rolling * 2) * growth + 0.3F,
                    0.25F + heat * 0.65F, 0.21F + heat * 0.29F, 0.18F + heat * 0.04F, 0.98F, i);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        // Dust advances down the avenue; at return time its front remains short of the group.
        RenderSystem.depthMask(false);
        buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float radius = 8 + seconds * 18;
        ring(buffer, matrix, radius, radius + 4, 0.4F, 0.85F, 0.76F, 0.61F, 0.68F);
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI * 2 / 64;
            sphere(buffer, matrix, (float) Math.cos(a) * radius, 4 + (float) Math.sin(i * 2.7) * 2,
                    (float) Math.sin(a) * radius, 5, 6, 5, 0.64F, 0.56F, 0.47F, 0.48F, i);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void sphere(BufferBuilder b, Matrix4f m, float x, float y, float z,
                               float rx, float ry, float rz, float r, float g, float blue, float alpha, int seed) {
        for (int row = 0; row < 8; row++) {
            double p0 = -Math.PI / 2 + row * Math.PI / 8, p1 = p0 + Math.PI / 8;
            for (int col = 0; col < 12; col++) {
                double a0 = col * Math.PI * 2 / 12, a1 = a0 + Math.PI * 2 / 12;
                float shade = 0.65F + 0.35F * (float) (Math.sin(p1) * 0.5 + 0.5);
                point(b, m, x, y, z, rx, ry, rz, p0, a0, r * shade, g * shade, blue * shade, alpha, seed);
                point(b, m, x, y, z, rx, ry, rz, p0, a1, r * shade, g * shade, blue * shade, alpha, seed);
                point(b, m, x, y, z, rx, ry, rz, p1, a1, r * shade, g * shade, blue * shade, alpha, seed);
                point(b, m, x, y, z, rx, ry, rz, p1, a0, r * shade, g * shade, blue * shade, alpha, seed);
            }
        }
    }

    private static void point(BufferBuilder b, Matrix4f m, float x, float y, float z,
                              float rx, float ry, float rz, double p, double a,
                              float r, float g, float blue, float alpha, int seed) {
        float bump = 1 + 0.07F * (float) (Math.sin(a * 5 + seed) * Math.cos(p * 4 + seed));
        b.addVertex(m, x + (float) (Math.cos(a) * Math.cos(p)) * rx * bump,
                y + (float) Math.sin(p) * ry * bump,
                z + (float) (Math.sin(a) * Math.cos(p)) * rz * bump).setColor(r, g, blue, alpha);
    }

    private static void ring(BufferBuilder b, Matrix4f m, float inner, float outer, float y,
                             float r, float g, float blue, float alpha) {
        for (int i = 0; i < 128; i++) {
            double a = i * Math.PI / 64, next = (i + 1) * Math.PI / 64;
            b.addVertex(m, (float) Math.cos(a) * inner, y, (float) Math.sin(a) * inner).setColor(r, g, blue, alpha);
            b.addVertex(m, (float) Math.cos(a) * outer, y, (float) Math.sin(a) * outer).setColor(r, g, blue, alpha);
            b.addVertex(m, (float) Math.cos(next) * outer, y, (float) Math.sin(next) * outer).setColor(r, g, blue, alpha);
            b.addVertex(m, (float) Math.cos(next) * inner, y, (float) Math.sin(next) * inner).setColor(r, g, blue, alpha);
        }
    }

    private static final class Siren extends AbstractTickableSoundInstance {
        Siren() {
            super(ModSounds.NUCLEAR_SIREN.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = 0.65F;
        }
        @Override public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (!active || mc.level == null || !TimeIntroService.isCity(mc.level)) stop();
            // Leave space in the mix for the much louder detonation and its rolling echoes.
            volume = detonation < 0 ? 0.55F : 0.18F;
        }
    }

    private static final class BlastSound extends AbstractTickableSoundInstance {
        BlastSound() {
            super(ModSounds.NUCLEAR_BLAST.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            relative = true;
            attenuation = Attenuation.NONE;
            volume = 1.0F;
        }
        @Override public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (!active || mc.level == null || !TimeIntroService.isCity(mc.level)) stop();
        }
    }
}
