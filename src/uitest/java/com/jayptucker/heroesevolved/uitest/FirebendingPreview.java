package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.pyro.PyroCombat;
import com.jayptucker.heroesevolved.pyro.PyroService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Pillager;
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
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Throws every firebending strike, kick and dodge in a flat world well away from the spawn bunker, from behind, the
 * front and first person, and saves shots to {@code build/firebending-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class FirebendingPreview {
    private static final List<Mob> ACTORS = new ArrayList<>();
    private static boolean started;
    private static int startup, age;
    private static String capture;

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    private static void reset(ServerPlayer player, int x, float pitch) {
        PyroService.forget(player);
        CooldownService.clearAll(player);
        PlayerEnergyService.restore(player, 999);
        ACTORS.forEach(Mob::discard);
        ACTORS.clear();
        player.serverLevel().setDayTime(6000);
        player.teleportTo(player.serverLevel(), x + .5, -60, .5, 0, pitch);
        player.setDeltaMovement(Vec3.ZERO);
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
    }

    private static Pillager actor(ServerPlayer player, double dx, double dz) {
        Pillager actor = EntityType.PILLAGER.create(player.serverLevel());
        actor.setNoAi(true);
        actor.setPersistenceRequired();
        float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        actor.moveTo(player.getX() + dx, -60, player.getZ() + dz, yaw, 0);
        actor.setYHeadRot(yaw);
        actor.yBodyRot = yaw;
        actor.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
        actor.setHealth(400);
        player.serverLevel().addFreshEntity(actor);
        ACTORS.add(actor);
        return actor;
    }

    private static void look(ServerPlayer p) {
        if (!ACTORS.isEmpty()) p.lookAt(EntityAnchorArgument.Anchor.EYES, ACTORS.getFirst().getBoundingBox().getCenter());
    }

    private static void turn(ServerPlayer p, float yaw) {
        p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), p.getYRot() + yaw, p.getXRot());
        p.setYHeadRot(p.getYRot());
        p.yBodyRot = p.getYRot();
    }

    private static void strike(ServerPlayer p) {
        System.out.println("FIRE_PREVIEW " + PyroCombat.next(p) + " " + PyroCombat.strike(p));
    }

    private static void dodge(ServerPlayer p, PyroCombat.Dodge dodge) {
        System.out.println("FIRE_PREVIEW " + dodge + " " + PyroCombat.dodge(p, dodge));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.firebending")) return;
        var mc = Minecraft.getInstance();
        if (!started && ++startup >= 35 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.fov().set(78);
            mc.options.renderDistance().set(6);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Firebending-" + System.currentTimeMillis(),
                    new LevelSettings("Firebending Preview", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(73L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (age >= 100) mc.getToasts().clear();
        switch (++age) {
            case 110 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> {
                    CryoChamberService.release(p);
                    p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                            .assignDormant(ModAbilities.PYROKINESIS_ID).unlock(ModAbilities.PYROKINESIS_ID));
                    p.setData(ModDataAttachments.POWER_INTRO_HISTORY,
                            p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.PYROKINESIS_ID));
                    PlayerProgressionService.setLevel(p, 5);
                    p.setGameMode(GameType.SURVIVAL);
                    reset(p, 210, 0);
                });
            }
            // ---------------------------------------------------------- The combo, from behind
            case 140 -> server(mc, p -> { reset(p, 210, 0); actor(p, 3, 6); look(p); });
            case 150 -> server(mc, FirebendingPreview::strike);
            case 152 -> capture = "01-jab.png";
            case 154 -> capture = "02-jab-landing.png";
            case 157 -> server(mc, FirebendingPreview::strike);
            case 159 -> capture = "03-cross.png";
            case 164 -> server(mc, FirebendingPreview::strike);
            case 165 -> server(mc, p -> turn(p, 70));
            case 167 -> capture = "04-roundhouse.png";
            case 170 -> capture = "05-roundhouse-late.png";
            // ---------------------------------------------------------- The roundhouse from the front
            case 190 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                server(mc, p -> reset(p, 240, 0));
            }
            case 200 -> server(mc, FirebendingPreview::strike);
            case 206 -> server(mc, FirebendingPreview::strike);
            case 212 -> server(mc, FirebendingPreview::strike);
            case 214 -> capture = "06-roundhouse-front.png";
            case 217 -> capture = "07-roundhouse-front-late.png";
            // ---------------------------------------------------------- Sweep kick, crouched
            case 235 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> { reset(p, 270, 25); actor(p, 2.5, .5); actor(p, -2.5, 1.5); actor(p, .5, 3.2); });
            }
            case 243 -> mc.options.keyShift.setDown(true);
            case 250 -> server(mc, FirebendingPreview::strike);
            case 252 -> capture = "08-sweep.png";
            case 255 -> capture = "09-sweep-late.png";
            case 262 -> mc.options.keyShift.setDown(false);
            // ---------------------------------------------------------- Jet step, behind and in front
            case 275 -> server(mc, p -> reset(p, 300, 0));
            case 285 -> server(mc, p -> dodge(p, PyroCombat.Dodge.LEFT));
            case 287 -> capture = "10-jet-step.png";
            case 290 -> capture = "11-jet-step-late.png";
            case 300 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 308 -> server(mc, p -> dodge(p, PyroCombat.Dodge.RIGHT));
            case 310 -> capture = "12-jet-step-front.png";
            // ---------------------------------------------------------- Flip kick
            case 330 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> { reset(p, 330, 0); actor(p, 1.6, 3); });
            }
            case 340 -> server(mc, p -> dodge(p, PyroCombat.Dodge.BACK));
            case 342 -> capture = "13-flip.png";
            case 345 -> capture = "14-flip-mid.png";
            case 348 -> capture = "15-flip-late.png";
            case 365 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                server(mc, p -> reset(p, 360, 0));
            }
            case 375 -> server(mc, p -> dodge(p, PyroCombat.Dodge.BACK));
            case 378 -> capture = "16-flip-front.png";
            case 381 -> capture = "17-flip-front-mid.png";
            // ---------------------------------------------------------- Counterflame
            case 400 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> { reset(p, 390, 0); actor(p, 3, 6); look(p); });
            }
            case 410 -> server(mc, p -> dodge(p, PyroCombat.Dodge.LEFT));
            case 411 -> server(mc, p -> {
                p.invulnerableTime = 0;
                p.hurt(p.damageSources().mobAttack(ACTORS.getFirst()), 4);
                System.out.println("FIRE_PREVIEW loaded " + PyroCombat.isLoaded(p));
            });
            case 414 -> capture = "18-counterflame.png";
            case 420 -> server(mc, FirebendingPreview::look);
            case 422 -> capture = "19-loaded-fist.png";
            case 424 -> server(mc, FirebendingPreview::strike);
            case 426 -> capture = "20-fireball.png";
            case 428 -> capture = "21-fireball-landing.png";
            // ---------------------------------------------------------- Blue fire
            case 445 -> server(mc, p -> { reset(p, 420, 0); actor(p, 3, 6); look(p); PyroService.addHeat(p, 100); });
            case 455 -> server(mc, FirebendingPreview::strike);
            case 457 -> capture = "22-blue-jab.png";
            case 461 -> server(mc, FirebendingPreview::strike);
            case 467 -> server(mc, FirebendingPreview::strike);
            case 468 -> server(mc, p -> turn(p, 70));
            case 470 -> capture = "23-blue-roundhouse.png";
            // ---------------------------------------------------------- First person
            case 490 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(mc, p -> { reset(p, 450, 0); actor(p, 2, 7); look(p); });
            }
            case 500 -> server(mc, FirebendingPreview::strike);
            case 501 -> capture = "24-jab-first-person.png";
            case 506 -> server(mc, FirebendingPreview::strike);
            case 507 -> capture = "25-cross-first-person.png";
            case 512 -> server(mc, FirebendingPreview::strike);
            case 515 -> capture = "26-roundhouse-first-person.png";
            case 530 -> server(mc, p -> reset(p, 480, 20));
            case 533 -> mc.options.keyShift.setDown(true);
            case 540 -> server(mc, FirebendingPreview::strike);
            case 543 -> capture = "27-sweep-first-person.png";
            case 550 -> mc.options.keyShift.setDown(false);
            case 560 -> server(mc, p -> dodge(p, PyroCombat.Dodge.LEFT));
            case 562 -> capture = "28-jet-step-first-person.png";
            case 585 -> server(mc, p -> dodge(p, PyroCombat.Dodge.BACK));
            case 588 -> capture = "29-flip-first-person.png";
            case 610 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Pre event) {
        if (age >= 100 && Boolean.getBoolean("heroes.preview.firebending")) event.setCanceled(true);
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (capture == null) return;
        Minecraft mc = Minecraft.getInstance();
        Path path = mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Files.createDirectories(path.getParent());
            image.writeToFile(path);
            System.out.println("FIRE_PREVIEW_CAPTURE " + capture);
        } catch (Exception e) {
            throw new IllegalStateException("Could not capture firebending preview", e);
        } finally {
            capture = null;
        }
    }
}
