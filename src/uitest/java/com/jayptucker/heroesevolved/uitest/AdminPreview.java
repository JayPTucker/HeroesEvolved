package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.admin.AdminService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.gui.screen.AdminScreen;
import com.jayptucker.heroesevolved.intro.PowerIntroHistory;
import com.jayptucker.heroesevolved.network.AdminActionPayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Operator console at normal and compact GUI scales, including actual button requests and cleanup. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class AdminPreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void act(Minecraft mc, AdminActionPayload.Action action, int value) {
        onServer(mc, p -> AdminService.handle(p, new AdminActionPayload(action, p.getUUID(), "", value)));
    }

    private static void shot(String name) { pending = "admin-" + name + ".png"; }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.admin")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.guiScale().set(2);
            mc.resizeDisplay();
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Admin-" + System.currentTimeMillis(),
                    new LevelSettings("Admin preview", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(91L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        mc.getToasts().clear();
        if (mc.screen instanceof AdminScreen screen)
            for (var child : screen.children())
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) widget.setTooltip(null);
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 4, 4);
        switch (++worldTicks) {
            case 130 -> onServer(mc, com.jayptucker.heroesevolved.story.prologue.PrologueService::skip);
            case 150 -> onServer(mc, p -> {
                com.jayptucker.heroesevolved.story.bunker.CryoChamberService.release(p);
                var world = p.getServer().overworld();
                for (int cx = 92; cx <= 95; cx++) for (int cz = -2; cz <= 1; cz++) world.setChunkForced(cx, cz, true);
                p.teleportTo(world, 1500.5, -60, -8.5, 0, 10);
                p.serverLevel().setDayTime(6000);
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, PowerIntroHistory.empty().with(ModAbilities.SUPERSPEED_ID));
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                        .assignDormant(ModAbilities.SUPERSPEED_ID).unlock(ModAbilities.SUPERSPEED_ID));
                PlayerProgressionService.setLevel(p, 4);
                p.teleportTo(p.serverLevel(), 1500.5, -60, -8.5, 0, 10);
                System.out.println("ADMIN_STAGE dimension=" + p.level().dimension().location() + " underfoot=" + p.level().getBlockState(p.blockPosition().below()));
                // A bell and a few villagers, for the village events.
                p.serverLevel().setBlockAndUpdate(new BlockPos(1500, -60, 0), Blocks.BELL.defaultBlockState()
                        .setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
                for (int i = 0; i < 4; i++) {
                    var v = EntityType.VILLAGER.create(p.serverLevel());
                    v.moveTo(1500.5 + (i - 1.5) * 3, -60, 3.5, 180, 0);
                    p.serverLevel().addFreshEntity(v);
                }
            });
            case 165 -> act(mc, AdminActionPayload.Action.TOGGLE_GOD_MODE, 0);
            case 170 -> { AdminScreen.showPlayer(); onServer(mc, AdminService::open); }
            case 185 -> shot("1-player");
            case 186 -> scrollBottom(mc);
            case 187 -> shot("12-player-tools");
            case 188 -> { if (mc.screen instanceof AdminScreen s) s.mouseScrolled(s.width - 25, s.height / 2.0, 0, 100); }
            case 190 -> { AdminScreen.showWorld(); if (mc.screen instanceof AdminScreen s) s.onStateUpdated(); }
            case 198 -> shot("2-world");
            case 205 -> { AdminScreen.showEvent("meltdown"); if (mc.screen instanceof AdminScreen s) s.onStateUpdated(); }
            case 215 -> click(mc, "60 seconds");
            case 245 -> act(mc, AdminActionPayload.Action.REFRESH, 0);
            case 255 -> { check(mc, "meltdown", true); shot("3-world-meltdown"); }
            case 258 -> { AdminScreen.showIncidents(); if (mc.screen instanceof AdminScreen s) s.onStateUpdated(); }
            case 259 -> shot("5-incidents");
            case 260 -> act(mc, AdminActionPayload.Action.MELTDOWN_CANCEL, 0);
            case 266 -> act(mc, AdminActionPayload.Action.GIVE_RADIO, 0);
            case 270 -> { AdminScreen.showEvent("overload"); act(mc, AdminActionPayload.Action.STOP_ECLIPSE, 0); }
            case 275 -> click(mc, "Blizzard");
            case 290 -> act(mc, AdminActionPayload.Action.REFRESH, 0);
            case 300 -> { check(mc, "overload", true); shot("4-overload"); }
            case 305 -> onServer(mc, p -> System.out.println("ADMIN_RADIO " + p.getInventory().items.stream()
                    .filter(s -> !s.isEmpty()).map(s -> s.getItem() + " " + s.getComponents()).toList()));
            case 310 -> click(mc, "Cancel event");
            case 325 -> { AdminScreen.showDebug(); if (mc.screen instanceof AdminScreen s) s.onStateUpdated(); }
            case 335 -> { check(mc, "overload", false); shot("6-debug"); }
            case 340 -> scrollBottom(mc);
            case 345 -> shot("7-debug-tools");
            case 350 -> click(mc, "Cancel all events");
            case 355 -> { if (mc.screen instanceof AdminScreen s) s.mouseScrolled(s.width - 25, s.height / 2.0, 0, 100); }
            case 356 -> click(mc, "Replay tips");
            case 360 -> click(mc, "Give civilian journal");
            case 366 -> onServer(mc, p -> {
                var data = p.getData(ModDataAttachments.GUIDANCE);
                boolean book = p.getInventory().items.stream().anyMatch(s -> s.is(net.minecraft.world.item.Items.WRITABLE_BOOK)
                        && s.get(net.minecraft.core.component.DataComponents.WRITABLE_BOOK_CONTENT) != null);
                PreviewQuiet.report("ADMIN_GUIDANCE " + (data.enabled() && data.seen() == 0 && book ? "PASS" : "FAIL") + ": reset receipts and actual journal delivery");
            });
            case 370 -> { AdminScreen.showStory(); if (mc.screen instanceof AdminScreen s) s.onStateUpdated(); }
            case 380 -> shot("8-story");
            case 390 -> { AdminScreen.showPlayer(); mc.options.guiScale().set(3); mc.resizeDisplay(); if (mc.screen instanceof AdminScreen s) s.onStateUpdated(); }
            case 400 -> shot("9-compact-player");
            case 405 -> { AdminScreen.showEvent("company"); if (mc.screen instanceof AdminScreen s) s.onStateUpdated(); }
            case 415 -> shot("10-compact-events");
            case 420 -> scrollBottom(mc);
            case 425 -> shot("11-compact-controls");
            case 430 -> { AdminScreen.showCommands(); if (mc.screen instanceof AdminScreen s) s.onStateUpdated(); }
            case 440 -> {
                var commands = com.jayptucker.heroesevolved.network.ClientAdminState.get().commands();
                PreviewQuiet.report("ADMIN_COMMANDS " + (commands.stream().anyMatch(c -> c.startsWith("/heroesevolved village project"))
                        && commands.stream().anyMatch(c -> c.startsWith("/heroesevolved mimic gift")) ? "PASS" : "FAIL") + ": live registered commands " + commands.size());
                shot("13-compact-commands");
            }
            case 445 -> {
                if (mc.screen instanceof AdminScreen s) {
                    var search = s.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.EditBox).findFirst().orElse(null);
                    s.setFocused(search);
                    for (char letter : "trace".toCharArray()) s.charTyped(letter, 0);
                }
            }
            case 450 -> {
                if (mc.screen instanceof AdminScreen s) {
                    long visible = s.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().startsWith("/heroesevolved trace ")).count();
                    PreviewQuiet.report("ADMIN_COMMAND_FILTER " + (visible == 2 ? "PASS" : "FAIL") + ": " + visible + " trace commands after typing");
                }
                shot("14-compact-command-search");
            }
            case 455 -> { mc.options.guiScale().set(2); mc.resizeDisplay(); }
            case 460 -> shot("15-command-search");
            case 465 -> click(mc, "/heroesevolved trace list");
            case 470 -> {
                boolean editor = mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen
                        && mc.screen.children().stream().anyMatch(c -> c instanceof net.minecraft.client.gui.components.EditBox box
                        && box.getValue().equals("/heroesevolved trace list"));
                PreviewQuiet.report("ADMIN_COMMAND_TEMPLATE " + (editor ? "PASS" : "FAIL") + ": opened in chat without submitting");
                shot("16-command-template");
            }
            case 475 -> PreviewQuiet.quit();
            default -> { }
        }
    }

    private static void scrollBottom(Minecraft mc) {
        if (mc.screen instanceof AdminScreen s) s.mouseScrolled(s.width - 25, s.height / 2.0, 0, -100);
    }

    private static void click(Minecraft mc, String label) {
        if (!(mc.screen instanceof AdminScreen s)) { PreviewQuiet.report("ADMIN_INPUT FAIL: screen closed for " + label); return; }
        for (int attempt = 0; attempt < 40; attempt++) {
            for (var child : s.children()) if (child instanceof net.minecraft.client.gui.components.Button b
                    && b.getMessage().getString().equals(label) && b.active) {
                s.mouseClicked(b.getX() + b.getWidth() / 2.0, b.getY() + 10, 0);
                PreviewQuiet.report("ADMIN_INPUT PASS: " + label); return;
            }
            s.mouseScrolled(s.width - 25, s.height / 2.0, 0, -1);
        }
        PreviewQuiet.report("ADMIN_INPUT FAIL: missing button " + label);
    }

    private static void check(Minecraft mc, String id, boolean active) {
        boolean actual = com.jayptucker.heroesevolved.network.ClientAdminState.get().events().stream()
                .anyMatch(e -> e.id().equals(id) && e.active());
        PreviewQuiet.report("ADMIN_STATE " + (actual == active ? "PASS" : "FAIL") + ": " + id + " active=" + actual);
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("ADMIN_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
