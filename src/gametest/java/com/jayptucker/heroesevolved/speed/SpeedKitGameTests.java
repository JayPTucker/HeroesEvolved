package com.jayptucker.heroesevolved.speed;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.EvolutionTrials;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Blitz chains, Snatch, Cyclone, Lightspeed, and what running fast does on its own. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class SpeedKitGameTests {
    private static ServerPlayer runner(GameTestHelper h, int tier) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "speedkit-test")) {
            @Override public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
                super.teleportTo(level, x, y, z, yaw, pitch); absMoveTo(x, y, z, yaw, pitch);
            }
        };
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3)))); p.setYRot(0); p.setXRot(0);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.SUPERSPEED_ID).unlock(ModAbilities.SUPERSPEED_ID));
        PlayerProgressionService.setLevel(p, tier);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        return p;
    }

    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, ServerPlayer p, double dx, double dz) {
        T mob = type.create(h.getLevel());
        mob.setNoAi(true); mob.setPersistenceRequired();
        mob.moveTo(p.getX() + dx, p.getY(), p.getZ() + dz, 180, 0);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); mob.setHealth(100);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    /** Runs {@code ticks} ticks of real movement, {@code step} blocks along +x each. */
    private static void run(ServerPlayer p, int ticks, double step) {
        SpeedService.toggle(p); p.setSprinting(true);
        for (int i = 0; i < ticks; i++) { p.setPos(p.position().add(step, 0, 0)); SpeedService.tickRunner(p); }
    }

    @GameTest(template = "rewind_empty", batch = "speedkit.blitz", timeoutTicks = 40)
    public static void blitzRushesAFlurryAndChainsToTheNextEnemy(GameTestHelper h) {
        var p = runner(h, 1);
        var first = mob(h, EntityType.PILLAGER, p, 0, 5);
        var second = mob(h, EntityType.PILLAGER, p, 2, 7);
        h.assertTrue(SpeedKit.blitz(p, 1), "Blitz finds the enemy you look at");
        h.assertTrue(p.distanceToSqr(first) <= 9.1, "And you are already there");
        h.runAfterDelay(10, () -> {
            h.assertTrue(first.getHealth() <= 100 - 4 * 2.5F + 0.1F, "Four blows land in a fraction of a second");
            h.assertTrue(SpeedKit.chaining(p), "The chain is open for a moment");
            h.assertTrue(SpeedKit.chainNext(p), "Pressing again goes on to the next enemy");
            h.assertTrue(!SpeedKit.chaining(p), "At tier 1 the chain is one link long");
            h.runAfterDelay(10, () -> {
                try {
                    h.assertTrue(second.getHealth() < 100, "The second enemy is hit too");
                    h.succeed();
                } finally { first.discard(); second.discard(); SpeedService.forget(p); }
            });
        });
    }

    @GameTest(template = "rewind_empty", batch = "speedkit.dash")
    public static void blitzWithNoOneInSightIsADash(GameTestHelper h) {
        var p = runner(h, 1);
        Vec3 start = p.position();
        try {
            h.assertTrue(SpeedKit.blitz(p, 1), "No target: still a dash");
            h.assertTrue(p.position().distanceToSqr(start) > 4, "It moved you");
            h.assertTrue(!SpeedKit.chaining(p), "A dash opens no chain");
            h.succeed();
        } finally { SpeedService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "speedkit.snatch")
    public static void snatchTakesItemsShotsAndAWeaponInOneBlink(GameTestHelper h) {
        var p = runner(h, 2);
        var drop = new ItemEntity(h.getLevel(), p.getX() + 2, p.getY() + 0.2, p.getZ() + 1, new ItemStack(Items.DIAMOND, 3));
        drop.setDeltaMovement(Vec3.ZERO);
        h.getLevel().addFreshEntity(drop);
        var arrow = new Arrow(EntityType.ARROW, h.getLevel());
        arrow.setNoGravity(true); arrow.setPos(p.getX() - 2, p.getY() + 1.4, p.getZ() + 5); arrow.setDeltaMovement(0, 0, -0.6);
        arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.ALLOWED;
        h.getLevel().addFreshEntity(arrow);
        var zombie = mob(h, EntityType.ZOMBIE, p, 0, 4);
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        try {
            int taken = SpeedKit.snatch(p, 2);
            h.assertTrue(taken == 3, "One item stack, one shot and one weapon: " + taken);
            h.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 3 && drop.isRemoved(), "The diamonds are yours");
            h.assertTrue(p.getInventory().countItem(Items.ARROW) == 1 && arrow.isRemoved(), "The arrow was plucked out of the air");
            h.assertTrue(p.getInventory().countItem(Items.IRON_SWORD) == 1 && zombie.getMainHandItem().isEmpty(), "And the zombie's sword");
            h.assertTrue(SpeedKit.snatch(p, 2) == 0, "With nothing left to take, Snatch does nothing");
            h.succeed();
        } finally { zombie.discard(); arrow.discard(); drop.discard(); SpeedService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "speedkit.cyclone", timeoutTicks = 90)
    public static void cycloneCarriesEnemiesThenLetsGo(GameTestHelper h) {
        var p = runner(h, 4);
        var caught = mob(h, EntityType.PILLAGER, p, 3, 0);
        h.assertTrue(SpeedKit.cyclone(p, 4) && SpeedKit.cycloning(p) && SpeedService.hasWhirlwind(p), "The cyclone starts");
        h.assertTrue(!SpeedKit.cyclone(p, 4), "One at a time");
        h.runAfterDelay(25, () -> {
            h.assertTrue(caught.getHealth() < 100, "Caught enemies are hurt as the wind carries them");
            h.runAfterDelay(SpeedKit.CYCLONE_TICKS - 25 + 3, () -> {
                try {
                    h.assertTrue(!SpeedKit.cycloning(p), "After three seconds it breaks");
                    h.succeed();
                } finally { caught.discard(); SpeedService.forget(p); }
            });
        });
    }

    @GameTest(template = "rewind_empty", batch = "speedkit.lightspeed")
    public static void lightspeedStrikesTheLineAndSpendsMomentum(GameTestHelper h) {
        var p = runner(h, 5);
        run(p, 40, 0);
        p.setYRot(0);
        var near = mob(h, EntityType.PILLAGER, p, 0, 4);
        var far = mob(h, EntityType.PILLAGER, p, 0.8, 8);
        var behind = mob(h, EntityType.PILLAGER, p, 0, -4);
        try {
            int struck = SpeedKit.lightspeed(p, 5);
            h.assertTrue(struck == 2, "Both enemies on the line are struck: " + struck);
            h.assertTrue(near.isOnFire() && far.isOnFire(), "Friction sets them alight");
            h.assertTrue(behind.getHealth() == 100, "Nothing behind you is touched");
            h.assertTrue(SpeedService.momentum(p) == 0, "It spends all your Momentum");
            h.succeed();
        } finally { near.discard(); far.discard(); behind.discard(); SpeedService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "speedkit.running")
    public static void runningFastBuffetsWhatYouPassAndCountsTowardTheTrial(GameTestHelper h) {
        var p = runner(h, 5);
        try {
            run(p, 40, 1.0);
            h.assertTrue(SpeedService.momentum(p) == 100, "Real movement builds Momentum to 100");
            h.assertTrue(EvolutionTrials.progress(p, "superspeed") >= 20, "Distance run counts toward the Evolved trial");
            var bystander = mob(h, EntityType.PILLAGER, p, 1.5, 0);
            // Run on past it over a few ticks, the way a runner actually passes someone.
            for (int i = 0; i < 4 && bystander.getHealth() >= 100; i++) {
                p.setPos(p.position().add(0.5, 0, 0));
                SpeedService.tickRunner(p);
            }
            h.assertTrue(bystander.getHealth() < 100, "At 60 Momentum you buffet what you run past: momentum=" + SpeedService.momentum(p)
                    + " active=" + SpeedService.active(p) + " dist=" + p.distanceTo(bystander) + " health=" + bystander.getHealth()
                    + " alive=" + bystander.isAlive() + " stopped=" + com.jayptucker.heroesevolved.time.TimeStopService.isStopped(bystander)
                    + " frozen=" + com.jayptucker.heroesevolved.cryo.CryoService.isFrozen(bystander) + " invuln=" + bystander.invulnerableTime);
            bystander.discard();
            int before = SpeedService.momentum(p);
            h.assertTrue(SpeedService.drainMomentum(p, 30) && SpeedService.momentum(p) == before - 30,
                    "Blitz's charge spends exactly 30: active=" + SpeedService.active(p) + " before=" + before + " after=" + SpeedService.momentum(p));
            h.succeed();
        } finally { SpeedService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "speedkit.trial")
    public static void superspeedHasItsOwnEvolvedTrial(GameTestHelper h) {
        h.assertTrue(EvolutionTrials.special("superspeed") && EvolutionTrials.trial("superspeed").goal() == 20000,
                "Superspeed evolves by running 20 km");
        h.succeed();
    }
}
