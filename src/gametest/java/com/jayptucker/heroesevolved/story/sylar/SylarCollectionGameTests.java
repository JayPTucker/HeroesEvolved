package com.jayptucker.heroesevolved.story.sylar;

import com.jayptucker.heroesevolved.PowerOwnershipTestSupport;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.intro.PowerIntroHistory;
import com.jayptucker.heroesevolved.intro.PowerIntroService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.story.AvenueData;
import com.jayptucker.heroesevolved.structure.Blueprint;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Sylar's Collection: the ticket and the trip into his hall and home again, the hall's markers against
 * {@link SylarGeometry}, the boss rules (caps, falloff, non-player blows, party scaling), the phases and what they do to the
 * room, the cases (a backfire when the drawn case breaks, a locked case mending), Appraisal and getting your power back,
 * Every Hour's spared hours, the light and its cover (and the light that fails in a world that kept him from it), Claire's
 * case, the death scene and its rewards, what you drop in his hall coming back, and how you meet him: the prisoner in
 * Level 5 and his list (waking, the nightly crossings and guarding, the glass and the lever, the man behind you).
 *
 * <p>Each test runs alone in its batch: there is one Collection, and a test that dies mid-way takes its players with it
 * (a player left behind keeps their power from every later test).</p>
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class SylarCollectionGameTests {
    private static final Map<UUID, Set<ChunkPos>> CHUNKS = new HashMap<>();
    private static final List<ServerPlayer> LIVE = new ArrayList<>();

    @SuppressWarnings("unchecked") private static <T> T field(Object object, Class<?> type, String name) {
        try { Field f = type.getDeclaredField(name); f.setAccessible(true); return (T) f.get(object); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    /** A listed survival player with a power, who can be hurt at once and can cross into the hall and back. */
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
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(x, 2, 5))));
        player.setGameMode(GameType.SURVIVAL);
        for (int dx = -16; dx <= 16; dx += 16) for (int dz = -16; dz <= 16; dz += 16) {
            ChunkPos chunk = new ChunkPos(player.blockPosition().offset(dx, 0, dz));
            if (!helper.getLevel().getForcedChunks().contains(chunk.toLong())) {
                helper.getLevel().setChunkForced(chunk.x, chunk.z, true);
                CHUNKS.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>()).add(chunk);
            }
        }
        helper.getLevel().addNewPlayer(player);
        SylarCollectionGameTests.<List<ServerPlayer>>field(player.getServer().getPlayerList(), PlayerList.class, "players").add(player);
        SylarCollectionGameTests.<Map<UUID, ServerPlayer>>field(player.getServer().getPlayerList(), PlayerList.class, "playersByUUID").put(player.getUUID(), player);
        if (power != null) {
            player.setData(ModDataAttachments.POWER_INTRO_HISTORY, PowerIntroHistory.empty().with(power));
            PlayerAbilityService.grantAbility(player, power);
        }
        LIVE.add(player);
        return player;
    }

    private static void reset(GameTestHelper helper) {
        if (!LIVE.isEmpty()) remove(LIVE.toArray(ServerPlayer[]::new));
        CollectionArena.reset(helper.getLevel().getServer());
        SylarStory.reset();
        helper.getLevel().getServer().overworld().getDataStorage().set(SylarData.NAME, new SylarData());
    }

    private static void remove(ServerPlayer... players) {
        if (players.length == 0) return;
        var server = players[0].getServer();
        CollectionArena.reset(server);
        for (ServerPlayer player : players) {
            if (!LIVE.remove(player)) continue;
            PowerOwnershipTestSupport.release(player); PowerIntroService.forget(player);
            SylarsWatchItem.forget(player.getUUID());
            SylarCollectionGameTests.<List<ServerPlayer>>field(server.getPlayerList(), PlayerList.class, "players").remove(player);
            SylarCollectionGameTests.<Map<UUID, ServerPlayer>>field(server.getPlayerList(), PlayerList.class, "playersByUUID").remove(player.getUUID());
            player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            var chunks = CHUNKS.remove(player.getUUID());
            if (chunks != null) for (var chunk : chunks) server.overworld().setChunkForced(chunk.x, chunk.z, false);
        }
    }

    /** A test step that, if it fails, takes its players with it. */
    private static Runnable guard(Runnable step) {
        return () -> {
            try { step.run(); }
            catch (RuntimeException | AssertionError error) { remove(LIVE.toArray(ServerPlayer[]::new)); throw error; }
        };
    }

    /** Opens the Collection with {@code holder} and returns its Sylar, kept still and with no move of his own. */
    private static SylarEntity open(GameTestHelper helper, ServerPlayer holder) {
        helper.assertTrue(CollectionArena.level(holder.getServer()) != null, "The Collection's dimension must load from the datapack");
        SylarEntity sylar = CollectionArena.open(holder);
        helper.assertTrue(sylar != null && sylar.fighting() && CollectionArena.inCollection(holder), "The ticket takes its holder into his hall");
        sylar.still(true);
        sylar.holdOff(100_000);
        return sylar;
    }

    /** Every corner of the hall is ticking (a freshly forced pocket chunk hides its players for a few seconds). */
    private static boolean ready(SylarEntity sylar) {
        ServerLevel hall = (ServerLevel) sylar.level();
        BlockPos o = SylarGeometry.ORIGIN;
        for (int dx = -22; dx <= 22; dx += 22) for (int dz = -22; dz <= 22; dz += 22)
            if (!hall.isPositionEntityTicking(o.offset(dx, 1, dz))) return false;
        return true;
    }

    private static void stand(ServerPlayer player, Vec3 at) {
        player.teleportTo((ServerLevel) player.level(), at.x, at.y, at.z, 0, 0);
        player.setDeltaMovement(Vec3.ZERO);
    }

    /** A spot on the dial plate, at clock angle {@code hours} (0 to 12) and radius {@code r}. */
    private static Vec3 floor(double hours, double r) {
        double g = hours * Math.PI / 6;
        return SylarGeometry.center().add(SylarGeometry.dx(g, r), 0, SylarGeometry.dz(g, r));
    }

    private static boolean has(ServerPlayer player, net.minecraft.world.item.Item item) {
        return player.getInventory().items.stream().anyMatch(stack -> stack.is(item));
    }

    private static void hit(SylarEntity sylar, ServerPlayer by, float amount) {
        sylar.invulnerableTime = 0;
        sylar.hurt(by.damageSources().playerAttack(by), amount);
    }

    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", batch = "sylar.markers")
    public static void theHallsMarkersMatchItsGeometry(GameTestHelper helper) {
        Blueprint hall = Blueprint.load("the_collection");
        for (int h = 0; h < 12; h++) {
            List<BlockPos> at = hall.markerOffsets("case:" + h);
            BlockPos want = SylarGeometry.caseGlass(h).subtract(SylarGeometry.ORIGIN);
            helper.assertTrue(at.size() == 1 && at.getFirst().equals(want), "Case " + h + " is where the fight looks for it: " + at + " vs " + want);
        }
        for (int i = 0; i < 4; i++) {
            List<BlockPos> at = hall.markerOffsets("cover:" + i);
            BlockPos want = SylarGeometry.cover(i).below().subtract(SylarGeometry.ORIGIN);
            helper.assertTrue(at.size() == 1 && at.getFirst().equals(want), "Pillar " + i + " rises where its floor is marked: " + at + " vs " + want);
        }
        helper.assertTrue(hall.markerOffsets("spawn:sylar").equals(List.of(BlockPos.ZERO)), "He starts on the dais's middle");
        Vec3 party = SylarGeometry.arrival(0);
        helper.assertTrue(hall.markerOffsets("spawn:party").equals(List.of(BlockPos.containing(party).below().subtract(SylarGeometry.ORIGIN))),
                "The party arrives where the floor says");
        helper.assertTrue(hall.markerOffsets("lamp").size() >= 24, "The hall's copper bulbs are marked: " + hall.markerOffsets("lamp").size());
        helper.assertTrue(SylarGeometry.CASES.size() == 12 && Arrays.stream(SylarGeometry.EMPTY_CASES).allMatch(h -> SylarGeometry.CASES.get(h) == null),
                "Twelve cases, three kept empty");
        helper.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "sylar.ticket", timeoutTicks = 300)
    public static void theTicketTakesItsHolderAndFriendsInAndEveryoneHome(GameTestHelper helper) {
        reset(helper);
        ServerPlayer holder = player(helper, ModAbilities.QUAKE_ID, 4, "collection-holder");
        ServerPlayer friend = player(helper, ModAbilities.CRYOKINESIS_ID, 7, "collection-friend");
        Vec3 home = holder.position(), friendHome = friend.position();
        SylarEntity[] sylar = new SylarEntity[1];
        try {
            sylar[0] = open(helper, holder);
            helper.assertTrue(CollectionArena.inCollection(friend), "Someone within sixteen blocks comes too");
            helper.assertTrue(Math.abs(sylar[0].getMaxHealth() - SylarEntity.BASE_HEALTH * 1.5) < 0.01,
                    "Two fighters: half again his health: " + sylar[0].getMaxHealth());
            ServerLevel hall = CollectionArena.level(holder.getServer());
            helper.assertTrue(hall.getBlockState(SylarGeometry.ORIGIN).is(Blocks.WAXED_COPPER_BLOCK), "The dais's brass boss is under his feet");
            for (int h = 0; h < 12; h++)
                helper.assertTrue(hall.getBlockState(SylarGeometry.caseGlass(h)).is(Blocks.GLASS), "Case " + h + " stands whole");
            int full = 0;
            for (int h = 0; h < 12; h++) if (SylarGeometry.CASES.get(h) != null) full |= 1 << h;
            helper.assertTrue(sylar[0].cases() == full, "A world that changed nothing in the past: every case holds its power: "
                    + Integer.toBinaryString(sylar[0].cases()));
            helper.assertTrue(CollectionArena.refusal(friend) != null, "One fight at a time");
        } catch (RuntimeException | AssertionError error) { remove(holder, friend); throw error; }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ready(sylar[0]), "The hall comes alive"))
                .thenExecuteAfter(5, guard(() -> {
                    helper.assertTrue(sylar[0].bar().getPlayers().contains(holder), "His bar shows to the fighters");
                    CollectionArena.end(holder.getServer(), CollectionArena.Outcome.ABANDONED);
                }))
                .thenExecuteAfter(30, guard(() -> {
                    try {
                        helper.assertTrue(!CollectionArena.inCollection(holder) && holder.position().distanceToSqr(home) < 0.01,
                                "Everyone goes home to where they stood: " + holder.position());
                        helper.assertTrue(!CollectionArena.inCollection(friend) && friend.position().distanceToSqr(friendHome) < 0.01, "The friend too");
                        helper.assertTrue(!CollectionArena.open() && SylarData.get(holder.getServer()).home(holder.getUUID()) == null,
                                "Nobody is owed a way home any more");
                        helper.succeed();
                    } finally { remove(holder, friend); }
                }));
    }

    @GameTest(template = "rewind_empty", batch = "sylar.rules", timeoutTicks = 300)
    public static void hitsAreCappedFallOffWithDistanceAndOnlyPlayersHurtHim(GameTestHelper helper) {
        reset(helper);
        ServerPlayer fighter = player(helper, ModAbilities.QUAKE_ID, 4, "collection-rules");
        SylarEntity[] sylar = new SylarEntity[1];
        float[] before = new float[1];
        try {
            sylar[0] = open(helper, fighter);
            helper.assertTrue(Math.abs(sylar[0].getMaxHealth() - SylarEntity.BASE_HEALTH) < 0.01, "One fighter: his base health");
        } catch (RuntimeException | AssertionError error) { remove(fighter); throw error; }
        float max = (float) SylarEntity.BASE_HEALTH;
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ready(sylar[0]), "The hall comes alive"))
                .thenExecute(guard(() -> {
                    stand(fighter, floor(6, 6));
                    before[0] = sylar[0].getHealth();
                    hit(sylar[0], fighter, 200);
                    float lost = before[0] - sylar[0].getHealth();
                    helper.assertTrue(lost > 0 && lost <= max * SylarEntity.HIT_CAP + 0.01, "No hit takes more than its cap: " + lost);
                    before[0] = sylar[0].getHealth();
                    hit(sylar[0], fighter, 200);
                    helper.assertTrue(before[0] - sylar[0].getHealth() < 0.01, "The next blow in the same second finds the cap spent: "
                            + (before[0] - sylar[0].getHealth()));
                }))
                .thenExecuteAfter(25, guard(() -> {
                    before[0] = sylar[0].getHealth();
                    hit(sylar[0], fighter, 6);
                    helper.assertTrue(before[0] - sylar[0].getHealth() > 0, "A second later he can be hurt again");
                    // From across the world's widest reach of the hall, a blow does nothing.
                    stand(fighter, floor(6, SylarGeometry.FLOOR_R).add(0, 0, 18));
                    before[0] = sylar[0].getHealth();
                }))
                .thenExecuteAfter(25, guard(() -> {
                    hit(sylar[0], fighter, 6);
                    helper.assertTrue(Math.abs(before[0] - sylar[0].getHealth()) < 0.01, "Nothing lands from past the falloff");
                    stand(fighter, floor(6, 6));
                }))
                .thenExecuteAfter(25, guard(() -> {
                    try {
                        before[0] = sylar[0].getHealth();
                        sylar[0].invulnerableTime = 0;
                        sylar[0].hurt(sylar[0].damageSources().lava(), 15);
                        float lost = before[0] - sylar[0].getHealth();
                        helper.assertTrue(lost <= SylarEntity.NON_PLAYER_CAP + 0.01, "Anything but a player chips at most three: " + lost);
                        sylar[0].invulnerableTime = 0;
                        helper.assertTrue(!sylar[0].hurt(sylar[0].damageSources().fall(), 30), "Falls don't hurt him");
                        helper.succeed();
                    } finally { remove(fighter); }
                }));
    }

    @GameTest(template = "rewind_empty", batch = "sylar.phases", timeoutTicks = 400)
    public static void thePhasesTurnAtTwoThirdsAndOneThirdAndChangeTheHall(GameTestHelper helper) {
        reset(helper);
        ServerPlayer fighter = player(helper, ModAbilities.QUAKE_ID, 4, "collection-phases");
        SylarEntity[] sylar = new SylarEntity[1];
        try { sylar[0] = open(helper, fighter); }
        catch (RuntimeException | AssertionError error) { remove(fighter); throw error; }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ready(sylar[0]), "The hall comes alive"))
                .thenExecute(guard(() -> {
                    stand(fighter, floor(6, 12));
                    helper.assertTrue(sylar[0].phase() == 1, "He opens with Viewing Hours");
                    sylar[0].setHealth(sylar[0].getMaxHealth() * 0.6F);
                }))
                .thenExecuteAfter(3, guard(() -> {
                    ServerLevel hall = (ServerLevel) sylar[0].level();
                    helper.assertTrue(sylar[0].phase() == 2, "Below two thirds: Unlocked");
                    helper.assertTrue(CollectionArena.marked("lamp").stream().noneMatch(p -> hall.getBlockState(p).getValue(CopperBulbBlock.LIT)),
                            "The hall's lights go out");
                    float health = sylar[0].getHealth();
                    hit(sylar[0], fighter, 10);
                    helper.assertTrue(sylar[0].getHealth() == health, "A phase change can't be hurt through");
                    sylar[0].setHealth(sylar[0].getMaxHealth() * 0.3F);
                }))
                .thenExecuteAfter(SylarEntity.TRANSITION_TWO + 3, guard(() -> helper.assertTrue(sylar[0].phase() == 3, "Below a third: the Masterwork")))
                .thenExecuteAfter(CollectionArena.COVER_WARN + CollectionArena.COVER_STEP * SylarGeometry.COVER_HEIGHT + 5, guard(() -> {
                    try {
                        ServerLevel hall = (ServerLevel) sylar[0].level();
                        for (int i = 0; i < 4; i++) {
                            BlockPos base = SylarGeometry.cover(i);
                            helper.assertTrue(!hall.getBlockState(base).isAir() && !hall.getBlockState(base.above(SylarGeometry.COVER_HEIGHT - 1)).isAir(),
                                    "Pillar " + i + " has risen from the floor");
                        }
                        helper.succeed();
                    } finally { remove(fighter); }
                }));
    }

    @GameTest(template = "rewind_empty", batch = "sylar.backfire", timeoutTicks = 300)
    public static void breakingTheCaseHeDrawsFromBackfiresAndALockedCaseMends(GameTestHelper helper) {
        reset(helper);
        ServerPlayer fighter = player(helper, ModAbilities.QUAKE_ID, 4, "collection-backfire");
        SylarEntity[] sylar = new SylarEntity[1];
        float[] before = new float[1];
        try { sylar[0] = open(helper, fighter); }
        catch (RuntimeException | AssertionError error) { remove(fighter); throw error; }
        int ice = SylarGeometry.caseOf(ModAbilities.CRYOKINESIS_ID), lightning = SylarGeometry.caseOf(ModAbilities.ELECTROKINESIS_ID);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ready(sylar[0]), "The hall comes alive"))
                .thenExecute(guard(() -> {
                    stand(fighter, floor(6, 12));
                    ServerLevel hall = (ServerLevel) sylar[0].level();
                    // A locked case: the glass is whole again before anything can come out of it.
                    sylar[0].breakCase(lightning);
                    helper.assertTrue(hall.getBlockState(SylarGeometry.caseGlass(lightning)).is(Blocks.GLASS) && sylar[0].caseWhole(lightning),
                            "A case he isn't drawing on mends itself");
                    helper.assertTrue(sylar[0].castNow("icefall") && sylar[0].drawing() == ice, "Icefall draws on the frost case");
                    before[0] = sylar[0].getHealth();
                    sylar[0].breakCase(ice);
                    float lost = before[0] - sylar[0].getHealth();
                    helper.assertTrue(lost >= sylar[0].getMaxHealth() * SylarEntity.BACKFIRE * 0.6F,
                            "The power lashes back into him, through the caps: " + lost);
                    helper.assertTrue(sylar[0].is(SylarEntity.STAGGERED) && sylar[0].casting().isEmpty(), "He reels, and the move is gone");
                    helper.assertTrue(!sylar[0].caseWhole(ice) && !hall.getBlockState(SylarGeometry.caseGlass(ice)).is(Blocks.GLASS),
                            "The frost case stays broken");
                    helper.assertTrue(SylarMoves.ICEFALL.weight(sylar[0], sylar[0].fighters()) == 0, "And Icefall is gone for the fight");
                }))
                .thenExecuteAfter(5, guard(() -> {
                    try {
                        helper.assertTrue(!sylar[0].collection().contains(ModAbilities.CRYOKINESIS_ID) && sylar[0].collection().contains(ModAbilities.TELEKINESIS_ID),
                                "What he holds now: " + sylar[0].collection());
                        helper.succeed();
                    } finally { remove(fighter); }
                }));
    }

    @GameTest(template = "rewind_empty", batch = "sylar.moves", timeoutTicks = 300)
    public static void movesLandOnlyWhenTheirSchematicAssembles(GameTestHelper helper) {
        reset(helper);
        ServerPlayer fighter = player(helper, ModAbilities.QUAKE_ID, 4, "collection-moves");
        SylarEntity[] sylar = new SylarEntity[1];
        float[] before = new float[1];
        try { sylar[0] = open(helper, fighter); }
        catch (RuntimeException | AssertionError error) { remove(fighter); throw error; }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ready(sylar[0]), "The hall comes alive"))
                .thenExecute(guard(() -> {
                    stand(fighter, floor(6, 10));
                    fighter.setHealth(20);
                    before[0] = fighter.getHealth();
                    helper.assertTrue(sylar[0].castNow("hurl"), "He tears a slab out of the floor");
                }))
                .thenExecuteAfter(SylarGeometry.HURL_WINDUP - 3, guard(() ->
                        helper.assertTrue(fighter.getHealth() == before[0], "Nothing lands before the ring assembles")))
                .thenExecuteAfter(6, guard(() -> {
                    helper.assertTrue(fighter.getHealth() < before[0], "The slab lands where they stood");
                    ServerLevel hall = (ServerLevel) sylar[0].level();
                    Vec3 spot = floor(6, 10);
                    BlockPos middle = BlockPos.containing(spot.x, SylarGeometry.floorY(), spot.z);
                    helper.assertTrue(BlockPos.betweenClosedStream(middle.offset(-1, 0, -1), middle.offset(1, 0, 1)).anyMatch(c -> !hall.getBlockState(c).isAir()),
                            "It breaks up into rubble on the floor round them");
                    helper.assertTrue(hall.getBlockState(middle).isAir(), "Never on top of anyone");
                    fighter.setHealth(20);
                    stand(fighter, floor(3, 9));
                    before[0] = fighter.getHealth();
                    helper.assertTrue(sylar[0].castNow("icefall"), "A ring under them");
                    // They step off the ring: the spot was fixed when the windup started.
                    stand(fighter, floor(3, 15));
                }))
                .thenExecuteAfter(SylarGeometry.ICE_WINDUP + 4, guard(() -> {
                    try {
                        helper.assertTrue(fighter.getHealth() == before[0], "Moving beats it");
                        ServerLevel hall = (ServerLevel) sylar[0].level();
                        Vec3 spot = floor(3, 9);
                        helper.assertTrue(hall.getBlockState(BlockPos.containing(spot.x, SylarGeometry.ORIGIN.getY(), spot.z)).is(Blocks.BLUE_ICE),
                                "The floor stays frozen where it fell");
                        helper.succeed();
                    } finally { remove(fighter); }
                }));
    }

    @GameTest(template = "rewind_empty", batch = "sylar.appraisal", timeoutTicks = 400)
    public static void appraisalPutsYourPowerInACaseAndBreakingItGivesItBack(GameTestHelper helper) {
        reset(helper);
        ServerPlayer fighter = player(helper, ModAbilities.QUAKE_ID, 4, "collection-appraised");
        SylarEntity[] sylar = new SylarEntity[1];
        try {
            sylar[0] = open(helper, fighter);
            sylar[0].setHealth(sylar[0].getMaxHealth() * 0.6F);
        } catch (RuntimeException | AssertionError error) { remove(fighter); throw error; }
        // Phase two summons an echo that can kill this 20-health fighter mid-windup and end the fight: this test is about Appraisal alone.
        helper.onEachTick(() -> { if (!sylar[0].isRemoved()) sylar[0].dissolveEchoes((ServerLevel) sylar[0].level(), -1); });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ready(sylar[0]), "The hall comes alive"))
                .thenExecuteAfter(SylarEntity.TRANSITION_TWO + 5, guard(() -> {
                    helper.assertTrue(sylar[0].phase() == 2, "Unlocked");
                    stand(fighter, floor(6, 8));
                    helper.assertTrue(sylar[0].castNow("appraise"), "A ring under their feet");
                }))
                .thenExecuteAfter(SylarGeometry.APPRAISE_WINDUP + 2, guard(() ->
                        helper.assertTrue(sylar[0].holding() == fighter && fighter.getY() > SylarGeometry.floorY() + 2,
                                "Still on the ring: held up in the air")))
                .thenExecuteAfter(SylarGeometry.HOLD_TICKS + 2, guard(() -> {
                    helper.assertTrue(sylar[0].holding() == null && CollectionArena.stolen(fighter), "Too slow: the power goes into a case");
                    helper.assertTrue(!PowerRules.canActWithoutPreservation(fighter), "They can't use it while it's under his glass");
                    int h = SylarGeometry.EMPTY_CASES[0];
                    helper.assertTrue(sylar[0].caseWhole(h) && sylar[0].stolenColour(0) >= 0, "The first empty case lights with it");
                    helper.assertTrue(sylar[0].collection().contains(ModAbilities.QUAKE_ID), "He holds it now");
                    sylar[0].breakCase(h);
                    helper.assertTrue(!CollectionArena.stolen(fighter) && !sylar[0].caseWhole(h), "Breaking the case gives it back");
                    stand(fighter, floor(6, 8));
                    helper.assertTrue(sylar[0].castNow("appraise"), "He tries again");
                }))
                .thenExecuteAfter(SylarGeometry.APPRAISE_WINDUP + 4, guard(() -> {
                    helper.assertTrue(sylar[0].holding() == fighter, "Held again");
                    // A friend's charged hit (stood in for by the fighter's own full swing) breaks his concentration.
                    SylarEvents.swing(fighter);
                    hit(sylar[0], fighter, 4);
                }))
                .thenExecuteAfter(2, guard(() -> {
                    try {
                        helper.assertTrue(sylar[0].holding() == null && sylar[0].is(SylarEntity.STAGGERED) && !CollectionArena.stolen(fighter),
                                "A charged hit frees them and he reels");
                        helper.succeed();
                    } finally { remove(fighter); }
                }));
    }

    @GameTest(template = "rewind_empty", batch = "sylar.hours", timeoutTicks = 400)
    public static void everyHourSparesTwoOppositeHours(GameTestHelper helper) {
        reset(helper);
        ServerPlayer caught = player(helper, ModAbilities.QUAKE_ID, 4, "collection-caught");
        ServerPlayer spared = player(helper, ModAbilities.CRYOKINESIS_ID, 6, "collection-spared");
        SylarEntity[] sylar = new SylarEntity[1];
        float[] health = new float[2];
        try {
            sylar[0] = open(helper, caught);
            sylar[0].setHealth(sylar[0].getMaxHealth() * 0.3F);
        } catch (RuntimeException | AssertionError error) { remove(caught, spared); throw error; }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ready(sylar[0]), "The hall comes alive"))
                .thenExecuteAfter(SylarEntity.TRANSITION_THREE + 50, guard(() -> {
                    helper.assertTrue(sylar[0].phase() == 3, "The Masterwork");
                    helper.assertTrue(sylar[0].castNow("every_hour"), "Every Hour");
                    int safe = sylar[0].safeHour;
                    stand(spared, floor((safe + 6) % 12, 12));
                    stand(caught, floor((safe + 3) % 12, 12));
                    caught.setHealth(20); spared.setHealth(20);
                    health[0] = caught.getHealth(); health[1] = spared.getHealth();
                }))
                .thenExecuteAfter(SylarGeometry.HOURS_WINDUP + 2, guard(() -> {
                    try {
                        helper.assertTrue(caught.getHealth() < health[0] && sylar[0].timeStopped(caught), "Stopped time catches whoever is in a gold-less hour");
                        helper.assertTrue(spared.getHealth() == health[1] && !sylar[0].timeStopped(spared), "The hour opposite the safe one is spared too");
                        helper.assertTrue(sylar[0].is(SylarEntity.SPENT), "He is spent after it");
                        helper.succeed();
                    } finally { remove(caught, spared); }
                }));
    }

    @GameTest(template = "rewind_empty", batch = "sylar.light", timeoutTicks = 500)
    public static void theLightBurnsWhoeverItSeesAndThePillarsAreCover(GameTestHelper helper) {
        reset(helper);
        ServerPlayer open = player(helper, ModAbilities.QUAKE_ID, 4, "collection-in-the-open");
        ServerPlayer covered = player(helper, ModAbilities.CRYOKINESIS_ID, 6, "collection-in-cover");
        SylarEntity[] sylar = new SylarEntity[1];
        float[] health = new float[2];
        try {
            sylar[0] = open(helper, open);
            sylar[0].setHealth(sylar[0].getMaxHealth() * 0.3F);
            helper.assertTrue(sylar[0].caseWhole(SylarGeometry.RADIATION_CASE), "He saw the light at 6:14 in this world");
        } catch (RuntimeException | AssertionError error) { remove(open, covered); throw error; }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ready(sylar[0]), "The hall comes alive"))
                .thenExecuteAfter(SylarEntity.TRANSITION_THREE + 50, guard(() -> {
                    // Behind pillar 0 (half past one, ten out), seen from the dais; and out in the open at four.
                    stand(covered, floor(1.5, 13));
                    stand(open, floor(4, 13));
                    open.setHealth(20); covered.setHealth(20);
                    health[0] = open.getHealth(); health[1] = covered.getHealth();
                    helper.assertTrue(sylar[0].castNow("light"), "He rises over the dais");
                }))
                .thenExecuteAfter(SylarGeometry.LIGHT_WINDUP + 2, guard(() -> {
                    try {
                        helper.assertTrue(open.getHealth() < health[0] && open.isOnFire(), "In his sight: burned");
                        helper.assertTrue(covered.getHealth() == health[1], "Behind a pillar: nothing");
                        helper.succeed();
                    } finally { remove(open, covered); }
                }));
    }

    @GameTest(template = "rewind_empty", batch = "sylar.no_light", timeoutTicks = 500)
    public static void aWorldThatKeptHimFromTheLightSeesItFail(GameTestHelper helper) {
        reset(helper);
        AvenueData avenue = AvenueData.get(helper.getLevel().getServer());
        avenue.record(UUID.randomUUID(), 1, true);
        ServerPlayer fighter = player(helper, ModAbilities.QUAKE_ID, 4, "collection-no-light");
        SylarEntity[] sylar = new SylarEntity[1];
        float[] health = new float[1];
        try {
            sylar[0] = open(helper, fighter);
            helper.assertTrue(!sylar[0].caseWhole(SylarGeometry.RADIATION_CASE), "Sprague's case stands dark");
            sylar[0].setHealth(sylar[0].getMaxHealth() * 0.3F);
        } catch (RuntimeException | AssertionError error) { avenue.forgetAll(); remove(fighter); throw error; }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ready(sylar[0]), "The hall comes alive"))
                .thenExecuteAfter(SylarEntity.TRANSITION_THREE + 5, guard(() -> {
                    stand(fighter, floor(4, 13));
                    fighter.setHealth(20);
                    health[0] = fighter.getHealth();
                    helper.assertTrue(SylarMoves.LIGHT.weight(sylar[0], sylar[0].fighters()) > 0, "He still reaches for it, once");
                    helper.assertTrue(sylar[0].castNow("light"), "He rises");
                }))
                .thenExecuteAfter(SylarGeometry.LIGHT_WINDUP + 2, guard(() -> {
                    try {
                        helper.assertTrue(fighter.getHealth() == health[0], "It comes apart in his hands");
                        helper.assertTrue(sylar[0].is(SylarEntity.SPENT), "And leaves him spent");
                        helper.assertTrue(SylarMoves.LIGHT.weight(sylar[0], sylar[0].fighters()) == 0, "He doesn't try again");
                        helper.succeed();
                    } finally { avenue.forgetAll(); remove(fighter); }
                }));
    }

    @GameTest(template = "rewind_empty", batch = "sylar.death", timeoutTicks = 500)
    public static void claireBringsHimBackUnlessHerCaseBreaksThenHeDiesAndLeavesHisWatch(GameTestHelper helper) {
        reset(helper);
        ServerPlayer fighter = player(helper, ModAbilities.QUAKE_ID, 4, "collection-winner");
        fighter.getInventory().add(new ItemStack(ModItems.REPAIR_TICKET.get()));
        SylarEntity[] sylar = new SylarEntity[1];
        try {
            sylar[0] = open(helper, fighter);
            helper.assertTrue(sylar[0].caseWhole(SylarGeometry.REGENERATION_CASE), "He reached Pod 14 in this world: Claire's case is full");
            sylar[0].setHealth(sylar[0].getMaxHealth() * 0.3F);
        } catch (RuntimeException | AssertionError error) { remove(fighter); throw error; }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ready(sylar[0]), "The hall comes alive"))
                .thenExecuteAfter(SylarEntity.TRANSITION_THREE + 50, guard(() -> {
                    stand(fighter, floor(6, 6));
                    sylar[0].setHealth(2);
                    hit(sylar[0], fighter, 10);
                    helper.assertTrue(sylar[0].isAlive() && sylar[0].is(SylarEntity.DOWNED) && sylar[0].drawing() == SylarGeometry.REGENERATION_CASE,
                            "A killing blow puts him down, and Claire's case starts mending him");
                }))
                .thenExecuteAfter(SylarEntity.DOWNED_TICKS + 3, guard(() -> {
                    helper.assertTrue(!sylar[0].is(SylarEntity.DOWNED) && Math.abs(sylar[0].getHealth() - sylar[0].getMaxHealth() * SylarEntity.REVIVE) < 1,
                            "Left alone, he gets up at a quarter of his health: " + sylar[0].getHealth());
                    helper.assertTrue(!sylar[0].caseWhole(SylarGeometry.REGENERATION_CASE), "And Claire's case is empty");
                    sylar[0].setHealth(2);
                    hit(sylar[0], fighter, 10);
                    helper.assertTrue(sylar[0].isDying() && sylar[0].isAlive(), "The second killing blow starts his death");
                    hit(sylar[0], fighter, 10);
                    helper.assertTrue(sylar[0].isAlive(), "Nothing ends the death scene early");
                }))
                .thenExecuteAfter(SylarEntity.DEATH_TICKS + 5, guard(() -> {
                    helper.assertTrue(!sylar[0].isAlive() || sylar[0].isRemoved(), "Then he falls");
                    var hall = (ServerLevel) fighter.level();
                    List<ItemEntity> prizes = hall.getEntitiesOfClass(ItemEntity.class, new AABB(SylarGeometry.ORIGIN).inflate(24));
                    boolean watch = prizes.stream().anyMatch(i -> i.getItem().is(ModItems.SYLARS_WATCH.get()))
                            || has(fighter, ModItems.SYLARS_WATCH.get());
                    boolean ledger = prizes.stream().anyMatch(i -> i.getItem().is(Items.WRITTEN_BOOK)) || has(fighter, Items.WRITTEN_BOOK);
                    helper.assertTrue(watch && ledger, "A first win leaves his watch and his ledger where he fell");
                    helper.assertTrue(SylarData.get(fighter.getServer()).wins(fighter.getUUID()) == 1, "The win is remembered");
                    helper.assertTrue(!has(fighter, ModItems.REPAIR_TICKET.get()), "The ticket is torn");
                }))
                .thenExecuteAfter(150, guard(() -> {
                    try {
                        helper.assertTrue(!CollectionArena.inCollection(fighter), "The hall closes and everyone goes home");
                        helper.assertTrue(has(fighter, ModItems.BRASS_MOVEMENT.get()),
                                "Whatever was still on the floor is in their pocket: the brass movement for another ticket");
                        helper.succeed();
                    } finally { remove(fighter); }
                }));
    }

    @GameTest(template = "rewind_empty", batch = "sylar.echoes", timeoutTicks = 300)
    public static void echoesAnswerThePhaseChangeAndFadeWithTheirCase(GameTestHelper helper) {
        reset(helper);
        ServerPlayer fighter = player(helper, ModAbilities.QUAKE_ID, 4, "collection-echoes");
        SylarEntity[] sylar = new SylarEntity[1];
        int[] hour = {-1};
        float[] health = new float[1];
        try {
            sylar[0] = open(helper, fighter);
            helper.assertTrue(SylarEntity.echoes(2, 1) == 1 && SylarEntity.echoes(2, 3) == 2 && SylarEntity.echoes(2, 4) == 2
                    && SylarEntity.echoes(3, 1) == 0 && SylarEntity.echoes(3, 3) == 1, "One echo, and one more for every two fighters past the first");
        } catch (RuntimeException | AssertionError error) { remove(fighter); throw error; }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ready(sylar[0]), "The hall comes alive"))
                .thenExecute(guard(() -> {
                    stand(fighter, floor(6, 12));
                    sylar[0].setHealth(sylar[0].getMaxHealth() * 0.6F);
                }))
                .thenExecuteAfter(3, guard(() -> {
                    helper.assertTrue(sylar[0].phase() == 2 && sylar[0].echoes().size() == 1, "Unlocked: an echo steps out of a case: " + sylar[0].echoes().size());
                    var echo = sylar[0].echoes().getFirst();
                    hour[0] = sylar[0].echoes.get(echo);
                    helper.assertTrue(com.jayptucker.heroesevolved.gifted.GiftedMobs.isGifted(echo)
                                    && SylarGeometry.CASES.get(hour[0]).equals(com.jayptucker.heroesevolved.gifted.GiftedMobs.powerOf(echo)),
                            "It carries its case's power, as a gifted raider");
                    helper.assertTrue(hour[0] != SylarGeometry.REGENERATION_CASE && hour[0] != SylarGeometry.RADIATION_CASE, "Never Claire's or Sprague's");
                }))
                .thenExecuteAfter(SylarEntity.TRANSITION_TWO + 3, guard(() -> {
                    helper.assertTrue((sylar[0].openCases() >> hour[0] & 1) != 0, "Its case stands open while it walks");
                    health[0] = sylar[0].getHealth();
                    sylar[0].breakCase(hour[0]);
                }))
                .thenExecuteAfter(2, guard(() -> {
                    try {
                        helper.assertTrue(sylar[0].echoes().isEmpty(), "Break its case and the echo fades with it");
                        helper.assertTrue(!sylar[0].caseWhole(hour[0]) && sylar[0].getHealth() == health[0] && !sylar[0].is(SylarEntity.STAGGERED),
                                "The power is gone for the fight, with no backfire: he wasn't drawing on it");
                        helper.succeed();
                    } finally { remove(fighter); }
                }));
    }

    @GameTest(template = "rewind_empty", batch = "sylar.finisher", timeoutTicks = 700)
    public static void aFinisherAtAThresholdForcesTheNextPhaseAndAtTheEndKills(GameTestHelper helper) {
        reset(helper);
        ServerPlayer fighter = player(helper, ModAbilities.QUAKE_ID, 4, "collection-finisher");
        SylarEntity[] sylar = new SylarEntity[1];
        try { sylar[0] = open(helper, fighter); }
        catch (RuntimeException | AssertionError error) { remove(fighter); throw error; }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ready(sylar[0]), "The hall comes alive"))
                .thenExecute(guard(() -> {
                    stand(fighter, floor(6, 3));
                    sylar[0].setHealth(sylar[0].getMaxHealth() * 0.8F);
                }))
                .thenExecuteAfter(2, guard(() -> {
                    helper.assertTrue(!sylar[0].finishWindow() && !com.jayptucker.heroesevolved.combat.Takedowns.finishable(fighter, sylar[0]),
                            "Far from a threshold he doesn't glisten");
                    sylar[0].setHealth(sylar[0].getMaxHealth() * 0.7F);
                }))
                .thenExecuteAfter(2, guard(() -> {
                    helper.assertTrue(sylar[0].finishWindow() && com.jayptucker.heroesevolved.combat.Takedowns.looksFinishable(fighter, sylar[0], false)
                                    && com.jayptucker.heroesevolved.combat.Takedowns.finishable(fighter, sylar[0]),
                            "Just above two thirds he glistens, for the client and the server alike");
                    var finisher = com.jayptucker.heroesevolved.combat.Takedowns.variants(ModAbilities.QUAKE_ID).getFirst();
                    com.jayptucker.heroesevolved.combat.Takedowns.start(fighter, sylar[0], finisher);
                }))
                .thenWaitUntil(() -> helper.assertTrue(!com.jayptucker.heroesevolved.combat.Takedowns.running(fighter) && sylar[0].phase() == 2,
                        "A real finisher plays out and forces Unlocked"))
                .thenExecute(guard(() -> helper.assertTrue(sylar[0].isAlive() && sylar[0].getHealth() <= sylar[0].getMaxHealth() * SylarEntity.PHASE_TWO,
                        "It took a slice and didn't kill him: " + sylar[0].getHealth())))
                .thenExecuteAfter(SylarEntity.TRANSITION_TWO + 4, guard(() -> {
                    helper.assertTrue(sylar[0].is(SylarEntity.STAGGERED), "He reels once the transition is over");
                    sylar[0].setHealth(sylar[0].getMaxHealth() * 0.3F);
                }))
                .thenExecuteAfter(SylarEntity.TRANSITION_THREE + 4, guard(() -> {
                    helper.assertTrue(sylar[0].phase() == 3, "The Masterwork");
                    sylar[0].setHealth(sylar[0].getMaxHealth() * 0.08F);
                }))
                .thenExecuteAfter(2, guard(() -> {
                    try {
                        helper.assertTrue(sylar[0].finishWindow(), "In his last tenth he glistens");
                        sylar[0].phaseFinished(fighter);
                        helper.assertTrue(sylar[0].is(SylarEntity.DOWNED), "At the end a finish is the killing blow (Claire's case still gets him up)");
                        helper.succeed();
                    } finally { remove(fighter); }
                }));
    }

    // ------------------------------------------------------------------
    // The prisoner and the list
    // ------------------------------------------------------------------

    /** Wakes him for {@code player} with the hatch in the test's own floor, and freezes the story's nights. */
    private static SylarData wake(GameTestHelper helper, ServerPlayer player) {
        SylarData data = SylarData.get(player.getServer());
        // The hatch goes in the test's floor, not some real bunker's.
        data.hatch(helper.absolutePos(new BlockPos(1, 1, 1)));
        PlayerProgressionService.setPowerLevel(player, com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON.sylarTicketPowerLevel.get());
        SylarStory.wake(player);
        data.lastNight(1_000_000);
        return data;
    }

    private static net.minecraft.network.chat.Component sign(ServerLevel level, String marker, int line) {
        var sign = (net.minecraft.world.level.block.entity.SignBlockEntity) level.getBlockEntity(LevelFive.at(marker));
        return sign.getFrontText().getMessage(line, false);
    }

    @GameTest(template = "rewind_empty", batch = "sylar.story_wake")
    public static void aPowerWorthTakingWakesHimPutsDownTheHatchAndWritesTheList(GameTestHelper helper) {
        reset(helper);
        ServerPlayer player = player(helper, ModAbilities.QUAKE_ID, 4, "collection-listed");
        try {
            // The test level stands in for the Overworld only if it is the Overworld: the story looks there.
            boolean overworld = player.level().dimension() == net.minecraft.world.level.Level.OVERWORLD;
            SylarData data = SylarData.get(player.getServer());
            data.hatch(helper.absolutePos(new BlockPos(1, 1, 1)));
            PlayerProgressionService.setPowerLevel(player, 2);
            helper.assertTrue(!SylarStory.check(player), "Not before their power is worth taking");
            PlayerProgressionService.setPowerLevel(player, com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON.sylarTicketPowerLevel.get());
            if (overworld) {
                helper.assertTrue(SylarStory.check(player), "At the configured Power Level, he wakes");
                helper.assertTrue(data.stage() == SylarData.AWAKE && data.listed().containsKey(player.getUUID()), "and their name is on his list");
                helper.assertTrue(!SylarStory.check(player), "Only once");
                helper.assertTrue(LevelFive.isHatch(helper.getLevel().getBlockState(data.hatch())), "The hatch is down: " + helper.getLevel().getBlockState(data.hatch()));
                helper.assertTrue(data.list().size() == SylarStory.LIVING && data.list().stream().noneMatch(e -> e.crossed)
                        && data.list().stream().map(e -> e.power).distinct().count() == SylarStory.LIVING, "Three living names, three different gifts");
                helper.assertTrue(!has(player, ModItems.REPAIR_TICKET.get()), "No ticket turns up any more: the first fight is the story's");
                List<net.minecraft.network.chat.Component> lines = SylarStory.listLines(player.getServer());
                helper.assertTrue(lines.size() == SylarStory.OLD.size() + SylarStory.LIVING + 1, "Old names, the living, and theirs: " + lines.size());
                helper.assertTrue(lines.getFirst().getString().equals("R. Calder") && lines.getFirst().getStyle().isStrikethrough(),
                        "The old names are crossed out where their power stands in a case");
                helper.assertTrue(!lines.get(SylarStory.OLD.size()).getStyle().isStrikethrough() && lines.getLast().getString().equals("collection-listed"),
                        "The living aren't, yet, and the player's name is last");
            }
            // The night turns over at dusk, so a night slept through still counts.
            helper.assertTrue(SylarStory.nightOf(12_999) == -1 && SylarStory.nightOf(13_000) == 0 && SylarStory.nightOf(24_000) == 0
                    && SylarStory.nightOf(37_000) == 1, "Nights turn over at dusk");
            // The record of his theme is a real jukebox song, dropped now and then by a re-fight.
            var songs = player.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.JUKEBOX_SONG);
            helper.assertTrue(songs.containsKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("heroesevolved", "the_collection"))
                    && new ItemStack(ModItems.MUSIC_DISC_THE_COLLECTION.get()).has(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE)
                    && CollectionArena.RECORD_CHANCE > 0 && CollectionArena.RECORD_CHANCE < 1, "The record plays in a jukebox");
            // He keeps nothing of yours: what is dropped in his hall comes back.
            data.stash(player.getUUID(), new ItemStack(Items.DIAMOND, 3), player.registryAccess());
            List<ItemStack> back = data.unstash(player.getUUID(), player.registryAccess());
            helper.assertTrue(back.size() == 1 && back.getFirst().is(Items.DIAMOND) && back.getFirst().getCount() == 3 && !data.hasStash(player.getUUID()),
                    "What was kept for them comes back whole");
            helper.succeed();
        } finally { remove(player); }
    }

    @GameTest(template = "rewind_empty", batch = "sylar.story_nights", timeoutTicks = 200)
    public static void eachNightCrossesOutANameUnlessSomeoneGuardsItAndHisDefeatGivesTheGiftsBack(GameTestHelper helper) {
        reset(helper);
        ServerPlayer player = player(helper, ModAbilities.PYROKINESIS_ID, 4, "collection-guard");
        try {
            SylarData data = wake(helper, player);
            var server = player.getServer();
            SylarData.Entry first = data.list().get(0), second = data.list().get(1), third = data.list().get(2);
            var villager = net.minecraft.world.entity.EntityType.VILLAGER.create(helper.getLevel());
            villager.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(6, 2, 5))));
            helper.getLevel().addFreshEntity(villager);
            SylarStory.name(villager, first);
            helper.assertTrue(villager.getCustomName().getString().equals(first.name) && villager.getTags().contains(SylarStory.LISTED_TAG)
                    && first.power.equals(com.jayptucker.heroesevolved.mimicry.GiftedVillagers.gift(villager)),
                    "A villager carries the first name, and its gift, dormant");
            // Standing by them when the night turns keeps them off his round: someone else goes instead.
            SylarData.Entry crossed = SylarStory.cross(server);
            helper.assertTrue(crossed == second && !first.crossed && second.crossed, "The guarded name is spared; the next one goes");
            // Nobody watching (a creative player doesn't count): the first name goes, and its villager is emptied.
            player.setGameMode(GameType.CREATIVE);
            crossed = SylarStory.cross(server);
            helper.assertTrue(crossed == first && first.emptied && villager.getTags().contains(SylarStory.EMPTIED_TAG)
                    && com.jayptucker.heroesevolved.mimicry.GiftedVillagers.gift(villager) == null
                    && villager.getCustomName().getStyle().isItalic(), "Crossed out: the villager sits there emptied, their name in grey");
            helper.assertTrue(SylarStory.cross(server) == third && data.list().stream().allMatch(e -> e.crossed), "The third night, the last living name");
            helper.assertTrue(SylarStory.listLines(server).get(SylarStory.OLD.size()).getStyle().isStrikethrough(), "Crossed out on the wall too");
            // The night after the last name: his cell stands open, and he comes for someone on his list.
            player.setGameMode(GameType.SURVIVAL);
            data.lastNight(SylarStory.nightOf(SylarStory.clock(server.overworld())) - 1);
            SylarStory.night(server, data);
            helper.assertTrue(data.stage() == SylarData.OUT && SylarStory.coming(), "With no name left above theirs, he is out and coming");
            SylarStory.reset();
            // Beaten: the people he emptied get their gifts back.
            SylarStory.beaten(server);
            helper.assertTrue(data.stage() == SylarData.BEATEN && !first.emptied && !villager.getTags().contains(SylarStory.EMPTIED_TAG)
                    && first.power.equals(com.jayptucker.heroesevolved.mimicry.GiftedVillagers.gift(villager)), "Beaten, he gives back what he took");
            helper.assertTrue(SylarStory.listLines(server).getLast().getString().equals("G. Gray"), "and his own name is crossed out last");
            villager.discard();
            helper.succeed();
        } finally { remove(player); }
    }

    @GameTest(template = "rewind_empty", batch = "sylar.story_level5", timeoutTicks = 500)
    public static void levelFiveKeepsHimBehindGlassAndTheLeverLetsHimOut(GameTestHelper helper) {
        reset(helper);
        ServerPlayer player = player(helper, ModAbilities.QUAKE_ID, 4, "collection-visitor");
        SylarData data = wake(helper, player);
        var server = player.getServer();
        helper.assertTrue(LevelFive.level(server) != null, "Level 5's dimension must load from the datapack");
        LevelFive.descend(player);
        ServerLevel five = LevelFive.level(server);
        helper.assertTrue(LevelFive.in(player) && player.position().distanceTo(LevelFive.arrival()) < 1.5, "Down the hatch: out of the lift in Level 5");
        SylarEntity prisoner = LevelFive.prisoner(five);
        helper.assertTrue(data.visits(player.getUUID()) == 1 && prisoner != null && LevelFive.prisoner(five) == prisoner
                && prisoner.position().distanceTo(LevelFive.cell()) < 0.5, "He is in his cell, and only one of him");
        helper.assertTrue(LevelFive.marked("glass").size() == 27 && LevelFive.marked("glass").stream().allMatch(p -> five.getBlockState(p).is(Blocks.GLASS)),
                "Behind nine by three of glass");
        helper.assertTrue(sign(five, "sign:list1", 0).getString().equals("R. Calder") && sign(five, "sign:list1", 0).getStyle().isStrikethrough()
                && sign(five, "sign:list4", 0).getString().equals("collection-visitor"), "The list on the wall, with the visitor's name on it");
        float health = prisoner.getHealth();
        prisoner.hurt(player.damageSources().playerAttack(player), 10);
        helper.assertTrue(prisoner.getHealth() == health && prisoner.actor(), "Nothing hurts the man in the cell");
        // Up to the glass: he talks, and tells them something true about their power.
        Vec3 glass = LevelFive.glassMiddle();
        LevelFive.move(player, five, new Vec3(glass.x, LevelFive.arrival().y, glass.z + 2.5), 180, 0);
        helper.runAfterDelay(25, guard(() -> {
            helper.assertTrue(SylarStory.talkKeys(player).contains("story.heroesevolved.sylar.prisoner.hint.quake"), "His first visit carries a hint for their power: "
                    + SylarStory.talkKeys(player));
            // The lever is stiff: one pull isn't enough, two are.
            SylarStory.lever(player);
            helper.assertTrue(data.stage() == SylarData.AWAKE, "One pull only loosens it");
            SylarStory.lever(player);
            helper.assertTrue(data.stage() == SylarData.OUT && SylarStory.coming() && LevelFive.marked("glass").stream().allMatch(p -> five.getBlockState(p).isAir()),
                    "Two: his glass is gone, and he is coming");
        }));
        helper.runAfterDelay(25 + SylarStory.REVEAL_TICKS, guard(() -> {
            SylarEntity boss = CollectionArena.sylar();
            helper.assertTrue(boss != null && CollectionArena.inCollection(player) && boss.party().contains(player), "His hand on their wrist: the Collection");
            helper.assertTrue(LevelFive.prisoners(five).isEmpty(), "and his cell is empty");
            CollectionArena.end(server, CollectionArena.Outcome.ABANDONED);
        }));
        helper.runAfterDelay(25 + SylarStory.REVEAL_TICKS + 40, guard(() ->
                helper.assertTrue(LevelFive.in(player), "Home again is where they were taken: Level 5")));
        helper.runAfterDelay(25 + SylarStory.REVEAL_TICKS + 40 + SylarStory.CELL_EMPTY_TICKS + 40, guard(() -> {
            helper.assertTrue(!SylarStory.coming() && !CollectionArena.open(), "He doesn't come again until they come down again");
            LevelFive.ascend(player);
            helper.assertTrue(player.level().dimension() == net.minecraft.world.level.Level.OVERWORLD
                    && player.blockPosition().equals(data.hatch().above()), "The lift goes up to the hatch");
            remove(player);
            SylarStory.reset();
            helper.succeed();
        }));
    }

    @GameTest(template = "rewind_empty", batch = "sylar.story_reveal", timeoutTicks = 500)
    public static void outOfHisCellHeComesUpBehindYouAndBeatingHimEndsTheStory(GameTestHelper helper) {
        reset(helper);
        ServerPlayer player = player(helper, ModAbilities.ELECTROKINESIS_ID, 4, "collection-next");
        SylarData data = wake(helper, player);
        var server = player.getServer();
        Vec3 stood = player.position();
        for (SylarData.Entry e : data.list()) e.crossed = true;
        SylarStory.out(server);
        helper.assertTrue(data.stage() == SylarData.OUT, "Every living name crossed out: his cell stands open");
        SylarStory.comeFor(player, 0);
        SylarEntity[] behind = new SylarEntity[1];
        helper.runAfterDelay(5, guard(() -> {
            List<SylarEntity> near = helper.getLevel().getEntitiesOfClass(SylarEntity.class, player.getBoundingBox().inflate(4), SylarEntity::actor);
            helper.assertTrue(near.size() == 1, "He is standing behind them");
            behind[0] = near.getFirst();
            Vec3 toHim = behind[0].position().subtract(player.position());
            helper.assertTrue(toHim.horizontalDistance() < 3.5, "close: " + toHim);
        }));
        helper.runAfterDelay(SylarStory.REVEAL_TICKS + 5, guard(() -> {
            SylarEntity boss = CollectionArena.sylar();
            helper.assertTrue(boss != null && CollectionArena.inCollection(player) && behind[0].isRemoved(), "A few words, then his hall");
            CollectionArena.defeated(server, boss, player.getUUID());
            helper.assertTrue(data.stage() == SylarData.BEATEN && data.wins(player.getUUID()) == 1, "Beaten: the story is over");
        }));
        helper.runAfterDelay(SylarStory.REVEAL_TICKS + 5 + 160, guard(() -> {
            helper.assertTrue(player.level().dimension() == net.minecraft.world.level.Level.OVERWORLD && player.position().distanceTo(stood) < 1.5,
                    "Home, where he found them");
            helper.assertTrue(has(player, ModItems.SYLARS_WATCH.get()) && has(player, ModItems.BRASS_MOVEMENT.get()), "with his watch and a movement for a ticket");
            SylarStory.comeFor(player, 0);
            remove(player);
            SylarStory.reset();
            helper.succeed();
        }));
    }
}
