package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.endgame.EndBattle;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(EnderDragon.class)
public abstract class EndDragonBattleMixin {
    @Inject(method="reallyHurt",at=@At("HEAD"),cancellable=true)
    private void heroes$hit(DamageSource source,float amount,CallbackInfoReturnable<Boolean> ci){
        var dragon=(EnderDragon)(Object)this;
        if(!EndBattle.rawDragon()&&EndBattle.nativeDragon(dragon))ci.setReturnValue(EndBattle.dragonHit(dragon,source,amount));
    }
    @Inject(method="hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",at=@At("HEAD"),cancellable=true)
    private void heroes$powerHit(DamageSource source,float amount,CallbackInfoReturnable<Boolean> ci){
        var dragon=(EnderDragon)(Object)this;
        if(EndBattle.nativeDragon(dragon))ci.setReturnValue(dragon.hurt(dragon.head,source,amount));
    }
    @Inject(method="checkCrystals",at=@At("HEAD"),cancellable=true)
    private void heroes$feeding(CallbackInfo ci){var d=(EnderDragon)(Object)this;if(EndBattle.managed(d)){d.nearestCrystal=null;ci.cancel();}}
    @Inject(method="checkWalls",at=@At("HEAD"),cancellable=true)
    private void heroes$terrain(AABB box,CallbackInfoReturnable<Boolean> ci){if(EndBattle.managed((EnderDragon)(Object)this))ci.setReturnValue(false);}
    @Inject(method="knockBack",at=@At("HEAD"),cancellable=true)
    private void heroes$noSilentWing(CallbackInfo ci){if(EndBattle.managed((EnderDragon)(Object)this))ci.cancel();}
    @Inject(method="hurt(Ljava/util/List;)V",at=@At("HEAD"),cancellable=true)
    private void heroes$noSilentBite(CallbackInfo ci){if(EndBattle.managed((EnderDragon)(Object)this))ci.cancel();}
    @Inject(method="tickDeath",at=@At("HEAD"))
    private void heroes$death(CallbackInfo ci){EndBattle.dragonDeathTick((EnderDragon)(Object)this);}
    @Redirect(method="tickDeath",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"))
    private void heroes$catchExperience(ServerLevel level,Vec3 at,int amount){if(!EndBattle.captureXP((EnderDragon)(Object)this,amount))ExperienceOrb.award(level,at,amount);}
}
