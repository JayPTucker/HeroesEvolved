package com.jayptucker.heroesevolved.energy;

import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.network.StaminaPayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stamina: the one pool every power, skill, class ability and fighting style spends.
 *
 * <ul>
 *   <li>The bar is 100 at Power Level 1 and grows 5 a level (195 at Level 20).</li>
 *   <li>It recovers every tick: 3 a second at Level 1, 0.12 more each level, doubled after five seconds out of
 *       combat. Spending on a move pauses recovery for 1.5 seconds, so a fight is paid for out of the bar and the
 *       quiet after it refills you.</li>
 *   <li>Held powers ({@link StaminaDrain}, and the older per-second drains that call {@link #drain}) take stamina
 *       a little at a time; they don't restart the pause, but a drain that holds the world still (Still World)
 *       stops recovery while it runs.</li>
 * </ul>
 *
 * Stamina is fractional; {@link #getEnergy} rounds down for the code that thinks in whole points.
 */
public final class PlayerEnergyService {
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    /** Per-player bookkeeping that isn't saved: the pause, the drain rate and what was last sent. */
    private static final class State {
        long pausedUntil = Long.MIN_VALUE;
        double drainedThisTick, drainRate;
        /** The last second of drains, a tick a slot, for powers that drain in steps (every half second, every second). */
        final double[] window = new double[20];
        int slot;
        float sentStamina = -1, sentRegen = -1, sentDrain = -1;
        int sentMax = -1, sentFlags = -1;
        long sentAt = Long.MIN_VALUE;
    }

    private PlayerEnergyService() {
    }

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    public static void forget(ServerPlayer player) {
        STATES.remove(player.getUUID());
    }

    /** Whole points of stamina. */
    public static int getEnergy(ServerPlayer player) {
        Objects.requireNonNull(player, "Player cannot be null.");
        if (hasUnlimitedEnergy(player)) return getMaximumEnergy(player);
        return (int) Math.floor(getStamina(player) + 1e-6);
    }

    /** Exact stamina. */
    public static double getStamina(ServerPlayer player) {
        if (hasUnlimitedEnergy(player)) return getMaximumEnergy(player);
        return Math.min(player.getData(ModDataAttachments.PLAYER_ENERGY.get()).stamina(), getMaximumEnergy(player));
    }

    public static int getMaximumEnergy(ServerPlayer player) {
        Objects.requireNonNull(player, "Player cannot be null.");
        int level = PlayerProgressionService.getPowerLevel(player);
        return (int) Math.round(HeroesEvolvedConfig.COMMON.baseEnergy.get()
                + (level - 1) * HeroesEvolvedConfig.COMMON.energyPerPowerLevel.get());
    }

    /** Spends {@code amount} for a move, or nothing if there isn't enough. Pauses recovery. */
    public static boolean tryConsume(ServerPlayer player, int amount) {
        validateAmount(amount);
        if (hasUnlimitedEnergy(player)) return true;
        double current = getStamina(player);
        if (current + 1e-6 < amount) return false;
        if (amount > 0) {
            setStamina(player, current - amount);
            pause(player);
        }
        return true;
    }

    /**
     * Takes stamina for something held (a flight, a beam, a stopped world): no recovery pause, and fractions are
     * fine. Returns false, taking nothing, when there isn't enough left.
     */
    public static boolean drain(ServerPlayer player, double amount) {
        if (amount < 0 || !Double.isFinite(amount)) throw new IllegalArgumentException("Drain must be a positive amount.");
        if (hasUnlimitedEnergy(player)) return true;
        double current = getStamina(player);
        if (current + 1e-6 < amount) return false;
        if (amount > 0) {
            setStamina(player, current - amount);
            state(player).drainedThisTick += amount;
        }
        return true;
    }

    public static void restore(ServerPlayer player, int amount) {
        validateAmount(amount);
        restore(player, (double) amount);
    }

    public static void restore(ServerPlayer player, double amount) {
        double max = getMaximumEnergy(player), current = getStamina(player);
        double restored = Math.min(max, current + amount);
        if (restored > current) setStamina(player, restored);
    }

    /** Called every server tick for every player: recovery, then a stamina packet if the bar moved. */
    public static void tick(ServerPlayer player) {
        State state = state(player);
        long now = player.level().getGameTime();
        double regen = currentRegeneration(player, now);
        if (regen > 0 && !hasUnlimitedEnergy(player)) {
            double current = getStamina(player), max = getMaximumEnergy(player);
            if (current < max) setStamina(player, Math.min(max, current + regen / 20.0D));
        }
        // The bar's "-n/s": a held power's own rate when one runs (exact, and gone the moment it ends), otherwise what the
        // last second of stepped drains added up to.
        state.window[state.slot] = state.drainedThisTick;
        state.slot = (state.slot + 1) % state.window.length;
        state.drainedThisTick = 0;
        double held = StaminaDrain.rate(player), stepped = 0;
        for (double d : state.window) stepped += d;
        state.drainRate = held > 0 ? held : stepped < .05D ? 0 : stepped;
        send(player, state, now, regen, false);
    }

    /** The old once-a-second hook; recovery now runs every tick in {@link #tick}. */
    public static void restoreNaturally(ServerPlayer player) {
    }

    /** Recovery per second when nothing pauses it: base, plus Power Level, plus an electrokinetic's storm. */
    public static double regenerationPerSecond(ServerPlayer player) {
        double base = HeroesEvolvedConfig.COMMON.staminaRegenerationPerSecond.get()
                + (PlayerProgressionService.getPowerLevel(player) - 1)
                * HeroesEvolvedConfig.COMMON.staminaRegenerationPerPowerLevel.get();
        // Electrokinetics are fed by thunderstorms.
        if (com.jayptucker.heroesevolved.events.ElectroEvents.hasAwakenedElectrokinesis(player)) {
            base += com.jayptucker.heroesevolved.electro.ElectroService.stormEnergyBonus(player);
        }
        return base;
    }

    /** Recovery per second right now: 0 while paused or while a held power stops it, more when calm. */
    public static double currentRegeneration(ServerPlayer player, long now) {
        if (paused(player, now) || StaminaDrain.holdsRecovery(player)) return 0;
        return regenerationPerSecond(player) * (calm(player, now) ? HeroesEvolvedConfig.COMMON.staminaCalmRegenerationMultiplier.get() : 1);
    }

    /** Five seconds without dealing or taking damage. */
    public static boolean calm(ServerPlayer player, long now) {
        return !player.getData(ModDataAttachments.PLAYER_COMBAT).wasDamagedWithin(now, 100);
    }

    public static boolean paused(ServerPlayer player, long now) {
        return now < state(player).pausedUntil;
    }

    /** Recovery waits a moment after a move. */
    public static void pause(ServerPlayer player) {
        state(player).pausedUntil = player.level().getGameTime() + HeroesEvolvedConfig.COMMON.staminaRecoveryDelayTicks.get();
    }

    public static boolean hasUnlimitedEnergy(ServerPlayer player) {
        Objects.requireNonNull(player, "Player cannot be null.");
        return player.getData(ModDataAttachments.PLAYER_UNLIMITED_ENERGY.get());
    }

    public static void setUnlimitedEnergy(ServerPlayer player, boolean enabled) {
        Objects.requireNonNull(player, "Player cannot be null.");
        player.setData(ModDataAttachments.PLAYER_UNLIMITED_ENERGY.get(), enabled);
        // Keep the stored value and HUD consistent when test mode changes.
        if (enabled) setStamina(player, getMaximumEnergy(player));
        PlayerPowerSyncService.sync(player);
        sendNow(player);
    }

    private static void setStamina(ServerPlayer player, double stamina) {
        player.setData(ModDataAttachments.PLAYER_ENERGY.get(), new PlayerEnergyData(Math.max(0, stamina)));
    }

    /** Sends the bar now (after a refill, a level change, a test-mode switch). */
    public static void sendNow(ServerPlayer player) {
        long now = player.level().getGameTime();
        send(player, state(player), now, currentRegeneration(player, now), true);
    }

    /** A held power ended: its drain leaves the bar's rate at once. */
    public static void drainEnded(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        if (state != null) { java.util.Arrays.fill(state.window, 0); state.drainedThisTick = 0; }
    }

    /** Flashes the bar: a move was refused because there wasn't enough stamina. */
    public static void refused(ServerPlayer player) {
        State state = state(player);
        long now = player.level().getGameTime();
        double regen = currentRegeneration(player, now);
        int flags = (paused(player, now) ? StaminaPayload.PAUSED : 0) | (calm(player, now) ? StaminaPayload.CALM : 0)
                | (StaminaDrain.active(player) ? StaminaPayload.HELD : 0) | StaminaPayload.REFUSED;
        try {
            PacketDistributor.sendToPlayer(player, new StaminaPayload((float) getStamina(player), getMaximumEnergy(player),
                    (float) regen, (float) state.drainRate, flags, (int) Math.max(0, Math.min(1000, state.pausedUntil - now))));
        } catch (RuntimeException ignored) {}
    }

    private static void send(ServerPlayer player, State state, long now, double regen, boolean force) {
        float stamina = (float) getStamina(player), drain = (float) state.drainRate, rate = (float) regen;
        int max = getMaximumEnergy(player);
        int flags = (paused(player, now) ? StaminaPayload.PAUSED : 0) | (calm(player, now) ? StaminaPayload.CALM : 0)
                | (StaminaDrain.active(player) ? StaminaPayload.HELD : 0) | (hasUnlimitedEnergy(player) ? StaminaPayload.UNLIMITED : 0);
        boolean changed = Math.abs(stamina - state.sentStamina) >= .05F;
        // The client runs the bar on the rates between packets, so a steady change needs a packet only now and
        // then; a jump (a move, a hit, a refill) or a new rate goes out at once.
        boolean due = force || max != state.sentMax || flags != state.sentFlags
                || Math.abs(rate - state.sentRegen) > .01F || Math.abs(drain - state.sentDrain) > .5F
                || (drain > 0) != (state.sentDrain > 0)
                || Math.abs(stamina - state.sentStamina) >= 1.5F
                || changed && now - state.sentAt >= 10;
        if (!due) return;
        state.sentStamina = stamina; state.sentMax = max; state.sentFlags = flags; state.sentRegen = rate;
        state.sentDrain = drain; state.sentAt = now;
        int pause = (int) Math.max(0, Math.min(Integer.MAX_VALUE, state.pausedUntil - now));
        try {
            PacketDistributor.sendToPlayer(player, new StaminaPayload(stamina, max, rate, drain, flags, pause));
        } catch (RuntimeException ignored) {
            // Game-test players have no real connection.
        }
    }

    private static void validateAmount(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Energy amount cannot be negative.");
        }
    }

    /** Takes up to {@code requestedAmount} (overexertion spends whatever is left). Pauses recovery. */
    public static int consumeUpTo(ServerPlayer player, int requestedAmount) {
        validateAmount(requestedAmount);
        if (hasUnlimitedEnergy(player)) return 0;
        // Normal ability use needs the full cost. Overexertion instead takes every remaining point of energy.
        double current = getStamina(player);
        int consumed = (int) Math.min(Math.floor(current + 1e-6), requestedAmount);
        if (consumed > 0) {
            setStamina(player, current - consumed);
            pause(player);
        }
        return consumed;
    }
}
