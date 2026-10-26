package com.jayptucker.heroesevolved.events;
import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.gui.screen.SkillTreeScreen;
import com.jayptucker.heroesevolved.network.ClientSkillTreeState;
import com.jayptucker.heroesevolved.client.sound.SonicVoiceSound;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import java.util.WeakHashMap;
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class InventorySkillTreeEvents {
    private static final WeakHashMap<InventoryScreen, java.lang.ref.WeakReference<Button>> BUTTONS = new WeakHashMap<>();
    @SubscribeEvent public static void init(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen inventory)) return;
        Button button = Button.builder(Component.translatable("screen.heroesevolved.skills_button"), b -> SkillTreeScreen.open(inventory))
                .bounds(inventory.getGuiLeft() + 128, inventory.getGuiTop() + 60, 40, 18).build();
        button.setTooltip(Tooltip.create(Component.translatable("screen.heroesevolved.skills_button.desc")));
        event.addListener(button); BUTTONS.put(inventory, new java.lang.ref.WeakReference<>(button));
    }
    @SubscribeEvent public static void render(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen inventory)) return;
        var reference = BUTTONS.get(inventory);
        Button button = reference == null ? null : reference.get();
        if (button != null) { button.setX(inventory.getGuiLeft() + 128); button.setY(inventory.getGuiTop() + 60); }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        BUTTONS.clear(); ClientSkillTreeState.clear(); SonicVoiceSound.clear();
    }
}
