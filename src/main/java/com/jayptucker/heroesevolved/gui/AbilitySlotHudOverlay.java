package com.jayptucker.heroesevolved.gui;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.jayptucker.heroesevolved.network.AbilityActionSnapshot;
import com.jayptucker.heroesevolved.network.ClientPowerState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(
        modid = HeroesEvolved.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD
)
public final class AbilitySlotHudOverlay {
    private static final int START_X = 8;
    private static final int START_Y = 8;
    private static final int LINE_HEIGHT = 12;

    private AbilitySlotHudOverlay() {
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(
                        HeroesEvolved.MOD_ID,
                        "ability_slots"
                ),
                AbilitySlotHudOverlay::render
        );
    }

    private static void render(
            GuiGraphics guiGraphics,
            net.minecraft.client.DeltaTracker deltaTracker
    ) {
        // Dormant powers are intentionally unknown to the player. Do not
        // reveal their actions or controls until the awakening occurs.
        if (!hasUnlockedAbility()) {
            return;
        }

        int y = START_Y;

        for (AbilityActionSnapshot action
                : ClientPowerState.getSnapshot().actions()) {
            boolean onCooldown = isOnCooldown(action);

            int textColor = getTextColor(action, onCooldown);
            String text = buildDisplayText(action, onCooldown);

            guiGraphics.drawString(
                    Minecraft.getInstance().font,
                    text,
                    START_X,
                    y,
                    textColor,
                    true
            );

            y += LINE_HEIGHT;
        }

        if (hasUnlockedGroupTeleport()) {
            String groupTeleportText = Minecraft.getInstance()
                    .options
                    .keyShift
                    .getTranslatedKeyMessage()
                    .getString()
                    + " + "
                    + getKeyMapping(AbilitySlot.PRIMARY)
                    .getTranslatedKeyMessage()
                    .getString()
                    + "  Group Teleport";

            guiGraphics.drawString(
                    Minecraft.getInstance().font,
                    groupTeleportText,
                    START_X,
                    y,
                    0xFFFFD7DC,
                    true
            );
            y += LINE_HEIGHT;
        }

        y = drawRadiationCore(guiGraphics, y);
        y = drawRegrowth(guiGraphics, y);

        if (!hasFlightSpeedControl()) {
            return;
        }

        var mc = Minecraft.getInstance();
        int step = mc.player == null ? 0 : com.jayptucker.heroesevolved.network.ClientFlightVisualState.speedStep(mc.player.getId());
        String speedText = net.minecraft.network.chat.Component.translatable(
                "hud.heroesevolved.flight_speed", step + 1).getString();

        guiGraphics.drawString(
                Minecraft.getInstance().font,
                speedText,
                START_X,
                y,
                0xFFFFD7DC,
                true
        );
        if (mc.player != null && (com.jayptucker.heroesevolved.network.ClientFlightVisualState.isFlightEnabled(mc.player.getId())
                || com.jayptucker.heroesevolved.network.ClientFlightVisualState.isDiving(mc.player.getId()))) {
            int id = mc.player.getId();
            int momentum = com.jayptucker.heroesevolved.network.ClientFlightVisualState.momentum(id);
            int color = momentum >= 100 ? 0xFFFFFFFF : 0xFF8EDAF0;
            y += LINE_HEIGHT;
            guiGraphics.drawString(mc.font, Component.translatable("hud.heroesevolved.flight_momentum", momentum),
                    START_X, y, color, true);
            guiGraphics.fill(START_X, y + 10, START_X + 100, y + 13, 0x80314650);
            guiGraphics.fill(START_X, y + 10, START_X + momentum, y + 13, color);
            String maneuver = com.jayptucker.heroesevolved.network.ClientFlightVisualState.isCharging(id) ? "flight_charging"
                    : com.jayptucker.heroesevolved.network.ClientFlightVisualState.isDiving(id) ? "flight_diving"
                    : com.jayptucker.heroesevolved.network.ClientFlightVisualState.isCycloneActive(id) ? "flight_tempest" : null;
            if (maneuver != null) guiGraphics.drawString(mc.font,
                    Component.translatable("hud.heroesevolved." + maneuver), START_X, y + 18, 0xFFCBEFF8, true);
        }
    }

    /** Claire Bennet's Regrowth: stored healing from the hits she has taken, and whether Resurrection is ready. */
    private static int drawRegrowth(GuiGraphics guiGraphics, int y) {
        var mc = Minecraft.getInstance();
        var regen = ClientPowerState.getSnapshot().abilities().stream().filter(power -> power.unlocked()
                && power.abilityId().equals(com.jayptucker.heroesevolved.ability.registry.ModAbilities.REGENERATION_ID)).findFirst().orElse(null);
        if (mc.player == null || regen == null) return y;
        int regrowth = com.jayptucker.heroesevolved.network.ClientRegenerationState.regrowth();
        int color = regrowth >= 50 ? 0xFFFF5A64 : 0xFFD0303C;
        guiGraphics.drawString(mc.font, Component.translatable("hud.heroesevolved.regrowth", regrowth), START_X, y, color, true);
        guiGraphics.fill(START_X, y + 10, START_X + 100, y + 13, 0x80401018);
        guiGraphics.fill(START_X, y + 10, START_X + regrowth, y + 13, color);
        // The notch marks what a Second Life costs.
        guiGraphics.fill(START_X + 50, y + 9, START_X + 51, y + 14, 0xC0FFFFFF);
        y += LINE_HEIGHT + 6;
        long down = com.jayptucker.heroesevolved.network.ClientRegenerationState.downedRemaining();
        if (down > 0) {
            // Down on the ground: a countdown, and how to give up once you're allowed to.
            guiGraphics.drawString(mc.font, Component.translatable("hud.heroesevolved.downed", time(down)), START_X, y, 0xFFFF6A6A, true);
            y += LINE_HEIGHT;
            int giveUp = com.jayptucker.heroesevolved.network.ClientRegenerationState.giveUp();
            if (giveUp >= 0) {
                guiGraphics.drawString(mc.font, Component.translatable("hud.heroesevolved.give_up",
                        mc.options.keyShift.getTranslatedKeyMessage()), START_X, y, 0xFFB89A9A, true);
                if (giveUp > 0) guiGraphics.fill(START_X, y + 10, START_X + giveUp * 100 / 60, y + 12, 0xFFFFD9A0);
                y += LINE_HEIGHT + 4;
            }
        } else {
            int recovery = com.jayptucker.heroesevolved.network.ClientRegenerationState.recoveryTicks();
            guiGraphics.drawString(mc.font, Component.translatable("hud.heroesevolved.unkillable", time(recovery)), START_X, y, 0xFFFFD9A0, true);
            y += LINE_HEIGHT;
        }
        return y;
    }

    private static String time(long ticks) {
        long seconds = (ticks + 19) / 20;
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    /** Ted Sprague's Core: a bar that turns orange when Hot, red when Critical, and flashes at 100. */
    private static int drawRadiationCore(GuiGraphics guiGraphics, int y) {
        var mc = Minecraft.getInstance();
        boolean ted = ClientPowerState.getSnapshot().abilities().stream().anyMatch(power -> power.unlocked()
                && power.abilityId().equals(com.jayptucker.heroesevolved.ability.registry.ModAbilities.RADIATION_ID));
        if (mc.player == null || !ted) return y;
        int core = Math.max(0, com.jayptucker.heroesevolved.client.effects.RadiationVisuals.stress(mc.player.getId()));
        String tier = core >= 100 ? "meltdown" : core >= 80 ? "critical" : core >= 60 ? "glowing" : core >= 40 ? "hot" : "stable";
        int color = core >= 100 ? ((mc.level.getGameTime() / 4) % 2 == 0 ? 0xFFFFFFFF : 0xFFFF4040)
                : core >= 80 ? 0xFFFF5A3A : core >= 40 ? 0xFFFFA040 : 0xFFB8E04A;
        guiGraphics.drawString(mc.font, Component.translatable("hud.heroesevolved.radiation_core", core,
                Component.translatable("hud.heroesevolved.radiation_" + tier)), START_X, y, color, true);
        guiGraphics.fill(START_X, y + 10, START_X + 100, y + 13, 0x80402A18);
        guiGraphics.fill(START_X, y + 10, START_X + core, y + 13, color);
        // Notches at the tier boundaries.
        for (int mark : new int[] {40, 60, 80}) guiGraphics.fill(START_X + mark, y + 9, START_X + mark + 1, y + 14, 0xC0FFFFFF);
        return y + LINE_HEIGHT + 6;
    }

    private static boolean hasFlightSpeedControl() {
        return ClientPowerState.getSnapshot().abilities().stream().anyMatch(
                ability -> ability.unlocked()
                        && ability.abilityId().equals(ModAbilities.FLIGHT_ID)
                        && ability.level() >= 1
        );
    }

    private static boolean hasUnlockedGroupTeleport() {
        return ClientPowerState.getSnapshot().abilities().stream().anyMatch(
                ability -> ability.unlocked()
                        && ability.abilityId().equals(
                        ModAbilities.TIME_MANIPULATION_ID
                )
                        && ability.level() >= 1
        );
    }

    private static boolean hasUnlockedAbility() {
        return ClientPowerState.getSnapshot().abilities().stream().anyMatch(
                ability -> ability.unlocked()
        );
    }

    private static boolean isOnCooldown(
            AbilityActionSnapshot action
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        return minecraft.level != null
                && action.cooldownEndGameTime()
                > minecraft.level.getGameTime();
    }

    private static int getTextColor(
            AbilityActionSnapshot action,
            boolean onCooldown
    ) {
        // Locked actions and actions on cooldown intentionally appear gray.
        if (!action.unlocked() || onCooldown) {
            return 0xFF777777;
        }

        return 0xFFFFD7DC;
    }

    private static String buildDisplayText(
            AbilityActionSnapshot action,
            boolean onCooldown
    ) {
        String keyName = getKeyMapping(action.slot())
                .getTranslatedKeyMessage()
                .getString();

        if (action.displayNameKey().equals(
                "hud.heroesevolved.temporal_snapshot_create"
        )) {
            keyName = Minecraft.getInstance().options.keyShift
                    .getTranslatedKeyMessage().getString() + " + " + keyName;
        }

        String actionName = Component.translatable(
                action.displayNameKey()
        ).getString();

        if (!action.unlocked()) {
            return keyName + "  " + actionName + " [Locked]";
        }

        if (onCooldown) {
            long remainingTicks = action.cooldownEndGameTime()
                    - Minecraft.getInstance().level.getGameTime();

            long remainingSeconds = (long) Math.ceil(remainingTicks / 20.0D);
            String remainingText = remainingSeconds >= 60
                    ? (long) Math.ceil(remainingSeconds / 60.0D) + "m"
                    : remainingSeconds + "s";

            return keyName + "  " + actionName
                    + " (" + remainingText + ")";
        }

        return keyName + "  " + actionName;
    }

    private static KeyMapping getKeyMapping(AbilitySlot slot) {
        return switch (slot) {
            case PRIMARY -> ModKeyMappings.ABILITY_1;
            case SECONDARY -> ModKeyMappings.ABILITY_2;
            case TERTIARY -> ModKeyMappings.ABILITY_3;
            case QUATERNARY -> ModKeyMappings.ABILITY_4;
            case QUINARY -> ModKeyMappings.ABILITY_5;
        };
    }
}
