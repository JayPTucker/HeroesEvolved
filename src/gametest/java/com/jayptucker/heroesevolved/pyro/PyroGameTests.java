package com.jayptucker.heroesevolved.pyro;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class PyroGameTests {
    private static ServerPlayer player(GameTestHelper h, int level) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "pyro-test")) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
        };
        try {
            Connection connection = p.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true); grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3)))); p.setYRot(0); p.setXRot(0);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.PYROKINESIS_ID).unlock(ModAbilities.PYROKINESIS_ID));
        PlayerProgressionService.setLevel(p, level);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        return p;
    }

    private static LivingEntity target(GameTestHelper h, ServerPlayer p, double distance) {
        var target = EntityType.PILLAGER.create(h.getLevel());
        target.setNoAi(true); target.setNoGravity(true); target.setPersistenceRequired();
        target.moveTo(p.getX(), p.getY(), p.getZ() + distance, 180, 0);
        h.getLevel().addFreshEntity(target);
        return target;
    }

    @GameTest(template = "rewind_empty", batch = "pyro.immunity")
    public static void fireNeverHurtsAndLavaStopsHurtingAtLevelThree(GameTestHelper h) {
        ServerPlayer novice = player(h, 1);
        float health = novice.getHealth();
        novice.hurt(novice.damageSources().inFire(), 4);
        novice.hurt(novice.damageSources().onFire(), 4);
        h.assertTrue(novice.getHealth() == health, "Fire must never hurt a pyrokinetic");
        novice.invulnerableTime = 0;
        novice.hurt(novice.damageSources().lava(), 4);
        h.assertTrue(novice.getHealth() < health, "Lava still hurts below level three");
        ServerPlayer veteran = player(h, 3);
        float before = veteran.getHealth();
        veteran.hurt(veteran.damageSources().lava(), 4);
        h.assertTrue(veteran.getHealth() == before, "Lava must not hurt a level three pyrokinetic");
        PyroService.forget(novice); PyroService.forget(veteran);
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "pyro.flare")
    public static void flareLightsRealFireUnlessCrouching(GameTestHelper h) {
        ServerPlayer p = player(h, 1);
        p.setXRot(50);
        try {
            h.assertTrue(PyroService.flare(p, 1), "Flare must fire");
            BlockPos origin = h.absolutePos(new BlockPos(3, 2, 3));
            boolean lit = BlockPos.betweenClosedStream(origin.offset(-1, -1, 0), origin.offset(1, 1, 5))
                    .anyMatch(pos -> h.getLevel().getBlockState(pos).is(BlockTags.FIRE));
            h.assertTrue(lit, "Flare must light a real fire block where it strikes");
            BlockPos.betweenClosed(origin.offset(-1, -1, 0), origin.offset(1, 1, 5)).forEach(pos -> {
                if (h.getLevel().getBlockState(pos).is(BlockTags.FIRE)) h.getLevel().removeBlock(pos, false);
            });
            p.setShiftKeyDown(true);
            PyroService.flare(p, 1);
            boolean contained = BlockPos.betweenClosedStream(origin.offset(-1, -1, 0), origin.offset(1, 1, 5))
                    .noneMatch(pos -> h.getLevel().getBlockState(pos).is(BlockTags.FIRE));
            h.assertTrue(contained, "Crouching keeps the flames contained");
            h.succeed();
        } finally { PyroService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "pyro.soul_fire")
    public static void blueFlarePlacesVanillaSoulFireOnStone(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        var level = h.getLevel();
        BlockPos at = p.blockPosition(), support = at.below();
        level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
        p.setXRot(90);
        LivingEntity victim = target(h, p, 3);
        try {
            PyroService.flare(p, 5);
            var regular = level.getBlockState(at);
            h.assertTrue(regular.is(Blocks.FIRE), "An orange flare lights regular fire on stone");
            regular.entityInside(level, at, victim);
            float regularDamage = victim.getMaxHealth() - victim.getHealth();
            level.removeBlock(at, false);

            PyroService.addHeat(p, 100);
            p.setShiftKeyDown(true);
            PyroService.flare(p, 5);
            h.assertTrue(level.getBlockState(at).isAir(), "A contained blue flare leaves the stone clear");
            p.setShiftKeyDown(false);
            PyroService.flare(p, 5);
            level.setBlockAndUpdate(at.east(), Blocks.STONE.defaultBlockState());
            var soul = level.getBlockState(at);
            h.assertTrue(soul.is(Blocks.SOUL_FIRE), "Blue fire on stone survives a neighbor update as vanilla Soul Fire");

            victim.invulnerableTime = 0;
            victim.setHealth(victim.getMaxHealth());
            soul.entityInside(level, at, victim);
            h.assertTrue(regularDamage > 0 && victim.getMaxHealth() - victim.getHealth() == regularDamage * 2,
                    "The placed block does twice the contact damage of regular fire");

            level.removeBlock(support, false);
            h.assertTrue(level.getBlockState(at).isAir(), "Soul Fire extinguishes when its support is removed");
            h.succeed();
        } finally { victim.discard(); PyroService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "pyro.soul_sunfall")
    public static void blueSunfallStillPlacesSoulFireAfterSpendingHeat(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(6, 2, 6))));
        p.setXRot(90);
        PyroService.addHeat(p, 100);
        h.assertTrue(PyroService.startInferno(p, 5) && PyroService.releaseInferno(p), "A blue Sunfall can drop");
        h.assertTrue(!PyroService.isBlue(p), "The caster spends their blue Heat before the sun lands");
        h.runAfterDelay(PyroGeometry.SUN_FLIGHT + 2, () -> {
            try {
                var fires = BlockPos.betweenClosedStream(h.absolutePos(new BlockPos(0, 1, 0)),
                                h.absolutePos(new BlockPos(11, 3, 11))).map(h.getLevel()::getBlockState)
                        .filter(state -> state.is(BlockTags.FIRE)).toList();
                h.assertTrue(!fires.isEmpty() && fires.stream().allMatch(state -> state.is(Blocks.SOUL_FIRE)),
                        "A blue Sunfall leaves Soul Fire even after it uses up the caster's Heat");
                h.succeed();
            } finally { PyroService.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "pyro.heat")
    public static void heatTurnsFlamesBlueAndWaterQuenchesIt(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        try {
            var victim = target(h, p, 4);
            PyroService.burn(p, victim, 2, 0);
            float orange = victim.getMaxHealth() - victim.getHealth();
            h.assertTrue(!PyroService.isBlue(p), "Heat starts cold");
            PyroService.addHeat(p, 80);
            h.assertTrue(PyroService.isBlue(p), "Eighty heat turns the flames blue");
            PyroService.quench(p);
            h.assertTrue(PyroService.heat(p) == 0 && !PyroService.isBlue(p), "Quenching resets Heat");
            h.assertTrue(orange > 0 && victim.getRemainingFireTicks() <= 0, "A zero-second burn deals damage without ignition");
            victim.discard();
            h.succeed();
        } finally { PyroService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "pyro.ring")
    public static void ringOfFireBurnsUpIncomingArrows(GameTestHelper h) {
        ServerPlayer p = player(h, 2);
        PyroService.ringOfFire(p, 2);
        Arrow arrow = new Arrow(EntityType.ARROW, h.getLevel());
        arrow.setPos(p.position().add(3, 1, 0));
        arrow.setDeltaMovement(-0.01, 0, 0);
        arrow.setNoGravity(true);
        h.getLevel().addFreshEntity(arrow);
        h.runAfterDelay(6, () -> h.assertTrue(!arrow.isRemoved(), "Firestorm gives a warning before it intercepts"));
        h.runAfterDelay(17, () -> {
            try {
                h.assertTrue(arrow.isRemoved(), "Projectiles inside the ring must burn up");
                h.succeed();
            } finally { PyroService.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "pyro.thermal")
    public static void fireOnAFrozenEnemyCausesThermalShock(GameTestHelper h) {
        ServerPlayer p = player(h, 3);
        try {
            var frozen = target(h, p, 3);
            var control = target(h, p, -3);
            h.assertTrue(CryoService.freezeSolid(frozen, 100, p), "The setup must actually freeze the target");
            PyroService.burn(p, frozen, 2, 0);
            PyroService.burn(p, control, 2, 0);
            h.assertTrue(!CryoService.isFrozen(frozen), "Thermal Shock consumes the freeze");
            h.assertTrue(frozen.getHealth() < control.getHealth(), "Thermal Shock adds a steam explosion on top of the burn");
            frozen.discard(); control.discard();
            h.succeed();
        } finally { PyroService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "pyro.inferno")
    public static void infernoChargesAndShiftCancelsIt(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        try {
            h.assertTrue(PyroService.startInferno(p, 5) && PyroService.isCharging(p), "Inferno must start charging");
            h.assertTrue(PyroService.cancelInferno(p) && !PyroService.isCharging(p), "Shift cancels a charging Inferno");
            h.succeed();
        } finally { PyroService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "pyro.skills")
    public static void techniquesHaveRealConsequences(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        try {
            var context = new AbilityUseContext(p, ModAbilities.PYROKINESIS_ID, 5, false);
            BlockPos fire = h.absolutePos(new BlockPos(5, 2, 3));
            h.getLevel().setBlock(fire, Blocks.FIRE.defaultBlockState(), 3);
            h.assertTrue(h.getLevel().getBlockState(fire).is(BlockTags.FIRE), "Setup must place a fire");
            PlayerEnergyService.consumeUpTo(p, 40);
            int before = PlayerEnergyService.getEnergy(p);
            h.assertTrue(PyroService.technique(context, "fire_shepherd"), "Fire Shepherd must activate");
            h.assertTrue(h.getLevel().getBlockState(fire).isAir(), "Fire Shepherd must put out nearby fire");
            h.assertTrue(PlayerEnergyService.getEnergy(p) > before, "Drawing in fire restores stamina");
            var victim = target(h, p, 6);
            float health = victim.getHealth();
            h.assertTrue(PyroService.technique(context, "blue_lance"), "Blue Lance must fire");
            h.assertTrue(victim.getHealth() < health, "Blue Lance must hit what it pierces");
            victim.discard();
            h.succeed();
        } finally { PyroService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "pyro.combo")
    public static void flareDetonatesOnlyWithBurningTargetAndHeat(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        var victim = target(h, p, 4);
        var nearby = target(h, p, 5);
        try {
            victim.igniteForSeconds(10);
            PyroService.addHeat(p, 20);
            float before = nearby.getHealth();
            PyroService.flare(p, 5);
            h.assertTrue(nearby.getHealth() < before, "Burning targets must splash nearby enemies");
            h.assertTrue(PyroService.heat(p) == 12, "Detonation spends 20 Heat before generating 12");
            h.assertTrue(!PyroService.spendHeat(p, 20) && PyroService.heat(p) == 12,
                    "Insufficient Heat must not be spent");
            h.succeed();
        } finally { victim.discard(); nearby.discard(); PyroService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "pyro.collapse")
    public static void ringCollapseDamagesAndEndsProtection(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        p.setShiftKeyDown(true);
        p.setXRot(90);
        var victim = target(h, p, 2);
        PyroService.ringOfFire(p, 5);
        PyroService.addHeat(p, 5);
        float before = victim.getHealth();
        h.assertTrue(PyroService.collapseRing(p, 5), "An active ring can collapse");
        h.assertTrue(!PyroService.hasRing(p), "Collapse immediately trades away ring protection");
        h.assertTrue(victim.getHealth() == before, "The inward warning must precede the damaging eruption");
        h.assertTrue(PyroService.heat(p) == 0, "Fueled collapse spends twenty Heat once");
        h.runAfterDelay(9, () -> {
            try {
                h.assertTrue(victim.getHealth() < before, "Collapse damages enemies after the inward warning");
                h.assertTrue(!PyroService.collapseRing(p, 5), "A ring cannot collapse twice");
                h.succeed();
            } finally { victim.discard(); PyroService.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "pyro.backdraft")
    public static void heatPowersBackdraftMovementAndBlast(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        p.setShiftKeyDown(true);
        var victim = target(h, p, 4);
        try {
            PyroService.backdraft(p, 5);
            double ordinarySpeed = p.getDeltaMovement().horizontalDistance();
            h.assertTrue(victim.getHealth() == victim.getMaxHealth(), "Ordinary launch cannot reach four blocks");
            PyroService.addHeat(p, 12);
            PyroService.backdraft(p, 5);
            h.assertTrue(p.getDeltaMovement().horizontalDistance() > ordinarySpeed,
                    "Fueled Backdraft must launch farther");
            h.assertTrue(victim.getHealth() < victim.getMaxHealth(), "Fueled blast reaches four blocks");
            h.assertTrue(PyroService.heat(p) == 8 && PyroService.cushion(p),
                    "Launch spends 20, builds 8, and still cushions landing");
            h.succeed();
        } finally { victim.discard(); PyroService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "pyro.release")
    public static void infernoEarlyReleaseConsumesHeatOnlyOnce(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        p.setShiftKeyDown(true);
        var near = target(h, p, 3);
        var far = target(h, p, 11);
        PyroService.addHeat(p, 40);
        PyroService.startInferno(p, 5);
        h.assertTrue(PyroService.releaseInferno(p), "Releasing a held Sunfall drops the sun");
        h.assertTrue(near.getHealth() == near.getMaxHealth(), "Enemies can move before the falling sun reaches the ground");
        h.assertTrue(PyroService.heat(p) == 0 && !PyroService.isCharging(p), "Release consumes Heat and charge");
        h.assertTrue(!PyroService.releaseInferno(p), "Repeated releases cannot duplicate the explosion");
        h.runAfterDelay(PyroGeometry.SUN_FLIGHT + 2, () -> {
            try {
                h.assertTrue(near.getHealth() < near.getMaxHealth(), "The early sun damages nearby enemies when it lands");
                h.assertTrue(far.getHealth() == far.getMaxHealth(), "Early release has less reach than full charge");
                h.succeed();
            } finally { near.discard(); far.discard(); PyroService.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "pyro.spear")
    public static void chargedSpearPiercesEnemiesButStopsAtWalls(GameTestHelper h) {
        ServerPlayer p = player(h, 3);
        p.setShiftKeyDown(true);
        var first = target(h, p, 3);
        var second = target(h, p, 5);
        var blocked = target(h, p, 8);
        BlockPos wall = p.blockPosition().offset(0, 0, 7);
        h.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(wall.above(), Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(wall.above(2), Blocks.STONE.defaultBlockState(), 3);
        PyroService.startSpear(p, 3);
        h.runAfterDelay(22, () -> {
            try {
                h.assertTrue(first.getHealth() < first.getMaxHealth() && second.getHealth() < second.getMaxHealth(),
                        "Fully charged Cinder Spear must pierce multiple enemies");
                h.assertTrue(blocked.getHealth() == blocked.getMaxHealth(), "Spear cannot pass through walls");
                h.assertTrue(!PyroService.isChargingSpear(p) && !PyroService.releaseSpear(p),
                        "Automatic release clears charge and cannot fire twice");
                h.succeed();
            } finally { first.discard(); second.discard(); blocked.discard(); PyroService.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "pyro.sunfall")
    public static void sunfallLocksItsRemoteTargetAndDoesNotFollowCaster(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        p.setShiftKeyDown(true);
        var remote = target(h, p, 8);
        PyroService.startInferno(p, 5);
        p.setPos(p.position().add(8, 0, 0));
        h.assertTrue(PyroService.releaseInferno(p), "Aimed Sunfall releases");
        h.runAfterDelay(PyroGeometry.SUN_FLIGHT + 2, () -> {
            try {
                h.assertTrue(remote.getHealth() < remote.getMaxHealth(),
                        "Sunfall strikes the original remote target after caster moves");
                h.succeed();
            } finally { remote.discard(); PyroService.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "pyro.vortex")
    public static void firestormPullsBurningEnemiesTowardItsCenter(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        p.setShiftKeyDown(true);
        p.setXRot(90);
        var victim = target(h, p, 3);
        victim.setNoGravity(true);
        victim.igniteForSeconds(8);
        PyroService.ringOfFire(p, 5);
        Vec3 start = victim.position();
        h.runAfterDelay(16, () -> {
            try {
                h.assertTrue(victim.getZ() < start.z || victim.getDeltaMovement().z < 0,
                        "Firestorm must draw burning enemies inward");
                h.succeed();
            } finally { victim.discard(); PyroService.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "pyro.cancel_spear")
    public static void cancellingSpearLeavesTargetsUnharmed(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        var victim = target(h, p, 4);
        try {
            PyroService.startSpear(p, 5);
            h.assertTrue(PyroService.cancelSpear(p) && !PyroService.releaseSpear(p),
                    "Cancelled spear cannot release later");
            h.assertTrue(victim.getHealth() == victim.getMaxHealth(), "Cancellation does not attack");
            h.succeed();
        } finally { victim.discard(); PyroService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "pyro.fanned_storm")
    public static void breathWidensTheStormAndCannotOverwriteAHeldCast(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        p.setPos(p.position().add(0, 0, 3));
        var outside = target(h, p, 4.8);
        p.setShiftKeyDown(true);
        p.setXRot(90);
        h.assertTrue(PyroService.ringOfFire(p, 5), "Set a contained Firestorm at the test point");
        p.setPos(p.position().add(0, 0, -3));
        p.setXRot(10);
        h.runAfterDelay(PyroGeometry.STORM_WARNING + 1, () -> {
            h.assertTrue(outside.getHealth() == outside.getMaxHealth(), "An enemy beyond the normal fire ring is safe");
            h.assertTrue(PyroService.startPalm(p, 5), "Breath can fan the placed ring");
            h.assertTrue(!PyroService.startSpear(p, 5) && !PyroService.startInferno(p, 5),
                    "A held Breath must keep ownership of the cast until it ends");
        });
        h.runAfterDelay(PyroGeometry.STORM_WARNING + 23, () -> {
            try {
                h.assertTrue(outside.getHealth() < outside.getMaxHealth(), "Fanning widens the ring beyond four blocks");
                h.succeed();
            } finally { outside.discard(); PyroService.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "pyro.flight")
    public static void flameFlightSteersBoostsAndRestoresPermissions(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        ServerPlayer cruise = player(h, 5);
        try {
            boolean couldFly = p.mayFly();
            h.assertTrue(PyroFlightService.toggle(p, 5) && PyroFlightService.isFlying(p), "Level five can ignite jets");
            h.assertTrue(p.mayFly() && p.getAbilities().flying, "Jets must authorize flight on the server");
            PyroFlightService.toggle(cruise, 5);
            h.getLevel().getChunkAt(p.blockPosition().south(8));
            for (int step = 0; step < 3; step++) {
                PyroFlightService.changeSpeed(p, 1);
                PyroFlightService.changeSpeed(cruise, 1);
            }
            PyroFlightService.input(cruise, 1, 0, false, false, false);
            PyroService.addHeat(p, 40);
            PyroFlightService.input(p, 1, 0, false, false, true);
            int energy = PlayerEnergyService.getEnergy(p);
            for (int tick = 0; tick < 20; tick++) {
                PyroFlightService.tick(p);
                PyroFlightService.tick(cruise);
            }
            h.assertTrue(p.getDeltaMovement().z > cruise.getDeltaMovement().z * 1.15,
                    "A spooled Heat boost must outrun ordinary cruise");
            h.assertTrue(p.getDeltaMovement().z > 3.9, "Top afterburner must exceed the vanilla motion packet's limit");
            h.assertTrue(PyroService.heat(p) == 32 && PlayerEnergyService.getEnergy(p) == energy - 10,
                    "Boosting spends Heat and sustained flight stamina");
            PyroFlightService.input(p, 0, 0, false, false, false);
            for (int tick = 0; tick < 15; tick++) PyroFlightService.tick(p);
            h.assertTrue(p.getDeltaMovement().length() < 0.01, "Releasing movement must settle into a hover");
            PyroFlightService.deactivate(p, false);
            h.assertTrue(!PyroFlightService.isFlying(p) && p.mayFly() == couldFly && !p.getAbilities().flying,
                    "Stopping removes only the flight permission granted by jets");
            h.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING), "Stopping cushions the descent");
            h.succeed();
        } finally { PyroService.forget(p); PyroService.forget(cruise); }
    }

    @GameTest(template = "rewind_empty", batch = "pyro.flight_gate")
    public static void flameFlightRejectsNovicesAndStopsOnSuppression(GameTestHelper h) {
        ServerPlayer novice = player(h, 2);
        ServerPlayer veteran = player(h, 5);
        try {
            h.assertTrue(!PyroFlightService.toggle(novice, 5), "Packet tier cannot bypass the actual level requirement");
            PyroFlightService.toggle(veteran, 5);
            com.jayptucker.heroesevolved.ability.service.PowerRules.rewinding(veteran, true);
            PyroFlightService.tick(veteran);
            h.assertTrue(!PyroFlightService.isFlying(veteran) && !veteran.mayFly(),
                    "An immobilized player must lose propulsion and its flight permission");
            h.succeed();
        } finally {
            com.jayptucker.heroesevolved.ability.service.PowerRules.rewinding(veteran, false);
            PyroService.forget(novice); PyroService.forget(veteran);
        }
    }

    @GameTest(template = "rewind_empty", batch = "pyro.meteor")
    public static void meteorDiveCollidesWithGroundAndErupts(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        var victim = target(h, p, 2);
        h.getLevel().setBlock(p.blockPosition().below(), Blocks.STONE.defaultBlockState(), 3);
        p.setPos(p.position().add(0, 2, 0));
        p.setXRot(90);
        p.setOnGround(false);
        try {
            PyroFlightService.toggle(p, 5);
            PyroService.addHeat(p, 40);
            h.assertTrue(PyroFlightService.deactivate(p, true) && PyroFlightService.isDiving(p),
                    "Shift while airborne starts Meteor Dive");
            PyroFlightService.tick(p);
            h.assertTrue(!PyroFlightService.isPlunging(p) && p.getDeltaMovement().equals(Vec3.ZERO)
                    && victim.getHealth() == victim.getMaxHealth(), "Meteor Dive gathers flame before starting the plunge");
            for (int tick = 0; tick < 20 && PyroFlightService.isFlying(p); tick++) {
                PyroFlightService.tick(p);
                if (PyroFlightService.isFlying(p)) p.move(net.minecraft.world.entity.MoverType.SELF, p.getDeltaMovement());
            }
            h.assertTrue(!PyroFlightService.isFlying(p), "Contact must finish the dive");
            h.assertTrue(p.getY() >= h.absolutePos(new BlockPos(3, 2, 3)).getY(), "Dive cannot tunnel through the ground");
            h.assertTrue(victim.getHealth() < victim.getMaxHealth(), "Meteor impact burns nearby enemies");
            h.assertTrue(PyroService.heat(p) == 10, "Meteor impact spends up to thirty Heat");
            h.succeed();
        } finally { victim.discard(); PyroService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "pyro.hover_mastery")
    public static void hoverGrowsSteadierWithLevel(GameTestHelper h) {
        ServerPlayer novice = player(h, 3);
        ServerPlayer master = player(h, 5);
        try {
            PyroFlightService.toggle(novice, 3);
            PyroFlightService.toggle(master, 5);
            double noviceMotion = 0, masterMotion = 0;
            for (int tick = 0; tick < 45; tick++) {
                PyroFlightService.tick(novice);
                PyroFlightService.tick(master);
                if (tick > 15) {
                    noviceMotion += novice.getDeltaMovement().length();
                    masterMotion += master.getDeltaMovement().length();
                }
            }
            h.assertTrue(noviceMotion > 0.5 && masterMotion < 0.01,
                    "Level three must bob and sway while level five holds a steady hover");
            // Raising the same character's level takes effect without restarting flight.
            PlayerProgressionService.setLevel(novice, 4);
            PyroFlightService.tick(novice);
            h.assertTrue(novice.getDeltaMovement().length() < 0.12,
                    "Intermediate hover instability stays gentle and bounded");
            PyroFlightService.input(novice, 1, 0, false, false, false);
            for (int tick = 0; tick < 10; tick++) PyroFlightService.tick(novice);
            h.assertTrue(novice.getDeltaMovement().z > 0.4,
                    "Steering must remain responsive despite novice hover instability");
            h.succeed();
        } finally { PyroService.forget(novice); PyroService.forget(master); }
    }

    @GameTest(template = "rewind_empty", batch = "pyro.flight_speed")
    public static void flightWheelRespectsRealLevelAndCanBrakeToHover(GameTestHelper h) {
        ServerPlayer p = player(h, 3);
        p.setOnGround(false);
        try {
            PyroFlightService.toggle(p, 5);
            for (int notch = 0; notch < 20; notch++) PyroFlightService.changeSpeed(p, 1);
            h.assertTrue(PyroFlightService.speedStep(p) == 2,
                    "A supplied tier cannot unlock speeds above the player's real level");
            PlayerProgressionService.setLevel(p, 5);
            for (int notch = 0; notch < 20; notch++) PyroFlightService.changeSpeed(p, 1);
            h.assertTrue(PyroFlightService.speedStep(p) == 4, "Mastery unlocks all Flight speed settings");
            PyroFlightService.input(p, 1, 0, false, false, false);
            for (int tick = 0; tick < 20; tick++) PyroFlightService.tick(p);
            h.assertTrue(p.getDeltaMovement().length() > 1.5, "Top cruise must reach Flight-like speed");
            for (int notch = 0; notch < 20; notch++) PyroFlightService.changeSpeed(p, -1);
            PyroFlightService.input(p, 0, 0, false, false, false);
            for (int tick = 0; tick < 15; tick++) PyroFlightService.tick(p);
            h.assertTrue(PyroFlightService.speedStep(p) == 0 && p.getDeltaMovement().length() < 0.02,
                    "Scrolling down must shed cruise speed and settle into a hover");
            h.assertTrue(!PyroFlightService.visualPoseActive(p), "Hover returns from the streamlined pose");
            h.succeed();
        } finally { PyroService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "pyro.flight_momentum")
    public static void momentumRequiresTravelAndCruiseFollowsPitchedAim(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        p.setOnGround(false);
        try {
            PyroFlightService.toggle(p, 5);
            PyroFlightService.changeSpeed(p, 1);
            PyroFlightService.changeSpeed(p, 1);
            PyroFlightService.input(p, 1, 0, false, false, false);
            for (int tick = 0; tick < 20; tick++) PyroFlightService.tick(p);
            h.assertTrue(PyroFlightService.momentum(p) == 0,
                    "Holding Forward without displacement must not build Momentum");
            double cruise = p.getDeltaMovement().length();
            p.setXRot(-30);
            for (int tick = 0; tick < 16; tick++) {
                p.setPos(p.position().add(0, 0, 0.2));
                PyroFlightService.tick(p);
            }
            h.assertTrue(PyroFlightService.momentum(p) > 5,
                    "Real forward travel at cruise speed must build Momentum");
            h.assertTrue(p.getDeltaMovement().y > 0.3 && PyroFlightService.visualPoseActive(p),
                    "Looking up must steer cruise into a climb and activate Flight's body pose");
            h.assertTrue(p.getDeltaMovement().length() <= cruise + 0.3,
                    "Redirecting cruise must not create an unbounded acceleration");
            h.succeed();
        } finally { PyroService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "pyro.flight_stale")
    public static void invalidAndLostFlightInputCannotLeaveCruiseRunning(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        p.setOnGround(false);
        PyroFlightService.toggle(p, 5);
        PyroFlightService.input(p, Float.NaN, Float.POSITIVE_INFINITY, true, false, true);
        for (int tick = 0; tick < 10; tick++) PyroFlightService.tick(p);
        h.assertTrue(p.getDeltaMovement().horizontalDistance() < 0.01,
                "Non-finite movement input must never enter propulsion");
        PyroFlightService.input(p, 1, 0, false, false, false);
        for (int tick = 0; tick < 15; tick++) PyroFlightService.tick(p);
        double speed = p.getDeltaMovement().length();
        h.runAfterDelay(14, () -> {
            try {
                for (int tick = 0; tick < 15; tick++) PyroFlightService.tick(p);
                h.assertTrue(p.getDeltaMovement().length() < speed * 0.1 && !PyroFlightService.visualPoseActive(p),
                        "A lost input stream must brake into a hover rather than continuing to cruise");
                h.succeed();
            } finally { PyroService.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "pyro.flight_nozzles")
    public static void flameNozzlesFollowThePitchedFlightBody(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        try {
            p.setYRot(0); p.yRotO = 0;
            p.setXRot(0); p.xRotO = 0;
            Vec3 standingFeet = PyroFlightGeometry.feet(p, false);
            Vec3 cruiseFeet = PyroFlightGeometry.feet(p, true);
            h.assertTrue(cruiseFeet.distanceTo(standingFeet) > 0.5 && cruiseFeet.z < p.getZ(),
                    "Cruise foot jets must move behind the torso rather than stay below the upright hitbox");
            Vec3 left = PyroFlightGeometry.palm(p, true, -1);
            Vec3 right = PyroFlightGeometry.palm(p, true, 1);
            h.assertTrue(left.distanceTo(right) > 0.5,
                    "The two hands must retain distinct flame origins in the streamlined pose");
            p.setXRot(-60); p.xRotO = -60;
            Vec3 climbingFeet = PyroFlightGeometry.feet(p, true);
            h.assertTrue(climbingFeet.y < cruiseFeet.y && climbingFeet.z > cruiseFeet.z,
                    "Jet anchors must tilt with a climb instead of trailing along a fixed world axis");
            h.succeed();
        } finally { PyroService.forget(p); }
    }

    private static LivingEntity ally(GameTestHelper h, ServerPlayer p, double distance) {
        var ally = EntityType.VILLAGER.create(h.getLevel());
        ally.setNoAi(true); ally.setNoGravity(true); ally.setPersistenceRequired();
        ally.moveTo(p.getX(), p.getY(), p.getZ() + distance, 180, 0);
        h.getLevel().addFreshEntity(ally);
        return ally;
    }

    private static AbilityUseContext skillContext(ServerPlayer p) {
        return new AbilityUseContext(p, ModAbilities.PYROKINESIS_ID, 5, false);
    }

    @GameTest(template = "rewind_empty", batch = "pyro.lash_reel")
    public static void emberLashReelsItsVictimThenEnds(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        var victim = target(h, p, 6);
        double start = victim.getZ();
        h.assertTrue(PyroService.skill(skillContext(p), 0), "Ember Lash must find the visible victim");
        h.runAfterDelay(5, () -> {
            h.assertTrue(victim.getZ() < start || victim.getDeltaMovement().z < -0.01,
                    "The lash must physically reel the victim toward its caster");
        });
        h.runAfterDelay(12, () -> {
            try {
                h.assertTrue(!PyroService.isLashing(p), "The short reel must end without leaving a permanent tether");
                h.succeed();
            } finally { victim.discard(); PyroService.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "pyro.mantle_fuel")
    public static void heatMantleFeedsOnHostileShotsAndLeavesAlliedShots(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        var friendly = ally(h, p, -2);
        var board = h.getLevel().getScoreboard();
        var team = board.addPlayerTeam("pyro" + UUID.randomUUID().toString().substring(0, 8));
        board.addPlayerToTeam(p.getScoreboardName(), team);
        board.addPlayerToTeam(friendly.getScoreboardName(), team);
        var friendlyShot = new Arrow(EntityType.ARROW, h.getLevel());
        friendlyShot.setOwner(friendly); friendlyShot.setNoGravity(true);
        friendlyShot.setPos(p.position().add(-1.5, 1, 0));
        h.getLevel().addFreshEntity(friendlyShot);
        var hostileShots = new java.util.ArrayList<Arrow>();
        for (int i = 0; i < 5; i++) {
            var shot = new Arrow(EntityType.ARROW, h.getLevel());
            shot.setNoGravity(true); shot.setPos(p.position().add(1.5, 0.8 + i * 0.1, 0));
            h.getLevel().addFreshEntity(shot); hostileShots.add(shot);
        }
        PyroService.skill(skillContext(p), 1);
        int initialHeat = PyroService.heat(p);
        h.runAfterDelay(6, () -> {
            try {
                h.assertTrue(hostileShots.stream().allMatch(Arrow::isRemoved), "Flame plates must intercept hostile shots");
                h.assertTrue(!friendlyShot.isRemoved(), "A friendly projectile must pass through the mantle");
                h.assertTrue(PyroService.heat(p) > initialHeat && PyroService.heat(p) <= initialHeat + 8,
                        "Intercepted shots feed Heat with a bounded return per pulse");
                PyroService.forget(p);
                h.assertTrue(!PyroService.hasMantle(p), "Power cleanup must remove the projectile-catching shell");
                h.succeed();
            } finally {
                hostileShots.forEach(Arrow::discard); friendlyShot.discard(); friendly.discard();
                board.removePlayerTeam(team); PyroService.forget(p);
            }
        });
    }

    @GameTest(template = "rewind_empty", batch = "pyro.cauterize_ally")
    public static void cauterizingFlameCanRescueAFriendUsingStoredHeat(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        var friend = ally(h, p, 4);
        try {
            p.setHealth(12); friend.setHealth(5);
            friend.igniteForSeconds(8);
            friend.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 100));
            PyroService.addHeat(p, 20);
            h.assertTrue(PyroService.skill(skillContext(p), 2), "Cauterizing Flame must cast on an aimed friend");
            h.assertTrue(friend.getHealth() > 5 && !friend.isOnFire()
                            && !friend.hasEffect(net.minecraft.world.effect.MobEffects.POISON),
                    "Friendly cauterization heals, extinguishes and cleanses its recipient");
            h.assertTrue(p.getHealth() == 12, "Aimed rescue must not silently heal only the caster");
            h.assertTrue(friend.getAbsorptionAmount() >= 4 && PyroService.heat(p) == 0,
                    "Twenty stored Heat becomes a short protective absorption reserve");
            h.succeed();
        } finally { friend.discard(); PyroService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "pyro.mine_warning")
    public static void emberMineArmsAndWarnsBeforeItsEruption(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        p.setXRot(90);
        var victim = target(h, p, 1);
        p.setShiftKeyDown(true);
        h.assertTrue(PyroService.technique(skillContext(p), "ember_mine"), "A mine must attach to aimed ground");
        h.runAfterDelay(5, () -> h.assertTrue(victim.getHealth() == victim.getMaxHealth(),
                "An unarmed mine cannot instantly erupt beneath its victim"));
        h.runAfterDelay(22, () -> {
            try {
                h.assertTrue(victim.getHealth() < victim.getMaxHealth(), "A victim remaining through the warning must be burned");
                h.assertTrue(PyroService.mineCount(p) == 0, "One eruption consumes its mine exactly once");
                h.succeed();
            } finally { victim.discard(); PyroService.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "pyro.mine_escape")
    public static void leavingAnEmberMineWarningAvoidsTheBlast(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        p.setXRot(90); p.setShiftKeyDown(true);
        var victim = target(h, p, 1);
        PyroService.technique(skillContext(p), "ember_mine");
        h.runAfterDelay(10, () -> victim.setPos(p.position().add(4, 0, 0)));
        h.runAfterDelay(22, () -> {
            try {
                h.assertTrue(victim.getHealth() == victim.getMaxHealth(), "The warning must allow a victim to leave the blast radius");
                h.succeed();
            } finally { victim.discard(); PyroService.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "pyro.lance_seam")
    public static void blueLanceBurnsAnArrivingEnemyThenClearsItsSeam(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        p.setShiftKeyDown(true);
        var arriving = target(h, p, 4);
        arriving.setPos(p.position().add(3, 0, 4));
        h.assertTrue(PyroService.technique(skillContext(p), "blue_lance"), "Blue Lance must fire its fixed drill seam");
        h.assertTrue(arriving.getHealth() == arriving.getMaxHealth(), "An enemy outside the original line is initially safe");
        h.runAfterDelay(2, () -> arriving.setPos(p.position().add(0, 0, 4)));
        h.runAfterDelay(9, () -> h.assertTrue(arriving.getHealth() < arriving.getMaxHealth(),
                "An enemy entering the short-lived drill seam must take fire damage"));
        h.runAfterDelay(16, () -> {
            try {
                h.assertTrue(!PyroService.isLancing(p), "The drill seam must expire without a held key");
                h.succeed();
            } finally { arriving.discard(); PyroService.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "pyro.shepherd_rescue")
    public static void fireShepherdRecoversHeatAndExtinguishesFriends(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        var friend = ally(h, p, 3);
        try {
            friend.setHealth(8); friend.igniteForSeconds(8);
            for (int i = -1; i <= 1; i++) {
                BlockPos fire = p.blockPosition().offset(2, 0, i);
                h.getLevel().setBlock(fire.below(), Blocks.STONE.defaultBlockState(), 3);
                h.getLevel().setBlock(fire, Blocks.FIRE.defaultBlockState(), 3);
            }
            PyroService.technique(skillContext(p), "fire_shepherd");
            h.assertTrue(PyroService.heat(p) >= 6, "Absorbed world fire must replenish flight's Heat fuel");
            h.assertTrue(!friend.isOnFire() && friend.getHealth() >= 10, "The shepherd must rescue nearby burning friends");
            h.succeed();
        } finally { friend.discard(); PyroService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "helixite")
    public static void helixiteOreDropsShardsAndGeneratesUnderground(GameTestHelper h) {
        var tool = new ItemStack(Items.IRON_PICKAXE);
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        for (Block ore : new Block[] {ModBlocks.HELIXITE_ORE.get(), ModBlocks.DEEPSLATE_HELIXITE_ORE.get()}) {
            var drops = Block.getDrops(ore.defaultBlockState(), h.getLevel(), pos, null, null, tool);
            h.assertTrue(drops.size() == 1 && drops.getFirst().is(ModItems.HELIXITE_SHARD.get()),
                    "Mining Helixite with a pickaxe must drop one shard: " + drops);
            h.assertTrue(ore.defaultBlockState().getLightEmission() > 0, "Helixite glows faintly");
        }
        var placed = h.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        h.assertTrue(placed.containsKey(ResourceLocation.fromNamespaceAndPath("heroesevolved", "helixite_ore")),
                "The Helixite ore feature must be registered for world generation");
        h.succeed();
    }
}
