package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.speed.SpeedWindArms;
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
 * Superspeed's Wind Arms, blowing pillagers away, lifting the speedster, and clearing a smoke screen and a lingering cloud
 * while turning arrows back, photographed 400+ blocks from spawn.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class WindArmsPreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;
    private static volatile int watcher = -1;
    private static final List<LivingEntity> MOBS = new ArrayList<>();

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static <T extends Mob> T spawn(ServerPlayer p, EntityType<T> type, double x, double z) {
        T mob = type.create(p.serverLevel());
        mob.setPersistenceRequired();
        mob.moveTo(x, -60, z, 180, 0);
        p.serverLevel().addFreshEntity(mob);
        MOBS.add(mob);
        return mob;
    }

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

    private static void shot(String name) { pending = "arms-" + name + ".png"; }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.windarms")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Arms-" + System.currentTimeMillis(),
                    new LevelSettings("Wind Arms preview", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(31L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        worldTicks++;
        if (worldTicks >= 240 && worldTicks < 300 && worldTicks % 4 == 0) { int t0 = worldTicks; double cy = mc.player.getY(); onServer(mc, p -> System.out.println("ARMS_LIFT t=" + t0 + " client=" + cy + " server=" + p.getY() + " h=" + SpeedWindArms.heightAboveGround(p))); }
        if (worldTicks >= 336 && worldTicks < 348) {
            // A smoke bomb going off: a ball of long-lived campfire smoke in front of the speedster.
            var r = mc.level.random;
            for (int i = 0; i < 70; i++) {
                double a = r.nextDouble() * Math.PI * 2, b = r.nextDouble() * 2 - 1, d = Math.cbrt(r.nextDouble()) * 2.6;
                double rr = Math.sqrt(1 - b * b) * d;
                mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
                        480.5 + Math.cos(a) * rr, -58.2 + b * d * .7, 706.5 + Math.sin(a) * rr, 0, .005, 0);
            }
        }
        switch (worldTicks) {
            case 150 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(5000);
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 120, 4, false, false));
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.SUPERSPEED_ID));
                PlayerAbilityService.replaceWithAbility(p, ModAbilities.SUPERSPEED_ID);
                PlayerProgressionService.setLevel(p, 5);
                PlayerEnergyService.restore(p, 999);
                p.teleportTo(p.serverLevel(), 400.5, -60, 700.5, 0, 0);
                for (double[] at : new double[][] {{400.5, 705}, {398.5, 708}, {402.5, 710}}) spawn(p, EntityType.PILLAGER, at[0], at[1]);
                stand(p, new Vec3(392.5, -58.5, 704.5), -90, 8);
            });
            case 156 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 162 -> { mc.options.hideGui = true; look(mc, true); }
            case 170 -> onServer(mc, SpeedWindArms::start);
            case 173 -> shot("1-push-start");
            case 180 -> shot("1b-push");
            case 190 -> shot("1c-push-later");
            case 196 -> look(mc, false);
            case 200 -> shot("1d-push-behind");
            case 202 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 206 -> shot("1e-push-front");
            case 215 -> onServer(mc, p -> { SpeedWindArms.stop(p); MOBS.forEach(LivingEntity::discard); MOBS.clear(); });
            // Pointed at the ground: a lift, a hover, and a float down.
            case 225 -> onServer(mc, p -> {
                p.teleportTo(p.serverLevel(), 440.5, -60, 700.5, 0, 55);
                stand(p, new Vec3(432.5, -56, 700.5), -90, 10);
            });
            case 230 -> { look(mc, false); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
            case 240 -> onServer(mc, SpeedWindArms::start);
            case 250 -> shot("2-lift-rising");
            case 270 -> shot("2b-lift-hover");
            case 290 -> shot("2c-lift-high");
            case 302 -> onServer(mc, SpeedWindArms::stop);
            case 315 -> shot("2d-floating-down");
            // A smoke screen, a lingering poison cloud and arrows: the arms clear the air and send the arrows back.
            case 330 -> onServer(mc, p -> {
                p.teleportTo(p.serverLevel(), 480.5, -60, 700.5, 0, 0);
                p.setYHeadRot(0);
                var archer = spawn(p, EntityType.PILLAGER, 480.5, 716);
                archer.setNoAi(true);
                var cloud = new net.minecraft.world.entity.AreaEffectCloud(p.serverLevel(), 480.5, -60, 707);
                cloud.setRadius(3); cloud.setDuration(600); cloud.setRadiusPerTick(0);
                cloud.setPotionContents(new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.POISON));
                p.serverLevel().addFreshEntity(cloud);
                stand(p, new Vec3(471.5, -58.5, 705.5), -90, 6);
            });
            case 334 -> { look(mc, true); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
            case 350 -> onServer(mc, SpeedWindArms::start);
            case 352, 358, 364, 370 -> onServer(mc, p -> {
                var archer = MOBS.isEmpty() ? null : MOBS.get(MOBS.size() - 1);
                var arrow = EntityType.ARROW.create(p.serverLevel());
                arrow.setOwner(archer);
                arrow.setPos(480.5 + (worldTicks % 2 == 0 ? .4 : -.4), p.getEyeY(), 712);
                arrow.setDeltaMovement(0, 0, -1.6);
                p.serverLevel().addFreshEntity(arrow);
            });
            case 348 -> shot("3-smoke-before");
            case 354 -> shot("3b-deflect");
            case 360 -> shot("3c-smoke-clearing");
            case 366 -> shot("3d-deflect-again");
            case 376 -> shot("3e-cleared");
            case 380 -> { look(mc, false); mc.options.setCameraType(CameraType.FIRST_PERSON); }
            case 384 -> shot("3f-first-person");
            case 392 -> onServer(mc, p -> { SpeedWindArms.stop(p); MOBS.forEach(LivingEntity::discard); MOBS.clear(); });
            case 400 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("ARMS_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
