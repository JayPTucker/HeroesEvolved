package com.jayptucker.heroesevolved.gifted;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.radio.EmergencyBand;
import com.jayptucker.heroesevolved.village.VillageTickets;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Raids with champions: a wave's leader (the one carrying the banner) may carry a power
 * ({@link HeroesEvolvedConfig.Common#giftedRaidChampionChance}, likelier in later waves), and the last wave's leader
 * always does, at Epic or better. A raided village also calls for help on the emergency band for as long as the raid
 * lasts, so other players hundreds of blocks off can come.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class GiftedRaids {
    private record Watched(Raid raid, ServerLevel level, EmergencyBand.Call call) {}
    /** Raids calling for help, by level and raid id. */
    private static final Map<String, Watched> RAIDS = new HashMap<>();
    /** Waves that already have their champion. */
    private static final Set<String> CHAMPIONED = new HashSet<>();

    private GiftedRaids() {}

    private static String key(ServerLevel level, Raid raid) { return level.dimension().location() + "#" + raid.getId(); }

    /** Called as a raider joins its raid: the wave leader may become its champion. True if it did. */
    static boolean champion(Raider raider) {
        Raid raid = raider.getCurrentRaid();
        if (raid == null || !(raider.level() instanceof ServerLevel level)) return false;
        watch(level, raid);
        if (!raider.isPatrolLeader()) return false;
        int wave = raider.getWave(), waves = raid.getNumGroups(level.getDifficulty());
        boolean last = wave >= waves;
        String waveKey = key(level, raid) + "/" + wave;
        if (CHAMPIONED.contains(waveKey)) return false;
        double chance = HeroesEvolvedConfig.COMMON.giftedRaidChampionChance.get() * (1 + .25 * (wave - 1));
        if (!last && raider.getRandom().nextDouble() >= chance) return false;
        CHAMPIONED.add(waveKey);
        Rarity floor = last ? Rarity.EPIC : wave * 2 > waves ? Rarity.RARE : Rarity.UNCOMMON;
        GiftedMobs.bestow(raider, GiftedMobs.randomPower(raider.getRandom()), Rarity.roll(raider.getRandom(), floor), true,
                Gift.Origin.RAID, raid.getCenter(), null);
        return true;
    }

    /** A raid has begun: its village calls for help, and stays loaded while the raid lasts. */
    private static void watch(ServerLevel level, Raid raid) {
        String key = key(level, raid);
        if (RAIDS.containsKey(key)) return;
        EmergencyBand.Call call = EmergencyBand.call(level, raid.getCenter(), EmergencyBand.Trouble.RAID, words(level, raid), 20 * 60 * 30);
        VillageTickets.hold(level, raid.getCenter(), "raid:" + key);
        RAIDS.put(key, new Watched(raid, level, call));
    }

    private static Component words(ServerLevel level, Raid raid) {
        int waves = raid.getNumGroups(level.getDifficulty());
        return Component.literal("Raiders at the village! Wave " + Math.max(1, raid.getGroupsSpawned()) + " of " + waves
                + ", " + raid.getTotalRaidersAlive() + " raiders. Come quick!");
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % 20 != 0 || RAIDS.isEmpty()) return;
        for (Map.Entry<String, Watched> e : Map.copyOf(RAIDS).entrySet()) {
            Watched w = e.getValue();
            if (w.level.getServer() != server) continue;
            if (w.raid.isOver() || w.raid.isStopped() || !EmergencyBand.live(w.call)) {
                end(e.getKey(), w);
                continue;
            }
            w.call.say(words(w.level, w.raid));
        }
    }

    private static void end(String key, Watched w) {
        RAIDS.remove(key);
        EmergencyBand.end(w.call);
        VillageTickets.release("raid:" + key);
        CHAMPIONED.removeIf(k -> k.startsWith(key + "/"));
    }

    /** Raids calling now (tests, admin). */
    public static int calling() { return RAIDS.size(); }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) {
        for (Map.Entry<String, Watched> e : Map.copyOf(RAIDS).entrySet()) end(e.getKey(), e.getValue());
        CHAMPIONED.clear();
    }
}
