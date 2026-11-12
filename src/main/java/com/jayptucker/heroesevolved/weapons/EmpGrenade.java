package com.jayptucker.heroesevolved.weapons;

import com.jayptucker.heroesevolved.electro.ElectroService;
import com.jayptucker.heroesevolved.grid.GridDisruption;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The EMP grenade. TaCZ and its default pack have no EMP grenade, so this is ours, crafted at TaCZ's gun smith table
 * (a "custom" result in its recipe). Thrown like a snowball, it sticks where it lands, beeps for a second and goes off
 * as a smaller version of Induced Radioactivity's EMP: through {@link GridDisruption#emp} redstone (and the lamps it
 * lights) dies, Forge Energy machines are drained and stopped, and Create's shafts seize, all for eight seconds within
 * ten blocks. Hostile creatures caught in it are dazzled for a moment, and an Electrokinetic's stored Charge is lost.
 */
public final class EmpGrenade {
    public static final int RADIUS = 10, DURATION = 160, FUSE = 20;

    private EmpGrenade() {}

    /** The blast. Public for tests and the preview. Returns what the grid lost. */
    public static GridDisruption.Pulse detonate(ServerLevel level, Vec3 at, @Nullable Entity owner) {
        ServerPlayer source = owner instanceof ServerPlayer player ? player : null;
        BlockPos center = BlockPos.containing(at);
        GridDisruption.Pulse pulse = GridDisruption.emp(source, level, center, RADIUS, DURATION);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(center).inflate(RADIUS),
                t -> t.isAlive() && t != owner && t.distanceToSqr(at) <= RADIUS * RADIUS && !friend(owner, t))) {
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, true));
            if (target instanceof Mob mob) mob.getNavigation().stop();
            if (target instanceof ServerPlayer player) {
                if (ElectroService.charge(player) > 0) {
                    ElectroService.drainCharge(player);
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "The pulse tears through you. Your stored Charge is gone.").withStyle(net.minecraft.ChatFormatting.AQUA), true);
                }
                ScreenTintPayload.send(player, 0xA0000008, 40, ScreenTintPayload.Style.VIGNETTE);
            }
        }
        SetPiecePayload.send(level, Piece.ELECTRO_BLACKOUT, at, at.add(0, 1, 0), RADIUS, null);
        CameraShakePayload.sendAround(level, at, RADIUS * 2, 0.9F, 10);
        level.playSound(null, center, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.6F, 0.6F);
        level.playSound(null, center, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F, 1.6F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.3, at.z, 40, 1.2, 0.6, 1.2, 0.6);
        return pulse;
    }

    /** The thrower and their friends are spared the dazzle (the grid goes dark for everyone). */
    private static boolean friend(@Nullable Entity owner, LivingEntity target) {
        if (owner instanceof ServerPlayer player)
            return com.jayptucker.heroesevolved.flight.FlightSkillService.isFriend(player, target)
                    || target instanceof ServerPlayer other && !(player.getServer() != null && player.getServer().isPvpAllowed() && player.canHarmPlayer(other));
        return owner != null && target.isAlliedTo(owner);
    }

    // ------------------------------------------------------------------

    public static final class GrenadeItem extends Item {
        public GrenadeItem(Properties properties) { super(properties); }

        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6F, 0.6F);
            if (!level.isClientSide) {
                Thrown grenade = new Thrown(level, player);
                grenade.setItem(stack.copyWithCount(1));
                grenade.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 1.1F, 0.8F);
                level.addFreshEntity(grenade);
            }
            player.getCooldowns().addCooldown(this, 20);
            player.awardStat(Stats.ITEM_USED.get(this));
            stack.consume(1, player);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
    }

    /** The grenade in flight: it sticks where it lands, beeps, and goes off. */
    public static final class Thrown extends ThrowableItemProjectile {
        private int fuse = -1;

        public Thrown(EntityType<? extends Thrown> type, Level level) { super(type, level); }

        public Thrown(Level level, LivingEntity owner) { super(ModEntities.EMP_GRENADE.get(), owner, level); }

        @Override protected Item getDefaultItem() { return ModItems.EMP_GRENADE.get(); }

        public boolean landed() { return fuse >= 0; }

        @Override protected void onHitBlock(BlockHitResult hit) {
            super.onHitBlock(hit);
            land(hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.1)));
        }

        @Override protected void onHitEntity(EntityHitResult hit) {
            super.onHitEntity(hit);
            land(hit.getLocation());
        }

        private void land(Vec3 at) {
            if (landed()) return;
            setPos(at);
            setDeltaMovement(Vec3.ZERO);
            setNoGravity(true);
            fuse = FUSE;
        }

        @Override public void tick() {
            if (!landed()) {
                super.tick();
                if (!level().isClientSide && tickCount > 200) land(position());
                return;
            }
            // Stuck: no movement, just the fuse.
            baseTick();
            if (!(level() instanceof ServerLevel level)) return;
            if (fuse % 5 == 0) {
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.9F, 1.2F + (FUSE - fuse) * 0.04F);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.15, getZ(), 3, 0.1, 0.1, 0.1, 0.05);
            }
            if (fuse-- <= 0) {
                detonate(level, position(), getOwner());
                discard();
            }
        }

        @Override public void addAdditionalSaveData(CompoundTag tag) {
            super.addAdditionalSaveData(tag);
            tag.putInt("fuse", fuse);
        }

        @Override public void readAdditionalSaveData(CompoundTag tag) {
            super.readAdditionalSaveData(tag);
            fuse = tag.contains("fuse") ? tag.getInt("fuse") : -1;
            if (landed()) setNoGravity(true);
        }
    }
}
