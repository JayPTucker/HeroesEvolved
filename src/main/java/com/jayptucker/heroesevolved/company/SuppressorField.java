package com.jayptucker.heroesevolved.company;

import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The quiet around a Suppressor: within {@link #RADIUS} blocks of one who is awake, no power answers,
 * a player's or Gray's. It reaches through walls, as the Eclipse does. Only server-side Suppressors
 * that are standing and awake are counted.
 */
public final class SuppressorField {
    public static final double RADIUS = 9;
    private static final Set<CompanyAgentEntity> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());

    private SuppressorField() {}

    static void update(CompanyAgentEntity suppressor, boolean active) {
        if (active) ACTIVE.add(suppressor); else ACTIVE.remove(suppressor);
    }

    /** True if {@code entity} stands inside an awake Suppressor's field. */
    public static boolean suppresses(Entity entity) {
        return source(entity) != null;
    }

    /** The Suppressor whose field {@code entity} is in, or null. */
    public static CompanyAgentEntity source(Entity entity) {
        if (ACTIVE.isEmpty() || entity.level().isClientSide()) return null;
        for (CompanyAgentEntity suppressor : List.copyOf(ACTIVE)) {
            if (suppressor.isRemoved() || !suppressor.isAlive() || suppressor.outCold()) { ACTIVE.remove(suppressor); continue; }
            if (suppressor.level() == entity.level() && suppressor.distanceToSqr(entity) <= RADIUS * RADIUS) return suppressor;
        }
        return null;
    }

    /** Every awake Suppressor, for tests and diagnostics. */
    public static List<CompanyAgentEntity> active() { return new ArrayList<>(ACTIVE); }
}
