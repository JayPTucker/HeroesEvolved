package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.amplify.AmpService;
import com.jayptucker.heroesevolved.amplify.AmpSkills;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.styles.Piece;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
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
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Photographs Power Amplification into {@code build/amp-preview/screenshots/}, then closes. Flag {@code heroes.preview.amp}.
 * The player is the amplifier, a stand-in player beside them is the ally, and pillagers stand in front as foes. Every
 * scene is shot from a still camera to the side; the finishers are photographed by {@code FinisherPreview}
 * ({@code -Ponly=power_amplification}).
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class AmpPreview {
    // FIRST leaves the world five seconds to brighten after the Last Day's black hold, or the first scene shoots dark.
    private static final int STAGE = 180, FIRST = 220, SCENE = 80;

    private record Scene(String name, ResourceLocation allyPower, BiConsumer<ServerPlayer, ServerPlayer> act, int[] shots, boolean hud) {}

    private static final List<Scene> SCENES = new ArrayList<>();
    private static final List<ServerPlayer> STANDINS = new ArrayList<>();
    private static boolean started;
    private static int startup, age;
    private static String capture;

    private static AbilityUseContext context(ServerPlayer p) { return new AbilityUseContext(p, ModAbilities.AMPLIFICATION_ID, 5, false); }

    static {
        SCENES.add(new Scene("surge", ModAbilities.PYROKINESIS_ID, (p, ally) -> AmpService.surge(p, ally), new int[]{2, 8, 24}, true));
        SCENES.add(new Scene("overcharge", ModAbilities.QUAKE_ID, (p, ally) -> AmpService.overcharge(p, ally), new int[]{3, 30, 70}, false));
        SCENES.add(new Scene("ball-and-beam", null, (p, ally) -> AmpService.gather(context(p)), new int[]{12, 30}, false));
        SCENES.add(new Scene("bolt", null, (p, ally) -> AmpService.gather(context(p)), new int[]{4}, false));
        SCENES.add(new Scene("van-full", null, (p, ally) -> AmpSkills.vanFull(p), new int[]{2, 5, 10}, false));
        SCENES.add(new Scene("shock-therapy", ModAbilities.TELEPATHY_ID, (p, ally) -> {
            ally.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
            AmpService.shock(p, ally);
        }, new int[]{3, 8}, false));
        SCENES.add(new Scene("jumpstart", null, (p, ally) -> {
            ServerLevel level = p.serverLevel();
            BlockPos door = BlockPos.containing(STAGE + .5, -60, STAGE + 3.5);
            level.setBlock(door, Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
            level.setBlock(door.above(), Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
            AmpService.jumpBlock(p, door, AmpService.JUMPSTART_TICKS);
        }, new int[]{3, 10}, false));
        SCENES.add(new Scene("thousand-times", ModAbilities.HYDROKINESIS_ID, (p, ally) -> {
            standIn(p, ModAbilities.FLIGHT_ID, STAGE - 2.5, STAGE + 1, "Second");
            AmpService.thousandTimes(context(p));
        }, new int[]{10, 30, 42, 52}, false));
        SCENES.add(new Scene("storm", ModAbilities.ELECTROKINESIS_ID, (p, ally) -> AmpService.overcharge(p, ally), new int[]{21, 41, 61}, false));
        SCENES.add(new Scene("dome", ModAbilities.INVISIBILITY_ID, (p, ally) -> AmpService.overcharge(p, ally), new int[]{12, 40}, false));
        SCENES.add(new Scene("flash-freeze", ModAbilities.CRYOKINESIS_ID, (p, ally) -> AmpService.overcharge(p, ally), new int[]{3, 12}, false));
        SCENES.add(new Scene("burnout", null, (p, ally) -> {
            Mob foe = p.serverLevel().getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(8)).stream().findFirst().orElse(null);
            if (foe != null) AmpSkills.burnout(p, foe);
        }, new int[]{3, 12}, false));
        SCENES.add(new Scene("doorbuster", null, (p, ally) -> {
            ServerLevel level = p.serverLevel();
            BlockPos door = BlockPos.containing(STAGE + .5, -60, STAGE + 3.5);
            level.setBlock(door, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
            level.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
            p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(door));
            AmpSkills.doorbuster(p, door);
        }, new int[]{2, 6}, false));
        SCENES.add(new Scene("partner", ModAbilities.SUPERSPEED_ID, (p, ally) -> {
            AmpService.surge(p, ally);
            SetPiecePayload.send(p.serverLevel(), Piece.AMP_THREAD, p.position().add(0, 1.3, 0), ally.getBoundingBox().getCenter(), 1, p, ally.getId());
        }, new int[]{3}, false));
        // A pet wears the field too: its own model drawn again a size larger (client/SuperchargeField's creature layer).
        SCENES.add(new Scene("pet-field", null, (p, ally) -> {
            var wolf = EntityType.WOLF.create(p.serverLevel());
            wolf.moveTo(STAGE + 2.5, -60, STAGE + 1.5, 200, 0);
            wolf.setNoAi(true);
            wolf.tame(p);
            p.serverLevel().addFreshEntity(wolf);
            AmpService.surge(p, wolf);
            AmpService.overcharge(p, wolf);
        }, new int[]{3, 30}, false));
        // Every power boosted by us, in the table's order: the ally is Surged and then Overcharged, and shot as the
        // Overcharge lands and a second and a half into it. AmplificationGameTests checks the same twenty by rule.
        for (ResourceLocation power : List.of(ModAbilities.SUPERSPEED_ID, ModAbilities.TIME_MANIPULATION_ID, ModAbilities.TELEPATHY_ID,
                ModAbilities.VIBE_ID, ModAbilities.FLIGHT_ID, ModAbilities.PYROKINESIS_ID, ModAbilities.ELECTROKINESIS_ID,
                ModAbilities.HYDROKINESIS_ID, ModAbilities.CRYOKINESIS_ID, ModAbilities.QUAKE_ID, ModAbilities.SONIC_RESONANCE_ID,
                ModAbilities.TELEKINESIS_ID, ModAbilities.PHASING_ID, ModAbilities.INVISIBILITY_ID, ModAbilities.REGENERATION_ID,
                ModAbilities.RADIATION_ID, ModAbilities.TECHNOPATHY_ID, ModAbilities.MATTER_ASSEMBLY_ID, ModAbilities.MIMICRY_ID,
                ModAbilities.SHAPESHIFTING_ID))
            SCENES.add(new Scene("boost-" + power.getPath(), power, (p, ally) -> {
                AmpService.surge(p, ally);
                AmpService.overcharge(p, ally);
            }, new int[]{3, 30}, false));
        // -Ponly=boost shoots only the boosted powers; -Ponly=boost-quake,storm picks scenes by name.
        String only = System.getProperty("heroes.preview.amp.only", "");
        if (!only.isBlank()) {
            List<String> wanted = List.of(only.split(","));
            SCENES.removeIf(scene -> wanted.stream().noneMatch(w -> scene.name.contains(w.trim())));
        }
    }

    private static boolean on() { return Boolean.getBoolean("heroes.preview.amp"); }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!on()) return;
        var mc = Minecraft.getInstance();
        if (!started && ++startup >= 35 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(5);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Amp-" + System.currentTimeMillis(),
                    new LevelSettings("Amplification Preview", GameType.CREATIVE, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(93L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (age >= 100) mc.getToasts().clear();
        ++age;
        if (age == 104) { server(mc, com.jayptucker.heroesevolved.story.prologue.PrologueService::skip); return; }
        if (age == 110) {
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
            server(mc, p -> {
                CryoChamberService.release(p);
                p.setGameMode(GameType.SURVIVAL);
                var world = p.getServer().overworld();
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                    world.setChunkForced((STAGE >> 4) + dx, (STAGE >> 4) + dz, true);
                world.setDayTime(6000);
                p.teleportTo(world, STAGE + .5, -60, STAGE + .5, 0, 8);
                System.out.println("AMP_PREVIEW staged in " + p.level().dimension().location() + " on "
                        + world.getBlockState(p.blockPosition().below()));
            });
            return;
        }
        if (age % 20 == 0) com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
        if (age < FIRST) return;
        int index = (age - FIRST) / SCENE, local = (age - FIRST) % SCENE;
        if (index >= SCENES.size()) {
            if (local == 5) { TempestCamera.fix(null, null); server(mc, p -> dropAll()); }
            if (local == 15) {
                // One line that says whether the whole run worked: every scene staged and acted, and every photo saved.
                int expected = 0;
                for (Scene s : SCENES)
                    expected += s.shots.length + (s.name.equals("ball-and-beam") || s.name.equals("bolt") ? 2 : 0);
                boolean pass = FAILED.isEmpty() && photos >= expected;
                PreviewQuiet.report("AMP_PREVIEW_RESULT " + (pass ? "PASS" : "FAIL") + ": " + SCENES.size() + " scenes, "
                        + photos + "/" + expected + " photos" + (FAILED.isEmpty() ? "" : "; failed: " + String.join(", ", FAILED)));
                PreviewQuiet.quit();
            }
            return;
        }
        Scene scene = SCENES.get(index);
        if (local == 0) {
            mc.options.hideGui = !scene.hud;
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            TempestCamera.fix(null, null);
            server(mc, p -> guarded(scene, "staging", () -> stage(p, scene)));
        }
        if (local == 3) TempestCamera.fix(new Vec3(STAGE + .5 + 5.5, -58.2, STAGE + .5 + 1.6), new Vec3(STAGE + .5 + .4, -59.1, STAGE + .5 + 1.4));
        if (local == 6) server(mc, p -> guarded(scene, "acting", () -> act(p, scene)));
        // The ball becomes a beam after a long hold, and a bolt after a short one.
        if (scene.name.equals("ball-and-beam") && local == 6 + 36)
            server(mc, p -> guarded(scene, "releasing", () -> AmpService.releaseLightning(context(p))));
        if (scene.name.equals("bolt") && local == 6 + 5)
            server(mc, p -> guarded(scene, "releasing", () -> AmpService.releaseLightning(context(p))));
        if (scene.name.equals("ball-and-beam") && (local == 6 + 37 || local == 6 + 39)) capture = "amp-ball-and-beam-beam-" + (local - 6) + ".png";
        if (scene.name.equals("bolt") && (local == 6 + 6 || local == 6 + 8)) capture = "amp-bolt-fired-" + (local - 6) + ".png";
        for (int shot : scene.shots) if (local == 6 + shot) capture = "amp-" + scene.name + "-t" + shot + ".png";
    }

    private static ServerPlayer ally;
    /** Written on the server thread, read on the client's. */
    private static final List<String> FAILED = java.util.Collections.synchronizedList(new ArrayList<>());
    private static int photos;

    /**
     * Runs one step of a scene on the server and reports a throw instead of letting it crash the integrated server, so a
     * broken scene is named in the result and the rest still run.
     */
    private static void guarded(Scene scene, String step, Runnable job) {
        try {
            job.run();
        } catch (Throwable t) {
            FAILED.add(scene.name + " (" + step + ")");
            PreviewQuiet.report("AMP_PREVIEW " + scene.name + " FAIL while " + step + ": " + t);
            t.printStackTrace(System.out);
        }
    }

    private static void act(ServerPlayer p, Scene scene) {
        scene.act.accept(p, ally);
        System.out.println("AMP_PREVIEW " + scene.name + " acted; charge " + AmpService.charge(p));
    }

    /** The amplifier with the whole tree, a stand-in ally beside them, and pillagers in front. */
    private static void stage(ServerPlayer p, Scene scene) {
        ServerLevel world = p.serverLevel();
        dropAll();
        for (Mob old : world.getEntitiesOfClass(Mob.class, new AABB(STAGE - 20, -70, STAGE - 20, STAGE + 20, -40, STAGE + 20))) old.discard();
        for (BlockPos pos : BlockPos.betweenClosed(STAGE - 6, -60, STAGE - 6, STAGE + 6, -55, STAGE + 8))
            if (!world.getBlockState(pos).isAir()) world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        var power = ModAbilities.AMPLIFICATION_ID;
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(power));
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(power.toString(), new SkillTreeData.Build(15,
                Set.of(AmpSkills.PARTNER, AmpSkills.VAN_FULL, AmpSkills.BURNOUT, AmpSkills.DOORBUSTER, AmpSkills.BIGGER_BALL), Map.of())));
        com.jayptucker.heroesevolved.hydro.HydroService.powerChanged(p);
        PlayerProgressionService.setPowerLevel(p, 15);
        p.teleportTo(world, STAGE + .5, -60, STAGE + .5, 0, 8);
        p.setHealth(p.getMaxHealth());
        PlayerEnergyService.restore(p, 999);
        AmpService.addCharge(p, 100);
        com.jayptucker.heroesevolved.network.PlayerPowerSyncService.sync(p);
        PlayerEnergyService.sendNow(p);
        ally = scene.allyPower == null ? null : standIn(p, scene.allyPower, STAGE + 2, STAGE + 1, "Ally");
        if (ally != null) AmpService.bond(p, ally);
        pillager(world, new Vec3(STAGE + .5, -60, STAGE + 4.5), 24);
        pillager(world, new Vec3(STAGE + 2.2, -60, STAGE + 5.5), 24);
        pillager(world, new Vec3(STAGE - 1.6, -60, STAGE + 3.5), 24);
        System.out.println("AMP_PREVIEW staged " + scene.name);
    }

    private static Mob pillager(ServerLevel world, Vec3 at, float health) {
        Mob mob = EntityType.PILLAGER.create(world);
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        mob.moveTo(at.x, at.y, at.z, 180, 0);
        mob.setYHeadRot(180);
        mob.setYBodyRot(180);
        mob.setHealth(health);
        world.addFreshEntity(mob);
        return mob;
    }

    /** A stand-in player holding {@code power}, seen by the client as an ordinary player. */
    private static ServerPlayer standIn(ServerPlayer you, ResourceLocation power, double x, double z, String name) {
        ServerPlayer standIn = new FakePlayer(you.serverLevel(), new GameProfile(UUID.randomUUID(), name)) {
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource damage) { return false; }
        };
        try {
            var connection = standIn.connection.getConnection();
            Field channel = net.minecraft.network.Connection.class.getDeclaredField("channel");
            channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new io.netty.channel.embedded.EmbeddedChannel());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        standIn.moveTo(x + .5, -60, z + .5, 0, 0);
        standIn.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        PlayerProgressionService.setLevel(standIn, 5);
        PlayerEnergyService.restore(standIn, 999);
        PlayerList list = you.getServer().getPlayerList();
        AmpPreview.<List<ServerPlayer>>field(list, "players").add(standIn);
        AmpPreview.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").put(standIn.getUUID(), standIn);
        you.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(standIn)));
        you.serverLevel().addNewPlayer(standIn);
        STANDINS.add(standIn);
        return standIn;
    }

    private static void dropAll() {
        for (ServerPlayer p : STANDINS) {
            PlayerList list = p.getServer().getPlayerList();
            AmpPreview.<List<ServerPlayer>>field(list, "players").remove(p);
            AmpPreview.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").remove(p.getUUID());
            if (!p.isRemoved()) p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
        }
        STANDINS.clear();
        ally = null;
    }

    @SuppressWarnings("unchecked") private static <T> T field(PlayerList list, String name) {
        try {
            Field f = PlayerList.class.getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(list);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (capture == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
            photos++;
            System.out.println("AMP_CAPTURE " + capture);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        capture = null;
    }
}
