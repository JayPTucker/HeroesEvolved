package com.jayptucker.heroesevolved.skills;

import com.jayptucker.heroesevolved.ability.AbilityActivationResult;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.electro.BlackoutService;
import com.jayptucker.heroesevolved.electro.ElectroService;
import com.jayptucker.heroesevolved.grid.GridDisruption;
import com.jayptucker.heroesevolved.grid.GridGameTests;
import com.jayptucker.heroesevolved.grid.TestMachines;
import com.jayptucker.heroesevolved.pyro.PhoenixService;
import com.jayptucker.heroesevolved.time.SingularityService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;


/** The three signature abilities: where they sit in their trees, and what each one really does. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class SignatureAbilityGameTests {
    /** Signature id, its power, and the branch Mastery it hangs under. */
    private static final String[][] SIGNATURES = {
            {"electrokinesis_blackout", "electrokinesis", "electrokinesis_1_2"},
            {"time_manipulation_singularity", "time_manipulation", "time_manipulation_1_2"},
            {"pyrokinesis_phoenix", "pyrokinesis", "pyrokinesis_2_2"}};

    private static ServerPlayer player(GameTestHelper h, ResourceLocation power) { return GridGameTests.player(h, power, 5); }

    private static Pillager pillager(GameTestHelper h, Vec3 at) {
        Pillager pillager = EntityType.PILLAGER.create(h.getLevel());
        pillager.setNoAi(true); pillager.setPersistenceRequired();
        pillager.moveTo(at.x, at.y, at.z, 180, 0);
        h.getLevel().addFreshEntity(pillager);
        return pillager;
    }

    @GameTest(template = "rewind_empty", batch = "signature.catalog")
    public static void signatureAbilitiesHangUnderTheirBranchMastery(GameTestHelper h) {
        for (String[] signature : SIGNATURES) {
            String id = signature[0], power = signature[1], parent = signature[2];
            var node = SkillCatalog.find(power, id).orElse(null);
            h.assertTrue(node != null, "Missing signature node " + id);
            h.assertTrue(node.signature() && node.active() && !node.capstone(), id + " must be an active signature, not a capstone");
            h.assertTrue(node.prerequisite().equals(parent) && node.level() == 4 && node.cost() == 3, id + " sits under its branch Mastery at Level 10");
            h.assertTrue(node.action() != null && !node.mechanic().isEmpty(), id + " must have a working action");
        }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "signature.blackout", timeoutTicks = 80)
    public static void blackoutDrinksTheGridShocksEnemiesAndBecomesCharge(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.ELECTROKINESIS_ID);
        BlockPos cellPos = h.absolutePos(new BlockPos(6, 2, 3));
        h.getLevel().setBlock(cellPos, Blocks.BARREL.defaultBlockState(), 3);
        EnergyStorage cell = TestMachines.power(h.getLevel().getBlockEntity(cellPos), 100_000, 40_000);
        Pillager pillager = pillager(h, p.position().add(0, 0, 4));
        var node = SkillCatalog.find("electrokinesis", "electrokinesis_blackout").orElseThrow();
        AbilityUseContext context = new AbilityUseContext(p, ModAbilities.ELECTROKINESIS_ID, 5, false);
        h.assertTrue(node.action().canUse(context) && node.action().activate(context) == AbilityActivationResult.SUCCESS,
                "Blackout starts through the skill pipeline");
        h.assertTrue(BlackoutService.winding(p) && cell.getEnergyStored() == 40_000, "It pulls for a second before the pulse");
        h.assertFalse(node.action().canUse(context), "One Blackout at a time");
        h.runAfterDelay(BlackoutService.WIND_UP + 2, () -> {
            try {
                h.assertFalse(BlackoutService.winding(p), "The pulse has gone off");
                h.assertTrue(cell.getEnergyStored() == 0 && GridDisruption.stalled(h.getLevel(), cellPos), "The machine is drained and dead");
                h.assertTrue(ElectroService.charge(p) >= 20 + 40_000 / GridDisruption.FE_PER_CHARGE, "The grid became Charge: " + ElectroService.charge(p));
                h.assertTrue(pillager.hasEffect(MobEffects.DARKNESS) && pillager.getHealth() < pillager.getMaxHealth(), "Enemies are shocked into the dark");
                h.succeed();
            } finally { pillager.discard(); ElectroService.forget(p); BlackoutService.forget(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "signature.singularity", timeoutTicks = 120)
    public static void singularityPullsEnemiesInSwallowsShotsAndCollapses(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.TIME_MANIPULATION_ID);
        Vec3 center = Vec3.atCenterOf(h.absolutePos(new BlockPos(3, 3, 9)));
        Cow cow = EntityType.COW.create(h.getLevel());
        cow.moveTo(center.x + 6, center.y - 1.5, center.z, 0, 0);
        h.getLevel().addFreshEntity(cow);
        Pillager shooter = pillager(h, p.position().add(-3, 0, 0));
        Arrow arrow = new Arrow(h.getLevel(), center.x + 0.5, center.y, center.z, new ItemStack(Items.ARROW), null);
        arrow.setOwner(shooter);
        h.getLevel().addFreshEntity(arrow);
        ItemEntity loot = new ItemEntity(h.getLevel(), center.x - 2, center.y, center.z, new ItemStack(Items.EMERALD));
        h.getLevel().addFreshEntity(loot);
        double start = cow.getBoundingBox().getCenter().distanceTo(center);
        h.assertTrue(SingularityService.open(p, center, 5) && SingularityService.active(p), "The fold opens");
        h.runAfterDelay(3, () -> h.assertTrue(arrow.isRemoved(), "A hostile shot near the core is swallowed"));
        h.runAfterDelay(35, () -> {
            double now = cow.getBoundingBox().getCenter().distanceTo(center);
            h.assertTrue(now < start - 1.5, "It drags enemies in: " + start + " -> " + now);
            float before = cow.getHealth();
            SingularityService.collapse(p);
            try {
                h.assertFalse(SingularityService.active(p), "It collapses");
                h.assertTrue(cow.getHealth() < before || cow.isRemoved(), "The snap back hurts what it held");
                h.assertTrue(loot.isRemoved() || loot.distanceTo(p) < 1.5, "What it swallowed lands at your feet: " + loot.distanceTo(p));
                h.assertFalse(loot.isNoGravity(), "Loose items fall normally again");
                h.succeed();
            } finally { cow.discard(); shooter.discard(); loot.discard(); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "signature.phoenix", timeoutTicks = 240)
    public static void phoenixHuntsStrikesAndComesHomeToHeal(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.PYROKINESIS_ID);
        p.setHealth(10);
        // Exactly as many targets as it has strikes at Level 10, close together, so it never goes looking further afield.
        Pillager[] prey = {pillager(h, p.position().add(0, 0, 7)), pillager(h, p.position().add(2, 0, 8)), pillager(h, p.position().add(-2, 0, 8))};
        var node = SkillCatalog.find("pyrokinesis", "pyrokinesis_phoenix").orElseThrow();
        AbilityUseContext context = new AbilityUseContext(p, ModAbilities.PYROKINESIS_ID, 4, false);
        h.assertTrue(node.action().activate(context) == AbilityActivationResult.SUCCESS && PhoenixService.active(p), "The Phoenix takes wing");
        h.assertFalse(node.action().canUse(context), "One Phoenix at a time");
        h.succeedWhen(() -> {
            h.assertFalse(PhoenixService.active(p), "Still hunting");
            for (Pillager struck : prey)
                h.assertTrue(struck.isRemoved() || !struck.isAlive() || struck.getHealth() < struck.getMaxHealth(), "It struck each of its prey");
            h.assertTrue(p.getHealth() > 10 && p.hasEffect(MobEffects.FIRE_RESISTANCE), "It came home and closed the wounds: " + p.getHealth());
            for (Pillager struck : prey) struck.discard();
            PhoenixService.forget(p);
        });
    }
}
