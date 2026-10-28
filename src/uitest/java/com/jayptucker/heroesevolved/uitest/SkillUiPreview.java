package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.gui.screen.PowersScreen;
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
        if (!Boolean.getBoolean("heroes.preview.skills")) return;
        Minecraft mc = Minecraft.getInstance();
        if (shown || ++ticks < 40 || mc.getOverlay() != null || mc.screen == null) return;
        shown = true;
        mc.options.guiScale().set(2);
        mc.resizeDisplay();
        mc.setScreen(new PaintingScreen());
    }

    /** The Abilities page for a level-4 Pyrokinetic with these skills learned and these keys assigned. */
    private static void abilities(Minecraft mc, Set<String> learned, Map<String, String> slots, String select) {
        var id = com.jayptucker.heroesevolved.ability.registry.ModAbilities.PYROKINESIS_ID;
        var actions = new java.util.ArrayList<com.jayptucker.heroesevolved.network.AbilityActionSnapshot>();
        var ability = com.jayptucker.heroesevolved.ability.registry.AbilityRegistry.ABILITIES.get(id);
        for (var slot : com.jayptucker.heroesevolved.ability.AbilitySlot.values()) {
            var action = ability.actions().get(slot);
            String override = slots.get(slot.name());
            String key = override != null ? "action.heroesevolved." + override : action.definition().displayNameKey();
            actions.add(new com.jayptucker.heroesevolved.network.AbilityActionSnapshot(slot, key, action.definition().unlockLevel() <= 4, 0,
                    action.definition().unlockLevel(), action.energyCost(4), action.cooldownTicks(4)));
        }
        com.jayptucker.heroesevolved.network.ClientPowerState.update(new com.jayptucker.heroesevolved.network.PlayerPowerSyncPayload(72, 140, 6,
                java.util.List.of(new com.jayptucker.heroesevolved.network.AbilitySnapshot(id, true, 4, 5200, 4000, 7000, 5)), actions));
        ClientSkillTreeState.update(new SkillTreeStatePayload("pyrokinesis", true, 4, new SkillTreeData.Build(4, learned, slots)));
        if (!(mc.screen instanceof PowersScreen)) mc.setScreen(new PowersScreen());
        if (mc.screen instanceof PowersScreen screen) screen.select(select);
    }

    @SubscribeEvent public static void rendered(RenderFrameEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!shown || !(mc.screen instanceof PaintingScreen) && !(mc.screen instanceof PowersScreen)) return;
        frames++;
        if (frames == 5) capture(mc, "unwritten-paintings.png");
        // Nothing learned: the roots of all three branches are ready and pulse.
        if (frames == 6) abilities(mc, Set.of(), Map.of(), "pyrokinesis_0_0");
        if (frames == 10) capture(mc, "abilities-fresh.png");
        // One branch partly learned down its technique path, and the technique on a key.
        if (frames == 11) abilities(mc, Set.of("pyrokinesis_1_0", "pyrokinesis_blue_lance", "pyrokinesis_white_heat"), Map.of("QUATERNARY", "pyrokinesis_blue_lance"), "pyrokinesis_blue_lance");
        if (frames == 15) capture(mc, "abilities-one-branch.png");
        // A wider build: both paths of one branch and the roots of the others.
        if (frames == 16) abilities(mc, Set.of("pyrokinesis_0_0", "pyrokinesis_0_1", "pyrokinesis_ember_mine", "pyrokinesis_1_0", "pyrokinesis_blue_lance", "pyrokinesis_2_0"),
                Map.of("QUATERNARY", "pyrokinesis_blue_lance"), "pyrokinesis_1_1");
        if (frames == 20) capture(mc, "abilities-scale2.png");
        if (frames == 21) { mc.options.guiScale().set(3); mc.resizeDisplay(); }
        if (frames == 25) capture(mc, "abilities-scale3.png");
        if (frames == 28) mc.stop();
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
