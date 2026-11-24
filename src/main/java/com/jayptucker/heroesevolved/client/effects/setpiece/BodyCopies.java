package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.combat.FinisherBeats;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

import java.util.ArrayList;
import java.util.List;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.hash;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.smooth;

/**
 * A body drawn differently while a finisher plays on it, from the set pieces bound to it: turned to stone (Monument),
 * split into tinted copies (Off Frequency), reflected in panes (Hall of Mirrors' copies of the player), or hidden
 * (Unmade, once its plates have closed). The real model is drawn again with its own pose, through a vertex wrapper that
 * tints it or swaps its texture for stone, so a copy moves and breathes like the real thing.
 *
 * <p>Copies are opaque; their colour is multiplied, not blended. A copy of the local player isn't drawn in first person
 * (the game doesn't draw the local player there).</p>
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class BodyCopies {
    private static final ResourceLocation STONE = ResourceLocation.withDefaultNamespace("textures/block/stone.png");

    /**
     * One extra drawing: where (relative to the body), turned to face {@code yaw} (NaN keeps the body's own), how tinted.
     * {@code calm}: never flashes red when the real body is hurt (a copy that is someone else: Echo, Its Own Face).
     */
    record Copy(Vec3 offset, float yaw, float r, float g, float b, boolean stone, boolean calm) {
        Copy(Vec3 offset, float yaw, float r, float g, float b, boolean stone) { this(offset, yaw, r, g, b, stone, false); }
    }

    /** What a piece does to the body it is bound to this frame. */
    record Plan(boolean hide, List<Copy> copies) {
        static final Plan NONE = new Plan(false, List.of());
    }

    private static boolean drawing;

    private BodyCopies() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void pre(RenderLivingEvent.Pre<?, ?> event) {
        if (drawing) return;
        LivingEntity entity = event.getEntity();
        List<Active> pieces = SetPieceClient.on(entity.getId());
        if (pieces.isEmpty()) return;
        float partial = event.getPartialTick();
        long now = entity.level().getGameTime();
        boolean hide = false;
        List<Copy> copies = new ArrayList<>();
        for (Active a : pieces) {
            Plan plan = plan(a, entity, now - a.start() + partial);
            hide |= plan.hide();
            copies.addAll(plan.copies());
        }
        if (copies.isEmpty() && !hide) return;
        if (!copies.isEmpty()) draw(event, entity, copies, partial);
        if (hide) event.setCanceled(true);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void draw(RenderLivingEvent.Pre<?, ?> event, LivingEntity entity, List<Copy> copies, float partial) {
        LivingEntityRenderer renderer = event.getRenderer();
        PoseStack stack = event.getPoseStack();
        float body = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot);
        float yaw = Mth.rotLerp(partial, entity.yRotO, entity.getYRot());
        Vec3 camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 feet = entity.getPosition(partial);
        drawing = true;
        try {
            for (Copy copy : copies) {
                // Never draw a copy round the camera (a shape worn by the local player, seen in first person).
                Vec3 middle = feet.add(copy.offset).add(0, entity.getBbHeight() * .5, 0);
                if (Math.abs(camera.x - middle.x) < entity.getBbWidth() * .5 + .3 && Math.abs(camera.z - middle.z) < entity.getBbWidth() * .5 + .3
                        && Math.abs(camera.y - middle.y) < entity.getBbHeight() * .5 + .2) continue;
                stack.pushPose();
                stack.translate(copy.offset.x, copy.offset.y, copy.offset.z);
                if (!Float.isNaN(copy.yaw)) stack.mulPose(Axis.YP.rotationDegrees(body - copy.yaw));
                renderer.render(entity, yaw, partial, stack, new Tinted(event.getMultiBufferSource(), copy), event.getPackedLight());
                stack.popPose();
            }
        } catch (RuntimeException broken) {
            HeroesEvolved.LOGGER.warn("A finisher's body copy failed to draw", broken);
        } finally {
            drawing = false;
        }
    }

    /** The rules, one per piece, timed by the same beats as the server ({@link FinisherBeats}). */
    private static Plan plan(Active a, LivingEntity entity, float t) {
        switch (a.piece()) {
            case END_BATTLE_RELEASE -> {
                var v=com.jayptucker.heroesevolved.endgame.EndClientState.view();
                float age=v.stage()==com.jayptucker.heroesevolved.endgame.EndBattle.VICTORY?com.jayptucker.heroesevolved.endgame.EndClientState.age(0):t;
                return age>=com.jayptucker.heroesevolved.endgame.EndGeometry.RUPTURE_TICK?new Plan(true,List.of()):Plan.NONE;
            }
            case END_BATTLE_DASH -> {
                if(entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon||t>5
                        ||SetPieceClient.superseded(a,java.util.Set.of(com.jayptucker.heroesevolved.styles.Piece.END_BATTLE_DASH),entity.level().getGameTime()))return Plan.NONE;
                // Real posed bodies mark the path already travelled, never a silhouette ahead of the hit.
                Vec3 offset=a.origin().subtract(entity.getPosition(0));
                return new Plan(false,List.of(new Copy(offset,Float.NaN,.42F,.62F,.85F,false,true)));
            }
            case FINISH_MONUMENT -> {
                // Stone from the moment the plates close, lifted on its plinth; gone once it crumbles.
                if (t < FinisherBeats.MONUMENT_SHUT) return Plan.NONE;
                if (t >= FinisherBeats.MONUMENT_CRUMBLE) return new Plan(true, List.of());
                double lift = .5 * smooth(FinisherBeats.MONUMENT_SHUT, FinisherBeats.MONUMENT_SHUT + 8, t);
                return new Plan(true, List.of(new Copy(new Vec3(0, lift, 0), Float.NaN, 1, 1, 1, true)));
            }
            case FINISH_FREQUENCY -> {
                // Split into three copies, blue, red and green, that drift apart buzzing and blink out one by one.
                if (t < FinisherBeats.FREQUENCY_SPLIT || t >= FinisherBeats.FREQUENCY_GONE + 3) return t < FinisherBeats.FREQUENCY_SPLIT
                        ? Plan.NONE : new Plan(true, List.of());
                Vec3 facing = a.facing(), side = new Vec3(-facing.z, 0, facing.x);
                float drift = .12F + .85F * smooth(FinisherBeats.FREQUENCY_SPLIT + 6, FinisherBeats.FREQUENCY_SPLIT + 13, t);
                float[][] tints = {{.45F, .65F, 1}, {1, .45F, .45F}, {.5F, 1, .55F}};
                List<Copy> out = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    if (t >= FinisherBeats.FREQUENCY_GONE + i) continue;
                    int tick = (int) t;
                    double buzz = (hash(a.seed() + tick, i) - .5) * .08;
                    Vec3 offset = side.scale((i - 1) * drift + buzz).add(0, (hash(a.seed() + tick, i + 7) - .5) * .05, 0);
                    out.add(new Copy(offset, Float.NaN, tints[i][0], tints[i][1], tints[i][2], false));
                }
                return new Plan(true, out);
            }
            case FINISH_MIRRORS -> {
                // Your reflections in the panes round the foe, each turned to face it; the real you is hidden until you
                // step out of the pane behind it.
                if (t >= FinisherBeats.MIRRORS_STEP) return Plan.NONE;
                if (t < FinisherBeats.MIRRORS_RISE) return new Plan(true, List.of());
                Vec3 foe = a.origin(), me = entity.getPosition(0);
                List<Copy> out = new ArrayList<>();
                int panes = FinisherBeats.MIRRORS_PANES;
                for (int i = 0; i < panes; i++) {
                    Vec3 at = FinisherBeats.pane(foe, a.target(), i, panes);
                    Vec3 look = foe.subtract(at);
                    float face = (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG);
                    float rise = smooth(FinisherBeats.MIRRORS_RISE + i, FinisherBeats.MIRRORS_RISE + i + 4, t);
                    out.add(new Copy(at.subtract(me).add(0, -.6 * (1 - rise), 0), face, .7F, .86F, 1, false));
                }
                return new Plan(true, out);
            }
            case FINISH_UNMADE -> {
                return t >= FinisherBeats.UNMADE_GONE ? new Plan(true, List.of()) : Plan.NONE;
            }
            case FINISH_UNWOUND -> {
                // Its last moments step back out of it as violet copies, and are drawn back in; then it is gone.
                if (t >= FinisherBeats.UNWOUND_GONE) return new Plan(true, List.of());
                if (t < FinisherBeats.UNWOUND_SPLIT) return Plan.NONE;
                float k = smooth(FinisherBeats.UNWOUND_SPLIT, FinisherBeats.UNWOUND_SPLIT + 5, t)
                        * (1 - smooth(FinisherBeats.UNWOUND_GONE - 7, FinisherBeats.UNWOUND_GONE - 1, t));
                Vec3 back = a.facing().scale(-1);
                List<Copy> out = new ArrayList<>();
                for (int i = 2; i >= 0; i--)
                    out.add(new Copy(back.scale((i + 1) * .5 * k), Float.NaN, .62F - i * .06F, .55F - i * .05F, 1, false));
                return new Plan(false, out);
            }
            case FINISH_HALF_LIFE -> {
                // Greening as the dose takes, then grey ash; gone once it has crumbled.
                if (t >= FinisherBeats.HALF_LIFE_GONE) return new Plan(true, List.of());
                float green = smooth(0, FinisherBeats.HALF_LIFE_ASH, t), ash = smooth(FinisherBeats.HALF_LIFE_ASH, FinisherBeats.HALF_LIFE_LAND, t);
                float r = Mth.lerp(ash, Mth.lerp(green, 1, .55F), .32F), g = Mth.lerp(ash, 1, .3F), b = Mth.lerp(ash, Mth.lerp(green, 1, .3F), .28F);
                return new Plan(true, List.of(new Copy(Vec3.ZERO, Float.NaN, r, g, b, false)));
            }
            case FINISH_BREACH -> {
                // Falling through the breach under it: the body sinks out of sight, the ground hides the rest.
                if (t < FinisherBeats.BREACH_FALL) return Plan.NONE;
                float k = t - FinisherBeats.BREACH_FALL;
                double drop = Math.min(4, .07 * k * k + .1 * k);
                return new Plan(true, drop >= 3.5 ? List.of() : List.of(new Copy(new Vec3(0, -drop, 0), Float.NaN, .8F, .9F, 1, false)));
            }
            case FINISH_HEAVY -> {
                // Driven into the crater by the blow.
                if (t < FinisherBeats.HEAVY_LAND) return Plan.NONE;
                double sink = .85 * smooth(FinisherBeats.HEAVY_LAND, FinisherBeats.HEAVY_LAND + 3, t);
                return new Plan(true, List.of(new Copy(new Vec3(0, -sink, 0), Float.NaN, 1, 1, 1, false)));
            }
            case FINISH_ECHO -> {
                // A gold outline of the foe rises in front of it, facing it, and hits it with its own swing.
                // Gone as its blow lands, before the foe's death tips it over too.
                if (t < FinisherBeats.ECHO_RISE || t >= FinisherBeats.ECHO_LAND + 1) return Plan.NONE;
                Vec3 at = a.facing().scale(1.25);
                float face = (float) (Mth.atan2(at.x, -at.z) * Mth.RAD_TO_DEG);
                float rise = smooth(FinisherBeats.ECHO_RISE, FinisherBeats.ECHO_RISE + 5, t);
                return new Plan(false, List.of(new Copy(at.add(0, -.8 * (1 - rise), 0), face, 1, .78F, .32F, false, true)));
            }
            case FINISH_FACE -> {
                // Two pieces: the one bound to you hides you; the one bound to the foe draws its shape where you stand.
                boolean worn = t >= FinisherBeats.FACE_WEAR && t < FinisherBeats.FACE_BACK;
                if (!worn) return Plan.NONE;
                if (a.scale() <= FinisherBeats.HIDE_MARK) return new Plan(true, List.of());
                Vec3 at = a.target().subtract(entity.getPosition(0));
                float face = (float) (Mth.atan2(at.x, -at.z) * Mth.RAD_TO_DEG);
                return new Plan(false, List.of(new Copy(at, face, 1, 1, 1, false, true)));
            }
            case FINISH_APEX -> {
                // You are hidden under the predator's shape (MorphClient draws it) while it lunges.
                boolean shifted = t >= FinisherBeats.APEX_SHIFT && t < FinisherBeats.APEX_BACK;
                return shifted && a.scale() <= FinisherBeats.HIDE_MARK ? new Plan(true, List.of()) : Plan.NONE;
            }
            default -> { return Plan.NONE; }
        }
    }

    /** A buffer source that multiplies every vertex colour, or swaps the texture for stone. */
    private record Tinted(MultiBufferSource inner, Copy copy) implements MultiBufferSource {
        @Override public VertexConsumer getBuffer(RenderType type) {
            VertexConsumer out = inner.getBuffer(copy.stone ? RenderType.entityCutoutNoCull(STONE) : type);
            return copy.stone ? new Tint(out, .82F, .82F, .8F, true) : new Tint(out, copy.r, copy.g, copy.b, copy.calm);
        }
    }

    /** {@code still}: no hurt flash (stone doesn't flinch red). */
    private record Tint(VertexConsumer inner, float r, float g, float b, boolean still) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) { inner.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            inner.setColor((int) (red * r), (int) (green * g), (int) (blue * b), alpha);
            return this;
        }
        @Override public VertexConsumer setUv(float u, float v) { inner.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) {
            if (still) inner.setUv1(0, 10);
            else inner.setUv1(u, v);
            return this;
        }
        @Override public VertexConsumer setUv2(int u, int v) { inner.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { inner.setNormal(x, y, z); return this; }
    }
}
