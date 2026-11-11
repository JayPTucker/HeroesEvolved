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
                "Odessa has lost power. Seal the stasis wing and keep the pods on the generator.",
                "Leave the receiver on the emergency frequency. Anyone who answers from the surface will need a clear route to the hatch.");
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
