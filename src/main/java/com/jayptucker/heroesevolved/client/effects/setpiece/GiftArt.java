package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.ClientGifted;
import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.gifted.MobMoves;
import com.jayptucker.heroesevolved.mimicry.MimicColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.UP;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.hash;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.rgb;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.smooth;
import static net.minecraft.util.Mth.TWO_PI;
import static net.minecraft.util.Mth.cos;
import static net.minecraft.util.Mth.sin;

/**
 * Gifted mobs ({@code gifted/}). One idea: a sigil over the head in the power's own colour. A guardian wears a halo of
 * pale gold; an evil gift wears a broken crown of crimson thorns. Its rarity is the number of beads on the sigil, and a
 * mini-boss has a second ring turning the other way. While it winds a move up, a ring at its feet fills in its power's
 * colour (full when the move lands) and its hands glow where the move will leave them; unbound, flames climb it.
 * <p>
 * Pieces: {@code GIFT_AWAKEN} (light spirals up the body and closes into the sigil), {@code GIFT_TURN} (crimson cracks
 * climb a villager, a ring closes in on them, and the halo breaks into thorns) and {@code GIFT_UNBOUND} (rings burst
 * along the ground, a pillar flares and the crown throws bolts).
 * <p>
 * Palette: the power's colour ({@code MimicColors}), {@code FFE7A0} halo gold, {@code D6283A} crimson, {@code 8E1020}
 * deep crimson, {@code FFF8EC} core, {@code 1A0A10} shadow (normal blending).
 */
final class GiftArt {
    private static final float[] GOLD = rgb(0xFFE7A0), CRIMSON = rgb(0xD6283A), DEEP = rgb(0x8E1020), CORE = rgb(0xFFF8EC),
            SHADOW = rgb(0x1A0A10), HALO = rgb(0xD9A93A);
    /** How far off a sigil is still drawn. */
    private static final double RANGE = 48;

    private GiftArt() {}

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }

    private static float[] power(int index) { return rgb(MimicColors.color(index)); }

    // ------------------------------------------------------------------
    // Live: every gifted mob's sigil, cast and flames
    // ------------------------------------------------------------------

    static boolean live() { return ClientGifted.any(); }

    static void drawLive(Canvas c) {
        if (c.level == null) return;
        long now = c.level.getGameTime();
        float clock;
        boolean minimal = minimal();
        for (Map.Entry<Integer, ClientGifted.State> e : ClientGifted.all().entrySet()) {
            Entity entity = c.level.getEntity(e.getKey());
            if (!(entity instanceof LivingEntity body) || !body.isAlive() || body.isInvisible()) continue;
            Vec3 feet = body.getPosition(c.partial);
            if (feet.distanceToSqr(c.cx, c.cy, c.cz) > RANGE * RANGE) continue;
            ClientGifted.State s = e.getValue();
            clock = (float) com.jayptucker.heroesevolved.client.ClientMobTime.elapsed(entity.getId(), 0, c.partial) % 24000;
            float[] p = power(s.power());
            float dim = s.spent() ? .4F : 1;
            sigil(c, body, feet, s, p, clock, dim, minimal);
            if (s.casting()) cast(c, body, feet, s, p, s.castProgress(now, c.partial), clock, minimal);
            if (s.unbound() && !minimal) flames(c, body, feet, s, p, clock, dim);
        }
    }

    /**
     * The sigil: a halo floating over a guardian, or a thorned crown sitting on an evil gift's head, with beads for its
     * rarity. Its body is drawn with normal blending (the SOFT pass) so its colour holds against a bright sky; additive
     * light only rims it, beads it and tips the thorns. Additive crimson alone washed out to pink at noon.
     */
    private static void sigil(Canvas c, LivingEntity body, Vec3 feet, ClientGifted.State s, float[] p, float clock, float dim, boolean minimal) {
        boolean evil = s.evil();
        Vec3 head = feet.add(0, body.getBbHeight() + (evil ? .08 : .32), 0);
        // Staggered, the sigil wobbles.
        Vec3 axis = s.staggered() ? new Vec3(sin(clock * .9F) * .45, 1, cos(clock * .7F) * .45) : UP;
        float spin = clock * .05F, r = evil ? .27F : .3F;
        int thorns = 5 + s.rarity() * 2;
        if (c.soft()) {
            if (evil) {
                c.ring(head, axis, r, .05F, 0, TWO_PI, DEEP, .9F * dim);
                if (!minimal)
                    for (int i = 0; i < thorns; i++) {
                        Vec3[] thorn = thorn(head, r, spin, i, thorns, body.getId());
                        c.line(thorn[0], thorn[1], .05F, DEEP, .95F * dim, .6F * dim);
                    }
            } else c.ring(head, axis, r, .05F, 0, TWO_PI, HALO, .75F * dim);
            if (s.spent())
                for (int i = 0; i < 3; i++) {
                    float a = i * 2.1F + body.getId();
                    c.line(head.add(cos(a) * r, 0, sin(a) * r), head.add(cos(a + .5F) * r * .6, .05, sin(a + .5F) * r * .6), .02F, SHADOW, .7F, .3F);
                }
            return;
        }
        if (!c.glow()) return;
        c.ring(head, axis, r, .13F, 0, TWO_PI, p, .22F * dim);
        c.ring(head, axis, r, .03F, 0, TWO_PI, evil ? CRIMSON : GOLD, .7F * dim);
        c.ring(head, axis, r, .012F, 0, TWO_PI, CORE, .8F * dim);
        int beads = s.rarity() + 1;
        for (int i = 0; i < beads; i++) {
            float a = spin + i * TWO_PI / beads;
            c.glow(head.add(cos(a) * r, 0, sin(a) * r), .09F, p, .95F * dim);
        }
        if (s.boss() && !minimal) c.ring(head.add(0, .06, 0), axis, r + .14F, .025F, -spin * 2, -spin * 2 + TWO_PI * .7F, p, .55F * dim);
        if (evil && !minimal) {
            // White-hot tips, a breath of the power's colour round each.
            for (int i = 0; i < thorns; i++) {
                Vec3[] thorn = thorn(head, r, spin, i, thorns, body.getId());
                c.line(thorn[0], thorn[1], .014F, CRIMSON, .5F * dim, .7F * dim);
                c.glow(thorn[1], .08F, p, .35F * dim);
                c.glow(thorn[1], .04F, CORE, .7F * dim);
            }
        } else if (!evil) c.glow(head, .6F, GOLD, .18F * dim);
    }

    /** One thorn of the crown: its base on the ring and its tip, tilted out, each its own height. */
    private static Vec3[] thorn(Vec3 head, float r, float spin, int i, int thorns, int seed) {
        float a = spin * .5F + i * TWO_PI / thorns, h = .14F + .09F * hash(seed, i);
        Vec3 base = head.add(cos(a) * r, 0, sin(a) * r);
        return new Vec3[] {base, base.add(cos(a) * .06, h, sin(a) * .06)};
    }

    /**
     * A move winding up: a ring at the feet fills clockwise in the power's colour (full as it lands), sparks climb off
     * it, and the hands glow where the move will leave them.
     */
    private static void cast(Canvas c, LivingEntity body, Vec3 feet, ClientGifted.State s, float[] p, float k, float clock, boolean minimal) {
        if (!c.glow()) return;
        Vec3 ground = feet.add(0, .06, 0);
        float r = .9F + .12F * s.rarity(), start = -Mth.HALF_PI;
        c.ring(ground, UP, r, .05F, 0, TWO_PI, p, .18F);
        c.ring(ground, UP, r, .07F, start, start + TWO_PI * k, p, .75F);
        c.ring(ground, UP, r, .02F, start, start + TWO_PI * k, CORE, .9F);
        for (int i = 0; i < 4; i++) c.spoke(ground, UP, start + i * Mth.HALF_PI, r - .18F, r + .18F, .03F, p, .5F + .4F * k);
        if (!minimal)
            for (int i = 0; i < 3; i++) {
                float a = hash(body.getId(), i) * TWO_PI + clock * .03F, rise = ((clock * .05F + i / 3F) % 1);
                Vec3 at = ground.add(cos(a) * r, rise * 1.6, sin(a) * r);
                c.line(at, at.add(0, .3, 0), .03F, p, .6F * (1 - rise), 0);
            }
        float yaw = Mth.rotLerp(c.partial, body.yBodyRotO, body.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-sin(yaw), 0, cos(yaw)), right = new Vec3(-cos(yaw), 0, -sin(yaw));
        float h = body.getBbHeight(), glow = .18F + .3F * k, alpha = .45F + .45F * k;
        MobMoves.Pose pose = MobMoves.Pose.values()[Math.clamp(s.pose(), 0, MobMoves.Pose.values().length - 1)];
        switch (pose) {
            case CAST -> {
                Vec3 up = feet.add(0, h * .98, 0);
                c.glow(up.add(right.scale(.5)), glow, p, alpha);
                c.glow(up.subtract(right.scale(.5)), glow, p, alpha);
            }
            case POINT -> c.glow(feet.add(0, h * .72, 0).add(forward.scale(.6)).add(right.scale(.3)), glow, p, alpha);
            case SLAM -> c.glow(feet.add(0, h + .25, 0).add(forward.scale(.2)), glow * 1.3F, p, alpha);
            default -> c.glow(feet.add(0, h * .6, 0).add(forward.scale(.35)), glow, p, alpha);
        }
    }

    /** Unbound: two strands of flame climb it, its power's colour and crimson (or gold, for a guardian). */
    private static void flames(Canvas c, LivingEntity body, Vec3 feet, ClientGifted.State s, float[] p, float clock, float dim) {
        if (!c.glow()) return;
        float h = body.getBbHeight(), w = body.getBbWidth() * .75F, pulse = .75F + .25F * sin(clock * .4F);
        c.helix(feet, h + .2F, w, w * .55F, 1.5F, clock * .12F, .06F, p, .5F * pulse * dim, 24);
        c.helix(feet, h + .1F, w * .9F, w * .5F, 1.5F, clock * .12F + Mth.PI, .05F, s.evil() ? CRIMSON : GOLD, .45F * pulse * dim, 24);
        c.glow(feet.add(0, h * .55, 0), h * .7F, p, .14F * pulse * dim);
    }

    // ------------------------------------------------------------------
    // Pieces
    // ------------------------------------------------------------------

    static void draw(Canvas c, Active a, float t) {
        switch (a.piece()) {
            case GIFT_AWAKEN -> awaken(c, a, t);
            case GIFT_TURN -> turn(c, a, t);
            case GIFT_UNBOUND -> unbound(c, a, t);
            default -> {}
        }
    }

    /** A gift waking: light spirals up the body from a ring on the ground, flashes at the head, and becomes the sigil. */
    private static void awaken(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        int index = (int) Math.floor(a.scale());
        boolean evil = a.scale() - index >= .5F;
        float[] p = power(index), ring = evil ? CRIMSON : GOLD;
        Entity e = a.bound(c);
        Vec3 feet = e != null ? e.getPosition(c.partial) : a.origin();
        float h = e != null ? e.getBbHeight() : 1.95F, life = a.piece().ticks;
        float out = 1 - smooth(life - 6, life, t);
        float rise = smooth(0, 14, t);
        // The ground ring draws in as the light leaves it.
        c.ring(feet.add(0, .06, 0), UP, 1.3F - .9F * rise, .08F, 0, TWO_PI, p, .6F * (1 - rise) + .05F);
        if (t < 18) {
            float fade = 1 - smooth(14, 18, t);
            c.helix(feet, (h + .35F) * rise, .9F, .32F, 2.5F, t * .35F, .07F, p, .8F * fade, 32);
            c.helix(feet, (h + .35F) * rise, .9F, .32F, 2.5F, t * .35F + Mth.PI, .025F, CORE, .9F * fade, 32);
        }
        Vec3 head = feet.add(0, h + .32, 0);
        if (t > 12) {
            float flash = smooth(12, 15, t) * (1 - smooth(15, 22, t));
            c.glow(head, 1.4F * flash + .2F, CORE, .9F * flash);
            float form = smooth(14, 20, t);
            c.ring(head, UP, .3F * form, .05F, 0, TWO_PI, ring, .8F * out);
            float wave = smooth(14, 30, t);
            c.ring(head, UP, .3F + 2.6F * wave, .06F, 0, TWO_PI, p, .5F * (1 - wave) * out);
        }
    }

    /**
     * A villager turning: crimson cracks climb them and scorch spreads under them, a ring closes in on them (the gift
     * swallowing them), a flash hides the moment they become an illager, and a crown of thorns bursts up and settles.
     */
    private static void turn(Canvas c, Active a, float t) {
        Vec3 feet = a.origin();
        int index = (int) Math.floor(Math.max(0, a.scale()));
        float[] p = power(index);
        float life = a.piece().ticks, out = 1 - smooth(life - 8, life, t);
        int seed = a.seed() >>> 3;
        if (c.soft()) {
            float scorch = smooth(0, 20, t);
            c.disc(feet.add(0, .05, 0), UP, .4F + 1.6F * scorch, SHADOW, .45F * out, 0);
            return;
        }
        if (!c.glow()) return;
        float climb = smooth(0, 20, t);
        for (int i = 0; i < 6; i++) {
            float ang = i * TWO_PI / 6 + hash(seed, i);
            Vec3 base = feet.add(cos(ang) * .32, .05, sin(ang) * .32);
            Vec3 top = base.add(cos(ang + .4F) * .06, .2 + 1.7 * climb * (.7 + .3 * hash(seed, i + 9)), sin(ang + .4F) * .06);
            c.bolt(base, top, seed + i + (int) (t / 3), .12F, .035F, i % 2 == 0 ? CRIMSON : DEEP, .8F * out);
        }
        Vec3 chest = feet.add(0, 1, 0);
        c.glow(chest, .6F + 1.4F * climb, DEEP, .3F * out);
        // The ring closes inward: something is taken, not given.
        float close = smooth(10, 22, t);
        if (t < 24) c.ring(feet.add(0, .08, 0), UP, 3F - 2.7F * close, .1F, 0, TWO_PI, CRIMSON, .7F * (1 - smooth(20, 24, t)));
        // The flash the body changes under (Renegades.TURN_TICKS, 21): bright enough to hide the swap.
        float flash = smooth(17, 21, t) * (1 - smooth(21, 28, t));
        if (flash > 0) {
            c.glow(chest, 2.2F * flash + .2F, CRIMSON, .85F * flash);
            c.glow(chest, 1.3F * flash, CORE, .9F * flash);
        }
        // The crown: thorns burst up from the head and settle.
        if (t > 20) {
            float burst = smooth(20, 26, t), settle = smooth(26, 34, t);
            Vec3 head = feet.add(0, 2.03, 0);
            c.glow(head, 1.6F * (1 - settle) + .3F, CRIMSON, .7F * (1 - settle * .7F) * out);
            for (int i = 0; i < 9; i++) {
                float ang = i * TWO_PI / 9 + hash(seed, i + 30);
                float reach = .3F + (1.4F - 1.1F * settle) * burst;
                Vec3 base = head.add(cos(ang) * .3, 0, sin(ang) * .3);
                c.line(base, base.add(cos(ang) * reach * .4, reach, sin(ang) * reach * .4), .04F, CRIMSON, .8F * out, .1F * out);
            }
            c.ring(head, UP, .3F, .05F, 0, TWO_PI, CRIMSON, .8F * burst * out);
            float wave = smooth(22, 40, t);
            c.ring(feet.add(0, .08, 0), UP, .5F + 5F * wave, .08F, 0, TWO_PI, p, .5F * (1 - wave));
        }
    }

    /** Unbound: three rings burst along the ground one after another, a pillar flares, the crown throws bolts upward. */
    private static void unbound(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        int index = (int) Math.floor(a.scale());
        float[] p = power(index);
        Entity e = a.bound(c);
        Vec3 feet = e != null ? e.getPosition(c.partial) : a.origin();
        float h = e != null ? e.getBbHeight() : 1.95F, life = a.piece().ticks;
        int seed = a.seed() >>> 3;
        for (int i = 0; i < 3; i++) {
            float k = smooth(i * 4, i * 4 + 16, t);
            if (k <= 0 || k >= 1) continue;
            float r = .6F + 6.5F * k;
            c.ring(feet.add(0, .07, 0), UP, r, .2F, 0, TWO_PI, p, .22F * (1 - k));
            c.ring(feet.add(0, .07, 0), UP, r, .07F, 0, TWO_PI, i == 1 ? CRIMSON : p, .7F * (1 - k));
            c.ring(feet.add(0, .07, 0), UP, r, .02F, 0, TWO_PI, CORE, .8F * (1 - k));
        }
        float pillar = smooth(0, 4, t) * (1 - smooth(10, 24, t));
        c.line(feet, feet.add(0, 14, 0), .5F * pillar + .05F, p, .45F * pillar, 0);
        c.line(feet, feet.add(0, 10, 0), .14F, CORE, .7F * pillar, 0);
        Vec3 head = feet.add(0, h + .32, 0);
        float bolts = 1 - smooth(8, 22, t);
        for (int i = 0; i < 5; i++) {
            float ang = i * TWO_PI / 5 + hash(seed, i);
            c.bolt(head, head.add(cos(ang) * 1.2, 2.5 + hash(seed, i + 7) * 2, sin(ang) * 1.2), seed + i + (int) (t / 2), .3F, .04F,
                    i % 2 == 0 ? CRIMSON : p, .8F * bolts);
        }
        c.glow(feet.add(0, h * .55, 0), 2.4F * (1 - smooth(4, life, t)), p, .35F);
    }
}
