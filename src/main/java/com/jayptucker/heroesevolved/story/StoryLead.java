package com.jayptucker.heroesevolved.story;

import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.expansion.FacilityData;
import com.jayptucker.heroesevolved.progression.StoryDiscoveries;
import com.jayptucker.heroesevolved.progression.StoryDiscoveries.Place;
import com.jayptucker.heroesevolved.story.bunker.BunkerData;
import net.minecraft.server.level.ServerPlayer;

/**
 * The journal's "Next lead": the one thing this player should look for now, so the story can be
 * followed without a guide. Leads only point; nothing is locked behind them. The order is the
 * story's: wake, learn what happened, find who saw it coming, awaken, then walk back to the Last Day:
 * the Annex at 5:50, then the avenue at 6:14, then everyone left behind.
 */
public final class StoryLead {
    private StoryLead() {}

    public static String next(ServerPlayer player) {
        BunkerData bunker = BunkerData.get(player.getServer());
        boolean hasBunker = HeroesEvolvedConfig.COMMON.bunkerEnabled.get() && bunker.built;
        boolean awakened = PlayerAbilityService.getData(player).assignedPower()
                .map(entry -> entry.getValue().isUnlocked()).orElse(false);
        FacilityData annex = FacilityData.get(player.getServer());

        if (hasBunker && !found(player, "Stasis Wing: Orientation")) {
            return "A sealed stasis bunker lies under X " + bunker.origin.getX() + ", Z " + bunker.origin.getZ()
                    + ". Read the orientation log on the lectern in the middle of its stasis hall.";
        }
        if (hasBunker && !found(player, "Lockdown Order 7")) {
            return "Find out what happened. The records room lies past the corridor: read Noah Bennet's lockdown order.";
        }
        if (!found(player, "I Painted It") && !found(player, "Dad")) {
            return "Someone saw it coming. Search the ruins on the surface: a brick loft with a collapsed corner was "
                    + "Isaac Mendez's studio, and part of the Bennets' house still stands.";
        }
        if (!awakened) {
            return "Your power hasn't woken. It wakes under stress, under an eclipse, or close to death. "
                    + "The Formula, synthesized in the bunker lab, is another way.";
        }
        if (hasBunker && !found(player, "Containment: G. Gray")) {
            return "If you hear ticking, you are not alone. Read the containment file in the cell west of the bunker's corridor.";
        }
        if (hasBunker && !found(player, "A Note, Folded Small")) {
            return "Someone left a note folded small in the records room chest. It isn't in a researcher's handwriting.";
        }
        if (hasBunker && !found(player, "For Claire")) {
            return found(player, "Shift Notes")
                    ? "The guard at the checkpoint never moved the ugly red rug. You can."
                    : "Noah Bennet hid something in the bunker. A guard's notes in the security chest, west of the records room, know where.";
        }
        if (!annex.discovered(player.getUUID(), "unfinished_hour")) {
            return "Hiro left a key \"in the annex beneath the hourglass\". Village cartographers have been painting it "
                    + "without knowing why: talk to one, or ask a precognitive to share their canvas.";
        }
        if (!annex.discovered(player.getUUID(), "annex_saved")) {
            return "Follow the omen into the Hourglass Annex: Odessa, 5:50 PM on the Last Day. Bring Mara and Ivo out alive.";
        }
        AvenueData.Best avenue = AvenueData.get(player.getServer()).best(player.getUUID());
        if (avenue.saved() == 0) {
            return "Hiro's key opens the avenue. Follow the omen to Odessa at 6:10 PM: Noah Bennet holds the Company's door "
                    + "until 6:14. Bring him everyone you can.";
        }
        if (!avenue.grayKept()) {
            return "Gabriel Gray reached the light on the avenue and understood it. Walk the Last Day again, and keep him "
                    + "from it until 6:14.";
        }
        long bunkerLeft = missing(player, Place.BUNKER), ruinsLeft = missing(player, Place.RUINS);
        if (hasBunker && bunkerLeft > 0) {
            return bunkerLeft + (bunkerLeft == 1 ? " record is" : " records are") + " still somewhere in the bunker.";
        }
        if (ruinsLeft > 0) {
            return ruinsLeft + (ruinsLeft == 1 ? " note is" : " notes are")
                    + " still out in the ruins. Survivors left them in burnt and collapsed houses.";
        }
        int lost = AvenueService.CITIZENS - avenue.saved();
        if (lost > 0) {
            return lost + (lost == 1 ? " person" : " people") + " on the avenue never reached Bennet's door. "
                    + "The Last Day is still there to walk.";
        }
        return "Everyone on the avenue lived to see 6:15, and the watchmaker never saw the light. "
                + "You have followed every lead the world holds.";
    }

    private static boolean found(ServerPlayer player, String title) {
        return StoryDiscoveries.has(player, title);
    }

    private static long missing(ServerPlayer player, Place place) {
        return StoryDiscoveries.ORDER.stream()
                .filter(note -> note.place() == place && !found(player, note.title()))
                .count();
    }
}
