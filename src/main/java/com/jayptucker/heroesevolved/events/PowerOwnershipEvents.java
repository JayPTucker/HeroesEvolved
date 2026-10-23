package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.data.PowerOwnershipSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class PowerOwnershipEvents {
    private PowerOwnershipEvents() {}
    @SubscribeEvent public static void started(ServerStartedEvent event) { PowerOwnershipSavedData.get(event.getServer()); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var assigned = PlayerAbilityService.getData(player).assignedPower();
        if (assigned.isEmpty()) return;
        var ownership = PowerOwnershipSavedData.get(player.getServer());
        if (ownership.claim(assigned.get().getKey(), player.getUUID())) return;
        // Migrate older worlds with duplicate assignments before that player can use the duplicate.
        PlayerAbilityService.removeAssignedAbility(player);
        var replacement = AbilityRegistry.ABILITIES.keySet().stream().sorted()
                .filter(power -> ownership.available(power, player.getUUID())).findFirst();
        if (replacement.isPresent() && PlayerAbilityService.assignDormantAbility(player, replacement.get()))
            player.sendSystemMessage(Component.literal("Your old power belongs to someone else. A different, unique power lies dormant within you."));
        else player.sendSystemMessage(Component.literal("Your old power already has its unique owner. No unclaimed powers remain."));
    }
}
