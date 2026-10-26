import com.jayptucker.heroesevolved.client.effects.PowerParticle;
import com.jayptucker.heroesevolved.client.effects.NuclearCloudGeometry;
import com.jayptucker.heroesevolved.client.effects.DetonationCloudGeometry;
import com.jayptucker.heroesevolved.radiation.NuclearTerrainCursor;
import net.minecraft.core.BlockPos;
import org.joml.Quaternionf;
import com.jayptucker.heroesevolved.network.NuclearCloudPayload;
import org.joml.Matrix4f;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload;
import com.jayptucker.heroesevolved.network.BlizzardStatePayload;
import com.jayptucker.heroesevolved.network.FrozenEntityPayload;
import com.jayptucker.heroesevolved.network.RadiationStatePayload;
import com.jayptucker.heroesevolved.render.RadiationGlowLayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import com.mojang.blaze3d.vertex.PoseStack;
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
        float previousOpacity = 1;
        for (int age = 0; age <= 24; age++) {
            float alpha = com.jayptucker.heroesevolved.client.effects.SpeedAfterimages.opacity(age);
            if (!Float.isFinite(alpha) || alpha < 0 || alpha > .42F || alpha > previousOpacity || age >= 18 && alpha != 0)
                throw new AssertionError("Afterimages must fade monotonically and expire");
            previousOpacity = alpha;
        }
        System.out.println("PASS: Player afterimages fade from 42% opacity to zero and expire after 18 ticks.");
        for (int age : new int[]{0, 1, 2, 40, 100}) {
            float full = com.jayptucker.heroesevolved.client.sound.SonicVoiceEnvelope.gain(age, -1);
            float fading = com.jayptucker.heroesevolved.client.sound.SonicVoiceEnvelope.gain(age, 1);
            float stopped = com.jayptucker.heroesevolved.client.sound.SonicVoiceEnvelope.gain(age, 0);
            if (!Float.isFinite(full) || full < 0 || full > 1 || fading > full || stopped != 0)
                throw new AssertionError("Canary cry playback ramp is invalid");
        }
        if (com.jayptucker.heroesevolved.client.sound.SonicVoiceEnvelope.gain(0, -1) != 0
                || com.jayptucker.heroesevolved.client.sound.SonicVoiceEnvelope.gain(2, -1) != 1
                || com.jayptucker.heroesevolved.client.sound.SonicVoiceEnvelope.gain(100, 1) != .5F)
            throw new AssertionError("Canary cry attack/release timing changed");
        System.out.println("PASS: Continuous Canary cry uses bounded attack and release ramps with a silent endpoint.");
        // A late default Screen background used to blur the already-drawn title
        // and descriptions. Neither custom menu should invoke that GPU pass.
        new com.jayptucker.heroesevolved.gui.screen.SkillTreeScreen(null).renderBackground(null, 0, 0, 0);
        new com.jayptucker.heroesevolved.gui.screen.PowersScreen().renderBackground(null, 0, 0, 0);
        System.out.println("PASS: Power menus do not invoke a late world/menu blur after drawing their content.");
        RegistryFriendlyByteBuf skillBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            var build = new com.jayptucker.heroesevolved.skills.SkillTreeData.Build(5,
                    java.util.Set.of("sonic_resonance_0_0", "sonic_resonance_0_1"),
                    java.util.Map.of("PRIMARY", "sonic_resonance_0_1"));
            var state = new com.jayptucker.heroesevolved.network.SkillTreeStatePayload("sonic_resonance", true, 5, build);
            com.jayptucker.heroesevolved.network.SkillTreeStatePayload.STREAM_CODEC.encode(skillBuffer, state);
            if (!state.equals(com.jayptucker.heroesevolved.network.SkillTreeStatePayload.STREAM_CODEC.decode(skillBuffer)) || skillBuffer.readableBytes() != 0)
                throw new AssertionError("Skill purchases/loadout did not round-trip");
            for (var operation : com.jayptucker.heroesevolved.network.SkillTreeActionPayload.Operation.values()) {
                var action = new com.jayptucker.heroesevolved.network.SkillTreeActionPayload(operation, "sonic_resonance_0_1",
                        com.jayptucker.heroesevolved.ability.AbilitySlot.QUINARY);
                com.jayptucker.heroesevolved.network.SkillTreeActionPayload.STREAM_CODEC.encode(skillBuffer, action);
                if (!action.equals(com.jayptucker.heroesevolved.network.SkillTreeActionPayload.STREAM_CODEC.decode(skillBuffer)))
                    throw new AssertionError("Skill request did not round-trip");
            }
            var voice = new com.jayptucker.heroesevolved.network.SonicVoicePayload(17, java.util.UUID.randomUUID(), true);
            com.jayptucker.heroesevolved.network.SonicVoicePayload.STREAM_CODEC.encode(skillBuffer, voice);
            if (!voice.equals(com.jayptucker.heroesevolved.network.SonicVoicePayload.STREAM_CODEC.decode(skillBuffer)))
                throw new AssertionError("Sustained scream identity did not round-trip");
            skillBuffer.clear(); skillBuffer.writeUtf("sonic_resonance"); skillBuffer.writeBoolean(true);
            skillBuffer.writeVarInt(5); skillBuffer.writeVarInt(5); skillBuffer.writeVarInt(91);
            try {
                com.jayptucker.heroesevolved.network.SkillTreeStatePayload.STREAM_CODEC.decode(skillBuffer);
                throw new AssertionError("Oversized skill state was accepted");
            } catch (IllegalArgumentException expected) { }
        } finally { skillBuffer.release(); }
        System.out.println("PASS: Skill purchases, all request operations, chosen keys and sustained voice identity round-trip; oversized state is rejected.");
        for (boolean slim : new boolean[]{false, true}) {
            var root = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot();
            PlayerModel<AbstractClientPlayer> model = new PlayerModel<>(root, slim);
            for (var pose : new com.jayptucker.heroesevolved.ability.ArmPose[]{
                    com.jayptucker.heroesevolved.ability.ArmPose.TWO_HANDS,
                    com.jayptucker.heroesevolved.ability.ArmPose.TWO_HANDS_STRAIN}) {
                for (var side : net.minecraft.world.entity.HumanoidArm.values()) {
                    var arm = side == net.minecraft.world.entity.HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
                    var sleeve = side == net.minecraft.world.entity.HumanoidArm.RIGHT ? model.rightSleeve : model.leftSleeve;
                    arm.setPos(0, 0, 0); arm.setRotation(0, 0, 0); sleeve.copyFrom(arm);
                    for (float time : new float[]{0, 1, 17.25F, 10000}) {
                        PoseStack transform = new PoseStack();
                        com.jayptucker.heroesevolved.client.FirstPersonArmGeometry.pose(transform, side, pose, time);
                        Vertices vertices = new Vertices(); vertices.camera = Vec3.ZERO;
                        vertices.bounds = side == net.minecraft.world.entity.HumanoidArm.RIGHT
                                ? new AABB(.1, -.6, -1.1, .7, -.1, -.02)
                                : new AABB(-.7, -.6, -1.1, -.1, -.1, -.02);
                        arm.render(transform, vertices, 0xF000F0, 0);
                        sleeve.render(transform, vertices, 0xF000F0, 0);
                        if (vertices.count != 48) throw new AssertionError("Missing arm or sleeve in first-person blast pose");
                    }
                }
            }
        }
        System.out.println("PASS: Both first-person blast arms and sleeves stay on their own sides, forward of the camera, for wide/slim skins and trembling poses.");
        for (int speed = 0; speed <= 4; speed++) {
            RegistryFriendlyByteBuf flightBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                var source = new com.jayptucker.heroesevolved.network.FlightVisualStatePayload(781, speed > 0, false, true, speed);
                com.jayptucker.heroesevolved.network.FlightVisualStatePayload.STREAM_CODEC.encode(flightBuffer, source);
                if (!source.equals(com.jayptucker.heroesevolved.network.FlightVisualStatePayload.STREAM_CODEC.decode(flightBuffer))
                        || flightBuffer.readableBytes() != 0) throw new AssertionError("Flight speed state did not round-trip");
                for (int direction : new int[]{-1, 0, 1, Integer.MAX_VALUE}) {
                    var scroll = new com.jayptucker.heroesevolved.network.FlightSpeedInputPayload(direction);
                    com.jayptucker.heroesevolved.network.FlightSpeedInputPayload.STREAM_CODEC.encode(flightBuffer, scroll);
                    if (!scroll.equals(com.jayptucker.heroesevolved.network.FlightSpeedInputPayload.STREAM_CODEC.decode(flightBuffer)))
                        throw new AssertionError("Wheel direction did not round-trip");
                }
            } finally { flightBuffer.release(); }
        }
        com.jayptucker.heroesevolved.network.ClientFlightVisualState.update(781, true, false, true, 4);
        if (!com.jayptucker.heroesevolved.network.ClientFlightVisualState.isFlightEnabled(781)
                || com.jayptucker.heroesevolved.network.ClientFlightVisualState.speedStep(781) != 4)
            throw new AssertionError("Client failed to retain selected Flight speed");
        com.jayptucker.heroesevolved.network.ClientFlightVisualState.clear();
        if (com.jayptucker.heroesevolved.network.ClientFlightVisualState.isFlightEnabled(781)
                || com.jayptucker.heroesevolved.network.ClientFlightVisualState.speedStep(781) != 0)
            throw new AssertionError("Flight wheel state leaked across worlds");
        System.out.println("PASS: Wheel directions, all five Flight speed states, and client world cleanup.");
        for (float radius : new float[]{20, 24}) for (boolean detail : new boolean[]{false, true})
                for (float time : new float[]{0, 40, 10000}) for (Vec3 viewPosition : new Vec3[]{new Vec3(2, 3, 4), new Vec3(180, 100, -90)}) {
            Vec3 origin = new Vec3(1420, 72, -915);
            Vertices dome = new Vertices(); dome.camera = viewPosition;
            dome.bounds = com.jayptucker.heroesevolved.cryo.ColdDomeShape.bounds(origin, radius).inflate(.1);
            com.jayptucker.heroesevolved.client.effects.ColdDomeGeometry.draw(dome,
                    DetonationCloudGeometry.worldTransform(origin, viewPosition, 1), radius, time, .8F, detail);
            if (dome.count != (detail ? 1024 : 384)) throw new AssertionError("Cold dome exceeded its bounded mesh budget");
            if (!com.jayptucker.heroesevolved.cryo.ColdDomeShape.contains(origin, origin.add(radius, 0, 0), radius)
                    || !com.jayptucker.heroesevolved.cryo.ColdDomeShape.contains(origin, origin.add(0, radius, 0), radius)
                    || com.jayptucker.heroesevolved.cryo.ColdDomeShape.contains(origin, origin.add(radius + .1, 0, 0), radius)
                    || com.jayptucker.heroesevolved.cryo.ColdDomeShape.contains(origin, origin.add(0, -6.1, 0), radius))
                throw new AssertionError("Cold dome volume and visual boundary disagree");
        }
        System.out.println("PASS: 20/24-block cold dome meshes remain world-anchored, bounded and finite, with matching freeze boundaries.");
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
        RegistryFriendlyByteBuf stateBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            java.util.UUID id = java.util.UUID.randomUUID();
            for (int duration : new int[] {0, 1, 600}) {
                FrozenEntityPayload source = new FrozenEntityPayload(1234, id, duration);
                FrozenEntityPayload.STREAM_CODEC.encode(stateBuffer, source);
                if (!source.equals(FrozenEntityPayload.STREAM_CODEC.decode(stateBuffer))) throw new AssertionError("Bad freeze state");
            }
            for (int duration : new int[] {0, 28}) {
                BlizzardStatePayload source = new BlizzardStatePayload(id, new Vec3(-123.5, 87.25, 14.5), 12, duration);
                BlizzardStatePayload.STREAM_CODEC.encode(stateBuffer, source);
                if (!source.equals(BlizzardStatePayload.STREAM_CODEC.decode(stateBuffer))) throw new AssertionError("Bad storm state");
            }
            if (stateBuffer.readableBytes() != 0) throw new AssertionError("State payload left unread data");
            for (int remaining : new int[] {0, 60, 600}) {
                RadiationStatePayload source = new RadiationStatePayload(531, id, remaining == 0 ? 0 : 100, remaining, 600);
                RadiationStatePayload.STREAM_CODEC.encode(stateBuffer, source);
                if (!source.equals(RadiationStatePayload.STREAM_CODEC.decode(stateBuffer)) || stateBuffer.readableBytes() != 0)
                    throw new AssertionError("Bad radiation heat/sound state");
            }
            System.out.println("PASS: Radiation heat and charging sound state round-trips, including full awakening and stop updates.");
            for (int age : new int[] {0, 40, 599}) {
                NuclearCloudPayload cloud = new NuclearCloudPayload(id, ResourceLocation.withDefaultNamespace("overworld"),
                        new Vec3(-1200.5, 64.25, 300.75), 1.6F, 48, age);
                NuclearCloudPayload.STREAM_CODEC.encode(stateBuffer, cloud);
                if (!cloud.equals(NuclearCloudPayload.STREAM_CODEC.decode(stateBuffer)) || stateBuffer.readableBytes() != 0)
                    throw new AssertionError("Bad mushroom-cloud position, dimension, scale or age");
            }
            System.out.println("PASS: Independent mushroom-cloud payloads preserve dimension, age and awakening scale.");
            System.out.println("PASS: Frozen entity and blizzard state payloads preserve identity, volume, duration and clearing updates.");
        } finally { stateBuffer.release(); }
        for (boolean slim : new boolean[] {false, true}) {
            var root = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot();
            PlayerModel<AbstractClientPlayer> model = new PlayerModel<>(root, slim);
            Vertices mesh = new Vertices(); mesh.camera = Vec3.ZERO; mesh.bounds = new AABB(-10, -10, -10, 10, 10, 10);
            model.rightArm.xRot = -1.2F; model.leftArm.zRot = 0.7F;
            model.renderToBuffer(new PoseStack(), mesh, 0xF000F0, 0, RadiationGlowLayer.color(1, 0));
            model.rightArm.render(new PoseStack(), mesh, 0xF000F0, 0, RadiationGlowLayer.color(0.4F, 0));
            if (mesh.count == 0) throw new AssertionError("Glowing body/hands produced no geometry");
            int cool = RadiationGlowLayer.color(0.2F, 0), hot = RadiationGlowLayer.color(1, 0);
            if ((hot >>> 16 & 255) <= (cool >>> 16 & 255) || (hot >>> 16 & 255) <= (hot >>> 8 & 255))
                throw new AssertionError("Orange emission does not brighten with stress");
        }
        System.out.println("PASS: Wide/slim glowing body and first-person arm meshes have finite geometry and valid UVs; orange brightness scales with stress.");
        int cloudVertices = 0;
        for (float scale : new float[] {0.2F, 1, 1.6F}) for (float seconds : new float[] {0, .25F, 2.8F, 9, 25, 29.99F}) {
            Vertices cloud = new Vertices(); cloud.camera = Vec3.ZERO;
            cloud.bounds = new AABB(-400, -10, -400, 400, 200, 400);
            Matrix4f transform = new Matrix4f().scale(scale);
            float opacity = Math.min(1, (30 - seconds) / 5);
            NuclearCloudGeometry.cloud(cloud, transform, seconds, opacity);
            NuclearCloudGeometry.shockwave(cloud, transform, 48 / scale, opacity);
            NuclearCloudGeometry.dust(cloud, transform, Math.min(seconds, 9), opacity);
            if (cloud.count == 0 || cloud.count % 4 != 0) throw new AssertionError("Invalid mushroom-cloud quad mesh");
            cloudVertices += cloud.count;
        }
        System.out.println("PASS: " + cloudVertices + " finite mushroom-cloud, shockwave and city-dust vertices across growth, cooling, fade and scaled awakening frames.");
        // Simulate the shader's existing view matrix, then recover the rendered center.
        // A duplicated camera rotation makes this fail when looking back from flight.
        Vec3 blastOrigin = new Vec3(1420.25, 82.5, -915.75);
        for (Vec3 viewPosition : new Vec3[]{blastOrigin.add(5, 2, 12), blastOrigin.add(180, 95, -140)})
            for (float yaw : new float[]{0, 1.2F, 3.14F}) for (float pitch : new float[]{-.8F, 0, .7F}) {
                Quaternionf rotation = new Quaternionf().rotateYXZ(yaw, pitch, 0);
                Matrix4f view = new Matrix4f().rotate(new Quaternionf(rotation).conjugate());
                Matrix4f submitted = new Matrix4f(view).mul(DetonationCloudGeometry.worldTransform(blastOrigin, viewPosition, 1.6F));
                final double[] sum = new double[3];
                Vertices captured = new Vertices() {
                    @Override public VertexConsumer addVertex(float x, float y, float z) {
                        sum[0] += x; sum[1] += y; sum[2] += z;
                        return super.addVertex(x, y, z);
                    }
                };
                captured.camera = Vec3.ZERO; captured.bounds = new AABB(-1000, -1000, -1000, 1000, 1000, 1000);
                DetonationCloudGeometry.draw(captured, submitted, rotation,
                        new java.util.ArrayList<>(java.util.List.of(new DetonationCloudGeometry.Puff(0, 0, 0, 8, 12, .5F, 1))), Vec3.ZERO);
                org.joml.Vector3f expected = view.transformPosition(new org.joml.Vector3f(
                        (float)(blastOrigin.x - viewPosition.x), (float)(blastOrigin.y - viewPosition.y), (float)(blastOrigin.z - viewPosition.z)));
                if (new Vec3(sum[0] / captured.count, sum[1] / captured.count, sum[2] / captured.count)
                        .distanceTo(new Vec3(expected.x, expected.y, expected.z)) > .0002)
                    throw new AssertionError("Cloud drifted from its world anchor under camera motion");
            }
        System.out.println("PASS: Cloud stays at the blast coordinates across 18 near/far camera positions and rotations with the shader view applied.");
        int smokeVertices = 0;
        for (int detail = 0; detail <= 2; detail++) for (float seconds : new float[] {0, .25F, 2.8F, 9, 25, 29.99F})
                for (float pitch : new float[] {-1.4F, 0, 1.4F}) {
            Vertices smoke = new Vertices(); smoke.camera = Vec3.ZERO;
            smoke.bounds = new AABB(-160, -80, -160, 160, 200, 160);
            var puffs = DetonationCloudGeometry.puffs(seconds, Math.min(1, (30 - seconds) / 5), detail);
            if (seconds >= 1.3F && puffs.stream().anyMatch(puff -> puff.y() - puff.height() < 0))
                throw new AssertionError("Smoke stem or cap fell below blast origin");
            DetonationCloudGeometry.draw(smoke, new Matrix4f().translate(0, 1.5F, 0).scale(1.6F),
                    new Quaternionf().rotateYXZ(.7F, pitch, 0), puffs, new Vec3(70, 20, 70));
            if (smoke.count == 0 || smoke.count > 256 || smoke.count % 4 != 0)
                throw new AssertionError("Smoke exceeded its per-cloud vertex budget");
            smokeVertices += smoke.count;
        }
        System.out.println("PASS: " + smokeVertices + " finite textured smoke vertices across three detail levels, growth/fade and camera angles; at most 256 per cloud.");
        var preview = DetonationCloudGeometry.puffs(8, 1, 2).stream().map(puff -> String.format(java.util.Locale.ROOT,
                "{\"x\":%f,\"y\":%f,\"z\":%f,\"width\":%f,\"height\":%f,\"heat\":%f,\"alpha\":%f}",
                puff.x(), puff.y(), puff.z(), puff.width(), puff.height(), puff.heat(), puff.alpha())).toList();
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/nuclear-puffs.json"), "[" + String.join(",", preview) + "]");
        for (int radius : new int[] {1, 2, 24, 48}) {
            NuclearTerrainCursor cursor = new NuclearTerrainCursor(radius);
            var offset = new BlockPos.MutableBlockPos();
            var visited = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
            int previousSlice = -1;
            while (cursor.next(offset)) {
                int slice = offset.getY() >= 0 ? offset.getY() * 2 : -offset.getY() * 2 - 1;
                if (slice < previousSlice) throw new AssertionError("Crater dug deeply before completing the outward surface sweep");
                previousSlice = slice;
                if (offset.getX() * offset.getX() + offset.getY() * offset.getY() + offset.getZ() * offset.getZ() > radius * radius
                        || !visited.add(offset.asLong())) throw new AssertionError("Duplicate or out-of-range crater voxel");
            }
            int expected = 0;
            for (int x = -radius; x <= radius; x++) for (int y = -radius; y <= radius; y++) for (int z = -radius; z <= radius; z++)
                if (x * x + y * y + z * z <= radius * radius) expected++;
            if (visited.size() != expected || cursor.next(offset)) throw new AssertionError("Crater cursor skipped voxels or restarted");
            System.out.println("PASS: Streaming radius-" + radius + " crater visits all " + expected + " voxels exactly once.");
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
            var trailGroup = new net.minecraft.core.particles.ParticleGroup(512);
            PowerParticle trail = new PowerParticle(null, PowerParticle.Shape.STREAK, anchor, anchor.add(2, 1, 0),
                    .04F, .09F, 160, 0, 0xE5F1F2, .32F).lingering(trailGroup);
            if (trail.getParticleGroup().orElseThrow() != trailGroup) throw new AssertionError("Flight trail shares combat budget");
            for (int tick = 0; tick < 160; tick++) {
                if (!trail.isAlive()) throw new AssertionError("Flight trail disappeared before eight seconds");
                vertices.bounds = trail.getRenderBoundingBox(.5F).inflate(.0005);
                trail.render(vertices, camera, .5F);
                trail.tick();
            }
            trail.tick();
            if (trail.isAlive()) throw new AssertionError("Flight trail failed to expire");
            System.out.println("PASS: Long flight vapor renders safely for eight seconds, expires, and has a separate particle budget.");
            if (vertices.count == 0) throw new AssertionError("No vertices emitted");
            System.out.println("PASS: " + frames + " rendered frames; " + vertices.count + " finite vertices with valid UVs/colors and culling bounds. All shapes expired, including delayed/moving arcs.");
            System.out.println("PASS: " + payloads + " ability effect payload kinds round-tripped without losing coordinates or scale.");
        }
    }
}
