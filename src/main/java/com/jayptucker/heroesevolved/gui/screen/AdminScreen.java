package com.jayptucker.heroesevolved.gui.screen;

import com.jayptucker.heroesevolved.network.AdminActionPayload;
import com.jayptucker.heroesevolved.network.AdminActionPayload.Action;
import com.jayptucker.heroesevolved.network.AdminStatePayload;
import com.jayptucker.heroesevolved.network.AdminStatePayload.World;
import com.jayptucker.heroesevolved.network.ClientAdminState;
import net.minecraft.ChatFormatting;
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

/**
 * Operator tools, in two tabs beside the list of online players.
 * <ul>
 *   <li><b>Player:</b> the chosen player's power, level, Mastery, energy, where they are and how far the stories have
 *   got with them; give or remove powers, set levels, refill, heal, go to them.</li>
 *   <li><b>World &amp; Events:</b> the Eclipse, the two bosses (Sylar the Watchmaker and Elle Bishop), village events
 *   (a meltdown or a raid near the chosen player) and the emergency band, each with a status line and its controls.</li>
 * </ul>
 * Every button is a request; the server checks permission and replies with fresh state, which redraws this screen.
 */
public final class AdminScreen extends Screen {
    private static final int MAX_WIDTH = 440, MAX_HEIGHT = 240;
    private static final int LIST_WIDTH = 108;
    private static final int ROW = 22;
    private static final int LABEL_WIDTH = 46;
    private static final int SECTION = 36;

    private static final int BORDER = 0xFF6B101D;
    private static final int BACKGROUND = 0xF016090D;
    private static final int INSET = 0xFF22101A;
    private static final int RULE = 0xFF3A1A24;
    private static final int TITLE = 0xFFFFD7DC;
    private static final int TEXT = 0xFFE9D9DC;
    private static final int MUTED = 0xFFB89A9F;
    private static final int ACCENT = 0xFFC51E32;
    private static final int GOOD = 0xFF92D66C;
    private static final int IDLE = 0xFF5A4248;
    private static final int GOLD = 0xFFE8B84A, SYLAR = 0xFFD8454F, ELLE = 0xFF6FB8FF, VILLAGE = 0xFF8FD16A, RADIO = 0xFF5FD3C8;

    enum Tab { PLAYER, WORLD }

    // Kept across redraws so a state refresh keeps the same selection.
    private static UUID selectedPlayer;
    private static int selectedPowerIndex;
    private static int playerScroll;
    private static Tab tab = Tab.PLAYER;

    /** Status lines drawn this frame, and their full text for hovering when cut short. */
    private record Line(int x, int y, int width, String text) {}
    private final List<Line> lines = new ArrayList<>();

    private int left, top, panelWidth, panelHeight;

    public AdminScreen() {
        super(Component.literal("Heroes Evolved Admin"));
    }

    /** Called by ClientAdminState whenever the server sends new data. */
    public void onStateUpdated() {
        rebuildWidgets();
    }

    /** For previews: which tab shows. */
    public static void show(Tab which) { tab = which; }
    public static void showWorld() { tab = Tab.WORLD; }
    public static void showPlayer() { tab = Tab.PLAYER; }

    private int contentX() { return left + LIST_WIDTH + 18; }
    private int contentWidth() { return panelWidth - LIST_WIDTH - 26; }
    private int visiblePlayers() { return Math.max(3, (panelHeight - 74) / 20); }

    @Override
    protected void init() {
        panelWidth = Math.min(MAX_WIDTH, width - 12);
        panelHeight = Math.min(MAX_HEIGHT, height - 8);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        AdminStatePayload state = ClientAdminState.get();
        ensureSelection(state);
        addHeader();
        addPlayerList(state, left + 8, top + 48);
        AdminStatePayload.PlayerEntry target = selectedEntry(state);
        if (tab == Tab.PLAYER) {
            if (target != null) addPlayerTab(state, target);
        } else addWorldTab(state, target);
    }

    // ------------------------------------------------------------------
    // Header: tabs, refresh, close
    // ------------------------------------------------------------------

    private void addHeader() {
        int y = top + 6;
        int x = contentX();
        tabButton("Player", Tab.PLAYER, x, y, 64, "The chosen player's power, level, energy and tools.");
        tabButton("World & Events", Tab.WORLD, x + 66, y, 96, "The Eclipse, the bosses, village events and the emergency band.");
        addRenderableWidget(Button.builder(Component.literal("⟳"), button -> send(Action.REFRESH))
                .tooltip(Tooltip.create(Component.literal("Refresh"))).bounds(left + panelWidth - 48, y, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("✕"), button -> onClose())
                .tooltip(Tooltip.create(Component.literal("Close"))).bounds(left + panelWidth - 26, y, 20, 18).build());
    }

    private void tabButton(String label, Tab which, int x, int y, int w, String tip) {
        boolean on = tab == which;
        Button button = Button.builder(Component.literal(label).withStyle(on ? ChatFormatting.GOLD : ChatFormatting.GRAY), b -> {
                    tab = which;
                    rebuildWidgets();
                })
                .tooltip(Tooltip.create(Component.literal(tip))).bounds(x, y, w, 18).build();
        button.active = !on;
        addRenderableWidget(button);
    }

    // ------------------------------------------------------------------
    // Players
    // ------------------------------------------------------------------

    private void addPlayerList(AdminStatePayload state, int x, int y) {
        List<AdminStatePayload.PlayerEntry> players = state.players();
        int visible = visiblePlayers();
        playerScroll = Math.clamp(playerScroll, 0, Math.max(0, players.size() - visible));
        for (int row = 0; row < visible && playerScroll + row < players.size(); row++) {
            AdminStatePayload.PlayerEntry entry = players.get(playerScroll + row);
            boolean selected = entry.id().equals(selectedPlayer);
            Component label = Component.literal((selected ? "▶ " : "") + entry.name())
                    .withStyle(selected ? ChatFormatting.GOLD : ChatFormatting.WHITE);
            Component tip = Component.literal(entry.name() + "\n").withStyle(ChatFormatting.BOLD)
                    .append(Component.literal((entry.powerId().isEmpty() ? "No power" : powerName(entry.powerId()).getString())
                            + "  Level " + entry.level() + "\n" + entry.whereabouts()).withStyle(ChatFormatting.GRAY));
            addRenderableWidget(Button.builder(label, button -> {
                        selectedPlayer = entry.id();
                        rebuildWidgets();
                    })
                    .tooltip(Tooltip.create(tip)).bounds(x, y + row * 20, LIST_WIDTH, 18).build());
        }
        if (players.size() > visible) {
            int arrowsY = y + visible * 20 + 2;
            addRenderableWidget(Button.builder(Component.literal("▲"), button -> { playerScroll--; rebuildWidgets(); })
                    .bounds(x, arrowsY, LIST_WIDTH / 2 - 1, 14).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), button -> { playerScroll++; rebuildWidgets(); })
                    .bounds(x + LIST_WIDTH / 2 + 1, arrowsY, LIST_WIDTH / 2 - 1, 14).build());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= left && mouseX < left + LIST_WIDTH + 14 && scrollY != 0) {
            playerScroll -= (int) Math.signum(scrollY);
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ------------------------------------------------------------------
    // The Player tab
    // ------------------------------------------------------------------

    /** Where the Player tab's rows start, under the player's card. */
    private int playerRowsY() { return top + 112; }

    private void addPlayerTab(AdminStatePayload state, AdminStatePayload.PlayerEntry target) {
        int x = contentX() + LABEL_WIDTH;
        int w = contentWidth() - LABEL_WIDTH;
        int y = playerRowsY();
        addPowerRow(state, target, x, y, w);
        addLevelRow(state, target, x, y + ROW, w);
        addMasteryRow(x, y + ROW * 2, w);
        addEnergyRow(target, x, y + ROW * 3, w);
        addToolsRow(target, x, y + ROW * 4, w);
    }

    private void addPowerRow(AdminStatePayload state, AdminStatePayload.PlayerEntry target, int x, int y, int w) {
        List<String> powers = state.powers();
        if (powers.isEmpty()) return;
        selectedPowerIndex = Math.floorMod(selectedPowerIndex, powers.size());
        String power = powers.get(selectedPowerIndex);
        int name = w - 16 - 16 - 40 - 50 - 6;
        addRenderableWidget(Button.builder(Component.literal("◀"), button -> { selectedPowerIndex--; rebuildWidgets(); })
                .bounds(x, y, 14, 20).build());
        addRenderableWidget(Button.builder(powerName(power), button -> { selectedPowerIndex++; rebuildWidgets(); })
                .tooltip(Tooltip.create(Component.literal("Click or use the arrows to pick a power (" + (selectedPowerIndex + 1)
                        + " of " + powers.size() + ").")))
                .bounds(x + 16, y, name, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), button -> { selectedPowerIndex++; rebuildWidgets(); })
                .bounds(x + 18 + name, y, 14, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Give").withStyle(ChatFormatting.GREEN),
                        button -> send(Action.GIVE_POWER, power, 0))
                .tooltip(Tooltip.create(Component.literal("Replace their power with this one, already awakened.")))
                .bounds(x + 34 + name, y, 40, 20).build());
        Button remove = Button.builder(Component.literal("Remove"), button -> send(Action.REMOVE_POWER))
                .tooltip(Tooltip.create(Component.literal("Take their power away.")))
                .bounds(x + 76 + name, y, 50, 20).build();
        remove.active = !target.powerId().isEmpty();
        addRenderableWidget(remove);
    }

    private void addLevelRow(AdminStatePayload state, AdminStatePayload.PlayerEntry target, int x, int y, int w) {
        // Power Level 1 to 20: a step down, the start of each stage, a step up.
        int max = state.maximumLevel();
        int[] levels = {target.level() - 1, 1, 10, 15, 20, target.level() + 1};
        String[] labels = {"−", "1", "10", "15", "20", "+"};
        for (int i = 0; i < levels.length; i++) {
            int chosen = Math.clamp(levels[i], 1, max);
            Button button = Button.builder(Component.literal(labels[i]), ignored -> send(Action.SET_LEVEL, "", chosen))
                    .tooltip(Tooltip.create(Component.literal("Set to Level " + chosen + ".")))
                    .bounds(x + i * 24, y, 22, 20).build();
            button.active = target.level() != chosen && levels[i] <= max && levels[i] >= 1;
            addRenderableWidget(button);
        }
        int at = x + levels.length * 24 + 2;
        Button awaken = Button.builder(Component.literal("Awaken"), button -> send(Action.AWAKEN))
                .tooltip(Tooltip.create(Component.literal("Wake their dormant power now, with its first-awakening scene.")))
                .bounds(at, y, x + w - at, 20).build();
        awaken.active = !target.powerId().isEmpty() && !target.awakened();
        addRenderableWidget(awaken);
    }

    private void addMasteryRow(int x, int y, int w) {
        int third = (w - 70) / 3;
        int[] amounts = {100, 1_000, 10_000};
        String[] labels = {"+100", "+1k", "+10k"};
        for (int i = 0; i < 3; i++) {
            int amount = amounts[i];
            addRenderableWidget(Button.builder(Component.literal(labels[i]), button -> send(Action.ADD_MASTERY, "", amount))
                    .tooltip(Tooltip.create(Component.literal("Give " + NumberFormat.getIntegerInstance().format(amount) + " Mastery.")))
                    .bounds(x + i * (third + 2), y, third, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Reset").withStyle(ChatFormatting.RED), button -> send(Action.RESET_PROGRESS))
                .tooltip(Tooltip.create(Component.literal("Back to Level 1 with no Mastery.")))
                .bounds(x + 3 * (third + 2), y, w - 3 * (third + 2), 20).build());
    }

    private void addEnergyRow(AdminStatePayload.PlayerEntry target, int x, int y, int w) {
        addRenderableWidget(Button.builder(Component.literal("Refill"), button -> send(Action.REFILL_ENERGY))
                .tooltip(Tooltip.create(Component.literal("Fill their energy to the top."))).bounds(x, y, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Unlimited: " + (target.unlimitedEnergy() ? "ON" : "OFF"))
                                .withStyle(target.unlimitedEnergy() ? ChatFormatting.GREEN : ChatFormatting.WHITE),
                        button -> send(Action.TOGGLE_UNLIMITED_ENERGY))
                .tooltip(Tooltip.create(Component.literal("Abilities cost them nothing while this is on.")))
                .bounds(x + 62, y, w - 62, 20).build());
    }

    private void addToolsRow(AdminStatePayload.PlayerEntry target, int x, int y, int w) {
        int third = (w - 4) / 3;
        addRenderableWidget(Button.builder(Component.literal("Cooldowns"), button -> send(Action.RESET_COOLDOWNS))
                .tooltip(Tooltip.create(Component.literal("Reset every cooldown."))).bounds(x, y, third, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Full Heal"), button -> send(Action.FULL_HEAL))
                .tooltip(Tooltip.create(Component.literal("Health, food, no effects, no fire."))).bounds(x + third + 2, y, third, 20).build());
        Button go = Button.builder(Component.literal("Go To"), button -> send(Action.TELEPORT_TO))
                .tooltip(Tooltip.create(Component.literal("Teleport yourself to them.")))
                .bounds(x + 2 * (third + 2), y, w - 2 * (third + 2), 20).build();
        go.active = minecraft == null || minecraft.player == null || !minecraft.player.getUUID().equals(target.id());
        addRenderableWidget(go);
    }

    // ------------------------------------------------------------------
    // The World & Events tab
    // ------------------------------------------------------------------

    /** Where the World tab's sections start, under the "acts on" line. */
    private int worldY() { return top + 48; }

    private void addWorldTab(AdminStatePayload state, AdminStatePayload.PlayerEntry target) {
        World world = state.world();
        int x = contentX();
        int w = contentWidth();
        int y = worldY() + 12;
        boolean someone = target != null;

        // The Eclipse.
        buttons(x, y, w,
                button("Start", "A full Eclipse from first contact.", Action.START_ECLIPSE, 0, !world.has(World.ECLIPSE)),
                button("Totality", "Straight to totality: dormant powers are handed out and every power falls silent.",
                        Action.ECLIPSE_TOTALITY, 0, !world.has(World.TOTALITY)),
                button("Stop", "End the Eclipse now.", Action.STOP_ECLIPSE, 0, world.has(World.ECLIPSE)));
        y += SECTION;
        // Sylar, the Watchmaker.
        buttons(x, y, w,
                button("Stalk", "He finds the chosen player's trail and watches from a distance.", Action.SYLAR_STALK, 0,
                        someone && !world.has(World.SYLAR)),
                button("Hunt", "He comes for the chosen player now; the fight moves to his clock shop.", Action.SYLAR_HUNT, 0,
                        someone && !world.has(World.SYLAR)),
                button("Dismiss", "He withdraws and everyone goes home.", Action.SYLAR_DISMISS, 0, world.has(World.SYLAR)));
        y += SECTION;
        // Elle Bishop.
        buttons(x, y, w,
                button("Omens", "A day of flickering lights and misfiring redstone around the chosen player; she comes when it ends.",
                        Action.ELLE_OMENS, 0, someone && !world.has(World.ELLE)),
                button("Arrive", "The lights go out and she comes for the chosen player now.", Action.ELLE_ARRIVE, 0,
                        someone && !world.has(World.ELLE)),
                button("Dismiss", "She leaves and everyone goes home.", Action.ELLE_DISMISS, 0, world.has(World.ELLE)));
        y += SECTION;
        // Village events near the chosen player.
        buttons(x, y, w,
                button("Meltdown", "The villager nearest the chosen player starts to glow: one minute.", Action.MELTDOWN_START, 60, someone),
                button("20 s", "A quick meltdown: twenty seconds.", Action.MELTDOWN_START, 20, someone),
                button("Raid", "A vanilla raid on the village nearest the chosen player.", Action.RAID_START, 0, someone),
                button("End", "End every meltdown with nothing happening.", Action.MELTDOWN_CANCEL, 0, world.has(World.MELTDOWN)));
        y += SECTION;
        // The emergency band.
        buttons(x, y, w,
                button("Give tuned radio", "Give the chosen player a walkie-talkie tuned to 101.10 FM.", Action.GIVE_RADIO, 0,
                        someone && world.has(World.ON_AIR)));
    }

    private record Spec(String label, String tip, Action action, int value, boolean active) {}

    private static Spec button(String label, String tip, Action action, int value, boolean active) {
        return new Spec(label, tip, action, value, active);
    }

    /** A row of buttons sharing the width evenly. */
    private void buttons(int x, int y, int w, Spec... specs) {
        int gap = 2, each = (w - gap * (specs.length - 1)) / specs.length;
        for (int i = 0; i < specs.length; i++) {
            Spec s = specs[i];
            int bx = x + i * (each + gap), bw = i == specs.length - 1 ? x + w - bx : each;
            Button b = Button.builder(Component.literal(s.label()), button -> send(s.action(), "", s.value()))
                    .tooltip(Tooltip.create(Component.literal(s.tip()))).bounds(bx, y, bw, 20).build();
            b.active = s.active();
            addRenderableWidget(b);
        }
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x70000000);
        graphics.fill(left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, BORDER);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, BACKGROUND);
        // Title bar and the list's inset.
        graphics.fill(left, top, left + panelWidth, top + 29, 0xFF1E0C12);
        graphics.fill(left, top + 29, left + panelWidth, top + 30, BORDER);
        graphics.fill(left + 4, top + 34, left + LIST_WIDTH + 12, top + panelHeight - 4, INSET);

        graphics.drawString(font, Component.literal("HEROES EVOLVED").withStyle(ChatFormatting.BOLD), left + 10, top + 7, TITLE, false);
        graphics.drawString(font, "Operator tools", left + 10, top + 17, ACCENT, false);

        AdminStatePayload state = ClientAdminState.get();
        AdminStatePayload.PlayerEntry target = selectedEntry(state);
        graphics.drawString(font, "PLAYERS  " + state.players().size(), left + 10, top + 37, MUTED, false);
        lines.clear();
        if (tab == Tab.PLAYER) {
            if (target == null) graphics.drawString(font, "No players online.", contentX(), top + 40, MUTED, false);
            else drawPlayer(graphics, target, state.maximumLevel());
        } else drawWorld(graphics, state.world(), target);

        super.render(graphics, mouseX, mouseY, partialTick);
        // A status line cut short shows in full on hover.
        for (Line l : lines)
            if (mouseX >= l.x && mouseX < l.x + l.width && mouseY >= l.y - 1 && mouseY < l.y + 9)
                graphics.renderTooltip(font, font.split(Component.literal(l.text), 220), mouseX, mouseY);
    }

    private void drawPlayer(GuiGraphics graphics, AdminStatePayload.PlayerEntry target, int maximumLevel) {
        NumberFormat numbers = NumberFormat.getIntegerInstance();
        int x = contentX(), w = contentWidth(), y = top + 38;
        graphics.drawString(font, Component.literal(target.name()).withStyle(ChatFormatting.BOLD), x, y, TITLE, false);
        Component power = target.powerId().isEmpty() ? Component.literal("No power")
                : powerName(target.powerId()).copy().append(Component.literal(target.awakened() ? "  Awakened" : "  Dormant"));
        int powerWidth = font.width(power);
        graphics.drawString(font, power, x + w - powerWidth, y, target.awakened() ? GOOD : MUTED, false);

        // Level and Mastery, with a bar to the next level.
        y += 13;
        boolean maxed = target.level() >= maximumLevel;
        String mastery = maxed ? numbers.format(target.mastery()) + " Mastery (max)"
                : numbers.format(target.mastery()) + " / " + numbers.format(target.masteryForNextLevel()) + " Mastery";
        graphics.drawString(font, "Level " + target.level() + "/" + maximumLevel, x, y, TEXT, false);
        graphics.drawString(font, mastery, x + w - font.width(mastery), y, MUTED, false);
        bar(graphics, x, y + 10, w, maxed ? 1 : target.masteryForNextLevel() <= 0 ? 0
                : (float) target.mastery() / target.masteryForNextLevel(), GOLD);

        // Energy, with its bar.
        y += 17;
        String energy = target.unlimitedEnergy() ? "Energy  Unlimited" : "Energy  " + target.energy() + " / " + target.maximumEnergy();
        graphics.drawString(font, energy, x, y, TEXT, false);
        bar(graphics, x, y + 10, w, target.unlimitedEnergy() ? 1 : target.maximumEnergy() <= 0 ? 0
                : (float) target.energy() / target.maximumEnergy(), target.unlimitedEnergy() ? GOOD : ACCENT);

        // Where they are, and the stories.
        y += 17;
        status(graphics, x, y, w, target.whereabouts(), MUTED);
        status(graphics, x, y + 10, w, target.story(), MUTED);

        graphics.fill(x, playerRowsY() - 5, x + w, playerRowsY() - 4, RULE);
        String[] labels = {"Power", "Level", "Mastery", "Energy", "Tools"};
        for (int row = 0; row < labels.length; row++)
            graphics.drawString(font, labels[row], x, playerRowsY() + 6 + row * ROW, MUTED, false);
    }

    private void drawWorld(GuiGraphics graphics, World world, AdminStatePayload.PlayerEntry target) {
        int x = contentX(), w = contentWidth(), y = worldY();
        String acts = target == null ? "No player chosen: only world-wide controls work." : "Player events act on " + target.name() + ".";
        graphics.drawString(font, acts, x, top + 36, target == null ? ACCENT : MUTED, false);
        y += 2;
        section(graphics, x, y, w, "ECLIPSE", GOLD, world.has(World.ECLIPSE), world.eclipse());
        section(graphics, x, y + SECTION, w, "SYLAR", SYLAR, world.has(World.SYLAR), world.sylar());
        section(graphics, x, y + SECTION * 2, w, "ELLE", ELLE, world.has(World.ELLE), world.elle());
        section(graphics, x, y + SECTION * 3, w, "VILLAGE", VILLAGE, world.has(World.MELTDOWN) || world.has(World.RAID), world.village());
        section(graphics, x, y + SECTION * 4, w, "RADIO", RADIO, world.has(World.ON_AIR), world.radio());
    }

    /** A section's heading: a dot (lit while it is going on), its name, and its status line beside it. */
    private void section(GuiGraphics graphics, int x, int y, int w, String name, int colour, boolean on, String text) {
        graphics.fill(x, y + 2, x + 5, y + 7, on ? colour : IDLE);
        graphics.drawString(font, Component.literal(name).withStyle(ChatFormatting.BOLD), x + 9, y, colour, false);
        int at = x + 9 + 52;
        status(graphics, at, y, x + w - at, text, on ? TEXT : MUTED);
        if (y > worldY() + 4) graphics.fill(x, y - 5, x + w, y - 4, RULE);
    }

    /** A line of text cut to fit, with "…", that shows in full on hover. */
    private void status(GuiGraphics graphics, int x, int y, int w, String text, int colour) {
        if (text == null || text.isEmpty()) return;
        String shown = text;
        if (font.width(text) > w) shown = font.plainSubstrByWidth(text, w - font.width("…")) + "…";
        graphics.drawString(font, shown, x, y, colour, false);
        if (!shown.equals(text)) lines.add(new Line(x, y, w, text));
    }

    private static void bar(GuiGraphics graphics, int x, int y, int w, float fraction, int colour) {
        graphics.fill(x, y, x + w, y + 3, 0xFF2E1820);
        graphics.fill(x, y, x + Math.round(w * Math.clamp(fraction, 0, 1)), y + 3, colour);
    }

    // ------------------------------------------------------------------

    private void ensureSelection(AdminStatePayload state) {
        boolean stillOnline = state.players().stream().anyMatch(entry -> entry.id().equals(selectedPlayer));
        if (!stillOnline) selectedPlayer = state.players().isEmpty() ? null : state.players().getFirst().id();
    }

    private static AdminStatePayload.PlayerEntry selectedEntry(AdminStatePayload state) {
        return state.players().stream().filter(entry -> entry.id().equals(selectedPlayer)).findFirst().orElse(null);
    }

    private static Component powerName(String powerId) {
        ResourceLocation id = ResourceLocation.tryParse(powerId);
        return id == null ? Component.literal(powerId) : Component.translatable("ability." + id.getNamespace() + "." + id.getPath());
    }

    private void send(Action action) {
        send(action, "", 0);
    }

    private void send(Action action, String argument, int value) {
        UUID target = selectedPlayer == null
                ? (minecraft == null || minecraft.player == null ? new UUID(0L, 0L) : minecraft.player.getUUID())
                : selectedPlayer;
        PacketDistributor.sendToServer(new AdminActionPayload(action, target, argument, value));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The panel draws its own dim backdrop; skip the world blur.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
