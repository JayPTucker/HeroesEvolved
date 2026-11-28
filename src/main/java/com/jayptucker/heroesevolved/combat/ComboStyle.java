package com.jayptucker.heroesevolved.combat;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.combat.FightingStyles.Dodge;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;

/**
 * The shape every fighting style since firebending shares, so a new style is only its moves. An empty-handed click
 * throws the next strike of a combo: the lead hand, the rear hand, and (from {@link #FINISHER_LEVEL}) a finisher;
 * crouching throws the low move instead. A double-tapped direction dodges, and back (from the same level) is the
 * style's special back dodge. The first blow a dodge evades is the counter, which loads the next strike and remembers
 * who threw the blow. A style overrides the moves and, if it wants, how it dodges ({@link #dodgeMove}), braces
 * ({@link #braced}) or ticks.
 */
public abstract class ComboStyle implements FightingStyles.Style {
    public enum Move { LEAD, REAR, FINISHER, LOW }

    public static final int FINISHER_LEVEL = 2;
    public static final int COMBO_WINDOW = 18, GAP = 5, FINISHER_RECOVERY = 12, LOW_RECOVERY = 14;
    public static final int EVADE_TICKS = 8, CUSHION_TICKS = 40, LOADED_TICKS = 40;

    /** One fighter's combo and the little a style keeps about them. */
    public static final class Fighter {
        public ServerPlayer player;
        public int step, stack;
        public long lastStrike = -1000, readyAt = -1000, dodgeReady = -1000, loadedUntil = -1000;
        /** Who threw the blow the counter answered (or null). */
        public UUID mark;
        /** Where the fighter has been over the last second, newest last (for styles that rewind). */
        public final ArrayDeque<Vec3> trail = new ArrayDeque<>();
        public double banked;

        public boolean loaded(long now) { return now < loadedUntil; }

        public LivingEntity marked() {
            if (mark == null || !(player.serverLevel().getEntity(mark) instanceof LivingEntity living) || !living.isAlive()) return null;
            return living;
        }
    }

    private final ResourceLocation power;
    private final String tired;
    private final Map<UUID, Fighter> fighters = new HashMap<>();

    protected ComboStyle(ResourceLocation power, String tiredKey) {
        this.power = power;
        this.tired = tiredKey;
    }

    @Override public ResourceLocation power() { return power; }

    protected static long now(ServerPlayer player) { return player.getServer().overworld().getGameTime(); }

    public Fighter fighter(ServerPlayer player) {
        Fighter fighter = fighters.computeIfAbsent(player.getUUID(), id -> new Fighter());
        fighter.player = player;
        return fighter;
    }

    /** May this player fight this way right now? Styles add what their power needs (no held cast, and so on). */
    protected boolean canFight(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && FightingStyles.fights(player, this) && PowerRules.canAct(player)
                && !com.jayptucker.heroesevolved.cryo.CryoService.isFrozen(player);
    }

    public Move next(ServerPlayer player) {
        Fighter fighter = fighter(player);
        int level = PlayerProgressionService.getLevel(player);
        if (player.isCrouching() && level >= FINISHER_LEVEL) return Move.LOW;
        int step = now(player) - fighter.lastStrike > COMBO_WINDOW ? 0 : fighter.step;
        return step == 2 && level >= FINISHER_LEVEL ? Move.FINISHER : step == 1 ? Move.REAR : Move.LEAD;
    }

    public boolean loaded(ServerPlayer player) {
        Fighter fighter = fighters.get(player.getUUID());
        return fighter != null && fighter.loaded(now(player));
    }

    /** Throws one move. {@code loaded} is true for the counter's free strike. */
    protected abstract void throwMove(ServerPlayer player, Fighter fighter, Move move, int level, boolean loaded);

    /** Moves the body for a dodge ({@code special}: the back dodge from level 2) and shows it. */
    protected abstract void dodgeMove(ServerPlayer player, Fighter fighter, Dodge dodge, Vec3 direction, boolean special, int level);

    /** The first blow a dodge evaded: after the combo has been loaded. */
    protected abstract void onCounter(ServerPlayer player, Fighter fighter, DamageSource source);

    /** Called every tick for each fighter who has fought (for lingering things: slicks, history, glows). */
    protected void tickFighter(ServerPlayer player, Fighter fighter, long now) {}

    protected int strikeCost(Move move) {
        String style = power.getPath();
        return move == Move.LEAD || move == Move.REAR ? com.jayptucker.heroesevolved.ability.PowerBalance.styleEnergy(style, "strike", COMMON.styleStrikeEnergyCost.get())
                : com.jayptucker.heroesevolved.ability.PowerBalance.styleEnergy(style, "finisher", COMMON.styleFlourishEnergyCost.get());
    }

    /** Whether the style dodges at all (a brawler who braces instead overrides {@link #dodge}). */
    protected int evadeTicks(boolean special) { return EVADE_TICKS; }

    @Override
    public int strike(ServerPlayer player) {
        if (!player.getMainHandItem().isEmpty() || !canFight(player)) return -1;
        long now = now(player);
        Fighter fighter = fighter(player);
        if (now < fighter.readyAt) return 0;
        Move move = next(player);
        boolean loaded = fighter.loaded(now) && move != Move.LOW;
        if (!loaded && !pay(player, strikeCost(move))) return -1;
        int level = Math.max(1, PlayerProgressionService.getLevel(player));
        // The counter is always thrown by the rear hand.
        if (loaded && move == Move.FINISHER) move = Move.REAR;
        throwMove(player, fighter, move, level, loaded);
        if (loaded) { fighter.loadedUntil = -1000; fighter.mark = null; }
        fighter.lastStrike = now;
        fighter.step = move == Move.FINISHER || move == Move.LOW ? 0 : (fighter.step + 1) % (level >= FINISHER_LEVEL ? 3 : 2);
        fighter.readyAt = now + switch (move) {
            case FINISHER -> FINISHER_RECOVERY;
            case LOW -> LOW_RECOVERY;
            default -> GAP;
        };
        player.swing(InteractionHand.MAIN_HAND, false);
        return 1;
    }

    @Override
    public boolean dodge(ServerPlayer player, Dodge dodge) {
        if (!canFight(player) || player.isPassenger() || player.isFallFlying() || StyleMotion.dashing(player)) return false;
        Fighter fighter = fighter(player);
        long now = now(player);
        if (now < fighter.dodgeReady) return false;
        int level = Math.max(1, PlayerProgressionService.getLevel(player));
        boolean special = dodge == Dodge.BACK && level >= FINISHER_LEVEL;
        if (!pay(player, com.jayptucker.heroesevolved.ability.PowerBalance.styleEnergy(power.getPath(), special ? "back_dodge" : "dodge", COMMON.styleDodgeEnergyCost.get() + (special ? 3 : 0)))) return false;
        Vec3 forward = StyleHits.level(player), right = StyleHits.right(forward);
        Vec3 direction = switch (dodge) {
            case LEFT -> right.scale(-1);
            case RIGHT -> right;
            case BACK -> forward.scale(-1);
        };
        int evade = evadeTicks(special);
        if (evade > 0) StyleMotion.evade(player, evade);
        StyleMotion.cushion(player, CUSHION_TICKS);
        fighter.dodgeReady = now + com.jayptucker.heroesevolved.ability.PowerBalance.styleCooldown(power.getPath(), "dodge", COMMON.styleDodgeCooldownTicks.get());
        dodgeMove(player, fighter, dodge, direction, special, level);
        return true;
    }

    @Override
    public void counter(ServerPlayer player, DamageSource source) {
        Fighter fighter = fighter(player);
        long now = now(player);
        fighter.loadedUntil = now + LOADED_TICKS;
        fighter.mark = source.getEntity() instanceof LivingEntity attacker && attacker != player ? attacker.getUUID() : null;
        fighter.step = 1;
        fighter.lastStrike = now;
        fighter.readyAt = Math.min(fighter.readyAt, now);
        onCounter(player, fighter, source);
    }

    protected boolean pay(ServerPlayer player, int cost) {
        com.jayptucker.heroesevolved.ability.Airborne.moved(player);
        if (cost <= 0) return true;
        if (!PlayerEnergyService.tryConsume(player, cost)) {
            PlayerEnergyService.refused(player);
            player.displayClientMessage(Component.translatable(tired), true);
            return false;
        }
        MasteryService.awardEnergySpent(player, cost);
        return true;
    }

    protected static void say(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.AQUA), true);
    }

    protected static void pose(ServerPlayer player, ArmPose pose, int ticks) { ArmPosePayload.show(player, pose, ticks); }

    @Override
    public void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (Fighter fighter : List.copyOf(fighters.values())) {
            ServerPlayer player = fighter.player;
            if (player == null || !player.isAlive()) continue;
            fighter.trail.addLast(player.position());
            while (fighter.trail.size() > 20) fighter.trail.removeFirst();
            tickFighter(player, fighter, now);
        }
    }

    @Override public void forget(ServerPlayer player) { fighters.remove(player.getUUID()); }

    @Override public void clear() { fighters.clear(); }
}
