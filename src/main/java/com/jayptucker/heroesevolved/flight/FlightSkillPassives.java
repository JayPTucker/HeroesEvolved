package com.jayptucker.heroesevolved.flight;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Event-driven Flight skills. Pending punches distinguish actual melee from power-attributed damage. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class FlightSkillPassives {
    public static final ResourceLocation LAST_SECOND_SAVE = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "flight_last_second_save");
    private static final ResourceLocation PRESSURE_TAP = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "flight_pressure_tap");
    private static final double RESCUE_RANGE = 18;
    private static final Map<UUID, Punch> PUNCHES = new HashMap<>();

    private FlightSkillPassives() {}

    @SubscribeEvent
    public static void handleFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer faller) || event.getDamageMultiplier() <= 0
                || event.getDistance() <= faller.getAttributeValue(Attributes.SAFE_FALL_DISTANCE)
                || FlightService.isFlightAuthorized(faller) || DiveBombService.isDiving(faller)) return;
        boolean heavyFall = event.getDistance() > 10;
        boolean featherlight = false;
        for (ServerPlayer guardian : faller.serverLevel().getEntitiesOfClass(ServerPlayer.class,
                faller.getBoundingBox().inflate(RESCUE_RANGE), player -> player != faller)) {
            if (guardian.distanceToSqr(faller) > RESCUE_RANGE * RESCUE_RANGE
                    || !FlightSkillService.isFriend(guardian, faller) || !PowerRules.canAct(guardian)) continue;
            boolean canCushion = SkillTreeService.has(guardian, "flight_0_0");
            boolean canSave = heavyFall && SkillTreeService.has(guardian, "flight_guardian_angel")
                    && !CooldownService.isOnCooldown(guardian, LAST_SECOND_SAVE);
            if (!(canCushion || canSave) || !guardian.hasLineOfSight(faller)) continue;
            featherlight |= canCushion;
            if (!canSave) continue;

            CooldownService.startCooldown(guardian, LAST_SECOND_SAVE, 20 * 45);
            event.setDamageMultiplier(0);
            faller.resetFallDistance();
            faller.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
            FlightDynamicsService.addMomentum(guardian, 25);
            SetPiecePayload.send(guardian.serverLevel(), Piece.FLIGHT_RESCUE,
                    guardian.getEyePosition(), faller.getBoundingBox().getCenter(), 1.4F, null);
            guardian.serverLevel().playSound(null, faller.blockPosition(), SoundEvents.BREEZE_WHIRL,
                    SoundSource.PLAYERS, .8F, 1.6F);
            return;
        }
        if (featherlight) event.setDamageMultiplier(heavyFall ? event.getDamageMultiplier() * .5F : 0);
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
