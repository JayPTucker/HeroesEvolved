package com.jayptucker.heroesevolved.sounds;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
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
        
        public static final Supplier<SoundEvent> FLIGHT_LAUNCH =
            SOUND_EVENTS.register("flight_launch", () ->
                SoundEvent.createVariableRangeEvent(
                        ResourceLocation.fromNamespaceAndPath(
                                HeroesEvolved.MOD_ID,
                                "flight_launch"
                        )
                )
        );

    private ModSounds() {
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
