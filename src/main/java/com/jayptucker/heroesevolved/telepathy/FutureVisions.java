package com.jayptucker.heroesevolved.telepathy;

import com.jayptucker.heroesevolved.data.ModDataAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A storyline heard in other minds. Villagers have been dreaming of a city
 * that has not been built yet, and of the blast that destroys it: the
 * future a Time Manipulator glimpses on awakening.
 *
 * <p>Each adult villager holds one memory, revealed in order across four
 * acts, so a telepath pieces the story together by reading many minds.
 * Some other creatures know a piece of it too, in their own way.</p>
 */
public final class FutureVisions {
    /** The story, in the order a telepath uncovers it. */
    private static final List<String> FRAGMENTS = List.of(
            // Act I: rumours.
            "Bad dreams again. A city of white stone... and then light. So much light.",
            "Grandmother said when the sun goes black, the special ones wake up. "
                    + "The sun went black. People can fly now.",
            "The cartographer has painted the same picture for days. A city on fire. "
                    + "He doesn't remember painting any of it.",
            // Act II: the glowing man.
            "In the painting, a man stands in the middle of the avenue. He's glowing. "
                    + "Everyone else is running.",
            "The cleric says the glowing man doesn't want it to happen. "
                    + "He's trying to hold it in. He can't.",
            "He was ordinary once. Then the Eclipse. Then he started taking "
                    + "what the others had, one power at a time.",
            // Act III: the place.
            "It isn't a place we know. It hasn't been built yet. Someone is going to build it.",
            "Twelve chunks wide. One long avenue down the middle. "
                    + "I count the streets in my sleep.",
            "I saw the evacuation. I saw myself in it. I was carrying "
                    + "my daughter, and I wasn't fast enough.",
            // Act IV: the way out.
            "There's a moment, right before the shockwave, when everything goes silent. "
                    + "Somebody stops time. Somebody pulls everyone back.",
            "Find the one who can bend time. Only they reach the city before the light does.",
            "If you can hear this... it isn't fixed. The future can still change. "
                    + "Don't let him reach the city.",
            // Act V: the watchmaker. Existing progress continues into this chapter.
            "There is another man in the dreams. A dark coat at the edge of the avenue. "
                    + "He watches the glowing one as if he has found something he lost.",
            "The watchmaker asked who could do things the others couldn't. "
                    + "I heard a little ticking sound. Then he knew which way to go.",
            "Gabriel Gray. Someone called him Gabriel, but he answered to Sylar. "
                    + "He doesn't want to meet the special ones. He wants to understand what is inside them.",
            "He can learn what makes you different. If he reaches the man with the heat, "
                    + "there could be another glowing figure on that avenue. Keep them apart."
    );

    // Each villager gives up its memory once; per-session is enough.
    private static final Map<UUID, Set<UUID>> MINDS_READ = new HashMap<>();

    private FutureVisions() {
    }

    /** The memories this player has heard so far, in story order (for the journal). */
    public static List<String> heard(ServerPlayer player) {
        int progress = Math.min(FRAGMENTS.size(),
                player.getData(ModDataAttachments.PLAYER_STORY_PROGRESS.get()));
        return List.copyOf(FRAGMENTS.subList(0, Math.max(0, progress)));
    }

    public static int fragmentCount() {
        return FRAGMENTS.size();
    }

    public static void radiationAwakening(ServerPlayer source) {
        source.sendSystemMessage(Component.literal("The visions spoke of one glowing man. Now the heat is inside you.")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        for (ServerPlayer witness : source.serverLevel().players()) {
            if (witness == source || witness.distanceToSqr(source) > 300 * 300) continue;
            if (witness.getData(ModDataAttachments.PLAYER_STORY_PROGRESS) >= 4 || hasSeenTheCity(witness))
                witness.sendSystemMessage(Component.literal("That orange glow... the figure from the visions is here.")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        }
    }

    /**
     * Gives the next part of the story if this mind holds one.
     *
     * @return true if a vision was revealed instead of ordinary thoughts
     */
    public static boolean tryReveal(ServerPlayer reader, LivingEntity mind) {
        String special = otherwordlyThought(mind, reader);
        if (special != null) {
            reveal(reader, mind, special, null);
            return true;
        }

        if (!(mind instanceof Villager villager) || villager.isBaby()) {
            return false;
        }

        int progress = reader.getData(ModDataAttachments.PLAYER_STORY_PROGRESS.get());
        Set<UUID> read = MINDS_READ.computeIfAbsent(reader.getUUID(), ignored -> new HashSet<>());
        if (progress >= FRAGMENTS.size()) {
            // The story is complete; the dreams have not stopped.
            if (reader.getRandom().nextFloat() < 0.35F) {
                reveal(reader, mind, "Have you stopped him yet? The dreams are getting louder.", null);
                return true;
            }
            return false;
        }
        if (!read.add(villager.getUUID())) {
            return false;
        }

        reader.setData(ModDataAttachments.PLAYER_STORY_PROGRESS.get(), progress + 1);
        reveal(reader, mind, FRAGMENTS.get(progress), progress + 1);

        if (progress + 1 == FRAGMENTS.size()) {
            reader.sendSystemMessage(Component.literal(
                    "The voices fall quiet. You know where the future breaks... "
                            + "and who must be there to stop it.")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            reader.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundSource.PLAYERS, 0.6F, 0.8F);
        }
        return true;
    }

    /** A memory that isn't the creature's own, shown apart from its thoughts. */
    private static void reveal(
            ServerPlayer reader,
            LivingEntity mind,
            String vision,
            Integer fragmentNumber
    ) {
        Component header = Component.literal(fragmentNumber == null
                        ? "[" + mind.getName().getString() + " - a vision] "
                        : "[" + mind.getName().getString() + " - memory "
                                + fragmentNumber + "/" + FRAGMENTS.size() + "] ")
                .withStyle(ChatFormatting.GOLD);
        reader.sendSystemMessage(header.copy().append(Component.literal("\"" + vision + "\"")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC)));

        // Someone who has stood in that city recognises it.
        if (fragmentNumber != null && fragmentNumber >= 7 && fragmentNumber <= 10
                && hasSeenTheCity(reader)) {
            reader.sendSystemMessage(Component.literal(
                    "  You know this place. You've stood on that avenue.")
                    .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
        }
        reader.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 0.7F, 0.7F);
    }

    /**
     * Some creatures perceive time strangely; reading them sometimes yields
     * a glimpse of the same future instead of their own thoughts.
     */
    private static String otherwordlyThought(LivingEntity mind, ServerPlayer reader) {
        if (mind instanceof com.jayptucker.heroesevolved.story.sylar.SylarEntity sylar) {
            return sylar.hunting() ? "Every heartbeat becomes a measurement. He is learning your pattern. "
                    + "Break his concentration before the ticking stops."
                    : "A watchmaker's patience. A hunger with no end. He has been following the trail your power leaves behind.";
        }
        // Reading the Glowing Man himself.
        if (mind instanceof ServerPlayer other && hasRadiation(other)) {
            return "...hold it in... hold it in... I can't. This is him. "
                    + "The glowing man. He doesn't know yet.";
        }
        // Villagers can feel him when he's near.
        if (mind instanceof Villager villager && !villager.isBaby()
                && reader.getRandom().nextFloat() < 0.5F
                && glowingManNear(mind)) {
            return "He's here. The glowing man is HERE. I can feel the heat on my skin.";
        }

        if (reader.getRandom().nextFloat() > 0.6F) {
            return null;
        }
        if (mind instanceof ZombieVillager) {
            return "...I was there... the avenue... the light... I didn't make it out...";
        }
        if (mind instanceof Witch) {
            return "The cauldron shows a city of ash. Twelve streets. "
                    + "One glowing heart at the centre.";
        }
        if (mind instanceof EnderMan) {
            return "Time is a corridor. At the end of it, a city burns. "
                    + "We have walked there. We have watched.";
        }
        if (mind instanceof AbstractIllager) {
            return "The chieftain wants the glowing man for himself. "
                    + "A weapon that levels cities.";
        }
        if (mind instanceof WanderingTrader) {
            return "I've sold maps to that city... to people who haven't been born yet.";
        }
        if (mind instanceof Allay) {
            return "I hum the song the children hummed, just before the light.";
        }
        if (mind instanceof Villager villager && villager.isBaby()) {
            return "Mama says the sun went dark and now people can fly. I want to fly!";
        }
        return null;
    }

    private static boolean hasRadiation(ServerPlayer player) {
        return com.jayptucker.heroesevolved.events.RadiationEvents.hasAwakenedRadiation(player);
    }

    private static boolean glowingManNear(LivingEntity mind) {
        return mind.level().players().stream().anyMatch(player ->
                player instanceof ServerPlayer serverPlayer && hasRadiation(serverPlayer)
                        && player.distanceToSqr(mind) <= 32.0D * 32.0D);
    }

    private static boolean hasSeenTheCity(ServerPlayer player) {
        return player.getData(ModDataAttachments.PLAYER_SEEN_FUTURE_CITY.get());
    }

    public static void forget(ServerPlayer player) {
        MINDS_READ.remove(player.getUUID());
    }
}
