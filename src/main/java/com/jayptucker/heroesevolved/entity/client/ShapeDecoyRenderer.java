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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

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

    /** The body drawn for {@code decoy}, made on first use; null if it can't be. */
    public static LivingEntity standIn(ShapeDecoy decoy) {
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

    /**
     * Puts the decoy's place, facing, swing and hurt flash on the body drawn for it. A speedster's copy (a Mirage's, or
     * a Between Heartbeats illusion) is also dressed in its maker's own armour and held items: it is them.
     */
    public static void sync(ShapeDecoy decoy, LivingEntity body) {
        body.setPosRaw(decoy.getX(), decoy.getY(), decoy.getZ());
        body.xo = decoy.xo; body.yo = decoy.yo; body.zo = decoy.zo;
        body.setYRot(decoy.getYRot()); body.yRotO = decoy.yRotO;
        body.yBodyRot = decoy.yBodyRot; body.yBodyRotO = decoy.yBodyRotO;
        body.yHeadRot = decoy.yHeadRot; body.yHeadRotO = decoy.yHeadRotO;
        body.setOnGround(true);
        body.tickCount = decoy.tickCount;
        body.hurtTime = decoy.hurtTime;
        body.swinging = decoy.swinging;
        body.swingingArm = decoy.swingingArm;
        body.swingTime = decoy.swingTime;
        body.attackAnim = decoy.attackAnim;
        body.oAttackAnim = decoy.oAttackAnim;
        if (decoy.speedster() && decoy.ownerId() >= 0 && decoy.level().getEntity(decoy.ownerId()) instanceof LivingEntity owner)
            dress(owner, body);
    }

    /** The maker's armour and held items on the copy, changed only when they differ. */
    private static void dress(LivingEntity owner, LivingEntity body) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HAND && slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack worn = owner.getItemBySlot(slot);
            if (!ItemStack.matches(body.getItemBySlot(slot), worn)) body.setItemSlot(slot, worn.copy());
        }
    }

    @Override
    public void render(ShapeDecoy decoy, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        LivingEntity body = standIn(decoy);
        if (body == null) return;
        sync(decoy, body);
        Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(body).render(body, decoy.getYRot(), partial, pose, buffers, light);
    }

    @Override public ResourceLocation getTextureLocation(ShapeDecoy decoy) { return NONE; }
}
