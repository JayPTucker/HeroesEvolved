package com.jayptucker.heroesevolved.gui;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.AbilitySnapshot;
import com.jayptucker.heroesevolved.network.ClientOverexertionState;
import com.jayptucker.heroesevolved.network.ClientPowerState;
import com.jayptucker.heroesevolved.network.ClientStamina;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The stamina bar, above the hunger bar, in the colour of your power.
 *
 * <ul>
 *   <li>Notches every 20 stamina, so a cost can be read off the bar.</li>
 *   <li>What a move just took stays as a pale chunk that shrinks away a moment later.</li>
 *   <li>While stamina recovers, a glint runs along the fill and the rate shows on the right ({@code +4.2/s}, brighter
 *       when recovering out of combat at the calm rate). Right after a move recovery pauses, and a thin timer
 *       under the bar runs out until it starts again.</li>
 *   <li>A held power (Still World, a flight, a beam) draws moving stripes at the end of the fill and shows what it
 *       is taking ({@code -12/s}), and how many seconds that leaves.</li>
 *   <li>Under a fifth the frame pulses red; a move refused for lack of stamina flashes and shakes it.</li>
 *   <li>Full and idle, the numbers fade out and only the bar stays.</li>
 * </ul>
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class EnergyHudOverlay {
    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 5;
    private static final int NOTCH = 20;
    private static int idleTicks;
    private static float textAlpha = 1;

    private EnergyHudOverlay() {
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.FOOD_LEVEL,
                ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "energy_bar"),
                EnergyHudOverlay::render
        );
    }

    /** Runs the client's stamina forward and the fade of the numbers. */
    @EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
    public static final class Ticks {
        private Ticks() {}

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            ClientStamina.tick();
            boolean full = ClientStamina.known() && ClientStamina.stamina() >= ClientStamina.maximum() - .01F;
            boolean busy = ClientStamina.held() || ClientStamina.sinceSpend() < 60 || ClientStamina.sinceRefusal() < 60;
            idleTicks = full && !busy ? idleTicks + 1 : 0;
            float target = idleTicks > 100 ? .0F : 1F;
            textAlpha += (target - textAlpha) * .12F;
        }

        @SubscribeEvent
        public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientStamina.reset();
        }
    }

    private static void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        renderOverexertionBorder(g);
        PlayerPowerSyncPayload snapshot = ClientPowerState.getSnapshot();
        AbilitySnapshot power = snapshot.abilities().stream().filter(AbilitySnapshot::unlocked).findFirst().orElse(null);
        if (power == null) return;
        float max = ClientStamina.known() ? ClientStamina.maximum() : snapshot.maximumEnergy();
        if (max <= 0) return;
        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        float now = ClientStamina.known() ? ClientStamina.shown() : Math.min(snapshot.energy(), max);
        float trail = ClientStamina.known() ? Math.max(ClientStamina.trail(), now) : now;
        float exact = ClientStamina.known() ? ClientStamina.stamina() : now;
        long time = mc.level == null ? 0 : mc.level.getGameTime();
        float clock = time + partial;

        int x = g.guiWidth() / 2 + 10;
        int y = g.guiHeight() - 49;
        // Air bubbles and a mount's hearts sit where the bar would; step over them.
        if (mc.player.getAirSupply() < mc.player.getMaxAirSupply() || mc.player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) y -= 10;
        if (mc.player.getVehicle() instanceof net.minecraft.world.entity.LivingEntity) y -= 10;

        // A refusal shakes the bar for half a second.
        int refused = ClientStamina.sinceRefusal();
        if (refused < 10) x += Math.round(Mth.sin(refused * 2.4F) * (10 - refused) * .25F);

        int color = PowerUiColors.fill(power.abilityId().getPath());
        float fraction = Mth.clamp(now / max, 0, 1), trailFraction = Mth.clamp(trail / max, 0, 1);
        int fill = Math.round(BAR_WIDTH * fraction), ghost = Math.round(BAR_WIDTH * trailFraction);
        boolean low = exact < max * .2F;

        // Frame: dark, pulsing red when low, flashing white when a move was refused.
        int frame = PowerUiColors.FRAME;
        if (low) frame = blend(frame, 0xFFB0202A, .45F + .45F * Mth.sin(clock * .3F));
        if (refused < 12) frame = blend(frame, 0xFFFFFFFF, (12 - refused) / 12F);
        g.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, frame);
        g.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, argb(.8F, PowerUiColors.TRACK));
        // The empty part's inner shade, so the trough reads as a groove.
        g.fill(x, y, x + BAR_WIDTH, y + 1, 0x60000000);

        // What was just spent, fading out as it shrinks.
        if (ghost > fill) {
            float age = Math.min(1, ClientStamina.sinceSpend() / 30F);
            g.fill(x + fill, y, x + ghost, y + BAR_HEIGHT, argb(.85F - .35F * age, blend(0xFFFFFFFF, color, .25F)));
        }

        // The fill: the power's colour, lit along the top and shaded along the bottom.
        if (fill > 0) {
            int body = color;
            g.fill(x, y, x + fill, y + BAR_HEIGHT, body);
            g.fill(x, y, x + fill, y + 1, blend(body, 0xFFFFFFFF, .45F));
            g.fill(x, y + 1, x + fill, y + 2, blend(body, 0xFFFFFFFF, .18F));
            g.fill(x, y + BAR_HEIGHT - 1, x + fill, y + BAR_HEIGHT, blend(body, 0xFF000000, .35F));

            boolean recovering = ClientStamina.known() && ClientStamina.regen() > 0 && !ClientStamina.paused() && exact < max - .01F;
            if (recovering) {
                // A glint running along the fill: faster when calm.
                float speed = ClientStamina.calm() ? 2.2F : 1.2F;
                int glint = Math.floorMod((int) (clock * speed), BAR_WIDTH + 24) - 12;
                for (int i = -4; i <= 4; i++) {
                    int gx = glint + i;
                    if (gx < 0 || gx >= fill) continue;
                    float a = (1 - Math.abs(i) / 5F) * .5F;
                    g.fill(x + gx, y, x + gx + 1, y + BAR_HEIGHT - 1, argb(a, 0xFFFFFFFF));
                }
            }
            if (ClientStamina.held()) {
                // Stripes sliding toward the empty end: something is pouring out.
                int stripes = Math.min(fill, 14);
                int offset = Math.floorMod((int) (clock * 1.5F), 4);
                for (int sx = fill - stripes; sx < fill; sx++) {
                    if (Math.floorMod(sx - offset, 4) != 0) continue;
                    g.fill(x + sx, y, x + sx + 1, y + BAR_HEIGHT, 0x70FFF4E0);
                }
                float pulse = .55F + .45F * Mth.sin(clock * .5F);
                g.fill(x + fill - 1, y - 1, x + fill, y + BAR_HEIGHT + 1, argb(pulse, 0xFFFFE6B0));
            }
        }

        // Notches every 20 stamina.
        for (int n = NOTCH; n < max; n += NOTCH) {
            int nx = x + Math.round(BAR_WIDTH * n / max);
            g.fill(nx, y + 1, nx + 1, y + BAR_HEIGHT - 1, nx - x < fill ? 0x55000000 : 0x30FFFFFF);
        }

        // The recovery pause: a thin line under the bar that runs out until stamina comes back.
        if (ClientStamina.known() && ClientStamina.paused() && !ClientStamina.held() && exact < max) {
            int pauseWidth = Math.round(BAR_WIDTH * Math.min(1, ClientStamina.pauseTicks() / 30F));
            if (pauseWidth > 0) g.fill(x, y + BAR_HEIGHT + 1, x + pauseWidth, y + BAR_HEIGHT + 2, 0x90FFFFFF);
        }

        // Numbers: stamina on the left, the rate on the right.
        float alpha = Math.max(textAlpha, low || refused < 40 ? 1 : 0);
        if (alpha < .04F) return;
        Font font = mc.font;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y - 7, 0);
        pose.scale(.75F, .75F, 1);
        String value = ClientStamina.unlimited() ? "∞" : String.valueOf((int) Math.floor(exact + 1e-4F));
        int valueColor = low ? blend(0xFFFF8A8A, 0xFFFFFFFF, .5F + .5F * Mth.sin(clock * .3F)) : PowerUiColors.TEXT;
        String rate = null;
        int rateColor = 0;
        if (ClientStamina.known() && !ClientStamina.unlimited()) {
            float drain = ClientStamina.drain(), regen = ClientStamina.paused() ? 0 : ClientStamina.regen();
            float net = regen - drain;
            if (drain > .05F) {
                int left = net >= 0 ? -1 : (int) (exact / -net);
                rate = String.format("-%.0f/s", drain) + (left >= 0 && left < 600 ? "  " + left + "s" : "");
                rateColor = 0xFFFFB25A;
            } else if (exact < max - .01F && regen > 0) {
                rate = String.format("+%.1f/s", regen);
                rateColor = ClientStamina.calm() ? 0xFF9CF0B4 : 0xFFB8D6C0;
            }
            // While recovery is paused nothing is written: the line under the bar shows the pause.
        }
        int room = Math.round(BAR_WIDTH / .75F), rateWidth = rate == null ? 0 : font.width(rate);
        g.drawString(font, value, 0, 0, argb(alpha, valueColor), true);
        // The bar's size gives way to the rate when there isn't room for both.
        String size = " / " + (int) max;
        if (font.width(value) + font.width(size) + rateWidth + 4 <= room)
            g.drawString(font, size, font.width(value), 0, argb(alpha * .75F, 0xFF9AA0A8), true);
        if (rate != null) g.drawString(font, rate, room - rateWidth, 0, argb(alpha, rateColor), true);
        pose.popPose();
    }

    private static int argb(float alpha, int rgb) {
        return (Mth.clamp(Math.round(alpha * ((rgb >>> 24) == 0 ? 255 : (rgb >>> 24))), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int blend(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16
                | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
    }

    private static void renderOverexertionBorder(GuiGraphics guiGraphics) {
        float intensity = ClientOverexertionState.getIntensity();
        if (intensity <= 0.0F) return;
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        float time = Minecraft.getInstance().level.getGameTime();
        float pulse = .78F + .22F * (float) Math.pow(Math.max(0, Math.sin(time * .19)), 4);
        int depth = Math.max(20, Math.min(width, height) / 9);
        for (int i = 0; i < depth; i++) {
            float falloff = 1 - i / (float) depth;
            int alpha = (int) (58 * intensity * pulse * falloff * falloff);
            int color = alpha << 24 | 0x611525;
            guiGraphics.fill(i, i, width - i, i + 1, color);
            guiGraphics.fill(i, height - i - 1, width - i, height - i, color);
            guiGraphics.fill(i, i + 1, i + 1, height - i - 1, color);
            guiGraphics.fill(width - i - 1, i + 1, width - i, height - i - 1, color);
        }
    }
}
