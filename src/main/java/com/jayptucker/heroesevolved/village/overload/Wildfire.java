package com.jayptucker.heroesevolved.village.overload;

import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A pyrokinetic's overload: a ring of fire spreading out from them ({@link OverloadGeometry#front}). Inside it the air
 * is burning and anyone there catches fire; the flames of the front itself hurt every half second. Embers arc out past
 * it, each landing marked by a ring for {@link OverloadGeometry#EMBER_WARNING} ticks before it bursts into a spot fire.
 * Behind the front the ground is scorched (grass to dirt, plants burnt away), only where {@code mobGriefing} allows. Rain
 * halves its growth and keeps embers from catching. The break is the Firestorm: a wall of fire out to
 * {@link OverloadGeometry#FIRESTORM}.
 */
final class Wildfire implements Weather {
    /** An ember on its way down, and the spot fire it starts. */
    record Ember(Vec3 at, long lands, long until) {}

    static final class State {
        final List<Ember> embers = new ArrayList<>();
        long nextEmber = -1000;
        /** The widest the front has spread, for the burn it leaves. */
        double widest;
        /** Rain slows it: the countdown counts only every other tick while it rains. */
        boolean slowed;
    }

    static State state(Overload o) {
        if (!(o.weather instanceof State)) o.weather = new State();
        return (State) o.weather;
    }

    @Override public void begin(Overload o) { state(o); }

    /** The front's radius now. */
    static double front(Overload o) {
        return o.stage == Overload.Stage.BURNING ? OverloadGeometry.FIRESTORM : OverloadGeometry.front(o.progress());
    }

    @Override public double reach(Overload o) { return front(o); }

    @Override
    public boolean held(Overload o) {
        // In the rain it grows at half the pace: every other tick is held.
        State s = state(o);
        s.slowed = raining(o);
        return s.slowed && o.level.getGameTime() % 2 == 0;
    }

    static boolean raining(Overload o) {
        BlockPos top = o.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, o.villager.blockPosition());
        return o.level.isRainingAt(top);
    }

    @Override
    public void tick(Overload o, long now) {
        if (!o.harms()) return;
        State s = state(o);
        float power = o.power();
        double front = front(o);
        s.widest = Math.max(s.widest, front);
        if (now % 10 == 0) burn(o, front, power);
        if (o.stage == Overload.Stage.GROWING && now >= s.nextEmber) {
            s.nextEmber = now + OverloadGeometry.emberEvery(power);
            if (!s.slowed) {
                int n = 1 + (power > 0.5F ? 1 : 0) + (power > 0.85F ? 1 : 0);
                for (int i = 0; i < n; i++) ember(o, s, front, now);
            }
        }
        s.embers.removeIf(e -> now >= e.until);
        for (Ember e : s.embers) if (now >= e.lands && (now - e.lands) % 10 == 0) spot(o, e, now == e.lands);
        if (griefing(o.level) && now % 2 == 0) scorch(o, front, 6 + (int) (front * 0.8));
        if (now % 16 == 0) {
            Vec3 at = o.eye();
            o.level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_AMBIENT, SoundSource.NEUTRAL, 2.5F, 0.6F);
            o.level.playSound(null, at.x, at.y, at.z, SoundEvents.BLAZE_BURN, SoundSource.NEUTRAL, 1.5F + power, 0.5F);
        }
    }

    /** Inside the front everyone catches fire; in its flames, every half second, they burn. */
    static void burn(Overload o, double front, float power) {
        Vec3 eye = o.eye();
        double edge = OverloadGeometry.FRONT_DEPTH / 2;
        for (LivingEntity e : o.level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(front + edge + 1, 8, front + edge + 1),
                e -> !Overloads.spared(o, e))) {
            double d = Math.sqrt(e.position().subtract(eye).horizontalDistanceSqr());
            if (d > front + edge || e.isInWater()) continue;
            if (d >= front - edge) {
                e.igniteForSeconds(4 + 2 * power);
                e.hurt(o.level.damageSources().inFire(), 1 + 2 * power);
            } else if (e.getRemainingFireTicks() < 40) e.igniteForSeconds(3);
        }
    }

    /** An ember thrown out past the front: its ring glows where it will land, then it bursts into a spot fire. */
    private static void ember(Overload o, State s, double front, long now) {
        ServerLevel level = o.level;
        double a = level.random.nextDouble() * Math.PI * 2, d = front + 2 + level.random.nextDouble() * OverloadGeometry.EMBER_THROW;
        Vec3 eye = o.eye();
        int x = (int) Math.floor(eye.x + Math.cos(a) * d), z = (int) Math.floor(eye.z + Math.sin(a) * d);
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) return;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        Vec3 at = new Vec3(x + 0.5, y, z + 0.5);
        long lands = now + OverloadGeometry.EMBER_WARNING;
        s.embers.add(new Ember(at, lands, lands + OverloadGeometry.EMBER_BURN));
        SetPiecePayload.send(level, Piece.OVERLOAD_EMBER, eye.add(0, 3, 0), at.add(0, 0.05, 0), OverloadGeometry.EMBER_RADIUS, null);
        level.playSound(null, eye.x, eye.y + 3, eye.z, SoundEvents.BLAZE_SHOOT, SoundSource.NEUTRAL, 1.2F, 0.7F);
    }

    /** A landed ember's spot fire: anyone standing in it burns. */
    private static void spot(Overload o, Ember e, boolean landing) {
        double r = OverloadGeometry.EMBER_RADIUS + 0.4;
        for (LivingEntity t : o.level.getEntitiesOfClass(LivingEntity.class, new AABB(e.at, e.at).inflate(r, 2, r),
                t -> !Overloads.spared(o, t) && t.position().subtract(e.at).horizontalDistanceSqr() <= r * r)) {
            if (t.isInWater()) continue;
            t.igniteForSeconds(4);
            t.hurt(o.level.damageSources().inFire(), 1.5F);
        }
        if (landing) {
            o.level.playSound(null, e.at.x, e.at.y, e.at.z, SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 1.2F, 0.8F);
            if (griefing(o.level)) scorchColumn(o.level, BlockPos.containing(e.at.x, e.at.y, e.at.z));
        }
    }

    @Override
    public void breaks(Overload o) {
        Vec3 eye = o.eye();
        float reach = OverloadGeometry.FIRESTORM;
        for (LivingEntity e : o.level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(reach, 10, reach),
                e -> !Overloads.spared(o, e) && e.position().subtract(eye).horizontalDistanceSqr() <= reach * reach)) {
            if (e.isInWater()) continue;
            e.igniteForSeconds(8);
            e.hurt(o.level.damageSources().inFire(), 4);
        }
        SetPiecePayload.send(o.level, Piece.OVERLOAD_FIRESTORM, eye, eye.add(0, 1, 0), reach, null);
        o.level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 3.0F, 0.5F);
        o.level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.BLAZE_SHOOT, SoundSource.NEUTRAL, 3.0F, 0.4F);
        CameraShakePayload.sendAround(o.level, eye, reach * 2, 0.8F, 20);
        if (griefing(o.level)) scorch(o, reach, 900);
    }

    @Override
    public void end(Overload o) {
        State s = state(o);
        s.embers.clear();
        // The burn it leaves heals over the next days, blooming (village/Scars).
        if (griefing(o.level) && s.widest > 6) com.jayptucker.heroesevolved.village.Scars.burn(o.level, o.villager.blockPosition(), (int) s.widest);
        s.widest = 0;
    }

    static boolean griefing(ServerLevel level) { return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING); }

    /** Scorches {@code n} spots just behind the front (or anywhere inside it, for the Firestorm). */
    private static void scorch(Overload o, double front, int n) {
        Vec3 eye = o.eye();
        boolean all = o.stage != Overload.Stage.GROWING;
        for (int i = 0; i < n; i++) {
            double a = o.level.random.nextDouble() * Math.PI * 2;
            double d = all ? Math.sqrt(o.level.random.nextDouble()) * front
                    : Math.max(0, front - OverloadGeometry.FRONT_DEPTH * 1.5 * o.level.random.nextDouble());
            scorchColumn(o.level, BlockPos.containing(eye.x + Math.cos(a) * d, eye.y, eye.z + Math.sin(a) * d));
        }
    }

    /** The ground at this column scorched ({@link com.jayptucker.heroesevolved.hazard.Scorch}, shared with meteors). */
    static void scorchColumn(ServerLevel level, BlockPos column) { com.jayptucker.heroesevolved.hazard.Scorch.column(level, column); }
}
