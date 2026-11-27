package com.jayptucker.heroesevolved.assembly;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.expansion.Signatures;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Ballista (Matter Assembly, Ballistics signature): the engineer throws {@link #COST} Matter at a spot up to 12 blocks
 * away and folds a crystal ballista together there: a blueprint first, then its parts flying from her hand one by one
 * and snapping in ({@link #lands}, the same schedule the drawing keeps), so it only starts shooting once the drawing has
 * finished it. For six seconds it turns on the nearest foe within 18 blocks and looses a bolt every {@link #SHOT}
 * ticks, 2 + tier/2 each, landing when the drawn bolt reaches it. When it is done, or on a second press, it comes apart and
 * its Matter flies home.
 */
public final class Ballista {
    public static final String ID = "ballista";
    public static final int COST = 6, PARTS = 9, BUILD = 20, ACTIVE = 120, SHOT = 20;
    public static final double RANGE = 18, BOLT_SPEED = 3, MUZZLE = 1.35;

    private Ballista() {}

    /** The tick each part lands, from the cast: the legs first, the bolt last. */
    public static float lands(int part) { return 5 + part * (BUILD - 7) / (float) (PARTS - 1); }

    /** How many ticks a bolt takes to cross {@code distance}. */
    public static int flight(double distance) { return Math.max(1, (int) Math.ceil(distance / BOLT_SPEED)); }

    static Vec3 spot(ServerPlayer p) {
        BlockPos aim = ExpansionSkills.aim(p);
        Vec3 at = new Vec3(aim.getX() + .5, aim.getY() + 1, aim.getZ() + .5);
        if (at.distanceToSqr(p.position()) > 12 * 12 || !p.serverLevel().hasChunkAt(aim)
                || !p.level().getBlockState(aim).isFaceSturdy(p.level(), aim, Direction.UP)
                || !p.level().getBlockState(aim.above()).getCollisionShape(p.level(), aim.above()).isEmpty()) {
            Vec3 ahead = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(2.5));
            BlockPos below = BlockPos.containing(ahead).below();
            for (int dy = 0; dy < 4; dy++, below = below.below())
                if (p.level().getBlockState(below).isFaceSturdy(p.level(), below, Direction.UP)) return new Vec3(ahead.x, below.getY() + 1, ahead.z);
            return null;
        }
        return at;
    }

    public static boolean canUse(ServerPlayer p) {
        if (Matter.of(p) < COST) {
            p.displayClientMessage(Component.literal("Ballista needs " + COST + " Matter.").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (spot(p) != null) return true;
        p.displayClientMessage(Component.literal("Nowhere to stand a ballista.").withStyle(ChatFormatting.GRAY), true);
        return false;
    }

    public static boolean activate(ServerPlayer p, int tier) {
        Vec3 base = spot(p);
        if (base == null || Matter.of(p) < COST) return false;
        Run run = new Run(p, tier, base);
        if (!Signatures.start(ID, run)) return false;
        for (int i = 0; i < COST; i++) Matter.take(p);
        Matter.sync(p, true);
        Vec3 face = base.subtract(p.position()).multiply(1, 0, 1);
        run.facing = face.lengthSqr() < 1e-4 ? p.getLookAngle().multiply(1, 0, 1).normalize() : face.normalize();
        SetPiecePayload.send(run.level, Piece.MATTER_BALLISTA, base, base.add(run.facing.scale(5)), 1, p);
        ArmPosePayload.show(p, ArmPose.MATTER_FORM, BUILD);
        run.level.playSound(null, BlockPos.containing(base), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, .8F);
        return true;
    }

    private record Bolt(LivingEntity target, int lands) {}

    static final class Run extends Signatures.Run {
        final Vec3 base;
        Vec3 facing = new Vec3(0, 0, 1);
        final List<Bolt> bolts = new ArrayList<>();
        int shots, hits;
        boolean folded;

        Run(ServerPlayer player, int tier, Vec3 base) { super(player, tier); this.base = base; }

        Vec3 muzzle() { return base.add(0, MUZZLE, 0); }

        @Override protected boolean tick() {
            if (age < BUILD) {
                if (age > 4 && age % 2 == 0) level.playSound(null, BlockPos.containing(base), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, .6F, 1 + age * .03F);
                return true;
            }
            int live = age - BUILD;
            if (live % SHOT == 0 && live < ACTIVE) shoot();
            for (Bolt b : List.copyOf(bolts)) {
                if (age < b.lands()) continue;
                bolts.remove(b);
                if (b.target().isAlive()) {
                    Signatures.hit(b.target(), player.damageSources().indirectMagic(player, player), 2 + tier / 2F);
                    Vec3 push = b.target().position().subtract(base).multiply(1, 0, 1);
                    if (push.lengthSqr() > 1e-4) Signatures.fling(b.target(), b.target().getDeltaMovement().add(push.normalize().scale(.35)).add(0, .15, 0));
                    SetPiecePayload.send(level, Piece.MATTER_BOUNCE, b.target().getBoundingBox().getCenter(), muzzle(), 2, null);
                    hits++;
                }
            }
            if (live >= ACTIVE && bolts.isEmpty()) { fold(); return false; }
            return true;
        }

        private void shoot() {
            LivingEntity target = null;
            for (LivingEntity t : Signatures.foes(player, muzzle(), RANGE, true)) { target = t; break; }
            Vec3 aim = target != null ? target.getBoundingBox().getCenter() : muzzle().add(facing.scale(5));
            if (target != null) {
                Vec3 flat = aim.subtract(base).multiply(1, 0, 1);
                if (flat.lengthSqr() > 1e-4) facing = flat.normalize();
                bolts.add(new Bolt(target, age + flight(muzzle().distanceTo(aim))));
                shots++;
                level.playSound(null, BlockPos.containing(base), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1, 1.4F);
            }
            SetPiecePayload.sendSeeded(level, Piece.MATTER_TURRET, base, aim, 1, null, 0, target != null ? 1 : 0);
        }

        @Override protected boolean press() {
            if (age < BUILD || folded) return false;
            fold();
            Signatures.end(ID, player);
            return true;
        }

        @Override protected void cancel() { if (!folded) { folded = true; Matter.give(player, COST); } }

        void fold() {
            if (folded) return;
            folded = true;
            Matter.returned(player, base.add(0, .8, 0), COST);
        }
    }

    /** For tests: the bolts this caster's ballista has landed (-1 when none stands). */
    public static int hits(ServerPlayer p) {
        Run run = Signatures.get(ID, p);
        return run == null ? -1 : run.hits;
    }
}
