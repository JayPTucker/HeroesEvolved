package com.jayptucker.heroesevolved.invisibility;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.cryo.TemporaryBlocks;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.flight.FlightSkillService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Force fields: invisible light made solid. The server owns every field: the Lens Shield and its Force Push, walkable
 * Force Bridges and Barriers (temporary barrier blocks that are drawn by the client), bubbles that hold foes or shelter
 * friends, the crush and the roll, Force Nova, the riding disc, Pinpoint and the Safety Net.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class ForceFieldService {
    // ---------------------------------------------------------------- tuning
    static final int SHIELD_TICKS = 60, WIDE_SHIELD_TICKS = 100, MAX_PRESSURE = 6;
    static final int BRIDGE_TICKS = 200, STEADY_BRIDGE_TICKS = 400, BRIDGE_LENGTH = 12, MAX_BRIDGES = 2;
    static final int WALL_TICKS = 200, WALL_HALF_WIDTH = 3, WALL_HEIGHT = ForceGeometry.WALL_HEIGHT;
    static final int CONTAIN_TICKS = 100, SEALED_TICKS = 160, SHELL_TICKS = 60, SHELTER_TICKS = 120, CRUSH_TICKS = 8;
    static final int ROLL_TICKS = 24, NOVA_WINDUP = 20, DISC_TICKS = 160, STEADY_DISC_TICKS = 320, NET_COOLDOWN = 200;
    static final double ROLL_SPEED = 1.15, DISC_SPEED = .85, PINPOINT_RANGE = 24, CONTAIN_RANGE = 20;

    // ---------------------------------------------------------------- state
    private static final class Shield {
        final ServerPlayer owner; final long until; final double radius; int pressure;
        Shield(ServerPlayer owner, long until, double radius) { this.owner = owner; this.until = until; this.radius = radius; }
    }
    private static final class Wave {
        final ServerPlayer owner; final ServerLevel level; final Vec3 start, dir; final double range, radius, knock; final float damage;
        final boolean unseen; double travelled; final Set<Integer> hit = new HashSet<>();
        Wave(ServerPlayer owner, Vec3 start, Vec3 dir, double range, double radius, float damage, double knock, boolean unseen) {
            this.owner = owner; this.level = owner.serverLevel(); this.start = start; this.dir = dir; this.range = range;
            this.radius = radius; this.damage = damage; this.knock = knock; this.unseen = unseen;
        }
    }
    enum Kind { BRIDGE, WALL }
    private record Construct(ServerPlayer owner, ServerLevel level, Kind kind, List<BlockPos> cells, Vec3 origin, Vec3 target,
                             float scale, long began, long until) {}
    private static final class Bubble {
        final ServerPlayer owner; final LivingEntity target; final ServerLevel level; final boolean shelter; final long began;
        long until, crushAt = -1, rollUntil = -1; Vec3 anchor, roll; boolean unseen;
        final Set<Integer> rolledOver = new HashSet<>();
        Bubble(ServerPlayer owner, LivingEntity target, boolean shelter, long began, long until) {
            this.owner = owner; this.target = target; this.level = (ServerLevel) target.level(); this.shelter = shelter;
            this.began = began; this.until = until; this.anchor = target.position();
        }
        boolean holding() { return !shelter && crushAt < 0 && rollUntil < 0; }
    }
    private record Nova(ServerPlayer owner, long release, boolean unseen) {}
    private record Disc(ServerPlayer owner, long until) {}

    private static final Map<UUID, Shield> SHIELDS = new HashMap<>();
    private static final List<Wave> WAVES = new ArrayList<>();
    private static final List<Construct> CONSTRUCTS = new ArrayList<>();
    private static final Map<UUID, Bubble> BUBBLES = new LinkedHashMap<>();
    private static final Map<UUID, Nova> NOVAS = new HashMap<>();
    private static final Map<UUID, Disc> DISCS = new HashMap<>();
    private static final Map<UUID, Long> NET_READY = new HashMap<>();
    /** Players standing on a Force Bridge step up its stairs without jumping. */
    private static final Set<ServerPlayer> STEPPING = new HashSet<>();
    private static final net.minecraft.resources.ResourceLocation STEP_ID =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "force_bridge_step");
    /** Set while a force move deals its own damage, so the hit isn't mistaken for a melee swing. */
    private static boolean striking;

    private ForceFieldService() {}

    private static long now(ServerPlayer p) { return p.serverLevel().getGameTime(); }
    private static boolean has(ServerPlayer p, String id) { return SkillTreeService.has(p, id); }
    private static int level(ServerPlayer p) { return Math.clamp(PlayerProgressionService.getLevel(p), 1, 5); }

    // ---------------------------------------------------------------- friend and foe

    /** Yourself, allies, your own pets, and other players who consent by crouching. */
    public static boolean friend(ServerPlayer p, LivingEntity t) {
        return FlightSkillService.isFriend(p, t) || t instanceof ServerPlayer && ExpansionSkills.friendly(p, t);
    }
    public static boolean foe(ServerPlayer p, LivingEntity t) { return !friend(p, t) && FlightSkillService.hostile(p, t); }

    // ---------------------------------------------------------------- queries (tests, HUD, other powers)

    public static boolean shielding(ServerPlayer p) { return SHIELDS.containsKey(p.getUUID()); }
    public static int pressure(ServerPlayer p) { Shield s = SHIELDS.get(p.getUUID()); return s == null ? 0 : s.pressure; }
    public static boolean contained(LivingEntity e) { Bubble b = BUBBLES.get(e.getUUID()); return b != null && !b.shelter; }
    public static boolean sheltered(LivingEntity e) { Bubble b = BUBBLES.get(e.getUUID()); return b != null && b.shelter; }
    public static boolean rolling(LivingEntity e) { Bubble b = BUBBLES.get(e.getUUID()); return b != null && b.rollUntil >= 0; }
    public static boolean crushing(LivingEntity e) { Bubble b = BUBBLES.get(e.getUUID()); return b != null && b.crushAt >= 0; }
    public static int constructs(ServerPlayer p, Kind kind) {
        return (int) CONSTRUCTS.stream().filter(c -> c.owner == p && c.kind == kind).count();
    }
    public static int bridges(ServerPlayer p) { return constructs(p, Kind.BRIDGE); }
    public static int walls(ServerPlayer p) { return constructs(p, Kind.WALL); }
    public static boolean charging(ServerPlayer p) { return NOVAS.containsKey(p.getUUID()); }
    public static boolean riding(ServerPlayer p) { return DISCS.containsKey(p.getUUID()); }
    public static boolean stepping(ServerPlayer p) { return STEPPING.contains(p); }

    // ---------------------------------------------------------------- drawing

    /**
     * Shows a construct. With Unseen Constructs, fields are drawn only for their maker and friends: foes walk into
     * walls they cannot see.
     */
    private static void show(ServerPlayer p, Piece piece, Vec3 origin, Vec3 target, float scale, Entity bound, boolean construct) {
        if (!construct || !has(p, InvisibilitySkills.UNSEEN_CONSTRUCTS)) {
            SetPiecePayload.send(p.serverLevel(), piece, origin, target, scale, bound);
            return;
        }
        var payload = new SetPiecePayload(piece.ordinal(), origin, target, scale, bound == null ? -1 : bound.getId(),
                p.getRandom().nextInt(), 0);
        for (ServerPlayer viewer : p.serverLevel().players())
            if (friend(p, viewer) && viewer.position().distanceToSqr(origin) < 128 * 128) PacketDistributor.sendToPlayer(viewer, payload);
    }
    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    // ---------------------------------------------------------------- damage

    /** Force moves cast from full concealment with Unseen Force hit 40% harder and do not give their caster away. */
    private static boolean unseenCast(ServerPlayer p) {
        return has(p, InvisibilitySkills.UNSEEN_FORCE) && InvisibilityService.isUnseen(p);
    }
    private static void cast(ServerPlayer p, boolean unseen) { if (!unseen) InvisibilityService.forceAttack(p); }
    private static void hurt(ServerPlayer p, LivingEntity t, float amount, boolean unseen) {
        striking = true;
        try { t.hurt(p.damageSources().playerAttack(p), amount * (unseen ? 1.4F : 1)); }
        finally { striking = false; }
    }
    public static boolean striking() { return striking; }
    private static void throwAway(LivingEntity t, Vec3 push) {
        double resist = 1 - Math.clamp(t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0, 1);
        t.setDeltaMovement(t.getDeltaMovement().scale(.3).add(push.scale(resist)));
        t.hurtMarked = true;
        if (t instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
    }

    // ================================================================ H: Lens Shield → Force Push

    public static boolean raiseShield(ServerPlayer p) {
        if (!PowerRules.canAct(p)) return false;
        boolean wide = has(p, InvisibilitySkills.WIDE_LENS);
        Shield shield = new Shield(p, now(p) + (wide ? WIDE_SHIELD_TICKS : SHIELD_TICKS),
                wide ? ForceGeometry.WIDE_SHIELD_RADIUS : ForceGeometry.SHIELD_RADIUS);
        SHIELDS.put(p.getUUID(), shield);
        ArmPosePayload.show(p, ArmPose.FORCE_SHIELD, wide ? WIDE_SHIELD_TICKS : SHIELD_TICKS);
        showShield(shield, true);
        sound(p.serverLevel(), p.getEyePosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1, 1.5F);
        sound(p.serverLevel(), p.getEyePosition(), SoundEvents.BEACON_ACTIVATE, .5F, 1.9F);
        return true;
    }
    private static void showShield(Shield s, boolean first) {
        show(s.owner, Piece.FORCE_SHIELD, s.owner.getEyePosition(), new Vec3(s.pressure, first ? 1 : 0, 0), (float) s.radius, s.owner, true);
    }

    /** The second press: the shield leaves the hands as a travelling wall of force. Free, like any follow-up. */
    public static boolean push(ServerPlayer p) {
        Shield shield = SHIELDS.remove(p.getUUID());
        if (shield == null || !PowerRules.canAct(p)) return false;
        boolean hard = has(p, InvisibilitySkills.HARD_PUSH), unseen = unseenCast(p);
        Vec3 eye = p.getEyePosition(), dir = p.getLookAngle().normalize();
        double range = hard ? ForceGeometry.HARD_PUSH_RANGE : ForceGeometry.PUSH_RANGE;
        HitResult wall = p.serverLevel().clip(new ClipContext(eye, eye.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (wall.getType() != HitResult.Type.MISS) range = Math.max(1.5, wall.getLocation().distanceTo(eye) + .5);
        float damage = 4 + level(p) + 2 * shield.pressure;
        double knock = (1.3 + .15 * shield.pressure) * (hard ? 1.5 : 1);
        WAVES.add(new Wave(p, eye, dir, range, shield.radius + .45, damage, knock, unseen));
        show(p, Piece.FORCE_PUSH, ForceGeometry.shieldCentre(eye, dir), eye.add(dir.scale(range)), (float) shield.radius, p, false);
        ArmPosePayload.show(p, ArmPose.FORCE_PUSH, 12);
        sound(p.serverLevel(), eye, SoundEvents.WIND_CHARGE_BURST.value(), 1.2F, .75F);
        sound(p.serverLevel(), eye, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, .6F);
        CameraShakePayload.sendAround(p.serverLevel(), eye, 24, .25F + shield.pressure * .05F, 6);
        cast(p, unseen);
        return true;
    }

    private static void tickWaves() {
        for (Wave w : List.copyOf(WAVES)) {
            if (w.owner.isRemoved() || w.owner.level() != w.level) { WAVES.remove(w); continue; }
            double from = w.travelled, to = Math.min(w.range, w.travelled + ForceGeometry.PUSH_SPEED);
            w.travelled = to;
            Vec3 a = w.start.add(w.dir.scale(from)), b = w.start.add(w.dir.scale(to));
            AABB box = new AABB(a, b).inflate(w.radius + 1);
            for (LivingEntity t : w.level.getEntitiesOfClass(LivingEntity.class, box, t -> t != w.owner && t.isAlive())) {
                Vec3 centre = ForceGeometry.bubbleCentre(t);
                double along = centre.subtract(w.start).dot(w.dir);
                if (along < from - 1 || along > to + .6) continue;
                double across = centre.subtract(w.start.add(w.dir.scale(along))).length();
                if (across > w.radius + t.getBbWidth() * .5 || !w.hit.add(t.getId())) continue;
                Bubble bubble = BUBBLES.get(t.getUUID());
                if (bubble != null && bubble.holding()) { startRoll(bubble, w.dir); continue; }
                if (!foe(w.owner, t)) continue;
                hurt(w.owner, t, w.damage, w.unseen);
                throwAway(t, w.dir.scale(w.knock).add(0, .35, 0));
            }
            for (Projectile shot : w.level.getEntitiesOfClass(Projectile.class, box, s -> hostileShot(w.owner, s)))
                deflect(w.owner, shot, shot.position(), w.dir.reverse(), w.dir);
            if (to >= w.range) WAVES.remove(w);
        }
    }

    // ================================================================ Z: Force Bridge

    public static boolean bridge(ServerPlayer p) {
        if (!PowerRules.canAct(p)) return false;
        Direction dir = Direction.fromYRot(p.getYRot());
        double slope = Mth.clamp(Math.tan(Math.toRadians(-p.getXRot())), -1, 1);
        if (Math.abs(slope) < .15) slope = 0;
        boolean grounded = p.onGround();
        double feet = p.getY();
        BlockPos base = p.blockPosition();
        Vec3 origin = new Vec3(base.getX() + .5 + (grounded ? dir.getStepX() : 0), feet, base.getZ() + .5 + (grounded ? dir.getStepZ() : 0));
        int ticks = has(p, InvisibilitySkills.STEADY_DISC) ? STEADY_BRIDGE_TICKS : BRIDGE_TICKS;
        List<BlockPos> cells = new ArrayList<>();
        int length = 0;
        for (int i = 0; i < BRIDGE_LENGTH; i++) {
            BlockPos centre = ForceGeometry.bridgeCell(origin, dir, slope, i, 0);
            if (blocked(p.serverLevel(), centre.above()) || blocked(p.serverLevel(), centre.above(2))) break;
            for (int lane = -1; lane <= 1; lane++) {
                BlockPos cell = ForceGeometry.bridgeCell(origin, dir, slope, i, lane);
                if (TemporaryBlocks.place(p.serverLevel(), cell, Blocks.BARRIER.defaultBlockState(), ticks + 40, p)) cells.add(cell);
            }
            length = i + 1;
        }
        if (cells.isEmpty()) return false;
        Vec3 target = origin.add(dir.getStepX() * (length - 1), slope * (length - 1), dir.getStepZ() * (length - 1));
        while (bridges(p) >= MAX_BRIDGES)
            CONSTRUCTS.stream().filter(c -> c.owner == p && c.kind == Kind.BRIDGE).findFirst().ifPresent(ForceFieldService::dissolve);
        Construct bridge = new Construct(p, p.serverLevel(), Kind.BRIDGE, cells, origin, target, 3, now(p), now(p) + ticks);
        CONSTRUCTS.add(bridge);
        show(p, Piece.FORCE_FORM, origin, target, 3, null, true);
        show(p, Piece.FORCE_BRIDGE, origin, target, 3, null, true);
        ArmPosePayload.show(p, ArmPose.FORCE_BUILD, 14);
        sound(p.serverLevel(), origin, SoundEvents.AMETHYST_BLOCK_RESONATE, 1, 1.8F);
        sound(p.serverLevel(), origin, SoundEvents.BEACON_POWER_SELECT, .4F, 1.8F);
        if (!grounded) p.resetFallDistance();
        return true;
    }
    private static boolean blocked(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && !TemporaryBlocks.isTemporary(level, pos);
    }

    // ---------------------------------------------------------------- Force Barrier (Aegis active)

    public static boolean barrier(ServerPlayer p) {
        if (!PowerRules.canAct(p)) return false;
        Vec3 eye = p.getEyePosition();
        HitResult hit = p.serverLevel().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(20)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 at = hit.getLocation().subtract(p.getLookAngle().scale(.2));
        BlockPos ground = BlockPos.containing(at);
        for (int i = 0; i < 6 && !blocked(p.serverLevel(), ground.below()); i++) ground = ground.below();
        Direction facing = Direction.fromYRot(p.getYRot()).getOpposite();
        Direction side = facing.getClockWise();
        List<BlockPos> cells = new ArrayList<>();
        for (int w = -WALL_HALF_WIDTH; w <= WALL_HALF_WIDTH; w++)
            for (int h = 0; h < WALL_HEIGHT; h++) {
                BlockPos cell = ground.relative(side, w).above(h);
                if (TemporaryBlocks.place(p.serverLevel(), cell, Blocks.BARRIER.defaultBlockState(), WALL_TICKS + 40, p)) cells.add(cell);
            }
        if (cells.isEmpty()) return false;
        Vec3 origin = Vec3.atBottomCenterOf(ground), target = origin.add(facing.getStepX(), 0, facing.getStepZ());
        CONSTRUCTS.add(new Construct(p, p.serverLevel(), Kind.WALL, cells, origin, target, WALL_HALF_WIDTH, now(p), now(p) + WALL_TICKS));
        show(p, Piece.FORCE_FORM, origin, target, ForceGeometry.SHELTER_FLAG + WALL_HALF_WIDTH, null, true);
        show(p, Piece.FORCE_WALL, origin, target, WALL_HALF_WIDTH, null, true);
        ArmPosePayload.show(p, ArmPose.FORCE_BUILD, 14);
        sound(p.serverLevel(), origin, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 1.2F);
        sound(p.serverLevel(), origin, SoundEvents.BEACON_ACTIVATE, .5F, 1.6F);
        return true;
    }

    private static void dissolve(Construct c) {
        CONSTRUCTS.remove(c);
        for (BlockPos cell : c.cells) TemporaryBlocks.restore(c.level, cell, null);
    }
    private static void tickConstructs(long time) {
        if (time % 5 == 0) tickSteps();
        for (Construct c : List.copyOf(CONSTRUCTS)) {
            if (time >= c.until || c.owner.isRemoved() || c.owner.level() != c.level) { dissolve(c); continue; }
            if ((time - c.began) % 10 == 0 && time != c.began)
                show(c.owner, c.kind == Kind.BRIDGE ? Piece.FORCE_BRIDGE : Piece.FORCE_WALL, c.origin, c.target, c.scale, null, true);
            if (c.kind == Kind.WALL) {
                AABB box = new AABB(c.origin, c.origin).inflate(WALL_HALF_WIDTH + 1.5, 0, WALL_HALF_WIDTH + 1.5).expandTowards(0, WALL_HEIGHT + 1, 0);
                Vec3 normal = c.target.subtract(c.origin).normalize();
                for (Projectile shot : c.level.getEntitiesOfClass(Projectile.class, box, s -> hostileShot(c.owner, s))) {
                    Vec3 v = shot.getDeltaMovement();
                    Vec3 face = v.dot(normal) > 0 ? normal.reverse() : normal;
                    deflect(c.owner, shot, shot.position(), face, null);
                }
            }
        }
    }

    /** A bridge's steps are a block high: friends on one get a step-up so they walk its stairs like a ramp. */
    private static void tickSteps() {
        Set<ServerPlayer> on = new HashSet<>();
        for (Construct c : CONSTRUCTS) {
            if (c.kind != Kind.BRIDGE) continue;
            AABB box = new AABB(c.cells.getFirst());
            for (BlockPos cell : c.cells) box = box.minmax(new AABB(cell));
            box = box.inflate(.5, 0, .5).expandTowards(0, 2, 0);
            for (ServerPlayer p : c.level.getEntitiesOfClass(ServerPlayer.class, box, p -> friend(c.owner, p))) on.add(p);
        }
        for (ServerPlayer p : List.copyOf(STEPPING))
            if (!on.contains(p)) { var step = p.getAttribute(Attributes.STEP_HEIGHT); if (step != null) step.removeModifier(STEP_ID); STEPPING.remove(p); }
        for (ServerPlayer p : on) {
            var step = p.getAttribute(Attributes.STEP_HEIGHT);
            if (step == null) continue;
            step.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(STEP_ID, .5,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            STEPPING.add(p);
        }
    }

    // ================================================================ X: Bubble → Crush

    /** Aimed at a foe: hold it in a sphere. Aimed at a friend or nothing: shelter yourself and those close by. */
    public static boolean bubble(ServerPlayer p) {
        if (!PowerRules.canAct(p)) return false;
        LivingEntity aimed = Targeting.livingEntityInSight(p, CONTAIN_RANGE, t -> !t.isSpectator());
        if (aimed != null && !friend(p, aimed)) {
            boolean unseen = unseenCast(p);
            contain(p, aimed, has(p, InvisibilitySkills.TIGHT_SEAL) ? SEALED_TICKS : CONTAIN_TICKS, unseen);
            ArmPosePayload.show(p, ArmPose.FORCE_BUBBLE, 16);
            cast(p, unseen);
            return true;
        }
        List<LivingEntity> covered = new ArrayList<>();
        covered.add(p);
        if (aimed != null && aimed != p) covered.add(aimed);
        for (LivingEntity t : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(ForceGeometry.SHELTER_REACH),
                t -> t != p && t.isAlive() && friend(p, t) && !covered.contains(t))) if (covered.size() < 6) covered.add(t);
        for (LivingEntity t : covered) shelter(p, t, SHELTER_TICKS);
        ArmPosePayload.show(p, ArmPose.FORCE_BUBBLE, 16);
        sound(p.serverLevel(), p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1, 1.1F);
        sound(p.serverLevel(), p.position(), SoundEvents.BEACON_ACTIVATE, .5F, 1.4F);
        return true;
    }

    public static void contain(ServerPlayer p, LivingEntity t, int ticks, boolean unseen) {
        Bubble old = BUBBLES.remove(t.getUUID());
        Bubble b = new Bubble(p, t, false, now(p), now(p) + ticks);
        b.unseen = unseen;
        BUBBLES.put(t.getUUID(), b);
        t.setDeltaMovement(Vec3.ZERO);
        if (t instanceof Mob mob) { mob.getNavigation().stop(); mob.setTarget(null); }
        show(p, Piece.FORCE_BUBBLE, ForceGeometry.bubbleCentre(t), ForceGeometry.bubbleCentre(t), (float) ForceGeometry.bubbleRadius(t), t, true);
        if (old == null) sound((ServerLevel) t.level(), t.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.6F);
    }
    private static void shelter(ServerPlayer p, LivingEntity t, int ticks) {
        Bubble old = BUBBLES.get(t.getUUID());
        if (old != null && !old.shelter) return;
        BUBBLES.put(t.getUUID(), new Bubble(p, t, true, now(p), now(p) + ticks));
        show(p, Piece.FORCE_BUBBLE, ForceGeometry.bubbleCentre(t), ForceGeometry.bubbleCentre(t),
                ForceGeometry.SHELTER_FLAG + (float) ForceGeometry.bubbleRadius(t), t, true);
    }

    /** The second press: every foe this caster is holding is crushed as its bubble closes. */
    public static boolean crush(ServerPlayer p) {
        boolean any = false;
        for (Bubble b : BUBBLES.values())
            if (b.owner == p && b.holding()) {
                b.crushAt = now(p); any = true;
                show(p, Piece.FORCE_CRUSH, ForceGeometry.bubbleCentre(b.target), ForceGeometry.bubbleCentre(b.target),
                        (float) ForceGeometry.bubbleRadius(b.target), b.target, false);
            }
        if (!any) return false;
        ArmPosePayload.show(p, ArmPose.FORCE_CRUSH, 14);
        sound(p.serverLevel(), p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1, .55F);
        return true;
    }
    public static boolean holdingAny(ServerPlayer p) {
        return BUBBLES.values().stream().anyMatch(b -> b.owner == p && b.holding());
    }

    private static void startRoll(Bubble b, Vec3 dir) {
        Vec3 flat = new Vec3(dir.x, Math.max(dir.y, 0) * .4, dir.z);
        if (flat.lengthSqr() < 1e-4) flat = new Vec3(0, 0, 1);
        b.roll = flat.normalize();
        b.rollUntil = b.level.getGameTime() + ROLL_TICKS;
        sound(b.level, b.target.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, .8F);
    }

    private static void endBubble(Bubble b, boolean pop) {
        BUBBLES.remove(b.target.getUUID(), b);
        if (pop) {
            Vec3 c = ForceGeometry.bubbleCentre(b.target);
            show(b.owner, Piece.FORCE_SHATTER, c, c, (float) ForceGeometry.bubbleRadius(b.target), b.target, false);
            sound(b.level, c, SoundEvents.GLASS_BREAK, .9F, 1.5F);
        }
    }

    private static void tickBubbles(long time) {
        for (Bubble b : List.copyOf(BUBBLES.values())) {
            LivingEntity t = b.target;
            if (!t.isAlive() || t.isRemoved() || t.level() != b.level || b.owner.isRemoved()) { endBubble(b, false); continue; }
            Vec3 centre = ForceGeometry.bubbleCentre(t);
            double radius = ForceGeometry.bubbleRadius(t);
            if (b.shelter) {
                if (time >= b.until) { endBubble(b, false); continue; }
                for (Projectile shot : b.level.getEntitiesOfClass(Projectile.class, t.getBoundingBox().inflate(radius + 1.2),
                        s -> hostileShot(b.owner, s) && s.position().distanceTo(centre) < radius + 1.2))
                    deflect(b.owner, shot, shot.position(), shot.position().subtract(centre).normalize(), null);
                if ((time - b.began) % 10 == 0 && time != b.began)
                    show(b.owner, Piece.FORCE_BUBBLE, centre, centre, ForceGeometry.SHELTER_FLAG + (float) radius, t, true);
                continue;
            }
            if (b.crushAt >= 0) {
                hold(t, b.anchor);
                if (time - b.crushAt >= CRUSH_TICKS) finishCrush(b);
                continue;
            }
            if (b.rollUntil >= 0) { tickRoll(b, time); continue; }
            if (time >= b.until) {
                endBubble(b, false);
                show(b.owner, Piece.FORCE_RIPPLE, centre, centre.add(0, 1, 0), (float) radius, t, false);
                continue;
            }
            hold(t, b.anchor);
            if ((time - b.began) % 10 == 0 && time != b.began)
                show(b.owner, Piece.FORCE_BUBBLE, centre, centre, (float) radius, t, true);
        }
    }

    private static void hold(LivingEntity t, Vec3 anchor) {
        t.setDeltaMovement(Vec3.ZERO);
        t.resetFallDistance();
        if (t instanceof Mob mob) mob.getNavigation().stop();
        if (t.position().distanceToSqr(anchor) > .0025) {
            if (t instanceof ServerPlayer sp) sp.connection.teleport(anchor.x, anchor.y, anchor.z, sp.getYRot(), sp.getXRot());
            else t.teleportTo(anchor.x, anchor.y, anchor.z);
        }
        t.hurtMarked = true;
    }

    private static void finishCrush(Bubble b) {
        LivingEntity t = b.target;
        ServerPlayer p = b.owner;
        double held = Math.min(6, (b.crushAt - b.began) / 20.0);
        float damage = (float) ((6 + 2 * level(p) + 1.5 * held) * (has(p, InvisibilitySkills.TIGHT_SEAL) ? 1.5 : 1));
        Vec3 centre = ForceGeometry.bubbleCentre(t);
        endBubble(b, true);
        hurt(p, t, damage, b.unseen);
        CameraShakePayload.sendAround(b.level, centre, 16, .35F, 6);
        sound(b.level, centre, SoundEvents.WIND_CHARGE_BURST.value(), 1, 1.3F);
        if (has(p, InvisibilitySkills.PRESSURE_CHAIN)) {
            show(p, Piece.FORCE_NOVA, centre, centre, 4, null, false);
            for (LivingEntity near : b.level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(4),
                    e -> e != t && foe(p, e) && e.position().distanceTo(centre) <= 4)) {
                hurt(p, near, damage * .5F, b.unseen);
                throwAway(near, near.position().subtract(centre).multiply(1, 0, 1).normalize().scale(1.1).add(0, .45, 0));
            }
        }
    }

    private static void tickRoll(Bubble b, long time) {
        LivingEntity t = b.target;
        ServerPlayer p = b.owner;
        Vec3 step = SpeedService.swept(t, b.roll.scale(ROLL_SPEED)).subtract(t.position());
        boolean struck = step.lengthSqr() < .09;
        if (!struck) {
            b.anchor = t.position().add(step);
            hold(t, b.anchor);
        }
        Vec3 centre = ForceGeometry.bubbleCentre(t);
        double radius = ForceGeometry.bubbleRadius(t);
        for (LivingEntity other : b.level.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(radius + .6),
                e -> e != t && foe(p, e) && !b.rolledOver.contains(e.getId()))) {
            b.rolledOver.add(other.getId());
            hurt(p, other, 5 + level(p), b.unseen);
            throwAway(other, b.roll.scale(1.2).add(0, .5, 0));
            sound(b.level, other.position(), SoundEvents.AMETHYST_BLOCK_HIT, 1.2F, .8F);
        }
        if ((time - b.rollUntil + ROLL_TICKS) % 10 == 0)
            show(p, Piece.FORCE_BUBBLE, centre, centre.add(b.roll), (float) radius, t, true);
        if (struck || time >= b.rollUntil) {
            endBubble(b, true);
            hurt(p, t, 4 + level(p) + (struck ? 3 : 0), b.unseen);
            throwAway(t, b.roll.scale(struck ? .2 : .8).add(0, .3, 0));
        }
    }

    // ================================================================ V: Force Nova

    public static boolean nova(ServerPlayer p) {
        if (!PowerRules.canAct(p) || charging(p)) return false;
        NOVAS.put(p.getUUID(), new Nova(p, now(p) + NOVA_WINDUP, unseenCast(p)));
        ArmPosePayload.show(p, ArmPose.FORCE_NOVA, NOVA_WINDUP + 14);
        SetPiecePayload.send(p.serverLevel(), Piece.FORCE_NOVA_CHARGE, p.position(), p.getEyePosition(),
                (float) (has(p, InvisibilitySkills.IMPLOSION) ? 16 : ForceGeometry.NOVA_RADIUS), p);
        sound(p.serverLevel(), p.position(), SoundEvents.BEACON_POWER_SELECT, 1.2F, .5F);
        sound(p.serverLevel(), p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5F, .5F);
        return true;
    }

    private static void tickNovas(long time) {
        for (Nova n : List.copyOf(NOVAS.values())) {
            ServerPlayer p = n.owner;
            if (p.isRemoved() || !p.isAlive() || !PowerRules.canAct(p)) { NOVAS.remove(p.getUUID()); continue; }
            boolean implosion = has(p, InvisibilitySkills.IMPLOSION);
            Vec3 centre = p.position().add(0, 1, 0);
            if (implosion && time < n.release)
                for (LivingEntity t : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(16),
                        e -> foe(p, e) && !contained(e) && e.position().distanceTo(p.position()) <= 16)) {
                    Vec3 pull = centre.subtract(ForceGeometry.bubbleCentre(t));
                    if (pull.length() > 3) throwAway(t, pull.normalize().scale(.45));
                }
            if (time < n.release) continue;
            NOVAS.remove(p.getUUID());
            release(p, n.unseen, implosion);
        }
    }

    private static void release(ServerPlayer p, boolean unseen, boolean implosion) {
        ServerLevel level = p.serverLevel();
        Vec3 centre = p.position().add(0, 1, 0);
        double radius = ForceGeometry.NOVA_RADIUS;
        if (implosion)
            level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(16), e -> foe(p, e) && !contained(e)
                            && e.position().distanceTo(p.position()) <= 16).stream()
                    .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(p))).limit(6)
                    .forEach(e -> contain(p, e, 40, unseen));
        // Your own walls break first, so they cut the foes beside them instead of sheltering them from the burst.
        for (Construct c : List.copyOf(CONSTRUCTS))
            if (c.owner == p) shatter(c, unseen);
        float base = 12 + 2 * level(p);
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(radius),
                e -> e != p && foe(p, e) && e.position().distanceTo(p.position()) <= radius)) {
            Vec3 body = ForceGeometry.bubbleCentre(t);
            if (level.clip(new ClipContext(centre, body, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType()
                    != HitResult.Type.MISS) continue;
            double d = body.distanceTo(centre);
            float damage = (float) (base * (1 - .5 * d / radius));
            Bubble bubble = BUBBLES.get(t.getUUID());
            if (bubble != null && !bubble.shelter) { damage *= 2; endBubble(bubble, true); }
            hurt(p, t, damage, unseen);
            Vec3 out = body.subtract(centre);
            out = out.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : out.normalize();
            throwAway(t, out.scale(.6 + 1.6 * (1 - d / radius)).add(0, .5, 0));
        }
        for (Projectile shot : level.getEntitiesOfClass(Projectile.class, new AABB(centre, centre).inflate(radius), s -> hostileShot(p, s)))
            shot.discard();
        SetPiecePayload.send(level, Piece.FORCE_NOVA, centre, centre, (float) radius, null);
        CameraShakePayload.sendAround(level, centre, 64, 1.1F, 18);
        sound(level, centre, SoundEvents.WIND_CHARGE_BURST.value(), 3, .5F);
        sound(level, centre, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 1.4F);
        sound(level, centre, SoundEvents.AMETHYST_BLOCK_RESONATE, 2, .5F);
        cast(p, unseen);
        InvisibilityService.fade(p, 60, false);
    }

    /** Force Nova breaks your bridges and walls outward into shards that cut whatever stands near them. */
    private static void shatter(Construct c, boolean unseen) {
        ServerPlayer p = c.owner;
        Set<LivingEntity> cut = new HashSet<>();
        for (BlockPos cell : c.cells)
            cut.addAll(c.level.getEntitiesOfClass(LivingEntity.class, new AABB(cell).inflate(2), e -> foe(p, e)));
        for (LivingEntity t : cut) hurt(p, t, 4 + level(p), unseen);
        SetPiecePayload.send(c.level, Piece.FORCE_SHATTER, c.origin, c.target,
                c.kind == Kind.WALL ? ForceGeometry.SHELTER_FLAG + c.scale : c.scale, null);
        dissolve(c);
    }

    // ================================================================ Projectiles

    private static boolean hostileShot(ServerPlayer owner, Projectile shot) {
        if (shot.isRemoved() || shot.getDeltaMovement().lengthSqr() < .04) return false;
        Entity shooter = shot.getOwner();
        return !(shooter instanceof LivingEntity living && friend(owner, living));
    }

    /** A field stops a projectile. With Rebound it is sent back at whoever fired it. */
    private static void deflect(ServerPlayer owner, Projectile shot, Vec3 at, Vec3 face, Vec3 push) {
        if (has(owner, InvisibilitySkills.REBOUND)) {
            Entity shooter = shot.getOwner();
            Vec3 v = shot.getDeltaMovement();
            double speed = Math.max(.9, v.length());
            Vec3 back = shooter != null && shooter.isAlive() && shooter.level() == shot.level()
                    ? shooter.getEyePosition().subtract(shot.position()).normalize()
                    : push != null ? push : v.subtract(face.scale(2 * v.dot(face))).normalize();
            shot.setDeltaMovement(back.scale(speed));
            shot.setOwner(owner);
            shot.hurtMarked = true;
        } else {
            shot.discard();
        }
        show(owner, Piece.FORCE_RIPPLE, at, at.add(face), 1, null, false);
        sound((ServerLevel) shot.level(), at, SoundEvents.AMETHYST_BLOCK_HIT, 1, 1.4F);
    }

    // ================================================================ Tree actives: Pinpoint, Kinetic Shell, Disc

    public static boolean pinpoint(ServerPlayer p) {
        if (!PowerRules.canAct(p)) return false;
        LivingEntity t = Targeting.livingEntityInSight(p, PINPOINT_RANGE, e -> foe(p, e));
        if (t == null) return false;
        boolean unseen = unseenCast(p);
        Vec3 hand = p.getEyePosition().add(p.getLookAngle().scale(.6)).add(0, -.3, 0);
        SetPiecePayload.send(p.serverLevel(), Piece.FORCE_BEAD, hand, ForceGeometry.bubbleCentre(t), 1, t);
        ArmPosePayload.show(p, ArmPose.FORCE_PUSH, 10);
        hurt(p, t, 6 + level(p), unseen);
        Bubble b = BUBBLES.get(t.getUUID());
        if (b == null || b.shelter) throwAway(t, new Vec3(0, 1.05, 0));
        sound(p.serverLevel(), t.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 2F);
        sound(p.serverLevel(), p.getEyePosition(), SoundEvents.WIND_CHARGE_BURST.value(), .6F, 1.8F);
        cast(p, unseen);
        return true;
    }

    public static boolean kineticShell(ServerPlayer p) {
        if (!PowerRules.canAct(p)) return false;
        Vec3 eye = p.getEyePosition();
        Vec3 at = p.serverLevel().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(24)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p)).getLocation();
        List<LivingEntity> foes = p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(6),
                        e -> foe(p, e) && e.position().distanceTo(at) <= 6).stream()
                .sorted(Comparator.comparingDouble(e -> e.position().distanceToSqr(at))).limit(4).toList();
        if (foes.isEmpty()) return false;
        boolean unseen = unseenCast(p);
        int ticks = has(p, InvisibilitySkills.TIGHT_SEAL) ? SHELL_TICKS * 5 / 3 : SHELL_TICKS;
        for (LivingEntity t : foes) contain(p, t, ticks, unseen);
        ArmPosePayload.show(p, ArmPose.FORCE_BUBBLE, 16);
        cast(p, unseen);
        return true;
    }

    public static boolean disc(ServerPlayer p) {
        if (!PowerRules.canAct(p) || p.isPassenger()) return false;
        int ticks = has(p, InvisibilitySkills.STEADY_DISC) ? STEADY_DISC_TICKS : DISC_TICKS;
        DISCS.put(p.getUUID(), new Disc(p, now(p) + ticks));
        show(p, Piece.FORCE_DISC, p.position(), p.position(), 1.3F, p, true);
        ArmPosePayload.show(p, ArmPose.FORCE_BUILD, 14);
        sound(p.serverLevel(), p.position(), SoundEvents.BEACON_ACTIVATE, .6F, 1.7F);
        return true;
    }
    public static boolean endDisc(ServerPlayer p) {
        if (DISCS.remove(p.getUUID()) == null) return false;
        show(p, Piece.FORCE_NET, p.position(), p.position().add(0, 1, 0), 1.3F, p, false);
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false));
        return true;
    }
    private static void tickDiscs(long time) {
        for (Disc d : List.copyOf(DISCS.values())) {
            ServerPlayer p = d.owner;
            if (time >= d.until || p.isRemoved() || !PowerRules.canAct(p) || p.isPassenger() || p.isShiftKeyDown()) { endDisc(p); continue; }
            Vec3 look = p.getLookAngle();
            Vec3 velocity = new Vec3(look.x, Mth.clamp(look.y, -.7, .7), look.z).normalize().scale(DISC_SPEED);
            p.setDeltaMovement(velocity);
            p.resetFallDistance();
            p.hurtMarked = true;
            p.connection.send(new ClientboundSetEntityMotionPacket(p));
            if ((d.until - time) % 10 == 0) show(p, Piece.FORCE_DISC, p.position(), p.position(), 1.3F, p, true);
        }
    }

    // ================================================================ tick, events, cleanup

    public static void tick(MinecraftServer server) {
        long time = server.overworld().getGameTime();
        for (Shield s : List.copyOf(SHIELDS.values())) {
            ServerPlayer p = s.owner;
            if (time >= s.until || p.isRemoved() || !PowerRules.canAct(p)) { SHIELDS.remove(p.getUUID()); continue; }
            Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
            Vec3 centre = ForceGeometry.shieldCentre(eye, look);
            for (Projectile shot : p.serverLevel().getEntitiesOfClass(Projectile.class, new AABB(centre, centre).inflate(s.radius + 1.5),
                    shot -> hostileShot(p, shot) && ForceGeometry.onShield(eye, look, s.radius, shot.position(), 1.4))) {
                deflect(p, shot, shot.position(), look, null);
                s.pressure = Math.min(MAX_PRESSURE, s.pressure + 1);
            }
            if ((s.until - time) % 10 == 0) showShield(s, false);
        }
        tickWaves();
        tickConstructs(time);
        tickBubbles(time);
        tickNovas(time);
        tickDiscs(time);
        NET_READY.values().removeIf(t -> t <= time);
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void damage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        Entity source = event.getSource().getEntity(), direct = event.getSource().getDirectEntity();
        // A held foe cannot reach out of its bubble.
        if (source instanceof LivingEntity attacker && contained(attacker) && attacker != victim) { event.setCanceled(true); return; }
        Bubble bubble = BUBBLES.get(victim.getUUID());
        if (bubble != null && bubble.shelter && source != null && !(source instanceof LivingEntity l && friend(bubble.owner, l))) {
            if (direct instanceof Projectile) { event.setCanceled(true); return; }
            event.setAmount(event.getAmount() * .5F);
        }
        if (victim instanceof ServerPlayer p && SHIELDS.get(p.getUUID()) instanceof Shield s && source != null && source != p) {
            Vec3 from = (direct != null ? direct : source).position();
            if (ForceGeometry.shieldFaces(p.getEyePosition(), p.getLookAngle(), from)) {
                s.pressure = Math.min(MAX_PRESSURE, s.pressure + 1);
                Vec3 at = ForceGeometry.shieldCentre(p.getEyePosition(), p.getLookAngle());
                show(p, Piece.FORCE_RIPPLE, at, at.add(p.getLookAngle()), (float) s.radius, null, false);
                if (direct instanceof Projectile) { event.setCanceled(true); return; }
                event.setAmount(event.getAmount() * .3F);
            }
        }
    }

    /** Safety Net: a pad of force catches a long fall, once every ten seconds. */
    @SubscribeEvent public static void fall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getDistance() < 6 || riding(p)) return;
        if (!has(p, InvisibilitySkills.SAFETY_NET) || NET_READY.containsKey(p.getUUID()) || !PowerRules.canAct(p)) return;
        NET_READY.put(p.getUUID(), now(p) + NET_COOLDOWN);
        event.setDamageMultiplier(0);
        event.setDistance(0);
        show(p, Piece.FORCE_NET, p.position(), p.position().add(0, 1, 0), 1.6F, null, false);
        sound(p.serverLevel(), p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1, 1.7F);
        p.setDeltaMovement(p.getDeltaMovement().multiply(1, 0, 1).add(0, .35, 0));
        p.hurtMarked = true;
    }

    public static void forget(ServerPlayer p) {
        SHIELDS.remove(p.getUUID());
        NOVAS.remove(p.getUUID());
        DISCS.remove(p.getUUID());
        NET_READY.remove(p.getUUID());
        WAVES.removeIf(w -> w.owner == p);
        for (Construct c : List.copyOf(CONSTRUCTS)) if (c.owner == p) dissolve(c);
        BUBBLES.values().removeIf(b -> b.owner == p || b.target == p);
        if (STEPPING.remove(p) && p.getAttribute(Attributes.STEP_HEIGHT) != null) p.getAttribute(Attributes.STEP_HEIGHT).removeModifier(STEP_ID);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (Construct c : List.copyOf(CONSTRUCTS)) dissolve(c);
        for (ServerPlayer p : STEPPING) if (p.getAttribute(Attributes.STEP_HEIGHT) != null) p.getAttribute(Attributes.STEP_HEIGHT).removeModifier(STEP_ID);
        SHIELDS.clear(); WAVES.clear(); BUBBLES.clear(); NOVAS.clear(); DISCS.clear(); NET_READY.clear(); STEPPING.clear();
    }
}
