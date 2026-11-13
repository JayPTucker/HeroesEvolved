package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.FirstPersonArmGeometry;
import com.jayptucker.heroesevolved.network.ClientArmPoseState;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.vibe.VibeGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.hash;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.rgb;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.smooth;

/** A tear opens from a hairline into a blue iris. Palm waves and visions are the same split edges seen in motion. */
final class VibeArt {
    private static final float[] VOID = rgb(0x07162F);
    private static final float[] DEPTH = rgb(0x183B80);
    private static final float[] BLUE = rgb(0x3096FF);
    private static final float[] ICE = rgb(0x90E6FF);
    private static final float[] WHITE = rgb(0xEDFAFF);
    private static final float[] GHOST = rgb(0x837CE8);
    private static final Set<Piece> SHUT = Set.of(Piece.VIBE_BREACH_CLOSE);
    private static final Set<Piece> GUARD_SHUT = Set.of(Piece.VIBE_GUARD_CLOSE);

    private VibeArt() {}

    static void draw(Canvas c, SetPieceClient.Active a, float t) {
        if (c.solid()) return;
        switch (a.piece()) {
            case VIBE_GATHER -> gather(c, a, t);
            case VIBE_WAVE, VIBE_CROSSWAVE -> wave(c, a, t);
            case VIBE_HIT -> hit(c, a, t);
            case VIBE_BREACH_SEED -> seed(c, a, t);
            case VIBE_BREACH_OPEN -> {
                if (!SetPieceClient.superseded(a, SHUT, a.start() + (long) t))
                    iris(c, a.origin(), facing(a), VibeGeometry.PORTAL_WIDTH, VibeGeometry.PORTAL_HEIGHT,
                            1, fade(t, 12, 2, 2), clock(a, t), false, 0);
            }
            case VIBE_BREACH_ARRIVE -> {
                float open = smooth(0, 6, t);
                float alpha = fade(t, 16, 2, 7);
                iris(c, a.origin(), facing(a), VibeGeometry.PORTAL_WIDTH, VibeGeometry.PORTAL_HEIGHT,
                        open, alpha, clock(a, t), false, 0);
                fragments(c, a, a.origin(), facing(a), t, 16, 1.25F);
            }
            case VIBE_BREACH_CLOSE, VIBE_GUARD_CLOSE -> {
                boolean guard = a.piece() == Piece.VIBE_GUARD_CLOSE;
                float open = 1 - smooth(0, 7, t);
                iris(c, a.origin(), facing(a), guard ? a.scale() : VibeGeometry.PORTAL_WIDTH,
                        VibeGeometry.PORTAL_HEIGHT, open, fade(t, 10, 0, 4), clock(a, t), guard, 0);
                if (c.glow() && t > 5)
                    c.glow(a.origin(), .63F * (1 - (t - 5) / 5), ICE, .7F * fade(t, 10, 1, 4));
            }
            case VIBE_TRANSIT -> transit(c, a, t);
            case VIBE_TOUCH -> touch(c, a, t);
            case VIBE_ECHO -> echo(c, a, t);
            case VIBE_GUARD -> guard(c, a, t);
            case VIBE_LOCK_WARN -> lock(c, a, t, false);
            case VIBE_LOCK -> lock(c, a, t, true);
            case VIBE_AWAKEN -> awaken(c, a, t);
            default -> { }
        }
    }

    private static void gather(Canvas c, SetPieceClient.Active a, float t) {
        Vec3 hand = hand(c, a, false);
        Vec3 direction = a.target().subtract(hand).normalize();
        float gather = smooth(0, a.scale(), t), alpha = fade(t, Math.min(9, a.scale() + 3), 1, 2);
        if (alpha <= 0) return;
        front(c, hand.add(direction.scale(.1)), direction, .33F - .18F * gather,
                .27F - .1F * gather, t * .31F, alpha, .45F);
        if (c.glow()) c.glow(hand, .16F + gather * .2F, BLUE, alpha * .78F);
    }

    private static void wave(Canvas c, SetPieceClient.Active a, float t) {
        Vec3 from = t < 2.5F ? hand(c, a, a.piece() == Piece.VIBE_CROSSWAVE).lerp(a.origin(), t / 2.5F)
                : a.origin();
        Vec3 line = a.target().subtract(from);
        if (line.lengthSqr() < .04) return;
        Vec3 direction = line.normalize();
        double travel = VibeGeometry.waveTravel(a.origin().distanceTo(a.target()));
        int count = minimal() ? 2 : 5;
        for (int k = 0; k < count; k++) {
            float age = t - k * .85F;
            float p = (float) (age / travel);
            if (p < 0 || p > 1.25) continue;
            float along = Math.min(1, p);
            float width = (.27F + .73F * Mth.sqrt(along)) * a.scale();
            float alpha = fade(age, (float) (travel * 1.25), 1, 3) * (1 - k * .13F);
            Vec3 center = from.add(line.scale(along));
            alpha *= smooth(.15F, .85F, (float) center.distanceTo(c.camera()));
            front(c, center, direction, width, width * .75F, k * 1.07F + t * .12F, alpha, .6F);
            if (c.glow() && k == 0) c.glow(center, width * .9F, BLUE, alpha * .38F);
        }
        if (!c.glow() || minimal()) return;
        float p = Math.min(1, (float) (t / travel));
        Vec3 side = VibeGeometry.side(direction);
        Vec3 up = side.cross(direction);
        for (int strand = 0; strand < 2; strand++) {
            Vec3[] points = new Vec3[15];
            for (int i = 0; i < points.length; i++) {
                float f = p * i / (points.length - 1);
                float angle = f * 14 - t * .65F + strand * Mth.PI;
                double radius = (.14 + f * .6) * a.scale();
                points[i] = from.add(line.scale(f)).add(side.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
            }
            stroke(c, points, .028F, fade(t, 18, 2, 9) * .7F, BLUE);
        }
    }

    private static void seed(Canvas c, SetPieceClient.Active a, float t) {
        Vec3 direction = facing(a);
        float alpha = fade(t, 14, 1, 4), p = smooth(0, VibeGeometry.BREACH_OPEN_TICKS, t);
        if (c.glow()) {
            for (boolean off : new boolean[]{false, true}) {
                Vec3 from = hand(c, a, off);
                filament(c, from, from.lerp(a.target(), p), .10F, t + (off ? 2 : 0), alpha, BLUE);
            }
        }
        iris(c, a.target(), direction, VibeGeometry.PORTAL_WIDTH, VibeGeometry.PORTAL_HEIGHT,
                p * .62F, alpha, clock(a, t), false, 0);
    }

    private static void guard(Canvas c, SetPieceClient.Active a, float t) {
        if (SetPieceClient.superseded(a, GUARD_SHUT, a.start() + (long) t)) return;
        int stored = (int) (a.scale() / 4);
        float width = a.scale() - stored * 4;
        Vec3 normal = facing(a);
        Vec3 center = a.origin();
        if (a.bound(c) instanceof LivingEntity owner) {
            Vec3 look = owner.getViewVector(c.partial).multiply(1, 0, 1).normalize();
            if (look.lengthSqr() > .01) normal = look;
            center = owner.getPosition(c.partial).add(0, owner.isCrouching() ? 1 : 1.23, 0).add(normal.scale(1.48));
        }
        float alpha = fade(t, 10, 2, 2);
        iris(c, center, normal, width, VibeGeometry.PORTAL_HEIGHT, 1, alpha, clock(a, t), true, stored);
    }

    private static void iris(Canvas c, Vec3 at, Vec3 normal, double width, double height, float open, float alpha,
                             float time, boolean guard, int stored) {
        if (alpha <= .005F) return;
        Vec3 side = VibeGeometry.side(normal);
        Vec3 up = side.cross(normal).normalize();
        double x = width * (.025 + .975 * open * open), y = height * (.38 + .62 * open);
        float near = smooth(.28F, 1.05F, (float) at.distanceTo(c.camera()));
        if (c.soft()) {
            int sides = 40;
            for (int i = 0; i < sides; i++) {
                float a = Mth.TWO_PI * i / sides, b = Mth.TWO_PI * (i + 1) / sides;
                Vec3 edge = lip(at, side, up, x, y, a, time);
                Vec3 next = lip(at, side, up, x, y, b, time);
                c.tri(at, edge, next, VOID, alpha * near * (guard ? .20F : .82F),
                        alpha * near * .46F, alpha * near * .46F);
            }
            return;
        }
        float clock = time * .07F;
        for (int arc = 0; arc < (minimal() ? 2 : 3); arc++) {
            float start = arc * Mth.TWO_PI / 3 + clock;
            Vec3[] edge = new Vec3[28];
            for (int i = 0; i < edge.length; i++) {
                float angle = start + 2.43F * i / (edge.length - 1);
                edge[i] = lip(at, side, up, x, y, angle, time)
                        .add(normal.scale(.05 * Math.sin(angle * 3 - time * .2F)));
            }
            stroke(c, edge, guard ? .042F : .048F, alpha * .94F, arc == 2 ? ICE : BLUE);
        }
        if (open < .4F || minimal()) return;
        int layers = guard ? 2 + Math.min(4, stored) : 3;
        for (int lane = 0; lane < layers; lane++) {
            float phase = time * (guard ? -.17F : .19F) + lane * 1.89F;
            Vec3[] trace = new Vec3[24];
            for (int i = 0; i < trace.length; i++) {
                float f = i / (float) (trace.length - 1), angle = phase + f * 2.9F;
                double radius = 1 - (guard ? .52 : .9) * f;
                trace[i] = at.add(side.scale(Math.cos(angle) * x * radius)).add(up.scale(Math.sin(angle) * y * radius))
                        .add(normal.scale(.13 * Mth.sin(f * Mth.PI)));
            }
            stroke(c, trace, .024F + stored * .0014F, alpha * near * (guard ? .36F : .49F), lane % 2 == 0 ? ICE : GHOST);
        }
        if (stored > 0) c.glow(at, .35F + stored * .047F, BLUE, alpha * near * .65F);
    }

    private static Vec3 lip(Vec3 at, Vec3 side, Vec3 up, double x, double y, float angle, float time) {
        double ripple = 1 + .043 * Math.sin(angle * 7 - time * .18) + .023 * Math.sin(angle * 11 + time * .29);
        return at.add(side.scale(Math.cos(angle) * x * ripple)).add(up.scale(Math.sin(angle) * y * ripple));
    }

    private static void front(Canvas c, Vec3 at, Vec3 normal, float x, float y, float phase, float alpha, float split) {
        Vec3 side = VibeGeometry.side(normal), up = side.cross(normal).normalize();
        for (int wing = 0; wing < 2; wing++) {
            Vec3[] edge = new Vec3[23];
            for (int i = 0; i < edge.length; i++) {
                float f = i / (float) (edge.length - 1), angle = phase + wing * Mth.PI + f * 2.65F;
                edge[i] = at.add(side.scale(x * Math.cos(angle))).add(up.scale(y * Math.sin(angle)))
                        .add(normal.scale(Math.sin(f * Mth.PI) * x * split));
            }
            if (c.glow()) stroke(c, edge, .036F, alpha, wing == 0 ? ICE : BLUE);
            else c.ribbon(edge, .085F, DEPTH, .3F * alpha, .1F * alpha);
        }
    }

    private static void hit(Canvas c, SetPieceClient.Active a, float t) {
        Vec3 at = a.origin();
        Vec3 normal = a.target().subtract(at).normalize();
        float p = smooth(0, 12, t), alpha = fade(t, 14, 1, 9);
        front(c, at, normal, (.22F + p) * a.scale(), (.22F + p * .75F) * a.scale(),
                t * .22F, alpha, .27F);
        if (c.glow()) c.glow(at, a.scale() * (.35F + p * .6F), ICE, alpha * .72F);
        fragments(c, a, at, normal, t, 14, a.scale());
    }

    private static void transit(Canvas c, SetPieceClient.Active a, float t) {
        Vec3 facing = facing(a);
        Vec3 at = a.origin();
        float alpha = fade(t, 18, 1, 10);
        for (int i = 0; i < (minimal() ? 1 : 3); i++) {
            float age = t - i * 1.7F;
            if (age < 0) continue;
            float spread = smooth(0, 12, age);
            Vec3 point = at.add(facing.scale(spread * (i + 1) * .47));
            front(c, point, facing, .88F * (1 - spread * .5F), 1.21F * (1 - spread * .5F),
                    i + t * .14F, alpha * (1 - i * .2F), .18F);
        }
        fragments(c, a, at, facing, t, 18, 1.05F);
    }

    private static void touch(Canvas c, SetPieceClient.Active a, float t) {
        if (!c.glow()) return;
        Vec3 hand = hand(c, a, false);
        float alpha = fade(t, 24, 3, 12), p = smooth(0, 7, t);
        filament(c, hand, hand.lerp(a.target(), p), .12F, t, alpha, GHOST);
        Vec3 at = a.target();
        front(c, at, VibeGeometry.UP, .26F + p * .38F, .26F + p * .38F, t * .12F, alpha, .15F);
    }

    private static void echo(Canvas c, SetPieceClient.Active a, float t) {
        Vec3 from = a.origin(), to = a.target(), axis = to.subtract(from).normalize();
        float alpha = fade(t, 29, 2, 10);
        float p = smooth(2, 15, t);
        if (c.glow()) {
            filament(c, from, from.lerp(to, p), .28F, t, alpha, GHOST);
            c.glow(from.lerp(to, p), .42F, ICE, alpha * .6F);
        }
        echoBody(c, from, axis, alpha * .63F, t, a.seed());
        if (p > .5F && from.distanceToSqr(to) > 1) echoBody(c, to, axis.reverse(), alpha, t + 3, a.seed() + 7);
    }

    private static void echoBody(Canvas c, Vec3 at, Vec3 normal, float alpha, float t, int seed) {
        Vec3 level = normal.multiply(1, 0, 1).normalize();
        if (level.lengthSqr() < .1) level = new Vec3(0, 0, 1);
        Vec3 side = VibeGeometry.side(level);
        float glitch = (hash(seed, (int) (t / 3)) - .5F) * .24F;
        Vec3 chest = at.add(side.scale(glitch)), head = chest.add(0, .61, 0);
        if (c.soft()) {
            c.box(head, .19F, .20F, .14F, 0, 0, 0, DEPTH, alpha * .38F);
            c.box(chest, .25F, .32F, .13F, 0, 0, 0, DEPTH, alpha * .32F);
        } else {
            front(c, head, level, .19F, .22F, .8F, alpha * .74F, .2F);
            for (int sign : new int[]{-1, 1}) {
                Vec3 shoulder = chest.add(side.scale(.29 * sign)).add(0, .23, 0);
                Vec3 hip = chest.add(side.scale(.19 * sign)).add(0, -.29, 0);
                stroke(c, new Vec3[]{shoulder.add(side.scale(.12 * sign)).add(0, -.39, 0), shoulder,
                        chest.add(0, .25, 0), hip, hip.add(0, -.56, .08 * sign)}, .025F, alpha * .67F, GHOST);
            }
        }
    }

    private static void lock(Canvas c, SetPieceClient.Active a, float t, boolean released) {
        Vec3 at = a.origin().add(0, .065, 0);
        float r = a.scale();
        float alpha = released ? fade(t, 33, 1, 19) : fade(t, VibeGeometry.LOCK_WARNING_TICKS, 2, 0);
        if (c.soft()) {
            c.disc(at, VibeGeometry.UP, r, VOID, alpha * .43F, alpha * .09F);
            return;
        }
        float progress = released ? smooth(0, 12, t) : Mth.clamp(t / VibeGeometry.LOCK_WARNING_TICKS, 0, 1);
        c.ring(at, r, .075F, BLUE, alpha * .85F);
        c.ring(at, VibeGeometry.UP, r - .17F, .034F, 0, Mth.TWO_PI * progress, WHITE, alpha);
        int bands = minimal() ? 2 : 4;
        for (int i = 0; i < bands; i++) {
            float phase = i * Mth.TWO_PI / bands + t * (released ? -.14F : .12F);
            float reach = released ? r * (.95F - .87F * progress) : r * (.65F + .24F * progress);
            Vec3 direction = new Vec3(Math.cos(phase), 0, Math.sin(phase));
            Vec3 start = at.add(direction.scale(reach)).add(0, released ? .3 + progress * 1.8 : .13, 0);
            Vec3 normal = released ? direction : VibeGeometry.UP;
            front(c, start, normal, released ? .6F + progress : .55F, released ? 1.3F + progress : .31F,
                    phase, alpha * .85F, .28F);
        }
        if (released) c.glow(at.add(0, 1, 0), r * (.32F + progress * .17F), ICE, alpha * .52F);
    }

    private static void awaken(Canvas c, SetPieceClient.Active a, float t) {
        Vec3 at = a.at(c).add(0, 1.24, 0);
        Vec3 direction = a.target().subtract(a.origin()).multiply(1, 0, 1).normalize();
        if (direction.lengthSqr() < .1) direction = a.bound(c) instanceof LivingEntity owner
                ? Vec3.directionFromRotation(0, owner.getYRot()) : new Vec3(0, 0, 1);
        float p = smooth(0, 27, t);
        Vec3 offset = VibeGeometry.side(direction).scale(1.25 + .53 * p);
        for (int sign : new int[]{-1, 1}) {
            Vec3 side = at.add(offset.scale(sign)).add(direction.scale(.7));
            iris(c, side, direction, .68, 1.02, .17F + p * .77F, fade(t, 28, 4, 0) * .75F, clock(a, t), false, 0);
            if (c.glow()) filament(c, side, at.add(0, .32, 0), .16F, t + sign, p * .75F, GHOST);
        }
    }

    private static void fragments(Canvas c, SetPieceClient.Active a, Vec3 at, Vec3 normal, float t, int life, float reach) {
        if (!c.glow() || minimal()) return;
        float p = t / life, alpha = fade(t, life, 2, life / 2F);
        Vec3 side = VibeGeometry.side(normal), up = side.cross(normal).normalize();
        for (int i = 0; i < 8; i++) {
            double angle = hash(a.seed(), i) * Mth.TWO_PI;
            Vec3 radial = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            Vec3 point = at.add(radial.scale(reach * (.45 + p * (1 + hash(a.seed(), i + 11)))))
                    .add(normal.scale(p * hash(a.seed(), i + 23) * .85));
            c.line(point, point.add(radial.scale(.08 + (1 - p) * .19)), .017F, ICE, alpha * .8F, 0);
        }
    }

    private static void filament(Canvas c, Vec3 from, Vec3 to, float drift, float t, float alpha, float[] color) {
        Vec3 line = to.subtract(from);
        if (line.lengthSqr() < .0025) return;
        Vec3 side = VibeGeometry.side(line.normalize());
        Vec3[] points = new Vec3[19];
        for (int i = 0; i < points.length; i++) {
            float p = i / (float) (points.length - 1);
            points[i] = from.add(line.scale(p)).add(side.scale(drift * Mth.sin(p * 21 - t * .61F) * Mth.sin(p * Mth.PI)));
        }
        stroke(c, points, .027F, alpha, color);
    }

    private static void stroke(Canvas c, Vec3[] points, float width, float alpha, float[] tint) {
        if (alpha <= .005F) return;
        float[] widths = new float[points.length], opacity = new float[points.length];
        for (int i = 0; i < points.length; i++) {
            float p = i / (float) (points.length - 1);
            float taper = .18F + .82F * Mth.sin(p * Mth.PI);
            opacity[i] = alpha * taper * smooth(.12F, .65F, (float) points[i].distanceTo(c.camera()));
            widths[i] = width * taper * 3.1F;
        }
        c.ribbon(points, widths, DEPTH, opacity);
        for (int i = 0; i < widths.length; i++) widths[i] *= .39F;
        c.ribbon(points, widths, tint, opacity);
        for (int i = 0; i < widths.length; i++) widths[i] *= .34F;
        c.ribbon(points, widths, WHITE, opacity);
    }

    private static Vec3 hand(Canvas c, SetPieceClient.Active a, boolean offHand) {
        if (!(a.bound(c) instanceof LivingEntity owner)) return a.origin();
        var mc = Minecraft.getInstance();
        Vec3 aim = a.target().subtract(owner.getEyePosition(c.partial)).normalize();
        if (aim.lengthSqr() < .1) aim = owner.getViewVector(c.partial);
        if (owner != mc.player || !mc.options.getCameraType().isFirstPerson()
                || owner.position().distanceToSqr(a.origin()) > 9)
            return owner.position().distanceToSqr(a.origin()) > 9 ? a.origin() : VibeGeometry.hand(owner, aim, c.partial, offHand);
        Vec3 look = owner.getViewVector(c.partial);
        Vec3 side = VibeGeometry.side(look);
        HumanoidArm arm = offHand ? owner.getMainArm().getOpposite() : owner.getMainArm();
        var pose = ClientArmPoseState.get(owner.getId());
        Vec3 palm = pose != null && pose.name().startsWith("VIBE_")
                ? FirstPersonArmGeometry.vibePalm(arm, pose, ClientArmPoseState.ageTicks(owner.getId()) + c.partial)
                : new Vec3(arm == HumanoidArm.RIGHT ? .33 : -.33, -.33, -.76);
        return c.camera().add(look.scale(-palm.z)).add(side.scale(palm.x)).add(side.cross(look).scale(palm.y));
    }

    private static Vec3 facing(SetPieceClient.Active a) { return a.facing(); }
    private static float clock(SetPieceClient.Active a, float age) { return (float) (a.start() % 24000) + age; }
    private static boolean minimal() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.options.particles().get() == ParticleStatus.MINIMAL;
    }

    private static float fade(float age, float life, float in, float out) {
        if (age < 0 || age >= life) return 0;
        return (in > 0 ? smooth(0, in, age) : 1) * (out > 0 ? 1 - smooth(life - out, life, age) : 1);
    }
}
