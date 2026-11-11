package com.jayptucker.heroesevolved.story.bunker;

import com.jayptucker.heroesevolved.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Holds nothing: it is there so the client can draw an alarm beacon's turning beams. The beams' angle
 * comes from the world clock, so every viewer sees the same sweep without any syncing.
 */
public class AlarmBeaconBlockEntity extends BlockEntity {
    public AlarmBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ALARM_BEACON_ENTITY.get(), pos, state);
    }
}
