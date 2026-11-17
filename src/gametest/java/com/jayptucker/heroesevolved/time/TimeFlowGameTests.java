package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.expansion.AdvancedTechniques;
import com.jayptucker.heroesevolved.network.TimeFlowPayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Slowed time as a whole ({@link TimeFlow}): the speedster's drag, who is hidden, fuses, and the picture sent to clients. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class TimeFlowGameTests {
    private static final class Scene implements AutoCloseable {
        final GameTestHelper h;
        final List<ServerPlayer> players = new ArrayList<>();
        Scene(GameTestHelper h) { this.h = h; }
        ServerPlayer player(ResourceLocation power, int tier, double x) {
            var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "time-flow"));
            player.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))).add(x, 0, 0));
            if (power != null) player.setData(ModDataAttachments.PLAYER_ABILITIES,
                    PlayerAbilityData.empty().assignDormant(power).unlock(power));
            PlayerProgressionService.setLevel(player, tier);
            PlayerList list = h.getLevel().getServer().getPlayerList();
            TimeFlowGameTests.<List<ServerPlayer>>field(list, "players").add(player);
            TimeFlowGameTests.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").put(player.getUUID(), player);
            players.add(player);
            return player;
        }
        @Override public void close() {
            for (var player : players) {
                TimeStopService.stop(player); TimeSlowService.stop(player); AdvancedTechniques.endPerception(player);
                TimeVeil.forget(player);
                player.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SpeedService.STRIDE);
                PlayerTimeSlow.update(player);
            }
            PlayerList list = h.getLevel().getServer().getPlayerList();
            for (var player : players) {
                TimeFlowGameTests.<List<ServerPlayer>>field(list, "players").remove(player);
                TimeFlowGameTests.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").remove(player.getUUID());
            }
            TemporalVoiceState.publish(h.getLevel().getServer());
        }
    }

    private static boolean near(double a, double b) { return Math.abs(a - b) < 1e-6; }

    private static void run(ServerPlayer speedster) {
        speedster.getAttribute(Attributes.MOVEMENT_SPEED).addOrUpdateTransientModifier(
                new AttributeModifier(SpeedService.STRIDE, 4, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    @GameTest(template = "rewind_empty", batch = "time.flow_drag")
    public static void aSpeedsterAtSpeedIsDraggedNotSlowed(GameTestHelper h) {
        try (var scene = new Scene(h)) {
            var traveller = scene.player(ModAbilities.TIME_MANIPULATION_ID, 5, 0);
            var speedster = scene.player(ModAbilities.SUPERSPEED_ID, 3, 2);
            var walker = scene.player(null, 1, -2);
            TimeSlowService.start(traveller, 5);
            double slowed = PlayerTimeSlow.movementScale(update(walker));
            h.assertTrue(slowed < 0.2, "An ordinary player crawls in Between Seconds (" + slowed + ")");
            h.assertTrue(near(PlayerTimeSlow.movementScale(update(speedster)), slowed), "So does a speedster standing about");
            run(speedster);
            h.assertTrue(near(PlayerTimeSlow.movementScale(update(speedster)), TimeFlow.DRAG_SLOW),
                    "At speed, a speedster is dragged as if running in water: still fast, not as fast");
            h.assertTrue(speedster.getAttribute(Attributes.ATTACK_SPEED).getModifier(ResourceLocation.fromNamespaceAndPath("heroesevolved", "time_slow")) == null,
                    "and their hands stay quick");
            h.assertTrue(TimeFlow.style(traveller) == TimeFlow.TIME && TimeFlow.style(speedster) == TimeFlow.SPEED
                    && TimeFlow.style(walker) == TimeFlow.NONE, "The traveller and the runner move through it; the walker is caught in it");
            // Voices: seeing the caster as a blur and dragging the runner change nothing about who can be heard.
            TemporalVoiceState.publish(h.getLevel().getServer());
            h.assertTrue(TemporalVoiceState.frozen(walker.getUUID()) && !TemporalVoiceState.frozen(traveller.getUUID())
                    && !TemporalVoiceState.frozen(speedster.getUUID()), "The walker's voice falls still; the traveller and the dragged runner keep theirs");
            h.assertTrue(!TemporalVoiceState.blocks(traveller.getUUID(), speedster.getUUID())
                    && !TemporalVoiceState.blocks(speedster.getUUID(), traveller.getUUID()), "The traveller and the runner hear each other");
            h.assertTrue(TemporalVoiceState.blocks(walker.getUUID(), traveller.getUUID())
                    && TemporalVoiceState.blocks(traveller.getUUID(), walker.getUUID()), "Neither way leaks between them and the walker");
            TimeSlowService.stop(traveller);
            h.assertTrue(near(PlayerTimeSlow.movementScale(update(speedster)), 1), "Out of slowed time the run is whole again");
            TemporalVoiceState.publish(h.getLevel().getServer());
            h.assertTrue(!TemporalVoiceState.blocks(walker.getUUID(), traveller.getUUID()) && !TemporalVoiceState.frozen(walker.getUUID()),
                    "and ordinary voice resumes");

            AdvancedTechniques.startPerception(speedster);
            h.assertTrue(near(PlayerTimeSlow.movementScale(update(speedster)), TimeFlow.DRAG_SLOW), "Their own Perception drags at them too");
            speedster.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SpeedService.STRIDE);
            h.assertTrue(near(PlayerTimeSlow.movementScale(update(speedster)), 1), "but walking in it, they are never slowed");
            AdvancedTechniques.endPerception(speedster);

            TimeStopService.start(traveller, 5);
            run(speedster);
            h.assertTrue(near(PlayerTimeSlow.movementScale(update(speedster)), TimeFlow.DRAG_STOP), "Stopped time drags harder");
            TemporalVoiceState.publish(h.getLevel().getServer());
            h.assertTrue(TemporalVoiceState.frozen(walker.getUUID()) && !TemporalVoiceState.blocks(traveller.getUUID(), speedster.getUUID()),
                    "Stopped, the walker is silent and the two who move in time still hear each other");
            h.succeed();
        }
    }

    private static ServerPlayer update(ServerPlayer player) { PlayerTimeSlow.update(player); return player; }

    @GameTest(template = "rewind_empty", batch = "time.flow_veil")
    public static void onlyStoppedTimeHidesTheCaster(GameTestHelper h) {
        try (var scene = new Scene(h)) {
            var traveller = scene.player(ModAbilities.TIME_MANIPULATION_ID, 5, 0);
            var bystander = scene.player(null, 1, 3);
            Zombie zombie = EntityType.ZOMBIE.create(h.getLevel());
            zombie.setPos(traveller.position().add(0, 0, 3));
            TimeVeil.begin(traveller, TimeVeil.Style.TIME, TimeVeil.SLOW);
            h.assertTrue(TimeVeil.veiled(traveller) && !TimeVeil.hiding(traveller), "Slowed time hides nobody: they are seen as a blur");
            h.assertTrue(!TimeVeil.masks(bystander, traveller.position()), "so nothing drawn beside them is hidden either");
            Vec3 started = traveller.position();
            h.assertTrue(TimeVeil.perceivedTarget(zombie, traveller).position().distanceTo(started) < 1e-6,
                    "A slowed zombie still swings at where it last saw them");
            traveller.setPos(started.add(4, 0, 0));
            TimeVeil.begin(traveller, TimeVeil.Style.TIME, TimeVeil.STOP);
            h.assertTrue(TimeVeil.hiding(traveller) && TimeVeil.masks(bystander, traveller.position()), "Stopping time hides them");
            h.assertTrue(TimeVeil.perceivedTarget(zombie, traveller).position().distanceTo(traveller.position()) < 1e-6,
                    "The stand-in stands where time stopped, not where the slow began");
            TimeVeil.end(traveller, TimeVeil.STOP);
            h.assertTrue(TimeVeil.veiled(traveller) && !TimeVeil.hiding(traveller), "Time starts again but stays slowed: seen again");
            TimeVeil.end(traveller, TimeVeil.SLOW);
            h.assertTrue(!TimeVeil.veiled(traveller), "and with every reason gone, the veil is gone");
            h.succeed();
        }
    }

    @GameTest(template = "rewind_empty", batch = "time.flow_fuse")
    public static void fusesBurnSlowlyEnoughToAnswer(GameTestHelper h) {
        try (var scene = new Scene(h)) {
            var speedster = scene.player(ModAbilities.SUPERSPEED_ID, 3, 0);
            Creeper creeper = EntityType.CREEPER.create(h.getLevel());
            creeper.setPos(speedster.position().add(3, 0, 0));
            AdvancedTechniques.startPerception(speedster);
            h.assertTrue(TemporalMobTime.interval(creeper) == 3, "Perception slows a creeper to a third");
            creeper.setSwellDir(1);
            h.assertTrue(TemporalMobTime.interval(creeper) == TimeFlow.FUSE_INTERVAL, "but a lit one burns six times slower");
            h.assertTrue(TimeFlow.fuse(20) == 20 && TimeFlow.fuse(1) == 1, "Slower fields keep their own rate; normal time is untouched");
            AdvancedTechniques.endPerception(speedster);
            h.assertTrue(TemporalMobTime.interval(creeper) == 1, "Out of slowed time the fuse runs as normal");
            h.succeed();
        }
    }

    @GameTest(template = "rewind_empty", batch = "time.flow_payload")
    public static void thePictureIsResentOnlyWhenItChanges(GameTestHelper h) {
        UUID owner = UUID.randomUUID();
        var slow = new TimeFlow.Field(owner, 7, Level.OVERWORLD, Vec3.ZERO, 32, 9, true);
        var moved = new TimeFlow.Field(owner, 7, Level.OVERWORLD, new Vec3(5, 0, 0), 32, 9, true);
        var stop = new TimeFlow.Field(owner, 7, Level.OVERWORLD, Vec3.ZERO, 32, 0, false);
        var shifted = new TimeFlow.Field(owner, 7, Level.OVERWORLD, new Vec3(5, 0, 0), 32, 0, false);
        var runner = List.of(new TimeFlowPayload.Mover(7, TimeFlow.TIME));
        h.assertTrue(TimeFlowPayload.of(true, List.of(slow), runner).sameAs(TimeFlowPayload.of(true, List.of(moved), runner)),
                "A field following its owner isn't re-sent as they walk");
        h.assertTrue(!TimeFlowPayload.of(true, List.of(stop), runner).sameAs(TimeFlowPayload.of(true, List.of(shifted), runner)),
                "A fixed field is");
        h.assertTrue(!TimeFlowPayload.of(true, List.of(slow), runner).sameAs(TimeFlowPayload.of(true, List.of(slow), List.of())),
                "So is a change in who moves through it");
        h.succeed();
    }

    @SuppressWarnings("unchecked") private static <T> T field(PlayerList list, String name) {
        try { var field = PlayerList.class.getDeclaredField(name); field.setAccessible(true); return (T) field.get(list); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}
