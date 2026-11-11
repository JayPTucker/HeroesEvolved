package com.jayptucker.heroesevolved.styles;

import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.SimpleAction;
import com.jayptucker.heroesevolved.skills.SkillCatalog;

/** The castable action behind a class ability's node. */
public final class StyleSkills {
    private StyleSkills() {}

    public static AbilityAction action(SkillCatalog.Node node) {
        return SimpleAction.builder(node.id(), node.level()).cost(node::energy, node::cooldown)
                .canUse(c -> StyleCasts.canUse(c, node.mechanic()))
                .activate(c -> StyleCasts.cast(c, node.mechanic()))
                .build();
    }
}
