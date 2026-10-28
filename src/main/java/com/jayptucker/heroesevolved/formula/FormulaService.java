package com.jayptucker.heroesevolved.formula;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.events.AwakeningService;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.telekinesis.TelekinesisService;
import com.jayptucker.heroesevolved.time.SpaceTimeService;
import com.jayptucker.heroesevolved.time.TimeStopService;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;

import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;

/**
 * The Formula: Regeneration blood is the catalyst that awakens a dormant power.
 *
 * <p>Blood can be donated (the owner crouches, or bottles their own Blood Gift)
 * or forced from a helpless owner. Forced draws respect PvP and teams, can be
 * disabled, and protect each owner for a configurable cooldown. Injection only
 * ever awakens an unclaimed power, through the normal ownership path.</p>
 */
public final class FormulaService {
    public static final float DONATION_COST = 8, FORCED_DAMAGE = 6;
    private static final int DRAINED_TICKS = 20 * 30;

    public enum Draw { DONATED, FORCED, NOT_REGENERATION, REFUSED, PROTECTED, NOT_HELPLESS, DISABLED }
    public enum Injection { AWAKENED, REJECTED, ALREADY_POWERED, NOTHING_LEFT, ECLIPSE, NOT_CONSENTING }

    private FormulaService() {}

    private static long now(ServerPlayer player) { return player.getServer().overworld().getGameTime(); }

    public static boolean isRegenerationOwner(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower()
                .filter(entry -> entry.getKey().equals(ModAbilities.REGENERATION_ID) && entry.getValue().isUnlocked())
                .isPresent();
    }

    /** Frozen, held, stopped, asleep or badly hurt: someone who cannot resist a needle. */
    public static boolean isHelpless(ServerPlayer target) {
        return target.getHealth() < target.getMaxHealth() * 0.3F || CryoService.isFrozen(target)
                || TelekinesisService.isHeld(target) || SpaceTimeService.isSuspended(target)
                || TimeStopService.isStopped(target) || target.isSleeping();
    }

    // ------------------------------------------------------------------
    // Drawing blood
    // ------------------------------------------------------------------

    /**
     * Uses an empty syringe on {@code donor}, who may be the holder. Consent is crouching,
     * or drawing your own blood. Without consent the donor must be helpless.
     */
    public static Draw draw(ServerPlayer holder, ServerPlayer donor, ItemStack syringe) {
        if (!isRegenerationOwner(donor)) return Draw.NOT_REGENERATION;
        boolean consent = holder == donor || donor.isShiftKeyDown();
        if (consent) {
            if (donor.getHealth() <= DONATION_COST && holder != donor) return Draw.REFUSED;
            donor.hurt(donor.damageSources().generic(), Math.min(DONATION_COST, donor.getHealth() - 1));
            give(holder, syringe, sample(donor, true));
            feedback(holder, donor, Component.translatable("formula.heroesevolved.donated", donor.getName()));
            if (holder != donor) donor.sendSystemMessage(Component.translatable("formula.heroesevolved.donor_thanks", holder.getName())
                    .withStyle(ChatFormatting.AQUA));
            award(donor, "save_the_cheerleader");
            return Draw.DONATED;
        }
        if (!COMMON.formulaAllowForcedDraws.get()) return Draw.DISABLED;
        // Includes the server's PvP setting and scoreboard teams.
        if (!holder.canHarmPlayer(donor)) return Draw.REFUSED;
        long last = donor.getData(ModDataAttachments.LAST_FORCED_DRAW);
        if (now(donor) - last < COMMON.formulaForcedDrawCooldownSeconds.get() * 20L) return Draw.PROTECTED;
        if (!isHelpless(donor)) return Draw.NOT_HELPLESS;
        donor.setData(ModDataAttachments.LAST_FORCED_DRAW, now(donor));
        donor.hurt(holder.damageSources().playerAttack(holder), FORCED_DAMAGE);
        donor.addEffect(new MobEffectInstance(ModItems.DRAINED, DRAINED_TICKS));
        donor.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DRAINED_TICKS, 1));
        give(holder, syringe, sample(donor, false));
        holder.displayClientMessage(Component.translatable("formula.heroesevolved.forced", donor.getName())
                .withStyle(ChatFormatting.DARK_RED), true);
        donor.sendSystemMessage(Component.translatable("formula.heroesevolved.forced_victim", holder.getName())
                .withStyle(ChatFormatting.DARK_RED));
        splash(donor);
        return Draw.FORCED;
    }

    /** Explains a refused draw to the person holding the needle. */
    public static void explain(ServerPlayer holder, ServerPlayer donor, Draw result) {
        String key = switch (result) {
            case NOT_REGENERATION -> "formula.heroesevolved.draw.not_regeneration";
            case REFUSED -> "formula.heroesevolved.draw.refused";
            case PROTECTED -> "formula.heroesevolved.draw.protected";
            case NOT_HELPLESS -> "formula.heroesevolved.draw.not_helpless";
            case DISABLED -> "formula.heroesevolved.draw.disabled";
            default -> null;
        };
        if (key != null) holder.displayClientMessage(Component.translatable(key, donor.getName()), true);
    }

    /** Blood Gift with an empty syringe in hand bottles a donated sample instead of healing someone. */
    public static boolean bottleBloodGift(ServerPlayer player, int powerLevel) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (!held.is(ModItems.EMPTY_SYRINGE.get())) continue;
            give(player, held, sample(player, true));
            feedback(player, player, Component.translatable("formula.heroesevolved.bottled"));
            award(player, "save_the_cheerleader");
            return true;
        }
        return false;
    }

    private static ItemStack sample(ServerPlayer donor, boolean donated) {
        ItemStack stack = new ItemStack(ModItems.BLOOD_SAMPLE.get());
        stack.set(ModItems.BLOOD_PURITY.get(), new BloodPurity(donated,
                PlayerProgressionService.getLevel(donor), donor.getGameProfile().getName()));
        return stack;
    }

    /** One donated sample for each rescuer, the first time they save the Hourglass Annex. */
    public static void giveRescueSample(ServerPlayer player) {
        ItemStack stack = new ItemStack(ModItems.BLOOD_SAMPLE.get());
        stack.set(ModItems.BLOOD_PURITY.get(), new BloodPurity(true, 1, "Annex survivor"));
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        player.sendSystemMessage(Component.translatable("formula.heroesevolved.rescue_sample").withStyle(ChatFormatting.AQUA));
    }

    private static void give(ServerPlayer holder, ItemStack syringe, ItemStack sample) {
        if (!holder.getAbilities().instabuild) syringe.shrink(1);
        if (!holder.getInventory().add(sample)) holder.drop(sample, false);
    }

    private static void feedback(ServerPlayer holder, ServerPlayer donor, Component message) {
        holder.displayClientMessage(message.copy().withStyle(ChatFormatting.AQUA), true);
        splash(donor);
    }

    private static void splash(ServerPlayer donor) {
        donor.serverLevel().sendParticles(new DustParticleOptions(new Vector3f(0.7F, 0.05F, 0.1F), 1.2F),
                donor.getX(), donor.getY() + 1, donor.getZ(), 12, 0.25, 0.3, 0.25, 0);
        donor.serverLevel().playSound(null, donor.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.8F, 0.7F);
    }

    // ------------------------------------------------------------------
    // Injection
    // ------------------------------------------------------------------

    public static double successChance(BloodPurity purity) {
        double base = purity.donated() ? COMMON.formulaDonatedSuccess.get() : COMMON.formulaForcedSuccess.get();
        return Math.min(1, base + 0.02 * Math.max(0, purity.donorLevel() - 1));
    }

    /** Every registered power nobody owns yet, in a stable order. */
    public static List<ResourceLocation> unclaimed(ServerPlayer recipient) {
        return AbilityRegistry.ABILITIES.keySet().stream()
                .filter(id -> PlayerAbilityService.availableTo(recipient, id))
                .sorted(Comparator.comparing(ResourceLocation::toString)).toList();
    }

    /** Injects a vial into {@code recipient} using {@code roll} in [0, 1). The vial is consumed except when nothing is left. */
    public static Injection inject(ServerPlayer injector, ServerPlayer recipient, ItemStack vial, double roll) {
        if (injector != recipient && !recipient.isShiftKeyDown()) return Injection.NOT_CONSENTING;
        if (EclipseService.arePowersSuppressed(recipient)) {
            injector.displayClientMessage(Component.translatable("formula.heroesevolved.inject.eclipse"), true);
            return Injection.ECLIPSE;
        }
        BloodPurity purity = vial.getOrDefault(ModItems.BLOOD_PURITY.get(), new BloodPurity(false, 1, "Unknown"));
        var assignment = PlayerAbilityService.getData(recipient).assignedPower();
        if (assignment.isPresent() && assignment.get().getValue().isUnlocked()) {
            consume(injector, vial);
            recipient.hurt(recipient.damageSources().magic(), Math.min(4, Math.max(0, recipient.getHealth() - 1)));
            sicken(recipient, 300);
            message(injector, recipient, "formula.heroesevolved.inject.already_powered");
            return Injection.ALREADY_POWERED;
        }
        // A dormant assignment is already this person's power: the Formula can only wake that one.
        List<ResourceLocation> available = assignment.map(entry -> List.of(entry.getKey())).orElseGet(() -> unclaimed(recipient));
        if (available.isEmpty()) {
            message(injector, recipient, "formula.heroesevolved.inject.nothing_left");
            return Injection.NOTHING_LEFT;
        }
        consume(injector, vial);
        recipient.serverLevel().playSound(null, recipient.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.4F);
        // A sealed Cryochamber is a controlled environment: +10%.
        double chance = Math.min(1, successChance(purity)
                + (com.jayptucker.heroesevolved.story.bunker.CryoChamberService.isInside(recipient) ? 0.10 : 0));
        if (roll >= chance) {
            sicken(recipient, 600);
            message(injector, recipient, "formula.heroesevolved.inject.rejected");
            return Injection.REJECTED;
        }
        ResourceLocation power = available.get(Math.min(available.size() - 1,
                (int) (recipient.getRandom().nextDouble() * available.size())));
        boolean assigned = assignment.isPresent() || PlayerAbilityService.assignDormantAbility(recipient, power);
        if (!assigned || !AwakeningService.awakenAssigned(recipient)) {
            HeroesEvolved.LOGGER.warn("Formula could not awaken {} for {}", power, recipient.getGameProfile().getName());
            sicken(recipient, 300);
            return Injection.REJECTED;
        }
        recipient.setData(ModDataAttachments.FORMULA_BORN, true);
        recipient.sendSystemMessage(Component.translatable("formula.heroesevolved.inject.awakened").withStyle(ChatFormatting.GOLD));
        award(recipient, "the_formula");
        return Injection.AWAKENED;
    }

    private static void consume(ServerPlayer injector, ItemStack vial) {
        if (injector.getAbilities().instabuild) return;
        vial.shrink(1);
        // The needle is spent along with the dose.
        var inventory = injector.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.EMPTY_SYRINGE.get())) { stack.shrink(1); return; }
        }
    }

    /** True when the injector carries a needle to deliver the dose (always in creative). */
    public static boolean hasNeedle(ServerPlayer injector) {
        return injector.getAbilities().instabuild || injector.getInventory().contains(new ItemStack(ModItems.EMPTY_SYRINGE.get()));
    }

    /** Never lethal: vanilla poison stops at one heart. */
    private static void sicken(ServerPlayer player, int ticks) {
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ticks));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks));
        player.addEffect(new MobEffectInstance(MobEffects.POISON, Math.min(ticks, 200)));
    }

    private static void message(ServerPlayer injector, ServerPlayer recipient, String key) {
        recipient.sendSystemMessage(Component.translatable(key));
        if (injector != recipient) injector.displayClientMessage(Component.translatable(key), true);
    }

    private static void award(ServerPlayer player, String name) {
        AdvancementHolder advancement = player.getServer().getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, name));
        if (advancement != null) player.getAdvancements().award(advancement, "granted");
    }
}
