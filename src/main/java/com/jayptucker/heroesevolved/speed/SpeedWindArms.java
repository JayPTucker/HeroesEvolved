package com.jayptucker.heroesevolved.speed;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Wind Arms (Shift + the Cyclone key): spin both arms so fast they become two funnels of wind, for as long as
 * the key is held (up to three seconds).
 * <ul>
 *   <li>Look ahead and the funnels blow everything in front of you away: creatures, dropped items, primed TNT and
 *   TNT minecarts, out to {@link #REACH} blocks, harder the closer they are. It does no harm, and puts out what is
 *   burning.</li>
 *   <li>Shots flying into the funnels are turned round and blown back along your look, and become yours: arrows,
 *   tridents, potions, a ghast's fireball, a wither skull, an EMP grenade, TaCZ bullets and explosive rounds. A
 *   bullet crosses several blocks a tick, so each shot's whole step is checked before it moves
 *   ({@link #catchShot}), again on impact, and again before a TaCZ round lands.</li>
 *   <li>Clouds are carried off and thinned: a lingering potion's or dragon's breath cloud drifts away along the
 *   wind and shrinks until it is gone. On every client, smoke and other particles in the funnels are blown along
 *   out into a plume and fade within three seconds ({@code client/effects/WindArmsAir}), so a smoke screen clears.</li>
 *   <li>Look down and the funnels drive into the ground and lift you: you rise up to {@link #LIFT} blocks above
 *   the ground and hover there, drifting the way you face. Let go and you float down.</li>
 * </ul>
 */
public final class SpeedWindArms {
    public static final int MAX_TICKS = 60, ENERGY = 12, COOLDOWN = 80;
    public static final double REACH = 14, LIFT = 7;
    /** Looking further down than this (the look's y) points the funnels at the ground. */
    static final double DOWN = -.7;

    private static final class Arms {
        final ServerPlayer player;
        final long start;
        boolean lifted;
        /** Shots already turned, so a trident (which keeps its thrower) isn't caught again every tick. */
        final Set<UUID> turned = new HashSet<>();
        Arms(ServerPlayer p, long start) { player = p; this.start = start; }
    }
    private static final Map<UUID, Arms> ARMS = new HashMap<>();

    private SpeedWindArms() {}

    private static long now(ServerPlayer p) { return p.getServer().overworld().getGameTime(); }

    public static boolean active(ServerPlayer p) { return ARMS.containsKey(p.getUUID()); }

    /** Whether anyone has Wind Arms going: the projectile hooks skip all their work when no one does. */
    public static boolean any() { return !ARMS.isEmpty(); }

    /** How far a funnel reaches out from the axis {@code along} blocks ahead of the eye. */
    public static double width(double along) { return 1.3 + along * .35; }

    /**
     * How far {@code point} is along the funnels held from {@code eye} along {@code look}, or -1 when it is outside
     * them. {@code extra} widens the test for things with a size of their own (a cloud's radius). Shared by the
     * server's push and the client's particles, so what is blown is what the funnels cover.
     */
    public static double inFunnel(Vec3 eye, Vec3 look, Vec3 point, double extra) {
        Vec3 rel = point.subtract(eye);
        double along = rel.dot(look);
        if (along < .3 || along > REACH) return -1;
        return rel.subtract(look.scale(along)).length() <= width(along) + extra ? along : -1;
    }

    public static boolean start(ServerPlayer p) {
        if (active(p) || p.isPassenger() || p.isSpectator()) return false;
        ARMS.put(p.getUUID(), new Arms(p, now(p)));
        ArmPosePayload.show(p, ArmPose.WIND_ARMS, MAX_TICKS + 5);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BREEZE_CHARGE, SoundSource.PLAYERS, .8F, 1.4F);
        blow(p, ARMS.get(p.getUUID()), 0);
        return true;
    }

    /** Lets the arms stop. A lift ends in a gentle float down. */
    public static boolean stop(ServerPlayer p) {
        Arms a = ARMS.remove(p.getUUID());
        if (a == null) return false;
        ArmPosePayload.clear(p);
        if (a.lifted && !p.onGround()) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false, true));
        return true;
    }

    static void tick(long now) {
        for (Arms a : List.copyOf(ARMS.values())) {
            ServerPlayer p = a.player;
            long age = now - a.start;
            if (p.isRemoved() || !p.isAlive() || p.isSpectator() || p.isPassenger() || age > MAX_TICKS || !PowerRules.canAct(p)) { stop(p); continue; }
            blow(p, a, age);
        }
    }

    private static void blow(ServerPlayer p, Arms a, long age) {
        ServerLevel level = p.serverLevel();
        Vec3 look = p.getLookAngle();
        if (look.y < DOWN) lift(p, a);
        else push(p, look);
        if (age % 10 == 0)
            SetPiecePayload.send(level, Piece.SPEED_ARMS, p.position(), p.position().add(look), 1, p);
        if (age % 8 == 0)
            level.playSound(null, p.blockPosition(), SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS, 1F, 1.6F + level.random.nextFloat() * .2F);
    }

    /** Funnels into the ground: up to {@link #LIFT} blocks above it, then a hover, drifting where you face. */
    private static void lift(ServerPlayer p, Arms a) {
        a.lifted = true;
        double height = heightAboveGround(p);
        double up = height < LIFT - 1 ? .5 : height < LIFT ? .12 : -.02;
        Vec3 drift = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
        if (drift.lengthSqr() > 1e-6) drift = drift.normalize().scale(.22);
        p.setDeltaMovement(drift.x, up, drift.z);
        p.resetFallDistance();
        p.hurtMarked = true;
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
    }

    public static double heightAboveGround(Entity e) {
        Vec3 from = e.position();
        var hit = e.level().clip(new ClipContext(from, from.add(0, -(LIFT + 2), 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, e));
        return hit.getType() == HitResult.Type.MISS ? LIFT + 2 : from.y - hit.getLocation().y;
    }

    /** Everything in the cone in front of you, out to {@link #REACH} blocks, is blown away along your look. */
    private static void push(ServerPlayer p, Vec3 look) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        for (Entity e : level.getEntities(p, p.getBoundingBox().inflate(REACH + 4),
                e -> e instanceof ItemEntity || e instanceof Projectile || explosive(e) || e instanceof AreaEffectCloud
                        || e instanceof LivingEntity l && SpeedService.canHit(p, l))) {
            if (e instanceof Projectile) {
                // Most shots are caught before their own tick; this catches any that slipped through.
                catchShot(level, e, e.position(), e.position().add(e.getDeltaMovement()));
                continue;
            }
            if (e instanceof AreaEffectCloud cloud) {
                double along = inFunnel(eye, look, cloud.position(), cloud.getRadius());
                if (along >= 0) disperse(level, cloud, look, 1 - along / REACH);
                continue;
            }
            double along = inFunnel(eye, look, e.getBoundingBox().getCenter(), 0);
            if (along < 0) continue;
            double k = 1 - along / REACH;
            double resist = e instanceof LivingEntity l ? Math.max(.2, 1 - l.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)) : 1;
            // Primed TNT is flung hard enough to go off well clear of you.
            double force = explosive(e) ? 1.5 : 1;
            Vec3 shove = look.scale((.3 + .55 * k) * resist * force).add(0, (.06 + .08 * k) * resist * force, 0);
            e.setDeltaMovement(e.getDeltaMovement().scale(.4).add(shove));
            e.hurtMarked = true;
            e.hasImpulse = true;
            if (e.isOnFire()) e.clearFire();
            if (e instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
        }
        if (p.isOnFire()) p.clearFire();
    }

    /** Explosives that aren't shots: primed TNT and a TNT minecart. (Fireballs, skulls and rounds are shots.) */
    static boolean explosive(Entity e) { return e instanceof PrimedTnt || e instanceof MinecartTNT; }

    /**
     * A lingering cloud in the wind drifts away along it and thins. Clouds never send their position on their own
     * (their tracker's interval is effectively never), so every move is sent as a teleport.
     */
    private static void disperse(ServerLevel level, AreaEffectCloud cloud, Vec3 look, double k) {
        float radius = cloud.getRadius() - (float) (.1 + .15 * k);
        if (radius < .5F) { cloud.discard(); return; }
        cloud.setRadius(radius);
        cloud.setPos(cloud.position().add(look.scale(.3 + .4 * k)));
        level.getChunkSource().broadcast(cloud, new ClientboundTeleportEntityPacket(cloud));
    }

    /**
     * A shot about to move from {@code from} to {@code to} meets a funnel: it is turned round, blown back along the
     * speedster's look at no less than its own speed, and becomes theirs. Asked before every projectile's tick, on its
     * impact, and before a TaCZ round lands, because a bullet can cross the whole funnel inside one tick. Returns
     * whether it was turned.
     */
    public static boolean catchShot(ServerLevel level, Entity entity, Vec3 from, Vec3 to) {
        if (ARMS.isEmpty() || entity.isRemoved() || !(entity instanceof Projectile shot)) return false;
        for (Arms a : ARMS.values()) {
            ServerPlayer p = a.player;
            if (p.serverLevel() != level || a.turned.contains(shot.getUUID()) || !blowable(p, shot)) continue;
            Vec3 look = p.getLookAngle();
            if (look.y < DOWN) continue;
            Vec3 eye = p.getEyePosition();
            // A shot coming from behind meets your back before it meets your hands.
            if (from.subtract(eye).dot(look) < 0) continue;
            if (from.distanceTo(eye) > REACH + 3 + from.distanceTo(to)) continue;
            Vec3 at = meets(eye, look, from, to);
            if (at == null) continue;
            turn(level, a, shot, from, at, look);
            return true;
        }
        return false;
    }

    /** Whether the arms would turn {@code shot}: not your own, not a friend's, and actually flying. */
    private static boolean blowable(ServerPlayer p, Projectile shot) {
        Entity owner = shot.getOwner();
        if (owner == p) return false;
        if (owner instanceof LivingEntity l && l != p && !SpeedService.canHit(p, l)) return false;
        // An arrow stuck in a wall is left there.
        return !(shot instanceof AbstractArrow) || shot.getDeltaMovement().lengthSqr() > .0025;
    }

    /** The first point of the step from {@code from} to {@code to} that lies in the funnels, or null. */
    private static Vec3 meets(Vec3 eye, Vec3 look, Vec3 from, Vec3 to) {
        Vec3 step = to.subtract(from);
        int n = Math.min(96, (int) Math.ceil(step.length() / .4));
        for (int i = 0; i <= n; i++) {
            Vec3 point = n == 0 ? from : from.add(step.scale(i / (double) n));
            if (inFunnel(eye, look, point, .3) >= 0) return point;
        }
        return null;
    }

    private static void turn(ServerLevel level, Arms a, Projectile shot, Vec3 from, Vec3 at, Vec3 look) {
        a.turned.add(shot.getUUID());
        boolean bullet = PowerWeapons.isBullet(shot);
        double speed = Math.max(bullet ? .5 : 1.2, shot.getDeltaMovement().length());
        // A thrown trident keeps its thrower, so loyalty still brings it home to them.
        if (!(shot instanceof ThrownTrident)) shot.setOwner(a.player);
        // The shot is turned where it is now; it never reaches the hands.
        shot.setDeltaMovement(look.scale(speed));
        shot.hurtMarked = true;
        shot.hasImpulse = true;
        level.getChunkSource().broadcastAndSend(shot, new ClientboundSetEntityMotionPacket(shot));
        SetPiecePayload.send(level, Piece.SPEED_DEFLECT, from, from.add(look), bullet ? .7F : 1, null);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.BREEZE_DEFLECT, SoundSource.PLAYERS, .7F,
                1.2F + level.random.nextFloat() * .3F);
    }

    static void forget(ServerPlayer p) { ARMS.remove(p.getUUID()); }

    static void clear() { ARMS.clear(); }
}
