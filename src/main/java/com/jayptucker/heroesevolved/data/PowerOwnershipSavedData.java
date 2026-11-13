package com.jayptucker.heroesevolved.data;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** One persistent owner per power, shared by every dimension and retained while owners are offline. */
public final class PowerOwnershipSavedData extends SavedData {
    public static final String NAME = "heroesevolved_power_owners";
    private final Map<ResourceLocation, UUID> owners = new HashMap<>();
    private boolean initialized;
    public static PowerOwnershipSavedData get(MinecraftServer server) {
        PowerOwnershipSavedData data = server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(PowerOwnershipSavedData::new, PowerOwnershipSavedData::load), NAME);
        data.initialize(server);
        return data;
    }
    public static PowerOwnershipSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PowerOwnershipSavedData data = new PowerOwnershipSavedData();
        CompoundTag entries = tag.getCompound("owners");
        for (String key : entries.getAllKeys()) {
            ResourceLocation power = ResourceLocation.tryParse(key);
            if (power != null && entries.hasUUID(key)) data.owners.put(power, entries.getUUID(key));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag entries = new CompoundTag();
        owners.forEach((power, owner) -> entries.putUUID(power.toString(), owner));
        tag.put("owners", entries); return tag;
    }
    public Optional<UUID> owner(ResourceLocation power) { return Optional.ofNullable(owners.get(power)); }
    /** True while no power in this world has ever had an owner. */
    public boolean noneClaimed() { return owners.isEmpty(); }
    public boolean available(ResourceLocation power, UUID player) { return !owners.containsKey(power) || owners.get(power).equals(player); }
    /** Frees a power, for one that no longer exists. */
    public void release(ResourceLocation power) { if (owners.remove(power) != null) setDirty(); }
    public boolean claim(ResourceLocation power, UUID player) {
        if (!available(power, player)) return false;
        if (owners.putIfAbsent(power, player) == null) setDirty();
        return true;
    }
    /**
     * For admins: frees the claim on {@code power} when its recorded owner no longer holds it (an admin removed or replaced
     * it, or it was a dormant power since cleared). Returns true when {@code player} may now take it.
     */
    public boolean reclaimForAdmin(MinecraftServer server, ResourceLocation power, UUID player) {
        UUID owner = owners.get(power);
        if (owner == null || owner.equals(player)) return true;
        if (holds(server, owner, power)) return false;
        release(power);
        return true;
    }

    /** Whether {@code player} still has {@code power} assigned: live data when online, their saved file when offline. */
    public static boolean holds(MinecraftServer server, UUID player, ResourceLocation power) {
        net.minecraft.world.entity.player.Player online = server.getPlayerList().getPlayer(player);
        for (var level : server.getAllLevels()) if (online == null) online = level.getPlayerByUUID(player);
        if (online != null) return online.getData(ModDataAttachments.PLAYER_ABILITIES).hasAbility(power);
        Path file = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player + ".dat");
        if (!Files.isRegularFile(file)) return false;
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.create(16 * 1024 * 1024));
            return root.getCompound("neoforge:attachments").getCompound("heroesevolved:player_abilities")
                    .getCompound("abilities").contains(power.toString());
        } catch (Exception e) {
            // Unreadable: keep the claim rather than hand someone's power away by mistake.
            HeroesEvolved.LOGGER.warn("Could not read {} to check power ownership", file.getFileName(), e);
            return true;
        }
    }

    public void importLegacyPlayer(UUID player, CompoundTag root) {
        CompoundTag powers = root.getCompound("neoforge:attachments")
                .getCompound("heroesevolved:player_abilities").getCompound("abilities");
        for (String key : powers.getAllKeys()) {
            ResourceLocation power = ResourceLocation.tryParse(key);
            if (power != null && AbilityRegistry.ABILITIES.containsKey(power)) claim(power, player);
        }
    }
    private void initialize(MinecraftServer server) {
        if (initialized) return;
        initialized = true;
        Path directory = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        if (Files.isDirectory(directory)) try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".dat"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
                try {
                    String file = path.getFileName().toString();
                    UUID player = UUID.fromString(file.substring(0, file.length() - 4));
                    CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.create(16 * 1024 * 1024));
                    importLegacyPlayer(player, root);
                } catch (Exception e) { HeroesEvolved.LOGGER.warn("Could not migrate power ownership from {}", path.getFileName(), e); }
            }
        } catch (Exception e) { HeroesEvolved.LOGGER.error("Could not scan existing player power ownership", e); }
        for (var player : server.getPlayerList().getPlayers())
            player.getData(ModDataAttachments.PLAYER_ABILITIES).assignedPower().ifPresent(entry -> claim(entry.getKey(), player.getUUID()));
    }
}
