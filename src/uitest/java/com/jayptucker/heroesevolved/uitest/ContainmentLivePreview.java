package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.hazard.SuitItems;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.speed.SpeedContainment;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.village.VillagerMeltdown;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A live run of the meltdown with a speedster on the ring, in the real client (flag {@code heroes.preview.liverun}):
 * three villages, each with a villager melting down and a speedster player who presses the real Cyclone key:
 * tier 5 in the velocity suit, tier 3 in the suit, tier 4 in the suit but spent (their body gives out and they run on
 * until the hold is done), and tier 4 with no suit on a short fuse (they burn away and he goes off). Photographs each as it happens into {@code build/live-run-preview/screenshots/} and logs {@code LIVE_RESULT}
 * lines with the outcome.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class ContainmentLivePreview {
    private static final int STAGE = 150;
    private static boolean started;
    private static int startup, age;
    private static String capture;
    private static final List<ServerPlayer> STANDINS = new ArrayList<>();
    private static Villager him;
    private static ServerPlayer runner;
    private static String scene = "";

    private static boolean on() { return Boolean.getBoolean("heroes.preview.liverun"); }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    private static ServerLevel world(ServerPlayer p) { return p.getServer().overworld(); }

    private static void log(String what) { System.out.println("LIVE_RUN " + what); }

    @SuppressWarnings("unchecked") private static <T> T field(PlayerList list, String name) {
        try { Field f = PlayerList.class.getDeclaredField(name); f.setAccessible(true); return (T) f.get(list); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    /** A speedster player at {@code tier}, seen by the client as an ordinary player, wearing the whole suit or none. */
    private static ServerPlayer speedster(ServerPlayer you, int tier, boolean suited, double x, double z) {
        ServerPlayer p = new FakePlayer(world(you), new GameProfile(UUID.randomUUID(), "Runner" + tier)) {
            // As a real player: hurt by anything, except while the run has made them untouchable (burning away, doomed).
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource damage) {
                return isInvulnerable() && !damage.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
            }
        };
        try {
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true); grace.setInt(p, 0);
            var connection = p.connection.getConnection();
            Field channel = net.minecraft.network.Connection.class.getDeclaredField("channel");
            channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new io.netty.channel.embedded.EmbeddedChannel());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.moveTo(x, -60, z, 0, 0);
        p.setGameMode(GameType.SURVIVAL);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.SUPERSPEED_ID).unlock(ModAbilities.SUPERSPEED_ID));
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.SUPERSPEED_ID));
        PlayerProgressionService.setLevel(p, tier);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        if (suited) {
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(SuitItems.VELOCITY_COWL.get()));
            p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(SuitItems.VELOCITY_SUIT.get()));
            p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(SuitItems.VELOCITY_LEGGINGS.get()));
            p.setItemSlot(EquipmentSlot.FEET, new ItemStack(SuitItems.VELOCITY_BOOTS.get()));
        }
        PlayerList list = you.getServer().getPlayerList();
        ContainmentLivePreview.<List<ServerPlayer>>field(list, "players").add(p);
        ContainmentLivePreview.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").put(p.getUUID(), p);
        you.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(p)));
        world(you).addNewPlayer(p);
        STANDINS.add(p);
        return p;
    }

    private static void dropAll() {
        for (ServerPlayer p : STANDINS) {
            p.setInvulnerable(false);
            PlayerList list = p.getServer().getPlayerList();
            ContainmentLivePreview.<List<ServerPlayer>>field(list, "players").remove(p);
            ContainmentLivePreview.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").remove(p.getUUID());
            if (!p.isRemoved()) p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
        }
        STANDINS.clear();
    }

    /** A cobbled square with a bell, a few stalls and villagers, and the villager the Eclipse woke beside the bell. */
    private static void village(ServerPlayer p, int x, int z, int fuse) {
        ServerLevel level = world(p);
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++)
            level.setBlockAndUpdate(new BlockPos(x + dx, -61, z + dz), ((Math.abs(dx) + Math.abs(dz)) % 3 == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(x, -60, z), Blocks.COBBLESTONE_WALL.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(x, -59, z), Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
        for (int[] s : new int[][] {{-5, -3}, {5, -3}, {-5, 4}, {5, 4}}) {
            level.setBlockAndUpdate(new BlockPos(x + s[0], -60, z + s[1]), Blocks.HAY_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(x + s[0], -60, z + s[1] + 1), Blocks.BARREL.defaultBlockState());
        }
        for (double[] s : new double[][] {{-3, 2}, {3, 3}, {-2, -4}, {4, -2}}) {
            Villager v = EntityType.VILLAGER.create(level);
            v.moveTo(x + 0.5 + s[0], -60, z + 0.5 + s[1], 0, 0);
            level.addFreshEntity(v);
        }
        him = EntityType.VILLAGER.create(level);
        him.setVillagerData(him.getVillagerData().setProfession(VillagerProfession.FARMER));
        him.moveTo(x + 0.5, -60, z - 1.5, 180, 0);
        level.addFreshEntity(him);
        var m = VillagerMeltdown.start(him, fuse);
        log("START " + scene + " village=" + (m == null ? 0 : m.villageSize()) + " safe=" + (m == null ? null : m.safe()));
    }

    /** The watcher (invulnerable, so the meltdown leaves them be) a little off to the side, looking at him. */
    private static void watch(ServerPlayer p, int x, int z) {
        p.teleportTo(world(p), x + 13.5, -55, z + 9.5, 0, 0);
        p.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(x + 0.5, -59.5, z - 1.5));
    }

    private static void result(ServerPlayer you) {
        log("RESULT " + scene + " villagerAlive=" + (him != null && him.isAlive()) + " stillMeltingDown=" + (him != null && VillagerMeltdown.critical(him))
                + " runnerAlive=" + runner.isAlive() + " runnerHealth=" + runner.getHealth() + "/" + runner.getMaxHealth()
                + " runnerStamina=" + PlayerEnergyService.getEnergy(runner) + "/" + PlayerEnergyService.getMaximumEnergy(runner)
                + " burningAway=" + SpeedContainment.burningAway(runner) + " running=" + SpeedContainment.running(runner));
    }

    /** One scene: a village at (x, z), a runner at {@code tier}, suited or not, and the key pressed. */
    private static void begin(Minecraft mc, String name, int x, int z, int tier, boolean suited, int fuse) {
        scene = name;
        server(mc, p -> {
            dropAll();
            village(p, x, z, fuse);
            watch(p, x, z);
            runner = speedster(p, tier, suited, x + 4.5, z - 1.5);
        });
    }

    private static void press(Minecraft mc) {
        server(mc, p -> log("KEY " + scene + " " + AbilityUseService.activateAssignedAction(runner, AbilitySlot.QUATERNARY, false)
                + " running=" + SpeedContainment.running(runner)));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!on()) return;
        var mc = Minecraft.getInstance();
        if (!started && ++startup >= 35 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.fov().set(70);
            mc.options.renderDistance().set(6);
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("LiveRun-" + System.currentTimeMillis(),
                    new LevelSettings("Live Run", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(59L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (age >= 100) mc.getToasts().clear();
        ++age;
        if (age == 104) { server(mc, com.jayptucker.heroesevolved.story.prologue.PrologueService::skip); return; }
        if (age == 110) {
            server(mc, p -> {
                CryoChamberService.release(p);
                var world = world(p);
                for (int cx = (STAGE - 48) >> 4; cx <= (STAGE + 270) >> 4; cx++)
                    for (int cz = (STAGE - 64) >> 4; cz <= (STAGE + 64) >> 4; cz++) world.setChunkForced(cx, cz, true);
                p.setGameMode(GameType.SURVIVAL);
                p.getAbilities().invulnerable = true; p.getAbilities().mayfly = true; p.getAbilities().flying = true;
                p.onUpdateAbilities();
                p.teleportTo(world, STAGE + .5, -55, STAGE + .5, 0, 0);
                world.setDayTime(6000);
                log("staged in " + p.level().dimension().location() + " on " + world.getBlockState(BlockPos.containing(STAGE, -61, STAGE)));
            });
            return;
        }
        if (age < 170) return;
        if (age % 20 == 0) com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
        script(mc, age - 170);
    }

    private static void script(Minecraft mc, int t) {
        // Scene 1: tier 5 in the velocity suit holds him in for ten seconds, building to the moment it breaks.
        if (t == 10) begin(mc, "tier5-suited", STAGE, STAGE, 5, true, 900);
        if (t == 40) press(mc);
        if (t == 60 || t == 120 || t == 180 || t == 215 || t == 232 || t == 244) capture = String.format("1-tier5-suited-%03d.png", t);
        if (t == 260) server(mc, ContainmentLivePreview::result);
        // Scene 2: tier 3 (the first tier with the Cyclone key) in the suit.
        if (t == 280) begin(mc, "tier3-suited", STAGE + 70, STAGE, 3, true, 900);
        if (t == 310) press(mc);
        if (t == 400 || t == 480 || t == 505) capture = String.format("2-tier3-suited-%03d.png", t);
        if (t == 530) server(mc, ContainmentLivePreview::result);
        // Scene 3: tier 4 in the suit, spent: their body gives out, they run on until it's done, then it takes them.
        if (t == 550) begin(mc, "tier4-spent", STAGE + 140, STAGE, 4, true, 900);
        if (t == 580) press(mc);
        if (t == 582) server(mc, p -> { PlayerEnergyService.tryConsume(runner, PlayerEnergyService.getEnergy(runner)); runner.setHealth(3); });
        if (t == 640 || t == 700) server(mc, p -> log("DOOMED " + scene + " t=" + (t - 580) + " " + SpeedContainment.doomed(runner)
                + " stillMeltingDown=" + VillagerMeltdown.critical(him)));
        if (t == 660 || t == 740 || t == 770 || t == 786 || t == 800 || t == 830) capture = String.format("3-tier4-spent-%03d.png", t);
        if (t == 790) server(mc, ContainmentLivePreview::result);
        if (t == 900) server(mc, ContainmentLivePreview::result);
        // Scene 4: tier 4 with no suit, on a short fuse: they burn up for three and a half seconds and burn away, and he
        // still goes off.
        if (t == 920) begin(mc, "tier4-unsuited", STAGE + 210, STAGE, 4, false, 300);
        if (t == 950) press(mc);
        if (t >= 956 && t <= 1040 && t % 12 == 8) capture = String.format("4-tier4-unsuited-%04d.png", t);
        if (t == 1050) server(mc, ContainmentLivePreview::result);
        if (t == 1070) server(mc, p -> p.teleportTo(world(p), STAGE + 210.5, -40, STAGE + 45.5, 180, 18));
        if (t >= 1210 && t <= 1270 && t % 10 == 0) capture = String.format("5-blast-%04d.png", t);
        if (t == 1275) server(mc, p -> { result(p); log("DONE"); dropAll(); });
        if (t == 1290) PreviewQuiet.quit();
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (capture == null || !on()) return;
        Minecraft mc = Minecraft.getInstance();
        Path path = mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Files.createDirectories(path.getParent());
            image.writeToFile(path);
            log("CAPTURE " + capture);
        } catch (Exception e) {
            log("CAPTURE FAILED " + capture + " " + e);
        }
        capture = null;
    }
}
