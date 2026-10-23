package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.FrozenEntityPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Translucent, faceted ice around the actual mob, with visible fractures and a frozen pose. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class FrozenVisuals {
    private record Frozen(UUID uuid, long until) {}
    private static final Map<Integer, Frozen> FROZEN = new HashMap<>();
    private static final ResourceLocation ICE = ResourceLocation.withDefaultNamespace("textures/block/ice.png");
    private static ClientLevel world;
    private FrozenVisuals() {}
    public static void update(FrozenEntityPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != world) { FROZEN.clear(); world = level; }
        if (level == null) return;
        if (payload.remainingTicks() <= 0) {
            Frozen old = FROZEN.get(payload.entityId());
            if (old != null && old.uuid.equals(payload.entityUuid())) FROZEN.remove(payload.entityId());
        } else if (FROZEN.size() < 2048 || FROZEN.containsKey(payload.entityId())) {
            FROZEN.put(payload.entityId(), new Frozen(payload.entityUuid(),
                    level.getGameTime() + Math.clamp(payload.remainingTicks(), 1, 610)));
        }
    }
    private static boolean frozen(Entity entity) {
        Frozen state = FROZEN.get(entity.getId());
        return entity.level() == world && state != null && state.uuid.equals(entity.getUUID())
                && entity.level().getGameTime() < state.until;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { FROZEN.clear(); world = null; }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (world != Minecraft.getInstance().level) { FROZEN.clear(); world = Minecraft.getInstance().level; }
        if (world != null) FROZEN.values().removeIf(state -> world.getGameTime() >= state.until);
    }
    @SubscribeEvent public static void pause(EntityTickEvent.Pre event) {
        if (event.getEntity().level().isClientSide && event.getEntity() instanceof Mob && frozen(event.getEntity())) {
            event.getEntity().setDeltaMovement(Vec3.ZERO);
            event.setCanceled(true);
        }
    }
    @SubscribeEvent public static void render(RenderLivingEvent.Post<?, ?> event) {
        Entity entity = event.getEntity();
        if (!frozen(entity)) return;
        PoseStack.Pose pose = event.getPoseStack().last();
        VertexConsumer ice = event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(ICE));
        float rx = (float) (entity.getBoundingBox().getXsize() * 0.57 + 0.12);
        float rz = (float) (entity.getBoundingBox().getZsize() * 0.57 + 0.12);
        float height = entity.getBbHeight() + 0.15F;
        for (int row = 0; row < 2; row++) for (int face = 0; face < 8; face++) {
            double a = face * Math.PI / 4 + Math.PI / 8, b = a + Math.PI / 4;
            float lower = row == 0 ? 1.05F : 1, upper = row == 0 ? 1 : 0.84F;
            float bottom = row == 0 ? 0.015F : height * 0.65F, top = row == 0 ? height * 0.65F : height;
            float nx = (float) Math.cos((a + b) / 2), nz = (float) Math.sin((a + b) / 2);
            vertex(ice, pose, (float) Math.cos(a) * rx * lower, bottom, (float) Math.sin(a) * rz * lower, 0, 1, nx, nz, event.getPackedLight());
            vertex(ice, pose, (float) Math.cos(b) * rx * lower, bottom, (float) Math.sin(b) * rz * lower, 1, 1, nx, nz, event.getPackedLight());
            vertex(ice, pose, (float) Math.cos(b) * rx * upper, top, (float) Math.sin(b) * rz * upper, 1, 0, nx, nz, event.getPackedLight());
            vertex(ice, pose, (float) Math.cos(a) * rx * upper, top, (float) Math.sin(a) * rz * upper, 0, 0, nx, nz, event.getPackedLight());
        }
        // Close the crown so the shell remains solid when viewed from above.
        for (int face = 0; face < 8; face++) {
            double a = face * Math.PI / 4 + Math.PI / 8, b = a + Math.PI / 4;
            vertex(ice, pose, 0, height, 0, 0.5F, 0.5F, 0, 0, event.getPackedLight());
            vertex(ice, pose, (float) Math.cos(a) * rx * 0.84F, height, (float) Math.sin(a) * rz * 0.84F,
                    (float) (0.5 + Math.cos(a) * 0.5), (float) (0.5 + Math.sin(a) * 0.5), 0, 0, event.getPackedLight());
            vertex(ice, pose, (float) Math.cos(b) * rx * 0.84F, height, (float) Math.sin(b) * rz * 0.84F,
                    (float) (0.5 + Math.cos(b) * 0.5), (float) (0.5 + Math.sin(b) * 0.5), 0, 0, event.getPackedLight());
            vertex(ice, pose, 0, height, 0, 0.5F, 0.5F, 0, 0, event.getPackedLight());
        }
        VertexConsumer cracks = event.getMultiBufferSource().getBuffer(RenderType.lines());
        for (int side = 0; side < 4; side++) {
            double a = side * Math.PI / 2 + Math.PI / 8;
            Vec3 p = new Vec3(Math.cos(a) * rx * 1.015, height * 0.14, Math.sin(a) * rz * 1.015);
            Vec3 middle = p.add(-Math.sin(a) * rx * 0.3, height * 0.3, Math.cos(a) * rz * 0.3);
            line(cracks, pose, p, middle);
            line(cracks, pose, middle, p.add(0, height * 0.65, 0));
            line(cracks, pose, middle, middle.add(Math.sin(a) * rx * 0.2, height * 0.12, -Math.cos(a) * rz * 0.2));
        }
    }
    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, float nx, float nz, int light) {
        out.addVertex(pose.pose(), x, y, z).setColor(0.68F, 0.89F, 1, 0.38F).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, nx == 0 && nz == 0 ? 1 : 0.1F, nz);
    }
    private static void line(VertexConsumer out, PoseStack.Pose pose, Vec3 from, Vec3 to) {
        Vec3 normal = to.subtract(from).normalize();
        for (Vec3 point : new Vec3[] {from, to}) out.addVertex(pose.pose(), (float) point.x, (float) point.y, (float) point.z)
                .setColor(0.85F, 0.97F, 1, 0.8F).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
