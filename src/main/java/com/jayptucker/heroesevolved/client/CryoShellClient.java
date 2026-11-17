package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.effects.FrozenVisuals;
import com.jayptucker.heroesevolved.network.CryoShellInputPayload;
import com.jayptucker.heroesevolved.network.CryoShellStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.Map;

/** Trapped players retain a readable way to fight the shell, including when ordinary attacks are blocked. */
@EventBusSubscriber(modid=HeroesEvolved.MOD_ID,value=Dist.CLIENT)
public final class CryoShellClient {
    private static final Map<Integer,Integer> CRACKS=new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static boolean jump,attack;
    private CryoShellClient(){}
    public static void update(CryoShellStatePayload payload){
        checkWorld();
        if(payload.active())CRACKS.put(payload.entityId(),Math.clamp(payload.cracks(),0,100));
        else CRACKS.remove(payload.entityId());
    }
    public static int cracks(int entityId){return CRACKS.getOrDefault(entityId,0);}
    public static void clear(int entityId){CRACKS.remove(entityId);}
    private static void checkWorld(){
        var next=Minecraft.getInstance().level;
        if(world!=next){world=next;CRACKS.clear();jump=attack=false;}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        checkWorld();var mc=Minecraft.getInstance();
        if(mc.player==null||mc.screen!=null||mc.isPaused()){jump=attack=false;return;}
        boolean nextJump=mc.options.keyJump.isDown(),nextAttack=mc.options.keyAttack.isDown();
        if(FrozenVisuals.isFrozen(mc.player)&&((nextJump&&!jump)||(nextAttack&&!attack)))
            PacketDistributor.sendToServer(new CryoShellInputPayload(true));
        jump=nextJump;attack=nextAttack;
    }
    @EventBusSubscriber(modid=HeroesEvolved.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Hud {
        @SubscribeEvent public static void register(RegisterGuiLayersEvent event){
            event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID,"cryo_shell"),(gui,delta)->{
                var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui||!FrozenVisuals.isFrozen(mc.player))return;
                int x=gui.guiWidth()/2,y=gui.guiHeight()/2+34,progress=cracks(mc.player.getId());
                // The cracks as a small ring just under the crosshair, like the ability HUD's, and how to crack them under it,
                // both clear of the action bar's messages.
                gui.drawCenteredString(mc.font,Component.translatable("cryo.heroesevolved.prison_struggle"),x,y+6,0xCDEFFF);
                com.jayptucker.heroesevolved.gui.HudGauges.at(gui,x-com.jayptucker.heroesevolved.gui.HudGauges.SIZE/2,y-26,
                        com.jayptucker.heroesevolved.gui.HudGauges.Gauge.ring(progress/100F,0xFFBCE9FF).middle(progress+"%").label("CRACKS"));
            });
        }
    }
}
