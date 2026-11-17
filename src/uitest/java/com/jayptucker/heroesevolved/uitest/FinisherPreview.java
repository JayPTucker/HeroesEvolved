package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.story.actor.StoryActorEntity;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Photographs the finishers into {@code build/finisher-preview/screenshots/}, then closes. Flag
 * {@code heroes.preview.finisher}; {@code heroes.preview.finisher.only} (comma-separated power paths) keeps some.
 *
 * <p>Each finisher is shot twice: once from its own finisher camera (the side view the player gets), once from a still
 * camera behind and above the player. The first shot of each is the glisten and the "[C] Finish" prompt, HUD on.</p>
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class FinisherPreview {
    private static final int STAGE = 150, FIRST = 140, SCENE = 90, SHOOT = 30, GO = 34;

    private record Scene(ResourceLocation power, Takedowns.Finisher finisher, String name, double ahead, int[] beats, boolean behind) {}

    private static final List<Scene> SCENES = new ArrayList<>();
    private static boolean started;
    private static int startup, age, beat;
    private static String capture;

    static {
        String only = System.getProperty("heroes.preview.finisher.only", "");
        // Every finisher that is built, shot at the opening, two beats before the blow, the blow and its aftermath.
        for (Takedowns.Finisher f : Takedowns.all()) {
            ResourceLocation power = f.power();
            // Keep by power path or by finisher id. Borrowed Strength needs a kept power, so it is left out.
            if (!only.isEmpty() && !List.of(only.split(",")).contains(power.getPath()) && !List.of(only.split(",")).contains(f.id())) continue;
            if (f == com.jayptucker.heroesevolved.mimicry.MimicFinishers.BORROWED_STRENGTH) continue;
            int land = f.landAt();
            int[] beats = {3, Math.max(4, land / 2), land - 3, land + 1, Math.min(f.ticks() - 1, land + 5)};
            for (boolean behind : new boolean[]{false, true})
                SCENES.add(new Scene(power, f, f.id().replace('_', '-'), f.reach() > Takedowns.REACH ? 5.0 : 2.5, beats, behind));
        }
    }

    private static boolean on() { return Boolean.getBoolean("heroes.preview.finisher"); }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!on()) return;
        var mc = Minecraft.getInstance();
        if (!started && ++startup >= 35 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(5);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Finishers-" + System.currentTimeMillis(),
                    new LevelSettings("Finisher Preview", GameType.CREATIVE, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(91L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (age >= 100) mc.getToasts().clear();
        ++age;
        if (age == 104) { server(mc, com.jayptucker.heroesevolved.story.prologue.PrologueService::skip); return; }
        if (age == 110) {
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
            server(mc, p -> {
                CryoChamberService.release(p);
                p.setGameMode(GameType.SURVIVAL);
                var world = p.getServer().overworld();
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                    world.setChunkForced((STAGE >> 4) + dx, (STAGE >> 4) + dz, true);
                world.setDayTime(6000);
                p.teleportTo(world, STAGE + .5, -60, STAGE + .5, 0, 12);
                System.out.println("FINISHER_PREVIEW staged in " + p.level().dimension().location() + " on "
                        + world.getBlockState(p.blockPosition().below()));
            });
            return;
        }
        if (age % 20 == 0) com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
        if (age < FIRST) return;
        int index = (age - FIRST) / SCENE, local = (age - FIRST) % SCENE;
        if (index >= SCENES.size()) {
            if (local == 5) { TempestCamera.fix(null, null); PreviewQuiet.quit(); }
            return;
        }
        Scene scene = SCENES.get(index);
        String shot = "finisher-" + scene.name + "-" + (scene.behind ? "behind" : "camera");
        if (local == 0) {
            mc.options.hideGui = true;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            TempestCamera.fix(null, null);
            server(mc, p -> stage(p, scene));
        }
        if (local == 6 && scene.behind) {
            // Behind and above the player, looking past them at the foe.
            Vec3 at = new Vec3(STAGE + .5 - 1.4, -60 + 3.2, STAGE + .5 - 4.2);
            TempestCamera.fix(at, new Vec3(STAGE + .5, -59, STAGE + .5 + scene.ahead));
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        if (local == SHOOT - 1) mc.options.hideGui = false;
        if (local == SHOOT) capture = shot + "-0-glisten.png";
        if (local == SHOOT + 1) mc.options.hideGui = true;
        if (local == GO) server(mc, p -> {
            Mob foe = p.serverLevel().getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(8),
                    m -> m.getTags().contains("finisher_preview_foe")).stream().findFirst().orElse(null);
            boolean ok = foe != null && Takedowns.request(p, foe.getId(), scene.finisher);
            System.out.println("FINISHER_PREVIEW " + scene.name + " started " + ok + " finisher " + scene.finisher.id()
                    + " foe " + (foe == null ? "none" : foe.getHealth() + "/" + Takedowns.threshold(foe)));
        });
        if (local == 0) beat = 0;
        // Shoot on the server's own clock for the move: an integrated server that is catching up runs ahead of the
        // client's ticks, so counting client ticks drifts.
        if (local > GO && beat < scene.beats.length) {
            var sp = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            Takedowns.Run run = sp == null ? null : Takedowns.run(sp);
            if (run != null && run.age() >= scene.beats[beat]) {
                capture = shot + "-" + (beat + 1) + "-t" + scene.beats[beat] + ".png";
                beat++;
            }
        }
        if (local == GO + 40) server(mc, p -> {
            long left = p.serverLevel().getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(10),
                    m -> m.getTags().contains("finisher_preview_foe") && m.isAlive()).size();
            System.out.println("FINISHER_PREVIEW " + scene.name + " foe left standing: " + left);
        });
    }

    /** The player on the stage with the power, a low pillager in front and a healthy one beside or beyond it. */
    private static void stage(ServerPlayer p, Scene scene) {
        var world = p.serverLevel();
        for (Mob old : world.getEntitiesOfClass(Mob.class, new AABB(STAGE - 20, -70, STAGE - 20, STAGE + 20, -40, STAGE + 20))) old.discard();
        for (StoryActorEntity actor : world.getEntitiesOfClass(StoryActorEntity.class, new AABB(STAGE - 20, -70, STAGE - 20, STAGE + 20, -40, STAGE + 20)))
            actor.discard();
        var power = scene.power;
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(power));
        // The last power's drawn state (Hydrokinesis's water round the waist) goes with it, as a real power change does.
        com.jayptucker.heroesevolved.hydro.HydroService.powerChanged(p);
        PlayerProgressionService.setPowerLevel(p, 10);
        p.teleportTo(world, STAGE + .5, -60, STAGE + .5, 0, 12);
        p.setHealth(p.getMaxHealth());
        PlayerEnergyService.restore(p, 999);
        Takedowns.resetCooldown(p);
        com.jayptucker.heroesevolved.network.PlayerPowerSyncService.sync(p);
        PlayerEnergyService.sendNow(p);
        Takedowns.sync(p, true);
        Mob foe = pillager(world, new Vec3(STAGE + .5, -60, STAGE + .5 + scene.ahead), 3);
        foe.addTag("finisher_preview_foe");

        pillager(world, new Vec3(STAGE + .5 + 1.8, -60, STAGE + .5 + scene.ahead + 1.2), 24);
        System.out.println("FINISHER_PREVIEW staged " + scene.name + (scene.behind ? " (behind)" : "") + " level "
                + PlayerProgressionService.getLevel(p) + " finisher " + Takedowns.of(p));
    }

    private static Mob pillager(net.minecraft.server.level.ServerLevel world, Vec3 at, float health) {
        Mob mob = EntityType.PILLAGER.create(world);
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        mob.moveTo(at.x, at.y, at.z, 180, 0);
        mob.setYHeadRot(180);
        mob.setYBodyRot(180);
        mob.setHealth(health);
        world.addFreshEntity(mob);
        return mob;
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (capture == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
            System.out.println("FINISHER_CAPTURE " + capture);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        capture = null;
    }
}
