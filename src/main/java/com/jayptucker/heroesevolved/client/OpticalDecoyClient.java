package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.DecoyInteractPayload;
import com.jayptucker.heroesevolved.network.OpticalDecoyPayload;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Public, render-only optical doubles. Their IDs never enter the world's entity collection. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class OpticalDecoyClient {
    private static final Map<Integer, Image> IMAGES = new LinkedHashMap<>();
    private static ClientLevel world;
    private OpticalDecoyClient() {}
    private static final class Image {
        final RemotePlayer actor;
        final long born;
        Vec3 previous, target, velocity;
        long updated, until, swingAt = -100;
        float yaw;
        boolean swinging;
        Image(RemotePlayer actor, OpticalDecoyPayload data, long now) {
            this.actor = actor; this.born = now; this.previous = data.position();
            update(data, now);
        }
        void update(OpticalDecoyPayload data, long now) {
            previous = position(now);
            target = data.position(); velocity = data.velocity(); updated = now;
            until = now + data.remaining(); yaw = data.yaw();
            if (data.swing() && !swinging) swingAt = now;
            swinging = data.swing();
        }
        Vec3 position(double now) {
            if (target == null) return previous;
            return previous.lerp(target, Math.clamp((now - updated) / 4.0, 0, 1));
        }
        AABB bounds(long now) {
            Vec3 p = position(now);
            return new AABB(p.x - .33, p.y, p.z - .33, p.x + .33, p.y + 1.85, p.z + .33);
        }
    }
    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
    public static int count() { return IMAGES.size(); }
    public static void accept(OpticalDecoyPayload data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !finite(data.position()) || !finite(data.velocity()) || !Float.isFinite(data.yaw())) return;
        if (world != mc.level) { IMAGES.clear(); world = mc.level; }
        if (data.operation() < 0) {
            Image old = IMAGES.remove(data.id());
            if (old != null) shatter(data.position());
            return;
        }
        if (data.remaining() <= 0 || data.remaining() > 160) return;
        long now = mc.level.getGameTime();
        Image image = IMAGES.get(data.id());
        if (image == null) {
            if (IMAGES.size() >= 64) IMAGES.remove(IMAGES.keySet().iterator().next());
            Player source = mc.level.getEntity(data.source()) instanceof Player p ? p : mc.level.getPlayerByUUID(data.owner());
            RemotePlayer actor = new RemotePlayer(mc.level, source == null
                    ? new GameProfile(data.owner(), data.name()) : source.getGameProfile()) {
                @Override public boolean isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart part) {
                    return source == null || source.isModelPartShown(part);
                }
            };
            actor.setId(-1_000_000_000 - data.id());
            actor.setInvisible(false);
            actor.setNoGravity(true);
            if (source != null) for (EquipmentSlot slot : EquipmentSlot.values())
                actor.setItemSlot(slot, source.getItemBySlot(slot).copy());
            image = new Image(actor, data, now);
            IMAGES.put(data.id(), image);
        } else image.update(data, now);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (world != mc.level) { IMAGES.clear(); world = mc.level; }
        if (mc.level == null || mc.isPaused()) return;
        long now = mc.level.getGameTime();
        for (var entry : List.copyOf(IMAGES.entrySet())) {
            Image image = entry.getValue();
            if (now >= image.until) { IMAGES.remove(entry.getKey()); shatter(image.position(now)); continue; }
            image.actor.walkAnimation.update(image.velocity.horizontalDistanceSqr() > .001 ? .55F : 0, .35F);
            image.actor.tickCount = (int) (now - image.born);
            float swing = Math.clamp((now - image.swingAt) / 6.0F, 0, 1);
            image.actor.attackAnim = swing >= 1 ? 0 : swing;
        }
    }
    @SubscribeEvent public static void interact(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Vec3 eye = mc.player.getEyePosition(), end = eye.add(mc.player.getLookAngle().scale(5));
        int closest = -1;
        double distance = Double.MAX_VALUE;
        for (var entry : IMAGES.entrySet()) {
            var contact = entry.getValue().bounds(mc.level.getGameTime()).clip(eye, end);
            if (contact.isEmpty()) continue;
            double candidate = eye.distanceToSqr(contact.get());
            // Do not steal clicks meant for a nearer real block or entity.
            if (mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS
                    && mc.hitResult.getLocation().distanceToSqr(eye) + .02 < candidate) continue;
            if (candidate < distance) { closest = entry.getKey(); distance = candidate; }
        }
        if (closest < 0) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        PacketDistributor.sendToServer(new DecoyInteractPayload(closest));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var pose = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = mc.level.getGameTime() + partial;
        for (Image image : IMAGES.values()) {
            Vec3 position = image.position(now);
            if (position.distanceToSqr(camera) > 80 * 80) continue;
            RemotePlayer actor = image.actor;
            actor.setPos(position); actor.setYRot(image.yaw); actor.yRotO = image.yaw;
            actor.yBodyRot = actor.yBodyRotO = actor.yHeadRot = actor.yHeadRotO = image.yaw;
            float phase = (float) Math.min((now - image.born) / 5.0, (image.until - now) / 8.0);
            float opacity = Math.clamp(phase, 0, 1);
            pose.pushPose();
            pose.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
            if (opacity > .95F) {
                // Convincing doubles retain armor, held equipment and the caster's actual skin.
                mc.getEntityRenderDispatcher().getRenderer(actor).render(actor, image.yaw, partial, pose, buffers, 0xF000F0);
            } else OpticalSkinRenderer.draw(actor, pose, buffers, partial, opacity * .65F, 0xBDEEF7);
            // Thin displaced skin layers reveal the optical imperfection at close inspection.
            pose.pushPose();
            pose.translate(Math.sin(now * .6) * .025, 0, .018);
            OpticalSkinRenderer.draw(actor, pose, buffers, partial, .12F * opacity, 0x8DE6FF);
            pose.popPose();
            pose.popPose();
        }
    }
    private static void shatter(Vec3 position) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        com.jayptucker.heroesevolved.client.effects.AbilityEffectRenderer.play(
                new com.jayptucker.heroesevolved.network.AbilityEffectPayload(
                        com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.OPTIC_DECOY_SHATTER,
                        position, position.add(0, 1.5, 0), 1));
        mc.level.playLocalSound(position.x, position.y + 1, position.z,
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, .55F, 1.7F, false);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) { IMAGES.clear(); world = null; }
    }
}
