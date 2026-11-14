package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-wide slowed and stopped time, and the frozen stand-in everyone else sees. Each test is its own batch. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class TimeVeilGameTests {
    private static ServerPlayer player(GameTestHelper h, Vec3 at, ResourceLocationHolder power) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "veil-test"));
        p.setPos(at);
        if (power != null) p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power.id).unlock(power.id));
        PlayerProgressionService.setLevel(p, 5);
        return p;
    }

    private record ResourceLocationHolder(net.minecraft.resources.ResourceLocation id) {}
    private static final ResourceLocationHolder TIME = new ResourceLocationHolder(ModAbilities.TIME_MANIPULATION_ID),
            SPEED = new ResourceLocationHolder(ModAbilities.SUPERSPEED_ID);

    @GameTest(template = "rewind_empty", batch = "time.veil_server_wide")
    public static void stoppedTimeReachesTheWholeServer(GameTestHelper h) {
        Vec3 origin = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3)));
        ServerPlayer traveller = player(h, origin, TIME);
        Pig far = EntityType.PIG.create(h.getLevel());
        far.setPos(origin.add(0, 0, 400));
        TimeFieldTestSupport.serverWide(true);
        try {
            TimeStopService.start(traveller, 5);
            h.assertTrue(TimeStopService.shouldStopEntityTick(far), "Server-wide, a creature 400 blocks away is stopped too");
            h.assertTrue(!TimeStopService.shouldStopEntityTick(traveller), "but never the traveller");
            TimeStopService.stop(traveller);
            TimeFieldTestSupport.serverWide(false);
            TimeStopService.start(traveller, 5);
            h.assertTrue(!TimeStopService.shouldStopEntityTick(far), "Kept to an area, it is not");
            h.succeed();
        } finally { TimeStopService.stop(traveller); TimeFieldTestSupport.serverWide(false); TimeVeil.forget(traveller); }
    }

    @GameTest(template = "rewind_empty", batch = "time.veil_stand_in")
    public static void othersSeeAStandInUnlessTheyMoveInTime(GameTestHelper h) {
        Vec3 origin = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3)));
        ServerPlayer traveller = player(h, origin, TIME), bystander = player(h, origin.add(2, 0, 0), null),
                speedster = player(h, origin.add(-2, 0, 0), SPEED);
        try {
            TimeVeil.begin(traveller, TimeVeil.Style.TIME, TimeVeil.STOP);
            h.assertTrue(TimeVeil.veiled(traveller), "Stopping time hides the traveller behind a stand-in");
            h.assertTrue(!TimeVeil.puzzled(traveller).contains(speedster.getUUID()),
                    "A speedster is only told someone is bending time, not puzzled by it");
            h.assertTrue(!TimeVeil.seesThrough(bystander, traveller.getUUID()) && TimeVeil.seesThrough(speedster, traveller.getUUID())
                    && TimeVeil.seesThrough(traveller, traveller.getUUID()), "Only those who move in time see them as they are");
            TimeVeil.begin(traveller, TimeVeil.Style.TIME, TimeVeil.SLOW);
            TimeVeil.end(traveller, TimeVeil.STOP);
            h.assertTrue(TimeVeil.veiled(traveller), "A second reason keeps the stand-in up");
            TimeVeil.end(traveller, TimeVeil.SLOW);
            h.assertTrue(!TimeVeil.veiled(traveller), "When every reason ends, they are seen again");
            h.succeed();
        } finally { TimeVeil.forget(traveller); }
    }

    @GameTest(template = "rewind_empty", batch = "time.veil_hints")
    public static void bystandersArePuzzledAndToldWhenItIsOver(GameTestHelper h) {
        Vec3 origin = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3)));
        ServerPlayer traveller = player(h, origin, TIME), bystander = player(h, origin.add(2, 0, 0), null),
                speedster = player(h, origin.add(-2, 0, 0), SPEED);
        var list = h.getLevel().getServer().getPlayerList();
        List<ServerPlayer> joined = List.of(traveller, bystander, speedster);
        joined.forEach(p -> { players(list).add(p); byId(list).put(p.getUUID(), p); });
        try {
            TimeVeil.begin(traveller, TimeVeil.Style.TIME, TimeVeil.SLOW);
            var puzzled = TimeVeil.puzzled(traveller);
            h.assertTrue(puzzled.contains(bystander.getUUID()), "Someone without the power gets a puzzled hint");
            h.assertTrue(!puzzled.contains(speedster.getUUID()) && !puzzled.contains(traveller.getUUID()),
                    "Speedsters and the traveller don't");
            TimeVeil.end(traveller, TimeVeil.SLOW);
            h.assertTrue(TimeVeil.puzzled(traveller).isEmpty(), "Once it is over, everyone puzzled has been told");
            h.succeed();
        } finally {
            TimeVeil.forget(traveller);
            joined.forEach(p -> { players(list).remove(p); byId(list).remove(p.getUUID()); });
        }
    }

    @SuppressWarnings("unchecked") private static List<ServerPlayer> players(PlayerList list) { return (List<ServerPlayer>) field(list, "players"); }
    @SuppressWarnings("unchecked") private static Map<UUID, ServerPlayer> byId(PlayerList list) { return (Map<UUID, ServerPlayer>) field(list, "playersByUUID"); }
    private static Object field(PlayerList list, String name) {
        try { var f = PlayerList.class.getDeclaredField(name); f.setAccessible(true); return f.get(list); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}
