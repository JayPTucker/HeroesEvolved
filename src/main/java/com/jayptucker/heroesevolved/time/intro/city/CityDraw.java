package com.jayptucker.heroesevolved.time.intro.city;

/** Draws every part of the plan that touches a canvas: cleared columns, then the ground, then features in order. */
public final class CityDraw {
    private CityDraw() {}

    public static void draw(CityCanvas canvas) {
        for (int x = canvas.x0; x <= canvas.x1; x++)
            for (int z = canvas.z0; z <= canvas.z1; z++) {
                canvas.clearColumn(x, z, CityGrid.STREET_Y);
                Streets.ground(canvas, x, z);
            }
        for (Feature feature : CityPlan.features())
            if (feature.touches(canvas)) feature.draw().accept(canvas);
        canvas.finish();
    }
}
