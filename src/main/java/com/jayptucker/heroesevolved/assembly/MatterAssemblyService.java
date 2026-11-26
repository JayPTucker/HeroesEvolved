package com.jayptucker.heroesevolved.assembly;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import com.jayptucker.heroesevolved.mimicry.MimicryService;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.progression.EvolutionTrials;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * Matter Assembly, the engineer: crystal folded out of thin air ({@link Matter}) into constructs that work for you.
 *
 * <ul>
 *   <li><b>Panel</b> (the {@code barricade} action): a three-by-two crystal pane wherever you aim, even in mid-air, or
 *   a flat floor when you look down. Cover, a step, and a mirror: your Shards ricochet off it. You keep three.</li>
 *   <li><b>Shard Lance</b>: a crystal shard formed at the hand. It ricochets off your own constructs twice, harder
 *   after every bounce, and hits a foe a Launch Pad threw half again as hard.</li>
 *   <li><b>Skywalk</b> (the {@code causeway} action): plates catch every step you take on air.</li>
 *   <li><b>Launch Pad</b> (the {@code cage} action): a plate on the ground, or hanging in the air, that throws friends
 *   along it and foes straight up, airborne for your Shards and turrets.</li>
 *   <li><b>Sentinel Engine</b> (the {@code cathedral} action): three crystal turrets that shoot (and ricochet) for you,
 *   airborne foes first.</li>
 * </ul>
 *
 * Every cell is a leased block ({@link AssemblyTerrain}) that gives no loot and vanishes safely; all geometry and swept
 * hits are server-owned and shared with the drawing through {@link AssemblyGeometry}.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class MatterAssemblyService {
    public static final int CELLS_PER_TICK = 24, GLOBAL_CELL_BUDGET = 96, MAX_JOBS = 32, MAX_SHARDS = 128;
    private static final List<Job> JOBS = new ArrayList<>();
    private static final List<Shard> SHARDS = new ArrayList<>(), NEW_SHARDS = new ArrayList<>();
    private static final Map<UUID, Crossing> CROSSINGS = new HashMap<>();
    private static final Map<UUID, Fortress> FORTRESSES = new HashMap<>();
    private static final Map<UUID, Skywalk> SKYWALKS = new HashMap<>();
    private static final Map<UUID, ArrayDeque<Construct>> PANELS = new HashMap<>();
    private static final Map<UUID, ArrayDeque<Pad>> PADS = new HashMap<>();
    private static final Map<UUID, Kaleido> KALEIDOS = new HashMap<>();
    /** Foes a pad threw (Shards and turrets favour them) and friends it threw (no fall damage), until a game time. */
    private static final Map<Entity, Long> AIRBORNE = new WeakHashMap<>(), CUSHIONED = new WeakHashMap<>(), REARM = new WeakHashMap<>();
    /** Cells whose lease ran out this tick, per owner: their Matter flies home together. */
    private static final Map<UUID, List<BlockPos>> EXPIRED = new HashMap<>();
    /** Skywalk: for this long, matter assembles under every step you take on air; each plate lasts a moment. */
    public static final int SKYWALK_TICKS = 160, PLATE_TICKS = 50;
    /** Sentinel Engine: three turrets, for this long, a Shard each every half second or so, this far. */
    public static final int SENTINELS = 3, SENTINEL_TICKS = 400, SENTINEL_VOLLEYS = 14;
    public static final double SENTINEL_RANGE = 32;
    private static final Map<UUID, Long> VALVES = new HashMap<>(), FOUNDATIONS = new HashMap<>();

    private record Crossing(ServerLevel level, List<BlockPos> cells) {}
    private record Construct(ServerLevel level, List<BlockPos> cells, Vec3 center, Job job) {}
    private record Pad(ServerPlayer owner, ServerLevel level, Vec3 center, Direction facing, boolean floating, long until) {}
    private record Kaleido(ServerLevel level, Set<BlockPos> cells, Vec3 center, long until) {}
    private static final class Skywalk {
        final ServerPlayer owner; final ServerLevel level; final long until;
        Skywalk(ServerPlayer p) { owner = p; level = p.serverLevel(); until = level.getGameTime() + SKYWALK_TICKS; }
    }
    private static final class Job {
        final ServerPlayer owner; final ServerLevel level; final ArrayDeque<BlockPos> pending;
        final List<BlockPos> built = new ArrayList<>(); final int duration; final String type;
        /** An emergency build (Second Foundation) spends no Matter. */
        final boolean free;
        /** Cells a tick, and the game time the first may land (a paced build matches its drawing). */
        int rate = CELLS_PER_TICK; long startAt;
        Job(ServerPlayer owner, Collection<BlockPos> shape, int duration, String type, boolean free) {
            this.owner = owner; level = owner.serverLevel(); pending = new ArrayDeque<>(new LinkedHashSet<>(shape));
            this.duration = duration; this.type = type; this.free = free;
        }
        Job paced(int delay, int rate) { this.startAt = level.getGameTime() + delay; this.rate = rate; return this; }
    }
    /** Sentinel Engine's turrets: two-cell pillars, each with a crystal head that finds and shoots enemies. */
    private static final class Fortress {
        final ServerPlayer owner; final ServerLevel level; final Vec3 anchor; final List<BlockPos> blueprint;
        final List<BlockPos> heads; final int[] volleys;
        final long until;
        Fortress(ServerPlayer p, List<BlockPos> shape, List<BlockPos> heads) {
            owner = p; level = p.serverLevel(); anchor = p.position(); blueprint = List.copyOf(shape);
            this.heads = List.copyOf(heads); volleys = new int[heads.size()];
            Arrays.fill(volleys, SENTINEL_VOLLEYS);
            until = level.getGameTime() + SENTINEL_TICKS;
        }
    }
    private static final class Shard {
        final ServerPlayer owner; final ServerLevel level; final float damage; final boolean split;
        Vec3 position, direction; double remaining = AssemblyGeometry.SHARD_RANGE; int bounces;
        Shard(ServerPlayer p, Vec3 origin, Vec3 direction, float damage, boolean split) {
            owner = p; level = p.serverLevel(); position = origin; this.direction = direction.normalize();
            this.damage = damage; this.split = split;
        }
    }
    private MatterAssemblyService() {}

    public static boolean authorized(ServerPlayer p) {
        return PlayerAbilityService.getData(p).assignedPower().filter(e -> e.getValue().isUnlocked()
                && e.getKey().equals(ModAbilities.MATTER_ASSEMBLY_ID)).isPresent()
                || MimicryService.isBorrowingMatterAssembly(p);
    }
    private static boolean valid(ServerPlayer p, ServerLevel level) {
        return !p.isRemoved() && p.serverLevel() == level && PowerRules.canAct(p) && authorized(p);
    }
    private static boolean has(ServerPlayer p, String skill) { return SkillTreeService.has(p, "matter_assembly_" + skill); }
    private static String canonical(String mechanic) {
        return switch (mechanic) {
            case "matter_assembly_0_1" -> "switchback";
            case "matter_assembly_1_1" -> "scattershot";
            case "matter_assembly_2_1" -> "shelter";
            // The node kept Faultline's id when it became Kaleidoscope.
            case "faultline", "matter_assembly_faultline" -> "kaleidoscope";
            default -> mechanic.startsWith("matter_assembly_") ? mechanic.substring(16) : mechanic;
        };
    }
    /** The tree node that unlocks an optional action, by its canonical name. */
    private static String node(String action) { return action.equals("kaleidoscope") ? "faultline" : action; }

    public static boolean canUse(AbilityUseContext c, String mechanic) {
        ServerPlayer p = c.player(); String action = canonical(mechanic);
        if (!valid(p, p.serverLevel()) || !c.abilityId().equals(ModAbilities.MATTER_ASSEMBLY_ID)) return false;
        if (Set.of("switchback", "drawbridge", "scattershot", "kaleidoscope", "shelter", "cradle").contains(action)
                && !has(p, node(action))) return false;
        int tier = switch (action) { case "causeway" -> 2; case "cage" -> 3; case "cathedral" -> 5; default -> 1; };
        if (c.abilityLevel() < tier) return false;
        if (action.equals("drawbridge") && CROSSINGS.containsKey(p.getUUID())) return true;
        if (action.equals("causeway") && SKYWALKS.containsKey(p.getUUID())) return false;
        return Set.of("barricade", "shard_lance", "causeway", "cage", "cathedral", "switchback",
                "drawbridge", "scattershot", "kaleidoscope", "shelter", "cradle").contains(action)
                && SHARDS.size() + NEW_SHARDS.size() < MAX_SHARDS - 6
                // Panels are capped at three of their own; other builds run two at a time.
                && JOBS.size() < MAX_JOBS && JOBS.stream().filter(j -> j.owner == p && !j.type.equals("panel")).count() < 2;
    }
    public static boolean activate(AbilityUseContext c, String mechanic) {
        String action = canonical(mechanic); ServerPlayer p = c.player();
        if (!canUse(c, action)) return false;
        return switch (action) {
            case "barricade" -> panel(p);
            case "causeway" -> skywalk(p);
            case "switchback" -> begin(p, route(p, 14, true, false), 600, action);
            case "drawbridge" -> drawbridge(p);
            case "shard_lance" -> lance(p, com.jayptucker.heroesevolved.amplify.Amplification.surged(p) ? 2 : 1, 8 + c.abilityLevel() * 1.2F);
            case "scattershot" -> scatter(p, 5 + c.abilityLevel() * .5F);
            case "cage" -> pad(p);
            case "cathedral" -> cathedral(p);
            case "kaleidoscope" -> kaleidoscope(p);
            case "shelter" -> begin(p, shelter(p), 260, action);
            case "cradle" -> cradle(p);
            default -> false;
        };
    }

    // ------------------------------------------------------------------ building

    private static boolean begin(ServerPlayer p, List<BlockPos> shape, int duration, String type) {
        if (shape.isEmpty() || JOBS.size() >= MAX_JOBS) return false;
        Job job = new Job(p, shape, duration, type, false);
        // A denied, occupied or Matter-less shape is refused before stamina is charged; never leave an unstarted job.
        build(job, 4, 48);
        if (job.built.isEmpty()) {
            if (Matter.of(p) <= 0) message(p, "Out of Matter: take a construct apart, or wait a moment for it to regrow.");
            return false;
        }
        if (!job.pending.isEmpty()) JOBS.add(job);
        if (type.equals("drawbridge")) CROSSINGS.put(p.getUUID(), new Crossing(p.serverLevel(), job.built));
        rise(p, shape);
        ArmPosePayload.show(p, ArmPose.MATTER_FORM, 12);
        sound(p, SoundEvents.AMETHYST_CLUSTER_PLACE, .9F, 1.2F);
        return true;
    }

    /** Matter streaming from the builder's hands into a construct's shape, course by course ({@code AssemblyArt.rise}). */
    private static void rise(ServerPlayer p, List<BlockPos> shape) {
        if (shape.isEmpty()) return;
        Vec3 center = centroid(shape);
        double size = 0;
        for (BlockPos pos : shape) size = Math.max(size, Vec3.atCenterOf(pos).distanceTo(center));
        SetPiecePayload.send(p.serverLevel(), Piece.MATTER_RISE, center, p.position(), (float) Math.max(1.5, size + .5), p);
    }

    private static Vec3 centroid(Collection<BlockPos> cells) {
        double x = 0, y = 0, z = 0;
        for (BlockPos pos : cells) { x += pos.getX() + .5; y += pos.getY() + .5; z += pos.getZ() + .5; }
        int n = Math.max(1, cells.size());
        return new Vec3(x / n, y / n, z / n);
    }

    private static int build(Job job, int budget, int attempts) {
        int placed = 0; AssemblyTerrain terrain = AssemblyTerrain.get(job.level);
        while (!job.pending.isEmpty() && placed < budget && attempts-- > 0) {
            if (terrain.count(job.owner.getUUID()) >= AssemblyTerrain.OWNER_LIMIT) { job.pending.clear(); break; }
            // Every cell is one Matter: short of it, the construct stops where it is.
            if (!job.free && !Matter.take(job.owner)) { job.pending.clear(); break; }
            BlockPos pos = job.pending.removeFirst();
            if (!terrain.place(job.owner, pos, job.duration)) { if (!job.free) Matter.give(job.owner, 1); continue; }
            job.built.add(pos); placed++;
        }
        if (placed > 0) Matter.sync(job.owner, false);
        return placed;
    }

    /**
     * Panel: three wide and two high across your look, standing where you aim (on the ground, against a wall, or
     * {@link AssemblyGeometry#PANEL_AIR} blocks out in mid-air); looking steeply down lays it flat as a floor. You keep
     * three; a fourth takes the oldest apart and its Matter comes home first.
     */
    private static boolean panel(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        boolean flat = p.getXRot() > AssemblyGeometry.FLAT_PITCH, wings = !flat && has(p, "buttress");
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(AssemblyGeometry.PANEL_RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Direction facing = p.getDirection();
        BlockPos base = flat ? AssemblyGeometry.floor(p.position(), facing, p.getXRot())
                : hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos().relative(hit.getDirection())
                : BlockPos.containing(eye.add(look.scale(AssemblyGeometry.PANEL_AIR)));
        List<BlockPos> shape = AssemblyGeometry.panel(base, facing, flat, wings);
        AssemblyTerrain terrain = AssemblyTerrain.get(level);
        if (Matter.of(p) <= 0) { message(p, "Out of Matter: take a construct apart, or wait a moment for it to regrow."); return false; }
        if (shape.stream().noneMatch(pos -> terrain.available(p, pos) && WorldMutationPolicy.canEdit(p, level, pos)) || JOBS.size() >= MAX_JOBS) return false;
        ArrayDeque<Construct> panels = PANELS.computeIfAbsent(p.getUUID(), id -> new ArrayDeque<>());
        panels.removeIf(c -> !JOBS.contains(c.job) && !standing(p, c));
        if (panels.size() >= AssemblyGeometry.PANELS) takeApart(p, panels.removeFirst());
        // The cells land one a tick after a short wind-up, exactly when the drawing shows them arriving.
        Job job = new Job(p, shape, AssemblyGeometry.PANEL_TICKS, "panel", false).paced(AssemblyGeometry.PANEL_DELAY, AssemblyGeometry.PANEL_RATE);
        JOBS.add(job);
        panels.addLast(new Construct(level, List.copyOf(shape), centroid(shape), job));
        Vec3 normal = flat ? new Vec3(0, 1, 0) : Vec3.atLowerCornerOf(facing.getNormal());
        SetPiecePayload.send(level, Piece.MATTER_PANEL, Vec3.atCenterOf(base), Vec3.atCenterOf(base).add(normal),
                AssemblyGeometry.panelFlags(flat, wings), p);
        ArmPosePayload.show(p, ArmPose.MATTER_FORM, 10);
        sound(p, SoundEvents.AMETHYST_CLUSTER_PLACE, 1, 1.35F);
        level.playSound(null, base, SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, .7F, 1.6F);
        return true;
    }

    /** Whether a remembered construct still has a cell of the owner's standing. */
    private static boolean standing(ServerPlayer p, Construct c) {
        AssemblyTerrain terrain = AssemblyTerrain.get(c.level);
        return c.cells.stream().anyMatch(pos -> terrain.owned(c.level, pos, p.getUUID()));
    }

    /** Takes a construct apart by choice: whatever it has not built yet is cancelled, and its standing cells' Matter flies home. */
    private static void takeApart(ServerPlayer p, Construct c) {
        JOBS.remove(c.job);
        collapse(p, c.level, c.cells, false);
    }

    /**
     * Skywalk: for eight seconds, matter assembles under every step you take on air, a three-wide plate across your
     * path. Jump to climb; crouch to drop through. Each plate crumbles a moment after you leave it, and its Matter
     * comes home, so a long walk costs only what is under you.
     */
    private static boolean skywalk(ServerPlayer p) {
        if (SKYWALKS.containsKey(p.getUUID())) return false;
        SKYWALKS.put(p.getUUID(), new Skywalk(p));
        step(SKYWALKS.get(p.getUUID()));
        ArmPosePayload.show(p, ArmPose.MATTER_STRIDE, 16);
        SetPiecePayload.send(p.serverLevel(), Piece.MATTER_PLATE, p.position(), p.position().add(0, 1, 0), 2, p);
        sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, .8F, 1.7F);
        return true;
    }

    /** One tick of Skywalk: a plate under the feet if they are over air and not falling away on purpose. */
    private static void step(Skywalk walk) {
        ServerPlayer p = walk.owner;
        if (p.isShiftKeyDown() || p.getDeltaMovement().y > 0.05) return;
        BlockPos below = BlockPos.containing(p.getX(), p.getBoundingBox().minY - 0.2, p.getZ());
        if (!walk.level.getBlockState(below).isAir()) return;
        Direction side = p.getDirection().getClockWise();
        AssemblyTerrain terrain = AssemblyTerrain.get(walk.level);
        int placed = 0;
        for (int offset = -1; offset <= 1; offset++) {
            if (!Matter.take(p)) break;
            if (terrain.place(p, below.relative(side, offset), PLATE_TICKS)) placed++;
            else Matter.give(p, 1);
        }
        if (placed > 0) {
            SetPiecePayload.send(walk.level, Piece.MATTER_PLATE, Vec3.atCenterOf(below), Vec3.atCenterOf(below).add(Vec3.atLowerCornerOf(p.getDirection().getNormal())), 1, null);
            Matter.sync(p, false);
        }
    }
    private static List<BlockPos> route(ServerPlayer p, int length, boolean bend, boolean wide) {
        Direction initial = p.getDirection(), direction = initial; BlockPos at = p.blockPosition().below();
        int slope = p.getXRot() < -18 ? 1 : p.getXRot() > 28 ? -1 : 0;
        boolean left = p.getLookAngle().cross(Vec3.atLowerCornerOf(initial.getNormal())).y > 0;
        List<BlockPos> cells = new ArrayList<>();
        for (int step = 1; step <= length; step++) {
            if (bend && step == length / 2) direction = left ? initial.getCounterClockWise() : initial.getClockWise();
            at = at.relative(direction).above(slope != 0 && step % 2 == 0 ? slope : 0);
            Direction side = direction.getClockWise();
            int half = wide ? 1 : 0;
            for (int offset = -half; offset <= half; offset++) cells.add(at.relative(side, offset));
            // Snap Rails: guard posts along both edges.
            if (has(p, "rails") && step % 2 == 0) {
                cells.add(at.relative(side, half + 1).above()); cells.add(at.relative(side, -half - 1).above());
            }
        }
        return cells;
    }
    private static boolean drawbridge(ServerPlayer p) {
        Crossing crossing = CROSSINGS.remove(p.getUUID());
        if (crossing != null) {
            JOBS.removeIf(j -> j.owner == p && j.type.equals("drawbridge"));
            List<BlockPos> reversed = new ArrayList<>(crossing.cells); Collections.reverse(reversed);
            collapse(p, crossing.level, reversed, true); return true;
        }
        return begin(p, route(p, 20, false, false), 400, "drawbridge");
    }

    /**
     * Launch Pad: a crystal plate where you aim on the ground (or hanging in the air when there is no ground in reach)
     * that throws whoever crosses it. Friends (and you) are thrown along the way you faced, landing without fall damage;
     * foes are thrown straight up and count as airborne for two seconds. You keep two.
     */
    private static boolean pad(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(AssemblyGeometry.PAD_RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 center = null; boolean floating = false;
        if (hit.getType() == HitResult.Type.BLOCK) {
            if (hit.getDirection() == Direction.UP) center = hit.getLocation();
            else {
                // Against a wall: the pad lies on the floor just in front of it, if there is one close below.
                BlockPos front = hit.getBlockPos().relative(hit.getDirection());
                for (int down = 0; down <= 3 && center == null; down++) {
                    BlockPos at = front.below(down);
                    if (level.getBlockState(at).isAir() && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP))
                        center = new Vec3(at.getX() + .5, at.getY(), at.getZ() + .5);
                }
            }
        }
        if (center == null) { center = eye.add(look.scale(AssemblyGeometry.PAD_AIR)); floating = true; }
        if (!level.getWorldBorder().isWithinBounds(BlockPos.containing(center))) return false;
        int paid = 0;
        while (paid < AssemblyGeometry.PAD_MATTER && Matter.take(p)) paid++;
        if (paid < AssemblyGeometry.PAD_MATTER) {
            Matter.give(p, paid);
            message(p, "A Launch Pad needs " + AssemblyGeometry.PAD_MATTER + " Matter. Take a construct apart, or wait a moment.");
            return false;
        }
        ArrayDeque<Pad> pads = PADS.computeIfAbsent(p.getUUID(), id -> new ArrayDeque<>());
        if (pads.size() >= AssemblyGeometry.PADS) retirePad(p, pads.removeFirst(), true);
        Direction facing = p.getDirection();
        Pad pad = new Pad(p, level, center, facing, floating, level.getGameTime() + AssemblyGeometry.PAD_TICKS);
        pads.addLast(pad);
        showPad(pad, true);
        ArmPosePayload.show(p, ArmPose.MATTER_PLANT, 12);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, .8F, 1.7F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 1, .9F);
        Matter.sync(p, false);
        return true;
    }

    private static void showPad(Pad pad, boolean placed) {
        Vec3 along = Vec3.atLowerCornerOf(pad.facing.getNormal());
        SetPiecePayload.send(pad.level, placed ? Piece.MATTER_PAD_PLACE : Piece.MATTER_PAD, pad.center, pad.center.add(along),
                pad.floating ? 2 : 1, null);
    }

    private static void retirePad(ServerPlayer p, Pad pad, boolean home) {
        if (home) Matter.returned(p, pad.center.add(0, .3, 0), AssemblyGeometry.PAD_MATTER);
    }

    /** One tick of a pad: anyone over it who isn't still coming down from its last throw is thrown. */
    private static void tickPad(Pad pad, long now) {
        for (LivingEntity e : pad.level.getEntitiesOfClass(LivingEntity.class, AssemblyGeometry.padZone(pad.center),
                e -> e.isAlive() && !e.isSpectator() && !e.isPassenger())) {
            if (REARM.getOrDefault(e, -1000L) > now) continue;
            boolean friend = e == pad.owner || e instanceof Player && !canHarm(pad.owner, e)
                    || e instanceof TamableAnimal pet && pet.isOwnedBy(pad.owner);
            boolean foe = !friend && canHarm(pad.owner, e) && hostileTo(pad.owner, e);
            if (!friend && !foe) continue;
            Vec3 throwV;
            if (friend) {
                Vec3 along = Vec3.atLowerCornerOf(pad.facing.getNormal());
                throwV = along.scale(AssemblyGeometry.FRIEND_FORWARD).add(0, AssemblyGeometry.FRIEND_UP, 0);
                CUSHIONED.put(e, now + 80);
            } else {
                throwV = new Vec3(0, AssemblyGeometry.FOE_UP, 0);
                AIRBORNE.put(e, now + AssemblyGeometry.AIRBORNE_TICKS);
            }
            REARM.put(e, now + AssemblyGeometry.PAD_REARM);
            e.setDeltaMovement(throwV); e.hurtMarked = true; e.hasImpulse = true; e.fallDistance = 0;
            SetPiecePayload.send(pad.level, Piece.MATTER_LAUNCH, pad.center, pad.center.add(throwV), friend ? 1 : 2, e);
            pad.level.playSound(null, pad.center.x, pad.center.y, pad.center.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, .8F, 1.35F);
            pad.level.playSound(null, pad.center.x, pad.center.y, pad.center.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1, 1.8F);
        }
    }

    /** A foe a Launch Pad threw within the last two seconds. */
    public static boolean airborne(Entity e) {
        Long until = AIRBORNE.get(e);
        return until != null && e.level().getGameTime() <= until;
    }

    /**
     * Kaleidoscope (the Ballistics technique that kept Faultline's node id): a ring of mirror cells two high snaps shut
     * round the foe you aim at, open to the sky. Your Shards ricochet off its walls up to eight times, so one shot fired
     * in rattles round inside, harder every bounce.
     */
    private static boolean kaleidoscope(ServerPlayer p) {
        LivingEntity target = aimEnemy(p, 32);
        if (target == null || target.getBbWidth() > 1.5 || target.getBbHeight() > 2.5) return false;
        BlockPos center = target.blockPosition();
        List<BlockPos> shape = AssemblyGeometry.kaleidoscope(center);
        AssemblyTerrain terrain = AssemblyTerrain.get(p.serverLevel());
        if (Matter.of(p) <= 0 || JOBS.size() >= MAX_JOBS
                || shape.stream().noneMatch(pos -> terrain.available(p, pos) && WorldMutationPolicy.canEdit(p, p.serverLevel(), pos))) return false;
        JOBS.add(new Job(p, shape, AssemblyGeometry.KALEIDO_TICKS, "kaleidoscope", false)
                .paced(AssemblyGeometry.KALEIDO_DELAY, AssemblyGeometry.KALEIDO_RATE));
        KALEIDOS.put(p.getUUID(), new Kaleido(p.serverLevel(), new HashSet<>(shape), Vec3.atBottomCenterOf(center),
                p.serverLevel().getGameTime() + AssemblyGeometry.KALEIDO_TICKS));
        SetPiecePayload.send(p.serverLevel(), Piece.MATTER_KALEIDO, Vec3.atBottomCenterOf(center), p.position(), 1, p);
        ArmPosePayload.show(p, ArmPose.MATTER_FORM, 12);
        sound(p, SoundEvents.AMETHYST_CLUSTER_BREAK, 1, 1.4F);
        p.serverLevel().playSound(null, center, SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1, .8F);
        return true;
    }

    /** Folded Shelter: a snug three-by-three hut round you, roofed, with a doorway behind (Clean Exit: a wide one). */
    private static List<BlockPos> shelter(ServerPlayer p) {
        BlockPos center = p.blockPosition(); Direction rear = p.getDirection().getOpposite();
        List<BlockPos> shape = new ArrayList<>();
        for (int y = 0; y <= 2; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            if (x == 0 && z == 0 && y < 2) continue;
            boolean rearFace = rear.getStepX() == 0 ? z == rear.getStepZ() : x == rear.getStepX();
            int across = rear.getStepX() == 0 ? x : z;
            if (y < 2 && rearFace && Math.abs(across) <= (has(p, "clean_exit") ? 1 : 0)) continue;
            shape.add(center.offset(x, y, z));
        }
        return shape;
    }
    private static boolean cradle(ServerPlayer p) {
        LivingEntity ally = aimAlly(p, 48); if (ally == null) ally = p;
        // Rescue only a consenting player (crouching), an ally on the same team, or the caster.
        if (ally != p && !p.isAlliedTo(ally) && !(ally instanceof Player other && other.isShiftKeyDown())) return false;
        BlockPos center = ally.blockPosition().below(2); List<BlockPos> shape = new ArrayList<>();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) shape.add(center.offset(x, 0, z));
        return begin(p, shape, 300, "cradle");
    }
    /**
     * Sentinel Engine: three turrets rise round you, each a two-cell pillar crowned with a turning crystal head.
     * For twenty seconds each finds the nearest enemy it can see within 32 blocks (an airborne one first) and shoots
     * Shards at it, which ricochet off your constructs like your own.
     */
    private static boolean cathedral(ServerPlayer p) {
        BlockPos center = p.blockPosition(); List<BlockPos> cells = new ArrayList<>(), heads = new ArrayList<>();
        float facing = p.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
        for (int i = 0; i < SENTINELS; i++) {
            double angle = facing + i * Math.PI * 2 / SENTINELS;
            BlockPos foot = center.offset((int) Math.round(-Math.sin(angle) * 3), 0, (int) Math.round(Math.cos(angle) * 3));
            cells.add(foot); cells.add(foot.above()); heads.add(foot.above());
        }
        if (!begin(p, cells, SENTINEL_TICKS, "cathedral")) return false;
        FORTRESSES.put(p.getUUID(), new Fortress(p, cells, heads));
        ArmPosePayload.show(p, ArmPose.MATTER_RAISE, 16);
        for (BlockPos head : heads)
            SetPiecePayload.send(p.serverLevel(), Piece.SENTINEL, Vec3.atCenterOf(head).add(0, .9, 0), p.position(), 1, null);
        p.serverLevel().playSound(null, center, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, .9F, 1.5F);
        return true;
    }

    // ------------------------------------------------------------------ shards

    /** Shard Lance: crystal formed at the hand from the orbit and thrown. Amplified, a second follows. */
    private static boolean lance(ServerPlayer p, int count, float damage) {
        if (!fire(p, count, damage, .12)) return false;
        ArmPosePayload.show(p, ArmPose.MATTER_FLICK, 8);
        sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, .9F, 1.8F);
        sound(p, SoundEvents.TRIDENT_THROW.value(), .7F, 1.6F);
        return true;
    }

    private static boolean scatter(ServerPlayer p, float damage) {
        if (!fire(p, 5, damage, .12)) return false;
        ArmPosePayload.show(p, ArmPose.MATTER_FLICK, 10);
        sound(p, SoundEvents.AMETHYST_CLUSTER_BREAK, 1, 1.5F);
        return true;
    }

    private static boolean fire(ServerPlayer p, int count, float damage, double spread) {
        if (shardCount(p) + count > 12) return false;
        Vec3 hand = hand(p);
        for (int i = 0; i < count; i++) {
            double angle = (i - (count - 1) / 2.0) * spread;
            Vec3 direction = p.getLookAngle().yRot((float) angle);
            NEW_SHARDS.add(new Shard(p, hand, direction, damage, true));
        }
        return true;
    }

    /** Where a Shard forms: just ahead of the casting hand. */
    private static Vec3 hand(ServerPlayer p) {
        Vec3 look = p.getLookAngle(), right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-4) right = new Vec3(1, 0, 0);
        return p.getEyePosition().add(look.scale(.55)).add(right.normalize().scale(.3)).subtract(0, .2, 0);
    }

    /** Fires one free Shard from {@code from} along {@code direction} (Monument's reward). */
    public static void launch(ServerPlayer p, Vec3 from, Vec3 direction, float damage) {
        if (shardCount(p) < 12) NEW_SHARDS.add(new Shard(p, from, direction, damage, false));
    }
    public static int shardCount(ServerPlayer p) {
        return (int) SHARDS.stream().filter(s -> s.owner == p).count() + (int) NEW_SHARDS.stream().filter(s -> s.owner == p).count();
    }
    public static int pendingCells(ServerPlayer p) { return JOBS.stream().filter(j -> j.owner == p).mapToInt(j -> j.pending.size()).sum(); }
    public static List<BlockPos> cells(ServerPlayer p) { return AssemblyTerrain.get(p.serverLevel()).owned(p); }
    /** The Launch Pads {@code p} keeps now (tests and the HUD). */
    public static int pads(ServerPlayer p) { return PADS.getOrDefault(p.getUUID(), new ArrayDeque<>()).size(); }
    /** The panels {@code p} keeps now. */
    public static int panels(ServerPlayer p) {
        ArrayDeque<Construct> panels = PANELS.get(p.getUUID());
        return panels == null ? 0 : (int) panels.stream().filter(c -> JOBS.contains(c.job) || standing(p, c)).count();
    }
    private static boolean canHarm(ServerPlayer p, LivingEntity target) {
        if (target == p || !target.isAlive() || target.isSpectator() || p.isAlliedTo(target)) return false;
        if (target instanceof Player other && (other.isCreative() || !p.server.isPvpAllowed() || !p.canHarmPlayer(other))) return false;
        if (target instanceof TamableAnimal pet && pet.isOwnedBy(p)) return false;
        return true;
    }
    /** A pad throws foes up only if they are really foes: monsters, players you may fight, or anything after you. */
    private static boolean hostileTo(ServerPlayer p, LivingEntity e) {
        return e instanceof Enemy || e instanceof Player || e instanceof Mob mob && mob.getTarget() == p || e.getLastHurtByMob() == p;
    }
    private static List<LivingEntity> enemies(ServerPlayer p, Vec3 center, double radius) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), t -> canHarm(p, t))
                .stream().sorted(Comparator.comparingDouble(t -> t.distanceToSqr(center))).limit(16).toList();
    }
    private static boolean visible(ServerPlayer p, Vec3 from, LivingEntity target) {
        return p.serverLevel().clip(new ClipContext(from, target.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
    }
    private static LivingEntity aimEnemy(ServerPlayer p, double range) { return aimed(p, range, false); }
    private static LivingEntity aimAlly(ServerPlayer p, double range) { return aimed(p, range, true); }
    private static LivingEntity aimed(ServerPlayer p, double range, boolean ally) {
        Vec3 start = p.getEyePosition(), end = start.add(p.getLookAngle().scale(range));
        HitResult terrain = p.serverLevel().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (terrain.getType() != HitResult.Type.MISS) end = terrain.getLocation();
        double closest = Double.MAX_VALUE; LivingEntity found = null;
        for (LivingEntity target : p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(1), t ->
                t != p && t.isAlive() && !t.isSpectator() && (ally ? p.isAlliedTo(t) || t instanceof Player other && other.isShiftKeyDown() : canHarm(p, t)))) {
            var hit = target.getBoundingBox().inflate(.3).clip(start, end);
            if (hit.isPresent() && start.distanceToSqr(hit.get()) < closest) { closest = start.distanceToSqr(hit.get()); found = target; }
        }
        return found;
    }

    /** How many times this Shard may still ricochet off the owner's matter. */
    private static int bounceLimit(Shard s, BlockPos cell) {
        Kaleido k = KALEIDOS.get(s.owner.getUUID());
        if (k != null && k.level == s.level && k.cells.contains(cell) && k.until > s.level.getGameTime()) return AssemblyGeometry.KALEIDO_BOUNCES;
        return AssemblyGeometry.SHARD_BOUNCES + (has(s.owner, "bank_shot") ? AssemblyGeometry.BANK_SHOT_BOUNCES : 0);
    }

    private static void tickShard(Shard s) {
        Vec3 start = s.position, end = start.add(s.direction.scale(Math.min(AssemblyGeometry.SHARD_SPEED, s.remaining)));
        BlockHitResult block = s.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, s.owner));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        LivingEntity victim = null; double nearest = Double.MAX_VALUE; Vec3 impact = end;
        for (LivingEntity target : s.level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(.45), t -> canHarm(s.owner, t))) {
            var hit = target.getBoundingBox().inflate(.3).clip(start, end);
            if (hit.isPresent() && hit.get().distanceToSqr(start) < nearest) {
                nearest = hit.get().distanceToSqr(start); victim = target; impact = hit.get();
            }
        }
        // The crystal in flight: one short piece per step, brighter for every bounce it has taken.
        SetPiecePayload.send(s.level, Piece.MATTER_SHARD, start, impact, 1 + Math.min(4, s.bounces) * .5F + (s.damage > 9 ? .25F : 0), null);
        s.remaining -= AssemblyGeometry.SHARD_SPEED;
        if (victim != null) {
            float damage = s.damage * AssemblyGeometry.bounced(s.bounces, has(s.owner, "bank_shot")) * (airborne(victim) ? AssemblyGeometry.AIRBORNE_BONUS : 1);
            if (victim.hurt(s.owner.damageSources().playerAttack(s.owner), damage)) {
                victim.knockback(.55, -s.direction.x, -s.direction.z);
                EvolutionTrials.advance(s.owner, ModAbilities.MATTER_ASSEMBLY_ID);
                if (s.split && has(s.owner, "splinter_storm")) {
                    int fragments = 0;
                    for (LivingEntity next : enemies(s.owner, impact, 10)) if (next != victim && fragments < 2
                            && visible(s.owner, impact.add(0, .2, 0), next) && SHARDS.size() + NEW_SHARDS.size() < MAX_SHARDS) {
                        Vec3 dir = next.getBoundingBox().getCenter().subtract(impact).normalize();
                        NEW_SHARDS.add(new Shard(s.owner, impact.add(dir.scale(.6)), dir, 4, false)); fragments++;
                    }
                }
            }
            s.level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, .6F, 1.7F);
            s.remaining = 0;
        } else if (block.getType() != HitResult.Type.MISS) {
            BlockPos cell = block.getBlockPos();
            if (s.bounces < bounceLimit(s, cell) && AssemblyTerrain.get(s.level).owned(s.level, cell, s.owner.getUUID())) {
                // A ricochet off your own matter: reflected about the face, harder for it.
                Vec3 normal = Vec3.atLowerCornerOf(block.getDirection().getNormal());
                s.direction = s.direction.subtract(normal.scale(2 * s.direction.dot(normal))).normalize();
                s.position = block.getLocation().add(normal.scale(.12)); s.bounces++;
                if (has(s.owner, "recall_angle")) for (LivingEntity next : enemies(s.owner, s.position, 12))
                    if (visible(s.owner, s.position, next)) { s.direction = next.getBoundingBox().getCenter().subtract(s.position).normalize(); break; }
                SetPiecePayload.send(s.level, Piece.MATTER_BOUNCE, s.position, s.position.add(normal), Math.min(4, s.bounces), null);
                s.level.playSound(null, s.position.x, s.position.y, s.position.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, .7F,
                        1.5F + .12F * s.bounces);
            } else {
                s.remaining = 0;
                s.level.playSound(null, end.x, end.y, end.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, .4F, 1.6F);
            }
        } else s.position = end;
    }

    // ------------------------------------------------------------------ taking things apart

    public static boolean dismantle(ServerPlayer p) {
        if (!valid(p, p.serverLevel())) return false;
        List<BlockPos> cells = cells(p); boolean had = !cells.isEmpty() || pendingCells(p) > 0 || pads(p) > 0;
        JOBS.removeIf(j -> j.owner == p); CROSSINGS.remove(p.getUUID()); FORTRESSES.remove(p.getUUID()); SKYWALKS.remove(p.getUUID());
        PANELS.remove(p.getUUID()); KALEIDOS.remove(p.getUUID());
        ArrayDeque<Pad> pads = PADS.remove(p.getUUID());
        if (pads != null) for (Pad pad : pads) retirePad(p, pad, true);
        collapse(p, p.serverLevel(), cells, true);
        if (had) ArmPosePayload.show(p, ArmPose.MATTER_CALL, 10);
        return had;
    }

    /**
     * Cells taken apart on purpose: their Matter flies home (Unstable Lattice turns up to six of them into Shards at
     * foes first, and those are spent).
     */
    private static void collapse(ServerPlayer p, ServerLevel level, List<BlockPos> cells, boolean weaponize) {
        int fragments = 0, home = 0; AssemblyTerrain terrain = AssemblyTerrain.get(level);
        List<BlockPos> gone = new ArrayList<>();
        for (BlockPos pos : cells) {
            boolean mine = terrain.owned(level, pos, p.getUUID());
            if (weaponize && mine && fragments < 6 && has(p, "unstable_lattice") && SHARDS.size() + NEW_SHARDS.size() < MAX_SHARDS && valid(p, level)) {
                Vec3 at = Vec3.atCenterOf(pos); LivingEntity target = enemies(p, at, 18).stream().filter(t -> visible(p, at, t)).findFirst().orElse(null);
                if (target != null) {
                    NEW_SHARDS.add(new Shard(p, at, target.getBoundingBox().getCenter().subtract(at), 5, false)); fragments++;
                    terrain.remove(level, pos, p.getUUID()); continue;
                }
            }
            if (terrain.remove(level, pos, p.getUUID()) && mine) { home++; gone.add(pos); }
        }
        if (!gone.isEmpty()) {
            Matter.returned(p, centroid(gone), home);
        }
    }

    /** {@link AssemblyTerrain} reports a cell whose lease ran out; its Matter flies home with the rest at the tick's end. */
    static void expired(UUID owner, BlockPos pos) { EXPIRED.computeIfAbsent(owner, id -> new ArrayList<>()).add(pos); }

    private static void flushExpired(MinecraftServer server) {
        if (EXPIRED.isEmpty()) return;
        for (var entry : EXPIRED.entrySet()) {
            ServerPlayer owner = owner(server, entry.getKey());
            if (owner != null) Matter.returned(owner, centroid(entry.getValue()), entry.getValue().size());
        }
        EXPIRED.clear();
    }

    /** An online owner by id, including game-test players the player list doesn't hold. */
    private static ServerPlayer owner(MinecraftServer server, UUID id) {
        ServerPlayer listed = server.getPlayerList().getPlayer(id);
        if (listed != null) return listed;
        for (Job j : JOBS) if (j.owner.getUUID().equals(id)) return j.owner;
        ArrayDeque<Pad> pads = PADS.get(id);
        if (pads != null && !pads.isEmpty()) return pads.peekFirst().owner;
        Skywalk walk = SKYWALKS.get(id);
        if (walk != null) return walk.owner;
        Fortress fort = FORTRESSES.get(id);
        return fort != null ? fort.owner : null;
    }

    // ------------------------------------------------------------------ awakening and ticking

    /** The awakening is visual and kinetic; it does not grant free building resources beyond a full orbit. */
    public static void awaken(ServerPlayer p) {
        Vec3 at = p.position().add(0, 1, 0);
        AbilityEffects.send(p.serverLevel(), Kind.MATTER_SHIELD, at, at.add(p.getLookAngle()), 5);
        AbilityEffects.send(p.serverLevel(), Kind.MATTER_WEAVE, at.add(0, 4, 0), at, 5);
        for (LivingEntity target : enemies(p, p.position(), 7)) if (visible(p, p.getEyePosition(), target)) {
            Vec3 away = target.position().subtract(p.position()).normalize();
            target.setDeltaMovement(target.getDeltaMovement().add(away.scale(1.4)).add(0, .3, 0)); target.hurtMarked = true;
        }
        Matter.fill(p);
        sound(p, SoundEvents.BEACON_POWER_SELECT, 1.1F, .6F);
    }

    public static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) AssemblyTerrain.get(level).tick(level);
        flushExpired(server);
        Matter.tick(server);
        long time = server.overworld().getGameTime();
        int budget = GLOBAL_CELL_BUDGET;
        for (Job job : List.copyOf(JOBS)) {
            if (!valid(job.owner, job.level)) { JOBS.remove(job); continue; }
            if (job.level.getGameTime() < job.startAt) continue;
            if (budget > 0) budget -= build(job, Math.min(job.rate, budget), 96);
            if (job.pending.isEmpty()) JOBS.remove(job);
        }
        for (Skywalk walk : List.copyOf(SKYWALKS.values())) {
            if (!valid(walk.owner, walk.level) || walk.level.getGameTime() >= walk.until) { SKYWALKS.remove(walk.owner.getUUID()); continue; }
            step(walk);
        }
        for (var entry : List.copyOf(PADS.entrySet())) {
            ArrayDeque<Pad> pads = entry.getValue();
            for (Pad pad : List.copyOf(pads)) {
                if (!valid(pad.owner, pad.level) || pad.level.getGameTime() >= pad.until) {
                    pads.remove(pad); retirePad(pad.owner, pad, valid(pad.owner, pad.owner.serverLevel())); continue;
                }
                tickPad(pad, pad.level.getGameTime());
                if ((pad.until - pad.level.getGameTime()) % 10 == 0) showPad(pad, false);
            }
            if (pads.isEmpty()) PADS.remove(entry.getKey());
        }
        KALEIDOS.values().removeIf(k -> k.until <= k.level.getGameTime());
        for (Fortress fort : List.copyOf(FORTRESSES.values())) {
            long now = fort.level.getGameTime();
            if (!valid(fort.owner, fort.level) || now >= fort.until || fort.owner.distanceToSqr(fort.anchor) > 48 * 48) { FORTRESSES.remove(fort.owner.getUUID()); continue; }
            AssemblyTerrain terrain = AssemblyTerrain.get(fort.level);
            if (has(fort.owner, "blueprint") && now % 10 == 0 && budget > 0) {
                for (BlockPos pos : fort.blueprint) if (fort.level.hasChunkAt(pos) && fort.level.getBlockState(pos).isAir() && Matter.take(fort.owner)) {
                    if (terrain.place(fort.owner, pos, (int) (fort.until - now))) { budget--; break; }
                    Matter.give(fort.owner, 1);
                }
            }
            // Each turret whose head still stands takes its turn to fire, staggered round the ring: airborne foes first.
            for (int i = 0; i < fort.heads.size(); i++) {
                BlockPos head = fort.heads.get(i);
                if (fort.volleys[i] <= 0 || (now + i * 5) % 14 != 0 || !terrain.owned(fort.level, head, fort.owner.getUUID())
                        || SHARDS.size() + NEW_SHARDS.size() >= MAX_SHARDS - 2) continue;
                Vec3 muzzle = Vec3.atCenterOf(head).add(0, 1.0, 0);
                List<LivingEntity> foes = new ArrayList<>(enemies(fort.owner, muzzle, SENTINEL_RANGE));
                foes.sort(Comparator.comparing((LivingEntity t) -> !airborne(t)));
                for (LivingEntity target : foes) if (visible(fort.owner, muzzle, target)) {
                    Vec3 aim = target.getBoundingBox().getCenter().subtract(muzzle);
                    NEW_SHARDS.add(new Shard(fort.owner, muzzle.add(aim.normalize().scale(.6)), aim, 6 + fort.owner.getRandom().nextInt(3), false));
                    fort.volleys[i]--;
                    fort.level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, .7F, 1.6F);
                    break;
                }
            }
        }
        SHARDS.addAll(NEW_SHARDS); NEW_SHARDS.clear();
        SHARDS.removeIf(s -> !valid(s.owner, s.level) || s.remaining <= 0 || !s.level.hasChunkAt(BlockPos.containing(s.position)));
        for (Shard shard : List.copyOf(SHARDS)) tickShard(shard);
        SHARDS.removeIf(s -> s.remaining <= 0);
        AIRBORNE.values().removeIf(until -> until < time - 40);
        CUSHIONED.values().removeIf(until -> until < time);
        REARM.values().removeIf(until -> until < time);
    }
    @SubscribeEvent public static void tickEvent(ServerTickEvent.Post event) { tick(event.getServer()); }
    @SubscribeEvent public static void chunkLoaded(net.neoforged.neoforge.event.level.ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)
            AssemblyTerrain.recoverChunk(level, chunk);
    }
    @SubscribeEvent public static void fall(LivingFallEvent event) {
        // A friend a Launch Pad threw lands without fall damage.
        Long cushion = CUSHIONED.get(event.getEntity());
        if (cushion != null && event.getEntity().level().getGameTime() <= cushion) { event.setDistance(0); event.setDamageMultiplier(0); return; }
        if (event.getEntity() instanceof ServerPlayer p && valid(p, p.serverLevel()) && has(p, "load_bearing")
                && AssemblyTerrain.get(p.serverLevel()).owned(p.serverLevel(), p.blockPosition().below(), p.getUUID())) {
            event.setDistance(0); event.setDamageMultiplier(0);
        }
    }
    @SubscribeEvent public static void threshold(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.getItemInHand(event.getHand()).isEmpty()
                || !valid(p, p.serverLevel()) || !has(p, "threshold")) return;
        BlockPos pos = event.getPos(); AssemblyTerrain terrain = AssemblyTerrain.get(p.serverLevel());
        if (!terrain.owned(p.serverLevel(), pos, p.getUUID()) || !WorldMutationPolicy.canEdit(p, p.serverLevel(), pos)) return;
        List<BlockPos> doorway = new ArrayList<>(List.of(pos));
        if (WorldMutationPolicy.canEdit(p, p.serverLevel(), pos.above())) doorway.add(pos.above());
        collapse(p, p.serverLevel(), doorway, false);
        event.setCanceled(true); sound(p, SoundEvents.COPPER_DOOR_OPEN, .6F, 1.6F);
    }
    @SubscribeEvent public static void defense(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !valid(p, p.serverLevel()) || event.getAmount() <= 0) return;
        long now = p.serverLevel().getGameTime();
        if (has(p, "pressure_valve") && event.getSource().getDirectEntity() instanceof LivingEntity attacker && canHarm(p, attacker)
                && attacker.distanceToSqr(p) < 16 && now >= VALVES.getOrDefault(p.getUUID(), -1000L)
                && cells(p).stream().anyMatch(pos -> pos.distSqr(p.blockPosition()) <= 10)) {
            Vec3 away = attacker.position().subtract(p.position()).normalize();
            attacker.setDeltaMovement(attacker.getDeltaMovement().add(away.scale(1.3)).add(0, .2, 0)); attacker.hurtMarked = true;
            VALVES.put(p.getUUID(), now + 40); AbilityEffects.at(p, Kind.MATTER_SHIELD, 2);
        }
    }
    /** Armor and potion reductions are final here; absorption is applied immediately after this event. */
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void secondFoundation(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !valid(p, p.serverLevel())) return;
        long now = p.serverLevel().getGameTime();
        float healthDamage = Math.max(0, event.getNewDamage() - p.getAbsorptionAmount());
        if (healthDamage >= p.getHealth() && has(p, "second_foundation") && p.getHealth() > 1
                && now >= FOUNDATIONS.getOrDefault(p.getUUID(), -1000L)) {
            // An emergency build spends no Matter: it must never fail a save for want of it.
            Job emergency = new Job(p, shelter(p), 160, "shelter", true); build(emergency, 12, 72);
            if (emergency.built.size() >= 4) {
                event.setNewDamage(Math.max(0, p.getHealth() + p.getAbsorptionAmount() - 1)); FOUNDATIONS.put(p.getUUID(), now + 1200);
                AbilityEffects.at(p, Kind.MATTER_SHIELD, 3);
            }
        }
    }
    public static void forget(ServerPlayer p) {
        JOBS.removeIf(j -> j.owner == p); SHARDS.removeIf(s -> s.owner == p); NEW_SHARDS.removeIf(s -> s.owner == p);
        CROSSINGS.remove(p.getUUID()); FORTRESSES.remove(p.getUUID()); VALVES.remove(p.getUUID()); SKYWALKS.remove(p.getUUID());
        PANELS.remove(p.getUUID()); PADS.remove(p.getUUID()); KALEIDOS.remove(p.getUUID()); EXPIRED.remove(p.getUUID());
        for (ServerLevel level : p.server.getAllLevels()) AssemblyTerrain.get(level).clearOwner(level, p.getUUID());
        Matter.forget(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) forget(p);
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (ServerPlayer p : List.copyOf(event.getServer().getPlayerList().getPlayers())) forget(p);
        JOBS.clear(); SHARDS.clear(); NEW_SHARDS.clear(); CROSSINGS.clear(); FORTRESSES.clear(); VALVES.clear(); FOUNDATIONS.clear(); SKYWALKS.clear();
        PANELS.clear(); PADS.clear(); KALEIDOS.clear(); AIRBORNE.clear(); CUSHIONED.clear(); REARM.clear(); EXPIRED.clear();
        Matter.clear();
    }
    private static void message(ServerPlayer p, String text) { p.displayClientMessage(Component.literal(text), true); }
    private static void sound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        p.serverLevel().playSound(null, p.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch);
    }
}
