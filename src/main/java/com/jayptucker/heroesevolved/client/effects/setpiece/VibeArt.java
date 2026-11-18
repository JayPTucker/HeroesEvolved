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
    private static final Set<Piece> CHANNEL = Set.of(Piece.VIBE_BREACH_CHANNEL);
    private static final Set<Piece> STREAM = Set.of(Piece.VIBE_STREAM);

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
                            1, fade(t, 12, 2, 2), clock(a, t), false, 0, window(a));
            }
            case VIBE_BREACH_ARRIVE -> {
                // A channelled mouth (scale 2) is already open when it arrives: it only flares.
                float open = a.scale() >= 1.5F ? 1 : smooth(0, 6, t);
                float alpha = fade(t, 16, 2, 7);
                iris(c, a.origin(), facing(a), VibeGeometry.PORTAL_WIDTH, VibeGeometry.PORTAL_HEIGHT,
                        open, alpha, clock(a, t), false, 0, window(a));
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
            case VIBE_BEAST -> beast(c, a, t);
            case VIBE_KICK -> kick(c, a, t);
            case VIBE_SEAM -> seam(c, a, t);
            case VIBE_BREACH_CHANNEL -> channel(c, a, t);
            case VIBE_PROJECT -> project(c, a, t);
            case VIBE_HOP -> hop(c, a, t);
            case VIBE_STREAM -> stream(c, a, t);
            case VIBE_RIFT_ZONE -> riftZone(c, a, t);
            case VIBE_RIFT_SKY -> riftSky(c, a, t);
            case VIBE_RIFT_FALL -> riftFall(c, a, t);
            default -> { }
        }
    }

    /**
     * A breach channelled from the palm, as Cisco opens one: a crackling blue blast held from the hand to a point in the
     * air, where a tear swirls open from a spark into a full iris, its rim spitting little arcs. Re-sent every two ticks;
     * the newest copy takes over, and each grows the tear on the channel's own clock (scale 1 + progress, seed length).
     */
    private static void channel(Canvas c, SetPieceClient.Active a, float t) {
        if (SetPieceClient.superseded(a, CHANNEL, a.start() + (long) t)) return;
        float length = Math.max(4, a.seed());
        float p = Mth.clamp(a.scale() - 1 + t / length, 0, 1);
        float alpha = fade(t, 5, 0, 1.5F);
        if (alpha <= .005F) return;
        Vec3 normal = facing(a), center = a.origin();
        float clock = clock(a, t);
        // The tear opens two steps in front of the viber: from their own eyes it must not flood the view.
        float near = smooth(1.4F, 3.4F, (float) center.distanceTo(c.camera()));
        float grow = .12F + .88F * (float) Math.pow(p, .8);
        double width = VibeGeometry.PORTAL_WIDTH * grow, height = VibeGeometry.PORTAL_HEIGHT * grow;
        iris(c, center, normal, width, height, (float) Math.pow(p, .6), alpha, clock * 1.6F, false, 0);
        if (!c.glow()) return;
        Vec3 hand = hand(c, a, false);
        Vec3 line = center.subtract(hand);
        if (line.lengthSqr() > .04) {
            Vec3 direction = line.normalize();
            // The blast: a white core, two jagged bolts re-struck every other tick, and fronts riding it to the tear.
            filament(c, hand, center, .04F, t * 2, alpha * .9F, WHITE);
            Vec3 side = VibeGeometry.side(direction), up = side.cross(direction).normalize();
            int strike = (int) (clock / 2);
            for (int bolt = 0; bolt < (minimal() ? 1 : 2); bolt++) {
                Vec3[] points = new Vec3[9];
                for (int i = 0; i < points.length; i++) {
                    float f = i / (float) (points.length - 1), envelope = Mth.sin(f * Mth.PI);
                    float jx = (hash(a.seed() + bolt * 31 + i, strike) - .5F) * .32F * envelope;
                    float jy = (hash(a.seed() + bolt * 57 + i * 3, strike + 7) - .5F) * .32F * envelope;
                    points[i] = hand.add(line.scale(f)).add(side.scale(jx)).add(up.scale(jy));
                }
                stroke(c, points, .03F, alpha * .85F, bolt == 0 ? BLUE : ICE);
            }
            for (int k = 0; k < (minimal() ? 2 : 4); k++) {
                float f = (clock * .22F + k / 4F) % 1;
                float r = .1F + .2F * f;
                front(c, hand.add(line.scale(f)), direction, r, r * .8F, clock * .3F + k, alpha * (1 - .35F * f), .35F);
            }
            c.glow(hand, .22F + .08F * Mth.sin(clock * 1.7F), BLUE, alpha * .8F);
            c.glow(hand, .09F, WHITE, alpha);
        }
        // The tear's rim crackles: short arcs jump round it, faster as it opens.
        Vec3 side = VibeGeometry.side(normal), up = side.cross(normal).normalize();
        int frame = (int) (clock / 1.5F);
        int arcs = minimal() ? 2 : 3 + (int) (p * 4);
        for (int k = 0; k < arcs; k++) {
            float start = hash(a.seed() + k * 13, frame) * Mth.TWO_PI, span = .5F + hash(a.seed() + k * 7, frame + 3) * .9F;
            Vec3[] points = new Vec3[7];
            for (int i = 0; i < points.length; i++) {
                float angle = start + span * i / (points.length - 1);
                double out = 1.04 + (hash(a.seed() + k * 5 + i, frame + 11) - .3F) * .22;
                points[i] = center.add(side.scale(Math.cos(angle) * width * out)).add(up.scale(Math.sin(angle) * height * out));
            }
            stroke(c, points, .022F, alpha * (.5F + .5F * p), k % 2 == 0 ? ICE : WHITE);
        }
        // An inward swirl, the far side not yet showing through.
        for (int arm = 0; arm < (minimal() ? 1 : 2); arm++) {
            Vec3[] spiral = new Vec3[18];
            for (int i = 0; i < spiral.length; i++) {
                float f = i / (float) (spiral.length - 1), angle = -clock * .5F + arm * Mth.PI + f * 4.4F;
                double r = 1 - .92 * f;
                spiral[i] = center.add(side.scale(Math.cos(angle) * width * r)).add(up.scale(Math.sin(angle) * height * r))
                        .add(normal.scale(-.06 * f));
            }
            stroke(c, spiral, .02F, alpha * .55F * p * (.4F + .6F * near), arm == 0 ? ICE : GHOST);
        }
        c.glow(center, (float) (.25F + .6F * width), BLUE, alpha * (.35F + .3F * p) * (.25F + .75F * near));
        if (p < .25F) c.glow(center, .2F, WHITE, alpha * (1 - p * 4) * near);
    }

    /**
     * Breach Hop: the near tear snaps open beside the hopper and pinches shut behind them, the far one opens as they
     * dive in, and for the moment they are inside, fronts streak from one to the other.
     */
    private static void hop(Canvas c, SetPieceClient.Active a, float t) {
        Vec3 entry = a.origin(), exit = a.target(), line = exit.subtract(entry);
        if (line.lengthSqr() < .25) return;
        Vec3 direction = line.normalize();
        float clock = clock(a, t) * 1.6F;
        float in = smooth(0, 1.5F, t) * (1 - smooth(3, 6, t)), out = smooth(1.2F, 2.6F, t) * (1 - smooth(5, 9, t));
        if (in > .01F) iris(c, entry, direction, VibeGeometry.PORTAL_WIDTH, VibeGeometry.PORTAL_HEIGHT, in, fade(t, 7, 0, 2), clock, false, 0);
        if (out > .01F) iris(c, exit, direction, VibeGeometry.PORTAL_WIDTH, VibeGeometry.PORTAL_HEIGHT, out, fade(t, 10, 1, 3), clock, false, 0);
        if (c.glow() && t >= 1 && t < 5) {
            float k = smooth(1, 4, t), alpha = fade(t - 1, 4, .5F, 1.5F);
            filament(c, entry, exit, .12F, t * 2, alpha * .6F, ICE);
            for (int i = 0; i < (minimal() ? 1 : 3); i++) {
                float f = Mth.clamp(k - i * .14F, 0, 1);
                front(c, entry.lerp(exit, f), direction, .42F - i * .08F, .55F - i * .1F, clock * .3F + i, alpha * (1 - i * .25F), .35F);
            }
        }
        if (t > 2) fragments(c, a, exit, direction, t - 2, 8, 1.1F);
    }

    /**
     * A held Vibe Blast: a steady, crackling blast from the palm to whatever stops it. Bound to the viber it starts at the
     * palm they see; unbound (the leg out of a breach) it runs from origin to target. Re-sent every two ticks.
     */
    private static void stream(Canvas c, SetPieceClient.Active a, float t) {
        if (a.entity() >= 0 && SetPieceClient.superseded(a, STREAM, a.start() + (long) t)) return;
        float alpha = fade(t, 5, 0, 1.5F) * (a.entity() >= 0 ? 1 : .6F);
        if (alpha <= .005F) return;
        Vec3 from, to;
        if (a.bound(c) instanceof LivingEntity owner) {
            Vec3 aim = a.target().subtract(a.origin());
            aim = aim.lengthSqr() < 1e-6 ? owner.getViewVector(c.partial) : aim.normalize();
            from = palm(c, owner, aim, false);
            to = a.origin();
        } else {
            from = a.origin();
            to = a.target();
        }
        Vec3 line = to.subtract(from);
        if (line.lengthSqr() < .04) return;
        Vec3 direction = line.normalize();
        float clock = clock(a, t), hot = a.scale() > 1.05F ? 1.3F : 1;
        if (c.soft()) {
            // A faint body of pressed air round the blast, so it reads against a bright sky.
            c.ribbon(new Vec3[]{from, from.lerp(to, .5), to}, .16F * hot, DEPTH, .18F * alpha, .05F * alpha);
            return;
        }
        if (!c.glow()) return;
        filament(c, from, to, .03F, clock * 2.2F, alpha, WHITE);
        Vec3 side = VibeGeometry.side(direction), up = side.cross(direction).normalize();
        int strike = (int) (clock / 2);
        double length = line.length();
        int points = Math.max(6, (int) (length * 2.2));
        for (int bolt = 0; bolt < (minimal() ? 1 : 3); bolt++) {
            Vec3[] path = new Vec3[points];
            for (int i = 0; i < points; i++) {
                float f = i / (float) (points - 1), envelope = .35F + .65F * Mth.sin(f * Mth.PI);
                float jx = (hash(a.seed() + bolt * 31 + i, strike + bolt) - .5F) * .34F * envelope * hot;
                float jy = (hash(a.seed() + bolt * 57 + i * 3, strike + 7 + bolt) - .5F) * .34F * envelope * hot;
                path[i] = from.add(line.scale(f)).add(side.scale(jx)).add(up.scale(jy));
            }
            stroke(c, path, .034F * hot, alpha * (bolt == 0 ? .95F : .7F), bolt == 1 ? ICE : BLUE);
        }
        // Wave fronts ride the blast out to its end.
        int fronts = minimal() ? 2 : Math.max(3, (int) (length / 1.6));
        for (int k = 0; k < fronts; k++) {
            float f = (clock * (2.4F / (float) Math.max(2, length)) + k / (float) fronts) % 1;
            float r = (.14F + .26F * f) * hot;
            Vec3 at = from.add(line.scale(f));
            // Fronts right by the camera (your own palm, in first person) would fill the view: let them fade there.
            float near = smooth(.7F, 2.4F, (float) at.distanceTo(c.camera()));
            front(c, at, direction, r, r * .8F, clock * .3F + k, alpha * (1 - .4F * f) * near, .4F);
        }
        float palmNear = smooth(.5F, 1.8F, (float) from.distanceTo(c.camera()));
        c.glow(from, (.22F + .06F * Mth.sin(clock * 1.9F)) * hot * (.4F + .6F * palmNear), BLUE, alpha * .85F * (.35F + .65F * palmNear));
        c.glow(from, .1F, WHITE, alpha * (.35F + .65F * palmNear));
        // Where it lands it splashes back.
        c.glow(to, .45F * hot, ICE, alpha * .7F);
        front(c, to.subtract(direction.scale(.1)), direction.reverse(), .3F * hot, .26F * hot, clock * .5F, alpha * .8F, .5F);
    }

    /**
     * Rift Fall's warning: the zone on the ground, its rim filling white as the time runs out, hairline tears flickering
     * open across the floor (more as it fills), and thin columns of light rising from the rim toward the sky breach.
     */
    private static void riftZone(Canvas c, SetPieceClient.Active a, float t) {
        Vec3 at = a.origin().add(0, .07, 0);
        float r = a.scale(), life = Piece.VIBE_RIFT_ZONE.ticks;
        float alpha = fade(t, life, 2, 1), p = Mth.clamp(t / life, 0, 1);
        if (c.soft()) {
            c.disc(at, VibeGeometry.UP, r, VOID, alpha * .42F, alpha * .1F);
            return;
        }
        if (!c.glow()) return;
        c.ring(at, r, .08F, BLUE, alpha * .9F);
        c.ring(at, VibeGeometry.UP, r - .18F, .04F, 0, Mth.TWO_PI * p, WHITE, alpha);
        int frame = (int) (clock(a, t) / 2);
        int tears = (minimal() ? 2 : 4) + (int) (p * 8);
        for (int k = 0; k < tears; k++) {
            float angle = hash(a.seed() + k, frame) * Mth.TWO_PI, reach = (float) Math.sqrt(hash(a.seed() + k * 7, frame)) * (r - .4F);
            Vec3 middle = at.add(Math.cos(angle) * reach, 0, Math.sin(angle) * reach);
            Vec3 along = new Vec3(-Math.sin(angle * 3), 0, Math.cos(angle * 3));
            Vec3[] crack = new Vec3[5];
            for (int i = 0; i < crack.length; i++) {
                float f = i / 4F - .5F;
                crack[i] = middle.add(along.scale(f * .9)).add(new Vec3(along.z, 0, -along.x).scale((hash(k * 13 + i, frame) - .5F) * .2F));
            }
            stroke(c, crack, .02F, alpha * (.4F + .6F * p), k % 2 == 0 ? ICE : BLUE);
        }
        int columns = minimal() ? 6 : 14;
        for (int i = 0; i < columns; i++) {
            float angle = i * Mth.TWO_PI / columns + t * .05F;
            Vec3 foot = at.add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
            c.line(foot, foot.add(0, .4 + 2.2 * p, 0), .025F, ICE, alpha * (.25F + .35F * p), 0);
        }
    }

    /**
     * Rift Fall's breach in the sky: a great iris facing down at the zone, its rim spitting arcs, fed at first by a blast
     * from each of the caster's palms. Once the ground opens, a faint shaft of light falls from it.
     */
    private static void riftSky(Canvas c, SetPieceClient.Active a, float t) {
        float life = Math.max(20, a.seed()), r = a.scale();
        float open = smooth(0, 14, t) * (1 - smooth(life - 10, life, t)), alpha = fade(t, life, 2, 6);
        if (alpha <= .005F || open <= .01F) return;
        Vec3 sky = a.origin(), normal = facing(a);
        float clock = clock(a, t);
        iris(c, sky, normal, r, r, open, alpha, clock * 1.2F, false, 0);
        if (!c.glow()) return;
        Vec3 side = VibeGeometry.side(normal), up = side.cross(normal).normalize();
        int frame = (int) (clock / 1.5F);
        for (int k = 0; k < (minimal() ? 3 : 7); k++) {
            float start = hash(k * 11, frame) * Mth.TWO_PI, span = .4F + hash(k * 5, frame + 3) * .8F;
            Vec3[] points = new Vec3[7];
            for (int i = 0; i < points.length; i++) {
                float angle = start + span * i / (points.length - 1);
                double out = (1.05 + (hash(k * 3 + i, frame + 9) - .3F) * .25) * open;
                points[i] = sky.add(side.scale(Math.cos(angle) * r * out)).add(up.scale(Math.sin(angle) * r * out));
            }
            stroke(c, points, .035F, alpha * .8F, k % 2 == 0 ? ICE : WHITE);
        }
        c.glow(sky, r * 1.1F * open, BLUE, alpha * .4F);
        // The caster's palms feed it while the ring fills.
        if (t < 23 && a.bound(c) instanceof LivingEntity owner) {
            float feed = fade(t, 23, 2, 3);
            for (boolean off : new boolean[]{false, true}) {
                Vec3 hand = palm(c, owner, sky.subtract(owner.getEyePosition(c.partial)).normalize(), off);
                filament(c, hand, sky, .06F, clock * 2 + (off ? 3 : 0), alpha * feed * .9F, WHITE);
                Vec3 line = sky.subtract(hand);
                Vec3 across = VibeGeometry.side(line.normalize()), lift = across.cross(line.normalize());
                Vec3[] bolt = new Vec3[10];
                for (int i = 0; i < bolt.length; i++) {
                    float f = i / 9F, envelope = Mth.sin(f * Mth.PI);
                    bolt[i] = hand.add(line.scale(f)).add(across.scale((hash(i + (off ? 40 : 0), frame) - .5F) * .5F * envelope))
                            .add(lift.scale((hash(i * 3 + (off ? 70 : 0), frame + 5) - .5F) * .5F * envelope));
                }
                stroke(c, bolt, .03F, alpha * feed * .8F, BLUE);
                c.glow(hand, .2F, BLUE, alpha * feed * .8F);
            }
        }
        // The shaft of light the bodies fall down.
        if (t >= 20 && t < life - 10) {
            float shaft = fade(t - 20, life - 30, 3, 6) * .22F;
            Vec3 ground = a.target();
            for (int i = 0; i < 3; i++) {
                float angle = clock * .08F + i * Mth.TWO_PI / 3;
                Vec3 offset = side.scale(Math.cos(angle) * r * .5).add(up.scale(Math.sin(angle) * r * .5));
                c.line(sky.add(offset), ground.add(offset), .05F, ICE, alpha * shaft, 0);
            }
        }
    }

    /** One body's plunge out of the sky breach: a streak of fronts and light above it, and a flash where it lands. */
    private static void riftFall(Canvas c, SetPieceClient.Active a, float t) {
        if (!c.glow()) return;
        int fall = com.jayptucker.heroesevolved.vibe.RiftFall.FALL;
        float h = Math.max(.6F, a.scale());
        Vec3 top = a.origin(), ground = a.target();
        Vec3 body;
        if (a.bound(c) instanceof LivingEntity foe && t < fall + 1) body = foe.getPosition(c.partial);
        else {
            float f = Mth.clamp(t / fall, 0, 1);
            body = new Vec3(top.x, top.y + (ground.y - top.y) * f * f, top.z);
        }
        if (t < fall + 1) {
            float alpha = fade(t, fall + 1, 1, 1);
            Vec3 head = body.add(0, h, 0);
            double trail = Math.min(head.distanceTo(top.add(0, h, 0)) + .5, 4.5);
            Vec3 tail = head.add(0, trail, 0);
            filament(c, head, tail, .06F, t * 3, alpha * .8F, WHITE);
            for (int k = 0; k < (minimal() ? 2 : 4); k++) {
                float f = k / 4F;
                front(c, head.add(0, trail * f, 0), new Vec3(0, 1, 0), (.5F - .08F * k) * Math.min(1.4F, h),
                        (.5F - .08F * k) * Math.min(1.4F, h), t * .4F + k, alpha * (1 - f * .7F), .3F);
            }
            c.glow(body.add(0, h * .5, 0), .6F * h, BLUE, alpha * .5F);
        }
        if (t >= fall) {
            float k = smooth(fall, fall + 4, t), alpha = 1 - k;
            c.ring(ground.add(0, .06, 0), VibeGeometry.UP, .4F + 2.2F * k, .07F, 0, Mth.TWO_PI, ICE, alpha);
            c.glow(ground.add(0, .3, 0), 1.2F * (1 - k), WHITE, alpha);
        }
    }

    /** The palm a blast leaves: the hand the viewer sees, in first person or third. */
    private static Vec3 palm(Canvas c, LivingEntity owner, Vec3 aim, boolean offHand) {
        var mc = Minecraft.getInstance();
        if (owner != mc.player || !mc.options.getCameraType().isFirstPerson()) return VibeGeometry.hand(owner, aim, c.partial, offHand);
        Vec3 look = owner.getViewVector(c.partial);
        Vec3 side = VibeGeometry.side(look);
        HumanoidArm arm = offHand ? owner.getMainArm().getOpposite() : owner.getMainArm();
        var pose = ClientArmPoseState.get(owner.getId());
        Vec3 palm = pose != null && pose.name().startsWith("VIBE_")
                ? FirstPersonArmGeometry.vibePalm(arm, pose, ClientArmPoseState.ageTicks(owner.getId()) + c.partial)
                : new Vec3(arm == HumanoidArm.RIGHT ? .33 : -.33, -.33, -.76);
        return c.camera().add(look.scale(-palm.z)).add(side.scale(palm.x)).add(side.cross(look).scale(palm.y));
    }

    /** A vision's hologram flickering in (a ring rising up a body's height) or out (falling), with a cold flash. */
    private static void project(Canvas c, SetPieceClient.Active a, float t) {
        if (!c.glow()) return;
        boolean leaving = a.scale() < .75F;
        float alpha = fade(t, 16, 1, 6), p = smooth(0, 10, t);
        Vec3 feet = a.origin();
        float h = 1.9F * (leaving ? 1 - p : p);
        c.ring(feet.add(0, h, 0), VibeGeometry.UP, .42F, .035F, 0, Mth.TWO_PI, ICE, alpha);
        c.ring(feet.add(0, h * .5, 0), VibeGeometry.UP, .36F, .02F, 0, Mth.TWO_PI, BLUE, alpha * .6F);
        c.ring(feet.add(0, .03, 0), VibeGeometry.UP, .3F + .55F * p, .03F, 0, Mth.TWO_PI, BLUE, alpha * (1 - p));
        c.glow(feet.add(0, 1, 0), .9F * (1 - .6F * p), BLUE, alpha * .4F);
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
            if (!c.glow()) continue;
            // The front has a body: a lens of pressed air, bright at its rim, and a whole ring round it, so it reads
            // from behind the caster and from the side, not only as two crescents seen face-on.
            float lens = k == 0 ? 1 : .55F;
            c.disc(center, direction, width * .96F, BLUE, alpha * .05F * lens, alpha * .24F * lens);
            c.ring(center, direction, width, .05F * a.scale() + .02F, 0, Mth.TWO_PI, ICE, alpha * .62F * lens);
            if (k == 0) c.glow(center, width * .9F, BLUE, alpha * .38F);
        }
        if (!c.glow()) return;
        // The wake: rings left hanging along the path the lead front has crossed, each fading a few ticks after it
        // passed, so a single frame shows the whole blast travel (the Quake shockwave's tube).
        double length = line.length();
        float lead = Math.min(1, (float) (t / travel));
        int rings = minimal() ? 3 : Math.max(3, (int) (length / 1.3));
        for (int i = 1; i <= rings; i++) {
            float f = i / (float) (rings + 1);
            if (f > lead) break;
            float since = (float) (t - f * travel);
            float ringAlpha = (1 - smooth(0, 7, since)) * smooth(-.5F, 1.5F, since) * .55F;
            if (ringAlpha <= .01F) continue;
            Vec3 at = from.add(line.scale(f));
            ringAlpha *= smooth(.4F, 1.4F, (float) at.distanceTo(c.camera()));
            float radius = (.2F + .62F * Mth.sqrt(f)) * a.scale() * (1 + since * .05F);
            c.ring(at, direction, radius, .035F, 0, Mth.TWO_PI, i % 2 == 0 ? ICE : BLUE, ringAlpha);
        }
        if (minimal()) return;
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
        iris(c, at, normal, width, height, open, alpha, time, guard, stored, false);
    }

    /** True when this breach mouth shows the far side's terrain ({@code BreachViewClient}); its middle then stays clear. */
    private static boolean window(SetPieceClient.Active a) {
        return com.jayptucker.heroesevolved.client.effects.BreachViewClient.showing(a.origin());
    }

    private static void iris(Canvas c, Vec3 at, Vec3 normal, double width, double height, float open, float alpha,
                             float time, boolean guard, int stored, boolean window) {
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
                if (window) {
                    Vec3 inner = at.lerp(edge, .74), innerNext = at.lerp(next, .74);
                    float rim = alpha * near * .58F;
                    c.tri(inner, edge, next, VOID, 0, rim, rim);
                    c.tri(inner, next, innerNext, VOID, 0, rim, 0);
                } else {
                    c.tri(at, edge, next, VOID, alpha * near * (guard ? .20F : .82F), alpha * near * .46F, alpha * near * .46F);
                }
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
                double radius = 1 - (guard ? .52 : window ? .2 : .9) * f;
                trace[i] = at.add(side.scale(Math.cos(angle) * x * radius)).add(up.scale(Math.sin(angle) * y * radius))
                        .add(normal.scale(.13 * Mth.sin(f * Mth.PI)));
            }
            stroke(c, trace, .024F + stored * .0014F, alpha * near * (guard ? .36F : .49F), lane % 2 == 0 ? ICE : GHOST);
        }
        if (stored > 0) c.glow(at, .35F + stored * .047F, BLUE, alpha * near * .65F);
    }

    private static Vec3 lip(Vec3 at, Vec3 side, Vec3 up, double x, double y, float angle, float time) {
        double ripple = VibeGeometry.lipScale(angle, time);
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
            if (c.glow()) stroke(c, edge, .05F, alpha, wing == 0 ? ICE : BLUE);
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

    /**
     * A replayed blow (Read the Vibe). The bodies themselves are ghosts of the real creatures ({@code VibeEchoes}); this
     * draws only what passes between them: from the attacker's piece (scale 1) a violet thread to the victim as the
     * blow lands, and on the victim's piece a split front where it hit.
     */
    private static void echo(Canvas c, SetPieceClient.Active a, float t) {
        Vec3 from = a.origin(), to = a.target();
        boolean attacker = a.scale() >= .8F;
        float alpha = fade(t, 29, 2, 10);
        if (attacker) {
            if (!c.glow()) return;
            float p = smooth(4, 9, t);
            if (p <= 0) return;
            filament(c, from, from.lerp(to, p), .16F, t, alpha * .8F, GHOST);
            if (p >= 1) c.glow(to, .35F * (1 - smooth(9, 18, t)), ICE, alpha * .7F);
            return;
        }
        float hit = smooth(8, 16, t);
        if (t < 8 || hit >= 1) return;
        Vec3 normal = from.subtract(to);
        normal = normal.lengthSqr() < 1e-4 ? VibeGeometry.UP : normal.normalize().reverse();
        front(c, from, normal, .25F + .55F * hit, .3F + .5F * hit, t * .2F, alpha * (1 - hit), .3F);
        fragments(c, a, from, normal, t - 8, 10, .8F);
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

    /**
     * A rift beast still out of phase with this world: two split fronts turning round its body like a collar of the
     * breach it came through, a cold glow at its heart and shards falling off it. Re-sent while it lasts.
     */
    private static void beast(Canvas c, SetPieceClient.Active a, float t) {
        if (!(a.bound(c) instanceof LivingEntity body)) return;
        float alpha = fade(t, 12, 2, 2) * .8F;
        Vec3 at = body.getPosition(c.partial);
        float w = body.getBbWidth(), h = body.getBbHeight();
        float time = clock(a, t);
        for (int band = 0; band < 2; band++) {
            Vec3 center = at.add(0, h * (.35 + .4 * band), 0);
            float spin = time * (band == 0 ? .11F : -.09F);
            front(c, center, VibeGeometry.UP, w * .78F, w * .78F, spin + band * 1.7F, alpha, .12F);
        }
        if (c.glow()) {
            c.glow(at.add(0, h * .55, 0), w * .9F, BLUE, alpha * .3F);
            fragments(c, a, at.add(0, h * .55, 0), VibeGeometry.UP, t, 12, w * .8F);
        }
    }

    /**
     * Breach Kick: a small tear opens at the kicking foot and another behind the foe, and the shin comes out of the far
     * one. {@code origin} is the near tear, {@code target} the far one.
     */
    private static void kick(Canvas c, SetPieceClient.Active a, float t) {
        Vec3 near = a.origin(), far = a.target();
        Vec3 into = far.subtract(near).multiply(1, 0, 1);
        if (into.lengthSqr() < .01) into = new Vec3(0, 0, 1);
        into = into.normalize();
        float open = smooth(0, 3, t) * (1 - smooth(9, 14, t));
        float alpha = fade(t, 14, 1, 4);
        iris(c, near, into, .42, .56, open, alpha, clock(a, t), false, 0);
        // The far tear faces back at the kicker: the kick comes out of it toward them.
        Vec3 out = into.reverse();
        iris(c, far, out, .42, .56, open, alpha, clock(a, t) + 3, false, 0);
        if (!c.glow()) return;
        float reach = smooth(1.5F, 4.5F, t) * (1 - smooth(7, 11, t));
        if (reach > .02F) {
            Vec3 tip = far.add(out.scale(.85 * reach));
            stroke(c, new Vec3[]{far.subtract(out.scale(.1)), far.add(out.scale(.4 * reach)), tip}, .09F, alpha, ICE);
            c.glow(tip, .3F, ICE, alpha * .7F);
        }
        fragments(c, a, far, out, t, 14, .7F);
    }

    /** Seam Sweep: a jagged crack runs along the ground from the palm, glowing, with shards kicked up where it passes. */
    private static void seam(Canvas c, SetPieceClient.Active a, float t) {
        Vec3 from = a.origin().add(0, .06, 0);
        Vec3 dir = a.target().subtract(a.origin()).multiply(1, 0, 1);
        if (dir.lengthSqr() < .01) return;
        dir = dir.normalize();
        Vec3 side = VibeGeometry.side(dir);
        float length = a.scale(), run = smooth(0, 4, t) * length, alpha = fade(t, 18, 1, 8);
        int segments = Math.max(4, (int) (length * 2.5));
        Vec3[] crack = new Vec3[segments + 1];
        for (int i = 0; i <= segments; i++) {
            float f = i / (float) segments;
            double jag = (hash(a.seed(), i) - .5F) * .45 * Mth.sin(f * Mth.PI);
            crack[i] = from.add(dir.scale(Math.min(f * length, run))).add(side.scale(jag));
        }
        if (c.soft()) {
            c.ribbon(crack, .22F, VOID, .5F * alpha, .2F * alpha);
            return;
        }
        if (!c.glow()) return;
        stroke(c, crack, .06F, alpha, BLUE);
        for (int i = 1; i < 6 && !minimal(); i++) {
            float f = i / 6F;
            if (f * length > run) break;
            Vec3 at = from.add(dir.scale(f * length));
            float lift = smooth(f * 4, f * 4 + 6, t);
            front(c, at.add(0, lift * .8, 0), VibeGeometry.UP, .3F + lift * .4F, .3F + lift * .4F, i + t * .2F, alpha * (1 - lift), .1F);
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

    private static Vec3 facing(SetPieceClient.Active a) {
        Vec3 direction = a.target().subtract(a.origin()).normalize();
        return direction.lengthSqr() < .01 ? a.facing() : direction;
    }
    private static float clock(SetPieceClient.Active a, float age) { return (float) (a.start() % 24000) + age; }
    private static boolean minimal() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.options.particles().get() == ParticleStatus.MINIMAL;
    }

    private static float fade(float age, float life, float in, float out) {
        if (age < 0 || age >= life) return 0;
        return (in > 0 ? smooth(0, in, age) : 1) * (out > 0 ? 1 - smooth(life - out, life, age) : 1);
    }

    /**
     * Off Frequency (the finisher, {@code vibe/OffFrequency}): wave fronts run between two blue panes either side of the
     * foe (the split, tinted copies of it are {@code BodyCopies}), and each copy blinks out with a breach's pop. A piece
     * with a scale under .5 is a filament from the foe to a marked foe that takes the overkill. Bound to the foe, Cisco's
     * entity id in the seed; origin its feet, target where Cisco stands; scale its height.
     */
    /**
     * The alternate finishers ({@code vibe/VibeFinishers}). Breach Drop: a breach lying flat under the foe, opening, the
     * body dropping through it (BodyCopies), and the iris snapping shut. Point Blank: wave fronts gathering in the palms
     * against the foe, then a run of fronts straight through it and out of its back.
     */
    static void alternates(Canvas c, SetPieceClient.Active a, float t) {
        if (c.solid()) return;
        float h = Math.max(.6F, a.scale()), life = a.piece().ticks;
        float clock = clock(a, t);
        if (a.piece() == Piece.FINISH_BREACH) {
            int open = com.jayptucker.heroesevolved.combat.FinisherBeats.BREACH_OPEN, land = com.jayptucker.heroesevolved.combat.FinisherBeats.BREACH_LAND;
            float alpha = fade(t, life, 2, 5);
            float shape = smooth(1, open, t) * (1 - smooth(land, land + 3, t));
            Vec3 floor = a.origin().add(0, .05, 0);
            float w = .7F + h * .2F;
            iris(c, floor, new Vec3(0, 1, 0), w, w, shape, alpha, clock, false, 0);
            if (t >= land && t < land + 5 && c.glow()) {
                float k = smooth(land, land + 5, t);
                c.ring(floor, new Vec3(0, 1, 0), w * (.4F + 1.4F * k), .05F, 0, Mth.TWO_PI, ICE, alpha * (1 - k));
                c.glow(floor.add(0, .2, 0), .9F * (1 - k), WHITE, alpha * (1 - k));
            }
            return;
        }
        // Point Blank: bound to you, the target the foe's chest.
        float alpha = fade(t, life, 1, 6);
        int gather = com.jayptucker.heroesevolved.vibe.VibeFinishers.BLANK_GATHER, land = com.jayptucker.heroesevolved.vibe.VibeFinishers.BLANK_LAND;
        Vec3 chest = a.target();
        Vec3 palms = hand(c, a, false).lerp(hand(c, a, true), .5);
        Vec3 dir = chest.subtract(palms);
        if (dir.lengthSqr() < 1e-4) return;
        dir = dir.normalize();
        if (t < land) {
            float build = smooth(gather, land, t);
            for (int k = 0; k < 3; k++) {
                float q = ((t * .18F + k / 3F) % 1);
                Vec3 at = palms.add(dir.scale(.1 + .25 * (1 - q)));
                front(c, at, dir, (.15F + .35F * q) * (.5F + build), (.12F + .28F * q) * (.5F + build), clock * .2F + k, alpha * build * (1 - q), .4F);
            }
            if (c.glow()) c.glow(palms, .2F + .3F * build, BLUE, alpha * .5F * build);
        } else {
            float s = t - land;
            for (int k = 0; k < (minimal() ? 2 : 4); k++) {
                float p = Mth.clamp((s - k * .8F) / 7, 0, 1.2F);
                if (p <= 0 || p > 1.15F) continue;
                Vec3 at = chest.subtract(dir.scale(.4)).add(dir.scale(p * 4.2));
                float width = (.45F + .6F * p) * Math.max(1, h * .6F);
                front(c, at, dir, width, width * .75F, k * 1.07F + t * .12F, alpha * (1 - p * .8F), .6F);
            }
            if (c.glow()) c.glow(chest, 1F * (1 - smooth(land, land + 3, t)), WHITE, alpha * (1 - smooth(land, land + 3, t)));
        }
    }

    static void finisher(Canvas c, SetPieceClient.Active a, float t) {
        float life = a.piece().ticks, f = Canvas.fade(t, life, 1, 6);
        if (a.scale() < .5F) {
            filament(c, a.origin(), a.target(), .2F, t, f * (1 - smooth(4, 10, t)), ICE);
            return;
        }
        float h = Math.max(.6F, a.scale());
        net.minecraft.world.entity.Entity foe = a.bound(c);
        Vec3 feet = foe != null ? foe.getPosition(c.partial) : a.origin();
        Vec3 toward = a.target().subtract(feet).multiply(1, 0, 1);
        Vec3 facing = toward.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : toward.normalize(), side = new Vec3(-facing.z, 0, facing.x);
        int split = com.jayptucker.heroesevolved.combat.FinisherBeats.FREQUENCY_SPLIT, gone = com.jayptucker.heroesevolved.combat.FinisherBeats.FREQUENCY_GONE;
        float phase = (c.level.getGameTime() % 2400 + c.partial) * .4F;
        // The fronts either side of the foe, the gap between them buzzing.
        float hold = smooth(0, 4, t) * (1 - smooth(gone, gone + 4, t));
        if (hold > 0) for (int sign : new int[]{-1, 1}) {
            Vec3 at = feet.add(side.scale(sign * (1.1 + .3 * smooth(split + 6, split + 13, t)))).add(0, h * .55, 0);
            front(c, at, side.scale(-sign), .55F, h * .6F, phase, f * hold, smooth(split, split + 6, t));
        }
        // Each copy blinks out with a pop where it stood.
        if (c.glow()) for (int i = 0; i < 3; i++) {
            float k = smooth(gone + i, gone + i + 3, t);
            if (k <= 0 || k >= 1) continue;
            float drift = .12F + .85F;
            Vec3 at = feet.add(side.scale((i - 1) * drift)).add(0, h * .55, 0);
            float[] tint = i == 0 ? BLUE : i == 1 ? WHITE : ICE;
            c.ring(at, facing, .2F + .9F * k, .05F, 0, net.minecraft.util.Mth.TWO_PI, tint, f * (1 - k));
            c.glow(at, .5F * (1 - k), WHITE, f * (1 - k));
        }
    }
}
