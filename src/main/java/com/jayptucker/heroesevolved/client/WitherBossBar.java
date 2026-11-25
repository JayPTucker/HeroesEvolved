package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

/**
 * The Wither's own boss bar, as Bosses'Rise gives each of its bosses one built from its shapes: a bone shaft with rib
 * teeth and knuckled ends, and its three skulls over the middle. The fill, the skulls' eyes and the star burn in the
 * phase's colour (mint, violet, ember; white as it collapses), and a pale trail shows the health just lost. Only this
 * boss's bar is replaced; every other bar is drawn as vanilla draws it.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class WitherBossBar {
    private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "textures/gui/wither_bar.png"),
            EYES = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "textures/gui/wither_bar_eyes.png");
    private static final int W = 256, H = 48, CHANNEL_X = 24, CHANNEL_Y = 30, CHANNEL_W = 209, CHANNEL_H = 7;
    private static final java.util.Map<java.util.UUID, float[]> TRAIL = new java.util.HashMap<>();

    private WitherBossBar() {}

    /** Whether a boss bar belongs to the Wither: its name is one of the Wither's own. */
    public static boolean ours(net.minecraft.network.chat.Component name) {
        return name.getContents() instanceof TranslatableContents t
                && (t.getKey().equals("entity.heroesevolved.rift_wither") || t.getKey().startsWith("wither.heroesevolved."));
    }

    @SubscribeEvent public static void draw(CustomizeGuiOverlayEvent.BossEventProgress event) {
        var boss = event.getBossEvent();
        if (!ours(boss.getName())) return;
        event.setCanceled(true);
        var g = event.getGuiGraphics();
        var font = Minecraft.getInstance().font;
        int x = g.guiWidth() / 2 - W / 2, y = event.getY() + 2;
        int colour = switch (boss.getColor()) {
            case PURPLE -> 0xA18AE4;
            case RED -> 0xFF947F;
            case WHITE -> 0xE8FFF4;
            default -> 0x6EE9CF;
        };
        float progress = Mth.clamp(boss.getProgress(), 0, 1);
        // A pale trail of the health just lost, easing down after the bar.
        float[] trail = TRAIL.computeIfAbsent(boss.getId(), k -> new float[]{progress});
        trail[0] = progress > trail[0] ? progress : Mth.lerp(.04F, trail[0], progress);
        int name = font.width(boss.getName());
        g.drawString(font, boss.getName(), g.guiWidth() / 2 - name / 2, event.getY() - 9, 0xFFFFFF);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.blit(FRAME, x, y, 0, 0, W, H, W, H);
        int full = Math.round(CHANNEL_W * progress), lost = Math.round(CHANNEL_W * trail[0]);
        int cx = x + CHANNEL_X, cy = y + CHANNEL_Y;
        if (lost > full) g.fill(cx + full, cy, cx + lost, cy + CHANNEL_H, 0xFFE8E2D0);
        if (full > 0) {
            g.fill(cx, cy, cx + full, cy + CHANNEL_H, 0xFF000000 | colour);
            g.fill(cx, cy, cx + full, cy + 2, 0xFF000000 | mix(colour, 0xFFFFFF, .45F));
            g.fill(cx, cy + CHANNEL_H - 2, cx + full, cy + CHANNEL_H, 0xFF000000 | mix(colour, 0x000000, .35F));
        }
        // Notches every tenth, as vanilla's notched bar has.
        for (int i = 1; i < 10; i++) g.fill(cx + CHANNEL_W * i / 10, cy, cx + CHANNEL_W * i / 10 + 1, cy + CHANNEL_H, 0x66000000);
        float pulse = .8F + .2F * Mth.sin((Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime()) * .2F);
        g.setColor(((colour >> 16) & 255) / 255F * pulse, ((colour >> 8) & 255) / 255F * pulse, (colour & 255) / 255F * pulse, 1);
        g.blit(EYES, x, y, 0, 0, W, H, W, H);
        g.setColor(1, 1, 1, 1);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        event.setIncrement(H + 12);
    }

    private static int mix(int a, int b, float k) {
        int r = (int) Mth.lerp(k, (a >> 16) & 255, (b >> 16) & 255), gr = (int) Mth.lerp(k, (a >> 8) & 255, (b >> 8) & 255), bl = (int) Mth.lerp(k, a & 255, b & 255);
        return r << 16 | gr << 8 | bl;
    }

    @SubscribeEvent public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) { TRAIL.clear(); }

}
