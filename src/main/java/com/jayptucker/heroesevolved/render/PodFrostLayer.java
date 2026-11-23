package com.jayptucker.heroesevolved.render;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Rime over a body sealed in a frosted pod. Nothing extra is synced: a villager with no AI standing in a pod whose
 * {@code condition} is frosted is one of them, so the Last Day's sleepers (in pods not yet frosted) stay clean.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PodFrostLayer extends RenderLayer<Villager, VillagerModel<Villager>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "textures/entity/pod_frost.png");

    private PodFrostLayer(LivingEntityRenderer<Villager, VillagerModel<Villager>> renderer) { super(renderer); }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.AddLayers event) {
        LivingEntityRenderer<Villager, VillagerModel<Villager>> renderer = event.getRenderer(EntityType.VILLAGER);
        if (renderer != null) renderer.addLayer(new PodFrostLayer(renderer));
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, Villager villager,
                       float swing, float amount, float partial, float age, float yaw, float pitch) {
        if (!villager.isNoAi() || villager.isInvisible() || !CryoChamberBlock.frosted(villager.level(), villager.blockPosition())) return;
        // The hat and its brim are see-through on a villager with no hat. Frost painted on them is a plate round the head.
        // Only the hat part: the model's own hatVisible hides the whole head with it.
        VillagerModel<Villager> model = getParentModel();
        var hat = model.getHead().getChild("hat");
        boolean shown = hat.visible;
        hat.visible = false;
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        hat.visible = shown;
    }
}
