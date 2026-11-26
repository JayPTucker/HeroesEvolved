package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.electro.BlackoutService;
import com.jayptucker.heroesevolved.dampener.AbilityDampenerBlockEntity;
import com.jayptucker.heroesevolved.dampener.DampenerGeometry;
import com.jayptucker.heroesevolved.electro.ElectroGeometry;
import com.jayptucker.heroesevolved.electro.ElectroMachines;
import com.jayptucker.heroesevolved.electro.ElectroService;
import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillActionService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Casts each Electrokinesis power at dusk against still targets and photographs it mid-effect. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class ElectroPreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;
    private static boolean cleanHud;

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void place(ServerPlayer p, double x, double z, float yaw, float pitch) {
        p.teleportTo(p.server.overworld(), x + 0.5, -60, z + 0.5, yaw, pitch);
    }

    /** -PelectroMachines: photograph only the machine nodes. */
    private static final boolean MACHINES_ONLY = Boolean.getBoolean("heroes.preview.electro.machines");

    /** Turns the player to look at {@code point}. */
    private static void aim(ServerPlayer p, Vec3 point) {
        Vec3 d = point.subtract(p.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), pitch = (float) Math.toDegrees(-Math.atan2(d.y, Math.hypot(d.x, d.z)));
        p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), yaw, pitch);
    }

    private static void target(ServerPlayer p, double x, double z) {
        var pillager = EntityType.PILLAGER.create(p.serverLevel());
        pillager.setNoAi(true);
        pillager.setInvulnerable(true);
        pillager.setPersistenceRequired();
        pillager.moveTo(x + .5, -60, z + .5, 180, 0);
        p.serverLevel().addFreshEntity(pillager);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.electro")) return;
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
            mc.createWorldOpenFlows().createFreshLevel("Electro-" + System.currentTimeMillis(),
                    new LevelSettings("Electro preview", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (cleanHud) mc.getToasts().clear();
        // -PelectroMachines: only the machine nodes, straight after the setup.
        int t = ++worldTicks;
        if (MACHINES_ONLY && t > 200) t += 350;
        switch (t) {
            case 200 -> {
                cleanHud = true;
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    // Out of the Last Day prologue (its own dimension) and the bunker's cryo chamber, into the Overworld.
                    com.jayptucker.heroesevolved.story.prologue.PrologueService.skip(p);
                    com.jayptucker.heroesevolved.story.bunker.CryoChamberService.release(p);
                    // Keep every stage loaded: the early shots near x 300 to 900, the machine nodes near x 1200 to 1280.
                    for (int cx = 18; cx <= 56; cx++) for (int cz = -1; cz <= 1; cz++) p.server.overworld().setChunkForced(cx, cz, true);
                    for (int cx = 74; cx <= 80; cx++) for (int cz = 74; cz <= 76; cz++) p.server.overworld().setChunkForced(cx, cz, true);
                    p.server.overworld().setDayTime(11750);
                    p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                            .assignDormant(ModAbilities.ELECTROKINESIS_ID).unlock(ModAbilities.ELECTROKINESIS_ID));
                    p.setData(ModDataAttachments.POWER_INTRO_HISTORY,
                            p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.ELECTROKINESIS_ID));
                    PlayerProgressionService.setLevel(p, 5);
                    p.getAbilities().flying = false; p.onUpdateAbilities();
                    place(p, MACHINES_ONLY ? 1200 : 300, MACHINES_ONLY ? 1198 : 0, 0, 4);
                    double[][] spots = {{300, 6}, {304, 8}, {301, 11}, {297, 10}};
                    for (double[] at : spots) target(p, at[0], at[1]);
                });
            }
            // Chain Lightning, tapped: a forked bolt leaping down the line.
            case 226 -> onServer(mc, p -> { PlayerEnergyService.restore(p, 999); ElectroService.press(p, 5); });
            case 229 -> pending = "electro-1-chain-draw.png";
            case 230 -> onServer(mc, ElectroService::release);
            case 234 -> pending = "electro-1-chain-a.png";
            case 236 -> pending = "electro-1-chain-b.png";
            // Live Current, held.
            case 250 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> { place(p, 300, 2, 0, 4); ElectroService.press(p, 5); });
            }
            case 266 -> pending = "electro-2-stream-a.png";
            case 271 -> pending = "electro-2-stream-b.png";
            case 280 -> onServer(mc, p -> {
                if (!ElectroService.isHolding(p)) throw new AssertionError("Live Current must hold until release");
                ElectroService.release(p);
                System.out.println("ELECTRO_CLIENT_CURRENT PASS");
            });
            // Ball Lightning in flight.
            case 290 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    place(p, 470, 0, 0, 4);
                    if (!ElectroService.ballLightning(p, 5)) throw new AssertionError("Ball Lightning did not cast");
                });
            }
            case 294 -> onServer(mc, p -> place(p, 473, 0, 27, 3));
            case 296 -> pending = "electro-3-ball-a.png";
            case 302 -> pending = "electro-3-ball-b.png";
            case 303 -> onServer(mc, p -> { place(p, 470, 0, 0, 4); ElectroService.chainLightning(p, 5); });
            case 305 -> onServer(mc, p -> place(p, 474, 0, 28, 5));
            case 307 -> pending = "electro-3-ball-prime.png";
            case 313 -> pending = "electro-3-ball-burst.png";
            case 315 -> onServer(mc, p -> {
                if (ElectroService.hasOrb(p)) throw new AssertionError("Chain Lightning must burst the orb it hits");
                System.out.println("ELECTRO_CLIENT_RELAY PASS");
            });
            // Thunderstrike: the warning, then the strike.
            case 320 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(12520);
                place(p, 560, 0, 0, 7);
                PlayerEnergyService.restore(p, 999);
                ElectroService.fillCharge(p);
                if (!ElectroService.thunderstrike(p, 5) || ElectroService.pendingStrikes(p) != 3)
                    throw new AssertionError("Overcharged Thunderstrike must schedule three strikes");
                System.out.println("ELECTRO_CLIENT_STRIKE PASS");
            });
            case 328 -> pending = "electro-4-strike-warning.png";
            case 337 -> pending = "electro-4-strike-a.png";
            case 339 -> pending = "electro-4-strike-b.png";
            case 346 -> pending = "electro-4-strike-followup.png";
            // Charged: the body crackles (seen from the front).
            case 360 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { place(p, 300, 0, 0, 10); ElectroService.fillCharge(p); }); }
            case 372 -> pending = "electro-5-overcharged.png";
            // Storm Surge: the wind-up and the release, from behind.
            case 380 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); onServer(mc, p -> {
                place(p, 300, 5, 0, 14);
                if (!ElectroService.startSurge(p, 5)) throw new AssertionError("Storm Surge did not start");
            }); }
            case 392 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 398 -> pending = "electro-6-surge-charge.png";
            case 408 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 411 -> pending = "electro-6-surge-release-a.png";
            case 419 -> pending = "electro-6-surge-release-b.png";
            // Ride the Lightning, from behind.
            case 437 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                onServer(mc, p -> { place(p, 670, 0, -60, 4); ElectroService.fillCharge(p); });
            }
            case 440 -> onServer(mc, p -> {
                Vec3 start = p.position();
                if (!ElectroService.rideTheLightning(p, 5) || p.position().distanceTo(start) < 10)
                    throw new AssertionError("Ride the Lightning did not travel along the open route");
                System.out.println("ELECTRO_CLIENT_RIDE PASS distance=" + p.position().distanceTo(start));
            });
            case 441 -> pending = "electro-7-ride-a.png";
            case 443 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.player.setYRot(120);
                mc.player.setXRot(15);
                pending = "electro-7-ride-b.png";
            }
            case 455 -> pending = "electro-7-scar.png";
            case 470 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    place(p, 775, 0, 0, 13);
                    for (int x : new int[]{-4, 0, 4}) for (int z : new int[]{4, 8})
                        p.serverLevel().setBlockAndUpdate(new BlockPos(775 + x, -60, z), Blocks.REDSTONE_BLOCK.defaultBlockState());
                    target(p, 777, 8);
                    if (!BlackoutService.activate(p, 5)) throw new AssertionError("Blackout did not start");
                });
            }
            case 484 -> pending = "electro-8-blackout-siphon.png";
            case 491 -> pending = "electro-8-blackout-release.png";
            case 499 -> pending = "electro-8-blackout-wave.png";
            case 506 -> onServer(mc, p -> {
                if (BlackoutService.winding(p) || ElectroService.charge(p) < 20) throw new AssertionError("Blackout must return Charge");
                System.out.println("ELECTRO_CLIENT_BLACKOUT PASS charge=" + ElectroService.charge(p));
            });
            case 530 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(1000);
                place(p, 890, 0, 0, 13);
                SkillActionService.activate(new AbilityUseContext(p, ModAbilities.ELECTROKINESIS_ID, 5, false), 1);
            });
            case 543 -> pending = "electro-9-static-screen.png";
            // ---- The machine nodes (electro/ElectroMachines), in daylight, well away from the earlier shots.
            case 600 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    p.serverLevel().setDayTime(6000);
                    p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.ELECTROKINESIS_ID.toString(),
                            new SkillTreeData.Build(15, Set.of(ElectroMachines.REMOTE_HAND, ElectroMachines.KILL_SWITCH, ElectroMachines.DEAD_TRIGGER,
                                    ElectroMachines.RETURN_TO_SENDER, ElectroMachines.COUNTERWEIGHT, ElectroMachines.WINCHLINE), Map.of())));
                    var level = p.serverLevel();
                    // Remote Hand: a stone wall with a door and a lever in it, and a copper line in the floor up to the door.
                    for (int x = 1197; x <= 1203; x++) for (int y = -60; y <= -57; y++)
                        level.setBlock(new BlockPos(x, y, 1207), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                    var door = Blocks.OAK_DOOR.defaultBlockState();
                    level.setBlock(new BlockPos(1200, -60, 1207), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
                    level.setBlock(new BlockPos(1200, -59, 1207), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
                    level.setBlock(new BlockPos(1202, -59, 1206), Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL)
                            .setValue(LeverBlock.FACING, Direction.NORTH), 3);
                    for (int z = 1202; z <= 1207; z++) level.setBlock(new BlockPos(1200, -61, z), Blocks.WAXED_COPPER_BLOCK.defaultBlockState(), 3);
                    place(p, 1200, 1198, 0, 0);
                    aim(p, Vec3.atCenterOf(new BlockPos(1202, -59, 1206)));
                });
            }
            case 612 -> onServer(mc, p -> { PlayerEnergyService.restore(p, 999); ElectroService.press(p, 5, false); });
            case 614 -> onServer(mc, ElectroService::release);
            case 617 -> pending = "electro-10-remote-hand-lever.png";
            case 630 -> onServer(mc, p -> aim(p, Vec3.atCenterOf(new BlockPos(1200, -61, 1203))));
            case 634 -> onServer(mc, p -> { PlayerEnergyService.restore(p, 999); ElectroService.press(p, 5, false); });
            case 636 -> onServer(mc, ElectroService::release);
            case 639 -> pending = "electro-11-remote-hand-network-a.png";
            case 644 -> pending = "electro-11-remote-hand-network-b.png";
            // Kill Switch: a running Ability Dampener, killed from outside its field.
            case 660 -> onServer(mc, p -> {
                var level = p.serverLevel();
                BlockPos pylon = new BlockPos(1220, -60, 1213);
                level.setBlock(pylon, ModBlocks.ABILITY_DAMPENER.get().defaultBlockState(), 3);
                if (level.getBlockEntity(pylon) instanceof AbilityDampenerBlockEntity field) field.fill(DampenerGeometry.CAPACITY);
                place(p, 1213, 1201, 0, 6);
            });
            case 700 -> pending = "electro-12-dampener-live.png";
            case 702 -> onServer(mc, p -> aim(p, Vec3.atCenterOf(new BlockPos(1220, -60, 1213))));
            case 704 -> onServer(mc, p -> { PlayerEnergyService.restore(p, 999); ElectroService.press(p, 5, true); });
            case 706 -> onServer(mc, ElectroService::release);
            case 707 -> onServer(mc, p -> place(p, 1213, 1201, 0, 6));
            case 711 -> pending = "electro-12-kill-switch-a.png";
            case 717 -> pending = "electro-12-kill-switch-b.png";
            // Dead Trigger: a pillager holding a Glock, shocked; the gun spits sparks while it's jammed.
            case 730 -> onServer(mc, p -> {
                place(p, 1230, 1200, 0, 0);
                var gunner = EntityType.PILLAGER.create(p.serverLevel());
                gunner.setNoAi(true); gunner.setInvulnerable(true); gunner.setPersistenceRequired();
                gunner.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, PowerWeapons.gun(PowerWeapons.AGENT_PISTOL));
                gunner.moveTo(1232.5, -60, 1205.5, 160, 0);
                p.serverLevel().addFreshEntity(gunner);
            });
            case 734 -> onServer(mc, p -> aim(p, new Vec3(1232.5, -59.1, 1205.5)));
            case 736 -> onServer(mc, p -> { PlayerEnergyService.restore(p, 999); ElectroService.press(p, 5, false); });
            case 738 -> onServer(mc, ElectroService::release);
            case 739 -> onServer(mc, p -> place(p, 1230, 1200, -8, 10));
            case 742 -> pending = "electro-13-dead-trigger-a.png";
            case 752 -> pending = "electro-13-dead-trigger-b.png";
            // Return to Sender: an arrow from a pillager turned back at the Static Screen's edge.
            case 770 -> {
                onServer(mc, p -> {
                    place(p, 1245, 1200, 0, 8);
                    SkillActionService.activate(new AbilityUseContext(p, ModAbilities.ELECTROKINESIS_ID, 5, false), 1);
                    target(p, 1245, 1214);
                });
            }
            case 778 -> onServer(mc, p -> {
                var archer = p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Pillager.class,
                        new net.minecraft.world.phys.AABB(1240, -62, 1210, 1250, -56, 1218)).stream().findFirst().orElse(null);
                var arrow = new Arrow(EntityType.ARROW, p.serverLevel());
                arrow.setOwner(archer);
                arrow.setPos(1245.5, -58.4, 1212.5);
                arrow.setDeltaMovement(0, 0, -1.6);
                p.serverLevel().addFreshEntity(arrow);
            });
            case 781 -> pending = "electro-14-return-to-sender-a.png";
            case 785 -> pending = "electro-14-return-to-sender-b.png";
            // Counterweight: hauled up to an iron block on a pillar, from behind, from the front and in first person.
            case 800 -> onServer(mc, p -> {
                var level = p.serverLevel();
                for (int y = -60; y <= -55; y++) level.setBlock(new BlockPos(1265, y, 1212), Blocks.IRON_BLOCK.defaultBlockState(), 3);
                place(p, 1258, 1201, 0, 0);
                aim(p, new Vec3(1265.5, -55.5, 1212));
            });
            case 806 -> onServer(mc, p -> ElectroMachines.counterweight(p));
            case 809 -> pending = "electro-15-counterweight-a.png";
            case 813 -> pending = "electro-15-counterweight-b.png";
            case 850 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> { place(p, 1258, 1201, 0, 0); aim(p, new Vec3(1265.5, -55.5, 1212)); });
            }
            case 856 -> onServer(mc, p -> ElectroMachines.counterweight(p));
            case 859 -> pending = "electro-15-counterweight-first-person.png";
            // Winchline: a lattice anchor reeling in a pillager its wire caught.
            case 880 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    place(p, 1274, 1196, -12, 12);
                    var runner = EntityType.PILLAGER.create(p.serverLevel());
                    runner.setPersistenceRequired();
                    runner.moveTo(1281.5, -60, 1212.5, 180, 0);
                    p.serverLevel().addFreshEntity(runner);
                    Vec3 anchor = new Vec3(1279.5, -59.5, 1203.5);
                    ElectroGeometry.show(p.serverLevel(), Piece.ELECTRO_ANCHOR, anchor, anchor, 1, null);
                    ElectroMachines.caught(p, runner, List.of(anchor));
                });
            }
            case 885 -> pending = "electro-16-winchline-a.png";
            case 891 -> pending = "electro-16-winchline-b.png";
            case 910 -> PreviewQuiet.quit();
            default -> { }
        }
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Pre event) {
        if (cleanHud && Boolean.getBoolean("heroes.preview.electro")) event.setCanceled(true);
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("ELECTRO_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
