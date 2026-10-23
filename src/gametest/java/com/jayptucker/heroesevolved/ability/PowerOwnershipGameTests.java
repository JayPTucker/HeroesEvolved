package com.jayptucker.heroesevolved.ability;

import com.jayptucker.heroesevolved.PowerOwnershipTestSupport;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.data.PowerOwnershipSavedData;
import com.jayptucker.heroesevolved.intro.PowerIntroService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Files;
import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class PowerOwnershipGameTests {
    private static ServerPlayer player(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "unique-power"));
        player.setPos(helper.absolutePos(new BlockPos(3, 2, 3)).getCenter());
        helper.getLevel().addNewPlayer(player); return player;
    }
    private static void remove(ServerPlayer player) {
        PowerIntroService.forget(player); PowerOwnershipTestSupport.release(player);
        player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }
    @GameTest(template = "rewind_empty", batch = "ownership.exclusive", timeoutTicks = 50)
    public static void dormantGrantReplacementAndRemovalCannotDuplicatePower(GameTestHelper helper) {
        ServerPlayer first = player(helper), second = player(helper);
        try {
            helper.assertTrue(PlayerAbilityService.assignDormantAbility(first, ModAbilities.RADIATION_ID), "First dormant assignment must reserve the role");
            helper.assertTrue(!PlayerAbilityService.grantAbility(second, ModAbilities.RADIATION_ID), "Another player cannot grant the same power");
            helper.assertTrue(PlayerAbilityService.assignDormantAbility(second, ModAbilities.FLIGHT_ID), "Another unclaimed power must remain available");
            helper.assertTrue(!PlayerAbilityService.replaceWithAbility(second, ModAbilities.RADIATION_ID), "Admin replacement must not steal another role");
            helper.assertTrue(PlayerAbilityService.getData(second).hasAbility(ModAbilities.FLIGHT_ID), "Rejected replacement must preserve the old power");
            helper.assertTrue(PlayerAbilityService.unlockAbility(first, ModAbilities.RADIATION_ID), "The reserved owner must be able to awaken");
            PlayerAbilityService.removeAssignedAbility(first);
            helper.assertTrue(!PlayerAbilityService.availableTo(second, ModAbilities.RADIATION_ID), "Removing or disconnecting must not transfer a storyline role");
            helper.assertTrue(PlayerAbilityService.grantAbility(first, ModAbilities.RADIATION_ID), "The original owner can regain their role");
            var data = PowerOwnershipSavedData.get(first.getServer());
            var decoded = PowerOwnershipSavedData.load(data.save(new CompoundTag(), first.getServer().registryAccess()), first.getServer().registryAccess());
            helper.assertTrue(decoded.owner(ModAbilities.RADIATION_ID).orElseThrow().equals(first.getUUID()), "Ownership must survive saved-data serialization");
            helper.succeed();
        } finally { remove(first); remove(second); }
    }
    @GameTest(template = "rewind_empty", batch = "ownership.offline", timeoutTicks = 50)
    public static void existingOfflinePlayerReservesPowerBeforeAnyoneCanClaimIt(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        UUID offlineOwner = UUID.randomUUID();
        var directory = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        Files.createDirectories(directory);
        var path = directory.resolve(offlineOwner + ".dat");
        CompoundTag root = new CompoundTag(), attachments = new CompoundTag();
        attachments.put("heroesevolved:player_abilities", PlayerAbilityData.CODEC.encodeStart(NbtOps.INSTANCE,
                PlayerAbilityData.empty().assignDormant(ModAbilities.RADIATION_ID)).getOrThrow());
        root.put("neoforge:attachments", attachments);
        NbtIo.writeCompressed(root, path);
        try {
            var migrated = new PowerOwnershipSavedData();
            migrated.importLegacyPlayer(offlineOwner, NbtIo.readCompressed(path, NbtAccounter.create(16 * 1024 * 1024)));
            helper.assertTrue(migrated.owner(ModAbilities.RADIATION_ID).orElseThrow().equals(offlineOwner), "Legacy offline player data must reserve Radiation before new assignments");
            helper.assertTrue(!migrated.claim(ModAbilities.RADIATION_ID, UUID.randomUUID()), "Offline ownership must block a new claimant");
            helper.succeed();
        } finally { Files.deleteIfExists(path); }
    }
    @GameTest(template = "rewind_empty", batch = "ownership.exhaustion", timeoutTicks = 50)
    public static void claimedRosterHasNoDuplicateFallback(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        var server = player.getServer();
        var original = PowerOwnershipSavedData.get(server);
        var before = PowerOwnershipSavedData.load(original.save(new CompoundTag(), server.registryAccess()), server.registryAccess());
        try {
            for (var power : AbilityRegistry.ABILITIES.keySet()) original.claim(power, UUID.randomUUID());
            helper.assertTrue(AbilityRegistry.ABILITIES.keySet().stream().noneMatch(power -> PlayerAbilityService.availableTo(player, power)),
                    "An exhausted roster must not expose duplicate powers as available");
            helper.assertTrue(!PlayerAbilityService.assignDormantAbility(player, ModAbilities.RADIATION_ID), "The final player must remain unassigned when all roles are taken");
            helper.succeed();
        } finally {
            // This fixture deliberately claims every role; discard only its isolated registry afterward.
            PowerOwnershipTestSupport.replace(server, before);
            remove(player);
        }
    }
}
