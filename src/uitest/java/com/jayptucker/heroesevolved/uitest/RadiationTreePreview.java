package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.intro.PowerIntroHistory;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.radiation.RadiationLeap;
import com.jayptucker.heroesevolved.radiation.RadiationService;
import com.jayptucker.heroesevolved.radiation.RadiationSickness;
import com.jayptucker.heroesevolved.radiation.RadiationSkills;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
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
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Casts each of Induced Radioactivity's skill-tree abilities at dusk, 600 blocks from spawn, and photographs them. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class RadiationTreePreview {
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

    private static LivingEntity spawn(ServerPlayer p, EntityType<? extends LivingEntity> type, double x, double z) {
        var mob = type.create(p.serverLevel());
        if (mob instanceof net.minecraft.world.entity.Mob m) { m.setNoAi(true); m.setPersistenceRequired(); }
        mob.moveTo(x + 0.5, -60, z + 0.5, 180, 0);
        mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);
        mob.setHealth(500);
        p.serverLevel().addFreshEntity(mob);
        MOBS.add(mob);
        return mob;
    }

    private static void clearMobs() { MOBS.forEach(LivingEntity::discard); MOBS.clear(); }

    private static AbilityUseContext cast(ServerPlayer p) { return new AbilityUseContext(p, ModAbilities.RADIATION_ID, 5, false); }

    private static void core(ServerPlayer p, int value) {
        RadiationService.vent(p, 100);
        if (value > 0) RadiationService.addMass(p, value);
        PlayerEnergyService.restore(p, 999);
    }

    private static void shot(String name) { pending = "tree-" + name + ".png"; }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.radtree")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("RadTree-" + System.currentTimeMillis(),
                    new LevelSettings("Radiation tree preview", GameType.SURVIVAL, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(91L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        switch (++worldTicks) {
            case 150 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(13000);
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, PowerIntroHistory.empty().with(ModAbilities.RADIATION_ID));
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                        .assignDormant(ModAbilities.RADIATION_ID).unlock(ModAbilities.RADIATION_ID));
                PlayerProgressionService.setLevel(p, 5);
                p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.RADIATION_ID.toString(),
                        new SkillTreeData.Build(15, Set.of("radiation_0_0", "radiation_0_1", "radiation_0_2", "radiation_heat_vent",
                                "radiation_clean_vent", "radiation_containment", "radiation_1_0", "radiation_1_1", "radiation_1_2",
                                "radiation_reactor_core", "radiation_power_transfer", "radiation_reactor", "radiation_2_0", "radiation_2_1",
                                "radiation_2_2", "radiation_glowing_path", "radiation_wider_path", "radiation_catastrophe"), Map.of())));
                p.getAbilities().invulnerable = true; p.onUpdateAbilities();
            });
            case 156 -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
            // Gamma Lance, charged with Core, through a line of pillagers.
            case 160 -> onServer(mc, p -> place(p, 600, 0, 0, 2));
            case 172 -> onServer(mc, p -> { for (int z : new int[] {6, 12, 18}) spawn(p, EntityType.PILLAGER, 600, z); });
            case 176 -> { mc.player.setYRot(0); mc.player.setXRot(2); }
            case 180 -> onServer(mc, p -> { core(p, 30); RadiationSkills.skill(cast(p), 0); });
            case 181 -> shot("1-gamma-lance-a");
            case 183 -> shot("1-gamma-lance-b");
            case 186 -> shot("1-gamma-lance-c");
            case 188 -> onServer(mc, p -> p.setYRot(-60));
            case 192 -> onServer(mc, p -> { p.setYRot(0); core(p, 30); RadiationSkills.skill(cast(p), 0); });
            case 190 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 194 -> shot("1-gamma-lance-front");
            case 196 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            // Heat Vent with Chain Vent: a sick pillager in the cone bursts onto the others.
            case 200 -> onServer(mc, p -> { clearMobs(); place(p, 600, 40, 0, 8);
                LivingEntity sick = spawn(p, EntityType.PILLAGER, 600, 45);
                spawn(p, EntityType.PILLAGER, 604, 43); spawn(p, EntityType.PILLAGER, 596, 46);
                RadiationSickness.dose(sick, p, 3, 400); });
            case 210 -> onServer(mc, p -> { core(p, 30); RadiationSkills.technique(p, "heat_vent"); });
            case 211 -> shot("2-heat-vent-a");
            case 213 -> shot("2-heat-vent-b");
            case 217 -> shot("2-heat-vent-c");
            // Lead Skin and Decontaminate, from the front.
            case 235 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { clearMobs(); place(p, 640, 0, 0, 5); }); }
            case 245 -> onServer(mc, p -> RadiationSkills.skill(cast(p), 1));
            case 247 -> shot("3-lead-skin-a");
            case 252 -> shot("3-lead-skin-b");
            case 270 -> onServer(mc, p -> { p.addEffect(new MobEffectInstance(MobEffects.POISON, 200)); p.igniteForSeconds(5); RadiationSkills.skill(cast(p), 2); });
            case 272 -> shot("4-decontaminate-a");
            case 277 -> shot("4-decontaminate-b");
            // Reactor Core, turning Core into stamina.
            case 295 -> onServer(mc, p -> { core(p, 40); RadiationSkills.technique(p, "reactor_core"); });
            case 300 -> shot("5-reactor-core-a");
            case 306 -> shot("5-reactor-core-b");
            // Glowing Path: walking leaves contaminated ground behind.
            case 320 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { place(p, 680, 0, 180, 30); RadiationSkills.technique(p, "glowing_path"); }); }
            case 325 -> mc.options.keyUp.setDown(true);
            case 360 -> { mc.options.keyUp.setDown(false); mc.player.setYRot(0); mc.player.setXRot(35); }
            case 364 -> shot("6-glowing-path-a");
            case 375 -> shot("6-glowing-path-b");
            // Fission Leap with Fallout Boots, Second Leap, then a slam back into the patch: Catastrophe.
            case 395 -> onServer(mc, p -> { place(p, 720, 0, 0, 15);
                for (double[] at : new double[][] {{725, 3}, {714, 4}, {720, 8}, {726, -5}}) spawn(p, EntityType.PILLAGER, at[0], at[1]); });
            case 405 -> onServer(mc, p -> { core(p, 20); RadiationLeap.launch(p, 5); });
            case 407 -> shot("7-leap-boots");
            case 413 -> onServer(mc, p -> { p.setYRot(180); RadiationLeap.launch(p, 5); });
            case 415 -> shot("7-second-leap");
            case 424 -> onServer(mc, p -> { p.setXRot(75); RadiationLeap.slam(p); });
            case 430 -> shot("7-catastrophe-a");
            case 434 -> shot("7-catastrophe-b");
            case 445 -> shot("7-catastrophe-c");
            // Supercritical: a kill while Critical bursts.
            case 465 -> onServer(mc, p -> { clearMobs(); place(p, 760, 0, 0, 10); spawn(p, EntityType.PILLAGER, 760, 6); spawn(p, EntityType.PILLAGER, 762, 7); });
            case 475 -> onServer(mc, p -> { core(p, 85); if (!MOBS.isEmpty()) RadiationSkills.onKill(p, MOBS.getFirst()); });
            case 476 -> shot("8-supercritical-a");
            case 480 -> shot("8-supercritical-b");
            // Breeder Reactor: 100 Core runs into Overdrive instead of a meltdown.
            case 500 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { clearMobs(); place(p, 800, 0, 0, 5); core(p, 99); RadiationService.addMass(p, 1); }); }
            case 505 -> shot("9-overdrive-a");
            case 515 -> shot("9-overdrive-b");
            case 520 -> { mc.options.hideGui = false; }
            case 524 -> shot("9-overdrive-hud");
            case 530 -> onServer(mc, p -> RadiationService.vent(p, 100));
            case 545 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("RADTREE_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
