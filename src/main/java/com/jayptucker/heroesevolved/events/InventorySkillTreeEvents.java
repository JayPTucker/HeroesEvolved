package com.jayptucker.heroesevolved.events;
import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.gui.screen.PowersScreen;
import net.minecraft.client.Minecraft;
import com.jayptucker.heroesevolved.network.ClientSkillTreeState;
import com.jayptucker.heroesevolved.client.sound.SonicVoiceSound;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * An eclipse-emblem Skills button beside the recipe book button, matching its frame and size.
 * It follows the recipe button each frame, so it stays level when the recipe book opens or closes.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class InventorySkillTreeEvents {
    private static final WidgetSprites SPRITES = new WidgetSprites(
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "skills_button"),
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "skills_button_highlighted"));
    private static final int GAP = 2;
    private record Pair(WeakReference<Button> skills, WeakReference<Button> recipe) {}
    private static final WeakHashMap<InventoryScreen, Pair> BUTTONS = new WeakHashMap<>();

    @SubscribeEvent public static void init(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen inventory)) return;
        // The recipe book toggle is the inventory's only image button.
        Button recipe = inventory.children().stream().filter(child -> child instanceof ImageButton)
                .map(child -> (Button) child).findFirst().orElse(null);
        Button button = new ImageButton(0, 0, 20, 18, SPRITES, b -> Minecraft.getInstance().setScreen(new PowersScreen(inventory)),
                Component.translatable("screen.heroesevolved.skills_button"));
        button.setTooltip(Tooltip.create(Component.translatable("screen.heroesevolved.skills_button.desc")));
        place(inventory, button, recipe);
        event.addListener(button);
        BUTTONS.put(inventory, new Pair(new WeakReference<>(button), new WeakReference<>(recipe)));
    }

    @SubscribeEvent public static void render(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen inventory)) return;
        Pair pair = BUTTONS.get(inventory);
        Button button = pair == null ? null : pair.skills.get();
        if (button != null) place(inventory, button, pair.recipe.get());
    }

    private static void place(InventoryScreen inventory, Button button, Button recipe) {
        if (recipe != null) { button.setX(recipe.getX() + recipe.getWidth() + GAP); button.setY(recipe.getY()); }
        else { button.setX(inventory.getGuiLeft() + 126); button.setY(inventory.height / 2 - 22); }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        BUTTONS.clear(); ClientSkillTreeState.clear(); SonicVoiceSound.clear();
        com.jayptucker.heroesevolved.network.ClientLoadout.clear();
    }
}
