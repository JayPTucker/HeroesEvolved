package com.jayptucker.heroesevolved.entity.client;

import com.jayptucker.heroesevolved.wither.RiftWitherEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** A hinged skeletal cage. The three heads are drawn into its chest in sequence, rather than simply fading out. */
public final class RiftWitherRenderer extends MobRenderer<RiftWitherEntity,RiftWitherRenderer.CageModel> {
    public RiftWitherRenderer(EntityRendererProvider.Context context){super(context,new CageModel(),1.2F);}
    @Override public ResourceLocation getTextureLocation(RiftWitherEntity entity){return ResourceLocation.fromNamespaceAndPath("heroesevolved","textures/entity/rift_wither.png");}
    @Override protected void scale(RiftWitherEntity e,PoseStack poses,float partial){
        float age=e.age()+partial;
        float size=e.mode()==1?.3F+.7F*Mth.clamp(age/25,0,1):1;
        if(e.mode()==1)size*=1-.97F*ease(103,120,age);
        if(e.dying())size*=1-.95F*ease(24,60,age);
        poses.scale(size*1.5F,size*1.5F,size*1.5F);
    }
    private static float ease(float a,float b,float x){float t=Mth.clamp((x-a)/(b-a),0,1);return t*t*(3-2*t);}
    public static final class CageModel extends HierarchicalModel<RiftWitherEntity> {
        private final ModelPart root,left,right,core;
        private final ModelPart[] heads=new ModelPart[3];
        CageModel(){
            MeshDefinition mesh=new MeshDefinition();PartDefinition r=mesh.getRoot();
            r.addOrReplaceChild("spine",CubeListBuilder.create().texOffs(0,0).addBox(-2,-8,1,4,31,4),PartPose.ZERO);
            r.addOrReplaceChild("shoulders",CubeListBuilder.create().texOffs(20,0).addBox(-17,-7,0,34,4,4),PartPose.ZERO);
            for(int i=0;i<3;i++) {
                float x=i==0?0:i==1?-14:14,y=i==0?-19:-11;
                PartDefinition head=r.addOrReplaceChild("head"+i,CubeListBuilder.create().texOffs(0,40).addBox(-5,-5,-5,10,10,9)
                        .texOffs(42,40).addBox(-3,5,-4,6,3,6),PartPose.offset(x,y,0));
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
            root=LayerDefinition.create(mesh,128,128).bakeRoot();left=root.getChild("left");right=root.getChild("right");core=root.getChild("core");
            for(int i=0;i<3;i++)heads[i]=root.getChild("head"+i);
        }
        @Override public ModelPart root(){return root;}
        @Override public void setupAnim(RiftWitherEntity e,float walk,float amount,float age,float yaw,float pitch){
            root.getAllParts().forEach(ModelPart::resetPose);
            float t=e.age()+(age-e.tickCount),open=e.mode()==1?ease(30,63,t):e.phase()==1?.08F:e.phase()==2?.5F:.9F;
            if(e.dying())open=1.3F;
            left.yRot=-open*1.35F;right.yRot=open*1.35F;
            root.y=Mth.sin(age*.055F)*.6F;
            for(int i=0;i<3;i++) {
                ModelPart h=heads[i];h.yRot=yaw*.01745F+Mth.sin(age*.04F+i)*.1F;h.xRot=pitch*.01745F;
                if(e.mode()==1||e.dying()) {
                    float swallow=e.mode()==1?ease(68+i*12,86+i*12,t):ease(i*6,24+i*6,t);
                    h.x*=1-swallow;h.y=Mth.lerp(swallow,h.y,2);h.z=-2*swallow;
                    h.xRot+=swallow*1.3F;h.yRot+=swallow*4;
                    h.xScale=h.yScale=h.zScale=1-swallow*.98F;
                }
                if(e.phase()>1&&e.mode()!=1) {
                    h.z+=Mth.sin(age*.05F+i*2)*(.5F+e.phase());
                    if(i>0){h.x*=1+.15F*(e.phase()-1);h.y-=2*e.phase()+Mth.sin(age*.04F+i)*1.5F;}
                }
            }
            core.xRot=age*.03F;core.yRot=age*.05F;core.zRot=age*.02F;
        }
    }
}
