package com.jayptucker.heroesevolved.vibe;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseResult;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.BreachActorsPayload;
import com.jayptucker.heroesevolved.network.FrequencyChoicePayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.Loadout;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Breach waypoints (frequencies): how many a viber holds, tuning, renaming and forgetting them, the wheel's free press,
 * a breach and a glimpse to one, and the terrain each mouth is sent to show the far side.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class VibeFrequencyGameTests {
    /** A viber at (5, 2, 2) facing +Z that can really be teleported through a breach. */
    private static ServerPlayer viber(GameTestHelper h, int powerLevel, String... skills) {
        ServerPlayer player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "vibe-frequency")) {
            @Override public boolean teleportTo(ServerLevel world, double x, double y, double z,
                                                Set<RelativeMovement> relative, float yaw, float pitch) {
                if (world != serverLevel()) return false;
                absMoveTo(x, y, z, yaw, pitch);
                return true;
            }
        };
        try {
            Connection connection = player.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel");
            channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        player.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.VIBE_ID).unlock(ModAbilities.VIBE_ID));
        PlayerProgressionService.setPowerLevel(player, powerLevel);
        PlayerEnergyService.restore(player, 999);
        player.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.VIBE_ID.toString(),
                new SkillTreeData.Build(15, Set.of(skills), Map.of(), 0)));
        place(h, player, 5, 2, 0);
        h.getLevel().addNewPlayer(player);
        return player;
    }

    private static void place(GameTestHelper h, ServerPlayer player, double x, double z, float yaw) {
        player.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(0, 2, 0))).add(x, 0, z));
        player.setYRot(yaw);
        player.setXRot(0);
        player.setOnGround(true);
    }

    private static void clean(ServerPlayer player) {
        VibeService.forget(player);
        VibeBreaches.close(player);
        if (player.serverLevel().getPlayerByUUID(player.getUUID()) != null)
            player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }

    private static void guarded(ServerPlayer player, Runnable check) {
        try {
            check.run();
        } catch (RuntimeException | AssertionError failure) {
            clean(player);
            throw failure;
        }
    }

    private static void done(GameTestHelper h, ServerPlayer player, Runnable check) {
        try {
            check.run();
            h.succeed();
        } finally {
            clean(player);
        }
    }

    /** Tunes slot {@code slot} at (x, 2, z) facing +Z, then puts the viber back at the start. */
    private static Vec3 tune(GameTestHelper h, ServerPlayer player, int slot, double x, double z) {
        place(h, player, x, z, 0);
        h.assertTrue(VibeFrequencies.mark(player, slot), "A frequency tunes on clear, solid ground");
        Vec3 feet = player.position();
        place(h, player, 5, 2, 0);
        return feet;
    }

    @GameTest(template = "rewind_empty", batch = "vibe.frequency")
    public static void frequencySlotsGrowWithPowerLevelAndWorldFrequency(GameTestHelper h) {
        h.assertTrue(VibeFrequencies.slots(1, false) == 1, "One frequency at Power Level 1");
        h.assertTrue(VibeFrequencies.slots(4, false) == 2 && VibeFrequencies.slots(8, false) == 3
                && VibeFrequencies.slots(12, false) == 4 && VibeFrequencies.slots(16, false) == 5, "One more every four levels");
        h.assertTrue(VibeFrequencies.slots(20, false) == 6, "Six at Level 20");
        h.assertTrue(VibeFrequencies.slots(20, true) == VibeFrequencies.MOST && VibeFrequencies.slots(1, true) == 3,
                "World Frequency holds two more");
        h.assertTrue(VibeFrequencies.nextLevel(5) == 8 && VibeFrequencies.nextLevel(19) == 20 && VibeFrequencies.nextLevel(20) == 0,
                "The wheel names the level that opens the next slot");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "vibe.frequency")
    public static void tuningFillsOnlyTheSlotsALevelHoldsAndCanBeRenamedAndForgotten(GameTestHelper h) {
        ServerPlayer p = viber(h, 4);
        done(h, p, () -> {
            h.assertTrue(VibeFrequencies.slots(p) == 2, "Power Level 4 holds two frequencies");
            tune(h, p, 0, 2, 8);
            tune(h, p, 1, 8, 8);
            place(h, p, 2, 5, 0);
            h.assertFalse(VibeFrequencies.mark(p, 2), "A third slot is locked until Power Level 8");
            p.setOnGround(false);
            h.assertFalse(VibeFrequencies.mark(p, 1), "Tuning needs solid ground underfoot");
            h.assertTrue(VibeFrequencies.rename(p, 0, "  Safe §chouse  "), "A tuned frequency can be renamed");
            h.assertTrue(VibeFrequencies.get(p, 0).name().equals("Safe chouse"), "Names are trimmed and stripped of formatting");
            h.assertTrue(VibeFrequencies.forget(p, 1) && VibeFrequencies.get(p, 1) == null, "A frequency can be forgotten");
            h.assertFalse(VibeFrequencies.rename(p, 1, "x"), "An empty slot has nothing to rename");
            h.assertTrue(VibeFrequencies.get(p, 0).feet().distanceToSqr(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 2, 8)))) < .01,
                    "The frequency keeps where it was tuned");
            Vec3 moved = tune(h, p, 0, 3, 8);
            h.assertTrue(VibeFrequencies.get(p, 0).name().equals("Safe chouse") && VibeFrequencies.get(p, 0).feet().equals(moved),
                    "Moving a named frequency keeps its name");
            tune(h, p, 1, 8, 8);
            PlayerProgressionService.setPowerLevel(p, 3);
            VibeFrequencies.choose(p, FrequencyChoicePayload.BREACH, 1, "");
            VibeBreaches.hurry(p);
            VibeFrequencies.choose(p, FrequencyChoicePayload.FORGET, 1, "");
            h.assertTrue(!VibeBreaches.isOpen(p) && VibeFrequencies.get(p, 1) != null,
                    "A stale wheel cannot use or forget a slot that has since locked");
        });
    }

    @GameTest(template = "rewind_empty", batch = "vibe.frequency")
    public static void theOldAttuneAnchorBecomesTheFirstFrequency(GameTestHelper h) {
        ServerPlayer p = viber(h, 5);
        done(h, p, () -> {
            CompoundTag anchor = new CompoundTag();
            anchor.putString("dimension", "minecraft:the_nether");
            anchor.putDouble("x", 8.5);
            anchor.putDouble("y", 128);
            anchor.putDouble("z", 8.5);
            anchor.putFloat("yaw", 90);
            CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            persisted.put(VibeFrequencies.OLD_ANCHOR, anchor);
            p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
            var first = VibeFrequencies.get(p, 0);
            h.assertTrue(first != null && first.dimension().equals(Level.NETHER.location()) && first.yaw() == 90,
                    "A saved World Frequency anchor carries over into slot 1");
            h.assertFalse(p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).contains(VibeFrequencies.OLD_ANCHOR),
                    "and the old anchor is gone");
            h.assertTrue("other_world".equals(VibeFrequencies.refusal(p, first)),
                    "Without World Frequency a breach can't reach another world");
        });
    }

    @GameTest(template = "rewind_empty", batch = "vibe.frequency")
    public static void theBreachKeyOpensTheWheelForFreeAndShiftStillAims(GameTestHelper h) {
        ServerPlayer p = viber(h, 15);
        p.setXRot(13);
        done(h, p, () -> {
            double stamina = PlayerEnergyService.getStamina(p);
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.SECONDARY, false) == AbilityUseResult.SUCCESS
                    && !VibeBreaches.isOpen(p) && PlayerEnergyService.getStamina(p) == stamina,
                    "A plain press costs nothing");
            VibeBreaches.letGo(p);
            h.assertTrue(!VibeBreaches.isOpen(p) && !VibeBreaches.holding(p) && PlayerEnergyService.getStamina(p) == stamina,
                    "and let go at once, it opens the frequency wheel and nothing else");
            h.assertTrue(VibeBreaches.shiftHold(p)
                    && VibeBreaches.kind(p) == VibeBreaches.Kind.AIMED && PlayerEnergyService.getStamina(p) < stamina,
                    "Shift-press opens a paid breach where you look");
        });
    }

    @GameTest(template = "rewind_empty", batch = "vibe.frequency")
    public static void aBreachToAFrequencyCarriesYouThereAndShowsTheFarSide(GameTestHelper h) {
        ServerPlayer p = viber(h, 15);
        Vec3 start = p.position();
        Vec3 far;
        BlockPos marker = h.absolutePos(new BlockPos(0, 2, 8));
        try {
            far = tune(h, p, 0, 2, 8);
            h.setBlock(new BlockPos(0, 2, 8), Blocks.GOLD_BLOCK);
            p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.VIBE_ID.toString(),
                    new SkillTreeData.Build(15, Set.of(), Map.of(Loadout.saveName(6), Loadout.base(AbilitySlot.SECONDARY),
                            Loadout.saveName(2), ""), 0)));
            double stamina = PlayerEnergyService.getStamina(p);
            h.assertTrue(AbilityUseService.activateKey(p, 6, false) == AbilityUseResult.SUCCESS && !VibeBreaches.isOpen(p),
                    "Breach opens the wheel when rebound to key six");
            VibeBreaches.letGo(p);
            VibeFrequencies.choose(p, FrequencyChoicePayload.BREACH, 0, "");
            VibeBreaches.hurry(p);
            h.assertTrue(VibeBreaches.kind(p) == VibeBreaches.Kind.FREQUENCY,
                    "Picking a frequency opens a breach even with the default key empty");
            h.assertTrue(PlayerEnergyService.getStamina(p) < stamina, "and pays Breach's cost");
        } catch (RuntimeException | AssertionError failure) {
            clean(p);
            throw failure;
        }
        Vec3 arrival = far.add(0, 0, 1.14);
        h.runAfterDelay(11, () -> guarded(p, () -> {
            BreachSnapshot view = VibeBreaches.view(p, 0);
            h.assertTrue(view != null && view.state(marker).is(Blocks.GOLD_BLOCK),
                    "The near mouth is sent the terrain round the far one");
            h.assertTrue(VibeBreaches.view(p, 1) != null && VibeBreaches.view(p, 1).contains(
                    (int) Math.floor(start.x), (int) Math.floor(start.y), (int) Math.floor(start.z)), "and the far mouth the near side");
            h.assertTrue(VibeBreaches.shown(p, 0, p), "The viber is shown the view through their own breach");
        }));
        h.runAfterDelay(12, () -> p.setPos(start.add(0, 0, 2.35)));
        h.runAfterDelay(14, () -> done(h, p, () -> h.assertTrue(p.position().distanceToSqr(arrival) < 1,
                "Walking into the near mouth comes out at the frequency: " + p.position() + " vs " + arrival)));
    }

    @GameTest(template = "rewind_empty", batch = "vibe.frequency")
    public static void anObstructedWaypointOpensNearbyAndMobsCrossBothWays(GameTestHelper h) {
        ServerPlayer player = viber(h, 15);
        Vec3 start = player.position();
        Vec3 saved = tune(h, player, 0, 2, 8);
        BlockPos obstruction = BlockPos.containing(saved);
        player.serverLevel().setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
        player.serverLevel().setBlockAndUpdate(obstruction.above(), Blocks.STONE.defaultBlockState());
        player.setXRot(80);
        var mob = EntityType.PILLAGER.create(h.getLevel());
        mob.setNoAi(true);
        mob.moveTo(start.add(0, 0, 1));
        h.getLevel().addFreshEntity(mob);
        h.runAfterDelay(19, mob::discard);
        guarded(player, () -> {
            VibeFrequencies.choose(player, FrequencyChoicePayload.BREACH, 0, "");
            VibeBreaches.hurry(player);
            h.assertTrue(VibeBreaches.kind(player) == VibeBreaches.Kind.FREQUENCY && !VibeBreaches.flat(player),
                    "A saved waypoint opens upright even when looking down");
            Vec3 exit = VibeBreaches.mouthAt(player, 1);
            h.assertTrue(exit.distanceToSqr(saved) > .25 && exit.distanceToSqr(saved) <= 18
                    && VibeFrequencies.get(player, 0).feet().equals(saved),
                    "The exit moves to clear space nearby while the saved point stays in place");
        });
        Vec3 exit = VibeBreaches.mouthAt(player, 1);
        h.runAfterDelay(12, () -> mob.setPos(start.add(0, 0, 2.45)));
        h.runAfterDelay(14, () -> guarded(player, () -> {
            h.assertTrue(mob.position().distanceToSqr(exit) < 2.3, "A walking mob comes out at the waypoint");
            mob.setPos(exit.subtract(mob.position().subtract(exit).scale(.4)));
        }));
        h.runAfterDelay(17, () -> {
            try {
                done(h, player, () -> h.assertTrue(mob.position().distanceToSqr(start) < 2,
                        "A mob can walk back through the far mouth"));
            } finally {
                mob.discard();
            }
        });
    }

    @GameTest(template = "rewind_empty", batch = "vibe.frequency", skyAccess = true)
    public static void highTierAimedBreachCanReachAsFarAsFoldSpace(GameTestHelper h) {
        ServerPlayer player = viber(h, 6);
        done(h, player, () -> {
            h.assertTrue(VibeBreaches.aimRange(player) == 30, "Early tiers keep the shorter range");
            PlayerProgressionService.setPowerLevel(player, 10);
            h.assertTrue(VibeBreaches.aimRange(player) == com.jayptucker.heroesevolved.time.SpaceTimeService.blinkRange(4),
                    "Tier four uses Fold Space's configured range");
            PlayerProgressionService.setPowerLevel(player, 15);
            player.setXRot(-90);
            double eyeY = player.getEyeY();
            h.assertTrue(VibeBreaches.shiftHold(player),
                    "The high-tier caster can aim through open sky");
            h.assertTrue(Math.abs(VibeBreaches.mouthAt(player, 1).y - eyeY
                    - com.jayptucker.heroesevolved.time.SpaceTimeService.blinkRange(5)) < .05,
                    "The breach actually lands at Fold Space's maximum distance");
        });
    }

    @GameTest(template = "rewind_empty", batch = "vibe.frequency")
    public static void preparingAnUnloadedFrequencyCanBeCanceledWithoutSpendingStamina(GameTestHelper h) {
        ServerPlayer player = viber(h, 15);
        done(h, player, () -> {
            Vec3 distant = player.position().add(4096, 0, 0);
            h.assertFalse(player.serverLevel().hasChunkAt(BlockPos.containing(distant)), "The destination starts unloaded");
            var frequency = new VibeFrequencies.Frequency("Distant", "", player.serverLevel().dimension().location(), distant, 0);
            CompoundTag tag = frequency.save();
            tag.putInt("slot", 0);
            var saved = new net.minecraft.nbt.ListTag();
            saved.add(tag);
            var persisted = new CompoundTag();
            persisted.put(VibeFrequencies.KEY, saved);
            player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);

            double stamina = PlayerEnergyService.getStamina(player);
            VibeFrequencies.choose(player, FrequencyChoicePayload.BREACH, 0, "");
            VibeBreaches.hurry(player);
            h.assertTrue(!VibeBreaches.isOpen(player) && PlayerEnergyService.getStamina(player) == stamina,
                    "Choosing an unloaded waypoint waits for chunks before paying for a breach");
            h.assertTrue(VibeBreaches.close(player) && !VibeBreaches.close(player), "A press cancels the pending opening once");
            h.assertTrue(frequency.equals(VibeFrequencies.get(player, 0)), "Canceling preserves the saved location");
        });
    }

    @GameTest(template = "rewind_empty", batch = "vibe.frequency")
    public static void aLiveBreachCarriesProximityVoiceBothWaysWithoutEcho(GameTestHelper h) {
        ServerPlayer listener = viber(h, 15, "vibe_1_1");
        ServerPlayer speaker = viber(h, 15);
        h.runAfterDelay(40, () -> {
            clean(listener);
            clean(speaker);
        });
        Vec3 destination = tune(h, listener, 0, 2, 8);
        speaker.setPos(destination.add(0, 0, 2.5));
        VibeFrequencies.choose(listener, FrequencyChoicePayload.BREACH, 0, "");
        VibeBreaches.hurry(listener);
        h.runAfterDelay(11, () -> guarded(listener, () -> {
            var route = BreachVoiceRoutes.routes(speaker.getUUID()).stream()
                    .filter(candidate -> candidate.listener().equals(listener.getUUID())).findFirst().orElse(null);
            h.assertTrue(route != null && route.audible(6) && !route.audible(3) && !route.audible(48),
                    "Voice uses the shorter path through the mouths and stays silent if normal proximity already works");
            h.assertTrue(route.position().subtract(listener.getEyePosition()).normalize().dot(listener.getLookAngle()) > .95,
                    "Voice seems to come from the local mouth");
            h.assertTrue(BreachVoiceRoutes.routes(listener.getUUID()).stream()
                    .anyMatch(candidate -> candidate.listener().equals(speaker.getUUID()) && candidate.audible(6)),
                    "The listener can speak back through the same breach");
            VibeBreaches.close(listener);
        }));
        h.runAfterDelay(14, () -> guarded(listener, () -> {
            h.assertTrue(BreachVoiceRoutes.routes(speaker.getUUID()).stream()
                    .noneMatch(route -> route.listener().equals(listener.getUUID()) && route.audible(6)),
                    "Closing the breach ends the relay between the two players");
            VibeFrequencies.choose(listener, FrequencyChoicePayload.GLIMPSE, 0, "");
            VibeBreaches.hurry(listener);
        }));
        h.runAfterDelay(26, () -> {
            try {
                done(h, listener, () -> h.assertTrue(VibeBreaches.kind(listener) == VibeBreaches.Kind.GLIMPSE
                        && BreachVoiceRoutes.routes(speaker.getUUID()).stream()
                        .noneMatch(route -> route.listener().equals(listener.getUUID()) && route.audible(6)),
                        "Glimpse leaves proximity voice unchanged"));
            } finally {
                clean(speaker);
            }
        });
    }

    @GameTest(template = "rewind_empty", batch = "vibe.frequency")
    public static void aGlimpseShowsAFrequencyButCannotBeCrossed(GameTestHelper h) {
        ServerPlayer p = viber(h, 15, "vibe_1_1");
        Vec3 start = p.position();
        try {
            tune(h, p, 0, 2, 8);
            h.assertTrue(AbilityUseService.activateAction(p, "vibe_1_1", false) == AbilityUseResult.SUCCESS && !VibeBreaches.isOpen(p),
                    "Glimpse's press opens the wheel for free");
            VibeFrequencies.choose(p, FrequencyChoicePayload.GLIMPSE, 0, "");
            VibeBreaches.hurry(p);
            h.assertTrue(VibeBreaches.kind(p) == VibeBreaches.Kind.GLIMPSE, "Picking a frequency opens a window to it");
        } catch (RuntimeException | AssertionError failure) {
            clean(p);
            throw failure;
        }
        h.runAfterDelay(11, () -> guarded(p, () -> {
            h.assertTrue(VibeBreaches.view(p, 0) != null, "The window shows the far side");
            h.assertTrue(VibeBreaches.seam(p, p.getEyePosition(), p.getEyePosition().add(0, 0, 10)) == null,
                    "A blast can't go through a window");
            Arrow arrow = new Arrow(EntityType.ARROW, p.serverLevel());
            arrow.setPos(start.add(0, 1.2, .5));
            arrow.setDeltaMovement(0, 0, 3);
            h.assertFalse(BreachProjectiles.beforeTick(p.serverLevel(), arrow), "A Glimpse does not transport arrows");
        }));
        h.runAfterDelay(12, () -> p.setPos(start.add(0, 0, 2.35)));
        h.runAfterDelay(14, () -> done(h, p, () -> h.assertTrue(p.position().distanceToSqr(start.add(0, 0, 2.35)) < .01,
                "Walking into a glimpse goes nowhere")));
    }

    @GameTest(template = "rewind_empty", batch = "vibe.frequency")
    public static void arrowsAndFastTaczRoundsKeepTheirAimThroughATurnedBreach(GameTestHelper h) {
        ServerPlayer p = viber(h, 15);
        Vec3 near = p.position().add(0, VibeGeometry.PORTAL_HEIGHT, 2);
        Vec3 far = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(7, 2, 8))).add(0, VibeGeometry.PORTAL_HEIGHT, 0);
        guarded(p, () -> {
            place(h, p, 7, 8, 90);
            h.assertTrue(VibeFrequencies.mark(p, 0), "Tune an exit turned ninety degrees from the entrance");
            place(h, p, 5, 2, 0);
            VibeFrequencies.choose(p, FrequencyChoicePayload.BREACH, 0, "");
            VibeBreaches.hurry(p);
        });
        h.runAfterDelay(12, () -> done(h, p, () -> {
            Vec3 start = near.add(.15, -.15, -1.7);
            Vec3 incoming = new Vec3(.16, .09, 3.2);
            Arrow arrow = new Arrow(EntityType.ARROW, p.serverLevel());
            arrow.setOwner(p);
            arrow.setPos(start);
            arrow.setDeltaMovement(incoming);
            h.setBlock(new BlockPos(5, 3, 3), Blocks.STONE);
            h.assertFalse(BreachProjectiles.beforeTick(p.serverLevel(), arrow), "Cover in front of the entrance stops an arrow");
            h.setBlock(new BlockPos(5, 3, 3), Blocks.AIR);
            h.assertTrue(BreachProjectiles.beforeTick(p.serverLevel(), arrow), "The same incoming arrow reaches an unobstructed breach");
            Vec3 expected = new Vec3(-3.2, .09, .16);
            h.assertTrue(arrow.getDeltaMovement().distanceToSqr(expected) < 1e-8 && arrow.position().distanceToSqr(far) < .2,
                    "The arrow preserves speed, pitch and its sideways aim at the exit: " + arrow.getDeltaMovement());
            arrow.tick();
            h.assertTrue(arrow.getX() < far.x - 2, "The arrow continues on the far side in the same direction");

            Entity round = PowerWeapons.testBullet(p.serverLevel(), 0);
            h.assertTrue(round instanceof Projectile, "TaCZ test round is available");
            Projectile bullet = (Projectile) round;
            bullet.setOwner(p);
            bullet.setPos(start);
            bullet.setDeltaMovement(.32, .08, 19);
            h.assertTrue(BreachProjectiles.beforeTick(p.serverLevel(), bullet), "A round can cross the whole entrance in one tick");
            h.assertTrue(bullet.getDeltaMovement().distanceToSqr(new Vec3(-19, .08, .32)) < 1e-8
                    && bullet.position().distanceToSqr(far) < .2, "The routed TaCZ round remains at full speed with the same aim");

            Arrow reverse = new Arrow(EntityType.ARROW, p.serverLevel());
            reverse.setOwner(p);
            reverse.setPos(far.add(-1.5, 0, 0));
            reverse.setDeltaMovement(2, 0, 0);
            h.assertTrue(BreachProjectiles.beforeTick(p.serverLevel(), reverse), "A shot can enter through the other mouth");
            h.assertTrue(reverse.getDeltaMovement().distanceToSqr(new Vec3(0, 0, -2)) < 1e-8
                    && reverse.position().distanceToSqr(near) < .01, "The reverse shot emerges toward the entrance side");
        }));
    }

    @GameTest(template = "rewind_empty", batch = "vibe.frequency")
    public static void aBreachActorPacketPreservesModelPoseAndEquipment(GameTestHelper h) {
        var level = h.getLevel();
        var guard = EntityType.PILLAGER.create(level);
        guard.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(6, 2, 8))));
        guard.setYBodyRot(35);
        guard.setYHeadRot(40);
        guard.setItemSlot(EquipmentSlot.MAINHAND, Items.CROSSBOW.getDefaultInstance());
        var payload = new BreachActorsPayload(12, List.of(BreachActorsPayload.Actor.capture(level, guard)));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            BreachActorsPayload.STREAM_CODEC.encode(buffer, payload);
            var copy = BreachActorsPayload.STREAM_CODEC.decode(buffer);
            var actor = copy.actors().getFirst();
            h.assertTrue(copy.view() == 12 && actor.spawn().getType() == EntityType.PILLAGER
                    && actor.spawn().getUUID().equals(guard.getUUID()) && actor.bodyYaw() == 35 && actor.headYaw() == 40,
                    "The far-side entity keeps its model, identity and facing");
            h.assertTrue(actor.equipment().get(EquipmentSlot.MAINHAND.ordinal()).is(Items.CROSSBOW)
                    && !actor.appearance().packedItems().isEmpty(), "and includes equipment and synced model state");
            h.succeed();
        } finally {
            buffer.release();
        }
    }

    @GameTest(template = "rewind_empty", batch = "vibe.frequency")
    public static void aSnapshotSurvivesThePacketWhole(GameTestHelper h) {
        h.setBlock(new BlockPos(4, 2, 4), Blocks.OAK_LOG);
        h.setBlock(new BlockPos(4, 3, 4), Blocks.GLOWSTONE);
        BreachSnapshot sent = BreachSnapshot.capture(h.getLevel(), Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5, 2, 5))));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        sent.write(buf);
        int bytes = buf.readableBytes();
        BreachSnapshot read = BreachSnapshot.read(buf);
        h.assertTrue(read.origin().equals(sent.origin()) && Arrays.equals(read.states(), sent.states())
                && Arrays.equals(read.light(), sent.light()) && read.sky() == sent.sky() && read.grass() == sent.grass(),
                "Every block, light value and colour arrives as it was sent");
        h.assertTrue(read.state(h.absolutePos(new BlockPos(4, 2, 4))).is(Blocks.OAK_LOG), "A block reads back at its world position");
        h.assertTrue(bytes < 64_000, "Run-length packing keeps a flat arena small: " + bytes + " bytes");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "vibe.frequency")
    public static void theViewTurnsTheFarSideTheWayATravelerTurns(GameTestHelper h) {
        for (float farYaw : new float[]{0, 90, -135, 200}) {
            for (float nearYaw : new float[]{0, 45, 180, -90}) {
                Vec3 farAhead = Vec3.directionFromRotation(0, farYaw), nearAhead = Vec3.directionFromRotation(0, nearYaw);
                Vec3 seen = VibeGeometry.rotate(farAhead.scale(3).add(0, 2, 0), farAhead, farYaw, nearAhead, nearYaw);
                h.assertTrue(seen.distanceToSqr(nearAhead.scale(3).add(0, 2, 0)) < 1e-6,
                        "Ahead of the far mouth is seen ahead of the near one (" + farYaw + ", " + nearYaw + ")");
                Vec3 traveler = VibeGeometry.rotate(nearAhead, nearAhead, nearYaw, farAhead, farYaw);
                h.assertTrue(traveler.distanceToSqr(farAhead) < 1e-6, "A traveler walking in comes out facing ahead of the far mouth");
            }
        }
        Vec3 tilted = Vec3.directionFromRotation(-57, 23), upright = Vec3.directionFromRotation(0, 145);
        Vec3 turned = VibeGeometry.rotate(tilted, tilted, 23, upright, 145);
        h.assertTrue(turned.distanceToSqr(upright) < 1e-6, "A tilted mouth maps its normal to the upright exit's normal");
        Vec3 up = VibeGeometry.side(tilted).cross(tilted).normalize();
        Vec3 turnedUp = VibeGeometry.rotate(up, tilted, 23, upright, 145);
        h.assertTrue(turnedUp.distanceToSqr(VibeGeometry.UP) < 1e-6, "The mouth's vertical axis keeps its orientation through both turns");
        h.succeed();
    }
}
