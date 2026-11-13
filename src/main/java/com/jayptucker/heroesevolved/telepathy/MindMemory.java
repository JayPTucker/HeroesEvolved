package com.jayptucker.heroesevolved.telepathy;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What Memory Walk reads: where creatures have been over the last minute and who hurt them. Every player is
 * remembered; other creatures only within 64 blocks of someone who has learned Memory Walk, at most 256 at a time. One
 * place every half second, a minute back, so a memory is at most 120 small entries.
 */
public final class MindMemory {
    /** Where a creature was at game time {@code time}; {@code hurtBy} names whoever hurt it there (or null). */
    public record Moment(long time, Vec3 at, String hurtBy) {}

    private static final class Memory {
        final ServerLevel level;
        final Deque<Moment> moments = new ArrayDeque<>();
        String pendingHurt;
        long seen;
        Memory(ServerLevel level) { this.level = level; }
    }

    private static final Map<UUID, Memory> MEMORIES = new HashMap<>();
    private static final int LIMIT = 256, SPAN = TelepathyGeometry.MEMORY_DEEP;

    private MindMemory() {}

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now % TelepathyGeometry.MEMORY_STEP != 0) return;
        int recorded = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isSpectator()) record(player, now);
            recorded++;
        }
        for (ServerPlayer walker : server.getPlayerList().getPlayers()) {
            if (recorded >= LIMIT || !TelepathySkills.has(walker, TelepathySkills.MEMORY_WALK)) continue;
            for (LivingEntity creature : walker.serverLevel().getEntitiesOfClass(LivingEntity.class, walker.getBoundingBox().inflate(64),
                    e -> !(e instanceof ServerPlayer) && e.isAlive())) {
                if (recorded++ >= LIMIT) break;
                record(creature, now);
            }
        }
        MEMORIES.values().removeIf(m -> now - m.seen > SPAN);
    }

    private static void record(LivingEntity entity, long now) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        Memory memory = MEMORIES.get(entity.getUUID());
        if (memory == null || memory.level != level) {
            memory = new Memory(level);
            MEMORIES.put(entity.getUUID(), memory);
        }
        if (memory.seen == now) return;
        memory.seen = now;
        memory.moments.addLast(new Moment(now, entity.position(), memory.pendingHurt));
        memory.pendingHurt = null;
        while (!memory.moments.isEmpty() && now - memory.moments.peekFirst().time() > SPAN) memory.moments.removeFirst();
    }

    /** Remembers who hurt a creature; the next place remembered for it carries their name. */
    public static void hurt(LivingEntity victim, Entity attacker) {
        Memory memory = MEMORIES.get(victim.getUUID());
        if (memory == null || attacker == null || attacker == victim) return;
        memory.pendingHurt = attacker.getName().getString();
    }

    /** Where a creature has been since {@code since}, oldest first. */
    public static List<Moment> path(UUID id, long since) {
        Memory memory = MEMORIES.get(id);
        if (memory == null) return List.of();
        List<Moment> out = new ArrayList<>();
        for (Moment m : memory.moments) if (m.time() >= since) out.add(m);
        return out;
    }

    /**
     * Everyone remembered passing within {@code radius} of {@code point} in {@code level} since {@code since}, those who
     * were there most recently first, at most {@code limit}, never {@code except}.
     */
    public static List<UUID> passedThrough(ServerLevel level, Vec3 point, double radius, long since, int limit, UUID except) {
        Map<UUID, Long> last = new HashMap<>();
        for (var entry : MEMORIES.entrySet()) {
            if (entry.getValue().level != level || entry.getKey().equals(except)) continue;
            for (Moment m : entry.getValue().moments)
                if (m.time() >= since && m.at().distanceToSqr(point) <= radius * radius) last.merge(entry.getKey(), m.time(), Math::max);
        }
        return last.entrySet().stream().sorted(Map.Entry.<UUID, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(limit).map(Map.Entry::getKey).toList();
    }

    /** For tests: remembers a place for a creature at a given time. */
    public static void remember(LivingEntity entity, long time, Vec3 at, String hurtBy) {
        Memory memory = MEMORIES.computeIfAbsent(entity.getUUID(), k -> new Memory((ServerLevel) entity.level()));
        memory.seen = time;
        memory.moments.addLast(new Moment(time, at, hurtBy));
    }

    public static void forget(UUID id) { MEMORIES.remove(id); }

    public static void clear() { MEMORIES.clear(); }
}
