package com.jayptucker.heroesevolved.grid;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.GridFieldPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;

/**
 * Electricity that leaves a body reaches real machines.
 *
 * <p>A <b>machine</b> is any block entity that exposes NeoForge's energy capability (that covers Mekanism,
 * Thermal, Powah, Flux Networks, Create Crafts &amp; Additions and nearly every other FE/RF mod, with no
 * compile-time dependency on any of them), plus Create's kinetic blocks, which run on rotation rather
 * than electricity but are the other machinery players build.
 *
 * <p>An electromagnetic pulse (Radiation's EMP, Electrokinesis's Blackout) opens a <b>field</b> for a few
 * seconds. Inside it:
 * <ul>
 *   <li>redstone puts out no signal (the original Radiation EMP behaviour, unchanged);</li>
 *   <li>stored Forge Energy is drained, at most {@code maxDrainPerMachine} per machine, and anything that
 *   refills (a generator) keeps being drained while the field lasts;</li>
 *   <li>machines stop ticking, so consumers that cannot be drained still stop working;</li>
 *   <li>Create's kinetic blocks read zero speed on both sides, so their shafts visibly seize;</li>
 *   <li>energy-holding items carried by hostile creatures and harmable players are drained.</li>
 * </ul>
 * Lightning that lands on a machine charges it instead ({@link #charge}).
 *
 * <p>Every drained or stalled machine passes {@link WorldMutationPolicy}, so claims and spawn protection
 * hold. Fields never place, replace or break blocks, never touch inventories, and every count is bounded.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class GridDisruption {
    public static final int MAX_MACHINES = 128, MAX_FIELDS = 32, MAX_STALLS = 64;
    /** Forge Energy that becomes one point of Electrokinesis Charge when Blackout drinks a grid. */
    public static final int FE_PER_CHARGE = 2_000;
    private static final String CREATE_KINETIC = "com.simibubi.create.content.kinetics.base.KineticBlockEntity";

    private static final class Field {
        final ServerLevel level;
        final BlockPos center;
        final int radius;
        final long until;
        @Nullable final ServerPlayer source;
        final Set<BlockPos> machines = new LinkedHashSet<>();
        final Set<BlockPos> kinetic = new LinkedHashSet<>();
        long drained;
        Field(ServerLevel level, BlockPos center, int radius, long until, @Nullable ServerPlayer source) {
            this.level = level; this.center = center.immutable(); this.radius = radius; this.until = until; this.source = source;
        }
        boolean covers(BlockPos pos) { return pos.distSqr(center) <= (double) radius * radius; }
    }

    /** What one pulse reached. {@code shown} is a bounded sample of machine positions for visuals. */
    public record Pulse(int machines, int kinetic, long drained, int items, List<BlockPos> shown) {
        public static final Pulse NONE = new Pulse(0, 0, 0, 0, List.of());
        public int charge() { return (int) Math.min(100, drained / FE_PER_CHARGE); }
    }

    private record ClientField(ResourceKey<Level> dimension, BlockPos center, int radius, long until) {}

    private static final List<Field> FIELDS = new ArrayList<>();
    /** Single machines shut down on purpose (Technopathy's Kill Switch), per level, until a game time. */
    private static final Map<ServerLevel, Map<BlockPos, Long>> STALLS = new HashMap<>();
    /** The client's copy of nearby fields; only Create's rotation reads it. */
    private static volatile List<ClientField> client = List.of();
    private static final Map<Class<?>, Boolean> KINETIC_CLASSES = Collections.synchronizedMap(new IdentityHashMap<>());
    /** Create blocks to re-sync a second time after a field ends, for clients whose clock runs a little behind. */
    private record Resync(ServerLevel level, List<BlockPos> positions, long at) {}
    private static final List<Resync> RESYNCS = new ArrayList<>();

    private GridDisruption() {}

    // ------------------------------------------------------------------
    // Queries used by mixins (hot paths: they bail out at once when nothing is active)
    // ------------------------------------------------------------------

    /** Redstone at {@code pos} is dead: it reads and gives no signal. */
    public static boolean jammed(ServerLevel level, BlockPos pos) {
        if (FIELDS.isEmpty()) return false;
        long now = level.getGameTime();
        for (Field field : FIELDS) if (field.level == level && now < field.until && field.covers(pos)) return true;
        return false;
    }

    /** The block entity at {@code pos} skips its tick this game tick. Server side only. */
    public static boolean stalled(ServerLevel level, BlockPos pos) {
        if (FIELDS.isEmpty() && STALLS.isEmpty()) return false;
        for (Field field : FIELDS) if (field.level == level && field.machines.contains(pos)) return true;
        Map<BlockPos, Long> stalls = STALLS.get(level);
        return stalls != null && stalls.getOrDefault(pos, 0L) > level.getGameTime();
    }

    /** A Create kinetic block at {@code pos} reads zero speed. Called on both sides, many times a frame. */
    public static boolean seized(@Nullable Level level, BlockPos pos) {
        if (level == null) return false;
        if (level instanceof ServerLevel server) {
            if (FIELDS.isEmpty() && STALLS.isEmpty()) return false;
            for (Field field : FIELDS) if (field.level == server && field.kinetic.contains(pos)) return true;
            Map<BlockPos, Long> stalls = STALLS.get(server);
            return stalls != null && stalls.getOrDefault(pos, 0L) > server.getGameTime();
        }
        List<ClientField> fields = client;
        if (fields.isEmpty()) return false;
        long now = level.getGameTime();
        for (ClientField field : fields)
            if (now < field.until && field.dimension == level.dimension() && pos.distSqr(field.center) <= (double) field.radius * field.radius)
                return true;
        return false;
    }

    public static int activeFields() { return FIELDS.size(); }

    // ------------------------------------------------------------------
    // What counts as a machine
    // ------------------------------------------------------------------

    /** Every distinct Forge Energy storage the block exposes, on any side. */
    public static List<IEnergyStorage> storages(Level level, BlockPos pos, @Nullable BlockState state, @Nullable BlockEntity entity) {
        if (entity == null) return List.of();
        BlockState at = state != null ? state : level.getBlockState(pos);
        Set<IEnergyStorage> found = Collections.newSetFromMap(new IdentityHashMap<>());
        IEnergyStorage any = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, at, entity, null);
        if (any != null) found.add(any);
        for (Direction side : Direction.values()) {
            IEnergyStorage sided = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, at, entity, side);
            if (sided != null) found.add(sided);
        }
        return List.copyOf(found);
    }

    /** Create's kinetic blocks, found by class name so Create is never needed to build or run this mod. */
    public static boolean isKinetic(@Nullable BlockEntity entity) {
        if (entity == null) return false;
        return KINETIC_CLASSES.computeIfAbsent(entity.getClass(), type -> {
            for (Class<?> c = type; c != null && c != BlockEntity.class; c = c.getSuperclass())
                if (c.getName().equals(CREATE_KINETIC)) return true;
            return false;
        });
    }

    public static boolean isMachine(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        BlockEntity entity = level.getBlockEntity(pos);
        return entity != null && (isKinetic(entity) || !storages(level, pos, null, entity).isEmpty());
    }

    public static long stored(Level level, BlockPos pos) {
        long total = 0;
        for (IEnergyStorage storage : storages(level, pos, null, level.getBlockEntity(pos))) total += storage.getEnergyStored();
        return total;
    }

    // ------------------------------------------------------------------
    // Pulses
    // ------------------------------------------------------------------

    /**
     * Opens a field: redstone, machines and Create's rotation die inside {@code radius} for {@code ticks}.
     * {@code source} (may be null for story events) is checked against claims for every machine.
     */
    public static Pulse emp(@Nullable ServerPlayer source, ServerLevel level, BlockPos center, int radius, int ticks) {
        if (FIELDS.size() >= MAX_FIELDS) release(FIELDS.removeFirst());
        Field field = new Field(level, center, radius, level.getGameTime() + ticks, source);
        FIELDS.add(field);
        refreshRedstone(field);
        int items = 0;
        boolean machines = COMMON.gridAffectMachines.get(), create = COMMON.gridAffectCreate.get();
        if (machines || create) {
            for (BlockEntity entity : blockEntities(level, center, radius)) {
                if (field.machines.size() >= MAX_MACHINES) break;
                BlockPos pos = entity.getBlockPos();
                boolean kinetic = create && isKinetic(entity);
                List<IEnergyStorage> storages = machines ? storages(level, pos, entity.getBlockState(), entity) : List.of();
                if (!kinetic && storages.isEmpty()) continue;
                if (source != null && !WorldMutationPolicy.canEdit(source, level, pos)) continue;
                field.machines.add(pos.immutable());
                if (kinetic) {
                    field.kinetic.add(pos.immutable());
                    // Create re-reads speed when its block entity syncs; this makes the shafts stop visibly.
                    level.sendBlockUpdated(pos, entity.getBlockState(), entity.getBlockState(), Block.UPDATE_CLIENTS);
                }
                field.drained += drain(storages, COMMON.gridMaxDrainPerMachine.get());
            }
        }
        if (COMMON.gridAffectCarriedItems.get() && source != null) {
            Vec3 middle = Vec3.atCenterOf(center);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(center).inflate(radius),
                    target -> target != source && target.isAlive() && target.distanceToSqr(middle) <= (double) radius * radius
                            && com.jayptucker.heroesevolved.expansion.ExpansionSkills.hostile(source, target))) {
                long drained = drainCarried(target, COMMON.gridMaxDrainPerMachine.get());
                if (drained > 0) { items++; field.drained += drained; }
            }
        }
        // Clients in range learn the field so Create's rotation can stop on their side too.
        if (!field.kinetic.isEmpty()) {
            GridFieldPayload payload = new GridFieldPayload(center, radius, ticks);
            for (ServerPlayer viewer : level.players())
                if (viewer.blockPosition().distSqr(center) <= (radius + 96.0) * (radius + 96.0)) PacketDistributor.sendToPlayer(viewer, payload);
        }
        List<BlockPos> shown = field.machines.stream().limit(24).toList();
        for (BlockPos pos : shown) AbilityEffects.send(level, Kind.GRID_SHORT, Vec3.atCenterOf(pos), Vec3.atCenterOf(pos).add(0, 1, 0), 1.0);
        if (!shown.isEmpty()) level.playSound(null, center, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 1.6F);
        return new Pulse(field.machines.size(), field.kinetic.size(), field.drained, items, shown);
    }

    /**
     * Redstone alone dies inside {@code radius} for {@code ticks}: lamps blink out, doors and pistons drop, circuits stop,
     * and wake as they were. Nothing is drained and no machine stalls. The Company's omens use it for a misfire.
     */
    public static void jam(ServerLevel level, BlockPos center, int radius, int ticks) {
        if (FIELDS.size() >= MAX_FIELDS) release(FIELDS.removeFirst());
        Field field = new Field(level, center, radius, level.getGameTime() + ticks, null);
        FIELDS.add(field);
        refreshRedstone(field);
    }

    /**
     * Shuts one machine down for {@code ticks} (Technopathy's Kill Switch; the Company's omens with no source).
     * False if it is not a machine.
     */
    public static boolean stall(@Nullable ServerPlayer source, ServerLevel level, BlockPos pos, int ticks) {
        BlockEntity entity = level.getBlockEntity(pos);
        boolean kinetic = COMMON.gridAffectCreate.get() && isKinetic(entity);
        boolean powered = COMMON.gridAffectMachines.get() && !storages(level, pos, null, entity).isEmpty();
        if (!kinetic && !powered || source != null && !WorldMutationPolicy.canEdit(source, level, pos)) return false;
        Map<BlockPos, Long> stalls = STALLS.computeIfAbsent(level, ignored -> new HashMap<>());
        if (stalls.size() >= MAX_STALLS && !stalls.containsKey(pos)) return false;
        stalls.put(pos.immutable(), level.getGameTime() + ticks);
        if (kinetic) {
            level.sendBlockUpdated(pos, entity.getBlockState(), entity.getBlockState(), Block.UPDATE_CLIENTS);
            PacketDistributor.sendToPlayersNear(level, null, pos.getX(), pos.getY(), pos.getZ(), 96, new GridFieldPayload(pos, 0, ticks));
        }
        AbilityEffects.send(level, Kind.GRID_SHORT, Vec3.atCenterOf(pos), Vec3.atCenterOf(pos).add(0, 1, 0), 1.0);
        return true;
    }

    /**
     * Pushes up to {@code amount} FE into the machine at {@code pos} (lightning landing on it). Returns what
     * it accepted. A machine inside a pulse takes nothing: the field eats the current.
     */
    public static int charge(@Nullable ServerPlayer source, ServerLevel level, BlockPos pos, int amount) {
        if (amount <= 0 || !COMMON.gridAffectMachines.get() || !level.hasChunkAt(pos) || jammed(level, pos)) return 0;
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null || source != null && !level.mayInteract(source, pos)) return 0;
        int accepted = 0;
        for (IEnergyStorage storage : storages(level, pos, null, entity)) {
            if (accepted >= amount) break;
            if (storage.canReceive()) accepted += storage.receiveEnergy(amount - accepted, false);
        }
        if (accepted > 0) AbilityEffects.send(level, Kind.GRID_CHARGE, Vec3.atCenterOf(pos), Vec3.atCenterOf(pos).add(0, 1, 0),
                Math.clamp(accepted / (double) Math.max(1, amount), 0.3, 1.0));
        return accepted;
    }

    /** {@link #charge} for the machines around a point (Storm Surge). Returns the ones that took current. */
    public static List<BlockPos> chargeArea(@Nullable ServerPlayer source, ServerLevel level, BlockPos center, int radius, int amountEach, int limit) {
        if (amountEach <= 0 || !COMMON.gridAffectMachines.get()) return List.of();
        List<BlockPos> charged = new ArrayList<>();
        for (BlockEntity entity : blockEntities(level, center, radius)) {
            if (charged.size() >= limit) break;
            if (charge(source, level, entity.getBlockPos(), amountEach) > 0) charged.add(entity.getBlockPos().immutable());
        }
        return charged;
    }

    /** Drains the energy-holding items a creature carries, and the creature itself if it stores energy. */
    public static long drainCarried(LivingEntity entity, int limitEach) {
        long drained = 0;
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (EquipmentSlot slot : EquipmentSlot.values()) drained += drainItem(entity.getItemBySlot(slot), limitEach, seen);
        if (entity instanceof Player player) for (ItemStack stack : player.getInventory().items) drained += drainItem(stack, limitEach, seen);
        IEnergyStorage body = entity.getCapability(Capabilities.EnergyStorage.ENTITY, null);
        if (body != null) drained += drain(List.of(body), limitEach);
        return drained;
    }

    private static long drainItem(ItemStack stack, int limit, Set<ItemStack> seen) {
        if (stack.isEmpty() || !seen.add(stack)) return 0;
        IEnergyStorage storage = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return storage == null ? 0 : drain(List.of(storage), limit);
    }

    /** Takes up to {@code limit} FE out of the given storages; ones that refuse extraction are left alone. */
    static long drain(List<IEnergyStorage> storages, int limit) {
        long total = 0;
        for (IEnergyStorage storage : storages) {
            if (total >= limit) break;
            if (!storage.canExtract()) continue;
            int want = (int) Math.min(limit - total, storage.getEnergyStored());
            if (want > 0) total += Math.max(0, storage.extractEnergy(want, false));
        }
        return total;
    }

    // ------------------------------------------------------------------
    // Lifetime
    // ------------------------------------------------------------------

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }

    public static void tick(MinecraftServer server) {
        if (!RESYNCS.isEmpty()) RESYNCS.removeIf(pending -> {
            if (pending.level.getServer() != server || pending.level.getGameTime() < pending.at) return false;
            for (BlockPos pos : pending.positions) resync(pending.level, pos);
            return true;
        });
        if (FIELDS.isEmpty() && STALLS.isEmpty()) return;
        for (Field field : List.copyOf(FIELDS)) {
            if (field.level.getServer() != server) continue;
            long now = field.level.getGameTime();
            if (now >= field.until) { FIELDS.remove(field); release(field); continue; }
            if (now % 10 != 0) continue;
            // Generators keep producing; the field keeps eating it. Machines spit sparks while they are dead.
            int shown = 0;
            for (BlockPos pos : field.machines) {
                if (!field.level.hasChunkAt(pos)) continue;
                BlockEntity entity = field.level.getBlockEntity(pos);
                if (entity == null) continue;
                drain(storages(field.level, pos, entity.getBlockState(), entity), Math.max(1, COMMON.gridMaxDrainPerMachine.get() / 10));
                if (shown < 4 && field.level.getRandom().nextInt(3) == 0) {
                    shown++;
                    AbilityEffects.send(field.level, Kind.GRID_SHORT, Vec3.atCenterOf(pos), Vec3.atCenterOf(pos).add(0, 1, 0), 0.5);
                }
            }
        }
        for (var entry : STALLS.entrySet()) {
            ServerLevel level = entry.getKey();
            if (level.getServer() != server) continue;
            entry.getValue().entrySet().removeIf(stall -> {
                if (stall.getValue() > level.getGameTime()) return false;
                resync(level, stall.getKey());
                return true;
            });
        }
        STALLS.values().removeIf(Map::isEmpty);
    }

    private static void release(Field field) {
        refreshRedstone(field);
        for (BlockPos pos : field.kinetic) resync(field.level, pos);
        if (!field.kinetic.isEmpty() && RESYNCS.size() < MAX_FIELDS)
            RESYNCS.add(new Resync(field.level, List.copyOf(field.kinetic), field.level.getGameTime() + 20));
    }

    private static void resync(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return;
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    /** Neighbours re-read their signal, so redstone dies at once and wakes at once when the field ends. */
    private static void refreshRedstone(Field field) {
        int r = field.radius;
        for (BlockPos pos : BlockPos.betweenClosed(field.center.offset(-r, -r, -r), field.center.offset(r, r, r))) {
            if (!field.covers(pos) || !field.level.hasChunkAt(pos)) continue;
            BlockState state = field.level.getBlockState(pos);
            if (state.isSignalSource() || state.hasProperty(BlockStateProperties.POWERED) || state.is(Blocks.REDSTONE_WIRE))
                field.level.updateNeighborsAt(pos.immutable(), state.getBlock());
        }
    }

    /** Loaded block entities within {@code radius}, nearest first, read straight from chunk maps (no block scan). */
    private static List<BlockEntity> blockEntities(ServerLevel level, BlockPos center, int radius) {
        List<BlockEntity> found = new ArrayList<>();
        double limit = (double) radius * radius;
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++)
            for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity entity : chunk.getBlockEntities().values())
                    if (!entity.isRemoved() && entity.getBlockPos().distSqr(center) <= limit) found.add(entity);
            }
        found.sort(java.util.Comparator.comparingDouble(entity -> entity.getBlockPos().distSqr(center)));
        return found;
    }

    // ------------------------------------------------------------------
    // Client mirror
    // ------------------------------------------------------------------

    /** Called on the client when a field opens nearby. A radius of 0 is a single stalled block. */
    public static void acceptClient(Level level, BlockPos center, int radius, int ticks) {
        long now = level.getGameTime();
        List<ClientField> next = new ArrayList<>();
        for (ClientField field : client) if (field.until > now && field.dimension == level.dimension()) next.add(field);
        if (next.size() >= MAX_FIELDS) next.removeFirst();
        next.add(new ClientField(level.dimension(), center.immutable(), radius, now + ticks));
        client = List.copyOf(next);
    }

    public static void clear() {
        List<Field> old = List.copyOf(FIELDS);
        FIELDS.clear();
        old.forEach(GridDisruption::release);
        STALLS.clear();
        RESYNCS.clear();
        client = List.of();
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { clear(); }
}
