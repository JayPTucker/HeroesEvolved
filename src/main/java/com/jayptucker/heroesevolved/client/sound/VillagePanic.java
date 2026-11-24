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
 * While an uncontrolled gift is near you, villagers escaping or heading for shelter scream as Odessa's crowd did: the same
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
        for (Vec3 at : SetPieceClient.where(Piece.GIFT_TURN)) {
            double d = at.distanceToSqr(mc.player.position());
            if (d < best) { best = d; nearest = at; }
        }
        for (var storm : com.jayptucker.heroesevolved.client.ClientOverloads.views()) {
            if (storm.ended() || storm.dying()) continue;
            double d = storm.eye.distanceToSqr(mc.player.position());
            double heard = HEARD + storm.reach();
            if (d < heard * heard && (nearest == null || d < best)) { best = d; nearest = storm.eye; }
        }
        for (var flare : com.jayptucker.heroesevolved.client.ClientDistress.all()) {
            if (flare.kind() != com.jayptucker.heroesevolved.radio.EmergencyBand.Trouble.RENEGADE.ordinal()) continue;
            double d = flare.at().distanceToSqr(mc.player.position());
            double heard = HEARD + com.jayptucker.heroesevolved.village.Villages.RADIUS;
            if (d < heard * heard && (nearest == null || d < best)) { best = d; nearest = flare.at(); }
        }
        if (nearest != null) PanicSounds.village(mc, mc.player.position());
    }
}
