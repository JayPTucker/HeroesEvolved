package com.jayptucker.heroesevolved.time;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
/** Offline players are restored on login, never duplicated or impersonated by an entity spawn. */
public final class PendingPlayerRewinds extends SavedData {
    private final Map<UUID, CompoundTag> pending = new HashMap<>();
    public static PendingPlayerRewinds get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(PendingPlayerRewinds::new,
                (tag, registries) -> {
                    var data = new PendingPlayerRewinds();
                    for (String key : tag.getAllKeys()) try { data.pending.put(UUID.fromString(key), tag.getCompound(key)); }
                    catch (IllegalArgumentException ignored) {}
                    return data;
                }), "heroesevolved_pending_rewinds");
    }
    public void put(UUID player, ResourceLocation dimension, CompoundTag state) {
        CompoundTag entry = new CompoundTag(); entry.putString("dimension", dimension.toString()); entry.put("state", state.copy());
        pending.put(player, entry); setDirty();
    }
    public void login(ServerPlayer player) {
        CompoundTag entry = pending.get(player.getUUID());
        if (entry == null) return;
        ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dimension"));
        if (dimension == null) return;
        var level = player.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimension));
        if (level == null) return;
        TimeRewindEntities.restorePlayer(player, level, entry.getCompound("state"));
        player.getServer().getPlayerList().saveAll();
        pending.remove(player.getUUID()); setDirty();
        player.getServer().overworld().getDataStorage().save();
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("A recent rewind restored your place in the timeline while you were away."));
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        pending.forEach((id, state) -> tag.put(id.toString(), state)); return tag;
    }
}
