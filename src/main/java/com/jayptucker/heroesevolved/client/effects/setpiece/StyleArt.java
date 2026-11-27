package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/**
 * The fighting styles' own pieces, each in its power's language. Force Palm is drawn with {@link ForceArt}'s lens and
 * pane (the field colour, a lattice of facets, a bright rim) so a punch through a lens is plainly the same power as the
 * shield; the Ice Skater's strip is frost laid flat on the ground, glittering as it lingers. Most other styles need no
 * pieces of their own: they speak in their power's existing strokes (see {@code combat/ComboStyle}).
 */
final class StyleArt {

    private StyleArt() {}

    static void draw(Canvas c, Active a, float t) {
        if (c.solid()) return;
        float alpha = fade(t, a.piece().ticks, 1, Math.min(8, a.piece().ticks * .45F));
        Vec3 at = a.origin(), end = a.target();
        Player owner = a.bound(c) instanceof Player p ? p : null;
        switch (a.piece()) {
            case STYLE_LENS_JAB -> lensJab(c, at, end, a.scale(), t, alpha);
            case STYLE_SHARDS -> shard(c, at, end, t, alpha, a.seed());
            case STYLE_FORCE_BURST -> burst(c, at, end, a.scale(), t, alpha, a.seed());
            case STYLE_FIELD_SLAM -> slam(c, at, a.scale(), t, alpha);
            case STYLE_LENS_SLIDE -> slide(c, owner, at, end, t, alpha);
            case STYLE_BOUNCE -> bounce(c, at, end, t, alpha);
            default -> { }
        }
    }

    /**
     * A jab through a lens: a small lens forms on the knuckles, flies out with the blow and flattens against what it
     * hits, its facets flashing as it cracks. A miss leaves it fading where the blow ran out.
     */
    private static void lensJab(Canvas c, Vec3 fist, Vec3 end, float size, float t, float alpha) {
        Vec3 path = end.subtract(fist);
        if (path.lengthSqr() < .01) return;
        Vec3 aim = path.normalize();
        float travel = smooth(0, 2.5F, t), hit = smooth(2, 4, t);
        Vec3 centre = fist.lerp(end.subtract(aim.scale(.25)), travel);
        float radius = (.22F + .18F * hit) * (size > .8F ? 1 : .8F);
        ForceArt.lens(c, centre, aim, radius, .05F - .08F * hit, t * .2F, alpha, size > .8F ? hit : 0);
        if (c.glow()) {
            c.line(fist, centre, .03F, ForceArt.FIELD, 0, alpha * .4F * (1 - hit));
            if (size > .8F) c.glow(end, .55F * hit * (1 - smooth(5, 9, t)), ForceArt.EDGE, alpha * .6F);
        }
    }

    /** One shard of released pressure: a tumbling triangle of field flying from the fist to its end. */
    private static void shard(Canvas c, Vec3 fist, Vec3 end, float t, float alpha, int seed) {
        Vec3 path = end.subtract(fist);
        if (path.lengthSqr() < .01 || !c.glow()) return;
        Vec3 aim = path.normalize(), side = side(aim), up = aim.cross(side);
        float travel = smooth(0, 4, t);
        Vec3 at = fist.lerp(end, travel);
        float spin = t * 1.3F + hash(seed, 1) * TWO_PI;
        Vec3 a = at.add(aim.scale(.28)), b = at.add(side.scale(cos(spin) * .14)).add(up.scale(sin(spin) * .14)),
                d = at.add(side.scale(cos(spin + 2.1F) * .14)).add(up.scale(sin(spin + 2.1F) * .14));
        float k = alpha * (1 - smooth(4, 7, t));
        c.tri(a, b, d, ForceArt.FIELD, k * .6F, k * .6F, k * .6F);
        c.line(a, b, .02F, ForceArt.EDGE, k, k);
        c.line(b, d, .02F, ForceArt.EDGE, k, k);
        c.line(d, a, .02F, ForceArt.EDGE, k, k);
        c.line(at.subtract(aim.scale(.9)), at, .025F, ForceArt.SHEEN, 0, k * .5F);
        if (t > 3.5F) c.glow(end, .3F * (1 - smooth(4, 8, t)), ForceArt.EDGE, alpha * .7F);
    }

    /** Force Burst: five lenses fanned from the palms race out across the cone and widen as they go. */
    private static void burst(Canvas c, Vec3 chest, Vec3 end, float reach, float t, float alpha, int seed) {
        Vec3 forward = flat(end.subtract(chest)), right = new Vec3(-forward.z, 0, forward.x);
        float open = smooth(0, 6, t), k = alpha * (1 - smooth(7, 14, t));
        for (int i = 0; i < 5; i++) {
            float angle = (i - 2) * .32F;
            Vec3 dir = forward.scale(cos(angle)).add(right.scale(sin(angle)));
            Vec3 centre = chest.add(dir.scale(.5 + reach * .85 * open)).add(0, (hash(seed, i) - .5F) * .3F, 0);
            ForceArt.lens(c, centre, dir, .35F + .75F * open, -.1F * open, t * .15F + i, k * (1 - .3F * Math.abs(i - 2) / 2), 1 - open);
        }
        if (c.glow()) c.glow(chest.add(forward.scale(.4)), 1.1F * (1 - smooth(0, 5, t)), ForceArt.EDGE, alpha * .7F);
    }

    /** Field Slam: a dome of force snaps out from the palm along the ground, its rim racing to the edge. */
    private static void slam(Canvas c, Vec3 feet, float radius, float t, float alpha) {
        float open = 1 - (float) Math.pow(1 - clamp(t / 7, 0, 1), 3), k = alpha * (1 - smooth(8, 16, t));
        float r = .4F + (radius - .4F) * open;
        if (c.glow()) {
            c.shell(feet, r, r * .55F, r, HALF_PI, ForceArt.FIELD, k * .35F, .05F, Canvas.PLAIN, t * .05F);
            ForceArt.cage(c, feet, r * .98F, t * .08F, UP, k * .4F, 0, ForceArt.EDGE);
            c.ring(feet.add(0, .05, 0), UP, r, .07F, 0, TWO_PI, ForceArt.EDGE, k);
            c.ring(feet.add(0, .05, 0), UP, r, .2F, 0, TWO_PI, ForceArt.DEEP, k * .4F);
        }
    }

    /** Shield Slide: a pane rides along on the side the body slides from, its rim lit. */
    private static void slide(Canvas c, Player owner, Vec3 from, Vec3 to, float t, float alpha) {
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < .01) return;
        dir = flat(dir);
        Vec3 feet = owner != null ? owner.getPosition(c.partial) : from.lerp(to, smooth(0, 5, t));
        // The pane stands behind the slide (where the blow came from), facing back along it.
        Vec3 normal = dir.scale(-1), across = new Vec3(-normal.z, 0, normal.x);
        Vec3 corner = feet.add(normal.scale(.7)).subtract(across.scale(.8)).add(0, .1, 0);
        ForceArt.pane(c, corner, across.scale(1.6), new Vec3(0, 2, 0), alpha * (1 - smooth(6, 12, t)), .35F, true);
    }

    /** Bounce: a pane under the feet bows down and flings the body up and back, then fades with a rim of light. */
    private static void bounce(Canvas c, Vec3 feet, Vec3 back, float t, float alpha) {
        float bow = sin(PI * clamp(t / 4, 0, 1));
        Vec3 centre = feet.add(0, .08 - .25 * bow, 0);
        ForceArt.lens(c, centre, UP, 1.1F, -.35F * bow, t * .1F, alpha * (1 - smooth(6, 14, t)), bow);
        if (c.glow()) c.ring(feet.add(0, .06, 0), UP, 1.1F + .9F * smooth(2, 10, t), .05F, 0, TWO_PI, ForceArt.EDGE,
                alpha * (1 - smooth(4, 12, t)));
    }

    private static Vec3 flat(Vec3 v) {
        Vec3 level = new Vec3(v.x, 0, v.z);
        return level.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : level.normalize();
    }

    private static Vec3 side(Vec3 axis) { return (Math.abs(axis.y) > .93 ? new Vec3(0, 0, 1) : UP).cross(axis).normalize(); }

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }
}
