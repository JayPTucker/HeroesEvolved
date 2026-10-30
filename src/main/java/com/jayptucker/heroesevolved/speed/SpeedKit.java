package com.jayptucker.heroesevolved.speed;

import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Superspeed's actions, after Daphne Millbrook and the Flash.
 * <ul>
 *   <li>Blitz: rush an enemy and land a flurry; press again in the moment after to chain to the next.
 *   With no one in sight, it is the old Quickstep dash.</li>
 *   <li>Snatch: in one blink, take every dropped item nearby, every enemy shot out of the air, and the
 *   weapon from the hand you are looking at. To everyone else you never moved.</li>
 *   <li>Cyclone: run rings around where you stand; the wind drags enemies in and up, then throws them.</li>
 *   <li>Lightspeed: one streak forward through everything on the line, ending in a sonic boom.</li>
 * </ul>
 */
public final class SpeedKit {
    public static final int CHAIN_WINDOW = 30, FLURRY_HITS = 4, CYCLONE_TICKS = 60, LIGHTSPEED_RANGE = 48, BLITZ_MOMENTUM = 30;
    private static final double CYCLONE_RADIUS = 6.5;

    private static final class Chain {
        final int tier; int left; long until; final Set<UUID> struck = new HashSet<>();
        Chain(int tier, int left, long until) { this.tier = tier; this.left = left; this.until = until; }
    }
    private record Hits(ServerPlayer player, LivingEntity target, float damage, int left, long next) {}
    private record Cyclone(ServerPlayer player, ServerLevel level, Vec3 center, long start, int tier, Set<UUID> caught) {}

    private static final Map<UUID, Chain> CHAINS = new HashMap<>();
    private static final Map<UUID, Hits> HITS = new HashMap<>();
    private static final Map<UUID, Cyclone> CYCLONES = new HashMap<>();

    private SpeedKit() {}

    private static long now(ServerPlayer p) { return p.getServer().overworld().getGameTime(); }

    private static void push(Entity target, Vec3 velocity) {
        target.setDeltaMovement(velocity);
        target.hurtMarked = true;
        if (target instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
    }

    // ------------------------------------------------------------------
    // Blitz
    // ------------------------------------------------------------------

    /** True while a chained Blitz can be pressed again. */
    public static boolean chaining(ServerPlayer p) {
        Chain chain = CHAINS.get(p.getUUID());
        return chain != null && chain.left > 0 && now(p) < chain.until;
    }

    /** Rush the enemy you are looking at and land a flurry, or dash if there is no one. */
    public static boolean blitz(ServerPlayer p, int tier) {
        LivingEntity target = SpeedService.target(p, 12 + 2 * tier);
        if (target == null) return SpeedService.dash(p, tier);
        if (!strike(p, target, tier)) return false;
        Chain chain = new Chain(tier, tier, now(p) + CHAIN_WINDOW);
        chain.struck.add(target.getUUID());
        CHAINS.put(p.getUUID(), chain);
        return true;
    }

    /** The next link: the nearest enemy you have not hit yet, within ten blocks. */
    public static boolean chainNext(ServerPlayer p) {
        Chain chain = CHAINS.get(p.getUUID());
        if (chain == null || !chaining(p)) return false;
        LivingEntity next = p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(10),
                        t -> SpeedService.canHit(p, t) && !chain.struck.contains(t.getUUID()) && p.hasLineOfSight(t))
                .stream().min(Comparator.comparingDouble(t -> t.distanceToSqr(p))).orElse(null);
        if (next == null || !strike(p, next, chain.tier)) return false;
        chain.struck.add(next.getUUID());
        chain.left--;
        chain.until = now(p) + CHAIN_WINDOW;
        if (chain.left > 0) p.displayClientMessage(Component.literal("Blitz x" + chain.struck.size() + "  (" + chain.left + " more)")
                .withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    /** Close the distance along a clear path, then four blows in a fraction of a second. Momentum makes them harder. */
    public static boolean strike(ServerPlayer p, LivingEntity target, int tier) {
        Vec3 from = p.position();
        if (p.distanceToSqr(target) > 9) {
            Vec3 delta = target.position().subtract(p.position());
            Vec3 approach = SpeedService.swept(p, delta.normalize().scale(Math.max(0, delta.length() - 1.6)));
            if (approach.distanceToSqr(target.position()) > 9) return false;
            SpeedService.move(p, approach);
        }
        boolean charged = SpeedService.drainMomentum(p, BLITZ_MOMENTUM);
        float damage = 2 + tier * 0.5F + (charged ? 2 : 0);
        HITS.put(p.getUUID(), new Hits(p, target, damage, FLURRY_HITS, now(p)));
        AbilityEffects.send(p.serverLevel(), Kind.SPEED_ZIP, from, p.position(), charged ? 1.5 : 1);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.5F);
        return true;
    }

    private static void tickHits(long now) {
        for (var entry : List.copyOf(HITS.entrySet())) {
            Hits h = entry.getValue();
            if (now < h.next) continue;
            ServerPlayer p = h.player;
            if (h.left <= 0 || !h.target.isAlive() || h.target.level() != p.level() || !PowerRules.canAct(p)
                    || p.distanceToSqr(h.target) > 16 || !SpeedService.canHit(p, h.target)) { HITS.remove(entry.getKey()); continue; }
            h.target.invulnerableTime = 0;
            SpeedService.hit(p, h.target, h.damage * (h.target instanceof ServerPlayer ? 0.6F : 1));
            Vec3 at = h.target.getBoundingBox().getCenter().add((p.getRandom().nextDouble() - 0.5) * 0.6, (p.getRandom().nextDouble() - 0.5) * 0.6, 0);
            AbilityEffects.send(p.serverLevel(), Kind.SPEED_IMPACT, p.getEyePosition(), at, 1);
            p.swing(h.left % 2 == 0 ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND, true);
            p.serverLevel().playSound(null, h.target.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.5F, 1.3F + h.left * 0.1F);
            if (h.left == 1) push(h.target, h.target.position().subtract(p.position()).normalize().scale(0.6).add(0, 0.25, 0));
            HITS.put(entry.getKey(), new Hits(p, h.target, h.damage, h.left - 1, now + 2));
        }
    }

    // ------------------------------------------------------------------
    // Snatch
    // ------------------------------------------------------------------

    /** Everything within reach, in one blink. Returns how many things were taken (0 means nothing was there). */
    public static int snatch(ServerPlayer p, int tier) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        double reach = 10 + tier;
        int taken = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(reach), i -> i.isAlive() && visible(p, i.position().add(0, 0.2, 0)))) {
            if (taken >= 16) break;
            ItemStack stack = item.getItem().copy();
            int before = stack.getCount();
            p.getInventory().add(stack);
            if (stack.getCount() == before) continue;
            AbilityEffects.send(level, Kind.SPEED_ZIP, eye, item.position(), 0.6);
            if (stack.isEmpty()) item.discard(); else item.setItem(stack);
            taken++;
        }
        for (Projectile shot : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(8), s -> s.isAlive() && s.getDeltaMovement().lengthSqr() > 0.01)) {
            Entity owner = shot.getOwner();
            if (owner == p || owner != null && p.isAlliedTo(owner) || !visible(p, shot.position())) continue;
            if (taken >= 32) break;
            AbilityEffects.send(level, Kind.SPEED_ZIP, eye, shot.position(), 0.6);
            if (shot instanceof AbstractArrow arrow && arrow.pickup == AbstractArrow.Pickup.ALLOWED) p.getInventory().add(new ItemStack(Items.ARROW));
            shot.discard();
            taken++;
        }
        LivingEntity aimed = Targeting.livingEntityInSight(p, 16, t -> SpeedService.canHit(p, t) && !t.getMainHandItem().isEmpty());
        if (aimed != null) {
            ItemStack weapon = aimed.getMainHandItem().copy();
            if (aimed instanceof ServerPlayer victim) {
                // A player's weapon is knocked out of their hand, not stolen outright.
                victim.drop(weapon, false, false);
                victim.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                victim.displayClientMessage(Component.literal("Something tore it out of your hand.").withStyle(ChatFormatting.RED), true);
            } else {
                aimed.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                if (aimed instanceof Mob mob) mob.setDropChance(EquipmentSlot.MAINHAND, 0);
                if (!p.getInventory().add(weapon)) p.drop(weapon, false);
            }
            AbilityEffects.send(level, Kind.SPEED_ZIP, eye, aimed.getEyePosition(), 1);
            taken++;
        }
        if (taken == 0) return 0;
        // To anyone watching you never moved: an afterimage holds your place while you were everywhere.
        AbilityEffects.send(level, Kind.SPEED_ZIP, p.position(), p.position(), 2);
        level.playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0F, 1.6F);
        level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 1.9F);
        p.displayClientMessage(Component.literal("Snatched " + taken + (taken == 1 ? " thing" : " things") + ".").withStyle(ChatFormatting.GOLD), true);
        return taken;
    }

    private static boolean visible(ServerPlayer p, Vec3 at) {
        var hit = p.level().clip(new ClipContext(p.getEyePosition(), at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(at) < 0.5;
    }

    // ------------------------------------------------------------------
    // Cyclone
    // ------------------------------------------------------------------

    public static boolean cycloning(ServerPlayer p) { return CYCLONES.containsKey(p.getUUID()); }

    /** Run rings around where you stand for three seconds. */
    public static boolean cyclone(ServerPlayer p, int tier) {
        if (cycloning(p)) return false;
        CYCLONES.put(p.getUUID(), new Cyclone(p, p.serverLevel(), p.position(), now(p), tier, new HashSet<>()));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
        return true;
    }

    private static void tickCyclones(long now) {
        for (var entry : List.copyOf(CYCLONES.entrySet())) {
            Cyclone c = entry.getValue();
            ServerPlayer p = c.player;
            long age = now - c.start;
            if (!PowerRules.canAct(p) || p.level() != c.level || p.isRemoved()) { CYCLONES.remove(entry.getKey()); continue; }
            if (age >= CYCLONE_TICKS) { CYCLONES.remove(entry.getKey()); release(c); continue; }
            // You are the wind, running the ring: you stay on the spot.
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 6, false, false, false));
            for (Entity e : c.level.getEntities(p, new AABB(c.center, c.center).inflate(CYCLONE_RADIUS + 1),
                    e -> e instanceof Projectile || e instanceof LivingEntity l && SpeedService.canHit(p, l))) {
                Vec3 radial = e.position().subtract(c.center).multiply(1, 0, 1);
                double distance = radial.length();
                if (distance > CYCLONE_RADIUS + 1) continue;
                Vec3 out = distance < 1.0E-3 ? new Vec3(1, 0, 0) : radial.scale(1 / distance);
                if (e instanceof Projectile shot) {
                    if (shot.getOwner() == p || shot.getOwner() != null && p.isAlliedTo(shot.getOwner())) continue;
                    push(shot, out.scale(1.2).add(0, 0.15, 0));
                    continue;
                }
                LivingEntity living = (LivingEntity) e;
                c.caught.add(living.getUUID());
                Vec3 around = new Vec3(-out.z, 0, out.x);
                double pull = distance > 2.5 ? -0.14 : 0.06;
                double lift = Math.min(0.12, 0.04 + age * 0.0015);
                push(living, around.scale(0.6).add(out.scale(pull)).add(0, living.getY() - c.center.y < 3.5 ? lift : 0, 0));
                living.fallDistance = 0;
                if (age % 10 == 0) SpeedService.hit(p, living, (2 + c.tier * 0.5F) * (living instanceof ServerPlayer ? 0.6F : 1));
            }
            if (age % 3 == 0) AbilityEffects.send(c.level, Kind.SPEED_CYCLONE, c.center, c.center.add(0, age / (double) CYCLONE_TICKS, 0), CYCLONE_RADIUS);
            if (age % 8 == 0) c.level.playSound(null, c.center.x, c.center.y, c.center.z, SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS, 0.9F, 1.4F);
        }
    }

    /** The ring breaks: everything it carried is thrown up and out. */
    private static void release(Cyclone c) {
        for (UUID id : c.caught) {
            if (!(c.level.getEntity(id) instanceof LivingEntity living) || !living.isAlive() || living.distanceToSqr(c.center) > 12 * 12) continue;
            Vec3 out = living.position().subtract(c.center).multiply(1, 0, 1);
            out = out.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : out.normalize();
            push(living, out.scale(1.1).add(0, 0.9, 0));
        }
        AbilityEffects.send(c.level, Kind.SPEED_BOOM, c.center.add(0, 1, 0), c.center.add(0, 2, 0), 2);
        c.level.playSound(null, c.center.x, c.center.y, c.center.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.3F, 0.6F);
    }

    // ------------------------------------------------------------------
    // Lightspeed
    // ------------------------------------------------------------------

    /**
     * One streak forward, up to 48 blocks along a clear path. Everything within reach of the line is
     * struck, set briefly alight by the friction and thrown aside; a sonic boom goes off where you stop.
     * Spends all your Momentum for damage. Returns how many were struck, or -1 if there was no room to run.
     */
    public static int lightspeed(ServerPlayer p, int tier) {
        Vec3 direction = SpeedService.horizontal(p.getLookAngle());
        Vec3 start = p.position(), end = SpeedService.swept(p, direction.scale(LIGHTSPEED_RANGE));
        if (end.distanceToSqr(start) < 16) return -1;
        int momentum = SpeedService.spendMomentum(p, 0);
        float damage = 8 + tier + momentum / 5F;
        ServerLevel level = p.serverLevel();
        Vec3 axis = end.subtract(start);
        double length = axis.length();
        Vec3 dir = axis.scale(1 / length), side = new Vec3(-dir.z, 0, dir.x);
        int struck = 0;
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(2), t -> SpeedService.canHit(p, t))) {
            Vec3 rel = t.position().subtract(start);
            double along = rel.dot(dir);
            if (along < -0.5 || along > length + 0.5 || Math.abs(rel.dot(side)) > 1.8) continue;
            if (++struck > 24) break;
            t.invulnerableTime = 0;
            SpeedService.hit(p, t, damage * (t instanceof ServerPlayer ? 0.6F : 1));
            t.igniteForSeconds(2);
            push(t, side.scale(rel.dot(side) >= 0 ? 1.0 : -1.0).add(dir.scale(0.5)).add(0, 0.45, 0));
        }
        SpeedService.move(p, end);
        AbilityEffects.send(level, Kind.SPEED_STREAK, start.add(0, 0.05, 0), end.add(0, 0.05, 0), 1 + momentum / 50F);
        AbilityEffects.send(level, Kind.SPEED_BOOM, end.add(0, 1, 0), end.add(0, 1, 0).add(dir), 2.5);
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(5), t -> SpeedService.canHit(p, t))) {
            Vec3 out = t.position().subtract(end).multiply(1, 0, 1);
            push(t, (out.lengthSqr() < 1.0E-4 ? dir : out.normalize()).scale(0.9).add(0, 0.4, 0));
            SpeedService.hit(p, t, 4);
        }
        level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8F, 1.3F);
        level.playSound(null, start.x, start.y, start.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.4F, 1.8F);
        return struck;
    }

    // ------------------------------------------------------------------

    static void tick(MinecraftServer server, long now) {
        tickHits(now);
        tickCyclones(now);
        CHAINS.entrySet().removeIf(e -> now >= e.getValue().until + 20);
    }

    static void forget(ServerPlayer p) { CHAINS.remove(p.getUUID()); HITS.remove(p.getUUID()); CYCLONES.remove(p.getUUID()); }

    static void clear() { CHAINS.clear(); HITS.clear(); CYCLONES.clear(); }
}
