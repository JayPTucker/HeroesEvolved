package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.BreachActorsPayload;
import com.jayptucker.heroesevolved.network.BreachViewPayload;
import com.jayptucker.heroesevolved.vibe.BreachSnapshot;
import com.jayptucker.heroesevolved.vibe.VibeGeometry;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The terrain on the other side of a breach, drawn through the opening.
 *
 * <p>The server sends each nearby player a {@link BreachSnapshot} of the blocks round a mouth's partner. It is
 * tessellated off the render thread into the game's own chunk layers (solid, cutout, translucent) with the turn
 * between the two mouths baked in, so the far side's ground lines up with this side's the way a traveler arrives.
 * Each frame, after entities, the opening is marked in the stencil buffer where it is visible, its depth is pushed to
 * the far plane and filled with the far side's sky, the far terrain is drawn inside it with the terrain shaders (lit
 * by the light the server sent, fogged into that sky at the edge of what was sent), and the opening's own depth is
 * put back so anything drawn later behind it stays hidden. Only the half of the far side ahead of the viewer (through
 * the mouth) is drawn, so a wall just behind the far mouth never blocks the view.</p>
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class BreachViewClient {
    /** Inside the iris's lip, so its moving rim always covers the window's edge. */
    private static final double INSET = .97;
    private static final double DRAW_RANGE = 96;
    private static final Map<Integer, View> VIEWS = new HashMap<>();
    private static @Nullable ClientLevel world;

    /** One built half of the far side: a vertex buffer per chunk layer. */
    private record Half(Map<RenderType, VertexBuffer> layers) {
        void close() { layers.values().forEach(VertexBuffer::close); }
    }

    /** Meshes built off the render thread, waiting to be uploaded. */
    private record Built(Map<RenderType, MeshData> front, Map<RenderType, MeshData> back, List<ByteBufferBuilder> memory) {
        void close() {
            front.values().forEach(MeshData::close);
            back.values().forEach(MeshData::close);
            memory.forEach(ByteBufferBuilder::close);
        }
    }

    private static final class View {
        final BreachViewPayload info;
        final long born;
        final Vec3 direction;
        final BreachViewActors actors = new BreachViewActors();
        @Nullable CompletableFuture<Built> building;
        @Nullable Half front, back;

        View(BreachViewPayload info, long born) {
            this.info = info;
            this.born = born;
            direction = info.normal();
        }

        void close() {
            if (front != null) front.close();
            if (back != null) back.close();
            front = back = null;
            if (building != null) building.thenAccept(Built::close);
            building = null;
        }
    }

    private BreachViewClient() {}

    public static void accept(BreachViewPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        View old = VIEWS.remove(payload.id());
        if (old != null) old.close();
        if (payload.snapshot() == null || mc.level == null) return;
        if (world != mc.level) clear();
        world = mc.level;
        View view = new View(payload, world.getGameTime());
        LevelLightEngine lightEngine = world.getLightEngine();
        view.building = CompletableFuture.supplyAsync(() -> build(payload, lightEngine), Util.backgroundExecutor());
        VIEWS.put(payload.id(), view);
    }

    /** True while the mouth at {@code center} shows the far side (the art then leaves its middle clear). */
    public static boolean showing(Vec3 center) {
        for (View view : VIEWS.values())
            if (view.front != null && view.info.center().distanceToSqr(center) < .09) return true;
        return false;
    }

    public static int count() { return VIEWS.size(); }

    public static int actorCount() { return VIEWS.values().stream().mapToInt(view -> view.actors.count()).sum(); }

    public static void acceptActors(BreachActorsPayload payload) {
        View view = VIEWS.get(payload.view());
        if (view != null && world != null && world == Minecraft.getInstance().level) view.actors.update(world, payload.actors());
    }

    private static void clear() {
        VIEWS.values().forEach(View::close);
        VIEWS.clear();
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        world = null;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != world) {
            clear();
            world = mc.level;
        }
        if (mc.level == null) return;
        // Between frames, so resizing the main target with a stencil attachment loses nothing on screen.
        RenderTarget target = mc.getMainRenderTarget();
        if (!VIEWS.isEmpty()) target.enableStencil();
        if (target.isStencilEnabled() && Minecraft.useShaderTransparency()) {
            // Fabulous blits depth from the main target. Both sides of each blit need the same depth format.
            enableStencil(mc.levelRenderer.getItemEntityTarget());
            enableStencil(mc.levelRenderer.getTranslucentTarget());
            enableStencil(mc.levelRenderer.getParticlesTarget());
        }
        long now = mc.level.getGameTime();
        VIEWS.values().removeIf(view -> {
            if (now - view.born <= view.info.ticks()) return false;
            view.close();
            return true;
        });
        for (View view : VIEWS.values()) {
            view.actors.tick(now);
            upload(view);
        }
    }

    private static void enableStencil(@Nullable RenderTarget target) {
        if (target != null) target.enableStencil();
    }

    private static void upload(View view) {
        if (view.building == null || !view.building.isDone()) return;
        Built built;
        try {
            built = view.building.join();
        } catch (RuntimeException failed) {
            HeroesEvolved.LOGGER.warn("A breach view failed to build", failed);
            view.building = null;
            return;
        }
        view.building = null;
        try {
            view.front = new Half(upload(built.front()));
            view.back = new Half(upload(built.back()));
        } finally {
            built.memory().forEach(ByteBufferBuilder::close);
        }
    }

    private static Map<RenderType, VertexBuffer> upload(Map<RenderType, MeshData> meshes) {
        Map<RenderType, VertexBuffer> out = new HashMap<>();
        for (var entry : meshes.entrySet()) {
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(entry.getValue());
            out.put(entry.getKey(), buffer);
        }
        VertexBuffer.unbind();
        return out;
    }

    // --- Building the far side ---

    private static Built build(BreachViewPayload info, LevelLightEngine lightEngine) {
        BreachSnapshot snapshot = info.snapshot();
        Getter getter = new Getter(snapshot, lightEngine);
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        RandomSource random = RandomSource.create(42);
        Matrix4f turn = new Matrix4f().rotation(VibeGeometry.rotation(info.farNormal(), info.farYaw(), info.normal(), info.yaw()));
        Matrix3f turnNormal = new Matrix3f(turn);
        Vec3 far = info.farCenter(), farDirection = info.farNormal();
        List<ByteBufferBuilder> memory = new ArrayList<>();
        Map<RenderType, BufferBuilder> front = new HashMap<>(), back = new HashMap<>();
        PoseStack pose = new PoseStack();
        Moved moved = new Moved();
        int[] states = snapshot.states();
        for (int i = 0; i < states.length; i++) {
            BlockState state = Block.stateById(states[i]);
            if (state.isAir()) continue;
            BlockPos pos = snapshot.at(i);
            FluidState fluid = state.getFluidState();
            if (fluid.isEmpty() && buried(getter, pos, state)) continue;
            // Which side of the far mouth this block is on. Blocks in the mouth's own plane go in both halves.
            double side = Vec3.atCenterOf(pos).subtract(far).dot(farDirection);
            for (int half = 0; half < 2; half++) {
                if (half == 0 ? side < -.35 : side > .35) continue;
                Map<RenderType, BufferBuilder> layers = half == 0 ? front : back;
                if (state.getRenderShape() == RenderShape.MODEL) {
                    var model = blocks.getBlockModel(state);
                    for (RenderType type : model.getRenderTypes(state, random, ModelData.EMPTY)) {
                        pose.setIdentity();
                        pose.mulPose(turn);
                        pose.translate(pos.getX() - far.x, pos.getY() - far.y, pos.getZ() - far.z);
                        blocks.renderBatched(state, pos, getter, pose, builder(layers, type, memory), true, random,
                                ModelData.EMPTY, type);
                    }
                }
                if (!fluid.isEmpty()) {
                    // The liquid renderer writes positions within the block's chunk section; move them back out.
                    moved.out = builder(layers, ItemBlockRenderTypes.getRenderLayer(fluid), memory);
                    moved.matrix = new Matrix4f(turn).translate((float) ((pos.getX() & ~15) - far.x),
                            (float) ((pos.getY() & ~15) - far.y), (float) ((pos.getZ() & ~15) - far.z));
                    moved.normal = turnNormal;
                    blocks.renderLiquid(pos, getter, moved, state, fluid);
                }
            }
        }
        return new Built(finish(front), finish(back), memory);
    }

    private static boolean buried(Getter getter, BlockPos pos, BlockState state) {
        if (!state.isSolidRender(getter, pos)) return false;
        for (Direction face : Direction.values()) {
            BlockPos neighbor = pos.relative(face);
            if (!getter.getBlockState(neighbor).isSolidRender(getter, neighbor)) return false;
        }
        return true;
    }

    private static BufferBuilder builder(Map<RenderType, BufferBuilder> layers, RenderType type, List<ByteBufferBuilder> memory) {
        return layers.computeIfAbsent(type, t -> {
            ByteBufferBuilder bytes = new ByteBufferBuilder(Math.min(t.bufferSize(), 32 * 1024));
            memory.add(bytes);
            return new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        });
    }

    private static Map<RenderType, MeshData> finish(Map<RenderType, BufferBuilder> layers) {
        Map<RenderType, MeshData> out = new HashMap<>();
        for (var entry : layers.entrySet()) {
            MeshData mesh = entry.getValue().build();
            if (mesh != null) out.put(entry.getKey(), mesh);
        }
        return out;
    }

    /** Applies a matrix to positions and normals written by a renderer that takes no pose (the liquid renderer). */
    private static final class Moved implements VertexConsumer {
        VertexConsumer out;
        Matrix4f matrix;
        Matrix3f normal;

        @Override public VertexConsumer addVertex(float x, float y, float z) {
            Vector4f at = matrix.transform(new Vector4f(x, y, z, 1));
            out.addVertex(at.x, at.y, at.z);
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { out.setColor(r, g, b, a); return this; }
        @Override public VertexConsumer setUv(float u, float v) { out.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { out.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { out.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) {
            Vector3f n = normal.transform(new Vector3f(x, y, z));
            out.setNormal(n.x, n.y, n.z);
            return this;
        }
    }

    /** The far side as a world the block renderers can read: its blocks, light and colours, nothing else. */
    private record Getter(BreachSnapshot snapshot, LevelLightEngine lightEngine) implements BlockAndTintGetter {
        @Override public float getShade(Direction direction, boolean shade) {
            if (!shade) return 1;
            return switch (direction) {
                case DOWN -> .5F;
                case UP -> 1;
                case NORTH, SOUTH -> .8F;
                default -> .6F;
            };
        }
        @Override public LevelLightEngine getLightEngine() { return lightEngine; }
        @Override public int getBrightness(LightLayer layer, BlockPos pos) {
            int light = snapshot.light(pos);
            return layer == LightLayer.SKY ? light >> 4 : light & 15;
        }
        @Override public int getRawBrightness(BlockPos pos, int darken) {
            int light = snapshot.light(pos);
            return Math.max((light >> 4) - darken, light & 15);
        }
        @Override public boolean canSeeSky(BlockPos pos) { return snapshot.light(pos) >> 4 == 15; }
        @Override public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            if (resolver == BiomeColors.FOLIAGE_COLOR_RESOLVER) return snapshot.foliage();
            if (resolver == BiomeColors.WATER_COLOR_RESOLVER) return snapshot.water();
            return snapshot.grass();
        }
        @Override public @Nullable BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public BlockState getBlockState(BlockPos pos) { return snapshot.state(pos); }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return 4064; }
        @Override public int getMinBuildHeight() { return -2032; }
    }

    // --- Drawing it ---

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || VIEWS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != world || !mc.getMainRenderTarget().isStencilEnabled()) return;
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long now = mc.level.getGameTime();
        List<View> order = new ArrayList<>(VIEWS.values());
        order.removeIf(view -> {
            Vec3 center = view.info.center();
            return view.front == null || center.distanceToSqr(camera) > DRAW_RANGE * DRAW_RANGE
                    || !event.getFrustum().isVisible(new AABB(center, center).inflate(VibeGeometry.PORTAL_HEIGHT));
        });
        if (order.isEmpty()) return;
        order.sort(Comparator.comparingDouble((View view) -> -view.info.center().distanceToSqr(camera)));
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        float[] fogColor = RenderSystem.getShaderFogColor().clone();
        FogShape fogShape = RenderSystem.getShaderFogShape();
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        try {
            for (View view : order) draw(mc, view, camera, now, partial);
        } finally {
            mc.getMainRenderTarget().bindWrite(false);
            GL11.glDepthRange(0, 1);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            RenderSystem.stencilMask(0xFF);
            RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogColor(fogColor[0], fogColor[1], fogColor[2], fogColor[3]);
            RenderSystem.setShaderFogShape(fogShape);
        }
    }

    private static void draw(Minecraft mc, View view, Vec3 camera, long now, float partial) {
        Vec3 center = view.info.center();
        double facing = camera.subtract(center).dot(view.direction);
        if (Math.abs(facing) < .03) return;
        // Looking along the mouth's facing, you see what lies ahead of the far mouth; from behind, what lies behind it.
        Half half = facing < 0 ? view.front : view.back;
        if (half == null) return;
        float open = smooth((now - view.born + partial) / 6F);
        double rx = VibeGeometry.PORTAL_WIDTH * (.025 + .975 * open * open) * INSET;
        double ry = VibeGeometry.PORTAL_HEIGHT * (.38 + .62 * open) * INSET;
        if (rx < .05) return;
        float phase = now % 24000 + partial;

        RenderSystem.stencilMask(0xFF);
        RenderSystem.clearStencil(0);
        RenderSystem.clear(GL11.GL_STENCIL_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();

        // 1. Mark the opening where nothing nearer hides it.
        RenderSystem.stencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.depthMask(false);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        ellipse(center, view.direction, rx, ry, phase, camera, -1);

        // 2. Inside it: the far side's sky, at the far plane, so the far terrain draws over it and this side's
        //    blocks behind the opening are gone.
        RenderSystem.stencilFunc(GL11.GL_EQUAL, 1, 0xFF);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        GL11.glDepthRange(1, 1);
        BreachSnapshot snapshot = view.info.snapshot();
        ellipse(center, view.direction, rx, ry, phase, camera, snapshot.sky());
        GL11.glDepthRange(0, 1);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);

        // 3. The far terrain, fogged into its sky where the snapshot ends.
        float[] sky = rgb(snapshot.sky());
        float edge = (float) camera.distanceTo(center) + BreachSnapshot.REACH - 3;
        RenderSystem.setShaderFogColor(sky[0], sky[1], sky[2], 1);
        RenderSystem.setShaderFogStart(Math.max(0, edge - 11));
        RenderSystem.setShaderFogEnd(edge);
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
        Vec3 offset = center.subtract(camera);
        for (RenderType type : RenderType.chunkBufferLayers()) {
            if (type == RenderType.translucent()) view.actors.draw(mc, view.info, camera, facing < 0, now, partial);
            VertexBuffer buffer = half.layers().get(type);
            if (buffer == null) continue;
            type.setupRenderState();
            // Fabulous normally sends translucent chunks to a separate target without this stencil.
            mc.getMainRenderTarget().bindWrite(false);
            ShaderInstance shader = RenderSystem.getShader();
            if (shader != null) {
                shader.setDefaultUniforms(VertexFormat.Mode.QUADS, RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(),
                        mc.getWindow());
                shader.apply();
                if (shader.CHUNK_OFFSET != null) {
                    shader.CHUNK_OFFSET.set((float) offset.x, (float) offset.y, (float) offset.z);
                    shader.CHUNK_OFFSET.upload();
                }
                buffer.bind();
                buffer.draw();
                if (shader.CHUNK_OFFSET != null) shader.CHUNK_OFFSET.set(0F, 0F, 0F);
                shader.clear();
            }
            type.clearRenderState();
        }
        mc.getMainRenderTarget().bindWrite(false);
        VertexBuffer.unbind();

        // 4. Put the opening's own depth back, so whatever is drawn later behind the breach stays behind it.
        RenderSystem.stencilFunc(GL11.GL_EQUAL, 1, 0xFF);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.disableCull();
        ellipse(center, view.direction, rx, ry, phase, camera, -1);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.colorMask(true, true, true, true);
    }

    /** A filled ellipse facing {@code normal}; {@code colour} -1 draws position only (for the stencil and depth). */
    private static void ellipse(Vec3 center, Vec3 normal, double rx, double ry, float phase, Vec3 camera, int colour) {
        Vec3 side = VibeGeometry.side(normal), up = side.cross(normal).normalize();
        boolean coloured = colour != -1;
        RenderSystem.setShader(coloured ? GameRenderer::getPositionColorShader : GameRenderer::getPositionShader);
        BufferBuilder out = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,
                coloured ? DefaultVertexFormat.POSITION_COLOR : DefaultVertexFormat.POSITION);
        Vec3 c = center.subtract(camera);
        int sides = 40;
        for (int i = 0; i < sides; i++) {
            float a = Mth.TWO_PI * i / sides, b = Mth.TWO_PI * (i + 1) / sides;
            double ar = VibeGeometry.lipScale(a, phase), br = VibeGeometry.lipScale(b, phase);
            Vec3 p = c.add(side.scale(Math.cos(a) * rx * ar)).add(up.scale(Math.sin(a) * ry * ar));
            Vec3 q = c.add(side.scale(Math.cos(b) * rx * br)).add(up.scale(Math.sin(b) * ry * br));
            for (Vec3 v : new Vec3[]{c, p, q}) {
                var vertex = out.addVertex((float) v.x, (float) v.y, (float) v.z);
                if (coloured) vertex.setColor(0xFF000000 | colour);
            }
        }
        MeshData mesh = out.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    private static float[] rgb(int colour) {
        return new float[]{(colour >> 16 & 255) / 255F, (colour >> 8 & 255) / 255F, (colour & 255) / 255F};
    }

    private static float smooth(float x) {
        float t = Mth.clamp(x, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
