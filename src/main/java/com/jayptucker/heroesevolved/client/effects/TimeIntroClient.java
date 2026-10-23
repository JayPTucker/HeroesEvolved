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
        // AFTER_PARTICLES already applies the view matrix in the shader.
        Matrix4f matrix = DetonationCloudGeometry.worldTransform(TimeIntroService.BLAST, camera, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                HeroesEvolved.MOD_ID, "textures/effects/nuclear_smoke.png"));
        try {
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION_TEX_COLOR);
            var puffs = DetonationCloudGeometry.puffs(seconds, 1, mc.options.particles().get()
                    == net.minecraft.client.ParticleStatus.MINIMAL ? 0 : 1);
            // Keep the cinematic's approaching dust front, using twelve soft billows
            // instead of sixty-four overlapping sphere meshes.
            float radius = 8 + seconds * 18;
            for (int i = 0; i < 12; i++) {
                double angle = i * Math.PI / 6;
                puffs.add(new DetonationCloudGeometry.Puff((float) Math.cos(angle) * radius,
                        5, (float) Math.sin(angle) * radius, 4 + radius * .22F, 5, 0, .38F));
            }
            DetonationCloudGeometry.draw(buffer, matrix, event.getCamera().rotation(), puffs,
                    camera.subtract(TimeIntroService.BLAST));
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
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
