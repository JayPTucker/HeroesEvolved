package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.hydro.HydroService;
import com.jayptucker.heroesevolved.network.HydroStatePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * What this client knows of every hydrokinetic in view: the water they carry (drawn circling their waist, eased
 * toward the server's number so it never jumps) and their state flags, each stamped with the game time it changed so
 * an animation can tell how far into its phase it is.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class ClientHydroState {
    public static final class Bender {
        public final int entity;
        public float water, shown, capacity = 100;
        public int flags;
        long heard;
        /** When each flag last changed (indexed by bit position). */
        final long[] changed = new long[8];
        Bender(int entity) { this.entity = entity; }

        public boolean has(int flag) { return (flags & flag) != 0; }

        /** Ticks since {@code flag} last changed, with the partial tick. */
        public float age(int flag, long now, float partial) {
            return now - changed[Integer.numberOfTrailingZeros(flag)] + partial;
        }

        /** The water drawn, as a fraction of a full reservoir (100). */
        public float fullness() { return Math.clamp(shown / 100F, 0, 1.5F); }
    }

    private static final Map<Integer, Bender> BENDERS = new HashMap<>();

    private ClientHydroState() {}

    public static void update(HydroStatePayload payload) {
        var level = Minecraft.getInstance().level;
        long now = level == null ? 0 : level.getGameTime();
        Bender bender = BENDERS.computeIfAbsent(payload.entity(), Bender::new);
        int changed = bender.flags ^ payload.flags();
        for (int bit = 0; bit < 8; bit++) if ((changed & 1 << bit) != 0) bender.changed[bit] = now;
        bender.flags = payload.flags();
        bender.heard = now;
        bender.water = payload.water();
        bender.capacity = Math.max(1, payload.capacity());
    }

    public static Bender get(int entity) { return BENDERS.get(entity); }

    public static Collection<Bender> benders() { return BENDERS.values(); }

    public static boolean any() { return !BENDERS.isEmpty(); }

    public static boolean octopus(int entity) {
        Bender bender = BENDERS.get(entity);
        return bender != null && bender.has(HydroService.OCTOPUS);
    }

    public static boolean riding(int entity) {
        Bender bender = BENDERS.get(entity);
        return bender != null && bender.has(HydroService.RIDING);
    }

    /** Eases the drawn water toward the server's, once a tick. */
    public static void tick() {
        for (Bender bender : BENDERS.values()) bender.shown += (bender.water - bender.shown) * .2F;
    }

    @SubscribeEvent
    public static void clientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (Minecraft.getInstance().isPaused()) return;
        tick();
        var level = Minecraft.getInstance().level;
        // A packet can arrive before its entity does, so a bender is only dropped once long gone.
        if (level != null) BENDERS.values().removeIf(b -> level.getEntity(b.entity) == null && level.getGameTime() - b.heard > 200);
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) BENDERS.clear();
    }
}
