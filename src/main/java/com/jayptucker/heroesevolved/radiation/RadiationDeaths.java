package com.jayptucker.heroesevolved.radiation;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.hazard.BodyBurn;
import com.jayptucker.heroesevolved.hazard.HazardDamage;
import com.jayptucker.heroesevolved.hazard.Suits;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.speed.SpeedContainment;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Whoever dies of radiation burns away: the body breaks into glowing cubes that drift up and cool to ash, and no corpse
 * is left (items still drop). It counts as radiation if the killing blow was a meltdown's heat or a speedster's friction,
 * if a radiation power (not a punch) dealt it, if radiation hurt them in the last half-second, or if they were sick with
 * it when they died.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class RadiationDeaths {
    /** Radiation that hurt someone this recently still counts for their death. */
    static final int RECENT = 10;
    private static final Map<UUID, Long> MARKED = new LinkedHashMap<>(64, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID, Long> eldest) { return size() > 512; }
    };

    private RadiationDeaths() {}

    /** Radiation is hurting {@code e} now. */
    public static void mark(LivingEntity e) {
        if (e.level() instanceof ServerLevel level) MARKED.put(e.getUUID(), level.getGameTime());
    }

    /** Would a death to {@code source} now be a death by radiation? */
    public static boolean byRadiation(LivingEntity dead, DamageSource source) {
        if (source.is(SpeedContainment.DISINTEGRATED)) return false; // that death has its own burning on the ring
        if (source.is(HazardDamage.MELTDOWN) || source.is(HazardDamage.FRICTION)) return true;
        if (dead.hasEffect(ModItems.RADIATION_SICKNESS)) return true;
        Long at = MARKED.get(dead.getUUID());
        if (at != null && dead.level().getGameTime() - at <= RECENT) return true;
        return source.getEntity() instanceof ServerPlayer p && Suits.radioactive(p) && !source.is(DamageTypes.PLAYER_ATTACK)
                && !source.is(DamageTypes.ARROW) && !source.is(DamageTypes.TRIDENT);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void died(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        LivingEntity dead = event.getEntity();
        MARKED.remove(dead.getUUID());
        if (byRadiation(dead, event.getSource())) disintegrate(dead);
    }

    /**
     * Breaks {@code dead}'s body apart into cubes where it stands. The piece is bound to them so every client hides the
     * corpse; its target lies along their facing, as far as the body is wide, and its scale is the body's height.
     */
    public static void disintegrate(LivingEntity dead) {
        if (!(dead.level() instanceof ServerLevel level)) return;
        Vec3 at = dead.position();
        Vec3 facing = Vec3.directionFromRotation(0, dead.getYRot()).multiply(1, 0, 1);
        facing = facing.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : facing.normalize();
        SetPiecePayload.send(level, Piece.RAD_DISINTEGRATE, at, at.add(facing.scale(Math.max(0.3, dead.getBbWidth()))),
                Math.max(0.3F, dead.getBbHeight()), dead);
        if (dead instanceof ServerPlayer p) BodyBurn.atLeast(p, 1);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 1.0F, 0.6F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SAND_BREAK, SoundSource.NEUTRAL, 1.2F, 0.6F);
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) { MARKED.clear(); }
}
