package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.gui.screen.AbilityWheelScreen;
import com.jayptucker.heroesevolved.gui.screen.MemoryWheelScreen;
import com.jayptucker.heroesevolved.mimicry.GiftedVillagers;
import com.jayptucker.heroesevolved.mimicry.MimicryService;
import com.jayptucker.heroesevolved.network.ClientLoadout;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.nio.file.*;
import java.util.function.Consumer;

/**
 * Empathic Mimicry in a flat world: an attunement building to a gifted villager, touches that keep sets, a switch,
 * and the memory wheel. Run with {@code tools/mimicry-preview.init.gradle}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class MimicryPreview {
    private static boolean started, cleanHud;
    private static int ticks, worldTicks;
    private static String pending;
    private static Villager pyro, flyer, cryo;

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void place(ServerPlayer p, double x, double z, float yaw, float pitch) {
        p.teleportTo(p.serverLevel(), x + .5, -60, z + .5, yaw, pitch);
    }

    private static Villager villager(ServerPlayer p, double x, double z, ResourceLocation gift) {
        Villager v = EntityType.VILLAGER.create(p.serverLevel());
        v.setNoAi(true);
        v.setInvulnerable(true);
        v.setPersistenceRequired();
        v.moveTo(x + .5, -60, z + .5, 180, 0);
        p.serverLevel().addFreshEntity(v);
        GiftedVillagers.give(v, gift);
        return v;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.mimicry")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = false;
            mc.options.pauseOnLostFocus = false;
            mc.options.fov().set(84);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Mimicry-" + System.currentTimeMillis(),
                    new LevelSettings("Mimicry preview", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (cleanHud) mc.getToasts().clear();
        switch (++worldTicks) {
            case 200 -> {
                cleanHud = true;
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    com.jayptucker.heroesevolved.story.bunker.CryoChamberService.release(p);
                    p.serverLevel().setDayTime(12600);
                    p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                            .assignDormant(ModAbilities.MIMICRY_ID).unlock(ModAbilities.MIMICRY_ID));
                    p.setData(ModDataAttachments.POWER_INTRO_HISTORY,
                            p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.MIMICRY_ID));
                    PlayerProgressionService.setPowerLevel(p, 15);
                    place(p, 1500, 0, 0, 8);
                });
            }
            // A gifted villager ahead: the threads draw tight over two seconds, then the power is taken.
            // (The villager stands off to the side, so the thread runs across the frame rather than at the camera.)
            case 220 -> onServer(mc, p -> pyro = villager(p, 1505, 2, ModAbilities.PYROKINESIS_ID));
            case 232 -> pending = "m1-attune-building.png";
            case 243 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); pending = "m1-attune-tight.png"; }
            case 262 -> pending = "m1-attune-imprint.png";
            // Touches keep sets: a palm laid on a villager, and a handprint of light.
            case 300 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                onServer(mc, p -> {
                    place(p, 1530, 0, 0, 14);
                    // A child, so the front camera sees over them to the palm.
                    flyer = villager(p, 1530, 1.2, ModAbilities.FLIGHT_ID);
                    flyer.setBaby(true);
                });
            }
            case 306 -> onServer(mc, p -> System.out.println("MIMIC touch flight " + MimicryService.touch(p, flyer)));
            case 308 -> pending = "m2-touch-front.png";
            case 314 -> pending = "m2-touch-bloom.png";
            case 340 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> { place(p, 1560, 0, 0, 12); cryo = villager(p, 1560, 2, ModAbilities.CRYOKINESIS_ID); });
            }
            case 346 -> onServer(mc, p -> System.out.println("MIMIC touch cryo " + MimicryService.touch(p, cryo)));
            case 348 -> pending = "m2-touch-firstperson.png";
            // Switching sets: the gold drawn in, the chosen power's colour opening out.
            case 380 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                onServer(mc, p -> { place(p, 1590, 0, 0, 8); System.out.println("MIMIC slots " + MimicryService.memory(p).slots()); });
            }
            case 386 -> onServer(mc, p -> System.out.println("MIMIC choose " + MimicryService.choose(p, 1)));
            case 389 -> pending = "m3-shift-draw.png";
            case 394 -> pending = "m3-shift-open.png";
            // The memory wheel, and the ability wheel showing the copied abilities.
            case 420 -> mc.setScreen(new MemoryWheelScreen().pin(2));
            case 426 -> pending = "m4-memory-wheel.png";
            case 432 -> mc.setScreen(null);
            case 436 -> {
                String first = ClientLoadout.get().entries().isEmpty() ? "" : ClientLoadout.get().entries().getFirst().actionKey();
                mc.setScreen(new AbilityWheelScreen(false).pin(first));
            }
            case 442 -> pending = "m5-ability-wheel-echoes.png";
            case 448 -> mc.setScreen(null);
            case 470 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Pre event) {
        if (cleanHud && Boolean.getBoolean("heroes.preview.mimicry") && Minecraft.getInstance().screen == null) event.setCanceled(true);
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("MIMIC_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
