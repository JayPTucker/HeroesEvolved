package com.jayptucker.heroesevolved.shapeshift;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.grid.GridGameTests;
import com.jayptucker.heroesevolved.progression.EvolutionTrials;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shapeshifting, phase 6: the tree's actives and passives, Apex Form, the class abilities, the trial and the awakening. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class ShapeshiftSkillsGameTests {
    private static ServerPlayer shifter(GameTestHelper h, int tier, String... nodes) {
        ServerPlayer p = GridGameTests.player(h, ModAbilities.SHAPESHIFTING_ID, tier);
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.SHAPESHIFTING_ID.toString(),
                new SkillTreeData.Build(28, Set.of(nodes), Map.of())));
        return p;
    }

    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, int x, int z) {
        T mob = type.create(h.getLevel());
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        mob.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x, 2, z))));
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static void close(double a, double b, String message, GameTestHelper h) {
        h.assertTrue(Math.abs(a - b) < 1.0E-3, message + " (" + a + " vs " + b + ")");
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.tree")
    public static void theTreeHasEighteenNodesASignatureAndThreeClasses(GameTestHelper h) {
        var nodes = SkillCatalog.nodes("shapeshifting");
        h.assertTrue(nodes.stream().filter(n -> !n.signature()).count() == 18, "Eighteen nodes");
        h.assertTrue(nodes.stream().filter(n -> !n.signature() && n.active()).count() == 6, "Six active skills");
        h.assertTrue(nodes.stream().filter(SkillCatalog.Node::signature).map(SkillCatalog.Node::id).toList().equals(List.of(ShapeshiftSkills.APEX_FORM)),
                "Apex Form is the signature");
        h.assertTrue(SkillCatalog.styles("shapeshifting").stream().map(SkillCatalog.Node::id).toList().equals(List.of(
                ShapeshiftSkills.MANY_HANDS, ShapeshiftSkills.PLATE_MAIL, ShapeshiftSkills.MASQUERADE)), "Offense, Defense, Support");
        for (var node : nodes) if (node.active()) h.assertTrue(node.action() != null, node.id() + " can be cast");
        h.assertTrue(EvolutionTrials.trial("shapeshifting").goal() == 1800, "The trial is thirty minutes among your kind");
        h.assertTrue(ModAbilities.rollable(ModAbilities.SHAPESHIFTING_ID), "With its tree and classes, the power can be handed out");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.tree")
    public static void quickChangeSwapsBackAndForth(GameTestHelper h) {
        ServerPlayer p = shifter(h, 2, ShapeshiftSkills.QUICK_CHANGE);
        try {
            h.assertTrue(!ShapeshiftSkills.quickChange(p), "Nothing to change back to yet");
            h.assertTrue(ShapeshiftService.morph(p, EntityType.ZOMBIE) && ShapeshiftService.morph(p, EntityType.COW), "A zombie, then a cow");
            h.assertTrue(ShapeshiftSkills.quickChange(p) && ShapeshiftService.form(p) == EntityType.ZOMBIE, "Quick Change: the zombie again");
            h.assertTrue(ShapeshiftSkills.quickChange(p) && ShapeshiftService.form(p) == EntityType.COW, "and back to the cow");
            h.assertTrue(ShapeshiftService.revert(p) && ShapeshiftSkills.quickChange(p) && ShapeshiftService.form(p) == EntityType.COW,
                    "From your own body it brings back the last shape");
        } finally { ShapeshiftService.forget(p); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.tree")
    public static void hybridAndChimeraBorrowFromEarlierShapes(GameTestHelper h) {
        ServerPlayer p = shifter(h, 3, ShapeshiftSkills.HYBRID, ShapeshiftSkills.CHIMERA);
        try {
            h.assertTrue(ShapeshiftService.morph(p, EntityType.CHICKEN) && ShapeshiftService.revert(p), "A chicken, then your own body");
            h.assertTrue(ShapeshiftSkills.hybrid(p), "Hybrid");
            h.assertTrue(ShapeshiftService.traits(p).contains(ShapeshiftTraits.Trait.SLOW_FALL), "Your own body falls like a chicken");
            h.assertTrue(p.getAttributeValue(Attributes.GRAVITY) < .08 - 1.0E-4, "with lighter gravity");
            h.assertTrue(ShapeshiftService.morph(p, EntityType.SPIDER) && ShapeshiftService.morph(p, EntityType.ZOMBIE), "A spider, then a zombie");
            h.assertTrue(!ShapeshiftSkills.hybrid(p), "Hybrid needs your own body");
            h.assertTrue(ShapeshiftSkills.chimera(p) && ShapeshiftInstinct.move(p) == InstinctMove.WEB, "Chimera: the zombie spits web");
        } finally { ShapeshiftService.forget(p); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.apex")
    public static void apexFormGrowsTheShapeAndSlams(GameTestHelper h) {
        ServerPlayer p = shifter(h, 4, ShapeshiftSkills.APEX, ShapeshiftSkills.APEX_FORM);
        var pillager = mob(h, EntityType.PILLAGER, 3, 6);
        try {
            h.assertTrue(!ShapeshiftSkills.apexForm(p), "Apex Form needs a creature's shape");
            h.assertTrue(ShapeshiftService.morph(p, EntityType.ZOMBIE), "A zombie");
            close(p.getMaxHealth(), 20, "A zombie's health (the Apex node lifts the cap, not the zombie)", h);
            float before = p.getMaxHealth();
            h.assertTrue(ShapeshiftSkills.apexForm(p), "Apex Form");
            close(p.getScale(), ShapeshiftSkills.APEX_SCALE, "Two and a half times the size", h);
            close(p.getBbHeight(), EntityType.ZOMBIE.getDimensions().height() * ShapeshiftSkills.APEX_SCALE, "and the hitbox grows with it", h);
            h.assertTrue(p.getMaxHealth() > before * 2, "with a boss's health");
            h.assertTrue(ShapeshiftInstinct.move(p) == InstinctMove.SLAM, "Instinct becomes a slam");
            float hp = pillager.getHealth();
            h.assertTrue(ShapeshiftInstinct.perform(p, InstinctMove.SLAM) && pillager.getHealth() < hp, "that throws what is near");
            h.assertTrue(ShapeshiftService.revert(p), "Back to your own body");
            close(p.getScale(), 1, "Apex Form ends with the shape", h);
        } finally { ShapeshiftService.forget(p); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.identity")
    public static void borrowedFaceSwapsLooksAndTurnsItsKindOnIt(GameTestHelper h) {
        ServerPlayer p = shifter(h, 4, ShapeshiftSkills.BORROWED_FACE);
        var pillager = mob(h, EntityType.PILLAGER, 3, 6);
        var vindicator = mob(h, EntityType.VINDICATOR, 7, 7);
        try {
            h.assertTrue(ShapeshiftTricks.borrowFace(p), "Borrowing the pillager's face");
            h.assertTrue(ShapeshiftService.form(p) == EntityType.PILLAGER, "You wear the pillager");
            var face = ShapeshiftTricks.borrowed(pillager);
            h.assertTrue(face != null && face.person() && face.isOf(p), "and it wears yours");
            h.assertTrue(vindicator.getTarget() == pillager && ShapeshiftTricks.turned(vindicator), "The vindicator goes for it");
            vindicator.setTarget(p);
            h.assertTrue(vindicator.getTarget() == pillager, "and can't be talked out of it");
        } finally { ShapeshiftService.forget(p); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.identity", timeoutTicks = 60)
    public static void aDoppelgangerWalksOnAhead(GameTestHelper h) {
        ServerPlayer p = shifter(h, 4, ShapeshiftSkills.DOPPELGANGER);
        var zombie = mob(h, EntityType.ZOMBIE, 9, 3);
        zombie.setTarget(p);
        ShapeDecoy copy;
        try {
            h.assertTrue(ShapeshiftTricks.doppelganger(p), "A doppelganger sets off");
            copy = ShapeshiftTricks.decoyOf(p);
            h.assertTrue(copy != null && copy.walking(), "walking");
            h.assertTrue(zombie.getTarget() == copy, "and your hunter follows it");
        } catch (RuntimeException e) { ShapeshiftService.forget(p); throw e; }
        Vec3 start = copy.position();
        h.runAfterDelay(20, () -> {
            try { h.assertTrue(copy.position().distanceTo(start) > 2, "A second later it has walked on (" + copy.position().distanceTo(start) + ")"); }
            finally { ShapeshiftService.forget(p); }
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.passives")
    public static void passivesChangeTheCoreWhereItDecides(GameTestHelper h) {
        ServerPlayer plain = shifter(h, 4);
        ServerPlayer skilled = shifter(h, 4, ShapeshiftSkills.BLEND_IN, ShapeshiftSkills.SECOND_SKIN, ShapeshiftSkills.HOLLOW_CROWD,
                ShapeshiftSkills.NATURAL_WEAPONS, ShapeshiftSkills.APEX, ShapeshiftSkills.AMBUSH, ShapeshiftSkills.LONG_CON,
                ShapeshiftSkills.PRACTISED, ShapeshiftSkills.SOCIAL);
        try {
            close(ShapeshiftSkills.suspicionGain(plain, 40, false), 40, "Plain Suspicion", h);
            close(ShapeshiftSkills.suspicionGain(skilled, 40, false), 28, "Blend In: 30% slower", h);
            close(ShapeshiftSkills.suspicionGain(skilled, 40, true), 14, "and Practised Manner halves it again among people", h);
            close(ShapeshiftSkills.heavyBlow(skilled), .5, "Second Skin: a blow must take half", h);
            h.assertTrue(ShapeshiftSkills.decoyLife(skilled) == 2 * ShapeDecoy.LIFE, "Hollow Crowd: decoys stand twice as long");
            h.assertTrue(ShapeshiftSkills.instinctCost(skilled, 20) == 14 && ShapeshiftSkills.instinctCost(plain, 20) == 20, "Natural Weapons: Instinct costs less");
            close(ShapeshiftSkills.lungeReach(skilled), ShapeshiftGeometry.LUNGE_REACH + 2, "and Lunge reaches farther", h);
            close(ShapeshiftSkills.personReach(skilled), 12, "Long Con: people from 12 blocks", h);
            h.assertTrue(ShapeshiftSkills.familiarTicks(skilled) == 600, "Social Engineering: Familiar lasts thirty seconds");
            h.assertTrue(ShapeshiftService.morph(skilled, EntityType.IRON_GOLEM), "An iron golem");
            close(skilled.getMaxHealth(), 80, "Apex: the health cap rises by 20", h);
            close(ShapeshiftSkills.ambush(skilled), 1.5, "Ambush Shift: the first Instinct after a change hits harder", h);
            close(ShapeshiftSkills.ambush(skilled), 1, "and only the first", h);
        } finally { ShapeshiftService.forget(plain); ShapeshiftService.forget(skilled); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.classes", timeoutTicks = 60)
    public static void manyHandsStrikesEveryFoeInTurn(GameTestHelper h) {
        ServerPlayer p = shifter(h, 4);
        var first = mob(h, EntityType.PILLAGER, 3, 6);
        var second = mob(h, EntityType.PILLAGER, 6, 5);
        float a = first.getHealth(), b = second.getHealth();
        try { h.assertTrue(ShapeshiftSkills.manyHands(p), "Many Hands"); }
        catch (RuntimeException e) { ShapeshiftService.forget(p); throw e; }
        h.runAfterDelay(ShapeshiftSkills.MANY_HANDS_GAP * 2 + 2, () -> {
            try { h.assertTrue(first.getHealth() < a && second.getHealth() < b, "Both foes are struck, one after the other"); }
            finally { ShapeshiftService.forget(p); }
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.classes")
    public static void plateMailAndMasquerade(GameTestHelper h) {
        ServerPlayer p = shifter(h, 4);
        mob(h, EntityType.HUSK, 3, 7);
        try {
            h.assertTrue(ShapeshiftSkills.plateMail(p) && ShapeshiftSkills.plated(p), "Plate Mail locks on");
            h.assertTrue(ShapeshiftSkills.masquerade(p), "Masquerade with nobody else near");
            h.assertTrue(ShapeshiftService.form(p) == EntityType.HUSK && ShapeshiftSkills.masked(p), "takes the nearest creature's shape for you");
        } finally { ShapeshiftService.forget(p); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.awakening")
    public static void theAwakeningTurnsThreatsOnTheirOwnKind(GameTestHelper h) {
        ServerPlayer p = shifter(h, 1);
        var threat = mob(h, EntityType.ZOMBIE, 3, 6);
        var other = mob(h, EntityType.ZOMBIE, 8, 8);
        var lonely = mob(h, EntityType.SPIDER, 1, 9);
        threat.setTarget(p);
        try {
            ShapeshiftTricks.awaken(p, p, List.of(threat, lonely));
            h.assertTrue(ShapeshiftService.form(p) == EntityType.ZOMBIE, "The owner ripples into the nearest threat's shape");
            h.assertTrue(threat.getTarget() == other && ShapeshiftTricks.turned(threat), "The zombie turns on the other zombie");
            h.assertTrue(ShapeshiftTricks.cowed(lonely), "A threat with none of its kind near stands confused");
        } finally { ShapeshiftService.forget(p); }
        h.succeed();
    }
}
