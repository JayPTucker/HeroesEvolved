package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient;
import com.jayptucker.heroesevolved.network.NuclearCloudPayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Critical Mass, drawn as the vision's explosion over Odessa in small: the flash, the fireball
 * rolling up into a mushroom cloud, the condensation dome and the ring of dust (see {@code MiniBlast}). The
 * server repeats its cloud every couple of seconds for anyone arriving late; each one is started once, at
 * the age the server says it has reached.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class NuclearCloudClient {
    public static final int LIFETIME = 600;
    private static final Set<UUID> STARTED = new HashSet<>();
    private static ClientLevel world;

    private NuclearCloudClient() {}

    public static void update(NuclearCloudPayload packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (world != level) { STARTED.clear(); world = level; }
        if (level == null || !level.dimension().location().equals(packet.dimension())
                || !Double.isFinite(packet.center().lengthSqr()) || packet.age() < 0 || packet.age() >= LIFETIME) return;
        if (!STARTED.add(packet.id())) return;
        if (STARTED.size() > 64) STARTED.clear();
        int radius = Math.clamp(packet.radius(), 1, 64);
        SetPieceClient.startedAt(Piece.EXPLODING_MAN, packet.center(), packet.center(), radius, -1, packet.id().hashCode(),
                level.getGameTime() - packet.age());
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { STARTED.clear(); world = null; }
}
