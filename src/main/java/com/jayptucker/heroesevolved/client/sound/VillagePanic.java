package com.jayptucker.heroesevolved.client.sound;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * While a villager in meltdown is near you, the villagers running from him scream as Odessa's crowd did: the same
 * recorded voices, each riding one runner ({@link PanicSounds#village}).
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class VillagePanic {
    /** A meltdown this close is heard. */
    private static final double HEARD = 48;

    private VillagePanic() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        Vec3 nearest = null;
        double best = HEARD * HEARD;
        for (Vec3 at : SetPieceClient.where(Piece.VILLAGE_MELTDOWN)) {
            double d = at.distanceToSqr(mc.player.position());
            if (d < best) { best = d; nearest = at; }
        }
        if (nearest != null) PanicSounds.village(mc, nearest);
    }
}
