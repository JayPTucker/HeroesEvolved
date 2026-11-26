package com.jayptucker.heroesevolved.combat;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.mimicry.MimicryService;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.*;

/**
 * What a boss asks of the player, and which powers answer it: the one table every boss, its drawing, the Guide and the
 * tests read (the bosses skill's fight-design.md, section 2). A <em>lock</em> is a mechanic a boss puts in front of the
 * player; every power has a <em>key</em> to some of them, and everyone has the slow answer (plain weapons, cover, milk).
 *
 * <p>A key reaches its lock one of four ways ({@link Channel}): the power's own hits count for more against the lock's
 * target; an aimed use of the power answers it (a technopath overrides a heart like a circuit); the power's kit already
 * does it (Wind Arms turns skulls, a cryokinetic freezes a skeleton solid, a shield holds a blast); or a power used in
 * the moment the blow lands carries its user out of it. Plain weapons are the fallback, never the plan.
 */
public final class BossLocks {
    /** The kinds of lock (the skill's lock table). A boss's phase is built from two or three of them. */
    public enum Lock { SHIELD, PROJECTILES, BODIES, ROOM_WEAPON, CURSE, UNBLOCKABLE, WEAK_POINT }
    /** What the power does to the lock. Each has a line in the Guide ({@code lock.heroesevolved.role.*}). */
    public enum Role { BREAK, SWITCH_OFF, SEVER, SEND_BACK, WALL, CLEAR, HOLD, TURN, CLEANSE, BRACE, DODGE, STOP, OUTLAST, REACH, HIT_THERE, OVERLOAD }
    public enum Strength { GOOD, STRONG }
    public enum Channel { HITS, AIMED, KIT, TIMED }
    public record Key(Lock lock, Role role, Strength strength, Channel channel) {
        public boolean strong() { return strength == Strength.STRONG; }
    }

    /** How much a hit from a power with this key counts against a lock's target; plain weapons count for half. */
    public static final float STRONG_HITS = 2.5F, GOOD_HITS = 1.75F, POWER_HITS = 1, PLAIN = .5F;
    /** A supercharged ally's answers count for a quarter more (Power Amplification makes an ally's answer bigger). */
    public static final float SURGED = 1.25F;
    /** An aimed answer takes this share of its target: a strong key, a good one. */
    public static final float STRONG_AIMED = .4F, GOOD_AIMED = .25F;
    /** An aimed answer reaches this far, within this angle of the look, and costs at least this much stamina. */
    public static final double AIM_RANGE = 32, AIM_ANGLE = Math.toRadians(11);
    public static final int AIM_COST = 8, AIM_COOLDOWN = 30;

    private static final Map<ResourceLocation, List<Key>> TABLE = new LinkedHashMap<>();
    private static final Map<UUID, Long> LAST_USE = new HashMap<>();
    private static final Map<String, Long> LAST_AIMED = new HashMap<>();

    private static Key k(Lock lock, Role role, Strength strength, Channel channel) { return new Key(lock, role, strength, channel); }
    private static void put(ResourceLocation power, Key... keys) { TABLE.put(power, List.of(keys)); }
    static {
        Strength S = Strength.STRONG, G = Strength.GOOD;
        Channel HITS = Channel.HITS, AIMED = Channel.AIMED, KIT = Channel.KIT, TIMED = Channel.TIMED;
        put(ModAbilities.PYROKINESIS_ID, k(Lock.SHIELD, Role.BREAK, S, HITS), k(Lock.BODIES, Role.CLEAR, S, KIT),
                k(Lock.ROOM_WEAPON, Role.BREAK, S, HITS), k(Lock.UNBLOCKABLE, Role.DODGE, S, TIMED), k(Lock.WEAK_POINT, Role.REACH, S, HITS));
        put(ModAbilities.CRYOKINESIS_ID, k(Lock.SHIELD, Role.BREAK, G, HITS), k(Lock.PROJECTILES, Role.WALL, S, KIT),
                k(Lock.BODIES, Role.HOLD, S, KIT), k(Lock.ROOM_WEAPON, Role.BREAK, G, HITS), k(Lock.UNBLOCKABLE, Role.BRACE, S, KIT),
                k(Lock.WEAK_POINT, Role.REACH, G, HITS));
        put(ModAbilities.ELECTROKINESIS_ID, k(Lock.SHIELD, Role.SWITCH_OFF, S, HITS), k(Lock.BODIES, Role.CLEAR, G, KIT),
                k(Lock.ROOM_WEAPON, Role.SWITCH_OFF, S, HITS), k(Lock.WEAK_POINT, Role.HIT_THERE, S, HITS));
        put(ModAbilities.HYDROKINESIS_ID, k(Lock.SHIELD, Role.BREAK, S, HITS), k(Lock.BODIES, Role.HOLD, G, KIT),
                k(Lock.CURSE, Role.CLEANSE, S, AIMED), k(Lock.WEAK_POINT, Role.HIT_THERE, S, HITS));
        put(ModAbilities.SUPERSPEED_ID, k(Lock.SHIELD, Role.BREAK, G, HITS), k(Lock.PROJECTILES, Role.SEND_BACK, S, KIT),
                k(Lock.BODIES, Role.CLEAR, S, KIT), k(Lock.ROOM_WEAPON, Role.REACH, G, HITS), k(Lock.UNBLOCKABLE, Role.DODGE, S, TIMED));
        put(ModAbilities.FLIGHT_ID, k(Lock.SHIELD, Role.REACH, G, HITS), k(Lock.PROJECTILES, Role.WALL, S, KIT),
                k(Lock.BODIES, Role.HOLD, S, KIT), k(Lock.UNBLOCKABLE, Role.DODGE, S, TIMED), k(Lock.WEAK_POINT, Role.REACH, S, HITS));
        put(ModAbilities.QUAKE_ID, k(Lock.SHIELD, Role.BREAK, S, HITS), k(Lock.PROJECTILES, Role.SEND_BACK, G, KIT),
                k(Lock.BODIES, Role.CLEAR, S, KIT), k(Lock.ROOM_WEAPON, Role.BREAK, S, HITS));
        put(ModAbilities.SONIC_RESONANCE_ID, k(Lock.SHIELD, Role.BREAK, S, HITS), k(Lock.PROJECTILES, Role.SEND_BACK, G, KIT),
                k(Lock.BODIES, Role.CLEAR, S, KIT), k(Lock.UNBLOCKABLE, Role.BRACE, S, KIT));
        put(ModAbilities.TELEPATHY_ID, k(Lock.SHIELD, Role.SEVER, S, AIMED), k(Lock.BODIES, Role.TURN, G, KIT),
                k(Lock.CURSE, Role.SEVER, S, AIMED), k(Lock.ROOM_WEAPON, Role.SEVER, S, AIMED));
        put(ModAbilities.TELEKINESIS_ID, k(Lock.SHIELD, Role.BREAK, S, HITS), k(Lock.PROJECTILES, Role.SEND_BACK, S, KIT),
                k(Lock.BODIES, Role.HOLD, S, KIT), k(Lock.UNBLOCKABLE, Role.BRACE, S, KIT), k(Lock.WEAK_POINT, Role.HIT_THERE, G, HITS));
        put(ModAbilities.TIME_MANIPULATION_ID, k(Lock.SHIELD, Role.STOP, G, AIMED), k(Lock.PROJECTILES, Role.SEND_BACK, S, KIT),
                k(Lock.CURSE, Role.CLEANSE, S, AIMED), k(Lock.ROOM_WEAPON, Role.STOP, G, AIMED), k(Lock.UNBLOCKABLE, Role.STOP, S, TIMED));
        put(ModAbilities.RADIATION_ID, k(Lock.SHIELD, Role.BREAK, S, HITS), k(Lock.BODIES, Role.CLEAR, S, KIT),
                k(Lock.ROOM_WEAPON, Role.BREAK, G, HITS), k(Lock.WEAK_POINT, Role.HIT_THERE, S, HITS));
        put(ModAbilities.REGENERATION_ID, k(Lock.SHIELD, Role.SEVER, S, AIMED), k(Lock.CURSE, Role.CLEANSE, S, AIMED),
                k(Lock.ROOM_WEAPON, Role.SEVER, G, AIMED), k(Lock.UNBLOCKABLE, Role.OUTLAST, S, KIT));
        put(ModAbilities.PHASING_ID, k(Lock.SHIELD, Role.BREAK, S, HITS), k(Lock.PROJECTILES, Role.WALL, G, KIT),
                k(Lock.BODIES, Role.HOLD, S, KIT), k(Lock.UNBLOCKABLE, Role.WALL, S, KIT), k(Lock.WEAK_POINT, Role.REACH, G, HITS));
        put(ModAbilities.INVISIBILITY_ID, k(Lock.SHIELD, Role.BREAK, G, HITS), k(Lock.PROJECTILES, Role.SEND_BACK, S, KIT),
                k(Lock.BODIES, Role.HOLD, S, KIT), k(Lock.UNBLOCKABLE, Role.BRACE, S, KIT));
        put(ModAbilities.MIMICRY_ID, k(Lock.SHIELD, Role.SEVER, S, AIMED), k(Lock.ROOM_WEAPON, Role.SEVER, S, AIMED));
        put(ModAbilities.SHAPESHIFTING_ID, k(Lock.SHIELD, Role.BREAK, S, HITS), k(Lock.BODIES, Role.TURN, G, KIT),
                k(Lock.ROOM_WEAPON, Role.BREAK, S, HITS), k(Lock.WEAK_POINT, Role.REACH, S, HITS));
        put(ModAbilities.TECHNOPATHY_ID, k(Lock.SHIELD, Role.SWITCH_OFF, S, AIMED), k(Lock.BODIES, Role.TURN, G, KIT),
                k(Lock.ROOM_WEAPON, Role.SWITCH_OFF, S, AIMED));
        put(ModAbilities.MATTER_ASSEMBLY_ID, k(Lock.SHIELD, Role.REACH, G, HITS), k(Lock.PROJECTILES, Role.WALL, S, KIT),
                k(Lock.BODIES, Role.TURN, S, KIT), k(Lock.UNBLOCKABLE, Role.BRACE, S, KIT), k(Lock.WEAK_POINT, Role.REACH, G, HITS));
        put(ModAbilities.VIBE_ID, k(Lock.SHIELD, Role.SWITCH_OFF, S, AIMED), k(Lock.ROOM_WEAPON, Role.SWITCH_OFF, S, AIMED),
                k(Lock.UNBLOCKABLE, Role.DODGE, S, TIMED));
        put(ModAbilities.AMPLIFICATION_ID, k(Lock.SHIELD, Role.OVERLOAD, S, AIMED), k(Lock.CURSE, Role.CLEANSE, G, AIMED),
                k(Lock.ROOM_WEAPON, Role.OVERLOAD, S, AIMED));
    }

    /** Curses a cleansing key lifts: real effects, so milk and every power that clears effects lift them too. */
    public static List<Holder<MobEffect>> curses() { return List.of(ModItems.HUNGERING); }

    private BossLocks() {}

    /** Every power the table knows, in order. */
    public static Set<ResourceLocation> powers() { return Collections.unmodifiableSet(TABLE.keySet()); }
    /** {@code power}'s key to {@code lock}, or null. */
    public static Key key(ResourceLocation power, Lock lock) {
        if (power == null) return null;
        for (Key key : TABLE.getOrDefault(power, List.of())) if (key.lock() == lock) return key;
        return null;
    }
    /**
     * This player's key to {@code lock}: their awakened power's, or (an empath) the power they are channeling, whichever
     * is stronger, so an empath gets both their own answers and the borrowed power's.
     */
    public static Key key(ServerPlayer player, Lock lock) {
        Key best = null;
        for (ResourceLocation power : powersOf(player)) {
            Key key = key(power, lock);
            if (key != null && (best == null || key.strength().ordinal() > best.strength().ordinal())) best = key;
        }
        return best;
    }
    private static List<ResourceLocation> powersOf(ServerPlayer player) {
        var own = PlayerAbilityService.getData(player).assignedPower();
        if (own.isEmpty() || !own.get().getValue().isUnlocked()) return List.of();
        ResourceLocation power = own.get().getKey();
        if (MimicryService.isMimic(player)) {
            ResourceLocation channel = MimicryService.memory(player).current();
            if (channel != null && !channel.equals(power)) return List.of(power, channel);
        }
        return List.of(power);
    }
    /** Has an awakened power at all. */
    public static boolean gifted(ServerPlayer player) { return !powersOf(player).isEmpty(); }

    /**
     * A plain weapon: a sword, axe, trident or mace swing, an arrow, a bullet. Anything else a gifted player does to a
     * boss is their power (fists included: a fighting style is a power's).
     */
    public static boolean plain(DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (direct instanceof AbstractArrow) return true;
        if (direct != null && PowerWeapons.isBullet(direct)) return true;
        if (source.is(DamageTypes.PLAYER_ATTACK) && source.getEntity() instanceof Player p && direct == p) {
            ItemStack held = p.getMainHandItem();
            return !held.isEmpty() && (held.getItem() instanceof TieredItem || held.getItem() instanceof TridentItem || held.getItem() instanceof MaceItem);
        }
        return false;
    }
    /**
     * How much a blow counts against a lock's target: plain weapons half; a gifted player's power fully, or more with a
     * key whose channel is its hits; a quarter more while supercharged. Zero for anything that isn't a player's.
     */
    public static float hitFactor(DamageSource source, Lock lock) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        if (plain(source) || !gifted(player)) return PLAIN;
        Key key = key(player, lock);
        float factor = key == null || key.channel() != Channel.HITS ? POWER_HITS : key.strong() ? STRONG_HITS : GOOD_HITS;
        return factor * (com.jayptucker.heroesevolved.amplify.Amplification.surged(player) ? SURGED : 1);
    }

    // ---------------------------------------------------------------- aimed and timed answers

    /** A boss with a party: its fighters, which may include fake players the level's own lists never hold. */
    public interface Party { List<ServerPlayer> everyone(); }

    /** A lock target an aimed power use can answer (a boss's heart, its pylon). */
    public interface Lockable {
        /** The lock this target is right now, or null while nothing can answer it. */
        Lock lock();
        /** Where an aimed use must point. */
        Vec3 aimPoint();
        /** {@code player}'s aimed power use, whose {@code key} answers this lock, reached it. */
        void answered(ServerPlayer player, Key key);
    }

    /**
     * Every successful power use passes through here ({@code AbilityUseService}): it is remembered for timed answers; an
     * aimed key reaches the lock target the player is looking at; a cleansing key lifts the user's own curse, or the
     * curse on the ally they are looking at.
     */
    public static void used(ServerPlayer player, ResourceLocation power, String action, int cost) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        LAST_USE.put(player.getUUID(), now);
        if (cost < AIM_COST) return;
        Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        // An aimed answer: the lock target best in line with the look, in sight.
        Lockable best = null;
        double bestAngle = AIM_ANGLE;
        for (Entity e : level.getEntities((Entity) null, new AABB(eye, eye).inflate(AIM_RANGE), e -> e instanceof Lockable)) {
            Lockable target = (Lockable) e;
            Lock lock = target.lock();
            if (lock == null) continue;
            Key key = key(player, lock);
            if (key == null || key.channel() != Channel.AIMED) continue;
            Vec3 to = target.aimPoint().subtract(eye);
            if (to.length() > AIM_RANGE || to.lengthSqr() < 1e-4) continue;
            double angle = Math.acos(Math.clamp(to.normalize().dot(look), -1, 1));
            if (angle < bestAngle && seen(level, eye, target.aimPoint(), e)) { best = target; bestAngle = angle; }
        }
        if (best != null) {
            String id = player.getUUID() + "/" + ((Entity) best).getUUID();
            Long last = LAST_AIMED.get(id);
            if (last == null || now - last >= AIM_COOLDOWN) {
                LAST_AIMED.put(id, now);
                best.answered(player, key(player, best.lock()));
            }
        }
        // A cleansing answer: the user's own curse, or the cursed ally in their sights.
        Key cleanse = key(player, Lock.CURSE);
        if (cleanse != null && cleanse.channel() == Channel.AIMED) {
            if (!lift(player)) {
                Set<ServerPlayer> near = new LinkedHashSet<>(level.getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(16), q -> true));
                for (Entity e : level.getEntities((Entity) null, player.getBoundingBox().inflate(64), e -> e instanceof Party))
                    near.addAll(((Party) e).everyone());
                for (ServerPlayer ally : near) {
                    if (ally == player || !cursed(ally) || ally.distanceTo(player) > 16) continue;
                    Vec3 to = ally.getBoundingBox().getCenter().subtract(eye);
                    if (to.lengthSqr() > 1e-4 && Math.acos(Math.clamp(to.normalize().dot(look), -1, 1)) < AIM_ANGLE * 1.6
                            && seen(level, eye, ally.getBoundingBox().getCenter(), ally)) { lift(ally); break; }
                }
            }
        }
    }
    /** Used a power in the last {@code ticks}: the timed answers (a dodge, a stopped moment) ask this. */
    public static boolean usedWithin(ServerPlayer player, int ticks) {
        Long last = LAST_USE.get(player.getUUID());
        return last != null && player.serverLevel().getGameTime() - last <= ticks;
    }
    /** When this player last used their power (game time), or far in the past. */
    public static long lastUse(ServerPlayer player) { return LAST_USE.getOrDefault(player.getUUID(), -100000L); }
    /** Tests and previews: as if {@code player} had just used their power. */
    public static void markUsed(ServerPlayer player) { LAST_USE.put(player.getUUID(), player.serverLevel().getGameTime()); }
    public static boolean cursed(ServerPlayer player) {
        for (var curse : curses()) if (player.hasEffect(curse)) return true;
        return false;
    }
    /** Lifts every boss curse on {@code player}; true if there was one. */
    public static boolean lift(ServerPlayer player) {
        boolean any = false;
        for (var curse : curses()) any |= player.removeEffect(curse);
        return any;
    }
    private static boolean seen(ServerLevel level, Vec3 from, Vec3 to, Entity target) {
        var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(to) < .7 || target.getBoundingBox().inflate(.3).contains(hit.getLocation());
    }
    public static void forget(ServerPlayer player) {
        LAST_USE.remove(player.getUUID());
        String prefix = player.getUUID() + "/";
        LAST_AIMED.keySet().removeIf(k -> k.startsWith(prefix));
    }
    public static void clear() { LAST_USE.clear(); LAST_AIMED.clear(); }
}
