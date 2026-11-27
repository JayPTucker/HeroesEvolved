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
 * Regeneration drawn by RegenArt, photographed in the Overworld at noon: Regrowth glowing in the veins at three levels,
 * Rapid Mending, Blood Gift and a Bond, a Second Life, a revive, a reckless punch and a bullet pushed out, and the
 * signature Last One Standing (foes drawn onto her, the release). Flag {@code heroes.preview.regenart}; shots in
 * {@code build/regenart-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class RegenArtPreview {
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
            } catch (Throwable t) { PreviewQuiet.report("REGEN_ART FAIL " + t); }
        });
    }
    private static void check(String name, boolean ok) { PreviewQuiet.report("REGEN_ART " + (ok ? "PASS " : "FAIL ") + name); }
    private static void photo(String name) { pending = "regenart-" + name + ".png"; expected++; }
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
        com.jayptucker.heroesevolved.regeneration.LastOneStanding.forget(p);
        for (Mob mob : ACTORS) mob.discard();
        ACTORS.clear();
        refill(p);
    }

    private static void fx(ServerPlayer p, com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind kind, Vec3 a, Vec3 b, double size) {
        com.jayptucker.heroesevolved.effects.AbilityEffects.send(p.serverLevel(), kind, a, b, size);
    }

    private static void regrowth(ServerPlayer p, int amount) {
        var service = com.jayptucker.heroesevolved.regeneration.RegenerationService.class;
        com.jayptucker.heroesevolved.regeneration.RegenerationService.spendRegrowth(p, 100);
        com.jayptucker.heroesevolved.regeneration.RegenerationService.addRegrowth(p, amount);
        com.jayptucker.heroesevolved.regeneration.RegenerationService.broadcastRegrowth(p, true);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.regenart")) return;
        var mc = Minecraft.getInstance();
        if (!started && ++ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true; mc.options.pauseOnLostFocus = false; mc.options.guiScale().set(2); mc.options.fov().set(80);
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("RegenArt-" + System.currentTimeMillis(),
                    new LevelSettings("Regen preview", GameType.SURVIVAL, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        mc.getToasts().clear();
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        var K = com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.REGEN_MEND;
        switch (++world) {
            case 160 -> server(mc, p -> {
                PrologueService.skip(p); CryoChamberService.release(p);
                for (int cx = 122; cx <= 130; cx++) for (int cz = -3; cz <= 4; cz++) p.server.overworld().setChunkForced(cx, cz, true);
                p.server.overworld().setDayTime(6000);
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                        .assignDormant(ModAbilities.REGENERATION_ID).unlock(ModAbilities.REGENERATION_ID));
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.REGENERATION_ID));
                PlayerProgressionService.setLevel(p, 5);
                p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.REGENERATION_ID.toString(),
                        new SkillTreeData.Build(15, Set.of("regeneration_last_one_standing"), Map.of())));
                com.jayptucker.heroesevolved.network.PlayerPowerSyncService.sync(p);
                place(p, 0, 0, 180, 0);
                PreviewQuiet.report("REGEN_ART staged in " + p.level().dimension().location() + " on " + p.level().getBlockState(p.blockPosition().below()));
            });
            // ---- Regrowth in the veins: a little, a lot, full; seen from the front.
            case 240 -> { cleanHud = true; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); server(mc, p -> { reset(p); place(p, 0, 0, 180, 0); regrowth(p, 30); });
                TempestCamera.fix(new Vec3(X + .6, Y + 1.5, Z - 2.1), new Vec3(X, Y + 1.1, Z)); }
            case 262 -> photo("01-veins-30");
            case 265 -> server(mc, p -> regrowth(p, 70));
            case 300 -> photo("02-veins-70");
            case 303 -> server(mc, p -> regrowth(p, 100));
            case 330 -> photo("03-veins-100");
            case 332 -> TempestCamera.fix(new Vec3(X + 2.2, Y + 1.4, Z - .6), new Vec3(X, Y + 1.1, Z));
            case 340 -> photo("04-veins-side");
            // ---- Rapid Mending: blood back in, the gold seam rising, bones clicking home; the veins empty.
            case 350 -> server(mc, p -> { fx(p, K, p.position(), p.position().add(0, 1, 0), 1); regrowth(p, 0); });
            case 352 -> TempestCamera.fix(new Vec3(X + 3, Y + 2, Z - 2.5), new Vec3(X, Y + 1.1, Z));
            case 355 -> photo("05-mend-rushing");
            case 362 -> photo("06-mend-seam");
            // ---- Blood Gift's strand to a friend, a Bond's ring, a Second Life, a revive.
            case 390 -> server(mc, p -> {
                reset(p); place(p, 14, 0, 0, 0);
                Mob friend = puppet(p, EntityType.VILLAGER, 17, 5);
                fx(p, com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.BLOOD_THREAD, p.position().add(0, 1.2, .5), friend.position().add(0, 1.1, 0), 1);
                fx(p, com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.HEALING_AURA, p.position(), p.position().add(0, 1, 0), 6);
                TempestCamera.fix(new Vec3(X + 21, Y + 4, Z - 2), new Vec3(X + 15.5, Y + 1, Z + 3));
            });
            case 397 -> photo("07-blood-gift-and-bond");
            case 420 -> server(mc, p -> {
                Mob friend = ACTORS.getFirst();
                fx(p, com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.REGEN_SECOND_LIFE, friend.position(), friend.position().add(0, 1, 0), friend.getBbHeight());
            });
            case 436 -> photo("08-second-life");
            case 460 -> server(mc, p -> fx(p, com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.REGEN_REVIVE, p.position(), p.position().add(0, 1, 0), 1.8));
            case 466 -> photo("09-revive");
            // ---- A reckless punch, a bullet pushed out, a limb taken.
            case 490 -> server(mc, p -> {
                reset(p); place(p, 28, 0, 0, 0);
                Mob foe = puppet(p, EntityType.PILLAGER, 28, 1.6);
                Vec3 hit = foe.getBoundingBox().getCenter().add(0, 0, -.3);
                fx(p, com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.REGEN_RECKLESS, hit, p.getEyePosition(), 1);
                fx(p, com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.REGEN_EJECT, p.position().add(.3, 1.2, 0), p.position().add(1.3, 1.5, 0), 1);
                TempestCamera.fix(new Vec3(X + 31, Y + 2, Z - 1), new Vec3(X + 28, Y + 1.2, Z + .8));
            });
            case 494 -> photo("10-reckless-and-eject");
            // ---- Last One Standing: four foes drawn onto her, then let go.
            case 520 -> server(mc, p -> {
                reset(p); place(p, 44, 0, 0, 0);
                puppet(p, EntityType.PILLAGER, 48, 4); puppet(p, EntityType.VINDICATOR, 41, 5);
                puppet(p, EntityType.HUSK, 44, 8); puppet(p, EntityType.PILLAGER, 39, -2);
                check("last one standing", com.jayptucker.heroesevolved.regeneration.LastOneStanding.activate(p, 5));
                TempestCamera.fix(new Vec3(X + 55, Y + 6, Z - 6), new Vec3(X + 44, Y + 1.2, Z + 2));
            });
            case 545 -> photo("11-stand");
            case 565 -> photo("12-stand-late");
            case 570 -> server(mc, p -> check("let go", com.jayptucker.heroesevolved.regeneration.LastOneStanding.followUp(p)));
            case 574 -> photo("13-release");
            case 580 -> photo("14-release-late");
            case 620 -> { TempestCamera.fix(null, null); check("all photographs", photos == expected); PreviewQuiet.quit(); }
            default -> { }
        }
    }

    @SubscribeEvent public static void hud(net.neoforged.neoforge.client.event.RenderGuiEvent.Pre event) {
        if (cleanHud && Boolean.getBoolean("heroes.preview.regenart")) event.setCanceled(true);
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            var path = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(path.getParent()); image.writeToFile(path); photos++;
            System.out.println("REGEN_ART_CAPTURE " + pending);
        } catch (Exception ex) { PreviewQuiet.report("REGEN_ART FAIL screenshot " + ex); }
        pending = null;
    }
}
