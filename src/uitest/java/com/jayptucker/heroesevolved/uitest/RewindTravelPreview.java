package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.time.TimeRewindService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.nio.file.*;
import java.util.function.Consumer;

/**
 * Rewrite the Moment across worlds: walk in the Overworld, go to the Nether, walk on, rewind fifteen seconds.
 * The trip back is photographed in the Nether, after it crosses back, and on landing.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class RewindTravelPreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void walk(Minecraft mc, boolean on) {
        mc.options.keyUp.setDown(on);
    }

    private static void shot(Minecraft mc, String name) {
        pending = "travel-" + name + ".png";
        System.out.println("TRAVEL_STATE " + name + " camera=" + mc.getCameraEntity().getClass().getSimpleName()
                + " world=" + mc.level.dimension().location() + " at=" + mc.getCameraEntity().position());
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.rewindtravel")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Travel-" + System.currentTimeMillis(),
                    new LevelSettings("Travel preview", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(91L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        worldTicks++;
        switch (worldTicks) {
            case 200 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(5000);
                p.setData(ModDataAttachments.TIME_INTRO_SEEN, true);
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.TIME_MANIPULATION_ID));
                PlayerAbilityService.replaceWithAbility(p, ModAbilities.TIME_MANIPULATION_ID);
                PlayerProgressionService.setLevel(p, 4);
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60, 4, false, false));
                // A row of gold to walk along, so the path is easy to see.
                // Staged well away from spawn: the bunker stands there and its walls stop a walk.
                for (int z = 80; z < 110; z++) p.serverLevel().setBlockAndUpdate(new BlockPos(80, -61, z), Blocks.GOLD_BLOCK.defaultBlockState());
                p.teleportTo(p.serverLevel(), 80.5, -60, 80.5, 0, 0);
                // A walkway over the Nether to arrive on.
                ServerLevel nether = p.getServer().getLevel(Level.NETHER);
                for (int x = -2; x <= 2; x++) for (int z = -2; z < 40; z++) {
                    nether.setBlockAndUpdate(new BlockPos(x, 99, z), (x == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.POLISHED_BLACKSTONE).defaultBlockState());
                    for (int y = 100; y < 104; y++) nether.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            });
            // (The new world's wake-up scene holds the player for the first few hundred ticks.)
            case 330 -> walk(mc, true);
            case 400 -> { walk(mc, false); System.out.println("TRAVEL_OVERWORLD_WALKED to " + mc.player.position()); }
            // Through a "portal": into the Nether, then on along the walkway.
            case 410 -> onServer(mc, p -> p.teleportTo(p.getServer().getLevel(Level.NETHER), 0.5, 100, 0.5, 0, 0));
            case 450 -> walk(mc, true);
            case 530 -> walk(mc, false);
            case 560 -> shot(mc, "0-before");
            // Fifteen seconds back is about tick 320, just before the Overworld walk began.
            case 620 -> onServer(mc, p -> System.out.println("TRAVEL_REWIND " + TimeRewindService.canRewind(p) + " " + TimeRewindService.rewind(p)));
            case 624 -> shot(mc, "1-nether");
            case 632 -> shot(mc, "2-nether");
            case 642 -> shot(mc, "3-crossed");
            case 648 -> shot(mc, "4-overworld");
            case 662 -> shot(mc, "5-landed");
            case 680 -> shot(mc, "6-after");
            case 700 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("TRAVEL_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
