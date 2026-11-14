package com.jayptucker.heroesevolved.entity.client;

import com.jayptucker.heroesevolved.client.MorphClient;
import com.jayptucker.heroesevolved.shapeshift.ShapeDecoy;
import com.jayptucker.heroesevolved.shapeshift.ShapeshiftForm;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Draws a Decoy as the shape its maker wore: the same creature, or the same person (a double with their skin and name
 * tag), standing still where they were. The seam down its back is drawn by {@code ShapeTrickArt}; that seam is the only
 * thing that tells a careful eye it is empty.
 */
public final class ShapeDecoyRenderer extends EntityRenderer<ShapeDecoy> {
    private static final ResourceLocation NONE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    public ShapeDecoyRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = .45F;
    }

    private static LivingEntity standIn(ShapeDecoy decoy) {
        if (decoy.standIn instanceof LivingEntity existing) return existing;
        ShapeshiftForm form = decoy.form();
        if (form == null || !(decoy.level() instanceof ClientLevel level)) return null;
        LivingEntity made;
        if (form.person()) made = MorphClient.person(level, form.personOrNull());
        else {
            Entity entity;
            try { entity = form.type().create(level); } catch (RuntimeException e) { entity = null; }
            if (!(entity instanceof LivingEntity living)) return null;
            if (living instanceof Mob mob) mob.setNoAi(true);
            made = living;
        }
        made.setSilent(true);
        decoy.standIn = made;
        return made;
    }

    @Override
    public void render(ShapeDecoy decoy, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        LivingEntity body = standIn(decoy);
        if (body == null) return;
        body.setPosRaw(decoy.getX(), decoy.getY(), decoy.getZ());
        body.xo = decoy.xo; body.yo = decoy.yo; body.zo = decoy.zo;
        body.setYRot(decoy.getYRot()); body.yRotO = decoy.getYRot();
        body.yBodyRot = decoy.yBodyRot; body.yBodyRotO = decoy.yBodyRot;
        body.yHeadRot = decoy.yHeadRot; body.yHeadRotO = decoy.yHeadRot;
        body.setOnGround(true);
        body.tickCount = decoy.tickCount;
        body.hurtTime = decoy.hurtTime;
        Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(body).render(body, decoy.getYRot(), partial, pose, buffers, light);
    }

    @Override public ResourceLocation getTextureLocation(ShapeDecoy decoy) { return NONE; }
}
