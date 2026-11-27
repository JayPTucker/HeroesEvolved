package com.jayptucker.heroesevolved.telekinesis;

import com.jayptucker.heroesevolved.ability.AbilityRanges;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.time.TemporalEntityEchoService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;
import static com.jayptucker.heroesevolved.telekinesis.KineticGeometry.*;

/**
 * Telekinesis, the far hand: a hand of bent air that does at a distance what the caster's hand does. It moves only real
 * things (creatures, items, shots, primed TNT, carts, boats, falling blocks) and conjures nothing. The core kit lives
 * here: Shove (a palm driven along the aim, landing on each body as the drawn palm reaches it), Grip (hold anything
 * beside the view, swing it like a club by turning, throw it harder for the whip), Bullet Catch, Disarm and its fling, and
 * the Telekinetic Storm. Numbers and places shared with {@code KineticArt} are in {@link KineticGeometry}.
 */
public final class TelekinesisService {
    public static final int STORM_GATHER_TICKS = STORM_GATHER;
    public static final int STORM_LIFT_TICKS = STORM_LIFT;
    public static final int MAX_CAUGHT_PROJECTILES = MAX_CAUGHT;
    private static final int MAX_TARGETS = 64;
    private static final Map<UUID, Grip> GRIPS = new HashMap<>();
    private static final Map<UUID, BulletStop> BULLET_STOPS = new HashMap<>();
    private static final Map<UUID, Lift> LIFTS = new HashMap<>();
    private static final Map<UUID, Impact> IMPACTS = new HashMap<>();
    private static final Map<UUID, Plucked> PLUCKED = new HashMap<>();
    private static final List<PendingShove> SHOVES = new ArrayList<>();
    private static final List<Thrown> THROWN = new ArrayList<>();
    private static final List<Missile> MISSILES = new ArrayList<>();
    private static final ResourceLocation BULLET_ID = id("telekinesis_bullet_stop");
    private static final ResourceLocation STORM_ID = id("telekinesis_storm");

    private TelekinesisService() {}

    // ------------------------------------------------------------------ Shove

    /**
     * A palm of bent air driven along the aim. Everything in the cone is shoved as the palm reaches it
     * ({@link KineticGeometry#shoveArrival}), so the drawing and the blow agree; walls stop it.
     */
    public static void push(ServerPlayer player, int powerLevel) {
        if (!PowerRules.canAct(player)) return;
        Vec3 eye = player.getEyePosition(), direction = player.getLookAngle().normalize();
        double range = AbilityRanges.ranged(SHOVE_BASE_RANGE);
        Vec3 end = clippedEnd(player, eye, direction, range);
        double reach = end.distanceTo(eye);
        Owner owner = new Owner(player);
        for (Entity entity : player.serverLevel().getEntities(player, player.getBoundingBox().inflate(range), Entity::isAlive)
                .stream().limit(MAX_TARGETS).toList()) {
            Vec3 delta = entity.getBoundingBox().getCenter().subtract(eye);
            if (delta.lengthSqr() < .01 || delta.length() > reach + .5
                    || delta.normalize().dot(direction) < Math.cos(Math.toRadians(SHOVE_HALF_ANGLE))
                    || !clearLine(player.serverLevel(), player, eye, entity.getBoundingBox().getCenter())) continue;
            if (entity instanceof LivingEntity living) {
                if (!hostile(player, living) || isHeld(living)) continue;
            } else if (entity instanceof Projectile shot) {
                if (!hostileProjectile(player, shot)) continue;
            } else if (!loose(entity)) continue;
            double falloff = 1 - .45 * Math.min(1, delta.length() / range);
            PendingShove shove = new PendingShove(owner, entity, direction, falloff, Math.clamp(powerLevel, 1, 5));
            shove.wait = shoveArrival(delta.length(), reach);
            SHOVES.add(shove);
        }
        show(player.serverLevel(), Piece.KINETIC_SHOVE, hand(player), end, (float) reach, null, player.getId());
        ArmPosePayload.show(player, ArmPose.KINETIC_RELEASE, 10);
        sound(player, SoundEvents.WIND_CHARGE_BURST.value(), .8F, .85F);
        sound(player, SoundEvents.ILLUSIONER_CAST_SPELL, .35F, 1.6F);
        MasteryService.markPowerUse(player, ModAbilities.TELEKINESIS_ID);
    }

    /** Each pending shove counts down one tick per service tick and lands as the drawn palm reaches it. */
    private static void tickShoves() {
        for (PendingShove shove : List.copyOf(SHOVES)) {
            if (shove.wait-- > 0) continue;
            SHOVES.remove(shove);
            ServerPlayer player = shove.owner.player;
            Entity entity = shove.entity;
            if (!shove.owner.valid() || !entity.isAlive() || entity.level() != shove.owner.level) continue;
            Vec3 velocity = shove.direction.scale((1.5 + shove.level * .17) * shove.falloff).add(0, .22, 0);
            if (entity instanceof LivingEntity living) {
                if (!hostile(player, living) || isHeld(living)) continue;
                living.hurt(player.damageSources().playerAttack(player), 2 + shove.level * .25F);
                registerImpact(player, living, 3 + shove.level, false, 24);
                if (TelekinesisSkillService.has(player, "1_0")) thrown(player, living, 3 + shove.level, 24);
                if (TemporalEntityEchoService.isReplaying(living)) TemporalEntityEchoService.disturb(living, player);
            } else if (entity instanceof Projectile shot) shot.setOwner(player);
            setVelocity(entity, velocity);
            Vec3 at = entity.getBoundingBox().getCenter();
            show(shove.owner.level, Piece.KINETIC_IMPACT, at.subtract(shove.direction.scale(entity.getBbWidth() * .5)),
                    at.subtract(shove.direction.scale(entity.getBbWidth() * .5 + 1)), Math.max(.6F, entity.getBbWidth()), null, -1);
        }
    }

    // ------------------------------------------------------------------ Grip

    /** Both ordinary grips and an active storm reserve their targets against competing controllers. */
    public static boolean isHeld(Entity entity) {
        return GRIPS.values().stream().anyMatch(grip -> grip.target == entity)
                || LIFTS.values().stream().anyMatch(lift -> lift.targets.stream().anyMatch(t -> t.entity == entity))
                || TelekinesisSkillService.holds(entity);
    }

    public static boolean isGripping(ServerPlayer player) { return GRIPS.containsKey(player.getUUID()); }

    /** What this player holds, or null. */
    public static Entity gripped(ServerPlayer player) {
        Grip grip = GRIPS.get(player.getUUID());
        return grip == null ? null : grip.target;
    }

    /** A launched slam owns its landing damage until its single pending collision is resolved. */
    public static boolean isSlammed(Entity entity) {
        Impact impact = IMPACTS.get(entity.getUUID());
        return impact != null && impact.target == entity && impact.slam && impact.owner.valid()
                && entity.level() == impact.owner.level && now(impact.owner.player) <= impact.until;
    }

    private static double gripRange(ServerPlayer player) {
        return GRIP_BASE_RANGE + (TelekinesisSkillService.has(player, "long_reach") ? 4 : 0);
    }

    /** The creature in sight Grip would take (friend or foe), or null. */
    public static LivingEntity findLivingTarget(ServerPlayer player) {
        return Targeting.livingEntityInSight(player, gripRange(player), target -> eligibleTarget(player, target)
                && !isHeld(target) && (friendly(player, target) || hostile(player, target)));
    }

    /** What Grip would take: a creature in sight, or else a loose thing (an item, primed TNT, a cart, a boat, a falling block). */
    public static Entity findGripTarget(ServerPlayer player) {
        LivingEntity living = findLivingTarget(player);
        return living != null ? living : looseInSight(player, AbilityRanges.ranged(gripRange(player)));
    }

    /** Things a hand can pick up that are not creatures. */
    public static boolean loose(Entity entity) {
        return entity instanceof ItemEntity || entity instanceof PrimedTnt || entity instanceof AbstractMinecart
                || entity instanceof Boat || entity instanceof FallingBlockEntity;
    }

    private static Entity looseInSight(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition(), end = clippedEnd(player, eye, player.getLookAngle(), range);
        return player.serverLevel().getEntities(player, new AABB(eye, end).inflate(1), e -> e.isAlive() && loose(e)
                        && !e.isPassenger() && e.getPassengers().isEmpty() && !isHeld(e)
                        && e.getBoundingBox().inflate(.5).clip(eye, end).isPresent())
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
    }

    /** The far hand closes on what is in the crosshair and holds it beside the view. */
    public static boolean grip(ServerPlayer player, int powerLevel) {
        if (!PowerRules.canAct(player) || isGripping(player) || hasActiveLift(player)) return false;
        Entity target = findGripTarget(player);
        if (target == null) return false;
        boolean surged = com.jayptucker.heroesevolved.amplify.Amplification.surged(player);
        GRIPS.put(player.getUUID(), new Grip(new Owner(player), target, Math.clamp(powerLevel, 1, 5),
                now(player) + 20L * (6 + Math.clamp(powerLevel, 1, 5)) * (surged ? 2 : 1)));
        show(player.serverLevel(), Piece.KINETIC_SEND, hand(player), target.getBoundingBox().getCenter(), 1, player, player.getId());
        showGrip(player, target);
        ArmPosePayload.show(player, ArmPose.KINETIC_HOLD, 20);
        sound(player, SoundEvents.BREEZE_CHARGE, .7F, .75F);
        sound(player, SoundEvents.ILLUSIONER_PREPARE_MIRROR, .4F, 1.4F);
        if (target instanceof ServerPlayer held)
            held.displayClientMessage(Component.literal("An invisible hand takes hold of you.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        MasteryService.markPowerUse(player, ModAbilities.TELEKINESIS_ID);
        return true;
    }

    private static void showGrip(ServerPlayer player, Entity target) {
        show(player.serverLevel(), Piece.KINETIC_GRIP, target.position(), target.getBoundingBox().getCenter(),
                handScale(target.getBbWidth(), target.getBbHeight()), target, player.getId());
    }

    /** Throws what is held along the aim, harder for every block a tick the hold was swinging just before. */
    public static boolean throwGripped(ServerPlayer player) {
        Grip grip = GRIPS.get(player.getUUID());
        if (grip == null) return false;
        if (!grip.owner.valid() || !stillHoldable(player, grip.target)) return cancelGrip(player);
        GRIPS.remove(player.getUUID());
        Entity target = grip.target;
        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 aim = aimAt(player, AbilityRanges.ranged(20), target);
        Vec3 direction = aim.subtract(center);
        direction = direction.lengthSqr() < .01 ? player.getLookAngle() : direction.normalize();
        double whip = Math.min(MAX_WHIP, grip.whip());
        if (target instanceof LivingEntity living && friendly(player, living)) {
            gentleRelease(player, living);
            setVelocity(living, direction.scale(1.25).add(0, .15, 0));
        } else {
            setVelocity(target, direction.scale(THROW_SPEED + WHIP_BONUS * whip).add(0, .12, 0));
            float damage = 4 + grip.powerLevel + (float) (whip * 1.5);
            if (target instanceof LivingEntity living) {
                registerImpact(player, living, damage, false, 55);
                if (TelekinesisSkillService.has(player, "1_0")) thrown(player, living, damage, 40);
            } else MISSILES.add(new Missile(grip.owner, target, missileDamage(target) + (float) whip, now(player) + 60, new HashSet<>()));
        }
        show(player.serverLevel(), Piece.KINETIC_THROW, center, center.add(direction.scale(6)),
                handScale(target.getBbWidth(), target.getBbHeight()), target, player.getId());
        ArmPosePayload.show(player, ArmPose.KINETIC_FLING, 10);
        sound(player, SoundEvents.WIND_CHARGE_BURST.value(), .95F, .65F - (float) whip * .05F);
        return true;
    }

    /** Shift + Shove drives a held foe straight down. Friends and loose things are set down instead. */
    public static boolean slamGripped(ServerPlayer player) {
        Grip grip = GRIPS.get(player.getUUID());
        if (grip == null) return false;
        if (!grip.owner.valid() || !(grip.target instanceof LivingEntity living) || !hostile(player, living)) return placeGripped(player);
        GRIPS.remove(player.getUUID());
        registerImpact(player, living, 5 + grip.powerLevel, true, 45);
        setVelocity(living, new Vec3(0, -2.5, 0));
        show(player.serverLevel(), Piece.KINETIC_POP, living.position(), living.position().add(0, -3, 0), 2, living, player.getId());
        ArmPosePayload.show(player, ArmPose.KINETIC_SLAM, 12);
        sound(player, SoundEvents.BREEZE_SHOOT, .8F, .6F);
        return true;
    }

    /** A free precision release. Only an unobstructed, body-safe surface can reposition what is held. */
    public static boolean placeGripped(ServerPlayer player) {
        Grip grip = GRIPS.remove(player.getUUID());
        if (grip == null) return false;
        Entity target = grip.target;
        if (stillHoldable(player, target)) {
            Vec3 eye = player.getEyePosition();
            BlockHitResult hit = player.serverLevel().clip(new ClipContext(eye,
                    eye.add(player.getLookAngle().scale(16)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP) {
                Vec3 feet = hit.getLocation().add(0, .02, 0);
                Vec3 reached = SpeedService.swept(target, feet.subtract(target.position()));
                if (reached.distanceToSqr(feet) < .04 && SpeedService.safe(target, feet)) teleport(target, feet);
            }
            if (target instanceof LivingEntity living) gentleRelease(player, living);
            else setVelocity(target, Vec3.ZERO);
            show(player.serverLevel(), Piece.KINETIC_CUSHION, target.position(), target.position().add(0, 1, 0),
                    handScale(target.getBbWidth(), target.getBbHeight()), target, player.getId());
        }
        ArmPosePayload.clear(player);
        return true;
    }

    /** Lifecycle cancellation never aims, launches, or teleports an already released target. */
    public static boolean cancelGrip(ServerPlayer player) {
        Grip grip = GRIPS.remove(player.getUUID());
        if (grip == null) return false;
        if (grip.target.isAlive() && grip.target.level() == grip.owner.level && !grip.target.isPassenger()) {
            if (grip.target instanceof LivingEntity living) gentleRelease(player, living);
            else setVelocity(grip.target, Vec3.ZERO);
        }
        ArmPosePayload.clear(player);
        return true;
    }

    // ------------------------------------------------------------------ Disarm and fling

    /** Is there a plucked weapon this player can still fling? */
    public static boolean hasPlucked(ServerPlayer player) {
        Plucked plucked = PLUCKED.get(player.getUUID());
        return plucked != null && plucked.item.isAlive() && plucked.item.level() == player.level() && now(player) <= plucked.until;
    }

    /** The far hand plucks the weapon out of a mob's hand and brings it home; loose items near you come too. */
    public static void disarm(ServerPlayer player, int powerLevel) {
        if (!PowerRules.canAct(player)) return;
        Grip grip = GRIPS.get(player.getUUID());
        LivingEntity target = grip != null && grip.target instanceof LivingEntity held ? held : findLivingTarget(player);
        boolean affected = false;
        if (target != null && hostile(player, target) && player.hasLineOfSight(target)) {
            if (target instanceof Mob mob && !mob.getMainHandItem().isEmpty()) {
                ItemStack weapon = mob.getMainHandItem().copy();
                mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                ItemEntity item = new ItemEntity(player.serverLevel(), mob.getX(), mob.getEyeY() - .3, mob.getZ(), weapon);
                item.setDeltaMovement(towards(item.position(), player, .16).add(0, .12, 0));
                // It hangs beside you, ready to fling, until the window closes; then it is yours to pick up.
                item.setPickUpDelay(FLING_WINDOW + 5);
                player.serverLevel().addFreshEntity(item);
                PLUCKED.put(player.getUUID(), new Plucked(item, now(player) + FLING_WINDOW, player));
                show(player.serverLevel(), Piece.KINETIC_PLUCK, item.position(), hand(player), 1, item, player.getId());
                sound(player, SoundEvents.ITEM_BREAK, .6F, 1.4F);
                sound(player, SoundEvents.ARMOR_EQUIP_CHAIN.value(), .7F, 1.6F);
            } else if (grip == null) {
                setVelocity(target, safeVelocity(target, towards(target.position(), player, .12).add(0, .18, 0)));
                show(player.serverLevel(), Piece.KINETIC_BECKON, target.getBoundingBox().getCenter(), hand(player), 2, target, player.getId());
            }
            affected = true;
        }
        double radius = 8 + Math.clamp(powerLevel, 1, 5) * 2;
        int shown = 0;
        for (Entity loose : player.serverLevel().getEntities(player, player.getBoundingBox().inflate(radius),
                entity -> entity instanceof ItemEntity || entity instanceof ExperienceOrb).stream().limit(32).toList()) {
            if (loose.distanceToSqr(player) > radius * radius
                    || !clearLine(player.serverLevel(), player, player.getEyePosition(), loose.position().add(0, .1, 0))) continue;
            setVelocity(loose, safeVelocity(loose, towards(loose.position(), player, .1).add(0, .18, 0)));
            if (shown++ < 6) show(player.serverLevel(), Piece.KINETIC_BECKON, loose.position(), hand(player), 2, loose, player.getId());
            affected = true;
        }
        ArmPosePayload.show(player, affected ? ArmPose.KINETIC_PLUCK : ArmPose.KINETIC_BECKON, 10);
        MasteryService.markPowerUse(player, ModAbilities.TELEKINESIS_ID);
    }

    /** The second press: the plucked weapon turns in the air and flies where you aim, cutting what it meets. */
    public static boolean fling(ServerPlayer player) {
        if (!hasPlucked(player) || !PowerRules.canAct(player)) return false;
        Plucked plucked = PLUCKED.remove(player.getUUID());
        ItemEntity item = plucked.item;
        Vec3 aim = aimAt(player, AbilityRanges.ranged(16), item);
        Vec3 direction = aim.subtract(item.position());
        direction = direction.lengthSqr() < .01 ? player.getLookAngle() : direction.normalize();
        item.setPickUpDelay(30);
        setVelocity(item, direction.scale(FLING_SPEED).add(0, .06, 0));
        MISSILES.add(new Missile(new Owner(player), item, weaponDamage(item.getItem()), now(player) + 40, new HashSet<>()));
        show(player.serverLevel(), Piece.KINETIC_FLING, item.position(), item.position().add(direction), 1, item, player.getId());
        ArmPosePayload.show(player, ArmPose.KINETIC_FLING, 10);
        sound(player, SoundEvents.TRIDENT_THROW.value(), .9F, 1.25F);
        return true;
    }

    /** A plucked weapon hangs beside the caster's off hand, turning, until it is flung or the window closes. */
    private static void tickPlucked(long time) {
        for (var entry : List.copyOf(PLUCKED.entrySet())) {
            Plucked plucked = entry.getValue();
            ItemEntity item = plucked.item;
            if (!item.isAlive()) { PLUCKED.remove(entry.getKey()); continue; }
            ServerPlayer owner = item.level() instanceof ServerLevel level ? level.getServer().getPlayerList().getPlayer(entry.getKey()) : null;
            if (owner == null) owner = plucked.owner;
            if (time > plucked.until || owner.isRemoved() || owner.level() != item.level()) {
                item.setPickUpDelay(0);
                PLUCKED.remove(entry.getKey());
                continue;
            }
            Vec3 forward = Vec3.directionFromRotation(0, owner.getYRot());
            Vec3 side = new Vec3(forward.z, 0, -forward.x).scale(owner.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1);
            Vec3 hold = owner.position().add(forward.scale(.9)).add(side.scale(.9)).add(0, 1.1 + .08 * Math.sin(time * .2), 0);
            Vec3 velocity = hold.subtract(item.position()).scale(.35);
            if (velocity.lengthSqr() > .64) velocity = velocity.normalize().scale(.8);
            setVelocity(item, velocity.add(0, .04, 0));
        }
    }

    /** What a weapon hits for: its attack damage, at least three. */
    public static float weaponDamage(ItemStack stack) {
        double damage = 1;
        for (var entry : stack.getAttributeModifiers().modifiers())
            if (entry.attribute().value() == Attributes.ATTACK_DAMAGE.value()
                    && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE)
                damage += entry.modifier().amount();
        return (float) Math.max(3, damage);
    }

    private static float missileDamage(Entity entity) {
        if (entity instanceof ItemEntity item) return weaponDamage(item.getItem()) * .75F;
        if (entity instanceof AbstractMinecart || entity instanceof Boat) return 6;
        if (entity instanceof FallingBlockEntity) return 5;
        return 0;
    }

    // ------------------------------------------------------------------ Bullet Catch

    public static boolean hasBulletStop(ServerPlayer player) { return BULLET_STOPS.containsKey(player.getUUID()); }

    public static int caughtCount(ServerPlayer player) {
        BulletStop stop = BULLET_STOPS.get(player.getUUID());
        return stop == null ? 0 : stop.frozen.size();
    }

    /** A cupped hand ahead: shots coming at you stop dead in the air before it. */
    public static void bulletStop(ServerPlayer player, int powerLevel) {
        if (!PowerRules.canAct(player) || hasBulletStop(player)) return;
        BULLET_STOPS.put(player.getUUID(), new BulletStop(new Owner(player),
                now(player) + 20L * (5 + Math.clamp(powerLevel, 1, 5)), MAX_CAUGHT, CATCH_RADIUS, false));
        ArmPosePayload.show(player, ArmPose.KINETIC_CATCH, 20);
        sound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, .8F, .7F);
        show(player.serverLevel(), Piece.KINETIC_CATCH, player.position(), catchHand(player), 1, player, player.getId());
        MasteryService.markPowerUse(player, ModAbilities.TELEKINESIS_ID);
    }

    /** Still Air: a hovering telekinetic catches what reaches them without lifting a hand (ends with the hover). */
    public static void stillAir(ServerPlayer player, int ticks) {
        BulletStop stop = BULLET_STOPS.get(player.getUUID());
        if (stop != null) { if (stop.passive) stop.until = Math.max(stop.until, now(player) + ticks); return; }
        BULLET_STOPS.put(player.getUUID(), new BulletStop(new Owner(player), now(player) + ticks, STILL_AIR_CAUGHT, STILL_AIR_RADIUS, true));
    }

    /** Press Bullet Catch again to send every caught shot where you aim. */
    public static void releaseBulletStop(ServerPlayer player) { finishBulletStop(player, true); }
    public static boolean cancelBulletStop(ServerPlayer player) { return finishBulletStop(player, false); }

    /** Still Air's catch ends with the hover; shots still held drop where they hang. */
    static void cancelBulletStopIfPassive(ServerPlayer player) {
        BulletStop stop = BULLET_STOPS.get(player.getUUID());
        if (stop != null && stop.passive) finishBulletStop(player, false);
    }

    private static boolean finishBulletStop(ServerPlayer player, boolean launch) {
        BulletStop stop = BULLET_STOPS.remove(player.getUUID());
        if (stop == null) return false;
        launch &= stop.owner.valid();
        Vec3 aim = aimAt(player, AbilityRanges.ranged(20), null);
        for (FrozenProjectile frozen : stop.frozen) {
            Projectile projectile = frozen.projectile;
            if (projectile.isRemoved()) continue;
            projectile.setNoGravity(frozen.hadNoGravity);
            if (launch && projectile.level() == player.level()) {
                Vec3 direction = aim.subtract(projectile.position()).normalize();
                if (direction.lengthSqr() < .01) direction = player.getLookAngle();
                projectile.setOwner(player);
                setVelocity(projectile, direction.scale(2.5));
                show(player.serverLevel(), Piece.KINETIC_RETURN, projectile.position(), projectile.position().add(direction.scale(5)), 1, projectile, player.getId());
            } else setVelocity(projectile, Vec3.ZERO);
        }
        if (!stop.passive) startRecovery(player, BULLET_ID, COMMON.telekinesisBulletStopCooldownTicks.get());
        if (!stop.passive || launch) ArmPosePayload.show(player, launch ? ArmPose.KINETIC_FLING : ArmPose.KINETIC_BECKON, 10);
        if (launch && !stop.frozen.isEmpty()) sound(player, SoundEvents.WIND_CHARGE_BURST.value(), 1, 1.3F);
        return true;
    }

    // ------------------------------------------------------------------ Storm

    public static boolean hasActiveLift(ServerPlayer player) { return LIFTS.containsKey(player.getUUID()); }

    /** Two hands sweep everything round you in, lift it and turn it, then slam. Press again after the gather to slam early. */
    public static void telekineticStorm(ServerPlayer player, int powerLevel) {
        if (!PowerRules.canAct(player) || hasActiveLift(player)) return;
        cancelGrip(player);
        List<Lifted> targets = player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(STORM_RADIUS), target -> hostile(player, target)
                        && !isHeld(target) && eligibleTarget(player, target) && target.distanceToSqr(player) <= STORM_RADIUS * STORM_RADIUS
                        && player.hasLineOfSight(target)).stream()
                .sorted(Comparator.comparingDouble(target -> target.distanceToSqr(player))).limit(16)
                .map(target -> new Lifted(target, target.getY() + STORM_HEIGHT)).toList();
        Lift lift = new Lift(new Owner(player), targets, Math.clamp(powerLevel, 1, 5));
        LIFTS.put(player.getUUID(), lift);
        show(player.serverLevel(), Piece.KINETIC_STORM, lift.center, lift.center.add(0, 1, 0), STORM_RADIUS, player, player.getId());
        ArmPosePayload.show(player, ArmPose.KINETIC_STORM, STORM_GATHER);
        sound(player, SoundEvents.WARDEN_SONIC_CHARGE, 1.1F, .7F);
        MasteryService.markPowerUse(player, ModAbilities.TELEKINESIS_ID);
    }

    public static boolean slamStorm(ServerPlayer player) {
        Lift lift = LIFTS.get(player.getUUID());
        if (lift == null) return false;
        if (!lift.owner.valid()) return cancelStorm(player);
        if (lift.age < STORM_GATHER) {
            player.displayClientMessage(Component.literal("The storm is still gathering.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return false;
        }
        LIFTS.remove(player.getUUID());
        for (Lifted lifted : lift.targets) {
            LivingEntity target = lifted.entity;
            if (!target.isAlive() || target.level() != player.level() || !hostile(player, target)
                    || !player.hasLineOfSight(target)) continue;
            registerImpact(player, target, 6 + lift.powerLevel, true, 50);
            setVelocity(target, new Vec3(0, -2.8, 0));
        }
        show(player.serverLevel(), Piece.KINETIC_SLAM, lift.center, lift.center.add(0, 1, 0), STORM_RADIUS, player, player.getId());
        startRecovery(player, STORM_ID, COMMON.telekinesisStormCooldownTicks.get());
        ArmPosePayload.show(player, ArmPose.KINETIC_SLAM, 15);
        sound(player, SoundEvents.WIND_CHARGE_BURST.value(), 1.2F, .55F);
        CameraShakePayload.sendAround(player.serverLevel(), player.position(), 24, .8F, 10);
        return true;
    }

    public static boolean cancelStorm(ServerPlayer player) {
        Lift lift = LIFTS.remove(player.getUUID());
        if (lift == null) return false;
        for (Lifted lifted : lift.targets)
            if (lifted.entity.isAlive() && lifted.entity.level() == lift.owner.level) gentleRelease(player, lifted.entity);
        // The hands open and let go: a quiet release rather than a slam.
        show(lift.owner.level, Piece.KINETIC_IMPACT, lift.center.add(0, .1, 0), lift.center.add(0, 1.1, 0), STORM_RADIUS * .5F, null, -1);
        startRecovery(player, STORM_ID, COMMON.telekinesisStormCooldownTicks.get());
        ArmPosePayload.clear(player);
        return true;
    }

    // ------------------------------------------------------------------ Shared

    /** Loose objects near a telekinetic drift toward them, with fresh drops and solid cover respected. */
    public static void drawNearbyItems(ServerPlayer player) {
        if (!PowerRules.canAct(player)) return;
        for (ItemEntity item : player.serverLevel().getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(6), item -> item.getAge() > 40 && !isHeld(item)
                        && item.distanceToSqr(player) <= 36).stream().limit(24).toList()) {
            if (clearLine(player.serverLevel(), player, player.getEyePosition(), item.position().add(0, .1, 0)))
                item.setDeltaMovement(item.getDeltaMovement().add(towards(item.position(), player, .02)));
        }
    }

    public static void awaken(ServerPlayer player) {
        show(player.serverLevel(), Piece.KINETIC_SHOVE, hand(player), player.getEyePosition().add(player.getLookAngle().scale(10)), 10, null, player.getId());
        sound(player, SoundEvents.WIND_CHARGE_BURST.value(), 1.1F, .55F);
    }

    public static void migraine(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100));
        ScreenTintPayload.send(player, 0x503C294C, 40, ScreenTintPayload.Style.EDGE);
        player.displayClientMessage(Component.literal("Your concentration fractures.").withStyle(ChatFormatting.DARK_RED), true);
    }

    public static void stopAll(ServerPlayer player) {
        cancelGrip(player);
        cancelStorm(player);
        cancelBulletStop(player);
        IMPACTS.values().removeIf(impact -> impact.owner.player == player);
        SHOVES.removeIf(shove -> shove.owner.player == player);
        THROWN.removeIf(t -> t.owner.player == player);
        MISSILES.removeIf(m -> m.owner.player == player);
        PLUCKED.remove(player.getUUID());
        TelekinesisSkillService.forget(player);
        ArmPosePayload.clear(player);
    }

    public static void clear() {
        Set<ServerPlayer> owners = new HashSet<>();
        GRIPS.values().forEach(grip -> owners.add(grip.owner.player));
        LIFTS.values().forEach(lift -> owners.add(lift.owner.player));
        BULLET_STOPS.values().forEach(stop -> owners.add(stop.owner.player));
        owners.forEach(TelekinesisService::stopAll);
        GRIPS.clear(); LIFTS.clear(); BULLET_STOPS.clear(); IMPACTS.clear();
        SHOVES.clear(); THROWN.clear(); MISSILES.clear(); PLUCKED.clear();
        TelekinesisSkillService.clear();
    }

    public static void tick(MinecraftServer server) {
        long time = server.overworld().getGameTime();
        tickShoves();
        tickGrips(time);
        tickBulletStops(time);
        tickLifts();
        tickImpacts(time);
        tickThrown(time);
        tickMissiles(time);
        tickPlucked(time);
    }

    private static boolean stillHoldable(ServerPlayer player, Entity target) {
        if (target instanceof LivingEntity living) return eligibleTarget(player, living);
        return target.isAlive() && !target.isRemoved() && target.level() == player.level() && !target.isPassenger();
    }

    private static void tickGrips(long time) {
        for (Grip grip : List.copyOf(GRIPS.values())) {
            ServerPlayer player = grip.owner.player;
            Entity target = grip.target;
            double reach = AbilityRanges.ranged(gripRange(player)) + 4;
            if (!grip.owner.valid() || !stillHoldable(player, target) || time >= grip.until
                    || target.distanceToSqr(player) > reach * reach || !player.hasLineOfSight(target)) {
                cancelGrip(player);
                continue;
            }
            Vec3 hold = holdPoint(player, target);
            Vec3 velocity = hold.subtract(target.position()).scale(.45);
            if (velocity.lengthSqr() > GRIP_MAX_SPEED * GRIP_MAX_SPEED) velocity = velocity.normalize().scale(GRIP_MAX_SPEED);
            // Gravity is compensated in velocity, never by persisting noGravity/noAI flags.
            Vec3 moved = safeVelocity(target, velocity.add(0, .08, 0));
            setVelocity(target, moved);
            target.resetFallDistance();
            double speed = moved.length();
            grip.swing[grip.age++ % grip.swing.length] = speed;
            if (speed >= SWING_HIT_SPEED) swingInto(grip, moved, time);
            if (target instanceof LivingEntity living && time % 10 == 0)
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 4, false, false));
            if (time % 10 == 0) {
                ArmPosePayload.show(player, ArmPose.KINETIC_HOLD, 15);
                showGrip(player, target);
            }
        }
    }

    /** A held body swung through others bats them aside, and takes the blow too: a club made of a creature. */
    private static void swingInto(Grip grip, Vec3 motion, long time) {
        ServerPlayer player = grip.owner.player;
        Entity club = grip.target;
        AABB swept = club.getBoundingBox().expandTowards(motion).inflate(.25);
        float damage = 2 + grip.powerLevel * .5F;
        for (LivingEntity struck : club.level().getEntitiesOfClass(LivingEntity.class, swept,
                e -> e != club && e != player && hostile(player, e) && !isHeld(e))) {
            Long last = grip.batted.get(struck.getUUID());
            if (last != null && time - last < 10) continue;
            grip.batted.put(struck.getUUID(), time);
            struck.invulnerableTime = 0;
            struck.hurt(player.damageSources().playerAttack(player), damage);
            if (club instanceof LivingEntity body && hostile(player, body)) {
                body.invulnerableTime = 0;
                body.hurt(player.damageSources().playerAttack(player), damage * .5F);
            }
            setVelocity(struck, motion.normalize().scale(Math.min(1.6, .6 + motion.length() * .6)).add(0, .3, 0));
            if (TelekinesisSkillService.has(player, "1_0")) thrown(player, struck, damage, 20);
            Vec3 at = struck.getBoundingBox().getCenter();
            show(grip.owner.level, Piece.KINETIC_IMPACT, at, at.subtract(motion.normalize()), Math.max(.8F, struck.getBbWidth() * 1.2F), null, -1);
            soundAt(grip.owner.level, at, SoundEvents.PLAYER_ATTACK_KNOCKBACK, .9F, .8F);
        }
    }

    private static void tickBulletStops(long time) {
        for (BulletStop stop : List.copyOf(BULLET_STOPS.values())) {
            ServerPlayer player = stop.owner.player;
            if (!stop.owner.valid() || time >= stop.until) {
                cancelBulletStop(player);
                continue;
            }
            stop.frozen.removeIf(frozen -> frozen.projectile.isRemoved());
            double radius = stop.radius;
            for (Projectile shot : player.serverLevel().getEntitiesOfClass(Projectile.class,
                    player.getBoundingBox().inflate(radius), shot -> !shot.isRemoved()
                            && shot.getDeltaMovement().lengthSqr() > .01).stream().limit(MAX_TARGETS).toList()) {
                if (stop.frozen.size() >= stop.capacity) break;
                if (!hostileProjectile(player, shot) || stop.frozen.stream().anyMatch(frozen -> frozen.projectile == shot)
                        || shot.distanceToSqr(player) > radius * radius
                        || shot.getDeltaMovement().dot(shot.position().subtract(player.getEyePosition())) >= 0
                        || !clearLine(player.serverLevel(), player, player.getEyePosition(), shot.position())) continue;
                stop.frozen.add(new FrozenProjectile(shot, shot.isNoGravity()));
                show(player.serverLevel(), Piece.KINETIC_SHOT, shot.position(), shot.position().add(shot.getDeltaMovement()), 1, shot, player.getId());
                sound(player, SoundEvents.AMETHYST_BLOCK_HIT, .35F, 1.7F);
            }
            for (int i = 0; i < stop.frozen.size(); i++) {
                Projectile shot = stop.frozen.get(i).projectile;
                if (shot.level() != stop.owner.level) continue;
                Vec3 destination = stop.passive ? stillAirSlot(player, i, time) : shotSlot(player, i);
                // Shots ease into their places rather than jumping, and slow to a stop as they arrive.
                Vec3 eased = shot.position().lerp(destination, .35);
                Vec3 safe = SpeedService.swept(shot, eased.subtract(shot.position()));
                shot.setNoGravity(true);
                shot.setPos(safe);
                setVelocity(shot, Vec3.ZERO);
                if (time % 10 == 0) show(player.serverLevel(), Piece.KINETIC_SHOT, shot.position(), shot.position().add(0, 1, 0), 1, shot, player.getId());
            }
            if (time % 10 == 0 && !stop.passive) {
                ArmPosePayload.show(player, ArmPose.KINETIC_CATCH, 15);
                show(player.serverLevel(), Piece.KINETIC_CATCH, player.position(), catchHand(player), 1, player, player.getId());
            }
        }
    }

    /** Where a shot caught by Still Air hangs: a slow ring round the hovering body. */
    private static Vec3 stillAirSlot(ServerPlayer player, int index, long time) {
        double angle = index * Math.PI * 2 / STILL_AIR_CAUGHT + time * .02;
        return player.position().add(Math.cos(angle) * 1.6, 1.1 + .2 * Math.sin(time * .05 + index), Math.sin(angle) * 1.6);
    }

    private static void tickLifts() {
        for (Lift lift : List.copyOf(LIFTS.values())) {
            ServerPlayer player = lift.owner.player;
            if (!lift.owner.valid() || player.position().distanceToSqr(lift.center) > 24 * 24) {
                cancelStorm(player);
                continue;
            }
            lift.age++;
            if (lift.age < STORM_GATHER) {
                // The gather is the warning: the hands sweep in and the ring fills, but nobody is touched yet.
                if (lift.age % 10 == 0) ArmPosePayload.show(player, ArmPose.KINETIC_STORM, 15);
                continue;
            }
            for (Lifted lifted : lift.targets) {
                LivingEntity target = lifted.entity;
                if (!eligibleTarget(player, target) || !hostile(player, target) || !player.hasLineOfSight(target)) continue;
                Vec3 offset = target.position().subtract(lift.center).multiply(1, 0, 1);
                Vec3 swirl = offset.lengthSqr() < .01 ? Vec3.ZERO : new Vec3(-offset.z, 0, offset.x).normalize().scale(.08);
                Vec3 velocity = swirl.add(0, Math.clamp((lifted.hoverY - target.getY()) * .23, -.3, .65) + .08, 0);
                setVelocity(target, safeVelocity(target, velocity));
                target.resetFallDistance();
            }
            if (lift.age % 10 == 0) ArmPosePayload.show(player, ArmPose.KINETIC_STORM, 15);
            if (lift.age >= STORM_GATHER + STORM_LIFT) slamStorm(player);
        }
    }

    /** Marks a body whose next collision (a wall, or the ground for a slam) is a hit by this player. */
    static void registerImpact(ServerPlayer player, LivingEntity target, float damage, boolean slam, int ticks) {
        IMPACTS.put(target.getUUID(), new Impact(new Owner(player), target, damage, slam, now(player) + ticks, false));
    }

    private static void tickImpacts(long time) {
        for (Impact impact : List.copyOf(IMPACTS.values())) {
            LivingEntity target = impact.target;
            if (!impact.owner.valid() || !target.isAlive() || target.level() != impact.owner.level
                    || time > impact.until || !hostile(impact.owner.player, target)) {
                IMPACTS.remove(target.getUUID(), impact);
                continue;
            }
            if (!(impact.slam ? target.onGround() || target.verticalCollision || target.isInWater()
                    : target.horizontalCollision)) continue;
            IMPACTS.remove(target.getUUID(), impact);
            ServerPlayer player = impact.owner.player;
            if (!player.hasLineOfSight(target)) continue;
            // The impact is its own blow: an earlier hit's immunity (a pop, a swing) must not eat it.
            target.invulnerableTime = 0;
            boolean hurt = target.hurt(player.damageSources().playerAttack(player), impact.damage);
            if (impact.slam) {
                target.resetFallDistance();
                // A slam drives the body into the ground: it doesn't skid away on the hit's knockback.
                setVelocity(target, new Vec3(0, Math.min(0, target.getDeltaMovement().y), 0));
            }
            Vec3 at = impact.slam ? target.position().add(0, .1, 0) : target.getBoundingBox().getCenter();
            Vec3 normal = impact.slam ? new Vec3(0, 1, 0) : target.getDeltaMovement().reverse().normalize();
            if (normal.lengthSqr() < .01) normal = player.getLookAngle().reverse();
            show(impact.owner.level, Piece.KINETIC_IMPACT, at, at.add(normal), impact.slam ? 2.6F : 1.5F, null, -1);
            soundAt(player.serverLevel(), at, SoundEvents.ANVIL_LAND, .5F, .8F);
            if (hurt) TelekinesisSkillService.onImpact(player, target, at, impact.damage, impact.slam, impact.rebounded);
        }
    }

    /** Carom: sends a body that has just struck a wall at another foe, and marks the rebound so it happens once. */
    static void rebound(ServerPlayer player, LivingEntity body, LivingEntity at, float damage) {
        Vec3 to = at.getBoundingBox().getCenter().subtract(body.getBoundingBox().getCenter());
        if (to.lengthSqr() < .01) return;
        setVelocity(body, to.normalize().scale(1.9).add(0, .15, 0));
        IMPACTS.put(body.getUUID(), new Impact(new Owner(player), body, damage, false, now(player) + 30, true));
        thrown(player, body, damage, 30);
    }

    /** A body in flight that hurts what it runs into (Bowling, Carom, swung foes), once each. */
    static void thrown(ServerPlayer player, LivingEntity body, float damage, int ticks) {
        THROWN.removeIf(t -> t.body == body);
        THROWN.add(new Thrown(new Owner(player), body, damage, now(player) + ticks, new HashSet<>()));
    }

    private static void tickThrown(long time) {
        for (Thrown t : List.copyOf(THROWN)) {
            if (time > t.until || !t.body.isAlive() || !t.owner.valid() || t.body.level() != t.owner.level) { THROWN.remove(t); continue; }
            Vec3 motion = t.body.getDeltaMovement();
            if (motion.lengthSqr() < .09) continue;
            ServerPlayer player = t.owner.player;
            for (LivingEntity other : t.body.level().getEntitiesOfClass(LivingEntity.class,
                    t.body.getBoundingBox().expandTowards(motion).inflate(.3),
                    e -> e != t.body && e != player && hostile(player, e) && !isHeld(e))) {
                if (!t.struck.add(other.getUUID())) continue;
                other.invulnerableTime = 0;
                other.hurt(player.damageSources().playerAttack(player), t.damage * .7F);
                setVelocity(other, motion.scale(.7).add(0, .25, 0));
                Vec3 at = other.getBoundingBox().getCenter();
                show(t.owner.level, Piece.KINETIC_IMPACT, at, at.subtract(motion.normalize()), Math.max(.8F, other.getBbWidth() * 1.2F), null, -1);
                soundAt(t.owner.level, at, SoundEvents.PLAYER_ATTACK_CRIT, .9F, .8F);
            }
        }
    }

    private static void tickMissiles(long time) {
        for (Missile m : List.copyOf(MISSILES)) {
            Entity thing = m.thing;
            if (time > m.until || !thing.isAlive() || !m.owner.valid() || thing.level() != m.owner.level) { MISSILES.remove(m); continue; }
            Vec3 motion = thing.getDeltaMovement();
            if (motion.lengthSqr() < .04 || m.damage <= 0) { if (m.damage <= 0) MISSILES.remove(m); continue; }
            ServerPlayer player = m.owner.player;
            Vec3 start = thing.getBoundingBox().getCenter(), end = start.add(motion);
            LivingEntity hit = thing.level().getEntitiesOfClass(LivingEntity.class, thing.getBoundingBox().expandTowards(motion).inflate(.35),
                            e -> e != player && hostile(player, e) && !m.struck.contains(e.getUUID()))
                    .stream().filter(e -> e.getBoundingBox().inflate(.35).clip(start, end).isPresent() || e.getBoundingBox().inflate(.35).contains(start))
                    .min(Comparator.comparingDouble(e -> e.distanceToSqr(start))).orElse(null);
            if (hit == null) continue;
            m.struck.add(hit.getUUID());
            hit.invulnerableTime = 0;
            hit.hurt(player.damageSources().playerAttack(player), m.damage);
            setVelocity(hit, motion.normalize().scale(.6).add(0, .2, 0));
            // A thrown weapon stops in what it hits and drops; a cart or a boat bowls on, slowed.
            setVelocity(thing, thing instanceof ItemEntity ? new Vec3(0, .1, 0) : motion.scale(.5));
            if (thing instanceof ItemEntity) MISSILES.remove(m);
            Vec3 at = hit.getBoundingBox().getCenter();
            show(m.owner.level, Piece.KINETIC_IMPACT, at, at.subtract(motion.normalize()), Math.max(.8F, hit.getBbWidth() * 1.2F), null, -1);
            soundAt(m.owner.level, at, thing instanceof ItemEntity ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.ANVIL_LAND, .8F, 1.1F);
        }
    }

    private static void gentleRelease(ServerPlayer player, LivingEntity target) {
        target.resetFallDistance();
        setVelocity(target, Vec3.ZERO);
        if (friendly(player, target) && TelekinesisSkillService.has(player, "0_0"))
            target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
    }

    static boolean eligibleTarget(ServerPlayer player, LivingEntity target) {
        return target != player && target.isAlive() && !target.isRemoved() && !target.isSpectator()
                && target.level() == player.level() && !target.noPhysics
                && !target.isPassenger() && target.getPassengers().isEmpty()
                && !(target instanceof TemporalEchoEntity) && !TemporalEntityEchoService.isPastCopy(target)
                && !EntityCarryService.isBeingCarried(target);
    }

    static boolean friendly(ServerPlayer player, LivingEntity target) {
        return target == player || player.isAlliedTo(target) || target.isAlliedTo(player)
                || target instanceof TamableAnimal pet && player.getUUID().equals(pet.getOwnerUUID());
    }

    static boolean hostile(ServerPlayer player, LivingEntity target) {
        return eligibleTarget(player, target) && !friendly(player, target)
                && (!(target instanceof ServerPlayer other) || player.getServer().isPvpAllowed() && player.canHarmPlayer(other));
    }

    private static boolean hostileProjectile(ServerPlayer player, Projectile projectile) {
        Entity owner = projectile.getOwner();
        return owner != player && !(owner instanceof LivingEntity living && friendly(player, living));
    }

    /** Where the force leaves the caster on the server: a little ahead of the eye and below it (the drawing uses the visible hand). */
    static Vec3 hand(ServerPlayer player) { return player.getEyePosition().add(player.getLookAngle().scale(.35)).add(0, -.25, 0); }
    private static Vec3 towards(Vec3 from, ServerPlayer player, double strength) { return player.position().add(0, .8, 0).subtract(from).scale(strength); }
    static Vec3 safeVelocity(Entity entity, Vec3 desired) { return SpeedService.swept(entity, desired).subtract(entity.position()); }
    static boolean clearLine(ServerLevel level, Entity context, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context))
                .getType() == HitResult.Type.MISS;
    }
    /**
     * Where a throw goes: the middle of the foe under the crosshair, else where the look meets a block. Something held
     * off to the side of the view must fly at what the crosshair is on, not along a line parallel to it.
     */
    static Vec3 aimAt(ServerPlayer player, double range, Entity except) {
        LivingEntity aimed = Targeting.livingEntityInRange(player, range, ClipContext.Fluid.NONE, t -> t != except && hostile(player, t));
        return aimed != null ? aimed.getBoundingBox().getCenter() : clippedEnd(player, player.getEyePosition(), player.getLookAngle(), range);
    }

    static Vec3 clippedEnd(ServerPlayer player, Vec3 origin, Vec3 direction, double range) {
        BlockHitResult hit = player.serverLevel().clip(new ClipContext(origin, origin.add(direction.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : origin.add(direction.scale(range));
    }
    static long now(ServerPlayer player) { return player.getServer().overworld().getGameTime(); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("heroesevolved", path); }
    static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        soundAt(player.serverLevel(), player.position(), sound, volume, pitch);
    }
    static void soundAt(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }
    /** Shows a far-hand piece; {@code caster} is the entity id whose visible hand the drawing starts from (-1 for none). */
    static void show(ServerLevel level, Piece piece, Vec3 origin, Vec3 target, float scale, Entity bound, int caster) {
        SetPiecePayload.sendSeeded(level, piece, origin, target, scale, bound, 0, caster);
    }
    static void teleport(Entity entity, Vec3 at) {
        if (entity instanceof ServerPlayer player) player.teleportTo(player.serverLevel(), at.x, at.y, at.z, player.getYRot(), player.getXRot());
        else entity.teleportTo(at.x, at.y, at.z);
    }
    static void setVelocity(Entity entity, Vec3 velocity) {
        boolean changed = entity.getDeltaMovement().distanceToSqr(velocity) > .0001;
        entity.setDeltaMovement(velocity);
        entity.hurtMarked = true;
        if (entity instanceof ServerPlayer player && (changed || velocity.lengthSqr() > .0001 || player.tickCount % 5 == 0))
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        else if (!(entity instanceof LivingEntity) && entity.level() instanceof ServerLevel level && changed)
            level.getChunkSource().broadcastAndSend(entity, new ClientboundSetEntityMotionPacket(entity));
        if (entity instanceof Mob) entity.setOnGround(false);
    }
    static void startRecovery(ServerPlayer player, ResourceLocation id, int configured) {
        int ticks = SkillTreeService.cooldown(player, ModAbilities.TELEKINESIS_ID,
                com.jayptucker.heroesevolved.ability.PowerBalance.cooldown(id, configured));
        if (ticks > 0) CooldownService.startCooldown(player, id, ticks);
    }
    private static ResourceLocation assigned(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower().filter(entry -> entry.getValue().isUnlocked())
                .map(Map.Entry::getKey).orElse(null);
    }

    /** A caster, kept as the player (game-test players aren't in the player list) with what they held at the start. */
    static final class Owner {
        final ServerPlayer player;
        final ServerLevel level;
        final ResourceLocation power;
        Owner(ServerPlayer player) { this.player = player; this.level = player.serverLevel(); this.power = assigned(player); }
        boolean valid() { return !player.isRemoved() && player.level() == level && PowerRules.canAct(player)
                && Objects.equals(power, assigned(player)); }
    }
    private static final class Grip {
        final Owner owner; final Entity target; final int powerLevel; final long until;
        final double[] swing = new double[4]; final Map<UUID, Long> batted = new HashMap<>(); int age;
        Grip(Owner owner, Entity target, int powerLevel, long until) { this.owner = owner; this.target = target; this.powerLevel = powerLevel; this.until = until; }
        double whip() { double best = 0; for (double s : swing) best = Math.max(best, s); return best; }
    }
    private record FrozenProjectile(Projectile projectile, boolean hadNoGravity) {}
    private static final class BulletStop {
        final Owner owner; long until; final int capacity; final double radius; final boolean passive;
        final List<FrozenProjectile> frozen = new ArrayList<>();
        BulletStop(Owner owner, long until, int capacity, double radius, boolean passive) {
            this.owner = owner; this.until = until; this.capacity = capacity; this.radius = radius; this.passive = passive;
        }
    }
    private record Lifted(LivingEntity entity, double hoverY) {}
    private static final class Lift {
        final Owner owner; final List<Lifted> targets; final int powerLevel; final Vec3 center; int age;
        Lift(Owner owner, List<Lifted> targets, int powerLevel) {
            this.owner = owner; this.targets = targets; this.powerLevel = powerLevel; this.center = owner.player.position();
        }
    }
    private record Impact(Owner owner, LivingEntity target, float damage, boolean slam, long until, boolean rebounded) {}
    private static final class PendingShove {
        final Owner owner; final Entity entity; final Vec3 direction; final double falloff; final int level; int wait;
        PendingShove(Owner owner, Entity entity, Vec3 direction, double falloff, int level) {
            this.owner = owner; this.entity = entity; this.direction = direction; this.falloff = falloff; this.level = level;
        }
    }
    private record Thrown(Owner owner, LivingEntity body, float damage, long until, Set<UUID> struck) {}
    private record Missile(Owner owner, Entity thing, float damage, long until, Set<UUID> struck) {}
    private record Plucked(ItemEntity item, long until, ServerPlayer owner) {}
}
