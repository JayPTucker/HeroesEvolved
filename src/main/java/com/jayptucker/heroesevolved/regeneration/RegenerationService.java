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
 * Rapid cellular regeneration, after Claire Bennet: the one who keeps getting back up. Damage she
 * takes becomes Regrowth (0 to 100), stored healing that her body burns to close wounds fast and
 * that her powers spend. She mends, shares her blood, turns reckless and unbreakable, takes her
 * friends' wounds onto herself, gives someone else a second life, and comes back from the dead.
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

    public static final int MAX_REGROWTH = 100, SECOND_LIFE_COST = 50;
    public static final int SECOND_LIFE_COOLDOWN_TICKS = 20 * 90, SECOND_LIFE_TICKS = 20 * 60;
    public static final ResourceLocation SECOND_LIFE_ID = ResourceLocation.fromNamespaceAndPath(
            com.jayptucker.heroesevolved.HeroesEvolved.MOD_ID, "regeneration_second_life");
    private static final Map<UUID, Long> INDESTRUCTIBLE_UNTIL = new HashMap<>();
    private static final Map<UUID, Integer> REGROWTH = new HashMap<>();
    private static final Map<UUID, Integer> SENT = new HashMap<>();
    /** Creatures carrying Claire's blood, and when it stops protecting them. */
    private static final Map<UUID, Long> SECOND_LIFE = new HashMap<>();
    private static int sharing;

    private RegenerationService() {
    }

    // ------------------------------------------------------------------
    // Passive
    // ------------------------------------------------------------------

    public static int regrowth(ServerPlayer player) {
        return REGROWTH.getOrDefault(player.getUUID(), 0);
    }

    public static void addRegrowth(ServerPlayer player, int amount) {
        REGROWTH.put(player.getUUID(), Math.clamp(regrowth(player) + amount, 0, MAX_REGROWTH));
    }

    /** Spends up to {@code amount} Regrowth; returns how much was spent. */
    public static int spendRegrowth(ServerPlayer player, int amount) {
        int spent = Math.min(regrowth(player), Math.max(0, amount));
        REGROWTH.put(player.getUUID(), regrowth(player) - spent);
        return spent;
    }

    /** Every point of real damage becomes two points of Regrowth. */
    public static void wounded(ServerPlayer player, float damage) {
        if (damage > 0 && Float.isFinite(damage)) addRegrowth(player, Math.max(1, Math.round(damage * 2)));
    }

    /** Keeps the owner's HUD current: Regrowth, time left on the ground, and giving up. Only sends changes. */
    public static void syncHud(ServerPlayer player, int powerLevel) {
        int downed = BodyService.downedRemaining(player);
        int recovery = BodyService.recoveryTicks(powerLevel, regrowth(player));
        int giveUp = BodyService.canGiveUp(player) ? BodyService.giveUpProgress(player) : -1;
        int key = java.util.Objects.hash(regrowth(player), downed / 20, recovery / 20, giveUp);
        if (SENT.getOrDefault(player.getUUID(), Integer.MIN_VALUE) == key) return;
        SENT.put(player.getUUID(), key);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new com.jayptucker.heroesevolved.network.RegenerationStatePayload(regrowth(player), downed, recovery, giveUp));
    }

    /** Passive healing burns stored Regrowth: up to 10 a tick, each 5 of it one more point of healing. */
    public static float passiveHeal(ServerPlayer player, int powerLevel) {
        int burned = spendRegrowth(player, 10);
        return passiveHealAmount(powerLevel) + burned / 5.0F;
    }

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

    /**
     * Rapid Mending: the wound closes at once. It spends all stored Regrowth (one more point of healing
     * per 8), and healing past full health stays on as up to four hearts of Absorption.
     */
    public static void rapidMending(ServerPlayer player, int powerLevel) {
        int spent = spendRegrowth(player, MAX_REGROWTH);
        float heal = 4.0F + 2.0F * powerLevel + spent / 8.0F;
        float overflow = Math.max(0, player.getHealth() + heal - player.getMaxHealth());
        player.heal(heal);
        if (overflow > 0) {
            // Absorption hearts need the effect: it raises the cap that a bare absorption value is clamped to.
            float total = Math.min(8.0F, player.getAbsorptionAmount() + overflow);
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 30, (int) Math.ceil(total / 4.0F) - 1, false, false, true));
            player.setAbsorptionAmount(total);
        }
        boolean purged = purgeToxins(player);
        player.clearFire();
        // Missing limbs grow back in a second.
        BodyService.regrowAll(player);
        AbilityEffects.send(player.serverLevel(), Kind.REGEN_MEND, player.position(), player.position().add(0, 1, 0), 1.0D + spent / 50.0D);

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
        return Targeting.livingEntityInSight(player, 6.0D,
                target -> !(target instanceof TemporalEchoEntity));
    }

    /**
     * Claire's blood heals others. It costs her a little of her own
     * health, which she regrows, and it burns the undead.
     */
    public static boolean bloodGift(ServerPlayer player, int powerLevel) {
        // Holding an empty syringe bottles the gift instead: a donated sample for the Formula.
        if (com.jayptucker.heroesevolved.formula.FormulaService.bottleBloodGift(player, powerLevel)) {
            return true;
        }
        LivingEntity target = findBloodGiftTarget(player);
        if (target == null) {
            player.displayClientMessage(Component.literal(
                    "There is no one close enough to share your blood with."
            ), true);
            return false;
        }

        ServerLevel level = player.serverLevel();
        float potency = 6.0F + 2.0F * powerLevel + regrowth(player) / 10.0F;
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

        // Her own cut, never fatal: she will regrow it in seconds, and it feeds her Regrowth.
        player.setHealth(Math.max(1.0F, player.getHealth() - BLOOD_GIFT_SELF_COST));
        addRegrowth(player, 6);
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
                "You can't feel pain anymore. Hit as hard as you like."
        ).withStyle(ChatFormatting.RED), true);
        MasteryService.markPowerUse(player, ModAbilities.REGENERATION_ID);
    }

    public static boolean isIndestructible(ServerPlayer player) {
        Long until = INDESTRUCTIBLE_UNTIL.get(player.getUUID());
        return until != null && player.serverLevel().getGameTime() < until;
    }

    /**
     * Reckless: while Indestructible, Claire punches without caring what breaks. Each blow lands
     * 3 + level harder and breaks her own hand a little (1 health), which she regrows.
     *
     * @return the extra damage for this punch
     */
    public static float recklessBlow(ServerPlayer player, LivingEntity target, int powerLevel) {
        player.setHealth(Math.max(1.0F, player.getHealth() - 1.0F));
        addRegrowth(player, 2);
        AbilityEffects.send(player.serverLevel(), Kind.REGEN_RECKLESS, target.getBoundingBox().getCenter(),
                player.getEyePosition(), 1.0D);
        player.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F);
        return 3.0F + powerLevel;
    }

    /**
     * The bullet comes back out. A projectile that hits her while Indestructible does half damage, and
     * an arrow pushes out of the wound and drops at her feet.
     */
    public static void ejectProjectile(ServerPlayer player, net.minecraft.world.entity.Entity projectile) {
        ServerLevel level = player.serverLevel();
        net.minecraft.world.item.ItemStack dropped = projectile instanceof net.minecraft.world.entity.projectile.SpectralArrow
                ? new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SPECTRAL_ARROW)
                : projectile instanceof net.minecraft.world.entity.projectile.Arrow
                ? new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW) : net.minecraft.world.item.ItemStack.EMPTY;
        Vec3 wound = player.position().add(0, 1.1, 0);
        if (!dropped.isEmpty()) {
            var item = new net.minecraft.world.entity.item.ItemEntity(level, wound.x, wound.y, wound.z, dropped);
            item.setDeltaMovement(player.getLookAngle().scale(-0.1).add(0, 0.15, 0));
            item.setPickUpDelay(10);
            level.addFreshEntity(item);
            projectile.discard();
        }
        AbilityEffects.send(level, Kind.REGEN_EJECT, wound, wound.add(projectile.getDeltaMovement().normalize().scale(-1)), 1.0D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 1.6F);
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

        SECOND_LIFE.values().removeIf(until -> gameTime >= until + 20);
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
     * Blood Bond: for ten seconds, everyone friendly within eight blocks heals with Claire each second,
     * the undead burn, and 35% of any damage her friends take is drawn onto her instead.
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

    /** Is this creature sheltered by someone's Blood Bond? Returns the Claire holding it, or null. */
    public static ServerPlayer bondHolder(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || sharing > 0) return null;
        for (UUID id : HEALING_AURAS.keySet()) {
            ServerPlayer claire = level.getServer().getPlayerList().getPlayer(id);
            if (claire == null || claire == entity || claire.level() != entity.level() || !claire.isAlive()) continue;
            if (entity.distanceToSqr(claire) <= AURA_RADIUS * AURA_RADIUS && bonded(claire, entity)) return claire;
        }
        return null;
    }

    /** A friend: no monster or undead, and a player only if Claire is not fighting them (PvP off, or on her team). */
    private static boolean bonded(ServerPlayer claire, LivingEntity entity) {
        if (entity == claire || entity instanceof net.minecraft.world.entity.monster.Enemy || entity.isInvertedHealAndHarm()) return false;
        return !(entity instanceof ServerPlayer other) || claire.isAlliedTo(other)
                || !com.jayptucker.heroesevolved.expansion.ExpansionSkills.hostile(claire, other);
    }

    /** Takes {@code share} of a bonded friend's wound onto Claire. */
    public static void takeShare(ServerPlayer claire, LivingEntity friend, float share) {
        sharing++;
        try {
            claire.hurt(claire.damageSources().magic(), share);
        } finally {
            sharing--;
        }
        AbilityEffects.send(claire.serverLevel(), Kind.REGEN_BOND, friend.getBoundingBox().getCenter(),
                claire.position().add(0, 1.1, 0), 1.0D);
    }

    // ------------------------------------------------------------------
    // Second Life
    // ------------------------------------------------------------------

    /** The friend Second Life would reach: in sight within twelve blocks, friendly, alive. */
    public static LivingEntity findSecondLifeTarget(ServerPlayer player) {
        return Targeting.livingEntityInSight(player, 6.0D, target -> !(target instanceof TemporalEchoEntity) && bonded(player, target));
    }

    /**
     * Second Life: Claire's blood brought Nathan back. Inject it into a friend: they heal to full, and for
     * a minute the next killing blow leaves them standing. Costs 50 Regrowth and 6 of her own health.
     */
    public static boolean secondLife(ServerPlayer player) {
        LivingEntity target = findSecondLifeTarget(player);
        if (target == null) return false;
        if (regrowth(player) < SECOND_LIFE_COST) {
            player.displayClientMessage(Component.literal("You need " + SECOND_LIFE_COST + " Regrowth to give a second life (you have "
                    + regrowth(player) + "). Take a few hits first.").withStyle(ChatFormatting.RED), true);
            return false;
        }
        spendRegrowth(player, SECOND_LIFE_COST);
        target.heal(target.getMaxHealth());
        purgeToxins(target);
        SECOND_LIFE.put(target.getUUID(), player.serverLevel().getGameTime() + SECOND_LIFE_TICKS);
        player.setHealth(Math.max(1.0F, player.getHealth() - 6.0F));
        ServerLevel level = player.serverLevel();
        AbilityEffects.send(level, Kind.BLOOD_THREAD, player.position().add(0, 1.1, 0), target.getBoundingBox().getCenter(), 1.4D);
        AbilityEffects.send(level, Kind.REGEN_SECOND_LIFE, target.position(), target.position().add(0, 1, 0), target.getBbHeight());
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.4F, 1.2F);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.6F, 1.8F);
        if (target instanceof ServerPlayer friend) friend.displayClientMessage(Component.literal(
                player.getName().getString() + "'s blood is in your veins. Death will not take you this minute.")
                .withStyle(ChatFormatting.DARK_RED), false);
        player.displayClientMessage(Component.literal("Your blood will keep them alive.").withStyle(ChatFormatting.RED), true);
        MasteryService.markPowerUse(player, ModAbilities.REGENERATION_ID);
        return true;
    }

    public static boolean hasSecondLife(LivingEntity entity) {
        Long until = SECOND_LIFE.get(entity.getUUID());
        return until != null && entity.level().getGameTime() < until;
    }

    /** A creature with Claire's blood in it would die: it gasps back instead, once. */
    public static boolean trySecondLife(LivingEntity entity, DamageSource source) {
        if (!hasSecondLife(entity) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypes.GENERIC_KILL)) return false;
        SECOND_LIFE.remove(entity.getUUID());
        entity.setHealth(4.0F);
        entity.clearFire();
        entity.invulnerableTime = 40;
        entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 5, 2));
        if (entity.level() instanceof ServerLevel level) {
            AbilityEffects.send(level, Kind.REGEN_REVIVE, entity.position(), entity.position().add(0, 1, 0), entity.getBbHeight());
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5F, 1.0F);
        }
        if (entity instanceof ServerPlayer friend) friend.sendSystemMessage(Component.literal("You gasp back to life. Her blood held.")
                .withStyle(ChatFormatting.DARK_RED));
        return true;
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
        REGROWTH.remove(player.getUUID());
        SENT.remove(player.getUUID());
    }

    public static void clear() {
        INDESTRUCTIBLE_UNTIL.clear(); HEALING_AURAS.clear(); REGROWTH.clear(); SENT.clear(); SECOND_LIFE.clear();
    }

    private static boolean purgeToxins(LivingEntity entity) {
        boolean purged = false;
        for (Holder<MobEffect> toxin : TOXINS) {
            purged |= entity.removeEffect(toxin);
        }
        return purged;
    }
}
