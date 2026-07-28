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

    public static final class Common {
        public final ModConfigSpec.IntValue maximumLevel;
        public final ModConfigSpec.LongValue baseMasteryPerLevel;
        public final ModConfigSpec.LongValue masteryIncreasePerLevel;
        public final ModConfigSpec.IntValue baseEnergy;
        public final ModConfigSpec.IntValue energyPerLevel;
        public final ModConfigSpec.IntValue energyRegenerationPerSecond;
        public final ModConfigSpec.IntValue regenerationEnergyCost;
        public final ModConfigSpec.IntValue regenerationIntervalTicks;
        public final ModConfigSpec.IntValue regenerationDamageDelayTicks;

        // Controls how dangerous it is to use an ability without enough energy.
        public final ModConfigSpec.DoubleValue overexertionBaseDamage;
        public final ModConfigSpec.DoubleValue overexertionDamagePerMissingEnergy;
        public final ModConfigSpec.IntValue overexertionWeaknessDurationSeconds;
        public final ModConfigSpec.IntValue overexertionNauseaDurationSeconds;

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

        private Common(ModConfigSpec.Builder builder) {
            builder.push("progression");

            maximumLevel = builder
                    .comment("The highest character level a player can reach.")
                    .defineInRange("maximumLevel", 50, 1, 1_000);

            baseMasteryPerLevel = builder
                    .comment("Mastery required to advance from level 1 to level 2.")
                    .defineInRange(
                            "baseMasteryPerLevel",
                            100L,
                            1L,
                            Long.MAX_VALUE
                    );

            masteryIncreasePerLevel = builder
                    .comment("Additional Mastery required for each later level.")
                    .defineInRange(
                            "masteryIncreasePerLevel",
                            25L,
                            0L,
                            Long.MAX_VALUE
                    );

            builder.pop();

            builder.push("energy");

                baseEnergy = builder
                        .comment("Energy available to a level-one player.")
                        .defineInRange("baseEnergy", 100, 1, 10_000);

                energyPerLevel = builder
                        .comment("Additional maximum energy gained for each character level after level one.")
                        .defineInRange("energyPerLevel", 10, 0, 1_000);

                energyRegenerationPerSecond = builder
                        .comment("Energy naturally restored every second.")
                        .defineInRange("energyRegenerationPerSecond", 1, 0, 1_000);

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
                        .defineInRange("trailIntervalTicks", 2, 1, 20);

                builder.pop();
        }
    }
}
