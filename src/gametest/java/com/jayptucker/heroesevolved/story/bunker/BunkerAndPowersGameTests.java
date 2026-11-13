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
        for (int i = 0; i < BunkerBuilder.PODS; i++) {
            var state = overworld.getBlockState(data.origin.offset(BunkerBuilder.podOffset(i)));
            h.assertTrue(state.is(ModBlocks.CRYOCHAMBER.get()), "Missing pod " + i);
        }
        h.assertTrue(overworld.getBlockEntity(data.origin.offset(0, 1, 26)) instanceof LecternBlockEntity lectern && lectern.hasBook(),
                "The orientation log waits on the chamber lectern");
        h.assertTrue(BunkerData.get(server).built && BunkerService.ensureBuilt(server).origin.equals(data.origin), "The bunker is built only once");
        BlockPos receiver = BunkerBuilder.receiverPos(data.origin);
        h.assertTrue(overworld.getBlockState(receiver).is(Blocks.WAXED_WEATHERED_CUT_COPPER), "The receiver stands on the records desk");
        h.assertTrue(BunkerBuilder.survivorPositions(data.origin).size() == 2
                        && BunkerBuilder.survivorPositions(data.origin).stream().allMatch(pos ->
                                overworld.getBlockState(pos).isAir() && overworld.getBlockState(pos.above()).isAir()),
                "Both recovery spots have room to stand");
        BlockPos grayCot = data.origin.offset(-6, 1, 14);
        h.assertTrue(overworld.getBlockState(grayCot).is(Blocks.GRAY_BED)
                        && overworld.getBlockState(grayCot).getValue(BlockStateProperties.BED_PART) == BedPart.FOOT
                        && overworld.getBlockState(grayCot.south()).is(Blocks.GRAY_BED)
                        && overworld.getBlockState(grayCot.south()).getValue(BlockStateProperties.BED_PART) == BedPart.HEAD,
                "The original cot has a connected foot and head");
        h.assertTrue(overworld.getBlockEntity(data.origin.offset(8, 1, 14)) instanceof ChestBlockEntity lab
                        && lab.getItem(6).is(ModItems.BLOOD_SAMPLE.get())
                        && lab.getItem(6).getOrDefault(ModItems.BLOOD_PURITY.get(),
                                new com.jayptucker.heroesevolved.formula.BloodPurity(false, 1, "")).donated(),
                "The lab offers a donated Formula sample");
        h.assertTrue(overworld.getBlockState(data.origin.offset(-12, 0, 9)).getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock,
                "The maintenance hatch is still in the security room");
        h.assertTrue(overworld.getBlockState(data.origin.offset(-12, 1, 9)).isAir(), "The hatch is marked and exposed");
        boolean deepEnough = data.origin.getY() - 6 > overworld.getMinBuildHeight();   // shallow worlds have no room below
        if (deepEnough) {
            h.assertTrue(overworld.getBlockState(data.origin.offset(-12, -1, 9)).is(Blocks.LADDER), "A ladder goes down from the hatch");
            h.assertTrue(overworld.getBlockState(data.origin.offset(-7, -5, 7)).is(Blocks.CRAFTING_TABLE), "The maintenance bay has a workbench");
        }
        h.assertTrue(explosions(overworld, data.origin).size() == 1 && explosions(overworld, data.origin).getFirst().survives(),
                "Isaac's explosion, Exhibit 1, hangs whole above the dead screen: " + explosions(overworld, data.origin));
        h.assertTrue(exhibitOne(overworld, data).equals("EXHIBIT 1"), "Its label reads EXHIBIT 1: " + exhibitOne(overworld, data));
        h.succeed();
    }

    private static java.util.List<net.minecraft.world.entity.decoration.Painting> explosions(net.minecraft.server.level.ServerLevel level, BlockPos origin) {
        return level.getEntitiesOfClass(net.minecraft.world.entity.decoration.Painting.class, new net.minecraft.world.phys.AABB(origin).inflate(20, 14, 45),
                painting -> painting.getVariant().is(ResourceLocation.fromNamespaceAndPath("heroesevolved", "explosion")));
    }

    private static BlockPos chamberCell(BunkerData data, String marker) {
        return data.origin.offset(BunkerBuilder.CHAMBER.at())
                .offset(com.jayptucker.heroesevolved.structure.Blueprint.load(BunkerBuilder.CHAMBER.blueprint()).markerOffsets(marker).getFirst());
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
        BunkerData data = BunkerService.ensureBuilt(server);
        var overworld = server.overworld();
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
        BlockPos receiver = BunkerBuilder.receiverPos(data.origin);
        overworld.setBlock(receiver, Blocks.BLACK_STAINED_GLASS_PANE.defaultBlockState(), 3);
        data.layout = 2;
        var min = new ChunkPos(data.origin.offset(-14, 0, 0));
        var max = new ChunkPos(data.origin.offset(15, 0, 40));
        var forced = new java.util.ArrayList<ChunkPos>();
        for (int x = min.x; x <= max.x; x++)
            for (int z = min.z; z <= max.z; z++)
                if (overworld.setChunkForced(x, z, true)) forced.add(new ChunkPos(x, z));
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
            h.assertTrue(overworld.getBlockState(receiver).is(Blocks.WAXED_WEATHERED_CUT_COPPER),
                    "The receiver replaced the old display pane");
            h.assertTrue(BunkerBuilder.upgrade(overworld, data) && explosions(overworld, data.origin).size() == 1, "A second upgrade adds nothing");
            overworld.setBlock(lectern.above(), Blocks.AIR.defaultBlockState(), 3);
            overworld.setBlock(alteredPanel, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
            forced.forEach(chunk -> overworld.setChunkForced(chunk.x, chunk.z, false));
        });
    }

    /** Every light the bunker's rooms have, as world positions with the state it has when the power is on. */
    private static java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> bunkerLights(net.minecraft.server.level.ServerLevel level, BunkerData data) {
        java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> lights = new java.util.LinkedHashMap<>();
        var rooms = new java.util.ArrayList<>(BunkerBuilder.ROOMS);
        // On a shallow world there is no room for the office.
        if (data.origin.getY() + BunkerBuilder.OFFICE.at().getY() > level.getMinBuildHeight()) rooms.add(BunkerBuilder.OFFICE);
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
        for (var room : BunkerBuilder.ROOMS)
            com.jayptucker.heroesevolved.structure.Blueprint.load(room.blueprint()).markerOffsets(BunkerBuilder.BEACON)
                    .forEach(cell -> beacons.add(data.origin.offset(room.at()).offset(cell)));
        h.assertTrue(beacons.size() == 3, "Three alarm beacons: " + beacons.size());
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
        // A bunker some earlier test already lit goes dark the way it does for each new sleeper.
        if (data.powered || BunkerPower.switching()) BunkerPower.cut(overworld, data);
        dark.run();
        h.assertTrue(BunkerPower.start(overworld, data) && !BunkerPower.start(overworld, data), "The breaker closes once");
        h.startSequence()
                .thenWaitUntil(lit)
                .thenExecute(() -> {
                    // The next sleeper wakes: the power fails again, every light at once, and comes back the same way.
                    BunkerPower.cut(overworld, data);
                    h.assertTrue(!data.powered, "Back on emergency power");
                    dark.run();
                    h.assertTrue(BunkerPower.start(overworld, data), "The breaker closes again");
                })
                .thenWaitUntil(lit)
                .thenSucceed();
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
        BunkerBuilder.labelPod(overworld, data.origin, 4, "Tester", false);
        h.assertTrue(words.get().equals("SUBJECT 05|Tester|REVIVAL|IN PROGRESS"), "While Tester wakes: " + words.get());
        BunkerBuilder.labelPod(overworld, data.origin, 4, "Tester", true);
        h.assertTrue(words.get().equals("SUBJECT 05|Tester|REVIVED|"), "Once Tester is out: " + words.get());
        h.succeed();
    }

    /** Every shipped blueprint parses against the real block registry, and the bunker's markers are all there. */
    @GameTest(template = "rewind_empty", batch = "blueprints")
    public static void blueprintsLoadWithTheirMarkers(GameTestHelper h) {
        for (String name : new String[] {"bunker_chamber", "bunker_records", "bunker_corridor", "bunker_lab", "bunker_cell",
                "bunker_generator", "bunker_security", "bunker_entrance", "bunker_office"}) {
            var blueprint = com.jayptucker.heroesevolved.structure.Blueprint.load(name);
            h.assertTrue(blueprint.width() > 0 && blueprint.height() > 0 && blueprint.depth() > 0, "Empty blueprint " + name);
        }
        var chamber = com.jayptucker.heroesevolved.structure.Blueprint.load("bunker_chamber");
        var pods = chamber.markerOffsets("pod");
        h.assertTrue(pods.size() == BunkerBuilder.PODS, "The chamber must hold " + BunkerBuilder.PODS + " pods, found " + pods.size());
        for (int i = 0; i < BunkerBuilder.PODS; i++) {
            BlockPos expected = BunkerBuilder.podOffset(i).offset(0, 0, -18);
            h.assertTrue(pods.contains(expected), "Pod " + i + " moved; saved pod assignments depend on " + expected);
        }
        h.assertTrue(chamber.markerOffsets("log:orientation").equals(java.util.List.of(new BlockPos(0, 1, 8))),
                "The orientation lectern must stay in the middle of the chamber");
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
            if (power.equals(ModAbilities.FLIGHT_ID) || power.equals(ModAbilities.TECHNOPATHY_ID)
                    || power.equals(ModAbilities.MATTER_ASSEMBLY_ID)) continue;
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
