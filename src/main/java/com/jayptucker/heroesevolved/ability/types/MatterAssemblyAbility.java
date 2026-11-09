package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.assembly.MatterAssemblyService;
import com.jayptucker.heroesevolved.intro.PowerIntroService;
import java.util.Map;

/** Real paid architecture, rewinding-safe material conservation, and swept physical Shards. */
public final class MatterAssemblyAbility implements Ability {
    private static AbilityAction action(String name, int tier, int energy, int cooldown) {
        return SimpleAction.builder("matter_assembly_" + name, tier).cost(() -> energy, () -> cooldown)
                .canUse(c -> MatterAssemblyService.canUse(c, name))
                .activate(c -> MatterAssemblyService.activate(c, name))
                .deactivate(c -> MatterAssemblyService.dismantle(c.player())).build();
    }
    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, action("barricade", 1, 12, 60),
            AbilitySlot.SECONDARY, action("shard_lance", 1, 14, 30),
            AbilitySlot.TERTIARY, action("causeway", 2, 22, 160),
            AbilitySlot.QUATERNARY, action("cage", 3, 30, 220),
            AbilitySlot.QUINARY, action("cathedral", 5, 70, 1000));
    @Override public AbilityDefinition definition() { return new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT); }
    @Override public Map<AbilitySlot, AbilityAction> actions() { return ACTIONS; }
    @Override public boolean canUse(AbilityUseContext context) { return false; }
    @Override public AbilityActivationResult activate(AbilityUseContext context) { return AbilityActivationResult.REJECTED; }
    @Override public void onAwaken(AbilityUseContext context) { PowerIntroService.acquired(context.player(), context.abilityId()); }
    @Override public void onRevoked(AbilityUseContext context) { MatterAssemblyService.forget(context.player()); }
}
