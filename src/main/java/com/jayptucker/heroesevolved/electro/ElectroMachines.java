package com.jayptucker.heroesevolved.electro;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.grid.GridDisruption;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.TemporalProjectilePayload;
import com.jayptucker.heroesevolved.skills.SkillActionService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.weapons.Jamming;
import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The machine side of current, folded into Electrokinesis's tree when Technopathy was retired (25 November 2026). The
 * six nodes that used to be stat bonuses each change a move the electrokinetic already has:
 *
 * <ul>
 *   <li><b>Remote Hand</b> ({@link #REMOTE_HAND}): Chain Lightning tapped at a lever, button, door, trapdoor, gate or
 *   copper bulb works it; tapped at a conductor, current runs through it to every control it touches. Shift turns
 *   things off and never closes a door on a body.</li>
 *   <li><b>Kill Switch</b> ({@link #KILL_SWITCH}): Shift + tap at a powered machine shuts it off for five seconds
 *   ({@link GridDisruption#stall}); a dead Ability Dampener drops its field.</li>
 *   <li><b>Dead Trigger</b> ({@link #DEAD_TRIGGER}): an electrokinetic shock fries a TaCZ gun in the target's hand
 *   ({@link Jamming}).</li>
 *   <li><b>Return to Sender</b> ({@link #RETURN_TO_SENDER}): Static Screen catches bullets as well as arrows and sends
 *   every shot back at whoever fired it.</li>
 *   <li><b>Counterweight</b> ({@link #COUNTERWEIGHT}): Shift + Ride the Lightning at metal hauls you to it.</li>
 *   <li><b>Winchline</b> ({@link #WINCHLINE}): an Arc Lattice wire that catches someone reels them to its anchor.</li>
 * </ul>
 *
 * Every device is worked through the same probes a hand would pass ({@link #permitted}): build rights, the world border,
 * spawn protection and the right-click event claim mods cancel. No {@code BreakEvent} is posted.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class ElectroMachines {
    public static final String REMOTE_HAND = "electrokinesis_0_0", KILL_SWITCH = "electrokinesis_0_2",
            DEAD_TRIGGER = "electrokinesis_1_0", RETURN_TO_SENDER = "electrokinesis_1_2",
            COUNTERWEIGHT = "electrokinesis_2_0", WINCHLINE = "electrokinesis_2_2";
    /** Kill Switch's dead time, a jam's length, and how many controls a struck conductor reaches. */
    public static final int KILL_TICKS = 100, JAM_TICKS = 60, MAX_CONTROLS = 6;
    /** Counterweight: how far you can be hauled, how fast, for how long at most, and the landing cushion after. */
    public static final double HAUL_RANGE = 24, HAUL_SPEED = 1.15;
    public static final int HAUL_TICKS = 40, CUSHION_TICKS = 10;
    /** Winchline: how long a caught body is reeled, and how fast. */
    public static final int REEL_TICKS = 20;
    public static final double REEL_SPEED = .5;
    /** Static Screen's reach (the same seven blocks its reflection uses). */
    public static final double SCREEN_RADIUS = 7;

    private record Haul(ServerPlayer player, ServerLevel level, Vec3 anchor, long until) {}
    private record Reel(ServerPlayer owner, LivingEntity target, ServerLevel level, Vec3 anchor, long until) {}
    private static final Map<UUID, Haul> HAULS = new HashMap<>();
    private static final Map<UUID, Long> CUSHIONS = new HashMap<>();
    private static final List<Reel> REELS = new ArrayList<>();
    private static final Map<LivingEntity, Long> JAMS = new WeakHashMap<>();
    private static final Set<Projectile> TURNED = Collections.newSetFromMap(new WeakHashMap<>());

    private ElectroMachines() {}

    private static long now(ServerPlayer p) { return p.getServer().overworld().getGameTime(); }
    public static boolean has(ServerPlayer p, String node) { return SkillTreeService.has(p, node); }

    // ------------------------------------------------------------------ Remote Hand and Kill Switch

    /** Doors, gates and trapdoors open and close; levers and buttons switch; copper bulbs light. */
    public static boolean isControl(BlockState s) {
        Block b = s.getBlock();
        return b instanceof LeverBlock || b instanceof ButtonBlock || b instanceof DoorBlock
                || b instanceof TrapDoorBlock || b instanceof FenceGateBlock || b instanceof CopperBulbBlock;
    }
    private static BooleanProperty property(BlockState s) {
        if (s.hasProperty(BlockStateProperties.OPEN)) return BlockStateProperties.OPEN;
        if (s.getBlock() instanceof CopperBulbBlock) return BlockStateProperties.LIT;
        return BlockStateProperties.POWERED;
    }
    private static boolean value(BlockState s) { return isControl(s) && s.getValue(property(s)); }
    /** A door is worked from its lower half. */
    private static BlockPos canonical(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos.immutable();
    }
    private static boolean conductor(BlockState s) {
        Block b = s.getBlock();
        return b == Blocks.IRON_BLOCK || b == Blocks.IRON_BARS || b == Blocks.CHAIN || b == Blocks.LIGHTNING_ROD
                || BuiltInRegistries.BLOCK.getKey(b).getPath().contains("copper");
    }

    /**
     * Chain Lightning's bolt ended on a block. Remote Hand works a control, or every control the struck conductor's
     * network touches; Kill Switch (Shift) shuts a powered machine down. True when the bolt did one of these, so the
     * ordinary charge into the block is skipped.
     */
    public static boolean struck(ServerPlayer p, BlockHitResult hit, boolean off) {
        ServerLevel level = p.serverLevel();
        BlockPos pos = canonical(level, hit.getBlockPos());
        if (!level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        // Shift means "off": a machine is never charged instead, even when it can't be shut down here.
        if (off && has(p, KILL_SWITCH) && !isControl(state) && GridDisruption.isMachine(level, pos)) {
            killSwitch(p, level, pos);
            return true;
        }
        if (!has(p, REMOTE_HAND)) return false;
        if (isControl(state)) {
            boolean worked = operate(p, level, pos, off ? false : !value(state));
            if (worked) device(level, pos, false);
            return worked;
        }
        if (!conductor(state)) return false;
        // Current runs through the conductor to every control it touches: ON / OPEN, or OFF / CLOSED with Shift.
        List<BlockPos> network = ExpansionSkills.conductorNetwork(level, pos);
        Set<BlockPos> controls = new LinkedHashSet<>();
        for (BlockPos node : network) for (Direction d : Direction.values()) {
            BlockPos near = node.relative(d);
            if (level.hasChunkAt(near) && isControl(level.getBlockState(near))) controls.add(canonical(level, near));
        }
        int worked = 0; Vec3 previous = hit.getLocation();
        for (BlockPos control : controls.stream().sorted(Comparator.comparingDouble(c -> c.distSqr(pos))).limit(MAX_CONTROLS).toList()) {
            if (!operate(p, level, control, !off)) continue;
            Vec3 point = Vec3.atCenterOf(control).add(0, .53, 0);
            ElectroGeometry.show(level, Piece.ELECTRO_THREAD, previous, point, .8, null, Math.min(30, 2 + worked * 2));
            device(level, control, false);
            previous = point; worked++;
        }
        return worked > 0;
    }

    private static boolean killSwitch(ServerPlayer p, ServerLevel level, BlockPos pos) {
        if (!permitted(p, level, pos) || !GridDisruption.stall(p, level, pos, KILL_TICKS)) return false;
        device(level, pos, true);
        level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, .8F, 1.7F);
        level.playSound(null, pos, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, .6F, 1.2F);
        return true;
    }

    /** The device the current reached: its edges light (worked) or go dark from the top down (killed). */
    private static void device(ServerLevel level, BlockPos pos, boolean killed) {
        ElectroGeometry.show(level, Piece.ELECTRO_DEVICE, Vec3.atCenterOf(pos), Vec3.atCenterOf(pos), killed ? 2 : 1, null);
    }

    /** Flips a control the way a hand would, through every protection a hand passes. True if it now reads {@code desired}. */
    public static boolean operate(ServerPlayer p, ServerLevel level, BlockPos target, boolean desired) {
        if (!level.hasChunkAt(target)) return false;
        BlockPos pos = canonical(level, target); BlockState s = level.getBlockState(pos);
        if (!isControl(s) || value(s) == desired || !permitted(p, level, pos)) return false;
        if (s.getBlock() instanceof DoorBlock && (!level.hasChunkAt(pos.above()) || !permitted(p, level, pos.above()))) return false;
        // Never close a door, gate or trapdoor on anyone standing in it.
        if (!desired && s.hasProperty(BlockStateProperties.OPEN) && occupied(level, pos)) return false;
        if (s.getBlock() instanceof LeverBlock lever) lever.pull(s, level, pos, p);
        else if (s.getBlock() instanceof ButtonBlock button) { if (desired) button.press(s, level, pos, p); else return false; }
        else if (s.getBlock() instanceof DoorBlock door) door.setOpen(p, level, s, pos, desired);
        else {
            level.setBlock(pos, s.setValue(property(s), desired), 3);
            if (s.getBlock() instanceof ButtonBlock) {
                Direction support = s.getValue(BlockStateProperties.ATTACH_FACE) == AttachFace.FLOOR ? Direction.DOWN
                        : s.getValue(BlockStateProperties.ATTACH_FACE) == AttachFace.CEILING ? Direction.UP
                        : s.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
                level.updateNeighborsAt(pos.relative(support), s.getBlock());
            }
            level.gameEvent(p, desired ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, pos);
            level.playSound(null, pos, desired ? SoundEvents.COPPER_BULB_TURN_ON : SoundEvents.COPPER_BULB_TURN_OFF, SoundSource.BLOCKS, .7F, 1.3F);
        }
        return value(level.getBlockState(pos)) == desired;
    }

    /**
     * What a hand would have to pass to use this block: build rights, height, the world border, spawn protection, and the
     * right-click event claim mods cancel. Never posts a {@code BreakEvent} (that records echoes and mirrors snapshots).
     */
    public static boolean permitted(ServerPlayer p, ServerLevel level, BlockPos pos) {
        if (!p.mayBuild() || !level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || !level.mayInteract(p, pos)) return false;
        // A right-click event derives its world from its player, so don't forge one for another world.
        if (p.serverLevel() != level) return false;
        var event = new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, pos,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        return !NeoForge.EVENT_BUS.post(event).isCanceled() && event.getUseBlock() != TriState.FALSE;
    }

    private static boolean occupied(ServerLevel level, BlockPos pos) {
        AABB box = new AABB(pos).inflate(.1).expandTowards(0, level.getBlockState(pos).getBlock() instanceof DoorBlock ? 1 : 0, 0);
        return !level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive).isEmpty();
    }

    // ------------------------------------------------------------------ Dead Trigger

    /** An electrokinetic shock landed on {@code target}: Dead Trigger jams the gun in their hand. */
    public static void jolted(ServerPlayer p, LivingEntity target) {
        if (!target.isAlive() || !has(p, DEAD_TRIGGER) || !ElectroService.hostile(p, target)) return;
        if (!Jamming.jam(target, JAM_TICKS, p)) return;
        boolean fresh = !JAMS.containsKey(target);
        JAMS.put(target, target.level().getGameTime() + JAM_TICKS);
        if (fresh && target.level() instanceof ServerLevel level) {
            ElectroGeometry.show(level, Piece.ELECTRO_JAM, target.position(), target.position(), 1, target);
            level.playSound(null, target.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, .5F, 1.9F);
            level.playSound(null, target.blockPosition(), SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.PLAYERS, .7F, 1.4F);
        }
    }

    // ------------------------------------------------------------------ Return to Sender

    /** Whether this screen's owner sends shots back (Return to Sender learned, power still answering). */
    private static boolean returns(ServerPlayer owner) { return PowerRules.canAct(owner) && has(owner, RETURN_TO_SENDER); }

    /**
     * The way a caught shot should go back: at its shooter's eyes when they are alive, in this world, not the screen's
     * owner, and in clear sight of where it was caught; otherwise null (the screen just throws it away).
     */
    public static @Nullable Vec3 returnPath(ServerPlayer owner, @Nullable Entity shooter, Vec3 from) {
        if (!(shooter instanceof LivingEntity living) || shooter == owner || !living.isAlive() || shooter.level() != owner.level()) return null;
        Vec3 eye = living.getEyePosition();
        if (eye.distanceToSqr(from) < .25 || !ElectroService.visible(owner, from, eye)) return null;
        return eye.subtract(from).normalize();
    }

    /**
     * Turns {@code shot} at {@code at} back along its return path (or straight back the way it came), and makes it the
     * owner's. A fast round's new course is sent to the clients that draw it.
     */
    private static void turn(ServerLevel level, ServerPlayer owner, Projectile shot, Vec3 at) {
        Vec3 velocity = shot.getDeltaMovement();
        double speed = Math.max(.6, velocity.length());
        Vec3 direction = returnPath(owner, shot.getOwner(), at);
        if (direction == null) direction = velocity.lengthSqr() < 1e-6 ? at.subtract(owner.getEyePosition()).normalize() : velocity.normalize().reverse();
        TURNED.add(shot);
        shot.setPos(at); shot.setOwner(owner);
        shot.setDeltaMovement(direction.scale(speed)); shot.hurtMarked = true; shot.hasImpulse = true;
        if (PowerWeapons.isBullet(shot))
            PacketDistributor.sendToPlayersTrackingEntity(shot, new TemporalProjectilePayload(shot.getId(), 1, shot.position(),
                    shot.getDeltaMovement(), direction, false));
        ElectroGeometry.show(level, Piece.ELECTRO_ARC, at, at.add(direction.scale(3.2)), .7, null);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, .6F, 1.8F);
    }

    /**
     * Fast shots cross the whole screen in a tick, so each one's step is tested against every live screen whose owner
     * returns shots (three hooks: here, {@link #impact} and TaCZ's pre-hurt in {@code TaczLink}). True if it was caught.
     */
    public static boolean screenCatch(ServerLevel level, Entity entity, Vec3 from, Vec3 to) {
        if (!(entity instanceof Projectile shot) || shot.isRemoved() || TURNED.contains(shot)) return false;
        List<SkillActionService.Screen> screens = SkillActionService.screens(level);
        if (screens.isEmpty()) return false;
        for (SkillActionService.Screen screen : screens) {
            ServerPlayer owner = screen.owner();
            if (shot.getOwner() == owner || shot.getOwner() != null && shot.getOwner().isAlliedTo(owner) || !returns(owner)) continue;
            Vec3 center = screen.center().add(0, 1, 0);
            // Only shots coming in: one fired outward from inside the screen goes on its way.
            if (to.subtract(from).dot(center.subtract(from)) <= 0) continue;
            Vec3 entry = entry(from, to, center, SCREEN_RADIUS);
            // Never through a wall: the round must reach the screen's edge this step.
            if (entry == null || entry != from && level.clip(new ClipContext(from, entry, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, shot)).getType() != HitResult.Type.MISS) continue;
            turn(level, owner, shot, entry);
            return true;
        }
        return false;
    }

    /** Where the segment first enters the sphere (its start, when it starts inside), or null if it misses. */
    private static @Nullable Vec3 entry(Vec3 from, Vec3 to, Vec3 center, double radius) {
        if (from.distanceToSqr(center) <= radius * radius) return from;
        Vec3 d = to.subtract(from); double a = d.lengthSqr();
        if (a < 1e-9) return null;
        Vec3 f = from.subtract(center);
        double b = 2 * f.dot(d), c = f.lengthSqr() - radius * radius, disc = b * b - 4 * a * c;
        if (disc < 0) return null;
        double u = (-b - Math.sqrt(disc)) / (2 * a);
        return u >= 0 && u <= 1 ? from.add(d.scale(u)) : null;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void projectileTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Projectile shot) || !(shot.level() instanceof ServerLevel level)) return;
        if (shot.getDeltaMovement().lengthSqr() < .01) return;
        screenCatch(level, shot, shot.position(), shot.position().add(shot.getDeltaMovement()));
    }

    @SubscribeEvent public static void impact(ProjectileImpactEvent event) {
        if (event.getProjectile().level() instanceof ServerLevel level && event.getRayTraceResult().getType() != HitResult.Type.MISS
                && screenCatch(level, event.getProjectile(), event.getProjectile().position(), event.getRayTraceResult().getLocation()))
            event.setCanceled(true);
    }

    // ------------------------------------------------------------------ Counterweight

    /** Something current can magnetise you to: iron, copper, chain, a rail, a lightning rod or an anvil. */
    private static boolean magnetic(BlockState s) {
        return conductor(s) || s.getBlock() instanceof BaseRailBlock || s.getBlock() instanceof AnvilBlock || s.is(Blocks.IRON_DOOR)
                || s.is(Blocks.IRON_TRAPDOOR) || s.is(Blocks.HOPPER) || s.is(Blocks.CAULDRON) || s.is(Blocks.LANTERN);
    }

    /** Shift + Ride the Lightning: a magnetic line hauls you to the metal (or your own lattice anchor) you are aiming at. */
    public static boolean counterweight(ServerPlayer p) {
        if (!has(p, COUNTERWEIGHT) || p.isPassenger() || HAULS.containsKey(p.getUUID())) return false;
        Vec3 anchor = haulTarget(p);
        if (anchor == null) return false;
        HAULS.put(p.getUUID(), new Haul(p, p.serverLevel(), anchor, now(p) + HAUL_TICKS));
        CUSHIONS.put(p.getUUID(), now(p) + HAUL_TICKS + CUSHION_TICKS);
        ArmPosePayload.show(p, ArmPose.ELECTRO_HAUL, 12);
        ElectroGeometry.show(p.serverLevel(), Piece.ELECTRO_HAUL, anchor, anchor, 1, p);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, .9F, 1.6F);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, .5F, 1.8F);
        return true;
    }

    /** The point a haul pulls toward: the face of the aimed metal block, or a lattice anchor in line with the aim. */
    public static @Nullable Vec3 haulTarget(ServerPlayer p) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        for (Vec3 anchor : ExpansionSkills.latticeAnchors(p)) {
            Vec3 to = anchor.subtract(eye);
            if (to.length() <= HAUL_RANGE && to.length() > 1.5 && to.normalize().dot(look) > .985 && ElectroService.visible(p, eye, anchor)) return anchor;
        }
        BlockHitResult hit = p.serverLevel().clip(new ClipContext(eye, eye.add(look.scale(HAUL_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.BLOCK || !p.serverLevel().hasChunkAt(hit.getBlockPos())
                || !magnetic(p.serverLevel().getBlockState(hit.getBlockPos()))) return null;
        Vec3 face = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.6));
        return face.distanceToSqr(eye) > 2.25 ? face : null;
    }

    private static boolean tickHaul(Haul h, long time) {
        ServerPlayer p = h.player;
        if (p.isRemoved() || p.level() != h.level || !PowerRules.canAct(p) || p.isPassenger() || time >= h.until) return false;
        Vec3 body = p.getBoundingBox().getCenter(), to = h.anchor.subtract(body);
        if (to.lengthSqr() < 1.4 * 1.4 || !ElectroService.visible(p, body, h.anchor)) {
            // Arrived (or the line was cut): a little lift so a ledge can be caught, and the cushion still holds.
            if (to.lengthSqr() < 1.4 * 1.4) { p.setDeltaMovement(p.getDeltaMovement().scale(.2).add(0, .42, 0)); p.hurtMarked = true; }
            return false;
        }
        p.setDeltaMovement(to.normalize().scale(Math.min(HAUL_SPEED, to.length())));
        p.hurtMarked = true; p.hasImpulse = true; p.fallDistance = 0;
        if ((h.until - time) % 10 == 0) ElectroGeometry.show(h.level, Piece.ELECTRO_HAUL, h.anchor, h.anchor, 1, p);
        return true;
    }

    @SubscribeEvent public static void fall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Long until = CUSHIONS.get(p.getUUID());
        if (until != null && now(p) <= until) { event.setDistance(0); event.setDamageMultiplier(0); }
    }

    // ------------------------------------------------------------------ Winchline

    /** An Arc Lattice wire caught {@code target}: Winchline reels them toward the nearest of the lattice's anchors. */
    public static void caught(ServerPlayer owner, LivingEntity target, List<Vec3> anchors) {
        if (!has(owner, WINCHLINE) || anchors.isEmpty() || !target.isAlive() || target.isPassenger()) return;
        if (REELS.stream().anyMatch(r -> r.target == target)) return;
        Vec3 anchor = anchors.stream().min(Comparator.comparingDouble(a -> a.distanceToSqr(target.position()))).orElseThrow();
        REELS.add(new Reel(owner, target, owner.serverLevel(), anchor, now(owner) + REEL_TICKS));
        ElectroGeometry.show(owner.serverLevel(), Piece.ELECTRO_REEL, anchor, anchor, 1, target);
        ArmPosePayload.show(owner, ArmPose.ELECTRO_REEL, 10);
        owner.serverLevel().playSound(null, target.blockPosition(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 1, 1.4F);
    }

    private static boolean tickReel(Reel r, long time) {
        LivingEntity t = r.target;
        if (time >= r.until || !t.isAlive() || t.isRemoved() || t.level() != r.level || t.isPassenger()
                || !PowerRules.canAct(r.owner) || !ElectroService.hostile(r.owner, t)) return false;
        Vec3 body = t.getBoundingBox().getCenter(), to = r.anchor.subtract(body);
        // Cover between the anchor and the body snaps the line.
        if (to.lengthSqr() < 1.2 * 1.2 || !ElectroService.visible(r.owner, r.anchor, body)) return false;
        t.setDeltaMovement(to.normalize().scale(REEL_SPEED).add(0, .04, 0));
        t.hurtMarked = true; t.hasImpulse = true;
        if ((r.until - time) % 10 == 0) ElectroGeometry.show(r.level, Piece.ELECTRO_REEL, r.anchor, r.anchor, 1, t);
        return true;
    }

    // ------------------------------------------------------------------ Lifecycle

    public static void tick(MinecraftServer server) {
        long time = server.overworld().getGameTime();
        for (Haul h : List.copyOf(HAULS.values())) if (!tickHaul(h, time)) HAULS.remove(h.player.getUUID());
        CUSHIONS.values().removeIf(until -> time > until);
        REELS.removeIf(r -> !tickReel(r, time));
        // A jammed gun keeps spitting sparks while it lasts.
        for (var entry : List.copyOf(JAMS.entrySet())) {
            LivingEntity target = entry.getKey();
            if (!target.isAlive() || target.isRemoved() || !Jamming.jammed(target) || target.level().getGameTime() > entry.getValue()) {
                JAMS.remove(target); continue;
            }
            if (target.level() instanceof ServerLevel level && target.tickCount % 10 == 0)
                ElectroGeometry.show(level, Piece.ELECTRO_JAM, target.position(), target.position(), 1, target);
        }
    }

    /** Whether {@code p} is being hauled right now (tests, and Ride's refusal to start a second one). */
    public static boolean hauling(ServerPlayer p) { return HAULS.containsKey(p.getUUID()); }
    /** Whether {@code target} is on a Winchline right now. */
    public static boolean reeling(LivingEntity target) { return REELS.stream().anyMatch(r -> r.target == target); }

    public static void forget(ServerPlayer p) {
        HAULS.remove(p.getUUID()); CUSHIONS.remove(p.getUUID());
        REELS.removeIf(r -> r.owner == p || r.target == p);
        JAMS.remove(p);
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void death(LivingDeathEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { HAULS.clear(); CUSHIONS.clear(); REELS.clear(); JAMS.clear(); TURNED.clear(); }
}
