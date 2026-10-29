package com.jayptucker.heroesevolved.registry;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.formula.BloodPurity;
import com.jayptucker.heroesevolved.formula.BloodSampleItem;
import com.jayptucker.heroesevolved.formula.FormulaVialItem;
import com.jayptucker.heroesevolved.formula.PurityItem;
import com.jayptucker.heroesevolved.formula.SyringeItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Items, their data components, the Drained effect and the mod's creative tab. */
public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HeroesEvolved.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HeroesEvolved.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(HeroesEvolved.MOD_ID);
    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, HeroesEvolved.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BloodPurity>> BLOOD_PURITY =
            COMPONENTS.registerComponentType("blood_purity", builder -> builder
                    .persistent(BloodPurity.CODEC).networkSynchronized(BloodPurity.STREAM_CODEC));

    /** After a forced draw: weakened, and a regenerating body cannot regrow until it wears off. */
    public static final DeferredHolder<MobEffect, MobEffect> DRAINED = EFFECTS.register("drained",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x7A1020) {});
    /** Ted Sprague's radiation: a stacking dose that burns from inside. See RadiationSickness. */
    public static final DeferredHolder<MobEffect, MobEffect> RADIATION_SICKNESS = EFFECTS.register("radiation_sickness",
            com.jayptucker.heroesevolved.radiation.RadiationSickness::new);

    public static final DeferredItem<Item> HELIXITE_SHARD = ITEMS.registerSimpleItem("helixite_shard",
            new Item.Properties().rarity(Rarity.UNCOMMON));

    public static final DeferredItem<?> HELIXITE_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.HELIXITE_ORE);
    public static final DeferredItem<?> DEEPSLATE_HELIXITE_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_HELIXITE_ORE);
    public static final DeferredItem<?> CENTRIFUGE = ITEMS.registerSimpleBlockItem(ModBlocks.CENTRIFUGE);
    public static final DeferredItem<?> SYNTHESIS_STATION = ITEMS.registerSimpleBlockItem(ModBlocks.SYNTHESIS_STATION);
    public static final DeferredItem<net.minecraft.world.item.DoubleHighBlockItem> CRYOCHAMBER = ITEMS.register("cryochamber",
            () -> new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.CRYOCHAMBER.get(), new Item.Properties().stacksTo(16)));

    public static final DeferredItem<SyringeItem> EMPTY_SYRINGE = ITEMS.register("empty_syringe",
            () -> new SyringeItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<BloodSampleItem> BLOOD_SAMPLE = ITEMS.register("regenerative_blood_sample",
            () -> new BloodSampleItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<PurityItem> ISOLATED_CATALYST = ITEMS.register("isolated_catalyst",
            () -> new PurityItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> BUFFERED_SALINE = ITEMS.registerSimpleItem("buffered_saline",
            new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> UNSTABLE_COMPOUND = ITEMS.registerSimpleItem("unstable_compound",
            new Item.Properties().stacksTo(16));
    public static final DeferredItem<FormulaVialItem> FORMULA_VIAL = ITEMS.register("formula_vial",
            () -> new FormulaVialItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    /** The mod's emblem: the black sun. Used as the creative tab icon; not obtainable in survival. */
    public static final DeferredItem<Item> ECLIPSE_EMBLEM = ITEMS.registerSimpleItem("eclipse_emblem", new Item.Properties());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.heroesevolved"))
                    .icon(() -> new ItemStack(ECLIPSE_EMBLEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(HELIXITE_SHARD.get());
                        output.accept(HELIXITE_ORE.get());
                        output.accept(DEEPSLATE_HELIXITE_ORE.get());
                        output.accept(CENTRIFUGE.get());
                        output.accept(SYNTHESIS_STATION.get());
                        output.accept(CRYOCHAMBER.get());
                        output.accept(EMPTY_SYRINGE.get());
                        output.accept(BUFFERED_SALINE.get());
                        output.accept(UNSTABLE_COMPOUND.get());
                        for (boolean donated : new boolean[] {true, false}) {
                            BloodPurity purity = new BloodPurity(donated, 3, "Creative");
                            for (Item item : new Item[] {BLOOD_SAMPLE.get(), ISOLATED_CATALYST.get(), FORMULA_VIAL.get()}) {
                                ItemStack stack = new ItemStack(item);
                                stack.set(BLOOD_PURITY.get(), purity);
                                output.accept(stack);
                            }
                        }
                    })
                    .build());

    private ModItems() {}

    public static void register(IEventBus modEventBus) {
        ModBlocks.register(modEventBus);
        COMPONENTS.register(modEventBus);
        EFFECTS.register(modEventBus);
        ITEMS.register(modEventBus);
        TABS.register(modEventBus);
        ModMenus.register(modEventBus);
    }
}
