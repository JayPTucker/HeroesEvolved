package com.jayptucker.heroesevolved.expansion;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import java.util.*;

/** Explicit reactions consume their setup and cannot recursively react. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class PowerReactions {
    private record Seam(ServerLevel level, BlockPos pos) {}
    private static final Map<Seam, Long> SEAMS = new LinkedHashMap<>();
    private static final Map<UUID, Long> REACTED = new HashMap<>();
    private static int depth;
    private PowerReactions() {}
    public static void resonate(ServerLevel level, BlockPos pos, long until) {
        SEAMS.entrySet().removeIf(e -> e.getValue() <= level.getServer().overworld().getGameTime());
        while (SEAMS.size() >= 128) SEAMS.remove(SEAMS.keySet().iterator().next());
        SEAMS.put(new Seam(level, pos.immutable()), until);
    }
    public static boolean resonating(ServerLevel level, BlockPos pos) {
        return SEAMS.getOrDefault(new Seam(level, pos), 0L) > level.getServer().overworld().getGameTime();
    }
    private static boolean consume(ServerPlayer p, LivingEntity t) {
        long now = p.getServer().overworld().getGameTime();
        REACTED.values().removeIf(until -> until <= now);
        if (depth != 0 || !CryoService.isFrozen(t) || !ExpansionSkills.hostile(p, t) || REACTED.getOrDefault(t.getUUID(), 0L) > now) return false;
        REACTED.put(t.getUUID(), now + 40); CryoService.releaseEntity(t); return true;
    }
    public static boolean quake(ServerPlayer p, LivingEntity t) {
        if (!consume(p, t)) return false;
        depth++;
        try {
            var direction = t.position().subtract(p.position()).normalize();
            for (LivingEntity other : ExpansionSkills.enemies(p, t.position(), 5).stream().limit(6).toList()) {
                if (other == t || other.position().subtract(t.position()).normalize().dot(direction) < .2) continue;
                other.hurt(p.damageSources().playerAttack(p), other instanceof ServerPlayer ? 2 : 4);
                AbilityEffects.send(p.serverLevel(), Kind.CRYO_BOLT, t.getEyePosition(), other.getEyePosition(), 1);
            }
            AbilityEffects.send(p.serverLevel(), Kind.CRYO_SHATTER, t.position(), t.getEyePosition(), 2);
            FacilityService.discovery(p, "brittle_fan");
        } finally { depth--; }
        return true;
    }
    public static boolean electric(ServerPlayer p, LivingEntity t) {
        if (!consume(p, t)) return false;
        depth++;
        try {
            for (var other : ExpansionSkills.enemies(p, t.position(), 6).stream().filter(e -> e != t).limit(3).toList()) {
                other.hurt(p.damageSources().playerAttack(p), other instanceof ServerPlayer ? 2 : 5);
                AbilityEffects.send(p.serverLevel(), Kind.ELECTRIC_ARC, t.getEyePosition(), other.getEyePosition(), 1.5);
            }
            AbilityEffects.send(p.serverLevel(), Kind.ELECTRIC_NOVA, t.position(), t.getEyePosition(), 3);
            FacilityService.discovery(p, "rime_circuit");
        } finally { depth--; }
        return true;
    }
    /** Thermal Shock: fire meets a frozen enemy. The ice flashes to steam around it. */
    public static boolean thermal(ServerPlayer p, LivingEntity t) {
        if (!consume(p, t)) return false;
        depth++;
        try {
            t.invulnerableTime = 0;
            t.hurt(p.damageSources().playerAttack(p), t instanceof ServerPlayer ? 4 : 10);
            for (LivingEntity other : ExpansionSkills.enemies(p, t.position(), 3).stream().limit(6).toList()) {
                if (other != t) other.hurt(p.damageSources().playerAttack(p), other instanceof ServerPlayer ? 2 : 4);
                other.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, 40));
            }
            AbilityEffects.send(p.serverLevel(), Kind.STEAM, t.position(), t.getEyePosition(), 3);
            AbilityEffects.send(p.serverLevel(), Kind.CRYO_SHATTER, t.position(), t.getEyePosition(), 1.5);
            p.serverLevel().playSound(null, t.getX(), t.getY(), t.getZ(), net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.4F, 0.6F);
            FacilityService.discovery(p, "thermal_shock");
        } finally { depth--; }
        return true;
    }
    private record Veil(ServerLevel level, Vec3 center, long until) {}
    private static final List<Veil> VEILS = new ArrayList<>();
    /** Steam Veil: fire striking water or ice raises a cloud that hides everyone inside from mobs. */
    public static void steamVeil(ServerPlayer p, ServerLevel level, Vec3 at) {
        long now = level.getServer().overworld().getGameTime();
        VEILS.removeIf(v -> v.until <= now);
        if (VEILS.size() >= 32 || VEILS.stream().anyMatch(v -> v.level == level && v.center.distanceToSqr(at) < 9)) return;
        VEILS.add(new Veil(level, at, now + 120));
        AbilityEffects.send(level, Kind.STEAM, at, at.add(0, 2, 0), 4);
        level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 0.7F);
        FacilityService.discovery(p, "steam_veil");
    }
    /** Mobs inside a veil cannot see out of it, and mobs outside cannot target anyone within. */
    public static boolean veiled(LivingEntity entity) {
        long now = entity.level().getServer() == null ? 0 : entity.level().getServer().overworld().getGameTime();
        for (Veil v : VEILS) if (v.until > now && v.level == entity.level() && v.center.distanceToSqr(entity.position()) <= 16) return true;
        return false;
    }
    private static final Map<UUID, Long> FIRESTORMS = new HashMap<>();
    /** Firestorm: a Flight Cyclone or Superspeed Whirlwind passing through a Ring of Fire catches alight. */
    public static void firestorm(ServerPlayer owner, ServerLevel level, Vec3 center, double radius) {
        long now = level.getServer().overworld().getGameTime();
        for (ServerPlayer spinner : level.getEntitiesOfClass(ServerPlayer.class, new AABB(center, center).inflate(radius + 2),
                s -> com.jayptucker.heroesevolved.flight.FlightService.isCycloneActive(s)
                        || com.jayptucker.heroesevolved.speed.SpeedService.hasWhirlwind(s))) {
            if (FIRESTORMS.getOrDefault(spinner.getUUID(), 0L) > now) continue;
            FIRESTORMS.put(spinner.getUUID(), now + 100);
            spinner.displayClientMessage(net.minecraft.network.chat.Component.translatable("reaction.heroesevolved.firestorm")
                    .withStyle(net.minecraft.ChatFormatting.GOLD), true);
            FacilityService.discovery(spinner, "firestorm");
            if (spinner != owner) FacilityService.discovery(owner, "firestorm");
        }
    }
    private static void tickFirestorms(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        FIRESTORMS.values().removeIf(until -> until <= now);
        if (now % 10 != 0) return;
        for (UUID id : FIRESTORMS.keySet()) {
            ServerPlayer spinner = server.getPlayerList().getPlayer(id);
            if (spinner == null) continue;
            depth++;
            try {
                for (LivingEntity t : ExpansionSkills.enemies(spinner, spinner.position(), 6).stream().limit(12).toList()) {
                    t.igniteForSeconds(3);
                    if (now % 20 == 0) t.hurt(spinner.damageSources().playerAttack(spinner), t instanceof ServerPlayer ? 1 : 3);
                }
            } finally { depth--; }
            AbilityEffects.send(spinner.serverLevel(), Kind.FIRE_RING, spinner.position(), spinner.getEyePosition(), 5);
        }
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) { tickFirestorms(e.getServer()); }
    @SubscribeEvent public static void target(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent e) {
        if (VEILS.isEmpty() || e.getNewAboutToBeSetTarget() == null) return;
        if (veiled(e.getNewAboutToBeSetTarget()) || veiled(e.getEntity())) e.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void incoming(LivingIncomingDamageEvent e) {
        if (depth == 0 && e.getSource().getDirectEntity() instanceof ServerPlayer p
                && com.jayptucker.heroesevolved.ability.service.PowerRules.canAct(p)
                && com.jayptucker.heroesevolved.ability.service.PlayerAbilityService.getData(p).assignedPower()
                .filter(a -> a.getValue().isUnlocked() && a.getKey().getPath().equals("quake")).isPresent()) quake(p, e.getEntity());
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent e) { SEAMS.clear(); REACTED.clear(); VEILS.clear(); FIRESTORMS.clear(); depth = 0; }
}
