package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.amplify.AmpGeometry;
import com.jayptucker.heroesevolved.amplify.AmpService;
import com.jayptucker.heroesevolved.client.ClientAmp;
import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/**
 * Power Amplification's red lightning (Ando Masahashi's): every piece is drawn from one forked bolt, a crackle that
 * climbs a body, a ring and a ball, in one palette. Red drawn additively washes out to pink in daylight, so a stroke's
 * body is drawn in the SOFT pass (normal blending, so it stays red at noon) and only its hot rim and pale core glow.
 *
 * <p>Palette: shadow {@code 2A0A10}, deep {@code 8E1424}, red {@code E0263A}, hot {@code FF6A5A}, core {@code FFE6DE}.</p>
 */
final class AmpArt {
    private static final float[] SHADOW = rgb(0x2A0A10), DEEP = rgb(0x8E1424), RED = rgb(0xE0263A), HOT = rgb(0xFF6A5A),
            CORE = rgb(0xFFE6DE), BLUE = rgb(0x5588FF), ICE = rgb(0xBFEFFF);
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private AmpArt() {}

    static void draw(Canvas c, Active a, float t) {
        switch (a.piece()) {
            case AMP_ZAP -> zap(c, a, t);
            case AMP_SURGE -> surge(c, a, t);
            case AMP_OVERCHARGE -> overcharge(c, a, t);
            case AMP_BOLT -> bolt(c, a, t);
            case AMP_BEAM -> beam(c, a, t);
            case AMP_BURST, AMP_AWAKEN -> burst(c, a, t);
            case AMP_SHOCK -> shock(c, a, t);
            case AMP_JUMP -> jump(c, a, t);
            case AMP_GATHER -> gather(c, a, t);
            case AMP_WAVE -> wave(c, a, t);
            case AMP_THREAD -> thread(c, a, t);
            case AMP_BURNOUT -> burnout(c, a, t);
            case AMP_WILD -> wild(c, a, t);
            case AMP_DOOR -> door(c, a, t);
            case AMP_DOME -> dome(c, a, t);
            case AMP_STORM -> storm(c, a, t);
            case AMP_BACKLASH -> backlash(c, a, t);
            case FINISH_JUICE -> juice(c, a, t);
            case FINISH_RED_WAVE -> redWave(c, a, t);
            case FINISH_REVERSE -> reverse(c, a, t);
            default -> {}
        }
    }

    // ------------------------------------------------------------------
    // Pieces
    // ------------------------------------------------------------------

    /** A zap from the hand to an ally (or anything near): a short fork that flickers and fades. */
    private static void zap(Canvas c, Active a, float t) {
        float alpha = fade(t, a.piece().ticks, 0, 6) * clamp(a.scale(), .3F, 1.6F);
        Vec3 from = handOf(c, a, a.origin());
        Vec3 to = entityCenter(c, a.seed(), a.target());
        int seed = a.seed() * 31 + (int) (c.time() * 2);
        fork(c, from, to, .05F * clamp(a.scale(), .4F, 1.8F), alpha, seed, 2);
        if (c.glow()) c.glow(to, .25F + .2F * a.scale(), HOT, alpha * .5F);
    }

    /**
     * A Surge taking hold: a ring flashes at the feet. The body itself wears the red field from then on
     * ({@code client/SuperchargeField}, the charged creeper's swirl in red), not sparks.
     */
    private static void surge(Canvas c, Active a, float t) {
        Entity body = a.bound(c);
        Vec3 feet = a.at(c);
        float width = body != null ? body.getBbWidth() : .6F, alpha = fade(t, a.piece().ticks, 0, 8);
        if (t < 10) ring(c, feet.add(0, .05, 0), UP, width * (.6F + t * .12F), .06F, alpha * (1 - t / 10F));
    }

    /**
     * An Overcharge: a ring at the feet that tightens as its six seconds run out, the time an ally has before the backlash.
     * The body's field glows brighter and swirls faster meanwhile ({@code client/SuperchargeField}).
     */
    private static void overcharge(Canvas c, Active a, float t) {
        Entity body = a.bound(c);
        if (body == null) return;
        Vec3 feet = a.at(c);
        float life = a.piece().ticks, k = clamp(t / life, 0, 1);
        float alpha = fade(t, life, 3, 6), width = body.getBbWidth();
        float r = width * (1.5F - .7F * k) + .2F;
        ring(c, feet.add(0, .04, 0), UP, r, .05F, alpha * .8F);
    }

    /** A bolt thrown from the hand: one thick fork with a flash where it lands. */
    private static void bolt(Canvas c, Active a, float t) {
        float alpha = fade(t, a.piece().ticks, 0, 5);
        Vec3 from = handOf(c, a, a.origin());
        float w = .07F * clamp(a.scale(), .8F, 2.4F);
        fork(c, from, a.target(), w, alpha, a.seed() + (int) (t * .7F), 3);
        if (c.glow()) {
            c.glow(a.target(), .5F + .25F * a.scale(), HOT, alpha * .7F);
            c.glow(from, .25F, CORE, alpha * .6F);
        }
    }

    /** The beam: both hands forward, a straight crimson channel with a fork winding round it. */
    private static void beam(Canvas c, Active a, float t) {
        float alpha = fade(t, a.piece().ticks, 0, 5);
        Vec3 from = handOf(c, a, a.origin()), to = a.target();
        float w = .12F * clamp(a.scale(), .8F, 3.2F);
        Vec3[] line = {from, from.lerp(to, .5), to};
        if (c.soft()) {
            c.ribbon(line, w * 2.8F, DEEP, alpha * .55F, alpha * .35F);
            c.ribbon(line, w * 1.6F, RED, alpha * .8F, alpha * .6F);
        }
        if (c.glow()) {
            c.ribbon(line, w * 1.1F, HOT, alpha * .7F, alpha * .5F);
            c.ribbon(line, w * .35F, CORE, alpha, alpha * .8F);
            c.glow(to, .6F + w * 2, HOT, alpha * .6F);
        }
        fork(c, from, to, w * .35F, alpha * .8F, a.seed() + (int) (c.time() * 2), 4);
    }

    /** A burst round a body: forks thrown out in every direction and a ring racing over the ground. */
    private static void burst(Canvas c, Active a, float t) {
        Vec3 at = a.at(c).add(0, 1, 0);
        float life = a.piece().ticks, k = 1 - (1 - clamp(t / 6F, 0, 1)) * (1 - clamp(t / 6F, 0, 1)) * (1 - clamp(t / 6F, 0, 1));
        float alpha = fade(t, life, 0, life * .6F);
        float r = a.scale() * k;
        int arms = minimal() ? 6 : 12;
        for (int i = 0; i < arms; i++) {
            float yaw = TWO_PI * hash(a.seed(), i), pitch = (hash(a.seed(), i + 40) - .3F) * 1.2F;
            Vec3 dir = new Vec3(cos(yaw) * cos(pitch), sin(pitch), sin(yaw) * cos(pitch));
            fork(c, at, at.add(dir.scale(r)), .05F, alpha, a.seed() + i * 13, 1);
        }
        ring(c, a.at(c).add(0, .05, 0), UP, r, .12F, alpha);
        if (c.glow()) c.glow(at, .8F + r * .25F, HOT, alpha * (1 - k) * .9F);
    }

    /** Shock Therapy's jolt: two short forks into the temples and a ring round the head. */
    private static void shock(Canvas c, Active a, float t) {
        Entity body = a.bound(c);
        if (body == null) return;
        float alpha = fade(t, a.piece().ticks, 0, 7);
        Vec3 head = body.getEyePosition(c.partial);
        Vec3 side = Vec3.directionFromRotation(0, body.getViewYRot(c.partial) + 90).scale(.32);
        int seed = a.seed() + (int) (c.time() * 3);
        fork(c, head.add(side.scale(2.2)), head.add(side), .03F, alpha, seed, 0);
        fork(c, head.subtract(side.scale(2.2)), head.subtract(side), .03F, alpha, seed + 5, 0);
        ring(c, head.add(0, .15, 0), UP, .45F + t * .02F, .04F, alpha);
        if (c.glow()) c.glow(head, .4F, CORE, alpha * .5F * (1 - t / 14F));
    }

    /** Jumpstart: a fork into a block, and lit edges crackling round it. */
    private static void jump(Canvas c, Active a, float t) {
        float alpha = fade(t, a.piece().ticks, 0, 8) * clamp(a.scale(), .5F, 1.6F);
        Vec3 from = handOf(c, a, a.origin()), at = a.target();
        if (t < 6) fork(c, from, at, .05F, alpha, a.seed() + (int) t, 2);
        float e = .52F;
        int seed = a.seed() + (int) (c.time() * 2);
        for (int i = 0; i < 4; i++) {
            float ang = i * HALF_PI;
            Vec3 corner = at.add(cos(ang) * e, -e + 2 * e * hash(seed, i), sin(ang) * e);
            Vec3 next = at.add(cos(ang + HALF_PI) * e, -e + 2 * e * hash(seed, i + 9), sin(ang + HALF_PI) * e);
            fork(c, corner, next, .025F, alpha * .8F, seed + i, 0);
        }
        if (c.glow()) c.glow(at, .7F, HOT, alpha * .5F);
    }

    /** A Thousand Times gathering (or a Lightning Cannon): sparks drawn in from round the body to the raised hands. */
    private static void gather(Canvas c, Active a, float t) {
        Entity body = a.bound(c);
        if (body == null) return;
        float life = a.piece().ticks, k = clamp(t / life, 0, 1), alpha = fade(t, life, 4, 4);
        boolean cannon = a.scale() < 4;
        Vec3 focus = cannon && body instanceof Player p ? AmpGeometry.ball(p, c.partial).add(p.getViewVector(c.partial).scale(.3))
                : body.getPosition(c.partial).add(0, body.getBbHeight() + .6, 0);
        int sparks = minimal() ? 5 : 10;
        float reach = (cannon ? 2.2F : a.scale() * .45F) * (1 - k * .7F);
        for (int i = 0; i < sparks; i++) {
            float ang = TWO_PI * hash(a.seed(), i) + c.time() * .2F;
            float y = (hash(a.seed(), i + 20) - .4F) * 2;
            Vec3 from = focus.add(cos(ang) * reach, y * (1 - k), sin(ang) * reach);
            fork(c, from, focus, .03F, alpha * (.5F + .5F * k), a.seed() + i * 11 + (int) (c.time() * 3), 0);
        }
        if (c.glow()) {
            c.glow(focus, .2F + .5F * k, HOT, alpha * .8F);
            c.glow(focus, .1F + .25F * k, CORE, alpha);
        }
        if (c.soft()) c.sphere(focus, .1F + .22F * k, RED, alpha * .6F);
    }

    /** The release: a ring racing out over the ground with forks standing up from it. */
    private static void wave(Canvas c, Active a, float t) {
        float life = a.piece().ticks, k = 1 - (1 - clamp(t / life, 0, 1)) * (1 - clamp(t / life, 0, 1));
        float alpha = fade(t, life, 0, life * .5F);
        Vec3 at = a.at(c).add(0, .08, 0);
        float r = a.scale() * k;
        boolean cold = a.seed() == 2;
        ring(c, at, UP, r, .18F, alpha);
        if (cold && c.glow()) c.ring(at, UP, r * .96F, .12F, 0, TWO_PI, ICE, alpha * .6F);
        int arms = minimal() ? 6 : 14;
        for (int i = 0; i < arms; i++) {
            float ang = i * TWO_PI / arms + hash(a.seed(), i);
            Vec3 foot = at.add(cos(ang) * r, 0, sin(ang) * r);
            fork(c, foot, foot.add(0, .6F + 1.2F * hash(a.seed(), i + 30) * (1 - k), 0), .035F, alpha * .8F, a.seed() + i * 7 + (int) t, 0);
        }
    }

    /** The Partner thread: a thin red line from the hand to the partner. */
    private static void thread(Canvas c, Active a, float t) {
        float alpha = fade(t, a.piece().ticks, 2, 4);
        Vec3 from = handOf(c, a, a.origin()), to = entityCenter(c, a.seed(), a.target());
        if (c.soft()) c.line(from, to, .03F, RED, alpha * .8F, alpha * .8F);
        if (c.glow()) c.line(from, to, .012F, CORE, alpha, alpha);
    }

    /** Burnout or an overload: a cage of crackle round the body over a dark core. */
    private static void burnout(Canvas c, Active a, float t) {
        Entity body = a.bound(c);
        if (body == null) return;
        Vec3 feet = a.at(c);
        float alpha = fade(t, a.piece().ticks, 0, 8), h = a.scale(), w = body.getBbWidth() * .7F;
        if (c.soft()) c.sphere(feet.add(0, h * .55F, 0), Math.max(w, h * .35F), SHADOW, alpha * .25F);
        for (int i = 0; i < 6; i++) {
            float ang = i * TWO_PI / 6 + c.time() * .3F;
            Vec3 low = feet.add(cos(ang) * w, 0, sin(ang) * w), high = feet.add(cos(ang + 1) * w, h, sin(ang + 1) * w);
            fork(c, low, high, .03F, alpha, a.seed() + i * 5 + (int) (c.time() * 3), 0);
        }
    }

    /** A misfire: forks thrown out of the body in every direction at once. */
    private static void wild(Canvas c, Active a, float t) {
        Entity body = a.bound(c);
        Vec3 center = body != null ? body.getBoundingBox().getCenter() : a.origin();
        float alpha = fade(t, a.piece().ticks, 0, 8), r = 1.2F + t * .2F;
        for (int i = 0; i < (minimal() ? 4 : 9); i++) {
            float yaw = TWO_PI * hash(a.seed() + (int) t / 3, i), pitch = (hash(a.seed(), i + 9) - .5F) * 2;
            Vec3 dir = new Vec3(cos(yaw) * cos(pitch), sin(pitch), sin(yaw) * cos(pitch));
            fork(c, center, center.add(dir.scale(r)), .04F, alpha, a.seed() + i * 17 + (int) t, 1);
        }
        if (c.glow()) c.glow(center, .9F, HOT, alpha * .6F);
    }

    /** Doorbuster: a thick bolt into the door and a blast where it was. */
    private static void door(Canvas c, Active a, float t) {
        float alpha = fade(t, a.piece().ticks, 0, 8);
        if (t < 5) fork(c, handOf(c, a, a.origin()), a.target(), .08F, alpha, a.seed() + (int) t, 3);
        float r = 1.4F * smooth(0, 1, t / 5F);
        for (int i = 0; i < 8; i++) {
            float ang = i * TWO_PI / 8;
            fork(c, a.target(), a.target().add(cos(ang) * r, (hash(a.seed(), i) - .5F) * r, sin(ang) * r), .04F, alpha * .8F, a.seed() + i, 0);
        }
        if (c.glow()) c.glow(a.target(), 1.1F, HOT, alpha * .6F);
    }

    /** A dome friends shelter under (an overcharged force field, or a ghost party round a phaser). */
    private static void dome(Canvas c, Active a, float t) {
        float alpha = fade(t, a.piece().ticks, 2, 2);
        Vec3 at = a.entity() >= 0 ? a.at(c) : a.origin();
        float r = a.scale();
        if (c.soft()) c.shell(at, r, r * .8F, r, HALF_PI, RED, alpha * .16F, .4F, hexes(9, .1F, .3F), c.time() * .01F);
        ring(c, at.add(0, .06, 0), UP, r, .08F, alpha * .9F);
        if (c.glow()) c.ring(at.add(0, r * .4F, 0), UP, r * .92F, .03F, 0, TWO_PI, HOT, alpha * .4F);
    }

    /** A storm bolt from the sky, red and blue braided. */
    private static void storm(Canvas c, Active a, float t) {
        float alpha = fade(t, a.piece().ticks, 0, 5);
        fork(c, a.origin(), a.target(), .09F * a.scale(), alpha, a.seed() + (int) t, 4);
        if (c.glow()) {
            Vec3[] blue = {a.origin().add(.25, 0, 0), a.origin().lerp(a.target(), .5).add(-.3, 0, .2), a.target()};
            c.ribbon(blue, .05F, BLUE, alpha * .7F, alpha * .5F);
            c.glow(a.target(), 1.2F, HOT, alpha * .7F);
        }
        ring(c, a.target().add(0, .05, 0), UP, .6F + t * .2F, .08F, alpha);
    }

    /** A backlash: a ring that collapses into the body, and dark sparks. */
    private static void backlash(Canvas c, Active a, float t) {
        Entity body = a.bound(c);
        if (body == null) return;
        Vec3 feet = a.at(c);
        float life = a.piece().ticks, k = clamp(t / life, 0, 1), alpha = fade(t, life, 0, 6);
        float r = body.getBbWidth() * (1.8F - 1.4F * k);
        ring(c, feet.add(0, a.scale() * .5F, 0), UP, r, .06F, alpha);
        if (c.soft()) c.ring(feet.add(0, a.scale() * .5F, 0), UP, r, .14F, 0, TWO_PI, SHADOW, alpha * .5F);
    }

    // ------------------------------------------------------------------
    // Finishers
    // ------------------------------------------------------------------

    /** Sidekick Juice: the palm on the chest, red light pouring in until it shines through, then a flash. */
    private static void juice(Canvas c, Active a, float t) {
        Entity foe = a.bound(c);
        if (foe == null) return;
        Vec3 chest = foe.getBoundingBox().getCenter().add(0, foe.getBbHeight() * .1, 0);
        Entity owner = c.level.getEntity(a.seed());
        float alpha = fade(t, a.piece().ticks, 2, 8);
        if (t < 20) {
            float k = clamp(t / 20F, 0, 1);
            if (owner instanceof Player p) fork(c, AmpGeometry.hand(p, c.partial), chest, .04F + .04F * k, alpha, a.seed() + (int) (c.time() * 3), 1);
            if (c.soft()) c.sphere(chest, .15F + foe.getBbWidth() * .55F * k, RED, alpha * .35F * k);
            if (c.glow()) c.glow(chest, .2F + .9F * k, HOT, alpha * k);
            climb(c, foe.getPosition(c.partial), a.scale() * k, foe.getBbWidth() * .6F, alpha * k, a.seed() + (int) (c.time() * 4), 3);
        } else {
            float b = clamp((t - 20) / 8F, 0, 1);
            if (c.glow()) c.glow(chest, 1.6F * (1 - b) + .4F, CORE, alpha * (1 - b));
            for (int i = 0; i < 10; i++) {
                float yaw = TWO_PI * hash(a.seed(), i), pitch = (hash(a.seed(), i + 3) - .5F) * 2;
                Vec3 dir = new Vec3(cos(yaw) * cos(pitch), sin(pitch), sin(yaw) * cos(pitch));
                fork(c, chest, chest.add(dir.scale(.6 + 2.2 * b)), .05F, alpha * (1 - b), a.seed() + i, 1);
            }
        }
    }

    /** Red Wave: the ball between the hands, then a beam that drives the foe back. */
    private static void redWave(Canvas c, Active a, float t) {
        Entity owner = a.bound(c);
        if (!(owner instanceof Player p)) return;
        Entity foe = c.level.getEntity(a.seed());
        float alpha = fade(t, a.piece().ticks, 0, 8);
        Vec3 ball = AmpGeometry.ball(p, c.partial);
        if (t < 9) {
            float k = clamp(t / 9F, 0, 1);
            if (c.soft()) c.sphere(ball, .08F + .22F * k, RED, alpha * .8F);
            if (c.glow()) c.glow(ball, .2F + .4F * k, HOT, alpha);
            ballSparks(c, ball, .08F + .22F * k, alpha, a.seed() + (int) (c.time() * 3));
        } else if (foe != null) {
            Vec3 to = foe.getBoundingBox().getCenter();
            float w = .2F * (1 - clamp((t - 16) / 10F, 0, 1));
            Vec3[] line = {ball, ball.lerp(to, .5), to};
            if (c.soft()) {
                c.ribbon(line, w * 2.6F, DEEP, alpha * .55F, alpha * .35F);
                c.ribbon(line, w * 1.6F, RED, alpha * .8F, alpha * .6F);
            }
            if (c.glow()) {
                c.ribbon(line, w * .45F, CORE, alpha, alpha * .7F);
                c.glow(to, .9F, HOT, alpha * .6F);
            }
            fork(c, ball, to, .05F, alpha * .8F, a.seed() + (int) (c.time() * 2), 3);
        }
    }

    /** Shock Therapy, Reversed: two jolts to the temples, then sparks run over the body. */
    private static void reverse(Canvas c, Active a, float t) {
        Entity foe = a.bound(c);
        if (foe == null) return;
        float alpha = fade(t, a.piece().ticks, 0, 6);
        Vec3 head = foe.getEyePosition(c.partial);
        Vec3 side = Vec3.directionFromRotation(0, foe.getViewYRot(c.partial) + 90).scale(.3);
        boolean jolt = t > 5 && t < 9 || t > 11 && t < 15;
        if (jolt) {
            fork(c, head.add(side.scale(2.4)), head.add(side), .045F, alpha, a.seed() + (int) t, 1);
            fork(c, head.subtract(side.scale(2.4)), head.subtract(side), .045F, alpha, a.seed() + 3 + (int) t, 1);
        }
        if (t >= 15) climb(c, foe.getPosition(c.partial), a.scale(), foe.getBbWidth() * .6F, alpha, a.seed() + (int) (c.time() * 4), 4);
    }

    // ------------------------------------------------------------------
    // Live: charging hands and sparking hands (a supercharged body's field is a render layer, client/SuperchargeField)
    // ------------------------------------------------------------------

    static boolean live() { return !ClientAmp.states().isEmpty(); }

    static void drawLive(Canvas c) {
        if (c.level == null) return;
        Minecraft mc = Minecraft.getInstance();
        for (Map.Entry<Integer, ClientAmp.State> e : ClientAmp.states().entrySet()) {
            if (!(c.level.getEntity(e.getKey()) instanceof Player p)) continue;
            ClientAmp.State s = e.getValue();
            boolean self = p == mc.player && firstPerson();
            if (s.chargingSince() >= 0) {
                float age = c.level.getGameTime() - s.chargingSince() + c.partial;
                float r = AmpGeometry.BALL_RADIUS * clamp(.25F + age / AmpGeometry.BALL_TICKS, .25F, 1) * (.6F + .4F * s.charge() / 100F);
                Vec3 ball = self ? firstPersonBall(c, p) : AmpGeometry.ball(p, c.partial);
                if (c.soft()) c.sphere(ball, r, RED, .75F);
                if (c.glow()) { c.glow(ball, r * 2.2F, HOT, .7F); c.glow(ball, r * .9F, CORE, .8F); }
                ballSparks(c, ball, r, 1, p.getId() + (int) (c.time() * 3));
            }
            if (self) continue;
            if ((s.flags() & (AmpService.SPARKING | AmpService.SHOWING)) != 0) {
                Vec3 hand = AmpGeometry.hand(p, c.partial);
                boolean show = (s.flags() & AmpService.SHOWING) != 0;
                int seed = p.getId() * 13 + (int) (c.time() * (show ? 3 : 1.5F));
                if (show || hash(seed, 1) > .55F) {
                    Vec3 off = hand.add((hash(seed, 2) - .5) * .5, (hash(seed, 3) - .2) * .4, (hash(seed, 4) - .5) * .5);
                    fork(c, hand, off, .02F, show ? .9F : .6F, seed, 0);
                    if (show) {
                        Vec3 other = hand.add(Vec3.directionFromRotation(0, p.getViewYRot(c.partial) - 90).scale(.55));
                        fork(c, hand, other, .02F, .8F, seed + 7, 0);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Primitives
    // ------------------------------------------------------------------

    /** Crackle climbing a body: {@code count} short forks between random points on a column round it. */
    private static void climb(Canvas c, Vec3 feet, float height, float radius, float alpha, int seed, int count) {
        if (alpha < .01F || height < .05F) return;
        for (int i = 0; i < count; i++) {
            float ang = TWO_PI * hash(seed, i * 3), y = height * hash(seed, i * 3 + 1);
            Vec3 a = feet.add(cos(ang) * radius, y, sin(ang) * radius);
            float ang2 = ang + (hash(seed, i * 3 + 2) - .5F) * 1.6F;
            Vec3 b = feet.add(cos(ang2) * radius, Math.min(height, y + .25F + .35F * hash(seed, i + 20)), sin(ang2) * radius);
            fork(c, a, b, .025F, alpha, seed + i * 19, 0);
        }
    }

    private static void ballSparks(Canvas c, Vec3 ball, float r, float alpha, int seed) {
        for (int i = 0; i < 3; i++) {
            Vec3 dir = new Vec3(hash(seed, i) - .5, hash(seed, i + 5) - .5, hash(seed, i + 9) - .5);
            if (dir.lengthSqr() < 1e-4) continue;
            fork(c, ball, ball.add(dir.normalize().scale(r * 1.8)), .015F, alpha * .8F, seed + i, 0);
        }
    }

    /**
     * Overdrive ({@code amplify/Overdrive}): a linked body held open, red lightning crawling up it in short forks and a
     * ring turning at its waist, and (for a friend) a live channel jumping between the amplifier's hand and its chest.
     * Bound to the linked body, the amplifier's id in the seed (-1 on the amplifier himself), scale its height; re-sent
     * each second.
     */
    static void overdrive(Canvas c, Active a, float t) {
        if (SetPieceClient.superseded(a, java.util.Set.of(com.jayptucker.heroesevolved.styles.Piece.AMP_OVERDRIVE), c.level.getGameTime())) return;
        Entity body = a.bound(c);
        if (body == null) return;
        float h = Math.max(.6F, a.scale()), life = a.piece().ticks, f = Math.min(1, t / 3 + .4F) * Math.clamp((life - t) / 2, 0, 1);
        Vec3 feet = body.getPosition(c.partial);
        float clock = (c.level.getGameTime() % 2400 + c.partial);
        int flick = (int) (clock / 2);
        climb(c, feet, h, Math.max(.3F, body.getBbWidth() * .7F), f, a.seed() * 31 + flick, minimal() ? 2 : 4);
        ring(c, feet.add(0, h * .5, 0), new Vec3(sin(clock * .1F) * .2, 1, cos(clock * .13F) * .2).normalize(), .55F + .05F * sin(clock * .3F), .04F, f * .8F);
        if (c.glow()) c.glow(feet.add(0, h * .55, 0), h * .6F, RED, f * .18F);
        if (a.seed() >= 0 && c.level.getEntity(a.seed()) instanceof Player amp) {
            Vec3 from = AmpGeometry.hand(amp, c.partial), to = feet.add(0, h * .6, 0);
            fork(c, from, to, .05F, f * (.6F + .4F * hash(flick, 3)), a.seed() + flick, 1);
        }
    }

    /** One fork of an Overdrive's discharge, from a linked body to a foe, with a flash where it lands. */
    static void discharge(Canvas c, Active a, float t) {
        Vec3 from = a.origin(), to = a.target();
        float life = a.piece().ticks, f = Math.clamp((life - t) / 5, 0, 1), k = smooth(0, 2, t);
        fork(c, from, from.lerp(to, k), .09F, f, a.seed() + (int) (t / 2), 2);
        if (t >= 2) {
            float b = smooth(2, 9, t);
            ring(c, to, to.subtract(from).normalize(), .2F + .9F * b, .05F, f * (1 - b));
            if (c.glow()) c.glow(to, .5F + .8F * (1 - b), HOT, f * (1 - b));
        }
    }

    private static void ring(Canvas c, Vec3 at, Vec3 axis, float r, float width, float alpha) {
        if (alpha < .01F || r < .02F) return;
        if (c.soft()) c.ring(at, axis, r, width * 2.2F, 0, TWO_PI, RED, alpha * .7F);
        if (c.glow()) {
            c.ring(at, axis, r, width, 0, TWO_PI, HOT, alpha * .6F);
            c.ring(at, axis, r, width * .3F, 0, TWO_PI, CORE, alpha * .7F);
        }
    }

    /** A jagged channel with a few side branches, drawn in three layers (see the class comment for the passes). */
    static void fork(Canvas c, Vec3 start, Vec3 end, float width, float alpha, int seed, int branches) {
        double distance = start.distanceTo(end);
        if (distance < .025 || alpha < .008F) return;
        Vec3 axis = end.subtract(start).normalize(), right = side(axis), up = axis.cross(right);
        Vec3[] channel = new Vec3[clamp(3 + (int) (distance * 1.6), 4, 24)];
        float jitter = (float) Math.min(1.4, distance * .1);
        for (int i = 0; i < channel.length; i++) {
            float u = i / (float) (channel.length - 1), edge = sin(PI * u);
            channel[i] = start.lerp(end, u).add(right.scale((hash(seed, i * 2) - .5) * jitter * edge))
                    .add(up.scale((hash(seed, i * 2 + 1) - .5) * jitter * edge));
        }
        stroke(c, channel, width, alpha);
        if (minimal()) return;
        for (int i = 0; i < branches; i++) {
            int node = 1 + (int) (hash(seed, 201 + i) * (channel.length - 2));
            Vec3 joint = channel[node];
            float angle = TWO_PI * hash(seed, 214 + i);
            Vec3 tip = joint.add(right.scale(cos(angle)).add(up.scale(sin(angle))).scale(jitter * (1.2 + hash(seed, 234 + i))))
                    .add(axis.scale(jitter * (.8 + hash(seed, 259 + i))));
            stroke(c, new Vec3[]{joint, joint.lerp(tip, .45).add(up.scale(jitter * .2)), tip}, width * .45F, alpha * .65F);
        }
    }

    private static void stroke(Canvas c, Vec3[] points, float width, float alpha) {
        if (c.soft()) {
            c.ribbon(points, width * 2.8F, DEEP, alpha * .5F, alpha * .2F);
            c.ribbon(points, width * 1.6F, RED, alpha * .85F, alpha * .5F);
        }
        if (c.glow()) {
            c.ribbon(points, width * 1.15F, HOT, alpha * .55F, alpha * .3F);
            c.ribbon(points, width * .35F, CORE, alpha, alpha * .6F);
        }
    }

    private static Vec3 side(Vec3 axis) { return (abs((float) axis.y) > .93F ? new Vec3(1, 0, 0) : UP).cross(axis).normalize(); }

    /** Where a piece's red lightning leaves the hand: the bound player's hand as this frame sees it, or where it was cast. */
    private static Vec3 handOf(Canvas c, Active a, Vec3 fallback) {
        Entity owner = a.bound(c);
        if (!(owner instanceof Player p)) return fallback;
        Minecraft mc = Minecraft.getInstance();
        if (p == mc.player && firstPerson()) {
            Vec3 look = p.getViewVector(c.partial);
            Vec3 right = Vec3.directionFromRotation(0, p.getViewYRot(c.partial) + 90);
            Vec3 cameraUp = right.cross(look).normalize();
            return c.camera().add(look.scale(.58)).add(right.scale(p.getMainArm() == HumanoidArm.RIGHT ? .38 : -.38)).subtract(cameraUp.scale(.42));
        }
        return AmpGeometry.hand(p, c.partial);
    }

    private static Vec3 firstPersonBall(Canvas c, Player p) {
        Vec3 look = p.getViewVector(c.partial);
        Vec3 right = Vec3.directionFromRotation(0, p.getViewYRot(c.partial) + 90);
        Vec3 cameraUp = right.cross(look).normalize();
        return c.camera().add(look.scale(.62)).subtract(cameraUp.scale(.3));
    }

    private static Vec3 entityCenter(Canvas c, int id, Vec3 fallback) {
        if (id < 0 || c.level == null) return fallback;
        Entity e = c.level.getEntity(id);
        return e == null ? fallback : e.getBoundingBox().move(e.getPosition(c.partial).subtract(e.position())).getCenter();
    }

    private static boolean firstPerson() { return Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON; }

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }
}
