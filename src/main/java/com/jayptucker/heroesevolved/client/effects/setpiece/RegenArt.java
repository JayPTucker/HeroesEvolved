package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/**
 * Claire Bennet's blood and heartbeat as set pieces, in the look her older effects already have
 * ({@code RegenerationEffects}): crimson for blood, warm gold for life. Blood is drawn with normal blending, so it reads
 * as blood rather than light; only its wet highlight glows. Four primitives draw every piece:
 * <ul>
 *   <li>{@link #strand}: a sagging, wavering ribbon of blood between two points;</li>
 *   <li>{@link #drops}: beads of blood travelling along a line;</li>
 *   <li>{@link #beat}: a heartbeat ring that jumps twice (lub-dub);</li>
 *   <li>{@link #ecg}: a heart-monitor trace whose spikes can flatten.</li>
 * </ul>
 * Palette: {@code B0101C} blood, {@code FF4050} bright, {@code 3A060C} clot, {@code FFD9A0} life, {@code FFF4F0} core.
 */
final class RegenArt {
    private static final float[] BLOOD = rgb(0xB0101C), BRIGHT = rgb(0xFF4050), CLOT = rgb(0x3A060C), LIFE = rgb(0xFFD9A0),
            CORE = rgb(0xFFF4F0);
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private RegenArt() {}

    static void draw(Canvas c, Active a, float t) {
        Vec3 at = a.origin(), end = a.target();
        Entity bound = a.bound(c);
        float life = a.piece().ticks, size = a.scale();
        float clock = (c.level.getGameTime() % 2400 + c.partial);
        int seed = a.seed();
        switch (a.piece()) {
            case REGEN_STREAM -> {
                Vec3 from = bound == null ? at : bound.getPosition(c.partial).add(0, 1.1, 0)
                        .add(bound.getViewVector(c.partial).scale(0.55));
                float alpha = fade(t, life, 1, 2) * 0.6F;
                strand(c, from, end, clock * 0.4F, 0.09F, alpha, 0.35F);
                drops(c, from, end, clock * 0.09F, minimal() ? 3 : 6, 0.07F, alpha * 1.6F, 0.35F);
                if (c.glow()) c.glow(end, 0.35F, BRIGHT, alpha * 0.5F);
            }
            case REGEN_FAN -> fan(c, at, end.subtract(at), t, seed);
            case REGEN_IMPACT -> {
                Vec3 ground = at.add(0, 0.06, 0);
                float p = clamp(t / 20, 0, 1), open = 1 - (float) Math.pow(1 - clamp(t / 8, 0, 1), 3);
                if (c.soft()) for (int i = 0; i < (minimal() ? 6 : 12); i++) {
                    float ang = i * TWO_PI / 12 + hash(seed, i) * 0.4F, reach = size * (0.4F + 0.5F * hash(seed, i + 30)) * open;
                    Vec3 d = new Vec3(cos(ang), 0, sin(ang));
                    gash(c, ground.add(d.scale(0.3)), ground.add(d.scale(0.3 + reach)), seed + i, 0.07F, (1 - p) * 0.9F);
                }
                beat(c, ground.add(0, 0.02, 0), UP, size, t, 1 - p);
                if (c.glow() && t < 4) c.glow(at.add(0, 0.8, 0), 0.9F, BRIGHT, 0.6F * (1 - t / 4));
            }
            case REGEN_PAYBACK -> {
                Vec3 o = bound == null ? at : bound.getPosition(c.partial);
                float alpha = fade(t, life, 0.5F, 10);
                beat(c, o.add(0, 0.06, 0), UP, size, t, alpha);
                beat(c, o.add(0, 1.1, 0), UP, size * 0.6F, t - 2, alpha * 0.7F);
                if (c.glow() && t < 5) c.glow(o.add(0, 1.1, 0), 0.7F, LIFE, 0.7F * (1 - t / 5));
            }
            case REGEN_FEIGN -> {
                if (bound == null) return;
                Vec3 o = bound.getPosition(c.partial);
                // Above the body lying still: a heart monitor whose beat slows and flattens.
                float alpha = fade(t, life, 2, 2) * 0.8F;
                Vec3 right = Vec3.directionFromRotation(0, bound.getYRot() + 90);
                ecg(c, o.add(0, 1.0, 0), right, 1.4F, clock * 0.05F, alpha, clamp(1 - size, 0, 1) * 0.8F);
            }
            case REGEN_CPR -> {
                Vec3 o = bound == null ? at : bound.getPosition(c.partial);
                float alpha = fade(t, life, 1, 6);
                Vec3 chest = o.add(0, Math.max(0.6, size * 0.6), 0);
                for (int i = 0; i < 3; i++) {
                    float k = clamp((t - i * 5) / 5, 0, 1);
                    if (k > 0 && k < 1) beat(c, chest, UP, 0.4F + 0.5F * k, k * 6, alpha * (1 - k));
                }
                Vec3 right = Vec3.directionFromRotation(0, (bound == null ? 0 : bound.getYRot()) + 90);
                // Flat at first, then the beat comes back.
                ecg(c, o.add(0, Math.max(1.6, size + 0.4), 0), right, 1.2F, t * 0.05F, alpha, clamp(1 - t / 12, 0, 1));
            }
            default -> { }
        }
    }

    /** Bloodletting: a fan of blood flung from the palm, arcing and falling, splashing where it lands. */
    private static void fan(Canvas c, Vec3 from, Vec3 dir, float t, int seed) {
        Vec3 d = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 side = d.cross(UP);
        side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
        float reach = (float) dir.length();
        int count = minimal() ? 9 : 18;
        float travel = clamp(t / 7, 0, 1);
        for (int i = 0; i < count; i++) {
            float spread = (i / (float) (count - 1) - 0.5F) * 1.2F + (hash(seed, i) - 0.5F) * 0.15F;
            float far = reach * (0.55F + 0.45F * hash(seed, i + 40));
            Vec3 aim = d.add(side.scale(spread)).normalize();
            float k = travel;
            Vec3 p = from.add(aim.scale(far * k)).add(0, 0.6 * k - 1.6 * k * k, 0);
            Vec3 tail = from.add(aim.scale(far * Math.max(0, k - 0.15F))).add(0, 0.6 * (k - 0.15) - 1.6 * (k - 0.15) * (k - 0.15), 0);
            float alpha = fade(t, 16, 0.5F, 6);
            if (c.soft()) c.line(tail, p, 0.06F, BLOOD, alpha * 0.5F, alpha);
            if (c.glow()) c.glow(p, 0.06F, BRIGHT, alpha * 0.5F);
            if (travel >= 1 && c.soft()) {
                // A splash where it lands.
                Vec3 land = from.add(aim.scale(far)).add(0, -1.0, 0);
                c.disc(land, UP, 0.15F + 0.1F * hash(seed, i + 70), CLOT, alpha * 0.7F, alpha * 0.2F);
            }
        }
    }

    // ------------------------------------------------------------------
    // Primitives
    // ------------------------------------------------------------------

    /** A ribbon of blood from one point to another, sagging by {@code sag} and wavering, with a wet glowing line along it. */
    static void strand(Canvas c, Vec3 from, Vec3 to, float phase, float width, float alpha, float sag) {
        if (alpha < 0.01F) return;
        int n = 24;
        Vec3[] pts = new Vec3[n + 1];
        Vec3 dir = to.subtract(from);
        Vec3 side = dir.cross(UP);
        side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n, bend = 4 * u * (1 - u);
            pts[i] = from.add(dir.scale(u)).add(0, -sag * bend, 0).add(side.scale(0.05 * bend * sin(u * 12 + phase * 6)));
        }
        // Each point fades near the camera, so a stream passing the viewer doesn't fill the screen.
        float[] w = new float[n + 1], wet = new float[n + 1], body = new float[n + 1], shine = new float[n + 1];
        Vec3 eye = c.camera();
        for (int i = 0; i <= n; i++) {
            float near = (float) clamp((pts[i].distanceTo(eye) - 1.2) / 2.4, 0, 1);
            w[i] = width; wet[i] = width * 0.3F; body[i] = alpha * near; shine[i] = alpha * 0.6F * near;
        }
        if (c.soft()) c.ribbon(pts, w, BLOOD, body);
        if (c.glow()) c.ribbon(pts, wet, BRIGHT, shine);
    }

    /** Beads of blood moving along a sagging line; {@code phase} slides them. */
    static void drops(Canvas c, Vec3 from, Vec3 to, float phase, int count, float size, float alpha, float sag) {
        if (alpha < 0.01F) return;
        Vec3 dir = to.subtract(from);
        for (int i = 0; i < count; i++) {
            float u = frac(phase + i / (float) count), bend = 4 * u * (1 - u);
            Vec3 p = from.add(dir.scale(u)).add(0, -sag * bend, 0);
            if (c.soft()) c.disc(p, c.camera().subtract(p), size, BLOOD, alpha, alpha * 0.5F);
            if (c.glow()) c.glow(p, size * 0.6F, BRIGHT, alpha * 0.4F);
        }
    }

    /** A dark, jagged split in the ground (soft pass, normal blending, so it stays dark). */
    static void gash(Canvas c, Vec3 from, Vec3 to, int seed, float width, float alpha) {
        if (!c.soft() || alpha < 0.01F) return;
        Vec3 d = to.subtract(from), side = d.cross(UP);
        side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 last = from;
        for (int i = 1; i <= 5; i++) {
            float u = i / 5F, kink = i == 5 ? 0 : (hash(seed, i) - 0.5F) * 0.35F * (float) d.length() / 2;
            Vec3 next = from.add(d.scale(u)).add(side.scale(kink));
            c.line(last, next, width * (1.2F - u * 0.7F), CLOT, alpha, alpha);
            last = next;
        }
    }

    /** A heartbeat ring across {@code axis} that jumps twice (lub-dub) as it spreads, crimson going to gold. */
    static void beat(Canvas c, Vec3 at, Vec3 axis, float radius, float t, float alpha) {
        if (alpha < 0.01F || t < 0) return;
        for (int i = 0; i < 2; i++) {
            float k = clamp((t - i * 3) / 15, 0, 1);
            if (k <= 0 || k >= 1) continue;
            float r = radius * (1 - (1 - k) * (1 - k));
            float[] col = mix(BRIGHT, LIFE, k);
            if (c.soft()) c.ring(at, axis, r, 0.16F, 0, TWO_PI, BLOOD, alpha * (1 - k) * 0.35F);
            if (c.glow()) {
                c.ring(at, axis, r, 0.07F, 0, TWO_PI, col, alpha * (1 - k) * 0.9F);
                c.ring(at, axis, r, 0.025F, 0, TWO_PI, CORE, alpha * (1 - k) * 0.6F);
            }
        }
    }

    /**
     * A heart-monitor trace across {@code right}, {@code width} long, scrolling with {@code phase}. {@code flat} from 0 (a
     * healthy beat) to 1 (a flatline) shrinks the spikes.
     */
    static void ecg(Canvas c, Vec3 center, Vec3 right, float width, float phase, float alpha, float flat) {
        if (alpha < 0.01F || !c.glow()) return;
        int n = 48;
        Vec3[] pts = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n, beat = frac(u * 2 - phase);
            float spike = beat < 0.08F ? sin(beat / 0.08F * PI) * 0.3F : beat < 0.14F ? -sin((beat - 0.08F) / 0.06F * PI) * 0.12F : 0;
            pts[i] = center.add(right.scale((u - 0.5) * width)).add(0, spike * (1 - flat), 0);
        }
        float[] col = mix(BRIGHT, LIFE, 1 - flat);
        c.ribbon(pts, 0.035F, col, alpha, alpha);
        c.ribbon(pts, 0.012F, CORE, alpha * 0.8F, alpha * 0.8F);
    }

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }

    /**
     * Paid in Full (the finisher, {@code regeneration/PaidInFull}): the blow she takes opens a gash across her chest that
     * closes from the edges in; a red ring runs down her arm to the fist as she swings, and the punch throws blood out of
     * the foe. Bound to her, the foe's entity id in the seed; origin where she stood, target the foe's feet; scale its
     * height.
     */
    /**
     * The alternate finishers ({@code regeneration/RegenFinishers}). No Pain: a gash opens at each of the three blows
     * (chest, shoulder, ribs) and closes from the edges in as she walks on, a heartbeat ring at each; then blood thrown
     * out of the foe at the haymaker. Grip: a gash across her forearm, closing while she holds the foe's arm, a warm
     * glow along it, and blood at the headbutt. Bound to her, the foe's entity id in the seed; scale the foe's height.
     */
    static void alternates(Canvas c, Active a, float t) {
        if (c.solid()) return;
        float h = Math.max(.6F, a.scale()), f = fade(t, a.piece().ticks, 1, 6);
        net.minecraft.world.entity.Entity her = a.bound(c), foe = c.level.getEntity(a.seed());
        if (!(her instanceof net.minecraft.world.entity.player.Player p)) return;
        Vec3 body = p.getPosition(c.partial);
        float yaw = rotLerp(c.partial, p.yBodyRotO, p.yBodyRot) * DEG_TO_RAD;
        Vec3 fwd = new Vec3(-sin(yaw), 0, cos(yaw)), side = new Vec3(cos(yaw), 0, sin(yaw));
        Vec3 foeChest = foe != null ? foe.getPosition(c.partial).add(0, h * .6, 0) : a.target().add(0, h * .6, 0);
        int land;
        if (a.piece() == com.jayptucker.heroesevolved.styles.Piece.FINISH_NO_PAIN) {
            int[] blows = com.jayptucker.heroesevolved.regeneration.RegenFinishers.NO_PAIN_BLOWS;
            land = com.jayptucker.heroesevolved.regeneration.RegenFinishers.NO_PAIN_LAND;
            float[][] spots = {{0, 1.15F, -.2F, .2F}, {.25F, 1.38F, .1F, -.1F}, {-.2F, .95F, .15F, .15F}};
            for (int i = 0; i < blows.length; i++) {
                float open = smooth(blows[i], blows[i] + 1, t) * (1 - smooth(blows[i] + 1, blows[i] + 5, t));
                if (open <= 0) continue;
                Vec3 at = body.add(side.scale(spots[i][0])).add(0, spots[i][1], 0).add(fwd.scale(.2));
                gash(c, at.add(side.scale(spots[i][2] * open)).add(0, spots[i][3] * open * .5, 0),
                        at.subtract(side.scale(spots[i][2] * open)).subtract(0, spots[i][3] * open * .5, 0), a.seed() + i, .05F * open, f);
                beat(c, at, fwd, .3F, t - blows[i], f * .7F);
            }
        } else {
            int hit = com.jayptucker.heroesevolved.regeneration.RegenFinishers.GRIP_HIT, hold = com.jayptucker.heroesevolved.regeneration.RegenFinishers.GRIP_CATCH;
            land = com.jayptucker.heroesevolved.regeneration.RegenFinishers.GRIP_LAND;
            Vec3 fist = com.jayptucker.heroesevolved.combat.StyleHits.hand(p, c.partial, true);
            Vec3 elbow = body.add(side.scale(p.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? .4 : -.4)).add(0, 1.1, 0);
            float open = smooth(hit, hit + 1, t) * (1 - smooth(hold + 1, hold + 8, t));
            if (open > 0) {
                Vec3 mid = elbow.lerp(fist, .5);
                Vec3 along = fist.subtract(elbow).normalize();
                gash(c, mid.subtract(along.scale(.2 * open)), mid.add(along.scale(.2 * open)), a.seed(), .045F * open, f);
            }
            // The healing glow running along the arm while it holds.
            if (t > hold && t < land && c.glow()) {
                float k = smooth(hold, hold + 7, t);
                c.ribbon(new Vec3[]{elbow, elbow.lerp(fist, k)}, .06F, LIFE, f * .5F, f * .7F);
                c.glow(fist, .2F, LIFE, f * .5F);
            }
            // The arm it holds: a thread of blood between the hands.
            if (t > hold && t < land + 1 && c.glow()) c.glow(foeChest.lerp(fist, .5), .12F, BRIGHT, f * .3F);
        }
        if (t >= land) {
            Vec3 out = foeChest.add(fwd.scale(1.3)).add(0, .3, 0);
            drops(c, foeChest, out, t - land, 9, .07F, f * (1 - smooth(land + 3, land + 10, t)), .5F);
            if (c.glow()) c.glow(foeChest, .8F * (1 - smooth(land, land + 3, t)), BRIGHT, f * (1 - smooth(land, land + 3, t)));
        }
    }

    static void finisher(Canvas c, Active a, float t) {
        float h = Math.max(.6F, a.scale()), life = a.piece().ticks, f = fade(t, life, 1, 6);
        net.minecraft.world.entity.Entity her = a.bound(c), foe = c.level.getEntity(a.seed());
        if (!(her instanceof net.minecraft.world.entity.player.Player p)) return;
        int take = com.jayptucker.heroesevolved.regeneration.PaidInFull.TAKE, close = com.jayptucker.heroesevolved.regeneration.PaidInFull.CLOSE;
        int punch = com.jayptucker.heroesevolved.regeneration.PaidInFull.PUNCH, land = com.jayptucker.heroesevolved.regeneration.PaidInFull.LAND;
        Vec3 body = p.getPosition(c.partial);
        float yaw = rotLerp(c.partial, p.yBodyRotO, p.yBodyRot) * DEG_TO_RAD;
        Vec3 fwd = new Vec3(-sin(yaw), 0, cos(yaw)), side = new Vec3(cos(yaw), 0, sin(yaw));
        // The wound, opened by the blow and closing from the edges in.
        if (t >= take && t < close + 3) {
            float open = smooth(take, take + 1, t) * (1 - smooth(take + 3, close + 2, t));
            Vec3 chest = body.add(0, 1.15, 0).add(fwd.scale(.2));
            gash(c, chest.add(side.scale(-.22 * open)).add(0, .15, 0), chest.add(side.scale(.22 * open)).add(0, -.1, 0), a.seed(), .05F * open, f);
        }
        // The ring of every healed wound running down the arm to the fist.
        if (t >= close && t < land + 1) {
            Vec3 shoulder = body.add(side.scale(p.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? .35 : -.35)).add(0, 1.38, 0);
            Vec3 fist = com.jayptucker.heroesevolved.combat.StyleHits.hand(p, c.partial, true);
            float k = smooth(close, land, t);
            Vec3 at = shoulder.lerp(fist, k);
            if (c.glow()) {
                c.ring(at, fist.subtract(shoulder).normalize(), .13F, .05F, 0, TWO_PI, BRIGHT, f * .8F);
                c.glow(at, .18F, BRIGHT, f * .5F);
            }
        }
        // The punch: blood thrown out of the foe.
        if (t >= land) {
            Vec3 chest = foe != null ? foe.getPosition(c.partial).add(0, h * .6, 0) : a.target().add(0, h * .6, 0);
            Vec3 out = chest.add(fwd.scale(1.4)).add(0, .3, 0);
            drops(c, chest, out, t - land, 9, .07F, f * (1 - smooth(land + 3, land + 10, t)), .5F);
            if (c.glow()) c.glow(chest, .8F * (1 - smooth(land, land + 3, t)), BRIGHT, f * (1 - smooth(land, land + 3, t)));
        }
    }

    private static final float[] BONE = rgb(0xEDE3D0);

    /** A point on a shell round {@code centre}, the same for every viewer. */
    private static Vec3 shellPoint(Vec3 centre, double radius, int seed, int i) {
        double a = hash(seed, i) * TWO_PI, y = hash(seed, i + 50) * 1.6 - .8;
        double r = Math.sqrt(Math.max(0, 1 - y * y * .6));
        return centre.add(Math.cos(a) * r * radius, y * radius * .7, Math.sin(a) * r * radius);
    }

    /**
     * What the older short {@code REGEN_*} effects draw now (the kind's ordinal in the seed's high bits), in blood that
     * moves the wrong way: drawn back into the body, flowing along a strand to a friend, pushed out of a wound; with a
     * heartbeat for every save and gold where life comes back.
     */
    static void fx(Canvas c, Active a, float t) {
        var kinds = com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.values();
        int ordinal = a.seed() >>> 16, seed = a.seed() & 0xFFFF;
        if (ordinal >= kinds.length) return;
        Vec3 o = a.origin(), to = a.target();
        float size = a.scale(), clock = c.level.getGameTime() % 2400 + c.partial;
        switch (kinds[ordinal]) {
            case REGENERATION -> {
                float alpha = fade(t, 12, 2, 6);
                Vec3 chest = o.add(0, 1.1, 0);
                for (int i = 0; i < (minimal() ? 2 : 4); i++) {
                    Vec3 from = shellPoint(chest, .9 * Math.max(.6, size), seed, i);
                    drops(c, from, chest, smooth(0, 10, t), 1, .06F, alpha, .1F);
                }
                if (c.glow()) c.glow(chest, .25F, LIFE, alpha * .35F);
            }
            case REGEN_MEND -> {
                // Blood rushes back in from all round, a gold seam closes up the body from the feet, bones click home.
                float alpha = fade(t, 24, 1, 8);
                Vec3 chest = o.add(0, 1.1, 0);
                int strands = minimal() ? 4 : 8;
                float pull = smooth(0, 9, t);
                for (int i = 0; i < strands; i++) {
                    Vec3 from = shellPoint(chest, 1.6, seed, i);
                    Vec3 head = from.lerp(chest, pull);
                    strand(c, from.lerp(chest, Math.max(0, pull - .45F)), head, clock * .4F + i, .045F, alpha * (1 - smooth(9, 13, t)), .15F);
                    drops(c, from, chest, pull, 1, .07F, alpha * (1 - smooth(9, 13, t)), .15F);
                }
                float rise = smooth(4, 16, t);
                if (c.glow() && t > 4) {
                    c.ring(o.add(0, .1 + 1.8 * rise, 0), UP, .5F - .1F * rise, .05F, 0, TWO_PI, LIFE, alpha * .8F * (1 - smooth(14, 20, t)));
                    c.ring(o.add(0, .1 + 1.8 * rise, 0), UP, .5F - .1F * rise, .015F, 0, TWO_PI, CORE, alpha * (1 - smooth(14, 20, t)));
                    for (int i = 0; i < 3; i++) {
                        float at = 5 + i * 3;
                        if (t > at && t < at + 3) c.glow(o.add((hash(seed, i + 9) - .5) * .5, .3 + 1.3 * hash(seed, i + 19), (hash(seed, i + 29) - .5) * .5),
                                .2F, BONE, alpha * (1 - (t - at) / 3));
                    }
                    c.glow(chest, .7F * (1 - smooth(9, 14, t)) * smooth(7, 9, t), BRIGHT, alpha * .7F);
                }
                beat(c, o.add(0, .06, 0), UP, .8F + Math.max(0, size) * .3F, t - 8, alpha);
            }
            case BLOOD_THREAD, REGEN_BOND -> {
                boolean drawnIn = kinds[ordinal] == com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.REGEN_BOND;
                float alpha = fade(t, 16, 1, 6), flow = smooth(0, 8, t);
                Vec3 from = drawnIn ? to : o, end = drawnIn ? o : to;
                strand(c, from, from.lerp(end, flow), clock * .4F, .05F + .03F * Math.min(2, size), alpha * .8F, .35F);
                drops(c, from, end, flow, minimal() ? 2 : 4, .07F, alpha, .35F);
                if (t > 6) {
                    if (c.glow()) c.glow(end, .45F * (1 - smooth(10, 16, t)), drawnIn ? BRIGHT : LIFE, alpha * .7F);
                    beat(c, end, UP, .45F, t - 6, alpha * .8F);
                }
            }
            case HEALING_AURA -> {
                float alpha = fade(t, 20, 2, 8), r = Math.max(1, size);
                beat(c, o.add(0, .08, 0), UP, r, t, alpha);
                if (c.glow()) {
                    c.ring(o.add(0, .12, 0), UP, r * .4F, .03F, 0, TWO_PI, LIFE, alpha * .6F);
                    for (int i = 0; i < (minimal() ? 4 : 10); i++) {
                        double ang = hash(seed, i) * TWO_PI, rr = Math.sqrt(hash(seed, i + 40)) * r;
                        float up = ((t * .05F) + hash(seed, i + 80)) % 1;
                        c.glow(o.add(Math.cos(ang) * rr, .2 + 1.6 * up, Math.sin(ang) * rr), .07F, LIFE, alpha * .7F * sin(up * PI));
                    }
                }
            }
            case REGEN_EJECT -> {
                Vec3 out = to.subtract(o);
                fan(c, o, out.lengthSqr() < 1e-4 ? UP : out.normalize().scale(.6), t, seed);
                if (c.glow() && t < 4) c.glow(o, .35F, CORE, 1 - t / 4);
            }
            case REGEN_RECKLESS -> {
                float alpha = fade(t, 12, .5F, 6);
                Vec3 axis = o.subtract(to);
                axis = axis.lengthSqr() < 1e-4 ? UP : axis.normalize();
                Vec3 side = (Math.abs(axis.y) > .9 ? new Vec3(1, 0, 0) : UP).cross(axis).normalize(), up = side.cross(axis).normalize();
                if (c.soft()) for (int i = 0; i < 4; i++) {
                    double ang = i * Math.PI / 2 + hash(seed, i) * .6;
                    Vec3 d = side.scale(Math.cos(ang)).add(up.scale(Math.sin(ang)));
                    gash(c, o, o.add(d.scale(.35 + .2 * hash(seed, i + 4))), seed + i, .04F, alpha);
                }
                beat(c, o, axis, .45F, t, alpha * .8F);
                if (c.glow() && t < 3) c.glow(o, .5F, CORE, alpha * (1 - t / 3));
            }
            case REGEN_SECOND_LIFE -> {
                float alpha = fade(t, 30, 2, 10), h = Math.max(1, size), climb = smooth(0, 14, t);
                if (c.glow()) c.helix(o, h * climb, .55F, .45F, 2, clock * .05F, .03F, LIFE, alpha * .8F, 28);
                if (c.soft()) c.helix(o.add(0, .05, 0), h * climb, .5F, .4F, 2, clock * .05F + PI, .05F, BLOOD, alpha * .7F, 28);
                Vec3 chest = o.add(0, h * .6, 0);
                beat(c, chest, UP, .7F, t - 10, alpha);
                beat(c, chest, UP, .95F, t - 18, alpha * .8F);
                if (t > 12) ecg(c, o.add(0, h + .5, 0), (Math.abs(to.subtract(o).x) > 1e-3 ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1)), .9F,
                        clock * .1F, alpha * smooth(12, 16, t), 1 - smooth(16, 22, t));
            }
            case REGEN_REVIVE -> {
                float alpha = fade(t, 30, .5F, 10);
                Vec3 chest = o.add(0, Math.max(.6, size * .6), 0);
                if (c.glow() && t < 6) { c.glow(chest, 1.4F * (1 - t / 6), BRIGHT, alpha); c.glow(chest, .6F * (1 - t / 6), CORE, alpha); }
                for (int i = 0; i < (minimal() ? 4 : 9); i++)
                    drops(c, shellPoint(chest, 2.2, seed, i), chest, smooth(0, 10, t), 1, .08F, alpha * (1 - smooth(10, 14, t)), .1F);
                for (int k = 0; k < 3; k++) beat(c, o.add(0, .08, 0), UP, 1.2F + k * 1.4F, t - k * 5, alpha * (1 - k * .2F));
            }
            case REGEN_SEVER -> {
                Vec3 out = to.subtract(o);
                fan(c, o, out.lengthSqr() < 1e-4 ? UP : out.normalize(), t, seed);
                if (c.glow() && t < 6) c.glow(o, .3F, BONE, 1 - t / 6);
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ Last One Standing (the signature)

    /**
     * Claire, standing her ground: her heart beats out across the ground in crimson rings a little harder each second, a
     * ring of dried blood marks how far she is drawing them from, and fine cracks open and close over her body as the
     * wounds land and heal. Re-sent while it lasts; the seed's low bits count the seconds so far.
     */
    static void stand(Canvas c, Active a, float t) {
        Entity e = a.bound(c);
        Vec3 feet = e != null ? e.getPosition(c.partial) : a.origin();
        float alpha = fade(t, a.piece().ticks, 2, 2), r = Math.max(2, a.scale());
        float clock = c.level.getGameTime() % 2400 + c.partial, beatT = clock % 20;
        int second = a.seed() & 0xFF;
        float hard = Math.min(1, .4F + second * .12F);
        beat(c, feet.add(0, .06, 0), UP, 1.4F + 2.2F * hard, beatT, alpha * hard);
        if (c.soft()) c.ring(feet.add(0, .05, 0), UP, r, .18F, 0, TWO_PI, CLOT, alpha * .35F);
        if (c.glow()) c.ring(feet.add(0, .07, 0), UP, r, .03F, 0, TWO_PI, BRIGHT, alpha * .45F);
        float h = e != null ? e.getBbHeight() : 1.8F;
        if (c.soft()) for (int i = 0; i < 5; i++) {
            float open = sin((clock * .15F + hash(a.entity(), i) * 6.28F)) * .5F + .5F;
            double ang = hash(a.entity(), i + 10) * TWO_PI, y = .3 + h * .6 * hash(a.entity(), i + 20);
            Vec3 at = feet.add(Math.cos(ang) * .33, y, Math.sin(ang) * .33);
            gash(c, at, at.add(0, .25 * open, 0).add(Math.cos(ang) * .02, 0, Math.sin(ang) * .02), i, .025F, alpha * open);
        }
    }

    /** A foe drawn to her: a sagging cord of blood from its head to her chest, pulsing with her heart. */
    static void taunt(Canvas c, Active a, float t) {
        Entity foe = a.bound(c), her = c.level.getEntity(a.seed());
        if (foe == null || her == null) return;
        float alpha = fade(t, a.piece().ticks, 2, 2);
        float clock = c.level.getGameTime() % 2400 + c.partial;
        float beatK = Math.max(0, 1 - (clock % 20) / 4);
        Vec3 from = foe.getPosition(c.partial).add(0, foe.getBbHeight() * .85, 0);
        Vec3 chest = her.getPosition(c.partial).add(0, her.getBbHeight() * .62, 0);
        strand(c, from, chest, clock * .3F, .03F + .02F * beatK, alpha * .7F, .5F);
        if (c.glow()) c.glow(from, .14F + .1F * beatK, BRIGHT, alpha * .6F);
    }

    /** The banked wounds let go: a flash, one great lub-dub racing out over the ground, cracks and a ring of spray. */
    static void release(Canvas c, Active a, float t) {
        Vec3 feet = a.origin();
        float r = Math.max(2, a.scale()), life = a.piece().ticks, alpha = fade(t, life, .5F, 10);
        if (c.glow() && t < 5) { c.glow(feet.add(0, 1.1, 0), 2.2F * (1 - t / 5), BRIGHT, alpha); c.glow(feet.add(0, 1.1, 0), .9F * (1 - t / 5), CORE, alpha); }
        beat(c, feet.add(0, .06, 0), UP, r, t, alpha);
        float open = 1 - (float) Math.pow(1 - clamp(t / 7, 0, 1), 3);
        if (c.soft()) for (int i = 0; i < (minimal() ? 8 : 16); i++) {
            float ang = i * TWO_PI / 16 + hash(a.seed(), i) * .3F, reach = r * (.4F + .5F * hash(a.seed(), i + 30)) * open;
            Vec3 d = new Vec3(cos(ang), 0, sin(ang));
            gash(c, feet.add(0, .07, 0).add(d.scale(.4)), feet.add(0, .07, 0).add(d.scale(.4 + reach)), a.seed() + i, .07F, alpha * (1 - smooth(12, life, t)));
        }
        for (int i = 0; i < 6; i++) {
            float ang = i * TWO_PI / 6 + .3F;
            fan(c, feet.add(0, 1, 0), new Vec3(cos(ang), .35, sin(ang)), t, a.seed() + i);
        }
    }
}
