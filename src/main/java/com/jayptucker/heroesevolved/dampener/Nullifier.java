package com.jayptucker.heroesevolved.dampener;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.grid.GridDisruption;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * The Nullifier's battery. Its dampener rounds are made from Forge Energy, {@value DampenerGeometry#FE_PER_ROUND} FE
 * each, and kept in the gun itself: TaCZ's internal reserve ("dummy ammo") is the battery, so a reload pulls rounds out
 * of it, never out of the inventory, and TaCZ's HUD counts them.
 *
 * <p>Ways to charge it:
 * <ul>
 *   <li>Use an Ability Dampener with it (TaCZ's Interact key, O by default): up to six rounds a use.</li>
 *   <li>Sneak and use any other Forge Energy machine or battery with it: it drinks what it can.</li>
 *   <li>Any FE mod's charger (a Mekanism chargepad or energy cube, a Flux point, a Powah energizer...): the gun exposes
 *   Forge Energy as an item, through NeoForge's capability.</li>
 * </ul>
 * An EMP drains it like any other battery a foe carries.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Nullifier {
    /** Energy short of a whole round, kept between charges so nothing is lost. */
    private static final String PARTIAL = "heroesevolved_partial_fe";
    /** The reserve's cap: the battery and one magazine, so unloading a full magazine into a full battery loses nothing. */
    public static final int RESERVE_CAP = DampenerGeometry.BATTERY_ROUNDS + DampenerGeometry.MAGAZINE;

    private Nullifier() {}

    public static boolean is(ItemStack stack) { return !stack.isEmpty() && PowerWeapons.is(stack, PowerWeapons.DAMPENER_GUN); }

    /** Gives a Nullifier its battery if it has none yet (a fresh one from the gun smith table). Safe to call often. */
    public static void prepare(ItemStack stack) {
        if (is(stack) && PowerWeapons.reserve(stack) < 0) PowerWeapons.reserve(stack, 0, RESERVE_CAP);
    }

    /** A loaded Nullifier with a full battery (the creative tab, tests). Empty if TaCZ is missing. */
    public static ItemStack charged() {
        ItemStack gun = PowerWeapons.gun(PowerWeapons.DAMPENER_GUN);
        if (!gun.isEmpty()) set(gun, DampenerGeometry.BATTERY_ROUNDS * DampenerGeometry.FE_PER_ROUND);
        return gun;
    }

    /** Whole rounds in the battery. */
    public static int rounds(ItemStack stack) { return Math.max(0, PowerWeapons.reserve(stack)); }

    /** Forge Energy in the battery, whole rounds and the part-made one. */
    public static int stored(ItemStack stack) { return rounds(stack) * DampenerGeometry.FE_PER_ROUND + partial(stack); }

    public static int capacity(ItemStack stack) {
        return Math.max(DampenerGeometry.BATTERY_ROUNDS, rounds(stack)) * DampenerGeometry.FE_PER_ROUND;
    }

    private static int partial(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : Math.clamp(data.copyTag().getInt(PARTIAL), 0, DampenerGeometry.FE_PER_ROUND - 1);
    }

    /** Sets the battery to {@code fe}: whole rounds into the reserve, the rest kept as the part-made round. */
    static void set(ItemStack stack, int fe) {
        int rounds = fe / DampenerGeometry.FE_PER_ROUND, rest = fe % DampenerGeometry.FE_PER_ROUND;
        PowerWeapons.reserve(stack, rounds, RESERVE_CAP);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> { if (rest > 0) tag.putInt(PARTIAL, rest); else tag.remove(PARTIAL); });
    }

    /** Puts up to {@code fe} into the battery. Returns what it took. */
    public static int receive(ItemStack stack, int fe, boolean simulate) {
        if (!is(stack) || fe <= 0) return 0;
        prepare(stack);
        int room = DampenerGeometry.BATTERY_ROUNDS * DampenerGeometry.FE_PER_ROUND - stored(stack);
        int took = Math.clamp(fe, 0, Math.max(0, room));
        if (took > 0 && !simulate) set(stack, stored(stack) + took);
        return took;
    }

    /** Takes up to {@code fe} out of the battery (an EMP, a mod that empties batteries). Returns what it took. */
    public static int extract(ItemStack stack, int fe, boolean simulate) {
        if (!is(stack) || fe <= 0) return 0;
        int took = Math.min(fe, stored(stack));
        if (took > 0 && !simulate) set(stack, stored(stack) - took);
        return took;
    }

    /** The gun as a Forge Energy item, for chargers and EMPs. Null for any other TaCZ gun. */
    public static @Nullable IEnergyStorage storage(ItemStack stack) {
        if (!is(stack)) return null;
        return new IEnergyStorage() {
            @Override public int receiveEnergy(int fe, boolean simulate) { return receive(stack, Math.min(fe, DampenerGeometry.GUN_MAX_RECEIVE), simulate); }
            @Override public int extractEnergy(int fe, boolean simulate) { return extract(stack, fe, simulate); }
            @Override public int getEnergyStored() { return stored(stack); }
            @Override public int getMaxEnergyStored() { return capacity(stack); }
            @Override public boolean canExtract() { return true; }
            @Override public boolean canReceive() { return true; }
        };
    }

    /** Registers the gun's Forge Energy capability on TaCZ's gun item (it answers only for a Nullifier). */
    public static void capabilities(RegisterCapabilitiesEvent event) {
        Item gun = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("tacz", "modern_kinetic_gun"));
        if (gun == Items.AIR) return;
        event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, context) -> storage(stack), gun);
    }

    // ------------------------------------------------------------------
    // Charging from a machine
    // ------------------------------------------------------------------

    /**
     * Fills the gun from the machine at {@code pos}: an Ability Dampener's battery, or any Forge Energy storage. Draws
     * at most what the battery has room for, and at most what each storage gives in one transfer. Returns the FE moved.
     */
    public static int charge(ServerPlayer player, ItemStack gun, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        prepare(gun);
        int room = receive(gun, Integer.MAX_VALUE, true);
        int moved = 0;
        if (room > 0) {
            List<IEnergyStorage> storages = GridDisruption.storages(level, pos, null, level.getBlockEntity(pos));
            for (IEnergyStorage storage : storages) {
                if (moved >= room || !storage.canExtract()) continue;
                int got = storage.extractEnergy(room - moved, false);
                if (got > 0) moved += receive(gun, got, false);
            }
        }
        report(player, gun, room == 0 ? "full" : moved == 0 ? "empty" : "charged");
        if (moved > 0) {
            Vec3 from = Vec3.atCenterOf(pos), hand = player.getEyePosition().add(player.getLookAngle().scale(0.6)).add(0, -0.4, 0);
            SetPiecePayload.send(level, Piece.DAMPENER_CHARGE, from, hand, moved / (float) DampenerGeometry.FE_PER_ROUND, player);
            level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7F, 0.8F);
            level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.7F, 1.4F);
        }
        return moved;
    }

    private static void report(Player player, ItemStack gun, String how) {
        NumberFormat n = NumberFormat.getIntegerInstance(Locale.ROOT);
        player.displayClientMessage(Component.translatable("item.heroesevolved.nullifier." + how, rounds(gun), DampenerGeometry.BATTERY_ROUNDS,
                n.format(stored(gun))).withStyle(how.equals("empty") ? ChatFormatting.GRAY : ChatFormatting.AQUA), true);
    }

    /**
     * Sneak and use any Forge Energy machine with a Nullifier: drink from it. (Without sneaking, an Ability Dampener
     * handles the use itself; sneaking with an item in hand skips a block's own use, so the projector is covered here.)
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void useOnMachine(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!event.getEntity().isShiftKeyDown() || !is(stack)) return;
        if (level.isClientSide || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (!GridDisruption.isMachine(level, pos) || !level.mayInteract(player, pos)) return;
        charge(player, stack, pos);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
    }

    /** A Nullifier from the gun smith table has no battery yet: give it one as soon as a player carries it. */
    @SubscribeEvent public static void carried(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 10 == 0) {
            for (ItemStack stack : player.getInventory().items) prepare(stack);
            prepare(player.getOffhandItem());
        } else {
            prepare(player.getMainHandItem());
        }
    }
}
