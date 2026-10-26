package com.jayptucker.heroesevolved.speed;

import com.jayptucker.heroesevolved.ability.service.PowerRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.*;

/** Shared client prediction/server collision, using server-synchronized eligibility. */
public final class SpeedMovement {
    private static final VoxelShape[] SURFACES = new VoxelShape[10];
    static {
        for (int i = 1; i <= 9; i++)
            SURFACES[i] = Shapes.box(0, 0, 0, 1, Math.min(1, i / 9.0 + .001), 1);
    }
    private SpeedMovement() {}

    public static boolean accelerating(Player player) {
        var attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        return player.isAlive() && !player.isSpectator() && !player.isPassenger() && !player.isFallFlying()
                && attribute != null && attribute.hasModifier(SpeedService.ACCELERATING)
                && (!(player instanceof ServerPlayer server) || SpeedService.active(server)
                    && SpeedService.awakened(server) && PowerRules.canAct(server));
    }

    public static VoxelShape waterSurface(BlockGetter level, BlockPos pos, FluidState fluid, CollisionContext context) {
        if (!(context instanceof EntityCollisionContext entityContext)
                || !(entityContext.getEntity() instanceof Player player)
                || !player.isSprinting() || player.isShiftKeyDown()
                || player.fallDistance >= 3 || player.getDeltaMovement().y <= -.5
                || !accelerating(player) || !player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(SpeedService.WATER_RUN)
                || !fluid.is(FluidTags.WATER) || level.getFluidState(pos.above()).is(FluidTags.WATER)) return Shapes.empty();
        // Match source/flowing water height, with a tiny clearance to prevent swimming drag.
        int height = Math.clamp(Math.round(fluid.getHeight(level, pos) * 9), 1, 9);
        VoxelShape surface = SURFACES[height];
        // Only catch feet arriving from above. Never lift a submerged swimmer or catch a long fall.
        return context.isAbove(surface, pos, false) ? surface : Shapes.empty();
    }
}
