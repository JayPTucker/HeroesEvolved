package com.jayptucker.heroesevolved.weapons;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * The one place that touches TaCZ (Timeless and Classics Zero). The guns themselves are a gun pack in our own data
 * and assets; this only listens for their shots and hits and lets the Company's agents draw and fire. Every call is
 * guarded: if TaCZ changes and something here breaks, it logs once, {@link #available()} turns false, and our guns
 * fire as plain TaCZ guns with no power effects while agents fall back to their old weapons.
 */
final class TaczLink {
    private static boolean broken, listening;

    private TaczLink() {}

    static boolean available() { return !broken; }

    private static void fail(String what, Throwable t) {
        if (!broken) HeroesEvolved.LOGGER.warn("TaCZ link failed ({}); power-themed guns lose their effects", what, t);
        broken = true;
    }

    /** Subscribes to TaCZ's shot and hit events. Called once from the mod's constructor. */
    static void listen() {
        if (listening || broken) return;
        try {
            NeoForge.EVENT_BUS.addListener(EntityHurtByGunEvent.Pre.class, TaczLink::vibeIntercept);
            NeoForge.EVENT_BUS.addListener(EntityHurtByGunEvent.Post.class, TaczLink::hurt);
            NeoForge.EVENT_BUS.addListener(GunShootEvent.class, TaczLink::shot);
            NeoForge.EVENT_BUS.addListener(AmmoHitBlockEvent.class, TaczLink::block);
            listening = true;
        } catch (Throwable t) {
            fail("listen", t);
        }
    }

    private static void vibeIntercept(EntityHurtByGunEvent.Pre event) {
        if (broken || event.getLogicalSide() != LogicalSide.SERVER
                || !(event.getBullet().level() instanceof ServerLevel level)) return;
        Entity bullet = event.getBullet();
        var from = bullet.position();
        var target = event.getHurtEntity().getBoundingBox().clip(from, from.add(bullet.getDeltaMovement()));
        if (target.isPresent() && com.jayptucker.heroesevolved.vibe.VibeService.intercept(level, bullet, from, target.get()))
            event.setCanceled(true);
    }

    private static void hurt(EntityHurtByGunEvent.Post event) {
        if (broken || event.getLogicalSide() != LogicalSide.SERVER) return;
        try {
            if (event.getHurtEntity() instanceof LivingEntity target)
                PowerWeapons.hit(event.getAttacker(), target, event.getGunId(), event.getBullet().position());
        } catch (Throwable t) {
            fail("hurt", t);
        }
    }

    private static void shot(GunShootEvent event) {
        if (broken || event.getLogicalSide() != LogicalSide.SERVER) return;
        try {
            ResourceLocation id = gunId(event.getGunItemStack());
            if (id != null) PowerWeapons.shot(event.getShooter(), id);
        } catch (Throwable t) {
            fail("shot", t);
        }
    }

    private static void block(AmmoHitBlockEvent event) {
        if (broken || !(event.getLevel() instanceof ServerLevel level)) return;
        try {
            var ammo = event.getAmmo();
            PowerWeapons.hitBlock(level, event.getHitResult(), ammo.getOwner(), ammo.getGunId());
        } catch (Throwable t) {
            fail("block", t);
        }
    }

    // ------------------------------------------------------------------
    // Items
    // ------------------------------------------------------------------

    /** A gun with a full magazine and a round chambered, or empty if TaCZ doesn't know the id. */
    static ItemStack gun(ResourceLocation id, int ammo, boolean automatic) {
        if (broken) return ItemStack.EMPTY;
        try {
            return GunItemBuilder.create().setId(id).setAmmoCount(ammo).setAmmoInBarrel(true)
                    .setFireMode(automatic ? FireMode.AUTO : FireMode.SEMI).build();
        } catch (Throwable t) {
            fail("gun", t);
            return ItemStack.EMPTY;
        }
    }

    static ItemStack ammo(ResourceLocation id, int count) {
        if (broken) return ItemStack.EMPTY;
        try {
            return AmmoItemBuilder.create().setId(id).setCount(count).build();
        } catch (Throwable t) {
            fail("ammo", t);
            return ItemStack.EMPTY;
        }
    }

    /** The TaCZ gun id of a stack, or null if it isn't a TaCZ gun. */
    static @Nullable ResourceLocation gunId(ItemStack stack) {
        if (broken || stack.isEmpty()) return null;
        try {
            IGun gun = IGun.getIGunOrNull(stack);
            return gun == null ? null : gun.getGunId(stack);
        } catch (Throwable t) {
            fail("gunId", t);
            return null;
        }
    }

    static int ammoCount(ItemStack stack) {
        if (broken) return 0;
        try {
            IGun gun = IGun.getIGunOrNull(stack);
            return gun == null ? 0 : gun.getCurrentAmmoCount(stack) + (gun.hasBulletInBarrel(stack) ? 1 : 0);
        } catch (Throwable t) {
            fail("ammoCount", t);
            return 0;
        }
    }

    /** Fills a gun's magazine and chambers a round (an agent changing magazines). */
    static void reload(ItemStack stack, int ammo) {
        if (broken) return;
        try {
            IGun gun = IGun.getIGunOrNull(stack);
            if (gun == null) return;
            gun.setCurrentAmmoCount(stack, ammo);
            gun.setBulletInBarrel(stack, true);
        } catch (Throwable t) {
            fail("reload", t);
        }
    }

    // ------------------------------------------------------------------
    // Shooting (any living entity is a TaCZ gun operator)
    // ------------------------------------------------------------------

    /** Raises the gun in the shooter's main hand; TaCZ needs this before a shot and after a swap. */
    static void draw(LivingEntity shooter) {
        if (broken) return;
        try {
            IGunOperator.fromLivingEntity(shooter).draw(shooter::getMainHandItem);
        } catch (Throwable t) {
            fail("draw", t);
        }
    }

    /** Fires the drawn gun along pitch and yaw. Returns TaCZ's result name ("SUCCESS", "IS_DRAWING", "COOL_DOWN", ...). */
    static String shoot(LivingEntity shooter, float pitch, float yaw) {
        if (broken) return "BROKEN";
        try {
            ShootResult result = IGunOperator.fromLivingEntity(shooter).shoot(() -> pitch, () -> yaw);
            return result.name();
        } catch (Throwable t) {
            fail("shoot", t);
            return "BROKEN";
        }
    }

    // ------------------------------------------------------------------
    // Bullets in flight (Quake shakes them apart, or sets off the explosive ones)
    // ------------------------------------------------------------------

    /** Whether {@code entity} is a TaCZ bullet in flight. */
    static boolean bullet(Entity entity) {
        if (broken) return false;
        try {
            return entity instanceof com.tacz.guns.entity.EntityKineticBullet;
        } catch (Throwable t) {
            fail("bullet", t);
            return false;
        }
    }

    /**
     * An explosive bullet's blast radius and whether it breaks blocks, or null for an ordinary round. TaCZ keeps these
     * private, so they are read by reflection; any failure just treats the round as ordinary.
     */
    static float[] explosive(Entity entity) {
        if (broken || !bullet(entity)) return null;
        try {
            Class<?> type = com.tacz.guns.entity.EntityKineticBullet.class;
            if (!(boolean) field(type, "explosion").get(entity)) return null;
            float radius = (float) field(type, "explosionRadius").get(entity);
            boolean destroy = (boolean) field(type, "explosionDestroyBlock").get(entity);
            return new float[] {Math.max(1, radius), destroy ? 1 : 0};
        } catch (Throwable t) {
            return null;
        }
    }

    /** A bare TaCZ bullet (tests): ordinary, or explosive with {@code radius}. */
    static Entity testBullet(net.minecraft.server.level.ServerLevel level, float radius) {
        try {
            var bullet = new com.tacz.guns.entity.EntityKineticBullet(com.tacz.guns.entity.EntityKineticBullet.TYPE, level);
            if (radius > 0) {
                Class<?> type = com.tacz.guns.entity.EntityKineticBullet.class;
                field(type, "explosion").set(bullet, true);
                field(type, "explosionRadius").set(bullet, radius);
            }
            return bullet;
        } catch (Throwable t) {
            fail("testBullet", t);
            return null;
        }
    }

    private static java.lang.reflect.Field field(Class<?> type, String name) throws NoSuchFieldException {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
