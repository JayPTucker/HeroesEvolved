package com.jayptucker.heroesevolved.story.elle;

import com.jayptucker.heroesevolved.grid.GridDisruption;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.List;

/**
 * Elle's Capacitor, what she leaves when she is beaten: a cracked Primatech cell that still holds a charge. It comes with
 * three charges. Use it to throw a bolt that hurts and stuns what it hits; sneak and use it on any machine that stores
 * Forge Energy to drink {@value #FE_PER_CHARGE} FE back into a charge.
 */
public final class ElleCapacitorItem extends Item {
    public static final int MAX_CHARGES = 3, FE_PER_CHARGE = 10_000, COOLDOWN = 20;
    public static final double RANGE = 24;
    public static final float DAMAGE = 8;
    private static final String CHARGES = "heroesevolved_charges";

    public ElleCapacitorItem(Properties properties) { super(properties); }

    public static int charges(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : Math.clamp(data.copyTag().getInt(CHARGES), 0, MAX_CHARGES);
    }

    public static ItemStack charged(ItemStack stack, int charges) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(CHARGES, Math.clamp(charges, 0, MAX_CHARGES)));
        return stack;
    }

    @Override public boolean isBarVisible(ItemStack stack) { return true; }
    @Override public int getBarWidth(ItemStack stack) { return Math.round(13F * charges(stack) / MAX_CHARGES); }
    @Override public int getBarColor(ItemStack stack) { return 0x84D5FF; }
    @Override public boolean isFoil(ItemStack stack) { return charges(stack) == MAX_CHARGES; }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.heroesevolved.elles_capacitor.charges", charges(stack), MAX_CHARGES).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.heroesevolved.elles_capacitor.use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.heroesevolved.elles_capacitor.drink").withStyle(ChatFormatting.GRAY));
    }

    /** Sneak and use on a machine: drink its energy back into a charge. */
    @Override public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!GridDisruption.isMachine(level, pos)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack stack = context.getItemInHand();
        if (charges(stack) >= MAX_CHARGES) {
            player.displayClientMessage(Component.translatable("item.heroesevolved.elles_capacitor.full").withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.FAIL;
        }
        if (!level.mayInteract(player, pos)) return InteractionResult.FAIL;
        if (drink(level, pos) < FE_PER_CHARGE) {
            player.displayClientMessage(Component.translatable("item.heroesevolved.elles_capacitor.empty").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.FAIL;
        }
        charged(stack, charges(stack) + 1);
        SetPiecePayload.send((ServerLevel) level, Piece.ELLE_BOLT, Vec3.atCenterOf(pos), player.getEyePosition().add(0, -0.5, 0), 0.6F, null);
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.6F);
        return InteractionResult.CONSUME;
    }

    /** Takes one charge's worth of Forge Energy out of a machine, if it has that much. Returns what it took. */
    static int drink(Level level, BlockPos pos) {
        List<IEnergyStorage> storages = GridDisruption.storages(level, pos, null, level.getBlockEntity(pos));
        int available = 0;
        for (IEnergyStorage storage : storages) if (storage.canExtract()) available += storage.extractEnergy(FE_PER_CHARGE - available, true);
        if (available < FE_PER_CHARGE) return available;
        int taken = 0;
        for (IEnergyStorage storage : storages) if (taken < FE_PER_CHARGE && storage.canExtract()) taken += storage.extractEnergy(FE_PER_CHARGE - taken, false);
        return taken;
    }

    /** Throw a bolt down your look: whatever it hits is hurt and stunned. */
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (charges(stack) <= 0) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("item.heroesevolved.elles_capacitor.spent").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!(player instanceof ServerPlayer user)) return InteractionResultHolder.sidedSuccess(stack, true);
        fire(user, stack);
        return InteractionResultHolder.consume(stack);
    }

    /** Fires one charge. Returns what it hit, if anything. */
    public static LivingEntity fire(ServerPlayer player, ItemStack stack) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition(), end = eye.add(player.getLookAngle().scale(RANGE));
        var block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        end = block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end, new AABB(eye, end).inflate(1),
                e -> e instanceof LivingEntity l && l.isAlive() && !e.isSpectator() && e != player, 0.3F);
        LivingEntity target = hit != null && hit.getEntity() instanceof LivingEntity l ? l : null;
        if (target != null) end = target.getBoundingBox().getCenter();
        Vec3 hand = eye.add(player.getLookAngle().scale(0.6)).add(0, -0.35, 0);
        SetPiecePayload.send(level, Piece.ELLE_BOLT, hand, end, 1.0F, null);
        level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6F, 1.9F);
        if (target != null && target.hurt(level.damageSources().playerAttack(player), DAMAGE)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 4));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1));
        }
        charged(stack, charges(stack) - 1);
        player.getCooldowns().addCooldown(stack.getItem(), COOLDOWN);
        return target;
    }

    static boolean hitsSomething(Entity e) { return e instanceof LivingEntity; }
}
