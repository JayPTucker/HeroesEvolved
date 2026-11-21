package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.company.CompanyAgentEntity;
import com.jayptucker.heroesevolved.company.CompanyAttention;
import com.jayptucker.heroesevolved.company.CompanyOps;
import com.jayptucker.heroesevolved.company.Convoy;
import com.jayptucker.heroesevolved.company.Investigation;
import com.jayptucker.heroesevolved.company.Raid;
import com.jayptucker.heroesevolved.gifted.Renegades;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.village.Shouts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Company notices, photographed: an investigator walking into a village with his notebook, asking a villager and
 * hearing the answer, making notes; the HUD's attention ring at each band; a convoy setting out with its prisoner and
 * Suppressor, on the march, its distress flare from far off, and freed; a raid at dusk (the warning, the team coming out
 * of the dark, the Suppressor close, beaten). Run with {@code ./gradlew runClient --init-script
 * tools/company-preview.init.gradle}; shots land in {@code build/company-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class CompanyPreview {
    private static final int PHOTOS = 16;
    private static boolean started, asked, answered, noted;
    private static int ticks, age, photos;
    private static String pending;
    private static Investigation inv;
    private static Convoy convoy;
    private static Raid raid;

    private static boolean on() { return Boolean.getBoolean("heroes.preview.company"); }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try { action.accept(server.getPlayerList().getPlayer(id)); }
            catch (RuntimeException error) { PreviewQuiet.report("COMPANY_PREVIEW FAIL step at " + age + ": " + error); error.printStackTrace(); }
        });
    }

    private static void camera(Minecraft mc, Vec3 at, Vec3 look) { mc.execute(() -> TempestCamera.fix(at, look)); }

    private static void shot(String name) { pending = "company-" + name + ".png"; }

    private static void move(ServerPlayer p, Vec3 at, float yaw, float pitch) {
        p.teleportTo(p.getServer().overworld(), at.x, at.y, at.z, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) { level.setBlockAndUpdate(new BlockPos(x, y, z), state); }

    private static void hold(ServerLevel level, int x, int z) {
        for (int cx = (x - 64) >> 4; cx <= (x + 64) >> 4; cx++)
            for (int cz = (z - 64) >> 4; cz <= (z + 64) >> 4; cz++) level.setChunkForced(cx, cz, true);
    }

    /** A plank house with a door, glass windows, a slab roof, a bed and a torch, its corner at {@code hx, hz}. */
    private static void house(ServerLevel level, int hx, int hz, boolean doorSouth) {
        for (int dx = 0; dx < 5; dx++) for (int dz = 0; dz < 5; dz++) for (int y = -60; y <= -58; y++) {
            boolean wall = dx == 0 || dx == 4 || dz == 0 || dz == 4, corner = (dx == 0 || dx == 4) && (dz == 0 || dz == 4);
            BlockState s = !wall ? Blocks.AIR.defaultBlockState() : corner ? Blocks.OAK_LOG.defaultBlockState()
                    : y == -59 && (dx == 2 || dz == 2) ? Blocks.GLASS_PANE.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState();
            set(level, hx + dx, y, hz + dz, s);
        }
        int dz = doorSouth ? 4 : 0;
        Direction facing = doorSouth ? Direction.SOUTH : Direction.NORTH;
        set(level, hx + 2, -60, hz + dz, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(level, hx + 2, -59, hz + dz, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        for (int x = -1; x < 6; x++) for (int z = -1; z < 6; z++)
            set(level, hx + x, -57, hz + z, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        set(level, hx + 1, -60, hz + 1, Blocks.RED_BED.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT)
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.EAST));
        set(level, hx + 2, -60, hz + 1, Blocks.RED_BED.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD)
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.EAST));
        set(level, hx + 3, -60, hz + 3, Blocks.TORCH.defaultBlockState());
        set(level, hx + 2, -58, hz + (doorSouth ? 5 : -1), Blocks.WALL_TORCH.defaultBlockState()
                .setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, facing));
    }

    /** A little village round {@code x, z}: a square with a bell, four houses, grass and flowers, still villagers. */
    private static BlockPos village(ServerLevel level, int x, int z, int people, double spread) {
        hold(level, x, z);
        RandomSource r = RandomSource.create(x * 31L + z);
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++)
            set(level, x + dx, -61, z + dz, ((Math.abs(dx) + Math.abs(dz)) % 3 == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState());
        set(level, x, -60, z, Blocks.COBBLESTONE_WALL.defaultBlockState());
        set(level, x, -59, z, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
        int[][] houses = {{-13, -11}, {9, -11}, {-13, 9}, {9, 9}};
        for (int[] h : houses) house(level, x + h[0], z + h[1], h[1] < 0);
        for (int i = 0; i < 160; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 7 + r.nextDouble() * 30;
            int px = x + (int) Math.round(Math.cos(a) * d), pz = z + (int) Math.round(Math.sin(a) * d);
            if (!level.getBlockState(new BlockPos(px, -61, pz)).is(Blocks.GRASS_BLOCK) || !level.getBlockState(new BlockPos(px, -60, pz)).isAir()) continue;
            set(level, px, -60, pz, (r.nextInt(4) == 0 ? Blocks.POPPY : Blocks.SHORT_GRASS).defaultBlockState());
        }
        for (int i = 0; i < people; i++) {
            double a = i * Math.PI * 2 / people;
            var v = EntityType.VILLAGER.create(level);
            v.moveTo(x + 0.5 + Math.cos(a) * spread, -60, z + 0.5 + Math.sin(a) * spread, (float) Math.toDegrees(a) + 90, 0);
            v.getPersistentData().putBoolean(com.jayptucker.heroesevolved.gifted.GiftedMobs.ROLLED, true);
            v.setNoAi(true);
            level.addFreshEntity(v);
        }
        return new BlockPos(x, -59, z);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!on()) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Company-" + System.currentTimeMillis(),
                    new LevelSettings("Company preview", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(91L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (age >= 100) mc.getToasts().clear();
        ++age;
        if (age == 104) { server(mc, PrologueService::skip); return; }
        if (age == 110) {
            server(mc, p -> {
                CryoChamberService.release(p);
                p.setGameMode(GameType.SURVIVAL);
                p.removeAllEffects();
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 900, 4, false, false));
                p.getAbilities().invulnerable = true; p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
                ServerLevel level = p.getServer().overworld();
                level.setDayTime(6_000);
                level.setWeatherParameters(24000, 0, false, false);
                // Only what this preview starts: the Company never acts on its own here.
                CompanyAttention.file(p);
                move(p, new Vec3(700.5, -40, -40.5), 0, 20);
            });
            return;
        }
        if (age == 112) server(mc, p -> {
            try {
                var f = CompanyAttention.File.class.getDeclaredField("nextOp");
                f.setAccessible(true);
                f.setLong(CompanyAttention.file(p), Long.MAX_VALUE / 4);
            } catch (ReflectiveOperationException ex) { throw new RuntimeException(ex); }
        });
        if (age < 160) return;
        if (age % 20 == 0) com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
        int t = age - 160;
        if (inv != null && t < 700) watchInvestigation(mc);
        script(mc, t);
    }

    /** The investigator's beats come when they come: shoot each the first time it shows. */
    private static void watchInvestigation(Minecraft mc) {
        if (pending != null || shotLater >= 0) return;
        CompanyAgentEntity agent = inv.agent();
        if (agent == null || agent.isRemoved()) return;
        Villager asking = inv.asking();
        boolean close = asking != null && agent.distanceTo(asking) <= Investigation.TALK + 0.3;
        if (!asked && close && !Shouts.shouting(agent).isEmpty()) {
            asked = true;
            frame(mc, agent, asking);
            shotLater = 6;
            later = "03-question";
        } else if (asked && !answered && close && !Shouts.shouting(asking).isEmpty()) {
            answered = true;
            frame(mc, agent, asking);
            shotLater = 6;
            later = "04-answer";
        } else if (answered && !noted && inv.stage() == Investigation.Stage.NOTES) {
            noted = true;
            camera(mc, agent.position().add(3.2, 2.0, 2.4), agent.position().add(0, 1.4, 0));
            shotLater = 25;
            later = "05-notes";
        }
    }

    /** Side-on to the two of them, on the side away from the village's middle. */
    private static void frame(Minecraft mc, CompanyAgentEntity agent, Villager v) {
        Vec3 mid = agent.position().add(v.position()).scale(0.5);
        Vec3 across = new Vec3(-(v.getZ() - agent.getZ()), 0, v.getX() - agent.getX()).normalize();
        Vec3 bell = new Vec3(700.5, -60, 0.5);
        if (mid.add(across).distanceToSqr(bell) < mid.subtract(across).distanceToSqr(bell)) across = across.scale(-1);
        camera(mc, mid.add(across.scale(4.6)).add(0, 1.9, 0), mid.add(0, 1.7, 0));
    }

    private static int shotLater = -1;
    private static String later;

    private static void script(Minecraft mc, int t) {
        if (shotLater >= 0 && --shotLater < 0 && later != null) { shot(later); later = null; }
        switch (t) {
            // ---- Noticed: the investigator ----
            case 0 -> server(mc, p -> {
                mc.options.hideGui = true;
                ServerLevel level = p.serverLevel();
                BlockPos bell = village(level, 700, 0, 5, 5);
                p.setData(com.jayptucker.heroesevolved.data.ModDataAttachments.PLAYER_ABILITIES,
                        com.jayptucker.heroesevolved.ability.data.PlayerAbilityData.empty().assignDormant(ModAbilities.PYROKINESIS_ID).unlock(ModAbilities.PYROKINESIS_ID));
                com.jayptucker.heroesevolved.network.PlayerPowerSyncService.sync(p);
                CompanyAttention.raise(p, 32, null, "preview");
                inv = Investigation.start(level, p, new BlockPos(703, -60, 3), bell, new Vec3(684.5, -60, -10.5));
                System.out.println("COMPANY_PREVIEW investigation " + (inv != null));
            });
            case 36 -> server(mc, p -> {
                Vec3 a = inv.agent().position();
                camera(mc, a.add(2.6, 1.7, 3.2), a.add(0, 1.2, 0));
            });
            case 40 -> shot("01-walking-in");
            case 42 -> {
                mc.options.hideGui = false;
                camera(mc, new Vec3(688.5, -55, -20.5), new Vec3(700.5, -59.5, 1.5));
            }
            case 60 -> shot("02-hud-noticed");
            case 62 -> mc.options.hideGui = true;
            case 700 -> server(mc, p -> System.out.println("COMPANY_PREVIEW investigation over " + inv.over() + " stage " + inv.stage()
                    + " attention " + CompanyAttention.of(p)));
            // ---- Watched: a convoy ----
            case 705 -> server(mc, p -> {
                CompanyOps.callOffAll();
                ServerLevel level = p.serverLevel();
                BlockPos bell = village(level, 900, 0, 4, 6);
                Villager guardian = level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(10)).getFirst();
                guardian.setNoAi(false);
                Renegades.guard(guardian, ModAbilities.CRYOKINESIS_ID);
                CompanyAttention.set(p, 58);
                move(p, new Vec3(900.5, -40, -50.5), 0, 20);
                convoy = Convoy.start(level, p, bell, new Vec3(1, 0, 0.25));
                System.out.println("COMPANY_PREVIEW convoy " + (convoy != null));
            });
            case 745 -> server(mc, p -> {
                Vec3 c = convoy.center();
                camera(mc, c.add(1, 3.0, 10), c.add(1.5, 0.8, 0));
            });
            case 750 -> shot("06-convoy-sets-out");
            case 752 -> {
                mc.options.hideGui = false;
                server(mc, p -> {
                    Vec3 c = convoy.center();
                    camera(mc, c.add(-14, 6, -12), c.add(2, 0.5, 0));
                });
            }
            case 770 -> shot("07-hud-watched");
            case 772 -> mc.options.hideGui = true;
            case 960 -> server(mc, p -> {
                Vec3 c = convoy.center();
                camera(mc, c.add(9, 2.4, -6), c.add(0, 1.0, 0.5));
                System.out.println("COMPANY_PREVIEW convoy along " + convoy.along());
            });
            case 970 -> shot("08-convoy-marching");
            case 972 -> server(mc, p -> {
                move(p, new Vec3(760.5, -30, -120.5), 0, 0);
                camera(mc, new Vec3(760.5, -40, -120.5), new Vec3(900.5, -10, 0.5));
            });
            case 1010 -> shot("09-flare-from-afar");
            case 1012 -> server(mc, p -> {
                move(p, convoy.center().add(-4, 12, -14), 0, 20);
                for (CompanyAgentEntity a : convoy.agents()) a.hurt(p.damageSources().playerAttack(p), 1000);
            });
            case 1030 -> server(mc, p -> {
                Villager v = convoy.prisoner();
                camera(mc, v.position().add(2.4, 1.9, -3.0), v.position().add(0, 1.3, 0));
                System.out.println("COMPANY_PREVIEW convoy " + convoy.stage() + " attention " + CompanyAttention.of(p));
            });
            case 1040 -> shot("10-freed");
            // ---- Hunted: a raid at dusk ----
            case 1045 -> server(mc, p -> {
                ServerLevel level = p.serverLevel();
                hold(level, 1100, 0);
                house(level, 1098, -2, true);
                p.setRespawnPosition(Level.OVERWORLD, new BlockPos(1099, -60, -1), 0, true, false);
                level.setDayTime(13_400);
                CompanyAttention.set(p, 86);
                move(p, new Vec3(1100.5, -50, 8.5), 180, 10);
                raid = Raid.start(level, p, new BlockPos(1100, -60, 0), new Vec3(1072.5, -60, 22.5));
                System.out.println("COMPANY_PREVIEW raid " + (raid != null));
            });
            case 1050 -> {
                mc.options.hideGui = false;
                camera(mc, new Vec3(1106.5, -56, 10.5), new Vec3(1090.5, -59, 14.5));
            }
            case 1062 -> shot("11-raid-warning");
            case 1064 -> {
                mc.options.hideGui = true;
                server(mc, p -> raid.now());
            }
            case 1110 -> server(mc, p -> {
                Vec3 s = raid.suppressor().position();
                camera(mc, new Vec3(1101.5, -57.5, 5.5), s.add(0, 1.2, 0));
            });
            case 1116 -> shot("12-raid-coming");
            case 1160 -> server(mc, p -> {
                Vec3 s = raid.suppressor().position();
                camera(mc, s.add(3.0, 2.2, -4.2), s.add(0, 1.0, 0));
            });
            case 1166 -> shot("13-suppressor");
            case 1168 -> server(mc, p -> {
                CompanyAgentEntity aiming = null;
                for (CompanyAgentEntity g : raid.guards()) if (aiming == null && g.isAlive()) aiming = g;
                if (aiming != null) camera(mc, aiming.position().add(-2.8, 2.0, 2.6), aiming.position().add(0, 1.4, 0));
            });
            case 1178 -> shot("14-closing-in");
            case 1180 -> {
                mc.options.hideGui = false;
                camera(mc, new Vec3(1106.5, -56, 10.5), new Vec3(1090.5, -59, 14.5));
            }
            case 1186 -> shot("15-hud-hunted");
            case 1190 -> server(mc, p -> {
                for (CompanyAgentEntity a : raid.agents()) if (a.isAlive()) a.hurt(p.damageSources().playerAttack(p), 1000);
            });
            case 1206 -> {
                shot("16-raid-beaten");
                server(mc, p -> System.out.println("COMPANY_PREVIEW raid " + raid.stage() + " attention " + CompanyAttention.of(p)));
            }
            case 1220 -> {
                System.out.println("COMPANY_PREVIEW_RESULT " + (photos >= PHOTOS ? "PASS" : "FAIL") + ": " + photos + " photos");
                if (photos < PHOTOS) PreviewQuiet.report("COMPANY_PREVIEW FAIL only " + photos + " photos");
                PreviewQuiet.quit();
            }
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
            photos++;
            System.out.println("COMPANY_CAPTURE " + pending);
        } catch (Exception ex) { PreviewQuiet.report("COMPANY_PREVIEW FAIL capture " + pending + ": " + ex); }
        pending = null;
    }
}
