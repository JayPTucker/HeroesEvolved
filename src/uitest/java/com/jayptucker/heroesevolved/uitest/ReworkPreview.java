package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.assembly.MatterAssemblyService;
import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.flight.FlightService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.speed.SpeedKit;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Flight's grounded Tempest, a Superspeed tornado left standing alone, Matter Assembly's new constructs, and the
 * higher Starfall, photographed in daylight in a flat world, staged 400 blocks from spawn (the bunker is at spawn).
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class ReworkPreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;
    private static volatile int watcher = -1;
    private static final List<LivingEntity> MOBS = new ArrayList<>();

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void power(ServerPlayer p, ResourceLocation id, int tier) {
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(id));
        PlayerAbilityService.replaceWithAbility(p, id);
        PlayerProgressionService.setLevel(p, tier);
        PlayerEnergyService.restore(p, 999);
    }

    private static <T extends Mob> T spawn(ServerPlayer p, EntityType<T> type, double x, double z, boolean ai) {
        T mob = type.create(p.serverLevel());
        mob.setNoAi(!ai); mob.setPersistenceRequired();
        mob.moveTo(x, -60, z, 180, 0);
        p.serverLevel().addFreshEntity(mob);
        MOBS.add(mob);
        return mob;
    }

    private static void clearMobs() { MOBS.forEach(LivingEntity::discard); MOBS.clear(); }

    private static void stand(ServerPlayer p, Vec3 at, float yaw, float pitch) {
        var level = p.serverLevel();
        if (watcher >= 0 && level.getEntity(watcher) != null) level.getEntity(watcher).discard();
        var stand = new ArmorStand(EntityType.ARMOR_STAND, level);
        stand.setInvisible(true); stand.setNoGravity(true); stand.setInvulnerable(true);
        stand.moveTo(at.x, at.y, at.z, yaw, pitch);
        stand.setYHeadRot(yaw);
        level.addFreshEntity(stand);
        watcher = stand.getId();
    }

    private static void look(Minecraft mc, boolean fromWatcher) {
        var e = fromWatcher && watcher >= 0 ? mc.level.getEntity(watcher) : null;
        mc.setCameraEntity(e != null ? e : mc.player);
    }

    private static AbilityUseContext matter(ServerPlayer p) { return new AbilityUseContext(p, ModAbilities.MATTER_ASSEMBLY_ID, 5, false); }

    private static void shot(String name) { pending = "rework-" + name + ".png"; }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.rework")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_MOBGRIEFING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Rework-" + System.currentTimeMillis(),
                    new LevelSettings("Rework preview", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(31L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        worldTicks++;
        if (worldTicks % 60 == 0 && worldTicks > 200) onServer(mc, p -> PlayerEnergyService.restore(p, 999));
        // How fast the flyer really goes round their Tempest.
        if (worldTicks >= 250 && worldTicks < 262) System.out.println("TEMPEST_ORBIT t=" + worldTicks + " at " + mc.player.position());
        if (worldTicks == 296 && Boolean.getBoolean("heroes.preview.rework.tempestOnly")) mc.stop();
        switch (worldTicks) {
            // Flight's Tempest: flying high, the tornado touches down on the ground ahead.
            case 200 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(5000);
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 120, 4, false, false));
                power(p, ModAbilities.FLIGHT_ID, 5);
                p.teleportTo(p.serverLevel(), 400.5, -48, 400.5, 0, 10);
                for (double[] at : new double[][] {{396, 414}, {404, 418}, {400, 424}, {394, 409}, {407, 410}}) spawn(p, EntityType.PILLAGER, at[0], at[1], true);
                stand(p, new Vec3(400.5, -52, 372.5), 0, -6);
            });
            case 206 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); onServer(mc, FlightService::startCyclone); }
            // The Tempest's own view: it stands well back and holds still while the flyer circles.
            case 226 -> shot("1-tempest-touchdown");
            case 250 -> shot("1b-tempest");
            case 268 -> shot("1c-tempest-circling");
            case 280 -> onServer(mc, p -> p.setYRot(p.getYRot() + 70));
            case 288 -> shot("1d-tempest-turned");
            case 290 -> onServer(mc, p -> { FlightService.cancelCyclone(p); clearMobs(); });
            // Superspeed: hold Cyclone until the tornado is anchored, let go, and walk away from it.
            case 300 -> onServer(mc, p -> {
                power(p, ModAbilities.SUPERSPEED_ID, 5);
                p.teleportTo(p.serverLevel(), 400.5, -60, 460.5, 0, 4);
                for (double[] at : new double[][] {{404, 462}, {397, 464}}) spawn(p, EntityType.PILLAGER, at[0], at[1], true);
                for (double[] at : new double[][] {{400, 480}, {418, 462}, {384, 470}}) spawn(p, EntityType.COW, at[0], at[1], true);
                stand(p, new Vec3(400.5, -54, 430.5), 0, 4);
            });
            case 306 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); onServer(mc, p -> SpeedKit.cyclone(p, 5, true)); }
            case 312 -> look(mc, true);
            case 330 -> shot("2-cyclone-ring");
            case 345 -> shot("2b-tornado-rising");
            case 400 -> onServer(mc, SpeedKit::releaseCyclone);
            case 402 -> { look(mc, false); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.player.setYRot(180); mc.options.keyUp.setDown(true); }
            case 425 -> { mc.options.keyUp.setDown(false); mc.player.setYRot(0); }
            case 430 -> shot("2c-walked-away");
            case 434 -> look(mc, true);
            case 440 -> shot("2d-standing-alone");
            case 470 -> { look(mc, false); onServer(mc, p -> clearMobs()); }
            // Matter Assembly: Foldwall, a cage, the Sentinel Engine, and Skywalk over thin air.
            case 480 -> onServer(mc, p -> {
                power(p, ModAbilities.MATTER_ASSEMBLY_ID, 5);
                p.teleportTo(p.serverLevel(), 400.5, -60, 520.5, 0, 10);
                spawn(p, EntityType.PILLAGER, 400.5, 532.5, false);
                stand(p, new Vec3(392.5, -56, 512.5), -40, 18);
            });
            case 486 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); onServer(mc, p -> MatterAssemblyService.activate(matter(p), "barricade")); }
            case 490 -> look(mc, true);
            case 494 -> shot("3-foldwall-rising");
            case 506 -> shot("3b-foldwall");
            case 510 -> onServer(mc, p -> { p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, MOBS.getFirst().position().add(0, 1, 0));
                MatterAssemblyService.dismantle(p); });
            case 514 -> onServer(mc, p -> MatterAssemblyService.activate(matter(p), "cage"));
            case 526 -> shot("3c-cage");
            case 540 -> onServer(mc, p -> {
                MatterAssemblyService.dismantle(p);
                clearMobs();
                for (double[] at : new double[][] {{410, 534}, {390, 536}, {402, 540}}) spawn(p, EntityType.PILLAGER, at[0], at[1], false);
                MatterAssemblyService.activate(matter(p), "cathedral");
            });
            case 552 -> shot("3d-sentinels");
            case 570 -> shot("3e-sentinels-firing");
            case 580 -> { look(mc, false); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
            case 584 -> shot("3f-sentinels-behind");
            case 600 -> onServer(mc, p -> {
                MatterAssemblyService.dismantle(p);
                clearMobs();
                p.teleportTo(p.serverLevel(), 440.5, -52, 520.5, 0, 30);
                stand(p, new Vec3(448.5, -50, 516.5), 60, 20);
                MatterAssemblyService.activate(matter(p), "causeway");
            });
            case 604 -> mc.options.keyUp.setDown(true);
            case 618 -> { look(mc, true); shot("3g-skywalk"); }
            case 626 -> { mc.options.keyUp.setDown(false); shot("3h-skywalk"); }
            // Starfall from high in the sky.
            case 640 -> { look(mc, false); onServer(mc, p -> {
                p.teleportTo(p.serverLevel(), 400.5, -60, 580.5, 0, -18);
                stand(p, new Vec3(380.5, -54, 600.5), -110, -16);
            }); }
            case 650 -> { look(mc, true); SetPieceClient.play(Piece.SUNFALL, new Vec3(400.5, -60, 580.5), new Vec3(400.5, -60, 604.5), 5, -1, 11); }
            case 656 -> shot("4-starfall-high");
            case 666 -> shot("4b-starfall");
            case 676 -> shot("4c-starfall-low");
            case 684 -> shot("4d-starfall-impact");
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
            System.out.println("REWORK_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
