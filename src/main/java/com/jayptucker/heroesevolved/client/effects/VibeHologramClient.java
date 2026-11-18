package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.VibeHologramPayload;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A viber who appears in a vision ({@code vibe/VibeVisions}): their own skin, translucent and cold blue, glowing in the
 * dark, flickering like a bad signal, standing where they watch from and turning as they look. Nothing else of them is
 * here (they are a spectator), so it is drawn from {@link VibeHologramPayload} alone, eased between updates.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class VibeHologramClient {
    private static final class Ghost {
        Vec3 from, to;
        float yawFrom, yawTo, pitchFrom, pitchTo;
        long seen, arrived;

        Ghost(Vec3 at, float yaw, float pitch, long now) {
            from = to = at;
            yawFrom = yawTo = yaw;
            pitchFrom = pitchTo = pitch;
            seen = arrived = now;
        }
    }

    private static final Map<UUID, Ghost> GHOSTS = new HashMap<>();
    /** The charged creeper's sheen, drawn as a scrolling band of light over the figure. */
    private static final net.minecraft.resources.ResourceLocation SHEEN =
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper_armor.png");
    private static MultiBufferSource.BufferSource buffers;

    private VibeHologramClient() {}

    public static void receive(VibeHologramPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || payload.owner().equals(mc.player.getUUID())) return;
        long now = mc.level.getGameTime();
        if (!payload.shown()) {
            GHOSTS.remove(payload.owner());
            return;
        }
        Ghost ghost = GHOSTS.get(payload.owner());
        if (ghost == null) {
            GHOSTS.put(payload.owner(), new Ghost(payload.at(), payload.yaw(), payload.pitch(), now));
            return;
        }
        ghost.from = position(ghost, now);
        ghost.yawFrom = Mth.rotLerp(progress(ghost, now), ghost.yawFrom, ghost.yawTo);
        ghost.pitchFrom = Mth.lerp(progress(ghost, now), ghost.pitchFrom, ghost.pitchTo);
        ghost.to = payload.at();
        ghost.yawTo = payload.yaw();
        ghost.pitchTo = payload.pitch();
        ghost.seen = now;
    }

    private static float progress(Ghost ghost, float now) { return Mth.clamp((now - ghost.seen) / 2F, 0, 1); }

    private static Vec3 position(Ghost ghost, float now) { return ghost.from.lerp(ghost.to, progress(ghost, now)); }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            GHOSTS.clear();
            return;
        }
        long now = mc.level.getGameTime();
        // A lost "gone" must never leave a hologram standing.
        GHOSTS.values().removeIf(ghost -> now - ghost.seen > 40 || now < ghost.seen - 40);
    }

    @SubscribeEvent public static void draw(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || GHOSTS.isEmpty() || mc.level == null
                || mc.getConnection() == null) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(64 * 1024));
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float now = mc.level.getGameTime() + partial;
        Vec3 camera = event.getCamera().getPosition();
        for (var entry : GHOSTS.entrySet()) {
            Ghost ghost = entry.getValue();
            Vec3 at = position(ghost, now);
            if (at.distanceToSqr(camera) > 96 * 96 || !event.getFrustum().isVisible(new AABB(at.add(-.6, 0, -.6), at.add(.6, 2, .6)))) continue;
            var info = mc.getConnection().getPlayerInfo(entry.getKey());
            PlayerSkin skin = info != null ? info.getSkin() : DefaultPlayerSkin.get(entry.getKey());
            var model = SpeedAfterimages.model(skin.model() == PlayerSkin.Model.SLIM);
            for (var part : SpeedAfterimages.parts(model)) {
                part.resetPose();
                part.visible = true;
            }
            float yaw = Mth.rotLerp(progress(ghost, now), ghost.yawFrom, ghost.yawTo);
            float pitch = Mth.lerp(progress(ghost, now), ghost.pitchFrom, ghost.pitchTo);
            // Standing, arms loose and breathing a little; the head follows their look.
            model.head.xRot = pitch * Mth.DEG_TO_RAD;
            model.hat.copyFrom(model.head);
            float sway = Mth.sin(now * .08F) * .04F;
            model.rightArm.zRot = .06F + sway;
            model.leftArm.zRot = -.06F - sway;
            model.rightSleeve.copyFrom(model.rightArm);
            model.leftSleeve.copyFrom(model.leftArm);
            // A bad signal: the whole figure dims in uneven pulses, and now and then drops out for a frame.
            float flicker = .55F + .2F * Mth.sin(now * .9F) + .12F * Mth.sin(now * 2.3F + 1);
            if (hash((int) now + entry.getKey().hashCode()) < .06F) flicker *= .25F;
            float arrive = Mth.clamp((now - ghost.arrived) / 10F, 0, 1);
            int alpha = Mth.clamp((int) (flicker * arrive * 200), 0, 255);
            PoseStack pose = new PoseStack();
            pose.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
            pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
            pose.scale(-.9375F, -.9375F, .9375F);
            pose.translate(0, -1.501, 0);
            int light = 0xF000F0;
            // A projection, not a person: the skin washed cold blue and see-through, a scrolling sheen of light over it,
            // and a second, offset copy a hair to one side, because the image doesn't quite hold together.
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(skin.texture())), light,
                    OverlayTexture.NO_OVERLAY, alpha << 24 | 0x4C9CFF);
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.energySwirl(SHEEN, now * .006F % 1, now * .021F % 1)), light,
                    OverlayTexture.NO_OVERLAY, (int) (alpha * .8F) << 24 | 0x3A8CFF);
            pose.translate(.035 * Mth.sin(now * 1.7F), 0, 0);
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(skin.texture())), light,
                    OverlayTexture.NO_OVERLAY, (alpha / 3) << 24 | 0xBDE8FF);
        }
        buffers.endBatch();
    }

    private static float hash(int i) {
        int x = i * 0x27D4EB2D ^ 0x5BD1E995;
        x ^= x >>> 15;
        x *= 0x2C1B3C6D;
        x ^= x >>> 12;
        return (x & 0xFFFFFF) / (float) 0x1000000;
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { GHOSTS.clear(); }
}
