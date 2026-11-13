package com.jayptucker.heroesevolved.mimicry;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.effects.PrivateGlow;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

/**
 * A few villagers are born gifted: a quiet power of their own, never used, never claiming a player's role. Everyone
 * may notice a stray mote drift off them now and then; an Empathic Mimic nearby feels them glow, and can borrow it.
 * The roll is made once per villager and saved on it.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class GiftedVillagers {
    private static final String GIFT = "heroesevolved_gift", ROLLED = "heroesevolved_gift_rolled";

    private GiftedVillagers() {}

    /** The villager's gift, or null. */
    public static ResourceLocation gift(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.contains(GIFT) ? ResourceLocation.tryParse(data.getString(GIFT)) : null;
    }

    /** Gives a villager a gift (the admin command and tests); {@code null} takes it away. */
    public static void give(Villager villager, ResourceLocation power) {
        CompoundTag data = villager.getPersistentData();
        data.putBoolean(ROLLED, true);
        if (power == null) data.remove(GIFT);
        else data.putString(GIFT, power.toString());
    }

    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Villager villager)) return;
        CompoundTag data = villager.getPersistentData();
        if (data.getBoolean(ROLLED)) return;
        data.putBoolean(ROLLED, true);
        if (villager.getRandom().nextDouble() >= HeroesEvolvedConfig.COMMON.giftedVillagerChance.get()) return;
        List<ResourceLocation> powers = AbilityRegistry.ABILITIES.keySet().stream()
                .filter(power -> !power.equals(ModAbilities.MIMICRY_ID)).sorted().toList();
        if (!powers.isEmpty()) data.putString(GIFT, powers.get(villager.getRandom().nextInt(powers.size())).toString());
    }

    /** The tell: a mote now and then for anyone, a glow that only a mimic sees. */
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (event.getServer().overworld().getGameTime() % 60 != 0) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            boolean mimic = MimicryService.isMimic(player);
            for (Villager villager : player.serverLevel().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(24),
                    v -> gift(v) != null)) {
                if (mimic) PrivateGlow.glow(player, villager, 30);
                if (villager.getRandom().nextInt(3) == 0)
                    player.serverLevel().sendParticles(ParticleTypes.END_ROD, villager.getX(), villager.getY() + 1.9, villager.getZ(),
                            1, .2, .1, .2, .005);
            }
        }
    }
}
