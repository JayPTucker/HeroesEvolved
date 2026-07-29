package com.jayptucker.heroesevolved.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** World-persistent state for the global Eclipse event. */
public final class EclipseSavedData extends SavedData {
    private static final String DATA_NAME = "heroesevolved_eclipse";
    private static final long NO_TIME_SCHEDULED = -1L;

    private long eclipseId;
    private long nextStartGameTime = NO_TIME_SCHEDULED;
    private long endGameTime = NO_TIME_SCHEDULED;

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
        data.nextStartGameTime = tag.contains("next_start_game_time")
                ? tag.getLong("next_start_game_time")
                : NO_TIME_SCHEDULED;
        data.endGameTime = tag.contains("end_game_time")
                ? tag.getLong("end_game_time")
                : NO_TIME_SCHEDULED;
        return data;
    }

    @Override
    public CompoundTag save(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        tag.putLong("eclipse_id", eclipseId);
        tag.putLong("next_start_game_time", nextStartGameTime);
        tag.putLong("end_game_time", endGameTime);
        return tag;
    }

    public boolean isActive() {
        return endGameTime != NO_TIME_SCHEDULED;
    }

    public long eclipseId() {
        return eclipseId;
    }

    public long nextStartGameTime() {
        return nextStartGameTime;
    }

    public long endGameTime() {
        return endGameTime;
    }

    public void scheduleFirstEclipse(long gameTime, long intervalTicks) {
        if (nextStartGameTime != NO_TIME_SCHEDULED) {
            return;
        }

        nextStartGameTime = gameTime + intervalTicks;
        setDirty();
    }

    public void begin(long gameTime, long durationTicks, long intervalTicks) {
        eclipseId++;
        endGameTime = gameTime + durationTicks;
        nextStartGameTime = gameTime + intervalTicks;
        setDirty();
    }

    public void end() {
        if (!isActive()) {
            return;
        }

        endGameTime = NO_TIME_SCHEDULED;
        setDirty();
    }
}
