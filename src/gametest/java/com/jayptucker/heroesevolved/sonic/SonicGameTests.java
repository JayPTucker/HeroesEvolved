package com.jayptucker.heroesevolved.sonic;

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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Canary Cry (tap and hold), Resonant Note's lure and burst, Wall of Sound, Sonic Vault's landing, Crescendo and Resonance,
 * and the rework's follow-ups and interactions: a Cry or Crescendo through your Note, the scream feeding it, the shoved
 * Wall, Sonic Dive, Ringing, Feedback Loop and the three branch skills.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class SonicGameTests {
    private static ServerPlayer singer(GameTestHelper h, int tier) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "singer-test")) {
            @Override public boolean isInvulnerableTo(DamageSource s) { return false; }
        };
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
        p.setYRot(0); p.setXRot(0);
        var id = ModAbilities.SONIC_RESONANCE_ID;
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(id).unlock(id));
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(id));
        PlayerProgressionService.setLevel(p, tier);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        return p;
    }

    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, ServerPlayer p, double dx, double dz, boolean ai) {
        T mob = type.create(h.getLevel());
        mob.setNoAi(!ai); mob.setPersistenceRequired();
        mob.moveTo(p.getX() + dx, p.getY(), p.getZ() + dz, 180, 0);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); mob.setHealth(100);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    @GameTest(template = "rewind_empty", batch = "sonic.cry", timeoutTicks = 40)
    public static void aTappedCryHitsAndAHeldOneBecomesTheScream(GameTestHelper h) {
        var p = singer(h, 1);
        var t = mob(h, EntityType.PILLAGER, p, 0, 4, false);
        SonicService.press(p, 1);
        SonicService.release(p);
        h.assertTrue(t.getHealth() < 100, "A tap is the cry: it hits what is in front of you");
        h.assertTrue(SonicService.resonance(p) == 4, "A landed cry builds Resonance");
        h.assertTrue(!SonicService.screaming(p), "A tap is not the scream");
        SonicService.press(p, 1);
        h.runAfterDelay(10, () -> {
            try {
                h.assertTrue(SonicService.screaming(p), "Holding the key becomes the sustained scream");
                SonicService.release(p);
                h.assertTrue(!SonicService.screaming(p) && !SonicService.holdingCry(p), "Letting go ends it");
                h.succeed();
            } finally { t.discard(); SonicService.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "sonic.recoil")
    public static void yourOwnCryPushesYouBackUnlessYouCrouch(GameTestHelper h) {
        var p = singer(h, 1);
        try {
            p.setOnGround(true);
            p.setDeltaMovement(Vec3.ZERO);
            SonicService.cry(p, 1, false);
            h.assertTrue(p.getDeltaMovement().z < -0.5, "Standing, the cry throws you back, away from where you face");
            p.setDeltaMovement(Vec3.ZERO);
            p.setShiftKeyDown(true);
            SonicService.cry(p, 1, false);
            h.assertTrue(p.getDeltaMovement().horizontalDistanceSqr() < 1.0E-6, "Crouching braces you: no push");
            p.setOnGround(false);
            h.assertTrue(SonicService.recoil(p, SonicService.SCREAM_RECOIL), "In the air there is nothing to brace on");
            h.succeed();
        } finally { SonicService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "sonic.note", timeoutTicks = 40)
    public static void aResonantNoteLuresEnemiesAndBurstsOnThem(GameTestHelper h) {
        var p = singer(h, 2);
        var zombie = mob(h, EntityType.PILLAGER, p, 2, 5, true);
        zombie.setTarget(p);
        SonicService.placeNoteAt(p, p.position().add(0, 1, 6), 2);
        h.assertTrue(SonicService.hasNote(p), "The note is set");
        h.runAfterDelay(6, () -> {
            try {
                h.assertTrue(SonicService.lured(zombie) && zombie.getTarget() == null, "A hunting mob forgets you for the note");
                zombie.setTarget(p);
                h.assertTrue(zombie.getTarget() == null, "It cannot pick a new target while the note plays");
                h.assertTrue(SonicService.detonate(p) && !SonicService.hasNote(p), "Pressing again breaks the note");
                h.assertTrue(zombie.getHealth() <= 100 - 8 + 0.1F, "Everything gathered is hit (5 + 1.5 per tier)");
                h.assertTrue(!SonicService.lured(zombie), "And it is free again");
                h.assertTrue(EvolutionTrials.progress(p, "sonic_resonance") == 1, "Each enemy caught counts toward the Evolved trial");
                h.succeed();
            } finally { zombie.discard(); SonicService.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "sonic.harmonic")
    public static void resonanceMakesTheNoteHarmonic(GameTestHelper h) {
        var p = singer(h, 2);
        var t = mob(h, EntityType.PILLAGER, p, 0, 6, false);
        try {
            SonicService.addResonance(p, 40);
            SonicService.placeNoteAt(p, p.position().add(0, 1, 6), 2);
            SonicService.detonate(p);
            h.assertTrue(SonicService.resonance(p) == 40 - SonicService.HARMONIC_COST, "A harmonic burst spends 30 Resonance");
            h.assertTrue(Math.abs(100 - t.getHealth() - 12) < 0.1F, "It hits half again as hard (8 becomes 12)");
            h.succeed();
        } finally { t.discard(); SonicService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "sonic.wall", timeoutTicks = 30)
    public static void theWallOfSoundTurnsShotsBack(GameTestHelper h) {
        var p = singer(h, 3);
        SonicService.wall(p, 3);
        var arrow = new Arrow(EntityType.ARROW, h.getLevel());
        arrow.setNoGravity(true);
        arrow.setPos(p.getX(), p.getY() + 1.2, p.getZ() + 6);
        arrow.setDeltaMovement(0, 0, -0.8);
        h.getLevel().addFreshEntity(arrow);
        h.runAfterDelay(8, () -> {
            try {
                h.assertTrue(arrow.getDeltaMovement().z > 0, "The shot turned back the way it came");
                h.assertTrue(arrow.getOwner() == p, "And it is yours now");
                h.assertTrue(SonicService.dropWall(p) && !SonicService.hasWall(p), "The wall can be dropped");
                h.succeed();
            } finally { arrow.discard(); SonicService.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "sonic.landing")
    public static void sonicVaultLandsOnAShockwave(GameTestHelper h) {
        var p = singer(h, 4);
        var t = mob(h, EntityType.PILLAGER, p, 2, 0, false);
        try {
            SonicService.vault(p);
            h.assertTrue(SonicService.cushion(p, 6), "The next landing is cushioned");
            h.assertTrue(t.getHealth() < 100, "A real fall lands on a shockwave");
            h.assertTrue(!SonicService.cushion(p, 6), "Only the next landing");
            h.succeed();
        } finally { t.discard(); SonicService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "sonic.crescendo")
    public static void crescendoSpendsAllResonanceForAHarderWave(GameTestHelper h) {
        var p = singer(h, 5);
        var near = mob(h, EntityType.PILLAGER, p, 0, 5, false);
        var behind = mob(h, EntityType.PILLAGER, p, 0, -5, false);
        try {
            SonicService.addResonance(p, 50);
            int hit = SonicService.fireCrescendo(p, 5, 1F);
            h.assertTrue(hit == 1 && behind.getHealth() == 100, "The wave goes where you face, not behind");
            h.assertTrue(Math.abs(100 - near.getHealth() - (6 + 10 + 5 + 5)) < 0.1F, "Fully built, with 50 Resonance: 6 + 10 + 5 + tier");
            h.assertTrue(SonicService.resonance(p) == 0, "It spends all of it");
            h.succeed();
        } finally { near.discard(); behind.discard(); SonicService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "sonic.crescendo_hold", timeoutTicks = 90)
    public static void aHeldCrescendoReleasesOnItsOwnAtFullStrength(GameTestHelper h) {
        var p = singer(h, 5);
        SonicService.startCrescendo(p, 5);
        h.assertTrue(SonicService.building(p), "It builds while held");
        h.runAfterDelay(SonicService.CRESCENDO_TICKS + 3, () -> {
            h.assertTrue(!SonicService.building(p), "At three seconds it lets go by itself");
            SonicService.forget(p);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "sonic.trial")
    public static void sonicResonanceHasItsOwnEvolvedTrial(GameTestHelper h) {
        h.assertTrue(EvolutionTrials.special("sonic_resonance") && EvolutionTrials.trial("sonic_resonance").goal() == 40,
                "Sonic Resonance evolves by catching 40 enemies in Resonant Note bursts");
        h.succeed();
    }

    private static void learn(ServerPlayer p, String... ids) {
        p.setData(ModDataAttachments.SKILL_TREES, com.jayptucker.heroesevolved.skills.SkillTreeData.empty().with(
                ModAbilities.SONIC_RESONANCE_ID.toString(), new com.jayptucker.heroesevolved.skills.SkillTreeData.Build(15, Set.of(ids), Map.of())));
    }

    /** A point straight ahead of the singer's eyes (the test singer faces +Z). */
    private static Vec3 ahead(ServerPlayer p, double distance) { return p.getEyePosition().add(0, 0, distance); }

    @GameTest(template = "rewind_empty", batch = "sonic.note_cry")
    public static void aCryThroughYourNoteBreaksItHarmonicForFree(GameTestHelper h) {
        var p = singer(h, 2);
        var t = mob(h, EntityType.PILLAGER, p, 3.5, 6, false);
        try {
            SonicService.placeNoteAt(p, ahead(p, 6), 2);
            SonicService.press(p, 2);
            SonicService.release(p);
            h.assertTrue(!SonicService.hasNote(p), "The cry broke the note it passed through");
            h.assertTrue(SonicService.resonance(p) == 0, "A harmonic it didn't have to pay for");
            h.assertTrue(Math.abs(100 - t.getHealth() - 12) < 0.1F, "Harmonic damage (8 becomes 12), got " + (100 - t.getHealth()));
            h.succeed();
        } finally { t.discard(); SonicService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "sonic.note_feed")
    public static void theScreamFeedsTheNoteInsteadOfBreakingIt(GameTestHelper h) {
        var p = singer(h, 2);
        var t = mob(h, EntityType.PILLAGER, p, 3.5, 6, false);
        try {
            SonicService.placeNoteAt(p, ahead(p, 6), 2);
            SonicService.cry(p, 2, true);
            h.assertTrue(SonicService.hasNote(p), "The held scream doesn't break the note");
            float before = t.getHealth();
            SonicService.detonate(p);
            h.assertTrue(Math.abs(before - t.getHealth() - 8.5F) < 0.1F, "Fed once, the burst hits half a point harder (8.5), got " + (before - t.getHealth()));
            h.succeed();
        } finally { t.discard(); SonicService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "sonic.note_move")
    public static void shiftCarriesTheNoteOnce(GameTestHelper h) {
        var p = singer(h, 2);
        try {
            SonicService.placeNoteAt(p, ahead(p, 3), 2);
            Vec3 first = SonicService.notePosition(p);
            h.assertTrue(SonicService.noteFollowUp(p, true), "Shift + press moves the note");
            h.assertTrue(SonicService.hasNote(p) && !SonicService.notePosition(p).equals(first), "It is still playing, somewhere else");
            Vec3 second = SonicService.notePosition(p);
            SonicService.noteFollowUp(p, true);
            h.assertTrue(SonicService.notePosition(p).equals(second), "Only once");
            h.assertTrue(SonicService.noteFollowUp(p, false) && !SonicService.hasNote(p), "A plain press breaks it");
            h.succeed();
        } finally { SonicService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "sonic.wall_push", timeoutTicks = 40)
    public static void aShovedWallRollsForwardThroughEnemies(GameTestHelper h) {
        var p = singer(h, 3);
        var t = mob(h, EntityType.PILLAGER, p, 0, 5, false);
        SonicService.wall(p, 3);
        h.assertTrue(SonicService.pushWall(p) && SonicService.pushing(p), "Pressing again shoves the wall");
        h.assertTrue(!SonicService.pushWall(p), "A rolling wall can't be shoved again");
        h.runAfterDelay(8, () -> {
            try { h.assertTrue(100 - t.getHealth() >= 7 - 0.1F, "It hit what it rolled through (4 + tier)"); }
            catch (RuntimeException e) { t.discard(); SonicService.forget(p); throw e; }
        });
        h.runAfterDelay(SonicGeometry.WALL_PUSH_TICKS + 4, () -> {
            try {
                h.assertTrue(!SonicService.hasWall(p), "It ends when it has rolled its course");
                h.succeed();
            } finally { t.discard(); SonicService.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "sonic.dive", timeoutTicks = 80)
    public static void sonicDiveLandsOnAWiderHarderShockwave(GameTestHelper h) {
        var p = singer(h, 4);
        var t = mob(h, EntityType.PILLAGER, p, 5, 0, false);
        h.assertTrue(!SonicService.dive(p, 4), "No dive without a Vault first");
        SonicService.vault(p);
        h.assertTrue(SonicService.dive(p, 4) && SonicService.diving(p), "After a Vault, pressing again in the air dives");
        h.runAfterDelay(SonicGeometry.DIVE_MAX + 3, () -> {
            try {
                h.assertTrue(!SonicService.diving(p), "The dive lands (or times out)");
                h.assertTrue(Math.abs(100 - t.getHealth() - 10.5F) < 0.1F, "Five blocks out, a 1.5x shockwave: (3 + tier) * 1.5, got " + (100 - t.getHealth()));
                h.assertTrue(SonicService.cushion(p, 12), "The landing takes no fall damage");
                h.succeed();
            } finally { t.discard(); SonicService.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "sonic.ring")
    public static void ringingShattersOnTheNextSonicHit(GameTestHelper h) {
        var p = singer(h, 1);
        var t = mob(h, EntityType.PILLAGER, p, 0, 4, false);
        try {
            SonicService.ring(p, t);
            h.assertTrue(SonicService.ringing(t), "The target rings");
            SonicService.press(p, 1);
            SonicService.release(p);
            h.assertTrue(!SonicService.ringing(t), "The next hit shatters the ring");
            h.assertTrue(Math.abs(100 - t.getHealth() - (5.2F + SonicGeometry.RING_DAMAGE)) < 0.1F, "Cry (5.2) + ring (6), got " + (100 - t.getHealth()));
            h.assertTrue(SonicService.resonance(p) == 4 + SonicGeometry.RING_RESONANCE, "4 for the hit and 10 for the ring");
            h.succeed();
        } finally { t.discard(); SonicService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "sonic.feedback")
    public static void feedbackLoopAmplifiesACryThroughYourWall(GameTestHelper h) {
        var p = singer(h, 3);
        var t = mob(h, EntityType.PILLAGER, p, 0, 6, false);
        try {
            SonicService.wall(p, 3);
            SonicService.press(p, 3);
            SonicService.release(p);
            float plain = 100 - t.getHealth();
            h.assertTrue(Math.abs(plain - 7.6F) < 0.1F, "Without the skill the wall changes nothing (7.6), got " + plain);
            learn(p, "sonic_resonance_0_2");
            t.setHealth(100);
            SonicService.press(p, 3);
            SonicService.release(p);
            h.assertTrue(Math.abs(100 - t.getHealth() - 11.4F) < 0.1F, "Through the wall with Feedback Loop: half again (11.4), got " + (100 - t.getHealth()));
            h.succeed();
        } finally { t.discard(); SonicService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "sonic.shriek")
    public static void focusedShriekHitsOneTargetAndLeavesItRinging(GameTestHelper h) {
        var p = singer(h, 3);
        var t = mob(h, EntityType.PILLAGER, p, 0, 6, false);
        try {
            h.assertTrue(SonicSkills.focusedShriek(p, 3), "The shriek finds the target ahead");
            h.assertTrue(Math.abs(100 - t.getHealth() - 12) < 0.1F, "9 + level, got " + (100 - t.getHealth()));
            h.assertTrue(SonicService.ringing(t), "And leaves it Ringing");
            h.succeed();
        } finally { t.discard(); SonicService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "sonic.quiet", timeoutTicks = 30)
    public static void theQuietZoneKeepsMobsFromTargeting(GameTestHelper h) {
        var p = singer(h, 3);
        var zombie = mob(h, EntityType.ZOMBIE, p, 2, 0, true);
        zombie.setTarget(p);
        SonicSkills.quietZone(p);
        h.runAfterDelay(6, () -> {
            try {
                h.assertTrue(zombie.getTarget() == null && SonicSkills.hushed(zombie), "Inside the zone it loses its target");
                zombie.setTarget(p);
                h.assertTrue(zombie.getTarget() == null, "And can't pick a new one");
                h.succeed();
            } finally { zombie.discard(); SonicService.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "sonic.crescendo_note")
    public static void crescendoBreaksTheNoteInItsPath(GameTestHelper h) {
        var p = singer(h, 5);
        try {
            SonicService.placeNoteAt(p, ahead(p, 8), 5);
            SonicService.fireCrescendo(p, 5, 1F);
            h.assertTrue(!SonicService.hasNote(p), "The wave broke the note");
            h.succeed();
        } finally { SonicService.forget(p); }
    }
}
