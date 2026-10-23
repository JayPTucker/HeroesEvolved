package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.electro.ElectroService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class ElectroEvents {
    private ElectroEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(
            net.neoforged.neoforge.event.tick.ServerTickEvent.Post event
    ) {
        ElectroService.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        // Lightning feeds Elle instead of hurting her.
        if (event.getEntity() instanceof ServerPlayer victim
                && event.getSource().is(DamageTypeTags.IS_LIGHTNING)
                && hasAwakenedElectrokinesis(victim)
                && !EclipseService.arePowersSuppressed(victim)
                && ElectroService.absorbLightning(victim)) {
            event.setCanceled(true);
            return;
        }

        // Static Touch: a direct melee blow, not an arrow or a power.
        if (event.getSource().getEntity() instanceof ServerPlayer attacker
                && event.getSource().getDirectEntity() == attacker
                && !ElectroService.isDealingPowerDamage()
                && event.getSource().is(net.minecraft.world.damagesource
                        .DamageTypes.PLAYER_ATTACK)
                && hasAwakenedElectrokinesis(attacker)
                && !EclipseService.arePowersSuppressed(attacker)) {
            int level = PlayerProgressionService.getLevel(attacker);
            // Vanilla resets attack strength before the damage lands, so it
            // cannot be read here; the energy cost limits spam-clicking.
            if (level >= ElectroService.STATIC_TOUCH_UNLOCK_LEVEL) {
                float bonus = ElectroService.staticTouch(
                        attacker, event.getEntity(), level);
                event.setAmount(event.getAmount() + bonus);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player
                && hasAwakenedElectrokinesis(player)) {
            ElectroService.ambientCrackle(player,
                    player.serverLevel().getGameTime());
        }
    }

    public static boolean hasAwakenedElectrokinesis(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower()
                .filter(entry -> entry.getKey()
                        .equals(ModAbilities.ELECTROKINESIS_ID)
                        && entry.getValue().isUnlocked())
                .isPresent();
    }
}
