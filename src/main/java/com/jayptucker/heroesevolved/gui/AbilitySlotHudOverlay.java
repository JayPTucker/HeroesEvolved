package com.jayptucker.heroesevolved.gui;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.jayptucker.heroesevolved.network.AbilityActionSnapshot;
import com.jayptucker.heroesevolved.network.ClientLoadout;
import com.jayptucker.heroesevolved.network.ClientPowerState;
import com.jayptucker.heroesevolved.network.LoadoutPayload;
import com.jayptucker.heroesevolved.skills.Loadout;
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

        if (ClientLoadout.known()) {
            y = drawLoadout(guiGraphics, y);
        } else {
            for (AbilityActionSnapshot action
                    : ClientPowerState.getSnapshot().actions()) {
                boolean onCooldown = isOnCooldown(action);
                guiGraphics.drawString(
                        Minecraft.getInstance().font,
                        buildDisplayText(action, onCooldown),
                        START_X,
                        y,
                        getTextColor(action, onCooldown),
                        true
                );
                y += LINE_HEIGHT;
            }
        }

        if (hasUnlockedGroupTeleport()) {
            String groupTeleportText = Minecraft.getInstance()
                    .options
                    .keyShift
                    .getTranslatedKeyMessage()
                    .getString()
                    + " + "
                    + keyName(ClientLoadout.known()
                            ? ClientLoadout.keyOf(Loadout.base(AbilitySlot.PRIMARY))
                            : 1)
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
        y = drawClarity(guiGraphics, y);
        y = drawResonance(guiGraphics, y);
        y = drawMomentum(guiGraphics, y);
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

    /**
     * Each bound key and its ability, then the wheel. Empty keys and keys
     * with no key set in Controls are left out.
     */
    private static int drawLoadout(GuiGraphics guiGraphics, int y) {
        var mc = Minecraft.getInstance();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        for (int key = 1; key <= ModKeyMappings.ABILITY_KEYS.length; key++) {
            LoadoutPayload.Entry entry = ClientLoadout.entry(ClientLoadout.onKey(key));
            if (entry == null || ModKeyMappings.ABILITY_KEYS[key - 1].isUnbound()) continue;
            String keyName = keyName(key);
            if (entry.nameKey().equals("hud.heroesevolved.temporal_snapshot_create")) {
                keyName = mc.options.keyShift.getTranslatedKeyMessage().getString() + " + " + keyName;
            }
            String text = keyName + "  " + Component.translatable(entry.nameKey()).getString()
                    + (entry.rank() > 1 ? " " + "\u2022".repeat(entry.rank() - 1) : "");
            long left = entry.cooldownEnd() - now;
            int color = 0xFFFFD7DC;
            if (!entry.unlocked()) {
                text += " [Level " + entry.unlockLevel() + "]";
                color = 0xFF777777;
            } else if (left > 0) {
                long seconds = (left + 19) / 20;
                text += " (" + (seconds >= 60 ? (seconds + 59) / 60 + "m" : seconds + "s") + ")";
                color = 0xFF777777;
            }
            guiGraphics.drawString(mc.font, text, START_X, y, color, true);
            y += LINE_HEIGHT;
        }
        if (!ModKeyMappings.ABILITY_WHEEL.isUnbound()) {
            guiGraphics.drawString(mc.font, Component.translatable("hud.heroesevolved.ability_wheel",
                    ModKeyMappings.ABILITY_WHEEL.getTranslatedKeyMessage()), START_X, y, 0xFFB89AA0, true);
            y += LINE_HEIGHT;
        }
        return y;
    }

    private static String keyName(int key) {
        return key >= 1 && key <= ModKeyMappings.ABILITY_KEYS.length
                ? ModKeyMappings.ABILITY_KEYS[key - 1].getTranslatedKeyMessage().getString()
                : Component.translatable("hud.heroesevolved.wheel_short").getString();
    }

    /** Isaac Mendez's Clarity: earned each time a foreseen harm misses, spent on painting and The Future Is Written. */
    private static int drawClarity(GuiGraphics guiGraphics, int y) {
        var mc = Minecraft.getInstance();
        boolean seer = ClientPowerState.getSnapshot().abilities().stream().anyMatch(power -> power.unlocked()
                && power.abilityId().equals(ModAbilities.PRECOGNITION_ID));
        if (mc.player == null || !seer) return y;
        int clarity = com.jayptucker.heroesevolved.network.ClientClarity.get();
        int color = clarity >= 100 ? 0xFFFFF4E0 : 0xFFF7D88D;
        guiGraphics.drawString(mc.font, Component.translatable("hud.heroesevolved.clarity", clarity), START_X, y, color, true);
        guiGraphics.fill(START_X, y + 10, START_X + 100, y + 13, 0x80403418);
        guiGraphics.fill(START_X, y + 10, START_X + clarity, y + 13, color);
        // The notch marks the most a painted ending can spend.
        guiGraphics.fill(START_X + 50, y + 9, START_X + 51, y + 14, 0xC0FFFFFF);
        return y + LINE_HEIGHT + 6;
    }

    /** Superspeed's Momentum, with notches where the body crackles, you buffet, and the sound barrier breaks. */
    private static int drawMomentum(GuiGraphics guiGraphics, int y) {
        var mc = Minecraft.getInstance();
        boolean speedster = ClientPowerState.getSnapshot().abilities().stream().anyMatch(power -> power.unlocked()
                && power.abilityId().equals(ModAbilities.SUPERSPEED_ID));
        if (mc.player == null || !speedster) return y;
        int momentum = com.jayptucker.heroesevolved.network.ClientMomentum.get();
        long time = mc.level == null ? 0 : mc.level.getGameTime();
        int color = momentum >= 100 ? ((time / 3) % 2 == 0 ? 0xFFFFF6D8 : 0xFFFFC23A) : momentum >= 50 ? 0xFFFFC23A : 0xFFFF8A1E;
        guiGraphics.drawString(mc.font, Component.translatable("hud.heroesevolved.momentum", momentum), START_X, y, color, true);
        guiGraphics.fill(START_X, y + 10, START_X + 100, y + 13, 0x80402810);
        guiGraphics.fill(START_X, y + 10, START_X + momentum, y + 13, color);
        for (int mark : new int[] {30, 50, 60}) guiGraphics.fill(START_X + mark, y + 9, START_X + mark + 1, y + 14, 0xC0FFFFFF);
        return y + LINE_HEIGHT + 6;
    }

    /** Sonic Resonance: built by sonic hits and loud sounds, spent by a harmonic Note and by Crescendo. */
    private static int drawResonance(GuiGraphics guiGraphics, int y) {
        var mc = Minecraft.getInstance();
        boolean singer = ClientPowerState.getSnapshot().abilities().stream().anyMatch(power -> power.unlocked()
                && power.abilityId().equals(ModAbilities.SONIC_RESONANCE_ID));
        if (mc.player == null || !singer) return y;
        int resonance = com.jayptucker.heroesevolved.network.ClientResonance.get();
        guiGraphics.drawString(mc.font, Component.translatable("hud.heroesevolved.resonance", resonance), START_X, y, 0xFFE0B8FF, true);
        guiGraphics.fill(START_X, y + 10, START_X + 100, y + 13, 0x80302040);
        // The bar runs through every colour, the way Emma sees a rising note.
        long time = mc.level == null ? 0 : mc.level.getGameTime();
        for (int x = 0; x < resonance; x++)
            guiGraphics.fill(START_X + x, y + 10, START_X + x + 1, y + 13,
                    0xFF000000 | net.minecraft.util.Mth.hsvToRgb(((x / 100F) + time / 200F) % 1F, 0.6F, 1F));
        // The notch marks what a harmonic Note costs.
        guiGraphics.fill(START_X + 30, y + 9, START_X + 31, y + 14, 0xC0FFFFFF);
        return y + LINE_HEIGHT + 6;
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
