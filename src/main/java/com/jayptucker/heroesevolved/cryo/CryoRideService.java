package com.jayptucker.heroesevolved.cryo;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.mimicry.MimicryService;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.CryoRideStatePayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.speed.SpeedService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Skating over an actual, reversible ribbon of ice, with bounded rising steps and unsupported gaps. */
public final class CryoRideService {
    private static final int TAIL_TICKS = 120, MAXIMUM_CELLS = 128;
    private static final int SLOPE_TICKS = 3, HEIGHT_LIMIT = 12;
    private static final Map<UUID, Ride> RIDES = new HashMap<>();
    private CryoRideService() {}
    private static final class Ride {
        final ServerPlayer player;
        final ServerLevel level;
        final boolean priorNoGravity;
        final LinkedHashSet<BlockPos> cells = new LinkedHashSet<>();
        final Set<BlockPos> foundations = new java.util.HashSet<>();
        final LinkedHashMap<BlockPos, Integer> treads = new LinkedHashMap<>();
        Vec3 direction, previous, freshIce;
        float forward, strafe;
        boolean rise, descend, sprint;
        int age, speedStep = 1, surface, groundTop, lastSlope;
        double unsupported;
        long inputAt;
        Ride(ServerPlayer p, int supportHeight) {
            player = p; level = p.serverLevel(); priorNoGravity = p.isNoGravity();
            direction = horizontal(p); previous = p.position();
            surface = groundTop = supportHeight;
            inputAt = level.getGameTime();
        }
    }
    public static boolean isRiding(ServerPlayer p) { return RIDES.containsKey(p.getUUID()); }
    public static int speedStep(ServerPlayer p) { Ride r = RIDES.get(p.getUUID()); return r == null ? 0 : r.speedStep; }
    public static double rideSpeed(ServerPlayer p) { return isRiding(p) ? p.getDeltaMovement().horizontalDistance() : 0; }
    public static int ribbonCells(ServerPlayer p) { Ride r = RIDES.get(p.getUUID()); return r == null ? 0 : r.cells.size(); }
    private static boolean authorized(ServerPlayer p) {
        return PlayerAbilityService.getData(p).assignedPower()
                .filter(e -> e.getKey().equals(ModAbilities.CRYOKINESIS_ID) && e.getValue().isUnlocked()).isPresent()
                || MimicryService.isBorrowingCryokinesis(p);
    }
    public static boolean canStart(ServerPlayer p) {
        return authorized(p) && PlayerProgressionService.getLevel(p) >= 3 && PowerRules.canAct(p)
                && !p.isPassenger() && !p.isFallFlying() && !p.isInLava();
    }
    public static boolean toggle(ServerPlayer p, int level) {
        if (isRiding(p)) { stop(p, true); return true; }
        if (!canStart(p)) return false;
        BlockPos foot = BlockPos.containing(p.position().add(0,-.06,0));
        var atFoot = p.level().getBlockState(foot);
        int supportHeight;
        if (FrostConstructBlock.isFrost(atFoot)) {
            supportHeight = foot.getY()*2 + (int)(atFoot.getValue(FrostConstructBlock.FORM).top*2);
        } else {
            BlockPos support = ground(p.serverLevel(), p.position(), 2, Set.of());
            if (support == null || p.getY() - support.getY() - 1 > 1.25) return false;
            supportHeight = (support.getY()+1)*2;
        }
        Ride ride = new Ride(p, supportHeight);
        if (!platform(ride, BlockPos.containing(p.position()).atY(0), ride.surface)) return false;
        RIDES.put(p.getUUID(), ride);
        CryoCastEffects.spray(p, p.position().add(ride.direction.scale(.85)).add(0, -.06, 0), .62F);
        p.setNoGravity(true); p.resetFallDistance();
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, .8F, 1.1F);
        sync(p);
        return true;
    }
    public static void input(ServerPlayer p, float forward, float strafe, boolean rise, boolean descend, boolean sprint) {
        Ride r = RIDES.get(p.getUUID());
        if (r == null || !Float.isFinite(forward) || !Float.isFinite(strafe)) return;
        r.forward = Math.clamp(forward, -1, 1); r.strafe = Math.clamp(strafe, -1, 1);
        r.rise = rise; r.descend = descend; r.sprint = sprint; r.inputAt = p.serverLevel().getGameTime();
    }
    public static void changeSpeed(ServerPlayer p, int direction) {
        Ride r = RIDES.get(p.getUUID());
        if (r == null || !canStart(p) || direction == 0) return;
        r.speedStep = Math.clamp(r.speedStep + Integer.signum(direction), 1,
                Math.clamp(PlayerProgressionService.getLevel(p) - 2, 1, 3));
        sync(p);
    }
    private static Vec3 horizontal(ServerPlayer p) {
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        return look.lengthSqr() < .001 ? Vec3.directionFromRotation(0, p.getYRot()) : look.normalize();
    }
    /** Ignore grown air steps, retaining only terrain that existed before this ride frosted it. */
    private static BlockPos ground(ServerLevel level, Vec3 near, int depth, Set<BlockPos> foundations) {
        BlockPos start = BlockPos.containing(near.add(0, -.05, 0));
        for (int dy = 0; dy <= depth; dy++) {
            BlockPos pos = start.below(dy);
            if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)) return null;
            var state = level.getBlockState(pos);
            if (TemporaryBlocks.isTemporary(level, pos)) {
                if (foundations.contains(pos)) return pos;
                continue;
            }
            if (state.is(Blocks.WATER) || state.isFaceSturdy(level, pos, Direction.UP)) return pos;
        }
        return null;
    }
    private static boolean cell(Ride r, BlockPos pos, int surface) {
        if (!r.level.hasChunkAt(pos) || r.level.isOutsideBuildHeight(pos)) return false;
        boolean wasTemporary = TemporaryBlocks.isTemporary(r.level, pos);
        var original = r.level.getBlockState(pos);
        boolean foundation = !wasTemporary && (original.is(Blocks.WATER) || original.isFaceSturdy(r.level, pos, Direction.UP));
        if (!CryoBridge.lay(r.player, pos, surface, TAIL_TICKS, r.cells.contains(pos))) return false;
        BlockPos flat = pos.atY(0);
        r.treads.putLast(flat, surface);
        if (r.treads.size() > MAXIMUM_CELLS) r.treads.pollFirstEntry();
        // Riding existing ice never takes ownership of it.
        if (!TemporaryBlocks.isTemporary(r.level, pos) || wasTemporary && !r.cells.contains(pos)) return true;
        if (!original.equals(r.level.getBlockState(pos)))
            r.freshIce = new Vec3(pos.getX()+.5, surface*.5+.05, pos.getZ()+.5);
        r.cells.remove(pos); r.cells.add(pos.immutable());
        if (foundation) r.foundations.add(pos.immutable());
        else if (!wasTemporary) r.foundations.remove(pos);
        while (r.cells.size() > MAXIMUM_CELLS) {
            BlockPos old = r.cells.iterator().next();
            r.cells.remove(old); r.foundations.remove(old); TemporaryBlocks.restore(r.level, old, r.player);
        }
        return true;
    }
    private static boolean platform(Ride r, BlockPos flat, int surface) {
        BlockPos center = CryoBridge.atHeight(flat, surface);
        if (!cell(r, center, surface)) return false;
        if (SkillTreeService.has(r.player, "cryokinesis_double_weave")) {
            Vec3 centerSpray = r.freshIce;
            Direction side = CryoBridge.side(r.direction);
            widen(r, center.relative(side), surface);
            widen(r, center.relative(side.getOpposite()), surface);
            if (centerSpray != null) r.freshIce = centerSpray;
        }
        return true;
    }

    private static void widen(Ride r, BlockPos pos, int surface) {
        if (r.treads.getOrDefault(pos.atY(0), surface) == surface) cell(r, pos, surface);
    }

    private static boolean ribbon(Ride r, Vec3 from, Vec3 to, int slope) {
        int height = r.surface;
        for (BlockPos flat : CryoBridge.route(from, to)) {
            Integer laid = r.treads.get(flat);
            // The ribbon retains its steps when the rider changes slope or carves back over it.
            if (laid != null && Math.abs(laid - height) <= 2) height = laid;
            else height = nextTread(r, flat, height, slope);
            if (!platform(r, flat, height)) return false;
        }
        r.surface = height;
        return true;
    }

    private static int nextTread(Ride r, BlockPos flat, int height, int slope) {
        double y = r.player.getY();
        BlockPos terrain = ground(r.level, Vec3.atCenterOf(flat).with(Direction.Axis.Y, y), 8, r.foundations);
        int floor = terrain != null ? (terrain.getY() + 1) * 2
                : Math.max(r.level.getMinBuildHeight() * 2 + 1, r.groundTop - HEIGHT_LIMIT);
        boolean supported = slope > 0 ? y >= height * .5 - .05 : y <= height * .5 + .55;
        if (slope == 0 || !supported || r.age - r.lastSlope < SLOPE_TICKS) return Math.max(floor, height);
        int next = Math.clamp(height + slope, floor, Math.max(floor, r.groundTop + HEIGHT_LIMIT));
        // Leave the tread under the rider intact. The next rise starts beyond their leading edge.
        if (next > height && new AABB(flat).move(0, y, 0).intersects(r.player.getBoundingBox())) return height;
        if (next != height) r.lastSlope = r.age;
        return next;
    }

    private static Vec3 followTread(ServerPlayer p, Vec3 forward, double top) {
        Vec3 start = p.position();
        double lift = top + .015 - start.y;
        double vertical = Math.clamp(lift * .65, -.32, .28);
        if (vertical < 0) {
            // Land flush on the old tread when the rider is still crossing its edge.
            AABB body = p.getBoundingBox();
            vertical = Shapes.collide(Direction.Axis.Y, body,
                    p.level().getBlockCollisions(p, body.expandTowards(0, vertical, 0)), vertical);
        }
        Vec3 elevated = SpeedService.swept(p, new Vec3(0, vertical, 0));
        Vec3 end = SpeedService.sweptFrom(p, elevated, forward, point -> true, true);
        double covered = end.subtract(elevated).horizontalDistanceSqr();
        if (lift > vertical && covered + 1e-5 < forward.horizontalDistanceSqr()) {
            // Clear the face of the new half step before crossing it, matching vanilla's Y-first movement.
            Vec3 raised = SpeedService.swept(p, new Vec3(0, Math.min(.55, lift), 0));
            Vec3 further = SpeedService.sweptFrom(p, raised, forward, point -> true, true);
            if (further.subtract(raised).horizontalDistanceSqr() > covered) end = further;
        }
        return end.subtract(start);
    }
    public static void tick(ServerPlayer p) {
        Ride r = RIDES.get(p.getUUID());
        if (r == null) return;
        if (r.level != p.level() || !canStart(p)) { stop(p, true); return; }
        r.age++;
        if (r.level.getGameTime() - r.inputAt > 10) { r.forward = r.strafe = 0; r.rise = r.descend = r.sprint = false; }
        int level = PlayerProgressionService.getLevel(p);
        r.speedStep = Math.min(r.speedStep, Math.clamp(level - 2, 1, 3));
        boolean steady = SkillTreeService.has(p, "cryokinesis_1_0");
        Vec3 desiredDirection = horizontal(p).add(Vec3.directionFromRotation(0, p.getYRot() - 90).scale(r.strafe * .65));
        if (desiredDirection.lengthSqr() > .001) r.direction = r.direction.lerp(desiredDirection.normalize(), steady ? .24 : .16).normalize();
        double throttle = Math.clamp(r.forward, 0, 1);
        if (r.forward == 0 && (r.rise || r.descend)) throttle = 1;
        double speed = (.35 + r.speedStep * .25) * throttle * (r.sprint ? 1.18 : 1);
        double travel = p.position().distanceTo(r.previous);
        r.previous = p.position();
        if (r.age % 20 == 0) {
            int drain = speed > .05 ? 8 : 4;
            if (!PlayerEnergyService.tryConsume(p, drain)) { stop(p, true); return; }
            MasteryService.awardEnergySpent(p, drain);
        }
        BlockPos actual = ground(r.level, p.position(), 8, r.foundations);
        if (actual != null) { r.groundTop = (actual.getY() + 1) * 2; r.unsupported = 0; }
        else if (travel > .04) r.unsupported += Math.min(2, travel);
        if (r.unsupported > 6 || p.getY() > (r.groundTop + HEIGHT_LIMIT) * .5 + .25) { stop(p, true); return; }
        int slope = speed > .05 ? Boolean.compare(r.rise, r.descend) : 0;
        Vec3 desired = r.direction.scale(speed);
        Vec3 forward = p.getDeltaMovement().multiply(1,0,1).lerp(desired, throttle == 0 ? steady ? .56 : .45 : .36);
        Vec3 travelDirection = forward.horizontalDistanceSqr() > .01 ? forward.normalize() : r.direction;
        Vec3 ahead = p.position().add(travelDirection.scale(Math.max(.9, forward.horizontalDistance() + .4)));
        if (!ribbon(r, p.position(), ahead, slope)) { stop(p, true); return; }
        Vec3 velocity = followTread(p, forward, r.surface * .5);
        if (velocity.lengthSqr() < 1e-5 && forward.horizontalDistanceSqr() >= .01) { stop(p, true); return; }
        p.setDeltaMovement(velocity); p.hurtMarked = true; p.resetFallDistance();
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        if (r.age % 3 == 0) {
            AbilityEffects.send(r.level, Kind.CRYO_RIDE, p.position(), p.position().add(velocity), 1);
        }
        if (r.age % 4 == 0 && r.freshIce != null) {
            CryoCastEffects.spray(p, r.freshIce, .72F);
            r.freshIce = null;
        }
        if (r.age % 10 == 0 && travel > .08 && SkillTreeService.has(p, "cryokinesis_1_2")) {
            for (LivingEntity enemy : r.level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(2.5),
                    e -> ExpansionSkills.hostile(p, e) && e.distanceToSqr(p) < 6.25 && p.hasLineOfSight(e)).stream().limit(12).toList())
                CryoService.chill(enemy, 20, p);
        }
        if (r.age % 5 == 0) sync(p);
    }
    public static void stop(ServerPlayer p, boolean safe) {
        Ride r = RIDES.remove(p.getUUID());
        if (r == null) return;
        p.setNoGravity(r.priorNoGravity);
        p.setDeltaMovement(p.getDeltaMovement().multiply(.4, 1, .4));
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        p.resetFallDistance();
        if (safe && p.isAlive()) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false));
        sync(p);
    }
    public static void forget(ServerPlayer p) { stop(p, false); }
    public static void clear() { for (Ride r : List.copyOf(RIDES.values())) stop(r.player, false); }
    public static void sync(ServerPlayer p) {
        CryoRideStatePayload.send(p, isRiding(p), speedStep(p), (float) rideSpeed(p));
    }
}
