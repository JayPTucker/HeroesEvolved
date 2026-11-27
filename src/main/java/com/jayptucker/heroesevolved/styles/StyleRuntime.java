package com.jayptucker.heroesevolved.styles;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

/**
 * The class perks that run all the time: Offense hits a tenth harder, Defense takes an eighth less, and Support keeps
 * friends near it regenerating. The class's Ultimate itself runs on {@code combat/Ultimates}.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class StyleRuntime {
    private StyleRuntime() {}

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (event.getServer().overworld().getGameTime() % 100 != 0) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) supportPerk(player);
    }

    /** The role of the class this player has chosen for their power: "offense", "defense", "support" or "". */
    public static String role(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower().filter(e -> e.getValue().isUnlocked())
                .flatMap(e -> SkillCatalog.chosenStyle(e.getKey().getPath(), SkillTreeService.build(player, e.getKey())))
                .map(SkillCatalog.Node::role).orElse("");
    }

    /** Support: friends near you regenerate, and so do you while one is near. */
    private static void supportPerk(ServerPlayer player) {
        if (!"support".equals(role(player)) || !PowerRules.canAct(player)) return;
        List<LivingEntity> friends = StyleOps.friends(player, player.position(), 12);
        if (friends.size() < 2) return;
        for (LivingEntity friend : friends) friend.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 110, 0, true, false, true));
    }

    /** Offense hits harder; Defense takes less. */
    @SubscribeEvent(priority = EventPriority.HIGH) public static void incoming(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && attacker != victim && "offense".equals(role(attacker)))
            event.setAmount(event.getAmount() * 1.10F);
        if (victim instanceof ServerPlayer player && "defense".equals(role(player))) event.setAmount(event.getAmount() * 0.88F);
    }
}
