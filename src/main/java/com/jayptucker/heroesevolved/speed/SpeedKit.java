package com.jayptucker.heroesevolved.speed;

import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.SpeedCyclonePayload;
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
 *   With no one in sight, it is the old Quickstep dash. At 80 Momentum you vibrate through a wall to reach
 *   an enemy you can see through a window or gap.</li>
 *   <li>Snatch: in one blink, take every dropped item nearby, every enemy shot out of the air, and the
 *   weapon from the hand you are looking at. To everyone else you never moved.</li>
 *   <li>Cyclone: run rings around where you stand; the wind drags enemies in and up, then throws them. Hold the
 *   key and the ring rises into a tornado that draws in everything for 24 blocks, carries it high up its funnel
 *   and leaves it no air. Hold on three seconds more and it stands on its own for thirty.</li>
 *   <li>Lightspeed: one streak forward through everything on the line, ending in a sonic boom.</li>
 * </ul>
 */
public final class SpeedKit {
    public static final int CHAIN_WINDOW = 30, FLURRY_HITS = 4, CYCLONE_TICKS = 60, LIGHTSPEED_RANGE = 48, BLITZ_MOMENTUM = 30;
    public static final double CYCLONE_RADIUS = 6.5;
    /**
     * Hold Cyclone this long and the ring rises into a tornado. Keep holding while it stands for {@code TORNADO_ANCHOR}
     * ticks (stamina each second) and it is anchored: let go and it stands on its own, without you, until
     * {@code TORNADO_STAND} ticks after it rose. Let go before that and it comes down at once.
     */
    public static final int TORNADO_HOLD = 30, TORNADO_ANCHOR = 60, TORNADO_STAND = 600, TORNADO_ENERGY = 4;
    /** How far the tornado draws things in, in blocks. */
    public static final double TORNADO_PULL = 24;

    private static final class Chain {
        final int tier; int left; long until; final Set<UUID> struck = new HashSet<>();
        Chain(int tier, int left, long until) { this.tier = tier; this.left = left; this.until = until; }
    }
    private record Hits(ServerPlayer player, LivingEntity target, float damage, int left, long next) {}
    private static final class Cyclone {
        final ServerPlayer player; final ServerLevel level; final Vec3 center; final long start; final int tier;
        final Set<UUID> caught = new HashSet<>();
        /** The key is still down; the tick its tornado began to rise, or -1; the runner has left it standing alone. */
        boolean held, free; long tornado = -1;
        Cyclone(ServerPlayer player, ServerLevel level, Vec3 center, long start, int tier) {
            this.player = player; this.level = level; this.center = center; this.start = start; this.tier = tier;
        }
    }

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
            Vec3 goal = p.position().add(delta.normalize().scale(Math.max(0, delta.length() - 1.6)));
            Vec3 approach = SpeedService.swept(p, goal.subtract(p.position()));
            // At 80 Momentum a window or a gap is enough: you vibrate through the wall around it.
            if (approach.distanceToSqr(target.position()) > 9 && SpeedService.momentum(p) >= SpeedService.VIBRATE_MOMENTUM) {
                Vec3 through = com.jayptucker.heroesevolved.phasing.Passage.sweep(p, goal, SpeedService.VIBRATE_WALL);
                if (through.distanceToSqr(target.position()) <= 9) {
                    AbilityEffects.send(p.serverLevel(), Kind.SPEED_VIBRATE, from, through, 1);
                    approach = through;
                }
            }
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

    /** True once the key has been held long enough for the ring to rise into a tornado. */
    public static boolean tornado(ServerPlayer p) { Cyclone c = CYCLONES.get(p.getUUID()); return c != null && c.tornado >= 0; }

    /** Run rings around where you stand for three seconds (a tap: it never rises into a tornado). */
    public static boolean cyclone(ServerPlayer p, int tier) { return cyclone(p, tier, false); }

    /**
     * Run rings around where you stand. While {@code held}, keeping the key down for {@link #TORNADO_HOLD} ticks
     * raises the ring into a tornado that stands as long as the key is held (up to {@link #TORNADO_TICKS}).
     */
    public static boolean cyclone(ServerPlayer p, int tier, boolean held) {
        if (cycloning(p)) return false;
        Cyclone c = new Cyclone(p, p.serverLevel(), p.position(), now(p), tier);
        c.held = held;
        CYCLONES.put(p.getUUID(), c);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
        sync(c, now(p), false);
        return true;
    }

    /** True once the tornado stands on its own and its runner has gone. */
    public static boolean standing(ServerPlayer p) { Cyclone c = CYCLONES.get(p.getUUID()); return c != null && c.free; }

    /**
     * The key was let go: a ring runs out its three seconds; a tornado not yet anchored breaks now; an anchored
     * one stands on without its runner.
     */
    public static void releaseCyclone(ServerPlayer p) {
        Cyclone c = CYCLONES.get(p.getUUID());
        if (c == null) return;
        c.held = false;
        if (c.tornado < 0 || c.free) return;
        long now = now(p);
        if (now - c.tornado >= TORNADO_ANCHOR) letGo(c, now);
        else { CYCLONES.remove(p.getUUID()); release(c, now); }
    }

    /** The runner steps out of their tornado; it keeps turning where it stands. */
    private static void letGo(Cyclone c, long now) {
        c.free = true;
        c.player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        sync(c, now, false);
    }

    private static void sync(Cyclone c, long now, boolean ended) {
        SpeedCyclonePayload.send(c.level, c.player, c.center, (float) CYCLONE_RADIUS, (int) (now - c.start),
                c.tornado < 0 ? -1 : (int) (now - c.tornado), ended, c.free, SpeedCyclonePayload.SPEED);
    }

    private static void tickCyclones(long now) {
        for (var entry : List.copyOf(CYCLONES.entrySet())) {
            Cyclone c = entry.getValue();
            ServerPlayer p = c.player;
            long age = now - c.start;
            long built = c.tornado < 0 ? -1 : now - c.tornado;
            if (!c.free && (!PowerRules.canAct(p) || p.level() != c.level || p.isRemoved() || !p.isAlive())) {
                // An anchored tornado outlasts its runner; anything else ends with them.
                if (built >= TORNADO_ANCHOR && !p.isRemoved()) letGo(c, now);
                else { CYCLONES.remove(entry.getKey()); sync(c, now, true); continue; }
            }
            if (c.tornado < 0 && c.held && age >= TORNADO_HOLD) { rise(c, now); built = 0; }
            if (c.tornado < 0 && age >= CYCLONE_TICKS) { CYCLONES.remove(entry.getKey()); release(c, now); continue; }
            if (built >= TORNADO_STAND) { CYCLONES.remove(entry.getKey()); release(c, now); continue; }
            if (built >= 0 && built < TORNADO_ANCHOR) {
                // The key is never trusted to come back up, and holding the wind up costs stamina until it is anchored.
                if (built > 0 && built % 20 == 0 && !PlayerEnergyService.tryConsume(p, TORNADO_ENERGY)) {
                    CYCLONES.remove(entry.getKey()); release(c, now); continue;
                }
            }
            if (built == TORNADO_ANCHOR) anchor(c);
            if (!c.free) {
                // You are the wind, running the ring: your body stays at its eye.
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 6, false, false, false));
                p.fallDistance = 0;
            }
            if (c.tornado >= 0) twister(c, now); else ring(c, age);
            if (age % 20 == 0 && age > 0) sync(c, now, false);
            if (age % 8 == 0) c.level.playSound(null, c.center.x, c.center.y, c.center.z, SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS,
                    c.tornado >= 0 ? 1.6F : 0.9F, c.tornado >= 0 ? 0.7F : 1.4F);
        }
    }

    /** The ring: three seconds of wind dragging enemies round and up, flinging their shots away. */
    private static void ring(Cyclone c, long age) {
        ServerPlayer p = c.player;
        for (Entity e : c.level.getEntities(p, new AABB(c.center, c.center).inflate(CYCLONE_RADIUS + 1),
                e -> e instanceof Projectile || e instanceof LivingEntity l && SpeedService.canHit(p, l))) {
            Vec3 radial = e.position().subtract(c.center).multiply(1, 0, 1);
            double distance = radial.length();
            if (distance > CYCLONE_RADIUS + 1) continue;
            Vec3 out = distance < 1.0E-3 ? new Vec3(1, 0, 0) : radial.scale(1 / distance);
            if (e instanceof Projectile shot) { fling(p, shot, out); continue; }
            LivingEntity living = (LivingEntity) e;
            c.caught.add(living.getUUID());
            Vec3 around = new Vec3(-out.z, 0, out.x);
            double pull = distance > 2.5 ? -0.14 : 0.06;
            double lift = Math.min(0.12, 0.04 + age * 0.0015);
            push(living, around.scale(0.6).add(out.scale(pull)).add(0, living.getY() - c.center.y < 3.5 ? lift : 0, 0));
            living.fallDistance = 0;
            if (age % 10 == 0) SpeedService.hit(p, living, (2 + c.tier * 0.5F) * (living instanceof ServerPlayer ? 0.6F : 1));
        }
    }

    private static void fling(ServerPlayer p, Projectile shot, Vec3 out) {
        if (shot.getOwner() == p || shot.getOwner() != null && p.isAlliedTo(shot.getOwner())) return;
        push(shot, out.scale(1.2).add(0, 0.15, 0));
    }

    /** Held long enough, the ring rises: the wind climbs into a funnel over the runner. */
    private static void rise(Cyclone c, long now) {
        c.tornado = now;
        sync(c, now, false);
        Vec3 o = c.center;
        c.level.playSound(null, o.x, o.y, o.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.6F, 0.45F);
        c.level.playSound(null, o.x, o.y, o.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 1.4F, 0.6F);
        com.jayptucker.heroesevolved.network.CameraShakePayload.sendAround(c.level, o, 24, 0.35F, 30);
    }

    /** Held long enough, the tornado is anchored: it will stand on its own now, and holding it costs nothing more. */
    private static void anchor(Cyclone c) {
        Vec3 o = c.center;
        c.level.playSound(null, o.x, o.y + 6, o.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.9F, 0.7F);
        c.level.playSound(null, o.x, o.y, o.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.4F, 0.5F);
        com.jayptucker.heroesevolved.network.CameraShakePayload.sendAround(c.level, o, 28, 0.3F, 16);
    }

    /**
     * The tornado: it draws in everything for {@link #TORNADO_PULL} blocks, whirls what reaches it round the
     * funnel's wall and carries it up, hurts it every half second and leaves it no air (see {@link TornadoWind}).
     */
    private static void twister(Cyclone c, long now) {
        ServerPlayer p = c.player;
        float built = now - c.tornado, height = TornadoShape.height(built);
        TornadoWind.blow(p, c.level, c.center, built, (float) CYCLONE_RADIUS, TORNADO_PULL, 2.5F + c.tier * 0.5F, true, c.caught, now);
        if ((now - c.tornado) % 37 == 11) {
            Vec3 o = c.center;
            c.level.playSound(null, o.x, o.y + height * 0.6, o.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.45F, 1.5F);
        }
    }

    /** The ring breaks (or the tornado comes down): everything it carried is thrown up and out. */
    private static void release(Cyclone c, long now) {
        boolean tornado = c.tornado >= 0;
        for (UUID id : c.caught) {
            if (!(c.level.getEntity(id) instanceof LivingEntity living) || !living.isAlive()
                    || living.distanceToSqr(c.center) > (tornado ? 40 * 40 : 12 * 12)) continue;
            Vec3 out = living.position().subtract(c.center).multiply(1, 0, 1);
            out = out.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : out.normalize();
            push(living, tornado ? out.scale(1.4).add(0, 0.5, 0) : out.scale(1.1).add(0, 0.9, 0));
        }
        sync(c, now, true);
        AbilityEffects.send(c.level, Kind.SPEED_BOOM, c.center.add(0, 1, 0), c.center.add(0, 2, 0), tornado ? 3 : 2);
        c.level.playSound(null, c.center.x, c.center.y, c.center.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS,
                tornado ? 1.8F : 1.3F, tornado ? 0.45F : 0.6F);
        if (tornado) com.jayptucker.heroesevolved.network.CameraShakePayload.sendAround(c.level, c.center, 32, 0.6F, 14);
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
        SpeedWindArms.tick(now);
        SpeedContainment.tick(now);
        CHAINS.entrySet().removeIf(e -> now >= e.getValue().until + 20);
    }

    static void forget(ServerPlayer p) {
        CHAINS.remove(p.getUUID()); HITS.remove(p.getUUID()); SpeedWindArms.forget(p); SpeedContainment.forget(p);
        Cyclone c = CYCLONES.remove(p.getUUID());
        if (c != null) sync(c, now(p), true);
    }

    static void clear() { CHAINS.clear(); HITS.clear(); CYCLONES.clear(); SpeedWindArms.clear(); SpeedContainment.clear(); }
}
