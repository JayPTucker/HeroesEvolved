package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.combat.BossLocks;
import com.jayptucker.heroesevolved.endgame.*;
import com.jayptucker.heroesevolved.endgame.SealGeometry.Move;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/**
 * The Living Seal, drawn in one idea: the Seal closing. Three violet rings orbit its chest; every warning is an orbit of
 * rings that converges and seats on its mark exactly as the hit lands (the fill runs the ticks the server put in the
 * seed); every weakness opened is a white fracture. Ground marks follow the terrain (EndBattleArt's lanes and sectors).
 * Knots, their beams, the drag on its wings and the open star come from the synced {@link SealView}.
 */
final class SealArt {
    static final float[] SEAL = rgb(0xA78AF2), ROOM = rgb(0xA6F3E8), FRACTURE = rgb(0xF0F2DC), EMBER = rgb(0xFF5A6E),
            VOID = rgb(0x1B1030), BREATH = rgb(0xD35BFF), GOLD = rgb(0xFFD58A);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private SealArt() {}

    static void draw(Canvas c, Active a, float t) {
        switch (a.piece()) {
            case END_SEAL_WARNING -> warning(c, a, t);
            case END_SEAL_KEY -> key(c, a, t);
            case END_SEAL_CRACK -> crack(c, a, t);
            case END_SEAL_SHATTER -> shatter(c, a, t);
            case END_SEAL_SPARED -> spared(c, a, t);
            default -> {}
        }
    }

    // ------------------------------------------------------------------ warnings

    private static float[] tint(Move move) {
        return switch (move) {
            case BITE, TAIL, RUSH, RIFTDIVE -> EMBER;
            case BREATH, SCORCH -> BREATH;
            case DESCENT, ROAR, LEAP -> GOLD;
            case HARPOON -> ROOM;
            default -> SEAL;
        };
    }
    private static void warning(Canvas c, Active a, float t) {
        Move move = SealFight.seedMove(a.seed());
        if (move == null) return;
        boolean flash = SealFight.seedRole(a.seed()) == 1;
        int fill = Math.max(1, SealFight.seedFill(a.seed()));
        if (t < 0 || (flash ? t > 12 : t > fill + 1)) return;
        float p = flash ? 1 : clamp(t / fill, 0, 1), alpha = flash ? 1 - t / 12F : smooth(0, 3, t);
        float[] tint = flash ? FRACTURE : tint(move);
        Vec3 o = a.origin(), to = a.target();
        switch (move) {
            case RIFTDIVE -> {
                EndBattleArt.strip(c, o, to, SealGeometry.DIVE_RADIUS, p, tint, alpha, true);
                Vec3 mid = o.lerp(to, .5);
                seat(c, mid, SealGeometry.DIVE_IMPACT, p, tint, alpha, flash);
                if (!flash && c.glow()) { Vec3 g = EndBattleArt.ground(c, mid); c.line(g.add(0, 26, 0), g.add(0, .2, 0), .06F + .12F * p, EMBER, alpha * .15F, alpha * .8F); }
            }
            case BREATH -> EndBattleArt.strip(c, o, to, SealGeometry.RUN_RADIUS, p, tint, alpha, true);
            case STARFALL -> {
                float r = a.scale();
                seat(c, to, r, p, tint, alpha, flash);
                if (!flash && c.glow()) {
                    // The shard's path from the rings on its chest to the mark.
                    Vec3 g = EndBattleArt.ground(c, to);
                    c.line(o, g.add(0, .3, 0), .025F, SEAL, alpha * .12F, alpha * .5F * p);
                }
            }
            case HARPOON -> {
                if (!c.glow()) return;
                Vec3 d = to.subtract(o), side = EndBattleArt.perpendicular(d);
                Vec3[] path = new Vec3[17];
                for (int i = 0; i < 17; i++) { float k = i / 16F; path[i] = o.lerp(to, k).add(side.scale(sin(k * PI) * sin(k * 9 + c.time() * .5F) * .5 * (1 - p))); }
                c.ribbon(path, flash ? .22F : .05F + .06F * p, tint, alpha * .6F, alpha);
                c.ring(to, d.normalize(), a.scale() * (2 - p), .05F, 0, TWO_PI, FRACTURE, alpha * p);
            }
            case DESCENT -> {
                seat(c, o, SealGeometry.LAND_RADIUS, p, tint, alpha, flash);
                if (!flash && c.glow()) {
                    Vec3 g = EndBattleArt.ground(c, o);
                    for (int i = 0; i < 6; i++) {
                        float ang = i * TWO_PI / 6 + c.time() * .01F;
                        c.line(g.add(cos(ang) * SealGeometry.LAND_RADIUS * 1.3, .15, sin(ang) * SealGeometry.LAND_RADIUS * 1.3),
                                g.add(cos(ang) * SealGeometry.LAND_RADIUS * (1 - p * .6), .15, sin(ang) * SealGeometry.LAND_RADIUS * (1 - p * .6)), .12F, GOLD, alpha * .2F, alpha * .9F);
                    }
                }
            }
            case BITE -> EndBattleArt.strip(c, o, to, SealGeometry.BITE_RADIUS, p, tint, alpha, true);
            case RUSH -> EndBattleArt.strip(c, o, to, SealGeometry.RUSH_RADIUS, p, tint, alpha, true);
            case TAIL -> EndBattleArt.sector(c, o, to.subtract(o), SealGeometry.TAIL_RADIUS, SealGeometry.TAIL_HALF, p, tint, alpha);
            case SCORCH -> EndBattleArt.sector(c, o, to.subtract(o), SealGeometry.SCORCH_RADIUS, SealGeometry.SCORCH_HALF, p, tint, alpha);
            case BUFFET -> {
                // Rings gather in toward the body; the shock rolls out after.
                if (c.glow()) for (int i = 0; i < 3; i++) { float r = 3 + (1 - ((p + i / 3F) % 1F)) * 12; EndBattleArt.ringOnGround(c, o, r, .09F, FRACTURE, alpha * .75F); }
                if (c.glow()) EndBattleArt.ringOnGround(c, o, 3, .12F, SEAL, alpha);
            }
            case ROAR -> seat(c, o, a.scale(), p, tint, alpha * .85F, flash);
            case TAKEOFF -> seat(c, o, a.scale(), p, tint, alpha * .8F, flash);
            case LEAP -> {
                seat(c, o, SealGeometry.LEAP_RADIUS, p, tint, alpha, flash);
                if (!flash && c.glow() && a.bound(c) != null) {
                    // Its arc from where it crouches to where it lands.
                    Vec3 from = a.bound(c).position(), g = EndBattleArt.ground(c, o);
                    Vec3[] arc = new Vec3[17];
                    for (int i = 0; i < 17; i++) { float k = i / 16F; arc[i] = from.lerp(g, k).add(0, 4 * SealGeometry.LEAP_HEIGHT * k * (1 - k) + .3, 0); }
                    c.ribbon(arc, .05F, GOLD, alpha * .2F, alpha * .7F * p);
                }
            }
            default -> {}
        }
    }
    /**
     * The Seal closing on a mark: a faint disc of the whole danger, three orbit rings converging from wide and high onto
     * its rim as {@code p} fills, a bright front sweeping out from the centre, and a white seat when it lands.
     */
    static void seat(Canvas c, Vec3 at, float radius, float p, float[] tint, float alpha, boolean flash) {
        EndBattleArt.sector(c, at, new Vec3(1, 0, 0), radius, PI, p, tint, alpha * (flash ? 1 : .9F));
        if (!c.glow()) return;
        Vec3 g = EndBattleArt.ground(c, at);
        if (flash) { EndBattleArt.ringOnGround(c, at, radius, .22F, FRACTURE, alpha); return; }
        for (int i = 0; i < 3; i++) {
            float open = (1 - p) * (.55F + .45F * i), r = radius * (1 + open), lift = open * radius * .5F;
            float spin = c.time() * (.02F + .01F * i) * (i % 2 == 0 ? 1 : -1);
            Vec3 axis = new Vec3(sin(spin) * open * .6, 1, cos(spin) * open * .6).normalize();
            c.ring(g.add(0, lift + .08, 0), axis, r, .05F + .03F * p, 0, TWO_PI, i == 0 ? FRACTURE : tint, alpha * (.35F + .55F * p));
        }
    }

    // ------------------------------------------------------------------ locks and windows

    /** An aimed power reaching a knot or dragging the Dragon down, and Riftbrand's drinking thread. */
    private static void key(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float fade = 1 - smooth(6, 18, t), in = smooth(0, 2, t);
        boolean strong = (a.seed() & (1 << 5)) != 0;
        float[] col = a.scale() > 1.5F ? ROOM : a.seed() == 0 ? SEAL : strong ? GOLD : ROOM;
        Vec3 from = a.origin(), to = a.target();
        Vec3 side = EndBattleArt.perpendicular(to.subtract(from));
        Vec3[] path = new Vec3[21];
        for (int i = 0; i < 21; i++) { float k = i / 20F; path[i] = from.lerp(to, k * in).add(side.scale(sin(k * PI) * sin(k * 7 + c.time() * .3F) * .25)); }
        c.ribbon(path, strong ? .09F : .06F, col, fade * .5F, fade);
        if (in >= 1) { c.ring(to, UP, .6F + t * .08F, .05F, 0, TWO_PI, FRACTURE, fade); c.glow(to, .5F, col, fade * .6F); }
    }
    /** A knot or its stance cracking: white fracture lines from the point. */
    private static void crack(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float fade = 1 - smooth(6, 16, t), size = a.scale() >= 2 ? 2.4F : 1.4F;
        int lines = 4 + Math.clamp(a.seed(), 0, 16) / 2;
        for (int i = 0; i < lines; i++) {
            float ang = i * TWO_PI / lines + hash(i, 3) * .6F, pitch = (hash(i, 7) - .5F) * 1.6F, len = size * (.5F + .5F * hash(i, 11)) * smooth(0, 4, t);
            Vec3 d = new Vec3(cos(ang) * cos(pitch), sin(pitch), sin(ang) * cos(pitch));
            Vec3 mid = a.origin().add(d.scale(len * .5)).add(EndBattleArt.perpendicular(d).scale((hash(i, 5) - .5) * .3));
            c.line(a.origin(), mid, .045F, FRACTURE, fade, fade * .8F);
            c.line(mid, a.origin().add(d.scale(len)), .03F, FRACTURE, fade * .8F, 0);
        }
        c.glow(a.origin(), size * .4F, a.scale() >= 3 ? EMBER : SEAL, fade * .7F);
    }
    /** The Seal's rings flying off its chest (a phase, a backfire in ember, the star cracking in gold). */
    private static void shatter(Canvas c, Active a, float t) {
        float[] col = a.scale() >= 3 ? GOLD : a.scale() >= 2 ? EMBER : SEAL;
        float fade = 1 - smooth(20, 46, t), out = smooth(0, 30, t);
        Vec3 at = a.origin();
        if (c.glow()) {
            for (int i = 0; i < 3; i++) {
                Vec3 axis = new Vec3(sin(i * 2.1F + t * .05F), 1, cos(i * 1.7F)).normalize();
                for (int s = 0; s < 4; s++) {
                    float from = s * HALF_PI + i, to = from + HALF_PI * .7F;
                    Vec3 drift = new Vec3(cos(from + .4F), .3 * (i - 1), sin(from + .4F)).scale(out * 6);
                    c.ring(at.add(drift), axis, 3.2F + i * .6F + out * 2, .07F, from, to, s % 2 == 0 ? FRACTURE : col, fade);
                }
            }
            c.glow(at, 2.5F * (1 - out) + .5F, FRACTURE, fade * .8F);
        }
        if (c.soft()) for (int i = 0; i < 12; i++) {
            float ang = i * TWO_PI / 12; Vec3 p = at.add(cos(ang) * (2 + out * 9), (hash(i, 2) - .5) * 4 * out, sin(ang) * (2 + out * 9));
            c.shard(p, p.add(cos(ang) * .8, .6, sin(ang) * .8), .3F, 3, ang + t * .1F, col, fade * .6F);
        }
    }
    /** A fighter a power carried out of The Unmaking. */
    private static void spared(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        Entity who = a.bound(c);
        Vec3 at = who == null ? a.origin() : who.position().add(0, 1, 0);
        float fade = 1 - smooth(10, 22, t);
        c.ring(at, UP, 1.2F + t * .05F, .06F, 0, TWO_PI, FRACTURE, fade);
        c.ring(at, new Vec3(1, 0, 0), 1F, .04F, 0, TWO_PI, GOLD, fade * .8F);
    }

    // ------------------------------------------------------------------ the body, the knots, the beams

    /** The local player's awakened power, for the glints only they see. */
    private static net.minecraft.resources.ResourceLocation localPower() {
        var snap = com.jayptucker.heroesevolved.network.ClientPowerState.getSnapshot();
        if (snap == null) return null;
        return snap.abilities().stream().filter(x -> x.unlocked()).map(x -> x.abilityId()).findFirst().orElse(null);
    }

    /**
     * Drawn with the field every few ticks: the Seal's rings round its chest (whole in the first phase, cracking as knots
     * break and in the second phase, broken round the open star in the third), the knots at the altars and the beams
     * they feed, the drag of a bring-down on its wings, and a glint on whatever the local player's power answers.
     */
    static void body(Canvas c, EnderDragon dragon, EndView v, float alpha) {
        SealView s = v.seal();
        float time = c.time();
        Vec3 chest = SealGeometry.chest(dragon);
        var power = localPower();
        int phase = v.phase();
        if (c.glow()) {
            // The Seal: three rings in orbit. In the first phase each knot standing keeps a quarter of them whole.
            int whole = phase == 1 && !s.has(SealView.SHATTERED) ? Math.round(s.warding() * 3 / 4F) : 0;
            for (int i = 0; i < 3; i++) {
                float spin = time * (.035F + .012F * i) * (i == 1 ? -1 : 1);
                Vec3 axis = new Vec3(sin(spin + i * 2.1F) * .55, 1, cos(spin * .7F + i) * .55).normalize();
                float r = 3.3F + i * .7F;
                if (i < whole) { c.ring(chest, axis, r, .07F, 0, TWO_PI, SEAL, alpha * .85F); c.ring(chest, axis, r, .02F, 0, TWO_PI, FRACTURE, alpha * .6F); }
                else {
                    int pieces = phase >= 3 ? 3 : 5;
                    for (int k = 0; k < pieces; k++) {
                        float from = k * TWO_PI / pieces + spin, to = from + TWO_PI / pieces * (phase >= 3 ? .45F : .7F);
                        c.ring(chest, axis, r + (phase >= 3 ? .4F * sin(time * .1F + k) : 0), .05F, from, to, phase >= 3 ? EMBER : SEAL, alpha * .7F);
                    }
                }
            }
            if (phase >= 3) {
                // The open star in its chest.
                float beat = .7F + .3F * sin(time * .35F);
                c.glow(chest, 1.1F * beat, GOLD, alpha * .8F);
                c.glow(chest, .45F, FRACTURE, alpha);
                for (int i = 0; i < 4; i++) { Vec3 d = new Vec3(cos(i * HALF_PI + time * .02F), 0, sin(i * HALF_PI + time * .02F)).scale(1.4 * beat); c.line(chest.subtract(d), chest.add(d), .04F, GOLD, 0, alpha * .8F); }
                BossLocks.Key key = power == null ? null : BossLocks.key(power, BossLocks.Lock.WEAK_POINT);
                if (key != null) glint(c, chest, 1.6F, key, alpha * .8F, time);
            }
            if (s.drag() > .01F) {
                // A bring-down's hold on its wings: a ring filling round it, chains reaching for the ground.
                c.ring(chest, UP, 6, .08F, -HALF_PI, -HALF_PI + TWO_PI * s.drag(), ROOM, alpha);
                for (int i = 0; i < 4; i++) { float ang = i * HALF_PI + time * .01F; Vec3 at = chest.add(cos(ang) * 6, 0, sin(ang) * 6); c.line(at, at.add(0, -6 * s.drag(), 0), .04F, ROOM, alpha * .7F, 0); }
            }
            if (s.has(SealView.AIRBORNE) && phase <= 2 && power != null) {
                BossLocks.Key key = BossLocks.key(power, BossLocks.Lock.OUT_OF_REACH);
                if (key != null) glint(c, chest.add(0, 3.5, 0), 1.2F, key, alpha * .7F, time);
            }
            if (s.has(SealView.STAGGERED) || s.crash() > 0) for (int i = 0; i < 6; i++) {
                float ang = i * TWO_PI / 6 + time * .05F;
                c.line(chest.add(cos(ang) * 2.5, 2.2, sin(ang) * 2.5), chest.add(cos(ang + .5F) * 2.9, 2.6, sin(ang + .5F) * 2.9), .06F, FRACTURE, alpha * .9F, alpha * .3F);
            }
        }
        for (int i = 0; i < s.knots().size(); i++) knot(c, s, i, chest, power, alpha, time);
    }
    private static void knot(Canvas c, SealView s, int i, Vec3 chest, net.minecraft.resources.ResourceLocation power, float alpha, float time) {
        int state = s.knots().get(i);
        if (state == SealView.NONE) return;
        Vec3 at = s.at().get(i).add(0, .6, 0);
        float health = s.health().get(i);
        float[] col = state == SealView.WARDING ? SEAL : state == SealView.FEEDING ? EMBER : ROOM;
        float bright = state == SealView.DORMANT ? .45F : 1;
        if (c.solid()) c.shard(at.add(0, -.55, 0), at.add(0, .55, 0), .45F, 5, time * .03F + i, col, alpha * bright);
        if (!c.glow()) return;
        c.shard(at.add(0, -.55, 0), at.add(0, .55, 0), .45F, 5, time * .03F + i, mix(col, FRACTURE, .3F), alpha * bright * .8F);
        for (int k = 0; k < 3; k++) {
            float spin = time * (.06F + .02F * k) * (k == 1 ? -1 : 1);
            Vec3 axis = new Vec3(sin(spin + k * 2.1F), .7, cos(spin + k)).normalize();
            c.ring(at, axis, .95F + k * .2F, .035F, 0, TWO_PI, col, alpha * bright * .8F);
        }
        c.glow(at, .9F, col, alpha * bright * .55F);
        // Cracks across it as it weakens.
        int cracks = Math.round((1 - health) * 6);
        for (int k = 0; k < cracks; k++) { float ang = k * 1.9F + i; c.line(at, at.add(cos(ang) * .7, sin(ang * 1.3F) * .7, sin(ang) * .7), .03F, FRACTURE, alpha, alpha * .2F); }
        if (state == SealView.WARDING) {
            // Its thread into the Seal on the chest.
            Vec3 side = EndBattleArt.perpendicular(chest.subtract(at));
            Vec3[] path = new Vec3[17];
            for (int k = 0; k < 17; k++) { float f = k / 16F; path[k] = at.lerp(chest, f).add(0, sin(f * PI) * 6, 0).add(side.scale(sin(f * TWO_PI * 2 + time * .1F) * .3)); }
            c.ribbon(path, .045F, SEAL, alpha * .35F, alpha * .55F);
        } else if (state == SealView.FEEDING) {
            // It drinks: a thick beam pouring from the knot into its chest.
            float pulse = .7F + .3F * sin(time * .5F);
            Vec3[] path = new Vec3[17];
            for (int k = 0; k < 17; k++) { float f = k / 16F; path[k] = at.lerp(chest, f).add(0, sin(f * PI) * 4, 0); }
            c.ribbon(path, .22F * pulse, EMBER, alpha * .5F, alpha * .9F);
            c.ribbon(path, .06F, FRACTURE, alpha, alpha);
        }
        BossLocks.Lock lock = state == SealView.WARDING ? BossLocks.Lock.SHIELD : state == SealView.FEEDING ? BossLocks.Lock.ROOM_WEAPON : null;
        BossLocks.Key key = lock == null || power == null ? null : BossLocks.key(power, lock);
        if (key != null) glint(c, at, .85F, key, alpha, time);
    }
    /** What only a player whose power answers the lock sees: a turning diamond of light, brighter for a strong key. */
    private static void glint(Canvas c, Vec3 at, float size, BossLocks.Key key, float alpha, float time) {
        float[] col = key.strong() ? GOLD : ROOM;
        float k = key.strong() ? 1 : .55F, spin = time * .06F;
        for (int i = 0; i < 4; i++) {
            float a0 = spin + i * HALF_PI, a1 = a0 + HALF_PI;
            Vec3 p0 = at.add(cos(a0) * size * 1.5, 0, sin(a0) * size * 1.5), p1 = at.add(cos(a1) * size * 1.5, 0, sin(a1) * size * 1.5);
            c.line(p0, at.add(0, size * 1.7, 0), .02F, col, alpha * k * .8F, alpha * k * .3F);
            c.line(p0, at.add(0, -size * 1.7, 0), .02F, col, alpha * k * .8F, alpha * k * .3F);
            c.line(p0, p1, .025F, col, alpha * k, alpha * k);
        }
        c.glow(at.add(0, size * 1.9, 0), .12F, FRACTURE, alpha * k * (.6F + .4F * sin(time * .25F)));
    }
}
