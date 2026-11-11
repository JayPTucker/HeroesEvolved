package com.jayptucker.heroesevolved.speed;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Cyclone's tornado, the gust a sudden stop throws, and outrunning the last minute. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class SpeedRunGameTests {
    private static ServerPlayer runner(GameTestHelper h, int tier, boolean inWorld) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "speedrun-test")) {
            @Override public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
                super.teleportTo(level, x, y, z, yaw, pitch); absMoveTo(x, y, z, yaw, pitch);
            }
        };
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3)))); p.setYRot(0); p.setXRot(0);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.SUPERSPEED_ID).unlock(ModAbilities.SUPERSPEED_ID));
        PlayerProgressionService.setLevel(p, tier);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        if (inWorld) { p.setInvulnerable(true); h.getLevel().addNewPlayer(p); }
        return p;
    }

    private static void done(ServerPlayer p, boolean inWorld) {
        SpeedService.forget(p);
        com.jayptucker.heroesevolved.time.TimeRewindService.forget(p);
        if (inWorld) p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
    }

    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, ServerPlayer p, double dx, double dz) {
        return mob(h, type, p, dx, dz, false);
    }

    /** A mob with 100 health; without AI it stands still, but it also cannot be moved by being pushed. */
    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, ServerPlayer p, double dx, double dz, boolean ai) {
        T mob = type.create(h.getLevel());
        mob.setNoAi(!ai); mob.setPersistenceRequired();
        mob.moveTo(p.getX() + dx, p.getY(), p.getZ() + dz, 180, 0);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); mob.setHealth(100);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    @GameTest(template = "rewind_empty", batch = "speedrun.tornado", timeoutTicks = 140)
    public static void heldCycloneRisesIntoATornadoAndLetsGoOnRelease(GameTestHelper h) {
        var p = runner(h, 4, false);
        // A mob without AI is never moved by its velocity, so this one has its AI.
        var caught = mob(h, EntityType.VINDICATOR, p, 5, 0, true);
        double ground = caught.getY();
        h.assertTrue(SpeedKit.cyclone(p, 4, true), "The cyclone starts with the key held");
        h.assertTrue(!SpeedKit.tornado(p), "It is a ring at first");
        h.runAfterDelay(SpeedKit.TORNADO_HOLD + 2, () -> {
            h.assertTrue(SpeedKit.tornado(p), "Held long enough, the ring rises into a tornado");
            h.runAfterDelay(SpeedKit.TORNADO_ANCHOR - 22, () -> {
                try {
                    h.assertTrue(SpeedKit.cycloning(p), "A tornado outlasts the ring's three seconds while the key is held");
                    h.assertTrue(caught.getY() > ground + 3, "It carries what it caught up its funnel: " + (caught.getY() - ground));
                    h.assertTrue(caught.getHealth() < 100, "And hurts it");
                    h.assertTrue(PlayerEnergyService.getEnergy(p) < PlayerEnergyService.getMaximumEnergy(p), "Holding it costs stamina");
                    SpeedKit.releaseCyclone(p);
                    h.assertTrue(!SpeedKit.cycloning(p), "Letting go before it is anchored brings it down at once");
                    Vec3 out = caught.getDeltaMovement(), away = caught.position().subtract(p.position()).multiply(1, 0, 1).normalize();
                    h.assertTrue(out.dot(away) > 0.5, "And throws what it carried out of it: " + out);
                    h.succeed();
                } finally { caught.discard(); done(p, false); }
            });
        });
    }

    @GameTest(template = "rewind_empty", batch = "speedrun.tornado.anchored", timeoutTicks = 320)
    public static void anAnchoredTornadoStandsAloneDrawsThingsInAndTakesTheirAir(GameTestHelper h) {
        var p = runner(h, 4, false);
        Vec3 center = p.position();
        var near = mob(h, EntityType.VINDICATOR, p, 4, 0, true);
        // Well outside the funnel's reach (it holds what is within about seven blocks at the ground), inside its pull.
        var far = mob(h, EntityType.COW, p, 0, 12, true);
        h.assertTrue(SpeedKit.cyclone(p, 4, true), "The cyclone starts with the key held");
        h.runAfterDelay(SpeedKit.TORNADO_HOLD + SpeedKit.TORNADO_ANCHOR + 2, () -> {
            h.assertTrue(SpeedKit.tornado(p), "It rose");
            SpeedKit.releaseCyclone(p);
            h.assertTrue(SpeedKit.cycloning(p) && SpeedKit.standing(p), "Held three seconds after it rose, letting go leaves it standing on its own");
            h.assertTrue(!p.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "And the runner is free to go");
            h.runAfterDelay(60, () -> {
                h.assertTrue(far.position().distanceTo(center) < 9, "It draws things in from far off: " + far.position().distanceTo(center));
                h.assertTrue(near.getAirSupply() < near.getMaxAirSupply(), "What it carries cannot breathe: " + near.getAirSupply());
                h.runAfterDelay(120, () -> {
                    try {
                        h.assertTrue(SpeedKit.cycloning(p), "It stands far longer than the old ten seconds");
                        h.succeed();
                    } finally { near.discard(); far.discard(); done(p, false); }
                });
            });
        });
    }

    @GameTest(template = "rewind_empty", batch = "speedrun.tap", timeoutTicks = 100)
    public static void aQuickPressStaysARing(GameTestHelper h) {
        var p = runner(h, 4, false);
        h.assertTrue(SpeedKit.cyclone(p, 4, true), "The cyclone starts");
        h.runAfterDelay(5, () -> {
            SpeedKit.releaseCyclone(p);
            h.assertTrue(SpeedKit.cycloning(p), "Letting go early does not cut the ring short");
            h.runAfterDelay(SpeedKit.TORNADO_HOLD, () -> {
                h.assertTrue(SpeedKit.cycloning(p) && !SpeedKit.tornado(p), "It never rises");
                h.runAfterDelay(SpeedKit.CYCLONE_TICKS - SpeedKit.TORNADO_HOLD - 5 + 3, () -> {
                    try {
                        h.assertTrue(!SpeedKit.cycloning(p), "It breaks after its three seconds");
                        h.succeed();
                    } finally { done(p, false); }
                });
            });
        });
    }

    @GameTest(template = "rewind_empty", batch = "speedrun.ledge")
    public static void aRunnerStepsUpBlocksDropsStraightDownLedgesAndNeverTakesFallDamage(GameTestHelper h) {
        var p = runner(h, 1, false);
        try {
            SpeedService.toggle(p);
            SpeedService.tickRunner(p);
            h.assertTrue(p.getAttributeValue(Attributes.STEP_HEIGHT) >= 1.5, "Accelerate steps up a full block even between sprints: " + p.getAttributeValue(Attributes.STEP_HEIGHT));
            // Off an edge with the ground two blocks below: straight down onto it.
            double ground = p.getY();
            p.setPos(p.getX(), ground + 2, p.getZ());
            double drop = SpeedMovement.ledge(p);
            h.assertTrue(Math.abs(drop - 2) < .06, "A two-block drop goes straight down onto the lower ground: " + drop);
            p.setPos(p.getX(), ground + 6, p.getZ());
            h.assertTrue(SpeedMovement.ledge(p) == 0, "A real fall is left to fall");
            p.setPos(p.getX(), ground + .25, p.getZ());
            h.assertTrue(SpeedMovement.ledge(p) == 0, "A slab's lip is just walked");
            p.setPos(p.getX(), ground, p.getZ());
            var fall = new net.neoforged.neoforge.event.entity.living.LivingFallEvent(p, 40, 1);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(fall);
            h.assertTrue(fall.getDistance() == 0 && fall.getDamageMultiplier() == 0, "No fall hurts a speedster under Accelerate");
            SpeedService.toggle(p);
            h.assertTrue(p.getAttributeValue(Attributes.STEP_HEIGHT) < 1, "The step goes with Accelerate");
            h.succeed();
        } finally { done(p, false); }
    }

    @GameTest(template = "rewind_empty", batch = "speedrun.fire")
    public static void runningFlatOutPutsOutTheFlames(GameTestHelper h) {
        var p = runner(h, 2, false);
        try {
            p.setRemainingFireTicks(200);
            for (int i = 0; i < 20; i++) SpeedService.smotherForTest(p, .3);
            h.assertTrue(p.isOnFire(), "A jog does not put it out");
            for (int i = 0; i < 9; i++) SpeedService.smotherForTest(p, 1.2);
            h.assertTrue(p.isOnFire(), "It takes half a second of real running");
            SpeedService.smotherForTest(p, 1.2);
            h.assertFalse(p.isOnFire(), "Half a second flat out and the flames are out");
            h.succeed();
        } finally { done(p, false); }
    }

    @GameTest(template = "rewind_empty", batch = "speedrun.arms.push", timeoutTicks = 60)
    public static void windArmsBlowWhatIsAheadAwayAndNothingBehind(GameTestHelper h) {
        var p = runner(h, 4, true);
        p.setYRot(0); p.setXRot(0); p.setYHeadRot(0);
        var ahead = mob(h, EntityType.PILLAGER, p, 0, 5);
        var behind = mob(h, EntityType.PILLAGER, p, 0, -5);
        ahead.setRemainingFireTicks(200);
        h.assertTrue(SpeedWindArms.start(p), "The arms start spinning");
        h.runAfterDelay(4, () -> {
            try {
                h.assertTrue(ahead.getDeltaMovement().z > .2, "What is ahead is blown away along the look: " + ahead.getDeltaMovement());
                h.assertTrue(Math.abs(behind.getDeltaMovement().z) < .1, "Nothing behind is touched: " + behind.getDeltaMovement());
                h.assertTrue(ahead.getHealth() == 100, "The wind does no harm");
                h.assertFalse(ahead.isOnFire(), "And it blows out flames");
                h.assertTrue(SpeedWindArms.stop(p), "Letting go stops the arms");
                h.assertFalse(SpeedWindArms.active(p), "They are stopped");
                h.succeed();
            } finally { ahead.discard(); behind.discard(); done(p, true); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "speedrun.arms.lift", timeoutTicks = 60)
    public static void windArmsPointedDownLiftYouAndLetYouFloatDown(GameTestHelper h) {
        var p = runner(h, 4, true);
        p.setXRot(85);
        h.assertTrue(SpeedWindArms.start(p), "The arms start spinning");
        h.assertTrue(p.getDeltaMovement().y > .3, "Funnels into the ground lift you: " + p.getDeltaMovement());
        h.assertTrue(p.fallDistance == 0, "No fall builds while lifted");
        h.assertTrue(SpeedWindArms.stop(p), "Letting go stops the arms");
        h.runAfterDelay(1, () -> {
            try {
                h.succeed();
            } finally { done(p, true); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "speedrun.gust")
    public static void aSuddenStopThrowsAGustThatPushesCloseThingsBack(GameTestHelper h) {
        var p = runner(h, 3, false);
        try {
            SpeedService.toggle(p); p.setSprinting(true);
            // Run back and forth over the same ground, ending on a step toward +x.
            for (int i = 0; i < 21; i++) { p.setPos(p.position().add(i % 2 == 0 ? 1.5 : -1.5, 0, 0)); SpeedService.tickRunner(p); }
            var close = mob(h, EntityType.PILLAGER, p, 2, 0);
            var far = mob(h, EntityType.PILLAGER, p, 9, 0);
            close.setDeltaMovement(Vec3.ZERO); far.setDeltaMovement(Vec3.ZERO);
            // Stop dead. One still tick could be a move arriving late, so the gust comes on the second.
            p.setPos(p.position().add(0.05, 0, 0)); SpeedService.tickRunner(p);
            h.assertTrue(close.getDeltaMovement().lengthSqr() < 1.0E-6, "One still tick is not yet a stop");
            p.setPos(p.position().add(0.05, 0, 0)); SpeedService.tickRunner(p);
            try {
                h.assertTrue(close.getDeltaMovement().x > 0.25, "The wind shoves what is close on ahead: " + close.getDeltaMovement());
                h.assertTrue(far.getDeltaMovement().lengthSqr() < 1.0E-6, "Nothing out of reach is touched");
                h.assertTrue(close.getHealth() == 100, "It does no harm");
                // Standing still is not another stop.
                close.setDeltaMovement(Vec3.ZERO);
                for (int i = 0; i < 5; i++) SpeedService.tickRunner(p);
                h.assertTrue(close.getDeltaMovement().lengthSqr() < 1.0E-6, "One stop, one gust");
                h.succeed();
            } finally { close.discard(); far.discard(); }
        } finally { done(p, false); }
    }

    /** Runs flat out (back and forth) for one tick at simulated time {@code at}, then lets Overrun look. */
    private static void stride(ServerPlayer p, int i, long at) {
        if (i % 100 == 0) PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        p.setSprinting(true);
        p.setPos(p.position().add(i % 2 == 0 ? 1.2 : -1.2, 0, 0));
        SpeedService.tickRunner(p);
        SpeedOverrun.tickPlayer(p, at);
    }

    @GameTest(template = "rewind_empty", batch = "speedrun.overrun.charge", timeoutTicks = 40)
    public static void onlyTheFastestOutrunTheMinuteAndEasingOffClosesTheBreach(GameTestHelper h) {
        var slow = runner(h, 4, false);
        var fast = runner(h, 5, false);
        try {
            long base = h.getLevel().getServer().overworld().getGameTime() - 3000;
            SpeedService.toggle(slow); SpeedService.toggle(fast);
            int limit = SpeedOverrun.REWIND_TICKS + SpeedOverrun.CHARGE_TICKS + 40;
            for (int i = 0; i < limit && !SpeedOverrun.breachOpen(fast); i++) { stride(slow, i, base + i); stride(fast, i, base + i); }
            h.assertTrue(SpeedOverrun.breachOpen(fast), "Flat out long enough, with a minute behind them, the breach opens: charge " + SpeedOverrun.charge(fast));
            h.assertTrue(SpeedOverrun.charge(slow) == 0 && !SpeedOverrun.breachOpen(slow), "Below tier 5 nothing happens");
            // Ease off: stop running.
            fast.setSprinting(false);
            long at = base + limit;
            for (int i = 0; i < 14; i++) { SpeedService.tickRunner(fast); SpeedOverrun.tickPlayer(fast, at + i); }
            h.assertTrue(!SpeedOverrun.breachOpen(fast), "Easing off closes the breach");
            h.assertTrue(SpeedOverrun.charge(fast) <= SpeedOverrun.WAKE_TICKS, "And the wake drops back");
            h.succeed();
        } finally { done(slow, false); done(fast, false); }
    }

    @GameTest(template = "rewind_empty", batch = "speedrun.overrun.cross", timeoutTicks = 40)
    public static void outrunningTheLastMinuteRollsItBack(GameTestHelper h) {
        h.setBlock(6, 1, 6, Blocks.STONE);
        var p = runner(h, 5, true);
        var newcomer = (Mob) null;
        try {
            long now = h.getLevel().getServer().overworld().getGameTime();
            long base = now - SpeedOverrun.REWIND_TICKS - 100;
            Vec3 start = p.position();
            // A minute ago they stood here, empty-handed, the stone still in the ground.
            for (int i = 0; i <= 5; i++) SpeedOverrun.recordAt(p, base + i * SpeedOverrun.SAMPLE_TICKS);
            // Since then: they ran off, broke the stone, picked something up, and a pillager turned up.
            p.teleportTo(p.serverLevel(), start.x + 4, start.y, start.z + 1, 90, 0);
            h.getLevel().destroyBlock(h.absolutePos(new BlockPos(6, 1, 6)), false, p);
            p.getInventory().add(new ItemStack(Items.DIAMOND, 2));
            newcomer = mob(h, EntityType.PILLAGER, p, 2, 2);
            for (int i = 6; i <= 65; i++) SpeedOverrun.recordAt(p, base + i * SpeedOverrun.SAMPLE_TICKS);
            h.assertBlockPresent(Blocks.AIR, new BlockPos(6, 1, 6));

            h.assertTrue(SpeedOverrun.crossAt(p, now), "Through the breach");
            h.assertTrue(p.position().distanceToSqr(start) < 0.01, "They are back where they stood a minute ago: " + p.position());
            h.assertBlockPresent(Blocks.STONE, new BlockPos(6, 1, 6));
            h.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 0, "What they picked up since is not theirs yet");
            h.assertTrue(newcomer.isRemoved(), "What turned up since is not there yet");
            h.assertTrue(CooldownService.isOnCooldown(p, SpeedOverrun.ID), "Not again for a while");
            h.assertTrue(!SpeedOverrun.crossAt(p, now + 1), "The minute they outran is gone: there is nothing to go back to");
            h.succeed();
        } finally { if (newcomer != null) newcomer.discard(); done(p, true); }
    }

    /** A player who can die (FakePlayers normally cannot), optionally a tier-{@code tier} speedster, in the world. */
    private static ServerPlayer mortal(GameTestHelper h, String name, int tier) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), name)) {
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) { return false; }
            // FakePlayers skip death entirely; a real player's death starts with this event.
            @Override public void die(net.minecraft.world.damagesource.DamageSource source) { net.neoforged.neoforge.common.CommonHooks.onLivingDeath(this, source); }
            @Override public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) { absMoveTo(x, y, z, yaw, pitch); }
        };
        try {
            var grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true); grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3)))); p.setYRot(0); p.setXRot(0);
        if (tier > 0) {
            p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.SUPERSPEED_ID).unlock(ModAbilities.SUPERSPEED_ID));
            PlayerProgressionService.setLevel(p, tier);
        }
        h.getLevel().addNewPlayer(p);
        return p;
    }

    private static void record(ServerPlayer p, long base, int from, int to) {
        for (int i = from; i <= to; i++) SpeedOverrun.recordAt(p, base + (long) i * SpeedOverrun.SAMPLE_TICKS);
    }

    @GameTest(template = "rewind_empty", batch = "speedrun.overrun.placed", timeoutTicks = 40)
    public static void aBlockPlacedWhereYouStoodIsUndoneEvenAfterYouLeft(GameTestHelper h) {
        var p = runner(h, 5, true);
        try {
            long now = h.getLevel().getServer().overworld().getGameTime(), base = now - SpeedOverrun.REWIND_TICKS - 100;
            Vec3 start = p.position();
            record(p, base, 0, 5);
            // They ran off, well out of reach of the spot...
            p.teleportTo(p.serverLevel(), start.x + 90, start.y, start.z, 0, 0);
            record(p, base, 6, 10);
            // ...and then someone built where they had stood.
            h.setBlock(new BlockPos(6, 2, 3), Blocks.STONE);
            record(p, base, 11, 65);
            h.assertTrue(SpeedOverrun.crossAt(p, now), "Through the breach");
            h.assertBlockPresent(Blocks.AIR, new BlockPos(6, 2, 3));
            h.assertTrue(p.position().distanceToSqr(start) < 0.01, "Back where they stood: " + p.position());
            h.succeed();
        } finally { done(p, true); }
    }

    @GameTest(template = "rewind_empty", batch = "speedrun.overrun.friend", timeoutTicks = 40)
    public static void runningBackUndoesADeathAlongTheWay(GameTestHelper h) {
        var p = runner(h, 5, true);
        var friend = mortal(h, "friend", 0);
        ItemEntity drop = null;
        try {
            long now = h.getLevel().getServer().overworld().getGameTime(), base = now - SpeedOverrun.REWIND_TICKS - 100;
            Vec3 start = p.position(), far = start.add(100, 0, 0);
            // A minute ago the friend was a hundred blocks off, with three diamonds: out of the runner's reach then.
            friend.teleportTo(friend.serverLevel(), far.x, far.y, far.z, 0, 0);
            friend.getInventory().add(new ItemStack(Items.DIAMOND, 3));
            com.jayptucker.heroesevolved.time.TimeRewindPlayers.record(friend, base + 100);
            record(p, base, 0, 5);
            // The runner ran past them, and the friend died there.
            p.teleportTo(p.serverLevel(), far.x, far.y, far.z + 2, 0, 0);
            record(p, base, 6, 20);
            friend.hurt(h.getLevel().damageSources().generic(), 1000);
            drop = new ItemEntity(h.getLevel(), friend.getX(), friend.getY(), friend.getZ(), friend.getInventory().removeItem(0, 3));
            h.getLevel().addFreshEntity(drop);
            com.jayptucker.heroesevolved.time.TimeRewindPlayers.dropped(friend, java.util.List.of(drop));
            friend.getInventory().clearContent();
            // A new life somewhere else; and the runner ran on, far from both.
            friend.setHealth(friend.getMaxHealth());
            friend.teleportTo(friend.serverLevel(), far.x, far.y, far.z + 12, 0, 0);
            p.teleportTo(p.serverLevel(), start.x + 200, start.y, start.z, 0, 0);
            record(p, base, 21, 65);
            h.assertTrue(SpeedOverrun.crossAt(p, now), "Through the breach");
            h.assertTrue(friend.getInventory().countItem(Items.DIAMOND) == 3, "The friend has what they carried before they died");
            h.assertTrue(friend.position().distanceToSqr(far) < 0.01, "And stands where they stood: " + friend.position());
            h.assertTrue(drop.isRemoved(), "What their death dropped is gone again, so nothing is duplicated");
            h.succeed();
        } finally {
            if (drop != null) drop.discard();
            friend.serverLevel().removePlayerImmediately(friend, Entity.RemovalReason.DISCARDED);
            done(p, true);
        }
    }

    @GameTest(template = "rewind_empty", batch = "speedrun.overrun.death", timeoutTicks = 40)
    public static void runningBackUndoesYourOwnDeath(GameTestHelper h) {
        var p = mortal(h, "mortal-runner", 5);
        ItemEntity drop = null;
        try {
            long now = h.getLevel().getServer().overworld().getGameTime(), base = now - SpeedOverrun.REWIND_TICKS - 100;
            Vec3 start = p.position();
            p.getInventory().add(new ItemStack(Items.DIAMOND, 4));
            record(p, base, 0, 5);
            p.teleportTo(p.serverLevel(), start.x + 3, start.y, start.z, 0, 0);
            record(p, base, 6, 10);
            p.hurt(h.getLevel().damageSources().generic(), 1000);
            drop = new ItemEntity(h.getLevel(), p.getX(), p.getY(), p.getZ(), p.getInventory().removeItem(0, 4));
            h.getLevel().addFreshEntity(drop);
            com.jayptucker.heroesevolved.time.TimeRewindPlayers.dropped(p, java.util.List.of(drop));
            p.getInventory().clearContent();
            // A new life.
            p.setHealth(p.getMaxHealth());
            com.jayptucker.heroesevolved.time.TimeRewindService.respawned(p);
            p.teleportTo(p.serverLevel(), start.x, start.y, start.z + 6, 0, 0);
            record(p, base, 11, 65);
            h.assertTrue(SpeedOverrun.crossAt(p, now), "Their history survived the death, so the breach still leads back past it");
            h.assertTrue(p.position().distanceToSqr(start) < 0.01, "Back where they stood: " + p.position());
            h.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 4, "With what they carried then");
            h.assertTrue(drop.isRemoved(), "And their death's drops are gone again");
            h.succeed();
        } finally {
            if (drop != null) drop.discard();
            done(p, true);
        }
    }

    @GameTest(template = "rewind_empty", batch = "speedrun.hunger")
    public static void runningCostsLessHungerTheFurtherYouGrow(GameTestHelper h) {
        var p = runner(h, 1, false);
        try {
            PlayerProgressionService.setPowerLevel(p, 1);
            float start = SpeedService.runHunger(p);
            PlayerProgressionService.setPowerLevel(p, 15);
            float overrun = SpeedService.runHunger(p);
            PlayerProgressionService.setPowerLevel(p, 19);
            float late = SpeedService.runHunger(p);
            h.assertTrue(start > overrun && overrun > late, "Each level makes running cheaper: " + start + " > " + overrun + " > " + late);
            h.assertTrue(start <= 0.6F && overrun < 0.3F, "And never as dear as vanilla sprinting");
            // Twenty-five seconds flat out at Power Level 15 (about 52 blocks a second) costs under half a bar.
            float food = 25 * 52 * 0.1F * overrun / 4;
            h.assertTrue(food < 10, "A whole Overrun run costs " + food + " food points");
            h.succeed();
        } finally { done(p, false); }
    }
}
