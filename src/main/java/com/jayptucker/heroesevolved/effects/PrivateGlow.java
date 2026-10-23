package com.jayptucker.heroesevolved.effects;

import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Makes an entity glow through walls for one viewer only, for a while.
 *
 * <p>The viewer is sent a private copy of the entity's shared-flags byte
 * with the glowing bit set. The server re-sends the real flags whenever
 * they change, so active glows are refreshed every few ticks, and the real
 * flags are restored when a glow ends.</p>
 */
public final class PrivateGlow {
    private static final int REFRESH_TICKS = 5;
    private static final Map<Key, Glow> GLOWS = new HashMap<>();

    private PrivateGlow() {
    }

    public static void glow(ServerPlayer viewer, Entity entity, int durationTicks) {
        if (viewer == entity) {
            return;
        }
        long until = viewer.serverLevel().getGameTime() + durationTicks;
        Key key = new Key(viewer.getUUID(), entity.getId());
        Glow existing = GLOWS.get(key);
        if (existing == null) {
            sendFlags(viewer, entity, true);
        }
        GLOWS.put(key, new Glow(entity, existing == null
                ? until : Math.max(until, existing.until)));
    }

    public static void tick(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        Iterator<Map.Entry<Key, Glow>> iterator = GLOWS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Key, Glow> entry = iterator.next();
            ServerPlayer viewer = server.getPlayerList().getPlayer(entry.getKey().viewerId);
            Glow glow = entry.getValue();
            if (viewer == null) {
                iterator.remove();
                continue;
            }

            boolean gone = glow.entity.isRemoved()
                    || glow.entity.level() != viewer.level();
            if (gone || gameTime >= glow.until) {
                iterator.remove();
                if (!gone) {
                    sendFlags(viewer, glow.entity, false);
                }
            } else if (gameTime % REFRESH_TICKS == 0) {
                sendFlags(viewer, glow.entity, true);
            }
        }
    }

    /** Ends every glow a viewer sees, restoring the real flags. */
    public static void clearFor(ServerPlayer viewer) {
        Iterator<Map.Entry<Key, Glow>> iterator = GLOWS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Key, Glow> entry = iterator.next();
            if (entry.getKey().viewerId.equals(viewer.getUUID())) {
                if (!entry.getValue().entity.isRemoved()) {
                    sendFlags(viewer, entry.getValue().entity, false);
                }
                iterator.remove();
            }
        }
    }

    private static void sendFlags(ServerPlayer viewer, Entity entity, boolean reveal) {
        byte flags = 0;
        if (entity.isOnFire()) {
            flags |= 1;
        }
        if (entity.isShiftKeyDown()) {
            flags |= 1 << 1;
        }
        if (entity.isSprinting()) {
            flags |= 1 << 3;
        }
        if (entity.isSwimming()) {
            flags |= 1 << 4;
        }
        if (entity.isInvisible()) {
            flags |= 1 << 5;
        }
        if (reveal || entity.isCurrentlyGlowing()) {
            flags |= 1 << 6;
        }
        if (entity instanceof LivingEntity living && living.isFallFlying()) {
            flags |= (byte) (1 << 7);
        }
        // Data id 0 is Entity's shared-flags byte.
        viewer.connection.send(new ClientboundSetEntityDataPacket(entity.getId(),
                List.of(new SynchedEntityData.DataValue<>(0, EntityDataSerializers.BYTE, flags))));
    }

    private record Key(UUID viewerId, int entityId) {
    }

    private record Glow(Entity entity, long until) {
    }
}
