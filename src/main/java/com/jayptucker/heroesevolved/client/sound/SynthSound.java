package com.jayptucker.heroesevolved.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A sound whose audio the client makes itself ({@link PcmStream}) rather than reads from a file.
 * It resolves to itself, so it needs no entry in sounds.json; the subtitle is given here. Minecraft
 * plays it through a streaming channel, of which there are only about eight, so few of these
 * should be playing at once.
 */
public class SynthSound extends AbstractTickableSoundInstance {
    private final Supplier<? extends AudioStream> source;
    @Nullable private final String subtitle;
    private BooleanSupplier alive = () -> true;

    public SynthSound(ResourceLocation id, @Nullable String subtitle, SoundSource category, Supplier<? extends AudioStream> source) {
        super(SoundEvent.createVariableRangeEvent(id), category, SoundInstance.createUnseededRandom());
        this.source = source;
        this.subtitle = subtitle;
        looping = false;
        delay = 0;
        relative = true;
        attenuation = Attenuation.NONE;
    }

    /** Heard from {@code x, y, z}, fading to nothing {@code reach} blocks away. */
    public SynthSound at(double x, double y, double z, float reach) {
        relative = false;
        attenuation = Attenuation.LINEAR;
        this.x = x; this.y = y; this.z = z;
        // Linear attenuation reaches max(volume, 1) times the sound's 16 blocks; gain itself caps at 1.
        volume = reach / 16;
        return this;
    }

    /** Stops by itself as soon as {@code condition} no longer holds. */
    public SynthSound whileTrue(BooleanSupplier condition) { alive = condition; return this; }

    public void set(float volume, float pitch) { this.volume = volume; this.pitch = pitch; }

    public float volume() { return volume; }

    public void end() { stop(); }

    /**
     * Fades to silence over {@code ticks}, then stops. A stream renders seconds ahead of what is heard,
     * so changing the sound itself would come late; the channel's volume changes at once.
     */
    public void fadeOut(int ticks) { fadeStep = volume / Math.max(1, ticks); }

    private float fadeStep;

    @Override public WeighedSoundEvents resolve(SoundManager manager) {
        sound = new Sound(getLocation(), ConstantFloat.of(1), ConstantFloat.of(1), 1, Sound.Type.FILE, true, false, 16);
        return new WeighedSoundEvents(getLocation(), subtitle);
    }

    @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
        return CompletableFuture.completedFuture(source.get());
    }

    /** These fade in from silence, so they must start even when their volume is still zero. */
    @Override public boolean canStartSilent() { return true; }

    @Override public void tick() {
        if (!alive.getAsBoolean()) stop();
        if (fadeStep > 0) {
            volume = Math.max(0, volume - fadeStep);
            if (volume <= 0) stop();
        }
    }
}
