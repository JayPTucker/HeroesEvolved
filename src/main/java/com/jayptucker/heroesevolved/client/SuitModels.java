package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.hazard.SuitArmorItem;
import com.jayptucker.heroesevolved.hazard.SuitItems;
import com.jayptucker.heroesevolved.hazard.Suits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * The suits' own armor models: skin-tight, with real 3D detail standing off the body.
 * <ul>
 *   <li><b>Thin.</b> Vanilla armor stands a whole pixel off the body (1.0 for the helmet, chestplate and boots, 0.5 for
 *   leggings), right for plate armor and far too bulky for a suit. These inflate each box only just past the player's
 *   own outer skin layer, so no skin pixel pokes through: the hood or cowl past the hat (0.5), the rest past the jacket,
 *   sleeves and pants (0.25), the outer pieces just over the leggings.</li>
 *   <li><b>3D.</b> Extra boxes are children of the body parts, so they move and hide with them (the armor layer shows
 *   the head for the helmet, the body and arms for the chestplate, the legs for the boots). Their texture lives in the
 *   64x32 armor texture's hat area (x 32-64, y 0-16), which a suit doesn't otherwise use; the hat box itself is removed.
 *   The velocity suit has swept fins on the cowl, a raised chest plate, a raised belt and forearm bands; the radiation
 *   suit a respirator canister, an air tank and thick boot soles. The UV of each is in {@link Velocity} and
 *   {@link Radiation}, shared with {@code tools/generate-suit-textures.py}.</li>
 * </ul>
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SuitModels {
    /** Inflation, in model pixels, of the hood or cowl, the suits' outer pieces, and their leggings. */
    static final float HEAD = 0.55F, OUTER = 0.42F, INNER = 0.38F;

    /** Where the velocity suit's extra boxes take their texture (u, v), and their sizes. */
    static final class Velocity {
        static final int BAND_U = 32, BAND_V = 4, FIN_U = 32, FIN_V = 10, BELT_U = 40, BELT_V = 10, PLATE_U = 50, PLATE_V = 0;
    }

    /** Where the radiation suit's extra boxes take their texture. */
    static final class Radiation {
        static final int CANISTER_U = 32, CANISTER_V = 0, TANK_U = 42, TANK_V = 0, SOLE_U = 32, SOLE_V = 10;
    }

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "suit"), name);
    }

    static final ModelLayerLocation VELOCITY_LAYER = layer("velocity"), RADIATION_LAYER = layer("radiation"), INNER_LAYER = layer("inner");

    private static HumanoidArmorModel<LivingEntity> velocity, radiation, inner;

    private SuitModels() {}

    /** The vanilla armor mesh at {@code deformation}, its head at {@code head}, and no hat box. */
    private static MeshDefinition mesh(float deformation, float head) {
        MeshDefinition mesh = HumanoidArmorModel.createBodyLayer(new CubeDeformation(deformation));
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4, -8, -4, 8, 8, 8, new CubeDeformation(head)), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return mesh;
    }

    /** Red and white, made for speed: swept fins, a raised chest plate, a belt and forearm bands. */
    static LayerDefinition velocityLayer() {
        MeshDefinition mesh = mesh(OUTER, HEAD);
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.getChild("head"), body = root.getChild("body");
        // Fins swept back from the temples: a low box and a higher one further back, each side.
        for (int side : new int[] {-1, 1}) {
            float x = side < 0 ? -5.35F : 4.35F;
            CubeListBuilder fin = CubeListBuilder.create().texOffs(Velocity.FIN_U, Velocity.FIN_V);
            if (side > 0) fin.mirror();
            fin.addBox(x, -5.4F, -1.8F, 1, 2, 2).addBox(x, -6.9F, -0.4F, 1, 2, 3);
            head.addOrReplaceChild(side < 0 ? "fin_right" : "fin_left", fin, PartPose.ZERO);
        }
        body.addOrReplaceChild("plate", CubeListBuilder.create().texOffs(Velocity.PLATE_U, Velocity.PLATE_V)
                .addBox(-3, 1.5F, -2.95F, 6, 5, 1), PartPose.ZERO);
        body.addOrReplaceChild("belt", CubeListBuilder.create().texOffs(Velocity.BELT_U, Velocity.BELT_V)
                .addBox(-4, 10, -2, 8, 2, 4, new CubeDeformation(0.5F)), PartPose.ZERO);
        root.getChild("right_arm").addOrReplaceChild("band", CubeListBuilder.create().texOffs(Velocity.BAND_U, Velocity.BAND_V)
                .addBox(-3, 7, -2, 4, 1, 4, new CubeDeformation(0.5F)), PartPose.ZERO);
        root.getChild("left_arm").addOrReplaceChild("band", CubeListBuilder.create().texOffs(Velocity.BAND_U, Velocity.BAND_V).mirror()
                .addBox(-1, 7, -2, 4, 1, 4, new CubeDeformation(0.5F)), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** A hazmat suit: a respirator canister at the mouth, an air tank on the back, thick soles. */
    static LayerDefinition radiationLayer() {
        MeshDefinition mesh = mesh(OUTER, HEAD);
        PartDefinition root = mesh.getRoot();
        root.getChild("head").addOrReplaceChild("canister", CubeListBuilder.create().texOffs(Radiation.CANISTER_U, Radiation.CANISTER_V)
                .addBox(-1.5F, -2.6F, -6.3F, 3, 3, 2), PartPose.ZERO);
        root.getChild("body").addOrReplaceChild("tank", CubeListBuilder.create().texOffs(Radiation.TANK_U, Radiation.TANK_V)
                .addBox(-2, 1, 2.45F, 4, 7, 3), PartPose.ZERO);
        for (String leg : new String[] {"right_leg", "left_leg"})
            root.getChild(leg).addOrReplaceChild("sole", CubeListBuilder.create().texOffs(Radiation.SOLE_U, Radiation.SOLE_V)
                    .addBox(-2.5F, 11.5F, -3, 5, 1, 5), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(VELOCITY_LAYER, SuitModels::velocityLayer);
        event.registerLayerDefinition(RADIATION_LAYER, SuitModels::radiationLayer);
        event.registerLayerDefinition(INNER_LAYER, () -> LayerDefinition.create(mesh(INNER, INNER), 64, 32));
    }

    @SubscribeEvent
    public static void reload(EntityRenderersEvent.AddLayers event) {
        velocity = radiation = inner = null;
    }

    private static HumanoidArmorModel<LivingEntity> model(Suits.Kind kind, boolean legs) {
        var models = Minecraft.getInstance().getEntityModels();
        if (legs) return inner != null ? inner : (inner = new HumanoidArmorModel<>(models.bakeLayer(INNER_LAYER)));
        if (kind == Suits.Kind.VELOCITY)
            return velocity != null ? velocity : (velocity = new HumanoidArmorModel<>(models.bakeLayer(VELOCITY_LAYER)));
        return radiation != null ? radiation : (radiation = new HumanoidArmorModel<>(models.bakeLayer(RADIATION_LAYER)));
    }

    @SubscribeEvent
    public static void extensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                Suits.Kind kind = stack.getItem() instanceof SuitArmorItem suit ? suit.kind() : Suits.Kind.RADIATION;
                return model(kind, slot == EquipmentSlot.LEGS);
            }
        }, SuitItems.RADIATION_HOOD.get(), SuitItems.RADIATION_COAT.get(), SuitItems.RADIATION_TROUSERS.get(), SuitItems.RADIATION_BOOTS.get(),
                SuitItems.VELOCITY_COWL.get(), SuitItems.VELOCITY_SUIT.get(), SuitItems.VELOCITY_LEGGINGS.get(), SuitItems.VELOCITY_BOOTS.get());
    }
}
