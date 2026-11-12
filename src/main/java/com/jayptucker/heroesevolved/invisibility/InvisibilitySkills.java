package com.jayptucker.heroesevolved.invisibility;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import net.minecraft.server.level.ServerPlayer;

/**
 * Invisibility's skill tree, built around the kit rather than stat bonuses. Three branches:
 * <ul>
 *   <li><b>Veil</b> (unseen people and things): Quiet Light, Vanishing Touch, Unseen Constructs, Vanishing Squad →
 *   Long Vanish → Unseen Force.</li>
 *   <li><b>Aegis</b> (shields and things to stand on): Wide Lens, Force Barrier, Safety Net, Force Disc →
 *   Steady Disc → Rebound.</li>
 *   <li><b>Pressure</b> (blasts and containment): Hard Push, Pinpoint, Tight Seal, Kinetic Shell → Pressure Chain →
 *   Implosion.</li>
 * </ul>
 * Node ids are kept from the older trees, so purchases and prerequisites carry over.
 */
public final class InvisibilitySkills {
    // Veil
    public static final String QUIET_LIGHT = "invisibility_0_0", UNSEEN_CONSTRUCTS = "invisibility_0_2",
            VANISHING_SQUAD = "invisibility_smoke_and_mirrors", LONG_VANISH = "invisibility_lingering_mirage",
            UNSEEN_FORCE = "invisibility_assassin";
    // Aegis
    public static final String WIDE_LENS = "invisibility_1_0", SAFETY_NET = "invisibility_1_2",
            FORCE_DISC = "invisibility_pickpocket", STEADY_DISC = "invisibility_light_fingers", REBOUND = "invisibility_phantom";
    // Pressure
    public static final String HARD_PUSH = "invisibility_2_0", TIGHT_SEAL = "invisibility_2_2",
            KINETIC_SHELL = "invisibility_cloak_field", PRESSURE_CHAIN = "invisibility_wider_cloak",
            IMPLOSION = "invisibility_claudes_lesson";

    private InvisibilitySkills() {}

    public static boolean has(ServerPlayer p, String id) { return SkillTreeService.has(p, id); }

    /** The three branch actives: Vanishing Touch, Force Barrier and Pinpoint. */
    public static boolean canUse(AbilityUseContext c, int branch) { return !c.player().isSpectator(); }

    public static boolean skill(AbilityUseContext c, int branch) {
        ServerPlayer p = c.player();
        return switch (branch) {
            case 0 -> InvisibilityService.vanishingTouch(p);
            case 1 -> ForceFieldService.barrier(p);
            case 2 -> ForceFieldService.pinpoint(p);
            default -> false;
        };
    }

    /** The three named techniques: Vanishing Squad, Force Disc and Kinetic Shell. */
    public static boolean technique(AbilityUseContext c, String mechanic) {
        ServerPlayer p = c.player();
        return switch (mechanic) {
            case "smoke_and_mirrors" -> InvisibilityService.vanishingSquad(p);
            case "pickpocket" -> ForceFieldService.disc(p);
            case "cloak_field" -> ForceFieldService.kineticShell(p);
            default -> false;
        };
    }
}
