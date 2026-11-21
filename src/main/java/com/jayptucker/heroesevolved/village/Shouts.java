package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.effects.SpeechBubble;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A village in a panic, for every village event: words shouted over a runner's head in a red bubble, and villagers
 * running from something the way Odessa's crowd ran from the light ({@code time/intro/VisionCrowd}).
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Shouts {
    /**
     * Bubbles carry this tag. One that loads again later (saved riding its speaker when a chunk unloaded, or across a
     * restart) is not in {@link #LIVE}, so it is turned away.
     */
    public static final String BUBBLE_TAG = HeroesEvolved.MOD_ID + "_meltdown_bubble";
    /** How fast they run: Odessa's crowd ran from the light at the same pace. */
    public static final float RUN_SPEED = 0.95F;
    private static final Set<UUID> LIVE = new HashSet<>();

    private record Bubble(Display.TextDisplay display, Entity speaker, String text, long until) {}
    private static final List<Bubble> BUBBLES = new ArrayList<>();

    private Shouts() {}

    /** Words over {@code speaker}'s head for {@code ticks} ticks, replacing anything they were shouting. */
    public static void say(ServerLevel level, Entity speaker, String text, int ticks) { bubble(level, speaker, text, ticks, true); }

    /** Calm words over {@code speaker}'s head (white, not a shout): a greeting. */
    public static void talk(ServerLevel level, Entity speaker, String text, int ticks) { bubble(level, speaker, text, ticks, false); }

    private static void bubble(ServerLevel level, Entity speaker, String text, int ticks, boolean shout) {
        long now = level.getServer().overworld().getGameTime();
        BUBBLES.removeIf(b -> {
            if (b.speaker != speaker) return false;
            b.display.discard();
            LIVE.remove(b.display.getUUID());
            return true;
        });
        var display = SpeechBubble.show(level, speaker, text, shout, BUBBLE_TAG, d -> LIVE.add(d.getUUID()));
        if (display == null) return;
        BUBBLES.add(new Bubble(display, speaker, text, now + ticks));
    }

    /** The words being shouted right now, and by whom (for tests). */
    public static List<String> shouting(Entity speaker) {
        List<String> out = new ArrayList<>();
        for (Bubble b : BUBBLES)
            if (b.speaker == speaker && !b.display.isRemoved()) out.add(b.text);
        return out;
    }

    /**
     * Sends {@code v} running from {@code from} at a sprint, to a point {@code distance} out the other side, and wakes
     * them if they were asleep. Set through the brain's walk target, which the brain itself would overwrite on the
     * navigation.
     */
    public static void runFrom(Villager v, Vec3 from, double distance) {
        if (v.isSleeping()) v.stopSleeping();
        Vec3 away = v.position().subtract(from).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0E-4) away = new Vec3(v.getRandom().nextDouble() - 0.5, 0, v.getRandom().nextDouble() - 0.5);
        Vec3 to = from.add(away.normalize().scale(distance));
        var brain = v.getBrain();
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(BlockPos.containing(to), RUN_SPEED, 2));
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) { tick(event.getServer()); }

    /** Bubbles over their time, or whose speaker is gone, are taken down. */
    static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        BUBBLES.removeIf(b -> {
            if (now < b.until && b.speaker.isAlive() && !b.speaker.isRemoved() && !b.display.isRemoved()) return false;
            b.display.discard();
            LIVE.remove(b.display.getUUID());
            return true;
        });
    }

    /** A shout left over from before a restart (it was saved riding its speaker): gone. */
    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Display.TextDisplay d && d.getTags().contains(BUBBLE_TAG) && !LIVE.contains(d.getUUID()))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) { clear(); }

    public static void clear() {
        BUBBLES.forEach(b -> b.display.discard());
        BUBBLES.clear();
        LIVE.clear();
    }
}
