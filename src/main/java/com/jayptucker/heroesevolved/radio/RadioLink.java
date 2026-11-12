package com.jayptucker.heroesevolved.radio;

import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.central.WorldlyPosition;
import com.codinglitch.simpleradio.routers.Receiver;
import com.codinglitch.simpleradio.routers.Transmitter;
import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The one place that speaks to Simple Radio (CodinGlitch). It has no published API, so this uses its internal radio
 * network: a frequency has transmitters and receivers, and a transmitter takes raw audio. Every call here is guarded:
 * if Simple Radio changes and something here breaks, it logs once and the caller falls back to subtitles alone.
 */
final class RadioLink {
    private static boolean broken;

    private RadioLink() {}

    static boolean available() { return !broken; }

    private static void fail(String what, Throwable t) {
        if (!broken) HeroesEvolved.LOGGER.warn("Simple Radio link failed ({}); distress calls fall back to subtitles", what, t);
        broken = true;
    }

    private static Frequency band(String frequency) {
        return ServerSimpleRadioApi.getInstance().frequencies().getOrCreate(frequency, Frequency.Modulation.FREQUENCY);
    }

    /** A transmitter on {@code frequency} (FM) at {@code at}, or null if the radio network can't be reached. */
    static Object open(ServerLevel level, BlockPos at, UUID id, String frequency) {
        if (broken) return null;
        try {
            return band(frequency).addTransmitter(id, WorldlyPosition.of(at, level));
        } catch (Throwable t) {
            fail("open", t);
            return null;
        }
    }

    /** Pushes 48 kHz mono audio into a transmitter; the radio mod frames it and sends it to every tuned radio in range. */
    static void push(Object transmitter, short[] samples) {
        if (broken || !(transmitter instanceof Transmitter t)) return;
        try {
            t.push(samples);
        } catch (Throwable e) {
            fail("push", e);
        }
    }

    static void close(String frequency, UUID id) {
        if (broken) return;
        try {
            band(frequency).removeTransmitter(id);
        } catch (Throwable t) {
            fail("close", t);
        }
    }

    /**
     * Where everything tuned to {@code frequency} is listening: a radio someone holds (its owner) or a radio block. Each
     * entry is the listening entity (or null) and its position.
     */
    static List<Listening> listeners(ServerLevel level, String frequency) {
        List<Listening> out = new ArrayList<>();
        if (broken) return out;
        try {
            for (Receiver r : band(frequency).getReceivers()) {
                if (r == null || !r.isValid()) continue;
                Entity owner = r.getOwner();
                WorldlyPosition at = r.getPosition() != null ? r.getPosition() : r.getLocation();
                if (owner != null && owner.level() == level) out.add(new Listening(owner, owner.position()));
                else if (at != null && at.level == level) out.add(new Listening(null, new Vec3(at.x, at.y, at.z)));
            }
        } catch (Throwable t) {
            fail("listeners", t);
        }
        return out;
    }

    record Listening(Entity holder, Vec3 at) {}
}
