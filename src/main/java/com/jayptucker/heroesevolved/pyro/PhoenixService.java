package com.jayptucker.heroesevolved.pyro;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Phoenix (Pyrokinesis, Phoenix signature). The fire takes wing.
 *
 * <p>A firebird tears itself out of the pyrokinetic's chest and climbs, wings spread. It wheels once over
 * their head, then hunts: it dives through up to three enemies in turn (and burns whatever else it
 * passes on the way), lighting real fire where each one stood. When the hunt is over it comes home and
 * folds back into the one who made it, closing their wounds and leaving them fireproof for a while.
 *
 * <p>The bird is not an entity: it is a server-side point with a heading, drawn on clients from one small
 * description a tick. It never breaks blocks; fire is placed only where Pyrokinesis may place fire.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class PhoenixService {
    public static final int LIFETIME = 200, MAX_ACTIVE = 16, RISE = 14, SEEK_RANGE = 24;
    private static final double HUNT_SPEED = 0.95, HOME_SPEED = 1.05, STRIKE_REACH = 1.7, GRAZE_REACH = 1.3;

    enum Phase { RISING, CIRCLING, HUNTING, RETURNING }

    private static final class Bird {
        final ServerPlayer owner;
        final ServerLevel level;
        final int powerLevel, strikes;
        final long born;
        final Set<UUID> struck = new HashSet<>(), grazed = new HashSet<>();
        Vec3 position, velocity;
        Phase phase = Phase.RISING;
        LivingEntity target;
        int phaseTicks, hits;
        Bird(ServerPlayer owner, int powerLevel) {
            this.owner = owner; this.level = owner.serverLevel(); this.powerLevel = powerLevel;
            this.strikes = powerLevel >= 5 ? 4 : 3;
            this.born = level.getGameTime();
            this.position = owner.position().add(0, 1.2, 0);
            Vec3 look = owner.getLookAngle().multiply(1, 0, 1);
            this.velocity = (look.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : look.normalize()).scale(0.15).add(0, 0.35, 0);
        }
        int age() { return (int) (level.getGameTime() - born); }
    }
    private static final Map<UUID, Bird> BIRDS = new HashMap<>();

    private PhoenixService() {}

    public static boolean active(ServerPlayer player) { return BIRDS.containsKey(player.getUUID()); }
    public static int count() { return BIRDS.size(); }

    public static boolean activate(ServerPlayer player, int powerLevel) {
        if (BIRDS.containsKey(player.getUUID()) || BIRDS.size() >= MAX_ACTIVE || !PowerRules.canAct(player)) return false;
        if (player.isInWaterOrBubble()) {
            player.displayClientMessage(Component.literal("The water smothers it before it can take wing.").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        Bird bird = new Bird(player, powerLevel);
        BIRDS.put(player.getUUID(), bird);
        ServerLevel level = bird.level;
        AbilityEffects.send(level, Kind.PYRO_PHOENIX_STRIKE, bird.position, bird.position.add(0, 1, 0), 1.4);
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2F, 0.6F);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.0F, 1.5F);
        level.playSound(null, player.blockPosition(), SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS, 0.9F, 1.6F);
        ArmPosePayload.show(player, ArmPose.ARMS_RAISED, 16);
        return true;
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }

    public static void tick(MinecraftServer server) {
        if (BIRDS.isEmpty()) return;
        for (Bird bird : List.copyOf(BIRDS.values())) {
            if (bird.level.getServer() != server) continue;
            ServerPlayer owner = bird.owner;
            if (owner.isRemoved() || !owner.isAlive() || owner.serverLevel() != bird.level || !PowerRules.canAct(owner)
                    || bird.age() >= LIFETIME || !bird.level.isLoaded(BlockPos.containing(bird.position))
                    || bird.level.getFluidState(BlockPos.containing(bird.position)).is(net.minecraft.tags.FluidTags.WATER)) {
                BIRDS.remove(owner.getUUID());
                dissolve(bird, false);
                continue;
            }
            step(bird);
            if (bird.phase == null) { BIRDS.remove(owner.getUUID()); continue; }
            // One small description a tick: where it is, which way it faces, and how hard it is diving.
            double dive = bird.phase == Phase.HUNTING ? 1.4 : bird.phase == Phase.RETURNING ? 1.15 : 1.0;
            Vec3 heading = bird.velocity.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : bird.velocity.normalize();
            AbilityEffects.send(bird.level, Kind.PYRO_PHOENIX, bird.position, bird.position.add(heading), dive);
            if (bird.age() % 8 == 0) bird.level.playSound(null, bird.position.x, bird.position.y, bird.position.z,
                    SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.5F, 1.6F + bird.level.getRandom().nextFloat() * 0.3F);
            if (bird.age() % 5 == 0) bird.level.playSound(null, bird.position.x, bird.position.y, bird.position.z,
                    SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.8F, 1.2F);
        }
    }

    private static void step(Bird bird) {
        ServerPlayer owner = bird.owner;
        bird.phaseTicks++;
        Vec3 home = owner.position().add(0, 1.2, 0);
        switch (bird.phase) {
            case RISING -> {
                bird.velocity = bird.velocity.scale(0.9).add(0, 0.04, 0);
                if (bird.phaseTicks >= RISE) enter(bird, Phase.CIRCLING);
            }
            case CIRCLING -> {
                // Wheel above their head, looking for prey.
                double angle = bird.phaseTicks * 0.22;
                Vec3 orbit = owner.position().add(Math.cos(angle) * 3.2, 4.2, Math.sin(angle) * 3.2);
                steer(bird, orbit, 0.7, 0.3);
                if (bird.phaseTicks % 4 == 0) {
                    LivingEntity prey = prey(bird);
                    if (prey != null) { bird.target = prey; enter(bird, Phase.HUNTING); swoop(bird); }
                }
                if (bird.phaseTicks > (bird.hits == 0 ? 50 : 16)) enter(bird, Phase.RETURNING);
            }
            case HUNTING -> {
                LivingEntity target = bird.target;
                if (target == null || !target.isAlive() || target.level() != bird.level || bird.phaseTicks > 50) {
                    bird.target = null; enter(bird, Phase.CIRCLING); break;
                }
                Vec3 aim = target.getBoundingBox().getCenter();
                steer(bird, aim, HUNT_SPEED, 0.32);
                graze(bird);
                if (bird.position.distanceTo(aim) <= STRIKE_REACH) strike(bird, target);
            }
            case RETURNING -> {
                steer(bird, home, HOME_SPEED, 0.35);
                if (bird.position.distanceTo(home) <= 1.4) { dissolve(bird, true); bird.phase = null; return; }
            }
        }
        bird.position = bird.position.add(bird.velocity);
    }

    private static void enter(Bird bird, Phase phase) { bird.phase = phase; bird.phaseTicks = 0; }

    /** Turns toward a point: {@code agility} is how much of the turn happens each tick. */
    private static void steer(Bird bird, Vec3 point, double speed, double agility) {
        Vec3 toward = point.subtract(bird.position);
        Vec3 desired = toward.lengthSqr() < 1.0E-4 ? Vec3.ZERO : toward.normalize().scale(Math.min(speed, toward.length()));
        bird.velocity = bird.velocity.add(desired.subtract(bird.velocity).scale(agility));
    }

    /** The nearest enemy it can see that it has not struck yet. */
    private static LivingEntity prey(Bird bird) {
        if (bird.struck.size() >= bird.strikes) return null;
        return bird.level.getEntitiesOfClass(LivingEntity.class, new AABB(bird.position, bird.position).inflate(SEEK_RANGE),
                        target -> ExpansionSkills.hostile(bird.owner, target) && !bird.struck.contains(target.getUUID())
                                && !target.fireImmune() && target.distanceToSqr(bird.owner) <= SEEK_RANGE * SEEK_RANGE && sees(bird, target))
                .stream().min(Comparator.comparingDouble(target -> target.distanceToSqr(bird.position))).orElse(null);
    }

    private static boolean sees(Bird bird, LivingEntity target) {
        return bird.level.clip(new ClipContext(bird.position, target.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, bird.owner)).getType() == HitResult.Type.MISS;
    }

    private static void swoop(Bird bird) {
        bird.level.playSound(null, bird.position.x, bird.position.y, bird.position.z, SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS, 1.1F, 1.3F);
    }

    /** Anything else it brushes past on a dive gets singed, once. */
    private static void graze(Bird bird) {
        for (LivingEntity passed : bird.level.getEntitiesOfClass(LivingEntity.class, new AABB(bird.position, bird.position).inflate(GRAZE_REACH),
                target -> target != bird.target && ExpansionSkills.hostile(bird.owner, target) && !bird.grazed.contains(target.getUUID()))) {
            bird.grazed.add(passed.getUUID());
            PyroService.burn(bird.owner, passed, 3.0F, 3);
        }
    }

    private static void strike(Bird bird, LivingEntity target) {
        bird.struck.add(target.getUUID());
        bird.hits++;
        PyroService.burn(bird.owner, target, 7.0F + bird.powerLevel, 6);
        Vec3 shove = bird.velocity.multiply(1, 0, 1);
        if (shove.lengthSqr() > 1.0E-4 && target.isAlive()) {
            target.setDeltaMovement(target.getDeltaMovement().add(shove.normalize().scale(0.8)).add(0, 0.35, 0));
            target.hurtMarked = true;
            if (target instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
        }
        PyroService.ignite(bird.owner, bird.level, target.blockPosition());
        Vec3 at = target.getBoundingBox().getCenter();
        AbilityEffects.send(bird.level, Kind.PYRO_PHOENIX_STRIKE, at, at.add(bird.velocity), 2.2);
        CameraShakePayload.sendAround(bird.level, at, 12, 0.7F, 6);
        bird.level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 0.8F);
        bird.level.playSound(null, at.x, at.y, at.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.4F);
        // Pull up out of the dive and look for the next one.
        bird.velocity = bird.velocity.multiply(0.4, 0, 0.4).add(0, 0.7, 0);
        bird.target = null;
        LivingEntity next = prey(bird);
        if (next != null) { bird.target = next; enter(bird, Phase.HUNTING); swoop(bird); }
        else enter(bird, Phase.RETURNING);
    }

    /** It folds back into its maker (or, cut short, just burns out where it is). */
    private static void dissolve(Bird bird, boolean home) {
        ServerPlayer owner = bird.owner;
        AbilityEffects.send(bird.level, Kind.PYRO_PHOENIX_STRIKE, bird.position, bird.position.add(0, 1, 0), home ? 1.6 : 1.0);
        bird.level.playSound(null, bird.position.x, bird.position.y, bird.position.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 0.7F);
        if (!home || !owner.isAlive()) return;
        float healed = 2.0F + 2.0F * bird.hits;
        owner.heal(healed);
        owner.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
        owner.clearFire();
        bird.level.playSound(null, owner.blockPosition(), SoundEvents.BLAZE_AMBIENT, SoundSource.PLAYERS, 0.8F, 1.6F);
        owner.displayClientMessage(Component.literal(bird.hits == 0 ? "The Phoenix finds nothing to hunt and comes home."
                : "The Phoenix comes home: " + bird.hits + (bird.hits == 1 ? " strike" : " strikes") + ". It closes your wounds.")
                .withStyle(ChatFormatting.GOLD), true);
    }

    public static void forget(ServerPlayer player) { BIRDS.remove(player.getUUID()); }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void death(LivingDeathEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { BIRDS.clear(); }
}
