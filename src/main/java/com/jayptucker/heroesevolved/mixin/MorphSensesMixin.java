package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.client.MorphSenses;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** What a wolf's nose or a warden's vibration sense picks out glows for the shapeshifter alone. */
@Mixin(Minecraft.class)
public abstract class MorphSensesMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void heroesevolved$senses(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (MorphSenses.sensed(entity)) cir.setReturnValue(true);
    }
}
