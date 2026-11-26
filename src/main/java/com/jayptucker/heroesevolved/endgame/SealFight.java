package com.jayptucker.heroesevolved.endgame;

import com.jayptucker.heroesevolved.combat.BossLocks;
import com.jayptucker.heroesevolved.combat.BossScaling;
import com.jayptucker.heroesevolved.endgame.SealGeometry.Move;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.story.sylar.SylarVoice;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * The Living Seal's fight: the native Dragon's body, every move it makes in five beats, and the locks each phase is built
 * from (combat/BossLocks): the Seal and its knots, Starfall's shards, reach, the feeding knot, Riftbrand, The Unmaking
 * and the open star. EndBattle owns the encounter (stages, party, scenery, Sylar); this owns the Dragon stage.
 *
 * <p>Its body hunts on vanilla's steering (a lead point circling the party) and follows exact cubic {@link Leg}s for
 * everything committed, so the native neck and tail trail behind from its position history. Every mark is fixed when
 * its windup starts and fills exactly as its hit lands ({@link SealGeometry#travel}).
 */
public final class SealFight {
    /** A stretch of exact flight: a cubic curve the body follows tick by tick. */
    static final class Leg {
        final Vec3 a, b, c, d; final int len; final boolean committed, face, accelerate; int age;
        BiConsumer<Vec3, Vec3> step; Runnable end;
        Leg(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int len, boolean committed, boolean face, boolean accelerate) {
            this.a = a; this.b = b; this.c = c; this.d = d; this.len = Math.max(1, len); this.committed = committed; this.face = face; this.accelerate = accelerate;
        }
        Vec3 at(double t) { double u = 1 - t; return a.scale(u * u * u).add(b.scale(3 * u * u * t)).add(c.scale(3 * u * t * t)).add(d.scale(t * t * t)); }
    }
    /** A lingering cloud of breath on the ground. */
    static final class Pool { final Vec3 at; final int life; int age; Pool(Vec3 at, int life) { this.at = at; this.life = life; } }
    /** A low ring of pressure rolling out over the ground (Wing Buffet, Rift Leap's shock): a jump clears it. */
    static final class Ring {
        final Vec3 at; final float damage; final double reach; int age; final Set<UUID> hit = new HashSet<>();
        Ring(Vec3 at, float damage, double reach) { this.at = at; this.damage = damage; this.reach = reach; }
    }

    public static final ResourceKey<DamageType> FRACTURE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("heroesevolved", "seal_fracture"));

    final EndBattle.Encounter e;
    private final ArrayDeque<Leg> legs = new ArrayDeque<>();
    private final ArrayDeque<float[]> twists = new ArrayDeque<>();
    final List<Pool> pools = new ArrayList<>();
    final List<Ring> rings = new ArrayList<>();
    private final Set<UUID> struck = new HashSet<>();
    final Map<UUID, Float> strengths = new HashMap<>();
    private double orbit;

    // The move in play: what, its windup left and in all, when it began (for the poses), and whether it is a feint.
    public Move casting, action;
    public int windup, castTotal, actionStart = -1000;
    boolean feint;
    private final EnumMap<Move, Integer> casts = new EnumMap<>(Move.class);
    public final ArrayDeque<Move> chain = new ArrayDeque<>();
    public int turn, airChains, rest = 80, slump, crash, stagger, stance, quiet, lastStance = -1000, lastInterrupt = -1000, lastWardFlash = -1000;
    boolean falling, exhausted, chainOpen;
    /** The shape a move fixed at its windup's start. */
    public Vec3 dir = new Vec3(1, 0, 0), spot = Vec3.ZERO, runA = Vec3.ZERO, runB = Vec3.ZERO, laneEnd;
    private ServerPlayer harpooned;
    private int runPools;

    // The locks.
    public final SealKnot[] knots = new SealKnot[4];
    final int[] mend = new int[4];
    final EndCrystal[] feeders = new EndCrystal[4];
    public boolean shattered;
    public int feeding = -1, feedTicks;
    public float drag;
    final Map<UUID, Integer> unseen = new HashMap<>();
    private final ArrayDeque<Integer> starHits = new ArrayDeque<>();
    private final Set<String> told = new HashSet<>();
    private boolean scaled;

    SealFight(EndBattle.Encounter e) {
        this.e = e;
        orbit = e.dragon == null ? 0 : Math.atan2(e.dragon.getZ(), e.dragon.getX());
    }

    // =========================================================================== state

    EnderDragon dragon() { return e.dragon; }
    int phase() { return Math.clamp(e.phase, 1, 3); }
    boolean fighting() { return e.stage == EndBattle.DRAGON && dragon() != null && dragon().isAlive(); }
    public boolean airborne() { return e.grounded <= 0 && !falling; }
    public boolean busy() { return !legs.isEmpty(); }
    boolean owns(SealKnot knot) { return fighting() && knots[knot.index()] == knot; }
    public int warding() { int n = 0; for (SealKnot k : knots) if (k != null && !k.isRemoved() && k.state() == SealView.WARDING) n++; return n; }
    public boolean warded() { return phase() == 1 && !shattered && warding() > 0; }
    public int casts(Move move) { return casts.getOrDefault(move, 0); }
    private List<ServerPlayer> fighters() { return e.fighters(); }

    /** Where altar {@code index}'s knot floats: just over its focus. */
    Vec3 knotAt(int index) {
        List<Vec3> anchors = e.data.anchors;
        Vec3 focus = index < anchors.size() ? anchors.get(index) : EndGeometry.center(e.data.floor).add(EndGeometry.ALTAR_OFFSETS.get(index)).add(0, 3.5, 0);
        return focus.add(0, SealGeometry.KNOT_LIFT, 0);
    }

    // ---- who it fights ----
    void join(ServerPlayer p) {
        strengths.merge(p.getUUID(), BossScaling.strength(p, SealGeometry.BASELINE_LEVEL), Math::max);
        rescale();
    }
    public float partyWeight() { float sum = 0; for (float s : strengths.values()) sum += s; return Math.max(.5F, sum); }
    public float fightersAverage() {
        var people = fighters();
        if (people.isEmpty()) return 1;
        float sum = 0;
        for (ServerPlayer p : people) sum += BossScaling.strength(p, SealGeometry.BASELINE_LEVEL);
        return sum / people.size();
    }
    /** Health for the party: set by the first fighter, then only raised (more join, levels rise), keeping the share left. */
    void rescale() {
        if (dragon() == null) return;
        float wanted = SealGeometry.BASE_HEALTH * BossScaling.healthScale(partyWeight());
        if (scaled && wanted <= e.damage.effective() + .01F) return;
        e.damage.scaleTo(dragon(), wanted);
        scaled = true;
        for (SealKnot knot : knots) if (knot != null && !knot.isRemoved()) {
            float share = knot.getHealth() / knot.getMaxHealth(), health = knotHealth();
            knot.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(health);
            knot.setHealth(health * share);
        }
    }
    float knotHealth() { return SealGeometry.KNOT_HEALTH * BossScaling.lockScale(partyWeight()); }

    // =========================================================================== flight

    /** Where vanilla steering flies the body while it hunts; null while an exact path, the ground or a hold has it. */
    Vec3 flyTarget() {
        if (e.still || !legs.isEmpty() || !airborne() || casting != null && casting.air) return null;
        Vec3 center = EndGeometry.center(e.data.floor), focus = center;
        double top = e.data.floor;
        var people = fighters();
        if (!people.isEmpty()) {
            Vec3 sum = Vec3.ZERO;
            for (ServerPlayer p : people) { sum = sum.add(p.position()); top = Math.max(top, p.getY()); }
            focus = sum.scale(1D / people.size());
        }
        focus = island(focus);
        double height = Math.clamp(Math.max(e.data.floor + 20, top + SealGeometry.ORBIT_LIFT), e.data.floor + 20, e.data.floor + 70);
        double lead = orbit + .7;
        return new Vec3(focus.x + Math.cos(lead) * SealGeometry.ORBIT_RADIUS, height, focus.z + Math.sin(lead) * SealGeometry.ORBIT_RADIUS);
    }
    private void place(Vec3 to, boolean face) {
        EnderDragon d = dragon();
        Vec3 delta = to.subtract(d.position());
        d.setPos(to);
        d.setDeltaMovement(delta);
        if (face && delta.horizontalDistanceSqr() > .0025) turn(SealGeometry.yaw(delta), 14);
    }
    private void turn(float yaw, float most) {
        EnderDragon d = dragon();
        float now = d.getYRot(), step = Mth.clamp(Mth.wrapDegrees(yaw - now), -most, most);
        d.setYRot(now + step); d.setYHeadRot(now + step); d.yBodyRot = now + step;
    }
    /** A path from where the body is, leaving along its heading, arriving travelling along {@code arrive}. */
    private Leg toward(Vec3 target, Vec3 arrive, int len, boolean committed) {
        EnderDragon d = dragon();
        Vec3 from = d.position(), v = d.getDeltaMovement();
        double reach = Math.min(18, from.distanceTo(target) / 3);
        Vec3 out = v.lengthSqr() < .01 ? SealGeometry.forward(d.getYRot()) : v.normalize();
        return new Leg(from, from.add(out.scale(reach)), target.subtract(arrive.scale(reach)), target, len, committed, true, false);
    }
    private void hold(Vec3 at, int len) { legs.add(new Leg(at, at, at, at, len, false, false, false)); }
    private void twist(float yaw, int ticks) { twists.clear(); twists.add(new float[]{yaw, Math.max(1, ticks)}); }
    /** Clear queued flight a cancelled move no longer needs; committed paths (dives, drops, falls, climbs) always finish. */
    void cancelLegs() {
        Leg leg = legs.peekFirst();
        if (leg != null && leg.committed) return;
        legs.clear(); twists.clear();
    }
    public void cancelAll() { legs.clear(); twists.clear(); falling = false; }

    // ---- the ground ----
    /** The standing height of the ground at x, z (the island's floor where it isn't loaded). */
    public double ground(double x, double z) { return EndGeometry.waveHeight(e.level, x, z, e.data.floor + .1) - .1; }
    Vec3 groundAt(Vec3 at) { return new Vec3(at.x, ground(at.x, at.z), at.z); }
    private Vec3 island(Vec3 at) {
        Vec3 center = EndGeometry.center(e.data.floor), flat = new Vec3(at.x - center.x, 0, at.z - center.z);
        return flat.length() <= SealGeometry.ISLAND_RADIUS ? at : new Vec3(center.x, at.y, center.z).add(flat.normalize().scale(SealGeometry.ISLAND_RADIUS));
    }
    /** The spot a move marks under a fighter: the ground, or the fighter where they fly well above it. */
    private Vec3 under(ServerPlayer p) { double g = ground(p.getX(), p.getZ()); return p.getY() > g + 5 ? p.position() : new Vec3(p.getX(), g, p.getZ()); }
    private Vec3 approach(Vec3 at) {
        Vec3 d = at.subtract(dragon().position()).multiply(1, 0, 1);
        return d.lengthSqr() < 1 ? SealGeometry.forward(dragon().getYRot()) : d.normalize();
    }
    /** Turn a lane until the whole flight along it is clear of the towers, so the body never passes through stone. */
    private Vec3 clear(Vec3 at, Vec3 heading, double run, double rise, double exit) {
        for (double degrees : new double[]{0, 35, -35, 70, -70, 110, -110, 150}) {
            Vec3 d = heading.yRot((float) Math.toRadians(degrees));
            Vec3 in = at.subtract(d.scale(run)).add(0, rise, 0), out = at.add(d.scale(run)).add(0, exit, 0), low = at.add(0, 1.5, 0);
            if (EndMoves.seen(e.level, in, low) && EndMoves.seen(e.level, low, out)) return d;
        }
        return heading;
    }
    /** Real island under x, z: loaded, and neither void nor a tower top. */
    private boolean solid(double x, double z) {
        BlockPos at = BlockPos.containing(x, 64, z);
        if (!e.level.hasChunkAt(at)) return false;
        int height = e.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        return height > e.level.getMinBuildHeight() && height <= e.data.floor + 12;
    }
    /** Someone on a tower or far above the island, where the floor moves can't reach. */
    boolean outOfReach(ServerPlayer p) { return p.getY() > ground(p.getX(), p.getZ()) + 10 || p.getY() > e.data.floor + 24; }
    private ServerPlayer nearest(double range) {
        EnderDragon d = dragon(); ServerPlayer best = null; double most = range * range;
        for (ServerPlayer p : fighters()) { double s = p.distanceToSqr(d); if (s < most) { most = s; best = p; } }
        return best;
    }
    private ServerPlayer farthest() {
        EnderDragon d = dragon(); ServerPlayer best = null; double most = -1;
        for (ServerPlayer p : fighters()) { double s = p.distanceToSqr(d); if (s > most) { most = s; best = p; } }
        return best;
    }
    private double flat(ServerPlayer p) { return Math.sqrt(p.position().subtract(dragon().position()).horizontalDistanceSqr()); }

    // =========================================================================== the tick

    void tick() {
        EnderDragon d = dragon();
        if (d == null || !d.isAlive()) return;
        if (d.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.HOVERING) d.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
        if (!e.still) orbit += SealGeometry.ORBIT_SPEED;
        if (e.clock % 100 == 0) { for (ServerPlayer p : fighters()) strengths.merge(p.getUUID(), BossScaling.strength(p, SealGeometry.BASELINE_LEVEL), Math::max); rescale(); }
        Leg leg = legs.peekFirst();
        if (leg != null) {
            leg.age++;
            double t = Math.min(1, leg.age / (double) leg.len);
            if (leg.accelerate) t = t * t;
            Vec3 from = d.position(), to = leg.at(t);
            place(to, leg.face);
            if (leg.step != null) leg.step.accept(from, to);
            if (leg.age >= leg.len && legs.peekFirst() == leg) { legs.removeFirst(); if (leg.end != null) leg.end.run(); }
        } else if (!airborne()) {
            d.setDeltaMovement(Vec3.ZERO);
            float[] twist = twists.peekFirst();
            if (twist != null) {
                turn(twist[0], Math.max(1, Math.abs(Mth.wrapDegrees(twist[0] - d.getYRot())) / Math.max(1, twist[1])));
                if (--twist[1] <= 0) twists.removeFirst();
            } else if (casting == null && !e.still && stagger == 0 && crash == 0) {
                ServerPlayer p = nearest(48);
                if (p != null) turn(SealGeometry.yaw(p.position().subtract(d.position())), 5);
            }
        }
        if (e.grounded > 0) {
            // Its time on the ground pauses while a move winds up or plays out, so none outlasts the landing.
            if (casting == null && legs.isEmpty() && crash == 0) e.grounded--;
            quiet = nearest(SealGeometry.IDLE_REACH) == null ? quiet + 1 : 0;
        }
        if (casting == Move.SCORCH && e.clock % 3 == 0) { Vec3 mouth = d.head.getBoundingBox().getCenter(); e.level.sendParticles(ParticleTypes.DRAGON_BREATH, mouth.x, mouth.y, mouth.z, 6, .4, .3, .4, .01); }
        drag = Math.max(0, drag - SealGeometry.DRAG_FADE);
        tickKnots();
        tickPools();
        tickRings();
        tickBrand();
        if (e.clock % 3 == 0) aura(d);
        if (stance > 0 && e.clock - lastStance > 45 && e.clock % 20 == 0) stance--;

        if (e.transition > 0) { e.transition--; return; }
        if (crash > 0) { if (--crash == 0) recover(); return; }
        if (stagger > 0) { stagger--; return; }
        if (casting != null) {
            if (--windup <= 0) { Move move = casting; casting = null; land(move); landed(move); }
            return;
        }
        if (!legs.isEmpty()) return;
        if (slump > 0) { slump--; return; }
        if (e.still || fighters().isEmpty()) return;
        if (--rest > 0) return;
        if (chain.isEmpty()) {
            if (chainOpen) {
                // After a chain it slumps with its guard open.
                chainOpen = false;
                slump = SealGeometry.slump(phase());
                e.broadcast();
                return;
            }
            choose();
        }
        while (!chain.isEmpty()) { if (start(chain.poll())) return; }
        rest = 6;
    }

    /** Picks the next chain by where its focus stands: never dice. */
    void choose() {
        var people = fighters();
        if (people.isEmpty()) return;
        boolean high = people.stream().anyMatch(this::outOfReach);
        int phase = phase();
        Move[] next;
        if (phase >= 3) {
            if (airborne()) next = new Move[]{Move.DESCENT};
            else if (Math.floorMod(turn, 4) == 3) next = new Move[]{Move.UNMAKING};
            else next = groundFor(phase, high);
        } else if (!airborne()) {
            if (e.grounded <= Move.TAKEOFF.windup + 4 || quiet >= SealGeometry.IDLE_TAKEOFF) next = new Move[]{Move.TAKEOFF};
            else next = groundFor(phase, high);
        } else if (airChains >= SealGeometry.AIR_CHAINS[phase - 1]) next = new Move[]{Move.DESCENT};
        else { next = SealGeometry.airChain(phase, turn, high); airChains++; }
        turn++;
        chain.clear();
        chain.addAll(Arrays.asList(next));
        chainOpen = next.length > 1 || next[0] != Move.TAKEOFF && next[0] != Move.DESCENT && next[0] != Move.UNMAKING;
    }
    private Move[] groundFor(int phase, boolean high) {
        ServerPlayer focus = nearest(200);
        double distance = focus == null ? 99 : flat(focus);
        Move[] next = SealGeometry.groundChain(phase, turn, distance, high);
        // Hugging its back meets the tail first.
        EnderDragon d = dragon();
        Vec3 back = SealGeometry.forward(d.getYRot()).scale(-1);
        boolean behind = fighters().stream().anyMatch(p -> Math.abs(p.getY() - d.getY()) < 5
                && EndGeometry.inSector(d.position(), back, SealGeometry.TAIL_RADIUS * .8, SealGeometry.TAIL_HALF * .8, p.position()));
        if (behind && next[0] != Move.TAIL && action != Move.TAIL) {
            Move[] withTail = new Move[next.length + 1];
            withTail[0] = Move.TAIL;
            System.arraycopy(next, 0, withTail, 1, next.length);
            return withTail;
        }
        return next;
    }

    // =========================================================================== moves

    /** Starts a move: fixes its shape, lines the body up, marks it; false if it can't be done now (no lane, nobody there). */
    public boolean start(Move move) {
        EnderDragon d = dragon();
        if (d == null || !fighting()) return false;
        if (move.air && !airborne() && move != Move.HARPOON) return false;
        if (!move.air && move != Move.UNMAKING && move != Move.HARPOON && airborne()) return false;
        int cast = casts(move);
        e.marks.clear();
        struck.clear();
        windup = SealGeometry.castWindup(move, phase(), cast, fightersAverage());
        if (move == Move.UNMAKING) windup = EndGeometry.Move.ENDINGS.windup;
        if (!warn(move)) { e.marks.clear(); return false; }
        casts.put(move, cast + 1);
        feint = SealGeometry.feint(move, phase(), cast) && move != Move.UNMAKING;
        casting = move; action = move; castTotal = windup; actionStart = e.clock;
        if (SealGeometry.feeds(move, phase())) feed();
        e.broadcast();
        return true;
    }

    /** Marks the move and lines the body up for it. */
    private boolean warn(Move move) {
        EnderDragon d = dragon();
        int phase = phase();
        switch (move) {
            case RIFTDIVE -> {
                ServerPlayer p = pick(); if (p == null) return false;
                spot = under(p);
                dir = clear(spot, approach(spot), SealGeometry.DIVE_RUN, SealGeometry.DIVE_HEIGHT, SealGeometry.DIVE_EXIT);
                Vec3 start = spot.subtract(dir.scale(SealGeometry.DIVE_RUN)).add(0, SealGeometry.DIVE_HEIGHT, 0);
                legs.clear(); legs.add(toward(start, dir, windup, false));
                e.marks.add(spot.add(0, .9, 0)); e.target = spot; e.castFrom = start;
                warning(move, spot.subtract(dir.scale(SealGeometry.DIVE_STRIP)), spot.add(dir.scale(SealGeometry.DIVE_STRIP)), 1, 0);
                sound(d.position(), SoundEvents.ENDER_DRAGON_GROWL, 6, .75F);
            }
            case BREATH -> {
                ServerPlayer p = pick(); if (p == null) return false;
                Vec3 mid = groundAt(p.position());
                dir = clear(mid, approach(mid), SealGeometry.RUN_HALF + SealGeometry.RUN_LEAD, SealGeometry.RUN_ALT, SealGeometry.RUN_ALT + 4);
                runA = mid.subtract(dir.scale(SealGeometry.RUN_HALF)); runB = mid.add(dir.scale(SealGeometry.RUN_HALF));
                for (int i = 0; i < SealGeometry.RUN_POOLS; i++) e.marks.add(groundAt(runA.lerp(runB, i / (SealGeometry.RUN_POOLS - 1D))).add(0, .9, 0));
                Vec3 start = runA.subtract(dir.scale(SealGeometry.RUN_LEAD)).add(0, SealGeometry.RUN_ALT, 0);
                legs.clear(); legs.add(toward(start, dir, windup, false)); e.target = mid; e.castFrom = start;
                warning(move, runA, runB, 1, 0);
                sound(d.position(), SoundEvents.ENDER_DRAGON_GROWL, 6, 1F);
            }
            case STARFALL -> {
                // A ring under every fighter; from the second phase more fall round each of them.
                int extra = phase - 1;
                for (ServerPlayer p : fighters()) {
                    e.marks.add(under(p).add(0, .9, 0));
                    for (int i = 0; i < extra; i++) { double angle = e.clock * .37 + i * Math.PI; e.marks.add(groundAt(p.position().add(Math.cos(angle) * 5.5, 0, Math.sin(angle) * 5.5)).add(0, .9, 0)); }
                }
                if (e.marks.isEmpty()) return false;
                legs.clear(); hold(d.position(), windup + SealGeometry.SHARD_FLIGHT);
                e.castFrom = SealGeometry.chest(d);
                for (Vec3 mark : e.marks) warning(move, e.castFrom, mark.add(0, -.9, 0), SealGeometry.SHARD_RADIUS, 0);
                sound(d.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 6, .6F);
                sound(d.position(), SoundEvents.ENDER_DRAGON_GROWL, 5, 1.25F);
            }
            case HARPOON -> {
                ServerPlayer p = fighters().stream().filter(this::outOfReach).min(Comparator.comparingDouble(q -> q.distanceToSqr(d))).orElse(null);
                if (p == null) p = farthest();
                if (p == null || p.distanceTo(d) > SealGeometry.HARPOON_REACH) return false;
                Vec3 head = d.head.getBoundingBox().getCenter(), end = p.getEyePosition().add(p.getEyePosition().subtract(head).normalize().scale(3));
                if (!EndMoves.seen(e.level, head, p.getEyePosition())) return false;
                harpooned = p; e.castFrom = head; e.target = end; e.marks.add(end);
                if (airborne()) { legs.clear(); hold(d.position(), windup + 6); }
                else twist(SealGeometry.yaw(p.position().subtract(d.position())), windup / 2);
                warning(move, head, end, SealGeometry.HARPOON_RADIUS, 0);
                sound(d.position(), SoundEvents.TRIDENT_RETURN, 5, .5F);
            }
            case DESCENT -> {
                ServerPlayer p = pick(); if (p == null) return false;
                spot = groundAt(island(p.position())); dir = approach(spot);
                Vec3 over = spot.subtract(dir.scale(10)).add(0, 14, 0);
                legs.clear(); legs.add(toward(over, dir, windup, true));
                e.marks.add(spot.add(0, .9, 0)); e.target = spot; e.castFrom = over;
                warning(move, spot, spot, SealGeometry.LAND_RADIUS, 0);
                sound(d.position(), SoundEvents.ENDER_DRAGON_GROWL, 6, .6F);
            }
            case BITE -> {
                Vec3 body = d.position(); dir = SealGeometry.forward(d.getYRot());
                ServerPlayer p = nearest(SealGeometry.BITE_REACH + 4);
                if (p != null) { dir = approach(p.position()); twist(SealGeometry.yaw(dir), Math.min(windup / 2, 6)); }
                Vec3 a = body.add(dir.scale(SealGeometry.BITE_FROM)), b = body.add(dir.scale(SealGeometry.BITE_REACH));
                e.marks.add(b.add(0, .9, 0)); e.castFrom = a; e.target = b; spot = body;
                warning(move, a, b, 1, 0);
                sound(body, SoundEvents.ENDER_DRAGON_GROWL, 3, 1.5F);
            }
            case TAIL -> {
                Vec3 body = d.position(); dir = SealGeometry.forward(d.getYRot()).scale(-1); spot = body;
                e.marks.add(body.add(dir.scale(SealGeometry.TAIL_RADIUS * .6)).add(0, .9, 0)); e.castFrom = body; e.target = body.add(dir.scale(SealGeometry.TAIL_RADIUS));
                warning(move, body, e.target, 1, 0); twist(d.getYRot() - 24, windup);
                sound(body, SoundEvents.ENDER_DRAGON_FLAP, 4, .55F);
            }
            case BUFFET -> {
                Vec3 body = d.position(); spot = body; e.castFrom = groundAt(body); e.target = e.castFrom; e.marks.add(body.add(0, .9, 0));
                warning(move, e.castFrom, e.castFrom, 1, 0); twist(d.getYRot(), windup);
                sound(body, SoundEvents.ENDER_DRAGON_FLAP, 5, .65F);
            }
            case SCORCH -> {
                Vec3 body = d.position(); dir = SealGeometry.forward(d.getYRot());
                ServerPlayer p = nearest(SealGeometry.SCORCH_RADIUS + 4);
                if (p != null) { dir = approach(p.position()); twist(SealGeometry.yaw(dir), Math.min(windup / 2, 8)); }
                spot = body;
                e.marks.add(body.add(dir.scale(SealGeometry.SCORCH_RADIUS * .6)).add(0, .9, 0)); e.castFrom = d.head.getBoundingBox().getCenter(); e.target = body.add(dir.scale(SealGeometry.SCORCH_RADIUS));
                warning(move, body, e.target, 1, 0);
                sound(body, SoundEvents.ENDER_DRAGON_GROWL, 5, 1.1F);
            }
            case RUSH -> {
                Vec3 body = d.position();
                ServerPlayer p = rushTarget(); laneEnd = p == null ? null : lane(p);
                if (laneEnd == null) return false;
                dir = laneEnd.subtract(body).multiply(1, 0, 1).normalize(); spot = body; e.castFrom = body.add(dir.scale(2)); e.target = laneEnd; e.marks.add(laneEnd.add(0, .9, 0));
                warning(move, e.castFrom, laneEnd, 1, 0); twist(SealGeometry.yaw(dir), Math.max(1, windup - 8));
                sound(body, SoundEvents.ENDER_DRAGON_GROWL, 6, .8F); sound(body, SoundEvents.RAVAGER_ROAR, 4, .6F);
            }
            case ROAR, TAKEOFF -> {
                Vec3 body = d.position(); spot = body; e.castFrom = body; e.target = body; e.marks.add(body.add(0, .9, 0));
                warning(move, groundAt(body), groundAt(body), move == Move.ROAR ? SealGeometry.ROAR_RADIUS : SealGeometry.TAKEOFF_RADIUS, 0); twist(d.getYRot(), windup);
                sound(body, move == Move.ROAR ? SoundEvents.ENDER_DRAGON_AMBIENT : SoundEvents.ENDER_DRAGON_FLAP, 5, move == Move.ROAR ? .5F : .8F);
            }
            case LEAP -> {
                ServerPlayer p = farthest();
                if (p == null || flat(p) < SealGeometry.LEAP_MIN || outOfReach(p)) return false;
                Vec3 body = d.position(), toward = p.position().subtract(body).multiply(1, 0, 1).normalize();
                Vec3 at = groundAt(p.position().subtract(toward.scale(SealGeometry.LEAP_SHORT)));
                if (!solid(at.x, at.z)) return false;
                spot = at; dir = toward; e.castFrom = body; e.target = at; e.marks.add(at.add(0, .9, 0));
                warning(move, at, at, SealGeometry.LEAP_RADIUS, 0); twist(SealGeometry.yaw(toward), windup);
                sound(body, SoundEvents.RAVAGER_ROAR, 5, .5F);
            }
            case UNMAKING -> {
                var people = fighters();
                Vec3 high = EndGeometry.center(e.data.floor).add(0, SealGeometry.UNMAKING_HEIGHT, 0);
                e.grounded = 0;
                legs.clear(); legs.add(toward(high, approach(high), 30, true)); hold(high, Math.max(1, windup - 30) + 2);
                e.castFrom = high.add(0, 2, 0);
                e.safeAngle = (float) (Math.atan2(people.getFirst().getZ(), people.getFirst().getX()) + Math.PI / 2);
                e.target = EndGeometry.center(e.data.floor).add(Math.cos(e.safeAngle) * 110, 0, Math.sin(e.safeAngle) * 110);
                for (ServerPlayer p : people) {
                    Vec3 side = p.getLookAngle().multiply(1, 0, 1).normalize().cross(new Vec3(0, 1, 0));
                    if (side.lengthSqr() < .01) side = new Vec3(1, 0, 0);
                    e.marks.add(p.position().add(0, .9, 0).add(side.scale(EndGeometry.SAFE_ORBIT_OFFSET)));
                }
                SetPiecePayload.sendSeeded(e.level, Piece.END_BATTLE_WARNING, e.castFrom, e.target, 1, d, 0, EndMoves.seed(EndGeometry.Move.ENDINGS, -1, 0));
                for (Vec3 safe : e.marks) SetPiecePayload.sendSeeded(e.level, Piece.END_BATTLE_WARNING, e.castFrom, safe, 1, d, 0, EndMoves.seed(EndGeometry.Move.ENDINGS, -1, 1));
                sound(d.position(), SoundEvents.ENDER_DRAGON_GROWL, 8, .45F);
                sound(d.position(), SoundEvents.BEACON_POWER_SELECT, 6, .5F);
            }
        }
        return true;
    }
    /** A warning on the ground or in the air, filling over this cast's windup plus the move's travel. */
    private void warning(Move move, Vec3 origin, Vec3 target, float scale, int role) {
        SetPiecePayload.sendSeeded(e.level, Piece.END_SEAL_WARNING, origin, target, scale, dragon(), 0, seed(move, role, windup + SealGeometry.travel(move)));
    }
    /** A seal warning's seed: the move, a role (0 the warning, 1 the landing flash) and the ticks its fill runs. */
    public static int seed(Move move, int role, int fill) { return move.ordinal() | (role & 1) << 5 | Math.clamp(fill, 0, 1023) << 6; }
    public static Move seedMove(int seed) { return Move.byId(seed & 31); }
    public static int seedRole(int seed) { return (seed >>> 5) & 1; }
    public static int seedFill(int seed) { return seed >>> 6; }
    private int picks;
    private ServerPlayer pick() { var people = fighters(); return people.isEmpty() ? null : people.get(Math.floorMod(picks++, people.size())); }

    /** Lands each move: the strike, then its end pose and window. */
    private void land(Move move) {
        EnderDragon d = dragon();
        int phase = phase();
        switch (move) {
            case RIFTDIVE -> dive();
            case BREATH -> run();
            case STARFALL -> {
                Vec3 from = SealGeometry.chest(d);
                for (Vec3 mark : e.marks) {
                    EndBolt bolt = com.jayptucker.heroesevolved.registry.ModEntities.END_BOLT.get().create(e.level);
                    if (bolt == null) continue;
                    Vec3 to = mark.add(0, -.6, 0);
                    bolt.setOwner(d); bolt.setPos(from); bolt.setDeltaMovement(to.subtract(from).scale(1D / SealGeometry.SHARD_FLIGHT)); bolt.accelerationPower = 0;
                    bolt.shard(damage(move)); e.level.addFreshEntity(bolt); e.bolts.add(bolt);
                }
                sound(from, SoundEvents.ENDER_DRAGON_SHOOT, 6, .8F); sound(from, SoundEvents.AMETHYST_CLUSTER_BREAK, 6, .6F);
            }
            case HARPOON -> {
                ServerPlayer p = harpooned; harpooned = null;
                if (p != null && fighters().contains(p) && EndGeometry.segmentDistance(e.castFrom, e.target, p.position().add(0, .9, 0)) < SealGeometry.HARPOON_RADIUS + .6
                        && EndMoves.seen(e.level, e.castFrom, p.getEyePosition()) && hit(p, move, damage(move))) {
                    Vec3 down = groundAt(p.position().lerp(d.position(), .35)).subtract(p.position());
                    p.setDeltaMovement(down.normalize().scale(1.6).add(0, -.4, 0)); p.hurtMarked = true;
                }
                SetPiecePayload.sendSeeded(e.level, Piece.END_SEAL_WARNING, e.castFrom, e.target, SealGeometry.HARPOON_RADIUS, d, 0, seed(move, 1, 0));
                sound(d.position(), SoundEvents.TRIDENT_THROW.value(), 5, .5F);
            }
            case DESCENT -> {
                Vec3 from = d.position();
                Leg drop = new Leg(from, from.add(dir.scale(4)), spot.add(0, 6, 0), spot, SealGeometry.LAND_DROP, true, false, false);
                drop.end = this::touchdown; legs.clear(); legs.add(drop);
            }
            case BITE -> {
                Vec3 body = d.position(); Vec3 a = e.castFrom, b = e.target;
                for (ServerPlayer p : fighters())
                    if (EndGeometry.stripDistance(a, b, p.position()) < SealGeometry.BITE_RADIUS && p.getY() > body.y - 1.5 && p.getY() < body.y + 4 && hit(p, move, damage(move)))
                        push(p, dir.scale(1.1).add(0, .4, 0));
                Leg lunge = new Leg(body, body, body.add(dir.scale(3)), body.add(dir.scale(3)), 3, true, false, false),
                        back = new Leg(body.add(dir.scale(3)), body.add(dir.scale(3)), body, body, 6, true, false, false);
                legs.add(lunge); legs.add(back); flash(move, a, b, 1);
                sound(body, SoundEvents.EVOKER_FANGS_ATTACK, 3, .5F); sound(body, SoundEvents.ENDER_DRAGON_HURT, 3, 1.4F);
                CameraShakePayload.sendAround(e.level, body, 40, .12F, 8);
            }
            case TAIL -> {
                Vec3 body = spot;
                for (ServerPlayer p : fighters())
                    if (Math.abs(p.getY() - body.y) < 5 && EndGeometry.inSector(body, dir, SealGeometry.TAIL_RADIUS, SealGeometry.TAIL_HALF, p.position()) && hit(p, move, damage(move)))
                        push(p, p.position().subtract(body).multiply(1, 0, 1).normalize().scale(1.6).add(0, .5, 0));
                twists.clear(); float yaw = SealGeometry.yaw(dir.scale(-1)); twists.add(new float[]{yaw + 70, 4}); twists.add(new float[]{yaw, 10});
                flash(move, body, e.target, 1); sound(body, SoundEvents.PLAYER_ATTACK_SWEEP, 4, .45F); sound(body, SoundEvents.ENDER_DRAGON_FLAP, 4, 1.3F);
                CameraShakePayload.sendAround(e.level, body, 48, .18F, 10);
            }
            case BUFFET -> {
                rings.add(new Ring(e.castFrom, damage(move), SealGeometry.BUFFET_REACH));
                SetPiecePayload.sendSeeded(e.level, Piece.END_BATTLE_WAVE, e.castFrom, e.castFrom, 1, null, 0, 0);
                flash(move, e.castFrom, e.castFrom, 3);
                sound(d.position(), SoundEvents.ENDER_DRAGON_FLAP, 6, .5F); CameraShakePayload.sendAround(e.level, d.position(), 60, .22F, 12);
            }
            case SCORCH -> {
                Vec3 body = spot, mouth = d.head.getBoundingBox().getCenter();
                for (ServerPlayer p : fighters())
                    if (Math.abs(p.getY() - body.y) < 6 && EndGeometry.inSector(body, dir, SealGeometry.SCORCH_RADIUS, SealGeometry.SCORCH_HALF, p.position()) && hit(p, move, damage(move)))
                        brand(p);
                for (double r : new double[]{6.5, 12}) for (int s = -1; s <= 1; s += 2) pool(groundAt(body.add(dir.yRot((float) (s * SealGeometry.SCORCH_HALF * .5)).scale(r))), false);
                for (int i = 0; i < 48; i++) {
                    Vec3 v = dir.yRot((float) ((e.level.random.nextFloat() * 2 - 1) * SealGeometry.SCORCH_HALF)).add(0, -.25 - e.level.random.nextFloat() * .2, 0).normalize();
                    e.level.sendParticles(ParticleTypes.DRAGON_BREATH, mouth.x, mouth.y, mouth.z, 0, v.x, v.y, v.z, .55 + e.level.random.nextFloat() * .4);
                }
                flash(move, body, e.target, 1); sound(body, SoundEvents.ENDER_DRAGON_SHOOT, 5, .7F);
            }
            case RUSH -> { if (laneEnd != null) rush(laneEnd); laneEnd = null; }
            case ROAR -> {
                Vec3 body = spot;
                for (ServerPlayer p : fighters())
                    if (Math.abs(p.getY() - body.y) < 8 && p.position().multiply(1, 0, 1).distanceTo(body.multiply(1, 0, 1)) < SealGeometry.ROAR_RADIUS && hit(p, move, damage(move))) {
                        push(p, away(p, body).scale(1.8).add(0, .55, 0)); p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                    }
                flash(move, groundAt(body), groundAt(body), SealGeometry.ROAR_RADIUS); sound(body, SoundEvents.ENDER_DRAGON_GROWL, 8, .5F);
                CameraShakePayload.sendAround(e.level, body, 120, .4F, 24);
            }
            case LEAP -> leap();
            case TAKEOFF -> {
                Vec3 body = spot;
                for (ServerPlayer p : fighters())
                    if (Math.abs(p.getY() - body.y) < 5 && p.position().multiply(1, 0, 1).distanceTo(body.multiply(1, 0, 1)) < SealGeometry.TAKEOFF_RADIUS && hit(p, move, damage(move)))
                        push(p, away(p, body).scale(1.4).add(0, .7, 0));
                flash(move, groundAt(body), groundAt(body), SealGeometry.TAKEOFF_RADIUS);
                e.grounded = 0; quiet = 0; airChains = 0; exhausted = false;
                Vec3 f = SealGeometry.forward(d.getYRot());
                legs.add(new Leg(body, body.add(0, 8, 0), body.add(f.scale(6)).add(0, 18, 0), body.add(f.scale(16)).add(0, 24, 0), 24, true, true, false));
                sound(body, SoundEvents.ENDER_DRAGON_FLAP, 6, .6F); CameraShakePayload.sendAround(e.level, body, 60, .2F, 12);
            }
            case UNMAKING -> unmaking();
        }
    }
    /** Bookkeeping after a move lands. */
    private void landed(Move move) {
        feint = false;
        rest = Math.max(rest, Math.round(move.rest * SealGeometry.restScale(phase())) + SealGeometry.hold(move));
        if (feeding >= 0 && move != Move.UNMAKING && move != Move.LEAP && move != Move.BREATH && move != Move.RIFTDIVE) endFeed();
    }
    float damage(Move move) { return SealGeometry.damage(move, phase()); }

    private void dive() {
        EnderDragon d = dragon(); Vec3 from = d.position(), low = spot.add(0, 1.5, 0), out = spot.add(dir.scale(SealGeometry.DIVE_RUN)).add(0, SealGeometry.DIVE_EXIT, 0);
        Vec3 a = spot.subtract(dir.scale(SealGeometry.DIVE_STRIP)), b = spot.add(dir.scale(SealGeometry.DIVE_STRIP));
        Leg in = new Leg(from, from.add(dir.scale(12)).add(0, -3, 0), low.subtract(dir.scale(10)).add(0, 1, 0), low, SealGeometry.DIVE_APPROACH, true, true, false);
        Leg up = new Leg(low, low.add(dir.scale(10)), out.subtract(dir.scale(10)).add(0, -4, 0), out, SealGeometry.DIVE_PULLOUT, true, true, false);
        BiConsumer<Vec3, Vec3> sweep = (prev, now) -> {
            for (ServerPlayer p : fighters()) {
                if (struck.contains(p.getUUID())) continue;
                Vec3 body = p.position().add(0, .9, 0);
                if (EndGeometry.segmentDistance(prev.add(0, 1.5, 0), now.add(0, 1.5, 0), body) < SealGeometry.DIVE_RADIUS && EndGeometry.stripDistance(a, b, body) < SealGeometry.DIVE_RADIUS + .5) {
                    struck.add(p.getUUID());
                    if (hit(p, Move.RIFTDIVE, damage(Move.RIFTDIVE))) push(p, dir.scale(1.2).add(0, .6, 0));
                }
            }
            SetPiecePayload.send(e.level, Piece.END_BATTLE_DASH, prev.add(0, 2, 0), now.add(0, 2, 0), 5, d);
        };
        in.step = sweep; up.step = sweep;
        in.end = () -> {
            for (ServerPlayer p : fighters())
                if (!struck.contains(p.getUUID()) && p.position().multiply(1, 0, 1).distanceTo(spot.multiply(1, 0, 1)) < SealGeometry.DIVE_IMPACT && Math.abs(p.getY() - spot.y) < 4) {
                    struck.add(p.getUUID());
                    if (hit(p, Move.RIFTDIVE, damage(Move.RIFTDIVE) * .85F)) push(p, away(p, spot).scale(1.3).add(0, .55, 0));
                }
            SetPiecePayload.sendSeeded(e.level, Piece.END_BATTLE_IMPACT, spot.add(0, .2, 0), spot, SealGeometry.DIVE_IMPACT, null, 0, 0);
            flash(Move.RIFTDIVE, a, b, 1); debris(spot, 30);
            CameraShakePayload.sendAround(e.level, spot, 120, .42F, 18); sound(spot, SoundEvents.GENERIC_EXPLODE.value(), 4, .6F);
        };
        legs.clear(); legs.add(in); legs.add(up);
        sound(from, SoundEvents.ENDER_DRAGON_FLAP, 6, 1.3F);
    }
    private void run() {
        EnderDragon d = dragon(); Vec3 from = d.position(), end = runB.add(dir.scale(SealGeometry.RUN_LEAD)).add(0, SealGeometry.RUN_ALT + 4, 0);
        Leg pass = new Leg(from, from.add(dir.scale(15)), end.subtract(dir.scale(15)).add(0, -4, 0), end, SealGeometry.RUN_TICKS, true, true, false);
        runPools = 0;
        Vec3 length = runB.subtract(runA).multiply(1, 0, 1); double total = length.length();
        List<Vec3> marks = List.copyOf(e.marks);
        pass.step = (prev, now) -> {
            Vec3 mouth = d.head.getBoundingBox().getCenter();
            double along = mouth.subtract(runA).multiply(1, 0, 1).dot(length) / Math.max(1e-6, total * total);
            while (runPools < Math.min(SealGeometry.RUN_POOLS, marks.size()) && along >= runPools / (SealGeometry.RUN_POOLS - 1D)) { pool(marks.get(runPools).add(0, -.9, 0), true); runPools++; }
            if (along > -.15 && along < 1.1) e.level.sendParticles(ParticleTypes.DRAGON_BREATH, mouth.x, mouth.y - 1, mouth.z, 16, .7, 1.4, .7, .02);
        };
        pass.end = () -> { while (runPools < Math.min(SealGeometry.RUN_POOLS, marks.size())) { pool(marks.get(runPools).add(0, -.9, 0), true); runPools++; } };
        legs.clear(); legs.add(pass); sound(from, SoundEvents.ENDER_DRAGON_SHOOT, 6, .6F);
    }
    private void touchdown() {
        EnderDragon d = dragon(); Vec3 at = d.position(); d.setDeltaMovement(Vec3.ZERO);
        for (ServerPlayer p : fighters())
            if (p.position().multiply(1, 0, 1).distanceTo(at.multiply(1, 0, 1)) < SealGeometry.LAND_RADIUS && Math.abs(p.getY() - at.y) < 4 && hit(p, Move.DESCENT, damage(Move.DESCENT)))
                push(p, away(p, at).scale(1.3).add(0, .5, 0));
        settle(SealGeometry.LANDED_TICKS[phase() - 1]);
        SetPiecePayload.sendSeeded(e.level, Piece.END_BATTLE_IMPACT, at.add(0, .2, 0), at, SealGeometry.LAND_RADIUS, null, 0, 0);
        flash(Move.DESCENT, at, at, SealGeometry.LAND_RADIUS); debris(at, 40);
        CameraShakePayload.sendAround(e.level, at, 160, .55F, 22); sound(at, SoundEvents.GENERIC_EXPLODE.value(), 5, .5F); sound(at, SoundEvents.ENDER_DRAGON_GROWL, 8, .7F);
        tell("landed");
    }
    /** On the ground now: for {@code ticks}, its hunt reset. */
    private void settle(int ticks) { e.grounded = Math.max(1, ticks); airChains = 0; quiet = 0; falling = false; chain.clear(); }

    /** Seal Rush: straight down its lane along the ground, trampling whoever is in it aside, and skidding to a stop. */
    private void rush(Vec3 end) {
        EnderDragon d = dragon(); Vec3 from = d.position(), a = e.castFrom; Vec3 side = new Vec3(-dir.z, 0, dir.x);
        Leg charge = new Leg(from, from.lerp(end, 1 / 3D), from.lerp(end, 2 / 3D), end, SealGeometry.RUSH_TICKS, true, false, false);
        ParticleOptions dust = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.END_STONE.defaultBlockState());
        charge.step = (prev, now) -> {
            double g = ground(now.x, now.z); d.setPos(now.x, g, now.z);
            for (ServerPlayer p : fighters()) {
                if (struck.contains(p.getUUID()) || Math.abs(p.getY() - g) > 3) continue;
                if (EndGeometry.segmentDistance(prev.multiply(1, 0, 1), now.multiply(1, 0, 1), p.position().multiply(1, 0, 1)) < SealGeometry.RUSH_RADIUS) {
                    struck.add(p.getUUID()); double s = Math.signum(p.position().subtract(now).dot(side));
                    if (hit(p, Move.RUSH, damage(Move.RUSH))) push(p, side.scale((s == 0 ? 1 : s) * 1.5).add(dir.scale(.5)).add(0, .6, 0));
                }
            }
            burst(dust, now.x, g + .3, now.z, 14, 1.6, .2, 1.6, .15); burst(ParticleTypes.CLOUD, now.x, g + .5, now.z, 8, 1.5, .25, 1.5, .08);
            SetPiecePayload.send(e.level, Piece.END_BATTLE_DASH, prev.add(0, 1.2, 0), now.add(0, 1.2, 0), 3, d);
        };
        charge.end = () -> {
            for (ServerPlayer p : fighters())
                if (!struck.contains(p.getUUID()) && p.position().multiply(1, 0, 1).distanceTo(end.multiply(1, 0, 1)) < SealGeometry.RUSH_RADIUS + 1.5 && Math.abs(p.getY() - end.y) < 3
                        && hit(p, Move.RUSH, damage(Move.RUSH) * .6F))
                    push(p, away(p, end).scale(1.2).add(dir.scale(.6)).add(0, .5, 0));
            flash(Move.RUSH, a, end, 1); debris(end, 24);
            CameraShakePayload.sendAround(e.level, end, 70, .32F, 12); sound(end, SoundEvents.GENERIC_EXPLODE.value(), 3, .75F);
        };
        legs.clear(); legs.add(charge);
        sound(from, SoundEvents.ENDER_DRAGON_GROWL, 6, 1.15F); CameraShakePayload.sendAround(e.level, from, 50, .15F, 10);
    }
    /** Rift Leap: an arc onto the marked ring; the landing hits the ring and a shock rolls out. */
    private void leap() {
        EnderDragon d = dragon(); Vec3 from = d.position(), to = spot;
        Vec3 up = new Vec3(0, SealGeometry.LEAP_HEIGHT, 0);
        Leg arc = new Leg(from, from.lerp(to, .25).add(up), from.lerp(to, .75).add(up), to, SealGeometry.LEAP_TICKS, true, true, false);
        arc.end = () -> {
            for (ServerPlayer p : fighters())
                if (p.position().multiply(1, 0, 1).distanceTo(to.multiply(1, 0, 1)) < SealGeometry.LEAP_RADIUS && Math.abs(p.getY() - to.y) < 4 && hit(p, Move.LEAP, damage(Move.LEAP)))
                    push(p, away(p, to).scale(1.4).add(0, .6, 0));
            rings.add(new Ring(to, damage(Move.LEAP) * .5F, SealGeometry.BUFFET_REACH));
            SetPiecePayload.sendSeeded(e.level, Piece.END_BATTLE_WAVE, to, to, 1, null, 0, 0);
            SetPiecePayload.sendSeeded(e.level, Piece.END_BATTLE_IMPACT, to.add(0, .2, 0), to, SealGeometry.LEAP_RADIUS, null, 0, 0);
            flash(Move.LEAP, to, to, SealGeometry.LEAP_RADIUS); debris(to, 40);
            CameraShakePayload.sendAround(e.level, to, 120, .5F, 18); sound(to, SoundEvents.GENERIC_EXPLODE.value(), 5, .55F);
        };
        legs.clear(); legs.add(arc);
        sound(from, SoundEvents.ENDER_DRAGON_FLAP, 6, .7F);
    }
    /** The Unmaking: everyone it can see, except in a safe orbit, the sector, behind cover, or answered by their power. */
    private void unmaking() {
        EnderDragon d = dragon();
        float damage = damage(Move.UNMAKING);
        for (ServerPlayer p : fighters()) {
            boolean safe = EndGeometry.safeSector(EndGeometry.center(e.data.floor), p.position(), e.safeAngle)
                    || e.marks.stream().anyMatch(mark -> mark.distanceToSqr(p.position().add(0, .9, 0)) <= EndGeometry.SAFE_ORBIT_RADIUS * EndGeometry.SAFE_ORBIT_RADIUS);
            if (safe || !EndMoves.seen(e.level, e.castFrom, p.getEyePosition())) continue;
            BossLocks.Key key = BossLocks.key(p, BossLocks.Lock.UNBLOCKABLE);
            if (key != null) {
                boolean spared = switch (key.role()) {
                    case BRACE, WALL -> com.jayptucker.heroesevolved.radiation.NuclearShields.canBrace(p) || key.role() == BossLocks.Role.WALL && BossLocks.usedWithin(p, 40);
                    case DODGE, STOP -> BossLocks.usedWithin(p, SealGeometry.UNMAKING_DODGE);
                    default -> false;
                };
                if (spared) { SetPiecePayload.sendSeeded(e.level, Piece.END_SEAL_SPARED, p.position().add(0, 1, 0), e.castFrom, 1, p, 0, key.role().ordinal()); continue; }
                if (key.role() == BossLocks.Role.OUTLAST) {
                    float left = Math.max(0, p.getHealth() - SealGeometry.OUTLAST_LEFT) / BossScaling.blowScale(fightersAverage());
                    if (left < damage) { if (left > 0) hit(p, null, left); continue; }
                }
            }
            hit(p, Move.UNMAKING, damage);
        }
        SetPiecePayload.sendSeeded(e.level, Piece.END_BATTLE_NOVA, e.castFrom, e.target, 1, d, 0, EndMoves.seed(EndGeometry.Move.ENDINGS, -1, 0));
        CameraShakePayload.sendAround(e.level, d.position(), 220, .6F, 30);
        sound(d.position(), SoundEvents.BEACON_DEACTIVATE, 8, .5F); sound(d.position(), SoundEvents.GENERIC_EXPLODE.value(), 6, .5F);
        endFeed();
        // Spent, it falls back to the island and lies there.
        exhausted = true;
        crashDown(SealGeometry.CRASH_TICKS + 20);
    }

    // =========================================================================== hits on the party

    /**
     * A blow from {@code move} (null for a light one): most of it is the body's blow, which armour and a raised shield
     * meet; {@link SealGeometry#PIERCE} of it is the Seal's fracture, which no armour stops; a heavy move also takes
     * {@link SealGeometry#HEAVY_SHARE} of the target's own maximum health. Scaled to the party's strength.
     */
    boolean hit(ServerPlayer p, Move move, float amount) {
        if (!fighting() || !fighters().contains(p) || EndBattle.sheltered(p) || !p.isAlive() || amount <= 0) return false;
        float total = amount * BossScaling.blowScale(fightersAverage()) + (move != null && SealGeometry.heavy(move) ? p.getMaxHealth() * SealGeometry.HEAVY_SHARE : 0);
        float fracture = total * SealGeometry.PIERCE;
        p.invulnerableTime = 0;
        boolean landed = p.hurt(dragon().damageSources().mobAttack(dragon()), total - fracture);
        if (p.isAlive()) { p.invulnerableTime = 0; landed |= p.hurt(fracture(), fracture); }
        if (landed) EndChannel.cancel(p);
        return landed;
    }
    DamageSource fracture() {
        return new DamageSource(e.level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(FRACTURE), dragon());
    }
    private static Vec3 away(ServerPlayer p, Vec3 from) { Vec3 d = p.position().subtract(from).multiply(1, 0, 1); return d.lengthSqr() < .01 ? new Vec3(1, 0, 0) : d.normalize(); }
    private static void push(ServerPlayer p, Vec3 v) { p.setDeltaMovement(p.getDeltaMovement().scale(.3).add(v)); p.hurtMarked = true; }

    // ---- Riftbrand ----
    void brand(ServerPlayer p) {
        if (phase() < 2 || !p.isAlive()) return;
        p.addEffect(new MobEffectInstance(ModItems.RIFTBRAND, SealGeometry.BRAND_TICKS, 0));
        unseen.put(p.getUUID(), 0);
        tell(p, "brand");
    }
    private void tickBrand() {
        if (e.clock % 20 != 0) return;
        EnderDragon d = dragon();
        Vec3 head = d.head.getBoundingBox().getCenter();
        for (ServerPlayer p : fighters()) {
            if (!p.hasEffect(ModItems.RIFTBRAND)) { unseen.remove(p.getUUID()); continue; }
            if (EndMoves.seen(e.level, head, p.getEyePosition())) {
                unseen.put(p.getUUID(), 0);
                p.invulnerableTime = 0;
                if (p.hurt(fracture(), SealGeometry.BRAND_DRAIN)) mendBy(d.getMaxHealth() * SealGeometry.BRAND_FEED);
                SetPiecePayload.send(e.level, Piece.END_SEAL_KEY, p.position().add(0, 1.1, 0), SealGeometry.chest(d), 1, p);
            } else if (unseen.merge(p.getUUID(), 20, Integer::sum) >= SealGeometry.BRAND_UNSEEN) { p.removeEffect(ModItems.RIFTBRAND); unseen.remove(p.getUUID()); }
        }
    }
    /** Mends, never past the top of the phase it is in. */
    private void mendBy(float amount) {
        EnderDragon d = dragon();
        float top = d.getMaxHealth() * SealGeometry.phaseTop(phase());
        if (d.getHealth() < top) d.setHealth(Math.min(top, d.getHealth() + amount));
    }

    // ---- pools and rings ----
    private void pool(Vec3 at, boolean strike) {
        int life = phase() == 3 ? SealGeometry.POOL_TICKS_LAST : SealGeometry.POOL_TICKS;
        pools.add(new Pool(at, life));
        if (strike) for (ServerPlayer p : fighters()) if (inPool(p, at) && hit(p, Move.BREATH, damage(Move.BREATH))) brand(p);
        SetPiecePayload.sendSeeded(e.level, Piece.END_BATTLE_POOL, at, at, SealGeometry.POOL_RADIUS, null, 0, life);
    }
    private static boolean inPool(ServerPlayer p, Vec3 at) {
        return p.position().multiply(1, 0, 1).distanceTo(at.multiply(1, 0, 1)) < SealGeometry.POOL_RADIUS + .3 && p.getY() > at.y - 1 && p.getY() < at.y + 2.5;
    }
    private void tickPools() {
        for (var it = pools.iterator(); it.hasNext(); ) {
            Pool pool = it.next();
            if (++pool.age >= pool.life) { it.remove(); continue; }
            if (pool.age % 10 == 0) for (ServerPlayer p : fighters()) if (inPool(p, pool.at) && hit(p, null, SealGeometry.poolDamage(phase()))) brand(p);
            if (pool.age % 4 == 0) e.level.sendParticles(ParticleTypes.DRAGON_BREATH, pool.at.x, pool.at.y + .25, pool.at.z, 4, SealGeometry.POOL_RADIUS * .45, .05, SealGeometry.POOL_RADIUS * .45, .004);
        }
    }
    private void tickRings() {
        for (var it = rings.iterator(); it.hasNext(); ) {
            Ring ring = it.next();
            double radius = ++ring.age * SealGeometry.WAVE_SPEED;
            for (ServerPlayer p : fighters())
                if (!ring.hit.contains(p.getUUID()) && Math.abs(p.position().multiply(1, 0, 1).distanceTo(ring.at.multiply(1, 0, 1)) - radius) < 1.4
                        && Math.abs(p.getY() - ground(p.getX(), p.getZ())) < SealGeometry.WAVE_HEIGHT) {
                    ring.hit.add(p.getUUID());
                    if (hit(p, null, ring.damage)) push(p, away(p, ring.at).scale(.8).add(0, .4, 0));
                }
            if (radius > ring.reach) it.remove();
        }
    }

    // =========================================================================== the knots

    /** The four knots arrive with the fight, warding; each altar's nearest tower crystal feeds its knot. */
    void formKnots() {
        for (int i = 0; i < 4; i++) {
            if (knots[i] != null && !knots[i].isRemoved()) knots[i].discard();
            knots[i] = SealKnot.form(this, i, phase() == 1 ? SealView.WARDING : SealView.DORMANT, knotHealth(), knotAt(i));
            mend[i] = 0;
        }
        assignFeeders();
    }
    void assignFeeders() {
        List<EndCrystal> free = new ArrayList<>(e.crystals.stream().filter(c -> !c.isRemoved()).toList());
        for (int i = 0; i < 4; i++) {
            Vec3 at = knotAt(i);
            EndCrystal best = free.stream().min(Comparator.comparingDouble(c -> c.position().multiply(1, 0, 1).distanceToSqr(at.multiply(1, 0, 1)))).orElse(null);
            feeders[i] = best;
            if (best != null) free.remove(best);
        }
    }
    private void tickKnots() {
        int phase = phase();
        for (int i = 0; i < 4; i++) {
            SealKnot knot = knots[i];
            if (knot != null && knot.isRemoved()) { knots[i] = knot = null; if (mend[i] == 0) mend[i] = SealGeometry.KNOT_MEND; }
            if (knot == null && mend[i] > 0 && --mend[i] == 0) {
                // A ward knot re-forms only while its tower crystal still feeds it; a sleeping one always does.
                boolean fed = feeders[i] != null && !feeders[i].isRemoved();
                if (phase == 1 && !shattered && fed) knots[i] = SealKnot.form(this, i, SealView.WARDING, knotHealth(), knotAt(i));
                else if (phase >= 2) knots[i] = SealKnot.form(this, i, SealView.DORMANT, knotHealth(), knotAt(i));
                else mend[i] = phase == 1 && !shattered ? 0 : SealGeometry.KNOT_MEND;
            }
        }
        if (feeding >= 0 && --feedTicks <= 0) endFeed();
        if (e.clock % 10 == 0) for (int i = 0; i < 4; i++) {
            EndCrystal crystal = feeders[i];
            if (crystal == null || crystal.isRemoved()) continue;
            crystal.setBeamTarget(knots[i] != null && !knots[i].isRemoved() ? BlockPos.containing(knots[i].position()).below() : null);
        }
    }
    /** A big move drinks from the knot nearest the Dragon: the room weapon, while it winds up and plays out. */
    private void feed() {
        Vec3 body = dragon().position();
        int best = -1; double most = Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            SealKnot k = knots[i];
            if (k == null || k.isRemoved() || k.state() != SealView.DORMANT) continue;
            double s = k.position().distanceToSqr(body);
            if (s < most) { most = s; best = i; }
        }
        if (best < 0) return;
        if (feeding >= 0 && knots[feeding] != null) knots[feeding].state(SealView.DORMANT);
        feeding = best; feedTicks = SealGeometry.FEED_TICKS; knots[best].state(SealView.FEEDING);
        SealKnot k = knots[best];
        e.level.playSound(null, k.getX(), k.getY(), k.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3, .7F);
        tell("feeding");
    }
    private void endFeed() {
        if (feeding >= 0 && knots[feeding] != null && !knots[feeding].isRemoved()) knots[feeding].state(SealView.DORMANT);
        feeding = -1; feedTicks = 0;
    }
    /** A knot broke: in the first phase one ring of the Seal breaks, all four shatter it; a feeding knot backfires. */
    void knotBroken(int index, SealKnot knot, ServerPlayer by) {
        int state = knot.state();
        knots[index] = null;
        Vec3 at = knot.aimPoint();
        SetPiecePayload.sendSeeded(e.level, Piece.END_SEAL_CRACK, at, SealGeometry.chest(dragon()), 2, knot, 0, 16);
        e.level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.5F, .5F);
        CameraShakePayload.sendAround(e.level, at, 32, .25F, 10);
        if (state == SealView.WARDING) {
            boolean fed = feeders[index] != null && !feeders[index].isRemoved();
            mend[index] = fed ? SealGeometry.KNOT_MEND : 0;
            if (warding() == 0) shatter(by);
            else tell("knot");
        } else if (state == SealView.FEEDING) {
            mend[index] = SealGeometry.KNOT_MEND;
            feeding = -1; feedTicks = 0;
            backfire(by);
        } else mend[index] = SealGeometry.KNOT_MEND;
    }
    /** All four knots broken: the Seal shatters and throws it out of the sky. */
    private void shatter(ServerPlayer by) {
        shattered = true;
        for (int i = 0; i < 4; i++) mend[i] = 0;
        SetPiecePayload.send(e.level, Piece.END_SEAL_SHATTER, SealGeometry.chest(dragon()), SealGeometry.chest(dragon()), 1, dragon());
        sound(dragon().position(), SoundEvents.ENDER_DRAGON_HURT, 8, .5F);
        interrupt();
        crashDown(SealGeometry.CRASH_TICKS);
        tell("shattered");
    }
    /** Breaking the knot it drinks from: the move is gone, it crashes, and takes a tenth of its health past the caps. */
    private void backfire(ServerPlayer by) {
        EnderDragon d = dragon();
        SetPiecePayload.send(e.level, Piece.END_SEAL_SHATTER, SealGeometry.chest(d), SealGeometry.chest(d), 2, d);
        sound(d.position(), SoundEvents.ENDER_DRAGON_HURT, 8, .45F);
        legs.clear(); twists.clear();
        interrupt();
        crashDown(SealGeometry.CRASH_TICKS);
        e.bypassDragon(SealGeometry.BACKFIRE_SHARE, by);
        tell("backfire");
    }
    /** A tower crystal broke: its altar's knot loses its beam and won't re-form in the first phase. */
    void crystalBroken(EndCrystal crystal, ServerPlayer by) {
        Vec3 at = crystal.position();
        crystal.discard();
        e.level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 4, .6F);
        SetPiecePayload.sendSeeded(e.level, Piece.END_SEAL_CRACK, at.add(0, 1, 0), at, 3, null, 0, 16);
        for (int i = 0; i < 4; i++) if (feeders[i] == crystal) { feeders[i] = null; if (phase() == 1 && knots[i] == null) mend[i] = 0; }
        tell("crystal");
    }

    // =========================================================================== the Dragon as a lock (out of reach)

    /** It is out of reach while it flies in the first two phases (combat/BossLocks; EnderDragonLockMixin asks this). */
    public BossLocks.Lock lock() { return fighting() && airborne() && phase() <= 2 && e.transition == 0 ? BossLocks.Lock.OUT_OF_REACH : null; }
    /** An aimed bring-down: drag; at full it is pulled out of the sky. */
    public void answered(ServerPlayer player, BossLocks.Key key) {
        if (lock() == null || key.role() != BossLocks.Role.BRING_DOWN) return;
        float add = key.strong() ? SealGeometry.DRAG_STRONG : SealGeometry.DRAG_GOOD;
        if (com.jayptucker.heroesevolved.amplify.Amplification.surged(player)) add *= BossLocks.SURGED;
        drag += add;
        Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(.6)).add(0, -.25, 0);
        SetPiecePayload.sendSeeded(e.level, Piece.END_SEAL_KEY, from, SealGeometry.chest(dragon()), 2, dragon(), 0, key.role().ordinal() | (key.strong() ? 1 << 5 : 0));
        e.level.playSound(null, dragon().getX(), dragon().getY(), dragon().getZ(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 4, .5F);
        if (drag >= 1) { drag = 0; draggedDown(); }
    }
    /** Pulled out of the sky: it lands hard where it is and fights on foot. */
    private void draggedDown() {
        Leg leg = legs.peekFirst();
        if (leg != null && leg.committed && action == Move.UNMAKING) return;
        interrupt();
        legs.clear(); twists.clear();
        EnderDragon d = dragon();
        Vec3 from = d.position(), land = groundAt(island(from.add(d.getDeltaMovement().scale(4))));
        falling = true;
        Leg fall = new Leg(from, from.add(0, -2, 0), land.add(0, 6, 0), land, 14, true, false, true);
        fall.end = () -> {
            settle(SealGeometry.LANDED_TICKS[phase() - 1]);
            stagger = SealGeometry.DRAGGED_TICKS;
            impact(land, SealGeometry.LAND_RADIUS);
        };
        legs.add(fall);
        tell("dragged");
    }

    // =========================================================================== hits on the Dragon

    /**
     * A blow on the Dragon (part null for a blow on its whole body): the locks' factors, its windows, the caps, the
     * Seal's gate, then its stance and the star. True if it landed.
     */
    boolean hurt(DamageSource source, float incoming, EnderDragonPart part, boolean heavy) {
        EnderDragon d = dragon();
        if (e.transition > 0) return false;
        ServerPlayer by = source.getEntity() instanceof ServerPlayer p ? p : null;
        boolean returned = source.getDirectEntity() instanceof EndBolt bolt && bolt.returned();
        float factor;
        if (returned) factor = 0;
        else if (by == null) factor = 1;
        else if (airborne() && crash == 0) factor = BossLocks.hitFactor(source, BossLocks.Lock.OUT_OF_REACH) * SealGeometry.AIR_TAKES;
        else factor = BossLocks.plain(source) || !BossLocks.gifted(by) ? SealGeometry.PLAIN_BODY : 1;
        if (part == d.head) factor *= SealGeometry.HEAD_TAKES;
        else if (part != null && !(phase() == 3 && part == SealGeometry.body(d))) factor *= SealGeometry.BODY_TAKES;
        if (crash > 0 || slump > 0 && casting == null) factor *= SealGeometry.OPEN_TAKES;
        else if (stagger > 0) factor *= SealGeometry.STAGGER_TAKES;
        boolean star = false;
        if (phase() == 3 && by != null && onStar(source, part)) {
            star = true;
            BossLocks.Key key = !BossLocks.plain(source) ? BossLocks.key(by, BossLocks.Lock.WEAK_POINT) : null;
            factor *= key != null && key.channel() == BossLocks.Channel.HITS ? Math.max(SealGeometry.STAR_TAKES, key.strong() ? BossLocks.STRONG_HITS : BossLocks.GOOD_HITS) : SealGeometry.STAR_TAKES;
        }
        if (warded()) factor *= SealGeometry.SEAL_TAKES[Math.min(4, warding())];
        float amount;
        if (returned) amount = e.damage.effective() * SealGeometry.RETURNED_SHARE / Math.max(1, e.damage.units(d));
        else amount = e.damage.amount(d, source, incoming, e.clock, factor, false, SealGeometry.HIT_CAP, SealGeometry.SECOND_CAP);
        if (warded()) {
            float room = d.getHealth() - d.getMaxHealth() * SealGeometry.SEAL_GATE;
            amount = Math.min(amount, room);
            if (amount <= 0) {
                if (e.clock - lastWardFlash > 6) {
                    lastWardFlash = e.clock;
                    SetPiecePayload.send(e.level, Piece.END_SEAL_CRACK, SealGeometry.chest(d), SealGeometry.chest(d), 1, d);
                    e.level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 3, .5F);
                    if (by != null) tell(by, "warded");
                }
                return false;
            }
        }
        if (amount <= 0) return false;
        boolean lethal = amount >= d.getHealth();
        float actual = Math.min(amount, Math.max(0, d.getHealth() - 1));
        boolean landed = actual <= 0 || EndBattle.reallyHurt(d, source, lethal ? amount : actual);
        if (!landed) return false;
        if (!returned) e.damage.paid(e.clock, amount);
        e.lastHurt = e.clock;
        if (by != null) e.killer = by.getUUID();
        if (heavy && casting != null && !SealGeometry.committed(casting)) stanceHit();
        if (star) starHit();
        if (lethal) e.dragonDown(source);
        else e.phaseChange();
        return true;
    }
    /** A blow that lands on the open star: on its chest part, a shot close to it, or a blow aimed at it from near. */
    private boolean onStar(DamageSource source, EnderDragonPart part) {
        EnderDragon d = dragon();
        if (part == SealGeometry.body(d)) return true;
        Vec3 chest = SealGeometry.chest(d);
        if (source.getDirectEntity() != null && !(source.getDirectEntity() instanceof net.minecraft.world.entity.player.Player)
                && source.getDirectEntity().position().distanceTo(chest) < SealGeometry.STAR_RADIUS + .8) return true;
        if (!(source.getEntity() instanceof ServerPlayer p)) return false;
        Vec3 to = chest.subtract(p.getEyePosition());
        return to.length() < 14 && to.normalize().dot(p.getLookAngle()) > Math.cos(Math.toRadians(14));
    }
    private void stanceHit() {
        if (e.clock - lastInterrupt < SealGeometry.INTERRUPT_GAP || e.clock - lastStance < SealGeometry.STANCE_GAP) return;
        lastStance = e.clock;
        stance++;
        SetPiecePayload.sendSeeded(e.level, Piece.END_SEAL_CRACK, SealGeometry.chest(dragon()), SealGeometry.chest(dragon()), 1, dragon(), 0, 4 + stance * 4);
        if (stance >= SealGeometry.STANCE_HITS) {
            lastInterrupt = e.clock; stance = 0;
            interrupt();
            stagger = SealGeometry.STAGGER;
            sound(dragon().position(), SoundEvents.ENDER_DRAGON_HURT, 6, .6F);
            CameraShakePayload.sendAround(e.level, dragon().position(), 48, .3F, 14);
        }
    }
    private void starHit() {
        while (!starHits.isEmpty() && e.clock - starHits.peekFirst() > SealGeometry.STAR_WINDOW) starHits.removeFirst();
        starHits.add(e.clock);
        if (starHits.size() >= SealGeometry.STAR_CRACKS) {
            starHits.clear();
            interrupt();
            stagger = Math.max(stagger, SealGeometry.STAR_REEL);
            SetPiecePayload.send(e.level, Piece.END_SEAL_SHATTER, SealGeometry.chest(dragon()), SealGeometry.chest(dragon()), 3, dragon());
            sound(dragon().position(), SoundEvents.ENDER_DRAGON_HURT, 6, .4F);
            tell("star");
        }
    }
    /** Ends the move being cast and the chain (a committed path still finishes). */
    void interrupt() {
        if (casting != null) SetPiecePayload.send(e.level, Piece.END_BATTLE_CANCEL, dragon().position(), dragon().position(), 1, dragon());
        if (casting != null && SealGeometry.feeds(casting, phase())) endFeed();
        casting = null; windup = 0; feint = false; chain.clear(); chainOpen = false; harpooned = null; laneEnd = null;
        cancelLegs();
    }
    /** Thrown out of the sky (or knocked flat on the ground): it lies stunned for {@code ticks}. */
    void crashDown(int ticks) {
        EnderDragon d = dragon();
        crash = Math.max(crash, ticks);
        slump = 0; stagger = 0;
        if (!airborne() && legs.isEmpty()) { settle(Math.max(e.grounded, SealGeometry.LANDED_TICKS[phase() - 1])); return; }
        legs.clear(); twists.clear();
        Vec3 from = d.position(), land = groundAt(island(from.add(d.getDeltaMovement().scale(8))));
        falling = true;
        Leg fall = new Leg(from, from.add(d.getDeltaMovement().scale(6)), land.add(0, 10, 0), land, 22, true, false, true);
        fall.end = () -> {
            settle(SealGeometry.LANDED_TICKS[phase() - 1]);
            impact(land, SealGeometry.LAND_RADIUS);
        };
        legs.add(fall);
    }
    private void impact(Vec3 at, float radius) {
        CameraShakePayload.sendAround(e.level, at, 160, .5F, 20);
        sound(at, SoundEvents.GENERIC_EXPLODE.value(), 5, .45F);
        SetPiecePayload.sendSeeded(e.level, Piece.END_BATTLE_IMPACT, at.add(0, .2, 0), at, radius, null, 0, 0);
        debris(at, 40);
    }
    private void recover() { rest = Math.max(rest, 20); exhausted = false; }

    /** A phase change: everyone near is thrown back while it roars; the knots change their job. */
    void phaseChange() {
        EnderDragon d = dragon(); if (d == null) return;
        Vec3 at = d.position();
        interrupt();
        for (ServerPlayer p : fighters()) if (p.distanceTo(d) < 22) push(p, away(p, at).scale(1.5).add(0, .5, 0));
        Vec3 floor = groundAt(at);
        SetPiecePayload.sendSeeded(e.level, Piece.END_SEAL_WARNING, floor, floor, 22, d, 0, seed(Move.ROAR, 1, 0));
        SetPiecePayload.sendSeeded(e.level, Piece.END_BATTLE_IMPACT, floor.add(0, .2, 0), floor, 12, null, 0, 0);
        SetPiecePayload.send(e.level, Piece.END_SEAL_SHATTER, SealGeometry.chest(d), SealGeometry.chest(d), 1, d);
        sound(at, SoundEvents.ENDER_DRAGON_GROWL, 10, .55F);
        crash = 0; slump = 0; stagger = 0; turn = 0; airChains = 0;
        if (airborne() && legs.isEmpty()) hold(at, SealGeometry.TRANSITION_TICKS);
        // From the second phase the knots sleep until a big move drinks from one.
        shattered = false;
        for (int i = 0; i < 4; i++) {
            if (knots[i] != null && !knots[i].isRemoved()) { knots[i].state(SealView.DORMANT); knots[i].setHealth(knots[i].getMaxHealth()); }
            else { knots[i] = SealKnot.form(this, i, SealView.DORMANT, knotHealth(), knotAt(i)); mend[i] = 0; }
        }
        feeding = -1;
    }

    // =========================================================================== feel and voice

    private void flash(Move move, Vec3 origin, Vec3 target, float scale) {
        SetPiecePayload.sendSeeded(e.level, Piece.END_SEAL_WARNING, origin, target, scale, dragon(), 0, seed(move, 1, 0));
    }
    private void debris(Vec3 at, int count) {
        burst(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.END_STONE.defaultBlockState()), at.x, at.y + .3, at.z, count, 2.2, .3, 2.2, .3);
    }
    /**
     * What each phase does to its body, seen from anywhere on the island: torn space streams off its wings in the second
     * phase; in the third it sheds embers and flame from its wings, head and open chest.
     */
    private void aura(EnderDragon d) {
        int phase = phase(); if (phase < 2) return;
        for (var part : d.getSubEntities()) {
            Vec3 at = part.getBoundingBox().getCenter(); boolean wing = "wing".equals(part.name);
            if (phase == 2) {
                if (wing) { burst(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 8, 1.8, .3, 1.8, .05); burst(ParticleTypes.END_ROD, at.x, at.y, at.z, 2, 1.8, .3, 1.8, .02); }
                else if ("body".equals(part.name)) burst(ParticleTypes.PORTAL, at.x, at.y + 1, at.z, 4, 1.2, .6, 1.2, .3);
            } else {
                burst(EMBER, at.x, at.y, at.z, wing ? 6 : 3, wing ? 1.8 : .7, .4, wing ? 1.8 : .7, 0);
                if (wing || part == d.head) burst(ParticleTypes.FLAME, at.x, at.y - .3, at.z, wing ? 4 : 2, wing ? 1.6 : .4, .2, wing ? 1.6 : .4, .02);
                if ("body".equals(part.name) && e.clock % 9 == 0) burst(ParticleTypes.LAVA, at.x, at.y + .8, at.z, 1, .8, .3, .8, 0);
            }
        }
    }
    private static final DustParticleOptions EMBER = new DustParticleOptions(new Vector3f(1F, .35F, .43F), 3F);
    /** Particles every fighter sees from anywhere on the island (ordinary ones vanish 32 blocks from the camera). */
    private void burst(ParticleOptions particle, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayer viewer : e.party.values())
            if (viewer.level() == e.level && viewer.distanceToSqr(x, y, z) < 140 * 140) e.level.sendParticles(viewer, particle, true, x, y, z, count, dx, dy, dz, speed);
    }
    private void sound(Vec3 at, SoundEvent sound, float volume, float pitch) { e.level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch); }
    /** A line about the fight to everyone in it, once per fight. */
    private void tell(String key) {
        if (!told.add(key)) return;
        for (ServerPlayer p : e.party.values()) SylarVoice.narrate(p, "endgame.heroesevolved.seal." + key);
    }
    private void tell(ServerPlayer p, String key) {
        if (told.add(key + "/" + p.getUUID())) SylarVoice.narrate(p, "endgame.heroesevolved.seal." + key);
    }

    // =========================================================================== the view, tests and cleanup

    SealView view() {
        int flags = (feint ? SealView.FEINT : 0) | (shattered ? SealView.SHATTERED : 0) | (slump > 0 && casting == null ? SealView.SLUMPED : 0)
                | (stagger > 0 ? SealView.STAGGERED : 0) | (airborne() ? SealView.AIRBORNE : 0) | (phase() == 3 ? SealView.STAR : 0) | (falling ? SealView.FALLING : 0);
        List<Integer> states = new ArrayList<>(); List<Float> health = new ArrayList<>(); List<Vec3> at = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            SealKnot k = knots[i];
            boolean live = k != null && !k.isRemoved();
            states.add(live ? k.state() : SealView.NONE); health.add(live ? k.getHealth() / k.getMaxHealth() : 0); at.add(knotAt(i));
        }
        return new SealView(action == null ? -1 : action.ordinal(), e.clock - actionStart, castTotal, flags, crash, stance, feeding, Math.min(1, drag),
                List.copyOf(states), List.copyOf(health), List.copyOf(at));
    }
    /** Seal Rush's target: the nearest fighter a lane can reach. */
    private ServerPlayer rushTarget() {
        ServerPlayer best = null; double most = Double.MAX_VALUE; Vec3 body = dragon().position().multiply(1, 0, 1);
        for (ServerPlayer p : fighters()) { double s = p.position().multiply(1, 0, 1).distanceToSqr(body); if (s < most && lane(p) != null) { most = s; best = p; } }
        return best;
    }
    /**
     * Where a charge at this fighter would stop: past them along a straight lane on solid island that climbs no cliff and
     * runs into no tower. Null when they are too near, too far, up in the air, or the lane can't reach them.
     */
    Vec3 lane(ServerPlayer p) {
        EnderDragon d = dragon(); Vec3 body = d.position(), flat = p.position().subtract(body).multiply(1, 0, 1); double far = flat.length();
        if (far < SealGeometry.RUSH_MIN || far > SealGeometry.RUSH_REACH || p.getY() > ground(p.getX(), p.getZ()) + 3 || Math.abs(p.getY() - body.y) > 8) return null;
        Vec3 heading = flat.normalize(); double want = far + SealGeometry.RUSH_OVER, reached = 0, last = body.y;
        for (double step = 1; step <= want; step++) {
            Vec3 at = body.add(heading.scale(step));
            if (!solid(at.x, at.z)) break;
            double g = ground(at.x, at.z);
            if (Math.abs(g - last) > 2.5 || !EndMoves.seen(e.level, body.add(0, 2, 0), new Vec3(at.x, g + 2, at.z))) break;
            last = g; reached = step;
        }
        if (reached < far) return null;
        Vec3 end = body.add(heading.scale(reached));
        return new Vec3(end.x, ground(end.x, end.z), end.z);
    }
    /** The admin console: the Seal's knots break (all of them in the first phase; the feeding one, a backfire, after). */
    String operatorBreak() {
        if (phase() == 1) {
            if (shattered) return "The Seal is already shattered this phase.";
            for (SealKnot k : knots.clone()) if (k != null && !k.isRemoved() && k.state() == SealView.WARDING) k.take(k.getHealth() + 1, null);
            if (!shattered) shatter(null);
            return "Broke every knot: the Seal shatters and the Dragon crashes.";
        }
        feeding = -1; feedTicks = 0;
        backfire(null);
        return "A feeding knot backfires: the Dragon crashes and takes a tenth of its health.";
    }
    /** Tests and previews: put it down on the ground facing {@code yaw}, ready to fight on foot. */
    public void landAt(Vec3 at, float yaw) {
        cancelAll(); chain.clear(); casting = null; crash = 0; stagger = 0; slump = 0;
        EnderDragon d = dragon(); d.setPos(at); d.setYRot(yaw); d.setYHeadRot(yaw); d.yBodyRot = yaw; d.setDeltaMovement(Vec3.ZERO);
        settle(Math.max(e.grounded, 400));
    }
    /** Tests and previews: up in the air again, hunting. */
    public void liftTo(Vec3 at) {
        cancelAll(); chain.clear(); casting = null; crash = 0; stagger = 0; slump = 0; e.grounded = 0;
        dragon().setPos(at); dragon().setDeltaMovement(Vec3.ZERO);
    }
    void clear() { pools.clear(); rings.clear(); cancelAll(); }
    void cleanup() {
        clear();
        for (int i = 0; i < 4; i++) { if (knots[i] != null) knots[i].discard(); knots[i] = null; }
        for (EndCrystal c : feeders) if (c != null && !c.isRemoved()) c.setBeamTarget(null);
        for (ServerPlayer p : e.party.values()) p.removeEffect(ModItems.RIFTBRAND);
    }
}
