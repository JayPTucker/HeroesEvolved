package com.jayptucker.heroesevolved.story.bunker;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.time.intro.city.Apartments;
import com.jayptucker.heroesevolved.time.intro.city.CityGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.Arrays;
import java.util.List;

/** Ordinary scavenging and eight small, unfinished lives. These are vanilla books and keepsakes, not quest items. */
public final class OdessaCaches {
    private OdessaCaches() {}
    private static final ResourceKey<LootTable> SUPPLIES = ResourceKey.create(Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "chests/odessa_supplies"));

    public record Journal(int x, int z, String title, String author, boolean unfinished, String... pages) {}
    public static final List<Journal> JOURNALS = List.of(
            new Journal(Apartments.X0 + 14, Apartments.Z0 + 3, "The Spare Key", "Elena Ruiz", false,
                    "Day 1.\n\nThe stair at the Arms is still standing. I keep hearing someone coming down it. Every time I look, it is plaster falling.",
                    "Day 3.\n\nI went back to Primatech. The green doors were open upstairs. The steel one below would not move. I left my name where they might see it.",
                    "Day 6.\n\nA grocer left tins behind the last shelf at the City Market. I took two. My spare key is here for Tomas. He always loses his.\n\nDay 8. I cannot manage the stairs today."),
            new Journal(18, 58, "Stock Book", "Nikhil Shah", true,
                    "Nikhil Shah, City Market.\n\nDay 2: Six tins. Two bottles. Gave the little girl my coat. The front came down before the big light; I keep thinking I ought to have heard it sooner.",
                    "Day 5: Four tins. One bottle. Leave the rear shelf alone; that is for whoever comes after us. Someone has made beds between the church pews, east across the avenue.",
                    "Day 9: I can see the market sign from my bed. I used to hate taking it down in the rain.\n\nDay 11: If the rain starts, put the pot under the"),
            new Journal(54, 56, "Names in the Aisle", "Ruth Bell", false,
                    "Day 1.\n\nWe laid eleven people in the church aisle. I used the altar cloth for bandages. Nobody objected. I wish someone had been here to object.",
                    "Day 4.\n\nSeven now. I am writing their names in the city hall ledger. They should have more than a number. A council clerk hid the ledger behind the rear desk.",
                    "Day 10.\n\nOne blanket occupied. Mine. I put the clean cloth beneath the altar. If someone comes in, let them find something useful."),
            new Journal(-18, 56, "Temporary Register", "Ada Mercer", true,
                    "City of Odessa. Temporary register.\n\nDay 2. I wrote SURVIVORS at the top. There are nineteen names. I left room for more.",
                    "Day 7. Nobody has added a name since Tuesday. I no longer know if it is Tuesday. Ruth brings me the names from the church; I write them on a different page.",
                    "Day 13. I took the word temporary off the cover.\n\nDay 16. Please tell my sister I stayed at the desk. Her name is"),
            new Journal(-17, -18, "Night Shift", "Leon Cho", false,
                    "Day 1.\n\nI kept the State Building keys because I was still on shift. There is no top to the building now. I don't know when my shift ends.",
                    "Day 4.\n\nThe service cupboard has wire and spare metal. Take them. The lifts are gone; don't try the shafts. The smaller tower across the avenue has fallen toward us.",
                    "Day 9.\n\nThe lobby clock is the only thing I have not wound. I am sitting where I can see the door. If the morning man comes, tell him I waited."),
            new Journal(79, 83, "Before the Pour", "Beatrice Holt", true,
                    "Day 2, construction site.\n\nWe never finished the next floor. It is on top of the last one now. I found my lunch tin under the mixer. I was saving the peach for later.",
                    "Day 5.\n\nI tied a bright rag on the fence so the rescue trucks could see us. The road to Primatech is south and then west. There are no trucks.",
                    "Day 8.\n\nI ate the peach. I put the tools in the barrel by the footing. Tomorrow I will try to"),
            new Journal(23, -79, "The Red Sculpture", "Oscar Finch", false,
                    "Day 1. Kirby Plaza.\n\nThe sculpture is still red under the dust. I remember being annoyed when they put it up. Now it is the only thing I recognise.",
                    "Day 3.\n\nThere was a man in the light. Someone flew down to him. I cannot make anybody believe this, but I saw it. I keep trying to draw where they stood.",
                    "Day 6.\n\nI have moved into the broken planter. It stops the wind. I left my compass in the tin. The needle still points somewhere. That seems unfair."),
            new Journal(-79, -79, "Water for Tomorrow", "June Okafor", true,
                    "Day 3, the Commons.\n\nWe dug beside the dry fountain. The ground underneath was damp. I promised there would be water if we kept digging.",
                    "Day 7.\n\nI have the bottles ready. The others went quiet last night. The grass has begun to show through the ash. I wish I could be glad about it.",
                    "Day 12.\n\nTo whoever finds the bottles: boil it first. Promise me you will. I think I can hear rain, but I cannot"));

    static void place(ServerLevel level, BlockPos pos, Journal journal) {
            // A low cupboard set against the surviving room, reachable without a power or a special tool.
            if (!com.jayptucker.heroesevolved.structure.RuinWater.water(level, pos.below()))
                level.setBlock(pos.below(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 2 | 16);
            level.setBlock(pos, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, net.minecraft.core.Direction.UP), 2 | 16);
            if (!com.jayptucker.heroesevolved.structure.RuinWater.water(level, pos.above()))
                level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2 | 16);
            if (level.getBlockEntity(pos) instanceof BarrelBlockEntity box) {
                box.setItem(13, book(journal));
                box.setItem(3, new ItemStack(Items.PAPER, 2));
                box.setItem(7, new ItemStack(Items.CHARCOAL, 3));
                box.setItem(19, keepsake(journal));
                box.setChanged();
            }
    }

    public static ItemStack book(Journal journal) {
        if (!journal.unfinished) return BunkerLogs.book(journal.title, journal.author, journal.pages);
        ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
        book.set(DataComponents.CUSTOM_NAME, Component.literal(journal.title));
        var pages = new java.util.ArrayList<Filterable<String>>();
        pages.add(Filterable.passThrough(journal.title + "\n" + journal.author + "\n\nRecovered from Odessa.\nThe last entry was never finished."));
        Arrays.stream(journal.pages).map(Filterable::passThrough).forEach(pages::add);
        book.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(pages));
        return book;
    }

    private static ItemStack keepsake(Journal journal) {
        boolean keys = journal.title.equals("The Spare Key") || journal.title.equals("Night Shift");
        ItemStack item = new ItemStack(keys ? Items.TRIPWIRE_HOOK : journal.title.equals("The Red Sculpture") ? Items.COMPASS
                : journal.title.equals("Water for Tomorrow") ? Items.GLASS_BOTTLE : Items.PAPER);
        item.set(DataComponents.CUSTOM_NAME, Component.literal(keys ? journal.author + "'s key"
                : journal.title.equals("The Red Sculpture") ? "Oscar's compass" : journal.title.equals("Water for Tomorrow") ? "June's empty bottle" : "A folded scrap"));
        item.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Recovered from Odessa, after the Last Day."))));
        return item;
    }

    static void supplies(ServerLevel level, BlockPos pos, long seed) {
        level.setBlock(pos, Blocks.BARREL.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        if (level.getBlockEntity(pos) instanceof BarrelBlockEntity box) { box.setLootTable(SUPPLIES, seed); box.setChanged(); }
    }
}
