package com.jayptucker.heroesevolved.mimicry;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;

/** Empathic Mimicry's three phases: near (one set), kept by touch (two), and remembered whole (three). */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class MimicryGameTests {
    private static ServerPlayer player(GameTestHelper h, ResourceLocation power, int x) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "mimicry-" + x));
        try {
            Connection connection = p.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x, 2, 6))));
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        h.getLevel().addNewPlayer(p);
        return p;
    }

    private static ServerPlayer mimic(GameTestHelper h, int level) {
        ServerPlayer p = player(h, ModAbilities.MIMICRY_ID, 2);
        PlayerProgressionService.setPowerLevel(p, level);
        return p;
    }

    private static void remove(ServerPlayer... players) {
        for (ServerPlayer p : players) {
            MimicryService.forget(p);
            p.setData(ModDataAttachments.MIMIC_MEMORY, MimicryService.Memory.empty());
            p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
        }
    }

    private static void attune(ServerPlayer mimic) {
        for (int i = 0; i < MimicryService.ATTUNE_TICKS / 5; i++) MimicryService.attune(mimic);
    }

    @GameTest(template = "rewind_empty", batch = "mimicry.near")
    public static void atLowLevelsYouCarryWhoeverYouWereLastNear(GameTestHelper h) {
        ServerPlayer mimic = mimic(h, 3), pyro = player(h, ModAbilities.PYROKINESIS_ID, 6), flyer = player(h, ModAbilities.FLIGHT_ID, 10);
        try {
            MimicryService.attune(mimic);
            h.assertTrue(MimicryService.memory(mimic).current() == null, "Attuning takes a moment");
            attune(mimic);
            h.assertTrue(ModAbilities.PYROKINESIS_ID.equals(MimicryService.memory(mimic).current()), "Two seconds near a pyrokinetic gives you fire");
            pyro.setPos(pyro.position().add(0, 0, 40));
            attune(mimic);
            h.assertTrue(ModAbilities.FLIGHT_ID.equals(MimicryService.memory(mimic).current())
                    && MimicryService.memory(mimic).slots().size() == 1, "The next person you're near replaces it: one set at a time");
            pyro.setPos(mimic.position().add(1, 0, 0));
            h.assertTrue(MimicryService.touch(mimic, pyro) && ModAbilities.PYROKINESIS_ID.equals(MimicryService.memory(mimic).current())
                    && MimicryService.memory(mimic).kept().isEmpty(), "Below Level 6 a touch attunes at once, but keeps nothing");
            h.succeed();
        } finally { remove(mimic, pyro, flyer); }
    }

    @GameTest(template = "rewind_empty", batch = "mimicry.kept")
    public static void atMidLevelsATouchKeepsASetAndTheOldestMakesRoom(GameTestHelper h) {
        ServerPlayer mimic = mimic(h, 8), pyro = player(h, ModAbilities.PYROKINESIS_ID, 3),
                flyer = player(h, ModAbilities.FLIGHT_ID, 4), cryo = player(h, ModAbilities.CRYOKINESIS_ID, 5);
        try {
            h.assertTrue(MimicryService.keepCapacity(mimic) == 2, "Levels 6 to 14 keep two sets");
            MimicryService.touch(mimic, pyro);
            MimicryService.touch(mimic, flyer);
            h.assertTrue(MimicryService.memory(mimic).kept().size() == 2
                    && ModAbilities.FLIGHT_ID.equals(MimicryService.memory(mimic).current()), "Each touch keeps the set and channels it");
            MimicryService.touch(mimic, cryo);
            var kept = MimicryService.memory(mimic).kept();
            h.assertTrue(kept.size() == 2 && kept.getFirst().power().equals(ModAbilities.FLIGHT_ID)
                    && kept.get(1).power().equals(ModAbilities.CRYOKINESIS_ID), "A third touch lets the oldest go");
            h.assertTrue(kept.getFirst().name().equals(flyer.getGameProfile().getName()), "A kept set remembers who it came from");
            attune(mimic);
            h.assertTrue(MimicryService.memory(mimic).near().isPresent()
                    && ModAbilities.CRYOKINESIS_ID.equals(MimicryService.memory(mimic).current()),
                    "Being near someone fills the near set without pulling you off the kept one you chose");
            int flight = MimicryService.memory(mimic).slots().indexOf(MimicryService.memory(mimic).kept().getFirst());
            h.assertTrue(MimicryService.choose(mimic, flight) && ModAbilities.FLIGHT_ID.equals(MimicryService.memory(mimic).current()),
                    "The memory wheel channels any set you carry");
            h.assertTrue(MimicryService.forgetSlot(mimic, flight) && MimicryService.memory(mimic).kept().size() == 1,
                    "A kept set can be let go");
            h.succeed();
        } finally { remove(mimic, pyro, flyer, cryo); }
    }

    @GameTest(template = "rewind_empty", batch = "mimicry.remembered")
    public static void atHighLevelsThreeWholeSetsAreRemembered(GameTestHelper h) {
        ServerPlayer mimic = mimic(h, 15), pyro = player(h, ModAbilities.PYROKINESIS_ID, 4);
        try {
            h.assertTrue(MimicryService.keepCapacity(mimic) == 3, "Level 15 keeps three sets");
            PlayerProgressionService.setPowerLevel(mimic, 20);
            h.assertTrue(MimicryService.keepCapacity(mimic) == 4, "Evolved keeps four");
            MimicryService.touch(mimic, pyro);
            var mimicry = AbilityRegistry.ABILITIES.get(ModAbilities.MIMICRY_ID);
            var echoFive = mimicry.actions().get(AbilitySlot.QUINARY);
            var pyroFive = AbilityRegistry.ABILITIES.get(ModAbilities.PYROKINESIS_ID).actions().get(AbilitySlot.QUINARY);
            h.assertTrue(echoFive.definition().isUnlockedAt(5), "Echo V opens at the fifth tier: the whole set");
            h.assertTrue(pyroFive.definition().id().equals(MimicryService.shown(mimic, echoFive).id()), "Menus show the copied ability, not 'Echo V'");
            h.succeed();
        } finally { remove(mimic, pyro); }
    }

    @GameTest(template = "rewind_empty", batch = "mimicry.villager")
    public static void aGiftedVillagersPowerCanBeBorrowed(GameTestHelper h) {
        ServerPlayer mimic = mimic(h, 6);
        Villager villager = EntityType.VILLAGER.create(h.getLevel());
        villager.setNoAi(true);
        villager.moveTo(mimic.getX() + 2, mimic.getY(), mimic.getZ());
        h.getLevel().addFreshEntity(villager);
        try {
            GiftedVillagers.give(villager, null);
            h.assertTrue(MimicryService.imprintOf(mimic, villager) == null, "An ordinary villager has nothing to give");
            GiftedVillagers.give(villager, ModAbilities.CRYOKINESIS_ID);
            var imprint = MimicryService.imprintOf(mimic, villager);
            h.assertTrue(imprint != null && imprint.power().equals(ModAbilities.CRYOKINESIS_ID) && imprint.kind().equals("villager"),
                    "A gifted villager carries a power a mimic can feel");
            h.assertTrue(MimicryService.touch(mimic, villager) && ModAbilities.CRYOKINESIS_ID.equals(MimicryService.memory(mimic).current()),
                    "And a touch keeps it");
            h.succeed();
        } finally { villager.discard(); remove(mimic); }
    }

    @GameTest(template = "rewind_empty", batch = "mimicry.legacy")
    public static void olderSavesLoadTheirMemoriesAsKeptSets(GameTestHelper h) {
        var json = JsonParser.parseString("{\"powers\":[\"heroesevolved:flight\",\"heroesevolved:pyrokinesis\"],\"active\":1}");
        var memory = MimicryService.Memory.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        h.assertTrue(memory.kept().size() == 2 && memory.near().isEmpty() && ModAbilities.PYROKINESIS_ID.equals(memory.current()),
                "An older memory loads as kept sets, still channeling the same power");
        var again = MimicryService.Memory.CODEC.parse(JsonOps.INSTANCE,
                MimicryService.Memory.CODEC.encodeStart(JsonOps.INSTANCE, memory).getOrThrow()).getOrThrow();
        h.assertTrue(again.equals(memory), "The new memory round-trips");
        h.succeed();
    }
}
