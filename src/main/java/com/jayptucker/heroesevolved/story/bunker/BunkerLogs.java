package com.jayptucker.heroesevolved.story.bunker;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.Arrays;
import java.util.List;

/**
 * The recovered records of Primatech's Odessa stasis wing: what happened to the world, and why
 * the sleepers wake alone. Original writing; the people named are adapted from Heroes.
 * Read in order they tell the story, but each stands alone.
 */
public final class BunkerLogs {
    private BunkerLogs() {}

    public static ItemStack orientation() {
        return book("Stasis Wing: Orientation", "Dr. M. Suresh",
                "PRIMATECH RESEARCH\nOdessa Site, Sublevel 4\nSTASIS WING\n\nIf you are reading this, your pod has opened. Do not be afraid. You are safe here.",
                "Every sleeper in this wing carries an ability: dormant, unstable, or both. Stasis keeps it from waking before we understand it.\n\nYou volunteered. Some of you were found. All of you were protected.",
                "Your memory will return slowly. Your power may not wait.\n\nIt wakes under stress, under an eclipse, or when the body is close to death. When it does, you will know.",
                "Records are kept in the room past the corridor. The lab is to the east. Do not open the cell to the west.\n\nThe ladder leads to the surface.\n\nThe surface should be safe by now.");
    }

    public static ItemStack lockdown() {
        return book("Lockdown Order 7", "N. Bennet",
                "PRIORITY ONE\n\nThe man in the painting was real. Sprague lost control downtown at 6:14 this evening.\n\nThe city is gone.",
                "The flash was seen from here. The tremor reached Sublevel 4 eleven seconds later.\n\nI am sealing the site. Nobody goes up. Nobody comes down.",
                "Somebody saw this coming. A painter in New York. A boy with a sword who said he came from the future.\n\nWe filed both reports.\n\nWe did nothing.",
                "Keep the sleepers under. Whatever happens on the surface, they are the only ones who can fix it.\n\nI'm sorry, Claire.\n\n- Noah");
    }

    public static ItemStack quietYears() {
        return book("Day 2,190", "Dr. M. Suresh",
                "Six years.\n\nThe last of the staff left through the shaft this spring. I stayed with the pods.\n\nThe ash has stopped. Something green grows over the vent caps.",
                "Twice a year the sun goes black at noon. It is not a normal eclipse. Under it, powers fail; after it, sleepers stir.\n\nI think it is the same event, still echoing.",
                "The power cells will not last. When they fail, the pods will open one at a time, whenever someone arrives to be woken.\n\nThe world above will not remember any of us.",
                "To whoever wakes:\n\nThe Formula notes are in the lab. The crystal we mined, Helixite, still grows below us.\n\nYou are not the last. You are the first.");
    }

    public static ItemStack helixite() {
        return book("Assay: Helixite", "Pinehearst liaison",
                "The crystal seam below Sublevel 6 is not natural. It glows. It hums at the frequency our sleepers dream at.\n\nWe are calling it Helixite.",
                "Helixite stabilizes the Formula's compound stage. Without it the synthetic ability collapses in hours.\n\nIt forms deep, below Y 0, mostly in deepslate. It is rare. Mine carefully.",
                "The catalyst must come from regenerating blood. Only one sleeper carries it.\n\nTake it with consent. We have seen what happens when we do not.",
                "Centrifuge: two shards, iron, a comparator.\nSynthesis bench: one shard, a brewing stand, copper.\n\nOne shard per batch of compound. The rest of the method is on the bench.");
    }

    public static ItemStack gabrielGray() {
        return book("Containment: G. Gray", "Security",
                "SUBJECT: Gabriel Gray, called \"Sylar\". Watchmaker. Intuitive aptitude: he understands how things work.\n\nIncluding people.",
                "Day 1 of lockdown. The subject asked to see the regeneration sleeper. Request denied.\n\nDay 3. Two guards missing. Pod 14 found open and empty of blood.",
                "Day 4. Cell breached from inside. The bars were not cut. They were unmade.\n\nHe took her regeneration. He will not age.",
                "He knows the pods will open.\n\nHe is patient. He will wait at the top of the shaft for as long as it takes.\n\nIf you can hear ticking, you are not alone.");
    }

    public static ItemStack unfamiliarHand() {
        return book("A Note, Folded Small", "H. N.",
                "I came back to stop it. I was too late, again.\n\nThis is the future I saw in the painting: a city of ash. It has already happened here.",
                "But the past is only another place to visit, if you are brave and very careful.\n\nWalk the past. Find the moment. Save the man before he becomes the bomb.",
                "I left a key where a time traveller will find it: in the visions, in the paintings, in the annex beneath the hourglass.\n\nThe future was unwritten once.",
                "It can be unwritten again.\n\nYatta... someday.\n\n- H.");
    }

    /** Noah Bennet's letter, on the lectern in his hidden office under the security checkpoint. */
    public static ItemStack bennet() {
        return book("For Claire", "N. Bennet",
                "Claire,\n\nIf you're reading this, you found the rug. You always did find the things I hid. Christmas presents. Report cards.\n\nThe truth, eventually.",
                "You came to Odessa because you thought Sublevel 4 was where they kept people like you safe. It was. I made sure of it.\n\nI sealed you into Pod 14 myself, the night the city burned.",
                "Gray reached you on the third day. I was two floors up. By the time I got down, the pod was open and he was gone.\n\nYou healed. Of course you healed. He took what he came for anyway.",
                "Before the lockdown I kept one sample of your blood, cold, in the box beside this desk. The Company wanted it. Gray wanted it.\n\nI want whoever wakes to have it, if you can't.",
                "Make something better than we did.\n\nAnd if you ever hear ticking, run.\n\nI love you, Claire-bear.\n\n- Dad");
    }

    /** A guard's shift notes in the security chest: the hint that Bennet's office exists. */
    public static ItemStack shiftNotes() {
        return book("Shift Notes", "Checkpoint B",
                "Week 3. Bennet goes into the checkpoint at night and doesn't come out until morning.\n\nThere's only one door. I watched it.",
                "Week 5. Asked Hansen where Bennet goes. Hansen says Sublevel 4-B. There is no Sublevel 4-B. I've seen the plans.",
                "Week 6. I've checked behind the lockers, the monitors, under the desk. Everywhere except that ugly red rug, which Bennet told me never to move.\n\nI'm going to move the rug.");
    }

    private static ItemStack book(String title, String author, String... pages) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> content = Arrays.stream(pages).map(page -> Filterable.passThrough((Component) Component.literal(page))).toList();
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), author, 0, content, true));
        return stack;
    }
}
