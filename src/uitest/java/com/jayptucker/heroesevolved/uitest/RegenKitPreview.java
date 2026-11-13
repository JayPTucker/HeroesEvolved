package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.expansion.AdvancedTechniques;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.regeneration.RegenSkills;
import com.jayptucker.heroesevolved.regeneration.RegenerationService;
import com.jayptucker.heroesevolved.skills.SkillActionService;
import com.jayptucker.heroesevolved.styles.StyleCasts;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.*;
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
 * Photographs every Regeneration move, follow-up, skill and style (the arm poses and RegenArt) from behind, in front and
 * in first person, at dusk in a flat world. Run with {@code tools/regen-kit-preview.init.gradle}; shots land in
 * {@code build/regen-kit-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class RegenKitPreview {
    private static boolean started, cleanHud;
    private static int ticks, worldTicks;
    private static String pending;
    private static LivingEntity friend;

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void place(ServerPlayer p, double x, double z, float yaw, float pitch) {
        p.teleportTo(p.serverLevel(), x + 0.5, -60, z + 0.5, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO);
        p.setHealth(p.getMaxHealth());
    }

    /** A hurt friend standing ahead (a villager, so it isn't counted as a monster). */
    private static LivingEntity hurtFriend(ServerPlayer p, double x, double z, float health) {
        Mob mob = EntityType.VILLAGER.create(p.serverLevel());
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        mob.moveTo(x + .5, -60, z + .5, 180, 0);
        mob.setHealth(health);
        p.serverLevel().addFreshEntity(mob);
        friend = mob;
        return mob;
    }

    private static AbilityUseContext context(ServerPlayer p) {
        PlayerEnergyService.restore(p, 999);
        return new AbilityUseContext(p, ModAbilities.REGENERATION_ID, 5, false);
    }

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "REGEN_KIT_PASS " : "REGEN_KIT_FAIL ") + what);
    }

    private static void camera(Minecraft mc, CameraType type) { mc.options.setCameraType(type); }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.regenkit")) return;
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
            mc.createWorldOpenFlows().createFreshLevel("RegenKit-" + System.currentTimeMillis(),
                    new LevelSettings("Regeneration kit preview", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (cleanHud) mc.getToasts().clear();
        switch (++worldTicks) {
            case 200 -> {
                cleanHud = true;
                camera(mc, CameraType.THIRD_PERSON_FRONT);
                onServer(mc, p -> {
                    com.jayptucker.heroesevolved.story.bunker.CryoChamberService.release(p);
                    p.serverLevel().setDayTime(12600);
                    var id = ModAbilities.REGENERATION_ID;
                    p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(id).unlock(id));
                    p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(id));
                    PlayerProgressionService.setLevel(p, 5);
                    p.setGameMode(GameType.SURVIVAL);
                    p.getAbilities().flying = false; p.onUpdateAbilities();
                    place(p, 3000, 0, 0, 5);
                });
            }
            // ---------------- Rapid Mending, pushing arrows out ----------------
            case 230 -> onServer(mc, p -> { p.setArrowCount(5); p.setHealth(6); RegenerationService.addRegrowth(p, 60); RegenerationService.rapidMending(p, 5); });
            case 232 -> pending = "a1-mend-press.png";
            case 240 -> pending = "a1-mend-flex.png";
            case 260 -> { camera(mc, CameraType.FIRST_PERSON); onServer(mc, p -> { place(p, 3000, 0, 0, 5); p.setHealth(6); RegenerationService.rapidMending(p, 5); }); }
            case 263 -> pending = "a1-mend-firstperson.png";
            // ---------------- Blood Gift, then Blood Stream ----------------
            case 290 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { place(p, 3020, 0, 0, 10); hurtFriend(p, 3020, 4, 4); }); }
            case 296 -> onServer(mc, p -> check(RegenerationService.bloodGift(p, 5), "blood gift"));
            case 299 -> pending = "b1-gift-back.png";
            case 320 -> onServer(mc, p -> { friend.setHealth(4); RegenSkills.pressGift(p, 5); });
            case 334 -> pending = "b2-stream-back.png";
            case 346 -> camera(mc, CameraType.FIRST_PERSON);
            case 350 -> pending = "b2-stream-firstperson.png";
            case 360 -> onServer(mc, RegenSkills::releaseGift);
            // ---------------- Indestructible and Freefall ----------------
            case 390 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { friend.discard(); place(p, 3040, 0, 0, 5); RegenerationService.indestructible(p, 5); }); }
            case 394 -> pending = "c1-indestructible.png";
            case 400 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> check(RegenSkills.freefall(p), "freefall")); }
            case 404 -> pending = "c2-freefall-leap.png";
            case 412 -> pending = "c2-freefall-high.png";
            case 431 -> pending = "c3-landing-a.png";
            case 437 -> pending = "c3-landing-b.png";
            // ---------------- Blood Bond and Pay It Back ----------------
            case 470 -> {
                camera(mc, CameraType.THIRD_PERSON_FRONT);
                onServer(mc, p -> { place(p, 3060, 0, 0, 5); hurtFriend(p, 3062, 2, 18); RegenerationService.healingAura(p, 5); });
            }
            case 478 -> pending = "d1-bond-embrace.png";
            case 480 -> onServer(mc, p -> { RegenerationService.takeShare(p, friend, 5); friend.setHealth(6); });
            case 488 -> onServer(mc, p -> check(RegenerationService.payItBack(p), "pay it back"));
            case 490 -> pending = "d2-payback-a.png";
            case 495 -> pending = "d2-payback-b.png";
            // ---------------- Second Life ----------------
            case 520 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { friend.discard(); place(p, 3080, 0, 0, 10);
                hurtFriend(p, 3080, 3, 6); RegenerationService.addRegrowth(p, 100); }); }
            case 526 -> onServer(mc, p -> check(RegenerationService.secondLife(p), "second life"));
            case 529 -> pending = "e1-second-life.png";
            // ---------------- Skills ----------------
            case 560 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { friend.discard(); place(p, 3100, 0, 0, 10); hurtFriend(p, 3101, 4, 4); }); }
            case 566 -> onServer(mc, p -> check(SkillActionService.activate(context(p), 0), "bloodletting"));
            case 568 -> pending = "k1-bloodletting-a.png";
            case 572 -> pending = "k1-bloodletting-b.png";
            case 590 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { place(p, 3100, -4, 0, 10); RegenerationService.addRegrowth(p, 40);
                check(SkillActionService.activate(context(p), 1), "adrenal rush"); }); }
            case 594 -> pending = "k2-adrenal-rush.png";
            case 620 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { friend.discard(); place(p, 3120, 0, 0, 30); check(SkillActionService.activate(context(p), 2), "play dead"); }); }
            case 630 -> pending = "k3-play-dead.png";
            case 640 -> onServer(mc, p -> check(RegenSkills.followUp(context(p), 2), "get up"));
            case 642 -> pending = "k3-get-up.png";
            case 670 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { place(p, 3140, 0, 0, 10); hurtFriend(p, 3140, 3, 6); }); }
            case 676 -> onServer(mc, p -> check(com.jayptucker.heroesevolved.expansion.AdvancedTechniques.activate(context(p), "take_the_wound"), "take the wound"));
            case 680 -> pending = "k4-take-the-wound.png";
            case 700 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { friend.discard(); place(p, 3160, 0, 0, 5); check(AdvancedTechniques.activate(context(p), "adaptation"), "adaptation"); }); }
            case 706 -> pending = "k5-adaptation.png";
            case 730 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { place(p, 3180, 0, 0, 25); hurtFriend(p, 3180, 2, 3); }); }
            case 736 -> onServer(mc, p -> check(AdvancedTechniques.activate(context(p), "revive"), "revive"));
            case 742 -> pending = "k6-revive-a.png";
            case 752 -> pending = "k6-revive-b.png";
            // ---------------- Styles ----------------
            case 790 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { friend.discard(); place(p, 3200, 0, 0, 5); check(StyleCasts.cast(context(p), "vital_burst"), "vital burst"); }); }
            case 794 -> pending = "t1-vital-burst.png";
            case 820 -> onServer(mc, p -> { place(p, 3220, 0, 0, 5); check(StyleCasts.cast(context(p), "undying"), "undying"); });
            case 826 -> pending = "t2-undying.png";
            case 850 -> onServer(mc, p -> { place(p, 3240, 0, 0, 5); check(StyleCasts.cast(context(p), "lifebloom"), "lifebloom"); });
            case 862 -> pending = "t3-lifebloom.png";
            case 890 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Pre event) {
        if (cleanHud && Boolean.getBoolean("heroes.preview.regenkit")) event.setCanceled(true);
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("REGEN_KIT_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
