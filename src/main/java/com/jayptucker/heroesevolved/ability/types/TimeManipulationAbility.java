package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.Ability;
import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.AbilityActionDefinition;
import com.jayptucker.heroesevolved.ability.AbilityActivationResult;
import com.jayptucker.heroesevolved.ability.AbilityActivationType;
import com.jayptucker.heroesevolved.ability.AbilityDefinition;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public final class TimeManipulationAbility implements Ability {
    private static final AbilityDefinition DEFINITION =
            new AbilityDefinition(
                    0,
                    0,
                    5,
                    AbilityActivationType.INSTANT
            );

    private static final AbilityAction BLINK = new BlinkAction();

    // Level 1 starts with only Blink. We will add Temporal Field to
    // SECONDARY later, after its reusable field service exists.
    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, BLINK
    );

    @Override
    public AbilityDefinition definition() {
        return DEFINITION;
    }

    @Override
    public Map<AbilitySlot, AbilityAction> actions() {
        return ACTIONS;
    }

    @Override
    public boolean canUse(AbilityUseContext context) {
        return false;
    }

    @Override
    public AbilityActivationResult activate(AbilityUseContext context) {
        return AbilityActivationResult.REJECTED;
    }

    private static final class BlinkAction implements AbilityAction {
        private static final AbilityActionDefinition DEFINITION =
                new AbilityActionDefinition(
                        ResourceLocation.fromNamespaceAndPath(
                                HeroesEvolved.MOD_ID,
                                "time_blink"
                        ),
                        "action.heroesevolved.time_blink",
                        1,
                        0,
                        0
                );

        @Override
        public AbilityActionDefinition definition() {
            return DEFINITION;
        }

        @Override
        public int energyCost(int powerLevel) {
            return HeroesEvolvedConfig.COMMON.timeBlinkEnergyCost.get();
        }

        @Override
        public int cooldownTicks(int powerLevel) {
            return HeroesEvolvedConfig.COMMON.timeBlinkCooldownTicks.get();
        }

        @Override
        public boolean canUse(AbilityUseContext context) {
            return !context.player().isPassenger();
        }

        @Override
        public AbilityActivationResult activate(AbilityUseContext context) {
            ServerPlayer player = context.player();
            ServerLevel level = player.serverLevel();

            Vec3 origin = player.position();
            Vec3 direction = player.getLookAngle().normalize();
            int maximumDistance = HeroesEvolvedConfig.COMMON
                    .timeBlinkDistance
                    .get();

            // Start at maximum distance and work backward until we find
            // a position where the player's full hitbox will not collide.
            for (double distance = maximumDistance;
                    distance >= 2.0D;
                    distance -= 0.5D) {
                Vec3 destination = origin.add(direction.scale(distance));
                AABB destinationBox = player.getBoundingBox().move(
                        destination.subtract(origin)
                );

                if (!level.noCollision(player, destinationBox)) {
                    continue;
                }

                player.teleportTo(
                        destination.x,
                        destination.y,
                        destination.z
                );

                // The server plays this once at the destination, so the player
                // and everyone nearby hear the same Blink effect.
                level.playSound(
                        null,
                        destination.x,
                        destination.y,
                        destination.z,
                        ModSounds.TIME_BLINK.get(),
                        SoundSource.PLAYERS,
                        0.90F,
                        1.0F
                );

                MasteryService.awardPowerUse(
                        player,
                        context.abilityId()
                );

                return AbilityActivationResult.SUCCESS;
            }

            // No safe destination means no stamina is consumed or cooldown used.
            return AbilityActivationResult.REJECTED;
        }
    }
}
