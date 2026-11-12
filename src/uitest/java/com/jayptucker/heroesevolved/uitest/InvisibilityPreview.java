package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.intro.PowerIntroHistory;
import com.jayptucker.heroesevolved.invisibility.ForceFieldService;
import com.jayptucker.heroesevolved.invisibility.InvisibilityService;
import com.jayptucker.heroesevolved.invisibility.InvisibilitySkills;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Arrow;
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
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Invisibility's force-field kit and tree, cast in a flat survival world 600+ blocks from spawn and photographed from
 * behind, the front and first person. Screenshots land in {@code build/invisibility-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class InvisibilityPreview {
    private static boolean started;
    private static int ticks, world;
    private static String pending;
    private static final List<LivingEntity> MOBS = new ArrayList<>();
    private static final double GROUND = -60;

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(mc.player.getUUID());
            PlayerEnergyService.restore(p, 999);
            action.accept(p);
        });
    }
    private static void place(ServerPlayer p, double x, double z, float yaw, float pitch) {
        ForceFieldService.forget(p);
        p.teleportTo(p.serverLevel(), x + .5, GROUND, z + .5, yaw, pitch);
    }
    private static void look(Minecraft mc, float yaw, float pitch) {
        mc.player.setYRot(yaw); mc.player.setXRot(pitch);
        server(mc, p -> { p.setYRot(yaw); p.setXRot(pitch); p.connection.teleport(p.getX(), p.getY(), p.getZ(), yaw, pitch); });
    }
    private static <T extends LivingEntity> T spawn(ServerPlayer p, EntityType<T> type, double x, double y, double z) {
        T mob = type.create(p.serverLevel());
        if (mob instanceof Mob m) { m.setNoAi(true); m.setPersistenceRequired(); }
        mob.moveTo(x + .5, y, z + .5, 180, 0);
        mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);
        mob.setHealth(500);
        p.serverLevel().addFreshEntity(mob);
        MOBS.add(mob);
        return mob;
    }
    private static <T extends LivingEntity> T spawn(ServerPlayer p, EntityType<T> type, double x, double z) { return spawn(p, type, x, GROUND, z); }
    /** A husk that moves for itself, so pushes and pops visibly throw it (no-AI mobs ignore velocity). */
    private static Mob husk(ServerPlayer p, double x, double z) {
        Mob mob = spawn(p, EntityType.HUSK, x, z);
        mob.setNoAi(false);
        return mob;
    }
    private static boolean netShot;
    private static int netAt;
    private static void clear() { MOBS.forEach(LivingEntity::discard); MOBS.clear(); }
    private static void shoot(ServerPlayer p, LivingEntity from, Vec3 at) {
        var arrow = new Arrow(p.serverLevel(), from, new ItemStack(Items.ARROW), null);
        Vec3 start = from.getEyePosition();
        arrow.setPos(start);
        arrow.setDeltaMovement(at.subtract(start).normalize().scale(1.6));
        p.serverLevel().addFreshEntity(arrow);
    }
    private static void learn(ServerPlayer p, String... nodes) {
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.INVISIBILITY_ID.toString(),
                new SkillTreeData.Build(15, Set.of(nodes), Map.of())));
    }
    private static AbilityUseContext cast(ServerPlayer p) { return new AbilityUseContext(p, ModAbilities.INVISIBILITY_ID, 5, false); }
    private static void shot(String name) { pending = "force-" + name + ".png"; }
    private static void camera(Minecraft mc, CameraType type) { mc.options.setCameraType(type); }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.invisibility")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Force-" + System.currentTimeMillis(),
                    new LevelSettings("Invisibility preview", GameType.SURVIVAL, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (world > 775 && world < 830 && !netShot && mc.player.onGround()) { netShot = true; netAt = world; shot("16-net-landing"); }
        if (netShot && (world == netAt + 3 || world == netAt + 7)) shot("16-net-landing-" + (world - netAt));
        if (world == 782) shot("16-net-falling");
        switch (++world) {
            case 150 -> server(mc, p -> {
                p.serverLevel().setDayTime(5000);
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, PowerIntroHistory.empty().with(ModAbilities.INVISIBILITY_ID));
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                        .assignDormant(ModAbilities.INVISIBILITY_ID).unlock(ModAbilities.INVISIBILITY_ID));
                PlayerProgressionService.setLevel(p, 5);
                learn(p);
                p.getAbilities().invulnerable = true; p.onUpdateAbilities();
            });
            case 156 -> { mc.options.hideGui = true; camera(mc, CameraType.THIRD_PERSON_FRONT); }

            // 1. Fade, shared with a tamed wolf at your side.
            case 160 -> server(mc, p -> { place(p, 600, 0, 0, 5);
                var wolf = spawn(p, EntityType.WOLF, 601.5, 0.5); wolf.tame(p); });
            case 172 -> shot("01-fade-before");
            case 174 -> server(mc, InvisibilityService::toggleFade);
            case 177 -> shot("01-fade-a");
            case 186 -> shot("01-fade-b");
            case 200 -> shot("01-fade-shared");
            case 204 -> server(mc, p -> { InvisibilityService.unfade(p); clear(); });

            // 2. Lens Shield, an arrow struck from the front, then the Force Push into a line of foes.
            case 210 -> { camera(mc, CameraType.THIRD_PERSON_BACK); server(mc, p -> { place(p, 640, 0, 0, 4);
                husk(p, 640, 6); husk(p, 641.5, 7.5); husk(p, 638.5, 8);
                spawn(p, EntityType.PILLAGER, 643, 12); }); }
            case 222 -> server(mc, ForceFieldService::raiseShield);
            case 224 -> shot("02-shield-rise");
            case 228 -> shot("02-shield-held");
            case 229 -> server(mc, p -> shoot(p, MOBS.getLast(), p.getEyePosition()));
            case 236 -> shot("02-shield-struck");
            case 237 -> camera(mc, CameraType.THIRD_PERSON_FRONT);
            case 240 -> shot("02-shield-front");
            case 241 -> camera(mc, CameraType.FIRST_PERSON);
            case 244 -> shot("02-shield-first-person");
            case 245 -> camera(mc, CameraType.THIRD_PERSON_BACK);
            case 248 -> server(mc, ForceFieldService::push);
            case 249 -> { shot("03-push-a"); look(mc, -35, 4); }
            case 251 -> shot("03-push-b");
            case 253 -> shot("03-push-c");
            case 256 -> shot("03-push-d");
            case 262 -> shot("03-push-after");
            case 266 -> server(mc, p -> clear());

            // 3. Force Bridge over a ravine, rising, then walked.
            case 270 -> server(mc, p -> { place(p, 680, 0, 0, -18);
                for (int x = 676; x <= 684; x++) for (int z = 1; z <= 16; z++) for (int y = -64; y <= -61; y++)
                    p.serverLevel().setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2); });
            case 274 -> look(mc, 0, -18);
            case 282 -> server(mc, ForceFieldService::bridge);
            case 283 -> shot("04-bridge-form-a");
            case 287 -> shot("04-bridge-form-b");
            case 295 -> shot("04-bridge-held");
            case 296 -> camera(mc, CameraType.THIRD_PERSON_FRONT);
            case 299 -> shot("04-bridge-front");
            case 300 -> { camera(mc, CameraType.THIRD_PERSON_BACK); mc.options.keyUp.setDown(true); }
            case 330 -> { mc.options.keyUp.setDown(false); }
            case 332 -> shot("04-bridge-walked");
            case 334 -> server(mc, p -> look(mc, 90, 25));
            case 338 -> shot("04-bridge-side");

            // 4. Bubble round a foe, then Crush.
            case 350 -> server(mc, p -> { place(p, 720, 0, 0, 6); spawn(p, EntityType.PILLAGER, 722, 6); });
            case 354 -> look(mc, -18, 6);
            case 360 -> server(mc, ForceFieldService::bubble);
            case 362 -> shot("05-bubble-a");
            case 363 -> look(mc, -50, 6);
            case 368 -> shot("05-bubble-held");
            case 369 -> { look(mc, -18, 6); camera(mc, CameraType.FIRST_PERSON); }
            case 372 -> shot("05-bubble-first-person");
            case 373 -> { camera(mc, CameraType.THIRD_PERSON_BACK); look(mc, -45, 6); }
            case 380 -> server(mc, ForceFieldService::crush);
            case 382 -> shot("06-crush-a");
            case 385 -> shot("06-crush-b");
            case 388 -> shot("06-crush-pop");
            case 395 -> server(mc, p -> clear());

            // 5. A Push sends a held bubble rolling into the foes behind it.
            case 400 -> server(mc, p -> { place(p, 760, 0, 0, 6);
                spawn(p, EntityType.PILLAGER, 760, 4); husk(p, 760.5, 13); husk(p, 759, 15); });
            case 404 -> look(mc, 0, 6);
            case 410 -> server(mc, p -> ForceFieldService.contain(p, MOBS.getFirst(), 100, false));
            case 412 -> server(mc, ForceFieldService::raiseShield);
            case 418 -> server(mc, ForceFieldService::push);
            case 419 -> look(mc, -40, 6);
            case 421 -> shot("07-roll-a");
            case 425 -> shot("07-roll-b");
            case 430 -> shot("07-roll-c");
            case 440 -> server(mc, p -> clear());

            // 6. Shelter bubbles round you and your wolf, an arrow stopped at the shell.
            case 445 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); server(mc, p -> { place(p, 800, 0, 0, -60);
                var wolf = spawn(p, EntityType.WOLF, 801.5, 0.5); wolf.tame(p); spawn(p, EntityType.PILLAGER, 800, 9); }); }
            case 449 -> look(mc, 0, -60);
            case 455 -> server(mc, ForceFieldService::bubble);
            case 458 -> server(mc, p -> shoot(p, MOBS.getLast(), p.position().add(0, 1, 0)));
            case 460 -> look(mc, 0, 8);
            case 463 -> shot("08-shelter-a");
            case 470 -> shot("08-shelter-b");
            case 480 -> server(mc, p -> clear());

            // 7. Force Nova: a bridge and a barrier up, a foe held, a ring of foes; the wind-up, then the burst.
            case 485 -> { camera(mc, CameraType.THIRD_PERSON_BACK); server(mc, p -> { place(p, 840, 0, 0, 30);
                for (double[] at : new double[][] {{846, 2}, {834, 3}, {840, 8}, {843, -6}, {836, -5}}) spawn(p, EntityType.PILLAGER, at[0], at[1]);
                learn(p, InvisibilitySkills.FORCE_DISC); }); }
            case 488 -> look(mc, 0, 30);
            case 492 -> server(mc, p -> { ForceFieldService.barrier(p); ForceFieldService.contain(p, MOBS.get(2), 100, false); });
            case 496 -> look(mc, 0, 8);
            case 500 -> server(mc, ForceFieldService::nova);
            case 504 -> shot("09-nova-charge-a");
            case 514 -> shot("09-nova-charge-b");
            case 518 -> shot("09-nova-charge-c");
            case 521 -> shot("09-nova-burst-a");
            case 523 -> shot("09-nova-burst-b");
            case 527 -> shot("09-nova-burst-c");
            case 535 -> shot("09-nova-after");
            case 540 -> camera(mc, CameraType.THIRD_PERSON_FRONT);
            case 542 -> shot("09-nova-vanished");
            case 548 -> server(mc, p -> { clear(); InvisibilityService.unfade(p); ForceFieldService.forget(p);
                learn(p, InvisibilitySkills.QUIET_LIGHT, InvisibilitySkills.UNSEEN_CONSTRUCTS, InvisibilitySkills.VANISHING_SQUAD,
                        InvisibilitySkills.LONG_VANISH, InvisibilitySkills.UNSEEN_FORCE, InvisibilitySkills.WIDE_LENS,
                        InvisibilitySkills.SAFETY_NET, InvisibilitySkills.FORCE_DISC, InvisibilitySkills.STEADY_DISC,
                        InvisibilitySkills.REBOUND, InvisibilitySkills.HARD_PUSH, InvisibilitySkills.TIGHT_SEAL,
                        InvisibilitySkills.KINETIC_SHELL, InvisibilitySkills.PRESSURE_CHAIN, InvisibilitySkills.IMPLOSION,
                        "invisibility_0_1", "invisibility_1_1", "invisibility_2_1"); });

            // Tree 1. Vanishing Touch on a villager.
            case 555 -> { camera(mc, CameraType.THIRD_PERSON_BACK); server(mc, p -> { place(p, 880, 0, 0, 8); spawn(p, EntityType.VILLAGER, 882, 5); }); }
            case 559 -> look(mc, -22, 8);
            case 563 -> shot("10-touch-before");
            case 565 -> server(mc, p -> InvisibilitySkills.skill(cast(p), 0));
            case 566 -> look(mc, -60, 8);
            case 568 -> shot("10-touch-a");
            case 574 -> shot("10-touch-b");
            case 584 -> shot("10-touch-gone");
            case 586 -> server(mc, p -> { MOBS.forEach(InvisibilityService::unhide); clear(); });

            // Tree 2. Vanishing Squad with two wolves.
            case 590 -> { camera(mc, CameraType.THIRD_PERSON_FRONT); server(mc, p -> { place(p, 920, 0, 0, 10);
                spawn(p, EntityType.WOLF, 922, 1).tame(p); spawn(p, EntityType.WOLF, 918, 2).tame(p); }); }
            case 594 -> shot("11-squad-before");
            case 596 -> server(mc, p -> InvisibilitySkills.technique(cast(p), "smoke_and_mirrors"));
            case 599 -> shot("11-squad-a");
            case 604 -> shot("11-squad-b");
            case 612 -> server(mc, p -> { MOBS.forEach(InvisibilityService::unhide); InvisibilityService.unfade(p); clear(); });

            // Tree 3. Force Barrier with Rebound: arrows struck off it.
            case 616 -> { camera(mc, CameraType.THIRD_PERSON_BACK); server(mc, p -> { place(p, 960, 0, 0, 35); spawn(p, EntityType.PILLAGER, 960, 12); }); }
            case 619 -> look(mc, 0, 35);
            case 623 -> server(mc, p -> InvisibilitySkills.skill(cast(p), 1));
            case 625 -> shot("12-barrier-form");
            case 627 -> look(mc, 0, 4);
            case 630 -> server(mc, p -> shoot(p, MOBS.getLast(), p.getEyePosition()));
            case 636 -> shot("12-barrier-struck");
            case 640 -> look(mc, 70, 15);
            case 643 -> shot("12-barrier-side");
            case 648 -> server(mc, p -> { ForceFieldService.forget(p); clear(); });

            // Tree 4. Pinpoint pops a foe up; Kinetic Shell seals a group; one Crush with Pressure Chain.
            case 652 -> { camera(mc, CameraType.THIRD_PERSON_BACK); server(mc, p -> { place(p, 1000, 0, 0, 4); husk(p, 1000, 9); }); }
            case 656 -> look(mc, 0, 4);
            case 660 -> server(mc, p -> InvisibilitySkills.skill(cast(p), 2));
            case 661 -> { shot("13-pinpoint-a"); look(mc, -30, -5); }
            case 663 -> shot("13-pinpoint-b");
            case 668 -> shot("13-pinpoint-pop");
            case 675 -> server(mc, p -> { clear(); place(p, 1040, 0, 0, 22);
                for (double[] at : new double[][] {{1038.5, 7}, {1041.5, 7.5}, {1040, 9.5}, {1043, 10}}) spawn(p, EntityType.PILLAGER, at[0], at[1]); });
            case 679 -> look(mc, 0, 22);
            case 684 -> server(mc, p -> InvisibilitySkills.technique(cast(p), "cloak_field"));
            case 686 -> look(mc, 0, 6);
            case 690 -> shot("14-shell-a");
            case 696 -> shot("14-shell-held");
            case 698 -> server(mc, ForceFieldService::crush);
            case 702 -> shot("14-shell-crush");
            case 707 -> shot("14-shell-chain");
            case 715 -> server(mc, p -> clear());

            // Tree 5. Force Disc ride.
            case 720 -> server(mc, p -> { place(p, 1080, 0, 0, -8); });
            case 724 -> look(mc, 0, -8);
            case 728 -> server(mc, p -> InvisibilitySkills.technique(cast(p), "pickpocket"));
            case 734 -> shot("15-disc-a");
            case 742 -> camera(mc, CameraType.THIRD_PERSON_FRONT);
            case 745 -> shot("15-disc-front");
            case 748 -> look(mc, 0, 20);
            case 752 -> shot("15-disc-down");
            case 756 -> server(mc, ForceFieldService::endDisc);

            // Tree 6. Safety Net: dropped from 14 blocks.
            case 770 -> { camera(mc, CameraType.THIRD_PERSON_BACK); server(mc, p -> { p.removeEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING); p.resetFallDistance();
                p.teleportTo(p.serverLevel(), 1120.5, GROUND + 14, .5, 0, 40); }); }
            case 773 -> look(mc, 0, 40);
            case 802 -> server(mc, p -> System.out.println("FORCE_NET health=" + p.getHealth()));

            // Tree 7. Implosion Nova.
            case 805 -> server(mc, p -> { place(p, 1160, 0, 0, 25);
                for (double[] at : new double[][] {{1166, 9}, {1150, 6}, {1162, -11}, {1171, -1}, {1158, 13}}) spawn(p, EntityType.PILLAGER, at[0], at[1]); });
            case 809 -> look(mc, 0, 25);
            case 812 -> server(mc, ForceFieldService::nova);
            case 818 -> shot("17-implosion-a");
            case 828 -> shot("17-implosion-b");
            case 833 -> shot("17-implosion-burst");
            case 840 -> server(mc, p -> { clear(); InvisibilityService.forget(p); });
            case 846 -> mc.options.hideGui = false;
            case 850 -> server(mc, p -> { place(p, 1200, 0, 0, 5); InvisibilityService.toggleFade(p); });
            case 856 -> shot("18-hud");
            case 870 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("FORCE_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
