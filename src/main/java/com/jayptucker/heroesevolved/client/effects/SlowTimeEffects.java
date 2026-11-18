package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.ClientProjectileTime;
import com.jayptucker.heroesevolved.client.ClientTimeFlow;
import com.jayptucker.heroesevolved.network.TimeFlowPayload;
import com.jayptucker.heroesevolved.time.TimeFlow;
import com.jayptucker.heroesevolved.time.ProjectileTimeService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What slowed and stopped time look like round the people in it. One idea runs through all of it: <b>bent time makes the
 * air as thick as water</b>, and everything fast leaves a mark in it.
 * <ul>
 *   <li>Chips of the ground lift and hang round anyone standing in it (and burst up round whoever bent it).</li>
 *   <li>Anyone running fast kicks up the ground under their feet, and the chips hang in the air behind them.</li>
 *   <li>Whoever moves faster than the world is a blur: afterimages left more often the faster they go, gold for a
 *       speedster, amber for a time traveller.</li>
 *   <li>A speedster wades: rings ripple out from each footfall, and the air presses ahead of them like a bow wave.</li>
 *   <li>A slowed shot leaves a corkscrew of thin rings in the air behind it.</li>
 * </ul>
 * The rings are drawn by {@code setpiece/SlowTimeArt}; the chips are particles ({@link TimeMote}) the world's clock slows.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class SlowTimeEffects {
    /** Faster than a sprint (0.28 a tick): fast enough to kick up the ground. */
    public static final double KICK = 0.42;
    /** A speedster wading through thick air, from about a sprint. */
    public static final double WADE = 0.3;
    /** Blocks between a wading speedster's footfalls, and between the rings pushed off their body. */
    private static final double STRIDE = 1.1, BODY_RING = 1.8;
    /**
     * Blocks between afterimages: {@code BLUR_SPACING + speed * BLUR_STRETCH}, so a mover leaves one every so far and the
     * faster they go the more of them there are, easing toward two a tick at a speedster's pace (any more stacks into a
     * solid tube rather than a blur).
     */
    private static final double BLUR_SPACING = 0.5, BLUR_STRETCH = 0.5;
    /** The blur's tint over the skin: the speedster's gold, the time traveller's slowed amber. */
    public static final int SPEED_TINT = 0xFFE9C0, TIME_TINT = 0xFFCF8C;
    private static final int SPEED_LIFE = 7, TIME_LIFE = 10, MAX_RINGS = 640;
    /** A blur's afterimages are fainter than a running speedster's own, since there are more of them. */
    private static final float BLUR_STRENGTH = 0.7F;
    /** A shot's wake: blocks between rings (plus its width), and the turn of the corkscrew from one ring to the next. */
    private static final double WAKE_SPACING = 0.32, WAKE_TWIST = 0.9;

    /** A ring left in the thick air: by a shot, a footfall or a runner's body. */
    public static final class Ring {
        public static final int SHOT = 0, STEP = 1, BODY = 2;
        public final Vec3 at, axis;
        public final float radius, grow, life;
        public final int kind, seed;
        public float age, step = 1;

        Ring(int kind, Vec3 at, Vec3 axis, float radius, float grow, float life, int seed) {
            this.kind = kind; this.at = at; this.axis = axis; this.radius = radius; this.grow = grow; this.life = life; this.seed = seed;
        }
    }

    /** One player as this client follows them through bent time. */
    public static final class Runner {
        public final AbstractClientPlayer player;
        Vec3 last;
        public Vec3 velocity = Vec3.ZERO;
        public int style;
        public boolean blurring, wading;
        double carried, stride, body;
        boolean foot;
        long seen;

        Runner(AbstractClientPlayer player) { this.player = player; this.last = player.position(); }
    }

    private static final class Shot { Vec3 last; double carry; int rings; long seen; Shot(Vec3 at) { last = at; } }

    private static final List<TimeMote> MOTES = new ArrayList<>();
    private static final Map<Integer, Runner> RUNNERS = new HashMap<>();
    private static final Map<Integer, Shot> SHOTS = new HashMap<>();
    private static final ArrayDeque<Ring> RINGS = new ArrayDeque<>();
    private static final Set<Integer> GREETED = new HashSet<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static ClientLevel world;
    private static boolean bent;

    private SlowTimeEffects() {}

    /** True while {@code player} is being drawn as a blur (their ordinary running afterimages give way to it). */
    public static boolean blurring(Player player) {
        Runner runner = RUNNERS.get(player.getId());
        return runner != null && runner.blurring;
    }

    public static Collection<Ring> rings() { return RINGS; }
    public static Collection<Runner> runners() { return RUNNERS.values(); }
    public static boolean live() { return !RINGS.isEmpty() || bent && !RUNNERS.isEmpty(); }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (world != mc.level) { world = mc.level; reset(); }
        if (world == null || mc.player == null || mc.isPaused()) return;
        long now = world.getGameTime();
        MOTES.removeIf(mote -> !mote.isAlive());
        boolean active = ClientTimeFlow.active();
        if (!active) {
            // Time runs again: what hung in the air falls, and the rings fade at the world's pace.
            if (bent) MOTES.forEach(TimeMote::settle);
            bent = false;
            RUNNERS.clear(); SHOTS.clear(); GREETED.clear();
            ageRings();
            return;
        }
        if (bent) {
            // A field ended but another remains: anything now in running time settles.
            for (TimeMote mote : MOTES) if (ClientTimeFlow.rate(mote.getPos()) >= 1) mote.settle();
        }
        bent = true;
        int detail = switch (mc.options.particles().get()) { case ALL -> 2; case DECREASED -> 1; default -> 0; };
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();

        greet(detail);
        dust(camera, detail);
        for (AbstractClientPlayer player : world.players()) {
            if (player.distanceToSqr(camera) > 64 * 64) continue;
            Runner runner = RUNNERS.computeIfAbsent(player.getId(), id -> new Runner(player));
            runner.seen = now;
            follow(runner, detail, now);
        }
        RUNNERS.values().removeIf(runner -> runner.seen != now || runner.player.isRemoved());
        shots(now);
        ageRings();
    }

    // ------------------------------------------------------------------
    // The ground lifts
    // ------------------------------------------------------------------

    /** As time bends, the ground under whoever bent it lifts round their feet, once per field. */
    private static void greet(int detail) {
        Set<Integer> owners = new HashSet<>();
        for (TimeFlowPayload.Field field : ClientTimeFlow.fields()) {
            owners.add(field.owner());
            if (GREETED.contains(field.owner()) || !(world.getEntity(field.owner()) instanceof Entity owner)) continue;
            GREETED.add(field.owner());
            if (owner.distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()) > 48 * 48) continue;
            int count = detail == 2 ? 36 : detail == 1 ? 18 : 8;
            for (int i = 0; i < count; i++) {
                double angle = RANDOM.nextDouble() * Mth.TWO_PI, reach = 0.4 + RANDOM.nextDouble() * 2.6;
                Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                BlockPos ground = ground(Mth.floor(owner.getX() + out.x * reach), Mth.floor(owner.getY()), Mth.floor(owner.getZ() + out.z * reach));
                if (ground == null) continue;
                Vec3 at = top(ground, owner.getX() + out.x * reach, owner.getZ() + out.z * reach);
                float rate = ClientTimeFlow.rate(at);
                if (rate <= 0) at = at.add(0, 0.1 + RANDOM.nextDouble() * 1.3, 0);
                Vec3 lift = out.scale(0.02 + RANDOM.nextDouble() * 0.04).add(0, 0.12 + RANDOM.nextDouble() * 0.22, 0);
                spawn(at, lift, ground, 40 + RANDOM.nextInt(30), 0.45F, 0.8F + RANDOM.nextFloat() * 0.6F, false);
            }
        }
        GREETED.retainAll(owners);
    }

    /** Fine chips of the ground rise and hang round the camera while time is bent: a little dust caught between moments. */
    private static void dust(Vec3 camera, int detail) {
        float rate = ClientTimeFlow.rate(camera);
        if (rate >= 1) return;
        int target = detail == 2 ? 70 : detail == 1 ? 34 : 10, ambient = 0;
        for (TimeMote mote : MOTES) if (mote.ambient()) ambient++;
        // Stopped, nothing dies: fill the air quickly once and hold it there.
        int spawn = Math.min(target - ambient, rate <= 0 ? 8 : 3);
        for (int i = 0; i < spawn; i++) {
            double angle = RANDOM.nextDouble() * Mth.TWO_PI, reach = 1.5 + 10 * Math.pow(RANDOM.nextDouble(), 1.4);
            double x = camera.x + Math.cos(angle) * reach, z = camera.z + Math.sin(angle) * reach;
            BlockPos ground = ground(Mth.floor(x), Mth.floor(camera.y - 1.6), Mth.floor(z));
            if (ground == null) continue;
            Vec3 at = top(ground, x, z).add(0, RANDOM.nextDouble() * 0.15, 0);
            if (ClientTimeFlow.rate(at) >= 1) continue;
            if (rate <= 0) at = at.add(0, RANDOM.nextDouble() * 1.8, 0);
            Vec3 drift = new Vec3((RANDOM.nextDouble() - 0.5) * 0.012, 0.012 + RANDOM.nextDouble() * 0.03, (RANDOM.nextDouble() - 0.5) * 0.012);
            spawn(at, drift, ground, 70 + RANDOM.nextInt(60), 0, 0.5F + RANDOM.nextFloat() * 0.5F, true);
        }
    }

    // ------------------------------------------------------------------
    // People moving through it
    // ------------------------------------------------------------------

    private static void follow(Runner runner, int detail, long now) {
        AbstractClientPlayer player = runner.player;
        Vec3 position = player.position(), step = position.subtract(runner.last);
        runner.last = position;
        runner.style = ClientTimeFlow.style(player);
        if (step.lengthSqr() > 64 || player.isInvisible() || player.isSpectator() || !player.isAlive() || player.isPassenger()
                || TimeVeilClient.hidden(player)) {
            runner.velocity = Vec3.ZERO;
            runner.blurring = runner.wading = false;
            return;
        }
        runner.velocity = runner.velocity.scale(0.5).add(step.scale(0.5));
        float rate = ClientTimeFlow.rate(position);
        double speed = step.horizontalDistance();
        runner.blurring = runner.style != TimeFlow.NONE && rate < 1;
        runner.wading = runner.style == TimeFlow.SPEED && rate < 1 && player.onGround() && speed > WADE;
        if (runner.blurring) blur(runner, step);
        if (rate < 1 && player.onGround() && speed > KICK) kick(runner, step, speed, rate, detail, now);
        if (runner.wading) wade(runner, step, speed);
    }

    /** Afterimages every so far along the way: the faster they go, the more of them, until it reads as a blur. */
    private static void blur(Runner runner, Vec3 step) {
        double distance = step.length();
        if (distance < 0.04) return;
        boolean speedster = runner.style == TimeFlow.SPEED;
        double spacing = BLUR_SPACING + distance * BLUR_STRETCH;
        runner.carried += distance;
        int left = 0;
        while (runner.carried >= spacing && left < 3) {
            runner.carried -= spacing;
            Vec3 at = runner.player.position().subtract(step.scale(Math.min(1, runner.carried / distance)));
            SpeedAfterimages.blur(runner.player, at, runner.player.yBodyRot, speedster ? SPEED_TINT : TIME_TINT,
                    speedster ? SPEED_LIFE : TIME_LIFE, BLUR_STRENGTH);
            left++;
        }
        if (left == 3) runner.carried = 0;
    }

    /** The ground under fast feet sprays up behind them, and hangs there (stopped, it holds where it was thrown). */
    private static void kick(Runner runner, Vec3 step, double speed, float rate, int detail, long now) {
        AbstractClientPlayer player = runner.player;
        BlockPos under = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
        BlockState state = world.getBlockState(under);
        if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) return;
        int count = detail == 0 ? (now % 2 == 0 ? 1 : 0) : (int) Math.min(detail == 2 ? 5 : 2, 1 + speed * 2);
        Vec3 dir = new Vec3(step.x, 0, step.z).normalize(), side = new Vec3(-dir.z, 0, dir.x);
        for (int i = 0; i < count; i++) {
            // Spread along the whole of this tick's stride, so a fast runner's spray is a trail, not a clump.
            Vec3 at = top(under, player.getX(), player.getZ())
                    .subtract(dir.scale(0.1 + RANDOM.nextDouble() * Math.max(0.5, speed))).add(side.scale((RANDOM.nextDouble() - 0.5) * 0.7));
            if (rate <= 0) at = at.add(0, 0.05 + RANDOM.nextDouble() * 0.8, 0);
            Vec3 throwing = dir.scale(-(0.05 + RANDOM.nextDouble() * 0.12) * Math.min(2, speed))
                    .add(side.scale((RANDOM.nextDouble() - 0.5) * 0.14)).add(0, 0.16 + RANDOM.nextDouble() * 0.26, 0);
            spawn(at, throwing, under, 26 + RANDOM.nextInt(18), 0.85F, 0.8F + RANDOM.nextFloat() * 0.6F, false);
        }
    }

    /** A speedster wades: a ripple from each footfall, and now and then a ring of air pushed off the body. */
    private static void wade(Runner runner, Vec3 step, double speed) {
        AbstractClientPlayer player = runner.player;
        Vec3 dir = new Vec3(step.x, 0, step.z).normalize(), side = new Vec3(-dir.z, 0, dir.x);
        runner.stride += speed;
        if (runner.stride >= STRIDE) {
            runner.stride = Math.min(runner.stride - STRIDE, STRIDE);
            runner.foot = !runner.foot;
            BlockPos under = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
            Vec3 foot = top(under, player.getX(), player.getZ()).add(side.scale(runner.foot ? 0.2 : -0.2)).add(0, 0.04, 0);
            add(new Ring(Ring.STEP, foot, new Vec3(0, 1, 0), 0.14F, 6.5F, 26, player.getId() * 31 + (int) player.tickCount));
        }
        runner.body += speed;
        if (runner.body >= BODY_RING) {
            runner.body = Math.min(runner.body - BODY_RING, BODY_RING);
            add(new Ring(Ring.BODY, player.position().add(0, 0.95, 0).subtract(dir.scale(0.35)), dir, 0.5F, 0.6F, 16, player.tickCount));
        }
    }

    // ------------------------------------------------------------------
    // Shots
    // ------------------------------------------------------------------

    /** A slowed shot leaves a corkscrew of thin rings behind it, each fixed where it was made. */
    private static void shots(long now) {
        ClientProjectileTime.forEachShot(shot -> {
            Shot s = SHOTS.computeIfAbsent(shot.getId(), id -> new Shot(shot.position()));
            s.seen = now;
            // A round first seen already stopped never moved on this client, so it has no wake to preserve.
            // Seed the same corkscrew behind its saved heading, already open: its clock cannot fade it in.
            if (ClientProjectileTime.stopped(shot) && s.rings == 0 && ProjectileTimeService.isTaczBullet(shot)) {
                Vec3 dir = ClientProjectileTime.direction(shot), u = perpendicular(dir), v = dir.cross(u);
                int count = Minecraft.getInstance().options.particles().get() == net.minecraft.client.ParticleStatus.MINIMAL ? 3 : 7;
                for (int i = 1; i <= count; i++) {
                    double phase = i * WAKE_TWIST;
                    Vec3 at = shot.position().subtract(dir.scale(i * .38))
                            .add(u.scale(Math.cos(phase) * .025)).add(v.scale(Math.sin(phase) * .025));
                    Ring ring = new Ring(Ring.SHOT, at, dir, .05F, 1.2F, 15, shot.getId() * 131 + i);
                    ring.age = 3 + i * .65F;
                    ring.step = 0;
                    add(ring);
                }
                s.rings = count;
            }
            Vec3 position = shot.position(), step = position.subtract(s.last);
            s.last = position;
            double distance = step.length();
            if (distance < 1e-3 || distance > 8) return;
            Vec3 dir = step.scale(1 / distance);
            float size = Mth.clamp(shot.getBbWidth(), 0.1F, 0.5F), radius = 0.04F + size * 0.1F;
            double spacing = WAKE_SPACING + size * 0.5;
            Vec3 u = perpendicular(dir), v = dir.cross(u);
            s.carry += distance;
            int made = 0;
            while (s.carry >= spacing && made < 6) {
                s.carry -= spacing;
                // Each ring sits a little round from the last, so the wake winds like a loose spring.
                double phase = ++s.rings * WAKE_TWIST, wobble = radius * 0.45;
                Vec3 at = position.subtract(dir.scale(s.carry))
                        .add(u.scale(Math.cos(phase) * wobble)).add(v.scale(Math.sin(phase) * wobble));
                add(new Ring(Ring.SHOT, at, dir, radius, 1.2F, 15, shot.getId() * 131 + s.rings));
                made++;
            }
            if (made == 6) s.carry = 0;
        });
        SHOTS.values().removeIf(s -> s.seen != now);
    }

    /**
     * Ripples age at the pace of the time they hang in (a little faster than slowed time, so a trail stays a trail); a
     * shot's rings open and fade at the pace of whoever watches it go, so its wake stays a few blocks long. Stopped, none
     * of them age.
     */
    private static void ageRings() {
        RINGS.removeIf(ring -> {
            float rate = ClientTimeFlow.rate(ring.at);
            ring.step = rate <= 0 ? 0 : ring.kind == Ring.SHOT ? 1 : Math.min(1, 0.3F + rate);
            ring.age += ring.step;
            return ring.age >= ring.life;
        });
    }

    private static void add(Ring ring) {
        while (RINGS.size() >= MAX_RINGS) RINGS.removeFirst();
        RINGS.addLast(ring);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void spawn(Vec3 at, Vec3 velocity, BlockPos from, int life, float gravity, float scale, boolean ambient) {
        BlockState state = world.getBlockState(from);
        if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) return;
        TimeMote mote = new TimeMote(world, at, velocity, state, from, life, gravity, scale, ambient);
        Minecraft.getInstance().particleEngine.add(mote);
        MOTES.add(mote);
    }

    /** The highest standable block in a column near {@code y}, or null. */
    private static BlockPos ground(int x, int y, int z) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, y + 3, z);
        for (int i = 0; i < 10; i++, at.move(Direction.DOWN)) {
            BlockState state = world.getBlockState(at);
            if (state.getCollisionShape(world, at).isEmpty() || state.getRenderShape() == RenderShape.INVISIBLE) continue;
            BlockPos above = at.above();
            if (world.getBlockState(above).getCollisionShape(world, above).isEmpty() && world.getFluidState(above).isEmpty()) return at.immutable();
            return null;
        }
        return null;
    }

    /** A point on the top of {@code ground}'s shape. */
    private static Vec3 top(BlockPos ground, double x, double z) {
        var shape = world.getBlockState(ground).getCollisionShape(world, ground);
        double height = shape.isEmpty() ? 1 : shape.max(Direction.Axis.Y);
        return new Vec3(x, ground.getY() + height + 0.02, z);
    }

    private static Vec3 perpendicular(Vec3 axis) {
        Vec3 u = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0).cross(axis) : new Vec3(0, 1, 0).cross(axis);
        return u.normalize();
    }

    private static void reset() {
        MOTES.clear(); RUNNERS.clear(); SHOTS.clear(); RINGS.clear(); GREETED.clear(); bent = false;
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); world = null; }
}
