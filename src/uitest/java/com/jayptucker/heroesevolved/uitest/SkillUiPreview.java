package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.gui.screen.SkillTreeScreen;
import com.jayptucker.heroesevolved.network.ClientSkillTreeState;
import com.jayptucker.heroesevolved.network.SkillTreeStatePayload;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

/** Render-only fixture; point purchases and networking are covered by GameTests. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class SkillUiPreview {
    private static boolean shown;
    private static int ticks, frames;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (Boolean.getBoolean("heroes.preview.afterimages") || Boolean.getBoolean("heroes.preview.unwritten")) return;
        Minecraft mc = Minecraft.getInstance();
        if (shown || ++ticks < 40 || mc.getOverlay() != null || mc.screen == null) return;
        shown = true;
        ClientSkillTreeState.update(new SkillTreeStatePayload("sonic_resonance", true, 5,
                new SkillTreeData.Build(5, Set.of(), Map.of())));
        mc.options.guiScale().set(2);
        mc.resizeDisplay();
        mc.setScreen(new SkillTreeScreen(new TitleScreen()));
    }

    @SubscribeEvent public static void rendered(RenderFrameEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!shown || (!(mc.screen instanceof SkillTreeScreen) && !(mc.screen instanceof PaintingScreen))) return;
        frames++;
        if (frames == 3) capture(mc, "skill-tree-scale2.png");
        if (frames == 4) for (var child : mc.screen.children()) {
            if (child instanceof Button button && button.getMessage().getString().equals("Focused Shriek")) {
                button.onPress(); break;
            }
        }
        if (frames == 6) capture(mc, "skill-tree-locked.png");
        if (frames == 7) { mc.options.guiScale().set(3); mc.resizeDisplay(); }
        if (frames == 10) capture(mc, "skill-tree-scale3.png");
        if (frames == 11) {
            ClientSkillTreeState.update(new SkillTreeStatePayload("sonic_resonance", true, 5,
                    new SkillTreeData.Build(5, Set.of("sonic_resonance_0_0", "sonic_resonance_0_1"), Map.of())));
            mc.screen.tick();
        }
        if (frames == 14) capture(mc, "skill-tree-learned.png");
        if (frames == 18) {
            ClientSkillTreeState.update(new SkillTreeStatePayload("cryokinesis", true, 5,
                    new SkillTreeData.Build(5, Set.of("cryokinesis_0_0"), Map.of(), 4)));
            mc.screen.tick();
            for (var child : mc.screen.children()) if (child instanceof Button b && b.getMessage().getString().equals(">")) { b.onPress(); break; }
        }
        if (frames == 22) capture(mc, "unwritten-tree-page2-scale3.png");
        if (frames == 23) { mc.options.guiScale().set(2); mc.resizeDisplay(); }
        if (frames == 26) capture(mc, "unwritten-tree-page2-scale2.png");
        if (frames == 27) mc.setScreen(new PaintingScreen());
        if (frames == 32) capture(mc, "unwritten-paintings.png");
        if (frames == 36) mc.stop();
    }

    private static final class PaintingScreen extends net.minecraft.client.gui.screens.Screen {
        private net.minecraft.resources.ResourceLocation before, after;
        PaintingScreen() { super(net.minecraft.network.chat.Component.literal("The Unfinished Hour")); }
        private net.minecraft.resources.ResourceLocation texture(String name, byte[] pixels) {
            var image = new com.mojang.blaze3d.platform.NativeImage(128, 128, false);
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) image.setPixelRGBA(x, y, net.minecraft.world.level.material.MapColor.getColorFromPackedId(pixels[x + y * 128] & 255));
            return minecraft.getTextureManager().register(name, new net.minecraft.client.renderer.texture.DynamicTexture(image));
        }
        @Override protected void init() {
            before = texture("unwritten_before", com.jayptucker.heroesevolved.expansion.PropheticPainting.pixels(com.jayptucker.heroesevolved.expansion.FacilityData.Phase.PLANNED, false, 0));
            after = texture("unwritten_after", com.jayptucker.heroesevolved.expansion.PropheticPainting.pixels(com.jayptucker.heroesevolved.expansion.FacilityData.Phase.SAVED, true, 3));
        }
        @Override public void render(net.minecraft.client.gui.GuiGraphics g, int x, int y, float p) {
            g.fill(0, 0, width, height, 0xFF0C1420);
            int left = (width - 284) / 2, top = (height - 128) / 2;
            g.drawCenteredString(font, "THE UNFINISHED HOUR", width / 2, top - 36, 0xFFF4D18F);
            g.drawCenteredString(font, "FORETOLD", left + 64, top - 14, 0xFFE4A76E);
            g.drawCenteredString(font, "REVISED", left + 220, top - 14, 0xFF9FE3E8);
            g.blit(before, left, top, 0, 0, 128, 128, 128, 128);
            g.blit(after, left + 156, top, 0, 0, 128, 128, 128, 128);
            g.drawCenteredString(font, "Hold it. Frame it. Share it. Change it.", width / 2, top + 145, 0xFFD1D9E0);
        }
        @Override public void renderBackground(net.minecraft.client.gui.GuiGraphics g, int x, int y, float p) {}
    }
    private static void capture(Minecraft mc, String name) {
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            File dir = new File(mc.gameDirectory, "screenshots");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create UI screenshot folder");
            image.writeToFile(new File(dir, name));
            System.out.println("UI_CAPTURE " + name);
        } catch (IOException error) { throw new RuntimeException(error); }
    }
}
