package com.jayptucker.heroesevolved.radiation;

import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.NuclearCloudPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import com.jayptucker.heroesevolved.time.TemporalSnapshotService;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.*;

/** Fast damage front, bounded terrain processing, and a temporary radioactive aftermath. */
public final class NuclearFallout {
    public static final int RADIUS = 24, WAVE_TICKS = 8, FALLOUT_TICKS = 20 * 60;
    public static final int AWAKENING_RADIUS = 48, CLOUD_TICKS = 600;
    private static final List<Blast> BLASTS = new ArrayList<>();
    public static final int MAX_SCANS_PER_TICK = 8192, MAX_BREAKS_PER_TICK = 512;
    private static final long TERRAIN_NANOS = 4_000_000;
    private static final int TERRAIN_TIMEOUT = 20 * 180;
    private static int roundRobin;
    private NuclearFallout() {}
    private static final class Blast {
        final ServerLevel level;
        final ServerPlayer owner;
        final Vec3 center;
        final BlockPos block;
        final long began;
        final Set<UUID> struck = new HashSet<>();
        final UUID id = UUID.randomUUID();
        final int radius;
        final NuclearTerrainCursor cursor;
        final BlockPos.MutableBlockPos offset = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        boolean terrainFinished;
        Blast(ServerPlayer owner, boolean awakening) {
            this.owner = owner; level = owner.serverLevel(); center = owner.position();
            block = BlockPos.containing(center); began = level.getGameTime();
            radius = awakening ? AWAKENING_RADIUS : RADIUS;
            cursor = new NuclearTerrainCursor(radius);
        }
    }
    public static void begin(ServerPlayer owner) {
        begin(owner, false);
    }
    public static void begin(ServerPlayer owner, boolean awakening) {
        if (TimeIntroService.isCity(owner.level()) || TemporalSnapshotService.isTemporalDimension(owner.serverLevel())) return;
        // Overlapping detonations refresh their own area rather than growing an unbounded work queue.
        BLASTS.removeIf(blast -> blast.owner.getUUID().equals(owner.getUUID()));
        if (BLASTS.size() >= 32) BLASTS.removeFirst();
        Blast blast = new Blast(owner, awakening);
        BLASTS.add(blast);
        cloud(blast, 0);
        AbilityEffects.at(owner, Kind.NUCLEAR_SHOCKWAVE, blast.radius);
    }
    public static void tick(MinecraftServer server) {
        for (Blast blast : List.copyOf(BLASTS)) {
            if (blast.level.getServer() != server) continue;
            long age = blast.level.getGameTime() - blast.began;
            if (age > TERRAIN_TIMEOUT) blast.terrainFinished = true;
            if (age > FALLOUT_TICKS && blast.terrainFinished) { BLASTS.remove(blast); continue; }
            double front = Math.min(blast.radius, (age + 1) * blast.radius / (double) WAVE_TICKS);
            if (age <= WAVE_TICKS) wave(blast, front);
            if (age <= FALLOUT_TICKS && age % 20 == 0) fallout(blast);
            if (age > 0 && age < CLOUD_TICKS && age % 40 == 0) cloud(blast, (int) age);
        }
        // One budget for the entire server, not one allowance per active explosion.
        List<Blast> terrain = BLASTS.stream().filter(blast -> blast.level.getServer() == server
                && !blast.terrainFinished && blast.level.getGameTime() - blast.began >= WAVE_TICKS).toList();
        if (terrain.isEmpty()) return;
        long deadline = System.nanoTime() + TERRAIN_NANOS;
        int scans = 0, breaks = 0, empty = 0;
        while (scans < MAX_SCANS_PER_TICK && breaks < MAX_BREAKS_PER_TICK && System.nanoTime() < deadline) {
            Blast blast = terrain.get(Math.floorMod(roundRobin++, terrain.size()));
            if (blast.terrainFinished) { if (++empty >= terrain.size()) break; continue; }
            empty = 0; scans++;
            if (terrainStep(blast)) breaks++;
        }
    }
    private static boolean vulnerable(Blast blast, LivingEntity entity) {
        return entity.isAlive() && !entity.getUUID().equals(blast.owner.getUUID()) && !entity.isSpectator()
                && (!(entity instanceof ServerPlayer player) || !player.isCreative());
    }
    private static void wave(Blast blast, double front) {
        for (LivingEntity target : blast.level.getEntitiesOfClass(LivingEntity.class,
                new AABB(blast.center, blast.center).inflate(front + 1), target -> vulnerable(blast, target))) {
            double distance = target.position().distanceTo(blast.center);
            if (distance > front || !blast.struck.add(target.getUUID())) continue;
            float damage = (float) ((blast.radius == AWAKENING_RADIUS ? 32 : 20)
                    + (blast.radius == AWAKENING_RADIUS ? 88 : 60) * (1 - distance / blast.radius));
            target.hurt(blast.level.damageSources().explosion(blast.owner, blast.owner), damage);
            RadiationService.irradiate(target, 160);
            Vec3 away = target.position().subtract(blast.center).multiply(1, 0, 1).normalize();
            target.setDeltaMovement(away.scale(1.7).add(0, 0.5, 0));
            target.hurtMarked = true;
        }
    }
    private static boolean terrainStep(Blast blast) {
        ServerPlayer owner = blast.owner;
        ServerLevel level = blast.level;
        if (!owner.mayBuild() || owner.level() != level) { blast.terrainFinished = true; return false; }
        if (!blast.cursor.next(blast.offset)) { blast.terrainFinished = true; return false; }
        BlockPos offset = blast.offset;
        if (offset.getY() < 0 && offset.getX() * offset.getX() + offset.getZ() * offset.getZ() <= 2) return false;
        BlockPos pos = blast.position.setWithOffset(blast.block, offset);
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(owner, pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0) return false;
        if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos.immutable(), state, owner)).isCanceled()) return false;
        // Avoid a sound/particle event for every voxel. Normal removal still handles container
        // contents, neighbors, lighting and the Time Rewind journal; chunk updates remain batched.
        return level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }
    private static void fallout(Blast blast) {
        AbilityEffects.send(blast.level, Kind.NUCLEAR_FALLOUT, blast.center, blast.center, blast.radius);
        for (LivingEntity target : blast.level.getEntitiesOfClass(LivingEntity.class,
                new AABB(blast.center, blast.center).inflate(blast.radius), target -> vulnerable(blast, target))) {
            if (target.position().distanceToSqr(blast.center) > blast.radius * blast.radius) continue;
            RadiationService.irradiate(target, 60);
            target.hurt(blast.level.damageSources().indirectMagic(blast.owner, blast.owner), 2);
        }
    }
    private static void cloud(Blast blast, int age) {
        NuclearCloudPayload payload = new NuclearCloudPayload(blast.id, blast.level.dimension().location(),
                blast.center, blast.radius == AWAKENING_RADIUS ? 1.6F : 1, blast.radius, age);
        for (ServerPlayer viewer : blast.level.players())
            if (viewer.position().distanceToSqr(blast.center) <= 400 * 400) PacketDistributor.sendToPlayer(viewer, payload);
    }
    public static void clear() { BLASTS.clear(); roundRobin = 0; }
}
