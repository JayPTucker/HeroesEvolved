package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.client.FinisherClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A foe low enough to finish glistens: it is outlined for this player alone (the colour is {@code FinisherOutlineMixin}'s). */
@Mixin(Minecraft.class)
public abstract class FinisherGlowMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void heroesevolved$glisten(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (com.jayptucker.heroesevolved.client.ClientGodMode.enabled(entity.getUUID()) || FinisherClient.outlined(entity)
                || com.jayptucker.heroesevolved.client.ClientAmp.sees(entity)) cir.setReturnValue(true);
    }
}
