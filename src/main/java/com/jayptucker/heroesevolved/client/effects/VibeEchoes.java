package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient;
import com.jayptucker.heroesevolved.styles.Piece;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Read the Vibe's replayed blows, as ghosts of the real creatures: each {@code VIBE_ECHO} piece is one body (its type in
 * the seed, the living creature in the entity id if it is still about), drawn with its own model and skin, see-through
 * and cold blue, flickering. The attacker leans in and swings at its victim; the victim flinches back, flashing red.
 * Stand-ins are created per type (a player's from their profile), never added to the level, and cached.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class VibeEchoes {
    private static final Map<EntityType<?>, LivingEntity> STANDS = new HashMap<>();
    private static final Map<UUID, RemotePlayer> PLAYERS = new HashMap<>();
    private static final Set<EntityType<?>> BROKEN = new HashSet<>();
    private static final UUID NOBODY = new UUID(0, 0);
    private static ClientLevel world;
    private static MultiBufferSource.BufferSource buffers;

    private VibeEchoes() {}

    private static LivingEntity stand(ClientLevel level, SetPieceClient.Active a) {
        if (world != level) {
            STANDS.clear();
            PLAYERS.clear();
            world = level;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.byId(a.seed());
        if (type == null || BROKEN.contains(type)) return null;
        if (type == EntityType.PLAYER) {
            Entity live = a.entity() >= 0 ? level.getEntity(a.entity()) : null;
            GameProfile profile = live instanceof AbstractClientPlayer player ? player.getGameProfile() : new GameProfile(NOBODY, "");
            return PLAYERS.computeIfAbsent(profile.getId(), id -> new RemotePlayer(level, profile));
        }
        LivingEntity stand = STANDS.get(type);
        if (stand == null && !STANDS.containsKey(type)) {
            Entity made = null;
            try {
                made = type.create(level);
            } catch (RuntimeException ignored) {
            }
            stand = made instanceof LivingEntity living ? living : null;
            STANDS.put(type, stand);
        }
        // Copy what changes the look: a baby, a sheared sheep's coat isn't worth it, but size is.
        Entity live = a.entity() >= 0 ? level.getEntity(a.entity()) : null;
        if (stand instanceof net.minecraft.world.entity.AgeableMob ageable && live instanceof net.minecraft.world.entity.AgeableMob source)
            ageable.setAge(source.getAge());
        return stand;
    }

    @SubscribeEvent public static void draw(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var echoes = SetPieceClient.running(Piece.VIBE_ECHO);
        if (echoes.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(64 * 1024));
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 camera = event.getCamera().getPosition();
        for (var a : echoes) {
            float t = a.age(partial);
            if (t < 0 || t > Piece.VIBE_ECHO.ticks) continue;
            LivingEntity stand = stand(mc.level, a);
            if (stand == null) continue;
            try {
                ghost(mc, stand, a, t, partial, camera, event);
            } catch (RuntimeException broken) {
                // A mob whose model needs state a stand-in lacks: leave it out rather than break the frame.
                BROKEN.add(stand.getType());
                HeroesEvolved.LOGGER.warn("Could not draw a Vibe echo of {}", stand.getType(), broken);
            }
        }
        buffers.endBatch();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void ghost(Minecraft mc, LivingEntity stand, SetPieceClient.Active a, float t, float partial, Vec3 camera,
                              RenderLevelStageEvent event) {
        if (!(mc.getEntityRenderDispatcher().getRenderer(stand) instanceof LivingEntityRenderer renderer)) return;
        boolean attacker = a.scale() >= .8F;
        float height = stand.getBbHeight();
        Vec3 toward = a.target().subtract(a.origin()).multiply(1, 0, 1);
        toward = toward.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : toward.normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-toward.x, toward.z));
        // The attacker leans in as it strikes; the victim is knocked back a step when the blow lands.
        float lunge = attacker ? smooth((t - 2) / 6) * (1 - smooth((t - 10) / 8)) : 0;
        float recoil = attacker ? 0 : smooth((t - 8) / 3) * (1 - smooth((t - 15) / 10));
        Vec3 feet = a.origin().subtract(0, height * .5, 0).add(toward.scale(.4 * lunge - .45 * recoil));
        if (feet.distanceToSqr(camera) > 64 * 64 || !event.getFrustum().isVisible(
                new AABB(feet.add(-1, 0, -1), feet.add(1, height, 1)))) return;
        stand.setPos(feet);
        stand.setYRot(yaw);
        stand.yRotO = yaw;
        stand.yBodyRot = stand.yBodyRotO = yaw;
        stand.yHeadRot = stand.yHeadRotO = yaw;
        stand.setXRot(attacker ? 8 : -12 * recoil);
        stand.xRotO = stand.getXRot();
        var model = renderer.getModel();
        model.attackTime = attacker ? Mth.clamp((t - 4) / 6, 0, 1) : 0;
        model.riding = false;
        model.young = stand.isBaby();
        model.prepareMobModel(stand, 0, 0, partial);
        model.setupAnim(stand, 0, 0, t, 0, stand.getXRot());
        ResourceLocation texture = renderer.getTextureLocation(stand);
        float fade = fade(t, Piece.VIBE_ECHO.ticks, 3, 10);
        float flicker = .62F + .2F * Mth.sin(t * 1.3F + a.seed()) + .12F * Mth.sin(t * 3.1F);
        if (hash((int) t * 31 + a.seed()) < .08F) flicker *= .3F;
        int alpha = Mth.clamp((int) (fade * flicker * 170), 0, 255);
        if (alpha <= 2) return;
        boolean hurt = !attacker && t >= 8 && t < 13;
        int overlay = OverlayTexture.pack(OverlayTexture.u(0), OverlayTexture.v(hurt));
        PoseStack pose = new PoseStack();
        pose.translate(feet.x - camera.x, feet.y - camera.y, feet.z - camera.z);
        pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        pose.scale(-1, -1, 1);
        if (stand instanceof AbstractClientPlayer) pose.scale(.9375F, .9375F, .9375F);
        pose.translate(0, -1.501, 0);
        int light = 0xF000F0;
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(texture)), light, overlay,
                alpha << 24 | (attacker ? 0x8C96FF : 0x8CC8FF));
        // A cold glow over it, so it reads as a memory and not a creature standing there.
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(texture)), light, OverlayTexture.NO_OVERLAY,
                (int) (alpha * .55F) << 24 | (attacker ? 0x3A3CA8 : 0x2C66B0));
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static float fade(float age, float life, float in, float out) {
        if (age < 0 || age >= life) return 0;
        return (in > 0 ? smooth(age / in) : 1) * (out > 0 ? 1 - smooth((age - (life - out)) / out) : 1);
    }

    private static float hash(int i) {
        int x = i * 0x27D4EB2D ^ 0x5BD1E995;
        x ^= x >>> 15;
        x *= 0x2C1B3C6D;
        x ^= x >>> 12;
        return (x & 0xFFFFFF) / (float) 0x1000000;
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        STANDS.clear();
        PLAYERS.clear();
        BROKEN.clear();
        world = null;
    }
}
