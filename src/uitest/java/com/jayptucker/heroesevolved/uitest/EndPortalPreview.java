package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.endportal.EndPortalRitual;
import com.jayptucker.heroesevolved.endportal.PortalGeometry;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.jayptucker.heroesevolved.network.ClientScreenTintState;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
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
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Photographs actual server-driven offerings, with real local key input; camera keeps the local player as its entity. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class EndPortalPreview {
    private static final BlockPos CENTER = new BlockPos(150, -60, 150);
    private static ServerPlayer partner;
    private static boolean started;
    private static int startup, age;
    private static String capture;
    private static boolean on() { return Boolean.getBoolean("heroes.preview.endportal"); }

    private static void server(Consumer<ServerPlayer> task) {
        var mc = Minecraft.getInstance(); var id = mc.player.getUUID(); var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try { task.accept(server.getPlayerList().getPlayer(id)); }
            catch (Throwable error) { error.printStackTrace(); PreviewQuiet.report("PORTAL_PREVIEW FAIL " + error); }
        });
    }

    private static void power(ServerPlayer p, ResourceLocation power, int level) {
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(power));
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().replaceWithUnlockedPower(power));
        p.setData(ModDataAttachments.PLAYER_UNLIMITED_ENERGY, false);
        PlayerProgressionService.setPowerLevel(p, level);
        PlayerEnergyService.restore(p, 999);
        PlayerPowerSyncService.sync(p);
        PlayerEnergyService.sendNow(p);
    }

    private static void stand(ServerPlayer p, int x) {
        var world = p.getServer().overworld();
        Vec3 at = Vec3.atBottomCenterOf(CENTER.offset(x, 1, -2));
        Vec3 look = PortalGeometry.surface(CENTER).subtract(at.add(0, p.getEyeHeight(), 0));
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance()));
        p.teleportTo(world, at.x, at.y, at.z, yaw, pitch);
        p.setPos(at); p.setYRot(yaw); p.setXRot(pitch); p.setYHeadRot(yaw);
        p.yBodyRot = yaw; p.yBodyRotO = yaw; p.setDeltaMovement(Vec3.ZERO); p.setOnGround(true);
    }

    private static void reset(ServerPlayer p, ResourceLocation power) {
        EndPortalRitual.clear();
        if (partner != null) {
            EndPortalRitual.cancel(partner);
            partner.serverLevel().removePlayerImmediately(partner, Entity.RemovalReason.DISCARDED);
            partner.connection.getConnection().channel().close(); partner = null;
        }
        var world = p.getServer().overworld();
        for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
            world.setBlock(CENTER.offset(x, -1, z), (Math.abs(x) == 7 || Math.abs(z) == 7 ? Blocks.POLISHED_DEEPSLATE
                    : (x + z) % 4 == 0 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState(), 3);
            for (int y = 0; y < 4; y++) world.setBlock(CENTER.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        for (BlockPos at : PortalGeometry.frames(CENTER)) world.setBlock(at, Blocks.END_PORTAL_FRAME.defaultBlockState()
                .setValue(EndPortalFrameBlock.HAS_EYE, true).setValue(EndPortalFrameBlock.FACING, PortalGeometry.facing(at.subtract(CENTER))), 3);
        p.setGameMode(GameType.SURVIVAL); p.removeAllEffects(); p.setHealth(p.getMaxHealth());
        power(p, power, 16); stand(p, 0);
        EndPortalRitual.eyeInserted(world, CENTER.offset(0, 0, -2), p);
        Minecraft.getInstance().execute(SetPieceClient::clear);
    }

    private static void partner(ServerPlayer p) {
        var q = new FakePlayer(p.serverLevel(), new GameProfile(UUID.randomUUID(), "Morgan"));
        var connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
        q.connection = new ServerGamePacketListenerImpl(p.getServer(), connection, q, CommonListenerCookie.createInitial(q.getGameProfile(), false)) {
            @Override public boolean hasChannel(ResourceLocation id) { return true; }
            @Override public void send(Packet<?> packet) {}
        };
        power(q, ModAbilities.ELECTROKINESIS_ID, 16); stand(q, 1);
        p.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(q)));
        p.serverLevel().addNewPlayer(q);
        partner = q;
    }

    private static void camera(double x, double y, double z) {
        var mc = Minecraft.getInstance(); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        TempestCamera.fix(Vec3.atCenterOf(CENTER).add(x, y, z), Vec3.atCenterOf(CENTER).add(0, 1.1, -.6));
    }

    private static void check(boolean passed, String what) { PreviewQuiet.report("PORTAL_PREVIEW " + what + " " + (passed ? "PASS" : "FAIL")); }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!on()) return;
        var mc = Minecraft.getInstance();
        if (!started && ++startup >= 35 && mc.screen != null && mc.getOverlay() == null) {
            started = true; mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(5);
            mc.options.guiScale().set(2); mc.options.fov().set(68); mc.resizeDisplay();
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules(); rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Portal-" + System.currentTimeMillis(),
                    new LevelSettings("End Portal Preview", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(73L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        age++;
        if (age == 104) server(PrologueService::skip);
        if (age == 110) {
            mc.options.hideGui = true; mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            server(p -> {
                CryoChamberService.release(p);
                var world = p.getServer().overworld();
                for (int x = 8; x <= 10; x++) for (int z = 8; z <= 10; z++) world.setChunkForced(x, z, true);
                reset(p, ModAbilities.PYROKINESIS_ID);
                PlayerProgressionService.setPowerLevel(p, 15); PlayerPowerSyncService.sync(p);
                world.setDayTime(6000);
                System.out.println("PORTAL_PREVIEW staged " + p.level().dimension().location() + " on " + world.getBlockState(p.blockPosition().below()));
            });
        }
        if (age % 20 == 0) { ClientScreenTintState.clear(); mc.getToasts().clear(); }
        switch (age) {
            case 140 -> camera(6, 5, 6);
            case 145 -> capture = "01-dormant.png";
            case 150 -> ModKeyMappings.ABILITY_1.setDown(true);
            case 153 -> ModKeyMappings.ABILITY_1.setDown(false);
            case 160 -> server(p -> { check(!EndPortalRitual.channeling(p), "level 15 refused"); power(p, ModAbilities.PYROKINESIS_ID, 16); partner(p); });
            case 180 -> { ModKeyMappings.ABILITY_1.setDown(true); server(p -> AbilityUseService.activateKey(partner, 1, false)); }
            case 215 -> { camera(4, 3.2, -5); capture = "02-paired-offerings-early.png"; }
            case 250 -> { camera(-4, 2.7, -3); capture = "03-hands-and-streams.png"; }
            case 285 -> { camera(5, 4, 4); capture = "04-paired-offerings-late.png"; }
            case 300 -> { TempestCamera.fix(null, null); mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false; }
            case 307 -> capture = "05-first-person.png";
            case 320 -> { mc.options.hideGui = false; capture = "06-stamina-draining.png"; }
            case 335 -> { mc.options.hideGui = true; camera(5, 5, 4); }
            case 355 -> capture = "07-opening-membrane.png";
            case 365 -> capture = "08-opening-iris.png";
            case 386 -> { ModKeyMappings.ABILITY_1.setDown(false); server(p -> check(PortalGeometry.open(p.serverLevel(), CENTER), "co-op opened")); }
            case 395 -> capture = "09-portal-open.png";
            case 420 -> server(p -> reset(p, ModAbilities.HYDROKINESIS_ID));
            case 440 -> ModKeyMappings.ABILITY_1.setDown(true);
            case 490 -> { camera(-3, 2.5, -4); capture = "10-solo-water-offering.png"; }
            case 605 -> ModKeyMappings.ABILITY_1.setDown(false);
            case 610 -> server(p -> { check(!PortalGeometry.open(p.serverLevel(), CENTER), "solo first offering waits"); p.serverLevel().setDayTime(18000); stand(p, 1); });
            case 635 -> { camera(4, 3, -3); capture = "11-remembered-offering-night.png"; }
            case 970 -> { ModKeyMappings.ABILITY_1.setDown(true); server(p -> check(PlayerEnergyService.getStamina(p) >= PlayerEnergyService.getMaximumEnergy(p) * .95, "solo recovered naturally")); }
            case 1040 -> { camera(3, 3, -4); capture = "12-solo-second-offering.png"; }
            case 1140 -> { camera(5, 5, 4); capture = "13-solo-convergence.png"; }
            case 1185 -> { ModKeyMappings.ABILITY_1.setDown(false); server(p -> check(PortalGeometry.open(p.serverLevel(), CENTER), "solo opened")); }
            case 1190 -> capture = "14-solo-portal-open.png";
            case 1210 -> PreviewQuiet.quit();
            default -> {}
        }
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (capture == null) return;
        var mc = Minecraft.getInstance();
        try (var shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            var file = mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);
            Files.createDirectories(file.getParent()); shot.writeToFile(file);
            System.out.println("PORTAL_CAPTURE " + capture);
        } catch (Exception e) { throw new RuntimeException(e); }
        capture = null;
    }
}
