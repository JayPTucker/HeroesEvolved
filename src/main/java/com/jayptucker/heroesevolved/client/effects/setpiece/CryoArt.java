package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.client.effects.intro.CloudMesh;
import com.jayptucker.heroesevolved.cryo.CryoBlastGeometry;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.jayptucker.heroesevolved.cryo.FrostConstructBlock;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/**
 * Cryokinesis, drawn in one place with one palette: <b>cold made visible</b>. Frost creeps over what it touches in a
 * front of rime and feathered veins, ice grows in faceted shards, mist rolls off both palms, and the only warm colour is
 * heat being drawn out (Last Warmth, a siphoned reactor, Absolute Zero). Solid ice that stands in the world keeps the
 * ice block's own texture ({@code CryoSkillVisuals}, {@code CryoMeshVisuals}, {@code FrozenVisuals}), so a construct
 * and a placed ice block match; everything else is here.
 *
 * <p>Palette: {@code 2E5470} deep (the shadow side of ice, cracks and seams, soft pass), {@code 5AA8E8} glacier (halo,
 * body of cold light), {@code A6D8EE} ice, {@code DDF1F8} rime (frost, mist, lit faces), {@code F6FCFF} core (glints),
 * and {@code FF9A3C}/{@code FFE0A0} warmth.</p>
 */
final class CryoArt {
    static final float[] DEEP = Canvas.rgb(0x2E5470), GLACIER = Canvas.rgb(0x5AA8E8), ICE = Canvas.rgb(0xA6D8EE),
            RIME = Canvas.rgb(0xDDF1F8), CORE = Canvas.rgb(0xF6FCFF), WARM = Canvas.rgb(0xFF9A3C), WARM_CORE = Canvas.rgb(0xFFE0A0);
    /** The cloud shading runs from this to rime. */
    private static final float[] SHADOW = Canvas.mix(DEEP, ICE, .55F);
    private static final float[] MIST = RIME, WHITE = CORE, HEAT = WARM;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private CryoArt() {}

    static void draw(Canvas c, Active a, float t) {
        if (c.solid()) return;
        if (a.piece() == Piece.CRYO_TERRAIN_FROST) {
            if (c.soft()) terrainFrost(c, a, t);
            return;
        }
        if (a.piece() == Piece.CRYO_QUENCH) {
            quench(c, a, t);
            return;
        }
        var mc = Minecraft.getInstance();
        boolean minimal = mc.options.particles().get() == ParticleStatus.MINIMAL;
        Player player = a.bound(c) instanceof Player caster ? caster : null;
        Vec3 end = a.target();
        float now = c.level.getGameTime() % 2400 + c.partial;
        boolean cast = a.piece() == Piece.CRYO_CAST;
        float alpha = cast ? Canvas.fade(t, a.piece().ticks, 2, 6)
                : .76F * (1 - Canvas.smooth(2, a.piece().ticks, t)) * Canvas.smooth(0, 2, t);
        boolean siphon = !cast && a.scale() > 1.5F;
        if (alpha < .01F || c.glow() && !siphon) return;
        Vec3 right = player == null ? Vec3.ZERO : Vec3.directionFromRotation(0, player.getViewYRot(c.partial) + 90);
        Vec3 aim = player == null ? end.subtract(a.origin()).normalize() : player.getViewVector(c.partial);
        boolean firstPerson = player != null && player == mc.player && mc.options.getCameraType().isFirstPerson();
        float flight = cast ? (float) Math.clamp(a.origin().distanceTo(end) / 3, 2, 6) : 0;
        float front = cast ? Canvas.smooth(0, flight, t) : 1;
        float tail = cast ? Canvas.smooth(6, a.piece().ticks, t) : 0;
        for (int side = -1; side <= 1; side += 2) {
            Vec3 start = a.origin().add(aim.scale(.6)).add(0, -.2, 0);
            if (firstPerson) start = player.getEyePosition(c.partial).add(aim.scale(.61))
                    .add(right.scale(side * .40)).subtract(right.cross(aim).scale(.33));
            else if (player != null) {
                Vec3 shoulder = CryoBlastGeometry.shoulder(player, c.partial, side);
                start = cast ? shoulder.add(end.subtract(shoulder).normalize().scale(CryoBlastGeometry.ARM_REACH))
                        : CryoBlastGeometry.palm(player, c.partial, side);
            }
            Vec3 lead = start.lerp(end, front);
            Vec3 trail = start.lerp(end, tail);
            if (trail.distanceToSqr(lead) > (cast ? .04 : .12))
                stream(c, trail, lead, side, now, alpha, minimal, siphon, a.entity(), cast ? a.scale() : 1);
        }
        if (c.soft() && !minimal && (cast ? t >= flight : a.scale() >= 1)) {
            float spread = cast ? a.scale() : 1;
            float impact = cast ? Canvas.smooth(flight, flight + 2, t) : 1;
            Vec3 landing = cast ? end : end.subtract(aim.scale(.15));
            c.cloud(landing, .44F * spread, .37F * spread, .44F * spread, 2, now * .19F,
                    (cast ? .42F : .28F) * alpha * impact, frost(c));
        }
    }

    private static void terrainFrost(Canvas c, Active a, float age) {
        BlockPos pos = BlockPos.containing(a.origin());
        if (!c.level.hasChunkAt(pos)) return;
        var state = c.level.getBlockState(pos);
        if (state.isAir()) return;
        Vec3 normal = a.target().subtract(a.origin()).normalize();
        Vec3 right = Math.abs(normal.y) > .5 ? new Vec3(1, 0, 0) : normal.cross(UP);
        Vec3 up = normal.cross(right);
        Vec3 center = a.origin().add(normal.scale(.504));
        if (normal.y > .5 && (state.is(Blocks.WATER) || state.is(Blocks.LAVA))) {
            double height = state.getFluidState().getHeight(c.level, pos);
            center = center.add(0, height - 1, 0);
        }

        // The rime starts as a small star and spreads until, just before the block turns, it frosts the whole face.
        float growth = Math.max(0, a.scale() - .12F * (1 - Canvas.smooth(0, 5, age)));
        float alpha = (0.4F + .45F * growth) * (1 - Canvas.smooth(58, a.piece().ticks, age));
        float radius = .74F * (float) Math.sqrt(growth);
        int seed = Long.hashCode(pos.asLong());
        boolean minimal = Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL;
        float rim = alpha * (.38F + .4F * growth);
        for (int i = 0; i < 8; i++) {
            Vec3 left = frostEdge(center, right, up, radius, seed, i);
            Vec3 edge = frostEdge(center, right, up, radius, seed, (i + 1) % 8);
            float[] tint = Canvas.mix(ICE, MIST, Canvas.hash(seed, i + 8));
            c.tri(center, left, edge, tint, alpha, rim, rim);
        }
        if (minimal || growth < .12F) return;
        // Frost feathers: white veins from the impact, each with barbs swept back toward it.
        for (int i = 0; i < 6; i++) {
            double angle = i * Math.PI / 3 + (Canvas.hash(seed, i + 32) - .5) * .7;
            double reach = radius * (.75 + .3 * Canvas.hash(seed, i + 40));
            Vec3 tip = frostPoint(center, right, up, Math.cos(angle) * reach, Math.sin(angle) * reach);
            c.line(center, tip, .014F, WHITE, alpha * .7F, alpha * .15F);
            for (int b = 1; b <= 3; b++) {
                double along = reach * b / 4, barb = reach * (.22 - .04 * b);
                for (int side = -1; side <= 1; side += 2) {
                    double branch = angle + side * .75;
                    Vec3 root = frostPoint(center, right, up, Math.cos(angle) * along, Math.sin(angle) * along);
                    Vec3 end = frostPoint(center, right, up, Math.cos(angle) * along + Math.cos(branch) * barb,
                            Math.sin(angle) * along + Math.sin(branch) * barb);
                    c.line(root, end, .009F, WHITE, alpha * .5F, alpha * .08F);
                }
            }
        }
    }

    private static Vec3 frostEdge(Vec3 center, Vec3 right, Vec3 up, float radius, int seed, int i) {
        double angle = i * Math.PI / 4 + (Canvas.hash(seed, i) - .5) * .35;
        double reach = radius * (.8 + Canvas.hash(seed, i + 16) * .57);
        return frostPoint(center, right, up, Math.cos(angle) * reach, Math.sin(angle) * reach);
    }

    /** A point on the struck face, kept inside it. */
    private static Vec3 frostPoint(Vec3 center, Vec3 right, Vec3 up, double x, double y) {
        return center.add(right.scale(Math.clamp(x, -.489, .489))).add(up.scale(Math.clamp(y, -.489, .489)));
    }

    private static void stream(Canvas c, Vec3 start, Vec3 end, int side, float now, float alpha,
                               boolean minimal, boolean siphon, int seed, float width) {
        Vec3 path = end.subtract(start), axis = path.normalize();
        Vec3 right = axis.cross(Math.abs(axis.y) > .95 ? new Vec3(0, 0, 1) : UP).normalize();
        Vec3 up = right.cross(axis);
        if (c.soft()) {
            var shade = frost(c);
            int billows = minimal ? 4 : 8;
            for (int i = 0; i < billows; i++) {
                float u = (now * .0875F + (i + (side > 0 ? .5F : 0)) / billows) % 1;
                float spin = i * 2.39996F + side * now * .31F;
                float spread = (.07F + u * .42F) * width;
                Vec3 at = start.lerp(end, u).add(right.scale(Mth.cos(spin) * spread))
                        .add(up.scale(Mth.sin(spin) * spread * .78F)).add(0, -.16F * u * u, 0);
                float radius = (.12F + u * .48F) * (.78F + .44F * Canvas.hash(seed, i)) * width;
                float fade = Canvas.smooth(0, .09F, u) * (1 - Canvas.smooth(.82F, 1, u));
                float stretch = (float) Math.min(.53, path.length() * .06);
                c.cloud(at, radius + stretch * (float) Math.abs(axis.x), radius * .83F + stretch * (float) Math.abs(axis.y),
                        radius + stretch * (float) Math.abs(axis.z), i, spin, .32F * alpha * fade * clearance(c, at), shade);
            }
            int flakes = minimal ? 5 : 16;
            for (int i = 0; i < flakes; i++) {
                float u = (now * .1675F + (i + Canvas.hash(seed, i + 50)) / flakes) % 1;
                float angle = i * 2.39996F + side * now * .24F;
                float spread = (.09F + u * .62F) * Canvas.hash(seed, i + 23) * width;
                Vec3 drift = right.scale(Mth.cos(angle)).add(up.scale(Mth.sin(angle)));
                Vec3 at = start.lerp(end, u).add(drift.scale(spread)).add(0, -.21F * u * u, 0);
                Vec3 tip = at.add(axis.scale(.07F + .14F * Canvas.hash(seed, i + 104)))
                        .add(drift.scale(.06F + Mth.sin(now * .9F + i) * .09F));
                float fade = Canvas.smooth(0, .06F, u) * (1 - Canvas.smooth(.88F, 1, u));
                c.shard(at, tip, .021F + .041F * Canvas.hash(seed, i + 112), 4, angle + now * .46F,
                        i % 3 == 0 ? ICE : WHITE, alpha * fade * clearance(c, at));
            }
        }
        if (c.glow() && siphon) {
            int embers = minimal ? 2 : 5;
            for (int i = 0; i < embers; i++) {
                float u = (now * .0825F + (i + Canvas.hash(seed, i + 80)) / embers + side * .075F + 1) % 1;
                float flare = Mth.sin(u * Mth.PI);
                float angle = now * .34F + i * 2.39996F;
                Vec3 at = end.lerp(start, u).add(right.scale(.28F * flare * Mth.cos(angle)))
                        .add(up.scale(.32F * flare * Mth.sin(angle)));
                c.glow(at, .088F, HEAT, .68F * flare * alpha * clearance(c, at));
            }
        }
    }

    private static CloudMesh.Shade frost(Canvas c) {
        float daylight = c.daylight;
        return (nx, ny, nz, bump, x, y, z, rgb) -> {
            float light = Mth.clamp(.56F + daylight * .2F + ny * .21F + bump * .7F, .22F, 1);
            for (int i = 0; i < rgb.length; i++) rgb[i] = Mth.lerp(light, SHADOW[i], MIST[i]);
        };
    }

    private static float clearance(Canvas c, Vec3 at) {
        return Canvas.smooth(.28F, .9F, (float) at.distanceTo(c.camera()));
    }

    private static void quench(Canvas c, Active a, float t) {
        float age = t / a.piece().ticks;
        float alpha = Canvas.smooth(0, 2, t) * (1 - Canvas.smooth(.3F, 1, age));
        Vec3 center = a.bound(c) instanceof Player target
                ? target.getPosition(c.partial).add(0, target.getBbHeight() * .5, 0) : a.origin();
        float size = a.scale(), radius = size * (.17F + .66F * Canvas.smooth(0, .5F, age));
        if (c.soft()) {
            c.cloud(center.add(0, age * .4, 0), radius, radius * .7F, radius, 3, age,
                    .51F * alpha, frost(c));
            int count = Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL ? 4 : 9;
            for (int i = 0; i < count; i++) {
                double angle = (i + Canvas.hash(a.seed(), i) * .5) * Math.PI * 2 / count;
                Vec3 base = center.add(Math.cos(angle) * radius, age * .47 - .26, Math.sin(angle) * radius);
                Vec3 tip = base.add(Math.cos(angle) * .13, size * (.16 + Canvas.hash(a.seed(), i + count) * .34),
                        Math.sin(angle) * .13);
                c.shard(base, tip, size * .062F, 4, (float) angle, i % 3 == 0 ? ICE : WHITE, alpha);
            }
        }
    }

    // ------------------------------------------------------------------ frost constructs

    /** One frost-construct cell: faceted faces lit by the sun, a crest on walls, icicles under decks. */
    static void cell(Canvas c, BlockPos pos, FrostConstructBlock.Form form) {
        double low = form.bottom, high = form.top;
        var below = CryoConstructArt.form(pos.below());
        var above = CryoConstructArt.form(pos.above());
        Vec3 root = Vec3.atLowerCornerOf(pos);
        if (high < 1 || above == null || above.bottom > 0) {
            face(c, root.add(0, high, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), Direction.UP, pos);
            if (c.solid() && (form == FrostConstructBlock.Form.WALL || form == FrostConstructBlock.Form.CROWN))
                crest(c, root.add(0, high, 0), Long.hashCode(pos.asLong()));
        }
        if (low > 0 || below == null || below.top < 1)
            face(c, root.add(0, low, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), Direction.DOWN, pos);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            var neighbor = CryoConstructArt.form(pos.relative(dir));
            if (neighbor == null || high <= neighbor.bottom || low >= neighbor.top) {
                side(c, pos, root, dir, low, high);
                if (c.solid() && neighbor == null && (form == FrostConstructBlock.Form.DECK || form == FrostConstructBlock.Form.LOW)
                        && (low > 0 || below == null && c.level.getBlockState(pos.below()).isAir()))
                    icicle(c, root.add(.5, low, .5), dir, Long.hashCode(pos.asLong()));
            }
            else if (neighbor.bottom > low) side(c, pos, root, dir, low, neighbor.bottom);
            else if (neighbor.top < high) side(c, pos, root, dir, neighbor.top, high);
        }
    }

    private static void crest(Canvas c, Vec3 root, int seed) {
        for (int i = 0; i < 3; i++) {
            double x = .26 + .51 * Canvas.hash(seed, 4*i);
            double z = .24 + .53 * Canvas.hash(seed, 4*i+1);
            Vec3 at = root.add(x, -.11, z);
            Vec3 tip = at.add((x-.5)*.68, .36 + .42 * Canvas.hash(seed, 4*i+2), (z-.5)*.61);
            c.shard(at, tip, .12F + .10F * Canvas.hash(seed, 4*i+3), 5, i*2.4F, i == 0 ? RIME : ICE, 1);
        }
    }

    private static void icicle(Canvas c, Vec3 center, Direction side, int seed) {
        int variant = seed ^ side.ordinal() * 997;
        if (Canvas.hash(variant, 0) < .28F) return;
        Vec3 normal = Vec3.atLowerCornerOf(side.getNormal());
        Vec3 along = new Vec3(-normal.z, 0, normal.x);
        Vec3 root = center.add(normal.scale(.32)).add(along.scale(Canvas.hash(variant, 1) * .49 - .245)).add(0, .16, 0);
        Vec3 tip = root.add(normal.scale(.05)).add(along.scale(.14)).add(0, -.33 - .38 * Canvas.hash(variant, 2), 0);
        c.shard(root, tip, .15F + .08F * Canvas.hash(variant, 3), 5, Canvas.hash(variant, 4) * 5, ICE, 1);
    }

    private static void side(Canvas c, BlockPos pos, Vec3 root, Direction dir, double low, double high) {
        var along = dir.getAxis() == Direction.Axis.X ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0);
        face(c, root.add(Math.max(0, dir.getStepX()), low, Math.max(0, dir.getStepZ())), along,
                new Vec3(0, high - low, 0), dir, pos);
    }

    private static void face(Canvas c, Vec3 root, Vec3 u, Vec3 v, Direction dir, BlockPos pos) {
        int seed = Long.hashCode(pos.asLong()) ^ dir.ordinal() * 293;
        Vec3 inward = Vec3.atLowerCornerOf(dir.getNormal()).scale(-.065);
        Vec3 center = root.add(u.scale(.24 + Canvas.hash(seed, 0) * .52))
                .add(v.scale(.23 + Canvas.hash(seed, 1) * .54)).add(inward);
        Vec3[] rim = {root, root.add(u.scale(.5)), root.add(u), root.add(u).add(v.scale(.5)),
                root.add(u).add(v), root.add(v).add(u.scale(.5)), root.add(v), root.add(v.scale(.5))};
        float daylight = .53F + c.daylight * .47F;
        float light = switch (dir) {
            case UP -> .93F;
            case DOWN -> .18F;
            case EAST, SOUTH -> .58F;
            default -> .46F;
        };
        if (c.solid()) {
            for (int i = 0; i < rim.length; i++) {
                float tint = (light + Canvas.hash(seed, i + 2) * .24F) * daylight;
                float[] color = Canvas.mix(DEEP, dir == Direction.UP ? RIME : ICE, Math.min(1, tint));
                c.tri(rim[i], rim[(i + 1) % rim.length], center, color, 1, 1, 1);
            }
            return;
        }
        // Pale seams catch light inside the facets, with no outline between adjoining cells.
        Vec3 forward = inward.scale(-1.08);
        Vec3 vein = center.add(forward);
        int edge = (int) (Canvas.hash(seed, 24) * 7);
        c.line(vein, vein.lerp(rim[edge].add(forward), .84), .022F, RIME, .64F * daylight, .12F);
        c.line(vein, vein.lerp(rim[(edge + 3) % rim.length].add(forward), .59), .013F, RIME, .48F * daylight, .06F);
    }

    // ------------------------------------------------------------------ the Ice Skater and the finishers

    /** The Ice Skater's strip: frost laid flat on the ground, glittering as it lingers. */
    static void strip(Canvas c, Vec3 from, Vec3 to, float t, int life, int seed) {
        Vec3 path = to.subtract(from);
        double length = path.length();
        if (length < .3) return;
        Vec3 dir = path.scale(1 / length), right = new Vec3(-dir.z, 0, dir.x);
        float laid = smooth(0, 5, t), melt = smooth(life - 20, life, t), k = smooth(0, 1.5F, t) * (1 - melt);
        int n = 10;
        if (c.soft()) {
            Vec3[] spine = new Vec3[n];
            float[] w = new float[n], a = new float[n];
            for (int i = 0; i < n; i++) {
                float u = laid * i / (n - 1F);
                spine[i] = from.add(dir.scale(length * u)).add(0, .035, 0);
                // As it melts, it breaks into patches.
                float patch = .6F + .4F * sin(i * 2.3F + seed);
                w[i] = .5F * (1 - melt * (1 - patch));
                a[i] = k * (.45F + .15F * patch) * (1 - smooth(.7F, 1, (float) i / (n - 1)) * .5F);
            }
            c.ribbon(spine, w, ICE, a);
            for (int s : new int[]{-1, 1}) {
                Vec3[] edge = new Vec3[n];
                for (int i = 0; i < n; i++) edge[i] = spine[i].add(right.scale(s * w[i] * .9));
                c.ribbon(edge, .03F, GLACIER, k * .5F, k * .5F);
            }
        }
        if (c.glow() && !minimal()) {
            float clock = (c.level.getGameTime() % 2400 + c.partial);
            for (int i = 0; i < 9; i++) {
                float u = hash(seed, i), twinkle = .5F + .5F * sin(clock * .5F + i * 2.7F);
                Vec3 at = from.add(dir.scale(length * u * laid)).add(right.scale((hash(seed, i + 9) - .5F) * .7F)).add(0, .05, 0);
                c.glow(at, .07F + .05F * twinkle, RIME, k * twinkle * .8F);
            }
        }
    }

    // ------------------------------------------------------------------
    // Puppeteer (Telekinesis): the unseen hand, seen
    // ------------------------------------------------------------------


    /**
     * Last Warmth (Cryokinesis's finisher, {@code cryo/LastWarmth}): an ember of the foe's warmth is drawn out of its
     * chest along a thread to the open palm (the only warm colour in the kit), frost rings creep over the body from the
     * feet and the crown toward the middle as it leaves, the fist closes on it in a puff of steam, and the rimed body
     * breaks into falling snow. Bound to the foe, the caster's entity id in the seed; scale its height (+ HOT: a flame).
     */
    static void warmth(Canvas c, Active a, float t) {
        boolean hot = a.scale() >= com.jayptucker.heroesevolved.cryo.LastWarmth.HOT;
        float h = Math.max(.6F, a.scale() - (hot ? com.jayptucker.heroesevolved.cryo.LastWarmth.HOT : 0));
        float life = a.piece().ticks, f = fade(t, life, 1, 6);
        net.minecraft.world.entity.Entity foe = a.bound(c), owner = c.level.getEntity(a.seed());
        Vec3 feet = foe != null && foe.isAlive() ? foe.getPosition(c.partial) : a.origin();
        Vec3 chest = feet.add(0, h * .6, 0);
        Vec3 palm = owner instanceof net.minecraft.world.entity.player.Player p
                ? com.jayptucker.heroesevolved.combat.StyleHits.hand(p, c.partial, true) : chest.add(0, 0, 1);
        int draw = com.jayptucker.heroesevolved.cryo.LastWarmth.DRAW, close = com.jayptucker.heroesevolved.cryo.LastWarmth.CLOSE;
        int land = com.jayptucker.heroesevolved.cryo.LastWarmth.LAND;
        float r = Math.max(.45F, h * .22F);
        // Frost creeping in from the feet and the crown.
        float creep = smooth(draw, land, t);
        if (c.glow() && t < land + 4) for (int k = 0; k < 2; k++) {
            double y = k == 0 ? h * (.05 + .45 * creep) : h * (.98 - .4 * creep);
            float a1 = f * (.3F + .6F * creep) * (1 - smooth(land, land + 4, t));
            c.ring(feet.add(0, y, 0), UP, r, .07F, 0, TWO_PI, GLACIER, a1 * .4F);
            c.ring(feet.add(0, y, 0), UP, r, .022F, 0, TWO_PI, RIME, a1);
        }
        // The ember drawn out to the palm, and the thread it rides.
        float pull = smooth(draw, close, t);
        if (t >= draw && t < close + 1) {
            Vec3 ember = chest.lerp(palm, pull);
            float size = hot ? .26F : .12F;
            if (c.glow()) {
                c.line(chest, ember, .02F, ICE, f * .5F, f * .8F);
                c.glow(ember, size * 2.4F, WARM, f * .5F);
                c.glow(ember, size, WARM_CORE, f);
            }
        }
        // The fist closes: steam.
        float steam = smooth(close, close + 1, t) * (1 - smooth(close + 2, close + 8, t));
        if (steam > 0 && c.glow()) for (int i = 0; i < 5; i++) {
            Vec3 puff = palm.add((hash(a.seed(), i) - .5) * .3, (t - close) * .05 + hash(a.seed(), i + 5) * .2, (hash(a.seed(), i + 9) - .5) * .3);
            c.glow(puff, .14F + (t - close) * .02F, RIME, steam * f * .35F);
        }
        // Snow and chips of ice as the rimed body breaks.
        if (t >= land && c.glow()) {
            float fall = t - land;
            for (int i = 0; i < 16; i++) {
                double ang = hash(a.seed(), i + 20) * TWO_PI, rr = r * (.4 + 1.2 * hash(a.seed(), i + 40));
                double y = h * hash(a.seed(), i + 60) - fall * (.05 + .05 * hash(a.seed(), i + 80));
                if (y < 0) continue;
                c.glow(feet.add(Math.cos(ang) * rr * (1 + fall * .03), y, Math.sin(ang) * rr * (1 + fall * .03)), .05F, RIME, f * .9F);
            }
        }
    }

    /**
     * Cryokinesis's alternate finishers ({@code cryo/CryoFinishers}), in Last Warmth's frost: Coffin (leaning shards grow
     * up over the body and seal, then burst outward and skid away) and Icicle Ring (a ring of icicles rises, leans in until
     * their points meet over the foe, a frost burst, and they crumble). Origin the foe's feet, target yours, scale its height.
     */
    static void cryoFinishers(Canvas c, Active a, float t) {
        if (c.solid()) return;
        float h = Math.max(.6F, a.scale()), f = fade(t, a.piece().ticks, 1, 7);
        Vec3 feet = a.origin();
        int seed = a.seed();
        if (a.piece() == com.jayptucker.heroesevolved.styles.Piece.FINISH_COFFIN) {
            int sealed = com.jayptucker.heroesevolved.cryo.CryoFinishers.COFFIN_SEALED, land = com.jayptucker.heroesevolved.cryo.CryoFinishers.COFFIN_LAND;
            float r = Math.max(.4F, h * .26F);
            int n = minimal() ? 7 : 11;
            if (t < land) {
                for (int i = 0; i < n; i++) {
                    float ang = (i + hash(seed, i) * .4F) * TWO_PI / n;
                    // Each shard starts a little later than the last, so the ice climbs round the body.
                    float grow = smooth(i * .6F, sealed - 2 + i * .2F, t);
                    if (grow <= 0) continue;
                    Vec3 out = new Vec3(cos(ang), 0, sin(ang));
                    Vec3 base = feet.add(out.scale(r * 1.05));
                    Vec3 tip = feet.add(out.scale(r * .35)).add(0, (h + .25) * (.75F + .25F * hash(seed, i + 7)) * grow, 0);
                    float shake = t > sealed ? (hash(seed + (int) t, i) - .5F) * .02F : 0;
                    c.shard(base.add(shake, 0, shake), tip, .16F + .06F * hash(seed, i + 3), 5, ang, ICE, f * .55F);
                }
                float seal = smooth(sealed - 3, sealed, t);
                if (c.soft() && seal > 0) c.shell(feet.add(0, h * .5, 0), r * .95F, h * .58F, r * .95F, PI, GLACIER, f * .25F * seal, .6F,
                        Canvas.hexes(5, .12F, .4F), 0);
                if (c.glow()) for (int k = 0; k < 3; k++) {
                    float y = (float) (h * (.15 + .35 * k)) * smooth(0, sealed, t);
                    c.ring(feet.add(0, y, 0), UP, r, .02F, 0, TWO_PI, RIME, f * .7F);
                }
            } else {
                // Shattered: shards thrown out and skidding over the ground, slowing, then snow.
                float k = t - land;
                for (int i = 0; i < n + 4; i++) {
                    float ang = hash(seed, i + 30) * TWO_PI;
                    Vec3 out = new Vec3(cos(ang), 0, sin(ang));
                    float travel = (1 - (float) Math.exp(-k * .25F)) * (1.2F + 1.6F * hash(seed, i + 40));
                    double hop = Math.max(0, .9 * hash(seed, i + 50) * h * (1 - k / 6F) * (k < 6 ? 1 : 0) + .04);
                    Vec3 at = feet.add(out.scale(r + travel)).add(0, hop, 0);
                    Vec3 tip = at.add(out.scale(.3)).add(0, .08, 0);
                    c.shard(at, tip, .07F + .05F * hash(seed, i + 60), 4, ang + k * .4F, ICE, f * .7F);
                }
                if (c.glow()) {
                    c.glow(feet.add(0, h * .5, 0), 1.2F * (1 - smooth(land, land + 3, t)), RIME, f * (1 - smooth(land, land + 3, t)));
                    c.ring(feet.add(0, .04, 0), UP, r + 2.2F * smooth(land, land + 8, t), .03F, 0, TWO_PI, RIME, f * (1 - smooth(land + 2, land + 9, t)));
                }
                snow(c, feet, r, h, k, f, seed);
            }
            return;
        }
        // Icicle Ring.
        int risen = com.jayptucker.heroesevolved.cryo.CryoFinishers.ICICLES_RISEN, land = com.jayptucker.heroesevolved.cryo.CryoFinishers.ICICLES_LAND;
        float ringR = Math.max(1.3F, h * .6F + .7F);
        int n = minimal() ? 6 : 9;
        Vec3 meet = feet.add(0, h + .35, 0);
        float rise = smooth(0, risen, t), lean = smooth(risen, land, t), crumble = smooth(land + 3, land + 10, t);
        for (int i = 0; i < n; i++) {
            float ang = i * TWO_PI / n + .2F;
            Vec3 base = feet.add(cos(ang) * ringR, 0, sin(ang) * ringR);
            Vec3 upright = base.add(0, (h + .8) * rise, 0);
            Vec3 tip = upright.lerp(meet, lean);
            if (crumble > 0) tip = base.lerp(tip, 1 - crumble);
            if (base.distanceToSqr(tip) < .01) continue;
            c.shard(base, tip, .14F * (1 - crumble * .5F), 5, ang, ICE, f * .6F);
            if (c.glow()) c.line(base, tip, .02F, RIME, f * .3F, f * .7F);
        }
        if (c.glow() && t < land + 4) c.ring(feet.add(0, .04, 0), UP, ringR, .03F, 0, TWO_PI, GLACIER, f * .6F * rise);
        if (t >= land) {
            float burst = smooth(land, land + 6, t), gone = f * (1 - smooth(land + 2, land + 9, t));
            if (c.glow()) {
                c.glow(meet, .3F + 1.3F * (1 - smooth(land, land + 3, t)), RIME, f * (1 - smooth(land, land + 3, t)));
                c.ring(feet.add(0, h * .5, 0), UP, .4F + 1.6F * burst, .04F, 0, TWO_PI, ICE, gone);
            }
            if (c.soft()) c.shell(feet.add(0, h * .5, 0), .45F + .2F * h, h * .55F, .45F + .2F * h, PI, GLACIER, gone * .3F, .6F,
                    Canvas.hexes(5, .12F, .4F), 0);
            snow(c, feet, ringR * .6F, h, t - land, f, seed);
        }
    }

    /** Falling snow and glinting chips round a broken frozen body. */
    private static void snow(Canvas c, Vec3 feet, float r, float h, float fall, float f, int seed) {
        if (!c.glow()) return;
        for (int i = 0; i < 16; i++) {
            double ang = hash(seed, i + 20) * TWO_PI, rr = r * (.4 + 1.2 * hash(seed, i + 40));
            double y = h * hash(seed, i + 60) - fall * (.05 + .05 * hash(seed, i + 80));
            if (y < 0) continue;
            c.glow(feet.add(Math.cos(ang) * rr * (1 + fall * .03), y, Math.sin(ang) * rr * (1 + fall * .03)), .05F, RIME, f * .9F);
        }
    }

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }

    // ------------------------------------------------------------------ frost primitives (one shape language)

    /** A cold line in three layers: a glacier halo, an ice body, a white core. */
    static void frostLine(Canvas c, Vec3 a, Vec3 b, float width, float from, float to) {
        if (!c.glow() || from < .01F && to < .01F) return;
        c.line(a, b, width * 2.6F, GLACIER, from * .3F, to * .3F);
        c.line(a, b, width, ICE, from * .8F, to * .8F);
        c.line(a, b, width * .35F, CORE, from, to);
    }

    /** Ice's own ring: a hexagon of cold light on a plane, a dark seam under it at noon. */
    static void hexRing(Canvas c, Vec3 at, Vec3 normal, float radius, float width, float spin, float alpha) {
        if (alpha < .01F || radius <= .02F) return;
        Vec3 n = normal.lengthSqr() < 1e-5 ? UP : normal.normalize();
        Vec3 u = (Math.abs(n.y) > .9 ? new Vec3(1, 0, 0) : UP).cross(n).normalize(), v = n.cross(u).normalize();
        for (int i = 0; i < 6; i++) {
            float a0 = spin + i * TWO_PI / 6, a1 = spin + (i + 1) * TWO_PI / 6;
            Vec3 p0 = at.add(u.scale(Math.cos(a0) * radius)).add(v.scale(Math.sin(a0) * radius));
            Vec3 p1 = at.add(u.scale(Math.cos(a1) * radius)).add(v.scale(Math.sin(a1) * radius));
            if (c.soft()) c.line(p0, p1, width * 2.2F, DEEP, alpha * .25F, alpha * .25F);
            frostLine(c, p0, p1, width, alpha, alpha);
            if (c.glow()) c.glow(p0, width * 3.5F, CORE, alpha * .5F);
        }
    }

    /** Frost feathers on a plane: veins growing out from {@code at} with barbs swept back, as on a cold window. */
    static void feather(Canvas c, Vec3 at, Vec3 normal, float radius, float grow, float alpha, int seed) {
        if (alpha < .01F || grow <= 0) return;
        Vec3 n = normal.lengthSqr() < 1e-5 ? UP : normal.normalize();
        Vec3 u = (Math.abs(n.y) > .9 ? new Vec3(1, 0, 0) : UP).cross(n).normalize(), v = n.cross(u).normalize();
        int veins = minimal() ? 4 : 6;
        for (int i = 0; i < veins; i++) {
            double angle = i * TWO_PI / veins + (hash(seed, i) - .5) * .6;
            double reach = radius * grow * (.7 + .4 * hash(seed, i + 9));
            Vec3 dir = u.scale(Math.cos(angle)).add(v.scale(Math.sin(angle)));
            Vec3 tip = at.add(dir.scale(reach));
            if (c.soft()) c.line(at, tip, .05F, DEEP, alpha * .3F, alpha * .05F);
            frostLine(c, at, tip, .016F, alpha * .9F, alpha * .1F);
            for (int b = 1; b <= 3; b++) {
                double along = reach * b / 4, barb = reach * (.24 - .05 * b);
                for (int s = -1; s <= 1; s += 2) {
                    double branch = angle + s * .8;
                    Vec3 root = at.add(dir.scale(along));
                    Vec3 end = root.add(u.scale(Math.cos(branch) * barb)).add(v.scale(Math.sin(branch) * barb));
                    if (c.glow()) c.line(root, end, .01F, RIME, alpha * .55F, alpha * .05F);
                }
            }
        }
    }

    /** Chips of ice thrown off a point along {@code dir} and falling as {@code t} runs; glints in the glow pass. */
    static void chips(Canvas c, Vec3 at, Vec3 dir, float size, float t, float alpha, int seed) {
        if (alpha < .01F || t > 16) return;
        Vec3 n = dir.lengthSqr() < 1e-5 ? UP : dir.normalize();
        Vec3 u = (Math.abs(n.y) > .9 ? new Vec3(1, 0, 0) : UP).cross(n).normalize(), v = n.cross(u).normalize();
        int count = minimal() ? 4 : 9;
        for (int i = 0; i < count; i++) {
            double ang = hash(seed, i + 70) * TWO_PI, speed = (.1 + .14 * hash(seed, i + 80)) * (.7 + size * .3);
            Vec3 out = n.scale(.6).add(u.scale(Math.cos(ang) * .8)).add(v.scale(Math.sin(ang) * .8)).normalize();
            Vec3 p = at.add(out.scale(speed * t)).add(0, -.025 * t * t, 0);
            float chip = (.05F + .06F * hash(seed, i + 90)) * Math.max(.6F, size * .5F);
            Vec3 tip = p.add(out.scale(chip * 2.2));
            // Clear ice, not stone: a translucent body, a bright edge and a glint.
            if (c.soft()) c.shard(p, tip, chip, 4, t * .5F + i, i % 3 == 0 ? RIME : ICE, alpha * .55F);
            if (c.glow()) { c.line(p, tip, chip * .25F, CORE, alpha * .6F, alpha * .2F); c.glow(p, chip * 1.4F, CORE, alpha * .35F * (1 - t / 16)); }
        }
    }

    /** Snow falling (and drifting round) in a column, deterministic so every viewer sees the same. */
    static void snowfall(Canvas c, Vec3 centre, float radius, float height, float swirl, float alpha, int seed) {
        if (!c.glow() || alpha < .01F) return;
        float now = c.level.getGameTime() + c.partial;
        int flakes = minimal() ? 10 : 30;
        for (int i = 0; i < flakes; i++) {
            float fall = (now * (.02F + .02F * hash(seed, i)) + hash(seed, i + 40)) % 1;
            double a = i * 2.39996 + now * swirl, r = Math.sqrt((i + .5) / flakes) * radius;
            Vec3 p = centre.add(Math.cos(a) * r, height * (1 - fall), Math.sin(a) * r);
            c.glow(p, .045F + .03F * hash(seed, i + 7), i % 4 == 0 ? ICE : CORE, alpha * .8F * Mth.sin(fall * PI));
        }
    }

    // ------------------------------------------------------------------ the older short effects, as shapes

    /** What a {@code CRYO_*} effect draws now (the kind's ordinal is in the seed's high bits). */
    static void fx(Canvas c, Active a, float t) {
        var kinds = com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.values();
        int ordinal = a.seed() >>> 16, seed = a.seed() & 0xFFFF;
        if (ordinal >= kinds.length) return;
        Vec3 o = a.origin(), to = a.target(), path = to.subtract(o);
        float size = a.scale();
        switch (kinds[ordinal]) {
            case CRYO_BOLT -> {
                float life = 16, alpha = fade(t, life, .5F, 8), reach = smooth(0, 2, t);
                Vec3 dir = path.lengthSqr() < 1e-4 ? UP : path.normalize();
                frostLine(c, o, o.lerp(to, reach), .05F, alpha, alpha);
                int rings = Math.clamp((int) (path.length() / 3), 1, 8);
                for (int i = 1; i <= rings; i++) {
                    float k = smooth(i * .25F, i * .25F + 6, t);
                    if (k > 0 && k < 1) hexRing(c, o.lerp(to, i / (rings + 1F)), dir, .18F + .3F * k, .02F, i * .4F, alpha * (1 - k));
                }
                if (t >= 2) { feather(c, to, dir.reverse(), .8F, smooth(2, 8, t), alpha, seed); chips(c, to, dir.reverse(), 1, t - 2, alpha, seed); }
            }
            case CRYO_CHILL -> {
                float life = 18, alpha = fade(t, life, 1, 8), h = (float) Math.clamp(path.y, .5, 6), r = Math.max(.4F, size * .6F);
                hexRing(c, o.add(0, .06, 0), UP, r * (.6F + .5F * smooth(0, 6, t)), .025F, seed * .01F, alpha * .8F);
                for (int k = 0; k < 2; k++) hexRing(c, o.add(0, h * smooth(k * 2, 10 + k * 2, t), 0), UP, r * .55F, .018F, k + t * .02F, alpha * .6F);
            }
            case CRYO_FREEZE -> {
                float life = 22, alpha = fade(t, life, 1, 8), h = (float) Math.clamp(path.y, .6, 8), r = Math.max(.45F, size * .5F);
                float creep = smooth(0, 10, t);
                for (int k = 0; k < 2; k++) {
                    double y = k == 0 ? h * .5 * creep : h * (1 - .5 * creep);
                    hexRing(c, o.add(0, y, 0), UP, r, .03F, k * .5F, alpha * (1 - smooth(10, 14, t)));
                }
                if (t >= 10) {
                    float seal = smooth(10, 13, t);
                    if (c.soft()) c.shell(o.add(0, h * .5, 0), r * 1.05F, h * .58F, r * 1.05F, PI, ICE, alpha * .3F * seal, .6F, Canvas.hexes(6, .12F, .4F), 0);
                    if (c.glow()) c.glow(o.add(0, h * .5, 0), r * 2 * (1 - smooth(10, 15, t)), CORE, alpha * (1 - smooth(10, 15, t)));
                }
            }
            case CRYO_SHATTER -> {
                float life = 24, alpha = fade(t, life, .5F, 10);
                Vec3 centre = o.lerp(to, .5);
                if (c.glow()) c.glow(centre, Math.max(1, size) * (1 - smooth(0, 4, t)), CORE, alpha);
                chips(c, centre, UP, Math.max(1.5F, size * 1.5F), t, alpha, seed);
                hexRing(c, o.add(0, .06, 0), UP, .3F + Math.max(1, size) * 2 * smooth(0, 10, t), .03F, seed * .01F, alpha * (1 - smooth(4, 14, t)));
                snowfall(c, centre.subtract(0, 1, 0), Math.max(.8F, size), 2.2F, .02F, alpha * smooth(4, 8, t), seed);
            }
            case CRYO_ICE_GROWTH, CRYO_SCULPT -> {
                float life = 18, alpha = fade(t, life, 1, 8);
                hexRing(c, o.add(0, .05, 0), UP, .4F + .7F * smooth(0, 6, t), .025F, seed * .01F, alpha * .8F);
                feather(c, o.add(0, .04, 0), UP, 1.1F, smooth(0, 8, t), alpha * .8F, seed);
            }
            case CRYO_PRISON -> {
                float life = 14, alpha = fade(t, life, 2, 2), h = (float) Math.clamp(path.y, .6, 6), grown = Math.clamp(size, 0, 1);
                hexRing(c, o.add(0, .05, 0), UP, .7F, .03F, seed * .01F, alpha);
                for (int k = 0; k < 3; k++) if (k / 3F <= grown) hexRing(c, o.add(0, h * (.2 + .3 * k) * grown, 0), UP, .55F, .02F, k * .7F, alpha * .7F);
            }
            case CRYO_FRACTURE -> {
                float life = 24, alpha = fade(t, life, .5F, 10), r = Math.max(1, size);
                hexRing(c, o.add(0, .08, 0), UP, r * smooth(0, 8, t), .04F, 0, alpha * (1 - smooth(6, 16, t)));
                feather(c, o.add(0, .04, 0), UP, r, smooth(0, 6, t), alpha, seed);
                chips(c, o.add(0, .6, 0), UP, 1.5F, t, alpha, seed);
            }
            case CRYO_WHITEOUT -> {
                float life = 14, alpha = fade(t, life, 2, 2), r = Math.max(2, size);
                float spin = (c.level.getGameTime() + c.partial) * .006F;
                hexRing(c, o.add(0, .08, 0), UP, r, .05F, spin, alpha * .8F);
                feather(c, o.add(0, .05, 0), UP, r, 1, alpha * .45F, seed);
                snowfall(c, o, r, 5, .03F, alpha, 7);
            }
            case CRYO_BRANCH, CRYO_RIBBON, CRYO_LOOM, CRYO_CONSTELLATION -> {
                float life = 18, alpha = fade(t, life, 1, 8), grow = smooth(0, 6, t);
                if (path.lengthSqr() < 1e-4) return;
                Vec3 tip = o.lerp(to, grow), dir = path.normalize(), side = (Math.abs(dir.y) > .9 ? new Vec3(1, 0, 0) : UP).cross(dir).normalize();
                frostLine(c, o, tip, .02F, alpha, alpha * .6F);
                int forks = Math.clamp((int) (path.length() * 1.5 * grow), 1, 10);
                for (int i = 1; i <= forks; i++) {
                    Vec3 root = o.lerp(to, i / (forks + 1F) * grow);
                    Vec3 end = root.subtract(dir.scale(.18)).add(side.scale((i % 2 == 0 ? 1 : -1) * (.2 + .2 * hash(seed, i))));
                    if (c.glow()) c.line(root, end, .012F, RIME, alpha * .6F, alpha * .05F);
                }
            }
            case CRYO_RIDE -> {
                float life = 10, alpha = fade(t, life, .5F, 6);
                Vec3 back = path.lengthSqr() > 1e-3 ? path.normalize().reverse() : new Vec3(0, 0, -1);
                Vec3 side = new Vec3(-back.z, 0, back.x);
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 skate = o.add(side.scale(s * .2)).add(0, .03, 0);
                    frostLine(c, skate, skate.add(back.scale(.9)), .02F, alpha, 0);
                }
                chips(c, o.add(back.scale(.3)).add(0, .1, 0), back.add(0, .6, 0), .6F, t, alpha * .8F, seed);
            }
            case CRYO_RIME_STREAM -> {
                float life = 8, alpha = fade(t, life, .5F, 4);
                if (path.lengthSqr() < 1e-4) return;
                Vec3 dir = path.normalize(), side = (Math.abs(dir.y) > .9 ? new Vec3(1, 0, 0) : UP).cross(dir).normalize(), up = side.cross(dir);
                Vec3 prev = o;
                for (int i = 1; i <= 10; i++) {
                    double u = i / 10.0, phase = (c.level.getGameTime() + c.partial) * .3 + u * 10;
                    Vec3 p = o.add(path.scale(u)).add(side.scale(Math.sin(phase) * .06 * u)).add(up.scale(Math.cos(phase) * .06 * u));
                    frostLine(c, prev, p, .018F, alpha, alpha);
                    prev = p;
                }
                feather(c, to, dir.reverse(), .5F, 1, alpha * .8F, seed);
            }
            case CRYO_BLIZZARD -> {
                float life = 22, alpha = fade(t, life, 3, 6), r = Math.max(2, size);
                snowfall(c, o, r, 7, .05F, alpha, seed);
                if (c.soft()) for (int k = 0; k < 3; k++)
                    c.ring(o.add(0, .6 + k * 1.6, 0), UP, r * (.5F + k * .15F), .35F, 0, TWO_PI, RIME, alpha * .12F);
            }
            case CRYO_AWAKEN_CHARGE, CRYO_AWAKEN_RELEASE -> {
                boolean release = kinds[ordinal] == com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.CRYO_AWAKEN_RELEASE;
                float life = release ? 30 : 16, alpha = fade(t, life, 1, 8), r = Math.max(1, size);
                hexRing(c, o.add(0, .08, 0), UP, (release ? r * 2 : r) * smooth(0, life * .6F, t) + .4F, .04F, t * .02F, alpha);
                if (c.glow()) c.helix(o, release ? 4 : 2.5F, .4F, r * .9F, 2, t * .1F, .03F, RIME, alpha * .6F, 32);
                if (release) { chips(c, o.add(0, 1, 0), UP, 2, t, alpha, seed); snowfall(c, o, r * 1.5F, 4, .03F, alpha, seed); }
            }
            case CRYO_SNAP -> {
                float life = 14, alpha = fade(t, life, .5F, 8);
                hexRing(c, o.add(0, .04, 0), UP, Math.max(.4F, size) * smooth(0, 6, t) + .2F, .03F, 0, alpha * (1 - smooth(4, 12, t)));
            }
            case CRYO_LANCE_FORGE -> {
                float alpha = fade(t, 10, .5F, 5);
                if (c.glow()) c.glow(o.add(path.lengthSqr() > 1e-4 ? path.normalize().scale(.3) : Vec3.ZERO), .18F, CORE, alpha * .6F);
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ Absolute Zero (the signature)

    /** Where a caster's palms are, in camera space for their own first-person view. */
    private static Vec3 palm(Canvas c, Player p, int side) {
        Minecraft mc = Minecraft.getInstance();
        if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
            Vec3 aim = p.getViewVector(c.partial), right = Vec3.directionFromRotation(0, p.getViewYRot(c.partial) + 90);
            return c.camera().add(aim.scale(.7)).add(right.scale(side * .42)).subtract(right.cross(aim).scale(.35));
        }
        return com.jayptucker.heroesevolved.cryo.CryoBlastGeometry.palm(p, c.partial, side);
    }

    /**
     * Absolute Zero round its caster: for the channel, hexagonal rims of frost close in over the ground toward them and
     * the cold gathers at the palms; on the release a white flash, a hexagonal frost wave races out to the reach with
     * feathered frost behind it, and snow hangs in the air.
     */
    static void zero(Canvas c, Active a, float t) {
        Entity e = a.bound(c);
        Vec3 feet = e != null ? e.getPosition(c.partial) : a.origin();
        float r = Math.max(2, a.scale()), life = a.piece().ticks, alpha = fade(t, life, 2, 12);
        int channel = com.jayptucker.heroesevolved.cryo.AbsoluteZero.CHANNEL;
        Vec3 ground = feet.add(0, .06, 0);
        if (t < channel + 1) {
            float k = smooth(0, channel, t);
            if (c.glow()) c.ring(ground, UP, r, .06F, 0, TWO_PI, GLACIER, alpha * .4F);
            for (int i = 0; i < 3; i++) {
                float u = ((t / channel) * 1.5F + i / 3F) % 1;
                hexRing(c, ground, UP, r * (1 - u) + .4F, .03F, i * .3F + t * .01F, alpha * u * .8F);
            }
            if (e instanceof Player p) for (int s = -1; s <= 1; s += 2) {
                Vec3 at = palm(c, p, s);
                if (c.glow()) { c.glow(at, .2F + .3F * k, ICE, alpha * .5F); c.glow(at, .08F + .1F * k, CORE, alpha); }
            }
            snowfall(c, feet, r * .6F, 3.5F, -.03F, alpha * k * .6F, a.seed());
        } else {
            float k = smooth(channel, channel + 10, t), after = t - channel;
            if (c.glow()) c.glow(feet.add(0, 1, 0), 2.5F * (1 - smooth(channel, channel + 4, t)), CORE, alpha);
            hexRing(c, ground, UP, .5F + r * k, .06F, 0, alpha * (1 - smooth(channel + 8, channel + 18, t)));
            feather(c, ground, UP, r, k, alpha * (1 - smooth(channel + 14, life, t)), a.seed());
            chips(c, feet.add(0, 1, 0), UP, 2.5F, after, alpha, a.seed());
            snowfall(c, feet, r, 4.5F, .01F, alpha * (1 - smooth(channel + 20, life, t)), a.seed() + 1);
        }
    }

    /**
     * One foe giving up its warmth: an ember leaves the chest and runs along a thread to the caster's nearer palm (the
     * only warm colour in the kit), and frost rims creep up from the feet and down from the crown as it goes.
     */
    static void ember(Canvas c, Active a, float t) {
        Entity foe = a.bound(c), owner = c.level.getEntity(a.seed());
        if (foe == null || !(owner instanceof Player p)) return;
        int channel = com.jayptucker.heroesevolved.cryo.AbsoluteZero.CHANNEL;
        float life = a.piece().ticks, alpha = fade(t, life, 2, 4), h = Math.max(.6F, a.scale());
        Vec3 feet = foe.getPosition(c.partial), chest = feet.add(0, h * .6, 0);
        Vec3 palm = palm(c, p, (int) Math.signum(hash(foe.getId(), 1) - .5F + 1e-3F));
        float creep = smooth(0, channel, t);
        float r = Math.max(.45F, foe.getBbWidth() * .7F);
        for (int k = 0; k < 2; k++) {
            double y = k == 0 ? h * (.05 + .45 * creep) : h * (.98 - .45 * creep);
            hexRing(c, feet.add(0, y, 0), UP, r, .02F, k * .5F + t * .02F, alpha * (.3F + .6F * creep) * (1 - smooth(channel, channel + 3, t)));
        }
        if (t < channel + 1 && c.glow()) {
            for (int i = 0; i < 3; i++) {
                float u = ((t * .06F) + i / 3F) % 1;
                Vec3 at = chest.lerp(palm, u).add(0, Mth.sin(u * PI) * .4, 0);
                c.glow(at, .34F, WARM, alpha * .55F * Mth.sin(u * PI));
                c.glow(at, .13F, WARM_CORE, alpha * Mth.sin(u * PI));
            }
            Vec3 mid = chest.lerp(palm, .5).add(0, .4, 0);
            c.line(chest, mid, .05F, WARM, alpha * .25F, alpha * .2F);
            c.line(mid, palm, .05F, WARM, alpha * .2F, alpha * .1F);
            c.line(chest, mid, .018F, RIME, alpha * .55F, alpha * .45F);
            c.line(mid, palm, .018F, RIME, alpha * .45F, alpha * .15F);
            c.glow(chest, .3F * (1 - creep), WARM, alpha * .6F * (1 - creep));
        }
    }

    // ------------------------------------------------------------------
    // Ultimates (cryo/CryoUltimates)
    // ------------------------------------------------------------------

    /** A faceted shard of ice from {@code base} to {@code tip}: a translucent body, a bright edge and a glint. */
    private static void crystal(Canvas c, Vec3 base, Vec3 tip, float radius, int sides, float twist, float alpha) {
        if (alpha < .01F) return;
        if (c.soft()) c.shard(base, tip, radius, sides, twist, ICE, alpha * .6F);
        if (c.soft()) c.shard(base, base.lerp(tip, .6), radius * 1.06F, sides, twist, DEEP, alpha * .18F);
        if (c.glow()) {
            c.line(base, tip, radius * .18F, CORE, alpha * .7F, alpha * .25F);
            c.glow(tip, radius * 1.2F, RIME, alpha * .3F);
        }
    }

    /** A prism of ice round a body: shards standing round it and leaning in, grown from the feet up to {@code grow}. */
    private static void prism(Canvas c, Vec3 feet, float h, float grow, float alpha, int seed) {
        int n = minimal() ? 5 : 9;
        for (int i = 0; i < n; i++) {
            float ang = i * TWO_PI / n + hash(seed, i) * .4F, r = .55F + .2F * hash(seed, i + 9);
            Vec3 root = feet.add(cos(ang) * r, 0, sin(ang) * r);
            Vec3 tip = feet.add(cos(ang) * r * .35, h * (.85 + .35 * hash(seed, i + 19)) * grow, sin(ang) * r * .35);
            crystal(c, root, root.lerp(tip, Math.max(.05F, grow)), .26F, 5, ang, alpha);
        }
        if (c.soft()) c.shell(feet.add(0, h * .5 * grow, 0), .7F, h * .55F * grow, .7F, PI, ICE, alpha * .16F, .3F, PLAIN, 0);
    }

    /** One block of the glacier: its blueprint edges, or (grown) a lit slab of ice. */
    private static void block(Canvas c, Vec3 centre, float sx, float sy, float sz, float yaw, float grow, float plan, float alpha) {
        if (plan > .01F && c.glow()) {
            Vec3[] corners = new Vec3[8];
            float cy = cos(yaw), syw = sin(yaw);
            for (int i = 0; i < 8; i++) {
                double x = ((i & 1) == 0 ? -sx : sx), y = ((i & 2) == 0 ? -sy : sy), z = ((i & 4) == 0 ? -sz : sz);
                corners[i] = centre.add(x * cy - z * syw, y, x * syw + z * cy);
            }
            int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
            for (int[] e : edges) frostLine(c, corners[e[0]], corners[e[1]], .02F, plan * alpha * .7F, plan * alpha * .7F);
        }
        if (grow > .01F) {
            if (c.soft()) c.box(centre, sx * grow, sy * grow, sz * grow, yaw, 0, 0, ICE, alpha * .7F);
            if (c.glow()) c.glow(centre, Math.max(sx, sz) * 1.2F * grow, RIME, alpha * .12F);
        }
    }

    /**
     * Glacier Fall ({@code CryoUltimates.GlacierFall}): frost racing over the ground to the foe and climbing it into a
     * prism, the glacier's plan drawn overhead and its blocks growing into it with a sheen, the fall, the shattering
     * (prism and glacier together, shards thrown, a hexagonal rime ring) and the foe's warmth rising into the palm.
     */
    static void glacier(Canvas c, Active a, float t) {
        if (UltArt.cut(c, a)) return;
        Entity caster = a.bound(c), foe = UltArt.foe(c, a);
        Vec3 base = a.target(), facing = a.facing();
        float h = Math.max(.8F, a.scale());
        int seed = a.seed() * 11 + 5;
        int creep = com.jayptucker.heroesevolved.cryo.CryoUltimates.GlacierFall.CREEP,
                sealed = com.jayptucker.heroesevolved.cryo.CryoUltimates.GlacierFall.SEALED,
                plan = com.jayptucker.heroesevolved.cryo.CryoUltimates.GlacierFall.PLAN,
                built = com.jayptucker.heroesevolved.cryo.CryoUltimates.GlacierFall.BUILT,
                drop = com.jayptucker.heroesevolved.cryo.CryoUltimates.GlacierFall.DROP,
                impact = com.jayptucker.heroesevolved.cryo.CryoUltimates.GlacierFall.IMPACT,
                warmth = com.jayptucker.heroesevolved.cryo.CryoUltimates.GlacierFall.WARMTH;
        // Frost across the ground to the foe: a line of rime with feathers growing off it.
        float frost = UltArt.window(t, 0, impact + 4, 1, 6);
        if (frost > 0) {
            Vec3 from = a.origin().add(0, .03, 0), to = base.add(0, .03, 0);
            float reach = smooth(0, creep, t);
            frostLine(c, from, from.lerp(to, reach), .05F, frost, frost);
            int feathers = minimal() ? 3 : 6;
            for (int i = 0; i < feathers; i++) {
                float u = (i + .5F) / feathers;
                if (u > reach) break;
                feather(c, from.lerp(to, u), UP, .6F, smooth(u * creep, u * creep + 6, t), frost * .8F, seed + i);
            }
        }
        // The prism round the foe, climbing from its feet.
        float sealedK = smooth(creep - 4, sealed, t), standing = 1 - smooth(impact, impact + 1, t);
        if (sealedK > 0 && standing > 0) prism(c, foe != null ? foe.getPosition(c.partial) : base, h, sealedK, standing, seed + 20);
        // The glacier overhead: its plan, then the blocks growing in, a sheen across it, then the fall.
        Vec3 top = base.add(0, com.jayptucker.heroesevolved.cryo.CryoUltimates.GlacierFall.GLACIER, 0);
        float fall = com.jayptucker.heroesevolved.cryo.CryoUltimates.GlacierFall.fall(t);
        Vec3 g = top.lerp(base.add(0, h + 1.2, 0), fall);
        float present = UltArt.window(t, plan, impact + 1, 2, 1);
        if (present > 0) {
            int blocks = 7;
            for (int i = 0; i < blocks; i++) {
                float ang = i * TWO_PI / (blocks - 1), r = i == 0 ? 0 : 1.6F;
                Vec3 at = g.add(cos(ang) * r, (i == 0 ? .4 : -.1) + hash(seed, i + 40) * .5, sin(ang) * r);
                float start = plan + 4 + i * 2, grown = smooth(start, start + 5, t);
                float sx = .9F + .4F * hash(seed, i + 50), sy = .8F + .5F * hash(seed, i + 60), sz = .9F + .4F * hash(seed, i + 70);
                block(c, at, sx, sy, sz, ang + hash(seed, i) * .5F, grown, smooth(plan, plan + 3, t) * (1 - grown), present);
                if (t >= start && t < start + 5 && c.glow()) c.glow(at, 1.1F * (1 - smooth(start, start + 5, t)), CORE, present);
            }
            // The sheen once it is whole.
            if (t >= built && t < built + 6 && c.glow()) {
                float s = smooth(built, built + 6, t);
                c.line(g.add(-2.6 + 5.2 * s, -1.2, -2), g.add(-2.6 + 5.2 * s, 1.6, 2), .18F, CORE, (1 - s) * present, (1 - s) * present);
            }
        }
        // The shattering.
        if (t >= impact) {
            float age = t - impact, f = fade(age, a.piece().ticks - impact, 0, 14);
            Vec3 at = base.add(0, h * .5, 0);
            for (int i = 0; i < 6; i++) {
                float ang = i * TWO_PI / 6 + hash(seed, i + 80);
                chips(c, at, new Vec3(cos(ang), .6, sin(ang)), 2.2F, age, f, seed + i * 9);
            }
            hexRing(c, base.add(0, .05, 0), UP, .6F + 6.5F * smooth(0, 12, age), .06F, age * .02F, f * (1 - smooth(8, 20, age)));
            if (c.soft() && age < 20) for (int i = 0; i < (minimal() ? 3 : 7); i++) {
                float ang = i * TWO_PI / 7 + hash(seed, i + 90);
                double d = 1 + 3.5 * (1 - Math.exp(-age / 6));
                c.cloud(base.add(cos(ang) * d, .5 + age * .03, sin(ang) * d), 1, .6F, 1, i, ang, .5F * f * (1 - age / 20), frost(c));
            }
            if (age < 4 && c.glow()) c.glow(at, 3, CORE, 1 - age / 4);
            snowfall(c, base, 4, 5, .01F, f * .8F, seed + 99);
        }
        // The foe's warmth drawn up out of the wreck into the palm.
        if (t >= warmth && t < warmth + 26 && caster instanceof Player p && c.glow()) {
            float k = smooth(warmth, warmth + 20, t), f = 1 - smooth(warmth + 20, warmth + 26, t);
            Vec3 to = palm(c, p, 1), from = base.add(0, .6, 0);
            Vec3 ember = from.lerp(to, k).add(0, sin(k * PI) * 1.2, 0);
            c.glow(ember, .3F, WARM, f);
            c.glow(ember, .12F, WARM_CORE, f);
            c.line(from.lerp(to, Math.max(0, k - .25F)).add(0, sin(Math.max(0, k - .25F) * PI) * 1.2, 0), ember, .05F, WARM, 0, f * .6F);
        }
    }

    /**
     * Winter Citadel, one second (re-sent each second; the second in the seed, -1 its bursting): ice walls in a ring
     * round the scale's radius, grown from the ground on the first second, snow falling inside; the burst throws shards
     * out from every wall.
     */
    static void citadel(Canvas c, Active a, float t) {
        int second = a.seed();
        Vec3 centre = a.origin();
        float r = a.scale();
        int walls = com.jayptucker.heroesevolved.cryo.CryoUltimates.WinterCitadel.WALLS;
        if (second < 0) {
            float f = 1 - smooth(4, a.piece().ticks, t);
            for (int i = 0; i < walls; i++) {
                float ang = i * TWO_PI / walls;
                chips(c, centre.add(cos(ang) * r, 1.2, sin(ang) * r), new Vec3(cos(ang), .4, sin(ang)), 2.4F, t, f, i * 13 + 5);
            }
            hexRing(c, centre.add(0, .05, 0), UP, r + 4 * smooth(0, 12, t), .06F, 0, f);
            return;
        }
        boolean last = second >= com.jayptucker.heroesevolved.cryo.CryoUltimates.WinterCitadel.TICKS / 20 - 1;
        float f = Math.clamp(t / 2 + (second > 0 ? .5F : 0), 0, 1) * (last ? 1 - smooth(10, 22, t) : Math.clamp((a.piece().ticks - t) / 2 + .3F, 0, 1));
        float grow = second == 0 ? smooth(0, 12, t) : 1;
        for (int i = 0; i < walls; i++) {
            float ang = (i + .5F) * TWO_PI / walls, h = 2.6F + .6F * hash(i, 31);
            Vec3 foot = centre.add(cos(ang) * r, 0, sin(ang) * r);
            // A slab standing tangent to the ring, with a crest of shards along its top.
            if (c.soft()) c.box(foot.add(0, h * .5 * grow, 0), 1.2F, h * .5F * grow, .3F, -ang + HALF_PI, 0, 0, ICE, f * .55F);
            crystal(c, foot.add(0, h * grow * .8, 0), foot.add(0, (h + .7) * grow, 0), .25F, 4, ang, f);
            if (c.glow()) c.line(foot.add(0, .05, 0), foot.add(0, h * grow, 0), .04F, CORE, f * .5F, f * .2F);
        }
        hexRing(c, centre.add(0, .05, 0), UP, r, .05F, c.time() * .005F, f * .7F);
        snowfall(c, centre, r * .9F, 4, .01F, f, 7);
    }

    /** A shot shattering on an ice wall: chips thrown back off it. */
    static void wallShard(Canvas c, Active a, float t) {
        Vec3 d = a.origin().subtract(a.target());
        chips(c, a.origin(), d, 1.2F, t, fade(t, a.piece().ticks, 0, 6), a.seed());
    }

    /** Hibernation, one second (re-sent each second; the second in the seed): soft snow over the scale's radius and rime on the ground. */
    static void hibernation(Canvas c, Active a, float t) {
        int second = a.seed();
        boolean last = second >= com.jayptucker.heroesevolved.cryo.CryoUltimates.Hibernation.TICKS / 20 - 1;
        float f = Math.clamp(t / 2 + (second > 0 ? .5F : 0), 0, 1) * (last ? 1 - smooth(10, 22, t) : Math.clamp((a.piece().ticks - t) / 2 + .3F, 0, 1));
        float grow = second == 0 ? smooth(0, 16, t) : 1;
        Vec3 centre = a.origin();
        snowfall(c, centre, a.scale() * grow, 7, .006F, f, 13);
        snowfall(c, centre, a.scale() * .6F * grow, 5, -.008F, f, 14);
        hexRing(c, centre.add(0, .05, 0), UP, a.scale() * grow, .04F, 0, f * .5F);
        if (c.soft()) c.disc(centre.add(0, .03, 0), UP, a.scale() * grow, RIME, f * .12F, f * .03F);
    }

    /** A friend armoured in frost by Hibernation (bound; scale their height): feathers of frost over the body, settling to a glint. */
    static void frostArmour(Canvas c, Active a, float t) {
        Entity body = a.bound(c);
        if (body == null) return;
        Vec3 feet = body.getPosition(c.partial);
        float h = Math.max(.6F, a.scale()), f = fade(t, a.piece().ticks, 2, 12), grow = smooth(0, 10, t);
        Vec3 chest = feet.add(0, h * .62, 0);
        Vec3 look = body.getViewVector(c.partial).multiply(1, 0, 1);
        if (look.lengthSqr() < 1e-4) look = new Vec3(0, 0, 1);
        feather(c, chest.add(look.normalize().scale(.3)), look, .45F, grow, f, a.seed());
        hexRing(c, chest, UP, .55F * grow, .025F, t * .05F, f);
    }

    /** A friend sealed in Hibernation's crystal cocoon (bound; scale their height), melting open in a warm glow. */
    static void cocoon(Canvas c, Active a, float t) {
        Entity body = a.bound(c);
        Vec3 feet = body != null ? body.getPosition(c.partial) : a.origin();
        float h = Math.max(.6F, a.scale()), life = a.piece().ticks;
        float close = smooth(0, 8, t), melt = smooth(life - 12, life, t), f = 1 - melt;
        if (c.soft()) c.shell(feet.add(0, h * .5, 0), .75F * close, h * .62F * close, .75F * close, PI, ICE, f * .35F, .4F, PLAIN, 0);
        prism(c, feet, h * 1.05F, close, f, a.seed() + 3);
        if (c.glow() && melt > 0) c.glow(feet.add(0, h * .5, 0), h * .9F, WARM, melt * (1 - melt) * 2);
    }
}
