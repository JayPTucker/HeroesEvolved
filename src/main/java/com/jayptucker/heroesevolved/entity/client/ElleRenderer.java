package com.jayptucker.heroesevolved.entity.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.story.elle.ElleEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Elle Bishop: platinum hair, a pale blue top, current at her fingertips. Each windup has its own pose, so a player
 * learns to read what is coming: a pointed hand for the arc bolt, palms to the floor for ground current, both hands
 * drawing in for a siphon, both forward for ball lightning, both raised for the storm, and reeling after a backfire.
 */
public final class ElleRenderer extends HumanoidMobRenderer<ElleEntity, PlayerModel<ElleEntity>> {
    private static final ResourceLocation SKIN = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "textures/entity/elle_bishop.png");

    public ElleRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true) {
            @Override public void setupAnim(ElleEntity entity, float limbSwing, float limbAmount, float age, float yaw, float pitch) {
                super.setupAnim(entity, limbSwing, limbAmount, age, yaw, pitch);
                float twitch = Mth.sin(age * 1.7F) * 0.04F;
                switch (entity.pose()) {
                    case POINT -> { rightArm.xRot = -Mth.HALF_PI + head.xRot; rightArm.yRot = head.yRot - 0.05F; leftArm.xRot = -0.2F; leftArm.zRot = 0.25F; }
                    case PALMS_DOWN -> {
                        rightArm.xRot = -0.35F + twitch; leftArm.xRot = -0.35F - twitch;
                        rightArm.zRot = 0.95F; leftArm.zRot = -0.95F;
                    }
                    case DRAW -> {
                        // Both hands reach out and pull in, trembling with what comes up the arc.
                        float pull = 0.15F * Mth.sin(age * 0.5F);
                        rightArm.xRot = -1.25F + pull + twitch; leftArm.xRot = -1.25F + pull - twitch;
                        rightArm.yRot = -0.35F; leftArm.yRot = 0.35F;
                        body.xRot = -0.08F;
                    }
                    case BOTH_HANDS -> { rightArm.xRot = -1.4F + head.xRot; leftArm.xRot = -1.4F + head.xRot; rightArm.yRot = 0.3F; leftArm.yRot = -0.3F; }
                    case RAISED -> { rightArm.xRot = -2.85F + twitch; leftArm.xRot = -2.85F - twitch; rightArm.zRot = 0.3F; leftArm.zRot = -0.3F; }
                    case STAGGER -> {
                        // The backfire: thrown back, arms flung, shaking.
                        float shake = Mth.sin(age * 2.3F) * 0.12F;
                        rightArm.xRot = -0.4F + shake; leftArm.xRot = -0.5F - shake;
                        rightArm.zRot = 1.1F; leftArm.zRot = -1.1F;
                        body.xRot = -0.22F; head.xRot = -0.45F; head.zRot = shake;
                    }
                    case SCENE -> { rightArm.xRot = -0.15F; leftArm.xRot = -0.15F; rightArm.zRot = 0.3F; leftArm.zRot = -0.3F; }
                    default -> { return; }
                }
                rightSleeve.copyFrom(rightArm); leftSleeve.copyFrom(leftArm); jacket.copyFrom(body); hat.copyFrom(head);
            }
        }, .45F);
    }

    @Override public ResourceLocation getTextureLocation(ElleEntity entity) { return SKIN; }
    @Override protected boolean shouldShowName(ElleEntity entity) { return false; }
}
