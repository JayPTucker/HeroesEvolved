package com.jayptucker.heroesevolved.phasing;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

import static com.jayptucker.heroesevolved.phasing.DensityGeometry.*;

/**
 * Density's skill tree, built from interactions with the kit rather than stat bonuses. Three branches:
 * <ul>
 *   <li><b>Infiltrator</b> (hands and walls): Through the Wall, Pass Through, Inside Knowledge, Phase Carry → Ambush →
 *   Ghost Network.</li>
 *   <li><b>Density</b> (weight and the ground): Pile Driver, Harden → Rebound, Crater Landing, Sinkhole → Set in Stone
 *   → Entombed.</li>
 *   <li><b>Escape Artist</b> (Ghost and underground): Uproot, Fade Through, Grasp from Below, Phase Fall → Deep Fall →
 *   Untouchable.</li>
 * </ul>
 * The core moves ask this class at the points where they meet: {@link DensityService#pass} calls {@link #passed}, Reach
 * Through calls {@link #touched} and {@link #throughWall}, every shockwave asks {@link #crater}, a surfacing uppercut
 * calls {@link #uprooted}. Node ids are kept from the older tree, so purchases and prerequisites carry over.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class DensitySkills {
    public static final String THROUGH_WALL = "phasing_0_0", PASS_THROUGH = "phasing_0_1", INSIDE = "phasing_0_2",
            CARRY = "phasing_phase_carry", AMBUSH = "phasing_thicker_walls", NETWORK = "phasing_ghost_network",
            PILE_DRIVER = "phasing_1_0", HARDEN = "phasing_1_1", CRATER = "phasing_1_2",
            SINKHOLE = "phasing_phase_lock", SET_IN_STONE = "phasing_long_lock", ENTOMBED = "phasing_entombed",
            UPROOT = "phasing_2_0", FADE = "phasing_2_1", GRASP = "phasing_2_2",
            PHASE_FALL = "phasing_phase_fall", DEEP_FALL = "phasing_deep_fall", UNTOUCHABLE = "phasing_untouchable";

    /** A body a hand has been inside: whose hand, and until when (Inside Knowledge). */
    private record Knowing(UUID owner, long until) {}
    /** A wall left open behind you (Ghost Network): its two mouths and the way through. */
    private record Door(ServerPlayer owner, ServerLevel level, Vec3 from, Vec3 to, Vec3 normal, long until) {
        Vec3 near() { return from.add(normal.scale(.5)).add(0, 1, 0); }
        Vec3 far() { return to.subtract(normal.scale(.5)).add(0, 1, 0); }
    }
    private static final class Hardened {
        final ServerPlayer player; final long until; float stored;
        Hardened(ServerPlayer player, long until) { this.player = player; this.until = until; }
    }
    private record Sinkhole(ServerPlayer owner, ServerLevel level, Vec3 center, long opens, long until) {}
    private record Fade(ServerPlayer player, long until, int tier, Set<UUID> passed) {}

    private static final Map<UUID, Knowing> KNOWN = new HashMap<>();
    private static final Map<UUID, Long> PRIMED = new HashMap<>(), LOOSE = new HashMap<>(), PILED = new HashMap<>(),
            DOOR_USED = new HashMap<>(), SLIP_READY = new HashMap<>();
    private static final List<Door> DOORS = new ArrayList<>();
    private static final Map<UUID, Hardened> HARDENS = new HashMap<>();
    private static final List<Sinkhole> SINKHOLES = new ArrayList<>();
    private static final Map<UUID, Fade> FADES = new HashMap<>();

    private DensitySkills() {}

    private static long now(Entity e) { return e.getServer() == null ? 0 : e.getServer().overworld().getGameTime(); }
    private static boolean has(ServerPlayer p, String node) { return SkillTreeService.has(p, node); }

    // ------------------------------------------------------------------
    // Routing
    // ------------------------------------------------------------------

    public static boolean canUse(AbilityUseContext c, int branch) {
        ServerPlayer p = c.player();
        if (DensityService.sinking(p)) return false;
        return branch != 0 || passTarget(p) != null;
    }

    /** The three branch actives: Pass Through, Harden, Fade Through. Each poses itself. */
    public static boolean skill(AbilityUseContext c, int branch) {
        ServerPlayer p = c.player();
        return switch (branch) {
            case 0 -> passThrough(p, c.abilityLevel());
            case 1 -> harden(p);
            case 2 -> fade(p, c.abilityLevel());
            default -> false;
        };
    }

    public static boolean technique(AbilityUseContext c, String mechanic) {
        ServerPlayer p = c.player();
        return switch (mechanic) {
            case "phase_carry" -> carry(p);
            case "phase_lock" -> sinkhole(p);
            case "phase_fall" -> phaseFall(p, c.modifierHeld());
            default -> false;
        };
    }

    /** The second press of a branch active: Harden's Rebound. */
    public static boolean followUp(AbilityUseContext c, int branch, String mechanic) {
        return mechanic.isEmpty() && branch == 1 && rebound(c.player());
    }

    // ------------------------------------------------------------------
    // Infiltrator
    // ------------------------------------------------------------------

    static LivingEntity passTarget(ServerPlayer p) {
        return Targeting.livingEntityInSight(p, PASS_RANGE, t -> ExpansionSkills.hostile(p, t) && !DensityService.buried(t));
    }

    /** Pass Through: run straight through a body and come out behind it, facing it. Nothing inside it works right for a moment. */
    static boolean passThrough(ServerPlayer p, int tier) {
        LivingEntity t = passTarget(p);
        if (t == null) return false;
        ServerLevel level = p.serverLevel();
        Vec3 from = p.position(), to = landing(p, behind(p, t));
        if (to == null || !clear(p, from, to)) {
            p.displayClientMessage(Component.translatable("phasing.heroesevolved.no_passage"), true);
            return false;
        }
        p.teleportTo(level, to.x, to.y, to.z, yaw(to, t.position()), p.getXRot());
        p.resetFallDistance();
        t.invulnerableTime = 0;
        t.hurt(p.damageSources().indirectMagic(p, p), 4 + tier);
        touched(p, t, PASS_SEAL);
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
        ArmPosePayload.show(p, ArmPose.DENSITY_LUNGE, 10);
        show(level, Piece.DENSITY_PASS, t.position(), to, t.getBbHeight(), null);
        level.playSound(null, t.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1, .5F);
        level.playSound(null, t.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, .8F, 1.6F);
        return true;
    }

    /** An open place for a body at {@code at}, or a step up, or a little to either side. */
    private static Vec3 landing(Entity body, Vec3 at) {
        for (Vec3 v : new Vec3[] {at, at.add(0, 1, 0), at.add(.6, 0, 0), at.add(-.6, 0, 0), at.add(0, 0, .6), at.add(0, 0, -.6)})
            if (SpeedService.safe(body, v)) return v;
        return null;
    }

    /** True if nothing a phaser can't pass (bedrock, barriers, protected ground) lies between two places. */
    private static boolean clear(ServerPlayer p, Vec3 from, Vec3 to) {
        double length = from.distanceTo(to);
        for (double d = 0; d <= length; d += .5) {
            Vec3 at = from.lerp(to, length < 1.0E-4 ? 0 : d / length);
            for (double y = .1; y < p.getBbHeight(); y += .9)
                if (!Passage.passable(p, BlockPos.containing(at.add(0, y, 0)))) return false;
        }
        return true;
    }

    /**
     * A hand has been inside {@code target} (Reach Through, Pass Through, Fade Through, Grasp from Below): it can't heal
     * for {@code ticks}, twice that with Inside Knowledge, which also keeps the body in reach of your Ghost hands.
     */
    public static void touched(ServerPlayer p, LivingEntity target, int ticks) {
        boolean inside = has(p, INSIDE);
        int seal = inside ? ticks * 2 : ticks;
        DensityService.seal(target, seal);
        if (inside) KNOWN.put(target.getUUID(), new Knowing(p.getUUID(), now(p) + seal));
    }

    /** Inside Knowledge: true while {@code player}'s hand still knows its way into {@code target}. */
    public static boolean knows(ServerPlayer player, Entity target) {
        Knowing k = KNOWN.get(target.getUUID());
        return k != null && k.owner.equals(player.getUUID()) && k.until > now(player);
    }

    /** Through the Wall: Reach Through finds an enemy behind up to two blocks of wall, and lands harder for the surprise. */
    static boolean throughWall(ServerPlayer p, int tier) {
        if (!has(p, THROUGH_WALL)) return false;
        ServerLevel level = p.serverLevel();
        double reach = 4 + tier * .5 + WALL_BLOCKS;
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle(), end = eye.add(look.scale(reach));
        LivingEntity best = null;
        Vec3 bestHit = null;
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1), t -> ExpansionSkills.hostile(p, t))) {
            Optional<Vec3> hit = t.getBoundingBox().inflate(.3).clip(eye, end);
            if (hit.isPresent() && (bestHit == null || hit.get().distanceToSqr(eye) < bestHit.distanceToSqr(eye))) { best = t; bestHit = hit.get(); }
        }
        if (best == null) return false;
        Vec3 surface = null;
        int solid = 0;
        BlockPos last = null;
        double length = eye.distanceTo(bestHit);
        for (double d = .2; d < length; d += .2) {
            Vec3 at = eye.add(look.scale(d));
            BlockPos pos = BlockPos.containing(at);
            if (pos.equals(last)) continue;
            last = pos;
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) continue;
            if (!Passage.passable(p, pos) || ++solid > WALL_BLOCKS) return false;
            if (surface == null) surface = at;
        }
        if (solid == 0) return false;
        ArmPosePayload.show(p, ArmPose.REACH, 12);
        AbilityEffects.send(level, Kind.DENSITY_RIPPLE, surface, surface.subtract(look), .9);
        DensityService.reachInside(p, best, tier, WALL_BONUS);
        return true;
    }

    /**
     * Ambush (the second press of Reach Through while a hand is primed): free, waiting on no cooldown, and harder. A hand
     * is primed for a few seconds after you step out of a wall.
     */
    public static boolean ambush(AbilityUseContext c) {
        ServerPlayer p = c.player();
        Long until = PRIMED.get(p.getUUID());
        if (until == null || until <= now(p) || DensityService.sinking(p)) return false;
        LivingEntity t = Targeting.livingEntityInSight(p, 4 + c.abilityLevel() * .5, e -> ExpansionSkills.hostile(p, e));
        if (t == null) return false;
        PRIMED.remove(p.getUUID());
        ArmPosePayload.show(p, ArmPose.REACH, 12);
        DensityService.reachInside(p, t, c.abilityLevel(), AMBUSH_BONUS);
        show(p.serverLevel(), Piece.DENSITY_AMBUSH, DensityService.hand(p), t.getBoundingBox().getCenter(), 1, p);
        p.serverLevel().playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1, .7F);
        return true;
    }

    public static boolean primed(ServerPlayer p) { Long until = PRIMED.get(p.getUUID()); return until != null && until > now(p); }

    /** Every passage through a wall: Ambush primes the hand, Ghost Network leaves the wall open and you at Full Ghost. */
    static void passed(ServerPlayer p, Vec3 from, Vec3 to) {
        long now = now(p);
        if (has(p, AMBUSH)) {
            PRIMED.put(p.getUUID(), now + PRIMED_TICKS);
            showTo(p, Piece.DENSITY_PRIMED, p.position(), p.position(), 1, p);
        }
        if (has(p, NETWORK)) {
            DensityService.setNotch(p, DensityService.FULL_GHOST, 40);
            Vec3 dir = to.subtract(from);
            if (dir.lengthSqr() > 1.0E-4) {
                Door door = new Door(p, p.serverLevel(), from, to, dir.normalize(), now + DOOR_TICKS);
                DOORS.removeIf(d -> d.owner == p && d.from.distanceToSqr(from) < 1 && d.to.distanceToSqr(to) < 1);
                DOORS.add(door);
                showDoor(door, 1);
            }
        }
    }

    private static void showDoor(Door d, float scale) {
        show(d.level, Piece.DENSITY_DOOR, d.near(), d.near().add(d.normal), scale, null);
        show(d.level, Piece.DENSITY_DOOR, d.far(), d.far().add(d.normal), scale, null);
    }

    /** Phase Carry: through the wall ahead with up to three friends, who stay loose enough for shots to pass for a while. */
    static boolean carry(ServerPlayer p) {
        Vec3 heading = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
        if (heading.lengthSqr() < 1.0E-4) return false;
        heading = heading.normalize();
        Vec3 from = p.position(), to = Passage.through(p, heading, DensityService.wallLimit(p, DensityService.FULL_GHOST) + 1.5);
        if (to == null) { p.displayClientMessage(Component.translatable("phasing.heroesevolved.no_passage"), true); return false; }
        long now = now(p);
        for (LivingEntity friend : DensityService.carryFriends(p, to.subtract(from), 3, 3)) {
            LOOSE.put(friend.getUUID(), now + CARRY_GHOST);
            show(p.serverLevel(), Piece.DENSITY_FADE, friend.position(), friend.position(), .5F, friend);
        }
        ArmPosePayload.show(p, ArmPose.DENSITY_USHER, 14);
        DensityService.pass(p, from, to);
        Door door = new Door(p, p.serverLevel(), from, to, heading, now);
        showDoor(door, 1 + CARRIED);
        return true;
    }

    /** Phase Carry's friends: shots pass through them for a few seconds. */
    public static boolean loosened(Entity e) {
        Long until = LOOSE.get(e.getUUID());
        return until != null && until > now(e);
    }

    // ------------------------------------------------------------------
    // Density
    // ------------------------------------------------------------------

    /** Harden: Full Dense, free of upkeep, banking the weight of every blow you take. Press again for Rebound. */
    static boolean harden(ServerPlayer p) {
        DensityService.setNotch(p, DensityService.FULL_DENSE, HARDEN_TICKS);
        if (DensityService.notch(p) != DensityService.FULL_DENSE) return false;
        HARDENS.put(p.getUUID(), new Hardened(p, now(p) + HARDEN_TICKS));
        ArmPosePayload.show(p, ArmPose.DENSITY_BRACE, 12);
        show(p.serverLevel(), Piece.DENSITY_HARDEN, p.position(), p.position(), 1, p);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.DEEPSLATE_BRICKS_PLACE, SoundSource.PLAYERS, 1, .5F);
        return true;
    }

    public static float hardened(ServerPlayer p) { Hardened h = HARDENS.get(p.getUUID()); return h == null ? -1 : h.stored; }

    /** Rebound: every blow Harden banked comes back out of the ground as one shockwave. */
    static boolean rebound(ServerPlayer p) {
        Hardened h = HARDENS.get(p.getUUID());
        if (h == null || h.until <= now(p)) return false;
        HARDENS.remove(p.getUUID());
        double radius = REBOUND_RADIUS + Math.min(3, h.stored / 8);
        float damage = Math.min(18, 4 + h.stored * .6F);
        ArmPosePayload.show(p, ArmPose.DENSITY_BURST, 10);
        DensityService.shockwave(p, p.position(), radius, damage);
        show(p.serverLevel(), Piece.DENSITY_REBOUND, p.position(), p.position(), (float) radius, null);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1, .35F);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, .7F, .6F);
        return true;
    }

    /** Crater Landing: an enemy a shockwave of yours hits is rooted knee-deep instead of thrown. False if it wasn't. */
    static boolean crater(ServerPlayer p, LivingEntity t) {
        if (!has(p, CRATER)) return false;
        if (DensityService.embedded(t)) return true;
        if (!DensityService.embed(t, p, CRATER_TICKS, false, .3)) return false;
        Vec3 ground = DensityService.surfaceOf(t);
        show(p.serverLevel(), Piece.DENSITY_PILE, ground, ground, t.getBbWidth(), null);
        return true;
    }

    /** Sinkhole: after a short warning, the ground at a point you look at goes soft, and enemies in it sink waist-deep. */
    static boolean sinkhole(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        HitResult hit = p.pick(SINKHOLE_RANGE, 0, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            p.displayClientMessage(Component.translatable("density.heroesevolved.no_ground_there"), true);
            return false;
        }
        BlockPos ground = block.getBlockPos();
        if (!level.getBlockState(ground.above()).getCollisionShape(level, ground.above()).isEmpty() || !Passage.passable(p, ground)) {
            p.displayClientMessage(Component.translatable("density.heroesevolved.no_ground_there"), true);
            return false;
        }
        sinkholeAt(p, Vec3.atBottomCenterOf(ground.above()));
        return true;
    }

    /** Opens a Sinkhole on the ground at {@code center} (its warning first). */
    static void sinkholeAt(ServerPlayer p, Vec3 center) {
        ServerLevel level = p.serverLevel();
        long now = now(p);
        SINKHOLES.add(new Sinkhole(p, level, center, now + SINKHOLE_WARN, now + SINKHOLE_WARN + SINKHOLE_TICKS));
        ArmPosePayload.show(p, ArmPose.DENSITY_PRESS, 14);
        show(level, Piece.DENSITY_SINK_MARK, center, center, (float) SINKHOLE_RADIUS, null);
        level.playSound(null, BlockPos.containing(center), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1, .4F);
    }

    /**
     * Entombed (the second press of Embed, aimed at a body you hold): the ground closes right over it. It can't act, and
     * it takes damage every second until it is thrown back up.
     */
    public static boolean entomb(AbilityUseContext c) {
        ServerPlayer p = c.player();
        if (!has(p, ENTOMBED)) return false;
        LivingEntity t = Targeting.livingEntityInSight(p, SINKHOLE_RANGE, e -> DensityService.holder(e) == p && !DensityService.buried(e));
        if (t == null) return false;
        if (!DensityService.entomb(t, p, ENTOMB_TICKS)) return false;
        Vec3 ground = DensityService.surfaceOf(t);
        ArmPosePayload.show(p, ArmPose.DENSITY_CLOSE, 14);
        show(p.serverLevel(), Piece.DENSITY_ENTOMB, ground, ground, t.getBbWidth() + .4F, null);
        p.serverLevel().playSound(null, t.blockPosition(), SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1.2F, .4F);
        p.serverLevel().playSound(null, t.blockPosition(), SoundEvents.GRAVEL_PLACE, SoundSource.PLAYERS, 1, .5F);
        return true;
    }

    // ------------------------------------------------------------------
    // Escape Artist
    // ------------------------------------------------------------------

    /** Uproot: a body you surface under can't heal for a moment (one you held in the ground is torn out of it). */
    static void uprooted(ServerPlayer p, LivingEntity above, boolean torn) {
        touched(p, above, UPROOT_SEAL);
        show(p.serverLevel(), Piece.DENSITY_UPROOT, above.position(), above.position(), torn ? 2 : 1, null);
    }

    /** Fade Through: a few seconds at Full Ghost; every enemy you drift through is hurt and can't heal. */
    static boolean fade(ServerPlayer p, int tier) {
        DensityService.setNotch(p, DensityService.FULL_GHOST, FADE_TICKS);
        if (!DensityService.fullGhost(p)) return false;
        FADES.put(p.getUUID(), new Fade(p, now(p) + FADE_TICKS, tier, new HashSet<>()));
        ArmPosePayload.show(p, ArmPose.DENSITY_FADE, 16);
        show(p.serverLevel(), Piece.DENSITY_FADE, p.position(), p.position(), 1, p);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1, .4F);
        return true;
    }

    /** Grasp from Below (Reach Through while sunk): the nearest enemy above you is pulled waist-deep by the ankles. */
    static boolean grasp(ServerPlayer p, int tier) {
        ServerLevel level = p.serverLevel();
        List<LivingEntity> above = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(GRASP_REACH, 8, GRASP_REACH),
                t -> ExpansionSkills.hostile(p, t) && t.onGround() && t.getY() > p.getEyeY() - .5 && !DensityService.embedded(t));
        above.sort(Comparator.comparingDouble(p::distanceToSqr));
        ArmPosePayload.show(p, ArmPose.DENSITY_GRASP, 12);
        for (LivingEntity t : above) {
            if (!DensityService.embed(t, p, GRASP_TICKS + tier * 4, false, .5)) continue;
            touched(p, t, GRASP_TICKS);
            t.invulnerableTime = 0;
            t.hurt(p.damageSources().indirectMagic(p, p), 2 + tier * .5F);
            Vec3 ground = DensityService.surfaceOf(t);
            show(level, Piece.DENSITY_GRASP, ground, ground, t.getBbWidth(), null);
            level.playSound(null, t.blockPosition(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1, .5F);
            return true;
        }
        p.displayClientMessage(Component.translatable("density.heroesevolved.nothing_to_reach"), true);
        return false;
    }

    /** Phase Fall: through the floor to the open space below, with friends (Shift rises). Deep Fall lands it like a fall. */
    static boolean phaseFall(ServerPlayer p, boolean rise) {
        double range = has(p, DEEP_FALL) ? 32 : 16;
        Vec3 from = p.position(), to = Passage.through(p, new Vec3(0, rise ? 1 : -1, 0), range);
        if (to == null) { p.displayClientMessage(Component.translatable("phasing.heroesevolved.no_passage"), true); return false; }
        DensityService.carryFriends(p, to.subtract(from), 3, 3);
        ArmPosePayload.show(p, ArmPose.DENSITY_DROP, 14);
        show(p.serverLevel(), Piece.DENSITY_DROP, from, to, 1, null);
        DensityService.pass(p, from, to);
        // Deep Fall: dropped through at Dense, you land as heavily as if you had fallen the whole way.
        if (!rise && has(p, DEEP_FALL) && DensityService.dense(p) && from.y - to.y >= 3) DensityService.impact(p, (float) (from.y - to.y));
        return true;
    }

    // ------------------------------------------------------------------
    // Damage
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void damage(LivingIncomingDamageEvent event) {
        LivingEntity hurt = event.getEntity();
        var source = event.getSource();
        if (hurt instanceof ServerPlayer h) {
            Hardened hard = HARDENS.get(h.getUUID());
            if (hard != null && hard.until > now(h)) hard.stored = Math.min(HARDEN_CAP, hard.stored + event.getAmount());
        }
        // Only your own fists: not Reach Through's hand, not the ground's grip on an Entombed body.
        if (!(source.getEntity() instanceof ServerPlayer p) || source.getDirectEntity() != p || hurt == p
                || !source.is(DamageTypes.PLAYER_ATTACK)) return;
        int notch = DensityService.notch(p);
        // Inside Knowledge: a Ghost's hand isn't weak against a body it has already been inside.
        if (notch == DensityService.GHOST && knows(p, hurt)) event.setAmount(event.getAmount() * 2);
        // Set in Stone: each blow at Dense on a body you hold sets it a second longer.
        if (notch > 0 && has(p, SET_IN_STONE) && DensityService.holder(hurt) == p) DensityService.extend(hurt, SET_STEP, SET_MAX);
        // Pile Driver: a Full Dense punch drives a standing enemy into the ground.
        if (notch == DensityService.FULL_DENSE && has(p, PILE_DRIVER) && hurt.onGround() && !DensityService.embedded(hurt)
                && ExpansionSkills.hostile(p, hurt) && PILED.getOrDefault(hurt.getUUID(), 0L) <= now(p)
                && DensityService.embed(hurt, p, PILE_TICKS, false, .3)) {
            PILED.put(hurt.getUUID(), now(p) + PILE_COOLDOWN);
            Vec3 ground = DensityService.surfaceOf(hurt);
            show(p.serverLevel(), Piece.DENSITY_PILE, ground, ground, hurt.getBbWidth(), null);
            p.serverLevel().playSound(null, hurt.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, .6F, .5F);
        }
    }

    /** Untouchable: a blow that would take you below a third of your health passes through as you slip to Full Ghost. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void slip(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.isCanceled() || !has(p, UNTOUCHABLE) || !DensityService.isOwner(p)) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (p.getHealth() - event.getAmount() >= p.getMaxHealth() * SLIP_AT) return;
        long now = now(p);
        if (SLIP_READY.getOrDefault(p.getUUID(), 0L) > now) return;
        SLIP_READY.put(p.getUUID(), now + SLIP_COOLDOWN);
        event.setCanceled(true);
        DensityService.setNotch(p, DensityService.FULL_GHOST, SLIP_TICKS);
        ArmPosePayload.show(p, ArmPose.DENSITY_SLIP, 12);
        show(p.serverLevel(), Piece.DENSITY_SLIP, p.position(), p.position(), 1, p);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1, .5F);
        p.displayClientMessage(Component.translatable("density.heroesevolved.slip").withStyle(ChatFormatting.AQUA), true);
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        tickDoors(now);
        tickHarden(now);
        tickSinkholes(now);
        tickFades(now);
        if (now % 100 == 0) {
            KNOWN.values().removeIf(k -> k.until <= now);
            for (Map<UUID, Long> map : List.of(PRIMED, LOOSE, PILED, DOOR_USED)) map.values().removeIf(until -> until <= now);
        }
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }

    private static void tickDoors(long now) {
        for (Door d : List.copyOf(DOORS)) {
            if (now >= d.until || d.owner.isRemoved() || d.owner.level() != d.level) { DOORS.remove(d); continue; }
            if (now % 10 == 0) showDoor(d, 1);
            for (LivingEntity e : d.level.getEntitiesOfClass(LivingEntity.class, new AABB(d.from, d.to).inflate(DOOR_REACH + 1),
                    e -> e != d.owner && (StyleOps.isFriend(d.owner, e) || ExpansionSkills.friendly(d.owner, e))
                            && !e.isPassenger() && !EntityCarryService.isBeingCarried(e))) {
                if (DOOR_USED.getOrDefault(e.getUUID(), 0L) > now) continue;
                if (walksInto(e, d.from, d.normal, d.owner)) through(e, d.to, now);
                else if (walksInto(e, d.to, d.normal.reverse(), d.owner)) through(e, d.from, now);
            }
        }
    }

    /** A friend at a door's mouth heading into the wall: a player facing it, a pet whose owner is on the other side. */
    private static boolean walksInto(LivingEntity e, Vec3 mouth, Vec3 normal, ServerPlayer owner) {
        if (e.position().subtract(mouth).horizontalDistance() > DOOR_REACH || Math.abs(e.getY() - mouth.y) > 1.2) return false;
        if (e instanceof ServerPlayer player) return !player.isShiftKeyDown() && player.getLookAngle().multiply(1, 0, 1).normalize().dot(normal) > .3;
        return owner.position().subtract(mouth).dot(normal) > 0;
    }

    private static void through(LivingEntity e, Vec3 to, long now) {
        if (!SpeedService.safe(e, to)) return;
        Vec3 from = e.position();
        if (e instanceof ServerPlayer player) player.teleportTo(player.serverLevel(), to.x, to.y, to.z, player.getYRot(), player.getXRot());
        else e.teleportTo(to.x, to.y, to.z);
        e.resetFallDistance();
        DOOR_USED.put(e.getUUID(), now + 40);
        if (e.level() instanceof ServerLevel level) {
            AbilityEffects.send(level, Kind.DENSITY_RIPPLE, from.add(0, 1, 0), to.add(0, 1, 0), .9);
            level.playSound(null, BlockPos.containing(to), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, .7F, .6F);
        }
    }

    private static void tickHarden(long now) {
        for (var it = HARDENS.entrySet().iterator(); it.hasNext();) {
            Hardened h = it.next().getValue();
            ServerPlayer p = h.player;
            if (gone(p) || now >= h.until || DensityService.notch(p) != DensityService.FULL_DENSE) { it.remove(); continue; }
            if (now % 10 == 0) {
                show(p.serverLevel(), Piece.DENSITY_HARDEN, p.position(), p.position(), 1 + h.stored / 8, p);
                ArmPosePayload.show(p, ArmPose.DENSITY_BRACE, 12);
            }
        }
    }

    private static void tickSinkholes(long now) {
        for (Sinkhole s : List.copyOf(SINKHOLES)) {
            if (now >= s.until || s.owner.isRemoved() || s.owner.level() != s.level) { SINKHOLES.remove(s); continue; }
            if (now < s.opens) continue;
            if (now == s.opens) s.level.playSound(null, BlockPos.containing(s.center), SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 1, .9F);
            if ((now - s.opens) % 10 == 0) show(s.level, Piece.DENSITY_SINKHOLE, s.center, s.center, (float) SINKHOLE_RADIUS, null);
            if ((now - s.opens) % 5 != 0) continue;
            for (LivingEntity t : s.level.getEntitiesOfClass(LivingEntity.class, new AABB(s.center, s.center).inflate(SINKHOLE_RADIUS, 2, SINKHOLE_RADIUS),
                    t -> ExpansionSkills.hostile(s.owner, t) && !DensityService.embedded(t)
                            && t.position().subtract(s.center).horizontalDistance() <= SINKHOLE_RADIUS && Math.abs(t.getY() - s.center.y) < 1.5))
                DensityService.embed(t, s.owner, SINKHOLE_HOLD, false, .5);
        }
    }

    private static void tickFades(long now) {
        for (var it = FADES.entrySet().iterator(); it.hasNext();) {
            Fade f = it.next().getValue();
            ServerPlayer p = f.player;
            if (gone(p) || now >= f.until || !DensityService.fullGhost(p)) { it.remove(); continue; }
            for (LivingEntity t : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(.15),
                    t -> ExpansionSkills.hostile(p, t) && !f.passed.contains(t.getUUID()))) {
                f.passed.add(t.getUUID());
                t.invulnerableTime = 0;
                t.hurt(p.damageSources().indirectMagic(p, p), 3 + f.tier * .5F);
                touched(p, t, FADE_SEAL);
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
                show(p.serverLevel(), Piece.DENSITY_PASS, t.position(), t.position().add(p.getLookAngle().multiply(1, 0, 1)), t.getBbHeight(), null);
                p.serverLevel().playSound(null, t.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, .7F, 1.6F);
            }
        }
    }

    private static boolean gone(ServerPlayer p) { return p.isRemoved() || !p.isAlive(); }

    // ------------------------------------------------------------------
    // Cleanup
    // ------------------------------------------------------------------

    static void drop(ServerPlayer p) {
        UUID id = p.getUUID();
        PRIMED.remove(id); HARDENS.remove(id); FADES.remove(id); LOOSE.remove(id);
        DOORS.removeIf(d -> d.owner == p);
        SINKHOLES.removeIf(s -> s.owner == p);
        KNOWN.values().removeIf(k -> k.owner.equals(id));
    }

    static void clear() {
        KNOWN.clear(); PRIMED.clear(); LOOSE.clear(); PILED.clear(); DOOR_USED.clear(); SLIP_READY.clear();
        DOORS.clear(); HARDENS.clear(); SINKHOLES.clear(); FADES.clear();
    }
}
