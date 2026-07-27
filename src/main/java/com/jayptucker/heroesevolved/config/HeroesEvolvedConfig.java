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
        }
    }
}
