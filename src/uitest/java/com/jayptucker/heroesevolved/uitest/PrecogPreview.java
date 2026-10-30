package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.precog.Foresight;
import com.jayptucker.heroesevolved.precog.PrecognitionService;
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

/** Uses each Precognition power at night against real mobs and photographs it mid-effect. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class PrecogPreview {
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

    /** An arrow already in flight at the player from {@code (x, z)}. */
    private static void shoot(ServerPlayer p, double x, double z, double y) {
        var arrow = new Arrow(EntityType.ARROW, p.serverLevel());
        arrow.setPos(x, p.getY() + y, z);
        // Aim like an archer: the flattest lob that lands on the chest.
        Vec3 flat = p.position().subtract(arrow.position()).multiply(1, 0, 1).normalize().scale(1.4);
        var chest = p.getBoundingBox().deflate(0.25, 0.6, 0.25).move(0, 0.3, 0);
        for (double lift = -0.2; lift < 0.8; lift += 0.01) {
            arrow.setDeltaMovement(flat.x, lift, flat.z);
            if (Foresight.predictHit(arrow, chest).isPresent()) break;
        }
        p.serverLevel().addFreshEntity(arrow);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.precog")) return;
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
            mc.createWorldOpenFlows().createFreshLevel("Precog-" + System.currentTimeMillis(),
                    new LevelSettings("Precog preview", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        switch (++worldTicks) {
            case 200 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(14500);
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                        .assignDormant(ModAbilities.PRECOGNITION_ID).unlock(ModAbilities.PRECOGNITION_ID));
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.PRECOGNITION_ID));
                PlayerProgressionService.setLevel(p, 5);
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 120, 4, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 120, 4, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 120, 0, false, false));
                place(p, 300, 0, 0, 8);
            });
            // Danger Sense: two shots on their way, a creeper fusing, a skeleton drawing.
            case 230 -> onServer(mc, p -> {
                var creeper = spawn(p, EntityType.CREEPER, 302, 4, false);
                creeper.ignite();
                spawn(p, EntityType.SKELETON, 297, 14, true);
                shoot(p, 296.5, 12.5, 1.5);
                shoot(p, 305.5, 10.5, 2.5);
            });
            case 236 -> pending = "precog-1-danger-sense.png";
            case 252 -> pending = "precog-1-danger-sense-b.png";
            case 262 -> onServer(mc, p -> clearMobs());
            // Premonition: zombies closing in, each with a ghost of where it is about to be.
            case 270 -> onServer(mc, p -> {
                for (double[] at : new double[][] {{296, 12}, {301, 15}, {305, 10}, {299, 18}}) spawn(p, EntityType.ZOMBIE, at[0], at[1], true);
            });
            case 290 -> onServer(mc, p -> { PlayerEnergyService.restore(p, 999); PrecognitionService.startPremonition(p); });
            case 302 -> pending = "precog-2-premonition.png";
            case 312 -> pending = "precog-2-premonition-b.png";
            case 318 -> onServer(mc, p -> clearMobs());
            // Paint the Future on a pillager, from behind.
            case 325 -> {
                onServer(mc, p -> { place(p, 300, 0, 0, 6); spawn(p, EntityType.PILLAGER, 300, 6, false); });
            }
            case 330 -> onServer(mc, PrecognitionService::paint);
            case 345 -> pending = "precog-3-painting.png";
            case 368 -> pending = "precog-3-canvas.png";
            // The ending comes true.
            case 372 -> onServer(mc, p -> MOBS.forEach(m -> m.hurt(p.damageSources().playerAttack(p), m.getMaxHealth() * 0.8F)));
            case 374 -> pending = "precog-4-ending-a.png";
            case 378 -> pending = "precog-4-ending-b.png";
            case 385 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); onServer(mc, p -> clearMobs()); }
            // Saw It Coming: the blow lands where you were. A ghost stays; the attacker is marked.
            case 390 -> onServer(mc, p -> {
                var zombie = spawn(p, EntityType.ZOMBIE, 300, 2, false);
                PrecognitionService.sawItComing(p);
                p.hurt(p.damageSources().mobAttack(zombie), 4);
            });
            case 393 -> pending = "precog-5-saw-it-coming.png";
            case 400 -> onServer(mc, p -> clearMobs());
            // Fate Thread, as a friend would see it.
            case 405 -> onServer(mc, p -> {
                var villager = spawn(p, EntityType.VILLAGER, 303, 5, false);
                AbilityEffects.send(p.serverLevel(), Kind.PRECOG_THREAD, p.getEyePosition().subtract(0, 0.4, 0), villager.getEyePosition().subtract(0, 0.4, 0), 2);
            });
            case 410 -> pending = "precog-6-fate-thread.png";
            case 416 -> onServer(mc, p -> clearMobs());
            // The Future Is Written, with arrows crossed out of the air.
            case 420 -> onServer(mc, p -> {
                Foresight.addClarity(p, 80);
                PlayerEnergyService.restore(p, 999);
                PrecognitionService.writeTheFuture(p);
                spawn(p, EntityType.ZOMBIE, 297, 9, true);
                spawn(p, EntityType.ZOMBIE, 303, 11, true);
            });
            case 423 -> pending = "precog-7-future-written.png";
            case 430 -> onServer(mc, p -> { shoot(p, 300.5, 9.5, 1.5); shoot(p, 296.5, 7.5, 1.6); });
            case 436 -> pending = "precog-7-future-written-b.png";
            case 450 -> { onServer(mc, p -> clearMobs()); mc.stop(); }
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("PRECOG_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
