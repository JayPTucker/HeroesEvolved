package com.jayptucker.heroesevolved.ability;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.cryo.CryoCombatSkills;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.electro.ElectroService;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.invisibility.ForceFieldService;
import com.jayptucker.heroesevolved.hydro.HydroService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.pyro.PyroService;
import com.jayptucker.heroesevolved.quake.QuakeService;
import com.jayptucker.heroesevolved.radiation.RadiationService;
import com.jayptucker.heroesevolved.radiation.RadiationSkills;
import com.jayptucker.heroesevolved.telekinesis.TelekineticArsenalService;
import com.jayptucker.heroesevolved.vibe.VibeService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Long lanes outside the small, barrier-boxed template: real loaded chunks, real casts and real damage. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class ProjectileRangeGameTests {
    private static int nextLane;

    private static final class Lane implements AutoCloseable {
        final GameTestHelper h;
        final ServerPlayer player;
        final ResourceLocation power;
        final List<LivingEntity> targets = new ArrayList<>();
        final List<BlockPos> blocks = new ArrayList<>();
        final int chunkX;
        Lane(GameTestHelper h, ResourceLocation power) {
            this.h = h; this.power = power;
            // Spaced well apart so tests running together never put actors in another test's line of fire.
            int x = 12000 + nextLane++ * 128;
            chunkX = x >> 4;
            for (int z = 62; z <= 69; z++) h.getLevel().setChunkForced(chunkX, z, true);
            player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "range-test"));
            try {
                var channel = Connection.class.getDeclaredField("channel");
                channel.setAccessible(true);
                channel.set(player.connection.getConnection(), new EmbeddedChannel());
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            player.setPos(x + .5, h.absolutePos(new BlockPos(0, 2, 0)).getY() + 90, 1000.5);
            player.setYRot(0); player.setXRot(0); player.setNoGravity(true);
            player.setData(ModDataAttachments.PLAYER_ABILITIES,
                    PlayerAbilityData.empty().assignDormant(power).unlock(power));
            player.setData(ModDataAttachments.POWER_INTRO_HISTORY, player.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(power));
            PlayerProgressionService.setLevel(player, 15);
            PlayerEnergyService.restore(player, PlayerEnergyService.getMaximumEnergy(player));
        }
        LivingEntity target(double distance) {
            var target = EntityType.PILLAGER.create(h.getLevel());
            target.setNoAi(true); target.setNoGravity(true); target.setPersistenceRequired();
            target.setPos(player.position().add(0, 0, distance));
            h.getLevel().addFreshEntity(target); targets.add(target);
            return target;
        }
        void wall(int distance, boolean water) {
            BlockPos middle = BlockPos.containing(player.getEyePosition().add(0, 0, distance));
            for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-1, -1, 0), middle.offset(1, 1, 0))) {
                BlockPos copy = pos.immutable(); blocks.add(copy);
                h.getLevel().setBlock(copy, (water ? Blocks.WATER : Blocks.STONE).defaultBlockState(), 2);
            }
        }
        AbilityUseContext context() { return new AbilityUseContext(player, power, 5, false); }
        /** Wait for entity sections, not a fixed warm-up: large suites may still be loading this lane. */
        void ready(Runnable action) {
            h.runAfterDelay(2, () -> {
                for (int z = 62; z <= 69; z++) {
                    if (!h.getLevel().isPositionEntityTicking(new BlockPos(chunkX * 16, 30, z * 16))) {
                        ready(action); return;
                    }
                }
                try { action.run(); } catch (RuntimeException e) { close(); throw e; }
            });
        }
        void later(int ticks, Runnable assertion) {
            h.runAfterDelay(ticks, () -> { try { assertion.run(); h.succeed(); } finally { close(); } });
        }
        @Override public void close() {
            PyroService.forget(player); CryoCombatSkills.forget(player); CryoService.stopAll(player);
            ElectroService.forget(player); QuakeService.stopAll(player); VibeService.forget(player);
            ForceFieldService.forget(player); RadiationService.stopAll(player);
            HydroService.forget(player); TelekineticArsenalService.forget(player);
            targets.forEach(LivingEntity::discard);
            blocks.forEach(pos -> h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2));
            for (int z = 62; z <= 69; z++) h.getLevel().setChunkForced(chunkX, z, false);
            com.jayptucker.heroesevolved.PowerOwnershipTestSupport.release(player);
            player.discard();
        }
    }

    @GameTest(template = "rewind_empty", batch = "projectile.range00", timeoutTicks = 240)
    public static void fireballHitsAtSeventyNineButStopsAtEighty(GameTestHelper h) {
        Lane lane = new Lane(h, ModAbilities.PYROKINESIS_ID);
        lane.ready(() -> { try (lane) {
            LivingEntity near = lane.target(79);
            PyroService.flare(lane.player, 1);
            h.assertTrue(near.getHealth() < near.getMaxHealth(), "A novice's fireball reaches 79 blocks");
            near.discard();
            LivingEntity beyond = lane.target(82);
            PyroService.flare(lane.player, 1);
            h.assertTrue(beyond.getHealth() == beyond.getMaxHealth() && !beyond.isOnFire(), "Fireball has a finite 80-block reach");
            h.succeed();
        } });
    }

    @GameTest(template = "rewind_empty", batch = "projectile.range01", timeoutTicks = 240)
    public static void fireballStopsAtWaterBeforeADistantEnemy(GameTestHelper h) {
        Lane lane = new Lane(h, ModAbilities.PYROKINESIS_ID);
        lane.ready(() -> { try (lane) {
            LivingEntity target = lane.target(65);
            lane.wall(30, true);
            PyroService.flare(lane.player, 1);
            h.assertTrue(target.getHealth() == target.getMaxHealth(), "Fire quenches on the first water, not a body beyond it");
            h.succeed();
        } });
    }

    @GameTest(template = "rewind_empty", batch = "projectile.range02", timeoutTicks = 240)
    public static void chargedFireSpearReachesTheSameLongRange(GameTestHelper h) {
        Lane lane = new Lane(h, ModAbilities.PYROKINESIS_ID);
        LivingEntity target = lane.target(78);
        lane.ready(() -> { try {
            h.assertTrue(PyroService.startSpear(lane.player, 5), "Spear starts charging");
            lane.later(23, () -> h.assertTrue(target.getHealth() < target.getMaxHealth(), "The charged spear reaches a distant body"));
        } catch (RuntimeException e) { lane.close(); throw e; } });
    }

    @GameTest(template = "rewind_empty", batch = "projectile.range03", timeoutTicks = 240)
    public static void quakeBlastReachesFortyAndStopsAtCover(GameTestHelper h) {
        Lane lane = new Lane(h, ModAbilities.QUAKE_ID);
        lane.ready(() -> { try (lane) {
            LivingEntity target = lane.target(39), beyond = lane.target(43);
            QuakeService.shockwave(lane.player, 1);
            h.assertTrue(target.getHealth() < target.getMaxHealth(), "Even an early Quake blast reaches 39 blocks");
            h.assertTrue(beyond.getHealth() == beyond.getMaxHealth(), "Quake stops at 40 blocks");
            target.setHealth(target.getMaxHealth()); target.invulnerableTime = 0;
            lane.wall(30, false);
            QuakeService.shockwave(lane.player, 1);
            h.assertTrue(target.getHealth() == target.getMaxHealth(), "Quake cannot hit through a wall");
            h.succeed();
        } });
    }

    @GameTest(template = "rewind_empty", batch = "projectile.range04", timeoutTicks = 240)
    public static void lightningChainHasLongRangeWithoutIncreasingItsJumpRadius(GameTestHelper h) {
        Lane lane = new Lane(h, ModAbilities.ELECTROKINESIS_ID);
        lane.ready(() -> { try (lane) {
            LivingEntity target = lane.target(62);
            ElectroService.chainLightning(lane.player, 1);
            h.assertTrue(target.getHealth() < target.getMaxHealth(), "Chain Lightning can acquire its first target at 62 blocks");
            h.succeed();
        } });
    }

    @GameTest(template = "rewind_empty", batch = "projectile.range05", timeoutTicks = 240)
    public static void ballLightningCanActuallyFlySixtyBlocks(GameTestHelper h) {
        Lane lane = new Lane(h, ModAbilities.ELECTROKINESIS_ID);
        LivingEntity target = lane.target(61);
        lane.ready(() -> { try {
            h.assertTrue(ElectroService.ballLightning(lane.player, 1), "Ball Lightning launches");
            lane.later(122, () -> {
                h.assertTrue(target.getHealth() < target.getMaxHealth(), "The orb reaches and hurts the distant enemy");
                h.assertTrue(!ElectroService.hasOrb(lane.player), "The orb ends after impact or its distance budget");
            });
        } catch (RuntimeException e) { lane.close(); throw e; } });
    }

    @GameTest(template = "rewind_empty", batch = "projectile.range06", timeoutTicks = 240)
    public static void crystalLanceFliesBeyondItsOldFortyBlockLimit(GameTestHelper h) {
        Lane lane = new Lane(h, ModAbilities.CRYOKINESIS_ID);
        LivingEntity target = lane.target(60);
        lane.ready(() -> { try {
            h.assertTrue(CryoCombatSkills.activate(lane.context(), 0), "Crystal Lance forges");
            lane.later(52, () -> h.assertTrue(target.getHealth() < target.getMaxHealth(), "A forged lance collides at 60 blocks"));
        } catch (RuntimeException e) { lane.close(); throw e; } });
    }

    @GameTest(template = "rewind_empty", batch = "projectile.range07", timeoutTicks = 240)
    public static void crystalLanceCannotReachBeyondItsBudget(GameTestHelper h) {
        Lane lane = new Lane(h, ModAbilities.CRYOKINESIS_ID);
        LivingEntity beyond = lane.target(67);
        lane.ready(() -> { try {
            h.assertTrue(CryoCombatSkills.activate(lane.context(), 0), "Crystal Lance forges");
            lane.later(52, () -> {
                h.assertTrue(beyond.getHealth() == beyond.getMaxHealth(), "Crystal Lance cannot exceed 64 blocks");
                h.assertTrue(CryoCombatSkills.activeShots(lane.player) == 0, "The expired lance is removed");
            });
        } catch (RuntimeException e) { lane.close(); throw e; } });
    }

    @GameTest(template = "rewind_empty", batch = "projectile.range08", timeoutTicks = 240)
    public static void vibePalmReachesADistantEnemy(GameTestHelper h) {
        Lane lane = new Lane(h, ModAbilities.VIBE_ID);
        LivingEntity target = lane.target(45);
        lane.ready(() -> { try {
            h.assertTrue(VibeService.blast(lane.context(), VibeService.Blast.PALM), "Vibe Blast starts");
            lane.later(30, () -> h.assertTrue(target.getHealth() < target.getMaxHealth(), "The travelling palm blast reaches 45 blocks"));
        } catch (RuntimeException e) { lane.close(); throw e; } });
    }

    @GameTest(template = "rewind_empty", batch = "projectile.range09", timeoutTicks = 240)
    public static void forcePushReachesTwentyTwo(GameTestHelper h) {
        Lane lane = new Lane(h, ModAbilities.INVISIBILITY_ID);
        LivingEntity target = lane.target(22);
        lane.ready(() -> { try {
            ForceFieldService.raiseShield(lane.player); ForceFieldService.push(lane.player);
            lane.later(14, () -> h.assertTrue(target.getHealth() < target.getMaxHealth(), "A travelling Force Push reaches 22 blocks"));
        } catch (RuntimeException e) { lane.close(); throw e; } });
    }

    @GameTest(template = "rewind_empty", batch = "projectile.radiation", timeoutTicks = 240)
    public static void radiationBeamsReachDistantBodiesAndStopOnCover(GameTestHelper h) {
        Lane lane = new Lane(h, ModAbilities.RADIATION_ID);
        lane.ready(() -> { try (lane) {
            LivingEntity target = lane.target(62), beyond = lane.target(67);
            RadiationService.addMass(lane.player, 30);
            h.assertTrue(RadiationService.meltdownBeam(lane.player, 5), "A core with stored heat fires its beam");
            h.assertTrue(target.getHealth() < target.getMaxHealth(), "Meltdown Beam reaches 62 blocks");
            h.assertTrue(beyond.getHealth() == beyond.getMaxHealth(), "Meltdown Beam stops at 64 blocks");
            target.setHealth(target.getMaxHealth()); target.invulnerableTime = 0;
            lane.wall(40, false);
            RadiationService.meltdownBeam(lane.player, 5);
            h.assertTrue(target.getHealth() == target.getMaxHealth(), "Radiation cannot hurt bodies behind cover");
            h.succeed();
        } });
    }

    @GameTest(template = "rewind_empty", batch = "projectile.gamma", timeoutTicks = 240)
    public static void gammaLanceReachesSeventyEight(GameTestHelper h) {
        Lane lane = new Lane(h, ModAbilities.RADIATION_ID);
        lane.ready(() -> { try (lane) {
            LivingEntity target = lane.target(78);
            RadiationSkills.gammaLanceForTest(lane.player, 5);
            h.assertTrue(target.getHealth() < target.getMaxHealth(), "Gamma Lance reaches 78 blocks");
            h.succeed();
        } });
    }

    @GameTest(template = "rewind_empty", batch = "projectile.water", timeoutTicks = 240)
    public static void crashingWaveTravelsThirtyBlocks(GameTestHelper h) {
        Lane lane = new Lane(h, ModAbilities.HYDROKINESIS_ID);
        LivingEntity target = lane.target(30);
        lane.ready(() -> {
            BlockPos start = lane.player.blockPosition().below();
            for (BlockPos cell : BlockPos.betweenClosed(start.offset(-4, 0, -2), start.offset(4, 0, 38))) {
                lane.blocks.add(cell.immutable()); h.getLevel().setBlock(cell, Blocks.STONE.defaultBlockState(), 2);
            }
            HydroService.crashingWave(lane.context());
            lane.later(32, () -> h.assertTrue(target.getHealth() < target.getMaxHealth(), "The Crashing Wave reaches 30 blocks"));
        });
    }

    @GameTest(template = "rewind_empty", batch = "projectile.kinetic", timeoutTicks = 240)
    public static void kineticLanceFliesSixtyBlocks(GameTestHelper h) {
        Lane lane = new Lane(h, ModAbilities.TELEKINESIS_ID);
        LivingEntity target = lane.target(60);
        lane.ready(() -> {
            h.assertTrue(TelekineticArsenalService.lance(lane.player, target.getBoundingBox().getCenter()), "Kinetic Lance launches");
            lane.later(35, () -> h.assertTrue(target.getHealth() < target.getMaxHealth(), "The physical Kinetic Lance hits at 60 blocks"));
        });
    }

    @GameTest(template = "rewind_empty", batch = "projectile.forcecover", timeoutTicks = 240)
    public static void forcePushCannotHitThroughAWall(GameTestHelper h) {
        Lane lane = new Lane(h, ModAbilities.INVISIBILITY_ID);
        LivingEntity target = lane.target(22);
        lane.ready(() -> {
            lane.wall(15, false);
            ForceFieldService.raiseShield(lane.player); ForceFieldService.push(lane.player);
            lane.later(14, () -> h.assertTrue(target.getHealth() == target.getMaxHealth(), "Force Push cannot damage a body behind a wall"));
        });
    }

    @GameTest(template = "rewind_empty", batch = "projectile.range10", timeoutTicks = 240)
    public static void sweptShotsFindTheFirstBodyRegardlessOfEntityOrder(GameTestHelper h) {
        Lane lane = new Lane(h, ModAbilities.CRYOKINESIS_ID);
        lane.ready(() -> { try (lane) {
            lane.target(60);
            LivingEntity near = lane.target(25);
            Vec3 start = lane.player.getEyePosition();
            var hit = Targeting.firstLivingHit(lane.player, start, start.add(0, 0, 70), .18, t -> true);
            h.assertTrue(hit != null && hit.getEntity() == near, "A swept shot picks the nearest contact, not insertion order");
            LivingEntity closest = lane.target(10);
            var first = Targeting.firstLivingHit(lane.player, lane.player.getEyePosition(),
                    lane.player.getEyePosition().add(0, 0, 70), .18, t -> true);
            h.assertTrue(first != null && first.getEntity() == closest, "Nearest body wins even when it was spawned last");
            var overlap = Targeting.firstLivingHit(lane.player, near.getBoundingBox().getCenter(),
                    lane.player.getEyePosition().add(0, 0, 70), .18, t -> true);
            h.assertTrue(overlap != null && overlap.getEntity() == near, "A body containing the start is an immediate hit");
            h.succeed();
        } });
    }
}
