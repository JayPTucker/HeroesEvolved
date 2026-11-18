package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.amplify.AmpService;
import com.jayptucker.heroesevolved.gui.HudGauges;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What this client knows about Power Amplification: which bodies are supercharged (and how strongly, until when), each
 * amplifier's Charge and what their hands are doing, and the minds an overcharged telepath hears. The art reads it every
 * frame ({@code AmpArt}); the HUD shows the local player's Charge as a ring.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class ClientAmp {
    public record Charged(int level, long start, long until) {}
    public record State(int charge, int flags, long chargingSince, long gatheringSince) {}

    private static final Map<Integer, Charged> CHARGED = new HashMap<>();
    private static final Map<Integer, State> STATES = new HashMap<>();
    private static final Map<Integer, Long> SIGHT = new HashMap<>();
    public static final int OUTLINE = 0xFF3048;

    private ClientAmp() {}

    private static long now() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime();
    }

    public static void supercharged(int entity, int level, int ticks) {
        if (level <= 0) { CHARGED.remove(entity); return; }
        Charged old = CHARGED.get(entity);
        long now = now();
        long start = old != null && old.level == level ? old.start : now;
        CHARGED.put(entity, new Charged(level, start, now + ticks));
    }

    public static void state(int entity, int charge, int flags) {
        if (charge < 0) { STATES.remove(entity); return; } // no longer an amplifier
        State old = STATES.get(entity);
        long now = now();
        long charging = (flags & AmpService.CHARGING) == 0 ? -1 : old != null && old.chargingSince >= 0 ? old.chargingSince : now;
        long gathering = (flags & AmpService.GATHERING) == 0 ? -1 : old != null && old.gatheringSince >= 0 ? old.gatheringSince : now;
        STATES.put(entity, new State(charge, flags, charging, gathering));
    }

    public static void sight(List<Integer> ids, int ticks) {
        long until = now() + ticks;
        for (int id : ids) SIGHT.put(id, until);
    }

    /** 0 not supercharged, 1 a Surge, 2 an Overcharge. */
    public static int level(int entity) {
        Charged c = CHARGED.get(entity);
        if (c == null) return 0;
        if (now() > c.until + 2) { CHARGED.remove(entity); return 0; }
        return c.level;
    }

    public static Map<Integer, Charged> charged() { return CHARGED; }

    public static Map<Integer, State> states() { return STATES; }

    public static State state(int entity) { return STATES.get(entity); }

    /** Whether an overcharged telepath (this player) hears this mind right now: it is outlined through walls. */
    public static boolean sees(Entity entity) {
        if (SIGHT.isEmpty()) return false;
        Long until = SIGHT.get(entity.getId());
        if (until == null) return false;
        if (now() > until) { SIGHT.remove(entity.getId()); return false; }
        return true;
    }

    /** The local player's Charge, as a red ring; a white notch marks what an Overcharge spends. */
    public static void gauges(List<HudGauges.Gauge> gauges) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean holds = com.jayptucker.heroesevolved.network.ClientPowerState.getSnapshot().abilities().stream()
                .anyMatch(a -> a.unlocked() && a.abilityId().equals(ModAbilities.AMPLIFICATION_ID));
        if (!holds) return;
        State s = STATES.get(mc.player.getId());
        int charge = s == null ? AmpService.REST : s.charge;
        boolean sparking = s != null && (s.flags & AmpService.SPARKING) != 0;
        int color = sparking ? (HudGauges.flash() ? 0xFFFFE6DE : 0xFFFF3048) : charge >= AmpService.OVERCHARGE_COST ? 0xFFFF3048 : 0xFFB0202E;
        gauges.add(HudGauges.Gauge.ring(charge / 100F, color).middle(Integer.toString(charge))
                .label(sparking ? "SPARKING" : "CHARGE", sparking ? color : HudGauges.LABEL).notches(AmpService.OVERCHARGE_COST / 100F));
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { forget(); }

    /**
     * Entity ids belong to one world: leaving it (another dimension) forgets everything, and the server resends what the
     * new one holds. The old level unloads while the client handles the change, before any packet for the new one.
     */
    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) forget();
    }

    private static void forget() {
        CHARGED.clear();
        STATES.clear();
        SIGHT.clear();
        SuperchargeField.forget();
    }
}
