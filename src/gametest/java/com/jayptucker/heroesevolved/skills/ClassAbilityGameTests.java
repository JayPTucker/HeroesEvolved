package com.jayptucker.heroesevolved.skills;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.grid.GridGameTests;
import com.jayptucker.heroesevolved.styles.StyleCasts;
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

import java.util.List;

/** The class abilities: three per power, one class at a time, and what the wards and perks really do. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class ClassAbilityGameTests {
    private static Pillager pillager(GameTestHelper h, Vec3 at) {
        Pillager pillager = EntityType.PILLAGER.create(h.getLevel());
        pillager.setNoAi(true); pillager.setPersistenceRequired();
        pillager.moveTo(at.x, at.y, at.z, 180, 0);
        h.getLevel().addFreshEntity(pillager);
        return pillager;
    }

    @GameTest(template = "rewind_empty", batch = "class.catalog")
    public static void everyPowerHasAnOffenseADefenseAndASupportClass(GameTestHelper h) {
        int powers = 0;
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
            }
            // The tree itself is untouched: still eighteen nodes (and any signature).
            h.assertTrue(SkillCatalog.nodes(power.getPath()).stream().noneMatch(SkillCatalog.Node::style), power + "'s tree holds no class abilities");
        }
        h.assertTrue(powers == AbilityRegistry.ABILITIES.size(), "Every registered power must have classes");
        h.succeed();
    }

    /** Each class ability runs through its real mechanic without failing, with a foe in front of it. */
    @GameTest(template = "rewind_empty", batch = "class.casts", timeoutTicks = 60)
    public static void everyClassAbilityCasts(GameTestHelper h) {
        for (ResourceLocation power : AbilityRegistry.ABILITIES.keySet()) {
            for (var node : SkillCatalog.styles(power.getPath())) {
                ServerPlayer p = GridGameTests.player(h, power, 5);
                Pillager foe = pillager(h, p.position().add(0, 0, 3));
                var context = new AbilityUseContext(p, power, 5, false);
                h.assertTrue(StyleCasts.canUse(context, node.mechanic()), node.id() + " can be used with a foe near");
                h.assertTrue(StyleCasts.cast(context, node.mechanic()), node.id() + " casts");
                StyleRuntime.forget(p);
                foe.discard();
            }
        }
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

    @GameTest(template = "rewind_empty", batch = "class.wards")
    public static void wardsCatchKillingBlowsAndShareWounds(GameTestHelper h) {
        ServerPlayer p = GridGameTests.player(h, ModAbilities.REGENERATION_ID, 5);
        Pillager undying = pillager(h, p.position().add(2, 0, 2));
        StyleRuntime.ward(undying, StyleRuntime.Ward.UNDYING, 100, p);
        undying.hurt(h.getLevel().damageSources().generic(), 500);
        h.assertTrue(undying.isAlive() && undying.getHealth() >= 1, "Undying keeps its last heart");

        Pillager painted = pillager(h, p.position().add(-2, 0, 2));
        StyleRuntime.ward(painted, StyleRuntime.Ward.PROPHECY, 100, p);
        painted.hurt(h.getLevel().damageSources().generic(), 500);
        h.assertTrue(painted.isAlive(), "The prophecy saves them once");
        painted.invulnerableTime = 0;
        painted.removeAllEffects();
        painted.hurt(h.getLevel().damageSources().generic(), 500);
        h.assertFalse(painted.isAlive(), "but only once");

        Pillager warded = pillager(h, p.position().add(2, 0, -2));
        StyleRuntime.ward(warded, StyleRuntime.Ward.EMPATHY, 100, p).charges = 2;
        float full = warded.getHealth();
        warded.hurt(h.getLevel().damageSources().generic(), 5);
        warded.invulnerableTime = 0;
        warded.hurt(h.getLevel().damageSources().generic(), 5);
        h.assertTrue(warded.getHealth() == full, "Each sigil catches a blow");
        warded.invulnerableTime = 0;
        warded.hurt(h.getLevel().damageSources().generic(), 5);
        h.assertTrue(warded.getHealth() == full - 5, "and then they are spent");

        Pillager a = pillager(h, p.position().add(-2, 0, -2)), b = pillager(h, p.position().add(-3, 0, -2));
        List<net.minecraft.world.entity.LivingEntity> hive = List.of(a, b);
        StyleRuntime.ward(a, StyleRuntime.Ward.HIVE, 100, p).group = hive;
        StyleRuntime.ward(b, StyleRuntime.Ward.HIVE, 100, p).group = hive;
        float ha = a.getHealth(), hb = b.getHealth();
        a.hurt(h.getLevel().damageSources().generic(), 8);
        h.assertTrue(Math.abs((ha - a.getHealth()) - 4) < 0.01F && Math.abs((hb - b.getHealth()) - 4) < 0.01F,
                "The hive shares a wound evenly: " + (ha - a.getHealth()) + " and " + (hb - b.getHealth()));
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "class.hour_hand", timeoutTicks = 60)
    public static void theHourHandSweepsEveryFoeItPasses(GameTestHelper h) {
        ServerPlayer p = GridGameTests.player(h, ModAbilities.TIME_MANIPULATION_ID, 5);
        List<Pillager> foes = List.of(pillager(h, p.position().add(0, 0, 4)), pillager(h, p.position().add(4, 0, 0)),
                pillager(h, p.position().add(0, 0, -4)), pillager(h, p.position().add(-4, 0, 0)));
        float full = foes.getFirst().getHealth();
        h.assertTrue(StyleCasts.cast(new AbilityUseContext(p, ModAbilities.TIME_MANIPULATION_ID, 5, false), "hour_hand"), "The hand sweeps");
        h.runAfterDelay(26, () -> {
            for (Pillager foe : foes) h.assertTrue(foe.getHealth() < full, "Every foe round the clock was struck");
            h.succeed();
        });
    }
}
