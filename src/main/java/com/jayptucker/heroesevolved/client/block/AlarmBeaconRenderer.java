package com.jayptucker.heroesevolved.client.block;

import com.jayptucker.heroesevolved.story.bunker.AlarmBeaconBlock;
import com.jayptucker.heroesevolved.story.bunker.AlarmBeaconBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The light of an alarm beacon. A mirror turns behind the lamp and throws two opposite beams around the
 * room: each is a soft red cone that fades with distance, lays a moving patch of light on whatever it
 * reaches, and flares in the viewer's eyes as it swings past them. Everything is additive light, drawn
 * without writing depth, so beams overlap and pass through each other cleanly; walls still hide them.
 */
public final class AlarmBeaconRenderer implements BlockEntityRenderer<AlarmBeaconBlockEntity> {
    /** Ticks per turn of the mirror. With two beams, anyone in the room sees a flash every 0.7 seconds. */
    private static final float TURN_TICKS = 28;
    /** How far a beam reaches before it has faded away. */
    private static final double RANGE = 14;
    private static final double SPREAD = Math.tan(Math.toRadians(13)), CORE = Math.tan(Math.toRadians(4.5));
    private static final float R = 1F, G = 0.13F, B = 0.06F;
    private static final int SIDES = 12;

    public AlarmBeaconRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(AlarmBeaconBlockEntity beacon, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = beacon.getBlockState();
        Level level = beacon.getLevel();
        if (level == null || !state.hasProperty(AlarmBeaconBlock.LIT) || !state.getValue(AlarmBeaconBlock.LIT)) return;
        BlockPos pos = beacon.getBlockPos();
        Vec3 corner = Vec3.atLowerCornerOf(pos);
        Vec3 lamp = corner.add(0.5, AlarmBeaconBlock.lampHeight(state), 0.5);
        // Each beacon keeps its own place in the turn, so a room of them never flashes in step.
        double turn = ((level.getGameTime() % 24000L) + partialTick) / TURN_TICKS + Mth.frac(pos.asLong() * 0.6180339887);
        double angle = turn * Math.PI * 2;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 toEye = camera.getPosition().subtract(lamp);
        double eyeDistance = Math.max(0.01, toEye.length());
        Vec3 eyeDir = toEye.scale(1 / eyeDistance);

        Brush brush = new Brush(buffers.getBuffer(RenderType.dragonRays()), poseStack.last(), corner);
        // Mounted high, the mirror is set to throw the light a little down into the room.
        double tilt = Math.toRadians(AlarmBeaconBlock.tilt(state));
        double glare = 0;
        for (int beam = 0; beam < 2; beam++) {
            double a = angle + beam * Math.PI;
            Vec3 dir = new Vec3(Math.cos(a) * Math.cos(tilt), -Math.sin(tilt), Math.sin(a) * Math.cos(tilt));
            BlockHitResult hit = level.clip(new ClipContext(lamp.add(dir.scale(0.3)), lamp.add(dir.scale(RANGE)),
                    ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
            boolean blocked = hit.getType() != HitResult.Type.MISS;
            double length = blocked ? hit.getLocation().distanceTo(lamp) : RANGE;
            // From inside a beam its cone would wash the whole view red; the flare shows that moment instead.
            double along = toEye.dot(dir), across = toEye.subtract(dir.scale(along)).length();
            float inside = along <= 0 ? 1 : (float) Mth.clamp((across / (along * SPREAD + 0.2) - 0.7) / 0.9, 0.12, 1);
            brush.cone(lamp, dir, length, SPREAD, 0.13F * inside);
            brush.cone(lamp, dir, length, CORE, 0.34F * inside);
            if (blocked) spot(brush, hit, dir, length);
            // Where the beam leaves the dome, the plastic glows.
            brush.disc(lamp.add(dir.scale(0.27)), dir, 0.2, 0.2, Vec3.ZERO, 0.75F);
            mirror(brush, lamp, dir);
            glare = Math.max(glare, dir.dot(eyeDir));
        }
        flare(brush, lamp, camera, glare, eyeDistance);
    }

    /** The patch of light a beam lays on a surface: an oval, stretched where it strikes at a slant. */
    private static void spot(Brush brush, BlockHitResult hit, Vec3 dir, double length) {
        Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        double incidence = Math.max(0.5, Math.abs(dir.dot(normal)));
        Vec3 along = dir.subtract(normal.scale(dir.dot(normal)));
        along = along.lengthSqr() < 1e-6 ? (Math.abs(normal.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)) : along.normalize();
        double radius = Math.min(1.6, 0.15 + length * SPREAD * 0.6);
        float strength = (float) (0.85 * (1 - 0.55 * length / RANGE));
        Vec3 centre = hit.getLocation().add(normal.scale(0.02));
        brush.disc(centre, normal, radius / incidence, radius, along, strength);
        brush.disc(centre.add(normal.scale(0.005)), normal, radius * 0.45 / incidence, radius * 0.45, along, strength * 0.7F);
    }

    /** The mirror behind the lamp: two bright slivers turning with the beams. */
    private static void mirror(Brush brush, Vec3 lamp, Vec3 dir) {
        Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
        Vec3 c = lamp.subtract(dir.scale(0.09));
        brush.quad(c.add(side.scale(0.11)).add(0, -0.15, 0), c.add(side.scale(-0.11)).add(0, -0.15, 0),
                c.add(side.scale(-0.11)).add(0, 0.15, 0), c.add(side.scale(0.11)).add(0, 0.15, 0), 1F, 0.55F, 0.2F, 0.55F);
    }

    /** The flash in the viewer's eyes as a beam swings past them, and the faint glow of the dome between flashes. */
    private static void flare(Brush brush, Vec3 lamp, Camera camera, double facing, double eyeDistance) {
        double flash = Math.pow(Math.max(0, facing), 10);
        Quaternionf rotation = camera.rotation();
        float near = (float) Mth.clamp(eyeDistance / 3, 0.35, 1);
        float glow = 0.12F + 0.85F * (float) flash;
        brush.billboard(lamp, rotation, 0.42 + 1.5 * flash, 0.42 + 1.5 * flash, glow * near);
        if (flash > 0.02) brush.billboard(lamp, rotation, 0.6 + 3.2 * flash, 0.05 + 0.07 * flash, (float) flash * 0.7F * near);
    }

    @Override public boolean shouldRenderOffScreen(AlarmBeaconBlockEntity beacon) { return true; }

    @Override public int getViewDistance() { return 64; }

    @Override public AABB getRenderBoundingBox(AlarmBeaconBlockEntity beacon) { return new AABB(beacon.getBlockPos()).inflate(RANGE); }

    /** Additive red light in world coordinates, written relative to the block being drawn. */
    private record Brush(VertexConsumer out, PoseStack.Pose pose, Vec3 origin) {
        void vertex(Vec3 p, float r, float g, float b, float a) {
            out.addVertex(pose, (float) (p.x - origin.x), (float) (p.y - origin.y), (float) (p.z - origin.z)).setColor(r, g, b, a);
        }

        /** A triangle, both ways round so it shows from either side. */
        void triangle(Vec3 a, float alphaA, Vec3 b, float alphaB, Vec3 c, float alphaC, float r, float g, float bl) {
            vertex(a, r, g, bl, alphaA); vertex(b, r, g, bl, alphaB); vertex(c, r, g, bl, alphaC);
            vertex(a, r, g, bl, alphaA); vertex(c, r, g, bl, alphaC); vertex(b, r, g, bl, alphaB);
        }

        void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float r, float g, float bl, float alpha) {
            triangle(a, alpha, b, alpha, c, alpha, r, g, bl);
            triangle(a, alpha, c, alpha, d, alpha, r, g, bl);
        }

        /** A cone of light from {@code apex} along {@code dir}: bright at the lamp, gone at its far end. */
        void cone(Vec3 apex, Vec3 dir, double length, double spread, float alpha) {
            Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize(), up = side.cross(dir).normalize();
            Vec3 end = apex.add(dir.scale(length));
            double radius = length * spread;
            Vec3 previous = null;
            for (int i = 0; i <= SIDES; i++) {
                double t = i * Math.PI * 2 / SIDES;
                Vec3 rim = end.add(up.scale(Math.cos(t) * radius)).add(side.scale(Math.sin(t) * radius));
                if (previous != null) triangle(apex, alpha, previous, 0, rim, 0, R, G, B);
                previous = rim;
            }
        }

        /** A soft oval facing {@code normal}: {@code long} across {@code along}, {@code wide} across the other way. */
        void disc(Vec3 centre, Vec3 normal, double lengthwise, double wide, Vec3 along, float alpha) {
            Vec3 u = along.lengthSqr() < 1e-6 ? perpendicular(normal) : along;
            Vec3 v = normal.cross(u).normalize();
            Vec3 previous = null;
            for (int i = 0; i <= 16; i++) {
                double t = i * Math.PI * 2 / 16;
                Vec3 rim = centre.add(u.scale(Math.cos(t) * lengthwise)).add(v.scale(Math.sin(t) * wide));
                if (previous != null) triangle(centre, alpha, previous, 0, rim, 0, R, G * 1.4F, B);
                previous = rim;
            }
        }

        /** A soft oval facing the camera. */
        void billboard(Vec3 centre, Quaternionf rotation, double width, double height, float alpha) {
            Vec3 right = turn(rotation, 1, 0), up = turn(rotation, 0, 1);
            Vec3 previous = null;
            for (int i = 0; i <= 16; i++) {
                double t = i * Math.PI * 2 / 16;
                Vec3 rim = centre.add(right.scale(Math.cos(t) * width)).add(up.scale(Math.sin(t) * height));
                if (previous != null) triangle(centre, alpha, previous, 0, rim, 0, R, 0.28F, 0.12F);
                previous = rim;
            }
        }

        private static Vec3 turn(Quaternionf rotation, float x, float y) {
            Vector3f v = rotation.transform(new Vector3f(x, y, 0));
            return new Vec3(v.x, v.y, v.z);
        }

        private static Vec3 perpendicular(Vec3 normal) {
            return Math.abs(normal.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0).cross(normal).normalize();
        }
    }
}
