package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.client.effects.SpeedAfterimages;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import java.io.File;

/** Uses the production translucent model renderer in a real Minecraft frame. No game world edited. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class AfterimagePreview {
    private static int ticks, frames;
    private static boolean shown;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.afterimages")) return;
        Minecraft mc = Minecraft.getInstance();
        if (shown || ++ticks < 40 || mc.getOverlay() != null || mc.screen == null) return;
        shown = true; mc.options.guiScale().set(2); mc.resizeDisplay(); mc.setScreen(new Preview());
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (!shown) return;
        if (++frames == 10) {
            Minecraft mc = Minecraft.getInstance();
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                File file = new File(mc.gameDirectory, "afterimages.png"); image.writeToFile(file);
            } catch (Exception e) { throw new RuntimeException(e); }
        }
        if (frames == 14) Minecraft.getInstance().stop();
    }
    private static final class Preview extends Screen {
        Preview() { super(Component.literal("Speed afterimage render check")); }
        @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
            graphics.fill(0, 0, width, height, 0xFF142430);
            for (int row = 0; row < height; row += 20) for (int col = 0; col < width; col += 20)
                if ((row / 20 + col / 20) % 2 == 0) graphics.fill(col, row, col + 20, row + 20, 0xFF294550);
            graphics.drawCenteredString(font, "Actual player skins / frozen running poses / fading transparency", width / 2, 24, 0xFFFFE3A3);
            graphics.flush();
            for (int i = 0; i < 4; i++) {
                boolean slim = i % 2 == 1;
                PlayerModel<AbstractClientPlayer> model = new PlayerModel<>(minecraft.getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
                model.leftArm.xRot = -.95F; model.rightArm.xRot = .95F;
                model.leftLeg.xRot = .9F; model.rightLeg.xRot = -.9F;
                model.leftSleeve.copyFrom(model.leftArm); model.rightSleeve.copyFrom(model.rightArm);
                model.leftPants.copyFrom(model.leftLeg); model.rightPants.copyFrom(model.rightLeg);
                var pose = graphics.pose(); pose.pushPose();
                pose.translate(width * (.16 + i * .225), height * .62, 150);
                pose.scale(70, -70, 70); pose.mulPose(Axis.YP.rotationDegrees(-30));
                pose.scale(-1, -1, 1); pose.translate(0, -1.501, 0);
                SpeedAfterimages.draw(model, pose, minecraft.renderBuffers().bufferSource(), DefaultPlayerSkin.getDefaultTexture(), i == 0 ? 1 : SpeedAfterimages.opacity((i - 1) * 6));
                minecraft.renderBuffers().bufferSource().endBatch(); pose.popPose();
                graphics.drawCenteredString(font, i == 0 ? "Reference" : "Age: " + (i - 1) * 6 + " ticks", (int) (width * (.16 + i * .225)), (int) (height * .72), 0xFFF5E9D5);
            }
        }
        @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) {}
    }
}

