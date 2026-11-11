package com.jayptucker.heroesevolved.assembly;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import com.jayptucker.heroesevolved.mimicry.MimicryService;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.progression.EvolutionTrials;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/**
 * Matter Assembly: the ground itself is the material. Matter is drawn up out of the world (the power costs
 * stamina, never the player's blocks) into lean constructs: a six-cell shield, plates that catch your every step,
 * a cage, sentinel turrets that shoot for you. Every cell is leased, gives no loot and vanishes safely. All
 * geometry and swept hits are server-owned.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class MatterAssemblyService {
    public static final int CELLS_PER_TICK = 24, GLOBAL_CELL_BUDGET = 96, MAX_JOBS = 32, MAX_SHARDS = 128;
    private static final List<Job> JOBS = new ArrayList<>();
    private static final List<Shard> SHARDS = new ArrayList<>(), NEW_SHARDS = new ArrayList<>();
    private static final Map<UUID, Crossing> CROSSINGS = new HashMap<>();
    private static final Map<UUID, Fortress> FORTRESSES = new HashMap<>();
    private static final Map<UUID, Skywalk> SKYWALKS = new HashMap<>();
    /** Skywalk: for this long, matter assembles under every step you take on air; each plate lasts a moment. */
    public static final int SKYWALK_TICKS = 160, PLATE_TICKS = 50;
    /** Sentinel Engine: three turrets, for this long, a Shard each every half second or so, this far. */
    public static final int SENTINELS = 3, SENTINEL_TICKS = 400, SENTINEL_VOLLEYS = 14;
    public static final double SENTINEL_RANGE = 32;
    private static final class Skywalk {
        final ServerPlayer owner; final ServerLevel level; final long until;
        Skywalk(ServerPlayer p) { owner = p; level = p.serverLevel(); until = level.getGameTime() + SKYWALK_TICKS; }
    }
    private static final Map<UUID, Long> VALVES = new HashMap<>(), FOUNDATIONS = new HashMap<>();
    private record Crossing(ServerLevel level, List<BlockPos> cells) {}
    private static final class Job {
        final ServerPlayer owner; final ServerLevel level; final ArrayDeque<BlockPos> pending;
        final List<BlockPos> built = new ArrayList<>(); final int duration; final String type;
        Job(ServerPlayer owner, Collection<BlockPos> shape, int duration, String type) {
            this.owner = owner; level = owner.serverLevel(); pending = new ArrayDeque<>(new LinkedHashSet<>(shape));
            this.duration = duration; this.type = type;
        }
    }
    /** Sentinel Engine's turrets: two-cell pillars, each with a crystal head that finds and shoots enemies. */
    private static final class Fortress {
        final ServerPlayer owner; final ServerLevel level; final Vec3 anchor; final List<BlockPos> blueprint;
        final List<BlockPos> heads; final int[] volleys;
        final long until;
        Fortress(ServerPlayer p, List<BlockPos> shape, List<BlockPos> heads) {
            owner = p; level = p.serverLevel(); anchor = p.position(); blueprint = List.copyOf(shape);
            this.heads = List.copyOf(heads); volleys = new int[heads.size()];
            java.util.Arrays.fill(volleys, SENTINEL_VOLLEYS);
            until = level.getGameTime() + SENTINEL_TICKS;
        }
    }
    private static final class Shard {
        final ServerPlayer owner; final ServerLevel level; final float damage; final boolean split;
        Vec3 position, direction; double remaining = 64; boolean banked;
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
            default -> mechanic.startsWith("matter_assembly_") ? mechanic.substring(16) : mechanic;
        };
    }
    public static boolean canUse(AbilityUseContext c, String mechanic) {
        ServerPlayer p = c.player(); String action = canonical(mechanic);
        if (!valid(p, p.serverLevel()) || !c.abilityId().equals(ModAbilities.MATTER_ASSEMBLY_ID)) return false;
        if (Set.of("switchback", "drawbridge", "scattershot", "faultline", "shelter", "cradle").contains(action)
                && !has(p, action)) return false;
        int tier = switch (action) { case "causeway" -> 2; case "cage" -> 3; case "cathedral" -> 5; default -> 1; };
        if (c.abilityLevel() < tier) return false;
        if (action.equals("drawbridge") && CROSSINGS.containsKey(p.getUUID())) return true;
        if (action.equals("causeway") && SKYWALKS.containsKey(p.getUUID())) return false;
        return Set.of("barricade", "shard_lance", "causeway", "cage", "cathedral", "switchback",
                "drawbridge", "scattershot", "faultline", "shelter", "cradle").contains(action)
                && SHARDS.size() + NEW_SHARDS.size() < MAX_SHARDS - 6
                && JOBS.size() < MAX_JOBS && JOBS.stream().filter(j -> j.owner == p).count() < 2;
    }
    public static boolean activate(AbilityUseContext c, String mechanic) {
        String action = canonical(mechanic); ServerPlayer p = c.player();
        if (!canUse(c, action)) return false;
        return switch (action) {
            case "barricade" -> begin(p, wall(p), 400, action);
            case "causeway" -> skywalk(p);
            case "switchback" -> begin(p, route(p, 14, true, false), 600, action);
            case "drawbridge" -> drawbridge(p);
            case "shard_lance" -> firePaid(p, 1, 3, 8 + c.abilityLevel() * 1.2F);
            case "scattershot" -> firePaid(p, 5, 5, 5 + c.abilityLevel() * .5F);
            case "cage" -> cage(p);
            case "cathedral" -> cathedral(p);
            case "faultline" -> begin(p, faultline(p), 140, action);
            case "shelter" -> begin(p, shelter(p), 260, action);
            case "cradle" -> cradle(p);
            default -> false;
        };
    }
    private static boolean begin(ServerPlayer p, List<BlockPos> shape, int duration, String type) {
        if (shape.isEmpty() || JOBS.size() >= MAX_JOBS) return false;
        Job job = new Job(p, shape, duration, type);
        // A denied/occupied shape is rejected before charging energy; never leave an unstarted delayed job.
        build(job, 4, 48);
        if (job.built.isEmpty()) return false;
        if (!job.pending.isEmpty()) JOBS.add(job);
        if (type.equals("drawbridge")) CROSSINGS.put(p.getUUID(), new Crossing(p.serverLevel(), job.built));
        rise(p, shape);
        sound(p, SoundEvents.BEACON_ACTIVATE, .6F, 1.7F); return true;
    }

    /** The ground gives up its matter: plates tear free round the construct and fly up into its shape. */
    private static void rise(ServerPlayer p, List<BlockPos> shape) {
        if (shape.isEmpty()) return;
        double x = 0, y = 0, z = 0;
        for (BlockPos pos : shape) { x += pos.getX() + .5; y += pos.getY() + .5; z += pos.getZ() + .5; }
        Vec3 center = new Vec3(x / shape.size(), y / shape.size(), z / shape.size());
        double size = 0;
        for (BlockPos pos : shape) size = Math.max(size, Vec3.atCenterOf(pos).distanceTo(center));
        com.jayptucker.heroesevolved.network.SetPiecePayload.send(p.serverLevel(), com.jayptucker.heroesevolved.styles.Piece.MATTER_RISE,
                center, p.position(), (float) Math.max(1.5, size + .5), null);
        sound(p, SoundEvents.GRAVEL_BREAK, .8F, .7F);
    }
    private static int build(Job job, int budget, int attempts) {
        int placed = 0; AssemblyTerrain terrain = AssemblyTerrain.get(job.level);
        while (!job.pending.isEmpty() && placed < budget && attempts-- > 0) {
            if (terrain.count(job.owner.getUUID()) >= AssemblyTerrain.OWNER_LIMIT) { job.pending.clear(); break; }
            BlockPos pos = job.pending.removeFirst();
            if (!terrain.place(job.owner, pos, job.duration)) continue;
            job.built.add(pos); placed++;
            if (job.type.equals("faultline")) {
                for (LivingEntity target : enemies(job.owner, Vec3.atCenterOf(pos), 1.65)) {
                    if (!visible(job.owner, Vec3.atCenterOf(pos).add(0, 1.2, 0), target)) continue;
                    if (target.hurt(job.owner.damageSources().playerAttack(job.owner), 5)) {
                        target.setDeltaMovement(target.getDeltaMovement().add(0, .65, 0)); target.hurtMarked = true;
                    }
                }
            }
        }
        if (placed > 0) AbilityEffects.send(job.level, Kind.MATTER_WEAVE, job.owner.getEyePosition(),
                Vec3.atCenterOf(job.built.getLast()), job.type.equals("cathedral") ? 2 : 1);
        return placed;
    }
    /** Foldwall: a lean three-wide, two-high shield (six cells); Buttressed Corners folds a wing back on each side. */
    private static List<BlockPos> wall(ServerPlayer p) {
        Direction forward = p.getDirection(), side = forward.getClockWise();
        BlockPos center = p.blockPosition().relative(forward, 2); List<BlockPos> result = new ArrayList<>();
        for (int y = 0; y < 2; y++) for (int x = -1; x <= 1; x++) result.add(center.relative(side, x).above(y));
        if (has(p, "buttress")) for (int sign : new int[]{-1, 1})
            for (int y = 0; y < 2; y++) result.add(center.relative(side, sign * 2).relative(forward.getOpposite()).above(y));
        return result;
    }

    /**
     * Skywalk: for eight seconds, matter assembles under every step you take on air, a three-wide plate across
     * your path. Jump to climb; crouch to drop through. Each plate crumbles a moment after you leave it.
     */
    private static boolean skywalk(ServerPlayer p) {
        if (SKYWALKS.containsKey(p.getUUID())) return false;
        SKYWALKS.put(p.getUUID(), new Skywalk(p));
        step(SKYWALKS.get(p.getUUID()));
        AbilityEffects.at(p, Kind.MATTER_SHIELD, 2);
        sound(p, SoundEvents.BEACON_ACTIVATE, .6F, 1.9F);
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
        for (int offset = -1; offset <= 1; offset++) if (terrain.place(p, below.relative(side, offset), PLATE_TICKS)) placed++;
        if (placed > 0) AbilityEffects.send(walk.level, Kind.MATTER_WEAVE, Vec3.atCenterOf(below).add(0, -1.5, 0), Vec3.atCenterOf(below), .6);
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
    /** Compression Cage: a tight three-by-three cell round one enemy, two high and roofed (25 cells). */
    private static boolean cage(ServerPlayer p) {
        LivingEntity target = aimEnemy(p, 64);
        if (target == null || target.getBbWidth() > 1.5 || target.getBbHeight() > 2.5) return false;
        BlockPos center = target.blockPosition(); List<BlockPos> shape = new ArrayList<>();
        for (int y = 0; y <= 2; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (Math.abs(x) == 1 || Math.abs(z) == 1 || y == 2) shape.add(center.offset(x, y, z));
        return begin(p, shape, 160, "cage");
    }
    /** Folded Shelter: a snug three-by-three hut round you, roofed, with a doorway behind (Clean Exit: a wide one). */
    private static List<BlockPos> shelter(ServerPlayer p) {
        BlockPos center = p.blockPosition(); Direction rear = p.getDirection().getOpposite();
        List<BlockPos> shape = new ArrayList<>();
        for (int y = 0; y <= 2; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            if (x == 0 && z == 0 && y < 2) continue;
            if (y < 2 && Math.abs(x) < 1 && Math.abs(z) < 1) continue;
            boolean rearFace = rear.getStepX() == 0 ? z == rear.getStepZ() : x == rear.getStepX();
            int across = rear.getStepX() == 0 ? x : z;
            if (y < 2 && rearFace && Math.abs(across) <= (has(p, "clean_exit") ? 1 : 0)) continue;
            if (y < 2 && Math.abs(x) < 1 && Math.abs(z) < 1) continue;
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
     * For twenty seconds each finds the nearest enemy it can see within 32 blocks and shoots Shards at it.
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
        for (BlockPos head : heads)
            com.jayptucker.heroesevolved.network.SetPiecePayload.send(p.serverLevel(), com.jayptucker.heroesevolved.styles.Piece.SENTINEL,
                    Vec3.atCenterOf(head).add(0, .9, 0), p.position(), 1, null);
        return true;
    }
    private static List<BlockPos> faultline(ServerPlayer p) {
        Vec3 flat = p.getLookAngle().multiply(1, 0, 1).normalize(); List<BlockPos> result = new ArrayList<>();
        if (flat.lengthSqr() < .5) return result;
        for (int i = 2; i <= 17; i++) {
            BlockPos pos = BlockPos.containing(p.position().add(flat.scale(i)));
            for (int dy = 2; dy >= -3; dy--) {
                BlockPos cell = pos.above(dy);
                if (p.serverLevel().hasChunkAt(cell) && p.serverLevel().getBlockState(cell).isAir()
                        && p.serverLevel().getBlockState(cell.below()).isFaceSturdy(p.serverLevel(), cell.below(), Direction.UP)) {
                    result.add(cell); break;
                }
            }
        }
        return result;
    }
    private static boolean firePaid(ServerPlayer p, int count, int materials, float damage) {
        if (shardCount(p) + count > 12) return false;
        for (int i = 0; i < count; i++) {
            double angle = (i - (count - 1) / 2.0) * .12;
            Vec3 direction = p.getLookAngle().yRot((float) angle);
            NEW_SHARDS.add(new Shard(p, p.getEyePosition().add(direction.scale(.4)), direction, damage, true));
        }
        sound(p, SoundEvents.TRIDENT_THROW.value(), .8F, 1.5F); return true;
    }
    public static int shardCount(ServerPlayer p) {
        return (int) SHARDS.stream().filter(s -> s.owner == p).count() + (int) NEW_SHARDS.stream().filter(s -> s.owner == p).count();
    }
    public static int pendingCells(ServerPlayer p) { return JOBS.stream().filter(j -> j.owner == p).mapToInt(j -> j.pending.size()).sum(); }
    public static List<BlockPos> cells(ServerPlayer p) { return AssemblyTerrain.get(p.serverLevel()).owned(p); }
    private static boolean canHarm(ServerPlayer p, LivingEntity target) {
        if (target == p || !target.isAlive() || target.isSpectator() || p.isAlliedTo(target)) return false;
        if (com.jayptucker.heroesevolved.phasing.DensityService.isIntangible(target)) return false;
        if (target instanceof Player other && (other.isCreative() || !p.server.isPvpAllowed() || !p.canHarmPlayer(other))) return false;
        if (target instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isOwnedBy(p)) return false;
        return true;
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
    private static void tickShard(Shard s) {
        Vec3 start = s.position, end = start.add(s.direction.scale(Math.min(2.8, s.remaining)));
        BlockHitResult block = s.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, s.owner));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        LivingEntity victim = null; double nearest = Double.MAX_VALUE; Vec3 impact = end;
        for (LivingEntity target : s.level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(.45), t -> canHarm(s.owner, t))) {
            var hit = target.getBoundingBox().inflate(.3).clip(start, end);
            if (hit.isPresent() && hit.get().distanceToSqr(start) < nearest) {
                nearest = hit.get().distanceToSqr(start); victim = target; impact = hit.get();
            }
        }
        if ((s.level.getGameTime() & 1) == 0 || victim != null || block.getType() != HitResult.Type.MISS)
            AbilityEffects.send(s.level, Kind.MATTER_SHARD, start, impact, s.damage > 9 ? 1.3 : .8);
        s.remaining -= 2.8;
        if (victim != null) {
            if (victim.hurt(s.owner.damageSources().playerAttack(s.owner), s.damage)) {
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
            s.remaining = 0;
        } else if (block.getType() != HitResult.Type.MISS) {
            if (!s.banked && has(s.owner, "bank_shot") && AssemblyTerrain.get(s.level).owned(s.level, block.getBlockPos(), s.owner.getUUID())) {
                Vec3 normal = Vec3.atLowerCornerOf(block.getDirection().getNormal());
                s.direction = s.direction.subtract(normal.scale(2 * s.direction.dot(normal))).normalize();
                s.position = block.getLocation().add(normal.scale(.12)); s.banked = true;
                if (has(s.owner, "recall_angle")) for (LivingEntity next : enemies(s.owner, s.position, 12))
                    if (visible(s.owner, s.position, next)) { s.direction = next.getBoundingBox().getCenter().subtract(s.position).normalize(); break; }
                sound(s.owner, SoundEvents.AMETHYST_BLOCK_HIT, .4F, 1.9F);
            } else s.remaining = 0;
        } else s.position = end;
    }
    public static boolean dismantle(ServerPlayer p) {
        if (!valid(p, p.serverLevel())) return false;
        List<BlockPos> cells = cells(p); boolean had = !cells.isEmpty() || pendingCells(p) > 0;
        JOBS.removeIf(j -> j.owner == p); CROSSINGS.remove(p.getUUID()); FORTRESSES.remove(p.getUUID()); SKYWALKS.remove(p.getUUID());
        collapse(p, p.serverLevel(), cells, true); return had;
    }
    private static void collapse(ServerPlayer p, ServerLevel level, List<BlockPos> cells, boolean weaponize) {
        int fragments = 0; AssemblyTerrain terrain = AssemblyTerrain.get(level); Vec3 center = p.position();
        for (BlockPos pos : cells) {
            if (weaponize && fragments < 6 && has(p, "unstable_lattice") && terrain.owned(level, pos, p.getUUID())
                    && SHARDS.size() + NEW_SHARDS.size() < MAX_SHARDS && valid(p, level)) {
                Vec3 at = Vec3.atCenterOf(pos); LivingEntity target = enemies(p, at, 18).stream().filter(t -> visible(p, at, t)).findFirst().orElse(null);
                if (target != null) { NEW_SHARDS.add(new Shard(p, at, target.getBoundingBox().getCenter().subtract(at), 5, false)); fragments++; }
            }
            if (terrain.remove(level, pos, p.getUUID())) center = Vec3.atCenterOf(pos);
        }
        if (!cells.isEmpty()) AbilityEffects.send(level, Kind.MATTER_COLLAPSE, center, center, Math.min(6, 1 + cells.size() / 20.0));
    }
    /** The awakening is visual and kinetic; it does not grant free building resources. */
    public static void awaken(ServerPlayer p) {
        Vec3 at = p.position().add(0, 1, 0);
        AbilityEffects.send(p.serverLevel(), Kind.MATTER_SHIELD, at, at.add(p.getLookAngle()), 5);
        AbilityEffects.send(p.serverLevel(), Kind.MATTER_WEAVE, at.add(0, 4, 0), at, 5);
        for (LivingEntity target : enemies(p, p.position(), 7)) if (visible(p, p.getEyePosition(), target)) {
            Vec3 away = target.position().subtract(p.position()).normalize();
            target.setDeltaMovement(target.getDeltaMovement().add(away.scale(1.4)).add(0, .3, 0)); target.hurtMarked = true;
        }
        sound(p, SoundEvents.BEACON_POWER_SELECT, 1.1F, .6F);
    }
    public static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) AssemblyTerrain.get(level).tick(level);
        int budget = GLOBAL_CELL_BUDGET;
        for (Job job : List.copyOf(JOBS)) {
            if (!valid(job.owner, job.level)) { JOBS.remove(job); continue; }
            if (budget > 0) budget -= build(job, Math.min(CELLS_PER_TICK, budget), 96);
            if (job.pending.isEmpty()) JOBS.remove(job);
        }
        for (Skywalk walk : List.copyOf(SKYWALKS.values())) {
            if (!valid(walk.owner, walk.level) || walk.level.getGameTime() >= walk.until) { SKYWALKS.remove(walk.owner.getUUID()); continue; }
            step(walk);
        }
        for (Fortress fort : List.copyOf(FORTRESSES.values())) {
            long now = fort.level.getGameTime();
            if (!valid(fort.owner, fort.level) || now >= fort.until || fort.owner.distanceToSqr(fort.anchor) > 48 * 48) { FORTRESSES.remove(fort.owner.getUUID()); continue; }
            AssemblyTerrain terrain = AssemblyTerrain.get(fort.level);
            if (has(fort.owner, "blueprint") && now % 10 == 0 && budget > 0) {
                for (BlockPos pos : fort.blueprint) if (fort.level.hasChunkAt(pos) && fort.level.getBlockState(pos).isAir()
                        && terrain.place(fort.owner, pos, (int) (fort.until - now))) { budget--; break; }
            }
            // Each turret whose head still stands takes its turn to fire, staggered round the ring.
            for (int i = 0; i < fort.heads.size(); i++) {
                BlockPos head = fort.heads.get(i);
                if (fort.volleys[i] <= 0 || (now + i * 5) % 14 != 0 || !terrain.owned(fort.level, head, fort.owner.getUUID())
                        || SHARDS.size() + NEW_SHARDS.size() >= MAX_SHARDS - 2) continue;
                Vec3 muzzle = Vec3.atCenterOf(head).add(0, 1.0, 0);
                for (LivingEntity target : enemies(fort.owner, muzzle, SENTINEL_RANGE)) if (visible(fort.owner, muzzle, target)) {
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
    }
    @SubscribeEvent public static void tickEvent(ServerTickEvent.Post event) { tick(event.getServer()); }
    @SubscribeEvent public static void chunkLoaded(net.neoforged.neoforge.event.level.ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)
            AssemblyTerrain.recoverChunk(level, chunk);
    }
    @SubscribeEvent public static void fall(LivingFallEvent event) {
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
        terrain.remove(p.serverLevel(), pos, p.getUUID());
        if (WorldMutationPolicy.canEdit(p, p.serverLevel(), pos.above())) terrain.remove(p.serverLevel(), pos.above(), p.getUUID());
        event.setCanceled(true); sound(p, SoundEvents.COPPER_DOOR_OPEN, .6F, 1.6F);
    }
    @SubscribeEvent public static void defense(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !valid(p, p.serverLevel()) || event.getAmount() <= 0) return;
        long now = p.serverLevel().getGameTime();
        if (has(p, "pressure_valve") && event.getSource().getDirectEntity() instanceof LivingEntity attacker && canHarm(p, attacker)
                && attacker.distanceToSqr(p) < 16 && now >= VALVES.getOrDefault(p.getUUID(), Long.MIN_VALUE)
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
                && now >= FOUNDATIONS.getOrDefault(p.getUUID(), Long.MIN_VALUE)) {
            Job emergency = new Job(p, shelter(p), 160, "shelter"); build(emergency, 12, 72);
            if (emergency.built.size() >= 4) {
                event.setNewDamage(Math.max(0, p.getHealth() + p.getAbsorptionAmount() - 1)); FOUNDATIONS.put(p.getUUID(), now + 1200);
                AbilityEffects.at(p, Kind.MATTER_SHIELD, 3);
            }
        }
    }
    public static void forget(ServerPlayer p) {
        JOBS.removeIf(j -> j.owner == p); SHARDS.removeIf(s -> s.owner == p); NEW_SHARDS.removeIf(s -> s.owner == p);
        CROSSINGS.remove(p.getUUID()); FORTRESSES.remove(p.getUUID()); VALVES.remove(p.getUUID()); SKYWALKS.remove(p.getUUID());
        for (ServerLevel level : p.server.getAllLevels()) AssemblyTerrain.get(level).clearOwner(level, p.getUUID());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) forget(p);
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (ServerPlayer p : List.copyOf(event.getServer().getPlayerList().getPlayers())) forget(p);
        JOBS.clear(); SHARDS.clear(); NEW_SHARDS.clear(); CROSSINGS.clear(); FORTRESSES.clear(); VALVES.clear(); FOUNDATIONS.clear(); SKYWALKS.clear();
    }
    private static void sound(ServerPlayer p, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        p.serverLevel().playSound(null, p.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch);
    }
}
