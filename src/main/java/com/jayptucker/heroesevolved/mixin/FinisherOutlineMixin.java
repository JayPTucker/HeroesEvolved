package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.client.FinisherClient;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The glisten's outline is pale gold, whatever team the foe is on. The outline shader reads its colour from
 * {@code getTeamColor}; on the client only (a client mixin), and only for a foe that glistens for this player.
 */
@Mixin(Entity.class)
public abstract class FinisherOutlineMixin {
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void heroesevolved$glistenGold(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide && FinisherClient.outlined(self)) cir.setReturnValue(FinisherClient.GLISTEN);
        // An overcharged telepath hears every mind near (Power Amplification): outlined in red.
        else if (self.level().isClientSide && com.jayptucker.heroesevolved.client.ClientAmp.sees(self)) cir.setReturnValue(com.jayptucker.heroesevolved.client.ClientAmp.OUTLINE);
    }
}
