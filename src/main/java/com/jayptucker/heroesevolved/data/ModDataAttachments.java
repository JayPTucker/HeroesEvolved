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

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
    
    
}
