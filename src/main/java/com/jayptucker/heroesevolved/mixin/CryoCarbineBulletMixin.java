package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Give cryo bolts fluid collision before TaCZ picks the nearest block or entity hit. */
@Pseudo
@Mixin(targets = "com.tacz.guns.entity.EntityKineticBullet", remap = false)
public abstract class CryoCarbineBulletMixin {
    @ModifyArg(method = "onBulletTick", index = 1, require = 0,
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/util/block/BlockRayTrace;rayTraceBlocks(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"))
    private ClipContext heroesevolved$clipColdBolts(ClipContext clip) {
        return PowerWeapons.bulletClip((Entity) (Object) this, clip);
    }
}
