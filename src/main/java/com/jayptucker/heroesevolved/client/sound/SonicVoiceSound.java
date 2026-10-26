package com.jayptucker.heroesevolved.client.sound;
import com.jayptucker.heroesevolved.network.SonicVoicePayload;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import java.util.*;

/** One continuous positional cry, with server heartbeats and a short release ramp. */
public final class SonicVoiceSound extends AbstractTickableSoundInstance {
    private static final Map<UUID, SonicVoiceSound> PLAYING = new HashMap<>();
    private final ClientLevel world;
    private final int entityId;
    private final UUID uuid;
    private long refreshed;
    private int age;
    private int releaseRemaining = -1;
    private SonicVoiceSound(ClientLevel level, SonicVoicePayload state) {
        super(ModSounds.SONIC_SCREAM.get(), SoundSource.PLAYERS, RandomSource.create());
        world = level; entityId = state.entity(); uuid = state.uuid(); refreshed = level.getGameTime();
        looping = false; delay = 0; volume = 0; pitch = 1; tick();
    }
    public static void update(SonicVoicePayload state) {
        var world = Minecraft.getInstance().level;
        var current = PLAYING.get(state.uuid());
        if (world == null) { if (current != null) current.end(); return; }
        if (!state.active()) { if (current != null) current.release(); return; }
        if (current != null && (current.world != world || current.entityId != state.entity() || current.isStopped() || current.releaseRemaining >= 0)) {
            current.end(); current = null;
        }
        if (current == null) {
            current = new SonicVoiceSound(world, state); PLAYING.put(state.uuid(), current);
            Minecraft.getInstance().getSoundManager().play(current);
        }
        current.refreshed = world.getGameTime();
    }
    private void release() {
        if (releaseRemaining < 0) releaseRemaining = SonicVoiceEnvelope.RELEASE_TICKS;
    }
    private void end() { stop(); PLAYING.remove(uuid, this); }
    public static void clear() { for (var sound : List.copyOf(PLAYING.values())) sound.end(); }
    @Override public boolean canStartSilent() { return true; }
    @Override public void tick() {
        var entity = world.getEntity(entityId);
        if (Minecraft.getInstance().level != world || entity == null || !entity.getUUID().equals(uuid)
                || !entity.isAlive()) { end(); return; }
        if (world.getGameTime() - refreshed > 30 || age >= SonicVoiceEnvelope.MAXIMUM_TICKS) release();
        if (releaseRemaining == 0) { end(); return; }
        volume = .75F * SonicVoiceEnvelope.gain(age++, releaseRemaining);
        if (releaseRemaining > 0) releaseRemaining--;
        relative = entity == Minecraft.getInstance().player;
        x = relative ? 0 : entity.getX(); y = relative ? 0 : entity.getEyeY(); z = relative ? 0 : entity.getZ();
    }
}
