package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.SpeedCyclonePayload;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.util.TriState;
import org.joml.Quaternionf;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Cyclone as everyone sees it: the speedster is not standing at the eye of their own wind, they are the blur
 * running laps round its rim, leaning into the turn with a string of fading afterimages behind them. Their body
 * (and the camera, for them) stays at the eye; it is hidden there, and a stand-in with their skin, armour and
 * cape runs the ring instead. The wind and the tornado are drawn by {@code SpeedArt} from the same clock.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class SpeedCyclone {
    /** Ticks the wind takes to die away after a ring ends, and after a tornado comes down. */
    public static final int RING_FADE = 10, TORNADO_FADE = 26;
    /** Radians a tick round the ring once up to speed (a lap in about fifteen ticks), and ticks to reach it. */
    public static final float LAP_SPEED = 0.42F, SPIN_UP = 6;
    private static final int GHOSTS = 6;
    private static final float GHOST_GAP = 0.2F;

    /** One cyclone on this client's clock. */
    public static final class View {
        public final int entity;
        /** Where it is drawn (eased toward where the server last put it, for a Tempest being steered). */
        public Vec3 center = Vec3.ZERO, previous = Vec3.ZERO;
        Vec3 goal = Vec3.ZERO;
        public float radius = 6.5F, phase;
        /** No runner on its ring (an anchored Cyclone left alone, or a Tempest); whose colours it wears. */
        public boolean free;
        public int style;
        /** When it started, when its tornado began to rise (or MIN), when it ended (or MAX), and when last heard of. */
        public long start, tornado = Long.MIN_VALUE, end = Long.MAX_VALUE, heard;
        Puppet puppet;
        View(int entity) { this.entity = entity; }

        public boolean ended() { return end != Long.MAX_VALUE; }
        public boolean rose() { return tornado != Long.MIN_VALUE; }
        public float age(long now, float partial) { return now - start + partial; }
        /** Ticks since the tornado began to rise, or -1. */
        public float built(long now, float partial) { return rose() ? now - tornado + partial : -1; }
        /** Ticks since it ended, or -1. */
        public float gone(long now, float partial) { return ended() ? now - end + partial : -1; }
        /** The runner's angle round the ring at this age: spinning up from a standstill, then flat out. */
        /** Where it is between ticks. */
        public Vec3 at(float partial) { return previous.lerp(center, partial); }
        public float angle(float age) {
            float spun = age < SPIN_UP ? LAP_SPEED * age * age / (2 * SPIN_UP) : LAP_SPEED * (age - SPIN_UP / 2);
            return phase + spun;
        }
        /** Where the runner is on the ring at this age. */
        public Vec3 runner(float age) {
            float a = angle(age);
            return center.add(Mth.cos(a) * radius, 0, Mth.sin(a) * radius);
        }
    }

    /** A stand-in for the runner: their profile (so their skin and cape), never added to the world. */
    static final class Puppet extends RemotePlayer {
        Puppet(ClientLevel level, GameProfile profile) { super(level, profile); }
        void dress(Player owner) {
            entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, owner.getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION));
            for (EquipmentSlot slot : EquipmentSlot.values())
                if (!net.minecraft.world.item.ItemStack.matches(getItemBySlot(slot), owner.getItemBySlot(slot)))
                    setItemSlot(slot, owner.getItemBySlot(slot).copy());
        }
    }

    private static final Map<Integer, View> VIEWS = new HashMap<>();
    private static ClientLevel world;
    private static boolean drawing;
    private static MultiBufferSource.BufferSource ghostBuffers;

    private SpeedCyclone() {}

    public static Collection<View> views() { return VIEWS.values(); }

    public static void update(SpeedCyclonePayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !Double.isFinite(p.center().lengthSqr())) return;
        if (world != level) { VIEWS.clear(); world = level; }
        long now = level.getGameTime();
        View v = VIEWS.get(p.entity());
        if (p.ended()) { if (v != null && !v.ended()) v.end = now; return; }
        if (v == null || v.ended()) {
            v = new View(p.entity());
            v.start = now - p.age();
            var owner = level.getEntity(p.entity());
            // The runner sets off from where they faced.
            v.phase = owner == null ? 0 : (owner.getYRot() + 90) * Mth.DEG_TO_RAD;
            VIEWS.put(p.entity(), v);
        } else if (Math.abs(now - p.age() - v.start) > 3) v.start = now - p.age();
        if (v.goal == Vec3.ZERO || v.goal.distanceToSqr(p.center()) > 64) { v.center = p.center(); v.previous = p.center(); }
        v.goal = p.center();
        v.free = p.free();
        v.style = p.style();
        v.radius = Math.clamp(p.radius(), 1, 16);
        if (p.tornadoAge() >= 0 && (!v.rose() || Math.abs(now - p.tornadoAge() - v.tornado) > 3)) v.tornado = now - p.tornadoAge();
        v.heard = now;
    }

    /** Is this entity running a cyclone right now (so its body at the eye is hidden)? */
    public static boolean running(int entity) {
        View v = VIEWS.get(entity);
        return v != null && !v.ended() && !v.free;
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { VIEWS.clear(); world = null; }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (world != mc.level) { VIEWS.clear(); world = mc.level; }
        if (world == null || mc.isPaused()) return;
        long now = world.getGameTime();
        // Never trust the end to arrive: a cyclone not heard of for three seconds is over.
        for (View v : VIEWS.values()) if (!v.ended() && now - v.heard > 60) v.end = now;
        VIEWS.values().removeIf(v -> v.ended() && now - v.end > (v.rose() ? TORNADO_FADE : RING_FADE) + 2);
        for (View v : VIEWS.values()) {
            // A steered Tempest glides to where the server has it.
            v.previous = v.center;
            v.center = v.center.lerp(v.goal, 0.35);
            // Legs going as fast as they can carry a body round the ring.
            if (v.puppet != null && !v.ended() && !v.free) v.puppet.walkAnimation.update(2.2F, 1);
        }
    }

    /** The body at the eye is not drawn: the runner is out on the ring. */
    @SubscribeEvent public static void hide(RenderPlayerEvent.Pre event) {
        if (!drawing && running(event.getEntity().getId())) event.setCanceled(true);
    }

    /** No arms in front of the camera either: you are out there running. */
    @SubscribeEvent public static void hand(RenderHandEvent event) {
        var player = Minecraft.getInstance().player;
        if (player != null && running(player.getId())) event.setCanceled(true);
    }

    /** Your own name does not chase you round the ring. */
    @SubscribeEvent public static void nameTag(RenderNameTagEvent event) {
        var player = Minecraft.getInstance().player;
        if (event.getEntity() instanceof Puppet puppet && player != null && puppet.getUUID().equals(player.getUUID()))
            event.setCanRender(TriState.FALSE);
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        boolean solid = event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        if (!solid && event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (VIEWS.isEmpty() || mc.level == null || mc.level != world) return;
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long now = mc.level.getGameTime();
        if (solid) runners(mc, camera, partial, now, event.getPoseStack());
        else ghosts(mc, camera, partial, now);
    }

    /** The lean of a body running the ring: forward into the run and in toward the eye. */
    static Quaternionf lean(float angle) {
        float fx = -Mth.sin(angle), fz = Mth.cos(angle), ix = -Mth.cos(angle), iz = -Mth.sin(angle);
        float tx = fx * 0.26F + ix * 0.32F, tz = fz * 0.26F + iz * 0.32F, tilt = Mth.sqrt(tx * tx + tz * tz);
        return new Quaternionf().rotationAxis(tilt, tz / tilt, 0, -tx / tilt);
    }

    /** The body's yaw (degrees, as Minecraft turns bodies) for running along the ring at this angle. */
    static float yaw(float angle) { return angle * Mth.RAD_TO_DEG; }

    private static void runners(Minecraft mc, Vec3 camera, float partial, long now, PoseStack pose) {
        var dispatcher = mc.getEntityRenderDispatcher();
        var buffers = mc.renderBuffers().bufferSource();
        drawing = true;
        try {
            for (View v : VIEWS.values()) {
                if (v.ended() || v.free || !(mc.level.getEntity(v.entity) instanceof AbstractClientPlayer owner)) continue;
                if (v.center.distanceToSqr(camera) > 160 * 160) continue;
                if (v.puppet == null) v.puppet = new Puppet(mc.level, owner.getGameProfile());
                Puppet puppet = v.puppet;
                puppet.dress(owner);
                float age = v.age(now, partial), angle = v.angle(age), yaw = yaw(angle);
                Vec3 at = v.runner(age).add(0, owner.getY() - v.center.y > 0 ? Math.min(0.5, owner.getY() - v.center.y) : 0, 0);
                place(puppet, at, yaw);
                pose.pushPose();
                pose.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
                pose.mulPose(lean(angle));
                dispatcher.render(puppet, 0, 0, 0, yaw, partial, pose, buffers, dispatcher.getPackedLightCoords(puppet, partial));
                pose.popPose();
            }
        } finally { drawing = false; }
        buffers.endBatch();
    }

    private static void place(Puppet puppet, Vec3 at, float yaw) {
        puppet.setPos(at.x, at.y, at.z);
        puppet.xo = puppet.xOld = at.x; puppet.yo = puppet.yOld = at.y; puppet.zo = puppet.zOld = at.z;
        puppet.setYRot(yaw); puppet.yRotO = yaw;
        puppet.yBodyRot = puppet.yBodyRotO = yaw;
        puppet.yHeadRot = puppet.yHeadRotO = yaw;
        puppet.setXRot(12); puppet.xRotO = 12;
        // The cape streams out behind: as if the air it hangs in were a step behind the body.
        float r = yaw * Mth.DEG_TO_RAD;
        double bx = Mth.sin(r) * 0.9, bz = -Mth.cos(r) * 0.9;
        puppet.xCloak = puppet.xCloakO = at.x + bx;
        puppet.yCloak = puppet.yCloakO = at.y + 0.1;
        puppet.zCloak = puppet.zCloakO = at.z + bz;
    }

    /** Afterimages strung back along the ring, fading, each caught a moment earlier in its stride. */
    private static void ghosts(Minecraft mc, Vec3 camera, float partial, long now) {
        if (ghostBuffers == null) ghostBuffers = MultiBufferSource.immediate(new ByteBufferBuilder(256 * 1024));
        for (View v : VIEWS.values()) {
            if (v.ended() || v.free || v.puppet == null || v.center.distanceToSqr(camera) > 96 * 96) continue;
            Puppet puppet = v.puppet;
            boolean narrow = puppet.getSkin().model() == PlayerSkin.Model.SLIM;
            var m = SpeedAfterimages.model(narrow);
            var parts = SpeedAfterimages.parts(m);
            float age = v.age(now, partial);
            float limb = puppet.walkAnimation.position(partial);
            for (int k = GHOSTS; k >= 1; k--) {
                float angle = v.angle(age) - k * GHOST_GAP, yaw = yaw(angle);
                if (age < k * 1.5F) continue;
                Vec3 at = v.center.add(Mth.cos(angle) * v.radius, 0, Mth.sin(angle) * v.radius);
                if (at.add(0, 1, 0).distanceToSqr(camera) < 1.5 * 1.5) continue;
                for (var part : parts) part.visible = true;
                m.young = false; m.crouching = false; m.riding = false; m.attackTime = 0; m.swimAmount = 0;
                m.leftArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;
                m.rightArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;
                m.setupAnim(puppet, limb - k * 1.6F, 1, puppet.tickCount + partial, 0, 12);
                m.hat.visible = puppet.isModelPartShown(PlayerModelPart.HAT);
                m.jacket.visible = puppet.isModelPartShown(PlayerModelPart.JACKET);
                m.leftSleeve.visible = puppet.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
                m.rightSleeve.visible = puppet.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
                m.leftPants.visible = puppet.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
                m.rightPants.visible = puppet.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
                PoseStack pose = new PoseStack();
                pose.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
                pose.mulPose(lean(angle));
                pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
                pose.scale(-0.9375F, -0.9375F, 0.9375F);
                pose.translate(0, -1.501, 0);
                SpeedAfterimages.draw(m, pose, ghostBuffers, puppet.getSkin().texture(), 0.42F * (1 - (k - 1) / (float) GHOSTS));
            }
        }
        ghostBuffers.endBatch();
    }
}
