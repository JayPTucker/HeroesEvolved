package com.jayptucker.heroesevolved.speed;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.OverrunPayload;
import com.jayptucker.heroesevolved.network.OverrunStatusPayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.time.RewindTravel;
import com.jayptucker.heroesevolved.time.TimeRewindBlocks;
import com.jayptucker.heroesevolved.time.TimeRewindPlayers;
import com.jayptucker.heroesevolved.time.TimeRewindContainers;
import com.jayptucker.heroesevolved.time.TimeRewindEntities;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Overrun: outrunning the last minute. Nothing to press; it belongs to the fastest speedsters only (tier 5,
 * Power Level 15). Run flat out long enough and time starts to smear around you (the wake). Keep going and a
 * breach opens in the air ahead; run on through it and you come out of it a minute ago. Everything within
 * 64 blocks of where you were then is as it was: blocks, chests, creatures, people, and you, with what you
 * carried and how hurt you were. Ease off and the breach closes. Once every ten minutes.
 *
 * <p>The history is the same one Time Manipulation's Rewind keeps (see {@code TimeRewindService}), kept for a
 * minute instead of fifteen seconds and sampled once a second.</p>
 */
public final class SpeedOverrun {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("heroesevolved", "speed_overrun");
    public static final int TIER = 5;
    public static final int REWIND_SECONDS = 60, REWIND_TICKS = REWIND_SECONDS * 20, SAMPLE_TICKS = 20;
    /** Ticks flat out before the breach opens; when the wake begins to show; how long you must run on through the breach. */
    public static final int CHARGE_TICKS = 400, WAKE_TICKS = 160, BREACH_TICKS = 60;
    /** Flat out: at least this fast (blocks a tick), with at least this much Momentum. */
    public static final double TOP_SPEED = 1.0;
    public static final int TOP_MOMENTUM = 90;
    /** Ticks you may ease off (a corner, a jump) before the charge starts to fade or the breach closes. */
    private static final int SLACK = 10;
    public static final int COOLDOWN_TICKS = 20 * 60 * 10;

    private record Sample(long time, ResourceKey<Level> dimension, Vec3 position, float yaw, float pitch, long blockRevision,
                          TimeRewindEntities.Frame entities, TimeRewindContainers.Frame containers) {}

    private static final class State {
        final Deque<Sample> samples = new ArrayDeque<>();
        int charge, slack, breach = -1, sentStage = OverrunPayload.NONE, sentValue = -1;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** What each speedster's HUD was last told (state * 100000 + value), so it is told only of changes. */
    private static final Map<UUID, Integer> TOLD = new HashMap<>();

    private SpeedOverrun() {}

    /** A speedster fast enough for it, somewhere it may happen. */
    public static boolean eligible(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && SpeedService.awakened(p) && PlayerProgressionService.getLevel(p) >= TIER
                && !TimeIntroService.isVisiting(p) && !TimeIntroService.isCity(p.level());
    }

    static void tick(MinecraftServer server, long now) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) tickPlayer(p, now);
    }

    /** One tick for one speedster: record, build the charge while flat out, open the breach, cross it. */
    public static void tickPlayer(ServerPlayer p, long now) {
        // A death does not end the history: running back can undo it. Nor does travelling back through it.
        if (!p.isAlive() || com.jayptucker.heroesevolved.time.RewindTravel.travelling(p)) { interrupt(p); return; }
        if (!eligible(p)) {
            if (STATES.containsKey(p.getUUID())) forget(p);
            tell(p, SpeedService.awakened(p) && !TimeIntroService.isVisiting(p) ? OverrunStatusPayload.LOCKED : OverrunStatusPayload.HIDDEN, 0);
            return;
        }
        State s = STATES.computeIfAbsent(p.getUUID(), id -> new State());
        if (now % SAMPLE_TICKS == 0) record(p, s, now);
        boolean ready = !CooldownService.isOnCooldown(p, ID) && target(s, now) != null;
        boolean running = ready && PowerRules.canAct(p) && SpeedService.flatOut(p, TOP_SPEED, TOP_MOMENTUM);
        s.slack = running ? 0 : s.slack + 1;
        boolean easing = s.slack > SLACK;
        if (s.breach >= 0) {
            if (easing || !ready) collapse(p, s);
            else if (++s.breach >= BREACH_TICKS) cross(p, s, now);
        } else if (running) {
            if (++s.charge >= CHARGE_TICKS) open(p, s);
        } else if (easing) {
            s.charge = Math.max(0, s.charge - 4);
        }
        show(p, s);
        status(p, s, now);
    }

    /** What the runner's HUD shows: the wait (history or cooldown), the charge, or the breach. */
    private static void status(ServerPlayer p, State s, long now) {
        long cooldown = CooldownService.getRemainingTicks(p, ID);
        if (s.breach >= 0) tell(p, OverrunStatusPayload.BREACH, s.breach * 1000 / BREACH_TICKS);
        else if (cooldown > 0) tell(p, OverrunStatusPayload.COOLDOWN, (int) ((cooldown + 19) / 20));
        else if (target(s, now) == null) tell(p, OverrunStatusPayload.GATHERING, historyLeft(s, now));
        // The charge is told in steps of a twentieth, so a run does not send a packet every tick.
        else tell(p, OverrunStatusPayload.READY, s.charge * 20 / CHARGE_TICKS * 50);
    }

    /** Seconds until the history reaches back a full minute. */
    private static int historyLeft(State s, long now) {
        if (s.samples.isEmpty()) return REWIND_SECONDS;
        long ready = s.samples.getFirst().time + REWIND_TICKS;
        return (int) Math.clamp((ready - now + 19) / 20, 1, REWIND_SECONDS);
    }

    private static void tell(ServerPlayer p, int state, int value) {
        int code = state * 100000 + value;
        Integer told = TOLD.put(p.getUUID(), code);
        if (told == null || told != code) OverrunStatusPayload.send(p, state, value);
    }

    private static void record(ServerPlayer p, State s, long now) {
        // The block and creature journal is shared with Rewind. If it was dropped (a logout, a rewind), the
        // samples taken against it are no longer complete.
        if (!s.samples.isEmpty() && !TimeRewindBlocks.isTracking(p.getUUID())) s.samples.clear();
        long revision = TimeRewindBlocks.track(p, TimeRewindBlocks.LONGEST_RETENTION_TICKS);
        // Everywhere you pass is watched for the whole minute, not only while you are there.
        TimeRewindBlocks.anchor(p, now);
        s.samples.addLast(new Sample(now, p.level().dimension(), p.position(), p.getYRot(), p.getXRot(), revision,
                TimeRewindEntities.capture(p), TimeRewindContainers.capture(p)));
        while (!s.samples.isEmpty() && s.samples.getFirst().time < now - REWIND_TICKS - SAMPLE_TICKS * 3) s.samples.removeFirst();
    }

    /** The moment a minute ago (in whatever world you were in then), if the history reaches back that far unbroken. */
    private static Sample target(State s, long now) {
        if (s.samples.isEmpty() || s.samples.getLast().time < now - SAMPLE_TICKS * 2) return null;
        long cutoff = now - REWIND_TICKS;
        for (Iterator<Sample> it = s.samples.descendingIterator(); it.hasNext(); ) {
            Sample sample = it.next();
            if (sample.time <= cutoff)
                return sample.time >= cutoff - SAMPLE_TICKS * 2 ? sample : null;
        }
        return null;
    }

    private static void open(ServerPlayer p, State s) {
        s.breach = 0;
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 0.5F);
        level.playSound(null, p.blockPosition(), ModSounds.TIME_SLOW.get(), SoundSource.PLAYERS, 0.8F, 1.4F);
    }

    /** You eased off: the breach closes, and the wake drops back to where it first began to show. */
    private static void collapse(ServerPlayer p, State s) {
        s.breach = -1;
        s.charge = Math.min(s.charge, WAKE_TICKS);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    /** Through the breach: a minute ago, everything within reach as it was then. */
    private static boolean cross(ServerPlayer p, State s, long now) {
        s.breach = -1;
        s.charge = 0;
        ServerLevel level = p.serverLevel();
        Sample target = target(s, now);
        ServerLevel home = target == null ? null : p.getServer().getLevel(target.dimension);
        TimeRewindBlocks.Plan blocks = home == null ? null : TimeRewindBlocks.plan(p, target.blockRevision, target.dimension);
        if (target == null || blocks == null || !target.entities.complete() || !target.containers.complete()
                || !TimeRewindBlocks.canOccupy(p, p.getBoundingBox().move(target.position.subtract(p.position())), blocks)) {
            collapse(p, s);
            return false;
        }
        Vec3 from = p.position();
        Vec3 heading = SpeedService.horizontal(p.getLookAngle());
        SetPiecePayload.send(level, Piece.OVERRUN_DEPART, from, from.add(heading), 1, null);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8F, 0.55F);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.7F, 1.6F);

        // The world snaps back at once. Everyone it reaches (the runner, anyone near where they were a minute
        // ago, anyone whose death in reach it undoes) is carried back through their own minute, across worlds if
        // they crossed any, and lands as they were.
        UUID runner = p.getUUID();
        Sample arrival = target;
        TimeRewindEntities.Landing landing = RewindTravel.landing(target.time, RewindTravel.MINUTE_TICKS, current -> {
            if (current.getUUID().equals(runner)) landed(current, arrival);
        });
        TimeRewindBlocks.restore(p, blocks);
        TimeRewindContainers.restore(p, home, target.containers);
        Set<UUID> restored = TimeRewindEntities.restore(p, home, target.entities, target.time, landing);
        TimeRewindPlayers.undoDeaths(p, target.time, target.dimension, target.position, restored, landing);
        SpeedService.landed(p);

        CooldownService.startCooldown(p, ID, COOLDOWN_TICKS);
        MasteryService.markPowerUse(p, ModAbilities.SUPERSPEED_ID);
        // The minute you outran never happened: history starts again from here.
        s.samples.clear();
        TimeRewindBlocks.forget(p.getUUID());
        TimeRewindEntities.forget(p.getUUID());
        return true;
    }

    /** The runner arrives a minute ago, exactly where they stood. */
    private static void landed(ServerPlayer p, Sample target) {
        ServerLevel level = p.getServer().getLevel(target.dimension);
        if (level != null) p.teleportTo(level, target.position.x, target.position.y, target.position.z, target.yaw, target.pitch);
        p.resetFallDistance();
        SpeedService.landed(p);
        level = p.serverLevel();
        Vec3 to = p.position();
        float yaw = target.yaw * Mth.DEG_TO_RAD;
        SetPiecePayload.send(level, Piece.OVERRUN_ARRIVE, to, to.add(-Mth.sin(yaw), 0, Mth.cos(yaw)), 1, null);
        level.playSound(null, to.x, to.y, to.z, ModSounds.TIME_UNSLOW.get(), SoundSource.PLAYERS, 1.0F, 0.7F);
        level.playSound(null, to.x, to.y, to.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6F, 0.8F);
        ScreenTintPayload.send(p, 0xB0EEE6FF, 16, ScreenTintPayload.Style.FLASH);
        CameraShakePayload.sendAround(level, to, 16, 0.5F, 12);
        p.displayClientMessage(Component.literal("One minute ago.").withStyle(ChatFormatting.GOLD), true);
    }

    /** Tells everyone watching what to draw: nothing, the wake (and how strong), or the breach (and how long open). */
    private static void show(ServerPlayer p, State s) {
        int stage = s.breach >= 0 ? OverrunPayload.BREACH : s.charge >= WAKE_TICKS ? OverrunPayload.WAKE : OverrunPayload.NONE;
        int value = stage == OverrunPayload.BREACH ? s.breach : stage == OverrunPayload.WAKE ? s.charge * 1000 / CHARGE_TICKS : 0;
        boolean due = stage != s.sentStage || stage == OverrunPayload.WAKE && Math.abs(value - s.sentValue) >= 25
                || stage == OverrunPayload.BREACH && value - s.sentValue >= 20;
        if (!due) return;
        s.sentStage = stage; s.sentValue = value;
        OverrunPayload.send(p, stage, value);
    }

    public static void forget(ServerPlayer p) {
        TOLD.remove(p.getUUID());
        State s = STATES.remove(p.getUUID());
        if (s != null && s.sentStage != OverrunPayload.NONE) OverrunPayload.send(p, OverrunPayload.NONE, 0);
    }

    /** The run is broken (a death, a trip through a portal, being carried back), but the minute behind you is kept. */
    public static void interrupt(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s == null) return;
        s.charge = 0; s.breach = -1; s.slack = 0;
        if (s.sentStage != OverrunPayload.NONE) { s.sentStage = OverrunPayload.NONE; s.sentValue = -1; OverrunPayload.send(p, OverrunPayload.NONE, 0); }
    }

    public static void clear() { STATES.clear(); TOLD.clear(); }

    // ------------------------------------------------------------------
    // For tests
    // ------------------------------------------------------------------

    /** Records a sample as if it were game tick {@code now}. */
    public static void recordAt(ServerPlayer p, long now) {
        record(p, STATES.computeIfAbsent(p.getUUID(), id -> new State()), now);
    }

    /** Crosses the breach as if at game tick {@code now}; false if the history does not reach back a minute. */
    public static boolean crossAt(ServerPlayer p, long now) {
        State s = STATES.get(p.getUUID());
        return s != null && cross(p, s, now);
    }

    public static int charge(ServerPlayer p) { State s = STATES.get(p.getUUID()); return s == null ? 0 : s.charge; }

    public static boolean breachOpen(ServerPlayer p) { State s = STATES.get(p.getUUID()); return s != null && s.breach >= 0; }
}
