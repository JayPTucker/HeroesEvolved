package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.quake.QuakeKit;
import com.jayptucker.heroesevolved.quake.QuakeService;
import com.jayptucker.heroesevolved.quake.QuakeSkills;
import com.jayptucker.heroesevolved.styles.StyleCasts;
import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.PrimedTnt;
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
 * Photographs every Quake move, follow-up, skill and style (the arm poses and QuakeArt) from behind, in front and in first
 * person, at dusk in a flat world. Run with {@code tools/quake-kit-preview.init.gradle}; shots land in
 * {@code build/quake-kit-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class QuakeKitPreview {
    private static boolean started, cleanHud;
    private static int ticks, worldTicks;
    private static String pending;

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void place(ServerPlayer p, double x, double z, float yaw, float pitch) {
        p.teleportTo(p.serverLevel(), x + 0.5, -60, z + 0.5, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO);
        QuakeKit.setStrain(p, 0);
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

    /** A bullet flying at the player from {@code dz} ahead. */
    private static void bullet(ServerPlayer p, double dx, double dz, float explosive) {
        Entity b = PowerWeapons.testBullet(p.serverLevel(), explosive);
        if (b == null) { check(false, "bullet"); return; }
        b.setPos(p.getX() + dx, p.getEyeY() - 0.1, p.getZ() + dz);
        b.setDeltaMovement(0, 0, -0.6);
        b.setNoGravity(true);
        p.serverLevel().addFreshEntity(b);
    }

    private static AbilityUseContext context(ServerPlayer p) {
        PlayerEnergyService.restore(p, 999);
        return new AbilityUseContext(p, ModAbilities.QUAKE_ID, 5, false);
    }

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "QUAKE_KIT_PASS " : "QUAKE_KIT_FAIL ") + what);
    }

    private static void camera(Minecraft mc, CameraType type) { mc.options.setCameraType(type); }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.quakekit")) return;
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
            mc.createWorldOpenFlows().createFreshLevel("QuakeKit-" + System.currentTimeMillis(),
                    new LevelSettings("Quake kit preview", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (cleanHud) mc.getToasts().clear();
        switch (++worldTicks) {
            case 200 -> {
                cleanHud = true;
                camera(mc, CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    com.jayptucker.heroesevolved.story.bunker.CryoChamberService.release(p);
                    p.serverLevel().setDayTime(12600);
                    var id = ModAbilities.QUAKE_ID;
                    p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(id).unlock(id));
                    p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(id));
                    PlayerProgressionService.setLevel(p, 5);
                    p.setGameMode(GameType.SURVIVAL);
                    p.getAbilities().flying = false; p.onUpdateAbilities();
                    place(p, 2000, 0, 0, 3);
                });
            }
            // ---------------- Shockwave and Double Tap ----------------
            case 230 -> onServer(mc, p -> QuakeService.shockwave(p, 5));
            case 232 -> pending = "a1-shockwave-back.png";
            case 236 -> onServer(mc, p -> check(QuakeService.doubleTap(p), "double tap"));
            case 238 -> pending = "a2-doubletap-back.png";
            case 260 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { place(p, 2000, 0, 0, 3); QuakeService.shockwave(p, 5); }); }
            case 262 -> pending = "a1-shockwave-front.png";
            case 266 -> onServer(mc, QuakeService::doubleTap);
            case 268 -> pending = "a2-doubletap-front.png";
            case 290 -> { camera(mc, CameraType.FIRST_PERSON); onServer(mc, p -> { place(p, 2000, 0, 0, 3); QuakeService.shockwave(p, 5); }); }
            case 292 -> pending = "a1-shockwave-firstperson.png";
            // ---------------- Bullets and explosives ----------------
            case 320 -> {
                camera(mc, CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    place(p, 2020, 0, 0, 2);
                    for (int i = 0; i < 4; i++) bullet(p, -0.6 + i * 0.4, 7 + i, 0);
                });
            }
            case 323 -> onServer(mc, p -> QuakeService.shockwave(p, 5));
            case 325 -> pending = "b1-bullets-dust-a.png";
            case 330 -> pending = "b1-bullets-dust-b.png";
            case 350 -> {
                camera(mc, CameraType.FIRST_PERSON);
                onServer(mc, p -> { place(p, 2020, 0, 0, 2); for (int i = 0; i < 3; i++) bullet(p, -0.4 + i * 0.4, 8 + i, 0); });
            }
            case 353 -> onServer(mc, p -> QuakeService.shockwave(p, 5));
            case 355 -> pending = "b1-bullets-dust-firstperson.png";
            case 380 -> {
                camera(mc, CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    place(p, 2040, 0, 0, 2);
                    PrimedTnt tnt = new PrimedTnt(p.serverLevel(), p.getX(), p.getY(), p.getZ() + 9, null);
                    tnt.setFuse(200);
                    p.serverLevel().addFreshEntity(tnt);
                });
            }
            case 384 -> onServer(mc, p -> QuakeService.shockwave(p, 5));
            case 386 -> pending = "b2-tnt-set-off.png";
            // ---------------- Resonate and Shatter ----------------
            case 420 -> onServer(mc, p -> { place(p, 2060, 0, 0, 3); mob(p, EntityType.PILLAGER, 2060, 8); });
            case 425 -> onServer(mc, p -> check(QuakeKit.resonate(p, 5), "resonate"));
            case 432 -> pending = "c1-resonate-back.png";
            case 440 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); pending = "c1-resonate-front.png"; }
            case 446 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> check(QuakeKit.collapse(p), "shatter")); }
            case 448 -> pending = "c2-shatter-a.png";
            case 453 -> pending = "c2-shatter-b.png";
            // ---------------- Fault Line and Upheaval; the airborne Slam ----------------
            case 480 -> onServer(mc, p -> { place(p, 2080, 0, 0, 10); mob(p, EntityType.PILLAGER, 2080, 9); });
            case 485 -> onServer(mc, p -> check(QuakeKit.faultLine(p, 5), "fault line"));
            case 487 -> pending = "d1-faultline-punch.png";
            case 490 -> pending = "d1-faultline-racing.png";
            case 491 -> onServer(mc, p -> check(QuakeKit.upheave(p), "upheaval"));
            case 493 -> pending = "d2-upheaval-a.png";
            case 497 -> pending = "d2-upheaval-b.png";
            case 520 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { place(p, 2100, 0, 0, 10); QuakeKit.faultLine(p, 5); }); }
            case 522 -> pending = "d1-faultline-front.png";
            case 524 -> onServer(mc, QuakeKit::upheave);
            case 527 -> pending = "d2-upheaval-front.png";
            case 550 -> onServer(mc, p -> { place(p, 2100, 0, 0, 10); p.teleportTo(p.getX(), p.getY() + 6, p.getZ()); });
            case 552 -> onServer(mc, p -> QuakeService.slam(p, 5));
            case 554 -> pending = "d3-plunge.png";
            // ---------------- Quake Blast and its flight ----------------
            case 590 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { place(p, 2120, 0, 0, 3); QuakeService.startQuakeBlast(p, 5); }); }
            case 600 -> pending = "e1-beam-front.png";
            case 604 -> camera(mc, CameraType.THIRD_PERSON_BACK);
            case 607 -> pending = "e1-beam-back.png";
            case 610 -> camera(mc, CameraType.FIRST_PERSON);
            case 613 -> pending = "e1-beam-firstperson.png";
            case 616 -> onServer(mc, p -> QuakeService.stopQuakeBlast(p, false));
            case 640 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { place(p, 2140, 0, 0, 70); QuakeService.startQuakeBlast(p, 5); }); }
            case 652 -> pending = "e2-flight.png";
            case 660 -> onServer(mc, p -> QuakeService.stopQuakeBlast(p, false));
            // ---------------- Earthquake and the early aftershock ----------------
            case 700 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { place(p, 2160, 0, 0, 10); QuakeService.startEarthquake(p, 5); }); }
            case 712 -> pending = "f1-earthquake-rumble.png";
            case 720 -> onServer(mc, QuakeService::stopEarthquake);
            case 722 -> pending = "f2-aftershock-a.png";
            case 726 -> pending = "f2-aftershock-b.png";
            // ---------------- Tremor Sense; a fracture ----------------
            case 760 -> onServer(mc, p -> { place(p, 2180, 0, 0, 30); p.setShiftKeyDown(true); QuakeService.startTremorSense(p, 5); });
            case 766 -> pending = "g1-tremor-feel.png";
            case 780 -> onServer(mc, p -> { p.setShiftKeyDown(false); place(p, 2180, 0, 0, 5); QuakeKit.setStrain(p, 99); QuakeKit.strain(p, 5); });
            case 784 -> pending = "g2-fracture.png";
            // ---------------- Skills ----------------
            case 830 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { place(p, 2200, 0, 0, 3); mob(p, EntityType.PILLAGER, 2200, 9); }); }
            case 836 -> onServer(mc, p -> check(QuakeSkills.skill(context(p), 0), "seismic lance"));
            case 838 -> pending = "k1-lance-uppercut.png";
            case 843 -> pending = "k1-lance-column-a.png";
            case 848 -> pending = "k1-lance-column-b.png";
            case 870 -> {
                camera(mc, CameraType.THIRD_PERSON_FRONT);
                onServer(mc, p -> {
                    place(p, 2220, 0, 0, 5);
                    check(QuakeSkills.skill(context(p), 1), "vibration shell");
                    for (int i = 0; i < 4; i++) bullet(p, -0.6 + i * 0.4, -8 - i * 2, 0);
                });
            }
            case 876 -> pending = "k2-shell-front.png";
            case 880 -> camera(mc, CameraType.THIRD_PERSON_BACK);
            case 884 -> pending = "k2-shell-back.png";
            case 920 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { place(p, 2240, 0, 180, 5); check(QuakeSkills.skill(context(p), 2), "pressure step"); }); }
            case 922 -> pending = "k3-step-a.png";
            case 926 -> pending = "k3-step-b.png";
            case 960 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { place(p, 2260, 0, 0, 25); check(ExpansionSkills.activate(context(p), "fault_script"), "fault script"); }); }
            case 964 -> pending = "k4-faultscript-written.png";
            case 990 -> pending = "k4-faultscript-glowing.png";
            case 1010 -> onServer(mc, p -> { place(p, 2280, 0, 0, 5); check(ExpansionSkills.activate(context(p), "counterforce"), "brace"); });
            case 1014 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); }
            case 1018 -> pending = "k5-brace.png";
            case 1022 -> onServer(mc, p -> ExpansionSkills.activate(context(p), "counterforce"));
            case 1024 -> pending = "k5-release.png";
            case 1050 -> {
                camera(mc, CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    for (int x = -2; x <= 2; x++) for (int y = 0; y <= 3; y++)
                        p.serverLevel().setBlockAndUpdate(new BlockPos(2300 + x, -60 + y, 3), Blocks.STONE.defaultBlockState());
                    place(p, 2300, 0, 0, 10);
                });
            }
            case 1054 -> onServer(mc, p -> check(ExpansionSkills.activate(context(p), "seamfinder"), "surgical fracture"));
            case 1057 -> pending = "k6-seam-touch.png";
            case 1064 -> pending = "k6-seam-cracks.png";
            // ---------------- Styles ----------------
            case 1100 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { place(p, 2320, 0, 0, 15); check(StyleCasts.cast(context(p), "fissure_maw"), "fissure maw"); }); }
            case 1106 -> pending = "t1-fissure-maw.png";
            case 1140 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { place(p, 2340, 0, 0, 10); check(StyleCasts.cast(context(p), "stone_rampart"), "stone rampart"); }); }
            case 1146 -> pending = "t2-stone-rampart.png";
            case 1180 -> onServer(mc, p -> { place(p, 2360, 0, 0, 10); check(StyleCasts.cast(context(p), "bedrock_stance"), "bedrock stance"); });
            case 1188 -> pending = "t3-bedrock-stance.png";
            case 1220 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Pre event) {
        if (cleanHud && Boolean.getBoolean("heroes.preview.quakekit")) event.setCanceled(true);
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("QUAKE_KIT_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
