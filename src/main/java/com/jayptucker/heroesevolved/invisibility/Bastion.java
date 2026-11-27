package com.jayptucker.heroesevolved.invisibility;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.expansion.Signatures;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Bastion (Invisibility, Aegis signature): Sarah throws up a dome of force six blocks round where she stands, for six
 * seconds. Shots from outside glance off it and fly back, foes inside are shoved out through its wall, and friends under
 * it take half damage. When it ends, or on a second press, it bursts outward: everyone hostile within nine blocks takes
 * 3 + tier and is thrown clear. {@code ForceArt.bastion} draws the dome (re-sent each second); each glance is a
 * {@code FORCE_RIPPLE} on its wall.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Bastion {
    public static final String ID = "bastion";
    public static final int HOLD = 120, RESEND = 20;
    public static final double RADIUS = 6, BURST = 9;
    /** How much of a blow a friend under the dome still takes. */
    public static final float SHELTERED = .5F;

    private Bastion() {}

    public static boolean canUse(ServerPlayer p) { return true; }

    public static boolean activate(ServerPlayer p, int tier) {
        Run run = new Run(p, tier, p.position());
        if (!Signatures.start(ID, run)) return false;
        run.show();
        ArmPosePayload.show(p, ArmPose.FORCE_BUBBLE, 16);
        run.level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.4F, 1.3F);
        run.level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, .8F);
        return true;
    }

    static final class Run extends Signatures.Run {
        final Vec3 centre;
        final Set<UUID> glanced = new HashSet<>();
        int turned;

        Run(ServerPlayer player, int tier, Vec3 centre) { super(player, tier); this.centre = centre; }

        void show() {
            SetPiecePayload.sendSeeded(level, Piece.FORCE_BASTION, centre, centre.add(0, RADIUS, 0), (float) RADIUS, null, 0, age);
        }

        boolean inside(Entity e) { return e.position().distanceToSqr(centre) <= RADIUS * RADIUS && e.getY() >= centre.y - 1.5; }

        @Override protected boolean tick() {
            if (age % RESEND == 0 && age < HOLD) show();
            deflect();
            if (age % 4 == 0) shove();
            if (age >= HOLD) { burst(); return false; }
            return true;
        }

        @Override protected boolean press() {
            burst();
            Signatures.end(ID, player);
            return true;
        }

        /** Shots from outside glance off the wall and fly back the way they came. */
        private void deflect() {
            for (Projectile shot : level.getEntitiesOfClass(Projectile.class, new AABB(centre, centre).inflate(RADIUS + 3))) {
                if (glanced.contains(shot.getUUID())) continue;
                Entity owner = shot.getOwner();
                if (owner instanceof LivingEntity o && !ExpansionSkills.hostile(player, o)) continue;
                Vec3 pos = shot.position(), v = shot.getDeltaMovement(), next = pos.add(v);
                double now = pos.distanceTo(centre), then = next.distanceTo(centre);
                if (now < RADIUS - .2 || then > RADIUS + .6 || then >= now) continue;
                Vec3 normal = pos.subtract(centre).normalize();
                Vec3 bounced = v.subtract(normal.scale(2 * v.dot(normal))).scale(.8);
                shot.setDeltaMovement(bounced);
                shot.setOwner(player);
                shot.hurtMarked = true;
                glanced.add(shot.getUUID());
                turned++;
                Vec3 at = centre.add(normal.scale(RADIUS));
                SetPiecePayload.send(level, Piece.FORCE_RIPPLE, at, at.add(normal), 1.6F, null);
                level.playSound(null, BlockPos.containing(at), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1, 1.5F);
            }
        }

        /** Foes inside are pushed out through the wall. */
        private void shove() {
            for (LivingEntity foe : Signatures.foes(player, centre, RADIUS + 1, false)) {
                if (!inside(foe)) continue;
                Vec3 out = foe.position().subtract(centre).multiply(1, 0, 1);
                out = out.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : out.normalize();
                Signatures.fling(foe, out.scale(.75).add(0, .2, 0));
                if (age % 8 == 0) {
                    Vec3 at = centre.add(out.scale(RADIUS)).add(0, Math.min(RADIUS - 1, foe.getY() - centre.y + 1), 0);
                    SetPiecePayload.send(level, Piece.FORCE_RIPPLE, at, at.add(out), 1.2F, null);
                }
            }
        }

        void burst() {
            float damage = 3 + tier;
            for (LivingEntity foe : Signatures.foes(player, centre.add(0, 1, 0), BURST, false)) {
                Signatures.hit(foe, player.damageSources().indirectMagic(player, player), damage);
                Vec3 out = foe.position().subtract(centre).multiply(1, 0, 1);
                Signatures.fling(foe, (out.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : out.normalize()).scale(1.4).add(0, .4, 0));
            }
            SetPiecePayload.send(level, Piece.FORCE_BASTION_BURST, centre, centre.add(0, RADIUS, 0), (float) BURST, null);
            level.playSound(null, BlockPos.containing(centre), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2, .7F);
            level.playSound(null, BlockPos.containing(centre), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.4F, 1.4F);
            CameraShakePayload.sendAround(level, centre, BURST * 2, .8F, 8);
            ArmPosePayload.show(player, ArmPose.FORCE_NOVA, 12);
        }
    }

    /** Whether any Bastion covers this body for a friend of its caster. */
    public static boolean shelters(LivingEntity body) {
        for (Run run : Signatures.<Run>all(ID))
            if (run.level == body.level() && run.inside(body) && (body == run.player || ExpansionSkills.friendly(run.player, body))) return true;
        return false;
    }

    /** Friends under the dome take half. */
    @SubscribeEvent public static void sheltered(LivingIncomingDamageEvent event) {
        if (event.getAmount() > 0 && shelters(event.getEntity())) event.setAmount(event.getAmount() * SHELTERED);
    }

    /** For tests: how many shots this caster's dome has turned (-1 when none is up). */
    public static int turned(ServerPlayer p) {
        Run run = Signatures.get(ID, p);
        return run == null ? -1 : run.turned;
    }
}
