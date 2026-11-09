package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.client.effects.AbilityEffectRenderer;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fixed-camera captures of the signature abilities and machine effects at night: the Phoenix in level
 * flight and in a dive, a Singularity forming and collapsing, Blackout's siphon and pulse, and machines
 * shorting out and charging. Effects are played straight into the client renderer. This source set never
 * enters the released jar.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class SignaturePreview {
    private static final Vec3 CENTER = new Vec3(300.5, -58.5, 5.5);
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;

    private SignaturePreview() {}

    private static void effect(Kind kind, Vec3 from, Vec3 to, float scale) {
        AbilityEffectRenderer.play(new AbilityEffectPayload(kind, from, to, scale));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.signature")) return;
        var mc = Minecraft.getInstance();
        if (!started && ++ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.particles().set(ParticleStatus.ALL);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Signature-" + System.currentTimeMillis(),
                    new LevelSettings("Signature preview", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                            true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (++worldTicks == 1 || worldTicks == 180) mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            com.jayptucker.heroesevolved.story.bunker.CryoChamberService.release(player);
            player.removeAllEffects();
            player.serverLevel().setDayTime(18000);
            player.teleportTo(player.serverLevel(), 300.5, -60, -6.5, 0, -2);
            for (int x = 290; x <= 311; x++) for (int z = -4; z <= 16; z++)
                player.serverLevel().setBlock(new BlockPos(x, -61, z), Blocks.DEEPSLATE_TILES.defaultBlockState(), 3);
            // Props for the machine shots: a row of "machines" to the left.
            for (int i = 0; i < 4; i++) player.serverLevel().setBlock(new BlockPos(296 + i * 2, -60, 3), i % 2 == 0 ? Blocks.FURNACE.defaultBlockState() : Blocks.BARREL.defaultBlockState(), 3);
        });
        mc.player.setYRot(0); mc.player.setXRot(-2); mc.player.yHeadRot = 0; mc.player.yBodyRot = 0;
        int t = worldTicks;
        // Phoenix in level flight across the view, then a steep dive.
        if (t >= 200 && t <= 214) {
            Vec3 at = CENTER.add(-2 + (t - 200) * 0.2, 2.4, 5 - (t - 200) * 0.45);
            effect(Kind.PYRO_PHOENIX, at, at.add(0.35, 0.02, -1), 1.0F);
        }
        if (t == 213) pending = "signature-1-phoenix-glide.png";
        if (t >= 240 && t <= 252) effect(Kind.PYRO_PHOENIX, CENTER.add(2 - (t - 240) * 0.3, 4.5 - (t - 240) * 0.3, 1), CENTER.add(1.6 - (t - 240) * 0.3, 3.7 - (t - 240) * 0.3, 0.9), 1.4F);
        if (t == 250) pending = "signature-2-phoenix-dive.png";
        if (t == 262) effect(Kind.PYRO_PHOENIX_STRIKE, CENTER, CENTER.add(-0.5, -1, 0), 2.2F);
        if (t == 264) pending = "signature-3-phoenix-strike.png";
        // A Singularity forming and holding, seen a little from above, then its collapse.
        if (t >= 300 && t <= 346 && t % 2 == 0) effect(Kind.TIME_SINGULARITY, CENTER.add(0, 1.5, 0),
                CENTER.add(0, 1.5, 0).add(com.jayptucker.heroesevolved.time.SingularityService.DISK_NORMAL), Math.min(1, (t - 300) / 10F));
        if (t == 330) pending = "signature-4-singularity.png";
        if (t == 348) effect(Kind.TIME_SINGULARITY_COLLAPSE, CENTER.add(0, 1.5, 0), CENTER.add(0, 1.5, 0).add(com.jayptucker.heroesevolved.time.SingularityService.DISK_NORMAL), 8F);
        if (t == 353) pending = "signature-5-singularity-collapse.png";
        // Blackout: arcs from the machines into a point, then the pulse.
        if (t >= 390 && t <= 410 && t % 2 == 0) for (int i = 0; i < 4; i++)
            effect(Kind.GRID_SIPHON, new Vec3(296.5 + i * 2, -59.5, 3.5), CENTER.add(2, 0.2, 2), (t - 390) / 20F);
        if (t == 406) pending = "signature-6-blackout-siphon.png";
        if (t == 412) effect(Kind.ELECTRIC_BLACKOUT, CENTER.add(2, -1.5, 2), CENTER.add(2, -0.3, 2), 10F);
        if (t == 416) pending = "signature-7-blackout-pulse.png";
        // Machines dying in a pulse, then one charging.
        if (t == 450 || t == 454) for (int i = 0; i < 4; i++) effect(Kind.GRID_SHORT, new Vec3(296.5 + i * 2, -59.5, 3.5), new Vec3(296.5 + i * 2, -58.5, 3.5), 1F);
        if (t == 456) pending = "signature-8-machines-short.png";
        if (t == 480) effect(Kind.GRID_CHARGE, new Vec3(298.5, -59.5, 3.5), new Vec3(298.5, -58.5, 3.5), 1F);
        if (t == 484) pending = "signature-9-machine-charge.png";
        if (t == 510) mc.stop();
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent());
            screenshot.writeToFile(out);
            System.out.println("SIGNATURE_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
