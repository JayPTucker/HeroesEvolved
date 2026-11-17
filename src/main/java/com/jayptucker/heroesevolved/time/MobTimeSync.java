package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.MobTimePayload;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Map;
import java.util.HashMap;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class MobTimeSync {
    private static final Map<Mob, Integer> SENT = new HashMap<>();
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for (var level : event.getServer().getAllLevels()) {
            if (level.players().isEmpty()) continue;
            for (var entity : level.getAllEntities()) {
                if (!(entity instanceof Mob mob)) continue;
                int interval = TemporalMobTime.interval(mob);
                int old = SENT.getOrDefault(mob, 1);
                if (old != interval) PacketDistributor.sendToPlayersTrackingEntity(mob, new MobTimePayload(mob.getId(), interval));
                if (interval != 1) SENT.put(mob, interval); else SENT.remove(mob);
            }
        }
        SENT.keySet().removeIf(mob -> mob.isRemoved() || mob.level() instanceof net.minecraft.server.level.ServerLevel level && level.players().isEmpty());
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof Mob mob)
            PacketDistributor.sendToPlayer(player, new MobTimePayload(mob.getId(), TemporalMobTime.interval(mob)));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { SENT.clear(); }
}
