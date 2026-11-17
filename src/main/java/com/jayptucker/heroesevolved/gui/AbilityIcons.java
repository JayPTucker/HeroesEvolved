package com.jayptucker.heroesevolved.gui;

import com.google.gson.JsonParser;
import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.gui.screen.PowerIcons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** The same authored glyph, keyed by its action or skill ID, on every ability surface. */
public final class AbilityIcons {
    private static final String INDEX = "/assets/heroesevolved/ability_icons.json";
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HeroesEvolved.MOD_ID, "textures/gui/ability_icons.png");
    private static final int CELL = 32;
    private static final int COLUMNS = 16;
    private static final Map<String, Integer> POSITIONS = index();
    private static final int ROWS = Math.ceilDiv(POSITIONS.size(), COLUMNS);

    private AbilityIcons() {}

    public static void draw(GuiGraphics graphics, String power, String id, int x, int y, int size) {
        Integer index = POSITIONS.get(id);
        if (index == null) {
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(size / 16F, size / 16F, 1);
            graphics.renderFakeItem(PowerIcons.emblem(power), 0, 0);
            graphics.pose().popPose();
            return;
        }
        float u = index % COLUMNS * CELL;
        float v = index / COLUMNS * CELL;
        graphics.blit(TEXTURE, x, y, size, size, u, v, CELL, CELL, COLUMNS * CELL, ROWS * CELL);
    }

    private static Map<String, Integer> index() {
        try (var stream = AbilityIcons.class.getResourceAsStream(INDEX)) {
            if (stream == null) throw new IllegalStateException("Missing ability icon index");
            var names = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray();
            Map<String, Integer> positions = new HashMap<>();
            for (int i = 0; i < names.size(); i++) positions.put(names.get(i).getAsString(), i);
            return Map.copyOf(positions);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read ability icon index", e);
        }
    }
}
