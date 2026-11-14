package com.jayptucker.heroesevolved.shapeshift;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.telepathy.TelepathyFocus;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Speaking as someone else. In a person's form, what a shapeshifter types in public chat goes out under that person's
 * name, with firm limits (docs/shapeshifting.md, "Speaking as someone else"):
 * <ul>
 *   <li>Public chat only: {@code /msg}, team chat and every other command never pass through {@link ServerChatEvent},
 *       so they always carry the real name.</li>
 *   <li>Never an operator: nobody can take an operator's form, and if the copied person has become one since, the line
 *       goes out under the real name.</li>
 *   <li>The copied person always knows: their copy of the line is marked "not you", and a notice tells them someone is
 *       speaking in their shape (and who, if they are a telepath).</li>
 *   <li>Some viewers see who really spoke: the copied person, operators, the speaker, and any telepath reading the
 *       speaker's mind get a mark after the name that shows "Spoken by …" on hover.</li>
 *   <li>Every line is logged with the real sender, and the server config {@code shapeshifting.chatImpersonation} turns
 *       the whole feature off (chat then uses the real name, and forms still work).</li>
 * </ul>
 * Each viewer gets their own copy as a system message styled like chat: signed chat can't carry another player's name.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class ShapeshiftChat {
    /** How often the copied person is told, at most, so a long conversation doesn't flood their action bar. */
    static final int NOTICE_GAP = 200;
    private static final Map<UUID, Long> LAST_NOTICE = new HashMap<>();

    private ShapeshiftChat() {}

    public static boolean enabled() { return HeroesEvolvedConfig.COMMON.shapeshiftChatImpersonation.get(); }

    /** The person {@code speaker}'s public chat goes out as, or null if it goes out under their own name. */
    public static @Nullable GameProfile speaksAs(ServerPlayer speaker) {
        GameProfile copied = ShapeshiftService.posingAs(speaker);
        if (copied == null || !enabled() || ShapeshiftService.operator(speaker.getServer(), copied)) return null;
        return copied;
    }

    /** Whether {@code viewer} is told who really sent a line {@code speaker} spoke as {@code copied}. */
    static boolean seesThrough(ServerPlayer viewer, ServerPlayer speaker, GameProfile copied) {
        return viewer == speaker || viewer.getUUID().equals(copied.getId()) || viewer.hasPermissions(2) || reads(viewer, speaker);
    }

    /** A telepath with a thread on the speaker's mind. */
    static boolean reads(ServerPlayer viewer, ServerPlayer speaker) {
        return telepath(viewer) && TelepathyFocus.focus(viewer, speaker) > 0;
    }

    static boolean telepath(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower()
                .filter(e -> e.getKey().equals(ModAbilities.TELEPATHY_ID) && e.getValue().isUnlocked()).isPresent();
    }

    /** One viewer's copy of the line: {@code <Name> message}, with the marks and hovers that viewer is owed. */
    static Component line(ServerPlayer viewer, ServerPlayer speaker, GameProfile copied, Component message) {
        String name = copied.getName();
        MutableComponent who = Component.literal(name).withStyle(Style.EMPTY
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY, new HoverEvent.EntityTooltipInfo(EntityType.PLAYER, copied.getId(), Component.literal(name))))
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tell " + name + " "))
                .withInsertion(name));
        if (seesThrough(viewer, speaker, copied)) {
            Component spoken = viewer == speaker
                    ? Component.translatable("shapeshift.heroesevolved.chat.you", name)
                    : Component.translatable("shapeshift.heroesevolved.chat.spoken_by", speaker.getGameProfile().getName());
            who.append(Component.literal("*").withStyle(Style.EMPTY.withColor(0xC29A5E)
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, spoken))));
        }
        MutableComponent line = Component.translatable("chat.type.text", who, message);
        if (viewer.getUUID().equals(copied.getId()))
            line.append(Component.literal(" ").append(Component.translatable("shapeshift.heroesevolved.chat.not_you")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        return line;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void chat(ServerChatEvent event) {
        ServerPlayer speaker = event.getPlayer();
        GameProfile copied = speaksAs(speaker);
        if (copied == null) return;
        event.setCanceled(true);
        var server = speaker.getServer();
        HeroesEvolved.LOGGER.info("[Shapeshifting] {} ({}) spoke as {}: {}", speaker.getGameProfile().getName(), speaker.getUUID(),
                copied.getName(), event.getRawText());
        server.sendSystemMessage(Component.literal("[" + speaker.getGameProfile().getName() + " as " + copied.getName() + "] ")
                .append(event.getMessage()));
        for (ServerPlayer viewer : server.getPlayerList().getPlayers())
            viewer.sendSystemMessage(line(viewer, speaker, copied, event.getMessage()));
        ServerPlayer person = server.getPlayerList().getPlayer(copied.getId());
        if (person != null) notice(person, speaker);
    }

    /** Tells the copied person someone is speaking in their shape; a telepath is told who. */
    static void notice(ServerPlayer person, ServerPlayer speaker) {
        long now = person.serverLevel().getGameTime();
        Long last = LAST_NOTICE.get(person.getUUID());
        if (last != null && now - last < NOTICE_GAP && now >= last) return;
        LAST_NOTICE.put(person.getUUID(), now);
        person.displayClientMessage(telepath(person)
                ? Component.translatable("shapeshift.heroesevolved.chat.notice_who", speaker.getGameProfile().getName())
                : Component.translatable("shapeshift.heroesevolved.chat.notice"), true);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { LAST_NOTICE.remove(event.getEntity().getUUID()); }
}
