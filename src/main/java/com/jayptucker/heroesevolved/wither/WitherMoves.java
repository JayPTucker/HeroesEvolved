package com.jayptucker.heroesevolved.wither;

import com.jayptucker.heroesevolved.entity.QuakeDebrisEntity;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.wither.WitherGeometry.Move;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.*;

/**
 * Every move's fixed telegraph and what lands, the hazards that outlive a windup (rolling floor, held beams, bone
 * spires), the fall out of the arrival tear, the phase roar and the death detonation. Positions are fixed when a windup
 * starts and read back from the entity's own fields, never re-aimed.
 */
public final class WitherMoves {
    private WitherMoves() {}

    /** A ring of thrown-up floor rolling out from {@code center}: hits each fighter once, only if their feet are low. */
    static final class Ripple {
        final Vec3 center;
        final float speed, reach, damage;
        int age, delay;
        final Set<UUID> hit = new HashSet<>();
        Ripple(Vec3 center, float speed, float reach, float damage, int delay) {
            this.center = center; this.speed = speed; this.reach = reach; this.damage = damage; this.delay = delay;
        }
        double radius() { return WitherGeometry.ripple(age, speed); }
    }
    /** Persistent tag on every skeleton it brings: no loot, no experience, and it crumbles in time. */
    public static final String BROOD = "heroesevolved:wither_brood";
    /** A skeleton in flight from its mouth to a mark. */
    record Spew(net.minecraft.world.entity.monster.WitherSkeleton mob, Vec3 from, Vec3 to, int[] age) {}
    /** One spire on a Bone Spires crack, bursting on clock tick {@code at}. */
    static final class Spire {
        final Vec3 base;
        final int at;
        Spire(Vec3 base, int at) { this.base = base; this.at = at; }
    }

    // ---------------------------------------------------------------- telegraphs

    /** Fixes every position the move will use and draws its warning. {@code b.marks} holds each fighter's spot. */
    public static void warn(RiftWitherEntity b, Move move) {
        ServerLevel level = (ServerLevel) b.level();
        Vec3 floor = WitherGeometry.floor();
        b.castFrom = b.core();
        List<Vec3> aimed = List.copyOf(b.marks);
        Vec3 focus = aimed.isEmpty() ? b.position().add(WitherGeometry.forward(b.lockedYaw).scale(10)).add(0, .8, 0) : focusMark(b, aimed);
        b.marks.clear();
        b.origins.clear();
        switch (move) {
            case BITE -> {
                Vec3[] lane = WitherGeometry.biteLane(b.position(), b.lockedYaw);
                b.castFrom = lane[0];
                b.origins.add(lane[0]);
                b.marks.add(lane[1]);
            }
            case RAKE -> {
                b.castFrom = new Vec3(b.getX(), floor.y, b.getZ());
                b.marks.add(b.castFrom.add(WitherGeometry.forward(b.lockedYaw).scale(WitherGeometry.RAKE_REACH)));
            }
            case VERDICT -> {
                // The center head takes the focus; the side heads take other fighters, or flank the focus.
                List<Vec3> others = new ArrayList<>(aimed);
                others.remove(focus);
                Vec3 side = focus.subtract(b.position()).multiply(1, 0, 1);
                side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize().cross(new Vec3(0, 1, 0));
                for (int head = 0; head < 3; head++) {
                    if (head > 0 && (b.burnt() & (1 << (head - 1))) != 0) continue; // its anchor is spent: that head is silent
                    Vec3 aim = head == 0 ? focus : others.size() >= head ? others.get(head - 1)
                            : focus.add(side.scale((head == 1 ? -1 : 1) * WitherGeometry.VERDICT_SPREAD));
                    Vec3 mouth = WitherGeometry.mouth(b.position(), b.lockedYaw, head, aim);
                    Vec3 dir = aim.subtract(mouth).normalize();
                    b.origins.add(mouth);
                    b.marks.add(clip(level, mouth, mouth.add(dir.scale(WitherGeometry.VERDICT_LENGTH))));
                }
            }
            case RAIN -> {
                int n = 0;
                for (Vec3 mark : aimed) for (int i = 0; i < 3; i++) {
                    double angle = i * Math.PI * 2 / 3 + n * .7, radius = i == 0 ? 0 : WitherGeometry.RAIN_SPREAD;
                    b.marks.add(mark.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
                    n++;
                }
            }
            case PULSE -> b.castFrom = new Vec3(b.getX(), floor.y + .05, b.getZ());
            case TETHER -> {
                Vec3 mouth = WitherGeometry.mouth(b.position(), b.lockedYaw, 0, focus);
                b.castFrom = mouth;
                b.marks.add(clip(level, mouth, mouth.add(focus.subtract(mouth).normalize().scale(WitherGeometry.TETHER_LENGTH))));
            }
            case LUNGE -> {
                Vec3 from = b.position(), lift = new Vec3(0, WitherGeometry.LUNGE_LIFT, 0);
                boolean airborne = focus.y > floor.y + 3;
                Vec3 to = focus.add(0, .2, 0).subtract(from.add(lift));
                if (!airborne) to = new Vec3(to.x, 0, to.z);
                if (to.lengthSqr() < 1e-3) to = WitherGeometry.forward(b.lockedYaw);
                double length = Math.min(WitherGeometry.LUNGE_REACH, to.length() + WitherGeometry.LUNGE_OVERSHOOT);
                Vec3 dir = to.normalize(), end = from;
                b.lungeCrash = false;
                // Sweep the real body along the lane: the first stone in the way is where it will crash.
                for (double d = .5; d <= length + 1e-6; d += .5) {
                    Vec3 next = from.add(dir.scale(Math.min(d, length)));
                    if (next.y < floor.y + .3) next = new Vec3(next.x, floor.y + .3, next.z);
                    if (!level.noCollision(b, b.getBoundingBox().move(next.subtract(from)))) { b.lungeCrash = true; break; }
                    end = next;
                }
                b.castFrom = from.add(lift);
                b.marks.add(end.add(lift));
            }
            case SLAM -> {
                b.marks.add(new Vec3(focus.x, floor.y, focus.z));
                b.motionFrom = b.position();
            }
            case SPIRES -> {
                Vec3 from = new Vec3(b.getX(), floor.y, b.getZ());
                b.castFrom = from;
                for (Vec3 mark : aimed) {
                    Vec3 flat = new Vec3(mark.x, floor.y, mark.z);
                    List<Vec3> line = WitherGeometry.spires(from, flat);
                    for (int i = 0; i < line.size(); i++) b.spires.add(new Spire(line.get(i), b.clock + b.windup + i * WitherGeometry.SPIRE_RATE));
                    b.marks.add(flat);
                    SetPiecePayload.sendSeeded(level, Piece.WITHER_SPIRES, from, flat, 1, b, 0, b.phase() | (b.windup << 4));
                }
            }
            case HORIZON -> b.castFrom = b.core();
            case BROOD -> {
                // A mark beside each fighter, on its side of them, and the rest in front of the cage.
                Vec3 mouth = WitherGeometry.mouth(b.position(), b.lockedYaw, 0, focus);
                b.castFrom = mouth;
                int count = Math.min(WitherGeometry.broodCount(b.phase(), Math.max(1, aimed.size())),
                        WitherGeometry.broodCap(Math.max(1, aimed.size())) - b.minions.size());
                int n = Math.max(1, aimed.size());
                for (int i = 0; i < count; i++) {
                    // Each fighter in turn gets one beside them, the next one round them on the other side.
                    Vec3 at = aimed.isEmpty() ? focus : aimed.get(i % n), toward = b.position().subtract(at).multiply(1, 0, 1);
                    toward = toward.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : toward.normalize();
                    int j = i / n;
                    double turn = (j % 2 == 0 ? 1 : -1) * (.55 + .6 * (j / 2));
                    Vec3 dir = new Vec3(toward.x * Math.cos(turn) - toward.z * Math.sin(turn), 0, toward.x * Math.sin(turn) + toward.z * Math.cos(turn));
                    Vec3 mark = at.add(dir.scale(WitherGeometry.BROOD_BESIDE));
                    Vec3 flat = new Vec3(mark.x, floor.y, mark.z);
                    if (flat.subtract(floor).horizontalDistance() > WitherGeometry.RADIUS - 2)
                        flat = floor.add(flat.subtract(floor).multiply(1, 0, 1).normalize().scale(WitherGeometry.RADIUS - 2));
                    b.marks.add(flat);
                }
            }
        }
        int seed = move.ordinal() | (b.variant << 5) | (b.lungeCrash && move == Move.LUNGE ? 1 << 6 : 0) | (Math.min(255, b.windup) << 8);
        switch (move) {
            case PULSE, HORIZON, RAKE -> SetPiecePayload.sendSeeded(level, Piece.WITHER_WARNING, b.castFrom,
                    b.marks.isEmpty() ? b.castFrom : b.marks.getFirst(), 1, b, 0, seed);
            case SPIRES -> {}
            default -> {
                for (int i = 0; i < b.marks.size(); i++) {
                    Vec3 from = move == Move.VERDICT || move == Move.BITE ? b.origins.get(i) : b.castFrom;
                    SetPiecePayload.sendSeeded(level, Piece.WITHER_WARNING, from, b.marks.get(i), 1, b, 0, seed);
                }
            }
        }
        SoundEvent cue = switch (move) {
            case BITE -> SoundEvents.SKELETON_AMBIENT;
            case RAKE, SPIRES -> SoundEvents.BONE_BLOCK_BREAK;
            case VERDICT, RAIN -> SoundEvents.AMETHYST_BLOCK_RESONATE;
            case HORIZON, PULSE -> SoundEvents.BEACON_POWER_SELECT;
            case LUNGE, SLAM -> SoundEvents.WITHER_AMBIENT;
            case TETHER -> SoundEvents.CHAIN_PLACE;
            case BROOD -> SoundEvents.WITHER_SKELETON_AMBIENT;
        };
        level.playSound(null, b.getX(), b.getY() + 4, b.getZ(), cue, SoundSource.HOSTILE, 2.2F, .55F + b.phase() * .07F);
    }
    private static Vec3 focusMark(RiftWitherEntity b, List<Vec3> aimed) {
        ServerPlayer focus = b.focus();
        if (focus == null) return aimed.getFirst();
        Vec3 at = focus.position().add(0, .8, 0);
        return aimed.stream().min(Comparator.comparingDouble(m -> m.distanceToSqr(at))).orElse(aimed.getFirst());
    }
    /** Where a ray from {@code from} toward {@code to} first meets stone (or {@code to}). */
    static Vec3 clip(ServerLevel level, Vec3 from, Vec3 to) {
        var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
    }
    public static boolean seen(ServerLevel level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    // ---------------------------------------------------------------- windups

    public static void windup(RiftWitherEntity b, Move move) {
        if (move == Move.SLAM && !b.marks.isEmpty()) {
            // It climbs and drifts over the fixed mark while the fists rise.
            double progress = 1 - b.windup / (double) move.windup;
            Vec3 wanted = b.motionFrom.lerp(b.marks.getFirst().add(0, WitherGeometry.SLAM_RISE + .3, 0), Math.min(1, progress * 1.25));
            Vec3 delta = wanted.subtract(b.position());
            if (delta.length() > 1.2) delta = delta.normalize().scale(1.2);
            if (b.level().noCollision(b, b.getBoundingBox().move(delta))) b.setPos(b.position().add(delta));
        } else if (move == Move.HORIZON) {
            // The star drinks: everyone slides toward it, and its light hums higher.
            for (ServerPlayer p : b.fighters()) {
                Vec3 pull = b.core().subtract(p.position()).multiply(1, 0, 1);
                if (pull.lengthSqr() < 4) continue;
                p.setDeltaMovement(p.getDeltaMovement().add(pull.normalize().scale(WitherGeometry.HORIZON_PULL)));
                p.hurtMarked = true;
            }
            if (b.windup % 8 == 0) b.level().playSound(null, b.getX(), b.getY() + 6, b.getZ(), SoundEvents.BEACON_AMBIENT,
                    SoundSource.HOSTILE, 2.5F, .5F + (1 - b.windup / (float) move.windup));
        }
    }

    // ---------------------------------------------------------------- landing

    public static void sideShot(RiftWitherEntity b, Vec3 from, Vec3 to) {
        ServerLevel level = (ServerLevel) b.level();
        var skull = ModEntities.RIFT_SKULL.get().create(level);
        if (skull == null) return;
        skull.setOwner(b);
        skull.setPos(from);
        skull.setDeltaMovement(to.subtract(from).normalize().scale(.72));
        skull.accelerationPower = .01;
        level.addFreshEntity(skull);
        b.skulls.add(skull);
        SetPiecePayload.send(level, Piece.WITHER_MUZZLE, from, to, 1, b);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.4F, .9F);
    }
    public static boolean hit(RiftWitherEntity b, ServerPlayer p, float amount) {
        if (!b.fighting() || WitherArena.sheltered(p) || !p.isAlive()) return false;
        p.invulnerableTime = 0;
        boolean landed = p.hurt(b.damageSources().mobAttack(b), amount * (b.starving() ? 1.1F : 1));
        if (landed) p.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
        return landed;
    }
    private static void shove(ServerPlayer p, Vec3 from, double strength, double up) {
        Vec3 away = p.position().subtract(from).multiply(1, 0, 1);
        away = away.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : away.normalize();
        p.setDeltaMovement(away.scale(strength).add(0, up, 0));
        p.hurtMarked = true;
    }

    public static void land(RiftWitherEntity b, Move move) {
        ServerLevel level = (ServerLevel) b.level();
        float damage = WitherGeometry.damage(move, b.phase());
        float shake = .2F;
        switch (move) {
            case BITE -> {
                Vec3 from = b.origins.isEmpty() ? b.castFrom : b.origins.getFirst(), to = b.marks.getFirst();
                for (ServerPlayer p : b.fighters()) {
                    Vec3 body = p.position().add(0, .8, 0);
                    Vec3 flat = new Vec3(body.x, from.y, body.z);
                    if (WitherGeometry.lineDistance(from, to, flat) < WitherGeometry.BITE_RADIUS && p.getY() < WitherGeometry.floor().y + 5
                            && seen(level, b.core(), p.getEyePosition())) hit(b, p, damage);
                }
                SetPiecePayload.sendSeeded(level, Piece.WITHER_ARC, from, to, WitherGeometry.BITE_RADIUS, b, 0, move.ordinal());
                level.playSound(null, to.x, to.y, to.z, SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 2F, .5F);
            }
            case RAKE -> {
                for (ServerPlayer p : b.fighters())
                    if (WitherGeometry.inArc(b.castFrom, p.position(), b.lockedYaw, WitherGeometry.RAKE_REACH, WitherGeometry.RAKE_HALF_ANGLE)
                            && p.getY() - b.castFrom.y < 4.2 && seen(level, b.core(), p.getEyePosition())) {
                        if (hit(b, p, damage)) shove(p, b.position(), 1, .35);
                    }
                Vec3 dir = WitherGeometry.forward(b.lockedYaw);
                SetPiecePayload.sendSeeded(level, Piece.WITHER_ARC, b.castFrom, b.castFrom.add(dir), WitherGeometry.RAKE_REACH, b, 0,
                        move.ordinal() | (b.variant << 5));
                level.playSound(null, b.getX(), b.getY() + 3, b.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.5F, .45F);
            }
            case VERDICT -> {
                b.beams.clear();
                for (int i = 0; i < b.marks.size(); i++) {
                    b.beams.add(new Vec3[]{b.origins.get(i), b.marks.get(i)});
                    SetPiecePayload.sendSeeded(level, Piece.WITHER_FIRE, b.origins.get(i), b.marks.get(i), WitherGeometry.VERDICT_RADIUS, b, 0, move.ordinal());
                }
                b.beamHit.clear();
                b.beamTicks = WitherGeometry.VERDICT_FIRE;
                level.playSound(null, b.getX(), b.getY() + 8, b.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.4F, 1.6F);
            }
            case RAIN -> {
                int index = 0;
                for (Vec3 mark : b.marks) {
                    var skull = ModEntities.RIFT_SKULL.get().create(level);
                    if (skull == null) continue;
                    skull.setOwner(b);
                    skull.setPos(mark.add(0, WitherGeometry.RAIN_HEIGHT + (index++ % 3) * 1.2, 0));
                    skull.setDeltaMovement(0, -.7, 0);
                    skull.accelerationPower = .03;
                    level.addFreshEntity(skull);
                    b.skulls.add(skull);
                }
                level.playSound(null, b.getX(), b.getY() + 8, b.getZ(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 2F, .6F);
            }
            case PULSE -> {
                ripple(b, b.castFrom, WitherGeometry.PULSE_SPEED, WitherGeometry.PULSE_REACH, damage, 0);
                if (b.phase() >= 2) ripple(b, b.castFrom, WitherGeometry.PULSE_SPEED, WitherGeometry.PULSE_REACH, damage, WitherGeometry.PULSE_ECHO);
                if (b.phase() >= 3) ripple(b, b.castFrom, WitherGeometry.PULSE_SPEED * 1.3F, WitherGeometry.PULSE_REACH, damage, WitherGeometry.PULSE_ECHO * 2);
                level.playSound(null, b.getX(), b.getY(), b.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 3F, .5F);
                shake = .35F;
            }
            case TETHER -> {
                Vec3 to = b.marks.getFirst();
                boolean caught = false;
                for (ServerPlayer p : b.fighters())
                    if (WitherGeometry.lineDistance(b.castFrom, to, p.position().add(0, .8, 0)) < WitherGeometry.TETHER_RADIUS + .3
                            && seen(level, b.castFrom, p.getEyePosition())) {
                        if (hit(b, p, damage)) {
                            caught = true;
                            p.setDeltaMovement(b.castFrom.subtract(p.position()).normalize().scale(1.15).add(0, .25, 0));
                            p.hurtMarked = true;
                        }
                    }
                SetPiecePayload.sendSeeded(level, Piece.WITHER_FIRE, b.castFrom, to, WitherGeometry.TETHER_RADIUS, b, 0, move.ordinal());
                level.playSound(null, b.castFrom.x, b.castFrom.y, b.castFrom.z, SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 2F, .5F);
                // From the second phase, whoever it hauls in is bitten as they land: the hook is half of a combo.
                if (caught && b.phase() >= 2) { b.chain.addFirst(Move.BITE); b.quickFollow = true; }
            }
            case LUNGE -> {
                b.motion = move; b.motionTicks = WitherGeometry.LUNGE_TICKS; b.motionStep = 0; b.motionFrom = b.position();
                b.motionTo = b.marks.getFirst().subtract(0, WitherGeometry.LUNGE_LIFT, 0); b.motionHit.clear();
                level.playSound(null, b.getX(), b.getY() + 3, b.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.5F, .55F);
            }
            case SLAM -> {
                b.motion = move; b.motionTicks = WitherGeometry.SLAM_TICKS; b.motionStep = 0; b.motionFrom = b.position();
                b.motionTo = b.marks.getFirst().add(0, .3, 0); b.motionHit.clear();
            }
            case SPIRES -> {
                level.playSound(null, b.getX(), b.getY(), b.getZ(), SoundEvents.WARDEN_DIG, SoundSource.HOSTILE, 2.5F, .7F);
                shake = .3F;
            }
            case BROOD -> {
                for (Vec3 mark : b.marks) {
                    var skeleton = EntityType.WITHER_SKELETON.create(level);
                    if (skeleton == null) continue;
                    brood(skeleton);
                    skeleton.setNoAi(true);
                    skeleton.setInvulnerable(true);
                    skeleton.moveTo(b.castFrom.x, b.castFrom.y - 1, b.castFrom.z, b.lockedYaw, 0);
                    level.addFreshEntity(skeleton);
                    b.minions.add(skeleton);
                    b.spews.add(new Spew(skeleton, b.castFrom.subtract(0, 1, 0), mark, new int[]{0}));
                    SetPiecePayload.sendSeeded(level, Piece.WITHER_SPEW, b.castFrom, mark, 1, b, 0, b.phase());
                }
                level.playSound(null, b.castFrom.x, b.castFrom.y, b.castFrom.z, SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 2.5F, .5F);
                level.playSound(null, b.castFrom.x, b.castFrom.y, b.castFrom.z, SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 2F, .45F);
                level.playSound(null, b.castFrom.x, b.castFrom.y, b.castFrom.z, SoundEvents.SKELETON_AMBIENT, SoundSource.HOSTILE, 2.5F, .5F);
            }
            case HORIZON -> {
                for (ServerPlayer p : b.fighters()) if (seen(level, b.castFrom, p.getEyePosition())) hit(b, p, damage);
                SetPiecePayload.send(level, Piece.WITHER_NOVA, b.castFrom, b.castFrom, WitherGeometry.HORIZON_REACH, b);
                level.playSound(null, b.getX(), b.getY() + 6, b.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 4F, .5F);
                level.playSound(null, b.getX(), b.getY() + 6, b.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3F, .6F);
                shake = .6F;
            }
        }
        CameraShakePayload.sendAround(level, b.position(), 48, shake, 12);
    }
    private static void ripple(RiftWitherEntity b, Vec3 center, float speed, float reach, float damage, int delay) {
        Ripple ripple = new Ripple(center, speed, reach, damage, delay);
        b.ripples.add(ripple);
        if (delay == 0) sendRipple((ServerLevel) b.level(), ripple, b);
    }
    private static void sendRipple(ServerLevel level, Ripple r, RiftWitherEntity b) {
        SetPiecePayload.sendSeeded(level, Piece.WITHER_PULSE, r.center, r.center, r.reach, b, 0, Math.round(r.speed * 100) | (b.phase() << 10));
    }

    /** Ribbreaker's charge and Ossuary Slam's descent: the body really travels, swept so it can't pass through stone. */
    public static void tickMotion(RiftWitherEntity b) {
        ServerLevel level = (ServerLevel) b.level();
        Vec3 before = b.position();
        boolean lunge = b.motion == Move.LUNGE;
        double p = ++b.motionStep / (double) (lunge ? WitherGeometry.LUNGE_TICKS : WitherGeometry.SLAM_TICKS);
        double ease = lunge ? p * p * (3 - 2 * p) : p * p;
        Vec3 desired = b.motionFrom.lerp(b.motionTo, Math.min(1, ease)), delta = desired.subtract(before), end = before;
        if (delta.length() > 3.6) delta = delta.normalize().scale(3.6);
        for (int i = 1; i <= 12; i++) {
            Vec3 next = before.add(delta.scale(i / 12D));
            if (!level.noCollision(b, b.getBoundingBox().move(next.subtract(before)))) break;
            end = next;
        }
        b.setPos(end);
        if (lunge) {
            Vec3 a = before.add(0, WitherGeometry.LUNGE_LIFT, 0), z = end.add(0, WitherGeometry.LUNGE_LIFT, 0);
            for (ServerPlayer player : b.fighters())
                if (!b.motionHit.contains(player.getUUID()) && WitherGeometry.lineDistance(a, z, player.position().add(0, .8, 0)) < WitherGeometry.LUNGE_RADIUS) {
                    b.motionHit.add(player.getUUID());
                    if (hit(b, player, WitherGeometry.damage(Move.LUNGE, b.phase()))) {
                        player.setDeltaMovement(b.motionTo.subtract(b.motionFrom).normalize().scale(1.1).add(0, .35, 0));
                        player.hurtMarked = true;
                    }
                }
            SetPiecePayload.send(level, Piece.WITHER_WAKE, a, z, 3, b);
        }
        boolean blocked = end.distanceToSqr(before.add(delta)) > .05;
        if (--b.motionTicks > 0 && !blocked) return;
        if (lunge) {
            if (blocked || b.lungeCrash) crash(b, end);
            else if (b.phase() >= 3 && b.lungeStreak < 2) {
                // Critical Mass: a charge that misses wheels round and comes again at once.
                b.chain.addFirst(Move.LUNGE);
                b.motionTicks = 0; b.motion = null; b.recovery = 5; b.rest = 0; b.spent = 0;
                return;
            }
        } else if (end.distanceTo(b.motionTo) < 1) {
            Vec3 at = b.motionTo;
            for (ServerPlayer player : b.fighters())
                if (player.position().multiply(1, 0, 1).distanceTo(at.multiply(1, 0, 1)) < WitherGeometry.SLAM_RADIUS && player.getY() < at.y + 3)
                    if (hit(b, player, WitherGeometry.damage(Move.SLAM, b.phase()))) shove(player, at, .9, .5);
            Vec3 ground = new Vec3(at.x, WitherGeometry.floor().y + .05, at.z);
            ripple(b, ground, .8F, WitherGeometry.SLAM_WAVE, WitherGeometry.damage(Move.PULSE, b.phase()) * .8F, 0);
            SetPiecePayload.send(level, Piece.WITHER_IMPACT, ground, ground, WitherGeometry.SLAM_RADIUS, b);
            debris(level, ground, 3, WitherGeometry.SLAM_RADIUS + 1, 16, .55, b.getRandom().nextInt());
            CameraShakePayload.sendAround(level, at, 80, .55F, 22);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.5F, .55F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5F, .4F);
        }
        b.motionTicks = 0;
        b.motion = null;
        b.recovery = Math.max(b.recovery, 14);
    }
    /** Ribbreaker hit stone: the pillar or wall bursts, and it reels with its chest open. */
    private static void crash(RiftWitherEntity b, Vec3 at) {
        ServerLevel level = (ServerLevel) b.level();
        Vec3 dir = b.motionTo.subtract(b.motionFrom);
        dir = dir.lengthSqr() < 1e-4 ? WitherGeometry.forward(b.getYRot()) : dir.normalize();
        Vec3 front = at.add(dir.scale(WitherGeometry.WIDTH / 2 + .6)).add(0, WitherGeometry.LUNGE_LIFT, 0);
        for (int i = 0; i < 14; i++) {
            BlockPos hitAt = BlockPos.containing(front.add((i % 5 - 2) * .8, (i / 5 - 1) * 1.4, 0));
            BlockState state = level.getBlockState(hitAt);
            if (state.isAir()) state = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
            Vec3 back = dir.scale(-.35 - b.getRandom().nextDouble() * .3).add((b.getRandom().nextDouble() - .5) * .5, .35 + b.getRandom().nextDouble() * .3, (b.getRandom().nextDouble() - .5) * .5);
            QuakeDebrisEntity.launch(level, state, Vec3.atCenterOf(hitAt), back);
        }
        SetPiecePayload.send(level, Piece.WITHER_IMPACT, front, front.add(dir), 3, b);
        CameraShakePayload.sendAround(level, at, 64, .6F, 20);
        level.playSound(null, front.x, front.y, front.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2F, .7F);
        level.playSound(null, front.x, front.y, front.z, SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, 3F, .35F);
        b.crashed(at);
    }
    /** Chunks of the real floor thrown up in a ring around {@code center}. Visual only: nothing is removed or placed. */
    static void debris(ServerLevel level, Vec3 center, double inner, double outer, int count, double up, int seed) {
        Random random = new Random(seed);
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2 / count + random.nextDouble() * .4, r = inner + random.nextDouble() * (outer - inner);
            Vec3 at = center.add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
            BlockState state = level.getBlockState(BlockPos.containing(at.x, center.y - .5, at.z));
            if (state.isAir()) continue;
            Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle)).scale(.12 + random.nextDouble() * .18);
            QuakeDebrisEntity.launch(level, state, at.add(0, .6, 0), out.add(0, up * (.7 + random.nextDouble() * .6), 0));
        }
    }

    // ---------------------------------------------------------------- what outlives a windup

    public static void tickHazards(RiftWitherEntity b) {
        ServerLevel level = (ServerLevel) b.level();
        for (Ripple r : List.copyOf(b.ripples)) {
            if (r.delay > 0) {
                if (--r.delay == 0) sendRipple(level, r, b);
                continue;
            }
            r.age++;
            double radius = r.radius();
            for (ServerPlayer p : b.fighters()) {
                double d = p.position().multiply(1, 0, 1).distanceTo(r.center.multiply(1, 0, 1));
                if (Math.abs(d - radius) < WitherGeometry.WAVE_FRONT && p.getY() - r.center.y < WitherGeometry.WAVE_HEIGHT
                        && p.getY() - r.center.y > -2 && r.hit.add(p.getUUID()) && hit(b, p, r.damage)) shove(p, r.center, .5, .5);
            }
            if (radius > r.reach) b.ripples.remove(r);
        }
        if (b.beamTicks > 0) {
            b.beamTicks--;
            for (ServerPlayer p : b.fighters()) {
                if (b.beamHit.contains(p.getUUID())) continue;
                for (Vec3[] beam : b.beams)
                    if (WitherGeometry.lineDistance(beam[0], beam[1], p.position().add(0, .8, 0)) < WitherGeometry.VERDICT_RADIUS) {
                        b.beamHit.add(p.getUUID());
                        hit(b, p, WitherGeometry.damage(Move.VERDICT, b.phase()));
                        break;
                    }
            }
        }
        for (Spew s : List.copyOf(b.spews)) {
            // In flight on its arc; it lands exactly on its mark, then fights.
            int age = ++s.age()[0];
            float k = Math.min(1, age / (float) WitherGeometry.BROOD_FLIGHT);
            Vec3 at = WitherGeometry.spewArc(s.from(), s.to(), k);
            s.mob().setPos(at);
            s.mob().setDeltaMovement(Vec3.ZERO);
            s.mob().setYRot(s.mob().getYRot() + 31);
            if (age < WitherGeometry.BROOD_FLIGHT) continue;
            b.spews.remove(s);
            s.mob().setNoAi(false);
            s.mob().setInvulnerable(false);
            s.mob().fallDistance = 0;
            for (ServerPlayer p : b.fighters())
                if (p.position().multiply(1, 0, 1).distanceTo(s.to().multiply(1, 0, 1)) < WitherGeometry.BROOD_LAND && p.getY() - s.to().y < 2.5
                        && hit(b, p, WitherGeometry.damage(Move.BROOD, b.phase()))) shove(p, s.to(), .55, .35);
            ServerPlayer nearest = b.fighters().stream().min(Comparator.comparingDouble(p -> p.distanceToSqr(s.to()))).orElse(null);
            if (nearest != null) s.mob().setTarget(nearest);
            Vec3 ground = new Vec3(s.to().x, WitherGeometry.floor().y + .05, s.to().z);
            SetPiecePayload.send(level, Piece.WITHER_IMPACT, ground, ground, WitherGeometry.BROOD_LAND + .4F, b);
            debris(level, ground, .6, WitherGeometry.BROOD_LAND + .8, 7, .4, s.mob().getId());
            level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.HOSTILE, 2F, .6F);
            level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.WITHER_SKELETON_STEP, SoundSource.HOSTILE, 2F, .5F);
        }
        for (var minion : List.copyOf(b.minions))
            if (minion.getPersistentData().getBoolean(BROOD) && minion.tickCount > WitherGeometry.BROOD_LIFE && minion.isAlive()) crumble(minion);
        for (Spire s : List.copyOf(b.spires)) {
            if (b.clock == s.at) {
                for (ServerPlayer p : b.fighters())
                    if (p.position().multiply(1, 0, 1).distanceTo(s.base.multiply(1, 0, 1)) < WitherGeometry.SPIRE_RADIUS + .3
                            && p.getY() - s.base.y < WitherGeometry.SPIRE_HEIGHT && p.getY() - s.base.y > -1.5) {
                        if (hit(b, p, WitherGeometry.damage(Move.SPIRES, b.phase()))) { p.setDeltaMovement(p.getDeltaMovement().multiply(.3, 0, .3).add(0, .75, 0)); p.hurtMarked = true; }
                    }
                if ((s.at & 1) == 0) level.playSound(null, s.base.x, s.base.y, s.base.z, SoundEvents.BONE_BLOCK_PLACE, SoundSource.HOSTILE, 1.6F, .5F);
            }
            if (b.clock > s.at + WitherGeometry.SPIRE_LINGER) b.spires.remove(s);
        }
    }

    // ---------------------------------------------------------------- arrival, phases and minions

    /** The fight opens: a tear above the dais, the Wither falls out of it, lands and roars. Invulnerable throughout. */
    public static void arrivalTick(RiftWitherEntity b, int t) {
        ServerLevel level = (ServerLevel) b.level();
        Vec3 gate = WitherGeometry.arrivalGate();
        if (t == 0) {
            SetPiecePayload.send(level, Piece.WITHER_TEAR, gate, gate.add(WitherGeometry.arrivalNormal()), 1, b);
            level.playSound(null, gate.x, gate.y, gate.z, SoundEvents.PORTAL_TRIGGER, SoundSource.HOSTILE, 3F, .5F);
        }
        if (t == WitherGeometry.ARRIVAL_OPEN - 2) level.playSound(null, gate.x, gate.y, gate.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.5F, .6F);
        if (t <= WitherGeometry.ARRIVAL_LAND) b.setPos(WitherGeometry.arrivalPath(t));
        if (t == WitherGeometry.ARRIVAL_LAND) {
            Vec3 ground = WitherGeometry.floor().add(0, .05, 0);
            ripple(b, ground, .9F, WitherGeometry.SLAM_WAVE + 4, 3, 0);
            SetPiecePayload.send(level, Piece.WITHER_IMPACT, ground, ground, WitherGeometry.SLAM_RADIUS + 1, b);
            debris(level, ground, 3, 8, 20, .6, 77);
            CameraShakePayload.sendAround(level, ground, 90, .7F, 26);
            level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3F, .5F);
            level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2F, .35F);
        }
        if (t == WitherGeometry.ARRIVAL_LAND + 6) {
            level.playSound(null, b.getX(), b.getY() + 6, b.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3F, .5F);
            CameraShakePayload.sendAround(level, b.position(), 64, .4F, 30);
        }
    }
    /** A phase change: everyone is thrown back, the room answers, and it roars. */
    public static void roar(RiftWitherEntity b, int phase) {
        ServerLevel level = (ServerLevel) b.level();
        for (ServerPlayer p : b.fighters()) if (p.distanceTo(b) < 14) shove(p, b.position(), 1.2, .45);
        Vec3 ground = new Vec3(b.getX(), WitherGeometry.floor().y + .05, b.getZ());
        SetPiecePayload.sendSeeded(level, Piece.WITHER_PHASE, ground, b.core(), phase, b, 0, phase);
        ripple(b, ground, 1.1F, 14, 0, 0);
        CameraShakePayload.sendAround(level, b.position(), 90, .5F, 30);
        level.playSound(null, b.getX(), b.getY() + 6, b.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3F, phase == 2 ? .7F : .55F);
        minions(b, phase);
    }
    public static void roarTick(RiftWitherEntity b, int t) {
        if (t == 10 && b.phase() == 2) b.level().playSound(null, b.getX(), b.getY() + 7, b.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.HOSTILE, 3F, .45F);
        if (t == 12) b.level().playSound(null, b.getX(), b.getY() + 6, b.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 3F, .45F);
    }
    /** Marks a skeleton as the Wither's: a follower that rolls no gift, drops nothing and crumbles in time. */
    static void brood(net.minecraft.world.entity.monster.AbstractSkeleton skeleton) {
        skeleton.getPersistentData().putBoolean(GiftedMobs.FOLLOWER, true);
        skeleton.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        skeleton.getPersistentData().putBoolean(BROOD, true);
        skeleton.setPersistenceRequired();
        skeleton.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_SWORD));
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) skeleton.setDropChance(slot, 0);
    }
    /** Its time is up (or its maker is gone): it falls apart into bone and dust. */
    public static void crumble(net.minecraft.world.entity.Mob minion) {
        if (!(minion.level() instanceof ServerLevel level)) { minion.discard(); return; }
        Vec3 at = minion.position();
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2 + minion.getId();
            QuakeDebrisEntity.launch(level, Blocks.BONE_BLOCK.defaultBlockState(), at.add(0, 1 + i * .3, 0), new Vec3(Math.cos(angle) * .15, .25, Math.sin(angle) * .15));
        }
        SetPiecePayload.send(level, Piece.WITHER_IMPACT, at.add(0, .05, 0), at, 1.2F, null);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SKELETON_DEATH, SoundSource.HOSTILE, 1.5F, .6F);
        minion.discard();
    }
    public static void minions(RiftWitherEntity b, int phase) {
        ServerLevel level = (ServerLevel) b.level();
        int count = phase == 2 ? Math.min(3, 1 + b.fighters().size() / 2) : b.fighters().size() > 2 ? 1 : 0;
        for (int i = 0; i < count; i++) {
            var skeleton = EntityType.WITHER_SKELETON.create(level);
            if (skeleton == null) continue;
            brood(skeleton);
            Vec3 at = WitherGeometry.floor().add(i % 2 == 0 ? -21 : 21, 0, 4 - i * 4);
            skeleton.moveTo(at);
            if (!b.fighters().isEmpty()) skeleton.setTarget(b.fighters().getFirst());
            level.addFreshEntity(skeleton);
            b.minions.add(skeleton);
            SetPiecePayload.send(level, Piece.WITHER_IMPACT, at.add(0, .05, 0), at, 1.6F, null);
            debris(level, at.add(0, .05, 0), .5, 2.2, 6, .4, i);
        }
    }

    // ---------------------------------------------------------------- death

    /** The death scene, tick by tick: thrash, burst, swell, drink, detonate. The entity itself ends at DEATH_TICKS. */
    public static void deathTick(RiftWitherEntity b, int age) {
        ServerLevel level = (ServerLevel) b.level();
        Vec3 core = b.core();
        if (age == 6 || age == 16 || age == 25) {
            level.playSound(null, core.x, core.y, core.z, SoundEvents.WITHER_HURT, SoundSource.HOSTILE, 3F, .4F + age * .01F);
            CameraShakePayload.sendAround(level, core, 64, .25F, 8);
        }
        if (age == WitherGeometry.RIBS_BURST) {
            for (int i = 0; i < 10; i++) {
                double angle = i * Math.PI * 2 / 10 + b.getYRot() * Math.PI / 180;
                Vec3 out = new Vec3(Math.cos(angle) * .45, .35 + (i % 3) * .12, Math.sin(angle) * .45);
                QuakeDebrisEntity.launch(level, (i % 3 == 0 ? Blocks.CALCITE : Blocks.BONE_BLOCK).defaultBlockState(), core.add(out.scale(2)), out);
            }
            level.playSound(null, core.x, core.y, core.z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.HOSTILE, 4F, .5F);
            level.playSound(null, core.x, core.y, core.z, SoundEvents.SKELETON_DEATH, SoundSource.HOSTILE, 3F, .4F);
            CameraShakePayload.sendAround(level, core, 80, .45F, 14);
        }
        if (age > WitherGeometry.RIBS_BURST && age < WitherGeometry.IMPLODE) {
            // It rises over the dais as the star swells.
            Vec3 lift = new Vec3(0, .045, 0);
            if (level.noCollision(b, b.getBoundingBox().move(lift))) b.setPos(b.position().add(lift));
            if (age % 10 == 0) level.playSound(null, core.x, core.y, core.z, SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 4F, .5F + (age - 30) * .015F);
        }
        if (age >= WitherGeometry.IMPLODE && age < WitherGeometry.DETONATE) {
            // The star drinks everything back in: a breath before the blast.
            if (age == WitherGeometry.IMPLODE) level.playSound(null, core.x, core.y, core.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 5F, .5F);
            for (ServerPlayer p : b.everyone()) {
                Vec3 pull = core.subtract(p.position()).multiply(1, 0, 1);
                if (pull.length() < 3 || pull.length() > 40) continue;
                p.setDeltaMovement(p.getDeltaMovement().add(pull.normalize().scale(.06)));
                p.hurtMarked = true;
            }
        }
        if (age == WitherGeometry.DETONATE) detonate(b);
    }
    /** The star goes off. Everyone in the Ossuary is knocked flat unless they shield themselves. */
    static void detonate(RiftWitherEntity b) {
        ServerLevel level = (ServerLevel) b.level();
        Vec3 core = b.core(), ground = new Vec3(b.getX(), WitherGeometry.floor().y + .05, b.getZ());
        SetPiecePayload.send(level, Piece.WITHER_DETONATE, core, ground, WitherGeometry.BLAST_RADIUS, null);
        SetPiecePayload.sendSeeded(level, Piece.WITHER_PULSE, ground, ground, 44, null, 0, 120 | (3 << 10));
        debris(level, ground, 3, 9, 28, .85, 6141);
        debris(level, ground, 9, 18, 20, .55, 6142);
        level.playSound(null, core.x, core.y, core.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 6F, .45F);
        level.playSound(null, core.x, core.y, core.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 5F, .5F);
        level.playSound(null, core.x, core.y, core.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 5F, .55F);
        CameraShakePayload.sendAround(level, core, 120, 1F, 40);
        for (ServerPlayer p : b.everyone()) if (p.position().distanceTo(core) <= WitherGeometry.BLAST_RADIUS) WitherArena.blast(p, core);
    }
}
