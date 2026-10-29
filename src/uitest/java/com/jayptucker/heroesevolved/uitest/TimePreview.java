package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.intro.PowerIntroHistory;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.nio.file.*;
import java.util.function.Consumer;

/** Casts each Time Manipulation power through the real key path and photographs the new effects. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class TimePreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void cast(Minecraft mc, AbilitySlot slot, boolean shift) {
        onServer(mc, p -> {
            PlayerEnergyService.restore(p, 999);
            System.out.println("TIME_CAST " + slot + " -> " + AbilityUseService.activateAssignedAction(p, slot, shift));
        });
    }

    private static void place(ServerPlayer p, double x, double z, float yaw, float pitch) {
        p.teleportTo(p.serverLevel(), x + 0.5, -60, z + 0.5, yaw, pitch);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.time")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Time-" + System.currentTimeMillis(),
                    new LevelSettings("Time preview", GameType.SURVIVAL, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(19L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        int w = ++worldTicks;
        // Walk east two blocks a second from 400 on, so a Rewind has a path to unwind.
        if (w >= 400 && w < 700 && w % 20 == 0) onServer(mc, p -> place(p, 300 + (w - 400) / 32.0, 0, -90, 8));
        switch (w) {
            case 200 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); onServer(mc, p -> {
                p.serverLevel().setDayTime(12600);
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, PowerIntroHistory.empty().with(ModAbilities.TIME_MANIPULATION_ID));
                p.setData(ModDataAttachments.TIME_INTRO_SEEN, true);
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                        .assignDormant(ModAbilities.TIME_MANIPULATION_ID).unlock(ModAbilities.TIME_MANIPULATION_ID));
                PlayerProgressionService.setLevel(p, 5);
                place(p, 300, 0, -90, 6);
                for (double[] at : new double[][] {{306, 3}, {307, -2}, {304, -4}}) {
                    var mob = EntityType.PILLAGER.create(p.serverLevel());
                    mob.setNoAi(true); mob.setInvulnerable(true); mob.setPersistenceRequired();
                    mob.moveTo(at[0] + 0.5, -60, at[1] + 0.5, 90, 0);
                    p.serverLevel().addFreshEntity(mob);
                }
            }); }
            // Fold Space.
            case 230 -> cast(mc, AbilitySlot.PRIMARY, false);
            case 231 -> pending = "time-1-fold-a.png";
            case 234 -> pending = "time-1-fold-b.png";
            // Between Seconds: the clock, then the amber dome while it lasts.
            case 260 -> onServer(mc, p -> place(p, 300, 0, -90, 35));
            case 262 -> cast(mc, AbilitySlot.SECONDARY, false);
            case 264 -> pending = "time-2-slow-clock.png";
            case 292 -> pending = "time-2-slow-dome.png";
            case 300 -> cast(mc, AbilitySlot.SECONDARY, true);
            // Still World: the clock, then the frozen violet dome.
            case 320 -> cast(mc, AbilitySlot.TERTIARY, false);
            case 322 -> pending = "time-3-stop-clock.png";
            case 350 -> pending = "time-3-stop-dome.png";
            case 360 -> cast(mc, AbilitySlot.TERTIARY, true);
            // Remember This Moment.
            case 380 -> cast(mc, AbilitySlot.QUINARY, true);
            case 382 -> pending = "time-4-snapshot.png";
            // The Rewind marker, seen from the front: the ghost of where you were fifteen seconds ago.
            // Turn around and look back down the path: your ghost is standing where you were.
            case 700 -> { mc.options.setCameraType(CameraType.FIRST_PERSON); onServer(mc, p -> p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), 90, 12)); }
            case 708 -> System.out.println("TIME_MARKERS " + com.jayptucker.heroesevolved.client.effects.TimeGhosts.markers());
            case 712 -> pending = "time-5-rewind-marker.png";
            // Rewind, from behind.
            case 716 -> onServer(mc, p -> p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), -90, 8));
            case 720 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); cast(mc, AbilitySlot.QUATERNARY, false); }
            case 722 -> pending = "time-6-rewind-a.png";
            case 726 -> pending = "time-6-rewind-b.png";
            case 760 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("TIME_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
