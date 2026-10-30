package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.story.sylar.SylarEntity;
import com.jayptucker.heroesevolved.story.sylar.SylarStoryData;
import com.jayptucker.heroesevolved.story.sylar.SylarStoryService;
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

/** Starts a real Sylar hunt and has him use what he has taken, one move at a time, photographed mid-windup and mid-strike. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class SylarPreview {
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

    private static void sylar(Minecraft mc, Consumer<SylarEntity> action) {
        onServer(mc, p -> { SylarEntity s = SylarStoryService.hunter(); if (s != null) action.accept(s); });
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.sylar")) return;
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
            mc.createWorldOpenFlows().createFreshLevel("Sylar-" + System.currentTimeMillis(),
                    new LevelSettings("Sylar preview", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        switch (++worldTicks) {
            case 200 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(12600);
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                        .assignDormant(ModAbilities.ELECTROKINESIS_ID).unlock(ModAbilities.ELECTROKINESIS_ID));
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.ELECTROKINESIS_ID));
                PlayerProgressionService.setLevel(p, 5);
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 200, 3, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 200, 3, false, false));
                var story = SylarStoryData.get(p.getServer());
                for (var id : java.util.List.of(ModAbilities.CRYOKINESIS_ID, ModAbilities.FLIGHT_ID, ModAbilities.TIME_MANIPULATION_ID,
                        ModAbilities.REGENERATION_ID, ModAbilities.QUAKE_ID)) story.learn(id);
                place(p, 300, 0, 0, 2);
            });
            case 220 -> onServer(mc, p -> {
                if (!SylarStoryService.start(p, true)) { System.out.println("SYLAR_PREVIEW no sightline"); return; }
                SylarEntity s = SylarStoryService.hunter();
                s.teleportTo(300.5, -60, 8.5);
                s.hunt(p);
            });
            case 240 -> pending = "sylar-1-hunt-begins.png";
            // Your own lightning, in his hand.
            case 250 -> sylar(mc, s -> s.castNow("chain_lightning"));
            case 256 -> pending = "sylar-2-lightning-windup.png";
            case 263 -> pending = "sylar-2-lightning.png";
            // The pin: both hands, and you held in the air.
            case 280 -> sylar(mc, s -> s.castNow("tk_pin"));
            case 286 -> pending = "sylar-3-pin-windup.png";
            case 300 -> pending = "sylar-3-pinned.png";
            // A cold lance.
            case 330 -> sylar(mc, s -> s.castNow("ice_lance"));
            case 336 -> pending = "sylar-4-ice-windup.png";
            case 342 -> pending = "sylar-4-ice.png";
            // Ground pound, crouched.
            case 360 -> sylar(mc, s -> { s.teleportTo(300.5, -60, 4.5); s.castNow("ground_pound"); });
            case 370 -> pending = "sylar-5-quake-windup.png";
            case 382 -> pending = "sylar-5-quake.png";
            // Phase two: Hunger.
            case 400 -> sylar(mc, s -> { s.teleportTo(300.5, -60, 8.5); s.setHealth(s.getMaxHealth() * 0.45F); });
            case 404 -> pending = "sylar-6-hunger.png";
            // The dive, from above.
            case 420 -> sylar(mc, s -> s.castNow("dive"));
            case 432 -> pending = "sylar-7-dive-rise.png";
            case 442 -> pending = "sylar-7-dive.png";
            // Regeneration: struck down, and getting back up.
            case 460 -> onServer(mc, p -> { SylarEntity s = SylarStoryService.hunter(); if (s != null) s.hurt(p.damageSources().playerAttack(p), 1000); });
            case 470 -> pending = "sylar-8-down.png";
            case 565 -> pending = "sylar-8-revived.png";
            case 580 -> { onServer(mc, p -> SylarStoryService.dismiss(p.getServer())); mc.stop(); }
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("SYLAR_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
