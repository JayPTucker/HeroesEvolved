package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.story.elle.CompanyOmens;
import com.jayptucker.heroesevolved.story.elle.ElleEntity;
import com.jayptucker.heroesevolved.story.elle.ElleStoryService;
import com.jayptucker.heroesevolved.story.elle.PrimatechArena;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.nio.file.*;
import java.util.function.Consumer;

/**
 * Elle Bishop, start to finish: a little workshop 400 blocks from spawn whose lamps, wiring and furnace go wrong the day
 * before, her arrival in the dark, then each phase of her fight on Primatech's test floor, every telegraph at its windup
 * and its landing. Run with {@code ./gradlew runClient --init-script tools/elle-preview.init.gradle}; shots land in
 * {@code build/elle-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class ElleBishopPreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;
    private static final BlockPos SHED = new BlockPos(400, -61, 0);

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void elle(Minecraft mc, Consumer<ElleEntity> action) {
        onServer(mc, p -> { ElleEntity e = ElleStoryService.elle(); if (e != null) action.accept(e); });
    }

    /** Puts the player on the test floor (offset from its middle), flying when off the floor. */
    private static void stand(ServerPlayer p, double dx, double dy, double dz, float yaw, float pitch) {
        Vec3 at = PrimatechArena.center().add(dx, dy, dz);
        p.getAbilities().mayfly = dy > 0.5; p.getAbilities().flying = dy > 0.5; p.onUpdateAbilities();
        p.teleportTo((ServerLevel) p.level(), at.x, at.y, at.z, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO);
    }

    /** Puts her on her mark, facing the player, choosing nothing of her own. */
    private static void mark(ElleEntity e, double dx, double dz) {
        Vec3 at = PrimatechArena.center().add(dx, 0, dz);
        e.teleportTo(at.x, at.y, at.z);
        e.holdOff(400);
    }

    private static void shot(String name) { pending = "elle-" + name + ".png"; }

    /** A small workshop: stone walls, lamps on redstone, two copper bulbs, a furnace, a note block and a door. */
    private static void shed(ServerLevel level) {
        BlockPos o = SHED;
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            level.setBlock(o.offset(x, 0, z), Blocks.SPRUCE_PLANKS.defaultBlockState(), 2);
            level.setBlock(o.offset(x, 5, z), Blocks.SPRUCE_PLANKS.defaultBlockState(), 2);
            for (int y = 1; y <= 4; y++) {
                boolean wall = Math.abs(x) == 4 || Math.abs(z) == 4;
                level.setBlock(o.offset(x, y, z), wall ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // Lamps on the north wall, each powered from a redstone block behind it.
        for (int x : new int[] {-2, 0, 2}) {
            // The lamp first, so placing its power lights it.
            level.setBlock(o.offset(x, 4, -4), Blocks.REDSTONE_LAMP.defaultBlockState(), 3);
            level.setBlock(o.offset(x, 4, -5), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        }
        level.setBlock(o.offset(-3, 3, -3), Blocks.WAXED_COPPER_BULB.defaultBlockState().setValue(BlockStateProperties.LIT, true), 2);
        level.setBlock(o.offset(3, 3, -3), Blocks.WAXED_COPPER_BULB.defaultBlockState().setValue(BlockStateProperties.LIT, true), 2);
        level.setBlock(o.offset(-2, 1, -3), Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), 3);
        level.setBlock(o.offset(-1, 1, -3), Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), 3);
        level.setBlock(o.offset(2, 1, -3), Blocks.NOTE_BLOCK.defaultBlockState(), 3);
        level.setBlock(o.offset(1, 1, -3), Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
        level.setBlock(o.offset(0, 1, 4), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH), 3);
        level.setBlock(o.offset(0, 2, 4), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.elle")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Elle-" + System.currentTimeMillis(),
                    new LevelSettings("Elle preview", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(614L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        switch (++worldTicks) {
            case 200 -> {
                mc.options.hideGui = true;
                onServer(mc, p -> {
                    p.serverLevel().setDayTime(18000);
                    p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                            .assignDormant(ModAbilities.QUAKE_ID).unlock(ModAbilities.QUAKE_ID));
                    p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.QUAKE_ID));
                    PlayerProgressionService.setLevel(p, 5);
                    p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 400, 4, false, false));
                    p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 400, 3, false, false));
                    p.addEffect(new MobEffectInstance(MobEffects.SATURATION, 20 * 400, 1, false, false));
                    // 400 blocks from spawn, well clear of the bunker.
                    p.serverLevel().getChunk(SHED);
                    shed(p.serverLevel());
                    p.teleportTo(p.serverLevel(), SHED.getX() + 0.5, SHED.getY() + 1, SHED.getZ() + 2.5, 180, 8);
                });
            }
            // ---- The day before ----
            case 240 -> shot("1-omens-before");
            case 244 -> onServer(mc, p -> System.out.println("ELLE_PREVIEW flicker " + CompanyOmens.flicker(p)));
            case 246 -> shot("1-omens-flicker");
            case 250 -> shot("1-omens-flicker-b");
            case 270 -> onServer(mc, p -> System.out.println("ELLE_PREVIEW misfire " + CompanyOmens.misfire(p)));
            case 273 -> shot("1-omens-misfire");
            case 300 -> onServer(mc, p -> System.out.println("ELLE_PREVIEW machines " + CompanyOmens.machines(p)));
            case 303 -> shot("1-omens-machines");
            // ---- Her arrival: outside, in the field ----
            case 340 -> onServer(mc, p -> p.teleportTo(p.serverLevel(), SHED.getX() + 0.5, SHED.getY() + 1, SHED.getZ() + 9.5, 0, 6));
            case 350 -> onServer(mc, p -> System.out.println("ELLE_PREVIEW arrive " + ElleStoryService.arrive(p)));
            case 372 -> shot("2-arrival-dark");
            case 389 -> shot("2-arrival-dart");
            // ---- Primatech's test floor: the room from up by the booth ----
            case 425 -> {
                elle(mc, el -> { mark(el, 0, -5); el.holdSurges(2000); });
                onServer(mc, p -> stand(p, 0, 7.2, 11, 180, 30));
                System.out.println("ELLE_PREVIEW in arena=" + (mc.level.dimension() == PrimatechArena.DIMENSION));
            }
            case 440 -> shot("3-floor-overview");
            // ---- Field Test: she stands by the west bank, the pylon out of the line of sight ----
            case 450 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> stand(p, 4, 0, 6, 127, 6));
            }
            case 455 -> elle(mc, el -> { mark(el, -6, 2); el.castNow("arc_bolt"); });
            case 466 -> shot("4-field-arc-aim");
            case 472 -> shot("4-field-arc-bolt");
            case 490 -> onServer(mc, p -> stand(p, -1.5, 0, 4.5, 116, 22));
            case 493 -> elle(mc, el -> { mark(el, -6, 2); el.castNow("ground_current"); });
            case 510 -> shot("4-field-ground-mark");
            case 519 -> shot("4-field-ground-ring");
            case 540 -> { mc.options.setCameraType(CameraType.FIRST_PERSON); onServer(mc, p -> stand(p, -4, 0, 9, 160, 6)); }
            case 543 -> elle(mc, el -> { mark(el, -6, 2); el.siphonFrom(5); });
            case 575 -> shot("4-field-siphon");
            case 582 -> onServer(mc, p -> PrimatechArena.setBreaker((ServerLevel) p.level(), 5, false));
            case 586 -> shot("4-field-backfire");
            case 596 -> shot("4-field-stunned");
            // ---- Overcharged ----
            case 660 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> stand(p, 4, 0, 6, 127, 8));
                elle(mc, el -> { mark(el, -6, 2); el.setHealth(el.getMaxHealth() * 0.6F); });
            }
            case 664 -> shot("5-overcharged-burst");
            case 690 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> stand(p, 0, 6.8, 10, 180, 50));
            }
            case 712 -> shot("5-overcharged-conduit-warn");
            case 726 -> shot("5-overcharged-conduit-surge");
            case 740 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> { PrimatechArena.setBreaker((ServerLevel) p.level(), 4, false); stand(p, 4, 0, 4, 76, 14); });
            }
            case 745 -> elle(mc, el -> { mark(el, -6, 2); el.castNow("reboot"); });
            case 780 -> shot("5-overcharged-reboot");
            case 820 -> onServer(mc, p -> stand(p, 5, 0, 7, 114, 8));
            case 823 -> elle(mc, el -> { mark(el, -6, 2); el.castNow("ball_lightning"); });
            case 832 -> shot("5-overcharged-ball-forming");
            case 846 -> shot("5-overcharged-ball-rolling");
            // ---- Blackout ----
            case 900 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> stand(p, 5, 0, 8, 118, -18));
                elle(mc, el -> { mark(el, -6, 2); el.setHealth(el.getMaxHealth() * 0.25F); });
            }
            case 908 -> shot("6-blackout-lights-out");
            case 940 -> { onServer(mc, p -> stand(p, 5, 0, 8, 118, 6)); }
            case 946 -> shot("6-blackout-dark");
            case 950 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); onServer(mc, p -> stand(p, 5, 0, 8, 130, -8)); }
            case 954 -> elle(mc, el -> { mark(el, -6, 2); el.castNow("storm"); });
            case 976 -> shot("6-blackout-storm-marks");
            case 986 -> shot("6-blackout-storm-strikes");
            // ---- She falls ----
            case 1010 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> stand(p, 1, 0, 6, 124, 10));
                elle(mc, el -> mark(el, -5, 2));
            }
            case 1016 -> onServer(mc, p -> {
                ElleEntity el = ElleStoryService.elle();
                if (el != null) { el.invulnerableTime = 0; el.hurt(p.damageSources().playerAttack(p), 4000); }
            });
            case 1024 -> shot("7-capacitor");
            case 1034 -> shot("7-capacitor-caught");
            case 1130 -> shot("8-home");
            case 1140 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("ELLE_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
