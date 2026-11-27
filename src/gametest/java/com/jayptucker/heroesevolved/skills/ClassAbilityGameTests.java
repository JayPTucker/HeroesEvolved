package com.jayptucker.heroesevolved.skills;

import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.Ultimates;
import com.jayptucker.heroesevolved.grid.GridGameTests;
import com.jayptucker.heroesevolved.styles.StyleRuntime;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The classes: three per power (Offense, Defense, Support), one at a time, each an Ultimate on a long cooldown, and the
 * class perks. Every Ultimate itself is run by {@code combat/UltimateGameTests}.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class ClassAbilityGameTests {
    /** Two minutes, the shortest an Ultimate may wait between uses. */
    public static final int ULTIMATE_COOLDOWN = 20 * 120;

    private static Pillager pillager(GameTestHelper h, Vec3 at) {
        Pillager pillager = EntityType.PILLAGER.create(h.getLevel());
        pillager.setNoAi(true); pillager.setPersistenceRequired();
        pillager.moveTo(at.x, at.y, at.z, 180, 0);
        h.getLevel().addFreshEntity(pillager);
        return pillager;
    }

    @GameTest(template = "rewind_empty", batch = "class.catalog")
    public static void everyPowerHasAnOffenseADefenseAndASupportUltimate(GameTestHelper h) {
        int powers = 0;
        Set<String> mechanics = new HashSet<>();
        for (ResourceLocation power : AbilityRegistry.ABILITIES.keySet()) {
            var styles = SkillCatalog.styles(power.getPath());
            if (SkillCatalog.nodes(power.getPath()).isEmpty()) continue;
            powers++;
            h.assertTrue(styles.stream().map(SkillCatalog.Node::role).toList().equals(List.of("offense", "defense", "support")),
                    power + " must offer Offense, Defense and Support, in that order");
            for (var node : styles) {
                h.assertTrue(node.style() && node.active() && !node.capstone() && node.prerequisite().isEmpty() && node.cost() == 3,
                        node.id() + " is a free-standing active class ability");
                h.assertTrue(node.action() != null && SkillCatalog.find(power.getPath(), node.id()).isPresent(), node.id() + " can be found and cast");
                h.assertTrue(Ultimates.handles(node.mechanic()), node.id() + " is an Ultimate (" + node.mechanic() + ")");
                h.assertTrue(Ultimates.of(node.mechanic()).kind().name().toLowerCase().equals(node.role()),
                        node.id() + "'s Ultimate is a " + node.role() + " one");
                h.assertTrue(node.cooldown() >= ULTIMATE_COOLDOWN, node.id() + " waits at least two minutes: " + node.cooldown() / 20 + " s");
                h.assertTrue(mechanics.add(node.mechanic()), node.mechanic() + " is used by one class only");
            }
            // The tree itself is untouched: still eighteen nodes (and any signature).
            h.assertTrue(SkillCatalog.nodes(power.getPath()).stream().noneMatch(SkillCatalog.Node::style), power + "'s tree holds no class abilities");
        }
        // Powers still being built get their tree and classes in a later phase.
        h.assertTrue(powers == AbilityRegistry.ABILITIES.size() - ModAbilities.IN_DEVELOPMENT.size(), "Every registered power must have classes");
        h.assertTrue(mechanics.equals(Ultimates.mechanics()), "Every Ultimate belongs to a class, and every class has one");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "class.choice")
    public static void choosingAnotherClassReplacesTheFirstAndGivesItsPointsBack(GameTestHelper h) {
        ServerPlayer p = GridGameTests.player(h, ModAbilities.PYROKINESIS_ID, 5);
        var power = ModAbilities.PYROKINESIS_ID;
        int before = SkillCatalog.available("pyrokinesis", SkillTreeService.build(p, power));
        h.assertTrue(SkillTreeService.purchase(p, "pyrokinesis_sunfall"), "Offense can be chosen");
        h.assertTrue(SkillTreeService.bind(p, 7, "pyrokinesis_sunfall"), "and put on key 7");
        h.assertTrue("offense".equals(StyleRuntime.role(p)), "The class is Offense");
        h.assertTrue(SkillCatalog.available("pyrokinesis", SkillTreeService.build(p, power)) == before - 3, "It cost three points");
        h.assertTrue(SkillTreeService.purchase(p, "pyrokinesis_hearthfire"), "Support can be chosen instead");
        var build = SkillTreeService.build(p, power);
        h.assertTrue(build.learned().contains("pyrokinesis_hearthfire") && !build.learned().contains("pyrokinesis_sunfall"), "One class at a time");
        h.assertTrue(SkillCatalog.available("pyrokinesis", build) == before - 3, "The first class's points came back");
        h.assertTrue("pyrokinesis_hearthfire".equals(Loadout.binding(build, 7)), "The new class ability took the old one's key");
        h.assertTrue("support".equals(StyleRuntime.role(p)), "The class is now Support");
        h.assertFalse(SkillTreeService.purchase(p, "pyrokinesis_hearthfire"), "Choosing the class you have does nothing");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "class.perks")
    public static void offenseHitsHarder(GameTestHelper h) {
        ServerPlayer p = GridGameTests.player(h, ModAbilities.PYROKINESIS_ID, 5);
        Pillager foe = pillager(h, p.position().add(0, 0, 2));
        float start = foe.getHealth();
        foe.hurt(p.damageSources().playerAttack(p), 10);
        float plain = start - foe.getHealth();
        h.assertTrue(SkillTreeService.purchase(p, "pyrokinesis_sunfall"), "Offense chosen");
        foe.invulnerableTime = 0;
        float mid = foe.getHealth();
        foe.hurt(p.damageSources().playerAttack(p), 10);
        float boosted = mid - foe.getHealth();
        h.assertTrue(Math.abs(boosted - plain * 1.1F) < 0.01F, "Offense deals 10% more: " + plain + " then " + boosted);
        h.succeed();
    }
}
