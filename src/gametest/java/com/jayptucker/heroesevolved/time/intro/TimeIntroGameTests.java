package com.jayptucker.heroesevolved.time.intro;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseResult;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class TimeIntroGameTests {
    @SuppressWarnings("unchecked")
    private static <T> T field(PlayerList list, String name) {
        try {
            Field field = PlayerList.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(list);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static ServerPlayer player(GameTestHelper helper, Vec3 pos, String name) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name)) {
            @Override public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
                super.teleportTo(level, x, y, z, yaw, pitch);
                // FakePlayer's no-op network handler does not apply server teleports to coordinates.
                absMoveTo(x, y, z, yaw, pitch);
            }
        };
        // Dimension transitions query negotiated channels even for the no-op fake connection.
        try {
            Connection connection = player.connection.getConnection();
            if (connection.channel() == null) {
                Field channel = Connection.class.getDeclaredField("channel");
                channel.setAccessible(true);
                channel.set(connection, new EmbeddedChannel());
            }
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        player.setPos(pos);
        player.setYRot(37);
        player.setXRot(-12);
        helper.getLevel().addNewPlayer(player);
        PlayerList list = helper.getLevel().getServer().getPlayerList();
        TimeIntroGameTests.<List<ServerPlayer>>field(list, "players").add(player);
        TimeIntroGameTests.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").put(player.getUUID(), player);
        return player;
    }

    private static void remove(ServerPlayer player) {
        com.jayptucker.heroesevolved.PowerOwnershipTestSupport.release(player);
        TimeIntroService.logout(player);
        PlayerList list = player.getServer().getPlayerList();
        TimeIntroGameTests.<List<ServerPlayer>>field(list, "players").remove(player);
        TimeIntroGameTests.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").remove(player.getUUID());
        player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }

    /** The first traveller folds to each player in turn, takes them along, and moves on with everyone gathered. */
    @GameTest(template = "rewind_empty", timeoutTicks = 200, batch = "TimeIntroGameTests.gatherEveryone")
    public static void gatherFetchesEachPlayerInTurn(GameTestHelper helper) {
        Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3)));
        // Without the power the finished group is dropped from the queue, so no vision starts under the other tests.
        ServerPlayer owner = player(helper, origin, "gather-owner");
        ServerPlayer first = player(helper, origin.add(4, 0, 0), "gather-first");
        ServerPlayer second = player(helper, origin.add(0, 0, 6), "gather-second");
        List<ServerPlayer> players = List.of(owner, first, second);
        try {
            TimeGather.begin(owner, List.of(first, second), List.of(owner.getUUID()));
            helper.assertTrue(TimeGather.gathering(owner) && TimeIntroService.isQueued(owner), "The traveller starts gathering");
        } catch (RuntimeException e) { players.forEach(TimeIntroGameTests::remove); throw e; }
        helper.runAfterDelay(10, () -> {
            try {
                helper.assertTrue(owner.distanceTo(first) < 2.5, "The first fold lands beside the nearest player");
                helper.assertTrue(TimeGather.gathering(first) && !TimeGather.gathering(second), "and takes only them");
            } catch (RuntimeException e) { players.forEach(TimeIntroGameTests::remove); throw e; }
        });
        helper.runAfterDelay(10 + TimeGather.HOP_TICKS, () -> {
            try {
                helper.assertTrue(owner.distanceTo(second) < 2.5 && first.distanceTo(second) < 3,
                        "The next fold brings everyone gathered so far to the next player");
                helper.assertTrue(TimeGather.gathering(second), "who is taken too");
            } catch (RuntimeException e) { players.forEach(TimeIntroGameTests::remove); throw e; }
        });
        helper.runAfterDelay(20 + TimeGather.HOP_TICKS * 2, () -> {
            try {
                helper.assertTrue(!TimeGather.gathering(owner), "With nobody left, the group goes to the vision");
                helper.succeed();
            } finally { players.forEach(TimeIntroGameTests::remove); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 4000, batch = "TimeIntroGameTests.fullSceneAudienceReturnAndRecovery")
    public static void fullSceneAudienceReturnAndRecovery(GameTestHelper helper) {
        ServerLevel source = helper.getLevel();
        ServerLevel city = source.getServer().getLevel(TimeIntroService.DIMENSION);
        helper.assertTrue(city != null, "City dimension must load from the datapack");
        boolean originalMobRule = source.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
        List<ServerPlayer> players = new ArrayList<>();
        boolean[] crowdReady = {false};
        boolean[] froze = {false};
        // Let the real server tick build the city before acquiring the power.
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(FractureCity.get(city).ready()
                && !TimeIntroService.sceneRunning() && !com.jayptucker.heroesevolved.story.AvenueService.running(), "Waiting for the city")).thenExecute(() -> {
            helper.assertTrue(FractureCity.get(city).ready(), "Staged city construction must finish");
            helper.assertTrue(source.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) == originalMobRule,
                    "Scene must not change the overworld's game rules");
            Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3)));
            ServerPlayer owner = player(helper, origin, "intro-owner");
            ServerPlayer edge = player(helper, origin.add(10, 0, 0), "intro-edge");
            ServerPlayer outside = player(helper, origin.add(10.01, 0, 0), "intro-outside");
            ServerPlayer leaving = player(helper, origin.add(0, 0, 4), "intro-leaving");
            players.addAll(List.of(owner, edge, outside, leaving));
            PlayerAbilityService.grantAbility(owner, ModAbilities.TIME_MANIPULATION_ID);
            // Distant forced chunks become entity-ticking asynchronously; verify the actual
            // audience once loaded, rather than assuming every chunk completes within twelve ticks.
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(
                    city.getEntities(EntityType.VILLAGER, TimeIntroService::isCurrentCrowd).size() == TimeIntroService.CROWD_SIZE,
                    "Scene must load the whole crowd on the streets"))
                    .thenExecute(() -> crowdReady[0] = true);
            // The twist: the vision's own time traveler (the owner's double, with their name) comes for Peter, and takes him.
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(TimeIntroService.sceneTicks() >= 608, "Waiting for the traveler"))
                    .thenExecute(() -> {
                        var cast = city.getEntities(com.jayptucker.heroesevolved.registry.ModEntities.STORY_ACTOR.get(), actor -> true);
                        helper.assertTrue(cast.stream().anyMatch(a -> a.role() == com.jayptucker.heroesevolved.story.actor.StoryActorEntity.Role.TRAVELER
                                        && a.doubleOf().filter(owner.getUUID()::equals).isPresent()
                                        && a.displayName().equals(owner.getGameProfile().getName())),
                                "The time traveler who comes for Peter is the owner's double: " + cast.stream().map(a -> a.displayName()).toList());
                    })
                    .thenWaitUntil(() -> helper.assertTrue(TimeIntroService.sceneTicks() >= 624, "Waiting for the fold"))
                    .thenExecute(() -> {
                        var cast = city.getEntities(com.jayptucker.heroesevolved.registry.ModEntities.STORY_ACTOR.get(), actor -> true);
                        helper.assertTrue(cast.size() == 1 && cast.getFirst().role() == com.jayptucker.heroesevolved.story.actor.StoryActorEntity.Role.TED,
                                "Before the light falls, Peter and the traveler are gone; Ted is alone: " + cast.stream().map(a -> a.displayName()).toList());
                    });
            // A few blocks from the wave, time stops for each visitor before they go home.
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(TimeIntroService.isFrozen(owner), "Waiting for the wave"))
                    .thenExecute(() -> {
                        double gap = IntroBlast.distance(owner.getX(), owner.getZ())
                                - IntroBlast.front(TimeIntroService.sceneTicks() - TimeIntroService.DETONATION_TICK);
                        helper.assertTrue(gap > 0 && gap <= IntroBlast.MARGIN + 2, "Time stops just ahead of the wave: " + gap);
                        helper.assertTrue(TimeIntroService.isCity(owner.level()), "Frozen visitors are still in the city");
                        froze[0] = true;
                    });
            helper.runAfterDelay(12, () -> {
                helper.assertTrue(TimeIntroService.isCity(owner.level()), "Newly granted power must start intro");
                helper.assertTrue(owner.position().distanceToSqr(TimeIntroService.ARRIVAL) < 20,
                        "Arrival must actually place the group on the city avenue");
                var actors = city.getEntities(com.jayptucker.heroesevolved.registry.ModEntities.STORY_ACTOR.get(), actor -> true);
                helper.assertTrue(actors.size() == 3, "Peter, Sylar and Ted must be at ground zero: " + actors.size());
                helper.assertTrue(actors.stream().filter(a -> a.role() != com.jayptucker.heroesevolved.story.actor.StoryActorEntity.Role.TED)
                        .allMatch(a -> a.getY() > IntroBlast.CENTER.y + 20), "...Peter and Sylar fighting high in the sky over the plaza");
                helper.assertTrue(IntroBlast.distance(owner.getX(), owner.getZ()) > 50,
                        "The vantage must be far enough to watch the flash from");
                helper.assertTrue(TimeIntroService.isCity(edge.level()), "Player exactly ten blocks away must join");
                helper.assertTrue(outside.level() == source, "Player beyond ten blocks must stay home");
                helper.assertTrue(TimeIntroService.isVisiting(leaving), "Audience must have a durable return ticket");
                helper.assertTrue(owner.getData(ModDataAttachments.TIME_INTRO_SEEN), "Owner must be marked seen");
                helper.assertTrue(!edge.getData(ModDataAttachments.TIME_INTRO_SEEN), "Companion without Time keeps own intro eligibility");
                helper.assertTrue(AbilityUseService.activateAssignedAction(owner, AbilitySlot.PRIMARY, false)
                        == AbilityUseResult.CANNOT_USE, "Players must not spend powers or teleport out during scene");
                float health = owner.getHealth();
                owner.hurt(city.damageSources().generic(), 10);
                helper.assertTrue(owner.getHealth() == health, "Scene must protect its visitors from damage");
                TimeIntroVisit saved = leaving.getData(ModDataAttachments.TIME_INTRO_VISIT);
                var json = TimeIntroVisit.CODEC.encodeStart(JsonOps.INSTANCE, saved).getOrThrow();
                helper.assertTrue(TimeIntroVisit.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().equals(saved),
                        "Recovery ticket must survive player serialization");
                TimeIntroService.logout(leaving);
                helper.assertTrue(leaving.level() == source && leaving.position().distanceToSqr(origin.add(0, 0, 4)) < 0.001,
                        "Disconnect must immediately return the departing visitor");
                // Simulate a reconnect after a crash that left a persisted city position.
                leaving.setData(ModDataAttachments.TIME_INTRO_VISIT, saved);
                leaving.teleportTo(city, 4, 64, 70, 180, 0);
                TimeIntroService.login(leaving);
                helper.assertTrue(leaving.level() == source && !TimeIntroService.isVisiting(leaving),
                        "Reconnect must recover an interrupted visitor");
            });
            helper.runAfterDelay(TimeIntroService.RETURN_TICK + 4, () -> {
                try {
                    helper.assertTrue(owner.level() == source && edge.level() == source, "Everyone must return at the end");
                    helper.assertTrue(owner.position().distanceToSqr(origin) < 0.001,
                            "Owner must return to original position");
                    helper.assertTrue(edge.position().distanceToSqr(origin) < 16 && edge.position().distanceToSqr(origin) > 0.5,
                            "Companions must land together beside the one whose power it is: " + edge.position());
                    helper.assertTrue(owner.getYRot() == 37 && owner.getXRot() == -12, "Return must restore orientation");
                    helper.assertTrue(!TimeIntroService.isVisiting(owner), "Completed visit must clear its ticket");
                    helper.assertTrue(city.getForcedChunks().isEmpty(), "Scene chunk tickets must be released");
                    helper.assertTrue(crowdReady[0], "The full crowd must have loaded during the scene");
                    helper.assertTrue(froze[0], "Time must have stopped for the owner before the wave reached them");
                    helper.assertTrue(city.getEntities(EntityType.VILLAGER, villager -> true).isEmpty(), "Crowd must be removed");
                    helper.assertTrue(city.getEntities(com.jayptucker.heroesevolved.registry.ModEntities.STORY_ACTOR.get(), actor -> true).isEmpty(),
                            "Peter and Ted must be removed");
                    PlayerAbilityService.removeAssignedAbility(owner);
                    PlayerAbilityService.grantAbility(owner, ModAbilities.TIME_MANIPULATION_ID);
                } catch (Throwable error) {
                    players.forEach(TimeIntroGameTests::remove);
                    throw error;
                }
                helper.runAfterDelay(3, () -> {
                    try {
                        helper.assertTrue(owner.level() == source, "Regranting the power must not repeat a seen intro");
                        helper.succeed();
                    } finally { players.forEach(TimeIntroGameTests::remove); }
                });
            });
        });
    }

    @GameTest(template = "rewind_empty", batch = "TimeIntroGameTests.cityDetailsAndEscapeTiming")
    public static void cityDetailsAndEscapeTiming(GameTestHelper helper) {
        ServerLevel city = helper.getLevel().getServer().getLevel(TimeIntroService.DIMENSION);
        Villager stale = EntityType.VILLAGER.create(city);
        stale.addTag("heroesevolved_time_intro_crowd");
        stale.moveTo(4, 64, 70, 0, 0);
        helper.assertTrue(!city.addFreshEntity(stale), "Saved crowd from an interrupted scene must not respawn");
        int ax = (int) Math.floor(TimeIntroService.ARRIVAL.x), az = (int) Math.floor(TimeIntroService.ARRIVAL.z);
        FractureCity.buildColumn(city, ax, az);
        helper.assertTrue(city.getBlockState(new BlockPos(ax, 63, az)).getBlock().getDescriptionId().endsWith("_concrete"),
                "Arrival must have a solid, painted road");
        helper.assertTrue(city.getBlockState(new BlockPos(ax, 64, az)).isAir() && city.getBlockState(new BlockPos(ax, 65, az)).isAir(),
                "Arrival must be unobstructed");
        // A downtown tower's north face: glass behind its piers, somewhere up its height.
        FractureCity.rebuild(city, 11, -21, 28, -20);
        boolean glass = false;
        for (int x = 11; x <= 28 && !glass; x++)
            for (int z = -21; z <= -20 && !glass; z++)
                for (int y = 70; y < 130 && !glass; y++)
                    glass = city.getBlockState(new BlockPos(x, y, z)).getSoundType() == net.minecraft.world.level.block.SoundType.GLASS;
        helper.assertTrue(glass, "Building facades must have actual windows");
        // Even a visitor at the north edge of the vantage sees the flash and the wave leave ground zero; nobody is left behind.
        double nearest = IntroBlast.distance(IntroBlast.CENTER.x, TimeIntroService.ROAM_Z0);
        helper.assertTrue(IntroBlast.arrival(nearest) >= 10, "The closest allowed visitor must see the wave coming: " + IntroBlast.arrival(nearest));
        double farthest = Math.max(IntroBlast.distance(TimeIntroService.ROAM_X0, TimeIntroService.ROAM_Z1),
                IntroBlast.distance(TimeIntroService.ROAM_X1, TimeIntroService.ROAM_Z1));
        helper.assertTrue(TimeIntroService.RETURN_TICK >= TimeIntroService.DETONATION_TICK + IntroBlast.arrival(farthest)
                + IntroBlast.STOPPED_TICKS, "The farthest visitor must be frozen, rewound and held before the scene ends");
        helper.assertTrue(TimeIntroService.DETONATION_TICK >= 600, "Visitors get half a minute to walk the city first");
        helper.assertTrue(FractureCity.WIDTH == 16 * 16, "City must occupy sixteen chunks across");
        helper.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "TimeIntroGameTests.cityUpgradesAndMixedNeighbourhoods")
    public static void cityUpgradesAndMixedNeighbourhoods(GameTestHelper helper) {
        ServerLevel city = helper.getLevel().getServer().getLevel(TimeIntroService.DIMENSION);
        // An old building on the viewing avenue must disappear completely when rebuilt.
        city.setBlock(new BlockPos(4, 121, 70), Blocks.BRICKS.defaultBlockState(), 2);
        FractureCity.buildColumn(city, 4, 70);
        helper.assertTrue(city.getBlockState(new BlockPos(4, 121, 70)).isAir(), "Rebuild must remove old roof remnants");
        // The avenue is the way to Bennet's door: nothing stands or is parked on it, end to end.
        FractureCity.rebuild(city, 0, -120, 7, 88);
        for (int x = 0; x <= 7; x++)
            for (int z = -120; z <= 88; z++)
                for (int y = 64; y <= 65; y++)
                    helper.assertTrue(city.getBlockState(new BlockPos(x, y, z)).isAir(), "The avenue must be clear at " + x + ", " + y + ", " + z
                            + ": " + city.getBlockState(new BlockPos(x, y, z)));
        // City hall's colonnade faces the avenue under its name.
        FractureCity.buildColumn(city, -5, -12);
        var sign = city.getBlockEntity(new BlockPos(-5, 74, -12));
        helper.assertTrue(sign instanceof net.minecraft.world.level.block.entity.SignBlockEntity hall
                && hall.getFrontText().getMessage(1, false).getString().equals("CITY OF ODESSA"), "City hall must be labelled");
        // A park replaces a building lot: shade trees and a fountain.
        FractureCity.buildColumn(city, 46, 46);
        helper.assertTrue(city.getBlockState(new BlockPos(46, 65, 46)).is(net.minecraft.tags.BlockTags.LOGS), "Park must have trees");
        FractureCity.buildColumn(city, 51, 51);
        helper.assertTrue(city.getBlockState(new BlockPos(51, 67, 51)).is(Blocks.WATER_CAULDRON), "Park fountain must have a contained upper basin");
        // The tallest tower downtown carries a spire high over the street.
        FractureCity.buildColumn(city, 51, -44);
        boolean spire = false;
        for (int y = 160; y < 200 && !spire; y++) spire = !city.getBlockState(new BlockPos(51, y, -44)).isAir();
        helper.assertTrue(spire, "The tallest tower must rise past y 160");
        helper.succeed();
    }
}
