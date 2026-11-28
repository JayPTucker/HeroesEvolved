package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.combat.Ultimates;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.projectile.Arrow;
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

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Every class Ultimate, staged in the Overworld at noon: the caster faces two foes (a pillager four blocks ahead, a
 * vindicator beside it) with a tamed wolf as a friend at their side, casts through the class node's real action, and
 * is photographed through the Ultimate's own camera while it plays (what the caster sees) and from the side after it.
 * Each Ultimate is checked to end with the caster out of walls, the held foe's mind given back, and (Offense) the foe
 * hurt. Flag {@code heroes.preview.ultimate}; {@code heroes.preview.only} picks mechanics or powers; shots in
 * {@code build/ultimate-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class UltimatePreview {
    private static boolean started;
    private static int ticks, world, photos, expected;
    private static String pending;
    private static boolean cleanHud;
    private static final double X = 3000.5, Y = -60, Z = .5;
    private static final List<Mob> ACTORS = new ArrayList<>();
    private static Mob foe;
    private static float foeHealth;
    private static final Map<Integer, Runnable> EVENTS = new HashMap<>();
    private static final int START = 240, GAP = 50;

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (player != null) action.accept(player);
            } catch (Throwable t) { PreviewQuiet.report("ULTIMATE FAIL " + t); t.printStackTrace(); }
        });
    }

    private static void check(String name, boolean ok) { PreviewQuiet.report("ULTIMATE " + (ok ? "PASS " : "FAIL ") + name); }

    private static void photo(String name) { pending = "ult-" + name + ".png"; expected++; }

    private static Vec3 at(double dx, double dy, double dz) { return new Vec3(X + dx, Y + dy, Z + dz); }

    private static Mob actor(ServerPlayer p, EntityType<? extends Mob> type, Vec3 where, float yaw) {
        Mob mob = type.create(p.serverLevel());
        mob.moveTo(where.x, where.y, where.z, yaw, 0);
        mob.setPersistenceRequired();
        mob.getPersistentData().putBoolean(com.jayptucker.heroesevolved.gifted.GiftedMobs.ROLLED, true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
        mob.setHealth(400);
        p.serverLevel().addFreshEntity(mob);
        mob.goalSelector.removeAllGoals(goal -> true);
        mob.targetSelector.removeAllGoals(goal -> true);
        ACTORS.add(mob);
        return mob;
    }

    private static void reset(ServerPlayer p) {
        Ultimates.stop(p);
        for (Mob mob : ACTORS) mob.discard();
        ACTORS.clear();
        foe = null;
        PlayerEnergyService.restore(p, 999);
        com.jayptucker.heroesevolved.cooldown.CooldownService.clearAll(p);
        p.setHealth(p.getMaxHealth() * .6F);
    }

    private static void grant(ServerPlayer p, ResourceLocation power, String node) {
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(power));
        PlayerProgressionService.setLevel(p, 5);
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(power.toString(), new SkillTreeData.Build(15, Set.of(node), Map.of())));
        com.jayptucker.heroesevolved.network.PlayerPowerSyncService.sync(p);
    }

    /** One Ultimate to stage: its power, node and mechanic, its length, and where the side camera stands. */
    private record Scene(ResourceLocation power, SkillCatalog.Node node, Ultimates.Kind kind, int length, int index) {}

    private static List<Scene> scenes() {
        String only = System.getProperty("heroes.preview.only", "");
        Set<String> picked = only.isBlank() ? Set.of() : Set.copyOf(Arrays.asList(only.split(",")));
        List<Scene> list = new ArrayList<>();
        for (ResourceLocation power : AbilityRegistry.ABILITIES.keySet())
            for (SkillCatalog.Node node : SkillCatalog.styles(power.getPath())) {
                Ultimates.Ultimate u = Ultimates.of(node.mechanic());
                if (u == null) continue;
                if (!picked.isEmpty() && !picked.contains(node.mechanic()) && !picked.contains(power.getPath())) continue;
                list.add(new Scene(power, node, u.kind(), 0, list.size()));
            }
        return list;
    }

    /** When to photograph: through the Ultimate's own camera while it plays, then from the side. */
    private static int[] cinemaShots(Ultimates.Kind kind) {
        return kind == Ultimates.Kind.OFFENSE ? new int[]{7, 20, 40, 68, 84, 96, 108, 118} : new int[]{6, 16};
    }

    private static int[] sideShots(Ultimates.Kind kind, String mechanic) {
        // Between Heartbeats: the vibration, the split, the illusions fighting, one struck (at 57) and stepping aside,
        // and the run home.
        if (mechanic.equals("phase_blur")) return new int[]{10, 18, 22, 30, 45, 59, 60, 90, 120, 200, 250};
        return kind == Ultimates.Kind.OFFENSE ? new int[]{132} : new int[]{30, 60, 120, 200};
    }

    /** Long enough for the longest of its kind to end (Offense 140 ticks; Defense and Support up to 300). */
    private static int span(Ultimates.Kind kind) { return kind == Ultimates.Kind.OFFENSE ? 150 : 320; }

    private static void schedule(Minecraft mc) {
        List<Scene> list = scenes();
        int t0 = START;
        for (Scene sc : list) {
            final int start = t0;
            double dx = 40 * sc.index();
            String name = sc.node().mechanic();
            EVENTS.put(start - 8, () -> server(mc, p -> { reset(p); grant(p, sc.power(), sc.node().id()); }));
            EVENTS.put(start - 4, () -> server(mc, p -> {
                p.teleportTo(p.server.overworld(), X + dx, Y, Z, 0, 0);
                foe = actor(p, EntityType.PILLAGER, at(dx, 0, 4), 180);
                foeHealth = foe.getHealth();
                actor(p, EntityType.VINDICATOR, at(dx + 2.5, 0, 6), 200);
                Mob wolf = actor(p, EntityType.WOLF, at(dx - 1.5, 0, -.5), 0);
                if (wolf instanceof Wolf w) { w.tame(p); w.setHealth(w.getMaxHealth() * .4F); }
            }));
            EVENTS.put(start, () -> {
                cleanHud = true;
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                TempestCamera.fix(null, null);
                server(mc, p -> {
                    var context = new AbilityUseContext(p, sc.power(), 5, false);
                    boolean ready = Ultimates.canUse(context, name);
                    check(name + " can be used", ready);
                    check(name + " casts", ready && Ultimates.cast(context, name));
                    if (sc.kind() != Ultimates.Kind.OFFENSE)
                        // Something to answer: an arrow at the caster and a blow from the vindicator.
                        p.server.execute(() -> {
                            Arrow arrow = new Arrow(EntityType.ARROW, p.serverLevel());
                            arrow.setPos(X + dx, Y + 1.5, Z + 8);
                            arrow.shoot(0, 0, -1, 1.6F, 0);
                            p.serverLevel().addFreshEntity(arrow);
                        });
                });
            });
            int n = 1;
            for (int shot : cinemaShots(sc.kind())) {
                String file = String.format("%02d-%s-%d", sc.index() + 1, name, n++);
                EVENTS.merge(start + shot, () -> photo(file), (a, b) -> () -> { a.run(); b.run(); });
            }
            Vec3 eye = at(dx + 9, 3.5, 1.5), look = at(dx, 1, 2.5);
            if (name.equals("phase_blur")) {
                // A blow on an illusion: it should step aside and leave the speedster's own afterimage standing.
                EVENTS.merge(start + 57, () -> server(mc, p -> {
                    if (Ultimates.run(p) instanceof com.jayptucker.heroesevolved.speed.SpeedUltimates.BetweenHeartbeats run) {
                        var illusions = run.illusions();
                        check("phase_blur splits into three illusions", illusions.size() == 3);
                        if (!illusions.isEmpty() && ACTORS.size() > 1) {
                            illusions.getFirst().hurt(p.serverLevel().damageSources().mobAttack(ACTORS.get(1)), 4);
                            check("phase_blur's struck illusion steps aside", !illusions.getFirst().isRemoved() && run.blows().getFirst() == 2);
                        }
                        check("phase_blur's illusions fight the pillager", foe != null && foe.getHealth() < foeHealth);
                    } else check("phase_blur is running at 57", false);
                }), (a, b) -> () -> { a.run(); b.run(); });
            }
            for (int shot : sideShots(sc.kind(), name)) {
                String file = String.format("%02d-%s-%d", sc.index() + 1, name, n++);
                EVENTS.merge(start + shot - 1, () -> TempestCamera.fix(eye, look), (a, b) -> () -> { a.run(); b.run(); });
                EVENTS.merge(start + shot, () -> photo(file), (a, b) -> () -> { a.run(); b.run(); });
            }
            int end = start + span(sc.kind());
            EVENTS.put(end - 2, () -> server(mc, p -> {
                check(name + " ended", !Ultimates.running(p));
                check(name + " leaves the caster out of walls", p.serverLevel().noCollision(p));
                if (foe != null) {
                    check(name + " gives the foe its mind back", !foe.isNoAi() || !foe.getPersistentData().getBoolean("heroesevolved_ultimate_held"));
                    if (sc.kind() == Ultimates.Kind.OFFENSE) check(name + " hurts the foe", foe.getHealth() < foeHealth);
                }
                PreviewQuiet.report("ULTIMATE " + name + " foe " + (foe == null ? "-" : foe.getHealth() + "/" + foeHealth)
                        + " caster at " + p.position());
            }));
            t0 = end + GAP;
        }
        final int last = t0;
        EVENTS.put(last, () -> server(mc, UltimatePreview::reset));
        EVENTS.put(last + 10, () -> { TempestCamera.fix(null, null); check("all photographs", photos == expected); PreviewQuiet.quit(); });
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.ultimate")) return;
        var mc = Minecraft.getInstance();
        if (!started && ++ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true; mc.options.pauseOnLostFocus = false; mc.options.guiScale().set(2); mc.options.fov().set(80);
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Ultimates-" + System.currentTimeMillis(),
                    new LevelSettings("Ultimates preview", GameType.SURVIVAL, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        mc.getToasts().clear();
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        ++world;
        if (world == 160) {
            schedule(mc);
            server(mc, p -> {
                PrologueService.skip(p); CryoChamberService.release(p);
                int n = Math.max(1, scenes().size());
                for (int cx = (int) (X / 16) - 2; cx <= (int) ((X + 40 * n) / 16) + 2; cx++)
                    for (int cz = -3; cz <= 3; cz++) p.server.overworld().setChunkForced(cx, cz, true);
                p.server.overworld().setDayTime(6000);
                p.setInvulnerable(false);
                p.teleportTo(p.server.overworld(), X, Y, Z, 0, 0);
                PreviewQuiet.report("ULTIMATE staged " + scenes().size() + " in " + p.level().dimension().location()
                        + " on " + p.level().getBlockState(p.blockPosition().below()));
            });
        }
        Runnable scheduled = EVENTS.remove(world);
        if (scheduled != null) scheduled.run();
    }

    /** A clean frame, except the letterbox, which is part of what the caster sees. */
    @SubscribeEvent public static void hud(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) {
        if (cleanHud && Boolean.getBoolean("heroes.preview.ultimate") && !event.getName().getPath().equals("ultimate_letterbox"))
            event.setCanceled(true);
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            var path = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(path.getParent());
            image.writeToFile(path);
            photos++;
            System.out.println("ULTIMATE_CAPTURE " + pending);
        } catch (Exception ex) { PreviewQuiet.report("ULTIMATE FAIL screenshot " + ex); }
        pending = null;
    }
}
