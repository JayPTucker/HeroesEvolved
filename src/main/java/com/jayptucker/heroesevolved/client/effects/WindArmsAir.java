package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.mixin.ParticleEngineAccess;
import com.jayptucker.heroesevolved.mixin.ParticleWindAccess;
import com.jayptucker.heroesevolved.network.ClientArmPoseState;
import com.jayptucker.heroesevolved.speed.SpeedWindArms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * Wind Arms blowing the air clear. Particles live only on each client, so every client blows them itself: for each
 * player whose arms are spinning, smoke, dust, spores and every other particle in their funnels (the same shape the
 * server pushes with, {@link SpeedWindArms#inFunnel}) is pushed along the look and burns out faster, so a smoke bomb's
 * cloud streams away and thins. Pointed at the ground, the downdraft throws particles out in a ring round the lift.
 * Runs before particles tick, so the push moves them the same tick.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class WindArmsAir {
    /** Fastest a particle is blown, in blocks a tick: fast enough to clear a cloud in a second, not so fast it blinks out. */
    private static final double MAX_SPEED = 1.1;

    private record Blower(Vec3 eye, Vec3 look, boolean down, Vec3 ground) {}

    private WindArmsAir() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused() || mc.player == null) return;
        List<Blower> blowers = new ArrayList<>(2);
        for (Player p : mc.level.players()) {
            if (ClientArmPoseState.get(p.getId()) != ArmPose.WIND_ARMS || p.distanceToSqr(mc.player) > 128 * 128) continue;
            Vec3 look = p.getLookAngle();
            boolean down = look.y < -.7;
            Vec3 ground = down ? p.position().subtract(0, Math.min(SpeedWindArms.heightAboveGround(p), SpeedWindArms.LIFT + 2), 0) : null;
            blowers.add(new Blower(p.getEyePosition(), look, down, ground));
        }
        if (blowers.isEmpty()) return;
        for (Queue<Particle> queue : ((ParticleEngineAccess) mc.particleEngine).heroesEvolved$particles().values())
            for (Particle particle : queue)
                for (Blower b : blowers) if (blow(particle, b)) break;
    }

    private static boolean blow(Particle particle, Blower b) {
        Vec3 at = particle.getBoundingBox().getCenter();
        Vec3 push;
        if (b.down) {
            // The downdraft: out from under the lift, along the ground, and a little up.
            Vec3 out = at.subtract(b.ground);
            double flat = Math.sqrt(out.x * out.x + out.z * out.z);
            if (flat > 6 || out.y < -1 || out.y > b.eye.y - b.ground.y + .5) return false;
            Vec3 dir = flat < 1e-3 ? new Vec3(1, 0, 0) : new Vec3(out.x / flat, 0, out.z / flat);
            push = dir.scale(.12 + .1 * (1 - flat / 6)).add(0, .02, 0);
        } else {
            double along = SpeedWindArms.inFunnel(b.eye, b.look, at, .5);
            if (along < 0) return false;
            double k = 1 - along / SpeedWindArms.REACH;
            // Along the wind, and torn outward from its axis with a little gusting, so a ball of smoke streams out
            // into a plume instead of sliding away whole.
            Vec3 off = at.subtract(b.eye).subtract(b.look.scale(along));
            Vec3 out = off.lengthSqr() < 1e-4 ? Vec3.ZERO : off.normalize();
            push = b.look.scale(.1 + .16 * k).add(out.scale(.035 + .03 * k))
                    .add(gust(particle, 0), gust(particle, 1) * .6, gust(particle, 2));
        }
        ParticleWindAccess wind = (ParticleWindAccess) particle;
        Vec3 v = new Vec3(wind.heroesEvolved$xd(), wind.heroesEvolved$yd(), wind.heroesEvolved$zd()).add(push);
        if (v.lengthSqr() > MAX_SPEED * MAX_SPEED) v = v.normalize().scale(MAX_SPEED);
        particle.setParticleSpeed(v.x, v.y, v.z);
        // Torn apart by the wind: a long-lived puff (campfire smoke lasts fifteen seconds) has at most three seconds
        // left once the wind has it, and it ages twice as fast while it stays in a funnel. Three seconds, not less,
        // because smoke fades over its last three seconds; any shorter and it would vanish without fading.
        int age = wind.heroesEvolved$age(), lifetime = particle.getLifetime();
        if (lifetime > age + FADE) particle.setLifetime(age + FADE);
        else if (lifetime > age + 1) particle.setLifetime(lifetime - 1);
        return true;
    }

    /** Remaining life a particle keeps once the wind takes it, in ticks. */
    private static final int FADE = 60;

    /** A small gust per particle and axis that changes every few ticks: turbulence without a random source. */
    private static double gust(Particle particle, int axis) {
        long t = Minecraft.getInstance().level.getGameTime() / 3;
        int h = (int) (System.identityHashCode(particle) * 31L + axis * 7919L + t * 104729L);
        h ^= h >>> 15; h *= 0x2C1B3C6D; h ^= h >>> 12;
        return ((h & 0xFFFF) / 65535.0 - .5) * .07;
    }
}
