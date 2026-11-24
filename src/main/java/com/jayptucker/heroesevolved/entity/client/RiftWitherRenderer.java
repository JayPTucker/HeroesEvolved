package com.jayptucker.heroesevolved.entity.client;

import com.jayptucker.heroesevolved.wither.RiftWitherEntity;
import com.jayptucker.heroesevolved.wither.WitherGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** A hinged skeletal cage. The three heads are drawn into its chest in sequence, rather than simply fading out. */
public final class RiftWitherRenderer extends MobRenderer<RiftWitherEntity,RiftWitherRenderer.CageModel> {
    public RiftWitherRenderer(EntityRendererProvider.Context context){
        super(context,new CageModel(),2);
        addLayer(new RenderLayer<RiftWitherEntity,CageModel>(this){
            @Override public void render(PoseStack poses,MultiBufferSource buffers,int light,RiftWitherEntity e,float walk,float amount,float partial,float age,float yaw,float pitch){
                int color=e.phase()==3?0xFFFF9A88:e.phase()==2?0xFFB9A5FF:0xFF9EFFE1;
                getParentModel().renderToBuffer(poses,buffers.getBuffer(RenderType.eyes(ResourceLocation.fromNamespaceAndPath("heroesevolved","textures/entity/rift_wither_glow.png"))),15728880,OverlayTexture.NO_OVERLAY,color);
            }
        });
    }
    @Override public ResourceLocation getTextureLocation(RiftWitherEntity entity){return ResourceLocation.fromNamespaceAndPath("heroesevolved","textures/entity/rift_wither.png");}
    @Override protected void scale(RiftWitherEntity e,PoseStack poses,float partial){
        float age=e.age()+partial;
        float size=e.mode()==1?.3F+.7F*Mth.clamp(age/25,0,1):1;
        if(e.fighting()&&e.tickCount<28)size*=.2F+.8F*ease(0,28,e.tickCount+partial);
        if(e.mode()==1)size*=1-.97F*ease(103,120,age);
        if(e.dying())size*=1-.95F*ease(24,60,age);
        float scale=e.modelScale();poses.scale(size*scale,size*scale,size*scale);
    }
    private static float ease(float a,float b,float x){float t=Mth.clamp((x-a)/(b-a),0,1);return t*t*(3-2*t);}
    public static final class CageModel extends HierarchicalModel<RiftWitherEntity> {
        private final ModelPart root,left,right,core,tail;
        private final ModelPart[] heads=new ModelPart[3],jaws=new ModelPart[3];
        CageModel(){
            MeshDefinition mesh=new MeshDefinition();PartDefinition r=mesh.getRoot();
            r.addOrReplaceChild("spine",CubeListBuilder.create().texOffs(0,0).addBox(-2,-8,1,4,31,4),PartPose.ZERO);
            r.addOrReplaceChild("shoulders",CubeListBuilder.create().texOffs(20,0).addBox(-17,-7,0,34,4,4),PartPose.ZERO);
            r.addOrReplaceChild("neck",CubeListBuilder.create().texOffs(0,0).addBox(-1,-15,1,2,8,3),PartPose.ZERO);
            r.addOrReplaceChild("crown",CubeListBuilder.create().texOffs(50,5).addBox(-2,-13,1,4,6,3)
                    .addBox(-18,-12,1,3,8,3).addBox(15,-12,1,3,8,3),PartPose.ZERO);
            r.addOrReplaceChild("tail",CubeListBuilder.create().texOffs(0,4).addBox(-1,0,0,2,14,3)
                    .addBox(-3,8,0,6,2,3).addBox(-2,13,0,4,2,3),PartPose.offset(0,19,2));
            for(int i=0;i<3;i++) {
                float x=i==0?0:i==1?-14:14,y=i==0?-19:-11;
                PartDefinition head=r.addOrReplaceChild("head"+i,CubeListBuilder.create().texOffs(0,40).addBox(-5,-5,-5,10,9,9)
                        .texOffs(42,40).addBox(-5,0,-5.5F,2,4,2).addBox(3,0,-5.5F,2,4,2),PartPose.offset(x,y,0));
                head.addOrReplaceChild("jaw",CubeListBuilder.create().texOffs(42,40).addBox(-3,0,-3,6,3,6)
                        .texOffs(42,40).addBox(-3,-2,-3,1,2,1).addBox(2,-2,-3,1,2,1),PartPose.offset(0,4,-2));
                head.addOrReplaceChild("brow",CubeListBuilder.create().texOffs(0,65).addBox(-5,-2,-5.5F,10,2,1)
                        .texOffs(24,65).addBox(-3,1,-5.5F,2,1,1).addBox(1,1,-5.5F,2,1,1),PartPose.ZERO);
            }
            for(boolean l:new boolean[]{true,false}) {
                PartDefinition side=r.addOrReplaceChild(l?"left":"right",CubeListBuilder.create(),PartPose.offset(l?-2:2,1,0));
                int sign=l?-1:1;
                for(int j=0;j<4;j++) {
                    int width=9-j;
                    CubeListBuilder rib=CubeListBuilder.create().texOffs(50,j*10)
                            .addBox(sign<0?-width:0,j*4,0,width,2,3)
                            .addBox(sign<0?-width:width-2,j*4,-7,2,2,8)
                            .addBox(sign<0?-width:1,j*4,-8,width-1,2,2);
                    side.addOrReplaceChild("rib"+j,rib,PartPose.ZERO);
                }
            }
            r.addOrReplaceChild("core",CubeListBuilder.create().texOffs(90,0).addBox(-2,-2,-2,4,4,4),PartPose.offset(0,2,-2));
            root=LayerDefinition.create(mesh,128,128).bakeRoot();left=root.getChild("left");right=root.getChild("right");core=root.getChild("core");tail=root.getChild("tail");
            for(int i=0;i<3;i++){heads[i]=root.getChild("head"+i);jaws[i]=heads[i].getChild("jaw");}
        }
        @Override public ModelPart root(){return root;}
        @Override public void setupAnim(RiftWitherEntity e,float walk,float amount,float age,float yaw,float pitch){
            root.getAllParts().forEach(ModelPart::resetPose);
            float t=e.age()+(age-e.tickCount),open=e.mode()==1?ease(30,63,t):e.phase()==1?.08F:e.phase()==2?.5F:.9F;
            if(e.dying())open=1.3F;
            if(e.exposed())open=Math.max(open,1.05F);
            var action=e.action();float actionAge=e.actionAge()+(age-e.tickCount),charge=action==null?0:ease(0,action.windup,actionAge),strike=action==null?0:1-ease(action.windup+6,action.windup+22,actionAge);
            if(action!=null)switch(action){
                case VERDICT,TETHER -> open+=charge*.3F*strike;
                case LUNGE -> {open*=1-.75F*strike;root.xRot=.28F*charge*strike;}
                case SLAM -> {open+=charge*.5F*strike;root.xRot=-.3F*charge*strike;}
                case HORIZON -> open+=charge*.6F*strike;
                case BITE -> {root.xRot=.32F*charge*strike;heads[0].y+=charge*12*strike;}
                case RAKE -> {open+=.9F*charge*strike;root.yRot=(ease(action.windup-6,action.windup+8,actionAge)*1.6F-.8F)*strike;}
                default -> {}
            }
            left.yRot=-open*1.35F;right.yRot=open*1.35F;
            root.y=Mth.sin(age*.055F)*.6F;
            tail.xRot=1.25F+Mth.sin(age*.07F)*.12F;tail.zRot=Mth.sin(age*.06F)*.12F;
            for(int i=0;i<3;i++) {
                ModelPart h=heads[i];h.yRot=yaw*.01745F+Mth.sin(age*.04F+i)*.1F;h.xRot=pitch*.01745F;
                if(e.fighting()){
                    boolean side=i==e.sideHead()&&e.sideAge()<30;var pivot=WitherGeometry.head(e.position(),e.yBodyRot,i);var aim=(side?e.sideAim():e.aim()).subtract(pivot);
                    if(aim.lengthSqr()>.01&&(action!=null||side)){h.yRot=Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(-aim.x,aim.z))-e.yBodyRot)*Mth.DEG_TO_RAD;
                        h.xRot=(float)-Math.atan2(aim.y,Math.sqrt(aim.x*aim.x+aim.z*aim.z));}
                    jaws[i].xRot=.12F+(.45F+.15F*Mth.sin(age*.12F+i))*charge*strike;
                    if(side)jaws[i].xRot=.15F+.75F*ease(0,WitherGeometry.HEAD_WINDUP,e.sideAge())*(1-ease(WitherGeometry.HEAD_WINDUP+3,30,e.sideAge()));
                    if(action==WitherGeometry.Move.VERDICT&&actionAge>=action.windup)h.z+=1.5F*(1-ease(action.windup,action.windup+6,actionAge));
                    if(action==WitherGeometry.Move.BITE&&i==0){h.xRot+=.55F*charge*strike;jaws[i].xRot=.85F*charge*strike;}
                }
                if(e.mode()==1||e.dying()) {
                    float swallow=e.mode()==1?ease(68+i*12,86+i*12,t):ease(i*6,24+i*6,t);
                    h.x*=1-swallow;h.y=Mth.lerp(swallow,h.y,2);h.z=-2*swallow;
                    h.xRot+=swallow*1.3F;h.yRot+=swallow*4;
                    h.xScale=h.yScale=h.zScale=1-swallow*.98F;
                }
                if(e.phase()>1&&e.mode()!=1) {
                    h.z+=Mth.sin(age*.05F+i*2)*.25F;
                }
            }
            core.xRot=age*.03F;core.yRot=age*.05F;core.zRot=age*.02F;
        }
    }
}
