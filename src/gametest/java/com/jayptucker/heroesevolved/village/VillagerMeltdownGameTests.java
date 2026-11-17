package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.radiation.NuclearFallout;
import com.jayptucker.heroesevolved.radiation.RadiationService;
import com.jayptucker.heroesevolved.radio.EmergencyBand;
import com.jayptucker.heroesevolved.speed.SpeedContainment;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * A villager the Eclipse woke a power in. Everything is staged high in the air over the test's own chunk, so a blast
 * can't touch the test structures below.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class VillagerMeltdownGameTests {
    private static Villager villager(GameTestHelper helper, int height, double dx) {
        Villager v = EntityType.VILLAGER.create(helper.getLevel());
        if (v == null) throw new AssertionError("No villager");
        v.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 0, 5))).add(dx, 0, 0).with(net.minecraft.core.Direction.Axis.Y, height));
        v.setNoGravity(true);
        v.setNoAi(false);
        helper.getLevel().addFreshEntity(v);
        return v;
    }

    private static ServerPlayer holder(GameTestHelper helper, Vec3 at, ResourceLocation power, int tier) {
        ServerPlayer p = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "meltdown-hero")) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
        };
        try {
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true); grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(at);
        p.setNoGravity(true);
        p.setGameMode(GameType.SURVIVAL);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, com.jayptucker.heroesevolved.ability.data.PlayerAbilityData.empty()
                .assignDormant(power).unlock(power));
        com.jayptucker.heroesevolved.progression.PlayerProgressionService.setLevel(p, tier);
        com.jayptucker.heroesevolved.energy.PlayerEnergyService.restore(p, 999);
        helper.getLevel().addNewPlayer(p);
        return p;
    }

    private static void cleanup(ServerPlayer p, Entity... entities) {
        for (Entity e : entities) { VillagerMeltdown.cancel(e); e.discard(); }
        if (p != null) {
            com.jayptucker.heroesevolved.speed.SpeedService.forget(p);
            p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 80, batch = "village.meltdown.blast")
    public static void theCountdownRunsOutAndHeGoesOff(GameTestHelper helper) {
        Villager him = villager(helper, 440, 0), neighbour = villager(helper, 440, 3);
        try {
            VillagerMeltdown.Meltdown m = VillagerMeltdown.start(him, 30);
            helper.assertTrue(m != null && VillagerMeltdown.critical(him), "A grown villager can be woken");
            helper.assertTrue(m.villageSize() == 1, "His village is everyone round the bell but him: " + m.villageSize());
            helper.assertTrue(EmergencyBand.live(m.call()), "His village calls for help on the emergency band");
            helper.assertTrue(him.getAttribute(Attributes.MOVEMENT_SPEED).getValue() <= 1.0E-6, "He can't walk away from it");
            helper.assertTrue(him.getPersistentData().getCompound(VillagerMeltdown.TAG).getInt("fuse") == 30, "His countdown is kept in his own data");
        } catch (RuntimeException e) { cleanup(null, him, neighbour); NuclearFallout.clear(); throw e; }
        helper.runAfterDelay(45, () -> {
            try {
                helper.assertTrue(!VillagerMeltdown.critical(him), "When the time runs out it is over");
                helper.assertTrue(!him.isAlive(), "He goes with it");
                helper.assertTrue(!neighbour.isAlive(), "And whoever stayed beside him");
                helper.succeed();
            } finally { cleanup(null, him, neighbour); NuclearFallout.clear(); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 260, batch = "village.meltdown.flee")
    public static void hisNeighboursScreamAndRun(GameTestHelper helper) {
        Villager him = EntityType.VILLAGER.create(helper.getLevel()), neighbour = EntityType.VILLAGER.create(helper.getLevel());
        him.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 6))));
        neighbour.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 6))));
        helper.getLevel().addFreshEntity(him);
        helper.getLevel().addFreshEntity(neighbour);
        double before = neighbour.distanceTo(him);
        // Someone to hear them: shouts are only for runners near a player.
        ServerPlayer witness = holder(helper, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(9, 4, 6))), ModAbilities.SUPERSPEED_ID, 1);
        // A meltdown pauses while its villager isn't ticking, and while the rest of the suite runs a freshly loaded chunk
        // can take a few seconds to start: begin once they're both live.
        whenTicking(helper, 150, () -> {
            if (!helper.getLevel().isPositionEntityTicking(him.blockPosition()) || !helper.getLevel().isPositionEntityTicking(neighbour.blockPosition()))
                return false;
            try {
                VillagerMeltdown.start(him, 2000);
            } catch (RuntimeException e) { cleanup(witness, him, neighbour); throw e; }
            flee(helper, him, neighbour, witness, before);
            return true;
        }, () -> cleanup(witness, him, neighbour));
    }

    /** Tries {@code step} each tick until it returns true, for up to {@code ticks}; then gives up and fails. */
    private static void whenTicking(GameTestHelper helper, int ticks, java.util.function.BooleanSupplier step, Runnable giveUp) {
        if (step.getAsBoolean()) return;
        if (ticks <= 0) {
            giveUp.run();
            helper.fail("The villagers' chunk never started ticking");
            return;
        }
        helper.runAfterDelay(1, () -> whenTicking(helper, ticks - 1, step, giveUp));
    }

    private static void flee(GameTestHelper helper, Villager him, Villager neighbour, ServerPlayer witness, double before) {
        helper.runAfterDelay(15, () -> {
            try {
                var target = neighbour.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
                helper.assertTrue(target.isPresent() && target.get().getSpeedModifier() >= 0.9F, "They break into a run, like Odessa's crowd");
                helper.assertTrue(target.get().getTarget().currentPosition().distanceTo(him.position()) > VillagerMeltdown.FLEE_RANGE,
                        "Away from him, out of reach");
                helper.assertTrue(VillagerMeltdown.shouting(neighbour).contains("It's happening again!"),
                        "And the first of them shouts it: " + VillagerMeltdown.shouting(neighbour));
            } catch (RuntimeException e) { cleanup(witness, him, neighbour); throw e; }
        });
        helper.runAfterDelay(80, () -> {
            try {
                helper.assertTrue(neighbour.distanceTo(him) > before + 2, "And run from him: " + before + " -> " + neighbour.distanceTo(him));
                helper.succeed();
            } finally { cleanup(witness, him, neighbour); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "village.meltdown.hurt")
    public static void hurtingHimOnlyMakesItWorse(GameTestHelper helper) {
        Villager him = villager(helper, 455, 0);
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        try {
            VillagerMeltdown.Meltdown m = VillagerMeltdown.start(him, 1000);
            int before = m.fuse();
            float health = him.getHealth();
            him.hurt(helper.getLevel().damageSources().mobAttack(zombie), 4);
            helper.assertTrue(him.getHealth() == health, "Blows don't hurt him");
            helper.assertTrue(m.fuse() <= before - VillagerMeltdown.HIT_PENALTY, "They take time off the countdown: " + m.fuse());
            helper.succeed();
        } finally { cleanup(null, him); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 280, batch = "village.meltdown.speed")
    public static void aSpeedsterHoldsHimInAndHeLives(GameTestHelper helper) {
        Villager him = villager(helper, 470, 0);
        ServerPlayer runner = holder(helper, him.position().add(4, 0, 0), ModAbilities.SUPERSPEED_ID, 5);
        try {
            com.jayptucker.heroesevolved.radiation.MeltdownFieldGameTests.suit(runner, true);
            VillagerMeltdown.start(him, 400);
            helper.assertTrue(SpeedContainment.start(runner, 5), "The Cyclone key near him starts a containment run");
            helper.assertTrue(SpeedContainment.containing(runner) || SpeedContainment.running(runner), "Round him");
        } catch (RuntimeException e) { cleanup(runner, him); throw e; }
        helper.runAfterDelay(60, () -> {
            try {
                VillagerMeltdown.Meltdown m = VillagerMeltdown.of(him);
                helper.assertTrue(m != null && m.fuse() >= 395, "While the run holds him, his countdown doesn't run: " + (m == null ? "over" : m.fuse()));
            } catch (RuntimeException e) { cleanup(runner, him); throw e; }
        });
        helper.runAfterDelay(SpeedContainment.DEFUSE_TICKS + 25, () -> {
            try {
                helper.assertTrue(!VillagerMeltdown.critical(him), "Held ten seconds, the heat is gone out of him");
                helper.assertTrue(him.isAlive(), "And he lives");
                helper.assertTrue(him.getAttribute(Attributes.MOVEMENT_SPEED).getValue() > 0, "Free to walk again");
                helper.succeed();
            } finally { cleanup(runner, him); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 240, batch = "village.meltdown.draw")
    public static void aRadioactivePlayerDrawsTheHeatIntoThemselves(GameTestHelper helper) {
        Villager him = villager(helper, 485, 0);
        ServerPlayer drawer = holder(helper, him.position().add(1.5, 0, 0), ModAbilities.RADIATION_ID, 3);
        try {
            VillagerMeltdown.start(him, 600);
            drawer.setShiftKeyDown(true);
            drawer.setPose(Pose.CROUCHING);
        } catch (RuntimeException e) { cleanup(drawer, him); throw e; }
        helper.runAfterDelay(VillagerMeltdown.DRAW_TICKS + 15, () -> {
            try {
                helper.assertTrue(!VillagerMeltdown.critical(him), "Crouched beside him long enough, they draw it all out");
                helper.assertTrue(him.isAlive(), "He lives");
                helper.assertTrue(RadiationService.mass(drawer) >= RadiationService.HOT, "And they run hot with it: " + RadiationService.mass(drawer));
                helper.succeed();
            } finally { cleanup(drawer, him); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "village.meltdown.safe")
    public static void villagersCarriedToTheSafePointCountAndStay(GameTestHelper helper) {
        Villager him = villager(helper, 500, 0), neighbour = villager(helper, 500, 3);
        VillagerMeltdown.Meltdown m = VillagerMeltdown.start(him, 1000);
        var chunk = new net.minecraft.world.level.ChunkPos(BlockPos.containing(m.safe()));
        helper.getLevel().setChunkForced(chunk.x, chunk.z, true);
        try {
            helper.getLevel().getChunk(chunk.x, chunk.z);
            helper.assertTrue(m.safe().subtract(him.position()).horizontalDistance() >= VillagerMeltdown.SAFE_DISTANCE - 1,
                    "The safe point is out of the blast's reach, with room to spare: " + m.safe().subtract(him.position()).horizontalDistance());
            neighbour.teleportTo(m.safe().x, m.safe().y, m.safe().z);
        } catch (RuntimeException e) { helper.getLevel().setChunkForced(chunk.x, chunk.z, false); cleanup(null, him, neighbour); throw e; }
        helper.runAfterDelay(25, () -> {
            try {
                helper.assertTrue(m.safeCount() == 1, "A villager at the safe point counts as safe: " + m.safeCount());
                helper.assertTrue(neighbour.getAttribute(Attributes.MOVEMENT_SPEED).getValue() <= 1.0E-6, "And stays huddled there");
                helper.succeed();
            } finally { helper.getLevel().setChunkForced(chunk.x, chunk.z, false); cleanup(null, him, neighbour); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 120, batch = "village.meltdown.unsuited")
    public static void aSpeedsterWithNoSuitBurnsAwayAndHeStillGoesOff(GameTestHelper helper) {
        Villager him = villager(helper, 515, 0);
        ServerPlayer runner = holder(helper, him.position().add(4, 0, 0), ModAbilities.SUPERSPEED_ID, 5);
        try {
            VillagerMeltdown.start(him, 400);
            helper.assertTrue(SpeedContainment.start(runner, 5), "The Cyclone key near him still starts a run");
        } catch (RuntimeException e) { cleanup(runner, him); throw e; }
        helper.runAfterDelay(SpeedContainment.UNSUITED_TICKS + 10, () -> {
            try {
                helper.assertTrue(SpeedContainment.burningAway(runner), "Without the velocity suit the heat goes straight through them");
                helper.assertTrue(VillagerMeltdown.critical(him), "And they didn't hold it long enough to stop it");
                helper.succeed();
            } finally { runner.setInvulnerable(false); cleanup(runner, him); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 80, batch = "village.meltdown.count")
    public static void everyoneWhoBelongsCountsEvenLateComers(GameTestHelper helper) {
        Villager him = villager(helper, 530, 0), neighbour = villager(helper, 530, 3);
        Villager[] late = new Villager[1];
        VillagerMeltdown.Meltdown m;
        try {
            m = VillagerMeltdown.start(him, 1000);
            helper.assertTrue(m.villageSize() == 1, "His neighbour is his village: " + m.villageSize());
            late[0] = villager(helper, 530, 6);
        } catch (RuntimeException e) { cleanup(null, him, neighbour); throw e; }
        helper.runAfterDelay(VillagerMeltdown.GATHER_EVERY + 5, () -> {
            try {
                helper.assertTrue(m.villageSize() == 2 && m.inVillage(late[0]), "Someone who wanders in later counts too: " + m.villageSize());
                helper.assertTrue(neighbour.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "Those still in danger glow, to be found");
                helper.assertTrue(VillagerMeltdown.survivors(m) == 2, "Both are alive");
                java.util.UUID gone = java.util.UUID.randomUUID();
                m.village.add(gone);
                helper.assertTrue(VillagerMeltdown.survivors(m) == 3, "Someone off in an unloaded chunk isn't counted as dead");
                m.dead.add(gone);
                helper.assertTrue(VillagerMeltdown.survivors(m) == 2, "Someone seen dying is");
                helper.succeed();
            } finally { cleanup(null, him, neighbour, late[0]); }
        });
    }

    // ------------------------------------------------------------------
    // A speedster at each level running rings round him, through the real Cyclone key
    // ------------------------------------------------------------------

    /**
     * A speedster at {@code tier} four blocks from him, wearing the first {@code pieces} of the velocity suit (suit, then
     * leggings, boots, cowl), with their power's awakening already seen so the key answers.
     */
    private static ServerPlayer runner(GameTestHelper helper, Villager him, int tier, int pieces) {
        ServerPlayer p = holder(helper, him.position().add(4, 0, 0), ModAbilities.SUPERSPEED_ID, tier);
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.SUPERSPEED_ID));
        com.jayptucker.heroesevolved.energy.PlayerEnergyService.restore(p, com.jayptucker.heroesevolved.energy.PlayerEnergyService.getMaximumEnergy(p));
        net.minecraft.world.item.Item[] items = {com.jayptucker.heroesevolved.hazard.SuitItems.VELOCITY_SUIT.get(),
                com.jayptucker.heroesevolved.hazard.SuitItems.VELOCITY_LEGGINGS.get(), com.jayptucker.heroesevolved.hazard.SuitItems.VELOCITY_BOOTS.get(),
                com.jayptucker.heroesevolved.hazard.SuitItems.VELOCITY_COWL.get()};
        net.minecraft.world.entity.EquipmentSlot[] slots = {net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET, net.minecraft.world.entity.EquipmentSlot.HEAD};
        for (int i = 0; i < pieces; i++) p.setItemSlot(slots[i], new net.minecraft.world.item.ItemStack(items[i]));
        return p;
    }

    /** Presses the Cyclone key, as a player would. */
    private static com.jayptucker.heroesevolved.ability.AbilityUseResult cyclone(ServerPlayer p) {
        return com.jayptucker.heroesevolved.ability.service.AbilityUseService.activateAssignedAction(p,
                com.jayptucker.heroesevolved.ability.AbilitySlot.QUATERNARY, false);
    }

    private static boolean pressed(com.jayptucker.heroesevolved.ability.AbilityUseResult r) {
        return r == com.jayptucker.heroesevolved.ability.AbilityUseResult.SUCCESS
                || r == com.jayptucker.heroesevolved.ability.AbilityUseResult.OVEREXERTED_SUCCESS;
    }

    /**
     * One run round him: a runner at {@code tier} in {@code pieces} of the suit presses the key; {@code spent} drains their
     * stamina and leaves them {@code health} just after. Four and a half seconds in, {@code during} sees the run (an
     * unsuited runner is burning away by then); just after the ten-second hold, {@code check} sees how it ended.
     */
    private static void run(GameTestHelper helper, int height, int tier, int pieces, boolean spent, float health,
                            java.util.function.BiConsumer<ServerPlayer, Villager> during,
                            java.util.function.BiConsumer<ServerPlayer, Villager> check) {
        Villager him = villager(helper, height, 0);
        ServerPlayer p = runner(helper, him, tier, pieces);
        try {
            VillagerMeltdown.start(him, 600);
            var result = cyclone(p);
            helper.assertTrue(pressed(result), "A tier-" + tier + " speedster's Cyclone key near him starts a run: " + result);
            helper.assertTrue(SpeedContainment.running(p), "And they are out on the ring");
            if (spent) com.jayptucker.heroesevolved.energy.PlayerEnergyService.tryConsume(p,
                    com.jayptucker.heroesevolved.energy.PlayerEnergyService.getEnergy(p));
            if (health > 0) p.setHealth(health);
        } catch (RuntimeException e) { cleanup(p, him); throw e; }
        helper.runAfterDelay(SpeedContainment.UNSUITED_TICKS + 20, () -> {
            try { during.accept(p, him); }
            catch (RuntimeException e) { p.setInvulnerable(false); cleanup(p, him); throw e; }
        });
        helper.runAfterDelay(SpeedContainment.DEFUSE_TICKS + 25, () -> {
            try {
                check.accept(p, him);
                helper.succeed();
            } finally { p.setInvulnerable(false); cleanup(p, him); }
        });
    }

    /** Holding him in: running, not burning, and he isn't counting down. */
    private static void holding(GameTestHelper helper, ServerPlayer p, Villager him) {
        helper.assertTrue(SpeedContainment.containing(p) && !SpeedContainment.burningAway(p), "Four seconds in, they're holding him in");
        helper.assertTrue(VillagerMeltdown.critical(him), "Not done yet");
    }

    /** Burning away on the ring. */
    private static void burning(GameTestHelper helper, ServerPlayer p, String why) {
        helper.assertTrue(SpeedContainment.burningAway(p), why);
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "village.meltdown.tier2")
    public static void belowTierThreeTheCycloneKeyIsLocked(GameTestHelper helper) {
        Villager him = villager(helper, 545, 0);
        ServerPlayer p = runner(helper, him, 2, 4);
        try {
            VillagerMeltdown.start(him, 600);
            var result = cyclone(p);
            helper.assertTrue(result == com.jayptucker.heroesevolved.ability.AbilityUseResult.ACTION_LOCKED && !SpeedContainment.running(p),
                    "A tier-2 speedster can't run rings round him yet, suit or not: " + result);
            helper.assertTrue(VillagerMeltdown.critical(him), "And he is still going to blow");
            helper.succeed();
        } finally { cleanup(p, him); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 300, batch = "village.meltdown.tier3")
    public static void aSuitedTierThreeSpeedsterHoldsHimIn(GameTestHelper helper) {
        run(helper, 635, 3, 4, false, 0, (p, him) -> holding(helper, p, him), (p, him) -> {
            helper.assertTrue(!VillagerMeltdown.critical(him) && him.isAlive(), "The first tier with the Cyclone key, in the suit: he's cool and alive");
            helper.assertTrue(p.isAlive() && !SpeedContainment.burningAway(p), "And the runner comes out of it (health " + p.getHealth() + ")");
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 300, batch = "village.meltdown.tier4")
    public static void aSuitedTierFourSpeedsterHoldsHimIn(GameTestHelper helper) {
        run(helper, 560, 4, 4, false, 0, (p, him) -> holding(helper, p, him), (p, him) -> {
            helper.assertTrue(!VillagerMeltdown.critical(him) && him.isAlive(), "Five seconds held at tier 4, in the suit: he's cool and alive");
            helper.assertTrue(p.isAlive() && !SpeedContainment.burningAway(p), "And the runner comes out of it (health " + p.getHealth() + ")");
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 300, batch = "village.meltdown.tier5")
    public static void aSuitedTierFiveSpeedsterHoldsHimInUnhurt(GameTestHelper helper) {
        run(helper, 575, 5, 4, false, 0, (p, him) -> holding(helper, p, him), (p, him) -> {
            helper.assertTrue(!VillagerMeltdown.critical(him) && him.isAlive(), "Held at tier 5, in the suit: he's cool and alive");
            helper.assertTrue(p.getHealth() >= p.getMaxHealth(), "A rested tier-5 runner pays it all in stamina, unhurt (health " + p.getHealth() + ")");
            helper.assertTrue(com.jayptucker.heroesevolved.hazard.BodyBurn.level(p) == 0, "And the suit kept every bit of burn off");
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 300, batch = "village.meltdown.tier4bare")
    public static void anUnsuitedTierFourSpeedsterBurnsAwayAndHeGoesOn(GameTestHelper helper) {
        run(helper, 590, 4, 0, false, 0, (p, him) -> burning(helper, p, "No suit: the heat goes straight through them on the ring"),
                (p, him) -> helper.assertTrue(VillagerMeltdown.critical(him), "And they didn't hold it long enough: he's still going to blow"));
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 300, batch = "village.meltdown.tier5bare")
    public static void evenATierFiveSpeedsterNeedsTheSuit(GameTestHelper helper) {
        run(helper, 650, 5, 0, false, 0, (p, him) -> burning(helper, p, "Tier 5 doesn't save a runner with no suit"),
                (p, him) -> helper.assertTrue(VillagerMeltdown.critical(him), "And the blast goes on"));
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 300, batch = "village.meltdown.partial")
    public static void threePiecesOfTheSuitAreNotEnough(GameTestHelper helper) {
        run(helper, 605, 5, 3, false, 0, (p, him) -> burning(helper, p, "Without the cowl the suit isn't friction-proof, even at tier 5"),
                (p, him) -> helper.assertTrue(VillagerMeltdown.critical(him), "So the blast goes on"));
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 300, batch = "village.meltdown.spent")
    public static void aSuitedRunnerWhoseBodyGivesOutStillStopsIt(GameTestHelper helper) {
        run(helper, 620, 4, 4, true, 3, (p, him) -> {
            helper.assertTrue(SpeedContainment.doomed(p) && !SpeedContainment.burningAway(p),
                    "Out of stamina and nearly out of health, their body gives out, but they run on");
            helper.assertTrue(VillagerMeltdown.critical(him), "Still holding him in: the moment isn't over");
        }, (p, him) -> {
            burning(helper, p, "When the hold is done, it takes them");
            helper.assertTrue(!VillagerMeltdown.critical(him) && him.isAlive(), "And they take it with them: he's cool and alive");
        });
    }
}
