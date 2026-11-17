package com.jayptucker.heroesevolved.hazard;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;
import java.util.function.Supplier;

/**
 * What the Suit Fabricator makes and from what. Each pattern takes its materials from the bench's six input slots in any
 * order; anything else left in them stays.
 * <ul>
 *   <li><b>Radiation suit:</b> a leather garment lined with lead and sealed with slime (standing in for rubber). The
 *   hood adds a leaded-glass visor and a respirator filter.</li>
 *   <li><b>Velocity suit:</b> aramid weave over fired ceramic plates, with a lead lining. The cowl's lenses are leaded
 *   glass.</li>
 * </ul>
 */
public final class SuitPatterns {
    /** One material: what counts, how many, and what to draw for it. */
    public record Need(Ingredient ingredient, int count, Supplier<ItemStack> icon) {}

    public record Pattern(String id, Supplier<? extends Item> result, List<Need> needs) {
        public ItemStack make() { return new ItemStack(result.get()); }

        /** Are all its materials in {@code input}? */
        public boolean matches(Container input) {
            for (Need need : needs) if (count(input, need.ingredient) < need.count) return false;
            return true;
        }

        /** Takes its materials out of {@code input}. */
        public void consume(Container input) {
            for (Need need : needs) {
                int left = need.count;
                for (int i = 0; i < input.getContainerSize() && left > 0; i++) {
                    ItemStack stack = input.getItem(i);
                    if (stack.isEmpty() || !need.ingredient.test(stack)) continue;
                    int take = Math.min(left, stack.getCount());
                    input.removeItem(i, take);
                    left -= take;
                }
            }
        }
    }

    private static int count(Container input, Ingredient ingredient) {
        int n = 0;
        for (int i = 0; i < input.getContainerSize(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty() && ingredient.test(stack)) n += stack.getCount();
        }
        return n;
    }

    private static Need lead(int n) { return new Need(Ingredient.of(SuitMaterials.LEAD_INGOTS), n, () -> new ItemStack(SuitItems.LEAD_INGOT.get())); }
    private static Need of(Item item, int n) { return new Need(Ingredient.of(item), n, () -> new ItemStack(item)); }
    private static Need of(Supplier<? extends Item> item, int n) {
        return new Need(Ingredient.of(item.get()), n, () -> new ItemStack(item.get()));
    }

    private static List<Pattern> all;

    /** The eight patterns, radiation suit first, head to foot. */
    public static List<Pattern> all() {
        if (all == null) all = List.of(
                new Pattern("radiation_hood", SuitItems.RADIATION_HOOD, List.of(of(Items.LEATHER_HELMET, 1), lead(2),
                        of(SuitItems.LEADED_GLASS_ITEM, 1), of(SuitItems.RESPIRATOR_FILTER, 1), of(Items.SLIME_BALL, 1))),
                new Pattern("radiation_coat", SuitItems.RADIATION_COAT, List.of(of(Items.LEATHER_CHESTPLATE, 1), lead(4), of(Items.SLIME_BALL, 2))),
                new Pattern("radiation_trousers", SuitItems.RADIATION_TROUSERS, List.of(of(Items.LEATHER_LEGGINGS, 1), lead(3), of(Items.SLIME_BALL, 1))),
                new Pattern("radiation_boots", SuitItems.RADIATION_BOOTS, List.of(of(Items.LEATHER_BOOTS, 1), lead(2), of(Items.SLIME_BALL, 1))),
                new Pattern("velocity_cowl", SuitItems.VELOCITY_COWL, List.of(of(SuitItems.ARAMID_FIBER, 3), of(SuitItems.LEADED_GLASS_ITEM, 1), lead(1))),
                new Pattern("velocity_suit", SuitItems.VELOCITY_SUIT, List.of(of(SuitItems.ARAMID_FIBER, 6), of(SuitItems.CERAMIC_PLATE, 2), lead(2))),
                new Pattern("velocity_leggings", SuitItems.VELOCITY_LEGGINGS, List.of(of(SuitItems.ARAMID_FIBER, 5), of(SuitItems.CERAMIC_PLATE, 2), lead(1))),
                new Pattern("velocity_boots", SuitItems.VELOCITY_BOOTS, List.of(of(SuitItems.ARAMID_FIBER, 2), of(SuitItems.CERAMIC_PLATE, 2),
                        lead(1), of(Items.SLIME_BALL, 1))));
        return all;
    }

    private SuitPatterns() {}
}
