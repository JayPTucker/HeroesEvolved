package com.jayptucker.heroesevolved.time;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import com.jayptucker.heroesevolved.entity.TemporalGhostBlockEntity;
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
    private static final Map<UUID, EchoPlayback> ECHO_PLAYBACKS = new HashMap<>();
    private static final Map<UUID, EchoSnapshotContext> ECHO_CONTEXTS =
            new HashMap<>();
    private static final List<GhostBlock> GHOST_BLOCKS = new ArrayList<>();

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
            ECHO_PLAYBACKS.put(echo.getUUID(), new EchoPlayback(
                    echo, data, snapshot, level.getGameTime()
            ));
            ECHO_CONTEXTS.put(echo.getUUID(), new EchoSnapshotContext(
                    ownerId, data
            ));
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
            ECHO_PLAYBACKS.remove(echo.getUUID());
            ECHO_CONTEXTS.remove(echo.getUUID());
        }
    }

    /**
     * An attack turns a replay into a consequence rather than allowing a
     * player to delete the Past. Its replay and item-delivery behavior stop
     * immediately, then its normal mob AI pursues the attacker.
     */
    public static void provoke(
            TemporalEchoEntity echo,
            ServerPlayer attacker
    ) {
        ECHO_PLAYBACKS.remove(echo.getUUID());
        ECHO_RECIPIENTS.remove(echo.getUUID());
        echo.becomeHostile(attacker);
        if (echo.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    echo.getX(), echo.getY() + 1.0D, echo.getZ(),
                    36, 0.35D, 0.65D, 0.35D, 0.12D);
            level.playSound(null, echo.getX(), echo.getY(), echo.getZ(),
                    SoundEvents.WARDEN_ANGRY, SoundSource.HOSTILE,
                    0.65F, 1.35F);
        }
    }

    /** Drops only the inventory captured with this echo, never live inventory. */
    public static void onEchoKilled(TemporalEchoEntity echo) {
        EchoSnapshotContext context = ECHO_CONTEXTS.remove(echo.getUUID());
        ECHO_PLAYBACKS.remove(echo.getUUID());
        ECHO_RECIPIENTS.remove(echo.getUUID());
        if (context != null && echo.level() instanceof ServerLevel level) {
            TemporalParadoxService.onEchoKilled(
                    context.snapshotOwnerId(), context.data(), level,
                    echo.position()
            );
        }
    }

    public static void tick() {
        for (List<TemporalEchoEntity> echoes : SNAPSHOT_ECHOES.values()) {
            for (TemporalEchoEntity echo : echoes) {
                if (!echo.isHostile()) {
                    replayHistory(echo);
                    emitFlicker(echo);
                    transferOfferedItems(echo);
                }
            }
        }
        removeExpiredGhostBlocks();
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

    /** Replays a recorded minute of history, then loops back to its start. */
    private static void replayHistory(TemporalEchoEntity echo) {
        EchoPlayback playback = ECHO_PLAYBACKS.get(echo.getUUID());
        if (playback == null || echo.isRemoved()
                || !(echo.level() instanceof ServerLevel level)) {
            return;
        }

        int playbackTick = (int) ((level.getGameTime() - playback.startedAt())
                % TemporalEchoRecordingService.getRecordingLengthTicks());
        moveEchoToFrame(playback, playbackTick);
        replayActions(playback, playbackTick);
    }

    private static void moveEchoToFrame(EchoPlayback playback, int playbackTick) {
        List<TemporalEchoFrame> frames = playback.data().frames();
        if (frames.isEmpty()) {
            return;
        }

        TemporalEchoFrame previous = frames.getFirst();
        TemporalEchoFrame next = previous;
        for (TemporalEchoFrame frame : frames) {
            if (frame.playbackTick() > playbackTick) {
                next = frame;
                break;
            }
            previous = frame;
            next = frame;
        }

        double progress = next.playbackTick() == previous.playbackTick()
                ? 0.0D
                : (double) (playbackTick - previous.playbackTick())
                        / (next.playbackTick() - previous.playbackTick());
        double sourceX = lerp(progress, previous.x(), next.x());
        double sourceY = lerp(progress, previous.y(), next.y());
        double sourceZ = lerp(progress, previous.z(), next.z());
        float yaw = (float) lerp(progress, previous.yaw(), next.yaw());
        float pitch = (float) lerp(progress, previous.pitch(), next.pitch());
        PlayerTemporalSnapshotData snapshot = playback.snapshot();
        TemporalEchoEntity echo = playback.echo();
        // setPos lets the normal entity tracker send small relative movement
        // updates. teleportTo sends abrupt teleport packets and made remote
        // echoes look laggy even when the server replay was smooth.
        echo.setPos(
                snapshot.snapshotMinX() + (sourceX - snapshot.sourceMinX()),
                sourceY,
                snapshot.snapshotMinZ() + (sourceZ - snapshot.sourceMinZ())
        );
        echo.setYRot(yaw);
        echo.setYHeadRot(yaw);
        echo.setXRot(pitch);
    }

    private static void replayActions(EchoPlayback playback, int playbackTick) {
        if (!(playback.echo().level() instanceof ServerLevel level)) {
            return;
        }
        for (TemporalEchoAction action : playback.data().actions()) {
            if (action.playbackTick() == playbackTick) {
                showGhostAction(level, playback.snapshot(), action);
            }
        }
    }

    /**
     * Block actions are deliberately visual only. The snapshot's real blocks
     * are never edited by an echo replay, so history cannot create dupes.
     */
    private static void showGhostAction(
            ServerLevel level,
            PlayerTemporalSnapshotData snapshot,
            TemporalEchoAction action
    ) {
        Block block = BuiltInRegistries.BLOCK.get(action.blockId());
        BlockState state = (block == null ? Blocks.STONE : block).defaultBlockState();
        BlockPos source = action.position();
        double x = snapshot.snapshotMinX() + (source.getX() - snapshot.sourceMinX()) + 0.5D;
        double y = source.getY() + 0.5D;
        double z = snapshot.snapshotMinZ() + (source.getZ() - snapshot.sourceMinZ()) + 0.5D;

        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                x, y, z, action.placement() ? 10 : 22,
                0.22D, 0.22D, 0.22D, 0.05D);
        level.sendParticles(ParticleTypes.END_ROD,
                x, y, z, action.placement() ? 8 : 14,
                0.18D, 0.18D, 0.18D, 0.015D);

        if (action.placement()) {
            spawnGhostBlock(level, state, x - 0.5D, y - 0.5D, z - 0.5D);
        }

        SoundEvent sound = action.placement()
                ? state.getSoundType().getPlaceSound()
                : state.getSoundType().getBreakSound();
        level.playSound(null, x, y, z, sound, SoundSource.BLOCKS,
                0.55F, action.placement() ? 1.18F : 0.88F);
    }

    /** A block display is visual-only: it cannot collide, be mined, or alter the snapshot. */
    private static void spawnGhostBlock(
            ServerLevel level,
            BlockState state,
            double x,
            double y,
            double z
    ) {
        TemporalGhostBlockEntity ghost = new TemporalGhostBlockEntity(
                ModEntities.TEMPORAL_GHOST_BLOCK.get(), level
        );
        ghost.setBlockState(state);
        ghost.setPos(x, y, z);
        ghost.setGlowingTag(true);
        ghost.setNoGravity(true);
        level.addFreshEntity(ghost);
        GHOST_BLOCKS.add(new GhostBlock(ghost, level.getGameTime() + 20L));
    }

    private static void removeExpiredGhostBlocks() {
        GHOST_BLOCKS.removeIf(ghost -> {
            if (!ghost.display().isRemoved()
                    && ghost.display().level().getGameTime() >= ghost.expiresAt()) {
                ghost.display().discard();
            }
            return ghost.display().isRemoved();
        });
    }

    private static double lerp(double progress, double start, double end) {
        return start + (end - start) * progress;
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

    private record EchoPlayback(
            TemporalEchoEntity echo,
            TemporalEchoData data,
            PlayerTemporalSnapshotData snapshot,
            long startedAt
    ) {
    }

    private record EchoSnapshotContext(
            UUID snapshotOwnerId,
            TemporalEchoData data
    ) {
    }

    private record GhostBlock(TemporalGhostBlockEntity display, long expiresAt) {
    }
}
