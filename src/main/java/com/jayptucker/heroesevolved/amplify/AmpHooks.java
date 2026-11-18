package com.jayptucker.heroesevolved.amplify;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Where other powers call into Power Amplification at the end of their own moves (see {@link AmpTuned}). */
public final class AmpHooks {
    private AmpHooks() {}

    /** Fold Space has moved {@code p}: an overcharged traveller brings every friend near with them (Brave New World). */
    public static void folded(ServerPlayer p, Vec3 from, Vec3 to) { AmpTuned.folded(p, from, to); }

    /** A gifted amplifier supercharges one of its own side (a Surge, or an Overcharge that backlashes). */
    public static void supercharge(net.minecraft.world.entity.LivingEntity ally, boolean overcharge) {
        if (overcharge) Amplification.overcharge(null, ally, false);
        else Amplification.surge(null, ally, Amplification.SURGE_TICKS);
    }

    /** A Cry has landed on {@code pos}: overcharged, it shatters glass, ice and stone there. */
    public static void shatter(ServerPlayer p, BlockPos pos) { AmpTuned.shatter(p, pos); }
}
