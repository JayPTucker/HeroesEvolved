package com.jayptucker.heroesevolved.gui.screen;

import com.jayptucker.heroesevolved.network.AbilitySnapshot;
import com.jayptucker.heroesevolved.network.ClientLoadout;
import com.jayptucker.heroesevolved.network.ClientPowerState;
import com.jayptucker.heroesevolved.network.ClientSkillTreeState;
import com.jayptucker.heroesevolved.network.LoadoutPayload;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncPayload;
import com.jayptucker.heroesevolved.progression.PowerLevels;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The frame every page of the hero menu shares: your power and level along the top, the tabs (Loadout,
 * Skill Tree, Class, Journal, Settings) under it, your Mastery and stamina along the bottom, and a few small
 * drawing helpers so every page looks like the same book.
 */
public abstract class HeroScreen extends Screen {
    public static final int GOLD = 0xFFF2D27A, TEXT = 0xFFEDE3D6, MUTED = 0xFFB8A99A, DIM = 0xFF77695E, RED = 0xFFE07A6A,
            GREEN = 0xFF9BD67A, ENERGY = 0xFF57B6E0, LINE = 0xFF8A7A6C, PANEL = 0x66000000, PANEL_EDGE = 0x33F2D27A;
    public static final int OFFENSE = 0xFFE3685A, DEFENSE = 0xFF5FA8E0, SUPPORT = 0xFF7ACB6A, UTILITY = 0xFFB8A99A;

    public enum Tab {
        LOADOUT("LOADOUT"), TREE("SKILL TREE"), CLASS("CLASS"), JOURNAL("JOURNAL"), SETTINGS("SETTINGS");
        final String label;
        Tab(String label) { this.label = label; }
    }

    /** A clickable area: a button, a chip, a card. */
    protected record Hit(int x, int y, int w, int h, Runnable click, Runnable rightClick) {
        boolean over(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    protected final Screen parent;
    protected final List<Hit> hits = new ArrayList<>();
    protected int frame;

    protected HeroScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    protected abstract Tab tab();

    protected abstract void renderPage(GuiGraphics g, int mouseX, int mouseY, float partial);

    /** Switches to another tab. Pages of the abilities screen switch in place; the others open their own screen. */
    protected void open(Tab tab) {
        Screen root = parent;
        switch (tab) {
            case JOURNAL -> JournalScreen.open(root);
            case SETTINGS -> minecraft.setScreen(new EffectsSettingsScreen(this));
            default -> {
                if (this instanceof PowersScreen powers) powers.page(tab);
                else minecraft.setScreen(new PowersScreen(root).page(tab));
            }
        }
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) { }
    @Override public void tick() { frame++; }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    protected int contentLeft() { return Math.max(6, (width - contentWidth()) / 2); }
    protected int contentWidth() { return Math.min(width - 12, 560); }
    protected int contentRight() { return contentLeft() + contentWidth(); }
    protected int contentTop() { return 48; }
    protected int contentBottom() { return height - 24; }

    private int tabX(int index) {
        int total = 0;
        for (Tab t : Tab.values()) total += font.width(t.label) + 16;
        int x = (width - total) / 2;
        for (int i = 0; i < index; i++) x += font.width(Tab.values()[i].label) + 16;
        return x + 8;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        hits.clear();
        g.fillGradient(0, 0, width, height, 0xEE140A07, 0xF4281008);
        g.fillGradient(0, height - height / 3, width, height, 0x00000000, 0x70000000);
        renderHeader(g, mouseX, mouseY);
        renderPage(g, mouseX, mouseY, partial);
        renderFooter(g);
        super.render(g, mouseX, mouseY, partial);
        renderTooltips(g, mouseX, mouseY);
    }

    /** Hover text drawn last, over everything. */
    protected void renderTooltips(GuiGraphics g, int mouseX, int mouseY) { }

    private void renderHeader(GuiGraphics g, int mouseX, int mouseY) {
        AbilitySnapshot power = power();
        int x = contentLeft();
        if (power != null) {
            String path = power.abilityId().getPath();
            g.renderFakeItem(PowerIcons.emblem(path), x, 5);
            g.drawString(font, Component.translatable("ability." + power.abilityId().getNamespace() + "." + path).withStyle(ChatFormatting.BOLD), x + 20, 5, GOLD, true);
            LoadoutPayload loadout = ClientLoadout.get();
            int level = power.unlocked() ? loadout.level() : 1;
            String stage = power.unlocked() ? "Level " + level + " · " + PowerLevels.stage(level).title : "Dormant: it has not woken yet";
            g.drawString(font, stage, x + 20, 16, MUTED, false);
            int points = points();
            if (power.unlocked()) {
                String label = points + (points == 1 ? " point" : " points");
                int w = font.width(label) + 14, px = contentRight() - w - 18;
                g.fill(px, 6, px + w, 20, points > 0 ? 0xAA6A4A14 : 0x66302620);
                g.renderOutline(px, 6, w, 14, points > 0 ? GOLD : LINE);
                g.drawString(font, "◆", px + 3, 9, GOLD, false);
                g.drawString(font, label, px + 11, 9, points > 0 ? GOLD : MUTED, false);
            }
        } else {
            g.drawString(font, Component.literal("Heroes Evolved").withStyle(ChatFormatting.BOLD), x, 8, GOLD, true);
        }
        // Close button.
        int cx = contentRight() - 12;
        boolean overClose = mouseX >= cx && mouseX < cx + 12 && mouseY >= 6 && mouseY < 20;
        g.drawString(font, "✕", cx + 2, 9, overClose ? TEXT : MUTED, false);
        hits.add(new Hit(cx, 6, 12, 14, this::onClose, null));
        // Tabs.
        g.fill(contentLeft(), 27, contentRight(), 28, 0x33FFFFFF);
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            int tx = tabX(i), tw = font.width(tabs[i].label);
            boolean active = tabs[i] == tab(), over = mouseY >= 30 && mouseY < 44 && mouseX >= tx - 6 && mouseX < tx + tw + 6;
            if (active) g.fill(tx - 6, 29, tx + tw + 6, 44, 0x55F2D27A);
            g.drawString(font, tabs[i].label, tx, 33, active ? GOLD : over ? TEXT : MUTED, false);
            if (active) g.fill(tx - 6, 43, tx + tw + 6, 44, GOLD);
            Tab target = tabs[i];
            hits.add(new Hit(tx - 6, 29, tw + 12, 15, () -> { if (target != tab()) open(target); }, null));
        }
    }

    private void renderFooter(GuiGraphics g) {
        AbilitySnapshot power = power();
        PlayerPowerSyncPayload sync = ClientPowerState.getSnapshot();
        int x = contentLeft(), y = height - 18, w = contentWidth();
        int half = (w - 12) / 2;
        if (power != null && power.unlocked()) {
            LoadoutPayload loadout = ClientLoadout.get();
            float progress = loadout.evolved() || loadout.masteryNeeded() <= 0 ? 1 : Mth.clamp(loadout.masteryInto() / (float) loadout.masteryNeeded(), 0, 1);
            g.fill(x, y, x + half, y + 3, 0xFF2A2A2A);
            g.fill(x, y, x + (int) (half * progress), y + 3, loadout.evolved() ? 0xFFC77DFF : 0xFF5FA8C8);
            String mastery = loadout.evolved() ? "Evolved"
                    : loadout.level() >= PowerLevels.EVOLVED - 1 && progress >= 1 ? "Evolve: " + loadout.trial() + " " + loadout.trialProgress() + "/" + loadout.trialGoal()
                    : "Mastery " + loadout.masteryInto() + " / " + loadout.masteryNeeded() + " to Level " + (loadout.level() + 1);
            g.drawString(font, font.plainSubstrByWidth(mastery, half), x, y + 6, MUTED, false);
        }
        float fraction = sync.maximumEnergy() <= 0 ? 0 : Mth.clamp(sync.energy() / (float) sync.maximumEnergy(), 0, 1);
        int sx = x + w - half;
        g.fill(sx, y, sx + half, y + 3, 0xFF1C2A33);
        g.fill(sx, y, sx + (int) (half * fraction), y + 3, ENERGY);
        String stamina = "Stamina " + sync.energy() + " / " + sync.maximumEnergy() + "  (+" + sync.energyRegenerationPerSecond() + "/s)";
        g.drawString(font, font.plainSubstrByWidth(stamina, half), sx + half - Math.min(half, font.width(stamina)), y + 6, ENERGY, false);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    protected static AbilitySnapshot power() {
        var sync = ClientPowerState.getSnapshot();
        return sync.abilities().stream().findFirst().orElse(null);
    }

    /** Unspent skill points, or 0 if the tree hasn't arrived yet. */
    protected static int points() {
        AbilitySnapshot power = power();
        if (power == null || !power.unlocked()) return 0;
        var tree = ClientSkillTreeState.get();
        if (!tree.power().equals(power.abilityId().getPath())) return 0;
        return SkillCatalog.available(tree.power(), tree.build(), ClientLoadout.get().storyPoints());
    }

    protected void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.fill(x, y, x + w, y + 1, PANEL_EDGE);
    }

    /** A labelled button; returns its width. Disabled buttons are drawn dim and do nothing. */
    protected int button(GuiGraphics g, int x, int y, int w, int h, Component label, boolean enabled, boolean highlighted, int mouseX, int mouseY, Runnable click) {
        boolean over = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int fill = !enabled ? (highlighted ? 0xAA5A3A14 : 0x88201A16) : over ? 0xCC7A4A1A : highlighted ? 0xAA6A3A14 : 0x88302620;
        int edge = !enabled ? (highlighted ? GOLD : 0xFF4A3F37) : over ? 0xFFFFFFFF : highlighted ? GOLD : LINE;
        g.fill(x, y, x + w, y + h, fill);
        g.renderOutline(x, y, w, h, edge);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, enabled || highlighted ? TEXT : DIM);
        if (enabled) hits.add(new Hit(x, y, w, h, () -> {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1F));
            click.run();
        }, null));
        return w;
    }

    /** A small coloured tag: OFFENSE, DEFENSE, SUPPORT. */
    protected int pill(GuiGraphics g, int x, int y, String text, int color) {
        int w = font.width(text) + 6;
        g.fill(x, y, x + w, y + 10, (color & 0x00FFFFFF) | 0x55000000);
        g.renderOutline(x, y, w, 10, color);
        g.drawString(font, text, x + 3, y + 1, color, false);
        return w;
    }

    /** Wrapped text from {@code y}, at most {@code maxLines} lines (the last one ending in an ellipsis if cut); returns the y after it. */
    protected int paragraph(GuiGraphics g, Component text, int x, int y, int width, int maxLines, int color) {
        List<FormattedCharSequence> lines = font.split(text, width);
        for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
            if (i == maxLines - 1 && lines.size() > maxLines)
                g.drawString(font, font.plainSubstrByWidth(lineText(lines.get(i)), width - font.width("…")) + "…", x, y, color, false);
            else g.drawString(font, lines.get(i), x, y, color, false);
            y += 10;
        }
        return y;
    }

    private static String lineText(FormattedCharSequence line) {
        StringBuilder out = new StringBuilder();
        line.accept((index, style, code) -> { out.appendCodePoint(code); return true; });
        return out.toString();
    }

    public static int roleColor(String role) {
        return switch (role) {
            case "offense" -> OFFENSE;
            case "defense" -> DEFENSE;
            case "support" -> SUPPORT;
            default -> UTILITY;
        };
    }

    public static String roleLabel(String role) {
        return switch (role) {
            case "offense" -> "OFFENSE";
            case "defense" -> "DEFENSE";
            case "support" -> "SUPPORT";
            default -> "UTILITY";
        };
    }

    /**
     * What an ability is for, from what it does: class abilities say so outright; for the rest the description
     * is read for who it helps and how, so the Loadout page can show how a build leans.
     */
    public static String roleOf(String role, String description) {
        if (!role.isEmpty()) return role;
        String d = " " + description.toLowerCase().replaceAll("[^a-z ]", " ") + " ";
        int support = count(d, " friend", " ally", " allies", " teammate", " companion", " heal them", " heals them", " others ");
        int defense = count(d, " shield", " reflect", " resistance", " resist ", " armor", " armour", " protect", " absorb", " dodge",
                " deflect", " invulnerab", " immune", " less damage", " intercept", " interception", " shelter", " block incoming");
        int offense = count(d, " damage", " strike", " burn", " blast", " explo", " attack", " enem", " foe", " shock", " stun", " pierc",
                " ignite", " bolt", " lance", " detonat", " slam", " knock", " scorch", " sear");
        int utility = count(d, " fly", " flight", " glide", " teleport", " dash", " sprint", " see ", " reveal", " sense", " carry",
                " lift ", " climb", " walk through", " find ", " invisible", " unseen", " speed ");
        if (support > 0 && support >= offense) return "support";
        if (defense > 0 && defense >= offense) return "defense";
        if (utility > offense) return "utility";
        return offense > 0 ? "offense" : utility > 0 ? "utility" : defense > 0 ? "defense" : "utility";
    }

    private static int count(String text, String... words) {
        int n = 0;
        for (String word : words) if (text.contains(word)) n++;
        return n;
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Hit hit : List.copyOf(hits)) {
            if (!hit.over(mouseX, mouseY)) continue;
            if (button == 1 && hit.rightClick != null) { hit.rightClick.run(); return true; }
            if (button == 0 && hit.click != null) { hit.click.run(); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
