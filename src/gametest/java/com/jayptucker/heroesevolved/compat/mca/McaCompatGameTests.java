package com.jayptucker.heroesevolved.compat.mca;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.gifted.Gift;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.gifted.Renegades;
import com.jayptucker.heroesevolved.mimicry.GiftedVillagers;
import com.jayptucker.heroesevolved.village.Favours;
import com.jayptucker.heroesevolved.village.GiftedWork;
import com.jayptucker.heroesevolved.village.VillagerMeltdown;
import com.jayptucker.heroesevolved.village.Villagers;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Villagers with and without Minecraft Comes Alive. Every test runs either way: with {@code -Pmca} an ordinary villager
 * must become MCA's human and keep who they are, and a scripted one must be left alone; without it, both are plain
 * vanilla spawning. MCA's classes are only reached through {@link McaTestCalls}, and only when it is loaded.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class McaCompatGameTests {
    private static boolean mca() { return ModList.get().isLoaded("mca"); }

    private static boolean human(Entity e) { return "mca".equals(EntityType.getKey(e.getType()).getNamespace()); }

    private static Vec3 at(GameTestHelper h, int x, int z) { return Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x, 2, z))); }

    private static Villager plain(GameTestHelper h, int x, int z) {
        Villager v = EntityType.VILLAGER.create(h.getLevel());
        v.moveTo(at(h, x, z));
        v.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        return v;
    }

    /** A vanilla villager already standing in the world, as one from before MCA was added to a save. */
    private static Villager alreadyHere(GameTestHelper h, int x, int z) {
        Villager v = Villagers.actor(h.getLevel());
        v.removeTag(Villagers.ACTOR_TAG);
        v.moveTo(at(h, x, z));
        v.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        h.assertTrue(h.getLevel().addFreshEntity(v), "A held-back villager is added");
        return v;
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "mca.actor")
    public static void aScriptedVillagerIsPlacedAndStaysVanilla(GameTestHelper h) {
        Villager actor = Villagers.actor(h.getLevel());
        actor.moveTo(at(h, 4, 4));
        actor.setNoAi(true);
        actor.setCustomName(Component.literal("Odessa Local"));
        h.assertTrue(h.getLevel().addFreshEntity(actor), "A scene's villager is added at once");
        h.runAfterDelay(12, () -> {
            h.assertTrue(h.getLevel().getEntity(actor.getUUID()) == actor && !actor.isRemoved(), "The same body is still there");
            h.assertFalse(human(actor), "A scripted villager stays vanilla");
            actor.discard();
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "mca.identity")
    public static void anOrdinaryVillagerKeepsWhoTheyAre(GameTestHelper h) {
        Villager v = plain(h, 5, 5);
        UUID id = v.getUUID();
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(3));
        v.setCustomName(Component.literal("Testa"));
        v.getPersistentData().putString("heroesevolved_test_marker", "kept");
        GiftedVillagers.give(v, ModAbilities.REGENERATION_ID);
        Villager in = Villagers.enter(h.getLevel(), v);
        h.assertTrue(in != null, "They arrive");
        h.assertTrue(in.getUUID().equals(id), "Same UUID");
        h.assertTrue(human(in) == mca(), mca() ? "MCA's human stands in" : "Without MCA they stay vanilla");
        h.assertTrue(mca() || in == v, "Without MCA it is the same object");
        h.runAfterDelay(12, () -> {
            h.assertTrue(h.getLevel().getEntity(id) == in && !in.isRemoved(), "They are still there, not swapped again");
            h.assertTrue(h.getLevel().getEntitiesOfClass(Villager.class, new AABB(in.blockPosition()).inflate(3)).size() == 1, "Only one of them");
            h.assertTrue("Testa".equals(in.getCustomName().getString()), "Their name is kept");
            h.assertTrue(in.getVillagerData().getProfession() == VillagerProfession.FARMER && in.getVillagerData().getLevel() == 3, "Their job is kept");
            h.assertTrue("kept".equals(in.getPersistentData().getString("heroesevolved_test_marker")), "Their saved data is kept");
            h.assertTrue(ModAbilities.REGENERATION_ID.equals(GiftedVillagers.gift(in)), "Their gift is kept");
            in.discard();
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "mca.child")
    public static void aChildStaysAChild(GameTestHelper h) {
        Villager child = plain(h, 5, 5);
        child.setBaby(true);
        Villager adult = plain(h, 7, 7);
        Villager inChild = Villagers.enter(h.getLevel(), child);
        Villager inAdult = Villagers.enter(h.getLevel(), adult);
        h.assertTrue(inChild != null && inAdult != null, "Both arrive");
        h.runAfterDelay(5, () -> {
            h.assertTrue(inChild.isBaby(), "A child is still a child");
            h.assertFalse(inAdult.isBaby(), "An adult is never rolled into a baby");
            inChild.discard();
            inAdult.discard();
            h.succeed();
        });
    }

    /** MCA's admin conversion queues villagers already in the world: identity survives it, and scenes keep their cast. */
    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "mca.queue")
    public static void convertingTheWorldKeepsPeopleAndLeavesScenes(GameTestHelper h) {
        if (!mca()) { h.succeed(); return; }
        Villager actor = Villagers.actor(h.getLevel());
        actor.moveTo(at(h, 2, 2));
        actor.setNoAi(true);
        h.getLevel().addFreshEntity(actor);
        Villager traveller = alreadyHere(h, 9, 2);
        traveller.addTag("heroesevolved_traveller");
        Villager dormant = alreadyHere(h, 4, 8);
        GiftedVillagers.give(dormant, ModAbilities.CRYOKINESIS_ID);
        Villager guardian = alreadyHere(h, 8, 8);
        Renegades.guard(guardian, ModAbilities.PYROKINESIS_ID);
        UUID dormantId = dormant.getUUID(), guardianId = guardian.getUUID();
        McaTestCalls.convert(actor);
        McaTestCalls.convert(traveller);
        McaTestCalls.convert(dormant);
        McaTestCalls.convert(guardian);
        h.runAfterDelay(12, () -> {
            h.assertTrue(h.getLevel().getEntity(actor.getUUID()) == actor && !human(actor), "A scene's body is left alone");
            h.assertTrue(h.getLevel().getEntity(traveller.getUUID()) == traveller && !human(traveller), "Someone on an errand is left alone");
            Entity newDormant = h.getLevel().getEntity(dormantId), newGuardian = h.getLevel().getEntity(guardianId);
            h.assertTrue(newDormant != null && human(newDormant), "A villager is converted under the same UUID");
            h.assertTrue(ModAbilities.CRYOKINESIS_ID.equals(GiftedVillagers.gift(newDormant)), "A dormant gift survives");
            h.assertTrue(newGuardian != null && human(newGuardian), "A guardian is converted under the same UUID");
            h.assertTrue(GiftedMobs.isGifted(newGuardian) && GiftedMobs.isGood(newGuardian), "A woken gift survives");
            actor.discard();
            traveller.discard();
            newDormant.discard();
            newGuardian.discard();
            h.succeed();
        });
    }

    /** Everything that moves a villager does it through the brain's walk target, which MCA's own brain must obey. */
    @GameTest(template = "rewind_empty", timeoutTicks = 200, batch = "mca.walk")
    public static void theyWalkWhereTheirBrainIsSent(GameTestHelper h) {
        Villager v = Villagers.enter(h.getLevel(), plain(h, 2, 6));
        BlockPos to = h.absolutePos(new BlockPos(9, 2, 6));
        double before = v.position().distanceTo(Vec3.atBottomCenterOf(to));
        h.onEachTick(() -> { if (!v.isRemoved()) v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(to, 0.7F, 1)); });
        h.succeedWhen(() -> {
            h.assertTrue(v.position().distanceTo(Vec3.atBottomCenterOf(to)) < before - 2, "They walk toward where they were sent");
            v.discard();
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "mca.meltdown")
    public static void aMeltdownRunsOnTheirBody(GameTestHelper h) {
        Villager him = Villagers.enter(h.getLevel(), plain(h, 5, 5));
        try {
            h.assertTrue(VillagerMeltdown.start(him, 2000) != null && VillagerMeltdown.critical(him), "They can melt down");
            float health = him.getHealth();
            him.hurt(h.getLevel().damageSources().generic(), 3);
            h.assertTrue(him.getHealth() == health, "A melting villager can't simply be killed");
        } finally {
            VillagerMeltdown.cancel(him);
            him.discard();
        }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 80, batch = "mca.renegade")
    public static void anEvilGiftTurnsThemIntoARenegade(GameTestHelper h) {
        Villager v = Villagers.enter(h.getLevel(), plain(h, 4, 4));
        Villager neighbour = Villagers.enter(h.getLevel(), plain(h, 7, 4));
        GiftedVillagers.give(v, ModAbilities.PYROKINESIS_ID);
        UUID id = v.getUUID();
        h.assertTrue(Renegades.awaken(v, true) == v && Renegades.turning(v), "The gift begins to turn them");
        h.runAfterDelay(Renegades.TURN_TICKS + 3, () -> {
            Mob renegade = Renegades.turnedFrom(id);
            try {
                h.assertTrue(renegade instanceof Raider, "They become an illager: " + renegade);
                h.assertTrue(v.isRemoved(), "The villager is gone");
                Gift gift = Gift.read(renegade);
                h.assertTrue(gift != null && gift.evil() && gift.power().equals(ModAbilities.PYROKINESIS_ID), "They keep their power, turned");
                h.succeed();
            } finally {
                for (Entity e : new Entity[] {renegade, neighbour, v}) {
                    if (e == null) continue;
                    Renegades.cancel(e);
                    if (e instanceof Mob m && GiftedMobs.isGifted(m)) GiftedMobs.cleanse(m);
                    e.discard();
                }
            }
        });
    }

    /** A real handler through the real path: handing a guardian what they asked for, with MCA's menu in the way. */
    @GameTest(template = "rewind_empty", timeoutTicks = 20, batch = "mca.favour")
    public static void aFavourCanBeHandedOver(GameTestHelper h) {
        Villager v = Villagers.enter(h.getLevel(), plain(h, 5, 5));
        Renegades.guard(v, ModAbilities.HYDROKINESIS_ID);
        GiftedWork.pause(v, 200);
        FakePlayer player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "mca_favour"));
        try {
            Favours.Favour asked = Favours.ask(v, GiftedWork.Job.POST);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(asked.item(), asked.count()));
            var use = new PlayerInteractEvent.EntityInteractSpecific(player, InteractionHand.MAIN_HAND, v, Vec3.ZERO);
            NeoForge.EVENT_BUS.post(use);
            if (mca()) h.assertTrue(use.isCanceled(), "Handing it over keeps MCA's menu shut");
            else NeoForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, v));
            h.assertTrue(Favours.open(v) == null, "The favour is done");
        } finally {
            GiftedWork.forget(v);
            if (GiftedMobs.isGifted(v)) GiftedMobs.cleanse(v);
            v.discard();
        }
        h.succeed();
    }

    /** Listens for the plain use a player makes of one villager. */
    public static final class Use {
        private final UUID target;
        private final boolean take;
        int heard;

        Use(UUID target, boolean take) { this.target = target; this.take = take; }

        @SubscribeEvent public void on(PlayerInteractEvent.EntityInteract event) {
            if (!event.getTarget().getUUID().equals(target)) return;
            heard++;
            if (take) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }

    /** MCA answers a use before the game's plain one: our handlers still hear it, and may take it from MCA's menu. */
    @GameTest(template = "rewind_empty", timeoutTicks = 20, batch = "mca.use")
    public static void ourInteractionsStillReachMcaVillagers(GameTestHelper h) {
        if (!mca()) { h.succeed(); return; }
        Villager v = Villagers.enter(h.getLevel(), plain(h, 5, 5));
        FakePlayer player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "mca_use"));
        Use taken = new Use(v.getUUID(), true), passed = new Use(v.getUUID(), false);
        try {
            NeoForge.EVENT_BUS.register(taken);
            var use = new PlayerInteractEvent.EntityInteractSpecific(player, InteractionHand.MAIN_HAND, v, Vec3.ZERO);
            NeoForge.EVENT_BUS.post(use);
            h.assertTrue(taken.heard == 1, "A plain handler hears a use of MCA's human");
            h.assertTrue(use.isCanceled() && use.getCancellationResult() == InteractionResult.SUCCESS, "A handler that took it keeps MCA's menu shut");
            NeoForge.EVENT_BUS.unregister(taken);

            NeoForge.EVENT_BUS.register(passed);
            var open = new PlayerInteractEvent.EntityInteractSpecific(player, InteractionHand.MAIN_HAND, v, Vec3.ZERO);
            NeoForge.EVENT_BUS.post(open);
            h.assertTrue(passed.heard == 1, "Handlers hear it once");
            h.assertFalse(open.isCanceled(), "Nobody took it: MCA's own menu opens");

            player.setShiftKeyDown(true);
            var crouched = new PlayerInteractEvent.EntityInteractSpecific(player, InteractionHand.MAIN_HAND, v, Vec3.ZERO);
            NeoForge.EVENT_BUS.post(crouched);
            h.assertTrue(crouched.isCanceled() && crouched.getCancellationResult() == InteractionResult.PASS,
                    "Crouched, the use goes on to the plain one, where carrying is");
            h.assertTrue(passed.heard == 1, "...which the game fires itself, so it isn't heard twice");
        } finally {
            NeoForge.EVENT_BUS.unregister(passed);
            try { NeoForge.EVENT_BUS.unregister(taken); } catch (IllegalArgumentException alreadyGone) { /* unregistered above */ }
            v.discard();
        }
        h.succeed();
    }
}
