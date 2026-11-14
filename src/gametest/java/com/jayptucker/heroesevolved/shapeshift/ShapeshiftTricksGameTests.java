package com.jayptucker.heroesevolved.shapeshift;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.grid.GridGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Shapeshifting, phase 5: Decoy, Reflection, Nightmare, Suspicion and the readers who see through a disguise. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class ShapeshiftTricksGameTests {
    private static ServerPlayer shifter(GameTestHelper h, int tier) {
        return GridGameTests.player(h, ModAbilities.SHAPESHIFTING_ID, tier);
    }

    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, int x, int z) {
        T mob = type.create(h.getLevel());
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        mob.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x, 2, z))));
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.decoy", timeoutTicks = 120)
    public static void aDecoyDrawsHuntersAndBurstsOnTheFirstBlow(GameTestHelper h) {
        ServerPlayer p = shifter(h, 3);
        var zombie = mob(h, EntityType.ZOMBIE, 3, 8);
        zombie.setTarget(p);
        ShapeDecoy decoy;
        try {
            h.assertTrue(ShapeshiftTricks.decoy(p), "Shedding a decoy");
            decoy = ShapeshiftTricks.decoyOf(p);
            h.assertTrue(decoy != null && decoy.form() != null && decoy.form().person() && decoy.form().isOf(p),
                    "In your own body the shell wears your own face");
            h.assertTrue(zombie.getTarget() == decoy, "The zombie that was hunting you turns on the shell");
            zombie.setTarget(p);
            h.assertTrue(zombie.getTarget() == decoy, "and keeps after it, not you");
            h.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SPEED), "You slip away faster");

            decoy.hurt(h.getLevel().damageSources().mobAttack(zombie), 3);
            h.assertTrue(decoy.isRemoved() && ShapeshiftTricks.decoyOf(p) == null, "The first blow bursts it");
            h.assertTrue(ShapeshiftTricks.cowed(zombie) && zombie.getTarget() == null, "and leaves the striker confused");
            zombie.setTarget(p);
            h.assertTrue(zombie.getTarget() == null, "A confused zombie picks no target");
        } catch (RuntimeException e) { ShapeshiftService.forget(p); throw e; }
        h.runAfterDelay(ShapeshiftTricks.CONFUSE_TICKS + 5, () -> {
            try {
                h.assertTrue(!ShapeshiftTricks.cowed(zombie), "Two seconds later the zombie comes to");
                zombie.setTarget(p);
                h.assertTrue(zombie.getTarget() == p, "and can hunt you again");
            } finally { ShapeshiftService.forget(p); }
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.decoy")
    public static void aDecoyWearsYourShapeAndCrumblesWhenYouLeave(GameTestHelper h) {
        ServerPlayer p = shifter(h, 3);
        try {
            h.assertTrue(ShapeshiftService.morph(p, EntityType.ZOMBIE) && ShapeshiftTricks.decoy(p), "A zombie sheds a decoy");
            ShapeDecoy decoy = ShapeshiftTricks.decoyOf(p);
            h.assertTrue(decoy != null && decoy.form().type() == EntityType.ZOMBIE, "The shell is a zombie");
            h.assertTrue(Math.abs(decoy.getBbHeight() - EntityType.ZOMBIE.getDimensions().height()) < 1.0E-3, "with a zombie's body");
            h.assertTrue(!decoy.hurt(h.getLevel().damageSources().fall(), 5) && !decoy.isRemoved(), "Falling doesn't touch it");
            ShapeshiftService.forget(p);
            h.assertTrue(decoy.isRemoved(), "When its maker's power goes, the shell goes too");
        } finally { ShapeshiftService.forget(p); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.reflection")
    public static void reflectionBecomesYourAttacker(GameTestHelper h) {
        ServerPlayer p = shifter(h, 4);
        var attacker = mob(h, EntityType.ZOMBIE, 3, 6);
        var other = mob(h, EntityType.HUSK, 6, 6);
        other.setTarget(p);
        try {
            h.assertTrue(!ShapeshiftTricks.reflect(p), "Nobody has hit you yet");
            ShapeshiftTricks.struck(p, attacker);
            h.assertTrue(ShapeshiftTricks.reflect(p), "Reflecting the zombie that just hit you");
            h.assertTrue(ShapeshiftService.form(p) == EntityType.ZOMBIE, "You are now a zombie");
            h.assertTrue(other.getTarget() == null, "A husk that was hunting you forgets you, even though it saw");
            h.assertTrue(ShapeshiftTricks.cowed(attacker) && attacker.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                    "The attacker staggers, face to face with itself");
            h.assertTrue(!ShapeshiftTricks.canReflect(p), "One hit, one reflection");
        } finally { ShapeshiftService.forget(p); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.reflection.window", timeoutTicks = 120)
    public static void reflectionOnlyWorksJustAfterTheHit(GameTestHelper h) {
        ServerPlayer p = shifter(h, 4);
        var attacker = mob(h, EntityType.ZOMBIE, 3, 6);
        ShapeshiftTricks.struck(p, attacker);
        h.runAfterDelay(ShapeshiftTricks.REFLECT_WINDOW + 5, () -> {
            try { h.assertTrue(!ShapeshiftTricks.reflect(p) && !ShapeshiftService.morphed(p), "Three seconds on, the moment has passed"); }
            finally { ShapeshiftService.forget(p); }
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.nightmare", timeoutTicks = 140)
    public static void aNightmareFrightensFoesSoTheyCantAttack(GameTestHelper h) {
        ServerPlayer p = shifter(h, 5);
        var pillager = mob(h, EntityType.PILLAGER, 3, 7);
        var cow = mob(h, EntityType.COW, 7, 7);
        try {
            h.assertTrue(ShapeshiftTricks.nightmare(p), "A nightmare rears");
            h.assertTrue(ShapeshiftTricks.frightened(pillager) && ShapeshiftTricks.frightened(cow), "Every foe in sight is frightened");
            h.assertTrue(!ShapeshiftService.morphed(p), "Your own shape doesn't change");
            pillager.setTarget(p);
            h.assertTrue(pillager.getTarget() == null, "A frightened pillager picks no target");
            float before = cow.getHealth();
            cow.hurt(h.getLevel().damageSources().mobAttack(pillager), 3);
            h.assertTrue(cow.getHealth() == before, "and its blows land on nothing");
        } catch (RuntimeException e) { ShapeshiftService.forget(p); throw e; }
        h.runAfterDelay(ShapeshiftTricks.FRIGHT_TICKS + 5, () -> {
            try { h.assertTrue(!ShapeshiftTricks.frightened(pillager), "Five seconds later the fear passes"); }
            finally { ShapeshiftService.forget(p); }
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.suspicion")
    public static void hurtingYourOwnKindGetsYouSeenThrough(GameTestHelper h) {
        ServerPlayer p = shifter(h, 2);
        var watcher = mob(h, EntityType.ZOMBIE, 3, 8);
        var victim = mob(h, EntityType.ZOMBIE, 5, 6);
        try {
            h.assertTrue(ShapeshiftService.morph(p, EntityType.ZOMBIE), "Taking a zombie's shape");
            h.assertTrue(ShapeshiftSuspicion.watched(p), "Your kind can see you");
            victim.hurt(p.damageSources().playerAttack(p), 1);
            h.assertTrue(Math.abs(ShapeshiftSuspicion.value(p) - ShapeshiftSuspicion.KIN_HURT) < .01, "Hurting a zombie costs 40 (" + ShapeshiftSuspicion.value(p) + ")");
            ShapeshiftSuspicion.raise(p, 60);
            h.assertTrue(!ShapeshiftService.morphed(p), "At 100 the disguise bursts off");
            h.assertTrue(p.hasEffect(MobEffects.GLOWING), "and you glow");
            h.assertTrue(watcher.getTarget() == p, "Every zombie near turns on you");
            h.assertTrue(ShapeshiftSuspicion.locked(p) && !ShapeshiftService.morph(p, EntityType.COW), "and Morph rests");
        } finally { ShapeshiftService.forget(p); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.suspicion.decay", timeoutTicks = 80)
    public static void suspicionFadesWhenNobodyIsLooking(GameTestHelper h) {
        ServerPlayer p = shifter(h, 2);
        try {
            h.assertTrue(ShapeshiftService.morph(p, EntityType.ZOMBIE), "Taking a zombie's shape");
            ShapeshiftSuspicion.raise(p, 30);
            h.assertTrue(!ShapeshiftSuspicion.watched(p), "with no zombie about");
        } catch (RuntimeException e) { ShapeshiftService.forget(p); throw e; }
        h.runAfterDelay(41, () -> {
            try {
                float now = ShapeshiftSuspicion.value(p);
                h.assertTrue(now < 30 - ShapeshiftSuspicion.DECAY_PER_SECOND * 1.5F && now > 30 - ShapeshiftSuspicion.DECAY_PER_SECOND * 2.6F,
                        "Two seconds unwatched takes about 10 off (" + now + ")");
            } finally { ShapeshiftService.forget(p); }
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.suspicion")
    public static void aHeavyBlowBreaksTheForm(GameTestHelper h) {
        ServerPlayer p = shifter(h, 2);
        try {
            h.assertTrue(ShapeshiftService.morph(p, EntityType.ZOMBIE), "Taking a zombie's shape");
            h.assertTrue(!ShapeshiftSuspicion.blow(p, 5) && ShapeshiftService.morphed(p), "A light blow doesn't break it");
            h.assertTrue(ShapeshiftSuspicion.blow(p, 7) && !ShapeshiftService.morphed(p), "A blow worth 30% of its health does");
            h.assertTrue(ShapeshiftSuspicion.locked(p), "and Morph rests a moment");
        } finally { ShapeshiftService.forget(p); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.suspicion")
    public static void aTelepathOrAVibeReadSeesThrough(GameTestHelper h) {
        ServerPlayer p = shifter(h, 2);
        ServerPlayer reader = GridGameTests.player(h, ModAbilities.TELEPATHY_ID, 3);
        try {
            h.assertTrue(!ShapeshiftSuspicion.read(reader, p), "There is nothing to see through in your own body");
            h.assertTrue(ShapeshiftService.morph(p, EntityType.ZOMBIE), "Taking a zombie's shape");
            h.assertTrue(ShapeshiftSuspicion.read(reader, p), "A read lands on the zombie");
            h.assertTrue(!ShapeshiftService.morphed(p) && ShapeshiftSuspicion.locked(p), "and the disguise is exposed");
        } finally { ShapeshiftService.forget(p); ShapeshiftService.forget(reader); }
        h.succeed();
    }
}
