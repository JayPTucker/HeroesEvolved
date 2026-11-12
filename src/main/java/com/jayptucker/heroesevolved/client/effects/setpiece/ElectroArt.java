package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.electro.ElectroGeometry;
import com.jayptucker.heroesevolved.network.ClientArmPoseState;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/** Indigo leaders, blue channels and pale return strokes. Every branch tapers away from its source. */
final class ElectroArt {
    private static final float[] SHADOW = rgb(0x10172D), INDIGO = rgb(0x3D52B8), BLUE = rgb(0x5588FF);
    private static final float[] ION = rgb(0x84D5FF), CORE = rgb(0xEAF5FF);
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private ElectroArt() {}

    static void draw(Canvas c, Active a, float t) {
        if (c.solid()) {
            if (a.piece() == Piece.TESLA_SPIRE) ElementArt.teslaMast(c, a, t);
            return;
        }
        Vec3 at = a.origin(), end = a.target();
        Player owner = a.bound(c) instanceof Player p ? p : null;
        Vec3 feet = owner == null ? at : owner.getPosition(c.partial);
        float clock = (c.level.getGameTime() % 2400 + c.partial) * .44F;
        float alpha = fade(t, a.piece().ticks, .8F, Math.min(9, a.piece().ticks * .55F));
        float size = a.scale();
        int seed = a.seed();
        switch (a.piece()) {
            case ELECTRO_DRAW -> {
                Vec3 palm = owner == null ? at : hand(c, owner, .56 - .15 * smooth(0, 5, t));
                corona(c, palm, (.14F + .19F * smooth(0, 6, t)) * size, clock, alpha, seed);
            }
            case ELECTRO_ARC -> {
                if (owner != null && t < 4) at = hand(c, owner, .68);
                float travel = smooth(0, 2.6F, t);
                Vec3 front = at.lerp(end, travel);
                float returnStroke = .56F + .44F * sin(t * 2.25F) * sin(t * 2.25F);
                fork(c, at, front, .082F * size, alpha * returnStroke, seed, 3);
                if (c.glow()) c.glow(front, .25F * size, ION, .5F * alpha);
            }
            case ELECTRO_CURRENT -> {
                if (owner != null) at = hand(c, owner, .70);
                int channelSeed = owner == null ? seed : owner.getId() * 197 + (int) (clock * 1.5F);
                fork(c, at, end, .088F * size, alpha * .85F, channelSeed, 2);
                braid(c, at, end, .21F * size, clock * 2, .65F * alpha, 1.85F);
                if (c.glow()) {
                    Vec3 front = at.lerp(end, frac(clock * .83F));
                    c.glow(front, .30F * size, ION, .53F * alpha);
                    c.glow(end, .43F * size, BLUE, .44F * alpha);
                }
            }
            case ELECTRO_ORB -> {
                Vec3 center = at.add(end.subtract(at).scale(Math.min(3.5F, t)));
                corona(c, center, size, clock, alpha * .80F, 57);
                if (!minimal()) fork(c, center, center.subtract(end.subtract(at).scale(2.8)),
                        size * .12F, alpha * .48F, seed, 1);
            }
            case ELECTRO_PRIME -> {
                float pack = smooth(0, ElectroGeometry.ORB_PRIME, t);
                corona(c, at, size * (1.55F - pack * .42F), -clock * 2, alpha, seed);
                ring(c, at, UP, size * (2.4F - pack * 1.25F), .15F, clock, TWO_PI - .55F, alpha);
            }
            case ELECTRO_WARN -> warning(c, at, size, t, seed);
            case ELECTRO_STRIKE -> {
                float drop = smooth(0, 2, t);
                float flash = (1 - smooth(4, 19, t)) * (.52F + .48F * abs(sin(t * 1.78F)));
                fork(c, end, end.lerp(at, drop), .25F * size, flash, seed, 5);
                if (drop > .5F) pulse(c, at, 2.8F * size, Math.max(0, t - 1), alpha, seed, false);
            }
            case ELECTRO_PULSE -> pulse(c, at, size, t, alpha, seed, false);
            case ELECTRO_BLACKOUT -> pulse(c, at, size, t, alpha, seed, true);
            case ELECTRO_FIELD -> {
                if (owner != null && ClientArmPoseState.get(owner.getId()) != ArmPose.ELECTRO_COIL) return;
                float build = smooth(0, ElectroGeometry.SURGE_TICKS, t);
                Vec3 floor = at.add(0, .035, 0);
                if (owner != null) floor = new Vec3(feet.x, Math.min(feet.y, at.y) + .035, feet.z);
                for (int i = 0; i < 6; i++)
                    ring(c, floor, UP, size, .075F, i * TWO_PI / 6 - clock * .32F,
                            .37F + .40F * build, alpha * (.36F + .38F * build));
            }
            case ELECTRO_RIDE -> {
                float move = smooth(0, 3.5F, t);
                Vec3 head = at.lerp(end, move).add(0, .72, 0);
                Vec3 tail = at.lerp(end, smooth(2, 11, t) * .95F).add(0, .48, 0);
                fork(c, tail, head, .14F * size, alpha, seed, 4);
                braid(c, tail, head, .24F, -clock * 2, alpha * .72F, 2.5F);
                if (c.glow()) c.glow(head, 1.15F * size, ION, alpha * .59F);
            }
            case ELECTRO_SCAR -> {
                Vec3 ground = at.add(0, .055, 0), finish = end.add(0, .055, 0);
                if (c.soft()) c.line(ground, finish, size * .27F, SHADOW, alpha * .54F, alpha * .38F);
                fork(c, ground, finish, .034F * size, alpha * .64F, seed, 2);
                if (c.glow() && !minimal()) {
                    Vec3 spark = ground.lerp(finish, frac(clock * .29F + hash(seed, 3)));
                    c.glow(spark, .31F, BLUE, .56F * alpha);
                }
            }
            case ELECTRO_COIL, ELECTRO_AURA -> {
                boolean gathering = a.piece() == Piece.ELECTRO_COIL;
                Vec3 center = ElectroGeometry.chest(feet);
                if (gathering) corona(c, center.add(0, .02, 0), .24F + size * .22F, clock * 1.6F, alpha, 19);
                int count = minimal() ? 1 : gathering ? 3 : 2;
                for (int i = 0; i < count; i++) {
                    float spin = clock * 1.3F + i * TWO_PI / count;
                    float width = gathering ? .67F + .33F * size : .48F + .13F * size;
                    Vec3 anchor = center.add(cos(spin) * width, -.69 + i * .34, sin(spin) * width);
                    Vec3 tip = center.add(cos(spin + 1.8F) * width, .52 - i * .12, sin(spin + 1.8F) * width);
                    fork(c, anchor, tip, gathering ? .051F : .038F, alpha * .63F, seed + i * 31, 1);
                }
            }
            case ELECTRO_SIPHON -> {
                if (owner != null) end = ElectroGeometry.chest(feet);
                Vec3 runner = at.lerp(end, smooth(0, 6, t));
                fork(c, at, runner, .049F + size * .04F, alpha, seed, 1);
                if (c.glow()) c.glow(runner, .43F, ION, alpha * .62F);
            }
            case ELECTRO_SCREEN, FARADAY_CAGE -> {
                Vec3 floor = (a.piece() == Piece.FARADAY_CAGE ? a.at(c) : at).add(0, .045, 0);
                float open = smooth(0, 8, t);
                float radius = size * (.35F + .65F * open);
                ring(c, floor, UP, radius, .081F, 0, TWO_PI, alpha * .8F);
                int struts = minimal() ? 4 : 8;
                for (int i = 0; i < struts; i++) {
                    float angle = i * TWO_PI / struts + clock * .24F;
                    Vec3 root = floor.add(cos(angle) * radius, 0, sin(angle) * radius);
                    Vec3 shoulder = floor.lerp(root, .80).add(0, size * .29 + .55, 0);
                    fork(c, root, shoulder, .063F, alpha * (.55F + .25F * sin(clock + i)), seed + i * 37, 1);
                }
                ring(c, floor.add(0, size * .29 + .55, 0), UP, radius * .8F, .063F,
                        clock * .19F, TWO_PI - .68F, alpha * .58F);
            }
            case TESLA_SPIRE -> {
                Vec3 crown = end.add(0, 3.2 * smooth(0, 10, t), 0);
                corona(c, crown, .49F, clock, alpha, seed);
                ring(c, end.add(0, .055, 0), UP, size, .067F, clock * .2F, TWO_PI - .48F, alpha * .46F);
            }
            case TESLA_ZAP -> {
                fork(c, at, end, .09F, alpha, seed, 3);
                if (c.glow()) c.glow(end, .75F, BLUE, alpha * .64F);
            }
            case JUMPSTART -> {
                Vec3 heart = ElectroGeometry.chest(end);
                fork(c, ElectroGeometry.chest(at), heart, .07F, alpha * (1 - smooth(4, 9, t)), seed, 2);
                ring(c, heart, UP, .24F + 1.45F * smooth(1, 12, t), .085F, 0, TWO_PI, alpha);
            }
            default -> { }
        }
    }

    private static void warning(Canvas c, Vec3 at, float radius, float t, int seed) {
        float build = smooth(0, ElectroGeometry.STRIKE_DELAY - 1, t);
        float alpha = smooth(0, 2, t) * (.55F + .40F * build);
        Vec3 floor = at.add(0, .045, 0);
        ring(c, floor, UP, radius, .085F, 0, TWO_PI, alpha);
        ring(c, floor, UP, radius * (1 - .80F * build), .14F, -t * .19F, TWO_PI - .7F, alpha * .72F);
        int leaders = minimal() ? 3 : 7;
        for (int i = 0; i < leaders; i++) {
            float angle = i * TWO_PI / leaders + t * .026F;
            Vec3 root = floor.add(cos(angle) * radius * .79, 0, sin(angle) * radius * .79);
            Vec3 tip = root.lerp(floor, .30 + build * .47).add(0, .18 + 2.05 * build, 0);
            fork(c, root, tip, .042F + .025F * build, alpha * (.35F + .5F * build), seed + i * 17, 1);
        }
    }

    private static void pulse(Canvas c, Vec3 at, float radius, float t, float alpha, int seed, boolean blackout) {
        if (alpha < .005F || t < 0) return;
        float front = smooth(0, blackout ? 11 : 9, t);
        float r = radius * (.11F + .89F * front);
        Vec3 floor = at.add(0, .065, 0);
        if (c.soft()) c.ring(floor, UP, r, blackout ? r * .23F : r * .09F, 0, TWO_PI, SHADOW, alpha * .61F);
        ring(c, floor, UP, r, .065F + radius * .014F, 0, TWO_PI, alpha);
        float echo = smooth(2.5F, 16, t);
        if (echo > 0) ring(c, floor, UP, radius * echo, .10F, t * .17F, TWO_PI - .62F, alpha * .52F);
        int fingers = minimal() ? 5 : blackout ? 13 : 10;
        for (int i = 0; i < fingers; i++) {
            float angle = (i + hash(seed, i) * .58F) * TWO_PI / fingers;
            Vec3 rim = floor.add(cos(angle) * r, 0, sin(angle) * r);
            Vec3 root = floor.lerp(rim, blackout ? .64 : .46)
                    .add(0, (.6F + radius * .19F) * sin(PI * front) + .14F, 0);
            fork(c, root, rim, .042F + radius * .004F, alpha * .86F, seed + i * 37, 1);
        }
        if (c.glow() && t < 7) c.glow(at.add(0, .58, 0), Math.min(3.2F, radius * .55F), BLUE, alpha * .45F);
    }

    private static void corona(Canvas c, Vec3 center, float r, float phase, float alpha, int seed) {
        if (c.soft()) c.shell(center, r * .8F, r * .8F, r * .8F, PI, INDIGO, alpha * .47F, .38F,
                (angle, pitch) -> .74F + .26F * sin(phase + 4 * angle + 3 * pitch), phase);
        if (c.glow()) {
            c.glow(center, r * 2, BLUE, alpha * .51F);
            c.glow(center, r * .68F, CORE, alpha * .91F);
        }
        int count = minimal() ? 1 : 3;
        for (int i = 0; i < count; i++) {
            float angle = phase * (i % 2 == 0 ? 1 : -1) + i * 2.4F;
            Vec3 axis = new Vec3(cos(angle) * .82F, .58, sin(angle) * .82F);
            ring(c, center, axis, r * (.80F + i * .13F), r * .106F, phase + hash(seed, i),
                    3.6F + i * .45F, alpha * .80F);
        }
    }

    private static void ring(Canvas c, Vec3 center, Vec3 normal, float r, float width, float from, float span, float alpha) {
        if (alpha < .005F || r < .02F) return;
        if (c.soft()) c.ring(center, normal, r, width * 2.2F, from, from + span, INDIGO, alpha * .67F);
        if (c.glow()) {
            c.ring(center, normal, r, width, from, from + span, BLUE, alpha * .72F);
            c.ring(center, normal, r, width * .34F, from, from + span, CORE, alpha * .75F);
        }
    }

    private static void braid(Canvas c, Vec3 start, Vec3 end, float radius, float phase, float alpha, float turns) {
        if (minimal() || !c.glow() || alpha < .01F || start.distanceToSqr(end) < .02) return;
        Vec3 axis = end.subtract(start).normalize(), right = side(axis), up = axis.cross(right);
        Vec3[] points = new Vec3[19];
        for (int i = 0; i < points.length; i++) {
            float progress = i / 18F, angle = phase + progress * turns * TWO_PI;
            float width = sin(PI * progress) * radius;
            points[i] = start.lerp(end, progress).add(right.scale(cos(angle) * width)).add(up.scale(sin(angle) * width));
        }
        c.ribbon(points, .032F, ION, alpha, alpha * .38F);
    }

    private static void fork(Canvas c, Vec3 start, Vec3 end, float width, float alpha, int seed, int branches) {
        double distance = start.distanceTo(end);
        if (distance < .025 || alpha < .008F) return;
        Vec3 axis = end.subtract(start).normalize(), right = side(axis), up = axis.cross(right);
        Vec3[] channel = new Vec3[clamp(3 + (int) (distance * 1.5), 4, 23)];
        float jitter = (float) Math.min(1.6, distance * .085);
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
            Vec3 reach = right.scale(cos(angle)).add(up.scale(sin(angle))).scale(jitter * (1.4 + hash(seed, 234 + i)));
            Vec3 tip = joint.add(reach).add(axis.scale(jitter * (1 + hash(seed, 259 + i))));
            stroke(c, new Vec3[]{joint, joint.lerp(tip, .41).add(up.scale(jitter * .23)), tip}, width * .42F, alpha * .65F);
        }
    }

    private static void stroke(Canvas c, Vec3[] points, float width, float alpha) {
        if (c.soft()) c.ribbon(points, width * 2.9F, INDIGO, alpha * .56F, alpha * .18F);
        if (c.glow()) {
            c.ribbon(points, width * 1.85F, BLUE, alpha * .46F, alpha * .14F);
            c.ribbon(points, width, ION, alpha * .75F, alpha * .40F);
            c.ribbon(points, width * .35F, CORE, alpha, alpha * .57F);
        }
    }

    private static Vec3 side(Vec3 axis) { return (abs((float) axis.y) > .93F ? new Vec3(1, 0, 0) : UP).cross(axis).normalize(); }

    private static Vec3 hand(Canvas c, Player owner, double reach) {
        Minecraft mc = Minecraft.getInstance();
        if (owner != mc.player || mc.options.getCameraType() != CameraType.FIRST_PERSON)
            return ElectroGeometry.hand(owner, c.partial, reach);
        Vec3 look = owner.getViewVector(c.partial);
        Vec3 right = Vec3.directionFromRotation(0, owner.getViewYRot(c.partial) + 90);
        Vec3 cameraUp = right.cross(look).normalize();
        return c.camera().add(look.scale(reach + .13))
                .add(right.scale(owner.getMainArm() == HumanoidArm.RIGHT ? .39 : -.39)).subtract(cameraUp.scale(.43));
    }

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }
}
