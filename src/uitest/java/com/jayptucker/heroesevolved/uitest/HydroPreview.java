package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.combat.FightingStyles;
import com.jayptucker.heroesevolved.combat.FightingStyles.Dodge;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.electro.ElectroCombat;
import com.jayptucker.heroesevolved.electro.ElectroService;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.hydro.HydroCombat;
import com.jayptucker.heroesevolved.hydro.HydroService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Photographs Hydrokinesis (flag {@code heroes.preview.hydro}: the kit, waterbending, the tree and the classes) or
 * Electrokinesis's lightning style (flag {@code heroes.preview.lightning}) in a flat world well away from the spawn
 * bunker, from behind, the front, the side and first person. Shots go to {@code build/<name>-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class HydroPreview {
    private static final List<Mob> ACTORS = new ArrayList<>();
    private static boolean started;
    private static int startup, age;
    private static String capture;
    private static Vec3 anchor = Vec3.ZERO;

    private static boolean hydro() { return Boolean.getBoolean("heroes.preview.hydro"); }
    private static boolean lightning() { return Boolean.getBoolean("heroes.preview.lightning"); }
    private static boolean styles() { return Boolean.getBoolean("heroes.preview.fighting"); }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    private static AbilityUseContext context(ServerPlayer p, boolean shift) {
        return new AbilityUseContext(p, hydro() ? ModAbilities.HYDROKINESIS_ID : ModAbilities.ELECTROKINESIS_ID, 5, shift);
    }

    /**
     * The staging area: a few force-loaded chunks about 150 blocks from the spawn bunker. Every scene stands on one of
     * nine spots inside it (picked from the scene's number), so the ground and the actors are always loaded; hopping
     * hundreds of blocks between scenes left the player over unloaded void.
     */
    private static final int STAGE = 150, SPOT = 12;

    /** The overworld: a new player starts in the Last Day's city, which is another dimension. */
    private static net.minecraft.server.level.ServerLevel world(ServerPlayer player) { return player.getServer().overworld(); }

    private static void stage(ServerPlayer player) {
        var world = world(player);
        for (int cx = (STAGE - 16) >> 4; cx <= (STAGE + 3 * SPOT + 16) >> 4; cx++)
            for (int cz = (STAGE - 16) >> 4; cz <= (STAGE + 3 * SPOT + 16) >> 4; cz++)
                world.setChunkForced(cx, cz, true);
        player.teleportTo(world, STAGE + .5, -60, STAGE + .5, 0, 0);
        System.out.println("HYDRO_PREVIEW staged in " + player.level().dimension().location() + " at " + player.blockPosition()
                + " on " + world.getBlockState(player.blockPosition().below()));
    }

    private static void reset(ServerPlayer player, int scene, float pitch) {
        int slot = (scene / 20) % 9;
        double x = STAGE + (slot % 3) * SPOT + .5, z = STAGE + (slot / 3) * SPOT + .5;
        HydroService.forget(player);
        ElectroService.forget(player);
        FightingStyles.forget(player);
        CooldownService.clearAll(player);
        PlayerEnergyService.restore(player, 999);
        HydroService.fill(player, 200);
        ACTORS.forEach(Mob::discard);
        ACTORS.clear();
        world(player).setDayTime(6000);
        player.teleportTo(world(player), x, -60, z, 0, pitch);
        player.setDeltaMovement(Vec3.ZERO);
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        anchor = new Vec3(x, -60, z);
        Minecraft.getInstance().execute(() -> TempestCamera.fix(null, null));
    }

    private static Mob actor(ServerPlayer player, EntityType<? extends Mob> type, double dx, double dz) {
        Mob actor = type.create(player.serverLevel());
        actor.setNoAi(true);
        actor.setPersistenceRequired();
        float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        actor.moveTo(player.getX() + dx, -60, player.getZ() + dz, yaw, 0);
        actor.setYHeadRot(yaw);
        actor.yBodyRot = yaw;
        actor.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
        actor.setHealth(400);
        player.serverLevel().addFreshEntity(actor);
        ACTORS.add(actor);
        return actor;
    }

    private static Mob actor(ServerPlayer player, double dx, double dz) { return actor(player, EntityType.PILLAGER, dx, dz); }

    private static void look(ServerPlayer p) {
        if (!ACTORS.isEmpty()) p.lookAt(EntityAnchorArgument.Anchor.EYES, ACTORS.getFirst().getBoundingBox().getCenter());
    }

    /**
     * A still camera beside the scene, {@code dx, dy, dz} from where the player was put, looking at {@code lx, ly, lz}.
     * Queued behind the scene's reset on the server thread, so it uses the anchor the reset set and reaches the client
     * after the reset's own camera clear, however far the server lags (a fixed delay lost that race early in the run).
     */
    private static void side(double dx, double dy, double dz, double lx, double ly, double lz) {
        Minecraft mc = Minecraft.getInstance();
        mc.getSingleplayerServer().execute(() -> {
            Vec3 at = anchor.add(dx, dy, dz), target = anchor.add(lx, ly, lz);
            mc.execute(() -> TempestCamera.fix(at, target));
        });
    }

    private static void log(String what) { System.out.println("HYDRO_PREVIEW " + what); }

    private static void learnAll(ServerPlayer p, ResourceLocation power) {
        Set<String> learned = new HashSet<>();
        for (var node : SkillCatalog.nodes(power.getPath())) learned.add(node.id());
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(power.toString(),
                new SkillTreeData.Build(20, learned, Map.of())));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!hydro() && !lightning() && !styles()) return;
        var mc = Minecraft.getInstance();
        if (!started && ++startup >= 35 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.fov().set(78);
            mc.options.renderDistance().set(6);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Styles-" + System.currentTimeMillis(),
                    new LevelSettings("Style Preview", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(73L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (age >= 100) mc.getToasts().clear();
        ++age;
        // A new player is enrolled in the Last Day (another dimension, behind a black hold): end it and wake them in the
        // bunker, then let them out of their pod, before anything is staged.
        if (age == 104) { server(mc, com.jayptucker.heroesevolved.story.prologue.PrologueService::skip); return; }
        if (age == 110) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
            ResourceLocation power = hydro() ? ModAbilities.HYDROKINESIS_ID : styles() ? STYLE_POWERS[0] : ModAbilities.ELECTROKINESIS_ID;
            server(mc, p -> {
                CryoChamberService.release(p);
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(power));
                PlayerProgressionService.setLevel(p, 5);
                learnAll(p, power);
                p.setGameMode(GameType.SURVIVAL);
                stage(p);
            });
            return;
        }
        // Three seconds for the staging area's chunks to load before the first scene; keep any wake-up tint off the shots.
        if (age < 170) return;
        if (age % 20 == 0) com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();

        if (hydro()) hydroScript(mc, age - 40);
        else if (styles() && input()) inputScript(mc, age - 40);
        else if (styles()) stylesScript(mc, age - 40);
        else lightningScript(mc, age - 40);
    }

    // ------------------------------------------------------------------
    // Hydrokinesis
    // ------------------------------------------------------------------

    private static void hydroScript(Minecraft mc, int age) {
        switch (age) {
            // The carried water, idle, behind and in front
            case 130 -> server(mc, p -> reset(p, 210, 0));
            case 140 -> capture = "01-carried-back.png";
            case 142 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 146 -> capture = "02-carried-front.png";
            // Water Whip from behind, and from the side
            case 160 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> { reset(p, 230, 0); actor(p, 1.5, 7); look(p); });
            }
            case 170 -> server(mc, p -> log("whip " + HydroService.whip(context(p, false))));
            case 172 -> capture = "03-whip-unroll.png";
            case 174 -> capture = "04-whip-crack.png";
            case 178 -> capture = "05-whip-retract.png";
            case 190 -> { server(mc, p -> { reset(p, 250, 0); actor(p, 0, 7); look(p); }); side(5, 1.6, 3.5, 0, 1.2, 3.5); }
            case 200 -> server(mc, p -> log("whip " + HydroService.whip(context(p, false))));
            case 201 -> capture = "06-whip-side-wind.png";
            case 203 -> capture = "07-whip-side-crack.png";
            // The snare
            case 220 -> server(mc, p -> { reset(p, 270, 0); actor(p, 1, 7); look(p); });
            case 230 -> server(mc, p -> log("snare " + HydroService.whip(context(p, true))));
            case 233 -> capture = "08-snare.png";
            case 238 -> capture = "09-snare-haul.png";
            // Crashing Wave, behind and from the side
            case 255 -> server(mc, p -> { reset(p, 290, 0); actor(p, -1, 7); actor(p, 1.5, 10); });
            case 265 -> server(mc, p -> log("wave " + HydroService.crashingWave(context(p, false))));
            case 268 -> capture = "10-wave-rise.png";
            case 273 -> capture = "11-wave-roll.png";
            case 278 -> capture = "12-wave-catch.png";
            case 290 -> capture = "13-wave-break.png";
            case 305 -> { server(mc, p -> { reset(p, 320, 0); actor(p, 0, 9); }); side(10, 3, 6, 0, 1, 7); }
            case 312 -> server(mc, p -> log("wave " + HydroService.crashingWave(context(p, false))));
            case 318 -> capture = "14-wave-side.png";
            case 323 -> capture = "15-wave-side-late.png";
            case 335 -> { server(mc, p -> { reset(p, 345, 0); actor(p, 0, 9); }); side(-1, 2.5, 16, 0, 1, 5); }
            case 342 -> server(mc, p -> log("wave " + HydroService.crashingWave(context(p, false))));
            case 350 -> capture = "16-wave-front.png";
            // Octopus Form
            // Nobody stands straight ahead: the front camera would end up inside them.
            case 370 -> server(mc, p -> { reset(p, 370, 0); actor(p, 2.8, 1); actor(p, -2.8, 2); actor(p, 1.8, -2.6); });
            case 375 -> server(mc, p -> log("octopus " + HydroService.startOctopus(context(p, false))));
            case 386 -> capture = "17-octopus-back.png";
            case 390 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 396 -> capture = "18-octopus-front.png";
            case 400 -> side(6, 2.5, 2, 0, 1.2, 0);
            case 404 -> capture = "19-octopus-side.png";
            case 408 -> server(mc, p -> log("burst " + HydroService.octopusBurst(context(p, false))));
            case 410 -> capture = "20-octopus-burst.png";
            case 414 -> capture = "21-octopus-burst-late.png";
            // Wave Rider, behind, front and side, and the leap
            case 430 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> reset(p, 400, 0));
            }
            case 435 -> server(mc, p -> log("ride " + HydroService.startRide(context(p, false))));
            case 444 -> capture = "22-surf-back.png";
            case 447 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 452 -> capture = "23-surf-front.png";
            case 454 -> server(mc, p -> side(-6, 2, 14, 0, 1, 14));
            case 457 -> capture = "24-surf-side.png";
            case 460 -> server(mc, p -> log("leap " + HydroService.leap(context(p, false))));
            case 464 -> capture = "25-leap.png";
            case 485 -> capture = "26-leap-landing.png";
            // Maelstrom: the whirlpool, the spout, the Deluge
            case 505 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> { reset(p, 450, 14); actor(p, 2, 11); actor(p, -2, 13); actor(p, 3, 9); });
            }
            case 512 -> server(mc, p -> log("maelstrom " + HydroService.startMaelstrom(context(p, false))));
            case 528 -> capture = "27-whirlpool.png";
            case 548 -> capture = "28-spout-rising.png";
            case 570 -> capture = "29-spout.png";
            case 575 -> side(-16, 7, -6, 0, 6, 12);
            case 580 -> capture = "30-spout-far.png";
            case 600 -> server(mc, p -> log("deluge " + HydroService.endMaelstrom(p)));
            case 603 -> capture = "31-deluge.png";
            case 609 -> capture = "32-deluge-late.png";
            // Waterbending: the combo
            case 630 -> server(mc, p -> { reset(p, 500, 0); actor(p, 1.5, 6); look(p); });
            case 640 -> server(mc, p -> log("strike " + HydroCombat.next(p) + " " + HydroCombat.strike(p)));
            case 642 -> capture = "33-lash.png";
            case 646 -> server(mc, p -> log("strike " + HydroCombat.next(p) + " " + HydroCombat.strike(p)));
            case 648 -> capture = "34-backhand.png";
            case 652 -> server(mc, p -> log("strike " + HydroCombat.next(p) + " " + HydroCombat.strike(p)));
            case 654 -> capture = "35-daggers.png";
            case 656 -> capture = "36-daggers-landing.png";
            case 670 -> { server(mc, p -> { reset(p, 520, 0); actor(p, 0, 6); look(p); }); side(5, 1.6, 2.5, 0, 1.2, 2.5); }
            case 680 -> server(mc, p -> HydroCombat.strike(p));
            case 681 -> capture = "37-lash-side.png";
            case 686 -> server(mc, p -> HydroCombat.strike(p));
            case 687 -> capture = "38-backhand-side.png";
            case 692 -> server(mc, p -> HydroCombat.strike(p));
            case 694 -> capture = "39-daggers-side.png";
            // Water Wheel, crouched
            case 710 -> server(mc, p -> { reset(p, 540, 25); actor(p, 2.5, .5); actor(p, -2.5, 1.5); actor(p, .5, 3.2); });
            case 715 -> mc.options.keyShift.setDown(true);
            case 722 -> server(mc, p -> log("wheel " + HydroCombat.next(p) + " " + HydroCombat.strike(p)));
            case 725 -> capture = "40-wheel.png";
            case 729 -> capture = "41-wheel-late.png";
            case 735 -> mc.options.keyShift.setDown(false);
            // Flow Step and Geyser Vault
            case 750 -> server(mc, p -> reset(p, 560, 0));
            case 758 -> server(mc, p -> log("flow " + HydroCombat.dodge(p, Dodge.LEFT)));
            case 760 -> capture = "42-flow.png";
            case 764 -> capture = "43-flow-late.png";
            case 780 -> server(mc, p -> { reset(p, 580, 0); actor(p, .5, 1.6); });
            case 785 -> side(6, 2, 0, 0, 1.5, 0);
            case 790 -> server(mc, p -> log("vault " + HydroCombat.dodge(p, Dodge.BACK)));
            case 793 -> capture = "44-vault.png";
            case 796 -> capture = "45-vault-mid.png";
            case 800 -> capture = "46-vault-late.png";
            // Skills: Geyser, Pressure Jet, Tide Wall, Water Spout, Healing Water, Rain Call
            case 820 -> server(mc, p -> { reset(p, 600, 22); actor(p, 0, 4.5); });
            case 826 -> server(mc, p -> log("geyser " + useNode(p, "hydro_0_1")));
            case 832 -> capture = "47-geyser-mark.png";
            case 843 -> capture = "48-geyser-burst.png";
            case 848 -> capture = "49-geyser-late.png";
            case 865 -> server(mc, p -> { reset(p, 620, 0); actor(p, 0, 7); look(p); });
            case 870 -> server(mc, p -> log("jet " + useNode(p, "hydro_pressure_jet")));
            case 880 -> capture = "50-jet.png";
            case 884 -> side(5, 1.8, 3, 0, 1.4, 3);
            case 888 -> capture = "51-jet-side.png";
            case 910 -> server(mc, p -> reset(p, 640, 0));
            case 914 -> server(mc, p -> log("wall " + useNode(p, "hydro_1_1")));
            case 922 -> capture = "52-tide-wall.png";
            case 925 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 930 -> capture = "53-tide-wall-front.png";
            case 945 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> reset(p, 660, 0));
            }
            case 950 -> server(mc, p -> log("spout " + useNode(p, "hydro_water_spout")));
            case 975 -> capture = "54-spout.png";
            case 978 -> side(7, 4, 3, 0, 4, 0);
            case 982 -> capture = "55-spout-side.png";
            case 1000 -> server(mc, p -> {
                reset(p, 680, 0);
                Mob friend = actor(p, EntityType.VILLAGER, 0, 3);
                friend.setHealth(100);
                look(p);
            });
            case 1003 -> side(4.5, 1.8, 1.5, 0, 1, 2);
            case 1008 -> server(mc, p -> log("heal " + useNode(p, "hydro_2_1")));
            case 1012 -> capture = "56-healing-water.png";
            case 1020 -> capture = "57-healing-water-late.png";
            case 1035 -> server(mc, p -> { reset(p, 700, 0); actor(p, 4, 4); });
            case 1040 -> server(mc, p -> log("rain " + useNode(p, "hydro_rain_call")));
            case 1050 -> side(-9, 4, -6, 0, 3, 2);
            case 1054 -> capture = "58-rain-call.png";
            // First person
            case 1185 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(mc, p -> { reset(p, 800, 0); actor(p, 1.5, 7); look(p); });
            }
            case 1195 -> server(mc, p -> HydroService.whip(context(p, false)));
            case 1197 -> capture = "63-whip-first-person.png";
            case 1199 -> capture = "64-whip-crack-first-person.png";
            case 1210 -> server(mc, p -> HydroCombat.strike(p));
            case 1211 -> capture = "65-lash-first-person.png";
            case 1216 -> server(mc, p -> HydroCombat.strike(p));
            case 1217 -> capture = "66-backhand-first-person.png";
            case 1222 -> server(mc, p -> HydroCombat.strike(p));
            case 1224 -> capture = "67-daggers-first-person.png";
            case 1240 -> server(mc, p -> HydroService.crashingWave(context(p, false)));
            case 1246 -> capture = "68-wave-first-person.png";
            case 1265 -> server(mc, p -> HydroService.startOctopus(context(p, false)));
            case 1275 -> capture = "69-octopus-first-person.png";
            case 1280 -> server(mc, p -> HydroService.endOctopus(p));
            case 1290 -> server(mc, p -> reset(p, 830, 30));
            case 1295 -> capture = "70-carried-first-person.png";
            case 1310 -> PreviewQuiet.quit();
            default -> { }
        }
    }

    // ------------------------------------------------------------------
    // The shared-engine fighting styles (flag heroes.preview.fighting)
    // ------------------------------------------------------------------

    /** Every style, or only those named in heroes.preview.fighting.only (comma-separated power paths). */
    private static final ResourceLocation[] STYLE_POWERS = java.util.stream.Stream.of(ModAbilities.SUPERSPEED_ID,
            ModAbilities.QUAKE_ID, ModAbilities.CRYOKINESIS_ID, ModAbilities.PHASING_ID, ModAbilities.REGENERATION_ID,
            ModAbilities.INVISIBILITY_ID, ModAbilities.TELEKINESIS_ID)
            .filter(id -> System.getProperty("heroes.preview.fighting.only", "").isBlank()
                    || java.util.List.of(System.getProperty("heroes.preview.fighting.only").split(",")).contains(id.getPath()))
            .toArray(ResourceLocation[]::new);
    /** How long each style's scene runs. */
    private static final int STYLE_SCENE = 130;

    private static com.jayptucker.heroesevolved.combat.ComboStyle style(ServerPlayer p) {
        return (com.jayptucker.heroesevolved.combat.ComboStyle) FightingStyles.of(p);
    }

    /**
     * Every style through the same scene, from the side: lead, rear, finisher; the low move from behind; a side dodge,
     * the back dodge and the counter (a blow taken during a dodge, or for the brawler, during a brace) and its strike.
     */
    // ------------------------------------------------------------------
    // Real input for every style (flag heroes.preview.fighting.input)
    // ------------------------------------------------------------------

    private static ResourceLocation[] INPUT_POWERS = {ModAbilities.PYROKINESIS_ID, ModAbilities.ELECTROKINESIS_ID,
            ModAbilities.HYDROKINESIS_ID, ModAbilities.SUPERSPEED_ID, ModAbilities.QUAKE_ID, ModAbilities.CRYOKINESIS_ID,
            ModAbilities.PHASING_ID, ModAbilities.REGENERATION_ID, ModAbilities.INVISIBILITY_ID, ModAbilities.TELEKINESIS_ID};
    static {
        String only = System.getProperty("heroes.preview.fighting.only", "");
        if (!only.isBlank()) INPUT_POWERS = java.util.Arrays.stream(INPUT_POWERS)
                .filter(id -> java.util.List.of(only.split(",")).contains(id.getPath())).toArray(ResourceLocation[]::new);
    }
    private static final int INPUT_SCENE = 90;
    private static float inputHealth;
    private static Vec3 inputFrom = Vec3.ZERO;
    private static int inputFailures;

    /** Claire doesn't dodge: a double-tap is a brace, and she stays where she is. */
    private static boolean braces(ServerPlayer p, ResourceLocation power) {
        return power.equals(ModAbilities.REGENERATION_ID) && com.jayptucker.heroesevolved.combat.StyleMotion.bracing(p);
    }

    private static boolean input() { return Boolean.getBoolean("heroes.preview.fighting.input"); }

    /** Presses the real keys, as a player would: three clicks at a pillager, then a double-tapped right and back. */
    private static void inputScript(Minecraft mc, int age) {
        if (age < 130) return;
        int scene = (age - 130) / INPUT_SCENE, t = (age - 130) % INPUT_SCENE;
        if (scene >= INPUT_POWERS.length) {
            if (scene == INPUT_POWERS.length && t == 0) {
                log("INPUT_RESULT " + (inputFailures == 0 ? "ALL PASS" : inputFailures + " FAILED"));
                PreviewQuiet.quit();
            }
            return;
        }
        ResourceLocation power = INPUT_POWERS[scene];
        String name = power.getPath();
        var attack = mc.options.keyAttack;
        switch (t) {
            case 0 -> server(mc, p -> {
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(power));
                PlayerProgressionService.setLevel(p, 5);
                com.jayptucker.heroesevolved.network.PlayerPowerSyncService.sync(p);
                reset(p, 200 + scene * 20, 0);
                Mob target = actor(p, 0, 3);
                target.setHealth(target.getMaxHealth());
                look(p);
            });
            case 12 -> server(mc, p -> inputHealth = ACTORS.getFirst().getHealth());
            case 14, 22, 30 -> {
                log(name + " click: crosshair " + (mc.hitResult == null ? "none" : mc.hitResult.getType()));
                net.minecraft.client.KeyMapping.click(attack.getKey());
            }
            case 44 -> server(mc, p -> {
                float health = ACTORS.getFirst().getHealth();
                boolean ok = health < inputHealth;
                if (!ok) inputFailures++;
                log("INPUT " + name + " strikes " + (ok ? "PASS" : "FAIL") + " (" + inputHealth + " -> " + health + ")");
                p.teleportTo(world(p), anchor.x, anchor.y, anchor.z, 0, 0);
                p.setDeltaMovement(Vec3.ZERO);
                ACTORS.forEach(Mob::discard);
                ACTORS.clear();
            });
            case 50 -> server(mc, p -> inputFrom = p.position());
            case 52, 55 -> mc.options.keyRight.setDown(true);
            case 53, 56 -> mc.options.keyRight.setDown(false);
            case 64 -> server(mc, p -> {
                double moved = p.position().x - inputFrom.x;
                // Facing south (yaw 0), right is west: x goes down.
                boolean ok = braces(p, power) || moved < -1.5;
                if (!ok) inputFailures++;
                log("INPUT " + name + " dodge right " + (ok ? "PASS" : "FAIL") + " (moved x " + String.format("%.2f", moved) + ")");
                p.teleportTo(world(p), anchor.x, anchor.y, anchor.z, 0, 0);
                p.setDeltaMovement(Vec3.ZERO);
                FightingStyles.forget(p);
                PlayerEnergyService.restore(p, 999);
            });
            case 68 -> server(mc, p -> inputFrom = p.position());
            case 70, 73 -> mc.options.keyDown.setDown(true);
            case 71, 74 -> mc.options.keyDown.setDown(false);
            case 86 -> server(mc, p -> {
                double moved = p.position().z - inputFrom.z;
                boolean ok = braces(p, power) || moved < -1.5;
                if (!ok) inputFailures++;
                log("INPUT " + name + " dodge back " + (ok ? "PASS" : "FAIL") + " (moved z " + String.format("%.2f", moved) + ")");
            });
            default -> { }
        }
    }

    private static void stylesScript(Minecraft mc, int age) {
        if (age < 130) return;
        int scene = (age - 130) / STYLE_SCENE, t = (age - 130) % STYLE_SCENE;
        if (scene >= STYLE_POWERS.length) {
            if (scene == STYLE_POWERS.length && t == 0) PreviewQuiet.quit();
            return;
        }
        ResourceLocation power = STYLE_POWERS[scene];
        String name = power.getPath();
        switch (t) {
            case 0 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> {
                    p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
                    p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(power));
                    PlayerProgressionService.setLevel(p, 5);
                    reset(p, 200 + scene * 20, 0);
                    actor(p, 0, 3.6);
                    look(p);
                });
                side(4.5, 1.7, 1.6, 0, 1.2, 1.8);
            }
            case 12 -> server(mc, p -> log(name + " lead " + style(p).strike(p)));
            case 13 -> capture = name + "-01-lead.png";
            case 15 -> capture = name + "-02-lead-late.png";
            case 19 -> server(mc, p -> log(name + " rear " + style(p).strike(p)));
            case 20 -> capture = name + "-03-rear.png";
            case 26 -> server(mc, p -> { look(p); log(name + " finisher " + style(p).next(p) + " " + style(p).strike(p)); });
            case 28 -> capture = name + "-04-finisher.png";
            case 32 -> capture = name + "-05-finisher-late.png";
            // The low move, from behind, with foes all round
            case 45 -> server(mc, p -> { reset(p, 200 + scene * 20, 25); actor(p, 2.4, .5); actor(p, -2.4, 1.2); actor(p, .4, 3); });
            case 50 -> mc.options.keyShift.setDown(true);
            case 56 -> server(mc, p -> log(name + " low " + style(p).next(p) + " " + style(p).strike(p)));
            case 58 -> capture = name + "-06-low.png";
            case 62 -> capture = name + "-07-low-late.png";
            case 66 -> mc.options.keyShift.setDown(false);
            // Dodges, from the side
            case 75 -> { server(mc, p -> { reset(p, 200 + scene * 20, 0); actor(p, 1.5, 5); }); side(-.5, 2.2, 9, 0, 1, 0); }
            case 82 -> server(mc, p -> log(name + " dodge " + style(p).dodge(p, FightingStyles.Dodge.RIGHT)));
            case 84 -> capture = name + "-08-dodge.png";
            case 102 -> server(mc, p -> log(name + " back " + style(p).dodge(p, FightingStyles.Dodge.BACK)));
            case 105 -> capture = name + "-09-back.png";
            case 108 -> capture = name + "-10-back-late.png";
            // The counter and its strike
            case 113 -> server(mc, p -> { reset(p, 200 + scene * 20, 0); actor(p, 0, 3.4); look(p); });
            case 116 -> server(mc, p -> {
                boolean dodged = style(p).dodge(p, FightingStyles.Dodge.LEFT);
                p.invulnerableTime = 0;
                p.hurt(p.damageSources().mobAttack(ACTORS.getFirst()), 4);
                log(name + " counter " + dodged + " loaded " + style(p).loaded(p));
            });
            case 118 -> capture = name + "-11-counter.png";
            case 121 -> server(mc, p -> { look(p); log(name + " loaded strike " + style(p).strike(p)); });
            case 123 -> capture = name + "-12-loaded.png";
            case 126 -> capture = name + "-13-loaded-late.png";
            default -> { }
        }
    }

    /** Uses a learned tree node's action, as the ability wheel would. */
    private static boolean useNode(ServerPlayer p, String id) {
        var node = SkillCatalog.nodes("hydrokinesis").stream().filter(n -> n.id().equals(id)).findFirst().orElseThrow();
        var action = node.action();
        var c = context(p, false);
        return action.canUse(c) && action.activate(c) == com.jayptucker.heroesevolved.ability.AbilityActivationResult.SUCCESS;
    }

    // ------------------------------------------------------------------
    // Electrokinesis's lightning style
    // ------------------------------------------------------------------

    private static void lightningScript(Minecraft mc, int age) {
        switch (age) {
            case 130 -> server(mc, p -> { reset(p, 210, 0); actor(p, 1.5, 6); look(p); });
            case 140 -> server(mc, p -> log("spark " + ElectroCombat.next(p) + " " + ElectroCombat.strike(p)));
            case 141 -> capture = "01-spark-jab.png";
            case 143 -> capture = "02-spark-landing.png";
            case 146 -> server(mc, p -> log("spark " + ElectroCombat.next(p) + " " + ElectroCombat.strike(p)));
            case 147 -> capture = "03-spark-cross.png";
            case 152 -> server(mc, p -> log("clap " + ElectroCombat.next(p) + " " + ElectroCombat.strike(p)));
            case 153 -> capture = "04-clap-meet.png";
            case 156 -> capture = "05-clap-fan.png";
            case 160 -> capture = "06-clap-late.png";
            case 175 -> { server(mc, p -> { reset(p, 230, 0); actor(p, 0, 6); look(p); }); side(5, 1.6, 2.5, 0, 1.2, 2.5); }
            case 185 -> server(mc, ElectroCombat::strike);
            case 186 -> capture = "07-jab-side.png";
            case 191 -> server(mc, ElectroCombat::strike);
            case 192 -> capture = "08-cross-side.png";
            case 197 -> server(mc, ElectroCombat::strike);
            case 199 -> capture = "09-clap-side.png";
            case 215 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                server(mc, p -> reset(p, 250, 0));
            }
            case 225 -> server(mc, ElectroCombat::strike);
            case 231 -> server(mc, ElectroCombat::strike);
            case 237 -> server(mc, ElectroCombat::strike);
            case 240 -> capture = "10-clap-front.png";
            // Grounding Strike
            case 255 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> { reset(p, 270, 25); actor(p, 2.5, .5); actor(p, -2.5, 1.5); actor(p, .5, 3.2); });
            }
            case 260 -> mc.options.keyShift.setDown(true);
            case 267 -> server(mc, p -> log("ground " + ElectroCombat.next(p) + " " + ElectroCombat.strike(p)));
            case 269 -> capture = "11-ground.png";
            case 273 -> capture = "12-ground-late.png";
            case 276 -> side(4, 1.4, 2, 0, .6, 0);
            case 278 -> capture = "13-ground-side.png";
            case 282 -> mc.options.keyShift.setDown(false);
            // Arc Step, behind and in front
            case 300 -> server(mc, p -> reset(p, 300, 0));
            case 308 -> server(mc, p -> log("step " + ElectroCombat.dodge(p, Dodge.LEFT)));
            case 310 -> capture = "14-arc-step.png";
            case 313 -> capture = "15-arc-step-late.png";
            case 320 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 330 -> server(mc, p -> ElectroCombat.dodge(p, Dodge.RIGHT));
            case 332 -> capture = "16-arc-step-front.png";
            // Flashback
            case 350 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> { reset(p, 330, 0); actor(p, .5, 1.6); });
            }
            case 355 -> side(6, 2, 0, 0, 1.2, -1.5);
            case 360 -> server(mc, p -> log("flashback " + ElectroCombat.dodge(p, Dodge.BACK)));
            case 362 -> capture = "17-flashback.png";
            case 368 -> capture = "18-flashback-static.png";
            case 373 -> capture = "19-flashback-burst.png";
            // Ground Out and the chained spark
            case 395 -> server(mc, p -> { reset(p, 360, 0); actor(p, 1, 6); actor(p, 3.5, 7); look(p); });
            case 405 -> server(mc, p -> ElectroCombat.dodge(p, Dodge.LEFT));
            case 406 -> server(mc, p -> {
                p.invulnerableTime = 0;
                LivingEntity attacker = ACTORS.getFirst();
                p.hurt(p.damageSources().mobAttack(attacker), 4);
                log("loaded " + ElectroCombat.isLoaded(p));
            });
            case 408 -> capture = "20-ground-out.png";
            case 414 -> server(mc, HydroPreview::look);
            case 416 -> capture = "21-loaded-fist.png";
            case 418 -> server(mc, p -> log("chain " + ElectroCombat.strike(p)));
            case 420 -> capture = "22-chain-spark.png";
            // First person
            case 440 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(mc, p -> { reset(p, 390, 0); actor(p, 1.5, 7); look(p); });
            }
            case 450 -> server(mc, ElectroCombat::strike);
            case 451 -> capture = "23-jab-first-person.png";
            case 456 -> server(mc, ElectroCombat::strike);
            case 457 -> capture = "24-cross-first-person.png";
            case 462 -> server(mc, ElectroCombat::strike);
            case 464 -> capture = "25-clap-first-person.png";
            case 480 -> server(mc, p -> reset(p, 420, 20));
            case 483 -> mc.options.keyShift.setDown(true);
            case 490 -> server(mc, ElectroCombat::strike);
            case 493 -> capture = "26-ground-first-person.png";
            case 500 -> mc.options.keyShift.setDown(false);
            case 510 -> server(mc, p -> ElectroCombat.dodge(p, Dodge.LEFT));
            case 512 -> capture = "27-arc-step-first-person.png";
            case 530 -> PreviewQuiet.quit();
            default -> { }
        }
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Pre event) {
        if (age >= 100 && (hydro() || lightning() || styles())) event.setCanceled(true);
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (capture == null) return;
        Minecraft mc = Minecraft.getInstance();
        Path path = mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Files.createDirectories(path.getParent());
            image.writeToFile(path);
            System.out.println("HYDRO_PREVIEW_CAPTURE " + capture);
        } catch (Exception e) {
            throw new IllegalStateException("Could not capture the style preview", e);
        } finally {
            capture = null;
        }
    }
}
