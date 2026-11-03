package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.*;
import com.jayptucker.heroesevolved.client.effects.AbilityEffectRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Render-only hallucinations: never inserted into the world entity or block collections. */
@EventBusSubscriber(modid=HeroesEvolved.MOD_ID,value=Dist.CLIENT)
public final class IllusionClient {
    private record Vision(IllusionPayload data,LivingEntity actor,long start){}
    private static final Map<Integer,Vision> VISIONS=new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel world;
    public static void accept(IllusionPayload p){
        Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;
        if(world!=mc.level){VISIONS.clear();world=mc.level;}
        if(p.kind()<0){Vision v=VISIONS.remove(p.id());if(v!=null)burst(position(v,mc.level.getGameTime()));return;}
        if(VISIONS.size()>=8)VISIONS.clear();
        LivingEntity actor=null;
        if(p.kind()==0){Entity source=mc.level.getEntity(p.source());if(source instanceof net.minecraft.world.entity.player.Player player)actor=new RemotePlayer(mc.level,player.getGameProfile());}
        if(p.kind()==1||p.kind()==0&&actor==null)actor=EntityType.ZOMBIE.create(mc.level);
        if(p.kind()==2)actor=EntityType.CREEPER.create(mc.level);
        if(actor!=null){if(p.kind()==0&&mc.level.getEntity(p.source()) instanceof LivingEntity source)for(EquipmentSlot slot:EquipmentSlot.values())actor.setItemSlot(slot,source.getItemBySlot(slot).copy());actor.setPos(p.origin());actor.setYRot(180);}
        VISIONS.put(p.id(),new Vision(p,actor,mc.level.getGameTime()));
    }
    private static Vec3 position(Vision v,double now){return v.data.kind()<3?v.data.origin().lerp(v.data.end(),Math.clamp((now-v.start)/v.data.ticks(),0,1)):v.data.origin();}
    private static AABB box(Vision v,long now){Vec3 p=position(v,now);return v.data.kind()==4?new AABB(p.x-1.5,p.y,p.z-1.5,p.x+1.5,p.y+3,p.z+1.5):new AABB(p.x-.5,p.y,p.z-.5,p.x+.5,p.y+(v.actor!=null?1.9:1),p.z+.5);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){Minecraft mc=Minecraft.getInstance();if(world!=mc.level){VISIONS.clear();world=mc.level;}if(mc.level==null)return;for(Vision v:List.copyOf(VISIONS.values()))if(mc.level.getGameTime()-v.start>v.data.ticks()){VISIONS.remove(v.data.id());burst(position(v,mc.level.getGameTime()));}}
    @SubscribeEvent public static void interact(InputEvent.InteractionKeyMappingTriggered e){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        Vec3 eye=mc.player.getEyePosition(),end=eye.add(mc.player.getLookAngle().scale(5));
        for(Vision v:List.copyOf(VISIONS.values()))if(box(v,mc.level.getGameTime()).clip(eye,end).isPresent()){
            e.setCanceled(true);e.setSwingHand(false);PacketDistributor.sendToServer(new MindInputPayload(4,v.data.id(),0,0,0,0,0,false,false));VISIONS.remove(v.data.id());burst(position(v,mc.level.getGameTime()));break;
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;
        var pose=e.getPoseStack();var buffers=mc.renderBuffers().bufferSource();Vec3 camera=e.getCamera().getPosition();
        for(Vision v:VISIONS.values()){
            Vec3 p=position(v,mc.level.getGameTime()+e.getPartialTick().getGameTimeDeltaPartialTick(false));
            if(p.distanceToSqr(camera)>64*64)continue;
            pose.pushPose();pose.translate(p.x-camera.x,p.y-camera.y,p.z-camera.z);
            if(v.actor!=null){
                Vec3 direction=v.data.end().subtract(v.data.origin());float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));
                v.actor.setPos(p);v.actor.setYRot(yaw);v.actor.yBodyRot=yaw;v.actor.yHeadRot=yaw;v.actor.yRotO=yaw;v.actor.yBodyRotO=yaw;v.actor.yHeadRotO=yaw;v.actor.walkAnimation.update(.55f,.4f);v.actor.tickCount=(int)(mc.level.getGameTime()-v.start);
                mc.getEntityRenderDispatcher().render(v.actor,0,0,0,yaw,0,pose,buffers,0xF000F0);
            }else if(v.data.kind()==3){
                pose.translate(0,.5,0);mc.getItemRenderer().renderStatic(new net.minecraft.world.item.ItemStack(Blocks.CHEST),net.minecraft.world.item.ItemDisplayContext.NONE,0xF000F0,OverlayTexture.NO_OVERLAY,pose,buffers,mc.level,0);
            }else {
                for(int x=-1;x<=1;x++)for(int y=0;y<=2;y++)for(int z=-1;z<=1;z++){
                    if(y<2&&x==0&&(z==0||z==-1))continue;
                    if(y<2&&Math.abs(x)!=1&&Math.abs(z)!=1)continue;
                    pose.pushPose();pose.translate(x-.5,y,z-.5);
                    mc.getBlockRenderer().renderSingleBlock((y==2?Blocks.OAK_PLANKS:Blocks.COBBLESTONE).defaultBlockState(),pose,buffers,0xF000F0,OverlayTexture.NO_OVERLAY);pose.popPose();
                }
            }
            pose.popPose();
        }
    }
    private static void burst(Vec3 p){
        Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;
        for(int i=0;i<32;i++)mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,p.x,p.y+1,p.z,(mc.level.random.nextDouble()-.5)*1.5,(mc.level.random.nextDouble()-.5)*1.5,(mc.level.random.nextDouble()-.5)*1.5);
        mc.level.playLocalSound(p.x,p.y,p.z,net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,net.minecraft.sounds.SoundSource.PLAYERS,.7f,.6f,false);
    }
    private IllusionClient(){}
}
