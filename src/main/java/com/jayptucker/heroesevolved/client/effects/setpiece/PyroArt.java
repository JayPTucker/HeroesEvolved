package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.client.effects.PhoenixGeometry;
import com.jayptucker.heroesevolved.flight.FlightVisualGeometry;
import com.jayptucker.heroesevolved.network.ClientFlightVisualState;
import com.jayptucker.heroesevolved.pyro.PyroFlightGeometry;
import com.jayptucker.heroesevolved.pyro.PyroGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.entity.HumanoidArm;
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
            case PYRO_MANTLE -> {
                Vec3 body = a.at(c).add(0, .25, 0);
                float clear = smooth(.4F, 2.4F, (float) c.camera().distanceTo(body));
                curtain(c, body, size,
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
            case PYRO_JAB -> jab(c, a, at, end, size, t, phase, alpha, fire);
            case PYRO_KICK -> {
                // The rear leg sweeps from its own side across: the crescent unrolls behind the foot and flies out.
                Vec3 forward = flat(end.subtract(at)), right = new Vec3(-forward.z, 0, forward.x);
                int sweep = a.bound(c) instanceof Player p && p.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
                float open = smooth(0, PyroGeometry.KICK_OPEN, t), half = (float) Math.toRadians(PyroGeometry.KICK_HALF_ANGLE);
                float radius = .9F + ((float) PyroGeometry.KICK_REACH - .9F) * (1 - (1 - open) * (1 - open));
                crescent(c, at, forward, right, radius, sweep * half, -sweep * half, smooth(0, 3.2F, t),
                        .25F, phase, alpha * (1 - smooth(7, 12, t) * .55F), fire, a.seed());
            }
            case PYRO_FLIP -> {
                // The feet come up from under the body and over the head: an arc rises in front and flies out.
                Vec3 forward = flat(end.subtract(at));
                float open = smooth(0, PyroGeometry.KICK_OPEN, t);
                float radius = .8F + ((float) PyroGeometry.FLIP_REACH - .8F) * (1 - (1 - open) * (1 - open));
                crescent(c, at, forward, UP, radius, -.55F, 1.45F, smooth(0, 4, t), .23F, .6F, phase,
                        alpha * (1 - smooth(7, 14, t) * .55F), fire, a.seed());
            }
            case PYRO_SWEEP_KICK -> {
                // One leg swept through a full turn at ankle height: a wheel of fire rolls out across the ground.
                float spread = smooth(0, PyroGeometry.SWEEP_OPEN, t);
                float radius = .7F + (size - .7F) * (1 - (1 - spread) * (1 - spread));
                Vec3 floor = at.add(0, .12, 0);
                float start = hash(a.seed(), 1) * TWO_PI;
                crescent(c, floor, new Vec3(1, 0, 0), new Vec3(0, 0, 1), radius, start,
                        start + TWO_PI * smooth(0, PyroGeometry.SWEEP_SPIN, t), 1, .2F, phase, alpha, fire, a.seed());
                curtain(c, at, radius, .2F + .55F * (1 - smooth(3, 14, t)), phase, alpha * .7F, fire);
                marker(c, at, radius, -phase, alpha * .5F, fire);
                smoke(c, at, t, alpha * .7F, a.seed());
            }
            case PYRO_JET_STEP -> jetStep(c, a, at, end, size > 1.5F, t, phase, alpha, fire);
            case PYRO_COUNTER -> {
                // A blow passes through where you were: a flare of flame snaps round the body and lifts away.
                Vec3 body = a.at(c);
                float r = .45F + 1.55F * smooth(0, 5, t);
                float clear = smooth(.4F, 2.4F, (float) c.camera().distanceTo(body.add(0, 1, 0)));
                curtain(c, body, r, 1.7F * (1 - smooth(2, 12, t)) + .2F, -phase * 2, alpha * (.3F + .7F * clear), fire);
                if (c.glow()) c.glow(body.add(0, 1.05, 0), 1.4F * (1 - smooth(0, 8, t)) + .2F, fire.gold, alpha * .45F);
                sparks(c, body, 1.8F, t, alpha, fire, a.seed());
            }
            case FINISH_DRAGON -> dragon(c, a, size, t, phase, fire, blue);
            case FINISH_PHOENIX -> phoenix(c, a, size, t, phase, fire);
            case FINISH_HEARTH -> hearth(c, a, size, t, phase, fire);
            default -> { }
        }
    }

    private static final float[] WHITE_HOT = rgb(0xF4FBFF);

    /**
     * Phoenix Kick ({@code pyro/PyroFinishers}): a backflip kick's crescent rises on your side of the foe and throws it
     * up; an axe kick's crescent comes down over it from above; it lands in a column of flame and two wings of fire sweep
     * out and up from its shoulders. Origin the foe's feet where it stood, target yours; bound to the foe (it is lifted);
     * size the foe's height.
     */
    private static void phoenix(Canvas c, Active a, float height, float t, float phase, Fire fire) {
        Vec3 feet = a.origin();
        Vec3 toMe = flat(a.target().subtract(feet)), right = new Vec3(-toMe.z, 0, toMe.x);
        float h = Math.max(.6F, height), radius = .6F + h * .25F;
        float alpha = fade(t, a.piece().ticks, 1, 9);
        Vec3 body = a.bound(c) != null ? a.bound(c).getPosition(c.partial) : feet;
        int flip = com.jayptucker.heroesevolved.pyro.PyroFinishers.PHOENIX_FLIP, axe = com.jayptucker.heroesevolved.pyro.PyroFinishers.PHOENIX_AXE;
        int slam = com.jayptucker.heroesevolved.pyro.PyroFinishers.PHOENIX_SLAM;
        // The flip kick: a vertical crescent up the near side of the foe, unrolling from below.
        float up = smooth(0, flip + 1, t);
        crescent(c, feet.add(0, h * .5, 0), toMe, UP, radius, -.55F * PI, .45F * PI, up, .24F, .2F, phase,
                alpha * (1 - smooth(flip + 4, flip + 10, t)), fire, a.seed());
        // The axe kick: from over the top, down the near side, round the lifted body.
        if (t > axe - 3) {
            float down = smooth(axe - 3, axe + 1, t);
            crescent(c, body.add(0, h * .5, 0), toMe, UP, radius * 1.1F, .62F * PI, -.3F * PI, down, .26F, .2F, phase + 2,
                    alpha * (1 - smooth(slam + 3, slam + 9, t)), fire, a.seed() + 7);
        }
        // A trail of embers under the body while it is in the air.
        if (t > flip && t < slam && c.glow()) c.glow(body.add(0, h * .5, 0), .5F + h * .3F, fire.body, alpha * .25F);
        if (t < slam + 6) marker(c, feet, radius * 1.1F, phase, alpha * smooth(0, 3, t) * (1 - smooth(slam, slam + 6, t)), fire);
        if (t < slam) return;
        // The slam: a column, and two wings of fire sweeping out and up from the shoulders, then folding away.
        float flash = 1 - smooth(slam, slam + 3, t), rise = 1 - smooth(slam + 2, slam + 12, t);
        if (c.glow()) c.glow(feet.add(0, h * .55, 0), .3F + .8F * flash, fire.gold, alpha * flash * .55F);
        jet(c, feet.add(0, .12, 0), feet.add(0, .5 + h * 1.2 * smooth(slam, slam + 4, t), 0), .3F, -phase, alpha * rise, fire,
                minimal() ? 2 : 4, radius * .3F);
        float spread = smooth(slam, slam + 5, t), fold = 1 - smooth(slam + 6, slam + 13, t);
        Vec3 shoulders = feet.add(0, h * .75, 0);
        for (int sign : new int[]{-1, 1}) {
            Vec3 root = shoulders.add(right.scale(sign * .25));
            Vec3 tip = shoulders.add(right.scale(sign * (.4 + 1.6 * spread))).add(0, .3 + 1.1 * spread, 0).add(toMe.scale(-.4 * spread));
            Vec3 mid = root.lerp(tip, .5).add(0, .45 * spread, 0);
            billows(c, root, mid, .22F, .2F, phase + sign, alpha * fold, fire, a.seed() + sign);
            billows(c, mid, tip, .18F, .28F, phase - sign, alpha * fold * .9F, fire, a.seed() + 3 * sign);
            for (int f = 0; f < 3; f++) {
                Vec3 feather = root.lerp(tip, .4 + f * .22);
                flame(c, feather, feather.add(0, -.35 - f * .1, 0).add(toMe.scale(-.25)), .14F, phase + f, alpha * fold * .8F, fire);
            }
        }
        if (c.soft()) c.ring(feet.add(0, .05, 0), radius * (.6F + .9F * smooth(slam, slam + 8, t)), .1F, SOOT, alpha * rise * .5F);
        sparks(c, feet, radius * 1.5F, t - slam, alpha, fire, a.seed());
        smoke(c, feet, t - slam, alpha, a.seed());
    }

    /**
     * Hearth ({@code pyro/PyroFinishers}): tongues of flame close round the foe's head from every side, a ball of fire
     * squeezed smaller and hotter by the palms; at the clap it falls in on itself, and the foe goes up in a column of
     * flame. Origin the foe's feet, target yours, size its height.
     */
    private static void hearth(Canvas c, Active a, float height, float t, float phase, Fire fire) {
        Vec3 feet = a.origin();
        float h = Math.max(.6F, height);
        float alpha = fade(t, a.piece().ticks, 1, 9);
        int squeeze = com.jayptucker.heroesevolved.pyro.PyroFinishers.HEARTH_SQUEEZE, clap = com.jayptucker.heroesevolved.pyro.PyroFinishers.HEARTH_CLAP;
        int land = com.jayptucker.heroesevolved.pyro.PyroFinishers.HEARTH_LAND;
        Vec3 head = feet.add(0, h * .82, 0);
        float gather = smooth(0, 4, t), tight = smooth(2, squeeze, t), fall = smooth(clap, land, t);
        float beat = .06F * sin(t * 1.9F) * (1 - fall);
        float r = (1.25F - .65F * tight) * (1 - fall * .9F) + beat;
        if (t < land + 2) {
            float shown = alpha * gather * (1 - smooth(land, land + 2, t));
            int n = minimal() ? 10 : detail() ? 26 : 16;
            for (int i = 0; i < n; i++) {
                // Points spread evenly over a sphere, each a tongue licking inward toward the head.
                float y = 1 - 2 * (i + .5F) / n, ring = sqrt(Math.max(0, 1 - y * y)), ang = i * 2.39996F + phase * .08F;
                Vec3 dir = new Vec3(cos(ang) * ring, y, sin(ang) * ring);
                Vec3 base = head.add(dir.scale(r));
                Vec3 tip = head.add(dir.scale(r * (.42F + .12F * sin(phase * 2 + i)))).add(0, .12, 0);
                flame(c, base, tip, .16F + .06F * tight, phase + i * 1.3F, shown * (.6F + .4F * tight), fire);
            }
            orb(c, head, .12F + .2F * tight * (1 - fall), phase, shown * (.4F + .6F * tight), fire);
            if (c.glow()) c.glow(head, r * 1.6F, fire.body, shown * (.15F + .25F * tight));
        }
        if (t < land) return;
        float flash = 1 - smooth(land, land + 3, t), rise = 1 - smooth(land + 3, land + 12, t);
        if (c.glow()) {
            c.glow(head, .4F + 1.1F * flash, WHITE_HOT, alpha * flash * .5F);
            c.glow(feet.add(0, h * .5, 0), .9F + h * .5F, fire.gold, alpha * flash * .35F);
        }
        jet(c, feet.add(0, .1, 0), feet.add(0, .6 + (h + 1.8) * smooth(land, land + 4, t), 0), .4F, -phase, alpha * rise, fire,
                minimal() ? 3 : 6, .25F);
        curtain(c, feet, .55F + .5F * smooth(land, land + 6, t), 1.1F * rise + .2F, phase, alpha * rise, fire);
        if (c.soft()) c.ring(feet.add(0, .05, 0), .6F + .9F * smooth(land, land + 8, t), .12F, SOOT, alpha * rise * .55F);
        sparks(c, head, 1.4F, t - land, alpha, fire, a.seed());
        smoke(c, feet, t - land, alpha, a.seed());
    }

    /**
     * Dragon Coil (the finisher, {@code pyro/DragonCoil}): three kicked crescents wrap the foe low, at the chest and at
     * the head, each unrolling behind the foot that threw it; then they join tail to head into one coil of flame that
     * winds up the body, a head rears over the foe and bites down, a flash, and the coil lifts away as embers. Origin
     * the foe's feet, target the finisher's feet, size the foe's height.
     */
    private static void dragon(Canvas c, Active a, float height, float t, float phase, Fire fire, boolean blue) {
        Vec3 feet = a.origin();
        Vec3 forward = flat(a.target().subtract(feet)), right = new Vec3(-forward.z, 0, forward.x);
        float h = Math.max(.6F, height), radius = .55F + h * .22F;
        float alpha = fade(t, a.piece().ticks, 1, 10);
        float knot = smooth(17, 21, t);
        // The kicks: a low hook, a back kick at the chest, a jumping roundhouse at the head, each from the other side.
        float[][] kicks = {{1, .22F, -.4F, 1.1F}, {7, .55F, .6F, -.9F}, {13, .9F, -.7F, .9F}};
        for (float[] kick : kicks) {
            float start = kick[0];
            if (t < start) continue;
            Vec3 center = feet.add(0, h * kick[1], 0);
            float reveal = smooth(start, start + 4, t);
            float bright = alpha * (1 - knot) * (1 - smooth(start + 10, start + 16, t) * .45F);
            crescent(c, center, forward, right, radius * (1.15F - .15F * reveal), kick[2] * PI, kick[3] * PI, reveal,
                    .22F, .18F, phase, bright, fire, a.seed() + (int) start);
        }
        if (t < 26) marker(c, feet, radius * 1.15F, phase, alpha * smooth(0, 3, t) * (1 - smooth(22, 26, t)), fire);
        if (t < 17) return;

        // The coil: tongues of flame wound up the body, trailing back along the turn like scales.
        float bite = smooth(21.5F, 23, t), lift = Math.max(0, t - 24) * .09F;
        float coil = alpha * (1 - smooth(24, 32, t));
        float r = radius * (1.05F - .25F * smooth(20, 23, t));
        int count = minimal() ? 8 : detail() ? 18 : 13;
        float turns = 2.25F, start = -.4F * PI + phase * .04F;
        Vec3[] lip = new Vec3[count];
        float[] lipAlpha = new float[count];
        int shown = 0;
        for (int i = 0; i < count; i++) {
            float u = i / (count - 1F);
            if (u > knot) break;
            float angle = start + u * turns * TWO_PI;
            Vec3 radial = forward.scale(cos(angle)).add(right.scale(sin(angle)));
            Vec3 along = forward.scale(-sin(angle)).add(right.scale(cos(angle)));
            Vec3 base = feet.add(radial.scale(r)).add(0, h * (.12 + .88 * u) + lift * (1 + u), 0);
            Vec3 tip = base.subtract(along.scale(.55)).add(radial.scale(.12)).add(0, .14 + lift, 0);
            float taper = .45F + .55F * sin(PI * Math.min(1, u * 1.15F));
            flame(c, base, tip, .2F * taper, phase + i * 1.9F, coil * taper, fire);
            lip[i] = base;
            lipAlpha[i] = coil * taper * cameraClearance(c, base);
            shown++;
        }
        if (c.glow() && shown >= 2) {
            Vec3[] points = java.util.Arrays.copyOf(lip, shown);
            float[] wide = new float[shown], thin = new float[shown], halo = new float[shown], core = new float[shown];
            for (int i = 0; i < shown; i++) {
                wide[i] = .2F; thin[i] = .04F;
                halo[i] = lipAlpha[i] * .25F; core[i] = lipAlpha[i] * .8F;
            }
            c.ribbon(points, wide, fire.body, halo);
            c.ribbon(points, thin, fire.gold, core);
        }

        // The head rears over the foe, jaws open, and bites down at the chest.
        if (t < 30 && knot > .6F) {
            float topAngle = start + turns * TWO_PI;
            Vec3 top = feet.add(forward.scale(cos(topAngle) * r)).add(right.scale(sin(topAngle) * r)).add(0, h, 0);
            Vec3 reared = feet.add(forward.scale(.25)).add(0, h + .55 + .45 * smooth(18, 21, t), 0);
            Vec3 chest = feet.add(0, h * .62, 0);
            Vec3 head = reared.lerp(chest.add(forward.scale(.2)), bite).add(0, lift, 0);
            float shown2 = coil * smooth(17.5F, 19.5F, t);
            billows(c, top, head, .2F, .25F, phase, shown2, fire, a.seed());
            orb(c, head, .26F, phase, shown2, fire);
            Vec3 jaw = chest.subtract(head);
            jaw = jaw.lengthSqr() < 1e-3 ? forward.scale(-1) : jaw.normalize();
            Vec3 across = side(jaw), up = jaw.cross(across).normalize();
            float open = .55F * (1 - bite) + .08F;
            for (int sign : new int[]{-1, 1}) {
                Vec3 tipAt = head.add(jaw.scale(.6)).add(up.scale(sign * open * .45));
                flame(c, head, tipAt, .14F, phase + sign, shown2, fire);
            }
            if (c.glow()) for (int sign : new int[]{-1, 1}) {
                Vec3 eye = head.add(across.scale(sign * .13)).add(up.scale(.12)).subtract(jaw.scale(.05));
                c.glow(eye, .08F, blue ? WHITE_HOT : fire.gold, shown2);
                c.glow(eye, .03F, WHITE_HOT, shown2);
            }
        }
        // The bite: a flash at the chest and a burst of fire round the feet.
        if (t >= 22) {
            // Kept to the foe's own size: a camera a couple of blocks off (first person, no finisher camera) must still
            // see the body go down through it.
            // (Not burst(): its low ring of tongues reads as a flat orange sheet from the finisher camera's side view.)
            float flash = 1 - smooth(22, 25, t), rise = 1 - smooth(24, 31, t);
            if (c.glow()) c.glow(feet.add(0, h * .62, 0), .25F + .7F * flash, fire.gold, alpha * flash * .55F);
            jet(c, feet.add(0, .15, 0), feet.add(0, .4 + h * .9 * smooth(22, 26, t), 0), .3F, -phase, alpha * rise, fire,
                    minimal() ? 2 : 4, radius * .3F);
            if (c.soft()) c.ring(feet.add(0, .05, 0), radius * (.6F + .9F * smooth(22, 28, t)), .1F, SOOT, alpha * rise * .5F);
            sparks(c, feet, radius * 1.4F, t - 22, alpha, fire, a.seed());
            smoke(c, feet, t - 22, alpha, a.seed());
        }
    }

    /** A fist of flame: a muzzle flare at the knuckles, a tight head with a combed tail, then a splash where it lands. */
    private static void jab(Canvas c, Active a, Vec3 at, Vec3 end, float size, float t, float phase, float alpha, Fire fire) {
        boolean blazing = size > 1.5F;
        float s = blazing ? 1.75F : 1, flight = PyroGeometry.JAB_FLIGHT * (blazing ? 1.35F : 1);
        Vec3 path = end.subtract(at);
        if (path.lengthSqr() < .01) return;
        Vec3 aim = path.normalize();
        float travel = smooth(0, flight, t);
        Vec3 head = at.lerp(end, travel);
        Vec3 tail = at.lerp(end, smooth(1.1F, flight + 4.5F, t) * .97F);
        float flying = alpha * (1 - smooth(flight, flight + 1.5F, t));
        float flare = alpha * (1 - smooth(0, 3, t));
        flame(c, at, at.add(aim.scale(.55 * s)), .13F * s, phase, flare, fire);
        billows(c, head, tail, .15F * s, .2F * s, -phase * 1.3F, flying, fire, a.seed());
        orb(c, head, .15F * s, phase, flying, fire);
        // The splash opens back toward the thrower and lifts.
        float splash = smooth(flight - .5F, flight + 1.5F, t) * (1 - smooth(flight + 1.5F, 9, t));
        if (splash > .01F) {
            Vec3 right = side(aim), up = aim.cross(right);
            int count = minimal() ? 3 : detail() ? 8 : 5;
            for (int i = 0; i < count; i++) {
                float angle = i * TWO_PI / count + hash(a.seed(), i) * .8F;
                Vec3 out = right.scale(cos(angle)).add(up.scale(sin(angle))).scale(.8).add(aim.scale(-.55)).add(0, .25, 0);
                flame(c, end, end.add(out.scale((.45 + .35 * hash(a.seed(), i + 9)) * s * (.4 + .6 * splash))),
                        .12F * s, phase + i * 1.7F, alpha * splash, fire);
            }
            if (c.glow()) c.glow(end, .55F * s, fire.gold, alpha * splash * .5F);
        }
    }

    /**
     * A kicked crescent: tongues of flame along an arc from angle {@code from} to {@code to} in the plane of
     * {@code forward} and {@code lateral}, unrolled to {@code reveal} of its length. Each tongue trails back the way
     * the foot came, and a bright lip runs along the leading edge.
     */
    private static void crescent(Canvas c, Vec3 center, Vec3 forward, Vec3 lateral, float radius, float from, float to,
                                 float reveal, float width, float phase, float alpha, Fire fire, int seed) {
        crescent(c, center, forward, lateral, radius, from, to, reveal, width, .16F, phase, alpha, fire, seed);
    }

    /** {@code thickness}: how far the tongues stray out of the arc's plane, so an arc seen edge-on still has body. */
    private static void crescent(Canvas c, Vec3 center, Vec3 forward, Vec3 lateral, float radius, float from, float to,
                                 float reveal, float width, float thickness, float phase, float alpha, Fire fire, int seed) {
        if (alpha < .01F || reveal <= .01F) return;
        int count = minimal() ? 6 : detail() ? 16 : 11;
        Vec3 normal = forward.cross(lateral).normalize();
        Vec3[] lip = new Vec3[count];
        float[] lipAlpha = new float[count];
        int shown = 0;
        for (int i = 0; i < count; i++) {
            float u = i / (count - 1F);
            if (u > reveal) break;
            float angle = from + (to - from) * u;
            Vec3 radial = forward.scale(cos(angle)).add(lateral.scale(sin(angle)));
            Vec3 back = forward.scale(sin(angle)).add(lateral.scale(-cos(angle))).scale(Math.signum(to - from));
            float taper = .35F + .65F * sin(PI * u);
            // The newest tongues, just thrown from the foot, burn hottest.
            float fresh = .7F + .3F * (1 - smooth(0, .3F, reveal - u));
            // A thick arc (the flip kick, seen edge-on from behind) scatters its roots across the plane as well.
            double stray = thickness > .3F ? thickness * .6 * (hash(seed, i + 40) - .5) : 0;
            Vec3 base = center.add(radial.scale(radius)).add(normal.scale(stray));
            Vec3 tip = base.add(back.scale(.5 + .55 * taper)).add(radial.scale(-.2))
                    .add(normal.scale(thickness * sin(phase * 1.3F + i * 2.1F)));
            flame(c, base, tip, width * (.55F + .65F * taper), phase + i * 1.9F + hash(seed, i), alpha * taper * fresh, fire);
            lip[i] = base.add(radial.scale(.06));
            lipAlpha[i] = alpha * taper * fresh;
            shown++;
        }
        if (c.glow() && shown >= 2) {
            Vec3[] points = java.util.Arrays.copyOf(lip, shown);
            float[] halo = new float[shown], core = new float[shown], wide = new float[shown], thin = new float[shown];
            for (int i = 0; i < shown; i++) {
                float clear = cameraClearance(c, points[i]);
                halo[i] = lipAlpha[i] * .28F * clear;
                core[i] = lipAlpha[i] * .85F * clear;
                wide[i] = width * .9F;
                thin[i] = width * .16F;
            }
            c.ribbon(points, wide, fire.body, halo);
            c.ribbon(points, thin, fire.gold, core);
        }
    }

    /**
     * A jet step: flame blasts from the feet and the trailing palm against the direction of travel, leaves two
     * scorched skid marks and a thin wake. A backflip (flip) fires both feet down and forward instead.
     */
    private static void jetStep(Canvas c, Active a, Vec3 at, Vec3 end, boolean flip, float t, float phase, float alpha, Fire fire) {
        Vec3 path = end.subtract(at);
        if (path.lengthSqr() < .01) return;
        Vec3 dir = path.normalize(), right = side(dir);
        Vec3 feet = a.bound(c) instanceof Player p ? p.getPosition(c.partial) : at.lerp(end, smooth(0, 5, t));
        float pushing = alpha * (1 - smooth(3, 8, t));
        Vec3 exhaust = flip ? dir.scale(-.55).add(0, -.85, 0).normalize() : dir.scale(-1).add(0, -.12, 0).normalize();
        for (int foot : new int[]{-1, 1}) {
            Vec3 nozzle = feet.add(right.scale(foot * .12)).add(0, .12, 0);
            jet(c, nozzle, nozzle.add(exhaust.scale(1.25 + .25 * sin(phase + foot))), .15F, phase + foot,
                    pushing, fire, minimal() ? 1 : 3, .16F);
        }
        if (!flip) {
            // The far arm is flung straight out against the dash: the palm is at shoulder height, an arm's length out.
            Vec3 palm = feet.add(0, 1.36, 0).add(dir.scale(-1.0));
            flame(c, palm, palm.add(dir.scale(-1.05)).add(0, .1, 0), .15F, phase * 1.2F, pushing, fire);
        }
        // The launch blast where the dodge began, spreading and rising away.
        float launch = alpha * (1 - smooth(1, 9, t));
        billows(c, at.add(0, .15, 0), at.add(exhaust.scale(1.8)).add(0, .35, 0), .22F, .55F, phase, launch, fire, a.seed());
        if (c.soft() && !flip) {
            // Two scorched skid marks along the ground, drawn dark with normal blending.
            float scorch = alpha * .55F * (1 - smooth(8, 14, t));
            for (int foot : new int[]{-1, 1}) {
                Vec3 offset = right.scale(foot * .17).add(0, .03, 0);
                Vec3[] marks = new Vec3[6];
                for (int i = 0; i < marks.length; i++)
                    marks[i] = at.lerp(end, .7 * i / (marks.length - 1.0) * smooth(0, 4, t)).add(offset);
                c.ribbon(marks, .06F, SOOT, scorch * .4F, scorch);
            }
        }
        smoke(c, at, t, alpha * .8F, a.seed());
    }

    private static Vec3 flat(Vec3 v) {
        Vec3 level = new Vec3(v.x, 0, v.z);
        return level.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : level.normalize();
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
            // A puff rising through the camera would fill the view; it clears near the eye like the flames do.
            c.cloud(puff, s, s * 1.25F, s, seed + i, t * .06F, alpha * .18F * smooth(1, 8, t) * cameraClearance(c, puff),
                    Shades.smoke(c, SOOT, 0));
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

    /**
     * Pyrokinesis's own ember flame, for fire that is the power but not a player's move: an overload's wildfire
     * ({@code OverloadArt}). Draw it in the soft and glow passes only.
     */
    static void emberFlame(Canvas c, Vec3 base, Vec3 tip, float width, float phase, float alpha) {
        flame(c, base, tip, width, phase, alpha, EMBER);
    }

    /** The ember palette's edge, body and gold, for drawing that sits beside {@link #emberFlame}. */
    static float[][] emberColors() { return new float[][] {EMBER.edge(), EMBER.body(), EMBER.gold()}; }

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

    // ------------------------------------------------------------------
    // Ultimates (pyro/PyroUltimates)
    // ------------------------------------------------------------------

    private static float clock(Canvas c) { return (c.level.getGameTime() % 2400 + c.partial) * .33F; }

    /**
     * Rising Sun ({@code PyroUltimates.RisingSun}): the jet-step in, a crescent off the rising knee, a jet of fire from
     * each fist of the flurry, boot jets as the firebender climbs, a wheel of flame round the kicking leg turning blue,
     * the blue axe kick's crescent and the streak it drives down, the dome of fire where the foe lands, and the sun
     * that rises out of it.
     */
    static void sun(Canvas c, Active a, float t) {
        if (c.solid() || UltArt.cut(c, a)) return;
        Player runner = a.bound(c) instanceof Player p ? p : null;
        var foe = UltArt.foe(c, a);
        Vec3 base = a.target(), facing = a.facing(), side = UltArt.side(facing);
        float phase = clock(c);
        int seed = a.seed() * 13 + 3;
        float fh = foe != null ? foe.getBbHeight() : 1.8F;
        Vec3 foeAt = foe != null ? foe.getPosition(c.partial) : base.add(0, com.jayptucker.heroesevolved.pyro.PyroUltimates.RisingSun.height(t), 0);
        Vec3 chest = foeAt.add(0, fh * .6, 0);
        int knee = com.jayptucker.heroesevolved.pyro.PyroUltimates.RisingSun.KNEE, flurry = com.jayptucker.heroesevolved.pyro.PyroUltimates.RisingSun.FLURRY,
                every = com.jayptucker.heroesevolved.pyro.PyroUltimates.RisingSun.EVERY, blows = com.jayptucker.heroesevolved.pyro.PyroUltimates.RisingSun.BLOWS,
                rise = com.jayptucker.heroesevolved.pyro.PyroUltimates.RisingSun.RISE, wheel = com.jayptucker.heroesevolved.pyro.PyroUltimates.RisingSun.WHEEL,
                axe = com.jayptucker.heroesevolved.pyro.PyroUltimates.RisingSun.AXE, impact = com.jayptucker.heroesevolved.pyro.PyroUltimates.RisingSun.IMPACT;
        // The jet-step: fire blasting back from the feet as the body crosses the gap.
        if (t < knee + 2 && runner != null) {
            Vec3 feet = runner.getPosition(c.partial).add(0, .15, 0);
            jet(c, feet, feet.subtract(facing.scale(1.6)).add(0, .2, 0), .22F, phase, UltArt.window(t, 0, knee + 2, 1, 3), EMBER, 3, .12F);
        }
        // The rising knee: a crescent of flame swept up under the foe.
        if (t >= knee && t < knee + 12) {
            float k = smooth(knee, knee + 4, t), f = 1 - smooth(knee + 4, knee + 12, t);
            crescent(c, base.add(0, .9, 0).subtract(facing.scale(.3)), UP, facing, 1.1F, -PI * .6F, PI * .4F, k, .22F, phase, f, EMBER, seed);
            if (c.glow()) c.glow(chest, 1.1F * f, EMBER.gold, f * .5F);
        }
        // The flurry: each blow a jet of fire from the fist that threw it, a burst where it lands.
        for (int i = 0; i < blows; i++) {
            float at = flurry + i * every, age = t - at;
            if (age < 0 || age > 10) continue;
            float f = 1 - smooth(3, 10, age);
            Vec3 fist = runner != null ? PyroGeometry.hand(runner, c.partial, i % 2 == 0) : chest.subtract(facing.scale(1));
            Vec3 tip = fist.lerp(chest, smooth(0, 2, age));
            billows(c, fist, tip.add(facing.scale(.4)), .16F, .22F, phase + i, f, EMBER, seed + i);
            if (age > 1.5F) burst(c, chest.subtract(0, .4, 0), 1.1F, age - 1.5F, f * .7F, phase, EMBER, seed + i * 7);
        }
        // Boot jets while they climb above the foe and hang there; a wheel of flame round the kicking leg that turns blue.
        if (runner != null && t > rise && t < axe + 2) {
            float f = UltArt.window(t, rise, axe + 2, 2, 2);
            Vec3 feet = runner.getPosition(c.partial).add(0, .1, 0);
            for (int s = -1; s <= 1; s += 2) {
                Vec3 boot = feet.add(side.scale(.14 * s));
                jet(c, boot, boot.add(0, -1.3, 0), .14F, phase + s, f, t > wheel + 4 ? BLUE : EMBER, 3, .08F);
            }
            if (t > wheel) {
                float spin = (t - wheel) * .9F;
                Fire fire = t > wheel + 5 ? BLUE : EMBER;
                Vec3 hip = runner.getPosition(c.partial).add(0, .7, 0);
                crescent(c, hip, facing, UP, 1, spin, spin + PI * 1.4F, 1, .2F, .2F, phase, f, fire, seed + 40);
                if (c.glow()) c.glow(hip, .9F, fire.gold, f * .45F);
            }
        }
        // The axe kick: a blue crescent swept down onto the foe, and the streak it drives it down along.
        if (t >= axe && t < impact + 8) {
            float k = smooth(axe, axe + 3, t), f = 1 - smooth(impact, impact + 8, t);
            Vec3 top = base.add(0, com.jayptucker.heroesevolved.pyro.PyroUltimates.RisingSun.ABOVE + .6, 0).subtract(facing.scale(.6));
            crescent(c, top.lerp(chest, .5), facing, UP, 2.2F, PI * .1F, PI * 1.2F, k, .32F, .24F, phase, f, BLUE, seed + 60);
            billows(c, base.add(0, com.jayptucker.heroesevolved.pyro.PyroUltimates.RisingSun.LIFT + 1, 0), foeAt.add(0, fh * .5, 0), .3F, .3F, phase, f, BLUE, seed + 61);
        }
        // The landing: a dome of fire blooms, then a sun rises out of the crater, rays turning slowly.
        if (t >= impact) {
            float age = t - impact, life = a.piece().ticks - impact, f = fade(age, life, 0, 18);
            burst(c, base, 5, age, f, phase, EMBER, seed + 80);
            if (age < 10) burst(c, base, 2.4F, age * 1.4F, f * (1 - age / 10), phase, BLUE, seed + 81);
            float up = smooth(4, 30, age);
            Vec3 sun = base.add(facing.scale(1.6)).add(0, -1.2 + 3.2 * up, 0);
            float r = .6F + 1.5F * up;
            if (c.soft()) c.shell(sun, r, r, r, PI, EMBER.body, f * .55F * up, .7F,
                    (theta, phi) -> .8F + .2F * sin(theta * 6 + phase), phase * .1F);
            if (c.glow()) {
                c.glow(sun, r * 2.4F, EMBER.gold, f * .45F * up);
                c.glow(sun, r * 1.1F, WHITE_HOT, f * .6F * up);
                Vec3 face = facing.scale(-1);
                for (int i = 0; i < 12; i++)
                    c.spoke(sun, face, i * TWO_PI / 12 + phase * .05F, r * 1.15F, r * (1.8F + .4F * sin(phase + i)), .08F, EMBER.gold, f * .5F * up);
            }
        }
    }

    /**
     * Living Flame, one second of it (re-sent each second; the second in the seed, 0 the start): tongues of fire over the
     * whole body, a blue heart at the chest, a ring of heat at the feet. The first second roars up from the ground.
     */
    static void livingFlame(Canvas c, Active a, float t) {
        if (c.solid() || UltArt.cut(c, a)) return;
        var body = a.bound(c);
        if (body == null) return;
        int second = a.seed();
        boolean last = second >= com.jayptucker.heroesevolved.pyro.PyroUltimates.LivingFlame.TICKS / 20 - 1;
        float f = Math.clamp(t / 2 + (second > 0 ? .5F : 0), 0, 1) * (last ? 1 - smooth(8, 22, t) : Math.clamp((a.piece().ticks - t) / 2 + .3F, 0, 1));
        float phase = clock(c);
        Vec3 feet = body.getPosition(c.partial);
        float h = body.getBbHeight();
        float grow = second == 0 ? smooth(0, 10, t) : 1;
        // Tongues licking up from shoulders, arms, hips and back, the body's silhouette in flame.
        int n = UltArt.minimal() ? 6 : 14;
        for (int i = 0; i < n; i++) {
            float ang = i * TWO_PI / n + hash(i, 3) * .4F;
            float y = (.15F + .75F * hash(i, 7)) * h * grow;
            Vec3 root = feet.add(cos(ang) * .3, y, sin(ang) * .3);
            Vec3 tip = root.add(cos(ang) * .25, .5 + .35 * sin(phase + i), sin(ang) * .25);
            flame(c, root, tip, .14F, phase + i * 1.7F, f, EMBER);
        }
        curtain(c, feet, .45F, .8F * grow, phase, f * .8F, EMBER);
        Vec3 heart = feet.add(0, h * .62, 0);
        if (c.glow()) {
            c.glow(heart, .5F, BLUE.gold, f * .7F);
            c.glow(heart, 1.4F, EMBER.body, f * .25F);
            c.ring(feet.add(0, .05, 0), UP, 1.1F + .1F * sin(phase), .05F, 0, TWO_PI, EMBER.gold, f * .5F);
        }
        if (second == 0) burst(c, feet, 2.4F, t * 1.4F, 1 - smooth(4, 20, t), phase, EMBER, a.seed() + 5);
    }

    /** A flare off Living Flame: a jet thrown at someone who struck (seed 0) or a shot burned up (seed 1). */
    static void flare(Canvas c, Active a, float t) {
        if (c.solid()) return;
        float f = fade(t, a.piece().ticks, 0, 7), phase = clock(c);
        Vec3 from = a.origin(), to = a.target();
        if (a.seed() == 1) {
            orb(c, to, .18F * (1 - smooth(0, 8, t)), phase, f, EMBER);
            sparks(c, to, .7F, t, f, EMBER, (int) (to.x * 31));
            return;
        }
        billows(c, from, from.lerp(to, smooth(0, 3, t)), .2F, .3F, phase, f, EMBER, (int) (to.z * 17));
        if (t > 2) burst(c, to.subtract(0, .5, 0), 1.2F, t - 2, f, phase, EMBER, (int) (to.x * 11));
    }

    /** Living Flame's end: the heat taken in goes out in a ring of fire (the scale its radius). */
    static void nova(Canvas c, Active a, float t) {
        if (c.solid()) return;
        float life = a.piece().ticks, f = fade(t, life, 0, 12), phase = clock(c);
        float r = a.scale() * smooth(0, 10, t);
        curtain(c, a.origin(), .5F + r, 1.2F + .6F * (1 - smooth(0, life, t)), phase, f, EMBER);
        burst(c, a.origin(), 2, t, f, phase, BLUE, a.seed());
        if (c.glow()) c.ring(a.origin().add(0, .1, 0), UP, .5F + r, .12F, 0, TWO_PI, EMBER.gold, f * .7F);
    }

    /**
     * Bonfire Vigil, one second (re-sent each second; the second in the seed, 0 the lighting): a pyre of flame tongues
     * standing taller than a person, a hot heart, embers drifting out over everyone it shelters, and a warm ring on the
     * ground where its shelter ends (the scale its radius).
     */
    static void bonfire(Canvas c, Active a, float t) {
        if (c.solid()) return;
        int second = a.seed();
        boolean last = second >= com.jayptucker.heroesevolved.pyro.PyroUltimates.BonfireVigil.TICKS / 20 - 1;
        float f = Math.clamp(t / 2 + (second > 0 ? .5F : 0), 0, 1) * (last ? 1 - smooth(6, 22, t) : Math.clamp((a.piece().ticks - t) / 2 + .3F, 0, 1));
        float phase = clock(c), grow = second == 0 ? smooth(0, 16, t) : 1;
        Vec3 at = a.origin();
        jet(c, at.add(0, .1, 0), at.add(0, 1.2 + 3.8 * grow, 0), .55F, phase, f, EMBER, UltArt.minimal() ? 3 : 7, .5F);
        curtain(c, at, .9F * grow + .2F, 2.2F * grow, phase, f, EMBER);
        orb(c, at.add(0, .8, 0), .45F * grow, phase, f, EMBER);
        float radius = a.scale();
        if (c.glow()) {
            c.glow(at.add(0, 1.5, 0), 4.5F * grow, EMBER.body, f * .22F);
            c.ring(at.add(0, .05, 0), UP, radius, .06F, 0, TWO_PI, EMBER.gold, f * .35F);
            int embers = UltArt.minimal() ? 10 : 34;
            for (int i = 0; i < embers; i++) {
                float k = frac((c.time() * .006F) + hash(second, i) + i / (float) embers);
                float ang = hash(i, 11) * TWO_PI + k * 1.3F, r = .4F + k * radius * .9F;
                Vec3 p = at.add(cos(ang) * r, 1.5 + 4 * sin(k * PI), sin(ang) * r);
                c.glow(p, .06F, i % 4 == 0 ? WHITE_HOT : EMBER.gold, f * .8F * (1 - k * .6F));
            }
        }
        smoke(c, at.add(0, 3, 0), 6 + t * .3F, f * .7F, second);
    }

    /** A friend wrapped in Bonfire Vigil's cocoon of flame (bound to them; scale their height), closing and healing. */
    static void cocoon(Canvas c, Active a, float t) {
        if (c.solid()) return;
        var body = a.bound(c);
        Vec3 feet = body != null ? body.getPosition(c.partial) : a.origin();
        float life = a.piece().ticks, f = fade(t, life, 2, 10), phase = clock(c), h = Math.max(.6F, a.scale());
        float close = 1 - smooth(0, 10, t) * .55F;
        int n = UltArt.minimal() ? 5 : 9;
        for (int i = 0; i < n; i++) {
            float ang = i * TWO_PI / n + phase * .05F;
            Vec3 root = feet.add(cos(ang) * .8 * close, .05, sin(ang) * .8 * close);
            Vec3 tip = feet.add(cos(ang + .6F) * .2, h * 1.05, sin(ang + .6F) * .2);
            flame(c, root, tip, .16F, phase + i, f * .9F, EMBER);
        }
        if (c.glow()) c.glow(feet.add(0, h * .55, 0), h * .7F, EMBER.gold, f * .5F * smooth(6, 16, t));
    }
}
