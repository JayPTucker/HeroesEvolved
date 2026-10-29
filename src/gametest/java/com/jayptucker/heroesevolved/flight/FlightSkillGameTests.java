package com.jayptucker.heroesevolved.flight;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Learned Flight skills are distinct combat, support and traversal mechanics, including their upgrades. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class FlightSkillGameTests {
    private static final String RESCUE = "flight_0_1", SANCTUARY = "flight_sky_catch",
            ROLL = "flight_1_1", JETSTREAM = "flight_tailwind", LANCE = "flight_2_1", CLAP = "flight_thunderclap";
    private static final Set<String> ACTIVES = Set.of(RESCUE, SANCTUARY, ROLL, JETSTREAM, LANCE, CLAP);

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "flight-skills")) {
            // FakePlayer's connection deliberately ignores teleports; the rescue still needs a real position change.
            @Override public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
                absMoveTo(x, y, z, yaw, pitch);
            }
        };
        try {
            Connection connection = p.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 40, 3))));
        p.setYRot(0); p.setXRot(0); p.setOnGround(false);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.FLIGHT_ID).unlock(ModAbilities.FLIGHT_ID));
        PlayerProgressionService.setLevel(p, 5);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        return p;
    }

    private static void learn(ServerPlayer p, String... upgrades) {
        var ids = new HashSet<>(ACTIVES);
        ids.addAll(Set.of(upgrades));
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.FLIGHT_ID.toString(),
                new SkillTreeData.Build(5, ids, Map.of(), 6)));
    }

    private static void cleanup(ServerPlayer p, Entity... targets) {
        PowerRules.rewinding(p, false);
        FlightSkillService.forget(p);
        FlightSkillPassives.forget(p);
        DiveBombService.forget(p);
        FlightService.stopForPowerRemoval(p);
        FlightDynamicsService.forget(p);
        for (Entity target : targets) target.discard();
    }

    private static AbilityUseContext context(ServerPlayer p) { return new AbilityUseContext(p, ModAbilities.FLIGHT_ID, 5, false); }
    private static AbilityAction action(String id) { return SkillCatalog.find("flight", id).orElseThrow().action(); }
    private static void cast(GameTestHelper h, ServerPlayer p, String id) {
        h.assertTrue(action(id).canUse(context(p)), "The fixture must be able to use " + id);
        h.assertTrue(action(id).activate(context(p)) == AbilityActivationResult.SUCCESS, "The learned skill must activate: " + id);
    }
    private static void tick(ServerPlayer p, int ticks) { for (int i = 0; i < ticks; i++) FlightSkillService.tick(p); }

    private static LivingEntity target(GameTestHelper h, Vec3 at) {
        var target = EntityType.PILLAGER.create(h.getLevel());
        target.setNoAi(true); target.setNoGravity(true); target.setPersistenceRequired();
        target.moveTo(at.x, at.y, at.z, 0, 0);
        h.getLevel().addFreshEntity(target);
        return target;
    }

    private static PlayerTeam team(ServerPlayer p, LivingEntity... friends) {
        var scoreboard = p.getScoreboard();
        var team = scoreboard.addPlayerTeam("air-" + UUID.randomUUID().toString().substring(0, 8));
        scoreboard.addPlayerToTeam(p.getScoreboardName(), team);
        for (var friend : friends) scoreboard.addPlayerToTeam(friend.getScoreboardName(), team);
        return team;
    }

    private static Arrow arrow(GameTestHelper h, Entity owner, Vec3 at, Vec3 velocity) {
        Arrow arrow = new Arrow(EntityType.ARROW, h.getLevel());
        arrow.setOwner(owner); arrow.setPos(at); arrow.setDeltaMovement(velocity);
        h.getLevel().addFreshEntity(arrow);
        return arrow;
    }

    private static void aim(ServerPlayer p, Vec3 point) {
        Vec3 delta = point.subtract(p.getEyePosition());
        p.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        p.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance())));
    }

    /** A bounded patch with an obvious top surface, centered six blocks ahead. */
    private static Vec3 ground(GameTestHelper h, ServerPlayer p) {
        BlockPos center = p.blockPosition().below().offset(0, 0, 6);
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++)
            h.getLevel().setBlock(center.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 3);
        Vec3 top = Vec3.atBottomCenterOf(center.above());
        aim(p, top);
        return top;
    }

    private static void clearGround(GameTestHelper h, Vec3 top) {
        BlockPos center = BlockPos.containing(top).below();
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++)
            h.getLevel().setBlock(center.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3);
    }

    @GameTest(template = "rewind_empty", batch = "flight.skills_persistence")
    public static void allEighteenNodesCanBePurchasedByBranchAndKeepSavedActionIds(GameTestHelper h) {
        var nodes = SkillCatalog.nodes("flight");
        h.assertTrue(nodes.size() == 18 && nodes.stream().filter(SkillCatalog.Node::active)
                        .map(SkillCatalog.Node::id).collect(Collectors.toSet()).equals(ACTIVES),
                "The redesign must retain all eighteen nodes and the six existing loadout IDs");
        for (int branch = 0; branch < 3; branch++) {
            ServerPlayer p = player(h);
            try {
                int slot = 0;
                for (var node : nodes) {
                    if (node.branch() != branch) continue;
                    h.assertTrue(SkillTreeService.purchase(p, node.id()), "A complete single-branch build must remain purchasable: " + node.id());
                    if (node.active()) h.assertTrue(SkillTreeService.equip(p, AbilitySlot.values()[slot++], node.id()), "Purchased actions must equip");
                }
                var before = p.getData(ModDataAttachments.SKILL_TREES);
                var encoded = SkillTreeData.CODEC.encodeStart(JsonOps.INSTANCE, before).getOrThrow();
                var restored = SkillTreeData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
                h.assertTrue(before.equals(restored), "The reworked purchases and keys must survive save/load");
                p.setData(ModDataAttachments.SKILL_TREES, restored);
                for (var entry : restored.build(ModAbilities.FLIGHT_ID.toString()).slots().entrySet())
                    h.assertTrue(SkillTreeService.actions(p, ModAbilities.FLIGHT_ID).get(AbilitySlot.valueOf(entry.getKey()))
                            .definition().id().getPath().equals(entry.getValue()), "Existing saved active IDs must resolve after the rework");
            } finally { cleanup(p); }
        }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "flight.skills_rejection")
    public static void missingRescueAndGroundTargetsDoNotSpendStamina(GameTestHelper h) {
        ServerPlayer p = player(h); learn(p);
        try {
            for (String id : new String[]{RESCUE, SANCTUARY, LANCE}) {
                h.assertTrue(SkillTreeService.equip(p, AbilitySlot.PRIMARY, id), "The learned test action must equip");
                int before = PlayerEnergyService.getEnergy(p);
                h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.CANNOT_USE,
                        "A missing friend or ground point must reject before payment: " + id);
                h.assertTrue(PlayerEnergyService.getEnergy(p) == before, "Rejected actions cannot spend stamina");
            }
            h.assertTrue(!FlightSkillService.canUse(p, "flight_unknown") && !FlightSkillService.canUse(p, "quake_0_1"),
                    "Unknown and foreign action IDs must not activate Flight skill state");
            h.succeed();
        } finally { cleanup(p); }
    }

    @GameTest(template = "rewind_empty", batch = "flight.rescue")
    public static void rescueRushFindsAFriendAndTandemLiftLaunchesBoth(GameTestHelper h) {
        ServerPlayer p = player(h); learn(p, "flight_0_2"); FlightService.toggleFlight(p);
        LivingEntity friend = target(h, p.position().add(0, -2, 10));
        LivingEntity enemy = target(h, p.position().add(0, 0, 3));
        var team = team(p, friend);
        try {
            Vec3 initial = p.position();
            cast(h, p, RESCUE);
            h.assertTrue(p.position().distanceTo(initial) > 5 && p.distanceTo(friend) < 4,
                    "Rescue Rush must close on the visible teammate rather than a nearer hostile");
            h.assertTrue(friend.hasEffect(MobEffects.SLOW_FALLING) && friend.hasEffect(MobEffects.ABSORPTION),
                    "The rescued friend receives real landing protection and temporary extra health");
            h.assertTrue(friend.getDeltaMovement().y >= .5 && p.getDeltaMovement().y >= .5,
                    "Tandem Lift adds an upward launch to the rescuer and the rescued friend");
            h.assertTrue(FlightDynamicsService.momentum(p) >= 15 && enemy.getDeltaMovement().equals(Vec3.ZERO),
                    "A successful upgraded rescue banks Momentum without affecting a hostile bystander");
            h.succeed();
        } finally { p.getScoreboard().removePlayerTeam(team); cleanup(p, friend, enemy); }
    }

    @GameTest(template = "rewind_empty", batch = "flight.sanctuary")
    public static void sanctuaryHealsOnceAndDeflectsOnlyHostileShots(GameTestHelper h) {
        ServerPlayer p = player(h); learn(p, "flight_safe_harbor");
        Vec3 center = ground(h, p);
        LivingEntity friend = target(h, center.add(1, 0, 0)); friend.setHealth(10);
        LivingEntity enemy = target(h, center.add(-1, 0, 0));
        var team = team(p, friend);
        Arrow hostile = arrow(h, enemy, center.add(0, 1, 3), new Vec3(0, 0, -1));
        Arrow friendly = arrow(h, p, center.add(1, 1, 3), new Vec3(0, 0, -1));
        try {
            cast(h, p, SANCTUARY); tick(p, 6);
            h.assertTrue(friend.getHealth() == 12 && friend.hasEffect(MobEffects.DAMAGE_RESISTANCE)
                            && friend.hasEffect(MobEffects.SLOW_FALLING),
                    "Safe Harbor heals each friend once and gives the sanctuary's defensive buffs");
            h.assertTrue(hostile.getDeltaMovement().z > 0 && hostile.getDeltaMovement().length() < 1,
                    "A hostile shot entering the sanctuary is bent outward and loses speed");
            h.assertTrue(friendly.getDeltaMovement().z == -1 && friendly.getOwner() == p,
                    "Friendly projectiles must pass through their own sanctuary unchanged");
            friend.setHealth(10); tick(p, 24);
            h.assertTrue(friend.getHealth() == 10, "Repeated field ticks must not turn Safe Harbor into unlimited healing");
            h.succeed();
        } finally { p.getScoreboard().removePlayerTeam(team); clearGround(h, center); cleanup(p, friend, enemy, hostile, friendly); }
    }

    @GameTest(template = "rewind_empty", batch = "flight.barrel_roll")
    public static void barrelRollDeflectsHostileShotsAndEndsItsMovementState(GameTestHelper h) {
        ServerPlayer p = player(h); learn(p); FlightService.toggleFlight(p);
        LivingEntity shooter = target(h, p.position().add(0, 0, 5));
        Arrow hostile = arrow(h, shooter, p.position().add(0, 1, 1), new Vec3(0, 0, -1));
        Arrow friendly = arrow(h, p, p.position().add(.5, 1, 1), new Vec3(0, 0, -1));
        try {
            cast(h, p, ROLL);
            h.assertTrue(FlightSkillService.isRolling(p), "Barrel Roll starts a distinct short movement state");
            tick(p, 3);
            h.assertTrue(p.getDeltaMovement().length() > .5 && FlightSkillService.rollProgress(p) > 0,
                    "The roll has real movement and progresses its animation");
            h.assertTrue(p.getDeltaMovement().x < -.01,
                    "Facing south at yaw zero, the default roll must move right toward negative X as documented");
            h.assertTrue(hostile.getDeltaMovement().z > 0,
                    "The roll deflects an incoming hostile shot");
            h.assertTrue(friendly.getDeltaMovement().z == -1, "The roll must leave the caster's own shots alone");
            tick(p, 16);
            h.assertTrue(!FlightSkillService.isRolling(p), "The roll must end instead of retaining forced movement indefinitely");
            p.setShiftKeyDown(true);
            cast(h, p, ROLL); tick(p, 3);
            h.assertTrue(p.getDeltaMovement().x > .01,
                    "Holding Shift reverses the same Node action into a left roll toward positive X");
            tick(p, 16);
            h.assertTrue(!FlightSkillService.isRolling(p), "The reversed roll also finishes its movement state");
            h.succeed();
        } finally { p.setShiftKeyDown(false); cleanup(p, shooter, hostile, friendly); }
    }

    @GameTest(template = "rewind_empty", batch = "flight.jetstream")
    public static void jetstreamBuffsFriendsPushesFoesAndStopsAtAWall(GameTestHelper h) {
        ServerPlayer p = player(h); learn(p, "flight_jetstream"); FlightService.toggleFlight(p);
        LivingEntity friend = target(h, p.position().add(0, 0, 2));
        LivingEntity enemy = target(h, p.position().add(0, 0, 3));
        LivingEntity behind = target(h, p.position().add(0, 0, 7));
        var team = team(p, friend);
        BlockPos wall = p.blockPosition().offset(0, 0, 5);
        for (int x = -2; x <= 2; x++) for (int y = 0; y <= 4; y++)
            h.getLevel().setBlock(wall.offset(x, y, 0), Blocks.STONE.defaultBlockState(), 3);
        try {
            cast(h, p, JETSTREAM); tick(p, 6);
            h.assertTrue(friend.hasEffect(MobEffects.MOVEMENT_SPEED) && friend.hasEffect(MobEffects.JUMP),
                    "A friend riding the stream receives temporary traversal buffs");
            h.assertTrue(enemy.getDeltaMovement().z > 0, "The same current pushes foes along its direction");
            h.assertTrue(behind.getDeltaMovement().equals(Vec3.ZERO), "The wind lane must not continue through solid cover");
            double granted = FlightDynamicsService.momentum(p);
            h.assertTrue(granted >= 20, "Draft Rider rewards a real teammate entering the lane");
            tick(p, 30);
            h.assertTrue(FlightDynamicsService.momentum(p) == granted, "A teammate remaining in the lane awards its Momentum once per cast");
            h.succeed();
        } finally {
            p.getScoreboard().removePlayerTeam(team); cleanup(p, friend, enemy, behind);
            for (int x = -2; x <= 2; x++) for (int y = 0; y <= 4; y++)
                h.getLevel().setBlock(wall.offset(x, y, 0), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    @GameTest(template = "rewind_empty", batch = "flight.thermal")
    public static void thermalLanceWarnsThenLiftsAndRisingPressureRepeats(GameTestHelper h) {
        ServerPlayer p = player(h); learn(p, "flight_2_2");
        Vec3 center = ground(h, p);
        LivingEntity enemy = target(h, center);
        try {
            cast(h, p, LANCE); tick(p, 6);
            h.assertTrue(enemy.getHealth() == enemy.getMaxHealth() && enemy.getDeltaMovement().equals(Vec3.ZERO),
                    "Thermal Lance must visibly mark the ground before applying force");
            tick(p, 2);
            h.assertTrue(enemy.getHealth() < enemy.getMaxHealth() && enemy.getDeltaMovement().y > .5,
                    "The first thermal pulse damages and launches a foe");
            // Move the actual hitbox between pulses; leaving a stationary test mob at the mark hides range bugs.
            for (int i = 0; i < 7; i++) {
                enemy.move(MoverType.SELF, enemy.getDeltaMovement());
                enemy.setDeltaMovement(enemy.getDeltaMovement().add(0, -.08, 0).scale(.98));
                tick(p, 1);
            }
            h.assertTrue(enemy.getY() - center.y > 3.5,
                    "The first lift must have carried the foe above the old ground-centered sphere before pulse two");
            enemy.move(MoverType.SELF, enemy.getDeltaMovement());
            enemy.setDeltaMovement(enemy.getDeltaMovement().add(0, -.08, 0).scale(.98));
            double beforeSecondPulse = enemy.getDeltaMovement().y;
            enemy.invulnerableTime = 0;
            tick(p, 1);
            h.assertTrue(enemy.getDeltaMovement().y > .9 && enemy.getDeltaMovement().y > beforeSecondPulse + .3,
                    "Rising Pressure reaches the launched foe in the vertical column and renews its upward lift eight ticks later");
            h.succeed();
        } finally { clearGround(h, center); cleanup(p, enemy); }
    }

    @GameTest(template = "rewind_empty", batch = "flight.vacuum")
    public static void vacuumClapPullsBeforeItsBurstAndShiftCancelsIt(GameTestHelper h) {
        ServerPlayer p = player(h); learn(p, "flight_wider_clap");
        LivingEntity enemy = target(h, p.position().add(0, 0, 7.5));
        Arrow shot = arrow(h, enemy, p.position().add(0, 1, 2), new Vec3(0, 0, -1));
        try {
            cast(h, p, CLAP); tick(p, 3);
            h.assertTrue(enemy.getHealth() == enemy.getMaxHealth() && enemy.getDeltaMovement().z < 0,
                    "Eye of Storm extends Vacuum Clap's gathering pull without dealing its burst early");
            h.assertTrue(shot.getDeltaMovement().length() < 1, "Eye of Storm brakes incoming hostile projectiles during the gather");
            h.assertTrue(action(CLAP).deactivate(context(p)), "Shift + Vacuum Clap can cancel its wind-up");
            enemy.setDeltaMovement(Vec3.ZERO); tick(p, 12);
            h.assertTrue(enemy.getHealth() == enemy.getMaxHealth(), "A canceled clap never executes its delayed burst");
            cast(h, p, CLAP); tick(p, 10);
            h.assertTrue(enemy.getHealth() < enemy.getMaxHealth() && enemy.getDeltaMovement().z > 0,
                    "Finishing the gather changes the pull into a damaging outward pressure wave");
            h.succeed();
        } finally { cleanup(p, enemy, shot); }
    }

    @GameTest(template = "rewind_empty", batch = "flight.slipstream_echo")
    public static void chargedSkyburstLeavesAUsableSlipstreamEcho(GameTestHelper h) {
        ServerPlayer p = player(h); learn(p, "flight_pressure_front"); FlightService.toggleFlight(p);
        LivingEntity friend = target(h, p.position().add(0, 0, 4));
        var team = team(p, friend);
        try {
            FlightSkillService.onSkyburst(p, p.position(), new Vec3(0, 0, 1), 49); tick(p, 6);
            h.assertTrue(!friend.hasEffect(MobEffects.MOVEMENT_SPEED), "A weak burst does not leave a free wind lane");
            FlightSkillService.onSkyburst(p, p.position(), new Vec3(0, 0, 1), 60); tick(p, 6);
            h.assertTrue(friend.hasEffect(MobEffects.MOVEMENT_SPEED), "Spending enough Momentum leaves a current another player can ride");
            h.succeed();
        } finally { p.getScoreboard().removePlayerTeam(team); cleanup(p, friend); }
    }

    @GameTest(template = "rewind_empty", batch = "flight.sonic_bloom")
    public static void sonicBloomIsDelayedHalfStrengthAndCannotScheduleItself(GameTestHelper h) {
        ServerPlayer p = player(h); learn(p, "flight_sound_barrier");
        LivingEntity enemy = target(h, p.position().add(0, 0, 2));
        try {
            FlightSkillService.onMeteorImpact(p, p.position(), 5, 8); tick(p, 11);
            h.assertTrue(enemy.getHealth() == enemy.getMaxHealth(), "Sonic Bloom leaves a readable delay after the original impact");
            tick(p, 1);
            h.assertTrue(Math.abs(enemy.getMaxHealth() - enemy.getHealth() - 4) < .1,
                    "The echo applies half the supplied impact damage");
            enemy.setHealth(enemy.getMaxHealth()); enemy.invulnerableTime = 0; tick(p, 30);
            h.assertTrue(enemy.getHealth() == enemy.getMaxHealth(), "The echo must not schedule another echo recursively");
            h.succeed();
        } finally { cleanup(p, enemy); }
    }

    @GameTest(template = "rewind_empty", batch = "flight.aerodynamic_upgrades")
    public static void ramAirBuildsMoreMomentumAndVectorControlChargesFaster(GameTestHelper h) {
        ServerPlayer baseline = player(h); learn(baseline);
        ServerPlayer upgraded = player(h); learn(upgraded, "flight_1_0", "flight_1_2");
        try {
            for (ServerPlayer p : new ServerPlayer[]{baseline, upgraded}) {
                FlightService.toggleFlight(p); FlightService.setForwardInput(p, true); FlightService.changeSpeed(p, 1);
                for (int i = 0; i < 40; i++) {
                    p.setPos(p.position().add(0, 0, 1)); p.setDeltaMovement(new Vec3(0, 0, 1));
                    FlightDynamicsService.tick(p);
                }
            }
            h.assertTrue(FlightDynamicsService.momentum(upgraded) > FlightDynamicsService.momentum(baseline) * 1.25,
                    "Ram Air must improve Momentum earned from the same actual flight, rather than a generic damage bonus");
            FlightDynamicsService.consumeMomentum(baseline); FlightDynamicsService.consumeMomentum(upgraded);
            for (ServerPlayer p : new ServerPlayer[]{baseline, upgraded}) {
                FlightDynamicsService.pressSkyburst(p);
                for (int i = 0; i < 18; i++) FlightDynamicsService.tick(p);
                FlightDynamicsService.releaseSkyburst(p);
            }
            h.assertTrue(upgraded.getDeltaMovement().length() > baseline.getDeltaMovement().length() + .4,
                    "Vector Control reaches a stronger Skyburst in the same eighteen-tick hold");
            h.succeed();
        } finally { cleanup(baseline); cleanup(upgraded); }
    }

    @GameTest(template = "rewind_empty", batch = "flight.fall_upgrades")
    public static void featherlightAndLastSecondSaveHaveDifferentFallRulesAndSuppression(GameTestHelper h) {
        ServerPlayer p = player(h); learn(p, "flight_0_0", "flight_guardian_angel"); FlightService.toggleFlight(p);
        try {
            var shortFall = new LivingFallEvent(p, 8, 1);
            FlightSkillPassives.handleFall(shortFall);
            h.assertTrue(shortFall.getDamageMultiplier() == 0, "Featherlight absorbs a short fall without using the emergency rescue");
            h.assertTrue(!CooldownService.isOnCooldown(p, FlightSkillPassives.LAST_SECOND_SAVE),
                    "A short landing must not consume Last Second Save's cooldown");
            var emergency = new LivingFallEvent(p, 25, 1);
            FlightSkillPassives.handleFall(emergency);
            h.assertTrue(emergency.getDamageMultiplier() == 0 && p.hasEffect(MobEffects.SLOW_FALLING)
                            && FlightDynamicsService.momentum(p) == 25,
                    "Last Second Save prevents a heavy fall and banks its one-time rescue Momentum");
            h.assertTrue(CooldownService.isOnCooldown(p, FlightSkillPassives.LAST_SECOND_SAVE), "The rescue starts its real cooldown");
            var recovering = new LivingFallEvent(p, 25, 1);
            FlightSkillPassives.handleFall(recovering);
            h.assertTrue(recovering.getDamageMultiplier() == .5F && FlightDynamicsService.momentum(p) == 25,
                    "During cooldown only Featherlight's large-fall reduction remains; the rescue cannot fire twice");
            EclipseService.start(p.getServer());
            var suppressed = new LivingFallEvent(p, 8, 1);
            FlightSkillPassives.handleFall(suppressed);
            h.assertTrue(suppressed.getDamageMultiplier() == 1, "The Eclipse suppresses learned fall protection too");
            h.succeed();
        } finally { EclipseService.end(p.getServer()); cleanup(p); }
    }

    @GameTest(template = "rewind_empty", batch = "flight.pressure_tap")
    public static void pressureTapRewardsAnActualAirbornePunchButNotAttributedPowerDamage(GameTestHelper h) {
        ServerPlayer p = player(h); learn(p, "flight_2_0"); FlightService.toggleFlight(p);
        LivingEntity enemy = target(h, p.position().add(0, 0, 2));
        try {
            enemy.hurt(p.damageSources().playerAttack(p), 2);
            h.assertTrue(FlightDynamicsService.momentum(p) == 0,
                    "Power damage attributed to the flier must not manufacture melee Momentum");
            enemy.invulnerableTime = 0;
            FlightSkillPassives.onAttack(new AttackEntityEvent(p, enemy));
            enemy.hurt(p.damageSources().playerAttack(p), 2);
            h.assertTrue(FlightDynamicsService.momentum(p) == 5,
                    "A real airborne attack followed by actual damage banks Pressure Tap Momentum");
            enemy.invulnerableTime = 0;
            FlightSkillPassives.onAttack(new AttackEntityEvent(p, enemy));
            enemy.hurt(p.damageSources().playerAttack(p), 2);
            h.assertTrue(FlightDynamicsService.momentum(p) == 5,
                    "Multiple hits inside the short Pressure Tap cooldown cannot stack free Momentum");
            h.succeed();
        } finally { cleanup(p, enemy); }
    }

    @GameTest(template = "rewind_empty", batch = "flight.skills_cleanup")
    public static void incapacitationAndRespecializingClearFieldsChannelsAndQueuedEchoes(GameTestHelper h) {
        ServerPlayer p = player(h); learn(p, "flight_sound_barrier"); FlightService.toggleFlight(p);
        LivingEntity friend = target(h, p.position().add(0, 0, 3));
        LivingEntity enemy = target(h, p.position().add(0, 0, 4));
        var team = team(p, friend);
        try {
            cast(h, p, JETSTREAM); tick(p, 6);
            h.assertTrue(friend.hasEffect(MobEffects.MOVEMENT_SPEED)
                            && friend.getEffect(MobEffects.MOVEMENT_SPEED).getDuration() < 100,
                    "Wind buffs must have a short real expiry instead of lasting indefinitely");
            PowerRules.rewinding(p, true); FlightSkillService.tick(p); PowerRules.rewinding(p, false);
            friend.removeAllEffects(); friend.setDeltaMovement(Vec3.ZERO); tick(p, 12);
            h.assertTrue(!friend.hasEffect(MobEffects.MOVEMENT_SPEED) && friend.getDeltaMovement().equals(Vec3.ZERO),
                    "Ending incapacitation cannot resurrect a removed wind field");
            cast(h, p, JETSTREAM); tick(p, 6);
            FlightSkillService.onMeteorImpact(p, p.position(), 5, 8);
            cast(h, p, CLAP);
            h.assertTrue(FlightSkillService.isChanneling(p) && friend.hasEffect(MobEffects.MOVEMENT_SPEED),
                    "The respecialization fixture contains a placed field, held channel and queued echo");
            h.assertTrue(SkillTreeService.respecialize(p), "A player out of combat can respecialize their learned Flight skills");
            friend.removeAllEffects(); friend.setDeltaMovement(Vec3.ZERO); enemy.setDeltaMovement(Vec3.ZERO);
            tick(p, 30);
            h.assertTrue(!FlightSkillService.isChanneling(p) && !friend.hasEffect(MobEffects.MOVEMENT_SPEED)
                            && enemy.getHealth() == enemy.getMaxHealth() && enemy.getDeltaMovement().equals(Vec3.ZERO),
                    "Respecializing must cancel existing fields, held attacks and future Sonic Bloom damage immediately");
            h.succeed();
        } finally { p.getScoreboard().removePlayerTeam(team); cleanup(p, friend, enemy); }
    }
}
