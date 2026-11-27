package com.jayptucker.heroesevolved.styles;

import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.SimpleAction;
import com.jayptucker.heroesevolved.combat.Ultimates;
import com.jayptucker.heroesevolved.skills.SkillCatalog;

/** The castable action behind a class node: its Ultimate ({@link Ultimates}). */
public final class StyleSkills {
    private StyleSkills() {}

    public static AbilityAction action(SkillCatalog.Node node) {
        return SimpleAction.builder(node.id(), node.level()).cost(node::energy, node::cooldown)
                .canUse(c -> Ultimates.canUse(c, node.mechanic()))
                .activate(c -> Ultimates.cast(c, node.mechanic()))
                .build();
    }
}
