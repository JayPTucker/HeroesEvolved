package com.jayptucker.heroesevolved.admin;

import com.jayptucker.heroesevolved.PowerOwnershipTestSupport;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.intro.PowerIntroService;
import com.jayptucker.heroesevolved.network.AdminActionPayload;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class AdminGameTests {
    private static FakePlayer player(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "admin-test")) {
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) { return false; }
        };
        player.setPos(helper.absolutePos(new BlockPos(3, 2, 3)).getCenter());
        helper.getLevel().addNewPlayer(player);
        try {
            var channel = net.minecraft.network.Connection.class.getDeclaredField("channel");
            channel.setAccessible(true);
            if (player.connection.getConnection().channel() == null)
                channel.set(player.connection.getConnection(), new io.netty.channel.embedded.EmbeddedChannel());
            var grace = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true); grace.setInt(player, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        return player;
    }
    private static void remove(FakePlayer player) {
        GodModeService.set(player, false);
        PowerIntroService.forget(player);
        PowerOwnershipTestSupport.release(player);
        player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }

    @GameTest(template = "rewind_empty", batch = "admin.protection", timeoutTicks = 40)
    public static void godModeProtectsAndCanBeDisabled(GameTestHelper helper) {
        var player = player(helper);
        try {
            GodModeService.set(player, true);
            float health = player.getHealth();
            player.hurt(player.damageSources().generic(), 1000);
            player.hurt(player.damageSources().fellOutOfWorld(), 1000);
            helper.assertTrue(player.isAlive() && player.getHealth() == health, "God mode blocks normal and void damage");
            GodModeService.set(player, false);
            player.invulnerableTime = 0;
            player.hurt(player.damageSources().generic(), 2);
            helper.assertTrue(player.getHealth() < health, "Disabling God mode restores normal damage");
            helper.succeed();
        } finally { remove(player); }
    }

    @GameTest(template = "rewind_empty", batch = "admin.dormant", timeoutTicks = 40)
    public static void dormantReplacementStaysAsleepAndRespectsOwnership(GameTestHelper helper) {
        var first = player(helper); var second = player(helper);
        try {
            var power = ModAbilities.SUPERSPEED_ID;
            helper.assertTrue(PlayerAbilityService.replaceWithDormantAbility(first, power), "Assign dormant power");
            helper.assertTrue(!PlayerAbilityService.getAbility(first, power).orElseThrow().isUnlocked(), "Assignment is unawakened");
            helper.assertTrue(!PowerIntroService.isActive(first), "Dormant assignment starts no awakening intro");
            helper.assertTrue(!PlayerAbilityService.replaceWithDormantAbility(second, power), "Another owner cannot take a dormant power");
            helper.assertTrue(PlayerAbilityService.unlockAbility(first, power), "Dormant power can awaken normally");
            PowerIntroService.forget(first);
            helper.assertTrue(PlayerAbilityService.replaceWithDormantAbility(first, power), "An awakened power can be made dormant");
            helper.assertTrue(!PlayerAbilityService.getAbility(first, power).orElseThrow().isUnlocked(), "It is dormant again");
            helper.assertTrue(!PowerIntroService.isActive(first), "Replacement cancels the old intro");
            helper.succeed();
        } finally { remove(first); remove(second); }
    }

    @GameTest(template = "rewind_empty", batch = "admin.permissions", timeoutTicks = 40)
    public static void unauthorizedGodModeRequestIsRejected(GameTestHelper helper) {
        var player = player(helper);
        try {
            helper.assertTrue(!AdminService.isAdmin(player), "Fixture is not an operator");
            AdminService.handle(player, AdminActionPayload.of(AdminActionPayload.Action.TOGGLE_GOD_MODE, player.getUUID()));
            helper.assertTrue(!GodModeService.enabled(player), "Clients cannot grant themselves God mode");
            helper.succeed();
        } finally { remove(player); }
    }
}
