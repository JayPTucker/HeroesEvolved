package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.expansion.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.nio.file.*;

/** An isolated integrated-server smoke test with the real local player and ordinary world preset. */
@EventBusSubscriber(modid="heroesevolved", value=Dist.CLIENT)
public final class UnwrittenWorldPreview {
    private static boolean started, requested;
    private static volatile boolean entering;
    private static int ticks, siteTicks, returnedTicks;
    private static volatile boolean returned;
    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.unwritten")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.createWorldOpenFlows().createFreshLevel("Unwritten-" + System.currentTimeMillis(),
                    new LevelSettings("Unwritten preview", GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(824631L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null || mc.level == null) return;
        if (!requested) {
            requested = true;
            server.execute(() -> {
                var p = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (server.getLevel(FacilityService.DIMENSION) == null) throw new AssertionError("Annex missing in normal world preset");
                FacilityService.plan(p);
            });
        }
        if (!entering && !mc.level.dimension().equals(FacilityService.DIMENSION)) server.execute(() -> {
            if (FacilityData.get(server).phase != FacilityData.Phase.READY) return;
            var p = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (FacilityService.enter(p)) {
                entering = true;
                p.getAbilities().flying = true; p.onUpdateAbilities();
                p.teleportTo(server.getLevel(FacilityService.DIMENSION), 14.5, 65, 10.1, 0, -4);
                System.out.println("UNWRITTEN_NORMAL_WORLD_ENTERED");
            }
        });
        if (mc.level.dimension().equals(FacilityService.DIMENSION)) {
            mc.options.hideGui = true;
            mc.player.setYRot(0); mc.player.setXRot(-4); mc.player.yHeadRot = 0; mc.player.yBodyRot = 0;
            siteTicks++;
            if (siteTicks == 100) server.execute(() -> {
                var p = server.getPlayerList().getPlayer(mc.player.getUUID());
                FacilityService.technique(p, FacilityService.CORE, "cold");
                FacilityService.technique(p, FacilityService.GATE, "fracture");
            });
            if (siteTicks == 180) server.execute(() -> {
                var p = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (!FacilityService.leave(p)) throw new AssertionError("Return failed");
                System.out.println("UNWRITTEN_NORMAL_WORLD_RETURNED");
                returned = true;
            });
        }
        if (returned && !mc.level.dimension().equals(FacilityService.DIMENSION) && ++returnedTicks == 80) server.execute(() -> {
                var p = server.getPlayerList().getPlayer(mc.player.getUUID());
                try {
                    var power = com.jayptucker.heroesevolved.ability.registry.ModAbilities.PRECOGNITION_ID;
                    p.setData(com.jayptucker.heroesevolved.data.ModDataAttachments.PLAYER_ABILITIES, com.jayptucker.heroesevolved.ability.data.PlayerAbilityData.empty().assignDormant(power).unlock(power));
                    p.setGameMode(GameType.SURVIVAL); p.setShiftKeyDown(true); p.setHealth(3);
                    if (!PrecognitionService.activate(new com.jayptucker.heroesevolved.ability.AbilityUseContext(p, power, 5, false), 3)) throw new AssertionError("Fate failed to arm");
                    p.hurt(p.damageSources().generic(), 100);
                    if (!p.isAlive() || p.getHealth() != 2) throw new AssertionError("Actual ServerPlayer self rescue failed: health=" + p.getHealth() + ", alive=" + p.isAlive());
                    System.out.println("UNWRITTEN_FATE_SELF_RESCUED");
                } finally { mc.execute(mc::stop); }
        });
        if (ticks > 3600 && !entering) throw new AssertionError("World preview did not enter the mission");
    }
    private static boolean before, after;
    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.unwritten")) return;
        var mc = Minecraft.getInstance(); String name = null;
        if (!before && siteTicks >= 80) { before = true; name = "annex-before.png"; }
        if (!after && siteTicks >= 150) { after = true; name = "annex-cooled.png"; }
        if (name == null) return;
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(name); Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("UNWRITTEN_WORLD_CAPTURE " + name);
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }
}
