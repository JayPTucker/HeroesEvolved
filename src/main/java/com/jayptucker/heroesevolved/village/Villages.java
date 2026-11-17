package com.jayptucker.heroesevolved.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Questions about a village every village event asks: where its bell is, and who belongs to it. */
public final class Villages {
    /** A village is everyone within this of the bell, or whose home, job or meeting place is. */
    public static final int RADIUS = 64;

    private static final List<MemoryModuleType<GlobalPos>> PLACES = List.of(MemoryModuleType.HOME, MemoryModuleType.JOB_SITE,
            MemoryModuleType.POTENTIAL_JOB_SITE, MemoryModuleType.MEETING_POINT);

    private Villages() {}

    /** The village bell nearest {@code near} within {@link #RADIUS}, or {@code near} itself if there is none. */
    public static BlockPos bell(ServerLevel level, BlockPos near) {
        return level.getPoiManager().findClosest(poi -> poi.is(PoiTypes.MEETING), near, RADIUS, PoiManager.Occupancy.ANY).orElse(near);
    }

    /** Whether there is a bell within {@link #RADIUS} of {@code near}. */
    public static boolean hasBell(ServerLevel level, BlockPos near) {
        return level.getPoiManager().findClosest(poi -> poi.is(PoiTypes.MEETING), near, RADIUS, PoiManager.Occupancy.ANY).isPresent();
    }

    /** Living villagers who belong to the village round {@code bell}. */
    public static List<Villager> members(ServerLevel level, BlockPos bell) {
        double reach = RADIUS + 48;
        return level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(reach, 64, reach), v -> v.isAlive() && belongs(level, bell, v));
    }

    /** Near the bell, or living, working or meeting in the village. */
    public static boolean belongs(ServerLevel level, BlockPos bell, Villager v) {
        if (v.blockPosition().distSqr(bell) <= (long) RADIUS * RADIUS) return true;
        var brain = v.getBrain();
        for (var place : PLACES) {
            // Check before fetching: fetching a memory a brain doesn't register throws.
            if (!brain.hasMemoryValue(place)) continue;
            GlobalPos at = brain.getMemory(place).orElse(null);
            if (at != null && at.dimension() == level.dimension() && at.pos().distSqr(bell) <= (long) RADIUS * RADIUS) return true;
        }
        return false;
    }
}
