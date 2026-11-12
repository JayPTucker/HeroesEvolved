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

/** The admin menu's two tabs, before and during a meltdown, photographed 1500 blocks from spawn. */
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
        switch (++worldTicks) {
            case 150 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(6000);
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, PowerIntroHistory.empty().with(ModAbilities.SUPERSPEED_ID));
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                        .assignDormant(ModAbilities.SUPERSPEED_ID).unlock(ModAbilities.SUPERSPEED_ID));
                PlayerProgressionService.setLevel(p, 4);
                p.teleportTo(p.serverLevel(), 1500.5, -60, -8.5, 0, 10);
                // A bell and a few villagers, for the village events.
                p.serverLevel().setBlockAndUpdate(new BlockPos(1500, -60, 0), Blocks.BELL.defaultBlockState()
                        .setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
                for (int i = 0; i < 4; i++) {
                    var v = EntityType.VILLAGER.create(p.serverLevel());
                    v.moveTo(1500.5 + (i - 1.5) * 3, -60, 3.5, 180, 0);
                    p.serverLevel().addFreshEntity(v);
                }
            });
            case 170 -> { AdminScreen.showPlayer(); onServer(mc, AdminService::open); }
            case 185 -> shot("1-player");
            case 190 -> { AdminScreen.showWorld(); if (mc.screen instanceof AdminScreen s) s.onStateUpdated(); }
            case 198 -> shot("2-world");
            case 205 -> act(mc, AdminActionPayload.Action.MELTDOWN_START, 60);
            case 245 -> act(mc, AdminActionPayload.Action.REFRESH, 0);
            case 255 -> shot("3-world-meltdown");
            case 260 -> act(mc, AdminActionPayload.Action.MELTDOWN_CANCEL, 0);
            case 266 -> act(mc, AdminActionPayload.Action.GIVE_RADIO, 0);
            case 270 -> act(mc, AdminActionPayload.Action.ECLIPSE_TOTALITY, 0);
            case 290 -> act(mc, AdminActionPayload.Action.REFRESH, 0);
            case 300 -> shot("4-world-totality");
            case 305 -> onServer(mc, p -> System.out.println("ADMIN_RADIO " + p.getInventory().items.stream()
                    .filter(s -> !s.isEmpty()).map(s -> s.getItem() + " " + s.getComponents()).toList()));
            case 315 -> mc.stop();
            default -> { }
        }
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
