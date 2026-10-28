package com.jayptucker.heroesevolved.client.screen;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.formula.CentrifugeMenu;
import com.jayptucker.heroesevolved.formula.SynthesisStationMenu;
import com.jayptucker.heroesevolved.registry.ModMenus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** The lab screens reuse the vanilla furnace and brewing stand artwork, whose layouts they mirror. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class LabScreens {
    private LabScreens() {}

    @SubscribeEvent
    public static void register(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CENTRIFUGE.get(), CentrifugeScreen::new);
        event.register(ModMenus.SYNTHESIS_STATION.get(), SynthesisStationScreen::new);
    }

    private abstract static class LabScreen<T extends net.minecraft.world.inventory.AbstractContainerMenu> extends AbstractContainerScreen<T> {
        LabScreen(T menu, Inventory inventory, Component title) { super(menu, inventory, title); }
        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.render(graphics, mouseX, mouseY, partialTick);
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    public static final class CentrifugeScreen extends LabScreen<CentrifugeMenu> {
        private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/furnace.png");
        private static final ResourceLocation CHARGE = ResourceLocation.withDefaultNamespace("container/furnace/lit_progress");
        private static final ResourceLocation SPIN = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");

        public CentrifugeScreen(CentrifugeMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

        @Override
        protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
            int left = leftPos, top = topPos;
            graphics.blit(TEXTURE, left, top, 0, 0, imageWidth, imageHeight);
            if (menu.charged()) graphics.blitSprite(CHARGE, 14, 14, 0, 0, left + 56, top + 36, 14, 14);
            int width = Mth.ceil(24.0F * menu.progress() / menu.total());
            if (width > 0) graphics.blitSprite(SPIN, 24, 16, 0, 0, left + 79, top + 34, width, 16);
        }
    }

    public static final class SynthesisStationScreen extends LabScreen<SynthesisStationMenu> {
        private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/brewing_stand.png");
        private static final ResourceLocation FUEL = ResourceLocation.withDefaultNamespace("container/brewing_stand/fuel_length");
        private static final ResourceLocation PROGRESS = ResourceLocation.withDefaultNamespace("container/brewing_stand/brew_progress");
        private static final ResourceLocation BUBBLES = ResourceLocation.withDefaultNamespace("container/brewing_stand/bubbles");
        private static final int[] BUBBLE_LENGTHS = {29, 24, 20, 16, 11, 6, 0};

        public SynthesisStationScreen(SynthesisStationMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

        @Override
        protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
            int left = leftPos, top = topPos;
            graphics.blit(TEXTURE, left, top, 0, 0, imageWidth, imageHeight);
            int fuel = Mth.clamp((18 * menu.fuel() + 7) / 8, 0, 18);
            if (fuel > 0) graphics.blitSprite(FUEL, 18, 4, 0, 0, left + 60, top + 44, fuel, 4);
            int brew = menu.brewTime();
            if (brew > 0) {
                int length = (int) (28.0F * (1.0F - brew / 400.0F));
                if (length > 0) graphics.blitSprite(PROGRESS, 9, 28, 0, 0, left + 97, top + 16, 9, length);
                int bubbles = BUBBLE_LENGTHS[brew / 2 % 7];
                if (bubbles > 0) graphics.blitSprite(BUBBLES, 12, 29, 0, 29 - bubbles, left + 63, top + 14 + 29 - bubbles, 12, bubbles);
            }
        }
    }
}
