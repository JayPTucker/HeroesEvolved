package com.jayptucker.heroesevolved.gui.screen;

import com.jayptucker.heroesevolved.network.AdminActionPayload;
import com.jayptucker.heroesevolved.network.AdminActionPayload.Action;
import com.jayptucker.heroesevolved.network.AdminStatePayload;
import com.jayptucker.heroesevolved.network.AdminStatePayload.EventEntry;
import com.jayptucker.heroesevolved.network.AdminStatePayload.PlayerEntry;
import com.jayptucker.heroesevolved.network.ClientAdminState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Operator console. Server-owned event state, independent target selection and scrollable pages. */
public final class AdminScreen extends Screen {
    private static final int BG = 0xFF11171E, PANEL = 0xFF19222C, RULE = 0xFF303E4C;
    private static final int TEXT = 0xFFE5EDF4, MUTED = 0xFF99ADBE, GOLD = 0xFFF2C66D;
    private static final int TEAL = 0xFF68D8C5, RED = 0xFFEF8A87;
    private enum Tab { PLAYER, EVENTS, STORY, DEBUG }
    private static Tab tab = Tab.PLAYER;
    private static UUID selectedPlayer;
    private static int powerIndex, playerScroll, eventScroll;
    private static String selectedEvent = "eclipse";
    private final int[] scroll = new int[4];
    private int left, top, panelWidth, panelHeight, sidebar, bodyTop, bodyBottom, maxScroll, refreshTicks;
    private String lastEvent = "";
    private String feedback = "Select a player. Event cards show where their controls act.";
    private final List<Label> labels = new ArrayList<>();
    private record Label(int x, int y, int width, String text, int colour) {}

    public AdminScreen() { super(Component.literal("Heroes Evolved | Admin")); }
    public static void showPlayer() { tab = Tab.PLAYER; }
    public static void showWorld() { tab = Tab.EVENTS; selectedEvent = "eclipse"; }
    public static void showIncidents() { showEvent("gifts"); }
    public static void showDebug() { tab = Tab.DEBUG; }
    public static void showStory() { tab = Tab.STORY; selectedEvent = "sylar"; }
    public static void showEvent(String id) { selectedEvent = id; tab = Tab.EVENTS; }
    public void onStateUpdated() {
        String focused = getFocused() instanceof Button b ? b.getMessage().getString() : "";
        if (!ClientAdminState.get().feedback().isEmpty()) feedback = ClientAdminState.get().feedback();
        rebuildWidgets();
        if (!focused.isEmpty()) for (var child : children())
            if (child instanceof Button b && b.active && b.getMessage().getString().equals(focused)) { setFocused(b); break; }
    }
    private int x() { return left + sidebar + 18; }
    private int w() { return panelWidth - sidebar - 30; }
    private int y(int offset) { return bodyTop + offset - scroll[tab.ordinal()]; }
    private PlayerEntry target() {
        return ClientAdminState.get().players().stream().filter(p -> p.id().equals(selectedPlayer)).findFirst().orElse(null);
    }

    @Override protected void init() {
        panelWidth = Math.min(740, width - 12); panelHeight = Math.min(420, height - 12);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        sidebar = panelWidth < 480 ? 92 : 118;
        bodyTop = top + 72; bodyBottom = top + panelHeight - 38;
        labels.clear(); maxScroll = 0;
        var state = ClientAdminState.get();
        if (state.players().stream().noneMatch(p -> p.id().equals(selectedPlayer)))
            selectedPlayer = state.players().isEmpty() ? null : state.players().getFirst().id();
        button("Refresh", left + panelWidth - 88, top + 9, 55, () -> send(Action.REFRESH), true, false, "Refresh; also updates every two seconds.");
        button("X", left + panelWidth - 29, top + 9, 20, this::onClose, true, false, "Close (Esc)");
        String[] names = {"Players", "Events", "Story", "Debug"}; int each = (w() - 9) / 4;
        for (Tab t : Tab.values()) button(names[t.ordinal()], x() + t.ordinal() * (each + 3), top + 40, each,
                () -> { tab = t; eventScroll = 0; rebuildWidgets(); }, true, tab == t, names[t.ordinal()] + " controls");
        int visible = Math.max(1, (bodyBottom - top - 70) / 28);
        playerScroll = Math.clamp(playerScroll, 0, Math.max(0, state.players().size() - visible));
        for (int i = 0; i < visible && playerScroll + i < state.players().size(); i++) {
            var p = state.players().get(playerScroll + i);
            button(p.name(), left + 8, top + 70 + i * 28, sidebar - 4, () -> {
                selectedPlayer = p.id(); send(Action.REFRESH); rebuildWidgets();
            }, true, p.id().equals(selectedPlayer), p.name() + " | Level " + p.level() + "\n" + powerName(p.powerId()) + "\n" + p.whereabouts());
        }
        switch (tab) {
            case PLAYER -> { if (target() != null) player(target(), state); }
            case EVENTS, STORY -> events(state);
            case DEBUG -> debug(state);
        }
        int old = scroll[tab.ordinal()]; scroll[tab.ordinal()] = Math.clamp(old, 0, maxScroll);
        if (old != scroll[tab.ordinal()]) rebuildWidgets();
    }

    private void player(PlayerEntry p, AdminStatePayload state) {
        label(0, p.name() + " / Level " + p.level() + " of " + state.maximumLevel(), GOLD);
        label(15, powerName(p.powerId()) + (p.powerId().isEmpty() ? "" : p.awakened() ? " (awake)" : " (dormant)"), TEXT);
        label(30, "Mastery " + NumberFormat.getIntegerInstance().format(p.mastery()) + " / " + NumberFormat.getIntegerInstance().format(p.masteryForNextLevel()), MUTED);
        label(44, "Stamina " + (p.unlimitedEnergy() ? "unlimited" : p.energy() + " / " + p.maximumEnergy()), TEAL);
        label(59, p.whereabouts(), MUTED); heading(81, "POWER ASSIGNMENT");
        if (!state.powers().isEmpty()) {
            powerIndex = Math.floorMod(powerIndex, state.powers().size()); String power = state.powers().get(powerIndex);
            body("<", x(), 97, 22, () -> { powerIndex--; rebuildWidgets(); }, true, "Previous power");
            body(powerName(power), x() + 25, 97, w() - 50, () -> { powerIndex++; rebuildWidgets(); }, true,
                    "Choose power " + (powerIndex + 1) + " / " + state.powers().size() + ". Click to cycle forward.");
            body(">", x() + w() - 22, 97, 22, () -> { powerIndex++; rebuildWidgets(); }, true, "Next power");
            row(122, spec("Give awake", Action.GIVE_POWER, power, 0, true, "Replace with the chosen awakened power. Unique ownership applies."),
                    spec("Dormant", Action.GIVE_DORMANT_POWER, power, 0, true, "Assign without an awakening scene."),
                    spec("Remove", Action.REMOVE_POWER, !p.powerId().isEmpty(), "Remove their power."));
        }
        heading(154, "PROGRESSION");
        int[] levels = {p.level() - 1, 1, 10, 15, 20, p.level() + 1}; String[] names = {"-1", "Lv 1", "10", "15", "20", "+1"};
        Spec[] buttons = new Spec[6];
        for (int i = 0; i < 6; i++) buttons[i] = spec(names[i], Action.SET_LEVEL, "", levels[i],
                levels[i] >= 1 && levels[i] <= state.maximumLevel() && levels[i] != p.level(), "Set Power Level to " + levels[i] + ".");
        row(170, buttons);
        row(195, spec("+100", Action.ADD_MASTERY, "", 100, true, "Grant 100 Mastery."), spec("+1,000", Action.ADD_MASTERY, "", 1000, true, "Grant 1,000 Mastery."), spec("+10,000", Action.ADD_MASTERY, "", 10000, true, "Grant 10,000 Mastery."));
        row(220, spec("Awaken", Action.AWAKEN, !p.powerId().isEmpty() && !p.awakened(), "Awaken with their first scene."), spec("Reset progress", Action.RESET_PROGRESS, true, "Reset this player's Power Level to 1 and Mastery to zero."));
        heading(252, "RECOVERY & TRAVEL");
        row(268, spec("Refill stamina", Action.REFILL_ENERGY, true, "Fill stamina."), spec("Full heal", Action.FULL_HEAL, true, "Restore health and food; clear effects and fire."));
        boolean other = minecraft != null && minecraft.player != null && !minecraft.player.getUUID().equals(p.id());
        row(293, spec("Go to player", Action.TELEPORT_TO, other, "Teleport yourself to this player and dimension."), spec("Bring here", Action.BRING_HERE, other, "Teleport them to your position and dimension."));
        extent(320);
    }

    private void events(AdminStatePayload state) {
        var entries = state.events().stream().filter(e -> e.story() == (tab == Tab.STORY)).toList();
        if (entries.isEmpty()) { label(0, "Waiting for event state...", MUTED); return; }
        EventEntry event = entries.stream().filter(e -> e.id().equals(selectedEvent)).findFirst().orElse(entries.getFirst());
        selectedEvent = event.id(); int listW = panelWidth < 480 ? 96 : 124;
        int visible = Math.max(1, (bodyBottom - bodyTop - 16) / 25);
        if (!lastEvent.equals(selectedEvent)) {
            int index = entries.indexOf(event);
            if (index < eventScroll || index >= eventScroll + visible) eventScroll = Math.max(0, index - visible + 1);
            lastEvent = selectedEvent;
        }
        eventScroll = Math.clamp(eventScroll, 0, Math.max(0, entries.size() - visible));
        for (int i = 0; i < visible && eventScroll + i < entries.size(); i++) {
            EventEntry e = entries.get(eventScroll + i);
            button((e.active() ? "* " : "") + e.title(), x(), bodyTop + i * 25, listW, () -> {
                selectedEvent = e.id(); scroll[tab.ordinal()] = 0; rebuildWidgets();
            }, true, e.id().equals(event.id()), e.title() + "\n" + e.status());
        }
        labels.add(new Label(x(), bodyBottom - 11, listW, entries.size() + " events | scroll", MUTED));
        int dx = x() + listW + 12, dw = w() - listW - 12, at = 0;
        at = wrap(dx, at, dw, event.title(), GOLD) + 5;
        at = wrap(dx, at, dw, event.active() ? "ACTIVE / QUEUED" : "IDLE", event.active() ? TEAL : MUTED) + 8;
        at = wrap(dx, at, dw, event.scope(), MUTED) + 10;
        at = wrap(dx, at, dw, event.status(), TEXT) + 12;
        for (int i = 0; i < event.starts().size(); i++) {
            int variant = i;
            body(event.starts().get(i), dx, at, dw, () -> send(Action.EVENT_START, event.id(), variant), event.canStart(), "Start " + event.starts().get(i) + ". " + event.scope()); at += 25;
        }
        body("Cancel event", dx, at + 5, dw, () -> send(Action.EVENT_CANCEL, event.id(), 0), true, "Cancel this event and its queued starts. " + event.scope()); at += 35;
        if (event.id().equals("sylar")) {
            body("Advance story", dx, at, dw, () -> send(Action.SYLAR_NEXT), target() != null, "Advance the prisoner's story one step."); at += 25;
            body("Give Repair Ticket", dx, at, dw, () -> send(Action.SYLAR_TICKET), target() != null, "Give this player a Collection rematch ticket."); at += 25;
        }
        if (event.id().equals("prologue")) {
            body("Jump all to pods", dx, at, dw, () -> send(Action.SKIP_TO_POD), state.world().has(AdminStatePayload.World.LAST_DAY), "Skip the shared Last Day to the cryopods."); at += 25;
        }
        extent(at + 5);
    }

    private void debug(AdminStatePayload state) {
        heading(0, "LIVE SERVER DIAGNOSTICS"); int at = 18;
        for (String line : state.diagnostics()) at = wrap(x(), at, w(), line, MUTED) + 4;
        at += 9; heading(at, "SELECTED PLAYER / TESTING"); at += 18;
        var p = target(); boolean player = p != null;
        row(at, spec("God: " + (player && p.godMode() ? "ON" : "OFF"), Action.TOGGLE_GOD_MODE, player, "Prevent all damage and death. Gold outline while enabled."), spec("Free stamina: " + (player && p.unlimitedEnergy() ? "ON" : "OFF"), Action.TOGGLE_UNLIMITED_ENERGY, player, "Toggle unlimited stamina.")); at += 25;
        row(at, spec("Reset cooldowns", Action.RESET_COOLDOWNS, player, "Clear all ability cooldowns."), spec("Cancel awakening", Action.CANCEL_INTRO, player, "Cancel this player's awakening. Cancel time visions under Story.")); at += 25;
        row(at, spec("Give tuned radio", Action.GIVE_RADIO, player, "Give a walkie-talkie on 101.10 FM.")); at += 32;
        heading(at, "COMPANY ATTENTION"); at += 17;
        row(at, spec("Clear", Action.SET_ATTENTION, "", 0, player, "Set attention to zero. Cancel operations under Events."), spec("Noticed", Action.SET_ATTENTION, "", 25, player, "Set attention to 25."), spec("Watched", Action.SET_ATTENTION, "", 50, player, "Set attention to 50."), spec("Hunted", Action.SET_ATTENTION, "", 100, player, "Set attention to 100.")); at += 34;
        heading(at, "EVENT CLEANUP / ENTIRE SERVER"); at += 17;
        at = wrap(x(), at, w(), "Stops world events, queued incidents and stories. Existing terrain damage and surviving mobs remain.", RED) + 7;
        row(at, spec("Cancel all events", Action.CANCEL_ALL_EVENTS, true, "Server-wide cleanup including queued village starts and Company operations.")); extent(at + 28);
    }

    private record Spec(String text, Action action, String argument, int value, boolean active, String tip) {}
    private Spec spec(String text, Action action, boolean active, String tip) { return spec(text, action, "", 0, active, tip); }
    private Spec spec(String text, Action action, String arg, int value, boolean active, String tip) { return new Spec(text, action, arg, value, active, tip); }
    private void row(int offset, Spec... specs) {
        int each = (w() - 4 * (specs.length - 1)) / specs.length;
        for (int i = 0; i < specs.length; i++) { Spec s = specs[i]; body(s.text(), x() + i * (each + 4), offset, each, () -> send(s.action(), s.argument(), s.value()), s.active(), s.tip()); }
    }
    private void heading(int offset, String text) { label(offset, text, TEAL); }
    private void label(int offset, String text, int colour) { labels.add(new Label(x(), y(offset), w(), text, colour)); }
    private int wrap(int x, int offset, int width, String text, int colour) {
        for (var line : font.split(Component.literal(text), width)) {
            StringBuilder plain = new StringBuilder();
            line.accept((index, style, cp) -> { plain.appendCodePoint(cp); return true; });
            labels.add(new Label(x, y(offset), width, plain.toString(), colour)); offset += 11;
        }
        return offset;
    }
    private void extent(int height) { maxScroll = Math.max(0, height - bodyBottom + bodyTop); }
    private void body(String text, int x, int offset, int width, Runnable pressed, boolean active, String tip) {
        int y = y(offset); if (y >= bodyTop && y + 21 <= bodyBottom) button(text, x, y, width, pressed, active, false, tip);
    }
    private void button(String text, int x, int y, int width, Runnable pressed, boolean active, boolean selected, String tip) {
        var button = new ConsoleButton(x, y, width, Component.literal(text), b -> pressed.run(), selected);
        button.active = active; button.setTooltip(Tooltip.create(Component.literal(tip))); addRenderableWidget(button);
    }
    @Override public void tick() { if (++refreshTicks >= 40) { refreshTicks = 0; send(Action.REFRESH); } }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        int change = switch (key) { case 266 -> -(bodyBottom - bodyTop); case 267 -> bodyBottom - bodyTop; default -> 0; };
        if (change != 0) { scroll[tab.ordinal()] = Math.clamp(scroll[tab.ordinal()] + change, 0, maxScroll); rebuildWidgets(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (dy == 0 || my < bodyTop || my >= bodyBottom) return super.mouseScrolled(mx, my, dx, dy);
        if (mx >= left && mx < x()) playerScroll -= (int) Math.signum(dy);
        else if ((tab == Tab.EVENTS || tab == Tab.STORY) && mx >= x() && mx < x() + (panelWidth < 480 ? 96 : 124)) eventScroll -= (int) Math.signum(dy);
        else if (mx >= x() && mx < x() + w()) scroll[tab.ordinal()] = Math.clamp(scroll[tab.ordinal()] - (int) (dy * 24), 0, maxScroll);
        else return super.mouseScrolled(mx, my, dx, dy);
        rebuildWidgets(); return true;
    }
    @Override public void render(GuiGraphics g, int mx, int my, float dt) {
        g.fill(0, 0, width, height, 0xA0000000);
        g.fill(left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, RULE);
        g.fill(left, top, left + panelWidth, top + panelHeight, BG);
        g.fill(left, top, left + panelWidth, top + 34, PANEL); g.fill(left, top, left + 3, top + 34, TEAL);
        g.drawString(font, "HEROES EVOLVED", left + 11, top + 8, TEXT, false);
        g.drawString(font, "ADMIN CONSOLE", left + 11, top + 21, TEAL, false);
        g.fill(left + 5, top + 38, left + sidebar + 8, bodyBottom, PANEL);
        g.drawString(font, "ONLINE / " + ClientAdminState.get().players().size(), left + 10, top + 45, MUTED, false);
        g.drawString(font, "Choose target", left + 10, top + 57, MUTED, false);
        g.enableScissor(x(), bodyTop, x() + w(), bodyBottom);
        for (Label l : labels) if (l.y + 9 >= bodyTop && l.y < bodyBottom) g.drawString(font, font.plainSubstrByWidth(l.text, l.width), l.x, l.y, l.colour, false);
        g.disableScissor();
        if (maxScroll > 0) {
            int track = bodyBottom - bodyTop, thumb = Math.max(16, track * track / (track + maxScroll));
            int sy = bodyTop + (track - thumb) * scroll[tab.ordinal()] / maxScroll;
            g.fill(left + panelWidth - 7, bodyTop, left + panelWidth - 5, bodyBottom, RULE);
            g.fill(left + panelWidth - 7, sy, left + panelWidth - 5, sy + thumb, TEAL);
        }
        g.fill(left + 8, bodyBottom + 6, left + panelWidth - 8, bodyBottom + 7, RULE);
        g.drawString(font, (target() == null ? "No target" : "Target: " + target().name()) + " | Live / 2s", left + 10, bodyBottom + 11, TEAL, false);
        g.drawString(font, font.plainSubstrByWidth(feedback, panelWidth - 22), left + 10, bodyBottom + 23, TEXT, false);
        super.render(g, mx, my, dt);
        if (my >= bodyBottom + 21 && my < bodyBottom + 34 && mx >= left && mx < left + panelWidth) g.renderTooltip(font, font.split(Component.literal(feedback), Math.min(350, panelWidth - 24)), mx, my);
        for (Label l : labels) if (l.y >= bodyTop && l.y + 9 < bodyBottom && font.width(l.text) > l.width && mx >= l.x && mx < l.x + l.width && my >= l.y && my < l.y + 10) g.renderTooltip(font, font.split(Component.literal(l.text), 260), mx, my);
    }
    private static String powerName(String power) {
        if (power.isEmpty()) return "No power assigned";
        var id = ResourceLocation.tryParse(power); return id == null ? power : Component.translatable("ability." + id.getNamespace() + "." + id.getPath()).getString();
    }
    private void send(Action action) { send(action, "", 0); }
    private void send(Action action, String argument, int value) {
        UUID target = selectedPlayer != null ? selectedPlayer : minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : new UUID(0, 0);
        PacketDistributor.sendToServer(new AdminActionPayload(action, target, argument, value));
    }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float dt) {}
    @Override public boolean isPauseScreen() { return false; }
    private static final class ConsoleButton extends Button {
        private final boolean selected;
        ConsoleButton(int x, int y, int width, Component label, OnPress pressed, boolean selected) { super(x, y, width, 21, label, pressed, DEFAULT_NARRATION); this.selected = selected; }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float dt) {
            g.fill(getX(), getY(), getX() + width, getY() + height, selected ? 0xFF26483F : isHoveredOrFocused() && active ? 0xFF344958 : PANEL);
            g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, selected ? TEAL : RULE);
            if (selected) g.fill(getX(), getY(), getX() + 2, getY() + height, TEAL);
            var font = net.minecraft.client.Minecraft.getInstance().font; String text = getMessage().getString();
            if (font.width(text) > width - 8) text = font.plainSubstrByWidth(text, Math.max(1, width - 15)) + "…";
            boolean cleanup = getMessage().getString().startsWith("Cancel") || getMessage().getString().equals("Remove") || getMessage().getString().equals("Reset progress");
            g.drawCenteredString(font, text, getX() + width / 2, getY() + 6, !active ? 0xFF61707C : selected ? TEAL : cleanup ? RED : TEXT);
        }
    }
}
