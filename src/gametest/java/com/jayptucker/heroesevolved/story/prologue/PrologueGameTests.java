package com.jayptucker.heroesevolved.story.prologue;

import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.story.AvenueService;
import com.jayptucker.heroesevolved.story.bunker.BunkerBuilder;
import com.jayptucker.heroesevolved.story.bunker.BunkerLayout;
import com.jayptucker.heroesevolved.story.bunker.BunkerData;
import com.jayptucker.heroesevolved.story.bunker.BunkerService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.bunker.PastBunker;
import com.jayptucker.heroesevolved.story.bunker.PodSleepers;
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
        BlockPos[] window = new BlockPos[1], crumbled = new BlockPos[1], marketDoor = new BlockPos[1];
        boolean[] marketOpen = new boolean[1];
        net.minecraft.world.entity.npc.Villager[] carried = new net.minecraft.world.entity.npc.Villager[1];
        Runnable cleanup = () -> { for (ServerPlayer p : players) if (p != null) remove(p); };
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(FractureCity.get(city).ready() && !TimeIntroService.sceneRunning()
                        && !AvenueService.running() && !PrologueService.running(), "Waiting for the city"))
                .thenExecute(() -> {
                    // Pods claimed by test players long gone are free again, so some are left for Odessa's people.
                    BunkerData.get(server).pods.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
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
                        helper.assertTrue(city.getBlockState(door).getBlock() instanceof DoorBlock && city.getBlockState(door).getValue(DoorBlock.OPEN),
                                "Apartment doors stand open: everyone comes down to the lobby");
                    }
                    helper.assertTrue(units.size() == 2, "Each player has their own apartment");
                    for (BlockPos front : Apartments.frontDoors())
                        helper.assertTrue(!city.getBlockState(front).getValue(DoorBlock.OPEN), "The street doors stay shut until everyone has met");
                    helper.assertTrue(PrologueService.useBlock(players[0], Apartments.frontDoors().get(0)), "Trying the street door says who we wait for");
                    // One comes down to the lobby; the day waits for the other.
                    Vec3 lobby = Apartments.lobby();
                    players[0].teleportTo(city, lobby.x, lobby.y, lobby.z, 0, 0);
                }))
                .thenIdle(3)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.phase().equals("apartment"), "Coming down isn't saying you're ready");
                    PrologueService.ready(players[0]);
                    PrologueService.ready(players[1]);
                    helper.assertTrue(!PrologueService.chat(players[1], "ready"), "Saying it upstairs doesn't count: everyone meets in the lobby");
                    Vec3 lobby = Apartments.lobby().add(1, 0, 0);
                    players[1].teleportTo(city, lobby.x, lobby.y, lobby.z, 0, 0);
                }))
                .thenIdle(3)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.phase().equals("apartment"), "Both in the lobby, one ready: the doors wait for the other");
                    PrologueService.goWithout(players[0]);
                    helper.assertTrue(PrologueService.phase().equals("apartment"), "Nobody may go on without a friend before they've waited for them");
                    helper.assertTrue(PrologueService.chat(players[1], "I'm ready!"), "Typing ready in the lobby counts");
                }))
                .thenIdle(2)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.phase().equals("evening"), "Everyone ready in the lobby: the evening begins");
                    for (BlockPos door : Apartments.frontDoors())
                        helper.assertTrue(city.getBlockState(door).getValue(DoorBlock.OPEN), "The street doors open");
                    helper.assertTrue(PrologueService.lightIn() < 0, "Nobody has been to the market yet");
                    // Out front isn't the errand: the checkpoint is inside.
                    Vec3 beacon = com.jayptucker.heroesevolved.time.intro.city.Market.beacon();
                    for (int k = 0; k < players.length; k++) players[k].teleportTo(city, beacon.x + k, beacon.y + 1, beacon.z, 0, 0);
                    // The market's doors open and shut by hand, logged so the day puts them back.
                    List<BlockPos> doors = com.jayptucker.heroesevolved.time.intro.city.Market.doors(city);
                    helper.assertTrue(!doors.isEmpty(), "The City Market has doors on the street");
                    marketDoor[0] = doors.get(0);
                    marketOpen[0] = city.getBlockState(marketDoor[0]).getValue(DoorBlock.OPEN);
                    helper.assertTrue(PrologueService.useDoor(players[0], marketDoor[0].above()), "A player can work the market's door");
                    helper.assertTrue(city.getBlockState(marketDoor[0]).getValue(DoorBlock.OPEN) != marketOpen[0]
                            && city.getBlockState(marketDoor[0].above()).getValue(DoorBlock.OPEN) != marketOpen[0], "...both halves of it");
                    helper.assertTrue(PrologueService.changedToday(marketDoor[0]), "...and the day remembers how it was");
                }))
                .thenIdle(12)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.lightIn() < 0, "Waiting out front doesn't count: the groceries are at the checkout");
                    // Everyone steps up to the checkout counter inside.
                    Vec3 checkout = com.jayptucker.heroesevolved.time.intro.city.Market.checkout();
                    for (int k = 0; k < players.length; k++) players[k].teleportTo(city, checkout.x + k, checkout.y, checkout.z, 0, 0);
                    helper.assertTrue(com.jayptucker.heroesevolved.time.intro.city.Market.arrived(players[0].position()), "The checkout is on the shop floor");
                }))
                .thenIdle(12)
                .thenExecute(() -> guard(cleanup, () -> {
                    int in = PrologueService.lightIn();
                    helper.assertTrue(in > 0 && in <= PrologueService.LIGHT_DELAY, "Everyone at the checkout: the light comes twenty seconds later: " + in);
                    helper.assertTrue(PrologueService.skipTo(server, "light", 0), "The light can be reached");
                }))
                // Freshly forced chunks expose their entities at different times: wait for the two entrance guards,
                // seven stair/hall guards, both Bennets and the crowd before checking the whole cast's placement.
                .thenWaitUntil(() -> helper.assertTrue(city.getEntities(ModEntities.STORY_ACTOR.get(), PrologueService::isScene).size() == 2
                        && city.getEntities(EntityType.VILLAGER, PrologueService::isScene).size() == TimeIntroService.CROWD_SIZE + 1
                        && cast(city).size() >= CAST,
                        "Noah and Claire wait in the safe house, the guards are at their posts, and Odessa's people are on the streets"))
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.phase().equals("light"), "The light: " + PrologueService.phase());
                    helper.assertTrue(!PrologueService.vaultOpen(city), "The vault stays shut through the fight and the wave");
                    helper.assertTrue(PrologueService.victims().size() == 1, "Two players: one person on the sidewalk the rubble will catch");
                    helper.assertTrue(PrologueService.agents().stream().allMatch(a -> !a.holstered().isEmpty() && a.getMainHandItem().isEmpty()),
                            "Until the fight starts, every agent's gun is in its holster");
                    standing(helper, city);
                    PrologueService.skipTo(server, "light", LastDayFight.ACTION + 1);
                }))
                .thenIdle(3)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.agents().stream().allMatch(a -> a.holstered().isEmpty() && !a.getMainHandItem().isEmpty()),
                            "The fight starts: they draw");
                    PrologueService.skipTo(server, "light", LastDayFight.pinnedAt(0) + 1);
                }))
                .thenIdle(3)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.victimStates().equals(List.of("pinned")), "The stone comes down on them: " + PrologueService.victimStates());
                    List<BlockPos> pins = PrologueService.pins(0);
                    helper.assertTrue(!pins.isEmpty(), "...and holds them down");
                    helper.assertTrue(!players[1].gameMode.destroyBlock(new BlockPos(4, CityGrid.STREET_Y, 70)), "Nothing else in the city breaks");
                    for (BlockPos pin : pins) helper.assertTrue(city.getBlockState(pin).is(Blocks.GRAVEL), "Gravel holds them down: " + pin + " " + city.getBlockState(pin));
                    // Too soon to lift them: they're told to dig first, and stay where they are.
                    players[0].setShiftKeyDown(true);
                    var buried = PrologueService.victims().get(0);
                    helper.assertTrue(PrologueService.lift(players[0], buried.position())
                            && !com.jayptucker.heroesevolved.entity.EntityCarryService.isCarrying(players[0]), "Nobody lifts someone still buried");
                    dig(helper, city, players[0]);
                }))
                // Gravel heaped above slides down into the hole as it's dug: dig again until it's all off them.
                .thenIdle(10)
                .thenExecute(() -> guard(cleanup, () -> dig(helper, city, players[0])))
                .thenIdle(10)
                .thenExecute(() -> guard(cleanup, () -> {
                    dig(helper, city, players[0]);
                    // Both by the market's beacon when the wave comes; a window near them, to see it burst and come back.
                    Vec3 beacon = com.jayptucker.heroesevolved.time.intro.city.Market.beacon();
                    for (int k = 0; k < 2; k++) players[k].teleportTo(city, beacon.x + k * 2, beacon.y + 1, beacon.z, 0, 0);
                    helper.assertTrue(glassNear(city, BlockPos.containing(beacon), 30) != null, "There are windows on the market's street");
                }))
                .thenIdle(3)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.victimStates().equals(List.of("free")), "Dug out: free, but they can't stand: " + PrologueService.victimStates());
                    PrologueService.skipTo(server, "light", PrologueTimeline.hits(players[0].getX(), players[0].getZ()) + 2);
                }))
                .thenIdle(3)
                .thenExecute(() -> guard(cleanup, () -> {
                    for (ServerPlayer p : players) {
                        helper.assertTrue(PrologueService.dazed(p) && p.getForcedPose() == net.minecraft.world.entity.Pose.SLEEPING,
                                "The wave throws " + p.getGameProfile().getName() + " flat on their back");
                    }
                    helper.assertTrue(PrologueService.windowsBroken() > 0, "The windows it passes burst: " + PrologueService.windowsBroken());
                    window[0] = PrologueService.firstWindow();
                    helper.assertTrue(window[0] != null && city.getBlockState(window[0]).isAir(), "...gone from its frame: " + window[0]);
                    helper.assertTrue(glassNear(city, BlockPos.containing(com.jayptucker.heroesevolved.time.intro.city.Market.beacon()), 30) != null,
                            "...but only about half of them: glass still stands in the frames round the market");
                    helper.assertTrue(PrologueService.shards() > 0, "Shards of it lie on the ground: " + PrologueService.shards());
                    helper.assertTrue(PrologueService.crumbled() > 0, "Roof edges and facades come loose round them: " + PrologueService.crumbled());
                    crumbled[0] = PrologueService.firstCrumbled();
                    helper.assertTrue(crumbled[0] != null && city.getBlockState(crumbled[0]).isAir(), "...and are gone from the building: " + crumbled[0]);
                    PrologueService.skipTo(server, "light", PrologueTimeline.hits(players[0].getX(), players[0].getZ()) + PrologueTimeline.DAZE + 6);
                }))
                .thenIdle(3)
                .thenExecute(() -> guard(cleanup, () -> {
                    for (ServerPlayer p : players)
                        helper.assertTrue(!PrologueService.dazed(p) && p.getForcedPose() == null, "They come to and get up: " + p.getGameProfile().getName());
                    helper.assertTrue(PrologueService.strewn() > 0, "What came loose lies in the street: " + PrologueService.strewn());
                    // The runner sneaks and right-clicks the ground beside whoever was under the rubble, from the road side.
                    var victim = PrologueService.victims().get(0);
                    Vec3 from = new Vec3(victim.getX() > 4 ? victim.getX() - 2 : victim.getX() + 2, CityGrid.STREET_Y + 1, victim.getZ());
                    players[0].teleportTo(city, from.x, from.y, from.z, victim.getX() > 4 ? -90 : 90, 40);
                    players[0].setShiftKeyDown(true);
                    helper.assertTrue(PrologueService.lift(players[0], victim.position().add(victim.getX() > 4 ? -1 : 1, 0, 0))
                            && com.jayptucker.heroesevolved.entity.EntityCarryService.isCarrying(players[0]), "Sneak and right-click beside them: lifted");
                    helper.assertTrue(victim.getPose() == net.minecraft.world.entity.Pose.STANDING
                                    && com.jayptucker.heroesevolved.entity.CarryGeometry.hold(victim) == com.jayptucker.heroesevolved.entity.CarryGeometry.Hold.ARMS,
                            "...in the two-arm carry, as a person is held: " + com.jayptucker.heroesevolved.entity.CarryGeometry.hold(victim));
                    players[0].setShiftKeyDown(false);
                }))
                .thenIdle(3)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.victimStates().equals(List.of("carried")), "Carried: " + PrologueService.victimStates());
                    PrologueService.skipTo(server, "light", PrologueTimeline.SHELTER + 1);
                }))
                .thenIdle(3)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.vaultOpen(city), "The sirens: the Company opens the vault at the foot of the stair");
                    walkable(helper, city);
                    // One runs down to the vault with them in their arms; the other dawdles in the street.
                    Vec3 landing = PrologueService.landing();
                    players[0].teleportTo(city, landing.x, landing.y, landing.z, 0, 0);
                    players[1].teleportTo(city, -28.5, CityGrid.STREET_Y, 68.5, 0, 0);
                }))
                .thenIdle(3)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.sheltered(players[0]), "Down the stair is safe");
                    helper.assertTrue(PrologueService.victimStates().equals(List.of("saved")), "...and so is whoever they carried: " + PrologueService.victimStates());
                    helper.assertTrue(!PrologueService.sheltered(players[1]), "The street isn't");
                    PrologueService.skipTo(server, "light", PrologueService.runTicks());
                }))
                .thenIdle(60)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.sheltered(players[1]), "A straggler is darted and carried down");
                    helper.assertTrue(CryoChamberService.isInside(players[1]), "...and sealed in a pod");
                    pods[1] = PrologueService.pod(players[1]);
                    helper.assertTrue(pods[1] >= 0 && pods[1] != PrologueService.CLAIRE, "Never in Claire's pod: " + pods[1]);
                    BlockPos o = PrologueService.LastDayData.origin();
                    Vec3 inside = BunkerLayout.Scene.GATHER.add(0, 0, 3);
                    players[0].teleportTo(city, o.getX() + inside.x, o.getY() + inside.y, o.getZ() + inside.z, 0, 0);
                }))
                .thenIdle(3)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.phase().equals("noah"), "Everyone gathered: Noah's scene: " + PrologueService.phase());
                    helper.assertTrue(!PrologueService.vaultOpen(city), "The vault shuts behind the last of them");
                    helper.assertTrue(PrologueService.shutOut() >= 3, "...too soon: people are left on the stair, pounding on it: " + PrologueService.shutOut());
                    var saved = PrologueService.victims().get(0);
                    carried[0] = saved;
                    BlockPos so = PrologueService.LastDayData.origin();
                    helper.assertTrue(!saved.isPassenger() && BunkerLayout.Scene.HALL.isInside(saved.blockPosition().subtract(so)),
                            "The one they carried is laid in the stasis hall: " + saved.blockPosition().toShortString());
                    PrologueService.skipTo(server, "noah", PrologueService.NOAH_SHOVE);
                }))
                .thenIdle(10)
                .thenExecute(() -> guard(cleanup, () -> {
                    BlockPos claire = PrologueService.LastDayData.origin().offset(BunkerLayout.STASIS.podOffset(PrologueService.CLAIRE));
                    helper.assertTrue(city.getBlockState(claire).is(ModBlocks.CRYOCHAMBER.get())
                            && !city.getBlockState(claire).getValue(com.jayptucker.heroesevolved.story.bunker.CryoChamberBlock.OPEN),
                            "Noah seals Claire in Pod 14");
                }))
                // Noah has to walk the hall, and under a full suite's load its chunks can still be waking up (an actor in a
                // chunk that isn't entity-ticking stands still while the scene's clock runs on).
                .thenWaitUntil(() -> helper.assertTrue(hallTicking(city), "Waiting for the stasis hall's chunks to tick"))
                .thenExecute(() -> guard(cleanup, () -> PrologueService.skipTo(server, "noah", PrologueService.NOAH_APPROACH)))
                // He walks up to the one left in the hall and stuns them himself (alone, no guards are called).
                .thenIdle(300)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.firedFrom() >= 0 && PrologueService.firedFrom() < 4.5,
                            "Noah walks right up to them before he fires: " + PrologueService.firedFrom());
                    helper.assertTrue(PrologueService.phase().equals("pods"), "After the stun gun, the pods: " + PrologueService.phase());
                    helper.assertTrue(CryoChamberService.isInside(players[0]), "Everyone is sealed in");
                    pods[0] = PrologueService.pod(players[0]);
                    helper.assertTrue(pods[0] >= 0 && pods[0] != pods[1] && pods[0] != PrologueService.CLAIRE, "Each has their own pod");
                    // Every hall pod nobody has a claim on holds one of Odessa's people, sealed; the one they carried is one of them.
                    BunkerData claims = BunkerData.get(server);
                    BlockPos po = PrologueService.LastDayData.origin();
                    int free = 0;
                    for (int i = 0; i < BunkerLayout.HALL_PODS; i++) {
                        if (!PodSleepers.hallPod(i) || claims.pods.containsValue(i)) continue;
                        free++;
                        BlockPos base = po.offset(BunkerLayout.STASIS.podOffset(i));
                        helper.assertTrue(PodSleepers.occupied(city, base)
                                        && !city.getBlockState(base).getValue(com.jayptucker.heroesevolved.story.bunker.CryoChamberBlock.OPEN),
                                "Pod " + (i + 1) + ", nobody else's, holds one of Odessa's people, sealed in");
                    }
                    helper.assertTrue(free > 0, "Some hall pods were nobody's");
                    helper.assertTrue(carried[0] != null && carried[0].isAlive() && carried[0].getTags().contains(PodSleepers.TAG),
                            "The one they carried down is sealed in a pod too");
                    // Two lived the day together: if the Helix Ward was still waiting, one of them was drawn for it, sealed in its
                    // private pod and dosed with a dormant power; the other is in the hall.
                    java.util.UUID ward = PrologueService.ward();
                    if (ward != null) {
                        int sealed = 0;
                        for (int i = 0; i < 2; i++) {
                            boolean drawn = players[i].getUUID().equals(ward);
                            helper.assertTrue((pods[i] == BunkerLayout.LAB_POD) == drawn, "Only the one drawn sleeps in the ward: " + pods[i]);
                            if (drawn) {
                                sealed++;
                                var power = com.jayptucker.heroesevolved.ability.service.PlayerAbilityService.getData(players[i]).assignedPower();
                                helper.assertTrue(power.isPresent() && !power.get().getValue().isUnlocked(), "The ward's sleeper carries a dormant power: " + power);
                                helper.assertTrue(BunkerData.get(server).lab.equals(ward) && !BunkerData.get(server).labOpen(), "The ward is theirs for good");
                            }
                        }
                        helper.assertTrue(sealed == 1, "Exactly one of the two is in the ward");
                    }
                }))
                // The hall's chunks were forced when the day began, but under a full suite's load they can still be waking
                // up here, and rubble dropped into a chunk that isn't entity-ticking hangs where nobody can see it.
                .thenWaitUntil(() -> helper.assertTrue(hallTicking(city), "Waiting for the stasis hall's chunks to tick"))
                .thenExecute(() -> guard(cleanup, () -> PrologueService.skipTo(server, "pods", 165)))
                .thenIdle(45)
                .thenExecute(() -> guard(cleanup, () -> {
                    // The blow has reached the hall: stone down the aisle, and lights out.
                    BlockPos o = PrologueService.LastDayData.origin();
                    int rubble = 0, dark = 0, lit = 0;
                    var hall = BunkerLayout.Scene.HALL;
                    for (BlockPos pos : BlockPos.betweenClosed(o.offset(hall.minX(), hall.minY(), hall.minZ()), o.offset(hall.maxX(), hall.maxY(), hall.maxZ()))) {
                        var state = city.getBlockState(pos);
                        if (state.is(Blocks.COBBLED_DEEPSLATE) || state.is(Blocks.GRAVEL) || state.is(Blocks.DEEPSLATE_TILES)) rubble++;
                        // A light the blast put out stands as the bunker's power-out state does (BunkerPower.off).
                        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                                && !state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT) || state.is(Blocks.SMOOTH_QUARTZ)) dark++;
                        else if (com.jayptucker.heroesevolved.story.bunker.BunkerPower.off(state) != null) lit++;
                    }
                    rubble += city.getEntities(EntityType.FALLING_BLOCK, PrologueService::isScene).size();
                    helper.assertTrue(rubble >= 3, "Rubble comes down from the ceiling when the blast hits: " + rubble);
                    helper.assertTrue(dark >= 1, "Some of the hall's lights are out: " + dark + " dark, " + lit + " lit");
                    PrologueService.skipTo(server, "pods", PrologueService.PODS_WAKE - 10);
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
                    helper.assertTrue(!city.getBlockState(window[0]).isAir(), "Every burst window is put back: " + window[0].toShortString());
                    helper.assertTrue(!city.getBlockState(crumbled[0]).isAir(), "Every piece of the buildings is put back: " + crumbled[0].toShortString());
                    helper.assertTrue(city.getBlockState(marketDoor[0]).getValue(DoorBlock.OPEN) == marketOpen[0],
                            "The market's door is as the city had it: " + marketDoor[0].toShortString());
                    helper.assertTrue(city.getForcedChunks().isEmpty(), "The city's chunks are let go");
                }))
                .thenExecute(cleanup)
                .thenSucceed();
    }

    /**
     * The admin menu's "To cryopod": pressed in the apartments, the day jumps to the pods (the vault shut, Claire
     * sealed in Pod 14, the player in their own pod), then plays on to the waking in the bunker.
     */
    @GameTest(template = "rewind_empty", batch = "PrologueGameTests.skipToPod", timeoutTicks = 2400)
    public static void skipToCryopodSealsEveryoneAndPlaysOn(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel city = server.getLevel(TimeIntroService.DIMENSION);
        helper.assertTrue(city != null, "The city dimension loads");
        ServerPlayer[] players = new ServerPlayer[1];
        Runnable cleanup = () -> { for (ServerPlayer p : players) if (p != null) remove(p); };
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(FractureCity.get(city).ready() && !TimeIntroService.sceneRunning()
                        && !AvenueService.running() && !PrologueService.running(), "Waiting for the city"))
                .thenExecute(() -> {
                    helper.assertTrue(PrologueService.toPods(server) == PrologueService.PodJump.NONE, "Nothing to skip with no day playing");
                    players[0] = player(helper, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3))), "lastday-skipper");
                    PrologueService.join(players[0]);
                })
                .thenIdle(5)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.phase().equals("apartment"), "The day begins in the apartments");
                    helper.assertTrue(PrologueService.podsAhead(), "The menu offers the skip");
                    helper.assertTrue(PrologueService.toPods(server) == PrologueService.PodJump.JUMPED, "The skip jumps the day");
                }))
                .thenIdle(5)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.phase().equals("pods"), "Straight to the pods: " + PrologueService.phase());
                    helper.assertTrue(!PrologueService.podsAhead(), "Nothing left to skip");
                    helper.assertTrue(CryoChamberService.isInside(players[0]), "Sealed in a cryopod");
                    helper.assertTrue(PrologueService.pod(players[0]) >= 0 && PrologueService.pod(players[0]) != PrologueService.CLAIRE,
                            "In their own pod: " + PrologueService.pod(players[0]));
                    helper.assertTrue(!PrologueService.vaultOpen(city), "The vault is shut behind them");
                    BlockPos claire = PrologueService.LastDayData.origin().offset(BunkerLayout.STASIS.podOffset(PrologueService.CLAIRE));
                    helper.assertTrue(city.getBlockState(claire).is(ModBlocks.CRYOCHAMBER.get())
                            && !city.getBlockState(claire).getValue(com.jayptucker.heroesevolved.story.bunker.CryoChamberBlock.OPEN),
                            "Claire is sealed in Pod 14 too");
                    helper.assertTrue(PrologueService.toPods(server) == PrologueService.PodJump.ALREADY, "A second press does nothing");
                    PrologueService.skipTo(server, "pods", PrologueService.PODS_WAKE - 10);
                }))
                .thenIdle(20)
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(!PrologueService.running(), "The day is over");
                    helper.assertTrue(players[0].level() == server.overworld() && BunkerService.isAwakening(players[0]),
                            "103 years later, the usual waking in the bunker");
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
                    for (int i = 0; i < players.size(); i++)
                        players.get(i).teleportTo(city, Apartments.X0 + 8.5 + (i % 5) * 1.5, Apartments.Y + 1, Apartments.Z0 + 2.5 + (i / 5) * 2, 0, 0);
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

    /**
     * Odessa's people take shelter below: someone handed over at the head of Primatech's stair goes on down it, through
     * the open vault and the corridor, into the stasis hall, and waits there; they never vanish from the street.
     */
    @GameTest(template = "rewind_empty", batch = "PrologueGameTests.shelter", timeoutTicks = 2000)
    public static void odessasPeopleWalkDownIntoTheHall(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel city = server.getLevel(TimeIntroService.DIMENSION);
        ServerPlayer[] player = new ServerPlayer[1];
        net.minecraft.world.entity.npc.Villager[] runner = new net.minecraft.world.entity.npc.Villager[1];
        Runnable cleanup = () -> { if (player[0] != null) remove(player[0]); };
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(FractureCity.get(city).ready() && !TimeIntroService.sceneRunning()
                        && !AvenueService.running() && !PrologueService.running(), "Waiting for the city"))
                .thenExecute(() -> {
                    player[0] = player(helper, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3))), "lastday-watcher");
                    PrologueService.join(player[0]);
                })
                .thenIdle(5)
                .thenExecute(() -> guard(cleanup, () -> {
                    PrologueService.skipTo(server, "light", PrologueTimeline.SHELTER + 1);
                    // Out front of Primatech, out of everyone's way, watching the door.
                    player[0].teleportTo(city, PrologueService.ENTRANCE.x + 6, CityGrid.STREET_Y + 1, PrologueService.ENTRANCE.z - 4, 0, 0);
                }))
                .thenIdle(3)
                .thenWaitUntil(() -> helper.assertTrue(hallTicking(city), "Waiting for the stasis hall's chunks to tick"))
                .thenExecute(() -> guard(cleanup, () -> {
                    helper.assertTrue(PrologueService.vaultOpen(city), "The vault is open");
                    runner[0] = PrologueService.shelterSomeone(city, new Vec3(CityGrid.STAIR_X0 + .5, CityGrid.STREET_Y, CityGrid.STAIR_Z0 + 1.5));
                    helper.assertTrue(runner[0] != null, "Someone reaches the head of the stair");
                }))
                .thenWaitUntil(() -> {
                    helper.assertTrue(runner[0].isAlive(), "They never vanish");
                    helper.assertTrue(PrologueService.gathered() >= 1, "Waiting for them to reach the hall: at " + runner[0].blockPosition().toShortString());
                })
                .thenExecute(() -> guard(cleanup, () -> {
                    BlockPos o = PrologueService.LastDayData.origin();
                    helper.assertTrue(PastBunker.bounds(o).isInside(runner[0].blockPosition()) && runner[0].getZ() > o.getZ() + 20,
                            "Down in the safe house, past the commons: " + runner[0].blockPosition().toShortString());
                }))
                .thenExecute(cleanup)
                .thenWaitUntil(() -> helper.assertTrue(!PrologueService.running(), "With everyone gone, the day ends"))
                .thenExecute(() -> helper.assertTrue(!runner[0].isAlive(), "...and its people go with it"))
                .thenSucceed();
    }

    /** The nine guards, Noah and Claire. */
    private static final int CAST = 11;

    private static List<net.minecraft.world.entity.Entity> cast(ServerLevel city) {
        List<net.minecraft.world.entity.Entity> cast = new java.util.ArrayList<>();
        cast.addAll(city.getEntities(ModEntities.COMPANY_AGENT.get(), PrologueService::isScene));
        cast.addAll(city.getEntities(ModEntities.STORY_ACTOR.get(), PrologueService::isScene));
        return cast;
    }

    /** Every chunk of the stasis hall (where the blast's rubble falls) is entity-ticking. */
    private static boolean hallTicking(ServerLevel city) {
        BlockPos o = PrologueService.LastDayData.origin();
        var hall = BunkerLayout.Scene.HALL;
        for (int x = (o.getX() + hall.minX()) >> 4; x <= (o.getX() + hall.maxX()) >> 4; x++)
            for (int z = (o.getZ() + hall.minZ()) >> 4; z <= (o.getZ() + hall.maxZ()) >> 4; z++)
                if (!city.isPositionEntityTicking(new BlockPos(x << 4, o.getY(), z << 4))) return false;
        return true;
    }

    /** Every guard and every Bennet stands on a floor in open air: none in a wall, a desk, a pod or the vault's swing. */
    private static void standing(GameTestHelper helper, ServerLevel city) {
        List<net.minecraft.world.entity.Entity> cast = cast(city);
        helper.assertTrue(cast.size() >= CAST, "The guards, Noah and Claire are all placed: " + cast.size());
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
     * Peter and Sylar's fights: the vision's over Kirby Plaza (time/intro/SkyFight) and the Last Day's down the avenue
     * (LastDayFight), every tick in open air and nobody jumping; the vision's throw finding the west tower's face where the
     * script draws it; and the Last Day's four facades real walls, with room on the sidewalks under them for the people
     * the rubble catches.
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
        Vec3[] last = null;
        for (int t = 0; t <= vision.landAt(); t++) {
            Vec3[] at = com.jayptucker.heroesevolved.time.intro.SkyFight.at(vision, t, impact);
            for (int who = 0; who < 2; who++) {
                if (at[who] == null) continue;
                boolean flinging = who == 1 && t > vision.impactAt() - 4;
                boolean landing = who == 0 && t >= vision.landAt() - 1;
                if (!flinging && !landing) openAir(helper, city, at[who], (who == 0 ? "Peter" : "Sylar") + " (vision), tick " + t);
                if (last != null && last[who] != null)
                    helper.assertTrue(at[who].distanceTo(last[who]) < 4.5, (who == 0 ? "Peter" : "Sylar") + " never jumps, tick " + t);
            }
            last = at;
        }
        // The Last Day's fight comes down the avenue to the market: the city round it must be the one it was drawn for.
        FractureCity.rebuild(city, -24, 0, 24, 70);
        LastDayFight.Facades found = LastDayFight.find(city);
        for (int i = 0; i < 4; i++) {
            Vec3 face = found.get(i);
            double dx = i == 0 || i == 3 ? -0.6 : 0.6;
            helper.assertTrue(!city.getBlockState(BlockPos.containing(face.x + dx, face.y, face.z)).isAir(),
                    "Facade " + i + " is a real wall: " + face);
        }
        Vec3 lastPeter = null, lastSylar = null;
        for (int t = 0; t <= LastDayFight.LAND; t++) {
            Vec3 p = LastDayFight.peter(t, found), s = LastDayFight.sylar(t, found);
            if (p != null && t < LastDayFight.LAND - 1) openAir(helper, city, p, "Peter (Last Day), tick " + t);
            if (s != null && t < LastDayFight.TOWER - 4) openAir(helper, city, s, "Sylar (Last Day), tick " + t);
            if (p != null && lastPeter != null) helper.assertTrue(p.distanceTo(lastPeter) < 8, "Peter never jumps, tick " + t);
            if (s != null && lastSylar != null) helper.assertTrue(s.distanceTo(lastSylar) < 8, "Sylar never jumps, tick " + t);
            lastPeter = p;
            lastSylar = s;
            for (int which = 0; which < 2; which++) {
                Vec3 c = LastDayFight.chunk(which, t, found);
                int hit = which == 0 ? LastDayFight.RUBBLE_EAST : LastDayFight.RUBBLE_WEST;
                if (c != null && t > (which == 0 ? LastDayFight.FLING_1 : LastDayFight.FLING_2) + 1 && t < hit - 1)
                    openAir(helper, city, c, "Chunk " + which + " flies in open air, tick " + t);
            }
        }
        // Room on the sidewalks for everyone the rubble can catch (six, for a group of twelve).
        for (Vec3 spot : LastDayFight.victims(found, 6)) {
            Vec3 at = LastDayRescue.standable(city, spot);
            BlockPos feet = BlockPos.containing(at);
            helper.assertTrue(city.getBlockState(feet).getCollisionShape(city, feet).isEmpty()
                    && !city.getBlockState(feet.below()).getCollisionShape(city, feet.below()).isEmpty(), "Someone can stand at " + at);
        }
        helper.succeed();
    }

    /** Digs whatever gravel is still on the first victim, by hand. */
    private static void dig(GameTestHelper helper, ServerLevel city, ServerPlayer digger) {
        for (BlockPos pin : PrologueService.pins(0))
            if (!city.getBlockState(pin).isAir()) helper.assertTrue(digger.gameMode.destroyBlock(pin), "The gravel on them breaks: " + pin);
    }

    private static void openAir(GameTestHelper helper, ServerLevel city, Vec3 at, String what) {
        BlockPos feet = BlockPos.containing(at), head = feet.above();
        helper.assertTrue(city.getBlockState(feet).getCollisionShape(city, feet).isEmpty()
                && city.getBlockState(head).getCollisionShape(city, head).isEmpty(), what + " in open air: " + feet.toShortString());
    }

    /** A pane of glass within {@code r} blocks of {@code at} at street level, in a building's face. */
    private static BlockPos glassNear(ServerLevel city, BlockPos at, int r) {
        for (int d = 2; d <= r; d++)
            for (int dx = -d; dx <= d; dx++)
                for (int dz = -d; dz <= d; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != d) continue;
                    for (int y = CityGrid.STREET_Y + 1; y <= CityGrid.STREET_Y + 10; y++) {
                        BlockPos pos = new BlockPos(at.getX() + dx, y, at.getZ() + dz);
                        var state = city.getBlockState(pos);
                        if (state.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS) || state.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_PANES)) return pos;
                    }
                }
        return null;
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
