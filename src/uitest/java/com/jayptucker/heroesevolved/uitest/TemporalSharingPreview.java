package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.expansion.AdvancedTechniques;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.time.TemporalSharing;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.Difficulty;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** A real second player wrapped in shared Perception, seen from daylight, night, side and first-person angles. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class TemporalSharingPreview {
    private static boolean started;
    private static int startup, age;
    private static String capture;
    private static ServerPlayer guest;

    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance(); var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    private static void camera(double x, double y, double z) {
        Minecraft.getInstance().execute(() -> TempestCamera.fix(new Vec3(x, y, z), new Vec3(150.5, -59, 152.5)));
    }

    @SuppressWarnings("unchecked") private static <T> T field(PlayerList list, String name) {
        try { var field = PlayerList.class.getDeclaredField(name); field.setAccessible(true); return (T) field.get(list); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void stage(ServerPlayer player) {
        PrologueService.skip(player); CryoChamberService.release(player);
        var world = player.getServer().overworld();
        for (int x = 8; x <= 10; x++) for (int z = 8; z <= 10; z++) world.setChunkForced(x, z, true);
        player.teleportTo(world, 150.5, -60, 150.5, 0, 10);
        player.setData(ModDataAttachments.PLAYER_ABILITIES,
                PlayerAbilityData.empty().assignDormant(ModAbilities.SUPERSPEED_ID).unlock(ModAbilities.SUPERSPEED_ID));
        PlayerProgressionService.setLevel(player, 5); PlayerPowerSyncService.sync(player);
        player.getAbilities().invulnerable = true; player.onUpdateAbilities();
        world.setDayTime(6000);
        guest = new FakePlayer(world, new GameProfile(UUID.randomUUID(), "TimeGuest"));
        try {
            var connection = guest.connection.getConnection();
            var channel = net.minecraft.network.Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            channel.set(connection, new io.netty.channel.embedded.EmbeddedChannel());
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        guest.moveTo(150.5, -60, 152.5, 180, 0); guest.yBodyRot = guest.yBodyRotO = 180; guest.setYHeadRot(180);
        PlayerList list = player.getServer().getPlayerList();
        TemporalSharingPreview.<List<ServerPlayer>>field(list, "players").add(guest);
        TemporalSharingPreview.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").put(guest.getUUID(), guest);
        player.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(guest)));
        world.addNewPlayer(guest);
        if (Boolean.getBoolean("heroes.preview.mobtime")) {
            for (int i = 0; i < 3; i++) {
                // The third is gifted, so it is a pillager: only villagers and illagers carry powers.
                net.minecraft.world.entity.Mob mob = i == 2 ? net.minecraft.world.entity.EntityType.PILLAGER.create(world) : net.minecraft.world.entity.EntityType.ZOMBIE.create(world);
                mob.getPersistentData().putBoolean(com.jayptucker.heroesevolved.gifted.GiftedMobs.ROLLED, true);
                mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
                mob.moveTo(147.5 + i * 3, -60, 156.5, 180, 0);
                mob.setTarget(player); world.addFreshEntity(mob);
                if (i == 2) com.jayptucker.heroesevolved.gifted.GiftedMobs.bestow(mob, ModAbilities.SUPERSPEED_ID,
                        com.jayptucker.heroesevolved.gifted.Rarity.RARE, true,
                        com.jayptucker.heroesevolved.gifted.Gift.Origin.ADMIN, null, "Temporal peer");
            }
        }
        System.out.println("TEMPORAL_PREVIEW staged " + player.level().dimension().location() + " at " + player.blockPosition()
                + " on " + world.getBlockState(player.blockPosition().below()));
    }

    private static void share(ServerPlayer player) {
        AdvancedTechniques.startPerception(player);
        System.out.println("TEMPORAL_PREVIEW shared=" + TemporalSharing.include(player, guest));
        if (Boolean.getBoolean("heroes.preview.mobtime")) player.teleportTo(player.serverLevel(), 147.5, -60, 150.5, 0, 12);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.temporal")) return;
        var mc = Minecraft.getInstance();
        if (!started && ++startup > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true; mc.options.pauseOnLostFocus = false;
            var rules = new GameRules(); rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Temporal-" + System.currentTimeMillis(),
                    new LevelSettings("Temporal sharing", GameType.SURVIVAL, false, Boolean.getBoolean("heroes.preview.mobtime") ? Difficulty.EASY : Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(91L, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                            .getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        switch (++age) {
            case 160 -> server(TemporalSharingPreview::stage);
            case 310 -> { mc.options.hideGui = true; server(p -> { share(p); camera(154.5, -58.5, 149); }); }
            case 335 -> capture = "1-daylight-front.png";
            case 345 -> camera(155, -59, 154.5);
            case 365 -> capture = "2-daylight-back.png";
            case 375 -> camera(155, -59.5, 152.5);
            case 395 -> capture = "3-side.png";
            case 405 -> server(p -> { share(p); p.serverLevel().setDayTime(18000); });
            case 425 -> capture = "4-night.png";
            case 435 -> { TempestCamera.fix(null, null); server(p -> { p.serverLevel().setDayTime(6000); p.teleportTo(p.serverLevel(), 150.5, -60, 150.5, 0, 12); }); }
            case 455 -> capture = "5-first-person.png";
            case 465 -> server(p -> { AdvancedTechniques.endPerception(p); camera(154.5, -58.5, 149); });
            case 485 -> capture = "6-released.png";
            case 500 -> PreviewQuiet.quit();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if (capture == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            var out = mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("TEMPORAL_CAPTURE " + capture);
        } catch (Exception error) { throw new RuntimeException(error); }
        capture = null;
    }
}
