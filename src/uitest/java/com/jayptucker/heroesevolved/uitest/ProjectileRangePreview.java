package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.cryo.CryoCombatSkills;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.pyro.PyroService;
import com.jayptucker.heroesevolved.quake.QuakeService;
import com.jayptucker.heroesevolved.vibe.VibeService;
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
import java.nio.file.Path;
import java.util.function.Consumer;

/** One loaded overworld firing lane: photographs long shots and logs damage at their actual destinations. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class ProjectileRangePreview {
    private static boolean started;
    private static int ticks, world;
    private static String pending;
    private static Mob target;
    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance();
        mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            if (player != null) action.accept(player);
        });
    }
    private static void scene(ServerPlayer p, ResourceLocation power, double distance) {
        PyroService.forget(p); QuakeService.stopAll(p); CryoCombatSkills.forget(p); VibeService.forget(p);
        if (target != null) target.discard();
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(power));
        PlayerProgressionService.setLevel(p, 15); PlayerEnergyService.restore(p, 999);
        p.connection.teleport(202.5, -60, 202.5, 0, 0);
        p.setDeltaMovement(Vec3.ZERO); p.setNoGravity(true);
        target = EntityType.PILLAGER.create(p.serverLevel());
        target.setNoAi(true); target.setNoGravity(true); target.setPersistenceRequired();
        target.setPos(p.getX(), p.getY(), p.getZ() + distance);
        p.serverLevel().addFreshEntity(target);
        PlayerPowerSyncService.sync(p);
        System.out.println("PROJECTILE_SCENE " + power + " range=" + distance + " dimension=" + p.level().dimension().location()
                + " floor=" + p.level().getBlockState(p.blockPosition().below()));
    }
    private static void report(String name) {
        server(p -> System.out.println("PROJECTILE_HIT " + name + " health=" + target.getHealth() + "/" + target.getMaxHealth()));
    }
    private static void side() { TempestCamera.fix(new Vec3(218, -54, 211), new Vec3(202.5, -58.5, 230)); }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.projectiles")) return;
        var mc = Minecraft.getInstance();
        if (!started && ++ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true; mc.options.pauseOnLostFocus = false;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_DOFIRETICK).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("ProjectileRange-" + System.currentTimeMillis(),
                    new LevelSettings("Projectile range", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules,
                            WorldDataConfiguration.DEFAULT), new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        switch (++world) {
            case 100 -> server(com.jayptucker.heroesevolved.story.prologue.PrologueService::skip);
            case 115 -> server(p -> {
                com.jayptucker.heroesevolved.story.bunker.CryoChamberService.release(p);
                var level = p.server.overworld();
                for (int x = 12; x <= 14; x++) for (int z = 12; z <= 18; z++) level.setChunkForced(x, z, true);
                p.teleportTo(level, 202.5, -60, 202.5, 0, 0); level.setDayTime(6000);
                scene(p, ModAbilities.PYROKINESIS_ID, 78);
            });
            case 185 -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); side(); }
            case 200 -> server(p -> PyroService.flare(p, 1));
            case 203 -> pending = "01-fireball-80-side.png";
            case 217 -> report("fireball-78");
            case 225 -> { TempestCamera.fix(null, null); mc.options.setCameraType(CameraType.FIRST_PERSON); }
            case 235 -> server(p -> { scene(p, ModAbilities.PYROKINESIS_ID, 78); PyroService.flare(p, 1); });
            case 238 -> pending = "02-fireball-80-first-person.png";
            case 255 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); side(); server(p -> scene(p, ModAbilities.QUAKE_ID, 39)); }
            case 270 -> server(p -> QuakeService.shockwave(p, 1));
            case 274 -> pending = "03-quake-40-side.png";
            case 285 -> report("quake-39");
            case 300 -> server(p -> scene(p, ModAbilities.CRYOKINESIS_ID, 60));
            case 315 -> server(p -> CryoCombatSkills.activate(new AbilityUseContext(p, ModAbilities.CRYOKINESIS_ID, 5, false), 0));
            case 338 -> pending = "04-crystal-lance-flight.png";
            case 365 -> report("crystal-lance-60");
            case 380 -> server(p -> scene(p, ModAbilities.VIBE_ID, 45));
            case 395 -> server(p -> VibeService.blast(new AbilityUseContext(p, ModAbilities.VIBE_ID, 5, false), VibeService.Blast.PALM));
            case 407 -> pending = "05-vibe-48-side.png";
            case 425 -> report("vibe-45");
            case 440 -> PreviewQuiet.quit();
            default -> {}
        }
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("PROJECTILE_CAPTURE " + out);
        } catch (Exception e) { throw new RuntimeException(e); }
        pending = null;
    }
}
