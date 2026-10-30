package com.jayptucker.heroesevolved.story.sylar;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.effects.PrivateGlow;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.intro.PowerIntroService;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.time.TemporalSnapshotService;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import java.util.*;

/**
 * Bounded story director: one real hunter, rare encounters, saved chapters, and no chunk forcing.
 *
 * <p>The story is a slow ladder, one beat per player at a time: a faint ticking; a man watching from
 * a distance; the sighting where he speaks; then hunts. Early beats are at least ten minutes apart,
 * hunts at least twenty-five. Everything he says goes through {@link SylarVoice}, so lines arrive one at
 * a time, eight seconds apart.</p>
 */
public final class SylarStoryService {
    private record Step(Vec3 position, long time) {}
    private static final Map<UUID, Deque<Step>> ROUTES = new HashMap<>();
    private static final Map<UUID, Long> SENSED = new HashMap<>();
    private static Encounter active;
    private static final class Encounter {
        final SylarEntity hunter;
        final UUID victim;
        final long began;
        final boolean ambush, speaks;
        int seenTicks;
        boolean announced, footsteps;
        Encounter(SylarEntity hunter, ServerPlayer player, long now, boolean ambush, boolean speaks) {
            this.hunter = hunter; victim = player.getUUID(); began = now; this.ambush = ambush; this.speaks = speaks;
        }
    }
    private SylarStoryService() {}
    private static long now(MinecraftServer server) { return server.overworld().getGameTime(); }
    public static boolean eligible(ServerPlayer player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator() && !player.isSleeping()
                && player.level().dimension().equals(Level.OVERWORLD) && player.level().getDifficulty() != Difficulty.PEACEFUL
                && !PowerIntroService.isActive(player) && !TimeIntroService.isVisiting(player)
                && !TemporalSnapshotService.isTemporalDimension(player.serverLevel())
                && !EclipseService.arePowersSuppressed(player)
                && PlayerAbilityService.getData(player).assignedPower().filter(entry -> entry.getValue().isUnlocked()).isPresent();
    }
    /** Only successful ability uses draw his attention; no cooldown/failed-input spam. */
    public static void notice(ServerPlayer player) {
        if (!HeroesEvolvedConfig.COMMON.sylarEnabled.get() || !eligible(player)) return;
        var data = SylarStoryData.get(player.getServer()); var trail = data.trail(player.getUUID());
        trail.attention = Math.min(12, trail.attention + 1); data.setDirty(); remember(player, now(player.getServer()));
    }
    private static void remember(ServerPlayer player, long time) {
        Deque<Step> route = ROUTES.computeIfAbsent(player.getUUID(), ignored -> new ArrayDeque<>());
        if (route.isEmpty() || route.getLast().position.distanceToSqr(player.position()) > 4) {
            route.addLast(new Step(player.position(), time)); while (route.size() > 12) route.removeFirst();
        }
    }
    public static void tick(MinecraftServer server) {
        long time = now(server);
        if (!HeroesEvolvedConfig.COMMON.sylarEnabled.get()) { dismiss(server); return; }
        SylarVoice.tick(server);
        if (time % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) if (eligible(player)) remember(player, time);
        if (active != null) { tickEncounter(server, time); return; }
        var data = SylarStoryData.get(server);
        if (time < data.quietUntil()) return;
        ServerPlayer chosen = null; int best = -1;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!eligible(player)) continue;
            var trail = data.trail(player.getUUID());
            if (trail.nextEncounter == 0) {
                trail.nextEncounter = time + HeroesEvolvedConfig.COMMON.sylarFirstEncounterSeconds.get() * 20L
                        + player.getRandom().nextInt(20 * 120); data.setDirty(); continue;
            }
            Deque<Step> route = ROUTES.get(player.getUUID());
            if (time < trail.nextEncounter || route == null || time - route.getLast().time > 20 * 60) continue;
            int interest = trail.attention + (player.serverLevel().isNight() ? 3 : 0);
            if (interest > best) { best = interest; chosen = player; }
        }
        if (chosen == null) return;
        var trail = data.trail(chosen.getUUID());
        if (trail.chapter == 0) {
            SylarVoice.narrate(chosen, "story.heroesevolved.sylar.watched"); clock(chosen, .16F);
            trail.chapter = 1; scheduleNext(chosen, trail, Beat.SIGHTING); data.setDirty();
        } else if (!start(chosen, trail.chapter >= 3, trail.chapter == 2)) {
            trail.nextEncounter = time + 20 * 30; data.setDirty();
        }
    }
    private static void tickEncounter(MinecraftServer server, long time) {
        Encounter encounter = active;
        SylarEntity hunter = encounter.hunter;
        ServerPlayer player = server.getPlayerList().getPlayer(encounter.victim);
        if (!hunter.isAlive() || hunter.isRemoved() || player == null || !eligible(player) || player.level() != hunter.level()) {
            end(server, false); return;
        }
        senseNearby(server, hunter, time);
        long age = time - encounter.began;
        double distance = hunter.distanceToSqr(player);
        if (distance > 112 * 112 || age > 20 * (hunter.hunting() ? 150 : encounter.speaks ? 24 : 14)) {
            if (hunter.hunting()) SylarVoice.narrate(player, "story.heroesevolved.sylar.lost");
            end(server, hunter.hunting() ? Outcome.ESCAPED : Outcome.WITHDREW);
            return;
        }
        if (hunter.hunting()) {
            if (time % 80 == 0 && distance < 40 * 40) clock(player, .17F);
            return;
        }
        hunter.getLookControl().setLookAt(player, 30, 30);
        if (age >= 40 && !encounter.footsteps) {
            encounter.footsteps = true;
            Deque<Step> route = ROUTES.get(player.getUUID());
            if (route != null) for (Step step : route) {
                double away = step.position.distanceToSqr(player.position());
                if (away >= 8 * 8 && away <= 24 * 24 && player.serverLevel().hasChunkAt(BlockPos.containing(step.position))) {
                    player.serverLevel().playSound(null, step.position.x, step.position.y, step.position.z,
                            net.minecraft.sounds.SoundEvents.DEEPSLATE_STEP, SoundSource.HOSTILE, .4F, .8F);
                    break;
                }
            }
        }
        if (age >= 60 && !encounter.announced) {
            encounter.announced = true;
            SylarVoice.narrate(player, encounter.ambush ? "story.heroesevolved.sylar.recognized" : "story.heroesevolved.sylar.figure");
            // The second sighting is the first time he speaks.
            if (encounter.speaks) SylarVoice.say(player, "story.heroesevolved.sylar.say.first");
            clock(player, .19F);
        }
        if (encounter.ambush && age >= 120) { hunt(encounter, player); return; }
        Vec3 toward = hunter.getEyePosition().subtract(player.getEyePosition()).normalize();
        boolean looking = player.getLookAngle().dot(toward) > .965 && player.hasLineOfSight(hunter);
        encounter.seenTicks = looking ? encounter.seenTicks + 20 : 0;
        if (!encounter.ambush && age >= (encounter.speaks ? 200 : 80) && (encounter.seenTicks >= 60 || distance < 18 * 18)) {
            SylarVoice.narrate(player, "story.heroesevolved.sylar.gone"); end(server, Outcome.WITHDREW);
        }
    }
    private static void hunt(Encounter encounter, ServerPlayer player) {
        encounter.hunter.hunt(player); encounter.announced = true;
        SylarVoice.narrate(player, "story.heroesevolved.sylar.hunt");
        SylarVoice.say(player, opener(player));
        ScreenTintPayload.send(player, 0x38201820, 45, ScreenTintPayload.Style.VIGNETTE);
    }

    /** What he says when a hunt begins: he remembers you, or he names what he wants. */
    static String opener(ServerPlayer player) {
        var trail = SylarStoryData.get(player.getServer()).trail(player.getUUID());
        if (trail.fled > 0 && trail.hunts > 0) return "story.heroesevolved.sylar.say.return_fled";
        if (trail.escapes > 0) return "story.heroesevolved.sylar.say.return_escaped";
        String power = PlayerAbilityService.getData(player).assignedPower().map(e -> e.getKey().getPath()).orElse("");
        return OPENERS.contains(power) ? "story.heroesevolved.sylar.say.want." + power : "story.heroesevolved.sylar.say.want";
    }

    private static final Set<String> OPENERS = Set.of("quake", "flight", "cryokinesis", "electrokinesis", "telekinesis", "telepathy",
            "regeneration", "time_manipulation", "radiation", "sonic_resonance", "superspeed", "precognition", "pyrokinesis",
            "invisibility", "phasing", "empathic_mimicry");
    /** Admin preview uses the same loaded, collision-safe spawn rules and singleton as natural encounters. */
    public static boolean start(ServerPlayer player, boolean ambush) { return start(player, ambush, false); }

    public static boolean start(ServerPlayer player, boolean ambush, boolean speaks) {
        if (active != null || !eligible(player) || !HeroesEvolvedConfig.COMMON.sylarEnabled.get()) return false;
        Vec3 position = findWatchPosition(player);
        if (position == null) return false;
        return beginAt(player, position, ambush, speaks);
    }
    static boolean beginAt(ServerPlayer player, Vec3 position, boolean ambush) { return beginAt(player, position, ambush, false); }

    static boolean beginAt(ServerPlayer player, Vec3 position, boolean ambush, boolean speaks) {
        if (active != null || !eligible(player)) return false;
        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(BlockPos.containing(position)) || !level.getWorldBorder().isWithinBounds(BlockPos.containing(position))) return false;
        SylarEntity hunter = ModEntities.SYLAR.get().create(level);
        if (hunter == null) return false;
        hunter.setPos(position);
        if (!level.noCollision(hunter)) return false;
        hunter.watch(player);
        if (!level.addFreshEntity(hunter)) return false;
        active = new Encounter(hunter, player, now(player.getServer()), ambush, speaks);
        senseNearby(player.getServer(), hunter, now(player.getServer()));
        return true;
    }
    private static Vec3 findWatchPosition(ServerPlayer player) {
        Deque<Step> route = ROUTES.get(player.getUUID());
        if (route != null) for (Step step : route) {
            double distance = step.position.distanceToSqr(player.position());
            if (distance > 32 * 32 && distance < 72 * 72) {
                Vec3 found = surface(player, step.position.x, step.position.z);
                if (found != null) return found;
            }
        }
        // A bounded search on already-loaded columns. Never load a ring of distant chunks.
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2, radius = 40 + player.getRandom().nextInt(25);
            Vec3 found = surface(player, player.getX() + Math.cos(angle) * radius, player.getZ() + Math.sin(angle) * radius);
            if (found != null) return found;
        }
        return null;
    }
    private static Vec3 surface(ServerPlayer player, double x, double z) {
        ServerLevel level = player.serverLevel();
        BlockPos column = BlockPos.containing(x, player.getY(), z);
        if (!level.hasChunkAt(column) || !level.getWorldBorder().isWithinBounds(column)) return null;
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        BlockPos feet = new BlockPos(column.getX(), surface, column.getZ());
        if (Math.abs(surface - player.getY()) > 24 || !level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                || !level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()) return null;
        Vec3 pos = Vec3.atBottomCenterOf(feet);
        // Appear beyond the player's central view, ready to be discovered when they turn.
        if (player.getLookAngle().dot(pos.add(0, 1.6, 0).subtract(player.getEyePosition()).normalize()) > .65) return null;
        AABB box = new AABB(pos.x - .3, pos.y, pos.z - .3, pos.x + .3, pos.y + 1.8, pos.z + .3);
        if (!level.noCollision(box) || !level.getWorldBorder().isWithinBounds(box)) return null;
        if (level.clip(new ClipContext(player.getEyePosition(), pos.add(0, 1.6, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) return null;
        return pos;
    }
    public static boolean isTelepath(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !EclipseService.arePowersSuppressed(player)
                && !PowerIntroService.isActive(player) && !TimeIntroService.isVisiting(player)
                && PlayerAbilityService.getData(player).assignedPower()
                    .filter(entry -> entry.getKey().equals(ModAbilities.TELEPATHY_ID) && entry.getValue().isUnlocked()).isPresent();
    }
    /** Senses a mind, not an attack target: works while he watches quietly and through solid walls. */
    public static void senseNearby(MinecraftServer server, SylarEntity hunter, long time) {
        for (ServerPlayer telepath : server.getPlayerList().getPlayers()) {
            if (telepath.level() != hunter.level() || !isTelepath(telepath) || telepath.distanceToSqr(hunter) > 64 * 64) continue;
            Long last = SENSED.get(telepath.getUUID());
            if (last != null && time - last < 20 * 15) continue;
            SENSED.put(telepath.getUUID(), time);
            Vec3 delta = hunter.position().subtract(telepath.position());
            String direction = Math.abs(delta.x) > Math.abs(delta.z) ? (delta.x > 0 ? "east" : "west") : (delta.z > 0 ? "south" : "north");
            telepath.sendSystemMessage(Component.translatable("story.heroesevolved.sylar.sense",
                    Component.translatable("story.heroesevolved.sylar.direction." + direction)).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
            PrivateGlow.glow(telepath, hunter, 40); clock(telepath, .13F);
        }
    }
    public static void provoked(SylarEntity hunter) {
        if (active == null || active.hunter != hunter || hunter.hunting()) return;
        ServerPlayer victim = hunter.getServer().getPlayerList().getPlayer(active.victim);
        if (victim != null && eligible(victim)) hunt(active, victim);
    }
    public static void acquire(SylarEntity hunter, ServerPlayer victim) {
        if (!HeroesEvolvedConfig.COMMON.sylarCanAcquirePowers.get() || active == null || active.hunter != hunter) return;
        PlayerAbilityService.getData(victim).assignedPower().filter(entry -> entry.getValue().isUnlocked()).ifPresent(entry -> {
            var data = SylarStoryData.get(victim.getServer());
            if (!data.learn(entry.getKey())) return;
            // Intuitive aptitude COPIES knowledge. Never reassign a player's exclusive story role.
            SylarVoice.say(victim, "story.heroesevolved.sylar.say.acquired");
            SylarVoice.narrate(victim, "story.heroesevolved.sylar.acquired");
            for (ServerPlayer witness : victim.serverLevel().players()) {
                if (witness == victim || witness.distanceToSqr(hunter) > 64 * 64) continue;
                SylarVoice.narrate(witness, "story.heroesevolved.sylar.witness");
            }
            if (entry.getKey().equals(ModAbilities.RADIATION_ID)) {
                for (ServerPlayer witness : victim.getServer().getPlayerList().getPlayers())
                    if (witness.getData(ModDataAttachments.PLAYER_STORY_PROGRESS) >= 4 || witness.getData(ModDataAttachments.PLAYER_SEEN_FUTURE_CITY))
                        SylarVoice.narrate(witness, "story.heroesevolved.sylar.future");
            }
        });
    }
    public static void defeated(SylarEntity hunter, Entity killer) {
        if (active == null || active.hunter != hunter) return;
        MinecraftServer server = hunter.getServer(); SylarStoryData.get(server).defeated(now(server));
        for (ServerPlayer witness : server.getPlayerList().getPlayers())
            if (witness.level() == hunter.level() && witness.distanceToSqr(hunter) < 96 * 96) {
                if (hunter.hunting()) SylarVoice.say(witness, "story.heroesevolved.sylar.say.defeated");
                SylarVoice.narrate(witness, "story.heroesevolved.sylar.defeated");
            }
        end(server, Outcome.DEFEATED);
    }

    /** He got away, wounded: no reprieve, and he will remember who did it. */
    public static void fled(SylarEntity hunter, ServerPlayer victim) {
        if (active == null || active.hunter != hunter) return;
        MinecraftServer server = hunter.getServer();
        for (ServerPlayer witness : server.getPlayerList().getPlayers())
            if (witness.level() == hunter.level() && witness.distanceToSqr(hunter) < 96 * 96) {
                SylarVoice.say(witness, "story.heroesevolved.sylar.say.escape");
                SylarVoice.narrate(witness, "story.heroesevolved.sylar.fled");
            }
        var data = SylarStoryData.get(server);
        data.trail(victim.getUUID()).fled++;
        data.setDirty();
        end(server, Outcome.FLED);
    }
    /** How an encounter ended. */
    enum Outcome { CANCELLED, WITHDREW, ESCAPED, FLED, DEFEATED }

    /** Which beat comes next for a player, and so how long until it. */
    private enum Beat { SIGHTING, HUNT }

    private static void scheduleNext(ServerPlayer player, SylarStoryData.Trail trail, Beat beat) {
        var config = HeroesEvolvedConfig.COMMON;
        long base = beat == Beat.HUNT ? config.sylarHuntIntervalSeconds.get() : config.sylarSightingIntervalSeconds.get();
        long jitter = beat == Beat.HUNT ? 300 : 240;
        // A player who keeps using their powers draws him a little sooner (at most a fifth sooner).
        double interest = 1 - Math.min(12, trail.attention) / 60.0;
        long seconds = (long) (base * interest) + player.getRandom().nextInt((int) jitter);
        trail.nextEncounter = now(player.getServer()) + seconds * 20L;
        trail.attention = Math.max(0, trail.attention - 3);
    }

    private static void end(MinecraftServer server, boolean progressed) { end(server, progressed ? Outcome.WITHDREW : Outcome.CANCELLED); }

    private static void end(MinecraftServer server, Outcome outcome) {
        if (active == null) return;
        Encounter encounter = active; active = null;
        var data = SylarStoryData.get(server); var trail = data.trail(encounter.victim);
        boolean hunted = encounter.hunter.hunting();
        if (outcome != Outcome.CANCELLED) {
            if (hunted) {
                trail.hunts++;
                trail.chapter = Math.max(trail.chapter, trail.hunts >= 3 ? 5 : 4);
                if (outcome == Outcome.ESCAPED) trail.escapes++;
            } else {
                trail.chapter = Math.min(3, trail.chapter + 1);
            }
        }
        ServerPlayer victim = server.getPlayerList().getPlayer(encounter.victim);
        if (victim != null) scheduleNext(victim, trail, trail.chapter >= 3 ? Beat.HUNT : Beat.SIGHTING);
        else trail.nextEncounter = now(server) + 20L * 60 * 10;
        data.setDirty();
        encounter.hunter.bar().removeAllPlayers();
        if (encounter.hunter.isAlive() && !encounter.hunter.isRemoved()) {
            ((ServerLevel) encounter.hunter.level()).sendParticles(ParticleTypes.SMOKE, encounter.hunter.getX(), encounter.hunter.getY() + 1,
                    encounter.hunter.getZ(), 8, .2, .5, .2, .01);
            encounter.hunter.discard();
        }
    }
    public static void message(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
    public static void clock(ServerPlayer player, float volume) {
        player.playNotifySound(com.jayptucker.heroesevolved.sounds.ModSounds.SYLAR_WATCH.get(), SoundSource.HOSTILE, volume, 1);
    }
    public static SylarEntity hunter() { return active == null ? null : active.hunter; }
    public static void dismiss(MinecraftServer server) { if (active != null) end(server, false); }
    public static void forget(ServerPlayer player) {
        if (active != null && active.victim.equals(player.getUUID())) end(player.getServer(), false);
        ROUTES.remove(player.getUUID()); SENSED.remove(player.getUUID()); SylarVoice.forget(player.getUUID());
    }
    public static void clear(MinecraftServer server) { dismiss(server); ROUTES.clear(); SENSED.clear(); SylarVoice.clear(); }
}
