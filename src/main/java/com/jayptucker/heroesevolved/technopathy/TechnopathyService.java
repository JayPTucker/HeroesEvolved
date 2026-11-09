package com.jayptucker.heroesevolved.technopathy;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityRanges;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import com.jayptucker.heroesevolved.grid.GridDisruption;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.mimicry.MimicryService;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.progression.EvolutionTrials;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * Copper, iron and redstone become a bounded, physical control network. No inventory access,
 * spawned helper entities, chunk tickets, persistent block replacement or electrically typed damage.
 * All temporary commands remember one boolean, never an entire block snapshot to resurrect later.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class TechnopathyService {
    public static final int MAX_NODES = 24, MAX_SESSIONS = 64;
    private static final int MAX_VISITED = 160, MAX_COMMANDS = 18, MAX_WIRES = 3;
    private static final double NETWORK_RADIUS = 32, OWNER_TETHER = 80;
    private static final Map<UUID, Network> NETWORKS = new HashMap<>();
    private TechnopathyService() {}

    private static final class Network {
        final ServerPlayer owner;
        final ServerLevel level;
        final BlockPos root;
        final LinkedHashSet<BlockPos> nodes;
        final List<Wire> wires = new ArrayList<>();
        final List<Command> commands = new ArrayList<>();
        final Set<UUID> deflected = new HashSet<>(), cityHit = new HashSet<>();
        final List<BlockPos> engine = new ArrayList<>();
        final Set<UUID> engineHit = new HashSet<>();
        long until, interceptUntil, cityUntil, winchUntil;
        BlockPos interceptor, seal, winch;
        long sealUntil;
        UUID winchTarget;
        int engineIndex, flywheel, blackBox;
        Network(ServerPlayer p, BlockPos start, Collection<BlockPos> nodes) {
            owner = p; level = p.serverLevel(); root = start.immutable(); this.nodes = new LinkedHashSet<>(nodes);
            until = now(p) + 2400;
        }
    }
    private static final class Wire {
        final BlockPos a, b;
        final Set<UUID> hit = new HashSet<>();
        final long until;
        int charges;
        Wire(BlockPos a, BlockPos b, long until, int charges) {
            this.a = a.immutable(); this.b = b.immutable(); this.until = until; this.charges = charges;
        }
    }
    private static final class Command {
        final BlockPos pos;
        final Block block;
        final boolean before, desired, restore;
        final long at, release;
        boolean applied;
        Command(BlockPos pos, BlockState before, boolean desired, long at, long release, boolean restore) {
            this.pos = pos.immutable(); this.block = before.getBlock(); this.before = value(before);
            this.desired = desired; this.at = at; this.release = release; this.restore = restore;
        }
    }

    private static long now(ServerPlayer p) { return p.getServer().overworld().getGameTime(); }
    public static boolean isOwner(ServerPlayer p) {
        return PlayerAbilityService.getData(p).assignedPower().filter(a -> a.getKey().equals(ModAbilities.TECHNOPATHY_ID)
                && a.getValue().isUnlocked()).isPresent();
    }
    private static boolean authorized(ServerPlayer p) {
        return !p.isRemoved() && PowerRules.canAct(p) && (isOwner(p) || MimicryService.isBorrowingTechnopathy(p));
    }
    public static boolean canUse(AbilityUseContext c) {
        return c.abilityId().equals(ModAbilities.TECHNOPATHY_ID) && authorized(c.player());
    }
    private static int tier(AbilityUseContext c) {
        return Math.min(c.abilityLevel(), PlayerProgressionService.getLevel(c.player()));
    }
    private static boolean has(ServerPlayer p, String suffix) {
        return isOwner(p) && SkillTreeService.has(p, "technopathy_" + suffix);
    }
    private static Network current(ServerPlayer p) {
        Network n = NETWORKS.get(p.getUUID());
        return n != null && n.owner == p && n.level == p.serverLevel() && n.until > now(p)
                && p.position().distanceToSqr(Vec3.atCenterOf(n.root)) <= OWNER_TETHER * OWNER_TETHER ? n : null;
    }
    public static int nodeCount(ServerPlayer p) { Network n = current(p); return n == null ? 0 : n.nodes.size(); }
    public static int activeFields(ServerPlayer p) {
        Network n = current(p); if (n == null) return 0;
        return n.wires.size() + (n.interceptUntil > now(p) ? 1 : 0) + (n.cityUntil > now(p) ? 1 : 0)
                + (n.sealUntil > now(p) ? 1 : 0) + (n.winchUntil > now(p) ? 1 : 0) + n.commands.size();
    }

    public static boolean core(AbilityUseContext c, String action) {
        if (!canUse(c)) return false;
        int required = switch (action) { case "handshake", "remote_hand" -> 1; case "tripwire" -> 2;
            case "intercept" -> 3; case "machine_city" -> 5; default -> 99; };
        if (tier(c) < required) return false;
        return switch (action) {
            case "handshake" -> handshake(c.player());
            case "remote_hand" -> remoteHand(c.player());
            case "tripwire" -> tripwire(c.player());
            case "intercept" -> intercept(c.player());
            case "machine_city" -> machineCity(c.player());
            default -> false;
        };
    }
    public static boolean skill(AbilityUseContext c, int branch) {
        return technique(c, switch (branch) { case 0 -> "tech_batch_instruction";
            case 1 -> "tech_sentry_seal"; case 2 -> "tech_winchline"; default -> ""; });
    }
    public static boolean technique(AbilityUseContext c, String mechanic) {
        if (!canUse(c)) return false;
        String node = switch (mechanic) {
            case "tech_batch_instruction" -> "batch_instruction";
            case "tech_delayed_instruction" -> "delayed_instruction";
            case "tech_sentry_seal" -> "sentry_seal";
            case "tech_kill_switch" -> "kill_switch";
            case "tech_winchline" -> "winchline";
            case "tech_domino_engine" -> "domino_engine";
            default -> "";
        };
        if (node.isEmpty() || !has(c.player(), node)) return false;
        Network n = current(c.player());
        if (n == null) { message(c.player(), "Use Handshake on copper, iron or a redstone device first."); return false; }
        return switch (mechanic) {
            case "tech_batch_instruction" -> batch(n, c.modifierHeld());
            case "tech_delayed_instruction" -> delayed(n, c.modifierHeld());
            case "tech_sentry_seal" -> seal(n);
            case "tech_kill_switch" -> killSwitch(n);
            case "tech_winchline" -> winch(n, c.modifierHeld());
            case "tech_domino_engine" -> domino(n);
            default -> false;
        };
    }

    /** A conductor, or a powered machine (anything holding Forge Energy, or Create machinery), which relays too. */
    public static boolean isNode(ServerLevel level, BlockPos pos) {
        return isNode(level.getBlockState(pos)) || GridDisruption.isMachine(level, pos);
    }
    /** Visible physical conductors; non-vanilla machine blocks may relay, but their inventories are never opened. */
    public static boolean isNode(BlockState s) {
        Block b = s.getBlock();
        if (s.isAir()) return false;
        String path = BuiltInRegistries.BLOCK.getKey(b).getPath();
        return isControl(s) || b == Blocks.IRON_BLOCK || b == Blocks.IRON_BARS || b == Blocks.CHAIN
                || b == Blocks.LIGHTNING_ROD || b == Blocks.REDSTONE_WIRE || b instanceof DiodeBlock
                || b instanceof ObserverBlock || b instanceof PistonBaseBlock || b instanceof DispenserBlock
                || b instanceof HopperBlock || b == Blocks.REDSTONE_LAMP || b == Blocks.REDSTONE_BLOCK
                || path.contains("copper") || path.equals("centrifuge") || path.equals("synthesis_station");
    }
    public static boolean isControl(BlockState s) {
        Block b = s.getBlock();
        return b instanceof LeverBlock || b instanceof ButtonBlock || b instanceof DoorBlock
                || b instanceof TrapDoorBlock || b instanceof FenceGateBlock || b instanceof CopperBulbBlock;
    }
    private static BlockPos canonical(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos.immutable();
    }
    private static BooleanProperty property(BlockState s) {
        if (s.hasProperty(BlockStateProperties.OPEN)) return BlockStateProperties.OPEN;
        if (s.getBlock() instanceof CopperBulbBlock) return BlockStateProperties.LIT;
        return BlockStateProperties.POWERED;
    }
    private static boolean value(BlockState s) { return isControl(s) && s.getValue(property(s)); }

    public static List<BlockPos> network(ServerPlayer p, BlockPos start) {
        if (!authorized(p) || !p.serverLevel().hasChunkAt(start) || !isNode(p.serverLevel(), start)) return List.of();
        ServerLevel level = p.serverLevel();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(); Set<BlockPos> visited = new HashSet<>();
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>(); queue.add(start.immutable());
        boolean gaps = has(p, "air_gap");
        while (!queue.isEmpty() && visited.size() < MAX_VISITED && result.size() < MAX_NODES) {
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos) || pos.distSqr(start) > NETWORK_RADIUS * NETWORK_RADIUS || !level.hasChunkAt(pos)
                    || !isNode(level, pos) || !WorldMutationPolicy.canEdit(p, level, pos)) continue;
            result.add(canonical(level, pos));
            for (Direction direction : Direction.values()) {
                BlockPos adjacent = pos.relative(direction); queue.addLast(adjacent);
                if (gaps && level.hasChunkAt(adjacent) && level.getBlockState(adjacent).isAir()) queue.addLast(pos.relative(direction, 2));
            }
        }
        return List.copyOf(result);
    }
    private static BlockPos aim(ServerPlayer p) {
        double range = AbilityRanges.ranged(24);
        BlockHitResult hit = p.serverLevel().clip(new ClipContext(p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(range)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.BLOCK && p.serverLevel().hasChunkAt(hit.getBlockPos())
                ? canonical(p.serverLevel(), hit.getBlockPos()) : null;
    }
    private static BlockPos selected(Network n) {
        BlockPos pos = aim(n.owner);
        return pos != null && n.nodes.contains(pos) && isNode(n.level, pos) ? pos : null;
    }
    private static Vec3 point(BlockPos pos) { return Vec3.atCenterOf(pos).add(0, 1, 0); }
    private static boolean clear(ServerLevel level, Vec3 from, Vec3 to, Entity context) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context)).getType() == HitResult.Type.MISS;
    }
    private static boolean hostile(ServerPlayer p, LivingEntity target) {
        return ExpansionSkills.hostile(p, target) && !(target instanceof TamableAnimal tame && tame.isOwnedBy(p))
                && (target instanceof ServerPlayer || target instanceof Enemy
                || target instanceof Mob mob && mob.getTarget() == p || target.getLastHurtByMob() == p || p.getLastHurtByMob() == target);
    }
    private static List<LivingEntity> enemies(Network n, Vec3 center, double radius) {
        return n.level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), t -> hostile(n.owner, t)
                && t.getBoundingBox().getCenter().distanceToSqr(center) <= radius * radius
                && clear(n.level, center, t.getBoundingBox().getCenter(), n.owner)).stream()
                .sorted(Comparator.comparingDouble(t -> t.distanceToSqr(center))).limit(16).toList();
    }

    public static boolean handshake(ServerPlayer p) {
        if (!authorized(p)) return false;
        BlockPos target = aim(p);
        if (target == null) { message(p, "Aim at a physical machine, copper, iron or redstone conductor."); return false; }
        List<BlockPos> nodes = network(p, target);
        if (nodes.isEmpty() || !NETWORKS.containsKey(p.getUUID()) && NETWORKS.size() >= MAX_SESSIONS) return false;
        forget(p);
        Network n = new Network(p, target, nodes); NETWORKS.put(p.getUUID(), n);
        AbilityEffects.send(n.level, Kind.TECH_SCAN, p.getEyePosition(), point(target), Math.min(16, nodes.size()));
        showRoutes(n);
        message(p, "Handshake: " + nodes.size() + " linked nodes. Aim at an enrolled device to command it. Copper and iron carry your network.");
        sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, .65f, 1.5f);
        return true;
    }
    private static boolean remoteHand(ServerPlayer p) {
        Network n = current(p); if (n == null) { message(p, "Use Handshake to enrol a physical circuit first."); return false; }
        BlockPos pos = selected(n);
        if (pos == null || !isControl(n.level.getBlockState(pos))) { message(p, "Aim at a linked lever, button, door, gate, trapdoor or copper bulb."); return false; }
        boolean changed = operate(n, pos, !value(n.level.getBlockState(pos)));
        if (changed) AbilityEffects.send(n.level, Kind.TECH_LINK, p.getEyePosition(), Vec3.atCenterOf(pos), 1);
        return changed;
    }

    /** Public for integrations and tests; still requires real ownership, a live enrolled node and all claim hooks. */
    public static boolean operate(ServerPlayer p, BlockPos target, boolean desired) {
        Network n = current(p); return authorized(p) && n != null && operate(n, target, desired);
    }
    private static boolean operate(Network n, BlockPos target, boolean desired) {
        if (!n.level.hasChunkAt(target)) return false;
        BlockPos pos = canonical(n.level, target); BlockState s = n.level.getBlockState(pos);
        if (!n.nodes.contains(pos) || !isControl(s) || value(s) == desired || !permitted(n.owner, n.level, pos)) return false;
        if (s.getBlock() instanceof DoorBlock && (!n.level.hasChunkAt(pos.above()) || !permitted(n.owner, n.level, pos.above()))) return false;
        if (!desired && s.hasProperty(BlockStateProperties.OPEN) && occupied(n, pos)) return false;
        if (s.getBlock() instanceof LeverBlock lever) lever.pull(s, n.level, pos, n.owner);
        else if (s.getBlock() instanceof ButtonBlock button && desired) button.press(s, n.level, pos, n.owner);
        else if (s.getBlock() instanceof DoorBlock door) door.setOpen(n.owner, n.level, s, pos, desired);
        else {
            n.level.setBlock(pos, s.setValue(property(s), desired), 3);
            if (s.getBlock() instanceof ButtonBlock) {
                n.level.updateNeighborsAt(pos, s.getBlock());
                Direction support = s.getValue(BlockStateProperties.ATTACH_FACE) == net.minecraft.world.level.block.state.properties.AttachFace.FLOOR ? Direction.DOWN
                        : s.getValue(BlockStateProperties.ATTACH_FACE) == net.minecraft.world.level.block.state.properties.AttachFace.CEILING ? Direction.UP
                        : s.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
                n.level.updateNeighborsAt(pos.relative(support), s.getBlock());
            }
            n.level.gameEvent(n.owner, desired ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, pos);
            n.level.playSound(null, pos, SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS, .6f, desired ? 1.25f : .8f);
        }
        return value(n.level.getBlockState(pos)) == desired;
    }
    private static boolean permitted(ServerPlayer p, ServerLevel level, BlockPos pos) {
        if (!WorldMutationPolicy.canEdit(p, level, pos)) return false;
        // Dimension-change cleanup still checks the destination-world edit/claim event above.
        // A RightClickBlock event derives its world from its player, so do not forge one in another world.
        if (p.serverLevel() != level) return true;
        var event = new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, pos,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        return !NeoForge.EVENT_BUS.post(event).isCanceled() && event.getUseBlock() != net.neoforged.neoforge.common.util.TriState.FALSE;
    }
    private static boolean occupied(Network n, BlockPos pos) {
        // Never close a device onto its operator. Safety Interlock extends that courtesy to everyone.
        AABB box = new AABB(pos).inflate(.1).expandTowards(0, n.level.getBlockState(pos).getBlock() instanceof DoorBlock ? 1 : 0, 0);
        return !n.level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive()
                && (e == n.owner || has(n.owner, "safety_interlock"))).isEmpty()
                || box.intersects(n.owner.getBoundingBox());
    }

    private static boolean tripwire(ServerPlayer p) {
        Network n = current(p); if (n == null || n.nodes.size() < 2 || n.wires.size() >= MAX_WIRES) return false;
        BlockPos a = selected(n); if (a == null) return false;
        BlockPos b = n.nodes.stream().filter(pos -> pos.distSqr(a) >= 4 && pos.distSqr(a) <= 24 * 24
                && clear(n.level, point(a), point(pos), p)).max(Comparator.comparingDouble(pos -> pos.distSqr(a))).orElse(null);
        if (b == null || n.wires.stream().anyMatch(w -> w.a.equals(a) && w.b.equals(b) || w.a.equals(b) && w.b.equals(a))) return false;
        int charges = n.blackBox > 0 ? 2 : 1; if (charges == 2) n.blackBox--;
        n.wires.add(new Wire(a, b, now(p) + 1200, charges));
        AbilityEffects.send(n.level, Kind.TECH_LINK, point(a), point(b), 1);
        message(p, "Tripwire armed between physical relays. It trips hostile bodies, never allies. Shift + Tripwire clears your wires.");
        return true;
    }
    private static boolean intercept(ServerPlayer p) {
        Network n = current(p); if (n == null || n.interceptUntil > now(p)) return false;
        BlockPos pos = selected(n); if (pos == null) return false;
        n.interceptor = pos; n.interceptUntil = now(p) + 200; n.deflected.clear();
        AbilityEffects.send(n.level, Kind.TECH_CAGE, p.getEyePosition(), point(pos), 5);
        message(p, "Intercept: hostile incoming arrows are diverted for ten seconds. Walls still stop the field.");
        return true;
    }
    private static boolean machineCity(ServerPlayer p) {
        Network n = current(p); if (n == null || n.nodes.size() < 3 || n.cityUntil > now(p)) return false;
        n.cityUntil = now(p) + 240; n.cityHit.clear(); n.deflected.clear();
        AbilityEffects.send(n.level, Kind.TECH_SCAN, p.getEyePosition(), point(n.root), 16);
        showRoutes(n); sound(p, SoundEvents.BEACON_ACTIVATE, 1, .8f);
        message(p, "Machine City: eight relays become a coordinated interception and recoil district for twelve seconds.");
        return true;
    }

    private static List<BlockPos> controls(Network n) {
        return n.nodes.stream().filter(pos -> n.level.hasChunkAt(pos) && isControl(n.level.getBlockState(pos))).limit(6).toList();
    }
    private static boolean batch(Network n, boolean off) {
        int changed = 0;
        for (BlockPos pos : controls(n)) if (operate(n, pos, !off)) {
            changed++; AbilityEffects.send(n.level, Kind.TECH_LINK, n.owner.getEyePosition(), Vec3.atCenterOf(pos), .6);
        }
        return changed > 0;
    }
    private static boolean delayed(Network n, boolean off) {
        BlockPos selected = selected(n); if (selected == null || !isControl(n.level.getBlockState(selected))) return false;
        List<BlockPos> targets = has(n.owner, "clockwork") ? controls(n) : List.of(selected);
        int added = 0; long base = now(n.owner) + 60;
        for (BlockPos pos : targets) {
            if (n.commands.size() >= MAX_COMMANDS) break;
            BlockState state = n.level.getBlockState(pos); boolean desired = off ? false : !value(state);
            if (value(state) != desired && permitted(n.owner, n.level, pos)) {
                long at = base + added * 8L;
                n.commands.add(new Command(pos, state, desired, at, at + 40, has(n.owner, "echo_instruction")));
                added++;
                AbilityEffects.send(n.level, Kind.TECH_CAGE, point(n.root), Vec3.atCenterOf(pos), .8);
            }
        }
        if (added > 0) message(n.owner, "Instruction queued: three seconds, then " + added + " device" + (added == 1 ? "." : "s."));
        return added > 0;
    }
    private static boolean seal(Network n) {
        BlockPos pos = selected(n); if (pos == null || n.sealUntil > now(n.owner)) return false;
        n.seal = pos; n.sealUntil = now(n.owner) + 600;
        AbilityEffects.send(n.level, Kind.TECH_CAGE, point(n.root), point(pos), 3);
        return true;
    }
    private static boolean killSwitch(Network n) {
        BlockPos pos = selected(n);
        // An enrolled machine (Forge Energy or Create) is shut down outright for five seconds, then wakes as it was.
        if (pos != null && !isControl(n.level.getBlockState(pos)) && GridDisruption.isMachine(n.level, pos)) {
            if (!GridDisruption.stall(n.owner, n.level, pos, 100)) return false;
            AbilityEffects.send(n.level, Kind.TECH_LINK, n.owner.getEyePosition(), Vec3.atCenterOf(pos), .8);
            message(n.owner, "Kill Switch: the machine goes dead for five seconds.");
            return true;
        }
        if (pos == null || !isControl(n.level.getBlockState(pos)) || !value(n.level.getBlockState(pos))) return false;
        return temporary(n, pos, false, 100);
    }
    private static boolean temporary(Network n, BlockPos pos, boolean desired, int ticks) {
        if (n.commands.size() >= MAX_COMMANDS || n.commands.stream().anyMatch(c -> c.pos.equals(pos))) return false;
        BlockState state = n.level.getBlockState(pos);
        if (!operate(n, pos, desired)) return false;
        Command command = new Command(pos, state, desired, now(n.owner), now(n.owner) + ticks, true);
        command.applied = true; n.commands.add(command);
        AbilityEffects.send(n.level, Kind.TECH_LINK, n.owner.getEyePosition(), Vec3.atCenterOf(pos), .8);
        return true;
    }
    private static boolean winch(Network n, boolean self) {
        if (n.owner.isPassenger() || n.winchUntil > now(n.owner)) return false;
        BlockPos pos = selected(n); if (pos == null) return false;
        Vec3 point = point(pos);
        if (self && has(n.owner, "counterweight")) {
            if (!clear(n.level, n.owner.getBoundingBox().getCenter(), point, n.owner) || n.owner.distanceToSqr(point) > 24 * 24) return false;
            n.winchTarget = n.owner.getUUID();
        } else {
            LivingEntity target = enemies(n, point, 7).stream().findFirst().orElse(null);
            if (target == null) return false; n.winchTarget = target.getUUID();
        }
        n.winch = pos; n.winchUntil = now(n.owner) + 60;
        return true;
    }
    private static boolean domino(Network n) {
        if (n.nodes.size() < 3 || n.engineIndex < n.engine.size()) return false;
        n.engine.clear(); n.engine.addAll(n.nodes.stream().limit(12).toList()); n.engineIndex = 0; n.engineHit.clear();
        AbilityEffects.send(n.level, Kind.TECH_LINK, n.owner.getEyePosition(), point(n.engine.getFirst()), 1.5);
        return true;
    }

    /** Awakened recoil is a short spectacle, not a free combat/trial farm or an unprotected remote switch. */
    public static void awaken(ServerPlayer p) {
        Vec3 center = p.getBoundingBox().getCenter();
        AbilityEffects.send(p.serverLevel(), Kind.TECH_SCAN, p.getEyePosition(), center, 12);
        AbilityEffects.send(p.serverLevel(), Kind.TECH_PULSE, center, center.add(0, 1, 0), 5);
        sound(p, SoundEvents.BEACON_POWER_SELECT, 1, 1.4f);
        int shown = 0;
        for (BlockPos pos : BlockPos.betweenClosed(p.blockPosition().offset(-3, -2, -3), p.blockPosition().offset(3, 2, 3))) {
            if (shown >= 4) break;
            if (p.serverLevel().hasChunkAt(pos) && isNode(p.serverLevel().getBlockState(pos))) {
                AbilityEffects.send(p.serverLevel(), Kind.TECH_LINK, center, Vec3.atCenterOf(pos), .8); shown++;
            }
        }
        for (AbstractArrow arrow : p.serverLevel().getEntitiesOfClass(AbstractArrow.class, p.getBoundingBox().inflate(5), a -> incoming(p, a, center))) {
            if (shown++ >= 12) break;
            divert(p, arrow, center, false);
        }
    }

    private static boolean incoming(ServerPlayer owner, AbstractArrow arrow, Vec3 center) {
        // Tridents and ownerless dispensers are deliberately untouched: ownership cannot be established safely.
        return !(arrow instanceof ThrownTrident) && !arrow.isNoPhysics() && arrow.isAlive()
                && arrow.getOwner() instanceof LivingEntity shooter && hostile(owner, shooter)
                && arrow.getDeltaMovement().lengthSqr() > .0025
                && arrow.getDeltaMovement().dot(center.subtract(arrow.position())) > 0
                && clear(owner.serverLevel(), center, arrow.position(), owner);
    }
    private static void divert(ServerPlayer owner, AbstractArrow arrow, Vec3 center, boolean returnToSender) {
        Vec3 motion;
        if (returnToSender && arrow.getOwner() instanceof LivingEntity shooter && hostile(owner, shooter)
                && clear(owner.serverLevel(), arrow.position(), shooter.getEyePosition(), owner)) {
            motion = shooter.getEyePosition().subtract(arrow.position()).normalize().scale(Math.min(2.5, Math.max(.8, arrow.getDeltaMovement().length())));
            arrow.setOwner(owner);
        } else {
            Vec3 outward = arrow.position().subtract(center).normalize();
            motion = outward.scale(Math.min(1.5, Math.max(.65, arrow.getDeltaMovement().length()))).add(0, .5, 0);
        }
        arrow.setDeltaMovement(motion); arrow.hasImpulse = true; arrow.hurtMarked = true;
        AbilityEffects.send(owner.serverLevel(), Kind.TECH_LINK, center, arrow.position(), .6);
    }
    private static void interceptAt(Network n, BlockPos node) {
        Vec3 center = point(node); int work = 0;
        for (AbstractArrow arrow : n.level.getEntitiesOfClass(AbstractArrow.class, new AABB(center, center).inflate(5),
                a -> !n.deflected.contains(a.getUUID()) && incoming(n.owner, a, center))) {
            if (++work > 12 || n.deflected.size() >= 64) break;
            n.deflected.add(arrow.getUUID()); divert(n.owner, arrow, center, has(n.owner, "return_to_sender"));
            if (has(n.owner, "flywheel")) n.flywheel = Math.min(3, n.flywheel + 1);
            if (has(n.owner, "black_box")) n.blackBox = 1;
            EvolutionTrials.advance(n.owner, ModAbilities.TECHNOPATHY_ID);
        }
    }
    private static void trip(Network n, LivingEntity target, Vec3 from) {
        target.hurt(n.owner.damageSources().playerAttack(n.owner), 6 + PlayerProgressionService.getLevel(n.owner));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2), n.owner);
        if (has(n.owner, "interrupt_request")) { target.stopUsingItem(); target.setSprinting(false); }
        Vec3 away = target.position().subtract(from).multiply(1, 0, 1).normalize();
        double force = .6 + n.flywheel * .25; n.flywheel = 0;
        target.push(away.x * force, .2, away.z * force); target.hurtMarked = true;
        AbilityEffects.send(n.level, Kind.TECH_PULSE, from, from.add(0, 1, 0), 2);
        if (has(n.owner, "airlock")) for (BlockPos pos : controls(n)) {
            BlockState s = n.level.getBlockState(pos);
            if (pos.distToCenterSqr(target.position()) <= 8 * 8 && s.hasProperty(BlockStateProperties.OPEN) && value(s)) temporary(n, pos, false, 60);
        }
    }
    private static double segmentDistanceSquared(Vec3 point, Vec3 a, Vec3 b) {
        Vec3 line = b.subtract(a); double fraction = line.lengthSqr() < .001 ? 0 : Math.clamp(point.subtract(a).dot(line) / line.lengthSqr(), 0, 1);
        return point.distanceToSqr(a.add(line.scale(fraction)));
    }
    private static void tickWires(Network n, long time) {
        Iterator<Wire> iterator = n.wires.iterator();
        while (iterator.hasNext()) {
            Wire wire = iterator.next(); Vec3 a = point(wire.a), b = point(wire.b);
            if (wire.until <= time || wire.charges <= 0 || !n.nodes.contains(wire.a) || !n.nodes.contains(wire.b)
                    || !clear(n.level, a, b, n.owner)) { iterator.remove(); continue; }
            if (time % 20 == 0) AbilityEffects.send(n.level, Kind.TECH_LINK, a, b, .65);
            int checked = 0; boolean sensor = has(n.owner, "motion_sensor");
            for (LivingEntity target : n.level.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(sensor ? 6 : 1), t -> hostile(n.owner, t))) {
                if (++checked > 16) break;
                if (wire.hit.contains(target.getUUID())) continue;
                Vec3 body = target.getBoundingBox().getCenter();
                double distance = segmentDistanceSquared(body, a, b);
                if (sensor) {
                    Vec3 motion = target.getDeltaMovement();
                    if (motion.lengthSqr() > 36) motion = motion.normalize().scale(6);
                    for (int sample = 1; sample <= 8; sample++) distance = Math.min(distance, segmentDistanceSquared(body.subtract(motion.scale(sample * .25)), a, b));
                }
                if (distance <= .85 * .85 && clear(n.level, a, body, n.owner) && clear(n.level, b, body, n.owner)) {
                    wire.hit.add(target.getUUID()); trip(n, target, a.add(b).scale(.5));
                    if (--wire.charges <= 0) break;
                }
            }
        }
    }
    private static void tickCommands(Network n, long time) {
        Iterator<Command> iterator = n.commands.iterator();
        while (iterator.hasNext()) {
            Command command = iterator.next();
            if (!n.level.hasChunkAt(command.pos) || !n.level.getBlockState(command.pos).is(command.block) || !n.nodes.contains(command.pos)) {
                iterator.remove(); continue;
            }
            BlockState state = n.level.getBlockState(command.pos);
            if (!isControl(state)) { iterator.remove(); continue; }
            if (!command.applied && time >= command.at) {
                // An intervening user/redstone command cancels ours instead of overwriting their work.
                if (value(state) != command.before || !operate(n, command.pos, command.desired)) { iterator.remove(); continue; }
                command.applied = true;
                AbilityEffects.send(n.level, Kind.TECH_LINK, point(n.root), Vec3.atCenterOf(command.pos), .7);
                if (!command.restore) { iterator.remove(); continue; }
            }
            if (command.applied && time >= command.release) { restore(n, command); iterator.remove(); }
        }
    }
    private static void restore(Network n, Command command) {
        if (command.applied && command.restore && n.level.hasChunkAt(command.pos)) {
            BlockState s = n.level.getBlockState(command.pos);
            if (s.is(command.block) && isControl(s) && value(s) == command.desired) operate(n, command.pos, command.before);
        }
    }
    private static void tickWinch(Network n, long time) {
        if (n.winchUntil <= time || n.winch == null || !n.nodes.contains(n.winch)) return;
        LivingEntity target = n.winchTarget.equals(n.owner.getUUID()) ? n.owner
                : n.level.getEntity(n.winchTarget) instanceof LivingEntity living ? living : null;
        Vec3 anchor = point(n.winch);
        if (target == null || !target.isAlive() || target.isPassenger() || target != n.owner && !hostile(n.owner, target)
                || target.distanceToSqr(anchor) > 24 * 24 || !clear(n.level, target.getBoundingBox().getCenter(), anchor, n.owner)) { n.winchUntil = 0; return; }
        Vec3 difference = anchor.subtract(target.getBoundingBox().getCenter());
        if (difference.lengthSqr() < 1) { n.winchUntil = 0; return; }
        Vec3 motion = difference.normalize().scale(target == n.owner ? .65 : .32);
        // Vanilla movement resolves collision; no phase/teleport or no-gravity flags are left behind.
        target.setDeltaMovement(motion); target.hurtMarked = true; target.hasImpulse = true;
        if (time % 10 == 0) AbilityEffects.send(n.level, Kind.TECH_LINK, anchor, target.getBoundingBox().getCenter(), .8);
    }
    private static void tickCity(Network n, long time) {
        if (n.cityUntil <= time) return;
        int index = 0;
        for (BlockPos pos : n.nodes) {
            if (++index > 8) break;
            if (time % 4 == index % 4) interceptAt(n, pos);
            if (time % 10 != 0) continue;
            Vec3 center = point(pos);
            for (LivingEntity target : enemies(n, center, 3)) if (n.cityHit.size() < 48 && n.cityHit.add(target.getUUID())) trip(n, target, center);
            if (time % 20 == 0 && index <= 4) AbilityEffects.send(n.level, Kind.TECH_CAGE, point(n.root), center, 2.5);
        }
    }
    private static void tickEngine(Network n) {
        if (n.engineIndex >= n.engine.size()) return;
        BlockPos pos = n.engine.get(n.engineIndex++);
        if (!n.nodes.contains(pos)) return;
        Vec3 center = point(pos);
        AbilityEffects.send(n.level, Kind.TECH_PULSE, center, center.add(0, 1, 0), 2.5);
        Vec3 rail = n.engineIndex > 1 ? center.subtract(point(n.engine.get(n.engineIndex - 2))).normalize() : n.owner.getLookAngle().multiply(1, 0, 1).normalize();
        for (LivingEntity target : enemies(n, center, 3)) if (n.engineHit.add(target.getUUID())) {
            target.hurt(n.owner.damageSources().playerAttack(n.owner), 8 + PlayerProgressionService.getLevel(n.owner));
            Vec3 direction = has(n.owner, "recoil_circuit") ? rail : target.position().subtract(center).multiply(1, 0, 1).normalize();
            target.push(direction.x * 1.2, .35, direction.z * 1.2); target.hurtMarked = true;
        }
    }
    public static void tick(MinecraftServer server) {
        long time = server.overworld().getGameTime();
        if (time % 2 != 0) return;
        for (Network n : List.copyOf(NETWORKS.values())) {
            if (n.owner.getServer() != server) continue;
            if (!authorized(n.owner) || current(n.owner) != n || !n.level.hasChunkAt(n.root)
                    || !isNode(n.level, n.root)) { forget(n.owner); continue; }
            if (time % 20 == 0) {
                // Re-read the small graph, so severed cables really sever the district.
                Set<BlockPos> live = new HashSet<>(network(n.owner, n.root));
                // A disconnected controller must unwind before it loses its enrolled-node authorization.
                for (Command command : List.copyOf(n.commands)) if (!live.contains(command.pos)) restore(n, command);
                n.commands.removeIf(command -> !live.contains(command.pos));
                n.nodes.retainAll(live);
                if (n.nodes.isEmpty()) { forget(n.owner); continue; }
            }
            tickCommands(n, time); tickWires(n, time); tickWinch(n, time); tickCity(n, time);
            if (n.interceptUntil > time && n.interceptor != null && n.nodes.contains(n.interceptor)) {
                interceptAt(n, n.interceptor);
                if (time % 20 == 0) AbilityEffects.send(n.level, Kind.TECH_CAGE, point(n.root), point(n.interceptor), 5);
            }
            if (n.sealUntil > time && n.seal != null && n.nodes.contains(n.seal)) {
                Vec3 center = point(n.seal); List<LivingEntity> targets = enemies(n, center, 3);
                if (!targets.isEmpty()) { trip(n, targets.getFirst(), center); interceptAt(n, n.seal); n.sealUntil = 0; }
                else if (time % 20 == 0) AbilityEffects.send(n.level, Kind.TECH_CAGE, point(n.root), center, 3);
            }
            if (time % 4 == 0) tickEngine(n);
        }
    }
    private static void showRoutes(Network n) {
        Vec3 previous = n.owner.getEyePosition(); int count = 0;
        for (BlockPos pos : n.nodes) {
            if (++count > 4) break;
            Vec3 next = point(pos); AbilityEffects.send(n.level, Kind.TECH_LINK, previous, next, .7); previous = next;
        }
    }
    public static boolean cancel(ServerPlayer p, String action) {
        Network n = current(p); if (n == null) return false;
        return switch (action) {
            case "handshake" -> { forget(p); yield true; }
            case "tripwire" -> { boolean existed = !n.wires.isEmpty(); n.wires.clear(); yield existed; }
            case "intercept" -> { boolean existed = n.interceptUntil > now(p); n.interceptUntil = 0; yield existed; }
            case "machine_city" -> { boolean existed = n.cityUntil > now(p); n.cityUntil = 0; yield existed; }
            default -> false;
        };
    }
    public static void forget(ServerPlayer p) {
        Network n = NETWORKS.get(p.getUUID());
        if (n == null || n.owner != p) return;
        // Unwind before removing: restoring a boolean must still find its enrolled node.
        for (Command command : List.copyOf(n.commands)) restore(n, command);
        NETWORKS.remove(p.getUUID());
    }
    private static void message(ServerPlayer p, String text) { p.displayClientMessage(Component.literal(text), true); }
    private static void sound(ServerPlayer p, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        p.serverLevel().playSound(null, p.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch);
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void death(LivingDeathEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (Network n : List.copyOf(NETWORKS.values())) if (n.owner.getServer() == event.getServer()) forget(n.owner);
    }
}
