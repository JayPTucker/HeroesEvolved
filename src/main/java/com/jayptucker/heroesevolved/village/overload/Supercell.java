package com.jayptucker.heroesevolved.village.overload;

import com.jayptucker.heroesevolved.flight.FlightService;
import com.jayptucker.heroesevolved.hazard.HazardDamage;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SpeedCyclonePayload;
import com.jayptucker.heroesevolved.phasing.DensityService;
import com.jayptucker.heroesevolved.speed.SpeedKit;
import com.jayptucker.heroesevolved.speed.TornadoWind;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A flyer's overload: the shared tornado ({@code speed/TornadoShape}) come down round them, {@link OverloadGeometry#scale}
 * times a Tempest's size, lifting them into its calm eye and wandering the village with them at a walk. Its wall
 * ({@link TornadoWind#storm}) drags things in, whirls them up and throws them out to fall; the eye is still. A runner
 * faster than {@link OverloadGeometry#OUTRUN} goes through the wall untouched, and a dense Density user can't be lifted.
 * A Tempest or a Cyclone nearby holds it back. The break is Touchdown: it swells to
 * {@link OverloadGeometry#TOUCHDOWN_SCALE} and runs through the village's bell and out the far side.
 */
final class Supercell implements Weather {
    static final class State {
        /** Where it is wandering to; where Touchdown runs from and to. */
        Vec3 waypoint, to;
        float scale = 1;
        final Set<UUID> caught = new HashSet<>();
        /** Players' positions last tick, for telling a runner fast enough to go through the wall. */
        final Map<UUID, Vec3> last = new HashMap<>();
        final Set<UUID> fast = new HashSet<>();
        long warnedHeld = -1000;
    }

    static State state(Overload o) {
        if (!(o.weather instanceof State)) o.weather = new State();
        return (State) o.weather;
    }

    @Override
    public void begin(Overload o) {
        State s = state(o);
        o.villager.setNoGravity(true);
        if (s.waypoint == null) s.waypoint = wander(o, o.eye());
    }

    /** The ground under them: the funnel stands on it. */
    static Vec3 ground(Overload o) {
        Vec3 at = o.eye();
        int y = o.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z));
        return new Vec3(at.x, Math.min(y, at.y), at.z);
    }

    @Override public double reach(Overload o) { return o.stage == Overload.Stage.BURNING ? OverloadGeometry.pull(1) + 6 : OverloadGeometry.pull(o.progress()); }

    @Override
    public Vec3 target(Overload o) {
        State s = state(o);
        if (o.stage == Overload.Stage.BREAKING || o.stage == Overload.Stage.BURNING) return touchdownEnd(o, s);
        return s.waypoint != null ? s.waypoint : o.eye();
    }

    /** Touchdown's far end: through the bell and thirty blocks on. */
    static Vec3 touchdownEnd(Overload o, State s) {
        if (s.to == null) {
            Vec3 bell = Vec3.atBottomCenterOf(o.bell), from = o.eye();
            Vec3 dir = bell.subtract(from).multiply(1, 0, 1);
            if (dir.lengthSqr() < 4) {
                double a = o.level.random.nextDouble() * Math.PI * 2;
                dir = new Vec3(Math.cos(a), 0, Math.sin(a));
            }
            s.to = bell.add(dir.normalize().scale(30));
        }
        return s.to;
    }

    @Override
    public boolean held(Overload o) {
        // A counter-wind: a Tempest or a Cyclone turning near it.
        Vec3 at = ground(o);
        double r = OverloadGeometry.UNWIND_REACH;
        for (ServerPlayer p : o.level.players()) {
            if (p.isSpectator() || p.distanceToSqr(at) > r * r) continue;
            if (FlightService.isCycloneActive(p) || SpeedKit.tornado(p) || SpeedKit.cycloneSpeed(p) > 0) {
                State s = state(o);
                long now = o.level.getGameTime();
                if (now - s.warnedHeld > 60) {
                    s.warnedHeld = now;
                    p.displayClientMessage(Component.literal("Your wind is holding it back.").withStyle(ChatFormatting.AQUA), true);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick(Overload o, long now) {
        State s = state(o);
        Villager v = o.villager;
        float k = Math.min(1, o.stageAge() / (float) OverloadGeometry.BREAK_WARNING);
        s.scale = switch (o.stage) {
            case GROWING -> OverloadGeometry.scale(o.progress());
            case BREAKING -> OverloadGeometry.scale(1) + (OverloadGeometry.TOUCHDOWN_SCALE - OverloadGeometry.scale(1)) * k;
            case BURNING -> OverloadGeometry.TOUCHDOWN_SCALE;
            case DYING -> s.scale;
        };
        move(o, s);
        Vec3 center = ground(o);
        runners(o, s);
        if (o.harms() && now % 5 == 0) tear(o, s, center);
        if (o.harms()) {
            float power = o.power();
            double pull = o.stage == Overload.Stage.BURNING ? OverloadGeometry.pull(1) + 6 : OverloadGeometry.pull(o.progress());
            TornadoWind.storm(o.level, center, o.age, OverloadGeometry.RING, s.scale, pull, 1.5F + 3 * power, OverloadGeometry.EYE,
                    e -> spared(o, s, e), HazardDamage.source(o.level, HazardDamage.OVERLOAD, v), s.caught, now);
        }
        if (now % 2 == 0) SpeedCyclonePayload.send(o.level, v, center, OverloadGeometry.RING * s.scale, o.age, o.age, false, true, SpeedCyclonePayload.STORM);
        if (now % 20 == 0) o.level.playSound(null, center.x, center.y, center.z, SoundEvents.BREEZE_WHIRL, SoundSource.NEUTRAL, 3.0F, 0.45F);
        if (now % 30 == 0) o.level.playSound(null, center.x, center.y + 8, center.z, SoundEvents.ELYTRA_FLYING, SoundSource.NEUTRAL, 2.5F, 0.5F);
        if (now % 120 == 0 && o.level.random.nextInt(3) == 0)
            o.level.playSound(null, center.x, center.y + 30, center.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 4.0F, 0.6F);
        if (o.stage == Overload.Stage.BURNING && now % 5 == 0) CameraShakePayload.sendAround(o.level, center, 48, 0.35F, 6);
    }

    /** Wandering at a walk toward its next stop, or (Touchdown) running on through the bell; they ride its eye. */
    private static void move(Overload o, State s) {
        Villager v = o.villager;
        Vec3 at = v.position();
        double speed;
        Vec3 goal;
        switch (o.stage) {
            case GROWING -> { goal = s.waypoint; speed = OverloadGeometry.WANDER_SPEED * (0.6 + 0.4 * o.power()); }
            case BURNING -> { goal = touchdownEnd(o, s); speed = 0.32; }
            default -> { goal = null; speed = 0; }
        }
        double x = at.x, z = at.z;
        if (goal != null && speed > 0) {
            Vec3 d = goal.subtract(at).multiply(1, 0, 1);
            double len = d.length();
            if (len < 1.5) {
                if (o.stage == Overload.Stage.GROWING) s.waypoint = wander(o, at);
            } else {
                Vec3 step = d.scale(Math.min(speed, len) / len);
                x += step.x; z += step.z;
            }
        }
        BlockPos column = BlockPos.containing(x, at.y, z);
        if (!o.level.hasChunkAt(column)) { x = at.x; z = at.z; }
        int y = o.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        double lift = OverloadGeometry.LIFT * Math.min(1, o.age / 40.0);
        // Ease up and down over roofs rather than snapping.
        double ty = y + lift, ny = at.y + Math.clamp(ty - at.y, -0.25, 0.25);
        v.setPos(x, ny, z);
        v.setDeltaMovement(Vec3.ZERO);
        v.fallDistance = 0;
    }

    /**
     * Where the funnel touches the ground, grass and flowers are torn up (only where {@code mobGriefing} allows); the track
     * it leaves heals over the next days ({@code village/Scars}).
     */
    private static void tear(Overload o, State s, Vec3 center) {
        if (!o.level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)) return;
        int r = Math.max(2, Math.round(OverloadGeometry.wall(0, o.age, s.scale)));
        BlockPos c = BlockPos.containing(center);
        for (int i = 0; i < 6; i++) {
            BlockPos column = c.offset(o.level.random.nextInt(2 * r + 1) - r, 0, o.level.random.nextInt(2 * r + 1) - r);
            if (!o.level.hasChunkAt(column)) continue;
            int y = o.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
            BlockPos plant = new BlockPos(column.getX(), y, column.getZ());
            if (com.jayptucker.heroesevolved.hazard.Scorch.burnable(o.level.getBlockState(plant)))
                o.level.setBlock(plant, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
        com.jayptucker.heroesevolved.village.Scars.track(o.level, "overload:" + o.villager.getUUID(), c, r + 1);
    }

    /** A new stop: somewhere within {@link OverloadGeometry#WANDER} of the bell, at least a dozen blocks off. */
    static Vec3 wander(Overload o, Vec3 from) {
        Vec3 bell = Vec3.atBottomCenterOf(o.bell);
        for (int i = 0; i < 8; i++) {
            double a = o.level.random.nextDouble() * Math.PI * 2, d = 6 + o.level.random.nextDouble() * (OverloadGeometry.WANDER - 6);
            Vec3 p = bell.add(Math.cos(a) * d, 0, Math.sin(a) * d);
            if (p.subtract(from).horizontalDistanceSqr() >= 144) return new Vec3(p.x, from.y, p.z);
        }
        return new Vec3(bell.x, from.y, bell.z);
    }

    /** Which players moved faster than the wall can grab this tick. */
    private static void runners(Overload o, State s) {
        s.fast.clear();
        for (ServerPlayer p : o.level.players()) {
            Vec3 was = s.last.put(p.getUUID(), p.position());
            if (was != null && was.subtract(p.position()).horizontalDistance() > OverloadGeometry.OUTRUN) s.fast.add(p.getUUID());
        }
    }

    /** Not taken: the storm's own (the same power), anyone moving faster than it grabs, anyone too dense to lift. */
    static boolean spared(Overload o, State s, Entity e) {
        return Overloads.spared(o, e) || s.fast.contains(e.getUUID()) || DensityService.dense(e);
    }

    @Override
    public void breaks(Overload o) {
        State s = state(o);
        Vec3 center = ground(o);
        touchdownEnd(o, s);
        o.level.playSound(null, center.x, center.y, center.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.NEUTRAL, 3.0F, 0.4F);
        o.level.playSound(null, center.x, center.y + 20, center.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 5.0F, 0.5F);
        CameraShakePayload.sendAround(o.level, center, 80, 0.8F, 20);
    }

    @Override
    public void end(Overload o) {
        State s = state(o);
        Villager v = o.villager;
        v.setNoGravity(false);
        com.jayptucker.heroesevolved.village.Scars.endTrack("overload:" + v.getUUID());
        SpeedCyclonePayload.send(o.level, v, ground(o), OverloadGeometry.RING * s.scale, o.age, o.age, true, true, SpeedCyclonePayload.STORM);
        s.caught.clear();
    }

    @Override
    public void save(Overload o, CompoundTag tag) {
        State s = state(o);
        if (s.waypoint != null) { tag.putDouble("wayX", s.waypoint.x); tag.putDouble("wayZ", s.waypoint.z); }
        if (s.to != null) { tag.putDouble("toX", s.to.x); tag.putDouble("toZ", s.to.z); }
    }

    @Override
    public void load(Overload o, CompoundTag tag) {
        State s = state(o);
        double y = o.villager.getY();
        if (tag.contains("wayX")) s.waypoint = new Vec3(tag.getDouble("wayX"), y, tag.getDouble("wayZ"));
        if (tag.contains("toX")) s.to = new Vec3(tag.getDouble("toX"), y, tag.getDouble("toZ"));
    }
}
