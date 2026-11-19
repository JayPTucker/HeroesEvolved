package com.jayptucker.heroesevolved.story.bunker;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.Arrays;
import java.util.List;

/** Operational records and written books placed in the stasis bunker. */
public final class BunkerLogs {
    private BunkerLogs() {}

    public static ItemStack orientation() {
        return book("Stasis Wing: Orientation", "Dr. M. Suresh",
                "PRIMATECH RESEARCH\nOdessa Site, Sublevel 4\n\nWhen your pod opens, wait for the main lights. Leave the stasis hall by its north door, into the commons.",
                "Records is through the commons' east door. Use the signal receiver on the north desk: it reports the coordinates of the surface relay.",
                "The way up is north of the commons: through the airlock, the vault door and up the stairs. For recovery, the infirmary is through the west door.");
    }

    public static ItemStack lockdown() {
        return book("Lockdown Order 7", "N. Bennet",
                "6:09 PM. Sprague is critical at Kirby Plaza. Petrelli is with him. We are out of time.",
                "Bring in everyone on the Odessa list we can reach. They carry the marker, whether they know it or not. Whoever won't come, dart them and carry them down.",
                "Seal them in the stasis wing. The generator will keep the pods for as long as it has to. They won't understand. They'll hate me for it.",
                "Claire goes in Pod 14. Whatever she says.",
                "Leave the receiver on the emergency frequency. Anyone who answers from the surface will need a clear route to the hatch.");
    }

    /** Left in Pod 14 by the one sleeper who woke early. */
    public static ItemStack claireNote() {
        return book("For Whoever Wakes Next", "Claire",
                "I woke up. The counter on my pod said sixty-one years. I guess I healed through the cold. Of course I did.",
                "Everyone else is still asleep. The panel says opening a pod early could kill you, so I didn't. I'm sorry. I sat with you for a while.",
                "Dad's order is in Records. He thought he was saving us. Maybe he was. I'm sorry for what he did to you. He never came back for me.",
                "I'm going up to find out what's left. If you're reading this, you're awake. Find me.\n\nClaire");
    }

    public static ItemStack helixite() {
        return book("Assay: Helixite", "Pinehearst liaison",
                "The donated sample in this lab is labeled with the donor's consent. Spin it in the centrifuge with redstone dust to isolate the catalyst.",
                "Supply glowstone dust to the synthesis station. Start with water bottles, then add bone meal, a Helixite shard, and the catalyst, one ingredient at a time.",
                "Inject the finished Formula with an empty syringe. A dose may be rejected. A successful dose awakens a dormant or unclaimed power.");
    }

    /** Dr. Weir's journal, on the lectern in his office in the sealed Helix Ward. */
    public static ItemStack weir() {
        return book("Helix Ward: Attending's Log", "Dr. A. Weir",
                "Sublevel 4-R. 6:11 PM.\n\nBennet's men brought one of them in here instead of the hall. A name off the Odessa list, drawn by lot. I gave the dose and sealed the pod. Whatever it wakes in them, it will sleep until something calls it.",
                "6:14 PM.\n\nThe blow came through the rock. The passage behind the blast door is gone. The pod held. My leg did not.",
                "Day four. Nobody comes. The vent on the mezzanine runs through to the stasis hall's catwalk. I can see the ladder from here. I cannot climb it.",
                "Day nine. Vitals stable, twice a day, every day. The subject sleeps. I am sorry we never asked them.",
                "If you came out of that pod: climb to the mezzanine. The vent comes out over the others. They will be waking too.\n\nA. Weir");
    }

    /** Noah Bennet's own notes on 4-R, kept off the record in maintenance under the airlock. */
    public static ItemStack bennetOnTheWard() {
        return book("Not for the Files", "N. Bennet",
                "The Company wants one sleeper kept apart from the rest. Sublevel 4-R, the Helix Ward, behind the blast door off the east corridor. Weir's ward. One pod.",
                "Orders from above: pick one from the list by lot, give them the compound, seal them in the ward. A spare, they call it. Someone with a gift when they wake, whatever is up there.",
                "I have done worse things for this company. I don't have to like this one. Odessa is out of time, and so am I.",
                "If that blast door ever jams, the ward's vent comes out on the east catwalk of the stasis hall. Somebody should know.");
    }

    public static ItemStack book(String title, String author, String... pages) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> content = Arrays.stream(pages).map(page -> Filterable.passThrough((Component) Component.literal(page))).toList();
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), author, 0, content, true));
        return stack;
    }
}
