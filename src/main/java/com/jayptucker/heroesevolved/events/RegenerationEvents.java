package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.regeneration.RegenerationService;
import com.jayptucker.heroesevolved.regeneration.BodyService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class RegenerationEvents {
    private RegenerationEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        RegenerationService.tick(event.getServer());
        BodyService.tick(event.getServer());
    }

    /**
     * Runs early so other mods see the player survive. The health must be
     * restored here: a cancelled death at zero health still plays out.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        // Claire's blood in someone else: their next killing blow leaves them standing.
        if (!event.getEntity().level().isClientSide()
                && RegenerationService.trySecondLife(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
            return;
        }
        // Claire herself cannot be killed: she goes down, and gets back up.
        if (event.getEntity() instanceof ServerPlayer player
                && hasAwakenedRegeneration(player)
                && !EclipseService.arePowersSuppressed(player)
                && BodyService.goDown(player, PlayerProgressionService.getLevel(player), event.getSource())) {
            event.setCanceled(true);
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) BodyService.forget(player);
    }

    @SubscribeEvent
    public static void onDamaged(
            net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event
    ) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getNewDamage() > 0.0F
                && hasAwakenedRegeneration(player)) {
            RegenerationService.bleed(player, event.getNewDamage());
            if (!EclipseService.arePowersSuppressed(player)) {
                RegenerationService.wounded(player, event.getNewDamage());
                // A heavy blade or an explosion takes the limb nearest the hit.
                if (!BodyService.isDowned(player) && BodyService.severs(event.getSource(), event.getNewDamage()))
                    BodyService.sever(player, BodyService.limbFor(player, event.getSource()), PlayerProgressionService.getLevel(player));
            }
        }
    }

    /** A body on the ground cannot be hurt any further, except by what truly kills her. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDownedDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && BodyService.isDowned(player)
                && !BodyService.trulyKills(player, event.getSource())) event.setCanceled(true);
    }

    /** Monsters think she's dead and look for someone else. */
    @SubscribeEvent
    public static void onTarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer player && BodyService.isDowned(player)) event.setCanceled(true);
    }

    /** No arm, no swing; downed, nothing at all. */
    @SubscribeEvent
    public static void onAttack(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !BodyService.canUseHand(player, net.minecraft.world.InteractionHand.MAIN_HAND))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUseItem(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && !BodyService.canUseHand(player, event.getHand())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUseBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && !BodyService.canUseHand(player, event.getHand())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUseEntity(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player && !BodyService.canUseHand(player, event.getHand())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMine(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && !BodyService.canUseHand(player, net.minecraft.world.InteractionHand.MAIN_HAND))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer body && BodyService.hasState(body))
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer, BodyService.state(body));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) BodyService.login(player);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) BodyService.forget(player);
    }

    @SubscribeEvent
    public static void onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        var source = event.getSource();
        // While Indestructible, bullets come back out: half damage, and the arrow drops out of the wound.
        if (event.getEntity() instanceof ServerPlayer claire && RegenerationService.isIndestructible(claire)
                && source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile) {
            event.setAmount(event.getAmount() * 0.5F);
            RegenerationService.ejectProjectile(claire, projectile);
        }
        // Reckless: an Indestructible Claire punches without caring what breaks.
        if (source.getEntity() instanceof ServerPlayer claire && source.getDirectEntity() == claire
                && source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)
                && RegenerationService.isIndestructible(claire) && event.getEntity() != claire) {
            event.setAmount(event.getAmount() + RegenerationService.recklessBlow(claire, event.getEntity(),
                    PlayerProgressionService.getLevel(claire)));
        }
        // Blood Bond: a share of a friend's wound is drawn onto Claire.
        if (event.getAmount() > 0 && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            ServerPlayer claire = RegenerationService.bondHolder(event.getEntity());
            if (claire != null && source.getEntity() != claire) {
                float share = event.getAmount() * 0.35F;
                event.setAmount(event.getAmount() - share);
                RegenerationService.takeShare(claire, event.getEntity(), share);
            }
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && RegenerationService.isIndestructible(player)) {
            event.setDamageMultiplier(0.0F);
        } else if (event.getEntity() instanceof ServerPlayer player && hasAwakenedRegeneration(player)
                && !EclipseService.arePowersSuppressed(player)) {
            // The cheerleader who jumps off buildings: falls hurt half as much, and she gets up.
            event.setDamageMultiplier(event.getDamageMultiplier() * 0.5F);
        }
    }

    @SubscribeEvent
    public static void stop(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        RegenerationService.clear();
        BodyService.clear();
    }

    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && RegenerationService.isIndestructible(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RegenerationService.forget(player);
            BodyService.logout(player);
        }
    }

    private static boolean hasAwakenedRegeneration(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower()
                .filter(entry -> entry.getKey()
                        .equals(ModAbilities.REGENERATION_ID)
                        && entry.getValue().isUnlocked())
                .isPresent();
    }
}
