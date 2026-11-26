package com.jayptucker.heroesevolved.gui.screen;

import com.jayptucker.heroesevolved.client.PlayerGuidance;
import com.jayptucker.heroesevolved.input.ModKeyMappings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Always available, including before awakening. Teaches inputs and signposts discoveries without solving the story. */
public final class GuideScreen extends HeroScreen {
    private static final String[] TOPICS = {"start", "controls", "growth", "combat", "world", "gear", "portal", "wither", "dragon", "trouble"};
    private int topic, scroll, maximumScroll;

    public GuideScreen(Screen parent) { super(Component.translatable("guide.heroesevolved.title"), parent); }

    public GuideScreen topic(String id) {
        for (int i = 0; i < TOPICS.length; i++) if (TOPICS[i].equals(id)) topic = i;
        scroll = 0;
        return this;
    }

    @Override protected Tab tab() { return Tab.GUIDE; }

    public static Component key(KeyMapping mapping) {
        return mapping.isUnbound() ? Component.translatable("guide.heroesevolved.unbound") : mapping.getTranslatedKeyMessage();
    }

    /** Resolve bindings when drawing, so rebinding never leaves stale instructions. */
    public static Component text(String translationKey) {
        Minecraft mc = Minecraft.getInstance();
        if (ModKeyMappings.ABILITY_WHEEL.isUnbound()
                && (translationKey.equals("guide.heroesevolved.controls.2") || translationKey.equals("guide.heroesevolved.hint.awakened")))
            translationKey += ".unbound";
        Component powers = ModKeyMappings.OPEN_POWERS.isUnbound() ? Component.translatable("guide.heroesevolved.inventory_shortcut") : key(ModKeyMappings.OPEN_POWERS);
        Component journal = ModKeyMappings.OPEN_JOURNAL.isUnbound() ? Component.translatable("guide.heroesevolved.journal_shortcut") : key(ModKeyMappings.OPEN_JOURNAL);
        return Component.translatable(translationKey, powers, key(ModKeyMappings.ABILITY_WHEEL),
                journal, key(mc.options.keyShift), key(ModKeyMappings.FINISHER),
                key(mc.options.keyAttack), key(mc.options.keyLeft), key(mc.options.keyRight), key(mc.options.keyDown),
                key(mc.options.keyJump), key(mc.options.keySprint), key(ModKeyMappings.MEMORY_WHEEL));
    }

    @Override protected void renderPage(GuiGraphics g, int mx, int my, float partial) {
        int x = contentLeft(), top = contentTop(), bottom = contentBottom() - 3;
        int side = Math.min(125, contentWidth() / 3);
        int row = Math.min(20, Math.max(14, (bottom - top - 44) / TOPICS.length));
        for (int i = 0; i < TOPICS.length; i++) {
            int chosen = i;
            button(g, x, top + i * row, side, row - 1, Component.translatable("guide.heroesevolved." + TOPICS[i] + ".title"),
                    true, topic == i, mx, my, () -> { topic = chosen; scroll = 0; });
        }
        button(g, x, bottom - 41, side, 19, Component.translatable("guide.heroesevolved.controls_button"), true, false, mx, my,
                () -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options)));
        button(g, x, bottom - 20, side, 19, Component.translatable(PlayerGuidance.enabled()
                        ? "guide.heroesevolved.tips_on" : "guide.heroesevolved.tips_off"),
                PlayerGuidance.known(), false, mx, my, PlayerGuidance::toggle);

        int px = x + side + 8, pw = contentRight() - px;
        panel(g, px, top, pw, bottom - top);
        Component heading = Component.translatable("guide.heroesevolved." + TOPICS[topic] + ".title");
        fitted(g, heading.getString(), px + 8, top + 7, pw - 16, GOLD, true);
        int areaTop = top + 23, areaBottom = bottom - 15;
        List<FormattedCharSequence> lines = lines(pw - 20);
        int visible = Math.max(1, (areaBottom - areaTop) / 11);
        maximumScroll = Math.max(0, lines.size() - visible);
        scroll = Mth.clamp(scroll, 0, maximumScroll);
        g.enableScissor(px, areaTop, px + pw, areaBottom);
        for (int i = scroll; i < Math.min(lines.size(), scroll + visible); i++)
            g.drawString(font, lines.get(i), px + 8, areaTop + (i - scroll) * 11, TEXT, false);
        g.disableScissor();
        if (maximumScroll > 0) {
            int track = areaBottom - areaTop, thumb = Math.max(8, track * visible / lines.size());
            int y = areaTop + (track - thumb) * scroll / maximumScroll;
            g.fill(px + pw - 4, areaTop, px + pw - 2, areaBottom, 0x33FFFFFF);
            g.fill(px + pw - 4, y, px + pw - 2, y + thumb, GOLD);
            button(g, px + pw - 39, bottom - 14, 17, 12, Component.literal("↑"), scroll > 0, false, mx, my,
                    () -> scroll = Math.max(0, scroll - visible));
            button(g, px + pw - 20, bottom - 14, 17, 12, Component.literal("↓"), scroll < maximumScroll, false, mx, my,
                    () -> scroll = Math.min(maximumScroll, scroll + visible));
        }
        g.drawString(font, Component.translatable("guide.heroesevolved.read_more", scroll + 1, Math.min(lines.size(), scroll + visible), lines.size()),
                px + 8, bottom - 11, MUTED, false);
    }

    private List<FormattedCharSequence> lines(int room) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        String id = TOPICS[topic];
        for (int i = 1; i <= 6; i++) {
            String key = "guide.heroesevolved." + id + "." + i;
            if (i > 4 && !net.minecraft.client.resources.language.I18n.exists(key)) break;
            lines.addAll(font.split(text(key), room));
            lines.add(FormattedCharSequence.EMPTY);
        }
        boolean wither = id.equals("wither"), seal = id.equals("dragon");
        if ((wither || seal) && power() != null && power().unlocked()) {
            // What this player's own power answers in each phase (combat/BossLocks, the table the fight reads).
            String boss = wither ? "wither" : "seal";
            lines.addAll(font.split(Component.translatable("guide.heroesevolved." + id + ".yours", Component.translatable(
                    "ability." + power().abilityId().getNamespace() + "." + power().abilityId().getPath())), room));
            for (int phase = 1; phase <= 3; phase++) {
                lines.addAll(font.split(Component.translatable(wither ? "wither.heroesevolved.phase_name." + phase : "endgame.heroesevolved.dragon.phase_name." + phase), room));
                for (var lock : wither ? com.jayptucker.heroesevolved.wither.WitherGeometry.locks(phase) : com.jayptucker.heroesevolved.endgame.SealGeometry.locks(phase)) {
                    var key = com.jayptucker.heroesevolved.combat.BossLocks.key(power().abilityId(), lock);
                    if (key == null) continue;
                    String name = lock.name().toLowerCase(java.util.Locale.ROOT), role = key.role().name().toLowerCase(java.util.Locale.ROOT);
                    String how = lock == com.jayptucker.heroesevolved.combat.BossLocks.Lock.CURSE ? "cleanse"
                            : key.role() == com.jayptucker.heroesevolved.combat.BossLocks.Role.BRING_DOWN ? "bring_down." + key.strength().name().toLowerCase(java.util.Locale.ROOT)
                            : key.channel().name().toLowerCase(java.util.Locale.ROOT) + "." + key.strength().name().toLowerCase(java.util.Locale.ROOT);
                    lines.addAll(font.split(Component.translatable("guide.heroesevolved." + id + ".key",
                            Component.translatable("lock.heroesevolved." + boss + "." + name), Component.translatable("lock.heroesevolved.role." + role),
                            Component.translatable("lock.heroesevolved.channel." + how)), room));
                }
            }
            lines.add(FormattedCharSequence.EMPTY);
        }
        if (id.equals("controls")) {
            for (int i = 0; i < ModKeyMappings.ABILITY_KEYS.length; i++)
                lines.addAll(font.split(Component.translatable("guide.heroesevolved.ability_key", i + 1, key(ModKeyMappings.ABILITY_KEYS[i])), room));
        }
        var loadout = com.jayptucker.heroesevolved.network.ClientLoadout.get();
        if (id.equals("growth") && power() != null && power().unlocked() && !loadout.trial().isEmpty()) {
            lines.addAll(font.split(Component.translatable("guide.heroesevolved.your_trial", loadout.trial(),
                    loadout.trialProgress(), loadout.trialGoal()), room));
        }
        return lines;
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scroll = Mth.clamp(scroll - (int) Math.signum(vertical) * 3, 0, maximumScroll);
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_UP -> scroll = Math.max(0, scroll - 1);
            case GLFW.GLFW_KEY_DOWN -> scroll = Math.min(maximumScroll, scroll + 1);
            case GLFW.GLFW_KEY_PAGE_UP -> scroll = Math.max(0, scroll - 10);
            case GLFW.GLFW_KEY_PAGE_DOWN -> scroll = Math.min(maximumScroll, scroll + 10);
            case GLFW.GLFW_KEY_HOME -> scroll = 0;
            case GLFW.GLFW_KEY_END -> scroll = maximumScroll;
            case GLFW.GLFW_KEY_LEFT -> { topic = Math.floorMod(topic - 1, TOPICS.length); scroll = 0; }
            case GLFW.GLFW_KEY_RIGHT -> { topic = (topic + 1) % TOPICS.length; scroll = 0; }
            default -> { return super.keyPressed(key, scan, modifiers); }
        }
        return true;
    }
}
