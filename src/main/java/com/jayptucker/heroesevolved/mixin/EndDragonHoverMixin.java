package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.endgame.EndBattle;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.*;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Keep vanilla flight, wings, neck and multipart updates; provide only the encounter's flight target. */
@Mixin(DragonHoverPhase.class)
public abstract class EndDragonHoverMixin extends AbstractDragonPhaseInstance {
    protected EndDragonHoverMixin(EnderDragon dragon){super(dragon);}
    @Inject(method="getFlyTargetLocation",at=@At("HEAD"),cancellable=true)
    private void heroes$target(CallbackInfoReturnable<Vec3> ci){if(EndBattle.managed(dragon))ci.setReturnValue(EndBattle.flyTarget(dragon));}
    @Inject(method="isSitting",at=@At("HEAD"),cancellable=true)
    private void heroes$sitting(CallbackInfoReturnable<Boolean> ci){if(EndBattle.managed(dragon))ci.setReturnValue(EndBattle.grounded(dragon));}
}
