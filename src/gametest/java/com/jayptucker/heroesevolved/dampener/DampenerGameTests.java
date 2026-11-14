package com.jayptucker.heroesevolved.dampener;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.grid.GridDisruption;
import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * The Ability Dampener and the Nullifier: a powered projector holds a field where no power answers, pays for it every
 * tick and drops it when it runs dry, is switched off by redstone and killed by an EMP; a dampener round quiets a body
 * for a while; the Nullifier's rounds come out of an RF battery that a projector, any FE machine or an FE charger
 * fills; and a real TaCZ shot dampens what it hits.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class DampenerGameTests {
    private static final BlockPos PYLON = new BlockPos(1, 2, 6);
    private static long lastTick;

    private static ServerPlayer gifted(GameTestHelper h, BlockPos at) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "dampener-test"));
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(at)));
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.QUAKE_ID).unlock(ModAbilities.QUAKE_ID));
        return p;
    }

    /** A projector at {@link #PYLON} with a four-block field and {@code fe} in its battery. */
    private static AbilityDampenerBlockEntity pylon(GameTestHelper h, int fe) {
        h.setBlock(PYLON, ModBlocks.ABILITY_DAMPENER.get());
        AbilityDampenerBlockEntity field = (AbilityDampenerBlockEntity) h.getBlockEntity(PYLON);
        while (field.radius() != 4) field.cycleRadius();
        field.fill(fe);
        return field;
    }

    @GameTest(template = "rewind_empty", batch = "dampener.field", timeoutTicks = 100)
    public static void aPoweredDampenerSilencesPowersInsideItsField(GameTestHelper h) {
        AbilityDampenerBlockEntity field = pylon(h, DampenerGeometry.CAPACITY);
        ServerPlayer near = gifted(h, PYLON.offset(2, 0, 0));
        ServerPlayer far = gifted(h, PYLON.offset(9, 0, 0));
        h.assertTrue(PowerRules.canAct(near), "(powers work before it spins up)");
        h.runAfterDelay(DampenerGeometry.SPIN_UP + 3, () -> {
            h.assertTrue(field.live(), "Spun up and paid for, the field is live");
            h.assertTrue(Dampening.dampened(near) && !PowerRules.canAct(near), "Two blocks from it, no power answers");
            h.assertTrue(!Dampening.dampened(far) && PowerRules.canAct(far), "Past its edge, powers work");
            h.assertTrue(h.getBlockState(PYLON).getValue(AbilityDampenerBlock.ACTIVE), "It lights up while it runs");
            int before = field.stored();
            h.runAfterDelay(10, () -> {
                int spent = before - field.stored();
                h.assertTrue(spent >= DampenerGeometry.upkeep(4) * 9 && spent <= DampenerGeometry.upkeep(4) * 11,
                        "It burns its upkeep every tick (spent " + spent + " FE in ten ticks)");
                h.succeed();
            });
        });
    }

    @GameTest(template = "rewind_empty", batch = "dampener.power", timeoutTicks = 100)
    public static void aDampenerWithoutEnoughPowerDropsItsField(GameTestHelper h) {
        int ticks = DampenerGeometry.SPIN_UP + 10;
        AbilityDampenerBlockEntity field = pylon(h, DampenerGeometry.upkeep(4) * ticks);
        ServerPlayer near = gifted(h, PYLON.offset(2, 0, 0));
        h.runAfterDelay(DampenerGeometry.SPIN_UP + 3, () -> {
            h.assertTrue(Dampening.dampened(near), "(the field is up while the charge lasts)");
            h.runAfterDelay(15, () -> {
                h.assertTrue(field.stored() < DampenerGeometry.upkeep(4) && !field.live(), "Out of RF, the field drops");
                h.assertTrue(!Dampening.dampened(near) && PowerRules.canAct(near), "...and powers come back");
                h.assertTrue(!h.getBlockState(PYLON).getValue(AbilityDampenerBlock.ACTIVE), "It goes dark");
                h.succeed();
            });
        });
    }

    @GameTest(template = "rewind_empty", batch = "dampener.switch", timeoutTicks = 100)
    public static void redstoneAndTheSwitchTurnItOff(GameTestHelper h) {
        AbilityDampenerBlockEntity field = pylon(h, DampenerGeometry.CAPACITY);
        ServerPlayer near = gifted(h, PYLON.offset(2, 0, 0));
        h.runAfterDelay(DampenerGeometry.SPIN_UP + 3, () -> {
            h.assertTrue(Dampening.dampened(near), "(on first)");
            h.setBlock(PYLON.above(), Blocks.REDSTONE_BLOCK);
            h.runAfterDelay(2, () -> {
                h.assertTrue(!field.live() && !Dampening.dampened(near), "A redstone signal switches it off");
                h.setBlock(PYLON.above(), Blocks.AIR);
                field.toggle();
                h.runAfterDelay(DampenerGeometry.SPIN_UP + 3, () -> {
                    h.assertTrue(!field.enabled() && !field.live(), "Switched off by hand, it stays off with no signal");
                    field.toggle();
                    h.runAfterDelay(DampenerGeometry.SPIN_UP + 3, () -> {
                        h.assertTrue(field.live() && Dampening.dampened(near), "Switched back on, it spins up again");
                        h.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = "rewind_empty", batch = "dampener.emp", timeoutTicks = 100)
    public static void anEmpKillsTheField(GameTestHelper h) {
        AbilityDampenerBlockEntity field = pylon(h, 100_000);
        ServerPlayer near = gifted(h, PYLON.offset(2, 0, 0));
        h.runAfterDelay(DampenerGeometry.SPIN_UP + 3, () -> {
            h.assertTrue(Dampening.dampened(near), "(on first)");
            int before = field.stored();
            GridDisruption.emp(null, h.getLevel(), h.absolutePos(PYLON.offset(3, 0, 0)), 8, 40);
            h.runAfterDelay(2, () -> {
                try {
                    h.assertTrue(field.stored() < before, "The pulse drains its battery");
                    h.assertTrue(!field.live() && !Dampening.dampened(near) && PowerRules.canAct(near), "...and the stalled projector drops its field");
                    h.succeed();
                } finally {
                    GridDisruption.clear();
                }
            });
        });
    }

    @GameTest(template = "rewind_empty", batch = "dampener.round", timeoutTicks = 260)
    public static void aDampenerRoundQuietsAPowerForAWhile(GameTestHelper h) {
        ServerPlayer p = gifted(h, new BlockPos(5, 2, 5));
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(7, 2, 7));
        h.assertTrue(PowerRules.canAct(p), "(powers work first)");
        int ticks = Dampening.strike(null, p, p.position());
        Dampening.strike(null, pig, pig.position());
        h.assertTrue(ticks == DampenerGeometry.ROUND_PLAYER_TICKS, "A round holds a player for eight seconds");
        h.assertTrue(Dampening.struck(p) && Dampening.dampened(p) && !PowerRules.canAct(p), "Struck, no power answers");
        h.assertTrue(Dampening.struck(pig), "It marks anything it hits");
        p.removeEffectsCuredBy(net.neoforged.neoforge.common.EffectCures.MILK);
        h.assertTrue(Dampening.struck(p), "Milk doesn't wash it out");
        Dampening.strike(null, p, p.position());
        h.assertTrue(p.getEffect(ModItems.DAMPENED).getDuration() == DampenerGeometry.ROUND_PLAYER_TICKS, "A second round refreshes the time, not adds to it");
        // The test player isn't in the level, so its effects don't tick: the pig shows the hold wearing off.
        h.runAfterDelay(DampenerGeometry.ROUND_MOB_TICKS + 5, () -> {
            h.assertTrue(!Dampening.struck(pig) && !Dampening.dampened(pig), "It wears off");
            p.removeEffect(ModItems.DAMPENED);
            h.assertTrue(PowerRules.canAct(p), "...and with it gone, the power comes back");
            pig.discard();
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "dampener.battery")
    public static void theNullifiersRoundsComeFromItsRfBattery(GameTestHelper h) {
        h.assertTrue(PowerWeapons.available(), "The TaCZ link is up");
        ItemStack gun = PowerWeapons.gun(PowerWeapons.DAMPENER_GUN);
        h.assertTrue(Nullifier.is(gun) && PowerWeapons.rounds(gun) >= DampenerGeometry.MAGAZINE, "TaCZ makes a loaded Nullifier");
        h.assertTrue(PowerWeapons.reserve(gun) == 0, "It has a battery (TaCZ's internal reserve) and it starts empty");
        int took = Nullifier.receive(gun, DampenerGeometry.FE_PER_ROUND * 2 + 1_000, false);
        h.assertTrue(took == DampenerGeometry.FE_PER_ROUND * 2 + 1_000 && Nullifier.rounds(gun) == 2, "RF becomes rounds");
        Nullifier.receive(gun, DampenerGeometry.FE_PER_ROUND - 1_000, false);
        h.assertTrue(Nullifier.rounds(gun) == 3, "Part-made rounds are kept until the next charge finishes them");
        Nullifier.receive(gun, Integer.MAX_VALUE, false);
        h.assertTrue(Nullifier.rounds(gun) == DampenerGeometry.BATTERY_ROUNDS, "The battery fills, and stops at " + DampenerGeometry.BATTERY_ROUNDS);

        ItemStack fresh = PowerWeapons.gun(PowerWeapons.DAMPENER_GUN);
        IEnergyStorage storage = fresh.getCapability(Capabilities.EnergyStorage.ITEM);
        h.assertTrue(storage != null && storage.canReceive(), "Any RF charger sees the gun as a Forge Energy item");
        int pushed = 0;
        for (int i = 0; i < 40; i++) pushed += storage.receiveEnergy(DampenerGeometry.GUN_MAX_RECEIVE * 4, false);
        h.assertTrue(Nullifier.rounds(fresh) == DampenerGeometry.BATTERY_ROUNDS && pushed == DampenerGeometry.BATTERY_ROUNDS * DampenerGeometry.FE_PER_ROUND,
                "A charger fills it through the capability (" + Nullifier.rounds(fresh) + " rounds)");
        h.assertTrue(PowerWeapons.gun(PowerWeapons.TASER).getCapability(Capabilities.EnergyStorage.ITEM) == null, "Other TaCZ guns hold no RF");

        Pig pig = h.spawn(EntityType.PIG, new BlockPos(5, 2, 5));
        pig.setItemSlot(EquipmentSlot.MAINHAND, fresh);
        long drained = GridDisruption.drainCarried(pig, 1_000_000);
        h.assertTrue(drained > 0 && Nullifier.rounds(pig.getMainHandItem()) < DampenerGeometry.BATTERY_ROUNDS, "An EMP drains the battery like any other");
        pig.discard();
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "dampener.charge")
    public static void aDampenerChargesTheGunAndKeepsItsChargeWhenBroken(GameTestHelper h) {
        AbilityDampenerBlockEntity field = pylon(h, 100_000);
        ServerPlayer p = gifted(h, PYLON.offset(1, 0, 0));
        ItemStack gun = PowerWeapons.gun(PowerWeapons.DAMPENER_GUN);
        int moved = Nullifier.charge(p, gun, h.absolutePos(PYLON));
        h.assertTrue(moved == DampenerGeometry.MAX_EXTRACT && Nullifier.rounds(gun) == DampenerGeometry.MAX_EXTRACT / DampenerGeometry.FE_PER_ROUND,
                "One use draws up to " + DampenerGeometry.MAX_EXTRACT + " FE from the projector into the gun (moved " + moved + ")");
        h.assertTrue(field.stored() == 100_000 - moved, "...and the projector loses exactly that");

        var drops = Block.getDrops(h.getBlockState(PYLON), h.getLevel(), h.absolutePos(PYLON), field);
        h.assertTrue(drops.size() == 1 && drops.getFirst().is(ModItems.ABILITY_DAMPENER.get())
                && drops.getFirst().getOrDefault(ModItems.STORED_ENERGY.get(), 0) == field.stored(), "Broken, it keeps its charge in the item");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "dampener.pack")
    public static void theNullifierIsAtTheGunSmithTable(GameTestHelper h) {
        h.assertTrue(!PowerWeapons.ammo(PowerWeapons.DAMPENER_ROUND, 1).isEmpty(), "TaCZ knows the dampener round");
        var recipes = h.getLevel().getRecipeManager();
        var gun = recipes.byKey(ResourceLocation.fromNamespaceAndPath("heroesevolved", "gun/dampener_gun"));
        h.assertTrue(gun.isPresent() && gun.get().value().getType().toString().contains("gun_smith_table"), "The Nullifier is crafted at the gun smith table");
        h.assertTrue(recipes.byKey(ResourceLocation.fromNamespaceAndPath("heroesevolved", "ammo/dampener_round")).isEmpty(),
                "Its rounds have no recipe: they come from the battery");
        h.assertTrue(recipes.byKey(ResourceLocation.fromNamespaceAndPath("heroesevolved", "ability_dampener")).isPresent(), "The projector has a recipe");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "dampener.shot", timeoutTicks = 160)
    public static void aRealNullifierShotDampensItsTarget(GameTestHelper h) {
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(7, 2, 3));
        pig.setPersistenceRequired();
        pig.setNoAi(true);
        ArmorStand stand = h.spawn(EntityType.ARMOR_STAND, new BlockPos(2, 2, 3));
        stand.setItemSlot(EquipmentSlot.MAINHAND, PowerWeapons.gun(PowerWeapons.DAMPENER_GUN));
        PowerWeapons.draw(stand);
        // TaCZ times shots in real milliseconds: hold each tick to 50 ms.
        h.onEachTick(() -> {
            long wait = 50 - (System.currentTimeMillis() - lastTick);
            if (wait > 0 && wait <= 50) try { Thread.sleep(wait); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            lastTick = System.currentTimeMillis();
            if (stand.tickCount > 12 && stand.tickCount % 5 == 0 && !Dampening.struck(pig)) {
                if (PowerWeapons.rounds(stand.getMainHandItem()) <= 1) PowerWeapons.reload(stand.getMainHandItem());
                PowerWeapons.fire(stand, pig.getBoundingBox().getCenter());
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(Dampening.struck(pig), "A Nullifier round dampens what it hits");
            stand.discard();
            pig.discard();
        });
    }
}
