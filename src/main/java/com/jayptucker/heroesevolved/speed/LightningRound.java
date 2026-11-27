package com.jayptucker.heroesevolved.speed;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.expansion.Signatures;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightning Round (Superspeed, Momentum Fighter signature): too fast to follow, the speedster visits every foe within 16 blocks in
 * turn (up to six, each the nearest to the last), arriving behind each one and striking it for 4 + tier, one every
 * {@link #HOP} ticks, then is back where they started before the first one has fallen. Nothing touches them while it
 * lasts. {@code SpeedArt.round} draws each hop: the streak, the gold afterimage left behind, the strike.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class LightningRound {
    public static final String ID = "lightning_round";
    public static final int MAX = 6, HOP = 3;
    public static final double RANGE = 16;

    private LightningRound() {}

    private static List<LivingEntity> route(ServerPlayer p) {
        List<LivingEntity> left = new ArrayList<>(Signatures.foes(p, p.getEyePosition(), RANGE, true));
        List<LivingEntity> order = new ArrayList<>();
        Vec3 at = p.position();
        while (!left.isEmpty() && order.size() < MAX) {
            LivingEntity next = left.getFirst();
            for (LivingEntity e : left) if (e.position().distanceToSqr(at) < next.position().distanceToSqr(at)) next = e;
            left.remove(next);
            order.add(next);
            at = next.position();
        }
        return order;
    }

    public static boolean canUse(ServerPlayer p) {
        if (!route(p).isEmpty()) return true;
        p.displayClientMessage(Component.literal("No one in reach to hit.").withStyle(ChatFormatting.GRAY), true);
        return false;
    }

    public static boolean activate(ServerPlayer p, int tier) {
        List<LivingEntity> order = route(p);
        if (order.isEmpty()) return false;
        Run run = new Run(p, tier, order);
        if (!Signatures.start(ID, run)) return false;
        ArmPosePayload.show(p, ArmPose.SPEED_LUNGE, order.size() * HOP + 4);
        run.level.playSound(null, p.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS, 1, 1.6F);
        return true;
    }

    static final class Run extends Signatures.Run {
        final List<LivingEntity> order;
        final Vec3 start;
        final float startYaw, startPitch;
        Vec3 at;
        int struck;

        Run(ServerPlayer player, int tier, List<LivingEntity> order) {
            super(player, tier);
            this.order = order;
            this.start = player.position();
            this.at = start;
            this.startYaw = player.getYRot();
            this.startPitch = player.getXRot();
        }

        @Override protected boolean tick() {
            if ((age - 1) % HOP != 0) return true;
            int i = (age - 1) / HOP;
            if (i < order.size()) {
                LivingEntity foe = order.get(i);
                if (foe.isAlive() && foe.level() == level) strike(foe, i);
                return true;
            }
            hop(start, startYaw, startPitch, -1);
            player.resetFallDistance();
            CameraShakePayload.sendAround(level, start, 4, .5F, 6);
            return false;
        }

        private void strike(LivingEntity foe, int i) {
            Vec3 from = at;
            Vec3 approach = foe.position().subtract(from).multiply(1, 0, 1);
            Vec3 dir = approach.lengthSqr() < 1e-4 ? player.getLookAngle().multiply(1, 0, 1).normalize() : approach.normalize();
            Vec3 behind = foe.position().add(dir.scale(foe.getBbWidth() * .5 + .7));
            Vec3 dest = clearAt(behind) ? behind : foe.position().subtract(dir.scale(foe.getBbWidth() * .5 + .7));
            if (!clearAt(dest)) dest = from;
            Vec3 face = foe.getBoundingBox().getCenter().subtract(dest.add(0, player.getEyeHeight(), 0));
            float yaw = (float) (Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90, pitch = (float) -(Mth.atan2(face.y, face.horizontalDistance()) * Mth.RAD_TO_DEG);
            hop(dest, yaw, pitch, i);
            Signatures.hit(foe, player.damageSources().playerAttack(player), 4 + tier);
            Signatures.fling(foe, dir.scale(-.25).add(0, .3, 0));
            SetPiecePayload.sendSeeded(level, Piece.SPEED_ROUND_HIT, foe.getBoundingBox().getCenter(), dest.add(0, 1.1, 0), foe.getBbHeight(), null, 0, i);
            level.playSound(null, foe.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1, 1.3F + i * .05F);
            struck++;
        }

        private void hop(Vec3 dest, float yaw, float pitch, int i) {
            SetPiecePayload.sendSeeded(level, Piece.SPEED_ROUND, at, dest, 1, null, 0, i);
            player.teleportTo(level, dest.x, dest.y, dest.z, yaw, pitch);
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            at = dest;
        }

        private boolean clearAt(Vec3 pos) {
            return level.hasChunkAt(net.minecraft.core.BlockPos.containing(pos))
                    && level.noCollision(player, player.getBoundingBox().move(pos.subtract(player.position())));
        }
    }

    /** Nothing touches a speedster mid-round. */
    @SubscribeEvent public static void untouchable(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && Signatures.running(ID, p) && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))
            event.setCanceled(true);
    }

    /** For tests: how many foes this caster's Lightning Round has struck (-1 when none is running). */
    public static int struck(ServerPlayer p) {
        Run run = Signatures.get(ID, p);
        return run == null ? -1 : run.struck;
    }
}
