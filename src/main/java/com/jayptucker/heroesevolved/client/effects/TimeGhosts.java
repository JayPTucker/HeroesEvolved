package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.TimeGhostPayload;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Ghosts of a time manipulator, drawn with their own skin in a violet-gold wash. Echoes are where they
 * were (Fold Space's starting point, the path a Rewind unwinds) and fade on their own. The marker is
 * where Rewind would take you right now: faint, slowly breathing, and only on your own screen.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class TimeGhosts {
    private static final int MAX = 32;
    private static final int ECHO_TINT = 0xE8D4FF, MARKER_TINT = 0xB7A2FF;
    private record Ghost(int entity, Vec3 position, float yaw, ResourceLocation skin, boolean slim, PartPose[] poses,
                         int mode, long born, int life) {}
    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static ClientLevel world;
    private static PlayerModel<AbstractClientPlayer> wide, slim;
    private static MultiBufferSource.BufferSource buffers;

    private TimeGhosts() {}

    private static PlayerModel<AbstractClientPlayer> model(boolean narrow) {
        Minecraft mc = Minecraft.getInstance();
        if (wide == null) wide = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        if (slim == null) slim = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
        return narrow ? slim : wide;
    }

    private static ModelPart[] parts(PlayerModel<?> m) {
        return new ModelPart[] {m.head, m.hat, m.body, m.leftArm, m.rightArm, m.leftLeg, m.rightLeg,
                m.jacket, m.leftSleeve, m.rightSleeve, m.leftPants, m.rightPants};
    }

    /** For previews and debugging: how many Rewind markers are showing. */
    public static long markers() { return GHOSTS.stream().filter(g -> g.mode == TimeGhostPayload.MARKER).count(); }

    public static void receive(TimeGhostPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != world) { GHOSTS.clear(); world = mc.level; wide = null; slim = null; }
        if (world == null || !(world.getEntity(payload.entityId()) instanceof AbstractClientPlayer player)) return;
        boolean narrow = player.getSkin().model() == PlayerSkin.Model.SLIM;
        var m = model(narrow);
        m.young = false; m.riding = false; m.attackTime = 0; m.swimAmount = 0;
        m.leftArmPose = HumanoidModel.ArmPose.EMPTY; m.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        if (payload.mode() == TimeGhostPayload.MARKER) {
            // Where you were: standing still, arms at your sides.
            m.crouching = false;
            m.setupAnim(player, 0, 0, 0, 0, 0);
        } else {
            m.crouching = player.isCrouching();
            m.setupAnim(player, player.walkAnimation.position(), player.walkAnimation.speed(), player.tickCount,
                    Mth.wrapDegrees(player.yHeadRot - player.yBodyRot), player.getXRot());
        }
        var parts = parts(m);
        PartPose[] poses = new PartPose[parts.length];
        for (int i = 0; i < parts.length; i++) poses[i] = parts[i].storePose();
        long now = world.getGameTime(), born = now + payload.delay();
        int life = payload.life();
        if (payload.mode() == TimeGhostPayload.MARKER) {
            // Only ever one marker. A refresh keeps its age and extends its life, so it never flickers.
            Ghost old = GHOSTS.stream().filter(g -> g.mode == TimeGhostPayload.MARKER && g.entity == payload.entityId()).findFirst().orElse(null);
            if (old != null) { GHOSTS.remove(old); born = old.born; life = (int) (now - born) + payload.life(); }
        }
        while (GHOSTS.size() >= MAX) GHOSTS.removeFirst();
        GHOSTS.add(new Ghost(payload.entityId(), payload.position(), payload.yaw(), player.getSkin().texture(), narrow, poses,
                payload.mode(), born, life));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != world) { GHOSTS.clear(); world = mc.level; wide = null; slim = null; }
        if (world == null) return;
        long now = world.getGameTime();
        GHOSTS.removeIf(g -> now - g.born >= g.life);
        if (now % 5 != 0) return;
        for (Ghost g : GHOSTS) {
            if (g.mode != TimeGhostPayload.MARKER || mc.player == null || g.position.distanceToSqr(mc.player.position()) < 1.5) continue;
            Vec3 floor = g.position.add(0, 0.05, 0);
            java.util.List<Vec3> ring = new java.util.ArrayList<>();
            for (int i = 0; i <= 24; i++) {
                double angle = i * Math.PI * 2 / 24;
                ring.add(floor.add(Math.cos(angle) * 0.7, 0, Math.sin(angle) * 0.7));
            }
            LightningRenderer.stroke(ring, 0.12F, 0.12F, TimeEffects.DEEP, 0.35F, 6, 0);
            LightningRenderer.stroke(ring, 0.03F, 0.03F, TimeEffects.VIOLET, 0.8F, 6, 0);
            world.addParticle(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, floor.x + (world.random.nextDouble() - 0.5) * 0.8,
                    floor.y + 0.1, floor.z + (world.random.nextDouble() - 0.5) * 0.8, 0, 0.04, 0);
        }
    }

    private static float alpha(Ghost g, float age) {
        if (age < 0) return 0;
        if (g.mode == TimeGhostPayload.MARKER) {
            float fadeIn = Math.min(1, age / 6F), fadeOut = Math.min(1, (g.life - age) / 4F);
            return (0.3F + 0.08F * Mth.sin(age * 0.18F)) * fadeIn * Math.max(0, fadeOut);
        }
        float t = age / g.life;
        return 0.55F * Math.min(1, age / 2F) * (1 - t) * (1 - t);
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || world == null || world != mc.level || GHOSTS.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(256 * 1024));
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (Ghost g : GHOSTS) {
            float age = world.getGameTime() - g.born + partial;
            float alpha = alpha(g, age);
            if (alpha <= 0.01F || g.position.distanceToSqr(camera) > 96 * 96) continue;
            // Don't put a ghost across your own eyes.
            if (g.position.add(0, 1.5, 0).distanceToSqr(camera) < 0.8) continue;
            if (g.mode == TimeGhostPayload.MARKER && mc.player != null && g.position.distanceToSqr(mc.player.position()) < 1.5) continue;
            var m = model(g.slim);
            var parts = parts(m);
            for (int i = 0; i < parts.length; i++) { parts[i].loadPose(g.poses[i]); parts[i].visible = true; }
            PoseStack pose = new PoseStack();
            pose.translate(g.position.x - camera.x, g.position.y - camera.y, g.position.z - camera.z);
            pose.mulPose(Axis.YP.rotationDegrees(180 - g.yaw));
            pose.scale(-0.9375F, -0.9375F, 0.9375F);
            pose.translate(0, -1.501, 0);
            int tint = g.mode == TimeGhostPayload.MARKER ? MARKER_TINT : ECHO_TINT;
            m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(g.skin)), 0xF000F0, OverlayTexture.NO_OVERLAY,
                    ((int) (Math.clamp(alpha, 0, 1) * 255) << 24) | tint);
        }
        buffers.endBatch();
    }
}
