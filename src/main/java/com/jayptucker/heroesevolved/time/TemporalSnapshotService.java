package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maintains permanent, player-owned copies of a small region of the present.
 * Copying is spread across server ticks so creating a snapshot cannot freeze
 * a multiplayer server.
 */
public final class TemporalSnapshotService {
    public static final ResourceKey<Level> TEMPORAL_DIMENSION =
            ResourceKey.create(
                    Registries.DIMENSION,
                    ResourceLocation.fromNamespaceAndPath(
                            HeroesEvolved.MOD_ID,
                            "temporal_snapshot"
                    )
            );

    private static final int SLOT_SPACING = 256;
    private static final Map<UUID, SnapshotBuildTask> ACTIVE_BUILDS =
            new HashMap<>();

    private TemporalSnapshotService() {
    }

    public static boolean isTemporalDimension(ServerLevel level) {
        return level.dimension().equals(TEMPORAL_DIMENSION);
    }

    public static boolean beginSnapshot(ServerPlayer player) {
        if (isTemporalDimension(player.serverLevel())
                || ACTIVE_BUILDS.containsKey(player.getUUID())) {
            return false;
        }

        MinecraftServer server = player.getServer();
        ServerLevel snapshotLevel = server == null
                ? null
                : server.getLevel(TEMPORAL_DIMENSION);

        if (snapshotLevel == null) {
            return false;
        }

        int widthBlocks = snapshotWidthBlocks();
        int sourceMinX = (player.chunkPosition().x
                - HeroesEvolvedConfig.COMMON.timeSnapshotWidthChunks.get() / 2)
                << 4;
        int sourceMinZ = (player.chunkPosition().z
                - HeroesEvolvedConfig.COMMON.timeSnapshotWidthChunks.get() / 2)
                << 4;
        BlockPos snapshotMin = getSnapshotMinimum(player.getUUID());

        PlayerTemporalSnapshotData data = new PlayerTemporalSnapshotData(
                true,
                false,
                player.serverLevel().dimension().location(),
                sourceMinX,
                sourceMinZ,
                snapshotMin.getX(),
                snapshotMin.getZ(),
                captureEchoes(player, sourceMinX, sourceMinZ, widthBlocks)
        );
        TemporalChestService.clearSnapshotHistory(player);
        player.setData(ModDataAttachments.PLAYER_TEMPORAL_SNAPSHOT.get(), data);

        ACTIVE_BUILDS.put(
                player.getUUID(),
                new SnapshotBuildTask(player.getUUID(), player.serverLevel(),
                        snapshotLevel, data, widthBlocks)
        );
        player.serverLevel().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS,
                0.80F,
                0.75F
        );
        player.displayClientMessage(
                Component.literal("Your temporal snapshot is forming..."),
                true
        );
        return true;
    }

    public static boolean enterSnapshot(ServerPlayer player) {
        PlayerTemporalSnapshotData data = player.getData(
                ModDataAttachments.PLAYER_TEMPORAL_SNAPSHOT.get()
        );
        if (!data.ready() || ACTIVE_BUILDS.containsKey(player.getUUID())) {
            return false;
        }

        MinecraftServer server = player.getServer();
        ServerLevel snapshotLevel = server == null
                ? null
                : server.getLevel(TEMPORAL_DIMENSION);
        if (snapshotLevel == null) {
            return false;
        }

        List<ServerPlayer> travellers = nearbyPlayers(player);
        for (ServerPlayer traveller : travellers) {
            visitSnapshot(traveller, player.getUUID(), data, snapshotLevel,
                    player);
        }
        TemporalEchoService.spawnSnapshotEchoes(
                player.getUUID(), snapshotLevel, data
        );
        return true;
    }

    public static boolean returnToPresent(ServerPlayer player) {
        PlayerTemporalVisitData visit = player.getData(
                ModDataAttachments.PLAYER_TEMPORAL_VISIT.get()
        );
        if (!visit.visiting() || !isTemporalDimension(player.serverLevel())) {
            return false;
        }

        MinecraftServer server = player.getServer();
        ResourceKey<Level> sourceKey = ResourceKey.create(
                Registries.DIMENSION,
                visit.sourceDimension()
        );
        ServerLevel sourceLevel = server == null ? null : server.getLevel(sourceKey);
        if (sourceLevel == null) {
            return false;
        }

        List<ServerPlayer> returningTravellers = nearbyPlayers(player).stream()
                .filter(traveller -> {
                    PlayerTemporalVisitData travellerVisit = traveller.getData(
                            ModDataAttachments.PLAYER_TEMPORAL_VISIT.get()
                    );
                    return travellerVisit.visiting()
                            && travellerVisit.ownerId().equals(visit.ownerId());
                })
                .toList();
        boolean showParadoxWarning = TemporalChestService
                .hasPendingParadoxWarning(visit.ownerId());
        boolean showRestorationMessage = TemporalChestService
                .consumeRestorationMessage(visit.ownerId());

        for (ServerPlayer traveller : returningTravellers) {
            PlayerTemporalVisitData travellerVisit = traveller.getData(
                    ModDataAttachments.PLAYER_TEMPORAL_VISIT.get()
            );
            returnTraveller(
                    traveller,
                    travellerVisit,
                    sourceLevel,
                    showParadoxWarning,
                    showRestorationMessage
            );
        }
        if (showParadoxWarning) {
            // The entire return group saw the message, so this snapshot is
            // ready to track its next paradox independently.
            TemporalChestService.clearParadoxWarning(visit.ownerId());
        }
        if (player.getUUID().equals(visit.ownerId())) {
            TemporalEchoService.removeSnapshotEchoes(visit.ownerId());
        }
        return true;
    }

    public static boolean isVisitorInsideSnapshot(
            ServerPlayer player,
            BlockPos position
    ) {
        PlayerTemporalVisitData visit = player.getData(
                ModDataAttachments.PLAYER_TEMPORAL_VISIT.get()
        );
        int width = snapshotWidthBlocks();
        return visit.visiting()
                && position.getX() >= visit.snapshotMinX()
                && position.getX() < visit.snapshotMinX() + width
                && position.getZ() >= visit.snapshotMinZ()
                && position.getZ() < visit.snapshotMinZ() + width;
    }

    public static BlockPos toPresentPosition(
            ServerPlayer player,
            BlockPos snapshotPosition
    ) {
        PlayerTemporalVisitData visit = player.getData(
                ModDataAttachments.PLAYER_TEMPORAL_VISIT.get()
        );
        return new BlockPos(
                visit.sourceMinX() + snapshotPosition.getX()
                        - visit.snapshotMinX(),
                snapshotPosition.getY(),
                visit.sourceMinZ() + snapshotPosition.getZ()
                        - visit.snapshotMinZ()
        );
    }

    public static ServerLevel getPresentLevel(ServerPlayer player) {
        PlayerTemporalVisitData visit = player.getData(
                ModDataAttachments.PLAYER_TEMPORAL_VISIT.get()
        );
        MinecraftServer server = player.getServer();
        return server == null ? null : server.getLevel(ResourceKey.create(
                Registries.DIMENSION,
                visit.sourceDimension()
        ));
    }

    public static void tick(MinecraftServer server) {
        TemporalEchoService.tick();
        int budget = HeroesEvolvedConfig.COMMON
                .timeSnapshotCopyBlocksPerTick.get();
        var iterator = ACTIVE_BUILDS.values().iterator();
        while (iterator.hasNext()) {
            SnapshotBuildTask task = iterator.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(task.ownerId);
            if (owner == null || task.copyNextBlocks(budget)) {
                if (owner != null) {
                    PlayerTemporalSnapshotData data = owner.getData(
                            ModDataAttachments.PLAYER_TEMPORAL_SNAPSHOT.get()
                    );
                    owner.setData(
                            ModDataAttachments.PLAYER_TEMPORAL_SNAPSHOT.get(),
                            data.withReady()
                    );
                    owner.displayClientMessage(
                            Component.literal("Your temporal snapshot is ready."),
                            true
                    );
                    owner.serverLevel().playSound(
                            null,
                            owner.getX(), owner.getY(), owner.getZ(),
                            SoundEvents.AMETHYST_BLOCK_CHIME,
                            SoundSource.PLAYERS,
                            0.90F,
                            1.25F
                    );
                }
                iterator.remove();
            }
        }
    }

    private static void visitSnapshot(
            ServerPlayer traveller,
            UUID ownerId,
            PlayerTemporalSnapshotData data,
            ServerLevel snapshotLevel,
            ServerPlayer leader
    ) {
        int width = snapshotWidthBlocks();
        double offsetX = traveller.getX() - leader.getX();
        double offsetZ = traveller.getZ() - leader.getZ();
        // The saved past uses the same coordinate layout as the captured
        // Present region. Enter at the matching location, not its centre.
        double leaderX = data.snapshotMinX() + clampToSnapshot(
                leader.getX() - data.sourceMinX(), width
        );
        double leaderZ = data.snapshotMinZ() + clampToSnapshot(
                leader.getZ() - data.sourceMinZ(), width
        );
        double x = leaderX + offsetX;
        double z = leaderZ + offsetZ;

        traveller.setData(ModDataAttachments.PLAYER_TEMPORAL_VISIT.get(),
                new PlayerTemporalVisitData(true, ownerId, data.sourceDimension(),
                        data.sourceMinX(), data.sourceMinZ(),
                        data.snapshotMinX(), data.snapshotMinZ()));
        traveller.teleportTo(snapshotLevel, x, traveller.getY(), z,
                traveller.getYRot(), traveller.getXRot());
        snapshotLevel.playSound(
                null,
                traveller.getX(), traveller.getY(), traveller.getZ(),
                ModSounds.TIME_SLOW.get(),
                SoundSource.PLAYERS,
                0.90F,
                1.0F
        );
    }

    private static void returnTraveller(
            ServerPlayer traveller,
            PlayerTemporalVisitData visit,
            ServerLevel sourceLevel,
            boolean showParadoxWarning,
            boolean showRestorationMessage
    ) {
        double x = visit.sourceMinX() + traveller.getX()
                - visit.snapshotMinX();
        double z = visit.sourceMinZ() + traveller.getZ()
                - visit.snapshotMinZ();
        traveller.teleportTo(sourceLevel, x, traveller.getY(), z,
                traveller.getYRot(), traveller.getXRot());
        sourceLevel.playSound(
                null,
                traveller.getX(), traveller.getY(), traveller.getZ(),
                ModSounds.TIME_SLOW.get(),
                SoundSource.PLAYERS,
                0.90F,
                1.0F
        );
        traveller.setData(ModDataAttachments.PLAYER_TEMPORAL_VISIT.get(),
                PlayerTemporalVisitData.empty());
        if (showParadoxWarning) {
            traveller.sendSystemMessage(Component.literal(
                    "You feel a strange rumble beneath your feet."
            ));
        }
        if (showRestorationMessage) {
            traveller.sendSystemMessage(Component.literal(
                    "Timeline is restored, the rumbling stops."
            ));
        }
    }

    private static List<ServerPlayer> nearbyPlayers(ServerPlayer player) {
        double radiusSquared = Math.pow(HeroesEvolvedConfig.COMMON
                .timeSnapshotGroupTravelRadius.get(), 2);
        return player.serverLevel().players().stream()
                .filter(candidate -> candidate.distanceToSqr(player)
                        <= radiusSquared)
                .toList();
    }

    private static int snapshotWidthBlocks() {
        return HeroesEvolvedConfig.COMMON.timeSnapshotWidthChunks.get() * 16;
    }

    private static List<TemporalEchoData> captureEchoes(
            ServerPlayer owner,
            int sourceMinX,
            int sourceMinZ,
            int widthBlocks
    ) {
        int sourceMaxX = sourceMinX + widthBlocks;
        int sourceMaxZ = sourceMinZ + widthBlocks;
        return owner.serverLevel().players().stream()
                .filter(player -> player.getX() >= sourceMinX
                        && player.getX() < sourceMaxX
                        && player.getZ() >= sourceMinZ
                        && player.getZ() < sourceMaxZ)
                .map(player -> new TemporalEchoData(
                        player.getUUID(),
                        player.getName().getString(),
                        player.getX(), player.getY(), player.getZ(),
                        player.getYRot(), player.getXRot()
                ))
                .toList();
    }

    private static double clampToSnapshot(double coordinate, int width) {
        // Leave a small margin so a player travelling later from far away
        // cannot be placed inside the snapshot boundary.
        return Math.clamp(coordinate, 1.5D, width - 1.5D);
    }

    private static BlockPos getSnapshotMinimum(UUID ownerId) {
        long identity = ownerId.getMostSignificantBits()
                ^ ownerId.getLeastSignificantBits();
        int gridX = (int) identity & 0xFFFF;
        int gridZ = (int) (identity >>> 16) & 0xFFFF;
        return new BlockPos(
                (gridX - 32_768) * SLOT_SPACING + 32,
                0,
                (gridZ - 32_768) * SLOT_SPACING + 32
        );
    }

    private static final class SnapshotBuildTask {
        private final UUID ownerId;
        private final ServerLevel source;
        private final ServerLevel destination;
        private final PlayerTemporalSnapshotData data;
        private final int width;
        private final int minY;
        private final int maxY;
        private int x;
        private int y;
        private int z;

        private SnapshotBuildTask(UUID ownerId, ServerLevel source,
                ServerLevel destination, PlayerTemporalSnapshotData data,
                int width) {
            this.ownerId = ownerId;
            this.source = source;
            this.destination = destination;
            this.data = data;
            this.width = width;
            this.minY = source.getMinBuildHeight();
            this.maxY = source.getMaxBuildHeight() - 1;
            this.y = minY;
        }

        private boolean copyNextBlocks(int budget) {
            BlockPos.MutableBlockPos sourcePos = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos destinationPos = new BlockPos.MutableBlockPos();
            for (int copied = 0; copied < budget; copied++) {
                sourcePos.set(data.sourceMinX() + x, y, data.sourceMinZ() + z);
                destinationPos.set(data.snapshotMinX() + x, y,
                        data.snapshotMinZ() + z);
                BlockState state = source.getBlockState(sourcePos);
                if (!destination.getBlockState(destinationPos).equals(state)) {
                    destination.setBlock(destinationPos, state, 2);
                }
                copyChestContents(sourcePos, destinationPos);

                if (++y > maxY) {
                    y = minY;
                    if (++x >= width) {
                        x = 0;
                        if (++z >= width) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        /** Chest inventories are block-entity data, not part of BlockState. */
        private void copyChestContents(
                BlockPos sourcePos,
                BlockPos destinationPos
        ) {
            BlockEntity sourceEntity = source.getBlockEntity(sourcePos);
            BlockEntity destinationEntity = destination.getBlockEntity(
                    destinationPos
            );

            if (!(sourceEntity instanceof ChestBlockEntity sourceChest)
                    || !(destinationEntity instanceof ChestBlockEntity
                    destinationChest)) {
                return;
            }

            for (int slot = 0; slot < sourceChest.getContainerSize(); slot++) {
                destinationChest.setItem(
                        slot,
                        sourceChest.getItem(slot).copy()
                );
            }
            destinationChest.setChanged();
        }
    }
}
