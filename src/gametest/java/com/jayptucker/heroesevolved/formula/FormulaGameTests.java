package com.jayptucker.heroesevolved.formula;

import com.jayptucker.heroesevolved.PowerOwnershipTestSupport;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.data.PowerOwnershipSavedData;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.intro.PowerIntroService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class FormulaGameTests {
    private static ServerPlayer player(GameTestHelper h, String name) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), name)) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
        };
        try {
            Connection connection = p.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true); grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
        return p;
    }

    /** A player allowed to harm others, standing in for a PvP-enabled server. */
    private static ServerPlayer hunter(GameTestHelper h) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "hunter")) {
            @Override public boolean canHarmPlayer(net.minecraft.world.entity.player.Player other) { return true; }
        };
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
        return p;
    }

    private static ServerPlayer regenerator(GameTestHelper h) {
        ServerPlayer p = player(h, "claire");
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.REGENERATION_ID).unlock(ModAbilities.REGENERATION_ID));
        PlayerProgressionService.setLevel(p, 3);
        return p;
    }

    private static ItemStack vial(boolean donated) {
        ItemStack stack = new ItemStack(ModItems.FORMULA_VIAL.get());
        stack.set(ModItems.BLOOD_PURITY.get(), new BloodPurity(donated, 1, "test"));
        return stack;
    }

    @GameTest(template = "rewind_empty", batch = "formula.draw")
    public static void bloodIsDonatedWithConsentOrForcedOnlyFromTheHelpless(GameTestHelper h) {
        ServerPlayer donor = regenerator(h), holder = player(h, "pinehearst");
        ItemStack syringe = new ItemStack(ModItems.EMPTY_SYRINGE.get(), 4);

        donor.setShiftKeyDown(true);
        h.assertTrue(FormulaService.draw(holder, donor, syringe) == FormulaService.Draw.DONATED, "Crouching is consent");
        h.assertTrue(syringe.getCount() == 3, "A draw uses one syringe");
        ItemStack sample = holder.getInventory().items.stream().filter(s -> s.is(ModItems.BLOOD_SAMPLE.get())).findFirst().orElseThrow();
        h.assertTrue(sample.get(ModItems.BLOOD_PURITY.get()).donated(), "Donated blood is marked donated");

        donor.setShiftKeyDown(false);
        donor.setHealth(donor.getMaxHealth());
        // The game-test server always has PvP off, so an ordinary attacker is refused.
        h.assertTrue(FormulaService.draw(holder, donor, syringe) == FormulaService.Draw.REFUSED, "Forced draws respect PvP settings");
        holder = hunter(h);
        {
            var healthy = FormulaService.draw(holder, donor, syringe);
            h.assertTrue(healthy == FormulaService.Draw.NOT_HELPLESS, "A healthy owner fights off the needle: " + healthy);
            donor.setHealth(4);
            h.assertTrue(FormulaService.draw(holder, donor, syringe) == FormulaService.Draw.FORCED, "A badly hurt owner can be drained");
            h.assertTrue(donor.hasEffect(ModItems.DRAINED), "A forced draw leaves the owner drained");
            h.assertTrue(FormulaService.draw(holder, donor, syringe) == FormulaService.Draw.PROTECTED, "Each owner is protected after a forced draw");
        }

        ServerPlayer ordinary = player(h, "ordinary");
        ordinary.setShiftKeyDown(true);
        h.assertTrue(FormulaService.draw(holder, ordinary, syringe) == FormulaService.Draw.NOT_REGENERATION, "Only Regeneration blood works");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "formula.lab")
    public static void centrifugeAndStationTurnBloodIntoTheFormula(GameTestHelper h) {
        BlockPos centrifugePos = h.absolutePos(new BlockPos(1, 2, 1)), stationPos = h.absolutePos(new BlockPos(4, 2, 1));
        h.getLevel().setBlock(centrifugePos, ModBlocks.CENTRIFUGE.get().defaultBlockState(), 3);
        h.getLevel().setBlock(stationPos, ModBlocks.SYNTHESIS_STATION.get().defaultBlockState(), 3);
        var centrifuge = (CentrifugeBlockEntity) h.getLevel().getBlockEntity(centrifugePos);
        var station = (SynthesisStationBlockEntity) h.getLevel().getBlockEntity(stationPos);

        ItemStack sample = new ItemStack(ModItems.BLOOD_SAMPLE.get());
        sample.set(ModItems.BLOOD_PURITY.get(), new BloodPurity(false, 4, "claire"));
        centrifuge.setItem(CentrifugeBlockEntity.INPUT, sample);
        centrifuge.setItem(CentrifugeBlockEntity.FUEL, new ItemStack(Items.REDSTONE, 2));
        for (int i = 0; i <= CentrifugeBlockEntity.SPIN_TICKS; i++)
            CentrifugeBlockEntity.serverTick(h.getLevel(), centrifugePos, h.getLevel().getBlockState(centrifugePos), centrifuge);
        ItemStack catalyst = centrifuge.getItem(CentrifugeBlockEntity.OUTPUT);
        h.assertTrue(catalyst.is(ModItems.ISOLATED_CATALYST.get()) && centrifuge.getItem(CentrifugeBlockEntity.INPUT).isEmpty(),
                "The Centrifuge must spin blood into catalyst");
        h.assertTrue(centrifuge.getItem(CentrifugeBlockEntity.FUEL).getCount() == 1, "One spin burns one redstone");

        for (int slot = 0; slot < 3; slot++)
            station.setItem(slot, PotionContents.createItemStack(Items.POTION, Potions.WATER));
        station.setItem(SynthesisStationBlockEntity.FUEL, new ItemStack(Items.GLOWSTONE_DUST));
        brew(h, station, stationPos, new ItemStack(Items.BONE_MEAL));
        h.assertTrue(station.getItem(0).is(ModItems.BUFFERED_SALINE.get()) && station.getItem(2).is(ModItems.BUFFERED_SALINE.get()),
                "Water and bone meal make Buffered Saline");
        brew(h, station, stationPos, new ItemStack(ModItems.HELIXITE_SHARD.get()));
        h.assertTrue(station.getItem(1).is(ModItems.UNSTABLE_COMPOUND.get()), "Saline and Helixite make Unstable Compound");
        brew(h, station, stationPos, catalyst.copy());
        ItemStack formula = station.getItem(0);
        h.assertTrue(formula.is(ModItems.FORMULA_VIAL.get()), "Compound and catalyst make the Formula");
        BloodPurity purity = formula.get(ModItems.BLOOD_PURITY.get());
        h.assertTrue(purity != null && !purity.donated() && purity.donorLevel() == 4, "The Formula remembers whose blood it carries");
        h.assertTrue(station.getItem(SynthesisStationBlockEntity.INGREDIENT).isEmpty(), "Each stage uses its ingredient");
        var server = h.getLevel().getServer();
        for (String recipe : new String[] {"centrifuge", "synthesis_station", "empty_syringe"})
            h.assertTrue(server.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("heroesevolved", recipe)).isPresent(),
                    "Missing crafting recipe: " + recipe);
        for (String advancement : new String[] {"save_the_cheerleader", "the_formula"})
            h.assertTrue(server.getAdvancements().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("heroesevolved", advancement)) != null,
                    "Missing advancement: " + advancement);
        h.succeed();
    }

    private static void brew(GameTestHelper h, SynthesisStationBlockEntity station, BlockPos pos, ItemStack ingredient) {
        station.setItem(SynthesisStationBlockEntity.INGREDIENT, ingredient);
        for (int i = 0; i <= SynthesisStationBlockEntity.BREW_TICKS + 1; i++)
            SynthesisStationBlockEntity.serverTick(h.getLevel(), pos, h.getLevel().getBlockState(pos), station);
    }

    @GameTest(template = "rewind_empty", batch = "formula.inject")
    public static void injectionAwakensOnlyUnclaimedPowersAndRejectsThePowered(GameTestHelper h) {
        ServerPlayer powered = regenerator(h), recipient = player(h, "volunteer"), unlucky = player(h, "unlucky");
        ServerPlayer placeholder = player(h, "placeholder");
        try {
            ItemStack first = vial(true);
            h.assertTrue(FormulaService.inject(powered, powered, first, 0) == FormulaService.Injection.ALREADY_POWERED,
                    "An awakened body rejects the Formula");
            h.assertTrue(first.isEmpty(), "A rejected dose is still spent");

            ItemStack failing = vial(false);
            h.assertTrue(FormulaService.inject(unlucky, unlucky, failing, 0.99) == FormulaService.Injection.REJECTED,
                    "A bad roll rejects the dose");
            h.assertTrue(!PlayerAbilityService.getData(unlucky).hasAssignedPower(), "Rejection grants nothing");

            // Reserve every role but Telepathy, so the dose cannot pick a destructive awakening
            // (Radiation's meltdown or Quake's blast) that would reach neighbouring tests.
            var ownership = PowerOwnershipSavedData.get(h.getLevel().getServer());
            for (var power : AbilityRegistry.ABILITIES.keySet())
                if (!power.equals(ModAbilities.TELEPATHY_ID)) ownership.claim(power, placeholder.getUUID());
            h.assertTrue(FormulaService.unclaimed(recipient).equals(java.util.List.of(ModAbilities.TELEPATHY_ID)),
                    "Only unclaimed powers are offered");

            ItemStack dose = vial(true);
            FormulaService.Injection result = FormulaService.inject(recipient, recipient, dose, 0);
            h.assertTrue(result == FormulaService.Injection.AWAKENED, "A good roll awakens an unclaimed power: " + result);
            var power = PlayerAbilityService.getData(recipient).assignedPower().orElseThrow();
            h.assertTrue(power.getKey().equals(ModAbilities.TELEPATHY_ID) && power.getValue().isUnlocked()
                            && recipient.getData(ModDataAttachments.FORMULA_BORN),
                    "The unclaimed power is awakened at once and remembered as Formula-born");

            ItemStack spare = vial(true);
            h.assertTrue(FormulaService.inject(unlucky, unlucky, spare, 0) == FormulaService.Injection.NOTHING_LEFT && !spare.isEmpty(),
                    "With every role claimed the vial is kept");
            ServerPlayer stranger = player(h, "stranger");
            h.assertTrue(FormulaService.inject(stranger, recipient, vial(true), 0) == FormulaService.Injection.NOT_CONSENTING,
                    "Injecting someone else needs their consent");
            h.succeed();
        } finally {
            PowerIntroService.cancel(recipient);
            PowerOwnershipTestSupport.release(recipient);
            PowerOwnershipTestSupport.release(powered);
            PowerOwnershipTestSupport.release(placeholder);
        }
    }

    @GameTest(template = "rewind_empty", batch = "formula.dormant")
    public static void theFormulaWakesADormantAssignmentInstead(GameTestHelper h) {
        ServerPlayer sleeper = player(h, "sleeper");
        sleeper.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.FLIGHT_ID));
        try {
            h.assertTrue(FormulaService.inject(sleeper, sleeper, vial(true), 0) == FormulaService.Injection.AWAKENED,
                    "A dormant assignment is awakened");
            var power = PlayerAbilityService.getData(sleeper).assignedPower().orElseThrow();
            h.assertTrue(power.getKey().equals(ModAbilities.FLIGHT_ID) && power.getValue().isUnlocked(),
                    "The Formula wakes the power already waiting, not a new one");
            h.succeed();
        } finally {
            PowerIntroService.cancel(sleeper);
            PowerOwnershipTestSupport.release(sleeper);
        }
    }
}
