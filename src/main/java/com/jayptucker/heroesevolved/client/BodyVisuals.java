package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.config.ClientVisualSettings;
import com.jayptucker.heroesevolved.network.BodyStatePayload;
import com.jayptucker.heroesevolved.network.GiveUpPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What a regenerating body looks like on this client: which limbs are missing and how far each has
 * grown back, and whether the body is down on the ground. Missing limbs are hidden from the player
 * model, the armour on them and the item in that hand; {@link RegrowthLayer} draws the regrowth.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class BodyVisuals {
    /** Limb order matches the server: right arm, left arm, right leg, left leg. */
    public static final int RIGHT_ARM = 0, LEFT_ARM = 1, RIGHT_LEG = 2, LEFT_LEG = 3;
    public record State(UUID uuid, boolean downed, long[] growStart, int[] growTicks) {
        public boolean missing(int limb) { return growTicks[limb] > 0; }
        boolean empty() { return !downed && growTicks[0] + growTicks[1] + growTicks[2] + growTicks[3] == 0; }
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static ClientLevel world;

    private BodyVisuals() {}

    public static void update(BodyStatePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != world) { STATES.clear(); world = mc.level; }
        if (world == null) return;
        long now = world.getGameTime();
        long[] starts = new long[4];
        for (int i = 0; i < 4; i++) starts[i] = now + payload.growStart()[i];
        State state = new State(payload.uuid(), payload.downed(), starts, payload.growTicks().clone());
        if (state.empty()) {
            STATES.remove(payload.entityId());
            pose(payload.entityId(), payload.uuid(), false);
        } else {
            if (STATES.size() < 512 || STATES.containsKey(payload.entityId())) STATES.put(payload.entityId(), state);
            pose(payload.entityId(), payload.uuid(), state.downed());
        }
    }

    /** A downed body lies on its back. The pose is forced on this client too, or the client would stand it back up. */
    private static void pose(int id, UUID uuid, boolean downed) {
        if (world == null) return;
        Entity entity = world.getEntity(id);
        if (entity instanceof Player player && player.getUUID().equals(uuid))
            player.setForcedPose(downed ? Pose.SLEEPING : null);
    }

    public static State get(Entity entity) {
        State state = STATES.get(entity.getId());
        return state == null || !state.uuid().equals(entity.getUUID()) ? null : state;
    }

    /** -1 a stump, 0 to 1 growing back, 2 whole. */
    public static float progress(Entity entity, int limb, float partialTick) {
        State state = get(entity);
        if (state == null || !state.missing(limb)) return 2;
        float age = world.getGameTime() + partialTick - state.growStart()[limb];
        return age < 0 ? -1 : Math.min(1, age / state.growTicks()[limb]);
    }

    public static boolean missing(Entity entity, int limb) {
        State state = get(entity);
        return state != null && state.missing(limb);
    }

    public static boolean downed(Entity entity) {
        State state = get(entity);
        return state != null && state.downed();
    }

    public static int arm(LivingEntity entity, HumanoidArm side) {
        return side == HumanoidArm.RIGHT ? RIGHT_ARM : LEFT_ARM;
    }

    public static int arm(Player player, InteractionHand hand) {
        return arm(player, hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite());
    }

    public static boolean goreShown() {
        return ClientVisualSettings.SEVERED_LIMBS.get();
    }

    // ------------------------------------------------------------------

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != world) { STATES.clear(); world = mc.level; }
        if (world == null) return;
        for (var entry : java.util.List.copyOf(STATES.entrySet())) {
            Entity entity = world.getEntity(entry.getKey());
            State state = entry.getValue();
            if (entity != null && !entity.getUUID().equals(state.uuid())) { STATES.remove(entry.getKey()); continue; }
            // Limbs finish growing on their own; the server's final update follows.
            long now = world.getGameTime();
            for (int i = 0; i < 4; i++) if (state.growTicks()[i] > 0 && now > state.growStart()[i] + state.growTicks()[i] + 40) state.growTicks()[i] = 0;
            if (state.empty()) { STATES.remove(entry.getKey()); pose(entry.getKey(), state.uuid(), false); continue; }
            if (state.downed() && entity instanceof Player player && player.getForcedPose() != Pose.SLEEPING) player.setForcedPose(Pose.SLEEPING);
        }
        // Holding Sneak while down asks to give up and wake at home.
        if (mc.player != null && downed(mc.player) && mc.options.keyShift.isDown() && mc.getConnection() != null)
            PacketDistributor.sendToServer(GiveUpPayload.INSTANCE);
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        STATES.clear();
        world = null;
        com.jayptucker.heroesevolved.network.ClientRegenerationState.clear();
    }

    /** Missing limbs (and their sleeves and trouser legs) leave the model. */
    @SubscribeEvent public static void hide(RenderPlayerEvent.Pre event) {
        State state = get(event.getEntity());
        if (state == null) return;
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        if (state.missing(RIGHT_ARM)) hide(model.rightArm, model.rightSleeve);
        if (state.missing(LEFT_ARM)) hide(model.leftArm, model.leftSleeve);
        if (state.missing(RIGHT_LEG)) hide(model.rightLeg, model.rightPants);
        if (state.missing(LEFT_LEG)) hide(model.leftLeg, model.leftPants);
    }

    private static void hide(ModelPart limb, ModelPart layer) {
        limb.visible = false;
        layer.visible = false;
    }

    /** In first person, a missing arm is not there, and neither is what it was holding. */
    @SubscribeEvent public static void firstPersonArm(RenderArmEvent event) {
        if (missing(event.getPlayer(), arm(event.getPlayer(), event.getArm()))) event.setCanceled(true);
    }

    @SubscribeEvent public static void firstPersonHand(RenderHandEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player != null && missing(player, arm(player, event.getHand()))) event.setCanceled(true);
    }
}
