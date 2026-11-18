package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.ClientProjectileTime;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.expansion.AdvancedTechniques;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.time.TemporalMobTime;
import com.jayptucker.heroesevolved.time.TemporalSharing;
import com.jayptucker.heroesevolved.time.TimeSlowService;
import com.jayptucker.heroesevolved.time.TimeStopService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Projectile;
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
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.nio.file.Files;
import java.util.function.Consumer;

/** Real server/client suspension, right-click catch packet and villager sharing, all staged in one loaded area. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class TemporalInteractionPreview {
    private static final double FLOOR = -60, Z = 150.5;
    private static boolean started;
    private static int startup, age;
    private static String capture;
    private static Projectile caught;
    private static Projectile extra;
    private static Villager villager;

    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance(); var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    private static void power(ServerPlayer player, ResourceLocation power) {
        player.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        PlayerProgressionService.setLevel(player, 5); PlayerPowerSyncService.sync(player);
    }

    private static Projectile bullet(ServerPlayer p, double x, double y, double z, boolean owned) {
        var shot = (Projectile) BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("tacz", "bullet")).create(p.level());
        shot.setPos(x, y, z); shot.setDeltaMovement(60, 0, 0);
        if (owned) shot.setOwner(p);
        p.serverLevel().addFreshEntity(shot);
        return shot;
    }

    private static void stage(ServerPlayer player) {
        PrologueService.skip(player); CryoChamberService.release(player);
        var world = player.getServer().overworld();
        for (int x = 8; x <= 10; x++) for (int z = 8; z <= 10; z++) world.setChunkForced(x, z, true);
        player.teleportTo(world, 150.5, FLOOR, Z, 0, 0);
        power(player, ModAbilities.TIME_MANIPULATION_ID);
        PlayerEnergyService.setUnlimitedEnergy(player, true);
        player.getAbilities().invulnerable = true; player.onUpdateAbilities();
        world.setDayTime(6000); world.setWeatherParameters(12000, 0, false, false);
        System.out.println("TEMPORAL_PREVIEW staged " + world.dimension().location() + " on " + world.getBlockState(player.blockPosition().below()));
    }

    private static void camera(Vec3 at, Vec3 toward) { TempestCamera.fix(at, toward); }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.temporalinteraction")) return;
        var mc = Minecraft.getInstance();
        if (!started && ++startup > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true; mc.options.pauseOnLostFocus = false;
            var rules = new GameRules(); rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("TemporalInteractions-" + System.currentTimeMillis(),
                    new LevelSettings("Temporal interactions", GameType.SURVIVAL, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(91L, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                            .getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        age++;
        // Each shot is selected through the same real client input and network packet used in play.
        if (age >= 280 && age < 412) {
            int index = (age - 280) / 12, phase = (age - 280) % 12;
            if (phase == 0) server(p -> {
                extra = switch (index) {
                    case 0 -> EntityType.ARROW.create(p.level());
                    case 1 -> EntityType.TRIDENT.create(p.level());
                    case 2 -> EntityType.SNOWBALL.create(p.level());
                    case 3 -> EntityType.POTION.create(p.level());
                    case 4 -> EntityType.SMALL_FIREBALL.create(p.level());
                    case 5 -> EntityType.WITHER_SKULL.create(p.level());
                    case 6 -> EntityType.WIND_CHARGE.create(p.level());
                    case 7 -> new net.minecraft.world.entity.projectile.FireworkRocketEntity(p.level(), 0, 0, 0,
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET));
                    case 8 -> EntityType.EGG.create(p.level());
                    case 9 -> EntityType.SHULKER_BULLET.create(p.level());
                    default -> com.jayptucker.heroesevolved.registry.ModEntities.EMP_GRENADE.get().create(p.level());
                };
                extra.setOwner(p); extra.setPos(p.getEyePosition().add(0, 0, 3)); extra.setDeltaMovement(0, 0, -1);
                p.serverLevel().addFreshEntity(extra);
            });
            if (phase == 4) NeoForge.EVENT_BUS.post(new InputEvent.InteractionKeyMappingTriggered(1, mc.options.keyUse, InteractionHand.MAIN_HAND));
            if (phase == 10) server(p -> {
                if (!extra.isRemoved()) throw new IllegalStateException("Client failed to catch " + extra.getType());
                System.out.println("TEMPORAL_PREVIEW real client caught " + BuiltInRegistries.ENTITY_TYPE.getKey(extra.getType()));
            });
        }
        switch (age) {
            case 160 -> server(TemporalInteractionPreview::stage);
            case 200 -> {
                mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(p -> {
                    TimeSlowService.hold(p, 1);
                    bullet(p, 144, FLOOR + 1.6, Z + 4, true);
                    bullet(p, 145, FLOOR + 1.9, Z + 5.1, false);
                });
                camera(new Vec3(151, FLOOR + 2.7, Z - 2), new Vec3(151, FLOOR + 1.7, Z + 4.5));
            }
            case 216 -> capture = "01-slowed-rounds.png";
            case 218 -> server(p -> {
                TimeStopService.start(p, 5); TimeSlowService.stop(p);
                caught = bullet(p, 150.5, FLOOR + 1.65, Z + 6, true);
            });
            case 232 -> capture = "02-stopped-wakes.png";
            case 236 -> camera(new Vec3(153, FLOOR + 2.2, Z + 2), new Vec3(150.5, FLOOR + 1.65, Z + 6));
            case 244 -> capture = "03-stopped-round-close.png";
            case 248 -> {
                camera(null, null); mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(p -> {
                    p.serverLevel().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(30), s -> s != caught).forEach(Projectile::discard);
                    p.connection.teleport(150.5, FLOOR, Z + 2, 0, 0);
                });
            }
            case 258 -> capture = "04-before-catch.png";
            case 260 -> {
                ClientProjectileTime.forEachShot(s -> System.out.println("TEMPORAL_PREVIEW client shot=" + s.getId()
                        + " wanted=" + caught.getId() + " distance=" + com.jayptucker.heroesevolved.time.ProjectileCatchGeometry.hitDistanceSquared(mc.player, s, true)));
                var click = new InputEvent.InteractionKeyMappingTriggered(1, mc.options.keyUse, InteractionHand.MAIN_HAND);
                NeoForge.EVENT_BUS.post(click);
                System.out.println("TEMPORAL_PREVIEW client catch enabled=" + ClientProjectileTime.canCatch() + " consumed=" + click.isCanceled());
            }
            case 270 -> server(p -> {
                if (!caught.isRemoved()) throw new IllegalStateException("Client right-click failed: canAct="
                        + com.jayptucker.heroesevolved.ability.service.PowerRules.canAct(p) + " canCatch="
                        + com.jayptucker.heroesevolved.time.ProjectileTimeService.canCatch(p, caught) + " aim="
                        + com.jayptucker.heroesevolved.time.ProjectileCatchGeometry.hitDistanceSquared(p, caught, true)
                        + " pos=" + p.position());
                System.out.println("TEMPORAL_PREVIEW caught owned round through real client input and packet");
            });
            case 272 -> capture = "05-after-catch.png";
            case 420 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(p -> {
                    TimeStopService.stop(p); power(p, ModAbilities.SUPERSPEED_ID);
                    p.connection.teleport(150.5, FLOOR, Z + 2, 0, 0);
                    AdvancedTechniques.startPerception(p);
                    villager = EntityType.VILLAGER.create(p.level());
                    villager.getPersistentData().putBoolean(com.jayptucker.heroesevolved.gifted.GiftedMobs.ROLLED, true);
                    villager.moveTo(152.5, FLOOR, Z + 2, 110, 0); villager.setPersistenceRequired();
                    p.serverLevel().addFreshEntity(villager);
                    NeoForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(p, InteractionHand.MAIN_HAND, villager));
                    if (!TemporalSharing.shared(villager) || TemporalMobTime.interval(villager) != 1)
                        throw new IllegalStateException("Villager sharing failed");
                    System.out.println("TEMPORAL_PREVIEW villager shared interval=" + TemporalMobTime.interval(villager));
                });
                camera(new Vec3(155, FLOOR + 2.8, Z - 3), new Vec3(151.5, FLOOR + 1, Z + 2));
            }
            case 440 -> capture = "06-villager-sharing.png";
            case 452 -> server(p -> {
                AdvancedTechniques.endPerception(p);
                if (TemporalSharing.shared(villager)) throw new IllegalStateException("Villager invitation leaked");
                System.out.println("TEMPORAL_PREVIEW villager released");
            });
            case 466 -> PreviewQuiet.quit();
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
