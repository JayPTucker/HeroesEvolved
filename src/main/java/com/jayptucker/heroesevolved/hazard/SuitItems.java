package com.jayptucker.heroesevolved.hazard;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Lead, the suits' other materials, the Suit Fabricator and the suits themselves. Real radiation suits are lead-lined
 * with a leaded-glass visor and a charcoal respirator; real heat suits are aramid (Kevlar and Nomex) over ceramic
 * tiles like a spacecraft's heat shield. Lead is new: galena veins in the overworld's stone, as common as copper's
 * deeper veins, and it is tagged {@code c:ingots/lead} so other mods' lead works too.
 */
public final class SuitItems {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HeroesEvolved.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HeroesEvolved.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, HeroesEvolved.MOD_ID);

    /** The velocity suit's colour until it is dyed: red, under undyed white trim. */
    public static final int VELOCITY_RED = 0xB4231C;

    public static final DeferredBlock<DropExperienceBlock> LEAD_ORE = BLOCKS.register("lead_ore",
            () -> new DropExperienceBlock(ConstantInt.of(0), BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE)));
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_LEAD_ORE = BLOCKS.register("deepslate_lead_ore",
            () -> new DropExperienceBlock(ConstantInt.of(0), BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_IRON_ORE)));
    /** Glass with lead in it: a pale amber tint, and the visor of the radiation suit. A building block too. */
    public static final DeferredBlock<TransparentBlock> LEADED_GLASS = BLOCKS.register("leaded_glass",
            () -> new TransparentBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).strength(0.6F, 3F)));
    public static final DeferredBlock<SuitFabricatorBlock> SUIT_FABRICATOR = BLOCKS.register("suit_fabricator",
            () -> new SuitFabricatorBlock(BlockBehaviour.Properties.of().strength(3F).requiresCorrectToolForDrops().sound(SoundType.METAL)));

    public static final DeferredItem<BlockItem> LEAD_ORE_ITEM = ITEMS.registerSimpleBlockItem(LEAD_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_LEAD_ORE_ITEM = ITEMS.registerSimpleBlockItem(DEEPSLATE_LEAD_ORE);
    public static final DeferredItem<BlockItem> LEADED_GLASS_ITEM = ITEMS.registerSimpleBlockItem(LEADED_GLASS);
    public static final DeferredItem<BlockItem> SUIT_FABRICATOR_ITEM = ITEMS.registerSimpleBlockItem(SUIT_FABRICATOR);

    public static final DeferredItem<Item> RAW_LEAD = ITEMS.registerSimpleItem("raw_lead", new Item.Properties());
    public static final DeferredItem<Item> LEAD_INGOT = ITEMS.registerSimpleItem("lead_ingot", new Item.Properties());
    public static final DeferredItem<Item> ARAMID_FIBER = ITEMS.registerSimpleItem("aramid_fiber", new Item.Properties());
    public static final DeferredItem<Item> UNFIRED_CERAMIC_PLATE = ITEMS.registerSimpleItem("unfired_ceramic_plate", new Item.Properties());
    public static final DeferredItem<Item> CERAMIC_PLATE = ITEMS.registerSimpleItem("ceramic_plate", new Item.Properties());
    public static final DeferredItem<Item> RESPIRATOR_FILTER = ITEMS.registerSimpleItem("respirator_filter", new Item.Properties().stacksTo(16));

    public static final DeferredItem<SuitArmorItem> RADIATION_HOOD = radiation("radiation_hood", ArmorItem.Type.HELMET);
    public static final DeferredItem<SuitArmorItem> RADIATION_COAT = radiation("radiation_coat", ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<SuitArmorItem> RADIATION_TROUSERS = radiation("radiation_trousers", ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<SuitArmorItem> RADIATION_BOOTS = radiation("radiation_boots", ArmorItem.Type.BOOTS);
    public static final DeferredItem<SuitArmorItem> VELOCITY_COWL = velocity("velocity_cowl", ArmorItem.Type.HELMET);
    public static final DeferredItem<SuitArmorItem> VELOCITY_SUIT = velocity("velocity_suit", ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<SuitArmorItem> VELOCITY_LEGGINGS = velocity("velocity_leggings", ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<SuitArmorItem> VELOCITY_BOOTS = velocity("velocity_boots", ArmorItem.Type.BOOTS);

    public static final DeferredHolder<MenuType<?>, MenuType<SuitFabricatorMenu>> FABRICATOR_MENU =
            MENUS.register("suit_fabricator", () -> new MenuType<>(SuitFabricatorMenu::new, FeatureFlags.VANILLA_SET));

    private static DeferredItem<SuitArmorItem> radiation(String name, ArmorItem.Type type) {
        return ITEMS.register(name, () -> new SuitArmorItem(SuitMaterials.RADIATION, type, Suits.Kind.RADIATION,
                new Item.Properties().durability(type.getDurability(18))));
    }

    private static DeferredItem<SuitArmorItem> velocity(String name, ArmorItem.Type type) {
        return ITEMS.register(name, () -> new SuitArmorItem(SuitMaterials.VELOCITY, type, Suits.Kind.VELOCITY,
                new Item.Properties().durability(type.getDurability(25)).component(DataComponents.DYED_COLOR, new DyedItemColor(VELOCITY_RED, false))));
    }

    private SuitItems() {}

    public static void register(IEventBus bus) {
        SuitMaterials.register(bus);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        MENUS.register(bus);
    }

    /** Everything here, in the order the creative tab shows it. */
    public static void tab(CreativeModeTab.Output output) {
        for (DeferredItem<?> item : java.util.List.of(LEAD_ORE_ITEM, DEEPSLATE_LEAD_ORE_ITEM, RAW_LEAD, LEAD_INGOT, LEADED_GLASS_ITEM,
                ARAMID_FIBER, UNFIRED_CERAMIC_PLATE, CERAMIC_PLATE, RESPIRATOR_FILTER, SUIT_FABRICATOR_ITEM,
                RADIATION_HOOD, RADIATION_COAT, RADIATION_TROUSERS, RADIATION_BOOTS,
                VELOCITY_COWL, VELOCITY_SUIT, VELOCITY_LEGGINGS, VELOCITY_BOOTS))
            output.accept(item.get());
    }
}
