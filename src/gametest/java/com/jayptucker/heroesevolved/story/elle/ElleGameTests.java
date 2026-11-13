package com.jayptucker.heroesevolved.story.elle;

import com.jayptucker.heroesevolved.PowerOwnershipTestSupport;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.grid.GridDisruption;
import com.jayptucker.heroesevolved.grid.TestMachines;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Elle Bishop, sent by the Company: the omens the day before (lamps flicker, redstone misfires, machines switch
 * themselves), the arrival when the day runs out, the test floor's banks and their markers, the phases at 65% and 30%
 * (and the lights going out), the banks' shield, the backfire when a siphoned bank's breaker is pulled, a cut bank that
 * only thins her shield, grounding her reboot, the conduits sparing rubber, and what she leaves behind.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class ElleGameTests {
    private static final Map<UUID, Set<ChunkPos>> CHUNKS = new HashMap<>();
    private static final List<ServerPlayer> LIVE = new ArrayList<>();

    @SuppressWarnings("unchecked") private static <T> T field(Object object, Class<?> type, String name) {
        try { Field field = type.getDeclaredField(name); field.setAccessible(true); return (T) field.get(object); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    /** A listed survival player with a power, standing at {@code at} (relative to the test), who can be hurt at once. */
    private static ServerPlayer player(GameTestHelper helper, ResourceLocation power, BlockPos at, String name) {
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
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(at)));
        player.setGameMode(GameType.SURVIVAL);
        for (int dx = -16; dx <= 16; dx += 16) for (int dz = -16; dz <= 16; dz += 16) {
            ChunkPos chunk = new ChunkPos(player.blockPosition().offset(dx, 0, dz));
            if (!helper.getLevel().getForcedChunks().contains(chunk.toLong())) {
                helper.getLevel().setChunkForced(chunk.x, chunk.z, true);
                CHUNKS.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>()).add(chunk);
            }
            helper.getLevel().getChunk(chunk.x, chunk.z);
        }
        helper.getLevel().addNewPlayer(player);
        ElleGameTests.<List<ServerPlayer>>field(player.getServer().getPlayerList(), PlayerList.class, "players").add(player);
        ElleGameTests.<Map<UUID, ServerPlayer>>field(player.getServer().getPlayerList(), PlayerList.class, "playersByUUID").put(player.getUUID(), player);
        player.setData(ModDataAttachments.POWER_INTRO_HISTORY, PowerIntroHistory.empty().with(power));
        PlayerAbilityService.grantAbility(player, power);
        LIVE.add(player);
        return player;
    }

    private static void reset(GameTestHelper helper) {
        if (!LIVE.isEmpty()) remove(LIVE.toArray(ServerPlayer[]::new));
        ElleStoryService.clear(helper.getLevel().getServer());
        helper.getLevel().getServer().overworld().getDataStorage().set(CompanyVisitData.NAME, new CompanyVisitData());
    }

    private static void remove(ServerPlayer... players) {
        if (players.length == 0) return;
        ElleStoryService.clear(players[0].getServer());
        for (ServerPlayer player : players) {
            if (!LIVE.remove(player)) continue;
            PowerOwnershipTestSupport.release(player); PowerIntroService.forget(player);
            ElleGameTests.<List<ServerPlayer>>field(player.getServer().getPlayerList(), PlayerList.class, "players").remove(player);
            ElleGameTests.<Map<UUID, ServerPlayer>>field(player.getServer().getPlayerList(), PlayerList.class, "playersByUUID").remove(player.getUUID());
            player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            var chunks = CHUNKS.remove(player.getUUID());
            if (chunks != null) for (var chunk : chunks) player.getServer().overworld().setChunkForced(chunk.x, chunk.z, false);
        }
    }

    /** A test step that, if it fails, takes its players with it (a player left behind keeps their power from every later test). */
    private static Runnable guard(Runnable step) {
        return () -> {
            try { step.run(); }
            catch (RuntimeException | AssertionError error) { remove(LIVE.toArray(ServerPlayer[]::new)); throw error; }
        };
    }

    /** Straight onto the test floor with Elle. */
    private static ElleEntity onTheFloor(GameTestHelper helper, ServerPlayer subject) {
        helper.assertTrue(PrimatechArena.available(subject.getServer()), "The test floor's dimension must load from the datapack");
        ElleEntity elle = ElleStoryService.arriveNow(subject);
        helper.assertTrue(elle != null && ElleStoryService.elle() == elle && PrimatechArena.inArena(elle), "She is on the test floor");
        helper.assertTrue(PrimatechArena.inArena(subject), "And so is her subject");
        elle.holdOff(1000);
        elle.holdSurges(1000);
        return elle;
    }

    private static boolean ticking(Entity e) { return ((ServerLevel) e.level()).isPositionEntityTicking(e.blockPosition()); }

    /**
     * Elle is ticking and so is every corner of the floor. A freshly forced pocket chunk hides its entities from lookups
     * for a few seconds, so a player standing in one is invisible to her hazards until then.
     */
    private static boolean ready(ElleEntity elle) {
        ServerLevel room = (ServerLevel) elle.level();
        for (int dx : new int[] {-12, 12}) for (int dz : new int[] {-12, 12})
            if (!room.isPositionEntityTicking(PrimatechArena.cell(dx, 1, dz))) return false;
        return ticking(elle);
    }

    private static void stand(ServerPlayer player, Vec3 at) {
        player.teleportTo((ServerLevel) player.level(), at.x, at.y, at.z, 0, 0);
        player.setDeltaMovement(Vec3.ZERO);
        player.setOnGround(true);
    }

    private static void hit(ElleEntity elle, ServerPlayer by, float amount) {
        elle.invulnerableTime = 0;
        elle.hurt(by.damageSources().indirectMagic(by, by), amount);
    }

    // ------------------------------------------------------------------
    // The day before
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", batch = "elle.omens.lights", timeoutTicks = 120)
    public static void omensFlickerLampsThenPutThemBack(GameTestHelper helper) {
        reset(helper);
        BlockPos dark = new BlockPos(2, 2, 2), bulb = new BlockPos(4, 2, 2);
        helper.setBlock(dark, Blocks.REDSTONE_LAMP);
        helper.setBlock(bulb, Blocks.WAXED_COPPER_BULB.defaultBlockState().setValue(BlockStateProperties.LIT, true));
        ServerPlayer player = player(helper, ModAbilities.QUAKE_ID, new BlockPos(6, 2, 6), "omen-lights");
        List<BlockPos> hit = new ArrayList<>();
        try {
            hit.addAll(CompanyOmens.flicker(player));
            helper.assertTrue(hit.contains(helper.absolutePos(dark)) && hit.contains(helper.absolutePos(bulb)),
                    "Both lights near the player flicker: " + hit);
        } catch (RuntimeException | AssertionError error) { remove(player); throw error; }
        helper.startSequence()
                .thenExecuteAfter(1, guard(() -> {
                    // The first light stutters at once; the rest a moment later.
                    BlockPos first = hit.getFirst();
                    boolean wasLit = first.equals(helper.absolutePos(bulb));
                    helper.assertTrue(helper.getLevel().getBlockState(first).getValue(BlockStateProperties.LIT) != wasLit, "The first light has flipped");
                    helper.assertTrue(CompanyOmens.pending() > 0, "Its restoration is queued");
                }))
                .thenExecuteAfter(45, guard(() -> {
                    try {
                        helper.assertTrue(!helper.getBlockState(dark).getValue(BlockStateProperties.LIT), "The unpowered lamp is dark again, as it was");
                        helper.assertTrue(helper.getBlockState(bulb).getValue(BlockStateProperties.LIT), "The bulb is lit again, as it was");
                        helper.assertTrue(CompanyOmens.pending() == 0, "Nothing is left to put back");
                        helper.succeed();
                    } finally { remove(player); }
                }));
    }

    @GameTest(template = "rewind_empty", batch = "elle.omens.wiring", timeoutTicks = 160)
    public static void omensMisfireRedstoneAndSwitchMachines(GameTestHelper helper) {
        reset(helper);
        BlockPos power = new BlockPos(2, 2, 2), lamp = new BlockPos(3, 2, 2), furnace = new BlockPos(2, 2, 5);
        // The lamp first, so placing its power lights it.
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
        helper.setBlock(power, Blocks.REDSTONE_BLOCK);
        helper.setBlock(furnace, Blocks.FURNACE);
        BlockEntity entity = helper.getBlockEntity(furnace);
        EnergyStorage battery = TestMachines.power(entity, 100_000, 50_000);
        ServerPlayer player = player(helper, ModAbilities.QUAKE_ID, new BlockPos(6, 2, 6), "omen-wiring");
        try {
            helper.assertTrue(helper.getBlockState(lamp).getValue(BlockStateProperties.LIT), "Fixture: the lamp is powered and lit");
            List<BlockPos> misfired = CompanyOmens.misfire(player);
            helper.assertTrue(misfired.contains(helper.absolutePos(lamp)), "The lamp's circuit misfires: " + misfired);
            helper.assertTrue(GridDisruption.jammed(helper.getLevel(), helper.absolutePos(lamp)), "Its redstone is dead for a moment");
            List<BlockPos> machines = CompanyOmens.machines(player);
            helper.assertTrue(machines.contains(helper.absolutePos(furnace)), "The furnace switches by itself: " + machines);
            helper.assertTrue(GridDisruption.stalled(helper.getLevel(), helper.absolutePos(furnace)), "The machine has stopped");
            helper.assertTrue(helper.getBlockState(furnace).getValue(BlockStateProperties.LIT), "And the cold furnace has flared on");
            helper.assertTrue(battery.getEnergyStored() == 50_000, "An omen drains nothing");
        } catch (RuntimeException | AssertionError error) { remove(player); throw error; }
        helper.startSequence()
                .thenExecuteAfter(110, guard(() -> {
                    try {
                        helper.assertTrue(!GridDisruption.jammed(helper.getLevel(), helper.absolutePos(lamp))
                                && helper.getBlockState(lamp).getValue(BlockStateProperties.LIT), "The circuit wakes and the lamp is lit again");
                        helper.assertTrue(!GridDisruption.stalled(helper.getLevel(), helper.absolutePos(furnace)), "The machine runs again");
                        helper.assertTrue(!helper.getBlockState(furnace).getValue(BlockStateProperties.LIT), "And the furnace is cold again, as it was");
                        helper.succeed();
                    } finally { remove(player); }
                }));
    }

    @GameTest(template = "rewind_empty", batch = "elle.arrival", timeoutTicks = 260)
    public static void theDayOfOmensEndsWithHerArrival(GameTestHelper helper) {
        reset(helper);
        ServerPlayer player = player(helper, ModAbilities.QUAKE_ID, new BlockPos(6, 2, 6), "omen-arrival");
        Vec3 home = player.position();
        var data = CompanyVisitData.get(player.getServer());
        try {
            ElleStoryService.beginOmens(player);
            var visit = data.visit(player.getUUID());
            long now = player.getServer().overworld().getGameTime();
            helper.assertTrue(visit.omens(now) && visit.arriveAt - visit.omensAt == com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON.elleOmenTicks.get(),
                    "A day of omens begins, and she arrives when it ends");
            // Skip to the end of the day.
            visit.arriveAt = now + 1;
        } catch (RuntimeException | AssertionError error) { remove(player); throw error; }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ElleStoryService.arriving(player), "The day is up: she comes"))
                .thenExecute(guard(() -> helper.assertTrue(GridDisruption.jammed(helper.getLevel(), player.blockPosition()), "The lights go out around them")))
                .thenWaitUntil(() -> helper.assertTrue(PrimatechArena.inArena(player), "They wake on the test floor"))
                .thenExecute(guard(() -> {
                    try {
                        ElleEntity elle = ElleStoryService.elle();
                        helper.assertTrue(elle != null && !elle.isScene() && PrimatechArena.open(), "Elle is there to fight");
                        helper.assertTrue(data.visit(player.getUUID()).visits == 1 && !data.visit(player.getUUID()).omens(player.getServer().overworld().getGameTime()),
                                "One visit, and the omens are over");
                        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ElleEntity.class, new net.minecraft.world.phys.AABB(home, home).inflate(16)).isEmpty(),
                                "Her figure in the dark is gone from the Overworld");
                        ElleStoryService.dismiss(player.getServer());
                    } catch (RuntimeException | AssertionError error) { remove(player); throw error; }
                }))
                .thenWaitUntil(() -> helper.assertTrue(!PrimatechArena.inArena(player), "Dismissed, everyone goes home"))
                .thenExecute(guard(() -> {
                    try {
                        helper.assertTrue(player.position().distanceToSqr(home) < 0.01, "Back where they stood: " + player.position());
                        helper.succeed();
                    } finally { remove(player); }
                }));
    }

    // ------------------------------------------------------------------
    // The test floor
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", batch = "elle.floor", timeoutTicks = 80)
    public static void theTestFloorsMarkersMatchItsGeometry(GameTestHelper helper) {
        reset(helper);
        ServerLevel room = PrimatechArena.level(helper.getLevel().getServer());
        helper.assertTrue(room != null, "The dimension loads");
        PrimatechArena.build(room);
        Vec3 floor = PrimatechArena.floor();
        helper.assertTrue(room.getBlockState(PrimatechArena.ORIGIN).is(Blocks.WAXED_CUT_COPPER), "The pylon's pad is in the middle");
        for (int i = 0; i < PrimatechGeometry.BANK_COUNT; i++) {
            List<BlockPos> breaker = PrimatechArena.marked("breaker:" + i);
            helper.assertTrue(breaker.size() == 1 && breaker.getFirst().equals(PrimatechArena.breaker(i)),
                    "Bank " + i + "'s breaker is where the geometry says: " + breaker + " vs " + PrimatechArena.breaker(i));
            helper.assertTrue(PrimatechArena.closed(room, i), "Bank " + i + " starts closed");
            List<BlockPos> conduit = PrimatechArena.marked("conduit:" + i);
            helper.assertTrue(conduit.size() >= 5, "Bank " + i + " has a conduit");
            for (BlockPos cell : conduit)
                helper.assertTrue(PrimatechGeometry.fromConduit(floor, i, Vec3.atCenterOf(cell)) <= 0.8,
                        "Conduit " + i + "'s cells follow the strip that surges: " + cell);
            helper.assertTrue(PrimatechArena.marked("bulb:" + i).size() == 2, "Bank " + i + " has two lights");
        }
        helper.assertTrue(PrimatechArena.marked("mat").size() == 9 * PrimatechGeometry.MATS.length, "Four 3x3 rubber mats");
        for (int[] mat : PrimatechGeometry.MATS)
            helper.assertTrue(room.getBlockState(PrimatechArena.cell(mat[0], 0, mat[1])).is(Blocks.BLACK_WOOL), "A mat at " + Arrays.toString(mat));
        helper.assertTrue(PrimatechArena.marked("light").size() == 16, "Sixteen ceiling lights");
        helper.assertTrue(PrimatechArena.liveCount() == 6, "All six banks live");
        helper.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "elle.phases", timeoutTicks = 200)
    public static void phasesTurnAtSixtyFiveAndThirtyAndTheLightsGoOut(GameTestHelper helper) {
        reset(helper);
        ServerPlayer player = player(helper, ModAbilities.QUAKE_ID, new BlockPos(6, 2, 6), "elle-phases");
        ElleEntity[] elle = new ElleEntity[1];
        try { elle[0] = onTheFloor(helper, player); }
        catch (RuntimeException | AssertionError error) { remove(player); throw error; }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ready(elle[0]), "She and the whole floor come alive"))
                .thenExecuteAfter(3, guard(() -> {
                    helper.assertTrue(elle[0].phase() == 1 && elle[0].bar().isVisible(), "Field Test, with a boss bar");
                    elle[0].setHealth(elle[0].getMaxHealth() * 0.7F);
                }))
                .thenExecuteAfter(3, guard(() -> {
                    helper.assertTrue(elle[0].phase() == 1, "Still Field Test above 65%");
                    elle[0].setHealth(elle[0].getMaxHealth() * 0.6F);
                }))
                .thenExecuteAfter(3, guard(() -> {
                    helper.assertTrue(elle[0].phase() == 2, "Below 65%: Overcharged");
                    helper.assertTrue(!PrimatechArena.dark(), "The lights are still on");
                }))
                .thenWaitUntil(() -> helper.assertTrue(elle[0].surgeBank() >= 0, "Overcharged: a conduit warns of a surge"))
                .thenExecute(guard(() -> elle[0].setHealth(elle[0].getMaxHealth() * 0.25F)))
                .thenExecuteAfter(3, guard(() -> {
                    try {
                        ServerLevel room = PrimatechArena.level(player.getServer());
                        helper.assertTrue(elle[0].phase() == 3, "Below 30%: Blackout");
                        helper.assertTrue(PrimatechArena.dark() && PrimatechArena.marked("light").stream()
                                .noneMatch(p -> room.getBlockState(p).getValue(BlockStateProperties.LIT)), "The ceiling lights are all out");
                        helper.succeed();
                    } finally { remove(player); }
                }));
    }

    // ------------------------------------------------------------------
    // The counterplay
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", batch = "elle.shield", timeoutTicks = 160)
    public static void liveBanksShieldHerAndCuttingThemThinsIt(GameTestHelper helper) {
        reset(helper);
        ServerPlayer player = player(helper, ModAbilities.QUAKE_ID, new BlockPos(6, 2, 6), "elle-shield");
        ElleEntity[] elle = new ElleEntity[1];
        try { elle[0] = onTheFloor(helper, player); }
        catch (RuntimeException | AssertionError error) { remove(player); throw error; }
        float[] before = new float[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ready(elle[0]), "She and the whole floor come alive"))
                .thenExecuteAfter(2, guard(() -> {
                    helper.assertTrue(Math.abs(elle[0].shield() - 0.4F) < 1e-4, "Six live banks take 60% off: " + elle[0].shield());
                    before[0] = elle[0].getHealth();
                    hit(elle[0], player, 10);
                    helper.assertTrue(Math.abs(before[0] - elle[0].getHealth() - 4) < 0.01, "A 10 lands as 4: " + (before[0] - elle[0].getHealth()));
                    ServerLevel room = PrimatechArena.level(player.getServer());
                    for (int i : new int[] {0, 2, 4}) PrimatechArena.setBreaker(room, i, false);
                }))
                .thenExecuteAfter(2, guard(() -> {
                    try {
                        helper.assertTrue(PrimatechArena.liveCount() == 3 && PrimatechArena.bank(2) == PrimatechArena.Bank.CUT, "Three banks cut off");
                        helper.assertTrue(elle[0].backfires() == 0, "Cutting a bank she isn't drinking only cuts it off");
                        before[0] = elle[0].getHealth();
                        hit(elle[0], player, 10);
                        helper.assertTrue(Math.abs(before[0] - elle[0].getHealth() - 7) < 0.01, "Now a 10 lands as 7: " + (before[0] - elle[0].getHealth()));
                        helper.succeed();
                    } finally { remove(player); }
                }));
    }

    @GameTest(template = "rewind_empty", batch = "elle.backfire", timeoutTicks = 220)
    public static void pullingTheBreakerMidSiphonBackfiresIntoHer(GameTestHelper helper) {
        reset(helper);
        ServerPlayer player = player(helper, ModAbilities.QUAKE_ID, new BlockPos(6, 2, 6), "elle-backfire");
        ElleEntity[] elle = new ElleEntity[1];
        float[] before = new float[1];
        try { elle[0] = onTheFloor(helper, player); }
        catch (RuntimeException | AssertionError error) { remove(player); throw error; }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ready(elle[0]), "She and the whole floor come alive"))
                .thenExecuteAfter(2, guard(() -> {
                    elle[0].setHealth(elle[0].getMaxHealth() * 0.9F);
                    helper.assertTrue(elle[0].siphonFrom(2) && elle[0].siphonBank() == 2, "She starts drinking bank 2");
                }))
                .thenExecuteAfter(10, guard(() -> {
                    before[0] = elle[0].getHealth();
                    PrimatechArena.setBreaker(PrimatechArena.level(player.getServer()), 2, false);
                }))
                .thenExecuteAfter(3, guard(() -> {
                    helper.assertTrue(elle[0].backfires() == 1 && elle[0].staggered(), "The current comes back up the arc: she is stunned");
                    float lost = before[0] - elle[0].getHealth();
                    helper.assertTrue(lost >= elle[0].getMaxHealth() * 0.15F, "A heavy hit through her shield: " + lost);
                    helper.assertTrue(PrimatechArena.bank(2) == PrimatechArena.Bank.BLOWN && elle[0].siphonBank() < 0, "The bank is blown, the siphon over");
                    PrimatechArena.setBreaker(PrimatechArena.level(player.getServer()), 2, true);
                }))
                .thenExecuteAfter(3, guard(() -> {
                    helper.assertTrue(!PrimatechArena.live(2), "A blown bank stays dead, whatever its breaker says");
                    helper.assertTrue(PrimatechArena.liveCount() == 5 && elle[0].shield() == 1.25F, "Five banks left; stunned, she takes a quarter more");
                    // A siphon that runs its course heals her and overcharges her next attacks.
                    elle[0].holdOff(1000);
                }))
                .thenWaitUntil(() -> helper.assertTrue(!elle[0].staggered(), "The stun wears off"))
                .thenExecute(guard(() -> {
                    before[0] = elle[0].getHealth();
                    helper.assertTrue(elle[0].siphonFrom(0), "She drinks bank 0");
                }))
                .thenExecuteAfter(ElleMoves.SIPHON.windup() + 2, guard(() -> {
                    try {
                        helper.assertTrue(elle[0].siphons() == 1 && elle[0].getHealth() > before[0], "Left alone, the siphon heals her");
                        helper.assertTrue(elle[0].overcharge() == 2 && PrimatechArena.live(0), "Her next two attacks are overcharged; the bank still lives");
                        helper.succeed();
                    } finally { remove(player); }
                }));
    }

    @GameTest(template = "rewind_empty", batch = "elle.ground", timeoutTicks = 260)
    public static void standingAtTheBreakerGroundsHerReboot(GameTestHelper helper) {
        reset(helper);
        ServerPlayer player = player(helper, ModAbilities.QUAKE_ID, new BlockPos(6, 2, 6), "elle-ground");
        ElleEntity[] elle = new ElleEntity[1];
        try { elle[0] = onTheFloor(helper, player); }
        catch (RuntimeException | AssertionError error) { remove(player); throw error; }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ready(elle[0]), "She and the whole floor come alive"))
                .thenExecuteAfter(2, guard(() -> PrimatechArena.setBreaker(PrimatechArena.level(player.getServer()), 3, false)))
                .thenExecuteAfter(2, guard(() -> {
                    helper.assertTrue(PrimatechArena.bank(3) == PrimatechArena.Bank.CUT, "Bank 3 is cut off");
                    // Stand right at its breaker.
                    stand(player, PrimatechArena.center().add(6, 0, 9.5));
                    helper.assertTrue(elle[0].castNow("reboot") && elle[0].casting().equals("reboot"), "She tries to force it back on");
                }))
                .thenExecuteAfter(ElleMoves.REBOOT.windup() + 2, guard(() -> {
                    helper.assertTrue(PrimatechArena.bank(3) == PrimatechArena.Bank.CUT, "Your body grounds the current: it stays cut");
                    stand(player, PrimatechArena.center().add(-8, 0, -5));
                    helper.assertTrue(elle[0].castNow("reboot"), "She tries again");
                }))
                .thenExecuteAfter(ElleMoves.REBOOT.windup() + 2, guard(() -> {
                    try {
                        helper.assertTrue(PrimatechArena.bank(3) == PrimatechArena.Bank.LIVE && PrimatechArena.closed(PrimatechArena.level(player.getServer()), 3),
                                "Nobody at the breaker: the bank is live again");
                        helper.succeed();
                    } finally { remove(player); }
                }));
    }

    // The pocket arena's freshly forced chunks can take several seconds to start ticking while the rest of the suite runs.
    @GameTest(template = "rewind_empty", batch = "elle.conduit", timeoutTicks = 400)
    public static void conduitSurgesHurtOnTheStripButNotOnRubber(GameTestHelper helper) {
        reset(helper);
        ServerPlayer player = player(helper, ModAbilities.QUAKE_ID, new BlockPos(6, 2, 6), "elle-conduit");
        ElleEntity[] elle = new ElleEntity[1];
        float[] before = new float[1];
        try { elle[0] = onTheFloor(helper, player); }
        catch (RuntimeException | AssertionError error) { remove(player); throw error; }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ready(elle[0]), "She and the whole floor come alive"))
                .thenExecuteAfter(2, guard(() -> {
                    // On the east bank's conduit.
                    stand(player, PrimatechArena.center().add(6, 0, 0));
                    helper.assertTrue(PrimatechGeometry.fromConduit(PrimatechArena.floor(), 2, player.position()) < 0.5, "Fixture: on the strip");
                    before[0] = player.getHealth();
                    String why = "onGround=" + player.onGround() + " onFloor=" + PrimatechArena.onFloor(player) + " onMat=" + PrimatechArena.onMat(player)
                            + " y=" + player.getY() + " foes=" + ElleMoves.foes(elle[0], player.position(), 2).size()
                            + " difficulty=" + player.level().getDifficulty() + " invulnerable=" + player.getAbilities().invulnerable;
                    elle[0].surgeNow(2);
                    helper.assertTrue(player.getHealth() < before[0], "The surge hurts on the strip: " + before[0] + " -> " + player.getHealth() + " " + why);
                    player.setHealth(player.getMaxHealth());
                    // Two blocks off the strip, it doesn't reach.
                    stand(player, PrimatechArena.center().add(6, 0, 2.2));
                    before[0] = player.getHealth();
                    elle[0].surgeNow(2);
                    helper.assertTrue(player.getHealth() == before[0], "Off the strip, the surge misses");
                    // The ground current: on rubber inside her ring, it can't reach you.
                    Vec3 mark = PrimatechArena.center().add(0, 0, -5.5);
                    elle[0].teleportTo(mark.x, mark.y, mark.z);
                    stand(player, PrimatechArena.center().add(0, 0, -8));
                    helper.assertTrue(PrimatechArena.onMat(player) && player.distanceTo(elle[0]) < ElleMoves.RING, "Fixture: on the north mat, in her reach");
                    helper.assertTrue(elle[0].castNow("ground_current"), "She calls the ground current");
                }))
                .thenExecuteAfter(ElleMoves.GROUND_CURRENT.windup() + 2, guard(() -> {
                    helper.assertTrue(player.getHealth() == before[0], "Rubber keeps the floor's current off");
                    // Step off the mat (still in her ring) and she tries again.
                    Vec3 off = elle[0].position().add(2.5, 0, 0);
                    stand(player, off);
                    helper.assertTrue(!PrimatechArena.onMat(player), "Fixture: off the mat");
                    helper.assertTrue(elle[0].castNow("ground_current"), "Again");
                }))
                .thenExecuteAfter(ElleMoves.GROUND_CURRENT.windup() + 2, guard(() -> {
                    try {
                        helper.assertTrue(player.getHealth() < before[0], "On bare floor it bites");
                        helper.succeed();
                    } finally { remove(player); }
                }));
    }

    // ------------------------------------------------------------------
    // What she leaves
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", batch = "elle.defeat", timeoutTicks = 260)
    public static void herDefeatLeavesTheCapacitorAndSendsEveryoneHome(GameTestHelper helper) {
        reset(helper);
        ServerPlayer player = player(helper, ModAbilities.QUAKE_ID, new BlockPos(6, 2, 6), "elle-defeat");
        Vec3 home = player.position();
        ElleEntity[] elle = new ElleEntity[1];
        try { elle[0] = onTheFloor(helper, player); }
        catch (RuntimeException | AssertionError error) { remove(player); throw error; }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ready(elle[0]), "She and the whole floor come alive"))
                .thenExecuteAfter(2, guard(() -> {
                    elle[0].invulnerableTime = 0;
                    elle[0].hurt(player.damageSources().playerAttack(player), 2000);
                    helper.assertTrue(!elle[0].isAlive() && ElleStoryService.elle() == null, "She is beaten");
                    ItemStack found = ItemStack.EMPTY;
                    for (ItemStack stack : player.getInventory().items) if (stack.is(ModItems.ELLES_CAPACITOR.get())) found = stack;
                    helper.assertTrue(!found.isEmpty() && ElleCapacitorItem.charges(found) == ElleCapacitorItem.MAX_CHARGES, "Her capacitor, fully charged, is theirs");
                    helper.assertTrue(CompanyVisitData.get(player.getServer()).visit(player.getUUID()).beaten == 1, "The visit counts as won");
                }))
                .thenWaitUntil(() -> helper.assertTrue(!PrimatechArena.inArena(player), "Everyone goes home"))
                .thenExecute(guard(() -> {
                    try {
                        helper.assertTrue(player.position().distanceToSqr(home) < 0.01, "Back where they stood");
                        helper.succeed();
                    } finally { remove(player); }
                }));
    }

    @GameTest(template = "rewind_empty", batch = "elle.capacitor", timeoutTicks = 60)
    public static void theCapacitorStunsWhatItHitsAndDrinksFromMachines(GameTestHelper helper) {
        reset(helper);
        ServerPlayer player = player(helper, ModAbilities.QUAKE_ID, new BlockPos(2, 2, 6), "elle-capacitor");
        try {
            Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(7, 2, 6));
            Vec3 eye = player.getEyePosition(), to = zombie.getBoundingBox().getCenter();
            player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, to);
            ItemStack stack = ElleCapacitorItem.charged(new ItemStack(ModItems.ELLES_CAPACITOR.get()), 3);
            float health = zombie.getHealth();
            helper.assertTrue(ElleCapacitorItem.fire(player, stack) == zombie, "The bolt finds what you look at from " + eye);
            helper.assertTrue(zombie.getHealth() < health && zombie.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "Hurt and stunned");
            helper.assertTrue(ElleCapacitorItem.charges(stack) == 2, "One charge spent");
            helper.setBlock(new BlockPos(2, 2, 2), Blocks.FURNACE);
            EnergyStorage battery = TestMachines.power(helper.getBlockEntity(new BlockPos(2, 2, 2)), 100_000, 50_000);
            helper.assertTrue(ElleCapacitorItem.drink(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2))) == ElleCapacitorItem.FE_PER_CHARGE
                    && battery.getEnergyStored() == 40_000, "A machine gives up a charge's worth");
            helper.succeed();
        } finally { remove(player); }
    }
}
