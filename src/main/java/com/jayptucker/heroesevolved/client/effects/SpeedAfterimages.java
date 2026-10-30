package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.HumanoidModel;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.*;

/** Local snapshots of skin and limb poses: no spawned entities, network spam, or live-player mutations. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class SpeedAfterimages {
    public static final int LIFETIME = 18, MAX_GHOSTS = 48;
    private record Ghost(UUID player, Vec3 position, float yaw, ResourceLocation skin, boolean slim,
                         PartPose[] poses, int clothes, long born) {}
    private static final Deque<Ghost> GHOSTS = new ArrayDeque<>();
    private static final Map<UUID, Vec3> PREVIOUS = new HashMap<>();
    private static ClientLevel world;
    private static PlayerModel<AbstractClientPlayer> wide, slim;
    private static MultiBufferSource.BufferSource buffers;
    private SpeedAfterimages() {}

    public static float opacity(float age) { return .42F * Math.clamp(1 - age / LIFETIME, 0, 1); }
    private static PlayerModel<AbstractClientPlayer> model(boolean narrow) {
        Minecraft mc = Minecraft.getInstance();
        if (wide == null) wide = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        if (slim == null) slim = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
        return narrow ? slim : wide;
    }
    private static ModelPart[] parts(PlayerModel<?> m) {
        return new ModelPart[]{m.head, m.hat, m.body, m.leftArm, m.rightArm, m.leftLeg, m.rightLeg,
                m.jacket, m.leftSleeve, m.rightSleeve, m.leftPants, m.rightPants};
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (world != mc.level) { world = mc.level; GHOSTS.clear(); PREVIOUS.clear(); wide = null; slim = null; }
        if (world == null || mc.player == null || mc.isPaused()) return;
        long now = world.getGameTime();
        GHOSTS.removeIf(g -> now - g.born >= LIFETIME || now < g.born || world.getPlayerByUUID(g.player) == null);
        Set<UUID> seen = new HashSet<>();
        int interval = mc.options.particles().get() == ParticleStatus.MINIMAL ? 6 : 3;
        for (AbstractClientPlayer p : world.players()) {
            if (p.distanceToSqr(mc.player) > 64 * 64) continue;
            seen.add(p.getUUID());
            Vec3 previous = PREVIOUS.put(p.getUUID(), p.position());
            var attribute = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (previous == null || now % interval != 0 || attribute == null || !attribute.hasModifier(SpeedService.STRIDE)
                    || p.isInvisible() || p.isSpectator() || !p.isAlive() || p.isPassenger()
                    || p.position().distanceToSqr(previous) < .025 || p.position().distanceToSqr(previous) > 64) continue;
            capture(p, now, p.position(), p.yBodyRot);
        }
        PREVIOUS.keySet().retainAll(seen);
    }
    /** The runner nearest {@code near} (within three blocks), if any. */
    private static AbstractClientPlayer runnerNear(Vec3 near) {
        if (world == null) return null;
        AbstractClientPlayer best = null;
        double bestDistance = 9;
        for (AbstractClientPlayer p : world.players()) {
            double d = p.position().distanceToSqr(near);
            if (d < bestDistance && !p.isInvisible() && !p.isSpectator()) { best = p; bestDistance = d; }
        }
        return best;
    }

    /** Afterimages strung along a burst of movement (or one, held in place, when {@code from} equals {@code to}). */
    public static void along(Vec3 from, Vec3 to, int count) {
        AbstractClientPlayer p = runnerNear(to);
        if (p == null || world == null) return;
        Vec3 delta = to.subtract(from);
        float yaw = delta.horizontalDistanceSqr() < 1.0E-4 ? p.yBodyRot : (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90;
        for (int i = 0; i < count; i++) capture(p, world.getGameTime(), count == 1 ? from : from.lerp(to, i / (double) count), yaw);
    }

    /** Afterimages of the runner spaced around a ring, each facing along it, as if caught mid-lap. */
    public static void ring(Vec3 center, double radius, int count, double phase) {
        AbstractClientPlayer p = runnerNear(center);
        if (p == null || world == null) return;
        for (int i = 0; i < count; i++) {
            double angle = phase + i * Math.PI * 2 / count;
            Vec3 at = center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            float yaw = (float) Math.toDegrees(angle);
            capture(p, world.getGameTime(), at, yaw);
        }
    }

    private static void capture(AbstractClientPlayer p, long now, Vec3 position, float yaw) {
        boolean narrow = p.getSkin().model() == PlayerSkin.Model.SLIM;
        var m = model(narrow);
        m.young = false; m.riding = false; m.crouching = p.isCrouching(); m.attackTime = 0; m.swimAmount = 0;
        m.leftArmPose = HumanoidModel.ArmPose.EMPTY; m.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        m.setupAnim(p, p.walkAnimation.position(), Math.max(.6F, p.walkAnimation.speed()), p.tickCount,
                Mth.wrapDegrees(p.yHeadRot - p.yBodyRot), p.getXRot());
        var parts = parts(m); PartPose[] poses = new PartPose[parts.length];
        for (int i = 0; i < parts.length; i++) poses[i] = parts[i].storePose();
        int clothes = 0;
        for (PlayerModelPart part : PlayerModelPart.values()) if (p.isModelPartShown(part)) clothes |= part.getMask();
        while (GHOSTS.size() >= MAX_GHOSTS) GHOSTS.removeFirst();
        GHOSTS.addLast(new Ghost(p.getUUID(), position, yaw, p.getSkin().texture(), narrow, poses, clothes, now));
    }
    public static void draw(PlayerModel<?> model, PoseStack pose, MultiBufferSource source, ResourceLocation skin, float alpha) {
        model.renderToBuffer(pose, source.getBuffer(RenderType.entityTranslucent(skin)), 0xF000F0,
                OverlayTexture.NO_OVERLAY, ((int) (Math.clamp(alpha, 0, 1) * 255) << 24) | 0xFFE9C0);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || world == null || world != mc.level || GHOSTS.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(256 * 1024));
        Vec3 camera = event.getCamera().getPosition();
        float fraction = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var visible = GHOSTS.stream().filter(g -> g.position.distanceToSqr(camera) < 64 * 64
                && event.getFrustum().isVisible(new AABB(g.position.add(-.8, 0, -.8), g.position.add(.8, 2.2, .8))))
                .sorted(Comparator.comparingDouble((Ghost g) -> g.position.distanceToSqr(camera)).reversed()).toList();
        for (Ghost g : visible) {
            // Do not put the local player's newest ghost directly across the first-person camera.
            if (mc.options.getCameraType().isFirstPerson() && mc.player != null && g.player.equals(mc.player.getUUID())
                    && g.position.distanceToSqr(mc.player.position()) < 1) continue;
            // A ghost on top of the camera (trailing a runner seen from behind) would only fill the view.
            boolean own = mc.player != null && g.player.equals(mc.player.getUUID());
            double clearance = own ? 3.4 : 1.8;
            if (g.position.add(0, 1, 0).distanceToSqr(camera) < clearance * clearance) continue;
            float alpha = opacity(world.getGameTime() - g.born + fraction);
            if (alpha <= 0) continue;
            var m = model(g.slim); var parts = parts(m);
            for (int i = 0; i < parts.length; i++) { parts[i].loadPose(g.poses[i]); parts[i].visible = true; }
            m.hat.visible = (g.clothes & PlayerModelPart.HAT.getMask()) != 0;
            m.jacket.visible = (g.clothes & PlayerModelPart.JACKET.getMask()) != 0;
            m.leftSleeve.visible = (g.clothes & PlayerModelPart.LEFT_SLEEVE.getMask()) != 0;
            m.rightSleeve.visible = (g.clothes & PlayerModelPart.RIGHT_SLEEVE.getMask()) != 0;
            m.leftPants.visible = (g.clothes & PlayerModelPart.LEFT_PANTS_LEG.getMask()) != 0;
            m.rightPants.visible = (g.clothes & PlayerModelPart.RIGHT_PANTS_LEG.getMask()) != 0;
            m.young = false; m.crouching = false;
            PoseStack pose = new PoseStack();
            // The stage already supplies the camera rotation in the shader view matrix.
            pose.translate(g.position.x - camera.x, g.position.y - camera.y, g.position.z - camera.z);
            pose.mulPose(Axis.YP.rotationDegrees(180 - g.yaw));
            pose.scale(-.9375F, -.9375F, .9375F); pose.translate(0, -1.501, 0);
            draw(m, pose, buffers, g.skin, alpha);
        }
        buffers.endBatch();
    }
}
