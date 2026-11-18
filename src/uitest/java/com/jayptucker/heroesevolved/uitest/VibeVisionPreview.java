package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.vibe.VibeService;
import com.jayptucker.heroesevolved.vibe.VibeVisions;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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
import net.neoforged.neoforge.common.util.FakePlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Photographs Vibe's held Breach (the blast from the palm and the tear opening slowly, from the side, in first person
 * and turning with the look), a vision from inside it, and a hologram as the endangered ally sees it.
 * Flag {@code heroes.preview.vibevision}; shots in {@code build/vibe-vision-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class VibeVisionPreview {
    private static boolean started;
    private static int startup, age;
    private static String capture;
    private static ServerPlayer other;

    private static boolean on() { return Boolean.getBoolean("heroes.preview.vibevision"); }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    private static void reset(ServerPlayer player, int x, float yaw, long time) {
        VibeService.forget(player);
        CooldownService.clearAll(player);
        PlayerEnergyService.restore(player, 999);
        var world = player.getServer().overworld();
        world.setDayTime(time);
        player.teleportTo(world, x + .5, -60, .5, yaw, 0);
        player.setDeltaMovement(Vec3.ZERO);
        System.out.println("VIBE_VISION_PREVIEW at " + player.level().dimension().location() + " " + player.blockPosition()
                + " on " + world.getBlockState(player.blockPosition().below()));
    }

    /** Holds Breach down (never let go): five ticks later a breach starts to channel open where the player looks. */
    private static void hold(ServerPlayer player) {
        System.out.println("VIBE_VISION_PREVIEW hold " + AbilityUseService.activateAssignedAction(player, AbilitySlot.SECONDARY, false));
    }

    /** Another player in the world, as ContainmentPreview makes one: in the player list, announced, on an embedded channel. */
    private static ServerPlayer stranger(ServerPlayer host, String name, Vec3 at, float yaw) {
        var fake = new FakePlayer(host.serverLevel(), new GameProfile(UUID.randomUUID(), name));
        try {
            var channel = Connection.class.getDeclaredField("channel");
            channel.setAccessible(true);
            var connection = fake.connection.getConnection();
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        fake.moveTo(at.x, at.y, at.z, yaw, 0);
        fake.setYHeadRot(yaw);
        fake.setYBodyRot(yaw);
        listed(host, fake, true);
        host.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(fake)));
        host.serverLevel().addNewPlayer(fake);
        fake.setGameMode(GameType.SURVIVAL);
        return fake;
    }

    @SuppressWarnings("unchecked")
    private static void listed(ServerPlayer host, ServerPlayer fake, boolean add) {
        try {
            var list = host.getServer().getPlayerList();
            var players = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
            var byId = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
            players.setAccessible(true);
            byId.setAccessible(true);
            if (add) {
                ((List<ServerPlayer>) players.get(list)).add(fake);
                ((java.util.Map<UUID, ServerPlayer>) byId.get(list)).put(fake.getUUID(), fake);
            } else {
                ((List<ServerPlayer>) players.get(list)).remove(fake);
                ((java.util.Map<UUID, ServerPlayer>) byId.get(list)).remove(fake.getUUID());
            }
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    /** A sturdy foe with no will of its own, standing at an offset from the player. */
    private static net.minecraft.world.entity.monster.Husk target(ServerPlayer near, double dx, double dz) {
        var husk = EntityType.HUSK.create(near.serverLevel());
        husk.setNoAi(true);
        husk.setPersistenceRequired();
        husk.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
        husk.setHealth(200);
        Vec3 at = near.position().add(dx, 0, dz);
        husk.moveTo(at.x, at.y, at.z, 180, 0);
        husk.setYHeadRot(180);
        near.serverLevel().addFreshEntity(husk);
        return husk;
    }

    private static void zombies(ServerPlayer near, double... offsets) {
        for (int i = 0; i + 1 < offsets.length; i += 2) {
            var zombie = EntityType.HUSK.create(near.serverLevel());
            zombie.setNoAi(true);
            zombie.setPersistenceRequired();
            Vec3 at = near.position().add(offsets[i], 0, offsets[i + 1]);
            Vec3 look = near.position().subtract(at);
            zombie.moveTo(at.x, at.y, at.z, (float) Math.toDegrees(Math.atan2(-look.x, look.z)), 0);
            zombie.setYHeadRot(zombie.getYRot());
            near.serverLevel().addFreshEntity(zombie);
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!on()) return;
        var mc = Minecraft.getInstance();
        if (!started && ++startup >= 35 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.fov().set(78);
            mc.options.renderDistance().set(6);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("VibeVision-" + System.currentTimeMillis(),
                    new LevelSettings("Vibe Vision Preview", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(71L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (age >= 100) mc.getToasts().clear();
        ++age;
        // Keep the bunker's wake-up tint off the shots (the vision draws its own, separately).
        if (age % 10 == 0) com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
        switch (age) {
            // A new player is enrolled in the Last Day (another dimension): end it before anything is staged.
            case 104 -> server(mc, com.jayptucker.heroesevolved.story.prologue.PrologueService::skip);
            case 110 -> {
                mc.options.hideGui = true;
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
                server(mc, p -> {
                    CryoChamberService.release(p);
                    var world = p.getServer().overworld();
                    for (int cx = 176 >> 4; cx <= 448 >> 4; cx++)
                        for (int cz = -4; cz <= 3; cz++) world.setChunkForced(cx, cz, true);
                    p.getServer().setPvpAllowed(false);
                    p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.VIBE_ID).unlock(ModAbilities.VIBE_ID));
                    p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.VIBE_ID));
                    PlayerProgressionService.setPowerLevel(p, 15);
                    p.setGameMode(GameType.SURVIVAL);
                    reset(p, 210, 0, 6000);
                    PlayerPowerSyncService.sync(p);
                });
            }
            // ---------------------------------------------------------- the held breach, from the side
            case 128 -> {
                TempestCamera.fix(new Vec3(214.6, -58.1, 2.4), new Vec3(210.5, -58.7, 2.1));
                server(mc, p -> {
                    p.removeAllEffects();
                    hold(p);
                });
            }
            case 137 -> capture = "50-channel-start.png";
            case 144 -> capture = "51-channel-half.png";
            case 151 -> capture = "52-channel-nearly-open.png";
            case 158 -> capture = "53-channel-open.png";
            // ---------------------------------------------------------- in first person
            case 166 -> {
                TempestCamera.fix(null, null);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(mc, p -> reset(p, 232, 0, 6000));
            }
            case 170 -> server(mc, VibeVisionPreview::hold);
            case 179 -> capture = "54-channel-first-person.png";
            case 188 -> capture = "55-channel-first-person-late.png";
            // ---------------------------------------------------------- turning while it opens
            case 196 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> reset(p, 252, 0, 6000));
            }
            case 200 -> server(mc, VibeVisionPreview::hold);
            case 207 -> server(mc, p -> p.connection.teleport(p.getX(), p.getY(), p.getZ(), 48, 12));
            case 209 -> TempestCamera.fix(new Vec3(249.5, -56.4, -2.5), new Vec3(253.6, -59.2, 2.2));
            case 213 -> capture = "56-channel-turned-with-the-look.png";
            case 215 -> TempestCamera.fix(null, null);
            // ---------------------------------------------------------- a vision of an ally in danger
            case 224 -> server(mc, p -> {
                reset(p, 272, 0, 6000);
                other = stranger(p, "Claire", new Vec3(272.5, -60, 44.5), 180);
                zombies(other, 1.6, -1.2, -1.4, -1.6, .3, 2.1);
            });
            case 230 -> {
                mc.options.hideGui = false;
                mc.gui.getChat().clearMessages(false);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(mc, p -> {
                    // Hurt only now, so the ordinary check can't start the vision a moment early.
                    other.setHealth(4);
                    System.out.println("VIBE_VISION_PREVIEW vision " + VibeVisions.trigger(p));
                });
            }
            case 236 -> capture = "57-vision-flicker.png";
            case 243 -> capture = "58-vision-flicker-deeper.png";
            case 256 -> capture = "59-vision.png";
            case 272 -> capture = "60-vision-later.png";
            case 282 -> server(mc, p -> {
                VibeVisions.forget(p);
                other.serverLevel().removePlayerImmediately(other, Entity.RemovalReason.DISCARDED);
                listed(p, other, false);
            });
            case 287 -> capture = "61-vision-wake.png";
            // ---------------------------------------------------------- the hologram, as the ally sees it
            case 300 -> server(mc, p -> {
                reset(p, 302, 0, 12600);
                zombies(p, 1.8, -1.4, -1.9, -.8);
                other = stranger(p, "Cisco", new Vec3(302.5, -60, 50.5), 180);
                other.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.VIBE_ID).unlock(ModAbilities.VIBE_ID));
                PlayerProgressionService.setPowerLevel(other, 15);
            });
            case 304 -> {
                mc.options.hideGui = true;
                server(mc, p -> {
                    p.setHealth(5);
                    System.out.println("VIBE_VISION_PREVIEW Cisco's vision " + VibeVisions.trigger(other));
                });
            }
            case 324 -> server(mc, p -> System.out.println("VIBE_VISION_PREVIEW hologram " + VibeVisions.press(other)
                    + " at " + other.position()));
            case 328 -> capture = "62-hologram-arriving.png";
            case 338 -> capture = "63-hologram-first-person.png";
            case 342 -> TempestCamera.fix(new Vec3(306.5, -58.2, 1.4), new Vec3(302.5, -59, 3.2));
            case 348 -> capture = "64-hologram-from-the-side.png";
            case 352 -> TempestCamera.fix(new Vec3(305.5, -58.3, 47.5), new Vec3(302.5, -59, 50.5));
            case 358 -> capture = "65-the-double-left-behind.png";
            case 364 -> {
                TempestCamera.fix(null, null);
                server(mc, p -> VibeVisions.forget(other));
            }
            // ---------------------------------------------------------- the held Vibe Blast
            case 372 -> server(mc, p -> {
                other.serverLevel().removePlayerImmediately(other, Entity.RemovalReason.DISCARDED);
                listed(p, other, false);
                reset(p, 332, 0, 6000);
                p.setHealth(20);
                target(p, 0, 7);
            });
            case 378 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                TempestCamera.fix(new Vec3(336.2, -58.2, 2.8), new Vec3(332.5, -58.8, 4.2));
                server(mc, p -> System.out.println("VIBE_VISION_PREVIEW stream " + AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false)));
            }
            case 386 -> capture = "66-blast-held-side.png";
            case 388 -> TempestCamera.fix(null, null);
            case 394 -> capture = "67-blast-held-behind.png";
            case 396 -> mc.options.setCameraType(CameraType.FIRST_PERSON);
            case 400 -> capture = "68-blast-held-first-person.png";
            case 402 -> server(mc, p -> {
                AbilityUseService.releaseAssignedAction(p, AbilitySlot.PRIMARY);
                System.out.println("VIBE_VISION_PREVIEW stream still on " + VibeService.streaming(p) + " stamina " + PlayerEnergyService.getStamina(p));
            });
            // ---------------------------------------------------------- Read the Vibe: ghosts of a fight
            case 410 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> {
                    reset(p, 352, 0, 6000);
                    var zombie = target(p, 1.6, 4.4);
                    var villager = EntityType.VILLAGER.create(p.serverLevel());
                    villager.setNoAi(true);
                    villager.moveTo(p.getX(), p.getY(), p.getZ() + 3, 160, 0);
                    p.serverLevel().addFreshEntity(villager);
                    villager.hurt(p.damageSources().mobAttack(zombie), 1);
                    zombie.discard();
                    p.connection.teleport(p.getX(), p.getY(), p.getZ(), 0, 12);
                });
            }
            case 414 -> server(mc, p -> System.out.println("VIBE_VISION_PREVIEW read " + AbilityUseService.activateAssignedAction(p, AbilitySlot.TERTIARY, false)));
            case 424 -> capture = "69-read-the-vibe-attacker-lunges.png";
            case 431 -> capture = "70-read-the-vibe-victim-recoils.png";
            // ---------------------------------------------------------- Breach Hop
            case 444 -> server(mc, p -> reset(p, 372, 0, 6000));
            case 448 -> {
                TempestCamera.fix(new Vec3(375.5, -57.2, -6.5), new Vec3(375.5, -59, .5));
                server(mc, p -> System.out.println("VIBE_VISION_PREVIEW hop "
                        + com.jayptucker.heroesevolved.vibe.VibeStyle.STYLE.dodge(p, com.jayptucker.heroesevolved.combat.FightingStyles.Dodge.LEFT)));
            }
            case 449 -> capture = "71-breach-hop-dive.png";
            case 451 -> capture = "72-breach-hop-out.png";
            case 454 -> capture = "73-breach-hop-after.png";
            // ---------------------------------------------------------- Rift Fall
            case 462 -> {
                TempestCamera.fix(null, null);
                server(mc, p -> {
                    reset(p, 392, 0, 6000);
                    PlayerProgressionService.setPowerLevel(p, 20);
                    PlayerPowerSyncService.sync(p);
                    // The local player's own client owns its look: turn it with a teleport, not setXRot.
                    p.connection.teleport(p.getX(), p.getY(), p.getZ(), 0, 16);
                    target(p, 0, 6);
                    target(p, 2.2, 5);
                    target(p, -2, 6.5);
                    target(p, 1, 8);
                });
            }
            case 468 -> {
                TempestCamera.fix(new Vec3(402, -55.5, -3), new Vec3(392.5, -52, 6));
                server(mc, p -> System.out.println("VIBE_VISION_PREVIEW rift " + AbilityUseService.activateAssignedAction(p, AbilitySlot.QUINARY, false)));
            }
            case 478 -> capture = "74-rift-fall-warning.png";
            case 490 -> capture = "75-rift-fall-swallowed.png";
            case 494 -> capture = "76-rift-fall-falling.png";
            case 499 -> capture = "77-rift-fall-landing.png";
            case 506 -> capture = "78-rift-fall-after.png";
            // ---------------------------------------------------------- beds behind a breach must not show through it
            case 520 -> {
                TempestCamera.fix(null, null);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(mc, p -> {
                    reset(p, 412, 180, 6000);
                    p.teleportTo(p.serverLevel(), 412.5, -60, -40.5, 180, 0);
                    p.setOnGround(true);
                    System.out.println("VIBE_VISION_PREVIEW tuned " + com.jayptucker.heroesevolved.vibe.VibeFrequencies.mark(p, 0));
                    p.teleportTo(p.serverLevel(), 412.5, -60, .5, 0, 0);
                    var level = p.serverLevel();
                    // Rain, as in the report of the rim flickering.
                    level.setWeatherParameters(0, 6000, true, false);
                    for (int x = 409; x <= 416; x++)
                        for (int y = -60; y <= -57; y++)
                            level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, y, 9), net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState());
                    for (int x = 410; x <= 415; x += 2) {
                        var foot = new net.minecraft.core.BlockPos(x, -60, 7);
                        var bed = net.minecraft.world.level.block.Blocks.WHITE_BED.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.SOUTH);
                        level.setBlockAndUpdate(foot, bed);
                        level.setBlockAndUpdate(foot.south(), bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
                                net.minecraft.world.level.block.state.properties.BedPart.HEAD));
                    }
                    level.setBlockAndUpdate(new net.minecraft.core.BlockPos(411, -59, 8), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
                    level.setBlockAndUpdate(new net.minecraft.core.BlockPos(414, -59, 8), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
                });
            }
            case 526 -> server(mc, p -> com.jayptucker.heroesevolved.vibe.VibeFrequencies.choose(p,
                    com.jayptucker.heroesevolved.network.FrequencyChoicePayload.BREACH, 0, ""));
            case 556 -> capture = "79-beds-behind-a-breach.png";
            case 558 -> server(mc, p -> p.teleportTo(p.serverLevel(), 413.4, -60, -.5, -12, 4));
            case 562 -> capture = "80-beds-behind-a-breach-angle.png";
            case 564 -> server(mc, p -> p.teleportTo(p.serverLevel(), 412.5, -60, .9, 0, -8));
            case 568 -> capture = "81-breach-rim-close-in-rain.png";
            case 569 -> capture = "82-breach-rim-close-in-rain-next-frame.png";
            case 576 -> PreviewQuiet.quit();
            default -> { }
        }
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (capture == null) return;
        Minecraft mc = Minecraft.getInstance();
        Path path = mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Files.createDirectories(path.getParent());
            image.writeToFile(path);
            System.out.println("VIBE_VISION_PREVIEW_CAPTURE " + capture);
        } catch (Exception e) {
            throw new IllegalStateException("Could not capture Vibe vision preview", e);
        } finally {
            capture = null;
        }
    }
}
