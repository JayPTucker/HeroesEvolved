package com.jayptucker.heroesevolved.skills;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class MasteryBalanceGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "mastery-balance"));
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.QUAKE_ID).unlock(ModAbilities.QUAKE_ID));
        return p;
    }

    private static Pillager foe(GameTestHelper h, ServerPlayer p) {
        var mob = EntityType.PILLAGER.create(h.getLevel());
        mob.setPos(p.position());
        mob.getPersistentData().putBoolean(com.jayptucker.heroesevolved.gifted.GiftedMobs.ROLLED, true);
        return mob;
    }

    private static PlayerProgressionData roundTrip(PlayerProgressionData data) {
        var saved = PlayerProgressionData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        return PlayerProgressionData.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
    }

    @GameTest(template = "rewind_empty", batch = "mastery.ledger")
    public static void oldSavesKeepTheirLevelsAndTheNewLedgerPersists(GameTestHelper h) {
        JsonObject old = new JsonObject(); old.addProperty("mastery", 8000);
        var data = PlayerProgressionData.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow();
        h.assertTrue(data.mastery() == 8000 && data.explored().isEmpty(), "Old saves migrate without losing Mastery");
        data = data.explore("overworld|1|2").encounter("overworld|1|2|zombie").practice(4).active(800, false);
        h.assertTrue(roundTrip(data).equals(data), "All anti-farm state survives a save/reload");
        var p = player(h);
        p.setData(ModDataAttachments.PLAYER_PROGRESSION, data);
        PlayerProgressionService.setPowerLevel(p, 20);
        h.assertTrue(PlayerProgressionService.getData(p).encounters().equals(data.encounters())
                && PlayerProgressionService.getData(p).practiceLeft() == 2, "Admin level changes do not reset the ledger");
        h.assertTrue(PlayerEnergyService.getMaximumEnergy(p) == 176, "Level 20 permanently increases max stamina to 176");
        PlayerProgressionService.setPowerLevel(p, 10);
        h.assertTrue(PlayerEnergyService.getMaximumEnergy(p) == 136, "Level 10 has 136 max stamina");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "mastery.farm")
    public static void localKillRewardsEndAndCannotBeResetByRelogging(GameTestHelper h) {
        var p = player(h);
        int limit = HeroesEvolvedConfig.COMMON.mobKillMaximumRewardedKills.get();
        for (int i = 0; i < limit; i++) MasteryService.awardHostileMobKill(p, foe(h, p));
        long earned = PlayerProgressionService.getData(p).mastery();
        h.assertTrue(earned > 0, "Ordinary encounters reward Mastery");
        MasteryService.forgetPlayer(p.getUUID());
        p.setData(ModDataAttachments.PLAYER_PROGRESSION, roundTrip(PlayerProgressionService.getData(p)));
        var extra = foe(h, p);
        MasteryService.markPowerUse(p, ModAbilities.QUAKE_ID);
        MasteryService.awardPowerHit(p, extra, 20);
        MasteryService.awardHostileMobKill(p, extra);
        h.assertTrue(PlayerProgressionService.getData(p).mastery() == earned, "Exhausted farm gives neither hit nor kill rewards after reload");
        var distant = foe(h, p); distant.setPos(p.position().add(512, 0, 0));
        MasteryService.awardHostileMobKill(p, distant);
        long fresh = PlayerProgressionService.getData(p).mastery();
        h.assertTrue(fresh > earned, "Exploring a new encounter region restores rewards for that mob type");
        MasteryService.awardHostileMobKill(p, distant);
        h.assertTrue(PlayerProgressionService.getData(p).mastery() == fresh, "The same death callback cannot pay twice");
        MasteryService.forgetPlayer(p.getUUID());
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "mastery.provenance")
    public static void spawnedFarmMobsGiveNothingAndBossesGiveMuchMore(GameTestHelper h) {
        var p = player(h);
        for (var kind : new MobSpawnType[]{MobSpawnType.SPAWNER, MobSpawnType.TRIAL_SPAWNER, MobSpawnType.SPAWN_EGG,
                MobSpawnType.COMMAND, MobSpawnType.MOB_SUMMONED, MobSpawnType.REINFORCEMENT}) {
            var mob = foe(h, p);
            mob.finalizeSpawn(h.getLevel(), h.getLevel().getCurrentDifficultyAt(mob.blockPosition()), kind, null);
            MasteryService.markPowerUse(p, ModAbilities.QUAKE_ID);
            MasteryService.awardPowerHit(p, mob, 20);
            MasteryService.awardHostileMobKill(p, mob);
        }
        h.assertTrue(PlayerProgressionService.getData(p).mastery() == 0, "Spawners, trial spawners, eggs, commands, summons and reinforcements never pay");
        MasteryService.forgetPlayer(p.getUUID());
        MasteryService.awardHostileMobKill(p, foe(h, p));
        long ordinary = PlayerProgressionService.getData(p).mastery();
        var boss = EntityType.WITHER.create(h.getLevel()); boss.setPos(p.position());
        MasteryService.awardHostileMobKill(p, boss);
        long bossReward = PlayerProgressionService.getData(p).mastery() - ordinary;
        h.assertTrue(bossReward >= 120 && bossReward > ordinary * 10, "A real boss gives a much larger reward: " + bossReward);
        MasteryService.forgetPlayer(p.getUUID());
        h.succeed();
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 100, batch = "mastery.afk")
    public static void castingIntoAirDoesNotCountAsPlaytimeOrRefillPractice(GameTestHelper h) {
        var p = player(h);
        h.onEachTick(() -> {
            MasteryService.markPowerUse(p, ModAbilities.QUAKE_ID);
            MasteryService.awardEnergySpent(p, 1000);
            MasteryService.tickActiveTime(p);
        });
        h.runAfterDelay(80, () -> {
            try {
                var data = PlayerProgressionService.getData(p);
                h.assertTrue(data.mastery() == PlayerProgressionData.PRACTICE_PER_MINUTE && data.activeTicks() == 0,
                        "Air spam can spend the initial practice allowance but earns no active playtime");
                MasteryService.forgetPlayer(p.getUUID());
                MasteryService.awardEnergySpent(p, 1000);
                h.assertTrue(PlayerProgressionService.getData(p).mastery() == data.mastery(), "Relogging does not refill practice");
                h.succeed();
            } finally { MasteryService.forgetPlayer(p.getUUID()); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 100, batch = "mastery.afk")
    public static void aWaterLoopOrAnAutoclickerIsNotPlaytime(GameTestHelper h) {
        var p = player(h);
        Vec3 base = p.position(); long start = h.getLevel().getGameTime();
        h.onEachTick(() -> {
            long age = h.getLevel().getGameTime() - start;
            // Carried round by a current at a fixed look, and "building" at that same look every tick.
            p.setPos(base.add((age / 10) % 2 * 3, 0, 0));
            MasteryService.tickActiveTime(p);
            MasteryService.activity(p);
        });
        h.runAfterDelay(60, () -> {
            var data = PlayerProgressionService.getData(p);
            if (data.activeTicks() != 0 || !data.explored().isEmpty()) {
                MasteryService.forgetPlayer(p.getUUID());
                h.fail("Moving or clicking without ever turning is not play: " + data.activeTicks() + " ticks");
            }
            p.setYRot(45); // Back at the keyboard: they turn to the work.
        });
        h.runAfterDelay(80, () -> {
            try {
                h.assertTrue(PlayerProgressionService.getData(p).activeTicks() > 0, "Turning to work counts as play again");
                h.succeed();
            } finally { MasteryService.forgetPlayer(p.getUUID()); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 1300, batch = "mastery.explore")
    public static void activePlayAndNewRegionsBothRewardProgress(GameTestHelper h) {
        var p = player(h);
        Vec3 base = p.position(); long start = h.getLevel().getGameTime();
        MasteryService.awardEnergySpent(p, 1000); // Empty the six-point practice allowance.
        h.onEachTick(() -> {
            long age = h.getLevel().getGameTime() - start;
            p.setPos(base.add((age / 10) % 2 * 2, 0, 0));
            p.setYRot((age / 10) % 2 * 30); // A player at the keyboard turns as they walk.
            MasteryService.tickActiveTime(p);
        });
        h.runAfterDelay(1210, () -> {
            try {
                var data = PlayerProgressionService.getData(p);
                h.assertTrue(data.activeTicks() >= 1200 && data.practiceLeft() == 6, "One active minute refills practice");
                long expected = 6 + MasteryService.EXPLORATION_MASTERY + HeroesEvolvedConfig.COMMON.activeMasteryAmount.get();
                h.assertTrue(data.mastery() == expected && data.explored().size() == 1, "Active time and first exploration are separate rewards");
                p.setPos(base.add(256, 0, 0)); p.setYRot(p.getYRot() + 45); MasteryService.tickActiveTime(p);
                h.assertTrue(PlayerProgressionService.getData(p).mastery() == expected + MasteryService.EXPLORATION_MASTERY,
                        "A new region earns another exploration reward after an active minute");
                p.setPos(base); p.setYRot(p.getYRot() + 45); MasteryService.tickActiveTime(p);
                h.assertTrue(PlayerProgressionService.getData(p).explored().size() == 2, "Revisiting an old region never pays twice");
                h.succeed();
            } finally { MasteryService.forgetPlayer(p.getUUID()); }
        });
    }
}
