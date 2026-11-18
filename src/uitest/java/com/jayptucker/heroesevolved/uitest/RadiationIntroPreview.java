package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.client.RadiationIntroClient;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.intro.PowerIntroService;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.nio.file.Files;
import java.util.function.Consumer;

/** A solid hill in one forced overworld area, followed through the actual acquisition hook. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class RadiationIntroPreview {
    private static boolean started;
    private static int startup, age;
    private static volatile boolean staged;
    private static String capture;
    private static volatile Vec3 floor;

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    private static void stage(ServerPlayer p) {
        PrologueService.skip(p);
        CryoChamberService.release(p);
        var level = p.getServer().overworld();
        for (int x = 5; x <= 13; x++) for (int z = 5; z <= 14; z++) level.setChunkForced(x, z, true);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = -53; y < 0; y++) for (int x = 96; x <= 204; x++) for (int z = 96; z <= 204; z++) {
            pos.set(x, y, z);
            level.setBlock(pos, (y == -1 ? Blocks.GRASS_BLOCK : Blocks.STONE).defaultBlockState(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        p.teleportTo(level, 150.5, 0, 150.5, 180, 5);
        p.setGameMode(GameType.SURVIVAL);
        p.removeAllEffects();
        level.setDayTime(6000);
        System.out.println("RADIATION_INTRO_STAGE " + p.level().dimension().location() + " " + p.position()
                + " underfoot=" + level.getBlockState(p.blockPosition().below()));
        staged = true;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.radiationintro")) return;
        Minecraft mc = Minecraft.getInstance();
        if (!started && ++startup > 35 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(10);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("RadiationIntro-" + System.currentTimeMillis(),
                    new LevelSettings("Radiation awakening", GameType.SURVIVAL, false, Difficulty.NORMAL, true,
                            rules, WorldDataConfiguration.DEFAULT), new WorldOptions(91L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        mc.getToasts().clear();
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (!staged) {
            if (++age == 100) server(mc, RadiationIntroPreview::stage);
            return;
        }
        switch (++age) {
            case 125 -> {
                mc.options.hideGui = true;
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                server(mc, p -> { PlayerAbilityService.grantAbility(p, ModAbilities.RADIATION_ID); PlayerPowerSyncService.sync(p); });
            }
            case 180 -> capture = "01-charge-front.png";
            case 220 -> TempestCamera.fix(new Vec3(162, 5, 157), new Vec3(150.5, 1.5, 150.5));
            case 230 -> capture = "02-charge-side.png";
            case 260 -> { TempestCamera.fix(null, null); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
            case 300 -> capture = "03-charge-back.png";
            case 660 -> TempestCamera.fix(new Vec3(150, 26, 225), new Vec3(150.5, 5, 150.5));
            case 690 -> capture = "04-charge-wide.png";
            case 720 -> {
                TempestCamera.fix(null, null);
                mc.options.hideGui = false; // Eyelids are part of the actual GUI render.
                mc.gui.getChat().clearMessages(true);
            }
            case 731 -> capture = "05-blackout.png";
            case 757 -> capture = "06-eyes-opening.png";
            case 770 -> capture = "07-blink.png";
            case 790 -> capture = "08-crater-first-person.png";
            case 825 -> {
                capture = "09-recovering.png";
                server(mc, p -> {
                    floor = p.position();
                    System.out.println("RADIATION_INTRO_FLOOR " + p.level().dimension().location() + " " + p.position()
                            + " underfoot=" + p.level().getBlockState(p.blockPosition().below()) + " pose=" + p.getPose()
                            + " active=" + PowerIntroService.isActive(p));
                });
                System.out.println("RADIATION_INTRO_VIEW camera=" + mc.options.getCameraType() + " active=" + RadiationIntroClient.active());
            }
            case 880 -> {
                // Re-stage the same sleeping pose after the real recovery, to photograph it from outside.
                server(mc, p -> p.setForcedPose(net.minecraft.world.entity.Pose.SLEEPING));
                mc.player.setForcedPose(net.minecraft.world.entity.Pose.SLEEPING);
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                TempestCamera.fix(floor.add(4, 2.5, 4), floor.add(0, .3, 0));
            }
            case 890 -> capture = "10-crater-floor.png";
            case 900 -> {
                server(mc, p -> p.setForcedPose(null));
                mc.player.setForcedPose(null);
                TempestCamera.fix(new Vec3(150, 40, 215), new Vec3(150, -25, 150));
            }
            case 910 -> capture = "11-crater-no-pole.png";
            case 1450 -> capture = "12-crater-clear.png";
            case 1470 -> PreviewQuiet.quit();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if (capture == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            var out = mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
            System.out.println("RADIATION_INTRO_CAPTURE " + capture);
        } catch (Exception e) { throw new RuntimeException(e); }
        capture = null;
    }
}
