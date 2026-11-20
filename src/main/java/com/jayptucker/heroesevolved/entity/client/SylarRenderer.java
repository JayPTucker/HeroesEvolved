package com.jayptucker.heroesevolved.entity.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.story.sylar.SylarEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Human silhouette, dark coat, watchmaker's hands. How he holds himself says what is coming: one hand out to draw on a
 * case, both hands to shape something wide, arms raised for a big move, crouched to strike low, one hand up holding
 * someone in the air, reeling from a broken concentration, and kneeling when he is spent, down or dying.
 */
public final class SylarRenderer extends HumanoidMobRenderer<SylarEntity, PlayerModel<SylarEntity>> {
    private static final ResourceLocation SKIN = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "textures/entity/sylar.png");

    public SylarRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false) {
            @Override public void setupAnim(SylarEntity entity, float limbSwing, float limbAmount, float age, float yaw, float pitch) {
                super.setupAnim(entity, limbSwing, limbAmount, age, yaw, pitch);
                switch (entity.castPose()) {
                    case ONE_HAND -> { rightArm.xRot = -1.5F; rightArm.yRot = -.1F; }
                    case TWO_HANDS -> { rightArm.xRot = -1.45F; leftArm.xRot = -1.45F; rightArm.yRot = -.25F; leftArm.yRot = .25F; }
                    case RAISED -> { rightArm.xRot = -2.9F; leftArm.xRot = -2.9F; rightArm.zRot = .25F; leftArm.zRot = -.25F; }
                    case CROUCH -> { rightArm.xRot = -.4F; leftArm.xRot = -.4F; rightArm.zRot = .6F; leftArm.zRot = -.6F; body.xRot = .45F; head.y = 3; }
                    // Holding someone up in the air: one arm high, fingers spread; the other hand at his temple, studying.
                    case HOLD -> {
                        rightArm.xRot = -2.5F; rightArm.yRot = -.2F; rightArm.zRot = .1F;
                        leftArm.xRot = -2.1F; leftArm.yRot = .9F; leftArm.zRot = -.3F;
                        head.xRot = -.45F;
                    }
                    // A broken concentration: he reels back, a hand at his eye.
                    case STAGGER -> {
                        float sway = Mth.sin(age * .45F) * .12F;
                        leftArm.xRot = -2.2F; leftArm.yRot = .55F; rightArm.xRot = -.9F; rightArm.yRot = -.75F;
                        body.xRot = -.18F + sway * .5F; head.xRot = -.35F; head.zRot = sway;
                    }
                    // Spent, down or dying: on one knee, a hand on the floor.
                    case KNEEL -> {
                        body.xRot = .35F; head.y = 4; head.xRot = .3F;
                        body.y = 4; rightArm.y = 6; leftArm.y = 6;
                        rightArm.xRot = -.5F; rightArm.zRot = .15F; leftArm.xRot = .4F; leftArm.zRot = -.25F;
                        rightLeg.xRot = -1.55F; rightLeg.y = 13; rightLeg.z = -1.5F;
                        leftLeg.xRot = -.15F; leftLeg.y = 13; leftLeg.z = 3;
                    }
                    default -> { return; }
                }
                rightSleeve.copyFrom(rightArm); leftSleeve.copyFrom(leftArm); jacket.copyFrom(body); hat.copyFrom(head);
                rightPants.copyFrom(rightLeg); leftPants.copyFrom(leftLeg);
            }
        }, .45F);
    }

    @Override public ResourceLocation getTextureLocation(SylarEntity entity) { return SKIN; }
    @Override protected boolean shouldShowName(SylarEntity entity) { return false; }
}
