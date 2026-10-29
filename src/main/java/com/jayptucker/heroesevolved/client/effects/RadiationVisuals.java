package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.sound.NuclearChargeSound;
import com.jayptucker.heroesevolved.network.RadiationStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.world.entity.Entity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class RadiationVisuals {
    private record State(UUID uuid, int stress, long chargeEnds, int duration, long expires) {}
    private static final Map<Integer, State> STATES = new HashMap<>();
    private static final Map<Integer, NuclearChargeSound> SOUNDS = new HashMap<>();
    private static ClientLevel world;
    private RadiationVisuals() {}
    public static void update(RadiationStatePayload value) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != world) { clear(); world = mc.level; }
        if (world == null) return;
        if (value.stress() <= 0 && value.remaining() <= 0) { remove(value.entityId()); return; }
        if (STATES.size() >= 1024 && !STATES.containsKey(value.entityId())) return;
        State old = STATES.get(value.entityId());
        if (old != null && !old.uuid.equals(value.uuid())) remove(value.entityId());
        STATES.put(value.entityId(), new State(value.uuid(), Math.clamp(value.stress(), 0, 100),
                world.getGameTime() + Math.clamp(value.remaining(), 0, 600), Math.clamp(value.duration(), 1, 600),
                world.getGameTime() + 60));
        if (value.remaining() > 0 && !SOUNDS.containsKey(value.entityId())) {
            // Tracking state can arrive just before the entity spawn packet; tick retries then.
            startSound(value.entityId());
        } else if (value.remaining() <= 0) stopSound(value.entityId());
    }
    private static void startSound(int id) {
        State state = STATES.get(id);
        if (state == null || world.getEntity(id) == null || !world.getEntity(id).getUUID().equals(state.uuid)) return;
        NuclearChargeSound sound = new NuclearChargeSound(world, id, state.uuid);
        SOUNDS.put(id, sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }
    public static float charge(int id, UUID uuid) {
        State state = STATES.get(id);
        if (state == null || world == null || !state.uuid.equals(uuid) || world.getGameTime() >= state.expires
                || world.getGameTime() >= state.chargeEnds) return -1;
        return Math.clamp(1 - (state.chargeEnds - world.getGameTime()) / (float) state.duration, 0, 1);
    }
    /** The last Core value the server sent for this entity, or -1 if none is current. */
    public static int stress(int id) {
        State state = STATES.get(id);
        return state == null || world == null || world.getGameTime() >= state.expires ? -1 : state.stress;
    }
    public static float heat(Entity entity) {
        State state = STATES.get(entity.getId());
        if (world == null || entity.level() != world || state == null || !state.uuid.equals(entity.getUUID())
                || world.getGameTime() >= state.expires) return 0;
        float charging = charge(entity.getId(), entity.getUUID());
        return charging >= 0 ? 0.55F + charging * 0.45F : state.stress / 100.0F;
    }
    private static void stopSound(int id) { NuclearChargeSound sound = SOUNDS.remove(id); if (sound != null) sound.end(); }
    private static void remove(int id) { STATES.remove(id); stopSound(id); }
    private static void clear() { SOUNDS.values().forEach(NuclearChargeSound::end); SOUNDS.clear(); STATES.clear(); world = null; }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level != world) { clear(); world = Minecraft.getInstance().level; }
        if (world == null) return;
        for (int id : java.util.List.copyOf(STATES.keySet())) {
            State state = STATES.get(id);
            if (world.getGameTime() >= state.expires) { remove(id); continue; }
            NuclearChargeSound sound = SOUNDS.get(id);
            if (sound != null && sound.isStopped()) stopSound(id);
            if (world.getGameTime() >= state.chargeEnds) stopSound(id);
            else if (!SOUNDS.containsKey(id)) startSound(id);
        }
    }
}
