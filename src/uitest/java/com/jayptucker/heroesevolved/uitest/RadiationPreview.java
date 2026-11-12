package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.intro.PowerIntroHistory;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.radiation.RadiationLeap;
import com.jayptucker.heroesevolved.radiation.RadiationService;
import com.jayptucker.heroesevolved.radiation.RadiationSickness;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Casts each Induced Radioactivity power at dusk against still targets and photographs it mid-effect. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class RadiationPreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;
    private static final List<LivingEntity> SICK = new ArrayList<>();

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void place(ServerPlayer p, double x, double z, float yaw, float pitch) {
        p.teleportTo(p.serverLevel(), x + 0.5, -60, z + 0.5, yaw, pitch);
    }

    private static LivingEntity spawn(ServerPlayer p, EntityType<? extends LivingEntity> type, double x, double z, boolean invulnerable) {
        var mob = type.create(p.serverLevel());
        if (mob instanceof net.minecraft.world.entity.Mob m) { m.setNoAi(true); m.setPersistenceRequired(); }
        mob.setInvulnerable(invulnerable);
        mob.moveTo(x + 0.5, -60, z + 0.5, 180, 0);
        p.serverLevel().addFreshEntity(mob);
        return mob;
    }

    private static volatile int watcher = -1;

    /** An invisible stand to watch the blast from (only once the caster no longer needs to move). */
    private static void stand(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        var stand = new net.minecraft.world.entity.decoration.ArmorStand(EntityType.ARMOR_STAND, p.serverLevel());
        stand.setInvisible(true); stand.setNoGravity(true); stand.setInvulnerable(true);
        stand.moveTo(x, y, z, yaw, pitch); stand.setYHeadRot(yaw);
        p.serverLevel().addFreshEntity(stand);
        watcher = stand.getId();
    }

    private static void look(Minecraft mc) {
        var e = watcher >= 0 ? mc.level.getEntity(watcher) : null;
        if (e != null) mc.setCameraEntity(e);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.radiation")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Radiation-" + System.currentTimeMillis(),
                    new LevelSettings("Radiation preview", GameType.SURVIVAL, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(91L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (worldTicks >= 453 && worldTicks <= 488 && worldTicks % 2 == 1) pending = String.format("radiation-8-blast-%02d.png", worldTicks - 453);
        switch (++worldTicks) {
            case 200 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(13300);
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, PowerIntroHistory.empty().with(ModAbilities.RADIATION_ID));
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                        .assignDormant(ModAbilities.RADIATION_ID).unlock(ModAbilities.RADIATION_ID));
                PlayerProgressionService.setLevel(p, 5);
                p.getAbilities().invulnerable = true; p.onUpdateAbilities();
                place(p, 300, 0, 0, 6);
                for (double[] at : new double[][] {{300, 5}, {302, 6}, {298, 7}, {300, 12}}) spawn(p, EntityType.PILLAGER, at[0], at[1], true);
            });
            // Radiant Heat: the cone from the palms, from behind.
            case 228 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 230 -> onServer(mc, p -> { PlayerEnergyService.restore(p, 999); RadiationService.radiantTouch(p, 5); });
            case 231 -> pending = "radiation-1-cone-a.png";
            case 233 -> pending = "radiation-1-cone-b.png";
            case 236 -> pending = "radiation-1-cone-c.png";
            // Fission Leap: the launch, the slam and the ground it leaves.
            case 248 -> onServer(mc, p -> place(p, 300, -3, 0, 15));
            case 250 -> onServer(mc, p -> RadiationLeap.launch(p, 5));
            case 251 -> pending = "radiation-2-leap-a.png";
            case 254 -> pending = "radiation-2-leap-b.png";
            case 261 -> pending = "radiation-2-leap-air.png";
            case 263 -> onServer(mc, p -> { p.setXRot(60); RadiationLeap.slam(p); });
            case 268 -> pending = "radiation-2-slam-a.png";
            case 271 -> pending = "radiation-2-slam-b.png";
            case 276 -> pending = "radiation-2-slam-c.png";
            case 292 -> pending = "radiation-2-zone.png";
            // Critical Core: the glow and the HUD gauge.
            case 300 -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                onServer(mc, p -> { place(p, 306, 0, 0, 12); RadiationService.addMass(p, 85 - RadiationService.mass(p)); }); }
            case 315 -> pending = "radiation-3-critical.png";
            // Meltdown Beam, held, in first person and from behind.
            case 320 -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> { place(p, 300, -2, 0, 4); RadiationService.startEmission(p, 5); }); }
            case 329 -> pending = "radiation-4-beam-a.png";
            case 332 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 336 -> pending = "radiation-4-beam-b.png";
            case 343 -> onServer(mc, RadiationService::releaseEmission);
            // EMP from behind.
            case 355 -> onServer(mc, p -> { place(p, 300, -3, 0, 20); RadiationService.emp(p); });
            case 357 -> pending = "radiation-5-emp-a.png";
            case 360 -> pending = "radiation-5-emp-b.png";
            case 365 -> pending = "radiation-5-emp-c.png";
            // A chain reaction: a sick cow dies among others.
            case 375 -> onServer(mc, p -> {
                place(p, 330, -6, 0, 18);
                SICK.clear();
                for (double[] at : new double[][] {{330, 0}, {332, 1}, {328, 1}, {331, -1}}) SICK.add(spawn(p, EntityType.COW, at[0], at[1], false));
                RadiationSickness.dose(SICK.getFirst(), p, 3, 200);
            });
            case 380 -> onServer(mc, p -> SICK.getFirst().hurt(p.damageSources().indirectMagic(p, p), 100));
            case 381 -> pending = "radiation-6-chain-a.png";
            case 383 -> pending = "radiation-6-chain-b.png";
            // Critical Mass: the caster rises glowing, then the blast, watched from 55 blocks off, frame by frame.
            case 395 -> onServer(mc, p -> { place(p, 360, 0, 180, 10); stand(p, 360.5, -50, 55.5, 180, 12);
                RadiationService.addMass(p, 100 - RadiationService.mass(p) - 1); RadiationService.beginDetonation(p, 5, true); });
            case 410 -> pending = "radiation-7-charge-a.png";
            case 440 -> pending = "radiation-7-charge-b.png";
            case 451 -> look(mc);
            case 500 -> pending = "radiation-7-cloud-a.png";
            case 550 -> pending = "radiation-7-cloud-b.png";
            case 630 -> pending = "radiation-7-cloud-c.png";
            case 670 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("RADIATION_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
