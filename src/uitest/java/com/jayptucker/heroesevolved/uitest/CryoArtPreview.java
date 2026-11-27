package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Cryokinesis drawn in one palette by CryoArt, photographed in the Overworld at noon from side cameras: Ice Blast, a
 * sampler of the frost effects that used to be particles (bolt, chill, freeze, shatter, fracture, branch), Crystal Prison
 * and its shatter, Whiteout, a Glacial Sculpt wall, the Ice Skater's jab, and the signature Absolute Zero (the embers
 * drawn out, the freeze). Flag {@code heroes.preview.cryoart}; shots in {@code build/cryoart-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class CryoArtPreview {
    private static boolean started;
    private static int ticks, world, photos, expected;
    private static String pending;
    private static boolean cleanHud;
    private static final double X = 2000.5, Y = -60, Z = .5;
    private static final List<Mob> ACTORS = new ArrayList<>();

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (player != null) action.accept(player);
            } catch (Throwable t) { PreviewQuiet.report("CRYO_ART FAIL " + t); }
        });
    }
    private static void check(String name, boolean ok) { PreviewQuiet.report("CRYO_ART " + (ok ? "PASS " : "FAIL ") + name); }
    private static void photo(String name) { pending = "cryoart-" + name + ".png"; expected++; }
    private static void refill(ServerPlayer p) { PlayerEnergyService.restore(p, 999); com.jayptucker.heroesevolved.cooldown.CooldownService.clearAll(p); }
    private static void place(ServerPlayer p, double dx, double dz, float yaw, float pitch) {
        p.teleportTo(p.server.overworld(), X + dx, Y, Z + dz, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO);
    }
    private static void aim(ServerPlayer p, Vec3 point) {
        Vec3 d = point.subtract(p.getEyePosition());
        p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), (float) Math.toDegrees(Math.atan2(-d.x, d.z)),
                (float) Math.toDegrees(-Math.atan2(d.y, Math.hypot(d.x, d.z))));
    }
    /** A foe that obeys physics (so the far hand can move it) but stands still: AI on, every goal removed. */
    private static Mob puppet(ServerPlayer p, EntityType<? extends Mob> type, double dx, double dz) {
        Mob mob = type.create(p.serverLevel());
        mob.moveTo(X + dx, Y, Z + dz, 180, 0); mob.setPersistenceRequired();
        mob.getPersistentData().putBoolean(com.jayptucker.heroesevolved.gifted.GiftedMobs.ROLLED, true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400); mob.setHealth(400);
        p.serverLevel().addFreshEntity(mob);
        mob.goalSelector.removeAllGoals(goal -> true);
        mob.targetSelector.removeAllGoals(goal -> true);
        ACTORS.add(mob);
        return mob;
    }
    private static void reset(ServerPlayer p) {
        com.jayptucker.heroesevolved.cryo.CryoBlastService.stop(p);
        com.jayptucker.heroesevolved.cryo.CryoService.stopAll(p);
        com.jayptucker.heroesevolved.cryo.CryoKitService.forget(p);
        for (Mob mob : ACTORS) mob.discard();
        ACTORS.clear();
        refill(p);
    }

    private static void fx(ServerPlayer p, com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind kind, Vec3 a, Vec3 b, double size) {
        com.jayptucker.heroesevolved.effects.AbilityEffects.send(p.serverLevel(), kind, a, b, size);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.cryoart")) return;
        var mc = Minecraft.getInstance();
        if (!started && ++ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true; mc.options.pauseOnLostFocus = false; mc.options.guiScale().set(2); mc.options.fov().set(80);
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("CryoArt-" + System.currentTimeMillis(),
                    new LevelSettings("Cryo preview", GameType.SURVIVAL, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        mc.getToasts().clear();
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        switch (++world) {
            case 160 -> server(mc, p -> {
                PrologueService.skip(p); CryoChamberService.release(p);
                for (int cx = 122; cx <= 130; cx++) for (int cz = -3; cz <= 4; cz++) p.server.overworld().setChunkForced(cx, cz, true);
                p.server.overworld().setDayTime(6000);
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                        .assignDormant(ModAbilities.CRYOKINESIS_ID).unlock(ModAbilities.CRYOKINESIS_ID));
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.CRYOKINESIS_ID));
                PlayerProgressionService.setLevel(p, 5);
                p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.CRYOKINESIS_ID.toString(),
                        new SkillTreeData.Build(15, Set.of("cryokinesis_absolute_zero"), Map.of())));
                com.jayptucker.heroesevolved.network.PlayerPowerSyncService.sync(p);
                place(p, 0, 0, 0, 5);
                PreviewQuiet.report("CRYO_ART staged in " + p.level().dimension().location() + " on " + p.level().getBlockState(p.blockPosition().below()));
            });
            // ---- Ice Blast at a husk, from the side.
            case 240 -> { cleanHud = true; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); server(mc, p -> {
                reset(p); place(p, 0, 0, 0, 4);
                Mob husk = puppet(p, EntityType.HUSK, 0, 7);
                aim(p, husk.getBoundingBox().getCenter());
                check("ice blast", com.jayptucker.heroesevolved.cryo.CryoBlastService.start(p, 5));
            }); TempestCamera.fix(new Vec3(X + 6, Y + 2.6, Z + 3), new Vec3(X, Y + 1.3, Z + 4.5)); }
            case 252 -> photo("01-ice-blast");
            case 262 -> photo("02-ice-blast-frost");
            case 266 -> server(mc, p -> com.jayptucker.heroesevolved.cryo.CryoBlastService.stop(p));
            // ---- The effects that used to be particles, now CryoArt's shapes.
            case 290 -> server(mc, p -> {
                reset(p); place(p, 14, 0, 0, 0);
                Vec3 eye = p.getEyePosition();
                fx(p, com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.CRYO_BOLT, eye.add(.3, -.3, .6), new Vec3(X + 14, Y + 1, Z + 9), 1);
                fx(p, com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.CRYO_CHILL, new Vec3(X + 17, Y, Z + 5), new Vec3(X + 17, Y + 1.8, Z + 5), 1.5);
                Mob frozen = puppet(p, EntityType.PILLAGER, 11, 5);
                fx(p, com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.CRYO_FREEZE, frozen.position(), frozen.getEyePosition(), 1.2);
                fx(p, com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.CRYO_FRACTURE, new Vec3(X + 14, Y, Z + 12), new Vec3(X + 14, Y + 1, Z + 12), 2.5);
                fx(p, com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.CRYO_BRANCH, new Vec3(X + 10, Y + .05, Z + 9), new Vec3(X + 18, Y + .05, Z + 9), .5);
            });
            case 291 -> TempestCamera.fix(new Vec3(X + 21, Y + 5, Z + 2), new Vec3(X + 14, Y + .8, Z + 7.5));
            case 297 -> photo("03-fx-sampler");
            case 303 -> photo("04-fx-sampler-late");
            case 320 -> server(mc, p -> fx(p, com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.CRYO_SHATTER,
                    new Vec3(X + 11, Y, Z + 5), new Vec3(X + 11, Y + 1.9, Z + 5), 1.2));
            case 324 -> photo("05-shatter");
            // ---- Crystal Prison: two casts form the shell, then it shatters.
            case 350 -> server(mc, p -> {
                reset(p); place(p, 28, 0, 0, 6);
                Mob foe = puppet(p, EntityType.PILLAGER, 28, 5);
                aim(p, foe.getBoundingBox().getCenter());
                com.jayptucker.heroesevolved.cryo.CryoKitService.prison(p, 5); refill(p);
                check("prison", com.jayptucker.heroesevolved.cryo.CryoKitService.prison(p, 5));
                TempestCamera.fix(new Vec3(X + 32, Y + 2.6, Z + 2.5), new Vec3(X + 28, Y + 1.1, Z + 5));
            });
            case 362 -> photo("06-prison");
            case 366 -> server(mc, p -> check("prison shatters", com.jayptucker.heroesevolved.cryo.CryoKitService.shatterPrison(p)));
            case 370 -> photo("07-prison-shatter");
            // ---- Whiteout.
            case 400 -> server(mc, p -> {
                reset(p); place(p, 44, 0, 0, 25);
                puppet(p, EntityType.PILLAGER, 44, 7); puppet(p, EntityType.PILLAGER, 47, 9);
                check("whiteout", com.jayptucker.heroesevolved.cryo.CryoKitService.whiteout(p, 5));
                TempestCamera.fix(new Vec3(X + 56, Y + 8, Z - 4), new Vec3(X + 44, Y + 1, Z + 8));
            });
            case 430 -> photo("08-whiteout");
            case 460 -> photo("09-whiteout-late");
            // ---- Glacial Sculpt: a frost wall.
            case 480 -> server(mc, p -> {
                reset(p); place(p, 60, 0, 0, 25);
                check("sculpt", com.jayptucker.heroesevolved.cryo.CryoKitService.sculpt(p, 5, false));
                TempestCamera.fix(new Vec3(X + 66, Y + 3.5, Z + 1), new Vec3(X + 60, Y + 1, Z + 4));
            });
            case 505 -> photo("10-sculpt-wall");
            // ---- The Ice Skater's jab.
            case 530 -> server(mc, p -> {
                reset(p); place(p, 74, 0, 0, 4);
                Mob foe = puppet(p, EntityType.PILLAGER, 74, 3);
                aim(p, foe.getBoundingBox().getCenter());
                com.jayptucker.heroesevolved.cryo.CryoStyle.STYLE.strike(p);
                TempestCamera.fix(new Vec3(X + 78, Y + 2.5, Z + 1), new Vec3(X + 74, Y + 1.2, Z + 2.5));
            });
            case 534 -> photo("11-skater-jab");
            // ---- Absolute Zero: four foes give up their warmth, then freeze.
            case 560 -> server(mc, p -> {
                reset(p); place(p, 90, 0, 0, 0);
                puppet(p, EntityType.PILLAGER, 94, 4); puppet(p, EntityType.VINDICATOR, 87, 5);
                puppet(p, EntityType.HUSK, 90, 8); puppet(p, EntityType.PILLAGER, 85, -2);
                check("absolute zero", com.jayptucker.heroesevolved.cryo.AbsoluteZero.activate(p, 5));
                TempestCamera.fix(new Vec3(X + 101, Y + 6, Z - 6), new Vec3(X + 90, Y + 1.2, Z + 2));
            });
            case 575 -> photo("12-zero-drawing");
            case 586 -> photo("13-zero-drawn");
            case 594 -> photo("14-zero-freeze");
            case 604 -> photo("15-zero-after");
            case 640 -> { TempestCamera.fix(null, null); check("all photographs", photos == expected); PreviewQuiet.quit(); }
            default -> { }
        }
    }

    @SubscribeEvent public static void hud(net.neoforged.neoforge.client.event.RenderGuiEvent.Pre event) {
        if (cleanHud && Boolean.getBoolean("heroes.preview.cryoart")) event.setCanceled(true);
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            var path = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(path.getParent()); image.writeToFile(path); photos++;
            System.out.println("CRYO_ART_CAPTURE " + pending);
        } catch (Exception ex) { PreviewQuiet.report("CRYO_ART FAIL screenshot " + ex); }
        pending = null;
    }
}
