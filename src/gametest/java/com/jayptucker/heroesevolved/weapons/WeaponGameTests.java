package com.jayptucker.heroesevolved.weapons;

import com.jayptucker.heroesevolved.company.CompanyAgentEntity;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.grid.GridDisruption;
import com.jayptucker.heroesevolved.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The power-themed TaCZ guns: the gun pack loads and its recipes are at the gun smith table; heat, cold and taser
 * rounds do what they should (called directly, and through real TaCZ shots); the EMP grenade kills redstone; and the
 * Company's guards carry TaCZ guns in the present and use them.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class WeaponGameTests {
    private static long lastTick;

    /**
     * TaCZ times draws and shots in real milliseconds, and the game-test server runs ticks as fast as it can, so a test
     * that fires real TaCZ guns holds each tick to the 50 ms it takes in play.
     */
    private static void realTime(GameTestHelper h) {
        h.onEachTick(() -> {
            long wait = 50 - (System.currentTimeMillis() - lastTick);
            if (wait > 0 && wait <= 50) try { Thread.sleep(wait); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            lastTick = System.currentTimeMillis();
        });
    }

    private static Pig pig(GameTestHelper h, BlockPos at) {
        Pig pig = h.spawn(EntityType.PIG, at);
        pig.setPersistenceRequired();
        return pig;
    }

    @GameTest(template = "rewind_empty", batch = "weapons.pack")
    public static void theGunPackLoadsWithItsRecipes(GameTestHelper h) {
        h.assertTrue(PowerWeapons.available(), "The TaCZ link is up");
        for (ResourceLocation id : new ResourceLocation[] {PowerWeapons.HEAT_GUN, PowerWeapons.COLD_GUN, PowerWeapons.TASER, PowerWeapons.AGENT_PISTOL}) {
            var gun = PowerWeapons.gun(id);
            h.assertTrue(!gun.isEmpty() && id.equals(PowerWeapons.gunId(gun)), "TaCZ makes a " + id);
            h.assertTrue(PowerWeapons.rounds(gun) >= PowerWeapons.magazine(id), id + " comes loaded");
        }
        for (ResourceLocation ammo : new ResourceLocation[] {PowerWeapons.FUEL_CELL, PowerWeapons.CRYO_CELL, PowerWeapons.TASER_CARTRIDGE})
            h.assertTrue(!PowerWeapons.ammo(ammo, 4).isEmpty(), "TaCZ makes " + ammo);
        var recipes = h.getLevel().getRecipeManager();
        for (String path : new String[] {"gun/heat_gun", "gun/cold_gun", "gun/taser", "ammo/fuel_cell", "ammo/cryo_cell", "ammo/taser_cartridge", "emp_grenade"}) {
            var recipe = recipes.byKey(ResourceLocation.fromNamespaceAndPath("heroesevolved", path));
            h.assertTrue(recipe.isPresent() && recipe.get().value().getType().toString().contains("gun_smith_table"),
                    path + " is crafted at the gun smith table");
        }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "weapons.heat")
    public static void heatRoundsSetAlightBuildAndThaw(GameTestHelper h) {
        Pig pig = pig(h, new BlockPos(3, 2, 3));
        PowerWeapons.heat(null, pig);
        h.assertTrue(pig.getRemainingFireTicks() >= PowerWeapons.HEAT_TICKS - 1, "One round sets it alight");
        for (int i = 0; i < 10; i++) PowerWeapons.heat(null, pig);
        h.assertTrue(pig.getRemainingFireTicks() <= PowerWeapons.HEAT_MAX, "The burn builds, but only so far: weaker than Pyrokinesis");
        pig.clearFire();
        CryoService.freezeSolid(pig, 100, null);
        h.assertTrue(CryoService.isFrozen(pig), "(frozen first)");
        PowerWeapons.heat(null, pig);
        h.assertTrue(!CryoService.isFrozen(pig), "Heat thaws a frozen creature");
        pig.discard();
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "weapons.cold")
    public static void coldRoundsBuildFrostThenBrieflyFreeze(GameTestHelper h) {
        Pig pig = pig(h, new BlockPos(3, 2, 3));
        double speed = pig.getAttributeValue(Attributes.MOVEMENT_SPEED);
        Vec3 at = pig.position();
        PowerWeapons.cold(null, pig, at);
        h.assertTrue(!CryoService.isFrozen(pig) && CryoService.frostAmount(pig) >= PowerWeapons.FROST_TICKS, "One bolt adds frost");
        h.assertTrue(pig.getAttributeValue(Attributes.MOVEMENT_SPEED) < speed, "Frost slows it");
        int hits = 1;
        while (!CryoService.isFrozen(pig) && hits < 10) { PowerWeapons.cold(null, pig, at); hits++; }
        h.assertTrue(CryoService.isFrozen(pig) && hits >= 3, "Several bolts freeze it solid (took " + hits + ")");
        h.runAfterDelay(PowerWeapons.FREEZE_MOB + 3, () -> {
            h.assertTrue(!CryoService.isFrozen(pig), "The freeze is brief");
            CryoService.releaseEntity(pig);
            pig.discard();
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "weapons.taser", timeoutTicks = 120)
    public static void aTaserLocksUpItsTarget(GameTestHelper h) {
        Zombie zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
        Pig pig = pig(h, new BlockPos(4, 2, 3));
        double jump = zombie.getAttributeValue(Attributes.JUMP_STRENGTH);
        PowerWeapons.tase(null, zombie, zombie.position().add(0, 1, 0));
        h.assertTrue(Stun.held(zombie), "The barbs hold it");
        h.assertTrue(zombie.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && zombie.getAttributeValue(Attributes.JUMP_STRENGTH) < jump * 0.1,
                "It can barely move and can't jump");
        float health = pig.getHealth();
        zombie.doHurtTarget(pig);
        h.assertTrue(pig.getHealth() == health, "A held body can't land a blow");
        h.runAfterDelay(Stun.MOB_TICKS + 3, () -> {
            h.assertTrue(!Stun.held(zombie) && Math.abs(zombie.getAttributeValue(Attributes.JUMP_STRENGTH) - jump) < 1e-6,
                    "The hold wears off");
            zombie.discard();
            pig.discard();
            h.succeed();
        });
    }

    /** An armor stand is a living entity, so TaCZ lets it fire: a shooter with no AI to get in the way. */
    private static ArmorStand shooter(GameTestHelper h, BlockPos at, ResourceLocation gun) {
        ArmorStand stand = h.spawn(EntityType.ARMOR_STAND, at);
        stand.setItemSlot(EquipmentSlot.MAINHAND, PowerWeapons.gun(gun));
        PowerWeapons.draw(stand);
        return stand;
    }

    private static void shootUntil(GameTestHelper h, ArmorStand stand, Pig pig, java.util.function.BooleanSupplier done, String why) {
        realTime(h);
        java.util.Map<String, Integer> results = new java.util.TreeMap<>();
        h.onEachTick(() -> {
            if (stand.tickCount > 12 && stand.tickCount % 5 == 0 && !done.getAsBoolean()) {
                if (PowerWeapons.rounds(stand.getMainHandItem()) <= 1) PowerWeapons.reload(stand.getMainHandItem());
                results.merge(PowerWeapons.fire(stand, pig.getBoundingBox().getCenter()), 1, Integer::sum);
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(done.getAsBoolean(), why + " (shots: " + results + ", pig hp " + pig.getHealth() + ")");
            CryoService.releaseEntity(pig);
            stand.discard();
            pig.discard();
        });
    }

    @GameTest(template = "rewind_empty", batch = "weapons.shots", timeoutTicks = 160)
    public static void realHeatShotsSetTheTargetAlight(GameTestHelper h) {
        Pig pig = pig(h, new BlockPos(7, 2, 3));
        pig.setNoAi(true);
        ArmorStand stand = shooter(h, new BlockPos(2, 2, 3), PowerWeapons.HEAT_GUN);
        shootUntil(h, stand, pig, () -> pig.getRemainingFireTicks() > 0 && pig.getHealth() < pig.getMaxHealth(), "A heat gun's rounds burn");
    }

    @GameTest(template = "rewind_empty", batch = "weapons.shots", timeoutTicks = 160)
    public static void realColdShotsFrostTheTarget(GameTestHelper h) {
        Pig pig = pig(h, new BlockPos(7, 2, 5));
        pig.setNoAi(true);
        ArmorStand stand = shooter(h, new BlockPos(2, 2, 5), PowerWeapons.COLD_GUN);
        shootUntil(h, stand, pig, () -> CryoService.frostAmount(pig) > 0 || CryoService.isFrozen(pig), "A cold gun's bolts frost");
    }

    @GameTest(template = "rewind_empty", batch = "weapons.shots", timeoutTicks = 160)
    public static void realTaserShotsStunTheTarget(GameTestHelper h) {
        Pig pig = pig(h, new BlockPos(6, 2, 7));
        pig.setNoAi(true);
        ArmorStand stand = shooter(h, new BlockPos(2, 2, 7), PowerWeapons.TASER);
        shootUntil(h, stand, pig, () -> Stun.held(pig), "A taser's barbs stun");
    }

    @GameTest(template = "rewind_empty", batch = "weapons.emp", timeoutTicks = 120)
    public static void anEmpGrenadeKillsTheRedstoneAroundIt(GameTestHelper h) {
        BlockPos power = new BlockPos(5, 2, 5), lamp = new BlockPos(5, 3, 5);
        h.setBlock(lamp, Blocks.REDSTONE_LAMP);
        h.setBlock(power, Blocks.REDSTONE_BLOCK);
        h.runAfterDelay(2, () -> {
            h.assertTrue(h.getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "(the lamp is lit first)");
            EmpGrenade.Thrown grenade = new EmpGrenade.Thrown(ModEntities.EMP_GRENADE.get(), h.getLevel());
            Vec3 from = h.absoluteVec(new Vec3(3.5, 4.5, 3.5));
            grenade.setPos(from);
            grenade.setDeltaMovement(0, -0.4, 0);
            h.getLevel().addFreshEntity(grenade);
            h.succeedWhen(() -> {
                h.assertTrue(grenade.isRemoved(), "The grenade lands, beeps and goes off");
                h.assertTrue(GridDisruption.jammed(h.getLevel(), h.absolutePos(lamp)), "Redstone near it is dead");
                h.assertTrue(!h.getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "...and the lamp goes dark");
                GridDisruption.clear();
            });
        });
    }

    @GameTest(template = "rewind_empty", batch = "weapons.agents")
    public static void guardsInThePresentCarryTaczGuns(GameTestHelper h) {
        Vec3 at = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 2, 2)));
        CompanyAgentEntity present = CompanyAgentEntity.create(ModEntities.COMPANY_AGENT.get(), h.getLevel(), CompanyAgentEntity.Role.GUARD, at, 0, false);
        CompanyAgentEntity past = CompanyAgentEntity.create(ModEntities.COMPANY_AGENT.get(), h.getLevel(), CompanyAgentEntity.Role.GUARD, at, 0, true);
        h.assertTrue(present.armed() && PowerWeapons.is(present.getMainHandItem(), PowerWeapons.AGENT_PISTOL)
                && PowerWeapons.is(present.taser(), PowerWeapons.TASER), "A guard in the present carries a TaCZ pistol and a taser");
        h.assertTrue(!past.armed() && past.getMainHandItem().is(Items.CROSSBOW), "A guard in the past keeps his dart rifle");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "weapons.agents", timeoutTicks = 260)
    public static void anArmedGuardShootsAtRange(GameTestHelper h) {
        CompanyAgentEntity guard = CompanyAgentEntity.create(ModEntities.COMPANY_AGENT.get(), h.getLevel(), CompanyAgentEntity.Role.GUARD,
                Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 2, 1))), 0, false);
        h.getLevel().addFreshEntity(guard);
        Pig pig = pig(h, new BlockPos(9, 2, 9));
        pig.setNoAi(true);
        realTime(h);
        h.onEachTick(() -> { if (guard.getTarget() != pig) guard.setTarget(pig); });
        h.succeedWhen(() -> {
            h.assertTrue(pig.getHealth() < pig.getMaxHealth() || !pig.isAlive(), "The guard's pistol hits");
            guard.discard();
            pig.discard();
        });
    }

    @GameTest(template = "rewind_empty", batch = "weapons.agents", timeoutTicks = 200)
    public static void anArmedGuardTasesUpClose(GameTestHelper h) {
        CompanyAgentEntity guard = CompanyAgentEntity.create(ModEntities.COMPANY_AGENT.get(), h.getLevel(), CompanyAgentEntity.Role.GUARD,
                Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 2, 2))), 0, false);
        h.getLevel().addFreshEntity(guard);
        Pig pig = pig(h, new BlockPos(6, 2, 2));
        pig.setNoAi(true);
        realTime(h);
        h.onEachTick(() -> { if (guard.getTarget() != pig) guard.setTarget(pig); });
        h.succeedWhen(() -> {
            h.assertTrue(Stun.held(pig), "Within seven blocks he swaps to the taser and stuns");
            guard.discard();
            pig.discard();
        });
    }
}
