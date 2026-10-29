package com.jayptucker.heroesevolved.telekinesis;

import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.SimpleAction;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The complete learned kit: six physical techniques and twelve interaction upgrades. */
public final class TelekinesisSkillService {
    private static final Map<UUID, State> STATES = new HashMap<>();
    private TelekinesisSkillService() {}

    public static AbilityAction action(SkillCatalog.Node node) {
        return SimpleAction.builder(node.id(), node.level()).cost(node::energy, node::cooldown)
                .canUse(c -> canUse(c.player(), node.id()))
                .activate(c -> activate(c.player(), node.id(), c.modifierHeld()))
                .deactivateOnPress(c -> hasActiveField(c.player(), node.id()))
                .deactivate(c -> cancel(c.player(), node.id(), !c.modifierHeld())).build();
    }

    public static boolean has(ServerPlayer player, String suffix) {
        return SkillTreeService.has(player, suffix.startsWith("telekinesis_") ? suffix : "telekinesis_" + suffix);
    }

    private static boolean hasActiveField(ServerPlayer player, String id) {
        State state = STATES.get(player.getUUID());
        return state != null && (id.equals("telekinesis_1_1") && state.well != null
                || id.equals("telekinesis_force_bubble") && state.bubble != null);
    }

    public static boolean eligible(ServerPlayer player) {
        return PowerRules.canAct(player) && !player.isRemoved()
                && PlayerAbilityService.getAbility(player, ModAbilities.TELEKINESIS_ID)
                .map(p -> p.isUnlocked()).orElse(false);
    }

    public static boolean canUse(ServerPlayer player, String id) {
        if (!eligible(player) || !has(player, id) || player.isPassenger()) return false;
        if (!STATES.containsKey(player.getUUID()) && STATES.size() >= 64) return false;
        return switch (id) {
            case "telekinesis_0_1", "telekinesis_kinetic_volley", "telekinesis_1_1", "telekinesis_force_bubble" -> true;
            case "telekinesis_remote_hand" -> remoteTarget(player) != null || actionableBlock(player);
            case "telekinesis_2_1" -> !player.isInWaterOrBubble() && !player.isFallFlying();
            default -> false;
        };
    }

    public static boolean activate(ServerPlayer player, String id, boolean modifier) {
        if (!canUse(player, id)) return false;
        switch (id) {
            case "telekinesis_0_1" -> { return TelekineticArsenalService.lance(player, aimPoint(player, 40)); }
            case "telekinesis_kinetic_volley" -> { return TelekineticArsenalService.volley(player, aimPoint(player, 32), modifier); }
            case "telekinesis_remote_hand" -> { return remoteHand(player); }
            case "telekinesis_1_1" -> {
                State state = state(player);
                Vec3 center = aimPoint(player, 16);
                state.well = new Well(center, 100);
                AbilityEffects.send(player.serverLevel(), Kind.TK_WELL, center, center.add(0, 1, 0), 4.5);
                ArmPosePayload.show(player, ArmPose.KINETIC_GATHER, 18);
            }
            case "telekinesis_2_1" -> {
                State state = state(player);
                state.step = 12;
                state.grounding = has(player, "2_0") ? 100 : 0;
                Vec3 heading = player.getLookAngle().multiply(1, 0, 1).normalize();
                // Force acts on the actual player; ordinary movement collision remains authoritative.
                move(player, heading.scale(modifier ? -.65 : .65).add(0, .65, 0));
                player.resetFallDistance();
                AbilityEffects.send(player.serverLevel(), Kind.TK_STEP, player.position(), player.position().add(heading), 1.2);
                ArmPosePayload.show(player, ArmPose.KINETIC_RELEASE, 12);
            }
            case "telekinesis_force_bubble" -> {
                State state = state(player);
                boolean anchored = modifier && has(player, "wider_bubble");
                state.bubble = new Bubble(anchored ? aimPoint(player, 12) : player.position().add(0, 1, 0),
                        anchored, has(player, "bastion") ? 200 : 120);
                AbilityEffects.send(player.serverLevel(), Kind.TK_BARRIER, state.bubble.center,
                        state.bubble.center.add(0, 1, 0), bubbleRadius(player));
                ArmPosePayload.show(player, ArmPose.KINETIC_HOLD, 20);
            }
            default -> { return false; }
        }
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                net.minecraft.sounds.SoundSource.PLAYERS, .6F, .7F);
        return true;
    }

    /** Recasting a field releases its stored positioning. Shift or changing loadout cancels safely. */
    public static boolean cancel(ServerPlayer player, String id, boolean release) {
        State state = STATES.get(player.getUUID());
        if (state == null) return false;
        if (id.equals("telekinesis_1_1") && state.well != null) {
            Vec3 center = state.well.center;
            state.well = null;
            if (release && eligible(player)) {
                for (LivingEntity target : enemies(player, center, 4.5)) {
                    move(target, center.subtract(target.position()).multiply(.12, 0, .12).add(0, .95, 0));
                    target.resetFallDistance();
                }
                AbilityEffects.send(player.serverLevel(), Kind.TK_WAVE, center, center.add(0, 1, 0), 4.5);
            }
            return true;
        }
        if (id.equals("telekinesis_force_bubble") && state.bubble != null) {
            Vec3 center = state.bubble.center;
            state.bubble = null;
            if (release && eligible(player)) {
                for (LivingEntity target : enemies(player, center, bubbleRadius(player) + 1))
                    move(target, target.getBoundingBox().getCenter().subtract(center).normalize().scale(1.25).add(0, .2, 0));
                AbilityEffects.send(player.serverLevel(), Kind.TK_WAVE, center, center.add(0, 1, 0), bubbleRadius(player));
            }
            return true;
        }
        return false;
    }

    public static void onImpact(ServerPlayer player, LivingEntity struck, Vec3 point, float damage, boolean slam) {
        if (!eligible(player) || !has(player, "1_2")) return;
        State state = state(player);
        if (state.crossfire > 0) return;
        for (LivingEntity target : enemies(player, point, 8)) {
            if (target == struck || !player.hasLineOfSight(target)) continue;
            if (TelekineticArsenalService.launchAt(player, target.getBoundingBox().getCenter(), struck)) {
                state.crossfire = 10;
                break;
            }
        }
    }

    public static boolean cushion(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        if (state == null || state.grounding <= 0 || !eligible(player)) return false;
        state.grounding = 0;
        player.resetFallDistance();
        AbilityEffects.send(player.serverLevel(), Kind.TK_STEP, player.position(), player.position().add(0, 1, 0), 1.5);
        return true;
    }

    public static void tick(MinecraftServer server) {
        for (State state : List.copyOf(STATES.values())) {
            ServerPlayer player = state.owner;
            if (!eligible(player) || player.serverLevel() != state.level) { forget(player); continue; }
            state.age++;
            if (state.crossfire > 0) state.crossfire--;
            if (state.grounding > 0) state.grounding--;
            if (state.step > 0) {
                state.step--;
                player.resetFallDistance();
                if (has(player, "2_2")) reflect(player, player.getBoundingBox().getCenter(), 2.5, false);
                if (state.age % 4 == 0) AbilityEffects.send(state.level, Kind.TK_STEP, player.position(),
                        player.position().add(player.getDeltaMovement()), .7);
            }
            if (state.well != null) {
                Well well = state.well;
                if (--well.remaining <= 0 || player.position().distanceToSqr(well.center) > 48 * 48) state.well = null;
                else {
                    for (LivingEntity target : enemies(player, well.center, 4.5)) {
                        if (TelekinesisService.isHeld(target)) continue;
                        Vec3 delta = well.center.subtract(target.getBoundingBox().getCenter());
                        Vec3 pull = delta.lengthSqr() < .04 ? Vec3.ZERO : delta.normalize().scale(Math.min(.32, delta.length() * .14));
                        move(target, target.getDeltaMovement().scale(.65).add(pull));
                        target.resetFallDistance();
                    }
                    if (state.age % 6 == 0) AbilityEffects.send(state.level, Kind.TK_WELL, well.center, well.center.add(0, 1, 0), 4.5);
                }
            }
            if (state.bubble != null) {
                Bubble bubble = state.bubble;
                if (--bubble.remaining <= 0 || player.position().distanceToSqr(bubble.center) > 48 * 48) state.bubble = null;
                else {
                    if (!bubble.anchored) bubble.center = player.position().add(0, 1, 0);
                    double radius = bubbleRadius(player);
                    reflect(player, bubble.center, radius, has(player, "bastion"));
                    for (LivingEntity target : enemies(player, bubble.center, radius + .5)) {
                        Vec3 away = target.getBoundingBox().getCenter().subtract(bubble.center);
                        if (away.lengthSqr() < .01) away = player.getLookAngle();
                        move(target, away.normalize().scale(.4).add(0, .06, 0));
                    }
                    if (state.age % 8 == 0) AbilityEffects.send(state.level, Kind.TK_BARRIER, bubble.center,
                            bubble.center.add(0, 1, 0), radius);
                }
            }
            state.retrieving.removeIf(pull -> !pull.item.isAlive() || pull.item.level() != state.level
                    || pull.item.distanceToSqr(player) > 64 * 64 || state.age >= pull.until);
            for (Pull pull : state.retrieving) {
                ItemEntity item = pull.item;
                Vec3 destination = player.position().add(0, .8, 0);
                Vec3 delta = destination.subtract(item.position());
                Vec3 end = item.position().add(delta.normalize().scale(Math.min(.9, delta.length())));
                if (state.level.clip(new ClipContext(item.position(), end, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) move(item, end.subtract(item.position()));
                else move(item, Vec3.ZERO);
                if (state.age % 8 == 0) AbilityEffects.send(state.level, Kind.TK_GATHER, item.position(), destination, .4);
            }
            if (state.age > 240 && state.well == null && state.bubble == null && state.step <= 0 && state.grounding <= 0 && state.retrieving.isEmpty())
                forget(player);
        }
    }

    private static void reflect(ServerPlayer player, Vec3 center, double radius, boolean counter) {
        var box = new AABB(center, center).inflate(radius + 8);
        int count = 0;
        for (Projectile shot : player.serverLevel().getEntitiesOfClass(Projectile.class, box,
                s -> s.getOwner() != player && !s.isRemoved() && !(s.getOwner() instanceof LivingEntity living && !hostile(player, living)))) {
            if (++count > 48) break;
            Vec3 away = shot.position().subtract(center);
            Vec3 velocity = shot.getDeltaMovement();
            if (velocity.lengthSqr() < .001 || away.dot(velocity) >= 0) continue;
            Vec3 next = shot.position().add(velocity);
            Vec3 segment = next.subtract(shot.position());
            double along = Math.clamp(center.subtract(shot.position()).dot(segment) / segment.lengthSqr(), 0, 1);
            boolean crossing = shot.position().add(segment.scale(along)).distanceToSqr(center) <= radius * radius;
            if (!crossing || !clear(player, center, shot.position())) continue;
            Vec3 outgoing = counter ? aimPoint(player, 32).subtract(shot.position()).normalize()
                    : (away.lengthSqr() > .01 ? away.normalize() : velocity.reverse().normalize());
            shot.setOwner(player);
            move(shot, outgoing.scale(Math.max(1.2, velocity.length())));
            AbilityEffects.send(player.serverLevel(), Kind.TK_IMPACT, shot.position(), shot.position().add(outgoing), .6);
        }
    }

    public static Vec3 aimPoint(ServerPlayer player, double range) {
        // This service's ranges are actual world distances, while Targeting accepts base power reach.
        LivingEntity aimed = Targeting.livingEntityInSight(player,
                range / com.jayptucker.heroesevolved.ability.AbilityRanges.ranged(1.0), t -> hostile(player, t));
        return aimed == null ? blockAim(player, range).getLocation() : aimed.getBoundingBox().getCenter();
    }

    private static BlockHitResult blockAim(ServerPlayer player, double range) {
        return player.serverLevel().clip(new ClipContext(player.getEyePosition(),
                player.getEyePosition().add(player.getLookAngle().scale(range)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    }

    private static ItemEntity remoteTarget(ServerPlayer player) {
        Vec3 eye = player.getEyePosition(), end = blockAim(player, has(player, "long_reach") ? 48 : 24).getLocation();
        return player.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(eye, end).inflate(.75),
                        item -> item.isAlive() && !item.hasPickUpDelay()
                                && item.getBoundingBox().inflate(.65).clip(eye, end).isPresent())
                .stream().min(Comparator.comparingDouble(item -> item.distanceToSqr(player))).orElse(null);
    }

    private static boolean actionableBlock(ServerPlayer player) {
        BlockHitResult hit = blockAim(player, has(player, "long_reach") ? 48 : 24);
        if (hit.getType() != HitResult.Type.BLOCK || !player.level().mayInteract(player, hit.getBlockPos())
                || !WorldMutationPolicy.canEdit(player, player.serverLevel(), hit.getBlockPos())) return false;
        Block block = player.level().getBlockState(hit.getBlockPos()).getBlock();
        return block instanceof LeverBlock || block instanceof ButtonBlock || block instanceof DoorBlock
                || block instanceof TrapDoorBlock || block instanceof FenceGateBlock || block instanceof BellBlock
                || has(player, "retrieve") && player.level().getBlockEntity(hit.getBlockPos()) instanceof Container container
                && (!(container instanceof BaseContainerBlockEntity locked) || locked.canOpen(player))
                && !container.isEmpty();
    }

    private static boolean remoteHand(ServerPlayer player) {
        State state = state(player);
        if (state.retrieving.size() >= 8) return false;
        ItemEntity item = remoteTarget(player);
        if (item == null) {
            if (!actionableBlock(player)) return false;
            BlockHitResult hit = blockAim(player, has(player, "long_reach") ? 48 : 24);
            if (has(player, "retrieve") && player.level().getBlockEntity(hit.getBlockPos()) instanceof Container container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    if (container.getItem(i).isEmpty()) continue;
                    ItemStack taken = container.removeItem(i, container.getItem(i).getCount());
                    container.setChanged();
                    Vec3 at = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.3));
                    item = new ItemEntity(player.serverLevel(), at.x, at.y, at.z, taken);
                    item.setNoPickUpDelay();
                    player.serverLevel().addFreshEntity(item);
                    break;
                }
            } else {
                player.level().getBlockState(hit.getBlockPos()).useWithoutItem(player.level(), player, hit);
                AbilityEffects.send(player.serverLevel(), Kind.TK_GRIP, player.getEyePosition(), hit.getLocation(), .5);
                ArmPosePayload.show(player, ArmPose.KINETIC_HOLD, 12);
                return true;
            }
        }
        if (item == null) return false;
        state.retrieving.add(new Pull(item, state.age + 100));
        AbilityEffects.send(player.serverLevel(), Kind.TK_GATHER, item.position(), player.position().add(0, 1, 0), .7);
        ArmPosePayload.show(player, ArmPose.KINETIC_GATHER, 16);
        return true;
    }

    public static boolean hostile(ServerPlayer player, LivingEntity target) {
        return target != player && target.isAlive() && !target.isSpectator() && !player.isAlliedTo(target) && !target.isAlliedTo(player)
                && !(target instanceof net.minecraft.world.entity.TamableAnimal pet && player.getUUID().equals(pet.getOwnerUUID()))
                && !com.jayptucker.heroesevolved.entity.EntityCarryService.isBeingCarried(target)
                && !(target instanceof TemporalEchoEntity)
                && !com.jayptucker.heroesevolved.time.TemporalEntityEchoService.isPastCopy(target)
                && (!(target instanceof ServerPlayer other) || player.getServer().isPvpAllowed() && player.canHarmPlayer(other));
    }

    public static List<LivingEntity> enemies(ServerPlayer player, Vec3 center, double radius) {
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                        target -> hostile(player, target) && target.position().distanceToSqr(center) <= radius * radius
                                && clear(player, center, target.getBoundingBox().getCenter()))
                .stream().sorted(Comparator.comparingDouble(target -> target.position().distanceToSqr(center))).limit(32).toList();
    }

    private static boolean clear(ServerPlayer player, Vec3 from, Vec3 to) {
        return player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .getType() == HitResult.Type.MISS;
    }

    private static double bubbleRadius(ServerPlayer player) { return has(player, "wider_bubble") ? 4 : 3; }
    public static void move(Entity entity, Vec3 velocity) {
        entity.setDeltaMovement(velocity); entity.hurtMarked = true;
        if (entity instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }
    private static State state(ServerPlayer player) { return STATES.computeIfAbsent(player.getUUID(), id -> new State(player)); }
    public static void forget(ServerPlayer player) { STATES.remove(player.getUUID()); }
    public static void clear() { STATES.clear(); }

    private static final class State {
        final ServerPlayer owner;
        final ServerLevel level;
        final List<Pull> retrieving = new ArrayList<>();
        Well well;
        Bubble bubble;
        int age, step, grounding, crossfire;
        State(ServerPlayer player) { owner = player; level = player.serverLevel(); }
    }
    private record Pull(ItemEntity item, int until) {}
    private static final class Well {
        final Vec3 center;
        int remaining;
        Well(Vec3 center, int remaining) { this.center = center; this.remaining = remaining; }
    }
    private static final class Bubble {
        Vec3 center;
        final boolean anchored;
        int remaining;
        Bubble(Vec3 center, boolean anchored, int remaining) { this.center = center; this.anchored = anchored; this.remaining = remaining; }
    }
}
