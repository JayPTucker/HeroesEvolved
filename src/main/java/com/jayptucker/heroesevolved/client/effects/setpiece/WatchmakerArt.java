package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.story.sylar.WatchmakerMoves;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.UP;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.fade;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.hash;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.rgb;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.smooth;
import static net.minecraft.util.Mth.PI;
import static net.minecraft.util.Mth.TWO_PI;
import static net.minecraft.util.Mth.cos;
import static net.minecraft.util.Mth.sin;

/**
 * Sylar, the Watchmaker, in his shop. One idea: <b>everything he does is a clock running down</b>. Every telegraph is a
 * brass dial whose hand sweeps round and lands when it reaches twelve; the power he stole shows in the dial's fill
 * (lavender for telekinesis, frost for ice, blue-white for lightning, violet for stopped time, hunger red for his grab).
 * His own pieces (the watch on his wrist, the clocks of his shop, the pull into it) are brass and watch-glass.
 *
 * <p>Palette: {@code C9A24A} brass, {@code 7A5A22} deep brass halo, {@code FFE7A8} gilt core, {@code CFE3E8} watch-glass,
 * {@code B3262E} hunger, {@code 2E2218} soot and cracks (normal blending). Stolen: {@code B9A6FF} telekinesis,
 * {@code A6D3E5} frost, {@code 9FD8FF} lightning, {@code 9A7CFF}/{@code C9C2FF} stopped time.</p>
 *
 * <p>Floor dials use the server's angle convention (twelve o'clock toward -z, hours clockwise seen from above), so the
 * gap a player sees in the clock face is exactly the one {@link WatchmakerMoves#inGap} spares.</p>
 */
final class WatchmakerArt {
    static final float[] BRASS = rgb(0xC9A24A), DEEP = rgb(0x7A5A22), GILT = rgb(0xFFE7A8), GLASS = rgb(0xCFE3E8),
            HUNGER = rgb(0xB3262E), SOOT = rgb(0x2E2218), TK = rgb(0xB9A6FF), FROST = rgb(0xA6D3E5), SPARK = rgb(0x9FD8FF),
            WHITE = rgb(0xF4FAFF), STOPPED = rgb(0x9A7CFF), STOPPED_DEEP = rgb(0x5B3FD1), STOPPED_PALE = rgb(0xC9C2FF),
            PLANK = rgb(0x4A3220), FACE = rgb(0xEDE6D6);
    private static final Vec3 SOUTH = new Vec3(0, 0, 1);

    private WatchmakerArt() {}

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }
    private static boolean detail() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.ALL; }
    private static float clock(Canvas c, float t) { return c.level != null ? (c.level.getGameTime() % 24000) + c.partial : t; }

    static void draw(Canvas c, Active a, float t) {
        switch (a.piece()) {
            case WATCH_ARENA -> arena(c, a, t);
            case WATCH_EXPOSED -> exposed(c, a, t);
            case WATCH_CRACK -> crack(c, a, t);
            case WATCH_SLAM_MARK -> slamMark(c, a, t);
            case WATCH_SLAM -> slam(c, a, t);
            case WATCH_ICE_MARK -> iceMark(c, a, t);
            case WATCH_ICE_WALL -> iceWall(c, a, t);
            case WATCH_RAIL_MARK -> railMark(c, a, t);
            case WATCH_RAIL_ARC -> railArc(c, a, t);
            case WATCH_GRAB_MARK -> grabMark(c, a, t);
            case WATCH_HOLD -> hold(c, a, t);
            case WATCH_TAKE -> take(c, a, t);
            case WATCH_RINGS_MARK -> ringsMark(c, a, t);
            case WATCH_RINGS -> rings(c, a, t);
            case WATCH_DOORS -> doors(c, a, t);
            case WATCH_PULL -> pull(c, a, t);
            case WATCH_DROP -> drop(c, a, t);
            default -> {}
        }
    }

    // ------------------------------------------------------------------
    // Primitives: a three-layer line and ring, a dial, a gear
    // ------------------------------------------------------------------

    /** A glowing line in three layers: a wide faint halo, the body, a thin bright core. */
    static void beam(Canvas c, Vec3 a, Vec3 b, float width, float[] halo, float[] body, float[] core, float alpha) {
        c.line(a, b, width * 3.2F, halo, alpha * 0.35F, alpha * 0.35F);
        c.line(a, b, width, body, alpha * 0.85F, alpha * 0.85F);
        c.line(a, b, width * 0.35F, core, alpha, alpha);
    }

    /** A ring in three layers, from one angle to another, on the plane across {@code axis}. */
    static void band(Canvas c, Vec3 at, Vec3 axis, float r, float width, float from, float to, float[] halo, float[] body, float[] core, float alpha) {
        c.ring(at, axis, r, width * 3.2F, from, to, halo, alpha * 0.3F);
        c.ring(at, axis, r, width, from, to, body, alpha * 0.85F);
        c.ring(at, axis, r, width * 0.35F, from, to, core, alpha);
    }

    /** The floor angle (for {@link Canvas#ring}) of a server angle: twelve toward -z, clockwise from above. */
    static float floor(double serverAngle) { return PI - (float) serverAngle; }

    /** A point on the floor at server angle {@code g} and radius {@code r} round {@code at}. */
    static Vec3 onFloor(Vec3 at, double g, double r) { return at.add(Math.sin(g) * r, 0, -Math.cos(g) * r); }

    /**
     * A dial lying on the floor: a brass rim, twelve hour marks (every third longer), the elapsed part filled with the
     * power's colour, and the hand sweeping from twelve, landing as {@code progress} reaches 1.
     */
    static void floorDial(Canvas c, Vec3 at, float r, float progress, float[] fill, float alpha) {
        if (!c.glow()) return;
        Vec3 p = at.add(0, 0.06, 0);
        float width = Math.max(0.04F, r * 0.022F);
        band(c, p, UP, r, width, 0, TWO_PI, DEEP, BRASS, GILT, alpha);
        for (int h = 0; h < 12; h++) {
            double g = h * PI / 6;
            float inner = h % 3 == 0 ? 0.78F : 0.86F;
            c.line(onFloor(p, g, r * inner), onFloor(p, g, r * 0.96F), width * (h % 3 == 0 ? 1.6F : 1.0F), GILT, alpha * 0.9F, alpha * 0.9F);
        }
        // What has run down, filled in the stolen power's colour, brighter as it nears twelve.
        float k = Mth.clamp(progress, 0, 1);
        if (k > 0.002F) c.ring(p, UP, r * 0.45F, r * 0.45F, floor(0), floor(k * TWO_PI), fill, alpha * (0.22F + 0.33F * k));
        Vec3 tip = onFloor(p, k * TWO_PI, r * 0.92F);
        beam(c, p, tip, width * 1.5F, DEEP, BRASS, GILT, alpha);
        c.glow(p, r * 0.12F + 0.15F, GILT, alpha * 0.6F);
        c.glow(tip, 0.25F + r * 0.05F, fill, alpha * 0.5F * (0.5F + k));
    }

    /** A gear: a ring with square teeth, turned by {@code spin}, on the plane across {@code axis}. */
    static void gear(Canvas c, Vec3 at, Vec3 axis, float r, int teeth, float spin, float[] col, float alpha) {
        c.ring(at, axis, r, r * 0.12F, 0, TWO_PI, col, alpha);
        c.ring(at, axis, r * 0.35F, r * 0.08F, 0, TWO_PI, col, alpha * 0.8F);
        for (int i = 0; i < teeth; i++) {
            float ang = spin + i * TWO_PI / teeth;
            c.spoke(at, axis, ang, r * 1.02F, r * 1.28F, r * 0.09F, col, alpha);
        }
        for (int i = 0; i < 4; i++) c.spoke(at, axis, spin + i * PI / 2, r * 0.35F, r * 0.92F, r * 0.05F, col, alpha * 0.7F);
    }

    // ------------------------------------------------------------------
    // The shop's clocks
    // ------------------------------------------------------------------

    /**
     * The shop, re-sent every second: the great clock over the north wall with its hands, the pendulum swinging in its
     * case beneath, and faint hands turning on the clock face in the floor. Taking Inventory keeps time; The Hunger runs
     * four times fast; Clockwork runs a dozen times fast and the floor's hour marks glow violet; while he runs, the hands
     * spin wildly backwards.
     */
    private static void arena(Canvas c, Active a, float t) {
        float f = fade(t, a.piece().ticks, 4, 4);
        if (f <= 0) return;
        int phase = Math.round(a.scale()) % 10;
        boolean running = a.scale() >= 10;
        float now = clock(c, t);
        float rate = running ? -40 : phase == 1 ? 1 : phase == 2 ? 4 : 12;
        // The great clock: its face is the wall at the target's back.
        Vec3 face = a.target().add(0, 0, -0.47);
        float minute = now * rate / 1200F, hour = minute / 12F;
        if (c.glow()) {
            float mAng = PI / 2 - TWO_PI * minute, hAng = PI / 2 - TWO_PI * hour;
            Vec3 mTip = face.add(cos(mAng) * 2.3, sin(mAng) * 2.3, 0), hTip = face.add(cos(hAng) * 1.5, sin(hAng) * 1.5, 0);
            beam(c, face, mTip, 0.07F, DEEP, BRASS, GILT, f * 0.9F);
            beam(c, face, hTip, 0.11F, DEEP, BRASS, GILT, f * 0.9F);
            for (int h = 0; h < 12; h++) {
                float ang = PI / 2 - h * TWO_PI / 12;
                c.spoke(face, SOUTH, ang, h % 3 == 0 ? 2.0F : 2.2F, 2.45F, 0.05F, phase >= 3 ? STOPPED_PALE : GILT, f * 0.7F);
            }
            c.glow(face, 0.35F, GILT, f * 0.7F);
            if (phase >= 2) c.glow(face, 3.2F, phase >= 3 ? STOPPED : HUNGER, f * 0.12F);
        }
        // The pendulum, in the case under the clock: pivot just under the face, a bob near the floor.
        Vec3 pivot = a.target().add(0, -2.3, -0.2);
        float swing = 0.32F * sin(now * 0.105F * (running ? 3 : phase == 1 ? 1 : phase == 2 ? 2 : 3.2F));
        Vec3 bob = pivot.add(sin(swing) * 2.1, -cos(swing) * 2.1, 0);
        if (c.solid()) {
            Vec3 mid = pivot.lerp(bob, 0.5);
            c.box(mid, 0.035F, 1.05F, 0.035F, 0, 0, -swing, BRASS, 1);
            c.box(bob, 0.32F, 0.32F, 0.06F, 0, 0, -swing, BRASS, 1);
        }
        if (c.glow()) c.glow(bob, 0.45F, GILT, f * 0.35F);
        // Hands on the clock face in the floor.
        if (c.glow() && !minimal()) {
            Vec3 middle = a.origin().add(0, 0.07, 0);
            float floorMinute = now * rate / 1200F;
            beam(c, middle, onFloor(middle, TWO_PI * floorMinute, 7.4), 0.08F, DEEP, BRASS, GILT, f * 0.4F);
            beam(c, middle, onFloor(middle, TWO_PI * floorMinute / 12, 5.0), 0.12F, DEEP, BRASS, GILT, f * 0.4F);
            if (phase >= 3) for (int h = 0; h < 12; h++)
                c.glow(onFloor(middle, h * PI / 6, h % 3 == 0 ? 6.6 : 7.1), 0.5F, STOPPED, f * 0.35F);
        }
    }

    // ------------------------------------------------------------------
    // His watch
    // ------------------------------------------------------------------

    /** Where his watch is: on his left wrist, held up while he winds a move up. */
    static Vec3 wrist(Entity e, float partial) {
        float yaw = (e instanceof LivingEntity l ? Mth.rotLerp(partial, l.yBodyRotO, l.yBodyRot) : e.getYRot()) * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-sin(yaw), 0, cos(yaw)), left = new Vec3(cos(yaw), 0, sin(yaw));
        return e.getPosition(partial).add(0, 1.05, 0).add(left.scale(0.42)).add(forward.scale(0.32));
    }

    /**
     * His watch showing: a small dial at his wrist with a second hand ticking round, a gilt glint that pulses, and a halo
     * that draws the eye. Cracks already in it show as dark lines.
     */
    private static void exposed(Canvas c, Active a, float t) {
        Entity e = a.bound(c);
        if (e == null || !c.glow()) return;
        float f = fade(t, a.piece().ticks, 2, 2);
        Vec3 w = wrist(e, c.partial);
        float now = clock(c, t);
        Vec3 axis = w.subtract(e.getPosition(c.partial).add(0, 1.05, 0)).normalize();
        float pulse = 0.75F + 0.25F * sin(now * 0.6F);
        c.ring(w, axis, 0.14F, 0.03F, 0, TWO_PI, BRASS, f);
        c.disc(w, axis, 0.12F, GLASS, f * 0.55F, f * 0.3F);
        c.spoke(w, axis, -Mth.floor(now / 4) * TWO_PI / 15, 0, 0.11F, 0.012F, SOOT, f);
        c.glow(w, 0.55F * pulse, GILT, f * 0.45F);
        c.glow(w, 0.18F, WHITE, f * 0.8F * pulse);
        // A four-pointed glint, so it reads from across the shop.
        float g = 0.5F * pulse;
        c.line(w.add(0, -g, 0), w.add(0, g, 0), 0.03F, GILT, f * 0.9F, f * 0.9F);
        c.line(w.add(-g * 0.7, 0, 0), w.add(g * 0.7, 0, 0), 0.03F, GILT, f * 0.9F, f * 0.9F);
        int cracks = Math.min(4, Math.round(a.scale()));
        for (int i = 0; i < cracks; i++) {
            float ang = hash(a.entity(), i) * TWO_PI;
            c.spoke(w, axis, ang, 0.02F, 0.13F, 0.01F, WHITE, f * 0.9F);
        }
    }

    /**
     * The watch cracks: glass shards burst from his wrist, little gears and a spring tumble out, a white flash; then a
     * ring of brass ticks circles his head while he reels.
     */
    private static void crack(Canvas c, Active a, float t) {
        Entity e = a.bound(c);
        Vec3 w = e != null ? wrist(e, c.partial) : a.origin();
        Vec3 head = e != null ? e.getPosition(c.partial).add(0, 2.15, 0) : a.origin().add(0, 0.5, 0);
        int seed = a.seed();
        float burst = smooth(0, 14, t), f = fade(t, a.piece().ticks, 1, 10);
        int shards = minimal() ? 5 : 10;
        for (int i = 0; i < shards; i++) {
            float yaw = hash(seed, i) * TWO_PI, up = (hash(seed, i + 30) - 0.3F) * 1.4F;
            Vec3 dir = new Vec3(cos(yaw), up, sin(yaw)).normalize();
            Vec3 at = w.add(dir.scale(burst * (0.8 + hash(seed, i + 60) * 1.2))).add(0, -0.25 * burst * burst, 0);
            if (c.glow()) c.shard(at, at.add(dir.scale(0.12)), 0.035F, 3, t * 0.4F + i, GLASS, f * 0.8F);
        }
        if (c.glow()) {
            float flash = 1 - smooth(0, 6, t);
            c.glow(w, 1.4F * flash + 0.2F, WHITE, flash);
            c.glow(w, 0.6F, GILT, f * 0.6F);
            // Two gears and a spring tumbling out.
            for (int i = 0; i < 2; i++) {
                float yaw = hash(seed, 90 + i) * TWO_PI;
                Vec3 at = w.add(cos(yaw) * burst * 0.9, 0.3 * burst - 0.6 * burst * burst, sin(yaw) * burst * 0.9);
                gear(c, at, new Vec3(cos(yaw + 1), 0.5, sin(yaw + 1)).normalize(), 0.07F, 8, t * 0.5F * (i == 0 ? 1 : -1), BRASS, f);
            }
            if (!minimal()) c.helix(w, 0.5F * burst, 0.03F, 0.09F, 4, t * 0.3F, 0.012F, GILT, f, 24);
            // Reeling: brass ticks round his head.
            float reel = Canvas.fade(t, a.piece().ticks, 6, 10);
            for (int i = 0; i < 5; i++) {
                float ang = clock(c, t) * 0.25F + i * TWO_PI / 5;
                Vec3 p = head.add(cos(ang) * 0.5, sin(clock(c, t) * 0.2F + i) * 0.06, sin(ang) * 0.5);
                c.glow(p, 0.12F, GILT, reel * 0.9F);
            }
        }
    }

    // ------------------------------------------------------------------
    // Taking Inventory: slam, ice wall, rail
    // ------------------------------------------------------------------

    /** The slam's mark: a dial round where you stood, filling lavender, and cracks spreading from its middle. */
    private static void slamMark(Canvas c, Active a, float t) {
        float life = a.piece().ticks, k = t / life, f = fade(t, life, 3, 2), r = a.scale();
        floorDial(c, a.origin(), r, k, TK, f);
        if (c.soft()) {
            int n = minimal() ? 4 : 8;
            for (int i = 0; i < n; i++) {
                double g = hash(a.seed(), i) * TWO_PI;
                // A jagged crack, drawn dark (normal blending), lengthening as the hand comes round.
                Vec3 from = a.origin().add(0, 0.05, 0);
                float reach = r * 0.95F * smooth(0, 1, k * 1.2F);
                Vec3[] crack = new Vec3[6];
                for (int p = 0; p < crack.length; p++) {
                    float u = p / (float) (crack.length - 1);
                    crack[p] = onFloor(from, g + (hash(a.seed() + i, p) - 0.5F) * 0.5F * u, reach * u);
                }
                c.ribbon(crack, 0.05F, SOOT, f * 0.9F, f * 0.5F);
            }
        }
        if (c.glow() && k > 0.6F) c.glow(a.origin().add(0, 0.6, 0), r * 0.6F, TK, f * (k - 0.6F) * 0.8F);
    }

    /** The slam: the floor boards thrown up and falling back, a ring of force running out, dust. */
    private static void slam(Canvas c, Active a, float t) {
        float life = a.piece().ticks, r = a.scale(), f = fade(t, life, 1, 10);
        Vec3 at = a.origin();
        int seed = a.seed();
        if (c.solid()) {
            int boards = minimal() ? 8 : 18;
            for (int i = 0; i < boards; i++) {
                double g = hash(seed, i) * TWO_PI, d = Math.sqrt(hash(seed, i + 40)) * r;
                float v = 0.35F + hash(seed, i + 80) * 0.35F, time = Math.min(t, 24);
                double y = v * time - 0.03 * time * time;
                if (y < -0.2) continue;
                Vec3 p = onFloor(at, g, d + time * 0.04).add(0, Math.max(0, y) + 0.1, 0);
                c.box(p, 0.45F, 0.07F, 0.16F, (float) g + t * 0.05F, t * 0.2F * (hash(seed, i + 9) - 0.5F), t * 0.13F, PLANK, 1);
            }
        }
        if (c.glow()) {
            float out = smooth(0, 10, t);
            band(c, at.add(0, 0.15, 0), UP, r * (0.3F + 0.9F * out), 0.08F, 0, TWO_PI, TK, TK, WHITE, f * (1 - out * 0.6F));
            c.glow(at.add(0, 0.5, 0), r * 0.8F * (1 - out), WHITE, (1 - out));
        }
        if (c.soft() && !minimal()) {
            var shade = Shades.smoke(c, rgb(0x8A7A66), 0);
            for (int i = 0; i < 6; i++) {
                double g = i * TWO_PI / 6 + hash(seed, i + 200);
                float grow = smooth(0, 20, t);
                c.cloud(onFloor(at, g, r * (0.4 + 0.6 * grow)).add(0, 0.4 + grow * 0.6, 0), 0.6F + grow, 0.4F + grow * 0.5F,
                        0.6F + grow, i, t * 0.03F, f * 0.45F, shade);
            }
        }
    }

    /** The ice wall's line: frost along the floor from wall to wall, its hour ticks lighting from both ends to the middle. */
    private static void iceMark(Canvas c, Active a, float t) {
        float life = a.piece().ticks, k = t / life, f = fade(t, life, 3, 2);
        Vec3 from = a.origin().add(0, 0.06, 0), to = a.target().add(0, 0.06, 0), mid = from.lerp(to, 0.5);
        if (!c.glow()) return;
        beam(c, from, to, 0.06F, FROST, FROST, WHITE, f * (0.35F + 0.5F * k));
        Vec3 along = to.subtract(from);
        double length = along.horizontalDistance();
        Vec3 dir = along.normalize(), across = new Vec3(-dir.z, 0, dir.x);
        for (double d = 0; d <= length; d += 1) {
            double u = d / length, lit = Math.abs(u - 0.5) * 2;   // 1 at the ends, 0 in the middle
            boolean on = lit >= 1 - k;
            Vec3 p = from.add(dir.scale(d));
            c.line(p.add(across.scale(-0.45)), p.add(across.scale(0.45)), on ? 0.06F : 0.03F, on ? WHITE : FROST, f * (on ? 0.95F : 0.35F), f * (on ? 0.95F : 0.35F));
        }
        floorDial(c, mid, 1.3F, k, FROST, f);
        // The cold rising off the line as it comes.
        c.glow(mid.add(0, 1.2, 0), (float) length * 0.25F * k, FROST, f * 0.15F);
    }

    /** The ice wall rising: a front of frost running up its face, shards at its foot, mist. */
    private static void iceWall(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 1, 8), h = a.scale();
        Vec3 from = a.origin(), to = a.target(), along = to.subtract(from);
        double length = along.horizontalDistance();
        Vec3 dir = along.normalize(), across = new Vec3(-dir.z, 0, dir.x);
        int seed = a.seed();
        float rise = smooth(0, 6, t);
        if (c.glow()) {
            Vec3 top = new Vec3(0, h * rise, 0);
            beam(c, from.add(top), to.add(top), 0.09F, FROST, FROST, WHITE, f);
            int n = minimal() ? 6 : 14;
            for (int i = 0; i < n; i++) {
                Vec3 base = from.add(dir.scale(length * hash(seed, i))).add(across.scale((hash(seed, i + 20) - 0.5) * 1.6));
                Vec3 tip = base.add(across.scale((hash(seed, i + 40) - 0.5) * 1.2)).add(0, (0.5 + hash(seed, i + 60) * 1.2) * rise, 0);
                c.shard(base, tip, 0.12F, 4, i, FROST, f * 0.7F);
            }
        }
        if (c.soft() && !minimal()) {
            var shade = Shades.smoke(c, rgb(0xDCEBF1), 0);
            for (int i = 0; i < 6; i++) {
                Vec3 p = from.add(dir.scale(length * (i + 0.5) / 6)).add(0, 0.6 + t * 0.06, 0);
                c.cloud(p, 0.9F, 0.6F, 0.9F, i, t * 0.02F, f * 0.35F, shade);
            }
        }
    }

    /** A gear rail charging: light creeps along the copper from both posts toward the middle, sparks hopping ahead. */
    private static void railMark(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float life = a.piece().ticks, k = t / life, f = fade(t, life, 3, 2);
        Vec3 from = a.origin().add(0, 0.04, 0), to = a.target().add(0, 0.04, 0), mid = from.lerp(to, 0.5);
        beam(c, from, to, 0.04F, SPARK, SPARK, WHITE, f * 0.3F);
        Vec3 left = from.lerp(mid, k), right = to.lerp(mid, k);
        beam(c, from, left, 0.12F, SPARK, SPARK, WHITE, f);
        beam(c, to, right, 0.12F, SPARK, SPARK, WHITE, f);
        c.glow(left.add(0, 0.2, 0), 0.9F, SPARK, f * 0.7F);
        c.glow(right.add(0, 0.2, 0), 0.9F, SPARK, f * 0.7F);
        int hop = (int) (clock(c, t) / 2);
        c.bolt(left, left.add(0, 0.6 + hash(hop, 1) * 0.5, 0), a.seed() + hop, 0.15F, 0.03F, SPARK, f);
        c.bolt(right, right.add(0, 0.6 + hash(hop, 2) * 0.5, 0), a.seed() - hop, 0.15F, 0.03F, SPARK, f);
        // A small dial over each post: the countdown.
        for (Vec3 post : new Vec3[] {a.origin(), a.target()}) {
            Vec3 top = post.add(0, 3.1, 0);
            c.ring(top, UP, 0.5F, 0.04F, 0, TWO_PI, BRASS, f);
            c.spoke(top, UP, floor(k * TWO_PI), 0, 0.45F, 0.03F, GILT, f);
            c.glow(top, 0.5F + k, SPARK, f * 0.4F);
        }
    }

    /** Lightning down the whole rail, arcs leaping off it, the posts blazing. */
    private static void railArc(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float life = a.piece().ticks, f = fade(t, life, 1, 8);
        Vec3 from = a.origin().add(0, 0.3, 0), to = a.target().add(0, 0.3, 0);
        int flicker = (int) (t / 2);
        c.bolt(from, to, a.seed() + flicker, 0.35F, 0.12F, SPARK, f);
        c.bolt(from, to, a.seed() + flicker * 7 + 3, 0.6F, 0.05F, WHITE, f * 0.8F);
        int arcs = minimal() ? 3 : 7;
        for (int i = 0; i < arcs; i++) {
            Vec3 p = from.lerp(to, hash(a.seed() + flicker, i));
            c.bolt(p, p.add((hash(flicker, i) - 0.5) * 1.5, 1.2 + hash(flicker, i + 9) * 1.4, (hash(flicker, i + 4) - 0.5) * 1.5),
                    a.seed() + i + flicker, 0.3F, 0.04F, SPARK, f * 0.8F);
        }
        c.glow(a.origin().add(0, 2.8, 0), 1.6F, SPARK, f * 0.6F);
        c.glow(a.target().add(0, 2.8, 0), 1.6F, SPARK, f * 0.6F);
    }

    // ------------------------------------------------------------------
    // The Hunger: the grab
    // ------------------------------------------------------------------

    /** The lane of his lunge: two red edges along the floor, chevrons lighting toward you, his reaching hand burning. */
    private static void grabMark(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float life = a.piece().ticks, k = t / life, f = fade(t, life, 2, 1);
        Entity e = a.bound(c);
        Vec3 start = (e != null ? e.getPosition(c.partial) : a.origin()).add(0, 0.06, 0);
        Vec3 dir = a.target().subtract(a.origin()).multiply(1, 0, 1);
        double reach = Math.min(a.scale(), dir.length() + 1);
        dir = dir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 across = new Vec3(-dir.z, 0, dir.x).scale(0.8), end = start.add(dir.scale(reach));
        c.line(start.add(across), end.add(across), 0.05F, HUNGER, f * 0.8F, f * 0.4F);
        c.line(start.subtract(across), end.subtract(across), 0.05F, HUNGER, f * 0.8F, f * 0.4F);
        int chevrons = (int) Math.ceil(reach);
        for (int i = 1; i <= chevrons; i++) {
            float u = i / (float) chevrons;
            boolean on = u <= k + 0.05F;
            Vec3 p = start.add(dir.scale(reach * u));
            float al = f * (on ? 0.95F : 0.25F);
            c.line(p.add(across.scale(0.8)).subtract(dir.scale(0.4)), p, 0.05F, on ? GILT : HUNGER, al, al);
            c.line(p.subtract(across.scale(0.8)).subtract(dir.scale(0.4)), p, 0.05F, on ? GILT : HUNGER, al, al);
        }
        floorDial(c, start, 0.9F, k, HUNGER, f);
        if (e != null) {
            float yaw = Mth.rotLerp(c.partial, ((LivingEntity) e).yBodyRotO, ((LivingEntity) e).yBodyRot) * Mth.DEG_TO_RAD;
            Vec3 right = new Vec3(-cos(yaw), 0, -sin(yaw));
            Vec3 hand = e.getPosition(c.partial).add(0, 1.375, 0).add(right.scale(0.32)).add(dir.scale(0.72));
            c.glow(hand, 0.5F + 0.6F * k, HUNGER, f * 0.7F);
            c.glow(hand, 0.18F, GILT, f);
        }
    }

    /**
     * Held in his grip: red bands closing round you, a line from his hand to your chest, and round your chest the
     * struggle: a gilt arc that fills as you fight (at full, you're free).
     */
    private static void hold(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = fade(t, a.piece().ticks, 2, 2);
        Entity e = a.bound(c);
        Vec3 chest = e != null ? e.getPosition(c.partial).add(0, 1.0, 0) : a.origin().add(0, 1, 0);
        float now = clock(c, t), struggle = Mth.clamp(a.scale(), 0, 1);
        for (int i = 0; i < 3; i++) {
            float phase = (now * 0.05F + i / 3F) % 1, r = 1.1F - 0.6F * phase;
            c.ring(chest.add(0, (i - 1) * 0.45, 0), UP, r, 0.05F, 0, TWO_PI, HUNGER, f * 0.7F * (1 - phase * 0.5F));
        }
        beam(c, a.target().add(0, -0.4, 0), chest, 0.05F, HUNGER, HUNGER, GILT, f * 0.7F);
        c.ring(chest, UP, 0.75F, 0.05F, 0, TWO_PI, DEEP, f * 0.5F);
        if (struggle > 0) band(c, chest, UP, 0.75F, 0.05F, floor(0), floor(struggle * TWO_PI), GILT, GILT, WHITE, f);
    }

    /** He takes your power: light drawn out of your chest in a twisting stream into him. */
    private static void take(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float life = a.piece().ticks, f = fade(t, life, 2, 12), k = smooth(0, 20, t);
        Vec3 from = a.origin(), to = a.target();
        Vec3 d = to.subtract(from), side = d.cross(UP);
        if (side.lengthSqr() < 1e-6) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = side.cross(d).normalize();
        int strands = minimal() ? 2 : 4;
        for (int s = 0; s < strands; s++) {
            int n = 18;
            Vec3[] pts = new Vec3[n + 1];
            float[] w = new float[n + 1], al = new float[n + 1];
            for (int i = 0; i <= n; i++) {
                float u = i / (float) n, ang = u * 9 + s * TWO_PI / strands - t * 0.4F, r = 0.35F * sin(u * PI);
                pts[i] = from.add(d.scale(u)).add(side.scale(cos(ang) * r)).add(up.scale(sin(ang) * r));
                w[i] = 0.05F; al[i] = f * (u <= k ? 0.9F : 0.1F);
            }
            c.ribbon(pts, w, s % 2 == 0 ? GILT : HUNGER, al);
        }
        c.glow(from, 0.8F * (1 - k) + 0.2F, GILT, f);
        c.glow(to, 0.3F + 0.9F * k, GILT, f * 0.8F);
    }

    // ------------------------------------------------------------------
    // Clockwork: the rings of stopped time
    // ------------------------------------------------------------------

    /**
     * The clock face lights up: violet over every hour but two, and the gap between those two burning gilt and white.
     * The hand sweeps to twelve; a ring of stopped time gathers outside the walls.
     */
    private static void ringsMark(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float life = a.piece().ticks, k = t / life, f = fade(t, life, 3, 1);
        Vec3 at = a.origin().add(0, 0.08, 0);
        double gap = gapAngle(a);
        float face = 9.0F;
        // Stopped time everywhere but the gap.
        c.ring(at, UP, face * 0.5F, face * 0.5F, floor(gap + WatchmakerMoves.GAP_HALF), floor(gap - WatchmakerMoves.GAP_HALF + TWO_PI),
                STOPPED, f * (0.06F + 0.16F * k));
        // The gap: gilt, its edges bright, pulsing.
        float pulse = 0.8F + 0.2F * sin(clock(c, t) * 0.5F);
        c.ring(at, UP, face * 0.5F, face * 0.5F, floor(gap - WatchmakerMoves.GAP_HALF), floor(gap + WatchmakerMoves.GAP_HALF),
                GILT, f * 0.28F * pulse);
        for (int side = -1; side <= 1; side += 2) {
            double edge = gap + side * WatchmakerMoves.GAP_HALF;
            beam(c, at, onFloor(at, edge, face), 0.07F, DEEP, GILT, WHITE, f);
        }
        floorDial(c, a.origin(), face + 0.4F, k, STOPPED, f);
        // The ring that will close, gathering beyond the walls.
        float r = (float) WatchmakerMoves.RING_START;
        band(c, at.add(0, 1, 0), UP, r, 0.12F, floor(gap + WatchmakerMoves.GAP_HALF), floor(gap - WatchmakerMoves.GAP_HALF + TWO_PI),
                STOPPED_DEEP, STOPPED, STOPPED_PALE, f * k * 0.8F);
    }

    /**
     * The rings close: a fence of stopped time, violet bands at four heights tied by hour-lines, sweeping in from beyond
     * the walls to the middle, open only at the gap. Everything inside it hangs still.
     */
    private static void rings(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 1, 3);
        Vec3 at = a.origin();
        double gap = gapAngle(a);
        float r = (float) WatchmakerMoves.ringRadius(t);
        if (r <= 0.05F) return;
        float from = floor(gap + WatchmakerMoves.GAP_HALF), to = floor(gap - WatchmakerMoves.GAP_HALF + TWO_PI);
        if (c.glow()) {
            int bands = minimal() ? 2 : 4;
            for (int i = 0; i < bands; i++) {
                float y = 0.1F + i * 2.6F / Math.max(1, bands - 1);
                band(c, at.add(0, y, 0), UP, r, 0.06F, from, to, STOPPED_DEEP, STOPPED, STOPPED_PALE, f);
            }
            // Hour lines standing round the ring (none in the gap).
            for (int h = 0; h < 24; h++) {
                double g = h * PI / 12;
                if (WatchmakerMoves.inGap(Math.sin(g), -Math.cos(g), gap)) continue;
                Vec3 base = onFloor(at, g, r);
                c.line(base, base.add(0, 2.7, 0), h % 2 == 0 ? 0.05F : 0.03F, STOPPED_PALE, f * 0.8F, f * 0.2F);
            }
            // The gap's edges blaze gilt, so you can see where to stand.
            for (int side = -1; side <= 1; side += 2) {
                Vec3 base = onFloor(at, gap + side * WatchmakerMoves.GAP_HALF, r);
                beam(c, base, base.add(0, 3, 0), 0.08F, DEEP, GILT, WHITE, f);
            }
            // Motes hanging still inside what has stopped.
            if (detail()) for (int i = 0; i < 20; i++) {
                double g = hash(a.seed(), i) * TWO_PI, d = r + hash(a.seed(), i + 30) * (WatchmakerMoves.RING_START - r);
                if (WatchmakerMoves.inGap(Math.sin(g), -Math.cos(g), gap)) continue;
                c.glow(onFloor(at, g, d).add(0, 0.3 + hash(a.seed(), i + 60) * 2.2, 0), 0.08F, STOPPED_PALE, f * 0.8F);
            }
        }
    }

    private static double gapAngle(Active a) {
        Vec3 d = a.target().subtract(a.origin());
        return Math.atan2(d.x, -d.z);
    }

    // ------------------------------------------------------------------
    // The doors, the pull, the drop
    // ------------------------------------------------------------------

    /** The doors lock: a shock and dust at the door, and a dial over it whose hands spin to a blur. */
    private static void doors(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 1, 10);
        Vec3 door = a.origin();
        if (c.glow()) {
            float out = smooth(0, 8, t);
            c.ring(door, SOUTH, 1.2F + 2.5F * out, 0.1F, 0, TWO_PI, HUNGER, f * (1 - out));
            Vec3 face = door.add(0, 2.4, -0.4);
            c.ring(face, SOUTH, 0.7F, 0.05F, 0, TWO_PI, BRASS, f);
            float spin = t * t * 0.03F;
            c.spoke(face, SOUTH, PI / 2 - spin, 0, 0.6F, 0.04F, GILT, f);
            c.spoke(face, SOUTH, PI / 2 - spin / 12, 0, 0.4F, 0.06F, GILT, f);
            c.glow(face, 1.0F, HUNGER, f * 0.4F);
        }
        if (c.soft() && !minimal()) {
            var shade = Shades.smoke(c, rgb(0x6E6458), 0);
            for (int i = 0; i < 4; i++)
                c.cloud(door.add((i - 1.5) * 0.9, -1 + t * 0.03, -0.6 - t * 0.04), 0.7F, 0.5F, 0.7F, i, t * 0.03F, f * 0.4F, shade);
        }
    }

    /**
     * The world folding: a clock face opens upright behind you, its hands running backward, and closes over you like a
     * watch case snapping shut. Scale 1 is the pull out of the world, 2 the arrival in the shop, 3 the way home.
     */
    private static void pull(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float life = a.piece().ticks, f = fade(t, life, 2, 6);
        Vec3 feet = a.origin();
        Vec3 face = a.target().subtract(feet).multiply(1, 0, 1);
        face = face.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : face.normalize();
        boolean arriving = a.scale() >= 1.5F;
        Vec3 center = feet.add(0, 1, 0).subtract(face.scale(arriving ? 0 : 0.7));
        float open = arriving ? 1 - smooth(4, 20, t) : smooth(0, 10, t) * (1 - smooth(18, life, t));
        float r = 1.5F * open + 0.05F;
        band(c, center, face, r, 0.06F, 0, TWO_PI, DEEP, BRASS, GILT, f);
        c.disc(center, face, r * 0.95F, a.scale() >= 2.5F ? FACE : STOPPED_DEEP, f * 0.25F, f * 0.05F);
        for (int h = 0; h < 12; h++) c.spoke(center, face, h * TWO_PI / 12, r * 0.82F, r * 0.95F, 0.03F, GILT, f);
        float spin = t * 0.5F;
        c.spoke(center, face, spin, 0, r * 0.85F, 0.04F, GILT, f);
        c.spoke(center, face, spin / 4, 0, r * 0.55F, 0.06F, GILT, f);
        c.glow(center, r * 1.4F, BRASS, f * 0.3F);
        if (!minimal()) gear(c, center.add(0, r + 0.2, 0), face, 0.3F * open, 10, -t * 0.2F, BRASS, f * 0.8F);
    }

    /** What he leaves: a brass watch arcing from where he fell to the one who finished him, glinting as it turns. */
    private static void drop(Canvas c, Active a, float t) {
        float life = a.piece().ticks, k = smooth(0, 22, t), f = fade(t, life, 2, 10);
        Entity e = a.bound(c);
        Vec3 to = e != null ? e.getEyePosition(c.partial).add(0, -0.5, 0) : a.target();
        Vec3 at = a.origin().lerp(to, k).add(0, sin(k * PI) * 1.6, 0);
        Vec3 axis = new Vec3(cos(t * 0.3F), 0.3, sin(t * 0.3F)).normalize();
        if (c.solid() && k < 1) c.box(at, 0.16F, 0.16F, 0.04F, t * 0.3F, 0, t * 0.2F, BRASS, 1);
        if (c.glow()) {
            c.ring(at, axis, 0.2F, 0.04F, 0, TWO_PI, GILT, f);
            c.glow(at, 0.6F, GILT, f * 0.6F);
            if (k >= 1) c.ring(to.add(0, -0.4, 0), UP, 0.4F + (t - 22) * 0.05F, 0.05F, 0, TWO_PI, GILT, f * 0.7F);
        }
    }
}
