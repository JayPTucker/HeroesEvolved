package com.jayptucker.heroesevolved.radiation;

import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.NuclearCloudPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import com.jayptucker.heroesevolved.time.TemporalSnapshotService;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/**
 * Fast damage front, a crater carved in one tick, and a temporary radioactive aftermath. The crater goes the moment the
 * wave reaches its edge ({@link #WAVE_TICKS}): the inside is cleared without neighbour updates (nothing inside is left
 * to hear about it), and the outer shell and anything touching water or lava with full updates, so sand falls in, water
 * flows and torches drop at the rim just as after an ordinary explosion.
 */
public final class NuclearFallout {
    public static final int RADIUS = 24, WAVE_TICKS = 5, FALLOUT_TICKS = 20 * 60;
    /** An ordinary blast's crater is twice its reach: the hole is radius 48 though the wave hurts out to 24. */
    public static final int CRATER_RADIUS = RADIUS * 2;
    public static final int AWAKENING_RADIUS = 48, CLOUD_TICKS = 600;
    private static final List<Blast> BLASTS = new ArrayList<>();
    /** How long the last crater took to carve, in milliseconds, and how many blocks it broke (tests and logs). */
    private static double lastCarveMillis;
    private static int lastCarveBlocks;
    private NuclearFallout() {}
    private static final class Blast {
        final ServerLevel level;
        /** A radioactive player, or a villager the Eclipse woke a power in. */
        final LivingEntity owner;
        final Vec3 center;
        final BlockPos block;
        final long began;
        final Set<UUID> struck = new HashSet<>();
        final UUID id = UUID.randomUUID();
        final int radius;
        final boolean awakening;
        boolean terrainFinished;
        Blast(LivingEntity owner, boolean awakening) {
            this.owner = owner; level = (ServerLevel) owner.level(); center = groundUnder(owner);
            block = BlockPos.containing(center); began = level.getGameTime();
            this.awakening = awakening;
            radius = awakening ? AWAKENING_RADIUS : RADIUS;
        }
    }
    /** The ground under a caster who has risen a few blocks while charging, or where they are. */
    static Vec3 groundUnder(LivingEntity owner) {
        Vec3 from = owner.position();
        var hit = owner.level().clip(new net.minecraft.world.level.ClipContext(from, from.add(0, -6, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY, owner));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? hit.getLocation() : from;
    }

    public static void begin(ServerPlayer owner) {
        begin(owner, false);
    }
    public static void begin(LivingEntity owner, boolean awakening) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        if (TimeIntroService.isCity(level) || TemporalSnapshotService.isTemporalDimension(level)) return;
        // Overlapping detonations refresh their own area rather than growing an unbounded list.
        BLASTS.removeIf(blast -> blast.owner.getUUID().equals(owner.getUUID()));
        if (BLASTS.size() >= 32) BLASTS.removeFirst();
        Blast blast = new Blast(owner, awakening);
        BLASTS.add(blast);
        cloud(blast, 0);
        if (owner instanceof ServerPlayer player) AbilityEffects.at(player, Kind.NUCLEAR_SHOCKWAVE, blast.radius);
        else AbilityEffects.send(level, Kind.NUCLEAR_SHOCKWAVE, owner.position(), owner.position().add(0, 1, 0), blast.radius);
    }

    /** The radius of the blast {@code owner} last set off, or 0 if none is under way (tests). */
    public static int radiusOf(LivingEntity owner) {
        for (Blast blast : BLASTS) if (blast.owner.getUUID().equals(owner.getUUID())) return blast.radius;
        return 0;
    }

    /** Only available after the awakening's crater has actually been carved. */
    public static Vec3 awakeningFloor(ServerPlayer owner) {
        for (Blast blast : BLASTS) {
            if (blast.owner != owner || !blast.awakening || !blast.terrainFinished) continue;
            // Prefer the middle. A protected block, fluid or narrow ledge can require a nearby spot.
            for (int ring = 0; ring <= 4; ring++) for (int x = -ring; x <= ring; x++) for (int z = -ring; z <= ring; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != ring) continue;
                Vec3 from = new Vec3(blast.block.getX() + x + .5, blast.center.y + 2, blast.block.getZ() + z + .5);
                var hit = blast.level.clip(new net.minecraft.world.level.ClipContext(from,
                        new Vec3(from.x, blast.level.getMinBuildHeight(), from.z),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.ANY, owner));
                if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || hit.getDirection() != Direction.UP
                        || !blast.level.getFluidState(hit.getBlockPos()).isEmpty()) continue;
                Vec3 feet = hit.getLocation().add(0, .01, 0);
                // A spherical crater can end in a one-block pit. Leave room for the whole lying body, and to stand.
                if (blast.level.getWorldBorder().isWithinBounds(BlockPos.containing(feet))
                        && blast.level.noCollision(owner, new AABB(feet.x - 1, feet.y, feet.z - 1,
                        feet.x + 1, feet.y + 1.8, feet.z + 1))) return feet;
            }
        }
        return null;
    }

    public static void tick(MinecraftServer server) {
        for (Blast blast : List.copyOf(BLASTS)) {
            if (blast.level.getServer() != server) continue;
            long age = blast.level.getGameTime() - blast.began;
            if (age > FALLOUT_TICKS && blast.terrainFinished) { BLASTS.remove(blast); continue; }
            double front = Math.min(blast.radius, (age + 1) * blast.radius / (double) WAVE_TICKS);
            if (age <= WAVE_TICKS) wave(blast, front);
            // The wave has reached its edge: the whole crater goes at once.
            if (age >= WAVE_TICKS && !blast.terrainFinished) {
                blast.terrainFinished = true;
                carve(blast);
            }
            if (age <= FALLOUT_TICKS && age % 20 == 0) fallout(blast);
            if (age > 0 && age < CLOUD_TICKS && age % 40 == 0) cloud(blast, (int) age);
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
            // A high enough shield-maker's shield goes up and holds: far less gets through, and never enough to kill.
            float dealt = NuclearShields.brace(target, blast.center, damage);
            boolean held = dealt < damage;
            RadiationDeaths.mark(target);
            if (dealt > 0) target.hurt(blast.level.damageSources().explosion(blast.owner, blast.owner), dealt);
            if (!held) RadiationService.irradiate(target, 160);
            Vec3 away = target.position().subtract(blast.center).multiply(1, 0, 1).normalize();
            target.setDeltaMovement(away.scale(held ? 0.6 : 1.7).add(0, held ? 0.2 : 0.5, 0));
            target.hurtMarked = true;
        }
    }

    /**
     * Breaks every block of the blast's sphere now. A player's blast breaks what they could break (one
     * {@code BreakEvent} each, so claims and protections still hold); anyone else's follows {@code mobGriefing}, like a
     * creeper's. Fluids and unbreakable blocks stay. Later blasts retain the caster's footing; the awakening clears
     * it too, and its intro places the caster on the crater floor. Returns how many blocks broke.
     */
    static int carve(Blast blast) {
        LivingEntity owner = blast.owner;
        ServerLevel level = blast.level;
        ServerPlayer player = owner instanceof ServerPlayer p ? p : null;
        boolean mayBreak = player != null ? player.mayBuild()
                : level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
        if (!mayBreak || owner.level() != level) return 0;
        return carve(level, blast.block, blast.radius == AWAKENING_RADIUS ? AWAKENING_RADIUS : CRATER_RADIUS,
                player, !blast.awakening);
    }

    /** The crater itself, at any size (tests carve small ones inside an arena). */
    public static int carve(ServerLevel level, BlockPos center, int r, ServerPlayer player) {
        return carve(level, center, r, player, true);
    }

    private static int carve(ServerLevel level, BlockPos center, int r, ServerPlayer player, boolean keepFooting) {
        long began = System.nanoTime();
        int inner = (r - 1) * (r - 1), broken = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Top down, so nothing is left hanging over a hole, even for the rest of the tick.
        for (int y = r; y >= -r; y--)
            for (int x = -r; x <= r; x++)
                for (int z = -r; z <= r; z++) {
                    int d2 = x * x + y * y + z * z;
                    if (d2 > r * r) continue;
                    if (keepFooting && player != null && y < 0 && x * x + z * z <= 2) continue;
                    pos.setWithOffset(center, x, y, z);
                    if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || player != null && !level.mayInteract(player, pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0) continue;
                    if (player != null && NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos.immutable(), state, player)).isCanceled()) continue;
                    // Inside, nothing is left to hear about it: skip neighbour and shape updates. The rim, and anything
                    // touching a fluid, update fully. No break sound or particles per block either way; ordinary removal
                    // still drops container contents, relights and records the change for Time Rewind.
                    int flags = d2 <= inner && !nearFluid(level, pos) ? Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE : Block.UPDATE_ALL;
                    if (level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags)) broken++;
                }
        lastCarveMillis = (System.nanoTime() - began) / 1.0e6;
        lastCarveBlocks = broken;
        return broken;
    }

    private static boolean nearFluid(ServerLevel level, BlockPos pos) {
        for (Direction d : Direction.values())
            if (!level.getFluidState(pos.relative(d)).isEmpty()) return true;
        return false;
    }

    /** The last crater's carve time in milliseconds, and how many blocks it broke. */
    public static double lastCarveMillis() { return lastCarveMillis; }
    public static int lastCarveBlocks() { return lastCarveBlocks; }

    private static void fallout(Blast blast) {
        AbilityEffects.send(blast.level, Kind.NUCLEAR_FALLOUT, blast.center, blast.center, blast.radius);
        for (LivingEntity target : blast.level.getEntitiesOfClass(LivingEntity.class,
                new AABB(blast.center, blast.center).inflate(blast.radius), target -> vulnerable(blast, target))) {
            if (target.position().distanceToSqr(blast.center) > blast.radius * blast.radius || NuclearShields.shielded(target)) continue;
            if (com.jayptucker.heroesevolved.hazard.Suits.sealed(target)) { com.jayptucker.heroesevolved.hazard.Suits.wear(target, 2); continue; }
            RadiationService.irradiate(target, 60);
            RadiationDeaths.mark(target);
            target.hurt(blast.level.damageSources().indirectMagic(blast.owner, blast.owner),
                    2 * (1 - com.jayptucker.heroesevolved.hazard.Suits.radiation(target)));
        }
    }
    private static void cloud(Blast blast, int age) {
        NuclearCloudPayload payload = new NuclearCloudPayload(blast.id, blast.level.dimension().location(),
                blast.center, blast.radius == AWAKENING_RADIUS ? 1.6F : 1, blast.radius, age);
        for (ServerPlayer viewer : blast.level.players())
            if (viewer.position().distanceToSqr(blast.center) <= 400 * 400) PacketDistributor.sendToPlayer(viewer, payload);
    }
    public static void clear() { BLASTS.clear(); NuclearShields.clear(); }
}
