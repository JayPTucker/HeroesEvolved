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
 * The twelve signatures run by {@code expansion/Signatures}, each staged with real foes in the Overworld at noon and
 * photographed from the side at the moments its own class names: Epicenter, Microburst, Puppeteer, Fission Star,
 * Standing Wave, Lightning Round, Bastion, Mountain Armor, Chorus, Ballista, Leviathan and Overdrive (with their second
 * presses). Flag {@code heroes.preview.sigset}; shots in {@code build/sigset-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class SignaturesPreview {
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
            } catch (Throwable t) { PreviewQuiet.report("SIG_SET FAIL " + t); }
        });
    }
    private static void check(String name, boolean ok) { PreviewQuiet.report("SIG_SET " + (ok ? "PASS " : "FAIL ") + name); }
    private static void photo(String name) { pending = "sigset-" + name + ".png"; expected++; }
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
    static void reset(ServerPlayer p) {
        com.jayptucker.heroesevolved.expansion.Signatures.forget(p);
        for (Mob mob : ACTORS) mob.discard();
        ACTORS.clear();
        refill(p);
    }

    /** One staged signature: who casts it, how it is set up, when its shots are taken, and when the second press comes. */
    private record Scene(String name, net.minecraft.resources.ResourceLocation power, String node, Consumer<ServerPlayer> stage,
                         Vec3 eye, Vec3 look, int[] shots, int press, String mechanic) {}

    private static final Map<Integer, Runnable> EVENTS = new java.util.HashMap<>();
    private static final int START = 240, SPAN = 100;

    private static Vec3 at(double dx, double dy, double dz) { return new Vec3(X + dx, Y + dy, Z + dz); }

    private static void grant(ServerPlayer p, net.minecraft.resources.ResourceLocation power, String node) {
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(power));
        PlayerProgressionService.setLevel(p, 5);
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(power.toString(), new SkillTreeData.Build(15, Set.of(node), Map.of())));
        com.jayptucker.heroesevolved.network.PlayerPowerSyncService.sync(p);
    }

    private static List<Scene> scenes() {
        List<Scene> s = new ArrayList<>();
        int i = 0;
        double d = 0;
        // Epicenter: foes in the ring; the faults warn, the plates heave, the ground drops back.
        d = 30 * i++;
        final double d0 = d;
        s.add(new Scene("epicenter", ModAbilities.QUAKE_ID, "quake_epicenter", p -> {
            place(p, d0, 0, 0, 0);
            puppet(p, EntityType.PILLAGER, d0 + 5, 2); puppet(p, EntityType.VINDICATOR, d0 - 4, 4); puppet(p, EntityType.HUSK, d0 + 1, -6);
            check("epicenter", com.jayptucker.heroesevolved.quake.Epicenter.activate(p, 5));
        }, at(d0 + 11, 7, -10), at(d0, 0, 0), new int[] {14, 26, 40}, -1, null));
        // Microburst: the lid over a spot eight blocks out, the column, the outflow.
        d = 30 * i++;
        final double d1 = d;
        s.add(new Scene("microburst", ModAbilities.FLIGHT_ID, "flight_microburst", p -> {
            place(p, d1, -2, 0, 0); aim(p, at(d1, -.5, 7));
            puppet(p, EntityType.PILLAGER, d1 + 2, 7); puppet(p, EntityType.VINDICATOR, d1 - 2, 8); puppet(p, EntityType.HUSK, d1 + 6, 12);
            check("microburst", com.jayptucker.heroesevolved.flight.Microburst.activate(p, 5));
        }, at(d1 + 15, 9, -4), at(d1, 5, 7), new int[] {12, 21, 32}, -1, null));
        // Puppeteer: strings on three foes, then cut.
        d = 30 * i++;
        final double d2 = d;
        s.add(new Scene("puppeteer", ModAbilities.TELEPATHY_ID, "telepathy_puppeteer", p -> {
            place(p, d2, 0, 0, 0);
            puppet(p, EntityType.PILLAGER, d2 - 2.5, 6); puppet(p, EntityType.VINDICATOR, d2 + 2.5, 6.5); puppet(p, EntityType.HUSK, d2, 9);
            check("puppeteer", com.jayptucker.heroesevolved.telepathy.Puppeteer.activate(p, 5));
        }, at(d2 + 7, 3, 0), at(d2, 1.6, 6), new int[] {8, 40, 53}, 50, "puppeteer"));
        // Fission Star: the core grows, flies, and goes off on a foe.
        d = 30 * i++;
        final double d3 = d;
        s.add(new Scene("fission-star", ModAbilities.RADIATION_ID, "radiation_fission_star", p -> {
            place(p, d3, 0, 0, 0);
            puppet(p, EntityType.PILLAGER, d3, 10); puppet(p, EntityType.VINDICATOR, d3 + 2, 11.5);
            check("fission star", com.jayptucker.heroesevolved.radiation.FissionStar.activate(p, 5));
        }, at(d3 + 13, 4, 2), at(d3, 1.5, 6), new int[] {22, 36, 43, 52}, -1, null));
        // Standing Wave: the column, a beat, the swell, the break.
        d = 30 * i++;
        final double d4 = d;
        s.add(new Scene("standing-wave", ModAbilities.SONIC_RESONANCE_ID, "sonic_resonance_standing_wave", p -> {
            place(p, d4, -1, 0, 0); aim(p, at(d4, -.5, 7));
            puppet(p, EntityType.PILLAGER, d4 + 3, 7); puppet(p, EntityType.VINDICATOR, d4 - 2, 9); puppet(p, EntityType.HUSK, d4 + 1, 4);
            check("standing wave", com.jayptucker.heroesevolved.sonic.StandingWave.activate(p, 5));
        }, at(d4 + 10, 4, 1), at(d4, 2.5, 7), new int[] {8, 22, 64, 73}, 70, "standing_wave"));
        // Lightning Round: four foes visited and home again.
        d = 30 * i++;
        final double d5 = d;
        s.add(new Scene("lightning-round", ModAbilities.SUPERSPEED_ID, "superspeed_lightning_round", p -> {
            place(p, d5, 0, 0, 0);
            puppet(p, EntityType.PILLAGER, d5, 6); puppet(p, EntityType.VINDICATOR, d5 + 5, 5); puppet(p, EntityType.HUSK, d5 - 5, 4);
            puppet(p, EntityType.PILLAGER, d5 + 3, 10);
            check("lightning round", com.jayptucker.heroesevolved.speed.LightningRound.activate(p, 5));
        }, at(d5 + 7, 4, -1), at(d5, .5, 5), new int[] {5, 9, 17}, -1, null));
        // Bastion: the dome rising, a shot glancing off it, the burst.
        d = 30 * i++;
        final double d6 = d;
        s.add(new Scene("bastion", ModAbilities.INVISIBILITY_ID, "invisibility_bastion", p -> {
            place(p, d6, 0, 0, 0);
            Mob archer = puppet(p, EntityType.PILLAGER, d6, 12); puppet(p, EntityType.VINDICATOR, d6 + 2, 1); puppet(p, EntityType.HUSK, d6 - 7, 3);
            check("bastion", com.jayptucker.heroesevolved.invisibility.Bastion.activate(p, 5));
            for (int k = 0; k < 3; k++) {
                Arrow arrow = new Arrow(EntityType.ARROW, p.serverLevel());
                arrow.setOwner(archer); arrow.setNoGravity(true);
                arrow.setPos(at(d6 - 2 + 2 * k, 1.6 + k * .7, 9 + k * 4));
                arrow.setDeltaMovement(0, 0, -1.3);
                p.serverLevel().addFreshEntity(arrow);
            }
        }, at(d6 + 12, 5, -9), at(d6, 2, 0), new int[] {4, 9, 30, 43}, 40, "bastion"));
        // Mountain Armor: the plates flying on, worn, blasting off.
        d = 30 * i++;
        final double d7 = d;
        s.add(new Scene("mountain-armor", ModAbilities.PHASING_ID, "phasing_mountain_armor", p -> {
            place(p, d7, 0, 0, 0);
            puppet(p, EntityType.PILLAGER, d7 + 1.5, 3); puppet(p, EntityType.HUSK, d7 - 2, 2.5);
            check("mountain armor", com.jayptucker.heroesevolved.phasing.MountainArmor.activate(p, 5));
        }, at(d7 + 3.2, 1.9, 3.6), at(d7, 1.1, 0), new int[] {5, 8, 16, 33}, 30, "mountain_armor"));
        // Chorus: the ring of voices, one voice, all together.
        d = 30 * i++;
        final double d8 = d;
        s.add(new Scene("chorus", ModAbilities.MIMICRY_ID, "empathic_mimicry_chorus", p -> {
            place(p, d8, 0, 0, 0);
            for (var power : List.of(ModAbilities.PYROKINESIS_ID, ModAbilities.CRYOKINESIS_ID, ModAbilities.ELECTROKINESIS_ID, ModAbilities.TELEKINESIS_ID, ModAbilities.FLIGHT_ID))
                com.jayptucker.heroesevolved.mimicry.MimicryService.remember(p, power, 4);
            puppet(p, EntityType.PILLAGER, d8, 7); puppet(p, EntityType.VINDICATOR, d8 + 3, 9); puppet(p, EntityType.HUSK, d8 - 3, 8);
            check("chorus", com.jayptucker.heroesevolved.mimicry.Chorus.activate(p, 5));
        }, at(d8 + 4, 2.4, -3), at(d8, 1.4, 2), new int[] {10, 22, 52}, 50, "chorus"));
        // Ballista: the blueprint and the parts flying in, built and shooting, folding home.
        d = 30 * i++;
        final double d9 = d;
        s.add(new Scene("ballista", ModAbilities.MATTER_ASSEMBLY_ID, "matter_assembly_ballista", p -> {
            place(p, d9, 0, 0, 0); com.jayptucker.heroesevolved.assembly.Matter.fill(p); aim(p, at(d9 + 1, -.5, 5));
            puppet(p, EntityType.PILLAGER, d9 + 2, 13); puppet(p, EntityType.VINDICATOR, d9 - 3, 14);
            check("ballista", com.jayptucker.heroesevolved.assembly.Ballista.activate(p, 5));
        }, at(d9 + 6, 2.6, 1.5), at(d9 + 1, 1, 6), new int[] {9, 15, 21, 41, 64}, 60, "ballista"));
        // Leviathan: rising, rearing over a marked spot, the bite, the dive.
        d = 30 * i++;
        final double d10 = d;
        s.add(new Scene("leviathan", ModAbilities.HYDROKINESIS_ID, "hydrokinesis_leviathan", p -> {
            place(p, d10, -2, 0, 0); aim(p, at(d10, -.5, 6));
            puppet(p, EntityType.PILLAGER, d10 + 3, 10); puppet(p, EntityType.VINDICATOR, d10 - 3, 11);
            check("leviathan", com.jayptucker.heroesevolved.hydro.Leviathan.activate(p, 5));
        }, at(d10 + 11, 4, 3), at(d10, 2.2, 8), new int[] {8, 19, 26, 60, 72}, -1, null));
        // Overdrive: the web over a friend and himself, then the forks.
        d = 30 * i++;
        final double d11 = d;
        s.add(new Scene("overdrive", ModAbilities.AMPLIFICATION_ID, "power_amplification_overdrive", p -> {
            place(p, d11, 0, 0, 0);
            Mob wolf = puppet(p, EntityType.WOLF, d11 + 2, 0);
            if (wolf instanceof net.minecraft.world.entity.animal.Wolf w) w.tame(p);
            puppet(p, EntityType.PILLAGER, d11, 7); puppet(p, EntityType.VINDICATOR, d11 + 4, 8);
            check("overdrive", com.jayptucker.heroesevolved.amplify.Overdrive.activate(p, 5));
        }, at(d11 + 5, 2.5, -4), at(d11 + 1, 1, 2), new int[] {10, 30, 42}, 40, "overdrive"));
        return s;
    }

    private static void schedule(Minecraft mc) {
        List<Scene> list = scenes();
        for (int i = 0; i < list.size(); i++) {
            Scene sc = list.get(i);
            int t0 = START + i * SPAN;
            EVENTS.put(t0 - 6, () -> server(mc, p -> { reset(p); grant(p, sc.power(), sc.node()); }));
            EVENTS.put(t0, () -> {
                cleanHud = true; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                TempestCamera.fix(sc.eye(), sc.look());
                server(mc, sc.stage());
            });
            int n = 1;
            for (int shot : sc.shots()) {
                String file = String.format("%02d-%s-%d", i + 1, sc.name(), n++);
                EVENTS.merge(t0 + shot, () -> photo(file), (a, b) -> () -> { a.run(); b.run(); });
            }
            if (sc.press() >= 0) EVENTS.merge(t0 + sc.press(), () -> server(mc, p -> check(sc.name() + " second press",
                    com.jayptucker.heroesevolved.expansion.Signatures.press(p, sc.mechanic()))), (a, b) -> () -> { a.run(); b.run(); });
        }
        EVENTS.put(START + list.size() * SPAN, () -> server(mc, SignaturesPreview::reset));
        EVENTS.put(START + list.size() * SPAN + 10, () -> { TempestCamera.fix(null, null); check("all photographs", photos == expected); PreviewQuiet.quit(); });
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.sigset")) return;
        var mc = Minecraft.getInstance();
        if (!started && ++ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true; mc.options.pauseOnLostFocus = false; mc.options.guiScale().set(2); mc.options.fov().set(80);
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Signatures-" + System.currentTimeMillis(),
                    new LevelSettings("Signatures preview", GameType.SURVIVAL, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        mc.getToasts().clear();
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        ++world;
        if (world == 160) {
            schedule(mc);
            server(mc, p -> {
                PrologueService.skip(p); CryoChamberService.release(p);
                for (int cx = 123; cx <= 150; cx++) for (int cz = -2; cz <= 2; cz++) p.server.overworld().setChunkForced(cx, cz, true);
                p.server.overworld().setDayTime(6000);
                p.setInvulnerable(true);
                place(p, 0, 0, 0, 0);
                PreviewQuiet.report("SIG_SET staged in " + p.level().dimension().location() + " on " + p.level().getBlockState(p.blockPosition().below()));
            });
        }
        Runnable scheduled = EVENTS.remove(world);
        if (scheduled != null) scheduled.run();
    }

    @SubscribeEvent public static void hud(net.neoforged.neoforge.client.event.RenderGuiEvent.Pre event) {
        if (cleanHud && Boolean.getBoolean("heroes.preview.sigset")) event.setCanceled(true);
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            var path = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(path.getParent()); image.writeToFile(path); photos++;
            System.out.println("SIG_SET_CAPTURE " + pending);
        } catch (Exception ex) { PreviewQuiet.report("SIG_SET FAIL screenshot " + ex); }
        pending = null;
    }
}
