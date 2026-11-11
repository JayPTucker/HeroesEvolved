package com.jayptucker.heroesevolved.quake;

import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * Held Quake powers grind blocks down: Quake Blast cracks what it is held on until it breaks, letting go
 * breaks nothing and the cracks fade, and unbreakable or protected blocks never crack.
 *
 * <p>Each test drives the beam tick by tick itself. FakePlayers are not in the server's player list, so
 * the server's own Quake tick would drop their beam between ticks.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class QuakeBlockBreakGameTests {
    private static ServerPlayer daisy(GameTestHelper h, BlockPos feet) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "quake-break"));
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(feet)));
        // Facing south (+z), braced on the ground, so the beam pours straight ahead into the wall.
        p.setYRot(0); p.setXRot(0); p.setOnGround(true);
        p.setData(ModDataAttachments.PLAYER_ABILITIES,
                PlayerAbilityData.empty().assignDormant(ModAbilities.QUAKE_ID).unlock(ModAbilities.QUAKE_ID));
        PlayerProgressionService.setLevel(p, 5);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        return p;
    }

    /** A wall two blocks thick across x 3 to 7, y 1 to 4, at z 4 and 5: the beam meets it at (5, 2, 4). */
    private static void wall(GameTestHelper h, int x0, int x1, Block block) {
        for (int x = x0; x <= x1; x++) for (int y = 1; y <= 4; y++) for (int z = 4; z <= 5; z++)
            h.getLevel().setBlock(h.absolutePos(new BlockPos(x, y, z)), block.defaultBlockState(), 3);
    }

    private static AbilityAction quakeBlast() {
        return ModAbilities.QUAKE.get().actions().get(AbilitySlot.QUATERNARY);
    }

    private static AbilityUseContext use(ServerPlayer p) {
        return new AbilityUseContext(p, ModAbilities.QUAKE_ID, 5, false);
    }

    @SuppressWarnings("unchecked")
    private static Object field(String name) {
        try {
            var field = QuakeService.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static Object call(String name, Object... arguments) {
        try {
            Method method = Arrays.stream(QuakeService.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals(name) && m.getParameterCount() == arguments.length)
                    .findFirst().orElseThrow();
            method.setAccessible(true);
            return method.invoke(null, arguments);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    /** One server tick of a held Quake Blast: the beam works its blocks, then the cracks update. */
    @SuppressWarnings("unchecked")
    private static void hold(ServerPlayer p) {
        Object blast = ((Map<UUID, ?>) field("BLASTS")).get(p.getUUID());
        if (blast == null) throw new AssertionError("The beam stopped on its own");
        call("tickQuakeBlast", p, blast, p.serverLevel().getGameTime());
        QuakeBlockPressure.tick();
    }

    private static boolean is(GameTestHelper h, BlockPos relative, Block block) {
        return h.getLevel().getBlockState(h.absolutePos(relative)).is(block);
    }

    private static void clearDrops(GameTestHelper h) {
        h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(16))
                .forEach(ItemEntity::discard);
    }

    /**
     * Empties the space above the template's floor. Batches reuse grid slots without clearing blocks above
     * the template, so an earlier test's floor or walls can still stand here; these tests also leave none.
     */
    private static void clearSpace(GameTestHelper h) {
        for (int x = 0; x < 12; x++) for (int y = 1; y < 6; y++) for (int z = 0; z < 12; z++)
            h.getLevel().setBlock(h.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState(), 3);
    }

    @GameTest(template = "rewind_empty", batch = "quake.break_hold")
    public static void holdingQuakeBlastCracksAWallThenBreaksIt(GameTestHelper h) {
        clearSpace(h);
        wall(h, 3, 7, Blocks.STONE);
        ServerPlayer p = daisy(h, new BlockPos(5, 1, 1));
        BlockPos centre = new BlockPos(5, 2, 4), above = new BlockPos(5, 3, 4), behind = new BlockPos(5, 2, 5);
        BlockPos centreAt = h.absolutePos(centre), aboveAt = h.absolutePos(above), behindAt = h.absolutePos(behind);
        try {
            h.assertTrue(quakeBlast().canUse(use(p)), "Quake Blast is ready");
            quakeBlast().activate(use(p));
            for (int tick = 0; tick < 20; tick++) hold(p);
            int stage = QuakeBlockPressure.stage(p, centreAt);
            h.assertTrue(stage >= 1 && stage < 9, "A second in, the stone shows cracks: stage " + stage);
            h.assertTrue(is(h, centre, Blocks.STONE), "But it still stands");
            float aboveProgress = QuakeBlockPressure.progress(p, aboveAt);
            h.assertTrue(aboveProgress > 0 && aboveProgress < QuakeBlockPressure.progress(p, centreAt),
                    "The stone around the impact cracks too, more slowly: " + aboveProgress);

            int ticks = 20;
            while (is(h, centre, Blocks.STONE) && ticks < 200) { hold(p); ticks++; }
            h.assertTrue(!is(h, centre, Blocks.STONE), "Held long enough, the stone breaks");
            h.assertTrue(ticks >= 60, "Slowly: stone takes about four seconds, not " + ticks + " ticks");
            h.assertTrue(QuakeBlockPressure.stage(p, centreAt) == -1, "Its crack overlay is gone with it");
            boolean dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(centreAt).inflate(2))
                    .stream().anyMatch(item -> item.getItem().is(Items.COBBLESTONE));
            h.assertTrue(dropped, "And it drops, like mined stone");
            h.assertTrue(is(h, above, Blocks.STONE) && QuakeBlockPressure.progress(p, aboveAt) > 0.2F,
                    "The stone above is badly cracked but holds for now");

            float behindBefore = Math.max(0, QuakeBlockPressure.progress(p, behindAt));
            for (int tick = 0; tick < 10; tick++) hold(p);
            float bored = QuakeBlockPressure.progress(p, behindAt) - behindBefore;
            h.assertTrue(bored > 0.1F, "Through the hole, the beam bores into the next block at full strength: " + bored);
            h.succeed();
        } finally { QuakeService.stopAll(p); clearDrops(h); clearSpace(h); }
    }

    @GameTest(template = "rewind_empty", batch = "quake.break_release")
    public static void lettingGoEarlyBreaksNothingAndTheCracksFade(GameTestHelper h) {
        clearSpace(h);
        wall(h, 3, 7, Blocks.STONE);
        ServerPlayer p = daisy(h, new BlockPos(5, 1, 1));
        BlockPos centre = new BlockPos(5, 2, 4), centreAt = h.absolutePos(centre);
        try {
            quakeBlast().activate(use(p));
            for (int tick = 0; tick < 40; tick++) hold(p);
            h.assertTrue(QuakeBlockPressure.stage(p, centreAt) >= 3, "Half-way through, the stone is cracked");
            // The key is let go: the same release the input layer sends.
            quakeBlast().release(use(p));
            h.assertTrue(!QuakeService.isBlasting(p), "Letting go ends the beam");

            int stage = QuakeBlockPressure.stage(p, centreAt), fading = 0;
            boolean steppedDown = false;
            while (QuakeBlockPressure.cracking(p) > 0 && fading < 200) {
                QuakeBlockPressure.tick();
                fading++;
                int now = QuakeBlockPressure.stage(p, centreAt);
                if (now >= 0 && now < stage) steppedDown = true;
            }
            h.assertTrue(QuakeBlockPressure.cracking(p) == 0 && QuakeBlockPressure.stage(p, centreAt) == -1,
                    "The cracks fade away");
            h.assertTrue(steppedDown && fading <= 60, "Gradually, within three seconds: " + fading + " ticks");
            for (int x = 3; x <= 7; x++) for (int y = 1; y <= 4; y++) for (int z = 4; z <= 5; z++)
                h.assertTrue(is(h, new BlockPos(x, y, z), Blocks.STONE), "Nothing broke at " + x + " " + y + " " + z);
            h.succeed();
        } finally { QuakeService.stopAll(p); clearSpace(h); }
    }

    @GameTest(template = "rewind_empty", batch = "quake.break_unbreakable")
    public static void unbreakableAndProtectedBlocksNeverCrack(GameTestHelper h) {
        clearSpace(h);
        // Bedrock, with a barrier and reinforced deepslate beside the impact.
        wall(h, 1, 3, Blocks.BEDROCK);
        h.getLevel().setBlock(h.absolutePos(new BlockPos(1, 2, 4)), Blocks.BARRIER.defaultBlockState(), 3);
        h.getLevel().setBlock(h.absolutePos(new BlockPos(3, 2, 4)), Blocks.REINFORCED_DEEPSLATE.defaultBlockState(), 3);
        ServerPlayer daisy = daisy(h, new BlockPos(2, 1, 1));
        // Stone, but the player beside it may not build here (as in adventure mode).
        wall(h, 7, 9, Blocks.STONE);
        ServerPlayer visitor = daisy(h, new BlockPos(8, 1, 1));
        visitor.getAbilities().mayBuild = false;
        try {
            quakeBlast().activate(use(daisy));
            quakeBlast().activate(use(visitor));
            for (int tick = 0; tick < 200; tick++) { hold(daisy); hold(visitor); }
            for (int x = 1; x <= 3; x++) for (int y = 1; y <= 4; y++) {
                BlockPos at = new BlockPos(x, y, 4);
                h.assertTrue(!h.getLevel().getBlockState(h.absolutePos(at)).isAir(), "Unbreakable blocks stand at " + at);
            }
            h.assertTrue(is(h, new BlockPos(2, 2, 4), Blocks.BEDROCK), "Ten seconds of beam on bedrock leaves bedrock");
            h.assertTrue(QuakeBlockPressure.cracking(daisy) == 0
                    && QuakeBlockPressure.stage(daisy, h.absolutePos(new BlockPos(2, 2, 4))) == -1,
                    "And not a single crack shows on it: " + QuakeBlockPressure.cracked(daisy).stream()
                            .map(at -> at + " " + h.getLevel().getBlockState(at).getBlock()).toList());
            h.assertTrue(QuakeBlockPressure.cracking(visitor) == 0 && is(h, new BlockPos(8, 2, 4), Blocks.STONE),
                    "Where she may not build, stone neither cracks nor breaks");
            h.succeed();
        } finally { QuakeService.stopAll(daisy); QuakeService.stopAll(visitor); clearSpace(h); }
    }

    @GameTest(template = "rewind_empty", batch = "quake.break_hardness")
    public static void harderBlocksTakeLonger(GameTestHelper h) {
        clearSpace(h);
        ServerPlayer p = daisy(h, new BlockPos(5, 1, 1));
        BlockPos dirt = h.absolutePos(new BlockPos(2, 1, 8)), stone = h.absolutePos(new BlockPos(5, 1, 8)),
                obsidian = h.absolutePos(new BlockPos(8, 1, 8));
        h.getLevel().setBlock(dirt, Blocks.DIRT.defaultBlockState(), 3);
        h.getLevel().setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(obsidian, Blocks.OBSIDIAN.defaultBlockState(), 3);
        try {
            for (int tick = 0; tick < 20; tick++) {
                for (BlockPos at : new BlockPos[]{dirt, stone, obsidian}) QuakeBlockPressure.work(p, at, 1.0F);
                QuakeBlockPressure.tick();
            }
            float d = QuakeBlockPressure.progress(p, dirt), s = QuakeBlockPressure.progress(p, stone),
                    o = QuakeBlockPressure.progress(p, obsidian);
            h.assertTrue(d > s && s > o && o > 0, "Dirt gives faster than stone, stone faster than obsidian: " + d + " " + s + " " + o);
            h.assertTrue(QuakeBlockPressure.ticksToBreak(50) > 20 * 15,
                    "Obsidian outlasts a whole held Quake Blast");
            h.succeed();
        } finally { QuakeService.stopAll(p); clearSpace(h); }
    }

    @GameTest(template = "rewind_empty", batch = "quake.break_earthquake")
    @SuppressWarnings("unchecked")
    public static void anEarthquakeOpensFissuresButLeavesHerFootingWhole(GameTestHelper h) {
        clearSpace(h);
        for (int x = 0; x < 12; x++) for (int z = 0; z < 12; z++)
            h.getLevel().setBlock(h.absolutePos(new BlockPos(x, 1, z)), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        ServerPlayer p = daisy(h, new BlockPos(6, 2, 6));
        try {
            // A small quake that stays inside this test's space: radius 7, eight seconds, breaking nothing fragile.
            call("startEarthquake", p, 7.0D, 160, 1.0F, false, true);
            Object quake = ((Map<UUID, ?>) field("EARTHQUAKES")).get(p.getUUID());
            int maximumCracks = 0;
            for (int tick = 0; tick < 160; tick++) {
                call("tickEarthquake", p, quake, h.getLevel().getGameTime());
                QuakeBlockPressure.tick();
                maximumCracks = Math.max(maximumCracks, QuakeBlockPressure.cracking(p));
            }
            int broken = 0;
            for (int x = 0; x < 12; x++) for (int z = 0; z < 12; z++)
                if (!is(h, new BlockPos(x, 1, z), Blocks.GRASS_BLOCK)) broken++;
            h.assertTrue(maximumCracks > 0, "The ground cracks along the fissures");
            h.assertTrue(broken > 0 && broken <= 9, "Some fissure ground gives way, only on the fissures: " + broken);
            for (int x = 5; x <= 7; x++) for (int z = 5; z <= 7; z++)
                h.assertTrue(is(h, new BlockPos(x, 1, z), Blocks.GRASS_BLOCK), "Her footing holds at " + x + " " + z);
            int fading = 0;
            while (QuakeBlockPressure.cracking(p) > 0 && fading < 200) { QuakeBlockPressure.tick(); fading++; }
            h.assertTrue(QuakeBlockPressure.cracking(p) == 0, "Once the quake ends, the cracks fade");
            h.succeed();
        } finally { QuakeService.stopAll(p); clearDrops(h); clearSpace(h); }
    }
}
