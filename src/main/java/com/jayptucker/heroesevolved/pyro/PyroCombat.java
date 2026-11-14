package com.jayptucker.heroesevolved.pyro;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;

/**
 * Firebending: fighting with fire thrown from the fists and feet, and dodging on jets of flame.
 *
 * <p>An empty-handed left click throws the next strike of a combo: a jab from the lead fist, a cross from the rear
 * fist, then (from level 2) a roundhouse whose kick throws a crescent of fire. Crouching turns the click into a
 * spinning sweep kick. Double-tapping left or right is a Jet Step; back is a backflip whose feet kick an arc of fire
 * up in front (level 2; a back step before that). A blow that lands during a dodge's first ticks is evaded, and the
 * first one each dodge is a Counterflame: Heat, and a loaded rear fist whose next cross is a fireball.</p>
 *
 * <p>Strikes never place fire blocks, so they are safe to throw anywhere; the kit's casts still do.</p>
 */
public final class PyroCombat {
    public enum Dodge { LEFT, RIGHT, BACK }
    public enum Strike { JAB, CROSS, ROUNDHOUSE, SWEEP }

    /** Level from which the combo ends in a roundhouse, crouching sweeps, and a back dodge flips. */
    public static final int KICK_LEVEL = 2;
    /** A strike within this many ticks of the last carries the combo on; a slower one starts it again. */
    static final int COMBO_WINDOW = 18;
    /** Ticks before the next strike: after a jab or cross, after a roundhouse, after a sweep. */
    static final int JAB_GAP = 5, KICK_RECOVERY = 12, SWEEP_RECOVERY = 14;
    /** A dodge evades blows for EVADE_TICKS, cushions falls for CUSHION_TICKS, and a Counterflame stays loaded LOADED_TICKS. */
    static final int EVADE_TICKS = 8, CUSHION_TICKS = 40, LOADED_TICKS = 40, COUNTER_HEAT = 10;

    private static final class Fighter {
        int step;
        long lastStrike = -1000, readyAt = -1000, dodgeReady = -1000, loadedUntil = -1000;
    }
    private record Dash(ServerPlayer player, Vec3 push, double lift, long start, boolean flip, boolean flying) {
        int length() { return flip ? PyroGeometry.FLIP_SPIN : PyroGeometry.STEP_TICKS + 2; }
    }
    private static final class Evade { long until; boolean countered; }

    private static final Map<UUID, Fighter> FIGHTERS = new HashMap<>();
    private static final Map<UUID, Dash> DASHES = new HashMap<>();
    private static final Map<UUID, Evade> EVADES = new HashMap<>();
    private static final Map<UUID, Long> CUSHIONS = new HashMap<>();

    private PyroCombat() {}

    // ------------------------------------------------------------------
    // Who can fight, and their combo state
    // ------------------------------------------------------------------

    private static long now(ServerPlayer player) { return player.getServer().overworld().getGameTime(); }

    private static Fighter fighter(ServerPlayer player) { return FIGHTERS.computeIfAbsent(player.getUUID(), id -> new Fighter()); }

    /** Bending needs the power in hand, dry hands, and no held cast or dive already using the body. */
    static boolean canFight(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && PyroService.isPyrokinetic(player) && PowerRules.canAct(player)
                && !player.isInWaterOrBubble() && !PyroFlightService.isDiving(player) && !PyroService.isChanneling(player)
                && !PyroService.isChargingSpear(player) && !PyroService.isCharging(player);
    }

    public static boolean isDodging(ServerPlayer player) { return DASHES.containsKey(player.getUUID()); }

    public static boolean isEvading(ServerPlayer player) {
        Evade evade = EVADES.get(player.getUUID());
        return evade != null && now(player) <= evade.until;
    }

    public static boolean isLoaded(ServerPlayer player) {
        Fighter fighter = FIGHTERS.get(player.getUUID());
        return fighter != null && now(player) < fighter.loadedUntil;
    }

    /** The strike the next click throws, for tests and the HUD's sake. */
    public static Strike next(ServerPlayer player) {
        Fighter fighter = fighter(player);
        int level = PlayerProgressionService.getLevel(player);
        if (player.isCrouching() && level >= KICK_LEVEL) return Strike.SWEEP;
        int step = now(player) - fighter.lastStrike > COMBO_WINDOW ? 0 : fighter.step;
        return step == 2 && level >= KICK_LEVEL ? Strike.ROUNDHOUSE : step == 1 ? Strike.CROSS : Strike.JAB;
    }

    // ------------------------------------------------------------------
    // Strikes
    // ------------------------------------------------------------------

    /**
     * An empty-handed click. If the fire can't be thrown at all (no stamina, the power switched off), the click is
     * still a plain punch at whatever the crosshair was on, so a tired or dampened fighter isn't left swinging at air.
     */
    public static void strikeInput(ServerPlayer player, int targetId) {
        if (!player.getMainHandItem().isEmpty() || strike(player) >= 0) return;
        Entity target = targetId < 0 ? null : player.level().getEntity(targetId);
        if (target != null && target != player && target.isAlive() && target.isAttackable()
                && player.canInteractWithEntity(target, 1)) {
            player.attack(target);
            player.swing(InteractionHand.MAIN_HAND, true);
        }
    }

    /** Throws the next strike. Returns 1 if thrown, 0 if it's too soon, and -1 if no fire can be thrown at all. */
    public static int strike(ServerPlayer player) {
        if (!player.getMainHandItem().isEmpty() || !canFight(player)) return -1;
        long now = now(player);
        Fighter fighter = fighter(player);
        if (now < fighter.readyAt) return 0;
        Strike move = next(player);
        boolean loaded = now < fighter.loadedUntil && move != Strike.SWEEP;
        int cost = loaded ? 0 : move == Strike.JAB || move == Strike.CROSS
                ? COMMON.pyroStrikeEnergyCost.get() : COMMON.pyroKickEnergyCost.get();
        if (cost > 0) {
            if (!PlayerEnergyService.tryConsume(player, cost)) {
                player.displayClientMessage(Component.translatable("pyro.heroesevolved.too_tired"), true);
                return -1;
            }
            MasteryService.awardEnergySpent(player, cost);
        }
        int level = Math.max(1, PlayerProgressionService.getLevel(player));
        // A loaded fist always throws the fireball, even when the combo was due a kick.
        if (loaded && move == Strike.ROUNDHOUSE) move = Strike.CROSS;
        switch (move) {
            case JAB -> fist(player, level, false, loaded);
            case CROSS -> fist(player, level, true, loaded);
            case ROUNDHOUSE -> roundhouse(player, level);
            case SWEEP -> sweep(player, level);
        }
        if (loaded) fighter.loadedUntil = -1000;
        fighter.lastStrike = now;
        fighter.step = move == Strike.ROUNDHOUSE || move == Strike.SWEEP ? 0
                : (fighter.step + 1) % (level >= KICK_LEVEL ? 3 : 2);
        fighter.readyAt = now + switch (move) {
            case ROUNDHOUSE -> KICK_RECOVERY;
            case SWEEP -> SWEEP_RECOVERY;
            default -> JAB_GAP;
        };
        return 1;
    }

    /** A fist of fire along the aim: the first exposed enemy in a narrow tube. A loaded fist is a fireball. */
    private static void fist(ServerPlayer player, int level, boolean rear, boolean loaded) {
        ServerLevel world = player.serverLevel();
        Vec3 eye = player.getEyePosition(), aim = player.getLookAngle().normalize();
        var hit = world.clip(new ClipContext(eye, eye.add(aim.scale(PyroGeometry.JAB_REACH)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        double length = eye.distanceTo(hit.getLocation()), width = loaded ? .9 : .45, nearest = Double.MAX_VALUE;
        LivingEntity target = null;
        for (LivingEntity candidate : PyroService.enemies(player, eye, length + 1)) {
            Vec3 center = candidate.getBoundingBox().getCenter(), delta = center.subtract(eye);
            double along = delta.dot(aim), reach = width + candidate.getBbWidth() * .5;
            if (along < 0 || along > length + .3 || along >= nearest
                    || delta.subtract(aim.scale(along)).lengthSqr() > reach * reach
                    || !PyroService.exposed(player, eye, center)) continue;
            target = candidate;
            nearest = along;
        }
        boolean blue = PyroService.isBlue(player);
        Vec3 end = target != null ? target.getBoundingBox().getCenter() : hit.getLocation();
        if (target != null) {
            boolean burning = target.isOnFire();
            float damage = PyroService.scaled(player, 2 + .4F * level) * (loaded ? 2.2F : 1);
            PyroService.burn(player, target, damage, loaded ? 5 : 2);
            PyroService.push(target, aim.scale(loaded ? .75 : .22).add(0, loaded ? .26 : .06, 0));
            if (loaded && burning) {
                for (LivingEntity nearby : PyroService.enemies(player, target.position(), 2.5))
                    if (nearby != target && target.hasLineOfSight(nearby)) PyroService.burn(player, nearby, damage * .5F, 3);
                PyroGeometry.show(world, Piece.PYRO_BURST, target.position(), target.position(), 2.5, blue, null);
            }
            PyroService.addHeat(player, 2);
        } else if (hit.getType() == HitResult.Type.BLOCK && PyroService.quenching(world, hit.getBlockPos())) {
            PyroService.steam(player, world, hit.getLocation());
        }
        PyroGeometry.show(world, Piece.PYRO_JAB, PyroGeometry.hand(player, 1, rear), end,
                loaded ? 1 + PyroGeometry.BLAZING : 1, blue, player);
        ArmPosePayload.show(player, rear ? ArmPose.FIRE_CROSS : ArmPose.FIRE_JAB, 9);
        player.swing(InteractionHand.MAIN_HAND, false);
        if (loaded) {
            sound(player, SoundEvents.BLAZE_SHOOT, 1.1F, .78F);
            sound(player, SoundEvents.FIRECHARGE_USE, .9F, .9F);
            CameraShakePayload.sendAround(world, player.position(), 10, .16F, 5);
        } else {
            sound(player, SoundEvents.BLAZE_SHOOT, .5F, rear ? 1.5F : 1.72F);
        }
        PyroService.addHeat(player, 3);
    }

    /** The rear leg swung round at hip height throws a crescent of fire across everything in front. */
    private static void roundhouse(ServerPlayer player, int level) {
        ServerLevel world = player.serverLevel();
        Vec3 hip = PyroGeometry.hip(player, 1), forward = PyroGeometry.level(player.getLookAngle(), player.getYRot());
        float damage = PyroService.scaled(player, 3 + level);
        int struck = 0;
        for (LivingEntity target : PyroService.enemies(player, hip, PyroGeometry.KICK_REACH + 1)) {
            AABB box = target.getBoundingBox();
            if (!PyroGeometry.inCrescent(hip, forward, PyroGeometry.KICK_REACH + target.getBbWidth() * .5,
                    PyroGeometry.KICK_HALF_ANGLE, target.position())
                    || box.maxY < hip.y - 1.2 || box.minY > hip.y + 1.6
                    || !PyroService.exposed(player, hip, box.getCenter())) continue;
            PyroService.burn(player, target, damage, 4);
            Vec3 out = target.position().subtract(player.position()).multiply(1, 0, 1);
            out = out.lengthSqr() < 1e-4 ? forward : out.normalize();
            PyroService.push(target, out.scale(.75).add(forward.scale(.2)).add(0, .28, 0));
            if (++struck >= 8) break;
        }
        PyroService.fan(player, hip, PyroGeometry.KICK_REACH);
        PyroGeometry.show(world, Piece.PYRO_KICK, hip, hip.add(forward.scale(PyroGeometry.KICK_REACH)), 1,
                PyroService.isBlue(player), player);
        ArmPosePayload.show(player, ArmPose.FIRE_KICK, 12);
        sound(player, SoundEvents.BLAZE_SHOOT, .9F, .82F);
        sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, .9F, .7F);
        PyroService.addHeat(player, 6 + Math.min(4, struck * 2));
    }

    /** Dropped low, one leg swept through a full turn: a ring of fire along the ground trips everyone round you. */
    private static void sweep(ServerPlayer player, int level) {
        ServerLevel world = player.serverLevel();
        Vec3 feet = player.position();
        double radius = PyroGeometry.SWEEP_RADIUS;
        float damage = PyroService.scaled(player, 2.5F + .5F * level);
        int struck = 0;
        for (LivingEntity target : PyroService.enemies(player, feet, radius + 1)) {
            if (Math.abs(target.getY() - feet.y) > 1.5
                    || target.position().subtract(feet).horizontalDistance() > radius + target.getBbWidth() * .5
                    || !PyroService.exposed(player, feet.add(0, .4, 0), target.getBoundingBox().getCenter())) continue;
            PyroService.burn(player, target, damage, 3);
            Vec3 out = target.position().subtract(feet).multiply(1, 0, 1);
            out = out.lengthSqr() < 1e-4 ? PyroGeometry.level(player.getLookAngle(), player.getYRot()) : out.normalize();
            PyroService.push(target, out.scale(.6).add(0, .42, 0));
            if (++struck >= 12) break;
        }
        PyroService.fan(player, feet, radius);
        PyroGeometry.show(world, Piece.PYRO_SWEEP_KICK, feet, feet, radius, PyroService.isBlue(player), player);
        ArmPosePayload.show(player, ArmPose.FIRE_SWEEP_KICK, 14);
        sound(player, SoundEvents.BLAZE_SHOOT, .8F, .66F);
        sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1, .6F);
        PyroService.addHeat(player, 6 + Math.min(6, struck * 2));
    }

    // ------------------------------------------------------------------
    // Dodges
    // ------------------------------------------------------------------

    /** A double-tapped direction: a jet step to the side, or a back step that becomes a flip kick from level 2. */
    public static boolean dodge(ServerPlayer player, Dodge dodge) {
        UUID id = player.getUUID();
        if (!canFight(player) || player.isPassenger() || player.isFallFlying() || DASHES.containsKey(id)) return false;
        Fighter fighter = fighter(player);
        long now = now(player);
        if (now < fighter.dodgeReady) return false;
        int level = Math.max(1, PlayerProgressionService.getLevel(player));
        boolean flip = dodge == Dodge.BACK && level >= KICK_LEVEL;
        int cost = COMMON.pyroDodgeEnergyCost.get() + (flip ? 3 : 0);
        if (cost > 0) {
            if (!PlayerEnergyService.tryConsume(player, cost)) {
                player.displayClientMessage(Component.translatable("pyro.heroesevolved.too_tired"), true);
                return false;
            }
            MasteryService.awardEnergySpent(player, cost);
        }
        Vec3 forward = PyroGeometry.level(player.getLookAngle(), player.getYRot());
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 direction = switch (dodge) {
            case LEFT -> right.scale(-1);
            case RIGHT -> right;
            case BACK -> forward.scale(-1);
        };
        boolean flying = PyroFlightService.isFlying(player);
        double speed = flip ? .62 : flying ? 1.35 : 1.05;
        double lift = flip ? (flying ? .45 : .62) : flying ? 0 : player.onGround() ? .26 : .1;
        Dash dash = new Dash(player, direction.scale(speed), lift, now, flip, flying);
        DASHES.put(id, dash);
        Evade evade = new Evade();
        evade.until = now + EVADE_TICKS;
        EVADES.put(id, evade);
        CUSHIONS.put(id, now + CUSHION_TICKS);
        fighter.dodgeReady = now + COMMON.pyroDodgeCooldownTicks.get();
        player.resetFallDistance();
        push(dash, 0);

        boolean blue = PyroService.isBlue(player);
        Vec3 from = player.position();
        double travel = flip ? 4.5 : flying ? 6 : 4.8;
        PyroGeometry.show(player.serverLevel(), Piece.PYRO_JET_STEP, from, from.add(direction.scale(travel)),
                flip ? 2 : 1, blue, player);
        if (flip) flipKick(player, level, forward);
        ArmPosePayload.show(player, flip ? ArmPose.FIRE_FLIP : ArmPose.FIRE_STEP, flip ? PyroGeometry.FLIP_SPIN + 2 : 10);
        sound(player, SoundEvents.FIRECHARGE_USE, .7F, 1.55F);
        sound(player, SoundEvents.BLAZE_SHOOT, .45F, 1.9F);
        PyroService.addHeat(player, 4);
        return true;
    }

    /** The feet sweep up and over as the body flips back: an arc of fire rises through everything in front. */
    private static void flipKick(ServerPlayer player, int level, Vec3 forward) {
        Vec3 hip = PyroGeometry.hip(player, 1);
        float damage = PyroService.scaled(player, 3 + .6F * level);
        int struck = 0;
        for (LivingEntity target : PyroService.enemies(player, hip, PyroGeometry.FLIP_REACH + 1)) {
            AABB box = target.getBoundingBox();
            if (!PyroGeometry.inCrescent(hip, forward, PyroGeometry.FLIP_REACH + target.getBbWidth() * .5,
                    PyroGeometry.FLIP_HALF_ANGLE, target.position())
                    || box.maxY < hip.y - 1.4 || box.minY > hip.y + 3
                    || !PyroService.exposed(player, hip, box.getCenter())) continue;
            PyroService.burn(player, target, damage, 3);
            PyroService.push(target, forward.scale(.3).add(0, .72, 0));
            if (++struck >= 8) break;
        }
        PyroGeometry.show(player.serverLevel(), Piece.PYRO_FLIP, hip, hip.add(forward.scale(PyroGeometry.FLIP_REACH)), 1,
                PyroService.isBlue(player), player);
        sound(player, SoundEvents.BLAZE_SHOOT, .85F, 1.05F);
        PyroService.addHeat(player, Math.min(6, struck * 2));
    }

    /** Re-sends the dash each tick so ground friction doesn't eat it; the hop's fall is worked out the client's way. */
    private static void push(Dash dash, int age) {
        ServerPlayer player = dash.player;
        if (dash.flip) {
            if (age == 0) PyroService.push(player, dash.push.add(0, dash.lift, 0));
            return;
        }
        if (age >= PyroGeometry.STEP_TICKS) return;
        double vertical = dash.lift;
        if (!dash.flying) for (int i = 0; i < age; i++) vertical = (vertical - .08) * .98;
        PyroService.push(player, dash.push.add(0, dash.flying ? 0 : vertical, 0));
    }

    /**
     * Called for every blow a pyrokinetic player takes. A blow from a creature or a shot during a dodge's first
     * ticks misses; the first one each dodge loads a Counterflame.
     */
    public static boolean evade(ServerPlayer player, DamageSource source) {
        Evade evade = EVADES.get(player.getUUID());
        if (evade == null || now(player) > evade.until || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || source.getEntity() == player || source.getEntity() == null && source.getDirectEntity() == null) return false;
        if (!evade.countered) {
            evade.countered = true;
            counterflame(player);
        }
        return true;
    }

    private static void counterflame(ServerPlayer player) {
        Fighter fighter = fighter(player);
        fighter.loadedUntil = now(player) + LOADED_TICKS;
        // The rear fist carries the fire, so the counter is always the cross.
        fighter.step = 1;
        fighter.lastStrike = now(player);
        fighter.readyAt = Math.min(fighter.readyAt, now(player));
        PyroService.addHeat(player, COUNTER_HEAT);
        PyroGeometry.show(player.serverLevel(), Piece.PYRO_COUNTER, player.position(), player.position(), 1,
                PyroService.isBlue(player), player);
        sound(player, SoundEvents.BLAZE_SHOOT, .8F, 1.95F);
        sound(player, SoundEvents.FIRE_EXTINGUISH, .5F, 1.7F);
        player.displayClientMessage(Component.translatable("pyro.heroesevolved.counterflame")
                .withStyle(ChatFormatting.GOLD), true);
    }

    /** Falls are cushioned for a moment after any dodge, so a flip off a ledge lands on its jets. */
    public static boolean cushion(ServerPlayer player) {
        Long until = CUSHIONS.get(player.getUUID());
        return until != null && now(player) <= until;
    }

    // ------------------------------------------------------------------
    // Tick and cleanup
    // ------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (Dash dash : List.copyOf(DASHES.values())) {
            ServerPlayer player = dash.player;
            int age = (int) (now - dash.start);
            if (player.isRemoved() || !player.isAlive() || !PowerRules.canAct(player) || age >= dash.length()
                    || age > 0 && !dash.flip && player.horizontalCollision) {
                DASHES.remove(player.getUUID());
                continue;
            }
            push(dash, age);
            player.resetFallDistance();
        }
        EVADES.values().removeIf(evade -> now > evade.until);
        CUSHIONS.values().removeIf(until -> now > until);
        if (now % 5 == 0) {
            for (var entry : List.copyOf(FIGHTERS.entrySet())) {
                if (now >= entry.getValue().loadedUntil) continue;
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player == null) continue;
                // The loaded fist burns brighter until it's thrown.
                Vec3 fist = PyroGeometry.castingHand(player, 1);
                PyroGeometry.show(player.serverLevel(), Piece.PYRO_FORGE, fist, fist, .7, PyroService.isBlue(player), player);
            }
        }
    }

    private static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void forget(ServerPlayer player) {
        UUID id = player.getUUID();
        FIGHTERS.remove(id);
        DASHES.remove(id);
        EVADES.remove(id);
        CUSHIONS.remove(id);
    }

    public static void clear() {
        FIGHTERS.clear();
        DASHES.clear();
        EVADES.clear();
        CUSHIONS.clear();
    }
}
