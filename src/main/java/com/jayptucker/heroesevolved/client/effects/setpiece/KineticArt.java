package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.telekinesis.KineticGeometry;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/**
 * Telekinesis, the far hand: <b>a hand of bent air does at a distance what the caster's hand does</b>. A ripple leaves
 * the visible hand (camera space in first person), and where the force lands a clear, glassy hand blooms, sized to what
 * it holds, with an orchid rim: it shoves with a palm, holds with a claw from above, cups shots, plucks weapons, pins
 * bodies to walls, cradles a levitating caster and drives a hauled foe out of the sky. Every hit answers with dent rings
 * on the struck face's plane, a flash and grit; nothing here is conjured matter, only real things moved.
 *
 * <p>Primitives: {@link #hand} (palm, four jointed fingers and a thumb, curled and spread), {@link #claw} (five fingers
 * closing over a body from above), {@link #ripple} (the force leaving the hand), {@link #dent} (pressure rings on a
 * plane), {@link #streaks} (air pulled after anything fast) and {@link #grit} (chips and dust thrown off a face).
 * Every glowing stroke is three layers (deep halo, orchid body, pale core); the refraction body is drawn dark in the soft
 * pass so the hand reads at noon. Palette: {@code 2A1030} plum shadow, {@code 8A2C6E} deep orchid (halo), {@code D65AA8}
 * orchid (body), {@code FFB3DE} blush (rim, glints), {@code FFF3FA} core, and dust {@code A8946E}.</p>
 */
final class KineticArt {
    static final float[] SHADOW = rgb(0x2A1030), DEEP = rgb(0x8A2C6E), ORCHID = rgb(0xD65AA8), BLUSH = rgb(0xFFB3DE),
            CORE = rgb(0xFFF3FA), DUST = rgb(0xA8946E), STONE = rgb(0x7C7468);
    /** Finger offsets across the knuckles (index on the thumb side), lengths, fan, and how much each joint bends. */
    private static final float[] FINGER_X = {.17F, .058F, -.058F, -.16F}, FINGER_LEN = {.5F, .56F, .52F, .42F},
            FAN = {.14F, .045F, -.045F, -.15F}, BEND = {.95F, 1.25F, 1.0F}, SEG = {.46F, .31F, .23F};

    private KineticArt() {}

    // =====================================================================================================
    // Where things are
    // =====================================================================================================

    /** The caster's visible hand: in first person, in camera space at the edge of the view; else the main hand. */
    static Vec3 hand(Canvas c, Player p, double reach) {
        Minecraft mc = Minecraft.getInstance();
        if (p == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            Vec3 look = p.getViewVector(c.partial), right = Vec3.directionFromRotation(0, p.getViewYRot(c.partial) + 90);
            Vec3 up = right.cross(look).normalize();
            return c.camera().add(look.scale(reach + .15)).add(right.scale(p.getMainArm() == HumanoidArm.RIGHT ? .36 : -.36)).subtract(up.scale(.4));
        }
        double side = p.getMainArm() == HumanoidArm.RIGHT ? -1 : 1;
        double yaw = Math.toRadians(Mth.rotLerp(c.partial, p.yBodyRotO, p.yBodyRot));
        return p.getPosition(c.partial).add(Math.cos(yaw) * side * .3125, p.isCrouching() ? 1.17 : 1.375, Math.sin(yaw) * side * .3125)
                .add(p.getViewVector(c.partial).scale(reach));
    }

    /** The caster named in the piece's seed, if it is a player here. */
    static Player caster(Canvas c, Active a) {
        if (a.seed() < 0) return null;
        Entity e = c.level.getEntity(a.seed());
        return e instanceof Player p ? p : null;
    }

    /** Where the force leaves: the caster's visible hand, or {@code fallback}. */
    static Vec3 source(Canvas c, Active a, Vec3 fallback) {
        Player p = caster(c, a);
        return p != null ? hand(c, p, .62) : fallback;
    }

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }

    private static float clock(Canvas c) { return c.level.getGameTime() % 24000 + c.partial; }

    private static Vec3 perpendicular(Vec3 axis) {
        Vec3 ref = Math.abs(axis.y) > .9 ? new Vec3(1, 0, 0) : UP;
        return ref.cross(axis).normalize();
    }

    private static Vec3 flat(Vec3 v) {
        Vec3 level = new Vec3(v.x, 0, v.z);
        return level.lengthSqr() < 1e-5 ? new Vec3(0, 0, 1) : level.normalize();
    }

    // =====================================================================================================
    // Primitives
    // =====================================================================================================

    /** A glowing stroke in three layers: deep halo, orchid body, pale core; and its refraction shadow in the soft pass. */
    static void stroke(Canvas c, Vec3[] points, float width, float from, float to) {
        if (from < .01F && to < .01F) return;
        if (c.soft()) c.ribbon(points, width * 1.35F, SHADOW, from * .2F, to * .2F);
        if (!c.glow()) return;
        c.ribbon(points, width, DEEP, from * .32F, to * .32F);
        c.ribbon(points, width * .42F, ORCHID, from * .78F, to * .78F);
        c.ribbon(points, width * .15F, CORE, from, to);
    }

    private static void stroke(Canvas c, Vec3 a, Vec3 b, float width, float from, float to) {
        stroke(c, new Vec3[]{a, b}, width, from, to);
    }

    /**
     * The far hand. {@code wrist} is where it begins, {@code along} the way its fingers point, {@code palm} the way the
     * palm faces; {@code curl} 0 is an open hand and 1 a fist, {@code spread} fans the fingers. A forearm of bent air
     * fades away behind the wrist, and refraction lines slide across the palm.
     */
    static void hand(Canvas c, Vec3 wrist, Vec3 along, Vec3 palm, float size, float curl, float spread, float alpha, float clock) {
        if (alpha < .01F || size <= .02F || c.solid()) return;
        along = along.normalize();
        palm = palm.subtract(along.scale(palm.dot(along)));
        palm = palm.lengthSqr() < 1e-5 ? perpendicular(along) : palm.normalize();
        Vec3 across = palm.cross(along).normalize();
        float s = size, w = .085F * s;
        Vec3 knuckles = wrist.add(along.scale(.5 * s)), middle = wrist.add(along.scale(.27 * s));
        // The palm: filled glass, darker in the middle and catching the light at its rim, refraction sliding over it.
        // A rounded palm: a squared-off ellipse, broad across the knuckles, narrowing a little to the heel.
        int sides = 14;
        Vec3[] outline = new Vec3[sides + 1];
        for (int i = 0; i <= sides; i++) {
            float th = i * TWO_PI / sides, cx = cos(th), cy = sin(th);
            double x = Math.signum(cx) * Math.pow(Math.abs(cx), .6) * (cy > 0 ? .23 : .19) * s;
            double y = Math.signum(cy) * Math.pow(Math.abs(cy), .7) * .25 * s;
            outline[i] = middle.add(across.scale(x)).add(along.scale(y));
        }
        for (int i = 0; i < sides; i++) {
            if (c.soft()) c.tri(middle, outline[i], outline[i + 1], SHADOW, .34F * alpha, .2F * alpha, .2F * alpha);
            if (c.glow()) c.tri(middle, outline[i], outline[i + 1], ORCHID, .06F * alpha, .26F * alpha, .26F * alpha);
        }
        if (c.glow()) {
            c.ribbon(outline, w * .55F, DEEP, .3F * alpha, .3F * alpha);
            c.ribbon(outline, w * .16F, BLUSH, .7F * alpha, .7F * alpha);
        }
        if (c.glow()) for (int k = 0; k < 2; k++) {
            float u = (clock * .045F + k * .5F) % 1;
            float k2 = alpha * .3F * sin(u * PI);
            Vec3 line = wrist.add(along.scale((.06 + .4 * u) * s));
            c.line(line.add(across.scale(-.18 * s)), line.add(across.scale(.18 * s)), .012F * s, CORE, k2, k2);
        }
        // The forearm: bent air fading back from the wrist into nothing.
        Vec3[] arm = {wrist.subtract(along.scale(.75 * s)), wrist.subtract(along.scale(.35 * s)), wrist};
        if (c.soft()) c.ribbon(arm, .2F * s, SHADOW, 0, .14F * alpha);
        if (c.glow()) { c.ribbon(arm, .17F * s, DEEP, 0, .22F * alpha); c.ribbon(arm, .05F * s, BLUSH, 0, .3F * alpha); }
        // Four fingers, each three joints bending toward the palm.
        for (int f = 0; f < 4; f++) {
            float len = FINGER_LEN[f] * s, fan = FAN[f] * (.6F + spread);
            float tremble = .04F * sin(clock * 1.9F + f * 2.3F);
            Vec3 dir = along.scale(Math.cos(fan)).add(across.scale(Math.sin(fan))).normalize();
            Vec3[] pts = new Vec3[4];
            pts[0] = knuckles.add(across.scale(FINGER_X[f] * s));
            double angle = 0;
            for (int j = 0; j < 3; j++) {
                angle += curl * BEND[j] + tremble * (j + 1) * .3;
                Vec3 seg = dir.scale(Math.cos(angle)).add(palm.scale(Math.sin(angle)));
                pts[j + 1] = pts[j].add(seg.scale(len * SEG[j]));
            }
            finger(c, pts, w, alpha);
            if (c.glow()) c.glow(pts[3], .05F * s, BLUSH, .4F * alpha);
        }
        // The thumb, out from the heel of the palm, closing across it as the hand curls.
        Vec3 thumbDir = along.scale(.5).add(across.scale(.86)).normalize();
        Vec3 inward = palm.scale(.7).subtract(across.scale(.7)).normalize();
        Vec3[] thumb = new Vec3[3];
        thumb[0] = wrist.add(along.scale(.12 * s)).add(across.scale(.19 * s));
        double angle = 0;
        for (int j = 0; j < 2; j++) {
            angle += curl * 1.0 + .15;
            Vec3 seg = thumbDir.scale(Math.cos(angle)).add(inward.scale(Math.sin(angle)));
            thumb[j + 1] = thumb[j].add(seg.scale(.24 * s));
        }
        finger(c, thumb, w * 1.1F, alpha);
        if (c.glow()) c.glow(thumb[2], .05F * s, BLUSH, .4F * alpha);
    }

    /** One finger of glass: a dark body in the soft pass, an orchid body and a blush line in the glow pass. */
    private static void finger(Canvas c, Vec3[] pts, float w, float alpha) {
        if (c.soft()) c.ribbon(pts, w * 1.25F, SHADOW, .32F * alpha, .22F * alpha);
        if (!c.glow()) return;
        c.ribbon(pts, w * 1.1F, DEEP, .24F * alpha, .18F * alpha);
        c.ribbon(pts, w * .62F, ORCHID, .45F * alpha, .35F * alpha);
        c.ribbon(pts, w * .14F, BLUSH, .75F * alpha, .55F * alpha);
        c.ribbon(pts, w * .05F, CORE, .45F * alpha, .25F * alpha);
    }

    /**
     * Five fingers of bent air closing over a body from above, from open ({@code close} 0) to shut, squeezed in by
     * {@code squeeze}, trembling; the palm a disc over the head, a band of pressure round the waist.
     */
    static void claw(Canvas c, Vec3 feet, float height, float width, float close, float squeeze, float alpha, float clock, int seed) {
        float k = close * alpha;
        if (k <= .005F || c.solid()) return;
        float r = (width * .5F + .32F) * (1 + .8F * (1 - close)) * (1 - .35F * squeeze);
        Vec3 crown = feet.add(0, height + .5, 0);
        int n = 8;
        for (int f = 0; f < 5; f++) {
            float angle = f * TWO_PI / 5 + hash(seed, 0) * TWO_PI;
            float tremble = (.035F + .05F * squeeze) * sin(clock * (1.7F + squeeze * 2) + f * 2.1F);
            Vec3 out = new Vec3(cos(angle), 0, sin(angle));
            float reach = f == 0 ? .55F : .8F;
            Vec3[] finger = new Vec3[n];
            for (int i = 0; i < n; i++) {
                float u = i / (n - 1F), bend = sin(u * PI * .5F);
                finger[i] = crown.add(out.scale(r * bend + tremble)).add(0, -(height + .5) * reach * u * u - .1 * u, 0);
            }
            finger(c, finger, .11F + width * .04F, k);
            if (c.glow()) {
                c.glow(finger[n - 1], .07F, BLUSH, k * .5F);
                // Knuckles: the joints catch the light.
                c.glow(finger[n / 3], .05F, CORE, k * .35F);
            }
        }
        if (c.soft()) c.disc(crown, UP, r * .75F, SHADOW, .18F * k, .03F * k);
        if (c.glow()) {
            c.disc(crown, UP, r * .7F, ORCHID, .1F * k, 0);
            c.ring(crown, UP, r * .72F, .04F, 0, TWO_PI, BLUSH, k * .6F);
            c.glow(crown, .45F * k, CORE, k * .35F);
            c.ring(feet.add(0, height * .55, 0), UP, r * .95F, .05F, 0, TWO_PI, ORCHID, k * (.35F + .2F * sin(clock * .9F)));
        }
    }

    /** The force leaving the hand: a lens of bent air flying from {@code from} to {@code to} as {@code u} goes 0 to 1. */
    static void ripple(Canvas c, Vec3 from, Vec3 to, float u, float alpha) {
        Vec3 path = to.subtract(from);
        if (path.lengthSqr() < 1e-4 || alpha < .01F) return;
        Vec3 axis = path.normalize(), at = from.add(path.scale(u));
        float r = .18F + .35F * u;
        if (c.glow()) {
            c.ring(at, axis, r * 1.3F, .07F, 0, TWO_PI, DEEP, alpha * .35F);
            c.ring(at, axis, r, .035F, 0, TWO_PI, ORCHID, alpha * .75F);
            c.ring(at, axis, r * .96F, .012F, 0, TWO_PI, CORE, alpha);
            c.line(from.lerp(at, .2), at, .03F, BLUSH, 0, alpha * .35F);
        }
        if (c.soft()) c.ring(at, axis, r, .09F, 0, TWO_PI, SHADOW, alpha * .2F);
    }

    /** Pressure on a plane: three rings opening out from {@code at} as {@code u} goes 0 to 1, and a flash at first. */
    static void dent(Canvas c, Vec3 at, Vec3 normal, float radius, float u, float alpha) {
        if (alpha < .01F) return;
        Vec3 n = normal.lengthSqr() < 1e-5 ? UP : normal.normalize();
        for (int i = 0; i < 3; i++) {
            float k = clamp(u * 1.25F - i * .12F, 0, 1);
            if (k <= 0) continue;
            float r = radius * (.2F + .8F * k), a = alpha * (1 - k) * (1 - i * .22F);
            if (c.glow()) {
                c.ring(at, n, r, .08F + radius * .015F, 0, TWO_PI, DEEP, a * .4F);
                c.ring(at, n, r, .035F + radius * .006F, 0, TWO_PI, ORCHID, a * .8F);
                c.ring(at, n, r * .99F, .012F, 0, TWO_PI, CORE, a);
            }
            if (c.soft()) c.ring(at, n, r, .12F + radius * .02F, 0, TWO_PI, SHADOW, a * .22F);
        }
        if (c.glow()) c.glow(at.add(n.scale(.05)), radius * .55F * (1 - smooth(0, .35F, u)), CORE, alpha * .7F);
    }

    /** Air dragged after something fast: thin streaks behind {@code at}, against {@code motion}. */
    static void streaks(Canvas c, Vec3 at, Vec3 motion, float length, float spreadR, float alpha, int seed) {
        if (!c.glow() || alpha < .01F || motion.lengthSqr() < 1e-4) return;
        Vec3 back = motion.normalize().reverse(), side = perpendicular(back), up = side.cross(back).normalize();
        int n = minimal() ? 3 : 6;
        for (int i = 0; i < n; i++) {
            double ang = hash(seed, i) * TWO_PI, rr = spreadR * (.4 + .6 * hash(seed, i + 9));
            Vec3 start = at.add(side.scale(Math.cos(ang) * rr)).add(up.scale(Math.sin(ang) * rr));
            float len = length * (.6F + .4F * hash(seed, i + 17));
            c.line(start, start.add(back.scale(len)), .025F, BLUSH, alpha * .6F, 0);
            c.line(start, start.add(back.scale(len * .7)), .008F, CORE, alpha * .8F, 0);
        }
    }

    /** Chips and dust thrown off a struck face along {@code normal}, falling back as {@code t} runs. */
    static void grit(Canvas c, Vec3 at, Vec3 normal, float size, float t, float alpha, int seed) {
        if (alpha < .01F) return;
        Vec3 n = normal.lengthSqr() < 1e-5 ? UP : normal.normalize(), side = perpendicular(n), up = side.cross(n).normalize();
        int count = minimal() ? 3 : 7;
        if (c.solid()) for (int i = 0; i < count; i++) {
            double ang = hash(seed, i + 40) * TWO_PI, speed = (.08 + .12 * hash(seed, i + 50)) * (.7 + size * .3);
            Vec3 out = n.scale(.7).add(side.scale(Math.cos(ang) * .8)).add(up.scale(Math.sin(ang) * .8)).normalize();
            Vec3 p = at.add(out.scale(speed * t)).add(0, -.02 * t * t, 0);
            if (t > 12) continue;
            float chip = .05F + .05F * hash(seed, i + 60);
            c.box(p, chip, chip * .8F, chip, t * .4F + i, t * .3F, i, STONE, 1);
        }
        if (c.soft() && size >= 1 && !minimal()) {
            float k = smooth(0, 3, t);
            c.cloud(at.add(n.scale(.2 + .05 * t)), .3F * size * (.6F + k), .22F * size * (.6F + k), .3F * size * (.6F + k), seed & 3, t * .1F,
                    alpha * .35F * (1 - smooth(4, 14, t)), Shades.smoke(c, DUST, 0));
        }
    }

    // =====================================================================================================
    // Pieces
    // =====================================================================================================

    static void draw(Canvas c, Active a, float t) {
        switch (a.piece()) {
            case KINETIC_SEND -> send(c, a, t);
            case KINETIC_SHOVE -> shove(c, a, t);
            case KINETIC_GRIP -> grip(c, a, t);
            case KINETIC_THROW -> thrown(c, a, t);
            case KINETIC_IMPACT -> impact(c, a, t);
            case KINETIC_CATCH -> cupped(c, a, t);
            case KINETIC_SHOT -> shot(c, a, t);
            case KINETIC_RETURN -> returned(c, a, t);
            case KINETIC_PLUCK -> pluck(c, a, t);
            case KINETIC_FLING -> fling(c, a, t);
            case KINETIC_STORM -> storm(c, a, t);
            case KINETIC_SLAM -> slam(c, a, t);
            case KINETIC_PIN -> pin(c, a, t);
            case KINETIC_BECKON -> beckon(c, a, t);
            case KINETIC_ROUND_UP -> roundUp(c, a, t);
            case KINETIC_POP -> pop(c, a, t);
            case KINETIC_STEP -> step(c, a, t);
            case KINETIC_LEVITATE -> levitate(c, a, t);
            case KINETIC_CUSHION -> cushion(c, a, t);
            case KINETIC_SKYFALL -> skyfall(c, a, t);
            case KINETIC_CLENCH -> clench(c, a, t);
            case KINETIC_WARD -> ward(c, a, t);
            case KINETIC_GATHER -> gather(c, a, t);
            case STYLE_TK_PALM -> swat(c, a, t);
            case STYLE_TK_GRASP -> grasp(c, a, t);
            case FINISH_CRUSH, FINISH_TOSS -> finishers(c, a, t);
            case FINISH_STRINGS -> strings(c, a, t);
            default -> { }
        }
    }

    /** A held piece re-sent every ten ticks: fade in and out over two so the copies hand over without a seam. */
    private static float held(Active a, float t) { return fade(t, a.piece().ticks, 2, 2); }

    /** The force leaving the visible hand for a far point. */
    private static void send(Canvas c, Active a, float t) {
        Vec3 from = source(c, a, a.origin()), to = a.target();
        float u = smooth(0, 5, t), alpha = fade(t, a.piece().ticks, 1, 3);
        ripple(c, from, to, u, alpha * (1 - smooth(5, 7, t) * .6F));
        if (t > 4) dent(c, to, to.subtract(from), .5F, smooth(4, 8, t), alpha);
    }

    /**
     * Shove: a palm, fingers up, flies from the hand along the cone and grows as the cone widens, reaching each body
     * at the tick the server shoves it ({@link KineticGeometry#shoveTravel}); rings of pressure open behind it.
     */
    private static void shove(Canvas c, Active a, float t) {
        Vec3 from = source(c, a, a.origin()), end = a.target();
        Vec3 path = end.subtract(from);
        if (path.lengthSqr() < .01) return;
        Vec3 dir = path.normalize();
        double reach = path.length();
        float u = KineticGeometry.shoveTravel(t), life = a.piece().ticks;
        float alpha = fade(t, life, 1, 8);
        float width = (float) Math.tan(Math.toRadians(KineticGeometry.SHOVE_HALF_ANGLE)) * (float) reach;
        Vec3 up = UP.subtract(dir.scale(UP.dot(dir)));
        up = up.lengthSqr() < 1e-4 ? perpendicular(dir) : up.normalize();
        // The palm, flat to the push, fingers up; it flattens a little as it lands.
        float size = Math.min(2.6F, 1.1F + 1.6F * u);
        float flatten = smooth(KineticGeometry.SHOVE_TRAVEL, KineticGeometry.SHOVE_TRAVEL + 4, t);
        Vec3 centre = from.add(dir.scale(reach * u + flatten * .6));
        Vec3 wrist = centre.subtract(up.scale(.45 * size));
        hand(c, wrist, up, dir, size, .08F - .1F * flatten, .4F, alpha * (1 - flatten * .5F), clock(c));
        // Rings of pressure along the cone, each opening as the palm passes it.
        int rings = minimal() ? 3 : 5;
        for (int i = 1; i <= rings; i++) {
            float at = i / (rings + 1F);
            float k = smooth(at * KineticGeometry.SHOVE_TRAVEL, at * KineticGeometry.SHOVE_TRAVEL + 6, t);
            if (k <= 0 || k >= 1) continue;
            Vec3 p = from.add(dir.scale(reach * at));
            float r = (.35F + .5F * Math.min(2.6F, 1.1F + 1.6F * at)) * (.8F + .5F * k);
            if (c.glow()) {
                c.ring(p, dir, r, .06F, 0, TWO_PI, DEEP, alpha * (1 - k) * .4F);
                c.ring(p, dir, r, .02F, 0, TWO_PI, BLUSH, alpha * (1 - k) * .8F);
            }
        }
        streaks(c, centre, dir, 1.2F + 2 * u, size * .6F, alpha * (1 - flatten), (int) (a.start() & 0xFFFF));
        if (t >= KineticGeometry.SHOVE_TRAVEL) dent(c, end, dir.reverse(), size * .8F, smooth(KineticGeometry.SHOVE_TRAVEL, life, t), alpha);
    }

    /** Grip: a claw of bent air closed over the held body, a tether back to the visible hand, and a smear when it swings. */
    private static void grip(Canvas c, Active a, float t) {
        Entity body = a.bound(c);
        Vec3 feet = body != null ? body.getPosition(c.partial) : a.origin();
        float h = body != null ? body.getBbHeight() : 1.8F, w = body != null ? body.getBbWidth() : .6F;
        float alpha = held(a, t), clock = clock(c);
        claw(c, feet, h, w, 1, .15F, alpha, clock, a.seed());
        Player p = caster(c, a);
        if (p != null) {
            Vec3 from = hand(c, p, .62), crown = feet.add(0, h + .5, 0);
            Vec3 mid = from.lerp(crown, .5).add(0, .5 + .1 * sin(clock * .7F), 0);
            stroke(c, new Vec3[]{from, from.lerp(mid, .6), mid, mid.lerp(crown, .5), crown}, .05F, alpha * .15F, alpha * .45F);
        }
        if (body != null) {
            Vec3 motion = body.getPosition(c.partial).subtract(body.getPosition(c.partial - 1));
            if (motion.lengthSqr() > .16) streaks(c, feet.add(0, h * .5, 0), motion, (float) Math.min(4, motion.length() * 2.5), w * .7F + .3F, alpha, body.getId());
        }
    }

    /** A throw: the claw flings open where it let go, and air streams after what flew. */
    private static void thrown(Canvas c, Active a, float t) {
        float alpha = fade(t, a.piece().ticks, 1, 6), open = smooth(0, 5, t);
        Vec3 at = a.origin(), dir = a.target().subtract(at);
        claw(c, at.subtract(0, a.scale() * .6, 0), a.scale() * 1.4F, a.scale() * .8F, 1 - open * .9F, 0, alpha * (1 - open), clock(c), a.seed());
        Entity body = a.bound(c);
        if (body != null && body.isAlive()) {
            Vec3 motion = body.getDeltaMovement().lengthSqr() > .01 ? body.getDeltaMovement() : dir;
            streaks(c, body.getPosition(c.partial).add(0, body.getBbHeight() * .5, 0), motion, 2.5F, body.getBbWidth() * .7F + .3F, alpha, body.getId());
        }
        dent(c, at, dir, .8F + a.scale() * .4F, smooth(0, 8, t), alpha);
    }

    /** Something struck something: dent rings on the struck face's plane, a flash, grit; a big one is a ring burst. */
    private static void impact(Canvas c, Active a, float t) {
        float life = a.piece().ticks, alpha = fade(t, life, .5F, 6), size = a.scale();
        Vec3 normal = a.target().subtract(a.origin());
        dent(c, a.origin(), normal, size, smooth(0, life * .8F, t), alpha);
        grit(c, a.origin(), normal, Math.min(size, 3), t, alpha, (int) (a.start() & 0xFFFF) ^ a.seed());
    }

    /** Bullet Catch: two cupped hands raised before the caster, palms out; a lens of bent air between them. */
    private static void cupped(Canvas c, Active a, float t) {
        Entity e = a.bound(c);
        if (!(e instanceof Player p)) return;
        Minecraft mc = Minecraft.getInstance();
        boolean own = p == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON;
        float alpha = held(a, t) * (own ? .55F : 1), clock = clock(c);
        Vec3 forward = Vec3.directionFromRotation(0, Mth.rotLerp(c.partial, p.yRotO, p.getYRot()));
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        Vec3 centre = p.getPosition(c.partial).add(forward.scale(1.4)).add(0, 1.25, 0);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 wrist = centre.add(side.scale(s * .62)).add(0, -.55, 0).subtract(forward.scale(.15));
            Vec3 along = UP.add(side.scale(s * .25)).add(forward.scale(.2)).normalize();
            hand(c, wrist, along, forward.add(side.scale(-s * .35)), 1.25F, .32F, .6F, alpha * .9F, clock + s * 7);
        }
        if (c.glow() && !own) c.disc(centre.add(forward.scale(.25)), forward, 1.2F, ORCHID, .05F * alpha, 0);
    }

    /** A caught shot: two thin rings slowly turning round it, and a glint. */
    private static void shot(Canvas c, Active a, float t) {
        Entity e = a.bound(c);
        Vec3 at = e != null ? e.getPosition(c.partial).add(0, e.getBbHeight() * .5, 0) : a.origin();
        float alpha = held(a, t), clock = clock(c);
        if (!c.glow() && !c.soft()) return;
        // Pinched between two fingertips of bent air, turning slowly.
        float ang = clock * .05F + (e != null ? e.getId() : 0);
        Vec3 dir = new Vec3(cos(ang), .35, sin(ang)).normalize();
        for (int s = -1; s <= 1; s += 2) {
            Vec3 tip = at.add(dir.scale(s * .1)), base = at.add(dir.scale(s * .5)).add(0, .14, 0);
            stroke(c, new Vec3[]{base, base.lerp(tip, .55).add(0, .06, 0), tip}, .045F, alpha * .35F, alpha * .9F);
        }
        if (c.glow()) c.glow(at, .1F, BLUSH, alpha * .5F);
    }

    /** A caught shot sent back: a flick ring where it turned, streaks along its way. */
    private static void returned(Canvas c, Active a, float t) {
        float alpha = fade(t, a.piece().ticks, .5F, 5);
        Vec3 dir = a.target().subtract(a.origin());
        dent(c, a.origin(), dir, .6F, smooth(0, 6, t), alpha);
        Entity e = a.bound(c);
        if (e != null && e.isAlive()) streaks(c, e.getPosition(c.partial), dir, 1.8F, .15F, alpha, e.getId());
    }

    /** Disarm: a small hand pinches the weapon and carries it home, a thread of bent air back to the caster's hand. */
    private static void pluck(Canvas c, Active a, float t) {
        Entity item = a.bound(c);
        Vec3 at = item != null && item.isAlive() ? item.getPosition(c.partial).add(0, .25, 0) : a.origin();
        float alpha = fade(t, a.piece().ticks, 1, 6), clock = clock(c);
        Vec3 home = source(c, a, a.target());
        Vec3 way = home.subtract(at);
        Vec3 along = way.lengthSqr() < 1e-3 ? UP : way.normalize().reverse();
        hand(c, at.subtract(along.scale(.35)), along, perpendicular(along), .55F, .75F, .1F, alpha, clock);
        if (t < 4) dent(c, a.origin(), UP, .5F, smooth(0, 4, t), alpha);
        stroke(c, new Vec3[]{home, home.lerp(at, .5).add(0, .2, 0), at}, .03F, alpha * .4F, alpha * .1F);
    }

    /** A flung weapon turning end over end: a crescent of bent air spinning round it, air streaming after. */
    private static void fling(Canvas c, Active a, float t) {
        Entity item = a.bound(c);
        if (item == null || !item.isAlive()) return;
        float alpha = fade(t, a.piece().ticks, .5F, 4);
        Vec3 at = item.getPosition(c.partial).add(0, .25, 0);
        Vec3 motion = item.getDeltaMovement().lengthSqr() > .01 ? item.getDeltaMovement() : a.target().subtract(a.origin());
        Vec3 axis = perpendicular(motion.normalize());
        float spin = (c.level.getGameTime() + c.partial) * 1.4F;
        if (c.glow()) {
            c.ring(at, axis, .5F, .06F, spin, spin + 2.2F, DEEP, alpha * .5F);
            c.ring(at, axis, .5F, .025F, spin, spin + 2.2F, ORCHID, alpha * .85F);
            c.ring(at, axis, .5F, .01F, spin + .6F, spin + 2.2F, CORE, alpha);
        }
        streaks(c, at, motion, 2.2F, .2F, alpha, item.getId());
    }

    /**
     * The Telekinetic Storm: a warning ring at its reach; two great hands lying low at the edges sweep the crowd in along
     * the ground, then turn palm up beneath it and lift, slowly circling, trembling under the weight.
     */
    private static void storm(Canvas c, Active a, float t) {
        if (SetPieceClient.superseded(a, Set.of(Piece.KINETIC_SLAM), c.level.getGameTime())) return;
        Vec3 centre = a.origin();
        float r = Math.max(4, a.scale()), life = a.piece().ticks, alpha = fade(t, life, 3, 3), clock = clock(c);
        float gather = smooth(0, KineticGeometry.STORM_GATHER, t), lift = smooth(KineticGeometry.STORM_GATHER, KineticGeometry.STORM_GATHER + 12, t);
        Vec3 ground = centre.add(0, .08, 0);
        if (c.glow()) {
            c.ring(ground, UP, r, .09F, 0, TWO_PI, DEEP, alpha * .35F);
            c.ring(ground, UP, r, .035F, 0, TWO_PI * gather, BLUSH, alpha * .8F);
        }
        if (c.soft()) c.ring(ground, UP, r, .25F, 0, TWO_PI, SHADOW, alpha * .15F);
        float base = hash(a.seed(), 3) * TWO_PI + (t - KineticGeometry.STORM_GATHER) * .02F * lift;
        float size = Math.min(4.2F, r * .34F);
        for (int s = 0; s < 2; s++) {
            float ang = base + s * PI;
            Vec3 out = new Vec3(cos(ang), 0, sin(ang));
            double dist = Mth.lerp(gather, r * .95, r * .42);
            double y = .1 + (KineticGeometry.STORM_HEIGHT - .4) * lift + .12 * sin(clock * .3F + s);
            Vec3 wrist = centre.add(out.scale(dist + size * .45)).add(0, y, 0);
            // Gathering: palm inward, fingers dragging along the ground; lifting: palm up beneath the crowd.
            Vec3 along = out.reverse().add(0, lift * .15, 0).normalize();
            Vec3 palm = out.reverse().scale(1 - lift).add(UP.scale(lift)).normalize();
            hand(c, wrist, along, palm, size, .25F + .2F * lift, .7F, alpha, clock + s * 11);
            if (gather < 1 && c.soft() && !minimal())
                c.cloud(centre.add(out.scale(dist)).add(0, .3, 0), .5F * size * .4F, .35F * size * .4F, .5F * size * .4F, s, t * .1F,
                        alpha * .35F * (1 - gather), Shades.smoke(c, DUST, 0));
        }
        if (lift > 0 && c.glow()) for (int i = 0; i < 2; i++) {
            float spin = (t - KineticGeometry.STORM_GATHER) * .05F + i * PI;
            c.ring(centre.add(0, KineticGeometry.STORM_HEIGHT + .9 + i * .5, 0), UP, r * .45F, .04F, spin, spin + 2.6F, ORCHID, alpha * lift * .6F);
        }
    }

    /** The slam: two great hands high over the centre turn palm down and drive to the ground; a ring bursts out over it. */
    private static void slam(Canvas c, Active a, float t) {
        Vec3 centre = a.origin();
        float r = Math.max(4, a.scale()), life = a.piece().ticks, alpha = fade(t, life, .5F, 8), clock = clock(c);
        float drive = smooth(0, 3, t), size = Math.min(4.2F, r * .34F);
        for (int s = 0; s < 2; s++) {
            float ang = hash(a.seed(), 3) * TWO_PI + s * PI;
            Vec3 out = new Vec3(cos(ang), 0, sin(ang));
            Vec3 palmAt = centre.add(out.scale(r * .25)).add(0, Mth.lerp(drive, KineticGeometry.STORM_HEIGHT + 3.5, .4), 0);
            Vec3 along = out.reverse().add(0, -.15, 0).normalize();
            hand(c, palmAt.subtract(along.scale(size * .3)), along, UP.reverse(), size, .1F, .6F, alpha * (1 - smooth(4, 10, t)), clock);
            if (t < 4) streaks(c, palmAt, new Vec3(0, -1, 0), 3, size * .5F, alpha, s);
        }
        if (t >= 3) {
            float k = smooth(3, life, t);
            dent(c, centre.add(0, .1, 0), UP, r * 1.05F, k, alpha);
            grit(c, centre.add(0, .1, 0), UP, 3, t - 3, alpha, a.seed());
        }
    }

    /** Pin: an open palm pressing the body back into what it met, trembling; pressure rings spread on that face. */
    private static void pin(Canvas c, Active a, float t) {
        Entity body = a.bound(c);
        Vec3 normal = a.target().subtract(a.origin());
        if (normal.lengthSqr() < 1e-4) return;
        normal = normal.normalize();
        float alpha = held(a, t), clock = clock(c);
        Vec3 centre = body != null ? body.getPosition(c.partial).add(0, body.getBbHeight() * .5, 0) : a.origin();
        float w = body != null ? body.getBbWidth() : .6F, size = Math.max(.9F, a.scale());
        Vec3 up = UP.subtract(normal.scale(UP.dot(normal)));
        up = up.lengthSqr() < 1e-3 ? perpendicular(normal) : up.normalize();
        float tremble = .03F * sin(clock * 2.6F);
        Vec3 palmCentre = centre.add(normal.scale(w * .5 + .2 + tremble));
        hand(c, palmCentre.subtract(up.scale(.3 * size)), up, normal.reverse(), size, .12F, .55F, alpha, clock);
        Vec3 face = a.origin();
        if (c.glow()) for (int i = 0; i < 2; i++) {
            float r = size * (.55F + .4F * i) + .08F * sin(clock * .9F + i);
            c.ring(face, normal, r, .05F, 0, TWO_PI, ORCHID, alpha * (.45F - .15F * i));
        }
        if (c.soft()) c.ring(face, normal, size * .7F, .18F, 0, TWO_PI, SHADOW, alpha * .2F);
    }

    /** Beckon: a small hand presses a control (1) or carries something home along a thread (2). */
    private static void beckon(Canvas c, Active a, float t) {
        float alpha = fade(t, a.piece().ticks, 1, 6), clock = clock(c);
        Vec3 home = source(c, a, a.target());
        if (a.scale() < 1.5F) {
            Vec3 spot = a.origin(), way = spot.subtract(home);
            if (way.lengthSqr() < 1e-3) return;
            Vec3 along = way.normalize();
            float press = sin(clamp(t / 6, 0, 1) * PI);
            Vec3 wrist = spot.subtract(along.scale(.9 - .25 * press));
            hand(c, wrist, along, perpendicular(along), .5F, .55F, .1F, alpha, clock);
            if (t > 2) dent(c, spot, along.reverse(), .4F, smooth(2, 8, t), alpha);
            ripple(c, home, spot, smooth(0, 3, t), alpha * (1 - smooth(3, 5, t)));
            return;
        }
        Entity carried = a.bound(c);
        Vec3 at = carried != null && carried.isAlive() ? carried.getPosition(c.partial).add(0, carried.getBbHeight() * .5, 0) : a.origin();
        Vec3 way = home.subtract(at);
        Vec3 along = way.lengthSqr() < 1e-3 ? UP : way.normalize().reverse();
        float size = carried != null ? Math.max(.45F, carried.getBbWidth() * 1.1F) : .45F;
        hand(c, at.subtract(along.scale(.3 * size)), along, UP.reverse(), size, .6F, .2F, alpha, clock);
        stroke(c, new Vec3[]{home, home.lerp(at, .5).add(0, .25, 0), at}, .03F, alpha * .35F, alpha * .1F);
    }

    /** Round Up: two hands cupped facing each other over the spot, breathing closed, a ring of pressure on the ground. */
    private static void roundUp(Canvas c, Active a, float t) {
        Vec3 centre = a.origin();
        float r = Math.max(1.5F, a.scale()), alpha = held(a, t), clock = clock(c);
        float breathe = .5F + .5F * sin(clock * .25F);
        for (int s = 0; s < 2; s++) {
            float ang = clock * .02F + s * PI;
            Vec3 out = new Vec3(cos(ang), 0, sin(ang));
            Vec3 wrist = centre.add(out.scale(r * (.95 - .15 * breathe))).add(0, .2, 0);
            Vec3 along = out.reverse().add(0, .55, 0).normalize();
            hand(c, wrist, along, out.reverse(), r * .55F, .45F + .15F * breathe, .6F, alpha, clock + s * 5);
        }
        if (c.glow()) {
            c.ring(centre.add(0, .06, 0), UP, r, .06F, 0, TWO_PI, DEEP, alpha * .35F);
            c.ring(centre.add(0, .06, 0), UP, r * (.6F + .1F * breathe), .025F, 0, TWO_PI, BLUSH, alpha * .6F);
        }
    }

    /** Juggle's pop: a palm strikes up under the feet (1); Spike: a palm slaps down from over the head (2). */
    private static void pop(Canvas c, Active a, float t) {
        Entity body = a.bound(c);
        Vec3 feet = body != null && body.isAlive() ? body.getPosition(c.partial) : a.origin();
        float h = body != null ? body.getBbHeight() : 1.8F, w = body != null ? body.getBbWidth() : .6F;
        float life = a.piece().ticks, alpha = fade(t, life, .5F, 6), clock = clock(c);
        float size = KineticGeometry.handScale(w, h) * 1.1F;
        boolean spike = a.scale() >= 1.5F;
        if (!spike) {
            float rise = smooth(0, 2, t);
            Vec3 palm = feet.add(0, Mth.lerp(rise, -.8, -.05), 0);
            Vec3 along = flat(a.target().subtract(a.origin()).lengthSqr() > 1e-3 && Math.abs(a.target().y - a.origin().y) < .5
                    ? a.target().subtract(a.origin()) : new Vec3(1, 0, 0));
            hand(c, palm.subtract(along.scale(size * .3)), along, UP, size, .15F, .5F, alpha * (1 - smooth(3, 9, t)), clock);
            dent(c, feet.add(0, .05, 0), UP, size * .8F, smooth(1, life, t), alpha);
        } else {
            float drive = smooth(0, 3, t);
            Vec3 palm = feet.add(0, Mth.lerp(drive, h + 1.4, h + .1), 0);
            hand(c, palm.subtract(new Vec3(0, 0, 1).scale(size * .3)), new Vec3(0, 0, 1), UP.reverse(), size, .1F, .5F, alpha * (1 - smooth(4, 10, t)), clock);
            if (t < 4) streaks(c, palm, new Vec3(0, -1, 0), 2.2F, size * .4F, alpha, a.seed());
        }
    }

    /** Vector Step: the caster's own far hand, behind them, shoves them along; its print hangs in the air and fades. */
    private static void step(Canvas c, Active a, float t) {
        Entity e = a.bound(c);
        Vec3 dir = flat(a.target().subtract(a.origin()));
        float alpha = fade(t, a.piece().ticks, .5F, 8), clock = clock(c);
        Vec3 push = a.origin().add(0, 1.0, 0).subtract(dir.scale(.6));
        float shove = smooth(0, 3, t);
        hand(c, push.subtract(UP.scale(.4)).add(dir.scale(.4 * shove)), UP, dir, 1.3F, .1F, .5F, alpha * (1 - smooth(4, 12, t)), clock);
        dent(c, push, dir.reverse(), 1, smooth(1, 12, t), alpha);
        if (e != null) streaks(c, e.getPosition(c.partial).add(0, 1, 0), e.getDeltaMovement().lengthSqr() > .01 ? e.getDeltaMovement() : dir, 1.6F, .5F, alpha * (1 - shove * .5F), e.getId());
    }

    /** Levitate: two hands under the feet, palms up, cradling; a slow ring beneath them. */
    private static void levitate(Canvas c, Active a, float t) {
        Entity e = a.bound(c);
        if (e == null) return;
        float alpha = held(a, t), clock = clock(c), scale = Math.max(.5F, a.scale());
        Vec3 feet = e.getPosition(c.partial);
        float yaw = (e instanceof LivingEntity l ? Mth.rotLerp(c.partial, l.yBodyRotO, l.yBodyRot) : e.getYRot()) * DEG_TO_RAD;
        Vec3 forward = new Vec3(-sin(yaw), 0, cos(yaw)), side = new Vec3(-forward.z, 0, forward.x);
        float bob = .05F * sin(clock * .15F);
        for (int s = -1; s <= 1; s += 2) {
            // Cupped under each foot: fingers rising forward beside the sole, palm turned up and in.
            Vec3 wrist = feet.add(side.scale(s * .6 * scale)).subtract(forward.scale(.45 * scale)).add(0, -.55 + bob, 0);
            Vec3 along = forward.scale(.7).add(UP.scale(.55)).add(side.scale(-s * .25)).normalize();
            Vec3 palm = UP.scale(.8).add(side.scale(-s * .6));
            hand(c, wrist, along, palm, 1.3F * scale, .4F, .5F, alpha * .9F, clock + s * 9);
        }
        if (c.glow()) {
            c.ring(feet.add(0, -.45 + bob, 0), UP, .9F * scale, .03F, 0, TWO_PI, BLUSH, alpha * .4F);
            c.ring(feet.add(0, -.6 + bob, 0), UP, (1.15F + .1F * sin(clock * .2F)) * scale, .05F, 0, TWO_PI, DEEP, alpha * .3F);
        }
    }

    /** A catch: a palm rises under the falling body and takes its weight; rings spread under it. */
    private static void cushion(Canvas c, Active a, float t) {
        Entity e = a.bound(c);
        Vec3 feet = e != null ? e.getPosition(c.partial) : a.origin();
        float alpha = fade(t, a.piece().ticks, 1, 8), clock = clock(c), size = Math.max(1, a.scale()) * 1.1F;
        float rise = smooth(0, 4, t);
        Vec3 palm = feet.add(0, Mth.lerp(rise, -1.2, -.1), 0);
        hand(c, palm.subtract(new Vec3(1, 0, 0).scale(size * .3)), new Vec3(1, 0, 0), UP, size, .35F, .5F, alpha * (1 - smooth(8, 16, t)), clock);
        dent(c, feet.add(0, .04, 0), UP, size, smooth(2, 16, t), alpha);
    }

    /**
     * Skyfall: the ground ring fills as a claw hauls the foe into the sky (its forearm reaching up out of sight), holds it
     * trembling, then turns over and drives it down; a ring bursts over the ground, dust rolls and grit flies.
     */
    private static void skyfall(Canvas c, Active a, float t) {
        Vec3 drop = a.origin();
        float r = Math.max(2, a.scale()), life = a.piece().ticks, alpha = fade(t, life, 2, 10), clock = clock(c);
        int impact = KineticGeometry.SKYFALL_IMPACT, hang = KineticGeometry.SKYFALL_HAUL + KineticGeometry.SKYFALL_HANG;
        Vec3 ground = drop.add(0, .07, 0);
        // The warning: a ring that fills as the haul and the hang run, full when the drop begins.
        if (t < impact + 1) {
            float fill = KineticGeometry.skyfallWarning(t);
            if (c.glow()) {
                c.ring(ground, UP, r, .1F, 0, TWO_PI, DEEP, alpha * .35F);
                c.ring(ground, UP, r, .04F, -HALF_PI, -HALF_PI + TWO_PI * fill, BLUSH, alpha * .9F);
                c.ring(ground, UP, r * fill, .02F, 0, TWO_PI, ORCHID, alpha * .35F);
            }
            if (c.soft()) c.disc(ground, UP, r, SHADOW, .12F * alpha * fill, .2F * alpha * fill);
        }
        Entity body = a.bound(c);
        if (t < impact && body != null && body.isAlive()) {
            Vec3 feet = body.getPosition(c.partial);
            float h = body.getBbHeight(), w = body.getBbWidth();
            float shake = t > KineticGeometry.SKYFALL_HAUL && t < hang ? .5F : .15F;
            if (t < hang) {
                claw(c, feet, h, w, 1, shake, alpha, clock, a.seed());
                Vec3 crown = feet.add(0, h + .5, 0);
                stroke(c, new Vec3[]{crown, crown.add(0, 2.5, 0), crown.add(0, 6, 0)}, .3F, alpha * .5F, 0);
                streaks(c, feet.add(0, h * .5, 0), UP, 2.5F * (1 - smooth(KineticGeometry.SKYFALL_HAUL - 6, KineticGeometry.SKYFALL_HAUL, t)), w + .2F, alpha, body.getId());
            } else {
                float size = KineticGeometry.handScale(w, h) * 1.3F;
                Vec3 palm = feet.add(0, h + .25, 0);
                hand(c, palm.subtract(new Vec3(0, 0, 1).scale(size * .3)), new Vec3(0, 0, 1), UP.reverse(), size, .1F, .55F, alpha, clock);
                stroke(c, new Vec3[]{palm, palm.add(0, 3, 0), palm.add(0, 7, 0)}, .35F, alpha * .5F, 0);
                streaks(c, feet.add(0, h * .5, 0), new Vec3(0, -1, 0), 5, w + .3F, alpha, body.getId());
            }
        }
        if (t >= impact) {
            float k = smooth(impact, life, t);
            dent(c, ground, UP, r * 1.5F, k, alpha);
            grit(c, ground, UP, 3.5F, t - impact, alpha, a.seed());
            if (c.soft() && !minimal()) for (int i = 0; i < 6; i++) {
                float ang = i * TWO_PI / 6 + hash(a.seed(), i) * .6F;
                double out = r * (.4 + .7 * k);
                c.cloud(ground.add(cos(ang) * out, .3 + .25 * k, sin(ang) * out), .7F + .6F * k, .45F + .3F * k, .7F + .6F * k, i & 3,
                        (t - impact) * .08F, alpha * .45F * (1 - smooth(impact + 6, life, t)), Shades.smoke(c, DUST, 0));
            }
            if (c.glow()) c.glow(ground.add(0, .6, 0), r * (1 - smooth(impact, impact + 4, t)), CORE, alpha * (1 - smooth(impact, impact + 4, t)));
        }
    }

    /** Clench (class): a great hand over the point closes slowly into a fist, dragging the ground's rings in, then crushes. */
    private static void clench(Canvas c, Active a, float t) {
        Vec3 point = a.origin();
        float r = Math.max(2, a.scale()), life = a.piece().ticks, alpha = fade(t, life, 2, 10), clock = clock(c);
        float close = smooth(0, 22, t), crushed = smooth(22, 26, t);
        float size = r * .55F;
        Vec3 wrist = point.add(0, 2.2 + 1.5 * (1 - close) - crushed * .8, 0).add(-size * .3, 0, 0);
        hand(c, wrist, new Vec3(1, -.1, 0), UP.reverse(), size, close * .95F, .7F - .5F * close, alpha * (1 - smooth(28, 36, t)), clock);
        if (t < 22 && c.glow()) for (int i = 0; i < 3; i++) {
            float k = ((t * .06F) + i / 3F) % 1;
            c.ring(point.add(0, .06, 0), UP, r * (1 - k), .04F, 0, TWO_PI, ORCHID, alpha * k * .7F);
        }
        if (t >= 22) {
            dent(c, point.add(0, 1.2, 0), UP, 3.5F, smooth(22, life, t), alpha);
            grit(c, point.add(0, .1, 0), UP, 2, t - 22, alpha, a.seed());
        }
    }

    /** Warding Hands (class): two hands circling the caster, palms out, batting away what comes close. */
    private static void ward(Canvas c, Active a, float t) {
        Entity e = a.bound(c);
        if (e == null) return;
        float alpha = fade(t, a.piece().ticks, 6, 10), clock = clock(c), r = Math.max(1.5F, a.scale());
        Vec3 centre = e.getPosition(c.partial).add(0, 1.0, 0);
        for (int s = 0; s < 2; s++) {
            float ang = (c.level.getGameTime() + c.partial) * TWO_PI / 40 + s * PI;
            Vec3 out = new Vec3(cos(ang), 0, sin(ang)), tangent = new Vec3(-sin(ang), 0, cos(ang));
            Vec3 at = centre.add(out.scale(r)).add(0, .15 * sin(clock * .2F + s), 0);
            hand(c, at.subtract(UP.scale(.35)), UP.add(tangent.scale(.3)).normalize(), out, .95F, .12F, .7F, alpha, clock + s * 3);
            if (c.glow()) c.ring(centre, UP, r, .03F, ang - 1.4F, ang - .1F, ORCHID, alpha * .45F);
        }
    }

    /** Gathering Hand (class): a hand scoops a friend up where they stand and carries them to the caster's side. */
    private static void gather(Canvas c, Active a, float t) {
        Entity friend = a.bound(c);
        float alpha = fade(t, a.piece().ticks, 2, 8), clock = clock(c);
        Vec3 feet = friend != null ? friend.getPosition(c.partial) : a.origin();
        float size = friend != null ? KineticGeometry.handScale(friend.getBbWidth(), friend.getBbHeight()) * 1.1F : 1.4F;
        Vec3 along = flat(a.target().subtract(a.origin()));
        hand(c, feet.add(0, -.15, 0).subtract(along.scale(size * .3)), along, UP, size, .45F, .5F, alpha, clock);
        if (t < 11) stroke(c, new Vec3[]{a.origin().add(0, .3, 0), a.origin().lerp(a.target(), .5).add(0, 1.4, 0), a.target().add(0, .3, 0)},
                .05F, alpha * .5F * (1 - smooth(8, 11, t)), alpha * .5F * smooth(0, 10, t));
        dent(c, a.target().add(0, .05, 0), UP, 1.2F, smooth(10, 24, t), alpha * smooth(9, 10, t));
    }

    // ----------------------------------------------------------------------------- the Puppeteer and the finishers

    /**
     * A swat: a hand of bent air, a body and a half tall, sweeps in from the far side, flattens against the foe with a
     * ripple, and follows through as it fades. Origin the foe's middle, target the way it was batted.
     */
    private static void swat(Canvas c, Active a, float t) {
        Vec3 at = a.origin(), dir = a.target().subtract(at);
        if (dir.lengthSqr() < 1e-4) return;
        dir = dir.normalize();
        float alpha = fade(t, a.piece().ticks, 1, 6), size = 1.1F * Math.max(.8F, a.scale());
        float arrive = smooth(0, 2.2F, t), follow = smooth(2, 10, t);
        Vec3 up = UP.subtract(dir.scale(UP.dot(dir)));
        up = up.lengthSqr() < 1e-3 ? perpendicular(dir) : up.normalize();
        Vec3 contact = at.subtract(dir.scale(.45));
        Vec3 palm = contact.subtract(dir.scale(1.6 * (1 - arrive))).add(dir.scale(.7 * follow));
        float flatten = sin(PI * clamp((t - 1.6F) / 4, 0, 1));
        hand(c, palm.subtract(up.scale(.35 * size)), up, dir, size, .12F * (1 - flatten), .5F, alpha * (.6F + .4F * flatten), clock(c));
        if (t > 1.6F) dent(c, contact, dir.reverse(), .5F + .9F * size, smooth(1.8F, 9, t), alpha);
    }

    /** Lift and Grip (the style's): the claw closes over the body and holds it for {@code hold} ticks (the scale), then opens. */
    private static void grasp(Canvas c, Active a, float t) {
        Entity body = a.bound(c);
        Vec3 feet = body != null ? body.getPosition(c.partial) : a.origin();
        float hold = a.scale(), close = smooth(0, 3, t), open = smooth(hold, hold + 4, t);
        claw(c, feet, body != null ? body.getBbHeight() : 1.8F, body != null ? body.getBbWidth() : .6F,
                close * (1 - open), .1F, 1, clock(c), a.seed());
    }

    /**
     * Crush and Toss ({@code telekinesis/KineticFinishers}): Crush closes the claw over the foe and squeezes it in, bands
     * of pressure tightening, then an inward ripple as it buckles; Toss carries it up in the claw, holds it, and drives it
     * into a ring on the ground. Bound to the foe; origin its feet where it stood, scale its height.
     */
    private static void finishers(Canvas c, Active a, float t) {
        float h = Math.max(.6F, a.scale()), f = fade(t, a.piece().ticks, 1, 7), clock = clock(c);
        Entity foe = a.bound(c);
        boolean alive = foe != null && foe.isAlive();
        Vec3 feet = alive ? foe.getPosition(c.partial) : a.origin();
        float w = foe != null ? foe.getBbWidth() : .6F;
        if (a.piece() == Piece.FINISH_CRUSH) {
            int land = com.jayptucker.heroesevolved.telekinesis.KineticFinishers.CRUSH_LAND;
            if (t < land + 1) {
                float squeeze = smooth(2, land, t);
                claw(c, feet, h, w, smooth(0, 3, t), squeeze, f, clock, a.seed());
                if (c.glow()) for (int i = 0; i < 3; i++) {
                    float r = (Math.max(.35F, h * .25F) + .3F) * (1.1F - .45F * squeeze) * (1 + .04F * sin(clock * 2 + i));
                    c.ring(feet.add(0, h * (.25 + .28 * i), 0), UP, r, .04F, 0, TWO_PI, ORCHID, f * (.35F + .45F * squeeze));
                }
            } else {
                dent(c, feet.add(0, h * .5, 0), UP, 1.4F, smooth(land, land + 8, t), f);
                grit(c, feet.add(0, h * .5, 0), UP, 1, t - land, f, a.seed());
            }
            return;
        }
        int high = com.jayptucker.heroesevolved.telekinesis.KineticFinishers.TOSS_HIGH, pull = com.jayptucker.heroesevolved.telekinesis.KineticFinishers.TOSS_PULL;
        int land = com.jayptucker.heroesevolved.telekinesis.KineticFinishers.TOSS_LAND;
        if (t < land) {
            claw(c, feet, h, w, smooth(0, 3, t), .25F, f, clock, a.seed());
            if (t > pull) streaks(c, feet.add(0, h * .5, 0), new Vec3(0, -1, 0), 2.5F, w + .2F, f, a.seed());
            if (c.glow() && t < high) c.ring(a.origin().add(0, .05, 0), UP, .6F + .4F * smooth(0, high, t), .04F, 0, TWO_PI, ORCHID, f * .5F);
        } else {
            Vec3 ground = a.origin().add(0, .05, 0);
            dent(c, ground, UP, 2.8F, smooth(land, land + 10, t), f);
            grit(c, ground, UP, 2, t - land, f, a.seed());
        }
    }

    /**
     * Cut Strings ({@code telekinesis/CutStrings}): thin strings run from the raised fingers to the foe's head, hands and
     * feet, swaying as it dances, and snap one by one at the cut, their halves whipping away. Bound to the foe, the
     * puppeteer's entity id in the seed; scale its height.
     */
    private static void strings(Canvas c, Active a, float t) {
        float h = Math.max(.6F, a.scale()), life = a.piece().ticks, f = fade(t, life, 2, 6);
        Entity foe = a.bound(c), owner = c.level.getEntity(a.seed());
        if (foe == null || !(owner instanceof Player p)) return;
        Vec3 feet = foe.getPosition(c.partial);
        float yaw = Mth.rotLerp(c.partial, foe instanceof LivingEntity l ? l.yBodyRotO : 0, foe instanceof LivingEntity l2 ? l2.yBodyRot : 0) * DEG_TO_RAD;
        Vec3 side = new Vec3(cos(yaw), 0, sin(yaw));
        Vec3 hand = hand(c, p, .62).add(0, .25, 0);
        Vec3[] ends = {feet.add(0, h * .97, 0), feet.add(side.scale(.38)).add(0, h * .6, 0), feet.add(side.scale(-.38)).add(0, h * .6, 0),
                feet.add(side.scale(.14)).add(0, .1, 0), feet.add(side.scale(-.14)).add(0, .1, 0)};
        int cut = com.jayptucker.heroesevolved.telekinesis.CutStrings.CUT;
        float reach = smooth(0, 6, t);
        for (int i = 0; i < ends.length; i++) {
            Vec3 finger = hand.add(side.scale((i - 2) * .05));
            float snapAt = cut + i * .6F, sway = sin((t + i * 3) * .5F) * .12F;
            Vec3 end = finger.lerp(ends[i], reach);
            if (t < snapAt) {
                Vec3 mid = finger.lerp(end, .5).add(0, -.1 + sway, 0);
                if (c.glow()) {
                    c.line(finger, mid, .02F, DEEP, f * .4F, f * .4F);
                    c.line(mid, end, .02F, DEEP, f * .4F, f * .4F);
                    c.line(finger, mid, .007F, BLUSH, f, f);
                    c.line(mid, end, .007F, BLUSH, f, f);
                    c.glow(end, .07F, CORE, f * reach);
                }
            } else if (c.glow()) {
                float k = smooth(snapAt, snapAt + 5, t), gone = f * (1 - k);
                Vec3 mid = finger.lerp(ends[i], .5);
                c.line(finger, finger.lerp(mid, 1 - k * .7).add(0, -k * .4, 0), .012F, BLUSH, gone, gone * .3F);
                c.line(ends[i], ends[i].lerp(mid, 1 - k * .7).add(0, k * .5, 0), .012F, BLUSH, gone, gone * .3F);
                if (k < .3F) c.glow(mid, .15F, CORE, f * (1 - k / .3F));
            }
        }
    }

    /** Two hands holding a blast back (a telekinetic's nuclear shield, drawn for {@code ShieldArt}). */
    static void brace(Canvas c, Vec3 at, Vec3 toward, float radius, float alpha) {
        Vec3 dir = toward.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat(toward);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        float clock = c.level.getGameTime() + c.partial;
        for (int s = -1; s <= 1; s += 2) {
            Vec3 wrist = at.add(dir.scale(radius * .7)).add(side.scale(s * radius * .4)).add(0, -radius * .3, 0);
            hand(c, wrist, UP.add(side.scale(s * .2)).normalize(), dir, radius * .8F, .1F, .6F, alpha, clock + s * 4);
        }
        dent(c, at.add(dir.scale(radius)), dir, radius, (clock * .05F) % 1, alpha * .6F);
    }
}
