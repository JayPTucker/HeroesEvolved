package com.jayptucker.heroesevolved.hazard;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.BurnPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How badly a player is burning up, 0 to 1: a meltdown's heat, radiation sickness, a speedster's friction. Everyone
 * near sees their skin peel away pixel by pixel over a charred, glowing body. It heals back slowly once the burning
 * stops, and fast for a Regeneration user, whose skin visibly grows back. A death that burns them away sets it to 1.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class BodyBurn {
    /** Healing back a tick, once nothing has burned them for {@link #SETTLE} ticks. */
    static final float HEAL = 0.004F, REGROW = 0.03F;
    static final int SETTLE = 30;

    private static final class Burn {
        ServerPlayer player;
        float level, sent;
        long touched;
        Burn(ServerPlayer player) { this.player = player; }
    }
    private static final Map<UUID, Burn> BURNS = new HashMap<>();

    private BodyBurn() {}

    public static float level(ServerPlayer player) {
        Burn b = BURNS.get(player.getUUID());
        return b == null ? 0 : b.level;
    }

    /** Burns them {@code amount} further, up to {@code cap}. */
    public static void raise(ServerPlayer player, float amount, float cap) {
        if (amount <= 0) return;
        Burn b = BURNS.computeIfAbsent(player.getUUID(), id -> new Burn(player));
        b.player = player;
        if (b.level < cap) b.level = Math.min(cap, b.level + amount);
        b.touched = player.serverLevel().getGameTime();
        sync(b, false);
    }

    /** At least this burnt, now. */
    public static void atLeast(ServerPlayer player, float level) {
        Burn b = BURNS.computeIfAbsent(player.getUUID(), id -> new Burn(player));
        b.player = player;
        b.level = Math.max(b.level, Mth.clamp(level, 0, 1));
        b.touched = player.serverLevel().getGameTime();
        sync(b, false);
    }

    /** Whole again at once (for previews and admin resets). */
    public static void heal(ServerPlayer player) {
        if (BURNS.remove(player.getUUID()) != null)
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new BurnPayload(player.getId(), 0));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        for (Burn b : List.copyOf(BURNS.values())) {
            ServerPlayer p = b.player;
            if (p.isRemoved() && p.server.getPlayerList().getPlayer(p.getUUID()) == null) { BURNS.remove(p.getUUID()); continue; }
            if (!p.isAlive()) continue;
            if (p.serverLevel().getGameTime() - b.touched > SETTLE)
                b.level = Math.max(0, b.level - (Suits.regenerates(p) ? REGROW : HEAL));
            sync(b, false);
            if (b.level <= 0 && b.sent <= 0) BURNS.remove(p.getUUID());
        }
    }

    private static void sync(Burn b, boolean force) {
        if (!force && Math.abs(b.level - b.sent) < 0.04F && !(b.level == 0 && b.sent > 0)) return;
        b.sent = b.level;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(b.player, new BurnPayload(b.player.getId(), b.level));
    }

    @SubscribeEvent
    public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer) {
            Burn b = BURNS.get(target.getUUID());
            if (b != null && b.level > 0) PacketDistributor.sendToPlayer(viewer, new BurnPayload(target.getId(), b.level));
        }
    }

    /** A new body: whole skin. */
    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Burn b = BURNS.remove(p.getUUID());
        if (b != null) PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new BurnPayload(p.getId(), 0));
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) { BURNS.clear(); }
}
