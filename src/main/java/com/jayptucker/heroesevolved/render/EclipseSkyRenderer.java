package com.jayptucker.heroesevolved.render;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.data.EclipseTimeline;
import com.jayptucker.heroesevolved.data.EclipseTimeline.Phase;
import com.jayptucker.heroesevolved.network.ClientEclipseState;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

/**
 * Draws the new moon crossing the vanilla Overworld sun. Everything is placed in the sun's own
 * frame (the same rotation vanilla uses), so the moon tracks the sun as it moves across the sky.
 * Like vanilla's sun and moon, the moon is a square, and so is the light around it: at totality
 * the black square is framed by a pearly corona and pink prominences, with a "diamond ring"
 * flare as the last and first sliver of sun disappears and returns.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class EclipseSkyRenderer {
    /** Vanilla's sun quad sits 100 blocks out with a bright core 7.5 units from centre to edge. */
    private static final float SKY_DISTANCE = 100.0F;
    private static final float SUN_CORE_HALF_SIZE = 7.5F;
    /** A little larger than the sun's square core, so it hides all of it: a total, not annular, eclipse. */
    private static final float MOON_HALF_SIZE = 9.0F;
    /** The moon's path across the sun, a shallow diagonal. */
    private static final float PATH_X = 0.8F;
    private static final float PATH_Z = 0.6F;
    /** Centre-to-centre distance at first and last contact, when the leading edges of the two squares meet. */
    private static final float CONTACT_DISTANCE = (MOON_HALF_SIZE + SUN_CORE_HALF_SIZE) / Math.max(PATH_X, PATH_Z);
    private static final int SEGMENTS = 48;
    private static final int STREAMERS = 14;
    private static final float FOG_AT_TOTALITY = 0.12F;

    private EclipseSkyRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || minecraft.level == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = ClientEclipseState.now(partialTick);
        EclipseTimeline timeline = ClientEclipseState.timeline();
        Phase phase = now < 0 ? Phase.NONE : timeline.phaseAt(now);
        // Rain hides the sun in vanilla; it hides the moon in front of it too.
        float visibility = 1.0F - minecraft.level.getRainLevel(partialTick);

        if (phase == Phase.NONE || visibility <= 0.0F
                || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(event.getModelViewMatrix());
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(minecraft.level.getTimeOfDay(partialTick) * 360.0F));
        Matrix4f matrix = poseStack.last().pose();

        float travel = timeline.moonTravel(now) * CONTACT_DISTANCE;
        float moonX = PATH_X * travel;
        float moonZ = PATH_Z * travel;
        float coverage = timeline.coverage(now);
        boolean totality = phase == Phase.TOTALITY;

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // Additive light first: corona and prominences only show once the sun is nearly gone.
        float corona = (totality ? 1.0F : (float) Math.pow(coverage, 16) * 0.5F) * visibility;
        if (corona > 0.01F) {
            additive();
            ring(matrix, moonX, moonZ, MOON_HALF_SIZE * 0.98F, MOON_HALF_SIZE * 2.7F,
                    0.92F, 0.95F, 1.0F, 0.55F * corona);
            streamers(matrix, moonX, moonZ, (float) now, 0.35F * corona);
            ring(matrix, moonX, moonZ, MOON_HALF_SIZE * 0.98F, MOON_HALF_SIZE * 1.1F,
                    1.0F, 0.35F, 0.45F, (0.3F + 0.4F * timeline.diamondRing(now)) * corona);
        }

        // The moon itself: slate grey against the blue sky while far off, true black at totality.
        RenderSystem.defaultBlendFunc();
        float shade = 1.0F - coverage;
        square(matrix, moonX, moonZ, MOON_HALF_SIZE,
                0.1F * shade, 0.12F * shade, 0.01F + 0.15F * shade,
                Mth.lerp(coverage, 0.9F, 1.0F) * visibility);

        float diamond = timeline.diamondRing(now) * visibility;
        if (diamond > 0.01F) {
            additive();
            diamondRing(matrix, moonX, moonZ, diamond);
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** Fog fades with the sky so the horizon does not glow while the stars are out. */
    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float darkness = ClientEclipseState.darkness((float) event.getPartialTick());

        if (darkness <= 0.0F) {
            return;
        }

        float scale = Mth.lerp(darkness, 1.0F, FOG_AT_TOTALITY);
        event.setRed(event.getRed() * scale);
        event.setGreen(event.getGreen() * scale);
        event.setBlue(event.getBlue() * scale);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientEclipseState.clear();
    }

    private static void additive() {
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
    }

    /** A flat square, axis-aligned with the sun's own square. */
    private static void square(Matrix4f matrix, float x, float z, float halfSize,
                               float red, float green, float blue, float alpha) {
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.addVertex(matrix, x - halfSize, SKY_DISTANCE, z - halfSize).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, x + halfSize, SKY_DISTANCE, z - halfSize).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, x + halfSize, SKY_DISTANCE, z + halfSize).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, x - halfSize, SKY_DISTANCE, z + halfSize).setColor(red, green, blue, alpha);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    /** Distance from the centre of a square of half-size 1 to its edge, in the direction of {@code angle}. */
    private static float squareEdge(float angle) {
        return 1.0F / Math.max(Math.abs(Mth.cos(angle)), Math.abs(Mth.sin(angle)));
    }

    /** A soft square frame fading from {@code alpha} at the inner edge to nothing at the outer edge. */
    private static void ring(Matrix4f matrix, float x, float z, float inner, float outer,
                             float red, float green, float blue, float alpha) {
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        // SEGMENTS is a multiple of 8, so a vertex lands exactly on every corner.
        for (int segment = 0; segment <= SEGMENTS; segment++) {
            float angle = segment * Mth.TWO_PI / SEGMENTS;
            float edge = squareEdge(angle);
            float cos = Mth.cos(angle) * edge;
            float sin = Mth.sin(angle) * edge;
            buffer.addVertex(matrix, x + cos * inner, SKY_DISTANCE, z + sin * inner)
                    .setColor(red, green, blue, alpha);
            buffer.addVertex(matrix, x + cos * outer, SKY_DISTANCE, z + sin * outer)
                    .setColor(red, green, blue, 0.0F);
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    /** Uneven coronal streamers, turning almost imperceptibly. */
    private static void streamers(Matrix4f matrix, float x, float z, float time, float alpha) {
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float turn = time * 0.0004F;

        for (int streamer = 0; streamer < STREAMERS; streamer++) {
            // A fixed per-streamer hash keeps the shape stable from frame to frame.
            float hash = Mth.frac(Mth.sin(streamer * 12.9898F) * 43758.547F);
            float angle = streamer * Mth.TWO_PI / STREAMERS + turn + hash * 0.4F;
            float half = 0.07F + hash * 0.08F;
            // Each streamer rises from the square's edge, so the ones off the corners reach furthest.
            float length = MOON_HALF_SIZE * squareEdge(angle) * (1.7F + 1.8F * hash);
            float before = MOON_HALF_SIZE * 0.98F * squareEdge(angle - half);
            float after = MOON_HALF_SIZE * 0.98F * squareEdge(angle + half);
            buffer.addVertex(matrix, x + Mth.cos(angle - half) * before, SKY_DISTANCE, z + Mth.sin(angle - half) * before)
                    .setColor(0.9F, 0.93F, 1.0F, alpha);
            buffer.addVertex(matrix, x + Mth.cos(angle + half) * after, SKY_DISTANCE, z + Mth.sin(angle + half) * after)
                    .setColor(0.9F, 0.93F, 1.0F, alpha);
            buffer.addVertex(matrix, x + Mth.cos(angle) * length, SKY_DISTANCE, z + Mth.sin(angle) * length)
                    .setColor(0.9F, 0.93F, 1.0F, 0.0F);
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    /**
     * One blazing bead on the moon's rim where the sun is about to vanish, or just returned: the
     * middle of the moon's edge nearest the sun's centre, where the last strip of the square sun shows.
     */
    private static void diamondRing(Matrix4f matrix, float moonX, float moonZ, float strength) {
        // The sun's centre is the origin. Mid-totality, where the offset is zero, never reaches here.
        float side = moonX == 0.0F ? -1.0F : Math.signum(moonX);
        float beadX = moonX - side * MOON_HALF_SIZE;
        float beadZ = 0.0F;

        ring(matrix, beadX, beadZ, 0.0F, 6.0F, 1.0F, 0.98F, 0.9F, strength);
        square(matrix, beadX, beadZ, 1.2F, 1.0F, 1.0F, 0.95F, strength);

        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int ray = 0; ray < 4; ray++) {
            float angle = ray * Mth.HALF_PI + Mth.PI / 4.0F;
            float cos = Mth.cos(angle);
            float sin = Mth.sin(angle);
            float rayLength = 16.0F * strength;
            buffer.addVertex(matrix, beadX - sin * 0.35F, SKY_DISTANCE, beadZ + cos * 0.35F)
                    .setColor(1.0F, 1.0F, 0.95F, strength);
            buffer.addVertex(matrix, beadX + sin * 0.35F, SKY_DISTANCE, beadZ - cos * 0.35F)
                    .setColor(1.0F, 1.0F, 0.95F, strength);
            buffer.addVertex(matrix, beadX + cos * rayLength, SKY_DISTANCE, beadZ + sin * rayLength)
                    .setColor(1.0F, 1.0F, 0.95F, 0.0F);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }
}
