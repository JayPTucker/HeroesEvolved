package com.jayptucker.heroesevolved.phasing;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.speed.SpeedService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phasing, inspired by D.L. Hawkins. Every passage ends in a checked, open landing: you never
 * stop inside a block, never pass bedrock or barriers, and never cross protected ground. While
 * intangible, blows, projectiles, falls and blasts pass through you, and you cannot strike back.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class PhasingService {
    private record Intangible(ServerPlayer player, long until, boolean drains) {}
    private record Lock(LivingEntity target, ServerPlayer owner, Vec3 at, long until, boolean entombed) {}
    private static final Map<UUID, Intangible> INTANGIBLE = new HashMap<>();
    private static final Map<UUID, Long> WALKING = new HashMap<>();
    private static final Map<UUID, Lock> LOCKS = new HashMap<>();
    private static final long FOREVER = Long.MAX_VALUE;

    private PhasingService() {}

    private static long now(MinecraftServer server) { return server.overworld().getGameTime(); }

    public static boolean isPhasingOwner(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower()
                .filter(e -> e.getKey().equals(ModAbilities.PHASING_ID) && e.getValue().isUnlocked()).isPresent()
                && !EclipseService.arePowersSuppressed(player);
    }

    public static boolean isIntangible(Entity entity) { return INTANGIBLE.containsKey(entity.getUUID()); }

    // ------------------------------------------------------------------
    // Passage
    // ------------------------------------------------------------------

    /** A block a phaser may pass: not bedrock or barrier, and not on ground the player may not touch. */
    private static boolean passable(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        var state = level.getBlockState(pos);
        return state.isAir() || state.getDestroySpeed(level, pos) >= 0 && level.mayInteract(player, pos) && level.hasChunkAt(pos);
    }

    /**
     * The first open landing at least one solid block beyond along {@code direction}, within
     * {@code maximum} blocks, or null. Thin walls only count if something solid was actually passed.
     */
    public static Vec3 passage(ServerPlayer player, Vec3 direction, int maximum) {
        Vec3 start = player.position();
        boolean passedSolid = false;
        for (double d = 0.5; d <= maximum; d += 0.5) {
            Vec3 point = start.add(direction.scale(d));
            for (BlockPos pos : new BlockPos[] {BlockPos.containing(point), BlockPos.containing(point.add(0, 1, 0))})
                if (!passable(player, pos)) return null;
            if (!SpeedService.safe(player, point)) passedSolid = true;
            else if (passedSolid) return point;
        }
        return null;
    }

    private static void arrive(ServerPlayer player, Vec3 from, Vec3 to) {
        player.teleportTo(player.serverLevel(), to.x, to.y, to.z, player.getYRot(), player.getXRot());
        player.resetFallDistance();
        AbilityEffects.send(player.serverLevel(), Kind.TEMPORAL_FIELD, from, from.add(0, 1, 0), 0.8);
        AbilityEffects.send(player.serverLevel(), Kind.TEMPORAL_FIELD, to, to.add(0, 1, 0), 0.8);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.5F, 1.6F);
    }

    /** Friends within {@code radius} come along, each needing its own open landing beside you. */
    private static void carry(ServerPlayer player, Vec3 offset, double radius, int limit) {
        int carried = 0;
        for (LivingEntity friend : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                other -> other != player && ExpansionSkills.friendly(player, other) && !other.isPassenger())) {
            if (carried >= limit) break;
            Vec3 to = friend.position().add(offset);
            if (!SpeedService.safe(friend, to)) continue;
            friend.teleportTo(to.x, to.y, to.z);
            friend.resetFallDistance();
            carried++;
        }
    }

    /** Level 1, Phase Step: walk straight through the wall ahead. Shift brings one nearby friend. */
    public static boolean phaseStep(ServerPlayer player, int level, boolean modifier) {
        int reach = Math.min(8, 2 + level) + (SkillTreeService.has(player, "phasing_thicker_walls") ? 4 : 0);
        Vec3 direction = player.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 from = player.position(), to = passage(player, direction, reach);
        if (to == null) {
            player.displayClientMessage(Component.translatable("phasing.heroesevolved.no_passage"), true);
            return false;
        }
        if (modifier) carry(player, to.subtract(from), 2, 1);
        arrive(player, from, to);
        if (SkillTreeService.has(player, "phasing_ghost_network")) makeIntangible(player, 40, false);
        return true;
    }

    public static void makeIntangible(ServerPlayer player, int ticks, boolean drains) {
        INTANGIBLE.put(player.getUUID(), new Intangible(player, drains ? FOREVER : now(player.getServer()) + ticks, drains));
        AbilityEffects.send(player.serverLevel(), Kind.TEMPORAL_FIELD, player.position(), player.getEyePosition(), 1);
    }

    public static boolean solidify(ServerPlayer player) {
        Intangible removed = INTANGIBLE.remove(player.getUUID());
        if (removed == null) return false;
        eject(player);
        return true;
    }

    /** Level 1, Intangible: a toggle that drains stamina. */
    public static boolean toggleIntangible(ServerPlayer player) {
        if (solidify(player)) return true;
        makeIntangible(player, 0, true);
        player.displayClientMessage(Component.translatable("phasing.heroesevolved.intangible").withStyle(ChatFormatting.GRAY), true);
        return true;
    }

    /** Level 2, Phase Strike: reach inside a target. Ignores armor. */
    public static boolean phaseStrike(ServerPlayer player, int level) {
        LivingEntity target = Targeting.livingEntityInSight(player, 6, t -> ExpansionSkills.hostile(player, t));
        if (target == null) return false;
        target.hurt(player.damageSources().indirectMagic(player, player), 6 + level);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
        AbilityEffects.send(player.serverLevel(), Kind.PSYCHIC_IMPACT, player.getEyePosition(), target.getEyePosition(), 1);
        return true;
    }

    /** Level 3, Phase Drop: sink through the floor to the next open space, or rise with Shift. */
    public static boolean phaseDrop(ServerPlayer player, boolean rise, double range, double carryRadius) {
        Vec3 from = player.position(), to = passage(player, new Vec3(0, rise ? 1 : -1, 0), (int) range);
        if (to == null) {
            player.displayClientMessage(Component.translatable("phasing.heroesevolved.no_passage"), true);
            return false;
        }
        if (carryRadius > 0) carry(player, to.subtract(from), carryRadius, 3);
        arrive(player, from, to);
        return true;
    }

    /** Level 5, Phase Walk: eight seconds intangible, walking through any wall up to three blocks thick. */
    public static boolean phaseWalk(ServerPlayer player) {
        WALKING.put(player.getUUID(), now(player.getServer()) + 160);
        makeIntangible(player, 160, false);
        return true;
    }

    /** Never leaves a phaser inside a block: steps out to the nearest open space. */
    private static void eject(ServerPlayer player) {
        if (SpeedService.safe(player, player.position())) return;
        for (int r = 1; r <= 4; r++)
            for (int dy = 0; dy <= r; dy++)
                for (int dx = -r; dx <= r; dx++)
                    for (int dz = -r; dz <= r; dz++) {
                        Vec3 point = player.position().add(dx, dy, dz);
                        if (SpeedService.safe(player, point)) { player.teleportTo(point.x, point.y, point.z); return; }
                    }
    }

    public static void awaken(ServerPlayer player) {
        makeIntangible(player, 100, false);
        player.displayClientMessage(Component.translatable("phasing.heroesevolved.awakened").withStyle(ChatFormatting.GRAY), false);
    }

    // ------------------------------------------------------------------
    // Skill tree
    // ------------------------------------------------------------------

    public static boolean skill(AbilityUseContext c, int branch) {
        ServerPlayer p = c.player();
        switch (branch) {
            case 0 -> {
                LivingEntity target = Targeting.livingEntityInSight(p, 40, t -> ExpansionSkills.hostile(p, t));
                if (target == null) return false;
                target.hurt(p.damageSources().indirectMagic(p, p), 6 + c.abilityLevel());
                AbilityEffects.send(p.serverLevel(), Kind.PSYCHIC_IMPACT, p.getEyePosition(), target.getEyePosition(), 1);
            }
            case 1 -> p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 1));
            case 2 -> makeIntangible(p, 60, false);
            default -> { return false; }
        }
        return true;
    }

    public static boolean technique(AbilityUseContext c, String mechanic) {
        ServerPlayer p = c.player();
        switch (mechanic) {
            case "phase_carry" -> {
                int reach = Math.min(8, 2 + c.abilityLevel()) + (SkillTreeService.has(p, "phasing_thicker_walls") ? 4 : 0);
                Vec3 from = p.position(), to = passage(p, p.getLookAngle().multiply(1, 0, 1).normalize(), reach);
                if (to == null) { p.displayClientMessage(Component.translatable("phasing.heroesevolved.no_passage"), true); return false; }
                carry(p, to.subtract(from), 3, 3);
                arrive(p, from, to);
                return true;
            }
            case "phase_lock" -> {
                LivingEntity target = Targeting.livingEntityInSight(p, 16, t -> ExpansionSkills.hostile(p, t));
                if (target == null) return false;
                int ticks = SkillTreeService.has(p, "phasing_long_lock") ? 100 : 60;
                LOCKS.put(target.getUUID(), new Lock(target, p, target.position(), now(p.getServer()) + ticks, SkillTreeService.has(p, "phasing_entombed")));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9));
                AbilityEffects.send(p.serverLevel(), Kind.TEMPORAL_FIELD, target.position(), target.getEyePosition(), 1.2);
                return true;
            }
            case "phase_fall" -> {
                double range = SkillTreeService.has(p, "phasing_deep_fall") ? 32 : 16;
                return phaseDrop(p, c.modifierHeld(), range, 3);
            }
            default -> { return false; }
        }
    }

    // ------------------------------------------------------------------
    // Tick and events
    // ------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        long now = now(server);
        for (Intangible state : List.copyOf(INTANGIBLE.values())) {
            ServerPlayer player = state.player;
            if (!player.isAlive() || player.isRemoved() || now >= state.until) { solidify(player); continue; }
            if (state.drains) {
                if (!isPhasingOwner(player)) { solidify(player); continue; }
                boolean frugal = SkillTreeService.has(player, "phasing_untouchable");
                if (now % (frugal ? 10 : 5) == 0 && !PlayerEnergyService.tryConsume(player, 1)) {
                    solidify(player);
                    player.displayClientMessage(Component.translatable("phasing.heroesevolved.exhausted"), true);
                    continue;
                }
            }
            if (now % 10 == 0) player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                    player.getX(), player.getY() + 1, player.getZ(), 4, .3, .5, .3, 0);
        }
        for (var entry : List.copyOf(WALKING.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || now >= entry.getValue()) { WALKING.remove(entry.getKey()); if (player != null) eject(player); continue; }
            // Walking into a wall carries you through it.
            if (player.horizontalCollision && now % 4 == 0) {
                Vec3 heading = player.getLookAngle().multiply(1, 0, 1).normalize();
                Vec3 to = passage(player, heading, 4);
                if (to != null) arrive(player, player.position(), to);
            }
        }
        for (Lock lock : List.copyOf(LOCKS.values())) {
            LivingEntity target = lock.target;
            if (!target.isAlive() || now >= lock.until) { LOCKS.remove(target.getUUID()); continue; }
            target.teleportTo(lock.at.x, lock.at.y, lock.at.z);
            target.setDeltaMovement(Vec3.ZERO);
            if (lock.entombed && now % 20 == 0) target.hurt(lock.owner.damageSources().indirectMagic(lock.owner, lock.owner), 2);
        }
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void damage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // A phaser cannot suffocate in walls.
        if (event.getSource().is(DamageTypes.IN_WALL) && isPhasingOwner(player)) { event.setCanceled(true); return; }
        if (isIntangible(player) && (event.getSource().getDirectEntity() != null || event.getSource().is(DamageTypeTags.IS_FALL)
                || event.getSource().is(DamageTypeTags.IS_EXPLOSION) || event.getSource().is(DamageTypes.FALLING_BLOCK)))
            event.setCanceled(true);
    }

    /** Arrows and other projectiles fly straight through an intangible player. */
    @SubscribeEvent public static void projectile(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof ServerPlayer player && isIntangible(player))
            event.setCanceled(true);
    }

    @SubscribeEvent public static void attack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isIntangible(player)) event.setCanceled(true);
    }

    public static void forget(ServerPlayer player) {
        solidify(player);
        WALKING.remove(player.getUUID());
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }

    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
        LOCKS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { INTANGIBLE.clear(); WALKING.clear(); LOCKS.clear(); }

    static List<UUID> intangibleIds() { return new ArrayList<>(INTANGIBLE.keySet()); }
}
