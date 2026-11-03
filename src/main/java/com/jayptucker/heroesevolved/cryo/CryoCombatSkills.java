package com.jayptucker.heroesevolved.cryo;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Forged projectiles, finite ice plates and a travelling cold front. All collision is server-owned. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class CryoCombatSkills {
    private static final int MAX_SHOTS = 128, MAX_FORGES = 32, MAX_SNAPS = 32, MAX_GUARDS = 32;
    private record Forge(ServerPlayer owner, ServerLevel level, int tier, long began) {}
    private static final class Shot {
        final ServerPlayer owner;
        final ServerLevel level;
        final Vec3 direction;
        final boolean lance;
        final float damage;
        Vec3 position;
        double remaining;
        Shot(ServerPlayer owner, Vec3 origin, Vec3 direction, boolean lance, float damage, double range) {
            this.owner = owner; level = owner.serverLevel(); position = origin;
            this.direction = direction.normalize(); this.lance = lance; this.damage = damage; remaining = range;
        }
    }
    private static final class Guard {
        final ServerPlayer owner;
        final ServerLevel level;
        final long until;
        int plates = 6;
        float stored;
        Vec3 last;
        Guard(ServerPlayer p) {
            owner = p; level = p.serverLevel(); until = level.getGameTime() + 160; last = p.position();
        }
    }
    private static final class Snap {
        final ServerPlayer owner;
        final ServerLevel level;
        final Vec3 center;
        final long began;
        final Set<UUID> hit = new HashSet<>();
        double radius;
        Snap(ServerPlayer p) { owner = p; level = p.serverLevel(); center = p.position(); began = level.getGameTime(); }
    }
    private static final Map<UUID, Forge> FORGES = new HashMap<>();
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static final Map<UUID, Guard> GUARDS = new HashMap<>();
    private static final Map<UUID, Snap> SNAPS = new HashMap<>();

    private CryoCombatSkills() {}
    private static boolean valid(ServerPlayer p, ServerLevel level) {
        return p.level() == level && !p.isRemoved() && !p.isPassenger()
                && PowerRules.canAct(p) && CryoKitService.authorized(p);
    }
    public static boolean isForging(ServerPlayer p) { return FORGES.containsKey(p.getUUID()); }
    /** Includes a still-forming lance as well as physical projectiles already in flight. */
    public static int lanceCount(ServerPlayer p) {
        return (isForging(p) ? 1 : 0) + (int) SHOTS.stream().filter(s -> s.owner == p && s.lance).count();
    }
    public static int activeShots(ServerPlayer p) { return (int) SHOTS.stream().filter(s -> s.owner == p).count(); }
    public static int armorPlates(ServerPlayer p) { Guard g = GUARDS.get(p.getUUID()); return g == null ? 0 : g.plates; }
    public static float storedCold(ServerPlayer p) { Guard g = GUARDS.get(p.getUUID()); return g == null ? 0 : g.stored; }
    public static int snapCount(ServerPlayer p) { return SNAPS.containsKey(p.getUUID()) ? 1 : 0; }

    public static boolean canUse(AbilityUseContext c, int branch) {
        ServerPlayer p = c.player();
        if (!valid(p, p.serverLevel())) return false;
        return switch (branch) {
            case 0 -> !isForging(p) && FORGES.size() < MAX_FORGES && SHOTS.size() < MAX_SHOTS - 6 && activeShots(p) < 8;
            case 1 -> !GUARDS.containsKey(p.getUUID()) && GUARDS.size() < MAX_GUARDS;
            case 2 -> !SNAPS.containsKey(p.getUUID()) && SNAPS.size() < MAX_SNAPS;
            default -> false;
        };
    }
    public static boolean activate(AbilityUseContext c, int branch) {
        if (!canUse(c, branch)) return false;
        ServerPlayer p = c.player();
        switch (branch) {
            case 0 -> {
                Forge forge = new Forge(p, p.serverLevel(), Math.clamp(c.abilityLevel(), 1, 5), p.level().getGameTime());
                FORGES.put(p.getUUID(), forge);
                ArmPosePayload.show(p, ArmPose.CRYO_LANCE, 24);
                forgeEffect(forge, .05F);
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, .8F, .65F);
            }
            case 1 -> {
                Guard guard = new Guard(p); GUARDS.put(p.getUUID(), guard); armorEffect(guard);
                ArmPosePayload.show(p, ArmPose.CRYO_GUARD, 20);
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1, .65F);
            }
            case 2 -> {
                Snap snap = new Snap(p); SNAPS.put(p.getUUID(), snap);
                AbilityEffects.send(snap.level, Kind.CRYO_SNAP, snap.center, snap.center.add(0, 1, 0), .15);
                ArmPosePayload.show(p, ArmPose.CRYO_SNAP, 18);
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 1.3F, .55F);
            }
            default -> { return false; }
        }
        return true;
    }
    /** A deliberate second armor press spends the remaining plates; cleanup never calls this. */
    public static boolean followUp(AbilityUseContext c, int branch) {
        if (branch != 1) return false;
        Guard guard = GUARDS.get(c.player().getUUID());
        if (guard == null) return false;
        if (guard.owner != c.player() || !valid(guard.owner, guard.level)) { removeGuard(guard); return true; }
        int count = guard.plates;
        Vec3 origin = palm(guard.owner), aim = guard.owner.getLookAngle();
        removeGuard(guard);
        for (int i = 0; i < count && SHOTS.size() < MAX_SHOTS; i++) {
            double angle = count <= 1 ? 0 : (i / (double)(count - 1) - .5) * .6;
            SHOTS.add(new Shot(guard.owner, origin, aim.yRot((float) angle), false, 3 + guard.stored / 8, 10));
        }
        AbilityEffects.send(guard.level, Kind.CRYO_REBOUND, origin, origin.add(aim.scale(10)), count);
        ArmPosePayload.show(guard.owner, ArmPose.CRYO_SHATTER, 14);
        guard.level.playSound(null, guard.owner.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.1F, 1.4F);
        return true;
    }
    private static Vec3 palm(ServerPlayer p) { return p.getEyePosition().add(p.getLookAngle().scale(.6)).add(0, -.35, 0); }
    private static void forgeEffect(Forge forge, float progress) {
        Vec3 origin = palm(forge.owner);
        AbilityEffects.send(forge.level, Kind.CRYO_LANCE_FORGE, origin, origin.add(forge.owner.getLookAngle().scale(1.6)), progress);
    }
    private static void armorEffect(Guard guard) {
        guard.last = guard.owner.position();
        AbilityEffects.send(guard.level, Kind.CRYO_ARMOR, guard.last, guard.owner.getEyePosition(), guard.plates);
    }
    private static void removeGuard(Guard guard) {
        GUARDS.remove(guard.owner.getUUID(), guard);
        AbilityEffects.send(guard.level, Kind.CRYO_ARMOR, guard.last, guard.last.add(0, guard.owner.getBbHeight(), 0), 0);
    }
    private static boolean hostileProjectile(Guard g, Projectile shot) {
        if (shot.getOwner() == g.owner) return false;
        return !(shot.getOwner() instanceof LivingEntity source) || ExpansionSkills.hostile(g.owner, source);
    }
    private static boolean visible(ServerLevel level, Vec3 origin, Vec3 target, LivingEntity actor) {
        return level.clip(new ClipContext(origin, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor)).getType() == HitResult.Type.MISS;
    }
    private static void consumePlate(Guard guard, Vec3 impact, float caught, boolean fire) {
        guard.plates = Math.max(0, guard.plates - (fire ? 2 : 1));
        guard.stored = Math.min(12, guard.stored + caught);
        AbilityEffects.send(guard.level, Kind.CRYO_FRACTURE, impact, impact.add(0, .4, 0), .65);
        guard.level.playSound(null, BlockPos.containing(impact), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, .55F, 1.5F);
        if (guard.plates == 0) removeGuard(guard); else armorEffect(guard);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void incomingDamage(LivingIncomingDamageEvent event) {
        Guard guard = GUARDS.get(event.getEntity().getUUID());
        if (guard == null || event.getAmount() <= 0 || !valid(guard.owner, guard.level)
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || event.getSource().getEntity() == guard.owner) return;
        boolean fire = event.getSource().is(DamageTypeTags.IS_FIRE);
        if (!fire && event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)) return;
        if (event.getSource().getDirectEntity() instanceof Projectile shot) {
            if (!hostileProjectile(guard, shot)) return;
            consumePlate(guard, shot.position(), 2, false); shot.discard(); event.setAmount(0);
        } else {
            float absorbed = Math.min(fire ? 3 : 6, event.getAmount() * (fire ? .2F : .6F));
            consumePlate(guard, guard.owner.position().add(0, 1, 0), absorbed, fire);
            event.setAmount(event.getAmount() - absorbed);
        }
    }

    public static void tick(MinecraftServer server) {
        for (Forge forge : List.copyOf(FORGES.values())) {
            if (forge.level.getServer() != server) continue;
            if (!valid(forge.owner, forge.level)) { FORGES.remove(forge.owner.getUUID()); continue; }
            long age = forge.level.getGameTime() - forge.began;
            if (age < 12) { if (age % 2 == 0) forgeEffect(forge, (float)age / 12); continue; }
            FORGES.remove(forge.owner.getUUID());
            if (SHOTS.size() < MAX_SHOTS) SHOTS.add(new Shot(forge.owner, palm(forge.owner), forge.owner.getLookAngle(), true, 6 + forge.tier, 40));
            forge.level.playSound(null, forge.owner.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, .9F, 1.5F);
        }
        for (Shot shot : List.copyOf(SHOTS)) {
            if (shot.level.getServer() != server) continue;
            if (!valid(shot.owner, shot.level) || shot.remaining <= 0) { SHOTS.remove(shot); continue; }
            step(shot);
        }
        for (Guard guard : List.copyOf(GUARDS.values())) {
            if (guard.level.getServer() != server) continue;
            if (!valid(guard.owner, guard.level) || guard.level.getGameTime() >= guard.until) { removeGuard(guard); continue; }
            if (guard.level.getGameTime() % 4 == 0) armorEffect(guard);
            for (Projectile shot : guard.level.getEntitiesOfClass(Projectile.class, guard.owner.getBoundingBox().inflate(1.8),
                    p -> !p.isRemoved() && hostileProjectile(guard, p)).stream().limit(8).toList()) {
                if (guard.plates == 0) break;
                if (!visible(guard.level, guard.owner.getEyePosition(), shot.position(), guard.owner)) continue;
                consumePlate(guard, shot.position(), 2, false); shot.discard();
            }
        }
        for (Snap snap : List.copyOf(SNAPS.values())) {
            if (snap.level.getServer() != server) continue;
            long age = snap.level.getGameTime() - snap.began;
            if (!valid(snap.owner, snap.level) || age > 12) { SNAPS.remove(snap.owner.getUUID()); continue; }
            double previous = snap.radius; snap.radius = Math.min(7, age * 7.0 / 12);
            if (age <= 0) continue;
            AbilityEffects.send(snap.level, Kind.CRYO_SNAP, snap.center, snap.center.add(0, 1, 0), snap.radius);
            for (LivingEntity target : snap.level.getEntitiesOfClass(LivingEntity.class, new AABB(snap.center, snap.center).inflate(7, 3, 7),
                    t -> ExpansionSkills.hostile(snap.owner, t) && !snap.hit.contains(t.getUUID())).stream().limit(32).toList()) {
                double distance = target.position().subtract(snap.center).horizontalDistance();
                if (distance > snap.radius + .35 || distance < previous - .75
                        || !visible(snap.level, snap.center.add(0, .6, 0), target.getBoundingBox().getCenter(), snap.owner)) continue;
                snap.hit.add(target.getUUID());
                if (CryoService.isFrozen(target)) CryoService.shatterHit(snap.owner, target, 4);
                else {
                    int frost = CryoService.frostAmount(target);
                    CryoService.coldHit(snap.owner, target, 2);
                    CryoService.chill(target, 45, snap.owner);
                    if (frost >= 40) CryoService.freezeSolid(target, target instanceof ServerPlayer ? 40 : 60, snap.owner);
                }
            }
        }
    }
    private static void step(Shot shot) {
        Vec3 from = shot.position;
        double travel = Math.min(shot.remaining, shot.lance ? 2 : 1.4);
        Vec3 to = from.add(shot.direction.scale(travel));
        if (!shot.level.hasChunkAt(BlockPos.containing(from)) || !shot.level.hasChunkAt(BlockPos.containing(to))) { SHOTS.remove(shot); return; }
        var block = shot.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shot.owner));
        Vec3 end = block.getType() == HitResult.Type.BLOCK ? block.getLocation() : to;
        LivingEntity struck = null;
        double nearest = from.distanceToSqr(end) + .00001;
        for (LivingEntity target : shot.level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(.35),
                t -> t != shot.owner && t.isAlive() && !t.isSpectator()).stream().limit(64).toList()) {
            var contact = target.getBoundingBox().inflate(.18).clip(from, end);
            Vec3 point = target.getBoundingBox().inflate(.18).contains(from) ? from : contact.orElse(null);
            if (point != null && from.distanceToSqr(point) < nearest) { nearest = from.distanceToSqr(point); end = point; struck = target; }
        }
        AbilityEffects.send(shot.level, Kind.CRYO_LANCE_FLIGHT, from, end, shot.lance ? 1 : .45);
        shot.position = end; shot.remaining -= travel;
        if (struck != null) {
            if (ExpansionSkills.hostile(shot.owner, struck)) impact(shot, struck);
            SHOTS.remove(shot);
        } else if (block.getType() == HitResult.Type.BLOCK) {
            if (shot.level.getBlockState(block.getBlockPos().above()).isAir()) TemporaryBlocks.frostSurface(shot.level, block.getBlockPos(), 100, shot.owner);
            AbilityEffects.send(shot.level, Kind.CRYO_FRACTURE, end, end.add(0, .5, 0), shot.lance ? 1 : .4);
            SHOTS.remove(shot);
        } else if (shot.remaining <= 0) {
            AbilityEffects.send(shot.level, Kind.CRYO_FRACTURE, end, end.add(0, .4, 0), .4); SHOTS.remove(shot);
        }
    }
    private static void impact(Shot shot, LivingEntity target) {
        boolean frozen = CryoService.isFrozen(target);
        int frost = CryoService.frostAmount(target);
        if (frozen && shot.lance) {
            CryoService.shatterHit(shot.owner, target, shot.damage);
            for (LivingEntity nearby : shot.level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(2),
                    t -> t != target && ExpansionSkills.hostile(shot.owner, t)).stream().limit(8).toList())
                if (visible(shot.level, target.getBoundingBox().getCenter(), nearby.getBoundingBox().getCenter(), shot.owner)) CryoService.chill(nearby, 25, shot.owner);
        } else {
            CryoService.coldHit(shot.owner, target, shot.damage);
            CryoService.chill(target, shot.lance ? 100 : 18, shot.owner);
            if (shot.lance && frost >= 40) CryoService.freezeSolid(target, target instanceof ServerPlayer ? 40 : 60, shot.owner);
        }
        AbilityEffects.send(shot.level, Kind.CRYO_FRACTURE, target.position(), target.getEyePosition(), frozen && shot.lance ? 2 : .8);
        shot.level.playSound(null, target.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, .8F, shot.lance ? .7F : 1.6F);
    }
    public static void forget(ServerPlayer p) {
        boolean posing = FORGES.remove(p.getUUID()) != null || SNAPS.containsKey(p.getUUID()) || GUARDS.containsKey(p.getUUID());
        SHOTS.removeIf(s -> s.owner == p); SNAPS.remove(p.getUUID());
        Guard guard = GUARDS.get(p.getUUID()); if (guard != null) removeGuard(guard);
        if (posing && p.connection != null) ArmPosePayload.clear(p);
    }
    public static void clear() {
        FORGES.clear(); SHOTS.clear(); SNAPS.clear();
        for (Guard guard : List.copyOf(GUARDS.values())) removeGuard(guard);
    }
}
