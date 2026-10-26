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
        if (Boolean.getBoolean("heroes.preview.afterimages")) return;
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
        if (!shown || !(mc.screen instanceof SkillTreeScreen)) return;
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
        if (frames == 18) mc.stop();
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
