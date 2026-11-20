package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.story.AvenueService;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The city is a vision: players cannot die, loot it, or build an escape portal. Runners on the avenue
 * at 6:14 are playing, not watching: they can use people, fight Gray and carry, but still not build,
 * and lethal harm sends them home instead of killing them.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class TimeIntroEvents {
    private TimeIntroEvents() {}

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        TimeIntroService.tick(event.getServer());
        com.jayptucker.heroesevolved.story.prologue.PrologueService.tick(event.getServer());
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) TimeIntroService.login(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.jayptucker.heroesevolved.story.prologue.PrologueService.logout(player);
            TimeIntroService.logout(player);
        }
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) TimeIntroService.login(player);
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        com.jayptucker.heroesevolved.story.prologue.PrologueService.shutdown(event.getServer());
        TimeIntroService.shutdown(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void damage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && (TimeIntroService.isVisiting(player)
                || com.jayptucker.heroesevolved.story.prologue.PrologueService.isMember(player) || com.jayptucker.heroesevolved.story.prologue.PrologueService.isWaiting(player))) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && (TimeIntroService.isVisiting(player)
                || com.jayptucker.heroesevolved.story.prologue.PrologueService.isMember(player) || com.jayptucker.heroesevolved.story.prologue.PrologueService.isWaiting(player))) {
            event.setCanceled(true);
            player.setHealth(Math.max(1, player.getHealth()));
        } else if (event.getEntity() instanceof ServerPlayer player && AvenueService.isRunner(player)) {
            event.setCanceled(true);
            player.setHealth(Math.max(1, player.getHealth()));
            AvenueService.knockOut(player);
        }
    }
    /** "ready" typed in chat in the Last Day's lobby says you're ready to go out (the message still goes to chat). */
    @SubscribeEvent public static void chat(net.neoforged.neoforge.event.ServerChatEvent event) {
        com.jayptucker.heroesevolved.story.prologue.PrologueService.chat(event.getPlayer(), event.getRawText());
    }
    @SubscribeEvent public static void spawn(EntityJoinLevelEvent event) {
        // Gravel that slides and lands on something it can't rest on would drop itself as an item; not in the city.
        if (!event.getLevel().isClientSide() && TimeIntroService.isCity(event.getLevel())
                && event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item && item.getOwner() == null
                && (item.getItem().is(net.minecraft.world.item.Items.GRAVEL) || item.getItem().is(net.minecraft.world.item.Items.FLINT))) {
            event.setCanceled(true);
            return;
        }
        // Server only. The event also fires as a client receives each entity, and a client knows neither the
        // scene tags nor the vision's crowd, so it would turn away everyone on the avenue and in the vision.
        if (!event.getLevel().isClientSide() && TimeIntroService.isCity(event.getLevel()) && event.getEntity() instanceof Mob
                && !TimeIntroService.isCurrentCrowd(event.getEntity()) && !com.jayptucker.heroesevolved.story.prologue.PrologueService.isScene(event.getEntity())
                && !(AvenueService.running() && AvenueService.isScene(event.getEntity()))) event.setCanceled(true);
    }
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent event) {
        if (!TimeIntroService.isCity(event.getPlayer().level())) return;
        // The Last Day's rubble, on someone who needs digging out, is the one thing anyone may break.
        if (event.getPlayer() instanceof ServerPlayer player && com.jayptucker.heroesevolved.story.prologue.PrologueService.mayBreak(player, event.getPos())) return;
        event.setCanceled(true);
    }
    /** Nothing broken in the city drops anything (the Last Day's gravel included): it's a memory, not a quarry. */
    @SubscribeEvent public static void drops(net.neoforged.neoforge.event.level.BlockDropsEvent event) {
        if (TimeIntroService.isCity(event.getLevel())) event.setCanceled(true);
    }
    @SubscribeEvent public static void placing(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() != null && TimeIntroService.isCity(event.getEntity().level())) event.setCanceled(true);
    }
    @SubscribeEvent public static void rightBlock(PlayerInteractEvent.RightClickBlock event) {
        // On the Last Day, sneak and right-click beside someone dug out of the rubble to lift them.
        if (event.getEntity() instanceof ServerPlayer player && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                && com.jayptucker.heroesevolved.story.prologue.PrologueService.lift(player, event.getHitVec().getLocation())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        // A runner may set down someone they are carrying; nobody uses the city's blocks.
        if (event.getEntity() instanceof ServerPlayer player && (AvenueService.isRunner(player)
                || com.jayptucker.heroesevolved.story.prologue.PrologueService.isMember(player)) && EntityCarryService.isCarrying(player)) return;
        if (TimeIntroService.isCity(event.getLevel())) {
            // Trying the Arms' street doors before everyone is ready says who the lobby is waiting for; on the Last Day any
            // other wooden door or gate opens and shuts by hand (the day logs it and puts it back). Nothing else is used.
            boolean opened;
            if (event.getEntity() instanceof ServerPlayer player)
                opened = !com.jayptucker.heroesevolved.story.prologue.PrologueService.useBlock(player, event.getPos())
                        && com.jayptucker.heroesevolved.story.prologue.PrologueService.useDoor(player, event.getPos());
            else opened = event.getLevel().isClientSide()
                    && com.jayptucker.heroesevolved.story.prologue.PrologueService.openableByHand(event.getLevel().getBlockState(event.getPos()));
            event.setCanceled(true);
            event.setCancellationResult(opened ? InteractionResult.SUCCESS : InteractionResult.FAIL);
        }
    }
    @SubscribeEvent public static void rightItem(PlayerInteractEvent.RightClickItem event) {
        if (TimeIntroService.isCity(event.getLevel())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }
    /** Before the ordinary carry's own handler, so the city decides what may be lifted (and how) first. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player && AvenueService.isRunner(player)) return;
        // On the Last Day, someone dug out of the rubble is lifted (sneak and right-click), never traded with.
        if (event.getEntity() instanceof ServerPlayer player && com.jayptucker.heroesevolved.story.prologue.PrologueService.isVictim(event.getTarget())) {
            boolean lifted = event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                    && com.jayptucker.heroesevolved.story.prologue.PrologueService.lift(player, event.getTarget().position());
            event.setCanceled(true);
            event.setCancellationResult(lifted ? InteractionResult.SUCCESS : InteractionResult.FAIL);
            return;
        }
        if (TimeIntroService.isCity(event.getLevel())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }
    @SubscribeEvent public static void attack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && AvenueService.isRunner(player)) return;
        if (TimeIntroService.isCity(event.getEntity().level())) event.setCanceled(true);
    }
}
