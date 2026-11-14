package com.jayptucker.heroesevolved.client.effects.intro;

import com.jayptucker.heroesevolved.client.effects.setpiece.Canvas;
import com.jayptucker.heroesevolved.client.effects.setpiece.Shades;
import com.jayptucker.heroesevolved.time.intro.SkyFight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The sky fight as the Last Day sees it, from the far end of the avenue while you run for Primatech: two small dark
 * figures over the light at Kirby Plaza, Peter trailing pale condensation and Sylar dark smoke, meeting in flashes
 * (Peter's lightning when he lands one, a red ring when Sylar does), until Sylar is thrown into the tower west of the
 * plaza (a flash and a plume of dust) and Peter dives into the light. Each boom reaches you a moment later, as sound would.
 * <p>
 * Drawn on each client from {@link SkyFight#LAST_DAY}, timed from the light appearing, so it needs no packets and no
 * entities: the server needn't send anything from that far away.
 */
public final class SkyFightArt {
    private static final float[] PETER = Canvas.rgb(0x2C2E36), SYLAR = Canvas.rgb(0x1A1214);
    private static final float[] VAPOR = Canvas.rgb(0xDCDDD5), CORE = Canvas.rgb(0xF6F4EC), BOLT = Canvas.rgb(0x84D5FF),
            SMOKE = Canvas.rgb(0x5A1A1C), RED = Canvas.rgb(0xC0303A), DUST = Canvas.rgb(0x9A968E);
    private static final SkyFight.Script S = SkyFight.LAST_DAY;
    /** The last boom heard (an index into the clashes, then the impact), and the light it belongs to. */
    private static int heard = -1;
    private static long heardLight = -2;

    private SkyFightArt() {}

    /** While the Last Day's light is up and this player is still above ground. */
    public static boolean live() {
        return IntroScene.prologue && IntroScene.light >= 0 && !IntroScene.underground && Minecraft.getInstance().level != null;
    }

    public static void drawLive(Canvas c) {
        if (!live()) return;
        float t = Minecraft.getInstance().level.getGameTime() - IntroScene.light + c.partial();
        if (t < S.start() || t > S.landAt() + 4) return;
        Vec3 impact = S.impactGuess();
        Vec3[] now = SkyFight.at(S, t, impact);
        int[] moves = SkyFight.moves(S, t);
        figure(c, now[0], SkyFight.at(S, t - 1, impact)[0], PETER, moves[0] == 2);
        if (now[1] != null) figure(c, now[1], prior(t, impact), SYLAR, moves[1] == 2);
        trail(c, t, impact, 0, VAPOR, true);
        if (now[1] != null) trail(c, t, impact, 1, SMOKE, false);
        for (int k = 0; k < S.passes(); k++) {
            float age = t - S.clashAt(k);
            if (age < 0 || age > 14) continue;
            clash(c, k, age, t, impact);
        }
        float since = t - S.impactAt();
        if (since >= 0 && since < 90) crash(c, impact, since);
        if (t > S.diveAt() && t < S.landAt() && c.glow()) c.glow(now[0].add(0, 0.9, 0), 1.6F, CORE, 0.5F);
        hear(c, t);
    }

    private static Vec3 prior(float t, Vec3 impact) {
        Vec3 p = SkyFight.at(S, t - 1, impact)[1];
        return p == null ? impact : p;
    }

    /** A small dark figure, leaning along its line of flight, or spinning when thrown; a faint halo so it reads on the sky. */
    private static void figure(Canvas c, Vec3 at, Vec3 before, float[] colour, boolean thrown) {
        Vec3 d = at.subtract(before);
        float yaw = (float) Math.atan2(d.x, d.z), pitch = d.lengthSqr() > 0.04 ? (float) (Math.atan2(d.horizontalDistance(), d.y)) : 0;
        if (thrown) pitch = (Minecraft.getInstance().level.getGameTime() + c.partial()) * 0.7F;
        Vec3 centre = at.add(0, 0.9, 0);
        if (c.solid()) {
            c.box(centre, 0.32F, 1.0F, 0.2F, yaw, pitch, 0, colour, 1);
            c.box(centre.add(lean(yaw, pitch, 1.25)), 0.27F, 0.27F, 0.27F, yaw, pitch, 0, colour, 1);
        }
        if (c.glow()) c.glow(centre, 1.3F, colour == PETER ? CORE : RED, 0.12F);
    }

    /** Where the head sits on a body turned by {@code yaw} and tipped by {@code pitch}. */
    private static Vec3 lean(float yaw, float pitch, double up) {
        double h = Math.sin(pitch) * up;
        return new Vec3(Math.sin(yaw) * h, Math.cos(pitch) * up, Math.cos(yaw) * h);
    }

    /** The way each has come in the last few ticks: pale condensation behind Peter, dark smoke behind Sylar. */
    private static void trail(Canvas c, float t, Vec3 impact, int who, float[] colour, boolean glow) {
        if (glow ? !c.glow() : !c.soft()) return;
        int n = 10;
        Vec3[] points = new Vec3[n];
        float[] width = new float[n], alpha = new float[n];
        for (int i = 0; i < n; i++) {
            Vec3 p = SkyFight.at(S, t - i * 0.8F, impact)[who];
            if (p == null) return;
            points[i] = p.add(0, 0.9, 0);
            float k = 1 - i / (float) n;
            width[i] = 0.25F + 0.5F * k;
            alpha[i] = (glow ? 0.55F : 0.5F) * k;
        }
        c.ribbon(points, width, colour, alpha);
    }

    /** Pass {@code k} meets: a flash and a ring opening toward you, and Peter's lightning when he lands it. */
    private static void clash(Canvas c, int k, float age, float t, Vec3 impact) {
        Vec3[] at = SkyFight.at(S, S.clashAt(k), impact);
        if (at[1] == null) return;
        Vec3 p = at[0].add(0, 0.9, 0), q = at[1].add(0, 0.9, 0), mid = p.add(q).scale(0.5);
        boolean peter = SkyFight.peterWins(k);
        float f = 1 - age / 14;
        if (c.glow()) {
            c.glow(mid, 2.5F + age * 0.5F, peter ? CORE : RED, 0.9F * f * f);
            Vec3 toward = c.camera().subtract(mid).normalize();
            c.ring(mid, toward, 1.5F + age * 0.9F, 0.35F, 0, Mth.TWO_PI, peter ? VAPOR : RED, 0.7F * f);
            if (peter && age < 6) c.bolt(p, q, k * 31 + (int) age, 0.5F, 0.18F, BOLT, 0.9F * f);
        }
    }

    /** Sylar into the tower: a flash, then dust rolling out of the hole and drifting down. */
    private static void crash(Canvas c, Vec3 impact, float since) {
        if (c.glow() && since < 12) c.glow(impact, 4 + since, CORE, 1 - since / 12);
        if (c.soft()) {
            float f = Mth.clamp(1 - since / 90, 0, 1), grow = Mth.clamp(since / 30, 0, 1);
            for (int i = 0; i < 4; i++) {
                Vec3 at = impact.add(1.5 + i * 0.9, -since * 0.03 - i * 0.6, (i - 1.5) * 0.8);
                float r = 1.2F + 2.4F * grow + i * 0.3F;
                c.cloud(at, r, r * 0.8F, r, i, since * 0.01F, 0.55F * f, Shades.smoke(c, DUST, 0));
            }
        }
    }

    /** The booms, heard late: sound takes about a tick to cross seventeen blocks. */
    private static void hear(Canvas c, float t) {
        if (!c.glow()) return;
        if (heardLight != IntroScene.light) { heardLight = IntroScene.light; heard = -1; }
        float delay = (float) (c.camera().distanceTo(S.centre()) / 17);
        int events = S.passes() + 1;
        while (heard + 1 < events) {
            int next = heard + 1;
            int at = next < S.passes() ? S.clashAt(next) : S.impactAt();
            if (t < at + delay) break;
            heard = next;
            if (t - (at + delay) > 20) continue;   // long past (a rejoin): don't play a backlog
            boolean impact = next == S.passes();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.GENERIC_EXPLODE.value(),
                    impact ? 0.55F : 0.75F, impact ? 0.55F : 0.3F));
        }
    }
}
