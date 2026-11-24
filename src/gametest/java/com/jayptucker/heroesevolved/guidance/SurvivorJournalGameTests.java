package com.jayptucker.heroesevolved.guidance;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class SurvivorJournalGameTests {
    @GameTest(template = "rewind_empty", batch = "guidance")
    public static void realLootCreatesReadableQuillsAndKeepsOriginalStoryNotes(GameTestHelper h) {
        var params = new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN,
                Vec3.atCenterOf(h.absolutePos(BlockPos.ZERO))).create(LootContextParamSets.CHEST);
        Set<String> expected = Set.of("A Watch at Dusk", "Glass That Fed It", "The Eye That Waited",
                "Under a Violet Sky", "The Bell Kept Ringing", "Nine Quiet Footsteps");
        for (String name : new String[]{"survivor_journals", "ruins_house", "odessa_supplies"}) {
            var table = h.getLevel().getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                    ResourceLocation.fromNamespaceAndPath("heroesevolved", "chests/" + name)));
            Set<String> found = new HashSet<>();
            boolean original = false, supplies = false, withoutJournal = false;
            for (int seed = 1; seed <= 256; seed++) {
                boolean journalThisRoll = false;
                for (var item : table.getRandomItems(params, seed)) {
                    if (item.is(Items.WRITABLE_BOOK)) {
                        journalThisRoll = true;
                        var pages = item.get(DataComponents.WRITABLE_BOOK_CONTENT);
                        h.assertTrue(pages != null && pages.pages().size() == 4, "The actual loot decoder keeps the cover and three entries");
                        String title = item.getHoverName().getString();
                        h.assertTrue(expected.contains(title) && pages.pages().getFirst().raw().startsWith(title), "Vanilla quills keep their title and attribution");
                        h.assertTrue(item.get(DataComponents.WRITTEN_BOOK_CONTENT) == null, "Unfinished notes remain editable book and quills");
                        found.add(title);
                    } else if (item.is(Items.WRITTEN_BOOK)) original = true;
                    else supplies = true;
                }
                if (!journalThisRoll) withoutJournal = true;
            }
            h.assertTrue(found.equals(expected), "All six clues can be found in " + name + ": " + found);
            if (!name.equals("survivor_journals")) h.assertTrue(supplies && withoutJournal, "Clues remain optional additions to normal scavenging");
            if (name.equals("ruins_house")) h.assertTrue(original, "Original signed story notes still drop");
        }
        h.succeed();
    }
}
