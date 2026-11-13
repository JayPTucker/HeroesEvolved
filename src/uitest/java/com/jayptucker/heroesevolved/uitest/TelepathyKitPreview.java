package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillActionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleCasts;
import com.jayptucker.heroesevolved.telepathy.MindMemory;
import com.jayptucker.heroesevolved.telepathy.TelepathyFocus;
import com.jayptucker.heroesevolved.telepathy.TelepathyGeometry;
import com.jayptucker.heroesevolved.telepathy.TelepathySkills;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
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
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Photographs every Telepathy pose and TelepathyArt piece from behind, from the front and in first person, at dusk in a
 * flat world. Pieces meant for a second player (a heard voice, a linked friend's halo) are sent to the preview player
 * with villagers standing in for the other minds. Run with {@code tools/telepathy-kit-preview.init.gradle}; shots land
 * in {@code build/telepathy-kit-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class TelepathyKitPreview {
    private static boolean started, cleanHud;
    private static int ticks, worldTicks;
    private static String pending;
    private static Mob friend, other, target;

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

    private static void clearMobs() {
        for (Mob m : new Mob[] {friend, other, target}) if (m != null) m.discard();
        friend = other = target = null;
    }

    /** A mob on a two-block pillar, so a camera behind the player sees it over their head while they aim at it. */
    private static Mob raised(ServerPlayer p, EntityType<? extends Mob> type, double x, double z) {
        var base = net.minecraft.core.BlockPos.containing(x + .5, -60, z + .5);
        p.serverLevel().setBlockAndUpdate(base, net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
        p.serverLevel().setBlockAndUpdate(base.above(), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
        Mob mob = mob(p, type, x, z);
        mob.moveTo(x + .5, -58, z + .5, 180, 0);
        return mob;
    }

    private static AbilityUseContext context(ServerPlayer p) {
        PlayerEnergyService.restore(p, 999);
        return new AbilityUseContext(p, ModAbilities.TELEPATHY_ID, 5, false);
    }

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "TELE_KIT_PASS " : "TELE_KIT_FAIL ") + what);
    }

    private static void camera(Minecraft mc, CameraType type) { mc.options.setCameraType(type); }

    private static void pose(ServerPlayer p, ArmPose pose, int ticks) { ArmPosePayload.show(p, pose, ticks); }

    /**
     * Turns the player 40 degrees left after a cast: the camera sits on their look line, so whatever they aimed at is
     * hidden behind their head until they turn.
     */
    private static void turn(ServerPlayer p) {
        p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), p.getYRot() - 40, Math.max(p.getXRot(), 5));
    }

    private static void look(ServerPlayer p, Mob at) {
        p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, at.getEyePosition());
    }

    /** A pose shot three ways: behind at {@code at}, the front at {@code at + 6}, first person at {@code at + 12}. */
    private static void poseShots(Minecraft mc, int tick, int at, String name, ArmPose pose, int length, double x) {
        if (tick == at) { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { place(p, x, 0, 0, 5); pose(p, pose, length); }); }
        if (tick == at + 5) pending = name + "-back.png";
        if (tick == at + 8) { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> pose(p, pose, length)); }
        if (tick == at + 13) pending = name + "-front.png";
        if (tick == at + 16) { camera(mc, CameraType.FIRST_PERSON); onServer(mc, p -> pose(p, pose, length)); }
        if (tick == at + 21) pending = name + "-firstperson.png";
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.telepathykit")) return;
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
            mc.createWorldOpenFlows().createFreshLevel("TelepathyKit-" + System.currentTimeMillis(),
                    new LevelSettings("Telepathy kit preview", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (cleanHud) mc.getToasts().clear();
        int t = ++worldTicks;
        // ---------------- The core's poses ----------------
        poseShots(mc, t, 230, "c1-read", ArmPose.TELE_READ, 40, 1500);
        poseShots(mc, t, 260, "c2-suggest", ArmPose.TELE_SUGGEST, 30, 1500);
        poseShots(mc, t, 290, "c3-link", ArmPose.TELE_LINK, 40, 1500);
        poseShots(mc, t, 330, "c4-puppet", ArmPose.TELE_PUPPET, 40, 1500);
        poseShots(mc, t, 370, "c5-nightmare", ArmPose.TELE_NIGHTMARE, 30, 1500);
        switch (t) {
            case 200 -> {
                cleanHud = true;
                camera(mc, CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    com.jayptucker.heroesevolved.story.bunker.CryoChamberService.release(p);
                    p.serverLevel().setDayTime(12600);
                    var id = ModAbilities.TELEPATHY_ID;
                    p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(id).unlock(id));
                    p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(id));
                    p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(id.toString(), new SkillTreeData.Build(15,
                            Set.of(TelepathySkills.DROP_IT, TelepathySkills.FAR_HEARING, TelepathySkills.PERSUASION, TelepathySkills.MEMORY_WALK,
                                    TelepathySkills.DEEP_READ, TelepathySkills.LOST_IN_THOUGHT, TelepathySkills.HIVE_LINK), Map.of())));
                    PlayerProgressionService.setLevel(p, 15);
                    // Survival: a creative player's hits ignore the targets' invulnerability.
                    p.setGameMode(GameType.SURVIVAL);
                    p.getAbilities().flying = false; p.onUpdateAbilities();
                    place(p, 1500, 0, 0, 5);
                });
            }
            // ---------------- Drop It ----------------
            case 410 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { clearMobs(); place(p, 1520, 0, 0, 3); target = raised(p, EntityType.PILLAGER, 1521, 9); look(p, target); }); }
            case 416 -> onServer(mc, p -> { check(SkillActionService.activate(context(p), 0), "drop it"); turn(p); });
            case 418 -> pending = "k1-dropit-back-a.png";
            case 423 -> pending = "k1-dropit-back-b.png";
            case 440 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { place(p, 1520, 0, 0, 3); look(p, target); SkillActionService.activate(context(p), 0); }); }
            case 443 -> pending = "k1-dropit-front.png";
            case 460 -> { camera(mc, CameraType.FIRST_PERSON); onServer(mc, p -> { place(p, 1520, 0, 0, 3); look(p, target); SkillActionService.activate(context(p), 0); }); }
            case 463 -> pending = "k1-dropit-firstperson.png";
            // ---------------- Far Hearing: the listener, then the voices it hears ----------------
            case 490 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { clearMobs(); place(p, 1540, 0, 0, 0); check(SkillActionService.activate(context(p), 1), "far hearing"); }); }
            case 498 -> pending = "k2-listen-front.png";
            case 504 -> onServer(mc, p -> {
                Vec3 eye = p.getEyePosition();
                TelepathyGeometry.showTo(p, Piece.TELE_VOICE, eye, eye.add(50, 6, 90), 1 + 10 * .3, p, 0);
                TelepathyGeometry.showTo(p, Piece.TELE_VOICE, eye, eye.add(-90, 0, 70), 1 + 10 * .7, p, 0);
                TelepathyGeometry.showTo(p, Piece.TELE_VOICE, eye, eye.add(10, 2, -60), 1 + 10 * .1, p, 0);
            });
            case 506 -> { camera(mc, CameraType.THIRD_PERSON_BACK); pending = "k2-voices-back.png"; }
            case 512 -> camera(mc, CameraType.FIRST_PERSON);
            case 514 -> onServer(mc, p -> {
                Vec3 eye = p.getEyePosition();
                TelepathyGeometry.showTo(p, Piece.TELE_VOICE, eye, eye.add(50, 6, 90), 1 + 10 * .3, p, 0);
                TelepathyGeometry.showTo(p, Piece.TELE_VOICE, eye, eye.add(-90, 0, 70), 1 + 10 * .7, p, 0);
            });
            case 516 -> pending = "k2-voices-firstperson.png";
            case 520 -> onServer(mc, p -> TelepathySkills.followUp(context(p), 1));
            // ---------------- Dream Double's weave ----------------
            case 540 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { place(p, 1560, 0, 0, 0); friend = mob(p, EntityType.VILLAGER, 1564, 7);
                pose(p, ArmPose.TELE_WEAVE, 30);
                TelepathyGeometry.showTo(p, Piece.TELE_WEAVE, TelepathyGeometry.hand(p, 1, .45), friend.getEyePosition(), 1, friend, 0); }); }
            case 546 -> pending = "k3-weave-back.png";
            case 553 -> pending = "k3-weave-closing.png";
            case 560 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> pose(p, ArmPose.TELE_WEAVE, 20)); }
            case 565 -> pending = "k3-weave-front.png";
            case 570 -> { camera(mc, CameraType.FIRST_PERSON); onServer(mc, p -> { pose(p, ArmPose.TELE_WEAVE, 30);
                TelepathyGeometry.showTo(p, Piece.TELE_WEAVE, TelepathyGeometry.hand(p, 1, .45), friend.getEyePosition(), 1, friend, 0); }); }
            case 576 -> pending = "k3-weave-firstperson.png";
            // ---------------- Persuasion: the talk-down, then a mind won ----------------
            case 600 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { clearMobs(); place(p, 1580, 0, 0, 3);
                target = raised(p, EntityType.ZOMBIE, 1581, 8); look(p, target); }); }
            case 604 -> onServer(mc, p -> { check(ExpansionSkills.activate(context(p), "persuasion"), "persuasion"); turn(p); });
            case 612 -> pending = "k4-persuade-early.png";
            case 634 -> pending = "k4-persuade-closing.png";
            case 638 -> camera(mc, CameraType.THIRD_PERSON_FRONT);
            case 641 -> pending = "k4-persuade-front.png";
            case 652 -> { camera(mc, CameraType.THIRD_PERSON_BACK); pending = "k4-persuade-won.png"; }
            case 660 -> { camera(mc, CameraType.FIRST_PERSON); onServer(mc, p -> {
                Mob next = mob(p, EntityType.ZOMBIE, 1577, 9); look(p, next); other = next;
                check(ExpansionSkills.activate(context(p), "persuasion"), "persuasion first person"); }); }
            case 676 -> pending = "k4-persuade-firstperson.png";
            // ---------------- Memory Walk, Deep Read and Lost in Thought ----------------
            case 710 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { clearMobs(); place(p, 1600, -4, 0, 20);
                target = mob(p, EntityType.ZOMBIE, 1602, 6);
                long now = p.serverLevel().getGameTime();
                Vec3 here = target.position();
                // A loop that ends where the zombie stands, so the echo is where it began.
                double end = Math.PI * 1.6;
                for (int i = 0; i < 50; i++) {
                    double a = i / 49.0 * end;
                    MindMemory.remember(target, now - 600 + i * 12,
                            here.add((Math.cos(a) - Math.cos(end)) * 5, 0, (Math.sin(a) - Math.sin(end)) * 5), i == 30 ? "Alex" : null);
                }
                look(p, target);
            }); }
            case 714 -> onServer(mc, p -> check(ExpansionSkills.activate(context(p), "memory_walk"), "memory walk"));
            case 716 -> pending = "k5-memory-cast.png";
            case 730 -> pending = "k5-memory-unspooling.png";
            case 760 -> pending = "k5-memory-trail.png";
            case 770 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> pose(p, ArmPose.TELE_MEMORY, 22)); }
            case 778 -> pending = "k5-memory-front.png";
            case 790 -> { camera(mc, CameraType.FIRST_PERSON); onServer(mc, p -> pose(p, ArmPose.TELE_MEMORY, 22)); }
            case 798 -> pending = "k5-memory-firstperson.png";
            case 806 -> { camera(mc, CameraType.THIRD_PERSON_BACK); pending = "k5-lost-in-thought.png"; }
            // ---------------- Waking Fright ----------------
            case 830 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { clearMobs(); place(p, 1620, 0, 0, 5); friend = mob(p, EntityType.VILLAGER, 1623, 6);
                TelepathyGeometry.show(p.serverLevel(), Piece.TELE_FRIGHT, friend.getEyePosition(), friend.position().add(0, 0, -1.5), 1, friend); }); }
            case 833 -> pending = "k6-fright-a.png";
            case 838 -> pending = "k6-fright-b.png";
            // ---------------- Hive Link: threads, then halos (speaking, in danger) ----------------
            case 860 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { clearMobs(); place(p, 1640, 0, 0, 0);
                friend = mob(p, EntityType.VILLAGER, 1637, 7); other = mob(p, EntityType.VILLAGER, 1643, 8);
                pose(p, ArmPose.TELE_HIVE, 20);
                for (Mob m : new Mob[] {friend, other}) TelepathyGeometry.show(p.serverLevel(), Piece.TELE_HIVE_LINK, p.getEyePosition(), m.getEyePosition(), 1, m); }); }
            case 865 -> pending = "k7-hive-threads.png";
            case 872 -> pending = "k7-hive-snap.png";
            case 880 -> onServer(mc, p -> {
                for (int i = 0; i < 3; i++) {
                    TelepathyGeometry.showTo(p, Piece.TELE_HALO, friend.getEyePosition(), friend.getEyePosition(), 1 + TelepathyGeometry.FLAG_SPEAKING, friend, i * 10);
                    TelepathyGeometry.showTo(p, Piece.TELE_HALO, other.getEyePosition(), other.getEyePosition(), 1 + TelepathyGeometry.FLAG_DANGER, other, i * 10);
                }
            });
            case 886 -> pending = "k7-hive-halos.png";
            case 890 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> pose(p, ArmPose.TELE_HIVE, 20)); }
            case 900 -> pending = "k7-hive-front.png";
            // ---------------- Mind Network: a voice inside your head ----------------
            case 920 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { clearMobs(); place(p, 1660, 0, 0, 0);
                for (int i = 0; i < 3; i++) TelepathyGeometry.showTo(p, Piece.TELE_PROJECT, p.getEyePosition(), p.getEyePosition(), 1, p, i * 10); }); }
            case 926 -> pending = "k8-project-front.png";
            case 932 -> camera(mc, CameraType.FIRST_PERSON);
            case 936 -> pending = "k8-project-firstperson.png";
            // ---------------- Styles ----------------
            case 960 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { clearMobs(); place(p, 1680, 0, 0, 15);
                target = mob(p, EntityType.ZOMBIE, 1680, 12); check(StyleCasts.cast(context(p), "psychic_storm"), "psychic storm"); }); }
            case 966 -> pending = "t1-storm-cast.png";
            case 990 -> pending = "t1-storm.png";
            case 1000 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { place(p, 1680, 0, 0, 15); StyleCasts.cast(context(p), "psychic_storm"); }); }
            case 1006 -> pending = "t1-storm-front.png";
            case 1030 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { clearMobs(); place(p, 1700, 0, 0, 5); check(StyleCasts.cast(context(p), "blind_spot"), "blind spot"); }); }
            case 1036 -> pending = "t2-blind-spot.png";
            case 1060 -> { camera(mc, CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { place(p, 1720, 0, 0, 5); check(StyleCasts.cast(context(p), "hive_mind"), "hive mind"); }); }
            case 1068 -> pending = "t3-hive-mind.png";
            case 1076 -> camera(mc, CameraType.THIRD_PERSON_FRONT);
            case 1080 -> pending = "t3-hive-mind-front.png";
            case 1100 -> mc.stop();
            default -> { }
        }
        if (t == 220) onServer(mc, p -> TelepathyFocus.forget(p));
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Pre event) {
        if (cleanHud && Boolean.getBoolean("heroes.preview.telepathykit")) event.setCanceled(true);
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("TELE_KIT_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
