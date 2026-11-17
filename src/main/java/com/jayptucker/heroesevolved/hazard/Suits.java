package com.jayptucker.heroesevolved.hazard;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Who a meltdown can hurt, and how much the protective suits keep off. There are two suits, both made at the Suit
 * Fabricator (see docs/protective-suits.md):
 * <ul>
 *   <li>the <b>radiation suit</b>: lead-lined, sealed, with a leaded-glass visor and a charcoal respirator;</li>
 *   <li>the <b>velocity suit</b>: aramid weave over ceramic plates, friction-proof for a speedster's top speeds, with a
 *   lead lining so it keeps radiation off too.</li>
 * </ul>
 * Each piece of either keeps a quarter of the radiation off; four pieces (mixed or not) seal the wearer completely. Only
 * the velocity suit keeps friction off, a quarter a piece. Radioactive players are immune to radiation (it is their own
 * power), and Regeneration users take it but can't die of it near a meltdown (see {@code MeltdownField}).
 */
public final class Suits {
    public enum Kind { RADIATION, VELOCITY }

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private Suits() {}

    /** How many pieces of this suit are worn. */
    public static int pieces(LivingEntity entity, Kind kind) {
        int n = 0;
        for (EquipmentSlot slot : ARMOR)
            if (entity.getItemBySlot(slot).getItem() instanceof SuitArmorItem suit && suit.kind() == kind) n++;
        return n;
    }

    /** The share of radiation kept off, 0 to 1: a quarter for each piece of either suit. */
    public static float radiation(LivingEntity entity) {
        return Math.min(1, (pieces(entity, Kind.RADIATION) + pieces(entity, Kind.VELOCITY)) / 4F);
    }

    /** Four pieces: no radiation gets in at all. */
    public static boolean sealed(LivingEntity entity) { return radiation(entity) >= 1; }

    /** The share of friction kept off, 0 to 1: a quarter for each piece of the velocity suit. */
    public static float friction(LivingEntity entity) { return pieces(entity, Kind.VELOCITY) / 4F; }

    /** The whole velocity suit: a speedster can run as fast as they like, and run rings round a meltdown. */
    public static boolean frictionProof(LivingEntity entity) { return pieces(entity, Kind.VELOCITY) >= 4; }

    /**
     * The suits wear as they work: {@code absorbed} is the damage they kept off, spread over the pieces worn. Fractions
     * round up by chance, so many small doses still wear a suit out.
     */
    public static void wear(LivingEntity entity, float absorbed) {
        if (absorbed <= 0 || entity.level().isClientSide || entity instanceof Player p && p.getAbilities().instabuild) return;
        int worn = pieces(entity, Kind.RADIATION) + pieces(entity, Kind.VELOCITY);
        if (worn == 0) return;
        float each = absorbed * 0.5F / worn;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!(stack.getItem() instanceof SuitArmorItem)) continue;
            int whole = (int) each + (entity.getRandom().nextFloat() < each - (int) each ? 1 : 0);
            if (whole > 0) stack.hurtAndBreak(whole, entity, slot);
        }
    }

    /** Wears the velocity suit's pieces only (friction). */
    public static void scuff(LivingEntity entity, float absorbed) {
        if (absorbed <= 0 || entity.level().isClientSide || entity instanceof Player p && p.getAbilities().instabuild) return;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.getItem() instanceof SuitArmorItem suit && suit.kind() == Kind.VELOCITY
                    && entity.getRandom().nextFloat() < absorbed * 0.25F) stack.hurtAndBreak(1, entity, slot);
        }
    }

    // ------------------------------------------------------------------
    // Powers that change the rules
    // ------------------------------------------------------------------

    private static boolean holds(LivingEntity entity, ResourceLocation power) {
        return entity instanceof ServerPlayer p && PlayerAbilityService.getData(p).assignedPower()
                .filter(e -> e.getKey().equals(power) && e.getValue().isUnlocked()).isPresent();
    }

    /** A radioactive player: radiation is their own power and can't hurt them. */
    public static boolean radioactive(LivingEntity entity) { return holds(entity, ModAbilities.RADIATION_ID); }

    /** A Regeneration user: the radiation burns them, but they grow back faster than it can kill them. */
    public static boolean regenerates(LivingEntity entity) { return holds(entity, ModAbilities.REGENERATION_ID); }

    /** A speedster: their body heals and regrows a little faster than anyone else's (SpeedFriction, BodyBurn). */
    public static boolean speedster(LivingEntity entity) { return holds(entity, ModAbilities.SUPERSPEED_ID); }

    /** Nothing a meltdown's radiation does reaches them: radioactive, sealed in a suit, or not really there. */
    public static boolean meltdownProof(LivingEntity entity) {
        return radioactive(entity) || sealed(entity) || entity.isSpectator()
                || entity instanceof Player p && p.getAbilities().invulnerable;
    }
}
