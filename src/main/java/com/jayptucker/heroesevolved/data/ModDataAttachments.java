package com.jayptucker.heroesevolved.data;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import com.jayptucker.heroesevolved.progression.PlayerProgressionData;
import com.jayptucker.heroesevolved.energy.PlayerEnergyData;
import com.jayptucker.heroesevolved.combat.PlayerCombatData;
import com.jayptucker.heroesevolved.cooldown.PlayerCooldownData;
import com.jayptucker.heroesevolved.flight.PlayerFlightData;
import com.jayptucker.heroesevolved.time.PlayerTemporalSnapshotData;
import com.jayptucker.heroesevolved.time.PlayerTemporalVisitData;
import com.mojang.serialization.Codec;

import java.util.function.Supplier;

public final class ModDataAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, HeroesEvolved.MOD_ID);

    public static final Supplier<AttachmentType<PlayerAbilityData>> PLAYER_ABILITIES =
        ATTACHMENTS.register("player_abilities", () ->
            AttachmentType.<PlayerAbilityData>builder(PlayerAbilityData::empty)
                .serialize(PlayerAbilityData.CODEC)
                .copyOnDeath()
                .build()
        );

    private ModDataAttachments() {

    }

    public static final Supplier<AttachmentType<PlayerProgressionData>> PLAYER_PROGRESSION =
        ATTACHMENTS.register("player_progression", () ->
            AttachmentType.<PlayerProgressionData>builder(PlayerProgressionData::empty)
                .serialize(PlayerProgressionData.CODEC)
                .copyOnDeath()
                .build()
    );
    
    public static final Supplier<AttachmentType<PlayerEnergyData>> PLAYER_ENERGY =
        ATTACHMENTS.register("player_energy", () ->
            AttachmentType.<PlayerEnergyData>builder(PlayerEnergyData::initial)
                .serialize(PlayerEnergyData.CODEC)
                .copyOnDeath()
                .build()
    );

    // Operator-controlled test state, saved with the player between relogs.
    public static final Supplier<AttachmentType<Boolean>> PLAYER_UNLIMITED_ENERGY =
        ATTACHMENTS.register("player_unlimited_energy", () ->
            AttachmentType.<Boolean>builder(() -> false)
                .serialize(Codec.BOOL)
                .build()
        );

    // Stores the last Eclipse that gave this player an ability-roll attempt.
    // It prevents reconnecting players from rerolling during the same event.
    // How many of the Telepathy storyline's memories this player has heard.
    public static final Supplier<AttachmentType<Integer>> PLAYER_STORY_PROGRESS =
        ATTACHMENTS.register("player_story_progress", () ->
            AttachmentType.<Integer>builder(() -> 0)
                .serialize(Codec.INT)
                .copyOnDeath()
                .build()
        );

    // Whether this player has stood in the future city of the time vision,
    // as the time traveller or as someone pulled along with them.
    public static final Supplier<AttachmentType<Boolean>> PLAYER_SEEN_FUTURE_CITY =
        ATTACHMENTS.register("player_seen_future_city", () ->
            AttachmentType.<Boolean>builder(() -> false)
                .serialize(Codec.BOOL)
                .copyOnDeath()
                .build()
        );

    public static final Supplier<AttachmentType<Long>> PLAYER_LAST_ECLIPSE_ROLL =
        ATTACHMENTS.register("player_last_eclipse_roll", () ->
            AttachmentType.<Long>builder(() -> -1L)
                .serialize(Codec.LONG)
                .copyOnDeath()
                .build()
        );

    public static final Supplier<AttachmentType<Integer>> PLAYER_RADIATION_STRESS =
        ATTACHMENTS.register("player_radiation_stress", () -> AttachmentType.<Integer>builder(() -> 0)
                .serialize(Codec.intRange(0, 100)).build());

    public static final Supplier<AttachmentType<PlayerCooldownData>> PLAYER_COOLDOWNS =
        ATTACHMENTS.register("player_cooldowns", () ->
            AttachmentType.<PlayerCooldownData>builder(
                    PlayerCooldownData::empty
                )
                .serialize(PlayerCooldownData.CODEC)
                .build()
    );

    public static final Supplier<AttachmentType<PlayerCombatData>> PLAYER_COMBAT =
        ATTACHMENTS.register("player_combat", () ->
            AttachmentType.builder(PlayerCombatData::empty)
                .build()
    );

    public static final Supplier<AttachmentType<PlayerFlightData>> PLAYER_FLIGHT =
        ATTACHMENTS.register("player_flight", () ->
                AttachmentType.builder(PlayerFlightData::empty)
                        .build()
    );

    public static final Supplier<AttachmentType<PlayerTemporalSnapshotData>>
            PLAYER_TEMPORAL_SNAPSHOT = ATTACHMENTS.register(
                    "player_temporal_snapshot",
                    () -> AttachmentType
                            .<PlayerTemporalSnapshotData>builder(
                                    PlayerTemporalSnapshotData::empty
                            )
                            .serialize(PlayerTemporalSnapshotData.CODEC)
                            .copyOnDeath()
                            .build()
            );

    public static final Supplier<AttachmentType<PlayerTemporalVisitData>>
            PLAYER_TEMPORAL_VISIT = ATTACHMENTS.register(
                    "player_temporal_visit",
                    () -> AttachmentType
                            .<PlayerTemporalVisitData>builder(
                                    PlayerTemporalVisitData::empty
                            )
                            .serialize(PlayerTemporalVisitData.CODEC)
                            .copyOnDeath()
                            .build()
            );

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    public static final Supplier<AttachmentType<Boolean>> TIME_INTRO_SEEN =
            ATTACHMENTS.register("time_intro_seen", () -> AttachmentType
                    .<Boolean>builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

    public static final Supplier<AttachmentType<com.jayptucker.heroesevolved.intro.PowerIntroHistory>> POWER_INTRO_HISTORY =
            ATTACHMENTS.register("power_intro_history", () -> AttachmentType
                    .builder(com.jayptucker.heroesevolved.intro.PowerIntroHistory::empty)
                    .serialize(com.jayptucker.heroesevolved.intro.PowerIntroHistory.CODEC).copyOnDeath().build());

    public static final Supplier<AttachmentType<com.jayptucker.heroesevolved.time.intro.TimeIntroVisit>>
            TIME_INTRO_VISIT = ATTACHMENTS.register("time_intro_visit", () -> AttachmentType
                    .builder(com.jayptucker.heroesevolved.time.intro.TimeIntroVisit::empty)
                    .serialize(com.jayptucker.heroesevolved.time.intro.TimeIntroVisit.CODEC)
                    .copyOnDeath().build());
    
    
}
