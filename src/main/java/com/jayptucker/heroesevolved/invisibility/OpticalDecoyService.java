package com.jayptucker.heroesevolved.invisibility;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.network.OpticalDecoyPayload;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

/** Public optical doubles: server-owned paths and counterplay, with no inventories or living entities. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class OpticalDecoyService {
    private static final int MAXIMUM_PER_OWNER = 3, MAXIMUM_TOTAL = 96;
    private static final double VIEW_RANGE_SQUARED = 80 * 80;
    private static final Map<Integer, Double> DOUBLES = new LinkedHashMap<>();
    private static final Map<UUID, Investigation> INVESTIGATIONS = new HashMap<>();
    private static int nextId = 1;
    private OpticalDecoyService() {}

    public record Snapshot(int id, Vec3 position, int remaining) {}
    private record Investigation(ServerLevel level, int decoy, long until) {}
    private static final class Double {
        final int id;
        final ServerPlayer owner;
        final ServerLevel level;
        final Vec3 direction;
        final Vec3 origin;
        final long created, until;
        final Set<UUID> viewers = new HashSet<>();
        Vec3 position, velocity = Vec3.ZERO;
        float yaw;
        boolean stopped;
        Double(int id, ServerPlayer owner, Vec3 direction, int ticks) {
            this.id = id; this.owner = owner; this.level = owner.serverLevel();
            this.position = this.origin = owner.position(); this.direction = direction;
            this.created = level.getGameTime(); this.until = created + ticks;
            this.yaw = direction.horizontalDistanceSqr() < .001 ? owner.getYRot()
                    : (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        }
        AABB bounds() { return new AABB(position.x - .33, position.y, position.z - .33,
                position.x + .33, position.y + 1.85, position.z + .33); }
    }

    public static int count(ServerPlayer player) {
        return (int) DOUBLES.values().stream().filter(d -> d.owner == player).count();
    }
    public static List<Snapshot> snapshots(ServerPlayer player) {
        return DOUBLES.values().stream().filter(d -> d.owner == player)
                .map(d -> new Snapshot(d.id, d.position, (int) Math.max(0, d.until - d.level.getGameTime()))).toList();
    }
    public static boolean isInvestigating(Mob mob) {
        Investigation search = INVESTIGATIONS.get(mob.getUUID());
        return search != null && search.level == mob.level() && search.until >= mob.level().getGameTime()
                && DOUBLES.containsKey(search.decoy);
    }
    public static boolean spawnMirage(ServerPlayer player, int level) {
        int ticks = 120 + (SkillTreeService.has(player, "invisibility_1_0") ? 20 : 0);
        return spawnEcho(player, player.getLookAngle(), ticks);
    }
    public static boolean burst(ServerPlayer player, int requested) {
        if (!PowerRules.canAct(player)) return false;
        boolean spawned = false;
        int count = Math.clamp(requested, 1, MAXIMUM_PER_OWNER);
        for (int i = 0; i < count; i++) {
            float offset = count == 1 ? 0 : -65 + i * 130F / (count - 1);
            spawned |= spawnEcho(player, Vec3.directionFromRotation(0, player.getYRot() + offset), 100);
        }
        return spawned;
    }
    public static boolean spawnEcho(ServerPlayer player, Vec3 intendedDirection, int ticks) {
        if (!PowerRules.canAct(player) || intendedDirection == null || !finite(intendedDirection)) return false;
        Vec3 horizontal = intendedDirection.multiply(1, 0, 1);
        Vec3 direction = horizontal.lengthSqr() < .001
                ? Vec3.directionFromRotation(0, player.getYRot()) : horizontal.normalize();
        while (count(player) >= MAXIMUM_PER_OWNER) {
            Double oldest = DOUBLES.values().stream().filter(d -> d.owner == player).findFirst().orElse(null);
            if (oldest == null) break;
            remove(oldest, true);
        }
        if (DOUBLES.size() >= MAXIMUM_TOTAL) remove(DOUBLES.values().iterator().next(), true);
        Double decoy = new Double(nextId++, player, direction, Math.clamp(ticks, 10, 160));
        DOUBLES.put(decoy.id, decoy);
        broadcast(decoy);
        return true;
    }

    /** A player must actually reach and see the double; client IDs never grant arbitrary deletion. */
    public static boolean dissolve(ServerPlayer player, int id) {
        Double decoy = DOUBLES.get(id);
        if (decoy == null || player.serverLevel() != decoy.level || !PowerRules.canAct(player)) return false;
        Vec3 eye = player.getEyePosition(), end = eye.add(player.getLookAngle().scale(5));
        var contact = decoy.bounds().inflate(.15).clip(eye, end);
        if (contact.isEmpty() && !decoy.bounds().contains(eye)) return false;
        Vec3 point = contact.orElse(eye);
        if (decoy.level.clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) return false;
        remove(decoy, true);
        return true;
    }

    private static boolean finite(Vec3 point) { return java.lang.Double.isFinite(point.x)
            && java.lang.Double.isFinite(point.y) && java.lang.Double.isFinite(point.z); }
    private static OpticalDecoyPayload packet(Double d, boolean removed) {
        boolean mimic = SkillTreeService.has(d.owner, "invisibility_1_2") && d.owner.swinging;
        return new OpticalDecoyPayload(d.id, removed ? -1 : 0, d.owner.getId(), d.owner.getUUID(),
                d.owner.getGameProfile().getName(), d.position, d.velocity, d.yaw,
                (int) Math.max(0, d.until - d.level.getGameTime()), mimic);
    }
    private static void broadcast(Double d) {
        OpticalDecoyPayload payload = packet(d, false);
        for (ServerPlayer viewer : d.level.players()) {
            if (viewer.position().distanceToSqr(d.position) > VIEW_RANGE_SQUARED) continue;
            d.viewers.add(viewer.getUUID());
            PacketDistributor.sendToPlayer(viewer, payload);
        }
    }
    private static void remove(Double d, boolean shatter) {
        if (DOUBLES.remove(d.id) == null) return;
        OpticalDecoyPayload payload = packet(d, true);
        for (ServerPlayer viewer : d.level.players()) {
            if (d.viewers.contains(viewer.getUUID()) || viewer.position().distanceToSqr(d.position) <= VIEW_RANGE_SQUARED)
                PacketDistributor.sendToPlayer(viewer, payload);
        }
        INVESTIGATIONS.values().removeIf(search -> search.decoy == d.id);
    }

    private static void move(Double d) {
        d.velocity = Vec3.ZERO;
        if (d.stopped) return;
        Vec3 next = d.position.add(d.direction.scale(.2));
        if (next.distanceToSqr(d.origin) > 12 * 12 || !d.level.hasChunkAt(BlockPos.containing(next))
                || !d.level.getWorldBorder().isWithinBounds(BlockPos.containing(next))) { d.stopped = true; return; }
        var ground = d.level.clip(new ClipContext(next.add(0, .6, 0), next.add(0, -1.2, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, d.owner));
        if (ground.getType() != HitResult.Type.BLOCK || ground.getDirection() != net.minecraft.core.Direction.UP) {
            d.stopped = true; return;
        }
        next = new Vec3(next.x, ground.getLocation().y, next.z);
        if (Math.abs(next.y - d.position.y) > .65) { d.stopped = true; return; }
        AABB bounds = new AABB(next.x - .3, next.y + .01, next.z - .3, next.x + .3, next.y + 1.8, next.z + .3);
        if (d.level.getBlockCollisions(d.owner, bounds).iterator().hasNext()) { d.stopped = true; return; }
        d.velocity = next.subtract(d.position);
        d.position = next;
    }

    private static boolean hitByProjectile(Double d) {
        for (Projectile shot : d.level.getEntitiesOfClass(Projectile.class, d.bounds().inflate(2),
                p -> !p.isRemoved() && p.getDeltaMovement().lengthSqr() > .001).stream().limit(32).toList()) {
            Vec3 previous = new Vec3(shot.xo, shot.yo, shot.zo);
            if (d.bounds().inflate(.1).contains(shot.position())
                    || d.bounds().inflate(.1).clip(previous, shot.position()).isPresent()) return true;
        }
        return false;
    }
    private static void investigate(Double d, long now) {
        for (Mob mob : d.level.getEntitiesOfClass(Mob.class, d.bounds().inflate(16),
                m -> m instanceof Enemy && m.isAlive() && !m.isAlliedTo(d.owner)).stream().limit(16).toList()) {
            if (mob.getTarget() != null && (mob.getTarget() != d.owner || !InvisibilityService.isUnseen(d.owner))) continue;
            if (d.level.clip(new ClipContext(mob.getEyePosition(), d.position.add(0, 1.1, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getType() != HitResult.Type.MISS) continue;
            double distance = mob.position().distanceToSqr(d.position);
            boolean closer = DOUBLES.values().stream().anyMatch(other -> other != d && other.level == d.level
                    && mob.position().distanceToSqr(other.position) < distance);
            if (closer) continue;
            // Null is a legitimate target change: really release aggro, rather than merely refusing a new target.
            if (mob.getTarget() == d.owner) mob.setTarget(null);
            mob.getNavigation().moveTo(d.position.x, d.position.y, d.position.z, 1.05);
            mob.getLookControl().setLookAt(d.position.x, d.position.y + 1.3, d.position.z, 30, 30);
            INVESTIGATIONS.put(mob.getUUID(), new Investigation(d.level, d.id, now + 6));
        }
    }

    public static void tick(MinecraftServer server) {
        for (Double d : List.copyOf(DOUBLES.values())) {
            long now = d.level.getGameTime();
            if (now >= d.until || !d.owner.isAlive() || d.owner.isRemoved() || d.owner.level() != d.level
                    || !PowerRules.canAct(d.owner) || hitByProjectile(d)) { remove(d, true); continue; }
            move(d);
            if (now % 4 == 0) broadcast(d);
            if (now % 5 == 0) investigate(d, now);
        }
        INVESTIGATIONS.values().removeIf(search -> search.until < search.level.getGameTime()
                || !DOUBLES.containsKey(search.decoy));
    }
    public static void clear(ServerPlayer player) {
        for (Double d : List.copyOf(DOUBLES.values())) if (d.owner == player) remove(d, true);
    }
    public static void clearAll() {
        for (Double d : List.copyOf(DOUBLES.values())) remove(d, true);
        INVESTIGATIONS.clear();
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event) { clearAll(); }
}
