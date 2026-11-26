package com.jayptucker.heroesevolved.endgame;

import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import java.util.*;

/**
 * A Starfall shard: a crystal of the Seal thrown from the Dragon's rings to a marked ring, flying straight so it lands as
 * the mark fills. Anything that turns it (a punch, Wind Arms, Bullet Catch, a quake's push, a force field) makes it the
 * turner's: a returned shard hits the Dragon for a share of its health past the Seal and shatters any knot it hits
 * (the projectiles lock, combat/BossLocks). No terrain explosion and no fire.
 */
public final class EndBolt extends LargeFireball implements net.minecraft.world.entity.projectile.ItemSupplier {
    private float damage = 9;
    public EndBolt(EntityType<? extends EndBolt> type, Level level) { super(type, level); }
    public void shard(float value) { damage = value; }
    /** Turned by a player: it is theirs now, and flies at the Dragon. */
    public boolean returned() { return getOwner() instanceof ServerPlayer; }
    @Override public ItemStack getItem() { return new ItemStack(Items.AMETHYST_SHARD); }
    @Override protected boolean shouldBurn() { return false; }
    /** A shard flies straight: no drag, so it lands exactly where and when its mark says. */
    @Override protected float getInertia() { return accelerationPower == 0 ? 1 : super.getInertia(); }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (tickCount > 180) discard();
            else if (tickCount % 3 == 0) SetPiecePayload.sendSeeded((ServerLevel) level(), Piece.END_BATTLE_BOLT, position(), position().subtract(getDeltaMovement().scale(2)), 1, this, 0, returned() ? 7 : 0);
        }
    }
    /** A quake's push sends it back at the Dragon. */
    public void disrupt(ServerPlayer p) {
        setOwner(p);
        var battle = EndBattle.current();
        Vec3 aim = battle != null && battle.dragon != null && battle.dragon.isAlive() ? SealGeometry.chest(battle.dragon) : position().add(p.getLookAngle().scale(8));
        Vec3 to = aim.subtract(position());
        setDeltaMovement(to.lengthSqr() < 1e-4 ? p.getLookAngle() : to.normalize().scale(1.4));
        accelerationPower = 0;
        hurtMarked = true;
    }
    @Override protected void onHit(HitResult hit) { if (!level().isClientSide) impact(hit.getLocation(), hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null); }
    private void impact(Vec3 at, Entity direct) {
        if (!(level() instanceof ServerLevel level)) return;
        var battle = EndBattle.current();
        if (battle == null || battle.level != level) { discard(); return; }
        Set<Entity> targets = new HashSet<>(level.getEntities(this, getBoundingBox().inflate(SealGeometry.SHARD_RADIUS),
                e -> e instanceof LivingEntity && e.getBoundingBox().distanceToSqr(at) <= SealGeometry.SHARD_RADIUS * SealGeometry.SHARD_RADIUS));
        if (direct instanceof EnderDragonPart part) targets.add(part.parentMob);
        if (direct != null) targets.add(direct);
        for (Entity target : targets) {
            if (target == getOwner()) continue;
            if (returned()) {
                ServerPlayer owner = (ServerPlayer) getOwner();
                // A thrown crystal, not fire: the Dragon is immune to fire.
                if (target == battle.dragon || target instanceof SealKnot) target.hurt(damageSources().thrown(this, owner), 14);
            } else if (target instanceof ServerPlayer p && battle.seal != null && battle.stage == EndBattle.DRAGON) {
                battle.seal.hit(p, SealGeometry.Move.STARFALL, damage);
            }
        }
        SetPiecePayload.sendSeeded(level, Piece.END_BATTLE_IMPACT, at, at, SealGeometry.SHARD_RADIUS, null, 0, returned() ? 7 : 0);
        level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.AMETHYST_CLUSTER_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 2, .7F);
        discard();
    }
}
