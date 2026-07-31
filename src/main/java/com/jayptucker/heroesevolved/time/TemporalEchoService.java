package com.jayptucker.heroesevolved.time;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import com.jayptucker.heroesevolved.registry.ModEntities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Manages non-interactive visual echoes for Temporal Snapshot travel. */
public final class TemporalEchoService {
    private static final Map<UUID, List<TemporalEchoEntity>> SNAPSHOT_ECHOES =
            new HashMap<>();
    private static final Map<UUID, UUID> ECHO_RECIPIENTS = new HashMap<>();

    private TemporalEchoService() {
    }

    public static void spawnSnapshotEchoes(
            UUID ownerId,
            ServerLevel level,
            PlayerTemporalSnapshotData snapshot
    ) {
        removeSnapshotEchoes(ownerId);
        List<TemporalEchoEntity> echoes = new ArrayList<>();
        for (TemporalEchoData data : snapshot.echoes()) {
            double snapshotX = snapshot.snapshotMinX()
                    + (data.x() - snapshot.sourceMinX());
            double snapshotZ = snapshot.snapshotMinZ()
                    + (data.z() - snapshot.sourceMinZ());
            TemporalEchoEntity echo = createEcho(
                    level,
                    data.playerId(),
                    data.playerName(),
                    snapshotX,
                    data.y(),
                    snapshotZ,
                    data.yaw(),
                    data.playerName() + " (Past Echo)"
            );
            ECHO_RECIPIENTS.put(echo.getUUID(), data.playerId());
            echoes.add(echo);
        }
        SNAPSHOT_ECHOES.put(ownerId, echoes);
    }

    public static void removeSnapshotEchoes(UUID ownerId) {
        List<TemporalEchoEntity> echoes = SNAPSHOT_ECHOES.remove(ownerId);
        if (echoes == null) {
            return;
        }

        for (TemporalEchoEntity echo : echoes) {
            if (!echo.isRemoved()) {
                echo.discard();
            }
            ECHO_RECIPIENTS.remove(echo.getUUID());
        }
    }

    public static void tick() {
        for (List<TemporalEchoEntity> echoes : SNAPSHOT_ECHOES.values()) {
            for (TemporalEchoEntity echo : echoes) {
                emitFlicker(echo);
                transferOfferedItems(echo);
            }
        }
    }

    private static TemporalEchoEntity createEcho(
            ServerLevel level,
            UUID profileId,
            String profileName,
            double x,
            double y,
            double z,
            float yaw,
            String name
    ) {
        TemporalEchoEntity echo = new TemporalEchoEntity(
                ModEntities.TEMPORAL_ECHO.get(), level, profileId, profileName
        );
        echo.setPos(x, y, z);
        echo.setYRot(yaw);
        echo.setYHeadRot(yaw);
        echo.setNoGravity(true);
        echo.setInvulnerable(true);
        echo.setSilent(true);
        echo.setCustomName(Component.literal(name));
        echo.setCustomNameVisible(true);
        echo.setGlowingTag(true);
        level.addFreshEntity(echo);
        return echo;
    }

    private static void emitFlicker(TemporalEchoEntity echo) {
        if (!echo.isRemoved() && echo.level() instanceof ServerLevel level
                && level.getGameTime() % 4 == 0) {
            level.sendParticles(ParticleTypes.END_ROD,
                    echo.getX(), echo.getY() + 1.0D, echo.getZ(),
                    2, 0.20D, 0.45D, 0.20D, 0.0D);
        }
    }

    /**
     * Each Past echo is an address to its captured player's real inventory.
     * The inventory follows that player across timelines.
     */
    private static void transferOfferedItems(TemporalEchoEntity echo) {
        UUID recipientId = ECHO_RECIPIENTS.get(echo.getUUID());
        if (recipientId == null || echo.isRemoved()
                || !(echo.level() instanceof ServerLevel level)) {
            return;
        }

        ServerPlayer recipient = level.getServer().getPlayerList().getPlayer(
                recipientId
        );
        if (recipient == null) {
            return;
        }

        for (ItemEntity itemEntity : level.getEntitiesOfClass(
                ItemEntity.class,
                echo.getBoundingBox().inflate(1.25D),
                item -> true
        )) {
            ItemStack offered = itemEntity.getItem();
            int originalCount = offered.getCount();
            recipient.getInventory().add(offered);

            if (offered.getCount() == originalCount) {
                continue;
            }

            if (offered.isEmpty()) {
                itemEntity.discard();
            } else {
                itemEntity.setItem(offered);
            }

            level.playSound(null, echo.getX(), echo.getY(), echo.getZ(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                    0.75F, 1.15F);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    echo.getX(), echo.getY() + 1.0D, echo.getZ(),
                    12, 0.25D, 0.50D, 0.25D, 0.05D);
        }
    }
}
