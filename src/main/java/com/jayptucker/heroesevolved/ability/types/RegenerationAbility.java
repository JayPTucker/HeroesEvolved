package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.Ability;
import com.jayptucker.heroesevolved.ability.AbilityActivationResult;
import com.jayptucker.heroesevolved.ability.AbilityActivationType;
import com.jayptucker.heroesevolved.ability.AbilityDefinition;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.combat.PlayerCombatData;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3f;

public final class RegenerationAbility implements Ability {
    private static final int HEAL_PARTICLE_COUNT = 24;

    private static final DustParticleOptions REGENERATION_PARTICLE =
        new DustParticleOptions(
            new Vector3f(0.82F, 0.03F, 0.08F),
            0.8F
        );
    
    private static final float HEALTH_RESTORED_PER_TICK = 1.0F;

    private static final AbilityDefinition DEFINITION = new AbilityDefinition(
        0,
        0,
        5,
        AbilityActivationType.PASSIVE
    );

    @Override
    public AbilityDefinition definition() {
        return DEFINITION;
    }

    @Override
    public boolean canUse(AbilityUseContext context) {
        if (context.player().getHealth() >= context.player().getMaxHealth()) {
            return false;
        }

        PlayerCombatData combatData =
            context.player().getData(ModDataAttachments.PLAYER_COMBAT.get());
        
        if (combatData.wasDamagedWithin(
            context.gameTime(),
            HeroesEvolvedConfig.COMMON.regenerationDamageDelayTicks.get()
        )) {
            return false;
        }

        return PlayerEnergyService.getEnergy(context.player())
            >= HeroesEvolvedConfig.COMMON.regenerationEnergyCost.get();
    }

    @Override
    public AbilityActivationResult activate(AbilityUseContext context) {
        return AbilityActivationResult.REJECTED;

    }

    @Override
    public void onAwaken(AbilityUseContext context) {
        ServerPlayer player = context.player();
        player.setHealth(player.getMaxHealth());
        spawnHealingParticles(player);
    }

    @Override
    public void tick(AbilityUseContext context) {
        int intervalTicks = HeroesEvolvedConfig.COMMON.regenerationIntervalTicks.get();

        if (context.gameTime() % intervalTicks != 0 || !canUse(context)) {
            return;
        }

        int energyCost = HeroesEvolvedConfig.COMMON.regenerationEnergyCost.get();

        if (!PlayerEnergyService.tryConsume(context.player(), energyCost)) {
            return;
        }

        context.player().heal(HEALTH_RESTORED_PER_TICK);
        spawnHealingParticles(context.player());

        
    }

    private static void spawnHealingParticles(ServerPlayer player) {
    ServerLevel level = player.serverLevel();

    for (int index = 0; index < HEAL_PARTICLE_COUNT; index++) {
        double progress = index / (double) HEAL_PARTICLE_COUNT;
        double angle = progress * Math.PI * 4.0D;
        double radius = 0.35D;

        double x = player.getX() + Math.cos(angle) * radius;
        double y = player.getY() + 0.2D + (progress * 1.4D);
        double z = player.getZ() + Math.sin(angle) * radius;

        level.sendParticles(
                REGENERATION_PARTICLE,
                x,
                y,
                z,
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D
            );
        }
    }
}
