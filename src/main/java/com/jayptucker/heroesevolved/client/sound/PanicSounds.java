package com.jayptucker.heroesevolved.client.sound;

import com.jayptucker.heroesevolved.HeroesEvolved;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * The people of the city when the sirens start, in two layers.
 *
 * <p>All around: a crowd panicking in every street, made on the fly from the CC0 crowd recording
 * (sounds/intro/crowd_panic*.ogg). Overlapping pieces of it, each at its own pitch, distance and
 * direction, so it is dense, wide, and never repeats.
 *
 * <p>Close by: single voices, each riding one of the people running past, so they sweep by and
 * fall away behind as they flee. A voice screams two to four times with breaths between, from
 * single screams lifted out of the same recording (audio/panic/, made by
 * tools/prepare-panic-voices.py); each runner has their own pitch, plus a touch of Doppler.
 */
public final class PanicSounds {
    private static final int MAX_VOICES = 3;
    private static final float VOICE_REACH = 36;

    private record Clips(List<float[]> screams, int screamRate, List<float[]> crowd, int crowdRate) {}

    private static volatile Clips clips;
    private static boolean loading;
    private static SynthSound bed;
    private static final List<RunnerVoice> VOICES = new ArrayList<>();
    private static final Map<Integer, Long> LAST_VOICED = new HashMap<>();
    private static boolean panicking, detonated;
    private static long since, nextVoice;
    private static final Random RANDOM = new Random();

    private PanicSounds() {}

    /** Reads the recordings off the main thread, ahead of when they are needed. */
    public static void preload() {
        if (clips != null || loading) return;
        loading = true;
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        CompletableFuture.runAsync(() -> {
            try {
                clips = load(resources);
            } catch (IOException | RuntimeException e) {
                HeroesEvolved.LOGGER.error("Could not load the panic recordings", e);
            }
        }, Util.backgroundExecutor()).whenComplete((done, error) -> loading = false);
    }

    /** The sirens: the crowd rises all around and people start screaming as they run. */
    public static void panic(long now) {
        preload();
        panicking = true;
        detonated = false;
        since = now;
        nextVoice = now + 16;
    }

    /**
     * The flash: the crowd's scream peaks, and only the nearest voice goes on. Minecraft streams on
     * about eight channels and frees a stopped one only on its next tick, so this makes room now
     * for the blast half a second later.
     */
    public static void detonate() {
        detonated = true;
        VOICES.sort(Comparator.comparingDouble(RunnerVoice::distance));
        while (VOICES.size() > 1) Minecraft.getInstance().getSoundManager().stop(VOICES.removeLast());
    }

    public static void stop() {
        panicking = detonated = false;
        if (bed != null) Minecraft.getInstance().getSoundManager().stop(bed);
        bed = null;
        for (RunnerVoice voice : VOICES) Minecraft.getInstance().getSoundManager().stop(voice);
        VOICES.clear();
        LAST_VOICED.clear();
    }

    /** Every client tick while the vision runs. */
    public static void tick(Minecraft mc) {
        if (!panicking || mc.level == null || mc.player == null) return;
        Clips ready = clips;
        if (ready == null) return;
        long now = mc.level.getGameTime();
        if (bed == null) {
            bed = new SynthSound(id("panic_crowd"), "subtitles.heroesevolved.crowd_panic", SoundSource.AMBIENT,
                    () -> new Crowd(ready.crowd, ready.crowdRate));
            bed.set(0, 1);
            mc.getSoundManager().play(bed);
        }
        // Fades in over three seconds as the sirens wind up; louder still once the light falls.
        float target = detonated ? 0.5F : Math.min(0.32F, (now - since) / 60F * 0.32F);
        bed.set(bed.volume() + Mth.clamp(target - bed.volume(), -0.02F, 0.04F), 1);

        VOICES.removeIf(voice -> !mc.getSoundManager().isActive(voice));
        if (!detonated) voice(mc, ready, now, mc.player.getBoundingBox().inflate(28, 10, 28));
    }

    /**
     * A village running from a meltdown: the same single voices as Odessa's streets, riding villagers who run near
     * {@code from}, but no crowd all around (a village is a few people, not a city). Called every client tick while a
     * meltdown is near.
     */
    public static void village(Minecraft mc, Vec3 from) {
        if (panicking || mc.level == null || mc.player == null) return;
        preload();
        Clips ready = clips;
        if (ready == null) return;
        voice(mc, ready, mc.level.getGameTime(), new net.minecraft.world.phys.AABB(from, from).inflate(24, 10, 24));
    }

    /** One more runner screaming, if there is room and it is time. */
    private static void voice(Minecraft mc, Clips ready, long now, net.minecraft.world.phys.AABB where) {
        VOICES.removeIf(voice -> !mc.getSoundManager().isActive(voice));
        if (VOICES.size() >= MAX_VOICES || now < nextVoice || ready.screams.isEmpty()) return;
        Villager runner = runner(mc, now, where);
        if (runner != null) {
            LAST_VOICED.put(runner.getId(), now);
            float person = 0.86F + RANDOM.nextFloat() * 0.26F;
            long seed = RANDOM.nextLong();
            RunnerVoice voice = new RunnerVoice(runner, person, () -> new Voice(ready.screams, ready.screamRate, new Random(seed)));
            VOICES.add(voice);
            mc.getSoundManager().play(voice);
        }
        nextVoice = now + 12 + RANDOM.nextInt(22);
    }

    /** Someone running in {@code where} who has not screamed for a while, among the few closest to you. */
    private static Villager runner(Minecraft mc, long now, net.minecraft.world.phys.AABB where) {
        List<Villager> near = mc.level.getEntitiesOfClass(Villager.class, where,
                v -> v.isAlive() && v.position().subtract(v.xo, v.yo, v.zo).horizontalDistanceSqr() > 0.05 * 0.05
                        && now - LAST_VOICED.getOrDefault(v.getId(), -1000L) > 120
                        && VOICES.stream().noneMatch(voice -> voice.runner == v));
        if (near.isEmpty()) return null;
        near.sort(Comparator.comparingDouble(v -> v.distanceToSqr(mc.player)));
        return near.get(RANDOM.nextInt(Math.min(4, near.size())));
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, path); }

    // ------------------------------------------------------------------
    // The recordings
    // ------------------------------------------------------------------

    private static Clips load(ResourceManager resources) throws IOException {
        List<float[]> screams = new ArrayList<>();
        int screamRate = 32000;
        Map<ResourceLocation, Resource> found = new TreeMap<>(resources.listResources("audio/panic",
                location -> location.getNamespace().equals(HeroesEvolved.MOD_ID) && location.getPath().endsWith(".wav")));
        for (Resource resource : found.values()) {
            try (InputStream in = resource.open()) {
                Wav wav = Wav.read(in);
                screams.add(wav.samples);
                screamRate = wav.rate;
            }
        }
        List<float[]> crowd = new ArrayList<>();
        int crowdRate = 44100;
        for (String name : new String[] {"crowd_panic", "crowd_panic_2", "crowd_panic_3"}) {
            try (InputStream in = resources.open(id("sounds/intro/" + name + ".ogg")); JOrbisAudioStream ogg = new JOrbisAudioStream(in)) {
                int channels = ogg.getFormat().getChannels();
                crowdRate = (int) ogg.getFormat().getSampleRate();
                FloatArrayList samples = new FloatArrayList();
                while (ogg.readChunk(samples::add)) { }
                float[] mono = new float[samples.size() / channels];
                for (int i = 0; i < mono.length; i++) {
                    float sum = 0;
                    for (int c = 0; c < channels; c++) sum += samples.getFloat(i * channels + c);
                    mono[i] = sum / channels;
                }
                crowd.add(mono);
            }
        }
        return new Clips(List.copyOf(screams), screamRate, List.copyOf(crowd), crowdRate);
    }

    /** Just enough of WAV to read 16-bit PCM. */
    private record Wav(float[] samples, int rate) {
        static Wav read(InputStream raw) throws IOException {
            DataInputStream in = new DataInputStream(raw);
            byte[] tag = new byte[4];
            in.readFully(tag);
            if (!new String(tag).equals("RIFF")) throw new IOException("Not a WAV file");
            readInt(in);
            in.readFully(tag);
            int channels = 1, rate = 44100, bits = 16;
            while (true) {
                try { in.readFully(tag); } catch (EOFException e) { throw new IOException("WAV without data"); }
                int size = readInt(in);
                String chunk = new String(tag);
                if (chunk.equals("fmt ")) {
                    byte[] fmt = new byte[size];
                    in.readFully(fmt);
                    ByteBuffer b = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN);
                    if (b.getShort(0) != 1) throw new IOException("Only PCM WAV is supported");
                    channels = b.getShort(2);
                    rate = b.getInt(4);
                    bits = b.getShort(14);
                } else if (chunk.equals("data")) {
                    if (bits != 16) throw new IOException("Only 16-bit WAV is supported");
                    byte[] data = new byte[size];
                    in.readFully(data);
                    ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
                    float[] samples = new float[size / 2 / channels];
                    for (int i = 0; i < samples.length; i++) {
                        float sum = 0;
                        for (int c = 0; c < channels; c++) sum += b.getShort((i * channels + c) * 2) / 32768F;
                        samples[i] = sum / channels;
                    }
                    return new Wav(samples, rate);
                } else {
                    in.skipNBytes(size + (size & 1));
                }
            }
        }

        private static int readInt(DataInputStream in) throws IOException { return Integer.reverseBytes(in.readInt()); }
    }

    // ------------------------------------------------------------------
    // The crowd all around
    // ------------------------------------------------------------------

    /** Overlapping pieces of the crowd recording, each its own distance, direction and pitch. */
    static final class Crowd extends PcmStream {
        private static final int RATE = 44100;
        private final List<float[]> clips;
        private final float step;
        private final Grain[] grains = new Grain[8];
        private final Random random = new Random();
        private final Dsp.Reverb streets = new Dsp.Reverb(RATE, 1.4F, 2.2F, 0.45F);
        private final Dsp.Biquad lowCutLeft = Dsp.Biquad.highPass(RATE, 140, 0.7F), lowCutRight = Dsp.Biquad.highPass(RATE, 140, 0.7F);
        private final float[] wet = new float[2];
        private int untilNext;

        Crowd(List<float[]> clips, int clipRate) {
            super(RATE, 2);
            this.clips = clips;
            this.step = clipRate / (float) RATE;
        }

        @Override protected int render(float[] out, int frames) {
            for (int i = 0; i < frames; i++) {
                if (--untilNext <= 0) {
                    spawn();
                    untilNext = (int) (RATE * (0.18F + 0.35F * random.nextFloat()));
                }
                float left = 0, right = 0, send = 0;
                for (int g = 0; g < grains.length; g++) {
                    Grain grain = grains[g];
                    if (grain == null) continue;
                    float s = grain.next();
                    if (grain.done()) grains[g] = null;
                    left += s * grain.left;
                    right += s * grain.right;
                    send += s * grain.send;
                }
                streets.run(send, wet);
                out[2 * i] = Dsp.soft(lowCutLeft.run(left + wet[0] * 0.6F));
                out[2 * i + 1] = Dsp.soft(lowCutRight.run(right + wet[1] * 0.6F));
            }
            return frames;
        }

        private void spawn() {
            int slot = -1;
            for (int g = 0; g < grains.length; g++) if (grains[g] == null) { slot = g; break; }
            if (slot < 0 || clips.isEmpty()) return;
            float[] clip = clips.get(random.nextInt(clips.size()));
            float rate = step * (0.9F + 0.18F * random.nextFloat());
            int length = (int) (RATE * (1.6F + 1.6F * random.nextFloat()));
            length = Math.min(length, (int) ((clip.length - 2) / rate * 0.95F));
            if (length < RATE / 4) return;
            double start = random.nextFloat() * (clip.length - 2 - length * rate);
            boolean far = random.nextFloat() < 0.65F;
            float pan = random.nextFloat() * 1.8F - 0.9F;
            grains[slot] = new Grain(clip, start, rate, length,
                    far ? 0.35F + 0.25F * random.nextFloat() : 0.6F + 0.3F * random.nextFloat(),
                    far ? 1800 + 1200 * random.nextFloat() : 4500 + 2500 * random.nextFloat(),
                    far ? 0.8F : 0.35F, pan);
        }

        private static final class Grain {
            final float[] clip;
            final float rate, gain, left, right, send;
            final int length;
            final Dsp.LowPass distance;
            double position;
            int age;

            Grain(float[] clip, double start, float rate, int length, float gain, float cutoff, float send, float pan) {
                this.clip = clip;
                this.position = start;
                this.rate = rate;
                this.length = length;
                this.gain = gain;
                this.send = send;
                this.left = (float) Math.cos((pan + 1) * Math.PI / 4);
                this.right = (float) Math.sin((pan + 1) * Math.PI / 4);
                distance = new Dsp.LowPass(RATE, cutoff);
            }

            float next() {
                int i = (int) position;
                float f = (float) (position - i);
                float s = clip[i] + (clip[i + 1] - clip[i]) * f;
                position += rate;
                float u = age++ / (float) length;
                float envelope = Dsp.ramp(u, 0, 0.3F) * (1 - Dsp.ramp(u, 0.7F, 1));
                return distance.run(s) * envelope * gain;
            }

            boolean done() { return age >= length; }
        }
    }

    // ------------------------------------------------------------------
    // One person running
    // ------------------------------------------------------------------

    /** Two to four screams from one person, with breaths between, written out in full up front. */
    static final class Voice extends PcmStream {
        private final float[] track;
        private int at;

        Voice(List<float[]> screams, int rate, Random random) {
            super(rate, 1);
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < screams.size(); i++) order.add(i);
            java.util.Collections.shuffle(order, random);
            int count = 2 + random.nextInt(3);
            float level = 0.62F + 0.12F * random.nextFloat();
            FloatArrayList out = new FloatArrayList();
            pad(out, (int) (rate * (0.05F + 0.3F * random.nextFloat())));
            for (int k = 0; k < count; k++) {
                float[] scream = screams.get(order.get(k % order.size()));
                // Out of breath: later screams are sometimes cut short, with a fade so it never clicks.
                int keep = Math.max(Math.min(scream.length, (int) (0.3F * rate)), (int) (scream.length * (0.7F + 0.3F * random.nextFloat())));
                int fade = Math.min(keep, (int) (0.08F * rate));
                float gain = level * (0.85F + 0.15F * random.nextFloat());
                for (int i = 0; i < keep; i++) {
                    float tail = i < keep - fade || keep == scream.length ? 1 : (keep - i) / (float) fade;
                    out.add(scream[i] * gain * tail);
                }
                if (k < count - 1) pad(out, (int) (rate * (0.25F + 0.75F * random.nextFloat())));
            }
            track = out.toFloatArray();
        }

        private static void pad(FloatArrayList out, int samples) { for (int i = 0; i < samples; i++) out.add(0); }

        @Override protected int render(float[] out, int frames) {
            int made = Math.min(frames, track.length - at);
            System.arraycopy(track, at, out, 0, Math.max(0, made));
            at += Math.max(0, made);
            return made;
        }
    }

    /** A voice heard from wherever its runner is now, fading out if they are gone. */
    private static final class RunnerVoice extends SynthSound {
        final Entity runner;
        private final float person;
        private double lastDistance = -1;
        private boolean fading;

        RunnerVoice(Entity runner, float person, java.util.function.Supplier<PcmStream> stream) {
            super(id("panic_scream"), "subtitles.heroesevolved.panic_scream", SoundSource.NEUTRAL, stream);
            at(runner.getX(), runner.getEyeY(), runner.getZ(), VOICE_REACH);
            this.runner = runner;
            this.person = person;
            pitch = person;
        }

        double distance() { return lastDistance < 0 ? Double.MAX_VALUE : lastDistance; }

        @Override public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (fading || runner.isRemoved() || mc.level == null || runner.level() != mc.level) {
                fading = true;
                volume *= 0.5F;
                if (volume < 0.03F) stop();
                return;
            }
            x = runner.getX(); y = runner.getEyeY(); z = runner.getZ();
            double d = mc.gameRenderer.getMainCamera().getPosition().distanceTo(new Vec3(x, y, z));
            // Doppler: a little higher coming, a little lower going (blocks a tick to metres a second).
            if (lastDistance >= 0) pitch = person * (float) Mth.clamp(343 / (343 + (d - lastDistance) * 20), 0.97, 1.03);
            lastDistance = d;
        }
    }
}
