package com.jayptucker.heroesevolved.assembly;

import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Per-dimension durable leases. Only air is replaced, so every legitimate restoration is air. */
public final class AssemblyTerrain extends SavedData {
    public static final int MAX_CELLS = 2048, OWNER_LIMIT = 256, CLEANUP_BUDGET = 96;
    public record Cell(BlockPos pos, UUID owner, long until) {}
    private final Map<BlockPos, Cell> cells = new LinkedHashMap<>();
    private final ArrayDeque<BlockPos> inspection = new ArrayDeque<>();
    private final Map<UUID, Integer> ownerCounts = new HashMap<>();
    public static AssemblyTerrain get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(AssemblyTerrain::new, AssemblyTerrain::load), "heroesevolved_assembly_leases");
    }
    public static AssemblyTerrain load(CompoundTag tag, HolderLookup.Provider registries) {
        AssemblyTerrain data = new AssemblyTerrain();
        for (Tag value : tag.getList("cells", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            if (!entry.hasUUID("owner") || data.cells.size() >= MAX_CELLS) continue;
            BlockPos pos = BlockPos.of(entry.getLong("pos"));
            UUID owner = entry.getUUID("owner");
            if (data.cells.putIfAbsent(pos, new Cell(pos, owner, entry.getLong("until"))) == null)
                data.ownerCounts.merge(owner, 1, Integer::sum);
        }
        data.inspection.addAll(data.cells.keySet());
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Cell cell : cells.values()) {
            CompoundTag entry = new CompoundTag(); entry.putLong("pos", cell.pos.asLong());
            entry.putUUID("owner", cell.owner); entry.putLong("until", cell.until); list.add(entry);
        }
        tag.put("cells", list); return tag;
    }
    public boolean valid(ServerLevel level, BlockPos pos) {
        Cell cell = cells.get(pos);
        return cell != null && cell.until > level.getGameTime();
    }
    public boolean owned(ServerLevel level, BlockPos pos, UUID owner) {
        Cell cell = cells.get(pos);
        return cell != null && cell.owner.equals(owner) && valid(level, pos)
                && level.hasChunkAt(pos) && level.getBlockState(pos).is(AssemblyBlocks.MATTER.get());
    }
    public List<BlockPos> owned(ServerPlayer player) {
        return cells.values().stream().filter(c -> c.owner.equals(player.getUUID()))
                .map(Cell::pos).filter(p -> owned(player.serverLevel(), p, player.getUUID())).toList();
    }
    public int count(UUID owner) { return ownerCounts.getOrDefault(owner, 0); }
    public boolean available(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        return cells.size() < MAX_CELLS && count(player.getUUID()) < OWNER_LIMIT && level.hasChunkAt(pos)
                && !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.getBlockState(pos).isAir() && level.getBlockEntity(pos) == null
                && level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(pos), e -> !e.isSpectator() && e.isAlive()).isEmpty();
    }
    /** Inventory is reserved before permission callbacks, and returned only if no shape was placed. */
    public boolean place(ServerPlayer player, BlockPos position, int duration) {
        BlockPos pos = position.immutable(); ServerLevel level = player.serverLevel();
        if (!available(player, pos)) return false;
        var payment = AssemblyMaterials.take(player);
        if (payment.isEmpty()) return false;
        if (!WorldMutationPolicy.place(player, level, pos, AssemblyBlocks.MATTER.get().defaultBlockState())) {
            AssemblyMaterials.refund(player, payment); return false;
        }
        Cell previous = cells.put(pos, new Cell(pos, player.getUUID(), level.getGameTime() + Math.max(1, duration)));
        if (previous != null) ownerCounts.computeIfPresent(previous.owner, (id, count) -> count <= 1 ? null : count - 1);
        ownerCounts.merge(player.getUUID(), 1, Integer::sum);
        // Removing stale queue entries keeps repeated mine/rebuild use bounded too.
        inspection.remove(pos);
        inspection.addLast(pos); setDirty(); return true;
    }
    public boolean remove(ServerLevel level, BlockPos pos, UUID owner) {
        Cell cell = cells.get(pos);
        if (cell == null || !cell.owner.equals(owner) || !level.hasChunkAt(pos)) return false;
        cells.remove(pos); inspection.remove(pos);
        ownerCounts.computeIfPresent(cell.owner, (id, count) -> count <= 1 ? null : count - 1); setDirty();
        if (level.getBlockState(pos).is(AssemblyBlocks.MATTER.get())) level.removeBlock(pos, false);
        return true;
    }
    public void tick(ServerLevel level) {
        int checks = Math.min(CLEANUP_BUDGET, inspection.size());
        while (checks-- > 0) {
            BlockPos pos = inspection.removeFirst(); Cell cell = cells.get(pos);
            if (cell == null) continue;
            boolean loaded = level.hasChunkAt(pos);
            if (!loaded && cell.until > level.getGameTime()) { inspection.addLast(pos); continue; }
            boolean construct = loaded && level.getBlockState(pos).is(AssemblyBlocks.MATTER.get());
            if (!construct || cell.until <= level.getGameTime()) {
                cells.remove(pos); ownerCounts.computeIfPresent(cell.owner, (id, count) -> count <= 1 ? null : count - 1);
                setDirty(); if (construct) level.removeBlock(pos, false);
            } else inspection.addLast(pos);
        }
    }
    public void clearOwner(ServerPlayer player) {
        clearOwner(player.serverLevel(), player.getUUID());
    }
    public void clearOwner(ServerLevel level, UUID owner) {
        for (Cell cell : List.copyOf(cells.values())) if (cell.owner.equals(owner)) {
            // Orphan validation on chunk load means cancelled unloaded cells need no live reservation.
            if (level.hasChunkAt(cell.pos)) remove(level, cell.pos, owner);
            else {
                cells.remove(cell.pos); inspection.remove(cell.pos);
                ownerCounts.computeIfPresent(cell.owner, (id, count) -> count <= 1 ? null : count - 1); setDirty();
            }
        }
    }
    /** Palette filtering skips every ordinary section; only sections containing our block are scanned. */
    public static void recoverChunk(ServerLevel level, net.minecraft.world.level.chunk.LevelChunk chunk) {
        var sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            var section = sections[sectionIndex];
            if (!section.maybeHas(state -> state.is(AssemblyBlocks.MATTER.get()))) continue;
            int yBase = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                if (section.getBlockState(x, y, z).is(AssemblyBlocks.MATTER.get()))
                    level.scheduleTick(new BlockPos(chunk.getPos().getMinBlockX() + x, yBase + y,
                            chunk.getPos().getMinBlockZ() + z), AssemblyBlocks.MATTER.get(), 1);
        }
    }
}
