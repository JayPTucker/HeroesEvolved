package com.jayptucker.heroesevolved.village.overload;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** What one kind of storm does to the world: {@link Wildfire}, {@link Supercell}, {@link Blizzard}. */
interface Weather {
    /** It starts, or resumes after loading. */
    void begin(Overload o);

    /** One tick of it. */
    void tick(Overload o, long now);

    /** The break lands, once, when its warning runs out. */
    void breaks(Overload o);

    /** It is over, however it ended: put everything it holds back. */
    void end(Overload o);

    /** How far it reaches now: villagers inside are in danger, and run. */
    double reach(Overload o);

    /** Whether something outside holds it back this tick (a counter-wind). */
    default boolean held(Overload o) { return false; }

    /** Where the drawing aims, besides the eye (the funnel's next stop, Touchdown's far end). */
    default Vec3 target(Overload o) { return o.eye(); }

    /** Whether this player sees the eye through it (a whiteout hides them from everyone else). */
    default boolean sees(Overload o, ServerPlayer p) { return true; }

    /** Its village's people in its reach: by default they run from the eye. */
    default void villagers(Overload o, List<Villager> inDanger) {
        for (Villager v : inDanger) com.jayptucker.heroesevolved.village.Shouts.runFrom(v, o.eye(), reach(o) + 10);
    }

    default void save(Overload o, CompoundTag tag) {}

    default void load(Overload o, CompoundTag tag) {}
}
