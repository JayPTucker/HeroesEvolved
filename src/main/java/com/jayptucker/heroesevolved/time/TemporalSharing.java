package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.expansion.AdvancedTechniques;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A touch brings another player into the caster's current fast time, until that field ends. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class TemporalSharing {
    private record Guest(ServerPlayer owner, ServerPlayer player, String reason, boolean speed) {}
    private static final Map<UUID, Map<UUID, Guest>> GUESTS = new HashMap<>();

    private TemporalSharing() {}

    private static boolean active(ServerPlayer owner) {
        return TimeStopService.hasActiveField(owner) || TimeSlowService.hasActiveField(owner)
                || AdvancedTechniques.perceiving(owner);
    }

    public static boolean movesWith(UUID owner, Entity entity) {
        var guests = GUESTS.get(owner);
        return guests != null && (guests.containsKey(entity.getUUID()) || guests.containsKey(entity.getRootVehicle().getUUID()));
    }

    public static boolean shared(ServerPlayer player) {
        return GUESTS.values().stream().anyMatch(guests -> guests.containsKey(player.getUUID()));
    }

    /** Server-authoritative reach, sight, power and active-field checks, also used by the interaction tests. */
    public static boolean include(ServerPlayer owner, ServerPlayer player) {
        boolean speed = SpeedService.awakened(owner) && PlayerProgressionService.getLevel(owner) >= 4;
        if ((!SpaceTimeService.awakened(owner) && !speed) || !active(owner) || !PowerRules.canAct(owner)
                || owner == player || !player.isAlive() || player.isSpectator() || owner.level() != player.level()
                || owner.distanceToSqr(player) > owner.entityInteractionRange() * owner.entityInteractionRange()
                || !owner.hasLineOfSight(player)) return false;
        var guests = GUESTS.computeIfAbsent(owner.getUUID(), id -> new HashMap<>());
        if (guests.containsKey(player.getUUID())) return true;
        String reason = "shared:" + owner.getUUID();
        guests.put(player.getUUID(), new Guest(owner, player, reason, speed));
        TimeVeil.begin(player, speed ? TimeVeil.Style.SPEED : TimeVeil.Style.TIME, reason);
        PlayerTimeSlow.update(player);
        TemporalVoiceState.publish(owner.getServer());
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("time.heroesevolved.shared"), true);
        return true;
    }

    /** Called as the last field ends, before its frozen stand-in is revealed. */
    public static void end(ServerPlayer owner) {
        var guests = GUESTS.remove(owner.getUUID());
        if (guests == null) return;
        for (Guest guest : List.copyOf(guests.values())) release(guest);
    }

    private static void release(Guest guest) {
        TimeVeil.end(guest.player, guest.reason);
        PlayerTimeSlow.update(guest.player);
    }

    public static void tick(MinecraftServer server) {
        for (var entry : List.copyOf(GUESTS.entrySet())) {
            var guests = entry.getValue();
            for (Guest guest : List.copyOf(guests.values())) {
                if (!active(guest.owner) || !guest.owner.isAlive() || !guest.player.isAlive()
                        || guest.owner.isRemoved() || guest.player.isRemoved() || !PowerRules.canAct(guest.owner)
                        || guest.owner.level() != guest.player.level()
                        || server.getPlayerList().getPlayer(guest.player.getUUID()) != guest.player) {
                    guests.remove(guest.player.getUUID());
                    release(guest);
                } else if (guest.speed && server.overworld().getGameTime() % 8 == 0) {
                    for (ServerPlayer viewer : guest.player.serverLevel().players())
                        if (TimeVeil.movesInTime(viewer) && viewer.distanceToSqr(guest.player) < 128 * 128)
                            PacketDistributor.sendToPlayer(viewer, new SetPiecePayload(Piece.SPEED_SHARED_TIME.ordinal(),
                                    guest.player.position(), guest.player.getEyePosition(), 1, guest.player.getId(),
                                    guest.owner.getId(), 0));
                }
            }
            if (guests.isEmpty()) GUESTS.remove(entry.getKey());
        }
    }

    public static void clear() { GUESTS.clear(); }

    private static boolean interact(PlayerInteractEvent event, Entity target) {
        return event.getHand() == InteractionHand.MAIN_HAND && event.getEntity() instanceof ServerPlayer owner
                && target instanceof ServerPlayer player && include(owner, player);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void touch(PlayerInteractEvent.EntityInteract event) {
        if (interact(event, event.getTarget())) { event.setCancellationResult(InteractionResult.SUCCESS); event.setCanceled(true); }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void touch(PlayerInteractEvent.EntityInteractSpecific event) {
        if (interact(event, event.getTarget())) { event.setCancellationResult(InteractionResult.SUCCESS); event.setCanceled(true); }
    }
}
