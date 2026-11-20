package com.jayptucker.heroesevolved.entity.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.company.CompanyAgentEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * A man in a dark suit with a rifle, or the Suppressor in grey with empty hands. A guard raises the rifle
 * to his shoulder while he aims, so you can see the shot coming. Out cold, he lies on the floor.
 */
public final class CompanyAgentRenderer extends HumanoidMobRenderer<CompanyAgentEntity, PlayerModel<CompanyAgentEntity>> {
    private static final ResourceLocation GUARD = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "textures/entity/company_agent.png");
    private static final ResourceLocation SUPPRESSOR = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "textures/entity/suppressor.png");

    public CompanyAgentRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false) {
            @Override public void setupAnim(CompanyAgentEntity entity, float limbSwing, float limbAmount, float age, float yaw, float pitch) {
                rightArmPose = entity.aiming() ? HumanoidModel.ArmPose.CROSSBOW_HOLD
                        : entity.role() == CompanyAgentEntity.Role.GUARD && !entity.getMainHandItem().isEmpty() ? HumanoidModel.ArmPose.ITEM
                        : HumanoidModel.ArmPose.EMPTY;
                leftArmPose = HumanoidModel.ArmPose.EMPTY;
                super.setupAnim(entity, limbSwing, limbAmount, age, yaw, pitch);
                if (entity.role() == CompanyAgentEntity.Role.SUPPRESSOR && !entity.outCold() && !entity.isAggressive()) {
                    // Hands folded in front of him: he isn't here to fight.
                    rightArm.xRot = -0.55F; rightArm.yRot = -0.45F;
                    leftArm.xRot = -0.55F; leftArm.yRot = 0.45F;
                    rightSleeve.copyFrom(rightArm); leftSleeve.copyFrom(leftArm);
                }
            }
        }, .45F);
        addLayer(new Holster(this, context.getItemInHandRenderer()));
    }

    /**
     * A holstered gun on his right hip, barrel down along the thigh and grip up by his hand, so you can see he's armed
     * before he draws (the Last Day's agents until the fight starts).
     */
    private static final class Holster extends net.minecraft.client.renderer.entity.layers.RenderLayer<CompanyAgentEntity, PlayerModel<CompanyAgentEntity>> {
        private final net.minecraft.client.renderer.ItemInHandRenderer items;

        Holster(net.minecraft.client.renderer.entity.RenderLayerParent<CompanyAgentEntity, PlayerModel<CompanyAgentEntity>> parent,
                net.minecraft.client.renderer.ItemInHandRenderer items) {
            super(parent);
            this.items = items;
        }

        @Override public void render(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, CompanyAgentEntity agent,
                                     float limbSwing, float limbAmount, float partial, float age, float yaw, float pitch) {
            net.minecraft.world.item.ItemStack gun = agent.holstered();
            if (gun.isEmpty() || agent.outCold()) return;
            pose.pushPose();
            getParentModel().body.translateAndRotate(pose);
            // Held as a right hand would hold it with the arm swung back level: the same transform the held-item layer
            // uses, less the arm's own swing, puts the grip at the hand and the barrel straight down. The pivot is set so
            // the grip lands at the right hip, just outside the jacket; it sways a little with the leg's stride.
            float stride = getParentModel().rightLeg.xRot * 0.25F;
            pose.translate(-4.3F / 16F, 9.4F / 16F, -0.66F);
            pose.mulPose(Axis.XP.rotation(stride));
            pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.translate(1F / 16F, 0.125F, -0.625F);
            pose.scale(0.85F, 0.85F, 0.85F);
            items.renderItem(agent, gun, net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, pose, buffers, light);
            pose.popPose();
        }
    }

    @Override public ResourceLocation getTextureLocation(CompanyAgentEntity entity) {
        return entity.role() == CompanyAgentEntity.Role.SUPPRESSOR ? SUPPRESSOR : GUARD;
    }

    @Override protected void setupRotations(CompanyAgentEntity entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
        super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, scale);
        if (entity.outCold()) {
            // On his back on the floor, like a body that fell where it stood.
            poseStack.translate(0, 0.2, 0);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90));
            poseStack.translate(0, -0.9, 0);
        }
    }

    @Override protected boolean shouldShowName(CompanyAgentEntity entity) {
        return !entity.outCold() && super.shouldShowName(entity);
    }
}
