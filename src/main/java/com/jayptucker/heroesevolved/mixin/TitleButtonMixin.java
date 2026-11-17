package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.client.TitleMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the menu's buttons in the title palette, retaining their original input and narration. */
@Mixin(AbstractButton.class)
public abstract class TitleButtonMixin extends AbstractWidget {
    protected TitleButtonMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void heroesEvolved$drawTitleButton(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo info) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof TitleScreen title
                && !minecraft.options.highContrast().get()
                && title.children().contains(this)
                && TitleMenu.drawButton(graphics, minecraft.font, (AbstractButton) (Object) this, alpha)) {
            info.cancel();
        }
    }
}
