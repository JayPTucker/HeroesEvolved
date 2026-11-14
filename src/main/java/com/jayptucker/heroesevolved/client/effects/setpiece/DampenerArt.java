package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.dampener.AbilityDampenerBlock;
import com.jayptucker.heroesevolved.dampener.DampenerGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/**
 * The Ability Dampener and the Nullifier, drawn in one shape language: <b>a wave being cancelled</b>. Every other
 * power's rings open outward; a dampener's close inward and go out, as if what reached them had been swallowed. The
 * field is a faint slate lattice over a sphere with rings sliding in across the ground toward the pylon; the shot is a
 * thin grey needle that leaves contracting rings in its wake; a hit closes rings down onto the body; a dampened body
 * wears a broken collar that turns slowly; charging the gun pulls beads of pale current from the machine into it.
 *
 * <p>Palette (no neon: an absence of power, not a power): {@code 1E242B} shadow, {@code 4A5866} slate, {@code 8796A3}
 * steel in the soft pass; {@code BFE3DC} hum and {@code F0F6F4} core in the glow pass.
 */
final class DampenerArt {
    private static final float[] SHADOW = rgb(0x1E242B), SLATE = rgb(0x4A5866), STEEL = rgb(0x8796A3);
    private static final float[] HUM = rgb(0xBFE3DC), CORE = rgb(0xF0F6F4);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Pattern LATTICE = hexes(9, 0.07F, 0.18F);

    private DampenerArt() {}

    static void draw(Canvas c, Active a, float t) {
        if (c.solid()) return;
        switch (a.piece()) {
            case DAMPENER_FIELD -> field(c, a, t);
            case DAMPENER_SHOT -> shot(c, a, t);
            case DAMPENER_HIT -> hit(c, a, t);
            case DAMPENER_MARK -> mark(c, a, t);
            case DAMPENER_CHARGE -> charge(c, a, t);
            default -> {}
        }
    }

    // ------------------------------------------------------------------
    // The field
    // ------------------------------------------------------------------

    private static void field(Canvas c, Active a, float t) {
        Vec3 center = a.origin();
        // The projector went dark: drop the dome at once instead of letting the last copy run out.
        BlockState state = c.level.getBlockState(BlockPos.containing(center));
        if (!(state.getBlock() instanceof AbilityDampenerBlock) || !state.getValue(AbilityDampenerBlock.ACTIVE)) return;
        // The server says how far it had spun when it sent this copy; it keeps growing at the same rate until the next.
        float grown = clamp((float) (a.target().x - center.x) + (grown(a) < 1 ? t / DampenerGeometry.SPIN_UP : 0), 0, 1);
        float r = a.scale() * smooth(0, 1, grown);
        // Re-sent every ten ticks as a twelve-tick piece: two-tick fades let the copies hand over seamlessly.
        float alpha = fade(t, DampenerGeometry.FIELD_PIECE, 2, 2);
        float time = c.level.getGameTime() + c.partial;
        Vec3 floor = center.add(0, -0.47, 0);
        boolean full = grown >= 1;
        if (r < 0.3F) {
            if (c.glow()) c.glow(center.add(0, 0.62, 0), 0.4F, HUM, alpha * 0.6F);
            return;
        }
        Pattern lattice = minimal() ? PLAIN : LATTICE;
        if (c.soft()) {
            // The dome: a lattice that is brightest edge-on, faint where you look through it (or out of it).
            c.shell(center, r, r, r, PI, SLATE, alpha * (full ? 0.18F : 0.26F), 0, lattice, time * 0.002F);
            // Its foot on the ground, the edge a player has to cross.
            c.ring(floor, UP, r, 0.12F, 0, TWO_PI, STEEL, alpha * 0.45F);
            c.ring(floor, UP, r - 0.25F, 0.05F, 0, TWO_PI, SHADOW, alpha * 0.3F);
            return;
        }
        // The lattice's seams also catch a faint light, so the dome still shows against dark ground and at night.
        c.shell(center, r, r, r, PI, HUM, alpha * (full ? 0.07F : 0.14F), 0, lattice, time * 0.002F);
        // Glow: rings sliding in from the edge to the pylon and going out, never out from it.
        int rings = minimal() ? 2 : 4;
        float period = 46;
        for (int i = 0; i < rings; i++) {
            float p = frac(time / period + i / (float) rings);
            float k = 1 - p;
            float ring = 0.6F + (r - 0.6F) * k * k;
            c.ring(floor.add(0, 0.02, 0), UP, ring, 0.05F + 0.06F * k, 0, TWO_PI, HUM, alpha * 0.32F * smooth(0, 0.15F, p) * (1 - smooth(0.75F, 1, p)));
        }
        // A scan line sweeping down the dome, slowly.
        float scan = frac(time / 70F);
        float phi = scan * PI * 0.5F;
        c.ring(center.add(0, r * cos(phi), 0), UP, r * sin(phi), 0.04F, 0, TWO_PI, HUM, alpha * 0.22F * sin(scan * PI));
        // The emitter on top of the pylon breathes.
        float breath = 0.65F + 0.35F * sin(time * 0.12F);
        c.glow(center.add(0, 0.62, 0), 0.32F + 0.08F * breath, HUM, alpha * 0.45F * breath);
        c.glow(center.add(0, 0.62, 0), 0.12F, CORE, alpha * 0.6F);
        if (!full) c.ring(center.add(0, 0.55, 0), UP, 0.4F + r * 0.1F, 0.05F, 0, TWO_PI, CORE, alpha * 0.4F * (1 - grown));
    }

    /** How far the projector had spun when the server sent this copy. */
    private static float grown(Active a) { return (float) (a.target().x - a.origin().x); }

    /** 0 right at the camera, 1 from two blocks off: a glow passing the lens must not fill the screen. */
    private static float clear(Canvas c, Vec3 at) { return smooth(0.5F, 2.0F, (float) at.distanceTo(c.camera())); }

    // ------------------------------------------------------------------
    // The Nullifier
    // ------------------------------------------------------------------

    private static void shot(Canvas c, Active a, float t) {
        Vec3 path = a.target().subtract(a.origin());
        Vec3 from = WeaponArt.muzzle(c, a.bound(c), a.origin());
        Vec3 end = from.add(path);
        double length = path.length();
        if (length < 0.1) return;
        Vec3 dir = path.scale(1 / length);
        float flight = (float) clamp(length / 24, 1, 2.5F);
        float front = smooth(0, flight, t), tail = smooth(flight * 0.5F, flight + 6, t);
        float alpha = fade(t, a.piece().ticks, 0.5F, 6);
        Vec3 head = from.lerp(end, front), back = from.lerp(end, tail);
        if (c.soft()) {
            if (head.distanceToSqr(back) > 0.01) c.line(back, head, 0.05F, STEEL, alpha * 0.15F, alpha * 0.55F);
            // A ring at the muzzle that closes as the round leaves.
            c.ring(from, dir, 0.22F * (1 - smooth(0, 4, t)) + 0.02F, 0.03F, 0, TWO_PI, SLATE, alpha * 0.6F);
            return;
        }
        if (head.distanceToSqr(back) > 0.01) c.line(back, head, 0.018F, CORE, alpha * 0.2F, alpha * 0.85F);
        if (t < flight + 0.5F) c.glow(head, 0.16F, HUM, alpha * 0.7F * clear(c, head));
        // The wake: rings left along the path, each closing down and going out.
        int wakes = minimal() ? 3 : 6;
        for (int i = 1; i <= wakes; i++) {
            float at = i / (wakes + 1F);
            float born = at * flight, age = t - born;
            if (age < 0 || at > front) continue;
            float k = 1 - smooth(0, 5, age);
            c.ring(from.lerp(end, at), dir, 0.05F + 0.25F * k, 0.025F, 0, TWO_PI, HUM, alpha * 0.5F * k);
        }
    }

    private static void hit(Canvas c, Active a, float t) {
        Entity body = a.bound(c);
        float height = Math.max(0.5F, a.scale());
        Vec3 core = body != null ? body.getPosition(c.partial).add(0, height * 0.55, 0) : a.target();
        float width = body != null ? Math.max(0.4F, body.getBbWidth()) : 0.6F;
        float alpha = fade(t, a.piece().ticks, 0.5F, 6);
        if (c.soft()) {
            // A shell that falls in on the body.
            float k = smooth(0, 6, t);
            float rx = width * (1.8F - 1.1F * k), ry = height * (0.95F - 0.4F * k);
            c.shell(core, rx, ry, rx, PI, SLATE, alpha * 0.35F * (1 - k * 0.6F), 0.05F, PLAIN, 0);
            return;
        }
        c.glow(a.origin(), 0.35F * (1 - smooth(0, 4, t)) + 0.05F, CORE, alpha * 0.7F);
        // Three rings closing on the body, staggered, at the chest, the waist and the knees.
        for (int i = 0; i < 3; i++) {
            float age = t - i * 2;
            if (age < 0) continue;
            float k = 1 - smooth(0, 7, age);
            Vec3 at = core.add(0, height * (0.25F - i * 0.25F), 0);
            c.ring(at, UP, width * 0.55F + width * 1.6F * k, 0.04F + 0.04F * k, 0, TWO_PI, HUM, alpha * (0.25F + 0.45F * k));
        }
    }

    private static void mark(Canvas c, Active a, float t) {
        Entity body = a.bound(c);
        if (body == null) return;
        float height = Math.max(0.5F, a.scale()), width = Math.max(0.4F, body.getBbWidth());
        Vec3 feet = body.getPosition(c.partial);
        float alpha = fade(t, DampenerGeometry.MARK_PIECE, 2, 2);
        float time = c.level.getGameTime() + c.partial;
        Minecraft mc = Minecraft.getInstance();
        // A collar would sit inside your own camera: in first person you only see the ring at your feet.
        boolean own = body == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
        float radius = width * 0.85F + 0.12F;
        if (c.soft()) {
            c.ring(feet.add(0, 0.04, 0), UP, radius + 0.1F, 0.06F, 0, TWO_PI, SLATE, alpha * 0.5F);
            if (own) return;
            // The collar: three broken arcs turning slowly round the chest.
            float spin = time * 0.05F;
            for (int i = 0; i < 3; i++) {
                float from = spin + i * TWO_PI / 3;
                c.ring(feet.add(0, height * 0.62, 0), UP, radius, 0.07F, from, from + TWO_PI / 3 * 0.7F, STEEL, alpha * 0.55F);
            }
            return;
        }
        // Now and then the quiet flickers, as if the power pushed against it and was swallowed.
        float flicker = 0.5F + 0.5F * sin(time * 0.9F + a.seed());
        if (!own) c.ring(feet.add(0, height * 0.62, 0), UP, radius * (0.96F + 0.04F * flicker), 0.02F, 0, TWO_PI, HUM, alpha * 0.25F * flicker);
        c.ring(feet.add(0, 0.05, 0), UP, radius * 0.9F, 0.025F, 0, TWO_PI, HUM, alpha * 0.2F);
    }

    // ------------------------------------------------------------------
    // Charging
    // ------------------------------------------------------------------

    private static void charge(Canvas c, Active a, float t) {
        Vec3 from = a.origin().add(0, 0.55, 0);
        Vec3 hand = WeaponArt.muzzle(c, a.bound(c), a.target());
        float alpha = fade(t, a.piece().ticks, 1, 5);
        Vec3 path = hand.subtract(from);
        if (path.lengthSqr() < 0.01) return;
        if (c.soft()) {
            c.line(from, hand, 0.03F, STEEL, alpha * 0.35F, alpha * 0.2F);
            return;
        }
        // Beads of current running from the machine into the gun, more for more rounds taken.
        int beads = Math.clamp(Math.round(a.scale()) + 2, 3, minimal() ? 4 : 9);
        for (int i = 0; i < beads; i++) {
            float p = (t * 0.12F + i / (float) beads) % 1F;
            Vec3 at = from.lerp(hand, p).add(0, sin(p * PI) * 0.25, 0);
            c.glow(at, 0.07F + 0.05F * hash(a.seed(), i), HUM, alpha * 0.7F * sin(p * PI) * clear(c, at));
        }
        c.glow(hand, 0.18F, CORE, alpha * 0.5F * (0.6F + 0.4F * sin(t * 1.3F)) * clear(c, hand));
        c.ring(from, UP, 0.5F * (1 - frac(t / 6)), 0.03F, 0, TWO_PI, HUM, alpha * 0.4F);
    }

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }
}
