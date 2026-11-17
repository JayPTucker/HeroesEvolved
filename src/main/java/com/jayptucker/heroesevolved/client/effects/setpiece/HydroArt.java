package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.client.ClientHydroState;
import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.flight.FlightVisualGeometry;
import com.jayptucker.heroesevolved.hydro.HydroGeometry;
import com.jayptucker.heroesevolved.hydro.HydroService;
import com.jayptucker.heroesevolved.network.ClientArmPoseState;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/**
 * Living water. Every Hydrokinesis piece is drawn from a handful of shapes: a {@link #tube} of glassy water lit by the
 * sun (sea-blue in its depths, teal where it thins, paler at the silhouette), a {@link #sheet} for waves and walls, a
 * white {@link #foam} lip wherever water curls or breaks, {@link #spray} thrown in arcs that fall, {@link #ripple}s on
 * the ground, and a {@link #splash} that combines them. Water is drawn with normal blending, so it reads as water and
 * not as light; only thin specular glints go in the glow pass.
 *
 * <p>Palette: {@code DEEP 0B3A5C} depths and shadow, {@code SEA 1F78B4} body, {@code TEAL 3CC2D6} thin water,
 * {@code SHALLOW 9EE8F2} highlights and healing glow, {@code FOAM F2FBFF} foam and spray, {@code ICE CDEFFF} ice.
 * Water pulled from the air (too little carried) is drawn thinner and paler.</p>
 */
final class HydroArt {
    private static final float[] DEEP = rgb(0x0B3A5C), SEA = rgb(0x1F78B4), TEAL = rgb(0x3CC2D6);
    private static final float[] SHALLOW = rgb(0x9EE8F2), FOAM = rgb(0xF2FBFF), ICE = rgb(0xCDEFFF), CLOUD = rgb(0x5E6E7E);

    private HydroArt() {}

    static void draw(Canvas c, Active a, float t) {
        if (c.solid()) return;
        boolean thin = a.scale() >= HydroGeometry.THIN;
        float size = a.scale() - (thin ? HydroGeometry.THIN : 0);
        float k = thin ? .62F : 1, pale = thin ? .35F : 0;
        float alpha = fade(t, a.piece().ticks, 1.2F, Math.min(9, a.piece().ticks * .45F)) * (thin ? .8F : 1);
        float phase = (c.level.getGameTime() % 2400 + c.partial) * .35F;
        Vec3 at = a.origin(), end = a.target();
        Player owner = a.bound(c) instanceof Player p ? p : null;
        int seed = a.seed();
        switch (a.piece()) {
            case HYDRO_WHIP -> {
                boolean snare = size >= 3.5F;
                float hit = snare ? size - 4 : size;
                if (owner != null && t < 6) at = hand(c, owner, true);
                // The arm cracks the whip down from above: it unrolls in an arc that straightens as it cracks.
                Vec3 bend = perpendicular(end.subtract(at), UP);
                whip(c, at, end, t, HydroGeometry.WHIP_FLIGHT, .11F * k, alpha * (snare ? 1 - smooth(4, 6, t) : 1),
                        phase, seed, bend, pale);
                if (!snare && t >= HydroGeometry.WHIP_FLIGHT - .5F) {
                    float crack = t - HydroGeometry.WHIP_FLIGHT + .5F;
                    splash(c, end, hit > .8F ? .75F : .5F, crack, alpha, seed, pale, hit > .8F);
                }
            }
            case HYDRO_SNARE -> snare(c, a, at, size, t, alpha * k, phase, seed, pale);
            case HYDRO_WAVE, HYDRO_TSUNAMI -> wave(c, a, at, end, size, t, alpha, phase, seed, pale);
            case HYDRO_TENDRIL -> {
                // An arm of the octopus lashes out to its foe and recoils.
                float out = smooth(0, 3, t), back = smooth(4, 9, t);
                float reach = out * (1 - back);
                Vec3 root = owner != null ? HydroGeometry.chest(owner.getPosition(c.partial)).add(end.subtract(at).normalize().scale(.9)) : at;
                Vec3 bend = perpendicular(end.subtract(root), UP).scale(.6).add(side(end.subtract(root).normalize()).scale(.5));
                arm(c, root, root.lerp(end, reach), .2F, .05F, bend, phase, alpha, seed, pale);
                if (t > 2.5F && t < 7) splash(c, end, .5F, t - 2.5F, alpha, seed, pale, true);
            }
            case HYDRO_BURST -> burst(c, owner != null ? owner.getPosition(c.partial) : at, size, t, alpha, phase, seed, pale);
            case HYDRO_SPLASH -> splash(c, at, size, t, alpha, seed, pale, true);
            case HYDRO_WHIRLPOOL -> whirlpool(c, at, size, t, alpha, phase, seed, pale);
            case HYDRO_DELUGE -> deluge(c, at, size, t, alpha, phase, seed, pale);
            case HYDRO_LASH -> {
                boolean loaded = size >= 3.5F;
                float hit = loaded ? size - 4 : size;
                boolean rear = owner != null && ClientArmPoseState.get(owner.getId()) == ArmPose.WATER_BACKHAND;
                if (owner != null && t < 5) at = hand(c, owner, rear);
                // A flick from the hip: the lead hand's lash bows out to its own side, the backhand to the other.
                Vec3 path = end.subtract(at);
                float handed = owner != null && owner.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
                Vec3 bend = side(path.normalize()).scale((rear ? -1 : 1) * handed).add(0, .35, 0);
                whip(c, at, end, t, HydroGeometry.LASH_FLIGHT, (loaded ? .1F : .075F) * k, alpha, phase, seed, bend, pale);
                if (t >= HydroGeometry.LASH_FLIGHT - .5F) splash(c, end, hit > .8F ? .5F : .35F, t - HydroGeometry.LASH_FLIGHT + .5F,
                        alpha * .85F, seed, pale, hit > .8F);
            }
            case HYDRO_DAGGER -> dagger(c, owner, at, end, size, t, alpha, seed, pale);
            case HYDRO_WHEEL -> wheel(c, owner, at, size, t, alpha * k, phase, seed, pale);
            case HYDRO_FLOW -> flow(c, owner, at, end, t, alpha, phase, seed);
            case HYDRO_VAULT -> geyser(c, at, size * .6F, 3.4F, t, a.piece().ticks, alpha, phase, seed, pale);
            case HYDRO_UNDERTOW -> {
                // A blow passes through water: a ring snaps in round the waist and climbs into the rear hand.
                Vec3 body = owner != null ? owner.getPosition(c.partial) : at;
                float snap = smooth(0, 5, t);
                Vec3 waist = body.add(0, .9, 0);
                ringTube(c, waist, 1.4F - .8F * snap, .09F, phase * 2, TWO_PI, alpha, pale);
                if (owner != null) {
                    Vec3 hand = hand(c, owner, true);
                    Vec3 from = waist.add(cos(phase) * .6, 0, sin(phase) * .6);
                    arm(c, from, from.lerp(hand, smooth(2, 7, t)), .08F, .04F, UP.scale(.3), phase, alpha, seed, pale);
                }
                splash(c, body.add(0, .1, 0), 1.1F, t, alpha * .7F, seed, pale, false);
            }
            case HYDRO_LOADED -> {
                if (owner == null) return;
                float live = fade(t, a.piece().ticks, 1.5F, 1.5F);
                Vec3 hand = hand(c, owner, true);
                orb(c, hand, .16F, phase, live, pale);
                ringTube(c, hand, .26F, .035F, phase * 3, TWO_PI * .8F, live * .9F, pale);
            }
            case HYDRO_GEYSER_MARK -> {
                // Rings close in on the spot as the water gathers under it; bubbles break the surface.
                float build = smooth(0, HydroGeometry.GEYSER_DELAY, t);
                Vec3 floor = at.add(0, .05, 0);
                ripple(c, floor, size, .07F, alpha * (.45F + .45F * build));
                for (int i = 0; i < 3; i++) {
                    float r = size * (1 - frac(build * 1.6F + i / 3F));
                    ripple(c, floor, r, .05F, alpha * .7F);
                }
                bubbles(c, floor, size * .8F, t, alpha * build, seed);
                if (c.soft()) c.disc(floor, UP, size * .55F * build, DEEP, alpha * .35F, 0);
            }
            case HYDRO_GEYSER -> geyser(c, at, size, 7, t, a.piece().ticks, alpha, phase, seed, pale);
            case HYDRO_JET -> jet(c, owner, at, end, size, t, alpha, phase, seed);
            case HYDRO_WALL -> wall(c, at, end, size, fade(t, a.piece().ticks, 2, 2) * (thin ? .75F : 1), phase, seed, pale);
            case HYDRO_HEAL -> heal(c, a, at, end, size, t, alpha, phase, seed);
            case HYDRO_RAIN -> rain(c, owner != null ? owner.getPosition(c.partial) : at, size, fade(t, a.piece().ticks, 2, 2), phase, seed);
            case HYDRO_DOME -> dome(c, at, size, fade(t, a.piece().ticks, 2, 2), phase, seed);
            case HYDRO_SPRING -> spring(c, at, size, fade(t, a.piece().ticks, 2, 2), phase, seed);
            case HYDRO_AWAKEN -> awaken(c, owner != null ? owner.getPosition(c.partial) : at, end, t, alpha, phase, seed);
            default -> { }
        }
    }

    // ------------------------------------------------------------------
    // The kit
    // ------------------------------------------------------------------

    /**
     * A whip: it unrolls from the hand along a bowed arc ({@code bend}), a pulse of water running ahead to the tip;
     * the arc straightens as it cracks at {@code flight}, then the water snaps back into the hand.
     */
    private static void whip(Canvas c, Vec3 from, Vec3 to, float t, float flight, float width, float alpha, float phase,
                             int seed, Vec3 bend, float pale) {
        Vec3 path = to.subtract(from);
        double length = path.length();
        if (length < .05 || alpha < .01F) return;
        Vec3 aim = path.scale(1 / length), across = side(aim);
        float unroll = smooth(0, flight, t), crack = smooth(flight - 1, flight + 1, t), retract = smooth(flight + 2, flight + 7, t);
        float tip = unroll * (1 - retract);
        if (tip < .02F) return;
        int n = minimal() ? 10 : 16;
        Vec3[] spine = new Vec3[n];
        float[] radius = new float[n], alphas = new float[n];
        float gone = alpha * (1 - smooth(flight + 6, flight + 9, t));
        for (int i = 0; i < n; i++) {
            float u = tip * i / (n - 1F);
            double sway = sin(u * TWO_PI * 1.2F - t * 1.3F + phase * .2F) * .14 * (1 - crack * .7F) * u;
            spine[i] = from.add(aim.scale(length * u)).add(bend.scale(sin(PI * u / Math.max(.3F, unroll)) * length * .13 * (1 - crack)))
                    .add(across.scale(sway));
            float pulse = (float) Math.exp(-(u - unroll) * (u - unroll) / .012F) * (1 - crack);
            radius[i] = width * (1 - .55F * u) + width * .6F * pulse;
            alphas[i] = gone * (.75F + .25F * smooth(0, .1F, u));
        }
        tube(c, spine, radius, alphas, pale);
        // The crack: a flash of foam at the tip as it snaps straight.
        if (crack > 0 && crack < 1 && c.glow()) c.glow(spine[n - 1], .32F, SHALLOW, gone * .5F * sin(PI * crack));
    }

    /** A snare: the whip's coil cinches round its catch and a taut line runs back to where the bender threw it. */
    private static void snare(Canvas c, Active a, Vec3 hand, float height, float t, float alpha, float phase, int seed, float pale) {
        Entity caught = a.bound(c);
        Vec3 body = caught != null ? caught.getPosition(c.partial) : a.target();
        float width = caught instanceof LivingEntity l ? l.getBbWidth() : .6F;
        Vec3 waist = body.add(0, height * .5, 0);
        float cinch = smooth(0, 4, t);
        int n = minimal() ? 14 : 24;
        Vec3[] spine = new Vec3[n];
        float[] radius = new float[n], alphas = new float[n];
        float r = (width * .5F + .45F) - .2F * cinch;
        for (int i = 0; i < n; i++) {
            float u = i / (n - 1F), angle = u * TWO_PI * 2.3F + phase * .5F;
            spine[i] = waist.add(cos(angle) * r, (u - .5F) * .5F * height * .45F, sin(angle) * r);
            radius[i] = .07F * (.6F + .4F * sin(PI * u));
            alphas[i] = alpha;
        }
        tube(c, spine, radius, alphas, pale);
        // The line back to the hand: taut while it hauls, slack once it lets go.
        float hold = 1 - smooth(8, 13, t);
        Vec3 knot = spine[n - 1];
        Vec3 sag = new Vec3(0, -.3 * (1 - hold), 0);
        Vec3[] line = new Vec3[8];
        float[] lr = new float[8], la = new float[8];
        for (int i = 0; i < line.length; i++) {
            float u = i / 7F;
            line[i] = knot.lerp(hand, u * hold).add(sag.scale(sin(PI * u)));
            lr[i] = .06F;
            la[i] = alpha * hold;
        }
        tube(c, line, lr, la, pale);
        spray(c, waist, UP, 1.2F, .2F, t, 10, alpha * .7F, seed, 8, pale);
    }

    /**
     * A wave rolling along the ground: a curling sheet that rises from the floor behind, crests, and throws its lip
     * forward in a line of foam; its edges trail behind its middle, it follows the ground, a wet sheen spreads behind
     * it and spray flies off the crest. At the end it collapses forward in a splash.
     */
    private static void wave(Canvas c, Active a, Vec3 start, Vec3 target, float halfWidth, float t, float alpha, float phase,
                             int seed, float pale) {
        Vec3 dir = flat(target.subtract(start));
        double reach = target.subtract(start).horizontalDistance();
        int travel = HydroGeometry.waveTravel(a.piece());
        double distance = HydroGeometry.waveFront(t, reach, travel);
        float rise = HydroGeometry.waveRise(t, travel);
        double floor = HydroGeometry.waveFloor(c.level, start, dir, distance);
        Vec3 base = new Vec3(start.x + dir.x * distance, floor, start.z + dir.z * distance);
        Vec3 right = new Vec3(-dir.z, 0, dir.x);
        float height = halfWidth * 1.05F * rise;
        float L = Math.max(.6F, height * .85F);
        // The wet ground it has rolled over: a sheen that dries from where it started.
        if (c.soft() && distance > .5) {
            int n = 8;
            Vec3[] wake = new Vec3[n];
            float[] w = new float[n], wa = new float[n];
            for (int i = 0; i < n; i++) {
                double d = distance * i / (n - 1F) - L * 1.4;
                double y = HydroGeometry.waveFloor(c.level, start, dir, Math.max(0, d));
                wake[i] = new Vec3(start.x + dir.x * d, y + .04, start.z + dir.z * d);
                w[i] = (float) halfWidth * (.7F + .3F * i / (n - 1F));
                wa[i] = alpha * .28F * smooth(0, .4F, i / (n - 1F)) * (1 - smooth(travel + 4, travel + 12, t));
            }
            c.ribbon(wake, w, SEA, wa);
        }
        if (height < .05F) {
            if (t > travel) breaking(c, base, dir, right, halfWidth, t - travel - HydroGeometry.WAVE_RISE, alpha, phase, seed, pale);
            return;
        }
        // The profile, from the floor behind up the face to the crest and the lip curling over in front. The back is
        // short and fades into the ground, so seen from behind the wave reads as a ridge, not a dome over the bender.
        float[] along = {-1.0F, -.7F, -.4F, -.08F, .3F, .55F, .6F, .48F};
        float[] up = {0, .3F, .66F, .92F, 1.0F, .86F, .62F, .42F};
        float[] tint = {0, .3F, .55F, .72F, .86F, .95F, 1, 1};
        float[] back = {0, .3F, .65F, 1, 1, 1, 1, 1};
        int slices = minimal() ? 7 : detail() ? 15 : 11, rows = along.length;
        Vec3[][] grid = new Vec3[slices][rows];
        float[][] tints = new float[slices][rows], alphas = new float[slices][rows];
        Vec3[] lip = new Vec3[slices];
        float[] lipW = new float[slices], lipA = new float[slices];
        for (int i = 0; i < slices; i++) {
            float s = i / (slices - 1F) * 2 - 1;
            float edge = (float) Math.pow(Math.max(0, 1 - Math.pow(Math.abs(s), 2.4)), .55);
            // The edges trail the middle, but never so far that a big wave wraps round the bender's sides.
            float lag = -.75F * Math.min(L, 1.6F) * s * s;
            float wobble = 1 + .07F * sin(phase + s * 5.3F + seed);
            for (int j = 0; j < rows; j++) {
                float curl = j >= 5 ? (1 - edge) * .5F : 0;
                double x = (along[j] - curl) * L + lag + .1 * sin(phase * 1.3F + s * 7 + j);
                double y = up[j] * height * edge * wobble;
                grid[i][j] = base.add(dir.scale(x)).add(right.scale(s * halfWidth)).add(0, y + .02, 0);
                tints[i][j] = tint[j] * (.6F + .4F * edge);
                alphas[i][j] = alpha * (.58F + .2F * tint[j]) * smooth(0, .25F, edge + .05F) * back[j];
            }
            lip[i] = grid[i][5].add(0, .05, 0);
            lipW[i] = .16F * edge + .04F;
            lipA[i] = alpha * edge;
        }
        sheet(c, grid, tints, alphas, pale);
        foam(c, lip, lipW, lipA);
        // Foam where the lip meets the ground in front, and spray torn off the crest.
        Vec3[] skirt = new Vec3[slices];
        float[] skirtW = new float[slices], skirtA = new float[slices];
        for (int i = 0; i < slices; i++) {
            skirt[i] = grid[i][0].add(dir.scale(L * 1.4)).add(0, .03, 0);
            skirtW[i] = .22F;
            skirtA[i] = lipA[i] * .55F * rise;
        }
        foam(c, skirt, skirtW, skirtA);
        int crests = minimal() ? 2 : 5;
        for (int i = 0; i < crests; i++) {
            float s = (hash(seed, i) * 2 - 1) * .8F;
            Vec3 from = base.add(right.scale(s * halfWidth)).add(dir.scale(.3 * L)).add(0, height * .95, 0);
            spray(c, from, dir.add(0, 1.1, 0).normalize(), .5F, .22F, (t * 1.7F + i * 3.1F) % 8, 8, alpha * .8F, seed + i * 13, 4, pale);
        }
        if (c.soft() && detail()) {
            Vec3 puff = base.add(dir.scale(L * .5)).add(0, height * .9, 0);
            c.cloud(puff, .5F + halfWidth * .2F, .3F, .5F + halfWidth * .2F, seed, phase * .05F, alpha * .14F, Shades.smoke(c, FOAM, 0));
        }
    }

    /** A wave breaking at the end of its run: the water collapses forward into foam and spray. */
    private static void breaking(Canvas c, Vec3 base, Vec3 dir, Vec3 right, float halfWidth, float t, float alpha, float phase,
                                 int seed, float pale) {
        float k = alpha * (1 - smooth(0, 9, t));
        if (k < .01F) return;
        int n = minimal() ? 3 : 6;
        for (int i = 0; i < n; i++) {
            float s = i / (n - 1F) * 2 - 1;
            splash(c, base.add(right.scale(s * halfWidth * .8)).add(dir.scale(.4)), .6F + halfWidth * .12F, t + 2, k, seed + i * 7, pale, true);
        }
    }

    /** Every octopus arm flung straight out at once, the shell bursting into spray and a ring running out over the ground. */
    private static void burst(Canvas c, Vec3 feet, float radius, float t, float alpha, float phase, int seed, float pale) {
        Vec3 center = HydroGeometry.chest(feet).add(0, -.2, 0);
        float out = smooth(0, 4, t), fall = 1 - smooth(5, 14, t);
        for (int i = 0; i < HydroGeometry.ARMS; i++) {
            float angle = i * TWO_PI / HydroGeometry.ARMS + hash(seed, i) * .3F;
            float lift = (i % 2 == 0 ? .25F : -.15F);
            Vec3 dir = new Vec3(cos(angle), lift, sin(angle)).normalize();
            Vec3 root = center.add(dir.scale(.8));
            arm(c, root, root.add(dir.scale((radius - .8) * out)), .22F * fall + .02F, .06F, UP.scale(.2), phase, alpha * fall, seed + i, pale);
            if (t > 3) spray(c, root.add(dir.scale((radius - .8) * out)), dir.add(0, .4, 0).normalize(), .6F, .18F, t - 3, 10,
                    alpha, seed + i * 5, 4, pale);
        }
        if (c.soft()) c.shell(center, 1.2F + 1.4F * out, 1.2F + 1.2F * out, 1.2F + 1.4F * out, PI, mix(TEAL, FOAM, pale),
                alpha * .35F * (1 - out), .3F, wobble(phase), phase * .1F);
        ripple(c, feet.add(0, .05, 0), radius * smooth(1, 10, t), .1F, alpha * (1 - smooth(8, 16, t)));
        mist(c, center, 1.5F + radius * .3F * out, t, alpha * .5F, seed);
    }

    /** Maelstrom's whirlpool opening: foam spirals wind in faster and faster, rings close on the eye, the middle darkens. */
    private static void whirlpool(Canvas c, Vec3 at, float ring, float t, float alpha, float phase, int seed, float pale) {
        float build = smooth(0, HydroGeometry.SPOUT_WINDUP, t);
        Vec3 floor = at.add(0, .06, 0);
        float spin = t * (.05F + .25F * build) + phase * .1F;
        int arms = minimal() ? 3 : 5;
        for (int s = 0; s < arms; s++) {
            int n = minimal() ? 14 : 28;
            Vec3[] spiral = new Vec3[n];
            float[] w = new float[n], sa = new float[n];
            for (int i = 0; i < n; i++) {
                float u = i / (n - 1F), r = ring * (1.1F - u * .95F);
                float angle = s * TWO_PI / arms + spin + u * 4.2F;
                spiral[i] = floor.add(cos(angle) * r, .01 * i, sin(angle) * r);
                w[i] = .12F + .1F * (1 - u);
                sa[i] = alpha * (.4F + .5F * build) * smooth(0, .15F, u) * (1 - smooth(.85F, 1, u));
            }
            foam(c, spiral, w, sa);
        }
        ripple(c, floor, ring, .08F, alpha * (.5F + .4F * build));
        ripple(c, floor, ring * (1 - .85F * frac(build * 2.5F)), .06F, alpha * .6F);
        if (c.soft()) {
            c.disc(floor, UP, ring * (.2F + .5F * build), DEEP, alpha * .55F * build, 0);
            c.disc(floor.add(0, .01, 0), UP, ring, SEA, alpha * .18F, alpha * .05F);
        }
        bubbles(c, floor, ring * .7F, t, alpha * build, seed);
        if (t > HydroGeometry.SPOUT_WINDUP - 6) spray(c, floor, UP, .7F, .35F, t - HydroGeometry.SPOUT_WINDUP + 6, 10, alpha, seed, 10, pale);
    }

    /** The waterspout coming down: a column of water falls on the spot and runs outward in a ring that breaks. */
    private static void deluge(Canvas c, Vec3 at, float radius, float t, float alpha, float phase, int seed, float pale) {
        float fall = smooth(0, 4, t);
        Vec3 floor = at.add(0, .05, 0);
        if (t < 8) {
            float top = 12 * (1 - fall) + .5F, collapse = 1 - smooth(3, 8, t);
            int n = 8;
            Vec3[] spine = new Vec3[n];
            float[] r = new float[n], a = new float[n];
            for (int i = 0; i < n; i++) {
                float u = i / (n - 1F);
                spine[i] = floor.add(sin(phase + u * 3) * .3, top + (14 - top) * u, cos(phase + u * 3) * .3);
                r[i] = radius * .32F * collapse * (1 - .4F * u);
                a[i] = alpha * collapse;
            }
            tube(c, spine, r, a, pale);
        }
        float spread = smooth(3, 16, t);
        float height = 1.6F * (1 - smooth(6, 20, t));
        if (c.soft() && height > .05F) {
            c.tube(floor, radius * spread, height, mix(TEAL, FOAM, pale), alpha * .55F, alpha * .1F, wobble(phase), phase * .1F);
            c.tube(floor, radius * spread * .92F, height * .7F, SEA, alpha * .35F, 0, wobble(phase + 2), phase * .1F);
        }
        Vec3[] crest = circle(floor.add(0, height * .9, 0), radius * spread, minimal() ? 16 : 28, phase);
        float[] cw = new float[crest.length], ca = new float[crest.length];
        for (int i = 0; i < crest.length; i++) { cw[i] = .18F; ca[i] = alpha * (1 - smooth(12, 22, t)); }
        foam(c, crest, cw, ca);
        ripple(c, floor, radius * smooth(2, 22, t), .1F, alpha * (1 - smooth(16, 26, t)));
        if (t > 3) splash(c, floor, radius * .45F, t - 3, alpha, seed, pale, true);
        mist(c, floor.add(0, 1, 0), radius * .5F * spread + 1, t, alpha * .6F, seed);
    }

    /** One ice dagger: a blade of ice flies from the hands to its end and shatters there into glints. */
    private static void dagger(Canvas c, Player owner, Vec3 from, Vec3 to, float size, float t, float alpha, int seed, float pale) {
        if (owner != null && t < 2) from = hand(c, owner, true).lerp(hand(c, owner, false), .5);
        Vec3 path = to.subtract(from);
        if (path.lengthSqr() < .01) return;
        Vec3 aim = path.normalize();
        float flight = HydroGeometry.DAGGER_FLIGHT, travel = smooth(0, flight, t);
        Vec3 tip = from.lerp(to, travel);
        if (t < flight + .5F) {
            float fly = alpha * (1 - smooth(flight, flight + .5F, t));
            Vec3 tail = tip.subtract(aim.scale(.55));
            if (c.soft()) {
                c.shard(tail, tip, .075F, 4, seed * .7F + t * .8F, mix(ICE, FOAM, pale), fly * .85F);
                c.line(tail.subtract(aim.scale(1.1)), tail, .05F, SHALLOW, 0, fly * .3F);
            }
            if (c.glow()) {
                c.line(tail, tip, .018F, FOAM, fly * .4F, fly * .9F);
                c.glow(tip, .14F, SHALLOW, fly * .5F);
            }
            return;
        }
        // It breaks where it lands: a few shards tumble away and a puff of frost hangs.
        float after = t - flight, k = alpha * (1 - smooth(1, 6, after));
        if (c.soft()) for (int i = 0; i < 4; i++) {
            float angle = hash(seed, i) * TWO_PI;
            Vec3 out = side(aim).scale(cos(angle)).add(aim.cross(side(aim)).scale(sin(angle))).scale(.5).subtract(aim.scale(.3));
            Vec3 p = to.add(out.scale(after * .25)).add(0, -.02 * after * after, 0);
            c.shard(p, p.add(out.normalize().scale(.16)), .035F, 3, after * (1 + i), ICE, k);
        }
        if (c.glow()) c.glow(to, .35F * (1 - smooth(0, 4, after)), FOAM, k * .6F);
        mist(c, to, .3F, after, k * .5F, seed);
    }

    /** Water Wheel: a ring of water whirled round the waist in one turn, its leading edge foaming, then flung outward. */
    private static void wheel(Canvas c, Player owner, Vec3 feet, float radius, float t, float alpha, float phase, int seed, float pale) {
        Vec3 waist = (owner != null ? owner.getPosition(c.partial) : feet).add(0, owner != null && owner.isCrouching() ? .55 : .8, 0);
        float spin = smooth(0, HydroGeometry.WHEEL_SPIN, t), out = smooth(HydroGeometry.WHEEL_SPIN - 2, 14, t);
        float r = .8F + (radius - .8F) * (1 - (1 - spin) * (1 - spin)) * (1 + .15F * out);
        float start = hash(seed, 1) * TWO_PI;
        int n = minimal() ? 14 : 24;
        Vec3[] spine = new Vec3[n];
        float[] rad = new float[n], a = new float[n];
        float sweep = TWO_PI * Math.max(.05F, spin);
        float handed = owner != null && owner.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
        for (int i = 0; i < n; i++) {
            float u = i / (n - 1F), angle = start + handed * sweep * u;
            spine[i] = waist.add(cos(angle) * r, .12 * sin(u * TWO_PI * 2 + phase), sin(angle) * r);
            rad[i] = (.07F + .11F * u) * (1 - out * .7F);
            a[i] = alpha * (.4F + .6F * u) * (1 - out);
        }
        tube(c, spine, rad, a, pale);
        Vec3 head = spine[n - 1];
        spray(c, head, UP.add(head.subtract(waist).normalize()).normalize(), .5F, .2F, t, 9, alpha, seed, 6, pale);
        if (out > 0) for (int i = 0; i < 6; i++) {
            float angle = start + i * TWO_PI / 6;
            Vec3 rim = waist.add(cos(angle) * r, 0, sin(angle) * r);
            spray(c, rim, new Vec3(cos(angle), .6, sin(angle)).normalize(), .4F, .22F, t - HydroGeometry.WHEEL_SPIN + 2, 9, alpha, seed + i, 3, pale);
        }
        ripple(c, feet.add(0, .05, 0), r * (.6F + .5F * out), .07F, alpha * .7F * (1 - smooth(9, 14, t)));
    }

    /**
     * Flow Step: a sheet of water spreads under the feet along the skate and lingers wet, a fan of spray is thrown off
     * the leading edge, and the trailing palm draws a curl of water.
     */
    private static void flow(Canvas c, Player owner, Vec3 at, Vec3 end, float t, float alpha, float phase, int seed) {
        Vec3 path = end.subtract(at);
        if (path.lengthSqr() < .01) return;
        Vec3 dir = path.normalize(), right = side(dir);
        Vec3 feet = owner != null ? owner.getPosition(c.partial) : at.lerp(end, smooth(0, 5, t));
        float reached = (float) Math.clamp(feet.subtract(at).dot(dir) / path.length(), 0, 1);
        if (c.soft()) {
            int n = 8;
            Vec3[] sheet = new Vec3[n];
            float[] w = new float[n], a = new float[n];
            for (int i = 0; i < n; i++) {
                float u = reached * i / (n - 1F);
                sheet[i] = at.lerp(end, u).add(0, .04, 0);
                w[i] = .55F + .2F * sin(phase + i);
                a[i] = alpha * .45F * smooth(0, .2F, u + .05F);
            }
            c.ribbon(sheet, w, TEAL, a);
            c.ribbon(sheet, w, SEA, a);
        }
        float pushing = alpha * (1 - smooth(3, 8, t));
        Vec3 edge = feet.add(dir.scale(.2)).add(0, .1, 0);
        spray(c, edge, dir.scale(-.4).add(right.scale(.6)).add(0, .6, 0).normalize(), .5F, .2F, t, 8, pushing, seed, 5, 0);
        spray(c, edge, dir.scale(-.4).add(right.scale(-.6)).add(0, .6, 0).normalize(), .5F, .2F, t, 8, pushing, seed + 3, 5, 0);
        if (owner != null && t < 8) {
            Vec3 palm = feet.add(0, 1.0, 0).add(dir.scale(-.75));
            arm(c, palm, palm.add(dir.scale(-.8)).add(0, -.5, 0), .06F, .02F, UP.scale(.3), phase, pushing, seed, 0);
        }
        ripple(c, at.add(0, .05, 0), .6F + 1.2F * smooth(0, 10, t), .06F, alpha * (1 - smooth(6, 14, t)));
    }

    /**
     * A geyser: the column of water shoots up from the ground to its height, a crown of foam bursts at the top and falls
     * as spray, and the column collapses from the top down. Used for a Geyser and for a Geyser Vault.
     */
    private static void geyser(Canvas c, Vec3 at, float radius, float top, float t, int life, float alpha, float phase,
                               int seed, float pale) {
        Vec3 floor = at.add(0, .03, 0);
        float shoot = smooth(0, 3, t), collapse = smooth(life * .45F, life * .85F, t);
        float height = top * shoot * (1 - collapse * .9F);
        int n = 9;
        Vec3[] spine = new Vec3[n];
        float[] r = new float[n], a = new float[n];
        for (int i = 0; i < n; i++) {
            float u = i / (n - 1F);
            spine[i] = floor.add(sin(phase * 1.4F + u * 4) * .08 * radius, height * u, cos(phase * 1.2F + u * 4) * .08 * radius);
            r[i] = radius * (.55F - .22F * u) * (1 + .1F * sin(phase * 2 + u * 9));
            a[i] = alpha * (1 - collapse * u);
        }
        if (height > .1F) tube(c, spine, r, a, pale);
        Vec3 crown = floor.add(0, height, 0);
        if (t > 1.5F) splash(c, crown, radius * .9F, t - 1.5F, alpha * (1 - collapse), seed, pale, true);
        ripple(c, floor, radius * (1 + smooth(0, life, t)), .08F, alpha * .8F);
        bubbles(c, floor, radius, t, alpha * (1 - shoot), seed);
        mist(c, floor.add(0, .5, 0), radius + .5F, t, alpha * .4F, seed);
    }

    /** Pressure Jet: a hard, straight line of water with a glassy sheath and a white core, fanning out where it hits. */
    private static void jet(Canvas c, Player owner, Vec3 at, Vec3 end, float size, float t, float alpha, float phase, int seed) {
        if (owner != null) at = hand(c, owner, true).lerp(hand(c, owner, false), .5);
        Vec3 path = end.subtract(at);
        if (path.lengthSqr() < .05) return;
        // Two overlapping copies are always drawing; each is half strength.
        float k = alpha * .55F;
        int n = 10;
        Vec3[] spine = new Vec3[n];
        float[] r = new float[n], a = new float[n];
        for (int i = 0; i < n; i++) {
            float u = i / (n - 1F);
            spine[i] = at.lerp(end, u).add(0, sin(phase * 3 + u * 11) * .015, 0);
            r[i] = .07F + .04F * u;
            a[i] = k * clearance(c, spine[i], 1.2F);
        }
        tube(c, spine, r, a, 0);
        if (c.glow()) c.ribbon(spine, .02F, FOAM, k * .9F, k * .6F);
        Vec3 aim = path.normalize();
        spray(c, end, aim.scale(-1).add(0, .5, 0).normalize(), .9F, .2F, (t * 2 + phase) % 6, 6, k * 1.6F, seed, 6, 0);
        if (c.glow()) c.glow(end, .45F * size, SHALLOW, k * .5F);
    }

    /** A Tide Wall: a bowed sheet of standing water, foam along its top, streaks running down its face. */
    private static void wall(Canvas c, Vec3 foot, Vec3 facing, float halfWidth, float alpha, float phase, int seed, float pale) {
        Vec3 normal = flat(facing.subtract(foot)), right = new Vec3(-normal.z, 0, normal.x);
        int cols = minimal() ? 7 : 13, rows = 6;
        float rise = smooth(0, 1, alpha);
        Vec3[][] grid = new Vec3[cols][rows];
        float[][] tint = new float[cols][rows], a = new float[cols][rows];
        Vec3[] top = new Vec3[cols];
        float[] tw = new float[cols], ta = new float[cols];
        for (int i = 0; i < cols; i++) {
            float s = i / (cols - 1F) * 2 - 1;
            float edge = (float) Math.pow(1 - s * s, .35);
            for (int j = 0; j < rows; j++) {
                float v = j / (rows - 1F);
                double h = 3.1 * v * edge * rise + .05 * sin(phase * 1.5F + s * 6 + v * 4);
                double bow = .45 * (1 - s * s) + .12 * sin(phase + v * 5 + s * 3);
                grid[i][j] = foot.add(right.scale(s * halfWidth)).add(normal.scale(bow)).add(0, h + .02, 0);
                tint[i][j] = .45F + .45F * v;
                a[i][j] = alpha * .5F * (.4F + .6F * edge);
            }
            top[i] = grid[i][rows - 1].add(0, .04, 0);
            tw[i] = .12F * edge + .03F;
            ta[i] = alpha * edge;
        }
        sheet(c, grid, tint, a, pale);
        foam(c, top, tw, ta);
        // Water running down the face, and a churn of foam where it meets the ground.
        if (c.soft() && !minimal()) for (int i = 0; i < 6; i++) {
            float s = hash(seed, i) * 1.6F - .8F;
            float v = 1 - frac(phase * .12F + hash(seed, i + 9));
            Vec3 p = foot.add(right.scale(s * halfWidth)).add(normal.scale(.5 * (1 - s * s))).add(0, 3 * v * rise, 0);
            c.line(p, p.add(0, -.6, 0), .05F, FOAM, alpha * .5F, 0);
        }
        Vec3[] footFoam = new Vec3[cols];
        for (int i = 0; i < cols; i++) footFoam[i] = grid[i][0].add(normal.scale(.3)).add(0, .03, 0);
        foam(c, footFoam, tw, ta);
    }

    /**
     * Healing Water: two glowing strands of water spiral up the body, a soft light gathers in them, and on the first
     * copy a stream arcs from the healer's hand to the one being healed.
     */
    private static void heal(Canvas c, Active a, Vec3 from, Vec3 feetAtCast, float size, float t, float alpha, float phase, int seed) {
        Entity target = a.bound(c);
        Vec3 feet = target != null ? target.getPosition(c.partial) : feetAtCast;
        float height = target != null ? target.getBbHeight() : 1.8F, width = target instanceof LivingEntity l ? l.getBbWidth() : .6F;
        if (from.distanceToSqr(feetAtCast) > .2 && t < 8) {
            Vec3 mid = from.lerp(feet.add(0, height * .6, 0), .5).add(0, .6, 0);
            float reach = smooth(0, 4, t), gone = 1 - smooth(5, 8, t);
            int n = 10;
            Vec3[] arc = new Vec3[n];
            float[] r = new float[n], aa = new float[n];
            for (int i = 0; i < n; i++) {
                float u = reach * i / (n - 1F);
                Vec3 p0 = from.lerp(mid, u), p1 = mid.lerp(feet.add(0, height * .6, 0), u);
                arc[i] = p0.lerp(p1, u);
                r[i] = .06F;
                aa[i] = alpha * gone;
            }
            tube(c, arc, r, aa, .2F);
        }
        float rise = smooth(0, 10, t);
        for (int s = 0; s < 2; s++) {
            int n = 16;
            Vec3[] spine = new Vec3[n];
            float[] r = new float[n], aa = new float[n];
            for (int i = 0; i < n; i++) {
                float u = i / (n - 1F) * rise, angle = s * PI + u * TWO_PI * 1.6F + phase * .6F;
                float rad = width * .5F + .28F;
                spine[i] = feet.add(cos(angle) * rad, u * height * 1.05, sin(angle) * rad);
                r[i] = .05F * (.5F + .5F * sin(PI * u / Math.max(.1F, rise)));
                aa[i] = alpha * clearance(c, spine[i], .9F);
            }
            tube(c, spine, r, aa, .3F);
            if (c.glow()) for (int i = 2; i < n; i += 4) c.glow(spine[i], .3F * size, SHALLOW, aa[i] * .45F);
        }
        if (c.glow()) c.glow(feet.add(0, height * .55, 0), .9F + .3F * sin(phase), SHALLOW, alpha * .22F * clearance(c, feet.add(0, height * .55, 0), 1.5F));
    }

    /** A Rain Call cloud: dark billows overhead, rain streaks falling through the field, rings where the drops land. */
    private static void rain(Canvas c, Vec3 center, float radius, float alpha, float phase, int seed) {
        if (alpha < .01F) return;
        double sky = center.y + 7;
        if (c.soft()) {
            int puffs = minimal() ? 6 : 12;
            for (int i = 0; i < puffs; i++) {
                float angle = i * TWO_PI / puffs + hash(seed, i) * .5F + phase * .003F;
                float r = radius * (.2F + .7F * hash(seed, i + 20));
                Vec3 p = new Vec3(center.x + cos(angle) * r, sky + hash(seed, i + 40) * .8, center.z + sin(angle) * r);
                float s = 1.6F + 1.2F * hash(seed, i + 60);
                c.cloud(p, s * 1.4F, s * .5F, s * 1.4F, seed + i, angle, alpha * .45F, Shades.smoke(c, CLOUD, 0));
            }
            int drops = minimal() ? 20 : detail() ? 70 : 40;
            float clock = (c.level.getGameTime() % 4800 + c.partial);
            for (int i = 0; i < drops; i++) {
                float angle = hash(seed, i + 100) * TWO_PI, r = radius * (float) Math.sqrt(hash(seed, i + 200));
                float fall = frac(clock * .09F + hash(seed, i + 300));
                double y = sky - fall * 7.5;
                Vec3 p = new Vec3(center.x + cos(angle) * r, y, center.z + sin(angle) * r);
                c.line(p, p.add(0, .55, 0), .025F, SHALLOW, alpha * .6F, 0);
            }
        }
        int rings = minimal() ? 4 : 10;
        float clock = (c.level.getGameTime() % 4800 + c.partial);
        for (int i = 0; i < rings; i++) {
            float k = frac(clock * .06F + hash(seed, i + 400));
            float angle = hash(seed, i + 500 + (int) (clock * .06F + hash(seed, i + 400))) * TWO_PI;
            float r = radius * (float) Math.sqrt(hash(seed, i + 600 + (int) (clock * .06F)));
            ripple(c, new Vec3(center.x + cos(angle) * r, center.y + .04, center.z + sin(angle) * r), .1F + .5F * k, .03F, alpha * (1 - k) * .7F);
        }
    }

    /** A Tidal Dome: a dome of clear water over the field, ripples running round it, foam at its foot. */
    private static void dome(Canvas c, Vec3 center, float radius, float alpha, float phase, int seed) {
        if (alpha < .01F) return;
        if (c.soft()) {
            c.shell(center, radius, radius * .85F, radius, HALF_PI, TEAL, alpha * .32F, .25F, wobble(phase), phase * .05F);
            c.shell(center, radius * 1.02F, radius * .87F, radius * 1.02F, HALF_PI, SEA, alpha * .14F, .1F,
                    (theta, phi) -> .5F + .5F * sin(theta * 6 - phase * 1.4F + phi * 3), -phase * .05F);
        }
        Vec3[] footRing = circle(center.add(0, .05, 0), radius, minimal() ? 18 : 32, phase * .1F);
        float[] w = new float[footRing.length], a = new float[footRing.length];
        for (int i = 0; i < footRing.length; i++) { w[i] = .16F; a[i] = alpha * .8F; }
        foam(c, footRing, w, a);
        if (c.glow()) for (int i = 0; i < 3; i++) {
            float angle = phase * .2F + i * TWO_PI / 3;
            c.glow(center.add(cos(angle) * radius * .6, radius * .7, sin(angle) * radius * .6), .4F, SHALLOW, alpha * .25F);
        }
    }

    /** A Healing Spring: a clear pool on the ground with a little fountain, rings spreading and light lifting off it. */
    private static void spring(Canvas c, Vec3 center, float radius, float alpha, float phase, int seed) {
        if (alpha < .01F) return;
        Vec3 floor = center.add(0, .05, 0);
        if (c.soft()) {
            c.disc(floor, UP, radius, TEAL, alpha * .45F, alpha * .15F);
            c.disc(floor.add(0, .005, 0), UP, radius * .5F, SEA, alpha * .3F, 0);
        }
        for (int i = 0; i < 3; i++) ripple(c, floor, radius * frac(phase * .05F + i / 3F), .05F, alpha * (1 - frac(phase * .05F + i / 3F)));
        int n = 6;
        Vec3[] spine = new Vec3[n];
        float[] r = new float[n], a = new float[n];
        for (int i = 0; i < n; i++) {
            float u = i / (n - 1F);
            spine[i] = floor.add(sin(phase + u * 3) * .05, u * 1.1, cos(phase + u * 3) * .05);
            r[i] = .09F * (1 - .5F * u);
            a[i] = alpha;
        }
        tube(c, spine, r, a, .1F);
        spray(c, floor.add(0, 1.1, 0), UP, .7F, .12F, (phase * 1.5F) % 10, 10, alpha, seed, 6, .2F);
        if (c.glow()) for (int i = 0; i < 6; i++) {
            float k = frac(phase * .04F + hash(seed, i));
            float angle = hash(seed, i + 10) * TWO_PI, r2 = radius * .8F * hash(seed, i + 20);
            c.glow(floor.add(cos(angle) * r2, k * 2.2, sin(angle) * r2), .12F, SHALLOW, alpha * (1 - k) * .7F);
        }
    }

    /** The awakening: water spirals up round the bender, a bubble closes over the ward, then the water is flung out. */
    private static void awaken(Canvas c, Vec3 feet, Vec3 ward, float t, float alpha, float phase, int seed) {
        float rise = smooth(0, 12, t), fling = smooth(12, 20, t);
        for (int s = 0; s < 3; s++) {
            int n = 18;
            Vec3[] spine = new Vec3[n];
            float[] r = new float[n], a = new float[n];
            for (int i = 0; i < n; i++) {
                float u = i / (n - 1F) * rise, angle = s * TWO_PI / 3 + u * TWO_PI * 1.4F + phase * .8F;
                float rad = 1.1F + 5 * fling;
                spine[i] = feet.add(cos(angle) * rad, u * 3.2 * (1 - fling * .7F), sin(angle) * rad);
                r[i] = .13F * (1 - fling * .6F);
                a[i] = alpha * (1 - fling * .8F) * clearance(c, spine[i], 1.1F);
            }
            tube(c, spine, r, a, 0);
        }
        if (c.soft() && ward.distanceToSqr(feet) > 1) c.shell(ward.add(0, .9, 0), 1.2F, 1.2F, 1.2F, PI, TEAL, alpha * .3F, .25F, wobble(phase), phase * .1F);
        if (t > 12) splash(c, feet, 2.5F, t - 12, alpha, seed, 0, true);
        ripple(c, feet.add(0, .05, 0), 1 + 8 * fling, .12F, alpha * (1 - fling));
    }

    // ------------------------------------------------------------------
    // Live: what lasts as long as a state does
    // ------------------------------------------------------------------

    static boolean live() { return ClientHydroState.any(); }

    static void drawLive(Canvas c) {
        if (c.solid()) return;
        long now = c.level.getGameTime();
        float clock = (now % 2400 + c.partial) * .35F;
        Minecraft mc = Minecraft.getInstance();
        for (ClientHydroState.Bender bender : ClientHydroState.benders()) {
            if (!(c.level.getEntity(bender.entity) instanceof Player player) || !player.isAlive()
                    || player.position().distanceToSqr(c.camera()) > 96 * 96) continue;
            boolean self = player == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON;
            float phase = clock + bender.entity * 1.7F;
            Vec3 feet = player.getPosition(c.partial);
            if (bender.has(HydroService.OCTOPUS)) octopus(c, player, feet, bender, now, phase, self);
            else carried(c, player, feet, bender.fullness(), phase);
            if (bender.has(HydroService.RIDING) && !bender.has(HydroService.LEAPING)) surf(c, player, feet, phase, bender.entity);
            if (bender.has(HydroService.SPOUT)) spout(c, feet, phase, bender.entity);
        }
    }

    /**
     * The water a hydrokinetic carries: a stream flowing round their waist, longer and thicker the fuller they are, a
     * second strand winding at a tilt once they carry more than half, drops shaken off its ends.
     */
    private static void carried(Canvas c, Player player, Vec3 feet, float fullness, float phase) {
        if (fullness < .03F) return;
        Vec3 waist = feet.add(0, player.isCrouching() ? .65 : .95, 0);
        int strands = fullness > .55F ? 2 : 1;
        for (int s = 0; s < strands; s++) {
            float amount = s == 0 ? Math.min(1, fullness) : Math.min(1, (fullness - .55F) / .6F);
            float span = TWO_PI * (.25F + .62F * amount);
            int n = minimal() ? 10 : 18;
            Vec3[] spine = new Vec3[n];
            float[] r = new float[n], a = new float[n];
            float tilt = s == 0 ? .12F : -.35F, spin = phase * (s == 0 ? .16F : -.21F);
            float radius = s == 0 ? .62F : .74F;
            for (int i = 0; i < n; i++) {
                float u = i / (n - 1F), angle = spin + span * u;
                double y = sin(angle) * tilt * radius + .08 * sin(angle * 2 + phase * .3F);
                spine[i] = waist.add(cos(angle) * radius, y + s * .25, sin(angle) * radius);
                r[i] = (.035F + .05F * amount) * sin(PI * (.08F + .84F * u));
                a[i] = .85F * clearance(c, spine[i], 1.05F);
            }
            tube(c, spine, r, a, 0);
            if (!minimal() && s == 0) spray(c, spine[0], new Vec3(cos(spin) * .3, .2, sin(spin) * .3).normalize(), .4F, .05F,
                    (phase * 1.3F) % 7, 7, .6F * clearance(c, spine[0], 1.05F), player.getId(), 2, 0);
        }
    }

    /**
     * Octopus Form: a clear shell of water round the body and eight arms reaching out of it, each curling and swaying
     * on its own beat, tips flicking. In the bender's own first-person view the shell is left out (they are inside it)
     * and only the arms reaching round them are drawn.
     */
    private static void octopus(Canvas c, Player player, Vec3 feet, ClientHydroState.Bender bender, long now, float phase, boolean self) {
        float grow = smooth(0, 6, bender.age(HydroService.OCTOPUS, now, c.partial));
        Vec3 center = feet.add(0, 1.0, 0);
        if (c.soft() && !self) c.shell(center, 1.25F * grow, 1.3F * grow, 1.25F * grow, PI, TEAL, .26F, .3F, wobble(phase), phase * .04F);
        if (c.glow() && !self) c.glow(center.add(-.4, .6, -.3), .35F, SHALLOW, .25F * grow);
        float yaw = (float) Math.toRadians(player.yBodyRot);
        for (int i = 0; i < HydroGeometry.ARMS; i++) {
            float angle = yaw + i * TWO_PI / HydroGeometry.ARMS + .2F * sin(phase * .07F + i);
            float lift = (i % 2 == 0 ? .5F : -.2F) + .15F * sin(phase * .11F + i * 1.9F);
            Vec3 dir = new Vec3(cos(angle), lift, sin(angle)).normalize();
            // In the bender's own view, arms reaching ahead would sweep across the crosshair: those stay out of sight.
            if (self && flat(dir).dot(flat(player.getViewVector(c.partial))) > .85) continue;
            Vec3 root = center.add(dir.scale(1.0 * grow));
            float length = (1.8F + .5F * sin(phase * .13F + i * 2.3F)) * grow;
            Vec3 tip = root.add(dir.scale(length)).add(0, .4 * sin(phase * .17F + i), 0);
            Vec3 bend = UP.scale(.7 * sin(phase * .09F + i * 1.3F)).add(side(dir).scale(.6 * cos(phase * .12F + i)));
            arm(c, root, tip, .17F, .035F, bend, phase + i * 2, .85F, i * 31 + bender.entity, 0);
        }
    }

    /**
     * Wave Rider: a cresting wave carries the rider, its lip curling up behind their heels, a fan of spray off its
     * edges and a white wake trailing back over the ground.
     */
    private static void surf(Canvas c, Player player, Vec3 feet, float phase, int seed) {
        Vec3 travel = FlightVisualGeometry.travel(player);
        Vec3 dir = travel.horizontalDistanceSqr() > 1e-4 ? flat(travel) : flat(Vec3.directionFromRotation(0, player.getYRot()));
        Vec3 right = new Vec3(-dir.z, 0, dir.x);
        // Big enough to read from any side: the crest stands a block behind the heels, the rider rides its face, and its
        // lip curls forward over their back.
        float halfWidth = 1.45F, height = 1.8F, L = 1.1F;
        float[] along = {-1.3F, -.9F, -.5F, -.2F, .1F, .3F};
        float[] up = {0, .45F, .85F, 1.0F, .92F, .7F};
        float[] tint = {0, .35F, .6F, .8F, .95F, 1};
        int slices = minimal() ? 5 : 9, rows = along.length;
        Vec3[][] grid = new Vec3[slices][rows];
        float[][] tints = new float[slices][rows], alphas = new float[slices][rows];
        Vec3[] lip = new Vec3[slices];
        float[] lw = new float[slices], la = new float[slices];
        // The crest sits just behind the heels; the rider stands on its face.
        Vec3 base = feet.add(dir.scale(-.75)).add(0, -.15, 0);
        for (int i = 0; i < slices; i++) {
            float s = i / (slices - 1F) * 2 - 1;
            float edge = (float) Math.pow(Math.max(0, 1 - s * s), .5);
            for (int j = 0; j < rows; j++) {
                double x = along[j] * L - .4 * s * s + .06 * sin(phase * 1.6F + s * 6 + j);
                grid[i][j] = base.add(dir.scale(x)).add(right.scale(s * halfWidth)).add(0, up[j] * height * edge, 0);
                tints[i][j] = tint[j];
                alphas[i][j] = .7F * smooth(0, .3F, edge + .05F) * clearance(c, grid[i][j], .9F);
            }
            lip[i] = grid[i][rows - 1].add(0, .04, 0);
            lw[i] = .1F * edge + .03F;
            la[i] = edge * clearance(c, lip[i], .9F);
        }
        sheet(c, grid, tints, alphas, 0);
        foam(c, lip, lw, la);
        // White water churning under the rider's feet, where the face meets the ground.
        Vec3[] churn = new Vec3[slices];
        float[] cw = new float[slices], ca = new float[slices];
        for (int i = 0; i < slices; i++) {
            float s = i / (slices - 1F) * 2 - 1;
            churn[i] = feet.add(right.scale(s * halfWidth * .9)).add(dir.scale(.15 - .3 * s * s)).add(0, .05 + .06 * sin(phase + s * 4), 0);
            cw[i] = .22F;
            ca[i] = .8F * (1 - s * s * .6F) * clearance(c, churn[i], .9F);
        }
        foam(c, churn, cw, ca);
        for (int side : new int[]{-1, 1})
            spray(c, base.add(right.scale(side * halfWidth * .9)).add(dir.scale(-.4)), right.scale(side).add(dir.scale(-.3)).add(0, .7, 0).normalize(),
                    .4F, .2F, (phase * 1.4F + side * 2) % 7, 7, .8F, seed + side, 4, 0);
        if (c.soft()) {
            int n = 7;
            Vec3[] wake = new Vec3[n];
            float[] w = new float[n], a = new float[n];
            for (int i = 0; i < n; i++) {
                float u = i / (n - 1F);
                wake[i] = feet.add(dir.scale(-.8 - u * 4)).add(right.scale(.15 * sin(phase * .5F + u * 4))).add(0, .04, 0);
                w[i] = .45F + .5F * u;
                a[i] = .5F * (1 - u);
            }
            c.ribbon(wake, w, FOAM, a);
            c.ribbon(wake, w, TEAL, a);
        }
    }

    /** Water Spout: a column of water from the ground to the bender's feet, spiralling, foaming at its foot. */
    private static void spout(Canvas c, Vec3 feet, float phase, int seed) {
        Vec3 ground = HydroGeometry.ground(c.level, feet, 0, 12);
        if (ground == null) ground = feet.add(0, -8, 0);
        double height = feet.y - ground.y;
        if (height < .2) return;
        int n = 10;
        Vec3[] spine = new Vec3[n];
        float[] r = new float[n], a = new float[n];
        for (int i = 0; i < n; i++) {
            float u = i / (n - 1F);
            spine[i] = ground.add(sin(phase * .4F + u * 5) * .1, height * u, cos(phase * .4F + u * 5) * .1);
            r[i] = .85F - .4F * u + .06F * sin(phase + u * 8);
            a[i] = .7F * clearance(c, spine[i], 1.1F);
        }
        tube(c, spine, r, a, 0);
        if (c.glow() && !minimal()) for (int s = 0; s < 2; s++) {
            Vec3[] streak = new Vec3[12];
            float[] w = new float[12], sa = new float[12];
            for (int i = 0; i < streak.length; i++) {
                float u = i / 11F, angle = s * PI + u * 6 + phase * .5F, rad = .9F - .42F * u;
                streak[i] = ground.add(cos(angle) * rad, height * u, sin(angle) * rad);
                w[i] = .05F;
                sa[i] = .35F * sin(PI * u) * clearance(c, streak[i], 1.1F);
            }
            c.ribbon(streak, w, SHALLOW, sa);
        }
        Vec3[] footRing = circle(ground.add(0, .05, 0), 1.1F, 18, phase * .2F);
        float[] fw = new float[footRing.length], fa = new float[footRing.length];
        for (int i = 0; i < footRing.length; i++) { fw[i] = .2F; fa[i] = .7F; }
        foam(c, footRing, fw, fa);
        spray(c, feet, UP, 1F, .12F, (phase * 1.2F) % 8, 8, .7F * clearance(c, feet, 1.2F), seed, 6, 0);
    }

    /**
     * Maelstrom's additions to the shared tornado (drawn by {@code SpeedArt} in the waterspout's colours): the
     * whirlpool turning at its foot, its dark eye, a crown of spray where the spout leaves the water, foam bands spiralling up.
     */
    static void waterspout(Canvas c, Vec3 o, float built, float f, float ring, float height, float collapse) {
        if (f < .01F) return;
        Vec3 floor = o.add(0, .06, 0);
        float spin = built * .3F;
        int arms = minimal() ? 3 : 5;
        for (int s = 0; s < arms; s++) {
            int n = minimal() ? 14 : 32;
            Vec3[] spiral = new Vec3[n];
            float[] w = new float[n], a = new float[n];
            for (int i = 0; i < n; i++) {
                float u = i / (n - 1F), r = ring * (1.8F - u * 1.5F);
                float angle = s * TWO_PI / arms + spin + u * 4.5F;
                spiral[i] = floor.add(cos(angle) * r, 0, sin(angle) * r);
                w[i] = .2F + .12F * (1 - u);
                a[i] = f * .7F * smooth(0, .15F, u) * (1 - smooth(.85F, 1, u));
            }
            foam(c, spiral, w, a);
        }
        if (c.soft()) {
            c.disc(floor, UP, ring * .55F, DEEP, f * .55F, 0);
            c.disc(floor.add(0, .01, 0), UP, ring * 1.8F, SEA, f * .2F, 0);
        }
        int seed = (int) (o.x * 31 + o.z * 17);
        for (int i = 0; i < 4; i++)
            spray(c, floor.add(cos(i * HALF_PI + spin) * ring * .5, 0, sin(i * HALF_PI + spin) * ring * .5),
                    new Vec3(cos(i * HALF_PI + spin), 1.6, sin(i * HALF_PI + spin)).normalize(), .5F, .4F,
                    (built * 1.3F + i * 2.5F) % 10, 10, f, seed + i, 5, 0);
        if (c.glow() && !minimal()) for (int band = 0; band < 3; band++) {
            int n = 24;
            Vec3[] helix = new Vec3[n];
            float[] w = new float[n], a = new float[n];
            for (int i = 0; i < n; i++) {
                float u = i / (n - 1F), h = u * height * .9F;
                float wall = com.jayptucker.heroesevolved.speed.TornadoShape.radius(h, height, ring) * .97F;
                float angle = band * TWO_PI / 3 + u * 7 + built * (.5F - .3F * u);
                helix[i] = o.add(cos(angle) * wall, h, sin(angle) * wall);
                w[i] = .12F + wall * .02F;
                a[i] = f * .35F * sin(PI * u) * (u < collapse ? 0 : 1);
            }
            c.ribbon(helix, w, FOAM, a);
        }
    }

    // ------------------------------------------------------------------
    // Shapes
    // ------------------------------------------------------------------

    /**
     * A tube of water along {@code spine}: lit by the sun, sea-blue where it faces away and teal where it faces up,
     * more opaque at its silhouette (where you look through more water), with a thin glint along its sunlit side.
     * {@code pale} mixes it toward foam (water pulled thin from the air).
     */
    private static void tube(Canvas c, Vec3[] spine, float[] radius, float[] alpha, float pale) {
        int n = spine.length;
        if (n < 2) return;
        if (c.soft()) {
            int sides = minimal() ? 5 : 7;
            double[][] prev = null;
            float[][] prevC = null;
            float[] prevA = null;
            for (int i = 0; i < n; i++) {
                Vec3 tangent = spine[Math.min(i + 1, n - 1)].subtract(spine[Math.max(i - 1, 0)]);
                tangent = tangent.lengthSqr() < 1e-8 ? UP : tangent.normalize();
                Vec3 right = side(tangent), up = tangent.cross(right);
                double[][] ring = new double[sides + 1][];
                float[][] col = new float[sides + 1][];
                float[] a = new float[sides + 1];
                for (int k = 0; k <= sides; k++) {
                    float angle = k * TWO_PI / sides;
                    double nx = right.x * cos(angle) + up.x * sin(angle), ny = right.y * cos(angle) + up.y * sin(angle),
                            nz = right.z * cos(angle) + up.z * sin(angle);
                    double x = spine[i].x + nx * radius[i], y = spine[i].y + ny * radius[i], z = spine[i].z + nz * radius[i];
                    ring[k] = new double[]{x, y, z};
                    double vx = c.cx - x, vy = c.cy - y, vz = c.cz - z, l = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-6;
                    float facing = (float) Math.abs((nx * vx + ny * vy + nz * vz) / l);
                    float[] base = mix(SEA, TEAL, clamp(.3F + .55F * (float) ny, 0, 1));
                    if (pale > 0) base = mix(base, FOAM, pale);
                    col[k] = c.lit((float) nx, (float) ny, (float) nz, base, new float[3]);
                    a[k] = alpha[i] * (.45F + .55F * (1 - facing) * (1 - facing));
                }
                if (prev != null) for (int k = 0; k < sides; k++)
                    quad(c, prev[k], prevC[k], prevA[k], prev[k + 1], prevC[k + 1], prevA[k + 1],
                            ring[k + 1], col[k + 1], a[k + 1], ring[k], col[k], a[k]);
                prev = ring;
                prevC = col;
                prevA = a;
            }
        }
        if (c.glow() && !minimal()) {
            Vec3[] glint = new Vec3[n];
            float[] w = new float[n], a = new float[n];
            for (int i = 0; i < n; i++) {
                glint[i] = spine[i].add(-.25 * radius[i], radius[i] * .6, -.2 * radius[i]);
                w[i] = radius[i] * .28F;
                a[i] = alpha[i] * .3F;
            }
            c.ribbon(glint, w, SHALLOW, a);
        }
    }

    /** One arm of water from {@code root} to {@code tip}, bowed by {@code bend}, tapering from {@code thick} to {@code fine}. */
    private static void arm(Canvas c, Vec3 root, Vec3 tip, float thick, float fine, Vec3 bend, float phase, float alpha, int seed, float pale) {
        Vec3 path = tip.subtract(root);
        double length = path.length();
        if (length < .05 || alpha < .01F) return;
        int n = minimal() ? 7 : 12;
        Vec3[] spine = new Vec3[n];
        float[] r = new float[n], a = new float[n];
        Vec3 across = side(path.normalize());
        for (int i = 0; i < n; i++) {
            float u = i / (n - 1F);
            double wave = sin(u * PI * 1.6F - phase * .5F) * .18 * u * length * .3;
            spine[i] = root.add(path.scale(u)).add(bend.scale(sin(PI * u) * length * .25)).add(across.scale(wave));
            r[i] = lerp(u, thick, fine) * (1 + .12F * sin(phase + u * 9));
            a[i] = alpha * clearance(c, spine[i], .8F);
        }
        tube(c, spine, r, a, pale);
    }

    /** A ring of water lying flat round {@code center}, {@code span} radians long, turning with {@code spin}. */
    private static void ringTube(Canvas c, Vec3 center, float radius, float thick, float spin, float span, float alpha, float pale) {
        int n = minimal() ? 10 : 18;
        Vec3[] spine = new Vec3[n];
        float[] r = new float[n], a = new float[n];
        for (int i = 0; i < n; i++) {
            float u = i / (n - 1F), angle = spin + span * u;
            spine[i] = center.add(cos(angle) * radius, .04 * sin(angle * 3 + spin), sin(angle) * radius);
            r[i] = thick * (span < TWO_PI - .1F ? sin(PI * u) : 1);
            a[i] = alpha * clearance(c, spine[i], .8F);
        }
        tube(c, spine, r, a, pale);
    }

    /** A ball of water: a wobbling shell with a glint. */
    private static void orb(Canvas c, Vec3 at, float radius, float phase, float alpha, float pale) {
        if (c.soft()) c.shell(at, radius, radius * .92F, radius, PI, mix(TEAL, FOAM, pale), alpha * .7F, .45F, wobble(phase), phase * .1F);
        if (c.glow()) c.glow(at.add(-radius * .35, radius * .4, -radius * .3), radius * .5F, FOAM, alpha * .4F);
    }

    /**
     * A sheet of water through a grid of points ({@code grid[i][j]}: across, then along its profile), lit by the sun
     * on whichever side faces the viewer; {@code tint} runs it from deep (0) through sea and teal to shallow (1).
     */
    private static void sheet(Canvas c, Vec3[][] grid, float[][] tint, float[][] alpha, float pale) {
        if (!c.soft()) return;
        int cols = grid.length, rows = grid[0].length;
        float[][][] col = new float[cols][rows][];
        for (int i = 0; i < cols; i++)
            for (int j = 0; j < rows; j++) {
                Vec3 du = grid[Math.min(i + 1, cols - 1)][j].subtract(grid[Math.max(i - 1, 0)][j]);
                Vec3 dv = grid[i][Math.min(j + 1, rows - 1)].subtract(grid[i][Math.max(j - 1, 0)]);
                Vec3 normal = du.cross(dv);
                normal = normal.lengthSqr() < 1e-8 ? UP : normal.normalize();
                Vec3 view = c.camera().subtract(grid[i][j]);
                if (normal.dot(view) < 0) normal = normal.scale(-1);
                float[] base = water(tint[i][j]);
                if (pale > 0) base = mix(base, FOAM, pale);
                col[i][j] = c.lit((float) normal.x, (float) normal.y, (float) normal.z, base, new float[3]);
                float facing = (float) Math.abs(normal.dot(view.normalize()));
                alpha[i][j] *= (.6F + .4F * (1 - facing)) * clearance(c, grid[i][j], .9F);
            }
        for (int i = 0; i < cols - 1; i++)
            for (int j = 0; j < rows - 1; j++)
                quad(c, xyz(grid[i][j]), col[i][j], alpha[i][j], xyz(grid[i + 1][j]), col[i + 1][j], alpha[i + 1][j],
                        xyz(grid[i + 1][j + 1]), col[i + 1][j + 1], alpha[i + 1][j + 1], xyz(grid[i][j + 1]), col[i][j + 1], alpha[i][j + 1]);
    }

    /** Deep (0) to sea (.35) to teal (.75) to shallow (1). */
    private static float[] water(float k) {
        if (k < .35F) return mix(DEEP, SEA, k / .35F);
        if (k < .75F) return mix(SEA, TEAL, (k - .35F) / .4F);
        return mix(TEAL, SHALLOW, Math.min(1, (k - .75F) / .25F));
    }

    /** A line of white foam along {@code line}, with a faint glint on its crest. */
    private static void foam(Canvas c, Vec3[] line, float[] width, float[] alpha) {
        if (line.length < 2) return;
        if (c.soft()) {
            float[] a = new float[line.length], w = new float[line.length];
            for (int i = 0; i < line.length; i++) { a[i] = alpha[i] * .85F * clearance(c, line[i], .9F); w[i] = width[i]; }
            c.ribbon(line, w, FOAM, a);
            for (int i = 0; i < line.length; i++) { w[i] = width[i] * 1.9F; a[i] *= .3F; }
            c.ribbon(line, w, TEAL, a);
        }
        if (c.glow() && !minimal()) {
            float[] a = new float[line.length], w = new float[line.length];
            for (int i = 0; i < line.length; i++) { a[i] = alpha[i] * .25F * clearance(c, line[i], .9F); w[i] = width[i] * .35F; }
            c.ribbon(line, w, SHALLOW, a);
        }
    }

    /**
     * Drops thrown from {@code at} round {@code up} (spreading by {@code spread}) at {@code speed} blocks a tick, falling
     * under gravity: each a short streak along its flight, gone by {@code life} ticks.
     */
    private static void spray(Canvas c, Vec3 at, Vec3 up, float spread, float speed, float t, float life, float alpha, int seed,
                              int count, float pale) {
        if (!c.soft() || alpha < .01F || t < 0 || t > life) return;
        int n = minimal() ? Math.max(1, count / 3) : detail() ? count * 2 : count;
        Vec3 right = side(up), fwd = up.cross(right);
        float[] col = mix(FOAM, SHALLOW, .25F + pale * .2F);
        for (int i = 0; i < n; i++) {
            float angle = hash(seed, i) * TWO_PI, tilt = hash(seed, i + 50) * spread;
            Vec3 v = right.scale(cos(angle) * tilt).add(fwd.scale(sin(angle) * tilt)).add(up.scale(1 - tilt * .4F))
                    .scale(speed * (.55F + .45F * hash(seed, i + 100)));
            float tt = t * (.8F + .4F * hash(seed, i + 150));
            if (tt > life) continue;
            Vec3 p = at.add(v.scale(tt)).add(0, -.045 * tt * tt, 0);
            Vec3 vel = v.add(0, -.09 * tt, 0);
            float k = alpha * (1 - tt / life) * clearance(c, p, .7F);
            c.line(p.subtract(vel.scale(.8)), p, .03F, col, k * .2F, k);
        }
    }

    /** A ring on the ground or water: a teal band, a foam line and a faint glint. */
    private static void ripple(Canvas c, Vec3 at, float radius, float width, float alpha) {
        if (alpha < .01F || radius < .03F) return;
        if (c.soft()) {
            c.ring(at, UP, radius, width * 2.6F, 0, TWO_PI, TEAL, alpha * .35F);
            c.ring(at, UP, radius, width, 0, TWO_PI, FOAM, alpha * .55F);
        }
        if (c.glow()) c.ring(at, UP, radius, width * .4F, 0, TWO_PI, SHALLOW, alpha * .35F);
    }

    /**
     * A splash: a crown of blades thrown up and out from a ring, falling back; spray; two rings spreading on the ground;
     * mist. {@code hard} for a full crown, otherwise just the spray and rings.
     */
    private static void splash(Canvas c, Vec3 at, float radius, float t, float alpha, int seed, float pale, boolean hard) {
        if (t < 0 || alpha < .01F) return;
        float k = alpha * (1 - smooth(6, 14, t));
        if (hard && t < 10) {
            int blades = minimal() ? 5 : detail() ? 12 : 8;
            float rise = sin(PI * clamp(t / 9, 0, 1));
            for (int i = 0; i < blades; i++) {
                float angle = (i + hash(seed, i) * .5F) * TWO_PI / blades;
                Vec3 out = new Vec3(cos(angle), 0, sin(angle));
                Vec3 base = at.add(out.scale(radius * .3));
                Vec3 tip = base.add(out.scale(radius * (.3 + .5 * smooth(0, 8, t)))).add(0, radius * (.6 + .4 * hash(seed, i + 20)) * rise, 0);
                Vec3[] spine = {base, base.lerp(tip, .55).add(0, radius * .12 * rise, 0), tip};
                float[] r = {.06F * radius + .02F, .045F * radius + .015F, .01F};
                float[] a = {k, k, k * .7F};
                tube(c, spine, r, a, pale);
            }
        }
        spray(c, at, UP, .9F, .16F + .05F * radius, t, 12, k, seed, 8, pale);
        ripple(c, at.add(0, .04, 0), radius * (.4F + 1.2F * smooth(0, 12, t)), .06F, k);
        ripple(c, at.add(0, .04, 0), radius * (.2F + .9F * smooth(3, 14, t)), .04F, k * .6F);
        mist(c, at, radius * .6F + .2F, t, k * .55F, seed);
    }

    /** Fine mist hanging and spreading where water broke. */
    private static void mist(Canvas c, Vec3 at, float radius, float t, float alpha, int seed) {
        if (!c.soft() || !detail() || alpha < .01F) return;
        for (int i = 0; i < 2; i++) {
            float s = radius * (.6F + t * .04F);
            Vec3 puff = at.add((hash(seed, i + 70) - .5) * radius, .2 + t * .03, (hash(seed, i + 80) - .5) * radius);
            c.cloud(puff, s, s * .55F, s, seed + i, t * .02F, alpha * .16F * smooth(0, 4, t) * clearance(c, puff, 1.4F),
                    Shades.smoke(c, FOAM, 0));
        }
    }

    /** Bubbles breaking a surface: small rings that pop and spread. */
    private static void bubbles(Canvas c, Vec3 at, float radius, float t, float alpha, int seed) {
        if (alpha < .01F || minimal()) return;
        for (int i = 0; i < 6; i++) {
            float k = frac(t * .14F + hash(seed, i + 30));
            float angle = hash(seed, i + 40 + (int) (t * .14F + hash(seed, i + 30))) * TWO_PI;
            float r = radius * (float) Math.sqrt(hash(seed, i + 50));
            ripple(c, at.add(cos(angle) * r, .02, sin(angle) * r), .05F + .25F * k, .025F, alpha * (1 - k));
        }
    }

    private static Canvas.Pattern wobble(float phase) {
        return (theta, phi) -> .7F + .3F * sin(theta * 5 + phase * .8F + phi * 4) * sin(phi * 3 - phase * .5F);
    }

    private static Vec3[] circle(Vec3 center, float radius, int n, float spin) {
        Vec3[] points = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            float angle = spin + i * TWO_PI / n;
            points[i] = center.add(cos(angle) * radius, 0, sin(angle) * radius);
        }
        return points;
    }

    private static void quad(Canvas c, double[] p0, float[] c0, float a0, double[] p1, float[] c1, float a1,
                             double[] p2, float[] c2, float a2, double[] p3, float[] c3, float a3) {
        c.v(p0[0], p0[1], p0[2], c0, a0); c.v(p1[0], p1[1], p1[2], c1, a1); c.v(p2[0], p2[1], p2[2], c2, a2);
        c.v(p0[0], p0[1], p0[2], c0, a0); c.v(p2[0], p2[1], p2[2], c2, a2); c.v(p3[0], p3[1], p3[2], c3, a3);
    }

    private static double[] xyz(Vec3 v) { return new double[]{v.x, v.y, v.z}; }

    /** Fades a point out as it nears the camera, inside {@code near} blocks, so water passing the eye never fills the view. */
    private static float clearance(Canvas c, Vec3 point, float near) {
        return smooth(near * .55F, near * 1.6F, (float) point.distanceTo(c.camera()));
    }

    /** {@code v} with its component along {@code along} removed, normalized (or zero if they are parallel). */
    private static Vec3 perpendicular(Vec3 along, Vec3 v) {
        if (along.lengthSqr() < 1e-6) return v;
        Vec3 axis = along.normalize();
        Vec3 off = v.subtract(axis.scale(v.dot(axis)));
        return off.lengthSqr() < 1e-6 ? side(axis) : off.normalize();
    }

    private static Vec3 flat(Vec3 v) {
        Vec3 level = new Vec3(v.x, 0, v.z);
        return level.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : level.normalize();
    }

    private static Vec3 side(Vec3 axis) { return (Math.abs(axis.y) > .93 ? new Vec3(0, 0, 1) : UP).cross(axis).normalize(); }

    /**
     * A hand as the viewer sees it: {@code main} for the main arm. In the bender's own first-person view it is the
     * camera-space hand, on its own side of the crosshair.
     */
    private static Vec3 hand(Canvas c, Player owner, boolean main) {
        Minecraft mc = Minecraft.getInstance();
        if (owner != mc.player || mc.options.getCameraType() != CameraType.FIRST_PERSON) return HydroGeometry.hand(owner, c.partial, main);
        Vec3 look = owner.getViewVector(c.partial);
        Vec3 right = Vec3.directionFromRotation(0, owner.getViewYRot(c.partial) + 90);
        Vec3 cameraUp = right.cross(look).normalize();
        boolean rightSide = (owner.getMainArm() == HumanoidArm.RIGHT) == main;
        return c.camera().add(look.scale(.77)).add(right.scale(rightSide ? .39 : -.39)).subtract(cameraUp.scale(.43));
    }

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }
    private static boolean detail() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.ALL; }

    /**
     * Breaker (the finisher, {@code hydro/Breaker}): a curling wave carries the rider on its crest along the same path the
     * server moves them ({@code Breaker.ride}), its body falling away to the ground behind; when its lip comes down on the
     * foe it breaks into a ring of foam that runs out over the ground and a burst of spray. In water or rain it is twice
     * the size. Origin the rider's start, target the foe's feet; scale the foe's height (+ WET).
     */
    /** The alternate finishers ({@code hydro/HydroFinishers}): Drowning Bubble and Riptide. */
    static void alternates(Canvas c, Active a, float t) {
        if (c.solid()) return;
        float h = Math.max(.6F, a.scale()), f = fade(t, a.piece().ticks, 2, 8);
        float phase = (c.level.getGameTime() % 2400 + c.partial) * .3F;
        Player owner = c.level.getEntity(a.seed()) instanceof Player p ? p : null;
        if (a.piece() == Piece.FINISH_BUBBLE) {
            // A ball of water closing over the head, bubbles streaming up through it, then bursting into a splash.
            int close = com.jayptucker.heroesevolved.hydro.HydroFinishers.BUBBLE_CLOSE, land = com.jayptucker.heroesevolved.hydro.HydroFinishers.BUBBLE_LAND;
            Vec3 feet = a.origin(), head = feet.add(0, h * .85, 0);
            if (t < land) {
                float grow = smooth(0, close, t), struggle = .04F * sin(t * 2.3F);
                Vec3 centre = head.add(0, .05 + struggle, 0);
                float r = .1F + .45F * grow;
                // Water drawn up from the ground to the head while it forms.
                if (t < close + 2 && owner != null) {
                    Vec3 palm = hand(c, owner, true);
                    whip(c, palm, centre, t, close, .1F, f * (1 - smooth(close, close + 2, t)), phase, a.seed(), UP, 0);
                }
                orb(c, centre, r, phase, f * (.6F + .4F * grow), 0);
                ringTube(c, centre, r * 1.02F, .05F, phase * .3F, TWO_PI, f * .6F * grow, .3F);
                // Bubbles rising inside, each a small bright bead that wobbles up and pops at the top.
                if (c.glow() && t > close) for (int i = 0; i < (minimal() ? 3 : 7); i++) {
                    float k = frac(t * .09F + hash(a.seed(), i));
                    float ang = hash(a.seed(), i + 9) * TWO_PI + k * 3;
                    Vec3 bead = centre.add(cos(ang) * r * .4 * (1 - k), -r * .7 + k * r * 1.5, sin(ang) * r * .4 * (1 - k));
                    c.glow(bead, .035F + .025F * hash(a.seed(), i + 4), FOAM, f * .8F);
                }
                spray(c, centre.add(0, r, 0), UP, .3F, .06F, t - close, 40, f * .4F * smooth(close, close + 3, t), a.seed(), 3, .3F);
            } else {
                splash(c, head, 1.1F, t - land, f, a.seed(), 0, true);
                splash(c, feet, .9F, t - land - 2, f * .7F, a.seed() + 1, 0, false);
            }
            return;
        }
        // Riptide: a whip from the palm to the foe, coiled round its waist; it swings once round the bender on the end of
        // it, trailing spray, and a splash where it lands.
        int wrap = com.jayptucker.heroesevolved.hydro.HydroFinishers.RIPTIDE_WRAP, land = com.jayptucker.heroesevolved.hydro.HydroFinishers.RIPTIDE_LAND;
        Entity foe = a.bound(c);
        Vec3 feet = foe != null && foe.isAlive() ? foe.getPosition(c.partial) : a.origin();
        Vec3 waist = feet.add(0, h * .5, 0);
        if (t < land + 1) {
            Vec3 palm = owner != null ? hand(c, owner, true) : a.target().add(0, 1.3, 0);
            Vec3 path = waist.subtract(palm);
            Vec3 bend = path.lengthSqr() < 1e-4 ? UP : path.normalize().cross(UP);
            if (bend.lengthSqr() < 1e-4) bend = new Vec3(1, 0, 0);
            float reach = smooth(0, wrap, t);
            int n = minimal() ? 9 : 14;
            Vec3[] spine = new Vec3[n];
            float[] r = new float[n], al = new float[n];
            for (int i = 0; i < n; i++) {
                float u = reach * i / (n - 1F);
                spine[i] = palm.lerp(waist, u).add(bend.normalize().scale(sin(PI * u) * .35 * (1 - smooth(wrap, wrap + 3, t))))
                        .add(0, sin(PI * u) * .25, 0);
                r[i] = .11F * (1 - .4F * u);
                al[i] = f * .9F;
            }
            tube(c, spine, r, al, 0);
            if (t > wrap - 2) ringTube(c, waist, Math.max(.32F, h * .22F), .09F, phase * .5F, TWO_PI, f * smooth(wrap - 2, wrap, t), 0);
            if (t > wrap) spray(c, waist, UP, .6F, .08F, (t - wrap) % 6, 6, f * .6F, a.seed() + (int) ((t - wrap) / 6), 4, 0);
        }
        if (t >= land) {
            splash(c, a.origin(), 1.3F, t - land, f, a.seed(), 0, true);
            ripple(c, a.origin().add(0, .04, 0), .5F + 2.4F * smooth(land, land + 9, t), .08F, f * (1 - smooth(land + 4, land + 10, t)));
        }
    }

    static void finisher(Canvas c, Active a, float t) {
        boolean wet = a.scale() >= com.jayptucker.heroesevolved.hydro.Breaker.WET;
        float size = wet ? 1.8F : 1;
        Vec3 home = a.origin(), foe = a.target();
        Vec3 dir = flat(foe.subtract(home)), right = new Vec3(-dir.z, 0, dir.x);
        float f = fade(t, a.piece().ticks, 2, 8), phase = (c.level.getGameTime() % 2400 + c.partial) * .3F;
        int land = com.jayptucker.heroesevolved.hydro.Breaker.LAND;
        if (t <= land + 1) {
            Vec3 rider = com.jayptucker.heroesevolved.hydro.Breaker.ride(home, foe, Math.min(t, land));
            double ground = Math.min(home.y, foe.y);
            float half = .9F * size, grow = smooth(0, 6, t);
            int n = 7;
            Vec3[] crest = new Vec3[n], lip = new Vec3[n];
            float[] r = new float[n], al = new float[n], w = new float[n];
            for (int i = 0; i < n; i++) {
                float u = i / (n - 1F) * 2 - 1;
                crest[i] = rider.add(right.scale(u * half)).add(0, -.3 - .2 * u * u, 0);
                lip[i] = crest[i].add(dir.scale(.25 * size)).add(0, .28 * size, 0);
                r[i] = (.42F + .08F * sin(phase + i)) * size * (1 - .5F * u * u) * grow;
                al[i] = f * (1 - .4F * u * u);
                w[i] = .12F * size;
            }
            tube(c, crest, r, al, 0);
            for (int i = 0; i < 3; i++) {
                float u = (i - 1) * .6F;
                Vec3 top = rider.add(right.scale(u * half)).add(0, -.45, 0);
                Vec3 base = new Vec3(top.x, ground + .1, top.z).subtract(dir.scale(1.2 * size));
                tube(c, new Vec3[]{top, top.lerp(base, .5).subtract(dir.scale(.3)), base},
                        new float[]{.42F * size * grow, .58F * size * grow, .7F * size * grow}, new float[]{f * .8F, f * .7F, f * .5F}, 0);
            }
            foam(c, lip, w, al);
            // The lip: thrown from the crest in an arc and down onto the foe, reaching it as the blow lands.
            int crestAt = com.jayptucker.heroesevolved.hydro.Breaker.CREST;
            if (t > crestAt - 2) {
                float reach = smooth(crestAt - 2, land, t);
                Vec3 from = rider.add(dir.scale(.35)).add(0, .1, 0), to = foe.add(0, Math.max(.6F, a.scale() % 32) * .7, 0);
                Vec3 mid = from.lerp(to, .5).add(0, 1.4 * size, 0);
                int m = 9;
                Vec3[] arc = new Vec3[m];
                float[] ar = new float[m], aa = new float[m], aw = new float[m];
                for (int i = 0; i < m; i++) {
                    float u = reach * i / (m - 1F), v = 1 - u;
                    arc[i] = from.scale(v * v).add(mid.scale(2 * u * v)).add(to.scale(u * u));
                    float tip = i / (m - 1F);
                    ar[i] = (.38F - .2F * tip) * size * (1 + .08F * sin(phase * 2 + i));
                    aa[i] = f * (.85F - .25F * tip);
                    aw[i] = .1F * size;
                }
                tube(c, arc, ar, aa, 0);
                foam(c, arc, aw, aa);
                spray(c, arc[m - 1], dir.add(0, .6, 0).normalize(), .5F * size, .18F, t - crestAt, 8, f * .8F, a.seed() + 3, 6, 0);
            }
        }
        if (t >= land - 1) {
            float k = smooth(land - 1, land + 8, t);
            int n = 28;
            Vec3 at = foe.add(0, .05, 0);
            Vec3[] ring = new Vec3[n + 1];
            float[] w = new float[n + 1], al = new float[n + 1];
            float radius = (.5F + 3.2F * k) * size;
            for (int i = 0; i <= n; i++) {
                float ang = i * TWO_PI / n;
                ring[i] = at.add(cos(ang) * radius, 0, sin(ang) * radius);
                w[i] = .04F + .16F * (1 - k);
                al[i] = f * (1 - k);
            }
            foam(c, ring, w, al);
            spray(c, foe.add(0, .3, 0), UP, 1.2F * size, .25F, t - land, 14, f, a.seed(), 10, 0);
        }
    }
}
