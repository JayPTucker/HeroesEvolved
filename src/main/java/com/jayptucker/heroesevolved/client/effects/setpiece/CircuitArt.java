package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.TWO_PI;

/**
 * Technopathy's finisher drawn (Hard Reset, {@code technopathy/HardReset}): copper current. A spark runs node to node
 * along the circuit (a piece of scale {@code HardReset.SPARK} each, origin the last node, target the next), each node
 * lighting as it is reached; then a line pulls taut from the last node through the foe and snaps bright as it goes down.
 * Palette: {@code 6B3A1E} shadow, {@code E0894A} copper, {@code FFD7A8} hot copper, {@code FFF6E8} core.
 */
final class CircuitArt {
    private static final float[] SHADOW = rgb(0x6B3A1E), COPPER = rgb(0xE0894A), HOT = rgb(0xFFD7A8), CORE = rgb(0xFFF6E8);

    private CircuitArt() {}

    static void draw(Canvas c, Active a, float t) {
        float life = a.piece().ticks;
        if (a.scale() < .5F) {
            // A spark along one wire, and the node it reaches lighting up.
            float run = smooth(0, 2, t), f = fade(t, life, 0, 8);
            Vec3 head = a.origin().lerp(a.target(), run);
            line(c, a.origin(), head, .035F, f);
            if (c.glow()) {
                c.glow(a.target(), .35F * smooth(1.5F, 2.5F, t) * (1 - smooth(4, 12, t)), HOT, f);
                c.glow(head, .08F, CORE, f);
            }
            return;
        }
        // The line through the foe: pulled taut, then a bright snap as it lands.
        Entity foe = a.bound(c);
        Vec3 to = foe != null && foe.isAlive() ? foe.getBoundingBox().getCenter() : a.target();
        float f = fade(t, life, 1, 6), taut = smooth(0, 2, t);
        Vec3 end = a.origin().lerp(to, taut);
        line(c, a.origin(), end, .05F, f);
        float snap = smooth(3, 4, t) * (1 - smooth(4, 9, t));
        if (snap > 0 && c.glow()) {
            c.glow(to, .9F * snap, HOT, f * snap);
            c.glow(to, .3F * snap, CORE, f);
        }
    }

    /**
     * The alternate finishers ({@code technopathy/TechnoFinishers}). Kill Switch: right-angled copper traces climbing
     * the foe's body from the feet with a node at every turn, then going dark node by node from the top after the flick,
     * and a last spark dying at the heart. Overclock: copper rings orbiting the body, faster and faster, sparks spitting
     * off them, then a burst of sparks and a puff of smoke. Bound to the foe; origin its feet, target yours, scale its height.
     */
    static void alternates(Canvas c, Active a, float t) {
        if (c.solid()) return;
        float h = Math.max(.6F, a.scale()), life = a.piece().ticks, f = fade(t, life, 1, 6);
        Entity foe = a.bound(c);
        Vec3 feet = foe != null && foe.isAlive() ? foe.getPosition(c.partial) : a.origin();
        Vec3 toMe = a.target().subtract(feet).multiply(1, 0, 1);
        toMe = toMe.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : toMe.normalize();
        Vec3 across = new Vec3(-toMe.z, 0, toMe.x);
        float w = Math.max(.25F, (foe != null ? foe.getBbWidth() : .6F) * .5F);
        int seed = a.seed();
        if (a.piece() == com.jayptucker.heroesevolved.styles.Piece.FINISH_KILL_SWITCH) {
            int traced = com.jayptucker.heroesevolved.technopathy.TechnoFinishers.SWITCH_TRACED;
            int flick = com.jayptucker.heroesevolved.technopathy.TechnoFinishers.SWITCH_FLICK;
            int land = com.jayptucker.heroesevolved.technopathy.TechnoFinishers.SWITCH_LAND;
            // Traces up all four sides of the body, each a run of right angles: up, across, up.
            int steps = 8;
            for (int lane = 0; lane < 4; lane++) {
                Vec3 out = lane == 0 ? toMe : lane == 1 ? across : lane == 2 ? toMe.scale(-1) : across.scale(-1);
                Vec3 face = feet.add(out.scale(w + .04));
                Vec3 side = new Vec3(-out.z, 0, out.x);
                float x = (lane % 2 == 0 ? -.35F : .35F) * w;
                Vec3 prev = face.add(side.scale(x)).add(0, .05, 0);
                for (int i = 1; i <= steps; i++) {
                    float u = i / (float) steps;
                    float shown = smooth(u * traced - 1, u * traced, t);
                    if (shown <= 0) break;
                    // Dark from the top down once the switch is flicked.
                    float dark = smooth(flick + (1 - u) * (land - flick - 1), flick + (1 - u) * (land - flick - 1) + 1, t);
                    float jog = (hash(seed + lane, i) - .5F) * w * .9F;
                    Vec3 up = new Vec3(prev.x, feet.y + h * .92 * u, prev.z);
                    Vec3 node = face.add(side.scale(Math.max(-w * .9F, Math.min(w * .9F, x + jog)))).add(0, h * .92 * u, 0);
                    float alpha = f * (1 - dark * .85F);
                    line(c, prev, prev.lerp(up, shown), .045F, alpha);
                    if (shown >= 1) line(c, up, node, .045F, alpha);
                    if (c.glow() && shown >= 1) {
                        c.glow(node, .14F, dark > .5F ? SHADOW : HOT, alpha);
                        c.glow(node, .05F, CORE, alpha * (1 - dark));
                    }
                    prev = shown >= 1 ? node : prev;
                }
            }
            // The whole body hums while the circuit is live, and goes dark with it.
            Vec3 heart = feet.add(0, h * .6, 0);
            if (c.glow() && t < land + 4) {
                float live = smooth(2, traced, t) * (1 - smooth(flick, land, t));
                c.glow(heart, .5F + h * .3F, COPPER, f * .3F * live);
                float last = t < land ? 1 : 1 - smooth(land, land + 4, t);
                c.glow(heart, .15F + .15F * (1 - smooth(flick, land, t)), CORE, f * last);
            }
            if (t >= land && c.soft()) c.disc(heart, toMe, .7F * (1 - smooth(land + 2, land + 8, t)), SHADOW, f * .5F, 0);
            return;
        }
        // Overclock.
        int land = com.jayptucker.heroesevolved.technopathy.TechnoFinishers.CLOCK_LAND;
        float r = w + .25F;
        if (t < land) {
            float speed = .3F + 1.6F * smooth(0, land, t);
            float spin = t * speed + t * t * .02F;
            for (int i = 0; i < 3; i++) {
                Vec3 at = feet.add(0, h * (.2 + .3 * i), 0);
                float start = spin * (i % 2 == 0 ? 1 : -1) + i * 2.1F;
                if (c.soft()) c.ring(at, UP, r, .08F, start, start + 4F, SHADOW, f * .3F);
                if (c.glow()) {
                    c.ring(at, UP, r, .05F, start, start + 4F, COPPER, f * .5F);
                    c.ring(at, UP, r, .018F, start, start + 4F, CORE, f);
                    c.glow(at.add(Math.cos(start + 4) * r, 0, Math.sin(start + 4) * r), .1F, HOT, f);
                }
            }
            // Sparks spitting off as it speeds up.
            if (c.glow()) for (int i = 0; i < (int) (smooth(4, land, t) * 8); i++) {
                int s = seed + (int) (t * 2) * 13 + i;
                float ang = hash(s, 1) * TWO_PI;
                Vec3 from = feet.add(Math.cos(ang) * r, h * hash(s, 2), Math.sin(ang) * r);
                Vec3 to = from.add(Math.cos(ang) * .35, .15 + .2 * hash(s, 3), Math.sin(ang) * .35);
                c.line(from, to, .02F, HOT, f, 0);
            }
        } else {
            float k = t - land;
            if (c.glow()) {
                c.glow(feet.add(0, h * .5, 0), 1.3F * (1 - smooth(land, land + 3, t)), CORE, f * (1 - smooth(land, land + 3, t)));
                for (int i = 0; i < 16; i++) {
                    float ang = hash(seed, i) * TWO_PI, up = hash(seed, i + 20);
                    Vec3 dir = new Vec3(Math.cos(ang), .3 + up, Math.sin(ang));
                    Vec3 at = feet.add(0, h * .5, 0).add(dir.scale(k * .2)).add(0, -.01 * k * k, 0);
                    c.line(at, at.subtract(dir.scale(.12)), .025F, HOT, f * (1 - smooth(land + 3, land + 10, t)), 0);
                }
            }
            if (c.soft() && k < 10) for (int i = 0; i < 2; i++) {
                float s = .3F + k * .04F;
                c.cloud(feet.add(0, h * .6 + k * .05, 0), s, s * 1.2F, s, seed + i, k * .05F, f * .2F * (1 - k / 10),
                        Shades.smoke(c, SHADOW, 0));
            }
        }
    }

    /** A three-layer copper line: a dark backing, the copper body and a white-hot core. */
    private static void line(Canvas c, Vec3 a, Vec3 b, float width, float alpha) {
        if (alpha < .01F || a.distanceToSqr(b) < 1e-4) return;
        if (c.soft()) c.line(a, b, width * 2.6F, SHADOW, alpha * .35F, alpha * .35F);
        if (!c.glow()) return;
        c.line(a, b, width * 2F, COPPER, alpha * .35F, alpha * .35F);
        c.line(a, b, width, HOT, alpha * .8F, alpha * .8F);
        c.line(a, b, width * .35F, CORE, alpha, alpha);
    }
}
