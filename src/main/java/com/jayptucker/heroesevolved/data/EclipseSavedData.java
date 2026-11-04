package com.jayptucker.heroesevolved.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** World-persistent state for the global Eclipse event. */
public final class EclipseSavedData extends SavedData {
    private static final String DATA_NAME = "heroesevolved_eclipse";
    private static final long NONE = -1L;

    private long eclipseId;
    private EclipseTimeline timeline = EclipseTimeline.NONE;
    /** Day an Eclipse last began, so a day never hosts two and a skipped one is not replayed. */
    private long lastEclipseDay = NONE;
    /** Last phase announced, so a restart mid-Eclipse does not repeat broadcasts or rolls. */
    private EclipseTimeline.Phase announced = EclipseTimeline.Phase.NONE;

    public static EclipseSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(EclipseSavedData::new, EclipseSavedData::load),
                DATA_NAME
        );
    }

    private static EclipseSavedData load(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        EclipseSavedData data = new EclipseSavedData();
        data.eclipseId = tag.getLong("eclipse_id");
        // Worlds saved before the phased Eclipse only stored an end time; they simply resume
        // on the day-based calendar.
        if (tag.contains("start_game_time")) {
            data.timeline = new EclipseTimeline(
                    tag.getLong("start_game_time"),
                    tag.getInt("approach_ticks"),
                    tag.getInt("totality_ticks"),
                    tag.getInt("departure_ticks")
            );
        }
        data.lastEclipseDay = tag.contains("last_eclipse_day") ? tag.getLong("last_eclipse_day") : NONE;
        data.announced = EclipseTimeline.Phase.values()[Math.floorMod(
                tag.getInt("announced_phase"), EclipseTimeline.Phase.values().length)];
        return data;
    }

    @Override
    public CompoundTag save(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        tag.putLong("eclipse_id", eclipseId);
        if (timeline.scheduled()) {
            tag.putLong("start_game_time", timeline.startGameTime());
            tag.putInt("approach_ticks", timeline.approachTicks());
            tag.putInt("totality_ticks", timeline.totalityTicks());
            tag.putInt("departure_ticks", timeline.departureTicks());
        }
        tag.putLong("last_eclipse_day", lastEclipseDay);
        tag.putInt("announced_phase", announced.ordinal());
        return tag;
    }

    public boolean isActive() {
        return timeline.scheduled();
    }

    public long eclipseId() {
        return eclipseId;
    }

    public EclipseTimeline timeline() {
        return timeline;
    }

    public long lastEclipseDay() {
        return lastEclipseDay;
    }

    public EclipseTimeline.Phase announced() {
        return announced;
    }

    public void announce(EclipseTimeline.Phase phase) {
        if (announced != phase) {
            announced = phase;
            setDirty();
        }
    }

    public void begin(EclipseTimeline timeline, long day) {
        eclipseId++;
        this.timeline = timeline;
        lastEclipseDay = day;
        announced = EclipseTimeline.Phase.NONE;
        setDirty();
    }

    /** Moves the running Eclipse to a new timeline, keeping its id so nobody rolls twice. */
    public void retime(EclipseTimeline timeline) {
        this.timeline = timeline;
        setDirty();
    }

    /** Time was wound back before the last Eclipse day; let the calendar fire again. */
    public void rewindCalendar(long day) {
        if (lastEclipseDay > day) {
            lastEclipseDay = NONE;
            setDirty();
        }
    }

    public void end() {
        if (!isActive()) {
            return;
        }

        timeline = EclipseTimeline.NONE;
        announced = EclipseTimeline.Phase.NONE;
        setDirty();
    }
}
