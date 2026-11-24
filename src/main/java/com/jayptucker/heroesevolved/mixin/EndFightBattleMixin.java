package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.endgame.EndBattle;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndDragonFight.class)
public abstract class EndFightBattleMixin {
    @Shadow @Final private ServerLevel level;
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void heroes$checkpoint(CallbackInfo ci){if(EndBattle.holdNativeFight(level))ci.cancel();}
    @Inject(method="setDragonKilled",at=@At("HEAD"),cancellable=true)
    private void heroes$falseVictory(EnderDragon dragon,CallbackInfo ci){if(EndBattle.deferDragonCompletion(dragon))ci.cancel();}
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerBossEvent;setVisible(Z)V"))
    private void heroes$oneBar(ServerBossEvent bar,boolean visible){bar.setVisible(visible&&!EndBattle.hideNativeBar(level));}
}
