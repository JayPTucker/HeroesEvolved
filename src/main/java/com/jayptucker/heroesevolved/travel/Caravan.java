package com.jayptucker.heroesevolved.travel;

import com.jayptucker.heroesevolved.company.CompanyOps;
import com.jayptucker.heroesevolved.gifted.Gift;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.gifted.Rarity;
import com.jayptucker.heroesevolved.radio.BandChatter;
import com.jayptucker.heroesevolved.village.Shouts;
import com.jayptucker.heroesevolved.village.VillageMemory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A caravan: two drovers and two pack llamas on leads, carrying goods between villages. Walk with it a while and
 * renegades may hit it; deliver it and everyone there is paid; lose every drover and its goods lie on the road.
 */
public final class Caravan extends Party {
    /** Walked with this long (within {@link #ESCORT}), it may be hit, with this chance, once. */
    public static final int ESCORT_TICKS = 600;
    public static final double ESCORT = 32, AMBUSH_CHANCE = 0.6;
    final List<Villager> drovers = new ArrayList<>();
    final List<Llama> llamas = new ArrayList<>();
    final List<Mob> ambushers = new ArrayList<>();
    int escorted;
    boolean rolled, delivered;
    Vec3 lastSeen;

    private Caravan(ServerLevel level, Vec3 start, BlockPos bell) { super(level, start, bell); }

    @Override public Party.Kind kind() { return Party.Kind.CARAVAN; }
    public List<Villager> drovers() { return List.copyOf(drovers); }
    public List<Llama> llamas() { return List.copyOf(llamas); }
    public List<Mob> ambushers() { return List.copyOf(ambushers); }
    public boolean delivered() { return delivered; }

    static Caravan start(ServerLevel level, Vec3 at, BlockPos bell) {
        Caravan c = new Caravan(level, at, bell);
        for (int i = 0; i < 2; i++) {
            Vec3 spot = CompanyOps.ground(level, at.x - c.heading.x * i * 3, at.z - c.heading.z * i * 3, at.y);
            Villager v = EntityType.VILLAGER.create(level);
            Llama l = EntityType.LLAMA.create(level);
            if (v == null || l == null) return null;
            v.moveTo(spot.x, spot.y, spot.z, 0, 0);
            v.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
            l.moveTo(spot.x + 1.5, spot.y, spot.z + 1.5, 0, 0);
            l.setChest(true);
            l.setTamed(true);
            if (Travellers.add(level, v) == null || Travellers.add(level, l) == null) return null;
            l.setLeashedTo(v, true);
            c.drovers.add(v);
            c.llamas.add(l);
            c.members.add(v);
            c.members.add(l);
        }
        BandChatter.news(level, bell, "A caravan's on the road to " + c.village + ".", 12_000);
        return c;
    }

    @Override String intro() { return "A caravan is on the road to " + village + ": two drovers and their llamas."; }

    @Override Mob lead() {
        for (Villager v : drovers) if (v.isAlive() && !v.isRemoved()) return v;
        return null;
    }

    @Override boolean lost() {
        for (Villager v : drovers) if (v.isAlive() && !v.isRemoved()) return false;
        return true;
    }

    @Override int stay() { return 600; }

    @Override void walk() {
        int i = 0;
        for (Villager v : drovers) if (v.isAlive() && !v.isRemoved()) goTo(v, slot(v, i++), 0.55F);
        // A llama that slipped its lead and is near its drover is led again.
        for (int k = 0; k < llamas.size(); k++) {
            Llama l = llamas.get(k);
            Villager v = drovers.get(k);
            if (l.isAlive() && !l.isLeashed() && v.isAlive() && l.distanceToSqr(v) < 64) l.setLeashedTo(v, true);
        }
    }

    @Override void business() {
        Mob lead = lead();
        if (lead != null) lastSeen = lead.position();
        if (stage != Stage.ROAD || rolled) return;
        escorted = near(ESCORT).isEmpty() ? Math.max(0, escorted - 2) : escorted + 1;
        if (escorted < ESCORT_TICKS) return;
        rolled = true;
        if (level.random.nextDouble() < AMBUSH_CHANCE) ambush();
    }

    /**
     * Renegades come at it from the road ahead: a gifted one and two pillagers, going for the drovers and their llamas.
     * Only ever when a player is there to answer it.
     */
    public void ambush() {
        rolled = true;
        Vec3 ahead = CompanyOps.ground(level, center.x + heading.x * 20, center.z + heading.z * 20, center.y);
        List<LivingEntity> prey = new ArrayList<>();
        for (Villager v : drovers) if (v.isAlive()) prey.add(v);
        for (Llama l : llamas) if (l.isAlive()) prey.add(l);
        for (int i = 0; i < 3; i++) {
            AbstractIllager m = (i == 0 && level.random.nextBoolean() ? EntityType.VINDICATOR : EntityType.PILLAGER).create(level);
            if (m == null) continue;
            Vec3 at = CompanyOps.ground(level, ahead.x + (i - 1) * 2.5, ahead.z, ahead.y);
            m.moveTo(at.x, at.y, at.z, 0, 0);
            m.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
            m.setPersistenceRequired();
            level.addFreshEntity(m);
            if (i == 0) GiftedMobs.bestow(m, GiftedMobs.randomPower(m.getRandom()), Rarity.roll(m.getRandom(), Rarity.RARE), true,
                    Gift.Origin.PATROL, null, null);
            if (!prey.isEmpty()) m.setTarget(prey.get(i % prey.size()));
            ambushers.add(m);
        }
        Mob lead = lead();
        if (lead != null) Shouts.say(level, lead, "Ambush! Up the road!", 80);
        for (ServerPlayer p : near(48))
            p.sendSystemMessage(Component.literal("Renegades on the road ahead! They're going for the caravan!").withStyle(ChatFormatting.RED));
    }

    /** It made it: everyone with it is paid, and the village thinks well of them. */
    @Override void arrive() {
        delivered = true;
        for (ServerPlayer p : near(ESCORT)) {
            int emeralds = 6 + level.random.nextInt(5);
            give(p, new ItemStack(Items.EMERALD, emeralds));
            give(p, new ItemStack(Items.BREAD, 6));
            give(p, new ItemStack(Items.IRON_INGOT, 2));
            give(p, new ItemStack(Items.LEATHER, 3));
            p.sendSystemMessage(Component.literal("The caravan master pays you " + emeralds + " emeralds, and a share of the goods.").withStyle(ChatFormatting.GREEN));
            VillageMemory.thanked(level, bell, p, 1);
        }
        BandChatter.news(level, bell, "The caravan made it to " + village + ". Fresh bread tonight.", 12_000);
        com.jayptucker.heroesevolved.village.Growth.kindness(level, bell);
    }

    private static void give(ServerPlayer p, ItemStack s) { if (!p.getInventory().add(s)) p.drop(s, false); }

    /** Every drover dead: its goods lie on the road, and word gets round. */
    @Override void fell() {
        Vec3 at = lastSeen != null ? lastSeen : center;
        for (ItemStack s : List.of(new ItemStack(Items.BREAD, 8), new ItemStack(Items.WHEAT, 12), new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.LEATHER, 4)))
            level.addFreshEntity(new ItemEntity(level, at.x, at.y + 0.5, at.z, s));
        for (Llama l : llamas) if (l.isAlive()) Travellers.release(l, false);
        BandChatter.news(level, bell, "The caravan to " + village + " never arrived. Renegades, they say.", 24_000);
        for (ServerPlayer p : near(64)) p.sendSystemMessage(Component.literal("The caravan is lost. Its goods lie on the road.").withStyle(ChatFormatting.GRAY));
    }
}
