package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.intro.PowerIntroHistory;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
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

/** Casts each Radiation power at dusk against still targets and photographs it mid-effect. */
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
            // Radiant Heat: the cone from the palms.
            case 230 -> onServer(mc, p -> { PlayerEnergyService.restore(p, 999); RadiationService.radiantTouch(p, 5); });
            case 231 -> pending = "radiation-1-cone-a.png";
            case 233 -> pending = "radiation-1-cone-b.png";
            // Contamination, seen from behind and above, then the patch it leaves.
            case 250 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { place(p, 300, -2, 0, 35); RadiationService.radiationPulse(p, 5, false); }); }
            case 253 -> pending = "radiation-2-pulse.png";
            case 268 -> pending = "radiation-2-zone.png";
            // Critical Core: the glow and the HUD gauge.
            case 285 -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                onServer(mc, p -> { place(p, 306, 0, 0, 12); RadiationService.addMass(p, 85 - RadiationService.mass(p)); }); }
            case 300 -> pending = "radiation-3-critical.png";
            // Controlled Emission, held, in first person.
            case 305 -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> { place(p, 300, -2, 0, 4); RadiationService.startEmission(p, 5); }); }
            case 314 -> pending = "radiation-4-beam-a.png";
            case 320 -> pending = "radiation-4-beam-b.png";
            case 328 -> onServer(mc, RadiationService::releaseEmission);
            // EMP from behind.
            case 340 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { place(p, 300, -3, 0, 20); RadiationService.emp(p); }); }
            case 342 -> pending = "radiation-5-emp-a.png";
            case 345 -> pending = "radiation-5-emp-b.png";
            // A chain reaction: a sick cow dies among others.
            case 360 -> onServer(mc, p -> {
                place(p, 330, -6, 0, 18);
                SICK.clear();
                for (double[] at : new double[][] {{330, 0}, {332, 1}, {328, 1}, {331, -1}}) SICK.add(spawn(p, EntityType.COW, at[0], at[1], false));
                RadiationSickness.dose(SICK.getFirst(), p, 3, 200);
            });
            case 365 -> onServer(mc, p -> SICK.getFirst().hurt(p.damageSources().indirectMagic(p, p), 100));
            case 366 -> pending = "radiation-6-chain-a.png";
            case 368 -> pending = "radiation-6-chain-b.png";
            // Exploding Man: the charge and the blast.
            case 385 -> onServer(mc, p -> { place(p, 360, 0, 180, 10); RadiationService.addMass(p, 100 - RadiationService.mass(p) - 1); RadiationService.beginDetonation(p, 5, true); });
            case 425 -> pending = "radiation-7-charge.png";
            case 447 -> { mc.options.setCameraType(CameraType.FIRST_PERSON); onServer(mc, p -> place(p, 360, 50, 180, -12)); }
            case 450 -> pending = "radiation-7-blast.png";
            case 480 -> pending = "radiation-7-cloud.png";
            case 500 -> mc.stop();
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
