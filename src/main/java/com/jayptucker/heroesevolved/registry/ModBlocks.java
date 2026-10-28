package com.jayptucker.heroesevolved.registry;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.jayptucker.heroesevolved.formula.CentrifugeBlockEntity;
import com.jayptucker.heroesevolved.formula.LabBlock;
import com.jayptucker.heroesevolved.formula.SynthesisStationBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Placeable blocks. Helixite is a deep, faintly glowing crystal ore: the
 * mineral the Formula laboratory is built from. See docs/pyrokinesis.md and docs/formula.md.
 */
public final class ModBlocks {
    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HeroesEvolved.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HeroesEvolved.MOD_ID);

    /** Veins form between Y -64 and 0, so most generate in deepslate; the stone variant appears near Y 0. */
    public static final DeferredBlock<DropExperienceBlock> HELIXITE_ORE = BLOCKS.register("helixite_ore",
            () -> new DropExperienceBlock(UniformInt.of(4, 8), BlockBehaviour.Properties.ofFullCopy(Blocks.DIAMOND_ORE)
                    .lightLevel(state -> 3).sound(SoundType.AMETHYST)));

    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_HELIXITE_ORE = BLOCKS.register("deepslate_helixite_ore",
            () -> new DropExperienceBlock(UniformInt.of(4, 8), BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_DIAMOND_ORE)
                    .lightLevel(state -> 3).sound(SoundType.AMETHYST)));

    /** Spins Regeneration blood into the Formula's catalyst. Built with Helixite. */
    public static final DeferredBlock<LabBlock> CENTRIFUGE = BLOCKS.register("centrifuge",
            () -> new LabBlock(BlockBehaviour.Properties.of().strength(3.5F).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL).lightLevel(state -> state.getValue(LabBlock.LIT) ? 5 : 0), true));

    /** A brewing-stand-style bench that turns saline, Helixite and catalyst into the Formula. */
    public static final DeferredBlock<LabBlock> SYNTHESIS_STATION = BLOCKS.register("synthesis_station",
            () -> new LabBlock(BlockBehaviour.Properties.of().strength(2.5F).requiresCorrectToolForDrops()
                    .sound(SoundType.COPPER).lightLevel(state -> state.getValue(LabBlock.LIT) ? 7 : 2), false));

    /** A two-block stasis pod: the bunker's sleepers wake in these, and players can build their own. */
    public static final DeferredBlock<com.jayptucker.heroesevolved.story.bunker.CryoChamberBlock> CRYOCHAMBER = BLOCKS.register("cryochamber",
            () -> new com.jayptucker.heroesevolved.story.bunker.CryoChamberBlock(BlockBehaviour.Properties.of().strength(3F, 6F)
                    .requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().lightLevel(state -> 7)
                    .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CentrifugeBlockEntity>> CENTRIFUGE_ENTITY =
            BLOCK_ENTITIES.register("centrifuge", () -> BlockEntityType.Builder.of(CentrifugeBlockEntity::new, CENTRIFUGE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SynthesisStationBlockEntity>> SYNTHESIS_STATION_ENTITY =
            BLOCK_ENTITIES.register("synthesis_station", () -> BlockEntityType.Builder.of(SynthesisStationBlockEntity::new, SYNTHESIS_STATION.get()).build(null));

    private ModBlocks() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        modEventBus.addListener(ModBlocks::capabilities);
    }

    /** Pipes and other mods see the same sided slots as hoppers. */
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CENTRIFUGE_ENTITY.get(), SidedInvWrapper::new);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SYNTHESIS_STATION_ENTITY.get(), SidedInvWrapper::new);
    }
}
