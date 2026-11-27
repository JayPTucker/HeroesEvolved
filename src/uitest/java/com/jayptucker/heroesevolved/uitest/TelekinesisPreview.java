package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.telekinesis.KineticStyle;
import com.jayptucker.heroesevolved.telekinesis.TelekinesisService;
import com.jayptucker.heroesevolved.telekinesis.TelekinesisSkillService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Telekinesis, the far hand, photographed in the Overworld at noon from side cameras with real foes: Shove's palm along
 * its cone, Grip held, swung through a foe and thrown, Bullet Catch's cupped hands (and first person), Disarm's pluck and
 * fling, the Storm's gather, lift and slam, Pin against a wall, Juggle and Spike, Skyfall's haul, hang and impact,
 * Levitate, Round Up, Clench and Warding Hands, the Puppeteer's swat, and minimal particles.
 * Flag {@code heroes.preview.telekinesis}; shots in {@code build/telekinesis-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class TelekinesisPreview {
    private static boolean started;
    private static int ticks, world, photos, expected;
    private static String pending;
    private static boolean cleanHud;
    private static final double X = 2000.5, Y = -60, Z = .5;
    private static final List<Mob> ACTORS = new ArrayList<>();

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (player != null) action.accept(player);
            } catch (Throwable t) { PreviewQuiet.report("KINETIC FAIL " + t); }
        });
    }
    private static void check(String name, boolean ok) { PreviewQuiet.report("KINETIC " + (ok ? "PASS " : "FAIL ") + name); }
    private static void photo(String name) { pending = "kinetic-" + name + ".png"; expected++; }
    private static void refill(ServerPlayer p) { PlayerEnergyService.restore(p, 999); com.jayptucker.heroesevolved.cooldown.CooldownService.clearAll(p); }
    private static boolean skill(ServerPlayer p, String id, boolean modifier) { refill(p); return TelekinesisSkillService.activate(p, id, modifier); }
    private static void place(ServerPlayer p, double dx, double dz, float yaw, float pitch) {
        p.teleportTo(p.server.overworld(), X + dx, Y, Z + dz, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO);
    }
    private static void aim(ServerPlayer p, Vec3 point) {
        Vec3 d = point.subtract(p.getEyePosition());
        p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), (float) Math.toDegrees(Math.atan2(-d.x, d.z)),
                (float) Math.toDegrees(-Math.atan2(d.y, Math.hypot(d.x, d.z))));
    }
    /** A foe that obeys physics (so the far hand can move it) but stands still: AI on, every goal removed. */
    private static Mob puppet(ServerPlayer p, EntityType<? extends Mob> type, double dx, double dz) {
        Mob mob = type.create(p.serverLevel());
        mob.moveTo(X + dx, Y, Z + dz, 180, 0); mob.setPersistenceRequired();
        mob.getPersistentData().putBoolean(com.jayptucker.heroesevolved.gifted.GiftedMobs.ROLLED, true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400); mob.setHealth(400);
        p.serverLevel().addFreshEntity(mob);
        mob.goalSelector.removeAllGoals(goal -> true);
        mob.targetSelector.removeAllGoals(goal -> true);
        ACTORS.add(mob);
        return mob;
    }
    private static void reset(ServerPlayer p) {
        TelekinesisService.stopAll(p);
        for (Mob mob : ACTORS) mob.discard();
        ACTORS.clear();
        refill(p);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.telekinesis")) return;
        var mc = Minecraft.getInstance();
        if (!started && ++ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true; mc.options.pauseOnLostFocus = false; mc.options.guiScale().set(2); mc.options.fov().set(80);
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Kinetic-" + System.currentTimeMillis(),
                    new LevelSettings("Telekinesis preview", GameType.SURVIVAL, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        mc.getToasts().clear();
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        switch (++world) {
            case 160 -> server(mc, p -> {
                // Out of the Last Day and the bunker, into the Overworld, well away from spawn.
                PrologueService.skip(p); CryoChamberService.release(p);
                for (int cx = 122; cx <= 132; cx++) for (int cz = -3; cz <= 4; cz++) p.server.overworld().setChunkForced(cx, cz, true);
                p.server.overworld().setDayTime(6000);
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                        .assignDormant(ModAbilities.TELEKINESIS_ID).unlock(ModAbilities.TELEKINESIS_ID));
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.TELEKINESIS_ID));
                PlayerProgressionService.setLevel(p, 5);
                p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.TELEKINESIS_ID.toString(),
                        new SkillTreeData.Build(15, Set.of(TelekinesisSkillService.PIN, "telekinesis_0_2", TelekinesisSkillService.ROUND_UP,
                                TelekinesisSkillService.JUGGLE, "telekinesis_iron_rain", "telekinesis_1_0", "telekinesis_1_2",
                                TelekinesisSkillService.SKYFALL, TelekinesisSkillService.LEVITATE, "telekinesis_wider_bubble",
                                "telekinesis_bastion", TelekinesisSkillService.STEP), Map.of())));
                com.jayptucker.heroesevolved.network.PlayerPowerSyncService.sync(p);
                place(p, 0, 0, 0, 5);
                PreviewQuiet.report("KINETIC staged in " + p.level().dimension().location() + " on " + p.level().getBlockState(p.blockPosition().below()));
            });

            // ---- Shove: the palm flies along the cone into three pillagers; seen from the side.
            case 860 -> { cleanHud = true; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); server(mc, p -> {
                reset(p); place(p, 0, 0, 0, 4);
                puppet(p, EntityType.PILLAGER, 0, 6); puppet(p, EntityType.PILLAGER, 1.6, 8); puppet(p, EntityType.PILLAGER, -1.4, 10);
            }); TempestCamera.fix(new Vec3(X + 8, Y + 3, Z + 4), new Vec3(X, Y + 1.3, Z + 6)); }
            case 870 -> server(mc, p -> { refill(p); TelekinesisService.push(p, 5); });
            case 873 -> photo("01-shove-leaving");
            case 875 -> photo("02-shove-reaching");
            case 879 -> photo("03-shove-landed");

            // ---- Grip: a husk held beside the view, swung through a pillager, then thrown.
            case 240 -> { cleanHud = true; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
            case 241 -> server(mc, p -> {
                reset(p); place(p, 14, 0, 0, 10);
                Mob held = puppet(p, EntityType.HUSK, 14, 4);
                aim(p, held.getBoundingBox().getCenter());
                refill(p); check("grip takes hold", TelekinesisService.grip(p, 5));
                TempestCamera.fix(new Vec3(X + 21, Y + 3, Z + 2), new Vec3(X + 15, Y + 1.5, Z + 3));
            });
            case 256 -> photo("04-grip-held");
            case 258 -> server(mc, p -> puppet(p, EntityType.PILLAGER, 16.5, 1.2));
            case 262 -> server(mc, p -> p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), -40, 10));
            case 263 -> server(mc, p -> p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), -80, 10));
            case 265 -> photo("05-grip-swing");
            case 268 -> server(mc, p -> { p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), 20, 0); check("grip throws", TelekinesisService.throwGripped(p)); });
            case 271 -> photo("06-grip-throw");

            // ---- Bullet Catch: arrows from a pillager stop in two arcs before the cupped hands.
            case 290 -> server(mc, p -> {
                reset(p); place(p, 28, 0, 0, 0);
                Mob archer = puppet(p, EntityType.PILLAGER, 28, 12);
                refill(p); TelekinesisService.bulletStop(p, 5);
                for (int i = 0; i < 8; i++) {
                    Arrow arrow = new Arrow(EntityType.ARROW, p.serverLevel());
                    arrow.setOwner(archer);
                    arrow.setPos(X + 28 + (i % 4 - 1.5) * .7, Y + 1.2 + (i / 4) * .5, Z + 5.5);
                    arrow.setDeltaMovement(0, 0, -.9);
                    p.serverLevel().addFreshEntity(arrow);
                }
                TempestCamera.fix(new Vec3(X + 32.5, Y + 2.6, Z + 5.5), new Vec3(X + 28, Y + 1.3, Z + 1.5));
            });
            case 306 -> { photo("07-catch-held"); server(mc, p -> check("shots caught", TelekinesisService.caughtCount(p) >= 4)); }
            case 309 -> { TempestCamera.fix(null, null); mc.options.setCameraType(CameraType.FIRST_PERSON); cleanHud = false; }
            case 314 -> photo("08-catch-first-person");
            case 316 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); cleanHud = true;
                TempestCamera.fix(new Vec3(X + 33, Y + 3, Z + 3), new Vec3(X + 28, Y + 1.3, Z + 6)); }
            case 318 -> server(mc, p -> TelekinesisService.releaseBulletStop(p));
            case 321 -> photo("09-catch-return");

            // ---- Disarm: a vindicator's axe plucked home, then flung back at an evoker.
            case 340 -> server(mc, p -> {
                reset(p); place(p, 42, 0, 0, 6);
                Mob vindicator = puppet(p, EntityType.VINDICATOR, 42, 5);
                vindicator.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                puppet(p, EntityType.EVOKER, 44, 9);
                aim(p, vindicator.getBoundingBox().getCenter());
                refill(p); TelekinesisService.disarm(p, 5);
                TempestCamera.fix(new Vec3(X + 48, Y + 3, Z + 3), new Vec3(X + 42.5, Y + 1.4, Z + 4));
            });
            case 344 -> photo("10-disarm-pluck");
            case 350 -> server(mc, p -> { aim(p, new Vec3(X + 44, Y + 1, Z + 9)); check("weapon flung", TelekinesisService.fling(p)); });
            case 353 -> photo("11-disarm-fling");

            // ---- Telekinetic Storm: the hands sweep in (nobody touched), lift and turn the crowd, slam.
            case 370 -> server(mc, p -> {
                reset(p); place(p, 60, 0, 0, 0);
                puppet(p, EntityType.PILLAGER, 64, 3); puppet(p, EntityType.PILLAGER, 57, 5); puppet(p, EntityType.VINDICATOR, 61, 7);
                puppet(p, EntityType.PILLAGER, 55, -2);
                refill(p); TelekinesisService.telekineticStorm(p, 5);
                TempestCamera.fix(new Vec3(X + 74, Y + 9, Z - 10), new Vec3(X + 60, Y + 1.5, Z + 1));
            });
            case 382 -> photo("12-storm-gather");
            case 422 -> photo("13-storm-lift");
            case 438 -> photo("14-storm-slam");

            // ---- Pin: a pillager driven into a wall and held there.
            case 460 -> server(mc, p -> {
                reset(p); place(p, 76, 0, 0, 4);
                for (int x = -2; x <= 2; x++) for (int y = 0; y < 4; y++)
                    p.serverLevel().setBlock(BlockPos.containing(X + 76 + x, Y + y, Z + 8), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                Mob foe = puppet(p, EntityType.PILLAGER, 76, 3);
                aim(p, foe.getBoundingBox().getCenter());
                check("pin drives", skill(p, TelekinesisSkillService.PIN, false));
                TempestCamera.fix(new Vec3(X + 81.5, Y + 2.8, Z + 3), new Vec3(X + 76, Y + 1.3, Z + 6.5));
            });
            case 464 -> photo("15-pin-drive");
            case 478 -> photo("16-pin-held");

            // ---- Juggle and Spike.
            case 500 -> server(mc, p -> {
                reset(p); place(p, 90, 0, 0, 4);
                Mob a = puppet(p, EntityType.PILLAGER, 90, 6); puppet(p, EntityType.PILLAGER, 91.5, 6.5); puppet(p, EntityType.HUSK, 88.6, 7);
                aim(p, a.getBoundingBox().getCenter());
                check("juggle", skill(p, TelekinesisSkillService.JUGGLE, false));
                TempestCamera.fix(new Vec3(X + 97, Y + 3.5, Z + 3), new Vec3(X + 90, Y + 2, Z + 6.5));
            });
            case 509 -> photo("17-juggle");
            case 520 -> server(mc, p -> check("spike", TelekinesisService.isHeld(ACTORS.getFirst())
                    && com.jayptucker.heroesevolved.ability.service.AbilityUseService.activateAssignedAction(p,
                    equipJuggle(p), false) == com.jayptucker.heroesevolved.ability.AbilityUseResult.SUCCESS));
            case 522 -> photo("18-spike");

            // ---- Skyfall: the haul, the hang, the impact; seen wide from the side.
            case 545 -> server(mc, p -> {
                reset(p); place(p, 104, 0, 0, 0);
                Mob foe = puppet(p, EntityType.VINDICATOR, 104, 8);
                puppet(p, EntityType.PILLAGER, 106.5, 9); puppet(p, EntityType.PILLAGER, 101.8, 7.2);
                aim(p, foe.getBoundingBox().getCenter());
                check("skyfall hauls", skill(p, TelekinesisSkillService.SKYFALL, false));
                TempestCamera.fix(new Vec3(X + 118, Y + 9, Z + 2), new Vec3(X + 104, Y + 7, Z + 8));
            });
            case 559 -> photo("19-skyfall-haul");
            case 576 -> photo("20-skyfall-hang");
            case 582 -> TempestCamera.fix(new Vec3(X + 115, Y + 5, Z + 1), new Vec3(X + 104, Y + 1.5, Z + 8));
            case 585 -> photo("21-skyfall-drop");
            case 589 -> photo("22-skyfall-impact");

            // ---- Levitate, from the side.
            case 610 -> server(mc, p -> {
                reset(p); place(p, 118, 0, 0, 10);
                check("levitate", skill(p, TelekinesisSkillService.LEVITATE, false));
                TempestCamera.fix(new Vec3(X + 122.5, Y + 2.2, Z + 2.5), new Vec3(X + 118, Y + 1.4, Z));
            });
            case 640 -> photo("23-levitate");
            case 645 -> server(mc, p -> TelekinesisSkillService.cancel(p, TelekinesisSkillService.LEVITATE, true));

            // ---- Round Up.
            case 660 -> server(mc, p -> {
                reset(p); place(p, 132, 0, 0, 6);
                Mob a = puppet(p, EntityType.PILLAGER, 132, 8); puppet(p, EntityType.PILLAGER, 134.5, 9); puppet(p, EntityType.HUSK, 130, 9.5);
                aim(p, a.getBoundingBox().getCenter());
                check("round up", skill(p, TelekinesisSkillService.ROUND_UP, false));
                TempestCamera.fix(new Vec3(X + 139, Y + 4, Z + 4), new Vec3(X + 132, Y + 1, Z + 8.5));
            });
            case 675 -> photo("24-round-up");

            // ---- The Puppeteer's swat, and a Vector Step.
            case 780 -> server(mc, p -> {
                reset(p); place(p, 174, 0, 0, 4);
                Mob foe = puppet(p, EntityType.PILLAGER, 174, 5);
                aim(p, foe.getBoundingBox().getCenter());
                KineticStyle.STYLE.strike(p);
                TempestCamera.fix(new Vec3(X + 178.5, Y + 2.5, Z + 2), new Vec3(X + 174, Y + 1.3, Z + 4.5));
            });
            case 784 -> photo("28-swat");
            case 800 -> server(mc, p -> { reset(p); place(p, 186, 0, 0, 0); check("vector step", skill(p, TelekinesisSkillService.STEP, false));
                TempestCamera.fix(new Vec3(X + 191, Y + 2.5, Z + 1), new Vec3(X + 186, Y + 1.5, Z + 1.5)); });
            case 803 -> photo("29-vector-step");

            // ---- Minimal particles: the main cues stay.
            case 890 -> { mc.options.particles().set(ParticleStatus.MINIMAL); server(mc, p -> {
                reset(p); place(p, 0, 0, 0, 4);
                puppet(p, EntityType.PILLAGER, 0, 6);
            }); TempestCamera.fix(new Vec3(X + 8, Y + 3, Z + 4), new Vec3(X, Y + 1.3, Z + 6)); }
            case 895 -> server(mc, p -> { refill(p); TelekinesisService.push(p, 5); });
            case 899 -> photo("30-minimal");
            case 910 -> { TempestCamera.fix(null, null); check("all photographs", photos == expected); PreviewQuiet.quit(); }
            default -> { }
        }
    }

    /** Puts Juggle on a key so its second press (Spike) goes through the real router. */
    private static com.jayptucker.heroesevolved.ability.AbilitySlot equipJuggle(ServerPlayer p) {
        var slot = com.jayptucker.heroesevolved.ability.AbilitySlot.PRIMARY;
        com.jayptucker.heroesevolved.skills.SkillTreeService.equip(p, slot, TelekinesisSkillService.JUGGLE);
        return slot;
    }

    @SubscribeEvent public static void hud(net.neoforged.neoforge.client.event.RenderGuiEvent.Pre event) {
        if (cleanHud && Boolean.getBoolean("heroes.preview.telekinesis")) event.setCanceled(true);
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            var path = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(path.getParent()); image.writeToFile(path); photos++;
            System.out.println("KINETIC_CAPTURE " + pending);
        } catch (Exception ex) { PreviewQuiet.report("KINETIC FAIL screenshot " + ex); }
        pending = null;
    }
}
