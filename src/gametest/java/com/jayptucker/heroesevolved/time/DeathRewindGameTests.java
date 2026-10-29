package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Rewind can undo your own death in the fifteen seconds after it, inventory and all. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class DeathRewindGameTests {
    private static ServerPlayer mortal(GameTestHelper helper, int level) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "death-rewind-test")) {
            @Override public boolean isInvulnerableTo(DamageSource source) { return false; }
            // FakePlayers skip death entirely; a real player's death starts with this event.
            @Override public void die(DamageSource source) { net.neoforged.neoforge.common.CommonHooks.onLivingDeath(this, source); }
            // FakePlayers ignore connection teleports; move them directly.
            @Override public void teleportTo(net.minecraft.server.level.ServerLevel level, double x, double y, double z, float yaw, float pitch) {
                absMoveTo(x, y, z, yaw, pitch);
            }
        };
        try {
            // New players are briefly protected from harm after spawning; these tests need them mortal at once.
            var grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true); grace.setInt(player, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
        helper.getLevel().addNewPlayer(player);
        // Skip the first-awakening cinematic, which protects its player from dying.
        player.setData(com.jayptucker.heroesevolved.data.ModDataAttachments.POWER_INTRO_HISTORY,
                com.jayptucker.heroesevolved.intro.PowerIntroHistory.empty().with(ModAbilities.TIME_MANIPULATION_ID));
        PlayerAbilityService.replaceWithAbility(player, ModAbilities.TIME_MANIPULATION_ID);
        PlayerProgressionService.setLevel(player, level);
        TimeRewindBlocks.track(player);
        return player;
    }

    private static void remove(ServerPlayer player) {
        com.jayptucker.heroesevolved.PowerOwnershipTestSupport.release(player);
        TimeRewindService.forget(player);
        player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }

    @GameTest(template = "rewind_empty", batch = "DeathRewindGameTests.undoesARecentDeath")
    public static void rewindUndoesARecentDeathWithTheInventory(GameTestHelper helper) {
        ServerPlayer player = mortal(helper, 4);
        try {
            Vec3 start = player.position();
            player.getInventory().add(new ItemStack(Items.DIAMOND, 5));
            long now = helper.getLevel().getServer().overworld().getGameTime();
            // Fifteen seconds ago: standing here with five diamonds.
            TimeRewindService.record(player, now - 300);
            // Then a few steps away, and dead.
            player.setPos(start.add(3, 0, 0));
            TimeRewindService.record(player, now - 5);
            player.hurt(helper.getLevel().damageSources().generic(), 1000);
            helper.assertTrue(TimeRewindService.canRewindDeath(player), "A time manipulator's death can be rewound");
            // Death drops what they carried.
            ItemEntity drop = new ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(), player.getInventory().removeItem(0, 5));
            helper.getLevel().addFreshEntity(drop);
            player.getInventory().clearContent();
            // A new life, somewhere else.
            player.setHealth(player.getMaxHealth());
            player.setPos(start.add(0, 0, 4));
            TimeRewindService.respawned(player);
            helper.assertTrue(TimeRewindService.canRewind(player), "The history from before the death survives it");
            helper.assertTrue(TimeRewindService.rewind(player), "Rewind undoes the death");
            helper.assertTrue(player.position().distanceTo(start) < 0.5, "Back where they stood fifteen seconds ago: " + player.position());
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 5, "With the diamonds they carried then");
            helper.assertTrue(drop.isRemoved(), "And what they dropped when they died is gone again, so nothing is duplicated");
            helper.assertTrue(!TimeRewindService.canRewindDeath(player), "A death is undone only once");
            helper.succeed();
        } finally { remove(player); }
    }

    @GameTest(template = "rewind_empty", batch = "DeathRewindGameTests.onlyWithRewind")
    public static void beforeLevelFourDeathIsFinal(GameTestHelper helper) {
        ServerPlayer player = mortal(helper, 3);
        try {
            long now = helper.getLevel().getServer().overworld().getGameTime();
            TimeRewindService.record(player, now - 300);
            TimeRewindService.record(player, now - 5);
            player.hurt(helper.getLevel().damageSources().generic(), 1000);
            helper.assertTrue(!TimeRewindService.canRewindDeath(player), "Rewind unlocks at Level 4; before that a death is final");
            helper.succeed();
        } finally { remove(player); }
    }

    @GameTest(template = "rewind_empty", batch = "DeathRewindGameTests.unloadedDrops")
    public static void dropsInAnUnloadedChunkDoNotComeBack(GameTestHelper helper) {
        ServerPlayer player = mortal(helper, 4);
        try {
            player.getInventory().add(new ItemStack(Items.DIAMOND, 5));
            long now = helper.getLevel().getServer().overworld().getGameTime();
            TimeRewindService.record(player, now - 300);
            TimeRewindService.record(player, now - 5);
            player.hurt(helper.getLevel().damageSources().generic(), 1000);
            ItemEntity drop = new ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(), player.getInventory().removeItem(0, 5));
            helper.getLevel().addFreshEntity(drop);
            UUID id = drop.getUUID();
            // Respawning far away unloads the chunk the drop lies in.
            drop.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            player.setHealth(player.getMaxHealth());
            TimeRewindService.respawned(player);
            helper.assertTrue(TimeRewindService.rewind(player), "Rewind undoes the death");
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 5, "The diamonds are back in the inventory");
            // The chunk loads again, bringing the dropped stack back from disk.
            ItemEntity reloaded = new ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(), new ItemStack(Items.DIAMOND, 5));
            reloaded.setUUID(id);
            helper.assertTrue(!helper.getLevel().addFreshEntity(reloaded), "The un-made drop is refused when its chunk loads, so the diamonds aren't duplicated");
            helper.succeed();
        } finally { remove(player); }
    }

    @GameTest(template = "rewind_empty", batch = "DeathRewindGameTests.cost")
    public static void rewindIsExpensiveAndRecoversFasterAtLevelFive(GameTestHelper helper) {
        var rewind = new com.jayptucker.heroesevolved.ability.types.TimeManipulationAbility().actions()
                .get(com.jayptucker.heroesevolved.ability.AbilitySlot.QUATERNARY);
        helper.assertTrue(rewind.energyCost(4) >= 120, "Rewind costs most of a tank of stamina, got " + rewind.energyCost(4));
        helper.assertTrue(rewind.cooldownTicks(4) == 20 * 240, "Four minutes at Level 4, got " + rewind.cooldownTicks(4));
        helper.assertTrue(rewind.cooldownTicks(5) == 20 * 150, "Two and a half at Level 5, got " + rewind.cooldownTicks(5));
        helper.succeed();
    }
}
