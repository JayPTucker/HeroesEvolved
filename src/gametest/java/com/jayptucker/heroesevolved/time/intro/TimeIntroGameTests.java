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
        TimeIntroService.logout(player);
        PlayerList list = player.getServer().getPlayerList();
        TimeIntroGameTests.<List<ServerPlayer>>field(list, "players").remove(player);
        TimeIntroGameTests.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").remove(player.getUUID());
        player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 650)
    public static void fullSceneAudienceReturnAndRecovery(GameTestHelper helper) {
        ServerLevel source = helper.getLevel();
        ServerLevel city = source.getServer().getLevel(TimeIntroService.DIMENSION);
        helper.assertTrue(city != null, "City dimension must load from the datapack");
        boolean originalMobRule = source.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
        List<ServerPlayer> players = new ArrayList<>();
        boolean[] crowdReady = {false};
        // Let the real server tick build the city before acquiring the power.
        helper.runAfterDelay(160, () -> {
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
                    city.getEntities(EntityType.VILLAGER, TimeIntroService::isCurrentCrowd).size() == 24,
                    "Scene must load the reduced 24-person crowd"))
                    .thenExecute(() -> crowdReady[0] = true);
            helper.runAfterDelay(12, () -> {
                helper.assertTrue(TimeIntroService.isCity(owner.level()), "Newly granted power must start intro");
                helper.assertTrue(owner.position().distanceToSqr(TimeIntroService.ARRIVAL) < 20,
                        "Arrival must actually place the group on the city avenue");
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
                    helper.assertTrue(edge.position().distanceToSqr(origin.add(10, 0, 0)) < 0.001,
                            "Companions must return to their own position");
                    helper.assertTrue(owner.getYRot() == 37 && owner.getXRot() == -12, "Return must restore orientation");
                    helper.assertTrue(!TimeIntroService.isVisiting(owner), "Completed visit must clear its ticket");
                    helper.assertTrue(city.getForcedChunks().isEmpty(), "Scene chunk tickets must be released");
                    helper.assertTrue(crowdReady[0], "The full reduced crowd must have loaded during the scene");
                    helper.assertTrue(city.getEntities(EntityType.VILLAGER, villager -> true).isEmpty(), "Crowd must be removed");
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

    @GameTest(template = "rewind_empty")
    public static void cityDetailsAndEscapeTiming(GameTestHelper helper) {
        ServerLevel city = helper.getLevel().getServer().getLevel(TimeIntroService.DIMENSION);
        Villager stale = EntityType.VILLAGER.create(city);
        stale.addTag("heroesevolved_time_intro_crowd");
        stale.moveTo(4, 64, 70, 0, 0);
        helper.assertTrue(!city.addFreshEntity(stale), "Saved crowd from an interrupted scene must not respawn");
        FractureCity.buildColumn(city, 4, 70);
        helper.assertTrue(city.getBlockState(new BlockPos(4, 63, 70)).is(Blocks.GRAY_CONCRETE),
                "Arrival must have a solid, clear road");
        helper.assertTrue(city.getBlockState(new BlockPos(4, 64, 70)).isAir(), "Arrival feet must be unobstructed");
        FractureCity.buildColumn(city, 12, 14);
        helper.assertTrue(city.getBlockState(new BlockPos(12, 66, 14)).is(Blocks.LIGHT_BLUE_STAINED_GLASS)
                        || city.getBlockState(new BlockPos(12, 66, 14)).is(Blocks.OCHRE_FROGLIGHT),
                "Building facades must have actual windows");
        double seconds = (TimeIntroService.RETURN_TICK - TimeIntroService.DETONATION_TICK) / 20.0;
        double dustFront = 8 + seconds * 18 + 5;
        helper.assertTrue(dustFront < 58 - TimeIntroService.BLAST.z,
                "Closest allowed player must escape before dust reaches them");
        helper.assertTrue(FractureCity.WIDTH == 12 * 16, "City must occupy twelve chunks across");
        helper.succeed();
    }

    @GameTest(template = "rewind_empty")
    public static void cityUpgradesAndMixedNeighbourhoods(GameTestHelper helper) {
        ServerLevel city = helper.getLevel().getServer().getLevel(TimeIntroService.DIMENSION);
        // An old building on the viewing avenue must disappear completely when rebuilt.
        city.setBlock(new BlockPos(4, 121, 70), Blocks.BRICKS.defaultBlockState(), 2);
        FractureCity.buildColumn(city, 4, 70);
        helper.assertTrue(city.getBlockState(new BlockPos(4, 121, 70)).isAir(), "Rebuild must remove old roof remnants");
        // Brick apartment with a labelled shopfront and a genuine two-block entrance.
        FractureCity.buildColumn(city, -76, -53);
        var sign = city.getBlockEntity(new BlockPos(-76, 68, -53));
        helper.assertTrue(sign instanceof net.minecraft.world.level.block.entity.SignBlockEntity,
                "Shopfront must have a real sign block entity");
        helper.assertTrue(!((net.minecraft.world.level.block.entity.SignBlockEntity) sign)
                .getFrontText().getMessage(1, false).getString().isBlank(), "Shop sign must be readable");
        FractureCity.buildColumn(city, -76, -52);
        helper.assertTrue(city.getBlockState(new BlockPos(-76, 64, -52)).isAir()
                && city.getBlockState(new BlockPos(-76, 65, -52)).isAir(), "Apartment doorway must be usable");
        // A park replaces a building lot; trees and a contained pool add another streetscape.
        FractureCity.buildColumn(city, -82, 78);
        helper.assertTrue(city.getBlockState(new BlockPos(-82, 64, 78)).is(Blocks.OAK_LOG), "Park must have trees");
        FractureCity.buildColumn(city, -76, 84);
        helper.assertTrue(city.getBlockState(new BlockPos(-76, 66, 84)).is(Blocks.WATER_CAULDRON),
                "Park fountain must have a contained upper basin");
        // The office podium and tower above it have different footprints.
        FractureCity.buildColumn(city, 12, 14);
        helper.assertTrue(!city.getBlockState(new BlockPos(12, 66, 14)).isAir()
                && city.getBlockState(new BlockPos(12, 90, 14)).isAir(), "Office tower must step back above its podium");
        helper.succeed();
    }
}
