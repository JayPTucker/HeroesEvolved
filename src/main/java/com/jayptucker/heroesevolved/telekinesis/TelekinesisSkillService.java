package com.jayptucker.heroesevolved.telekinesis;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.AbilityRanges;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.PowerBalance;
import com.jayptucker.heroesevolved.ability.SimpleAction;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import com.jayptucker.heroesevolved.energy.StaminaDrain;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.jayptucker.heroesevolved.telekinesis.KineticGeometry.*;
import static com.jayptucker.heroesevolved.telekinesis.TelekinesisService.show;

/**
 * The far hand's skill tree (node IDs kept from the first kit; names and meanings reworked so nothing here borrows
 * another power's idea). Fine Hands: Soft Landing (placed friends float down; the hand catches a long fall), Pin (Sylar's:
 * drive a body back into what it meets and hold it there), Pinned Together, Beckon, Long Reach, Retrieve. Collision:
 * Bowling (thrown bodies hurt what they hit), Round Up, Carom, Juggle, Five at Once, Spike, and the signature Skyfall.
 * Levitation: Grounding, Vector Step, Second Step, Levitate, Raise the Party, Still Air.
 */
public final class TelekinesisSkillService {
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final ResourceLocation LEVITATE_FLIGHT = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "kinetic_levitate");
    public static final String PIN = "telekinesis_0_1", BECKON = "telekinesis_remote_hand", ROUND_UP = "telekinesis_1_1",
            JUGGLE = "telekinesis_kinetic_volley", STEP = "telekinesis_2_1", LEVITATE = "telekinesis_force_bubble",
            SKYFALL = "telekinesis_skyfall";
    private TelekinesisSkillService() {}

    public static AbilityAction action(SkillCatalog.Node node) {
        return SimpleAction.builder(node.id(), node.level()).cost(node::energy, node::cooldown)
                .canUse(c -> canUse(c.player(), node.id()))
                .activate(c -> activate(c.player(), node.id(), c.modifierHeld()))
                .followUp(c -> followUp(c.player(), node.id()))
                .deactivateOnPress(c -> hasActiveField(c.player(), node.id()))
                .deactivate(c -> cancel(c.player(), node.id(), !c.modifierHeld())).build();
    }

    public static boolean has(ServerPlayer player, String suffix) {
        return SkillTreeService.has(player, suffix.startsWith("telekinesis_") ? suffix : "telekinesis_" + suffix);
    }

    private static boolean hasActiveField(ServerPlayer player, String id) {
        State state = STATES.get(player.getUUID());
        return state != null && (id.equals(ROUND_UP) && state.well != null || id.equals(LEVITATE) && state.levitate != null);
    }

    public static boolean eligible(ServerPlayer player) {
        return PowerRules.canAct(player) && !player.isRemoved()
                && PlayerAbilityService.getAbility(player, ModAbilities.TELEKINESIS_ID)
                .map(p -> p.isUnlocked()).orElse(false);
    }

    public static boolean canUse(ServerPlayer player, String id) {
        if (!eligible(player) || !has(player, id) || player.isPassenger()) return false;
        if (!STATES.containsKey(player.getUUID()) && STATES.size() >= 64) return false;
        State state = STATES.get(player.getUUID());
        return switch (id) {
            case PIN -> pinTarget(player) != null;
            case ROUND_UP -> true;
            case JUGGLE -> !juggleTargets(player).isEmpty();
            case BECKON -> remoteTarget(player) != null || actionableBlock(player);
            case STEP -> !player.isInWaterOrBubble() && !player.isFallFlying();
            case LEVITATE -> !player.isFallFlying() && !player.isInWaterOrBubble()
                    && !com.jayptucker.heroesevolved.pyro.PyroFlightService.isFlying(player);
            case SKYFALL -> (state == null || state.skyfall == null) && skyfallTarget(player) != null;
            default -> false;
        };
    }

    public static boolean activate(ServerPlayer player, String id, boolean modifier) {
        if (!canUse(player, id)) return false;
        boolean done = switch (id) {
            case PIN -> pin(player, modifier);
            case BECKON -> remoteHand(player);
            case ROUND_UP -> roundUp(player);
            case JUGGLE -> juggle(player);
            case STEP -> step(player, modifier);
            case LEVITATE -> levitate(player);
            case SKYFALL -> skyfall(player);
            default -> false;
        };
        if (done) MasteryService.markPowerUse(player, ModAbilities.TELEKINESIS_ID);
        return done;
    }

    /** Free second presses: Spike (a juggle in the air), and Second Step (a vault in the air). */
    private static boolean followUp(ServerPlayer player, String id) {
        State state = STATES.get(player.getUUID());
        if (state == null || !eligible(player)) return false;
        if (id.equals(JUGGLE) && has(player, "iron_rain") && !state.juggled.isEmpty()) return spike(player, state);
        if (id.equals(STEP) && has(player, "2_2") && state.secondStep > 0 && !player.onGround()) {
            state.secondStep = 0;
            vault(player, state, false);
            return true;
        }
        return false;
    }

    /** Recasting a held field releases it. Shift or changing loadout cancels safely. */
    public static boolean cancel(ServerPlayer player, String id, boolean release) {
        State state = STATES.get(player.getUUID());
        if (state == null) return false;
        if (id.equals(ROUND_UP) && state.well != null) {
            Vec3 center = state.well.center;
            state.well = null;
            if (release && eligible(player)) {
                // The cupped hands throw open: everyone gathered goes up.
                for (LivingEntity target : enemies(player, center, ROUND_UP_RADIUS)) {
                    move(target, center.subtract(target.position()).multiply(.12, 0, .12).add(0, .95, 0));
                    target.resetFallDistance();
                    show(player.serverLevel(), Piece.KINETIC_POP, target.position(), target.position().add(0, 1, 0), 1, target, player.getId());
                }
                ArmPosePayload.show(player, ArmPose.KINETIC_JUGGLE, 8);
            }
            return true;
        }
        if (id.equals(LEVITATE) && state.levitate != null) {
            stopLevitate(player, state);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ Fine Hands

    private static LivingEntity pinTarget(ServerPlayer player) {
        return Targeting.livingEntityInSight(player, PIN_BASE_RANGE, t -> TelekinesisService.hostile(player, t)
                && !TelekinesisService.isHeld(t) && !t.getType().is(Tags.EntityTypes.BOSSES));
    }

    /** Sylar's pin: a palm drives the body back along the aim until it meets a wall (Shift: the ground), and holds it. */
    private static boolean pin(ServerPlayer player, boolean down) {
        LivingEntity target = pinTarget(player);
        if (target == null) return false;
        State state = state(player);
        // The drive is level (a downward slant would meet the floor at once); Shift drives straight down.
        Vec3 look = player.getLookAngle();
        Vec3 direction = down ? new Vec3(0, -1, 0) : new Vec3(look.x, 0, look.z);
        direction = direction.lengthSqr() < 1e-4 ? new Vec3(0, -1, 0) : direction.normalize();
        List<LivingEntity> carried = new ArrayList<>();
        if (has(player, "0_2") && !down) {
            // Whoever stands in the body's path, nearest first, with nothing solid between them and it.
            Vec3 from = target.getBoundingBox().getCenter();
            AABB path = target.getBoundingBox().expandTowards(direction.scale(PIN_DRIVE)).inflate(1.2);
            for (LivingEntity other : player.serverLevel().getEntitiesOfClass(LivingEntity.class, path, t -> t != target
                            && hostile(player, t) && !TelekinesisService.isHeld(t)).stream()
                    .sorted(Comparator.comparingDouble(t -> t.distanceToSqr(target))).toList()) {
                if (carried.size() >= PIN_TOGETHER) break;
                Vec3 to = other.getBoundingBox().getCenter().subtract(from);
                double along = to.dot(direction);
                if (along > 0 && along < PIN_DRIVE && to.subtract(direction.scale(along)).lengthSqr() < 1.6 * 1.6
                        && clear(player, from, other.getBoundingBox().getCenter())) carried.add(other);
            }
        }
        float damage = PIN_DAMAGE + .5F * Math.max(0, tier(player) - 2);
        state.pins.add(new Pinned(target, direction, carried, damage));
        show(player.serverLevel(), Piece.KINETIC_SEND, TelekinesisService.hand(player), target.getBoundingBox().getCenter(), 1, player, player.getId());
        showPin(player, target, target.getBoundingBox().getCenter(), direction.reverse());
        ArmPosePayload.show(player, ArmPose.KINETIC_PIN, 20);
        TelekinesisService.sound(player, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), .9F, .6F);
        return true;
    }

    private static void showPin(ServerPlayer player, LivingEntity target, Vec3 contact, Vec3 normal) {
        show(player.serverLevel(), Piece.KINETIC_PIN, contact, contact.add(normal), handScale(target.getBbWidth(), target.getBbHeight()), target, player.getId());
    }

    private static void tickPins(ServerPlayer player, State state) {
        for (Pinned pin : List.copyOf(state.pins)) {
            LivingEntity target = pin.target;
            if (!target.isAlive() || target.level() != state.level || state.age > pin.holdUntil) { state.pins.remove(pin); continue; }
            if (!pin.landed) {
                double travel = Math.min(PIN_SPEED, pin.remaining);
                Vec3 step = pin.direction.scale(travel);
                Vec3 reached = SpeedService.swept(target, step);
                double moved = reached.subtract(target.position()).length();
                TelekinesisService.setVelocity(target, reached.subtract(target.position()));
                for (LivingEntity other : pin.carried) if (other.isAlive())
                    TelekinesisService.setVelocity(other, SpeedService.swept(other, step).subtract(other.position()));
                target.resetFallDistance();
                pin.remaining -= moved;
                boolean wall = moved < travel - .05;
                if (wall || pin.remaining <= .01) land(player, state, pin, wall, reached);
                continue;
            }
            Vec3 drift = pin.anchor.subtract(target.position());
            TelekinesisService.setVelocity(target, drift.scale(.5));
            target.resetFallDistance();
            for (LivingEntity other : pin.carried) if (other.isAlive()) { TelekinesisService.setVelocity(other, Vec3.ZERO); other.resetFallDistance(); }
            if (state.age % 10 == 0) {
                for (LivingEntity held : pin.all()) if (held.isAlive()) {
                    held.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 6, false, false));
                    held.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 15, 4, false, false));
                }
                showPin(player, target, pin.contact, pin.direction.reverse());
                ArmPosePayload.show(player, ArmPose.KINETIC_PIN, 12);
            }
        }
    }

    /** The drive ends where the body was going this tick ({@code reached}): against what it met, or at full reach. */
    private static void land(ServerPlayer player, State state, Pinned pin, boolean wall, Vec3 reached) {
        pin.landed = true;
        LivingEntity target = pin.target;
        pin.anchor = reached;
        pin.contact = reached.add(0, target.getBbHeight() * .5, 0).add(pin.direction.scale(target.getBbWidth() * .5 + .05));
        pin.holdUntil = state.age + (wall ? PIN_HOLD : PIN_HOLD_AIR);
        for (LivingEntity held : pin.all()) {
            if (!held.isAlive()) continue;
            if (held != target) TelekinesisService.setVelocity(held, Vec3.ZERO);
            if (wall) {
                held.invulnerableTime = 0;
                held.hurt(player.damageSources().playerAttack(player), pin.damage);
            }
        }
        if (wall) {
            show(state.level, Piece.KINETIC_IMPACT, pin.contact, pin.contact.add(pin.direction.reverse()), 2, null, -1);
            CameraShakePayload.sendAround(state.level, pin.contact, 12, .3F, 6);
            TelekinesisService.soundAt(state.level, pin.contact, SoundEvents.ANVIL_LAND, .7F, .7F);
        }
        showPin(player, target, pin.contact, pin.direction.reverse());
    }

    // ------------------------------------------------------------------ Collision

    /** Round Up: two hands cup foes at the aimed spot together for five seconds. Press again to toss the knot up. */
    private static boolean roundUp(ServerPlayer player) {
        State state = state(player);
        Vec3 center = aimPoint(player, 16);
        state.well = new Well(center, ROUND_UP_TICKS);
        show(player.serverLevel(), Piece.KINETIC_SEND, TelekinesisService.hand(player), center, 1, player, player.getId());
        show(player.serverLevel(), Piece.KINETIC_ROUND_UP, center, center.add(0, 1, 0), (float) ROUND_UP_RADIUS, null, player.getId());
        ArmPosePayload.show(player, ArmPose.KINETIC_BECKON, 18);
        TelekinesisService.sound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, .6F, .7F);
        return true;
    }

    private static List<LivingEntity> juggleTargets(ServerPlayer player) {
        Vec3 center = aimPoint(player, 16);
        int count = has(player, "five_fragments") ? 5 : JUGGLE_TARGETS;
        return enemies(player, center, JUGGLE_RADIUS).stream().filter(t -> !TelekinesisService.isHeld(t)
                && !t.getType().is(Tags.EntityTypes.BOSSES)).limit(count).toList();
    }

    /** Juggle: foes near where you aim are popped into the air one after another, and popped again as they fall. */
    private static boolean juggle(ServerPlayer player) {
        State state = state(player);
        List<LivingEntity> targets = juggleTargets(player);
        if (targets.isEmpty()) return false;
        int pops = has(player, "five_fragments") ? JUGGLE_POPS + 1 : JUGGLE_POPS;
        int i = 0;
        for (LivingEntity target : targets)
            state.juggled.add(new Juggled(target, pops, state.age + i++ * JUGGLE_STAGGER, target.getY()));
        ArmPosePayload.show(player, ArmPose.KINETIC_JUGGLE, 20);
        TelekinesisService.sound(player, SoundEvents.ILLUSIONER_CAST_SPELL, .7F, 1.3F);
        return true;
    }

    private static void tickJuggle(ServerPlayer player, State state) {
        for (Juggled j : List.copyOf(state.juggled)) {
            LivingEntity target = j.target;
            if (!target.isAlive() || target.level() != state.level || !TelekinesisService.hostile(player, target)) { state.juggled.remove(j); continue; }
            target.resetFallDistance();
            boolean falling = target.getDeltaMovement().y <= 0 && (target.onGround() || target.getY() <= j.floor + 1.2);
            if (j.pops > 0 && state.age >= j.next && (j.first || falling || state.age >= j.next + 18)) {
                j.first = false;
                j.pops--;
                j.next = state.age + JUGGLE_GAP;
                Vec3 nudge = player.position().subtract(target.position()).multiply(1, 0, 1);
                nudge = nudge.lengthSqr() < 1 ? Vec3.ZERO : nudge.normalize().scale(.06);
                TelekinesisService.setVelocity(target, new Vec3(nudge.x, JUGGLE_POP, nudge.z));
                target.invulnerableTime = 0;
                target.hurt(player.damageSources().playerAttack(player), JUGGLE_DAMAGE);
                show(state.level, Piece.KINETIC_POP, target.position(), target.position().add(0, 1, 0), 1, target, player.getId());
                TelekinesisService.soundAt(state.level, target.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, .8F, 1.4F);
                if (state.age % 2 == 0) ArmPosePayload.show(player, ArmPose.KINETIC_JUGGLE, 12);
            } else if (j.pops == 0 && (target.onGround() || state.age > j.next + 30)) state.juggled.remove(j);
        }
    }

    /** Spike: every juggled foe is driven into the ground at once. */
    private static boolean spike(ServerPlayer player, State state) {
        float damage = SPIKE_DAMAGE + tier(player) * .5F;
        for (Juggled j : List.copyOf(state.juggled)) {
            LivingEntity target = j.target;
            if (!target.isAlive() || target.level() != state.level) continue;
            TelekinesisService.registerImpact(player, target, damage, true, 40);
            TelekinesisService.setVelocity(target, new Vec3(0, -2.4, 0));
            show(state.level, Piece.KINETIC_POP, target.position(), target.position().add(0, -1, 0), 2, target, player.getId());
        }
        state.juggled.clear();
        ArmPosePayload.show(player, ArmPose.KINETIC_SLAM, 12);
        TelekinesisService.sound(player, SoundEvents.BREEZE_SHOOT, .9F, .55F);
        return true;
    }

    /** A held, thrown or slammed body struck something: Carom sends it on at another foe, once. */
    static void onImpact(ServerPlayer player, LivingEntity struck, Vec3 point, float damage, boolean slam, boolean rebounded) {
        if (!eligible(player) || slam || rebounded || !has(player, "1_2") || !struck.isAlive()) return;
        LivingEntity next = enemies(player, point, 8).stream().filter(t -> t != struck && player.hasLineOfSight(t)
                && !TelekinesisService.isHeld(t)).findFirst().orElse(null);
        if (next != null) TelekinesisService.rebound(player, struck, next, damage * .7F);
    }

    // ------------------------------------------------------------------ Skyfall (signature)

    private static LivingEntity skyfallTarget(ServerPlayer player) {
        return Targeting.livingEntityInSight(player, SKYFALL_BASE_RANGE, t -> TelekinesisService.hostile(player, t)
                && !TelekinesisService.isHeld(t));
    }

    /** Skyfall: the far hand hauls a foe into the sky, holds it, and drives it down where it stood. */
    private static boolean skyfall(ServerPlayer player) {
        LivingEntity target = skyfallTarget(player);
        if (target == null) return false;
        State state = state(player);
        ServerLevel level = player.serverLevel();
        if (target.getType().is(Tags.EntityTypes.BOSSES)) {
            // Far too heavy to lift: the hand drags it out of the air instead.
            TelekinesisService.registerImpact(player, target, 6 + tier(player), true, 40);
            TelekinesisService.setVelocity(target, new Vec3(0, -2.2, 0));
            show(level, Piece.KINETIC_POP, target.position(), target.position().add(0, -1, 0), 2, target, player.getId());
            ArmPosePayload.show(player, ArmPose.KINETIC_SLAM, 12);
            return true;
        }
        Vec3 start = target.position();
        BlockHitResult floor = level.clip(new ClipContext(start.add(0, .5, 0), start.add(0, -24, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, target));
        Vec3 drop = floor.getType() == HitResult.Type.MISS ? start : floor.getLocation();
        BlockHitResult roof = level.clip(new ClipContext(drop.add(0, target.getBbHeight() + .2, 0), drop.add(0, SKYFALL_HEIGHT + target.getBbHeight(), 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
        double top = roof.getType() == HitResult.Type.MISS ? SKYFALL_HEIGHT : Math.max(1, roof.getLocation().y - drop.y - target.getBbHeight() - .3);
        state.skyfall = new Skyfall(target, drop, top);
        show(level, Piece.KINETIC_SEND, TelekinesisService.hand(player), target.getBoundingBox().getCenter(), 1, player, player.getId());
        show(level, Piece.KINETIC_SKYFALL, drop, drop.add(0, top, 0), (float) SKYFALL_RADIUS, target, player.getId());
        ArmPosePayload.show(player, ArmPose.KINETIC_SKYFALL, SKYFALL_HAUL + SKYFALL_HANG);
        TelekinesisService.sound(player, SoundEvents.WARDEN_SONIC_CHARGE, 1, .55F);
        return true;
    }

    private static void tickSkyfall(ServerPlayer player, State state) {
        Skyfall fall = state.skyfall;
        LivingEntity target = fall.target;
        if (!target.isAlive() || target.level() != state.level) { state.skyfall = null; return; }
        fall.age++;
        Vec3 want = fall.drop.add(0, fall.top * skyfallLift(fall.age), 0);
        TelekinesisService.setVelocity(target, SpeedService.swept(target, want.subtract(target.position())).subtract(target.position()));
        target.resetFallDistance();
        if (fall.age % 10 == 0) target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 15, 4, false, false));
        if (fall.age == SKYFALL_HAUL + SKYFALL_HANG) {
            ArmPosePayload.show(player, ArmPose.KINETIC_SLAM, 12);
            TelekinesisService.soundAt(state.level, target.position(), SoundEvents.BREEZE_SHOOT, 1.2F, .45F);
        }
        if (fall.age < SKYFALL_IMPACT) return;
        state.skyfall = null;
        int tier = tier(player);
        // The last step of the drop lands on the impact tick: set the body down where it stood.
        Vec3 landed = SpeedService.swept(target, fall.drop.subtract(target.position()));
        TelekinesisService.teleport(target, landed);
        target.resetFallDistance();
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().playerAttack(player), 12 + tier);
        TelekinesisService.setVelocity(target, new Vec3(0, -.3, 0));
        for (LivingEntity near : enemies(player, fall.drop.add(0, .5, 0), SKYFALL_RADIUS)) {
            if (near == target) continue;
            near.invulnerableTime = 0;
            near.hurt(player.damageSources().playerAttack(player), 6 + tier * .5F);
            Vec3 out = near.position().subtract(fall.drop).multiply(1, 0, 1);
            out = out.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : out.normalize();
            TelekinesisService.setVelocity(near, out.scale(1.1).add(0, .55, 0));
        }
        CameraShakePayload.sendAround(state.level, fall.drop, 28, 1, 14);
        TelekinesisService.soundAt(state.level, fall.drop, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, .7F);
        TelekinesisService.soundAt(state.level, fall.drop, SoundEvents.ANVIL_LAND, 1, .5F);
    }

    // ------------------------------------------------------------------ Levitation

    private static boolean step(ServerPlayer player, boolean reverse) {
        State state = state(player);
        state.grounding = has(player, "2_0") ? 100 : 0;
        vault(player, state, reverse);
        state.secondStep = has(player, "2_2") ? 40 : 0;
        return true;
    }

    /** Vector Step: your own far hand, behind you, throws you forward and up (Shift: back). */
    private static void vault(ServerPlayer player, State state, boolean reverse) {
        Vec3 heading = player.getLookAngle().multiply(1, 0, 1);
        heading = heading.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : heading.normalize();
        // Force acts on the actual player; ordinary movement collision remains authoritative.
        move(player, heading.scale(reverse ? -.65 : .65).add(0, .65, 0));
        player.resetFallDistance();
        state.step = 12;
        show(player.serverLevel(), Piece.KINETIC_STEP, player.position(), player.position().add(heading.scale(reverse ? -1 : 1)), 1, player, player.getId());
        ArmPosePayload.show(player, ArmPose.KINETIC_RELEASE, 12);
        TelekinesisService.sound(player, SoundEvents.BREEZE_JUMP, .8F, 1.1F);
    }

    public static boolean levitating(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state != null && state.levitate != null;
    }

    /** Levitate: you lift yourself on the far hand and hover, drifting where you steer, for as long as you pay. */
    private static boolean levitate(ServerPlayer player) {
        State state = state(player);
        var attribute = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (attribute == null) return false;
        state.levitate = new Levitate(player.getAbilities().flying, player.getAbilities().getFlyingSpeed());
        if (!attribute.hasModifier(LEVITATE_FLIGHT))
            attribute.addTransientModifier(new AttributeModifier(LEVITATE_FLIGHT, 1, AttributeModifier.Operation.ADD_VALUE));
        player.getAbilities().flying = true;
        player.getAbilities().setFlyingSpeed(LEVITATE_SPEED);
        player.onUpdateAbilities();
        move(player, new Vec3(player.getDeltaMovement().x, .45, player.getDeltaMovement().z));
        player.resetFallDistance();
        StaminaDrain.start(player, LEVITATE, PowerBalance.drain(LEVITATE, 5), false, () -> {
            State s = STATES.get(player.getUUID());
            if (s != null && s.levitate != null) stopLevitate(player, s);
        });
        show(player.serverLevel(), Piece.KINETIC_LEVITATE, player.position(), player.position().add(0, 1, 0), 1, player, player.getId());
        ArmPosePayload.show(player, ArmPose.KINETIC_LEVITATE, 20);
        TelekinesisService.sound(player, SoundEvents.BEACON_ACTIVATE, .5F, 1.6F);
        return true;
    }

    private static void tickLevitate(ServerPlayer player, State state) {
        Levitate lev = state.levitate;
        lev.age++;
        player.resetFallDistance();
        if (!player.getAbilities().flying) { player.getAbilities().flying = true; player.onUpdateAbilities(); }
        // The hand only reaches so far: above the height cap it lets you sink back to it.
        BlockHitResult floor = state.level.clip(new ClipContext(player.position(), player.position().add(0, -LEVITATE_HEIGHT - 4, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        double height = floor.getType() == HitResult.Type.MISS ? LEVITATE_HEIGHT + 4 : player.getY() - floor.getLocation().y;
        if (height > LEVITATE_HEIGHT) move(player, new Vec3(player.getDeltaMovement().x, -.18, player.getDeltaMovement().z));
        // Loose things round you float up with you.
        if (lev.age % 4 == 0) for (ItemEntity item : state.level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4),
                i -> i.getY() < player.getY() + 1.2 && !TelekinesisService.isHeld(i)))
            item.setDeltaMovement(item.getDeltaMovement().multiply(.6, 0, .6).add(0, .12, 0));
        if (has(player, "wider_bubble")) for (LivingEntity friend : state.level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(RAISE_RADIUS), f -> f != player && TelekinesisService.friendly(player, f) && f.isAlive())) {
            friend.resetFallDistance();
            friend.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false));
            if (friend.getY() < player.getY() - .3) TelekinesisService.setVelocity(friend, new Vec3(friend.getDeltaMovement().x * .8, .22, friend.getDeltaMovement().z * .8));
            if (lev.age % 10 == 0) show(state.level, Piece.KINETIC_LEVITATE, friend.position(), friend.position().add(0, 1, 0), .8F, friend, player.getId());
        }
        if (has(player, "bastion") && lev.age % 10 == 1) TelekinesisService.stillAir(player, 15);
        if (lev.age % 10 == 0) {
            show(state.level, Piece.KINETIC_LEVITATE, player.position(), player.position().add(0, 1, 0), 1, player, player.getId());
            ArmPosePayload.show(player, ArmPose.KINETIC_LEVITATE, 15);
        }
    }

    private static void stopLevitate(ServerPlayer player, State state) {
        Levitate lev = state.levitate;
        state.levitate = null;
        StaminaDrain.stop(player, LEVITATE);
        var attribute = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (attribute != null) attribute.removeModifier(LEVITATE_FLIGHT);
        player.getAbilities().setFlyingSpeed(lev == null ? .05F : lev.priorSpeed);
        player.getAbilities().flying = lev != null && lev.priorFlying && player.mayFly();
        player.onUpdateAbilities();
        player.resetFallDistance();
        if (player.isAlive()) player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
        TelekinesisService.cancelBulletStopIfPassive(player);
        ArmPosePayload.clear(player);
    }

    /** Grounding's stored landing, used by a fall. */
    public static boolean cushion(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        if (state == null || state.grounding <= 0 || !eligible(player)) return false;
        state.grounding = 0;
        player.resetFallDistance();
        show(player.serverLevel(), Piece.KINETIC_CUSHION, player.position(), player.position().add(0, 1, 0), 1, player, player.getId());
        return true;
    }

    /**
     * Soft Landing's catch: a telekinetic within reach who sees a friend (or themself) hit the ground after a long fall
     * puts a palm under them. Once every twenty seconds per telekinetic.
     */
    public static boolean catchFall(LivingEntity fallen, float distance) {
        if (distance < CATCH_FALL || !(fallen.level() instanceof ServerLevel level)) return false;
        return catchFall(fallen, distance, level.getEntitiesOfClass(ServerPlayer.class, fallen.getBoundingBox().inflate(CATCH_REACH)));
    }

    /** The catch among these telekinetics (tests pass their own, since a test player isn't in the level). */
    static boolean catchFall(LivingEntity fallen, float distance, List<ServerPlayer> catchers) {
        if (distance < CATCH_FALL || !(fallen.level() instanceof ServerLevel level)) return false;
        for (ServerPlayer catcher : catchers) {
            if (!eligible(catcher) || !has(catcher, "0_0") || !TelekinesisService.friendly(catcher, fallen)
                    || catcher.distanceToSqr(fallen) > CATCH_REACH * CATCH_REACH
                    || catcher != fallen && !catcher.hasLineOfSight(fallen)) continue;
            State state = state(catcher);
            if (state.age < state.nextCatch) continue;
            state.nextCatch = state.age + CATCH_COOLDOWN;
            fallen.resetFallDistance();
            show(level, Piece.KINETIC_CUSHION, fallen.position(), fallen.position().add(0, 1, 0),
                    handScale(fallen.getBbWidth(), fallen.getBbHeight()), fallen, catcher.getId());
            TelekinesisService.soundAt(level, fallen.position(), SoundEvents.WOOL_PLACE, 1, .6F);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ Beckon (was Remote Hand)

    public static Vec3 aimPoint(ServerPlayer player, double range) {
        // This service's ranges are actual world distances, while Targeting accepts base power reach.
        LivingEntity aimed = Targeting.livingEntityInSight(player,
                range / AbilityRanges.ranged(1.0), t -> TelekinesisService.hostile(player, t));
        return aimed == null ? blockAim(player, range).getLocation() : aimed.getBoundingBox().getCenter();
    }

    private static BlockHitResult blockAim(ServerPlayer player, double range) {
        return player.serverLevel().clip(new ClipContext(player.getEyePosition(),
                player.getEyePosition().add(player.getLookAngle().scale(range)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    }

    private static double beckonRange(ServerPlayer player) { return has(player, "long_reach") ? 48 : 24; }

    private static ItemEntity remoteTarget(ServerPlayer player) {
        Vec3 eye = player.getEyePosition(), end = blockAim(player, beckonRange(player)).getLocation();
        return player.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(eye, end).inflate(.75),
                        item -> item.isAlive() && !item.hasPickUpDelay()
                                && item.getBoundingBox().inflate(.65).clip(eye, end).isPresent())
                .stream().min(Comparator.comparingDouble(item -> item.distanceToSqr(player))).orElse(null);
    }

    private static boolean actionableBlock(ServerPlayer player) {
        BlockHitResult hit = blockAim(player, beckonRange(player));
        if (hit.getType() != HitResult.Type.BLOCK || !player.level().mayInteract(player, hit.getBlockPos())
                || !WorldMutationPolicy.canEdit(player, player.serverLevel(), hit.getBlockPos())) return false;
        Block block = player.level().getBlockState(hit.getBlockPos()).getBlock();
        return block instanceof LeverBlock || block instanceof ButtonBlock || block instanceof DoorBlock
                || block instanceof TrapDoorBlock || block instanceof FenceGateBlock || block instanceof BellBlock
                || has(player, "retrieve") && player.level().getBlockEntity(hit.getBlockPos()) instanceof Container container
                && (!(container instanceof BaseContainerBlockEntity locked) || locked.canOpen(player))
                && !container.isEmpty();
    }

    /** Beckon: a small far hand works a control, or carries a loose item (or a stack from a chest) home. */
    private static boolean remoteHand(ServerPlayer player) {
        State state = state(player);
        if (state.retrieving.size() >= 8) return false;
        ItemEntity item = remoteTarget(player);
        if (item == null) {
            if (!actionableBlock(player)) return false;
            BlockHitResult hit = blockAim(player, beckonRange(player));
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
                show(player.serverLevel(), Piece.KINETIC_BECKON, hit.getLocation(), TelekinesisService.hand(player), 1, null, player.getId());
                ArmPosePayload.show(player, ArmPose.KINETIC_BECKON, 12);
                return true;
            }
        }
        if (item == null) return false;
        state.retrieving.add(new Pull(item, state.age + 100));
        show(player.serverLevel(), Piece.KINETIC_BECKON, item.position(), TelekinesisService.hand(player), 2, item, player.getId());
        ArmPosePayload.show(player, ArmPose.KINETIC_BECKON, 16);
        return true;
    }

    // ------------------------------------------------------------------ Shared

    /** Is this body held by one of the tree's techniques (pinned, juggled or hauled)? */
    public static boolean holds(Entity entity) {
        for (State state : STATES.values()) {
            if (state.skyfall != null && state.skyfall.target == entity) return true;
            for (Pinned pin : state.pins) if (pin.target == entity || pin.carried.contains(entity)) return true;
            for (Juggled j : state.juggled) if (j.target == entity) return true;
        }
        return false;
    }

    public static void tick(MinecraftServer server) {
        for (State state : List.copyOf(STATES.values())) {
            ServerPlayer player = state.owner;
            if (!eligible(player) || player.serverLevel() != state.level) { forget(player); continue; }
            state.age++;
            if (state.grounding > 0) state.grounding--;
            if (state.secondStep > 0) state.secondStep--;
            if (state.step > 0) {
                state.step--;
                player.resetFallDistance();
            }
            if (state.well != null) {
                Well well = state.well;
                if (--well.remaining <= 0 || player.position().distanceToSqr(well.center) > 48 * 48) state.well = null;
                else {
                    for (LivingEntity target : enemies(player, well.center, ROUND_UP_RADIUS)) {
                        if (TelekinesisService.isHeld(target)) continue;
                        Vec3 delta = well.center.subtract(target.getBoundingBox().getCenter());
                        Vec3 pull = delta.lengthSqr() < .04 ? Vec3.ZERO : delta.normalize().scale(Math.min(.32, delta.length() * .14));
                        move(target, target.getDeltaMovement().scale(.65).add(pull));
                        target.resetFallDistance();
                    }
                    if (state.age % 10 == 0) show(state.level, Piece.KINETIC_ROUND_UP, well.center, well.center.add(0, 1, 0), (float) ROUND_UP_RADIUS, null, player.getId());
                }
            }
            if (!state.pins.isEmpty()) tickPins(player, state);
            if (!state.juggled.isEmpty()) tickJuggle(player, state);
            if (state.skyfall != null) tickSkyfall(player, state);
            if (state.levitate != null) tickLevitate(player, state);
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
            }
            if (state.idle() && state.age > state.nextCatch && state.age > 240) STATES.remove(player.getUUID());
        }
    }

    public static boolean hostile(ServerPlayer player, LivingEntity target) {
        return TelekinesisService.hostile(player, target);
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

    private static int tier(ServerPlayer player) { return Math.clamp(PlayerProgressionService.getLevel(player), 1, 5); }

    public static void move(Entity entity, Vec3 velocity) { TelekinesisService.setVelocity(entity, velocity); }
    private static State state(ServerPlayer player) { return STATES.computeIfAbsent(player.getUUID(), id -> new State(player)); }

    public static void forget(ServerPlayer player) {
        State state = STATES.remove(player.getUUID());
        if (state != null && state.levitate != null) stopLevitate(player, state);
    }

    public static void clear() {
        for (State state : List.copyOf(STATES.values())) forget(state.owner);
        STATES.clear();
    }

    /** What the admin console shows about a telekinetic's techniques. */
    public static String status(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        if (state == null) return "idle";
        List<String> parts = new ArrayList<>();
        if (state.levitate != null) parts.add("levitating " + state.levitate.age / 20 + "s");
        if (!state.pins.isEmpty()) parts.add(state.pins.size() + " pinned");
        if (!state.juggled.isEmpty()) parts.add(state.juggled.size() + " juggled");
        if (state.skyfall != null) parts.add("Skyfall " + state.skyfall.age + "/" + SKYFALL_IMPACT);
        if (state.well != null) parts.add("Round Up " + state.well.remaining / 20 + "s");
        return parts.isEmpty() ? "idle" : String.join(", ", parts);
    }

    private static final class State {
        final ServerPlayer owner;
        final ServerLevel level;
        final List<Pull> retrieving = new ArrayList<>();
        final List<Pinned> pins = new ArrayList<>();
        final List<Juggled> juggled = new ArrayList<>();
        Well well;
        Levitate levitate;
        Skyfall skyfall;
        int age, step, grounding, secondStep, nextCatch;
        State(ServerPlayer player) { owner = player; level = player.serverLevel(); }
        boolean idle() {
            return well == null && levitate == null && skyfall == null && pins.isEmpty() && juggled.isEmpty()
                    && step <= 0 && grounding <= 0 && secondStep <= 0 && retrieving.isEmpty();
        }
    }
    private record Pull(ItemEntity item, int until) {}
    private static final class Well {
        final Vec3 center;
        int remaining;
        Well(Vec3 center, int remaining) { this.center = center; this.remaining = remaining; }
    }
    private static final class Pinned {
        final LivingEntity target; final Vec3 direction; final List<LivingEntity> carried; final float damage;
        double remaining = PIN_DRIVE; boolean landed; Vec3 anchor, contact; int holdUntil = Integer.MAX_VALUE;
        Pinned(LivingEntity target, Vec3 direction, List<LivingEntity> carried, float damage) {
            this.target = target; this.direction = direction; this.carried = carried; this.damage = damage;
        }
        List<LivingEntity> all() { List<LivingEntity> out = new ArrayList<>(carried); out.add(target); return out; }
    }
    private static final class Juggled {
        final LivingEntity target; final double floor; int pops, next; boolean first = true;
        Juggled(LivingEntity target, int pops, int next, double floor) { this.target = target; this.pops = pops; this.next = next; this.floor = floor; }
    }
    private static final class Levitate {
        boolean priorFlying; final float priorSpeed; int age;
        Levitate(boolean priorFlying, float priorSpeed) { this.priorFlying = priorFlying; this.priorSpeed = priorSpeed; }
    }
    private static final class Skyfall {
        final LivingEntity target; final Vec3 drop; final double top; int age;
        Skyfall(LivingEntity target, Vec3 drop, double top) { this.target = target; this.drop = drop; this.top = top; }
    }
}
