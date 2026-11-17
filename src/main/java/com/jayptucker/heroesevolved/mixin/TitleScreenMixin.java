package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.client.TitleMenu;
import com.jayptucker.heroesevolved.client.sound.TitleMusic;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    @Shadow private boolean fading;
    @Shadow private long fadeInStart;
    @Unique private long heroesEvolved$revealStart = -1L;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void heroesEvolved$arrangeMenu(CallbackInfo info) {
        TitleMenu.arrange((TitleScreen) (Object) this, minecraft.isDemo());
    }

    @Inject(method = "added", at = @At("TAIL"))
    private void heroesEvolved$resumeMenu(CallbackInfo info) {
        if (heroesEvolved$revealStart >= 0) TitleMusic.resumeMenu();
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void heroesEvolved$reveal(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo info) {
        // The loading overlay draws the title beneath its own fade. Start the reveal when the overlay is gone.
        long now = Util.getMillis();
        if (heroesEvolved$revealStart < 0) {
            if (minecraft.getOverlay() != null) {
                graphics.fill(0, 0, width, height, 0xFF000000);
                info.cancel();
                return;
            }
            heroesEvolved$revealStart = now;
            TitleMusic.beginIntro(minecraft);
        }
        if (heroesEvolved$revealing(now)) {
            heroesEvolved$drawTitle(graphics, now);
            info.cancel();
        } else if (fadeInStart == 0L) {
            // Enter the second half of the vanilla fade, where the widgets fade in.
            fadeInStart = heroesEvolved$revealStart + TitleMenu.CONTROLS_REVEAL_MS - 1000;
            fading = true;
        }
    }

    @Inject(method = "renderPanorama", at = @At("HEAD"), cancellable = true)
    private void heroesEvolved$renderEclipse(GuiGraphics graphics, float partialTick, CallbackInfo info) {
        heroesEvolved$drawTitle(graphics, Util.getMillis());
        info.cancel();
    }

    @Unique
    private void heroesEvolved$drawTitle(GuiGraphics graphics, long now) {
        float age = (now - heroesEvolved$revealStart) / 1000.0F;
        float motion = minecraft.options.panoramaSpeed().get().floatValue();
        TitleMenu.render(graphics, width, height, minecraft.isDemo(), age, motion);
    }

    @Unique
    private boolean heroesEvolved$revealing(long now) {
        return heroesEvolved$revealStart >= 0 && now - heroesEvolved$revealStart < TitleMenu.CONTROLS_REVEAL_MS;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void heroesEvolved$skipOnClick(double x, double y, int button, CallbackInfoReturnable<Boolean> info) {
        if (heroesEvolved$skipReveal()) info.setReturnValue(true);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (heroesEvolved$revealing(Util.getMillis())) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) heroesEvolved$skipReveal();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Unique
    private boolean heroesEvolved$skipReveal() {
        long now = Util.getMillis();
        if (!heroesEvolved$revealing(now)) return false;
        heroesEvolved$revealStart = now - TitleMenu.CONTROLS_REVEAL_MS - 1000L;
        TitleMusic.skipIntro(minecraft);
        return true;
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/LogoRenderer;renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IF)V"))
    private void heroesEvolved$replaceLogo(LogoRenderer logo, GuiGraphics graphics, int width, float alpha) {}

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/SplashRenderer;render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V"))
    private void heroesEvolved$hideSplash(SplashRenderer splash, GuiGraphics graphics, int width, Font font, int color) {}
}
