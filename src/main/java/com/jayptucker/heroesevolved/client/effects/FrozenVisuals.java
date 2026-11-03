package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.FrozenEntityPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Translucent, faceted ice around the actual mob, with visible fractures and a frozen pose. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class FrozenVisuals {
    private record Frozen(UUID uuid, long until, long born) {}
    private static final Map<Integer, Frozen> FROZEN = new HashMap<>();
    private static final ResourceLocation ICE = ResourceLocation.withDefaultNamespace("textures/block/ice.png");
    private static ClientLevel world;
    private FrozenVisuals() {}
    public static void update(FrozenEntityPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != world) { FROZEN.clear(); world = level; }
        if (level == null) return;
        if (payload.remainingTicks() <= 0) {
            Frozen old = FROZEN.get(payload.entityId());
            if (old != null && old.uuid.equals(payload.entityUuid())) FROZEN.remove(payload.entityId());
            com.jayptucker.heroesevolved.client.CryoShellClient.clear(payload.entityId());
        } else if (FROZEN.size() < 2048 || FROZEN.containsKey(payload.entityId())) {
            Frozen previous = FROZEN.get(payload.entityId());
            long born = previous != null && previous.uuid.equals(payload.entityUuid()) && previous.until > level.getGameTime()
                    ? previous.born : level.getGameTime();
            FROZEN.put(payload.entityId(), new Frozen(payload.entityUuid(),
                    level.getGameTime() + Math.clamp(payload.remainingTicks(), 1, 610), born));
        }
    }
    public static boolean isFrozen(Entity entity) { return frozen(entity); }
    private static boolean frozen(Entity entity) {
        Frozen state = FROZEN.get(entity.getId());
        return entity.level() == world && state != null && state.uuid.equals(entity.getUUID())
                && entity.level().getGameTime() < state.until;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { FROZEN.clear(); world = null; }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (world != Minecraft.getInstance().level) { FROZEN.clear(); world = Minecraft.getInstance().level; }
        if (world != null) FROZEN.values().removeIf(state -> world.getGameTime() >= state.until);
    }
    @SubscribeEvent public static void pause(EntityTickEvent.Pre event) {
        if (event.getEntity().level().isClientSide && event.getEntity() instanceof Mob && frozen(event.getEntity())) {
            event.getEntity().setDeltaMovement(Vec3.ZERO);
            event.setCanceled(true);
        }
    }
    @SubscribeEvent public static void render(RenderLivingEvent.Post<?, ?> event) {
        Entity entity = event.getEntity();
        if (!frozen(entity)) return;
        PoseStack.Pose pose = event.getPoseStack().last();
        VertexConsumer ice = event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(ICE));
        float rx = (float) (entity.getBoundingBox().getXsize() * 0.57 + 0.12);
        float rz = (float) (entity.getBoundingBox().getZsize() * 0.57 + 0.12);
        Frozen state = FROZEN.get(entity.getId());
        float growth = (float)Math.clamp((entity.level().getGameTime() + event.getPartialTick() - state.born) / 8.0, .12, 1);
        float fracture = com.jayptucker.heroesevolved.client.CryoShellClient.cracks(entity.getId()) / 100F;
        float height = (entity.getBbHeight() + 0.15F) * growth;
        // Separate offset shells refract around the real skin/model instead of replacing it.
        rx *= .92F + growth * .08F; rz *= .92F + growth * .08F;
        shell(ice, pose, rx * .94F, rz * .94F, height * .97F, .13F, .15F, event.getPackedLight());
        shell(ice, pose, rx, rz, height, 0, .30F, event.getPackedLight());
        for (int rib = 0; rib < 6; rib++) {
            double angle=rib*Math.PI/3;
            float top=height*(rib%2==0?1.1F:.9F);
            Vec3 left=ring(angle-.075, rx, rz, 1.04F, .02F);
            Vec3 edge=ring(angle, rx, rz, 1.17F, height*.22F);
            Vec3 right=ring(angle+.075, rx, rz, 1.04F, .02F);
            Vec3 tip=ring(angle, rx, rz, .84F, top);
            face(ice,pose,left,tip,edge,edge,.65F,.78F,event.getPackedLight());
            face(ice,pose,edge,tip,right,right,.54F,1,event.getPackedLight());
            face(ice,pose,left,edge,right,right,.38F,.7F,event.getPackedLight());
        }
        VertexConsumer cracks = event.getMultiBufferSource().getBuffer(RenderType.lines());
        int fractureLines = 3 + (int)(fracture * 7);
        for (int side = 0; side < fractureLines; side++) {
            // Existing fractures stay in place as more branches split away from them.
            double a = side * 2.399963 + Math.PI / 8;
            Vec3 p = new Vec3(Math.cos(a) * rx * 1.015, height * 0.14, Math.sin(a) * rz * 1.015);
            Vec3 middle = p.add(-Math.sin(a) * rx * 0.3, height * 0.3, Math.cos(a) * rz * 0.3);
            line(cracks, pose, p, middle, .45F + fracture * .5F);
            line(cracks, pose, middle, p.add(0, height * (.55 + fracture * .28), 0), .45F + fracture * .5F);
            line(cracks, pose, middle, middle.add(Math.sin(a) * rx * (.2 + fracture * .2), height * .12,
                    -Math.cos(a) * rz * (.2 + fracture * .2)), .45F + fracture * .5F);
            if (fracture > .3F) line(cracks, pose, p.add(0,height*.42,0),
                    p.add(Math.sin(a)*rx*.4,height*.65,-Math.cos(a)*rz*.4),fracture);
        }
    }
    private static Vec3 ring(double angle,float rx,float rz,float radius,float y) {
        return new Vec3(Math.cos(angle)*rx*radius,y,Math.sin(angle)*rz*radius);
    }
    static void shell(VertexConsumer out,PoseStack.Pose pose,float rx,float rz,float height,
                              float rotation,float alpha,int light) {
        for(int row=0;row<2;row++)for(int side=0;side<8;side++) {
            double a=side*Math.PI/4+Math.PI/8+rotation,b=a+Math.PI/4;
            float lower=row==0?1.05F:1,upper=row==0?1:.84F;
            float bottom=row==0?.015F:height*.65F,top=row==0?height*.65F:height;
            Vec3 al=ring(a,rx,rz,lower,bottom), ar=ring(b,rx,rz,lower,bottom);
            Vec3 bl=ring(a,rx,rz,upper,top),br=ring(b,rx,rz,upper,top);
            float shade=.76F+.22F*(float)(.5+.5*Math.cos((a+b)*.5));
            face(out,pose,al,bl,br,ar,alpha,shade,light);
            if(row==1)face(out,pose,new Vec3(0,height,0),br,bl,bl,alpha*.8F,1,light);
        }
    }
    private static void face(VertexConsumer out,PoseStack.Pose pose,Vec3 a,Vec3 b,Vec3 c,Vec3 d,
                             float alpha,float shade,int light) {
        Vec3 normal=b.subtract(a).cross(c.subtract(a)).normalize();
        Vec3[] points={a,b,c,d};float[][] uv={{0,1},{0,0},{1,0},{1,1}};
        for(int i=0;i<4;i++)out.addVertex(pose.pose(),(float)points[i].x,(float)points[i].y,(float)points[i].z)
                .setColor(.68F*shade,.89F*shade,shade,alpha).setUv(uv[i][0],uv[i][1])
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose,(float)normal.x,(float)normal.y,(float)normal.z);
    }
    private static void line(VertexConsumer out, PoseStack.Pose pose, Vec3 from, Vec3 to, float alpha) {
        Vec3 normal = to.subtract(from).normalize();
        for (Vec3 point : new Vec3[] {from, to}) out.addVertex(pose.pose(), (float) point.x, (float) point.y, (float) point.z)
                .setColor(0.85F, 0.97F, 1, alpha).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
