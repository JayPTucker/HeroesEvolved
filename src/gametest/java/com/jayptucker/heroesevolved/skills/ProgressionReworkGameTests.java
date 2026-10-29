package com.jayptucker.heroesevolved.skills;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseResult;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityRanks;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.EvolutionTrials;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.progression.PowerLevels;
import com.jayptucker.heroesevolved.progression.StoryDiscoveries;
import com.jayptucker.heroesevolved.story.bunker.BunkerLogs;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** Power Level 1 to 20, free key binding, the wheel's cast-by-action, ranks, Evolved trials and story points. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class ProgressionReworkGameTests {
    private static ServerPlayer player(GameTestHelper h, ResourceLocation power, int tier) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "progress-test"));
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        PlayerProgressionService.setLevel(p, tier);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        return p;
    }

    @GameTest(template = "rewind_empty", batch = "progress.levels")
    public static void powerLevelsKeepEveryOldTier(GameTestHelper h) {
        var p = player(h, ModAbilities.QUAKE_ID, 5);
        h.assertTrue(PlayerProgressionService.getPowerLevel(p) == 15, "Old Level 5 must be Power Level 15");
        h.assertTrue(PlayerProgressionService.getLevel(p) == 5, "Power Level 15 must still be tier 5");
        for (int tier = 1; tier <= 5; tier++) {
            int level = PowerLevels.levelForTier(tier);
            h.assertTrue(PowerLevels.tierFor(level) == tier && (level == 1 || PowerLevels.tierFor(level - 1) == tier - 1),
                    "Tier " + tier + " must begin exactly at Power Level " + level);
            h.assertTrue(PowerLevels.masteryFor(level) == com.jayptucker.heroesevolved.progression.ProgressionCalculator
                    .masteryRequiredForLevel(tier), "Each tier keeps the Mastery it always needed");
        }
        for (int level = 2; level <= 20; level++)
            h.assertTrue(PowerLevels.masteryFor(level) > PowerLevels.masteryFor(level - 1), "Every level must cost Mastery");
        h.assertTrue(PowerLevels.points(1) == 3 && PowerLevels.points(15) == 21 && PowerLevels.points(19) == 27
                && PowerLevels.points(20) == 28, "Points: 3 at awakening, 21 at 15, 27 at 19, 28 Evolved");
        h.assertTrue(PowerLevels.stage(4) == PowerLevels.Stage.MANIFESTING && PowerLevels.stage(5) == PowerLevels.Stage.CONTROLLED
                && PowerLevels.stage(19) == PowerLevels.Stage.MASTERED && PowerLevels.stage(20) == PowerLevels.Stage.EVOLVED, "Stages");
        PlayerProgressionService.setPowerLevel(p, 9);
        h.assertTrue(PlayerProgressionService.getLevel(p) == 3 && PlayerProgressionService.getPowerLevel(p) == 9, "Level 9 is tier 3");
        h.assertTrue(SkillTreeService.build(p, ModAbilities.QUAKE_ID).earned() == PowerLevels.points(15),
                "Points earned never shrink: the build remembers the highest level reached");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "progress.evolve")
    public static void level20NeedsTheTrialAsWellAsTheMastery(GameTestHelper h) {
        var p = player(h, ModAbilities.QUAKE_ID, 1);
        MasteryService.grantMastery(p, PowerLevels.masteryFor(PowerLevels.EVOLVED) + 10);
        h.assertTrue(PlayerProgressionService.getPowerLevel(p) == 19, "Mastery alone stops at 19");
        h.assertTrue(!PlayerProgressionService.evolved(p), "Not Evolved before the trial");
        int goal = EvolutionTrials.trial("quake").goal();
        for (int i = 0; i < goal - 1; i++) EvolutionTrials.advance(p, ModAbilities.QUAKE_ID);
        h.assertTrue(PlayerProgressionService.getPowerLevel(p) == 19, "One defeat short is still 19");
        EvolutionTrials.advance(p, ModAbilities.TELEPATHY_ID);
        h.assertTrue(EvolutionTrials.progress(p, "quake") == goal - 1, "Another power's feat must not count");
        EvolutionTrials.advance(p, ModAbilities.QUAKE_ID);
        h.assertTrue(PlayerProgressionService.evolved(p) && PlayerProgressionService.getPowerLevel(p) == 20, "Trial plus Mastery Evolves");
        h.assertTrue(SkillTreeService.build(p, ModAbilities.QUAKE_ID).earned() == PowerLevels.points(20), "Evolving gives its point");

        // The other order: trial first, Mastery later.
        var q = player(h, ModAbilities.TIME_MANIPULATION_ID, 1);
        EvolutionTrials.advance(q, ModAbilities.TIME_MANIPULATION_ID);
        h.assertTrue(!PlayerProgressionService.evolved(q), "A finished trial waits for the Mastery");
        MasteryService.grantMastery(q, PowerLevels.masteryFor(PowerLevels.EVOLVED));
        h.assertTrue(PlayerProgressionService.getPowerLevel(q) == 20, "Reaching the Mastery after the trial Evolves");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "progress.bind")
    public static void anyAbilityCanGoOnAnyKey(GameTestHelper h) {
        var p = player(h, ModAbilities.QUAKE_ID, 5);
        var quake = ModAbilities.QUAKE_ID;
        String primary = Loadout.base(AbilitySlot.PRIMARY);
        try {
            h.assertTrue(SkillTreeService.bind(p, 6, primary), "A core ability can go on key 6");
            var build = SkillTreeService.build(p, quake);
            h.assertTrue(Loadout.binding(build, 6).equals(primary), "Key 6 holds it");
            h.assertTrue(Loadout.binding(build, 1).isEmpty(), "Key 1 is left empty: its default moved");
            h.assertTrue(AbilityUseService.activateKey(p, 1, false) == AbilityUseResult.ACTION_NOT_ASSIGNED, "An empty key casts nothing");
            h.assertTrue(AbilityUseService.activateKey(p, 6, false) == AbilityUseResult.SUCCESS, "Key 6 casts it");

            h.assertTrue(SkillTreeService.bind(p, 2, ""), "A key can be emptied");
            build = SkillTreeService.build(p, quake);
            h.assertTrue(Loadout.binding(build, 2).isEmpty() && Loadout.keyOf(build, Loadout.base(AbilitySlot.SECONDARY)) == 0,
                    "Emptied: Secondary is now only on the wheel");
            h.assertTrue(AbilityUseService.activateAction(p, Loadout.base(AbilitySlot.SECONDARY), false) != AbilityUseResult.ACTION_NOT_ASSIGNED,
                    "The wheel casts an ability that is on no key");

            h.assertTrue(!SkillTreeService.bind(p, 9, primary), "There is no key 9");
            h.assertTrue(!SkillTreeService.bind(p, 3, "quake_2_5"), "An unlearned skill cannot be bound");
            h.assertTrue(!SkillTreeService.bind(p, 3, "base:NOPE"), "Nonsense is rejected");
            h.assertTrue(AbilityUseService.activateAction(p, "telepathy_0_1", false) == AbilityUseResult.ACTION_NOT_ASSIGNED,
                    "The wheel cannot cast another power's skill");

            SkillTreeService.bind(p, 1, null);
            h.assertTrue(Loadout.binding(SkillTreeService.build(p, quake), 1).equals(primary)
                    && Loadout.keyOf(SkillTreeService.build(p, quake), primary) == 1, "Restoring a default takes it back from key 6");
        } finally {
            CooldownService.clearAll(p);
        }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "progress.ranks")
    public static void abilitiesRankUpWithUse(GameTestHelper h) {
        var p = player(h, ModAbilities.QUAKE_ID, 5);
        var action = AbilityRegistry.ABILITIES.get(ModAbilities.QUAKE_ID).actions().get(AbilitySlot.PRIMARY);
        int cooldown = 200, cost = 40;
        h.assertTrue(AbilityRanks.cooldown(p, ModAbilities.QUAKE_ID, action, cooldown) == cooldown, "Rank I is the base");
        for (int i = 0; i < AbilityRanks.RANK_TWO; i++) AbilityRanks.used(p, ModAbilities.QUAKE_ID, action);
        h.assertTrue(AbilityRanks.rank(p, ModAbilities.QUAKE_ID, action) == 2, "25 uses is Rank II");
        h.assertTrue(AbilityRanks.cooldown(p, ModAbilities.QUAKE_ID, action, cooldown) == 180
                && AbilityRanks.energy(p, ModAbilities.QUAKE_ID, action, cost) == cost, "Rank II: 10% faster, same stamina");
        for (int i = AbilityRanks.RANK_TWO; i < AbilityRanks.RANK_THREE; i++) AbilityRanks.used(p, ModAbilities.QUAKE_ID, action);
        h.assertTrue(AbilityRanks.cooldown(p, ModAbilities.QUAKE_ID, action, cooldown) == 160
                && AbilityRanks.energy(p, ModAbilities.QUAKE_ID, action, cost) == 36, "Rank III: 20% faster, 10% cheaper");
        h.assertTrue(p.getData(ModDataAttachments.POWER_JOURNEY).uses("telepathy", action.definition().id().getPath()) == 0,
                "Uses belong to one power");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "progress.story")
    public static void storyNotesGiveOnePointEach(GameTestHelper h) {
        var p = player(h, ModAbilities.QUAKE_ID, 1);
        var build = SkillTreeService.build(p, ModAbilities.QUAKE_ID);
        int before = SkillCatalog.available("quake", build, SkillTreeService.storyPoints(p));
        long mastery = PlayerProgressionService.getData(p).mastery();
        h.assertTrue(StoryDiscoveries.found(p, BunkerLogs.lockdown()), "A new note is a discovery");
        h.assertTrue(!StoryDiscoveries.found(p, BunkerLogs.lockdown()), "The same note twice is not");
        h.assertTrue(SkillCatalog.available("quake", build, SkillTreeService.storyPoints(p)) == before + 1, "It gives a skill point");
        h.assertTrue(PlayerProgressionService.getData(p).mastery() == mastery + StoryDiscoveries.MASTERY, "And some Mastery");

        ItemStack forged = new ItemStack(Items.WRITTEN_BOOK);
        forged.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("For Claire"),
                p.getGameProfile().getName(), 0, List.of(Filterable.passThrough((Component) Component.literal("hi"))), true));
        h.assertTrue(!StoryDiscoveries.found(p, forged), "A book the player signed themself is not a story note");

        for (String title : StoryDiscoveries.NOTES.keySet()) {
            ItemStack note = new ItemStack(Items.WRITTEN_BOOK);
            note.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title),
                    StoryDiscoveries.NOTES.get(title), 0, List.of(), true));
            StoryDiscoveries.found(p, note);
        }
        h.assertTrue(SkillTreeService.storyPoints(p) == 8, "Story points stop at eight");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "progress.mastery")
    public static void usingThePowerOnSomethingEarnsMastery(GameTestHelper h) {
        var p = player(h, ModAbilities.QUAKE_ID, 1);
        long start = PlayerProgressionService.getData(p).mastery();
        MasteryService.awardPowerHit(p, 10);
        h.assertTrue(PlayerProgressionService.getData(p).mastery() == start, "A plain hit without the power earns nothing");
        MasteryService.markPowerUse(p, ModAbilities.QUAKE_ID);
        MasteryService.awardPowerHit(p, 10);
        h.assertTrue(PlayerProgressionService.getData(p).mastery() == start + 5, "A power hit earns one per two damage");
        MasteryService.awardPowerHit(p, 10);
        h.assertTrue(PlayerProgressionService.getData(p).mastery() == start + 5, "Hits are counted at most twice a second");
        MasteryService.awardRescue(p, 15);
        h.assertTrue(PlayerProgressionService.getData(p).mastery() == start + 20, "Saving someone earns Mastery");
        MasteryService.forgetPlayer(p.getUUID());
        h.succeed();
    }
}
