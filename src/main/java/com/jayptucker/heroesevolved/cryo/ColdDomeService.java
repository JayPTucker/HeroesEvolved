package com.jayptucker.heroesevolved.cryo;

import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.network.BlizzardStatePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded surface frosting and a short-lived cold volume shared by awakening and Blizzard. */
public final class ColdDomeService {
    public static final int BLIZZARD_RADIUS = 20, AWAKENING_RADIUS = 24, AWAKENING_TICKS = 160;
    public static final int MAX_COLUMNS_PER_TICK = 64;
    private static final Map<UUID, Dome> DOMES = new LinkedHashMap<>();
    private static int nextDome;
    private static final class Dome {
        final ServerPlayer owner;
        final ServerLevel level;
        final int radius;
        final long until;
        final boolean follows;
        Vec3 center, scanCenter;
        int ring, edge;
        boolean finished;
        Dome(ServerPlayer owner, int radius, int duration, boolean follows) {
            this.owner = owner; this.level = owner.serverLevel(); this.radius = radius;
            this.until = level.getGameTime() + duration; this.follows = follows;
            center = scanCenter = owner.position();
        }
    }
    private ColdDomeService() {}
    public static void start(ServerPlayer owner, int radius, int duration, boolean follows) {
        if (DOMES.size() >= 16 && !DOMES.containsKey(owner.getUUID())) return;
        Dome dome = new Dome(owner, Math.clamp(radius, 1, 24), Math.clamp(duration, 1, 600), follows);
        DOMES.put(owner.getUUID(), dome);
        freeze(dome);
        sync(dome, true);
    }
    public static void awakening(ServerPlayer owner) { start(owner, AWAKENING_RADIUS, AWAKENING_TICKS, false); }
    public static void stop(ServerPlayer owner) {
        Dome dome = DOMES.remove(owner.getUUID());
        if (dome != null) sync(dome, false);
    }
    private static void freeze(Dome dome) {
        for (LivingEntity target : dome.level.getEntitiesOfClass(LivingEntity.class,
                ColdDomeShape.bounds(dome.center, dome.radius), target -> target != dome.owner && target.isAlive()
                        && !target.isSpectator() && (!(target instanceof ServerPlayer player) || !player.isCreative())
                        && ColdDomeShape.contains(dome.center, target.position(), dome.radius))) {
            CryoService.freezeSolid(target, target instanceof ServerPlayer ? 40 : 80, dome.owner);
        }
    }
    private static void sync(Dome dome, boolean active) {
        BlizzardStatePayload payload = new BlizzardStatePayload(dome.owner.getUUID(), dome.center,
                active ? dome.radius : 0, active ? 28 : 0);
        for (ServerPlayer viewer : dome.level.players())
            if (!active || viewer.position().distanceToSqr(dome.center) <= 96 * 96) PacketDistributor.sendToPlayer(viewer, payload);
    }
    public static void tick(MinecraftServer server) {
        var iterator = DOMES.values().iterator();
        while (iterator.hasNext()) {
            Dome dome = iterator.next();
            if (dome.level.getServer() != server) continue;
            long now = dome.level.getGameTime();
            if (now >= dome.until || !dome.owner.isAlive() || dome.owner.isRemoved()
                    || dome.owner.level() != dome.level || EclipseService.arePowersSuppressed(dome.owner)) {
                sync(dome, false); iterator.remove(); continue;
            }
            if (dome.follows) dome.center = dome.owner.position();
            if (now % 10 == 0) { freeze(dome); sync(dome, true); }
            if (dome.follows && dome.center.distanceToSqr(dome.scanCenter) > 64) {
                dome.scanCenter = dome.center; dome.ring = dome.edge = 0; dome.finished = false;
            }
        }
        if (DOMES.isEmpty()) return;
        var work = DOMES.values().stream().filter(dome -> dome.level.getServer() == server && !dome.finished).toList();
        long deadline = System.nanoTime() + 2_000_000;
        for (int i = 0; !work.isEmpty() && i < MAX_COLUMNS_PER_TICK && System.nanoTime() < deadline; i++) {
            Dome dome = work.get(Math.floorMod(nextDome++, work.size()));
            if (!dome.finished) frostColumn(dome);
        }
    }
    private static void frostColumn(Dome dome) {
        if (dome.ring > dome.radius) { dome.finished = true; return; }
        int x, z;
        if (dome.ring == 0) { x = z = 0; dome.ring = 1; }
        else {
            int r = dome.ring, sideLength = r * 2, side = dome.edge / sideLength, step = dome.edge % sideLength;
            switch (side) {
                case 0 -> { x = -r + step; z = -r; }
                case 1 -> { x = r; z = -r + step; }
                case 2 -> { x = r - step; z = r; }
                default -> { x = -r; z = r - step; }
            }
            if (++dome.edge >= r * 8) { dome.ring++; dome.edge = 0; }
        }
        if (x * x + z * z > dome.radius * dome.radius) return;
        BlockPos column = BlockPos.containing(dome.scanCenter).offset(x, 0, z);
        if (!dome.level.hasChunkAt(column)) return;
        BlockPos ground = CryoService.surfaceNear(dome.level, column, 6);
        if (ground != null && ColdDomeShape.contains(dome.center, Vec3.atCenterOf(ground), dome.radius))
            TemporaryBlocks.frostSurface(dome.level, ground,
                    (int) Math.max(20, dome.until - dome.level.getGameTime()) + 80, dome.owner);
    }
    public static void clear() {
        for (Dome dome : DOMES.values()) sync(dome, false);
        DOMES.clear(); nextDome = 0;
    }
}
