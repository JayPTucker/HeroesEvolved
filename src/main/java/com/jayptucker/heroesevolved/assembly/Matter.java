package com.jayptucker.heroesevolved.assembly;

import com.jayptucker.heroesevolved.network.MatterStatePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * Matter: the crystal an assembler folds out of thin air, carried as fragments orbiting the body. Every cell of every
 * construct is one Matter; Shards cost none (they are paid in stamina). A construct you take apart, or that runs its
 * time out, flies home as fragments and is yours again; a cell someone else knocks out is lost. Matter regrows
 * slowly when you stop building. Short of Matter, a construct builds as far as it can rather than being refused, and
 * Shard Lance always works, so the kit never locks.
 */
public final class Matter {
    /** How much an assembler carries (more once Evolved), and how often one fragment regrows. */
    public static final int CAPACITY = 24, TIER_FIVE_CAPACITY = 32, REGROW_TICKS = 10, REGROW_PAUSE = 30;

    private static final class Pool {
        final ServerPlayer player;
        int matter;
        long spentAt = -1000, syncedAt = -1000;
        int synced = -1, syncedCapacity = -1;
        Pool(ServerPlayer player) { this.player = player; matter = capacity(player); }
    }

    private static final Map<UUID, Pool> POOLS = new HashMap<>();

    private Matter() {}

    private static long now(ServerPlayer p) { return p.getServer().overworld().getGameTime(); }

    public static int capacity(ServerPlayer p) { return PlayerProgressionService.getLevel(p) >= 5 ? TIER_FIVE_CAPACITY : CAPACITY; }

    private static Pool pool(ServerPlayer p) {
        Pool pool = POOLS.get(p.getUUID());
        if (pool == null || pool.player != p) { pool = new Pool(p); POOLS.put(p.getUUID(), pool); }
        return pool;
    }

    /** The Matter {@code p} carries now. */
    public static int of(ServerPlayer p) { return MatterAssemblyService.authorized(p) ? pool(p).matter : 0; }

    /** Spends one Matter for one cell. False when there is none left (the construct stops where it is). */
    public static boolean take(ServerPlayer p) {
        Pool pool = pool(p);
        if (pool.matter <= 0) return false;
        pool.matter--; pool.spentAt = now(p);
        return true;
    }

    /** Puts Matter back without a show (a cell that could not be placed after all). */
    public static void give(ServerPlayer p, int amount) {
        Pool pool = pool(p);
        pool.matter = Math.min(capacity(p), pool.matter + Math.max(0, amount));
    }

    /** Matter flying home from a construct at {@code from}: the fragments arc back into the orbit. */
    public static void returned(ServerPlayer p, Vec3 from, int amount) {
        if (amount <= 0 || !MatterAssemblyService.authorized(p)) return;
        give(p, amount);
        if (p.serverLevel().getChunkSource() != null && from.distanceToSqr(p.position()) < 96 * 96) {
            SetPiecePayload.send(p.serverLevel(), Piece.MATTER_RETURN, from, p.position(), Math.min(32, amount), p);
            p.serverLevel().playSound(null, from.x, from.y, from.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, .7F, 1.4F);
        }
        sync(p, true);
    }

    /** Fills the pool (tests, admin, a fresh awakening). */
    public static void fill(ServerPlayer p) { pool(p).matter = capacity(p); sync(p, true); }

    public static void tick(MinecraftServer server) {
        long time = server.overworld().getGameTime();
        if (time % 20 == 0) for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (MatterAssemblyService.authorized(p)) pool(p);
        for (Pool pool : List.copyOf(POOLS.values())) {
            ServerPlayer p = pool.player;
            if (p.isRemoved() && !p.isAlive() || !MatterAssemblyService.authorized(p)) {
                POOLS.remove(p.getUUID()); send(p, 0, 0); continue;
            }
            int cap = capacity(p);
            if (pool.matter > cap) pool.matter = cap;
            if (pool.matter < cap && time - pool.spentAt >= REGROW_PAUSE && time % REGROW_TICKS == 0) pool.matter++;
            sync(p, false);
        }
    }

    /** Tells the assembler and everyone watching how much Matter they carry: on change, and every five seconds. */
    public static void sync(ServerPlayer p, boolean force) {
        Pool pool = POOLS.get(p.getUUID());
        if (pool == null) return;
        long time = now(p);
        int cap = capacity(p);
        if (!force && pool.synced == pool.matter && pool.syncedCapacity == cap && time - pool.syncedAt < 100) return;
        pool.synced = pool.matter; pool.syncedCapacity = cap; pool.syncedAt = time;
        send(p, pool.matter, cap);
    }

    private static void send(ServerPlayer p, int matter, int capacity) {
        if (p.connection == null) return;
        try {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new MatterStatePayload(p.getId(), matter, capacity));
        } catch (RuntimeException ignored) {
            // A game-test fake player has no tracking set; the state still lives on the server.
        }
    }

    public static void forget(ServerPlayer p) {
        if (POOLS.remove(p.getUUID()) != null) send(p, 0, 0);
    }

    public static void clear() { POOLS.clear(); }
}
