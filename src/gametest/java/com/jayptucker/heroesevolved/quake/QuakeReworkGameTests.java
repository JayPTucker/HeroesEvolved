package com.jayptucker.heroesevolved.quake;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Quake rework: blasts shaking bullets to dust and setting off explosives, the follow-ups (Double Tap, Shatter,
 * Upheaval, an early aftershock), the Vibration Shell, Seismic Lance, Pressure Step, and the passive nodes.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class QuakeReworkGameTests {
    private static ServerPlayer daisy(GameTestHelper h, int tier) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "quake-rework")) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
            @Override public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
                super.teleportTo(level, x, y, z, yaw, pitch); absMoveTo(x, y, z, yaw, pitch);
            }
        };
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 2)))); p.setYRot(0); p.setXRot(0);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.QUAKE_ID).unlock(ModAbilities.QUAKE_ID));
        PlayerProgressionService.setLevel(p, tier);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        p.setOnGround(true);
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

    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 12; x++) for (int z = 0; z < 12; z++)
            h.getLevel().setBlock(h.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
    }

    private static void learn(ServerPlayer p, String... ids) {
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.QUAKE_ID.toString(),
                new SkillTreeData.Build(15, Set.of(ids), Map.of())));
    }

    /** A bullet at eye height, {@code dz} ahead of her, flying along z at {@code speed}. */
    private static Entity bullet(GameTestHelper h, ServerPlayer p, double dz, double speed, float explosive) {
        Entity bullet = PowerWeapons.testBullet(h.getLevel(), explosive);
        h.assertTrue(bullet != null, "TaCZ can make a bullet");
        bullet.setPos(p.getX(), p.getEyeY(), p.getZ() + dz);
        bullet.setDeltaMovement(0, 0, speed);
        bullet.setNoGravity(true);
        h.getLevel().addFreshEntity(bullet);
        return bullet;
    }

    private static void done(ServerPlayer p) { QuakeService.stopAll(p); QuakeKit.forget(p); }

    @GameTest(template = "rewind_empty", batch = "quakerework.bullets")
    public static void aShockwaveShakesIncomingBulletsToDust(GameTestHelper h) {
        var p = daisy(h, 3);
        Entity incoming = bullet(h, p, 5, -3, 0), outgoing = bullet(h, p, 4, 3, 0);
        try {
            QuakeService.shockwave(p, 3);
            h.assertTrue(incoming.isRemoved(), "A bullet coming at her shakes apart");
            h.assertTrue(!outgoing.isRemoved(), "A bullet flying away (a friend's shot) is left alone");
            h.succeed();
        } finally { incoming.discard(); outgoing.discard(); done(p); }
    }

    @GameTest(template = "rewind_empty", batch = "quakerework.explosives")
    public static void explosivesGoOffWhereTheWaveMeetsThem(GameTestHelper h) {
        var p = daisy(h, 3);
        Entity round = bullet(h, p, 6, 3, 2);
        PrimedTnt tnt = new PrimedTnt(h.getLevel(), p.getX() + 0.4, p.getEyeY() - 0.5, p.getZ() + 4, null);
        tnt.setFuse(200);
        tnt.setNoGravity(true);
        h.getLevel().addFreshEntity(tnt);
        Creeper creeper = mob(h, EntityType.CREEPER, p, -0.4, 3);
        try {
            QuakeService.shockwave(p, 3);
            h.assertTrue(round.isRemoved(), "An explosive round goes off, even one flying away from her");
            h.assertTrue(tnt.isRemoved(), "Primed TNT goes off at once instead of waiting out its fuse");
            h.assertTrue(creeper.isIgnited() || creeper.isRemoved(), "A creeper's fuse is lit");
            h.succeed();
        } finally { round.discard(); tnt.discard(); creeper.discard(); done(p); }
    }

    @GameTest(template = "rewind_empty", batch = "quakerework.beam", timeoutTicks = 40)
    public static void quakeBlastCatchesBulletsEveryTick(GameTestHelper h) {
        var p = daisy(h, 4);
        Entity incoming = bullet(h, p, 6, -0.5, 0);
        // The beam's own tick isn't reached for a player outside the player list; its shot screen is the same call.
        QuakeService.catchShots(p, p.getEyePosition(), p.getLookAngle(), 10);
        try {
            h.assertTrue(incoming.isRemoved(), "The beam shakes a bullet on its line apart");
            h.succeed();
        } finally { incoming.discard(); done(p); }
    }

    @GameTest(template = "rewind_empty", batch = "quakerework.doubletap", timeoutTicks = 40)
    public static void doubleTapAnswersOnceWithTheOtherPalm(GameTestHelper h) {
        var p = daisy(h, 3);
        p.setXRot(90);
        h.assertTrue(!QuakeService.doubleTap(p), "No Double Tap without a Shockwave first");
        QuakeService.shockwave(p, 3);
        h.assertTrue(QuakeService.doubleTap(p), "Pressed again at once, the other palm answers");
        h.assertTrue(!QuakeService.doubleTap(p), "Only once");
        QuakeService.shockwave(p, 3);
        h.runAfterDelay(QuakeGeometry.DOUBLE_TAP_WINDOW + 2, () -> {
            try {
                h.assertTrue(!QuakeService.doubleTap(p), "Too late: the window has closed");
                h.succeed();
            } finally { done(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "quakerework.shatter")
    public static void shatterCollapsesAResonanceInOneBlow(GameTestHelper h) {
        var p = daisy(h, 3);
        var t = mob(h, EntityType.PILLAGER, p, 0, 5);
        try {
            h.assertTrue(QuakeKit.resonate(p, 3) && QuakeKit.resonating(t), "Resonate takes hold");
            float before = t.getHealth();
            h.assertTrue(QuakeKit.collapse(p), "Pressed again, it shatters");
            h.assertTrue(!QuakeKit.resonating(t), "The resonance is spent");
            // 3 + tier/2 = 3.0 a pulse, six pulses left, half again as hard, plus 2.
            h.assertTrue(before - t.getHealth() >= 28, "Everything it had left lands at once (took " + (before - t.getHealth()) + ")");
            h.assertTrue(!QuakeKit.collapse(p), "Nothing left to shatter");
            h.succeed();
        } finally { t.discard(); done(p); }
    }

    @GameTest(template = "rewind_empty", batch = "quakerework.upheaval")
    public static void upheavalEruptsTheRestOfTheLineAtOnce(GameTestHelper h) {
        floor(h);
        var p = daisy(h, 3);
        var far = mob(h, EntityType.PILLAGER, p, 0, 8);
        try {
            h.assertTrue(QuakeKit.faultLine(p, 3) && QuakeKit.faulting(), "The crack opens");
            h.assertTrue(QuakeKit.upheave(p), "Pressed again while it races: Upheaval");
            h.assertTrue(!QuakeKit.faulting(), "The crack is spent");
            h.assertTrue(far.getHealth() < 100, "An enemy far down the line is hit at once, before the crack could reach it");
            h.assertTrue(!QuakeKit.upheave(p), "Nothing left to heave");
            h.succeed();
        } finally { far.discard(); done(p); }
    }

    @GameTest(template = "rewind_empty", batch = "quakerework.shell", timeoutTicks = 30)
    public static void theVibrationShellBreaksBulletsAndTurnsArrows(GameTestHelper h) {
        var p = daisy(h, 3);
        Entity incoming = bullet(h, p, 5, -1.2, 0);
        Arrow arrow = new Arrow(EntityType.ARROW, h.getLevel());
        arrow.setPos(p.getX() + 4, p.getEyeY(), p.getZ());
        arrow.setDeltaMovement(-1.0, 0, 0);
        arrow.setNoGravity(true);
        h.getLevel().addFreshEntity(arrow);
        h.assertTrue(QuakeSkills.vibrationShell(p) && QuakeSkills.shelled(p), "The shell stands");
        h.runAfterDelay(6, () -> {
            try {
                h.assertTrue(incoming.isRemoved(), "A bullet that reaches the shell shakes apart");
                h.assertTrue(arrow.getOwner() == p && arrow.getDeltaMovement().x > 0, "An arrow is turned back and becomes hers");
                h.succeed();
            } finally { incoming.discard(); arrow.discard(); done(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "quakerework.lance", timeoutTicks = 30)
    public static void seismicLanceBurstsUpUnderItsTarget(GameTestHelper h) {
        floor(h);
        var p = daisy(h, 3);
        var t = mob(h, EntityType.PILLAGER, p, 0, 7);
        t.setOnGround(true);
        h.assertTrue(QuakeSkills.seismicLance(p, 3), "Her uppercut finds the enemy ahead");
        h.assertTrue(t.getHealth() == 100, "The column comes a beat later, after its warning");
        h.runAfterDelay(QuakeGeometry.LANCE_DELAY + 2, () -> {
            try {
                h.assertTrue(t.getHealth() < 100, "Then it bursts up under them");
                h.succeed();
            } finally { t.discard(); done(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "quakerework.step")
    public static void pressureStepLaunchesHerAlongHerAim(GameTestHelper h) {
        var p = daisy(h, 3);
        var t = mob(h, EntityType.PILLAGER, p, 1, -0.5);
        try {
            h.assertTrue(QuakeSkills.pressureStep(p, 3), "She steps");
            h.assertTrue(p.getDeltaMovement().z > 1.5, "Launched along her aim");
            h.assertTrue(t.getHealth() < 100, "The ring where she left hits whoever was beside her");
            h.succeed();
        } finally { t.discard(); done(p); }
    }

    @GameTest(template = "rewind_empty", batch = "quakerework.skin")
    public static void pressureSkinShakesShotsApartOnHer(GameTestHelper h) {
        var p = daisy(h, 3);
        var skeleton = mob(h, EntityType.SKELETON, p, 0, 6);
        Arrow arrow = new Arrow(EntityType.ARROW, h.getLevel());
        arrow.setOwner(skeleton);
        arrow.setPos(p.getX(), p.getEyeY(), p.getZ() + 1);
        h.getLevel().addFreshEntity(arrow);
        try {
            // A new player ignores damage for three seconds; a test has to take that away.
            var grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true);
            grace.setInt(p, 0);
            // An ownerless shot: a skeleton's would scale to nothing on the test server's difficulty.
            QuakeSkills.vibrationShell(p);
            float before = p.getHealth();
            p.hurt(p.damageSources().arrow(arrow, null), 4);
            h.assertTrue(p.getHealth() < before, "Without Pressure Skin the shot still lands");
            learn(p, QuakeSkills.PRESSURE_SKIN);
            p.invulnerableTime = 0;
            float now = p.getHealth();
            Arrow second = new Arrow(EntityType.ARROW, h.getLevel());
            second.setOwner(skeleton);
            second.setPos(p.getX(), p.getEyeY(), p.getZ() + 1);
            h.getLevel().addFreshEntity(second);
            p.hurt(p.damageSources().arrow(second, null), 4);
            h.assertTrue(p.getHealth() == now && second.isRemoved(), "With it, the shot shakes apart on her skin");
            h.succeed();
        } catch (ReflectiveOperationException e) { throw new AssertionError(e);
        } finally { skeleton.discard(); arrow.discard(); done(p); }
    }

    @GameTest(template = "rewind_empty", batch = "quakerework.sink")
    public static void kineticSinkBleedsStrainForEveryShotBroken(GameTestHelper h) {
        var p = daisy(h, 3);
        learn(p, QuakeSkills.KINETIC_SINK);
        Entity incoming = bullet(h, p, 5, -3, 0);
        try {
            QuakeKit.setStrain(p, 50);
            QuakeService.catchShots(p, p.getEyePosition(), p.getLookAngle(), 10);
            h.assertTrue(incoming.isRemoved() && QuakeKit.strain(p) == 47, "A broken bullet bleeds 3 Strain (now " + QuakeKit.strain(p) + ")");
            h.succeed();
        } finally { incoming.discard(); done(p); }
    }

    @GameTest(template = "rewind_empty", batch = "quakerework.livefault")
    public static void liveFaultBreaksAResonanceItRunsUnder(GameTestHelper h) {
        floor(h);
        var p = daisy(h, 3);
        learn(p, QuakeSkills.LIVE_FAULT);
        var t = mob(h, EntityType.PILLAGER, p, 0, 3);
        try {
            h.assertTrue(QuakeKit.resonate(p, 3), "Resonate takes hold");
            QuakeKit.faultLine(p, 3);
            h.assertTrue(QuakeKit.upheave(p), "The line erupts under it");
            h.assertTrue(!QuakeKit.resonating(t), "The crack broke the resonance");
            h.succeed();
        } finally { t.discard(); done(p); }
    }
}
