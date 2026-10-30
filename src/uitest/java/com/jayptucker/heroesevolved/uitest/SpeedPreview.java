package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.speed.SpeedKit;
import com.jayptucker.heroesevolved.speed.SpeedService;
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

/** Runs for real (the client sprints) and uses each Superspeed power at night against real mobs, photographed mid-effect. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class SpeedPreview {
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

    private static void sprint(Minecraft mc, boolean on) {
        mc.options.keyUp.setDown(on);
        mc.options.keySprint.setDown(on);
        if (mc.player != null) mc.player.setSprinting(on);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.speed")) return;
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
            mc.createWorldOpenFlows().createFreshLevel("Speed-" + System.currentTimeMillis(),
                    new LevelSettings("Speed preview", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        // While running, turn steadily so the trail curves into view from behind.
        if (worldTicks >= 220 && worldTicks < 262) mc.player.setYRot(mc.player.getYRot() + 3.5F);
        switch (++worldTicks) {
            case 200 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(14500);
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                        .assignDormant(ModAbilities.SUPERSPEED_ID).unlock(ModAbilities.SUPERSPEED_ID));
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.SUPERSPEED_ID));
                PlayerProgressionService.setLevel(p, 5);
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 120, 4, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 120, 0, false, false));
                PlayerEnergyService.restore(p, 999);
                place(p, 300, 0, -90, 5);
            });
            // Running: the trail, lightning on the body, afterimages, then the sound barrier. Seen from behind.
            case 215 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); onServer(mc, SpeedService::toggle); }
            case 220 -> sprint(mc, true);
            case 232 -> pending = "speed-1-running.png";
            case 245 -> pending = "speed-1-running-fast.png";
            case 258 -> pending = "speed-1-running-barrier.png";
            case 262 -> sprint(mc, false);
            case 270 -> pending = "speed-1-stopped.png";
            // Blitz down a line of pillagers, chaining.
            case 280 -> onServer(mc, p -> {
                place(p, 300, 0, 0, 5);
                for (double[] at : new double[][] {{300, 8}, {303, 11}, {297, 13}, {301, 16}}) spawn(p, EntityType.PILLAGER, at[0], at[1], false);
            });
            case 290 -> onServer(mc, p -> { PlayerEnergyService.restore(p, 999); SpeedKit.blitz(p, 5); });
            case 293 -> pending = "speed-2-blitz.png";
            case 300 -> onServer(mc, SpeedKit::chainNext);
            case 302 -> pending = "speed-2-blitz-chain.png";
            case 306 -> onServer(mc, SpeedKit::chainNext);
            case 308 -> pending = "speed-2-blitz-chain-b.png";
            case 318 -> onServer(mc, p -> clearMobs());
            // Snatch: drops, a zombie's sword, an arrow in flight.
            case 325 -> onServer(mc, p -> {
                place(p, 300, 0, 0, 12);
                for (int i = 0; i < 5; i++) {
                    var drop = new net.minecraft.world.entity.item.ItemEntity(p.serverLevel(), 297.5 + i * 1.5, -59.8, 4.5 + (i % 2) * 2,
                            new net.minecraft.world.item.ItemStack(i % 2 == 0 ? net.minecraft.world.item.Items.DIAMOND : net.minecraft.world.item.Items.GOLD_INGOT, 2));
                    p.serverLevel().addFreshEntity(drop);
                }
                var zombie = spawn(p, EntityType.ZOMBIE, 300, 9, false);
                zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
            });
            case 335 -> pending = "speed-3-before-snatch.png";
            case 337 -> onServer(mc, p -> SpeedKit.snatch(p, 5));
            case 339 -> pending = "speed-3-snatch.png";
            case 350 -> onServer(mc, p -> clearMobs());
            // Cyclone among zombies, from behind and above.
            case 355 -> onServer(mc, p -> {
                place(p, 300, 0, 0, 14);
                for (double[] at : new double[][] {{303, 2}, {297, 3}, {300, 5}, {304, -2}, {296, -3}}) spawn(p, EntityType.ZOMBIE, at[0], at[1], true);
            });
            case 360 -> onServer(mc, p -> SpeedKit.cyclone(p, 5));
            case 380 -> pending = "speed-4-cyclone.png";
            case 400 -> pending = "speed-4-cyclone-b.png";
            case 422 -> pending = "speed-4-cyclone-release.png";
            case 430 -> onServer(mc, p -> clearMobs());
            // Lightspeed down a line, seen from behind.
            case 435 -> onServer(mc, p -> {
                place(p, 300, -20, 0, 8);
                for (double[] at : new double[][] {{300, -14}, {301, -8}, {299, -2}, {300, 4}}) spawn(p, EntityType.PILLAGER, at[0], at[1], false);
            });
            case 445 -> onServer(mc, p -> { PlayerEnergyService.restore(p, 999); SpeedKit.lightspeed(p, 5); });
            case 447 -> pending = "speed-5-lightspeed.png";
            case 452 -> pending = "speed-5-lightspeed-b.png";
            case 470 -> { onServer(mc, p -> clearMobs()); mc.stop(); }
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("SPEED_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
