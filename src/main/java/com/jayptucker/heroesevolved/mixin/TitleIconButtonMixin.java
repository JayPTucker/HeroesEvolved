package com.jayptucker.heroesevolved.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SpriteIconButton.CenteredIcon.class)
public abstract class TitleIconButtonMixin extends AbstractWidget {
    protected TitleIconButtonMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void heroesEvolved$fadeIcon(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height) {
        if (Minecraft.getInstance().screen instanceof TitleScreen && alpha < 1) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.setColor(1, 1, 1, alpha);
            graphics.blitSprite(sprite, x, y, width, height);
            graphics.setColor(1, 1, 1, 1);
        } else {
            graphics.blitSprite(sprite, x, y, width, height);
        }
    }
}
