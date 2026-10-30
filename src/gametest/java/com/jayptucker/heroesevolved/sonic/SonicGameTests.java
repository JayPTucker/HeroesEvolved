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

import java.util.UUID;

/** Canary Cry (tap and hold), Resonant Note's lure and burst, Wall of Sound, Sonic Vault's landing, Crescendo and Resonance. */
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
}
