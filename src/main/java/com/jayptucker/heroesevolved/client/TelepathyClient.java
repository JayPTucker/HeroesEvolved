package com.jayptucker.heroesevolved.client;
import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid=HeroesEvolved.MOD_ID,value=Dist.CLIENT)
public final class TelepathyClient {
    private static MindStatePayload state=new MindStatePayload(0,-1,0,0,0,0,0,0);
    private static long seen; private static Entity previous; private static int previousRole;
    /** The possessor's half-typed line as the possessed player sees it, and the last line this possessor sent. */
    private static String typing="", sentTyping="";
    public static void typed(String text){typing=text;}
    public static int bodyId(){return role()==2?state.entity():Minecraft.getInstance().player.getId();}
    public static int role(){return state.role();}
    public static boolean immobilized(){return role()==1||role()==2||role()==4||role()==3&&state.focus()==1;}
    public static void update(MindStatePayload p){
        Minecraft mc=Minecraft.getInstance();
        if(p.role()==5||p.role()==6){mc.setScreen(new Choice(p.role()==6));return;}
        previousRole=role();state=p;seen=mc.level==null?0:mc.level.getGameTime();
        if(p.role()!=3)typing="";
        if(p.role()==0 && previousRole!=0){if(mc.player!=null)mc.setCameraEntity(previous==null||previous.level()!=mc.level?mc.player:previous);previous=null;mc.setScreen(null);ClientMomentum.clear();ClientResonance.clear();ClientClarity.clear();ClientStrain.clear();ClientDensity.clear();ClientRegenerationState.clear();}
        if((p.role()==2||p.role()==4)&&mc.player!=null&&mc.level!=null){
            Entity target=mc.level.getEntity(p.entity());
            if(target!=null){if(previous==null){previous=mc.getCameraEntity();mc.player.setYRot(target.getYRot());mc.player.setXRot(target.getXRot());}mc.setCameraEntity(target);}
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Pre e){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null){state=new MindStatePayload(0,-1,0,0,0,0,0,0);previous=null;return;}
        if(immobilized()&&mc.level.getGameTime()-seen>40){update(new MindStatePayload(0,-1,0,0,0,0,0,0));return;}
        if(role()==1||role()==2||role()==4)mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        if(role()==2){
            Entity body=mc.level.getEntity(state.entity());if(body!=null){body.setYRot(mc.player.getYRot());body.setXRot(mc.player.getXRot());}
            boolean input=mc.screen==null;
            PacketDistributor.sendToServer(new MindInputPayload(0,mc.options.keySprint.isDown()?1:0,0,input?axis(mc.options.keyUp.isDown(),mc.options.keyDown.isDown()):0,input?axis(mc.options.keyLeft.isDown(),mc.options.keyRight.isDown()):0,mc.player.getYRot(),mc.player.getXRot(),input&&mc.options.keyJump.isDown(),input&&mc.options.keyShift.isDown()));
            // A possessed player watches their own chat box fill with words they did not type.
            String line=body instanceof net.minecraft.world.entity.player.Player&&mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen chat?chatLine(chat):"";
            if(!line.equals(sentTyping)){sentTyping=line;PacketDistributor.sendToServer(new MindTypingPayload(line));}
        }else sentTyping="";
    }
    private static String chatLine(Screen chat){
        String line=chat.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance).map(box->((net.minecraft.client.gui.components.EditBox)box).getValue()).findFirst().orElse("");
        return line.length()>MindTypingPayload.MAX_LENGTH?line.substring(0,MindTypingPayload.MAX_LENGTH):line;
    }
    private static float axis(boolean a,boolean b){return (a?1:0)-(b?1:0);}
    @SubscribeEvent public static void movement(MovementInputUpdateEvent e){if(immobilized()){e.getInput().forwardImpulse=0;e.getInput().leftImpulse=0;e.getInput().jumping=false;e.getInput().shiftKeyDown=false;}}
    @SubscribeEvent public static void key(InputEvent.Key e){
        if(e.getAction()!=GLFW.GLFW_PRESS)return;
        if(role()==3){int cue=switch(e.getKey()){case GLFW.GLFW_KEY_UP->0;case GLFW.GLFW_KEY_RIGHT->1;case GLFW.GLFW_KEY_DOWN->2;case GLFW.GLFW_KEY_LEFT->3;default->-1;};if(cue>=0)PacketDistributor.sendToServer(new MindInputPayload(1,cue,state.nonce(),0,0,0,0,false,false));}
        else if((role()==1||role()==2||role()==4)&&e.getKey()==GLFW.GLFW_KEY_ESCAPE)PacketDistributor.sendToServer(new MindInputPayload(2,0,0,0,0,0,0,false,false));
    }
    @SubscribeEvent public static void attack(InputEvent.InteractionKeyMappingTriggered e){
        Minecraft mc=Minecraft.getInstance();
        if(role()==2&&mc.level!=null&&mc.level.getEntity(state.entity()) instanceof net.minecraft.world.entity.Mob){
            e.setCanceled(true);e.setSwingHand(false);
            if(e.isAttack())PacketDistributor.sendToServer(new MindInputPayload(6,0,0,0,0,mc.player.getYRot(),mc.player.getXRot(),false,false));
        }
        if(role()==1||role()==4||role()==3&&state.focus()==1){e.setCanceled(true);e.setSwingHand(false);}
    }
    public static void render(GuiGraphics g,net.minecraft.client.DeltaTracker delta){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        int w=g.guiWidth(),h=g.guiHeight(),cx=w/2;
        if(role()>=1&&role()<=4){
            for(int i=0;i<12;i++){int c=((30-i*2)<<24)|0x642591;g.fill(i*3,0,i*3+3,h,c);g.fill(w-i*3-3,0,w-i*3,h,c);}
            String title=role()==3?(state.focus()==1?"YOUR MIND IS BEING CONTROLLED":"SOMEONE IS ENTERING YOUR MIND"):role()==1?"ENTERING THEIR MIND":role()==4?"SHARED SIGHT":"PUPPET — THEIR BODY, YOUR WILL";
            int titleY=role()==3?h/2-40:32;
            g.drawCenteredString(mc.font,title,cx,titleY,0xEDCBFF);
            g.drawCenteredString(mc.font,String.format(java.util.Locale.ROOT,"%.1f seconds",state.ticks()/20f),cx,titleY+14,0xC4ABD8);
            if(role()==3){
                String[] names={"UP ↑","RIGHT →","DOWN ↓","LEFT ←"};
                g.fill(cx-100,h/2+18,cx+100,h/2+83,0xD0181028);
                g.drawCenteredString(mc.font,"PRESS "+names[Math.clamp(state.cue(),0,3)]+" TO FIGHT BACK",cx,h/2+29,0xFFFFFF);
                g.fill(cx-85,h/2+48,cx+85,h/2+55,0xFF382342);g.fill(cx-85,h/2+48,cx-85+(170*state.resistance()/100),h/2+55,0xFFDBB4FF);
                g.drawCenteredString(mc.font,"Break free: "+state.resistance()+"%",cx,h/2+64,0xDCBDEF);
            }else g.drawCenteredString(mc.font,"Esc · return to yourself",cx,60,0xB59CCB);
            if(role()==3&&!typing.isEmpty()){
                // Where their own chat box would be; above it when they have it open themselves.
                int y=h-(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen?26:12);
                String shown=mc.font.plainSubstrByWidth(typing,w-16,true)+(net.minecraft.Util.getMillis()/400%2==0?"_":"");
                g.fill(2,y-2,Math.min(w-2,mc.font.width(shown)+8),y+10,0x90180C28);
                g.drawString(mc.font,shown,4,y,0xEDCBFF);
            }
        }else if(state.focus()>0||state.strain()>0)g.drawCenteredString(mc.font,"Mind Thread "+state.focus()+"  ·  Strain "+state.strain(),cx,h-76,0xDCBDEF);
    }
    private static final class Choice extends Screen {
        final boolean illusion;
        Choice(boolean i){super(Component.literal(i?"Plant an Illusion":"Plant a Suggestion"));illusion=i;}
        protected void init(){String[] choices=illusion?new String[]{"Dream Double · a copy of you","Approaching Zombie","Approaching Creeper","False Chest","False Shelter"}:new String[]{"Look There","Come Here","Leave","Forget Me","Defend Me"};for(int i=0;i<choices.length;i++){final int choice=i;addRenderableWidget(Button.builder(Component.literal(choices[i]),b->{PacketDistributor.sendToServer(new MindInputPayload(illusion?5:3,choice,0,0,0,0,0,false,false));onClose();}).bounds(width/2-110,height/2-65+i*25,220,20).build());}}
        public void render(GuiGraphics g,int x,int y,float pt){renderBackground(g,x,y,pt);g.drawCenteredString(font,title,width/2,height/2-88,0xDCBDEF);super.render(g,x,y,pt);}
        public boolean isPauseScreen(){return false;}
    }
    @EventBusSubscriber(modid=HeroesEvolved.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Hud {
        @SubscribeEvent public static void register(RegisterGuiLayersEvent e){e.registerAboveAll(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID,"mind"),TelepathyClient::render);}
    }
}
