package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.events.AwakeningService;
import com.jayptucker.heroesevolved.intro.PowerIntroHistory;
import com.jayptucker.heroesevolved.intro.PowerIntroService;
import com.jayptucker.heroesevolved.intro.TimeAwakening;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Creeper;
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
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A live run through every power's awakening (flag {@code heroes.preview.awakening}), to watch rather than photograph.
 * Each scene gives the player a dormant power, puts them (or their wolf) in the danger it answers, and lets the real
 * danger rules wake it: husks wearing them down, a creeper hissing, a forty-block drop, a lava pit, a flooded shaft.
 * A title names the power and the danger. The run ends on Space-Time stopping time for a creeper, and the Odessa vision
 * that follows; the game stays open afterwards. {@code -Ponly=cryokinesis,time_manipulation} keeps some scenes.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class AwakeningPreview {
    private enum Danger { FIGHT, ALLY, BLAST, FALL, LAVA, DROWN }

    private record Scene(String power, Danger danger, String what) {}

    private static final List<Scene> ALL = List.of(
            // Every power answers a fight it is losing.
            new Scene("superspeed", Danger.FIGHT, "Losing a fight: it runs you out"),
            new Scene("superspeed", Danger.ALLY, "Your wolf is going down 14 blocks away"),
            new Scene("flight", Danger.FIGHT, "Losing a fight under open sky"),
            new Scene("flight", Danger.ALLY, "Your wolf is going down"),
            new Scene("cryokinesis", Danger.FIGHT, "Losing a fight"),
            new Scene("pyrokinesis", Danger.FIGHT, "Losing a fight"),
            new Scene("electrokinesis", Danger.FIGHT, "Losing a fight"),
            new Scene("quake", Danger.FIGHT, "Losing a fight"),
            new Scene("telekinesis", Danger.FIGHT, "Losing a fight"),
            new Scene("telepathy", Danger.FIGHT, "Losing a fight: they turn on each other"),
            new Scene("sonic_resonance", Danger.FIGHT, "Losing a fight"),
            new Scene("invisibility", Danger.FIGHT, "Losing a fight"),
            new Scene("phasing", Danger.FIGHT, "Losing a fight"),
            new Scene("empathic_mimicry", Danger.FIGHT, "Losing a fight"),
            new Scene("regeneration", Danger.FIGHT, "Losing a fight"),
            new Scene("technopathy", Danger.FIGHT, "Losing a fight"),
            new Scene("matter_assembly", Danger.FIGHT, "Losing a fight"),
            new Scene("vibe", Danger.FIGHT, "Losing a fight"),
            new Scene("shapeshifting", Danger.FIGHT, "Losing a fight"),
            new Scene("hydrokinesis", Danger.FIGHT, "Losing a fight"),
            // The new dangers.
            new Scene("telekinesis", Danger.BLAST, "A creeper is about to go off: thrown at the sky"),
            new Scene("cryokinesis", Danger.BLAST, "A creeper is about to go off: frozen, fuse run back"),
            new Scene("sonic_resonance", Danger.BLAST, "A creeper is about to go off: screamed away"),
            new Scene("telekinesis", Danger.FALL, "Falling forty blocks: caught in mid-air"),
            new Scene("cryokinesis", Danger.FALL, "Falling forty blocks: a drift of powder snow"),
            new Scene("hydrokinesis", Danger.FALL, "Falling forty blocks: a pool to land in"),
            new Scene("matter_assembly", Danger.FALL, "Falling forty blocks: a platform underfoot"),
            new Scene("flight", Danger.FALL, "Falling forty blocks: you fly instead"),
            new Scene("cryokinesis", Danger.LAVA, "Standing in lava: it sets into obsidian"),
            new Scene("hydrokinesis", Danger.LAVA, "Standing in lava: water from nowhere"),
            new Scene("pyrokinesis", Danger.LAVA, "Standing in lava: it doesn't burn you"),
            new Scene("hydrokinesis", Danger.DROWN, "Out of air at the bottom of a shaft"),
            new Scene("radiation", Danger.FIGHT, "Losing a fight: thirty seconds you can't hold in"),
            // Last: time stops, then the vision.
            new Scene("time_manipulation", Danger.BLAST, "A creeper is about to go off: time stops"));

    /** Scenes stand in a line well away from the spawn bunker, each on fresh ground (Quake and Radiation leave craters). */
    private static final int ORIGIN = 400, SPACING = 64, GROUND = -60;
    private static final int SETTLE = 50, TIMEOUT = 20 * 30, RADIATION_TIMEOUT = 20 * 45, AFTER = 50;

    private static final List<Entity> ACTORS = new ArrayList<>();
    private static List<Scene> scenes;
    private static boolean started, ready, finished, visionLogged, surgeSeen;
    /** Client ticks left before the game closes itself once every scene has played. */
    private static int closing = -1;
    private static int startup, age, index = -1, sceneAge, awokeAt = -1, quietFor;

    private static boolean on() { return Boolean.getBoolean("heroes.preview.awakening"); }

    // ------------------------------------------------------------------
    // Client: open a fresh flat world, get the player out of the prologue
    // ------------------------------------------------------------------

    @SubscribeEvent public static void client(ClientTickEvent.Post event) {
        if (!on()) return;
        var mc = Minecraft.getInstance();
        if (!started && ++startup >= 35 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(8);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Awakenings-" + System.currentTimeMillis(),
                    new LevelSettings("Awakening Preview", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules,
                            WorldDataConfiguration.DEFAULT),
                    new WorldOptions(91L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(),
                    mc.screen);
        }
        if (closing >= 0 && --closing <= 0) PreviewQuiet.quit();
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        ++age;
        if (age == 104) server(mc, com.jayptucker.heroesevolved.story.prologue.PrologueService::skip);
        if (age == 110) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
            server(mc, p -> {
                CryoChamberService.release(p);
                p.setGameMode(GameType.SURVIVAL);
                p.getServer().setDifficulty(Difficulty.NORMAL, true);
                p.serverLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, p.getServer());
                p.getServer().overworld().setDayTime(6000);
                ready = true;
            });
        }
    }

    private static void server(Minecraft mc, java.util.function.Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    // ------------------------------------------------------------------
    // Server: one scene at a time
    // ------------------------------------------------------------------

    @SubscribeEvent public static void server(ServerTickEvent.Post event) {
        if (!on() || !ready || finished) return;
        MinecraftServer server = event.getServer();
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
        if (scenes == null) scenes = chosen();
        if (index < 0 || done(p)) {
            if (++index >= scenes.size()) { finish(p); return; }
            begin(p, scenes.get(index));
        }
        Scene scene = scenes.get(index);
        ++sceneAge;
        if (sceneAge == SETTLE) danger(p, scene);
        if (sceneAge > SETTLE) during(p, scene);
    }

    private static List<Scene> chosen() {
        String only = System.getProperty("heroes.preview.awakening.only", "");
        if (only.isBlank()) return ALL;
        Set<String> keep = Arrays.stream(only.split(",")).map(String::trim).collect(Collectors.toSet());
        return ALL.stream().filter(scene -> keep.contains(scene.power)).toList();
    }

    private static boolean dormant(ServerPlayer p) { return AwakeningService.hasDormantPower(p); }

    /** Over once the power woke, its intro (and any rescue or stop) has ended, and a moment has passed to see it. */
    private static boolean done(ServerPlayer p) {
        Scene scene = scenes.get(index);
        int limit = scene.power.equals("radiation") ? RADIATION_TIMEOUT : TIMEOUT;
        if (sceneAge > limit) {
            log("scene " + (index + 1) + " " + scene.power + " " + scene.danger + " TIMED OUT (woke=" + !dormant(p) + ")");
            return true;
        }
        if (awokeAt < 0) return false;
        boolean surging = com.jayptucker.heroesevolved.intro.Surge.active(p);
        if (surging != surgeSeen) {
            surgeSeen = surging;
            log((surging ? "surge started: " : "surge over: ") + scene.power + " at scene tick " + sceneAge);
        }
        if (PowerIntroService.isActive(p) || TimeAwakening.stopping(p) || surging) { quietFor = 0; return false; }
        return ++quietFor > AFTER;
    }

    private static void begin(ServerPlayer p, Scene scene) {
        sceneAge = 0;
        awokeAt = -1;
        quietFor = 0;
        clearActors();
        PowerIntroService.forget(p);
        AwakeningService.forget(p);
        PlayerAbilityService.removeAssignedAbility(p);
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY, PowerIntroHistory.empty());
        ResourceLocation power = ResourceLocation.fromNamespaceAndPath("heroesevolved", scene.power);
        boolean assigned = PlayerAbilityService.assignDormantAbility(p, power);
        // The Odessa vision isn't what this run is checking: mark it seen, so time stops and starts again right here.
        p.setData(ModDataAttachments.TIME_INTRO_SEEN, true);
        ServerLevel world = p.getServer().overworld();
        world.setDayTime(6000);
        Vec3 spot = spot();
        p.teleportTo(world, spot.x, spot.y, spot.z, 0, 10);
        p.setDeltaMovement(Vec3.ZERO);
        heal(p);
        title(p, Component.translatable("ability.heroesevolved." + scene.power).withStyle(ChatFormatting.GOLD),
                Component.literal(scene.what).withStyle(ChatFormatting.GRAY));
        p.sendSystemMessage(Component.literal("[" + (index + 1) + "/" + scenes.size() + "] ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.translatable("ability.heroesevolved." + scene.power).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("  " + scene.what).withStyle(ChatFormatting.GRAY)));
        log("scene " + (index + 1) + "/" + scenes.size() + " " + scene.power + " " + scene.danger + " assigned=" + assigned
                + " at " + p.blockPosition() + " in " + p.level().dimension().location());
    }

    private static Vec3 spot() { return new Vec3(ORIGIN + index * SPACING + 0.5, GROUND, ORIGIN + 0.5); }

    /** Sets up the danger once the ground has loaded round the new spot. */
    private static void danger(ServerPlayer p, Scene scene) {
        ServerLevel world = p.serverLevel();
        Vec3 at = p.position();
        switch (scene.danger) {
            case FIGHT -> {
                p.setHealth(6);
                for (int i = 0; i < 3; i++) {
                    double angle = Math.PI * 2 * i / 3 + 0.4;
                    Mob husk = mob(world, EntityType.HUSK, at.add(Math.sin(angle) * 3.5, 0, Math.cos(angle) * 3.5 + 1.5));
                    husk.setTarget(p);
                }
            }
            case ALLY -> {
                Wolf wolf = (Wolf) mob(world, EntityType.WOLF, at.add(0, 0, 14));
                wolf.tame(p);
                wolf.setHealth(3);
                for (int i = 0; i < 3; i++) {
                    Mob husk = mob(world, EntityType.HUSK, wolf.position().add(Math.sin(i * 2.1) * 2, 0, Math.cos(i * 2.1) * 2));
                    husk.setNoAi(true);
                    AwakeningService.recordCombat(wolf, wolf.damageSources().mobAttack(husk));
                }
                p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, wolf.getEyePosition());
            }
            case BLAST -> {
                Creeper creeper = (Creeper) mob(world, EntityType.CREEPER, at.add(0, 0, 4));
                creeper.setTarget(p);
                p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, creeper.getEyePosition());
            }
            case FALL -> p.teleportTo(world, at.x, at.y + 40, at.z, 0, 50);
            case LAVA -> {
                BlockPos center = p.blockPosition().below();
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                    world.setBlockAndUpdate(center.offset(dx, 0, dz), Blocks.LAVA.defaultBlockState());
                p.setHealth(10);
            }
            case DROWN -> {
                BlockPos center = p.blockPosition().below();
                for (int dy = 0; dy < 3; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                    world.setBlockAndUpdate(center.offset(dx, -dy, dz), Blocks.WATER.defaultBlockState());
                p.teleportTo(world, at.x, at.y - 3, at.z, 0, 10);
                p.setAirSupply(0);
                p.setHealth(7);
            }
        }
        log("danger staged: " + scene.danger + " health=" + p.getHealth());
    }

    private static void during(ServerPlayer p, Scene scene) {
        if (awokeAt < 0 && !dormant(p)) {
            awokeAt = sceneAge;
            log("woke " + scene.power + " after " + (sceneAge - SETTLE) + " ticks");
        }
        if (p.getHealth() < 1.5F) p.setHealth(4);
        if (awokeAt >= 0) {
            if (scene.power.equals("time_manipulation") && !visionLogged && TimeIntroService.isQueued(p)) {
                visionLogged = true;
                log("time started again and the vision is queued");
            }
            return;
        }
        // Keep the wolf's fight going, and never let the player actually die if something doesn't wake.
        if (scene.danger == Danger.ALLY && sceneAge % 40 == 0)
            for (Entity actor : ACTORS) if (actor instanceof Wolf wolf && wolf.isAlive())
                ACTORS.stream().filter(a -> a instanceof Mob && !(a instanceof Wolf)).findFirst()
                        .ifPresent(husk -> AwakeningService.recordCombat(wolf, wolf.damageSources().mobAttack((Mob) husk)));
        if (scene.danger == Danger.DROWN) p.setAirSupply(0);
    }

    private static Mob mob(ServerLevel world, EntityType<? extends Mob> type, Vec3 at) {
        Mob mob = type.create(world);
        mob.moveTo(at.x, at.y, at.z, 180, 0);
        mob.setPersistenceRequired();
        world.addFreshEntity(mob);
        ACTORS.add(mob);
        return mob;
    }

    private static void clearActors() {
        ACTORS.forEach(Entity::discard);
        ACTORS.clear();
    }

    private static void heal(ServerPlayer p) {
        p.removeAllEffects();
        p.clearFire();
        p.setHealth(p.getMaxHealth());
        p.setAirSupply(p.getMaxAirSupply());
        p.resetFallDistance();
        p.getFoodData().setFoodLevel(20);
    }

    private static void finish(ServerPlayer p) {
        finished = true;
        clearActors();
        title(p, Component.literal("That's every awakening").withStyle(ChatFormatting.GOLD),
                Component.literal("Closing in five seconds").withStyle(ChatFormatting.GRAY));
        log("all scenes done");
        Minecraft.getInstance().execute(() -> closing = 100);
    }

    private static void title(ServerPlayer p, Component title, Component subtitle) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 70, 15));
        p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    private static void log(String what) { System.out.println("AWAKENING_PREVIEW " + what); }
}
