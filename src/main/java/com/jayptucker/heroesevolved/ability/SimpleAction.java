package com.jayptucker.heroesevolved.ability;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.resources.ResourceLocation;

import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * A configurable action for powers whose skills differ only in cost,
 * cooldown and what they do. Activation returning false means "nothing
 * happened": no energy is spent and no cooldown starts.
 */
public final class SimpleAction implements AbilityAction {
    @FunctionalInterface
    public interface Activation {
        boolean activate(AbilityUseContext context);
    }

    private final AbilityActionDefinition definition;
    private final IntSupplier energyCost;
    private final IntSupplier cooldownTicks;
    private final Predicate<AbilityUseContext> canUse;
    private final Activation activation;
    private final Predicate<AbilityUseContext> deactivation;
    private final Predicate<AbilityUseContext> deactivateOnPress;
    private final Predicate<AbilityUseContext> followUp;
    private final java.util.function.Consumer<AbilityUseContext> release;

    private SimpleAction(Builder builder) {
        definition = new AbilityActionDefinition(
                ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, builder.path),
                "action." + HeroesEvolved.MOD_ID + "." + builder.path,
                builder.unlockLevel,
                0,
                0
        );
        energyCost = builder.energyCost;
        cooldownTicks = builder.cooldownTicks;
        canUse = builder.canUse;
        activation = builder.activation;
        deactivation = builder.deactivation;
        deactivateOnPress = builder.deactivateOnPress;
        followUp = builder.followUp;
        release = builder.release;
    }

    public static Builder builder(String path, int unlockLevel) {
        return new Builder(path, unlockLevel);
    }

    @Override
    public AbilityActionDefinition definition() {
        return definition;
    }

    @Override
    public int energyCost(int powerLevel) {
        return energyCost.getAsInt();
    }

    @Override
    public int cooldownTicks(int powerLevel) {
        return cooldownTicks.getAsInt();
    }

    @Override
    public boolean canUse(AbilityUseContext context) {
        return !context.player().isSpectator() && canUse.test(context);
    }

    @Override
    public boolean deactivate(AbilityUseContext context) {
        return deactivation.test(context);
    }

    @Override
    public boolean deactivateOnPress(AbilityUseContext context) {
        return deactivateOnPress.test(context);
    }

    @Override public boolean followUp(AbilityUseContext context) { return followUp.test(context); }

    @Override public void release(AbilityUseContext context) { release.accept(context); }

    @Override
    public AbilityActivationResult activate(AbilityUseContext context) {
        return activation.activate(context)
                ? AbilityActivationResult.SUCCESS
                : AbilityActivationResult.REJECTED;
    }

    public static final class Builder {
        private final String path;
        private final int unlockLevel;
        private IntSupplier energyCost = () -> 0;
        private IntSupplier cooldownTicks = () -> 0;
        private Predicate<AbilityUseContext> canUse = context -> true;
        private Activation activation = context -> false;
        private Predicate<AbilityUseContext> deactivation = context -> false;
        private Predicate<AbilityUseContext> deactivateOnPress = context -> false;
        private Predicate<AbilityUseContext> followUp = context -> false;
        private java.util.function.Consumer<AbilityUseContext> release = context -> {};

        private Builder(String path, int unlockLevel) {
            this.path = path;
            this.unlockLevel = unlockLevel;
        }

        public Builder cost(IntSupplier energy, IntSupplier cooldown) {
            energyCost = energy;
            cooldownTicks = cooldown;
            return this;
        }

        public Builder canUse(Predicate<AbilityUseContext> predicate) {
            canUse = predicate;
            return this;
        }

        public Builder activate(Activation action) {
            activation = action;
            return this;
        }

        /** Shift + key ends the effect for free when this returns true. */
        public Builder deactivate(Predicate<AbilityUseContext> action) {
            deactivation = action;
            return this;
        }

        public Builder deactivateOnPress(Predicate<AbilityUseContext> predicate) {
            deactivateOnPress = predicate;
            return this;
        }

        public Builder followUp(Predicate<AbilityUseContext> action) {
            followUp = action;
            return this;
        }

        /** Called when the key is let go: for actions that do more the longer the key is held. */
        public Builder release(java.util.function.Consumer<AbilityUseContext> action) {
            release = action;
            return this;
        }

        public SimpleAction build() {
            return new SimpleAction(this);
        }
    }
}
