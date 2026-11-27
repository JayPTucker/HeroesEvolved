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

    @GameTest(template = "rewind_empty", batch = "admin.events.queues", timeoutTicks = 40)
    public static void cancellingQueuedIncidentsReleasesTheirTickets(GameTestHelper helper) {
        var level = helper.getLevel();
        var at = helper.absolutePos(new BlockPos(6, 2, 6));
        var kinds = com.jayptucker.heroesevolved.village.VillageIncidents.Kind.values();
        try {
            for (var kind : kinds) com.jayptucker.heroesevolved.village.VillageIncidents.requestAt(level, at, kind);
            AdminEvents.cancel(level.getServer(), null, "meltdown");
            helper.assertTrue(com.jayptucker.heroesevolved.village.VillageIncidents.pending(
                    com.jayptucker.heroesevolved.village.VillageIncidents.Kind.MELTDOWN) == 0, "Meltdown cannot begin after cancellation");
            for (String id : new String[]{"gifts", "overload", "meteors"}) AdminEvents.cancel(level.getServer(), null, id);
            helper.assertTrue(com.jayptucker.heroesevolved.village.VillageIncidents.pending() == 0, "All queued event kinds are cancellable");
            helper.succeed();
        } finally { com.jayptucker.heroesevolved.village.VillageIncidents.cancel(kinds); }
    }

    @GameTest(template = "rewind_empty", batch = "admin.events.storms", timeoutTicks = 40)
    public static void everyOverloadCanStartAndCancelWithoutAnOutcome(GameTestHelper helper) {
        var p = player(helper);
        var v = net.minecraft.world.entity.EntityType.VILLAGER.create(helper.getLevel());
        v.setPos(p.position().add(2, 0, 0)); v.setNoAi(true);
        v.getPersistentData().putBoolean(com.jayptucker.heroesevolved.gifted.GiftedMobs.ROLLED, true);
        helper.getLevel().addFreshEntity(v);
        try {
            for (int variant = 0; variant < 3; variant++) {
                AdminEvents.start(helper.getLevel().getServer(), p, "overload", variant);
                helper.assertTrue(com.jayptucker.heroesevolved.village.overload.Overloads.critical(v), "Storm starts through the admin dispatcher: " + variant);
                AdminEvents.cancel(helper.getLevel().getServer(), p, "overload");
                helper.assertTrue(!com.jayptucker.heroesevolved.village.overload.Overloads.critical(v), "Storm is gone");
                helper.assertTrue(v.isAlive() && !v.getPersistentData().contains(com.jayptucker.heroesevolved.village.overload.Overloads.TAG), "Villager survives without resumable storm state");
            }
            helper.succeed();
        } finally { com.jayptucker.heroesevolved.village.overload.Overloads.cancel(v); v.discard(); remove(p); }
    }

    @GameTest(template = "rewind_empty", batch = "admin.events.naturalshower", timeoutTicks = 40)
    public static void cancellingNaturalStarfallKeepsItOffUntilAnotherNight(GameTestHelper helper) {
        var server = helper.getLevel().getServer(); var level = server.overworld(); long clock = level.getDayTime();
        try {
            int interval = com.jayptucker.heroesevolved.data.EclipseCalendar.intervalDays();
            level.setDayTime((interval - 2) * 24_000L + 14_000);
            helper.assertTrue(com.jayptucker.heroesevolved.meteor.Starfall.active(server), "The shower is naturally scheduled");
            AdminEvents.cancel(server, null, "starfall");
            helper.assertTrue(!com.jayptucker.heroesevolved.meteor.Starfall.active(server), "A natural shower stays cancelled");
            com.jayptucker.heroesevolved.meteor.Starfall.force(server, true);
            helper.assertTrue(com.jayptucker.heroesevolved.meteor.Starfall.active(server), "An operator can restart it immediately");
            AdminEvents.cancel(server, null, "starfall");
            level.setDayTime((2 * interval - 2) * 24_000L + 14_000);
            helper.assertTrue(com.jayptucker.heroesevolved.meteor.Starfall.active(server), "Cancellation does not disable future showers");
            helper.succeed();
        } finally { level.setDayTime(clock); com.jayptucker.heroesevolved.meteor.Starfall.force(server, false); }
    }

    @GameTest(template = "rewind_empty", batch = "admin.events.turning", timeoutTicks = 40)
    public static void cancellingAnAwakeningUnrootsTheTurningVillager(GameTestHelper helper) {
        var v = net.minecraft.world.entity.EntityType.VILLAGER.create(helper.getLevel());
        v.setPos(helper.absolutePos(new BlockPos(5, 2, 5)).getCenter());
        helper.getLevel().addFreshEntity(v);
        try {
            double speed = v.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            com.jayptucker.heroesevolved.gifted.Renegades.turn(v, ModAbilities.PYROKINESIS_ID, com.jayptucker.heroesevolved.gifted.Rarity.RARE);
            helper.assertTrue(com.jayptucker.heroesevolved.gifted.Renegades.turning(v), "Awakening is in flight");
            AdminEvents.cancel(helper.getLevel().getServer(), null, "gifts");
            helper.assertTrue(!com.jayptucker.heroesevolved.gifted.Renegades.turning(v), "Conversion is cancelled");
            helper.assertTrue(v.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) == speed, "Villager can move again");
            helper.succeed();
        } finally { com.jayptucker.heroesevolved.gifted.Renegades.cancelAll(); v.discard(); }
    }

    @GameTest(template = "rewind_empty", batch = "admin.events.permissions", timeoutTicks = 40)
    public static void untrustedClientsCannotStartOrCancelEvents(GameTestHelper helper) {
        var p = player(helper); var server = helper.getLevel().getServer();
        try {
            com.jayptucker.heroesevolved.meteor.Starfall.force(server, true);
            for (var action : new AdminActionPayload.Action[]{AdminActionPayload.Action.EVENT_CANCEL,
                    AdminActionPayload.Action.CANCEL_ALL_EVENTS, AdminActionPayload.Action.SET_ATTENTION, AdminActionPayload.Action.CANCEL_INTRO})
                AdminService.handle(p, new AdminActionPayload(action, p.getUUID(), "starfall", 100));
            helper.assertTrue(com.jayptucker.heroesevolved.meteor.Starfall.active(server), "Unauthorized cancellation has no effect");
            helper.assertTrue(com.jayptucker.heroesevolved.company.CompanyAttention.of(p) == 0, "Unauthorized debug changes have no effect");
            AdminService.handle(p, new AdminActionPayload(AdminActionPayload.Action.EVENT_START, p.getUUID(), "eclipse", 0));
            helper.assertTrue(!com.jayptucker.heroesevolved.events.EclipseService.isActive(server), "Unauthorized start has no effect");
            helper.assertTrue(AdminEvents.start(server, p, "overload", 999).equals("Unknown event or start option."), "Malformed variants are rejected");
            helper.succeed();
        } finally { com.jayptucker.heroesevolved.meteor.Starfall.force(server, false); remove(p); }
    }

    @GameTest(template = "rewind_empty", batch = "admin.events.remoteraid", timeoutTicks = 40)
    public static void globalCleanupFindsRaidsWithoutNearbyPlayers(GameTestHelper helper) {
        var p = player(helper);
        var raid = helper.getLevel().getRaids().createOrExtendRaid(p, helper.absolutePos(new BlockPos(5, 2, 5)));
        try {
            helper.assertTrue(raid != null, "Raid fixture starts");
            p.setPos(p.position().add(1000, 0, 0));
            helper.assertTrue(helper.getLevel().getRaidAt(p.blockPosition()) == null, "No player is standing in the raid");
            helper.assertTrue(AdminEvents.cancelAllRaids(helper.getLevel().getServer()) > 0 && raid.isStopped(), "Global cleanup stops the distant raid");
            helper.succeed();
        } finally { if (raid != null) raid.stop(); remove(p); }
    }

    @GameTest(template = "rewind_empty", batch = "admin.events.codec", timeoutTicks = 40)
    public static void catalogueAndFeedbackSurviveTheNetworkCodec(GameTestHelper helper) {
        var p = player(helper);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            var server = helper.getLevel().getServer();
            var state = new com.jayptucker.heroesevolved.network.AdminStatePayload(false, false, 20,
                    java.util.List.of(ModAbilities.SUPERSPEED_ID.toString()), java.util.List.of(),
                    com.jayptucker.heroesevolved.network.AdminStatePayload.World.NONE,
                    AdminEvents.describe(server, p), AdminEvents.diagnostics(server, p), "Cancelled event; queued starts released.");
            com.jayptucker.heroesevolved.network.AdminStatePayload.STREAM_CODEC.encode(buffer, state);
            helper.assertTrue(state.equals(com.jayptucker.heroesevolved.network.AdminStatePayload.STREAM_CODEC.decode(buffer)), "The complete catalogue, scopes, diagnostics and result round-trip");
            helper.succeed();
        } finally { buffer.release(); remove(p); }
    }

    @GameTest(template = "rewind_empty", batch = "admin.events.vision", timeoutTicks = 40)
    public static void replayedVisionCanBeCancelledBeforeItStartsWithoutGrantingAPower(GameTestHelper helper) {
        var p = player(helper); var server = helper.getLevel().getServer();
        try {
            String result = AdminEvents.start(server, p, "vision", 0);
            helper.assertTrue(result.startsWith("Queued") && com.jayptucker.heroesevolved.time.intro.TimeIntroService.isQueued(p), "Operator can queue the vision without a power");
            helper.assertTrue(!PlayerAbilityService.getData(p).hasAssignedPower(), "Replay never grants or replaces the player's power");
            AdminEvents.cancel(server, p, "vision");
            helper.assertTrue(!com.jayptucker.heroesevolved.time.intro.TimeIntroService.isQueued(p), "Cancelled vision cannot start later");
            helper.succeed();
        } finally { com.jayptucker.heroesevolved.time.intro.TimeIntroService.cancelVision(server); remove(p); }
    }

    @GameTest(template = "rewind_empty", batch = "admin.events.selectedtarget", timeoutTicks = 40)
    public static void authorizedRequestsUseTheSelectedPlayerAndRejectAnOfflineTarget(GameTestHelper helper) {
        var admin = player(helper); var target = player(helper); var server = helper.getLevel().getServer();
        var list = server.getPlayerList();
        java.util.Map<UUID, net.minecraft.server.level.ServerPlayer> lookup;
        try {
            var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID"); field.setAccessible(true);
            @SuppressWarnings("unchecked") var index = (java.util.Map<UUID, net.minecraft.server.level.ServerPlayer>) field.get(list);
            lookup = index;
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        list.getOps().add(new net.minecraft.server.players.ServerOpListEntry(admin.getGameProfile(), 4, false));
        lookup.put(target.getUUID(), target);
        try {
            helper.assertTrue(AdminService.isAdmin(admin), "Operator fixture has permission");
            AdminService.handle(admin, new AdminActionPayload(AdminActionPayload.Action.SET_ATTENTION, target.getUUID(), "", 500));
            helper.assertTrue(com.jayptucker.heroesevolved.company.CompanyAttention.of(target) == 100
                    && com.jayptucker.heroesevolved.company.CompanyAttention.of(admin) == 0, "Debug controls affect the selected player and clamp input");
            lookup.remove(target.getUUID());
            AdminService.handle(admin, new AdminActionPayload(AdminActionPayload.Action.TOGGLE_GOD_MODE, target.getUUID(), "", 0));
            helper.assertTrue(!GodModeService.enabled(target) && !GodModeService.enabled(admin), "Offline selection never falls back to the operator");
            helper.succeed();
        } finally {
            lookup.remove(target.getUUID()); list.getOps().remove(admin.getGameProfile());
            com.jayptucker.heroesevolved.company.CompanyAttention.forget(target);
            remove(admin); remove(target);
        }
    }

    /** The console's Ultimates row and {@code /heroesevolved ultimate}: cast a class's Ultimate, stop it, ready it again. */
    @GameTest(template = "rewind_empty", batch = "admin.ultimate", timeoutTicks = 40)
    public static void ultimateRowCastsStopsAndReadiesAClassUltimate(GameTestHelper helper) {
        var player = player(helper);
        var server = helper.getLevel().getServer();
        var foe = net.minecraft.world.entity.EntityType.PILLAGER.create(helper.getLevel());
        try {
            var power = ModAbilities.SUPERSPEED_ID;
            player.setData(com.jayptucker.heroesevolved.data.ModDataAttachments.PLAYER_ABILITIES,
                    com.jayptucker.heroesevolved.ability.data.PlayerAbilityData.empty().assignDormant(power).unlock(power));
            com.jayptucker.heroesevolved.progression.PlayerProgressionService.setLevel(player, 5);
            helper.assertTrue(server.getCommands().getDispatcher().getRoot().getChild("heroesevolved").getChild("ultimate") != null,
                    "/heroesevolved ultimate is registered for the Commands tab");
            helper.assertTrue(com.jayptucker.heroesevolved.combat.UltimateCommands.cast(player, null).contains("no class"),
                    "With no class, a cast asks for a mechanic");
            helper.assertTrue(com.jayptucker.heroesevolved.skills.SkillTreeService.purchase(player, "superspeed_lightning_flurry"), "Offense chosen");
            foe.setNoAi(true);
            foe.setPersistenceRequired();
            foe.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
            foe.setHealth(200);
            foe.moveTo(player.getX(), player.getY(), player.getZ() + 3.5, 180, 0);
            helper.getLevel().addFreshEntity(foe);
            player.setYRot(0);
            player.setXRot(0);
            player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, foe.getBoundingBox().getCenter());
            var row = AdminEvents.describe(server, player).stream().filter(e -> e.id().equals("ultimate")).findFirst().orElseThrow();
            helper.assertTrue(row.canStart() && !row.active(), "The row can cast for a selected player, and nothing is playing yet");
            String cast = AdminEvents.start(server, player, "ultimate", 0);
            helper.assertTrue(cast.startsWith("Cast lightning_flurry") && com.jayptucker.heroesevolved.combat.Ultimates.running(player),
                    "Cast plays the player's class Ultimate: " + cast);
            helper.assertTrue(AdminEvents.describe(server, player).stream().anyMatch(e -> e.id().equals("ultimate") && e.active()),
                    "The row shows it playing");
            String stop = AdminEvents.cancel(server, player, "ultimate");
            helper.assertTrue(stop.startsWith("Stopped") && !com.jayptucker.heroesevolved.combat.Ultimates.running(player),
                    "Cancel stops it: " + stop);
            helper.assertTrue(foe.isNoAi(), "The foe keeps the mind it had (this one never had AI)");
            com.jayptucker.heroesevolved.cooldown.CooldownService.startCooldown(player,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("heroesevolved", "superspeed_lightning_flurry"), 3000);
            helper.assertTrue(AdminEvents.start(server, player, "ultimate", 1).contains("ready again")
                    && !com.jayptucker.heroesevolved.cooldown.CooldownService.isOnCooldown(player,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("heroesevolved", "superspeed_lightning_flurry")),
                    "Ready it again clears the wait");
            helper.succeed();
        } finally {
            com.jayptucker.heroesevolved.combat.Ultimates.stop(player);
            foe.discard();
            remove(player);
        }
    }
}
