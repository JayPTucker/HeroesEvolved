package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.effects.AbilityEffectRenderer;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
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

/** Fixed-camera captures of both new visual families. This source set never enters the released jar. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class ConstructPreview {
    private static final Vec3 CENTER = new Vec3(300.5, -59, 3.5);
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;

    private ConstructPreview() {}

    private static void effect(Kind kind, Vec3 from, Vec3 to, float scale) {
        AbilityEffectRenderer.play(new AbilityEffectPayload(kind, from, to, scale));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.construct")) return;
        var mc = Minecraft.getInstance();
        if (!started && ++ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.particles().set(ParticleStatus.ALL);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Construct-" + System.currentTimeMillis(),
                    new LevelSettings("Construct preview", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                            true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (++worldTicks == 1) mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            player.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                    .assignDormant(ModAbilities.TECHNOPATHY_ID).unlock(ModAbilities.TECHNOPATHY_ID));
            player.serverLevel().setDayTime(12800);
            player.teleportTo(player.serverLevel(), 300.5, -60, -5.5, 0, 4);
            for (int x = 295; x <= 306; x++) for (int z = -1; z <= 9; z++)
                player.serverLevel().setBlock(new BlockPos(x, -61, z), Blocks.DEEPSLATE_TILES.defaultBlockState(), 3);
        });
        if (worldTicks == 180) mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            com.jayptucker.heroesevolved.story.bunker.CryoChamberService.release(player);
            player.removeAllEffects();
            player.teleportTo(player.serverLevel(), 300.5, -60, -5.5, 0, 4);
        });
        mc.player.setYRot(0); mc.player.setXRot(4); mc.player.yHeadRot = 0; mc.player.yBodyRot = 0;
        boolean weaveOnly = Boolean.getBoolean("heroes.preview.construct.weaveOnly");
        if (weaveOnly && worldTicks < 200) return;
        if (weaveOnly && worldTicks >= 220) { mc.stop(); return; }
        switch (weaveOnly ? worldTicks + 150 : worldTicks) {
            case 200 -> effect(Kind.TECH_LINK, CENTER.add(-2.6, -.2, -2.5), CENTER.add(1.3, .5, 1), 1);
            case 204 -> pending = "construct-1-tech-link.png";
            case 235 -> effect(Kind.TECH_PULSE, CENTER.add(0, -.9, 0), CENTER, 4.5F);
            case 241 -> pending = "construct-2-tech-pulse.png";
            case 270 -> effect(Kind.TECH_SCAN, mc.player.getEyePosition(), CENTER, 8);
            case 278 -> pending = "construct-3-tech-scan.png";
            case 310 -> effect(Kind.TECH_CAGE, CENTER.add(-2, 0, 0), CENTER, 2);
            case 315 -> pending = "construct-4-tech-cage.png";
            case 350 -> {
                mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld()
                        .setBlock(BlockPos.containing(CENTER), Blocks.SMOOTH_STONE.defaultBlockState(), 3));
                effect(Kind.MATTER_WEAVE, mc.player.getEyePosition(), CENTER.add(0, .5, 0), 1);
            }
            case 355 -> pending = "construct-5-matter-weave.png";
            case 375 -> mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld()
                    .removeBlock(BlockPos.containing(CENTER), false));
            case 390 -> effect(Kind.MATTER_SHARD, CENTER.add(-1.7, .2, -1.4), CENTER.add(1.2, .8, .5), 1.6F);
            case 391 -> pending = "construct-6-matter-shard.png";
            case 425 -> effect(Kind.MATTER_COLLAPSE, CENTER, CENTER, 3.5F);
            case 430 -> pending = "construct-7-matter-collapse.png";
            case 465 -> effect(Kind.MATTER_SHIELD, CENTER, CENTER.add(0, 0, -1), 3);
            case 469 -> pending = "construct-8-matter-shield.png";
            case 500 -> {
                mc.options.particles().set(ParticleStatus.MINIMAL);
                effect(Kind.TECH_CAGE, CENTER, CENTER, 2);
            }
            case 504 -> pending = "construct-9-tech-minimal.png";
            case 535 -> effect(Kind.MATTER_SHIELD, CENTER, CENTER.add(0, 0, -1), 3);
            case 539 -> pending = "construct-10-matter-minimal.png";
            case 565 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent());
            screenshot.writeToFile(out);
            System.out.println("CONSTRUCT_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
