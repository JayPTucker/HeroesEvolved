package com.jayptucker.heroesevolved.radiation;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Radiation Sickness: a dose that stacks in five stages. Each second it burns from the inside for
 * half a heart per stage (armor does not help, and the undead are not spared), and it saps strength.
 * Ted Sprague and anyone holding his power are immune. A creature that dies while sick bursts,
 * dosing everything around it: radiation spreads through a crowd as a chain reaction.
 */
public final class RadiationSickness extends MobEffect {
    public static final int MAX_STAGE = 4;
    /** Who gave each sick creature its dose, for kill credit and the chain reaction. */
    private static final Map<UUID, UUID> SOURCES = new HashMap<>();

    public RadiationSickness() {
        super(MobEffectCategory.HARMFUL, 0xB8E04A);
        addAttributeModifier(Attributes.ATTACK_DAMAGE, ResourceLocation.fromNamespaceAndPath("heroesevolved", "radiation_sickness"),
                -0.75D, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof ServerLevel level) || immune(entity)) return true;
        ServerPlayer source = source(level, entity);
        DamageSource damage = source != null ? level.damageSources().indirectMagic(source, source) : level.damageSources().magic();
        entity.hurt(damage, 0.5F + 0.5F * amplifier);
        return true;
    }

    // ------------------------------------------------------------------

    /** True for anyone who carries the radiation power: their own emissions cannot make them sick. */
    public static boolean immune(LivingEntity entity) {
        return entity instanceof ServerPlayer player && PlayerAbilityService.getData(player).assignedPower()
                .filter(entry -> entry.getKey().equals(ModAbilities.RADIATION_ID)).isPresent();
    }

    public static int stage(LivingEntity entity) {
        MobEffectInstance sickness = entity.getEffect(ModItems.RADIATION_SICKNESS);
        return sickness == null ? -1 : sickness.getAmplifier();
    }

    /**
     * Adds {@code stages} to a creature's dose (up to {@code cap}) and keeps it sick for at least
     * {@code ticks}. Returns true if it took effect.
     */
    public static boolean dose(LivingEntity target, ServerPlayer caster, int stages, int ticks, int cap) {
        if (!target.isAlive() || immune(target) || stages <= 0) return false;
        MobEffectInstance current = target.getEffect(ModItems.RADIATION_SICKNESS);
        int stage = Math.min(Math.min(MAX_STAGE, cap), (current == null ? -1 : current.getAmplifier()) + stages);
        if (current != null && stage < current.getAmplifier()) stage = current.getAmplifier();
        int duration = Math.max(ticks, current == null ? 0 : current.getDuration());
        target.addEffect(new MobEffectInstance(ModItems.RADIATION_SICKNESS, duration, stage, false, true, true));
        if (caster != null) SOURCES.put(target.getUUID(), caster.getUUID());
        return true;
    }

    public static boolean dose(LivingEntity target, ServerPlayer caster, int stages, int ticks) {
        return dose(target, caster, stages, ticks, MAX_STAGE);
    }

    private static ServerPlayer source(ServerLevel level, LivingEntity entity) {
        UUID id = SOURCES.get(entity.getUUID());
        return id == null ? null : level.getServer().getPlayerList().getPlayer(id);
    }

    /**
     * A sick creature died: the dose inside it bursts. Everyone within 3 blocks (plus one per stage)
     * takes damage and a dose of their own; it does not hurt the caster or their allies.
     */
    public static void chainReaction(LivingEntity dead) {
        int stage = stage(dead);
        UUID sourceId = SOURCES.remove(dead.getUUID());
        if (stage < 1 || !(dead.level() instanceof ServerLevel level)) return;
        ServerPlayer caster = sourceId == null ? null : level.getServer().getPlayerList().getPlayer(sourceId);
        double radius = 3 + stage;
        var center = dead.getBoundingBox().getCenter();
        int spread = 0;
        for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                e -> e != dead && e.isAlive() && e.distanceToSqr(center) <= radius * radius
                        && (caster == null ? !immune(e) : ExpansionSkills.hostile(caster, e)))) {
            if (++spread > 12) break;
            near.hurt(caster != null ? level.damageSources().indirectMagic(caster, caster) : level.damageSources().magic(), 2 + stage);
            dose(near, caster, 1, 120, stage);
        }
        AbilityEffects.send(level, Kind.RADIATION_CHAIN, center, center.add(0, 1, 0), radius);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5F, 1.6F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 1.8F);
    }

    public static void forget(LivingEntity entity) { SOURCES.remove(entity.getUUID()); }

    public static void clear() { SOURCES.clear(); }

    /** For tests and debugging: the registry name. */
    public static ResourceLocation id() { return BuiltInRegistries.MOB_EFFECT.getKey(ModItems.RADIATION_SICKNESS.get()); }
}
