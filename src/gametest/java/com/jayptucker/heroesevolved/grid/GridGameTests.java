package com.jayptucker.heroesevolved.grid;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.electro.ElectroService;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.radiation.RadiationService;
import com.jayptucker.heroesevolved.technopathy.TechnopathyService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Electricity reaching real machines: pulses drain and stop anything exposing Forge Energy (the same
 * capability lookup Mekanism and Thermal machines answer), lightning charges them, carried energy items
 * are drained, and Technopathy treats powered machines as part of a circuit.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class GridGameTests {
    public static ServerPlayer player(GameTestHelper h, ResourceLocation power, int tier) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "grid-test"));
        try {
            Connection connection = p.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3)))); p.setYRot(0); p.setXRot(0);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        PlayerProgressionService.setLevel(p, tier);
        PlayerEnergyService.restore(p, 999);
        return p;
    }

    static BlockEntity machine(GameTestHelper h, BlockPos relative, net.minecraft.world.level.block.Block block) {
        BlockPos pos = h.absolutePos(relative);
        h.getLevel().setBlock(pos, block.defaultBlockState(), 3);
        return h.getLevel().getBlockEntity(pos);
    }

    private static int litTime(AbstractFurnaceBlockEntity furnace) {
        try {
            Field field = AbstractFurnaceBlockEntity.class.getDeclaredField("litTime"); field.setAccessible(true);
            return field.getInt(furnace);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @GameTest(template = "rewind_empty", batch = "grid.emp_machines", timeoutTicks = 140)
    public static void pulseDrainsAndStopsPoweredMachinesThenTheyWake(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.RADIATION_ID, 5);
        var furnace = (AbstractFurnaceBlockEntity) machine(h, new BlockPos(6, 2, 3), Blocks.FURNACE);
        furnace.setItem(0, new ItemStack(Items.RAW_IRON, 8));
        furnace.setItem(1, new ItemStack(Items.COAL, 4));
        EnergyStorage battery = TestMachines.power(furnace, 100_000, 50_000);
        BlockEntity consumerBlock = machine(h, new BlockPos(1, 2, 6), Blocks.BARREL);
        EnergyStorage consumer = TestMachines.consumer(consumerBlock, 10_000, 8_000);
        h.runAfterDelay(5, () -> {
            h.assertTrue(litTime(furnace) > 0, "Fixture: the furnace must be burning before the pulse");
            GridDisruption.Pulse pulse = GridDisruption.emp(p, h.getLevel(), p.blockPosition(), 16, 40);
            h.assertTrue(pulse.machines() >= 2, "Both powered machines are in the pulse: " + pulse.machines());
            h.assertTrue(battery.getEnergyStored() == 0 && pulse.drained() >= 50_000, "A drainable battery is emptied: " + battery.getEnergyStored());
            h.assertTrue(consumer.getEnergyStored() == 8_000, "A consumer that refuses extraction keeps its buffer (it is stalled instead)");
            h.assertTrue(GridDisruption.stalled(h.getLevel(), furnace.getBlockPos()) && GridDisruption.stalled(h.getLevel(), consumerBlock.getBlockPos()),
                    "Every machine in the pulse stops ticking");
            int before = litTime(furnace);
            h.runAfterDelay(20, () -> {
                h.assertTrue(litTime(furnace) >= before - 1, "A dead machine does not run: " + before + " -> " + litTime(furnace));
                battery.receiveEnergy(5_000, false);
            });
            h.runAfterDelay(31, () -> h.assertTrue(battery.getEnergyStored() < 5_000, "Energy a machine regains inside the field is eaten"));
            h.runAfterDelay(60, () -> {
                h.assertFalse(GridDisruption.stalled(h.getLevel(), furnace.getBlockPos()), "The machine wakes when the field ends");
                int woke = litTime(furnace);
                h.runAfterDelay(10, () -> {
                    h.assertTrue(litTime(furnace) < woke - 5, "And runs again: " + woke + " -> " + litTime(furnace));
                    h.assertTrue(h.getLevel().getBlockState(furnace.getBlockPos()).is(Blocks.FURNACE)
                            && furnace.getItem(0).is(Items.RAW_IRON), "The machine and its contents are untouched");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = "rewind_empty", batch = "grid.drain_cap")
    public static void oneMachineCannotBeDrainedPastTheConfiguredCap(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.RADIATION_ID, 5);
        BlockEntity cell = machine(h, new BlockPos(5, 2, 3), Blocks.BARREL);
        EnergyStorage storage = TestMachines.power(cell, 2_000_000, 1_000_000);
        GridDisruption.Pulse pulse = GridDisruption.emp(p, h.getLevel(), p.blockPosition(), 8, 20);
        int cap = com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON.gridMaxDrainPerMachine.get();
        h.assertTrue(pulse.drained() >= cap && storage.getEnergyStored() == 1_000_000 - cap,
                "An induction-matrix-sized store loses at most the cap: " + storage.getEnergyStored());
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "grid.radiation_emp", timeoutTicks = 60)
    public static void radiationEmpReachesMachinesToo(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.RADIATION_ID, 5);
        BlockEntity cell = machine(h, new BlockPos(6, 2, 3), Blocks.BARREL);
        EnergyStorage storage = TestMachines.power(cell, 10_000, 10_000);
        RadiationService.emp(p);
        h.assertTrue(storage.getEnergyStored() == 0, "Ted's pulse drains Forge Energy");
        h.assertTrue(GridDisruption.stalled(h.getLevel(), cell.getBlockPos()), "and stops the machine");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "grid.lightning_charge")
    public static void lightningChargesAMachineItLandsOn(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.ELECTROKINESIS_ID, 3);
        BlockEntity cell = machine(h, new BlockPos(3, 2, 8), Blocks.BARREL);
        EnergyStorage storage = TestMachines.power(cell, 100_000, 0);
        // Aim straight at the cell, five blocks ahead, and throw a bolt at it.
        Vec3 target = Vec3.atCenterOf(cell.getBlockPos()), direction = target.subtract(p.getEyePosition());
        p.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        p.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z))));
        ElectroService.chainLightning(p, 3);
        int bolt = com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON.gridLightningCharge.get();
        h.assertTrue(storage.getEnergyStored() == bolt, "A bolt pushes one bolt's worth into the machine: " + storage.getEnergyStored());
        // Inside a pulse the field eats the current instead.
        GridDisruption.emp(p, h.getLevel(), cell.getBlockPos(), 4, 20);
        h.assertTrue(GridDisruption.charge(p, h.getLevel(), cell.getBlockPos(), 1_000) == 0, "A machine inside a pulse takes no charge");
        ElectroService.forget(p);
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "grid.storm_surge")
    public static void stormSurgeChargesEveryMachineAround(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.ELECTROKINESIS_ID, 5);
        EnergyStorage a = TestMachines.power(machine(h, new BlockPos(6, 2, 3), Blocks.BARREL), 1_000_000, 0);
        EnergyStorage b = TestMachines.power(machine(h, new BlockPos(1, 2, 6), Blocks.BARREL), 1_000_000, 0);
        ElectroService.stormSurge(p, 5);
        h.assertTrue(a.getEnergyStored() > 0 && b.getEnergyStored() > 0, "A power surge charges every machine in reach");
        ElectroService.forget(p);
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "grid.carried")
    public static void pulsesDrainEnergyItemsEnemiesCarry(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.RADIATION_ID, 5);
        Pillager pillager = EntityType.PILLAGER.create(h.getLevel());
        pillager.setNoAi(true);
        pillager.moveTo(p.getX() + 3, p.getY(), p.getZ(), 0, 0);
        ItemStack gadget = new ItemStack(Items.COMPASS);
        EnergyStorage cell = TestMachines.power(gadget, 40_000, 40_000);
        pillager.setItemSlot(EquipmentSlot.MAINHAND, gadget);
        h.getLevel().addFreshEntity(pillager);
        try {
            GridDisruption.Pulse pulse = GridDisruption.emp(p, h.getLevel(), p.blockPosition(), 8, 20);
            h.assertTrue(cell.getEnergyStored() == 0 && pulse.items() >= 1, "The pillager's powered gadget is drained: " + cell.getEnergyStored());
            h.succeed();
        } finally { pillager.discard(); }
    }

    @GameTest(template = "rewind_empty", batch = "grid.technopathy")
    public static void technopathyEnrolsAndKillsPoweredMachines(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.TECHNOPATHY_ID, 5);
        BlockEntity cell = machine(h, new BlockPos(6, 2, 3), Blocks.BARREL);
        h.assertFalse(TechnopathyService.isNode(h.getLevel(), cell.getBlockPos()), "An unpowered barrel is not a relay");
        TestMachines.consumer(cell, 10_000, 0);
        h.assertTrue(TechnopathyService.isNode(h.getLevel(), cell.getBlockPos()), "A powered machine relays a Handshake");
        h.assertTrue(GridDisruption.stall(p, h.getLevel(), cell.getBlockPos(), 20) && GridDisruption.stalled(h.getLevel(), cell.getBlockPos()),
                "Kill Switch shuts a machine down");
        h.assertFalse(GridDisruption.stall(p, h.getLevel(), h.absolutePos(new BlockPos(5, 2, 5)), 20), "Plain blocks cannot be shut down");
        h.runAfterDelay(22, () -> {
            h.assertFalse(GridDisruption.stalled(h.getLevel(), cell.getBlockPos()), "It wakes on its own");
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "grid.redstone")
    public static void redstoneStillDiesInsideAPulse(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.ELECTROKINESIS_ID, 5);
        BlockPos source = h.absolutePos(new BlockPos(5, 2, 3));
        h.getLevel().setBlock(source, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        GridDisruption.emp(p, h.getLevel(), p.blockPosition(), 8, 20);
        h.assertTrue(h.getLevel().getBlockState(source).getSignal(h.getLevel(), source, Direction.UP) == 0, "Redstone gives no signal in a pulse");
        h.runAfterDelay(25, () -> {
            h.assertTrue(h.getLevel().getBlockState(source).getSignal(h.getLevel(), source, Direction.UP) == 15, "and recovers after");
            h.succeed();
        });
    }
}
