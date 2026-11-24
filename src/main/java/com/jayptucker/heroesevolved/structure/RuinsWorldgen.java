package com.jayptucker.heroesevolved.structure;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class RuinsWorldgen {
    private static final DeferredRegister<StructureProcessorType<?>> PROCESSORS = DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, HeroesEvolved.MOD_ID);
    public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<RuinsWaterProcessor>> WATER =
            PROCESSORS.register("ruins_water", () -> () -> RuinsWaterProcessor.CODEC);
    private RuinsWorldgen() {}
    public static void register(IEventBus bus) { PROCESSORS.register(bus); }
}
