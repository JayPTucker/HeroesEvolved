package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.gifted.Gift;
import com.jayptucker.heroesevolved.gifted.GiftedMob;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.gifted.MobMoves;
import com.jayptucker.heroesevolved.gifted.Rarity;
import com.jayptucker.heroesevolved.gifted.Renegades;
import com.jayptucker.heroesevolved.mimicry.GiftedVillagers;
import com.jayptucker.heroesevolved.radiation.NuclearFallout;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.village.VillagerMeltdown;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
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
import java.util.function.Consumer;

/**
 * Gifted mobs and village emergencies, photographed: evil sigils (crowns) and a guardian's halo, the three cast poses with
 * the ring filling at the feet, a mini-boss going unbound, a villager turning into an illager, the distress flare from 300
 * and 600 blocks away, and a villager's meltdown blowing its crater in one tick out of solid stone (the carve is timed).
 * Staged in the overworld 150 blocks from spawn. Run with {@code tools/gifted-preview.init.gradle}; shots go to
 * {@code build/gifted-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class GiftedPreview {
    private static final int STAGE = 150;
    /** The crater block: a solid cube of stone east of the stage. */
    private static final int CRATER_X = STAGE + 170, CRATER_Z = STAGE, CRATER_HALF = 52, CRATER_TOP = -10;
    private static boolean started;
    private static int startup, age;
    private static String pending;
    private static Vec3 anchor = Vec3.ZERO;
    private static final List<Mob> ACTORS = new ArrayList<>();
    private static Mob lead;

    private static boolean on() { return Boolean.getBoolean("heroes.preview.gifted"); }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    private static ServerLevel world(ServerPlayer p) { return p.getServer().overworld(); }

    private static void log(String what) { System.out.println("GIFTED_PREVIEW " + what); }

    private static void shot(String name) { pending = "gifted-" + name + ".png"; }

    private static void force(ServerLevel world, int x0, int z0, int x1, int z1) {
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) world.setChunkForced(cx, cz, true);
    }

    private static void stage(ServerPlayer p) {
        ServerLevel world = world(p);
        force(world, STAGE - 32, STAGE - 32, STAGE + 32, STAGE + 32);
        force(world, CRATER_X - CRATER_HALF - 16, CRATER_Z - CRATER_HALF - 16, CRATER_X + CRATER_HALF + 16, CRATER_Z + CRATER_HALF + 16);
        force(world, STAGE + 300 - 16, STAGE - 16, STAGE + 300 + 16, STAGE + 16);
        force(world, STAGE + 600 - 16, STAGE - 16, STAGE + 600 + 16, STAGE + 16);
        p.teleportTo(world, STAGE + .5, -60, STAGE + .5, 0, 0);
        log("staged in " + p.level().dimension().location() + " at " + p.blockPosition() + " on " + world.getBlockState(p.blockPosition().below()));
    }

    private static java.util.UUID turnedFrom;

    private static void reset(ServerPlayer p) {
        ACTORS.forEach(m -> { Renegades.cancel(m); if (GiftedMobs.isGifted(m)) GiftedMobs.cleanse(m); m.discard(); });
        ACTORS.clear();
        // An unbound mob's followers walk on their own; they mustn't wander into the next scene.
        world(p).getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(anchor, anchor).inflate(64),
                m -> m.getPersistentData().getBoolean(GiftedMobs.FOLLOWER)).forEach(Mob::discard);
        lead = null;
        ServerLevel world = world(p);
        world.setDayTime(6000);
        p.teleportTo(world, STAGE + .5, -60, STAGE + .5, 0, 5);
        anchor = new Vec3(STAGE + .5, -60, STAGE + .5);
        Minecraft.getInstance().execute(() -> TempestCamera.fix(null, null));
    }

    private static <T extends Mob> T actor(ServerPlayer p, EntityType<T> type, double dx, double dz) {
        T mob = type.create(world(p));
        mob.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        if (mob instanceof Villager v) GiftedVillagers.give(v, null);
        mob.setNoAi(true);
        float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        mob.moveTo(anchor.x + dx, -60, anchor.z + dz, yaw, 0);
        mob.setYHeadRot(yaw);
        mob.yBodyRot = yaw;
        mob.finalizeSpawn(world(p), world(p).getCurrentDifficultyAt(mob.blockPosition()), net.minecraft.world.entity.MobSpawnType.COMMAND, null);
        world(p).addFreshEntity(mob);
        ACTORS.add(mob);
        return mob;
    }

    private static GiftedMob gift(Mob mob, ResourceLocation power, Rarity rarity, boolean evil) {
        GiftedMob g = GiftedMobs.bestow(mob, power, rarity, evil, Gift.Origin.ADMIN, null, null);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300);
        mob.setHealth(mob.getMaxHealth());
        return g;
    }

    /** Creative flight stops on landing (each reset stands the player on the ground): start it again where they are. */
    private static void hover(ServerPlayer p) {
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
        p.setDeltaMovement(Vec3.ZERO);
    }

    /** A still camera {@code dx, dy, dz} from the anchor, looking at {@code lx, ly, lz} from it. */
    private static void side(double dx, double dy, double dz, double lx, double ly, double lz) {
        Minecraft mc = Minecraft.getInstance();
        mc.getSingleplayerServer().execute(() -> {
            Vec3 at = anchor.add(dx, dy, dz), target = anchor.add(lx, ly, lz);
            mc.execute(() -> TempestCamera.fix(at, target));
        });
    }

    private static void cast(Minecraft mc, String move) {
        server(mc, p -> {
            if (lead == null || GiftedMobs.of(lead) == null) return;
            GiftedMobs.of(lead).start(MobMoves.byId(move), p);
            log("cast " + move);
        });
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!on()) return;
        var mc = Minecraft.getInstance();
        if (!started && ++startup >= 35 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.fov().set(78);
            mc.options.renderDistance().set(8);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Gifted-" + System.currentTimeMillis(),
                    new LevelSettings("Gifted preview", GameType.CREATIVE, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (age >= 100) mc.getToasts().clear();
        ++age;
        if (age == 104) { server(mc, com.jayptucker.heroesevolved.story.prologue.PrologueService::skip); return; }
        if (age == 110) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
            server(mc, p -> {
                CryoChamberService.release(p);
                p.setGameMode(GameType.CREATIVE);
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                stage(p);
            });
            return;
        }
        if (age < 170) return;
        if (age % 20 == 0) com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
        script(mc, age - 170);
    }

    private static void script(Minecraft mc, int t) {
        switch (t) {
            // ---- The sigils: three evil gifts of three rarities, crowns over their heads ----
            case 0 -> server(mc, p -> {
                reset(p);
                gift(actor(p, EntityType.PILLAGER, -3, 6), ModAbilities.PYROKINESIS_ID, Rarity.EPIC, true);
                gift(actor(p, EntityType.VINDICATOR, 0, 7), ModAbilities.ELECTROKINESIS_ID, Rarity.RARE, true);
                gift(actor(p, EntityType.ILLUSIONER, 3, 6), ModAbilities.TELEPATHY_ID, Rarity.MYTHIC, true);
            });
            case 4 -> { mc.options.hideGui = true; side(7, 2.4, 2.5, 0, 2.2, 6.5); }
            case 30 -> shot("1-crowns-side");
            case 32 -> side(-1.2, 2.9, 4, -3, 2.6, 6);
            case 44 -> shot("1-crown-close");
            case 46 -> side(0, 3, 1, 0, 2.2, 7);
            case 58 -> shot("1-crowns-front");
            // ---- A guardian: a villager's halo, fighting a monster ----
            case 60 -> server(mc, p -> {
                reset(p);
                lead = actor(p, EntityType.VILLAGER, -1.5, 5);
                gift(lead, ModAbilities.HYDROKINESIS_ID, Rarity.MYTHIC, false);
                gift(actor(p, EntityType.ZOMBIE, 2.5, 8), ModAbilities.QUAKE_ID, Rarity.UNCOMMON, true);
            });
            case 64 -> side(5.5, 2.4, 3, 0.5, 1.8, 6.5);
            case 80 -> shot("2-guardian-a");
            case 96 -> shot("2-guardian-b");
            case 98 -> side(-2.5, 2.6, 3.5, -1.5, 2.3, 5);
            case 110 -> shot("2-halo-close");
            // ---- Casting: the ring fills at the feet; the illager raises its arms ----
            case 120 -> server(mc, p -> {
                reset(p);
                lead = actor(p, EntityType.PILLAGER, 0, 5);
                gift(lead, ModAbilities.PYROKINESIS_ID, Rarity.EPIC, true);
            });
            case 124 -> side(4.5, 1.7, 2.5, 0, 1.3, 5);
            case 128 -> cast(mc, "firestorm");
            case 138 -> shot("3-cast-a");
            case 150 -> shot("3-cast-b");
            case 158 -> shot("3-cast-land");
            case 170 -> cast(mc, "gamma_lance");
            case 178 -> shot("3-point");
            case 196 -> cast(mc, "ground_pound");
            case 208 -> shot("3-slam");
            // ---- Unbound at half health ----
            case 230 -> server(mc, p -> {
                reset(p);
                lead = actor(p, EntityType.PILLAGER, 0, 6);
                gift(lead, ModAbilities.QUAKE_ID, Rarity.EPIC, true);
            });
            case 234 -> side(7, 3, 1, 0, 1.5, 6);
            case 244 -> server(mc, p -> { lead.setHealth(lead.getMaxHealth() * .4F); log("unbinding"); });
            case 248 -> shot("4-unbound-a");
            case 253 -> shot("4-unbound-b");
            case 262 -> shot("4-unbound-c");
            case 290 -> shot("4-unbound-after");
            // ---- A villager turns ----
            case 300 -> server(mc, p -> {
                reset(p);
                lead = actor(p, EntityType.VILLAGER, 0, 6);
                GiftedVillagers.give((Villager) lead, ModAbilities.PYROKINESIS_ID);
                actor(p, EntityType.VILLAGER, 4, 9);
                actor(p, EntityType.VILLAGER, -4, 9);
            });
            case 304 -> side(5, 2.2, 2.5, 0, 1.4, 6);
            case 310 -> server(mc, p -> { turnedFrom = lead.getUUID(); log("turning " + (Renegades.awaken((Villager) lead, true) != null)); });
            case 316 -> shot("5-turn-a");
            case 324 -> shot("5-turn-b");
            case 330 -> shot("5-turn-flash");
            case 331 -> server(mc, p -> {
                Mob renegade = Renegades.turnedFrom(turnedFrom);
                log("turned into " + (renegade == null ? null : renegade.getType()) + " " + (renegade == null ? "" : Gift.read(renegade)));
                if (renegade != null) { renegade.setNoAi(true); ACTORS.add(renegade); lead = renegade; }
            });
            case 338 -> shot("5-turn-c");
            case 350 -> shot("5-turn-d");
            case 370 -> { mc.options.hideGui = false; }
            case 374 -> shot("5-renegade-hud");
            case 376 -> { mc.options.hideGui = true; }
            // ---- The flare, from 300 and 600 blocks away (the renegade's call is still going) ----
            case 380 -> {
                mc.execute(() -> TempestCamera.fix(null, null));
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(mc, p -> { p.teleportTo(world(p), STAGE + 300.5, -40, STAGE + .5, 90, -12); hover(p); });
            }
            case 384 -> { mc.player.setYRot(90); mc.player.setXRot(-12); }
            case 450 -> shot("6-flare-300");
            case 452 -> server(mc, p -> { p.teleportTo(world(p), STAGE + 600.5, -40, STAGE + .5, 90, -8); hover(p); });
            case 456 -> { mc.player.setYRot(90); mc.player.setXRot(-8); }
            case 520 -> shot("6-flare-600");
            case 522 -> { mc.options.hideGui = false; }
            case 524 -> shot("6-flare-600-hud");
            case 526 -> { mc.options.hideGui = true; }
            // ---- A meltdown out of solid stone: the crater in one tick ----
            case 530 -> server(mc, p -> {
                reset(p);
                Renegades.cancelAll();
                ServerLevel world = world(p);
                long began = System.nanoTime();
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                for (int x = -CRATER_HALF; x <= CRATER_HALF; x++)
                    for (int z = -CRATER_HALF; z <= CRATER_HALF; z++)
                        for (int y = -63; y <= CRATER_TOP; y++)
                            world.setBlock(pos.set(CRATER_X + x, y, CRATER_Z + z), (y + x + z) % 11 == 0 ? Blocks.ANDESITE.defaultBlockState()
                                    : Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                log("crater block built in " + (System.nanoTime() - began) / 1_000_000 + " ms");
            });
            case 560 -> server(mc, p -> {
                Villager v = EntityType.VILLAGER.create(world(p));
                v.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
                GiftedVillagers.give(v, null);
                v.moveTo(CRATER_X + .5, CRATER_TOP + 1, CRATER_Z + .5, 180, 0);
                world(p).addFreshEntity(v);
                log("meltdown " + (VillagerMeltdown.start(v, 80) != null));
                p.teleportTo(world(p), CRATER_X - 120.5, CRATER_TOP + 50, CRATER_Z + .5, -90, 20);
                hover(p);
            });
            case 564 -> { mc.player.setYRot(-90); mc.player.setXRot(20); }
            case 600 -> shot("7-meltdown-building");
            case 642 -> shot("7-blast-a");
            case 652 -> shot("7-blast-b");
            case 670 -> server(mc, p -> log("crater carved " + NuclearFallout.lastCarveBlocks() + " blocks in "
                    + String.format("%.1f", NuclearFallout.lastCarveMillis()) + " ms"));
            // From high off to one side, under the cloud's cap, looking down into the bowl.
            case 720 -> server(mc, p -> { p.teleportTo(world(p), CRATER_X - 70.5, CRATER_TOP + 60, CRATER_Z - 70.5, -45, 36); hover(p); });
            case 724 -> { mc.player.setYRot(-45); mc.player.setXRot(36); }
            case 760 -> shot("7-crater");
            case 780 -> PreviewQuiet.quit();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null || !on()) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
            log("capture " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
