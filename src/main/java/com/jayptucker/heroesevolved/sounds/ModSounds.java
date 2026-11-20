package com.jayptucker.heroesevolved.sounds;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, HeroesEvolved.MOD_ID);

    public static final Supplier<SoundEvent> NEW_BEGINNING =
            SOUND_EVENTS.register("new_beginning", () ->
                    SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(
                                    HeroesEvolved.MOD_ID,
                                    "new_beginning"
                            )
                    )
            );

    public static final Supplier<SoundEvent> TIME_BLINK =
            SOUND_EVENTS.register("time_blink", () ->
                    SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(
                                    HeroesEvolved.MOD_ID,
                                    "time_blink"
                            )
                    )
            );

    public static final Supplier<SoundEvent> TIME_SLOW =
            SOUND_EVENTS.register("time_slow", () ->
                    SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(
                                    HeroesEvolved.MOD_ID,
                                    "time_slow"
                            )
                    )
            );

    public static final Supplier<SoundEvent> TIME_UNSLOW =
            SOUND_EVENTS.register("time_unslow", () ->
                    SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(
                                    HeroesEvolved.MOD_ID,
                                    "time_unslow"
                            )
                    )
            );

    /** Shared cue for activating or releasing local Time Slow/Time Stop. */
    public static final Supplier<SoundEvent> TIME_SLOW_STOP =
            SOUND_EVENTS.register("time_slow_stop", () ->
                    SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(
                                    HeroesEvolved.MOD_ID,
                                    "time_slow_stop"
                            )
                    )
            );
        
        public static final Supplier<SoundEvent> FLIGHT_LAUNCH =
            SOUND_EVENTS.register("flight_launch", () ->
                SoundEvent.createVariableRangeEvent(
                        ResourceLocation.fromNamespaceAndPath(
                                HeroesEvolved.MOD_ID,
                                "flight_launch"
                        )
                )
        );

        public static final DeferredHolder<SoundEvent, SoundEvent> FLIGHT_BOOST =
                SOUND_EVENTS.register("flight_boost", () ->
                        SoundEvent.createVariableRangeEvent(
                                ResourceLocation.fromNamespaceAndPath(
                                        HeroesEvolved.MOD_ID, 
                                        "flight_boost"
                                )
                        )
        );

    // Keep the server's delivery radius aligned with the sample's attenuation distance in sounds.json.
    public static final Supplier<SoundEvent> SKYBURST_BOOM = SOUND_EVENTS.register("skyburst_boom", () ->
            SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "skyburst_boom"), 192));

    private ModSounds() {
    }
    public static final Supplier<SoundEvent> SONIC_CRY = SOUND_EVENTS.register("sonic_cry", () ->
            SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "sonic_cry")));
    public static final Supplier<SoundEvent> SONIC_SCREAM = SOUND_EVENTS.register("sonic_scream", () ->
            SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "sonic_scream")));
    public static final Supplier<SoundEvent> SONIC_BURST = SOUND_EVENTS.register("sonic_burst", () ->
            SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "sonic_burst")));

    public static final Supplier<SoundEvent> NUCLEAR_SIREN = SOUND_EVENTS.register("nuclear_siren", () ->
            SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "nuclear_siren")));
    public static final Supplier<SoundEvent> CROWD_PANIC = SOUND_EVENTS.register("crowd_panic", () ->
            SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "crowd_panic")));
    public static final Supplier<SoundEvent> NUCLEAR_BLAST = SOUND_EVENTS.register("nuclear_blast", () ->
            SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "nuclear_blast")));
    public static final Supplier<SoundEvent> NUCLEAR_CHARGE = SOUND_EVENTS.register("nuclear_charge", () ->
            SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "nuclear_charge")));
    public static final Supplier<SoundEvent> SYLAR_WATCH = SOUND_EVENTS.register("sylar_watch", () ->
            SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "sylar_watch")));
    /** The record of Sylar's theme (tools/generate-sylar-music.py), a rare re-fight drop played by a jukebox. */
    public static final Supplier<SoundEvent> MUSIC_DISC_THE_COLLECTION = SOUND_EVENTS.register("music_disc.the_collection", () ->
            SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "music_disc.the_collection")));

    public static final Supplier<SoundEvent> VIBE_PULSE = vibeSound("vibe_pulse");
    public static final Supplier<SoundEvent> VIBE_BREACH = vibeSound("vibe_breach");
    public static final Supplier<SoundEvent> VIBE_SHUT = vibeSound("vibe_shut");
    public static final Supplier<SoundEvent> VIBE_ECHO = vibeSound("vibe_echo");
    public static final Supplier<SoundEvent> VIBE_COLLAPSE = vibeSound("vibe_collapse");

    /** Every power's signature cues ({@link PowerCue}), keyed by event path, e.g. {@code power.pyrokinesis.cast}. */
    public static final java.util.Map<String, Supplier<SoundEvent>> POWER_CUES = powerCues();

    private static java.util.Map<String, Supplier<SoundEvent>> powerCues() {
        java.util.Map<String, Supplier<SoundEvent>> cues = new java.util.HashMap<>();
        for (String power : PowerCue.POWERS) {
            for (PowerCue cue : PowerCue.values()) {
                if (PowerCue.has(power, cue)) cues.put(PowerCue.event(power, cue), vibeSound(PowerCue.event(power, cue)));
            }
        }
        return java.util.Map.copyOf(cues);
    }

    private static Supplier<SoundEvent> vibeSound(String id) {
        return SOUND_EVENTS.register(id, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, id)));
    }

    /** Story music (tools/generate-story-cues.py): sealed in a pod, 103 years later, the Helix Ward, and the waking. */
    public static final Supplier<SoundEvent> STORY_SEALED = story("story_sealed"), STORY_YEARS = story("story_years"),
            STORY_WARD = story("story_ward"), STORY_WAKE_MUSIC = story("story_wake_music");

    /**
     * The Last Day (tools/generate-lastday-music.py): the evening's music and the fight's layer over it (two stems of one
     * loop, played together), the ringing in your ears after the wave, and the crash of every window bursting at once.
     */
    public static final Supplier<SoundEvent> LASTDAY_EVENING = story("lastday_evening"), LASTDAY_RUN = story("lastday_run"),
            LASTDAY_RINGING = story("lastday_ringing"), LASTDAY_SHATTER = story("lastday_shatter");

    private static Supplier<SoundEvent> story(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
