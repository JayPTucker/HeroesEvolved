package com.jayptucker.heroesevolved.story.sylar;

import com.jayptucker.heroesevolved.PowerOwnershipTestSupport;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.data.PowerOwnershipSavedData;
import com.jayptucker.heroesevolved.intro.PowerIntroHistory;
import com.jayptucker.heroesevolved.intro.PowerIntroService;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.telepathy.FutureVisions;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import java.lang.reflect.Field;
import java.util.*;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class SylarGameTests {
    private static final Map<UUID, Set<ChunkPos>> CHUNKS = new HashMap<>();
    @SuppressWarnings("unchecked") private static <T> T field(Object object, Class<?> type, String name) {
        try { Field field = type.getDeclaredField(name); field.setAccessible(true); return (T) field.get(object); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static ServerPlayer player(GameTestHelper helper, ResourceLocation power, int x, List<Packet<?>> packets) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "sylar-test")) {
            @Override public boolean isInvulnerableTo(DamageSource source) { return false; }
            // FakePlayer intentionally drops chat instead of using its connection.
            @Override public void sendSystemMessage(net.minecraft.network.chat.Component message) {
                packets.add(new ClientboundSystemChatPacket(message, false));
            }
        };
        try {
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime"); grace.setAccessible(true); grace.setInt(player, 0);
            Connection connection = player.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
            player.connection = new ServerGamePacketListenerImpl(player.getServer(), connection, player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
                @Override public void send(Packet<?> packet) { packets.add(packet); }
            };
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(x, 230, 5))));
        player.setNoGravity(true); player.setGameMode(GameType.SURVIVAL);
        for (int dx = -16; dx <= 16; dx += 16) for (int dz = -16; dz <= 16; dz += 16) {
            ChunkPos chunk = new ChunkPos(player.blockPosition().offset(dx, 0, dz));
            if (!helper.getLevel().getForcedChunks().contains(chunk.toLong())) {
                helper.getLevel().setChunkForced(chunk.x, chunk.z, true);
                CHUNKS.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>()).add(chunk);
            }
            helper.getLevel().getChunk(chunk.x, chunk.z);
        }
        helper.getLevel().addNewPlayer(player);
        SylarGameTests.<List<ServerPlayer>>field(player.getServer().getPlayerList(), PlayerList.class, "players").add(player);
        SylarGameTests.<Map<UUID, ServerPlayer>>field(player.getServer().getPlayerList(), PlayerList.class, "playersByUUID").put(player.getUUID(), player);
        if (power != null) {
            player.setData(ModDataAttachments.POWER_INTRO_HISTORY, PowerIntroHistory.empty().with(power));
            if (power.equals(ModAbilities.TIME_MANIPULATION_ID)) player.setData(ModDataAttachments.TIME_INTRO_SEEN, true);
            PlayerAbilityService.grantAbility(player, power);
        }
        return player;
    }
    private static void reset(GameTestHelper helper) {
        SylarStoryService.clear(helper.getLevel().getServer());
        helper.getLevel().getServer().overworld().getDataStorage().set(SylarStoryData.NAME, new SylarStoryData());
    }
    private static SylarEntity hunter(GameTestHelper helper, ServerPlayer quarry, int distance) {
        boolean begun = SylarStoryService.beginAt(quarry, quarry.position().add(distance, 0, 0), false);
        helper.assertTrue(begun, "Fixture must create the singleton hunter: eligible=" + SylarStoryService.eligible(quarry)
                + " active=" + (SylarStoryService.hunter() != null) + " eclipse=" + com.jayptucker.heroesevolved.events.EclipseService.isActive(quarry.getServer())
                + " intro=" + PowerIntroService.isActive(quarry) + " alive=" + quarry.isAlive() + " dim=" + quarry.level().dimension().location()
                + " mode=" + quarry.gameMode.getGameModeForPlayer() + " sleeping=" + quarry.isSleeping() + " difficulty=" + quarry.level().getDifficulty()
                + " timeIntro=" + com.jayptucker.heroesevolved.time.intro.TimeIntroService.isVisiting(quarry)
                + " suppressed=" + com.jayptucker.heroesevolved.events.EclipseService.arePowersSuppressed(quarry)
                + " power=" + PlayerAbilityService.getData(quarry).assignedPower().map(e -> e.getKey() + ":" + e.getValue().isUnlocked()).orElse("none"));
        SylarEntity hunter = SylarStoryService.hunter(); hunter.setNoGravity(true); return hunter;
    }
    private static void remove(ServerPlayer... players) {
        if (players.length == 0) return;
        SylarStoryService.clear(players[0].getServer());
        for (ServerPlayer player : players) {
            PowerOwnershipTestSupport.release(player); PowerIntroService.forget(player);
            com.jayptucker.heroesevolved.telepathy.TelepathyService.stopAll(player);
            com.jayptucker.heroesevolved.time.TimeRewindService.forget(player);
            SylarGameTests.<List<ServerPlayer>>field(player.getServer().getPlayerList(), PlayerList.class, "players").remove(player);
            SylarGameTests.<Map<UUID, ServerPlayer>>field(player.getServer().getPlayerList(), PlayerList.class, "playersByUUID").remove(player.getUUID());
            player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            var chunks = CHUNKS.remove(player.getUUID());
            if (chunks != null) for (var chunk : chunks) player.serverLevel().setChunkForced(chunk.x, chunk.z, false);
        }
    }
    private static long messages(List<Packet<?>> packets, String key) {
        return packets.stream().filter(packet -> packet instanceof ClientboundSystemChatPacket chat
                && chat.content().getContents() instanceof TranslatableContents contents && contents.getKey().equals(key)).count();
    }

    @GameTest(template = "rewind_empty", batch = "sylar.sense")
    public static void telepathSensesQuietWatcherThroughWallsWithoutSpamming(GameTestHelper helper) {
        reset(helper);
        List<Packet<?>> quarryPackets = new ArrayList<>(), telepathPackets = new ArrayList<>();
        ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, quarryPackets);
        ServerPlayer telepath = player(helper, ModAbilities.TELEPATHY_ID, 6, telepathPackets);
        for (int y = 0; y <= 3; y++) for (int z = -2; z <= 2; z++)
            helper.getLevel().setBlock(quarry.blockPosition().offset(8, y, z), Blocks.STONE.defaultBlockState(), 3);
        SylarEntity hunter = hunter(helper, quarry, 18);
        try {
            helper.assertTrue(hunter.getTarget() == null && !hunter.hunting(), "A distant watcher must not need an attack target to be sensed");
            helper.assertTrue(!telepath.hasLineOfSight(hunter), "Fixture wall must occlude Sylar");
            helper.assertTrue(messages(telepathPackets, "story.heroesevolved.sylar.sense") == 1, "Nearby telepath must receive the private presence warning through walls");
            helper.assertTrue(messages(quarryPackets, "story.heroesevolved.sylar.sense") == 0, "Non-telepaths must not receive telepathic detection");
            SylarStoryService.senseNearby(helper.getLevel().getServer(), hunter, helper.getLevel().getGameTime());
            helper.assertTrue(messages(telepathPackets, "story.heroesevolved.sylar.sense") == 1, "Repeated sensing must respect the fifteen-second warning cooldown");
            helper.assertTrue(FutureVisions.tryReveal(telepath, hunter), "Reading Sylar's mind must reveal his hunger rather than a generic mob thought");
            helper.succeed();
        } finally { remove(quarry, telepath); }
    }

    @GameTest(template = "rewind_empty", batch = "sylar.range")
    public static void senseHonorsRangeCreativeAndDormantPowers(GameTestHelper helper) {
        reset(helper); List<Packet<?>> packets = new ArrayList<>();
        ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, new ArrayList<>());
        ServerPlayer telepath = player(helper, ModAbilities.TELEPATHY_ID, 6, packets);
        telepath.setPos(quarry.position().add(100, 0, 0));
        SylarEntity hunter = hunter(helper, quarry, 20);
        try {
            helper.assertTrue(messages(packets, "story.heroesevolved.sylar.sense") == 0, "Telepathy must not detect Sylar beyond sixty-four blocks");
            telepath.setPos(hunter.position().add(64, 0, 0));
            SylarStoryService.senseNearby(helper.getLevel().getServer(), hunter, helper.getLevel().getGameTime());
            helper.assertTrue(messages(packets, "story.heroesevolved.sylar.sense") == 1, "The exact sixty-four-block boundary must be included");
            quarry.setGameMode(GameType.CREATIVE);
            helper.assertTrue(!SylarStoryService.eligible(quarry), "Creative players must not be hunted");
            quarry.setGameMode(GameType.SURVIVAL); PlayerAbilityService.removeAssignedAbility(quarry);
            helper.assertTrue(!SylarStoryService.eligible(quarry), "Unassigned players must not be hunted");
            PlayerAbilityService.assignDormantAbility(quarry, ModAbilities.QUAKE_ID);
            helper.assertTrue(!SylarStoryService.eligible(quarry), "Dormant powers must not attract the hunter");
            PlayerAbilityService.removeAssignedAbility(telepath);
            PlayerAbilityService.assignDormantAbility(telepath, ModAbilities.TELEPATHY_ID);
            helper.assertTrue(!SylarStoryService.isTelepath(telepath), "Dormant telepathy must not reveal the hunter");
            helper.succeed();
        } finally { remove(quarry, telepath); }
    }

    @GameTest(template = "rewind_empty", batch = "sylar.interrupt", timeoutTicks = 135)
    public static void damagingSylarInterruptsTheSixSecondAcquisition(GameTestHelper helper) {
        reset(helper); ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, new ArrayList<>());
        SylarEntity hunter = hunter(helper, quarry, 6); hunter.hunt(quarry);
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(hunter.extracting() && hunter.extractionTicks() > 0, "Sylar must telegraph an acquisition channel before copying a power");
            hunter.hurt(helper.getLevel().damageSources().playerAttack(quarry), 4);
            helper.assertTrue(!hunter.extracting() && hunter.extractionTicks() == 0, "Hitting him must break his concentration immediately");
        });
        helper.runAfterDelay(115, () -> {
            try {
                helper.assertTrue(!SylarStoryData.get(quarry.getServer()).knows(ModAbilities.QUAKE_ID), "An interrupted attempt must not grant the victim's power");
                helper.succeed();
            } finally { remove(quarry); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "sylar.copy", timeoutTicks = 245)
    public static void completedAcquisitionCopiesKnowledgeWithoutTakingUniquePlayerRole(GameTestHelper helper) {
        reset(helper); ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, new ArrayList<>());
        SylarEntity hunter = hunter(helper, quarry, 6); hunter.hunt(quarry);
        helper.runAfterDelay(90, () -> helper.assertTrue(!SylarStoryData.get(quarry.getServer()).knows(ModAbilities.QUAKE_ID), "A partial channel must not acquire anything"));
        helper.runAfterDelay(220, () -> {
            try {
                helper.assertTrue(SylarStoryData.get(quarry.getServer()).knows(ModAbilities.QUAKE_ID), "A completed, damaging six-second study must teach Sylar the power");
                helper.assertTrue(PlayerAbilityService.getAbility(quarry, ModAbilities.QUAKE_ID).isPresent(), "A surviving victim keeps their original ability");
                helper.assertTrue(PowerOwnershipSavedData.get(quarry.getServer()).owner(ModAbilities.QUAKE_ID).orElseThrow().equals(quarry.getUUID()),
                        "NPC copying must never reassign the exclusive player story role");
                helper.succeed();
            } finally { remove(quarry); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "sylar.escape", timeoutTicks = 150)
    public static void breakingSightlineInterruptsTheAcquisition(GameTestHelper helper) {
        reset(helper); ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, new ArrayList<>());
        SylarEntity hunter = hunter(helper, quarry, 6); hunter.hunt(quarry);
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(hunter.extracting(), "Fixture must reach the hold");
            for (int y = 0; y <= 3; y++) for (int z = -2; z <= 2; z++)
                helper.getLevel().setBlock(quarry.blockPosition().offset(3, y, z), Blocks.STONE.defaultBlockState(), 3);
        });
        helper.runAfterDelay(110, () -> {
            try {
                helper.assertTrue(!hunter.extracting() && !SylarStoryData.get(quarry.getServer()).knows(ModAbilities.QUAKE_ID),
                        "A real wall between victim and hunter must stop the acquisition"); helper.succeed();
            } finally { remove(quarry); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "sylar.freeze", timeoutTicks = 215)
    public static void freezingSylarPausesHisAcquisition(GameTestHelper helper) {
        reset(helper); ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, new ArrayList<>());
        SylarEntity hunter = hunter(helper, quarry, 6); hunter.hunt(quarry); int[] progress = {0};
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(hunter.extracting(), "Fixture must reach the hold before freezing");
            progress[0] = hunter.extractionTicks();
            helper.assertTrue(CryoService.freezeSolid(hunter, 80, quarry), "Sylar must be vulnerable to Cryokinesis");
        });
        helper.runAfterDelay(145, () -> {
            helper.assertTrue(hunter.extractionTicks() == progress[0], "Acquisition must not secretly advance while the hunter is frozen");
        });
        helper.runAfterDelay(195, () -> {
            try {
                helper.assertTrue(hunter.extractionTicks() > progress[0] && !SylarStoryData.get(quarry.getServer()).knows(ModAbilities.QUAKE_ID),
                        "Acquisition resumes with its remaining channel, not an instant catch-up hit"); helper.succeed();
            } finally { CryoService.clear(); remove(quarry); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "sylar.saved_singleton")
    public static void storyPersistsAndTheWorldCannotSpawnTwoHunters(GameTestHelper helper) {
        reset(helper); ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, new ArrayList<>());
        SylarEntity hunter = hunter(helper, quarry, 10);
        try {
            helper.assertTrue(!SylarStoryService.beginAt(quarry, quarry.position().add(15, 0, 0), true), "Only one Sylar may exist across encounters");
            var data = SylarStoryData.get(quarry.getServer());
            var trail = data.trail(quarry.getUUID()); trail.chapter = 3; trail.attention = 9; trail.nextEncounter = 12345;
            data.learn(ModAbilities.CRYOKINESIS_ID); data.defeated(helper.getLevel().getGameTime());
            var restored = SylarStoryData.load(data.save(new CompoundTag(), quarry.getServer().registryAccess()), quarry.getServer().registryAccess());
            helper.assertTrue(restored.trail(quarry.getUUID()).chapter == 3 && restored.trail(quarry.getUUID()).attention == 9
                    && restored.trail(quarry.getUUID()).nextEncounter == 12345 && restored.knows(ModAbilities.CRYOKINESIS_ID)
                    && restored.defeats() == 1 && restored.quietUntil() > helper.getLevel().getGameTime(), "Story progress, learned powers and defeat cooldown must survive serialization");
            SylarStoryService.forget(quarry);
            helper.assertTrue(SylarStoryService.hunter() == null && hunter.isRemoved(), "Logout/dimension cleanup must remove the active hunter without duplicate NPCs");
            helper.succeed();
        } finally { remove(quarry); }
    }

    @GameTest(template = "rewind_empty", batch = "sylar.pacing", timeoutTicks = 110)
    public static void naturalStoryStartsWithGraceAndOneHintRatherThanAnAmbush(GameTestHelper helper) {
        reset(helper); List<Packet<?>> packets = new ArrayList<>();
        ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, packets);
        // Other fixtures also build distant platforms. Keep this no-surface test above their height band.
        quarry.setPos(quarry.position().add(0, 80, 0));
        SylarStoryService.notice(quarry);
        helper.runAfterDelay(25, () -> {
            var trail = SylarStoryData.get(quarry.getServer()).trail(quarry.getUUID());
            helper.assertTrue(trail.chapter == 0 && trail.nextEncounter > helper.getLevel().getGameTime() + 20 * 250,
                    "Newly eligible players must receive the configured five-minute grace");
            trail.nextEncounter = helper.getLevel().getGameTime() - 1;
        });
        helper.runAfterDelay(55, () -> {
            var trail = SylarStoryData.get(quarry.getServer()).trail(quarry.getUUID());
            helper.assertTrue(trail.chapter == 1 && messages(packets, "story.heroesevolved.sylar.watched") == 1,
                    "The first due chapter must produce exactly one uneasy chat hint");
            helper.assertTrue(SylarStoryService.hunter() == null && trail.nextEncounter > helper.getLevel().getGameTime() + 20 * 150,
                    "The first hint must not spawn an attacker or immediately repeat");
            trail.nextEncounter = helper.getLevel().getGameTime() - 1;
        });
        helper.runAfterDelay(90, () -> {
            try {
                helper.assertTrue(SylarStoryService.hunter() == null && SylarStoryData.get(quarry.getServer()).trail(quarry.getUUID()).nextEncounter > helper.getLevel().getGameTime(),
                        "With no safe loaded ground near this floating fixture, the director must postpone instead of spawning inside terrain");
                helper.succeed();
            } finally { remove(quarry); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "sylar.time_stop", timeoutTicks = 260)
    public static void stoppedTimePausesTheActualHunterChannel(GameTestHelper helper) {
        reset(helper); ServerPlayer quarry = player(helper, ModAbilities.TIME_MANIPULATION_ID, 5, new ArrayList<>());
        SylarEntity hunter = hunter(helper, quarry, 6); hunter.hunt(quarry); int[] progress = {0};
        long began = helper.getLevel().getGameTime();
        boolean[] cleaned = {false};
        Runnable cleanup = () -> {
            if (cleaned[0]) return;
            cleaned[0] = true;
            com.jayptucker.heroesevolved.time.TimeStopService.stop(quarry); remove(quarry);
        };
        helper.startSequence()
                // Forced chunks become entity-ticking asynchronously; wait for eighty actual hunter ticks.
                .thenWaitUntil(() -> {
                    if (!hunter.extracting() && helper.getLevel().getGameTime() - began >= 180) cleanup.run();
                    helper.assertTrue(hunter.extracting(), "Fixture must reach the hold before stopping time");
                })
                .thenExecute(() -> {
                    progress[0] = hunter.extractionTicks();
                    com.jayptucker.heroesevolved.time.TimeStopService.start(quarry, 3);
                })
                .thenExecuteAfter(30, () -> {
                    try {
                        helper.assertTrue(hunter.extractionTicks() == progress[0], "Sylar's acquisition must stop with his entity, not run from an independent server timer");
                        com.jayptucker.heroesevolved.time.TimeStopService.stop(quarry);
                    } catch (RuntimeException | Error failure) { cleanup.run(); throw failure; }
                })
                .thenExecuteAfter(30, () -> {
                    try {
                        helper.assertTrue(hunter.extractionTicks() > progress[0] && !SylarStoryData.get(quarry.getServer()).knows(ModAbilities.TIME_MANIPULATION_ID),
                                "Time restarting must resume the remaining hold without instant acquisition"); helper.succeed();
                    } finally { cleanup.run(); }
                });
    }

    @GameTest(template = "rewind_empty", batch = "sylar.mortal")
    public static void defeatingTheRealHunterStartsAWorldWideReprieve(GameTestHelper helper) {
        reset(helper); ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, new ArrayList<>());
        SylarEntity hunter = hunter(helper, quarry, 12);
        try {
            hunter.hurt(helper.getLevel().damageSources().playerAttack(quarry), 1000);
            var story = SylarStoryData.get(quarry.getServer());
            helper.assertTrue(!hunter.isAlive() && SylarStoryService.hunter() == null && story.defeats() == 1
                    && story.quietUntil() >= helper.getLevel().getGameTime() + 20 * 60 * 29,
                    "Sylar must be killable and his defeat must grant a saved thirty-minute world-wide reprieve");
            helper.succeed();
        } finally { remove(quarry); }
    }

    @GameTest(template = "rewind_empty", batch = "sylar.surface_preview")
    public static void operatorPreviewFindsSafeGroundOutsideThePlayersCentralView(GameTestHelper helper) {
        reset(helper); ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, new ArrayList<>());
        var positions = net.minecraft.util.RandomSource.create(99123);
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = positions.nextDouble() * Math.PI * 2, radius = 40 + positions.nextInt(25);
            BlockPos floor = BlockPos.containing(quarry.getX() + Math.cos(angle) * radius, quarry.getY() - 1,
                    quarry.getZ() + Math.sin(angle) * radius);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                BlockPos pos = floor.offset(x, 0, z); ChunkPos chunk = new ChunkPos(pos);
                if (!helper.getLevel().getForcedChunks().contains(chunk.toLong())) {
                    helper.getLevel().setChunkForced(chunk.x, chunk.z, true);
                    CHUNKS.computeIfAbsent(quarry.getUUID(), ignored -> new HashSet<>()).add(chunk);
                }
                helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            }
        }
        quarry.getRandom().setSeed(99123);
        try {
            helper.assertTrue(SylarStoryService.start(quarry, false), "The operator preview must find actual safe, loaded surface ground");
            SylarEntity hunter = SylarStoryService.hunter();
            helper.assertTrue(helper.getLevel().getBlockState(hunter.blockPosition().below()).is(Blocks.STONE)
                    && helper.getLevel().noCollision(hunter), "The distant watcher must stand above solid ground with a clear body volume");
            helper.assertTrue(quarry.getLookAngle().dot(hunter.getEyePosition().subtract(quarry.getEyePosition()).normalize()) <= .65,
                    "The hunter must appear beyond central view, ready to be discovered by turning around");
            helper.succeed();
        } finally { remove(quarry); }
    }

    // ------------------------------------------------------------------
    // The reworked fight
    // ------------------------------------------------------------------

    private static long speech(List<Packet<?>> packets) {
        return packets.stream().filter(packet -> packet instanceof ClientboundSystemChatPacket chat
                && chat.content().getString().startsWith("Sylar:")).count();
    }

    @GameTest(template = "rewind_empty", batch = "sylar.boss", timeoutTicks = 40)
    public static void aHuntShowsABossBarSizedToWhatHeHasTaken(GameTestHelper helper) {
        reset(helper); List<Packet<?>> packets = new ArrayList<>();
        ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, packets);
        var story = SylarStoryData.get(quarry.getServer());
        story.learn(ModAbilities.ELECTROKINESIS_ID); story.learn(ModAbilities.CRYOKINESIS_ID); story.learn(ModAbilities.FLIGHT_ID);
        SylarEntity hunter = hunter(helper, quarry, 10); hunter.hunt(quarry);
        helper.assertTrue(hunter.getMaxHealth() == 80 + 3 * 12 && hunter.getHealth() == hunter.getMaxHealth(), "Health grows with every power he has taken");
        helper.runAfterDelay(12, () -> {
            try {
                helper.assertTrue(hunter.bar().isVisible() && hunter.bar().getPlayers().contains(quarry), "The quarry sees his boss bar");
                hunter.setHealth(hunter.getMaxHealth() * 0.45F);
                helper.runAfterDelay(2, () -> {
                    try {
                        helper.assertTrue(hunter.phase() == 2, "Below half, the second phase: Hunger");
                        helper.succeed();
                    } finally { remove(quarry); }
                });
            } catch (RuntimeException | AssertionError error) { remove(quarry); throw error; }
        });
    }

    @GameTest(template = "rewind_empty", batch = "sylar.moves", timeoutTicks = 40)
    public static void aPowerHeHasTakenIsARealTelegraphedMove(GameTestHelper helper) {
        reset(helper); List<Packet<?>> packets = new ArrayList<>();
        ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, packets);
        SylarStoryData.get(quarry.getServer()).learn(ModAbilities.ELECTROKINESIS_ID);
        SylarEntity hunter = hunter(helper, quarry, 6); hunter.hunt(quarry);
        float health = quarry.getHealth();
        helper.assertTrue(hunter.castNow("chain_lightning") && hunter.casting().equals("chain_lightning"), "He winds up chain lightning");
        helper.assertTrue(hunter.castPose() == SylarPowers.Pose.ONE_HAND, "With a hand raised, so you can read it");
        helper.assertTrue(quarry.getHealth() == health, "Nothing lands during the windup");
        helper.runAfterDelay(16, () -> {
            try {
                helper.assertTrue(health - quarry.getHealth() >= 5.9F, "Then the lightning lands: " + (health - quarry.getHealth()));
                helper.succeed();
            } finally { remove(quarry); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "sylar.thunder", timeoutTicks = 50)
    public static void thunderstrikeLandsWhereYouStoodSoYouCanStepOut(GameTestHelper helper) {
        reset(helper); List<Packet<?>> packets = new ArrayList<>();
        ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, packets);
        SylarStoryData.get(quarry.getServer()).learn(ModAbilities.ELECTROKINESIS_ID);
        SylarEntity hunter = hunter(helper, quarry, 8); hunter.hunt(quarry);
        float health = quarry.getHealth();
        helper.assertTrue(hunter.castNow("thunderstrike"), "He marks the spot");
        quarry.setPos(quarry.position().add(0, 0, 5));
        helper.runAfterDelay(34, () -> {
            try {
                helper.assertTrue(quarry.getHealth() == health, "Out of the mark, the strike misses");
                helper.succeed();
            } finally { remove(quarry); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "sylar.pin", timeoutTicks = 40)
    public static void hisPinHoldsYouUntilYouHitHim(GameTestHelper helper) {
        reset(helper); List<Packet<?>> packets = new ArrayList<>();
        ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, packets);
        SylarEntity hunter = hunter(helper, quarry, 6); hunter.hunt(quarry);
        helper.assertTrue(hunter.castNow("tk_pin"), "He raises both hands");
        helper.runAfterDelay(14, () -> {
            try {
                helper.assertTrue(hunter.pinning(), "You are held");
                hunter.hurt(helper.getLevel().damageSources().playerAttack(quarry), 2);
                helper.assertTrue(!hunter.pinning(), "Hitting him breaks his hold");
                helper.succeed();
            } finally { remove(quarry); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "sylar.stagger", timeoutTicks = 30)
    public static void aHardHitBreaksAWindup(GameTestHelper helper) {
        reset(helper); List<Packet<?>> packets = new ArrayList<>();
        ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, packets);
        SylarStoryData.get(quarry.getServer()).learn(ModAbilities.CRYOKINESIS_ID);
        SylarEntity hunter = hunter(helper, quarry, 6); hunter.hunt(quarry);
        float health = quarry.getHealth();
        helper.assertTrue(hunter.castNow("ice_lance"), "He winds up an ice lance");
        hunter.hurt(helper.getLevel().damageSources().playerAttack(quarry), 6);
        helper.assertTrue(hunter.casting().isEmpty() && hunter.castPose() == SylarPowers.Pose.NONE, "A hard hit breaks the windup");
        helper.runAfterDelay(14, () -> {
            try {
                helper.assertTrue(quarry.getHealth() == health, "So the lance never comes");
                helper.succeed();
            } finally { remove(quarry); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "sylar.regen", timeoutTicks = 140)
    public static void withRegenerationHeGetsBackUpOnce(GameTestHelper helper) {
        reset(helper); List<Packet<?>> packets = new ArrayList<>();
        ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, packets);
        SylarStoryData.get(quarry.getServer()).learn(ModAbilities.REGENERATION_ID);
        SylarEntity hunter = hunter(helper, quarry, 10); hunter.hunt(quarry);
        hunter.hurt(helper.getLevel().damageSources().playerAttack(quarry), 1000);
        helper.assertTrue(hunter.isAlive() && hunter.downed() && hunter.revived(), "The killing blow puts him down instead");
        helper.assertTrue(!hunter.hurt(helper.getLevel().damageSources().playerAttack(quarry), 5), "While his wounds close, nothing hurts him");
        helper.runAfterDelay(105, () -> {
            try {
                helper.assertTrue(!hunter.downed() && Math.abs(hunter.getHealth() - hunter.getMaxHealth() * 0.4F) < 2F, "He rises at two-fifths of his health");
                hunter.hurt(helper.getLevel().damageSources().playerAttack(quarry), 1000);
                helper.assertTrue(!hunter.isAlive() && SylarStoryData.get(quarry.getServer()).defeats() == 1, "Only once: the second killing blow is final");
                helper.succeed();
            } finally { remove(quarry); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "sylar.flee", timeoutTicks = 240)
    public static void woundedWithAnEscapePowerHeTriesToRun(GameTestHelper helper) {
        reset(helper); List<Packet<?>> packets = new ArrayList<>();
        ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, packets);
        SylarStoryData.get(quarry.getServer()).learn(ModAbilities.INVISIBILITY_ID);
        SylarEntity hunter = hunter(helper, quarry, 10); hunter.hunt(quarry);
        hunter.setHealth(hunter.getMaxHealth() * 0.15F);
        long quiet = SylarStoryData.get(quarry.getServer()).quietUntil();
        helper.runAfterDelay(4, () -> helper.assertTrue(hunter.phase() == 3 && hunter.escapeIn() > 0, "At a fifth of his health he gets ready to run"));
        // He runs after four seconds of fighting at that health (other tests nearby can pause his ticks, so wait for it).
        helper.succeedWhen(() -> {
            var story = SylarStoryData.get(quarry.getServer());
            helper.assertTrue(hunter.isRemoved() && SylarStoryService.hunter() == null, "Not finished in time: he gets away (in " + hunter.escapeIn() + " ticks)");
            helper.assertTrue(story.trail(quarry.getUUID()).fled == 1 && story.defeats() == 0 && story.quietUntil() == quiet,
                    "Fleeing is not a defeat: no reprieve, and he remembers who did it");
            helper.assertTrue(SylarStoryService.opener(quarry).endsWith("return_fled"), "Next time, he opens with that");
            remove(quarry);
        });
    }

    @GameTest(template = "rewind_empty", batch = "sylar.voice", timeoutTicks = 200)
    public static void hisLinesArriveOneAtATimeEightSecondsApart(GameTestHelper helper) {
        reset(helper); List<Packet<?>> packets = new ArrayList<>();
        ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, packets);
        SylarVoice.say(quarry, "story.heroesevolved.sylar.say.first");
        SylarVoice.say(quarry, "story.heroesevolved.sylar.say.want.quake");
        SylarVoice.say(quarry, "story.heroesevolved.sylar.say.phase2");
        helper.assertTrue(speech(packets) == 1 && SylarVoice.pending(quarry) == 2, "One line now, the rest wait their turn");
        helper.runAfterDelay(SylarVoice.GAP_TICKS / 2, () -> helper.assertTrue(speech(packets) == 1, "Nothing more for eight seconds"));
        helper.runAfterDelay(SylarVoice.GAP_TICKS + 25, () -> {
            try {
                helper.assertTrue(speech(packets) == 2, "Then the next line: " + speech(packets));
                helper.assertTrue(SylarStoryService.opener(quarry).equals("story.heroesevolved.sylar.say.want.quake"), "He names what he wants from you");
                helper.succeed();
            } finally { remove(quarry); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "sylar.ladder", timeoutTicks = 60)
    public static void escapingAHuntCountsAndTheNextOneIsFarOff(GameTestHelper helper) {
        reset(helper); List<Packet<?>> packets = new ArrayList<>();
        ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, packets);
        SylarEntity hunter = hunter(helper, quarry, 10); hunter.hunt(quarry);
        quarry.setPos(quarry.position().add(130, 0, 0));
        helper.runAfterDelay(25, () -> {
            try {
                var trail = SylarStoryData.get(quarry.getServer()).trail(quarry.getUUID());
                helper.assertTrue(SylarStoryService.hunter() == null && trail.hunts == 1 && trail.escapes == 1 && trail.chapter == 4,
                        "Getting away counts as surviving a hunt: hunts=" + trail.hunts + " escapes=" + trail.escapes + " chapter=" + trail.chapter);
                helper.assertTrue(trail.nextEncounter > helper.getLevel().getGameTime() + 20L * 1200, "And the next hunt is at least twenty minutes off");
                helper.succeed();
            } finally { remove(quarry); }
        });
    }
}
