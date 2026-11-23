package com.jayptucker.heroesevolved.travel;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.dampener.Dampening;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.gifted.Gift;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.gifted.Rarity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** A travelling healer: a villager whose gift is Regeneration, mending anyone hurt they pass, on the road and where they stop. */
public final class Healer extends Party {
    /** Mends this often, this far, this much. */
    public static final int EVERY = 60;
    public static final double REACH = 8;
    public static final float MEND = 3;
    Villager healer;

    private Healer(ServerLevel level, Vec3 start, BlockPos bell) { super(level, start, bell); }

    @Override public Party.Kind kind() { return Party.Kind.HEALER; }
    public Villager healer() { return healer; }

    static Healer start(ServerLevel level, Vec3 at, BlockPos bell) {
        Healer h = new Healer(level, at, bell);
        Villager v = EntityType.VILLAGER.create(level);
        if (v == null) return null;
        v.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360, 0);
        v.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        v = Travellers.add(level, v);
        if (v == null) return null;
        GiftedMobs.bestow(v, ModAbilities.REGENERATION_ID, Rarity.UNCOMMON, false, Gift.Origin.GUARDIAN, null, null);
        h.healer = v;
        h.members.add(v);
        return h;
    }

    @Override String intro() {
        return healer.getName().getString() + ", a travelling healer, is passing through on the way to " + village + ".";
    }

    @Override void business() {
        if (age % EVERY != 0 || !healer.isAlive() || Dampening.dampened(healer)) return;
        boolean mended = false;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, healer.getBoundingBox().inflate(REACH),
                e -> e != healer && e.isAlive() && (e instanceof Player || e instanceof Villager || e instanceof IronGolem || e instanceof Animal)
                        && e.getHealth() < e.getMaxHealth() && !(e instanceof Player p && (p.isSpectator() || p.isCreative())))) {
            e.heal(MEND);
            AbilityEffects.send(level, com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.REGEN_MEND, e.position(), e.getEyePosition(), 0.7);
            mended = true;
        }
        if (mended) level.playSound(null, healer.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.9F, 1.3F);
    }
}
