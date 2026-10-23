package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.cryo.TemporaryBlocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class CryoEvents {
    private CryoEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        CryoService.tick(event.getServer());
        TemporaryBlocks.tick(event.getServer());
    }

    /** Temporary ice must never be saved into the world. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        CryoService.clear();
        TemporaryBlocks.meltAll();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (!event.getEntity().level().isClientSide && CryoService.shouldPauseMob(event.getEntity())) {
            // NoAI is deliberately never changed: it would otherwise survive a save/crash.
            event.getEntity().invulnerableTime = Math.max(0, event.getEntity().invulnerableTime - 1);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent public static void onTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player) CryoService.syncTo(player, event.getTarget());
    }
    @SubscribeEvent public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) CryoService.stopAll(player);
    }
    @SubscribeEvent public static void onFrozenAttack(AttackEntityEvent event) {
        if (CryoService.isFrozen(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent public static void onFrozenItem(PlayerInteractEvent.RightClickItem event) {
        if (CryoService.isFrozen(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent public static void onFrozenBlock(PlayerInteractEvent.RightClickBlock event) {
        if (CryoService.isFrozen(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent public static void onFrozenEntity(PlayerInteractEvent.EntityInteract event) {
        if (CryoService.isFrozen(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        boolean freezing = event.getSource().is(DamageTypeTags.IS_FREEZING);

        // Tracy cannot freeze, but heat is her weakness.
        if (event.getEntity() instanceof ServerPlayer victim && hasAwakenedCryokinesis(victim)
                && !EclipseService.arePowersSuppressed(victim)) {
            if (freezing) {
                event.setCanceled(true);
                return;
            }
            if (event.getSource().is(DamageTypeTags.IS_FIRE)) {
                event.setAmount(event.getAmount() * 1.25F);
            }
        }

        // Frozen solid: any blow shatters the ice for heavy damage.
        event.setAmount(CryoService.onFrozenHit(event.getEntity(), event.getAmount(),
                freezing || CryoService.isApplyingColdDamage()));

        // Cold Touch: a cryokinetic's punches chill whatever they hit.
        if (event.getSource().getEntity() instanceof ServerPlayer attacker
                && event.getSource().getDirectEntity() == attacker
                && event.getSource().is(DamageTypes.PLAYER_ATTACK)
                && !CryoService.isApplyingColdDamage()
                && hasAwakenedCryokinesis(attacker)
                && !EclipseService.arePowersSuppressed(attacker)) {
            CryoService.chill(event.getEntity(), 40, attacker);
        }
    }

    /** Something frozen solid cannot choose a new target. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Mob && CryoService.isFrozen(event.getEntity())
                && event.getNewAboutToBeSetTarget() != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CryoService.stopAll(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CryoService.stopAll(player);
        }
    }

    public static boolean hasAwakenedCryokinesis(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower()
                .filter(entry -> entry.getKey().equals(ModAbilities.CRYOKINESIS_ID)
                        && entry.getValue().isUnlocked())
                .isPresent();
    }
}
