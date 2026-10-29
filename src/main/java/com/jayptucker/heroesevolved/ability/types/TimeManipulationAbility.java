package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.AbilityRanges;

import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;

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
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.time.TemporalEntityEchoService;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.time.TimeSlowService;
import com.jayptucker.heroesevolved.time.SpaceTimeService;
import com.jayptucker.heroesevolved.time.TemporalStoryService;
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

    /**
     * Level 4: restore the recorded local timeline, including blocks and entities. It costs a full tank
     * of stamina (Shift overexerts when short) and recovers slowly: four minutes at Level 4, two and a
     * half at Level 5.
     */
    private static final AbilityAction REWIND = new AbilityAction() {
        private final AbilityActionDefinition definition = new AbilityActionDefinition(
                com.jayptucker.heroesevolved.time.TimeRewindService.REWIND_ID, "action.heroesevolved.time_rewind", 4, 0, 0);
        @Override public AbilityActionDefinition definition() { return definition; }
        @Override public int energyCost(int level) { return HeroesEvolvedConfig.COMMON.timeRewindEnergyCost.get(); }
        @Override public int cooldownTicks(int level) { return com.jayptucker.heroesevolved.time.TimeRewindService.cooldownTicks(level); }
        @Override public boolean canUse(AbilityUseContext context) {
            return !context.player().isPassenger() && com.jayptucker.heroesevolved.time.TimeRewindService.canRewind(context.player());
        }
        @Override public AbilityActivationResult activate(AbilityUseContext context) {
            return com.jayptucker.heroesevolved.time.TimeRewindService.rewind(context.player())
                    ? AbilityActivationResult.SUCCESS : AbilityActivationResult.REJECTED;
        }
    };

    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, BLINK,
            AbilitySlot.SECONDARY, TIME_SLOW,
            AbilitySlot.TERTIARY, TIME_STOP,
            AbilitySlot.QUATERNARY, REWIND,
            AbilitySlot.QUINARY, TEMPORAL_SNAPSHOT
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
        SpaceTimeService.forget(context.player());
        TimeSlowService.stop(context.player());
        TimeStopService.stop(context.player());
        com.jayptucker.heroesevolved.time.TimeRewindService.forget(context.player());
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

            for (int distance = 1; distance <= maximumDistance; distance++) {
                if (!level.hasChunkAt(BlockPos.containing(origin.add(direction.scale(distance))))) {
                    maximumDistance = distance - 1; break;
                }
            }
            if (maximumDistance < 2) return AbilityActivationResult.REJECTED;
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
            Vec3 destination = hit.getType() == HitResult.Type.BLOCK
                    ? findSafeLandingDestination(level, player, hit.getBlockPos()) : null;
            // If the aimed surface has no headroom, step back along the visible ray.
            // Looking into open air is useful for escaping a fall or crossing a gap.
            if (destination == null) {
                double distance = hit.getType() == HitResult.Type.BLOCK
                        ? Math.max(0, origin.distanceTo(hit.getLocation()) - .8) : maximumDistance;
                for (double d = distance; d >= 2; d -= .5) {
                    Vec3 candidate = player.position().add(direction.scale(d));
                    if (SpaceTimeService.safe(player, candidate)) { destination = candidate; break; }
                }
            }
            if (destination == null) return AbilityActivationResult.REJECTED;

            // Carry Blink is intentionally folded into the familiar Blink
            // input: beginning at Level 2, Sneak selects the nearest living
            // target within one block and brings it along when space permits.
            LivingEntity carriedEntity = context.abilityLevel() >= 2
                    && context.modifierHeld()
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

            Vec3 leftBehind = player.position();
            com.jayptucker.heroesevolved.network.TimeGhostPayload.echo(player, leftBehind, player.yBodyRot, 16, 0);
            player.teleportTo(destination.x, destination.y, destination.z);
            player.resetFallDistance();
            player.setDeltaMovement(Vec3.ZERO);
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
            AbilityEffects.send(level, Kind.TIME_FOLD, leftBehind, destination, 1.0);
            com.jayptucker.heroesevolved.network.ScreenTintPayload.send(
                    player, 0x50FFFFFF, 4,
                    com.jayptucker.heroesevolved.network.ScreenTintPayload.Style.FLASH
            );

            if (carriedDestination != null && carriedEntity.isAlive()) {
                carriedEntity.teleportTo(
                        carriedDestination.x,
                        carriedDestination.y,
                        carriedDestination.z
                );
                carriedEntity.resetFallDistance();
                carriedEntity.setDeltaMovement(Vec3.ZERO);
                carriedEntity.hurtMarked = true;
                if (carriedEntity instanceof ServerPlayer passenger) passenger.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(passenger));
                TemporalStoryService.reach(player, TemporalStoryService.RESCUE);
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

            MasteryService.markPowerUse(
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
                            // Echoes and replaying Past mobs are recordings,
                            // not bodies that can be carried out of history.
                            && !(candidate instanceof TemporalEchoEntity)
                            && !TemporalEntityEchoService.isReplaying(candidate)
                            && (!(candidate instanceof Player otherPlayer)
                            || !otherPlayer.isSpectator() && (player.isAlliedTo(otherPlayer) || otherPlayer.isShiftKeyDown()))
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
                        && SpaceTimeService.safe(carriedEntity, candidateDestination)) {
                    return candidateDestination;
                }
            }

            // The player can still Blink normally if there is no safe space
            // for the selected target at the destination.
            return null;
        }

        private static int getMaximumDistance(int powerLevel) {
            return AbilityRanges.ranged(switch (Math.clamp(powerLevel, 1, 5)) {
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
            });
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
            return SpaceTimeService.safe(player, candidate) ? candidate : null;
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

            TimeStopService.stop(player);
            TimeSlowService.start(player, context.abilityLevel());
            MasteryService.markPowerUse(
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
            TimeStopService.start(player, context.abilityLevel(), context.modifierHeld());
            MasteryService.markPowerUse(player, context.abilityId());
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
            if (inPast(context)) {
                // Returning is always free, so a traveller who has spent
                // their energy can never be stranded in the Past.
                return 0;
            }

            return creatingSnapshot(context)
                    ? HeroesEvolvedConfig.COMMON.timeSnapshotEnergyCost.get()
                    : HeroesEvolvedConfig.COMMON
                            .timeSnapshotTravelEnergyCost.get();
        }

        @Override
        public int cooldownTicks(AbilityUseContext context) {
            if (inPast(context)) return 0;
            return creatingSnapshot(context)
                    ? HeroesEvolvedConfig.COMMON.timeSnapshotCooldownTicks.get()
                    : HeroesEvolvedConfig.COMMON
                            .timeSnapshotTravelCooldownTicks.get();
        }

        @Override
        public ResourceLocation cooldownId(AbilityUseContext context) {
            if (inPast(context)) return ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "temporal_return");
            return creatingSnapshot(context)
                    ? TEMPORAL_SNAPSHOT_CREATE_COOLDOWN
                    : TEMPORAL_SNAPSHOT_TRAVEL_COOLDOWN;
        }

        @Override
        public boolean canUse(AbilityUseContext context) {
            // canUse must only check. Travel used to happen here, before
            // the energy check, so it was free whenever energy ran short.
            ServerPlayer player = context.player();
            if (inPast(context)) {
                return TemporalSnapshotService.canReturnToPresent(player);
            }

            return context.modifierHeld()
                    ? TemporalSnapshotService.canBeginSnapshot(player)
                    : TemporalSnapshotService.canEnterSnapshot(player);
        }

        @Override
        public AbilityActivationResult activate(AbilityUseContext context) {
            ServerPlayer player = context.player();
            boolean succeeded;
            boolean travellingToPast = !inPast(context) && !context.modifierHeld();
            if (inPast(context)) {
                succeeded = TemporalSnapshotService.returnToPresent(player);
            } else if (context.modifierHeld()) {
                succeeded = TemporalSnapshotService.beginSnapshot(player);
            } else {
                succeeded = TemporalSnapshotService.enterSnapshot(player);
            }

            if (succeeded) {
                MasteryService.markPowerUse(player, context.abilityId());
                if (travellingToPast) TemporalStoryService.reach(player, TemporalStoryService.TRAVEL);
            }
            return succeeded
                    ? AbilityActivationResult.SUCCESS
                    : AbilityActivationResult.REJECTED;
        }

        private static boolean inPast(AbilityUseContext context) {
            return TemporalSnapshotService.isTemporalDimension(
                    context.player().serverLevel()
            );
        }

        /** Shift creates a snapshot in the Present; in the Past it means nothing. */
        private static boolean creatingSnapshot(AbilityUseContext context) {
            return context.modifierHeld() && !inPast(context);
        }
    }
}
