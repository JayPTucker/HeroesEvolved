package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.effects.setpiece.Canvas;
import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient;
import com.jayptucker.heroesevolved.electro.ElectroGeometry;
import com.jayptucker.heroesevolved.electro.ShortCircuit;
import com.jayptucker.heroesevolved.styles.Piece;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Full-sized electrical facades, with the charged creeper's scrolling field on a frozen player mesh. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class ElectricalAfterimages {
    private static final ResourceLocation FIELD = ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper_armor.png");
    private record Snapshot(boolean slim, float yaw, PartPose[] pose) {}
    private static final Map<SetPieceClient.Active, Snapshot> SNAPSHOTS = new HashMap<>();
    private static ClientLevel world;
    private static MultiBufferSource.BufferSource buffers;
    private static PlayerModel<AbstractClientPlayer> wide, slim;

    private ElectricalAfterimages() {}

    private static PlayerModel<AbstractClientPlayer> shell(boolean narrow) {
        if (narrow && slim == null) slim = makeShell(true);
        if (!narrow && wide == null) wide = makeShell(false);
        return narrow ? slim : wide;
    }

    private static PlayerModel<AbstractClientPlayer> makeShell(boolean narrow) {
        return new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(.55F), narrow), 64, 64).bakeRoot(), narrow);
    }

    private static Snapshot capture(SetPieceClient.Active a) {
        var entity = a.entity() < 0 ? null : world.getEntity(a.entity());
        boolean narrow = entity instanceof AbstractClientPlayer p && p.getSkin().model() == PlayerSkin.Model.SLIM;
        var model = SpeedAfterimages.model(narrow);
        for (var part : SpeedAfterimages.parts(model)) part.resetPose();
        // Freeze a substantial, slightly open stance; the field never follows the dodging player's later turns.
        model.leftArm.zRot = -.12F;
        model.rightArm.zRot = .12F;
        model.leftLeg.xRot = -.12F;
        model.rightLeg.xRot = .12F;
        if (entity instanceof AbstractClientPlayer p) {
            model.head.xRot = p.getXRot() * Mth.DEG_TO_RAD;
            model.head.yRot = Mth.wrapDegrees(p.yHeadRot - p.yBodyRot) * Mth.DEG_TO_RAD;
        }
        var parts = SpeedAfterimages.parts(model);
        PartPose[] pose = new PartPose[parts.length];
        for (int i = 0; i < parts.length; i++) pose[i] = parts[i].storePose();
        float yaw = entity instanceof LivingEntity living ? living.yBodyRot
                : (float) Math.toDegrees(Math.atan2(-a.facing().x, a.facing().z));
        return new Snapshot(narrow, yaw, pose);
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { reset(null); }

    private static void reset(ClientLevel level) {
        world = level;
        SNAPSHOTS.clear();
        wide = slim = null;
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (world != mc.level) reset(mc.level);
        if (world == null) return;
        var pieces = new ArrayList<>(SetPieceClient.running(Piece.ELECTRO_ARC_STEP));
        pieces.addAll(SetPieceClient.running(Piece.ELECTRO_DECOY));
        pieces.addAll(SetPieceClient.running(Piece.FINISH_SHORT));
        SNAPSHOTS.keySet().retainAll(pieces);
        if (pieces.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(128 * 1024));
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        boolean minimal = mc.options.particles().get() == ParticleStatus.MINIMAL;
        for (var a : pieces) {
            float t = a.age(partial), alpha;
            float height = 1.875F;
            Vec3 feet = a.origin();
            if (a.piece() == Piece.ELECTRO_ARC_STEP) {
                if (a.scale() > 1.5F) continue; // Flashback has its own longer-lived facade.
                alpha = Canvas.fade(t, 9, .6F, 5);
            } else if (a.piece() == Piece.ELECTRO_DECOY) {
                alpha = Canvas.fade(t, ElectroGeometry.FLASHBACK_FUSE, .6F, 2);
            } else {
                if (a.scale() < .2F) continue;
                alpha = Canvas.fade(t, a.piece().ticks, 2, 4);
                if (a.scale() >= .5F) {
                    height = a.scale() >= ShortCircuit.TRAP ? a.scale() - ShortCircuit.TRAP : a.scale();
                    alpha *= Canvas.smooth(ShortCircuit.CRAWL, 11, t) * (1 - Canvas.smooth(ShortCircuit.LAND, ShortCircuit.LAND + 1, t));
                    var bound = a.entity() < 0 ? null : world.getEntity(a.entity());
                    if (bound != null) feet = bound.getPosition(partial);
                    feet = feet.add(0, 1.3 * Canvas.smooth(ShortCircuit.BLOW, ShortCircuit.LAND, t), 0);
                }
            }
            if (alpha <= .01F || feet.distanceToSqr(camera) > 64 * 64
                    || !event.getFrustum().isVisible(new AABB(feet.add(-.8, -.1, -.8), feet.add(.8, height + .2, .8)))) continue;
            // Fade the entire field near the camera, including in first person.
            alpha *= Canvas.smooth(.65F, 1.6F, (float) feet.add(0, height * .6, 0).distanceTo(camera));
            if (alpha <= .01F) continue;
            Snapshot snapshot = SNAPSHOTS.computeIfAbsent(a, ElectricalAfterimages::capture);
            var model = shell(snapshot.slim);
            model.young = false;
            model.riding = false;
            model.crouching = false;
            var parts = SpeedAfterimages.parts(model);
            for (int i = 0; i < parts.length; i++) { parts[i].loadPose(snapshot.pose[i]); parts[i].visible = i == 0 || i >= 2 && i <= 6; }
            PoseStack pose = new PoseStack();
            pose.translate(feet.x - camera.x, feet.y - camera.y, feet.z - camera.z);
            pose.mulPose(Axis.YP.rotationDegrees(180 - snapshot.yaw));
            float size = height / 2;
            pose.scale(-size, -size, size);
            pose.translate(0, -1.501, 0);
            float flicker = .86F + .14F * Mth.sin(t * 2.1F + Canvas.hash(a.seed(), 0) * Mth.TWO_PI);
            // A dim body gives the facade volume in daylight. The scrolling shells provide the electric surface.
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(FIELD)), 0xF000F0,
                    OverlayTexture.NO_OVERLAY, color(alpha * .17F, 0x3D52B8));
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.energySwirl(FIELD, t * .013F % 1, t * .023F % 1)), 0xF000F0,
                    OverlayTexture.NO_OVERLAY, color(alpha * flicker * .85F, 0x5588FF));
            if (!minimal) {
                pose.translate(0, -1.501 * .025, 0);
                pose.scale(1.025F, 1.025F, 1.025F);
                model.renderToBuffer(pose, buffers.getBuffer(RenderType.energySwirl(FIELD, -t * .009F % 1, t * .016F % 1 + .5F)), 0xF000F0,
                        OverlayTexture.NO_OVERLAY, color(alpha * flicker * .28F, 0xEAF5FF));
            }
        }
        buffers.endBatch();
    }

    private static int color(float alpha, int rgb) { return (int) (Mth.clamp(alpha, 0, 1) * 255) << 24 | rgb; }
}
