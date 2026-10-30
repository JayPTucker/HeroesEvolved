package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.sonic.SonicService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Arrow;
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

/** Shows Sound Sight and each Sonic Resonance power at night against real mobs, photographed mid-effect. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class SonicPreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;
    private static final List<LivingEntity> MOBS = new ArrayList<>();

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void place(ServerPlayer p, double x, double z, float yaw, float pitch) {
        p.teleportTo(p.serverLevel(), x + 0.5, -60, z + 0.5, yaw, pitch);
    }

    private static <T extends Mob> T spawn(ServerPlayer p, EntityType<T> type, double x, double z, boolean ai) {
        T mob = type.create(p.serverLevel());
        mob.setNoAi(!ai); mob.setPersistenceRequired();
        mob.moveTo(x + 0.5, -60, z + 0.5, 180, 0);
        p.serverLevel().addFreshEntity(mob);
        MOBS.add(mob);
        return mob;
    }

    private static void clearMobs() { MOBS.forEach(LivingEntity::discard); MOBS.clear(); }

    /** A straight shot at the player from {@code (x, z)}. */
    private static void shoot(ServerPlayer p, double x, double z) {
        var arrow = new Arrow(EntityType.ARROW, p.serverLevel());
        arrow.setNoGravity(true);
        arrow.setPos(x, p.getY() + 1.3, z);
        arrow.setDeltaMovement(p.position().add(0, 1.3, 0).subtract(arrow.position()).normalize().scale(0.7));
        p.serverLevel().addFreshEntity(arrow);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.sonic")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = false;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_MOBGRIEFING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Sonic-" + System.currentTimeMillis(),
                    new LevelSettings("Sonic preview", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        switch (++worldTicks) {
            case 200 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(14500);
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                        .assignDormant(ModAbilities.SONIC_RESONANCE_ID).unlock(ModAbilities.SONIC_RESONANCE_ID));
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.SONIC_RESONANCE_ID));
                PlayerProgressionService.setLevel(p, 5);
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 120, 4, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 120, 4, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 120, 0, false, false));
                place(p, 300, 0, 0, 8);
            });
            // Sound Sight: zombies shuffling closer, a cow, chickens, then something explodes.
            case 220 -> onServer(mc, p -> {
                for (double[] at : new double[][] {{296, 12}, {303, 14}, {299, 18}}) spawn(p, EntityType.ZOMBIE, at[0], at[1], true);
                spawn(p, EntityType.COW, 305, 7, true);
                spawn(p, EntityType.CHICKEN, 294, 8, true);
                spawn(p, EntityType.CHICKEN, 295, 9, true);
            });
            case 262 -> pending = "sonic-1-sound-sight.png";
            case 264 -> onServer(mc, p -> p.serverLevel().explode(null, 303.5, -59, 12.5, 2.0F, Level.ExplosionInteraction.NONE));
            case 270 -> pending = "sonic-1-sound-sight-explosion.png";
            // Canary Cry, then the held scream down the line.
            case 280 -> onServer(mc, p -> { PlayerEnergyService.restore(p, 999); SonicService.cry(p, 5, false); });
            case 283 -> pending = "sonic-2-cry.png";
            case 292 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 295 -> onServer(mc, p -> { SonicService.start(p, 5); SonicService.cry(p, 5, true); });
            case 303 -> pending = "sonic-2-scream.png";
            case 310 -> { mc.options.setCameraType(CameraType.FIRST_PERSON); onServer(mc, p -> { SonicService.stop(p); place(p, 300, 0, 0, 8); }); }
            // Resonant Note: the zombies walk to it, then it breaks.
            case 315 -> onServer(mc, p -> SonicService.placeNoteAt(p, p.position().add(1, 1.2, 9), 5));
            case 345 -> pending = "sonic-3-note-lure.png";
            case 360 -> pending = "sonic-3-note-lure-b.png";
            case 362 -> onServer(mc, p -> { SonicService.addResonance(p, 40); SonicService.detonate(p); });
            case 364 -> pending = "sonic-4-note-burst.png";
            case 375 -> onServer(mc, p -> clearMobs());
            // Wall of Sound turning shots back.
            case 380 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> { place(p, 300, 0, 0, 10); SonicService.wall(p, 5); });
            }
            case 384 -> onServer(mc, p -> { shoot(p, 299.5, 9.5); shoot(p, 301.5, 10.5); shoot(p, 300.5, 12.5); });
            case 392 -> pending = "sonic-5-wall.png";
            case 402 -> pending = "sonic-5-wall-b.png";
            case 410 -> onServer(mc, SonicService::dropWall);
            // Crescendo: building, then the wave.
            case 415 -> onServer(mc, p -> {
                for (double[] at : new double[][] {{297, 12}, {302, 15}, {300, 20}}) spawn(p, EntityType.PILLAGER, at[0], at[1], false);
                SonicService.addResonance(p, 80);
                SonicService.startCrescendo(p, 5);
            });
            case 445 -> pending = "sonic-6-crescendo-charge.png";
            case 452 -> onServer(mc, SonicService::releaseCrescendo);
            case 454 -> pending = "sonic-6-crescendo.png";
            case 458 -> pending = "sonic-6-crescendo-b.png";
            case 465 -> onServer(mc, p -> clearMobs());
            // Sonic Vault lands on a shockwave.
            case 470 -> onServer(mc, p -> { place(p, 300, 0, 0, 30); SonicService.vault(p); });
            case 474 -> onServer(mc, p -> { p.teleportTo(p.serverLevel(), p.getX(), -60, p.getZ(), 0, 30); SonicService.cushion(p, 6); });
            case 477 -> pending = "sonic-7-landing.png";
            case 490 -> { onServer(mc, p -> clearMobs()); mc.stop(); }
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("SONIC_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
