package com.jayptucker.heroesevolved.entity.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.story.sylar.SylarEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

/** Human silhouette, dark coat, watchmaker's hands. No floating name to announce a distant sighting. */
public final class SylarRenderer extends HumanoidMobRenderer<SylarEntity, PlayerModel<SylarEntity>> {
    private static final ResourceLocation SKIN = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "textures/entity/sylar.png");
    public SylarRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false) {
            @Override public void setupAnim(SylarEntity entity, float limbSwing, float limbAmount, float age, float yaw, float pitch) {
                super.setupAnim(entity, limbSwing, limbAmount, age, yaw, pitch);
                if (entity.extracting()) { rightArm.xRot = -1.4F; rightArm.yRot = -.15F; rightSleeve.copyFrom(rightArm); return; }
                // How he holds himself while winding a power up, so you can read what is coming.
                switch (entity.castPose()) {
                    case ONE_HAND -> { rightArm.xRot = -1.5F; rightArm.yRot = -.1F; }
                    case TWO_HANDS -> { rightArm.xRot = -1.45F; leftArm.xRot = -1.45F; rightArm.yRot = -.25F; leftArm.yRot = .25F; }
                    case RAISED -> { rightArm.xRot = -2.9F; leftArm.xRot = -2.9F; rightArm.zRot = .25F; leftArm.zRot = -.25F; }
                    case CROUCH -> { rightArm.xRot = -.4F; leftArm.xRot = -.4F; rightArm.zRot = .6F; leftArm.zRot = -.6F; body.xRot = .45F; head.y = 3; }
                    default -> { return; }
                }
                rightSleeve.copyFrom(rightArm); leftSleeve.copyFrom(leftArm); jacket.copyFrom(body); hat.copyFrom(head);
            }
        }, .45F);
    }
    @Override public ResourceLocation getTextureLocation(SylarEntity entity) { return SKIN; }
    @Override protected boolean shouldShowName(SylarEntity entity) { return false; }
}
