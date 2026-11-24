package com.jayptucker.heroesevolved.guidance;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.network.GuidanceStatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class GuidanceService {
    private GuidanceService() {}

    /** Requests can only change the sending player's help preferences. Unknown operations are ignored. */
    public static void apply(ServerPlayer player, int operation, int hintId) {
        GuidanceData data = player.getData(ModDataAttachments.GUIDANCE);
        GuidanceData.Hint hint = GuidanceData.Hint.byId(hintId);
        GuidanceData next = switch (operation) {
            case 0 -> hint == null ? data : data.mark(hint);
            case 1 -> data.enabled(false);
            case 2 -> data.enabled(true);
            default -> data;
        };
        if (!data.equals(next)) {
            player.setData(ModDataAttachments.GUIDANCE, next);
            sync(player);
        }
    }

    public static void sync(ServerPlayer player) {
        GuidanceData data = player.getData(ModDataAttachments.GUIDANCE);
        PacketDistributor.sendToPlayer(player, new GuidanceStatePayload(data.enabled(), data.seen()));
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer player) sync(player);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer player) sync(player);
    }
}
