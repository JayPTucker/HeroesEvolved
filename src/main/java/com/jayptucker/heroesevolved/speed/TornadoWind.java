package com.jayptucker.heroesevolved.speed;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.UUID;

/**
 * The wind of a tornado standing on the ground: Superspeed's Cyclone once it rises, and Flight's Tempest. Shaped
 * by {@link TornadoShape}, so what it carries rides the wall that is drawn.
 * <ul>
 *   <li><b>Inflow.</b> Creatures and loose items out to the pull range are drawn in along the ground, swirling,
 *   faster the closer they get.</li>
 *   <li><b>The wall.</b> What reaches the funnel is whirled round its wall and carried up it, hurt every half
 *   second, with no fall damage while it is carried. Shots are thrown out of it.</li>
 *   <li><b>No air.</b> If the tornado suffocates, anything that breathes loses its breath inside it (a player
 *   sees their bubbles go), then drowns in the air every half second until it is let go.</li>
 * </ul>
 */
public final class TornadoWind {
    private TornadoWind() {}

    /**
     * One tick of wind round {@code center} (on the ground), {@code built} ticks after the tornado began to rise.
     * Everything it gets hold of is added to {@code caught}.
     */
    public static void blow(ServerPlayer owner, ServerLevel level, Vec3 center, float built, float ring, double pull,
                            float damage, boolean suffocate, Set<UUID> caught, long now) {
        float height = TornadoShape.height(built), lift = TornadoShape.lift(built);
        AABB reach = new AABB(center, center).inflate(pull, 0, pull).expandTowards(0, height + 4, 0).move(0, -4, 0);
        for (Entity e : level.getEntities(owner, reach, e -> target(owner, e))) {
            Vec3 radial = e.position().subtract(center).multiply(1, 0, 1);
            double distance = radial.length();
            if (distance > pull) continue;
            float h = (float) (e.getY() - center.y), up = Math.max(0, h);
            float wall = TornadoShape.radius(up, height, ring);
            Vec3 out = distance < 1.0E-3 ? new Vec3(1, 0, 0) : radial.scale(1 / distance);
            Vec3 around = new Vec3(-out.z, 0, out.x);
            // It reaches a little past its wall, more at the ground where it feeds.
            boolean held = distance <= wall + (up < 3 ? 4 : 2.5) && h > -3 && h < height + 2;
            if (e instanceof Projectile shot) {
                if (held && shot.getOwner() != owner && (shot.getOwner() == null || !owner.isAlliedTo(shot.getOwner())))
                    push(shot, out.scale(1.2).add(0, 0.15, 0));
                continue;
            }
            double resist = e instanceof LivingEntity living
                    ? Math.max(0.3, 1 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) * 0.6) : 1;
            if (!held) {
                double k = 1 - distance / pull;
                Vec3 draw = out.scale(-(0.12 + 0.34 * k)).add(around.scale(0.1 + 0.22 * k)).scale(resist);
                Vec3 v = e.getDeltaMovement();
                push(e, new Vec3(v.x * 0.4 + draw.x, v.y + (e.onGround() ? 0.05 * k : 0), v.z * 0.4 + draw.z));
                continue;
            }
            caught.add(e.getUUID());
            double toward = Math.clamp((wall - distance) * 0.12, -0.35, 0.35);
            double rise = h < lift ? 0.3 : 0.08 + 0.04 * Math.sin((now + e.getId()) * 0.3);
            push(e, around.scale(0.85 * resist).add(out.scale(toward)).add(0, rise * resist, 0));
            e.fallDistance = 0;
            if (e instanceof LivingEntity living) {
                if (damage > 0 && now % 10 == 0) SpeedService.hit(owner, living, damage * (living instanceof ServerPlayer ? 0.6F : 1));
                if (suffocate) choke(living, now);
            }
        }
    }

    /** Living things the owner may hurt, loose items, and shots in flight. */
    private static boolean target(ServerPlayer owner, Entity e) {
        if (e == owner || !e.isAlive() || e.isSpectator()) return false;
        if (e instanceof Projectile || e instanceof ItemEntity) return true;
        return e instanceof LivingEntity living && SpeedService.canHit(owner, living);
    }

    /** Breath torn away: air runs out fast (vanilla gives back four a tick out of water), then it drowns in the air. */
    static void choke(LivingEntity living, long now) {
        if (living.canBreatheUnderwater() || living instanceof Player player && player.getAbilities().invulnerable) return;
        int air = living.getAirSupply();
        living.setAirSupply(Math.max(-20, air - 9));
        if (air <= 0 && now % 10 == 5) living.hurt(living.damageSources().drown(), 2);
    }

    private static void push(Entity target, Vec3 velocity) {
        target.setDeltaMovement(velocity);
        target.hurtMarked = true;
        if (target instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
    }
}
