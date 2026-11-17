package com.jayptucker.heroesevolved.hazard;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

/**
 * The two suits' armor materials. Both protect about as well as leather against blows: they are made to keep
 * radiation and heat off, not swords. The radiation suit's lead also steadies its wearer against a shock wave
 * (knockback resistance). The velocity suit is dyeable, with its ceramic plates drawn on an undyed overlay.
 */
public final class SuitMaterials {
    private static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, HeroesEvolved.MOD_ID);

    public static final TagKey<Item> LEAD_INGOTS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ingots/lead"));

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> RADIATION = MATERIALS.register("radiation_suit", () -> new ArmorMaterial(
            Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                m.put(ArmorItem.Type.BOOTS, 1); m.put(ArmorItem.Type.LEGGINGS, 2);
                m.put(ArmorItem.Type.CHESTPLATE, 3); m.put(ArmorItem.Type.HELMET, 1); m.put(ArmorItem.Type.BODY, 3);
            }), 9, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(LEAD_INGOTS),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "radiation_suit"))),
            0, 0.05F));

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> VELOCITY = MATERIALS.register("velocity_suit", () -> new ArmorMaterial(
            Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                m.put(ArmorItem.Type.BOOTS, 1); m.put(ArmorItem.Type.LEGGINGS, 3);
                m.put(ArmorItem.Type.CHESTPLATE, 4); m.put(ArmorItem.Type.HELMET, 1); m.put(ArmorItem.Type.BODY, 4);
            }), 12, SoundEvents.ARMOR_EQUIP_CHAIN, () -> Ingredient.of(SuitItems.ARAMID_FIBER.get()),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "velocity_suit"), "", true),
                    new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "velocity_suit"), "_overlay", false)),
            0, 0));

    private SuitMaterials() {}

    public static void register(IEventBus bus) { MATERIALS.register(bus); }
}
