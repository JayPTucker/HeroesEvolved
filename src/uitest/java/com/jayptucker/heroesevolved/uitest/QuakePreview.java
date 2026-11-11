package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.quake.QuakeKit;
import com.jayptucker.heroesevolved.quake.QuakeService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Quake: Shockwave rings, Resonate, Fault Line, Strain and the fracture, heartbeats, Quake Blast flight,
 * Quake Blast grinding through a stone wall, and an Earthquake's fissures.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class QuakePreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;
    private static final List<Entity> SPAWNED = new ArrayList<>();
    private static final double GROUND = -60;
    /** Well away from the story bunker at the world's spawn. */
    private static final int OX = 500;

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) action.accept(player);
        });
    }

    private static void at(ServerPlayer p, double x, double z, float yaw, float pitch) {
        p.teleportTo(p.serverLevel(), x + OX, GROUND, z, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO);
    }

    private static <T extends Mob> T mob(ServerPlayer p, EntityType<T> type, double x, double z, float yaw) {
        T mob = type.create(p.serverLevel());
        mob.setNoAi(true); mob.setPersistenceRequired();
        mob.moveTo(x + OX, GROUND, z, yaw, 0);
        p.serverLevel().addFreshEntity(mob);
        SPAWNED.add(mob);
        return mob;
    }

    private static void clear() { SPAWNED.forEach(Entity::discard); SPAWNED.clear(); }

    private static void wall(ServerPlayer p, int x0, int x1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = 0; y < 3; y++)
            p.serverLevel().setBlock(new BlockPos(x + OX, (int) GROUND + y, z), Blocks.STONE_BRICKS.defaultBlockState(), 3);
    }

    private static void power(ServerPlayer p, ResourceLocation id) {
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(id).unlock(id));
        PlayerProgressionService.setLevel(p, 5);
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(id));
        PlayerEnergyService.restore(p, 999);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.quake")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = false; mc.options.guiScale().set(2); mc.resizeDisplay();
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Quake-" + System.currentTimeMillis(),
                    new LevelSettings("Quake preview", GameType.SURVIVAL, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        worldTicks++;
        switch (worldTicks) {
            case 200 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(6000);
                p.getAbilities().invulnerable = true; p.onUpdateAbilities();
                power(p, ModAbilities.QUAKE_ID);
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                at(p, .5, .5, 0, 20);
                for (int i = 0; i < 3; i++) mob(p, EntityType.PILLAGER, -1 + i, 7 + i * 2, 180);
            });
            case 212 -> onServer(mc, p -> QuakeService.shockwave(p, 5));
            case 216 -> pending = "quake-1-shockwave.png";
            case 230 -> onServer(mc, p -> {
                clear();
                at(p, 20.5, .5, 0, 12);
                var pillager = mob(p, EntityType.PILLAGER, 20.5, 5, 180);
                pillager.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            });
            case 240 -> onServer(mc, p -> QuakeKit.resonate(p, 5));
            case 252 -> pending = "quake-2-resonate.png";
            case 270 -> onServer(mc, p -> {
                clear();
                at(p, 40.5, .5, 0, 25);
                mob(p, EntityType.PILLAGER, 40.5, 5, 180);
                mob(p, EntityType.PILLAGER, 41, 9, 180);
                mob(p, EntityType.PILLAGER, 40, 13, 180);
            });
            case 280 -> onServer(mc, p -> { p.setOnGround(true); QuakeKit.faultLine(p, 5); });
            case 285 -> pending = "quake-3-fault-line-racing.png";
            case 294 -> pending = "quake-4-fault-line-erupting.png";
            case 310 -> onServer(mc, p -> {
                clear();
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                at(p, 60.5, .5, 180, 10);
                QuakeKit.setStrain(p, 92);
            });
            case 326 -> pending = "quake-5-strain-breaking.png";
            case 330 -> onServer(mc, p -> { p.setXRot(0); p.setYRot(0); QuakeService.shockwave(p, 5); });
            case 334 -> pending = "quake-6-fracture.png";
            case 350 -> onServer(mc, p -> {
                // A new body: the fracture healed, rested, then a crouch to listen.
                p.removeAllEffects();
                QuakeKit.forget(p);
                power(p, ModAbilities.QUAKE_ID);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                at(p, 80.5, .5, 0, 5);
                wall(p, 78, 83, 3, 3);
                mob(p, EntityType.VILLAGER, 80.5, 6, 180);
                mob(p, EntityType.PILLAGER, 82.5, 9, 180);
                mob(p, EntityType.SKELETON, 78.5, 8, 180);
            });
            case 355 -> mc.options.keyShift.setDown(true);
            case 384 -> pending = "quake-7-tremor-sense.png";
            case 388 -> mc.options.keyShift.setDown(false);
            case 395 -> onServer(mc, p -> {
                clear();
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                p.teleportTo(p.serverLevel(), 100.5 + OX, GROUND + 6, .5, 0, 60);
                QuakeService.startQuakeBlast(p, 5);
            });
            case 410 -> pending = "quake-8-quake-blast-flight.png";
            case 415 -> onServer(mc, p -> QuakeService.stopQuakeBlast(p, false));
            // Held on a stone wall, the beam cracks it; let go early and the cracks fade with nothing broken.
            case 430 -> onServer(mc, p -> {
                QuakeKit.forget(p);
                power(p, ModAbilities.QUAKE_ID);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                at(p, 120.5, .5, 0, 0);
                for (int x = 117; x <= 124; x++) for (int y = 0; y < 5; y++) for (int z = 4; z <= 7; z++)
                    p.serverLevel().setBlock(new BlockPos(x + OX, (int) GROUND + y, z), Blocks.STONE.defaultBlockState(), 3);
            });
            // A moment later, once she is standing on the ground (aimed level, the beam then braces her).
            case 440 -> onServer(mc, p -> QuakeService.startQuakeBlast(p, 5));
            case 470 -> pending = "quake-9-blast-cracking.png";
            case 500 -> pending = "quake-10-blast-cracked.png";
            case 502 -> onServer(mc, p -> QuakeService.stopQuakeBlast(p, false));
            case 514 -> pending = "quake-11-released-fading.png";
            case 548 -> pending = "quake-12-faded.png";
            // Held again, and long enough: the stone breaks, and the hole slowly widens.
            case 550 -> onServer(mc, p -> QuakeService.startQuakeBlast(p, 5));
            case 640 -> pending = "quake-13-blast-broken.png";
            case 720 -> pending = "quake-14-blast-widening.png";
            case 725 -> onServer(mc, p -> QuakeService.stopQuakeBlast(p, false));
            // Earthquake: three fissures crack outward, and the grass along them gives way.
            case 740 -> onServer(mc, p -> {
                QuakeKit.forget(p);
                power(p, ModAbilities.QUAKE_ID);
                at(p, 160.5, .5, 0, 0);
            });
            case 750 -> onServer(mc, p -> {
                QuakeService.startEarthquake(p, 5);
                // Hover above the epicenter to watch.
                p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
                p.teleportTo(p.serverLevel(), 160.5 + OX, GROUND + 13, -7.5, 0, 58);
            });
            case 800 -> pending = "quake-15-earthquake-cracking.png";
            case 895 -> pending = "quake-16-earthquake-fissures.png";
            case 940 -> pending = "quake-17-earthquake-after.png";
            case 955 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("QUAKE_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
