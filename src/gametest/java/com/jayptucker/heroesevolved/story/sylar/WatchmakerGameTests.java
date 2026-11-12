package com.jayptucker.heroesevolved.story.sylar;

import com.jayptucker.heroesevolved.PowerOwnershipTestSupport;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.intro.PowerIntroHistory;
import com.jayptucker.heroesevolved.intro.PowerIntroService;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.*;

/**
 * The Watchmaker: Sylar's boss fight in his clock shop. The shop and its door, the phases at three-fifths and
 * three-tenths, the grab and breaking free (or losing your power until the fight ends), his watch cracking under a
 * charged hit, the rings of stopped time and their gap, the chase, and the watch he leaves behind.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class WatchmakerGameTests {
    private static final Map<UUID, Set<ChunkPos>> CHUNKS = new HashMap<>();
    /** The players the running test made (each Watchmaker test runs alone in its batch). */
    private static final List<ServerPlayer> LIVE = new ArrayList<>();

    @SuppressWarnings("unchecked") private static <T> T field(Object object, Class<?> type, String name) {
        try { Field field = type.getDeclaredField(name); field.setAccessible(true); return (T) field.get(object); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    /** A listed survival player with a power, who can be hurt at once and can cross into the shop and back. */
    private static ServerPlayer player(GameTestHelper helper, ResourceLocation power, int x, String name) {
        List<Packet<?>> packets = new ArrayList<>();
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name)) {
            @Override public boolean isInvulnerableTo(DamageSource source) { return false; }
            @Override public void teleportTo(ServerLevel level, double px, double py, double pz, float yaw, float pitch) {
                super.teleportTo(level, px, py, pz, yaw, pitch);
                absMoveTo(px, py, pz, yaw, pitch);
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
        WatchmakerGameTests.<List<ServerPlayer>>field(player.getServer().getPlayerList(), PlayerList.class, "players").add(player);
        WatchmakerGameTests.<Map<UUID, ServerPlayer>>field(player.getServer().getPlayerList(), PlayerList.class, "playersByUUID").put(player.getUUID(), player);
        player.setData(ModDataAttachments.POWER_INTRO_HISTORY, PowerIntroHistory.empty().with(power));
        PlayerAbilityService.grantAbility(player, power);
        LIVE.add(player);
        return player;
    }

    private static void reset(GameTestHelper helper) {
        // Anyone a timed-out test left behind goes now, so their powers are free again.
        if (!LIVE.isEmpty()) remove(LIVE.toArray(ServerPlayer[]::new));
        SylarStoryService.clear(helper.getLevel().getServer());
        helper.getLevel().getServer().overworld().getDataStorage().set(SylarStoryData.NAME, new SylarStoryData());
    }

    private static void remove(ServerPlayer... players) {
        if (players.length == 0) return;
        SylarStoryService.clear(players[0].getServer());
        for (ServerPlayer player : players) {
            if (!LIVE.remove(player)) continue;
            PowerOwnershipTestSupport.release(player); PowerIntroService.forget(player);
            com.jayptucker.heroesevolved.time.TimeRewindService.forget(player);
            SylarsWatchItem.forget(player.getUUID());
            WatchmakerGameTests.<List<ServerPlayer>>field(player.getServer().getPlayerList(), PlayerList.class, "players").remove(player);
            WatchmakerGameTests.<Map<UUID, ServerPlayer>>field(player.getServer().getPlayerList(), PlayerList.class, "playersByUUID").remove(player.getUUID());
            player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            var chunks = CHUNKS.remove(player.getUUID());
            if (chunks != null) for (var chunk : chunks) helper(player).setChunkForced(chunk.x, chunk.z, false);
        }
    }

    private static ServerLevel helper(ServerPlayer player) { return player.getServer().overworld(); }

    /** Starts a hunt and takes it into the shop. Returns the Sylar there. */
    private static SylarEntity inShop(GameTestHelper helper, ServerPlayer quarry) {
        helper.assertTrue(WatchmakerArena.available(quarry.getServer()), "The shop's dimension must load from the datapack");
        helper.assertTrue(SylarStoryService.beginAt(quarry, quarry.position().add(10, 0, 0), false), "Fixture must create the hunter");
        helper.assertTrue(SylarStoryService.huntNow(quarry), "The hunt starts");
        SylarEntity sylar = SylarStoryService.hunter();
        helper.assertTrue(sylar != null && sylar.arena() && sylar.hunting() && WatchmakerArena.inShop(sylar), "He fights in his shop");
        helper.assertTrue(WatchmakerArena.inShop(quarry), "He took the quarry with him: " + quarry.level().dimension().location());
        return sylar;
    }

    private static void stand(ServerPlayer player, Vec3 at) {
        player.teleportTo((ServerLevel) player.level(), at.x, at.y, at.z, 0, 0);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static Vec3 floor(double dx, double dz) { return WatchmakerArena.center().add(dx, 0, dz); }

    /** A test step that, if it fails, takes its players with it (a player left behind keeps their power from every later test). */
    private static Runnable guard(Runnable step) {
        return () -> {
            try { step.run(); }
            catch (RuntimeException | AssertionError error) { remove(LIVE.toArray(ServerPlayer[]::new)); throw error; }
        };
    }

    /** A freshly loaded pocket chunk takes a few ticks before the entities in it tick. */
    private static boolean ticking(SylarEntity sylar) {
        return ((ServerLevel) sylar.level()).isPositionEntityTicking(sylar.blockPosition());
    }

    @GameTest(template = "rewind_empty", batch = "watchmaker.shop", timeoutTicks = 300)
    public static void heTakesYouToHisShopAndTheOpenDoorIsAWayHome(GameTestHelper helper) {
        reset(helper);
        ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, "watch-quarry");
        ServerPlayer ally = player(helper, ModAbilities.CRYOKINESIS_ID, 7, "watch-ally");
        Vec3 home = quarry.position();
        try {
            inShop(helper, quarry);
            helper.assertTrue(WatchmakerArena.inShop(ally), "Someone fighting beside them comes too");
            ServerLevel shop = WatchmakerArena.level(quarry.getServer());
            helper.assertTrue(shop.getBlockState(WatchmakerArena.ORIGIN).is(Blocks.CHISELED_POLISHED_BLACKSTONE), "The clock face is built round its middle");
            helper.assertTrue(!WatchmakerArena.locked() && WatchmakerArena.marked("door:front").stream().allMatch(p -> shop.getBlockState(p).isAir()),
                    "In the first phase the front door stands open");
            helper.assertTrue(WatchmakerArena.rails().size() == 2 && !WatchmakerArena.marked("case").isEmpty(), "Two gear rails and the glass cases are marked");
            stand(quarry, Vec3.atBottomCenterOf(WatchmakerArena.cell(14, 2, 72)));
        } catch (RuntimeException | AssertionError error) { remove(quarry, ally); throw error; }
        helper.runAfterDelay(60, () -> {
            try {
                var trail = SylarStoryData.get(quarry.getServer()).trail(quarry.getUUID());
                helper.assertTrue(SylarStoryService.hunter() == null && trail.escapes == 1, "Walking out the open door is getting away: escapes=" + trail.escapes);
                helper.assertTrue(!WatchmakerArena.inShop(quarry) && quarry.position().distanceToSqr(home) < 0.01,
                        "Everyone goes home to where they stood: " + quarry.level().dimension().location() + " " + quarry.position());
                helper.assertTrue(!WatchmakerArena.inShop(ally), "The ally too");
                helper.succeed();
            } finally { remove(quarry, ally); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "watchmaker.phases", timeoutTicks = 320)
    public static void phasesTurnAtThreeFifthsAndThreeTenthsAndHeRunsAtATenth(GameTestHelper helper) {
        reset(helper);
        ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, "watch-phases");
        SylarEntity[] sylar = new SylarEntity[1];
        try {
            sylar[0] = inShop(helper, quarry);
            helper.assertTrue(sylar[0].phase() == 1, "He opens Taking Inventory");
            sylar[0].setHealth(sylar[0].getMaxHealth() * 0.65F);
        } catch (RuntimeException | AssertionError error) { remove(quarry); throw error; }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ticking(sylar[0]), "He comes alive in his shop"))
                .thenExecuteAfter(3, guard(() -> {
                    helper.assertTrue(sylar[0].phase() == 1, "Still Taking Inventory above three-fifths");
                    sylar[0].setHealth(sylar[0].getMaxHealth() * 0.55F);
                }))
                .thenExecuteAfter(3, guard(() -> {
                    ServerLevel shop = WatchmakerArena.level(quarry.getServer());
                    helper.assertTrue(sylar[0].phase() == 2 && WatchmakerArena.locked(), "Below three-fifths, The Hunger: the doors lock");
                    helper.assertTrue(WatchmakerArena.marked("door:front").stream().allMatch(p -> shop.getBlockState(p).is(Blocks.IRON_BARS)),
                            "Barred");
                    sylar[0].setHealth(sylar[0].getMaxHealth() * 0.25F);
                }))
                .thenExecuteAfter(3, guard(() -> {
                    helper.assertTrue(sylar[0].phase() == 3, "Below three-tenths, Clockwork");
                    helper.assertTrue(!sylar[0].fleeing(), "Not running yet");
                    sylar[0].setHealth(sylar[0].getMaxHealth() * 0.08F);
                }))
                .thenExecuteAfter(3, guard(() -> {
                    ServerLevel shop = WatchmakerArena.level(quarry.getServer());
                    helper.assertTrue(sylar[0].fleeing() && WatchmakerArena.backOpen(), "At a tenth he runs, and the back door bursts");
                    helper.assertTrue(WatchmakerArena.marked("door:back").stream().allMatch(p -> shop.getBlockState(p).isAir()), "The boards are gone");
                    // Carry him down the alley as if he ran it.
                    var route = WatchmakerArena.chaseRoute();
                    for (int i = 0; i < route.size(); i++) {
                        Vec3 at = route.get(i);
                        helper.runAfterDelay(1 + i * 3L, () -> sylar[0].teleportTo(at.x, at.y, at.z));
                    }
                }))
                .thenWaitUntil(() -> helper.assertTrue(SylarStoryService.hunter() == null, "He reaches the end of the alley"))
                .thenExecute(guard(() -> {
                    try {
                        var story = SylarStoryData.get(quarry.getServer());
                        helper.assertTrue(story.trail(quarry.getUUID()).fled == 1 && story.defeats() == 0, "He got away: fled, not defeated");
                        helper.succeed();
                    } finally { remove(quarry); }
                }));
    }

    @GameTest(template = "rewind_empty", batch = "watchmaker.grab", timeoutTicks = 260)
    public static void hisGrabHoldsYouUntilYouStruggleFree(GameTestHelper helper) {
        reset(helper);
        ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, "watch-grabbed");
        SylarEntity[] sylar = new SylarEntity[1];
        try {
            sylar[0] = inShop(helper, quarry);
            sylar[0].setHealth(sylar[0].getMaxHealth() * 0.5F);
        } catch (RuntimeException | AssertionError error) { remove(quarry); throw error; }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ticking(sylar[0]), "He comes alive in his shop"))
                .thenExecuteAfter(3, guard(() -> {
                    helper.assertTrue(sylar[0].phase() == 2, "The Hunger");
                    stand(quarry, sylar[0].position().add(0, 0, 4));
                    helper.assertTrue(sylar[0].castNow("grab") && sylar[0].exposed(), "He winds up his lunge, his watch showing");
                }))
                .thenExecuteAfter(WatchmakerMoves.GRAB.windup() + 2, guard(() -> {
                    helper.assertTrue(quarry.getUUID().equals(sylar[0].holding()), "He has you");
                    helper.assertTrue(quarry.position().distanceToSqr(sylar[0].position()) < 4, "Held in front of him");
                    // Struggle: a hit on him counts half its damage, sneak presses count one each.
                    sylar[0].invulnerableTime = 0;
                    sylar[0].hurt(quarry.damageSources().playerAttack(quarry), 4);
                    helper.assertTrue(sylar[0].struggle() >= 2 && quarry.getUUID().equals(sylar[0].holding()), "Not free yet: " + sylar[0].struggle());
                    quarry.setShiftKeyDown(true);
                }))
                .thenExecuteAfter(1, guard(() -> quarry.setShiftKeyDown(false)))
                .thenExecuteAfter(1, guard(() -> {
                    helper.assertTrue(sylar[0].struggle() >= 3, "A sneak press counts: " + sylar[0].struggle());
                    sylar[0].struggle(3);
                }))
                .thenExecuteAfter(2, guard(() -> {
                    try {
                        helper.assertTrue(sylar[0].holding() == null && sylar[0].staggered(), "You tear free, and he reels");
                        helper.assertTrue(!WatchmakerArena.stolen(quarry) && PowerRules.canAct(quarry), "Your power is still yours");
                        helper.succeed();
                    } finally { remove(quarry); }
                }));
    }

    @GameTest(template = "rewind_empty", batch = "watchmaker.taken", timeoutTicks = 320)
    public static void tooSlowAndHeTakesYourPowerUntilTheFightEnds(GameTestHelper helper) {
        reset(helper);
        ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, "watch-taken");
        SylarEntity[] sylar = new SylarEntity[1];
        try {
            sylar[0] = inShop(helper, quarry);
            sylar[0].setHealth(sylar[0].getMaxHealth() * 0.5F);
        } catch (RuntimeException | AssertionError error) { remove(quarry); throw error; }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ticking(sylar[0]), "He comes alive in his shop"))
                .thenExecuteAfter(3, guard(() -> {
                    stand(quarry, sylar[0].position().add(0, 0, 4));
                    helper.assertTrue(sylar[0].castNow("grab"), "He lunges");
                }))
                .thenExecuteAfter(WatchmakerMoves.GRAB.windup() + 2, guard(() -> helper.assertTrue(quarry.getUUID().equals(sylar[0].holding()), "He has you")))
                .thenExecuteAfter(SylarEntity.HOLD_TICKS + 2, guard(() -> {
                    helper.assertTrue(sylar[0].holding() == null, "He lets go");
                    helper.assertTrue(WatchmakerArena.stolen(quarry) && !PowerRules.canAct(quarry), "Your power is his for the rest of the fight");
                    helper.assertTrue(sylar[0].taken().contains(ModAbilities.QUAKE_ID), "And he uses it: " + sylar[0].taken());
                    SylarStoryService.dismiss(quarry.getServer());
                    helper.assertTrue(!WatchmakerArena.stolen(quarry) && PowerRules.canAct(quarry), "When the fight ends, it comes back");
                }))
                .thenWaitUntil(() -> helper.assertTrue(!WatchmakerArena.inShop(quarry), "Sent home"))
                .thenExecute(guard(() -> {
                    try {
                        helper.assertTrue(PlayerAbilityService.getData(quarry).assignedPower().map(e -> e.getKey().equals(ModAbilities.QUAKE_ID)).orElse(false),
                                "Still their power: he copies, he never takes a player's role");
                        helper.succeed();
                    } finally { remove(quarry); }
                }));
    }

    @GameTest(template = "rewind_empty", batch = "watchmaker.crack", timeoutTicks = 260)
    public static void aChargedHitOnHisExposedWatchStaggersHim(GameTestHelper helper) {
        reset(helper);
        ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, "watch-cracker");
        SylarEntity[] sylar = new SylarEntity[1];
        try {
            sylar[0] = inShop(helper, quarry);
        } catch (RuntimeException | AssertionError error) { remove(quarry); throw error; }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ticking(sylar[0]), "He comes alive in his shop"))
                .thenExecute(guard(() -> {
                    SylarEntity s = sylar[0];
                    stand(quarry, s.position().add(0, 0, 3));
                    // Charged, but his watch is hidden: nothing.
                    WatchmakerEvents.swing(quarry);
                    s.invulnerableTime = 0;
                    s.hurt(quarry.damageSources().playerAttack(quarry), 3);
                    helper.assertTrue(!s.staggered() && s.cracks() == 0, "A charged hit does nothing to a watch he isn't showing");
                    helper.assertTrue(s.castNow("floor_slam") && s.exposed(), "Winding up, his watch shows");
                    // An ordinary light hit: the windup goes on.
                    s.invulnerableTime = 0;
                    s.hurt(quarry.damageSources().playerAttack(quarry), 2);
                    helper.assertTrue(!s.staggered() && s.casting().equals("floor_slam"), "A light, uncharged hit doesn't crack it");
                    // Charged: the watch cracks.
                    WatchmakerEvents.swing(quarry);
                    s.invulnerableTime = 0;
                    s.hurt(quarry.damageSources().playerAttack(quarry), 3);
                    helper.assertTrue(s.staggered() && s.cracks() == 1 && s.casting().isEmpty() && s.castPose() == SylarPowers.Pose.STAGGER,
                            "A charged hit cracks his watch: the slam is gone and he staggers");
                    // Reeling, he takes more from every hit.
                    float before = s.getHealth();
                    s.invulnerableTime = 0;
                    s.hurt(quarry.damageSources().playerAttack(quarry), 4);
                    helper.assertTrue(Math.abs(before - s.getHealth() - 5) < 0.6F, "A quarter more while he reels: " + (before - s.getHealth()));
                }))
                .thenExecuteAfter(SylarEntity.STAGGER_TICKS + 4, guard(() -> {
                    try {
                        helper.assertTrue(!sylar[0].staggered(), "He recovers after three seconds");
                        helper.succeed();
                    } finally { remove(quarry); }
                }));
    }

    @GameTest(template = "rewind_empty", batch = "watchmaker.rings", timeoutTicks = 320)
    public static void ringsOfStoppedTimeSpareOnlyTheGap(GameTestHelper helper) {
        reset(helper);
        ServerPlayer safe = player(helper, ModAbilities.QUAKE_ID, 5, "watch-gap");
        ServerPlayer caught = player(helper, ModAbilities.CRYOKINESIS_ID, 7, "watch-caught");
        SylarEntity[] sylar = new SylarEntity[1];
        float[] health = new float[2];
        try {
            helper.assertTrue(WatchmakerMoves.inGap(0, -5, 0) && !WatchmakerMoves.inGap(0, 5, 0) && WatchmakerMoves.inGap(2, -5, 0.3),
                    "The gap is the wedge either side of its hour");
            sylar[0] = inShop(helper, safe);
            sylar[0].setHealth(sylar[0].getMaxHealth() * 0.25F);
        } catch (RuntimeException | AssertionError error) { remove(safe, caught); throw error; }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ticking(sylar[0]), "He comes alive in his shop"))
                .thenExecuteAfter(3, guard(() -> {
                    helper.assertTrue(sylar[0].phase() == 3, "Clockwork");
                    Vec3 middle = WatchmakerArena.center();
                    sylar[0].teleportTo(middle.x, middle.y, middle.z);
                    helper.assertTrue(sylar[0].castNow("time_rings"), "He raises his arms: the clock face lights");
                    double gap = sylar[0].gap();
                    stand(safe, floor(Math.sin(gap) * 6, -Math.cos(gap) * 6));
                    stand(caught, floor(-Math.sin(gap) * 6, Math.cos(gap) * 6));
                    health[0] = safe.getHealth(); health[1] = caught.getHealth();
                }))
                .thenExecuteAfter(WatchmakerMoves.TIME_RINGS.windup() + 2, guard(() -> {
                    helper.assertTrue(sylar[0].ringAge() >= 0, "The rings are closing");
                    helper.assertTrue(safe.getHealth() == health[0] && caught.getHealth() == health[1], "Nothing lands until the ring reaches you");
                }))
                .thenExecuteAfter(com.jayptucker.heroesevolved.styles.Piece.WATCH_RINGS.ticks, guard(() -> {
                    try {
                        helper.assertTrue(caught.getHealth() < health[1] && sylar[0].timeStopped(caught), "Outside the gap, the ring catches you and holds you still: health "
                                + health[1] + " -> " + caught.getHealth() + " stopped=" + sylar[0].timeStopped(caught) + " at=" + caught.position()
                                + " dim=" + caught.level().dimension().location() + " gap=" + sylar[0].gap() + " ring=" + sylar[0].ringAge()
                                + " sylar=" + sylar[0].position() + " alive=" + sylar[0].isAlive());
                        // (He may have come for the one in the gap with his fists by now, so look at the ring, not their health.)
                        helper.assertTrue(!sylar[0].timeStopped(safe), "In the gap, it passes you by");
                        helper.succeed();
                    } finally { remove(safe, caught); }
                }));
    }

    @GameTest(template = "rewind_empty", batch = "watchmaker.moves", timeoutTicks = 280)
    public static void hisShopMovesAreTelegraphedThenLand(GameTestHelper helper) {
        reset(helper);
        ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, "watch-moves");
        SylarEntity[] sylar = new SylarEntity[1];
        float[] health = new float[1];
        Vec3[] slamAt = new Vec3[1];
        try {
            sylar[0] = inShop(helper, quarry);
            // Stand on the north gear rail.
            Vec3[] rail = WatchmakerArena.rails().getFirst();
            stand(quarry, rail[0].lerp(rail[1], 0.3));
            health[0] = quarry.getHealth();
        } catch (RuntimeException | AssertionError error) { remove(quarry); throw error; }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ticking(sylar[0]), "He comes alive in his shop"))
                .thenExecute(guard(() -> helper.assertTrue(sylar[0].castNow("rail_arc"), "He reaches for the rail")))
                .thenExecuteAfter(WatchmakerMoves.RAIL_ARC.windup() - 4, guard(() -> helper.assertTrue(quarry.getHealth() == health[0], "Nothing yet: the light is still creeping along the rail")))
                .thenExecuteAfter(6, guard(() -> {
                    helper.assertTrue(health[0] - quarry.getHealth() >= 7, "Then lightning runs the rail: " + (health[0] - quarry.getHealth()));
                    quarry.setHealth(quarry.getMaxHealth());
                    stand(quarry, floor(3, 3));
                    health[0] = quarry.getHealth();
                    helper.assertTrue(sylar[0].castNow("floor_slam"), "He marks where you stand");
                    slamAt[0] = quarry.position();
                }))
                .thenExecuteAfter(WatchmakerMoves.FLOOR_SLAM.windup() + 2, guard(() -> {
                    try {
                        ServerLevel shop = WatchmakerArena.level(quarry.getServer());
                        helper.assertTrue(health[0] - quarry.getHealth() >= 6, "The slam lands: " + (health[0] - quarry.getHealth()));
                        BlockPos under = BlockPos.containing(slamAt[0].x, WatchmakerArena.ORIGIN.getY(), slamAt[0].z);
                        helper.assertTrue(shop.getBlockState(under).isAir() || shop.getBlockState(under).is(Blocks.COBBLED_DEEPSLATE),
                                "And rips up the floor: " + shop.getBlockState(under));
                        helper.succeed();
                    } finally { remove(quarry); }
                }));
    }

    @GameTest(template = "rewind_empty", batch = "watchmaker.drop", timeoutTicks = 40)
    public static void heLeavesHisWatchWhichRewindsOncePerLife(GameTestHelper helper) {
        reset(helper);
        ServerPlayer quarry = player(helper, ModAbilities.QUAKE_ID, 5, "watch-finder");
        try {
            helper.assertTrue(SylarStoryService.beginAt(quarry, quarry.position().add(8, 0, 0), false), "Fixture must create the hunter");
            SylarEntity sylar = SylarStoryService.hunter();
            sylar.setNoGravity(true);
            sylar.hunt(quarry);
            sylar.hurt(quarry.damageSources().playerAttack(quarry), 1000);
            helper.assertTrue(!sylar.isAlive(), "He falls");
            helper.assertTrue(quarry.getInventory().hasAnyMatching(s -> s.is(ModItems.SYLARS_WATCH.get())), "His watch is yours");

            long now = quarry.getServer().overworld().getGameTime();
            Vec3 then = quarry.position();
            SylarsWatchItem.forget(quarry.getUUID());
            quarry.setHealth(18);
            SylarsWatchItem.record(quarry, now - 60);
            quarry.setPos(then.add(3, 0, 0));
            quarry.setHealth(5);
            SylarsWatchItem.record(quarry, now - 2);
            helper.assertTrue(SylarsWatchItem.wound(quarry) && SylarsWatchItem.rewind(quarry), "The watch takes you back");
            helper.assertTrue(quarry.position().distanceToSqr(then) < 0.01 && quarry.getHealth() == 18,
                    "To where you stood, as hurt as you were then: " + quarry.position() + " " + quarry.getHealth());
            SylarsWatchItem.record(quarry, now - 1);
            SylarsWatchItem.record(quarry, now);
            helper.assertTrue(!SylarsWatchItem.wound(quarry) && !SylarsWatchItem.rewind(quarry), "Once a life");
            // (A FakePlayer ignores awardStat, so count the death by hand.)
            var deaths = Stats.CUSTOM.get(Stats.DEATHS);
            quarry.getStats().setValue(quarry, deaths, quarry.getStats().getValue(deaths) + 1);
            helper.assertTrue(SylarsWatchItem.wound(quarry), "A death winds it again");
            helper.succeed();
        } finally { remove(quarry); }
    }
}
