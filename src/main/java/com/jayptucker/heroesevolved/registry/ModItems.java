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

    /** Forge Energy kept in a broken Ability Dampener's item, so picking it up loses nothing. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> STORED_ENERGY =
            COMPONENTS.registerComponentType("stored_energy", builder -> builder
                    .persistent(com.mojang.serialization.Codec.INT).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    /** A dampener round's hold: no power answers until it wears off. See dampener/Dampening. */
    public static final DeferredHolder<MobEffect, MobEffect> DAMPENED = EFFECTS.register("dampened",
            com.jayptucker.heroesevolved.dampener.Dampening.Effect::new);

    /** Drenched by a hydrokinetic: no fire, more from lightning, less from burns. See hydro/Soaked. */
    public static final DeferredHolder<MobEffect, MobEffect> SOAKED = EFFECTS.register("soaked",
            com.jayptucker.heroesevolved.hydro.Soaked::new);

    /** Amplified by red lightning (amplify/Amplification): stronger, cheaper, quicker powers. Level 0 a Surge, 1 an Overcharge. */
    public static final DeferredHolder<MobEffect, MobEffect> SUPERCHARGED = EFFECTS.register("supercharged",
            com.jayptucker.heroesevolved.amplify.Amplification.Effect::new);

    /** After a forced draw: weakened, and a regenerating body cannot regrow until it wears off. */
    public static final DeferredHolder<MobEffect, MobEffect> DRAINED = EFFECTS.register("drained",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x7A1020) {});
    /** Induced Radioactivity's radiation: a stacking dose that burns from inside. See RadiationSickness. */
    public static final DeferredHolder<MobEffect, MobEffect> RADIATION_SICKNESS = EFFECTS.register("radiation_sickness",
            com.jayptucker.heroesevolved.radiation.RadiationSickness::new);
    /** A Company tranquilizer dose; three put a player under. See Tranquilizer. */
    public static final DeferredHolder<MobEffect, MobEffect> TRANQUILIZED = EFFECTS.register("tranquilized",
            com.jayptucker.heroesevolved.company.Tranquilizer::new);

    public static final DeferredItem<Item> HELIXITE_SHARD = ITEMS.registerSimpleItem("helixite_shard",
            new Item.Properties().rarity(Rarity.UNCOMMON));

    public static final DeferredItem<?> HELIXITE_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.HELIXITE_ORE);
    public static final DeferredItem<?> DEEPSLATE_HELIXITE_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_HELIXITE_ORE);
    public static final DeferredItem<?> CENTRIFUGE = ITEMS.registerSimpleBlockItem(ModBlocks.CENTRIFUGE);
    public static final DeferredItem<?> SYNTHESIS_STATION = ITEMS.registerSimpleBlockItem(ModBlocks.SYNTHESIS_STATION);
    public static final DeferredItem<net.minecraft.world.item.DoubleHighBlockItem> CRYOCHAMBER = ITEMS.register("cryochamber",
            () -> new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.CRYOCHAMBER.get(), new Item.Properties().stacksTo(16)));
    public static final DeferredItem<?> ALARM_BEACON = ITEMS.registerSimpleBlockItem(ModBlocks.ALARM_BEACON);
    public static final DeferredItem<?> ABILITY_DAMPENER = ITEMS.registerSimpleBlockItem(ModBlocks.ABILITY_DAMPENER);

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

    /** What Sylar leaves when he falls: once a life, five seconds back. */
    public static final DeferredItem<com.jayptucker.heroesevolved.story.sylar.SylarsWatchItem> SYLARS_WATCH = ITEMS.register("sylars_watch",
            () -> new com.jayptucker.heroesevolved.story.sylar.SylarsWatchItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    /** Noah Bennet's horn-rimmed glasses, worn on the head and drawn on the face (story/actor and the Last Day). */
    public static final DeferredItem<com.jayptucker.heroesevolved.item.HornRimmedGlassesItem> HORN_RIMMED_GLASSES = ITEMS.register("horn_rimmed_glasses",
            () -> new com.jayptucker.heroesevolved.item.HornRimmedGlassesItem(new Item.Properties().stacksTo(1)));

    /**
     * The Company's stun gun as Noah Bennet and his guards carry it in a scene (the Last Day's tasing): a prop with its own
     * boxy yellow model, not a working weapon. The usable taser is the TaCZ Company Taser (weapons/PowerWeapons).
     */
    public static final DeferredItem<Item> STUN_GUN = ITEMS.register("stun_gun", () -> new Item(new Item.Properties().stacksTo(1)));

    /** Gray &amp; Sons' repair ticket: "Ready for collection." Read it to be taken into Sylar's Collection (story/sylar). */
    public static final DeferredItem<com.jayptucker.heroesevolved.story.sylar.RepairTicketItem> REPAIR_TICKET = ITEMS.register("repair_ticket",
            () -> new com.jayptucker.heroesevolved.story.sylar.RepairTicketItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));

    /** The record of Sylar's theme: a rare drop when he is beaten again (story/sylar). */
    public static final DeferredItem<Item> MUSIC_DISC_THE_COLLECTION = ITEMS.register("music_disc_the_collection",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).jukeboxPlayable(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.JUKEBOX_SONG,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.jayptucker.heroesevolved.HeroesEvolved.MOD_ID, "the_collection")))));

    /** What every fight with Sylar leaves: the brass works of a watch, for crafting another Repair Ticket. */
    public static final DeferredItem<Item> BRASS_MOVEMENT = ITEMS.register("brass_movement",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    /** A TaCZ-crafted EMP grenade (weapons/EmpGrenade): thrown, it kills redstone, machines and Create shafts nearby. */
    public static final DeferredItem<com.jayptucker.heroesevolved.weapons.EmpGrenade.GrenadeItem> EMP_GRENADE = ITEMS.register("emp_grenade",
            () -> new com.jayptucker.heroesevolved.weapons.EmpGrenade.GrenadeItem(new Item.Properties().stacksTo(8)));

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
                        output.accept(ALARM_BEACON.get());
                        output.accept(EMPTY_SYRINGE.get());
                        output.accept(BUFFERED_SALINE.get());
                        output.accept(UNSTABLE_COMPOUND.get());
                        output.accept(SYLARS_WATCH.get());
                        output.accept(REPAIR_TICKET.get());
                        output.accept(BRASS_MOVEMENT.get());
                        output.accept(MUSIC_DISC_THE_COLLECTION.get());
                        output.accept(HORN_RIMMED_GLASSES.get());
                        output.accept(EMP_GRENADE.get());
                        output.accept(ABILITY_DAMPENER.get());
                        com.jayptucker.heroesevolved.hazard.SuitItems.tab(output);
                        ItemStack fullDampener = new ItemStack(ABILITY_DAMPENER.get());
                        fullDampener.set(STORED_ENERGY.get(), com.jayptucker.heroesevolved.dampener.DampenerGeometry.CAPACITY);
                        output.accept(fullDampener);
                        ItemStack nullifier = com.jayptucker.heroesevolved.dampener.Nullifier.charged();
                        if (!nullifier.isEmpty()) output.accept(nullifier);
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
        com.jayptucker.heroesevolved.hazard.SuitItems.register(modEventBus);
        TABS.register(modEventBus);
        ModMenus.register(modEventBus);
    }
}
