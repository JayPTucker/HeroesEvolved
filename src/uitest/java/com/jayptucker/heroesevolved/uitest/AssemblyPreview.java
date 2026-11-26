package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.assembly.Matter;
import com.jayptucker.heroesevolved.assembly.MatterAssemblyService;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Matter Assembly, the engineer, photographed in the Overworld at noon: the Matter orbit, a Panel folding together
 * (blueprint, fragments, snap, sheen; and in first person), a Shard ricocheting off it into a pillager, a Launch Pad
 * laid, throwing a foe up and the caster along, Kaleidoscope, Sentinel Engine, Skywalk, and Matter flying home.
 * Flag {@code heroes.preview.assembly}; shots in {@code build/assembly-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class AssemblyPreview {
    private static boolean started;
    private static int ticks, world, photos;
    private static String pending;
    private static boolean cleanHud;
    private static Vec3 aimPoint = Vec3.ZERO;
    private static final double X = 2000.5, Y = -60, Z = .5;

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (player != null) action.accept(player);
            } catch (Throwable t) { PreviewQuiet.report("ASSEMBLY FAIL " + t); }
        });
    }
    private static void check(String name, boolean ok) { PreviewQuiet.report("ASSEMBLY " + (ok ? "PASS " : "FAIL ") + name); }
    private static void photo(String name) { pending = "assembly-" + name + ".png"; }
    private static AbilityUseContext context(ServerPlayer p) { return new AbilityUseContext(p, ModAbilities.MATTER_ASSEMBLY_ID, 5, false); }
    private static boolean cast(ServerPlayer p, String action) {
        PlayerEnergyService.restore(p, 999);
        return MatterAssemblyService.activate(context(p), action);
    }
    private static void place(ServerPlayer p, double dx, double dz, float yaw, float pitch) {
        p.teleportTo(p.server.overworld(), X + dx, Y, Z + dz, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO);
    }
    private static void aim(ServerPlayer p, Vec3 point) {
        Vec3 d = point.subtract(p.getEyePosition());
        p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), (float) Math.toDegrees(Math.atan2(-d.x, d.z)),
                (float) Math.toDegrees(-Math.atan2(d.y, Math.hypot(d.x, d.z))));
    }
    private static Mob dummy(ServerPlayer p, EntityType<? extends Mob> type, double dx, double dz, boolean ai) {
        Mob mob = type.create(p.serverLevel());
        mob.moveTo(X + dx, Y, Z + dz, 180, 0); mob.setNoAi(!ai); mob.setPersistenceRequired();
        mob.getPersistentData().putBoolean(com.jayptucker.heroesevolved.gifted.GiftedMobs.ROLLED, true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400); mob.setHealth(400);
        p.serverLevel().addFreshEntity(mob);
        return mob;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.assembly")) return;
        var mc = Minecraft.getInstance();
        if (!started && ++ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true; mc.options.pauseOnLostFocus = false; mc.options.guiScale().set(2); mc.options.fov().set(80);
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Assembly-" + System.currentTimeMillis(),
                    new LevelSettings("Assembly preview", GameType.SURVIVAL, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        mc.getToasts().clear();
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        switch (++world) {
            case 160 -> server(mc, p -> {
                // Out of the Last Day (its own dimension) and the bunker, into the Overworld, well away from spawn.
                PrologueService.skip(p); CryoChamberService.release(p);
                for (int cx = 122; cx <= 128; cx++) for (int cz = -2; cz <= 3; cz++) p.server.overworld().setChunkForced(cx, cz, true);
                p.server.overworld().setDayTime(6000);
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                        .assignDormant(ModAbilities.MATTER_ASSEMBLY_ID).unlock(ModAbilities.MATTER_ASSEMBLY_ID));
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.MATTER_ASSEMBLY_ID));
                PlayerProgressionService.setLevel(p, 5);
                p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.MATTER_ASSEMBLY_ID.toString(),
                        new SkillTreeData.Build(15, Set.of("matter_assembly_bank_shot", "matter_assembly_faultline", "matter_assembly_buttress"), Map.of())));
                place(p, 0, 0, 0, 8);
                Matter.fill(p);
            });
            case 190 -> server(mc, p -> Matter.fill(p));
            // The orbit, from the front.
            case 205 -> { cleanHud = true; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                TempestCamera.fix(new Vec3(X + 1.9, Y + 1.9, Z + 2.4), new Vec3(X, Y + 1.1, Z + .5)); }

            // A Panel folding together on the ground ahead, seen from the side.
            case 228 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); server(mc, p -> place(p, 0, 0, 0, 10));
                TempestCamera.fix(new Vec3(X + 6.5, Y + 3, Z + 4), new Vec3(X, Y + 1, Z + 8.5)); }
            case 232 -> server(mc, p -> check("panel starts", cast(p, "barricade")));
            case 234 -> photo("02-panel-blueprint");
            case 237 -> photo("03-panel-fragments");
            case 240 -> photo("04-panel-snap");
            case 248 -> { photo("05-panel-sheen"); server(mc, p -> check("panel stands, wings and all", MatterAssemblyService.cells(p).size() == 10)); }
            // A Shard thrown at the panel's corner ricochets back into a pillager standing beside her.
            case 255 -> server(mc, p -> {
                place(p, 0, 0, 0, 0);
                var cells = MatterAssemblyService.cells(p);
                int far = cells.stream().mapToInt(net.minecraft.core.BlockPos::getZ).max().orElseThrow();
                var corner = cells.stream().filter(c -> c.getZ() == far).max(java.util.Comparator.comparingInt((net.minecraft.core.BlockPos c) -> c.getX()).thenComparingInt(c -> c.getY())).orElseThrow();
                Vec3 eye = p.getEyePosition(), hit = new Vec3(corner.getX() + .5, eye.y, corner.getZ() - .02);
                aimPoint = hit;
                Vec3 d = hit.subtract(eye).normalize();
                double k = (hit.z - eye.z - 2.5) / d.z;
                Vec3 back = hit.add(new Vec3(d.x, d.y, -d.z).scale(k));
                dummy(p, EntityType.PILLAGER, back.x - X, back.z - Z, false);
                TempestCamera.fix(new Vec3(X - 5, Y + 6.5, Z - 2.5), new Vec3(X + 1, Y + 1, Z + 5.5));
            });
            case 262 -> server(mc, p -> { aim(p, aimPoint); check("shard fires", cast(p, "shard_lance")); });
            case 264 -> photo("06-shard-flight");
            case 266 -> photo("07-shard-ricochet");
            case 268 -> photo("07b-shard-home");
            // A floor laid out over a drop: she stands on a pillar and looks down past its edge.
            case 280 -> server(mc, p -> {
                for (int y = 0; y < 4; y++) p.serverLevel().setBlock(net.minecraft.core.BlockPos.containing(X - 8, Y + y, Z), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), 3);
                p.teleportTo(p.serverLevel(), X - 8, Y + 4, Z, 0, 55); p.setDeltaMovement(Vec3.ZERO);
                TempestCamera.fix(new Vec3(X - 2.5, Y + 6, Z + 1), new Vec3(X - 8, Y + 3.5, Z + 2));
            });
            case 284 -> server(mc, p -> check("floor starts", cast(p, "barricade")));
            case 296 -> photo("08-panel-floor");
            case 299 -> TempestCamera.fix(new Vec3(X - 6.3, Y + 5.9, Z + 1.6), new Vec3(X - 8, Y + 5.1, Z));
            case 302 -> photo("01-orbit");
            // First person: the hands frame the shape as it folds together; then a flick throws a Shard.
            case 305 -> { TempestCamera.fix(null, null); mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(mc, p -> { MatterAssemblyService.dismantle(p); Matter.fill(p); place(p, -14, -4, 30, 12); }); }
            case 312 -> server(mc, p -> cast(p, "barricade"));
            case 316 -> photo("09-panel-first-person");
            case 326 -> server(mc, p -> cast(p, "shard_lance"));
            case 327 -> photo("10-shard-first-person");
            // Launch Pad: laid, held, a foe thrown up, then the caster thrown along; seen from high on the side.
            case 335 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); server(mc, p -> {
                MatterAssemblyService.dismantle(p); Matter.fill(p); place(p, 12, 0, 0, 30);
                check("pad lays", cast(p, "cage") && MatterAssemblyService.pads(p) == 1);
            }); TempestCamera.fix(new Vec3(X + 18, Y + 5, Z + 1), new Vec3(X + 12, Y + .5, Z + 3.5)); }
            case 339 -> photo("11-pad-laid");
            case 356 -> photo("12-pad-held");
            case 360 -> server(mc, p -> dummy(p, EntityType.HUSK, 12, 3.3, true));
            case 364 -> photo("13-pad-foe-thrown");
            case 371 -> photo("14-pad-foe-airborne");
            case 385 -> server(mc, p -> { p.teleportTo(p.serverLevel(), X + 12, Y, Z + 3.3, 0, 10); });
            case 389 -> photo("15-pad-friend-thrown");
            case 395 -> photo("16-pad-friend-flight");
            // Kaleidoscope round a pillager.
            case 420 -> { server(mc, p -> {
                Mob boxed = dummy(p, EntityType.PILLAGER, 24, 7, false); place(p, 24, 0, 0, 0);
                aim(p, boxed.getBoundingBox().getCenter());
                check("kaleidoscope starts", cast(p, "kaleidoscope"));
            }); TempestCamera.fix(new Vec3(X + 28.5, Y + 4.5, Z + 3.5), new Vec3(X + 24, Y + 1, Z + 7.5)); }
            case 426 -> photo("17-kaleidoscope-forming");
            case 452 -> photo("18-kaleidoscope");
            // Sentinel Engine among pillagers.
            case 470 -> { server(mc, p -> {
                MatterAssemblyService.dismantle(p); Matter.fill(p); place(p, 36, 0, 0, 12);
                dummy(p, EntityType.PILLAGER, 31, 9, false); dummy(p, EntityType.PILLAGER, 41, 11, false);
                check("sentinels rise", cast(p, "cathedral"));
            }); TempestCamera.fix(new Vec3(X + 43, Y + 6, Z - 5), new Vec3(X + 36, Y + 1, Z + 3)); }
            case 484 -> photo("19-sentinels-rise");
            case 505 -> photo("20-sentinels-fire");
            // Skywalk: stepping out into the air, from the side.
            case 520 -> { server(mc, p -> { MatterAssemblyService.dismantle(p); Matter.fill(p); p.teleportTo(p.serverLevel(), X + 48, Y + 5, Z, 0, 25); cast(p, "causeway"); });
                TempestCamera.fix(new Vec3(X + 54, Y + 6.5, Z + 2), new Vec3(X + 48, Y + 4.5, Z + 1)); }
            case 525 -> photo("21-skywalk");
            // Matter flying home: two panels taken apart at once.
            case 540 -> { server(mc, p -> { place(p, 60, 0, 0, 22); cast(p, "barricade"); p.setYRot(70); cast(p, "barricade"); });
                TempestCamera.fix(new Vec3(X + 64, Y + 3, Z + 5), new Vec3(X + 60, Y + 1.2, Z + 2)); }
            case 560 -> server(mc, p -> { p.setXRot(-8); MatterAssemblyService.dismantle(p); });
            case 565 -> photo("22-matter-home");
            case 570 -> photo("22b-matter-home");
            // Minimal particles: the main cues stay.
            case 580 -> { mc.options.particles().set(ParticleStatus.MINIMAL);
                server(mc, p -> { Matter.fill(p); place(p, 72, 0, 0, 10); cast(p, "barricade"); });
                TempestCamera.fix(new Vec3(X + 78, Y + 3, Z + 4), new Vec3(X + 72, Y + 1, Z + 8.5)); }
            case 589 -> photo("23-minimal");
            case 600 -> { TempestCamera.fix(null, null); check("all twenty-five photographs", photos == 25); PreviewQuiet.quit(); }
            default -> { }
        }
    }

    @SubscribeEvent public static void hud(net.neoforged.neoforge.client.event.RenderGuiEvent.Pre event) {
        if (cleanHud && Boolean.getBoolean("heroes.preview.assembly")) event.setCanceled(true);
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            var path = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(path.getParent()); image.writeToFile(path); photos++;
            System.out.println("ASSEMBLY_CAPTURE " + pending);
        } catch (Exception ex) { PreviewQuiet.report("ASSEMBLY FAIL screenshot " + ex); }
        pending = null;
    }
}
