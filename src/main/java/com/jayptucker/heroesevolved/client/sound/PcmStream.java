package com.jayptucker.heroesevolved.client.sound;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.client.sounds.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Sound made on the fly: a subclass renders floating-point samples and this turns them into the
 * 16-bit buffers OpenAL streams. Minecraft asks for about a second at a time, a few seconds ahead,
 * on its sound thread. A stream that is used positionally must be mono.
 */
public abstract class PcmStream implements AudioStream {
    protected final int rate, channels;
    private final AudioFormat format;
    private boolean failed;

    protected PcmStream(int rate, int channels) {
        this.rate = rate;
        this.channels = channels;
        format = new AudioFormat(rate, 16, channels, true, ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN);
    }

    /**
     * Writes up to {@code frames} frames of interleaved samples in [-1, 1] into {@code out} and
     * returns how many it wrote; fewer than asked means the sound is over.
     */
    protected abstract int render(float[] out, int frames);

    @Override public AudioFormat getFormat() { return format; }

    @Override public ByteBuffer read(int size) {
        if (failed) return null;
        int frames = Math.max(1, size / (2 * channels));
        float[] samples = new float[frames * channels];
        int made;
        try {
            made = render(samples, frames);
        } catch (RuntimeException e) {
            // A fault here would take the sound thread down with it; end this one sound instead.
            HeroesEvolved.LOGGER.error("Synthesized sound {} failed", getClass().getSimpleName(), e);
            failed = true;
            return null;
        }
        if (made <= 0) return null;
        ByteBuffer out = ByteBuffer.allocateDirect(made * channels * 2).order(ByteOrder.nativeOrder());
        for (int i = 0; i < made * channels; i++)
            out.putShort((short) Math.round(Math.max(-1, Math.min(1, samples[i])) * 32767));
        return out.flip();
    }

    @Override public void close() {}
}
