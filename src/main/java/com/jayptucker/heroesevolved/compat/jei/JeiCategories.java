package com.jayptucker.heroesevolved.compat.jei;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.formula.CentrifugeBlockEntity;
import com.jayptucker.heroesevolved.formula.SynthesisStationBlockEntity;
import com.jayptucker.heroesevolved.hazard.SuitItems;
import com.jayptucker.heroesevolved.hazard.SuitPatterns;
import com.jayptucker.heroesevolved.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The three machines JEI can't read on its own. Each recipe list comes from the machine's own rules
 * ({@link SuitPatterns}, {@link CentrifugeBlockEntity}, {@link SynthesisStationBlockEntity#result}), so JEI can never
 * show a recipe the machine wouldn't make.
 */
final class JeiCategories {
    static final RecipeType<SuitPatterns.Pattern> FABRICATOR = RecipeType.create(HeroesEvolved.MOD_ID, "suit_fabricator", SuitPatterns.Pattern.class);
    static final RecipeType<Spin> CENTRIFUGE = RecipeType.create(HeroesEvolved.MOD_ID, "centrifuge", Spin.class);
    static final RecipeType<Brew> SYNTHESIS = RecipeType.create(HeroesEvolved.MOD_ID, "synthesis", Brew.class);

    private JeiCategories() {}

    /** A Centrifuge spin: a blood sample and a redstone charge make the isolated catalyst. */
    record Spin(ItemStack sample, ItemStack fuel, ItemStack result, int ticks) {
        static List<Spin> all() {
            return List.of(new Spin(new ItemStack(ModItems.BLOOD_SAMPLE.get()), new ItemStack(Items.REDSTONE),
                    new ItemStack(ModItems.ISOLATED_CATALYST.get()), CentrifugeBlockEntity.SPIN_TICKS));
        }
    }

    /** A Synthesis Station brew: up to three bottles and one ingredient, burning glowstone. */
    record Brew(ItemStack bottle, ItemStack ingredient, ItemStack result) {
        static List<Brew> all() {
            ItemStack water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
            List<Brew> brews = new ArrayList<>();
            for (ItemStack[] pair : new ItemStack[][] {
                    {water, new ItemStack(Items.BONE_MEAL)},
                    {new ItemStack(ModItems.BUFFERED_SALINE.get()), new ItemStack(ModItems.HELIXITE_SHARD.get())},
                    {new ItemStack(ModItems.UNSTABLE_COMPOUND.get()), new ItemStack(ModItems.ISOLATED_CATALYST.get())}}) {
                ItemStack result = SynthesisStationBlockEntity.result(pair[0], pair[1]);
                if (!result.isEmpty()) brews.add(new Brew(pair[0], pair[1], result));
            }
            return brews;
        }
    }

    /** What every category shares: its type, title, icon and size. */
    private abstract static class Base<T> implements IRecipeCategory<T> {
        private final RecipeType<T> type;
        private final Component title;
        private final IDrawable icon;
        private final int width, height;

        Base(IGuiHelper gui, RecipeType<T> type, String key, ItemStack icon, int width, int height) {
            this.type = type;
            this.title = Component.translatable("jei.heroesevolved.category." + key);
            this.icon = gui.createDrawableItemStack(icon);
            this.width = width;
            this.height = height;
        }

        @Override public RecipeType<T> getRecipeType() { return type; }
        @Override public Component getTitle() { return title; }
        @Override public IDrawable getIcon() { return icon; }
        @Override public int getWidth() { return width; }
        @Override public int getHeight() { return height; }
    }

    /**
     * The Suit Fabricator: up to six materials in any order (shown in the bench's 3 × 2 grid) and the piece it makes.
     * A tag ingredient (lead ingots from any mod) cycles through every item that counts.
     */
    static final class Fabricator extends Base<SuitPatterns.Pattern> {
        Fabricator(IGuiHelper gui) {
            super(gui, FABRICATOR, "suit_fabricator", new ItemStack(SuitItems.SUIT_FABRICATOR_ITEM.get()), 124, 44);
        }

        @Override public void setRecipe(IRecipeLayoutBuilder builder, SuitPatterns.Pattern pattern, IFocusGroup focuses) {
            List<SuitPatterns.Need> needs = pattern.needs();
            for (int i = 0; i < 6; i++) {
                var slot = builder.addInputSlot(1 + (i % 3) * 18, 4 + (i / 3) * 18).setStandardSlotBackground();
                if (i >= needs.size()) continue;
                SuitPatterns.Need need = needs.get(i);
                slot.addItemStacks(Arrays.stream(need.ingredient().getItems()).map(stack -> stack.copyWithCount(need.count())).toList());
            }
            builder.addOutputSlot(98, 14).setOutputSlotBackground().addItemStack(pattern.make());
            builder.setShapeless(108, 0);
        }

        @Override public void createRecipeExtras(IRecipeExtrasBuilder builder, SuitPatterns.Pattern pattern, IFocusGroup focuses) {
            builder.addRecipeArrowWidget().setPosition(62, 13);
        }

        @Override public ResourceLocation getRegistryName(SuitPatterns.Pattern pattern) {
            return ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "suit_fabricator/" + pattern.id());
        }
    }

    /** The Centrifuge, laid out like a furnace: the sample on top, redstone below, the catalyst out. */
    static final class Centrifuge extends Base<Spin> {
        Centrifuge(IGuiHelper gui) {
            super(gui, CENTRIFUGE, "centrifuge", new ItemStack(ModItems.CENTRIFUGE.get()), 96, 56);
        }

        @Override public void setRecipe(IRecipeLayoutBuilder builder, Spin spin, IFocusGroup focuses) {
            builder.addInputSlot(1, 1).setStandardSlotBackground().addItemStack(spin.sample());
            builder.addInputSlot(1, 37).setStandardSlotBackground().addItemStack(spin.fuel());
            builder.addOutputSlot(64, 19).setOutputSlotBackground().addItemStack(spin.result());
        }

        @Override public void createRecipeExtras(IRecipeExtrasBuilder builder, Spin spin, IFocusGroup focuses) {
            builder.addAnimatedRecipeFlameWidget(spin.ticks()).setPosition(1, 20);
            builder.addAnimatedRecipeArrowWidget(spin.ticks()).setPosition(26, 18);
            builder.addText(List.of(seconds(spin.ticks())), 70, 10).setPosition(24, 40);
        }

        @Override public ResourceLocation getRegistryName(Spin spin) {
            return ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "centrifuge/isolated_catalyst");
        }
    }

    /** The Synthesis Station, laid out like a brewing stand: glowstone and the ingredient above, bottle in, bottle out. */
    static final class Synthesis extends Base<Brew> {
        Synthesis(IGuiHelper gui) {
            super(gui, SYNTHESIS, "synthesis", new ItemStack(ModItems.SYNTHESIS_STATION.get()), 110, 54);
        }

        @Override public void setRecipe(IRecipeLayoutBuilder builder, Brew brew, IFocusGroup focuses) {
            builder.addInputSlot(1, 25).setStandardSlotBackground().addItemStack(brew.bottle());
            builder.addInputSlot(27, 1).setStandardSlotBackground().addItemStack(brew.ingredient());
            builder.addInputSlot(1, 1).setStandardSlotBackground().addItemStack(new ItemStack(Items.GLOWSTONE_DUST));
            builder.addOutputSlot(82, 21).setOutputSlotBackground().addItemStack(brew.result());
        }

        @Override public void createRecipeExtras(IRecipeExtrasBuilder builder, Brew brew, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(SynthesisStationBlockEntity.BREW_TICKS).setPosition(48, 24);
            builder.addText(List.of(seconds(SynthesisStationBlockEntity.BREW_TICKS)), 40, 10).setPosition(48, 44);
        }

        @Override public ResourceLocation getRegistryName(Brew brew) {
            return ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID,
                    "synthesis/" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(brew.result().getItem()).getPath());
        }
    }

    private static Component seconds(int ticks) {
        return Component.translatable("jei.heroesevolved.seconds", ticks / 20).withStyle(ChatFormatting.DARK_GRAY);
    }
}
