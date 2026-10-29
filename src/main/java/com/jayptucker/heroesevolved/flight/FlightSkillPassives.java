package com.jayptucker.heroesevolved.flight;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

/** Event-driven Flight skills. Pending punches distinguish actual melee from power-attributed damage. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class FlightSkillPassives {
    public static final ResourceLocation LAST_SECOND_SAVE = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "flight_last_second_save");
    private static final ResourceLocation PRESSURE_TAP = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "flight_pressure_tap");
    private static final Map<UUID, Punch> PUNCHES = new HashMap<>();

    private FlightSkillPassives() {}

    @SubscribeEvent
    public static void handleFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer faller) || event.getDamageMultiplier() <= 0
                || DiveBombService.isDiving(faller)) return;
        if (event.getDistance() > 10) {
            // Include the faller even when a test or another mod does not register it in the world's player list.
            var candidates = new LinkedHashSet<ServerPlayer>();
            candidates.add(faller);
            candidates.addAll(faller.serverLevel().getEntitiesOfClass(ServerPlayer.class,
                    faller.getBoundingBox().inflate(18), guardian -> guardian != faller));
            for (ServerPlayer guardian : candidates) {
                if (!PowerRules.canAct(guardian) || !SkillTreeService.has(guardian, "flight_guardian_angel")
                        || !FlightSkillService.isFriend(guardian, faller) || !guardian.hasLineOfSight(faller)
                        || guardian.distanceToSqr(faller) > 18 * 18
                        || CooldownService.isOnCooldown(guardian, LAST_SECOND_SAVE)) continue;
                CooldownService.startCooldown(guardian, LAST_SECOND_SAVE, 20 * 45);
                event.setDamageMultiplier(0);
                faller.resetFallDistance();
                faller.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
                FlightDynamicsService.addMomentum(guardian, 25);
                AbilityEffects.send(guardian.serverLevel(), Kind.FLIGHT_RESCUE,
                        guardian.getEyePosition(), faller.getBoundingBox().getCenter(), 1.4);
                guardian.serverLevel().playSound(null, faller.blockPosition(), SoundEvents.BREEZE_WHIRL,
                        SoundSource.PLAYERS, .8F, 1.6F);
                return;
            }
        }
        if (PowerRules.canAct(faller) && SkillTreeService.has(faller, "flight_0_0"))
            event.setDamageMultiplier(event.getDistance() <= 10 ? 0 : event.getDamageMultiplier() * .5F);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof LivingEntity target)
                || player.onGround() || !PowerRules.canAct(player) || !FlightService.hasActiveSession(player)
                || !SkillTreeService.has(player, "flight_2_0") || FlightSkillService.isFriend(player, target)) return;
        PUNCHES.put(player.getUUID(), new Punch(target.getUUID(), player.serverLevel().getGameTime()));
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || event.getSource().getDirectEntity() != player || event.getNewDamage() <= 0) return;
        Punch punch = PUNCHES.remove(player.getUUID());
        if (punch == null || !punch.target.equals(event.getEntity().getUUID())
                || punch.time != player.serverLevel().getGameTime() || player.onGround()
                || !PowerRules.canAct(player) || !SkillTreeService.has(player, "flight_2_0")
                || FlightSkillService.isFriend(player, event.getEntity())
                || CooldownService.isOnCooldown(player, PRESSURE_TAP)) return;
        CooldownService.startCooldown(player, PRESSURE_TAP, 10);
        FlightDynamicsService.addMomentum(player, 5);
    }

    public static void forget(ServerPlayer player) { PUNCHES.remove(player.getUUID()); }
    public static void clear() { PUNCHES.clear(); }
    private record Punch(UUID target, long time) {}
}
