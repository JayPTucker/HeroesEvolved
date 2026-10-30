package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.sonic.SonicService;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;

/**
 * Sonic Resonance, after Emma Coolidge (who sees sound as colour and whose music draws people to it)
 * and Black Canary (whose voice is a weapon).
 * <ul>
 *   <li>Level 1, Canary Cry: tap for a focused cry; hold for the sustained scream.</li>
 *   <li>Level 2, Resonant Note: a humming note that lures enemies to it; press again to break it on them.</li>
 *   <li>Level 3, Wall of Sound: shots turn back off it and enemies are pushed away.</li>
 *   <li>Level 4, Sonic Vault: cry at the ground to leap; land on a shockwave.</li>
 *   <li>Level 5, Crescendo: hold to build, release one wave that spends all your Resonance.</li>
 *   <li>Always: Sound Sight shows every sound around you as colour. Sonic hits and explosions build Resonance.</li>
 * </ul>
 */
public final class SonicResonanceAbility implements Ability {
    private static final AbilityDefinition DEFINITION = new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT);

    /** An action whose key can be held: down starts it, up finishes it. */
    private abstract static class Held implements AbilityAction {
        private final AbilityActionDefinition definition;
        private final int energy, cooldown;
        Held(String path, int tier, int energy, int cooldown) {
            definition = new AbilityActionDefinition(ResourceLocation.fromNamespaceAndPath("heroesevolved", path),
                    "action.heroesevolved." + path, tier, 0, 0);
            this.energy = energy; this.cooldown = cooldown;
        }
        @Override public AbilityActionDefinition definition() { return definition; }
        @Override public int energyCost(int level) { return energy; }
        @Override public int cooldownTicks(int level) { return cooldown; }
    }

    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, new Held("sonic_cry", 1, 18, 25) {
                @Override public boolean canUse(AbilityUseContext c) { return !SonicService.holdingCry(c.player()); }
                @Override public AbilityActivationResult activate(AbilityUseContext c) {
                    SonicService.press(c.player(), c.abilityLevel()); return AbilityActivationResult.SUCCESS;
                }
                @Override public void release(AbilityUseContext c) { SonicService.release(c.player()); }
                @Override public boolean deactivate(AbilityUseContext c) {
                    boolean active = SonicService.screaming(c.player()); SonicService.stop(c.player()); return active;
                }
            },
            AbilitySlot.SECONDARY, SimpleAction.builder("sonic_note", 2).cost(() -> 20, () -> 120)
                    .deactivateOnPress(c -> SonicService.hasNote(c.player()))
                    .deactivate(c -> SonicService.detonate(c.player()))
                    .activate(c -> SonicService.placeNote(c.player(), c.abilityLevel())).build(),
            AbilitySlot.TERTIARY, SimpleAction.builder("sonic_wall", 3).cost(() -> 28, () -> 300)
                    .deactivate(c -> SonicService.dropWall(c.player()))
                    .activate(c -> SonicService.wall(c.player(), c.abilityLevel())).build(),
            AbilitySlot.QUATERNARY, SimpleAction.builder("sonic_vault", 4).cost(() -> 25, () -> 80)
                    .activate(c -> { SonicService.vault(c.player()); return true; }).build(),
            AbilitySlot.QUINARY, new Held("sonic_crescendo", 5, 60, 20 * 60) {
                @Override public boolean canUse(AbilityUseContext c) { return !SonicService.building(c.player()); }
                @Override public AbilityActivationResult activate(AbilityUseContext c) {
                    SonicService.startCrescendo(c.player(), c.abilityLevel()); return AbilityActivationResult.SUCCESS;
                }
                @Override public void release(AbilityUseContext c) { SonicService.releaseCrescendo(c.player()); }
                @Override public boolean deactivate(AbilityUseContext c) { return SonicService.cancelCrescendo(c.player()); }
            });
    @Override public AbilityDefinition definition() { return DEFINITION; }
    @Override public Map<AbilitySlot, AbilityAction> actions() { return ACTIONS; }
    @Override public boolean canUse(AbilityUseContext c) { return false; }
    @Override public AbilityActivationResult activate(AbilityUseContext c) { return AbilityActivationResult.REJECTED; }
    @Override public void onRevoked(AbilityUseContext c) { SonicService.forget(c.player()); }
    @Override public void onAwaken(AbilityUseContext c) {
        com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(c.player(), c.abilityId());
    }
}
