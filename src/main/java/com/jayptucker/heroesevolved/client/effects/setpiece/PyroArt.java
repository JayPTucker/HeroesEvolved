package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.client.effects.PhoenixGeometry;
import com.jayptucker.heroesevolved.flight.FlightVisualGeometry;
import com.jayptucker.heroesevolved.network.ClientFlightVisualState;
import com.jayptucker.heroesevolved.pyro.PyroFlightGeometry;
import com.jayptucker.heroesevolved.pyro.PyroGeometry;
import com.jayptucker.heroesevolved.styles.StyleCasts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/** Dense flame tongues roll outward, cool at their edges, and lift away as embers. */
final class PyroArt {
    private record Fire(float[] edge, float[] body, float[] gold) {}
    private static final Fire EMBER = new Fire(rgb(0xA32813), rgb(0xF76A13), rgb(0xFFBE42));
    private static final Fire BLUE = new Fire(rgb(0x38409E), rgb(0x447EF4), rgb(0x74CCF3));
    private static final float[] SOOT = rgb(0x493833);

    private PyroArt() {}

    static void draw(Canvas c, Active a, float t) {
        if (c.solid()) return;
        boolean blue = a.scale() >= PyroGeometry.BLUE_SCALE;
        float size = a.scale() - (blue ? PyroGeometry.BLUE_SCALE : 0);
        Fire fire = blue ? BLUE : EMBER;
        float phase = (c.level.getGameTime() % 2400 + c.partial) * .33F;
        float alpha = fade(t, a.piece().ticks, 1.4F, Math.min(12, a.piece().ticks * .38F));
        Vec3 at = a.origin(), end = a.target();
        switch (a.piece()) {
            case PYRO_FORGE -> {
                Vec3 palm = a.bound(c) instanceof Player p ? PyroGeometry.castingHand(p, c.partial) : at;
                orb(c, palm, .13F + size * .29F, phase, alpha, fire);
                jet(c, palm, palm.add(0, .2 + .58 * size, 0), .18F + .15F * size, phase, alpha, fire, 3, 0);
            }
            case PYRO_SPEAR -> {
                // A fast-moving head unfurls the wake from the palm, and the far end breaks into flame.
                float step = smooth(0, 3.5F, t);
                Vec3 head = at.lerp(end, step);
                Vec3 tail = at.lerp(end, smooth(2.5F, 10, t) * .94F);
                billows(c, head, tail, .29F * size, .45F * size, phase, alpha, fire, a.seed());
                if (t < 8) orb(c, head, size * .24F, phase, alpha, fire);
            }
            case PYRO_BREATH -> {
                Vec3 path = end.subtract(at);
                float reach = (float) path.length();
                if (a.bound(c) instanceof Player p) {
                    at = PyroGeometry.breathOrigin(p, c.partial);
                    end = at.add(path);
                }
                float open = smooth(0, 3, t);
                Vec3 front = at.lerp(end, .42F + .58F * open);
                billows(c, at, front, .28F + size * .15F,
                        reach * (float) PyroGeometry.BREATH_SLOPE * .82F * open, phase, alpha, fire, a.seed());
            }
            case PYRO_FIRESTORM_PRIME -> {
                marker(c, at, size, phase, alpha, fire);
                curtain(c, at, size * (1.22F - smooth(0, a.piece().ticks, t) * .22F), .7F, phase, alpha, fire);
            }
            case PYRO_FIRESTORM -> curtain(c, at, size, 2.05F, phase, alpha, fire);
            case PYRO_COLLAPSE -> {
                marker(c, at, size, phase, alpha * .65F, fire);
                curtain(c, at, size * (1 - .92F * smooth(0, PyroGeometry.COLLAPSE_WARNING, t)),
                        1.2F + t * .20F, -phase * 1.5F, alpha, fire);
            }
            case PYRO_BURST -> burst(c, at, size, t, alpha, phase, fire, a.seed());
            case PYRO_SUN_CHARGE -> {
                float radius = .48F + size * .91F;
                marker(c, end, 4 + 6 * size, phase, alpha, fire);
                orb(c, at, radius, phase, alpha, fire);
            }
            case PYRO_SUN_DROP -> fall(c, at, end, size, PyroGeometry.SUN_FLIGHT, t, phase, alpha, fire);
            case PYRO_LASH -> {
                if (a.bound(c) instanceof Player p) at = PyroGeometry.castingHand(p, c.partial);
                // Two snaking edges wrap the same tether, which shortens with the pulled target.
                jet(c, at, end, .13F, -phase * 1.9F, alpha, fire, minimal() ? 1 : 3, .38F);
                orb(c, end, .20F, phase, alpha, fire);
            }
            case PYRO_MANTLE, EMBER_SHROUD -> {
                Vec3 body = a.at(c).add(0, .25, 0);
                float clear = smooth(.4F, 2.4F, (float) c.camera().distanceTo(body));
                curtain(c, body, a.piece() == com.jayptucker.heroesevolved.styles.Piece.EMBER_SHROUD ? 2.6F : size,
                        1.45F, phase, alpha * (.27F + clear * .73F), fire);
            }
            case PYRO_CAUTERIZE -> {
                Vec3 recipient = a.at(c).add(0, .16, 0);
                curtain(c, recipient, size * .60F, 1.25F, -phase, alpha * .74F, fire);
                if (c.glow()) c.glow(recipient.add(0, 1.1, 0), 1.3F, fire.gold, alpha * .30F);
            }
            case PYRO_MINE_ARM -> {
                marker(c, at, .46F, phase, alpha * .6F, fire);
                orb(c, at.add(0, .10, 0), .10F, phase, alpha, fire);
            }
            case PYRO_MINE_WARN -> {
                marker(c, at, size, -phase, alpha, fire);
                curtain(c, at, size * smooth(0, 5, t), .65F, phase, alpha, fire);
            }
            case PYRO_LANCE -> {
                Vec3 front = at.lerp(end, smooth(0, 2.8F, t));
                Vec3 trail = at.lerp(end, smooth(2, 8, t) * .92F);
                billows(c, front, trail, .40F * size, .8F, -phase * 1.6F, alpha, fire, a.seed());
            }
            case PYRO_SHEPHERD -> {
                if (a.bound(c) instanceof Player p) end = PyroGeometry.castingHand(p, c.partial);
                Vec3 front = at.lerp(end, smooth(0, 9, t));
                Vec3 back = at.lerp(end, smooth(3, 13, t));
                flame(c, back, front, .21F, phase + hash(a.seed(), 2) * TWO_PI, alpha, fire);
            }
            case PYRO_TAKEOFF -> {
                float r = .32F + 1.6F * smooth(0, 10, t);
                curtain(c, at, r, 1.4F * (1 - t / 22), phase, alpha, fire);
                smoke(c, at, t, alpha * .55F, a.seed());
            }
            case PYRO_WAKE -> {
                float thinning = 1 - smooth(0, a.piece().ticks, t);
                Vec3 side = side(end.subtract(at).normalize()).scale(.28 + t * .015);
                Vec3 lift = UP.scale(t * .04);
                for (int sign : new int[]{-1, 1}) {
                    Vec3 offset = side.scale(sign).add(lift);
                    flame(c, end.add(offset), at.add(offset), .13F + size * .12F,
                            phase + sign * 1.7F, thinning * .42F, fire);
                }
                smoke(c, at.lerp(end, .5), t, thinning * .40F, a.seed());
            }
            case PYRO_PHOENIX -> {
                double sweep = clamp((size - 1) / .4F, 0, 1);
                double flap = (1 - sweep) * (.64 * sin(phase * 1.5F) + .12) - sweep * .14;
                Vec3 head = at.lerp(end, Math.min(.9, c.partial));
                float visible = .88F * (1 - smooth(1, 3, t));
                var bird = PhoenixGeometry.pose(head, end.subtract(at), flap, sweep, phase * 1.2);
                if (c.soft()) for (var edge : List.of(bird.leftWing(), bird.rightWing())) {
                    Vec3[] points = edge.toArray(Vec3[]::new);
                    for (int i = 1; i < points.length; i++)
                        c.tri(points[i - 1], points[i], head.subtract(end.subtract(at).normalize().scale(.9)),
                                fire.body, visible * .65F, visible * .45F, 0);
                    c.ribbon(points, .25F, fire.gold, visible * .8F, visible * .34F);
                }
                flame(c, bird.head(), bird.body().getLast(), .34F, phase, visible, fire);
                for (int i = 0; i < bird.feathers().size(); i += minimal() ? 3 : 1) {
                    var feather = bird.feathers().get(i);
                    flame(c, feather.getFirst(), feather.getLast(), .22F, phase + i * .83F, visible, fire);
                }
                for (int i = 0; i < bird.tail().size(); i++) {
                    var plume = bird.tail().get(i);
                    flame(c, plume.getFirst(), plume.getLast(), .24F, phase + i * 1.2F, visible, fire);
                }
                if (c.glow()) c.glow(bird.head(), .33F, fire.gold, visible * .4F);
            }
            case SUNFALL -> {
                float drop = StyleCasts.STARFALL_FALL;
                if (t < drop) fall(c, end.subtract(a.facing().scale(13)).add(0, 39, 0), end,
                        size, drop, t, phase, alpha, fire);
                else burst(c, end, size, t - drop, alpha * (1 - smooth(drop + 14, drop + 45, t)), phase, fire, a.seed());
            }
            case HEARTHFIRE -> {
                marker(c, at, size, -phase, alpha * .65F, fire);
                jet(c, at.add(0, .05, 0), at.add(0, 2.8, 0), .58F, phase, alpha, fire, minimal() ? 2 : 6, .75F);
            }
            default -> { }
        }
    }

    private static void fall(Canvas c, Vec3 sky, Vec3 ground, float radius, float duration,
                             float t, float phase, float alpha, Fire fire) {
        float p = clamp(t / duration, 0, 1);
        Vec3 head = sky.lerp(ground, p * p);
        Vec3 tail = head.lerp(sky, .28).add(0, .6, 0);
        marker(c, ground, radius, phase, alpha, fire);
        jet(c, head, tail, .55F + radius * .07F, phase, alpha, fire, minimal() ? 2 : 6, .85F);
        orb(c, head, .50F + radius * .08F, phase, alpha, fire);
    }

    private static void burst(Canvas c, Vec3 at, float radius, float t, float alpha, float phase, Fire fire, int seed) {
        float p = smooth(0, 12, t);
        float r = radius * (.13F + .87F * p);
        curtain(c, at, r, 1.2F + radius * .31F * sin(PI * (.12F + p * .75F)), phase, alpha, fire);
        float flare = 1 - smooth(3, 16, t);
        jet(c, at.add(0, .17, 0), at.add(0, 1 + radius * .90F * smooth(0, 8, t), 0),
                .6F + radius * .10F, -phase, alpha * flare, fire, minimal() ? 2 : 6, radius * .15F);
        if (c.glow()) c.glow(at.add(0, .5, 0), 1 + radius * .52F, fire.body, alpha * flare * .30F);
        sparks(c, at, radius, t, alpha * flare, fire, seed);
        smoke(c, at, t, alpha, seed);
    }

    private static void marker(Canvas c, Vec3 at, float radius, float phase, float alpha, Fire fire) {
        Vec3 floor = at.add(0, .045, 0);
        if (c.soft()) c.ring(floor, radius, .13F, fire.edge, alpha * .63F);
        if (c.glow()) {
            c.ring(floor, radius, .047F, fire.gold, alpha * .62F);
            for (int i = 0; i < 4; i++)
                c.ring(floor, UP, radius * .94F, .11F, phase + i * HALF_PI, phase + i * HALF_PI + .67F,
                        fire.body, alpha * .52F);
        }
    }

    private static void curtain(Canvas c, Vec3 at, float radius, float height, float phase, float alpha, Fire fire) {
        if (alpha < .005F || radius < .03F) return;
        int count = minimal() ? 7 : detail() ? 19 : 12;
        for (int i = 0; i < count; i++) {
            float angle = i * TWO_PI / count + phase * .12F;
            float tongue = .64F + .36F * sin(phase * 1.8F + i * 2.31F);
            Vec3 base = at.add(cos(angle) * radius, .06, sin(angle) * radius);
            Vec3 tip = base.add(-sin(angle) * height * .39, height * (.40 + tongue * .60), cos(angle) * height * .39);
            flame(c, base, tip, .18F + radius * .105F, phase + i * 1.57F, alpha, fire);
        }
    }

    private static void orb(Canvas c, Vec3 at, float radius, float phase, float alpha, Fire fire) {
        if (c.soft()) {
            c.shell(at, radius, radius * .93F, radius, PI, fire.body, alpha * .95F, .88F,
                    (angle, spin) -> .72F + .28F * sin(angle * 5 + spin * 5 - phase * 2), phase * .1F);
            for (int i = 0; i < 4; i++) {
                float angle = phase * .66F + i * HALF_PI;
                Vec3 rim = at.add(cos(angle) * radius * .65, -.16 * radius, sin(angle) * radius * .65);
                flame(c, rim, rim.add(cos(angle) * radius * .6, radius * (1.18 + .24 * sin(phase + i)),
                        sin(angle) * radius * .6), radius * .52F, phase + i, alpha * .8F, fire);
            }
        }
        if (c.glow()) {
            c.glow(at, radius * 1.8F, fire.body, alpha * .32F);
            c.glow(at, radius * .72F, fire.gold, alpha * .44F);
        }
    }

    private static void smoke(Canvas c, Vec3 at, float t, float alpha, int seed) {
        if (!c.soft() || !detail()) return;
        for (int i = 0; i < 3; i++) {
            float s = .30F + t * .045F;
            Vec3 puff = at.add((hash(seed, i) - .5) * t * .15, .4 + t * .12, (hash(seed, i + 8) - .5) * t * .15);
            c.cloud(puff, s, s * 1.25F, s, seed + i, t * .06F, alpha * .18F * smooth(1, 8, t), Shades.smoke(c, SOOT, 0));
        }
    }

    private static void sparks(Canvas c, Vec3 at, float radius, float t, float alpha, Fire fire, int seed) {
        if (!c.glow() || alpha < .01F || minimal()) return;
        float spread = smooth(0, 15, t);
        int count = detail() ? 18 : 9;
        for (int i = 0; i < count; i++) {
            float angle = hash(seed, i) * TWO_PI;
            float reach = (.28F + hash(seed, i + count) * .76F) * radius * spread;
            float lift = t * (.06F + .16F * hash(seed, i + count * 2));
            Vec3 ember = at.add(cos(angle) * reach, .3 + lift, sin(angle) * reach);
            c.glow(ember, .048F + .032F * hash(seed, i + count * 3), fire.gold, alpha * .75F);
        }
    }

    /** A stream of separate tongues that roll over one another and spread as they travel. */
    private static void billows(Canvas c, Vec3 at, Vec3 end, float width, float spread, float phase,
                                float alpha, Fire fire, int seed) {
        Vec3 path = end.subtract(at);
        double length = path.length();
        if (length < .03 || alpha < .01F) return;
        Vec3 axis = path.scale(1 / length), right = side(axis), up = axis.cross(right);
        int count = minimal() ? 4 : detail() ? 11 : 7;
        float stride = phase * .21F;
        for (int i = 0; i < count; i++) {
            float u = frac(stride + (i + hash(seed, i) * .7F) / count);
            float tip = Math.min(1, u + .28F + hash(seed, i + count) * .22F);
            float angle = i * 2.4F + phase * .34F;
            Vec3 radial = right.scale(cos(angle)).add(up.scale(sin(angle)));
            double roll = (.35 + hash(seed, i + count * 2) * .65) * spread;
            Vec3 root = at.lerp(end, u).add(radial.scale(roll * u * .57));
            Vec3 tipAt = at.lerp(end, tip).add(radial.scale(roll * tip)).add(0, width * tip * .5, 0);
            float strength = smooth(0, .14F, u + .07F) * (1 - smooth(.67F, 1, u));
            flame(c, root, tipAt, width * (.75F + .42F * u) + spread * u * .14F,
                    phase * 1.5F + i * 2.1F, strength * alpha, fire);
        }
    }

    private static void jet(Canvas c, Vec3 at, Vec3 end, float width, float phase, float alpha,
                            Fire fire, int count, float spread) {
        Vec3 axis = end.subtract(at).normalize();
        if (axis.lengthSqr() < .01) return;
        Vec3 right = side(axis), up = axis.cross(right);
        for (int i = 0; i < count; i++) {
            float angle = phase * .48F + i * TWO_PI / count;
            float flicker = .73F + .27F * sin(phase * 1.9F + i * 2.2F);
            Vec3 rim = right.scale(cos(angle) * spread * flicker).add(up.scale(sin(angle) * spread * flicker));
            Vec3 tip = at.lerp(end, i == 0 ? .61F + .22F * flicker : .53F + flicker * .47F).add(rim);
            flame(c, at.lerp(end, i == 0 ? 0 : .1F * i / count).add(rim.scale(.10)), tip,
                    width * (i == 0 ? 1.6F : 1.12F), phase + i * 2.3F, alpha, fire);
        }
    }

    /** The broad middle and shorter hot patch fold separately before the tip trails away. */
    private static void flame(Canvas c, Vec3 base, Vec3 tip, float width, float phase, float alpha, Fire fire) {
        Vec3 axis = tip.subtract(base).normalize();
        if (alpha < .005F || axis.lengthSqr() < .01) return;
        Vec3 right = side(axis), up = axis.cross(right);
        if (c.glow()) {
            Vec3 hot = base.lerp(tip, .18 + .09 * sin(phase));
            c.glow(hot, width * 1.40F, fire.body, alpha * .25F * cameraClearance(c, hot));
            return;
        }
        Vec3[] points = new Vec3[9], inner = new Vec3[9];
        float[] rim = new float[9], body = new float[9], heat = new float[9];
        float[] opacity = new float[9], hotOpacity = new float[9];
        for (int i = 0; i < points.length; i++) {
            float p = i / 8F;
            float curl = sin(phase - p * 6.4F), fold = cos(phase * .86F - p * 4.2F);
            Vec3 spine = base.lerp(tip, p);
            points[i] = spine.add(right.scale(curl * width * p * 2.4)).add(up.scale(fold * p * width * 1.7));
            inner[i] = points[i].add(right.scale(fold * width * p * .6));
            float profile = (.36F + 1.15F * sin(PI * p)) * (1 - .91F * smooth(.55F, 1, p));
            float lobe = .8F + .27F * sin(phase - p * 10.0F);
            rim[i] = width * profile * lobe * 1.85F;
            body[i] = width * profile * (.9F + .22F * fold) * 1.3F;
            heat[i] = width * profile * (.65F + .25F * curl);
            float visible = alpha * cameraClearance(c, points[i]);
            opacity[i] = visible * (.74F + .26F * smooth(0, .23F, p)) * (1 - smooth(.49F, 1, p));
            hotOpacity[i] = visible * smooth(0, .15F, p) * (1 - smooth(.28F, .72F, p))
                    * (.65F + .2F * fold);
        }
        c.ribbon(points, rim, fire.edge, opacity);
        c.ribbon(points, body, fire.body, opacity);
        c.ribbon(inner, heat, fire.gold, hotOpacity);
    }

    private static float cameraClearance(Canvas c, Vec3 point) {
        return smooth(.48F, 5.76F, (float) point.distanceToSqr(c.cx, c.cy, c.cz));
    }

    private static Vec3 side(Vec3 axis) { return (Math.abs(axis.y) > .93 ? new Vec3(0, 0, 1) : UP).cross(axis).normalize(); }
    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }
    private static boolean detail() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.ALL; }

    static boolean live() {
        var level = Minecraft.getInstance().level;
        return level != null && level.players().stream().anyMatch(p -> ClientFlightVisualState.isPyroFlight(p.getId()));
    }

    static void drawLive(Canvas c) {
        if (c.solid()) return;
        for (Player player : c.level.players()) {
            int id = player.getId();
            if (!ClientFlightVisualState.isPyroFlight(id) || !player.isAlive()
                    || player.position().distanceToSqr(c.camera()) > 96 * 96) continue;
            boolean posed = ClientFlightVisualState.isFlightActive(id), diving = ClientFlightVisualState.isPlunging(id);
            boolean boosted = ClientFlightVisualState.isPyroBoosting(id);
            boolean spooling = ClientFlightVisualState.isCharging(id);
            Fire fire = ClientFlightVisualState.isPyroBlue(id) ? BLUE : EMBER;
            Vec3 body = FlightVisualGeometry.bodyCenter(player.getPosition(c.partial));
            float clear = smooth(.55F, 2.35F, (float) c.camera().distanceTo(body));
            if (clear < .01F) continue;
            float phase = (c.level.getGameTime() % 2400 + c.partial) * .59F + id;
            if (ClientFlightVisualState.isDiving(id) && !diving) {
                float age = ClientFlightVisualState.phaseAge(id, c.level.getGameTime(), c.partial);
                curtain(c, body.add(0, -.58, 0), 1.45F - smooth(0, 8, age) * .86F,
                        1.48F, -phase, clear * .85F, fire);
                continue;
            }
            Vec3 exhaust = posed ? PyroFlightGeometry.axis(player, true, diving, c.partial).scale(-1) : UP.scale(-1);
            float length = boosted ? 6.4F : diving ? 4.1F : spooling ? 3.6F : posed ? 2.65F : 1.38F;
            Vec3 boots = PyroFlightGeometry.feet(player, posed, diving, c.partial);
            Vec3 lateral = side(exhaust);
            for (int hand : new int[]{-1, 1}) {
                Vec3 palm = PyroFlightGeometry.palm(player, posed, hand, diving, c.partial);
                Vec3 nozzle = boots.add(lateral.scale(hand * .11));
                jet(c, nozzle, nozzle.add(exhaust.scale(length)).add(lateral.scale(hand * (boosted ? .62 : .20))),
                        boosted ? .20F : .15F, phase + hand, .85F * clear, fire, minimal() ? 1 : 3, boosted ? .38F : .15F);
                Vec3 palmExhaust = exhaust.scale(length * .73).add(palm.subtract(body).scale(boosted ? 1.55 : .43));
                flame(c, palm, palm.add(palmExhaust), boosted ? .22F : .14F, phase + hand * 1.4F, clear, fire);
                if (spooling && c.glow()) c.glow(palm, .65F, fire.gold, clear * .55F);
            }
        }
    }
}
