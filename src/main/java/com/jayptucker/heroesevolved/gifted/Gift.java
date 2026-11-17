package com.jayptucker.heroesevolved.gifted;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * A mob's awakened power, saved in its own persistent data ({@link #TAG}) so it survives unloading, restarts and a
 * villager turning into an illager.
 *
 * @param power  the power it uses
 * @param rarity how strong it is
 * @param evil   whose side it is on: an evil gift hurts players, villagers and golems; a good one defends them
 * @param name   its generated name ("Hollis the Ashen")
 * @param origin how it came by the gift
 * @param home   the bell of the village it belongs to (renegades stay near it), or null
 */
public record Gift(ResourceLocation power, Rarity rarity, boolean evil, String name, Origin origin, BlockPos home) {
    public static final String TAG = HeroesEvolved.MOD_ID + "_gifted";

    /** How a mob came by its gift. */
    public enum Origin {
        /** A villager whose gift woke and who stayed good: their village's guardian. */
        GUARDIAN,
        /** A villager whose gift woke and turned them: now an illager terrorising their own village. */
        RENEGADE,
        /** A raider made a champion of its raid. */
        RAID,
        /** The leader of a pillager patrol. */
        PATROL,
        /** A humanoid monster that spawned with a gift. */
        WILD,
        /** Given by an admin command. */
        ADMIN
    }

    public static Gift read(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(TAG)) return null;
        CompoundTag tag = data.getCompound(TAG);
        ResourceLocation power = ResourceLocation.tryParse(tag.getString("power"));
        if (power == null) return null;
        Origin origin;
        try { origin = Origin.valueOf(tag.getString("origin")); } catch (IllegalArgumentException e) { origin = Origin.WILD; }
        return new Gift(power, Rarity.of(tag.getInt("rarity")), tag.getBoolean("evil"), tag.getString("name"), origin,
                tag.contains("home") ? BlockPos.of(tag.getLong("home")) : null);
    }

    public void write(Entity entity) {
        CompoundTag tag = new CompoundTag();
        tag.putString("power", power.toString());
        tag.putInt("rarity", rarity.ordinal());
        tag.putBoolean("evil", evil);
        tag.putString("name", name);
        tag.putString("origin", origin.name());
        if (home != null) tag.putLong("home", home.asLong());
        // Flags kept beside the gift (Regeneration's second life) survive a rewrite.
        CompoundTag old = entity.getPersistentData().getCompound(TAG);
        if (old.getBoolean("second")) tag.putBoolean("second", true);
        entity.getPersistentData().put(TAG, tag);
    }

    public static void clear(Entity entity) { entity.getPersistentData().remove(TAG); }

    public Gift withHome(BlockPos home) { return new Gift(power, rarity, evil, name, origin, home); }
    public Gift withEvil(boolean evil, Origin origin) { return new Gift(power, rarity, evil, name, origin, home); }

    /** Its power's short name, for messages ("Pyrokinesis"). */
    public String powerName() { return GiftNames.powerName(power); }

    /** Regeneration's second life, once spent. */
    public static boolean spentSecondLife(Entity entity) { return entity.getPersistentData().getCompound(TAG).getBoolean("second"); }
    public static void spendSecondLife(Entity entity) {
        CompoundTag tag = entity.getPersistentData().getCompound(TAG);
        tag.putBoolean("second", true);
        entity.getPersistentData().put(TAG, tag);
    }
}
