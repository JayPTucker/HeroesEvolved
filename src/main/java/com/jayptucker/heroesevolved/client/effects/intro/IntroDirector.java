package com.jayptucker.heroesevolved.client.effects.intro;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.sound.BunkerAlarm;
import com.jayptucker.heroesevolved.client.sound.CitySiren;
import com.jayptucker.heroesevolved.client.sound.PanicSounds;
import com.jayptucker.heroesevolved.client.sound.SynthSound;
import com.jayptucker.heroesevolved.gui.TimeIntroCaption;
import com.jayptucker.heroesevolved.network.ClientCameraShakeState;
import com.jayptucker.heroesevolved.network.TimeIntroPayload;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.time.intro.IntroBlast;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.function.Supplier;

/**
 * Runs the show on the client: cues from the server, the sound of the city's last half-minute, the
 * haze and the light, and when to draw what. In the Time vision, time stops a few blocks from the
 * wave and runs the explosion backwards; on the avenue, runners are simply pulled home.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class IntroDirector {
    private static SynthSound siren;
    private static Loop hum, roar;
    private static Loop blast;
    private static boolean rewindHeard;

    private IntroDirector() {}

    public static void cue(int stage) {
        cue(stage, java.util.List.of());
    }

    /** {@code players}: with FREEZE, the entity ids of the visitors whose power stops time, drawn on them as it runs back. */
    public static void cue(int stage, java.util.List<Integer> players) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        switch (stage) {
            case TimeIntroPayload.CANCEL -> { clear(); TimeIntroCaption.clear(); TimeIntroCaption.clearHint(); }
            case TimeIntroPayload.RETURN, TimeIntroPayload.AVENUE_ALL, TimeIntroPayload.AVENUE_SOME, TimeIntroPayload.AVENUE_NONE -> {
                clear();
                TimeIntroCaption.show(stage);
                if (stage == TimeIntroPayload.RETURN) instruct("vision_home", 300, JOURNAL);
                else if (stage != TimeIntroPayload.AVENUE_NONE) instruct("avenue_home", 300);
                else TimeIntroCaption.clearHint();
            }
            case TimeIntroPayload.NIGHT_THREE -> instruct("night_three", -1);
            case TimeIntroPayload.ANNEX -> instruct("annex", -1);
            case TimeIntroPayload.BUNKER_ALARM -> BunkerAlarm.play(true);
            case TimeIntroPayload.BUNKER_HALL_ALARM -> BunkerAlarm.play(false);
            case TimeIntroPayload.BUNKER_POWER -> BunkerAlarm.stop(true);
            case TimeIntroPayload.BUNKER_QUIET -> BunkerAlarm.stop(false);
            case TimeIntroPayload.ARRIVAL, TimeIntroPayload.AVENUE_ARRIVAL -> {
                clear();
                IntroScene.active = true;
                IntroScene.vision = stage == TimeIntroPayload.ARRIVAL;
                IntroScene.arrival = now;
                // The avenue has had its sirens from the start; the vision's start later.
                if (!IntroScene.vision) siren = sirens();
                else PanicSounds.preload();
                TimeIntroCaption.show(stage);
                instruct(IntroScene.vision ? "vision_arrival" : "avenue", -1);
            }
            case TimeIntroPayload.GLOW -> {
                if (hum == null) hum = play(new Loop(() -> SoundEvents.BEACON_AMBIENT, SoundSource.AMBIENT, 0.1F, 0.6F));
                TimeIntroCaption.show(stage);
                instruct("vision_glow", -1);
            }
            case TimeIntroPayload.SIREN -> {
                if (siren == null) siren = sirens();
                PanicSounds.panic(now);
                TimeIntroCaption.show(stage);
            }
            case TimeIntroPayload.DETONATION, TimeIntroPayload.AVENUE_LIGHT -> {
                if (!IntroScene.active) { IntroScene.active = true; IntroScene.vision = stage == TimeIntroPayload.DETONATION; }
                IntroScene.detonation = now;
                stop(hum); hum = null;
                if (siren != null) siren.set(0.25F, 1);
                PanicSounds.detonate();
                roar = play(new Loop(() -> SoundEvents.ELYTRA_FLYING, SoundSource.AMBIENT, 0.05F, 0.5F));
                TimeIntroCaption.show(stage);
                // From the flash on, nothing to read: just watch.
                TimeIntroCaption.clearHint();
            }
            case TimeIntroPayload.BLAST_SOUND -> {
                if (IntroScene.active) {
                    stop(blast);
                    blast = play(new Loop(ModSounds.NUCLEAR_BLAST, SoundSource.AMBIENT, 1, 1));
                    blast.once();
                }
            }
            case TimeIntroPayload.FREEZE -> freeze(now, players);
            case TimeIntroPayload.PROLOGUE_APARTMENT -> {
                clear();
                TimeIntroCaption.clear();
                TimeIntroCaption.clearHint();
                TimeIntroCaption.showKey("intro.heroesevolved.prologue_wake");
            }
            case TimeIntroPayload.PROLOGUE_EVENING -> {
                clear();
                IntroScene.active = true;
                IntroScene.prologue = true;
                IntroScene.arrival = now;
                PanicSounds.preload();
                instruct("prologue_evening", 600);
            }
            case TimeIntroPayload.PROLOGUE_LIGHT -> {
                if (!IntroScene.prologue) { clear(); IntroScene.active = IntroScene.prologue = true; IntroScene.arrival = now; }
                IntroScene.light = now;
                if (!players.isEmpty()) IntroScene.run = Math.max(1, players.getFirst());
                if (hum == null) hum = play(new Loop(() -> SoundEvents.BEACON_AMBIENT, SoundSource.AMBIENT, 0.1F, 0.6F));
                // First just the light; where to go comes with the sirens.
                TimeIntroCaption.showKey("intro.heroesevolved.prologue_light", true);
            }
            case TimeIntroPayload.PROLOGUE_SIRENS -> {
                if (!IntroScene.underground) instruct("prologue_run", -1);
                if (siren == null) siren = sirens();
                PanicSounds.panic(now);
                if (IntroScene.underground) underground();
            }
            case TimeIntroPayload.PROLOGUE_UNDERGROUND -> {
                IntroScene.underground = true;
                underground();
                instruct("prologue_hall", -1);
            }
            case TimeIntroPayload.PROLOGUE_POD -> {
                IntroScene.underground = true;
                if (!IntroScene.prologue) { IntroScene.active = IntroScene.prologue = true; }
                if (siren == null) siren = sirens();
                PanicSounds.panic(now);
                underground();
                TimeIntroCaption.clearHint();
            }
            case TimeIntroPayload.PROLOGUE_BLAST -> {
                // Through rock and steel: one deep blow, and then the city goes quiet.
                stop(blast);
                blast = play(new Loop(ModSounds.NUCLEAR_BLAST, SoundSource.AMBIENT, 0.55F, 0.62F));
                blast.once();
                stop(siren); siren = null;
                stop(hum); hum = null;
                PanicSounds.stop();
            }
            case TimeIntroPayload.PROLOGUE_NOAH -> TimeIntroCaption.clearHint();
            case TimeIntroPayload.PROLOGUE_END -> { clear(); TimeIntroCaption.clearHint(); }
            default -> { }
        }
    }

    /**
     * Time stops. Everything held: the wave, the dust, the pieces in the air, the sound. Only the clockwork
     * of the power under whoever's power it is still moves ({@link RewindSigil}).
     */
    private static void freeze(long now, java.util.List<Integer> players) {
        if (!IntroScene.active || IntroScene.detonation < 0) return;
        IntroScene.frozenAt = IntroScene.t(0);
        IntroScene.freeze = now;
        RewindSigil.begin(players);
        rewindHeard = false;
        stop(roar); roar = null;
        stop(blast); blast = null;
        stop(siren); siren = null;
        PanicSounds.stop();
        ClientCameraShakeState.reset();
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5F, 1));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_DEACTIVATE, 0.4F, 1));
        TimeIntroCaption.show(TimeIntroPayload.FREEZE);
    }

    private static final Object JOURNAL = new Object();
    /** The instruction line, by key: what to do now. {@code JOURNAL} in the arguments becomes the Journal key. */
    private static String instruction;

    private static void instruct(String key, int ticks, Object... args) {
        Object[] resolved = new Object[args.length];
        for (int i = 0; i < args.length; i++)
            resolved[i] = args[i] == JOURNAL ? net.minecraft.network.chat.Component.keybind("key.heroesevolved.open_journal") : args[i];
        instruction = key;
        TimeIntroCaption.hint(net.minecraft.network.chat.Component.translatable("hint.heroesevolved." + key, resolved), ticks);
    }

    /** Down in the safe house: the sirens and the crowd far above, the light's hum barely there. */
    private static void underground() {
        if (siren != null) siren.set(0.16F, 0.92F);
        if (hum != null) hum.set(0.03F, 0.5F);
        PanicSounds.muffle(true);
    }

    private static void clear() {
        stop(siren); stop(hum); stop(roar); stop(blast);
        siren = null;
        hum = roar = blast = null;
        PanicSounds.stop();
        CityShatter.reset();
        RewindSigil.reset();
        CrowdRewind.reset();
        IntroScene.reset();
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        TimeIntroCaption.clear();
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!IntroScene.active) return;
        if (mc.level == null || !TimeIntroService.isCity(mc.level)) { clear(); return; }
        float t = IntroScene.t(0);
        CityShatter.tick(t);
        if (IntroScene.prologue) PanicSounds.tick(mc);
        if (IntroScene.vision) {
            PanicSounds.tick(mc);
            CrowdRewind.record(mc);
            CrowdRewind.tick(mc);
        }
        if (hum != null && !IntroScene.underground) {
            float g = BlastRenderer.glowStrength(t);
            hum.set(0.08F + 0.55F * g, 0.55F + 0.5F * g);
        }
        if (roar != null && mc.player != null) {
            double distance = IntroBlast.distance(mc.player.getX(), mc.player.getZ()) - IntroBlast.front(t);
            roar.set((float) Mth.clamp(1 - distance / 150, 0.06, 1), (float) Mth.clamp(0.45 + (1 - distance / 150) * 0.25, 0.45, 0.8));
            // The ground shakes harder the closer the wall comes.
            if (!IntroScene.stopped() && distance < 90) ClientCameraShakeState.start((float) (0.05 + 0.4 * (1 - distance / 90)), 6);
        }
        if (IntroScene.rewinding(0) && !rewindHeard) {
            rewindHeard = true;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.TIME_UNSLOW.get(), 0.6F, 1));
        }
    }

    // ------------------------------------------------------------------
    // Haze and drawing
    // ------------------------------------------------------------------

    /** The air this frame, worked out once for the city's fog and for the cloud, so they always match. */
    private static void atmosphere(ViewportEvent event) {
        float t = IntroScene.active ? IntroScene.t((float) event.getPartialTick()) : -1;
        Vec3 camera = event.getCamera().getPosition();
        BlastRenderer.atmosphere(t, IntroBlast.distance(camera.x, camera.z), Minecraft.getInstance().gameRenderer.getRenderDistance());
    }

    @SubscribeEvent public static void fogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !TimeIntroService.isCity(mc.level)) return;
        atmosphere(event);
        event.setRed(BlastRenderer.FOG[0]);
        event.setGreen(BlastRenderer.FOG[1]);
        event.setBlue(BlastRenderer.FOG[2]);
    }

    @SubscribeEvent public static void fog(ViewportEvent.RenderFog event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !TimeIntroService.isCity(mc.level)) return;
        atmosphere(event);
        event.setFarPlaneDistance(Math.min(BlastRenderer.fogEnd, event.getFarPlaneDistance()));
        event.setNearPlaneDistance(Math.min(BlastRenderer.fogStart, event.getFarPlaneDistance() * 0.55F));
        event.setCanceled(true);
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!IntroScene.active || mc.level == null || !TimeIntroService.isCity(mc.level)) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float t = IntroScene.t(partial);
        Vec3 camera = event.getCamera().getPosition();
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            BlastRenderer.opaque(camera, t, event.getFrustum());
            CityShatter.render(camera, t, mc.renderBuffers().bufferSource());
            CrowdRewind.render(camera, partial, event.getPoseStack(), mc.renderBuffers().bufferSource());
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            BlastRenderer.veil(camera, t, event.getCamera().rotation());
            BlastRenderer.glowing(camera, t);
            RewindSigil.render(camera, partial);
            CrowdRewind.trails(camera, partial);
        }
    }

    // ------------------------------------------------------------------
    // Sound
    // ------------------------------------------------------------------

    private static Loop play(Loop sound) {
        Minecraft.getInstance().getSoundManager().play(sound);
        return sound;
    }

    /** The city's air-raid sirens, made live (CitySiren): they wind up from rest and never loop. */
    private static SynthSound sirens() {
        SynthSound sound = new SynthSound(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "nuclear_siren"),
                "subtitles.heroesevolved.nuclear_siren", SoundSource.AMBIENT, CitySiren::new)
                .whileTrue(() -> IntroScene.active && Minecraft.getInstance().level != null && TimeIntroService.isCity(Minecraft.getInstance().level));
        sound.set(0.75F, 1);
        Minecraft.getInstance().getSoundManager().play(sound);
        return sound;
    }

    private static void stop(SoundInstance sound) {
        if (sound != null) Minecraft.getInstance().getSoundManager().stop(sound);
    }

    /** A sound heard everywhere in the scene at once, whose volume and pitch the director moves. */
    private static final class Loop extends AbstractTickableSoundInstance {
        Loop(Supplier<SoundEvent> sound, SoundSource source, float volume, float pitch) {
            super(sound.get(), source, SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
            this.volume = volume;
            this.pitch = pitch;
        }
        void set(float volume, float pitch) { this.volume = volume; this.pitch = pitch; }
        void once() { looping = false; }
        @Override public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (!IntroScene.active || mc.level == null || !TimeIntroService.isCity(mc.level)) stop();
        }
    }
}
