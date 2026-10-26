package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.data.ModDataAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Durable, idempotent story flags available to future missions and dialogue. */
public final class TemporalStoryService {
    public static final int VISION = 1, RESCUE = 2, ANCHOR = 4, REWRITE = 8, TRAVEL = 16;
    private TemporalStoryService() {}
    public static boolean has(ServerPlayer p, int flag) { return (p.getData(ModDataAttachments.TEMPORAL_STORY) & flag) == flag; }
    public static void reach(ServerPlayer p, int flag) {
        if (has(p, flag)) return;
        p.setData(ModDataAttachments.TEMPORAL_STORY, p.getData(ModDataAttachments.TEMPORAL_STORY) | flag);
        String name = switch (flag) { case VISION -> "vision"; case RESCUE -> "rescue"; case ANCHOR -> "anchor"; case REWRITE -> "rewrite"; default -> "travel"; };
        p.sendSystemMessage(Component.translatable("story.heroesevolved.time." + name).withStyle(net.minecraft.ChatFormatting.GOLD));
        var advancement = p.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath("heroesevolved", "time/" + name));
        if (advancement != null) p.getAdvancements().award(advancement, "experienced");
    }
}
