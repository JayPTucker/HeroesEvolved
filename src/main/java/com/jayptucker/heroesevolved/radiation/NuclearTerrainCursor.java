package com.jayptucker.heroesevolved.radiation;

import net.minecraft.core.BlockPos;

/** Streams outward horizontal slices before deepening the crater; no sphere-sized allocation. */
public final class NuclearTerrainCursor {
    private final int radius;
    private int slice, ring, edgeIndex;
    private boolean finished;
    public NuclearTerrainCursor(int radius) {
        if (radius < 1 || radius > 64) throw new IllegalArgumentException("Invalid nuclear radius");
        this.radius = radius;
    }
    public boolean next(BlockPos.MutableBlockPos offset) {
        if (finished) return false;
        while (slice <= radius * 2) {
            // Player feet, ground immediately below, then alternate upward/downward.
            int y = slice % 2 == 0 ? slice / 2 : -(slice + 1) / 2;
            int horizontalSquared = radius * radius - y * y;
            int extent = (int) Math.sqrt(horizontalSquared);
            if (ring > extent) { slice++; ring = 0; edgeIndex = 0; continue; }
            int x, z;
            if (ring == 0) { x = 0; z = 0; ring = 1; }
            else {
                int r = ring, sideLength = r * 2, side = edgeIndex / sideLength, step = edgeIndex % sideLength;
                switch (side) {
                    case 0 -> { x = -r + step; z = -r; }
                    case 1 -> { x = r; z = -r + step; }
                    case 2 -> { x = r - step; z = r; }
                    default -> { x = -r; z = r - step; }
                }
                if (++edgeIndex >= r * 8) { ring++; edgeIndex = 0; }
            }
            if (x * x + z * z > horizontalSquared) continue;
            offset.set(x, y, z);
            return true;
        }
        finished = true;
        return false;
    }
}
