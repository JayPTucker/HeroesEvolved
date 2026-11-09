package com.jayptucker.heroesevolved.grid;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.EnergyStorage;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Stand-ins for Mekanism/Thermal machines in the test world only. A furnace, barrel or compass that a test
 * has powered exposes a real Forge Energy capability, through exactly the lookup those mods' blocks and
 * items go through; every other one exposes nothing, so other tests never see a machine.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class TestMachines {
    private static final Map<BlockEntity, EnergyStorage> BLOCKS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ItemStack, EnergyStorage> ITEMS = Collections.synchronizedMap(new WeakHashMap<>());

    private TestMachines() {}

    @SubscribeEvent public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BlockEntityType.FURNACE, (entity, side) -> BLOCKS.get(entity));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BlockEntityType.BARREL, (entity, side) -> BLOCKS.get(entity));
        event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, context) -> ITEMS.get(stack), Items.COMPASS);
    }

    /** Gives this block entity an FE buffer, like a machine's internal battery. */
    public static EnergyStorage power(BlockEntity entity, int capacity, int stored) {
        EnergyStorage storage = new EnergyStorage(capacity, capacity, capacity, stored);
        BLOCKS.put(entity, storage);
        entity.getLevel().invalidateCapabilities(entity.getBlockPos());
        return storage;
    }

    /** A consumer: it accepts power but will not give any back, like most real machines. */
    public static EnergyStorage consumer(BlockEntity entity, int capacity, int stored) {
        EnergyStorage storage = new EnergyStorage(capacity, capacity, 0, stored);
        BLOCKS.put(entity, storage);
        entity.getLevel().invalidateCapabilities(entity.getBlockPos());
        return storage;
    }

    public static EnergyStorage power(ItemStack stack, int capacity, int stored) {
        EnergyStorage storage = new EnergyStorage(capacity, capacity, capacity, stored);
        ITEMS.put(stack, storage);
        return storage;
    }
}
