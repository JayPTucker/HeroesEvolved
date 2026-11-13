package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.sonic.SonicService;
import com.jayptucker.heroesevolved.sonic.SonicSkills;
import com.jayptucker.heroesevolved.styles.StyleCasts;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
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
 * Photographs every Sonic Resonance move, skill and style (the arm poses and SonicArt) from behind, from the front and
 * in first person, at dusk in a flat world. Run with {@code tools/sonic-kit-preview.init.gradle}; shots land in
 * {@code build/sonic-kit-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class SonicKitPreview {
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

    private static AbilityUseContext context(ServerPlayer p) {
        PlayerEnergyService.restore(p, 999);
        return new AbilityUseContext(p, ModAbilities.SONIC_RESONANCE_ID, 5, false);
    }

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "SONIC_KIT_PASS " : "SONIC_KIT_FAIL ") + what);
    }

    private static void camera(Minecraft mc, CameraType type) { mc.options.setCameraType(type); }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.sonickit")) return;
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
            mc.createWorldOpenFlows().createFreshLevel("SonicKit-" + System.currentTimeMillis(),
                    new LevelSettings("Sonic kit preview", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
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
                    var id = ModAbilities.SONIC_RESONANCE_ID;
                    p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(id).unlock(id));
                    p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(id));
                    PlayerProgressionService.setLevel(p, 5);
                    // Survival: a creative player's hits ignore the targets' invulnerability.
                    p.setGameMode(GameType.SURVIVAL);
                    p.getAbilities().flying = false; p.onUpdateAbilities();
                    place(p, 1500, 0, 0, 3);
                    mob(p, EntityType.PILLAGER, 1500, 10);
                });
            }
            // ---------------- Canary Cry: the tap, then the held scream ----------------
            case 230 -> onServer(mc, p -> SonicService.cry(p, 5, false));
            case 232 -> pending = "c1-cry-back-a.png";
            case 235 -> pending = "c1-cry-back-b.png";
            case 250 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { place(p, 1500, 0, 0, 3); SonicService.cry(p, 5, false); }); }
            case 253 -> pending = "c1-cry-front.png";
            case 270 -> { camera(mc, CameraType.FIRST_PERSON); onServer(mc, p -> { place(p, 1500, 0, 0, 3); SonicService.cry(p, 5, false); }); }
            case 273 -> pending = "c1-cry-firstperson.png";
            case 290 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { place(p, 1500, 0, 0, 3); SonicService.press(p, 5); }); }
            case 306 -> pending = "c2-scream-back.png";
            case 312 -> camera(mc, CameraType.THIRD_PERSON_FRONT);
            case 316 -> pending = "c2-scream-front.png";
            case 320 -> camera(mc, CameraType.FIRST_PERSON);
            case 324 -> pending = "c2-scream-firstperson.png";
            case 330 -> onServer(mc, SonicService::release);
            // ---------------- Resonant Note: pluck, play, break ----------------
            case 350 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { place(p, 1520, 0, 0, 22); check(SonicService.placeNote(p, 5), "note"); }); }
            case 352 -> pending = "n1-note-pluck.png";
            case 368 -> pending = "n1-note-playing.png";
            case 372 -> camera(mc, CameraType.THIRD_PERSON_FRONT);
            case 376 -> pending = "n1-note-front.png";
            case 380 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> SonicService.addResonance(p, 40)); }
            case 392 -> pending = "n2-note-harmonic-ready.png";
            case 394 -> onServer(mc, p -> check(SonicService.noteFollowUp(p, false), "note burst"));
            case 397 -> pending = "n2-note-burst-a.png";
            case 403 -> pending = "n2-note-burst-b.png";
            // ---------------- Wall of Sound: raise, then shove ----------------
            case 430 -> onServer(mc, p -> { place(p, 1540, 0, 0, 5); check(SonicService.wall(p, 5), "wall"); });
            case 434 -> pending = "w1-wall-raise.png";
            case 446 -> pending = "w1-wall-standing.png";
            case 450 -> camera(mc, CameraType.THIRD_PERSON_FRONT);
            case 454 -> pending = "w1-wall-front.png";
            case 458 -> camera(mc, CameraType.THIRD_PERSON_BACK);
            case 462 -> onServer(mc, p -> check(SonicService.pushWall(p), "wall push"));
            case 464 -> pending = "w2-wall-shove.png";
            case 470 -> pending = "w2-wall-rolling.png";
            // ---------------- Sonic Vault, then the dive ----------------
            case 500 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { place(p, 1560, 0, 0, 10); SonicService.vault(p); }); }
            case 502 -> pending = "v1-vault-a.png";
            case 506 -> pending = "v1-vault-b.png";
            case 509 -> onServer(mc, p -> check(SonicService.dive(p, 5), "dive"));
            case 511 -> pending = "v2-dive.png";
            case 515 -> pending = "v2-dive-landing-a.png";
            case 520 -> pending = "v2-dive-landing-b.png";
            // ---------------- Crescendo: gather, release ----------------
            case 550 -> { onServer(mc, p -> { place(p, 1580, 0, 0, 3); mob(p, EntityType.PILLAGER, 1580, 14); SonicService.startCrescendo(p, 5); }); }
            case 560 -> pending = "x1-gather-early.png";
            case 595 -> pending = "x1-gather-full.png";
            case 600 -> camera(mc, CameraType.THIRD_PERSON_BACK);
            case 604 -> onServer(mc, SonicService::releaseCrescendo);
            case 607 -> pending = "x2-crescendo-a.png";
            case 612 -> pending = "x2-crescendo-b.png";
            case 630 -> { camera(mc, CameraType.FIRST_PERSON); onServer(mc, p -> { place(p, 1580, 0, 0, 3); SonicService.fireCrescendo(p, 5, 1); }); }
            case 633 -> pending = "x2-crescendo-firstperson.png";
            // ---------------- Skills ----------------
            // Focused Shriek: a hand cupped at the mouth, a needle, the target left Ringing.
            case 660 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { place(p, 1600, 0, 0, 2); mob(p, EntityType.PILLAGER, 1600, 12); }); }
            case 666 -> onServer(mc, p -> check(SonicSkills.skill(context(p), 0), "focused shriek"));
            case 668 -> pending = "k1-shriek-back.png";
            case 676 -> pending = "k1-shriek-ringing.png";
            case 690 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { place(p, 1600, 0, 0, 2); SonicSkills.skill(context(p), 0); }); }
            case 693 -> pending = "k1-shriek-front.png";
            case 710 -> { camera(mc, CameraType.FIRST_PERSON); onServer(mc, p -> { place(p, 1600, 0, 0, 2); SonicSkills.skill(context(p), 0); }); }
            case 713 -> pending = "k1-shriek-firstperson.png";
            // Dissonant Wave: a low, wide howl.
            case 740 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { place(p, 1620, 0, 0, 5); check(SonicSkills.skill(context(p), 1), "dissonant wave"); }); }
            case 743 -> pending = "k2-dissonance-a.png";
            case 749 -> pending = "k2-dissonance-b.png";
            // Resonant Guard: arms crossed, a shell of standing waves.
            case 770 -> onServer(mc, p -> { place(p, 1640, 0, 0, 5); check(SonicSkills.skill(context(p), 2), "resonant guard"); });
            case 775 -> pending = "k3-guard-front.png";
            case 784 -> { camera(mc, CameraType.THIRD_PERSON_BACK); pending = "k3-guard-back.png"; }
            // Shatter Note: a finger-snap at a glass wall.
            case 810 -> onServer(mc, p -> {
                for (int x = -2; x <= 2; x++) for (int y = 0; y <= 3; y++)
                    p.serverLevel().setBlockAndUpdate(new BlockPos(1660 + x, -60 + y, 8), Blocks.GLASS.defaultBlockState());
                place(p, 1660, 0, 0, -8);
            });
            case 815 -> onServer(mc, p -> check(SonicSkills.technique(p, "shatter_note"), "shatter note"));
            case 817 -> pending = "k4-shatter-snap.png";
            case 821 -> pending = "k4-shatter-break.png";
            case 840 -> { camera(mc, CameraType.FIRST_PERSON); onServer(mc, p -> {
                for (int y = 0; y <= 3; y++) p.serverLevel().setBlockAndUpdate(new BlockPos(1660, -60 + y, 8), Blocks.GLASS.defaultBlockState());
                place(p, 1660, 0, 0, -8); SonicSkills.technique(p, "shatter_note"); }); }
            case 842 -> pending = "k4-shatter-firstperson.png";
            // Echo Map: a hand to the ear, a sonar sweep.
            case 870 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { place(p, 1680, 0, 0, 5); check(SonicSkills.technique(p, "echo_map"), "echo map"); }); }
            case 874 -> pending = "k5-echo-listen.png";
            case 880 -> { camera(mc, CameraType.THIRD_PERSON_BACK); pending = "k5-echo-sweep.png"; }
            // Quiet Zone: a finger to the lips, rings closing inward.
            case 910 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { place(p, 1700, 0, 0, 5); check(SonicSkills.technique(p, "quiet_zone"), "quiet zone"); }); }
            case 916 -> pending = "k6-quiet-hush.png";
            case 930 -> { camera(mc, CameraType.THIRD_PERSON_BACK); pending = "k6-quiet-zone.png"; }
            // ---------------- Styles ----------------
            case 960 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { place(p, 1720, 0, 0, 20); check(StyleCasts.cast(context(p), "resonance_cascade"), "cascade"); }); }
            case 966 -> pending = "t1-cascade-conduct.png";
            case 975 -> pending = "t1-cascade-stack.png";
            case 979 -> pending = "t1-cascade-burst.png";
            case 1000 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { place(p, 1740, 0, 0, 10); check(StyleCasts.cast(context(p), "silence_dome"), "silence dome"); }); }
            case 1014 -> pending = "t2-silence-dome.png";
            case 1040 -> onServer(mc, p -> { place(p, 1760, 0, 0, 10); check(StyleCasts.cast(context(p), "battle_hymn"), "battle hymn"); });
            case 1052 -> pending = "t3-battle-hymn.png";
            case 1080 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Pre event) {
        if (cleanHud && Boolean.getBoolean("heroes.preview.sonickit")) event.setCanceled(true);
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("SONIC_KIT_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
