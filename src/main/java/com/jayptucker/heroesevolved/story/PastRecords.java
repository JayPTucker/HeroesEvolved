package com.jayptucker.heroesevolved.story;

import com.jayptucker.heroesevolved.progression.StoryDiscoveries;
import com.jayptucker.heroesevolved.story.bunker.BunkerLogs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the past writes into the present: nobody finds these in a chest. They are in your hands when
 * you come back from changing something. Original writing; the people named are adapted from Heroes.
 */
public final class PastRecords {
    public static final String KEY = "The Key", DOOR = "The Door at 6:14", ADDENDUM = "Addendum: G. Gray", WHY_YOU = "Why You";
    private static final int NAMES_PER_PAGE = 10;

    private PastRecords() {}

    /** Hiro's key, left in the Hourglass Annex for whoever brings Mara and Ivo out: the way to the avenue. */
    public static ItemStack key() {
        return BunkerLogs.book(KEY, "H. N.",
                "You brought them out of the annex. Good. Now you know it can be done.\n\nThis is the key I promised. It isn't a thing. It's a rule.",
                "The light at 6:14 is fixed. I tried to stop it twice, and twice it fell anyway.\n\nNobody under it is fixed. Not one of them.",
                "At 6:10 Noah Bennet opens the Company's street door at the south end of the avenue. He holds it until 6:14. Not a second longer.\n\nBring him everyone you can.",
                "Watch for a man in a dark coat walking toward the light. He wants to see how it works.\n\nDon't let him.\n\nYour Journal knows the way. Follow the omen.\n\n- H.");
    }

    /** Lockdown Order 7, as Bennet wrote it after someone held his door with him. Lists who came through it. */
    public static ItemStack door(List<String> saved) {
        int count = saved.size();
        List<String> pages = new ArrayList<>();
        pages.add("PRIORITY ONE, REVISED\n\nSprague lost control downtown at 6:14 this evening.\n\nThe city is gone. Its people aren't. Not all of them.");
        pages.add(count == AvenueService.CITIZENS
                ? "I held the street door until the last second. A stranger kept bringing people to it. Led some. Carried a few.\n\nEvery person on that avenue came down the stairs alive. All twenty-four. I counted twice."
                : count == 1
                ? "I held the street door until the last second. A stranger brought one person down the stairs, alive.\n\nOne. I keep writing the number to be sure it's real."
                : "I held the street door until the last second. A stranger kept bringing people to it. Led some. Carried a few.\n\n"
                        + count + " came down the stairs alive. I counted every one.");
        for (int from = 0; from < count; from += NAMES_PER_PAGE) {
            StringBuilder page = new StringBuilder(from == 0 ? "Their names:\n\n" : "");
            for (String name : saved.subList(from, Math.min(count, from + NAMES_PER_PAGE))) page.append(name).append('\n');
            pages.add(page.toString().stripTrailing());
        }
        pages.add("I never got the stranger's name. When I looked back up the stairs, they were gone.\n\nWhoever you are: thank you.\n\n- Noah");
        return BunkerLogs.book(DOOR, "N. Bennet", pages.toArray(String[]::new));
    }

    /** Gray's containment file, as it reads once he never saw the light. */
    public static ItemStack addendum() {
        return BunkerLogs.book(ADDENDUM, "Security",
                "ADDENDUM, NIGHT ONE\n\nSubject Gray was brought below at 6:40 PM. Found a mile south of the avenue, walking away from the city.",
                "Asked what he saw, he said: Nothing. Someone kept getting in my way.\n\nHe was angry about it. He still is.",
                "Whatever the man in the light was, Gray never saw how it worked. He asks about it every day.\n\nNobody here is going to tell him.");
    }

    /** Hiro's last note: the answer to the question the intro asks. */
    public static ItemStack whyYou() {
        return BunkerLogs.book(WHY_YOU, "H. N.",
                "You must have wondered why you. Why your pod opened, out of fourteen. Why I wrote to you and to nobody else.",
                "I didn't choose you. Isaac did. Before the Last Day he painted a sleeper waking in a pod. It hangs in the west gallery: Exhibit 9. Look at the face.",
                "He painted the avenue, too. People running for a door, and someone in the street bringing them in.\n\nThe same face.",
                "The light still falls at 6:14. It always will.\n\nBut someone on that street lived to see 6:15, because you were there.\n\nThe future was unwritten once. You wrote it.\n\n- H.");
    }

    /** Puts a record in the player's hands (or at their feet) and counts it straight away. */
    public static void give(ServerPlayer player, ItemStack book) {
        StoryDiscoveries.found(player, book);
        if (!player.getInventory().add(book)) player.drop(book, false);
    }

    public static boolean has(ServerPlayer player, String title) {
        return StoryDiscoveries.has(player, title);
    }
}
