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
 * A villager the Eclipse woke a power in: his glow building over the countdown, the safe point's column of light, the
 * blast, and a speedster holding one in. Photographed 1200 blocks from spawn.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class MeltdownPreview {
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
        MeltdownPreview.<List<ServerPlayer>>field(list, "players").add(runner);
        MeltdownPreview.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").put(runner.getUUID(), runner);
        you.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(runner)));
        you.serverLevel().addNewPlayer(runner);
        STANDINS.add(runner);
        return runner;
    }

    private static void dropAll() {
        for (ServerPlayer p : STANDINS) {
            PlayerList list = p.getServer().getPlayerList();
            MeltdownPreview.<List<ServerPlayer>>field(list, "players").remove(p);
            MeltdownPreview.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").remove(p.getUUID());
            if (!p.isRemoved()) p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
        }
        STANDINS.clear();
        runner = null;
    }

    private static void drop() {
        if (runner == null) return;
        PlayerList list = runner.getServer().getPlayerList();
        MeltdownPreview.<List<ServerPlayer>>field(list, "players").remove(runner);
        MeltdownPreview.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").remove(runner.getUUID());
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

    private static void shot(String name) { pending = "meltdown-" + name + ".png"; }

    private static net.minecraft.world.entity.npc.Villager him;

    private static void set(ServerPlayer p, int x, int y, int z, net.minecraft.world.level.block.state.BlockState state) {
        p.serverLevel().setBlockAndUpdate(new net.minecraft.core.BlockPos(x, y, z), state);
    }

    /** A bell on a little cobbled square, a few stalls round it, and villagers about. */
    private static void village(ServerPlayer p, int x, int z) {
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++)
            set(p, x + dx, -61, z + dz, ((Math.abs(dx) + Math.abs(dz)) % 3 == 0 ? net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE
                    : net.minecraft.world.level.block.Blocks.COBBLESTONE).defaultBlockState());
        set(p, x, -60, z, net.minecraft.world.level.block.Blocks.COBBLESTONE_WALL.defaultBlockState());
        set(p, x, -59, z, net.minecraft.world.level.block.Blocks.BELL.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BellBlock.ATTACHMENT, net.minecraft.world.level.block.state.properties.BellAttachType.FLOOR));
        int[][] stalls = {{-5, -3}, {5, -3}, {-5, 4}, {5, 4}};
        for (int[] s : stalls) {
            set(p, x + s[0], -60, z + s[1], net.minecraft.world.level.block.Blocks.HAY_BLOCK.defaultBlockState());
            set(p, x + s[0], -60, z + s[1] + 1, net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState());
            set(p, x + s[0], -59, z + s[1] + 1, net.minecraft.world.level.block.Blocks.LANTERN.defaultBlockState());
        }
        double[][] spots = {{-3, 2}, {3, 3}, {-2, -4}, {4, -2}, {0, 5}};
        for (double[] s : spots) {
            var v = net.minecraft.world.entity.EntityType.VILLAGER.create(p.serverLevel());
            v.moveTo(x + 0.5 + s[0], -60, z + 0.5 + s[1], (float) (Math.random() * 360), 0);
            p.serverLevel().addFreshEntity(v);
        }
    }

    /** The villager the Eclipse woke, just in front of the bell, with {@code fuse} ticks to go. */
    private static void glow(ServerPlayer p, int x, int z, int fuse) {
        if (him != null) { com.jayptucker.heroesevolved.village.VillagerMeltdown.cancel(him); him.discard(); }
        him = net.minecraft.world.entity.EntityType.VILLAGER.create(p.serverLevel());
        him.setVillagerData(him.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER));
        him.moveTo(x + 0.5, -60, z - 1.5, 180, 0);
        p.serverLevel().addFreshEntity(him);
        var m = com.jayptucker.heroesevolved.village.VillagerMeltdown.start(him, fuse);
        System.out.println("MELTDOWN_START " + (m != null) + " safe=" + (m == null ? null : m.safe()) + " village=" + (m == null ? 0 : m.villageSize()));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.meltdown")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Meltdown-" + System.currentTimeMillis(),
                    new LevelSettings("Meltdown preview", GameType.SURVIVAL, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(91L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        // The blast, frame by frame.
        if (worldTicks >= 318 && worldTicks <= 360 && worldTicks % 6 == 0) pending = String.format("meltdown-4-blast-%02d.png", worldTicks - 318);
        switch (++worldTicks) {
            case 150 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(12600);
                p.getAbilities().invulnerable = true; p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
                p.teleportTo(p.serverLevel(), 1200.5, -50, -14.5, 0, 22);
                village(p, 1200, 0);
            });
            // Down among them as he starts to glow: they scream, shout and run.
            case 160 -> onServer(mc, p -> { glow(p, 1200, 0, 900); p.teleportTo(p.serverLevel(), 1206.5, -59.4, -7.5, 38, 8); });
            case 164 -> { mc.options.hideGui = true; mc.player.setYRot(38); mc.player.setXRot(8); }
            case 170 -> shot("1-flee-a");
            case 178 -> shot("1-flee-b");
            case 186 -> shot("1-flee-c");
            case 194 -> shot("1-flee-d");
            // From above, with the countdown on the bar and the safe point beyond.
            case 198 -> onServer(mc, p -> p.teleportTo(p.serverLevel(), 1200.5, -50, -14.5, 0, 22));
            case 202 -> { mc.options.hideGui = false; mc.player.setYRot(0); mc.player.setXRot(22); }
            case 205 -> shot("1-start");
            case 207 -> { mc.options.hideGui = true; mc.player.setXRot(4); }
            case 209 -> shot("2-safe");
            // A short fuse, to see it build.
            case 210 -> onServer(mc, p -> { glow(p, 1200, 0, 110); p.teleportTo(p.serverLevel(), 1200.5, -55, -10.5, 0, 24); });
            case 216 -> { mc.player.setYRot(0); mc.player.setXRot(24); }
            case 240 -> shot("3-build-a");
            case 275 -> shot("3-build-b");
            case 305 -> shot("3-build-c");
            case 312 -> onServer(mc, p -> p.teleportTo(p.serverLevel(), 1200.5, -40, -44.5, 0, 18));
            case 316 -> { mc.player.setYRot(0); mc.player.setXRot(18); }
            case 380 -> shot("4-after");
            // A speedster holds another one in.
            case 400 -> onServer(mc, p -> {
                village(p, 1300, 0);
                him = null;
                glow(p, 1300, 0, 600);
                p.teleportTo(p.serverLevel(), 1300.5, -52, -14.5, 0, 26);
                speedster(p, 5, 1304, -1, 20, false);
            });
            case 406 -> { mc.player.setYRot(0); mc.player.setXRot(26); }
            case 412 -> onServer(mc, p -> System.out.println("MELTDOWN_RUN " + SpeedContainment.start(runner, 5)));
            case 430 -> shot("5-hold-a");
            case 470 -> shot("5-hold-b");
            case 505 -> shot("5-hold-c");
            case 518 -> shot("5-defused-a");
            case 524 -> shot("5-defused-b");
            case 540 -> onServer(mc, p -> System.out.println("MELTDOWN_SAVED alive=" + him.isAlive()
                    + " critical=" + com.jayptucker.heroesevolved.village.VillagerMeltdown.critical(him)));
            case 550 -> onServer(mc, p -> dropAll());
            case 560 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("MELTDOWN_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
