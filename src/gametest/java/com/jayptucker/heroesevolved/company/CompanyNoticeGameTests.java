package com.jayptucker.heroesevolved.company;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.dampener.Dampening;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.gifted.Renegades;
import com.jayptucker.heroesevolved.mimicry.GiftedVillagers;
import com.jayptucker.heroesevolved.radio.EmergencyBand;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.telepathy.TelepathyFocus;
import com.jayptucker.heroesevolved.village.Shouts;
import com.jayptucker.heroesevolved.village.VillageMemory;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The Company notices: attention (what raises and lowers it), the investigator, the convoy and the raid, and when the
 * Company acts. Each test is in its own batch, with its own bell on the arena's floor.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class CompanyNoticeGameTests {
    /** On top of the arena's stone floor (which is at absolutePos y 1). */
    private static BlockPos at(GameTestHelper h, int x, int z) { return h.absolutePos(new BlockPos(x, 2, z)); }

    private static Vec3 spot(GameTestHelper h, int x, int z) { return Vec3.atBottomCenterOf(at(h, x, z)); }

    private static BlockPos bell(GameTestHelper h) {
        BlockPos bell = at(h, 6, 6);
        h.getLevel().setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
        return bell;
    }

    private static Villager villager(GameTestHelper h, BlockPos at, boolean still) {
        Villager v = EntityType.VILLAGER.create(h.getLevel());
        v.moveTo(Vec3.atBottomCenterOf(at));
        v.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        v.setNoAi(still);
        h.getLevel().addFreshEntity(v);
        return v;
    }

    private static ServerPlayer player(GameTestHelper h, Vec3 at, String name, ResourceLocation power) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), name)) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
        };
        try {
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true); grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(at);
        p.setGameMode(GameType.SURVIVAL);
        var data = com.jayptucker.heroesevolved.ability.data.PlayerAbilityData.empty();
        if (power != null) data = data.assignDormant(power).unlock(power);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, data);
        h.getLevel().addNewPlayer(p);
        return p;
    }

    private static void cleanup(GameTestHelper h, BlockPos bell, Entity... entities) {
        CompanyOps.callOffAll();
        for (Entity e : entities) {
            if (e instanceof ServerPlayer p) {
                CompanyAttention.forget(p);
                p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
            } else if (e != null) e.discard();
        }
        if (bell != null) VillageMemory.forget(h.getLevel(), bell);
    }

    private static void killAll(List<CompanyAgentEntity> agents, ServerPlayer by) {
        for (CompanyAgentEntity a : agents) if (a.isAlive()) a.hurt(by.damageSources().playerAttack(by), 1000);
    }

    private static boolean near(float a, float b) { return Math.abs(a - b) < 0.01F; }

    // ------------------------------------------------------------------
    // Attention
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "company.seen")
    public static void bigMovesThatVillagersSeeRaiseAttention(GameTestHelper h) {
        ServerPlayer p = player(h, spot(h, 6, 6), "Wren", null);
        Villager one = null, two = null;
        try {
            h.assertTrue(h.getLevel().dimension() == Level.OVERWORLD, "Tests run in the overworld");
            CompanyAttention.seen(p, "test_big", 60);
            h.assertTrue(CompanyAttention.of(p) == 0, "Nobody saw it: nothing");
            one = villager(h, at(h, 2, 2), true);
            CompanyAttention.seen(p, "test_small", CompanyAttention.LOUD - 1);
            h.assertTrue(CompanyAttention.of(p) == 0, "A small move isn't talked about");
            CompanyAttention.seen(p, "test_big", 60);
            h.assertTrue(near(CompanyAttention.of(p), 7.5F), "Seen by one: an eighth of its cost, " + CompanyAttention.of(p));
            two = villager(h, at(h, 10, 2), true);
            CompanyAttention.seen(p, "test_big", 60);
            h.assertTrue(near(CompanyAttention.of(p), 7.5F + 7.5F * 1.15F), "Seen by two: more, " + CompanyAttention.of(p));
            float before = CompanyAttention.of(p);
            p.setInvisible(true);
            CompanyAttention.seen(p, "test_big", 60);
            h.assertTrue(CompanyAttention.of(p) == before, "Nobody sees an invisible player");
            p.setInvisible(false);
            p.setGameMode(GameType.CREATIVE);
            CompanyAttention.seen(p, "test_big", 120);
            h.assertTrue(CompanyAttention.of(p) == before, "Nor is anyone in creative noticed");
            h.succeed();
        } finally { cleanup(h, null, p, one, two); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "company.deeds")
    public static void endingAVillagesTroubleInFrontOfEveryoneIsTalkedAbout(GameTestHelper h) {
        BlockPos bell = bell(h);
        Villager v = villager(h, at(h, 3, 3), true);
        ServerPlayer hero = player(h, spot(h, 9, 9), "Wren", null), friend = player(h, spot(h, 2, 9), "Ash", null);
        try {
            VillageMemory.record(h.getLevel(), bell, "the wildfire", VillageMemory.Outcome.SAVED, hero, List.of(hero, friend), 1, 1);
            h.assertTrue(near(CompanyAttention.of(hero), CompanyAttention.HERO) && near(CompanyAttention.of(friend), CompanyAttention.PRESENT),
                    "Whoever ended it most, everyone there some: " + CompanyAttention.of(hero) + ", " + CompanyAttention.of(friend));
            VillageMemory.record(h.getLevel(), bell, "a meltdown", VillageMemory.Outcome.HIT, hero, List.of(hero, friend), 1, 2);
            h.assertTrue(near(CompanyAttention.of(hero), CompanyAttention.HERO + CompanyAttention.HIT_HERO), "A village hit but standing: less");
            h.succeed();
        } finally { cleanup(h, bell, v, hero, friend); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "company.quiet")
    public static void quietDaysLowerItAndTheBandsAreSaid(GameTestHelper h) {
        ServerPlayer p = player(h, spot(h, 6, 6), "Wren", null);
        try {
            CompanyAttention.raise(p, 40, null, "test");
            h.assertTrue(CompanyAttention.band(p) == CompanyAttention.Band.NOTICED, "40 is Noticed");
            CompanyAttention.quiet(p, CompanyAttention.QUIET);
            h.assertTrue(near(CompanyAttention.of(p), 40), "The first quiet day is only the start of quiet: " + CompanyAttention.of(p));
            CompanyAttention.quiet(p, CompanyAttention.QUIET);
            h.assertTrue(near(CompanyAttention.of(p), 30), "Then ten a day: " + CompanyAttention.of(p));
            CompanyAttention.quiet(p, CompanyAttention.QUIET);
            h.assertTrue(CompanyAttention.band(p) == CompanyAttention.Band.QUIET, "Below 25 it has forgotten: " + CompanyAttention.of(p));
            h.assertTrue(CompanyAttention.band(55) == CompanyAttention.Band.WATCHED && CompanyAttention.band(80) == CompanyAttention.Band.HUNTED,
                    "The bands start at 25, 50 and 80");
            h.succeed();
        } finally { cleanup(h, null, p); }
    }

    // ------------------------------------------------------------------
    // The investigator
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", timeoutTicks = 1600, batch = "company.investigator")
    public static void anInvestigatorAsksTheVillagersWhatTheySawAndFilesIt(GameTestHelper h) {
        BlockPos bell = bell(h);
        Villager a = villager(h, at(h, 3, 9), true), b = villager(h, at(h, 9, 3), true);
        ServerPlayer p = player(h, spot(h, 11, 11), "Wren", ModAbilities.PYROKINESIS_ID);
        // Raised, not set: a file nobody has added to lately is already fading.
        CompanyAttention.raise(p, 30, null, "test");
        Investigation op = Investigation.start(h.getLevel(), p, at(h, 5, 5), bell, spot(h, 1, 1));
        h.assertTrue(op != null && op.agent() != null, "He comes");
        CompanyAgentEntity agent = op.agent();
        h.assertTrue(agent.onErrand() && !agent.holstered().isEmpty() && agent.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.WRITABLE_BOOK),
                "Gun on his hip, notebook in his hand");
        h.assertTrue(CompanyOps.kind(p) == CompanyOps.Kind.INVESTIGATION, "It's under way");
        AtomicBoolean told = new AtomicBoolean(), turned = new AtomicBoolean();
        h.onEachTick(() -> {
            for (Villager v : List.of(a, b))
                for (String said : Shouts.shouting(v)) if (said.contains("Wren") || said.contains("Fire")) told.set(true);
            if (agent.getTarget() != null) turned.set(true);
        });
        h.succeedWhen(() -> {
            h.assertTrue(op.over(), "Not done yet: " + op.stage() + ", asked " + op.questioned());
            h.assertTrue(op.questioned() == 2, "He asked both of them: " + op.questioned());
            h.assertTrue(told.get(), "They told him who, and what they saw");
            h.assertTrue(!turned.get(), "He stopped nobody");
            h.assertTrue(agent.isRemoved(), "And walked away");
            h.assertTrue(near(CompanyAttention.of(p), 30 + CompanyAttention.FILED), "He has what he came for: " + CompanyAttention.of(p));
            cleanup(h, bell, a, b, p);
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 1600, batch = "company.covered")
    public static void aVillageThatOwesYouCoversForYou(GameTestHelper h) {
        BlockPos bell = bell(h);
        Villager a = villager(h, at(h, 3, 9), true);
        ServerPlayer p = player(h, spot(h, 11, 11), "Wren", null);
        VillageMemory.record(h.getLevel(), bell, "the wildfire", VillageMemory.Outcome.SAVED, p, List.of(p), 1, 1);
        CompanyAttention.set(p, 30);
        Investigation op = Investigation.start(h.getLevel(), p, at(h, 5, 5), bell, spot(h, 1, 1));
        h.assertTrue(op != null && op.covered(), "The village owes them");
        AtomicBoolean lied = new AtomicBoolean(), named = new AtomicBoolean();
        h.onEachTick(() -> {
            for (String said : Shouts.shouting(a)) {
                if (said.contains("Wren")) named.set(true);
                if (said.contains("Never saw") || said.contains("Nothing happened") || said.contains("weather")) lied.set(true);
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(op.over(), "Not done yet: " + op.stage());
            h.assertTrue(lied.get() && !named.get(), "Nobody saw a thing");
            h.assertTrue(near(CompanyAttention.of(p), 30 - CompanyAttention.COVERED), "He leaves with nothing: " + CompanyAttention.of(p));
            cleanup(h, bell, a, p);
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 100, batch = "company.killed")
    public static void killingTheInvestigatorIsNoticed(GameTestHelper h) {
        BlockPos bell = bell(h);
        ServerPlayer p = player(h, spot(h, 11, 11), "Wren", null);
        CompanyAttention.raise(p, 30, null, "test");
        Investigation op = Investigation.start(h.getLevel(), p, at(h, 5, 5), bell, spot(h, 1, 1));
        h.runAfterDelay(5, () -> {
            op.agent().hurt(p.damageSources().playerAttack(p), 1000);
            h.assertTrue(op.over(), "It's over");
            h.assertTrue(near(CompanyAttention.of(p), 30 + CompanyAttention.KILLED), "An agent who doesn't come back is noticed: " + CompanyAttention.of(p));
            h.succeed();
            cleanup(h, bell, p);
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 400, batch = "company.wiped")
    public static void aTelepathTakesWhatHeLearntOutOfHisHead(GameTestHelper h) {
        BlockPos bell = bell(h);
        ServerPlayer p = player(h, spot(h, 11, 11), "Wren", null);
        CompanyAttention.raise(p, 30, null, "test");
        Investigation op = Investigation.start(h.getLevel(), p, at(h, 5, 5), bell, spot(h, 1, 1));
        h.runAfterDelay(5, () -> TelepathyFocus.deepen(p, op.agent(), Investigation.WIPE_FOCUS + 10, 100));
        h.succeedWhen(() -> {
            h.assertTrue(op.over(), "Not done yet: " + op.stage());
            h.assertTrue(op.wiped(), "Wiped");
            h.assertTrue(near(CompanyAttention.of(p), 30 - CompanyAttention.WIPED), "He leaves with nothing: " + CompanyAttention.of(p));
            cleanup(h, bell, p);
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 400, batch = "company.sawpower")
    public static void anInvestigatorWhoSeesYouUseAPowerHasSeenEnough(GameTestHelper h) {
        BlockPos bell = bell(h);
        ServerPlayer p = player(h, spot(h, 8, 8), "Wren", null);
        CompanyAttention.raise(p, 30, null, "test");
        Investigation op = Investigation.start(h.getLevel(), p, at(h, 5, 5), bell, spot(h, 2, 2));
        AtomicReference<Investigation.Stage> after = new AtomicReference<>();
        h.runAfterDelay(5, () -> {
            CompanyAttention.seen(p, "test_small", 5);
            after.set(op.stage());
        });
        h.succeedWhen(() -> {
            h.assertTrue(op.over(), "Not done yet: " + op.stage());
            h.assertTrue(after.get() == Investigation.Stage.LEAVING, "He left at once: " + after.get());
            h.assertTrue(near(CompanyAttention.of(p), 30 + CompanyAttention.AGENT_SAW + CompanyAttention.FILED),
                    "He saw it, and filed it: " + CompanyAttention.of(p));
            cleanup(h, bell, p);
        });
    }

    // ------------------------------------------------------------------
    // The convoy
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "company.convoy")
    public static void aConvoyTakesAGuardianAndItsSuppressorKeepsTheirGiftQuiet(GameTestHelper h) {
        BlockPos bell = bell(h);
        Villager v = villager(h, at(h, 4, 6), false);
        Renegades.guard(v, ModAbilities.PYROKINESIS_ID);
        ServerPlayer p = player(h, spot(h, 6, -40), "Wren", null);
        try {
            h.assertTrue(GiftedMobs.isGood(v) && !Dampening.dampened(v), "A guardian, their gift awake");
            Convoy op = Convoy.start(h.getLevel(), p, bell, new Vec3(1, 0, 0));
            h.assertTrue(op != null && op.prisoner() == v, "They take the guardian");
            h.assertTrue(op.suppressor() != null && op.suppressor().role() == CompanyAgentEntity.Role.SUPPRESSOR && op.guards().size() == Convoy.GUARDS,
                    "Three agents and a Suppressor");
            h.assertTrue(v.getTags().contains(CompanyOps.PRISONER_TAG), "A prisoner");
            h.assertTrue(op.call() != null && EmergencyBand.live(op.call()) && op.call().trouble() == EmergencyBand.Trouble.COMPANY,
                    "The village calls for help");
            h.runAfterDelay(2, () -> {
                try {
                    h.assertTrue(Dampening.dampened(v), "The Suppressor's quiet keeps their gift asleep");
                    h.assertTrue(op.suppressor().getTarget() == null && !op.alerted(), "Nobody near: nobody stopped");
                    CompanyOps.callOffAll();
                    h.assertTrue(!v.getTags().contains(CompanyOps.PRISONER_TAG) && op.suppressor().isRemoved() && !EmergencyBand.live(op.call()),
                            "Called off: they let go, leave, and the call ends");
                    h.succeed();
                } finally { cleanup(h, bell, v, p); }
            });
        } catch (RuntimeException e) { cleanup(h, bell, v, p); throw e; }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 200, batch = "company.convoywarn")
    public static void aConvoyWarnsWhoeverComesNearThenFightsThemUpClose(GameTestHelper h) {
        BlockPos bell = bell(h);
        Villager v = villager(h, at(h, 3, 6), false);
        ServerPlayer p = player(h, spot(h, 6, -40), "Wren", null);
        Convoy op = Convoy.start(h.getLevel(), p, bell, new Vec3(1, 0, 0));
        h.assertTrue(op != null, "They take someone");
        ServerPlayer passer = player(h, op.center().add(0, 0, 12), "Ash", null);
        h.runAfterDelay(10, () -> {
            h.assertTrue(!op.alerted() && op.suppressor().getTarget() == null, "Twelve blocks off: warned, not stopped");
            boolean warned = false;
            for (CompanyAgentEntity g : op.guards()) for (String s : Shouts.shouting(g)) if (s.contains("Company business")) warned = true;
            h.assertTrue(warned, "The lead agent warns them");
            passer.setPos(op.prisoner().position().add(0, 0, 3));
        });
        h.runAfterDelay(20, () -> {
            h.assertTrue(op.alerted(), "Too close: alarmed");
            int on = 0;
            for (CompanyAgentEntity a : op.agents()) if (a.getTarget() == passer) on++;
            h.assertTrue(on == op.agents().size(), "Every agent turns on them: " + on);
            h.succeed();
            cleanup(h, bell, v, p, passer);
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "company.convoyfreed")
    public static void freeingTheConvoysPrisonerMakesThemTheirVillagesGuardian(GameTestHelper h) {
        BlockPos bell = bell(h);
        Villager v = villager(h, at(h, 4, 6), false);
        GiftedVillagers.give(v, ModAbilities.PYROKINESIS_ID);
        ServerPlayer p = player(h, spot(h, 6, -40), "Wren", null);
        ServerPlayer hero = player(h, spot(h, 9, 9), "Ash", null);
        CompanyAttention.set(p, 55);
        Convoy op = Convoy.start(h.getLevel(), p, bell, new Vec3(1, 0, 0));
        h.assertTrue(op != null && op.prisoner() == v && !GiftedMobs.isGifted(v), "They take the one with a dormant gift");
        h.runAfterDelay(2, () -> killAll(op.agents(), hero));
        h.runAfterDelay(4, () -> {
            h.assertTrue(op.over() && op.stage() == Convoy.Stage.FREED, "Every agent down: free (" + op.stage() + ")");
            h.assertTrue(GiftedMobs.isGood(v) && !v.getTags().contains(CompanyOps.PRISONER_TAG), "Their gift wakes: their village's guardian");
            var memory = VillageMemory.of(h.getLevel(), bell);
            var last = memory.history().getLast();
            h.assertTrue(last.what().equals("the Company") && last.outcome() == VillageMemory.Outcome.SAVED && "Ash".equals(last.hero()),
                    "The village remembers who freed them");
            h.assertTrue(near(CompanyAttention.of(hero), CompanyAttention.HERO + CompanyAttention.FREED),
                    "Fighting the Company gets you noticed: " + CompanyAttention.of(hero));
            boolean spoils = !h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(v.blockPosition()).inflate(4),
                    i -> i.getItem().is(Items.EMERALD)).isEmpty();
            h.assertTrue(spoils, "Their gear is left at the prisoner's feet");
            h.assertTrue(!EmergencyBand.live(op.call()), "The call ends");
            h.succeed();
            cleanup(h, bell, v, p, hero);
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "company.convoytaken")
    public static void aConvoyThatGetsAwayTakesThem(GameTestHelper h) {
        BlockPos bell = bell(h);
        Villager v = villager(h, at(h, 4, 6), false);
        ServerPlayer p = player(h, spot(h, 6, -40), "Wren", null);
        CompanyAttention.set(p, 60);
        Convoy op = Convoy.start(h.getLevel(), p, bell, new Vec3(1, 0, 0));
        h.assertTrue(op != null, "They take someone");
        h.runAfterDelay(2, () -> op.hurry(Convoy.LENGTH));
        h.runAfterDelay(4, () -> {
            h.assertTrue(op.over() && op.stage() == Convoy.Stage.TAKEN, "Out of reach: gone (" + op.stage() + ")");
            h.assertTrue(v.isRemoved(), "And the prisoner with it");
            var last = VillageMemory.of(h.getLevel(), bell).history().getLast();
            h.assertTrue(last.what().equals("the Company") && last.outcome() == VillageMemory.Outcome.HIT, "The village remembers");
            h.assertTrue(near(CompanyAttention.of(p), 60 - CompanyAttention.GOT_AWAY), "The Company has what it wanted, for now: " + CompanyAttention.of(p));
            h.succeed();
            cleanup(h, bell, v, p);
        });
    }

    // ------------------------------------------------------------------
    // The raid
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "company.raid")
    public static void aRaidComesWithASuppressorAndBeatingItEasesOff(GameTestHelper h) {
        ServerPlayer p = player(h, spot(h, 6, 6), "Wren", null);
        CompanyAttention.set(p, 90);
        Raid op = Raid.start(h.getLevel(), p, at(h, 6, 6), spot(h, 3, 3));
        h.assertTrue(op != null && op.stage() == Raid.Stage.WARNING, "A warning first");
        op.now();
        h.runAfterDelay(4, () -> {
            h.assertTrue(op.stage() == Raid.Stage.COMING && op.suppressor() != null && op.guards().size() == Raid.GUARDS,
                    "A Suppressor and three agents: " + op.stage() + ", " + op.guards().size());
            h.assertTrue(CompanyOps.stops(op.guards().getFirst(), p), "They stop whoever is at home");
            h.assertTrue(SuppressorField.suppresses(op.suppressor()), "His quiet comes with him");
            killAll(op.agents(), p);
        });
        h.runAfterDelay(6, () -> {
            h.assertTrue(op.over() && op.stage() == Raid.Stage.BEATEN, "Every agent down: beaten (" + op.stage() + ")");
            h.assertTrue(near(CompanyAttention.of(p), CompanyAttention.RAID_BEATEN), "They back off: " + CompanyAttention.of(p));
            h.succeed();
            cleanup(h, null, p);
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 300, batch = "company.raidwon")
    public static void aRaidPullsBackWhenItsSubjectGoesDown(GameTestHelper h) {
        ServerPlayer p = player(h, spot(h, 6, 6), "Wren", null);
        CompanyAttention.set(p, 90);
        Raid op = Raid.start(h.getLevel(), p, at(h, 6, 6), spot(h, 3, 3));
        op.now();
        List<CompanyAgentEntity> team = new ArrayList<>();
        h.runAfterDelay(2, () -> {
            team.addAll(op.agents());
            p.setHealth(0);
        });
        h.runAfterDelay(4, () -> h.assertTrue(op.stage() == Raid.Stage.WITHDRAWING
                && near(CompanyAttention.of(p), CompanyAttention.RAID_WON), "They pull back: " + op.stage() + ", " + CompanyAttention.of(p)));
        h.succeedWhen(() -> {
            h.assertTrue(op.over(), "Not gone yet");
            for (CompanyAgentEntity a : team) h.assertTrue(a.isRemoved(), "Every agent gone");
            cleanup(h, null, p);
        });
    }

    // ------------------------------------------------------------------
    // When the Company acts
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "company.consider")
    public static void theCompanyActsByBandAndByTheClock(GameTestHelper h) {
        BlockPos bell = bell(h);
        Villager v = villager(h, at(h, 4, 6), true);
        ServerPlayer p = player(h, spot(h, 9, 9), "Wren", null);
        long morning = 1000, midnight = 18_000;
        try {
            CompanyAttention.raise(p, 30, at(h, 5, 5), "test");
            h.assertTrue(CompanyOps.pending(p), "Noticed in a village: someone is coming to ask questions");
            CompanyOps.callOffAll();
            CompanyAttention.set(p, 20);
            h.assertTrue(CompanyOps.consider(p, morning) == null, "Quiet: nothing");
            CompanyAttention.set(p, 55);
            h.assertTrue(CompanyOps.consider(p, midnight) == null, "Watched, but at night: no convoy");
            CompanyOps.Op convoy = CompanyOps.consider(p, morning);
            h.assertTrue(convoy instanceof Convoy c && c.bell().equals(bell), "Watched, by day, near a village: a convoy");
            CompanyOps.callOffAll();
            CompanyAttention.file(p).nextOp = -1000;
            CompanyAttention.set(p, 90);
            h.assertTrue(CompanyOps.consider(p, morning) == null, "Hunted, but by day: not yet");
            p.setRespawnPosition(Level.OVERWORLD, at(h, 6, 6), 0, true, false);
            CompanyOps.Op raid = CompanyOps.consider(p, midnight);
            h.assertTrue(raid instanceof Raid r && r.home().equals(at(h, 6, 6)), "Hunted, at night, at home: a raid");
            h.assertTrue(CompanyOps.consider(p, midnight) == null, "One at a time");
            CompanyOps.callOffAll();
            CompanyAttention.file(p).nextOp = -1000;
            p.setRespawnPosition(Level.OVERWORLD, at(h, 6, 6).offset(200, 0, 0), 0, true, false);
            h.assertTrue(CompanyOps.consider(p, midnight) == null, "Not while they're away from home");
            h.succeed();
        } finally { cleanup(h, bell, v, p); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "company.leftovers")
    public static void leftoverAgentsAreTurnedAwayAndPrisonersLetGo(GameTestHelper h) {
        CompanyAgentEntity agent = CompanyAgentEntity.create(ModEntities.COMPANY_AGENT.get(), h.getLevel(), CompanyAgentEntity.Role.GUARD,
                spot(h, 3, 3), 0, false);
        agent.addTag(CompanyOps.AGENT_TAG);
        Villager v = EntityType.VILLAGER.create(h.getLevel());
        v.moveTo(spot(h, 8, 8));
        v.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        v.addTag(CompanyOps.PRISONER_TAG);
        try {
            h.assertTrue(!h.getLevel().addFreshEntity(agent), "An agent of no live operation is turned away");
            h.assertTrue(h.getLevel().addFreshEntity(v) && !v.getTags().contains(CompanyOps.PRISONER_TAG), "A prisoner of none is let go");
            h.succeed();
        } finally { cleanup(h, null, agent, v); }
    }
}
