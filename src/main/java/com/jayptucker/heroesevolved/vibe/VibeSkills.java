package com.jayptucker.heroesevolved.vibe;

import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.SimpleAction;
import com.jayptucker.heroesevolved.skills.SkillCatalog;

public final class VibeSkills {
    private VibeSkills() {}

    public static AbilityAction action(SkillCatalog.Node node) {
        return SimpleAction.builder(node.id(), node.level()).cost(node::energy, node::cooldown)
                .canUse(c -> VibeService.ready(c.player()))
                .activate(c -> switch (node.id()) {
                    case "vibe_0_1" -> VibeService.blast(c, VibeService.Blast.LANCE);
                    case "vibe_reverb" -> VibeService.blast(c, VibeService.Blast.REVERB);
                    case "vibe_1_1" -> VibeBreaches.attune(c.player());
                    case "vibe_flashstep" -> VibeBreaches.flash(c.player());
                    case "vibe_2_1" -> VibeSight.read(c.player(), true);
                    case "vibe_prism_lock" -> VibeService.lock(c, true);
                    default -> false;
                }).build();
    }
}
