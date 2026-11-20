package com.jayptucker.heroesevolved.compat.jei;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.screen.LabScreens;
import com.jayptucker.heroesevolved.client.screen.SuitFabricatorScreen;
import com.jayptucker.heroesevolved.formula.CentrifugeMenu;
import com.jayptucker.heroesevolved.hazard.SuitFabricatorMenu;
import com.jayptucker.heroesevolved.hazard.SuitItems;
import com.jayptucker.heroesevolved.hazard.SuitPatterns;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.registry.ModMenus;
import com.mojang.logging.LogUtils;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import org.slf4j.Logger;

/**
 * Heroes Evolved in JEI. JEI is optional: it finds this class by its annotation, and only this package imports JEI, so
 * the mod loads the same without it (see docs/agents/compat.md).
 *
 * <p>Vanilla-type recipes (crafting, smelting, blasting) show by themselves, and TaCZ's own JEI plugin shows our gun
 * smith table recipes. This adds what JEI can't know: the Suit Fabricator's patterns, the Centrifuge and the Synthesis
 * Station (each drawn from the machine's own rules), info pages for things found rather than crafted, click areas on
 * our machines' arrows, and moving ingredients in with the + button. Items not meant to be had are hidden with the
 * {@code c:hidden_from_recipe_viewers} item tag, which JEI, EMI and REI all read. Every step is guarded, so a change in
 * JEI's API turns one feature off with a log line instead of a crash.
 */
@JeiPlugin
public final class HeroesJeiPlugin implements IModPlugin {
    private static final Logger LOG = LogUtils.getLogger();
    private static boolean broken;

    @Override public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "jei");
    }

    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        guard("categories", () -> {
            var gui = registration.getJeiHelpers().getGuiHelper();
            registration.addRecipeCategories(new JeiCategories.Fabricator(gui), new JeiCategories.Centrifuge(gui),
                    new JeiCategories.Synthesis(gui));
        });
    }

    @Override public void registerRecipes(IRecipeRegistration registration) {
        guard("recipes", () -> {
            registration.addRecipes(JeiCategories.FABRICATOR, SuitPatterns.all());
            registration.addRecipes(JeiCategories.CENTRIFUGE, JeiCategories.Spin.all());
            registration.addRecipes(JeiCategories.SYNTHESIS, JeiCategories.Brew.all());
        });
        guard("info", () -> {
            info(registration, ModItems.HELIXITE_SHARD.get(), "helixite", ModItems.HELIXITE_ORE.get(), ModItems.DEEPSLATE_HELIXITE_ORE.get());
            info(registration, SuitItems.RAW_LEAD.get(), "lead", SuitItems.LEAD_ORE_ITEM.get(), SuitItems.DEEPSLATE_LEAD_ORE_ITEM.get());
            info(registration, ModItems.BLOOD_SAMPLE.get(), "blood_sample");
            info(registration, ModItems.FORMULA_VIAL.get(), "formula_vial");
            info(registration, ModItems.SYLARS_WATCH.get(), "sylars_watch");
            info(registration, ModItems.EMP_GRENADE.get(), "emp_grenade");
            info(registration, SuitItems.SUIT_FABRICATOR_ITEM.get(), "suit_fabricator");
        });
    }

    /** One info page, shared by {@code item} and {@code also}: {@code jei.heroesevolved.info.<key>}. */
    private static void info(IRecipeRegistration registration, ItemLike item, String key, ItemLike... also) {
        Component text = Component.translatable("jei.heroesevolved.info." + key);
        registration.addIngredientInfo(item, text);
        for (ItemLike other : also) registration.addIngredientInfo(other, text);
    }

    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        guard("catalysts", () -> {
            registration.addRecipeCatalyst(SuitItems.SUIT_FABRICATOR_ITEM.get(), JeiCategories.FABRICATOR);
            registration.addRecipeCatalyst(ModItems.CENTRIFUGE.get(), JeiCategories.CENTRIFUGE);
            registration.addRecipeCatalyst(ModItems.SYNTHESIS_STATION.get(), JeiCategories.SYNTHESIS);
        });
    }

    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // Each machine's arrow opens its recipes. Our screens draw inside their own bounds, so JEI's list never covers them.
        guard("click areas", () -> {
            registration.addRecipeClickArea(SuitFabricatorScreen.class, 90, SuitFabricatorMenu.RESULT_Y + 2, 32, 12, JeiCategories.FABRICATOR);
            registration.addRecipeClickArea(LabScreens.CentrifugeScreen.class, 78, 34, 26, 17, JeiCategories.CENTRIFUGE);
            registration.addRecipeClickArea(LabScreens.SynthesisStationScreen.class, 96, 16, 11, 28, JeiCategories.SYNTHESIS);
        });
    }

    @Override public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        guard("transfer", () -> {
            // The fabricator's six inputs, then its result, then the player's 36 slots.
            registration.addRecipeTransferHandler(SuitFabricatorMenu.class, SuitItems.FABRICATOR_MENU.get(), JeiCategories.FABRICATOR,
                    0, SuitFabricatorMenu.INPUTS, SuitFabricatorMenu.INVENTORY, 36);
            // The centrifuge's sample and redstone, its output, then the player's 36 slots.
            registration.addRecipeTransferHandler(CentrifugeMenu.class, ModMenus.CENTRIFUGE.get(), JeiCategories.CENTRIFUGE, 0, 2, 3, 36);
        });
    }

    private static void guard(String step, Runnable run) {
        try {
            run.run();
        } catch (Throwable t) {
            if (!broken) LOG.warn("Heroes Evolved's JEI support skipped {}: {}", step, t.toString());
            broken = true;
        }
    }
}
