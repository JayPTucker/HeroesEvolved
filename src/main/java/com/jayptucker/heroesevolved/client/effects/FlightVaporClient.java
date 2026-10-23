package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.flight.FlightVisualGeometry;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.network.ClientFlightVisualState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleGroup;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static com.jayptucker.heroesevolved.client.effects.AbilityEffectRenderer.*;

/** Short, connected wingtip/boot vortices. No per-particle network traffic. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class FlightVaporClient {
    private record Sample(Vec3 left, Vec3 right, Vec3 position) {}
    private static final Map<UUID, Sample> PREVIOUS = new HashMap<>();
    private static ClientLevel world;
    private static final ParticleGroup TRAILS = new ParticleGroup(512);
    private FlightVaporClient() {}

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            PREVIOUS.clear(); world = null; ClientFlightVisualState.clear(); AbilityEffectRenderer.clear();
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (world != mc.level) { PREVIOUS.clear(); world = mc.level; }
        if (world == null || mc.isPaused()) return;
        PREVIOUS.keySet().removeIf(id -> world.getPlayerByUUID(id) == null);
        for (var player : world.players()) {
            boolean cyclone = ClientFlightVisualState.isCycloneActive(player.getId());
            boolean active = ClientFlightVisualState.isFlightActive(player.getId()) || cyclone;
            if (!active || player.onGround() || !player.isAlive()
                    || mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(player.position()) > 128 * 128) {
                PREVIOUS.remove(player.getUUID()); continue;
            }
            int interval = HeroesEvolvedConfig.COMMON.flightTrailIntervalTicks.get();
            if (world.getGameTime() % interval != 0) continue;
            Vec3 axis = FlightVisualGeometry.direction(player, cyclone, 1);
            // Yaw supplies a stable lateral axis even during a vertical climb.
            double yaw = Math.toRadians(FlightVisualGeometry.yaw(player, cyclone, 1));
            Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
            Vec3 feet = FlightVisualGeometry.feet(player.position(), axis, true);
            Sample next = new Sample(feet.subtract(right.scale(.18)), feet.add(right.scale(.18)), player.position());
            Sample previous = PREVIOUS.put(player.getUUID(), next);
            if (previous == null || previous.position.distanceToSqr(next.position) > 144) continue;
            double speed = previous.position.distanceTo(next.position) / interval;
            if (speed < .035) continue;
            boolean detail = mc.options.particles().get() == ParticleStatus.ALL;
            boolean minimal = mc.options.particles().get() == ParticleStatus.MINIMAL;
            float width = (float) Math.clamp(.025 + speed * .025, .025, .085);
            vapor(world, previous.left, next.left, width, minimal ? 100 : 160);
            vapor(world, previous.right, next.right, width, minimal ? 100 : 160);
            if (detail) {
                vapor(world, previous.left, next.left, width * 2.8F, 9);
                vapor(world, previous.right, next.right, width * 2.8F, 9);
                if (world.getGameTime() % 4 == 0) {
                    Vec3 center = FlightVisualGeometry.bodyCenter(player.position());
                    for (int side : new int[]{-1, 1})
                        add(new PowerParticle(world, PowerParticle.Shape.ARC, center, axis,
                                .48F, .85F, 9, 0, 0xD8E8EC, .2F)
                                .arc(side * 1.4F, side * .08F, 1.2F, .025F));
                }
            }
        }
    }
    private static void vapor(ClientLevel level, Vec3 from, Vec3 to, float width, int ticks) {
        PowerParticle vapor = new PowerParticle(level, PowerParticle.Shape.STREAK, from, to,
                width, width * 2.2F, ticks, 0, 0xE5F1F2, .32F);
        if (ticks > 20) vapor.lingering(TRAILS);
        add(vapor);
    }
}
