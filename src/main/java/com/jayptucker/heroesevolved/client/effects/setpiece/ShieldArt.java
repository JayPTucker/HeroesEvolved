package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.UP;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.hash;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.rgb;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.smooth;
import static net.minecraft.util.Mth.TWO_PI;
import static net.minecraft.util.Mth.cos;
import static net.minecraft.util.Mth.sin;

/**
 * A shield holding against a nuclear blast, in the look of the power that raised it: a force-field lattice, a
 * shimmering kinetic shell, plates of assembled matter, a crown of ice, or rings of sound. It flares up as the wave
 * hits, with ripples running across the face turned to the blast, then stands faint and flickering while the fallout
 * settles, and fades.
 */
final class ShieldArt {
    private static final float RADIUS = 1.45F;
    private static final float[] KINETIC = rgb(0xC9A8FF), KINETIC_EDGE = rgb(0xF2E8FF), STONE = rgb(0x8D8A84),
            SEAM = rgb(0xFFD58A), ICE = rgb(0xBFEFFF), ICE_DEEP = rgb(0x6FB8E8), SOUND = rgb(0xFFCF7A), SOUND_PALE = rgb(0xFFF4DC),
            FLASH = rgb(0xFFF6E0);

    private ShieldArt() {}

    static void draw(Canvas c, Active a, float t) {
        int kind = Math.round(a.scale()) - 1;
        Entity e = a.bound(c);
        Vec3 feet = e != null ? e.getPosition(c.partial) : a.origin();
        Vec3 body = feet.add(0, 0.95, 0);
        Vec3 toBlast = a.target().subtract(a.origin());
        Vec3 dir = toBlast.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : toBlast.normalize();
        float life = a.piece().ticks;
        // Up at once, full strength through the wave, then faint and flickering while it keeps the fallout off.
        float rise = smooth(0, 4, t), hold = 1 - 0.7F * smooth(30, 50, t), out = 1 - smooth(life - 20, life, t);
        float flicker = t > 50 ? 0.75F + 0.25F * sin(t * 1.7F) * sin(t * 0.63F + 1) : 1;
        float alpha = rise * hold * out * flicker;
        if (alpha <= 0.003F) return;
        float r = RADIUS * (0.6F + 0.4F * rise);
        float spin = (c.level == null ? t : (c.level.getGameTime() % 24000L) + c.partial) * 0.02F;
        switch (kind) {
            case 0 -> ForceArt.cage(c, body, r, spin, UP, alpha, 0.5F, ForceArt.EDGE);
            case 1 -> kinetic(c, body, r, spin, alpha);
            case 2 -> matter(c, body, dir, r, alpha, a.seed());
            case 3 -> ice(c, body, dir, r, alpha, a.seed());
            default -> sound(c, body, r, t, alpha);
        }
        // The wave breaking on the face turned to the blast: a flash and ripples running out across it.
        if (c.glow() && t < 16) {
            float k = smooth(0, 16, t);
            Vec3 face = body.add(dir.scale(r * 0.95));
            Vec3 front = body.add(dir.scale(r * 0.9));
            c.glow(front, (0.8F + 1.6F * (1 - k)) * rise, FLASH, 0.9F * (1 - k));
            for (int i = 0; i < 3; i++) {
                float ti = Mth.clamp((t - i * 2.5F) / 10, 0, 1);
                if (ti <= 0 || ti >= 1) continue;
                c.ring(face, dir, 0.2F + ti * r * 1.1F, 0.06F, 0, TWO_PI, kind == 3 ? ICE : kind == 4 ? SOUND : kind == 2 ? SEAM : FLASH, 0.8F * (1 - ti));
            }
        }
    }

    /** A shimmering lavender shell with rings of pressure turning round it. */
    private static void kinetic(Canvas c, Vec3 at, float r, float spin, float alpha) {
        if (!c.glow()) return;
        c.shell(at, r, r, r, Mth.PI, KINETIC, alpha * 0.4F, 0.05F, Canvas.PLAIN, spin);
        for (int i = 0; i < 3; i++) {
            Vec3 axis = new Vec3(sin(spin * 2 + i * 2.1F), 0.6, cos(spin * 2 + i * 2.1F)).normalize();
            c.ring(at, axis, r * (1.01F + i * 0.02F), 0.04F, 0, TWO_PI, KINETIC_EDGE, alpha * 0.6F);
        }
        c.glow(at, r * 1.2F, KINETIC, alpha * 0.1F);
    }

    /** Plates of assembled matter locked into a dome on the side facing the blast, with hot seams between them. */
    private static void matter(Canvas c, Vec3 at, Vec3 dir, float r, float alpha, int seed) {
        Vec3 side = new Vec3(dir.z, 0, -dir.x);
        if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = dir.cross(side).normalize();
        int i = 0;
        for (int row = -2; row <= 2; row++) for (int col = -3; col <= 3; col++, i++) {
            float yaw = col * 0.42F, pitch = row * 0.42F;
            Vec3 n = dir.scale(cos(yaw) * cos(pitch)).add(side.scale(sin(yaw) * cos(pitch))).add(up.scale(sin(pitch)));
            Vec3 at2 = at.add(n.scale(r));
            float yawDeg = (float) Math.toDegrees(Math.atan2(-n.x, n.z)), pitchDeg = (float) Math.toDegrees(Math.asin(Mth.clamp((float) n.y, -1, 1)));
            if (c.solid()) c.box(at2, 0.5F, 0.5F, 0.08F, yawDeg, pitchDeg, hash(seed, i) * 4, STONE, alpha > 0.2F ? 1 : 0);
            if (c.glow()) c.glow(at2, 0.18F, SEAM, alpha * 0.35F);
        }
    }

    /** A crown of ice shards standing out round the body, over a pale shell. */
    private static void ice(Canvas c, Vec3 at, Vec3 dir, float r, float alpha, int seed) {
        if (c.glow()) c.shell(at, r, r, r, Mth.PI, ICE, alpha * 0.3F, 0.06F, Canvas.PLAIN, 0);
        if (!c.soft() && !c.solid()) return;
        for (int i = 0; i < 14; i++) {
            float ang = i * TWO_PI / 14 + hash(seed, i) * 0.3F, lift = (hash(seed, i + 20) - 0.5F) * 1.2F;
            Vec3 n = new Vec3(cos(ang), lift, sin(ang)).normalize();
            Vec3 base = at.add(n.scale(r * 0.7)), tip = at.add(n.scale(r * (1.15 + 0.3 * hash(seed, i + 40))));
            if (c.soft()) c.shard(base, tip, 0.12F + 0.06F * hash(seed, i + 60), 5, hash(seed, i + 80), i % 3 == 0 ? ICE_DEEP : ICE, alpha * 0.85F);
        }
    }

    /** Rings of sound standing round the body at every angle, pulsing outward and back. */
    private static void sound(Canvas c, Vec3 at, float r, float t, float alpha) {
        if (!c.glow()) return;
        for (int i = 0; i < 5; i++) {
            float pulse = 1 + 0.06F * sin(t * 0.8F + i * 1.3F);
            Vec3 axis = new Vec3(sin(i * 1.26F), 0.4F + i * 0.15F, cos(i * 1.26F)).normalize();
            c.ring(at, axis, r * pulse, 0.05F, 0, TWO_PI, i % 2 == 0 ? SOUND : SOUND_PALE, alpha * 0.7F);
        }
        c.glow(at, r * 1.3F, SOUND, alpha * 0.12F);
    }
}
