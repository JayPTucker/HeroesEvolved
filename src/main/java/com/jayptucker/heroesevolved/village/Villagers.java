package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.gifted.Gift;
import com.jayptucker.heroesevolved.village.overload.Overloads;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Set;

/**
 * Where villagers come from, and what may replace one. Minecraft Comes Alive (optional) turns vanilla villagers into its
 * own humans as they spawn; its humans extend {@link Villager}, so {@code instanceof Villager} and
 * {@code getEntitiesOfClass(Villager.class, ...)} are already the right question everywhere. What needs one place is the
 * spawning:
 * <ul>
 *   <li>{@link #actor} makes a scripted body (a story scene's cast), which is always vanilla and which MCA leaves alone.
 *   <li>{@link #enter} puts an ordinary villager in the world, as MCA's human when MCA is there, keeping their UUID, data
 *       and gift. Use what it returns: it may be a different object.
 * </ul>
 * Only {@code compat/mca} touches MCA's classes, through {@link Bridge}. Without MCA both are plain vanilla spawning.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Villagers {
    /** On every scripted body made by {@link #actor}. */
    public static final String ACTOR_TAG = HeroesEvolved.MOD_ID + "_actor";

    /** Casts saved by worlds older than {@link #ACTOR_TAG}: they carry only their own scene's tag. */
    private static final Set<String> SCENE_TAGS = Set.of("heroesevolved_story_actor", "heroesevolved_last_day",
            "heroesevolved_avenue", "heroesevolved_pod_sleeper", "heroesevolved_last_signal_actor",
            "heroesevolved_night_three", "heroesevolved_time_intro_crowd", "heroesevolved_closed_loop");

    private static final String OUR_TAGS = HeroesEvolved.MOD_ID + "_";

    /** What the mod asks of a villager mod. Every method is safe to call: a failure is that mod's link switching off. */
    public interface Bridge {
        /** Keeps this vanilla villager from being taken and replaced when it is added to the level. */
        void holdBack(ServerLevel level, Villager villager);

        /** Adds {@code villager} as the other mod's body, same UUID and data. Null if it couldn't; nothing was added. */
        Villager become(ServerLevel level, Villager villager);

        /** They are leaving their village for good (a turned renegade): their bed and place are free. */
        void leaving(Villager villager);

        /** Every server tick, before the levels. */
        void tick(MinecraftServer server);

        /** A player used a villager at a point on their body, which the other mod may answer before the plain use. */
        void usedAt(PlayerInteractEvent.EntityInteractSpecific event);
    }

    private static Bridge bridge;
    private static boolean looked;

    private Villagers() {}

    private static Bridge bridge() {
        if (!looked) {
            looked = true;
            if (ModList.get().isLoaded("mca")) {
                try {
                    bridge = new com.jayptucker.heroesevolved.compat.mca.McaLink();
                } catch (Throwable broken) {
                    HeroesEvolved.LOGGER.error("Minecraft Comes Alive is here but its link failed; villagers stay vanilla", broken);
                }
            }
        }
        return bridge;
    }

    /** A scripted body for a scene: always a vanilla villager, never replaced. Null if one can't be made. */
    public static Villager actor(ServerLevel level) {
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) return null;
        villager.addTag(ACTOR_TAG);
        Bridge link = bridge();
        if (link != null) link.holdBack(level, villager);
        return villager;
    }

    /**
     * Puts an ordinary villager in the world: as they are, or as MCA's human with the same UUID, data and gift. Returns
     * whoever is now in the level (use it, not the argument), or null if nobody could be added.
     */
    public static Villager enter(ServerLevel level, Villager villager) {
        Bridge link = bridge();
        if (link != null && !isActor(villager)) {
            Villager human = link.become(level, villager);
            if (human != null) return human;
            link.holdBack(level, villager);     // the link failed: they still arrive, as themselves
        }
        return level.addFreshEntity(villager) ? villager : null;
    }

    /** They are leaving their village for good, as a renegade does when they turn. */
    public static void leaving(Villager villager) {
        Bridge link = bridge();
        if (link != null) link.leaving(villager);
    }

    /**
     * Sealed in a stasis pod. The one question every village event asks before it counts, picks, moves, wakes or names
     * anyone: a body in a pod is not part of any village, even with a village right over the bunker.
     */
    public static boolean inStasis(net.minecraft.world.entity.Entity entity) {
        return entity.getTags().contains(com.jayptucker.heroesevolved.story.bunker.PodSleepers.TAG);
    }

    /** A body a scene is using: replacing it would take it out of the scene. */
    public static boolean isActor(Villager villager) {
        if (villager.isNoAi()) return true;     // standing where a scene put them (the facility's captives)
        for (String tag : villager.getTags()) if (tag.equals(ACTOR_TAG) || SCENE_TAGS.contains(tag)) return true;
        return false;
    }

    /**
     * Must this villager stay the entity they are right now? Scenes and running events hold them by reference, so they
     * are not replaced mid-way: an actor, anyone carried or riding, anyone with one of our live tags (a traveller, a
     * prisoner, a courier), and anyone in a meltdown or a storm.
     */
    public static boolean staysAsIs(Villager villager) {
        if (isActor(villager) || villager.isPassenger() || EntityCarryService.isBeingCarried(villager)) return true;
        for (String tag : villager.getTags()) if (tag.startsWith(OUR_TAGS) && !tag.equals(Gift.TAG)) return true;
        var data = villager.getPersistentData();
        return data.contains(VillagerMeltdown.TAG) || data.contains(Overloads.TAG);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Pre event) {
        Bridge link = bridge();
        if (link != null) link.tick(event.getServer());
    }

    @SubscribeEvent
    public static void usedAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getLevel().isClientSide() || !(event.getTarget() instanceof Villager)) return;
        Bridge link = bridge();
        if (link != null) link.usedAt(event);
    }
}
