package com.jayptucker.heroesevolved.story.prologue;

import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.story.AvenueService;
import com.jayptucker.heroesevolved.story.bunker.BunkerBuilder;
import com.jayptucker.heroesevolved.story.bunker.BunkerData;
import com.jayptucker.heroesevolved.story.bunker.BunkerService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.bunker.PastBunker;
import com.jayptucker.heroesevolved.time.intro.FractureCity;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import com.jayptucker.heroesevolved.time.intro.city.Apartments;
import com.jayptucker.heroesevolved.time.intro.city.CityGrid;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The Last Day: apartments, the evening, the light, the safe house, Noah's scene, the pods and the hand-off. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class PrologueGameTests {
    private PrologueGameTests() {}

    private static ServerPlayer player(GameTestHelper helper, Vec3 pos, String name) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name)) {
            @Override public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
                super.teleportTo(level, x, y, z, yaw, pitch);
                absMoveTo(x, y, z, yaw, pitch);
            }
            @Override public void teleportTo(double x, double y, double z) {
                super.teleportTo(x, y, z);
                absMoveTo(x, y, z, getYRot(), getXRot());
            }
        };
        try {
            Connection connection = player.connection.getConnection();
            if (connection.channel() == null) {
                Field channel = Connection.class.getDeclaredField("channel");
                channel.setAccessible(true);
                channel.set(connection, new EmbeddedChannel());
            }
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        player.setPos(pos);
        helper.getLevel().addNewPlayer(player);
        PlayerList list = helper.getLevel().getServer().getPlayerList();
        PrologueGameTests.<List<ServerPlayer>>field(list, "players").add(player);
        PrologueGameTests.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").put(player.getUUID(), player);
        return player;
    }

    private static void remove(ServerPlayer player) {
        com.jayptucker.heroesevolved.PowerOwnershipTestSupport.release(player);
        PrologueService.logout(player);
        if (CryoChamberService.isInside(player)) CryoChamberService.release(player);
        TimeIntroService.logout(player);
        PlayerList list = player.getServer().getPlayerList();
        PrologueGameTests.<List<ServerPlayer>>field(list, "players").remove(player);
        PrologueGameTests.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").remove(player.getUUID());
        player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object owner, String name) {
        try {
            Field f = PlayerList.class.getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(owner);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    /** Two new players live the whole day: one runs down the stair to the vault, the other is darted in the street. */
    @GameTest(template = "rewind_empty", batch = "PrologueGameTests.wholeDay", timeoutTicks = 2400)
    public static void theLastDayRunsFromApartmentToPod(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel city = server.getLevel(TimeIntroService.DIMENSION);
        helper.assertTrue(city != null, "The city dimension loads");
        ServerPlayer[] players = new ServerPlayer[2];
        int[] pods = {-1, -1};
        Runnable cleanup = () -> { for (ServerPlayer p : players) if (p != null) remove(p); };
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(FractureCity.get(city).ready() && !TimeIntroService.sceneRunning()
                        && !AvenueService.running() && !PrologueService.running(), "Waiting for the city"))
                .thenExecute(() -> {
                    Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3)));
                    players[0] = player(helper, origin, "lastday-runner");
                    players[1] = player(helper, origin.add(2, 0, 0), "lastday-straggler");
                    for (ServerPlayer p : players) PrologueService.join(p);
                })
                .thenIdle(5)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.phase().equals("apartment"), "The day begins in the apartments: " + PrologueService.phase());
                    Set<Integer> units = new HashSet<>();
                    for (ServerPlayer p : players) {
                        helper.assertTrue(TimeIntroService.isCity(p.level()), "Everyone wakes in Odessa");
                        int unit = -1;
                        for (int i = 0; i < Apartments.UNITS; i++) if (Apartments.inside(i, p.position())) unit = i;
                        helper.assertTrue(unit >= 0, "Everyone wakes in an apartment: " + p.position());
                        units.add(unit);
                        BlockPos door = Apartments.door(unit);
                        helper.assertTrue(city.getBlockState(door).getBlock() instanceof DoorBlock && !city.getBlockState(door).getValue(DoorBlock.OPEN),
                                "Apartment doors stay shut until everyone is ready");
                    }
                    helper.assertTrue(units.size() == 2, "Each player has their own apartment");
                    // Trying your door says you're ready; the day waits for the other.
                    helper.assertTrue(PrologueService.useBlock(players[0], Apartments.door(units.iterator().next()))
                            || PrologueService.useBlock(players[0], Apartments.door(units.stream().skip(1).findFirst().orElseThrow())), "The door counts as ready");
                    helper.assertTrue(PrologueService.phase().equals("apartment"), "One ready isn't everyone");
                    PrologueService.ready(players[1]);
                }))
                .thenIdle(2)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.phase().equals("evening"), "Everyone ready: the evening begins");
                    for (BlockPos door : Apartments.doors())
                        helper.assertTrue(city.getBlockState(door).getValue(DoorBlock.OPEN), "The apartment doors open");
                    helper.assertTrue(PrologueService.skipTo(server, "light", 0), "The light can be reached");
                }))
                // Freshly forced chunks take a while to show their entities (see AGENTS.md): wait for everyone.
                .thenWaitUntil(() -> helper.assertTrue(city.getEntities(ModEntities.STORY_ACTOR.get(), PrologueService::isScene).size() == 2
                        && city.getEntities(EntityType.VILLAGER, PrologueService::isScene).size() == TimeIntroService.CROWD_SIZE,
                        "Noah and Claire wait in the safe house, and Odessa's people are on the streets"))
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.phase().equals("light"), "The light: " + PrologueService.phase());
                    helper.assertTrue(PrologueService.vaultOpen(city), "The Company opens the vault at the foot of the stair");
                    walkable(helper, city);
                    standing(helper, city);
                    // One runs down to the vault; the other dawdles in the street.
                    Vec3 landing = PrologueService.landing();
                    players[0].teleportTo(city, landing.x, landing.y, landing.z, 0, 0);
                    players[1].teleportTo(city, -28.5, CityGrid.STREET_Y, 68.5, 0, 0);
                }))
                .thenIdle(3)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.sheltered(players[0]), "Down the stair is safe");
                    helper.assertTrue(!PrologueService.sheltered(players[1]), "The street isn't");
                    int run = 20 * com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON.prologueRunSeconds.get();
                    PrologueService.skipTo(server, "light", run);
                }))
                .thenIdle(60)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.sheltered(players[1]), "A straggler is darted and carried down");
                    helper.assertTrue(CryoChamberService.isInside(players[1]), "...and sealed in a pod");
                    pods[1] = PrologueService.pod(players[1]);
                    helper.assertTrue(pods[1] >= 0 && pods[1] != PrologueService.CLAIRE, "Never in Claire's pod: " + pods[1]);
                    BlockPos o = PrologueService.LastDayData.origin();
                    players[0].teleportTo(city, o.getX() + .5, o.getY() + 1, o.getZ() + 21.5, 0, 0);
                }))
                .thenIdle(3)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.phase().equals("noah"), "Everyone gathered: Noah's scene: " + PrologueService.phase());
                    helper.assertTrue(!PrologueService.vaultOpen(city), "The vault shuts behind the last of them");
                    PrologueService.skipTo(server, "noah", 275);
                }))
                .thenIdle(10)
                .thenExecute(() -> guard(cleanup, () -> {
                    BlockPos claire = PrologueService.LastDayData.origin().offset(BunkerBuilder.podOffset(PrologueService.CLAIRE));
                    helper.assertTrue(city.getBlockState(claire).is(ModBlocks.CRYOCHAMBER.get())
                            && !city.getBlockState(claire).getValue(com.jayptucker.heroesevolved.story.bunker.CryoChamberBlock.OPEN),
                            "Noah seals Claire in Pod 14");
                    PrologueService.skipTo(server, "noah", 455);
                }))
                .thenIdle(80)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.phase().equals("pods"), "After the stun gun, the pods: " + PrologueService.phase());
                    helper.assertTrue(CryoChamberService.isInside(players[0]), "Everyone is sealed in");
                    pods[0] = PrologueService.pod(players[0]);
                    helper.assertTrue(pods[0] >= 0 && pods[0] != pods[1] && pods[0] != PrologueService.CLAIRE, "Each has their own pod");
                    PrologueService.skipTo(server, "pods", 165);
                }))
                .thenIdle(45)
                .thenExecute(() -> guard(cleanup, () -> {
                    // The blow has reached the hall: stone down the aisle, and lights out.
                    BlockPos o = PrologueService.LastDayData.origin();
                    int rubble = 0, dark = 0, lit = 0;
                    for (BlockPos pos : BlockPos.betweenClosed(o.offset(-7, 1, 18), o.offset(7, 13, 40))) {
                        var state = city.getBlockState(pos);
                        if (state.is(Blocks.COBBLED_DEEPSLATE) || state.is(Blocks.GRAVEL)) rubble++;
                        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT))
                            if (state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) lit++; else dark++;
                    }
                    rubble += city.getEntities(EntityType.FALLING_BLOCK, PrologueService::isScene).size();
                    helper.assertTrue(rubble >= 3, "Rubble comes down from the ceiling when the blast hits: " + rubble);
                    helper.assertTrue(dark >= 1, "Some of the hall's lights are out: " + dark + " dark, " + lit + " lit");
                    PrologueService.skipTo(server, "pods", 490);
                }))
                .thenIdle(20)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(!PrologueService.running(), "The day is over");
                    BunkerData data = BunkerData.get(server);
                    for (int i = 0; i < 2; i++) {
                        helper.assertTrue(players[i].level() == server.overworld(), "103 years later: the bunker under spawn");
                        helper.assertTrue(BunkerService.isAwakening(players[i]), "The usual waking begins");
                        helper.assertTrue(data.pods.get(players[i].getUUID()) == pods[i], "Woken in the pod with the same number");
                        helper.assertTrue(players[i].getPersistentData().getBoolean(PrologueService.SLEPT), "Never lives the day twice");
                    }
                    helper.assertTrue(city.getEntities(ModEntities.STORY_ACTOR.get(), e -> true).isEmpty()
                            && city.getEntities(EntityType.VILLAGER, PrologueService::isScene).isEmpty(), "The city is cleared for the next");
                    helper.assertTrue(city.getForcedChunks().isEmpty(), "The city's chunks are let go");
                }))
                .thenExecute(cleanup)
                .thenSucceed();
    }

    /**
     * The way down, step by step: the lobby, the top flight east, the turn, the lower flight west, the landing, through
     * the open vault and into Records. Every place a foot lands is solid underfoot with two blocks of air above.
     */
    private static void walkable(GameTestHelper helper, ServerLevel city) {
        BlockPos o = PrologueService.LastDayData.origin();
        java.util.List<int[]> route = new java.util.ArrayList<>();
        route.add(new int[] {CityGrid.STAIR_X0 - 1, CityGrid.STREET_Y + 1, 110});
        for (int x = CityGrid.STAIR_X0; x <= CityGrid.STAIR_X1; x++) route.add(new int[] {x, CityGrid.STREET_Y - (x - CityGrid.STAIR_X0), 110});
        route.add(new int[] {17, 56, 110});
        route.add(new int[] {17, 56, 106});
        for (int x = 15; x >= 8; x--) route.add(new int[] {x, 48 + (x - 8), 106});
        for (int z = 106; z <= 114; z++) route.add(new int[] {o.getX(), o.getY() + 1, z});
        for (int[] r : route) {
            BlockPos feet = new BlockPos(r[0], r[1], r[2]);
            helper.assertTrue(city.getBlockState(feet).getCollisionShape(city, feet).isEmpty()
                            && city.getBlockState(feet.above()).getCollisionShape(city, feet.above()).isEmpty(),
                    "Room to walk at " + feet.toShortString() + ": " + city.getBlockState(feet) + " / " + city.getBlockState(feet.above()));
            BlockPos under = feet.below();
            helper.assertTrue(!city.getBlockState(under).getCollisionShape(city, under).isEmpty()
                            || !city.getBlockState(feet).getCollisionShape(city, feet).isEmpty(),
                    "Something to stand on at " + under.toShortString() + ": " + city.getBlockState(under));
        }
    }

    /**
     * Fourteen new players at once: twelve apartments, so two are shared, each sharer waking on their own spot with both
     * names on the door. A fifteenth who joins once the doors are open still gets the evening; a sixteenth who joins once
     * the light is up waits for the next day.
     */
    @GameTest(template = "rewind_empty", batch = "PrologueGameTests.crowd", timeoutTicks = 1200)
    public static void aCrowdSharesApartmentsAndLateComersStillGetTheEvening(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel city = server.getLevel(TimeIntroService.DIMENSION);
        List<ServerPlayer> players = new java.util.ArrayList<>();
        Runnable cleanup = () -> { players.forEach(PrologueGameTests::remove); players.clear(); };
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(FractureCity.get(city).ready() && !TimeIntroService.sceneRunning()
                        && !AvenueService.running() && !PrologueService.running(), "Waiting for the city"))
                .thenExecute(() -> {
                    Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3)));
                    for (int i = 0; i < 14; i++) players.add(player(helper, origin, "lastday-crowd-" + i));
                    players.forEach(PrologueService::join);
                })
                .thenIdle(5)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.phase().equals("apartment"), "Fourteen wake together: " + PrologueService.phase());
                    Map<Integer, List<ServerPlayer>> homes = new java.util.HashMap<>();
                    Set<BlockPos> spots = new HashSet<>();
                    for (ServerPlayer p : players) {
                        int unit = -1;
                        for (int i = 0; i < Apartments.UNITS; i++) if (Apartments.inside(i, p.position())) unit = i;
                        helper.assertTrue(unit >= 0, p.getGameProfile().getName() + " wakes in an apartment: " + p.position());
                        homes.computeIfAbsent(unit, k -> new java.util.ArrayList<>()).add(p);
                        helper.assertTrue(spots.add(p.blockPosition()), "Nobody wakes on top of anyone else: " + p.blockPosition());
                    }
                    helper.assertTrue(homes.size() == Apartments.UNITS, "Every apartment is lived in: " + homes.size());
                    long shared = homes.values().stream().filter(l -> l.size() == 2).count();
                    helper.assertTrue(shared == 2 && homes.values().stream().allMatch(l -> l.size() <= 2), "Two apartments are shared, none by three");
                    homes.forEach((unit, living) -> {
                        if (living.size() < 2) return;
                        var sign = (net.minecraft.world.level.block.entity.SignBlockEntity) city.getBlockEntity(Apartments.plate(unit));
                        String second = sign.getFrontText().getMessage(2, false).getString(), third = sign.getFrontText().getMessage(3, false).getString();
                        helper.assertTrue(second.equals(living.get(0).getGameProfile().getName()) && third.equals(living.get(1).getGameProfile().getName()),
                                "Both names on the door of " + Apartments.number(unit) + ": " + second + " / " + third);
                    });
                    players.forEach(PrologueService::ready);
                }))
                .thenIdle(2)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.phase().equals("evening"), "Everyone ready: out into the evening");
                    ServerPlayer late = player(helper, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 5))), "lastday-late");
                    players.add(late);
                    PrologueService.join(late);
                    helper.assertTrue(PrologueService.isMember(late) && TimeIntroService.isCity(late.level()), "A late arrival still gets the evening");
                    int unit = -1;
                    for (int i = 0; i < Apartments.UNITS; i++) if (Apartments.inside(i, late.position())) unit = i;
                    helper.assertTrue(unit >= 0 && city.getBlockState(Apartments.door(unit)).getValue(DoorBlock.OPEN),
                            "...waking in an apartment whose door is already open");
                    PrologueService.skipTo(server, "light", 0);
                    ServerPlayer later = player(helper, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 2, 7))), "lastday-later");
                    players.add(later);
                    PrologueService.join(later);
                    helper.assertTrue(!PrologueService.isMember(later) && PrologueService.isWaiting(later),
                            "Once the light is up, a newcomer waits for the next Last Day");
                }))
                .thenExecute(cleanup)
                .thenWaitUntil(() -> helper.assertTrue(!PrologueService.running(), "With everyone gone, the day ends"))
                .thenSucceed();
    }

    /** Every guard and every Bennet stands on a floor in open air: none in a wall, a desk, a pod or the vault's swing. */
    private static void standing(GameTestHelper helper, ServerLevel city) {
        List<net.minecraft.world.entity.Entity> cast = new java.util.ArrayList<>();
        cast.addAll(city.getEntities(ModEntities.COMPANY_AGENT.get(), PrologueService::isScene));
        cast.addAll(city.getEntities(ModEntities.STORY_ACTOR.get(), PrologueService::isScene));
        helper.assertTrue(cast.size() >= 10, "The guards, Noah and Claire are all placed: " + cast.size());
        for (var who : cast) {
            String name = who.getName().getString() + " at " + who.blockPosition().toShortString();
            helper.assertTrue(city.noCollision(who, who.getBoundingBox().deflate(1e-3)), name + " stands clear of every block");
            helper.assertTrue(!city.noCollision(who, who.getBoundingBox().move(0, -0.2, 0).deflate(1e-3, 0, 1e-3)), name + " stands on something");
        }
    }

    private static void guard(Runnable cleanup, Runnable step) {
        try { step.run(); } catch (RuntimeException | AssertionError e) { cleanup.run(); throw e; }
    }

    /**
     * Peter and Sylar's fight over Kirby Plaza (time/intro/SkyFight): every tick of it in open air, nobody jumping, and the
     * throw finding the west tower's face close to where the Last Day's far view draws the crash.
     */
    @GameTest(template = "rewind_empty", batch = "PrologueGameTests.geometry")
    public static void theSkyFightHasRoomAndAWallToHit(GameTestHelper helper) {
        ServerLevel city = helper.getLevel().getServer().getLevel(TimeIntroService.DIMENSION);
        FractureCity.rebuild(city, -24, -104, 24, -36);
        var vision = com.jayptucker.heroesevolved.time.intro.SkyFight.VISION;
        Vec3 impact = com.jayptucker.heroesevolved.time.intro.VisionFightAccess.impact(city);
        helper.assertTrue(impact.distanceTo(vision.impactGuess()) < 6,
                "Sylar hits the west tower where the far view draws it: " + impact);
        helper.assertTrue(!city.getBlockState(BlockPos.containing(impact.add(
                vision.centre().subtract(impact).scale(-1).normalize().scale(1.0)))).isAir(),
                "...a real wall just past the impact point");
        for (var script : List.of(vision, com.jayptucker.heroesevolved.time.intro.SkyFight.LAST_DAY)) {
        Vec3 wall = script == vision ? impact : script.impactGuess();
        Vec3[] last = null;
        for (int t = 0; t <= script.landAt(); t++) {
            Vec3[] at = com.jayptucker.heroesevolved.time.intro.SkyFight.at(script, t, wall);
            for (int who = 0; who < 2; who++) {
                if (at[who] == null) continue;
                boolean flinging = who == 1 && t > script.impactAt() - 4;
                boolean landing = who == 0 && t >= script.landAt() - 1;
                BlockPos feet = BlockPos.containing(at[who]), head = feet.above();
                if (!flinging && !landing)
                    helper.assertTrue(city.getBlockState(feet).getCollisionShape(city, feet).isEmpty()
                                    && city.getBlockState(head).getCollisionShape(city, head).isEmpty(),
                            (who == 0 ? "Peter" : "Sylar") + " flies in open air at tick " + t + ": " + feet.toShortString() + (script == vision ? " (vision)" : " (Last Day)"));
                if (last != null && last[who] != null)
                    helper.assertTrue(at[who].distanceTo(last[who]) < 4.5, (who == 0 ? "Peter" : "Sylar") + " never jumps, tick " + t);
            }
            last = at;
        }
        }
        helper.succeed();
    }

    /** The buildings and rules the day depends on. */
    @GameTest(template = "rewind_empty", batch = "PrologueGameTests.geometry")
    public static void apartmentsShaftAndPodsFit(GameTestHelper helper) {
        ServerLevel city = helper.getLevel().getServer().getLevel(TimeIntroService.DIMENSION);
        Set<BlockPos> doors = new HashSet<>(Apartments.doors());
        helper.assertTrue(doors.size() == Apartments.UNITS, "Twelve apartments, twelve doors");
        for (int i = 0; i < Apartments.UNITS; i++)
            helper.assertTrue(Apartments.inside(i, Apartments.wake(i)), "Apartment " + Apartments.number(i) + " wakes its resident inside it");
        FractureCity.rebuild(city, Apartments.X0 - 2, Apartments.Z0 - 2, Apartments.X0 + Apartments.SIZE + 1, Apartments.Z0 + Apartments.SIZE + 1);
        for (int i = 0; i < Apartments.UNITS; i++) {
            helper.assertTrue(city.getBlockState(Apartments.door(i)).getBlock() instanceof DoorBlock, "Apartment " + i + " has a door");
            for (int seat = 0; seat < 3; seat++) {
                BlockPos wake = BlockPos.containing(Apartments.wake(i, seat));
                helper.assertTrue(Apartments.inside(i, Apartments.wake(i, seat)), "Sharer " + seat + " of " + i + " wakes inside it");
                helper.assertTrue(city.getBlockState(wake).getCollisionShape(city, wake).isEmpty()
                                && city.getBlockState(wake.above()).getCollisionShape(city, wake.above()).isEmpty(),
                        "Room to stand on waking in " + i + " (seat " + seat + "): " + city.getBlockState(wake));
            }
        }
        FractureCity.rebuild(city, CityGrid.STAIR_X0 - 2, CityGrid.STAIR_Z0 - 1, CityGrid.STAIR_X1 + 2, CityGrid.STAIR_Z1 + 2);
        helper.assertTrue(city.getBlockState(new BlockPos(CityGrid.STAIR_X0, CityGrid.STREET_Y - 1, CityGrid.STAIR_Z0)).is(Blocks.POLISHED_DEEPSLATE_STAIRS),
                "Primatech's lobby has the top of the stair");
        helper.assertTrue(city.getBlockState(new BlockPos(CityGrid.STAIR_X1, CityGrid.STREET_Y, CityGrid.STAIR_Z1)).isAir()
                && city.getBlockState(new BlockPos(CityGrid.STAIR_X1, CityGrid.STREET_Y + 1, CityGrid.STAIR_Z1 + 1)).is(Blocks.IRON_BARS),
                "...open to the lobby through a railed slot");
        var stair = com.jayptucker.heroesevolved.structure.Blueprint.load("lastday_stairwell");
        helper.assertTrue(stair.width() == 20 && stair.depth() == 9, "The stairwell blueprint loads");
        BlockPos origin = PrologueService.LastDayData.origin();
        helper.assertTrue(PastBunker.bounds(origin).maxY() < CityGrid.STREET_Y - 1, "The safe house sits below anything the city rebuilds");
        // Pod 14 is Claire's: nobody new is ever assigned to it.
        BunkerData scratch = new BunkerData();
        for (int i = 0; i < 40; i++)
            helper.assertTrue(scratch.pod(UUID.randomUUID(), BunkerBuilder.PODS, PrologueService.CLAIRE) != PrologueService.CLAIRE,
                    "Pod 14 stays Claire's");
        helper.succeed();
    }
}
