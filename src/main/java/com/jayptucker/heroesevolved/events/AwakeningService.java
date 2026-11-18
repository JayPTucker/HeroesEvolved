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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import com.jayptucker.heroesevolved.intro.Danger;
import com.jayptucker.heroesevolved.intro.Danger.Peril;
import com.jayptucker.heroesevolved.intro.TimeAwakening;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles the one-time transition from a dormant power to an awakened one.
 *
 * <p>A dormant power from the Eclipse waits for danger: it wakes when its owner, or an ally near them
 * (a friendly player or their own pet), is about to die of something it can answer ({@link Danger.Peril}):
 * losing a fight or a creeper about to go off for every power, a deadly fall, lava or drowning for the powers
 * that can help, and for Space-Time anyone under two hearts at all. A blow that would cross that line, lethal
 * or not, wakes the power first and cannot kill. The endangered one becomes the intro's
 * <em>ward</em>: the intro shields them, and its release saves them in the power's own way (a speedster
 * runs them out, a flyer carries them off; see {@code docs/power-intros.md}).
 */
public final class AwakeningService {
    private static final ResourceLocation SPECIAL_ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "special");
    /** Protection between an adrenaline awakening and the power's own intro taking over. */
    private static final int GRACE_TICKS = 60;
    /** The second wind every awakening release grants: a floor on health, not a full heal. */
    private static final float SECOND_WIND_HEALTH = 0.4F;
    private static final int SECOND_WIND_RESISTANCE_TICKS = 20 * 6;
    /** Allies are scanned this often; a blow that would cross the line is caught the moment it lands. */
    private static final int ALLY_SCAN_TICKS = 5;

    /** Last game tick each living thing traded blows with a living attacker. */
    private static final Map<UUID, Long> LAST_COMBAT = new HashMap<>();
    /** Owners and wards shielded until the intro takes over. */
    private static final Map<UUID, Long> GRACE_UNTIL = new HashMap<>();
    /** Whom each awakening is saving, read by the intro while the power unlocks. */
    private static final Map<UUID, LivingEntity> PENDING_WARD = new HashMap<>();
    /** What the ward is being saved from, read by the intro with the ward. */
    private static final Map<UUID, Peril> PENDING_PERIL = new HashMap<>();
    /** Last game tick each player was hurt by anything. */
    private static final Map<UUID, Long> LAST_HURT = new HashMap<>();
    /** Wards of a Space-Time awakening, shielded until their owner's vision ends: ward -> owner. */
    private static final Map<UUID, UUID> TIME_WARDS = new HashMap<>();
    private static final Map<UUID, Long> SKY_HINTS = new HashMap<>();

    private AwakeningService() {
    }

    // ------------------------------------------------------------------
    // Adrenaline
    // ------------------------------------------------------------------

    /** Called for any damage between living things; both sides are now in a fight. */
    public static void recordCombat(LivingEntity victim, DamageSource source) {
        if (victim.level().isClientSide) {
            return;
        }
        long now = victim.level().getGameTime();
        // Any hurt counts for Space-Time, which answers anyone under two hearts who is still being hurt.
        if (victim instanceof net.minecraft.world.entity.player.Player) {
            LAST_HURT.put(victim.getUUID(), now);
        }

        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }

        LAST_COMBAT.put(victim.getUUID(), now);
        LAST_COMBAT.put(attacker.getUUID(), now);

        if (LAST_COMBAT.size() > 2048) {
            long window = window();
            LAST_COMBAT.values().removeIf(at -> now - at > window);
        }
    }

    /** Hurt by anything at all within the combat window (players only). */
    public static boolean recentlyHurt(LivingEntity entity) {
        Long last = LAST_HURT.get(entity.getUUID());
        return last != null && entity.level().getGameTime() - last <= window();
    }

    public static boolean inCombat(LivingEntity entity) {
        Long last = LAST_COMBAT.get(entity.getUUID());
        return last != null && entity.level().getGameTime() - last <= window();
    }

    /** In a fight and about to lose it: below the threshold (scaled down for small pets). */
    public static boolean endangered(LivingEntity entity) {
        return entity.isAlive() && entity.getHealth() < threshold(entity) && inCombat(entity);
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
        return dormantPower(player) != null;
    }

    /** The path of the owner's dormant power ({@code "flight"}), or null. */
    public static String dormantPower(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower()
                .filter(entry -> !entry.getValue().isUnlocked())
                .map(entry -> entry.getKey().getPath())
                .orElse(null);
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

        if (!tryAwakenFromAdrenaline(player) && player.serverLevel().getGameTime() % ALLY_SCAN_TICKS == 0
                && !answerBlast(player)) {
            answerAlly(player);
        }
    }

    /**
     * What this creature is in danger from that this power answers, or null: losing a fight, a deadly landing,
     * lava or fire, drowning, or (Space-Time only) simply being under two hearts and still getting hurt.
     * Blasts are found from the explosive's side, in {@link #answerBlast}.
     */
    public static Peril peril(LivingEntity entity, String power) {
        if (endangered(entity)) {
            return Peril.FIGHT;
        }

        Peril hazard = Danger.hazard(entity, threshold(entity));
        if (hazard != null && Danger.answers(power, hazard)) {
            return hazard;
        }

        if (Danger.answers(power, Peril.LOW) && entity instanceof net.minecraft.world.entity.player.Player
                && entity.isAlive() && entity.getHealth() + entity.getAbsorptionAmount() < threshold() && recentlyHurt(entity)) {
            return Peril.LOW;
        }

        return null;
    }

    /** Wakes a dormant power if its owner is in danger it can answer (a fight going badly, a fall, fire, water). */
    public static boolean tryAwakenFromAdrenaline(ServerPlayer player) {
        String power = dormantPower(player);
        if (power == null || EclipseService.arePowersSuppressed(player)) {
            return false;
        }

        Peril peril = peril(player, power);
        if (peril == null) {
            return false;
        }

        if (!answers(player, player, power)) {
            hintSky(player);
            return false;
        }

        return awakenFromAdrenaline(player, player, peril);
    }

    /** Wakes a dormant power for the nearest ally in reach who is in danger it can answer. */
    public static boolean answerAlly(ServerPlayer owner) {
        String power = dormantPower(owner);
        if (power == null || EclipseService.arePowersSuppressed(owner)) {
            return false;
        }

        double reach = Danger.reach(power);
        boolean blocked = false;
        LivingEntity ward = null;
        Peril wardPeril = null;
        double nearest = Double.MAX_VALUE;
        for (LivingEntity ally : owner.serverLevel().getEntitiesOfClass(LivingEntity.class,
                owner.getBoundingBox().inflate(reach), ally -> Danger.ally(owner, ally)
                        && ally.distanceToSqr(owner) <= reach * reach)) {
            Peril peril = peril(ally, power);
            if (peril == null) {
                continue;
            }
            if (!answers(owner, ally, power)) {
                blocked = true;
                continue;
            }
            double distance = ally.distanceToSqr(owner);
            if (distance < nearest) {
                nearest = distance;
                ward = ally;
                wardPeril = peril;
            }
        }

        if (ward == null && blocked) {
            hintSky(owner);
        }

        return ward != null && awakenFromAdrenaline(owner, ward, wardPeril);
    }

    /**
     * A creeper hissing or TNT burning down, about to take the owner or an ally in reach below the line: the
     * power wakes before it goes off. The owner is answered first, then the nearest ally.
     */
    public static boolean answerBlast(ServerPlayer owner) {
        String power = dormantPower(owner);
        if (power == null || EclipseService.arePowersSuppressed(owner)) {
            return false;
        }

        double reach = Danger.reach(power);
        List<Entity> explosives = Danger.explosives(owner.level(), owner.position(), reach + 8);
        if (explosives.isEmpty()) {
            return false;
        }

        List<LivingEntity> people = new ArrayList<>();
        people.add(owner);
        people.addAll(owner.serverLevel().getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(reach),
                ally -> Danger.ally(owner, ally) && ally.distanceToSqr(owner) <= reach * reach));
        people.sort(Comparator.comparingDouble(person -> person.distanceToSqr(owner)));

        boolean blocked = false;
        for (LivingEntity person : people) {
            for (Entity explosive : explosives) {
                if (!Danger.blastEndangers(explosive, person, threshold(person))) {
                    continue;
                }
                if (!answers(owner, person, power)) {
                    blocked = true;
                    break;
                }
                return awakenFromAdrenaline(owner, person, Peril.BLAST);
            }
        }

        if (blocked) {
            hintSky(owner);
        }
        return false;
    }

    /**
     * Whether this power can answer for this ward right now. Flight only wakes under open sky, and can
     * only carry off someone who is out under it too; every other power answers anywhere.
     */
    public static boolean answers(ServerPlayer owner, LivingEntity ward, String power) {
        if ("flight".equals(power)) {
            return Danger.openSky(owner) && Danger.openSky(ward);
        }

        return true;
    }

    /**
     * Final damage about to land on a player. A blow that would take a dormant owner below the threshold,
     * from a danger their power answers (a fight, a blast, and for some powers a fall, fire or drowning),
     * wakes the power before it lands, and is capped so it cannot kill.
     */
    public static float interceptDamage(ServerPlayer player, DamageSource source, float damage) {
        recordCombat(player, source);
        // Absorption hearts soak the blow after this point, so they count toward what survives it.
        float pool = player.getHealth() + player.getAbsorptionAmount();
        String power = dormantPower(player);
        Peril peril = Danger.of(source, player);

        if (damage <= 0 || power == null || peril == null || !Danger.answers(power, peril)
                || peril == Peril.FIGHT && !inCombat(player)
                || EclipseService.arePowersSuppressed(player)
                || pool - damage >= threshold()) {
            return damage;
        }

        if (!answers(player, player, power)) {
            hintSky(player);
            return damage;
        }

        if (!awakenFromAdrenaline(player, player, peril)) {
            return damage;
        }

        return Math.min(damage, Math.max(0, pool - 1));
    }

    /**
     * Final damage about to land on anyone. A blow that would take an ally of a dormant owner below the line,
     * from a danger that owner's power answers, wakes it to save them, and is capped so it cannot kill.
     */
    public static float interceptAllyDamage(LivingEntity victim, DamageSource source, float damage) {
        Peril peril = Danger.of(source, victim);
        if (damage <= 0 || peril == null || !(victim.level() instanceof ServerLevel level)) {
            return damage;
        }

        float pool = victim.getHealth() + victim.getAbsorptionAmount();
        if (pool - damage >= threshold(victim)) {
            return damage;
        }

        Entity attacker = source.getEntity();
        ServerPlayer rescuer = null;
        double nearest = Double.MAX_VALUE;
        for (ServerPlayer owner : level.players()) {
            String power = owner == victim || owner == attacker ? null : dormantPower(owner);
            if (power == null || !Danger.answers(power, peril) || EclipseService.arePowersSuppressed(owner)
                    || !Danger.ally(owner, victim)) {
                continue;
            }
            double reach = Danger.reach(power);
            double distance = owner.distanceToSqr(victim);
            if (distance > reach * reach || distance >= nearest) {
                continue;
            }
            if (!answers(owner, victim, power)) {
                hintSky(owner);
                continue;
            }
            rescuer = owner;
            nearest = distance;
        }

        if (rescuer == null || !awakenFromAdrenaline(rescuer, victim, peril)) {
            return damage;
        }

        return Math.min(damage, Math.max(0, pool - 1));
    }

    /** True while an awakening keeps its owner or ward alive before the intro takes over. */
    public static boolean inGrace(LivingEntity entity) {
        UUID timeOwner = TIME_WARDS.get(entity.getUUID());
        if (timeOwner != null && entity.getServer() != null) {
            ServerPlayer owner = entity.getServer().getPlayerList().getPlayer(timeOwner);
            if (owner != null && (TimeIntroService.isQueued(owner) || TimeIntroService.isVisiting(owner))) {
                return true;
            }
        }

        Long until = GRACE_UNTIL.get(entity.getUUID());

        if (until == null) {
            if (timeOwner != null) TIME_WARDS.remove(entity.getUUID());
            return false;
        }

        // Time Manipulation's intro is a vision elsewhere: shield its owner until the city takes them.
        if (entity.level().getGameTime() < until
                || entity instanceof ServerPlayer player && TimeIntroService.isQueued(player)) {
            return true;
        }

        GRACE_UNTIL.remove(entity.getUUID());
        TIME_WARDS.remove(entity.getUUID());
        return false;
    }

    /** The ward an awakening in progress is saving (the owner when it is their own fight). */
    public static LivingEntity pendingWard(ServerPlayer owner) {
        LivingEntity ward = PENDING_WARD.get(owner.getUUID());
        return ward == null || !ward.isAlive() || ward.level() != owner.level() ? owner : ward;
    }

    /** What the awakening in progress is saving its ward from, or null when it isn't a danger awakening. */
    public static Peril pendingPeril(ServerPlayer owner) {
        return PENDING_PERIL.get(owner.getUUID());
    }

    /**
     * Shared by every awakening release: the surge that lets someone who was about to lose a
     * fight stand back up. Each power adds its own way of ending the fight.
     */
    public static void secondWind(LivingEntity entity) {
        secondWind(entity, true);
    }

    /** A low cinematic camera should not be filled by the owner's own Resistance particles. */
    public static void secondWind(LivingEntity entity, boolean showParticles) {
        entity.setHealth(Math.max(entity.getHealth(), entity.getMaxHealth() * SECOND_WIND_HEALTH));
        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, SECOND_WIND_RESISTANCE_TICKS, 1,
                false, showParticles, true));
        entity.clearFire();
        entity.setAirSupply(entity.getMaxAirSupply());
        entity.resetFallDistance();
    }

    /** A completed Space-Time vision: the ally the owner woke for is back on their feet too. */
    public static void timeVisionEnded(ServerPlayer owner) {
        for (Map.Entry<UUID, UUID> entry : Map.copyOf(TIME_WARDS).entrySet()) {
            if (!entry.getValue().equals(owner.getUUID())) {
                continue;
            }
            TIME_WARDS.remove(entry.getKey());
            GRACE_UNTIL.remove(entry.getKey());
            Entity found = owner.serverLevel().getEntity(entry.getKey());
            if (!(found instanceof LivingEntity ward) || !ward.isAlive()) {
                continue;
            }
            secondWind(ward);
            for (net.minecraft.world.entity.Mob mob : owner.serverLevel().getEntitiesOfClass(
                    net.minecraft.world.entity.Mob.class, ward.getBoundingBox().inflate(12),
                    mob -> mob.isAlive() && (mob instanceof net.minecraft.world.entity.monster.Enemy
                            || mob.getTarget() == ward))) {
                mob.setTarget(null);
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 6));
                mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
            }
        }
    }

    public static void forget(ServerPlayer player) {
        LAST_COMBAT.remove(player.getUUID());
        GRACE_UNTIL.remove(player.getUUID());
        PENDING_WARD.remove(player.getUUID());
        PENDING_PERIL.remove(player.getUUID());
        LAST_HURT.remove(player.getUUID());
        SKY_HINTS.remove(player.getUUID());
        TIME_WARDS.remove(player.getUUID());
        TIME_WARDS.values().removeIf(owner -> owner.equals(player.getUUID()));
    }

    public static void clear() {
        LAST_COMBAT.clear();
        GRACE_UNTIL.clear();
        PENDING_WARD.clear();
        PENDING_PERIL.clear();
        LAST_HURT.clear();
        TIME_WARDS.clear();
        SKY_HINTS.clear();
    }

    private static boolean awakenFromAdrenaline(ServerPlayer player, LivingEntity ward, Peril peril) {
        String power = dormantPower(player);
        boolean time = "time_manipulation".equals(power);
        PENDING_WARD.put(player.getUUID(), ward);
        PENDING_PERIL.put(player.getUUID(), peril);
        // Space-Time answers by stopping time where the danger is; its vision waits until time starts again.
        if (time) TimeAwakening.arm(player);
        boolean awakened = false;
        try {
            awakened = awakenAssigned(player);
            if (!awakened) {
                return false;
            }
        } finally {
            PENDING_WARD.remove(player.getUUID());
            PENDING_PERIL.remove(player.getUUID());
            if (time && !awakened) TimeAwakening.disarm(player);
        }

        long until = player.serverLevel().getGameTime() + GRACE_TICKS
                + (time ? TimeAwakening.FREEZE_TICKS : 0);
        GRACE_UNTIL.put(player.getUUID(), until);
        if (time) TimeAwakening.begin(player, ward, peril);
        // It doesn't ask. The heart slams, the edges of sight go red, and the power is already coming out.
        player.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 1.4F);
        player.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 0.8F);
        ScreenTintPayload.send(player, 0x70B00000, 24, ScreenTintPayload.Style.VIGNETTE);
        String cause = peril == Peril.FIGHT || peril == Peril.LOW ? "" : "." + peril.name().toLowerCase(java.util.Locale.ROOT);

        if (ward == player) {
            player.displayClientMessage(Component.translatable("awakening.heroesevolved.self" + cause)
                    .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC), true);
            return true;
        }

        // Someone you'd fight for is going down: the power wakes for them.
        GRACE_UNTIL.put(ward.getUUID(), until);
        if (time) {
            TIME_WARDS.put(ward.getUUID(), player.getUUID());
        }
        player.displayClientMessage(Component.translatable("awakening.heroesevolved.ally" + cause, ward.getDisplayName())
                .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC), true);
        if (ward instanceof ServerPlayer saved) {
            saved.displayClientMessage(Component.translatable("awakening.heroesevolved.saved", player.getDisplayName())
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), true);
        }
        return true;
    }

    /** Flight in a cave: the power strains toward the sky but cannot wake until there is sky above. */
    private static void hintSky(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        Long last = SKY_HINTS.get(player.getUUID());
        if (last != null && now - last < 60) {
            return;
        }
        SKY_HINTS.put(player.getUUID(), now);
        player.displayClientMessage(Component.translatable("awakening.heroesevolved.needs_sky")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC), true);
    }

    private static long window() {
        return HeroesEvolvedConfig.COMMON.awakeningCombatWindowSeconds.get() * 20L;
    }

    /** Players use the configured threshold; smaller creatures (a cat, a wolf) are in danger sooner. */
    private static float threshold(LivingEntity entity) {
        return entity instanceof net.minecraft.world.entity.player.Player ? threshold()
                : Math.min(threshold(), entity.getMaxHealth() * 0.4F);
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
