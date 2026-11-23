package com.jayptucker.heroesevolved.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.dimension.end.DragonRespawnAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.UUID;
@Mixin(EndDragonFight.class)
public interface EndFightAccess {
    @Accessor("dragonUUID") void heroes$dragonUUID(UUID id);
    @Accessor("dragonKilled") void heroes$dragonKilled(boolean killed);
    @Accessor("needsStateScanning") void heroes$needsScan(boolean scan);
    @Accessor("portalLocation") BlockPos heroes$portal();
    @Accessor("respawnStage") DragonRespawnAnimation heroes$respawn();
}
