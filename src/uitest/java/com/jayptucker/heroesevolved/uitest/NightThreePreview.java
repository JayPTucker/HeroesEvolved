package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.company.CompanyAgentEntity;
import com.jayptucker.heroesevolved.story.AvenueData;
import com.jayptucker.heroesevolved.story.night.NightThreeBunker;
import com.jayptucker.heroesevolved.story.night.NightThreeService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.nio.file.*;
import java.util.Comparator;
import java.util.function.Consumer;

/**
 * Night Three tour in a fresh flat world: walks into the third night once the bunker intro has let the
 * player out, and photographs the arrival, Gray in his cage, the stasis hall, the Suppressor, a guard
 * aiming, Gray loose, a guard out cold and Pod 14.
 *
 * <pre>./gradlew runClient --init-script tools/night-three-preview.init.gradle</pre>
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class NightThreePreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (player != null) action.accept(player);
        });
    }

    /** Stands the player at a spot in the bunker of the past (bunker-relative), looking along yaw and pitch. */
    private static void visit(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        server(mc, player -> {
            var night = player.getServer().getLevel(NightThreeBunker.DIMENSION);
            BlockPos origin = NightThreeBunker.ORIGIN;
            player.teleportTo(night, origin.getX() + x, origin.getY() + y, origin.getZ() + z, yaw, pitch);
        });
    }

    private static void mode(Minecraft mc, GameType type) { server(mc, player -> player.setGameMode(type)); }

    /** Takes a screenshot on the next frame, with the chat cleared so the room shows. */
    private static void capture(Minecraft mc, String name) {
        mc.gui.getChat().clearMessages(false);
        pending = name;
    }

    /** Knocks out the guard nearest the player and stands a few steps from where he fell, looking down at him. */
    private static void knockOutNearestGuard(ServerPlayer player) {
        var night = player.serverLevel();
        night.getEntitiesOfClass(CompanyAgentEntity.class, new AABB(NightThreeBunker.ORIGIN).inflate(40),
                        agent -> agent.role() == CompanyAgentEntity.Role.GUARD && !agent.outCold())
                .stream().min(Comparator.comparingDouble(agent -> agent.distanceToSqr(player))).ifPresent(agent -> {
                    agent.knockOut();
                    for (Vec3 step : new Vec3[] {new Vec3(0, 0, -2.5), new Vec3(0, 0, 2.5), new Vec3(2.5, 0, 0), new Vec3(-2.5, 0, 0)}) {
                        Vec3 at = agent.position().add(step);
                        BlockPos feet = BlockPos.containing(at);
                        if (!night.getBlockState(feet).isAir() || !night.getBlockState(feet.above()).isAir()) continue;
                        player.teleportTo(night, at.x, at.y, at.z, (float) Math.toDegrees(Math.atan2(step.x, -step.z)), 40);
                        break;
                    }
                });
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.night")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.gamma().set(1.0);
            mc.options.pauseOnLostFocus = false;
            mc.getTutorial().setStep(TutorialSteps.NONE);
            mc.createWorldOpenFlows().createFreshLevel("Night-" + System.currentTimeMillis(),
                    new LevelSettings("Night Three preview", GameType.SURVIVAL, false, Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(2026L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        switch (++worldTicks) {
            // The bunker intro holds a new player in a pod for a few seconds first.
            case 200 -> server(mc, player -> {
                AvenueData.get(player.getServer()).record(player.getUUID(), 3, true);
                player.setGameMode(GameType.CREATIVE);
                System.out.println("NIGHT_ENTER " + NightThreeService.enter(player));
            });
            case 240 -> capture(mc, "night-1-arrival.png");
            case 250 -> visit(mc, -1.0, 1, 14.5, 90, 8);
            case 275 -> capture(mc, "night-2-gray-caged.png");
            case 285 -> visit(mc, 0.5, 1, 19.2, 0, 10);
            case 310 -> capture(mc, "night-3-stasis-hall.png");
            case 320 -> visit(mc, -0.5, 1, 22.5, -18, 8);
            case 345 -> capture(mc, "night-4-suppressor.png");
            case 355 -> { visit(mc, 0.5, 1, 12.6, 0, 6); mode(mc, GameType.SURVIVAL); }
            case 373 -> capture(mc, "night-5-guard-aiming.png");
            case 376 -> mode(mc, GameType.CREATIVE);
            case 420 -> visit(mc, 0.5, 1, 12.6, 0, 6);
            case 445 -> capture(mc, "night-6-gray-loose.png");
            case 455 -> server(mc, NightThreePreview::knockOutNearestGuard);
            case 490 -> capture(mc, "night-7-out-cold.png");
            case 500 -> visit(mc, 2.5, 1, 29.5, -55, 14);
            case 525 -> capture(mc, "night-8-pod-14.png");
            case 545 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("NIGHT_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
