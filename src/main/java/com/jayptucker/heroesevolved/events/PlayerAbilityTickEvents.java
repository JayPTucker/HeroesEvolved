package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.Ability;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class PlayerAbilityTickEvents {
    private static final int ENERGY_REGENERATION_INTERVAL_TICKS = 20;

    private PlayerAbilityTickEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        long gameTime = player.serverLevel().getGameTime();

        if (gameTime % ENERGY_REGENERATION_INTERVAL_TICKS == 0) {
            PlayerEnergyService.restoreNaturally(player);
        }

        PlayerAbilityData abilityData = PlayerAbilityService.getData(player);

        abilityData.abilities().forEach((abilityId, progress) -> {
            if (!progress.isUnlocked()) {
                return;
            }

            Ability ability = AbilityRegistry.ABILITIES.get(abilityId);
            if (ability == null) {
                return;
            }

            ability.tick(new AbilityUseContext(
                    player,
                    abilityId,
                    progress.level()
            ));
        });
    }
}