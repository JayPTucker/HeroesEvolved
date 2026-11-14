package com.jayptucker.heroesevolved.shapeshift;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shapeshifting's last three keys (docs/shapeshifting.md, "Core abilities"): Decoy, Reflection and Nightmare, and the
 * status two of them leave on others. A <b>cowed</b> creature, confused by a decoy or a reflection or frightened by a
 * nightmare, can't pick a target and its blows land on nothing; a frightened one also runs.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class ShapeshiftTricks {
    /** How far a decoy draws hunters from, how long a decoy's burst confuses, and Reflection's window and reach. */
    public static final double DECOY_DRAW = 24, REFLECT_REACH = 24, NIGHTMARE_RANGE = 12, NIGHTMARE_SEEN = 24;
    public static final int CONFUSE_TICKS = 40, STAGGER_TICKS = 20, REFLECT_WINDOW = 60, FRIGHT_TICKS = 100;
    /**
     * What each viewer of a Nightmare sees in the shapeshifter's place, carried in the piece's scale: 2 a warden, 1 only
     * the plates. (Clients clamp a piece's scale to at least 0.05, so no flag may be zero or less.)
     */
    public static final int SEEN_WARDEN = 2, SEEN_NOTHING = 1;

    /** Who last hurt each shapeshifter, and when. */
    private record Hit(UUID attacker, long at) {}

    /** A cowed creature: until when, and the point a frightened one runs from (null when only confused). */
    private record Cowed(LivingEntity who, long until, @Nullable Vec3 from) {}

    private static final Map<UUID, Hit> LAST_HIT = new HashMap<>();
    private static final Map<UUID, Cowed> COWED = new HashMap<>();
    /** Each player's current decoy, and the mobs each decoy has drawn. */
    private static final Map<UUID, ShapeDecoy> DECOYS = new HashMap<>();
    private static final Map<UUID, ShapeDecoy> DRAWN = new HashMap<>();
    /** Creatures set upon by their own kind (Second Skin, Borrowed Face, the awakening): each attacker and its victim. */
    private record Turned(Mob mob, LivingEntity victim, long until) {}
    private static final Map<UUID, Turned> TURNED = new HashMap<>();
    /** Creatures wearing a shapeshifter's face after a Borrowed Face, and the face they wear. */
    private record Borrowed(LivingEntity who, ShapeshiftForm face, long until) {}
    private static final Map<UUID, Borrowed> BORROWED = new HashMap<>();

    private ShapeshiftTricks() {}

    // ---- Decoy ----

    /**
     * Shed an empty shell of your shape where you stand: it holds for six seconds and every mob that was hunting you
     * turns on it. You slip away a little faster for two seconds.
     */
    public static boolean decoy(ServerPlayer player) { return shell(player, null) != null; }

    /** A Doppelganger: a shell of your shape that walks on ahead for eight seconds, drawing your hunters. */
    public static boolean doppelganger(ServerPlayer player) {
        Vec3 ahead = player.getLookAngle().multiply(1, 0, 1);
        ahead = ahead.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : ahead.normalize();
        return shell(player, ahead.scale(.17)) != null;
    }

    private static @Nullable ShapeDecoy shell(ServerPlayer player, @Nullable Vec3 walk) {
        ServerLevel level = player.serverLevel();
        ShapeshiftForm worn = ShapeshiftService.worn(player);
        ShapeshiftForm form = worn != null ? worn : ShapeshiftForm.of(player.getGameProfile());
        ShapeDecoy old = DECOYS.remove(player.getUUID());
        if (old != null) burst(old, null);
        ShapeDecoy decoy = ModEntities.SHAPE_DECOY.get().create(level);
        if (decoy == null) return null;
        ShapeshiftSuspicion.power(player);
        decoy.setup(form, player, walk != null ? 160 : ShapeshiftSkills.decoyLife(player), walk, ShapeshiftSkills.hollowCrowd(player));
        Vec3 at = player.onGround() ? player.position() : StyleOps.ground(level, player.position());
        float yaw = walk != null ? (float) (Math.atan2(-walk.x, walk.z) * 180 / Math.PI) : player.getYRot();
        decoy.moveTo(at.x, at.y, at.z, yaw, 0);
        decoy.setYBodyRot(walk != null ? yaw : player.yBodyRot);
        decoy.setYHeadRot(walk != null ? yaw : player.getYHeadRot());
        level.addFreshEntity(decoy);
        DECOYS.put(player.getUUID(), decoy);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(DECOY_DRAW),
                mob -> mob.isAlive() && ShapeshiftService.targets(mob, player))) {
            draw(mob, decoy);
            DRAWN.put(mob.getUUID(), decoy);
        }
        SetPiecePayload.send(level, Piece.SHIFT_DECOY, decoy.position(), decoy.position().add(player.getLookAngle()),
                decoy.getBbHeight(), decoy);
        ArmPosePayload.show(player, ArmPose.SHIFT_SHED, 14);
        if (walk == null) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, false, false));
        sound(player, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1, .6F);
        sound(player, SoundEvents.ILLUSIONER_MIRROR_MOVE, .8F, 1.3F);
        return decoy;
    }

    private static void draw(Mob mob, LivingEntity decoy) {
        mob.setTarget(decoy);
        var brain = mob.getBrain();
        if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED))
            brain.setMemory(MemoryModuleType.ATTACK_TARGET, decoy);
    }

    /**
     * The shell bursts into plates: from a blow ({@code by} is whoever struck it, who is left confused for two seconds) or
     * from age (null). The mobs it drew are let go.
     */
    static void burst(ShapeDecoy decoy, @Nullable Entity by) {
        if (decoy.isRemoved()) return;
        ServerLevel level = (ServerLevel) decoy.level();
        SetPiecePayload.send(level, Piece.SHIFT_DECOY_BURST, decoy.position(), decoy.position().add(0, decoy.getBbHeight(), 0),
                by != null ? 1 : 0, null);
        level.playSound(null, decoy.getX(), decoy.getY(), decoy.getZ(), by != null ? SoundEvents.SHIELD_BREAK : SoundEvents.ARMOR_EQUIP_CHAIN.value(),
                SoundSource.PLAYERS, .9F, by != null ? .7F : 1.4F);
        if (by instanceof LivingEntity struck && !(by instanceof ShapeDecoy)) confuse(struck, CONFUSE_TICKS);
        // Hollow Crowd: the burst confuses everything close, not just the striker.
        if (by != null && decoy.wide())
            for (Mob near : level.getEntitiesOfClass(Mob.class, decoy.getBoundingBox().inflate(4), m -> m.isAlive() && m != by))
                confuse(near, CONFUSE_TICKS);
        DRAWN.entrySet().removeIf(e -> {
            if (e.getValue() != decoy) return false;
            if (level.getEntity(e.getKey()) instanceof Mob mob && mob.getTarget() == decoy) mob.setTarget(null);
            return true;
        });
        if (decoy.owner() != null) DECOYS.remove(decoy.owner(), decoy);
        decoy.discard();
    }

    /** A player's decoy now standing, or null. */
    public static @Nullable ShapeDecoy decoyOf(ServerPlayer player) {
        ShapeDecoy decoy = DECOYS.get(player.getUUID());
        return decoy == null || decoy.isRemoved() ? null : decoy;
    }

    // ---- Reflection ----

    /**
     * Within three seconds of being hit, become your attacker. Every one of its kind near you forgets you, and the attacker
     * staggers for a second, face to face with itself. Free of Morph's own cost: the key pays for it.
     */
    public static boolean reflect(ServerPlayer player) {
        Hit hit = LAST_HIT.get(player.getUUID());
        long now = player.serverLevel().getGameTime();
        Entity found = hit == null || now - hit.at > REFLECT_WINDOW ? null : player.serverLevel().getEntity(hit.attacker);
        if (!(found instanceof LivingEntity attacker) || !attacker.isAlive() || attacker.distanceToSqr(player) > REFLECT_REACH * REFLECT_REACH
                || attacker instanceof ShapeDecoy) {
            ShapeshiftService.say(player, "reflect_none");
            return false;
        }
        String refused = ShapeshiftService.refusal(player, attacker);
        if (refused != null) {
            if (refused.equals("too_big")) ShapeshiftService.say(player, refused, ShapeshiftForms.requiredLevel(attacker));
            else ShapeshiftService.say(player, refused);
            return false;
        }
        ShapeshiftSuspicion.power(player);
        Vec3 from = chest(player);
        if (!ShapeshiftService.morph(player, ShapeshiftService.formOf(attacker), false)) return false;
        ShapeshiftService.forgetKin(player, ShapeshiftSuspicion.WATCH_RANGE);
        confuse(attacker, STAGGER_TICKS);
        attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STAGGER_TICKS, 3, false, true));
        if (attacker instanceof Player) attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STAGGER_TICKS, 1, false, true));
        LAST_HIT.remove(player.getUUID());
        // Second Skin: its own kind see two of it, and go for the one that started it.
        if (ShapeshiftSkills.secondSkin(player)) turnOn(attacker, 100, player);
        SetPiecePayload.send(player.serverLevel(), Piece.SHIFT_REFLECT, from, chest(attacker), attacker.getBbHeight(), player);
        ArmPosePayload.show(player, ArmPose.SHIFT_REFLECT, 16);
        sound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 1, 1.4F);
        sound(player, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1, .8F);
        return true;
    }

    /** Whether a press of Reflection has someone to become now (for tests and the HUD). */
    public static boolean canReflect(ServerPlayer player) {
        Hit hit = LAST_HIT.get(player.getUUID());
        return hit != null && player.serverLevel().getGameTime() - hit.at <= REFLECT_WINDOW;
    }

    // ---- Borrowed Face ----

    /**
     * Touch a creature and swap looks for eight seconds: you take its shape, it wears yours, and its own kind turn on it.
     * Free of Morph's own cost: the key pays for it.
     */
    public static boolean borrowFace(ServerPlayer player) {
        LivingEntity target = com.jayptucker.heroesevolved.ability.Targeting.livingEntityInSight(player, 4,
                e -> e instanceof Mob && e.isAlive() && !(e instanceof ShapeDecoy));
        if (target == null) { ShapeshiftService.say(player, "borrow_none"); return false; }
        String refused = ShapeshiftService.refusal(player, target);
        if (refused != null) {
            if (refused.equals("too_big")) ShapeshiftService.say(player, refused, ShapeshiftForms.requiredLevel(target));
            else ShapeshiftService.say(player, refused);
            return false;
        }
        ShapeshiftForm worn = ShapeshiftService.worn(player);
        ShapeshiftForm mine = worn != null ? worn : ShapeshiftForm.of(player.getGameProfile());
        if (!ShapeshiftService.morph(player, ShapeshiftService.formOf(target), false)) return false;
        ShapeshiftService.forgetKin(player, ShapeshiftSuspicion.WATCH_RANGE);
        long until = player.serverLevel().getGameTime() + ShapeshiftSkills.BORROW_TICKS;
        BORROWED.put(target.getUUID(), new Borrowed(target, mine, until));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(target,
                new com.jayptucker.heroesevolved.network.MorphStatePayload(target.getId(), java.util.Optional.of(mine), true, 0));
        turnOn(target, ShapeshiftSkills.BORROW_TICKS, player);
        if (target instanceof Mob mob) ShapeshiftService.lose(mob, player);
        SetPiecePayload.send(player.serverLevel(), Piece.SHIFT_TOUCH, target.getBoundingBox().getCenter(), target.position(), 1, target);
        ArmPosePayload.show(player, ArmPose.SHIFT_TOUCH, 12);
        sound(player, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1, .7F);
        return true;
    }

    /** The face a creature wears after a Borrowed Face, or null. */
    public static @Nullable ShapeshiftForm borrowed(Entity who) {
        Borrowed b = BORROWED.get(who.getUUID());
        return b == null ? null : b.face;
    }

    private static void giveBack(Borrowed b) {
        if (!b.who.isRemoved())
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(b.who,
                    new com.jayptucker.heroesevolved.network.MorphStatePayload(b.who.getId(), java.util.Optional.empty(), true, 0));
    }

    /**
     * Sets a creature's own kind on it for {@code ticks}: every one of them within 12 blocks (but never the shapeshifter
     * who caused it) goes for it and won't be talked out of it.
     */
    static void turnOn(LivingEntity victim, int ticks, @Nullable ServerPlayer cause) {
        String kin = ShapeshiftForms.kin(victim);
        long until = victim.level().getGameTime() + ticks;
        for (Mob mob : victim.level().getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(12),
                m -> m != victim && m.isAlive() && kin.equals(ShapeshiftForms.kin(m)))) {
            mob.setTarget(victim);
            TURNED.put(mob.getUUID(), new Turned(mob, victim, until));
        }
    }

    /** Whether a creature is set upon its own kind now (for tests). */
    public static boolean turned(Entity who) { return TURNED.containsKey(who.getUUID()); }

    // ---- The awakening ----

    /**
     * The release when the power wakes to save someone: the owner ripples into the nearest threat's shape, and every
     * threat loses the owner and the ward and turns on the nearest of its own kind for five seconds (or stands confused,
     * if it has none near).
     */
    public static void awaken(ServerPlayer player, LivingEntity ward, List<LivingEntity> threats) {
        LivingEntity nearest = null;
        for (LivingEntity t : threats)
            if (t instanceof Mob && ShapeshiftForms.requiredLevel(t) != ShapeshiftForms.NEVER
                    && (nearest == null || t.distanceToSqr(player) < nearest.distanceToSqr(player))) nearest = t;
        if (nearest != null) ShapeshiftService.morph(player, ShapeshiftService.formOf(nearest), false);
        for (LivingEntity t : threats) {
            if (!(t instanceof Mob mob)) continue;
            if (mob.getTarget() == player || mob.getTarget() == ward) mob.setTarget(null);
            String kin = ShapeshiftForms.kin(mob);
            Mob other = null;
            for (Mob m : mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(12),
                    m -> m != mob && m.isAlive() && kin.equals(ShapeshiftForms.kin(m))))
                if (other == null || m.distanceToSqr(mob) < other.distanceToSqr(mob)) other = m;
            if (other != null) {
                mob.setTarget(other);
                TURNED.put(mob.getUUID(), new Turned(mob, other, mob.level().getGameTime() + 100));
            } else confuse(mob, 100);
        }
    }

    // ---- Nightmare ----

    /**
     * For one breath every enemy within 12 blocks sees what it fears most where you stand, and is frightened for five
     * seconds: it runs and can't attack. Each player sees their own piece: a foe sees a warden rear up, anyone else only
     * the plates bursting. Your shape doesn't change.
     */
    public static boolean nightmare(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(NIGHTMARE_RANGE),
                e -> ShapeshiftInstinct.hostile(player, e) && !(e instanceof ShapeDecoy) && e.distanceToSqr(player) <= NIGHTMARE_RANGE * NIGHTMARE_RANGE
                        && e.hasLineOfSight(player));
        if (foes.isEmpty()) { ShapeshiftService.say(player, "nightmare_none"); return false; }
        ShapeshiftSuspicion.power(player);
        Vec3 at = player.position();
        for (LivingEntity foe : foes) {
            frighten(foe, at, FRIGHT_TICKS);
            if (foe instanceof ServerPlayer viewer) {
                ShapeshiftGeometry.showTo(viewer, Piece.SHIFT_NIGHTMARE, at, viewer.getEyePosition(), SEEN_WARDEN, player);
                viewer.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
            }
        }
        for (ServerPlayer viewer : level.getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(NIGHTMARE_SEEN),
                p -> !foes.contains(p)))
            ShapeshiftGeometry.showTo(viewer, Piece.SHIFT_NIGHTMARE, at, viewer.getEyePosition(), SEEN_NOTHING, player);
        ArmPosePayload.show(player, ArmPose.SHIFT_NIGHTMARE, 22);
        sound(player, SoundEvents.WARDEN_ROAR, 1.2F, .9F);
        sound(player, SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1, .5F);
        return true;
    }

    // ---- Cowed creatures ----

    /** Confused: no target and no blows that land, for {@code ticks}. A mob also stops where it is. */
    public static void confuse(LivingEntity who, int ticks) {
        cow(who, ticks, null);
        if (who instanceof Mob mob) mob.getNavigation().stop();
        if (who.level() instanceof ServerLevel level)
            SetPiecePayload.send(level, Piece.SHIFT_DAZE, who.position(), who.position().add(0, who.getBbHeight(), 0), ticks, who);
    }

    /** Frightened: as confused, and a mob runs from {@code from}. */
    public static void frighten(LivingEntity who, Vec3 from, int ticks) { cow(who, ticks, from); }

    private static void cow(LivingEntity who, int ticks, @Nullable Vec3 from) {
        long until = who.level().getGameTime() + ticks;
        Cowed old = COWED.get(who.getUUID());
        if (old != null && old.until > until && from == null) return;
        COWED.put(who.getUUID(), new Cowed(who, until, from != null ? from : old == null ? null : old.from));
        if (who instanceof Mob mob) mob.setTarget(null);
    }

    /** Whether a creature is confused or frightened right now. */
    public static boolean cowed(Entity who) {
        Cowed c = COWED.get(who.getUUID());
        return c != null && who.level().getGameTime() < c.until;
    }

    public static boolean frightened(Entity who) {
        Cowed c = COWED.get(who.getUUID());
        return c != null && c.from != null && who.level().getGameTime() < c.until;
    }

    static void forget(ServerPlayer player) {
        ShapeDecoy decoy = DECOYS.remove(player.getUUID());
        if (decoy != null && !decoy.isRemoved()) burst(decoy, null);
        LAST_HIT.remove(player.getUUID());
    }

    /** A viewer who comes into range of a creature wearing a borrowed face is told what it looks like. */
    @SubscribeEvent public static void tracking(net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking event) {
        Borrowed b = BORROWED.get(event.getTarget().getUUID());
        if (b != null && event.getEntity() instanceof ServerPlayer viewer)
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer,
                    new com.jayptucker.heroesevolved.network.MorphStatePayload(b.who.getId(), java.util.Optional.of(b.face), false, 0));
    }

    static void clear() {
        TURNED.clear();
        BORROWED.clear();
        LAST_HIT.clear();
        COWED.clear();
        DECOYS.clear();
        DRAWN.clear();
    }

    private static Vec3 chest(Entity e) { return e.position().add(0, e.getBbHeight() * .6, 0); }

    private static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    // ---- Events ----

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        List<UUID> over = new ArrayList<>();
        for (var entry : COWED.entrySet()) {
            Cowed c = entry.getValue();
            LivingEntity who = c.who;
            long now = who.level().getGameTime();
            if (who.isRemoved() || !who.isAlive() || now >= c.until) { over.add(entry.getKey()); continue; }
            if (who instanceof Mob mob) {
                if (mob.getTarget() != null) mob.setTarget(null);
                if (c.from != null && now % 5 == 0 && mob instanceof PathfinderMob runner) {
                    Vec3 away = DefaultRandomPos.getPosAway(runner, 16, 7, c.from);
                    if (away != null) runner.getNavigation().moveTo(away.x, away.y, away.z, 1.4);
                }
            }
        }
        over.forEach(COWED::remove);
        TURNED.values().removeIf(t -> t.mob.isRemoved() || !t.victim.isAlive() || t.mob.level().getGameTime() >= t.until);
        BORROWED.values().removeIf(b -> {
            boolean done = b.who.isRemoved() || !b.who.isAlive() || b.who.level().getGameTime() >= b.until;
            if (done) giveBack(b);
            return done;
        });
        DRAWN.entrySet().removeIf(e -> e.getValue().isRemoved());
    }

    /** A cowed creature picks no target; one a decoy drew keeps after the decoy, not its maker. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void target(LivingChangeTargetEvent event) {
        LivingEntity who = event.getEntity();
        // A creature set upon its own kind keeps after its victim whatever else asks (even the kin rule, which runs first).
        Turned turned = TURNED.get(who.getUUID());
        if (turned != null && turned.victim.isAlive() && !cowed(who)) {
            if (event.getNewAboutToBeSetTarget() != turned.victim) event.setNewAboutToBeSetTarget(turned.victim);
            return;
        }
        if (event.getNewAboutToBeSetTarget() == null) return;
        if (cowed(who)) { event.setNewAboutToBeSetTarget(null); return; }
        ShapeDecoy decoy = DRAWN.get(who.getUUID());
        if (decoy != null && !decoy.isRemoved() && decoy.owner() != null && event.getNewAboutToBeSetTarget().getUUID().equals(decoy.owner()))
            event.setNewAboutToBeSetTarget(decoy);
    }

    /** A cowed creature's blows land on nothing; and every blow on a shapeshifter is remembered for Reflection. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void damage(LivingIncomingDamageEvent event) {
        Entity source = event.getSource().getEntity();
        if (source != null && source != event.getEntity() && cowed(source)) { event.setCanceled(true); return; }
        if (event.getEntity() instanceof ServerPlayer victim && source instanceof LivingEntity attacker && attacker != victim
                && ShapeshiftService.owns(victim))
            struck(victim, attacker);
    }

    /** Remembers who just hurt a shapeshifter, for Reflection (public so tests and previews can stage a hit). */
    public static void struck(ServerPlayer victim, LivingEntity attacker) {
        if (attacker instanceof ShapeDecoy) return;
        LAST_HIT.put(victim.getUUID(), new Hit(attacker.getUUID(), victim.serverLevel().getGameTime()));
    }
}
