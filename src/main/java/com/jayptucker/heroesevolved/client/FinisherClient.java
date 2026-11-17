package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.config.ClientVisualSettings;
import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.jayptucker.heroesevolved.network.ClientFinisher;
import com.jayptucker.heroesevolved.network.FinisherPayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The finisher on the client: which foes glisten (gold outline, drawn through {@code FinisherGlowMixin}, and a glint
 * that rises up the body, drawn by {@code TakedownArt}), which one the key would take (the glistening foe nearest the
 * crosshair, in reach and in sight), the key itself (C finishes, Shift + C switches finishers on or off), the prompt
 * under the crosshair, and the camera that pulls back to a still side view while your own finisher plays. The server
 * decides everything; this only draws and asks.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class FinisherClient {
    /** The pale gold of the glisten's outline (Space-Time's gold, shared by every power so players learn one signal). */
    public static final int GLISTEN = 0xFFD27A;
    /** How far off the crosshair a foe can be and still be the one the key takes, in degrees. */
    private static final double AIM_CONE = 14;

    private static LivingEntity aimed;
    private static CameraType before;
    private static TempestCamera.Shot shot;
    private static long shotUntil = Long.MIN_VALUE, runEndsAt = Long.MIN_VALUE;

    private FinisherClient() {}

    /** Whether the local player can finish anything at all right now (has a finisher, switched on). */
    public static boolean armed() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && ClientFinisher.has() && ClientFinisher.state().enabled() && mc.player.isAlive()
                && !mc.player.isSpectator();
    }

    /** Whether {@code entity} glistens for this player: low enough to finish, close, and finishers armed. */
    public static boolean glistens(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !entity.level().isClientSide || !armed()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (entity.getId() == ClientFinisher.runTarget() || entity.distanceToSqr(mc.player) > Takedowns.GLISTEN_RANGE * Takedowns.GLISTEN_RANGE)
            return false;
        return Takedowns.looksFinishable(mc.player, living, ClientFinisher.state().pvp());
    }

    /** Whether the outline should show (the setting can leave only the prompt). */
    public static boolean outlined(Entity entity) {
        return ClientVisualSettings.FINISHER_GLISTEN.get() && glistens(entity);
    }

    /** The foe the key would finish now, or null. */
    public static LivingEntity aimed() { return aimed; }

    private static LivingEntity pick(Minecraft mc) {
        if (!armed() || mc.level == null || ClientFinisher.runTarget() >= 0) return null;
        double reach = Math.max(1, ClientFinisher.state().reach());
        Vec3 eye = mc.player.getEyePosition(), look = mc.player.getViewVector(1);
        LivingEntity best = null;
        double bestAngle = Math.cos(Math.toRadians(AIM_CONE));
        for (LivingEntity candidate : mc.level.getEntitiesOfClass(LivingEntity.class,
                mc.player.getBoundingBox().inflate(reach + 2), FinisherClient::glistens)) {
            var box = candidate.getBoundingBox();
            Vec3 nearest = new Vec3(Mth.clamp(eye.x, box.minX, box.maxX), Mth.clamp(eye.y, box.minY, box.maxY),
                    Mth.clamp(eye.z, box.minZ, box.maxZ));
            if (nearest.distanceToSqr(eye) > (reach + .5) * (reach + .5)) continue;
            Vec3 to = box.getCenter().subtract(eye);
            double facing = to.normalize().dot(look);
            // The crosshair right on the body always counts, however wide it is.
            boolean onIt = box.inflate(.3).clip(eye, eye.add(look.scale(reach + 1))).isPresent();
            if (!onIt && facing < bestAngle) continue;
            if (mc.level.clip(new ClipContext(eye, box.getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    mc.player)).getType() != HitResult.Type.MISS) continue;
            best = candidate;
            bestAngle = onIt ? 2 : facing;
        }
        return best;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { aimed = null; release(mc); return; }
        aimed = mc.screen == null ? pick(mc) : null;
        while (ModKeyMappings.FINISHER.consumeClick()) {
            if (mc.screen != null) continue;
            if (Screen.hasShiftDown()) PacketDistributor.sendToServer(new FinisherPayload(FinisherPayload.TOGGLE, -1));
            else if (aimed != null) PacketDistributor.sendToServer(new FinisherPayload(FinisherPayload.FINISH, aimed.getId()));
        }
        long now = mc.level.getGameTime();
        if (ClientFinisher.takeChange()) {
            if (ClientFinisher.runTarget() >= 0) {
                runEndsAt = now + ClientFinisher.runTicks() + 10;
                frame(mc);
            } else {
                release(mc);
            }
        }
        if (ClientFinisher.runTarget() >= 0 && now > runEndsAt) ClientFinisher.endRun();
        if (shot != null && now > shotUntil) release(mc);
    }

    /**
     * You stand your ground while your finisher plays: no walking, jumping or crouching (turning the head is fine). Done
     * on the input, not with a speed attribute, which would also zoom the field of view in.
     */
    @SubscribeEvent
    public static void hold(MovementInputUpdateEvent event) {
        if (ClientFinisher.runTarget() < 0) return;
        var input = event.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    /**
     * Your finisher began: the view steps to one side of you and the foe and holds still there, a little behind you, so
     * the whole move reads. It stays in front of walls, and comes back when the move ends.
     */
    private static void frame(Minecraft mc) {
        if (!ClientVisualSettings.FINISHER_CAMERA.get()) return;
        Entity foe = mc.level.getEntity(ClientFinisher.runTarget());
        if (foe == null) return;
        Vec3 me = mc.player.position(), them = foe.position();
        Vec3 mid = me.lerp(them, .5).add(0, 1.1, 0);
        Vec3 along = them.subtract(me).multiply(1, 0, 1);
        along = along.lengthSqr() < 1e-4 ? mc.player.getLookAngle().multiply(1, 0, 1).normalize() : along.normalize();
        Vec3 side = new Vec3(-along.z, 0, along.x);
        Vec3 best = null;
        double room = -1;
        for (int sign : new int[]{1, -1}) {
            Vec3 want = mid.add(side.scale(sign * 4.2)).subtract(along.scale(1.8)).add(0, 1.2, 0);
            var hit = mc.level.clip(new ClipContext(mid, want, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            Vec3 at = hit.getType() == HitResult.Type.BLOCK
                    ? hit.getLocation().add(mid.subtract(hit.getLocation()).normalize().scale(.35)) : want;
            double space = at.distanceToSqr(mid);
            if (space > room) { room = space; best = at; }
        }
        if (best == null || room < 2.5 * 2.5) return;
        Vec3 look = mid.subtract(best);
        shot = new TempestCamera.Shot(best, (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG),
                (float) (-Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG));
        shotUntil = mc.level.getGameTime() + ClientFinisher.runTicks() + 4;
        if (before == null) before = mc.options.getCameraType();
        if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    private static void release(Minecraft mc) {
        shot = null;
        if (before != null) { mc.options.setCameraType(before); before = null; }
    }

    /** The finisher's still view while it plays, or null (asked by {@link TempestCamera#shot}). */
    public static TempestCamera.Shot shot() { return shot; }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientFinisher.clear();
        aimed = null;
        release(Minecraft.getInstance());
    }

    /** The prompt under the crosshair: "[C] Finish", in the glisten's gold. */
    @EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Hud {
        @SubscribeEvent public static void register(RegisterGuiLayersEvent event) {
            event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "finisher_prompt"), (gui, delta) -> {
                Minecraft mc = Minecraft.getInstance();
                if (aimed == null || mc.options.hideGui || mc.screen != null) return;
                Component text = Component.translatable("finisher.heroesevolved.prompt", ModKeyMappings.FINISHER.getTranslatedKeyMessage());
                gui.drawCenteredString(mc.font, text, gui.guiWidth() / 2, gui.guiHeight() / 2 + 12, GLISTEN);
            });
        }
    }
}
