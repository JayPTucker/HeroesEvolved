package com.jayptucker.heroesevolved.hazard;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.List;

/**
 * One piece of a protective suit. The radiation suit's lead lining is heavy: each piece slows its wearer by
 * {@link #LEAD_DRAG}. The velocity suit weighs nothing worth mentioning.
 */
public class SuitArmorItem extends ArmorItem {
    /** Movement lost to each piece of lead-lined radiation suit. */
    public static final double LEAD_DRAG = 0.03;

    private final Suits.Kind kind;
    private ItemAttributeModifiers modifiers;

    public SuitArmorItem(Holder<ArmorMaterial> material, Type type, Suits.Kind kind, Properties properties) {
        super(material, type, properties);
        this.kind = kind;
    }

    public Suits.Kind kind() { return kind; }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        if (modifiers == null) {
            ItemAttributeModifiers base = super.getDefaultAttributeModifiers();
            modifiers = kind != Suits.Kind.RADIATION ? base : base.withModifierAdded(Attributes.MOVEMENT_SPEED,
                    new AttributeModifier(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "lead_lining_" + getType().getName()),
                            -LEAD_DRAG, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                    EquipmentSlotGroup.bySlot(getEquipmentSlot()));
        }
        return modifiers;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.heroesevolved.suit.radiation").withStyle(ChatFormatting.GREEN));
        if (kind == Suits.Kind.VELOCITY) tooltip.add(Component.translatable("item.heroesevolved.suit.friction").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.heroesevolved.suit.set").withStyle(ChatFormatting.GRAY));
    }
}
