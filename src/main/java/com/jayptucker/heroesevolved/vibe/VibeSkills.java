package com.jayptucker.heroesevolved.vibe;

import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.SimpleAction;
import com.jayptucker.heroesevolved.skills.SkillCatalog;

public final class VibeSkills {
    private VibeSkills() {}

    public static AbilityAction action(SkillCatalog.Node node) {
        return SimpleAction.builder(node.id(), node.level()).cost(node::energy, node::cooldown)
                .canUse(c -> VibeService.ready(c.player()))
                // Glimpse: the press opens the wheel (or closes an open window) for free; the wheel's pick pays.
                .followUp(c -> switch (node.id()) {
                    case "vibe_1_1" -> VibeBreaches.glimpseFollowUp(c.player());
                    // Rift Beast: a second press sends it at what you look at (Shift falls through to send it home).
                    case "vibe_rift_beast" -> VibeBeast.command(c);
                    default -> false;
                })
                .deactivate(c -> node.id().equals("vibe_rift_beast") && VibeBeast.dismiss(c.player()))
                .activate(c -> switch (node.id()) {
                    case "vibe_0_1" -> VibeService.blast(c, VibeService.Blast.LANCE);
                    case "vibe_reverb" -> VibeService.blast(c, VibeService.Blast.REVERB);
                    case "vibe_1_1" -> VibeBreaches.glimpse(c.player());
                    case "vibe_rift_beast" -> VibeBeast.summon(c);
                    case "vibe_flashstep" -> VibeBreaches.flash(c.player());
                    case "vibe_2_1" -> VibeSight.read(c.player(), true);
                    case "vibe_prism_lock" -> VibeService.lock(c, true);
                    default -> false;
                }).build();
    }
}
