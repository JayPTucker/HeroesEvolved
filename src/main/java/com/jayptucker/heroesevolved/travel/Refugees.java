package com.jayptucker.heroesevolved.travel;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.company.Pursuit;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.gifted.Renegades;
import com.jayptucker.heroesevolved.mimicry.GiftedVillagers;
import com.jayptucker.heroesevolved.radio.BandChatter;
import com.jayptucker.heroesevolved.village.Growth;
import com.jayptucker.heroesevolved.village.Shouts;
import com.jayptucker.heroesevolved.village.VillageMemory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Refugees: a family (two or three villagers, one with a gift) running from the Company to a village. Meet them and they
 * ask for help; soon two agents come up the road behind them ({@link Pursuit}). Walk them in and they settle there, the
 * gifted one its guardian; if the agents reach them, they are taken.
 */
public final class Refugees extends Party {
    /** The agents come up the road this long after a player first meets them. */
    public static final int PURSUIT_AFTER = 200;
    final List<Villager> family = new ArrayList<>();
    ResourceLocation gift;
    ServerPlayer met;
    int pursueAt = -1;
    Pursuit pursuit;
    boolean settled;

    private Refugees(ServerLevel level, Vec3 start, BlockPos bell) { super(level, start, bell); }

    @Override public Party.Kind kind() { return Party.Kind.REFUGEES; }
    public List<Villager> family() { return List.copyOf(family); }
    public Pursuit pursuit() { return pursuit; }
    public boolean settled() { return settled; }

    static Refugees start(ServerLevel level, Vec3 at, BlockPos bell) {
        Refugees r = new Refugees(level, at, bell);
        int n = 2 + level.random.nextInt(2);
        for (int i = 0; i < n; i++) {
            Villager v = EntityType.VILLAGER.create(level);
            if (v == null) return null;
            v.moveTo(at.x + i, at.y, at.z, 0, 0);
            v.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
            if (i == n - 1) v.setBaby(true);
            v = Travellers.add(level, v);
            if (v == null) return null;
            r.family.add(v);
            r.members.add(v);
        }
        do r.gift = GiftedMobs.randomPower(level.random);
        while (r.gift.equals(ModAbilities.RADIATION_ID));
        GiftedVillagers.give(r.family.getFirst(), r.gift);
        return r;
    }

    @Override String intro() { return "A family is running down the road toward " + village + ", looking back over their shoulders."; }

    @Override void met(ServerPlayer p) {
        if (met != null) return;
        met = p;
        pursueAt = age + PURSUIT_AFTER;
        Mob lead = lead();
        if (lead != null) Shouts.say(level, lead, "Please, they're right behind us!", 100);
    }

    /** Hurried along: the walk is quicker for being chased. */
    @Override void walk() {
        for (Mob m : members) if (m.isAlive() && !m.isRemoved()) goTo(m, slot(m, 0), 0.75F);
    }

    @Override void business() {
        if (pursueAt >= 0 && age >= pursueAt && pursuit == null && met != null && stage == Stage.ROAD) {
            pursueAt = -1;
            pursuit = Pursuit.start(level, met, family, center.subtract(heading.scale(30)));
        }
    }

    /** They made it: they belong to the village now, and the one with a gift guards it. */
    @Override void arrive() {
        settled = true;
        for (Villager v : family) {
            if (!v.isAlive() || v.isRemoved()) continue;
            Travellers.release(v, false);
            if (GiftedVillagers.gift(v) != null) Renegades.guard(v, gift, bell);
        }
        if (pursuit != null) pursuit.reached();
        Growth.sheltered(level, bell);
        for (ServerPlayer p : near(48)) {
            VillageMemory.thanked(level, bell, p, 2);
            p.sendSystemMessage(Component.literal("The family reaches " + village + ". They're safe.").withStyle(ChatFormatting.GREEN));
        }
        BandChatter.news(level, bell, village + " took in a family off the road. Nobody's saying who from.", 24_000);
        over();
    }

    @Override boolean lost() {
        for (Villager v : family) if (v.isAlive() && !v.isRemoved()) return false;
        return true;
    }

    @Override void gone() {
        if (pursuit != null && !pursuit.over()) pursuit.cancel();
        super.gone();
    }

    @Override void fell() {
        if (settled) return;
        for (ServerPlayer p : near(64)) p.sendSystemMessage(Component.literal("The Company has them all.").withStyle(ChatFormatting.GRAY));
        BandChatter.news(level, bell, "They say the Company caught a family on the road to " + village + ".", 24_000);
    }
}
