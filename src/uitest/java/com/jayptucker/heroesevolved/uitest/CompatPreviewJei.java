package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.hazard.SuitItems;
import com.jayptucker.heroesevolved.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.List;

/**
 * The JEI half of {@link CompatPreview}: holds JEI's runtime and drives it. Only touched when JEI is loaded, so the
 * preview also runs without it.
 */
@JeiPlugin
public final class CompatPreviewJei implements IModPlugin {
    private static IJeiRuntime runtime;

    @Override public ResourceLocation getPluginUid() { return ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "preview_jei"); }

    @Override public void onRuntimeAvailable(IJeiRuntime jei) { runtime = jei; }

    static boolean ready() { return runtime != null; }

    /** Filters the list to our mod and reports what shows and what must not. */
    static void filterOurs() {
        runtime.getIngredientFilter().setFilterText("@heroesevolved");
    }

    static void checkList() {
        List<ItemStack> shown = runtime.getIngredientFilter().getFilteredItemStacks();
        boolean emblem = shown.stream().anyMatch(s -> s.is(ModItems.ECLIPSE_EMBLEM.get()));
        boolean fabricator = shown.stream().anyMatch(s -> s.is(SuitItems.SUIT_FABRICATOR_ITEM.get()));
        long ours = shown.stream().filter(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().equals(HeroesEvolved.MOD_ID)).count();
        CompatPreview.check("JEI list hides the Eclipse Emblem", !emblem);
        CompatPreview.check("JEI list shows the Suit Fabricator", fabricator);
        System.out.println("COMPAT_JEI_LIST ours=" + ours + " total=" + shown.size());
        for (String type : List.of("suit_fabricator", "centrifuge", "synthesis")) {
            var found = runtime.getRecipeManager().getRecipeType(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, type));
            long recipes = found.map(t -> runtime.getRecipeManager().createRecipeLookup(t).get().count()).orElse(0L);
            CompatPreview.check("JEI " + type + " recipes (" + recipes + ")", recipes > 0);
        }
        // TaCZ's own JEI plugin owns the workbench categories (one per table); find ours through the grenade.
        var tacz = taczCategory();
        System.out.println("COMPAT_JEI_GUNSMITH category=" + (tacz == null ? null : tacz.getRecipeType().getUid()));
        CompatPreview.check("JEI shows the EMP grenade at TaCZ's workbench", tacz != null);
    }

    private static mezz.jei.api.recipe.category.IRecipeCategory<?> taczCategory() {
        return runtime.getRecipeManager().createRecipeCategoryLookup().limitFocus(List.of(grenade())).get()
                .filter(c -> c.getRecipeType().getUid().getNamespace().equals("tacz")).findFirst().orElse(null);
    }

    private static mezz.jei.api.recipe.IFocus<ItemStack> grenade() {
        return runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK,
                new ItemStack(ModItems.EMP_GRENADE.get()));
    }

    /** Opens TaCZ's workbench tab on the EMP grenade's recipe. */
    static void showGunSmith() {
        var category = taczCategory();
        if (category != null) showIn(category);
    }

    private static <T> void showIn(mezz.jei.api.recipe.category.IRecipeCategory<T> category) {
        List<T> recipes = runtime.getRecipeManager().createRecipeLookup(category.getRecipeType()).limitFocus(List.of(grenade())).get().toList();
        runtime.getRecipesGui().showRecipes(category, recipes, List.of(grenade()));
    }

    static void showType(String path) {
        runtime.getRecipeManager().getRecipeType(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, path))
                .ifPresent(type -> runtime.getRecipesGui().showTypes(List.of(type)));
    }

    static void showRecipesFor(ItemLike item) {
        runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT,
                VanillaTypes.ITEM_STACK, new ItemStack(item)));
    }

    static void showUsesOf(ItemLike item) {
        runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.INPUT,
                VanillaTypes.ITEM_STACK, new ItemStack(item)));
    }

    static void clearFilter() { runtime.getIngredientFilter().setFilterText(""); }
}
