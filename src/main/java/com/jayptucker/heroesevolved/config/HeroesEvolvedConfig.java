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
        public final ModConfigSpec.IntValue timeRewindEnergyCost;
        public final ModConfigSpec.IntValue timeRewindCooldownTicks;
        public final ModConfigSpec.IntValue regenerationAuraEnergyCost;
        public final ModConfigSpec.IntValue regenerationAuraCooldownTicks;
        public final ModConfigSpec.IntValue electroBallEnergyCost;
        public final ModConfigSpec.IntValue electroBallCooldownTicks;
        public final ModConfigSpec.IntValue telekinesisDisarmEnergyCost;
        public final ModConfigSpec.IntValue telekinesisDisarmCooldownTicks;
        public final ModConfigSpec.IntValue electroChainEnergyCost;
        public final ModConfigSpec.IntValue electroChainCooldownTicks;
        public final ModConfigSpec.IntValue electroThunderstrikeEnergyCost;
        public final ModConfigSpec.IntValue electroThunderstrikeCooldownTicks;
        public final ModConfigSpec.IntValue electroRideEnergyCost;
        public final ModConfigSpec.IntValue electroRideCooldownTicks;
        public final ModConfigSpec.IntValue electroStormEnergyCost;
        public final ModConfigSpec.IntValue electroStormCooldownTicks;
        public final ModConfigSpec.IntValue telekinesisPushEnergyCost;
        public final ModConfigSpec.IntValue telekinesisPushCooldownTicks;
        public final ModConfigSpec.IntValue telekinesisGripEnergyCost;
        public final ModConfigSpec.IntValue telekinesisGripCooldownTicks;
        public final ModConfigSpec.IntValue telekinesisBulletStopEnergyCost;
        public final ModConfigSpec.IntValue telekinesisBulletStopCooldownTicks;
        public final ModConfigSpec.IntValue telekinesisStormEnergyCost;
        public final ModConfigSpec.IntValue telekinesisStormCooldownTicks;

        private Common(ModConfigSpec.Builder builder) {
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
                    .comment("Mastery earned per point of energy spent on powers. "
                            + "This is the main Mastery source; energy regeneration "
                            + "naturally limits how fast it can be earned.")
                    .defineInRange("masteryPerEnergySpent", 1.0D, 0.0D, 100.0D);

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
                        .comment("Energy consumed when Launch begins a Flight session.")
                        .defineInRange("launchEnergyCost", 25, 0, 1_000);

                flightLaunchCooldownTicks = builder
                        .comment("Cooldown in ticks after using Flight Launch.")
                        .defineInRange("launchCooldownTicks", 20 * 8, 0, 20 * 60);

                flightLaunchAscentDurationSeconds = builder
                        .comment("Seconds spent steadily ascending before Launch boosts forward.")
                        .defineInRange("launchAscentDurationSeconds", 3, 1, 30);

                flightAscentSpeed = builder
                        .comment("Per-tick speed applied during Flight Launch's ascent phase.")
                        .defineInRange("ascentSpeed", 0.85D, 0.01D, 4.0D);

                flightForwardSpeed = builder
                        .comment("Forward burst speed applied after Flight Launch ascends.")
                        .defineInRange("forwardSpeed", 15.0D, 0.1D, 70.0D);

                flightCruiseSpeed = builder
                        .comment("Forward speed while Flight Boost is active.")
                        .defineInRange("cruiseSpeed", 2.0D, 0.05D, 4.0D);

                flightEnergyDrainPerSecond = builder
                        .comment("Energy drained every second during normal Flight.")
                        .defineInRange("energyDrainPerSecond", 4, 0, 1_000);

                flightBoostEnergyDrainPerSecond = builder
                        .comment("Energy drained every second while Flight Boost is active.")
                        .defineInRange("boostEnergyDrainPerSecond", 15, 0, 1_000);

                flightSafeLandingSeconds = builder
                        .comment("Slow Falling duration when Flight ends from zero energy.")
                        .defineInRange("safeLandingSeconds", 10, 0, 60);

                flightTrailIntervalTicks = builder
                        .comment("Ticks between white contrail particle emissions.")
                        .defineInRange("trailIntervalTicks", 1, 1, 20);

                flightCycloneEnergyCost = builder
                        .comment("Energy consumed to begin Flight Cyclone.")
                        .defineInRange("cycloneEnergyCost", 50, 0, 1_000);

                flightCycloneCooldownTicks = builder
                        .comment("Cooldown in ticks after using Flight Cyclone.")
                        .defineInRange("cycloneCooldownTicks", 20 * 30, 0, 20 * 60 * 10);

                flightGrabEnergyCost = builder
                        .comment("Energy consumed by Skyward Grab. Hurling a held target costs a third of this.")
                        .defineInRange("grabEnergyCost", 30, 0, 1_000);

                flightGrabCooldownTicks = builder
                        .comment("Cooldown in ticks after hurling a target with Skyward Grab.")
                        .defineInRange("grabCooldownTicks", 20 * 5, 0, 20 * 60 * 10);

                flightCycloneDurationSeconds = builder
                        .comment("Seconds Flight Cyclone keeps its targets suspended.")
                        .defineInRange("cycloneDurationSeconds", 10, 1, 60);

                flightCycloneRadius = builder
                        .comment("Horizontal radius, in blocks, of Flight Cyclone's pull.")
                        .defineInRange("cycloneRadius", 12.0D, 1.0D, 32.0D);

                flightCycloneOrbitRadius = builder
                        .comment("Radius, in blocks, of the Flight user's path around Cyclone.")
                        .defineInRange("cycloneOrbitRadius", 16.0D, 1.0D, 32.0D);

                flightCycloneOrbitSpeed = builder
                        .comment("Radians travelled per tick while circling Flight Cyclone.")
                        .defineInRange("cycloneOrbitSpeed", 0.40D, 0.01D, 1.0D);

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
                        .comment("Maximum Blink distance, in blocks, at Level 1.")
                        .defineInRange("blinkLevel1Distance", 16, 2, 150);

                timeBlinkLevelTwoDistance = builder
                        .comment("Maximum Blink distance, in blocks, at Level 2.")
                        .defineInRange("blinkLevel2Distance", 45, 2, 150);

                timeBlinkLevelThreeDistance = builder
                        .comment("Maximum Blink distance, in blocks, at Level 3.")
                        .defineInRange("blinkLevel3Distance", 80, 2, 150);

                timeBlinkLevelFourDistance = builder
                        .comment("Maximum Blink distance, in blocks, at Level 4.")
                        .defineInRange("blinkLevel4Distance", 115, 2, 150);

                timeBlinkLevelFiveDistance = builder
                        .comment("Maximum Blink distance, in blocks, at Level 5.")
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
                timeRewindEnergyCost = energy(builder, "timeRewind", 45);
                timeRewindCooldownTicks = cooldown(builder, "timeRewind", 20 * 45);
                regenerationAuraEnergyCost = energy(builder, "healingAura", 45);
                regenerationAuraCooldownTicks = cooldown(builder, "healingAura", 20 * 60);
                electroBallEnergyCost = energy(builder, "ballLightning", 25);
                electroBallCooldownTicks = cooldown(builder, "ballLightning", 20 * 3);
                telekinesisDisarmEnergyCost = energy(builder, "disarm", 20);
                telekinesisDisarmCooldownTicks = cooldown(builder, "disarm", 20 * 8);
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
        }
    }
}
