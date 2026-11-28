package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.gui.screen.GuideScreen;
import com.jayptucker.heroesevolved.gui.screen.PowersScreen;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The client half of the multiplayer check ({@code tools/multiplayer-check.init.gradle}, flag
 * {@code heroes.preview.multiplayer}): joins the dedicated server on {@code heroes.check.port}, opens the Powers and Guide
 * screens, photographs the powers the server fires around it, and waits for the server's
 * {@link MultiplayerCheckServer#DONE} kick. Any other way of leaving (a packet that fails to decode on either side, a
 * handler that throws, a timeout) fails the run with the reason the game showed.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class MultiplayerCheckClient {
    private static final int JOIN_ATTEMPTS = 30;
    private static final int CHECK_TIMEOUT = 20 * 60 * 15;
    private static int clock, nextTry = 60, attempts, age, gone, shots;
    private static boolean joined, ended;
    private static String capture;

    private MultiplayerCheckClient() {
    }

    private static boolean on() {
        return Boolean.getBoolean("heroes.preview.multiplayer");
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!on() || ended) return;
        Minecraft mc = Minecraft.getInstance();
        clock++;
        if (mc.getOverlay() != null) return;
        if (!joined) {
            if (mc.player != null && mc.getConnection() != null) {
                joined = true;
                PreviewQuiet.report("MULTIPLAYER_CHECK joined the dedicated server");
                mc.options.pauseOnLostFocus = false;
                return;
            }
            if (mc.screen instanceof DisconnectedScreen screen) {
                String why = reason(screen);
                if (attempts >= JOIN_ATTEMPTS) {
                    end("MULTIPLAYER_CHECK FAIL could not join the server: " + why);
                    return;
                }
                System.out.println("MULTIPLAYER_CHECK join attempt " + attempts + " refused: " + why);
                mc.setScreen(new TitleScreen());
                nextTry = clock + 100;
            } else if (!(mc.screen instanceof ConnectScreen) && mc.level == null && clock >= nextTry) {
                // From the title screen, or the first-launch accessibility screen a fresh game folder opens with.
                attempts++;
                mc.options.onboardAccessibility = false;
                String address = "127.0.0.1:" + Integer.getInteger("heroes.check.port", 25599);
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address),
                        new ServerData("Heroes Evolved check", address, ServerData.Type.OTHER), false, null);
                nextTry = clock + 600;
            }
            return;
        }
        if (mc.player == null || mc.getConnection() == null) {
            if (mc.screen instanceof DisconnectedScreen screen) {
                String why = reason(screen);
                if (!why.contains(MultiplayerCheckServer.DONE)) {
                    end("MULTIPLAYER_CHECK FAIL the client was disconnected: " + why);
                } else if (why.contains("failures=0")) {
                    end("MULTIPLAYER_CHECK PASS the server fired every power at this client and reported no failures");
                } else {
                    end("MULTIPLAYER_CHECK FAIL the server reported problems (" + why + "); see its multiplayer-check-server.txt");
                }
            } else if (++gone > 400) {
                end("MULTIPLAYER_CHECK FAIL the client left the server without a reason on screen");
            }
            return;
        }
        age++;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        switch (age) {
            case 100 -> capture = "multiplayer-joined.png";
            case 120 -> mc.setScreen(new PowersScreen());
            case 150 -> capture = "multiplayer-powers-screen.png";
            case 160 -> mc.setScreen(new GuideScreen(null));
            case 190 -> capture = "multiplayer-guide-screen.png";
            case 200 -> mc.setScreen(null);
            default -> {
                if (age > 200 && age % 300 == 0) capture = "multiplayer-" + String.format("%02d", ++shots) + ".png";
            }
        }
        if (age > CHECK_TIMEOUT) end("MULTIPLAYER_CHECK FAIL the server never finished the check");
    }

    /** The reason a disconnected screen shows, read from whichever field holds it in this version. */
    private static String reason(DisconnectedScreen screen) {
        StringBuilder text = new StringBuilder(screen.getTitle().getString());
        for (Field field : DisconnectedScreen.class.getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object value = field.get(screen);
                if (value instanceof DisconnectionDetails details) text.append(": ").append(details.reason().getString());
                else if (value instanceof Component component) text.append(" / ").append(component.getString());
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return text.toString();
    }

    private static void end(String line) {
        ended = true;
        PreviewQuiet.report(line);
        PreviewQuiet.quit();
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Post event) {
        if (capture == null) return;
        Minecraft mc = Minecraft.getInstance();
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
            System.out.println("MULTIPLAYER_CHECK photographed " + capture);
        } catch (Exception e) {
            System.out.println("MULTIPLAYER_CHECK could not photograph " + capture + ": " + e);
        }
        capture = null;
    }
}
