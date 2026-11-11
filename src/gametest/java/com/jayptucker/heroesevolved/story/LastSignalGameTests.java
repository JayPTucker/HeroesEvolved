package com.jayptucker.heroesevolved.story;

import com.jayptucker.heroesevolved.story.bunker.BunkerBuilder;
import com.jayptucker.heroesevolved.story.bunker.BunkerService;
import com.jayptucker.heroesevolved.structure.Blueprint;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class LastSignalGameTests {
    @GameTest(template = "rewind_empty", batch = "story.lastsignal")
    public static void aFoundStationWorksWithoutPowersOrTravel(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var server = level.getServer();
        SignalSite site = placeStation(level);
        ServerPlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "signal-test"));

        var bunker = BunkerService.ensureBuilt(server);
        BlockPos receiver = BunkerBuilder.receiverPos(bunker.origin);
        player.setPos(Vec3.atBottomCenterOf(receiver));
        h.assertTrue(!LastSignalService.atSite(player), "The bunker and relay station are distinct places");
        // The flat test world disables naturally generated structures, so a receiver may have no bearing.
        Vec3 atReceiver = player.position();
        boolean powered = bunker.powered;
        try {
            bunker.powered = true;
            use(player, receiver);
        } finally {
            bunker.powered = powered;
        }
        h.assertTrue(player.level() == level && player.position().equals(atReceiver), "The receiver never moves the player");

        BlockPos arrival = site.at("arrival");
        player.setPos(Vec3.atBottomCenterOf(arrival));
        h.assertTrue(level.isEmptyBlock(arrival) && level.isEmptyBlock(arrival.above())
                        && level.getBlockState(arrival.below()).isFaceSturdy(level, arrival.below(), Direction.UP),
                "Arrival has solid ground and room to move");
        h.assertTrue(LastSignalService.phase(player) == LastSignalService.Phase.SEARCHING,
                "Players can discover a rotated station on foot before any other story step");

        use(player, site.at("coolant"));
        h.assertTrue(LastSignalService.phase(player) == LastSignalService.Phase.SEARCHING, "Use the relay first");
        use(player, site.at("relay"));
        h.assertTrue(LastSignalService.phase(player) == LastSignalService.Phase.ISOLATE
                && site.all("gate").stream().allMatch(level::isEmptyBlock), "The relay opens the annex");
        use(player, site.at("isolator"));
        h.assertTrue(LastSignalService.phase(player) == LastSignalService.Phase.COOL, "The isolator prepares the coolant");
        use(player, site.at("coolant"));
        h.assertTrue(LastSignalService.phase(player) == LastSignalService.Phase.RESCUE
                && site.all("rear_gate").stream().allMatch(level::isEmptyBlock), "Cooling opens the extraction route");
        use(player, site.at("exit"));
        h.assertTrue(!LastSignalService.completed(server), "Extraction waits for both sleepers");
        h.succeed();
    }

    private static SignalSite placeStation(ServerLevel level) {
        BlockPos origin = new BlockPos(240, 80, 240);
        Rotation rotation = Rotation.CLOCKWISE_90;
        var registry = level.registryAccess();
        var structure = registry.registryOrThrow(Registries.STRUCTURE).get(SignalSite.ID);
        var pool = registry.registryOrThrow(Registries.TEMPLATE_POOL).get(SignalSite.ID);
        var element = pool.getRandomTemplate(level.random);
        var manager = level.getStructureManager();
        var bounds = element.getBoundingBox(manager, origin, rotation);
        var piece = new PoolElementStructurePiece(manager, element, origin, element.getGroundLevelDelta(),
                rotation, bounds, LiquidSettings.IGNORE_WATERLOGGING);
        ChunkPos startChunk = new ChunkPos(origin);
        var start = new StructureStart(structure, startChunk, 0, new PiecesContainer(List.of(piece)));
        level.getChunk(startChunk.x, startChunk.z).setStartForStructure(structure, start);
        for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++) {
            for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) {
                level.getChunk(x, z).addReferenceForStructure(structure, startChunk.toLong());
            }
        }
        Blueprint.load("signal_site").place(level, origin, rotation);
        return new SignalSite(level, piece);
    }

    public static boolean swapCompleted(net.minecraft.server.MinecraftServer server, boolean completed) {
        var data = LastSignalData.get(server);
        boolean previous = data.rescued;
        data.rescued = completed;
        return previous;
    }

    private static void use(ServerPlayer player, BlockPos at) {
        player.setPos(Vec3.atBottomCenterOf(at));
        var hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.SOUTH, at, false);
        LastSignalService.console(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, at, hit));
    }
}
