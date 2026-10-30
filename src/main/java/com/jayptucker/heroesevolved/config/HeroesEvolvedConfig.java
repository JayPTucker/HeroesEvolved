package com.jayptucker.heroesevolved.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class HeroesEvolvedConfig {
    public static final ModConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        COMMON = new Common(builder);
        COMMON_SPEC = builder.build();
    }

    private HeroesEvolvedConfig() {
    }

    private static ModConfigSpec.IntValue energy(
            ModConfigSpec.Builder builder,
            String action,
            int defaultCost
    ) {
        return builder
                .comment("Energy consumed by " + action + ".")
                .defineInRange(action + "EnergyCost", defaultCost, 0, 1_000);
    }

    private static ModConfigSpec.IntValue cooldown(
            ModConfigSpec.Builder builder,
            String action,
            int defaultTicks
    ) {
        return builder
                .comment("Cooldown in ticks after " + action + ".")
                .defineInRange(action + "CooldownTicks", defaultTicks, 0, 20 * 60 * 30);
    }

    public static final class Common {
        public final ModConfigSpec.BooleanValue sylarEnabled, sylarCanAcquirePowers;
        public final ModConfigSpec.BooleanValue bunkerEnabled;
        public final ModConfigSpec.IntValue sylarFirstEncounterSeconds, sylarSightingIntervalSeconds, sylarHuntIntervalSeconds;
        public final ModConfigSpec.IntValue maximumLevel;
        public final ModConfigSpec.LongValue baseMasteryPerLevel;
        public final ModConfigSpec.LongValue masteryIncreasePerLevel;
        public final ModConfigSpec.IntValue activeMasteryIntervalTicks;
        public final ModConfigSpec.IntValue activeMasteryAmount;
        public final ModConfigSpec.IntValue mobKillMasteryPerTenHealth;
        public final ModConfigSpec.DoubleValue masteryPerEnergySpent;
        public final ModConfigSpec.IntValue energyRegenerationBonusEveryLevels;
        public final ModConfigSpec.IntValue mobKillWindowTicks;
        public final ModConfigSpec.IntValue mobKillFullRewardLimit;
        public final ModConfigSpec.IntValue mobKillMaximumRewardedKills;
        public final ModConfigSpec.IntValue baseEnergy;
        public final ModConfigSpec.IntValue energyPerLevel;
        public final ModConfigSpec.IntValue energyRegenerationPerSecond;
        public final ModConfigSpec.IntValue regenerationEnergyCost;
        public final ModConfigSpec.IntValue regenerationIntervalTicks;
        public final ModConfigSpec.IntValue regenerationDamageDelayTicks;
        public final ModConfigSpec.IntValue regenerationMendingEnergyCost;
        public final ModConfigSpec.IntValue regenerationMendingCooldownTicks;
        public final ModConfigSpec.IntValue regenerationBloodGiftEnergyCost;
        public final ModConfigSpec.IntValue regenerationBloodGiftCooldownTicks;
        public final ModConfigSpec.IntValue regenerationIndestructibleEnergyCost;
        public final ModConfigSpec.IntValue regenerationIndestructibleCooldownTicks;
        public final ModConfigSpec.IntValue regenerationResurrectionCooldownTicks;
        public final ModConfigSpec.IntValue flightGrabEnergyCost;
        public final ModConfigSpec.IntValue flightGrabCooldownTicks;

        // Controls how dangerous it is to use an ability without enough energy.
        public final ModConfigSpec.DoubleValue overexertionBaseDamage;
        public final ModConfigSpec.DoubleValue overexertionDamagePerMissingEnergy;
        public final ModConfigSpec.IntValue overexertionWeaknessDurationSeconds;
        public final ModConfigSpec.IntValue overexertionNauseaDurationSeconds;

        // Eclipse
        public final ModConfigSpec.LongValue eclipseIntervalTicks;
        public final ModConfigSpec.LongValue eclipseDurationTicks;
        public final ModConfigSpec.DoubleValue eclipseDormantAbilityChance;

        // Awakening
        public final ModConfigSpec.DoubleValue awakeningHealthThreshold;

        // Flight
        public final ModConfigSpec.IntValue flightLaunchEnergyCost;
        public final ModConfigSpec.IntValue flightLaunchCooldownTicks;
        public final ModConfigSpec.IntValue flightLaunchAscentDurationSeconds;
        public final ModConfigSpec.DoubleValue flightAscentSpeed;
        public final ModConfigSpec.DoubleValue flightForwardSpeed;
        public final ModConfigSpec.DoubleValue flightCruiseSpeed;
        public final ModConfigSpec.IntValue flightEnergyDrainPerSecond;
        public final ModConfigSpec.IntValue flightBoostEnergyDrainPerSecond;
        public final ModConfigSpec.IntValue flightSafeLandingSeconds;
        public final ModConfigSpec.IntValue flightTrailIntervalTicks;
        public final ModConfigSpec.IntValue flightCycloneEnergyCost;
        public final ModConfigSpec.IntValue flightCycloneCooldownTicks;
        public final ModConfigSpec.IntValue flightCycloneDurationSeconds;
        public final ModConfigSpec.DoubleValue flightCycloneRadius;
        public final ModConfigSpec.DoubleValue flightCycloneOrbitRadius;
        public final ModConfigSpec.DoubleValue flightCycloneOrbitSpeed;
        public final ModConfigSpec.DoubleValue flightCycloneLaunchSpeed;

        // Time Manipulation
        public final ModConfigSpec.IntValue timeBlinkEnergyCost;
        public final ModConfigSpec.IntValue timeBlinkLevelOneCooldownTicks;
        public final ModConfigSpec.IntValue timeBlinkLevelTwoCooldownTicks;
        public final ModConfigSpec.IntValue timeBlinkLevelThreeCooldownTicks;
        public final ModConfigSpec.IntValue timeBlinkLevelFourCooldownTicks;
        public final ModConfigSpec.IntValue timeBlinkLevelFiveCooldownTicks;
        public final ModConfigSpec.IntValue timeBlinkLevelOneDistance;
        public final ModConfigSpec.IntValue timeBlinkLevelTwoDistance;
        public final ModConfigSpec.IntValue timeBlinkLevelThreeDistance;
        public final ModConfigSpec.IntValue timeBlinkLevelFourDistance;
        public final ModConfigSpec.IntValue timeBlinkLevelFiveDistance;
        public final ModConfigSpec.IntValue timeStopLevelThreeEnergyCost;
        public final ModConfigSpec.IntValue timeStopLevelFourEnergyCost;
        public final ModConfigSpec.IntValue timeStopLevelFiveEnergyCost;
        public final ModConfigSpec.IntValue timeStopLevelThreeCooldownTicks;
        public final ModConfigSpec.IntValue timeStopLevelFourCooldownTicks;
        public final ModConfigSpec.IntValue timeStopLevelFiveCooldownTicks;
        public final ModConfigSpec.IntValue timeStopLevelThreeDurationSeconds;
        public final ModConfigSpec.IntValue timeStopLevelFourDurationSeconds;
        public final ModConfigSpec.IntValue timeStopLevelFiveDurationSeconds;
        public final ModConfigSpec.DoubleValue timeStopRadius;
        public final ModConfigSpec.IntValue timeSnapshotEnergyCost;
        public final ModConfigSpec.IntValue timeSnapshotTravelEnergyCost;
        public final ModConfigSpec.IntValue timeSnapshotCooldownTicks;
        public final ModConfigSpec.IntValue timeSnapshotTravelCooldownTicks;
        public final ModConfigSpec.IntValue timeSnapshotWidthChunks;
        public final ModConfigSpec.IntValue timeSnapshotCopyBlocksPerTick;
        public final ModConfigSpec.DoubleValue timeSnapshotGroupTravelRadius;
        public final ModConfigSpec.IntValue timeSlowEnergyCost;
        public final ModConfigSpec.IntValue timeSlowCooldownTicks;
        public final ModConfigSpec.IntValue timeSlowDurationSeconds;
        public final ModConfigSpec.DoubleValue timeSlowRadius;
        public final ModConfigSpec.IntValue timeSlowLevelOneTickIntervalTicks;
        public final ModConfigSpec.IntValue timeSlowLevelTwoTickIntervalTicks;
        public final ModConfigSpec.IntValue timeSlowLevelThreeTickIntervalTicks;
        public final ModConfigSpec.IntValue timeSlowLevelFourTickIntervalTicks;
        public final ModConfigSpec.IntValue timeSlowLevelFiveTickIntervalTicks;
        public final ModConfigSpec.IntValue quakeShockwaveEnergyCost;
        public final ModConfigSpec.IntValue quakeShockwaveCooldownTicks;
        public final ModConfigSpec.IntValue quakeSlamEnergyCost;
        public final ModConfigSpec.IntValue quakeSlamCooldownTicks;
        public final ModConfigSpec.IntValue quakeTremorSenseEnergyCost;
        public final ModConfigSpec.IntValue quakeTremorSenseCooldownTicks;
        public final ModConfigSpec.IntValue quakeEarthquakeEnergyCost;
        public final ModConfigSpec.IntValue quakeEarthquakeCooldownTicks;
        public final ModConfigSpec.IntValue quakeEarthquakeDurationSeconds;
        public final ModConfigSpec.DoubleValue quakeEarthquakeRadius;
        public final ModConfigSpec.BooleanValue quakeShattersFragileBlocks;
        public final ModConfigSpec.IntValue quakeBlastEnergyCost;
        public final ModConfigSpec.IntValue quakeBlastDrainPerSecond;
        public final ModConfigSpec.IntValue quakeBlastCooldownTicks;
        public final ModConfigSpec.IntValue flightDiveEnergyCost;
        public final ModConfigSpec.IntValue flightDiveCooldownTicks;
        /** Rewind is a once-in-a-while power: a full tank of stamina and a long recovery, shorter at Level 5. */
        public final ModConfigSpec.IntValue timeRewindEnergyCost;
        public final ModConfigSpec.IntValue timeRewindCooldownLevel4Seconds, timeRewindCooldownLevel5Seconds;
        public final ModConfigSpec.IntValue regenerationAuraEnergyCost;
        public final ModConfigSpec.IntValue regenerationAuraCooldownTicks;
        public final ModConfigSpec.IntValue electroBallEnergyCost;
        public final ModConfigSpec.IntValue electroBallCooldownTicks;
        public final ModConfigSpec.IntValue telekinesisDisarmEnergyCost;
        public final ModConfigSpec.IntValue telekinesisDisarmCooldownTicks;
        public final ModConfigSpec.IntValue telepathyReadEnergyCost;
        public final ModConfigSpec.IntValue telepathyReadCooldownTicks;
        public final ModConfigSpec.IntValue telepathySuggestionEnergyCost;
        public final ModConfigSpec.IntValue telepathySuggestionCooldownTicks;
        public final ModConfigSpec.IntValue telepathyLinkEnergyCost;
        public final ModConfigSpec.IntValue telepathyLinkCooldownTicks;
        public final ModConfigSpec.IntValue telepathyPuppetEnergyCost;
        public final ModConfigSpec.IntValue telepathyPuppetCooldownTicks;
        public final ModConfigSpec.IntValue telepathyNightmareEnergyCost;
        public final ModConfigSpec.IntValue telepathyNightmareCooldownTicks;
        public final ModConfigSpec.IntValue cryoBoltEnergyCost;
        public final ModConfigSpec.IntValue cryoBoltCooldownTicks;
        public final ModConfigSpec.IntValue cryoFreezeEnergyCost;
        public final ModConfigSpec.IntValue cryoFreezeCooldownTicks;
        public final ModConfigSpec.IntValue cryoWallEnergyCost;
        public final ModConfigSpec.IntValue cryoWallCooldownTicks;
        public final ModConfigSpec.IntValue cryoSpikesEnergyCost;
        public final ModConfigSpec.IntValue cryoSpikesCooldownTicks;
        public final ModConfigSpec.IntValue cryoBlizzardEnergyCost;
        public final ModConfigSpec.IntValue cryoBlizzardCooldownTicks;
        public final ModConfigSpec.IntValue radiationTouchEnergyCost;
        public final ModConfigSpec.IntValue radiationTouchCooldownTicks;
        public final ModConfigSpec.IntValue radiationPulseEnergyCost;
        public final ModConfigSpec.IntValue radiationPulseCooldownTicks;
        public final ModConfigSpec.IntValue radiationEmpEnergyCost;
        public final ModConfigSpec.IntValue radiationEmpCooldownTicks;
        public final ModConfigSpec.IntValue radiationBeamEnergyCost;
        public final ModConfigSpec.IntValue radiationBeamCooldownTicks;
        public final ModConfigSpec.IntValue radiationDetonationEnergyCost;
        public final ModConfigSpec.IntValue radiationDetonationCooldownTicks;
        public final ModConfigSpec.IntValue electroChainEnergyCost;
        public final ModConfigSpec.IntValue electroChainCooldownTicks;
        public final ModConfigSpec.IntValue electroThunderstrikeEnergyCost;
        public final ModConfigSpec.IntValue electroThunderstrikeCooldownTicks;
        public final ModConfigSpec.IntValue electroRideEnergyCost;
        public final ModConfigSpec.IntValue electroRideCooldownTicks;
        public final ModConfigSpec.IntValue electroStormEnergyCost;
        public final ModConfigSpec.IntValue electroStormCooldownTicks;
        public final ModConfigSpec.BooleanValue pyroWorldFire;
        public final ModConfigSpec.BooleanValue formulaAllowForcedDraws;
        public final ModConfigSpec.IntValue formulaForcedDrawCooldownSeconds;
        public final ModConfigSpec.DoubleValue formulaDonatedSuccess, formulaForcedSuccess;
        public final ModConfigSpec.IntValue pyroFlareEnergyCost;
        public final ModConfigSpec.IntValue pyroFlareCooldownTicks;
        public final ModConfigSpec.IntValue pyroPalmEnergyCost;
        public final ModConfigSpec.IntValue pyroPalmCooldownTicks;
        public final ModConfigSpec.IntValue pyroRingEnergyCost;
        public final ModConfigSpec.IntValue pyroRingCooldownTicks;
        public final ModConfigSpec.IntValue pyroBackdraftEnergyCost;
        public final ModConfigSpec.IntValue pyroBackdraftCooldownTicks;
        public final ModConfigSpec.IntValue pyroInfernoEnergyCost;
        public final ModConfigSpec.IntValue pyroInfernoCooldownTicks;
        public final ModConfigSpec.IntValue telekinesisPushEnergyCost;
        public final ModConfigSpec.IntValue telekinesisPushCooldownTicks;
        public final ModConfigSpec.IntValue telekinesisGripEnergyCost;
        public final ModConfigSpec.IntValue telekinesisGripCooldownTicks;
        public final ModConfigSpec.IntValue telekinesisBulletStopEnergyCost;
        public final ModConfigSpec.IntValue telekinesisBulletStopCooldownTicks;
        public final ModConfigSpec.IntValue telekinesisStormEnergyCost;
        public final ModConfigSpec.IntValue telekinesisStormCooldownTicks;

        private Common(ModConfigSpec.Builder builder) {
            builder.push("bunker");
            bunkerEnabled = builder.comment("New players wake sealed in a Cryochamber in the stasis bunker under world spawn.",
                            "The bunker is built once, the first time a player joins.")
                    .define("enabled", true);
            builder.pop();
            builder.push("sylar");
            sylarEnabled = builder.comment("Enable the unique Sylar storyline hunter in the Overworld.").define("enabled", true);
            sylarCanAcquirePowers = builder.comment("Allow completed, interruptible encounters to teach Sylar NPC copies; player roles remain exclusive.")
                    .define("canAcquirePowers", true);
            sylarFirstEncounterSeconds = builder.comment("Minimum grace after an awakened player becomes eligible, before the first ominous hint.")
                    .defineInRange("firstEncounterSeconds", 300, 60, 86400);
            sylarSightingIntervalSeconds = builder.comment("Minimum pause between the early story beats (the ticking, the sightings, his first words); up to four minutes of random delay is added.")
                    .defineInRange("sightingIntervalSeconds", 600, 60, 86400);
            sylarHuntIntervalSeconds = builder.comment("Minimum pause between hunts once he has started hunting a player; up to five minutes of random delay is added. Defeating him gives the whole world an hour.")
                    .defineInRange("huntIntervalSeconds", 1500, 120, 86400);
            builder.pop();
            builder.push("progression");

            maximumLevel = builder
                    .comment("The highest character level a player can reach.")
                    .defineInRange("maximumLevel", 5, 1, 1_000);

            // Key names changed with the energy-based rebalance, so existing
            // config files pick up the new defaults instead of the old curve.
            baseMasteryPerLevel = builder
                    .comment("Mastery required to advance from level 1 to level 2. "
                            + "With the defaults the curve is 800 / 2400 / 4800 / 8000 "
                            + "total, roughly 2-3 hours of active play to reach level 5.")
                    .defineInRange(
                            "masteryForLevelTwo",
                            800L,
                            1L,
                            Long.MAX_VALUE
                    );

            masteryIncreasePerLevel = builder
                    .comment("Additional Mastery required for each later level.")
                    .defineInRange(
                            "masteryIncreaseEachLevel",
                            800L,
                            0L,
                            Long.MAX_VALUE
                    );

            masteryPerEnergySpent = builder
                    .comment("Mastery earned per point of energy spent on powers. Using a power "
                            + "on something (hitting and defeating enemies, saving allies, finding "
                            + "story notes) earns more; plain stamina spent counts for less.")
                    .defineInRange("powerUseMasteryPerEnergy", 0.5D, 0.0D, 100.0D);

            activeMasteryIntervalTicks = builder
                    .comment("Ticks of active, non-AFK play required for an active-time Mastery award.")
                    .defineInRange("activeMasteryIntervalTicks", 20 * 60, 20, 20 * 60 * 60);

            activeMasteryAmount = builder
                    .comment("Mastery awarded for each active-time interval.")
                    .defineInRange("activeMasteryPerInterval", 3, 1, 1_000);

            mobKillMasteryPerTenHealth = builder
                    .comment("Mastery for a hostile-mob kill per 10 points of the mob's "
                            + "maximum health (a zombie earns this once, a Warden 25 times).")
                    .defineInRange("mobKillMasteryPerTenHealth", 3, 0, 1_000);

            mobKillWindowTicks = builder
                    .comment("Rolling window used to reduce repeated kills of the same hostile-mob type.")
                    .defineInRange("mobKillWindowTicks", 20 * 60 * 10, 20, 20 * 60 * 60);

            mobKillFullRewardLimit = builder
                    .comment("Same-type kills in the rolling window that grant full Mastery before diminishing returns.")
                    .defineInRange("mobKillFullRewardLimit", 6, 1, 1_000);

            mobKillMaximumRewardedKills = builder
                    .comment("Maximum same-type kills in the rolling window that can grant any Mastery.")
                    .defineInRange("mobKillMaximumRewardedKills", 12, 1, 1_000);

            builder.pop();

            builder.push("energy");

                baseEnergy = builder
                        .comment("Energy available to a level-one player.")
                        .defineInRange("baseEnergy", 100, 1, 10_000);

                energyPerLevel = builder
                        .comment("Additional maximum energy gained for each character level after level one.")
                        .defineInRange("energyPerLevel", 20, 0, 1_000);

                energyRegenerationPerSecond = builder
                        .comment("Energy naturally restored every second.")
                        .defineInRange("energyRegenerationPerSecond", 1, 0, 1_000);

                energyRegenerationBonusEveryLevels = builder
                        .comment("Every this many levels, energy regenerates 1 point faster per second, "
                                + "so higher-level powers stay usable. 0 disables the bonus.")
                        .defineInRange("energyRegenerationBonusEveryLevels", 2, 0, 1_000);

                builder.pop();
        
                builder.push("regeneration");

                regenerationEnergyCost = builder
                        .comment("Energy consumed each time Regeneration restores health.")
                        .defineInRange("energyCost", 3, 0, 1_000);

                regenerationIntervalTicks = builder
                        .comment("Ticks between each Regeneration heal. Twenty ticks equal one second.")
                        .defineInRange("healIntervalTicks", 40, 1, 20 * 60);

                regenerationDamageDelayTicks = builder
                        .comment("Ticks the player must go without taking damage before Regeneration begins.")
                        .defineInRange("damageDelayTicks", 20 * 6, 0, 20 * 60);

                regenerationMendingEnergyCost = builder
                        .comment("Energy consumed by Rapid Mending.")
                        .defineInRange("mendingEnergyCost", 25, 0, 1_000);

                regenerationMendingCooldownTicks = builder
                        .comment("Cooldown in ticks after Rapid Mending.")
                        .defineInRange("mendingCooldownTicks", 20 * 20, 0, 20 * 60 * 10);

                regenerationBloodGiftEnergyCost = builder
                        .comment("Energy consumed by Blood Gift.")
                        .defineInRange("bloodGiftEnergyCost", 30, 0, 1_000);

                regenerationBloodGiftCooldownTicks = builder
                        .comment("Cooldown in ticks after Blood Gift.")
                        .defineInRange("bloodGiftCooldownTicks", 20 * 30, 0, 20 * 60 * 10);

                regenerationIndestructibleEnergyCost = builder
                        .comment("Energy consumed by Indestructible.")
                        .defineInRange("indestructibleEnergyCost", 40, 0, 1_000);

                regenerationIndestructibleCooldownTicks = builder
                        .comment("Cooldown in ticks after Indestructible.")
                        .defineInRange("indestructibleCooldownTicks", 20 * 60, 0, 20 * 60 * 10);

                regenerationResurrectionCooldownTicks = builder
                        .comment("Cooldown in ticks before Resurrection can save the player from death again.")
                        .defineInRange("resurrectionCooldownTicks", 20 * 60 * 10, 0, 20 * 60 * 120);

                builder.pop();

                builder.push("overexertion");

                // Base damage is measured in Minecraft health points.
                // Two health points equals one heart.
                overexertionBaseDamage = builder
                        .comment("Base health damage caused by overexertion.")
                        .defineInRange("baseDamage", 2.0D, 0.0D, 20.0D);

                // The more energy an ability is short by, the more damage it causes.
                overexertionDamagePerMissingEnergy = builder
                        .comment("Additional damage for each missing point of energy.")
                        .defineInRange("damagePerMissingEnergy", 0.25D, 0.0D, 10.0D);

                overexertionWeaknessDurationSeconds = builder
                        .comment("Duration of Weakness I after overexertion.")
                        .defineInRange("weaknessDurationSeconds", 15, 0, 300);

                // Ten seconds is the requested default Nausea duration.
                overexertionNauseaDurationSeconds = builder
                        .comment("Duration of Nausea I after overexertion.")
                        .defineInRange("nauseaDurationSeconds", 10, 0, 300);

                builder.pop();

                builder.push("eclipse");

                eclipseIntervalTicks = builder
                        .comment("Ticks between Eclipse starts. 144000 ticks is six Minecraft days, or about two real hours.")
                        .defineInRange("intervalTicks", 144_000L, 20L, Long.MAX_VALUE);

                eclipseDurationTicks = builder
                        .comment("How long an Eclipse remains active. 6000 ticks is about five real minutes.")
                        .defineInRange("durationTicks", 6_000L, 20L, Long.MAX_VALUE);

                eclipseDormantAbilityChance = builder
                        .comment("Chance for an eligible player to receive one dormant ability during an Eclipse.")
                        .defineInRange("dormantAbilityChance", 0.40D, 0.0D, 1.0D);

                builder.pop();

                builder.push("awakening");

                awakeningHealthThreshold = builder
                        .comment("A dormant power awakens when health falls strictly below this many health points. Four points equals two hearts.")
                        .defineInRange("healthThreshold", 4.0D, 0.1D, 20.0D);

                builder.pop();

                builder.push("flight");

                flightLaunchEnergyCost = builder
                        .comment("Stamina consumed when Skyburst begins charging.")
                        .defineInRange("launchEnergyCost", 18, 0, 1_000);

                flightLaunchCooldownTicks = builder
                        .comment("Cooldown in ticks after beginning Skyburst.")
                        .defineInRange("launchCooldownTicks", 20 * 3, 0, 20 * 60);

                flightLaunchAscentDurationSeconds = builder
                        .comment("Legacy ascent setting retained for config compatibility. Skyburst now uses a short twelve-tick takeoff.")
                        .defineInRange("launchAscentDurationSeconds", 3, 1, 30);

                flightAscentSpeed = builder
                        .comment("Per-tick speed applied during Flight Launch's ascent phase.")
                        .defineInRange("ascentSpeed", 0.85D, 0.01D, 4.0D);

                flightForwardSpeed = builder
                        .comment("Skyburst tap speed. Maneuver velocity is bounded to 3.6 blocks per tick.")
                        .defineInRange("forwardSpeed", 2.6D, 0.1D, 70.0D);

                flightCruiseSpeed = builder
                        .comment("Maximum wheel-selected forward Flight speed at Level 5. Lower levels unlock progressively faster settings.")
                        .defineInRange("cruiseSpeed", 2.0D, 0.05D, 4.0D);

                flightEnergyDrainPerSecond = builder
                        .comment("Energy drained every second during normal Flight.")
                        .defineInRange("energyDrainPerSecond", 4, 0, 1_000);

                flightBoostEnergyDrainPerSecond = builder
                        .comment("Energy drained every second while moving forward above normal wheel-selected Flight speed.")
                        .defineInRange("boostEnergyDrainPerSecond", 9, 0, 1_000);

                flightSafeLandingSeconds = builder
                        .comment("Slow Falling duration when Flight ends from zero energy.")
                        .defineInRange("safeLandingSeconds", 10, 0, 60);

                flightTrailIntervalTicks = builder
                        .comment("Ticks between white contrail particle emissions.")
                        .defineInRange("trailIntervalTicks", 1, 1, 20);

                flightCycloneEnergyCost = builder
                        .comment("Stamina consumed to gather Tempest.")
                        .defineInRange("cycloneEnergyCost", 55, 0, 1_000);

                flightCycloneCooldownTicks = builder
                        .comment("Cooldown in ticks after beginning Tempest.")
                        .defineInRange("cycloneCooldownTicks", 20 * 30, 0, 20 * 60 * 10);

                flightGrabEnergyCost = builder
                        .comment("Energy consumed by Skyward Grab. Hurling a held target costs a third of this.")
                        .defineInRange("grabEnergyCost", 30, 0, 1_000);

                flightGrabCooldownTicks = builder
                        .comment("Cooldown in ticks after hurling a target with Skyward Grab.")
                        .defineInRange("grabCooldownTicks", 20 * 5, 0, 20 * 60 * 10);

                flightCycloneDurationSeconds = builder
                        .comment("Active seconds of Tempest after its one-second wind-up.")
                        .defineInRange("cycloneDurationSeconds", 5, 1, 60);

                flightCycloneRadius = builder
                        .comment("Horizontal radius, in blocks, of Flight Cyclone's pull.")
                        .defineInRange("cycloneRadius", 8.0D, 1.0D, 32.0D);

                flightCycloneOrbitRadius = builder
                        .comment("Radius, in blocks, of the Flight user's path around Cyclone.")
                        .defineInRange("cycloneOrbitRadius", 6.5D, 1.0D, 32.0D);

                flightCycloneOrbitSpeed = builder
                        .comment("Radians travelled per tick while circling Flight Cyclone.")
                        .defineInRange("cycloneOrbitSpeed", 0.16D, 0.01D, 1.0D);

                flightCycloneLaunchSpeed = builder
                        .comment("Horizontal speed applied when Cyclone throws its targets outward.")
                        .defineInRange("cycloneLaunchSpeed", 2.25D, 0.1D, 10.0D);

                builder.pop();

                builder.push("time");

                timeBlinkEnergyCost = builder
                        .comment("Energy consumed by Time Manipulation Blink.")
                        .defineInRange("blinkEnergyCost", 25, 0, 1_000);

                timeBlinkLevelOneCooldownTicks = builder
                        .comment("Cooldown in ticks after Level 1 Blink.")
                        .defineInRange("blinkLevel1CooldownTicks", 20 * 6, 0, 20 * 60);

                timeBlinkLevelTwoCooldownTicks = builder
                        .comment("Cooldown in ticks after Level 2 Blink.")
                        .defineInRange("blinkLevel2CooldownTicks", 20 * 3, 0, 20 * 60);

                timeBlinkLevelThreeCooldownTicks = builder
                        .comment("Cooldown in ticks after Level 3 Blink.")
                        .defineInRange("blinkLevel3CooldownTicks", 0, 0, 20 * 60);

                timeBlinkLevelFourCooldownTicks = builder
                        .comment("Cooldown in ticks after Level 4 Blink.")
                        .defineInRange("blinkLevel4CooldownTicks", 0, 0, 20 * 60);

                timeBlinkLevelFiveCooldownTicks = builder
                        .comment("Cooldown in ticks after Level 5 Blink.")
                        .defineInRange("blinkLevel5CooldownTicks", 0, 0, 20 * 60);

                timeBlinkLevelOneDistance = builder
                        .comment("Base Blink distance at Level 1; the shared ranged-power multiplier doubles this in play.")
                        .defineInRange("blinkLevel1Distance", 16, 2, 150);

                timeBlinkLevelTwoDistance = builder
                        .comment("Base Blink distance at Level 2; the shared ranged-power multiplier doubles this in play.")
                        .defineInRange("blinkLevel2Distance", 45, 2, 150);

                timeBlinkLevelThreeDistance = builder
                        .comment("Base Blink distance at Level 3; the shared ranged-power multiplier doubles this in play.")
                        .defineInRange("blinkLevel3Distance", 80, 2, 150);

                timeBlinkLevelFourDistance = builder
                        .comment("Base Blink distance at Level 4; the shared ranged-power multiplier doubles this in play.")
                        .defineInRange("blinkLevel4Distance", 115, 2, 150);

                timeBlinkLevelFiveDistance = builder
                        .comment("Base Blink distance at Level 5; the shared ranged-power multiplier doubles this in play.")
                        .defineInRange("blinkLevel5Distance", 150, 2, 150);

                timeStopLevelThreeEnergyCost = builder
                        .comment("Energy consumed by Level 3 Time Stop.")
                        .defineInRange("stopLevel3EnergyCost", 100, 0, 1_000);

                timeStopLevelFourEnergyCost = builder
                        .comment("Energy consumed by Level 4 Time Stop.")
                        .defineInRange("stopLevel4EnergyCost", 125, 0, 1_000);

                timeStopLevelFiveEnergyCost = builder
                        .comment("Energy consumed by Level 5 Time Stop.")
                        .defineInRange("stopLevel5EnergyCost", 150, 0, 1_000);

                timeStopLevelThreeCooldownTicks = builder
                        .comment("Cooldown in ticks after Level 3 Time Stop.")
                        .defineInRange("stopLevel3CooldownTicks", 20 * 90, 0, 20 * 60 * 10);

                timeStopLevelFourCooldownTicks = builder
                        .comment("Cooldown in ticks after Level 4 Time Stop.")
                        .defineInRange("stopLevel4CooldownTicks", 20 * 120, 0, 20 * 60 * 10);

                timeStopLevelFiveCooldownTicks = builder
                        .comment("Cooldown in ticks after Level 5 Time Stop.")
                        .defineInRange("stopLevel5CooldownTicks", 20 * 180, 0, 20 * 60 * 10);

                timeStopLevelThreeDurationSeconds = builder
                        .comment("Maximum seconds Level 3 Time Stop remains active.")
                        .defineInRange("stopLevel3DurationSeconds", 20, 1, 60);

                timeStopLevelFourDurationSeconds = builder
                        .comment("Maximum seconds Level 4 Time Stop remains active.")
                        .defineInRange("stopLevel4DurationSeconds", 40, 1, 60);

                timeStopLevelFiveDurationSeconds = builder
                        .comment("Maximum seconds Level 5 Time Stop remains active.")
                        .defineInRange("stopLevel5DurationSeconds", 60, 1, 60);

                timeStopRadius = builder
                        .comment("Radius, in blocks, of a Time Stop field.")
                        .defineInRange("stopRadius", 50.0D, 1.0D, 128.0D);

                timeSnapshotEnergyCost = builder
                        .comment("Energy consumed when creating or replacing a Temporal Snapshot.")
                        .defineInRange("snapshotEnergyCost", 100, 0, 1_000);

                timeSnapshotTravelEnergyCost = builder
                        .comment("Energy consumed when travelling into a Temporal Snapshot. Returning to the present is free.")
                        .defineInRange("snapshotTravelEnergyCost", 40, 0, 1_000);

                timeSnapshotCooldownTicks = builder
                        .comment("Cooldown in ticks after creating or replacing a Temporal Snapshot.")
                        .defineInRange("snapshotCooldownTicks", 20 * 60 * 20, 0, 20 * 60 * 30);

                timeSnapshotTravelCooldownTicks = builder
                        .comment("Cooldown in ticks after travelling between the present and a Temporal Snapshot.")
                        .defineInRange("snapshotTravelCooldownTicks", 20 * 30, 0, 20 * 60 * 30);

                timeSnapshotWidthChunks = builder
                        .comment("Width of each Temporal Snapshot area, in chunks.")
                        .defineInRange("snapshotWidthChunks", 12, 2, 24);

                timeSnapshotCopyBlocksPerTick = builder
                        .comment("Snapshot block states copied per server tick while a snapshot is forming.")
                        .defineInRange("snapshotCopyBlocksPerTick", 8_192, 256, 32_768);

                timeSnapshotGroupTravelRadius = builder
                        .comment("Radius, in blocks, of players brought along during Temporal Snapshot travel.")
                        .defineInRange("snapshotGroupTravelRadius", 6.0D, 1.0D, 32.0D);

                timeSlowEnergyCost = builder
                        .comment("Energy consumed to create a Time Slow field.")
                        .defineInRange("slowEnergyCost", 85, 0, 1_000);

                timeSlowCooldownTicks = builder
                        .comment("Cooldown in ticks after using Time Slow.")
                        .defineInRange(
                                "slowCooldownTicks",
                                20 * 45,
                                0,
                                20 * 60 * 10
                        );

                timeSlowDurationSeconds = builder
                        .comment("Seconds a Time Slow field remains active.")
                        .defineInRange("slowDurationSeconds", 10, 1, 60);

                timeSlowRadius = builder
                        .comment("Radius, in blocks, of a Time Slow field.")
                        .defineInRange("slowRadius", 50.0D, 1.0D, 128.0D);

                timeSlowLevelOneTickIntervalTicks = builder
                        .comment("Ticks between updates for Level 1 Time Slow. ")
                        .defineInRange("slowLevel1TickIntervalTicks", 7, 1, 100);

                timeSlowLevelTwoTickIntervalTicks = builder
                        .comment("Ticks between updates for Level 2 Time Slow.")
                        .defineInRange("slowLevel2TickIntervalTicks", 9, 1, 100);

                timeSlowLevelThreeTickIntervalTicks = builder
                        .comment("Ticks between updates for Level 3 Time Slow.")
                        .defineInRange("slowLevel3TickIntervalTicks", 13, 1, 100);

                timeSlowLevelFourTickIntervalTicks = builder
                        .comment("Ticks between updates for Level 4 Time Slow.")
                        .defineInRange("slowLevel4TickIntervalTicks", 17, 1, 100);

                timeSlowLevelFiveTickIntervalTicks = builder
                        .comment("Ticks between updates for Level 5 Time Slow.")
                        .defineInRange("slowLevel5TickIntervalTicks", 20, 1, 100);

                builder.pop();

                builder.push("quake");

                quakeShockwaveEnergyCost = builder
                        .comment("Energy consumed by a Quake Shockwave.")
                        .defineInRange("shockwaveEnergyCost", 15, 0, 1_000);

                quakeShockwaveCooldownTicks = builder
                        .comment("Cooldown in ticks after a Quake Shockwave.")
                        .defineInRange("shockwaveCooldownTicks", 30, 0, 20 * 60 * 10);

                quakeSlamEnergyCost = builder
                        .comment("Energy consumed by Seismic Slam.")
                        .defineInRange("slamEnergyCost", 35, 0, 1_000);

                quakeSlamCooldownTicks = builder
                        .comment("Cooldown in ticks after Seismic Slam.")
                        .defineInRange("slamCooldownTicks", 20 * 6, 0, 20 * 60 * 10);

                quakeTremorSenseEnergyCost = builder
                        .comment("Energy consumed by Tremor Sense.")
                        .defineInRange("tremorSenseEnergyCost", 30, 0, 1_000);

                quakeTremorSenseCooldownTicks = builder
                        .comment("Cooldown in ticks after Tremor Sense.")
                        .defineInRange("tremorSenseCooldownTicks", 20 * 30, 0, 20 * 60 * 10);

                quakeEarthquakeEnergyCost = builder
                        .comment("Energy consumed by Earthquake.")
                        .defineInRange("earthquakeEnergyCost", 120, 0, 1_000);

                quakeEarthquakeCooldownTicks = builder
                        .comment("Cooldown in ticks after Earthquake.")
                        .defineInRange("earthquakeCooldownTicks", 20 * 120, 0, 20 * 60 * 30);

                quakeEarthquakeDurationSeconds = builder
                        .comment("Seconds an Earthquake keeps shaking before its aftershock.")
                        .defineInRange("earthquakeDurationSeconds", 8, 1, 60);

                quakeEarthquakeRadius = builder
                        .comment("Radius, in blocks, of an Earthquake.")
                        .defineInRange("earthquakeRadius", 16.0D, 4.0D, 48.0D);

                quakeShattersFragileBlocks = builder
                        .comment("Whether Quake powers shatter glass, glass panes and ice. Respects claim/protection mods.")
                        .define("shattersFragileBlocks", true);

                quakeBlastEnergyCost = energy(builder, "quakeBlast", 10);
                quakeBlastDrainPerSecond = builder
                        .comment("Energy drained every second while Quake Blast is held.")
                        .defineInRange("quakeBlastDrainPerSecond", 8, 0, 1_000);
                quakeBlastCooldownTicks = cooldown(builder, "quakeBlast", 30);

                builder.pop();

                builder.push("fifthAbilities");
                flightDiveEnergyCost = energy(builder, "flightDiveBomb", 35);
                flightDiveCooldownTicks = cooldown(builder, "flightDiveBomb", 20 * 8);
                // New keys, so existing configs pick up the heavier Rewind instead of keeping 45 stamina / 45 seconds.
                timeRewindEnergyCost = energy(builder, "rewriteTheMoment", 130);
                timeRewindCooldownLevel4Seconds = builder.comment("Seconds before Rewind can be used again at Level 4.")
                        .defineInRange("rewriteTheMomentCooldownLevel4Seconds", 240, 0, 60 * 30);
                timeRewindCooldownLevel5Seconds = builder.comment("Seconds before Rewind can be used again at Level 5 and above.")
                        .defineInRange("rewriteTheMomentCooldownLevel5Seconds", 150, 0, 60 * 30);
                regenerationAuraEnergyCost = energy(builder, "healingAura", 45);
                regenerationAuraCooldownTicks = cooldown(builder, "healingAura", 20 * 60);
                electroBallEnergyCost = energy(builder, "ballLightning", 25);
                electroBallCooldownTicks = cooldown(builder, "ballLightning", 20 * 3);
                telekinesisDisarmEnergyCost = energy(builder, "disarm", 20);
                telekinesisDisarmCooldownTicks = cooldown(builder, "disarm", 20 * 8);
                builder.pop();

                builder.push("telepathy");
                telepathyReadEnergyCost = energy(builder, "readMind", 10);
                telepathyReadCooldownTicks = cooldown(builder, "readMind", 20 * 3);
                telepathySuggestionEnergyCost = energy(builder, "suggestion", 25);
                telepathySuggestionCooldownTicks = cooldown(builder, "suggestion", 20 * 12);
                telepathyLinkEnergyCost = energy(builder, "mindLink", 35);
                telepathyLinkCooldownTicks = cooldown(builder, "mindLink", 20 * 60);
                telepathyPuppetEnergyCost = energy(builder, "puppet", 50);
                telepathyPuppetCooldownTicks = cooldown(builder, "puppet", 20 * 45);
                telepathyNightmareEnergyCost = energy(builder, "nightmare", 90);
                telepathyNightmareCooldownTicks = cooldown(builder, "nightmare", 20 * 90);
                builder.pop();

                builder.push("cryokinesis");
                cryoBoltEnergyCost = energy(builder, "frostBolt", 14);
                cryoBoltCooldownTicks = cooldown(builder, "frostBolt", 20);
                cryoFreezeEnergyCost = energy(builder, "flashFreeze", 30);
                cryoFreezeCooldownTicks = cooldown(builder, "flashFreeze", 20 * 10);
                cryoWallEnergyCost = energy(builder, "iceWall", 35);
                cryoWallCooldownTicks = cooldown(builder, "iceWall", 20 * 15);
                cryoSpikesEnergyCost = energy(builder, "glacialSpikes", 45);
                cryoSpikesCooldownTicks = cooldown(builder, "glacialSpikes", 20 * 12);
                cryoBlizzardEnergyCost = energy(builder, "blizzard", 110);
                cryoBlizzardCooldownTicks = cooldown(builder, "blizzard", 20 * 120);
                builder.pop();

                builder.push("radiation");
                radiationTouchEnergyCost = energy(builder, "radiantTouch", 15);
                radiationTouchCooldownTicks = cooldown(builder, "radiantTouch", 20);
                radiationPulseEnergyCost = energy(builder, "radiationPulse", 30);
                radiationPulseCooldownTicks = cooldown(builder, "radiationPulse", 20 * 8);
                radiationEmpEnergyCost = energy(builder, "emp", 30);
                radiationEmpCooldownTicks = cooldown(builder, "emp", 20 * 20);
                radiationBeamEnergyCost = energy(builder, "meltdownBeam", 25);
                radiationBeamCooldownTicks = cooldown(builder, "meltdownBeam", 20 * 6);
                radiationDetonationEnergyCost = energy(builder, "explodingMan", 140);
                radiationDetonationCooldownTicks = cooldown(builder, "explodingMan", 20 * 600);
                builder.pop();

                builder.push("electrokinesis");
                electroChainEnergyCost = energy(builder, "chainLightning", 18);
                electroChainCooldownTicks = cooldown(builder, "chainLightning", 25);
                electroThunderstrikeEnergyCost = energy(builder, "thunderstrike", 45);
                electroThunderstrikeCooldownTicks = cooldown(builder, "thunderstrike", 20 * 10);
                electroRideEnergyCost = energy(builder, "rideTheLightning", 30);
                electroRideCooldownTicks = cooldown(builder, "rideTheLightning", 20 * 5);
                electroStormEnergyCost = energy(builder, "stormSurge", 110);
                electroStormCooldownTicks = cooldown(builder, "stormSurge", 20 * 120);
                builder.pop();

                builder.push("telekinesis");
                telekinesisPushEnergyCost = energy(builder, "push", 15);
                telekinesisPushCooldownTicks = cooldown(builder, "push", 30);
                telekinesisGripEnergyCost = energy(builder, "grip", 30);
                telekinesisGripCooldownTicks = cooldown(builder, "grip", 20 * 10);
                telekinesisBulletStopEnergyCost = energy(builder, "bulletStop", 40);
                telekinesisBulletStopCooldownTicks = cooldown(builder, "bulletStop", 20 * 30);
                telekinesisStormEnergyCost = energy(builder, "telekineticStorm", 110);
                telekinesisStormCooldownTicks = cooldown(builder, "telekineticStorm", 20 * 120);
                builder.pop();

                builder.push("formula");
                formulaAllowForcedDraws = builder.comment("Allow blood to be drawn from a helpless Regeneration owner without consent.",
                                "Forced draws still respect PvP and scoreboard-team rules.")
                        .define("allowForcedDraws", true);
                formulaForcedDrawCooldownSeconds = builder.comment("How long one Regeneration owner is protected after a forced draw.")
                        .defineInRange("forcedDrawCooldownSeconds", 600, 0, 60 * 60 * 24);
                formulaDonatedSuccess = builder.comment("Chance an injection awakens a power when its blood was donated.")
                        .defineInRange("donatedSuccessChance", 0.85, 0, 1);
                formulaForcedSuccess = builder.comment("Chance an injection awakens a power when its blood was taken by force.")
                        .defineInRange("forcedSuccessChance", 0.60, 0, 1);
                builder.pop();

                builder.push("pyrokinesis");
                pyroWorldFire = builder.comment("Pyrokinetic fire is real, spreading vanilla fire. It still respects",
                                "protection, build permissions and the doFireTick gamerule. False makes every",
                                "pyrokinetic flame cosmetic: it burns creatures but never places fire blocks.")
                        .define("worldFire", true);
                pyroFlareEnergyCost = energy(builder, "flare", 14);
                pyroFlareCooldownTicks = cooldown(builder, "flare", 25);
                pyroPalmEnergyCost = energy(builder, "flamePalm", 12);
                pyroPalmCooldownTicks = cooldown(builder, "flamePalm", 20 * 4);
                pyroRingEnergyCost = energy(builder, "ringOfFire", 35);
                pyroRingCooldownTicks = cooldown(builder, "ringOfFire", 20 * 15);
                pyroBackdraftEnergyCost = energy(builder, "backdraft", 25);
                pyroBackdraftCooldownTicks = cooldown(builder, "backdraft", 20 * 5);
                pyroInfernoEnergyCost = energy(builder, "inferno", 110);
                pyroInfernoCooldownTicks = cooldown(builder, "inferno", 20 * 120);
                builder.pop();
        }
    }
}
