package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.expansion.AdvancedTechniques;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/** One rate for vanilla AI, gifted casts, and the client picture of a creature. */
public final class TemporalMobTime {
    private static final java.util.Map<net.minecraft.world.entity.ai.Brain<?>, java.lang.ref.WeakReference<Mob>> BRAINS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    public static void track(Mob mob) { BRAINS.put(mob.getBrain(), new java.lang.ref.WeakReference<>(mob)); }
    public static Mob owner(net.minecraft.world.entity.ai.Brain<?> brain) {
        var ref = BRAINS.get(brain); return ref == null ? null : ref.get();
    }
    private static final ThreadLocal<Mob> ACTOR = new ThreadLocal<>();
    public static int actor() { Mob mob = ACTOR.get(); return mob == null ? -1 : mob.getId(); }
    public static void acting(Mob mob, Runnable action) {
        Mob old = ACTOR.get(); ACTOR.set(mob);
        try { action.run(); } finally { if (old == null) ACTOR.remove(); else ACTOR.set(old); }
    }
    private TemporalMobTime() {}

    public static boolean perceives(Entity entity) {
        if (!(entity instanceof Mob mob)) return false;
        var power = GiftedMobs.powerOf(mob);
        return ModAbilities.SUPERSPEED_ID.equals(power) || ModAbilities.TIME_MANIPULATION_ID.equals(power);
    }

    /** Zero means stopped, one normal, otherwise one tick per interval. */
    public static int interval(Entity mob) {
        if (perceives(mob)) return 1;
        if (TimeStopService.isStopped(mob)) return 0;
        int interval = Math.max(TimeSlowService.mobInterval(mob), AdvancedTechniques.perceptionInterval(mob));
        // A creeper caught mid-swell burns down slowly enough to get away from (or answer).
        return mob instanceof net.minecraft.world.entity.monster.Creeper creeper && creeper.getSwellDir() > 0
                ? TimeFlow.fuse(interval) : interval;
    }

    public static boolean advances(Entity mob) {
        int interval = interval(mob);
        return interval > 0 && Math.floorMod(mob.getId(), interval) == Math.floorMod(mob.level().getGameTime(), interval);
    }

    public static LivingEntity target(Mob mob, LivingEntity target) {
        if (perceives(mob) || TemporalSharing.shared(mob)) return target;
        return TimeVeil.perceivedTarget(mob, target);
    }

    public static void brain(Mob mob) {
        var brain = mob.getBrain();
        if (!brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) return;
        LivingEntity actual = brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        LivingEntity perceived = target(mob, actual);
        if (actual != perceived) brain.setMemory(MemoryModuleType.ATTACK_TARGET, perceived);
    }
}
