package com.jayptucker.heroesevolved.effects;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Words over someone's head: a text display riding them, so it moves as they run. Odessa's crowd talks and shouts with
 * these (white for talk, bold red for shouts), and so does a village fleeing a meltdown. The caller keeps the display
 * and discards it when its time is up.
 */
public final class SpeechBubble {
    private SpeechBubble() {}

    /** Puts {@code text} over {@code speaker}'s head and returns the display, or null if it couldn't be placed. */
    public static Display.TextDisplay show(ServerLevel level, Entity speaker, String text, boolean shout, String tag) {
        return show(level, speaker, text, shout, tag, d -> {});
    }

    /** As {@link #show}, with {@code beforeAdd} told of the display just before it joins the level. */
    public static Display.TextDisplay show(ServerLevel level, Entity speaker, String text, boolean shout, String tag,
                                           java.util.function.Consumer<Display.TextDisplay> beforeAdd) {
        Display.TextDisplay display = EntityType.TEXT_DISPLAY.create(level);
        if (display == null) return null;
        Component words = Component.literal(text).withStyle(shout ? ChatFormatting.RED : ChatFormatting.WHITE);
        if (shout) words = words.copy().withStyle(ChatFormatting.BOLD);
        CompoundTag nbt = new CompoundTag();
        nbt.putString("text", Component.Serializer.toJson(words, level.registryAccess()));
        nbt.putString("billboard", "center");
        // Vanilla reads alignment even when absent and logs an error without it.
        nbt.putString("alignment", "center");
        nbt.putInt("background", 0xA0101418);
        nbt.putInt("line_width", 150);
        CompoundTag transformation = new CompoundTag();
        transformation.put("left_rotation", floats(0, 0, 0, 1));
        transformation.put("right_rotation", floats(0, 0, 0, 1));
        transformation.put("translation", floats(0, 0.35F, 0));
        transformation.put("scale", floats(0.6F, 0.6F, 0.6F));
        nbt.put("transformation", transformation);
        display.load(nbt);
        display.moveTo(speaker.getX(), speaker.getY() + speaker.getBbHeight(), speaker.getZ());
        display.addTag(tag);
        beforeAdd.accept(display);
        if (!level.addFreshEntity(display)) return null;
        display.startRiding(speaker, true);
        return display;
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float v : values) list.add(FloatTag.valueOf(v));
        return list;
    }
}
