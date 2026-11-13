package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.sonic.SonicGeometry;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/**
 * Sound as Emma Coolidge sees it, for every Sonic Resonance move. One shape language from four primitives:
 * <ul>
 *   <li>{@link #front}: a wavefront, a ring whose edge ripples with compression;</li>
 *   <li>{@link #trace}: a waveform drawn along a line, fading where it passes the camera;</li>
 *   <li>{@link #staff}: the five lines of a stave bent round an arc;</li>
 *   <li>{@link #glyph}: a note head with its stem.</li>
 * </ul>
 * The palette is chromesthesia, not neon: rose, amber, mint, azure and violet blended along each sound by
 * {@link #tone}, with a warm white core. Silence (Quiet Zone, Silence Dome) is the same shapes inverted: dull
 * slate rings that close inward instead of opening.
 */
final class SonicArt {
    private static final float[][] TONES = {rgb(0xFF6F91), rgb(0xFFB547), rgb(0x7BE6C4), rgb(0x5DA8FF), rgb(0xA97CFF)};
    private static final float[] CORE = rgb(0xFFF8EE), HUSH = rgb(0x2B2A3D), HUSH_EDGE = rgb(0x8C8AA8), GOLD = rgb(0xFFD27A);
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private SonicArt() {}

    /** A colour along the five tones, blended; {@code k} wraps. */
    static float[] tone(float k) {
        float f = (k - (float) Math.floor(k)) * 5;
        int i = (int) f;
        return mix(TONES[i % 5], TONES[(i + 1) % 5], f - i);
    }

    static void draw(Canvas c, Active a, float t) {
        if (c.solid()) return;
        Vec3 at = a.origin(), end = a.target();
        Entity bound = a.bound(c);
        Player owner = bound instanceof Player p ? p : null;
        float clock = (c.level.getGameTime() % 2400 + c.partial) / 20F;
        float life = a.piece().ticks;
        float size = a.scale();
        switch (a.piece()) {
            case SONIC_CRY -> cry(c, owner, at, end, t, size);
            case SONIC_SCREAM -> scream(c, owner, at, end, t, clock);
            case SONIC_NOTE -> note(c, at, t, size, clock);
            case SONIC_NOTE_BURST -> noteBurst(c, at, t, size, a.seed());
            case SONIC_WALL -> {
                if (bound != null && SetPieceClient.superseded(a, Set.of(Piece.SONIC_WALL_PUSH), c.level.getGameTime())) return;
                stave(c, at, end.subtract(at).normalize(), size, clock, fade(t, life, 2, 2), 0);
            }
            case SONIC_WALL_PUSH -> {
                Vec3 normal = end.subtract(at).normalize();
                Vec3 center = SonicGeometry.pushed(at, normal, t);
                float alpha = fade(t, life, 0.5F, 3);
                stave(c, center, normal, size, clock * 2, alpha, smooth(0, 3, t) * 0.35F);
                for (int i = 1; i <= 3; i++) {
                    Vec3 behind = center.subtract(normal.scale(i * 0.9));
                    front(c, behind, normal, size * (0.95F - i * 0.12F), 0.05F, clock + i * 0.13F, alpha * (0.55F - i * 0.12F), 0.04F, clock * 6);
                }
            }
            case SONIC_VAULT -> vault(c, owner, at, end, t);
            case SONIC_DIVE -> {
                Vec3 feet = owner == null ? at : owner.getPosition(c.partial);
                float alpha = fade(t, life, 1, 2) * 0.7F;
                for (int i = 0; i < 4; i++) {
                    float k = frac(clock * 2.5F + i / 4F);
                    front(c, feet.add(0, -0.3 - k * 2.4, 0), UP, 0.45F + k * 1.1F, 0.06F, clock + i * 0.2F, alpha * (1 - k), 0.05F, clock * 8);
                }
                if (c.glow()) for (int i = 0; i < 3; i++) {
                    float ang = i * TWO_PI / 3 + clock * 3;
                    Vec3 base = feet.add(cos(ang) * 0.45, 0.2, sin(ang) * 0.45);
                    trace(c, base, base.add(0, 2.6, 0), 0.08F, 0.02F, 3, clock * 20, 0.04F, clock + i * 0.3F, alpha * 0.8F, null);
                }
            }
            case SONIC_LANDING -> landing(c, at, t, size, life);
            case SONIC_GATHER -> gather(c, owner == null ? at : owner.getPosition(c.partial), t, size, clock, life);
            case SONIC_CRESCENDO -> crescendo(c, owner, at, end, t, size);
            case SONIC_SHRIEK -> shriek(c, owner, at, end, t, clock);
            case SONIC_RING -> {
                if (bound == null) return;
                Vec3 mid = bound.getPosition(c.partial).add(0, bound.getBbHeight() * 0.55, 0);
                float alpha = fade(t, life, 2, 2) * 0.75F, r = Math.max(0.45F, size * 0.75F);
                float shiver = sin(clock * 55) * 0.04F;
                for (int i = 0; i < 2; i++) {
                    float tilt = 0.5F + i * 0.6F, spin = clock * (2.4F + i) + i * PI;
                    Vec3 axis = new Vec3(sin(tilt) * cos(spin), cos(tilt), sin(tilt) * sin(spin));
                    front(c, mid, axis, r + shiver, 0.035F, 0.2F + i * 0.1F, alpha, 0.03F, clock * 30);
                }
                if (c.glow()) c.glow(mid, 0.3F, TONES[1], alpha * 0.35F);
            }
            case SONIC_SHATTER -> shatter(c, owner, at, end, t, size, a.seed());
            case SONIC_DISSONANCE -> dissonance(c, owner == null ? at : owner.getPosition(c.partial), t, size, life);
            case SONIC_ECHO -> echo(c, at, t, size, life);
            case SONIC_GUARD -> {
                float alpha = fade(t, life, 2, 2);
                for (int i = 0; i < 6; i++) {
                    float lat = (i + 0.5F) / 6 * HALF_PI, y = size * sin(lat) * 0.8F, r = size * cos(lat);
                    float ripple = 0.025F * sin(clock * 4 + i);
                    front(c, at.add(0, y + 0.05, 0), UP, r, 0.045F, clock * 0.4F + i * 0.08F, alpha * (0.42F - i * 0.04F), ripple, clock * 5 + i);
                }
                if (c.soft()) c.disc(at.add(0, 0.04, 0), UP, size, TONES[3], 0.05F * alpha, 0);
            }
            case SONIC_QUIET, SILENCE_DOME -> hush(c, at, t, size, clock, life);
            case RESONANCE_CASCADE -> cascade(c, end, t, size, clock);
            case BATTLE_HYMN -> hymn(c, a.at(c), t, clock, life);
            default -> { }
        }
    }

    // ------------------------------------------------------------------
    // The moves
    // ------------------------------------------------------------------

    /** The Cry: wavefronts rolling out of the mouth down a narrow cone, two waveforms braided along it. */
    private static void cry(Canvas c, Player owner, Vec3 at, Vec3 end, float t, float size) {
        Vec3 from = owner != null && t < 3 ? mouth(c, owner) : at;
        float length = (float) from.distanceTo(end), reach = Math.min(length, t * 6);
        float alpha = fade(t, 14, 0.5F, 6);
        Vec3 dir = end.subtract(from).normalize();
        float slope = size > 1.2F ? 0.1F : 0.075F;
        for (float d = 0.6F; d < reach; d += 1.1F) {
            float age = (reach - d) / 6;
            front(c, from.add(dir.scale(d)), dir, (0.3F + d * slope) * size, 0.08F * size, d * 0.04F + t * 0.02F,
                    alpha * clamp(1 - age * 0.18F, 0, 1) * near(c, from.add(dir.scale(d))) * smooth(0.5F, 3, d), 0.04F, d * 2 + t);
        }
        Vec3 tip = from.add(dir.scale(reach));
        trace(c, from, tip, 0.08F, 0.2F + reach * slope * size, reach / 1.6F, t * 3, 0.06F * size, 0.1F, alpha * 0.85F, null);
        trace(c, from, tip, 0.08F, 0.2F + reach * slope * size, reach / 1.6F, t * 3 + PI, 0.06F * size, 0.6F, alpha * 0.85F, null);
        if (reach >= length - 0.1F && c.glow()) {
            float hit = clamp((t - length / 6) / 4, 0, 1);
            front(c, end, dir, (0.6F + 1.4F * hit) * size, 0.08F, 0.35F, alpha * (1 - hit), 0.1F, t);
            c.glow(end, 0.5F * size, CORE, 0.45F * alpha * (1 - hit));
        }
    }

    /** The held scream: a steady current of fronts flowing out along the cone, its waveforms crowded and shaking. */
    private static void scream(Canvas c, Player owner, Vec3 at, Vec3 end, float t, float clock) {
        Vec3 from = owner != null ? mouth(c, owner) : at;
        if (owner != null) end = from.add(owner.getViewVector(c.partial).scale(end.distanceTo(at)));
        float length = (float) from.distanceTo(end), alpha = fade(t, 5, 1, 2) * 0.5F;
        Vec3 dir = end.subtract(from).normalize();
        float flow = frac(clock * 6) * 1.1F;
        for (float d = 0.6F + flow; d < length; d += 1.1F)
            front(c, from.add(dir.scale(d)), dir, 0.25F + d * 0.05F, 0.06F, d * 0.05F + clock * 0.3F,
                    alpha * clamp(1 - d / length, 0.25F, 1) * near(c, from.add(dir.scale(d))), 0.08F, clock * 40 + d);
        for (int i = 0; i < 3; i++)
            trace(c, from, end, 0.06F, 0.15F + length * 0.05F, length / 1.1F, clock * 40 + i * TWO_PI / 3, 0.045F,
                    clock * 0.2F + i * 0.33F, alpha * 0.6F, null);
    }

    /** A Resonant Note playing: a note head that bobs to the phrase, a turning stave and a front on every beat. */
    private static void note(Canvas c, Vec3 at, float t, float size, float clock) {
        boolean harmonic = size >= SonicGeometry.HARMONIC_FLAG;
        float fed = harmonic ? size - SonicGeometry.HARMONIC_FLAG : size;
        float alpha = fade(t, 12, 2, 2);
        Vec3 head = at.add(0, sin(clock * PI * 2) * 0.08, 0);
        glyph(c, head, 0.2F * fed, harmonic ? GOLD : tone(clock * 0.2F), alpha, true);
        float turn = clock * 1.4F;
        staff(c, at.add(0, -0.25, 0), 0.75F * fed, turn, turn + PI * 1.4F, clock * 0.2F, alpha * 0.7F);
        float beat = frac(clock * 2);
        front(c, at, UP, 0.3F + beat * 2.6F * fed, 0.05F, clock * 0.2F + 0.4F, alpha * 0.55F * (1 - beat), 0.05F, clock * 6);
        if (!minimal()) front(c, at, new Vec3(0.4, 0.2, 1), 0.3F + beat * 1.8F * fed, 0.035F, clock * 0.2F + 0.7F,
                alpha * 0.35F * (1 - beat), 0.04F, clock * 4);
        if (harmonic && c.glow()) c.glow(at, 0.55F, GOLD, 0.25F * alpha);
    }

    /** The Note breaks: fronts on three planes snap open, its notes flung out; a harmonic adds a gold chord. */
    private static void noteBurst(Canvas c, Vec3 at, float t, float size, int seed) {
        boolean harmonic = size >= SonicGeometry.HARMONIC_FLAG;
        float r = harmonic ? size - SonicGeometry.HARMONIC_FLAG : size;
        // A sphere drawn as level rings (an upright ring seen edge-on reads as a laser line).
        float[] lat = {-0.5F, 0, 0.5F};
        for (int i = 0; i < 4; i++) {
            float p = clamp((t - i * 1.6F) / 10, 0, 1), open = 1 - (1 - p) * (1 - p) * (1 - p);
            float alpha = (1 - p) * (0.85F - i * 0.12F), rr = r * open * (0.6F + i * 0.13F);
            for (int k = 0; k < 3; k++)
                front(c, at.add(0, lat[k] * rr, 0), UP, rr * (k == 1 ? 1 : 0.87F), 0.07F, i * 0.18F + k * 0.25F, alpha, 0.03F, t * 2 + k);
        }
        if (harmonic) for (int i = 0; i < 3; i++) {
            float p = clamp((t - 2 - i * 2) / 14, 0, 1);
            front(c, at.add(0, 0.1, 0), UP, r * (0.3F + 0.9F * p), 0.09F, 0.2F, (1 - p) * 0.7F, 0, 0);
            if (c.glow()) c.ring(at.add(0, 0.1, 0), UP, r * (0.3F + 0.9F * p), 0.025F, 0, TWO_PI, GOLD, (1 - p) * 0.8F);
        }
        int flung = minimal() ? 4 : 9;
        for (int i = 0; i < flung; i++) {
            float yaw = hash(seed, i) * TWO_PI, pitch = (hash(seed, i + 40) - 0.3F) * 1.3F;
            Vec3 dir = new Vec3(cos(yaw) * cos(pitch), sin(pitch), sin(yaw) * cos(pitch));
            float p = clamp(t / 18, 0, 1), go = 1 - (1 - p) * (1 - p);
            glyph(c, at.add(dir.scale(r * 0.95F * go)).add(0, -0.4 * p * p, 0), 0.16F, tone(hash(seed, i + 80)), 1 - p, i % 2 == 0);
        }
        if (c.glow() && t < 6) c.glow(at, 1.1F + r * 0.2F, harmonic ? GOLD : CORE, 0.6F * (1 - t / 6));
    }

    /** Sonic Vault: a short cry down and back at the ground, which throws the singer the other way. */
    private static void vault(Canvas c, Player owner, Vec3 at, Vec3 end, float t) {
        Vec3 from = owner != null && t < 4 ? mouth(c, owner) : at.add(0, 1.4, 0);
        float alpha = fade(t, 16, 0.5F, 7);
        Vec3 dir = end.subtract(from).normalize();
        float length = (float) from.distanceTo(end), reach = Math.min(length, t * 5);
        for (float d = 0.5F; d < reach; d += 0.9F)
            front(c, from.add(dir.scale(d)), dir, 0.2F + d * 0.12F, 0.05F, d * 0.07F, alpha * near(c, from.add(dir.scale(d))), 0.06F, d * 3);
        if (reach >= length - 0.1F) for (int i = 0; i < 3; i++) {
            float p = clamp((t - length / 5 - i * 1.5F) / 9, 0, 1);
            front(c, end.add(0, 0.06, 0), UP, 0.4F + p * (2.6F - i * 0.4F), 0.08F, 0.15F + i * 0.2F, (1 - p) * alpha, 0.05F, t);
        }
    }

    /** A landing on a shockwave: rings racing out across the ground. */
    private static void landing(Canvas c, Vec3 at, float t, float radius, float life) {
        Vec3 ground = at.add(0, 0.07, 0);
        for (int i = 0; i < 4; i++) {
            float p = clamp((t - i * 2) / 14, 0, 1), open = 1 - (1 - p) * (1 - p) * (1 - p);
            front(c, ground, UP, radius * open, 0.09F, 0.05F + i * 0.18F, (1 - p) * 0.8F, 0.06F, t * 2 + i);
        }
        if (c.soft() && t < 10) c.disc(ground, UP, radius * 0.5F, TONES[3], 0.12F * (1 - t / 10), 0);
    }

    /** Crescendo building: fronts closing in on the chest from every side, a stave climbing round the body. */
    private static void gather(Canvas c, Vec3 feet, float t, float built, float clock, float life) {
        float alpha = fade(t, life, 1, 2) * 0.55F;
        Vec3 chest = feet.add(0, 1.2, 0);
        for (int i = 0; i < 3; i++) {
            float k = 1 - frac(clock * 1.2F + i / 3F);
            front(c, chest, new Vec3(sin(i * 2.1F) * 0.3F, 1, cos(i * 2.1F) * 0.3F), 0.4F + k * (1.2F + built * 0.8F), 0.06F,
                    clock * 0.3F + i * 0.33F, alpha * (1 - k) * 1.4F, 0.015F + built * 0.02F, clock * 12);
        }
        float turn = clock * (1 + built * 3);
        staff(c, feet.add(0, 0.2 + built * 1.3, 0), 1.0F + built * 0.25F, turn, turn + PI * (0.8F + built), clock * 0.15F, alpha * 1.3F);
        if (c.glow()) c.glow(chest, 0.25F + built * 0.45F, CORE, alpha * (0.4F + built * 0.6F));
    }

    /** Crescendo: a wide cone of every colour, fronts packed close, four waveforms spiralling round it. */
    private static void crescendo(Canvas c, Player owner, Vec3 at, Vec3 end, float t, float size) {
        Vec3 from = owner != null && t < 3 ? mouth(c, owner) : at;
        float length = (float) from.distanceTo(end), reach = Math.min(length, t * 4.5F);
        float alpha = fade(t, 30, 0.5F, 12);
        Vec3 dir = end.subtract(from).normalize();
        Vec3[] b = basis(dir);
        float slope = 0.2F + 0.06F * size;
        for (float d = 0.8F; d < reach; d += 1.0F) {
            float age = (reach - d) / 4.5F;
            front(c, from.add(dir.scale(d)), dir, 0.3F + d * slope, 0.06F + d * 0.004F, d * 0.06F,
                    alpha * clamp(1 - age * 0.25F, 0, 1) * near(c, from.add(dir.scale(d))), 0.07F, d * 1.5F + t * 2);
        }
        int strands = minimal() ? 2 : 4;
        for (int s = 0; s < strands; s++) {
            Vec3[] spiral = new Vec3[40];
            float[] w = new float[40], al = new float[40];
            for (int i = 0; i < 40; i++) {
                float d = reach * i / 39, ang = s * TWO_PI / strands + d * 0.5F - t * 0.4F, r = 0.2F + d * slope * 0.8F;
                spiral[i] = from.add(dir.scale(d)).add(b[0].scale(cos(ang) * r)).add(b[1].scale(sin(ang) * r));
                w[i] = 0.05F + d * 0.004F;
                al[i] = alpha * 0.7F * near(c, spiral[i]);
            }
            if (c.glow()) c.ribbon(spiral, w, tone(s / (float) strands + t * 0.01F), al);
        }
        if (reach >= length - 0.1F && c.glow()) {
            float hit = clamp((t - length / 4.5F) / 8, 0, 1);
            c.glow(end, (1.5F + length * slope * 0.6F) * (0.4F + hit), CORE, 0.35F * (1 - hit) * alpha);
        }
    }

    /** Focused Shriek: one needle of sound, fine and fast, that rings its target like a struck bell. */
    private static void shriek(Canvas c, Player owner, Vec3 at, Vec3 end, float t, float clock) {
        Vec3 from = owner != null && t < 3 ? mouth(c, owner) : at;
        float length = (float) from.distanceTo(end), reach = Math.min(length, t * 14);
        float alpha = fade(t, 14, 0.5F, 6);
        Vec3 dir = end.subtract(from).normalize();
        Vec3 tip = from.add(dir.scale(reach));
        trace(c, from, tip, 0.02F, 0.05F, reach * 2.2F, t * 6, 0.03F, 0.05F, alpha, null);
        if (c.glow()) {
            float[] al = {0, alpha * 0.9F};
            c.ribbon(new Vec3[] {from, tip}, new float[] {0.015F, 0.03F}, CORE, new float[] {al[0], al[1] * near(c, tip)});
        }
        for (float d = 1; d < reach; d += 0.8F)
            front(c, from.add(dir.scale(d)), dir, 0.09F, 0.025F, d * 0.03F, alpha * 0.5F * near(c, from.add(dir.scale(d))), 0, 0);
        if (reach >= length - 0.1F) {
            float hit = clamp((t - length / 14) / 6, 0, 1);
            for (int i = 0; i < 2; i++)
                front(c, end, i == 0 ? dir : UP, 0.3F + 1.2F * hit, 0.05F, 0.2F + i * 0.1F, alpha * (1 - hit), 0.1F, clock * 30);
        }
    }

    /** Shatter Note: a finger-snap of sound flicks out, finds the frequency, and the point cracks like glass. */
    private static void shatter(Canvas c, Player owner, Vec3 at, Vec3 end, float t, float half, int seed) {
        Vec3 from = owner != null && t < 3 ? SonicGeometry.hand(owner, c.partial, 0.6) : at;
        float travel = clamp(t / 3, 0, 1);
        float alpha = fade(t, 20, 0.5F, 8);
        if (travel < 1) trace(c, from, from.lerp(end, travel), 0.02F, 0.08F, (float) from.distanceTo(end) * travel, t * 4, 0.035F, 0.55F, alpha, null);
        float s = t - 3;
        if (s < 0) return;
        float open = 1 - (float) Math.pow(1 - clamp(s / 8, 0, 1), 3);
        float r = (half + 0.8F) * open, fadeOut = 1 - clamp(s / 15, 0, 1);
        front(c, end, UP, r, 0.06F, 0.45F, alpha * fadeOut, 0.12F, s);
        front(c, end, new Vec3(1, 0, 0), r * 0.85F, 0.05F, 0.55F, alpha * fadeOut, 0.12F, s + 1);
        front(c, end, new Vec3(0, 0, 1), r * 0.85F, 0.05F, 0.65F, alpha * fadeOut, 0.12F, s + 2);
        if (c.glow()) {
            int flecks = minimal() ? 5 : 12;
            for (int i = 0; i < flecks; i++) {
                float yaw = hash(seed, i) * TWO_PI, pitch = (hash(seed, i + 20) - 0.5F) * PI;
                Vec3 d = new Vec3(cos(yaw) * cos(pitch), sin(pitch), sin(yaw) * cos(pitch));
                Vec3 p0 = end.add(d.scale(r * 0.6F)), p1 = end.add(d.scale(r * 0.6F + 0.35F));
                c.line(p0, p1, 0.03F, i % 3 == 0 ? CORE : TONES[2], alpha * fadeOut, 0);
            }
            if (s < 4) c.glow(end, 0.7F, CORE, alpha * (1 - s / 4));
        }
    }

    /** Dissonant Wave: two sets of fronts from slightly different centres, clashing colours, jagged and beating. */
    private static void dissonance(Canvas c, Vec3 feet, float t, float radius, float life) {
        float alpha = fade(t, life, 0.5F, 8);
        Vec3 low = feet.add(0, 0.3, 0);
        for (int set = 0; set < 2; set++) {
            Vec3 center = low.add(set == 0 ? 0.35 : -0.35, set * 0.2, 0);
            for (int i = 0; i < 3; i++) {
                float p = clamp((t - i * 2.2F - set) / 12, 0, 1), open = 1 - (1 - p) * (1 - p);
                front(c, center, UP, radius * open, 0.07F, set == 0 ? 0.02F : 0.6F, alpha * (1 - p) * 0.75F,
                        0.12F + 0.05F * sin(t * 3), t * (set == 0 ? 4 : -4));
            }
        }
        float beat = abs(sin(t * 1.3F));
        if (c.soft()) c.disc(feet.add(0, 0.05, 0), UP, radius * clamp(t / 10, 0, 1), TONES[0], 0.08F * alpha * beat, 0);
    }

    /** Echo Map: a sonar sweep, a level ring and a standing ring racing out to the edge, then fading back. */
    private static void echo(Canvas c, Vec3 at, float t, float radius, float life) {
        float r = Math.min(radius, t * SonicGeometry.ECHO_SPEED * Math.max(1, radius / 16)), alpha = fade(t, life, 1, 14);
        Vec3 floor = at.add(0, 0.1, 0);
        front(c, floor, UP, r, 0.08F, 0.55F, alpha * 0.8F, 0.015F, t);
        // The upper half of the sweep, as level rings rising into a dome.
        for (int i = 1; i <= 3; i++) {
            float k = i / 4F;
            front(c, floor.add(0, r * k * 0.8F, 0), UP, r * (float) Math.sqrt(1 - k * k), 0.05F, 0.6F + i * 0.04F, alpha * (0.5F - i * 0.1F), 0, 0);
        }
        for (int i = 1; i <= 2; i++) {
            float back = r - i * 1.4F;
            if (back > 0.3F) front(c, floor, UP, back, 0.04F, 0.55F + i * 0.05F, alpha * (0.4F - i * 0.12F), 0, 0);
        }
    }

    /** Silence: slate rings closing inward instead of opening, over a dark floor where sound goes to die. */
    private static void hush(Canvas c, Vec3 at, float t, float radius, float clock, float life) {
        float alpha = fade(t, life, 2, Math.min(16, life * 0.2F)) * smooth(0, 6, t);
        Vec3 floor = at.add(0, 0.05, 0);
        if (c.soft()) {
            c.disc(floor, UP, radius, HUSH, 0.22F * alpha, 0.05F * alpha);
            c.shell(at, radius, radius * 0.7F, radius, HALF_PI, HUSH_EDGE, 0.06F * alpha, 0.3F, PLAIN, 0);
        }
        if (!c.glow()) return;
        for (int i = 0; i < 4; i++) {
            float k = 1 - frac(clock * 0.35F + i / 4F);
            float h = 0.3F + i * 0.45F, r = radius * k;
            float dash = clock * 0.6F + i;
            for (int seg = 0; seg < 6; seg++) {
                float from = dash + seg * TWO_PI / 6;
                c.ring(at.add(0, h * k, 0), UP, r, 0.03F, from, from + 0.6F, HUSH_EDGE, alpha * 0.55F * (1 - k * 0.6F));
            }
        }
        c.ring(floor, UP, radius, 0.05F, 0, TWO_PI, HUSH_EDGE, alpha * 0.5F);
    }

    /** Resonance Cascade: standing waves stack up over the point, then fold in on themselves and burst. */
    private static void cascade(Canvas c, Vec3 o, float t, float r, float clock) {
        float build = 16;
        float squeeze = t < build ? 1 : clamp(1 - (t - build) / 4, 0, 1);
        int rings = Math.min(8, (int) (t / 2) + 1);
        for (int i = 0; i < rings; i++) {
            float h = 0.3F + i * 0.45F, rr = r * (0.55F + 0.45F * sin((i + 1) / 9F * PI)) * squeeze;
            front(c, o.add(0, h, 0), UP, rr, 0.06F, i * 0.12F + clock * 0.2F, 0.75F, 0.07F * sin(t * 0.8F + i), t * 0.6F + i);
        }
        if (t < build) staff(c, o.add(0, 0.2, 0), r * 1.05F, clock * 2, clock * 2 + PI * 1.6F * t / build, clock * 0.2F, 0.6F);
        if (t >= build) {
            float s = t - build, p = clamp(s / 12, 0, 1);
            front(c, o.add(0, 0.1, 0), UP, r * 1.3F * p, 0.12F, 0.1F, 0.85F * (1 - p), 0.05F, s);
            front(c, o.add(0, 1.2, 0), UP, r * 1.1F * p, 0.1F, 0.5F, 0.6F * (1 - p), 0.1F, s * 2);
            if (s < 5 && c.glow()) c.glow(o.add(0, 1.6, 0), 4, CORE, 0.8F * (1 - s / 5));
        }
    }

    /** Battle Hymn: a stave circles the singer, notes rising off it on every beat. */
    private static void hymn(Canvas c, Vec3 o, float t, float clock, float life) {
        float f = fade(t, life, 8, 16);
        staff(c, o.add(0, 1.0, 0), 1.45F, t * 0.06F, t * 0.06F + PI * 1.5F, clock * 0.1F, 0.75F * f);
        for (int i = 0; i < 10; i++) {
            float k = ((t + i * 8) % 80) / 80, ang = i * 2.39996F + k * 1.5F, rr = 1.45F + k * 1.6F;
            Vec3 head = o.add(cos(ang) * rr, 1.1 + k * 2.4, sin(ang) * rr);
            glyph(c, head, 0.15F, tone(i * 0.1F), f * sin(k * PI), i % 2 == 0);
        }
        float beat = (t % 10) / 10;
        front(c, o.add(0, 0.06, 0), UP, 0.8F + beat * 3, 0.07F, clock * 0.2F, 0.5F * f * (1 - beat), 0.03F, t);
    }

    /** The Wall of Sound: a stave stood on end. Its five lines bend through standing-wave nodes; faint verticals between. */
    private static void stave(Canvas c, Vec3 center, Vec3 normal, float half, float clock, float alpha, float lean) {
        if (alpha < 0.01F) return;
        Vec3 side = new Vec3(-normal.z, 0, normal.x).normalize();
        int n = 24;
        for (int line = 0; line < 5; line++) {
            float y = -1.05F + line * 0.65F;
            Vec3[] pts = new Vec3[n + 1];
            for (int s = 0; s <= n; s++) {
                float x = -half + 2 * half * s / n;
                float stand = sin(x / half * PI * 2) * sin(clock * 7 + line) * 0.18F;
                pts[s] = center.add(side.scale(x)).add(0, y + stand * 0.4F, 0).add(normal.scale(stand + lean * (y + 1.4F) / 2.8F));
            }
            if (c.soft()) c.ribbon(pts, 0.14F, tone(line * 0.2F + clock * 0.05F), alpha * 0.18F, alpha * 0.18F);
            if (c.glow()) {
                c.ribbon(pts, 0.05F, tone(line * 0.2F + clock * 0.05F), alpha * 0.7F, alpha * 0.7F);
                c.ribbon(pts, 0.015F, CORE, alpha * 0.6F, alpha * 0.6F);
            }
        }
        if (c.glow() && !minimal()) for (int v = -3; v <= 3; v++) {
            float x = half * v / 3.5F;
            Vec3 bottom = center.add(side.scale(x)).add(0, -1.6, 0), top = center.add(side.scale(x)).add(normal.scale(lean)).add(0, 1.6, 0);
            trace(c, bottom, top, 0.08F, 0.08F, 2.5F, clock * 9 + v, 0.025F, 0.1F * v + 0.5F, alpha * 0.35F, normal);
        }
    }

    // ------------------------------------------------------------------
    // Primitives
    // ------------------------------------------------------------------

    /** A wavefront: a ring across {@code axis} whose edge ripples with compression, in tone {@code k}, with a pale core. */
    static void front(Canvas c, Vec3 at, Vec3 axis, float radius, float width, float k, float alpha, float ripple, float phase) {
        if (alpha < 0.01F || radius < 0.03F) return;
        if (ripple == 0) {
            if (c.soft()) c.ring(at, axis, radius, width * 2.5F, 0, TWO_PI, tone(k), alpha * 0.22F);
            if (c.glow()) {
                c.ring(at, axis, radius, width, 0, TWO_PI, tone(k), alpha * 0.8F);
                c.ring(at, axis, radius, width * 0.3F, 0, TWO_PI, CORE, alpha * 0.6F);
            }
            return;
        }
        Vec3[] b = basis(axis);
        int n = Math.max(32, Math.min(96, (int) (radius * 24)));
        Vec3[] pts = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            float ang = i * TWO_PI / n, rr = radius * (1 + ripple * sin(7 * ang + phase));
            pts[i] = at.add(b[0].scale(cos(ang) * rr)).add(b[1].scale(sin(ang) * rr));
        }
        if (c.soft()) c.ribbon(pts, width * 2.5F, tone(k), alpha * 0.22F, alpha * 0.22F);
        if (c.glow()) {
            c.ribbon(pts, width, tone(k), alpha * 0.8F, alpha * 0.8F);
            c.ribbon(pts, width * 0.3F, CORE, alpha * 0.6F, alpha * 0.6F);
        }
    }

    /**
     * A waveform along a line: a sine trace from {@code from} to {@code to} whose swing goes from {@code amp0} to
     * {@code amp1}, swinging along {@code side} (or across the view when null). Each point fades as it nears the
     * camera, so a cry from your own mouth doesn't fill the screen.
     */
    static void trace(Canvas c, Vec3 from, Vec3 to, float amp0, float amp1, float cycles, float phase, float width, float k,
                      float alpha, Vec3 side) {
        if (alpha < 0.01F || !c.glow() && !c.soft()) return;
        Vec3 dir = to.subtract(from);
        double length = dir.length();
        if (length < 0.05) return;
        dir = dir.scale(1 / length);
        if (side == null) {
            Vec3 view = c.camera().subtract(from.lerp(to, 0.5));
            side = dir.cross(view);
            if (side.lengthSqr() < 1e-6) side = dir.cross(UP);
            if (side.lengthSqr() < 1e-6) side = new Vec3(1, 0, 0);
        }
        side = side.normalize();
        int n = Math.max(8, Math.min(96, (int) (cycles * 10)));
        Vec3[] pts = new Vec3[n + 1];
        float[] w = new float[n + 1], al = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n, amp = lerp(u, amp0, amp1);
            pts[i] = from.add(dir.scale(length * u)).add(side.scale(amp * sin(u * cycles * TWO_PI + phase)));
            w[i] = width;
            al[i] = alpha * near(c, pts[i]) * (i == 0 || i == n ? 0 : 1);
        }
        if (c.soft()) c.ribbon(pts, scaled(w, 2.4F), tone(k), scaled(al, 0.2F));
        if (c.glow()) {
            c.ribbon(pts, w, tone(k), scaled(al, 0.8F));
            c.ribbon(pts, scaled(w, 0.35F), CORE, scaled(al, 0.55F));
        }
    }

    /** Five lines of a stave bent round an arc of radius {@code radius} about {@code center}, from one angle to another. */
    static void staff(Canvas c, Vec3 center, float radius, float from, float to, float k, float alpha) {
        if (alpha < 0.01F) return;
        for (int line = 0; line < 5; line++) {
            Vec3 at = center.add(0, line * 0.1, 0);
            if (c.soft()) c.ring(at, UP, radius, 0.05F, from, to, tone(k + line * 0.08F), alpha * 0.15F);
            if (c.glow()) c.ring(at, UP, radius, 0.018F, from, to, tone(k + line * 0.08F), alpha * 0.75F);
        }
    }

    /** A note: a glowing head, a stem, and a flag when asked. */
    static void glyph(Canvas c, Vec3 head, float size, float[] col, float alpha, boolean flag) {
        if (alpha < 0.01F || !c.glow()) return;
        Vec3 view = c.camera().subtract(head);
        Vec3 right = UP.cross(view);
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        c.glow(head, size, col, alpha * 0.85F);
        c.glow(head, size * 0.45F, CORE, alpha * 0.7F);
        Vec3 stemBase = head.add(right.scale(size * 0.45F)), stemTop = stemBase.add(0, size * 3, 0);
        c.line(stemBase, stemTop, size * 0.18F, col, alpha * 0.85F, alpha * 0.85F);
        if (flag) c.line(stemTop, stemTop.add(right.scale(size * 1.1F)).add(0, -size * 0.9F, 0), size * 0.16F, col, alpha * 0.8F, alpha * 0.4F);
    }

    // ------------------------------------------------------------------

    /** Dims anything close to the camera: 0 within 1.2 blocks, full past 3.6. */
    private static float near(Canvas c, Vec3 point) {
        return (float) clamp((point.distanceTo(c.camera()) - 1.2) / 2.4, 0, 1);
    }

    /** The mouth, or for your own first-person view a point just below and ahead of the camera. */
    private static Vec3 mouth(Canvas c, Player owner) {
        Minecraft mc = Minecraft.getInstance();
        if (owner == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON)
            return c.camera().add(owner.getViewVector(c.partial).scale(0.6)).add(0, -0.25, 0);
        return SonicGeometry.mouth(owner, c.partial);
    }

    private static Vec3[] basis(Vec3 axis) {
        Vec3 n = axis.normalize();
        Vec3 u = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0).cross(n) : UP.cross(n);
        u = u.normalize();
        return new Vec3[] {u, n.cross(u).normalize()};
    }

    private static float[] scaled(float[] v, float k) {
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = v[i] * k;
        return out;
    }

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }
}
