package com.jayptucker.heroesevolved.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerList.class)
public interface PlayerListSaveAccess {
    @Invoker("save") void heroesEvolved$savePlayer(ServerPlayer player);
}
