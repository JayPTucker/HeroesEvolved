package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.wither.WitherGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * The Wither's rolling shock: the real floor blocks rise in a ring that travels outward, each lifted by exactly the
 * height the server's hit uses ({@link WitherGeometry#lift}), and settle again behind the front. Drawn from the running
 * {@code WITHER_PULSE} pieces alone, so nothing is placed, removed or sent per block.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class WitherFloorWave {
    private WitherFloorWave() {}

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !level.dimension().equals(WitherGeometry.DIMENSION)) return;
        var waves = SetPieceClient.running(Piece.WITHER_PULSE);
        if (waves.isEmpty()) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        var blocks = mc.getBlockRenderer();
        for (var wave : waves) {
            float speed = (wave.seed() & 1023) / 100F;
            if (speed <= 0) speed = WitherGeometry.PULSE_SPEED;
            double radius = WitherGeometry.ripple(wave.age(partial), speed);
            if (radius > wave.scale() + WitherGeometry.WAVE_FRONT) continue;
            float fade = 1 - WitherGeometry.smooth(wave.scale() * .85F, wave.scale() + 1, (float) radius);
            Vec3 c = wave.origin();
            int floorY = (int) Math.floor(c.y - .5);
            double outer = radius + WitherGeometry.WAVE_FRONT, inner = Math.max(0, radius - WitherGeometry.WAVE_FRONT);
            int r = (int) Math.ceil(outer);
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                double bx = Math.floor(c.x) + x + .5, bz = Math.floor(c.z) + z + .5;
                double d = Math.sqrt((bx - c.x) * (bx - c.x) + (bz - c.z) * (bz - c.z));
                if (d > outer || d < inner) continue;
                float lift = WitherGeometry.lift(d - radius) * fade;
                if (lift < .04F) continue;
                BlockPos at = BlockPos.containing(bx, floorY, bz);
                BlockState state = level.getBlockState(at);
                if (state.isAir() || state.getRenderShape() != RenderShape.MODEL || !level.getBlockState(at.above()).isAir()) continue;
                // Tipped away from the center, as a slab of floor shoved up from beneath would be.
                double nx = (bx - c.x) / Math.max(d, .01), nz = (bz - c.z) / Math.max(d, .01);
                float tip = lift * 18 * (float) Math.signum(d - radius + .3);
                pose.pushPose();
                pose.translate(at.getX() - cam.x + .5, at.getY() - cam.y + lift, at.getZ() - cam.z + .5);
                pose.mulPose(Axis.of(new org.joml.Vector3f((float) nz, 0, (float) -nx)).rotationDegrees(tip));
                pose.translate(-.5, 0, -.5);
                blocks.renderSingleBlock(state, pose, buffers, LevelRenderer.getLightColor(level, at.above()), OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }
        buffers.endBatch();
    }
}
