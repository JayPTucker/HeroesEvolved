package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.pyro.PyroService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Pyrokinesis passives: fire immunity, burning punches, fire as fuel, water and cold as weaknesses. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class PyroEvents {
    /** Punches ignite from this level; lava stops hurting from LAVA_IMMUNITY_LEVEL. */
    public static final int BURNING_TOUCH_LEVEL = 2, LAVA_IMMUNITY_LEVEL = 3;

    private PyroEvents() {}

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { PyroService.tick(event.getServer()); }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim && PyroService.isPyrokinetic(victim)) {
            boolean lava = event.getSource().is(DamageTypes.LAVA);
            if (event.getSource().is(DamageTypeTags.IS_FIRE)
                    && (!lava || PlayerProgressionService.getLevel(victim) >= LAVA_IMMUNITY_LEVEL)) {
                event.setCanceled(true);
                return;
            }
            // Meredith's weakness mirrors Tracy's: the opposite element hurts more.
            if (event.getSource().is(DamageTypeTags.IS_FREEZING) || CryoService.isApplyingColdDamage()) {
                event.setAmount(event.getAmount() * 1.5F);
                PyroService.quench(victim);
            }
        }

        // Burning Touch: a direct punch, not an arrow or another power.
        if (event.getSource().getEntity() instanceof ServerPlayer attacker
                && event.getSource().getDirectEntity() == attacker
                && event.getSource().is(DamageTypes.PLAYER_ATTACK)
                && !PyroService.isDealingPowerDamage()
                && PyroService.isPyrokinetic(attacker)
                && PlayerProgressionService.getLevel(attacker) >= BURNING_TOUCH_LEVEL) {
            LivingEntity target = event.getEntity();
            target.igniteForSeconds(PyroService.isBlue(attacker) ? 5 : 3);
            PyroService.addHeat(attacker, 4);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0
                || !PyroService.isPyrokinetic(player)) return;
        // Standing in fire or lava refuels; rain and water quench and tire.
        boolean inFire = player.isInLava() || player.level().getBlockStates(player.getBoundingBox().deflate(0.001))
                .anyMatch(state -> state.is(BlockTags.FIRE));
        if (inFire) PlayerEnergyService.restore(player, 3);
        if (player.isInWaterRainOrBubble()) {
            PyroService.quench(player);
            PlayerEnergyService.consumeUpTo(player, 2);
        }
    }

    @SubscribeEvent
    public static void fall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && PyroService.cushion(player)) event.setDamageMultiplier(0);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PyroService.forget(player);
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PyroService.forget(player);
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PyroService.forget(player);
    }

    @SubscribeEvent public static void stop(ServerStoppingEvent event) { PyroService.clear(); }
}
