package com.jayptucker.heroesevolved.story.bunker;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.radio.EmergencyBand;
import com.jayptucker.heroesevolved.structure.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The walkie-talkies in the bunker, tuned to the emergency band (101.10 FM) so sleepers can hear the villages: one for
 * every two players who have woken there (or been told where it is), one in singleplayer. They go in the supply chest in
 * Records (the {@code loot:records} marker), added as more players come; a chest that is gone or full leaves the radio
 * on the floor where it stood. What the player sees: {@code docs/living-radio.md}.
 */
public final class BunkerRadios {
    private BunkerRadios() {}

    /** How many radios the bunker owes {@code players} players: one for every two, rounded up. */
    public static int owed(int players) { return players <= 0 ? 0 : (players + 1) / 2; }

    /** Everyone who has slept in a pod here or been told of it. */
    public static int players(BunkerData data) {
        Set<UUID> all = new HashSet<>(data.counted);
        all.addAll(data.pods.keySet());
        return all.size();
    }

    /** {@code player} has woken in (or been told of) the bunker: count them, and stock any radio now owed. */
    public static void joined(ServerLevel level, BunkerData data, UUID player) {
        if (data.counted.add(player)) data.setDirty();
        stock(level, data);
    }

    /** Puts the radios still owed in the supply chest, if it is loaded. Returns how many it added. */
    public static int stock(ServerLevel level, BunkerData data) {
        int owe = owed(players(data)) - data.radios;
        if (owe <= 0 || !data.built) return 0;
        BlockPos chest = chest(data);
        if (chest == null || !level.isLoaded(chest)) return 0;
        int given = 0;
        for (int i = 0; i < owe; i++) {
            ItemStack radio = EmergencyBand.radio();
            if (radio.isEmpty()) break;   // no radio mod: no radios
            put(level, chest, radio);
            given++;
        }
        if (given > 0) {
            data.radios += given;
            data.setDirty();
            HeroesEvolved.LOGGER.info("Bunker supply chest: {} radio(s) for {} player(s)", data.radios, players(data));
        }
        return given;
    }

    /** Where the Records supply chest is, or null if this bunker's rooms have none. */
    public static BlockPos chest(BunkerData data) {
        for (BunkerBuilder.Room room : data.layout().rooms()) {
            if (!Blueprint.exists(room.blueprint())) continue;
            List<BlockPos> marked = Blueprint.load(room.blueprint()).markerOffsets("loot:records");
            if (!marked.isEmpty()) return data.origin.offset(room.at()).offset(marked.getFirst());
        }
        return null;
    }

    /** Into the chest's first free slot (the odd ones first: the supplies sit in the even ones), or onto the floor. */
    public static void put(ServerLevel level, BlockPos at, ItemStack radio) {
        if (level.getBlockEntity(at) instanceof Container box) {
            for (int pass = 0; pass < 2; pass++)
                for (int slot = pass == 0 ? 1 : 0; slot < box.getContainerSize(); slot += pass == 0 ? 2 : 1)
                    if (box.getItem(slot).isEmpty()) {
                        box.setItem(slot, radio);
                        box.setChanged();
                        return;
                    }
        }
        ItemEntity item = new ItemEntity(level, at.getX() + .5, at.getY() + .3, at.getZ() + .5, radio);
        item.setDeltaMovement(0, 0, 0);
        item.setUnlimitedLifetime();
        level.addFreshEntity(item);
    }
}
