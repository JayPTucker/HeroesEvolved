package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.data.EclipseCalendar;
import com.jayptucker.heroesevolved.data.EclipseTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;

/** Client-only copy of the server's current Eclipse timeline. */
public final class ClientEclipseState {
    private static EclipseTimeline timeline = EclipseTimeline.NONE;

    private ClientEclipseState() {
    }

    public static boolean isEclipseActive() {
        return timeline.scheduled();
    }

    public static EclipseTimeline timeline() {
        return timeline;
    }

    public static void set(EclipseTimeline value, int intervalDays) {
        timeline = value;
        EclipseCalendar.setIntervalDays(intervalDays);
    }

    public static void clear() {
        timeline = EclipseTimeline.NONE;
    }

    /** Current client time in ticks, or a negative value when there is no Overworld sky to show. */
    public static double now(float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(Level.OVERWORLD) || !timeline.scheduled()) return -1;
        return level.getGameTime() + partialTick;
    }

    /** Strength of the Eclipse shadow over this level right now, 0 to 1. Only the Overworld has one. */
    public static float darkness(ClientLevel level, float partialTick) {
        if (!timeline.scheduled() || !level.dimension().equals(Level.OVERWORLD)) return 0;
        return timeline.darkness(level.getGameTime() + partialTick);
    }

    public static float darkness(float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0 : darkness(level, partialTick);
    }
}
