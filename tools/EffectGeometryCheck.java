import com.jayptucker.heroesevolved.client.effects.PowerParticle;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class EffectGeometryCheck {
    static class TestSprite extends TextureAtlasSprite {
        TestSprite(SpriteContents contents) { super(ResourceLocation.withDefaultNamespace("test"), contents, 16, 16, 0, 0); }
    }
    static class TestCamera extends Camera {
        TestCamera(Vec3 point) { setPosition(point); }
    }
    static class Vertices implements VertexConsumer {
        int count;
        AABB bounds;
        Vec3 camera;
        public VertexConsumer addVertex(float x, float y, float z) {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) throw new AssertionError("Nonfinite geometry");
            Vec3 world = new Vec3(x, y, z).add(camera);
            if (!bounds.contains(world)) throw new AssertionError("Vertex outside culling bounds: " + world + " / " + bounds);
            count++;
            return this;
        }
        public VertexConsumer setColor(int r,int g,int b,int a) {
            if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255 || a < 0 || a > 255) throw new AssertionError("Bad color");
            return this;
        }
        public VertexConsumer setUv(float u,float v) {
            if (!Float.isFinite(u) || !Float.isFinite(v) || u < 0 || u > 1 || v < 0 || v > 1) throw new AssertionError("Bad UV");
            return this;
        }
        public VertexConsumer setUv1(int u,int v) { return this; }
        public VertexConsumer setUv2(int u,int v) { return this; }
        public VertexConsumer setNormal(float x,float y,float z) { return this; }
    }
    public static void main(String[] args) throws Exception {
        int payloads = 0;
        for (AbilityEffectPayload.Kind kind : AbilityEffectPayload.Kind.values()) {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                AbilityEffectPayload source = new AbilityEffectPayload(kind,
                        new Vec3(-1200.5, 64.25, 300.75), new Vec3(-1190.25, 68, 315.5), 8.0F);
                AbilityEffectPayload.STREAM_CODEC.encode(buffer, source);
                AbilityEffectPayload decoded = AbilityEffectPayload.STREAM_CODEC.decode(buffer);
                if (!source.equals(decoded) || buffer.readableBytes() != 0) throw new AssertionError("Bad payload: " + kind);
                payloads++;
            } finally { buffer.release(); }
        }
        try (SpriteContents contents = new SpriteContents(ResourceLocation.withDefaultNamespace("test"),
                new FrameSize(16,16), new NativeImage(16,16,true), ResourceMetadata.EMPTY)) {
            TextureAtlasSprite sprite = new TestSprite(contents);
            SpriteSet sprites = new SpriteSet() {
                public TextureAtlasSprite get(int age,int life) { return sprite; }
                public TextureAtlasSprite get(RandomSource random) { return sprite; }
            };
            PowerParticle.provider(PowerParticle.Shape.RING,sprites);
            PowerParticle.provider(PowerParticle.Shape.MOTE,sprites);
            PowerParticle.provider(PowerParticle.Shape.STREAK,sprites);
            Vec3[] axes = {new Vec3(0,1,0),new Vec3(0,-1,0),new Vec3(1,0,0),new Vec3(0,0,1),new Vec3(1,1,1),Vec3.ZERO};
            Vec3 anchor = new Vec3(101,64,-22);
            TestCamera camera = new TestCamera(anchor.add(6,4,8));
            Vertices vertices = new Vertices();
            vertices.camera = camera.getPosition();
            int frames=0;
            for (PowerParticle.Shape shape : PowerParticle.Shape.values()) {
                for (Vec3 axis : axes) {
                    for (int delay : new int[]{0,3}) {
                        for (boolean moving : new boolean[]{false,true}) {
                            Vec3 normalOrEnd = shape == PowerParticle.Shape.STREAK ? anchor.add(axis) : axis;
                            PowerParticle particle = new PowerParticle(null,shape,anchor,normalOrEnd,
                                    0.4F,1.6F,12,delay,0xA9E8FF,0.8F);
                            if (shape == PowerParticle.Shape.ARC) particle.arc(0.7F,0.3F,3.4F,0.035F);
                            if (moving && shape != PowerParticle.Shape.STREAK) particle.moving(new Vec3(0.55,0.1,-0.2));
                            for (int tick=0; tick<18; tick++) {
                                if (!particle.isAlive()) break;
                                for (float partial : new float[]{0,0.5F,0.99F}) {
                                    vertices.bounds=particle.getRenderBoundingBox(partial).inflate(0.0005D);
                                    int before = vertices.count;
                                    particle.render(vertices,camera,partial);
                                    if (tick < delay && vertices.count != before) throw new AssertionError("Delayed effect rendered early");
                                    frames++;
                                }
                                particle.tick();
                            }
                            if (particle.isAlive()) throw new AssertionError("Particle did not expire");
                        }
                    }
                }
            }
            if (vertices.count == 0) throw new AssertionError("No vertices emitted");
            System.out.println("PASS: " + frames + " rendered frames; " + vertices.count + " finite vertices with valid UVs/colors and culling bounds. All shapes expired, including delayed/moving arcs.");
            System.out.println("PASS: " + payloads + " ability effect payload kinds round-tripped without losing coordinates or scale.");
        }
    }
}
