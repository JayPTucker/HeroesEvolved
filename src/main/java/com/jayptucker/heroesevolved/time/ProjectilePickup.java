package com.jayptucker.heroesevolved.time;

import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** What goes into the hand when a shot is caught. Wither skulls are extinguished without a drop. */
public final class ProjectilePickup {
    private ProjectilePickup() {}

    public static ItemStack recover(Projectile shot) {
        if (shot instanceof WitherSkull) return ItemStack.EMPTY;
        if (shot instanceof AbstractArrow arrow) return arrow.getPickupItemStackOrigin().copy();
        if (shot instanceof ThrowableItemProjectile thrown) return thrown.getItem().copy();
        if (shot instanceof FireworkRocketEntity rocket) return rocket.getItem().copy();
        if (shot instanceof SmallFireball || shot instanceof LargeFireball) return new ItemStack(Items.FIRE_CHARGE);
        if (shot instanceof DragonFireball) return new ItemStack(Items.DRAGON_BREATH);
        if (shot instanceof AbstractWindCharge) return new ItemStack(Items.WIND_CHARGE);
        if (shot instanceof ShulkerBullet) return new ItemStack(Items.SHULKER_SHELL);
        return TaczAmmoRecovery.recover(shot);
    }
}
