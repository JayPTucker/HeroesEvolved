package com.jayptucker.heroesevolved.progression;

import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.Map;

/**
 * The notes, logs and letters hidden in the world's structures. The first time a player picks one up
 * (or reads it on a lectern) it is a discovery: a skill point, up to {@link PowerJourney#MAX_STORY_POINTS},
 * and some Mastery. A note is known by its title and its author, so a book signed by the player
 * themself never counts.
 */
public final class StoryDiscoveries {
    public static final int MASTERY = 40;
    /** Title to author, for every story note the mod places. */
    public static final Map<String, String> NOTES = Map.ofEntries(
            Map.entry("Stasis Wing: Orientation", "Dr. M. Suresh"),
            Map.entry("Lockdown Order 7", "N. Bennet"),
            Map.entry("Day 2,190", "Dr. M. Suresh"),
            Map.entry("Assay: Helixite", "Pinehearst liaison"),
            Map.entry("Containment: G. Gray", "Security"),
            Map.entry("A Note, Folded Small", "H. N."),
            Map.entry("For Claire", "N. Bennet"),
            Map.entry("Shift Notes", "Checkpoint B"),
            Map.entry("Dad", "Claire"),
            Map.entry("I Painted It", "I. Mendez"),
            Map.entry("To Whoever Finds This", "A neighbor"),
            Map.entry("Day 9", "Unsigned"),
            Map.entry("The Men in Suits", "R.")
    );
    private static final int SCAN_INTERVAL = 40;

    private StoryDiscoveries() {}

    /** Every couple of seconds, checks the player's inventory for notes they have not found yet. */
    public static void tick(ServerPlayer player) {
        if ((player.tickCount + player.getId()) % SCAN_INTERVAL != 0) return;
        for (ItemStack stack : player.getInventory().items) found(player, stack);
    }

    /** Records {@code stack} as a discovery if it is a story note the player has not found. Returns true if new. */
    public static boolean found(ServerPlayer player, ItemStack stack) {
        String title = title(player, stack);
        if (title == null) return false;
        PowerJourney journey = player.getData(ModDataAttachments.POWER_JOURNEY);
        if (journey.discoveries().contains(title)) return false;
        boolean point = journey.storyPoints() < PowerJourney.MAX_STORY_POINTS;
        player.setData(ModDataAttachments.POWER_JOURNEY, journey.discover(title));

        int found = journey.discoveries().size() + 1;
        player.displayClientMessage(Component.literal("Story found: " + title + " (" + found + "/" + NOTES.size() + ")")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        if (point) player.displayClientMessage(Component.literal(" + 1 skill point for your power")
                .withStyle(ChatFormatting.AQUA), false);
        player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.2F);
        boolean awakened = PlayerAbilityService.getData(player).assignedPower()
                .map(entry -> entry.getValue().isUnlocked()).orElse(false);
        if (awakened) MasteryService.grantMastery(player, MASTERY);
        SkillTreeService.sync(player);
        return true;
    }

    private static String title(ServerPlayer player, ItemStack stack) {
        WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (content == null) return null;
        String title = content.title().raw();
        String author = NOTES.get(title);
        return author != null && author.equals(content.author())
                && !content.author().equals(player.getGameProfile().getName()) ? title : null;
    }
}
