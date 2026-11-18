package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.amplify.AmpService;
import com.jayptucker.heroesevolved.intro.PowerIntroService;

import java.util.Map;

import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;

/**
 * Power Amplification, after Ando Masahashi's red lightning in <i>Heroes</i>: it makes other people's powers stronger,
 * never your own, and it can be thrown as lightning. See {@code docs/power-amplification.md}.
 *
 * <ul>
 *   <li>Level 1, Supercharge: zap an ally. A tap Surges them (15 s: their powers hit harder, cost less, recover faster,
 *   and each power gets its own bigger version); held two seconds it Overcharges them instead (6 s of something their
 *   power can't normally do, then a backlash).</li>
 *   <li>Level 1, Red Lightning: a ball of red energy grows between your cupped hands while you hold; let go to throw it
 *   as a bolt, or, held past a second, a beam.</li>
 *   <li>Level 3, Shock Therapy: a careful jolt that clears mind control, stuns and confusion from an ally or yourself.</li>
 *   <li>Level 6, Jumpstart: zap a door, lock, lever, furnace, battery or the horse you ride, and it springs to life.</li>
 *   <li>Level 15, A Thousand Times: overcharge every ally near you at once, with no backlash.</li>
 * </ul>
 */
public final class AmplificationAbility implements Ability {
    private static final AbilityDefinition DEFINITION = new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT);

    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, SimpleAction.builder("amp_supercharge", 1)
                    .cost(COMMON.ampSuperchargeEnergyCost::get, COMMON.ampSuperchargeCooldownTicks::get)
                    .canUse(AmpService::canSupercharge)
                    .activate(AmpService::supercharge)
                    .release(AmpService::releaseSupercharge).build(),
            AbilitySlot.SECONDARY, SimpleAction.builder("amp_red_lightning", 1)
                    .cost(COMMON.ampLightningEnergyCost::get, COMMON.ampLightningCooldownTicks::get)
                    .canUse(c -> AmpService.canCast(c.player()))
                    .activate(AmpService::gather)
                    .release(AmpService::releaseLightning).build(),
            AbilitySlot.TERTIARY, SimpleAction.builder("amp_shock_therapy", 2)
                    .cost(COMMON.ampShockEnergyCost::get, COMMON.ampShockCooldownTicks::get)
                    .canUse(c -> AmpService.canCast(c.player()))
                    .activate(AmpService::shockTherapy).build(),
            AbilitySlot.QUATERNARY, SimpleAction.builder("amp_jumpstart", 3)
                    .cost(COMMON.ampJumpstartEnergyCost::get, COMMON.ampJumpstartCooldownTicks::get)
                    .canUse(AmpService::canJumpstart)
                    .activate(AmpService::jumpstart).build(),
            AbilitySlot.QUINARY, SimpleAction.builder("amp_thousand_times", 5)
                    .cost(COMMON.ampThousandEnergyCost::get, COMMON.ampThousandCooldownTicks::get)
                    .canUse(c -> AmpService.canCast(c.player()))
                    .activate(AmpService::thousandTimes).build());

    @Override public AbilityDefinition definition() { return DEFINITION; }
    @Override public Map<AbilitySlot, AbilityAction> actions() { return ACTIONS; }
    @Override public boolean canUse(AbilityUseContext context) { return false; }
    @Override public AbilityActivationResult activate(AbilityUseContext context) { return AbilityActivationResult.REJECTED; }
    @Override public void onAwaken(AbilityUseContext c) { PowerIntroService.acquired(c.player(), c.abilityId()); }
    @Override public void onRevoked(AbilityUseContext c) { AmpService.forget(c.player()); }
}
