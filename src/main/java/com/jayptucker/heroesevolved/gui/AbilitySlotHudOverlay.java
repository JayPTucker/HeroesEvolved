package com.jayptucker.heroesevolved.gui;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.ClientHydroState;
import com.jayptucker.heroesevolved.client.MorphClient;
import com.jayptucker.heroesevolved.client.OpticalClient;
import com.jayptucker.heroesevolved.client.PyroFlightClient;
import com.jayptucker.heroesevolved.client.effects.SpeedCyclone;
import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.jayptucker.heroesevolved.network.ClientFlightVisualState;
import com.jayptucker.heroesevolved.network.ClientDensity;
import com.jayptucker.heroesevolved.network.ClientLoadout;
import com.jayptucker.heroesevolved.network.ClientMomentum;
import com.jayptucker.heroesevolved.network.ClientPowerState;
import com.jayptucker.heroesevolved.network.ClientStamina;
import com.jayptucker.heroesevolved.network.LoadoutPayload;
import com.jayptucker.heroesevolved.progression.PowerLevels;
import com.jayptucker.heroesevolved.time.TemporalSnapshotService;
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

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(
        modid = HeroesEvolved.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD
)
public final class AbilitySlotHudOverlay {
    private static final int START_X = 8;
    private static final int START_Y = 8;
    private static final int LINE_HEIGHT = 12;
    private static final int TILE = 24, TILE_HEIGHT = 32, GAP = 3;

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
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        // Without a power the keys stay hidden, but rings that aren't a power's own (faded, burnt) still show.
        int y = hasUnlockedAbility() ? drawLoadout(guiGraphics) : START_Y;
        // Every power's readout is a ring in one compact row (gui/HudGauges); each shows only while it has something to say.
        List<HudGauges.Gauge> gauges = new ArrayList<>();
        radiationCore(gauges);
        resonance(gauges);
        if (holds(ModAbilities.SUPERSPEED_ID)) HudGauges.speed(gauges);
        density(gauges);
        strain(gauges);
        regrowth(gauges);
        flight(gauges);
        com.jayptucker.heroesevolved.client.PyroFlightClient.gauges(gauges);
        com.jayptucker.heroesevolved.client.OpticalClient.gauges(gauges);
        com.jayptucker.heroesevolved.client.TelepathyClient.gauges(gauges);
        com.jayptucker.heroesevolved.client.ClientAmp.gauges(gauges);
        HudGauges.burn(gauges);
        y = HudGauges.row(guiGraphics, START_X, y + 2, gauges);
        // Words stay only for what you must do right now.
        y = downedHint(guiGraphics, y);
        flightHint(guiGraphics, y);
    }

    private static int drawLoadout(GuiGraphics graphics) {
        var mc = Minecraft.getInstance();
        var loadout = ClientLoadout.get();
        if (loadout.power().isEmpty()) return START_Y;
        var colors = PowerUiColors.forPower(loadout.power());
        long now = mc.level.getGameTime();
        int x = START_X, y = START_Y;
        List<Component> modifiers = new ArrayList<>(2);
        boolean sneak = mc.options.keyShift.isDown();
        for (int index = 0; index < ModKeyMappings.ABILITY_KEYS.length; index++) {
            KeyMapping mapping = ModKeyMappings.ABILITY_KEYS[index];
            LoadoutPayload.Entry entry = ClientLoadout.entry(ClientLoadout.onKey(index + 1));
            if (entry == null || mapping.isUnbound()) continue;
            if (x + TILE > graphics.guiWidth() - START_X) { x = START_X; y += TILE_HEIGHT + GAP; }
            long end = entry.cooldownEnd();
            int cooldown = entry.cooldownTicks();
            boolean snapshot = entry.path().equals("temporal_snapshot") && loadout.power().equals("time_manipulation")
                    && !mc.level.dimension().equals(TemporalSnapshotService.TEMPORAL_DIMENSION);
            boolean groupFold = entry.path().equals("time_blink") && loadout.power().equals("time_manipulation")
                    && loadout.level() >= PowerLevels.levelForTier(2);
            String key = mapping.getTranslatedKeyMessage().getString();
            if (sneak && entry.unlocked()) {
                if (snapshot) {
                    end = entry.alternateCooldownEnd();
                    cooldown = entry.alternateCooldownTicks();
                    modifiers.add(Component.translatable("hud.heroesevolved.ability_modifier", key,
                            Component.translatable("hud.heroesevolved.temporal_snapshot_create")));
                } else if (groupFold) {
                    modifiers.add(Component.translatable("hud.heroesevolved.ability_modifier", key,
                            Component.translatable("hud.heroesevolved.group_teleport")));
                }
            }
            drawTile(graphics, loadout.power(), colors, entry, key, x, y, end - now, cooldown, false, snapshot || groupFold);
            x += TILE + GAP;
        }

        KeyMapping wheel = ModKeyMappings.ABILITY_WHEEL;
        if (!wheel.isUnbound()) {
            if (x != START_X) x += 3;
            if (x + TILE > graphics.guiWidth() - START_X) { x = START_X; y += TILE_HEIGHT + GAP; }
            var quick = ClientLoadout.quickCast();
            drawTile(graphics, loadout.power(), colors, quick, wheel.getTranslatedKeyMessage().getString(), x, y,
                    quick == null ? 0 : quick.cooldownEnd() - now, quick == null ? 0 : quick.cooldownTicks(),
                    true, false);
            x += TILE;
        }
        if (x == START_X) return y;
        y += TILE_HEIGHT + GAP;
        for (Component modifier : modifiers) {
            graphics.drawString(mc.font, modifier, START_X, y, colors.highlight(), true);
            y += LINE_HEIGHT;
        }
        return y + 3;
    }

    private static void drawTile(GuiGraphics g, String power, PowerUiColors.Palette colors, LoadoutPayload.Entry entry, String key,
                                 int x, int y, long left, int duration, boolean wheel, boolean alternate) {
        var mc = Minecraft.getInstance();
        boolean unlocked = entry == null || entry.unlocked();
        boolean active = entry != null && unlocked && active(entry.path(), mc.player.getId());
        int edge = active ? colors.highlight() : !unlocked || left > 0 ? PowerUiColors.MUTED_EDGE
                : wheel ? colors.accent() : colors.edge();
        g.fill(x, y, x + TILE, y + TILE_HEIGHT, active ? colors.selected() : colors.surface());
        g.renderOutline(x, y, TILE, TILE_HEIGHT, edge);
        g.fill(x + 1, y + 10, x + TILE - 1, y + 11, PowerUiColors.FRAME);

        var font = mc.font;
        int keyWidth = font.width(key);
        float scale = Math.clamp((TILE - 4F) / Math.max(1, keyWidth), .64F, 1F);
        String label = keyWidth * scale > TILE - 4 ? font.plainSubstrByWidth(key, (int) ((TILE - 8) / scale)) + "…" : key;
        g.pose().pushPose();
        g.pose().translate(x + TILE / 2F, y + 2, 0);
        g.pose().scale(scale, scale, 1);
        g.drawCenteredString(font, label, 0, 0, unlocked ? PowerUiColors.TEXT : PowerUiColors.MUTED_TEXT);
        g.pose().popPose();

        if (entry == null) {
            g.renderOutline(x + 7, y + 16, 10, 10, edge);
            g.fill(x + 11, y + 20, x + 13, y + 22, edge);
            return;
        }
        AbilityIcons.draw(g, power, entry.path(), x + 2, y + 11, 20);
        if (!unlocked) {
            g.fill(x + 1, y + 11, x + TILE - 1, y + TILE_HEIGHT - 1, 0xA00B0D12);
            g.renderOutline(x + 15, y + 21, 5, 5, PowerUiColors.MUTED_TEXT);
            g.fill(x + 14, y + 24, x + 21, y + 29, PowerUiColors.MUTED_TEXT);
        } else if (left > 0) {
            int sweep = (int) Math.ceil(20 * Math.min(1.0, left / (double) Math.max(1, duration)));
            g.fill(x + 1, y + TILE_HEIGHT - 1 - sweep, x + TILE - 1, y + TILE_HEIGHT - 1, 0xB00B0D12);
            long seconds = (left + 19) / 20;
            String countdown = seconds >= 60 ? (seconds + 59) / 60 + "m" : seconds + "s";
            g.drawCenteredString(font, countdown, x + TILE / 2, y + 22, PowerUiColors.TEXT);
        }
        if (active) g.fill(x + 19, y + 13, x + 22, y + 16, colors.highlight());
        if (alternate && unlocked && left <= 0) {
            g.fill(x + 18, y + 26, x + 23, y + 27, colors.accent());
            g.fill(x + 20, y + 24, x + 21, y + 29, colors.accent());
        }
    }

    /** Use the power's live state so tapping a key or failing a cast never falsely marks it active. */
    private static boolean active(String action, int entity) {
        return switch (action) {
            case "flight_toggle" -> ClientFlightVisualState.isFlightEnabled(entity);
            case "flight_launch" -> ClientFlightVisualState.isCharging(entity) || ClientFlightVisualState.isBursting(entity);
            case "flight_dive_bomb" -> ClientFlightVisualState.isDiving(entity);
            case "flight_cyclone" -> ClientFlightVisualState.isCycloneActive(entity);
            case "pyro_backdraft" -> PyroFlightClient.active();
            case "speed_cyclone" -> SpeedCyclone.running(entity);
            case "time_stop" -> ClientStamina.stillWorld();
            case "time_slow" -> ClientStamina.betweenSeconds();
            case "invisibility_fade" -> OpticalClient.faded();
            case "phasing_shift" -> ClientDensity.get() != 0;
            case "hydro_octopus_form" -> ClientHydroState.octopus(entity);
            case "hydro_wave_rider" -> ClientHydroState.riding(entity);
            case "shapeshift_morph" -> MorphClient.form(entity) != null;
            default -> false;
        };
    }

    private static boolean holds(net.minecraft.resources.ResourceLocation power) {
        return ClientPowerState.getSnapshot().abilities().stream().anyMatch(ability -> ability.unlocked() && ability.abilityId().equals(power));
    }

    /** Induced Radioactivity's Core: notches at Hot, Glowing and Critical; orange when Hot, red when Critical, flashing at 100. */
    private static void radiationCore(List<HudGauges.Gauge> gauges) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !holds(ModAbilities.RADIATION_ID)) return;
        int core = Math.max(0, com.jayptucker.heroesevolved.client.effects.RadiationVisuals.stress(mc.player.getId()));
        boolean overdrive = com.jayptucker.heroesevolved.client.effects.RadiationVisuals.overdrive(mc.player.getId());
        String tier = overdrive ? "OVERDRIVE" : core >= 100 ? "VENT NOW!" : core >= 80 ? "CRITICAL" : core >= 60 ? "GLOWING" : core >= 40 ? "HOT" : "CORE";
        int color = overdrive ? ((mc.level.getGameTime() / 6) % 2 == 0 ? 0xFFFFD040 : 0xFFFFF0A0)
                : core >= 100 ? ((mc.level.getGameTime() / 4) % 2 == 0 ? 0xFFFFFFFF : 0xFFFF4040)
                : core >= 80 ? 0xFFFF5A3A : core >= 40 ? 0xFFFFA040 : 0xFFB8E04A;
        gauges.add(HudGauges.Gauge.ring(core / 100F, color).middle(Integer.toString(core))
                .label(tier, core >= 40 || overdrive ? color : HudGauges.LABEL).notches(0.4F, 0.6F, 0.8F));
    }

    /** Sonic Resonance: through every colour, the way Emma sees a rising note; the notch marks a harmonic Note's cost. */
    private static void resonance(List<HudGauges.Gauge> gauges) {
        if (Minecraft.getInstance().player == null || !holds(ModAbilities.SONIC_RESONANCE_ID)) return;
        int resonance = com.jayptucker.heroesevolved.network.ClientResonance.get();
        gauges.add(HudGauges.Gauge.ring(resonance / 100F, 0xFFE0B8FF).rainbow().middle(Integer.toString(resonance)).label("RESONANCE").notches(0.3F));
    }

    /**
     * D.L. Hawkins's Density: a two-way dial, Ghost filling anticlockwise from the top and Dense clockwise, a notch at
     * each setting. Underground, Breath runs out beside it.
     */
    private static void density(List<HudGauges.Gauge> gauges) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !holds(ModAbilities.PHASING_ID)) return;
        int density = com.jayptucker.heroesevolved.network.ClientDensity.get();
        int notch = Math.round(density / 50F);
        int color = density < 0 ? 0xFF7FE8D8 : density > 0 ? 0xFFB4B0A8 : 0xFFE6ECEA;
        String setting = Component.translatable("density.heroesevolved.notch." + (notch + 2)).getString().toUpperCase(java.util.Locale.ROOT);
        gauges.add(HudGauges.Gauge.dial(density / 100F, color).middle(density > 0 ? "+" + density : Integer.toString(density))
                .label(setting).notches(-0.25F, 0, 0.25F, 0.5F));
        int breath = com.jayptucker.heroesevolved.network.ClientDensity.breath();
        if (breath >= 0) {
            int air = breath > 25 || HudGauges.flash() ? 0xFF8FD8FF : 0xFFFF6A6A;
            gauges.add(HudGauges.Gauge.ring(breath / 100F, air).middle(Integer.toString(breath)).label("BREATH"));
        }
    }

    /** Daisy Johnson's Strain: blue in control, amber past 50, flickering red past 80; while her arms are fractured it says so. */
    private static void strain(List<HudGauges.Gauge> gauges) {
        if (Minecraft.getInstance().player == null || !holds(ModAbilities.QUAKE_ID)) return;
        int strain = com.jayptucker.heroesevolved.network.ClientStrain.get();
        if (com.jayptucker.heroesevolved.network.ClientStrain.fractured()) {
            gauges.add(HudGauges.Gauge.ring(1, 0xFFFF4040).middle("!").label("FRACTURED", 0xFFFF4040));
            return;
        }
        int color = strain >= 80 ? (HudGauges.flash() ? 0xFFFF5A5A : 0xFFFFB0A0) : strain >= 50 ? 0xFFFFC070 : 0xFF9FD8FF;
        gauges.add(HudGauges.Gauge.ring(strain / 100F, color).middle(Integer.toString(strain)).label("STRAIN").notches(0.5F, 0.8F));
    }

    /**
     * Claire Bennet's Regrowth (the notch marks a Second Life's cost), and how long she'd be down if killed; once down,
     * the time until she's up, filling as she holds the give-up key.
     */
    private static void regrowth(List<HudGauges.Gauge> gauges) {
        if (Minecraft.getInstance().player == null || !holds(ModAbilities.REGENERATION_ID)) return;
        int regrowth = com.jayptucker.heroesevolved.network.ClientRegenerationState.regrowth();
        int color = regrowth >= 50 ? 0xFFFF5A64 : 0xFFD0303C;
        gauges.add(HudGauges.Gauge.ring(regrowth / 100F, color).middle(Integer.toString(regrowth)).label("REGROWTH").notches(0.5F));
        long down = com.jayptucker.heroesevolved.network.ClientRegenerationState.downedRemaining();
        if (down > 0) {
            int giveUp = com.jayptucker.heroesevolved.network.ClientRegenerationState.giveUp();
            int red = HudGauges.flash() ? 0xFFFF6A6A : 0xFFB03030;
            gauges.add(HudGauges.Gauge.ring(giveUp > 0 ? giveUp / 60F : 0, 0xFFFFD9A0).middle(HudGauges.clock(down), 0xFFFF6A6A).label("DOWN", red));
        } else {
            int recovery = com.jayptucker.heroesevolved.network.ClientRegenerationState.recoveryTicks();
            gauges.add(HudGauges.Gauge.ring(0, 0xFFFFD9A0).middle(HudGauges.clock(recovery), 0xFFFFD9A0).label("DOWN FOR"));
        }
    }

    /** Down on the ground and allowed to give up: how. */
    private static int downedHint(GuiGraphics guiGraphics, int y) {
        var mc = Minecraft.getInstance();
        if (!holds(ModAbilities.REGENERATION_ID) || com.jayptucker.heroesevolved.network.ClientRegenerationState.downedRemaining() <= 0
                || com.jayptucker.heroesevolved.network.ClientRegenerationState.giveUp() < 0) return y;
        guiGraphics.drawString(mc.font, Component.translatable("hud.heroesevolved.give_up", mc.options.keyShift.getTranslatedKeyMessage()),
                START_X, y, 0xFFB89A9A, true);
        return y + LINE_HEIGHT;
    }

    /** Flight: Momentum while in the air, and pips for the wheel's speed setting; the label names a maneuver under way. */
    private static void flight(List<HudGauges.Gauge> gauges) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !hasFlightSpeedControl()) return;
        int id = mc.player.getId();
        int tier = ClientPowerState.getSnapshot().abilities().stream().filter(a -> a.unlocked() && a.abilityId().equals(ModAbilities.FLIGHT_ID))
                .mapToInt(a -> a.level()).findFirst().orElse(1);
        int step = ClientFlightVisualState.speedStep(id), steps = com.jayptucker.heroesevolved.flight.FlightSpeedControl.maximumStep(tier) + 1;
        boolean flying = ClientFlightVisualState.isFlightEnabled(id) || ClientFlightVisualState.isDiving(id);
        int momentum = flying ? ClientFlightVisualState.momentum(id) : 0;
        int color = momentum >= 100 ? 0xFFFFFFFF : 0xFF8EDAF0;
        String maneuver = !flying ? null : ClientFlightVisualState.isCharging(id) ? "COILING"
                : ClientFlightVisualState.isDiving(id) ? "DIVING" : ClientFlightVisualState.isCycloneActive(id) ? "TEMPEST" : null;
        gauges.add(HudGauges.Gauge.ring(momentum / 100F, color).middle(flying ? Integer.toString(momentum) : "")
                .label(maneuver != null ? maneuver : "FLIGHT " + (step + 1), maneuver != null ? 0xFFCBEFF8 : HudGauges.LABEL)
                .pips(steps, step + 1, 0xFF8EDAF0));
    }

    /** A flight maneuver under way: how to steer or release it. */
    private static void flightHint(GuiGraphics guiGraphics, int y) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !hasFlightSpeedControl()) return;
        int id = mc.player.getId();
        if (!ClientFlightVisualState.isFlightEnabled(id) && !ClientFlightVisualState.isDiving(id)) return;
        String maneuver = ClientFlightVisualState.isCharging(id) ? "flight_charging"
                : ClientFlightVisualState.isDiving(id) ? "flight_diving"
                : ClientFlightVisualState.isCycloneActive(id) ? "flight_tempest" : null;
        if (maneuver != null) guiGraphics.drawString(mc.font, Component.translatable("hud.heroesevolved." + maneuver), START_X, y, 0xFFCBEFF8, true);
    }

    private static boolean hasFlightSpeedControl() {
        return ClientPowerState.getSnapshot().abilities().stream().anyMatch(
                ability -> ability.unlocked()
                        && ability.abilityId().equals(ModAbilities.FLIGHT_ID)
                        && ability.level() >= 1
        );
    }

    private static boolean hasUnlockedAbility() {
        return ClientPowerState.getSnapshot().abilities().stream().anyMatch(
                ability -> ability.unlocked()
        );
    }
}
