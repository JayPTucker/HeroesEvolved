package com.jayptucker.heroesevolved.endportal;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseResult;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.energy.StaminaDrain;
import com.jayptucker.heroesevolved.mimicry.MimicColors;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Two adjacent offerings: simultaneous in a party, remembered sequentially for a lone traveler. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class EndPortalRitual {
    public static final String DRAIN = "end_portal_ritual";
    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
    private static final Map<UUID, java.util.Set<String>> INPUTS = new HashMap<>();
    private static final Map<Key, Portal> PORTALS = new HashMap<>();
    private static final Map<UUID, Notice> HINTS = new HashMap<>();
    private record Notice(String reason, long until) {}
    private record Key(ServerLevel level, BlockPos center) {}
    private record Imprint(int color, long until) {}
    private static final class Portal {
        final Key key;
        final Map<BlockPos, Imprint> imprints = new HashMap<>();
        int opening = -1;
        long lastPulse = -1000;
        Portal(Key key) { this.key = key; }
    }
    private static final class Channel {
        final ServerPlayer player;
        final ResourceLocation power;
        final Portal portal;
        final BlockPos frame;
        final String input;
        final double offering;
        int ticks;
        double paid;
        Channel(ServerPlayer player, ResourceLocation power, Portal portal, BlockPos frame, String input) {
            this.player = player; this.power = power; this.portal = portal; this.frame = frame; this.input = input;
            offering = PlayerEnergyService.getStamina(player);
        }
    }
    private EndPortalRitual() {}

    private static Portal portal(ServerLevel level, BlockPos center) {
        Key key = new Key(level, center.immutable());
        return PORTALS.computeIfAbsent(key, Portal::new);
    }

    /** Null means ordinary ability input; otherwise the frame owns this press, including a refused offering. */
    public static AbilityUseResult press(ServerPlayer player, String input) {
        if (CHANNELS.containsKey(player.getUUID())) {
            INPUTS.computeIfAbsent(player.getUUID(), id -> new java.util.HashSet<>()).add(input);
            return AbilityUseResult.SUCCESS;
        }
        if (com.jayptucker.heroesevolved.vibe.VibeVisions.watching(player)) return null;
        ServerLevel level = player.serverLevel();
        BlockPos frame = PortalGeometry.underfoot(player), center = PortalGeometry.center(level, frame);
        if (center == null || PortalGeometry.open(level, center) || !PortalGeometry.standing(player, frame, center)) return null;
        INPUTS.computeIfAbsent(player.getUUID(), id -> new java.util.HashSet<>()).add(input);
        var power = PlayerAbilityService.getData(player).assignedPower();
        if (power.isEmpty() || !power.get().getValue().isUnlocked()
                || AbilityRegistry.ABILITIES.get(power.get().getKey()) == null)
            return refuse(player, "unawakened");
        if (PlayerProgressionService.getPowerLevel(player) < PortalGeometry.MINIMUM_LEVEL)
            return refuse(player, "level");
        if (PlayerEnergyService.hasUnlimitedEnergy(player)) return refuse(player, "unlimited");
        if (!usable(player, power.get().getKey())) return refuse(player, "suppressed");
        if (!PortalGeometry.clear(level, center)) return refuse(player, "obstructed");
        Portal portal = portal(level, center);
        portal.imprints.values().removeIf(i -> i.until <= level.getGameTime());
        if (portal.opening >= 0 || portal.imprints.containsKey(frame)
                || CHANNELS.values().stream().anyMatch(c -> c.portal == portal && c.frame.equals(frame)))
            return refuse(player, "remembered");
        if (PlayerEnergyService.getStamina(player) + 1e-6 < PlayerEnergyService.getMaximumEnergy(player) * PortalGeometry.START_FRACTION) {
            PlayerEnergyService.refused(player);
            return refuse(player, "stamina");
        }
        Channel channel = new Channel(player, power.get().getKey(), portal, frame.immutable(), input);
        CHANNELS.put(player.getUUID(), channel);
        // This transaction pays an exact, captured bar in installments below. The shared held-drain owns recovery
        // and lifetime, but has no extra rate to discount or double-charge; there is no practice-Mastery farm.
        StaminaDrain.start(player, DRAIN, 0, true, () -> cancel(player));
        pulse(channel);
        sound(level, frame, SoundEvents.BEACON_POWER_SELECT, .35F, .7F);
        return AbilityUseResult.SUCCESS;
    }

    private static boolean usable(ServerPlayer player, ResourceLocation power) {
        return player.isAlive() && !player.isRemoved() && PowerRules.canAct(player)
                && !com.jayptucker.heroesevolved.vibe.VibeVisions.watching(player)
                && PlayerProgressionService.getPowerLevel(player) >= PortalGeometry.MINIMUM_LEVEL
                && PlayerAbilityService.getData(player).assignedPower()
                    .filter(e -> e.getValue().isUnlocked() && e.getKey().equals(power)).isPresent()
                && !com.jayptucker.heroesevolved.amplify.Amplification.locked(player, power)
                && !PlayerEnergyService.hasUnlimitedEnergy(player);
    }

    private static AbilityUseResult refuse(ServerPlayer player, String reason) {
        hint(player, reason);
        return AbilityUseResult.CANNOT_USE;
    }

    private static void hint(ServerPlayer player, String reason) {
        long now = player.serverLevel().getGameTime();
        Notice last = HINTS.get(player.getUUID());
        if (last != null && last.reason.equals(reason) && now < last.until) return;
        HINTS.put(player.getUUID(), new Notice(reason, now + (reason.equals("instructions") ? 6000 : 100)));
        player.sendSystemMessage(Component.translatable("portal.heroesevolved." + reason, PortalGeometry.MINIMUM_LEVEL));
    }

    public static boolean release(ServerPlayer player, String input) {
        var inputs = INPUTS.get(player.getUUID());
        if (inputs == null || !inputs.remove(input)) return false;
        if (inputs.isEmpty()) INPUTS.remove(player.getUUID());
        Channel channel = CHANNELS.get(player.getUUID());
        // A completed/canceled offering still owns its eventual key-up: do not release an ordinary action
        // whose press was redirected to the frame, especially a charged action such as Inferno.
        if (channel != null && channel.input.equals(input)) cancel(player);
        return true;
    }

    public static boolean channeling(ServerPlayer player) { return CHANNELS.containsKey(player.getUUID()); }

    public static void cancel(ServerPlayer player) {
        Channel channel = CHANNELS.remove(player.getUUID());
        if (channel == null) return;
        finishPose(player);
        SetPiecePayload.send(channel.portal.key.level, Piece.END_PORTAL_CANCEL, PortalGeometry.surface(channel.portal.key.center),
                PortalGeometry.eye(channel.frame), 1, player);
    }

    private static void finishPose(ServerPlayer player) {
        StaminaDrain.stop(player, DRAIN);
        ArmPosePayload.clearIf(player, ArmPose.PORTAL_CHANNEL);
        PlayerEnergyService.sendNow(player);
    }

    /** Called after the vanilla item has inserted and consumed the eye, before it would create portal blocks. */
    public static void eyeInserted(ServerLevel level, BlockPos frame, ServerPlayer player) {
        BlockPos center = PortalGeometry.center(level, frame);
        if (center == null || PortalGeometry.open(level, center)) return;
        portal(level, center);
        SetPiecePayload.send(level, Piece.END_PORTAL_DORMANT, PortalGeometry.surface(center), PortalGeometry.eye(frame), 1, null);
        if (player != null) hint(player, "instructions");
    }

    private static void pulse(Channel c) {
        int seed = MimicColors.index(c.power.getPath()) | c.ticks << 5;
        ArmPosePayload.show(c.player, ArmPose.PORTAL_CHANNEL, 16);
        SetPiecePayload.sendSeeded(c.portal.key.level, Piece.END_PORTAL_CHANNEL, PortalGeometry.surface(c.portal.key.center),
                PortalGeometry.eye(c.frame), 1, c.player, 0, seed);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for (Channel c : List.copyOf(CHANNELS.values())) {
            if (CHANNELS.get(c.player.getUUID()) != c) continue;
            var level = c.portal.key.level;
            var center = c.portal.key.center;
            if (c.player.serverLevel() != level || !usable(c.player, c.power)
                    || !StaminaDrain.active(c.player, DRAIN) || !PortalGeometry.standing(c.player, c.frame, center)
                    || !PortalGeometry.complete(level, center) || !PortalGeometry.clear(level, center)) {
                cancel(c.player); continue;
            }
            double payment = c.offering / PortalGeometry.CHANNEL_TICKS;
            if (c.ticks + 1 == PortalGeometry.CHANNEL_TICKS) payment = Math.max(payment, PlayerEnergyService.getStamina(c.player));
            if (!PlayerEnergyService.drain(c.player, payment)) { cancel(c.player); continue; }
            c.paid += payment;
            c.ticks++;
            if (c.ticks % 5 == 0) pulse(c);
            if (c.ticks % 20 == 0) sound(level, c.frame, SoundEvents.AMETHYST_BLOCK_CHIME, .35F, .65F + c.ticks / (float) PortalGeometry.CHANNEL_TICKS);
            if (c.ticks < PortalGeometry.CHANNEL_TICKS) continue;
            CHANNELS.remove(c.player.getUUID());
            finishPose(c.player);
            SetPiecePayload.send(level, Piece.END_PORTAL_CANCEL, PortalGeometry.surface(center), PortalGeometry.eye(c.frame), 1, c.player);
            if (c.paid + 1e-5 < c.offering) continue;
            c.portal.imprints.values().removeIf(i -> i.until <= level.getGameTime());
            c.portal.imprints.put(c.frame, new Imprint(MimicColors.index(c.power.getPath()), level.getGameTime() + PortalGeometry.MEMORY_TICKS));
            sound(level, c.frame, SoundEvents.RESPAWN_ANCHOR_CHARGE, .45F, 1.4F);
            hint(c.player, "offering");
            for (BlockPos other : c.portal.imprints.keySet()) if (PortalGeometry.adjacent(c.frame, other)) {
                c.portal.opening = 0;
                int colors = c.portal.imprints.get(c.frame).color | c.portal.imprints.get(other).color << 5;
                SetPiecePayload.sendSeeded(level, Piece.END_PORTAL_OPEN, PortalGeometry.surface(center), PortalGeometry.eye(c.frame), 1, null, 0, colors);
                for (Channel extra : List.copyOf(CHANNELS.values())) if (extra.portal == c.portal) cancel(extra.player);
                break;
            }
        }
        for (Portal portal : List.copyOf(PORTALS.values())) {
            ServerLevel level = portal.key.level;
            BlockPos center = portal.key.center;
            if (!PortalGeometry.complete(level, center) || !PortalGeometry.clear(level, center)) {
                remove(portal); continue;
            }
            portal.imprints.values().removeIf(i -> i.until <= level.getGameTime());
            if (portal.opening >= 0 && ++portal.opening >= PortalGeometry.OPEN_TICKS) {
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                    level.setBlock(center.offset(x, 0, z), Blocks.END_PORTAL.defaultBlockState(), Block.UPDATE_ALL);
                level.globalLevelEvent(1038, center, 0);
                remove(portal);
                continue;
            }
            if (level.getGameTime() % 10 == 0) for (var entry : portal.imprints.entrySet())
                SetPiecePayload.sendSeeded(level, Piece.END_PORTAL_IMPRINT, PortalGeometry.surface(center), PortalGeometry.eye(entry.getKey()),
                        (float) Math.min(1, (entry.getValue().until - level.getGameTime()) / 40.0), null, 0, entry.getValue().color);
            if (level.getGameTime() - portal.lastPulse >= 20) {
                SetPiecePayload.send(level, Piece.END_PORTAL_DORMANT, PortalGeometry.surface(center), PortalGeometry.surface(center), 1, null);
                portal.lastPulse = level.getGameTime();
            }
            // Forget an unoccupied, uncharged frame: only nearby players rediscover it, so old strongholds cost no ticks.
            if (portal.opening < 0 && portal.imprints.isEmpty() && CHANNELS.values().stream().noneMatch(c -> c.portal == portal))
                PORTALS.remove(portal.key);
        }
        if (event.getServer().getTickCount() % 20 == 0) for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            BlockPos center = PortalGeometry.center(player.serverLevel(), PortalGeometry.underfoot(player));
            if (center != null && !PortalGeometry.open(player.serverLevel(), center)) {
                portal(player.serverLevel(), center);
                if (!channeling(player) && !HINTS.containsKey(player.getUUID())) hint(player, "instructions");
            }
        }
    }

    private static void remove(Portal portal) {
        PORTALS.remove(portal.key);
        for (Channel c : List.copyOf(CHANNELS.values())) if (c.portal == portal) cancel(c.player);
    }

    private static void sound(ServerLevel level, BlockPos at, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at, sound, SoundSource.BLOCKS, volume, pitch);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            cancel(player); HINTS.remove(player.getUUID()); INPUTS.remove(player.getUUID());
        }
    }
    @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) cancel(player);
    }
    @SubscribeEvent public static void stopped(ServerStoppingEvent event) { clear(); }

    public static void clear() {
        for (Channel c : List.copyOf(CHANNELS.values())) cancel(c.player);
        PORTALS.clear(); HINTS.clear(); INPUTS.clear();
    }
}
