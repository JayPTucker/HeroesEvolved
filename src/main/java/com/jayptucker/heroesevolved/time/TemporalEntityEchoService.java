package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.data.TemporalErasureSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recreates the mobs and animals captured in a snapshot as real entities in
 * the Past, replaying the minute of movement recorded before it was made.
 *
 * <p>A replaying entity is a memory: it cannot be hurt or used. Striking it
 * disturbs history, and it wakes as an ordinary mob. Killing a woken mob
 * changes the timeline, and its Present-day counterpart is erased.</p>
 */
public final class TemporalEntityEchoService {
    /** Marks Past copies so stray ones saved with a chunk can be removed. */
    public static final String PAST_COPY_TAG = "heroesevolved_past_copy";

    private static final Map<UUID, List<PastEntity>> SNAPSHOT_ENTITIES =
            new HashMap<>();
    private static final Map<UUID, PastEntity> BY_COPY_ID = new HashMap<>();

    private TemporalEntityEchoService() {
    }

    public static boolean isPastCopy(Entity entity) {
        return entity.getTags().contains(PAST_COPY_TAG);
    }

    /** True for a Past copy this server is currently managing. */
    public static boolean isTracked(Entity entity) {
        return BY_COPY_ID.containsKey(entity.getUUID());
    }

    /** True while the entity is still replaying recorded history. */
    public static boolean isReplaying(Entity entity) {
        PastEntity pastEntity = BY_COPY_ID.get(entity.getUUID());
        return pastEntity != null && !pastEntity.awake;
    }

    public static void spawn(
            UUID ownerId,
            ServerLevel level,
            PlayerTemporalSnapshotData snapshot
    ) {
        remove(ownerId);
        TemporalErasureSavedData erasures =
                TemporalErasureSavedData.get(level.getServer());
        List<PastEntity> spawned = new ArrayList<>();

        for (TemporalEntityEchoData data : snapshot.entityEchoes()) {
            // An entity killed during an earlier visit stays dead in this Past.
            if (erasures.isErased(ownerId, data.entityId())) {
                continue;
            }

            Entity entity = EntityType.create(data.entity().copy(), level)
                    .orElse(null);
            if (entity == null) {
                continue;
            }

            entity.setPos(
                    snapshot.snapshotMinX() + (entity.getX() - snapshot.sourceMinX()),
                    entity.getY(),
                    snapshot.snapshotMinZ() + (entity.getZ() - snapshot.sourceMinZ())
            );
            entity.addTag(PAST_COPY_TAG);
            entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            freeze(entity);

            PastEntity pastEntity = new PastEntity(
                    ownerId, entity, data, snapshot, level.getGameTime()
            );
            // Track before adding, so the join filter recognises the copy.
            BY_COPY_ID.put(entity.getUUID(), pastEntity);
            if (!level.addFreshEntity(entity)) {
                BY_COPY_ID.remove(entity.getUUID());
                continue;
            }
            spawned.add(pastEntity);
        }

        SNAPSHOT_ENTITIES.put(ownerId, spawned);
    }

    public static void remove(UUID ownerId) {
        List<PastEntity> entities = SNAPSHOT_ENTITIES.remove(ownerId);
        if (entities == null) {
            return;
        }

        for (PastEntity pastEntity : entities) {
            BY_COPY_ID.remove(pastEntity.entity.getUUID());
            if (!pastEntity.entity.isRemoved()) {
                pastEntity.entity.discard();
            }
        }
    }

    public static boolean hasEntities(UUID ownerId) {
        return SNAPSHOT_ENTITIES.containsKey(ownerId);
    }

    public static Iterable<UUID> owners() {
        return List.copyOf(SNAPSHOT_ENTITIES.keySet());
    }

    public static void tick() {
        for (List<PastEntity> entities : SNAPSHOT_ENTITIES.values()) {
            for (PastEntity pastEntity : entities) {
                if (!pastEntity.awake && !pastEntity.entity.isRemoved()) {
                    replay(pastEntity);
                }
            }
        }
    }

    /** The first blow against a memory wakes it into a living Past mob. */
    public static void disturb(Entity entity, ServerPlayer attacker) {
        PastEntity pastEntity = BY_COPY_ID.get(entity.getUUID());
        if (pastEntity == null || pastEntity.awake) {
            return;
        }

        pastEntity.awake = true;
        CompoundTag original = pastEntity.data.entity();
        entity.setNoGravity(original.getBoolean("NoGravity"));
        entity.setInvulnerable(original.getBoolean("Invulnerable"));
        if (entity instanceof Mob mob) {
            mob.setNoAi(original.getBoolean("NoAI"));
        }

        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    entity.getX(), entity.getY() + entity.getBbHeight() / 2,
                    entity.getZ(), 24, 0.3D, 0.4D, 0.3D, 0.08D);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL,
                    0.8F, 0.6F);
        }
        attacker.displayClientMessage(Component.literal(
                "You have disturbed the Past."
        ).withStyle(ChatFormatting.DARK_AQUA), true);
    }

    /**
     * A mob killed in the Past never lived on to the Present. Its counterpart
     * vanishes now if loaded, or the moment its chunk next loads.
     */
    public static void onPastEntityKilled(
            LivingEntity entity,
            ServerPlayer killer
    ) {
        PastEntity pastEntity = BY_COPY_ID.remove(entity.getUUID());
        if (pastEntity == null) {
            return;
        }

        MinecraftServer server = entity.getServer();
        if (server == null) {
            return;
        }

        UUID presentId = pastEntity.data.entityId();
        TemporalErasureSavedData.get(server).erase(pastEntity.ownerId, presentId);

        ServerLevel present = server.getLevel(ResourceKey.create(
                Registries.DIMENSION,
                pastEntity.snapshot.sourceDimension()
        ));
        Entity counterpart = present == null ? null : present.getEntity(presentId);
        if (counterpart != null && !counterpart.isRemoved()) {
            present.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    counterpart.getX(),
                    counterpart.getY() + counterpart.getBbHeight() / 2,
                    counterpart.getZ(), 30, 0.3D, 0.5D, 0.3D, 0.1D);
            counterpart.discard();
        }

        if (killer != null) {
            killer.sendSystemMessage(Component.literal(
                    "Somewhere in the Present, " + entity.getName().getString()
                            + " never lived to see this day."
            ).withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    /** Keeps a recreated entity still until its recorded motion moves it. */
    private static void freeze(Entity entity) {
        entity.setNoGravity(true);
        entity.setInvulnerable(true);
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
            // Replaying memories must never despawn mid-visit.
            mob.setPersistenceRequired();
        }
    }

    private static void replay(PastEntity pastEntity) {
        TemporalEntityEchoData data = pastEntity.data;
        int frameCount = data.frameCount();
        if (frameCount == 0
                || !(pastEntity.entity.level() instanceof ServerLevel level)) {
            return;
        }

        int playbackTick = (int) ((level.getGameTime() - pastEntity.startedAt)
                % TemporalEchoRecordingService.getRecordingLengthTicks());

        int previous = 0;
        int next = 0;
        for (int frame = 0; frame < frameCount; frame++) {
            if (data.playbackTick(frame) > playbackTick) {
                next = frame;
                break;
            }
            previous = frame;
            next = frame;
        }

        int previousTick = data.playbackTick(previous);
        int nextTick = data.playbackTick(next);
        double progress = nextTick == previousTick
                ? 0.0D
                : (double) (playbackTick - previousTick) / (nextTick - previousTick);
        progress = Math.clamp(progress, 0.0D, 1.0D);

        PlayerTemporalSnapshotData snapshot = pastEntity.snapshot;
        Entity entity = pastEntity.entity;
        entity.setPos(
                snapshot.snapshotMinX()
                        + (lerp(progress, data.x(previous), data.x(next))
                        - snapshot.sourceMinX()),
                lerp(progress, data.y(previous), data.y(next)),
                snapshot.snapshotMinZ()
                        + (lerp(progress, data.z(previous), data.z(next))
                        - snapshot.sourceMinZ())
        );

        float yaw = lerpDegrees(progress, data.yaw(previous), data.yaw(next));
        entity.setYRot(yaw);
        entity.setXRot((float) lerp(progress,
                data.pitch(previous), data.pitch(next)));
        entity.setYHeadRot(lerpDegrees(progress,
                data.headYaw(previous), data.headYaw(next)));
        if (entity instanceof LivingEntity living) {
            living.yBodyRot = yaw;
        }
    }

    private static double lerp(double progress, double start, double end) {
        return start + (end - start) * progress;
    }

    /** Interpolates across the -180/180 seam instead of spinning around. */
    private static float lerpDegrees(double progress, float start, float end) {
        float delta = net.minecraft.util.Mth.wrapDegrees(end - start);
        return (float) (start + delta * progress);
    }

    private static final class PastEntity {
        private final UUID ownerId;
        private final Entity entity;
        private final TemporalEntityEchoData data;
        private final PlayerTemporalSnapshotData snapshot;
        private final long startedAt;
        private boolean awake;

        private PastEntity(
                UUID ownerId,
                Entity entity,
                TemporalEntityEchoData data,
                PlayerTemporalSnapshotData snapshot,
                long startedAt
        ) {
            this.ownerId = ownerId;
            this.entity = entity;
            this.data = data;
            this.snapshot = snapshot;
            this.startedAt = startedAt;
        }
    }
}
