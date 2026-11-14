package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.MorphClient;
import com.jayptucker.heroesevolved.gui.screen.FormWheelScreen;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.shapeshift.ShapeshiftService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Shapeshifting, phases 1 to 4: studying a zombie (front and back), the plates closing over the body and falling
 * away at six points of the change, the zombie's arm in first person, a big change into an iron golem, the change back
 * (head first), touching a cow to learn it, the sweep gesture in first person, and the form wheel. Phase 3: wearing a
 * person's face (with your own helmet and sword still showing), their arm in first person, and a line said as them.
 * Staged 1800 blocks from spawn. Screenshots go to build/shapeshift-preview/screenshots.
 *
 * <pre>./gradlew runClient --init-script tools/shapeshift-preview.init.gradle</pre>
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class ShapeshiftPreview {
    private static final int X = 1800, Y = -60, Z = 0;
    /** The person whose face is borrowed: not anyone on the server, so their default skin comes from this id. */
    private static final java.util.UUID PERSON = java.util.UUID.fromString("5a1c8e2f-0b3d-4c6e-9f7a-1b2c3d4e5f61");
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void shot(String name) { pending = "shapeshift-" + name + ".png"; }

    private static void morph(Minecraft mc, EntityType<?> type) {
        onServer(mc, p -> System.out.println("SHAPESHIFT_MORPH " + type + " " + ShapeshiftService.morph(p, type)
                + " size=" + p.getBbWidth() + "x" + p.getBbHeight() + " health=" + p.getMaxHealth()));
    }

    /** A still side view of the Instinct stage: the player at z+8.5 striking toward the dummy at z+12. */
    private static void side() {
        com.jayptucker.heroesevolved.client.effects.TempestCamera.fix(
                new net.minecraft.world.phys.Vec3(X + 6.5, Y + 1.9, Z + 10.4), new net.minecraft.world.phys.Vec3(X + 0.5, Y + 1.2, Z + 10.4));
    }

    private static void instinct(Minecraft mc) {
        onServer(mc, p -> System.out.println("SHAPESHIFT_INSTINCT " + com.jayptucker.heroesevolved.shapeshift.ShapeshiftInstinct.move(p)
                + " " + com.jayptucker.heroesevolved.shapeshift.ShapeshiftInstinct.use(p)));
    }

    private static com.jayptucker.heroesevolved.ability.AbilityUseContext context(ServerPlayer p) {
        return new com.jayptucker.heroesevolved.ability.AbilityUseContext(p, com.jayptucker.heroesevolved.ability.registry.ModAbilities.SHAPESHIFTING_ID, 5, false);
    }

    /** Casts one of the tree's active skills, as its key would (without the cost). */
    private static boolean skill(ServerPlayer p, String id) {
        return com.jayptucker.heroesevolved.shapeshift.ShapeshiftSkills.action(
                com.jayptucker.heroesevolved.skills.SkillCatalog.find("shapeshifting", id).orElseThrow()).activate(context(p))
                == com.jayptucker.heroesevolved.ability.AbilityActivationResult.SUCCESS;
    }

    private static void place(ServerLevel level, EntityType<? extends Mob> type, double x, double z, float yaw) {
        Mob mob = type.create(level);
        mob.moveTo(x, Y, z, yaw, 0);
        mob.setYHeadRot(yaw);
        mob.yBodyRot = yaw;
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
    }

    private static void stage(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        for (int dx = -12; dx <= 12; dx++)
            for (int dz = -12; dz <= 16; dz++)
                level.setBlockAndUpdate(new BlockPos(X + dx, Y - 1, Z + dz),
                        ((dx + dz) % 4 == 0 ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK).defaultBlockState());
        // Real ones off to the side, to compare size and pose.
        place(level, EntityType.HUSK, X + 2.5, Z + 0.5, 0);
        place(level, EntityType.COW, X - 2.5, Z + 0.5, 0);
    }

    private static void home(Minecraft mc) {
        onServer(mc, p -> p.teleportTo(p.serverLevel(), X + 0.5, Y, Z + 0.5, 0, 0));
    }

    /** Shoots a change of shape as it passes each of these points of its timeline. */
    private static float[] watch;
    private static String watchName;
    private static int watchIndex;

    private static void watch(String name, float... points) { watch = points; watchName = name; watchIndex = 0; }

    private static void face(Minecraft mc, float yaw) {
        onServer(mc, p -> p.teleportTo(p.serverLevel(), X + 0.5, Y, Z + 0.5, yaw, 0));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.shapeshift")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Shapeshift-" + System.currentTimeMillis(),
                    new LevelSettings("Shapeshift preview", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        mc.getToasts().clear();
        int t = ++worldTicks;
        switch (t) {
            case 150 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(6000);
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                        .assignDormant(ModAbilities.SHAPESHIFTING_ID).unlock(ModAbilities.SHAPESHIFTING_ID));
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.SHAPESHIFTING_ID));
                PlayerProgressionService.setPowerLevel(p, 20);
                PlayerEnergyService.setUnlimitedEnergy(p, true);
                stage(p);
                p.teleportTo(p.serverLevel(), X + 0.5, Y, Z + 0.5, 0, 0);
            });
            case 170 -> { mc.options.hideGui = true; mc.gui.getChat().clearMessages(false); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }
            case 186 -> shot("01-own-body-front");
            // Study the zombie standing to the player's left (east): face it, start, then turn back to the camera.
            case 190 -> face(mc, -90);
            case 194 -> onServer(mc, p -> System.out.println("SHAPESHIFT_STUDY " + ShapeshiftService.study(p)));
            case 197 -> { mc.player.setYRot(-35); mc.player.setYHeadRot(-35); }
            case 204 -> shot("02-study-front");
            case 206 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 209 -> shot("03-study-back");
            case 211 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); watch("04-morph", .14F, .3F, .46F, .62F, .74F, .88F); }
            case 250 -> shot("05-husk-front");
            case 254 -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }
            case 262 -> shot("06-husk-first-person-arm");
            case 266 -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); face(mc, 0); }
            case 270 -> { watch("07-golem", .2F, .36F, .5F, .66F, .8F); onServer(mc, p -> ShapeshiftService.morph(p, EntityType.IRON_GOLEM)); }
            case 310 -> shot("08-golem-front");
            case 314 -> { watch("09-revert", .25F, .5F, .7F, .85F); onServer(mc, p -> System.out.println("SHAPESHIFT_REVERT " + ShapeshiftService.revert(p))); }
            case 346 -> shot("10-own-body-again");
            // Touch the cow standing to the player's right (west).
            case 350 -> face(mc, 90);
            case 354 -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }
            case 357 -> {
                var cow = mc.level.getEntitiesOfClass(net.minecraft.world.entity.animal.Cow.class, mc.player.getBoundingBox().inflate(5)).stream().findFirst();
                cow.ifPresent(c -> mc.gameMode.interact(mc.player, c, net.minecraft.world.InteractionHand.MAIN_HAND));
                System.out.println("SHAPESHIFT_TOUCH " + cow.isPresent());
            }
            case 361 -> shot("11-touch-first-person");
            case 362 -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
            case 364 -> shot("12-touch-back");
            case 372 -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); face(mc, 0); }
            case 375 -> mc.player.setXRot(40);
            case 376 -> { watch("13-first-person", .2F, .4F, .52F, .7F, .85F); onServer(mc, p -> ShapeshiftService.morph(p, EntityType.CHICKEN)); }
            case 400 -> shot("14-chicken-first-person");
            case 404 -> { mc.options.hideGui = true; mc.player.setXRot(0); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }
            case 406 -> onServer(mc, p -> System.out.println("SHAPESHIFT_WHEEL " + ShapeshiftService.openWheel(p)));
            case 410 -> { if (mc.screen instanceof FormWheelScreen wheel) wheel.pin(1); }
            case 414 -> shot("15-wheel");
            case 416 -> { if (mc.screen instanceof FormWheelScreen wheel) wheel.pin(-2); }
            case 420 -> shot("16-wheel-centre");
            case 424 -> mc.setScreen(null);
            // Phase 3: a person's face. Your own helmet and sword still show (a clue), then a line said as them.
            case 428 -> onServer(mc, p -> {
                ShapeshiftService.revert(p);
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
            });
            case 446 -> { watch("17-person", .3F, .75F); onServer(mc, p -> System.out.println("SHAPESHIFT_PERSON " + ShapeshiftService.morph(p,
                    com.jayptucker.heroesevolved.shapeshift.ShapeshiftForm.of(new com.mojang.authlib.GameProfile(PERSON, "Ravenna"))))); }
            case 476 -> shot("18-person-front");
            case 478 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 482 -> shot("19-person-back");
            case 484 -> onServer(mc, p -> p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY));
            case 486 -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }
            case 492 -> shot("20-person-first-person-arm");
            case 494 -> mc.player.connection.sendChat("Meet me at the old tower at dusk.");
            case 500 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }
            case 504 -> shot("21-chat-as-ravenna");
            // Phase 4: Instinct against an iron golem dummy, seen from behind.
            case 508 -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); side(); onServer(mc, p -> {
                ShapeshiftService.revert(p);
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
                p.teleportTo(p.serverLevel(), X + 0.5, Y, Z + 8.5, 0, 0);
                place(p.serverLevel(), EntityType.IRON_GOLEM, X + 0.5, Z + 12, 180);
            }); }
            case 530 -> instinct(mc);
            case 532 -> shot("22-lunge-a");
            case 534 -> shot("22-lunge-b");
            case 540 -> onServer(mc, p -> ShapeshiftService.morph(p, EntityType.ZOMBIE));
            case 566 -> instinct(mc);
            case 569 -> shot("23-grab-a");
            case 580 -> shot("23-grab-b");
            case 590 -> onServer(mc, p -> ShapeshiftService.morph(p, EntityType.SPIDER));
            case 616 -> instinct(mc);
            case 618 -> shot("24-web-a");
            case 622 -> shot("24-web-b");
            case 630 -> onServer(mc, p -> ShapeshiftService.morph(p, EntityType.WARDEN));
            case 660 -> { instinct(mc); onServer(mc, p -> p.removeEffect(net.minecraft.world.effect.MobEffects.DARKNESS)); }
            case 666 -> shot("25-sonic-windup");
            case 672 -> shot("25-sonic-boom-a");
            case 676 -> shot("25-sonic-boom-b");
            case 690 -> onServer(mc, p -> ShapeshiftService.morph(p, EntityType.ENDERMAN));
            case 716 -> com.jayptucker.heroesevolved.client.effects.TempestCamera.fix(
                    new net.minecraft.world.phys.Vec3(X + 9, Y + 2.2, Z + 14), new net.minecraft.world.phys.Vec3(X + 0.5, Y + 1, Z + 14));
            case 718 -> instinct(mc);
            case 720 -> shot("26-blink-a");
            case 724 -> shot("26-blink-b");
            case 730 -> { side(); onServer(mc, p -> {
                p.teleportTo(p.serverLevel(), X + 0.5, Y, Z + 8.5, 0, 0);
                ShapeshiftService.morph(p, EntityType.CREEPER);
            }); }
            case 760 -> instinct(mc);
            case 762 -> shot("27-blast-a");
            case 765 -> shot("27-blast-b");
            case 770 -> shot("27-blast-c");
            case 790 -> { com.jayptucker.heroesevolved.client.effects.TempestCamera.fix(null, null);
                mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); onServer(mc, p -> p.teleportTo(p.serverLevel(), X + 0.5, Y, Z + 8.5, 0, 0)); }
            case 800 -> instinct(mc);
            case 802 -> shot("28-lunge-first-person-a");
            case 804 -> shot("28-lunge-first-person-b");
            // Phase 5: Decoy, Reflection, Nightmare, a disguise bursting off, and the Suspicion eye.
            case 812 -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); onServer(mc, p -> {
                ShapeshiftService.revert(p);
                p.teleportTo(p.serverLevel(), X + 0.5, Y, Z + 8.5, 0, 0);
                ShapeshiftService.morph(p, EntityType.ZOMBIE);
            }); }
            case 842 -> onServer(mc, p -> {
                System.out.println("SHAPESHIFT_DECOY " + com.jayptucker.heroesevolved.shapeshift.ShapeshiftTricks.decoy(p));
                p.teleportTo(p.serverLevel(), X - 2, Y, Z + 7.5, 0, 0);
            });
            // Behind the shell, to see the seam where you stepped out.
            case 844 -> com.jayptucker.heroesevolved.client.effects.TempestCamera.fix(
                    new net.minecraft.world.phys.Vec3(X + 1.8, Y + 2.1, Z + 6.0), new net.minecraft.world.phys.Vec3(X + 0.5, Y + 1.1, Z + 8.5));
            case 850 -> shot("29-decoy-seam-a");
            case 858 -> shot("29-decoy-seam-b");
            case 862 -> { side(); onServer(mc, p -> {
                var decoy = com.jayptucker.heroesevolved.shapeshift.ShapeshiftTricks.decoyOf(p);
                var golem = p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.IronGolem.class, p.getBoundingBox().inflate(16)).stream().findFirst().orElse(null);
                if (decoy != null && golem != null) decoy.hurt(p.serverLevel().damageSources().mobAttack(golem), 5);
                System.out.println("SHAPESHIFT_BURST " + (decoy != null && decoy.isRemoved()));
            }); }
            case 864 -> shot("30-decoy-burst-a");
            case 868 -> shot("30-decoy-burst-b");
            case 880 -> shot("31-daze");
            case 888 -> onServer(mc, p -> {
                ShapeshiftService.revert(p);
                p.teleportTo(p.serverLevel(), X + 0.5, Y, Z + 8.5, 0, 0);
            });
            case 910 -> onServer(mc, p -> {
                var golem = p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.IronGolem.class, p.getBoundingBox().inflate(16)).stream().findFirst().orElse(null);
                if (golem != null) com.jayptucker.heroesevolved.shapeshift.ShapeshiftTricks.struck(p, golem);
                System.out.println("SHAPESHIFT_REFLECT " + com.jayptucker.heroesevolved.shapeshift.ShapeshiftTricks.reflect(p));
            });
            case 914 -> shot("32-reflect-a");
            case 919 -> shot("32-reflect-b");
            case 924 -> shot("32-reflect-c");
            case 950 -> onServer(mc, p -> System.out.println("SHAPESHIFT_NIGHTMARE " + com.jayptucker.heroesevolved.shapeshift.ShapeshiftTricks.nightmare(p)));
            case 955 -> shot("33-nightmare-crown-a");
            case 962 -> shot("33-nightmare-crown-b");
            // What a foe sees: on this one client, send ourselves the piece a foe's client would get.
            case 990 -> onServer(mc, p -> com.jayptucker.heroesevolved.shapeshift.ShapeshiftGeometry.showTo(p,
                    com.jayptucker.heroesevolved.styles.Piece.SHIFT_NIGHTMARE, p.position(), p.getEyePosition(),
                    com.jayptucker.heroesevolved.shapeshift.ShapeshiftTricks.SEEN_WARDEN, p));
            case 997 -> shot("34-nightmare-foe-a");
            case 1006 -> shot("34-nightmare-foe-b");
            case 1030 -> onServer(mc, p -> ShapeshiftService.morph(p, EntityType.ZOMBIE));
            case 1060 -> onServer(mc, p -> com.jayptucker.heroesevolved.shapeshift.ShapeshiftSuspicion.expose(p));
            case 1062 -> shot("35-exposed-a");
            case 1067 -> shot("35-exposed-b");
            case 1074 -> shot("35-exposed-c");
            // The eye under the crosshair, filling as a watching zombie grows suspicious.
            case 1300 -> onServer(mc, p -> {
                p.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
                ShapeshiftService.morph(p, EntityType.ZOMBIE);
            });
            case 1330 -> { com.jayptucker.heroesevolved.client.effects.TempestCamera.fix(null, null); mc.options.hideGui = false;
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> com.jayptucker.heroesevolved.shapeshift.ShapeshiftSuspicion.raise(p, 62)); }
            case 1345 -> shot("36-suspicion-eye");
            // Phase 6: the tree's actives, Apex Form, and the class abilities, against the golem on the stage.
            case 1352 -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); side(); onServer(mc, p -> {
                ShapeshiftService.revert(p);
                var all = com.jayptucker.heroesevolved.skills.SkillCatalog.all("shapeshifting").stream()
                        .map(com.jayptucker.heroesevolved.skills.SkillCatalog.Node::id).collect(java.util.stream.Collectors.toSet());
                p.setData(com.jayptucker.heroesevolved.data.ModDataAttachments.SKILL_TREES,
                        com.jayptucker.heroesevolved.skills.SkillTreeData.empty().with(com.jayptucker.heroesevolved.ability.registry.ModAbilities.SHAPESHIFTING_ID.toString(),
                                new com.jayptucker.heroesevolved.skills.SkillTreeData.Build(28, all, java.util.Map.of())));
                p.teleportTo(p.serverLevel(), X + 0.5, Y, Z + 8.5, 0, 0);
                ShapeshiftService.morph(p, EntityType.ZOMBIE);
            }); }
            case 1385 -> onServer(mc, p -> ShapeshiftService.morph(p, EntityType.COW));
            case 1415 -> onServer(mc, p -> System.out.println("SHAPESHIFT_QUICK " + com.jayptucker.heroesevolved.shapeshift.ShapeshiftSkills.action(
                    com.jayptucker.heroesevolved.skills.SkillCatalog.find("shapeshifting", "shapeshifting_0_1").orElseThrow())
                    .activate(new com.jayptucker.heroesevolved.ability.AbilityUseContext(p, com.jayptucker.heroesevolved.ability.registry.ModAbilities.SHAPESHIFTING_ID, 5, false))));
            case 1423 -> shot("37-quick-change");
            case 1450 -> onServer(mc, p -> System.out.println("SHAPESHIFT_APEX " + skill(p, "shapeshifting_apex_form")));
            case 1453 -> shot("38-apex-a");
            case 1464 -> shot("38-apex-b");
            case 1470 -> instinct(mc);
            case 1473 -> shot("39-slam-a");
            case 1478 -> shot("39-slam-b");
            case 1500 -> onServer(mc, p -> ShapeshiftService.revert(p));
            case 1520 -> onServer(mc, p -> System.out.println("SHAPESHIFT_BORROW " + skill(p, "shapeshifting_2_1")));
            case 1546 -> shot("40-borrowed-face");
            case 1560 -> onServer(mc, p -> { ShapeshiftService.revert(p); p.teleportTo(p.serverLevel(), X + 0.5, Y, Z + 8.5, 90, 0); });
            case 1585 -> onServer(mc, p -> {
                System.out.println("SHAPESHIFT_DOPPEL " + skill(p, "shapeshifting_doppelganger"));
                p.teleportTo(p.serverLevel(), X + 2.5, Y, Z + 6.5, 90, 0);
            });
            case 1597 -> shot("41-doppelganger-a");
            case 1612 -> shot("41-doppelganger-b");
            case 1620 -> onServer(mc, p -> {
                p.teleportTo(p.serverLevel(), X + 0.5, Y, Z + 8.5, 0, 0);
                place(p.serverLevel(), EntityType.PILLAGER, X + 3.5, Z + 10.5, 200);
                place(p.serverLevel(), EntityType.PILLAGER, X - 2.5, Z + 10.5, 160);
            });
            case 1640 -> onServer(mc, p -> System.out.println("SHAPESHIFT_MANY " + com.jayptucker.heroesevolved.shapeshift.ShapeshiftSkills.cast(context(p),
                    "shapeshifting_many_hands")));
            case 1642 -> shot("42-many-hands-a");
            case 1645 -> shot("42-many-hands-b");
            case 1649 -> shot("42-many-hands-c");
            case 1670 -> onServer(mc, p -> com.jayptucker.heroesevolved.shapeshift.ShapeshiftSkills.cast(context(p), "shapeshifting_plate_mail"));
            case 1676 -> shot("43-plate-mail");
            case 1680 -> onServer(mc, p -> p.teleportTo(p.serverLevel(), X + 4.5, Y, Z + 6.5, 180, 0));
            case 1682 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 1684 -> com.jayptucker.heroesevolved.client.effects.TempestCamera.fix(null, null);
            case 1690 -> shot("43-plate-mail-front");
            case 1700 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); side();
                onServer(mc, p -> System.out.println("SHAPESHIFT_MASQUERADE " + com.jayptucker.heroesevolved.shapeshift.ShapeshiftSkills.cast(context(p),
                        "shapeshifting_masquerade"))); }
            case 1710 -> shot("44-masquerade");
            case 1740 -> onServer(mc, p -> ShapeshiftService.revert(p));
            case 1770 -> onServer(mc, p -> {
                ShapeshiftService.morph(p, EntityType.CHICKEN);
                ShapeshiftService.revert(p);
            });
            case 1800 -> onServer(mc, p -> System.out.println("SHAPESHIFT_HYBRID " + skill(p, "shapeshifting_1_1")));
            case 1804 -> shot("45-hybrid");
            case 1820 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (pending == null && watch != null && mc.player != null && mc.level != null) {
            var change = MorphClient.transition(mc.player.getId());
            float partial = e.getPartialTick().getGameTimeDeltaPartialTick(false);
            if (change != null && watchIndex < watch.length && change.progress(mc.level.getGameTime(), partial) >= watch[watchIndex]) {
                shot(watchName + "-" + (watchIndex + 1) + "-" + Math.round(watch[watchIndex] * 100));
                if (++watchIndex >= watch.length) watch = null;
            }
        }
        if (pending == null) return;
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
            System.out.println("SHAPESHIFT_CAPTURE " + pending + " client=" + mc.player.getBbWidth() + "x" + mc.player.getBbHeight());
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
