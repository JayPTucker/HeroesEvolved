package com.jayptucker.heroesevolved.vibe;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.BreachActorsPayload;
import com.jayptucker.heroesevolved.network.BreachViewPayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import com.jayptucker.heroesevolved.telepathy.MindVoiceRoutes;
import com.jayptucker.heroesevolved.time.SpaceTimeService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Breaches: two linked mouths anyone friendly can walk through, either way. A breach opens to where the viber looks
 * (Shift + Breach, or the wheel's centre) or to one of their frequencies ({@link VibeFrequencies}), however far, and
 * Glimpse opens one that can be looked through but not crossed. Each mouth shows the terrain round the other one:
 * when a breach opens, the server captures a {@link BreachSnapshot} round each mouth and sends it to players near the
 * other, and the client draws it through the opening.
 */
public final class VibeBreaches {
    private static final int LOAD_TIMEOUT = 160, LIFETIME = 180, FAR_LIFETIME = 300, GLIMPSE_LIFETIME = 200;
    private static final int TICKET_RADIUS = 3;
    /** How far from a mouth players are shown the view through it. */
    static final double VIEW_RANGE = 112;
    private static final TicketType<UUID> BREACH_TICKET = TicketType.create("heroesevolved_breach", Comparator.<UUID>naturalOrder());
    private static final Map<UUID, Gate> GATES = new HashMap<>();
    private static final Set<UUID> CROSSING = new HashSet<>();
    private static int serial;

    /** A grounded or floating opening, with a special floor/ceiling pair for dropping through a surface. */
    record Mouth(ServerLevel level, Vec3 feet, Vec3 direction, boolean flat, boolean airborne, float yaw) {
        static Mouth upright(ServerLevel level, Vec3 feet, Vec3 direction) {
            return grounded(level, feet, direction, VibeGeometry.yaw(direction));
        }

        static Mouth grounded(ServerLevel level, Vec3 feet, Vec3 direction, float yaw) {
            return new Mouth(level, feet, direction, false, false, yaw);
        }

        static Mouth floating(ServerPlayer player, Vec3 center, Vec3 direction) {
            return new Mouth(player.serverLevel(), center, direction, false, true, player.getYRot());
        }

        static Mouth flat(ServerLevel level, Vec3 feet, float heading) {
            // Preserve the opening's axes at vertical pitch, including the yaw of a tuned frequency.
            return new Mouth(level, feet, Vec3.directionFromRotation(89.99F, heading), true, false, heading);
        }

        Vec3 center() {
            if (airborne) return feet;
            return feet.add(0, flat ? .04 : VibeGeometry.PORTAL_HEIGHT * direction.horizontalDistance(), 0);
        }

        Vec3 arrival(LivingEntity traveler, double side) {
            if (!airborne) return feet.add(Vec3.directionFromRotation(0, yaw).scale(1.14 * side));
            double halfHeight = dimensions(traveler).height() / 2;
            return center().add(direction.scale(1.14 * side)).subtract(0, halfHeight, 0);
        }

        Vec3 rotate(Vec3 vector, Mouth to) {
            return VibeGeometry.rotate(vector, direction, yaw, to.direction, to.yaw);
        }

        ChunkPos chunk() { return new ChunkPos(BlockPos.containing(feet)); }
    }

    static final Vec3 DOWN = new Vec3(0, -1, 0);
    /** How far above a destination's ground a ceiling mouth opens, and the pitch past which a breach opens underfoot. */
    static final double DROP = 3, FLOOR_PITCH = 60;
    /** Ticks after a drop before the same traveler can be taken by a flat mouth again (no falling straight back). */
    private static final int DROP_GAP = 30;

    /** An aimed breach (where you look), a breach to a frequency, or a glimpse (a window you can't cross). */
    enum Kind { AIMED, FREQUENCY, GLIMPSE }

    private record Approach(ServerLevel world, Vec3 center) {}

    /** Where a wave passes into one mouth ({@code through}) and where, heading which way, it leaves the other. */
    record Seam(Vec3 through, Vec3 out, Vec3 direction) {}

    /** A physical projectile's nearest crossing, independent of who fired it. */
    record ShotCrossing(Mouth from, Mouth to, Vec3 hit) {
        Vec3 rotate(Vec3 vector) { return from.rotate(vector, to); }

        Vec3 map(Vec3 point) {
            return to.center().add(rotate(point.subtract(from.center())));
        }
    }

    /** What one mouth shows: the terrain round the other mouth, and who has been sent it. */
    private static final class View {
        final int id;
        final Mouth here, there;
        BreachSnapshot snapshot;
        final Map<UUID, ServerPlayer> sent = new HashMap<>();

        View(int id, Mouth here, Mouth there) {
            this.id = id;
            this.here = here;
            this.there = there;
        }
    }

    private record Ticket(ServerLevel level, ChunkPos chunk) {}

    private static final class Gate {
        final ServerPlayer owner;
        /** The near mouth turns with the viber's look while a keyed breach is still opening, so neither mouth is final. */
        Mouth entry, exit;
        final Kind kind;
        final boolean sanctuary;
        final long started;
        final int lifetime, id;
        /** How long the breach takes to open: a keyed breach is channelled ({@link VibeGeometry#channelTicks}). */
        int channel = VibeGeometry.BREACH_OPEN_TICKS;
        /** Opened from the Breach key (held or from the wheel): it is channelled from the palm and follows the look. */
        boolean keyed;
        /** Held open by the key: letting go before it opens collapses it (and gives the stamina back). */
        boolean held;
        double paid;
        /** Where the viber looked when it began: a keyed aimed breach re-aims its exit only if that changed. */
        Vec3 look = Vec3.ZERO;
        View[] views;
        final List<Ticket> tickets = new ArrayList<>();
        final Map<UUID, Approach> previous = new HashMap<>();
        final Map<UUID, Long> dropped = new HashMap<>();
        long opened = -1;

        Gate(ServerPlayer owner, Mouth entry, Mouth exit, Kind kind, boolean sanctuary) {
            this.owner = owner;
            this.entry = entry;
            this.exit = exit;
            this.kind = kind;
            this.sanctuary = sanctuary;
            started = entry.level().getGameTime();
            lifetime = kind == Kind.GLIMPSE ? GLIMPSE_LIFETIME : kind == Kind.FREQUENCY ? FAR_LIFETIME : LIFETIME;
            id = ++serial & 0x3FFFFFFF;
            views = views();
        }

        View[] views() { return new View[]{new View(id * 2, entry, exit), new View(id * 2 + 1, exit, entry)}; }

        boolean flat() { return entry.flat(); }

        /** Keeps both mouths' chunks loaded. Each ticket is remembered, so the ones released are exactly those added. */
        void ticket(boolean open) {
            if (open) {
                hold(entry);
                hold(exit);
                return;
            }
            for (Ticket ticket : tickets) VibeBreaches.ticket(ticket.level(), ticket.chunk(), owner.getUUID(), false);
            tickets.clear();
        }

        void hold(Mouth mouth) {
            Ticket ticket = new Ticket(mouth.level(), mouth.chunk());
            if (tickets.contains(ticket)) return;
            tickets.add(ticket);
            VibeBreaches.ticket(ticket.level(), ticket.chunk(), owner.getUUID(), true);
        }
    }

    /** A press of the Breach key, until it is let go: a tap opens the wheel, a hold channels a breach. */
    private static final class Press {
        /** Kept, not looked up: game-test players aren't in the player list. */
        final ServerPlayer player;
        final long at;
        final boolean shift;
        /** The press already did something (closed a breach): its release does nothing. */
        final boolean handled;
        boolean fired;

        Press(ServerPlayer player, long at, boolean shift, boolean handled) {
            this.player = player;
            this.at = at;
            this.shift = shift;
            this.handled = handled;
        }
    }

    /** How long the Breach key must be held before it starts channelling a breach instead of opening the wheel. */
    public static final int HOLD_TICKS = 5;
    private static final Map<UUID, Press> PRESSES = new HashMap<>();
    /** True while the Breach key's own paid press runs, so the breach it opens is channelled. */
    private static boolean keyed;

    private VibeBreaches() {}

    static void ticket(ServerLevel level, ChunkPos chunk, UUID owner, boolean open) {
        var chunks = level.getChunkSource();
        if (open) chunks.addRegionTicket(BREACH_TICKET, chunk, TICKET_RADIUS, owner, true);
        else chunks.removeRegionTicket(BREACH_TICKET, chunk, TICKET_RADIUS, owner, true);
    }

    /**
     * The Breach key's paid press, which always comes from a pick: a slot on the frequency wheel, -1 for where you
     * look, or {@link VibeFrequencies#VISION} for what a vision just showed. A hold picks for itself
     * ({@link #fire}). The breach it opens is channelled from the palm and opens slowly.
     */
    public static boolean breach(AbilityUseContext context) {
        ServerPlayer player = context.player();
        Integer pick = VibeFrequencies.picked(player, false);
        boolean was = keyed;
        keyed = true;
        try {
            boolean opened;
            if (pick == null || pick == -1) opened = open(player, false);
            else if (pick == VibeFrequencies.VISION) opened = openToVision(player);
            else opened = openTo(player, pick, Kind.FREQUENCY);
            if (opened && pick != null && pick != VibeFrequencies.VISION) VibeFrequencies.remember(player, pick);
            return opened;
        } finally {
            keyed = was;
        }
    }

    /**
     * The Breach key going down, always free. A press while a breach is open (or a destination is loading) closes it.
     * Otherwise the press is remembered: let go quickly and the wheel opens ({@link #breachRelease}); keep holding for
     * {@link #HOLD_TICKS} and a breach starts to channel toward the last frequency used ({@link #fire}). False lets a
     * wheel pick go on to {@link #breach}.
     */
    public static boolean breachFollowUp(AbilityUseContext context) {
        ServerPlayer player = context.player();
        if (VibeFrequencies.picked(player, false) != null) return false;
        long now = player.serverLevel().getGameTime();
        if (close(player)) {
            PRESSES.put(player.getUUID(), new Press(player, now, context.modifierHeld(), true));
            return true;
        }
        if (!canOpen(player)) return false;
        PRESSES.put(player.getUUID(), new Press(player, now, context.modifierHeld(), false));
        return true;
    }

    /** The Breach key let go: a tap opens the wheel, and a held breach that hasn't opened yet collapses. */
    public static void breachRelease(AbilityUseContext context) {
        ServerPlayer player = context.player();
        Press press = PRESSES.remove(player.getUUID());
        if (press == null || press.handled) return;
        if (!press.fired) {
            // A borrowed Vibe has no wheel: a tap opens where you look, as a hold does.
            if (!VibeFrequencies.wheel(player, com.jayptucker.heroesevolved.network.FrequencyListPayload.BREACH)) fire(player, press);
            return;
        }
        if (VibeFrequencies.cancelPending(player)) {
            fail(player, "let_go");
            return;
        }
        Gate gate = GATES.get(player.getUUID());
        if (gate == null || !gate.held || gate.opened >= 0) return;
        // Let go too soon: the tear pinches shut, and nothing was spent.
        double paid = gate.paid;
        close(player);
        if (paid > 0) com.jayptucker.heroesevolved.energy.PlayerEnergyService.restore(player, paid);
        com.jayptucker.heroesevolved.cooldown.CooldownService.reset(player,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "vibe_breach"));
        com.jayptucker.heroesevolved.network.PlayerPowerSyncService.sync(player);
        fail(player, "let_go");
    }

    /**
     * A hold long enough to be one: open toward what a vision just showed, else the last frequency the breach used, else
     * where you look. Shift (or a borrowed Vibe, with no frequencies) always opens where you look. The breach goes
     * through the key's normal paid press, so stamina, cooldown and distance cost apply.
     */
    private static void fire(ServerPlayer player, Press press) {
        press.fired = true;
        int target = press.shift || !VibeService.nativePower(player) ? -1 : VibeFrequencies.holdTarget(player);
        double before = com.jayptucker.heroesevolved.energy.PlayerEnergyService.getStamina(player);
        VibeFrequencies.press(player, target);
        Gate gate = GATES.get(player.getUUID());
        if (gate != null && gate.opened < 0)
            gate.paid = Math.max(0, before - com.jayptucker.heroesevolved.energy.PlayerEnergyService.getStamina(player));
    }

    /** Players whose breaches open as fast as an unkeyed one: tests that aren't about the channel. */
    private static final Set<UUID> HURRIED = new HashSet<>();

    /** Tests: this player's keyed breaches (now and later) open in {@link VibeGeometry#BREACH_OPEN_TICKS}. */
    static void hurry(ServerPlayer player) {
        HURRIED.add(player.getUUID());
        Gate gate = GATES.get(player.getUUID());
        if (gate != null && gate.opened < 0) gate.channel = Math.min(gate.channel, VibeGeometry.BREACH_OPEN_TICKS);
    }

    /** Tests: the Breach key, pressed with Shift and held long enough, opening where the viber looks. True if it opened. */
    static boolean shiftHold(ServerPlayer player) {
        if (com.jayptucker.heroesevolved.ability.service.AbilityUseService.activateAssignedAction(player,
                com.jayptucker.heroesevolved.ability.AbilitySlot.SECONDARY, true)
                != com.jayptucker.heroesevolved.ability.AbilityUseResult.SUCCESS) return false;
        hurry(player);
        holdNow(player);
        return isOpen(player);
    }

    /** Tests: the press waiting on the Breach key has been held long enough. */
    static void holdNow(ServerPlayer player) {
        Press press = PRESSES.get(player.getUUID());
        if (press != null && !press.handled && !press.fired) fire(player, press);
    }

    /** Tests: the Breach key let go. */
    static void letGo(ServerPlayer player) {
        breachRelease(new AbilityUseContext(player, com.jayptucker.heroesevolved.ability.registry.ModAbilities.VIBE_ID,
                Math.max(1, PlayerProgressionService.getLevel(player)), false));
    }

    /** Package-visible for tests: whether the Breach key is down and has started a breach. */
    static boolean holding(ServerPlayer player) {
        Press press = PRESSES.get(player.getUUID());
        return press != null && press.fired;
    }

    /** How far a channelled breach has opened, 0 to 1 (1 once open), or -1 with none. Package-visible for tests. */
    static float opening(ServerPlayer player) {
        Gate gate = GATES.get(player.getUUID());
        if (gate == null) return -1;
        if (gate.opened >= 0) return 1;
        return Mth.clamp((gate.entry.level().getGameTime() - gate.started) / (float) gate.channel, 0, 1);
    }

    /** Package-visible for tests: which way the near mouth faces. */
    static Vec3 facing(ServerPlayer player) {
        Gate gate = GATES.get(player.getUUID());
        return gate == null ? null : gate.entry.direction();
    }

    /** Glimpse's free press: close an open window, or open the wheel to choose where to look. */
    public static boolean glimpseFollowUp(ServerPlayer player) {
        if (VibeFrequencies.picked(player, true) != null) return false;
        if (close(player)) return true;
        return VibeFrequencies.wheel(player, com.jayptucker.heroesevolved.network.FrequencyListPayload.GLIMPSE);
    }

    /** Glimpse (Attune Frequency's slot): a window to a frequency picked on the wheel. */
    public static boolean glimpse(ServerPlayer player) {
        Integer pick = VibeFrequencies.picked(player, true);
        if (pick == null || pick < 0) return fail(player, "pick_frequency");
        return openTo(player, pick, Kind.GLIMPSE);
    }

    /** The extra stamina a press will cost: the distance to a frequency picked on the wheel. */
    public static int travelCost(AbilityUseContext context) {
        Integer pick = VibeFrequencies.picked(context.player(), false);
        if (pick == null || pick == -1) return 0;
        var frequency = pick == VibeFrequencies.VISION ? VibeVisions.spot(context.player()) : VibeFrequencies.get(context.player(), pick);
        return frequency == null ? 0 : VibeFrequencies.travel(context.player(), frequency);
    }

    public static boolean isOpen(ServerPlayer player) { return GATES.containsKey(player.getUUID()); }

    static boolean hasGates() { return !GATES.isEmpty(); }

    static final float FREQUENCY_TILT = 30;

    /** Looking down steeply opens the near mouth in the ground under the viber's feet. */
    static boolean downward(ServerPlayer player) { return player.getXRot() >= FLOOR_PITCH; }

    /** A walking-height opening, an opening in the air along the aim, or a hole underfoot. */
    private static Mouth entry(ServerPlayer player) {
        if (downward(player) && player.onGround()) {
            return Mouth.flat(player.serverLevel(), player.position(), player.getYRot());
        }
        return upright(player, 89.99F);
    }

    /**
     * A near mouth facing along the look, tilted to its pitch (at most {@code tilt}): on the ground two steps ahead, or
     * in the air. A frequency's mouth tilts at most {@link #FREQUENCY_TILT}, so it is always one you can walk into.
     */
    private static Mouth upright(ServerPlayer player, float tilt) {
        Vec3 normal = Vec3.directionFromRotation(Mth.clamp(player.getXRot(), -tilt, tilt), player.getYRot());
        if (player.onGround() && normal.y <= .15) {
            Vec3 foot = player.position().add(Vec3.directionFromRotation(0, player.getYRot()).scale(2));
            Mouth grounded = Mouth.grounded(player.serverLevel(), foot, normal, player.getYRot());
            if (safe(player, grounded, grounded.arrival(player, 1))) return grounded;
        }
        return Mouth.floating(player, player.getEyePosition().add(normal.scale(2)), normal);
    }

    private static boolean entryFits(ServerPlayer player, Mouth entry) {
        if (entry.flat()) return player.onGround() && safe(player, entry, entry.feet());
        return exitFits(player, entry) && clearLine(player, entry.center());
    }

    /**
     * Can the exit stay open: an upright mouth needs room and a footing on its far side, and a ceiling mouth needs
     * open air down to safe ground.
     */
    private static boolean exitFits(ServerPlayer player, Mouth mouth) {
        if (!mouth.flat()) return clearMouth(player, mouth) && safeArrival(player, mouth, mouth.arrival(player, 1));
        ServerLevel level = mouth.level();
        Vec3 ground = mouth.feet().subtract(0, DROP, 0);
        var column = new net.minecraft.world.phys.AABB(ground.x - .35, ground.y + .05, ground.z - .35,
                ground.x + .35, mouth.feet().y + .3, ground.z + .35);
        return clear(player, level, column) && safe(player, mouth, ground);
    }

    private static boolean openTo(ServerPlayer player, int slot, Kind kind) {
        if (!canOpen(player) || GATES.containsKey(player.getUUID())) return false;
        if (!travelWorld(player.serverLevel())) return fail(player, "restricted");
        var frequency = VibeFrequencies.get(player, slot);
        String refused = VibeFrequencies.refusal(player, frequency);
        if (refused != null) return fail(player, refused);
        Mouth entry = upright(player, FREQUENCY_TILT);
        if (!entryFits(player, entry)) return fail(player, "blocked");
        ServerLevel target = VibeFrequencies.world(player, frequency);
        if (target == null) return fail(player, "restricted");
        if (!VibeFrequencies.loaded(target, frequency)) {
            // The normal ability path has checked cooldown and stamina. Spend them when a clear exit is ready.
            VibeFrequencies.prepare(player, target, frequency, slot, kind == Kind.GLIMPSE);
            return false;
        }
        Mouth exit = frequencyExit(player, target, frequency);
        if (exit == null) return fail(player, "no_exit");
        return begin(player, new Gate(player, entry, exit, kind, false));
    }

    /** A breach to the place a vision just showed ({@link VibeVisions#spot}): beside the ally, or into the village. */
    private static boolean openToVision(ServerPlayer player) {
        if (!canOpen(player) || GATES.containsKey(player.getUUID())) return false;
        var spot = VibeVisions.spot(player);
        if (spot == null) return fail(player, "no_frequency");
        ServerLevel target = VibeFrequencies.world(player, spot);
        if (target == null || target != player.serverLevel()) return fail(player, "restricted");
        if (spot.feet().distanceToSqr(player.position()) < 25) return fail(player, "at_frequency");
        if (!VibeFrequencies.loaded(target, spot)) return fail(player, "no_exit");
        Mouth entry = upright(player, FREQUENCY_TILT);
        if (!entryFits(player, entry)) return fail(player, "blocked");
        Mouth exit = frequencyExit(player, target, spot);
        if (exit == null) return fail(player, "no_exit");
        return begin(player, new Gate(player, entry, exit, Kind.FREQUENCY, false));
    }

    private static Mouth frequencyExit(ServerPlayer player, ServerLevel level, VibeFrequencies.Frequency frequency) {
        Vec3 saved = frequency.feet();
        BlockPos base = BlockPos.containing(saved);
        for (BlockPos cell : BlockPos.spiralAround(base, 3, Direction.EAST, Direction.SOUTH)) {
            Vec3 column = saved.add(cell.getX() - base.getX(), 0, cell.getZ() - base.getZ());
            Vec3 ground = StyleOps.ground(level, column.add(0, 1.5, 0));
            if (ground.equals(column.add(0, 1.5, 0)) || Math.abs(ground.y - saved.y) > 2) continue;
            for (int turn : new int[]{0, 90, -90, 180}) {
                Vec3 direction = Vec3.directionFromRotation(0, frequency.yaw() + turn);
                Mouth mouth = Mouth.upright(level, ground, direction);
                if (level == player.serverLevel() && mouth.feet().distanceToSqr(player.position()) < 16) continue;
                if (exitFits(player, mouth)) return mouth;
            }
        }
        return null;
    }

    /** Early aimed breaches reach 30 blocks. Higher tiers use the corresponding configured Fold Space range. */
    static int aimRange(ServerPlayer player) {
        int tier = PlayerProgressionService.getLevel(player);
        return tier >= 4 ? SpaceTimeService.blinkRange(tier) : 30;
    }

    static boolean canOpen(ServerPlayer player) {
        return VibeService.active(player) && !player.isPassenger()
                && (!player.isVehicle() || EntityCarryService.isCarrying(player));
    }

    /** A breach to where the viber looks. Sanctuary Breach falls back to five blocks ahead. */
    public static boolean open(ServerPlayer player, boolean sanctuary) {
        if (!canOpen(player) || GATES.containsKey(player.getUUID())) return false;
        if (!travelWorld(player.serverLevel())) return fail(player, "restricted");
        Mouth entry = entry(player);
        if (!entryFits(player, entry)) return fail(player, "blocked");
        if (entry.flat()) return dropAhead(player, entry, sanctuary);
        Mouth exit = aimedExit(player, entry, sanctuary);
        if (exit == null) return fail(player, "no_exit");
        return begin(player, new Gate(player, entry, exit, Kind.AIMED, sanctuary));
    }

    private static Mouth aimedExit(ServerPlayer player, Mouth entry, boolean sanctuary) {
        Vec3 normal = entry.direction(), eye = player.getEyePosition();
        BlockHitResult hit = aimHit(player, normal, aimRange(player));
        Vec3 target = hit.getLocation();
        boolean surface = hit.getType() == HitResult.Type.BLOCK;
        boolean groundHit = surface && hit.getDirection() == Direction.UP;
        if (groundHit || Math.abs(normal.y) < .08 || sanctuary) {
            Vec3 landing = ground(player, surface ? target.subtract(normal.scale(.65)) : target);
            Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
            if (groundHit && !entry.airborne() && landing != null && landing.distanceToSqr(entry.feet()) < 9) {
                // The ray meets the floor too close to fit two mouths. Keep the tilt and open farther along the floor.
                landing = ground(player, player.position().add(forward.scale(7)).add(0, .75, 0));
            }
            if (landing == null && sanctuary) landing = ground(player, player.position().add(forward.scale(5)));
            if (landing != null) {
                for (int offset = 0; offset < 4; offset++) {
                    Vec3 floor = offset == 0 ? landing : ground(player, landing.subtract(forward.scale(offset)).add(0, .7, 0));
                    if (floor == null) continue;
                    Mouth candidate = Mouth.grounded(player.serverLevel(), floor, normal, player.getYRot());
                    if (candidate.center().distanceToSqr(entry.center()) >= 9 && clearLine(player, candidate.center())
                            && exitFits(player, candidate)) return candidate;
                }
            }
        }
        // Leave space past an exit on the ray for the traveler's whole body, even at ceilings and steep slopes.
        double reach = eye.distanceTo(target);
        for (double d = reach - (surface ? 1.5 : 0); d >= 4.5 && d >= reach - 4; d -= .5) {
            Mouth candidate = Mouth.floating(player, eye.add(normal.scale(d)), normal);
            if (candidate.center().distanceToSqr(entry.center()) >= 9 && exitFits(player, candidate)) return candidate;
        }
        return null;
    }

    /** A floor breach where you look down: you drop out of the air about twelve blocks ahead (nearer if that's blocked). */
    private static boolean dropAhead(ServerPlayer player, Mouth entry, boolean sanctuary) {
        Vec3 target = aimedGround(player, aimRange(player));
        if (target != null && target.distanceToSqr(entry.feet()) >= 25) {
            Mouth exit = Mouth.flat(player.serverLevel(), target.add(0, DROP, 0), player.getYRot());
            if (exitFits(player, exit)) return begin(player, new Gate(player, entry, exit, Kind.AIMED, sanctuary));
        }
        Vec3 ahead = Vec3.directionFromRotation(0, player.getYRot());
        for (double reach = 12; reach >= 5; reach -= 1.5) {
            Vec3 ground = ground(player, player.position().add(ahead.scale(reach)).add(0, 1.2, 0));
            if (ground == null || Math.abs(ground.y - player.getY()) > 6) continue;
            Mouth exit = Mouth.flat(player.serverLevel(), ground.add(0, DROP, 0), player.getYRot());
            if (exitFits(player, exit)) return begin(player, new Gate(player, entry, exit, Kind.AIMED, sanctuary));
        }
        return fail(player, "no_exit");
    }

    private static boolean begin(ServerPlayer player, Gate gate) {
        VibeFrequencies.cancelPending(player);
        GATES.put(player.getUUID(), gate);
        gate.ticket(true);
        if (keyed) {
            // Cisco's way: a vibe blast leaves the palm and tears the breach open slowly where he looks.
            gate.keyed = true;
            gate.held = holding(player);
            gate.look = player.getLookAngle();
            gate.channel = HURRIED.contains(player.getUUID()) ? VibeGeometry.BREACH_OPEN_TICKS
                    : VibeGeometry.channelTicks(PlayerProgressionService.getLevel(player));
            ArmPosePayload.show(player, ArmPose.VIBE_CHANNEL, 6);
            channel(gate, gate.started);
            StyleOps.sound(gate.entry.level(), gate.entry.center(), ModSounds.VIBE_BREACH.get(), .85F,
                    gate.kind == Kind.GLIMPSE ? 1.3F : 1.02F);
            StyleOps.sound(gate.entry.level(), gate.entry.center(), ModSounds.VIBE_PULSE.get(), .7F, .62F);
            return true;
        }
        ArmPosePayload.show(player, ArmPose.VIBE_OPEN, 18);
        SetPiecePayload.send(gate.entry.level(), Piece.VIBE_BREACH_SEED, gate.entry.center().subtract(gate.entry.direction()),
                gate.entry.center(), 1, player);
        StyleOps.sound(gate.entry.level(), gate.entry.center(), ModSounds.VIBE_BREACH.get(), .85F,
                gate.kind == Kind.GLIMPSE ? 1.7F : 1.42F);
        return true;
    }

    /**
     * One beat of a channelled opening: the blast from the palm and the tear growing at the end of it. Re-sent every two
     * ticks as a short piece bound to the viber (so it leaves the hand they see); the newest copy takes over from the
     * last. Its scale is 1 plus how far the tear has opened, and its seed the whole channel's length, so the client
     * grows it smoothly between copies.
     */
    private static void channel(Gate gate, long now) {
        float progress = Mth.clamp((now - gate.started) / (float) gate.channel, 0, 1);
        Mouth mouth = gate.entry;
        var payload = new SetPiecePayload(Piece.VIBE_BREACH_CHANNEL.ordinal(), mouth.center(),
                mouth.center().add(mouth.direction()), 1 + progress, gate.owner.getId(), gate.channel, 0);
        for (ServerPlayer viewer : mouth.level().players())
            if (viewer.position().distanceToSqr(mouth.center()) <= 128 * 128
                    && !com.jayptucker.heroesevolved.time.TimeVeil.masks(viewer, mouth.center()))
                PacketDistributor.sendToPlayer(viewer, payload);
        if (gate.owner.level() == mouth.level() && !mouth.level().players().contains(gate.owner))
            PacketDistributor.sendToPlayer(gate.owner, payload);
    }

    /** While a keyed breach opens, its near mouth turns with the viber's look (a floor breach stays in the floor). */
    private static void follow(Gate gate) {
        if (gate.flat() || gate.owner.level() != gate.entry.level()) return;
        Mouth next = upright(gate.owner, gate.kind == Kind.AIMED ? 89.99F : FREQUENCY_TILT);
        if (next.center().distanceToSqr(gate.entry.center()) < 1e-6 && next.direction().distanceToSqr(gate.entry.direction()) < 1e-6) return;
        if (entryFits(gate.owner, next)) {
            gate.entry = next;
            gate.hold(next);
        }
    }

    /** Where a frequency can be tuned: on the ground where the viber stands, with room for a mouth. Null (and told) if not. */
    static Mouth markable(ServerPlayer player) {
        if (!travelWorld(player.serverLevel())) {
            fail(player, "restricted");
            return null;
        }
        Mouth mouth = Mouth.upright(player.serverLevel(), player.position(), StyleOps.facing(player));
        if (!player.onGround() || !exitFits(player, mouth)) {
            fail(player, "no_mark");
            return null;
        }
        return mouth;
    }

    static boolean travelWorld(ServerLevel level) {
        // The mod's story/rewind worlds own their own entrances and exits.
        return !level.dimension().location().getNamespace().equals(HeroesEvolved.MOD_ID);
    }

    static Vec3 aimedGround(ServerPlayer player, double range) {
        Vec3 aim = player.getLookAngle();
        BlockHitResult hit = aimHit(player, aim, range);
        return ground(player, hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().subtract(aim.scale(.65)) : hit.getLocation());
    }

    private static Vec3 aim(ServerPlayer player) {
        return Vec3.directionFromRotation(Mth.clamp(player.getXRot(), -89.99F, 89.99F), player.getYRot());
    }

    private static BlockHitResult aimHit(ServerPlayer player, Vec3 aim, double range) {
        Vec3 eye = player.getEyePosition();
        ServerLevel level = player.serverLevel();
        if (aim.y > 0) range = Math.min(range, Math.max(0, (level.getMaxBuildHeight() - 2.5 - eye.y) / aim.y));
        else if (aim.y < 0) range = Math.min(range, Math.max(0, (level.getMinBuildHeight() + .1 - eye.y) / aim.y));
        // March only through chunks already on the server, before asking for block collision.
        for (int d = 1; d <= Math.ceil(range); d++) {
            if (!level.hasChunkAt(BlockPos.containing(eye.add(aim.scale(Math.min(d, range)))))) {
                range = d - 1;
                break;
            }
        }
        return level.clip(new ClipContext(eye, eye.add(aim.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
    }

    private static Vec3 ground(ServerPlayer player, Vec3 point) {
        if (!player.serverLevel().hasChunkAt(BlockPos.containing(point))) return null;
        Vec3 floor = StyleOps.ground(player.serverLevel(), point);
        if (floor.equals(point) || !SpaceTimeService.safe(player, floor)) return null;
        return floor;
    }

    public static boolean flash(ServerPlayer player) {
        if (!VibeService.ready(player) || !travelWorld(player.serverLevel())) return false;
        Vec3 point = aimedGround(player, 11);
        if (point == null || point.distanceToSqr(player.position()) < 4) return fail(player, "no_exit");
        Vec3 facing = StyleOps.facing(player);
        Vec3 origin = player.position().add(0, VibeGeometry.PORTAL_HEIGHT, 0);
        ArmPosePayload.show(player, ArmPose.VIBE_STEP, 13);
        SetPiecePayload.send(player.serverLevel(), Piece.VIBE_BREACH_ARRIVE, origin, origin.add(facing), 1, null);
        if (!move(player, player.serverLevel(), point)) return false;
        arrival(player, facing);
        return true;
    }

    static void rescue(ServerPlayer owner, net.minecraft.world.entity.LivingEntity ward, List<net.minecraft.world.entity.LivingEntity> threats) {
        Vec3 escape = threats.isEmpty() ? StyleOps.facing(owner)
                : ward.position().subtract(threats.getFirst().position()).multiply(1, 0, 1).normalize();
        if (escape.lengthSqr() < .1) escape = StyleOps.facing(owner);
        if (ward.isPassenger() || ward.isVehicle()) return;
        // swept returns the furthest safe position, not a displacement.
        Vec3 move = com.jayptucker.heroesevolved.speed.SpeedService.swept(ward, escape.scale(7)).subtract(ward.position());
        if (move.lengthSqr() < 9) return;
        Vec3 to = StyleOps.ground(owner.serverLevel(), ward.position().add(move).add(0, .35, 0));
        if (!SpaceTimeService.safe(ward, to) || Math.abs(to.y - ward.getY()) > 1.1) return;
        Vec3 from = ward.position().add(0, VibeGeometry.PORTAL_HEIGHT, 0);
        SetPiecePayload.send(owner.serverLevel(), Piece.VIBE_BREACH_ARRIVE, from, from.add(escape), 1, null);
        if (ward instanceof ServerPlayer player) {
            if (move(player, owner.serverLevel(), to)) arrival(player, escape);
        } else {
            ward.teleportTo(to.x, to.y, to.z);
            ward.resetFallDistance();
            SetPiecePayload.send(owner.serverLevel(), Piece.VIBE_BREACH_ARRIVE, to.add(0, VibeGeometry.PORTAL_HEIGHT, 0),
                    to.add(escape).add(0, VibeGeometry.PORTAL_HEIGHT, 0), 1, null);
        }
    }

    public static boolean close(ServerPlayer player) {
        Gate gate = GATES.remove(player.getUUID());
        if (gate == null) return VibeFrequencies.cancelPending(player);
        if (GATES.isEmpty()) BreachVoiceRoutes.clear();
        gate.ticket(false);
        for (View view : gate.views)
            for (ServerPlayer viewer : view.sent.values()) PacketDistributor.sendToPlayer(viewer, BreachViewPayload.ended(view.id));
        if (gate.opened >= 0) {
            for (Mouth mouth : List.of(gate.entry, gate.exit)) {
                show(mouth, Piece.VIBE_BREACH_CLOSE);
                StyleOps.sound(mouth.level(), mouth.center(), ModSounds.VIBE_SHUT.get(), .65F, 1.55F);
            }
        } else {
            SetPiecePayload.send(gate.entry.level(), Piece.VIBE_BREACH_CLOSE, gate.entry.center(),
                    gate.entry.center().add(gate.entry.direction()), 1, null);
        }
        return true;
    }

    static void tick(MinecraftServer server) {
        VibeFrequencies.tick(server);
        for (var entry : List.copyOf(PRESSES.entrySet())) {
            Press press = entry.getValue();
            if (press.handled || press.fired) continue;
            ServerPlayer player = press.player;
            if (player.server != server) continue;
            if (player.isRemoved() || !canOpen(player)) {
                PRESSES.remove(entry.getKey());
                continue;
            }
            if (player.serverLevel().getGameTime() - press.at >= HOLD_TICKS) fire(player, press);
        }
        for (Gate gate : List.copyOf(GATES.values())) {
            if (gate.owner.server != server) continue;
            long now = gate.entry.level().getGameTime();
            if (!VibeService.active(gate.owner) || gate.owner.isPassenger()
                    || gate.owner.level() != gate.entry.level() && gate.owner.level() != gate.exit.level()
                    || gate.opened >= 0 && now - gate.opened >= gate.lifetime) {
                close(gate.owner);
                continue;
            }
            if (gate.opened < 0) {
                if (gate.keyed && now - gate.started <= gate.channel) {
                    // Channelling: the viber has to keep at it, and the tear turns with their look.
                    if (!canOpen(gate.owner) || gate.owner.level() != gate.entry.level()) {
                        close(gate.owner);
                        continue;
                    }
                    follow(gate);
                    if ((now - gate.started) % 2 == 0) channel(gate, now);
                    if ((now - gate.started) % 4 == 0) ArmPosePayload.show(gate.owner, ArmPose.VIBE_CHANNEL, 6);
                    if ((now - gate.started) % 6 == 3)
                        StyleOps.sound(gate.entry.level(), gate.entry.center(), SoundEvents.AMETHYST_BLOCK_RESONATE, .5F,
                                .7F + .9F * (now - gate.started) / gate.channel);
                    if (now - gate.started < gate.channel) continue;
                }
                if (now - gate.started < gate.channel) continue;
                if (!gate.entry.level().isPositionEntityTicking(BlockPos.containing(gate.entry.feet()))
                        || !gate.exit.level().isPositionEntityTicking(BlockPos.containing(gate.exit.feet()))) {
                    if (now - gate.started < LOAD_TIMEOUT) continue;
                    fail(gate.owner, "no_exit");
                    close(gate.owner);
                    continue;
                }
                if (gate.keyed && gate.kind == Kind.AIMED && !gate.flat() && gate.owner.getLookAngle().dot(gate.look) < .999) {
                    // The tear turned as it opened: the far mouth goes where the viber looks now.
                    Mouth exit = aimedExit(gate.owner, gate.entry, gate.sanctuary);
                    if (exit != null) {
                        gate.exit = exit;
                        gate.hold(exit);
                    }
                }
                gate.views = gate.views();
                boolean entryClear = gate.flat() ? safe(gate.owner, gate.entry, gate.entry.feet()) : exitFits(gate.owner, gate.entry);
                if (!entryClear || !exitFits(gate.owner, gate.exit)) {
                    fail(gate.owner, "no_exit");
                    close(gate.owner);
                    continue;
                }
                gate.opened = now;
                gate.entry.level().resetEmptyTime();
                gate.exit.level().resetEmptyTime();
                // Each mouth shows the terrain round the other one. A channelled mouth is already open: it only flares.
                for (Mouth mouth : List.of(gate.entry, gate.exit)) {
                    SetPiecePayload.send(mouth.level(), Piece.VIBE_BREACH_ARRIVE, mouth.center(), mouth.center().add(mouth.direction()),
                            gate.keyed && mouth == gate.entry ? 2 : 1, null);
                    StyleOps.sound(mouth.level(), mouth.center(), SoundEvents.AMETHYST_BLOCK_CHIME, .44F, 1.65F);
                }
                if (gate.keyed) ArmPosePayload.show(gate.owner, ArmPose.VIBE_OPEN, 12);
            }
            if ((now - gate.opened) % 10 == 0) {
                boolean obstructed = gate.flat()
                        ? !safe(gate.owner, gate.entry, gate.entry.feet()) || !exitFits(gate.owner, gate.exit)
                        : !exitFits(gate.owner, gate.entry) || !exitFits(gate.owner, gate.exit);
                if (obstructed) {
                    close(gate.owner);
                    continue;
                }
                show(gate.entry, Piece.VIBE_BREACH_OPEN);
                show(gate.exit, Piece.VIBE_BREACH_OPEN);
                share(gate, now);
            }
            if ((now - gate.opened) % BreachActorsPayload.UPDATE_TICKS == 0) {
                for (View view : gate.views) shareActors(view);
            }
            if (gate.kind != Kind.GLIMPSE) {
                if (gate.flat()) drops(gate, now);
                else transit(gate);
            }
        }
        BreachVoiceRoutes.publish(GATES.values().stream()
                .filter(gate -> gate.owner.server == server && gate.kind != Kind.GLIMPSE && gate.opened >= 0)
                .map(gate -> new BreachVoiceRoutes.Link(gate.entry, gate.exit)).toList(), MindVoiceRoutes.voiceDistance());
    }

    /** Sends a bounded, live view of entities even when the viewer isn't tracking the destination's chunks. */
    private static void shareActors(View view) {
        if (view.sent.isEmpty()) return;
        ServerLevel far = view.there.level();
        Vec3 center = view.there.center();
        ChunkPos chunk = view.there.chunk();
        if (far.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) return;
        var volume = new AABB(center, center).inflate(BreachSnapshot.REACH);
        var actors = far.getEntities((Entity) null, volume, entity -> entity.isAlive() && !entity.isSpectator() && !entity.isInvisible()
                && entity.distanceToSqr(center) <= BreachSnapshot.REACH * BreachSnapshot.REACH);
        actors.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(center)));
        // Reserve room for both sides of the mouth, even when a crowd stands on one side.
        int front = 0, back = 0;
        var visible = new java.util.ArrayList<Entity>(BreachActorsPayload.LIMIT);
        for (Entity actor : actors) {
            boolean ahead = actor.getBoundingBox().getCenter().subtract(center).dot(view.there.direction()) >= 0;
            if (ahead ? front >= BreachActorsPayload.LIMIT / 2 : back >= BreachActorsPayload.LIMIT / 2) continue;
            visible.add(actor);
            if (ahead) front++;
            else back++;
        }
        var payload = new BreachActorsPayload(view.id, visible.stream()
                .map(entity -> BreachActorsPayload.Actor.capture(far, entity)).toList());
        for (ServerPlayer viewer : view.sent.values()) {
            if (viewer.level() == view.here.level() && viewer.distanceToSqr(view.here.center()) <= VIEW_RANGE * VIEW_RANGE) {
                PacketDistributor.sendToPlayer(viewer, payload);
            }
        }
    }

    /** Sends each mouth's view to players near it who don't have it yet, and forgets those who left its world. */
    private static void share(Gate gate, long now) {
        int left = (int) Math.max(1, gate.lifetime - (now - gate.opened)) + 10;
        for (View view : gate.views) {
            view.sent.values().removeIf(viewer -> viewer.isRemoved() || viewer.level() != view.here.level());
            Vec3 center = view.here.center();
            List<ServerPlayer> near = new java.util.ArrayList<>(view.here.level().players());
            // The owner always sees through their own breach, even when the level doesn't list them (test players).
            if (gate.owner.level() == view.here.level() && !near.contains(gate.owner)) near.add(gate.owner);
            for (ServerPlayer viewer : near) {
                if (view.sent.containsKey(viewer.getUUID()) || viewer.position().distanceToSqr(center) > VIEW_RANGE * VIEW_RANGE) continue;
                if (view.snapshot == null) {
                    if (!BreachSnapshot.ready(view.there.level(), view.there.center())) break;
                    view.snapshot = BreachSnapshot.capture(view.there.level(), view.there.center());
                }
                view.sent.put(viewer.getUUID(), viewer);
                PacketDistributor.sendToPlayer(viewer, new BreachViewPayload(view.id, center, view.here.yaw(), view.here.direction(),
                        view.there.center(), view.there.yaw(), view.there.direction(), left, view.snapshot));
            }
        }
    }

    /** Package-visible for tests: what one of a viber's open mouths shows (0 the near mouth, 1 the far one). */
    static BreachSnapshot view(ServerPlayer owner, int mouth) {
        Gate gate = GATES.get(owner.getUUID());
        return gate == null ? null : gate.views[mouth].snapshot;
    }

    /** Package-visible for tests: where one of a viber's open mouths stands (0 near, 1 far), and whether it lies flat. */
    static Vec3 mouthAt(ServerPlayer owner, int mouth) {
        Gate gate = GATES.get(owner.getUUID());
        return gate == null ? null : (mouth == 0 ? gate.entry : gate.exit).feet();
    }

    static boolean flat(ServerPlayer owner) {
        Gate gate = GATES.get(owner.getUUID());
        return gate != null && gate.flat();
    }

    static boolean shown(ServerPlayer owner, int mouth, ServerPlayer viewer) {
        Gate gate = GATES.get(owner.getUUID());
        return gate != null && gate.views[mouth].sent.containsKey(viewer.getUUID());
    }

    static Kind kind(ServerPlayer owner) {
        Gate gate = GATES.get(owner.getUUID());
        return gate == null ? null : gate.kind;
    }

    /**
     * A floor breach drops travelers through the hole and out of the ceiling mouth above the destination. Jumping
     * into the ceiling mouth sends them back out beside the hole. A traveler isn't taken again for
     * {@link #DROP_GAP} ticks, so landing near a mouth never bounces them straight back.
     */
    private static void drops(Gate gate, long now) {
        gate.dropped.values().removeIf(at -> now - at > DROP_GAP);
        for (Mouth from : List.of(gate.entry, gate.exit)) {
            Mouth to = from == gate.entry ? gate.exit : gate.entry;
            boolean down = from == gate.entry;
            for (LivingEntity traveler : travelers(gate, from, 2)) {
                if (gate.dropped.containsKey(traveler.getUUID())) continue;
                Vec3 offset = traveler.position().subtract(from.feet());
                if (!VibeGeometry.inside(offset, from.direction(), VibeGeometry.PORTAL_WIDTH, VibeGeometry.PORTAL_HEIGHT)) continue;
                // Down through a hole: feet on (or just above) the floor it is cut in. Up into a ceiling: head through it.
                boolean through = down ? offset.y > -.3 && offset.y < .8
                        : traveler.getEyeY() >= from.feet().y - .15 && traveler.getEyeY() < from.feet().y + 1.2;
                if (!through) continue;
                Vec3 arrival = down ? to.feet().subtract(0, Math.max(2.05, dimensions(traveler).height() + .15), 0)
                        : beside(traveler, to);
                if (down && !exitFits(gate.owner, to)) {
                    if (traveler instanceof ServerPlayer player) fail(player, "no_exit");
                    close(gate.owner);
                    return;
                }
                if (arrival == null || !clear(traveler, to.level(), dimensions(traveler).makeBoundingBox(arrival))) continue;
                float yaw = traveler.getYRot() + to.yaw() - from.yaw();
                Vec3 velocity = down ? new Vec3(0, -.35, 0) : Vec3.ZERO;
                LivingEntity crossed = moveTraveler(traveler, to, arrival, yaw, traveler.getXRot(), velocity);
                if (crossed == null) continue;
                gate.dropped.put(crossed.getUUID(), now);
                arrived(gate, crossed, down ? DOWN : Vec3.directionFromRotation(0, to.yaw()));
            }
        }
    }

    /** Where someone coming back up through a ceiling lands: a step beside the hole they fell through, or on it. */
    private static Vec3 beside(LivingEntity traveler, Mouth hole) {
        Vec3 step = hole.feet().add(Vec3.directionFromRotation(0, hole.yaw()).scale(1.5));
        if (safe(traveler, hole, step)) return step;
        return safe(traveler, hole, hole.feet()) ? hole.feet() : null;
    }

    private static void arrived(Gate gate, LivingEntity traveler, Vec3 direction) {
        arrival(traveler, direction);
        if (traveler == gate.owner) VibeService.prime(gate.owner);
        if (SkillTreeService.has(gate.owner, "vibe_wayfarer"))
            StyleOps.effect(traveler, net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 100, 0);
        if (gate.sanctuary || SkillTreeService.has(gate.owner, "vibe_horizon")) {
            traveler.clearFire();
            StyleOps.effect(traveler, net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 80, 0);
        }
    }

    private static void transit(Gate gate) {
        Map<UUID, Approach> next = new HashMap<>();
        Set<UUID> moved = new HashSet<>();
        for (Mouth from : List.of(gate.entry, gate.exit)) {
            Mouth to = from == gate.entry ? gate.exit : gate.entry;
            for (LivingEntity traveler : travelers(gate, from, 14)) {
                if (moved.contains(traveler.getUUID())) continue;
                Vec3 center = traveler.getBoundingBox().getCenter();
                if (center.distanceToSqr(from.center()) > 196) continue;
                next.put(traveler.getUUID(), new Approach(from.level(), center));
                Approach previous = gate.previous.get(traveler.getUUID());
                if (previous == null || previous.world() != from.level() || previous.center().distanceToSqr(center) > 196) continue;
                if (VibeGeometry.crossing(previous.center(), center, from.center(), from.direction(), VibeGeometry.PORTAL_WIDTH,
                        VibeGeometry.PORTAL_HEIGHT) == null) continue;
                // Come out on the side you were heading for, turned by the angle between the mouths, so walking
                // back through the exit lands you behind the entry instead of in front of it (and bouncing back).
                double side = center.subtract(previous.center()).dot(from.direction()) < 0 ? -1 : 1;
                Vec3 arrival = to.arrival(traveler, side);
                if (!clearMouth(traveler, from) || !safeArrival(traveler, to, arrival) || !clearMouth(traveler, to)) {
                    if (traveler instanceof ServerPlayer player) fail(player, "no_exit");
                    continue;
                }
                Vec3 heading = from.rotate(traveler.getLookAngle(), to);
                float yaw = heading.horizontalDistanceSqr() < 1e-8 ? traveler.getYRot() + to.yaw() - from.yaw() : VibeGeometry.yaw(heading);
                LivingEntity crossed = moveTraveler(traveler, to, arrival, yaw, VibeGeometry.pitch(heading),
                        from.rotate(traveler.getDeltaMovement(), to));
                if (crossed == null) continue;
                moved.add(crossed.getUUID());
                next.put(crossed.getUUID(), new Approach(to.level(), crossed.getBoundingBox().getCenter()));
                arrived(gate, crossed, to.direction().scale(side));
            }
        }
        gate.previous.clear();
        gate.previous.putAll(next);
    }

    private static List<LivingEntity> travelers(Gate gate, Mouth mouth, double range) {
        Vec3 center = mouth.center();
        List<LivingEntity> nearby = new ArrayList<>(mouth.level().getEntitiesOfClass(Mob.class,
                new AABB(center, center).inflate(range), Mob::isAlive));
        nearby.addAll(mouth.level().players());
        if (gate.owner.level() == mouth.level() && !nearby.contains(gate.owner)) nearby.add(gate.owner);
        nearby.removeIf(entity -> !canCross(gate, entity));
        return nearby;
    }

    private static boolean canCross(Gate gate, LivingEntity entity) {
        if (!entity.isAlive() || entity.isSpectator() || entity.isPassenger() || entity.isSleeping()) return false;
        if (entity instanceof ServerPlayer player) {
            return PowerRules.canAct(player) && (!player.isVehicle() || EntityCarryService.isCarrying(player))
                    && StyleOps.isFriend(gate.owner, player);
        }
        var size = dimensions(entity);
        return !entity.isVehicle() && size.width() <= VibeGeometry.PORTAL_WIDTH * 2 && size.height() <= VibeGeometry.PORTAL_HEIGHT * 2
                && !com.jayptucker.heroesevolved.cryo.CryoService.isFrozen(entity)
                && !com.jayptucker.heroesevolved.time.TimeStopService.isStopped(entity);
    }

    private static LivingEntity moveTraveler(LivingEntity entity, Mouth to, Vec3 at, float yaw, float pitch, Vec3 velocity) {
        if (entity instanceof ServerPlayer player) {
            if (!EntityCarryService.travelWithCargo(player, to.level(), at, yaw, () -> move(player, to.level(), at, yaw, pitch))) {
                fail(player, "no_exit");
                return null;
            }
            if (to.flat() && velocity.lengthSqr() > 0) StyleOps.push(player, velocity);
            return player;
        }
        LivingEntity arrived = entity;
        if (entity.level() == to.level()) {
            entity.moveTo(at.x, at.y, at.z, yaw, pitch);
        } else {
            if (!entity.canChangeDimensions((ServerLevel) entity.level(), to.level()) || VibeBeast.isBeast(entity)
                    || !(entity.changeDimension(new DimensionTransition(to.level(), at, velocity, yaw, pitch,
                    DimensionTransition.DO_NOTHING)) instanceof LivingEntity moved)) return null;
            arrived = moved;
            arrived.setXRot(pitch);
        }
        arrived.setYHeadRot(yaw);
        arrived.setYBodyRot(yaw);
        arrived.resetFallDistance();
        if (arrived instanceof Mob mob) mob.getNavigation().stop();
        StyleOps.push(arrived, velocity);
        return arrived;
    }

    private static boolean move(ServerPlayer player, ServerLevel level, Vec3 to) {
        return move(player, level, to, player.getYRot());
    }

    private static boolean move(ServerPlayer player, ServerLevel level, Vec3 to, float yaw) {
        return move(player, level, to, yaw, player.getXRot());
    }

    private static boolean move(ServerPlayer player, ServerLevel level, Vec3 to, float yaw, float pitch) {
        CROSSING.add(player.getUUID());
        boolean moved;
        try {
            yaw = Mth.wrapDegrees(yaw);
            moved = player.serverLevel() == level
                    ? player.teleportTo(level, to.x, to.y, to.z, Set.of(), yaw, pitch)
                    : player.changeDimension(new DimensionTransition(level, to, Vec3.ZERO, yaw, pitch,
                            DimensionTransition.DO_NOTHING)) == player;
        } finally {
            CROSSING.remove(player.getUUID());
        }
        if (!moved) return false;
        player.resetFallDistance();
        StyleOps.push(player, Vec3.ZERO);
        return true;
    }

    static boolean crossing(ServerPlayer player) { return CROSSING.contains(player.getUUID()); }

    static ShotCrossing shotCrossing(Projectile shot, Vec3 from, Vec3 to) {
        ShotCrossing nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (Gate gate : GATES.values()) {
            if (gate.opened < 0 || gate.kind == Kind.GLIMPSE) continue;
            for (Mouth mouth : List.of(gate.entry, gate.exit)) {
                if (shot.level() != mouth.level()) continue;
                Vec3 hit = VibeGeometry.crossing(from, to, mouth.center(), mouth.direction(), VibeGeometry.PORTAL_WIDTH,
                        VibeGeometry.PORTAL_HEIGHT);
                if (hit == null) continue;
                double along = hit.distanceToSqr(from);
                if (along >= distance) continue;
                nearest = new ShotCrossing(mouth, mouth == gate.entry ? gate.exit : gate.entry, hit);
                distance = along;
            }
        }
        return nearest;
    }

    /**
     * Through the Seam: the first of the owner's open mouths the segment crosses, mapped onto the other mouth at the
     * same place across its face. Only links whose two mouths are in the owner's world carry a wave; a glimpse
     * carries nothing.
     */
    static Seam seam(ServerPlayer owner, Vec3 from, Vec3 to) {
        Gate gate = GATES.get(owner.getUUID());
        if (gate == null || gate.opened < 0 || gate.kind == Kind.GLIMPSE || gate.flat() || gate.entry.level() != owner.serverLevel()
                || gate.exit.level() != owner.serverLevel()) return null;
        Seam best = null;
        double nearest = Double.MAX_VALUE;
        for (Mouth mouth : List.of(gate.entry, gate.exit)) {
            Vec3 hit = VibeGeometry.crossing(from, to, mouth.center(), mouth.direction(), VibeGeometry.PORTAL_WIDTH,
                    VibeGeometry.PORTAL_HEIGHT);
            if (hit == null || hit.distanceToSqr(from) >= nearest) continue;
            Mouth other = mouth == gate.entry ? gate.exit : gate.entry;
            Vec3 out = other.center().add(mouth.rotate(hit.subtract(mouth.center()), other));
            best = new Seam(hit, out, mouth.rotate(to.subtract(from).normalize(), other));
            nearest = hit.distanceToSqr(from);
        }
        return best;
    }

    private static void arrival(LivingEntity traveler, Vec3 direction) {
        ServerLevel level = (ServerLevel) traveler.level();
        Vec3 center = traveler.position().add(0, VibeGeometry.PORTAL_HEIGHT, 0);
        SetPiecePayload.send(level, Piece.VIBE_TRANSIT, center, center.add(direction), 1, traveler);
        if (traveler instanceof ServerPlayer player) ScreenTintPayload.send(player, 0x4329B8FF, 12, ScreenTintPayload.Style.VIGNETTE);
        StyleOps.sound(level, center, ModSounds.VIBE_SHUT.get(), .65F, 1.55F);
    }

    private static boolean clearMouth(LivingEntity player, Mouth mouth) {
        Vec3 side = VibeGeometry.side(mouth.direction());
        Vec3 up = side.cross(mouth.direction()).normalize();
        Vec3 center = mouth.center();
        double floor = mouth.airborne() ? Double.NEGATIVE_INFINITY : mouth.feet().y + .01;
        double width = VibeGeometry.PORTAL_WIDTH, height = VibeGeometry.PORTAL_HEIGHT;
        double xRange = Math.hypot(side.x * width, up.x * height) + .13;
        double yRange = Math.hypot(side.y * width, up.y * height) + .13;
        double zRange = Math.hypot(side.z * width, up.z * height) + .13;
        AABB bounds = new AABB(center.x - xRange, Math.max(floor, center.y - yRange), center.z - zRange,
                center.x + xRange, center.y + yRange, center.z + zRange);
        if (clear(player, mouth.level(), bounds)) return true;
        for (int x = -4; x <= 4; x++) {
            for (int y = -5; y <= 5; y++) {
                double across = x / 4.0, vertical = y / 5.0;
                if (across * across + vertical * vertical > 1) continue;
                Vec3 point = center.add(side.scale(across * width)).add(up.scale(vertical * height));
                double bottom = Math.max(point.y - .13, floor);
                AABB box = new AABB(point.x - .13, bottom, point.z - .13, point.x + .13, point.y + .13, point.z + .13);
                if (!clear(player, mouth.level(), box)) return false;
            }
        }
        return true;
    }

    private static net.minecraft.world.entity.EntityDimensions dimensions(LivingEntity entity) {
        return entity.getDimensions(entity instanceof ServerPlayer ? Pose.STANDING : entity.getPose());
    }

    private static boolean safeArrival(LivingEntity player, Mouth mouth, Vec3 at) {
        return mouth.airborne() ? clear(player, mouth.level(), dimensions(player).makeBoundingBox(at))
                : safe(player, mouth, at);
    }

    private static boolean safe(LivingEntity player, Mouth mouth, Vec3 at) {
        var box = dimensions(player).makeBoundingBox(at);
        BlockPos floor = BlockPos.containing(at.subtract(0, .05, 0));
        var level = mouth.level();
        if (!clear(player, level, box) || !level.hasChunkAt(floor)) return false;
        var state = level.getBlockState(floor);
        return !state.getCollisionShape(level, floor).isEmpty() && !state.is(BlockTags.FIRE)
                && !state.is(Blocks.MAGMA_BLOCK) && !state.is(Blocks.CACTUS) && !state.is(Blocks.POWDER_SNOW);
    }

    private static boolean clear(LivingEntity player, ServerLevel level, AABB box) {
        if (box.minY < level.getMinBuildHeight() || box.maxY >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(box)) return false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (!level.hasChunkAt(pos) || !level.getFluidState(pos).isEmpty() || level.getBlockState(pos).is(BlockTags.FIRE)
                    || level.getBlockState(pos).is(Blocks.POWDER_SNOW)) return false;
        }
        return !level.getBlockCollisions(player, box).iterator().hasNext();
    }

    private static boolean clearLine(ServerPlayer player, Vec3 to) {
        return VibeGeometry.clip(player, player.getEyePosition(), to).distanceToSqr(to) < .0001;
    }

    private static void show(Mouth mouth, Piece piece) {
        SetPiecePayload.send(mouth.level(), piece, mouth.center(), mouth.center().add(mouth.direction()), 1, null);
    }

    static boolean fail(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("vibe.heroesevolved." + key), true);
        return false;
    }

    static void dropPress(ServerPlayer player) {
        PRESSES.remove(player.getUUID());
        HURRIED.remove(player.getUUID());
    }

    static void clear() {
        PRESSES.clear();
        HURRIED.clear();
        for (Gate gate : List.copyOf(GATES.values())) close(gate.owner);
        BreachVoiceRoutes.clear();
        CROSSING.clear();
        VibeFrequencies.clear();
    }
}
