package com.jayptucker.heroesevolved.mixin;
import com.jayptucker.heroesevolved.telepathy.PossessionService;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.players.PlayerList;
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PossessionInputMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="handleUseItem",at=@At("HEAD"),cancellable=true)
    private void heroes$handleUseItem(ServerboundUseItemPacket p,CallbackInfo ci) { if(PossessionService.route(player,p))ci.cancel(); }
    @Inject(method="handleUseItemOn",at=@At("HEAD"),cancellable=true)
    private void heroes$handleUseItemOn(ServerboundUseItemOnPacket p,CallbackInfo ci) { if(PossessionService.route(player,p))ci.cancel(); }
    @Inject(method="handleInteract",at=@At("HEAD"),cancellable=true)
    private void heroes$handleInteract(ServerboundInteractPacket p,CallbackInfo ci) { if(PossessionService.route(player,p))ci.cancel(); }
    @Inject(method="handlePlayerAction",at=@At("HEAD"),cancellable=true)
    private void heroes$handlePlayerAction(ServerboundPlayerActionPacket p,CallbackInfo ci) { if(PossessionService.route(player,p))ci.cancel(); }
    @Inject(method="handleContainerClick",at=@At("HEAD"),cancellable=true)
    private void heroes$handleContainerClick(ServerboundContainerClickPacket p,CallbackInfo ci) { if(PossessionService.route(player,p))ci.cancel(); }
    @Inject(method="handleContainerClose",at=@At("HEAD"),cancellable=true)
    private void heroes$handleContainerClose(ServerboundContainerClosePacket p,CallbackInfo ci) { if(PossessionService.route(player,p))ci.cancel(); }
    @Inject(method="handleSetCarriedItem",at=@At("HEAD"),cancellable=true)
    private void heroes$handleSetCarriedItem(ServerboundSetCarriedItemPacket p,CallbackInfo ci) { if(PossessionService.route(player,p))ci.cancel(); }
    @Inject(method="handleAnimate",at=@At("HEAD"),cancellable=true)
    private void heroes$handleAnimate(ServerboundSwingPacket p,CallbackInfo ci) { if(PossessionService.route(player,p))ci.cancel(); }
    @Inject(method="handleContainerButtonClick",at=@At("HEAD"),cancellable=true)
    private void heroes$handleContainerButtonClick(ServerboundContainerButtonClickPacket p,CallbackInfo ci) { if(PossessionService.route(player,p))ci.cancel(); }
    @Inject(method="handlePlaceRecipe",at=@At("HEAD"),cancellable=true)
    private void heroes$handlePlaceRecipe(ServerboundPlaceRecipePacket p,CallbackInfo ci) { if(PossessionService.route(player,p))ci.cancel(); }
    @Inject(method="handleSelectTrade",at=@At("HEAD"),cancellable=true)
    private void heroes$handleSelectTrade(ServerboundSelectTradePacket p,CallbackInfo ci) { if(PossessionService.route(player,p))ci.cancel(); }
    @Inject(method="handleRenameItem",at=@At("HEAD"),cancellable=true)
    private void heroes$handleRenameItem(ServerboundRenameItemPacket p,CallbackInfo ci) { if(PossessionService.route(player,p))ci.cancel(); }
    @Inject(method="handleSetBeaconPacket",at=@At("HEAD"),cancellable=true)
    private void heroes$handleSetBeaconPacket(ServerboundSetBeaconPacket p,CallbackInfo ci) { if(PossessionService.route(player,p))ci.cancel(); }
    @Inject(method="handlePlayerCommand",at=@At("HEAD"),cancellable=true)
    private void heroes$command(ServerboundPlayerCommandPacket p,CallbackInfo ci){if(PossessionService.route(player,p))ci.cancel();}
    @Inject(method={"handleMovePlayer","handleMoveVehicle","handlePlayerInput","handlePlayerAbilities","handleSetCreativeModeSlot"},at=@At("HEAD"),cancellable=true)
    private void heroes$hold(CallbackInfo ci) { if(player.server.isSameThread() && PossessionService.locked(player))ci.cancel(); }
    /** Vanilla still checks and records the possessor's signed message; only who says it changes. */
    @WrapOperation(method="broadcastChatMessage",at=@At(value="INVOKE",target="Lnet/minecraft/server/players/PlayerList;broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V"))
    private void heroes$speak(PlayerList list,PlayerChatMessage message,ServerPlayer sender,ChatType.Bound type,Operation<Void> original) {
        if(!PossessionService.speak(sender,message))original.call(list,message,sender,type);
    }
    /** The possessor's own parse keeps secure chat in step with their client; the body runs the command. */
    @WrapOperation(method={"performUnsignedChatCommand","performSignedChatCommand"},at=@At(value="INVOKE",target="Lnet/minecraft/commands/Commands;performCommand(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)V"))
    private void heroes$command(Commands commands,ParseResults<CommandSourceStack> parsed,String command,Operation<Void> original) {
        if(!PossessionService.command(player,parsed,command,asBody->original.call(commands,asBody,command)))original.call(commands,parsed,command);
    }
}
