package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.ClientProjectileTime;
import com.jayptucker.heroesevolved.time.ProjectileTimeService;
import net.minecraft.world.phys.Vec3;

/** Gives even non-tracer rounds a readable shape while they hang in slowed time. */
final class TemporalProjectileArt {
    private static final float[] COPPER = Canvas.rgb(0xDC9C56), AMBER = Canvas.rgb(0xFFCC75),
            VIOLET = Canvas.rgb(0xAA8CFF), PALE = Canvas.rgb(0xF6EBFF);

    private TemporalProjectileArt() {}

    static void draw(Canvas canvas) {
        if (!canvas.glow()) return;
        ClientProjectileTime.forEachShot(shot -> {
            if (!ProjectileTimeService.isTaczBullet(shot)) return;
            Vec3 at = shot.getPosition(canvas.partial).add(0, .03, 0);
            if (at.distanceToSqr(canvas.cx, canvas.cy, canvas.cz) > 48 * 48) return;
            boolean stopped = ClientProjectileTime.stopped(shot);
            Vec3 axis = ClientProjectileTime.direction(shot);
            float[] glow = stopped ? VIOLET : AMBER;
            // The suspended tracer tapers into its frozen air wake instead of collapsing to a dot.
            if (stopped) {
                Vec3 tail = at.subtract(axis.scale(2.5)), tip = at.subtract(axis.scale(.12));
                canvas.line(tail, tip, .035F, VIOLET, 0, .14F);
                canvas.line(tail, tip, .012F, PALE, 0, .38F);
                canvas.line(at.subtract(axis.scale(.8)), tip, .004F, PALE, 0, .66F);
            }
            canvas.line(at.subtract(axis.scale(.15)), at.add(axis.scale(.065)), .045F, COPPER, .74F, .91F);
            canvas.line(at.subtract(axis.scale(.29)), at.add(axis.scale(.08)), .022F, PALE, .17F, .90F);
            canvas.glow(at, .23F, glow, .43F);
        });
    }
}
