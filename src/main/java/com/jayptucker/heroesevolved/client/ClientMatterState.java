package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.MatterStatePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * What this client knows of every matter assembler in view: the Matter they carry, eased toward the server's number so
 * fragments leave and rejoin the orbit one at a time instead of popping (drawn by {@code AssemblyArt.drawLive}).
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class ClientMatterState {
    public static final class Assembler {
        public final int entity;
        public float matter, shown, capacity = 24;
        long heard;
        Assembler(int entity) { this.entity = entity; }
    }

    private static final Map<Integer, Assembler> ASSEMBLERS = new HashMap<>();

    private ClientMatterState() {}

    public static void update(MatterStatePayload payload) {
        var level = Minecraft.getInstance().level;
        if (payload.capacity() <= 0) { ASSEMBLERS.remove(payload.entity()); return; }
        Assembler a = ASSEMBLERS.computeIfAbsent(payload.entity(), id -> { Assembler fresh = new Assembler(id); fresh.shown = payload.matter(); return fresh; });
        a.matter = payload.matter();
        a.capacity = payload.capacity();
        a.heard = level == null ? 0 : level.getGameTime();
    }

    public static Collection<Assembler> assemblers() { return ASSEMBLERS.values(); }

    public static Assembler get(int entity) { return ASSEMBLERS.get(entity); }

    @SubscribeEvent
    public static void clientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (Minecraft.getInstance().isPaused()) return;
        var level = Minecraft.getInstance().level;
        for (Assembler a : ASSEMBLERS.values()) {
            // One fragment at a time: the orbit grows and shrinks visibly rather than jumping.
            float step = Math.signum(a.matter - a.shown) * Math.min(Math.abs(a.matter - a.shown), Math.max(.25F, Math.abs(a.matter - a.shown) * .2F));
            a.shown += step;
        }
        // A packet can arrive before its entity; drop an assembler only once it has been silent and gone for a while.
        if (level != null) ASSEMBLERS.values().removeIf(a -> level.getGameTime() - a.heard > 400 && level.getEntity(a.entity) == null);
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) ASSEMBLERS.clear();
    }
}
