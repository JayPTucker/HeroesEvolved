package com.jayptucker.heroesevolved.gui.screen;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.jayptucker.heroesevolved.network.AbilityActionSnapshot;
import com.jayptucker.heroesevolved.network.AbilitySnapshot;
import com.jayptucker.heroesevolved.network.AdminActionPayload;
import com.jayptucker.heroesevolved.network.ClientPowerState;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.text.NumberFormat;
import java.util.List;
import java.util.Optional;

/**
 * The player's skills menu: the power's level track, Mastery and energy,
 * its passive, and a card for every skill showing its key, cost, cooldown,
 * live status and what it does.
 */
public final class PowersScreen extends Screen {
    private static final int PANEL_WIDTH = 340;
    private static final int MAXIMUM_PANEL_HEIGHT = 262;
    private static final int PADDING = 12;
    private static final int CARD_HEIGHT = 30;
    private static final int CARD_GAP = 3;
    private static final int SKILLS_TOP = 118;

    private static final int BORDER = 0xFF6B101D;
    private static final int BACKGROUND = 0xFF16090D;
    private static final int CARD = 0xFF241017;
    private static final int CARD_LOCKED = 0xFF1A0D11;
    private static final int TITLE = 0xFFFFD7DC;
    private static final int TEXT = 0xFFE7C9CE;
    private static final int MUTED = 0xFFB89A9F;
    private static final int DIM = 0xFF6E5559;
    private static final int ACCENT = 0xFFC51E32;
    private static final int READY = 0xFF92D66C;
    private static final int TRACK = 0xFF3A2028;
    private static final int ENERGY = 0xFF3FA7D6;

    private int scroll;

    public PowersScreen() {
        super(Component.translatable("screen.heroesevolved.powers"));
    }

    private int panelHeight() {
        return Math.min(MAXIMUM_PANEL_HEIGHT, height - 8);
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - panelHeight()) / 2;

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE,
                        button -> onClose())
                .bounds(left + PANEL_WIDTH / 2 - 40, top + panelHeight() - 26, 80, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Skill Tree"),
                        button -> SkillTreeScreen.open(this))
                .bounds(left + 10, top + panelHeight() - 26, 84, 20).build());

        if (minecraft != null && minecraft.player != null
                && minecraft.player.hasPermissions(2)) {
            addRenderableWidget(Button.builder(Component.literal("Admin"),
                            button -> PacketDistributor.sendToServer(
                                    AdminActionPayload.of(
                                            AdminActionPayload.Action.OPEN,
                                            minecraft.player.getUUID())))
                    .bounds(left + PANEL_WIDTH - 54, top + 6, 46, 16)
                    .build());
        }
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        graphics.fill(0, 0, width, height, 0x50000000);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - panelHeight()) / 2;
        graphics.fill(left - 1, top - 1, left + PANEL_WIDTH + 1,
                top + panelHeight() + 1, BORDER);
        graphics.fill(left, top, left + PANEL_WIDTH, top + panelHeight(),
                BACKGROUND);

        PlayerPowerSyncPayload snapshot = ClientPowerState.getSnapshot();
        Optional<AbilitySnapshot> power = snapshot.abilities().stream().findFirst();
        int x = left + PADDING;

        if (power.isEmpty()) {
            graphics.drawString(font, title, x, top + 10, TITLE, false);
            drawWrapped(graphics, Component.translatable(
                    "screen.heroesevolved.no_power"), x, top + 34,
                    PANEL_WIDTH - PADDING * 2, MUTED, 4);
            drawEnergy(graphics, snapshot, x, top + 80);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        AbilitySnapshot ability = power.get();
        String powerKey = "ability." + ability.abilityId().getNamespace() + "."
                + ability.abilityId().getPath();

        graphics.drawString(font, Component.translatable(powerKey), x, top + 10,
                TITLE, true);
        Component status = Component.translatable(ability.unlocked()
                ? "screen.heroesevolved.awakened"
                : "screen.heroesevolved.dormant");
        graphics.drawString(font, status,
                x + font.width(Component.translatable(powerKey)) + 8, top + 10,
                ability.unlocked() ? READY : 0xFFC47C7C, false);

        drawLevelTrack(graphics, ability, x, top + 28, mouseX, mouseY);
        drawMastery(graphics, ability, x, top + 52, mouseX, mouseY);
        drawEnergy(graphics, snapshot, x, top + 66);

        Component passive = Component.translatable("screen.heroesevolved.passive")
                .append(Component.translatable(powerKey + ".passive"));
        if (!ability.unlocked()) {
            passive = Component.translatable("screen.heroesevolved.dormant_hint");
        }
        drawWrapped(graphics, passive, x, top + 82, PANEL_WIDTH - PADDING * 2,
                MUTED, 2);

        drawSkills(graphics, snapshot, ability, left, top, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Five nodes, lit up to the current level, joined by a progress line. */
    private void drawLevelTrack(
            GuiGraphics graphics,
            AbilitySnapshot ability,
            int x,
            int y,
            int mouseX,
            int mouseY
    ) {
        int levels = Math.max(1, ability.maximumLevel());
        int trackWidth = PANEL_WIDTH - PADDING * 2 - 12;
        int spacing = levels > 1 ? trackWidth / (levels - 1) : 0;
        float progress = masteryProgress(ability);

        for (int index = 0; index < levels - 1; index++) {
            int startX = x + 6 + index * spacing;
            graphics.fill(startX, y + 5, startX + spacing, y + 7, TRACK);
            int level = index + 1;
            int filled = level < ability.level() ? spacing
                    : level == ability.level() ? Math.round(spacing * progress) : 0;
            if (filled > 0) {
                graphics.fill(startX, y + 5, startX + filled, y + 7, ACCENT);
            }
        }

        for (int index = 0; index < levels; index++) {
            int level = index + 1;
            int centerX = x + 6 + index * spacing;
            boolean reached = level <= ability.level();
            graphics.fill(centerX - 6, y, centerX + 6, y + 12,
                    reached ? 0xFFFFD7DC : BORDER);
            graphics.fill(centerX - 5, y + 1, centerX + 5, y + 11,
                    reached ? ACCENT : BACKGROUND);
            String label = String.valueOf(level);
            graphics.drawString(font, label, centerX - font.width(label) / 2 + 1,
                    y + 2, reached ? 0xFFFFFFFF : DIM, false);
        }
    }

    private void drawMastery(
            GuiGraphics graphics,
            AbilitySnapshot ability,
            int x,
            int y,
            int mouseX,
            int mouseY
    ) {
        NumberFormat numbers = NumberFormat.getIntegerInstance();
        Component text;
        if (!ability.unlocked()) {
            text = Component.translatable("screen.heroesevolved.mastery_dormant");
        } else if (ability.level() >= ability.maximumLevel()) {
            text = Component.translatable("screen.heroesevolved.mastery_complete");
        } else {
            long remaining = ability.masteryRequiredForNextLevel() - ability.mastery();
            text = Component.translatable("screen.heroesevolved.mastery_line",
                    numbers.format(ability.mastery()),
                    numbers.format(ability.masteryRequiredForNextLevel()),
                    numbers.format(Math.max(0, remaining)),
                    ability.level() + 1);
        }
        graphics.drawString(font, text, x, y, TEXT, false);

        if (ability.unlocked() && mouseY >= y - 26 && mouseY < y + 9
                && mouseX >= x && mouseX < x + PANEL_WIDTH - PADDING * 2) {
            graphics.renderTooltip(font, List.of(
                    Component.translatable("screen.heroesevolved.mastery_tooltip_title"),
                    Component.translatable("screen.heroesevolved.mastery_tooltip_power"),
                    Component.translatable("screen.heroesevolved.mastery_tooltip_combat"),
                    Component.translatable("screen.heroesevolved.mastery_tooltip_active")
            ), Optional.empty(), mouseX, mouseY);
        }
    }

    private void drawEnergy(
            GuiGraphics graphics,
            PlayerPowerSyncPayload snapshot,
            int x,
            int y
    ) {
        int barWidth = 150;
        graphics.fill(x, y, x + barWidth, y + 6, TRACK);
        float fraction = snapshot.maximumEnergy() <= 0 ? 0.0F
                : Math.clamp(snapshot.energy() / (float) snapshot.maximumEnergy(),
                0.0F, 1.0F);
        graphics.fill(x, y, x + Math.round(barWidth * fraction), y + 6, ENERGY);
        graphics.drawString(font, Component.translatable(
                        "screen.heroesevolved.energy_line",
                        snapshot.energy(), snapshot.maximumEnergy(),
                        snapshot.energyRegenerationPerSecond()),
                x + barWidth + 6, y - 1, MUTED, false);
    }

    private void drawSkills(
            GuiGraphics graphics,
            PlayerPowerSyncPayload snapshot,
            AbilitySnapshot ability,
            int left,
            int top,
            int mouseX,
            int mouseY
    ) {
        int areaTop = top + SKILLS_TOP;
        int areaBottom = top + panelHeight() - 32;
        int x = left + PADDING;
        int cardWidth = PANEL_WIDTH - PADDING * 2;

        graphics.drawString(font, Component.translatable(
                "screen.heroesevolved.skills"), x, areaTop - 11, TITLE, false);

        List<AbilityActionSnapshot> actions = snapshot.actions();
        if (actions.isEmpty()) {
            graphics.drawString(font, Component.translatable(
                    "screen.heroesevolved.no_skills"), x, areaTop + 4, MUTED, false);
            return;
        }

        int contentHeight = actions.size() * (CARD_HEIGHT + CARD_GAP);
        int maximumScroll = Math.max(0, contentHeight - (areaBottom - areaTop));
        scroll = Math.clamp(scroll, 0, maximumScroll);

        graphics.enableScissor(left, areaTop, left + PANEL_WIDTH, areaBottom);
        long gameTime = minecraft == null || minecraft.level == null
                ? 0L : minecraft.level.getGameTime();
        int y = areaTop - scroll;
        for (AbilityActionSnapshot action : actions) {
            drawSkillCard(graphics, action, ability, x, y, cardWidth, gameTime);
            y += CARD_HEIGHT + CARD_GAP;
        }
        graphics.disableScissor();

        if (maximumScroll > 0) {
            String hint = scroll < maximumScroll ? "v more" : "^";
            graphics.drawString(font, hint,
                    left + PANEL_WIDTH - PADDING - font.width(hint),
                    areaTop - 11, DIM, false);
        }
    }

    private void drawSkillCard(
            GuiGraphics graphics,
            AbilityActionSnapshot action,
            AbilitySnapshot ability,
            int x,
            int y,
            int width,
            long gameTime
    ) {
        boolean usable = action.unlocked() && ability.unlocked();
        graphics.fill(x, y, x + width, y + CARD_HEIGHT,
                usable ? CARD : CARD_LOCKED);
        graphics.fill(x, y, x + 2, y + CARD_HEIGHT, usable ? ACCENT : BORDER);

        // Key badge.
        String key = keyLabel(action.slot());
        int badgeWidth = Math.max(14, font.width(key) + 6);
        graphics.fill(x + 6, y + 4, x + 6 + badgeWidth, y + 15,
                usable ? 0xFF5A1622 : 0xFF2C1A1F);
        graphics.drawString(font, key, x + 6 + (badgeWidth - font.width(key)) / 2,
                y + 6, usable ? 0xFFFFFFFF : DIM, false);

        int nameX = x + 12 + badgeWidth;
        graphics.drawString(font, Component.translatable(action.displayNameKey()),
                nameX, y + 6, usable ? TITLE : DIM, false);

        // Right side: lock, cooldown, or cost.
        Component right;
        int rightColor;
        long remaining = action.cooldownEndGameTime() - gameTime;
        if (!action.unlocked()) {
            right = Component.translatable("screen.heroesevolved.unlocks_at",
                    action.unlockLevel());
            rightColor = DIM;
        } else if (remaining > 0) {
            right = Component.translatable("screen.heroesevolved.ready_in",
                    formatSeconds(remaining));
            rightColor = 0xFFE0A040;
        } else {
            right = costLine(action);
            rightColor = MUTED;
        }
        graphics.drawString(font, right, x + width - 6 - font.width(right),
                y + 6, rightColor, false);

        List<FormattedCharSequence> description = font.split(
                Component.translatable(action.displayNameKey() + ".desc"),
                width - 18
        );
        if (!description.isEmpty()) {
            graphics.drawString(font, description.getFirst(), x + 8, y + 18,
                    usable ? MUTED : DIM, false);
        }
    }

    private Component costLine(AbilityActionSnapshot action) {
        if (action.energyCost() <= 0 && action.cooldownTicks() <= 0) {
            return Component.translatable("screen.heroesevolved.free");
        }
        Component cost = action.energyCost() > 0
                ? Component.translatable("screen.heroesevolved.cost_energy",
                action.energyCost())
                : Component.translatable("screen.heroesevolved.cost_free");
        if (action.cooldownTicks() > 0) {
            cost = cost.copy().append("  ").append(Component.translatable(
                    "screen.heroesevolved.cost_cooldown",
                    formatSeconds(action.cooldownTicks())));
        }
        return cost;
    }

    private static String formatSeconds(long ticks) {
        double seconds = ticks / 20.0D;
        if (seconds >= 60.0D) {
            long minutes = (long) (seconds / 60.0D);
            long rest = Math.round(seconds - minutes * 60.0D);
            return rest == 0 ? minutes + "m" : minutes + "m " + rest + "s";
        }
        return seconds >= 10.0D || seconds == Math.floor(seconds)
                ? Math.round(seconds) + "s"
                : String.format("%.1fs", seconds);
    }

    private static String keyLabel(AbilitySlot slot) {
        KeyMapping mapping = switch (slot) {
            case PRIMARY -> ModKeyMappings.ABILITY_1;
            case SECONDARY -> ModKeyMappings.ABILITY_2;
            case TERTIARY -> ModKeyMappings.ABILITY_3;
            case QUATERNARY -> ModKeyMappings.ABILITY_4;
            case QUINARY -> ModKeyMappings.ABILITY_5;
        };
        return mapping.getTranslatedKeyMessage().getString().toUpperCase();
    }

    private void drawWrapped(
            GuiGraphics graphics,
            Component text,
            int x,
            int y,
            int width,
            int color,
            int maximumLines
    ) {
        List<FormattedCharSequence> lines = font.split(text, width);
        for (int index = 0; index < Math.min(maximumLines, lines.size()); index++) {
            graphics.drawString(font, lines.get(index), x, y + index * 10,
                    color, false);
        }
    }

    private static float masteryProgress(AbilitySnapshot ability) {
        if (ability.level() >= ability.maximumLevel()) {
            return 1.0F;
        }

        long range = ability.masteryRequiredForNextLevel()
                - ability.masteryRequiredForCurrentLevel();
        if (range <= 0) {
            return 0.0F;
        }

        long earnedInLevel = ability.mastery()
                - ability.masteryRequiredForCurrentLevel();
        return Math.clamp(earnedInLevel / (float) range, 0.0F, 1.0F);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        scroll -= (int) (scrollY * 16);
        return true;
    }

    @Override
    public void renderBackground(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        // Intentionally empty: the Powers screen should not blur the game world.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
