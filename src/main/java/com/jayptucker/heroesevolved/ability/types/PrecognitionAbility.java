package com.jayptucker.heroesevolved.ability.types;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.precog.PrecognitionService;
import java.util.Map;

/**
 * Precognition, inspired by Isaac Mendez: see what is coming, paint it, and change it.
 * <ul>
 *   <li>Level 1, Premonition: eight seconds of sharper sight; enemies show ghosts of where they are about to be.</li>
 *   <li>Level 2, Paint the Future: paint an enemy's ending and it comes true; paint nothing and see what is coming for you.</li>
 *   <li>Level 3, Saw It Coming: the next blow in the next moment misses, and its attacker is Exposed.</li>
 *   <li>Level 4, Fate Thread: cross out one lethal outcome for a friend, or yourself with Shift.</li>
 *   <li>Level 5, The Future Is Written: spend your Clarity to live two seconds ahead of everyone.</li>
 *   <li>Always: Danger Sense shows foreseen harm; letting it miss earns Clarity.</li>
 * </ul>
 */
public final class PrecognitionAbility implements Ability {
    private static final Map<AbilitySlot, AbilityAction> ACTIONS = Map.of(
            AbilitySlot.PRIMARY, SimpleAction.builder("precog_premonition", 1).cost(() -> 12, () -> 160)
                    .activate(c -> PrecognitionService.startPremonition(c.player())).build(),
            AbilitySlot.SECONDARY, SimpleAction.builder("precog_paint", 2).cost(() -> 22, () -> 200)
                    .canUse(c -> !PrecognitionService.painting(c.player()))
                    .activate(c -> PrecognitionService.paint(c.player())).build(),
            AbilitySlot.TERTIARY, SimpleAction.builder("precog_saw_it_coming", 3).cost(() -> 14, () -> 120)
                    .activate(c -> PrecognitionService.sawItComing(c.player())).build(),
            AbilitySlot.QUATERNARY, SimpleAction.builder("precog_fate_thread", 4).cost(() -> 32, () -> 600)
                    .activate(c -> PrecognitionService.fateThread(c.player())).build(),
            AbilitySlot.QUINARY, SimpleAction.builder("precog_future_written", 5).cost(() -> 50, () -> 20 * 90)
                    .canUse(c -> !PrecognitionService.futureWritten(c.player()))
                    .activate(c -> PrecognitionService.writeTheFuture(c.player())).build());
    @Override public AbilityDefinition definition() { return new AbilityDefinition(0, 0, 5, AbilityActivationType.INSTANT); }
    @Override public Map<AbilitySlot, AbilityAction> actions() { return ACTIONS; }
    @Override public boolean canUse(AbilityUseContext c) { return false; }
    @Override public AbilityActivationResult activate(AbilityUseContext c) { return AbilityActivationResult.REJECTED; }
    @Override public void onAwaken(AbilityUseContext c) { PrecognitionService.awaken(c.player()); }
    @Override public void onRevoked(AbilityUseContext c) { PrecognitionService.forget(c.player()); }
}
