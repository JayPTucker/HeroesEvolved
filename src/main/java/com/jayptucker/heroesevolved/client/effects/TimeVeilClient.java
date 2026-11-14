package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.TimeVeilPayload;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Draws a caster who is moving faster than time ({@code time/TimeVeil}) as everyone else sees them: their real body is
 * hidden (model, name and shadow), and a stand-in with their skin stands perfectly still where they started. The caster
 * sees a faint copy of their own stand-in, to find the spot again. When it ends away from the spot, the stand-in is gone
 * at once; a speedster's leaves a short blur of afterimages toward where they really are.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class TimeVeilClient {
    private static final class Stand {
        final Vec3 at;
        final float yaw, pitch;
        final ResourceLocation dimension;
        long seen;
        ResourceLocation skin;
        boolean slim;
        PartPose[] poses;
        int clothes;

        Stand(Vec3 at, float yaw, float pitch, ResourceLocation dimension) {
            this.at = at; this.yaw = yaw; this.pitch = pitch; this.dimension = dimension;
        }
    }

    private static final Map<UUID, Stand> STANDS = new HashMap<>();
    private static MultiBufferSource.BufferSource buffers;

    private TimeVeilClient() {}

    public static void receive(TimeVeilPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        switch (payload.action()) {
            case TimeVeilPayload.SHOW -> {
                Stand stand = STANDS.get(payload.owner());
                if (stand == null || stand.at.distanceToSqr(payload.at()) > 1.0E-4) {
                    stand = new Stand(payload.at(), payload.yaw(), payload.pitch(), payload.dimension());
                    STANDS.put(payload.owner(), stand);
                }
                stand.seen = mc.level == null ? 0 : mc.level.getGameTime();
            }
            case TimeVeilPayload.VANISH -> {
                Stand stand = STANDS.remove(payload.owner());
                boolean own = mc.player != null && payload.owner().equals(mc.player.getUUID());
                // A speedster leaves a smear of themselves from the spot to where they really are.
                if (stand != null && !own && payload.style() == 0) SpeedAfterimages.along(payload.at(), payload.to(), 6);
            }
            default -> STANDS.remove(payload.owner());
        }
    }

    /** True when this client must not see {@code entity} where it really is. */
    public static boolean hidden(Entity entity) {
        if (STANDS.isEmpty() || !(entity instanceof Player)) return false;
        Minecraft mc = Minecraft.getInstance();
        return entity != mc.player && STANDS.containsKey(entity.getUUID());
    }

    @SubscribeEvent public static void render(RenderLivingEvent.Pre<?, ?> event) {
        if (hidden(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { STANDS.clear(); return; }
        long now = mc.level.getGameTime();
        // A lost refresh (out of range, a dropped packet) must never leave someone hidden for good.
        STANDS.values().removeIf(stand -> now - stand.seen > 40 || now < stand.seen - 40);
        for (var entry : STANDS.entrySet()) {
            Stand stand = entry.getValue();
            if (stand.poses != null) continue;
            if (mc.level.getPlayerByUUID(entry.getKey()) instanceof AbstractClientPlayer player) capture(stand, player);
        }
    }

    /** The stand-in is the caster as they stood: their skin and clothes, upright and still. */
    private static void capture(Stand stand, AbstractClientPlayer player) {
        stand.slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
        stand.skin = player.getSkin().texture();
        var model = SpeedAfterimages.model(stand.slim);
        model.young = false; model.riding = false; model.crouching = false; model.attackTime = 0; model.swimAmount = 0;
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY; model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        model.setupAnim(player, 0, 0, 0, 0, stand.pitch);
        var parts = SpeedAfterimages.parts(model);
        stand.poses = new PartPose[parts.length];
        for (int i = 0; i < parts.length; i++) stand.poses[i] = parts[i].storePose();
        int clothes = 0;
        for (PlayerModelPart part : PlayerModelPart.values()) if (player.isModelPartShown(part)) clothes |= part.getMask();
        stand.clothes = clothes;
    }

    @SubscribeEvent public static void draw(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || STANDS.isEmpty() || mc.level == null || mc.player == null) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(64 * 1024));
        ClientLevel level = mc.level;
        Vec3 camera = event.getCamera().getPosition();
        for (var entry : STANDS.entrySet()) {
            Stand stand = entry.getValue();
            if (stand.poses == null || !stand.dimension.equals(level.dimension().location())
                    || stand.at.distanceToSqr(camera) > 96 * 96
                    || !event.getFrustum().isVisible(new AABB(stand.at.add(-.6, 0, -.6), stand.at.add(.6, 2, .6)))) continue;
            boolean own = entry.getKey().equals(mc.player.getUUID());
            // Your own stand-in is a faint marker, and never drawn across your own camera.
            if (own && stand.at.add(0, 1.6, 0).distanceToSqr(camera) < 1.2) continue;
            var model = SpeedAfterimages.model(stand.slim);
            var parts = SpeedAfterimages.parts(model);
            for (int i = 0; i < parts.length; i++) { parts[i].loadPose(stand.poses[i]); parts[i].visible = true; }
            model.hat.visible = (stand.clothes & PlayerModelPart.HAT.getMask()) != 0;
            model.jacket.visible = (stand.clothes & PlayerModelPart.JACKET.getMask()) != 0;
            model.leftSleeve.visible = (stand.clothes & PlayerModelPart.LEFT_SLEEVE.getMask()) != 0;
            model.rightSleeve.visible = (stand.clothes & PlayerModelPart.RIGHT_SLEEVE.getMask()) != 0;
            model.leftPants.visible = (stand.clothes & PlayerModelPart.LEFT_PANTS_LEG.getMask()) != 0;
            model.rightPants.visible = (stand.clothes & PlayerModelPart.RIGHT_PANTS_LEG.getMask()) != 0;
            PoseStack pose = new PoseStack();
            pose.translate(stand.at.x - camera.x, stand.at.y - camera.y, stand.at.z - camera.z);
            pose.mulPose(Axis.YP.rotationDegrees(180 - stand.yaw));
            pose.scale(-.9375F, -.9375F, .9375F);
            pose.translate(0, -1.501, 0);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(stand.at.add(0, 1, 0)));
            int color = own ? 0x66B8C8FF : 0xFFFFFFFF;
            model.renderToBuffer(pose, buffers.getBuffer(own ? RenderType.entityTranslucent(stand.skin) : RenderType.entityCutoutNoCull(stand.skin)),
                    light, OverlayTexture.NO_OVERLAY, color);
        }
        buffers.endBatch();
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { STANDS.clear(); }
}
