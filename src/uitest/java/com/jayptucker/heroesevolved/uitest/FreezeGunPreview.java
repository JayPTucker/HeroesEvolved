package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.function.Consumer;

/** Photographs the actual survival trigger, rather than repeatedly invoking a server shot. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class FreezeGunPreview {
    private static boolean started;
    private static int ticks, world, photos, stoppedRounds;
    private static String pending;
    private static Mob target;
    private static final double X = 1550.5, Y = -60, Z = .5;

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (player != null) action.accept(player);
            } catch (Throwable t) { PreviewQuiet.report("FREEZE_GUN FAIL " + t); }
        });
    }

    private static void check(String name, boolean ok) { PreviewQuiet.report("FREEZE_GUN " + (ok ? "PASS " : "FAIL ") + name); }
    private static void photo(String name) { pending = "freeze-gun-" + name + ".png"; }
    private static void trigger(Minecraft mc, boolean down) {
        var key = Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().equals("key.tacz.shoot.desc")).findFirst();
        if (key.isEmpty()) { check("TaCZ trigger registered", false); return; }
        key.get().setDown(down);
    }
    private static void reset(ServerPlayer p) {
        if (target != null) { CryoService.releaseEntity(target); target.discard(); }
        p.setItemInHand(InteractionHand.MAIN_HAND, PowerWeapons.gun(PowerWeapons.COLD_GUN));
        p.teleportTo(p.server.overworld(), X, Y, Z, 0, 2);
        p.setDeltaMovement(Vec3.ZERO); p.removeAllEffects();
        target = EntityType.HUSK.create(p.serverLevel());
        target.moveTo(X, Y, Z + 9, 180, 0); target.setNoAi(true); target.setPersistenceRequired();
        target.getPersistentData().putBoolean(com.jayptucker.heroesevolved.gifted.GiftedMobs.ROLLED, true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); target.setHealth(200);
        p.serverLevel().addFreshEntity(target);
        System.out.println("FREEZE_GUN staged " + p.level().dimension().location() + " on " + p.level().getBlockState(p.blockPosition().below()));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.freezegun")) return;
        var mc = Minecraft.getInstance();
        if (!started && ++ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true; mc.options.pauseOnLostFocus = false; mc.options.guiScale().set(2);
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("FreezeGun-" + System.currentTimeMillis(),
                    new LevelSettings("Freeze Gun", GameType.SURVIVAL, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        mc.getToasts().clear();
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        switch (++world) {
            case 160 -> server(mc, p -> {
                PrologueService.skip(p); CryoChamberService.release(p);
                for (int x = 95; x <= 98; x++) for (int z = -1; z <= 1; z++) p.server.overworld().setChunkForced(x, z, true);
                p.server.overworld().setDayTime(6000); reset(p);
            });
            case 220 -> { mc.options.setCameraType(CameraType.FIRST_PERSON); photo("01-held"); }
            case 230 -> trigger(mc, true);
            case 237 -> photo("02-blast-start");
            case 259 -> { photo("03-frozen"); server(mc, p -> {
                check("held trigger consumes charge", PowerWeapons.rounds(p.getMainHandItem()) < 32);
                check("held blast freezes target", CryoService.isFrozen(target));
            }); }
            case 261 -> { trigger(mc, false); server(mc, p -> stoppedRounds = PowerWeapons.rounds(p.getMainHandItem())); }
            case 278 -> { photo("04-released"); server(mc, p -> check("release stops charge use", PowerWeapons.rounds(p.getMainHandItem()) == stoppedRounds)); }
            case 285 -> { SetPieceClient.clear(); server(mc, FreezeGunPreview::reset); }
            case 310 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.options.hideGui = true;
                TempestCamera.fix(new Vec3(X - 4.5, Y + 2.2, Z + 1.5), new Vec3(X, Y + 1, Z + 3)); trigger(mc, true);
            }
            case 319 -> photo("05-side");
            case 337 -> photo("06-side-frozen");
            case 340 -> trigger(mc, false);
            case 350 -> { server(mc, FreezeGunPreview::reset); TempestCamera.fix(new Vec3(X - 3.5, Y + 2, Z + 12), new Vec3(X, Y + 1.2, Z + 2)); }
            case 375 -> trigger(mc, true);
            case 383 -> photo("07-front");
            case 397 -> { trigger(mc, false); mc.options.particles().set(ParticleStatus.MINIMAL); }
            case 405 -> server(mc, FreezeGunPreview::reset);
            case 430 -> { TempestCamera.fix(new Vec3(X - 4.5, Y + 2.2, Z + 1.5), new Vec3(X, Y + 1, Z + 3)); trigger(mc, true); }
            case 441 -> photo("08-minimal");
            case 455 -> { trigger(mc, false); mc.options.particles().set(ParticleStatus.ALL); server(mc, p -> { reset(p); p.serverLevel().setDayTime(18000); }); }
            case 480 -> trigger(mc, true);
            case 491 -> photo("09-night");
            case 503 -> { trigger(mc, false); server(mc, p -> { reset(p); p.serverLevel().setDayTime(6000); p.connection.teleport(X, Y, Z, 0, 24); target.discard(); }); }
            case 510 -> { TempestCamera.fix(null, null); mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false; }
            case 530 -> trigger(mc, true);
            case 557 -> photo("10-terrain");
            case 570 -> trigger(mc, false);
            case 585 -> { if (CompatPreviewJei.ready()) CompatPreviewJei.showFreezeGun(); else check("JEI runtime ready", false); }
            case 598 -> photo("11-recipe");
            case 610 -> { check("all eleven photographs", photos == 11); PreviewQuiet.quit(); }
            default -> {}
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            var path = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(path.getParent()); image.writeToFile(path); photos++;
            System.out.println("FREEZE_GUN_CAPTURE " + pending);
        } catch (Exception ex) { PreviewQuiet.report("FREEZE_GUN FAIL screenshot " + ex); }
        pending = null;
    }
}
