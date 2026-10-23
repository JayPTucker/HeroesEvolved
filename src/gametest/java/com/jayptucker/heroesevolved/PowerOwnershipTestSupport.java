package com.jayptucker.heroesevolved;

import com.jayptucker.heroesevolved.data.PowerOwnershipSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import java.lang.reflect.Field;

/** Fixtures run sequentially because production ownership is genuinely exclusive, even for FakePlayers. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class PowerOwnershipTestSupport {
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void start(ServerStartedEvent event) {
        replace(event.getServer(), new PowerOwnershipSavedData());
    }
    public static void release(ServerPlayer player) {
        var data = PowerOwnershipSavedData.get(player.getServer());
        CompoundTag tag = data.save(new CompoundTag(), player.getServer().registryAccess());
        CompoundTag owners = tag.getCompound("owners");
        for (String power : java.util.Set.copyOf(owners.getAllKeys()))
            if (owners.getUUID(power).equals(player.getUUID())) owners.remove(power);
        replace(player.getServer(), PowerOwnershipSavedData.load(tag, player.getServer().registryAccess()));
    }
    public static void replace(MinecraftServer server, PowerOwnershipSavedData data) {
        try {
            Field initialized = PowerOwnershipSavedData.class.getDeclaredField("initialized");
            initialized.setAccessible(true); initialized.setBoolean(data, true);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        server.overworld().getDataStorage().set(PowerOwnershipSavedData.NAME, data);
    }
}
