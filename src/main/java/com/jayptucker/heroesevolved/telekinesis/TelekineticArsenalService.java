package com.jayptucker.heroesevolved.telekinesis;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Transient, visible fragments with server-side swept collision. Never removes or drops blocks. */
public final class TelekineticArsenalService {
    private static final int OWNER_LIMIT = 64;
    private static final Map<UUID, Arsenal> ARSENALS = new HashMap<>();
    private TelekineticArsenalService() {}

    public static int orbitCount(ServerPlayer player) {
        Arsenal arsenal = ARSENALS.get(player.getUUID());
        return arsenal == null ? 0 : (int) arsenal.shards.stream().filter(s -> !s.flying).count();
    }

    public static int flyingCount(ServerPlayer player) {
        Arsenal arsenal = ARSENALS.get(player.getUUID());
        return arsenal == null ? 0 : (int) arsenal.shards.stream().filter(s -> s.flying).count();
    }

    public static void salvage(ServerPlayer player) {
        if (TelekinesisSkillService.has(player, "1_0")) gather(player, 1);
    }

    public static void gather(ServerPlayer player, int count) {
        if (!TelekinesisSkillService.eligible(player)) return;
        Arsenal arsenal = arsenal(player);
        if (arsenal == null) return;
        int capacity = TelekinesisSkillService.has(player, "five_fragments") ? 5 : 3;
        for (int i = 0; i < count && orbitCount(player) < capacity && arsenal.shards.size() < 12; i++) {
            int slot = freeSlot(arsenal);
            Vec3 at = player.position().add(side(player).scale((slot % 2 == 0 ? 1 : -1) * .8)).add(0, .3, 0);
            Shard shard = new Shard(player.serverLevel(), at, slot, .28F);
            arsenal.shards.add(shard);
            AbilityEffects.send(player.serverLevel(), Kind.TK_GATHER, at, orbitPoint(player, slot, 0), .7);
        }
        ArmPosePayload.show(player, ArmPose.KINETIC_GATHER, 12);
    }

    /** A push flicks the oldest available fragment down the crosshair. */
    public static boolean launchOne(ServerPlayer player) {
        return launchAt(player, TelekinesisSkillService.aimPoint(player, 40), null);
    }

    public static boolean launchAt(ServerPlayer player, Vec3 point, LivingEntity excluded) {
        Arsenal arsenal = ARSENALS.get(player.getUUID());
        if (arsenal == null || !TelekinesisSkillService.eligible(player)) return false;
        for (Shard shard : arsenal.shards) if (!shard.flying && shard.delay < 0) {
            if (excluded != null) shard.hit.add(excluded.getUUID());
            launch(player, shard, point, false);
            return true;
        }
        return false;
    }

    public static boolean lance(ServerPlayer player, Vec3 point) {
        Arsenal arsenal = arsenal(player);
        if (arsenal == null || arsenal.shards.size() >= 12) return false;
        Vec3 at = player.getEyePosition().add(side(player).scale(.65)).add(0, -.25, 0);
        Shard shard = new Shard(player.serverLevel(), at, 0, .42F);
        shard.damage = 8;
        shard.pierces = TelekinesisSkillService.has(player, "0_2") ? 2 : 1;
        shard.speed = 2.3;
        arsenal.shards.add(shard);
        launch(player, shard, point, false);
        return true;
    }

    public static boolean volley(ServerPlayer player, Vec3 point, boolean arcing) {
        Arsenal arsenal = arsenal(player);
        if (arsenal == null || arsenal.shards.size() > 7) return false;
        int count = TelekinesisSkillService.has(player, "five_fragments") ? 5 : 3;
        gather(player, count - orbitCount(player));
        int index = 0;
        for (Shard shard : arsenal.shards) if (!shard.flying) {
            shard.aim = point;
            shard.delay = 6 + index++ * 11;
            shard.arcing = arcing && TelekinesisSkillService.has(player, "iron_rain");
        }
        return index > 0;
    }

    private static void launch(ServerPlayer player, Shard shard, Vec3 point, boolean arcing) {
        shard.flying = true;
        shard.flightAge = 0;
        shard.delay = -1;
        shard.arcing = arcing;
        shard.remainingDistance = arcing ? 48 : shard.damage > 4 ? 40 : 32;
        Vec3 delta = point.subtract(shard.position);
        // A real ballistic path. A ceiling can stop Iron Rain before it reaches its destination.
        shard.velocity = arcing ? delta.scale(1.0 / 22).add(0, .045 * 21 / 2, 0)
                : delta.normalize().scale(shard.speed);
        AbilityEffects.send(player.serverLevel(), Kind.TK_LAUNCH, shard.position,
                shard.position.add(shard.velocity.normalize().scale(3)), shard.damage > 4 ? 1.8 : .8);
        ArmPosePayload.show(player, ArmPose.KINETIC_RELEASE, 8);
        player.serverLevel().playSound(null, BlockPos.containing(shard.position), SoundEvents.BREEZE_SHOOT,
                SoundSource.PLAYERS, .45F, 1.35F);
    }

    public static void tick(MinecraftServer server) {
        for (Arsenal arsenal : List.copyOf(ARSENALS.values())) {
            ServerPlayer player = arsenal.owner;
            if (!TelekinesisSkillService.eligible(player) || player.serverLevel() != arsenal.level) {
                forget(player);
                continue;
            }
            arsenal.age++;
            for (Shard shard : List.copyOf(arsenal.shards)) {
                if (shard.display.isRemoved() || ++shard.age > (shard.flying ? 260 : 180)) {
                    remove(arsenal, shard);
                    continue;
                }
                if (shard.flying) advance(arsenal, shard);
                else {
                    Vec3 goal = orbitPoint(player, shard.slot, arsenal.age);
                    shard.position = shard.position.lerp(goal, .25);
                    if (shard.age % 6 == 0) AbilityEffects.send(arsenal.level, Kind.TK_ORBIT,
                            player.position().add(0, 1, 0), shard.position, .6);
                    if (shard.delay >= 0 && shard.delay-- == 0) launch(player, shard, shard.aim, shard.arcing);
                }
                if (!shard.display.isRemoved()) {
                    shard.display.setPos(shard.position);
                    shard.display.setYRot(shard.age * 7);
                    shard.display.setXRot(shard.age * 3);
                }
            }
            if (arsenal.shards.isEmpty()) ARSENALS.remove(player.getUUID());
        }
    }

    private static void advance(Arsenal arsenal, Shard shard) {
        if (++shard.flightAge > 65 || !arsenal.level.hasChunkAt(BlockPos.containing(shard.position))) {
            remove(arsenal, shard); return;
        }
        Vec3 start = shard.position;
        Vec3 step = shard.velocity.length() > shard.remainingDistance
                ? shard.velocity.normalize().scale(shard.remainingDistance) : shard.velocity;
        Vec3 end = start.add(step);
        if (!arsenal.level.hasChunkAt(BlockPos.containing(end))) { remove(arsenal, shard); return; }
        var block = arsenal.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, arsenal.owner));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        // A swept segment prevents fast fragments tunnelling through either creatures or walls.
        var targets = arsenal.level.getEntitiesOfClass(LivingEntity.class, new AABB(start, stop).inflate(.4),
                        t -> TelekinesisSkillService.hostile(arsenal.owner, t) && !shard.hit.contains(t.getUUID()))
                .stream().filter(t -> t.getBoundingBox().inflate(.2).clip(start, stop).isPresent()
                        || t.getBoundingBox().inflate(.2).contains(start))
                .sorted(Comparator.comparingDouble(t -> t.position().distanceToSqr(start))).limit(8).toList();
        for (LivingEntity target : targets) {
            Vec3 contact = target.getBoundingBox().inflate(.2).clip(start, stop).orElse(start);
            shard.hit.add(target.getUUID());
            if (target.hurt(arsenal.owner.damageSources().playerAttack(arsenal.owner), shard.damage)) {
                TelekinesisSkillService.move(target, target.getDeltaMovement().add(shard.velocity.normalize().scale(.3)));
            }
            impact(arsenal, shard, contact, shard.velocity.reverse().normalize());
            if (--shard.pierces <= 0) { remove(arsenal, shard); return; }
        }
        if (block.getType() != HitResult.Type.MISS) {
            impact(arsenal, shard, stop, Vec3.atLowerCornerOf(block.getDirection().getNormal()));
            remove(arsenal, shard); return;
        }
        shard.position = end;
        shard.remainingDistance -= step.length();
        if (shard.remainingDistance <= .001) { remove(arsenal, shard); return; }
        if (shard.arcing) shard.velocity = shard.velocity.add(0, -.045, 0);
    }

    private static void impact(Arsenal arsenal, Shard shard, Vec3 at, Vec3 normal) {
        AbilityEffects.send(arsenal.level, Kind.TK_IMPACT, at, at.add(normal), shard.arcing ? 2 : .9);
        arsenal.level.playSound(null, BlockPos.containing(at), SoundEvents.STONE_BREAK,
                SoundSource.PLAYERS, .5F, .8F);
        if (shard.arcing) {
            // Iron Rain adds a small landing pressure wave, never terrain damage or friendly fire.
            for (LivingEntity target : TelekinesisSkillService.enemies(arsenal.owner, at, 2)) {
                if (shard.hit.contains(target.getUUID())) continue;
                target.hurt(arsenal.owner.damageSources().playerAttack(arsenal.owner), 2);
                TelekinesisSkillService.move(target, target.position().subtract(at).normalize().scale(.45).add(0, .18, 0));
            }
        }
    }

    /** Both orbit rows sit beside/behind the eye line; looking up never swings them into the camera. */
    public static Vec3 orbitPoint(ServerPlayer player, int slot, int age) {
        double sign = slot % 2 == 0 ? 1 : -1;
        Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
        return player.position().add(side(player).scale(sign * (1.25 + .22 * (slot / 2))))
                .add(forward.scale(-.35 - .22 * (slot / 2)))
                .add(0, 1.2 + .35 * (slot / 2) + .12 * Math.sin(age * .09 + slot), 0);
    }

    private static Vec3 side(ServerPlayer player) {
        Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
        return new Vec3(forward.z, 0, -forward.x);
    }

    private static int freeSlot(Arsenal arsenal) {
        for (int slot = 0; slot < 5; slot++) {
            int candidate = slot;
            if (arsenal.shards.stream().noneMatch(s -> !s.flying && s.slot == candidate)) return slot;
        }
        return 0;
    }

    private static Arsenal arsenal(ServerPlayer player) {
        if (!TelekinesisSkillService.eligible(player)) return null;
        Arsenal existing = ARSENALS.get(player.getUUID());
        if (existing != null && existing.level != player.serverLevel()) { forget(player); existing = null; }
        if (existing != null) return existing;
        if (ARSENALS.size() >= OWNER_LIMIT) return null;
        Arsenal created = new Arsenal(player);
        ARSENALS.put(player.getUUID(), created);
        return created;
    }

    private static void remove(Arsenal arsenal, Shard shard) {
        shard.display.discard();
        arsenal.shards.remove(shard);
    }

    public static void forget(ServerPlayer player) {
        Arsenal arsenal = ARSENALS.remove(player.getUUID());
        if (arsenal != null) for (Shard shard : arsenal.shards) shard.display.discard();
    }

    public static void clear() {
        for (Arsenal arsenal : ARSENALS.values()) for (Shard shard : arsenal.shards) shard.display.discard();
        ARSENALS.clear();
    }

    private static final class Arsenal {
        final ServerPlayer owner;
        final ServerLevel level;
        final List<Shard> shards = new ArrayList<>();
        int age;
        Arsenal(ServerPlayer owner) { this.owner = owner; level = owner.serverLevel(); }
    }

    private static final class Shard {
        final DebrisDisplay display;
        final int slot;
        final Set<UUID> hit = new HashSet<>();
        Vec3 position, velocity = Vec3.ZERO, aim;
        boolean flying, arcing;
        int age, flightAge, delay = -1, pierces = 1;
        float damage = 4;
        double speed = 1.65;
        double remainingDistance;
        Shard(ServerLevel level, Vec3 at, int slot, float size) {
            this.slot = slot; position = at;
            display = new DebrisDisplay(level, size);
            display.setPos(at);
            level.addFreshEntity(display);
        }
    }

    /** Vanilla display protocol/renderer, but no save data or loot can outlive the owning session. */
    private static final class DebrisDisplay extends Display.BlockDisplay {
        DebrisDisplay(ServerLevel level, float size) {
            super(EntityType.BLOCK_DISPLAY, level);
            CompoundTag tag = new CompoundTag();
            tag.put("block_state", NbtUtils.writeBlockState(Blocks.ANDESITE.defaultBlockState()));
            tag.putInt("teleport_duration", 2);
            tag.putFloat("view_range", .6F);
            CompoundTag transform = new CompoundTag();
            ListTag scale = new ListTag(), translation = new ListTag();
            for (int i = 0; i < 3; i++) { scale.add(FloatTag.valueOf(size)); translation.add(FloatTag.valueOf(-size / 2)); }
            transform.put("scale", scale); transform.put("translation", translation);
            ListTag rotation = new ListTag();
            for (float value : new float[]{0, 0, 0, 1}) rotation.add(FloatTag.valueOf(value));
            transform.put("left_rotation", rotation); transform.put("right_rotation", rotation.copy());
            tag.put("transformation", transform);
            readAdditionalSaveData(tag);
        }
        @Override public boolean shouldBeSaved() { return false; }
    }
}
