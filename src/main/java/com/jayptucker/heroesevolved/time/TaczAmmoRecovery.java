package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** Optional TACZ APIs for recovering the original ammo type from a caught round. */
public final class TaczAmmoRecovery {
    private static final Map<LivingEntity, Round> FIRED = new WeakHashMap<>();
    private static final Map<Projectile, Round> PELLETS = new WeakHashMap<>();
    private static AmmoApi api;

    private record AmmoApi(Method ammoId, Method create, Method setId, Method build) {}

    private static final class Round {
        final long tick;
        boolean recovered;

        Round(long tick) { this.tick = tick; }
    }

    private TaczAmmoRecovery() {}

    public static void setup(FMLCommonSetupEvent event) {
        if (!ModList.get().isLoaded("tacz")) return;
        event.enqueueWork(TaczAmmoRecovery::install);
    }

    private static void install() {
        try {
            ClassLoader loader = TaczAmmoRecovery.class.getClassLoader();
            Class<?> bullet = Class.forName("com.tacz.guns.entity.EntityKineticBullet", false, loader);
            Class<?> builder = Class.forName("com.tacz.guns.api.item.builder.AmmoItemBuilder", false, loader);
            Class<? extends Event> fire = Class.forName("com.tacz.guns.api.event.common.GunFireEvent", false, loader).asSubclass(Event.class);
            Method shooter = fire.getMethod("getShooter");
            api = new AmmoApi(bullet.getMethod("getAmmoId"), builder.getMethod("create"),
                    builder.getMethod("setId", ResourceLocation.class), builder.getMethod("build"));
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, fire, event -> markRound(shooter, event));
        } catch (ReflectiveOperationException | LinkageError incompatible) {
            HeroesEvolved.LOGGER.warn("TACZ ammo recovery is unavailable", incompatible);
        }
    }

    private static void markRound(Method getShooter, Event event) {
        try {
            if (getShooter.invoke(event) instanceof LivingEntity shooter && shooter.level() instanceof ServerLevel level)
                FIRED.put(shooter, new Round(level.getGameTime()));
        } catch (ReflectiveOperationException incompatible) {
            HeroesEvolved.LOGGER.warn("Could not identify the shooter for TACZ ammo recovery", incompatible);
        }
    }

    public static void track(Projectile bullet, ServerLevel level) {
        if (api == null || !ProjectileTimeService.isTaczBullet(bullet) || !(bullet.getOwner() instanceof LivingEntity owner)) return;
        Round round = FIRED.get(owner);
        // One gun fire event precedes all pellets of a shot, even when several shots fire in one tick.
        if (round != null && round.tick == level.getGameTime()) PELLETS.put(bullet, round);
    }

    public static ItemStack recover(Projectile bullet) {
        if (api == null || !ProjectileTimeService.isTaczBullet(bullet)) return ItemStack.EMPTY;
        Round round = PELLETS.get(bullet);
        if (round != null && round.recovered) return ItemStack.EMPTY;
        try {
            if (!(api.ammoId.invoke(bullet) instanceof ResourceLocation ammoId)) return ItemStack.EMPTY;
            Object builder = api.create.invoke(null);
            api.setId.invoke(builder, ammoId);
            ItemStack ammo = (ItemStack) api.build.invoke(builder);
            if (round != null && !ammo.isEmpty()) round.recovered = true;
            return ammo;
        } catch (ReflectiveOperationException incompatible) {
            HeroesEvolved.LOGGER.warn("Could not recover TACZ ammo from a caught projectile", incompatible);
            return ItemStack.EMPTY;
        }
    }

    public static void clear() {
        FIRED.clear();
        PELLETS.clear();
    }
}
