package com.jayptucker.heroesevolved.client.screen;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.hazard.SuitFabricatorMenu;
import com.jayptucker.heroesevolved.hazard.SuitItems;
import com.jayptucker.heroesevolved.hazard.SuitPatterns;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The Suit Fabricator: eight patterns along the top (the radiation suit, then the velocity suit, head to foot), six
 * material slots, and the piece. The selected pattern's materials are shown under the slots, each greyed out until
 * enough of it is in. Drawn with plain fills in the vanilla container style, so there is no background texture to keep.
 */
public class SuitFabricatorScreen extends AbstractContainerScreen<SuitFabricatorMenu> {
    private static final int BG = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, DARK = 0xFF555555, SLOT = 0xFF8B8B8B, LEAD = 0xFF7F8A93, EMBER = 0xFFB0402A;

    public SuitFabricatorScreen(SuitFabricatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = SuitFabricatorMenu.WIDTH;
        imageHeight = SuitFabricatorMenu.HEIGHT;
        inventoryLabelY = SuitFabricatorMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        bevel(g, x, y, imageWidth, imageHeight, BG, LIGHT, DARK);
        for (Slot slot : menu.slots) slot(g, x + slot.x - 1, y + slot.y - 1);
        // The two families of patterns, each on its own coloured strip.
        g.fill(x + SuitFabricatorMenu.BUTTON_X - 2, y + SuitFabricatorMenu.BUTTON_Y - 2,
                x + SuitFabricatorMenu.BUTTON_X + 4 * SuitFabricatorMenu.BUTTON_STEP, y + SuitFabricatorMenu.BUTTON_Y + 18, LEAD);
        g.fill(x + SuitFabricatorMenu.BUTTON_X + 4 * SuitFabricatorMenu.BUTTON_STEP, y + SuitFabricatorMenu.BUTTON_Y - 2,
                x + SuitFabricatorMenu.BUTTON_X + 8 * SuitFabricatorMenu.BUTTON_STEP + 2, y + SuitFabricatorMenu.BUTTON_Y + 18, EMBER);
        List<SuitPatterns.Pattern> patterns = SuitPatterns.all();
        for (int i = 0; i < patterns.size(); i++) {
            int bx = x + SuitFabricatorMenu.BUTTON_X + i * SuitFabricatorMenu.BUTTON_STEP, by = y + SuitFabricatorMenu.BUTTON_Y;
            boolean selected = menu.selected() == i, hover = inside(mouseX, mouseY, bx, by);
            bevel(g, bx - 1, by - 1, 18, 18, selected ? 0xFF6E8F5A : hover ? 0xFFAAAAAA : 0xFF9A9A9A, selected ? DARK : LIGHT, selected ? LIGHT : DARK);
            g.renderItem(patterns.get(i).make(), bx, by);
            if (menu.ready(i)) g.fill(bx + 12, by + 12, bx + 16, by + 16, 0xFF7CFC5A);
        }
        // The arrow from the slots to the piece.
        int ax = x + 90, ay = y + SuitFabricatorMenu.RESULT_Y + 4;
        g.fill(ax, ay + 3, ax + 26, ay + 6, DARK);
        for (int k = 0; k < 5; k++) g.fill(ax + 26 + k, ay + k - 1, ax + 27 + k, ay + 10 - k, DARK);
        SuitPatterns.Pattern pattern = menu.pattern();
        if (pattern == null) return;
        int nx = x + SuitFabricatorMenu.INPUT_X - 18;
        for (SuitPatterns.Need need : pattern.needs()) {
            int have = 0;
            for (int i = 0; i < SuitFabricatorMenu.INPUTS; i++) {
                ItemStack stack = menu.slots.get(i).getItem();
                if (!stack.isEmpty() && need.ingredient().test(stack)) have += stack.getCount();
            }
            ItemStack icon = need.icon().get().copyWithCount(need.count());
            g.renderItem(icon, nx, y + SuitFabricatorMenu.NEEDS_Y);
            g.renderItemDecorations(font, icon, nx, y + SuitFabricatorMenu.NEEDS_Y);
            if (have < need.count()) g.fill(nx, y + SuitFabricatorMenu.NEEDS_Y, nx + 16, y + SuitFabricatorMenu.NEEDS_Y + 16, 0x99C6C6C6);
            nx += 19;
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, 6, 0x404040, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
        if (menu.pattern() == null)
            g.drawString(font, Component.translatable("container.heroesevolved.suit_fabricator.pick"), 8, SuitFabricatorMenu.NEEDS_Y + 4, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        List<SuitPatterns.Pattern> patterns = SuitPatterns.all();
        for (int i = 0; i < patterns.size(); i++) {
            int bx = leftPos + SuitFabricatorMenu.BUTTON_X + i * SuitFabricatorMenu.BUTTON_STEP, by = topPos + SuitFabricatorMenu.BUTTON_Y;
            if (!inside(mouseX, mouseY, bx, by)) continue;
            List<Component> lines = new ArrayList<>();
            lines.add(patterns.get(i).make().getHoverName());
            for (SuitPatterns.Need need : patterns.get(i).needs())
                lines.add(Component.literal(need.count() + " x ").append(need.icon().get().getHoverName()).withStyle(ChatFormatting.GRAY));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < SuitPatterns.all().size(); i++) {
            int bx = leftPos + SuitFabricatorMenu.BUTTON_X + i * SuitFabricatorMenu.BUTTON_STEP, by = topPos + SuitFabricatorMenu.BUTTON_Y;
            if (inside((int) mouseX, (int) mouseY, bx, by) && menu.clickMenuButton(minecraft.player, i)) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1));
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean inside(int mx, int my, int x, int y) { return mx >= x - 1 && mx < x + 17 && my >= y - 1 && my < y + 17; }

    private static void bevel(GuiGraphics g, int x, int y, int w, int h, int fill, int light, int dark) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w - 1, y + 1, light);
        g.fill(x, y, x + 1, y + h - 1, light);
        g.fill(x + 1, y + h - 1, x + w, y + h, dark);
        g.fill(x + w - 1, y + 1, x + w, y + h, dark);
    }

    private static void slot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT);
        g.fill(x, y, x + 17, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + 17, 0xFF373737);
        g.fill(x + 1, y + 17, x + 18, y + 18, LIGHT);
        g.fill(x + 17, y + 1, x + 18, y + 18, LIGHT);
    }

    @EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void screens(RegisterMenuScreensEvent event) {
            event.register(SuitItems.FABRICATOR_MENU.get(), SuitFabricatorScreen::new);
        }

        /** The velocity suit's icon takes its dye on its first layer; the ceramic plates on the second stay as they are. */
        @SubscribeEvent
        public static void colors(RegisterColorHandlersEvent.Item event) {
            event.register((stack, layer) -> layer == 0 ? 0xFF000000 | DyedItemColor.getOrDefault(stack, SuitItems.VELOCITY_RED) : -1,
                    SuitItems.VELOCITY_COWL.get(), SuitItems.VELOCITY_SUIT.get(), SuitItems.VELOCITY_LEGGINGS.get(), SuitItems.VELOCITY_BOOTS.get());
        }
    }
}
