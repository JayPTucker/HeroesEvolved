package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.DeathRewindPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A time manipulator's death screen has one more button: rewind fifteen seconds and undo the death.
 * It counts down; once the fifteen seconds since death are gone, so is the chance. Pressing it asks
 * the server to rewind and respawns at once; the rewind happens as the new life begins.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class DeathRewindButton {
    private static long until = -1;
    private static Button button;
    private static boolean used;

    private DeathRewindButton() {}

    public static void offer(int ticks) {
        var level = Minecraft.getInstance().level;
        until = level == null ? -1 : level.getGameTime() + ticks;
        used = false;
        // The death screen may already be open.
        if (Minecraft.getInstance().screen instanceof DeathScreen screen) Minecraft.getInstance().setScreen(screen);
    }

    private static long remaining() {
        var level = Minecraft.getInstance().level;
        return level == null || until < 0 ? 0 : Math.max(0, until - level.getGameTime());
    }

    @SubscribeEvent public static void addButton(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof DeathScreen screen) || remaining() <= 0 || used) { button = null; return; }
        int width = screen.width, height = screen.height;
        button = Button.builder(label(), b -> {
                    used = true;
                    b.active = false;
                    PacketDistributor.sendToServer(new DeathRewindPayload(0));
                    var player = Minecraft.getInstance().player;
                    if (player != null) player.respawn();
                })
                .bounds(width / 2 - 100, height / 4 + 120, 200, 20)
                .tooltip(Tooltip.create(Component.literal("Go back 15 seconds from now, with what you were carrying then. "
                        + "The longer you wait, the closer to your death you land.")))
                .build();
        event.addListener(button);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (button == null) return;
        if (!(Minecraft.getInstance().screen instanceof DeathScreen)) { button = null; until = -1; return; }
        boolean open = remaining() > 0 && !used;
        button.active = open;
        button.setMessage(open ? label() : Component.literal("Too late to rewind").withStyle(ChatFormatting.GRAY));
    }

    private static Component label() {
        return Component.literal("⟲ Rewind 15 seconds  (" + (remaining() + 19) / 20 + "s)").withStyle(ChatFormatting.GOLD);
    }
}
