package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.gifted.GiftedMob;
import com.jayptucker.heroesevolved.network.GiftedMobPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * The client's picture of every gifted mob it can see (fed by {@link GiftedMobPayload}): its power's colour, rarity,
 * state flags and the move it is winding up, with the game time each started, so the art and the illager casting pose
 * can time themselves without a packet per frame.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class ClientGifted {
    /** One gifted mob's state, and when its cast and its unbinding began (client game time). */
    public record State(int power, int rarity, int flags, int pose, int windup, long castAt, long unboundAt, long seenAt) {
        public boolean evil() { return (flags & GiftedMob.EVIL) != 0; }
        public boolean casting() { return (flags & GiftedMob.CASTING) != 0; }
        public boolean unbound() { return (flags & GiftedMob.UNBOUND) != 0; }
        public boolean spent() { return (flags & GiftedMob.SPENT) != 0; }
        public boolean boss() { return (flags & GiftedMob.BOSS) != 0; }
        public boolean staggered() { return (flags & GiftedMob.STAGGERED) != 0; }
        /** How far through its windup it is, 0 to 1. */
        public float castProgress(long now, float partial) {
            return windup <= 0 ? 0 : Math.clamp((now - castAt + partial) / windup, 0, 1);
        }
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static ClientLevel world;

    private ClientGifted() {}

    public static void accept(GiftedMobPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (p.flags() < 0) { STATES.remove(p.entity()); return; }
        long now = level.getGameTime();
        State old = STATES.get(p.entity());
        boolean casting = (p.flags() & GiftedMob.CASTING) != 0, unbound = (p.flags() & GiftedMob.UNBOUND) != 0;
        long castAt = casting ? (old != null && old.casting() && old.pose() == p.pose() ? old.castAt : now) : -1;
        long unboundAt = unbound ? (old != null && old.unbound() ? old.unboundAt : now) : -1;
        STATES.put(p.entity(), new State(p.power(), p.rarity(), p.flags(), p.pose(), p.windup(), castAt, unboundAt, old != null ? old.seenAt : now));
    }

    public static State get(Entity entity) { return entity == null ? null : STATES.get(entity.getId()); }

    public static Map<Integer, State> all() { return STATES; }

    public static boolean any() { return !STATES.isEmpty(); }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != world) { STATES.clear(); world = level; }
        if (level == null || level.getGameTime() % 40 != 0) return;
        STATES.keySet().removeIf(id -> level.getEntity(id) == null);
    }
}
