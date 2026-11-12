package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.intro.PowerIntroHistory;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.radiation.RadiationService;
import com.jayptucker.heroesevolved.speed.SpeedContainment;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A speedster (a stand-in player) running the heat out of you, then holding in your blast, then burning away doing
 * it, photographed 900 blocks from spawn.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class ContainmentPreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;
    private static ServerPlayer runner;

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    @SuppressWarnings("unchecked") private static <T> T field(PlayerList list, String name) {
        try { Field f = PlayerList.class.getDeclaredField(name); f.setAccessible(true); return (T) f.get(list); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static final List<ServerPlayer> STANDINS = new java.util.ArrayList<>();

    /** A tier-{@code tier} speedster who can be hurt, seen by the client as an ordinary player. */
    private static void speedster(ServerPlayer you, int tier, double x, double z, float health, boolean exhausted) {
        if (runner != null) drop();
        runner = standIn(you, ModAbilities.SUPERSPEED_ID, tier, x, z, "Runner");
        if (exhausted) PlayerEnergyService.tryConsume(runner, PlayerEnergyService.getEnergy(runner));
        runner.setHealth(health);
    }

    /** A stand-in player holding {@code power} at {@code tier}, who can be hurt, seen by the client as an ordinary player. */
    private static ServerPlayer standIn(ServerPlayer you, net.minecraft.resources.ResourceLocation power, int tier, double x, double z, String name) {
        ServerPlayer runner = new FakePlayer(you.serverLevel(), new GameProfile(UUID.randomUUID(), name)) {
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource damage) { return false; }
            // NeoForge's fake players refuse all player-caused damage; a real player in a PvP world doesn't.
            @Override public boolean canHarmPlayer(net.minecraft.world.entity.player.Player other) { return true; }
        };
        try {
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true); grace.setInt(runner, 0);
            // A stand-in in the player list is sent what every player is sent (the time, for one): it needs a channel
            // to take it, or the server crashes trying.
            var connection = runner.connection.getConnection();
            Field channel = net.minecraft.network.Connection.class.getDeclaredField("channel");
            channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new io.netty.channel.embedded.EmbeddedChannel());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        runner.moveTo(x + 0.5, -60, z + 0.5, 180, 0);
        runner.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(power).unlock(power));
        PlayerProgressionService.setLevel(runner, tier);
        PlayerEnergyService.restore(runner, 999);
        PlayerList list = you.getServer().getPlayerList();
        ContainmentPreview.<List<ServerPlayer>>field(list, "players").add(runner);
        ContainmentPreview.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").put(runner.getUUID(), runner);
        you.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(runner)));
        you.serverLevel().addNewPlayer(runner);
        STANDINS.add(runner);
        return runner;
    }

    private static void dropAll() {
        for (ServerPlayer p : STANDINS) {
            PlayerList list = p.getServer().getPlayerList();
            ContainmentPreview.<List<ServerPlayer>>field(list, "players").remove(p);
            ContainmentPreview.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").remove(p.getUUID());
            if (!p.isRemoved()) p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
        }
        STANDINS.clear();
        runner = null;
    }

    private static void drop() {
        if (runner == null) return;
        PlayerList list = runner.getServer().getPlayerList();
        ContainmentPreview.<List<ServerPlayer>>field(list, "players").remove(runner);
        ContainmentPreview.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").remove(runner.getUUID());
        runner.serverLevel().removePlayerImmediately(runner, Entity.RemovalReason.DISCARDED);
        runner = null;
    }

    private static volatile int watcher = -1;

    /** An invisible stand to watch from (only where the player need not move). */
    private static void watch(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        var stand = new net.minecraft.world.entity.decoration.ArmorStand(net.minecraft.world.entity.EntityType.ARMOR_STAND, p.serverLevel());
        stand.setInvisible(true); stand.setNoGravity(true); stand.setInvulnerable(true);
        stand.moveTo(x, y, z, yaw, pitch); stand.setYHeadRot(yaw);
        p.serverLevel().addFreshEntity(stand);
        watcher = stand.getId();
    }

    private static void look(Minecraft mc) {
        var e = watcher >= 0 ? mc.level.getEntity(watcher) : null;
        if (e != null) mc.setCameraEntity(e);
    }

    private static void shot(String name) { pending = "contain-" + name + ".png"; }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.contain")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Contain-" + System.currentTimeMillis(),
                    new LevelSettings("Containment preview", GameType.SURVIVAL, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(91L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        // The burning away, every three ticks.
        if (worldTicks >= 374 && worldTicks <= 548 && worldTicks % 6 == 2) pending = String.format("contain-3-burn-%03d.png", worldTicks - 374);
        if (worldTicks >= 602 && worldTicks <= 650 && worldTicks % 4 == 2) pending = String.format("contain-4-shield-%02d.png", worldTicks - 602);
        switch (++worldTicks) {
            case 150 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(13000);
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, PowerIntroHistory.empty().with(ModAbilities.RADIATION_ID));
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                        .assignDormant(ModAbilities.RADIATION_ID).unlock(ModAbilities.RADIATION_ID));
                PlayerProgressionService.setLevel(p, 5);
                p.getAbilities().invulnerable = true; p.onUpdateAbilities();
                p.teleportTo(p.serverLevel(), 900.5, -60, 0.5, 0, 12);
            });
            case 156 -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
            // Cooling: you run Hot, and the speedster runs the heat out of you.
            case 165 -> onServer(mc, p -> { speedster(p, 5, 900, 5, 20, false); RadiationService.addMass(p, 75 - RadiationService.mass(p)); });
            case 172 -> { mc.player.setYRot(0); mc.player.setXRot(12); }
            case 175 -> onServer(mc, p -> SpeedContainment.start(runner, 5));
            case 182 -> shot("1-cool-a");
            case 188 -> shot("1-cool-b");
            case 196 -> shot("1-cool-c");
            case 215 -> onServer(mc, p -> SpeedContainment.release(runner));
            // Containment: you begin to blow, and a top speedster holds it in.
            case 230 -> onServer(mc, p -> { RadiationService.addMass(p, 100 - RadiationService.mass(p) - 1); RadiationService.beginDetonation(p, 5, true);
                speedster(p, 5, 900, 5, 20, false); SpeedContainment.start(runner, 5); });
            case 245 -> shot("2-hold-a");
            case 275 -> shot("2-hold-b");
            case 305 -> shot("2-hold-c");
            case 328 -> shot("2-hold-d");
            case 337 -> shot("2-defused-a");
            case 342 -> shot("2-defused-b");
            // An exhausted runner below tier 5: they burn away, and the blast goes with them.
            // A fixed camera that sees the whole ring, so wherever the runner stops, they are in shot.
            case 362 -> onServer(mc, p -> watch(p, 900.5, -53, -11.5, 0, 26));
            case 366 -> look(mc);
            case 553 -> mc.setCameraEntity(mc.player);
            case 368 -> onServer(mc, p -> { RadiationService.addMass(p, 100 - RadiationService.mass(p) - 1); RadiationService.beginDetonation(p, 5, true);
                speedster(p, 4, 900, 5, 6, true); SpeedContainment.start(runner, 4); });
            case 556 -> onServer(mc, p -> dropAll());
            // Shield-makers at tier 5 standing round a blast: a force lattice, a kinetic shell, matter plates, ice, sound.
            case 570 -> onServer(mc, p -> {
                RadiationService.vent(p, 100);
                p.teleportTo(p.serverLevel(), 960.5, -60, 0.5, 0, 0);
                var powers = List.of(ModAbilities.INVISIBILITY_ID, ModAbilities.TELEKINESIS_ID, ModAbilities.MATTER_ASSEMBLY_ID,
                        ModAbilities.CRYOKINESIS_ID, ModAbilities.SONIC_RESONANCE_ID);
                for (int i = 0; i < powers.size(); i++) {
                    double a = Math.toRadians(-150 + i * 75);
                    standIn(p, powers.get(i), 5, 960 + Math.sin(a) * 12, Math.cos(a) * 12, "Shield" + i).setNoGravity(true);
                }
                watch(p, 960.5, -50, -34.5, 0, 14);
            });
            case 580 -> look(mc);
            case 588 -> onServer(mc, p -> {
                ServerPlayer one = STANDINS.getFirst();
                boolean plain = one.hurt(one.damageSources().generic(), 1);
                one.invulnerableTime = 0;
                boolean blast = one.hurt(one.damageSources().explosion(p, p), 1);
                System.out.println("SHIELD_PROBE pvp=" + p.getServer().isPvpAllowed() + " canHarm=" + one.canHarmPlayer(p)
                        + " plain=" + plain + " blast=" + blast + " health=" + one.getHealth());
                one.setHealth(20); one.invulnerableTime = 0;
            });
            case 596 -> onServer(mc, p -> com.jayptucker.heroesevolved.radiation.NuclearFallout.begin(p));
            case 615 -> onServer(mc, p -> STANDINS.forEach(s -> System.out.println("SHIELD_HEALTH " + s.getName().getString()
                    + " " + s.getHealth() + " shielded=" + com.jayptucker.heroesevolved.radiation.NuclearShields.shielded(s))));
            case 680 -> onServer(mc, p -> dropAll());
            case 690 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("CONTAIN_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
