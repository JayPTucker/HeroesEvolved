package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.BreachActorsPayload;
import com.jayptucker.heroesevolved.network.BreachActorsPayload.Actor;
import com.jayptucker.heroesevolved.network.BreachViewPayload;
import com.jayptucker.heroesevolved.vibe.VibeGeometry;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Detached copies for a single far-side view. Vanilla renders their models without adding them to the client world. */
public final class BreachViewActors {
    private static final EquipmentSlot[] SLOTS = EquipmentSlot.values();
    private static MultiBufferSource.BufferSource buffers;
    private static boolean drawing;

    private final Map<Integer, Rendered> actors = new HashMap<>();
    private long receivedAt;

    private static final class PortalPlayer extends RemotePlayer {
        private final PlayerInfo appearance;

        PortalPlayer(ClientLevel level, GameProfile profile) {
            super(level, profile);
            appearance = new PlayerInfo(profile, false);
        }

        @Override protected PlayerInfo getPlayerInfo() { return appearance; }
    }

    private static final class Rendered {
        final Entity entity;
        Actor last, current;
        float walk;

        Rendered(Entity entity, Actor actor) {
            this.entity = entity;
            last = current = actor;
        }

        void update(Actor actor) {
            last = current;
            current = actor;
            Vec3 displacement = position(current).subtract(position(last));
            // A player walks on their own client. Their server-side limb speed can stay at zero.
            walk = displacement.lengthSqr() > 16 ? 0 : Math.max(actor.walk(),
                    Math.min(1, (float) displacement.horizontalDistance() * 4 / BreachActorsPayload.UPDATE_TICKS));
            if (displacement.lengthSqr() > 16) last = current;
            entity.getEntityData().assignValues(actor.appearance().packedItems());
            if (entity instanceof LivingEntity living) {
                for (int i = 0; i < actor.equipment().size(); i++) living.setItemSlot(SLOTS[i], actor.equipment().get(i));
            }
        }

        private static Vec3 position(Actor actor) {
            var spawn = actor.spawn();
            return new Vec3(spawn.getX(), spawn.getY(), spawn.getZ());
        }

        Vec3 place(float progress) {
            Vec3 position = position(last).lerp(position(current), progress);
            entity.setPos(position);
            entity.setYRot(Mth.rotLerp(progress, last.spawn().getYRot(), current.spawn().getYRot()));
            entity.setXRot(Mth.rotLerp(progress, last.spawn().getXRot(), current.spawn().getXRot()));
            entity.setOldPosAndRot();
            if (entity instanceof LivingEntity living) {
                living.yBodyRotO = living.yBodyRot = Mth.rotLerp(progress, last.bodyYaw(), current.bodyYaw());
                living.yHeadRotO = living.yHeadRot = Mth.rotLerp(progress, last.headYaw(), current.headYaw());
            }
            entity.tickCount = Mth.floor(Mth.lerp(progress, last.age(), current.age()));
            return position;
        }
    }

    int count() { return actors.size(); }

    public static boolean drawing() { return drawing; }

    void tick(long now) {
        boolean fresh = now - receivedAt <= 2 * BreachActorsPayload.UPDATE_TICKS;
        for (Rendered actor : actors.values()) {
            if (actor.entity instanceof LivingEntity living) living.walkAnimation.update(fresh ? actor.walk : 0, .4F);
        }
    }

    void update(ClientLevel level, List<Actor> samples) {
        Map<Integer, Rendered> next = new HashMap<>();
        for (Actor actor : samples) {
            int id = actor.spawn().getId();
            Rendered rendered = actors.get(id);
            if (rendered == null || !rendered.entity.getUUID().equals(actor.spawn().getUUID())) {
                Entity entity = create(level, actor);
                if (entity == null) continue;
                rendered = new Rendered(entity, actor);
            }
            rendered.update(actor);
            next.put(id, rendered);
        }
        actors.clear();
        actors.putAll(next);
        receivedAt = level.getGameTime();
    }

    private static Entity create(ClientLevel level, Actor actor) {
        Entity entity = actor.profile() == null ? actor.spawn().getType().create(level) : new PortalPlayer(level, actor.profile());
        if (entity == null) return null;
        entity.recreateFromPacket(actor.spawn());
        if (entity instanceof IEntityWithComplexSpawn complex && actor.extraSpawn().length != 0) {
            var buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(actor.extraSpawn()), level.registryAccess());
            try {
                complex.readSpawnData(buffer);
            } finally {
                buffer.release();
            }
        }
        return entity;
    }

    void draw(Minecraft mc, BreachViewPayload info, Vec3 camera, boolean front, long now, float partial) {
        if (actors.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(64 * 1024));
        float progress = Mth.clamp((now - receivedAt + partial) / BreachActorsPayload.UPDATE_TICKS, 0, 1);
        PoseStack pose = new PoseStack();
        var rotation = VibeGeometry.rotation(info.farNormal(), info.farYaw(), info.normal(), info.yaw());
        var dispatcher = mc.getEntityRenderDispatcher();
        var cameraRotation = dispatcher.cameraOrientation();
        dispatcher.overrideCameraOrientation(new Quaternionf(rotation).conjugate().mul(cameraRotation));
        drawing = true;
        try {
            for (Rendered actor : actors.values()) {
                Vec3 far = actor.place(progress);
                double side = actor.entity.getBoundingBox().getCenter().subtract(info.farCenter()).dot(info.farNormal());
                if (front ? side < 0 : side > 0) continue;
                Vec3 relative = far.subtract(info.farCenter());
                var turned = rotation.transform(new Vector3d(relative.x, relative.y, relative.z));
                Vec3 seen = info.center().subtract(camera).add(turned.x, turned.y, turned.z);
                int light = info.snapshot().light(BlockPos.containing(far.add(0, actor.entity.getEyeHeight(), 0)));
                var renderer = dispatcher.getRenderer(actor.entity);
                Vec3 renderOffset = renderer.getRenderOffset(actor.entity, partial);
                pose.pushPose();
                pose.translate(seen.x, seen.y, seen.z);
                pose.mulPose(rotation);
                pose.translate(renderOffset.x, renderOffset.y, renderOffset.z);
                renderer.render(actor.entity, actor.entity.getYRot(), partial, pose, buffers, LightTexture.pack(light & 15, light >> 4));
                pose.popPose();
            }
            buffers.endBatch();
        } finally {
            drawing = false;
            dispatcher.overrideCameraOrientation(cameraRotation);
        }
    }
}
