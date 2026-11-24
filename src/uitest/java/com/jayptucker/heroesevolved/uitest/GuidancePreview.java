package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.client.PlayerGuidance;
import com.jayptucker.heroesevolved.guidance.GuidanceData;
import com.jayptucker.heroesevolved.gui.screen.GuideScreen;
import com.jayptucker.heroesevolved.gui.screen.HeroScreen;
import com.jayptucker.heroesevolved.gui.screen.PowersScreen;
import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class GuidancePreview {
    private record Shot(String name, Runnable prepare) {}
    private static final List<Shot> shots = new ArrayList<>();
    private static boolean begun;
    private static int ticks, frame, index;
    private static final String[] TOPICS = {"start", "controls", "growth", "combat", "world", "gear", "portal", "trouble"};

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.guidance") || begun) return;
        Minecraft mc = Minecraft.getInstance();
        if (++ticks < 40 || mc.getOverlay() != null || mc.screen == null) return;
        begun = true;
        PlayerGuidance.accept(GuidanceData.empty());
        for (int scale : new int[]{2, 3}) for (String topic : TOPICS) {
            shots.add(new Shot("guide-" + topic + "-scale" + scale + "-top", () -> {
                mc.options.guiScale().set(scale); mc.resizeDisplay(); mc.setScreen(new GuideScreen(null).topic(topic));
            }));
            shots.add(new Shot("guide-" + topic + "-scale" + scale + "-end", () -> mc.screen.keyPressed(GLFW.GLFW_KEY_END, 0, 0)));
        }
        for (var hint : GuidanceData.Hint.values()) shots.add(new Shot("note-" + hint.name().toLowerCase(), () -> mc.setScreen(new NoteScreen(hint))));
        shots.add(new Shot("guide-rebound-controls", () -> {
            ModKeyMappings.OPEN_POWERS.setKey(InputConstants.getKey(GLFW.GLFW_KEY_K, 0));
            ModKeyMappings.ABILITY_WHEEL.setKey(InputConstants.UNKNOWN);
            KeyMapping.resetMapping();
            var text = GuideScreen.text("guide.heroesevolved.hint.welcome").getString();
            PreviewQuiet.report("GUIDANCE bindings " + (text.contains("K") && GuideScreen.key(ModKeyMappings.ABILITY_WHEEL).getString().equals("unbound") ? "PASS" : "FAIL"));
            mc.setScreen(new GuideScreen(null).topic("controls"));
        }));
        shots.add(new Shot("note-rebound-unbound", () -> mc.setScreen(new NoteScreen(GuidanceData.Hint.AWAKENED))));
        shots.add(new Shot("guide-tips-off", () -> {
            PlayerGuidance.accept(GuidanceData.empty().enabled(false));
            mc.setScreen(new GuideScreen(null));
        }));
        try (var resource = GuidancePreview.class.getResourceAsStream("/data/heroesevolved/loot_table/chests/survivor_journals.json")) {
            var table = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(resource, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (var element : table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries")) {
                var functions = element.getAsJsonObject().getAsJsonArray("functions");
                String title = functions.get(0).getAsJsonObject().getAsJsonObject("name").get("text").getAsString();
                var pages = new ArrayList<net.minecraft.server.network.Filterable<String>>();
                for (var page : functions.get(2).getAsJsonObject().getAsJsonArray("pages")) pages.add(net.minecraft.server.network.Filterable.passThrough(page.getAsString()));
                var book = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WRITABLE_BOOK);
                book.set(net.minecraft.core.component.DataComponents.WRITABLE_BOOK_CONTENT, new net.minecraft.world.item.component.WritableBookContent(pages));
                var access = net.minecraft.client.gui.screens.inventory.BookViewScreen.BookAccess.fromItem(book);
                for (int page = 0; page < pages.size(); page++) {
                    int number = page;
                    int lines = mc.font.split(access.pages().get(page), 114).size();
                    PreviewQuiet.report("JOURNAL " + title + " page " + (page + 1) + " " + (lines <= 14 ? "PASS" : "FAIL") + " " + lines + " lines");
                    shots.add(new Shot("journal-" + title.toLowerCase().replace(' ', '-') + "-" + (page + 1), () -> {
                        var screen = new net.minecraft.client.gui.screens.inventory.BookViewScreen(access);
                        mc.setScreen(screen); screen.setPage(number);
                    }));
                }
            }
        } catch (Exception error) { PreviewQuiet.report("JOURNAL load FAIL " + error); }
        mc.options.guiScale().set(3); mc.resizeDisplay();
        mc.setScreen(new PowersScreen());
    }

    @SubscribeEvent public static void rendered(RenderFrameEvent.Post e) {
        if (!begun || !Boolean.getBoolean("heroes.preview.guidance")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        if (++frame == 5) {
            // Navigate through the actual shared header, including on a character with no power.
            int total = 0;
            String[] labels = {"LOADOUT", "SKILL TREE", "CLASS", "JOURNAL", "GUIDE", "SETTINGS"};
            for (String label : labels) total += mc.font.width(label) + 16;
            int x = (mc.screen.width - total) / 2 + 8;
            for (int i = 0; i < 4; i++) x += mc.font.width(labels[i]) + 16;
            mc.screen.mouseClicked(x + 2, 34, 0);
            PreviewQuiet.report("GUIDANCE no-power guide tab " + (mc.screen instanceof GuideScreen ? "PASS" : "FAIL"));
        }
        if (frame < 10) return;
        if (frame % 8 == 2 && index < shots.size()) shots.get(index).prepare.run();
        if (frame % 8 == 6 && index < shots.size()) {
            String name = shots.get(index++).name;
            try (var picture = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                var directory = mc.gameDirectory.toPath().resolve("screenshots");
                Files.createDirectories(directory);
                picture.writeToFile(directory.resolve(name + ".png"));
                System.out.println("GUIDANCE_CAPTURE " + name);
            } catch (Exception error) { PreviewQuiet.report("GUIDANCE capture FAIL " + error); }
        }
        if (index == shots.size()) PreviewQuiet.quit();
    }

    private static final class NoteScreen extends Screen {
        private final GuidanceData.Hint hint;
        NoteScreen(GuidanceData.Hint hint) { super(Component.literal("Field note")); this.hint = hint; }
        @Override public void render(GuiGraphics g, int x, int y, float partial) {
            g.fillGradient(0, 0, width, height, 0xFF20322D, 0xFF101814);
            PlayerGuidance.draw(g, hint);
        }
        @Override public void renderBackground(GuiGraphics g, int x, int y, float partial) {}
    }
}
