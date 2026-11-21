package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

/**
 * Lasting consequences: what villages remember (gratitude, memorials, busts, ruins, greetings), ground that heals, and
 * rebuilding. Each test stands its own little village (a bell on the arena's floor) in its own batch.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class ConsequenceGameTests {
    /** On top of the arena's stone floor (which is at absolutePos y 1). */
    private static BlockPos at(GameTestHelper h, int x, int z) { return h.absolutePos(new BlockPos(x, 2, z)); }

    private static BlockPos bell(GameTestHelper h) {
        BlockPos bell = at(h, 6, 6);
        h.getLevel().setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
        return bell;
    }

    private static Villager villager(GameTestHelper h, BlockPos at) {
        Villager v = EntityType.VILLAGER.create(h.getLevel());
        v.moveTo(Vec3.atBottomCenterOf(at));
        v.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        h.getLevel().addFreshEntity(v);
        return v;
    }

    private static ServerPlayer player(GameTestHelper h, BlockPos at, String name, ResourceLocation power) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), name)) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
        };
        try {
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true); grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(at));
        p.setGameMode(GameType.SURVIVAL);
        var data = com.jayptucker.heroesevolved.ability.data.PlayerAbilityData.empty();
        if (power != null) data = data.assignDormant(power).unlock(power);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, data);
        h.getLevel().addNewPlayer(p);
        return p;
    }

    private static void cleanup(GameTestHelper h, BlockPos bell, Entity... entities) {
        for (Entity e : entities) {
            if (e instanceof ServerPlayer p) p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
            else if (e != null) e.discard();
        }
        if (bell != null) VillageMemory.forget(h.getLevel(), bell);
    }

    /** Any sign near {@code bell} whose front says {@code words}. */
    private static boolean signSays(GameTestHelper h, BlockPos bell, String words) {
        for (BlockPos p : BlockPos.betweenClosed(bell.offset(-9, -2, -9), bell.offset(9, 3, 9)))
            if (h.getLevel().getBlockEntity(p) instanceof SignBlockEntity sign)
                for (var line : sign.getFrontText().getMessages(false)) if (line.getString().contains(words)) return true;
        return false;
    }

    private static boolean blockNear(GameTestHelper h, BlockPos bell, net.minecraft.world.level.block.Block block) {
        for (BlockPos p : BlockPos.betweenClosed(bell.offset(-9, -2, -9), bell.offset(9, 3, 9)))
            if (h.getLevel().getBlockState(p).is(block)) return true;
        return false;
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "memory.saved")
    public static void aSavedVillageThanksItsHeroAndPutsUpAMemorial(GameTestHelper h) {
        BlockPos bell = bell(h);
        Villager neighbour = villager(h, at(h, 3, 3));
        ServerPlayer hero = player(h, at(h, 9, 9), "Wren", null), friend = player(h, at(h, 2, 9), "Ash", null);
        try {
            var v = VillageMemory.record(h.getLevel(), bell, "the wildfire", VillageMemory.Outcome.SAVED, hero, List.of(hero, friend), 3, 3);
            h.assertTrue(v.name() != null && !v.name().isEmpty() && v.name().equals(VillageMemory.nameFor(bell)), "It has a name of its own: " + v.name());
            h.assertTrue(VillageMemory.name(h.getLevel(), bell).equals(v.name()), "And it is always the same");
            h.assertTrue(v.standing(hero.getUUID()) == VillageMemory.HERO_STANDING && v.standing(friend.getUUID()) == VillageMemory.PRESENT_STANDING,
                    "It owes whoever ended it most, and everyone there something");
            h.assertTrue(neighbour.getPlayerReputation(hero) > neighbour.getPlayerReputation(friend) && neighbour.getPlayerReputation(friend) > 0,
                    "Its people think well of them (better trades): " + neighbour.getPlayerReputation(hero) + ", " + neighbour.getPlayerReputation(friend));
            h.assertTrue(signSays(h, bell, "IN THANKS") && signSays(h, bell, "Wren") && signSays(h, bell, "The Wildfire"),
                    "A memorial by its bell says what came and who ended it");
            h.assertTrue(!blockNear(h, bell, Blocks.PLAYER_HEAD), "No bust yet");
            VillageMemory.record(h.getLevel(), bell, "a meltdown", VillageMemory.Outcome.SAVED, hero, List.of(hero), 3, 3);
            h.assertTrue(blockNear(h, bell, Blocks.PLAYER_HEAD) && signSays(h, bell, "twice"), "Saved twice by the same hero: their bust");
            h.assertTrue(v.memorials().size() == 3, "Two memorials and a bust: " + v.memorials().size());
            h.succeed();
        } finally { cleanup(h, bell, neighbour, hero, friend); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "memory.fallen")
    public static void aFallenVillageStaysARuinWithAStoneForTheLost(GameTestHelper h) {
        BlockPos bell = bell(h);
        BlockPos door = at(h, 2, 2), window = at(h, 3, 2).above();
        h.getLevel().setBlockAndUpdate(door, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        h.getLevel().setBlockAndUpdate(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        h.getLevel().setBlockAndUpdate(window.below(), Blocks.OAK_PLANKS.defaultBlockState());
        h.getLevel().setBlockAndUpdate(window, Blocks.GLASS.defaultBlockState());
        try {
            var v = VillageMemory.record(h.getLevel(), bell, "a renegade", VillageMemory.Outcome.FALLEN, null, List.of(), 0, 4);
            h.assertTrue(v.fallen(), "It has fallen");
            h.assertTrue(h.getLevel().getBlockState(door).isAir() && h.getLevel().getBlockState(door.above()).isAir(), "Its doors are torn off");
            h.assertTrue(h.getLevel().getBlockState(window).isAir(), "Its windows are broken");
            h.assertTrue(h.getLevel().getBlockState(bell).is(Blocks.BELL), "Its bell still hangs");
            h.assertTrue(signSays(h, bell, v.name().toUpperCase()) && signSays(h, bell, "Remember them"), "A stone for the lost stands by its bell");
            h.succeed();
        } finally { cleanup(h, bell); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 120, batch = "memory.greet")
    public static void itsPeopleGreetTheirHeroByName(GameTestHelper h) {
        BlockPos bell = bell(h);
        Villager neighbour = villager(h, at(h, 4, 6));
        ServerPlayer hero = player(h, at(h, 7, 6), "Juniper", null);
        VillageMemory.record(h.getLevel(), bell, "a great meteor", VillageMemory.Outcome.SAVED, hero, List.of(hero), 2, 2);
        VillageMemory.greetSoon(hero);
        h.runAfterDelay(90, () -> {
            try {
                String said = VillageMemory.greeting(neighbour);
                h.assertTrue(said != null && said.contains("Juniper"), "Someone of the village greets them by name: " + said);
                h.succeed();
            } finally { cleanup(h, bell, neighbour, hero); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "scars.crater")
    public static void aCraterHealsButKeepsItsMeteorite(GameTestHelper h) {
        BlockPos center = at(h, 6, 6);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                h.getLevel().setBlockAndUpdate(center.offset(dx, 0, dz), ((dx + dz) % 2 == 0 ? Blocks.BLACKSTONE : Blocks.MAGMA_BLOCK).defaultBlockState());
        h.getLevel().setBlockAndUpdate(center, com.jayptucker.heroesevolved.registry.ModBlocks.HELIXITE_METEORITE.get().defaultBlockState());
        var scar = Scars.crater(h.getLevel(), center, 3);
        Scars.heal(h.getLevel(), scar, 40);
        boolean early = false;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-2, 0, -2), center.offset(2, 0, 2)))
            if (h.getLevel().getBlockState(p).is(Blocks.MAGMA_BLOCK)) early = true;
        h.assertTrue(early, "On its first day, nothing has healed yet");
        Scars.age(h.getLevel(), scar, 4.5F);
        Scars.heal(h.getLevel(), scar, 400);
        int scorched = 0, soil = 0;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-2, 0, -2), center.offset(2, 0, 2))) {
            var s = h.getLevel().getBlockState(p);
            if (s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.BLACKSTONE)) scorched++;
            if (s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.COARSE_DIRT)) soil++;
        }
        h.assertTrue(scorched == 0 && soil >= 20, "Days on, its scorched floor has turned back to soil: " + scorched + " scorched, " + soil + " soil");
        h.assertTrue(h.getLevel().getBlockState(center).is(com.jayptucker.heroesevolved.registry.ModBlocks.HELIXITE_METEORITE.get()),
                "Its meteorite stays: a landmark");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "scars.bloom")
    public static void aBurnBloomsDaysLater(GameTestHelper h) {
        BlockPos center = at(h, 6, 6);
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) h.getLevel().setBlockAndUpdate(center.offset(dx, 0, dz), Blocks.GRASS_BLOCK.defaultBlockState());
        var scar = Scars.burn(h.getLevel(), center, 3);
        Scars.age(h.getLevel(), scar, 3);
        Scars.heal(h.getLevel(), scar, 60);
        int growing = 0;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-3, 1, -3), center.offset(3, 1, 3)))
            if (!h.getLevel().getBlockState(p).isAir()) growing++;
        h.assertTrue(growing >= 10, "Grass and flowers have come up thick over it: " + growing + " (top "
                + h.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ())
                + " at " + center.getY() + ", under " + h.getLevel().getBlockState(center) + ", days " + scar.days(h.getLevel().getGameTime()) + ")");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "rebuild")
    public static void aVillageRebuildsWhatWasBrokenButKeepsWhatWasBuiltSince(GameTestHelper h) {
        BlockPos bell = bell(h), center = at(h, 3, 3);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = 0; dy <= 2; dy++)
                for (int dz = -1; dz <= 1; dz++) h.getLevel().setBlockAndUpdate(center.offset(dx, dy, dz), Blocks.OAK_PLANKS.defaultBlockState());
        var site = Rebuild.note(h.getLevel(), bell, center.above(), 2);
        int noted = site.size();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 2, 1))) h.getLevel().setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        // Something a player built there since: kept.
        h.getLevel().setBlockAndUpdate(center.above(), Blocks.STONE.defaultBlockState());
        try {
            h.assertTrue(noted >= 20, "What stood there is noted: " + noted);
            Rebuild.begin(h.getLevel(), site);
            h.assertTrue(site.started(), "It waits for the morning");
            Rebuild.hurry(h.getLevel(), site, 2000);
            int planks = 0;
            for (BlockPos p : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 2, 1)))
                if (h.getLevel().getBlockState(p).is(Blocks.OAK_PLANKS)) planks++;
            h.assertTrue(planks == 26, "It goes back up as it was: " + planks + " of 26");
            h.assertTrue(h.getLevel().getBlockState(center.above()).is(Blocks.STONE), "What was built there since is kept");
            h.assertTrue(!Rebuild.all(h.getLevel()).contains(site), "Then the site is done");
            h.succeed();
        } finally { cleanup(h, bell); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 260, batch = "memory.overload")
    public static void anOverloadCalmedIsRememberedByItsVillage(GameTestHelper h) {
        BlockPos bell = bell(h);
        Villager eye = villager(h, at(h, 4, 6));
        com.jayptucker.heroesevolved.mimicry.GiftedVillagers.give(eye, null);
        ServerPlayer friend = player(h, at(h, 5, 7), "Sorrel", ModAbilities.PYROKINESIS_ID);
        var o = com.jayptucker.heroesevolved.village.overload.Overloads.start(eye, com.jayptucker.heroesevolved.village.overload.Storm.WILDFIRE, 4000);
        h.runAfterDelay(200, () -> {
            try {
                h.assertTrue(o != null && !com.jayptucker.heroesevolved.village.overload.Overloads.critical(eye), "It was calmed");
                var v = VillageMemory.near(h.getLevel(), bell);
                h.assertTrue(v != null && v.lastSaved() != null && v.lastSaved().what().equals("the wildfire")
                        && "Sorrel".equals(v.lastSaved().hero()), "Its village remembers who calmed the wildfire");
                h.succeed();
            } finally { GiftedMobs.cleanse(eye); cleanup(h, bell, eye, friend); }
        });
    }
}
