package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.ClientCameraShakeState;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Applies Quake camera shake without moving the player's real rotation. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class CameraShakeEvents {
    private CameraShakeEvents() {
    }

    @SubscribeEvent
    public static void onLoggingOut(
            net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event
    ) {
        // Client-side timers use absolute game time and must not carry over
        // into the next world joined.
        ClientCameraShakeState.reset();
        com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
        com.jayptucker.heroesevolved.network.ClientOverexertionState.reset();
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(
            ViewportEvent.ComputeCameraAngles event
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        float partialTick = (float) event.getPartialTick();
        float strength = ClientCameraShakeState.getStrength(
                minecraft.level.getGameTime(), partialTick
        );
        strength *= com.jayptucker.heroesevolved.client.VisualComfort.shakeScale();
        if (strength <= 0.0F) {
            return;
        }

        // Layered sine waves at unrelated frequencies read as a rumble,
        // where random jitter each frame would read as lag.
        double time = (minecraft.level.getGameTime() + partialTick) * 1.7D;
        float yaw = (float) (Math.sin(time * 1.9D) + Math.sin(time * 3.7D) * 0.5D);
        float pitch = (float) (Math.cos(time * 2.3D) + Math.sin(time * 4.1D) * 0.5D);
        float roll = (float) Math.sin(time * 1.3D);

        event.setYaw(event.getYaw() + yaw * strength);
        event.setPitch(event.getPitch() + pitch * strength);
        event.setRoll(event.getRoll() + roll * strength * 0.8F);
    }
}
