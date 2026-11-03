package com.jayptucker.heroesevolved.cryo;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.mimicry.MimicryService;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.CryoRideStatePayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Skating over an actual, reversible ribbon of ice, with bounded rising steps and unsupported gaps. */
public final class CryoRideService {
    private static final int TAIL_TICKS = 120, MAXIMUM_CELLS = 128;
    private static final Map<UUID, Ride> RIDES = new HashMap<>();
    private CryoRideService() {}
    private static final class Ride {
        final ServerPlayer player;
        final ServerLevel level;
        final boolean priorNoGravity;
        final LinkedHashSet<BlockPos> cells = new LinkedHashSet<>();
        final Set<BlockPos> foundations = new java.util.HashSet<>();
        Vec3 direction, previous, effectPoint;
        float forward, strafe;
        boolean rise, brake, sprint;
        int age, speedStep = 1, floor;
        double groundTop, unsupported;
        long inputAt;
        Ride(ServerPlayer p, BlockPos support) {
            player = p; level = p.serverLevel(); priorNoGravity = p.isNoGravity();
            direction = horizontal(p); previous = effectPoint = p.position();
            floor = support.getY(); groundTop = floor + 1;
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
        BlockPos support = ground(p.serverLevel(), p.position(), 2, Set.of());
        if (support == null || p.getY() - support.getY() - 1 > 1.25) return false;
        Ride ride = new Ride(p, support);
        if (!platform(ride, p.position(), ride.floor)) return false;
        RIDES.put(p.getUUID(), ride);
        p.setNoGravity(true); p.resetFallDistance();
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, .8F, 1.1F);
        sync(p);
        return true;
    }
    public static void input(ServerPlayer p, float forward, float strafe, boolean rise, boolean brake, boolean sprint) {
        Ride r = RIDES.get(p.getUUID());
        if (r == null || !Float.isFinite(forward) || !Float.isFinite(strafe)) return;
        r.forward = Math.clamp(forward, -1, 1); r.strafe = Math.clamp(strafe, -1, 1);
        r.rise = rise; r.brake = brake; r.sprint = sprint; r.inputAt = p.serverLevel().getGameTime();
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
    private static boolean cell(Ride r, BlockPos pos) {
        if (!r.level.hasChunkAt(pos) || r.level.isOutsideBuildHeight(pos)) return false;
        if (!TemporaryBlocks.isTemporary(r.level, pos) && r.level.getBlockState(pos).is(net.minecraft.tags.BlockTags.ICE))
            return WorldMutationPolicy.canEdit(r.player, r.level, pos);
        boolean wasTemporary = TemporaryBlocks.isTemporary(r.level, pos);
        var original = r.level.getBlockState(pos);
        if (wasTemporary && !original.is(net.minecraft.tags.BlockTags.ICE)) return false;
        boolean foundation = !wasTemporary && (original.is(Blocks.WATER) || original.isFaceSturdy(r.level, pos, Direction.UP));
        boolean placed;
        if (wasTemporary) placed = TemporaryBlocks.refresh(r.level, pos, TAIL_TICKS, r.player);
        else {
            var state = r.level.getBlockState(pos);
            placed = state.isFaceSturdy(r.level, pos, Direction.UP) && !state.is(Blocks.WATER)
                    ? TemporaryBlocks.frostSurface(r.level, pos, TAIL_TICKS, r.player)
                    : TemporaryBlocks.place(r.level, pos, Blocks.PACKED_ICE.defaultBlockState(), TAIL_TICKS, r.player);
        }
        if (!placed) return false;
        // Riding someone else's ice may refresh support, but never takes ownership of their construct.
        if (wasTemporary && !r.cells.contains(pos)) return true;
        r.cells.remove(pos); r.cells.add(pos.immutable());
        if (foundation) r.foundations.add(pos.immutable());
        else if (!wasTemporary) r.foundations.remove(pos);
        while (r.cells.size() > MAXIMUM_CELLS) {
            BlockPos old = r.cells.iterator().next();
            r.cells.remove(old); r.foundations.remove(old); TemporaryBlocks.restore(r.level, old, r.player);
        }
        return true;
    }
    private static boolean platform(Ride r, Vec3 at, int floor) {
        BlockPos center = new BlockPos(net.minecraft.util.Mth.floor(at.x), floor, net.minecraft.util.Mth.floor(at.z));
        if (!cell(r, center)) return false;
        boolean wide = SkillTreeService.has(r.player, "cryokinesis_double_weave");
        if (wide) {
            Vec3 side = new Vec3(r.direction.z, 0, -r.direction.x);
            for (int sign : new int[]{-1, 1}) cell(r, BlockPos.containing(at.add(side.scale(sign))).atY(floor));
        }
        return true;
    }
    /** Fill every crossed cell so the fastest carve remains a connected, collidable ribbon. */
    private static boolean ribbon(Ride r, Vec3 from, Vec3 to) {
        int samples = Math.max(1, (int) Math.ceil(from.distanceTo(to) / .45));
        LinkedHashSet<BlockPos> centers = new LinkedHashSet<>();
        for (int i = 0; i <= samples; i++) centers.add(BlockPos.containing(from.lerp(to, (double) i / samples)).atY(r.floor));
        boolean prepared = false;
        for (BlockPos center : centers) {
            // A new stair's first cell must wait until the rider's feet have cleared its top.
            if (new AABB(center).intersects(r.player.getBoundingBox())) continue;
            if (!platform(r, Vec3.atCenterOf(center), r.floor)) return false;
            prepared = true;
        }
        return prepared;
    }
    public static void tick(ServerPlayer p) {
        Ride r = RIDES.get(p.getUUID());
        if (r == null) return;
        if (r.level != p.level() || !canStart(p)) { stop(p, true); return; }
        r.age++;
        if (r.level.getGameTime() - r.inputAt > 10) { r.forward = r.strafe = 0; r.rise = r.sprint = false; r.brake = true; }
        int level = PlayerProgressionService.getLevel(p);
        r.speedStep = Math.min(r.speedStep, Math.clamp(level - 2, 1, 3));
        boolean steady = SkillTreeService.has(p, "cryokinesis_1_0");
        Vec3 desiredDirection = horizontal(p).add(Vec3.directionFromRotation(0, p.getYRot() - 90).scale(r.strafe * .65));
        if (desiredDirection.lengthSqr() > .001) r.direction = r.direction.lerp(desiredDirection.normalize(), steady ? .24 : .16).normalize();
        double throttle = Math.clamp(r.forward, 0, 1);
        double speed = (.35 + r.speedStep * .25) * throttle * (r.sprint ? 1.18 : 1);
        if (r.forward < 0 || r.brake) speed = 0;
        double travel = p.position().distanceTo(r.previous);
        r.previous = p.position();
        if (r.age % 20 == 0) {
            int drain = speed > .05 ? 8 : 4;
            if (!PlayerEnergyService.tryConsume(p, drain)) { stop(p, true); return; }
            MasteryService.awardEnergySpent(p, drain);
        }
        BlockPos actual = ground(r.level, p.position(), 8, r.foundations);
        if (actual != null) { r.groundTop = actual.getY() + 1; r.unsupported = 0; }
        else if (travel > .04) r.unsupported += Math.min(2, travel);
        if (r.unsupported > 6 || p.getY() > r.groundTop + 6.25) { stop(p, true); return; }
        boolean rising = r.rise && level >= 4 && speed > .05;
        if (r.age % 8 == 0) {
            if (rising && p.getY() >= r.floor + .99 && r.floor + 2 <= r.groundTop + 6) r.floor++;
            if (r.brake && r.floor + 1 > r.groundTop) r.floor--;
        }
        if (actual != null) r.floor = Math.max(r.floor, actual.getY());
        Vec3 ahead = p.position().add(r.direction.scale(Math.max(.8, speed * 2)));
        if (!ribbon(r, p.position(), ahead)) { stop(p, true); return; }
        double vertical = Math.clamp((r.floor + 1.02 - p.getY()) * .45, -.3, .32);
        double forwardSpeed = p.getY() < r.floor + .92 ? Math.min(.12, speed) : speed;
        Vec3 desired = r.direction.scale(forwardSpeed).add(0, vertical, 0);
        Vec3 velocity = p.getDeltaMovement().lerp(desired, r.brake || throttle == 0 ? steady ? .5 : .35 : .22);
        if (!r.level.noCollision(p, p.getBoundingBox().move(velocity.multiply(1, 0, 1)))) {
            if (p.getY() < r.floor + 1 && vertical > 0
                    && r.level.noCollision(p, p.getBoundingBox().move(0, vertical, 0))) velocity = new Vec3(0, vertical, 0);
            else { stop(p, true); return; }
        }
        p.setDeltaMovement(velocity); p.hurtMarked = true; p.resetFallDistance();
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        if (r.age % 3 == 0) {
            AbilityEffects.send(r.level, Kind.CRYO_RIDE, p.position(), p.position().add(velocity), 1);
            if (r.effectPoint.distanceToSqr(p.position()) > .04) {
                AbilityEffects.send(r.level, Kind.CRYO_RIBBON, r.effectPoint.add(0, -.02, 0), p.position().add(0, -.02, 0),
                        SkillTreeService.has(p, "cryokinesis_double_weave") ? 3 : 1);
                r.effectPoint = p.position();
            }
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
