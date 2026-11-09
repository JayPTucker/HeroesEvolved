package com.jayptucker.heroesevolved.technopathy;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.mimicry.MimicryService;
import com.jayptucker.heroesevolved.progression.EvolutionTrials;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Behavioral integration tests: real redstone, the ability pipeline, live claim events and Echo sessions. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class TechnopathyGameTests {
    private static ServerPlayer player(GameTestHelper h, ResourceLocation power, int tier) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "technopathy-test"));
        try {
            Connection connection = p.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 3, 2))));
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        PlayerProgressionService.setLevel(p, tier); PlayerEnergyService.restore(p, 999);
        return p;
    }
    private static ServerPlayer player(GameTestHelper h) { return player(h, ModAbilities.TECHNOPATHY_ID, 5); }
    private static AbilityUseContext context(ServerPlayer p) { return new AbilityUseContext(p, ModAbilities.TECHNOPATHY_ID, 5, false); }
    private static void aim(ServerPlayer p, BlockPos pos) {
        var shape = p.level().getBlockState(pos).getShape(p.level(), pos);
        Vec3 target = shape.isEmpty() ? Vec3.atCenterOf(pos) : shape.bounds().getCenter().add(Vec3.atLowerCornerOf(pos));
        Vec3 direction = target.subtract(p.getEyePosition());
        p.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        p.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z))));
    }
    private static BlockPos circuit(GameTestHelper h, ServerPlayer p, boolean lever) {
        BlockPos root = h.absolutePos(new BlockPos(2, 2, 7));
        for (int x = 0; x < 6; x++) h.getLevel().setBlock(root.offset(x, 0, 0), Blocks.COPPER_BLOCK.defaultBlockState(), 3);
        BlockPos selected = lever ? root.above() : root;
        if (lever) h.getLevel().setBlock(selected, Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR), 3);
        aim(p, selected); return selected;
    }
    private static void learn(ServerPlayer p, String... ids) {
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.TECHNOPATHY_ID.toString(),
                new SkillTreeData.Build(20, Set.of(ids), Map.of(), 6)));
    }
    private static Mob enemy(GameTestHelper h, Vec3 pos) {
        Mob mob = EntityType.PILLAGER.create(h.getLevel()); mob.setNoAi(true); mob.setNoGravity(true); mob.setPos(pos);
        h.getLevel().addFreshEntity(mob); return mob;
    }
    private static Arrow arrow(GameTestHelper h, LivingEntity owner, Vec3 position, Vec3 velocity) {
        Arrow arrow = new Arrow(EntityType.ARROW, h.getLevel()); arrow.setOwner(owner); arrow.setPos(position);
        arrow.setNoGravity(true); arrow.setDeltaMovement(velocity); h.getLevel().addFreshEntity(arrow); return arrow;
    }
    private static void cleanup(ServerPlayer p, Entity... entities) {
        PowerRules.rewinding(p, false); TechnopathyService.forget(p); MimicryService.forget(p);
        for (Entity entity : entities) if (entity != null) entity.discard();
    }
    private static void handshake(GameTestHelper h, ServerPlayer p) {
        h.assertTrue(TechnopathyService.core(context(p), "handshake"), "A visible real conductor must enrol");
    }

    @GameTest(template = "rewind_empty", batch = "technopathy.pipeline")
    public static void actualActivationRejectsMissesLowEnergyAndCooldownWithoutMutating(GameTestHelper h) {
        ServerPlayer p = player(h);
        try {
            p.setXRot(-90); int before = PlayerEnergyService.getEnergy(p);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("heroesevolved", "technopathy_handshake");
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.ACTIVATION_REJECTED,
                    "A missed Handshake must be rejected by the real ability pipeline");
            h.assertTrue(PlayerEnergyService.getEnergy(p) == before && !CooldownService.isOnCooldown(p, id) && TechnopathyService.nodeCount(p) == 0,
                    "A missed target cannot charge energy, begin cooldown or enrol a network");
            circuit(h, p, true); PlayerEnergyService.consumeUpTo(p, 999);
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.INSUFFICIENT_ENERGY
                            && TechnopathyService.nodeCount(p) == 0, "An exhausted player cannot create a free network");
            PlayerEnergyService.restore(p, 999); before = PlayerEnergyService.getEnergy(p);
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS
                            && PlayerEnergyService.getEnergy(p) < before && TechnopathyService.nodeCount(p) > 0,
                    "The registered action must spend energy only when its real network is enrolled");
            int after = PlayerEnergyService.getEnergy(p), nodes = TechnopathyService.nodeCount(p);
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.ON_COOLDOWN
                            && after == PlayerEnergyService.getEnergy(p) && TechnopathyService.nodeCount(p) == nodes,
                    "Cooldown rejection cannot charge energy or replace the live network");
            h.succeed();
        } finally { cleanup(p); }
    }

    @GameTest(template = "rewind_empty", batch = "technopathy.machine")
    public static void remoteHandChangesARealLeverAndObeysBothProtectionHooks(GameTestHelper h) {
        ServerPlayer p = player(h); BlockPos lever = circuit(h, p, true); handshake(h, p);
        Consumer<PlayerInteractEvent.RightClickBlock> useGuard = e -> { if (e.getEntity() == p && e.getPos().equals(lever)) e.setCanceled(true); };
        Consumer<BlockEvent.BreakEvent> editGuard = e -> { if (e.getPlayer() == p && e.getPos().equals(lever)) e.setCanceled(true); };
        try {
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, PlayerInteractEvent.RightClickBlock.class, useGuard);
            h.assertTrue(!TechnopathyService.operate(p, lever, true) && !h.getLevel().getBlockState(lever).getValue(BlockStateProperties.POWERED),
                    "A right-click claim cancellation must prevent a remote operation");
            NeoForge.EVENT_BUS.unregister(useGuard);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.BreakEvent.class, editGuard);
            h.assertTrue(!TechnopathyService.operate(p, lever, true), "An edit claim cancellation must prevent a remote operation");
            NeoForge.EVENT_BUS.unregister(editGuard);
            h.assertTrue(TechnopathyService.core(context(p), "remote_hand")
                            && h.getLevel().getBlockState(lever).getValue(BlockStateProperties.POWERED)
                            && h.getLevel().hasNeighborSignal(lever.below()),
                    "A permitted Remote Hand must operate the actual lever and its real redstone output");
            p.getAbilities().mayBuild = false;
            h.assertTrue(!TechnopathyService.operate(p, lever, false), "Adventure/no-build players cannot change claimed machines");
            h.succeed();
        } finally { p.getAbilities().mayBuild = true; NeoForge.EVENT_BUS.unregister(useGuard); NeoForge.EVENT_BUS.unregister(editGuard); cleanup(p); }
    }

    @GameTest(template = "rewind_empty", batch = "technopathy.door")
    public static void doorsRequirePermissionOnBothHalves(GameTestHelper h) {
        ServerPlayer p = player(h); BlockPos lower = circuit(h, p, false).above();
        h.getLevel().setBlock(lower, Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 2);
        h.getLevel().setBlock(lower.above(), Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
        aim(p, lower); handshake(h, p);
        Consumer<BlockEvent.BreakEvent> guard = e -> { if (e.getPlayer() == p && e.getPos().equals(lower.above())) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.BreakEvent.class, guard);
        try {
            h.assertTrue(!TechnopathyService.operate(p, lower, true)
                            && !h.getLevel().getBlockState(lower).getValue(DoorBlock.OPEN)
                            && !h.getLevel().getBlockState(lower.above()).getValue(DoorBlock.OPEN),
                    "Protecting only the upper half still protects the entire real door");
            NeoForge.EVENT_BUS.unregister(guard);
            h.assertTrue(TechnopathyService.operate(p, lower, true)
                            && h.getLevel().getBlockState(lower).getValue(DoorBlock.OPEN)
                            && h.getLevel().getBlockState(lower.above()).getValue(DoorBlock.OPEN),
                    "A legitimate command updates both halves through vanilla door behavior");
            h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(guard); cleanup(p); }
    }

    @GameTest(template = "rewind_empty", batch = "technopathy.graph", timeoutTicks = 50)
    public static void graphIsBoundedAndRemovingRootCancelsItsFields(GameTestHelper h) {
        ServerPlayer p = player(h); BlockPos root = circuit(h, p, false);
        for (int x = 0; x < 6; x++) for (int z = 0; z < 6; z++) h.getLevel().setBlock(root.offset(x, 0, z), Blocks.COPPER_BLOCK.defaultBlockState(), 3);
        handshake(h, p);
        h.assertTrue(TechnopathyService.nodeCount(p) == TechnopathyService.MAX_NODES, "Dense conductor graphs must stop at the node budget");
        h.assertTrue(TechnopathyService.core(context(p), "intercept") && TechnopathyService.activeFields(p) == 1, "The enrolled relay must own its field");
        h.getLevel().setBlock(root, Blocks.AIR.defaultBlockState(), 3);
        h.runAfterDelay(3, () -> {
            try {
                h.assertTrue(TechnopathyService.nodeCount(p) == 0 && TechnopathyService.activeFields(p) == 0,
                        "Destroying the physical root must remove the whole circuit and its defensive field"); h.succeed();
            } finally { cleanup(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "technopathy.air_gap")
    public static void airGapRequiresALearnedNodeAndNeverConductsThroughWalls(GameTestHelper h) {
        ServerPlayer p = player(h); BlockPos root = h.absolutePos(new BlockPos(3, 2, 6));
        h.getLevel().setBlock(root, Blocks.COPPER_BLOCK.defaultBlockState(), 3);
        h.getLevel().setBlock(root.east(2), Blocks.COPPER_BLOCK.defaultBlockState(), 3); aim(p, root);
        try {
            h.assertTrue(TechnopathyService.network(p, root).size() == 1, "Ordinary Handshake cannot jump disconnected conductors");
            learn(p, "technopathy_air_gap");
            h.assertTrue(TechnopathyService.network(p, root).size() == 2, "The purchased Air Gap node changes actual network traversal");
            h.getLevel().setBlock(root.east(), Blocks.STONE.defaultBlockState(), 3);
            h.assertTrue(TechnopathyService.network(p, root).size() == 1, "An opaque nonconducting wall cannot become a wireless cable");
            h.succeed();
        } finally { cleanup(p); }
    }

    @GameTest(template = "rewind_empty", batch = "technopathy.learned", timeoutTicks = 140)
    public static void learnedDelayedInstructionRunsAndSafelyUnwindsThroughRealAction(GameTestHelper h) {
        ServerPlayer p = player(h); BlockPos lever = circuit(h, p, true); handshake(h, p);
        learn(p, "technopathy_delayed_instruction", "technopathy_echo_instruction");
        int before = PlayerEnergyService.getEnergy(p);
        h.assertTrue(AbilityUseService.activateAction(p, "technopathy_delayed_instruction", false) == AbilityUseResult.SUCCESS
                        && PlayerEnergyService.getEnergy(p) < before
                        && !h.getLevel().getBlockState(lever).getValue(BlockStateProperties.POWERED),
                "A learned action must queue a delayed real instruction, spend stamina and avoid toggling immediately");
        h.runAfterDelay(65, () -> h.assertTrue(h.getLevel().getBlockState(lever).getValue(BlockStateProperties.POWERED),
                "The scheduled command must turn on the actual lever after three seconds"));
        h.runAfterDelay(108, () -> {
            try {
                h.assertTrue(!h.getLevel().getBlockState(lever).getValue(BlockStateProperties.POWERED)
                                && TechnopathyService.activeFields(p) == 0,
                        "Echo Instruction must unwind its own command and remove its timer"); h.succeed();
            } finally { cleanup(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "technopathy.cleanup")
    public static void cleanupRestoresOnlyExpectedControllerStateAndNeverResurrectsBlocks(GameTestHelper h) {
        ServerPlayer p = player(h); BlockPos lever = circuit(h, p, true); handshake(h, p);
        learn(p, "technopathy_kill_switch");
        try {
            h.assertTrue(TechnopathyService.operate(p, lever, true), "The test controller starts on");
            h.assertTrue(TechnopathyService.technique(context(p), "tech_kill_switch")
                            && !h.getLevel().getBlockState(lever).getValue(BlockStateProperties.POWERED), "Kill Switch must turn off the real control");
            TechnopathyService.logout(new PlayerEvent.PlayerLoggedOutEvent(p));
            h.assertTrue(h.getLevel().getBlockState(lever).getValue(BlockStateProperties.POWERED)
                            && TechnopathyService.nodeCount(p) == 0, "Logout must unwind the temporary command and discard its session");
            aim(p, lever); handshake(h, p);
            h.assertTrue(TechnopathyService.technique(context(p), "tech_kill_switch"), "A later legitimate session can command the same real device");
            h.getLevel().setBlock(lever, Blocks.STONE.defaultBlockState(), 3); TechnopathyService.forget(p);
            h.assertTrue(h.getLevel().getBlockState(lever).is(Blocks.STONE), "Cleanup cannot resurrect a removed/replaced lever from a saved snapshot");
            h.succeed();
        } finally { cleanup(p); }
    }

    @GameTest(template = "rewind_empty", batch = "technopathy.sever_cleanup", timeoutTicks = 50)
    public static void severingACableUnwindsDetachedTemporaryControllersBeforePruningThem(GameTestHelper h) {
        ServerPlayer p = player(h); BlockPos root = circuit(h, p, false), lever = root.east(5).above();
        h.getLevel().setBlock(lever, Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR), 3);
        aim(p, root); handshake(h, p); learn(p, "technopathy_kill_switch");
        h.assertTrue(TechnopathyService.operate(p, lever, true), "The distant controller must belong to this physical cable network");
        aim(p, lever);
        h.assertTrue(TechnopathyService.technique(context(p), "tech_kill_switch")
                        && !h.getLevel().getBlockState(lever).getValue(BlockStateProperties.POWERED),
                "The temporary command must actually switch off the distant controller");
        h.getLevel().setBlock(root.east(2), Blocks.AIR.defaultBlockState(), 3);
        h.runAfterDelay(23, () -> {
            try {
                h.assertTrue(h.getLevel().getBlockState(lever).getValue(BlockStateProperties.POWERED)
                                && TechnopathyService.nodeCount(p) == 2 && TechnopathyService.activeFields(p) == 0,
                        "A severed cable must unwind the detached temporary command before discarding the relay address"); h.succeed();
            } finally { cleanup(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "technopathy.projectile", timeoutTicks = 45)
    public static void interceptDivertsHostileArrowsAndCountsOnlyRealInterceptions(GameTestHelper h) {
        ServerPlayer p = player(h); BlockPos relay = circuit(h, p, false); handshake(h, p);
        Vec3 center = Vec3.atCenterOf(relay).add(0, 1, 0);
        Mob shooter = enemy(h, center.add(0, -.5, 6));
        // Above the moving-projectile cutoff even after vanilla drag and either two-tick service parity.
        Arrow hostile = arrow(h, shooter, center.add(0, 0, 3), new Vec3(0, 0, -.2));
        Arrow friendly = arrow(h, p, center.add(1, 0, 3), new Vec3(0, 0, -.2));
        int before = EvolutionTrials.progress(p, "technopathy");
        h.assertTrue(TechnopathyService.core(context(p), "intercept"), "Intercept must start at the aimed real relay");
        h.runAfterDelay(5, () -> {
            try {
                h.assertTrue(!hostile.isRemoved() && hostile.getDeltaMovement().z > 0 && hostile.getOwner() == shooter,
                        "A normal hostile arrow is physically diverted, retained and still attributed to its original shooter");
                h.assertTrue(friendly.getDeltaMovement().z < 0 && friendly.getOwner() == p, "Allied/own arrows must remain untouched");
                h.assertTrue(EvolutionTrials.progress(p, "technopathy") == before + 1,
                        "Only one actual hostile interception advances the trial, regardless of repeated scans"); h.succeed();
            } finally { cleanup(p, shooter, hostile, friendly); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "technopathy.cover", timeoutTicks = 45)
    public static void interceptCannotReachAnArrowThroughSolidCover(GameTestHelper h) {
        ServerPlayer p = player(h); BlockPos relay = circuit(h, p, false); handshake(h, p);
        Vec3 center = Vec3.atCenterOf(relay).add(0, 1, 0);
        Mob shooter = enemy(h, center.add(0, -.5, 6));
        Arrow hostile = arrow(h, shooter, center.add(0, 0, 3), new Vec3(0, 0, -.12));
        BlockPos wall = relay.offset(0, 1, 1);
        for (int y = 0; y < 3; y++) h.getLevel().setBlock(wall.above(y), Blocks.STONE.defaultBlockState(), 3);
        h.assertTrue(TechnopathyService.core(context(p), "intercept"), "The relay itself remains visible to its operator");
        h.runAfterDelay(5, () -> {
            try {
                h.assertTrue(hostile.getDeltaMovement().z < 0 && EvolutionTrials.progress(p, "technopathy") == 0,
                        "Solid cover must block interception rather than silently hacking projectiles through a wall"); h.succeed();
            } finally {
                for (int y = 0; y < 3; y++) h.getLevel().setBlock(wall.above(y), Blocks.AIR.defaultBlockState(), 3);
                cleanup(p, shooter, hostile);
            }
        });
    }

    @GameTest(template = "rewind_empty", batch = "technopathy.tripwire", timeoutTicks = 45)
    public static void tripwireCatchesAHostileBodyButDoesNotAttackEvacuatingVillagers(GameTestHelper h) {
        ServerPlayer p = player(h); BlockPos relay = circuit(h, p, false); handshake(h, p);
        Mob victim = enemy(h, Vec3.atBottomCenterOf(relay.offset(3, 1, 0)));
        var villager = EntityType.VILLAGER.create(h.getLevel()); villager.setNoAi(true); villager.setNoGravity(true);
        villager.setPos(Vec3.atBottomCenterOf(relay.offset(2, 1, 0))); h.getLevel().addFreshEntity(villager);
        float hostileHealth = victim.getHealth(), civilianHealth = villager.getHealth();
        h.assertTrue(TechnopathyService.core(context(p), "tripwire"), "A prepared conductor lane must support a real tripwire");
        h.runAfterDelay(5, () -> {
            try {
                h.assertTrue(victim.getHealth() < hostileHealth && victim.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),
                        "The hostile body's crossing must produce actual damage and a short stumble");
                h.assertTrue(villager.getHealth() == civilianHealth, "An unprovoked civilian using the evacuation corridor must not trigger the trap"); h.succeed();
            } finally { cleanup(p, victim, villager); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "technopathy.optional", timeoutTicks = 90)
    public static void optionalInstructionsAndKineticMachinesHaveDistinctRealConsequences(GameTestHelper h) {
        ServerPlayer p = player(h); BlockPos lever = circuit(h, p, true); handshake(h, p);
        learn(p, "technopathy_batch_instruction", "technopathy_sentry_seal", "technopathy_winchline", "technopathy_domino_engine");
        h.assertTrue(TechnopathyService.technique(context(p), "tech_batch_instruction")
                        && h.getLevel().getBlockState(lever).getValue(BlockStateProperties.POWERED),
                "Batch Instruction operates the actual prepared switch bank");
        Mob victim = enemy(h, Vec3.atBottomCenterOf(lever.offset(5, 0, 0))); Mob[] engineVictim = new Mob[1];
        float before = victim.getHealth();
        h.assertTrue(TechnopathyService.technique(context(p), "tech_winchline"), "A visible hostile body can be attached to the physical winch");
        h.runAfterDelay(4, () -> {
            h.assertTrue(victim.getDeltaMovement().x < 0 && victim.getHealth() == before,
                    "Winchline physically pulls the body towards its relay without becoming a generic damage beam");
            victim.setPos(Vec3.atBottomCenterOf(lever.offset(1, 0, 0))); victim.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(TechnopathyService.technique(context(p), "tech_sentry_seal"), "A linked proximity seal can be prepared independently");
        });
        h.runAfterDelay(9, () -> {
            h.assertTrue(victim.getHealth() < before, "The Sentry Seal actually recoils the hostile body when it enters its prepared zone");
            engineVictim[0] = enemy(h, Vec3.atBottomCenterOf(lever.offset(4, 0, 0)));
            engineVictim[0].getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
            engineVictim[0].setHealth(100);
            h.assertTrue(TechnopathyService.technique(context(p), "tech_domino_engine"), "Domino Engine must accept the physical circuit route");
        });
        h.runAfterDelay(50, () -> {
            try {
                h.assertTrue(engineVictim[0] != null && engineVictim[0].getHealth() < 100 && engineVictim[0].getHealth() >= 87,
                        "A twelve-step physical relay sequence must hit its target once, never once per overlapping node"); h.succeed();
            } finally { cleanup(p, victim, engineVictim[0]); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "technopathy.swept_sensor", timeoutTicks = 45)
    public static void motionSensorChecksARecentlyCrossedBodyBeyondTheThinLine(GameTestHelper h) {
        ServerPlayer p = player(h); BlockPos relay = circuit(h, p, false); handshake(h, p);
        // Keep the supplied post-movement frame stable so the test isolates the sensor's swept query.
        Pillager victim = new Pillager(EntityType.PILLAGER, h.getLevel()) { @Override public void tick() {} };
        victim.setPos(Vec3.atBottomCenterOf(relay.offset(3, 1, 3))); victim.setDeltaMovement(0, 0, 3);
        victim.setNoAi(true); victim.setNoGravity(true); h.getLevel().addFreshEntity(victim);
        float before = victim.getHealth();
        h.assertTrue(TechnopathyService.core(context(p), "tripwire"), "The real conductor lane can be armed");
        h.runAfterDelay(3, () -> {
            h.assertTrue(victim.getHealth() == before, "A body three blocks past the line is outside an ordinary proximity tripwire");
            learn(p, "technopathy_motion_sensor");
        });
        h.runAfterDelay(7, () -> {
            try {
                h.assertTrue(victim.getHealth() < before,
                        "The learned sensor must examine the swept path, including bodies already outside the thin-line query"); h.succeed();
            } finally { cleanup(p, victim); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "technopathy.mimicry", timeoutTicks = 45)
    public static void rememberingDoesNotAuthorizeNativeCallsButARealEchoSessionDoes(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.MIMICRY_ID, 5); circuit(h, p, true);
        AbilityUseContext echoContext = new AbilityUseContext(p, ModAbilities.MIMICRY_ID, 5, false);
        try {
            h.assertTrue(MimicryService.remember(p, ModAbilities.TECHNOPATHY_ID, 5), "The power can be remembered without taking its unique ownership");
            h.assertTrue(!TechnopathyService.canUse(context(p)) && !TechnopathyService.core(context(p), "handshake")
                            && TechnopathyService.nodeCount(p) == 0, "Merely remembering it cannot authorize forged native power calls");
            AbilityAction echo = AbilityRegistry.ABILITIES.get(ModAbilities.MIMICRY_ID).actions().get(AbilitySlot.TERTIARY);
            h.assertTrue(echo.canUse(echoContext) && echo.activate(echoContext) == AbilityActivationResult.SUCCESS,
                    "A real borrowed Handshake must pass its narrowly scoped authorization gate");
            echo.release(echoContext);
            h.assertTrue(TechnopathyService.nodeCount(p) > 0 && MimicryService.isBorrowingTechnopathy(p),
                    "The real borrowed session must keep its physical graph after the invocation returns");
            PowerRules.rewinding(p, true);
        } catch (RuntimeException | Error failure) { cleanup(p); throw failure; }
        h.runAfterDelay(3, () -> {
            try {
                h.assertTrue(TechnopathyService.nodeCount(p) == 0 && !MimicryService.isBorrowingTechnopathy(p),
                        "A blocked/rewinding borrower must lose its sustained graph and native authorization"); h.succeed();
            } finally { cleanup(p); }
        });
    }
}
