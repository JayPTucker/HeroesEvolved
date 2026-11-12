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
            Vec3 direction = shot.getDeltaMovement();
            boolean stopped = direction.lengthSqr() < .0001;
            Vec3 axis = stopped ? Vec3.directionFromRotation(shot.getXRot(), shot.getYRot()) : direction.normalize();
            float[] glow = stopped ? VIOLET : AMBER;
            canvas.line(at.subtract(axis.scale(.15)), at.add(axis.scale(.065)), .045F, COPPER, .74F, .91F);
            canvas.line(at.subtract(axis.scale(.29)), at.add(axis.scale(.08)), .022F, PALE, .17F, .90F);
            canvas.glow(at, .23F, glow, .43F);
        });
    }
}
