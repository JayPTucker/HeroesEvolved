package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.ActionInputPayload;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.vibe.VibeService;
import com.jayptucker.heroesevolved.vibe.VibeFrequencies;
import com.jayptucker.heroesevolved.network.FrequencyChoicePayload;
import com.jayptucker.heroesevolved.gui.screen.FrequencyWheelScreen;
import com.jayptucker.heroesevolved.client.effects.BreachViewClient;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
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
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Runs the actual ability keys, every optional active, the class abilities and the awakening in a flat world well outside the spawn bunker. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class VibePreview {
    private static final boolean BREACHES = Boolean.getBoolean("heroes.preview.vibe.breaches");
    private static boolean started;
    private static int startup, age;
    private static String capture;
    private static ServerPlayer walker;

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    private static void reset(ServerPlayer player, int x, float yaw, float pitch, long time) {
        VibeService.forget(player);
        CooldownService.clearAll(player);
        PlayerEnergyService.restore(player, 999);
        var world = player.getServer().overworld();
        world.setDayTime(time);
        // A new player starts in the Last Day's city, another dimension where breaches are sealed: always the overworld.
        player.teleportTo(world, x + .5, -60, .5, yaw, pitch);
        player.setDeltaMovement(Vec3.ZERO);
        System.out.println("VIBE_PREVIEW at " + player.level().dimension().location() + " " + player.blockPosition()
                + " on " + world.getBlockState(player.blockPosition().below()));
    }

    /** Every scene stands on one strip of forced chunks along z = 0, from x = 192 to the garden at x = 600. */
    private static void stage(ServerPlayer player) {
        var world = player.getServer().overworld();
        int first = BREACHES ? 176 : 192, last = BREACHES ? 280 : 616;
        for (int cx = first >> 4; cx <= last >> 4; cx++)
            for (int cz = -2; cz <= (BREACHES ? 3 : 2); cz++) world.setChunkForced(cx, cz, true);
    }

    /** Somewhere unlike the flat preview world, for the breach to show: a walled garden with pools, trees and lamps. */
    private static void garden(ServerPlayer player, int x0, int wall) {
        var world = player.getServer().overworld();
        int y = -60;
        for (int x = x0 - 9; x <= x0 + 9; x++) {
            for (int z = 1; z <= wall; z++) {
                var floor = new BlockPos(x, y - 1, z);
                boolean path = Math.abs(x - x0) <= 1;
                world.setBlockAndUpdate(floor, (path ? Blocks.STONE_BRICKS : Blocks.MOSS_BLOCK).defaultBlockState());
                for (int h = 0; h < 8; h++) world.setBlockAndUpdate(floor.above(h + 1), Blocks.AIR.defaultBlockState());
            }
        }
        // A pool on each side of the path.
        for (int side : new int[]{-1, 1})
            for (int x = 3; x <= 6; x++) for (int z = 5; z <= 10; z++)
                world.setBlockAndUpdate(new BlockPos(x0 + side * x, y - 1, z), Blocks.WATER.defaultBlockState());
        // Pillars with lanterns along the path, and a far wall of deepslate and amethyst.
        for (int z = 3; z <= 15; z += 4) {
            for (int side : new int[]{-2, 2}) {
                for (int h = 0; h < 3; h++) world.setBlockAndUpdate(new BlockPos(x0 + side, y + h, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                world.setBlockAndUpdate(new BlockPos(x0 + side, y + 3, z), Blocks.LANTERN.defaultBlockState());
            }
        }
        for (int x = x0 - 9; x <= x0 + 9; x++)
            for (int h = 0; h < 6; h++)
                world.setBlockAndUpdate(new BlockPos(x, y + h, wall),
                        (Math.floorMod(x + h, 4) == 0 ? Blocks.AMETHYST_BLOCK : Blocks.DEEPSLATE_BRICKS).defaultBlockState());
        // Two trees, trunks first, then their leaves.
        for (int side : new int[]{-1, 1}) {
            int tx = x0 + side * 7, tz = 13;
            for (int h = 0; h < 5; h++) world.setBlockAndUpdate(new BlockPos(tx, y + h, tz), Blocks.CHERRY_LOG.defaultBlockState());
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 3; dy <= 5; dy++)
                if (Math.abs(dx) + Math.abs(dz) + Math.abs(dy - 4) <= 3 && !(dx == 0 && dz == 0 && dy < 5))
                    world.setBlockAndUpdate(new BlockPos(tx + dx, y + dy, tz + dz),
                            Blocks.CHERRY_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
        }
        for (int z = 2; z <= 16; z += 2)
            for (int side : new int[]{-4, 4})
                if (z < 5 || z > 10) world.setBlockAndUpdate(new BlockPos(x0 + side, y, z),
                        (z % 4 == 0 ? Blocks.PINK_TULIP : Blocks.CORNFLOWER).defaultBlockState());
    }

    private static Pillager actor(ServerPlayer player, double dx, double dz) {
        Pillager actor = EntityType.PILLAGER.create(player.serverLevel());
        actor.setNoAi(true);
        actor.setPersistenceRequired();
        actor.moveTo(player.position().add(dx, 0, dz));
        actor.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        actor.setHealth(100);
        player.serverLevel().addFreshEntity(actor);
        return actor;
    }

    private static void cast(ServerPlayer player, AbilitySlot slot) {
        System.out.println("VIBE_PREVIEW " + slot + " " + AbilityUseService.activateAssignedAction(player, slot, false));
    }

    /** Shift + Breach: a breach where the player looks (a plain press opens the frequency wheel). */
    private static void aim(ServerPlayer player) {
        System.out.println("VIBE_PREVIEW aimed breach " + AbilityUseService.activateAssignedAction(player, AbilitySlot.SECONDARY, true));
    }

    private static void cast(ServerPlayer player, String action) {
        System.out.println("VIBE_PREVIEW " + action + " " + AbilityUseService.activateAction(player, action, false));
    }

    private static void breachScenes(Minecraft mc, int tick) {
        if (tick >= 170 && tick <= 189) server(mc, p -> walker.setPos(walker.position().add(-.055, 0, -.17)));
        switch (tick) {
            case 137 -> server(mc, p -> {
                reset(p, 248, 0, 0, 6000);
                garden(p, 248, 26);
                p.setOnGround(true);
                System.out.println("VIBE_PREVIEW garden tuned " + VibeFrequencies.mark(p, 0));
                var visitor = EntityType.VILLAGER.create(p.serverLevel());
                visitor.setNoAi(true);
                visitor.moveTo(p.getX() - .75, p.getY(), p.getZ() + 5, 165, 0);
                p.serverLevel().addFreshEntity(visitor);
                var guard = actor(p, .8, 6);
                guard.setYRot(180);
                guard.setYBodyRot(180);
                guard.setYHeadRot(180);
                guard.setItemSlot(EquipmentSlot.MAINHAND, Items.CROSSBOW.getDefaultInstance());
                var slime = EntityType.SLIME.create(p.serverLevel());
                slime.setNoAi(true);
                slime.setSize(2, true);
                slime.moveTo(p.position().add(0, 0, 10));
                p.serverLevel().addFreshEntity(slime);
                walker = new FakePlayer(p.serverLevel(), new GameProfile(UUID.randomUUID(), "BreachWalker"));
                try {
                    var channel = Connection.class.getDeclaredField("channel");
                    channel.setAccessible(true);
                    var connection = walker.connection.getConnection();
                    if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                walker.moveTo(p.getX() + .85, p.getY(), p.getZ() + 7, 155, 0);
                walker.setYBodyRot(155);
                walker.setYHeadRot(155);
                p.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(walker)));
                p.serverLevel().addNewPlayer(walker);
                reset(p, 208, 0, 0, 6000);
            });
            case 159 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                PacketDistributor.sendToServer(new ActionInputPayload(2, "", true, false));
            }
            case 165 -> {
                if (mc.screen instanceof FrequencyWheelScreen wheel) {
                    wheel.mouseClicked(wheel.width / 2, wheel.height / 2 - Math.min(65, wheel.height / 4),
                            GLFW.GLFW_MOUSE_BUTTON_LEFT);
                }
            }
            case 184 -> capture = "43-breach-walking-player.png";
            case 188 -> capture = "44-breach-walking-player-next-step.png";
            case 185 -> {
                System.out.println("VIBE_PREVIEW views " + BreachViewClient.count() + " actors " + BreachViewClient.actorCount());
                capture = "38-breach-fog-and-entities.png";
            }
            case 190 -> server(mc, p -> p.teleportTo(p.serverLevel(), p.getX() + 2.4, p.getY(), p.getZ() - .3, 44, 4));
            case 197 -> capture = "39-breach-from-the-side.png";
            case 212 -> server(mc, p -> {
                walker.serverLevel().removePlayerImmediately(walker, Entity.RemovalReason.DISCARDED);
                walker = null;
                reset(p, 248, 0, 78, 6000);
            });
            case 221 -> PacketDistributor.sendToServer(new ActionInputPayload(2, "", true, true));
            case 224 -> server(mc, p -> p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ() - 1.65, 0, 47));
            case 251 -> capture = "40-breach-underfoot.png";
            case 262 -> server(mc, p -> reset(p, 220, 0, -42, 6000));
            case 270 -> PacketDistributor.sendToServer(new ActionInputPayload(2, "", true, true));
            case 294 -> capture = "41-breach-upward.png";
            case 299 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 305 -> capture = "42-breach-upward-third-person.png";
            case 313 -> PreviewQuiet.quit();
            default -> { }
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.vibe")) return;
        var mc = Minecraft.getInstance();
        if (!started && ++startup >= 35 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.fov().set(78);
            mc.options.renderDistance().set(BREACHES ? 2 : 6);
            if (BREACHES) mc.options.graphicsMode().set(GraphicsStatus.FABULOUS);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Vibe-" + System.currentTimeMillis(),
                    new LevelSettings("Vibe Preview", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(71L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (age >= 100) mc.getToasts().clear();
        ++age;
        if (BREACHES && age > 110) {
            breachScenes(mc, age);
            return;
        }
        switch (age) {
            // A new player is enrolled in the Last Day (another dimension): end it before anything is staged.
            case 104 -> server(mc, com.jayptucker.heroesevolved.story.prologue.PrologueService::skip);
            case 110 -> {
                if (BREACHES) mc.options.hideGui = true;
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
                server(mc, p -> {
                    CryoChamberService.release(p);
                    stage(p);
                    p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.VIBE_ID).unlock(ModAbilities.VIBE_ID));
                    p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.VIBE_ID));
                    PlayerProgressionService.setLevel(p, 5);
                    // Every optional active and class ability, so the second half can cast each one.
                    p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.VIBE_ID.toString(),
                            new SkillTreeData.Build(15, Set.of("vibe_0_0", "vibe_reservoir", "vibe_0_1", "vibe_reverb", "vibe_1_1", "vibe_flashstep",
                                    "vibe_2_1", "vibe_prism_lock", "vibe_crosswave", "vibe_mirror", "vibe_sanctuary"), Map.of(), 0)));
                    p.setGameMode(GameType.SURVIVAL);
                    reset(p, 210, 0, 0, 12800);
                    PlayerPowerSyncService.sync(p);
                });
            }
            case 151 -> server(mc, p -> { actor(p, .4, 7); cast(p, AbilitySlot.PRIMARY); });
            case 156 -> capture = "01-vibe-blast-dusk.png";
            case 161 -> capture = "02-vibe-blast-wavefront.png";
            case 179 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(mc, p -> reset(p, 222, 0, 0, 6000));
            }
            case 199 -> server(mc, p -> { actor(p, .1, 7); cast(p, AbilitySlot.PRIMARY); });
            case 205 -> capture = "03-vibe-blast-first-person.png";
            case 229 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> reset(p, 235, 0, 11, 12800));
            }
            case 254 -> server(mc, p -> aim(p));
            case 259 -> capture = "04-breach-forming.png";
            case 275 -> capture = "05-linked-breaches.png";
            case 279 -> mc.options.setCameraType(CameraType.FIRST_PERSON);
            case 281 -> capture = "06-breach-first-person.png";
            case 285 -> server(mc, p -> p.teleportTo(p.getX(), p.getY(), p.getZ() + 2.3));
            case 289 -> capture = "07-breach-arrival.png";
            case 297 -> server(mc, p -> cast(p, AbilitySlot.SECONDARY));
            case 301 -> capture = "08-breach-closing.png";
            case 320 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> reset(p, 248, 0, 0, 12800));
            }
            case 344 -> server(mc, p -> {
                var witness = actor(p, 0, 4);
                var attacker = actor(p, 3.2, 7);
                witness.hurt(p.damageSources().mobAttack(attacker), 3);
                cast(p, AbilitySlot.TERTIARY);
            });
            case 360 -> capture = "09-read-the-vibe.png";
            case 384 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                server(mc, p -> reset(p, 261, 0, 0, 12800));
            }
            case 410 -> server(mc, p -> cast(p, AbilitySlot.QUATERNARY));
            case 423 -> server(mc, p -> {
                var arrow = EntityType.ARROW.create(p.serverLevel());
                arrow.setPos(p.position().add(.45, 1.2, 5.5));
                arrow.setNoGravity(true);
                arrow.setDeltaMovement(0, 0, -1.8);
                p.serverLevel().addFreshEntity(arrow);
            });
            case 427 -> capture = "10-breach-guard.png";
            case 431 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> cast(p, AbilitySlot.QUATERNARY));
            }
            case 436 -> capture = "11-return-fire.png";
            case 462 -> server(mc, p -> reset(p, 276, 0, 16, 12800));
            case 486 -> server(mc, p -> { actor(p, 2, 6); cast(p, AbilitySlot.QUINARY); });
            case 497 -> capture = "12-frequency-lock-warning.png";
            case 510 -> capture = "13-frequency-lock-collapse.png";
            // ---------------------------------------------------------- skills and class abilities
            case 540 -> server(mc, p -> reset(p, 290, 0, 0, 12800));
            case 560 -> server(mc, p -> { actor(p, .3, 9); cast(p, "vibe_0_1"); });
            case 565 -> capture = "14-vibe-lance-windup.png";
            case 570 -> capture = "15-vibe-lance.png";
            case 590 -> server(mc, p -> reset(p, 304, 0, 0, 12800));
            case 610 -> server(mc, p -> { actor(p, 0, 6); cast(p, "vibe_reverb"); });
            case 619 -> capture = "16-reverb.png";
            case 640 -> server(mc, p -> reset(p, 318, 0, 0, 12800));
            case 660 -> server(mc, p -> { actor(p, 0, 7); actor(p, 3.2, 8); cast(p, "vibe_crosswave"); });
            case 669 -> capture = "17-crosswave.png";
            case 690 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                server(mc, p -> reset(p, 332, 0, 0, 12800));
            }
            case 710 -> server(mc, p -> cast(p, "vibe_mirror"));
            case 720 -> server(mc, p -> {
                for (double x : new double[] {-1.2, .4, 1.5}) {
                    var arrow = EntityType.ARROW.create(p.serverLevel());
                    arrow.setPos(p.position().add(x, 1.2, 5.5));
                    arrow.setNoGravity(true);
                    arrow.setDeltaMovement(0, 0, -1.8);
                    p.serverLevel().addFreshEntity(arrow);
                }
            });
            case 724 -> capture = "18-mirror-breach.png";
            case 740 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> reset(p, 346, 0, 12, 12800));
            }
            case 760 -> server(mc, p -> cast(p, "vibe_flashstep"));
            case 762 -> capture = "19-flashstep.png";
            case 785 -> server(mc, p -> reset(p, 360, 0, 11, 12800));
            case 805 -> server(mc, p -> cast(p, "vibe_sanctuary"));
            case 823 -> capture = "20-sanctuary-breach.png";
            case 845 -> server(mc, p -> reset(p, 374, 0, 0, 12800));
            case 865 -> server(mc, p -> {
                var witness = actor(p, 0, 5);
                var attacker = actor(p, 3, 7.5);
                witness.hurt(p.damageSources().mobAttack(attacker), 3);
                cast(p, "vibe_2_1");
            });
            case 881 -> capture = "21-co-vibe.png";
            case 905 -> server(mc, p -> reset(p, 388, 0, 16, 12800));
            case 925 -> server(mc, p -> { actor(p, 2, 6); actor(p, -3, 9); cast(p, "vibe_prism_lock"); });
            case 936 -> capture = "22-prism-lock-warning.png";
            case 949 -> capture = "23-prism-lock-collapse.png";
            case 970 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                server(mc, p -> reset(p, 402, 0, 0, 12800));
            }
            case 990 -> server(mc, p -> System.out.println("VIBE_PREVIEW tune " + VibeFrequencies.mark(p, 1)));
            case 996 -> capture = "24-tune-frequency.png";
            // ---------------------------------------------------------- the awakening
            case 1015 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> reset(p, 416, 0, 0, 12800));
            }
            case 1035 -> server(mc, p -> {
                Villager ward = EntityType.VILLAGER.create(p.serverLevel());
                ward.setNoAi(true);
                ward.moveTo(p.position().add(1, 0, 5));
                p.serverLevel().addFreshEntity(ward);
                var threat = actor(p, 1, 7);
                ArmPosePayload.show(p, ArmPose.VIBE_READ, 28);
                SetPiecePayload.send(p.serverLevel(), Piece.VIBE_AWAKEN, p.position(), ward.position(), 1, p);
                VibeService.awaken(p, ward, List.of(threat));
            });
            case 1039 -> capture = "25-awakening-tears.png";
            case 1048 -> capture = "26-awakening-rescue.png";
            // ---------------------------------------------------------- passives that link two moves
            case 1070 -> server(mc, p -> reset(p, 430, 0, 11, 12800));
            case 1090 -> server(mc, p -> aim(p));
            case 1104 -> server(mc, p -> {
                p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), 0, 0);
                actor(p, .3, 11);
            });
            case 1107 -> server(mc, p -> cast(p, AbilitySlot.PRIMARY));
            case 1112 -> capture = "27-through-the-seam.png";
            case 1116 -> capture = "28-through-the-seam-exit.png";
            case 1135 -> server(mc, p -> {
                reset(p, 444, 0, 0, 12800);
                for (int x = -3; x <= 3; x++) for (int y = 0; y < 4; y++)
                    p.serverLevel().setBlockAndUpdate(p.blockPosition().offset(x, y, 9), Blocks.STONE.defaultBlockState());
            });
            case 1150 -> server(mc, p -> cast(p, AbilitySlot.QUATERNARY));
            case 1158 -> server(mc, p -> {
                for (double x : new double[] {-.3, .1, .4}) {
                    var arrow = EntityType.ARROW.create(p.serverLevel());
                    arrow.setPos(p.position().add(x, 1.2, 5.5));
                    arrow.setNoGravity(true);
                    arrow.setDeltaMovement(0, 0, -1.8);
                    p.serverLevel().addFreshEntity(arrow);
                }
                actor(p, 2.2, 7.6);
            });
            case 1166 -> server(mc, p -> cast(p, AbilitySlot.QUATERNARY));
            case 1170 -> capture = "29-overflow-return-fire.png";
            case 1176 -> capture = "30-overflow-collapse.png";
            // ---------------------------------------------------------- frequencies: the wheel and the far side
            case 1195 -> server(mc, p -> {
                reset(p, 600, 0, 0, 6000);
                garden(p, 600, 17);
                p.setOnGround(true);
            });
            case 1202 -> server(mc, p -> {
                System.out.println("VIBE_PREVIEW garden " + VibeFrequencies.mark(p, 0) + " " + VibeFrequencies.rename(p, 0, "Garden"));
                Villager visitor = EntityType.VILLAGER.create(p.serverLevel());
                visitor.setNoAi(true);
                visitor.moveTo(p.getX() - .9, p.getY(), p.getZ() + 4.5, 160, 0);
                p.serverLevel().addFreshEntity(visitor);
                Pillager guard = actor(p, .85, 6);
                guard.setYRot(180);
                guard.setYBodyRot(180);
                guard.setYHeadRot(180);
                guard.setItemSlot(EquipmentSlot.MAINHAND, Items.CROSSBOW.getDefaultInstance());
                reset(p, 470, 0, 0, 6000);
            });
            case 1215 -> server(mc, p -> cast(p, AbilitySlot.SECONDARY));
            case 1219 -> { if (mc.screen instanceof FrequencyWheelScreen wheel) wheel.pin(0); }
            case 1222 -> capture = "31-frequency-wheel.png";
            case 1223 -> { if (mc.screen instanceof FrequencyWheelScreen wheel) wheel.keyPressed(GLFW.GLFW_KEY_R, 0, 0); }
            case 1224 -> capture = "31a-frequency-rename.png";
            case 1225 -> { if (mc.screen instanceof FrequencyWheelScreen wheel) wheel.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0); }
            case 1226 -> {
                if (mc.screen instanceof FrequencyWheelScreen wheel) {
                    double firstSlotY = wheel.height / 2.0 - Math.min(65, wheel.height / 4.0);
                    wheel.mouseClicked(wheel.width / 2.0, firstSlotY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                }
            }
            case 1250 -> {
                System.out.println("VIBE_PREVIEW views " + BreachViewClient.count() + " actors " + BreachViewClient.actorCount());
                capture = "32-breach-view.png";
            }
            case 1252 -> mc.options.setCameraType(CameraType.FIRST_PERSON);
            case 1256 -> capture = "33-breach-view-first-person.png";
            case 1258 -> server(mc, p -> p.teleportTo(p.serverLevel(), p.getX() + 2.4, p.getY(), p.getZ() - .4, 38, 4));
            case 1263 -> capture = "34-breach-view-angle.png";
            case 1265 -> server(mc, p -> p.teleportTo(p.serverLevel(), p.getX() - 2.4, p.getY(), p.getZ() + 1.1, 0, 6));
            case 1270 -> capture = "35-breach-view-close.png";
            case 1272 -> server(mc, p -> p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ() + 1.6, 0, 0));
            case 1282 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> {
                    System.out.println("VIBE_PREVIEW crossed to " + p.blockPosition());
                    p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ() + 3.5, 180, 4);
                });
            }
            case 1290 -> capture = "36-looking-back-through.png";
            case 1300 -> server(mc, p -> reset(p, 470, 0, 0, 6000));
            case 1306 -> server(mc, p -> VibeFrequencies.choose(p, FrequencyChoicePayload.GLIMPSE, 0, ""));
            case 1330 -> capture = "37-glimpse.png";
            case 1345 -> PreviewQuiet.quit();
            default -> { }
        }
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Pre event) {
        if (age >= 100 && Boolean.getBoolean("heroes.preview.vibe")) event.setCanceled(true);
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (capture == null) return;
        Minecraft mc = Minecraft.getInstance();
        Path path = mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Files.createDirectories(path.getParent());
            image.writeToFile(path);
            System.out.println("VIBE_PREVIEW_CAPTURE " + capture);
        } catch (Exception e) {
            throw new IllegalStateException("Could not capture Vibe preview", e);
        } finally {
            capture = null;
        }
    }
}
