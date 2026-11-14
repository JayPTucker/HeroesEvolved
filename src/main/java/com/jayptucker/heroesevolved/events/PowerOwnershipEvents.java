package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.data.PowerOwnershipSavedData;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.Comparator;
import java.util.List;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class PowerOwnershipEvents {
    private PowerOwnershipEvents() {}
    @SubscribeEvent public static void started(ServerStartedEvent event) { PowerOwnershipSavedData.get(event.getServer()); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var assigned = PlayerAbilityService.getData(player).assignedPower();
        if (assigned.isEmpty()) return;
        var ownership = PowerOwnershipSavedData.get(player.getServer());
        if (!AbilityRegistry.ABILITIES.containsKey(assigned.get().getKey())) {
            retired(player, assigned.get().getKey(), assigned.get().getValue().isUnlocked(), ownership);
            return;
        }
        if (ownership.claim(assigned.get().getKey(), player.getUUID())) return;
        // Migrate older worlds with duplicate assignments before that player can use the duplicate.
        PlayerAbilityService.removeAssignedAbility(player);
        var replacement = AbilityRegistry.ABILITIES.keySet().stream().sorted()
                .filter(com.jayptucker.heroesevolved.ability.registry.ModAbilities::rollable)
                .filter(power -> ownership.available(power, player.getUUID())).findFirst();
        if (replacement.isPresent() && PlayerAbilityService.assignDormantAbility(player, replacement.get()))
            player.sendSystemMessage(Component.literal("Your old power belongs to someone else. A different, unique power lies dormant within you."));
        else player.sendSystemMessage(Component.literal("Your old power already has its unique owner. No unclaimed powers remain."));
    }

    /**
     * A saved power that no longer exists (Precognition was removed in November 2026) is swapped for a random unclaimed
     * one: awakened if the old one was, and Evolved if the old one was. Power Level and mastery are the player's own and
     * carry over unchanged; the new power's skill tree starts empty, with every point to spend.
     */
    private static void retired(ServerPlayer player, ResourceLocation old, boolean awake, PowerOwnershipSavedData ownership) {
        var journey = player.getData(ModDataAttachments.POWER_JOURNEY);
        boolean evolved = journey.isEvolved(old.getPath());
        PlayerAbilityService.removeAssignedAbility(player);
        ownership.release(old);
        List<ResourceLocation> open = AbilityRegistry.ABILITIES.keySet().stream()
                .filter(com.jayptucker.heroesevolved.ability.registry.ModAbilities::rollable)
                .filter(power -> ownership.available(power, player.getUUID()))
                .sorted(Comparator.comparing(ResourceLocation::toString)).toList();
        if (open.isEmpty()) {
            player.sendSystemMessage(Component.literal("The power you carried is gone from this world, and no unclaimed powers remain."));
            return;
        }
        ResourceLocation power = open.get(player.getRandom().nextInt(open.size()));
        boolean given = awake ? PlayerAbilityService.replaceWithAbility(player, power) : PlayerAbilityService.assignDormantAbility(player, power);
        if (!given) return;
        if (evolved) player.setData(ModDataAttachments.POWER_JOURNEY, journey.evolve(power.getPath()));
        if (awake) {
            PlayerPowerSyncService.sync(player);
            player.sendSystemMessage(Component.literal("The future you saw has closed. A different power has woken in you: ")
                    .append(Component.translatable("ability." + power.getNamespace() + "." + power.getPath()))
                    .append(". Your Power Level is unchanged, and its skill tree is ready to spend."));
        } else {
            player.sendSystemMessage(Component.literal("The power sleeping in you has changed. A different one lies dormant now."));
        }
    }
}
