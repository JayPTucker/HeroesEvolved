package com.jayptucker.heroesevolved.intro;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.speed.SpeedService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Superspeed's awakening. The runner crosses to whoever is going down (themself, or an ally in reach),
 * scoops them up, and runs them out of the fight: up out of any cave by the shortest way to open sky,
 * then far across the surface, away from whatever was attacking.
 *
 * <p>The route is planned on the server and walked by teleporting a few blocks a tick, so it follows
 * twisting tunnels exactly; the blur is drawn with the speedster's own trail.
 */
final class SpeedRescue implements PowerIntroService.Rescue {
    /** Blocks a tick: about 64 metres a second. */
    static final double STEP = 3.2;
    /** How far across the surface the runner carries their ward once out in the open. */
    static final double RUN = 96;
    /** Over water the run keeps going this much further to find land. */
    static final double SHORE = 48;
    /** Cells a cave search may visit before settling for the best it found. */
    static final int SEARCH_BUDGET = 16000;
    static final int SEARCH_RADIUS = 72;
    static final int MAX_TICKS = 20 * 10;
    private static final double[] TURNS = {0, 25, -25, 50, -50, 80, -80, 115, -115};

    private enum Phase { APPROACH, CLIMB, RUN, DONE }

    private final ServerPlayer runner;
    private final LivingEntity ward;
    private final ServerLevel level;
    private final Map<Long, Boolean> passable = new HashMap<>();
    private Phase phase;
    private List<Vec3> route = List.of();
    private int index;
    private Vec3 heading;
    private double run;
    private int age;
    private Vec3 trail;

    SpeedRescue(ServerPlayer runner, LivingEntity ward) {
        this.runner = runner;
        this.ward = ward;
        this.level = runner.serverLevel();
        this.trail = runner.position();
        // Everything still fighting is suddenly moving through syrup: the runner's heartbeat has outpaced it.
        for (LivingEntity threat : Danger.threats(runner, ward, 12)) {
            threat.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 3));
        }
        ScreenTintPayload.send(runner, 0x40FFBE45, 30, ScreenTintPayload.Style.VIGNETTE);
        level.playSound(null, runner.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.2F, 1.4F);
        if (rescuing()) {
            phase = Phase.APPROACH;
            route = approach();
        } else {
            escape();
        }
    }

    private boolean rescuing() { return ward != runner; }

    private boolean wardHere() {
        return ward.isAlive() && !ward.isRemoved() && ward.level() == level;
    }

    @Override
    public boolean tick() {
        if (phase == Phase.DONE) return true;
        if (++age > MAX_TICKS || !runner.isAlive() || runner.level() != level) {
            finish();
            return true;
        }
        if (rescuing() && !wardHere()) {
            // Nobody left to carry: the runner gets themself clear instead.
            if (phase == Phase.APPROACH) escape();
        }
        runner.resetFallDistance();
        switch (phase) {
            case APPROACH -> {
                if (follow(STEP)) pickUp();
            }
            case CLIMB -> {
                if (follow(STEP)) {
                    if (Danger.openSky(runner)) startRun();
                    else finish();
                }
            }
            case RUN -> runStep();
            default -> { }
        }
        carryWard();
        return phase == Phase.DONE;
    }

    @Override
    public void stop() {
        if (phase != Phase.DONE) finish();
    }

    // ------------------------------------------------------------------
    // Phases
    // ------------------------------------------------------------------

    private List<Vec3> approach() {
        BlockPos target = ward.blockPosition();
        // Pull up a stride short, beside them rather than inside them.
        Vec3 across = ward.position().subtract(runner.position()).multiply(1, 0, 1);
        Vec3 beside = across.lengthSqr() > 1 ? ward.position().subtract(across.normalize()) : ward.position();
        Vec3 direct = SpeedService.sweptFrom(runner, runner.position(), beside.subtract(runner.position()),
                point -> true, true);
        if (direct.distanceToSqr(ward.position()) <= 2.25) return List.of(direct);
        List<Vec3> path = search(runner.blockPosition(), cell -> cell.distManhattan(target) <= 2
                && Math.abs(cell.getY() - target.getY()) <= 1, null);
        if (path != null) return path;
        // No way through that a body fits: a speedster still finds one, in a blink.
        return SpeedService.safe(runner, ward.position()) ? List.of(ward.position()) : List.of();
    }

    private void pickUp() {
        if (!wardHere()) {
            escape();
            return;
        }
        EntityCarryService.pickUp(runner, ward, EntityCarryService.ORDINARY_REACH);
        AbilityEffects.send(level, Kind.SPEED_BURST, ward.position(), ward.getEyePosition(), 1.6);
        level.playSound(null, ward.blockPosition(), SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 1, 1.6F);
        ArmPosePayload.show(runner, ArmPose.TWO_HANDS, 60);
        if (ward instanceof ServerPlayer saved) {
            ScreenTintPayload.send(saved, 0x40FFBE45, 30, ScreenTintPayload.Style.VIGNETTE);
        }
        escape();
    }

    /** Out of the open, the shortest way to the sky; in the open, straight into the run. */
    private void escape() {
        if (Danger.openSky(runner)) {
            startRun();
            return;
        }
        List<LivingEntity> threats = Danger.threats(runner, ward, 16);
        Vec3 origin = runner.position();
        List<Vec3> climb = search(runner.blockPosition(), this::open, cell -> {
            // Sealed in: settle for the furthest reachable point from whatever was attacking.
            Vec3 at = Vec3.atBottomCenterOf(cell);
            double score = at.distanceTo(origin);
            for (LivingEntity threat : threats) score += Math.min(24, at.distanceTo(threat.position())) * 0.5;
            return score;
        });
        phase = Phase.CLIMB;
        index = 0;
        route = climb == null ? List.of() : climb;
    }

    private void startRun() {
        phase = Phase.RUN;
        run = 0;
        Vec3 look = runner.getLookAngle();
        heading = Danger.away(runner.position(), Danger.threats(runner, ward, 24), look);
        AbilityEffects.send(level, Kind.SPEED_BURST, runner.position(), runner.position().add(heading), 1.4);
    }

    private void runStep() {
        Vec3 from = runner.position();
        Vec3 next = surfaceStep(from, STEP);
        if (next == null) {
            finish();
            return;
        }
        run += next.subtract(from).horizontalDistance();
        move(next);
        boolean wet = level.getFluidState(BlockPos.containing(next).below()).is(FluidTags.WATER)
                || level.getFluidState(BlockPos.containing(next)).is(FluidTags.WATER);
        if (run >= RUN + SHORE || run >= RUN && !wet) finish();
    }

    private void finish() {
        if (phase == Phase.DONE) return;
        phase = Phase.DONE;
        ArmPosePayload.clear(runner);
        if (EntityCarryService.isCarrying(runner)) {
            Vec3 side = runner.position().add(heading == null ? Vec3.ZERO
                    : new Vec3(-heading.z, 0, heading.x).scale(1.1));
            if (!EntityCarryService.setDown(runner, side)) EntityCarryService.release(runner);
        }
        if (rescuing() && wardHere()) {
            ward.resetFallDistance();
            ward.setDeltaMovement(Vec3.ZERO);
            if (ward instanceof ServerPlayer saved) {
                saved.displayClientMessage(Component.translatable("intro.heroesevolved.superspeed.carried",
                        runner.getDisplayName()), true);
            }
        }
        runner.resetFallDistance();
        AbilityEffects.send(level, Kind.SPEED_BURST, runner.position(), runner.getEyePosition(), 1.2);
        level.playSound(null, runner.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.9F, 0.8F);
        runner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 2));
        runner.sendSystemMessage(Component.translatable("speed.heroesevolved.awakened"));
    }

    // ------------------------------------------------------------------
    // Moving
    // ------------------------------------------------------------------

    /** Walks the planned route up to {@code budget} blocks. True once its end is reached. */
    private boolean follow(double budget) {
        Vec3 at = runner.position();
        while (budget > 1.0E-3 && index < route.size()) {
            Vec3 next = route.get(index);
            double distance = at.distanceTo(next);
            if (distance <= budget) {
                at = next;
                budget -= distance;
                index++;
            } else {
                at = at.add(next.subtract(at).scale(budget / distance));
                budget = 0;
            }
        }
        if (!at.equals(runner.position())) move(at);
        return index >= route.size();
    }

    private void move(Vec3 to) {
        Vec3 from = runner.position();
        Vec3 travel = to.subtract(from);
        float yaw = travel.horizontalDistanceSqr() > 1.0E-4
                ? (float) (Mth.atan2(travel.z, travel.x) * Mth.RAD_TO_DEG) - 90 : runner.getYRot();
        teleport(runner, to, yaw);
        if (age % 2 == 0 || trail.distanceToSqr(to) > 36) {
            AbilityEffects.send(level, Kind.SPEED_TRAIL, trail, to, 1.6);
            trail = to;
        }
        if (age % 5 == 0) {
            level.playSound(null, BlockPos.containing(to), SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 0.5F, 1.8F);
        }
        towTarget = from;
    }

    private Vec3 towTarget;

    /** Carried in the runner's arms; if the carry can't hold (a low tunnel, a big ally), towed right behind. */
    private void carryWard() {
        if (!rescuing() || !wardHere() || phase == Phase.APPROACH || phase == Phase.DONE) return;
        ward.resetFallDistance();
        if (EntityCarryService.isBeingCarried(ward)) return;
        Vec3 spot = towTarget != null && SpeedService.safe(ward, towTarget) ? towTarget : runner.position();
        if (ward.position().distanceToSqr(spot) > 1.0E-4) teleport(ward, spot, runner.getYRot());
    }

    private static void teleport(Entity entity, Vec3 to, float yaw) {
        if (entity instanceof ServerPlayer player) {
            player.connection.teleport(to.x, to.y, to.z, yaw, player.getXRot());
            // A test's fake connection ignores teleports; move the body directly too.
            if (player.position().distanceToSqr(to) > 1.0E-6) player.absMoveTo(to.x, to.y, to.z, yaw, player.getXRot());
        } else {
            entity.teleportTo(to.x, to.y, to.z);
            entity.setYRot(yaw);
        }
        entity.setDeltaMovement(Vec3.ZERO);
        entity.resetFallDistance();
    }

    /**
     * One stride across the surface, turning off the heading round lava, cliffs and walls. Follows the
     * ground's height, runs over water, and stops at the edge of loaded ground.
     */
    private Vec3 surfaceStep(Vec3 from, double step) {
        for (double turn : TURNS) {
            Vec3 direction = heading.yRot((float) Math.toRadians(turn));
            Vec3 to = from.add(direction.scale(step));
            BlockPos column = BlockPos.containing(to);
            if (!level.hasChunkAt(column)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
            if (Math.abs(y - from.y) > 6) continue;
            Vec3 point = new Vec3(to.x, y, to.z);
            BlockPos below = BlockPos.containing(point).below();
            BlockState ground = level.getBlockState(below);
            if (level.getFluidState(below).is(FluidTags.LAVA) || ground.is(BlockTags.FIRE)
                    || ground.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                    || ground.is(BlockTags.CAMPFIRES) || !SpeedService.safe(runner, point)) continue;
            if (turn != 0) heading = heading.add(direction.scale(2)).normalize();
            return point;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Route search
    // ------------------------------------------------------------------

    /** A cell under open sky that a runner can stand in. */
    private boolean open(BlockPos cell) {
        return cell.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cell.getX(), cell.getZ())
                && standable(cell);
    }

    /**
     * Breadth-first over every cell a runner's body fits in and can push off from (a floor, or a wall to
     * run up). Returns the route to the first goal cell; failing that, to the best-scored cell it reached
     * ({@code fallback}), or null.
     */
    private List<Vec3> search(BlockPos start, Predicate<BlockPos> goal, ToDoubleFunction<BlockPos> fallback) {
        Map<Long, Long> parent = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        parent.put(start.asLong(), Long.MIN_VALUE);
        queue.add(start);
        BlockPos best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        int visited = 0;
        while (!queue.isEmpty() && visited++ < SEARCH_BUDGET) {
            BlockPos cell = queue.poll();
            if (cell != start && goal.test(cell)) return route(parent, cell);
            if (fallback != null && cell != start) {
                double score = fallback.applyAsDouble(cell);
                if (score > bestScore) {
                    bestScore = score;
                    best = cell;
                }
            }
            for (BlockPos next : neighbours(cell)) {
                if (Math.abs(next.getX() - start.getX()) > SEARCH_RADIUS || Math.abs(next.getZ() - start.getZ()) > SEARCH_RADIUS
                        || Math.abs(next.getY() - start.getY()) > SEARCH_RADIUS
                        || parent.containsKey(next.asLong()) || !standable(next)) continue;
                parent.put(next.asLong(), cell.asLong());
                queue.add(next);
            }
        }
        return best == null ? null : route(parent, best);
    }

    private static List<Vec3> route(Map<Long, Long> parent, BlockPos end) {
        List<Vec3> points = new ArrayList<>();
        long at = end.asLong();
        while (true) {
            long previous = parent.get(at);
            if (previous == Long.MIN_VALUE) break;
            points.add(Vec3.atBottomCenterOf(BlockPos.of(at)));
            at = previous;
        }
        Collections.reverse(points);
        return points;
    }

    private static List<BlockPos> neighbours(BlockPos cell) {
        List<BlockPos> list = new ArrayList<>(14);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos flat = cell.relative(side);
            list.add(flat);
            list.add(flat.above());
            list.add(flat.below());
        }
        list.add(cell.above());
        list.add(cell.below());
        return list;
    }

    /** The body fits here, out of lava and fire, touching something to run on: a floor, or a wall. */
    private boolean standable(BlockPos cell) {
        if (!fits(cell)) return false;
        if (solid(cell.below()) || level.getFluidState(cell.below()).is(FluidTags.WATER)) return true;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (solid(cell.relative(side)) || solid(cell.relative(side).above())) return true;
        }
        return false;
    }

    private boolean fits(BlockPos cell) {
        return passable.computeIfAbsent(cell.asLong(), key -> level.hasChunkAt(cell)
                && SpeedService.safe(runner, Vec3.atBottomCenterOf(cell)));
    }

    private boolean solid(BlockPos pos) {
        return level.hasChunkAt(pos) && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
