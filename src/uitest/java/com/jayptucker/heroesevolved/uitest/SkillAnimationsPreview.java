package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.electro.ElectroService;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.expansion.AdvancedTechniques;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillActionService;
import com.jayptucker.heroesevolved.speed.SpeedService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.nio.file.*;
import java.util.function.Consumer;

/**
 * Photographs Electrokinesis's and Superspeed's skill animations (the arm poses and their effects) from behind,
 * from the front and in first person. Run with {@code tools/skill-anim-preview.init.gradle}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class SkillAnimationsPreview {
    private static boolean started, cleanHud;
    private static int ticks, worldTicks;
    private static String pending;

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void place(ServerPlayer p, double x, double z, float yaw, float pitch) {
        p.teleportTo(p.serverLevel(), x + 0.5, -60, z + 0.5, yaw, pitch);
    }

    private static Mob mob(ServerPlayer p, EntityType<? extends Mob> type, double x, double z) {
        Mob mob = type.create(p.serverLevel());
        mob.setNoAi(true);
        mob.setInvulnerable(true);
        mob.setPersistenceRequired();
        mob.moveTo(x + .5, -60, z + .5, 180, 0);
        p.serverLevel().addFreshEntity(mob);
        return mob;
    }

    private static AbilityUseContext context(ServerPlayer p, ResourceLocation power) {
        PlayerEnergyService.restore(p, 999);
        return new AbilityUseContext(p, power, 5, false);
    }

    private static void power(ServerPlayer p, ResourceLocation power) {
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(power));
        PlayerProgressionService.setLevel(p, 5);
    }

    private static void check(boolean ok, String what) {
        // Report and carry on: one missed cast must not end the whole photo run.
        System.out.println((ok ? "SKILL_ANIM_PASS " : "SKILL_ANIM_FAIL ") + what);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.skillanim")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = false;
            mc.options.pauseOnLostFocus = false;
            mc.options.fov().set(84);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("SkillAnim-" + System.currentTimeMillis(),
                    new LevelSettings("Skill animation preview", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (cleanHud) mc.getToasts().clear();
        ResourceLocation electro = ModAbilities.ELECTROKINESIS_ID, speed = ModAbilities.SUPERSPEED_ID;
        switch (++worldTicks) {
            case 200 -> {
                cleanHud = true;
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                onServer(mc, p -> {
                    com.jayptucker.heroesevolved.story.bunker.CryoChamberService.release(p);
                    p.serverLevel().setDayTime(12300);
                    power(p, electro);
                    // Survival: a creative player's hits ignore the targets' invulnerability and kill them.
                    p.setGameMode(GameType.SURVIVAL);
                    p.getAbilities().flying = false; p.onUpdateAbilities();
                    place(p, 1200, 0, 0, 5);
                });
            }
            // ---------------- Electrokinesis ----------------
            // Ball Lightning: palms cupped round the forming orb, then pushed out.
            case 230 -> onServer(mc, p -> check(ElectroService.ballLightning(p, 5), "ball lightning cast"));
            case 232 -> pending = "e1-ball-cup-front.png";
            case 236 -> pending = "e1-ball-push-front.png";
            case 260 -> { mc.options.setCameraType(CameraType.FIRST_PERSON); onServer(mc, p -> { place(p, 1200, 0, 0, 5); ElectroService.ballLightning(p, 5); }); }
            case 262 -> pending = "e1-ball-cup-firstperson.png";
            // Arc Lance: a braced, dead-straight shot.
            case 290 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> { place(p, 1200, 20, 0, 2); mob(p, EntityType.PILLAGER, 1200, 36); });
            }
            case 296 -> onServer(mc, p -> check(SkillActionService.activate(context(p, electro), 0), "arc lance"));
            case 299 -> pending = "e2-lance-back.png";
            case 304 -> pending = "e2-lance-breaking.png";
            case 320 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> SkillActionService.activate(context(p, electro), 0)); }
            case 323 -> pending = "e2-lance-front.png";
            case 340 -> { mc.options.setCameraType(CameraType.FIRST_PERSON); onServer(mc, p -> SkillActionService.activate(context(p, electro), 0)); }
            case 343 -> pending = "e2-lance-firstperson.png";
            // Circuit Thread: a palm on the metal, current crawling block to block.
            case 360 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    for (int z = 52; z <= 60; z++) p.serverLevel().setBlockAndUpdate(new BlockPos(1200, -60, z), Blocks.IRON_BLOCK.defaultBlockState());
                    for (int x = 1201; x <= 1204; x++) p.serverLevel().setBlockAndUpdate(new BlockPos(x, -60, 60), Blocks.IRON_BLOCK.defaultBlockState());
                    mob(p, EntityType.PILLAGER, 1201, 57);
                    place(p, 1200, 50, 0, 25);
                });
            }
            case 366 -> onServer(mc, p -> check(ExpansionSkills.activate(context(p, electro), "circuit_thread"), "circuit thread"));
            case 368 -> pending = "e3-thread-a.png";
            case 373 -> pending = "e3-thread-b.png";
            case 380 -> pending = "e3-thread-c.png";
            // Overcharge: current climbs the body, then flares off the chest.
            case 400 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                onServer(mc, p -> { place(p, 1220, 0, 0, 5); check(SkillActionService.activate(context(p, electro), 2), "overcharge"); });
            }
            case 405 -> pending = "e4-overcharge-climb.png";
            case 412 -> pending = "e4-overcharge-flare.png";
            // Ground Sink: a palm driven down, a rod of current left standing; cast again to discharge.
            case 430 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> { place(p, 1240, 0, 0, 45); check(ExpansionSkills.activate(context(p, electro), "ground_sink"), "ground sink"); });
            }
            case 432 -> pending = "e5-sink-drive.png";
            case 445 -> onServer(mc, p -> place(p, 1240, -3, 0, 30));
            case 452 -> pending = "e5-sink-rod.png";
            case 470 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    mob(p, EntityType.PILLAGER, 1243, 5); mob(p, EntityType.PILLAGER, 1237, 6);
                    check(ExpansionSkills.activate(context(p, electro), "ground_sink"), "ground sink discharge");
                });
            }
            case 473 -> pending = "e5-sink-burst-a.png";
            case 479 -> pending = "e5-sink-burst-b.png";
            // Arc Lattice: three anchors flicked onto the ground, wired together.
            case 500 -> onServer(mc, p -> { place(p, 1260, 0, -25, 35); check(ExpansionSkills.activate(context(p, electro), "arc_lattice"), "lattice 1"); });
            case 502 -> pending = "e6-lattice-point.png";
            case 506 -> onServer(mc, p -> { place(p, 1260, 0, 0, 28); ExpansionSkills.activate(context(p, electro), "arc_lattice"); });
            case 512 -> onServer(mc, p -> { place(p, 1260, 0, 30, 38); ExpansionSkills.activate(context(p, electro), "arc_lattice"); });
            case 520 -> onServer(mc, p -> place(p, 1260, -3, 0, 30));
            case 526 -> pending = "e6-lattice-wires.png";
            // Ride the Lightning: the arms flung back as the body becomes the bolt.
            case 550 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> { place(p, 1280, 0, 0, 4); check(ElectroService.rideTheLightning(p, 5), "ride the lightning"); });
            }
            case 552 -> pending = "e7-ride-back.png";
            case 570 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { place(p, 1290, 0, 0, 4); ElectroService.rideTheLightning(p, 5); }); }
            case 572 -> pending = "e7-ride-front.png";
            // ---------------- Superspeed ----------------
            case 600 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                onServer(mc, p -> { ElectroService.forget(p); power(p, speed); place(p, 1300, 0, 0, 5); });
            }
            // Reflex Focus: guard up, rings closing in.
            case 620 -> onServer(mc, p -> check(SpeedService.skill(context(p, speed), 0), "reflex focus"));
            case 622 -> pending = "s1-focus-a.png";
            case 626 -> pending = "s1-focus-b.png";
            // A dodge out of the guard.
            case 640 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    var skeleton = mob(p, EntityType.SKELETON, 1300, 10);
                    Arrow arrow = new Arrow(EntityType.ARROW, p.serverLevel());
                    arrow.setOwner(skeleton);
                    arrow.setPos(p.getX(), p.getEyeY(), p.getZ() + 2);
                    check(SpeedService.dodge(p, p.damageSources().arrow(arrow, skeleton)), "dodge");
                });
            }
            case 642 -> pending = "s1-dodge-a.png";
            case 645 -> pending = "s1-dodge-b.png";
            // Speed Mirage: afterimages tearing loose all round.
            case 670 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                onServer(mc, p -> { place(p, 1320, 0, 0, 5); check(AdvancedTechniques.activate(context(p, speed), "mirage"), "mirage"); });
            }
            case 674 -> pending = "s2-mirage-a.png";
            case 690 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); pending = "s2-mirage-b.png"; }
            // Momentum Strike: the rush, and the fist driven through.
            case 720 -> onServer(mc, p -> { place(p, 1340, 0, 0, 3); mob(p, EntityType.PILLAGER, 1340, 13); });
            case 726 -> onServer(mc, p -> check(SpeedService.skill(context(p, speed), 1), "momentum strike"));
            case 728 -> pending = "s3-strike-a.png";
            case 730 -> pending = "s3-strike-b.png";
            // Lightning Throw: cocked, whipped, released.
            // (After the Mirage has worn off, so its afterimages don't crowd the shot.)
            case 800 -> onServer(mc, p -> { place(p, 1360, 0, 0, 2); mob(p, EntityType.PILLAGER, 1360, 12); });
            case 806 -> onServer(mc, p -> { SpeedService.redline(p); check(AdvancedTechniques.activate(context(p, speed), "lightning_throw"), "lightning throw"); });
            case 807 -> pending = "s4-throw-cocked.png";
            case 810 -> pending = "s4-throw-release.png";
            case 830 -> { mc.options.setCameraType(CameraType.FIRST_PERSON); onServer(mc, p -> { place(p, 1360, 0, 0, 2); SpeedService.redline(p); AdvancedTechniques.activate(context(p, speed), "lightning_throw"); }); }
            case 831 -> pending = "s4-throw-firstperson-cocked.png";
            case 834 -> pending = "s4-throw-firstperson-release.png";
            // Slipstream Rescue: a villager carried in the wake.
            case 850 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                onServer(mc, p -> { place(p, 1380, 0, 180, 5); mob(p, EntityType.VILLAGER, 1381, 0); });
            }
            case 856 -> onServer(mc, p -> check(SpeedService.skill(context(p, speed), 2), "slipstream rescue"));
            case 858 -> pending = "s5-slipstream-a.png";
            case 861 -> pending = "s5-slipstream-b.png";
            // Perception: open hands held still while the world crawls.
            case 890 -> onServer(mc, p -> { place(p, 1400, 0, 0, 5); check(AdvancedTechniques.activate(context(p, speed), "perception"), "perception"); });
            case 896 -> pending = "s6-perception.png";
            case 930 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Pre event) {
        if (cleanHud && Boolean.getBoolean("heroes.preview.skillanim")) event.setCanceled(true);
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("SKILL_ANIM_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
