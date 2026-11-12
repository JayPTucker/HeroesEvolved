package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.OpticalStatePayload;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.util.ArrayDeque;
import java.util.Deque;

/** The optical cloak is skin refracted into separated layers, not a continuous enemy outline. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class OpticalClient {
    private record Projection(AbstractClientPlayer actor, Vec3 position, long born, boolean appearing, boolean ripple) {}
    private static final Deque<Projection> PROJECTIONS = new ArrayDeque<>();
    private static OpticalStatePayload state = new OpticalStatePayload(false, false, 0, false, false);
    private static ClientLevel world;
    private static long seen;
    private static MultiBufferSource.BufferSource buffers;
    private OpticalClient() {}

    public static void update(OpticalStatePayload payload) {
        var mc = Minecraft.getInstance();
        worldChanged(mc.level);
        state = payload;
        seen = mc.level == null ? 0 : mc.level.getGameTime();
    }
    public static boolean concealed() {
        var mc = Minecraft.getInstance();
        return mc.level != null && state.faded() && state.unseen() && mc.level.getGameTime() - seen < 25;
    }
    public static void transition(int id, boolean hidden) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(id) instanceof AbstractClientPlayer actor)) return;
        worldChanged(mc.level);
        while (PROJECTIONS.size() >= 24) PROJECTIONS.removeFirst();
        PROJECTIONS.addLast(new Projection(actor, actor.position(), world.getGameTime(), !hidden, false));
    }
    /** A short pulse is a last-known-position hint, never a permanently tracked outline. */
    public static void ripple(Vec3 near) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        worldChanged(mc.level);
        AbstractClientPlayer closest = null;
        double distance = 4;
        for (var player : mc.level.players()) {
            if (!UnseenClient.hidden(player.getId())) continue;
            double d = player.position().distanceToSqr(near);
            if (d < distance) { closest = player; distance = d; }
        }
        if (closest == null) return;
        while (PROJECTIONS.size() >= 24) PROJECTIONS.removeFirst();
        PROJECTIONS.addLast(new Projection(closest, closest.position(), world.getGameTime(), false, true));
    }
    private static void worldChanged(ClientLevel next) {
        if (world == next) return;
        world = next; PROJECTIONS.clear(); OpticalSkinRenderer.clear();
        state = new OpticalStatePayload(false, false, 0, false, false); seen = 0;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        worldChanged(mc.level);
        if (world == null) { state = new OpticalStatePayload(false, false, 0, false, false); return; }
        PROJECTIONS.removeIf(p -> world.getGameTime() - p.born > (p.ripple ? 7 : 15));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        var mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || mc.level == null || mc.player == null) return;
        worldChanged(mc.level);
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(128 * 1024));
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        boolean detail = mc.options.particles().get() != ParticleStatus.MINIMAL;
        for (Projection projection : PROJECTIONS) {
            float age = world.getGameTime() - projection.born + partial;
            float duration = projection.ripple ? 7 : 15;
            float progress = Math.clamp(age / duration, 0, 1);
            boolean ownFirst = projection.actor == mc.player && mc.options.getCameraType().isFirstPerson();
            if (ownFirst || projection.position.distanceToSqr(camera) > 64 * 64) continue;
            Vec3 at = projection.ripple ? projection.position : projection.actor.getPosition(partial);
            float alpha = projection.ripple ? .16F * (1 - progress) : .5F * (1 - progress);
            float spread = projection.appearing ? (1 - progress) * .22F : progress * .26F;
            drawProjection(projection.actor, at, camera, partial, alpha, spread, detail);
        }
        // The cloaked player sees a restrained, glass-like outline of their own body in third person.
        if (concealed() && !mc.options.getCameraType().isFirstPerson())
            drawProjection(mc.player, mc.player.getPosition(partial), camera, partial, .065F, .018F, detail);
        buffers.endBatch();
    }
    private static void drawProjection(AbstractClientPlayer actor, Vec3 at, Vec3 camera, float partial,
                                       float alpha, float spread, boolean detail) {
        if (alpha <= .003F) return;
        PoseStack pose = new PoseStack();
        pose.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
        Vec3 side = Vec3.directionFromRotation(0, actor.getYRot() - 90);
        int layers = detail ? 3 : 1;
        for (int layer = 0; layer < layers; layer++) {
            float offset = layers == 1 ? 0 : layer - 1;
            pose.pushPose();
            pose.translate(side.x * offset * spread, Math.abs(offset) * spread * .15, side.z * offset * spread);
            OpticalSkinRenderer.draw(actor, pose, buffers, partial, alpha * (layer == 1 || layers == 1 ? .8F : .42F),
                    layer == 0 ? 0x8CCEF0 : layer == 2 ? 0xC9AEFF : 0xE7F8FF);
            pose.popPose();
        }
        pose.pushPose();
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-actor.getYRot()));
        var lines = buffers.getBuffer(RenderType.lines());
        contour(lines, pose, -.23, 1.40, -.22, .23, 1.86, .22, alpha * .8F);
        contour(lines, pose, -.25, .75, -.13, .25, 1.40, .13, alpha * .65F);
        contour(lines, pose, -.47, .79, -.12, -.26, 1.39, .12, alpha * .55F);
        contour(lines, pose, .26, .79, -.12, .47, 1.39, .12, alpha * .55F);
        contour(lines, pose, -.23, .04, -.12, -.01, .75, .12, alpha * .55F);
        contour(lines, pose, .01, .04, -.12, .23, .75, .12, alpha * .55F);
        pose.popPose();
    }
    private static void contour(VertexConsumer out, PoseStack pose, double x0, double y0, double z0,
                                double x1, double y1, double z1, float alpha) {
        Vec3[] v = {new Vec3(x0,y0,z0),new Vec3(x1,y0,z0),new Vec3(x1,y1,z0),new Vec3(x0,y1,z0),
                new Vec3(x0,y0,z1),new Vec3(x1,y0,z1),new Vec3(x1,y1,z1),new Vec3(x0,y1,z1)};
        int[] edges = {0,1,1,2,2,3,3,0,4,5,5,6,6,7,7,4,0,4,1,5,2,6,3,7};
        for (int i=0;i<edges.length;i+=2) {
            Vec3 a=v[edges[i]],b=v[edges[i+1]],n=b.subtract(a).normalize();
            for(Vec3 p:new Vec3[]{a,b})out.addVertex(pose.last().pose(),(float)p.x,(float)p.y,(float)p.z)
                    .setColor(.70F,.89F,1,alpha).setNormal(pose.last(),(float)n.x,(float)n.y,(float)n.z);
        }
    }

    /** Suppress opaque held items while concealed, retaining two faint, low translucent hands. */
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void hands(RenderHandEvent event) {
        var mc = Minecraft.getInstance();
        if (!concealed() || mc.player == null) return;
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        var renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(mc.player);
        var pose = event.getPoseStack();
        for (HumanoidArm side : HumanoidArm.values()) {
            var arm = side == HumanoidArm.RIGHT ? renderer.getModel().rightArm : renderer.getModel().leftArm;
            var original = arm.storePose();
            pose.pushPose();
            try {
                float sign = side == HumanoidArm.RIGHT ? 1 : -1;
                var gesture = com.jayptucker.heroesevolved.network.ClientArmPoseState.get(mc.player.getId());
                if (gesture == com.jayptucker.heroesevolved.ability.ArmPose.OPTICAL_SPLIT
                        || gesture == com.jayptucker.heroesevolved.ability.ArmPose.OPTICAL_VEIL
                        || gesture == com.jayptucker.heroesevolved.ability.ArmPose.OPTICAL_DISPERSE
                        || gesture != null && gesture.name().startsWith("FORCE_")) {
                    FirstPersonArmGeometry.pose(pose, side, gesture, mc.player.tickCount,
                            com.jayptucker.heroesevolved.network.ClientArmPoseState.ageTicks(mc.player.getId()));
                } else {
                    pose.translate(sign * .48, -.46, -.36);
                    pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(sign * 9));
                    pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-87));
                }
                arm.setPos(0,0,0); arm.setRotation(0,0,0);
                arm.render(pose, event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(mc.player.getSkin().texture())),
                        event.getPackedLight(), OverlayTexture.NO_OVERLAY, 0x2DC7E9FF);
            } finally { arm.loadPose(original); pose.popPose(); }
        }
    }
    private static void hud(GuiGraphics gui, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || !state.faded()
                || mc.level.getGameTime() - seen >= 25) return;
        int x=gui.guiWidth()/2,y=gui.guiHeight()-94;
        int color=state.exposure()>=70?0xFFF0B58A:0xFFAEDDF2;
        gui.drawCenteredString(mc.font, Component.translatable("invisibility.heroesevolved.exposure",state.exposure()),x,y-11,color);
        gui.fill(x-61,y,x+61,y+5,0xA01D2230);
        gui.fill(x-60,y+1,x-60+Math.clamp(state.exposure(),0,100)*120/100,y+4,color);
        gui.drawCenteredString(mc.font,Component.translatable(!state.unseen()?"invisibility.heroesevolved.optical_revealed"
                :state.exposure()>=35?"invisibility.heroesevolved.optical_flicker":"invisibility.heroesevolved.optical_hidden"),x,y-24,color);
        if(state.veil()||state.ghost())gui.drawCenteredString(mc.font,Component.translatable(state.ghost()
                ?"invisibility.heroesevolved.optical_ghost":"invisibility.heroesevolved.optical_veil"),x,y-36,0xC6B2E8);
    }
    @EventBusSubscriber(modid=HeroesEvolved.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Hud {
        @SubscribeEvent public static void register(RegisterGuiLayersEvent event) {
            event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID,"optical_exposure"), OpticalClient::hud);
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        PROJECTIONS.clear(); state = new OpticalStatePayload(false,false,0,false,false); world=null;
    }
}
