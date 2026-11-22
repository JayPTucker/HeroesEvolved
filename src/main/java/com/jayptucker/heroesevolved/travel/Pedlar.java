package com.jayptucker.heroesevolved.travel;

import com.jayptucker.heroesevolved.company.CompanyOps;
import com.jayptucker.heroesevolved.radio.EmergencyBand;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.Vec3;

/** A pedlar: a wandering merchant, named for themselves, trading what villagers don't (Helixite, radios, Company gear). */
public final class Pedlar extends Party {
    private static final String[] NAMES = {"Ines", "Corwin", "Mabel", "Teodor", "Sabine", "Rook", "Elspeth", "Dario", "Wynn", "Odile"};
    WanderingTrader trader;
    String name;

    private Pedlar(ServerLevel level, Vec3 start, BlockPos bell) { super(level, start, bell); }

    @Override public Kind kind() { return Kind.PEDLAR; }
    public WanderingTrader trader() { return trader; }

    static Pedlar start(ServerLevel level, Vec3 at, BlockPos bell) {
        Pedlar p = new Pedlar(level, at, bell);
        WanderingTrader t = EntityType.WANDERING_TRADER.create(level);
        if (t == null) return null;
        p.name = NAMES[level.random.nextInt(NAMES.length)] + " the Pedlar";
        t.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360, 0);
        t.setCustomName(Component.literal(p.name));
        t.setDespawnDelay(20 * 60 * 60);
        stock(t);
        if (Travellers.add(level, t) == null) return null;
        p.trader = t;
        p.members.add(t);
        return p;
    }

    /** What they trade: their own stock only. */
    static void stock(WanderingTrader t) {
        MerchantOffers offers = t.getOffers();
        offers.clear();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 8), new ItemStack(ModItems.HELIXITE_SHARD.get()), 4, 2, 0.05F));
        ItemStack radio = EmergencyBand.radio();
        if (!radio.isEmpty()) offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 6), radio, 2, 2, 0.05F));
        ItemStack tasers = PowerWeapons.ammo(PowerWeapons.TASER_CARTRIDGE, 6);
        if (!tasers.isEmpty()) offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 10), tasers, 3, 2, 0.05F));
        ItemStack rounds = PowerWeapons.ammo(PowerWeapons.DAMPENER_ROUND, 4);
        if (!rounds.isEmpty()) offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 12), rounds, 3, 2, 0.05F));
        offers.add(new MerchantOffer(new ItemCost(ModItems.HELIXITE_METEORITE.get(), 1), new ItemStack(Items.EMERALD, 10), 4, 3, 0.05F));
        offers.add(new MerchantOffer(new ItemCost(ModItems.HELIXITE_SHARD.get(), 4), new ItemStack(Items.EMERALD, 9), 4, 3, 0.05F));
    }

    @Override String intro() { return name + " is on the road with a pack of wares, heading for " + village + "."; }

    @Override void walk() { if (trader.isAlive()) trader.setWanderTarget(BlockPos.containing(slot(trader, 0))); }

    @Override void walkNearBell() {
        if (!trader.isAlive()) return;
        Vec3 near = Vec3.atBottomCenterOf(bell).add(level.random.nextInt(17) - 8, 0, level.random.nextInt(17) - 8);
        trader.setWanderTarget(BlockPos.containing(CompanyOps.ground(level, near.x, near.z, bell.getY())));
    }

    @Override void leave() {
        stage(Stage.LEAVING);
        Vec3 away = Vec3.atBottomCenterOf(bell).add(heading.scale(40));
        if (trader.isAlive()) trader.setWanderTarget(BlockPos.containing(CompanyOps.ground(level, away.x, away.z, bell.getY())));
    }
}
