package com.jayptucker.heroesevolved.entity.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.effects.intro.IntroScene;
import com.jayptucker.heroesevolved.story.actor.StoryActorEntity;
import com.jayptucker.heroesevolved.story.actor.StoryActorEntity.Role;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Noah, Claire, Peter and Ted. One player-shaped model, a skin per role, and a pose per beat of a scene: Claire's hands
 * held out to her father, his shove and his raised stun gun, Peter's grip on Ted's shoulders, Ted down on one knee with
 * his arms out and shaking. Ted burns from the inside (an emissive layer), and in the Time vision Peter and Ted are not
 * drawn while the explosion stands where they were: they come back as time runs back past the flash.
 */
public final class StoryActorRenderer extends HumanoidMobRenderer<StoryActorEntity, PlayerModel<StoryActorEntity>> {
    private static final ResourceLocation[] SKINS = new ResourceLocation[Role.values().length];
    private static final ResourceLocation TED_GLOW = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "textures/entity/ted_sprague_glow.png");

    static {
        for (Role role : Role.values())
            SKINS[role.ordinal()] = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "textures/entity/" + switch (role) {
                case NOAH -> "noah_bennet";
                case CLAIRE -> "claire_bennet";
                case PETER -> "peter_petrelli";
                case TED -> "ted_sprague";
                case SYLAR -> "sylar";
                case TRAVELER -> "peter_petrelli";   // never used: a traveler wears its player's own skin
            } + ".png");
    }

    /** The two arm widths: every actor's model, and the slim one a double may need. */
    private final PlayerModel<StoryActorEntity> wide, slim;

    public StoryActorRenderer(EntityRendererProvider.Context context) {
        super(context, posedModel(context, false), .5F);
        wide = getModel();
        slim = posedModel(context, true);
        addLayer(new RenderLayer<>(this) {
            @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, StoryActorEntity actor, float limbSwing,
                                         float limbAmount, float partial, float age, float yaw, float pitch) {
                if (actor.role() != Role.TED || actor.isInvisible()) return;
                // He burns from the inside, brightest when he is about to go.
                getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(TED_GLOW)), 0xF000F0, OverlayTexture.NO_OVERLAY);
            }
        });
    }

    /** A player model, wide- or slim-armed, that takes the actors' poses. */
    private static PlayerModel<StoryActorEntity> posedModel(EntityRendererProvider.Context context, boolean slimArms) {
        return new PlayerModel<>(context.bakeLayer(slimArms ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slimArms) {
            @Override public void setupAnim(StoryActorEntity entity, float limbSwing, float limbAmount, float age, float yaw, float pitch) {
                super.setupAnim(entity, limbSwing, limbAmount, age, yaw, pitch);
                float shake = Mth.sin(age * 2.1F) * 0.05F;
                switch (entity.pose()) {
                    case PLEAD -> {
                        // Both hands out to him, palms up, trembling.
                        rightArm.xRot = -1.05F + shake; leftArm.xRot = -1.05F - shake;
                        rightArm.yRot = -0.3F; leftArm.yRot = 0.3F;
                        head.xRot = -0.15F;
                    }
                    case SHOVE -> {
                        rightArm.xRot = -1.5F; leftArm.xRot = -1.5F;
                        rightArm.yRot = -0.12F; leftArm.yRot = 0.12F;
                        body.xRot = 0.18F;
                    }
                    case AIM -> {
                        // The stun gun held out level along his look, the other hand under the grip.
                        rightArm.xRot = -Mth.HALF_PI + head.xRot; rightArm.yRot = head.yRot - 0.06F;
                        leftArm.xRot = -Mth.HALF_PI + head.xRot + 0.1F; leftArm.yRot = head.yRot + 0.5F;
                    }
                    case REACH -> {
                        // Hands on someone's shoulders in front of him.
                        rightArm.xRot = -1.35F; leftArm.xRot = -1.35F;
                        rightArm.yRot = 0.28F; leftArm.yRot = -0.28F;
                        body.xRot = 0.12F;
                    }
                    case HAND_ON_GLASS -> { rightArm.xRot = -1.45F; rightArm.yRot = -0.05F; head.xRot = 0.1F; }
                    case KNEEL -> {
                        rightLeg.xRot = -1.45F; rightLeg.yRot = 0.1F;
                        leftLeg.xRot = -0.05F; leftLeg.zRot = -0.05F;
                        body.xRot = 0.35F; head.xRot = 0.5F;
                        rightArm.xRot = -0.6F; leftArm.xRot = -0.6F; rightArm.zRot = 0.3F; leftArm.zRot = -0.3F;
                    }
                    case HOVER -> {
                        // Held in the air: arms a little out, legs hanging and drifting.
                        float drift = Mth.sin(age * 0.12F) * 0.08F;
                        rightArm.xRot = -0.15F; leftArm.xRot = -0.15F; rightArm.zRot = 0.35F; leftArm.zRot = -0.35F;
                        rightLeg.xRot = 0.12F + drift; leftLeg.xRot = -0.05F - drift; rightLeg.zRot = 0.05F; leftLeg.zRot = -0.05F;
                    }
                    case FLY -> {
                        // Lying along the line of flight (the renderer turns the body): fist first, the other arm back.
                        rightArm.xRot = -Mth.PI + 0.1F; rightArm.zRot = 0.05F;
                        leftArm.xRot = 0.2F; leftArm.zRot = -0.12F;
                        rightLeg.xRot = 0.04F; leftLeg.xRot = -0.04F;
                        head.xRot = -1.1F;
                    }
                    case STRIKE -> {
                        // A hand thrown out at someone: a punch, a bolt, a grip from across the sky.
                        rightArm.xRot = -Mth.HALF_PI + head.xRot; rightArm.yRot = head.yRot;
                        leftArm.xRot = 0.5F; leftArm.zRot = -0.45F;
                        body.yRot = -0.25F;
                        rightLeg.xRot = 0.3F; leftLeg.xRot = -0.2F;
                    }
                    case TUMBLE -> {
                        // Thrown: arms and legs flung wide (the renderer spins the body).
                        rightArm.zRot = 2.3F + Mth.sin(age * 0.9F) * 0.3F; leftArm.zRot = -2.3F - Mth.sin(age * 0.9F) * 0.3F;
                        rightLeg.zRot = 0.5F; leftLeg.zRot = -0.5F; head.xRot = 0.4F;
                    }
                    case GLOW -> {
                        // On his knees, arms flung out, shaking with what is coming up through him.
                        float hard = shake * 3;
                        rightLeg.xRot = -1.45F; leftLeg.xRot = -0.05F;
                        body.xRot = -0.2F + hard * 0.2F; head.xRot = -0.7F + hard * 0.3F;
                        rightArm.xRot = -0.3F + hard; leftArm.xRot = -0.3F - hard;
                        rightArm.zRot = 1.25F; leftArm.zRot = -1.25F;
                    }
                    default -> { return; }
                }
                rightSleeve.copyFrom(rightArm); leftSleeve.copyFrom(leftArm); jacket.copyFrom(body); hat.copyFrom(head);
                rightPants.copyFrom(rightLeg); leftPants.copyFrom(leftLeg);
            }
        };
    }

    @Override public void render(StoryActorEntity actor, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        Role role = actor.role();
        // A double of a player with slim arms is drawn on the slim model, everyone else on the wide one.
        model = role == Role.TRAVELER && skin(actor).model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM ? slim : wide;
        // In the Time vision, nobody at ground zero is drawn while the explosion stands where they were.
        if (role != Role.NOAH && role != Role.CLAIRE && IntroScene.isVision() && TimeIntroService.isCity(actor.level()) && IntroScene.t(partial) >= 0) return;
        pose.pushPose();
        // Down on one knee: the model hangs from the hips, so the whole body drops.
        if (actor.pose() == StoryActorEntity.Pose.KNEEL || actor.pose() == StoryActorEntity.Pose.GLOW) pose.translate(0, -0.42, 0);
        super.render(actor, yaw, partial, pose, buffers, light);
        pose.popPose();
    }

    /** In the air: lie along the line of flight (as an elytra flyer does), or spin end over end when thrown. */
    @Override protected void setupRotations(StoryActorEntity actor, PoseStack pose, float bob, float yBodyRot, float partial, float scale) {
        super.setupRotations(actor, pose, bob, yBodyRot, partial, scale);
        switch (actor.pose()) {
            case FLY -> {
                pose.translate(0, 0.9, 0);
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90 - actor.getViewXRot(partial)));
                pose.translate(0, -0.9, 0);
            }
            case TUMBLE -> {
                float spin = (actor.tickCount + partial) * 38;
                pose.translate(0, 0.9, 0);
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(spin));
                pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(spin * 0.4F));
                pose.translate(0, -0.9, 0);
            }
            default -> { }
        }
    }

    @Override public ResourceLocation getTextureLocation(StoryActorEntity actor) {
        if (actor.role() == Role.TRAVELER) return skin(actor).texture();
        return SKINS[actor.role().ordinal()];
    }

    /** A double's skin: its player's, as this client knows it (or that player's default skin). */
    private static net.minecraft.client.resources.PlayerSkin skin(StoryActorEntity actor) {
        java.util.UUID id = actor.doubleOf().orElse(actor.getUUID());
        var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
        var info = connection == null ? null : connection.getPlayerInfo(id);
        return info != null ? info.getSkin() : net.minecraft.client.resources.DefaultPlayerSkin.get(id);
    }

    /** Only a double shows a name over its head: the time traveler's own. */
    @Override protected boolean shouldShowName(StoryActorEntity actor) { return actor.role() == Role.TRAVELER && !actor.isInvisible(); }
}
