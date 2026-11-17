package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** The title reveal and the layout of the built-in Minecraft menu buttons. */
public final class TitleMenu {
    public static final long CONTROLS_REVEAL_MS = 5750;
    private static final ResourceLocation ODESSA = texture("odessa");
    private static final ResourceLocation WORDMARK = texture("wordmark");
    private static final int SKY_WIDTH = 1672;
    private static final int SKY_HEIGHT = 941;
    private static final int ECLIPSE_LETTER = 52;
    private static final int ROW_STEP = 23;
    private static final int[] LETTER_CENTERS = {-252, -154, -55, 155, 252};

    private TitleMenu() {}

    static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "textures/gui/title/" + name + ".png");
    }

    public static void render(GuiGraphics graphics, int width, int height, boolean demo, float age, float motion) {
        graphics.fill(0, 0, width, height, 0xFF000000);
        graphics.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int menuY = menuTop(height, demo);
        float titleScale = Math.min(width * 0.70F / 560, menuY / 191.0F);
        TitleStars.draw(graphics, width, height, age, motion);
        sky(graphics, width, height, age, motion);
        TitleAurora.draw(graphics, width, height, age, motion);
        wordmark(graphics, width, menuY, titleScale, age, motion);
        TitleEclipse.draw(graphics, width, height, width / 2.0F + ECLIPSE_LETTER * titleScale,
                menuY * 0.55F, titleScale, age, motion);
        graphics.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    private static void sky(GuiGraphics graphics, int width, int height, float age, float motion) {
        float drift = age * motion;
        float distance = 1.035F + 0.075F * (1 - ease(age, TitleEclipse.DOCK_START, 6.6F)) * motion;
        float cover = Math.max((float) width / SKY_WIDTH, (float) height / SKY_HEIGHT) * distance;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate((width - SKY_WIDTH * cover) / 2 + width * 0.012F * Mth.sin(drift * 0.18F),
                (height - SKY_HEIGHT * cover) / 2, 0);
        pose.scale(cover, cover, 1);
        tint(graphics, ease(age, 4.05F, 6.25F));
        graphics.blit(ODESSA, 0, 0, 0, 0, SKY_WIDTH, SKY_HEIGHT, SKY_WIDTH, SKY_HEIGHT);
        pose.popPose();
    }

    private static void wordmark(GuiGraphics graphics, int width, int menuY, float scale, float age, float motion) {
        if (scale <= 0 || age < 4.35F) return;

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(width / 2.0F, menuY * 0.55F, 0);
        pose.scale(scale, scale, 1);

        for (int i = 0; i < LETTER_CENTERS.length; i++) {
            // The letters nearest the eclipse resolve first.
            int distance = Math.min(Math.abs(i - 2), Math.abs(i - 3));
            float delay = distance * 0.09F;
            float reveal = ease(age, 4.35F + delay, 5.65F + delay);
            int center = LETTER_CENTERS[i];
            int slide = Math.round((center < 0 ? -15 : 15) * (1 - reveal) * motion);
            int x = center - 64 + slide;
            graphics.setColor(0.025F, 0.06F, 0.09F, reveal * 0.65F);
            graphics.blit(WORDMARK, x, -60, 128, 128, i * 256, 0, 256, 256, 1280, 384);
            tint(graphics, reveal);
            graphics.blit(WORDMARK, x, -62, 128, 128, i * 256, 0, 256, 256, 1280, 384);
        }

        tint(graphics, ease(age, 5.1F, 6.05F));
        graphics.blit(WORDMARK, -192, 34, 384, 64, 0, 256, 768, 128, 1280, 384);
        pose.popPose();
    }

    static void tint(GuiGraphics graphics, float alpha) {
        graphics.setColor(1, 1, 1, alpha);
    }

    static float ease(float age, float from, float to) {
        float t = Mth.clamp((age - from) / (to - from), 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static void arrange(TitleScreen screen, boolean demo) {
        int width = Math.min(216, screen.width - 16);
        int x = (screen.width - width) / 2;
        int half = (width - 4) / 2;
        int y = menuTop(screen.height, demo);
        int bottom = y + (demo ? 2 : 3) * ROW_STEP;
        int options = (width - 52) / 2;
        for (GuiEventListener listener : screen.children()) {
            if (!(listener instanceof AbstractButton button)) continue;
            switch (key(button)) {
                case "menu.singleplayer", "menu.playdemo" -> place(button, x, y, width);
                case "menu.multiplayer", "menu.resetdemo" -> place(button, x, y + ROW_STEP, width);
                case "menu.online" -> place(button, x, y + 2 * ROW_STEP, half);
                case "fml.menu.mods" -> place(button, x + width - half, y + 2 * ROW_STEP, half);
                case "options.language" -> place(button, x, bottom, 20);
                case "menu.options" -> place(button, x + 24, bottom, options);
                case "menu.quit" -> place(button, x + width - 24 - options, bottom, options);
                case "options.accessibility" -> place(button, x + width - 20, bottom, 20);
                default -> {}
            }
        }
    }

    private static int menuTop(int height, boolean demo) {
        return Math.max(0, Math.min(Math.round(height * 0.53F), height - (demo ? 96 : 119)));
    }

    private static void place(AbstractButton button, int x, int y, int width) {
        button.setPosition(x, y);
        button.setWidth(width);
    }

    private static String key(AbstractButton button) {
        return button.getMessage().getContents() instanceof TranslatableContents translated ? translated.getKey() : "";
    }

    public static boolean drawButton(GuiGraphics graphics, Font font, AbstractButton button, float opacity) {
        String key = key(button);
        boolean primary = key.equals("menu.singleplayer") || key.equals("menu.playdemo");
        switch (key) {
            case "menu.singleplayer", "menu.playdemo", "menu.multiplayer", "menu.resetdemo", "menu.online",
                    "fml.menu.mods", "options.language", "menu.options", "menu.quit", "options.accessibility" -> {}
            default -> { return false; }
        }

        boolean focused = button.active && button.isHoveredOrFocused();
        int background = focused ? 0xEE233B4B : 0xD0081520;
        int border = focused ? 0xFFE8D6AE : primary ? 0xC799AEB4 : 0x88577383;
        int text = button.active ? 0xFFF0F8FF : 0xFF9AA8B3;
        int x = button.getX(), y = button.getY();
        int right = x + button.getWidth(), bottom = y + button.getHeight();
        graphics.fill(x, y, right, bottom, fade(background, opacity));
        graphics.renderOutline(x, y, button.getWidth(), button.getHeight(), fade(border, opacity));
        button.renderString(graphics, font, fade(text, opacity));
        return true;
    }

    private static int fade(int argb, float opacity) {
        return (argb & 0xFFFFFF) | (Mth.ceil((argb >>> 24) * opacity) << 24);
    }
}
