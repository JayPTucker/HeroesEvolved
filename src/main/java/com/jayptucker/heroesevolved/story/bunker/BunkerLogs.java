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
                "PRIMATECH RESEARCH\nOdessa Site, Sublevel 4\n\nWhen your pod opens, wait for the main lights. Walk through the corridor to Records. Use the signal receiver on the east desk.",
                "The receiver reports the coordinates of the surface relay. Take the ladder in Records up to the hatch, then follow them across the surface.",
                "For recovery, the infirmary is on the west side of the corridor. The Formula lab is opposite.");
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

    public static ItemStack book(String title, String author, String... pages) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> content = Arrays.stream(pages).map(page -> Filterable.passThrough((Component) Component.literal(page))).toList();
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), author, 0, content, true));
        return stack;
    }
}
