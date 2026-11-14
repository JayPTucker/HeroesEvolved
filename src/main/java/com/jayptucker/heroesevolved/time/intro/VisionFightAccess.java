package com.jayptucker.heroesevolved.time.intro;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** For game tests: where the Time vision's throw lands Sylar in the west tower (VisionFight is package-private). */
public final class VisionFightAccess {
    private VisionFightAccess() {}

    public static Vec3 impact(ServerLevel city) { return VisionFight.findImpact(city); }
}
