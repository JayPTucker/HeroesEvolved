package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.combat.BossLocks;
import com.jayptucker.heroesevolved.endgame.EndBattle;
import com.jayptucker.heroesevolved.endgame.SealGeometry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The native Dragon is a lock target while it is the Living Seal: out of reach in the air, so an aimed power that
 * brings fliers down (combat/BossLocks.used) can drag it out of the sky. Outside the fight it answers nothing.
 */
@Mixin(EnderDragon.class)
public abstract class EnderDragonLockMixin implements BossLocks.Lockable {
    @Override public BossLocks.Lock lock() {
        EnderDragon dragon = (EnderDragon) (Object) this;
        return dragon.level().isClientSide ? null : EndBattle.dragonLock(dragon);
    }
    @Override public Vec3 aimPoint() { return SealGeometry.chest((EnderDragon) (Object) this); }
    @Override public void answered(ServerPlayer player, BossLocks.Key key) { EndBattle.dragonAnswered((EnderDragon) (Object) this, player, key); }
}
