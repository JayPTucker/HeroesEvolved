package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.flight.FlightService;
import com.jayptucker.heroesevolved.flight.FlightVisualGeometry;
import com.jayptucker.heroesevolved.network.ClientFlightVisualState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/** The same folds of compressed air, opening, tightening or streaming off the body. */
final class FlightArt {
    private static final float[] SHADOW = rgb(0x535A5A);
    private static final float[] VAPOR = rgb(0xD2D4CE);
    private static final float[] CORE = rgb(0xF6F4EC);
    private static final float[] CONDENSATION = rgb(0xDCDDD5);

    private FlightArt() {}

    static void draw(Canvas c, Active a, float t) {
        if (c.solid()) return;
        float alpha = fade(t, a.piece().ticks, 1.3F, 6);
        if (alpha < .003F) return;
        Vec3 at = a.origin();
        Vec3 direction = a.target().subtract(at).normalize();
        if (direction.lengthSqr() < .5) direction = UP;
        float radius = a.scale();
        float time = (float) (c.level.getGameTime() % 2400) + c.partial;
        switch (a.piece()) {
            case FLIGHT_RELEASE -> release(c, at, direction, radius, t, alpha, hash(a.seed(), 1) * TWO_PI);
            case FLIGHT_SKYBURST -> skyburst(c, at, direction, radius, t, alpha, a.seed());
            case FLIGHT_IMPACT -> impact(c, at, direction, radius, t, alpha, a.seed());
            case FLIGHT_RESCUE -> rescue(c, at, a.target(), radius, t, alpha);
            case FLIGHT_SHELTER -> shelter(c, at, radius, time, alpha * .58F);
            case FLIGHT_CURRENT -> current(c, at, a.target(), radius, time, alpha * .58F);
            case FLIGHT_THERMAL_MARK -> thermalMark(c, at, radius, t, alpha);
            case FLIGHT_THERMAL_RISE -> thermalRise(c, at, radius, (float) a.target().distanceTo(at), t, alpha);
            case TEMPEST_DRILL -> {
                Vec3 start = FlightVisualGeometry.bodyCenter(at);
                Vec3 end = FlightVisualGeometry.bodyCenter(at.lerp(a.target(), smooth(0, 10, t)));
                current(c, start, end, 1.55F, time * 2, alpha);
                release(c, start, direction, 1.4F, t, alpha * .65F, 0);
            }
            case GALE_WALL -> {
                Vec3 base = a.at(c);
                for (int i = 0; i < (minimal() ? 3 : 6); i++) {
                    float h = (t * .034F + hash(a.seed(), i)) % 1;
                    arc(c, base.add(0, .17 + h * 2.9, 0), UP, radius, .22F,
                            t * .23F + i * 1.9F, 2.65F, alpha * sin(h * PI));
                }
            }
            case FORMATION_FLIGHT -> {
                Vec3 base = a.at(c);
                arc(c, base.add(0, .28, 0), UP, .83F, .10F, t * .16F, 4.4F, alpha * .75F);
                if (!minimal()) spiral(c, base, UP, 1.4F, .68F, .40F, t * .25F, 5.4F, .085F, alpha * .7F);
            }
            case FLIGHT_INTERCEPT -> {
                float r = .22F + smooth(0, 9, t) * radius;
                arc(c, at.add(direction.scale(t * .045)), direction, r, .055F,
                        t * .18F, TWO_PI, alpha * .88F);
            }
            default -> {}
        }
    }

    private static void skyburst(Canvas c, Vec3 at, Vec3 axis, float size, float age, float alpha, int seed) {
        float phase = hash(seed, 1) * TWO_PI;
        // The first pressure front snaps open, then the slower folds peel off into the wake.
        int fronts = minimal() ? 1 : 3;
        for (int i = 0; i < fronts; i++) {
            float t = age - i * 2.5F;
            float life = fade(t, 25, 1.1F, 13);
            if (life <= 0) continue;
            float p = clamp(t / (10 + i * 2), 0, 1);
            float spread = 1 - (1 - p) * (1 - p) * (1 - p);
            float r = .65F + spread * size * (1 - i * .16F);
            Vec3 center = at.subtract(axis.scale(t * (.20 + i * .18)));
            float width = lerp(spread, .26F, .44F) * (1 - i * .15F);
            arc(c, center, axis, r, width, phase + i * 2.3F, i == 0 ? TWO_PI : 4.85F,
                    alpha * life * (1 - i * .23F));
            if (c.soft() && i == 0) {
                c.disc(center, axis, r, CONDENSATION, 0, .065F * alpha * life);
                if (detail()) {
                    for (int j = 0; j < 5; j++) {
                        float angle = phase + (j + hash(seed, 10 + j) * .75F) * TWO_PI / 5;
                        float drift = hash(seed, 20 + j);
                        Vec3 mist = center.subtract(axis.scale(.15F + spread * drift * 1.4F));
                        crest(c, mist, axis, r * (.83F + drift * .22F), width * (1.9F + drift),
                                angle + spread * .45F, .8F + drift * .7F, CONDENSATION, .36F * alpha * life);
                    }
                }
            }
        }
        if (!minimal()) {
            float run = smooth(0, 13, age);
            for (int i = 0; i < 3; i++)
                spiral(c, at, axis.scale(-1), 1.5F + size * 1.65F * run, .42F, size * .68F,
                        phase + i * TWO_PI / 3 + age * .1F, 2.15F, .20F,
                        alpha * (1 - smooth(10, 23, age)) * .82F);
        }
    }

    private static void release(Canvas c, Vec3 at, Vec3 axis, float size, float age, float alpha, float phase) {
        // A tight shock collar sheds successive rings backward into the space the flyer just left.
        for (int i = 0; i < (minimal() ? 2 : 4); i++) {
            float t = age - i * 2.2F;
            float life = fade(t, 19, 1.4F, 7);
            if (life <= 0) continue;
            float run = smooth(0, 18, t);
            float r = (.26F + run * .61F + i * .10F) * size;
            Vec3 center = at.subtract(axis.scale(run * (1.9 + i * .8))).add(axis.scale(.16 * i));
            arc(c, center, axis, r, (.13F - run * .055F) * size,
                    phase + i * 1.6F + run, i == 0 ? TWO_PI : 4.9F, alpha * life * (.9F - i * .14F));
        }
        if (!minimal()) {
            float reach = smooth(0, 15, age);
            for (int side = 0; side < 2; side++)
                spiral(c, at, axis.scale(-1), 1 + 4 * reach, .21F * size, .95F * size,
                        phase + side * PI + age * .12F, 3.8F, .12F * size, alpha * .7F);
        }
        if (c.soft() && detail()) {
            Vec3 side = transverse(axis);
            for (int i = 0; i < 3; i++) {
                float drift = smooth(0, 14, age);
                Vec3 wisp = at.subtract(axis.scale(.6 + drift * (1 + i)))
                        .add(side.scale((i - 1) * size * (.22 + .36 * drift)));
                float s = (.22F + drift * .45F) * size;
                c.cloud(wisp, s, s * .52F, s * 1.5F, i, phase + drift, alpha * .13F,
                        Shades.smoke(c, CONDENSATION, 0));
            }
        }
    }

    private static void impact(Canvas c, Vec3 at, Vec3 normal, float radius, float age, float alpha, int seed) {
        // The first front hugs the contact plane. The second folds over it and leaves scallops of mist.
        int fronts = minimal() ? 1 : 3;
        for (int i = 0; i < fronts; i++) {
            float t = age - i * 3;
            float life = fade(t, 21, 1.2F, 8);
            if (life <= 0) continue;
            float p = smooth(0, 16, t);
            float r = radius * (.09F + p * (.91F - i * .15F));
            Vec3 center = at.add(normal.scale(.03 + i * .21 + p * .23));
            arc(c, center, normal, r, (.10F + radius * .031F) * (1 - .5F * p),
                    i * 2 + age * .03F, i == 0 ? TWO_PI : 5.1F, alpha * life * (1 - i * .2F));
            if (c.soft() && i == 0) {
                c.disc(center, normal, r, CONDENSATION, .02F * life, .16F * life);
                if (detail()) {
                    Vec3 side = transverse(normal), lift = normal.cross(side);
                    for (int j = 0; j < 9; j++) {
                        float angle = TWO_PI * (j + hash(seed, j) * .6F) / 9;
                        Vec3 edge = center.add(side.scale(cos(angle) * r)).add(lift.scale(sin(angle) * r));
                        float s = (.25F + .5F * p) * (1 + hash(seed, j + 9) * .45F);
                        c.cloud(edge, s, s * .45F, s, j, angle, alpha * life * .24F,
                                Shades.smoke(c, CONDENSATION, 0));
                    }
                }
            }
        }
    }

    private static void rescue(Canvas c, Vec3 from, Vec3 to, float size, float age, float alpha) {
        float arrive = smooth(0, 6, age);
        Vec3 reached = from.lerp(to, arrive);
        float length = (float) from.distanceTo(reached);
        if (length > .05F) {
            Vec3 dir = reached.subtract(from).normalize();
            spiral(c, from, dir, length, .15F, .46F * size, -age * .19F,
                    7.4F, .105F, alpha * (1 - smooth(10, 22, age)));
            if (detail()) spiral(c, from, dir, length, .15F, .46F * size,
                    PI - age * .19F, 7.4F, .075F, alpha * .62F);
        }
        float catchAlpha = smooth(4, 7, age) * alpha;
        arc(c, to.add(0, -.4 + age * .044, 0), UP, (.5F + arrive * .44F) * size, .09F,
                -age * .16F, TWO_PI, catchAlpha);
    }

    private static void shelter(Canvas c, Vec3 at, float radius, float time, float alpha) {
        if (c.soft()) c.shell(at, radius, radius, radius, HALF_PI, CONDENSATION, .18F * alpha,
                .03F, (theta, phi) -> .4F + .6F * abs(sin(theta * 3 + phi * 8 - time * .10F)), 0);
        arc(c, at.add(0, .05, 0), UP, radius, .105F, time * .06F, TWO_PI, alpha);
        for (int i = 0; i < (minimal() ? 1 : 3); i++) {
            float p = (time * .037F + i / 3F) % 1;
            float h = p * radius;
            float r = sqrt(radius * radius - h * h);
            arc(c, at.add(0, h, 0), UP, r, .14F, time * .09F + i * 2,
                    4.6F, alpha * sin(p * PI));
        }
    }

    private static void current(Canvas c, Vec3 from, Vec3 to, float radius, float time, float alpha) {
        Vec3 dir = to.subtract(from).normalize();
        float length = (float) from.distanceTo(to);
        if (length < .05F) return;
        // Continuous helical lanes with faster crests travelling toward the open end.
        int count = minimal() ? 1 : 3;
        for (int i = 0; i < count; i++)
            spiral(c, from, dir, length, radius * .78F, radius * .78F,
                    -time * .12F + i * TWO_PI / count, 3.8F, .17F, alpha * .86F);
        int pulses = minimal() ? 2 : 5;
        for (int i = 0; i < pulses; i++) {
            float p = (time * .058F + i / (float) pulses) % 1;
            arc(c, from.lerp(to, p), dir, radius, .15F, -time * .11F + i * 1.7F,
                    2.85F, alpha * sin(p * PI) * .85F);
        }
    }

    private static void thermalMark(Canvas c, Vec3 at, float radius, float age, float alpha) {
        arc(c, at, UP, radius, .08F, 0, TWO_PI, alpha * .8F);
        arc(c, at.add(0, .08, 0), UP, radius * (1 - smooth(0, 8, age) * .72F),
                .19F, age * .4F, TWO_PI, alpha);
        if (c.soft()) c.disc(at, UP, radius, VAPOR, alpha * .12F, 0);
    }

    private static void thermalRise(Canvas c, Vec3 at, float radius, float height, float age, float alpha) {
        float top = Math.max(.1F, height * smooth(0, 9, age));
        int count = minimal() ? 1 : 3;
        for (int i = 0; i < count; i++)
            spiral(c, at, UP, top, radius, radius * .72F, age * .22F + i * TWO_PI / count,
                    4.2F, .24F, alpha * .93F);
        arc(c, at.add(0, top, 0), UP, radius * .78F, .16F, age * .23F, 5.5F, alpha * .92F);
        arc(c, at.add(0, .08, 0), UP, radius, .12F, 0, TWO_PI, alpha * .7F);
    }

    static boolean live() {
        var level = Minecraft.getInstance().level;
        return level != null && level.players().stream().anyMatch(FlightArt::hasAir);
    }

    private static boolean hasAir(Player player) {
        int id = player.getId();
        return player.isAlive() && !ClientFlightVisualState.isPyroFlight(id)
                && (ClientFlightVisualState.isFlightActive(id) || ClientFlightVisualState.isCharging(id)
                    || ClientFlightVisualState.isGatheringVacuum(id));
    }

    static void drawLive(Canvas c) {
        if (c.solid()) return;
        float time = (float) (c.level.getGameTime() % 2400) + c.partial;
        for (Player player : c.level.players()) {
            if (!hasAir(player) || player.position().distanceToSqr(c.camera()) > 96 * 96) continue;
            int id = player.getId();
            Vec3 center = FlightVisualGeometry.bodyCenter(player.getPosition(c.partial));
            Vec3 look = FlightVisualGeometry.direction(player, false, c.partial);
            float age = ClientFlightVisualState.phaseAge(id, c.level.getGameTime(), c.partial);
            if (ClientFlightVisualState.isCharging(id) || ClientFlightVisualState.isGatheringVacuum(id)
                    || ClientFlightVisualState.isDiving(id) && !ClientFlightVisualState.isPlunging(id)) {
                gather(c, id, center, look, age);
                continue;
            }
            if (player.onGround()) continue;
            Vec3 movement = FlightVisualGeometry.travel(player);
            if (movement.lengthSqr() < .03) continue;
            Vec3 dir = movement.normalize();
            boolean roll = ClientFlightVisualState.isRolling(id);
            boolean dive = ClientFlightVisualState.isPlunging(id);
            boolean burst = ClientFlightVisualState.isBursting(id) && movement.lengthSqr() > 3.6 * 3.6;
            int momentum = ClientFlightVisualState.momentum(id);
            float speed = (float) Mth.clamp(movement.length() / 2, .1, 1);
            float strength = speed * (.46F + momentum * .003F);
            if (roll || dive || ClientFlightVisualState.isBursting(id)) strength = .89F;
            // With the camera inside your own wake, show only the forward collar.
            float clear = smooth(.85F, 2.6F, (float) c.camera().distanceTo(center));
            float phase = roll ? ClientFlightVisualState.rollProgress(id) * TWO_PI * ClientFlightVisualState.rollDirection(id) : time * .16F;
            float r = burst ? 1.12F : dive ? .52F : .77F;
            arc(c, center.add(dir.scale(.7)), dir, r, .095F, phase, dive ? 5.6F : 3.9F, strength * Math.max(.23F, clear));
            if (!minimal() && clear > .003F) {
                Vec3 tail = center.subtract(dir.scale(.65));
                float length = burst ? Math.min(20, (float) movement.length() * 2.2F) : 1.2F + 2.6F * speed;
                for (int i = 0; i < 2; i++)
                    spiral(c, tail, dir.scale(-1), length, .35F, r * 1.2F,
                            phase + i * PI, roll ? 7.5F : 2.5F, .115F, strength * clear);
            }
        }
    }

    static void tempest(Canvas c, Vec3 center, float age, float alpha) {
        if (c.solid()) return;
        float reach = (float) FlightService.TEMPEST_PULL;
        Vec3 ground = center.add(0, .13, 0);
        float warning = 1 - smooth(FlightService.TEMPEST_WINDUP_TICKS - 3, FlightService.TEMPEST_WINDUP_TICKS + 4, age);
        int count = minimal() ? 3 : 6;
        for (int i = 0; i < count; i++) {
            float phase = i * TWO_PI / count + age * .035F;
            arc(c, ground, UP, reach, .12F, phase, TWO_PI / count * .68F, alpha * (.22F + warning * .32F));
            if (!minimal()) {
                float progress = (age * .044F + i / (float) count) % 1;
                arc(c, ground.add(0, .05, 0), UP, reach * (1 - progress * .8F),
                        .18F, phase + progress * .6F, 1.05F, alpha * sin(progress * PI) * .52F);
            }
        }
    }

    private static void gather(Canvas c, int id, Vec3 center, Vec3 look, float age) {
        boolean vacuum = ClientFlightVisualState.isGatheringVacuum(id);
        boolean dive = ClientFlightVisualState.isDiving(id);
        Vec3 axis = dive ? UP : look;
        float begin = vacuum || dive ? 0 : 4;
        float alpha = smooth(begin, begin + 2, age);
        if (alpha < .003F) return;
        float compress = smooth(begin, vacuum ? 8 : dive ? 10 : 28, age);
        float span = vacuum ? (ClientFlightVisualState.hasWideVacuum(id) ? 8.35F : 5.35F) : dive ? 2.25F : 2.5F;
        float clear = smooth(.75F, 2.1F, (float) c.camera().distanceTo(center));
        int bands = minimal() ? 2 : 4;
        for (int i = 0; i < bands; i++) {
            float flow = (age * (.12F + compress * .04F) + i / (float) bands) % 1;
            float r = .65F + span * (1 - flow) * (1 - compress * .55F);
            Vec3 at = center.add(axis.scale((flow - .45F) * (vacuum ? .6 : 1.7)));
            arc(c, at, axis, r, .075F + (1 - flow) * .06F, -age * .32F + i * 1.45F,
                    dive ? 5.1F : 3.7F, alpha * sin(flow * PI) * (.42F + .58F * clear));
        }
    }

    /** A gust has a broad translucent sheet, a turning lip, and wisps that taper away. */
    private static void arc(Canvas c, Vec3 at, Vec3 axis, float radius, float width, float start, float length, float alpha) {
        if (alpha < .003F || radius < .04F) return;
        if (c.soft()) {
            crest(c, at, axis, radius, width * 2.6F, start, length, SHADOW, alpha * .24F);
            crest(c, at, axis, radius, width * 1.65F, start, length, VAPOR, alpha * .55F);
            if (!minimal() && radius > .5F) {
                crest(c, at.add(axis.scale(width * .6)), axis, radius + width * 1.7F, width * .8F,
                        start + length * .12F, length * .64F, CONDENSATION, alpha * .32F);
            }
        } else if (c.glow()) {
            float light = alpha * lerp(c.daylight, .55F, .30F);
            crest(c, at, axis, radius, width * .68F, start, length, CORE, light);
        }
    }

    private static void crest(Canvas c, Vec3 at, Vec3 axis, float radius, float width, float start,
                              float length, float[] color, float alpha) {
        Vec3 right = transverse(axis);
        Vec3 up = axis.cross(right);
        int steps = Math.clamp((int) (length * (5 + radius)), 9, detail() ? 48 : 27);
        Vec3 previousInner = at, previousMiddle = at, previousOuter = at;
        float previousAlpha = 0;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float angle = start + length * t;
            float taper = sin(PI * t);
            float ripple = sin(angle * 3.2F + start) * .36F + sin(angle * 7.5F) * .11F;
            float thickness = width * taper * (.58F + .29F * sin(angle * 2 + start));
            float r = radius + width * (ripple + (t - .45F) * .72F);
            Vec3 radial = right.scale(cos(angle)).add(up.scale(sin(angle)));
            Vec3 middle = at.add(radial.scale(r)).add(axis.scale(width * ripple * .4F));
            Vec3 inner = middle.subtract(radial.scale(Math.min(r, thickness * 1.85F)));
            Vec3 outer = middle.add(radial.scale(thickness * .52F)).add(axis.scale(thickness * .27F));
            float opacity = alpha * taper * (.65F + .35F * cos(angle * 4 - start));
            if (i > 0) {
                c.tri(previousInner, previousMiddle, middle, color, 0, previousAlpha, opacity);
                c.tri(previousInner, middle, inner, color, 0, opacity, 0);
                c.tri(previousMiddle, previousOuter, outer, color, previousAlpha, 0, 0);
                c.tri(previousMiddle, outer, middle, color, previousAlpha, 0, opacity);
            }
            previousInner = inner;
            previousMiddle = middle;
            previousOuter = outer;
            previousAlpha = opacity;
        }
    }

    private static void spiral(Canvas c, Vec3 base, Vec3 axis, float length, float r0, float r1,
                               float phase, float twist, float width, float alpha) {
        if (alpha < .003F) return;
        Vec3 side = transverse(axis), lift = axis.cross(side);
        int n = detail() ? 19 : 12;
        double[] x = new double[n], y = new double[n], z = new double[n];
        float[] opacity = new float[n], widths = new float[n];
        for (int i = 0; i < n; i++) {
            float p = i / (float) (n - 1), angle = phase + p * twist;
            float r = lerp(p, r0, r1);
            Vec3 at = base.add(axis.scale(p * length)).add(side.scale(cos(angle) * r)).add(lift.scale(sin(angle) * r));
            x[i] = at.x; y[i] = at.y; z[i] = at.z;
            float taper = sin(PI * p);
            opacity[i] = taper * alpha * (.63F + .37F * cos(4 * angle + phase));
            widths[i] = width * (.5F + taper * (.9F + .45F * cos(3 * angle)));
        }
        if (c.soft()) {
            for (int i = 0; i < n; i++) { widths[i] *= 2.6F; opacity[i] *= .36F; }
            c.ribbon(x, y, z, opacity, widths, SHADOW);
            for (int i = 0; i < n; i++) { widths[i] *= .73F; opacity[i] *= 1.65F; }
            c.ribbon(x, y, z, opacity, widths, VAPOR);
        } else if (c.glow()) {
            for (int i = 0; i < n; i++) { widths[i] *= .61F; opacity[i] *= lerp(c.daylight, .54F, .31F); }
            c.ribbon(x, y, z, opacity, widths, CORE);
        }
    }

    private static Vec3 transverse(Vec3 axis) {
        return (abs((float) axis.y) > .9F ? new Vec3(1, 0, 0) : UP).cross(axis).normalize();
    }

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }
    private static boolean detail() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.ALL; }
}
