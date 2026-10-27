package com.jayptucker.heroesevolved.expansion;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
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
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void incoming(LivingIncomingDamageEvent e) {
        if (depth == 0 && e.getSource().getDirectEntity() instanceof ServerPlayer p
                && com.jayptucker.heroesevolved.ability.service.PowerRules.canAct(p)
                && com.jayptucker.heroesevolved.ability.service.PlayerAbilityService.getData(p).assignedPower()
                .filter(a -> a.getValue().isUnlocked() && a.getKey().getPath().equals("quake")).isPresent()) quake(p, e.getEntity());
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent e) { SEAMS.clear(); REACTED.clear(); depth = 0; }
}
