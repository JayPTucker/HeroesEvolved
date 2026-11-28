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
 * the memory wheel, then each of the empath's own skills from the side (and Borrowed Strike in first person): Borrowed
 * Strike carrying fire, Empathic Surge reaching two pets, Calm lifting poison and weakness, Absorb Pain from a pet,
 * Sense Powers finding three gifted villagers, and Overcharge. Run with {@code tools/mimicry-preview.init.gradle}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class MimicryPreview {
    private static boolean started, cleanHud;
    private static int ticks, worldTicks;
    private static String pending;
    private static Villager pyro, flyer, cryo;
    private static net.minecraft.world.entity.LivingEntity target;
    private static final java.util.List<net.minecraft.world.entity.Entity> ACTORS = new java.util.ArrayList<>();

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    /** Into the overworld (a new player starts in the Last Day's city, another dimension), over force-loaded ground. */
    private static void place(ServerPlayer p, double x, double z, float yaw, float pitch) {
        var world = p.getServer().overworld();
        world.setChunkForced((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4, true);
        p.teleportTo(world, x + .5, -60, z + .5, yaw, pitch);
    }

    private static com.jayptucker.heroesevolved.ability.AbilityUseContext context(ServerPlayer p) {
        return new com.jayptucker.heroesevolved.ability.AbilityUseContext(p, ModAbilities.MIMICRY_ID, 5, false);
    }

    private static <T extends net.minecraft.world.entity.Mob> T actor(ServerPlayer p, EntityType<T> type, double x, double z) {
        T mob = type.create(p.getServer().overworld());
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        mob.moveTo(x + .5, -60, z + .5, 180, 0);
        p.getServer().overworld().addFreshEntity(mob);
        ACTORS.add(mob);
        return mob;
    }

    private static net.minecraft.world.entity.animal.Wolf pet(ServerPlayer p, double x, double z) {
        var wolf = actor(p, EntityType.WOLF, x, z);
        wolf.tame(p);
        return wolf;
    }

    private static void clear() {
        for (var e : ACTORS) e.discard();
        ACTORS.clear();
    }

    /** A still camera to the side (the player drawn in third person); null clears it. */
    private static void side(Minecraft mc, double x, double y, double z, double lx, double ly, double lz) {
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        com.jayptucker.heroesevolved.client.effects.TempestCamera.fix(new net.minecraft.world.phys.Vec3(x, y, z),
                new net.minecraft.world.phys.Vec3(lx, ly, lz));
    }

    private static void cast(Minecraft mc, String name, java.util.function.Predicate<ServerPlayer> skill) {
        onServer(mc, p -> {
            boolean ok;
            try { ok = skill.test(p); }
            catch (RuntimeException e) { e.printStackTrace(); ok = false; }
            PreviewQuiet.check("MIMIC_SKILL", ok, name);
        });
    }

    private static Villager villager(ServerPlayer p, double x, double z, ResourceLocation gift) {
        Villager v = EntityType.VILLAGER.create(p.getServer().overworld());
        v.setNoAi(true);
        v.setInvulnerable(true);
        v.setPersistenceRequired();
        v.moveTo(x + .5, -60, z + .5, 180, 0);
        p.getServer().overworld().addFreshEntity(v);
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
                    // EASY, not Peaceful: Peaceful deletes the pillager Borrowed Strike is aimed at (spawning is off).
                    new LevelSettings("Mimicry preview", GameType.SURVIVAL, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (cleanHud) mc.getToasts().clear();
        switch (++worldTicks) {
            case 190 -> onServer(mc, com.jayptucker.heroesevolved.story.prologue.PrologueService::skip);
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
                    p.removeAllEffects();
                    System.out.println("MIMIC_PREVIEW staged in " + p.level().dimension().location() + " at " + p.blockPosition()
                            + " on " + p.level().getBlockState(p.blockPosition().below()));
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
            // ------------------------------------------------------------------ the empath's own skills
            // Borrowed Strike, channelling fire: the thread whipped out of the open hand, the foe blooming orange.
            case 470 -> onServer(mc, p -> {
                place(p, 1620, 0, 0, 0);
                p.removeAllEffects();
                MimicryService.remember(p, ModAbilities.PYROKINESIS_ID, 5);
                target = actor(p, EntityType.PILLAGER, 1621, 7);
                target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(400);
                target.setHealth(400);
            });
            case 476 -> side(mc, 1628.5, -58.2, 3.5, 1620.5, -59.2, 3.5);
            case 480 -> cast(mc, "borrowed strike", p -> {
                p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
                return com.jayptucker.heroesevolved.mimicry.MimicSkills.skill(context(p), 0) && target.isOnFire();
            });
            case 482 -> pending = "m6-lash-whip.png";
            case 485 -> pending = "m6-lash-land.png";
            case 490 -> pending = "m6-lash-bloom.png";
            case 500 -> {
                com.jayptucker.heroesevolved.client.effects.TempestCamera.fix(null, null);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            }
            case 504 -> cast(mc, "borrowed strike again", p -> {
                p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
                return com.jayptucker.heroesevolved.mimicry.MimicSkills.skill(context(p), 0);
            });
            case 506 -> pending = "m6-lash-firstperson.png";
            case 509 -> pending = "m6-lash-firstperson-land.png";
            // Empathic Surge: two heartbeats ring out, threads snap to two pets.
            case 530 -> onServer(mc, p -> {
                clear();
                place(p, 1650, 0, 0, 0);
                pet(p, 1652.5, 2);
                pet(p, 1647.5, 3);
            });
            case 536 -> side(mc, 1658, -56.8, 1.5, 1650.5, -59.3, 1.5);
            case 540 -> cast(mc, "empathic surge", p -> com.jayptucker.heroesevolved.mimicry.MimicSkills.skill(context(p), 1)
                    && p.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST));
            case 543 -> pending = "m7-surge-beat.png";
            case 548 -> pending = "m7-surge-threads.png";
            case 555 -> pending = "m7-surge-blooms.png";
            // Calm: the ring settles out; poison and weakness rise off as motes and arc into the heart.
            case 580 -> onServer(mc, p -> {
                clear();
                place(p, 1680, 0, 0, 0);
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 400, 0));
                var wolf = pet(p, 1682.5, 2.5);
                wolf.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 400, 0));
                wolf.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 400, 0));
            });
            case 586 -> side(mc, 1688, -57, 1.5, 1680.5, -59.3, 1.5);
            case 590 -> cast(mc, "calm", p -> com.jayptucker.heroesevolved.mimicry.MimicSkills.skill(context(p), 2)
                    && !p.hasEffect(net.minecraft.world.effect.MobEffects.POISON));
            case 593 -> pending = "m8-calm-ring.png";
            case 600 -> pending = "m8-calm-motes.png";
            case 610 -> pending = "m8-calm-settled.png";
            // Absorb Pain: a pet's sicknesses run down the threads into the heart as beads of their own colour.
            case 630 -> onServer(mc, p -> {
                clear();
                place(p, 1710, 0, 0, 0);
                var wolf = pet(p, 1712.5, 3);
                wolf.setHealth(10);
                wolf.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 400, 0));
                wolf.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 400, 0));
                target = wolf;
            });
            case 636 -> side(mc, 1718, -57.2, 0.5, 1711.5, -59.2, 1.8);
            case 640 -> cast(mc, "absorb pain", p -> {
                p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
                return com.jayptucker.heroesevolved.mimicry.MimicSkills.technique(context(p), "absorb_pain");
            });
            case 644 -> pending = "m9-absorb-threads.png";
            case 650 -> pending = "m9-absorb-beads.png";
            case 658 -> pending = "m9-absorb-in.png";
            // Sense Powers: two heartbeats race out; three gifted villagers flare in their colours.
            case 680 -> onServer(mc, p -> {
                clear();
                place(p, 1740, 0, 0, 0);
                ACTORS.add(villager(p, 1746, 8, ModAbilities.PYROKINESIS_ID));
                ACTORS.add(villager(p, 1733, 11, ModAbilities.CRYOKINESIS_ID));
                ACTORS.add(villager(p, 1749, -5, ModAbilities.FLIGHT_ID));
            });
            case 686 -> side(mc, 1757, -51, -9, 1741, -60, 3);
            case 690 -> cast(mc, "sense powers", p -> com.jayptucker.heroesevolved.mimicry.MimicSkills.technique(context(p), "sense_powers"));
            case 697 -> pending = "m10-sense-going-out.png";
            case 705 -> pending = "m10-sense-found.png";
            case 716 -> pending = "m10-sense-threads.png";
            // Overcharge: rings in the channelled power's colour close on both hands.
            case 740 -> onServer(mc, p -> {
                clear();
                place(p, 1770, 0, 0, 0);
                MimicryService.remember(p, ModAbilities.CRYOKINESIS_ID, 5);
            });
            case 746 -> side(mc, 1773, -58.3, 2.2, 1770.5, -58.9, .4);
            case 750 -> cast(mc, "overcharge", p -> com.jayptucker.heroesevolved.mimicry.MimicSkills.technique(context(p), "overcharge")
                    && MimicryService.overcharged(p));
            case 753 -> pending = "m11-overcharge-closing.png";
            case 759 -> pending = "m11-overcharge-flare.png";
            case 780 -> {
                com.jayptucker.heroesevolved.client.effects.TempestCamera.fix(null, null);
                onServer(mc, p -> clear());
            }
            case 790 -> PreviewQuiet.quit();
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
