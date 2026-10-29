package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.flight.FlightService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
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
import net.neoforged.neoforge.common.util.FakePlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Real server carries, photographed from the carrier and recipient viewpoints while moving and turning. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class CarryPreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;
    private static Mob carriedMob;
    private static volatile ServerPlayer otherCarrier;

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) action.accept(player);
        });
    }

    private static void reset(ServerPlayer p, double y, float yaw) {
        EntityCarryService.release(p);
        FlightService.stopForPowerRemoval(p);
        p.teleportTo(p.serverLevel(), 300.5, y, .5, yaw, 0);
        p.setDeltaMovement(Vec3.ZERO); p.setOnGround(y <= -60);
        p.getAbilities().flying = false; p.onUpdateAbilities();
    }

    private static void createMob(ServerPlayer p) {
        if (carriedMob != null) carriedMob.discard();
        carriedMob = EntityType.VILLAGER.create(p.serverLevel());
        carriedMob.setNoAi(true); carriedMob.setPersistenceRequired();
        carriedMob.moveTo(p.getX(), p.getY(), p.getZ() + 2, 180, 0);
        p.serverLevel().addFreshEntity(carriedMob);
        if (!EntityCarryService.pickUp(p, carriedMob)) throw new IllegalStateException("Preview could not carry nearby villager");
        EntityCarryService.tick(p);
    }

    private static void removeOther(ServerPlayer p) {
        if (otherCarrier == null) return;
        EntityCarryService.release(otherCarrier);
        p.serverLevel().removePlayerImmediately(otherCarrier, Entity.RemovalReason.DISCARDED);
        p.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(otherCarrier.getUUID())));
        otherCarrier = null;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.carry")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = false; mc.options.guiScale().set(2); mc.resizeDisplay();
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Carry-" + System.currentTimeMillis(),
                    new LevelSettings("Carry preview", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        worldTicks++;
        if (worldTicks >= 390 && worldTicks <= 440 && otherCarrier != null) onServer(mc, p -> {
            if (otherCarrier == null) return;
            otherCarrier.setPos(otherCarrier.position().add(0, 0, .12));
            otherCarrier.yBodyRot = otherCarrier.getYRot();
            EntityCarryService.tick(otherCarrier);
        });
        switch (worldTicks) {
            case 200 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> {
                    p.serverLevel().setDayTime(1000);
                    p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                            .assignDormant(ModAbilities.FLIGHT_ID).unlock(ModAbilities.FLIGHT_ID));
                    PlayerProgressionService.setLevel(p, 5);
                    reset(p, -60, 0); createMob(p);
                });
            }
            case 220 -> pending = "carry-1-carrier-first-person-standing.png";
            case 235 -> mc.options.keyUp.setDown(true);
            case 250 -> pending = "carry-2-carrier-first-person-walking.png";
            case 255 -> mc.options.keySprint.setDown(true);
            case 266 -> pending = "carry-3-carrier-first-person-sprinting.png";
            case 270 -> mc.options.keyJump.setDown(true);
            case 271 -> mc.options.keyJump.setDown(false);
            case 275 -> pending = "carry-4-carrier-first-person-jump.png";
            case 285 -> {
                mc.options.keyUp.setDown(false); mc.options.keySprint.setDown(false);
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
            case 290 -> pending = "carry-5-carrier-third-person-standing.png";
            case 305 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> {
                    p.teleportTo(p.serverLevel(), 300.5, -50, .5, 90, 0);
                    p.setOnGround(false); FlightService.toggleFlight(p); EntityCarryService.tick(p);
                });
            }
            case 315 -> pending = "carry-6-carrier-first-person-flight.png";
            case 320 -> onServer(mc, p -> {
                p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), -90, 0);
                EntityCarryService.tick(p);
            });
            case 325 -> pending = "carry-7-carrier-first-person-turned.png";
            case 330 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 335 -> pending = "carry-8-carrier-third-person-flight.png";
            case 345 -> onServer(mc, p -> EntityCarryService.hurl(p, p.getLookAngle().scale(1.5).add(0, .2, 0)));
            case 347 -> pending = "carry-9-hurl.png";
            case 375 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> {
                    reset(p, -60, 0);
                    if (carriedMob != null) { carriedMob.discard(); carriedMob = null; }
                    otherCarrier = new FakePlayer(p.serverLevel(), new GameProfile(UUID.randomUUID(), "CarryPreview"));
                    otherCarrier.moveTo(300.5, -60, -1.5, 0, 0);
                    otherCarrier.setNoGravity(true);
                    p.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(otherCarrier)));
                    p.serverLevel().addNewPlayer(otherCarrier);
                    if (!EntityCarryService.pickUp(otherCarrier, p)) throw new IllegalStateException("Preview could not carry local player");
                    EntityCarryService.tick(otherCarrier);
                });
            }
            case 385 -> pending = "carry-10-recipient-first-person-standing.png";
            case 405 -> pending = "carry-11-recipient-first-person-moving.png";
            case 410 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 420 -> pending = "carry-12-recipient-third-person-moving.png";
            case 430 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> {
                    if (otherCarrier != null) { otherCarrier.setYRot(90); EntityCarryService.tick(otherCarrier); }
                });
            }
            case 438 -> pending = "carry-13-recipient-first-person-turned.png";
            case 450 -> onServer(mc, p -> removeOther(p));
            case 465 -> pending = "carry-14-recipient-released.png";
            case 480 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("CARRY_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
