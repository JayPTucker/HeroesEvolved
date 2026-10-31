package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.phasing.DensityService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.speed.SpeedService;
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

/** Density's dial, passages, Reach Through, Embed, Sink, Hollow Ground and Bedrock Slam, and Superspeed's Vibrate. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class DensityPreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;
    private static final List<Entity> SPAWNED = new ArrayList<>();
    private static final double GROUND = -60;
    /** Well away from the story bunker at the world's spawn. */
    private static final int OX = 400;

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
        if (!Boolean.getBoolean("heroes.preview.density")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = false; mc.options.guiScale().set(2); mc.resizeDisplay();
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Density-" + System.currentTimeMillis(),
                    new LevelSettings("Density preview", GameType.SURVIVAL, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
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
                power(p, ModAbilities.PHASING_ID);
                at(p, .5, .5, 180, 10);
                DensityService.shift(p, false); DensityService.shift(p, false);
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            });
            case 215 -> pending = "density-1-full-ghost.png";
            case 220 -> onServer(mc, p -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                wall(p, -2, 2, 3, 4);
                at(p, .5, 1.5, 0, 15);
            });
            case 230 -> onServer(mc, p -> DensityService.pass(p, p.position(), new Vec3(.5 + OX, GROUND, 5.6)));
            case 233 -> pending = "density-2-through-the-wall.png";
            case 245 -> onServer(mc, p -> {
                DensityService.setNotch(p, DensityService.FULL_DENSE, 400);
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                at(p, 10.5, .5, 180, 20);
            });
            case 262 -> pending = "density-3-full-dense.png";
            case 270 -> onServer(mc, p -> {
                DensityService.setNotch(p, DensityService.NEUTRAL, 0);
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                at(p, 20.5, .5, 0, 30);
                var zombie = mob(p, EntityType.ZOMBIE, 20.5, 3, 180);
                zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            });
            case 280 -> onServer(mc, p -> DensityService.reach(p, 5));
            case 283 -> pending = "density-4-reach-through.png";
            case 295 -> onServer(mc, p -> {
                clear();
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                at(p, 30.5, .5, 0, 20);
                mob(p, EntityType.PILLAGER, 30.5, 4, 180);
            });
            case 305 -> onServer(mc, p -> DensityService.embed(p, 5, 6));
            case 312 -> pending = "density-5-embedded.png";
            case 320 -> onServer(mc, p -> {
                clear();
                at(p, 40.5, .5, 0, -35);
                p.setOnGround(true);
                DensityService.sinkOrSurface(p, 5);
                mob(p, EntityType.VILLAGER, 41.5, 3.5, 180);
                mob(p, EntityType.ZOMBIE, 39, 2, 180);
            });
            case 345 -> pending = "density-6-underground.png";
            case 350 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    SPAWNED.get(1).moveTo(40.5 + OX, GROUND, 1, 180, 0);
                    DensityService.sinkOrSurface(p, 5);
                });
            }
            case 353 -> pending = "density-7-surface-uppercut.png";
            case 370 -> onServer(mc, p -> {
                clear();
                at(p, 60.5, .5, 0, 35);
                for (int i = 0; i < 5; i++) {
                    double a = i * Math.PI * 2 / 5;
                    mob(p, EntityType.PILLAGER, 60.5 + Math.sin(a) * 4, .5 + Math.cos(a) * 4 + 2, 180);
                }
            });
            case 380 -> onServer(mc, p -> DensityService.ultimate(p, 5));
            case 386 -> pending = "density-8-hollow-ground.png";
            case 400 -> onServer(mc, p -> {
                clear();
                at(p, 80.5, .5, 0, 35);
                mob(p, EntityType.ZOMBIE, 80.5, 3, 180);
                DensityService.setNotch(p, DensityService.FULL_DENSE, 400);
            });
            case 410 -> onServer(mc, p -> DensityService.ultimate(p, 5));
            case 413 -> pending = "density-9-bedrock-slam.png";
            case 425 -> onServer(mc, p -> {
                clear();
                DensityService.forget(p);
                power(p, ModAbilities.SUPERSPEED_ID);
                wall(p, 99, 100, -2, 2);
                at(p, 97.3, .5, -90, 5);
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            });
            case 430 -> onServer(mc, p -> {
                // One stride at full speed, then the wall.
                SpeedService.redline(p); p.setSprinting(true);
                p.teleportTo(p.serverLevel(), 98.5 + OX, GROUND, .5, -90, 5);
                SpeedService.tickRunner(p);
                SpeedService.tickRunner(p);
            });
            case 432 -> pending = "density-10-vibrate.png";
            case 445 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("DENSITY_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
