package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.Ability;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.AbilityProgress;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles the one-time transition from a dormant power to an awakened one.
 *
 * <p>A dormant power from the Eclipse waits for adrenaline: it only wakes when its owner is in a
 * fight (recently hit by, or hitting, a living attacker) and their health falls below the
 * threshold. A blow that would cross that line, lethal or not, wakes the power first and cannot
 * kill; the power's intro then shields its owner and its release ends the fight.
 */
public final class AwakeningService {
    private static final ResourceLocation SPECIAL_ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "special");
    /** Protection between an adrenaline awakening and the power's own intro taking over. */
    private static final int GRACE_TICKS = 60;
    /** The second wind every awakening release grants: a floor on health, not a full heal. */
    private static final float SECOND_WIND_HEALTH = 0.4F;
    private static final int SECOND_WIND_RESISTANCE_TICKS = 20 * 6;

    /** Last game tick each player traded blows with a living attacker. */
    private static final Map<UUID, Long> LAST_COMBAT = new HashMap<>();
    private static final Map<UUID, Long> GRACE_UNTIL = new HashMap<>();

    private AwakeningService() {
    }

    // ------------------------------------------------------------------
    // Adrenaline
    // ------------------------------------------------------------------

    /** Called for any damage between living things; both sides are now in a fight. */
    public static void recordCombat(LivingEntity victim, DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }

        if (victim instanceof ServerPlayer player) {
            LAST_COMBAT.put(player.getUUID(), player.serverLevel().getGameTime());
        }

        if (attacker instanceof ServerPlayer player) {
            LAST_COMBAT.put(player.getUUID(), player.serverLevel().getGameTime());
        }
    }

    public static boolean inCombat(ServerPlayer player) {
        Long last = LAST_COMBAT.get(player.getUUID());
        return last != null && player.serverLevel().getGameTime() - last
                <= HeroesEvolvedConfig.COMMON.awakeningCombatWindowSeconds.get() * 20L;
    }

    /** 0 when calm or unhurt, rising as a fight drags health down, 1 at the awakening threshold. */
    public static float adrenaline(ServerPlayer player) {
        if (!inCombat(player)) {
            return 0;
        }

        float threshold = threshold();
        float span = Math.max(1, player.getMaxHealth() - threshold);
        return Mth.clamp((player.getMaxHealth() - player.getHealth()) / span, 0, 1);
    }

    public static boolean hasDormantPower(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower()
                .filter(entry -> !entry.getValue().isUnlocked())
                .isPresent();
    }

    /** Runs every tick while powers are not suppressed. */
    public static void tick(ServerPlayer player) {
        if (!hasDormantPower(player)) {
            return;
        }

        // The dormant power stirs as the fight turns: a heartbeat that quickens toward awakening.
        float adrenaline = adrenaline(player);
        if (adrenaline >= 0.5F) {
            int interval = Math.round(Mth.lerp((adrenaline - 0.5F) * 2, 30, 12));
            if (player.serverLevel().getGameTime() % interval == 0) {
                player.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS,
                        0.4F + 0.6F * adrenaline, 0.9F + 0.3F * adrenaline);
            }
        }

        tryAwakenFromAdrenaline(player);
    }

    /** Wakes a dormant power if its owner is in a fight and below the health threshold. */
    public static boolean tryAwakenFromAdrenaline(ServerPlayer player) {
        if (player.getHealth() >= threshold() || !inCombat(player)
                || EclipseService.arePowersSuppressed(player)) {
            return false;
        }

        return awakenFromAdrenaline(player);
    }

    /**
     * Final damage about to land on a player. A blow in a fight that would take a dormant owner
     * below the threshold wakes the power before it lands, and is capped so it cannot kill.
     */
    public static float interceptDamage(ServerPlayer player, DamageSource source, float damage) {
        recordCombat(player, source);
        // Absorption hearts soak the blow after this point, so they count toward what survives it.
        float pool = player.getHealth() + player.getAbsorptionAmount();

        if (damage <= 0 || !hasDormantPower(player) || !inCombat(player)
                || EclipseService.arePowersSuppressed(player)
                || pool - damage >= threshold()
                || !awakenFromAdrenaline(player)) {
            return damage;
        }

        return Math.min(damage, Math.max(0, pool - 1));
    }

    /** True while an adrenaline awakening keeps its owner alive before the intro takes over. */
    public static boolean inGrace(ServerPlayer player) {
        Long until = GRACE_UNTIL.get(player.getUUID());

        if (until == null) {
            return false;
        }

        // Time Manipulation's intro is a vision elsewhere: shield its owner until the city takes them.
        if (player.serverLevel().getGameTime() < until || TimeIntroService.isQueued(player)) {
            return true;
        }

        GRACE_UNTIL.remove(player.getUUID());
        return false;
    }

    /**
     * Shared by every awakening release: the surge that lets a player who was about to lose a
     * fight stand back up. Each power adds its own way of ending the fight.
     */
    public static void secondWind(ServerPlayer player) {
        player.setHealth(Math.max(player.getHealth(), player.getMaxHealth() * SECOND_WIND_HEALTH));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, SECOND_WIND_RESISTANCE_TICKS, 1));
        player.clearFire();
    }

    public static void forget(ServerPlayer player) {
        LAST_COMBAT.remove(player.getUUID());
        GRACE_UNTIL.remove(player.getUUID());
    }

    public static void clear() {
        LAST_COMBAT.clear();
        GRACE_UNTIL.clear();
    }

    private static boolean awakenFromAdrenaline(ServerPlayer player) {
        if (!awakenAssigned(player)) {
            return false;
        }

        GRACE_UNTIL.put(player.getUUID(), player.serverLevel().getGameTime() + GRACE_TICKS);
        player.displayClientMessage(Component.literal("Your heart slams. Something inside you wakes up.")
                .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC), true);
        player.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 1.4F);
        ScreenTintPayload.send(player, 0x70B00000, 24, ScreenTintPayload.Style.VIGNETTE);
        return true;
    }

    private static float threshold() {
        return HeroesEvolvedConfig.COMMON.awakeningHealthThreshold.get().floatValue();
    }

    // ------------------------------------------------------------------
    // Awakening
    // ------------------------------------------------------------------

    /** Awakens a dormant assignment now: unlock, the power's own intro, and the shared advancement. */
    public static boolean awakenAssigned(ServerPlayer player) {
        Optional<Map.Entry<ResourceLocation, AbilityProgress>> assignment =
                PlayerAbilityService.getData(player).assignedPower();

        if (assignment.isEmpty() || assignment.get().getValue().isUnlocked()) {
            return false;
        }

        ResourceLocation abilityId = assignment.get().getKey();

        if (!PlayerAbilityService.unlockAbility(player, abilityId)) {
            return false;
        }

        Ability ability = AbilityRegistry.ABILITIES.get(abilityId);

        if (ability != null) {
            // Every other system reads the shared progression level; the
            // stored per-ability level is always 1 and would make awakening
            // reactions ignore a player's actual power level.
            ability.onAwaken(new AbilityUseContext(
                    player,
                    abilityId,
                    PlayerProgressionService.getLevel(player),
                    false
            ));
        }

        awardSpecial(player);
        return true;
    }

    private static void awardSpecial(ServerPlayer player) {
        MinecraftServer server = player.getServer();

        if (server == null) {
            return;
        }

        AdvancementHolder advancement = server.getAdvancements().get(
                SPECIAL_ADVANCEMENT
        );

        if (advancement == null) {
            HeroesEvolved.LOGGER.error(
                    "Could not find awakening advancement {}.",
                    SPECIAL_ADVANCEMENT
            );
            return;
        }

        player.getAdvancements().award(advancement, "granted");
    }
}
