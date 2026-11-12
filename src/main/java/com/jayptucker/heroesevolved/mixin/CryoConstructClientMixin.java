package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.client.effects.setpiece.CryoConstructArt;
import com.jayptucker.heroesevolved.cryo.FrostConstructBlock;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class CryoConstructClientMixin {
    @Inject(method = "sendBlockUpdated", at = @At("TAIL"))
    private void heroes$frostChanged(BlockPos pos, BlockState before, BlockState after, int flags, CallbackInfo ci) {
        if (FrostConstructBlock.isFrost(before) || FrostConstructBlock.isFrost(after))
            CryoConstructArt.changed((ClientLevel) (Object) this, pos, after);
    }

    @Inject(method = "onChunkLoaded", at = @At("TAIL"))
    private void heroes$frostLoaded(ChunkPos pos, CallbackInfo ci) {
        ClientLevel level = (ClientLevel) (Object) this;
        LevelChunk chunk = level.getChunkSource().getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
        if (chunk != null) CryoConstructArt.loaded(level, chunk);
    }
}
