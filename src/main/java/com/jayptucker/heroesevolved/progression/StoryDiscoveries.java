package com.jayptucker.heroesevolved.progression;

import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The notes, logs and letters hidden in the world's structures. The first time a player picks one up
 * (or reads it on a lectern) it is a discovery: a skill point, up to {@link PowerJourney#MAX_STORY_POINTS},
 * and some Mastery. A note is known by its title and its author, so a book signed by the player
 * themself never counts.
 */
public final class StoryDiscoveries {
    public static final int MASTERY = 40;
    public static final String SIGNAL_TESTIMONY = "The Last Signal";
    public static final String SIGNAL_AUTHOR = "Mara and Ivo";

    /**
     * Where a note is available. Archived bunker books and past rewards may still exist in older saves.
     */
    public enum Place { BUNKER, RUINS, ARCHIVE, PAST }

    public record Note(String title, String author, Place place) {}

    /** Every story note the mod places, in the order the story reads best (the journal's order). */
    public static final List<Note> ORDER = List.of(
            new Note("Stasis Wing: Orientation", "Dr. M. Suresh", Place.BUNKER),
            new Note("Lockdown Order 7", "N. Bennet", Place.BUNKER),
            new Note("Day 2,190", "Dr. M. Suresh", Place.ARCHIVE),
            new Note("I Painted It", "I. Mendez", Place.RUINS),
            new Note("Dad", "Claire", Place.RUINS),
            new Note("The Men in Suits", "R.", Place.RUINS),
            new Note("Day 9", "Unsigned", Place.RUINS),
            new Note("To Whoever Finds This", "A neighbor", Place.RUINS),
            new Note("Containment: G. Gray", "Security", Place.ARCHIVE),
            new Note("Shift Notes", "Checkpoint B", Place.ARCHIVE),
            new Note("For Claire", "N. Bennet", Place.ARCHIVE),
            new Note("Assay: Helixite", "Pinehearst liaison", Place.BUNKER),
            new Note("A Note, Folded Small", "H. N.", Place.ARCHIVE),
            new Note(SIGNAL_TESTIMONY, SIGNAL_AUTHOR, Place.BUNKER),
            new Note("The Key", "H. N.", Place.PAST),
            new Note("The Door at 6:14", "N. Bennet", Place.PAST),
            new Note("Addendum: G. Gray", "Security", Place.PAST),
            new Note("Why You", "H. N.", Place.PAST),
            new Note("Pod 14", "Claire", Place.PAST)
    );
    /** Title to author, for every story note the mod places. */
    public static final Map<String, String> NOTES = ORDER.stream()
            .collect(Collectors.toUnmodifiableMap(Note::title, Note::author));
    private static final int SCAN_INTERVAL = 40;

    private StoryDiscoveries() {}

    /** The titles of the notes this player has found, in story order. */
    public static List<String> recovered(ServerPlayer player) {
        Set<String> found = player.getData(ModDataAttachments.POWER_JOURNEY).discoveries();
        return ORDER.stream().map(Note::title).filter(found::contains).toList();
    }

    public static boolean has(ServerPlayer player, String title) {
        return player.getData(ModDataAttachments.POWER_JOURNEY).discoveries().contains(title);
    }

    /** Older records enter the Journal total when found, so new worlds can still complete their collection. */
    public static int journalTotal(ServerPlayer player) {
        boolean bunkerEnabled = HeroesEvolvedConfig.COMMON.bunkerEnabled.get();
        return (int) ORDER.stream().filter(note -> note.place() == Place.RUINS
                || bunkerEnabled && note.place() == Place.BUNKER || has(player, note.title())).count();
    }

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

        int found = recovered(player).size();
        player.displayClientMessage(Component.literal("Story found: " + title + " (" + found + "/" + journalTotal(player) + ")")
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
