package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.sounds.PowerSounds;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts every sound the server plays in a world (both {@code playSeededSound} overloads, which every
 * {@code Level.playSound} and {@code Entity.playSound} ends in), so {@link PowerSounds} can tell whether an
 * action made any sound of its own.
 */
@Mixin(ServerLevel.class)
public abstract class ServerSoundCountMixin {
    // A bare name would hook only the first overload, so both are spelled out.
    @Inject(method = {
            "playSeededSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            "playSeededSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V"
    }, at = @At("HEAD"))
    private void heroesevolved$countSound(CallbackInfo ci) {
        PowerSounds.heard();
    }
}
