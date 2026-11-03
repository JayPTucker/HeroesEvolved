package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/** World-space ice has thickness, lit facets and rotating shard faces; it never billboards toward the camera. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class CryoMeshVisuals {
    private static final ResourceLocation ICE = ResourceLocation.withDefaultNamespace("textures/block/ice.png");
    private static final Deque<Crystal> CRYSTALS = new ArrayDeque<>();
    private static final Deque<Ribbon> RIBBONS = new ArrayDeque<>();
    private static final Map<BlockPos, Long> SCULPTED = new LinkedHashMap<>();
    private static ClientLevel world;
    private record Crystal(Vec3 origin, Vec3 axis, Vec3 velocity, float radius, float height,
                           long born, int lifetime, boolean shard, int seed) {}
    private record Ribbon(Vec3 from, Vec3 to, Vec3 control, float width, long born) {}
    private CryoMeshVisuals() {}
    private static void level(ClientLevel next) {
        if (world == next) return;
        world = next; CRYSTALS.clear(); RIBBONS.clear(); SCULPTED.clear();
    }
    /** The decoration is owned by an actual ice cell, so melting or mining it removes every facet. */
    public static void sculptCell(ClientLevel level, Vec3 base) {
        level(level);
        BlockPos pos = BlockPos.containing(base);
        if (!SCULPTED.containsKey(pos) && SCULPTED.size() >= 256) {
            var oldest = SCULPTED.keySet().iterator(); oldest.next(); oldest.remove();
        }
        SCULPTED.put(pos, level.getGameTime());
    }
    private static boolean ice(BlockPos pos) {
        var state = world.getBlockState(pos);
        return state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE);
    }
    public static void grow(ClientLevel level, Vec3 base, Vec3 tip, float radius) {
        level(level);
        if (base.distanceToSqr(tip) > 100) return;
        while (CRYSTALS.size() >= 180) CRYSTALS.removeFirst();
        Vec3 axis = tip.subtract(base);
        if (axis.lengthSqr() < .0001) axis = new Vec3(0, .2, 0);
        CRYSTALS.addLast(new Crystal(base, axis.normalize(), Vec3.ZERO, Math.clamp(radius, .07F, 1.2F),
                (float)Math.clamp(axis.length(), .2, 8), level.getGameTime(), 20, false, level.random.nextInt(10000)));
    }
    public static void shatter(ClientLevel level, Vec3 center, float radius, int count) {
        level(level);
        for (int i=0;i<Math.clamp(count, 3, 28);i++) {
            while (CRYSTALS.size() >= 180) CRYSTALS.removeFirst();
            double angle=i*2.399963;
            Vec3 direction=new Vec3(Math.cos(angle), .12+level.random.nextDouble()*.65, Math.sin(angle)).normalize();
            Vec3 from=center.add(direction.scale(Math.min(1, radius*.35)));
            Vec3 speed=direction.scale(.12+Math.min(3,radius)*.035).add(0,.09,0);
            CRYSTALS.addLast(new Crystal(from, direction, speed, .055F+level.random.nextFloat()*.12F,
                    .18F+level.random.nextFloat()*.34F, level.getGameTime(), 22+level.random.nextInt(10), true, i*319+17));
        }
    }
    public static void ribbon(ClientLevel level, Vec3 from, Vec3 to, float width) {
        level(level);
        double distance=from.distanceTo(to);
        if (distance < .025 || distance > 8) return;
        Vec3 direction=to.subtract(from), tangent=direction;
        Ribbon previous=RIBBONS.peekLast();
        if (previous!=null && previous.to.distanceToSqr(from)<1) tangent=direction.add(previous.to.subtract(previous.from)).normalize().scale(distance);
        Vec3 control=from.add(tangent.scale(.5));
        while(RIBBONS.size()>=160)RIBBONS.removeFirst();
        RIBBONS.addLast(new Ribbon(from, to, control, Math.clamp(width,.35F,3.5F),level.getGameTime()));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance(); level(mc.level);
        if(world==null || mc.isPaused())return;
        long now=world.getGameTime();
        for(Crystal crystal:CRYSTALS)if(crystal.shard && now-crystal.born==crystal.lifetime-5) {
            Vec3 p=position(crystal,now-crystal.born);
            world.addParticle(ParticleTypes.SNOWFLAKE,p.x,p.y,p.z,0,.025,0);
        }
        CRYSTALS.removeIf(c->now-c.born>=c.lifetime);
        RIBBONS.removeIf(r->now-r.born>=36);
        SCULPTED.entrySet().removeIf(entry -> now - entry.getValue() >= 600
                || now - entry.getValue() > 10 && (!world.hasChunkAt(entry.getKey()) || !ice(entry.getKey())));
    }
    private static Vec3 position(Crystal c,double age) {
        return c.shard?c.origin.add(c.velocity.scale(age*.85)).add(0,-.007*age*age,0):c.origin;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        var mc=Minecraft.getInstance();
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || mc.level==null)return;
        level(mc.level);
        var buffers=mc.renderBuffers().bufferSource();
        var ice=buffers.getBuffer(RenderType.entityTranslucent(ICE));
        var pose=event.getPoseStack(); Vec3 camera=event.getCamera().getPosition();
        double now=world.getGameTime()+event.getPartialTick().getGameTimeDeltaPartialTick(false);
        int limit=mc.options.particles().get()==ParticleStatus.MINIMAL?48:180,drawn=0;
        for(Crystal c:CRYSTALS) {
            if(++drawn>limit)break;
            double age=now-c.born; Vec3 at=position(c,age);
            if(at.distanceToSqr(camera)>96*96)continue;
            float growth=c.shard?1:(float)Math.clamp(age/7,0,1);
            float alpha=(float)Math.clamp((c.lifetime-age)/7,0,1)*.74F;
            pose.pushPose();pose.translate(at.x-camera.x,at.y-camera.y,at.z-camera.z);
            pose.mulPose(new org.joml.Quaternionf().rotationTo(0,1,0,(float)c.axis.x,(float)c.axis.y,(float)c.axis.z));
            if(c.shard){pose.mulPose(Axis.YP.rotationDegrees((float)(c.seed+age*11)));pose.mulPose(Axis.XP.rotationDegrees((float)(age*8)));}
            prism(ice,pose.last(),c.radius,c.height*growth,alpha,c.seed);
            pose.popPose();
        }
        for(Ribbon r:RIBBONS) {
            if(r.from.distanceToSqr(camera)>96*96)continue;
            float alpha=(float)Math.clamp((36-(now-r.born))/12,0,1)*.62F;
            pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
            strip(ice,pose.last(),r,alpha);
            pose.popPose();
        }
        int cells = 0, cellLimit = mc.options.particles().get() == ParticleStatus.MINIMAL ? 96 : 256;
        for (var entry : SCULPTED.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!world.hasChunkAt(pos) || !ice(pos) || Vec3.atCenterOf(pos).distanceToSqr(camera) > 80 * 80) continue;
            if (++cells > cellLimit) break;
            float alpha = (float)Math.clamp((now - entry.getValue()) / 7, 0, 1) * .72F;
            int light = LevelRenderer.getLightColor(world, pos.above());
            pose.pushPose(); pose.translate(pos.getX() + .5 - camera.x, pos.getY() + .5 - camera.y, pos.getZ() + .5 - camera.z);
            sculptedCell(ice, pose, pos, alpha, light);
            pose.popPose();
        }
        buffers.endBatch(RenderType.entityTranslucent(ICE));
    }
    private static boolean exposed(BlockPos pos, Direction face) {
        BlockPos adjacent = pos.relative(face);
        return world.hasChunkAt(adjacent) && !ice(adjacent) && !world.getBlockState(adjacent).canOcclude();
    }
    private static void sculptedCell(VertexConsumer out, PoseStack pose, BlockPos pos, float alpha, int light) {
        int seed = pos.hashCode();
        if (exposed(pos, Direction.UP)) {
            pose.pushPose(); pose.translate(0, .48, 0);
            prism(out, pose.last(), .16F, .26F + (seed & 3) * .035F, alpha, seed, light);
            pose.translate(.23, 0, -.12);
            prism(out, pose.last(), .075F, .18F, alpha * .8F, seed + 4, light);
            pose.translate(-.4, 0, .26);
            prism(out, pose.last(), .09F, .13F, alpha * .7F, seed + 7, light);
            pose.popPose();
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (!exposed(pos, side)) continue;
            Vec3 normal = Vec3.atLowerCornerOf(side.getNormal());
            Vec3 across = new Vec3(-normal.z, 0, normal.x);
            float offset = ((seed >>> side.get2DDataValue() & 3) - 1.5F) * .055F;
            Vec3 center = normal.scale(.505).add(across.scale(offset));
            Vec3 top = center.add(0, .4, 0).add(across.scale(.035));
            Vec3 left = center.subtract(across.scale(.11)).add(0, -.035, 0);
            Vec3 bottom = center.add(0, -.4, 0).subtract(across.scale(.035));
            Vec3 right = center.add(across.scale(.11)).add(0, .035, 0);
            Vec3 ridge = center.add(normal.scale(.085));
            // Four triangles form a raised split seam with a lit ridge, not a flat texture overlay.
            quad(out, pose.last(), top, ridge, left, left, 1, alpha, light);
            quad(out, pose.last(), left, ridge, bottom, bottom, .78F, alpha, light);
            quad(out, pose.last(), bottom, ridge, right, right, .64F, alpha, light);
            quad(out, pose.last(), right, ridge, top, top, .9F, alpha, light);
        }
    }
    /** Six differently shaded faces and an asymmetric crown provide volume even in flat daylight. */
    private static void prism(VertexConsumer out,PoseStack.Pose pose,float radius,float height,float alpha,int seed) {
        prism(out, pose, radius, height, alpha, seed, 0xF000F0);
    }
    static void prism(VertexConsumer out,PoseStack.Pose pose,float radius,float height,float alpha,int seed,int light) {
        if(height<.01 || alpha<=0)return;
        Vec3 bottom=new Vec3(0,0,0),tip=new Vec3(radius*.13,height,0);
        for(int side=0;side<6;side++) {
            double a=side*Math.PI/3,b=a+Math.PI/3;
            Vec3 l=new Vec3(Math.cos(a)*radius,height*.18,Math.sin(a)*radius);
            Vec3 r=new Vec3(Math.cos(b)*radius,height*.18,Math.sin(b)*radius);
            Vec3 lt=new Vec3(l.x*.72,height*.72,l.z*.72),rt=new Vec3(r.x*.72,height*.72,r.z*.72);
            float shade=.62F+.32F*(float)(.5+.5*Math.cos(a+seed));
            quad(out,pose,l,lt,rt,r,shade,alpha,light);
            quad(out,pose,lt,tip,rt,rt,Math.min(1,shade+.12F),alpha,light);
            quad(out,pose,bottom,l,r,bottom,shade*.8F,alpha,light);
        }
    }
    private static Vec3 curve(Ribbon r,double t) {
        return r.from.scale((1-t)*(1-t)).add(r.control.scale(2*(1-t)*t)).add(r.to.scale(t*t)).add(0,.025,0);
    }
    /** A curved icy slab, complete with top, underside and beveled side faces. */
    private static void strip(VertexConsumer out,PoseStack.Pose pose,Ribbon r,float alpha) {
        for(int i=0;i<6;i++) {
            Vec3 a=curve(r,i/6.0),b=curve(r,(i+1)/6.0),axis=b.subtract(a).multiply(1,0,1).normalize();
            if (axis.lengthSqr() < .0001) axis = new Vec3(0, 0, 1);
            Vec3 right=new Vec3(-axis.z,0,axis.x).scale(r.width*.5);
            Vec3 al=a.subtract(right),ar=a.add(right),bl=b.subtract(right),br=b.add(right);
            quad(out,pose,al,ar,br,bl,1,alpha);
            Vec3 drop=new Vec3(0,-.13,0);
            Vec3 lower=right.scale(.84),dal=a.subtract(lower).add(drop),dar=a.add(lower).add(drop);
            Vec3 dbl=b.subtract(lower).add(drop),dbr=b.add(lower).add(drop);
            quad(out,pose,dal,dbl,dbr,dar,.65F,alpha*.8F);
            quad(out,pose,al,bl,dbl,dal,.76F,alpha);
            quad(out,pose,br,ar,dar,dbr,.88F,alpha);
            if(i==0)quad(out,pose,ar,al,dal,dar,.78F,alpha);
            if(i==5)quad(out,pose,bl,br,dbr,dbl,.86F,alpha);
        }
    }
    private static void quad(VertexConsumer out,PoseStack.Pose pose,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float shade,float alpha) {
        quad(out, pose, a, b, c, d, shade, alpha, 0xF000F0);
    }
    static void quad(VertexConsumer out,PoseStack.Pose pose,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float shade,float alpha,int light) {
        Vec3 normal=b.subtract(a).cross(c.subtract(a)).normalize();
        Vec3[] p={a,b,c,d};float[][] uv={{0,1},{1,1},{1,0},{0,0}};
        for(int i=0;i<4;i++)out.addVertex(pose.pose(),(float)p[i].x,(float)p[i].y,(float)p[i].z)
                .setColor(.62F*shade,.86F*shade,shade,alpha).setUv(uv[i][0],uv[i][1])
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose,(float)normal.x,(float)normal.y,(float)normal.z);
    }
}
