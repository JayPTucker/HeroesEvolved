package com.jayptucker.heroesevolved.progression;

import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Map;

/**
 * The Evolved trial: the last step from Level 19 to 20. Each power has its own feat, counted from the
 * moment the power awakens; once the player has the Mastery for Level 20 and the feat is done, the power
 * Evolves. Most powers ask for enemies defeated with the power; a few ask for what only they can do.
 */
public final class EvolutionTrials {
    public record Trial(String description, int goal) {}

    /** The generic feat: enemies defeated within three seconds of using your power. */
    public static final Trial DEFEATS = new Trial("Defeat 60 enemies with your power", 60);
    private static final Map<String, Trial> TRIALS = Map.of(
            "regeneration", new Trial("Get back up 5 times after being put down", 5),
            "radiation", new Trial("Hold it in: cancel your own Nuclear Detonation 3 times at Critical Core", 3),
            "time_manipulation", new Trial("Undo your own death with Rewrite the Moment", 1),
            "electrokinesis", new Trial("Unleash 25 Overcharged powers", 25)
    );

    private EvolutionTrials() {}

    public static Trial trial(String power) {
        return TRIALS.getOrDefault(power, DEFEATS);
    }

    /** True if this power's trial counts something other than defeats. */
    public static boolean special(String power) { return TRIALS.containsKey(power); }

    public static int progress(ServerPlayer player, String power) {
        return player.getData(ModDataAttachments.POWER_JOURNEY).trial(power);
    }

    /** One step on the player's own power's trial, if {@code power} is theirs. */
    public static void advance(ServerPlayer player, ResourceLocation power) {
        var assigned = PlayerAbilityService.getData(player).assignedPower();
        if (assigned.isEmpty() || !assigned.get().getKey().equals(power) || !assigned.get().getValue().isUnlocked()) return;
        String path = power.getPath();
        var journey = player.getData(ModDataAttachments.POWER_JOURNEY);
        if (journey.isEvolved(path)) return;
        Trial trial = trial(path);
        int before = journey.trial(path);
        if (before >= trial.goal()) return;
        player.setData(ModDataAttachments.POWER_JOURNEY, journey.advance(path, 1));
        if (before + 1 >= trial.goal())
            player.displayClientMessage(Component.literal("Evolved trial complete: " + trial.description() + ".")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        check(player);
    }

    /** A defeat counts toward the generic trial. */
    public static void defeated(ServerPlayer player) {
        PlayerAbilityService.getData(player).assignedPower()
                .filter(e -> !special(e.getKey().getPath()))
                .ifPresent(e -> advance(player, e.getKey()));
    }

    /** Evolves the power once both the Mastery for Level 20 and the trial are in hand. */
    public static void check(ServerPlayer player) {
        var assigned = PlayerAbilityService.getData(player).assignedPower();
        if (assigned.isEmpty() || !assigned.get().getValue().isUnlocked()) return;
        String path = assigned.get().getKey().getPath();
        var journey = player.getData(ModDataAttachments.POWER_JOURNEY);
        if (journey.isEvolved(path) || journey.trial(path) < trial(path).goal()
                || PlayerProgressionService.getData(player).mastery() < PowerLevels.masteryFor(PowerLevels.EVOLVED)) return;
        player.setData(ModDataAttachments.POWER_JOURNEY, journey.evolve(path));
        com.jayptucker.heroesevolved.skills.SkillTreeService.build(player, assigned.get().getKey());
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 30));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("EVOLVED").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Level 20: your power has become something new")
                .withStyle(ChatFormatting.GOLD)));
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 0.8F);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.2F);
        com.jayptucker.heroesevolved.skills.SkillTreeService.sync(player);
    }
}
