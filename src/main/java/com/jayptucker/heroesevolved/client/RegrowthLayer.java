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
        float regrowth = ClientRegrowth.shown(player.getId(), partialTick);
        if (regrowth > .04F && !player.isInvisible()) veins(pose, buffers, player, regrowth, ageInTicks);
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

    /**
     * Regrowth glowing in the veins, for anyone who can see her: dark crimson lines under the skin with a bright line in
     * them, down both arms from the shoulder, out across the chest from the heart and up the neck as it builds; more of
     * them and brighter the more she carries, gold at the top, and a lub-dub every second. Drawn in each part's own
     * model space (pixels), so they follow every pose and animation.
     */
    private void veins(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer player, float amount, float age) {
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
        int seed = player.getUUID().hashCode();
        float t = age % 20;
        float beat = Math.max(pulse(t, 1), .7F * pulse(t, 5));
        float glow = Mth.clamp(amount * 1.3F, 0, 1) * (.5F + .5F * beat);
        float[] core = {Mth.lerp(amount, 1F, 1F), Mth.lerp(amount, .25F, .85F), Mth.lerp(amount, .31F, .63F)};
        // One buffer at a time: asking the source for a second render type ends the first one's batch.
        veinPass(pose, buffers.getBuffer(RenderType.debugQuads()), false, model, slim, seed, amount, glow, core);
        veinPass(pose, buffers.getBuffer(RenderType.lightning()), true, model, slim, seed, amount, glow, core);
    }

    private void veinPass(PoseStack pose, VertexConsumer out, boolean bright, PlayerModel<AbstractClientPlayer> model, boolean slim,
                          int seed, float amount, float glow, float[] core) {
        VertexConsumer dark = bright ? null : out, light = bright ? out : null;
        // Both arms: one vein down the front and one down the outside, branching as Regrowth builds.
        for (int side = 0; side < 2; side++) {
            ModelPart arm = side == 0 ? model.rightArm : model.leftArm;
            if (!arm.visible) continue;
            float sign = side == 0 ? -1 : 1, half = slim ? 1.5F : 2F, cx = sign * (slim ? .5F : 1F);
            pose.pushPose();
            arm.translateAndRotate(pose);
            vein(dark, light, pose.last(), front(cx, -1.6F, -2.02F, 11F, 1.1F, seed + side * 31), 0, 0, -1, amount, glow, core, seed + side);
            vein(dark, light, pose.last(), outer(cx + sign * half + sign * .02F, -1.4F, 10.5F, 1.2F, seed + side * 57), sign, 0, 0, amount, glow, core, seed + side * 7);
            pose.popPose();
        }
        // The chest: out from the heart toward the shoulders and down the ribs.
        if (amount > .25F && model.body.visible) {
            pose.pushPose();
            model.body.translateAndRotate(pose);
            float reach = Mth.clamp((amount - .25F) / .5F, 0, 1);
            for (int i = 0; i < 4; i++) {
                float ang = (float) (i * Math.PI / 2 + .6 + hash(seed, i) * .5);
                float[][] pts = new float[5][];
                for (int k = 0; k < 5; k++) {
                    float u = k / 4F * reach;
                    pts[k] = new float[] {1.4F + Mth.cos(ang) * 4.2F * u + (hash(seed, i * 5 + k) - .5F) * .7F,
                            3.6F + Mth.sin(ang) * 4.6F * u, -2.02F};
                }
                vein(dark, light, pose.last(), pts, 0, 0, -1, amount, glow, core, seed + 100 + i);
            }
            pose.popPose();
        }
        // The neck, last of all.
        if (amount > .6F && model.head.visible) {
            pose.pushPose();
            model.head.translateAndRotate(pose);
            float reach = Mth.clamp((amount - .6F) / .3F, 0, 1);
            for (int s = -1; s <= 1; s += 2) {
                float[][] pts = {{s * 2.4F, 0, -3.2F}, {s * 2.7F, -1.2F * reach, -3.6F}, {s * 3.1F, -2.4F * reach, -4.02F}};
                vein(dark, light, pose.last(), pts, 0, 0, -1, amount, glow, core, seed + 200 + s);
            }
            pose.popPose();
        }
    }

    private static float pulse(float t, float at) { float d = t - at; return (float) Math.exp(-d * d / 2.5F); }

    private static float hash(int seed, int i) {
        int h = seed * 374761393 + i * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535F;
    }

    /** A vein wandering down a front face (constant z), with its length set by how much Regrowth she has. */
    private static float[][] front(float cx, float top, float z, float length, float wander, int seed) {
        float[][] pts = new float[7][];
        for (int k = 0; k < 7; k++) pts[k] = new float[] {cx + (hash(seed, k) - .5F) * wander, top + length * k / 6F, z};
        return pts;
    }

    /** A vein wandering down an outer side face (constant x). */
    private static float[][] outer(float x, float top, float length, float wander, int seed) {
        float[][] pts = new float[7][];
        for (int k = 0; k < 7; k++) pts[k] = new float[] {x, top + length * k / 6F, (hash(seed, k) - .5F) * wander * 2};
        return pts;
    }

    /**
     * One vein along these points (model pixels), drawn on the face whose normal is (nx, ny, nz): how far along it shows
     * follows {@code amount}, with two short branches off it; a dark line under the skin, a bright one in it.
     */
    private static void vein(VertexConsumer dark, VertexConsumer light, PoseStack.Pose pose, float[][] pts, float nx, float ny, float nz,
                             float amount, float glow, float[] core, int seed) {
        int shown = Math.max(2, Math.round(pts.length * Mth.clamp(amount * 1.6F, 0, 1)));
        for (int k = 0; k + 1 < shown; k++) {
            strip(dark, light, pose, pts[k], pts[k + 1], nx, ny, nz, glow, core, k == shown - 2 ? .6F : 1);
            if (k % 2 == 1 && amount > .35F) {
                float[] a = pts[k], b = pts[k + 1];
                float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
                float s = hash(seed, k) > .5F ? 1 : -1;
                // A branch leaves at a slant across the face.
                float[] end = nz != 0 ? new float[] {a[0] + s * 1.3F + dx * .3F, a[1] + dy * .6F, a[2]}
                        : new float[] {a[0], a[1] + dy * .6F, a[2] + s * 1.3F + dz * .3F};
                strip(dark, light, pose, a, end, nx, ny, nz, glow * .8F, core, .55F);
            }
        }
    }

    private static void strip(VertexConsumer dark, VertexConsumer light, PoseStack.Pose pose, float[] a, float[] b,
                              float nx, float ny, float nz, float glow, float[] core, float thin) {
        float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        // Across the segment, in the face's plane.
        float px = dy * nz - dz * ny, py = dz * nx - dx * nz, pz = dx * ny - dy * nx;
        float len = (float) Math.sqrt(px * px + py * py + pz * pz);
        if (len < 1e-4F) return;
        px /= len; py /= len; pz /= len;
        // Solid pass: the dark vein, then its bright line lifted just off it so the two never fight for depth.
        if (dark != null) {
            quadStrip(dark, pose, a, b, px, py, pz, nx, ny, nz, 0, .34F * thin, .26F, .02F, .05F, .8F * Math.min(1, glow + .45F));
            quadStrip(dark, pose, a, b, px, py, pz, nx, ny, nz, .03F, .13F * thin, core[0], core[1], core[2], .95F * Math.min(1, glow + .35F));
        }
        // Glow pass: a soft additive halo around the line, brighter on each beat.
        if (light != null) quadStrip(light, pose, a, b, px, py, pz, nx, ny, nz, .05F, .65F * thin, core[0], core[1] * .6F, core[2] * .6F, .4F * glow);
    }

    private static void quadStrip(VertexConsumer out, PoseStack.Pose pose, float[] a, float[] b, float px, float py, float pz,
                                  float nx, float ny, float nz, float lift, float half, float r, float g, float bl, float alpha) {
        if (alpha < .01F) return;
        int ar = (int) (r * 255), ag = (int) (g * 255), ab = (int) (bl * 255), aa = (int) (Mth.clamp(alpha, 0, 1) * 255);
        float ox = nx * lift, oy = ny * lift, oz = nz * lift;
        out.addVertex(pose, (a[0] + ox - px * half) / 16, (a[1] + oy - py * half) / 16, (a[2] + oz - pz * half) / 16).setColor(ar, ag, ab, aa);
        out.addVertex(pose, (b[0] + ox - px * half) / 16, (b[1] + oy - py * half) / 16, (b[2] + oz - pz * half) / 16).setColor(ar, ag, ab, aa);
        out.addVertex(pose, (b[0] + ox + px * half) / 16, (b[1] + oy + py * half) / 16, (b[2] + oz + pz * half) / 16).setColor(ar, ag, ab, aa);
        out.addVertex(pose, (a[0] + ox + px * half) / 16, (a[1] + oy + py * half) / 16, (a[2] + oz + pz * half) / 16).setColor(ar, ag, ab, aa);
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
