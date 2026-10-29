package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * A limb growing back from the bone out. While it is missing it is a raw stump; then the bone pushes
 * out to full length (with a knob at the elbow or knee), red muscle wraps around it from the shoulder
 * or hip down, and finally the skin closes over it and the limb is whole. Drawn in the limb's own
 * model space, so it follows every animation and pose, including lying on the ground.
 * With severed limbs turned off in Effects Settings, the limb only fades out and back in.
 */
public final class RegrowthLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation BONE = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "textures/entity/regrowth/bone.png");
    private static final ResourceLocation MUSCLE = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "textures/entity/regrowth/muscle.png");
    private static final float LENGTH = 12;

    public RegrowthLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        BodyVisuals.State state = BodyVisuals.get(player);
        if (state == null || player.isInvisible()) return;
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
        for (int limb = 0; limb < 4; limb++) {
            if (!state.missing(limb)) continue;
            float progress = BodyVisuals.progress(player, limb, partialTick);
            ModelPart part = switch (limb) {
                case BodyVisuals.RIGHT_ARM -> model.rightArm;
                case BodyVisuals.LEFT_ARM -> model.leftArm;
                case BodyVisuals.RIGHT_LEG -> model.rightLeg;
                default -> model.leftLeg;
            };
            ModelPart layer = switch (limb) {
                case BodyVisuals.RIGHT_ARM -> model.rightSleeve;
                case BodyVisuals.LEFT_ARM -> model.leftSleeve;
                case BodyVisuals.RIGHT_LEG -> model.rightPants;
                default -> model.leftPants;
            };
            var shownPart = switch (limb) {
                case BodyVisuals.RIGHT_ARM -> net.minecraft.world.entity.player.PlayerModelPart.RIGHT_SLEEVE;
                case BodyVisuals.LEFT_ARM -> net.minecraft.world.entity.player.PlayerModelPart.LEFT_SLEEVE;
                case BodyVisuals.RIGHT_LEG -> net.minecraft.world.entity.player.PlayerModelPart.RIGHT_PANTS_LEG;
                default -> net.minecraft.world.entity.player.PlayerModelPart.LEFT_PANTS_LEG;
            };
            boolean arm = limb <= BodyVisuals.LEFT_ARM;
            // Where the limb's box sits in its part, in model pixels: arms hang from the shoulder, legs from the hip.
            float halfWidth = arm && slim ? 1.5F : 2F;
            float centerX = arm ? (limb == BodyVisuals.RIGHT_ARM ? -1 : 1) * (slim ? 0.5F : 1F) : 0F;
            float top = arm ? -2 : 0;
            boolean gore = BodyVisuals.goreShown();
            float skin = gore ? Mth.clamp((progress - 0.65F) / 0.35F, 0, 1) : Math.max(0, progress);
            if (gore && skin < 0.97F) {
                pose.pushPose();
                part.translateAndRotate(pose);
                flesh(pose, buffers, light, progress, centerX, top, halfWidth);
                pose.popPose();
            }
            if (skin > 0.02F) skin(pose, buffers, light, player, part, player.isModelPartShown(shownPart) ? layer : null, skin);
        }
    }

    /**
     * The stump, the bone and the muscle at this stage of growth. Each material is drawn in one batch:
     * asking the buffer source for a second texture ends the first one's batch.
     */
    private static void flesh(PoseStack pose, MultiBufferSource buffers, int light, float progress, float cx, float top, float half) {
        PoseStack.Pose matrix = pose.last();
        float m = half * 0.84F;
        java.util.List<float[]> bone = new java.util.ArrayList<>(), muscle = new java.util.ArrayList<>();
        // The stump: a raw cap where the limb was taken, with the bone end showing.
        muscle.add(new float[] {cx - m, top, -1.7F, cx + m, top + 1.4F, 1.7F});
        if (progress < 0) {
            bone.add(new float[] {cx - 0.7F, top + 1.2F, -0.7F, cx + 0.7F, top + 2.0F, 0.7F});
        } else {
            // The bone pushes out to full length first.
            float boneLength = LENGTH * Mth.clamp(progress / 0.35F, 0.08F, 1);
            bone.add(new float[] {cx - 0.7F, top, -0.7F, cx + 0.7F, top + boneLength, 0.7F});
            if (boneLength > LENGTH * 0.5F) bone.add(new float[] {cx - 1.1F, top + 5.4F, -1.1F, cx + 1.1F, top + 6.6F, 1.1F});
            float tip = top + boneLength;
            bone.add(new float[] {cx - 1.0F, tip - 1.2F, -1.0F, cx + 1.0F, tip, 1.0F});
            // Then the muscle wraps it, from the joint down.
            float muscleLength = LENGTH * Mth.clamp((progress - 0.28F) / 0.42F, 0, 1);
            if (muscleLength > 0.2F) {
                float grow = Mth.clamp((progress - 0.28F) / 0.2F, 0.45F, 1);
                muscle.add(new float[] {cx - m * grow, top, -1.7F * grow, cx + m * grow, top + muscleLength, 1.7F * grow});
            }
        }
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(BONE));
        for (float[] b : bone) box(out, matrix, b[0], b[1], b[2], b[3], b[4], b[5], light);
        out = buffers.getBuffer(RenderType.entityCutoutNoCull(MUSCLE));
        for (float[] b : muscle) box(out, matrix, b[0], b[1], b[2], b[3], b[4], b[5], light);
    }

    /** The real limb with this player's skin, fading in as the skin closes over. */
    private static void skin(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                             ModelPart part, ModelPart layer, float alpha) {
        int color = FastColor.ARGB32.color((int) (alpha * 255), 255, 255, 255);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(player.getSkin().texture()));
        boolean partShown = part.visible;
        part.visible = true;
        part.render(pose, consumer, light, OverlayTexture.NO_OVERLAY, color);
        part.visible = partShown;
        if (layer != null) {
            boolean layerShown = layer.visible;
            layer.visible = true;
            layer.render(pose, consumer, light, OverlayTexture.NO_OVERLAY, color);
            layer.visible = layerShown;
        }
    }

    /** An axis-aligned box in model pixels, textured on every face. */
    private static void box(VertexConsumer out, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
        float a = x0 / 16, b = y0 / 16, c = z0 / 16, d = x1 / 16, e = y1 / 16, f = z1 / 16;
        float v = Math.max(0.25F, (y1 - y0) / 12F);
        quad(out, pose, light, a, b, c, d, b, c, d, e, c, a, e, c, 0, 0, -1, v);
        quad(out, pose, light, d, b, f, a, b, f, a, e, f, d, e, f, 0, 0, 1, v);
        quad(out, pose, light, a, b, f, a, b, c, a, e, c, a, e, f, -1, 0, 0, v);
        quad(out, pose, light, d, b, c, d, b, f, d, e, f, d, e, c, 1, 0, 0, v);
        quad(out, pose, light, a, b, f, d, b, f, d, b, c, a, b, c, 0, -1, 0, 0.25F);
        quad(out, pose, light, a, e, c, d, e, c, d, e, f, a, e, f, 0, 1, 0, 0.25F);
    }

    private static void quad(VertexConsumer out, PoseStack.Pose pose, int light,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float nx, float ny, float nz, float vScale) {
        vertex(out, pose, light, x0, y0, z0, 0, 0, nx, ny, nz);
        vertex(out, pose, light, x1, y1, z1, 1, 0, nx, ny, nz);
        vertex(out, pose, light, x2, y2, z2, 1, vScale, nx, ny, nz);
        vertex(out, pose, light, x3, y3, z3, 0, vScale, nx, ny, nz);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, int light, float x, float y, float z,
                               float u, float v, float nx, float ny, float nz) {
        out.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
    }

    @EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void layers(EntityRenderersEvent.AddLayers event) {
            for (PlayerSkin.Model skin : event.getSkins()) {
                if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new RegrowthLayer(renderer));
            }
        }
    }
}
