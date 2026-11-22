package com.jayptucker.heroesevolved.quake;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.events.QuakeEvents;
import com.jayptucker.heroesevolved.events.TemporalSnapshotEvents;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.time.TemporalSnapshotService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** What holds a world must let go of it when it closes, or the next singleplayer world waits on the old one forever. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class WorldCloseGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "world-close"));
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.QUAKE_ID).unlock(ModAbilities.QUAKE_ID));
        PlayerProgressionService.setLevel(p, 5);
        return p;
    }

    @GameTest(template = "rewind_empty", batch = "world_close.quake")
    public static void closingTheWorldStopsEveryQuakeWave(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        var p = player(h);
        QuakeService.impact(p, 3, 1, false);
        h.assertTrue(QuakeService.waves() > 0, "The wave is travelling");
        QuakeEvents.onServerStopping(new ServerStoppingEvent(h.getLevel().getServer()));
        h.assertTrue(QuakeService.waves() == 0, "No wave outlives the world");
        h.succeed();
    }

    /** The test server has no Past dimension, so a real build can't start: an entry stands in for one. */
    @GameTest(template = "rewind_empty", batch = "world_close.snapshot")
    @SuppressWarnings("unchecked")
    public static void closingTheWorldStopsASnapshotBuild(GameTestHelper h) throws ReflectiveOperationException {
        var field = TemporalSnapshotService.class.getDeclaredField("ACTIVE_BUILDS");
        field.setAccessible(true);
        var builds = (java.util.Map<UUID, Object>) field.get(null);
        UUID id = UUID.randomUUID();
        builds.put(id, null);
        try {
            TemporalSnapshotEvents.onServerStopping(new ServerStoppingEvent(h.getLevel().getServer()));
            h.assertTrue(!builds.containsKey(id), "The build is gone with the world");
            h.succeed();
        } finally { builds.remove(id); }
    }
}
