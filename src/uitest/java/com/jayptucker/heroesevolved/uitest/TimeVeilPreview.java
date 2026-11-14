package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.expansion.AdvancedTechniques;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.time.TimeStopService;
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
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Moving faster than time, as an ordinary player sees it: a speedster's Perception and a traveller's Still World leave
 * a frozen stand-in, then the stand-in vanishes (or the caster is simply back on the spot). You have no power, so you
 * see what everyone else sees.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class TimeVeilPreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;
    private static ServerPlayer caster;

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    @SuppressWarnings("unchecked") private static <T> T field(PlayerList list, String name) {
        try { Field f = PlayerList.class.getDeclaredField(name); f.setAccessible(true); return (T) f.get(list); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    /** A second player holding {@code power}, seen by the client as an ordinary player (see AGENTS.md: previews with two players). */
    private static ServerPlayer second(ServerPlayer you, net.minecraft.resources.ResourceLocation power, double x, double z, String name) {
        ServerPlayer other = new FakePlayer(you.serverLevel(), new GameProfile(UUID.randomUUID(), name));
        try {
            var connection = other.connection.getConnection();
            Field channel = net.minecraft.network.Connection.class.getDeclaredField("channel");
            channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new io.netty.channel.embedded.EmbeddedChannel());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        other.moveTo(x + 0.5, -60, z + 0.5, 180, 0);
        other.setYHeadRot(180); other.yBodyRot = 180;
        other.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        PlayerProgressionService.setLevel(other, 5);
        PlayerEnergyService.restore(other, 999);
        PlayerList list = you.getServer().getPlayerList();
        TimeVeilPreview.<List<ServerPlayer>>field(list, "players").add(other);
        TimeVeilPreview.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").put(other.getUUID(), other);
        you.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(other)));
        you.serverLevel().addNewPlayer(other);
        return other;
    }

    private static void drop() {
        if (caster == null) return;
        PlayerList list = caster.getServer().getPlayerList();
        TimeVeilPreview.<List<ServerPlayer>>field(list, "players").remove(caster);
        TimeVeilPreview.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").remove(caster.getUUID());
        caster.serverLevel().removePlayerImmediately(caster, Entity.RemovalReason.DISCARDED);
        caster = null;
    }

    /** Moves the caster on the server; everyone else would see it, if they could see them at all. */
    private static void step(double x, double z) { caster.moveTo(x + 0.5, -60, z + 0.5, 180, 0); }

    private static void shot(String name) { pending = "veil-" + name + ".png"; }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.veil")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Veil-" + System.currentTimeMillis(),
                    new LevelSettings("Veil preview", GameType.SURVIVAL, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(91L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        switch (++worldTicks) {
            case 150 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(6000);
                p.getAbilities().invulnerable = true; p.onUpdateAbilities();
                p.teleportTo(p.serverLevel(), 900.5, -60, 0.5, 0, 8);
            });
            case 156 -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON); }
            // ---- A speedster's Perception: frozen to you, then gone in a blur.
            case 165 -> onServer(mc, p -> caster = second(p, ModAbilities.SUPERSPEED_ID, 900, 6, "Runner"));
            case 172 -> { mc.player.setYRot(0); mc.player.setXRot(8); }
            case 180 -> shot("1-before");
            case 185 -> onServer(mc, p -> AdvancedTechniques.startPerception(caster));
            case 190 -> onServer(mc, p -> step(905, 9));
            case 196 -> shot("2-perception-stand-in");
            case 205 -> onServer(mc, p -> AdvancedTechniques.endPerception(caster));
            case 207 -> shot("3-perception-vanish");
            case 214 -> shot("4-perception-after");
            // ---- Perception again, but back on the spot before it ends: nothing to see.
            case 225 -> onServer(mc, p -> step(900, 6));
            case 232 -> onServer(mc, p -> AdvancedTechniques.startPerception(caster));
            case 236 -> onServer(mc, p -> step(896, 10));
            case 244 -> onServer(mc, p -> step(900, 6));
            case 250 -> onServer(mc, p -> AdvancedTechniques.endPerception(caster));
            case 253 -> shot("5-perception-home");
            // ---- A traveller's Still World: frozen to you, and when time resumes they are elsewhere.
            case 262 -> onServer(mc, p -> { drop(); caster = second(p, ModAbilities.TIME_MANIPULATION_ID, 900, 6, "Traveller"); });
            case 272 -> onServer(mc, p -> TimeStopService.start(caster, 5));
            case 278 -> onServer(mc, p -> step(894, 9));
            case 284 -> shot("6-stopped-stand-in");
            case 292 -> onServer(mc, p -> TimeStopService.stop(caster));
            case 294 -> shot("7-stopped-reappear");
            case 303 -> shot("8-stopped-after");
            case 310 -> onServer(mc, p -> drop());
            case 318 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("VEIL_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
