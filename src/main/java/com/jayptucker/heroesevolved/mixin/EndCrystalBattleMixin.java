package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.endgame.EndBattle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EndCrystal.class)
public abstract class EndCrystalBattleMixin {
    @Inject(method="hurt",at=@At("HEAD"),cancellable=true)
    private void heroes$focus(DamageSource source,float amount,CallbackInfoReturnable<Boolean> ci){
        Boolean result=EndBattle.crystalHit((EndCrystal)(Object)this,source,amount);if(result!=null)ci.setReturnValue(result);
    }
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean heroes$noPrismFire(Level level,BlockPos pos,BlockState state){return !EndBattle.prism((EndCrystal)(Object)this)&&level.setBlockAndUpdate(pos,state);}
}
