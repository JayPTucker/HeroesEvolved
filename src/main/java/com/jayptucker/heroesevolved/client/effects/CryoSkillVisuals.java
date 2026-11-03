package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.UUID;

/** Short-lived, solid ice meshes driven by the actual six optional techniques. */
@EventBusSubscriber(modid=HeroesEvolved.MOD_ID,value=Dist.CLIENT)
public final class CryoSkillVisuals {
    private static final ResourceLocation ICE=ResourceLocation.withDefaultNamespace("textures/block/ice.png");
    private static final Deque<Visual> VISUALS=new ArrayDeque<>();
    private static ClientLevel world;
    private record Anchor(int id,UUID uuid) {}
    private record Visual(Kind kind,Vec3 from,Vec3 to,float size,long born,int life,
                          Anchor first,Anchor second) {}
    private CryoSkillVisuals() {}
    private static void level(ClientLevel next) {
        if(world!=next){world=next;VISUALS.clear();}
    }
    private static Anchor anchor(ClientLevel level,Vec3 point,boolean eye) {
        LivingEntity entity=level.getEntitiesOfClass(LivingEntity.class,new AABB(point,point).inflate(2),
                e->e.isAlive()&&(eye?e.getEyePosition():e.position()).distanceToSqr(point)<.8*.8).stream()
                .min(Comparator.comparingDouble(e->(eye?e.getEyePosition():e.position()).distanceToSqr(point))).orElse(null);
        return entity==null?null:new Anchor(entity.getId(),entity.getUUID());
    }
    private static LivingEntity entity(Anchor anchor) {
        if(anchor==null||world==null)return null;
        var entity=world.getEntity(anchor.id);
        return entity instanceof LivingEntity living&&living.isAlive()&&living.getUUID().equals(anchor.uuid)?living:null;
    }
    public static void play(ClientLevel level,Kind kind,Vec3 from,Vec3 to,float size) {
        level(level);
        if(!Double.isFinite(from.lengthSqr())||!Double.isFinite(to.lengthSqr())||!Float.isFinite(size))return;
        Anchor first=kind==Kind.CRYO_ARMOR||kind==Kind.CRYO_COCOON?anchor(level,from,false)
                :kind==Kind.CRYO_CONSTELLATION?anchor(level,from,true):null;
        Anchor second=kind==Kind.CRYO_CONSTELLATION?anchor(level,to,true):null;
        VISUALS.removeIf(old->old.kind==kind&&(kind==Kind.CRYO_LANCE_FLIGHT
                ?old.to.distanceToSqr(from)<.08
                :kind!=Kind.CRYO_LOOM&&kind!=Kind.CRYO_REBOUND
                &&(first!=null&&old.first!=null
                    ?first.equals(old.first)&&(kind!=Kind.CRYO_CONSTELLATION||java.util.Objects.equals(second,old.second))
                    :old.from.distanceToSqr(from)<.09&&old.to.distanceToSqr(to)<(kind==Kind.CRYO_CONSTELLATION?.09:4))));
        if(kind==Kind.CRYO_ARMOR&&size<=0||kind==Kind.CRYO_COCOON&&size<0)return;
        while(VISUALS.size()>=160)VISUALS.removeFirst();
        int life=switch(kind){case CRYO_LANCE_FLIGHT->4;case CRYO_LANCE_FORGE->5;case CRYO_LOOM->18;case CRYO_REBOUND->12;default->8;};
        VISUALS.addLast(new Visual(kind,from,to,size,level.getGameTime(),life,first,second));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();level(mc.level);
        if(world==null||mc.isPaused())return;
        long now=world.getGameTime();
        VISUALS.removeIf(v->now-v.born>=v.life||v.first!=null&&entity(v.first)==null);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();level(mc.level);if(world==null)return;
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now=world.getGameTime()+partial;Vec3 camera=event.getCamera().getPosition();
        var buffers=mc.renderBuffers().bufferSource();
        VertexConsumer ice=buffers.getBuffer(RenderType.entityTranslucent(ICE));
        PoseStack pose=event.getPoseStack();
        boolean detail=mc.options.particles().get()!=ParticleStatus.MINIMAL;
        int count=0;
        for(Visual v:VISUALS) {
            LivingEntity first=entity(v.first),second=entity(v.second);
            Vec3 from=first==null?v.from:v.kind==Kind.CRYO_CONSTELLATION?first.getEyePosition(partial):first.getPosition(partial);
            Vec3 to=second==null?v.to:second.getEyePosition(partial);
            if(from.distanceToSqr(camera)>96*96||++count>(detail?160:72))continue;
            float age=(float)(now-v.born),alpha=(float)Math.clamp((v.life-age)/3,0,1);
            int light=world.hasChunkAt(BlockPos.containing(from))?LevelRenderer.getLightColor(world,BlockPos.containing(from)):0xF000F0;
            pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
            switch(v.kind) {
                case CRYO_LANCE_FORGE -> {
                    Vec3 direction=to.subtract(from).normalize();if(direction.lengthSqr()<.001)direction=new Vec3(0,0,1);
                    float growth=Math.clamp(v.size,0,1);
                    lance(ice,pose,from, direction,.35F+growth*1.35F,.045F+growth*.045F,alpha*.8F,light);
                    for(int i=0;i<3;i++) {
                        double angle=now*.25+i*Math.PI*2/3;
                        Vec3 side=side(direction),up=direction.cross(side);
                        Vec3 p=from.add(direction.scale(.45+growth*.4)).add(side.scale(Math.cos(angle)*.2)).add(up.scale(Math.sin(angle)*.2));
                        crystal(ice,pose,p,direction,.045F,.18F,alpha*.8F,i,light);
                    }
                }
                case CRYO_LANCE_FLIGHT -> {
                    Vec3 direction=to.subtract(from).normalize();
                    float scale=Math.clamp(v.size,.25F,1.5F),length=1.6F*scale;
                    lance(ice,pose,to.subtract(direction.scale(length)),direction,length,Math.max(.035F,.085F*scale),alpha*.9F,light);
                }
                case CRYO_ARMOR -> armor(ice,pose,from,Math.clamp(Math.round(v.size),0,6),now,alpha,light);
                case CRYO_SNAP -> snap(ice,pose,from,Math.clamp(v.size,.15F,8),age,alpha,light,detail);
                case CRYO_REBOUND -> {
                    Vec3 axis=to.subtract(from).normalize(),across=side(axis);
                    for(int i=0;i<Math.clamp((int)v.size,1,6);i++) {
                        double spread=(i-(Math.clamp((int)v.size,1,6)-1)/2.0)*.14;
                        Vec3 p=from.add(across.scale(spread*(1+age*.12))).add(axis.scale(age*.06));
                        crystal(ice,pose,p,axis.add(across.scale(spread)).normalize(),.12F,.45F,alpha*.65F,i,light);
                    }
                }
                case CRYO_LOOM -> loom(ice,pose,from,to,v.size,age,alpha,light);
                case CRYO_COCOON -> {
                    float height=first==null?(float)Math.clamp(to.y-from.y+.15,.8,5):first.getBbHeight()+.22F;
                    float radius=first==null?.55F:first.getBbWidth()*.62F+.18F;
                    cocoon(ice,pose,from,radius,height,Math.clamp(v.size,0,1),now,alpha,light);
                }
                case CRYO_CONSTELLATION -> constellation(ice,pose,from,to,Math.clamp(v.size,0,1),now,alpha,light,detail);
                default -> { }
            }
            pose.popPose();
        }
        buffers.endBatch(RenderType.entityTranslucent(ICE));
    }
    private static Vec3 side(Vec3 axis) {
        Vec3 v=axis.cross(Math.abs(axis.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0));
        return v.lengthSqr()<.001?new Vec3(1,0,0):v.normalize();
    }
    private static void crystal(VertexConsumer out,PoseStack pose,Vec3 at,Vec3 axis,float radius,float height,float alpha,int seed,int light) {
        if(axis.lengthSqr()<.0001)return;
        axis=axis.normalize();pose.pushPose();pose.translate(at.x,at.y,at.z);
        pose.mulPose(new org.joml.Quaternionf().rotationTo(0,1,0,(float)axis.x,(float)axis.y,(float)axis.z));
        CryoMeshVisuals.prism(out,pose.last(),radius,height,alpha,seed,light);pose.popPose();
    }
    private static void lance(VertexConsumer out,PoseStack pose,Vec3 from,Vec3 axis,float length,float radius,float alpha,int light) {
        if(axis.lengthSqr()<.001)return;
        axis=axis.normalize();
        crystal(out,pose,from,axis,radius*.5F,length*.8F,alpha,19,light);
        crystal(out,pose,from.add(axis.scale(length*.5)),axis,radius*2.2F,length*.5F,alpha,7,light);
        Vec3 side=side(axis),up=axis.cross(side);
        for(int i=0;i<3;i++) {
            double a=i*Math.PI*2/3;
            Vec3 fin=side.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            crystal(out,pose,from.add(axis.scale(length*.33)),axis.scale(.5).add(fin).normalize(),radius*.65F,length*.28F,alpha*.8F,i,light);
        }
    }
    private static void armor(VertexConsumer out,PoseStack pose,Vec3 feet,int plates,double now,float alpha,int light) {
        for(int i=0;i<plates;i++) {
            double a=now*.045+i*Math.PI/3;
            Vec3 radial=new Vec3(Math.cos(a),0,Math.sin(a));
            Vec3 at=feet.add(radial.scale(.76)).add(0,.92+Math.sin(now*.08+i)*.1,0);
            pose.pushPose();pose.translate(at.x,at.y,at.z);pose.mulPose(Axis.YP.rotation((float)(-a+Math.PI/2)));
            plate(out,pose.last(),.23F,.43F,alpha*.8F,i,light);pose.popPose();
            crystal(out,pose,at.add(0,.35,0),radial.add(0,.7,0).normalize(),.06F,.25F,alpha*.6F,i,light);
        }
    }
    /** A closed beveled plate has an outer ridge, a back and eight visible edge faces. */
    private static void plate(VertexConsumer out,PoseStack.Pose pose,float width,float height,float alpha,int seed,int light) {
        Vec3 front=new Vec3(0,0,.15),back=new Vec3(0,0,-.055);
        for(int i=0;i<8;i++) {
            double a=i*Math.PI/4,b=a+Math.PI/4;
            Vec3 l=new Vec3(Math.cos(a)*width,Math.sin(a)*height,.015),r=new Vec3(Math.cos(b)*width,Math.sin(b)*height,.015);
            Vec3 bl=l.add(0,0,-.07),br=r.add(0,0,-.07);
            quad(out,pose,l,r,front,front,.66F+.3F*(float)(.5+.5*Math.cos(a+seed)),alpha,light);
            quad(out,pose,br,bl,back,back,.64F,alpha*.8F,light);
            quad(out,pose,r,l,bl,br,.88F,alpha,light);
        }
    }
    private static void snap(VertexConsumer out,PoseStack pose,Vec3 center,float radius,float age,float alpha,int light,boolean detail) {
        int pieces=detail?32:16;
        double rise=.06+Math.sin(Math.min(1,age/8)*Math.PI)*.18;
        for(int i=0;i<pieces;i++) {
            double a=i*Math.PI*2/pieces,b=(i+1)*Math.PI*2/pieces;
            Vec3 al=center.add(Math.cos(a)*Math.max(0,radius-.22),.025,Math.sin(a)*Math.max(0,radius-.22));
            Vec3 ar=center.add(Math.cos(b)*Math.max(0,radius-.22),.025,Math.sin(b)*Math.max(0,radius-.22));
            Vec3 br=center.add(Math.cos(b)*(radius+.1),rise,Math.sin(b)*(radius+.1));
            Vec3 bl=center.add(Math.cos(a)*(radius+.1),rise,Math.sin(a)*(radius+.1));
            quad(out,pose.last(),al,ar,br,bl,.95F,alpha*.65F,light);
            quad(out,pose.last(),bl,br,br.add(0,-.1,0),bl.add(0,-.1,0),.7F,alpha*.75F,light);
            if(i%2==0)crystal(out,pose,center.add(Math.cos(a)*radius,0,Math.sin(a)*radius),
                    new Vec3(Math.cos(a)*.28,1,Math.sin(a)*.28),.08F,.2F+(i%3)*.07F,alpha*.85F,i,light);
        }
    }
    private static void loom(VertexConsumer out,PoseStack pose,Vec3 from,Vec3 to,float width,float age,float alpha,int light) {
        Vec3 axis=to.subtract(from);if(axis.lengthSqr()<.001)return;
        Vec3 across=side(axis.normalize());
        for(int sign:new int[]{-1,1}) {
            Vec3 offset=across.scale(sign*Math.clamp(width,.5,3)*.3);
            crystal(out,pose,from.add(offset),axis,.045F,(float)Math.min(3,axis.length()),alpha*.75F,sign,light);
        }
        Vec3 needle=from.lerp(to,Math.clamp(age/8,0,1)).add(0,.12,0);
        crystal(out,pose,needle,axis,.09F,.42F,alpha,19,light);
    }
    private static void cocoon(VertexConsumer out,PoseStack pose,Vec3 feet,float radius,float height,float pressure,double now,float alpha,int light) {
        pose.pushPose();pose.translate(feet.x,feet.y,feet.z);
        FrozenVisuals.shell(out,pose.last(),radius*.96F,radius*.96F,height*.98F,.15F,alpha*.11F,light);
        FrozenVisuals.shell(out,pose.last(),radius,radius,height,0,alpha*.27F,light);
        for(int i=0;i<6;i++) {
            double a=i*Math.PI/3;
            Vec3 base=new Vec3(Math.cos(a)*radius,0,Math.sin(a)*radius);
            crystal(out,pose,base,new Vec3(-Math.cos(a)*.12,1,-Math.sin(a)*.12),.09F,height*(.9F+(i%2)*.18F),alpha*.68F,i,light);
        }
        // The reservoir visibly fills from the feet upward as impacts are absorbed.
        for(int i=0;i<1+(int)(pressure*5);i++) {
            double a=now*.045+i*Math.PI*2/Math.max(1,1+(int)(pressure*5));
            Vec3 p=new Vec3(Math.cos(a)*radius*1.1,height*(.16+pressure*.55),Math.sin(a)*radius*1.1);
            crystal(out,pose,p,new Vec3(0,1,0),.07F,.2F,alpha*.9F,i,light);
        }
        pose.popPose();
    }
    private static void constellation(VertexConsumer out,PoseStack pose,Vec3 from,Vec3 to,float intensity,double now,float alpha,int light,boolean detail) {
        Vec3 a=from.add(0,.45,0),b=to.add(0,.45,0),delta=b.subtract(a);
        int pieces=detail?8:4;
        Vec3 previous=a;
        for(int i=1;i<=pieces;i++) {
            double t=i/(double)pieces;
            Vec3 next=a.lerp(b,t).add(0,Math.sin(t*Math.PI)*(.2+intensity*.2),0);
            Vec3 segment=next.subtract(previous);
            if(segment.lengthSqr()>.0001)crystal(out,pose,previous,segment,.018F+intensity*.018F,(float)segment.length(),alpha*.65F,i,light);
            previous=next;
        }
        for(Vec3 p:new Vec3[]{a,b}) {
            pose.pushPose();pose.translate(p.x,p.y+Math.sin(now*.08)*.05,p.z);pose.mulPose(Axis.YP.rotation((float)(now*.06)));
            crystal(out,pose,new Vec3(0,-.19,0),new Vec3(0,1,0),.15F,.4F,alpha*.88F,2,light);
            for(int i=0;i<4;i++) {
                double angle=i*Math.PI/2;
                crystal(out,pose,Vec3.ZERO,new Vec3(Math.cos(angle),.12,Math.sin(angle)),.055F,.27F,alpha*.76F,i,light);
            }
            pose.popPose();
        }
    }
    private static void quad(VertexConsumer out,PoseStack.Pose pose,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float shade,float alpha,int light) {
        CryoMeshVisuals.quad(out,pose,a,b,c,d,shade,alpha,light);
    }
}
