package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.speed.SpeedService;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;

/** Keep native speed attributes from turning the camera into an extreme fisheye. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class SpeedCameraEvents {
    @SubscribeEvent public static void fov(ComputeFovModifierEvent event) {
        var speed = event.getPlayer().getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.hasModifier(SpeedService.STRIDE))
            event.setNewFovModifier(Math.min(1.12F, event.getNewFovModifier()));
    }
}
