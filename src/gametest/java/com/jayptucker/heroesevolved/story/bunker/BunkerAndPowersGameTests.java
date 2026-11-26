package com.jayptucker.heroesevolved.story.bunker;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.AbilityActivationResult;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.invisibility.InvisibilityService;
import com.jayptucker.heroesevolved.mimicry.MimicryService;
import com.jayptucker.heroesevolved.phasing.DensityService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class BunkerAndPowersGameTests {
    private static ServerPlayer player(GameTestHelper h, ResourceLocation power, int level) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "bunker-test")) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
            // FakePlayers ignore connection teleports; move them directly, as the speed tests do.
            @Override public void teleportTo(net.minecraft.server.level.ServerLevel level, double x, double y, double z, float yaw, float pitch) {
                super.teleportTo(level, x, y, z, yaw, pitch); absMoveTo(x, y, z, yaw, pitch);
            }
        };
        try {
            Connection connection = p.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true); grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3)))); p.setYRot(0); p.setXRot(0);
        if (power != null) p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        PlayerProgressionService.setLevel(p, level);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        return p;
    }

    private static LivingEntity target(GameTestHelper h, ServerPlayer p, double distance) {
        var target = EntityType.PILLAGER.create(h.getLevel());
        target.setNoAi(true); target.setPersistenceRequired();
        target.moveTo(p.getX(), p.getY(), p.getZ() + distance, 180, 0);
        h.getLevel().addFreshEntity(target);
        return target;
    }

    private static BlockPos pod(GameTestHelper h, BlockPos relative, boolean open) {
        BlockPos base = h.absolutePos(relative);
        var state = ModBlocks.CRYOCHAMBER.get().defaultBlockState().setValue(CryoChamberBlock.FACING, Direction.SOUTH).setValue(CryoChamberBlock.OPEN, open);
        h.getLevel().setBlock(base, state, 2);
        h.getLevel().setBlock(base.above(), state.setValue(CryoChamberBlock.HALF, DoubleBlockHalf.UPPER), 2);
        return base;
    }

    @GameTest(template = "rewind_empty", batch = "cryochamber")
    public static void cryochamberSealsHealsProtectsAndSetsRespawn(GameTestHelper h) {
        ServerPlayer p = player(h, null, 1);
        BlockPos base = pod(h, new BlockPos(3, 2, 3), true);
        try {
            h.assertTrue(CryoChamberService.use(p, base), "Using an open pod steps inside");
            h.assertTrue(CryoChamberService.isInside(p) && !h.getLevel().getBlockState(base).getValue(CryoChamberBlock.OPEN),
                    "The pod seals around its sleeper");
            h.assertTrue(base.equals(p.getRespawnPosition()), "A pod becomes the sleeper's respawn point");
            var respawn = ModBlocks.CRYOCHAMBER.get().getRespawnPosition(h.getLevel().getBlockState(base), EntityType.PLAYER, h.getLevel(), base, 0);
            h.assertTrue(respawn.isPresent(), "A pod offers a place to respawn");
            float health = p.getHealth();
            p.hurt(p.damageSources().generic(), 5);
            h.assertTrue(p.getHealth() == health, "Stasis blocks ordinary damage");
            h.assertTrue(CryoChamberService.exit(p) && !CryoChamberService.isInside(p)
                    && h.getLevel().getBlockState(base).getValue(CryoChamberBlock.OPEN), "Stepping out opens the pod");
            h.succeed();
        } finally { CryoChamberService.release(p); }
    }

    @GameTest(template = "rewind_empty", batch = "bunker", timeoutTicks = 400)
    public static void bunkerIsBuiltWithPodsAndLogsAndSealsTheSleeper(GameTestHelper h) {
        var server = h.getLevel().getServer();
        BunkerData data = BunkerService.ensureBuilt(server);
        var overworld = server.overworld();
        var layout = data.layout();
        h.assertTrue(layout.stasis() && layout.hasLab(), "A new bunker is Sublevel 4 as rebuilt, with the Helix Ward");
        for (int i = 0; i < layout.pods(); i++) {
            var state = overworld.getBlockState(data.origin.offset(layout.podOffset(i)));
            h.assertTrue(state.is(ModBlocks.CRYOCHAMBER.get()) && state.getValue(com.jayptucker.heroesevolved.story.bunker.CryoChamberBlock.FACING) == layout.podFacing(i),
                    "Pod " + i + " stands where BunkerLayout says, facing " + layout.podFacing(i) + ": " + state);
        }
        h.assertTrue(overworld.getBlockState(data.origin.offset(layout.orientation())).is(Blocks.CHEST)
                        && BunkerBuilder.log(overworld, data.origin.offset(layout.orientation())).getHoverName().getString().contains("Orientation"),
                "The orientation log waits in a chest in the hall's aisle");
        h.assertTrue(BunkerData.get(server).built && BunkerService.ensureBuilt(server).origin.equals(data.origin), "The bunker is built only once");
        BlockPos receiver = BunkerBuilder.receiverPos(data);
        h.assertTrue(BunkerBuilder.isReceiver(overworld.getBlockState(receiver)) && !overworld.getBlockState(receiver).is(Blocks.WAXED_WEATHERED_CUT_COPPER),
                "The radio receiver stands on the records desk");
        h.assertTrue(BunkerBuilder.survivorPositions(data).size() == 2
                        && BunkerBuilder.survivorPositions(data).stream().allMatch(pos ->
                                overworld.getBlockState(pos).isAir() && overworld.getBlockState(pos.above()).isAir()),
                "Both recovery spots have room to stand");
        // The Helix Ward: Weir's journal on his lectern, the blast door jammed, and the vent open through to the hall.
        h.assertTrue(BunkerBuilder.log(overworld, data.origin.offset(layout.marker("log:weir").getFirst())).getHoverName().getString().contains("Attending"),
                "Dr. Weir's journal waits in his office");
        for (int x = 14; x <= 16; x++)
            h.assertTrue(!overworld.getBlockState(data.origin.offset(x, 1, 24)).isAir() && overworld.getBlockState(data.origin.offset(x, 1, 23)).is(Blocks.IRON_BLOCK),
                    "The blast door to 4-R is shut and the passage behind it full of stone at x " + x);
        // The ward's vent: a slab floor under a solid top, a block and a half to crouch through, all the way to the hall.
        for (int x = 10; x <= 13; x++) {
            BlockPos duct = data.origin.offset(x, 7, 46);
            var floor = overworld.getBlockState(duct);
            h.assertTrue(floor.getBlock() instanceof net.minecraft.world.level.block.SlabBlock
                            && floor.getValue(net.minecraft.world.level.block.SlabBlock.TYPE) == net.minecraft.world.level.block.state.properties.SlabType.BOTTOM
                            && overworld.getBlockState(duct.above()).getCollisionShape(overworld, duct.above()).isEmpty()
                            && !overworld.getBlockState(duct.above(2)).getCollisionShape(overworld, duct.above(2)).isEmpty(),
                    "The ward's vent is a crawl-height duct at " + x);
        }
        h.assertTrue(!overworld.getBlockState(data.origin.offset(8, 6, 46)).isAir() && !overworld.getBlockState(data.origin.offset(14, 6, 46)).isAir(),
                "...with the catwalk at one end and the mezzanine at the other");
        h.assertTrue(overworld.getBlockEntity(data.origin.offset(layout.marker("loot:lab").getFirst())) instanceof ChestBlockEntity lab
                        && lab.getItem(6).is(ModItems.BLOOD_SAMPLE.get())
                        && lab.getItem(6).getOrDefault(ModItems.BLOOD_PURITY.get(),
                                new com.jayptucker.heroesevolved.formula.BloodPurity(false, 1, "")).donated(),
                "The lab offers a donated Formula sample");
        h.assertTrue(overworld.getBlockState(data.origin.offset(4, 0, 9)).getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock,
                "The maintenance hatch is in the security booth's floor");
        h.assertTrue(overworld.getBlockState(data.origin.offset(4, 1, 9)).isAir(), "The hatch is marked and exposed");
        boolean deepEnough = data.origin.getY() - 8 > overworld.getMinBuildHeight();   // shallow worlds have no room below
        if (deepEnough) {
            h.assertTrue(overworld.getBlockState(data.origin.offset(4, -1, 9)).is(Blocks.LADDER), "A ladder goes down from the hatch");
            h.assertTrue(overworld.getBlockState(data.origin.offset(6, -7, 2)).is(Blocks.CRAFTING_TABLE), "The maintenance bay has a workbench");
            h.assertTrue(BunkerBuilder.log(overworld, data.origin.offset(layout.marker("log:bennet4r").getFirst())).getHoverName().getString().contains("Not for the Files"),
                    "Bennet's notes on 4-R wait in maintenance");
        }
        // Pod 14: Claire's, open, clawed through from inside, with her note on a lectern beside it (put back if taken).
        BunkerBuilder.releaseClaire(overworld, data);
        BlockPos claire = data.origin.offset(layout.podOffset(com.jayptucker.heroesevolved.story.night.NightThreeBunker.CLAIRE));
        for (BlockPos half : new BlockPos[] {claire, claire.above()})
            h.assertTrue(overworld.getBlockState(half).is(ModBlocks.CRYOCHAMBER.get())
                            && overworld.getBlockState(half).getValue(com.jayptucker.heroesevolved.story.bunker.CryoChamberBlock.OPEN)
                            && overworld.getBlockState(half).getValue(com.jayptucker.heroesevolved.story.bunker.CryoChamberBlock.CRACKED),
                    "Pod 14 stands open, its glass clawed through: " + overworld.getBlockState(half));
        BlockPos note = BunkerBuilder.claireLectern(data);
        h.assertTrue(BunkerBuilder.log(overworld, note).getHoverName().getString().contains("Whoever Wakes Next"),
                "Claire's note waits in the chest beside her pod");
        ((ChestBlockEntity) overworld.getBlockEntity(note)).clearContent();
        BunkerBuilder.releaseClaire(overworld, data);
        h.assertTrue(!BunkerBuilder.log(overworld, note).isEmpty(), "...and is put back if someone takes it");
        // Walking up to Pod 14 turns up Claire's note, once; no right-click needed.
        ServerPlayer walker = player(h, null, 1);
        walker.getPersistentData().remove(CryoChamberService.CLAIRE_NOTE);
        Vec3 front = Vec3.atBottomCenterOf(claire).add(-1.6, 0, 0);
        walker.setPos(front);
        h.assertTrue(CryoChamberService.noticeClaire(walker, data), "Walking up to Pod 14 finds what Claire left");
        h.assertTrue(!CryoChamberService.noticeClaire(walker, data), "...once");
        // The pods nobody has a claim on hold Odessa's people, dead a long time; nobody's pod holds anyone else.
        java.util.Set<Long> podChunks = new java.util.HashSet<>();
        for (int i = 0; i < com.jayptucker.heroesevolved.story.bunker.BunkerLayout.HALL_PODS; i++)
            podChunks.add(net.minecraft.world.level.ChunkPos.asLong(data.origin.offset(layout.podOffset(i))));
        // The whole hall and a block round it, so its skulls can be laid.
        var hall = BunkerLayout.Scene.HALL;
        for (int x = hall.minX() - 2; x <= hall.maxX() + 2; x += 4)
            for (int z = hall.minZ() - 2; z <= hall.maxZ() + 2; z += 4)
                podChunks.add(net.minecraft.world.level.ChunkPos.asLong(data.origin.offset(x, 0, z)));
        java.util.List<Long> forcedPods = new java.util.ArrayList<>();
        for (long c : podChunks)
            if (overworld.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c), net.minecraft.world.level.ChunkPos.getZ(c), true)) forcedPods.add(c);
        // Exhibit 1 hangs on the far wall, seventy blocks from the vault: its chunk's entities must be loaded to see it.
        BlockPos exhibit = data.origin.offset(layout.marker(BunkerBuilder.EXHIBIT_ONE).getFirst());
        boolean forced = overworld.setChunkForced(exhibit.getX() >> 4, exhibit.getZ() >> 4, true);
        h.succeedWhen(() -> {
            h.assertTrue(overworld.isPositionEntityTicking(exhibit), "Waiting for the far wall's chunk");
            for (long c : podChunks)
                h.assertTrue(overworld.areEntitiesLoaded(c), "Waiting for the hall's pods to load");
            com.jayptucker.heroesevolved.story.bunker.PodSleepers.present(overworld, data);
            for (int i = 0; i < com.jayptucker.heroesevolved.story.bunker.BunkerLayout.HALL_PODS; i++) {
                BlockPos base = data.origin.offset(layout.podOffset(i));
                boolean someone = com.jayptucker.heroesevolved.story.bunker.PodSleepers.occupied(overworld, base);
                if (!com.jayptucker.heroesevolved.story.bunker.PodSleepers.hallPod(i) || data.pods.containsValue(i)) {
                    h.assertTrue(!someone, "Pod " + (i + 1) + " is someone's, or Claire's: nobody else in it");
                    continue;
                }
                boolean keeps = com.jayptucker.heroesevolved.story.bunker.PodSleepers.keeps(data.origin, i);
                var condition = overworld.getBlockState(base).getValue(CryoChamberBlock.CONDITION);
                var plate = overworld.getBlockEntity(data.origin.offset(layout.podSign(i)));
                h.assertTrue(someone == keeps, "Pod " + (i + 1) + ": a body only in the pods that kept one");
                h.assertTrue(keeps ? condition == CryoChamberBlock.Condition.FROSTED : condition.wrecked(),
                        "Pod " + (i + 1) + " is frosted over its body, or wrecked and empty: " + condition);
                h.assertTrue(plate instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign
                                && sign.getFrontText().getMessage(2, false).getString().equals(keeps ? "DECEASED" : "POD BREACHED")
                                && sign.getFrontText().getMessage(3, false).getString().equals(keeps ? "CAUSE UNKNOWN" : "NO REMAINS"),
                        "...and its nameplate says which");
            }
            com.jayptucker.heroesevolved.story.bunker.PodSleepers.present(overworld, data);
            int bodies = overworld.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class,
                    new net.minecraft.world.phys.AABB(data.origin).inflate(30, 6, 80),
                    v -> v.getTags().contains(com.jayptucker.heroesevolved.story.bunker.PodSleepers.TAG)).size();
            long kept = java.util.stream.IntStream.range(0, com.jayptucker.heroesevolved.story.bunker.BunkerLayout.HALL_PODS)
                    .filter(i -> com.jayptucker.heroesevolved.story.bunker.PodSleepers.keeps(data.origin, i) && !data.pods.containsValue(i)).count();
            h.assertTrue(bodies == kept, "One body to a kept pod, never two: " + bodies + " for " + kept);
            long allKept = java.util.stream.IntStream.range(0, com.jayptucker.heroesevolved.story.bunker.BunkerLayout.HALL_PODS)
                    .filter(i -> com.jayptucker.heroesevolved.story.bunker.PodSleepers.keeps(data.origin, i)).count();
            h.assertTrue(allKept == 7, "About half the hall's thirteen pods keep their sleeper: " + allKept);
            // No village event can see them, even with a bell right over the bunker.
            overworld.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, new net.minecraft.world.phys.AABB(data.origin).inflate(30, 6, 80),
                    com.jayptucker.heroesevolved.village.Villagers::inStasis).forEach(body -> {
                h.assertTrue(!com.jayptucker.heroesevolved.village.Villages.belongs(overworld, body.blockPosition(), body),
                        "A body in a pod belongs to no village");
                h.assertTrue(!body.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 40)),
                        "...and no effect takes on it");
            });
            h.assertTrue(com.jayptucker.heroesevolved.village.Villages.members(overworld, data.origin.offset(0, 1, 50)).stream()
                    .noneMatch(com.jayptucker.heroesevolved.village.Villagers::inStasis), "A village over the hall doesn't count them");
            // The rooms have their skulls, and none of them lies in front of a pod.
            BunkerBones.strew(overworld, data);
            int skulls = 0;
            for (BlockPos pos : BlockPos.betweenClosed(data.origin.offset(-10, 1, 33), data.origin.offset(10, 4, 69)))
                if (overworld.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.SKELETON_SKULL)) {
                    skulls++;
                    for (Direction side : Direction.Plane.HORIZONTAL)
                        h.assertTrue(!(overworld.getBlockState(pos.relative(side)).getBlock() instanceof CryoChamberBlock), "A skull blocks a pod's front at " + pos);
                }
            h.assertTrue(skulls > 0, "Skulls lie about the stasis hall");
            for (long c : forcedPods) overworld.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c), net.minecraft.world.level.ChunkPos.getZ(c), false);
            forcedPods.clear();
            h.assertTrue(explosions(overworld, data.origin).size() == 1 && explosions(overworld, data.origin).getFirst().survives(),
                    "Isaac's explosion, Exhibit 1, hangs whole above the balcony: " + explosions(overworld, data.origin));
            h.assertTrue(exhibitOne(overworld, data).equals("EXHIBIT 1"), "Its label reads EXHIBIT 1: " + exhibitOne(overworld, data));
            if (forced) overworld.setChunkForced(exhibit.getX() >> 4, exhibit.getZ() >> 4, false);
        });
    }

    private static java.util.List<net.minecraft.world.entity.decoration.Painting> explosions(net.minecraft.server.level.ServerLevel level, BlockPos origin) {
        return level.getEntitiesOfClass(net.minecraft.world.entity.decoration.Painting.class,
                new net.minecraft.world.phys.AABB(origin.offset(-40, -10, -12).getCenter(), origin.offset(45, 16, 75).getCenter()),
                painting -> painting.getVariant().is(ResourceLocation.fromNamespaceAndPath("heroesevolved", "explosion")));
    }

    private static BlockPos chamberCell(BunkerData data, String marker) {
        return data.origin.offset(data.layout().marker(marker).getFirst());
    }

    /**
     * A bunker as worlds built before the Sublevel 4 rebuild have it, placed apart from the real one with its own data:
     * the original rooms on emergency power, as they were built.
     */
    private static BunkerData legacyBunker(net.minecraft.server.level.ServerLevel level, BlockPos origin) {
        BunkerData data = new BunkerData();
        data.built = true;
        data.design = 1;
        data.origin = origin;
        data.layout = BunkerData.LAYOUT;
        data.powered = false;
        for (var room : com.jayptucker.heroesevolved.story.bunker.BunkerLayout.LEGACY.all())
            com.jayptucker.heroesevolved.structure.Blueprint.load(room.blueprint()).place(level, origin.offset(room.at()),
                    net.minecraft.world.level.block.Rotation.NONE, Integer.MAX_VALUE - 1, BunkerPower::unpowered);
        return data;
    }

    /** The first line on Exhibit 1's label, or what stands there instead. */
    private static String exhibitOne(net.minecraft.server.level.ServerLevel level, BunkerData data) {
        BlockPos sign = chamberCell(data, "sign:exhibit1");
        return level.getBlockEntity(sign) instanceof net.minecraft.world.level.block.entity.SignBlockEntity label
                ? label.getFrontText().getMessage(0, false).getString() : level.getBlockState(sign).toString();
    }

    /**
     * A bunker built under layout 2 takes Exhibit 1 down from high on the far wall and hangs it in the dead
     * screen's place once someone is near, and nothing else is rebuilt.
     */
    @GameTest(template = "rewind_empty", batch = "bunker.upgrade", timeoutTicks = 400)
    public static void anOlderBunkerHangsExhibitOneInTheScreensPlace(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var overworld = server.overworld();
        // Off to the side of the real bunker, in chunks forced and loaded first so its paintings can be hung and found.
        BlockPos origin = BunkerService.ensureBuilt(server).origin.offset(160, 0, 0);
        var min = new ChunkPos(origin.offset(-14, 0, 0));
        var max = new ChunkPos(origin.offset(15, 0, 40));
        var forced = new java.util.ArrayList<ChunkPos>();
        for (int x = min.x; x <= max.x; x++)
            for (int z = min.z; z <= max.z; z++)
                if (overworld.setChunkForced(x, z, true)) forced.add(new ChunkPos(x, z));
        h.startSequence()
                .thenWaitUntil(() -> {
                    for (int x = min.x; x <= max.x; x++)
                        for (int z = min.z; z <= max.z; z++)
                            h.assertTrue(overworld.isPositionEntityTicking(new BlockPos(x << 4, origin.getY(), z << 4)), "Waiting for the older bunker's chunks");
                })
                .thenExecute(() -> upgradeAnOlderBunker(h, overworld, legacyBunker(overworld, origin), forced));
    }

    private static void upgradeAnOlderBunker(GameTestHelper h, net.minecraft.server.level.ServerLevel overworld, BunkerData data,
                                             java.util.List<ChunkPos> forced) {
        h.assertTrue(!data.layout().stasis(), "An older bunker keeps the original rooms");
        var chamber = com.jayptucker.heroesevolved.structure.Blueprint.load(BunkerBuilder.CHAMBER.blueprint());
        BlockPos at = data.origin.offset(BunkerBuilder.CHAMBER.at());
        java.util.function.Function<BlockPos, BlockPos> cell = grid ->
                at.offset(chamber.offset(grid.getX(), grid.getY(), grid.getZ(), net.minecraft.world.level.block.Rotation.NONE));
        BlockPos beam = cell.apply(BunkerBuilder.BEAM_END);
        BlockPos lectern = data.origin.offset(0, 1, 26);
        var screen = BunkerBuilder.SCREEN;
        var screenGrid = BlockPos.betweenClosedStream(screen.minX(), screen.minY(), screen.minZ(), screen.maxX(), screen.maxY(), screen.maxZ())
                .map(BlockPos::immutable).toList();
        var screenCells = screenGrid.stream().map(cell).toList();
        var oldSigns = BunkerBuilder.FIRST_SIGNS.stream().map(cell).toList();
        for (BlockPos light : BunkerBuilder.EXHIBIT_LIGHTS.stream().map(cell).toList())
            h.assertTrue(overworld.getBlockState(light).is(Blocks.RED_STAINED_GLASS) && (overworld.getBlockState(light.above()).is(Blocks.GLOWSTONE)
                            || overworld.getBlockState(light.above()) == BunkerPower.off(Blocks.GLOWSTONE.defaultBlockState())),
                    "A new bunker has the lights over the far wall where BunkerBuilder.EXHIBIT_LIGHTS says: " + light);
        h.assertTrue(screenCells.stream().allMatch(pos -> overworld.getBlockState(pos).is(Blocks.POLISHED_DEEPSLATE)),
                "A new bunker has the panel behind Exhibit 1 where BunkerBuilder.SCREEN says");
        // As the chamber stood under layout 2: the screen, its sign, and Exhibit 1 high above it with its label beside it.
        explosions(overworld, data.origin).forEach(net.minecraft.world.entity.Entity::discard);
        overworld.setBlock(chamberCell(data, "sign:exhibit1"), Blocks.AIR.defaultBlockState(), 3);
        overworld.setBlock(beam, Blocks.AIR.defaultBlockState(), 3);
        for (BlockPos pos : screenGrid) overworld.setBlock(cell.apply(pos), BunkerBuilder.oldScreenBlock(pos), 3);
        for (int i = 0; i < oldSigns.size(); i++) {
            BlockPos pos = oldSigns.get(i);
            overworld.setBlock(pos, Blocks.SPRUCE_WALL_SIGN.defaultBlockState(), 3);
            if (overworld.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                var text = sign.getFrontText();
                var lines = i == 0 ? BunkerBuilder.OLD_EXHIBIT_LABEL : BunkerBuilder.OLD_SCREEN_LABEL;
                for (int line = 0; line < lines.size(); line++) text = text.setMessage(line, Component.literal(lines.get(line)));
                sign.setText(text, true);
                sign.setWaxed(true);
            }
        }
        var explosion = overworld.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.PAINTING_VARIANT)
                .getHolder(ResourceLocation.fromNamespaceAndPath("heroesevolved", "explosion")).orElseThrow();
        BlockPos high = cell.apply(BunkerBuilder.FIRST_EXHIBIT);
        overworld.addFreshEntity(new net.minecraft.world.entity.decoration.Painting(overworld, high, Direction.NORTH, explosion));
        h.assertTrue(explosions(overworld, data.origin).size() == 1, "Exhibit 1 hangs where layout 2 put it");
        // A player's change elsewhere in the chamber must survive the upgrade.
        overworld.setBlock(lectern.above(), Blocks.GLASS.defaultBlockState(), 3);
        BlockPos alteredPanel = screenCells.getFirst();
        overworld.setBlock(alteredPanel, Blocks.ORANGE_CONCRETE.defaultBlockState(), 3);
        BlockPos oldBeacon = cell.apply(new BlockPos(4, 10, 4));
        overworld.setBlock(oldBeacon, ModBlocks.ALARM_BEACON.get().defaultBlockState(), 3);
        BlockPos oldLamp = cell.apply(BunkerBuilder.EXHIBIT_LIGHTS.getFirst()).above();
        overworld.setBlock(oldLamp, Blocks.REDSTONE_LAMP.defaultBlockState(), 3);
        BlockPos receiver = BunkerBuilder.receiverPos(data);
        overworld.setBlock(receiver, Blocks.BLACK_STAINED_GLASS_PANE.defaultBlockState(), 3);
        data.layout = 2;
        h.succeedWhen(() -> {
            h.assertTrue(BunkerBuilder.upgrade(overworld, data), "The upgrade waits for the rooms to load");
            h.assertTrue(data.layout == BunkerData.LAYOUT, "The bunker is up to date: layout " + data.layout);
            var hanging = explosions(overworld, data.origin);
            h.assertTrue(hanging.size() == 1 && hanging.getFirst().getPos().equals(chamberCell(data, BunkerBuilder.EXHIBIT_ONE))
                    && hanging.getFirst().survives(), "Exhibit 1 hangs whole, in the screen's place: " + hanging);
            h.assertTrue(exhibitOne(overworld, data).equals("EXHIBIT 1"), "Its label moved with it: " + exhibitOne(overworld, data));
            h.assertTrue(oldSigns.stream().allMatch(pos -> overworld.getBlockState(pos).isAir()), "The old label and the screen's sign are gone");
            h.assertTrue(screenCells.stream().filter(pos -> !pos.equals(alteredPanel))
                    .allMatch(pos -> overworld.getBlockState(pos).is(Blocks.POLISHED_DEEPSLATE)), "The original screen became the panel");
            h.assertTrue(overworld.getBlockState(alteredPanel).is(Blocks.ORANGE_CONCRETE), "A changed panel block keeps its own color");
            h.assertTrue(overworld.getBlockState(beam).is(Blocks.WAXED_WEATHERED_CUT_COPPER), "The ceiling beam is whole again");
            h.assertTrue(overworld.getBlockState(lectern.above()).is(Blocks.GLASS), "Nothing else was rebuilt");
            h.assertTrue(overworld.getBlockState(oldBeacon).isAir(), "The excess old beacon was removed");
            h.assertTrue(overworld.getBlockState(oldLamp).equals(data.powered ? Blocks.GLOWSTONE.defaultBlockState()
                    : BunkerPower.off(Blocks.GLOWSTONE.defaultBlockState())), "The first-exhibit light uses the new dark state");
            h.assertTrue(BunkerBuilder.isReceiver(overworld.getBlockState(receiver)) && !overworld.getBlockState(receiver).is(Blocks.WAXED_WEATHERED_CUT_COPPER),
                    "The radio receiver replaced the old display pane");
            h.assertTrue(BunkerBuilder.upgrade(overworld, data) && explosions(overworld, data.origin).size() == 1, "A second upgrade adds nothing");
            overworld.setBlock(lectern.above(), Blocks.AIR.defaultBlockState(), 3);
            overworld.setBlock(alteredPanel, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
            forced.forEach(chunk -> overworld.setChunkForced(chunk.x, chunk.z, false));
        });
    }

    /** Every light the bunker's rooms have, as world positions with the state it has when the power is on. */
    private static java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> bunkerLights(net.minecraft.server.level.ServerLevel level, BunkerData data) {
        java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> lights = new java.util.LinkedHashMap<>();
        var rooms = new java.util.ArrayList<>(data.layout().rooms());
        // On a shallow world there is no room for maintenance.
        for (var deep : data.layout().deep()) if (data.origin.getY() + deep.at().getY() > level.getMinBuildHeight()) rooms.add(deep);
        for (var room : rooms)
            com.jayptucker.heroesevolved.structure.Blueprint.load(room.blueprint()).statesMatching(state -> BunkerPower.off(state) != null)
                    .forEach((cell, on) -> lights.put(data.origin.offset(room.at()).offset(cell), on));
        return lights;
    }

    /**
     * A new bunker wakes on emergency power: every light dark, only the alarm beacons turning. When the main
     * breaker closes the lights come on, each as the blueprint has it, and the beacons stay lit.
     */
    @GameTest(template = "rewind_empty", batch = "bunker.power", timeoutTicks = 200)
    public static void aNewBunkerWakesDarkAndThePowerComesBack(GameTestHelper h) {
        var server = h.getLevel().getServer();
        BunkerData data = BunkerService.ensureBuilt(server);
        var overworld = server.overworld();
        var lights = bunkerLights(overworld, data);
        h.assertTrue(lights.size() > 20, "The bunker's lights are found from its blueprints: " + lights.size());
        var beacons = new java.util.ArrayList<BlockPos>();
        for (var room : data.layout().rooms())
            com.jayptucker.heroesevolved.structure.Blueprint.load(room.blueprint()).markerOffsets(BunkerBuilder.BEACON)
                    .forEach(cell -> beacons.add(data.origin.offset(room.at()).offset(cell)));
        h.assertTrue(beacons.size() >= 10, "Alarm beacons in every room: " + beacons.size());
        for (BlockPos beacon : beacons) {
            var state = overworld.getBlockState(beacon);
            h.assertTrue(state.is(ModBlocks.ALARM_BEACON.get()) && state.getValue(com.jayptucker.heroesevolved.story.bunker.AlarmBeaconBlock.LIT)
                    && overworld.getBlockEntity(beacon) instanceof com.jayptucker.heroesevolved.story.bunker.AlarmBeaconBlockEntity
                    && state.canSurvive(overworld, beacon), "A lit alarm beacon, mounted on a wall or ceiling, at " + beacon + ": " + state);
        }
        Runnable dark = () -> lights.forEach((pos, on) -> {
            var state = overworld.getBlockState(pos);
            h.assertTrue(state == BunkerPower.off(on) && !state.is(Blocks.REDSTONE_LAMP),
                    "Dark on emergency power: " + pos + " is " + state + ", " + on + " when lit");
        });
        Runnable lit = () -> {
            h.assertTrue(data.powered && !BunkerPower.switching(), "The power is back");
            lights.forEach((pos, on) -> h.assertTrue(overworld.getBlockState(pos) == on,
                    "Lit: " + pos + " is " + overworld.getBlockState(pos) + ", not " + on));
            for (BlockPos beacon : beacons)
                h.assertTrue(overworld.getBlockState(beacon).getValue(com.jayptucker.heroesevolved.story.bunker.AlarmBeaconBlock.LIT), "The beacons still turn");
        };
        // Every light's chunk loaded first: the power only reaches what is loaded (as in play, where the bunker is round the
        // sleeper), and reading an unloaded light loads it from disk as an earlier test left it. The bunker test before this
        // one lets go of the far wall's chunk, which once failed this test with a lamp still lit there.
        java.util.Set<Long> chunks = new java.util.HashSet<>();
        lights.keySet().forEach(pos -> chunks.add(net.minecraft.world.level.ChunkPos.asLong(pos)));
        java.util.List<Long> held = new java.util.ArrayList<>();
        for (long c : chunks)
            if (overworld.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c), net.minecraft.world.level.ChunkPos.getZ(c), true)) held.add(c);
        Runnable release = () -> {
            for (long c : held) overworld.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c), net.minecraft.world.level.ChunkPos.getZ(c), false);
            held.clear();
        };
        h.startSequence()
                .thenWaitUntil(() -> lights.keySet().forEach(pos -> h.assertTrue(overworld.isLoaded(pos), "Waiting for the bunker's lights to load")))
                .thenExecute(() -> {
                    // A bunker some earlier test already lit goes dark the way it does for each new sleeper.
                    if (data.powered || BunkerPower.switching()) BunkerPower.cut(overworld, data);
                    try { dark.run(); } catch (RuntimeException e) { release.run(); throw e; }
                    h.assertTrue(BunkerPower.start(overworld, data) && !BunkerPower.start(overworld, data), "The breaker closes once");
                })
                .thenWaitUntil(lit)
                .thenExecute(() -> {
                    // The next sleeper wakes: the power fails again, every light at once, and comes back the same way.
                    BunkerPower.cut(overworld, data);
                    h.assertTrue(!data.powered, "Back on emergency power");
                    dark.run();
                    h.assertTrue(BunkerPower.start(overworld, data), "The breaker closes again");
                })
                .thenWaitUntil(lit)
                .thenExecute(release)
                .thenSucceed();
    }

    /**
     * The Helix Ward takes one of a group sealed together, never someone who lived the Last Day alone, only once, and
     * never in a bunker built before the rebuild; its sleeper carries a random power, dormant.
     */
    /** The waking's music plays while a woken sleeper is in the bunker, and leaving it ends the music with "What happened...?". */
    @GameTest(template = "rewind_empty", batch = "bunker.music", timeoutTicks = 400)
    public static void theWakingMusicPlaysUntilTheyLeaveTheBunker(GameTestHelper h) {
        var server = h.getLevel().getServer();
        BunkerData data = BunkerService.ensureBuilt(server);
        var overworld = server.overworld();
        FakePlayer sleeper = new FakePlayer(overworld, new GameProfile(java.util.UUID.randomUUID(), "music-sleeper"));
        sleeper.getPersistentData().putBoolean(BunkerService.MUSIC, true);
        // In the stasis hall, and up in the ruined lobby: still the bunker.
        for (BlockPos inside : new BlockPos[] {data.origin.offset(0, 1, 50), data.origin.offset(6, 30, -4)}) {
            sleeper.setPos(Vec3.atBottomCenterOf(inside));
            BunkerService.music(sleeper, data);
            h.assertTrue(BunkerService.musicPlaying(sleeper), "The music plays on inside the bunker at " + inside);
        }
        // Out past the lobby into the world.
        sleeper.setPos(Vec3.atBottomCenterOf(data.origin.offset(60, 34, -40)));
        BunkerService.music(sleeper, data);
        h.assertTrue(!BunkerService.musicPlaying(sleeper), "Leaving the bunker ends the music");
        var advancement = server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("heroesevolved", "what_happened"));
        // (A fake player ignores awards, so only check the advancement is there to earn, after Wake Up.)
        h.assertTrue(advancement != null && advancement.value().parent().map(id -> id.getPath().equals("wake_up")).orElse(false),
                "What happened...? is loaded, after Wake Up");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "bunker.ward")
    public static void theWardTakesOneOfAGroupOnceAndDosesThem(GameTestHelper h) {
        var random = net.minecraft.util.RandomSource.create(7);
        BunkerData fresh = new BunkerData();
        java.util.UUID a = java.util.UUID.randomUUID(), b = java.util.UUID.randomUUID();
        h.assertTrue(com.jayptucker.heroesevolved.story.bunker.HelixWard.draw(fresh, java.util.List.of(a), random) == null,
                "Nobody who lives the day alone is drawn");
        java.util.UUID drawn = com.jayptucker.heroesevolved.story.bunker.HelixWard.draw(fresh, java.util.List.of(a, b), random);
        h.assertTrue(drawn != null && (drawn.equals(a) || drawn.equals(b)), "One of a group is drawn");
        fresh.claimLab(drawn);
        h.assertTrue(fresh.pods.get(drawn) == com.jayptucker.heroesevolved.story.bunker.BunkerLayout.LAB_POD
                && com.jayptucker.heroesevolved.story.bunker.HelixWard.draw(fresh, java.util.List.of(a, b), random) == null, "...and only ever one");
        BunkerData old = new BunkerData();
        old.built = true;
        old.design = 1;
        h.assertTrue(com.jayptucker.heroesevolved.story.bunker.HelixWard.draw(old, java.util.List.of(a, b), random) == null,
                "A bunker built before the rebuild has no ward to fill");
        FakePlayer sleeper = new FakePlayer(h.getLevel(), new GameProfile(java.util.UUID.randomUUID(), "ward-sleeper"));
        try {
            h.assertTrue(com.jayptucker.heroesevolved.story.bunker.HelixWard.dose(sleeper), "The dose gives a power");
            var power = com.jayptucker.heroesevolved.ability.service.PlayerAbilityService.getData(sleeper).assignedPower();
            h.assertTrue(power.isPresent() && !power.get().getValue().isUnlocked(), "...that sleeps until danger wakes it: " + power);
            h.assertTrue(!com.jayptucker.heroesevolved.story.bunker.HelixWard.dose(sleeper), "Nobody is dosed twice");
        } finally {
            com.jayptucker.heroesevolved.PowerOwnershipTestSupport.release(sleeper);
        }
        h.succeed();
    }

    /** A sleeper's name goes on the nameplate over their pod: revival in progress, then revived. */
    @GameTest(template = "rewind_empty", batch = "bunker.signs")
    public static void aSleepersNameGoesOverTheirPod(GameTestHelper h) {
        var server = h.getLevel().getServer();
        BunkerData data = BunkerService.ensureBuilt(server);
        var overworld = server.overworld();
        BlockPos plate = chamberCell(data, "sign:pod05");
        java.util.function.Supplier<String> words = () -> overworld.getBlockEntity(plate) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign
                ? java.util.stream.IntStream.range(0, 4).mapToObj(i -> sign.getFrontText().getMessage(i, false).getString())
                        .collect(java.util.stream.Collectors.joining("|")) : overworld.getBlockState(plate).toString();
        BunkerBuilder.labelPod(overworld, data, 4, "Tester", false);
        h.assertTrue(words.get().equals("SUBJECT 05|Tester|REVIVAL|IN PROGRESS"), "While Tester wakes: " + words.get());
        BunkerBuilder.labelPod(overworld, data, 4, "Tester", true);
        h.assertTrue(words.get().equals("SUBJECT 05|Tester|REVIVED|"), "Once Tester is out: " + words.get());
        h.succeed();
    }

    /** Every shipped blueprint parses against the real block registry, and the bunker's markers are all there. */
    @GameTest(template = "rewind_empty", batch = "blueprints")
    public static void blueprintsLoadWithTheirMarkers(GameTestHelper h) {
        var legacy = com.jayptucker.heroesevolved.story.bunker.BunkerLayout.LEGACY;
        var stasis = com.jayptucker.heroesevolved.story.bunker.BunkerLayout.STASIS;
        for (var layout : java.util.List.of(legacy, stasis))
            for (var room : layout.all()) {
                var blueprint = com.jayptucker.heroesevolved.structure.Blueprint.load(room.blueprint());
                h.assertTrue(blueprint.width() > 0 && blueprint.height() > 0 && blueprint.depth() > 0, "Empty blueprint " + room.blueprint());
            }
        // The original pods stay where older worlds' sleepers were assigned them.
        var chamber = com.jayptucker.heroesevolved.structure.Blueprint.load("bunker_chamber");
        for (int i = 0; i < BunkerBuilder.PODS; i++)
            h.assertTrue(chamber.markerOffsets("pod").contains(legacy.podOffset(i).offset(0, 0, -18)), "Legacy pod " + i + " moved");
        // Sublevel 4: every pod where BunkerLayout says (saved assignments and the Last Day's pod numbers depend on it).
        var pods = stasis.marker("pod");
        h.assertTrue(pods.size() == stasis.pods(), "Fifteen pods: fourteen in the hall and the ward's, found " + pods.size());
        for (int i = 0; i < stasis.pods(); i++)
            h.assertTrue(pods.contains(stasis.podOffset(i)), "Pod " + i + " is not at " + stasis.podOffset(i) + "; pods are " + pods);
        for (int i = 0; i < stasis.pods(); i++) h.assertTrue(stasis.podSign(i) != null, "Pod " + i + " has its nameplate");
        // Every room as it was has the same marked cells as the room now (the past is built from the _past twins).
        for (var room : stasis.all()) {
            var now = com.jayptucker.heroesevolved.structure.Blueprint.load(room.blueprint());
            if (!com.jayptucker.heroesevolved.structure.Blueprint.exists(room.blueprint() + "_past")) {
                h.assertTrue(room.blueprint().equals("stasis_cave"), "Only the cave has no past: " + room.blueprint());
                continue;
            }
            var then = com.jayptucker.heroesevolved.structure.Blueprint.load(room.blueprint() + "_past");
            h.assertTrue(then.width() == now.width() && then.height() == now.height() && then.depth() == now.depth(),
                    room.blueprint() + " and its past are the same size");
            for (String marker : java.util.List.of("pod", "beacon", "log:orientation", "log:lockdown", "log:helixite", "log:weir", "signal:receiver"))
                h.assertTrue(now.markerOffsets(marker).equals(then.markerOffsets(marker)), room.blueprint() + " keeps its " + marker + " in the past");
        }
        h.succeed();
    }

    /** The story ruins register, their templates load, and the chests really hold the notes. */
    @GameTest(template = "rewind_empty", batch = "ruins")
    public static void storyRuinsGenerateWithTheirNotes(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var structures = server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        for (String name : new String[] {"burnt_house", "collapsed_house", "bennet_house", "isaac_studio"}) {
            h.assertTrue(structures.containsKey(ResourceLocation.fromNamespaceAndPath("heroesevolved", "ruins_" + name)), "Missing structure ruins_" + name);
            var template = server.getStructureManager().get(ResourceLocation.fromNamespaceAndPath("heroesevolved", "ruins/" + name));
            h.assertTrue(template.isPresent() && template.get().getSize().getX() > 0, "Missing template ruins/" + name);
        }
        var table = server.reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath("heroesevolved", "chests/bennet_house")));
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(h.getLevel())
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, Vec3.atCenterOf(h.absolutePos(BlockPos.ZERO)))
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        var items = table.getRandomItems(params);
        var note = items.stream().filter(i -> i.is(net.minecraft.world.item.Items.WRITTEN_BOOK)).findFirst().orElse(null);
        h.assertTrue(note != null, "Claire's chest must hold her note: " + items);
        var content = note.get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT);
        h.assertTrue(content != null && content.title().raw().equals("Dad") && content.pages().size() == 4, "Claire's note must be addressed to her dad");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "invisibility")
    public static void fadeHidesFromMobsAndDamageRevealsIt(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.INVISIBILITY_ID, 5);
        var zombie = EntityType.ZOMBIE.create(h.getLevel());
        zombie.setNoAi(true); zombie.moveTo(p.getX(), p.getY(), p.getZ() + 6, 0, 0);
        h.getLevel().addFreshEntity(zombie);
        try {
            h.assertTrue(InvisibilityService.toggleFade(p) && InvisibilityService.isUnseen(p), "Fade makes you unseen");
            zombie.setTarget(p);
            h.assertTrue(zombie.getTarget() == null, "A mob six blocks away cannot pick out an unseen player");
            p.hurt(p.damageSources().generic(), 1);
            h.assertTrue(!InvisibilityService.isUnseen(p) && InvisibilityService.isFaded(p), "Damage flickers you visible without ending the fade");
            h.assertTrue(InvisibilityService.toggleFade(p) && !InvisibilityService.isFaded(p), "Pressing again reappears");
            h.succeed();
        } finally { InvisibilityService.forget(p); zombie.discard(); }
    }

    @GameTest(template = "rewind_empty", batch = "mimicry")
    public static void mimicRemembersAPowerAndEchoesIt(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.MIMICRY_ID, 3);
        LivingEntity victim = target(h, p, 5);
        try {
            h.assertTrue(MimicryService.remember(p, ModAbilities.PYROKINESIS_ID, 3), "A power can be remembered");
            h.assertTrue(!MimicryService.remember(p, ModAbilities.MIMICRY_ID, 3), "Mimicry cannot copy itself");
            var echo = AbilityRegistry.ABILITIES.get(ModAbilities.MIMICRY_ID).actions().get(AbilitySlot.PRIMARY);
            var context = new AbilityUseContext(p, ModAbilities.MIMICRY_ID, 3, false);
            float health = victim.getHealth();
            h.assertTrue(echo.canUse(context) && echo.activate(context) == AbilityActivationResult.SUCCESS, "Echo I charges the borrowed Cinder Spear");
            echo.release(context);
            h.assertTrue(victim.getHealth() < health, "The borrowed fire actually burns");
            h.assertTrue(MimicryService.memory(p).powers().size() == 1, "Memories are kept");
            h.succeed();
        } finally { victim.discard(); MimicryService.forget(p); com.jayptucker.heroesevolved.pyro.PyroService.forget(p); }
    }

    /** Every technique in every tree can be cast without error, with an enemy and a friend nearby. */
    @GameTest(template = "rewind_empty", batch = "techniques", timeoutTicks = 400)
    public static void everyTechniqueActivatesSafely(GameTestHelper h) {
        int tried = 0;
        for (var power : AbilityRegistry.ABILITIES.keySet()) {
            // Physical kits use dedicated target/state/resource fixtures, including real learned actions.
            if (power.equals(ModAbilities.FLIGHT_ID) || power.equals(ModAbilities.MATTER_ASSEMBLY_ID)) continue;
            for (var node : SkillCatalog.nodes(power.getPath())) {
                // Signature abilities run for seconds over a wide area; SignatureAbilityGameTests covers them with cleanup.
                if (!node.active() || node.mechanic().isEmpty() || node.signature()) continue;
                ServerPlayer p = player(h, power, 5);
                LivingEntity victim = target(h, p, 5);
                try {
                    ExpansionSkills.activate(new AbilityUseContext(p, power, 5, false), node.mechanic());
                    tried++;
                } catch (RuntimeException error) {
                    throw new AssertionError("Technique " + node.id() + " failed: " + error, error);
                } finally {
                    victim.discard();
                    ExpansionSkills.forget(p);
                    InvisibilityService.forget(p);
                    DensityService.forget(p);
                    com.jayptucker.heroesevolved.pyro.PyroService.forget(p);
                }
            }
        }
        h.assertTrue(tried == 42, "Expected 42 techniques across the other fifteen powers, tried " + tried);
        h.succeed();
    }
}
