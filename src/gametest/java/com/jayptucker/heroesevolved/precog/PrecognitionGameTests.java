package com.jayptucker.heroesevolved.precog;

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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Danger Sense and Clarity, Saw It Coming, Painted Endings, The Future Is Written and the Evolved trial. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class PrecognitionGameTests {
    private static ServerPlayer seer(GameTestHelper h, int tier) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "seer-test")) {
            @Override public boolean isInvulnerableTo(DamageSource s) { return false; }
            @Override public void die(DamageSource source) { net.neoforged.neoforge.common.CommonHooks.onLivingDeath(this, source); }
            @Override public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) { absMoveTo(x, y, z, yaw, pitch); }
            @Override public void teleportTo(double x, double y, double z) { absMoveTo(x, y, z); }
        };
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
        p.setYRot(0); p.setXRot(0);
        var id = ModAbilities.PRECOGNITION_ID;
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(id).unlock(id));
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(id));
        PlayerProgressionService.setLevel(p, tier);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        try { var field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime"); field.setAccessible(true); field.setInt(p, 0); }
        catch (Exception ex) { throw new AssertionError(ex); }
        return p;
    }

    private static LivingEntity enemy(GameTestHelper h, ServerPlayer p, double distance) {
        var t = EntityType.PILLAGER.create(h.getLevel());
        t.setNoAi(true); t.setPersistenceRequired();
        t.moveTo(p.getX(), p.getY(), p.getZ() + distance, 180, 0);
        t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); t.setHealth(100);
        h.getLevel().addFreshEntity(t);
        return t;
    }

    /** An arrow already in flight at {@code p}'s chest from {@code distance} blocks ahead, owned by nobody. */
    private static Arrow shotAt(GameTestHelper h, ServerPlayer p, double distance) {
        var arrow = new Arrow(EntityType.ARROW, h.getLevel());
        arrow.setNoGravity(true);
        arrow.setPos(p.getX(), p.getY() + 1, p.getZ() + distance);
        arrow.setDeltaMovement(0, 0, -1.2);
        h.getLevel().addFreshEntity(arrow);
        return arrow;
    }

    @GameTest(template = "rewind_empty", batch = "precog.sense")
    public static void dangerSenseForeseesAShotAndAMissEarnsClarity(GameTestHelper h) {
        var p = seer(h, 1);
        var arrow = shotAt(h, p, 8);
        var away = new Arrow(EntityType.ARROW, h.getLevel());
        away.setNoGravity(true); away.setPos(p.getX() + 3, p.getY() + 1, p.getZ() + 8); away.setDeltaMovement(0, 0, 1.2);
        h.getLevel().addFreshEntity(away);
        try {
            h.assertTrue(Foresight.predictHit(arrow, p.getBoundingBox().inflate(0.35)).isPresent(), "A shot on course is foreseen");
            h.assertTrue(Foresight.predictHit(away, p.getBoundingBox().inflate(0.35)).isEmpty(), "A shot flying away is not");
            Foresight.scan(p);
            h.assertTrue(Foresight.foreseen(p) == 1, "Exactly one harm is on its way");
            // The shot never arrives: the future changed.
            arrow.discard();
        } finally { away.discard(); }
        h.runAfterDelay(2, () -> {
            h.assertTrue(Foresight.foreseen(p) == 0, "The threat is resolved");
            h.assertTrue(Foresight.clarity(p) == Foresight.PROJECTILE_CLARITY, "A foreseen shot that missed earns Clarity");
            h.assertTrue(EvolutionTrials.progress(p, "precognition") == 1, "Changing the future counts toward the Evolved trial");
            PrecognitionService.forget(p);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "precog.arc", timeoutTicks = 60)
    public static void anArcingArrowIsForeseenFromTheMomentItFlies(GameTestHelper h) {
        var p = seer(h, 1);
        var arrow = new Arrow(EntityType.ARROW, h.getLevel());
        arrow.setPos(p.getX() - 1, p.getY() + 1.5, p.getZ() + 7);
        // Aim like an archer: the flattest lob that the simulation says lands on the chest.
        Vec3 to = p.position().add(0, 1.2, 0).subtract(arrow.position());
        Vec3 flat = to.multiply(1, 0, 1).normalize().scale(0.9);
        var chest = p.getBoundingBox().deflate(0.25, 0.6, 0.25).move(0, 0.3, 0);
        for (double lift = -0.2; lift < 0.8; lift += 0.01) {
            arrow.setDeltaMovement(flat.x, lift, flat.z);
            if (Foresight.predictHit(arrow, chest).isPresent()) break;
        }
        h.getLevel().addFreshEntity(arrow);
        var predicted = Foresight.predictHit(arrow, p.getBoundingBox().inflate(0.35));
        // A FakePlayer is not in the world, so the arrow cannot strike it: watch whether its real
        // flight passes through where the player stands instead.
        var body = p.getBoundingBox().inflate(0.35);
        Vec3[] last = {arrow.position()};
        boolean[] passed = {false};
        h.onEachTick(() -> {
            if (arrow.isRemoved()) return;
            if (body.clip(last[0], arrow.position()).isPresent() || body.contains(arrow.position())) passed[0] = true;
            last[0] = arrow.position();
        });
        h.runAfterDelay(40, () -> {
            boolean hit = passed[0];
            arrow.discard(); PrecognitionService.forget(p);
            h.assertTrue(hit && predicted.isPresent(), "The prediction must match what the arrow really did: predicted="
                    + predicted.isPresent() + ", hit=" + hit);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "precog.wall")
    public static void aShotBehindAWallIsNotComing(GameTestHelper h) {
        var p = seer(h, 1);
        BlockPos wall = BlockPos.containing(p.getX(), p.getY() + 1, p.getZ() + 3);
        h.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        var arrow = shotAt(h, p, 8);
        try {
            h.assertTrue(Foresight.predictHit(arrow, p.getBoundingBox().inflate(0.35)).isEmpty(), "Stone between the shot and you: nothing is coming");
            h.succeed();
        } finally { arrow.discard(); h.getLevel().setBlock(wall, Blocks.AIR.defaultBlockState(), 3); PrecognitionService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "precog.read")
    public static void sawItComingDodgesTheNextBlowAndExposesTheAttacker(GameTestHelper h) {
        var p = seer(h, 3);
        var t = enemy(h, p, 2);
        try {
            Vec3 start = p.position();
            h.assertTrue(PrecognitionService.sawItComing(p), "The read starts");
            float health = p.getHealth();
            p.hurt(p.damageSources().mobAttack(t), 6);
            h.assertTrue(p.getHealth() == health, "The blow you saw coming misses");
            h.assertTrue(p.position().distanceToSqr(start) > 0.5, "You are already a step aside");
            h.assertTrue(PrecognitionService.exposed(t) && Foresight.clarity(p) == 20, "The attacker is Exposed and you gain Clarity");
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().mobAttack(t), 6);
            h.assertTrue(p.getHealth() < health, "One read stops one blow");
            float before = t.getHealth();
            t.hurt(p.damageSources().playerAttack(p), 4);
            h.assertTrue(before - t.getHealth() >= 5.9F, "Your next hit on an Exposed enemy deals half again");
            h.assertTrue(!PrecognitionService.exposed(t), "Exposed is spent by that hit");
            h.succeed();
        } finally { t.discard(); PrecognitionService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "precog.ending")
    public static void aPaintedEndingComesTrueAtAQuarterHealth(GameTestHelper h) {
        var p = seer(h, 2);
        var t = enemy(h, p, 3);
        try {
            PrecognitionService.paintEnding(p, t);
            h.assertTrue(PrecognitionService.painted(t), "Their ending is painted");
            float before = t.getHealth();
            t.hurt(p.damageSources().playerAttack(p), 10);
            h.assertTrue(Math.abs(before - t.getHealth() - 12) < 0.1F, "Everyone hurts a painted enemy a fifth more");
            t.setHealth(30); t.invulnerableTime = 0;
            t.hurt(p.damageSources().playerAttack(p), 5);
            h.assertTrue(t.isDeadOrDying(), "Below a quarter of its health the painted moment arrives");
            h.assertTrue(Foresight.clarity(p) == 15, "A painting that comes true gives Clarity");
            h.succeed();
        } finally { t.discard(); PrecognitionService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "precog.ending_clarity")
    public static void clarityRaisesWhereThePaintingComesTrue(GameTestHelper h) {
        var p = seer(h, 2);
        var t = enemy(h, p, 3);
        try {
            Foresight.addClarity(p, 80);
            PrecognitionService.paintEnding(p, t);
            h.assertTrue(Foresight.clarity(p) == 30, "A painting spends up to 50 Clarity");
            t.setHealth(48); t.invulnerableTime = 0;
            t.hurt(p.damageSources().playerAttack(p), 1);
            h.assertTrue(t.isDeadOrDying(), "With 50 Clarity spent it comes true at half health");
            h.succeed();
        } finally { t.discard(); PrecognitionService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "precog.paint", timeoutTicks = 80)
    public static void paintingAnEnemyInViewPaintsTheirEnding(GameTestHelper h) {
        var p = seer(h, 2);
        var t = enemy(h, p, 3);
        h.assertTrue(PrecognitionService.paint(p) && PrecognitionService.painting(p), "The trance begins");
        h.assertTrue(!PrecognitionService.paint(p), "One canvas at a time");
        h.runAfterDelay(35, () -> {
            try {
                h.assertTrue(!PrecognitionService.painting(p) && PrecognitionService.painted(t), "After the trance their ending is painted");
                h.succeed();
            } finally { t.discard(); PrecognitionService.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "precog.written")
    public static void theFutureIsWrittenSpendsClarityForTimeAhead(GameTestHelper h) {
        var p = seer(h, 5);
        var t = enemy(h, p, 2);
        try {
            Foresight.addClarity(p, 60);
            h.assertTrue(PrecognitionService.writeTheFuture(p) && PrecognitionService.futureWritten(p), "The future is written");
            h.assertTrue(Foresight.clarity(p) == 0, "It spends all Clarity");
            h.assertTrue(!PrecognitionService.writeTheFuture(p), "It cannot be written twice at once");
            float health = p.getHealth();
            int energy = PlayerEnergyService.getEnergy(p);
            p.hurt(p.damageSources().mobAttack(t), 6);
            h.assertTrue(p.getHealth() == health && PlayerEnergyService.getEnergy(p) < energy, "Every blow is sidestepped, for a little stamina");
            h.assertTrue(PrecognitionService.exposed(t), "The attacker is Exposed by the dodge");
            float before = t.getHealth();
            t.hurt(p.damageSources().playerAttack(p), 4);
            h.assertTrue(Math.abs(before - t.getHealth() - 7.8F) < 0.1F, "Exposed (half again) and living ahead (30% more) stack: 4 becomes 7.8");
            PrecognitionService.forget(p);
            h.assertTrue(!PrecognitionService.futureWritten(p), "Forgetting the power ends it");
            h.succeed();
        } finally { t.discard(); PrecognitionService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "precog.trial")
    public static void precognitionHasItsOwnEvolvedTrial(GameTestHelper h) {
        var trial = EvolutionTrials.trial("precognition");
        h.assertTrue(EvolutionTrials.special("precognition") && trial.goal() == 50, "Precognition evolves by changing the future fifty times");
        h.succeed();
    }
}
