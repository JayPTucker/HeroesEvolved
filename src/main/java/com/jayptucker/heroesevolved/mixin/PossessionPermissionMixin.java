package com.jayptucker.heroesevolved.mixin;
import com.jayptucker.heroesevolved.telepathy.PossessionService;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** While a possessed body runs its possessor's command, its own permission checks never exceed the cap. */
@Mixin(ServerPlayer.class)
public abstract class PossessionPermissionMixin {
    @Inject(method="getPermissionLevel",at=@At("RETURN"),cancellable=true)
    private void heroes$capPossessedCommand(CallbackInfoReturnable<Integer> result) {
        int level=result.getReturnValue(), capped=PossessionService.permissionLevel((ServerPlayer)(Object)this,level);
        if(capped!=level)result.setReturnValue(capped);
    }
}
