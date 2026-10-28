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
import com.jayptucker.heroesevolved.phasing.PhasingService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
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
        // The secret: a trapdoor under the red rug in security, a ladder down, and Bennet's letter in Sublevel 4-B.
        h.assertTrue(overworld.getBlockState(data.origin.offset(-12, 0, 9)).getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock,
                "The trapdoor under the rug leads to Sublevel 4-B");
        boolean deepEnough = data.origin.getY() - 6 > overworld.getMinBuildHeight();   // shallow worlds have no room below
        if (deepEnough) {
            h.assertTrue(overworld.getBlockState(data.origin.offset(-12, -1, 9)).is(net.minecraft.world.level.block.Blocks.LADDER), "A ladder goes down from the trapdoor");
            h.assertTrue(overworld.getBlockEntity(data.origin.offset(-7, -5, 7)) instanceof LecternBlockEntity letter && letter.hasBook(),
                    "Bennet's letter waits on his lectern");
        }
        var paintings = overworld.getEntitiesOfClass(net.minecraft.world.entity.decoration.Painting.class,
                new net.minecraft.world.phys.AABB(data.origin).inflate(20, 14, 45));
        h.assertTrue(paintings.stream().anyMatch(p -> p.getVariant().is(ResourceLocation.fromNamespaceAndPath("heroesevolved", "sleeper"))),
                "Isaac's painting of the sleeper hangs in the stasis hall gallery");
        h.assertTrue(!deepEnough || paintings.stream().anyMatch(p -> p.getVariant().is(ResourceLocation.fromNamespaceAndPath("heroesevolved", "bennets"))),
                "The family photo hangs in Bennet's office");
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

    @GameTest(template = "rewind_empty", batch = "phasing")
    public static void phaseStepCrossesWallsButNeverBedrock(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.PHASING_ID, 5);
        for (int z = 4; z <= 5; z++) for (int y = 2; y <= 4; y++) h.getLevel().setBlock(h.absolutePos(new BlockPos(3, y, z)), Blocks.STONE.defaultBlockState(), 3);
        try {
            double before = p.getZ();
            h.assertTrue(PhasingService.phaseStep(p, 5, false), "Phase Step must find the open space past the wall");
            h.assertTrue(p.getZ() > h.absolutePos(new BlockPos(3, 2, 5)).getZ(), "The phaser lands beyond the wall, not inside it: " + before + " -> " + p.getZ());
            ServerPlayer blocked = player(h, ModAbilities.PHASING_ID, 5);
            for (int y = 2; y <= 4; y++) h.getLevel().setBlock(h.absolutePos(new BlockPos(3, y, 4)), Blocks.BEDROCK.defaultBlockState(), 3);
            h.assertTrue(!PhasingService.phaseStep(blocked, 5, false), "Bedrock cannot be phased through");
            h.assertTrue(PhasingService.toggleIntangible(blocked), "Intangible toggles on");
            var zombie = EntityType.ZOMBIE.create(h.getLevel());
            float health = blocked.getHealth();
            blocked.hurt(blocked.damageSources().mobAttack(zombie), 6);
            h.assertTrue(blocked.getHealth() == health, "Blows pass through an intangible player");
            PhasingService.forget(blocked);
            h.succeed();
        } finally { PhasingService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "mimicry")
    public static void mimicRemembersAPowerAndEchoesIt(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.MIMICRY_ID, 3);
        LivingEntity victim = target(h, p, 5);
        try {
            h.assertTrue(MimicryService.remember(p, ModAbilities.PYROKINESIS_ID, 3), "A power can be remembered");
            h.assertTrue(!MimicryService.remember(p, ModAbilities.MIMICRY_ID, 3), "Mimicry cannot copy itself");
            var echo = AbilityRegistry.ABILITIES.get(ModAbilities.MIMICRY_ID).actions().get(AbilitySlot.TERTIARY);
            var context = new AbilityUseContext(p, ModAbilities.MIMICRY_ID, 3, false);
            float health = victim.getHealth();
            h.assertTrue(echo.canUse(context) && echo.activate(context) == AbilityActivationResult.SUCCESS, "Echo I performs the borrowed Flare");
            h.assertTrue(victim.getHealth() < health, "The borrowed fire actually burns");
            h.assertTrue(MimicryService.memory(p).powers().size() == 1, "Memories are kept");
            h.succeed();
        } finally { victim.discard(); com.jayptucker.heroesevolved.pyro.PyroService.forget(p); }
    }

    /** Every technique in every tree can be cast without error, with an enemy and a friend nearby. */
    @GameTest(template = "rewind_empty", batch = "techniques", timeoutTicks = 400)
    public static void everyTechniqueActivatesSafely(GameTestHelper h) {
        int tried = 0;
        for (var power : AbilityRegistry.ABILITIES.keySet()) {
            for (var node : SkillCatalog.nodes(power.getPath())) {
                if (!node.active() || node.mechanic().isEmpty()) continue;
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
                    PhasingService.forget(p);
                    com.jayptucker.heroesevolved.pyro.PyroService.forget(p);
                }
            }
        }
        h.assertTrue(tried == 48, "Expected 48 techniques (three per power), tried " + tried);
        h.succeed();
    }
}
