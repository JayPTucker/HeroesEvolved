package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.endportal.EndPortalRitual;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.EnderEyeItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep vanilla insertion, item payment, comparator updates and eye sound; defer only the portal opening. */
@Mixin(EnderEyeItem.class)
public abstract class EnderEyeRitualMixin {
    @Inject(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/EndPortalFrameBlock;getOrCreatePortalShape()Lnet/minecraft/world/level/block/state/pattern/BlockPattern;"), cancellable = true)
    private void heroes$waitForOfferings(UseOnContext context, CallbackInfoReturnable<InteractionResult> result) {
        if (context.getLevel() instanceof ServerLevel level) {
            EndPortalRitual.eyeInserted(level, context.getClickedPos(), context.getPlayer() instanceof ServerPlayer p ? p : null);
            result.setReturnValue(InteractionResult.CONSUME);
        }
    }
}
