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
import com.jayptucker.heroesevolved.time.TimeSlowService;
import com.jayptucker.heroesevolved.time.TimeStopService;
import com.jayptucker.heroesevolved.time.TemporalSnapshotService;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class TimeManipulationAbility implements Ability {
    private static final double BLINK_CARRY_RANGE = 1.0D;
    private static final double BLINK_CARRY_OFFSET = 1.2D;
    public static final ResourceLocation TEMPORAL_SNAPSHOT_CREATE_COOLDOWN =
            ResourceLocation.fromNamespaceAndPath(
                    HeroesEvolved.MOD_ID,
                    "temporal_snapshot_create"
            );
    public static final ResourceLocation TEMPORAL_SNAPSHOT_TRAVEL_COOLDOWN =
            ResourceLocation.fromNamespaceAndPath(
                    HeroesEvolved.MOD_ID,
                    "temporal_snapshot_travel"
            );

    private static final AbilityDefinition DEFINITION =
            new AbilityDefinition(
                    0,
                    0,
                    5,
                    AbilityActivationType.INSTANT
            );

    private static final AbilityAction BLINK = new BlinkAction();

    private static final AbilityAction TIME_SLOW = new TimeSlowAction();

    private static final AbilityAction TIME_STOP = new TimeStopAction();

    private static final AbilityAction TEMPORAL_SNAPSHOT =
            new TemporalSnapshotAction();

    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, BLINK,
            AbilitySlot.SECONDARY, TIME_SLOW,
            AbilitySlot.TERTIARY, TIME_STOP,
            AbilitySlot.QUATERNARY, TEMPORAL_SNAPSHOT
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

    @Override
    public void onRevoked(AbilityUseContext context) {
        TimeSlowService.stop(context.player());
        TimeStopService.stop(context.player());
    }

    @Override
    public void onAwaken(AbilityUseContext context) {
        // Time Manipulation first awakens as a defensive reflex: the world
        // around the player slows before they consciously control the power.
        TimeSlowService.start(context.player(), context.abilityLevel());
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
            return switch (Math.clamp(powerLevel, 1, 5)) {
                case 1 -> HeroesEvolvedConfig.COMMON
                        .timeBlinkLevelOneCooldownTicks.get();
                case 2 -> HeroesEvolvedConfig.COMMON
                        .timeBlinkLevelTwoCooldownTicks.get();
                case 3 -> HeroesEvolvedConfig.COMMON
                        .timeBlinkLevelThreeCooldownTicks.get();
                case 4 -> HeroesEvolvedConfig.COMMON
                        .timeBlinkLevelFourCooldownTicks.get();
                case 5 -> HeroesEvolvedConfig.COMMON
                        .timeBlinkLevelFiveCooldownTicks.get();
                default -> throw new IllegalStateException(
                        "Unexpected Blink power level."
                );
            };
        }

        @Override
        public boolean canUse(AbilityUseContext context) {
            return !context.player().isPassenger();
        }

        @Override
        public AbilityActivationResult activate(AbilityUseContext context) {
            ServerPlayer player = context.player();
            ServerLevel level = player.serverLevel();

            Vec3 origin = player.getEyePosition();
            Vec3 direction = player.getLookAngle().normalize();
            int maximumDistance = getMaximumDistance(
                    context.abilityLevel()
            );

            // Blink now uses the first solid block in the player's line of
            // sight. This makes its destination predictable: stand on top of
            // the block you are looking at, never somewhere below it.
            BlockHitResult hit = level.clip(new ClipContext(
                    origin,
                    origin.add(direction.scale(maximumDistance)),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            ));
            if (hit.getType() != HitResult.Type.BLOCK) {
                return AbilityActivationResult.REJECTED;
            }

            Vec3 destination = findSafeLandingDestination(
                    level,
                    player,
                    hit.getBlockPos()
            );
            if (destination == null) {
                return AbilityActivationResult.REJECTED;
            }

            // Carry Blink is intentionally folded into the familiar Blink
            // input: beginning at Level 2, Sneak selects the nearest living
            // target within one block and brings it along when space permits.
            LivingEntity carriedEntity = context.abilityLevel() >= 2
                    && player.isShiftKeyDown()
                    ? findBlinkCarryTarget(player, level)
                    : null;

            AABB destinationBox = player.getBoundingBox().move(
                    destination.subtract(player.position())
            );
            if (!level.noCollision(player, destinationBox)) {
                return AbilityActivationResult.REJECTED;
            }

            Vec3 carriedDestination = carriedEntity == null
                    ? null
                    : findSafeCarriedDestination(
                            level,
                            carriedEntity,
                            destination,
                            direction,
                            destinationBox
                    );

            player.teleportTo(
                    destination.x,
                    destination.y,
                    destination.z
            );

            if (carriedDestination != null && carriedEntity.isAlive()) {
                carriedEntity.teleportTo(
                        carriedDestination.x,
                        carriedDestination.y,
                        carriedDestination.z
                );
            }

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

        private static LivingEntity findBlinkCarryTarget(
                ServerPlayer player,
                ServerLevel level
        ) {
            List<LivingEntity> candidates = level.getEntitiesOfClass(
                    LivingEntity.class,
                    player.getBoundingBox().inflate(BLINK_CARRY_RANGE),
                    candidate -> candidate != player
                            && candidate.isAlive()
                            && !candidate.isPassenger()
                            && (!(candidate instanceof Player otherPlayer)
                            || !otherPlayer.isSpectator())
            );

            return candidates.stream()
                    .min(Comparator.comparingDouble(
                            candidate -> candidate.distanceToSqr(player)
                    ))
                    .orElse(null);
        }

        private static Vec3 findSafeCarriedDestination(
                ServerLevel level,
                LivingEntity carriedEntity,
                Vec3 playerDestination,
                Vec3 direction,
                AABB playerDestinationBox
        ) {
            Vec3 horizontalDirection = new Vec3(
                    direction.x,
                    0.0D,
                    direction.z
            );

            if (horizontalDirection.lengthSqr() < 0.0001D) {
                horizontalDirection = new Vec3(0.0D, 0.0D, 1.0D);
            } else {
                horizontalDirection = horizontalDirection.normalize();
            }

            Vec3 sideways = new Vec3(
                    -horizontalDirection.z,
                    0.0D,
                    horizontalDirection.x
            );

            // Try positions beside the player first, then just behind them.
            List<Vec3> offsets = List.of(
                    sideways.scale(BLINK_CARRY_OFFSET),
                    sideways.scale(-BLINK_CARRY_OFFSET),
                    horizontalDirection.scale(-BLINK_CARRY_OFFSET)
            );

            for (Vec3 offset : offsets) {
                Vec3 candidateDestination = playerDestination.add(offset);
                AABB candidateBox = carriedEntity.getBoundingBox().move(
                        candidateDestination.subtract(carriedEntity.position())
                );

                if (!candidateBox.intersects(playerDestinationBox)
                        && level.noCollision(carriedEntity, candidateBox)) {
                    return candidateDestination;
                }
            }

            // The player can still Blink normally if there is no safe space
            // for the selected target at the destination.
            return null;
        }

        private static int getMaximumDistance(int powerLevel) {
            return switch (Math.clamp(powerLevel, 1, 5)) {
                case 1 -> HeroesEvolvedConfig.COMMON
                        .timeBlinkLevelOneDistance.get();
                case 2 -> HeroesEvolvedConfig.COMMON
                        .timeBlinkLevelTwoDistance.get();
                case 3 -> HeroesEvolvedConfig.COMMON
                        .timeBlinkLevelThreeDistance.get();
                case 4 -> HeroesEvolvedConfig.COMMON
                        .timeBlinkLevelFourDistance.get();
                case 5 -> HeroesEvolvedConfig.COMMON
                        .timeBlinkLevelFiveDistance.get();
                default -> throw new IllegalStateException(
                        "Unexpected Blink power level."
                );
            };
        }

        /**
         * A valid Blink destination is the centre of the air space directly
         * above the solid block the player targeted.
         */
        private static Vec3 findSafeLandingDestination(
                ServerLevel level,
                ServerPlayer player,
                BlockPos targetBlock
        ) {
            if (!level.getBlockState(targetBlock)
                    .isFaceSturdy(level, targetBlock, Direction.UP)) {
                return null;
            }

            Vec3 candidate = Vec3.atBottomCenterOf(targetBlock.above());
            AABB candidateBox = player.getBoundingBox().move(
                    candidate.subtract(player.position())
            );
            return level.noCollision(player, candidateBox) ? candidate : null;
        }
    }

    private static final class TimeSlowAction implements AbilityAction {
        private static final AbilityActionDefinition DEFINITION =
                new AbilityActionDefinition(
                        ResourceLocation.fromNamespaceAndPath(
                                HeroesEvolved.MOD_ID,
                                "time_slow"
                        ),
                        "action.heroesevolved.time_slow",
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
            return HeroesEvolvedConfig.COMMON.timeSlowEnergyCost.get();
        }

        @Override
        public int cooldownTicks(int powerLevel) {
            return HeroesEvolvedConfig.COMMON.timeSlowCooldownTicks.get();
        }

        @Override
        public boolean canUse(AbilityUseContext context) {
            return !context.player().isPassenger()
                    && !TimeSlowService.hasActiveField(context.player());
        }

        @Override
        public boolean deactivate(AbilityUseContext context) {
            if (!TimeSlowService.hasActiveField(context.player())) {
                return false;
            }

            TimeSlowService.stop(context.player());
            return true;
        }

        @Override
        public AbilityActivationResult activate(AbilityUseContext context) {
            ServerPlayer player = context.player();

            TimeSlowService.start(player, context.abilityLevel());
            MasteryService.awardPowerUse(
                    player,
                    context.abilityId()
            );
            return AbilityActivationResult.SUCCESS;
        }
    }

    private static final class TimeStopAction implements AbilityAction {
        private static final AbilityActionDefinition DEFINITION =
                new AbilityActionDefinition(
                        ResourceLocation.fromNamespaceAndPath(
                                HeroesEvolved.MOD_ID,
                                "time_stop"
                        ),
                        "action.heroesevolved.time_stop",
                        3,
                        0,
                        0
                );

        @Override
        public AbilityActionDefinition definition() {
            return DEFINITION;
        }

        @Override
        public int energyCost(int powerLevel) {
            return TimeStopService.energyCostForLevel(powerLevel);
        }

        @Override
        public int cooldownTicks(int powerLevel) {
            return TimeStopService.cooldownTicksForLevel(powerLevel);
        }

        @Override
        public boolean canUse(AbilityUseContext context) {
            return !context.player().isPassenger()
                    && !TimeStopService.hasActiveField(context.player());
        }

        @Override
        public boolean deactivate(AbilityUseContext context) {
            if (!TimeStopService.hasActiveField(context.player())) {
                return false;
            }

            TimeStopService.stop(context.player());
            return true;
        }

        @Override
        public AbilityActivationResult activate(AbilityUseContext context) {
            ServerPlayer player = context.player();

            // Stopping time supersedes a current slow field, preventing two
            // overlapping effects from competing over entities and projectiles.
            TimeSlowService.stop(player);
            TimeStopService.start(player, context.abilityLevel());
            MasteryService.awardPowerUse(player, context.abilityId());
            return AbilityActivationResult.SUCCESS;
        }
    }

    private static final class TemporalSnapshotAction implements AbilityAction {
        private static final AbilityActionDefinition DEFINITION =
                new AbilityActionDefinition(
                        ResourceLocation.fromNamespaceAndPath(
                                HeroesEvolved.MOD_ID,
                                "temporal_snapshot"
                        ),
                        "action.heroesevolved.temporal_snapshot",
                        5,
                        0,
                        0
                );

        @Override
        public AbilityActionDefinition definition() {
            return DEFINITION;
        }

        @Override
        public int energyCost(AbilityUseContext context) {
            return context.modifierHeld()
                    ? HeroesEvolvedConfig.COMMON.timeSnapshotEnergyCost.get()
                    : 40;
        }

        @Override
        public int cooldownTicks(AbilityUseContext context) {
            return context.modifierHeld()
                    ? HeroesEvolvedConfig.COMMON.timeSnapshotCooldownTicks.get()
                    : HeroesEvolvedConfig.COMMON
                            .timeSnapshotTravelCooldownTicks.get();
        }

        @Override
        public ResourceLocation cooldownId(AbilityUseContext context) {
            return context.modifierHeld()
                    ? TEMPORAL_SNAPSHOT_CREATE_COOLDOWN
                    : TEMPORAL_SNAPSHOT_TRAVEL_COOLDOWN;
        }

        @Override
        public boolean canUse(AbilityUseContext context) {
            if (TemporalSnapshotService.isTemporalDimension(
                    context.player().serverLevel())) {
                return !context.modifierHeld()
                        && TemporalSnapshotService.returnToPresent(
                        context.player());
            }

            return context.modifierHeld()
                    || TemporalSnapshotService.enterSnapshot(context.player());
        }

        @Override
        public AbilityActivationResult activate(AbilityUseContext context) {
            // canUse performs travel immediately because its success depends
            // on destination availability. Snapshot creation happens here.
            if (TemporalSnapshotService.isTemporalDimension(
                    context.player().serverLevel()) || !context.modifierHeld()) {
                return AbilityActivationResult.SUCCESS;
            }

            return TemporalSnapshotService.beginSnapshot(context.player())
                    ? AbilityActivationResult.SUCCESS
                    : AbilityActivationResult.REJECTED;
        }
    }
}
