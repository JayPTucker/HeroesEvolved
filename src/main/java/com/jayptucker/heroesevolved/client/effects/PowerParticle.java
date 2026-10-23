package com.jayptucker.heroesevolved.client.effects;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.ParticleGroup;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.EnumMap;
import java.util.Optional;

/** Expanding oriented rings, continuous ribbons, and gently drifting luminous motes. */
public final class PowerParticle extends TextureSheetParticle {
    public enum Shape { RING, STREAK, MOTE, ARC }

    private static final EnumMap<Shape, SpriteSet> SPRITES = new EnumMap<>(Shape.class);
    private static final ParticleGroup GROUP = new ParticleGroup(768);
    private final Shape shape;
    private final Vec3 axisOrEnd;
    private final float startSize;
    private final float endSize;
    private final float opacity;
    private float arcPhase;
    private float arcSpeed;
    private float arcSpan;
    private float arcWidth;

    public PowerParticle(ClientLevel level, Shape shape, Vec3 position, Vec3 axisOrEnd,
                         float startSize, float endSize, int lifetime, int delay,
                         int color, float opacity) {
        super(level, position.x, position.y, position.z);
        this.shape = shape;
        this.axisOrEnd = axisOrEnd;
        this.startSize = startSize;
        this.endSize = endSize;
        this.opacity = opacity;
        this.lifetime = Math.max(1, lifetime);
        this.age = -delay;
        this.hasPhysics = false;
        this.friction = 0.94F;
        setColor(((color >> 16) & 255) / 255.0F, ((color >> 8) & 255) / 255.0F,
                (color & 255) / 255.0F);
        SpriteSet sprites = SPRITES.get(shape == Shape.ARC ? Shape.STREAK : shape);
        if (sprites != null) {
            pickSprite(sprites);
        } else {
            remove();
        }
    }

    /** An arc is a curved ribbon in a fixed plane, animated every render frame. */
    public PowerParticle arc(float phase, float speed, float span, float width) {
        arcPhase = phase;
        arcSpeed = speed;
        arcSpan = span;
        arcWidth = width;
        return this;
    }

    public PowerParticle moving(Vec3 velocity) {
        setParticleSpeed(velocity.x, velocity.y, velocity.z);
        friction = 1.0F;
        return this;
    }

    public static ParticleProvider<SimpleParticleType> provider(Shape shape, SpriteSet sprites) {
        SPRITES.put(shape, sprites);
        return (type, level, x, y, z, dx, dy, dz) -> new PowerParticle(level, shape,
                new Vec3(x, y, z), shape == Shape.STREAK
                        ? new Vec3(x + dx, y + dy, z + dz) : new Vec3(0, 1, 0),
                0.1F, 1.0F, 16, 0, 0xD4F2FF, 0.65F);
    }

    @Override
    public void tick() {
        if (age < 0) {
            age++;
            return;
        }
        super.tick();
    }

    @Override
    public float getQuadSize(float partialTick) {
        float progress = Mth.clamp((age + partialTick) / lifetime, 0, 1);
        // The pressure front is at 87% of the texture radius.
        return Mth.lerp(progress, startSize, endSize) / (shape == Shape.RING ? 0.87F : 1.0F);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        if (age < 0 || sprite == null) {
            return;
        }
        float progress = Mth.clamp((age + partialTick) / lifetime, 0, 1);
        // Fast attack, soft decay; no large opaque flash in first person.
        float fade = shape == Shape.RING ? 1 - progress * progress * progress
                : (1 - progress) * (1 - progress);
        alpha = opacity * Math.min(1.0F, (age + partialTick + 1) / 2.0F) * fade;
        if (shape == Shape.RING) {
            Vec3 normal = axisOrEnd.lengthSqr() < 0.001D ? new Vec3(0, 1, 0)
                    : axisOrEnd.normalize();
            Quaternionf rotation = new Quaternionf().rotationTo(0, 0, 1,
                    (float) normal.x, (float) normal.y, (float) normal.z);
            renderRotatedQuad(buffer, camera, rotation, partialTick);
            // The ring remains visible from above and below.
            renderRotatedQuad(buffer, camera, rotation.rotateY((float) Math.PI), partialTick);
        } else if (shape == Shape.STREAK) {
            renderStreak(buffer, camera, partialTick);
        } else if (shape == Shape.ARC) {
            renderArc(buffer, camera, partialTick);
        } else {
            super.render(buffer, camera, partialTick);
        }
    }

    private void renderStreak(VertexConsumer buffer, Camera camera, float partialTick) {
        renderRibbon(buffer, camera, new Vec3(x, y, z), axisOrEnd, getQuadSize(partialTick), 1.0F);
    }

    private void renderArc(VertexConsumer buffer, Camera camera, float partialTick) {
        Vec3 normal = axisOrEnd.lengthSqr() < 0.001D ? new Vec3(0, 1, 0) : axisOrEnd.normalize();
        Vec3 reference = Math.abs(normal.y) > 0.9D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = normal.cross(reference).normalize();
        Vec3 up = right.cross(normal).normalize();
        Vec3 center = new Vec3(Mth.lerp(partialTick, xo, x), Mth.lerp(partialTick, yo, y),
                Mth.lerp(partialTick, zo, z));
        double radius = getQuadSize(partialTick);
        double angle = arcPhase + (age + partialTick) * arcSpeed;
        Vec3 previous = center.add(right.scale(Math.cos(angle) * radius))
                .add(up.scale(Math.sin(angle) * radius));
        for (int index = 1; index <= 12; index++) {
            double nextAngle = angle + arcSpan * index / 12.0D;
            Vec3 next = center.add(right.scale(Math.cos(nextAngle) * radius))
                    .add(up.scale(Math.sin(nextAngle) * radius));
            float taper = 0.2F + 0.8F * (float) Math.sin(Math.PI * (index - 0.5D) / 12.0D);
            renderRibbon(buffer, camera, previous, next, arcWidth * taper, taper);
            previous = next;
        }
    }

    private void renderRibbon(VertexConsumer buffer, Camera camera, Vec3 start, Vec3 end,
                              float width, float brightness) {
        Vec3 tangent = end.subtract(start);
        if (tangent.lengthSqr() < 0.00001D) {
            return;
        }
        Vec3 facing = camera.getPosition().subtract(start.lerp(end, 0.5D));
        Vec3 across = tangent.cross(facing).normalize();
        if (across.lengthSqr() < 0.001D) {
            Vec3 reference = Math.abs(tangent.normalize().y) > 0.9D
                    ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            across = tangent.cross(reference).normalize();
        }
        across = across.scale(width);
        Vec3 relativeStart = start.subtract(camera.getPosition());
        Vec3 relativeEnd = end.subtract(camera.getPosition());
        float savedAlpha = alpha;
        alpha *= brightness;
        vertex(buffer, relativeStart.subtract(across), getU0(), getV1());
        vertex(buffer, relativeStart.add(across), getU0(), getV0());
        vertex(buffer, relativeEnd.add(across), getU1(), getV0());
        vertex(buffer, relativeEnd.subtract(across), getU1(), getV1());
        alpha = savedAlpha;
    }

    private void vertex(VertexConsumer buffer, Vec3 point, float u, float v) {
        buffer.addVertex((float) point.x, (float) point.y, (float) point.z)
                .setUv(u, v).setColor(rCol, gCol, bCol, alpha).setLight(0xF000F0);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public AABB getRenderBoundingBox(float partialTick) {
        if (shape == Shape.STREAK) {
            return new AABB(new Vec3(x, y, z), axisOrEnd).inflate(getQuadSize(partialTick));
        }
        // Include the interpolated motion and rotated quad corners, so a moving
        // orb or pressure front does not vanish at the edge of the camera view.
        double extent = getQuadSize(partialTick) * Math.sqrt(2.0D)
                + (shape == Shape.ARC ? arcWidth : 0.0D);
        return new AABB(new Vec3(xo, yo, zo), new Vec3(x, y, z)).inflate(extent);
    }

    @Override
    public Optional<ParticleGroup> getParticleGroup() {
        return Optional.of(GROUP);
    }
}
