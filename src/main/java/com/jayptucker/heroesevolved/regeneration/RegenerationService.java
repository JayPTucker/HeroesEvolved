package com.jayptucker.heroesevolved.regeneration;

import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;

import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.time.TemporalParadoxService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rapid cellular regeneration, after Claire Bennet: mending wounds on
 * demand, sharing healing blood, shrugging off harm, and coming back from
 * the dead.
 */
public final class RegenerationService {
    public static final ResourceLocation RESURRECTION_ID =
            ResourceLocation.fromNamespaceAndPath(
                    com.jayptucker.heroesevolved.HeroesEvolved.MOD_ID,
                    "regeneration_resurrection"
            );

    private static final DustParticleOptions BLOOD =
            new DustParticleOptions(new Vector3f(0.82F, 0.03F, 0.08F), 1.0F);
    private static final DustParticleOptions BRIGHT_BLOOD =
            new DustParticleOptions(new Vector3f(1.00F, 0.25F, 0.25F), 1.4F);
    private static final float BLOOD_GIFT_SELF_COST = 3.0F;
    private static final int RESURRECTION_UNLOCK_LEVEL = 5;

    // Effects a regenerating body purges. Weakness, Nausea and Mining
    // Fatigue are left alone: those are overexertion's price, not toxins.
    private static final List<Holder<MobEffect>> TOXINS = List.of(
            MobEffects.POISON,
            MobEffects.WITHER,
            MobEffects.HUNGER,
            MobEffects.BLINDNESS,
            MobEffects.DARKNESS
    );

    private static final Map<UUID, Long> INDESTRUCTIBLE_UNTIL = new HashMap<>();

    private RegenerationService() {
    }

    // ------------------------------------------------------------------
    // Passive
    // ------------------------------------------------------------------

    /** Passive healing grows with level: +25% per level above the first. */
    public static float passiveHealAmount(int powerLevel) {
        return 1.0F + 0.25F * (Math.max(1, powerLevel) - 1);
    }

    /** From Level 2 on, a regenerating body flushes toxins on its own. */
    public static void purgeToxinsPassively(ServerPlayer player, int powerLevel) {
        if (powerLevel >= 2) {
            purgeToxins(player);
        }
    }

    // ------------------------------------------------------------------
    // Rapid Mending
    // ------------------------------------------------------------------

    public static void rapidMending(ServerPlayer player, int powerLevel) {
        player.heal(4.0F + 2.0F * powerLevel);
        boolean purged = purgeToxins(player);
        player.clearFire();

        ServerLevel level = player.serverLevel();
        // The successful-action cue builds the healing strands on each client.
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 1.0F, 0.7F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BONE_BLOCK_PLACE, SoundSource.PLAYERS, 0.9F, 1.3F);
        ScreenTintPayload.send(player, 0x50A01010, 20, ScreenTintPayload.Style.VIGNETTE);
        player.displayClientMessage(Component.literal(purged
                ? "Your body burns the poison away."
                : "Your bones click back into place.")
                .withStyle(ChatFormatting.RED), true);
        MasteryService.markPowerUse(player, ModAbilities.REGENERATION_ID);
    }

    // ------------------------------------------------------------------
    // Blood Gift
    // ------------------------------------------------------------------

    public static LivingEntity findBloodGiftTarget(ServerPlayer player) {
        return Targeting.livingEntityInSight(player, 5.0D,
                target -> !(target instanceof TemporalEchoEntity));
    }

    /**
     * Claire's blood heals others. It costs her a little of her own
     * health, which she regrows, and it burns the undead.
     */
    public static boolean bloodGift(ServerPlayer player, int powerLevel) {
        LivingEntity target = findBloodGiftTarget(player);
        if (target == null) {
            player.displayClientMessage(Component.literal(
                    "There is no one close enough to share your blood with."
            ), true);
            return false;
        }

        ServerLevel level = player.serverLevel();
        float potency = 6.0F + 2.0F * powerLevel;
        if (target.isInvertedHealAndHarm()) {
            target.hurt(player.damageSources().indirectMagic(player, player),
                    potency);
            level.sendParticles(ParticleTypes.SMOKE, target.getX(),
                    target.getY() + target.getBbHeight() / 2, target.getZ(),
                    20, 0.3D, 0.4D, 0.3D, 0.02D);
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.6F);
        } else {
            target.heal(potency);
            purgeToxins(target);
            target.addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION, 20 * 5, 1));
            level.sendParticles(ParticleTypes.HEART, target.getX(),
                    target.getY() + target.getBbHeight() + 0.3D, target.getZ(),
                    5, 0.4D, 0.2D, 0.4D, 0.0D);
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS,
                    0.6F, 1.6F);
            if (target instanceof ServerPlayer healed) {
                healed.displayClientMessage(Component.literal(
                        player.getName().getString()
                                + "'s blood knits your wounds closed."
                ).withStyle(ChatFormatting.RED), true);
            }
        }

        // A thread of blood from Claire to the one she saves.
        Vec3 from = player.position().add(0.0D, 1.1D, 0.0D);
        Vec3 to = target.position().add(0.0D, target.getBbHeight() / 2, 0.0D);
        AbilityEffects.send(level,
                Kind.BLOOD_THREAD,
                from, to, 1.0D);
        AbilityEffects.send(level,
                Kind.REGENERATION,
                target.position(), target.position(), 1.0D);

        // Her own cut, never fatal: she will regrow it in seconds.
        player.setHealth(Math.max(1.0F, player.getHealth() - BLOOD_GIFT_SELF_COST));
        MasteryService.markPowerUse(player, ModAbilities.REGENERATION_ID);
        return true;
    }

    // ------------------------------------------------------------------
    // Indestructible
    // ------------------------------------------------------------------

    /**
     * Claire jumps from buildings and walks out of fires. For a while,
     * falls cannot hurt her, nothing knocks her down, and other damage is
     * dulled.
     */
    public static void indestructible(ServerPlayer player, int powerLevel) {
        int durationTicks = 20 * (10 + 2 * Math.max(0, powerLevel - 3));
        INDESTRUCTIBLE_UNTIL.put(player.getUUID(),
                player.serverLevel().getGameTime() + durationTicks);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                durationTicks, powerLevel >= 5 ? 1 : 0));
        player.serverLevel().playSound(null, player.getX(), player.getY(),
                player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(),
                SoundSource.PLAYERS, 1.0F, 0.7F);
        player.displayClientMessage(Component.literal(
                "You can't feel pain anymore."
        ).withStyle(ChatFormatting.RED), true);
        MasteryService.markPowerUse(player, ModAbilities.REGENERATION_ID);
    }

    public static boolean isIndestructible(ServerPlayer player) {
        Long until = INDESTRUCTIBLE_UNTIL.get(player.getUUID());
        return until != null && player.serverLevel().getGameTime() < until;
    }

    // ------------------------------------------------------------------
    // Resurrection
    // ------------------------------------------------------------------

    /**
     * At Level 5 Claire returns from death. Some deaths cannot be undone:
     * the void, /kill, and being erased from the timeline.
     *
     * @return true if the player was brought back and death was averted
     */
    public static boolean tryResurrect(
            ServerPlayer player,
            int powerLevel,
            DamageSource source
    ) {
        if (powerLevel < RESURRECTION_UNLOCK_LEVEL
                || CooldownService.isOnCooldown(player, RESURRECTION_ID)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || source.is(DamageTypes.GENERIC_KILL)
                || source.is(TemporalParadoxService.TEMPORAL_PARADOX_DAMAGE)) {
            return false;
        }

        player.setHealth(1.0F);
        player.clearFire();
        player.setAirSupply(player.getMaxAirSupply());
        purgeToxins(player);
        player.invulnerableTime = 60;
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 6, 3));
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));

        ServerLevel level = player.serverLevel();
        level.sendParticles(BRIGHT_BLOOD, player.getX(), player.getY() + 1.0D,
                player.getZ(), 60, 0.4D, 0.8D, 0.4D, 0.0D);
        AbilityEffects.at(player, Kind.REGENERATION, 1.8D);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(),
                player.getY() + 1.0D, player.getZ(), 12, 0.4D, 0.6D, 0.4D, 0.1D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5F, 1.0F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.8F, 1.4F);
        // Coming back: a crimson flash, then the world swims into focus.
        ScreenTintPayload.send(player, 0xC0700000, 30, ScreenTintPayload.Style.FLASH);
        ScreenTintPayload.send(player, 0x80500000, 100,
                ScreenTintPayload.Style.VIGNETTE);
        player.sendSystemMessage(Component.literal(
                "You gasp back to life."
        ).withStyle(ChatFormatting.DARK_RED));

        CooldownService.startCooldown(player, RESURRECTION_ID,
                HeroesEvolvedConfig.COMMON.regenerationResurrectionCooldownTicks.get());
        PlayerPowerSyncService.sync(player);
        MasteryService.markPowerUse(player, ModAbilities.REGENERATION_ID);
        return true;
    }

    // ------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        INDESTRUCTIBLE_UNTIL.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || gameTime >= entry.getValue()) {
                return true;
            }
            if (gameTime % 12 == 0) {
                AbilityEffects.at(player,
                        Kind.REGENERATION, 1.2D);
            }
            return false;
        });

        HEALING_AURAS.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || gameTime >= entry.getValue().until()) {
                return true;
            }
            if (gameTime % 20 == 0) {
                pulseHealingAura(player, entry.getValue().powerLevel());
            }
            return false;
        });
    }

    // ------------------------------------------------------------------
    // Healing Aura
    // ------------------------------------------------------------------

    private static final double AURA_RADIUS = 8.0D;
    private static final Map<UUID, Aura> HEALING_AURAS = new HashMap<>();

    public static boolean hasHealingAura(ServerPlayer player) {
        return HEALING_AURAS.containsKey(player.getUUID());
    }

    /**
     * Claire's blood in the air: for ten seconds, everyone nearby heals
     * with her each second, and the undead burn in it.
     */
    public static void healingAura(ServerPlayer player, int powerLevel) {
        HEALING_AURAS.put(player.getUUID(), new Aura(
                player.serverLevel().getGameTime() + 20 * 10, powerLevel));
        pulseHealingAura(player, powerLevel);
        player.serverLevel().playSound(null, player.getX(), player.getY(),
                player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS,
                0.9F, 1.4F);
        MasteryService.markPowerUse(player, ModAbilities.REGENERATION_ID);
    }

    private static void pulseHealingAura(ServerPlayer player, int powerLevel) {
        ServerLevel level = player.serverLevel();
        float potency = 1.0F + powerLevel * 0.5F;

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(AURA_RADIUS),
                entity -> entity.isAlive()
                        && entity.distanceToSqr(player) <= AURA_RADIUS * AURA_RADIUS)) {
            if (entity.isInvertedHealAndHarm()) {
                entity.hurt(player.damageSources().indirectMagic(player, player), potency);
            } else if (entity == player || entity instanceof ServerPlayer
                    || !(entity instanceof net.minecraft.world.entity.monster.Enemy)) {
                // Allies and peaceful creatures only; monsters are not healed.
                entity.heal(potency);
                if (entity != player) {
                    AbilityEffects.send(level, Kind.BLOOD_THREAD,
                            player.position().add(0.0D, 1.0D, 0.0D),
                            entity.position().add(0.0D, entity.getBbHeight() / 2, 0.0D),
                            0.6D);
                }
            }
        }

        AbilityEffects.at(player, Kind.HEALING_AURA, AURA_RADIUS);
    }

    private record Aura(long until, int powerLevel) {
    }

    /**
     * A regenerator bleeds visibly when hurt, and the wound starts closing
     * at once: a spray of blood, then a few drops drawn back in.
     */
    public static void bleed(ServerPlayer player, float damage) {
        ServerLevel level = player.serverLevel();
        int count = Math.min(30, 6 + (int) (damage * 3.0F));
        level.sendParticles(BLOOD, player.getX(), player.getY() + 1.0D,
                player.getZ(), count, 0.25D, 0.4D, 0.25D, 0.0D);
        level.sendParticles(BRIGHT_BLOOD, player.getX(), player.getY() + 1.1D,
                player.getZ(), count / 3, 0.15D, 0.2D, 0.15D, 0.0D);
    }

    public static void forget(ServerPlayer player) {
        INDESTRUCTIBLE_UNTIL.remove(player.getUUID());
        HEALING_AURAS.remove(player.getUUID());
    }

    private static boolean purgeToxins(LivingEntity entity) {
        boolean purged = false;
        for (Holder<MobEffect> toxin : TOXINS) {
            purged |= entity.removeEffect(toxin);
        }
        return purged;
    }
}
