package com.jayptucker.heroesevolved.gui.screen;

import com.jayptucker.heroesevolved.network.AdminActionPayload;
import com.jayptucker.heroesevolved.network.AdminActionPayload.Action;
import com.jayptucker.heroesevolved.network.AdminStatePayload;
import com.jayptucker.heroesevolved.network.ClientAdminState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.text.NumberFormat;
import java.util.List;
import java.util.UUID;

/**
 * Operator tools: pick an online player, then grant powers, set levels,
 * grant Mastery, manage energy and cooldowns, or control the Eclipse.
 * Every button is a request; the server validates permission and replies
 * with fresh state, which redraws this screen.
 */
public final class AdminScreen extends Screen {
    private static final int PANEL_WIDTH = 380;
    private static final int PANEL_HEIGHT = 236;
    private static final int LIST_WIDTH = 104;
    private static final int VISIBLE_PLAYERS = 9;
    private static final int ROW_HEIGHT = 22;
    private static final int LABEL_WIDTH = 50;

    private static final int BORDER = 0xFF6B101D;
    private static final int BACKGROUND = 0xFF16090D;
    private static final int INSET = 0xFF22101A;
    private static final int TITLE = 0xFFFFD7DC;
    private static final int MUTED = 0xFFB89A9F;
    private static final int ACCENT = 0xFFC51E32;

    // Kept across redraws so a state refresh keeps the same selection.
    private static UUID selectedPlayer;
    private static int selectedPowerIndex;
    private static int playerScroll;

    public AdminScreen() {
        super(Component.literal("Heroes Evolved Admin"));
    }

    /** Called by ClientAdminState whenever the server sends new data. */
    public void onStateUpdated() {
        rebuildWidgets();
    }

    @Override
    protected void init() {
        AdminStatePayload state = ClientAdminState.get();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        ensureSelection(state);
        addPlayerList(state, left + 8, top + 26);

        AdminStatePayload.PlayerEntry target = selectedEntry(state);
        int x = left + LIST_WIDTH + 20 + LABEL_WIDTH;
        int y = top + 76;

        if (target != null) {
            addPowerRow(state, target, x, y);
            addLevelRow(state, target, x, y + ROW_HEIGHT);
            addMasteryRow(x, y + ROW_HEIGHT * 2);
            addEnergyRow(target, x, y + ROW_HEIGHT * 3);
            addToolsRow(x, y + ROW_HEIGHT * 4);
        }
        addWorldRow(state, x, y + ROW_HEIGHT * 5);

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE,
                        button -> onClose())
                .bounds(left + PANEL_WIDTH - 88, top + PANEL_HEIGHT - 26, 80, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"),
                        button -> send(Action.REFRESH))
                .bounds(left + PANEL_WIDTH - 172, top + PANEL_HEIGHT - 26, 80, 20)
                .build());
    }

    private void addPlayerList(AdminStatePayload state, int x, int y) {
        List<AdminStatePayload.PlayerEntry> players = state.players();
        playerScroll = Math.clamp(playerScroll, 0,
                Math.max(0, players.size() - VISIBLE_PLAYERS));

        for (int row = 0; row < VISIBLE_PLAYERS
                && playerScroll + row < players.size(); row++) {
            AdminStatePayload.PlayerEntry entry = players.get(playerScroll + row);
            boolean selected = entry.id().equals(selectedPlayer);
            Component label = Component.literal(
                    (selected ? "> " : "") + entry.name()
            ).withStyle(selected ? ChatFormatting.GOLD : ChatFormatting.WHITE);
            addRenderableWidget(Button.builder(label, button -> {
                        selectedPlayer = entry.id();
                        rebuildWidgets();
                    })
                    .bounds(x, y + row * 20, LIST_WIDTH, 18)
                    .build());
        }

        if (players.size() > VISIBLE_PLAYERS) {
            int arrowsY = y + VISIBLE_PLAYERS * 20 + 2;
            addRenderableWidget(Button.builder(Component.literal("^"), button -> {
                        playerScroll--;
                        rebuildWidgets();
                    })
                    .bounds(x, arrowsY, LIST_WIDTH / 2 - 1, 16).build());
            addRenderableWidget(Button.builder(Component.literal("v"), button -> {
                        playerScroll++;
                        rebuildWidgets();
                    })
                    .bounds(x + LIST_WIDTH / 2 + 1, arrowsY, LIST_WIDTH / 2 - 1, 16)
                    .build());
        }
    }

    private void addPowerRow(
            AdminStatePayload state,
            AdminStatePayload.PlayerEntry target,
            int x,
            int y
    ) {
        List<String> powers = state.powers();
        if (powers.isEmpty()) {
            return;
        }
        selectedPowerIndex = Math.floorMod(selectedPowerIndex, powers.size());
        String power = powers.get(selectedPowerIndex);

        addRenderableWidget(Button.builder(Component.literal("<"), button -> {
                    selectedPowerIndex--;
                    rebuildWidgets();
                })
                .bounds(x, y, 14, 20).build());
        addRenderableWidget(Button.builder(powerName(power), button -> {
                    selectedPowerIndex++;
                    rebuildWidgets();
                })
                .bounds(x + 16, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> {
                    selectedPowerIndex++;
                    rebuildWidgets();
                })
                .bounds(x + 114, y, 14, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Give"),
                        button -> send(Action.GIVE_POWER, power, 0))
                .bounds(x + 130, y, 36, 20).build());
        Button remove = Button.builder(Component.literal("Remove"),
                        button -> send(Action.REMOVE_POWER))
                .bounds(x + 168, y, 38, 20).build();
        remove.active = !target.powerId().isEmpty();
        addRenderableWidget(remove);
    }

    private void addLevelRow(
            AdminStatePayload state,
            AdminStatePayload.PlayerEntry target,
            int x,
            int y
    ) {
        int shown = Math.min(5, state.maximumLevel());
        for (int level = 1; level <= shown; level++) {
            int chosen = level;
            Button button = Button.builder(Component.literal(String.valueOf(level)),
                            ignored -> send(Action.SET_LEVEL, "", chosen))
                    .bounds(x + (level - 1) * 24, y, 22, 20).build();
            button.active = target.level() != level;
            addRenderableWidget(button);
        }

        Button awaken = Button.builder(Component.literal("Awaken"),
                        button -> send(Action.AWAKEN))
                .bounds(x + shown * 24 + 4, y, 202 - shown * 24 - 4, 20).build();
        awaken.active = !target.powerId().isEmpty() && !target.awakened();
        addRenderableWidget(awaken);
    }

    private void addMasteryRow(int x, int y) {
        addRenderableWidget(Button.builder(Component.literal("+100"),
                        button -> send(Action.ADD_MASTERY, "", 100))
                .bounds(x, y, 44, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+1k"),
                        button -> send(Action.ADD_MASTERY, "", 1_000))
                .bounds(x + 46, y, 44, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+10k"),
                        button -> send(Action.ADD_MASTERY, "", 10_000))
                .bounds(x + 92, y, 44, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset"),
                        button -> send(Action.RESET_PROGRESS))
                .bounds(x + 138, y, 68, 20).build());
    }

    private void addEnergyRow(
            AdminStatePayload.PlayerEntry target,
            int x,
            int y
    ) {
        addRenderableWidget(Button.builder(Component.literal("Refill"),
                        button -> send(Action.REFILL_ENERGY))
                .bounds(x, y, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal(
                                "Unlimited: " + (target.unlimitedEnergy() ? "ON" : "OFF"))
                                .withStyle(target.unlimitedEnergy()
                                        ? ChatFormatting.GREEN : ChatFormatting.WHITE),
                        button -> send(Action.TOGGLE_UNLIMITED_ENERGY))
                .bounds(x + 62, y, 144, 20).build());
    }

    private void addToolsRow(int x, int y) {
        addRenderableWidget(Button.builder(Component.literal("Reset Cooldowns"),
                        button -> send(Action.RESET_COOLDOWNS))
                .bounds(x, y, 112, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Full Heal"),
                        button -> send(Action.FULL_HEAL))
                .bounds(x + 114, y, 92, 20).build());
    }

    private void addWorldRow(AdminStatePayload state, int x, int y) {
        boolean active = state.eclipseActive();
        addRenderableWidget(Button.builder(Component.literal(
                                active ? "Stop Eclipse" : "Start Eclipse"),
                        button -> send(active ? Action.STOP_ECLIPSE
                                : Action.START_ECLIPSE))
                .bounds(x, y, 112, 20).build());
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        graphics.fill(0, 0, width, height, 0x60000000);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        graphics.fill(left - 1, top - 1, left + PANEL_WIDTH + 1,
                top + PANEL_HEIGHT + 1, BORDER);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, BACKGROUND);
        graphics.fill(left + 4, top + 22, left + LIST_WIDTH + 12,
                top + PANEL_HEIGHT - 32, INSET);

        graphics.drawString(font, title, left + 10, top + 8, TITLE, false);
        graphics.drawString(font, Component.literal("OPERATOR"),
                left + PANEL_WIDTH - 10 - font.width("OPERATOR"), top + 8,
                ACCENT, false);

        AdminStatePayload state = ClientAdminState.get();
        AdminStatePayload.PlayerEntry target = selectedEntry(state);
        int infoX = left + LIST_WIDTH + 20;

        if (target == null) {
            graphics.drawString(font, "No players online.", infoX, top + 30,
                    MUTED, false);
        } else {
            drawStatus(graphics, target, state.maximumLevel(), infoX, top + 28);

            String[] labels = {"Power", "Level", "Mastery", "Energy", "Tools"};
            for (int row = 0; row < labels.length; row++) {
                graphics.drawString(font, labels[row], infoX,
                        top + 82 + row * ROW_HEIGHT, MUTED, false);
            }
        }
        graphics.drawString(font, "World", infoX, top + 82 + 5 * ROW_HEIGHT,
                MUTED, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawStatus(
            GuiGraphics graphics,
            AdminStatePayload.PlayerEntry target,
            int maximumLevel,
            int x,
            int y
    ) {
        NumberFormat numbers = NumberFormat.getIntegerInstance();
        graphics.drawString(font, Component.literal(target.name())
                .withStyle(ChatFormatting.BOLD), x, y, TITLE, false);

        Component power = target.powerId().isEmpty()
                ? Component.literal("No power")
                : powerName(target.powerId()).copy().append(Component.literal(
                        target.awakened() ? "  (Awakened)" : "  (Dormant)"));
        graphics.drawString(font, power, x, y + 12,
                target.awakened() ? 0xFF92D66C : MUTED, false);

        String mastery = target.level() >= maximumLevel
                ? numbers.format(target.mastery()) + " (max)"
                : numbers.format(target.mastery()) + " / "
                        + numbers.format(target.masteryForNextLevel());
        graphics.drawString(font, "Level " + target.level() + "/" + maximumLevel
                + "   Mastery " + mastery, x, y + 24, MUTED, false);

        String energy = target.unlimitedEnergy()
                ? "Energy: Unlimited"
                : "Energy " + target.energy() + " / " + target.maximumEnergy();
        graphics.drawString(font, energy, x, y + 36, ACCENT, false);
    }

    private void ensureSelection(AdminStatePayload state) {
        boolean stillOnline = state.players().stream()
                .anyMatch(entry -> entry.id().equals(selectedPlayer));
        if (!stillOnline) {
            selectedPlayer = state.players().isEmpty()
                    ? null
                    : state.players().getFirst().id();
        }
    }

    private static AdminStatePayload.PlayerEntry selectedEntry(
            AdminStatePayload state
    ) {
        return state.players().stream()
                .filter(entry -> entry.id().equals(selectedPlayer))
                .findFirst()
                .orElse(null);
    }

    private static Component powerName(String powerId) {
        ResourceLocation id = ResourceLocation.tryParse(powerId);
        return id == null
                ? Component.literal(powerId)
                : Component.translatable("ability." + id.getNamespace() + "."
                        + id.getPath());
    }

    private void send(Action action) {
        send(action, "", 0);
    }

    private void send(Action action, String argument, int value) {
        UUID target = selectedPlayer == null
                ? (minecraft == null || minecraft.player == null
                        ? new UUID(0L, 0L) : minecraft.player.getUUID())
                : selectedPlayer;
        PacketDistributor.sendToServer(
                new AdminActionPayload(action, target, argument, value)
        );
    }

    @Override
    public void renderBackground(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        // The panel draws its own dim backdrop; skip the world blur.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
