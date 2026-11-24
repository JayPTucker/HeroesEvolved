package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.endportal.PortalGeometry;
import com.jayptucker.heroesevolved.mimicry.MimicColors;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/** An eye remembering light: hand-spun offerings, twelve waking irises, then a membrane pulled into the End. */
final class PortalArt {
    private static final float[] TEAL = rgb(0x47CFC1), PALE = rgb(0xD9FFF5), DEEP = rgb(0x1C1540),
            VOID = rgb(0x080D1C), VIOLET = rgb(0xAA86EA);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Set<Piece> CHANNEL = Set.of(Piece.END_PORTAL_CHANNEL, Piece.END_PORTAL_CANCEL);
    private static final Set<Piece> DORMANT = Set.of(Piece.END_PORTAL_DORMANT);
    private PortalArt() {}

    static void draw(Canvas c, Active a, float t) {
        if (a.piece() == Piece.END_PORTAL_CHANNEL && SetPieceClient.superseded(a, CHANNEL, c.level.getGameTime())) return;
        if (a.piece() == Piece.END_PORTAL_DORMANT && SetPieceClient.superseded(a, DORMANT, c.level.getGameTime())) return;
        float alpha = Math.min(smooth(0, 2, t), 1 - smooth(a.piece().ticks - 5, a.piece().ticks, t));
        if (a.piece() == Piece.END_PORTAL_OPEN) { opening(c, a, t); return; }
        if (a.piece() == Piece.END_PORTAL_DORMANT) { dormant(c, a, alpha); return; }
        if (a.piece() == Piece.END_PORTAL_CANCEL) {
            float rest = 1 - smooth(0, a.piece().ticks, t);
            Vec3 focus = PortalGeometry.focus(a.target(), a.origin()).lerp(a.target(), 1 - rest);
            iris(c, focus, TEAL, .3F * rest, rest, t * .1F);
            return;
        }
        int color = a.seed() & 31;
        float[] tint = rgb(MimicColors.color(color));
        Vec3 eye = a.target();
        if (a.piece() == Piece.END_PORTAL_IMPRINT) {
            alpha *= Math.min(1, a.scale());
            iris(c, eye.add(0, .13, 0), tint, .28F, alpha, c.time() * .025F);
            if (c.glow()) {
                c.glow(eye, .45F, tint, alpha * .2F);
                // The stored offering rests in the eye until the neighboring one answers it.
                boolean opening = SetPieceClient.running(Piece.END_PORTAL_OPEN).stream().anyMatch(o -> o.origin().equals(a.origin()));
                if (opening) stream(c, eye, a.origin(), tint, color, alpha * .75F, .06F);
            }
            return;
        }
        float progress = clamp((a.seed() >>> 5) / (float) PortalGeometry.CHANNEL_TICKS, 0, 1);
        if (!(a.bound(c) instanceof LivingEntity player)) return;
        boolean firstPerson = player == Minecraft.getInstance().player
                && Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON;
        float breadth = firstPerson ? .55F : 1;
        if (firstPerson) alpha *= .65F;
        Vec3 feet = player.getPosition(c.partial);
        Vec3 focus = PortalGeometry.focus(eye, a.origin());
        if (!firstPerson && focus.distanceToSqr(c.camera()) < 2.25) alpha *= .35F;
        for (boolean right : new boolean[] {false, true}) {
            Vec3 hand = hand(c, player, focus, right);
            stream(c, hand, focus, tint, color, alpha, (.04F + progress * .045F) * breadth);
            if (c.glow()) c.ring(hand, focus.subtract(hand).normalize(), .07F, .008F, 0, TWO_PI, tint, alpha * .45F);
        }
        stream(c, focus, eye, tint, color, alpha * .8F, (.03F + progress * .035F) * breadth);
        iris(c, focus, tint, (.16F + .22F * progress) * (firstPerson ? .7F : 1), alpha, c.time() * .04F);
        if (c.glow()) {
            c.ring(feet.add(0, .035, 0), .48F, .012F, tint, alpha * .3F);
            c.ring(eye.add(0, .025, 0), UP, .4F, .018F, -HALF_PI, -HALF_PI + TWO_PI * progress, PALE, alpha * .7F);
            // The offering travels out along the ring, rather than lighting all twelve eyes at once.
            BlockPos center = BlockPos.containing(a.origin().x, a.origin().y - .28, a.origin().z);
            int index = 0;
            var frames = PortalGeometry.frames(center);
            for (BlockPos frame : frames) {
                float lit = smooth(index / 12F, (index + 1) / 12F, progress);
                if (lit > 0) {
                    Vec3 at = PortalGeometry.eye(frame), next = PortalGeometry.eye(frames.get((index + 1) % frames.size()));
                    c.ring(at, .16F, .012F, tint, alpha * lit * .5F);
                    c.line(at, next, .035F, tint, alpha * lit * .12F, alpha * lit * .12F);
                    c.line(at, next, .008F, PALE, alpha * lit * .5F, alpha * lit * .25F);
                }
                index++;
            }
        }
    }

    static Vec3 hand(Canvas c, LivingEntity player, Vec3 target, boolean rightHand) {
        var mc = Minecraft.getInstance();
        if (player == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            var camera = mc.gameRenderer.getMainCamera();
            Vec3 look = Vec3.directionFromRotation(camera.getXRot(), camera.getYRot());
            Vec3 right = Vec3.directionFromRotation(0, camera.getYRot() + 90);
            Vec3 up = right.cross(look).normalize();
            return c.camera().add(look.scale(.95)).add(right.scale(rightHand ? .34 : -.34)).subtract(up.scale(.35));
        }
        return PortalGeometry.hand(player, player.getPosition(c.partial), target, rightHand, c.partial);
    }

    private static void dormant(Canvas c, Active a, float alpha) {
        if (!c.glow()) return;
        // A thin rectangular seam respects the actual three-by-three opening, without hiding the empty center.
        Vec3 p = a.origin();
        float breath = .12F + .05F * sin(c.time() * .035F);
        for (int side = 0; side < 4; side++) {
            Vec3 from = corner(p, side, 1.48F), to = corner(p, side + 1, 1.48F);
            c.line(from, to, .009F, TEAL, alpha * breath, alpha * breath);
        }
        for (int i = 0; i < 4; i++) {
            float angle = i * HALF_PI + c.time() * .008F;
            Vec3 at = p.add(cos(angle) * .27, .03, sin(angle) * .27);
            c.line(at, at.add(0, .06, 0), .009F, PALE, 0, alpha * .15F);
        }
    }

    private static void iris(Canvas c, Vec3 eye, float[] tint, float radius, float alpha, float spin) {
        if (c.soft()) c.disc(eye, UP, radius * .85F, DEEP, alpha * .3F, 0);
        if (!c.glow()) return;
        c.ring(eye, radius, .038F, tint, alpha * .12F);
        c.ring(eye.add(0, .003, 0), radius, .012F, tint, alpha * .65F);
        c.ring(eye.add(0, .006, 0), radius, .004F, PALE, alpha * .7F);
        c.ring(eye.add(0, .008, 0), UP, radius * .64F, .008F, spin, spin + PI * 1.35F, tint, alpha * .8F);
        int spokes = minimal() ? 4 : 8;
        for (int i = 0; i < spokes; i++) {
            float r = i * TWO_PI / spokes - spin * .7F;
            Vec3 inner = eye.add(cos(r) * radius * .35, .009, sin(r) * radius * .35);
            Vec3 outer = eye.add(cos(r + .35F) * radius * .88, .009, sin(r + .35F) * radius * .88);
            c.line(inner, outer, .007F, tint, alpha * .1F, alpha * .65F);
        }
    }

    private static void stream(Canvas c, Vec3 from, Vec3 to, float[] tint, int power, float alpha, float width) {
        Vec3 axis = to.subtract(from).normalize();
        Vec3 right = (Math.abs(axis.y) > .9 ? new Vec3(1, 0, 0) : UP).cross(axis).normalize();
        Vec3 up = axis.cross(right).normalize();
        int strands = minimal() ? 1 : 2;
        for (int j = 0; j < strands; j++) {
            Vec3[] points = new Vec3[17];
            for (int i = 0; i < points.length; i++) {
                float u = i / 16F, envelope = sin(u * PI);
                float phase = u * TWO_PI * 1.5F - c.time() * .15F + j * PI;
                float curl = width * 1.6F * envelope;
                points[i] = from.lerp(to, u).add(right.scale(cos(phase) * curl)).add(up.scale(sin(phase) * curl));
            }
            // Matter retains its colour, especially water and amplification's crimson. Only the edges add light.
            if (c.soft()) c.ribbon(points, width * 1.4F, tint, alpha * .5F, alpha * .7F);
            if (c.glow()) {
                c.ribbon(points, width * 2.8F, tint, alpha * .07F, alpha * .12F);
                c.ribbon(points, width * .45F, tint, alpha * .45F, alpha * .6F);
                c.ribbon(points, width * .13F, PALE, alpha * .6F, alpha * .75F);
            }
        }
        if (!c.glow() || minimal()) return;
        if (power == 2 || power == 8 || power == 20) {
            int seed = (int) (c.time() / 2) + power * 71;
            c.bolt(from, to, seed, .075F, .01F, tint, alpha * .65F);
        } else if (power == 0 || power == 7 || power == 4 || power == 16) {
            for (int i = 0; i < 3; i++) {
                float u = (c.time() * .027F + i / 3F) % 1;
                c.ring(from.lerp(to, u), axis, .07F + .10F * u, .008F, 0, TWO_PI, tint, alpha * sin(u * PI) * .7F);
            }
        } else {
            float u = (c.time() * .024F) % 1;
            c.glow(from.lerp(to, u), .09F, tint, alpha * sin(u * PI) * .5F);
        }
    }

    private static void opening(Canvas c, Active a, float t) {
        float progress = clamp(t / PortalGeometry.OPEN_TICKS, 0, 1);
        float spread = smooth(0, .85F, progress), fade = 1 - smooth(PortalGeometry.OPEN_TICKS, a.piece().ticks, t);
        Vec3 center = a.origin().add(0, .025, 0);
        float[] first = rgb(MimicColors.color(a.seed() & 31)), second = rgb(MimicColors.color(a.seed() >>> 5 & 31));
        float half = .03F + 1.47F * spread;
        if (c.soft()) {
            // A bowed, square membrane reaches the exact portal cells; its center sags toward the other dimension.
            Vec3 middle = center.add(0, -.2 * sin(progress * PI), 0);
            for (int i = 0; i < 4; i++) c.tri(middle, corner(center, i, half), corner(center, i + 1, half), VOID, fade * .96F, fade * .85F, fade * .85F);
            c.disc(center.add(0, .007, 0), UP, half * .9F, DEEP, fade * .5F, 0);
        }
        if (!c.glow()) return;
        for (int i = 0; i < 4; i++) {
            Vec3 from = corner(center, i, half), to = corner(center, i + 1, half);
            float[] tint = i % 2 == 0 ? first : second;
            c.line(from, to, .095F, tint, fade * .15F, fade * .15F);
            c.line(from, to, .025F, TEAL, fade * .8F, fade * .8F);
            c.line(from, to, .007F, PALE, fade, fade);
        }
        iris(c, center.add(0, .015, 0), VIOLET, Math.max(.05F, 1.36F * spread), fade * (1 - progress * .6F), -t * .08F);
        if (!minimal()) for (int i = 0; i < 12; i++) {
            float angle = i * TWO_PI / 12 + t * .02F;
            float radius = .1F + 1.25F * spread;
            Vec3 star = center.add(cos(angle) * radius, .025, sin(angle) * radius);
            c.line(star.add(-.035, 0, 0), star.add(.035, 0, 0), .008F, PALE, 0, fade * .65F);
            c.line(star.add(0, 0, -.035), star.add(0, 0, .035), .008F, PALE, 0, fade * .65F);
        }
        if (progress < .3F) c.glow(center, .4F + spread, PALE, sin(progress / .3F * PI) * .3F);
    }

    private static Vec3 corner(Vec3 center, int i, float half) {
        return switch (i & 3) {
            case 0 -> center.add(-half, 0, -half);
            case 1 -> center.add(half, 0, -half);
            case 2 -> center.add(half, 0, half);
            default -> center.add(-half, 0, half);
        };
    }

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }
}
