package com.jayptucker.heroesevolved.expansion;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.service.*;
import com.jayptucker.heroesevolved.cryo.*;
import com.jayptucker.heroesevolved.electro.ElectroGeometry;
import com.jayptucker.heroesevolved.electro.ElectroService;
import com.jayptucker.heroesevolved.effects.*;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.*;
import java.util.*;

/** Bounded, short-lived techniques. No persistent AI flags or unbounded terrain scans. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class ExpansionSkills {
    private record Fault(ServerPlayer owner, ServerLevel level, List<Vec3> points, long at, Set<UUID> hit, boolean returning) {}
    private static final List<Fault> FAULTS = new ArrayList<>();
    private static final class Brace { final ServerPlayer owner; final long until; float stored; Brace(ServerPlayer p, long n) { owner = p; until = n + 100; } }
    private static final Map<UUID, Brace> BRACES = new HashMap<>();
    private record Sink(ServerPlayer owner, ServerLevel level, Vec3 center, long until, float[] charge) {}
    private static final Map<UUID, Sink> SINKS = new HashMap<>();
    private record Lattice(ServerPlayer owner, ServerLevel level, List<Vec3> anchors, long until, Set<UUID> struck) {}
    private static final Map<UUID, Lattice> LATTICES = new HashMap<>();
    private ExpansionSkills() {}
    private static long now(ServerPlayer p) { return p.getServer().overworld().getGameTime(); }
    public static boolean hostile(ServerPlayer p, LivingEntity t) {
        return t != p && t.isAlive() && !t.isSpectator() && !p.isAlliedTo(t)
                && !(t instanceof com.jayptucker.heroesevolved.entity.TemporalEchoEntity)
                && (!(t instanceof ServerPlayer other) || p.getServer().isPvpAllowed() && p.canHarmPlayer(other));
    }
    public static boolean friendly(ServerPlayer p, LivingEntity t) {
        return t == p || !(t instanceof Enemy) && (!(t instanceof ServerPlayer other) || p.isAlliedTo(other) || other.isShiftKeyDown());
    }
    public static List<LivingEntity> enemies(ServerPlayer p, Vec3 point, double radius) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(radius),
                t -> hostile(p, t) && t.position().distanceToSqr(point) <= radius * radius && p.hasLineOfSight(t))
                .stream().sorted(Comparator.comparingDouble(t -> t.position().distanceToSqr(point))).limit(24).toList();
    }
    public static BlockPos aim(ServerPlayer p) {
        var hit = p.level().clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(24)),
                net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.ANY, p));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : BlockPos.containing(p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(8))).below();
    }
    public static boolean canUse(AbilityUseContext c, String mechanic) {
        if (c.player().isPassenger() || !PowerRules.canAct(c.player())) return false;
        // Signature abilities: one of each at a time.
        return switch (mechanic) {
            case "blackout", "circuit_thread", "ground_sink", "arc_lattice" -> ElectroService.canStartCast(c.player());
            case "singularity" -> !com.jayptucker.heroesevolved.time.SingularityService.active(c.player());
            case "shared_blink" -> com.jayptucker.heroesevolved.time.SpaceTimeSkills.canEdge(c.player());
            case "phoenix" -> !com.jayptucker.heroesevolved.pyro.PhoenixService.active(c.player()) && !c.player().isInWaterOrBubble();
            default -> true;
        };
    }
    public static boolean activate(AbilityUseContext c, String mechanic) {
        switch (mechanic) {
            case "blackout" -> { return com.jayptucker.heroesevolved.electro.BlackoutService.activate(c.player(), c.abilityLevel()); }
            case "singularity" -> { return com.jayptucker.heroesevolved.time.SingularityService.activate(c.player(), c.abilityLevel()); }
            case "phoenix" -> { return com.jayptucker.heroesevolved.pyro.PhoenixService.activate(c.player(), c.abilityLevel()); }
            default -> { }
        }
        ServerPlayer p = c.player(); BlockPos target = aim(p);
        switch (mechanic) {
            case "fault_script" -> {
                if (FAULTS.size() >= 24) return false;
                List<Vec3> points = new ArrayList<>(); Vec3 direction = p.getLookAngle().multiply(1, 0, 1).normalize();
                for (int i = 1; i <= 12; i++) {
                    BlockPos step = BlockPos.containing(p.position().add(direction.scale(i)));
                    for (int y = 1; y >= -3; y--) {
                        BlockPos floor = step.offset(0, y - 1, 0);
                        if (!p.serverLevel().hasChunkAt(floor)) break;
                        if (p.level().getBlockState(floor).isFaceSturdy(p.level(), floor, Direction.UP) && p.level().getBlockState(floor.above()).getCollisionShape(p.level(), floor.above()).isEmpty()) {
                            points.add(Vec3.atBottomCenterOf(floor.above())); break;
                        }
                    }
                }
                if (points.isEmpty()) return false;
                int fuse = has(p, "quake_short_fuse") ? 20 : 40;
                FAULTS.add(new Fault(p, p.serverLevel(), List.copyOf(points), now(p) + fuse, new HashSet<>(), false));
                // A glowing seam is traced step by step along the ground, and burns until it answers.
                Vec3 last = p.position();
                for (Vec3 point : points) {
                    com.jayptucker.heroesevolved.quake.QuakeGeometry.show(p.serverLevel(), Piece.QUAKE_TRACE, last, point, fuse, null);
                    last = point;
                }
                ArmPosePayload.show(p, ArmPose.QUAKE_TRACE, 16);
                message(p, fuse == 20 ? "Fault written. It answers in a second, or to a Shockwave." : "Fault written. It answers in two seconds, or to a Shockwave.");
            }
            case "counterforce" -> {
                Brace b = BRACES.remove(p.getUUID());
                if (b == null) {
                    BRACES.put(p.getUUID(), new Brace(p, now(p)));
                    ArmPosePayload.show(p, ArmPose.QUAKE_BRACE, 100);
                    com.jayptucker.heroesevolved.quake.QuakeGeometry.show(p.serverLevel(), Piece.QUAKE_BRACE, p.position(), p.position(), 0, p);
                    message(p, "Brace: the next impacts become your counterwave. Cast again, or throw a Shockwave, to release.");
                }
                else release(b);
            }
            case "seamfinder" -> {
                int count = 0, maximum = has(p, "quake_seam_sight") ? 12 : 6;
                for (int y = 0; y < 4; y++) for (int x = -1; x <= 1; x++) {
                    BlockPos pos = target.offset(x, y, 0);
                    var state = p.level().getBlockState(pos); float hardness = state.getDestroySpeed(p.level(), pos);
                    if (++count > maximum) break;
                    PowerReactions.resonate(p.serverLevel(), pos, now(p) + 200);
                    if (!state.isAir() && hardness >= 0 && hardness <= (has(p, "quake_surgical_silence") ? 4 : 2)
                            && p.level().getBlockEntity(pos) == null && WorldMutationPolicy.canEdit(p, p.serverLevel(), pos)) p.serverLevel().destroyBlock(pos, true, p);
                }
                FacilityService.technique(p, target, "fracture");
                // A palm laid on the wall, and cracks run out from it along the seam.
                var face = p.level().clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(24)),
                        net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
                Vec3 contact = face.getType() == HitResult.Type.BLOCK ? face.getLocation() : Vec3.atCenterOf(target);
                Vec3 normal = face.getType() == HitResult.Type.BLOCK ? Vec3.atLowerCornerOf(face.getDirection().getNormal()) : p.getLookAngle().reverse();
                com.jayptucker.heroesevolved.quake.QuakeGeometry.show(p.serverLevel(), Piece.QUAKE_SEAM, contact, contact.add(normal),
                        maximum > 6 ? 1.5 : 1, null);
                ArmPosePayload.show(p, ArmPose.QUAKE_TOUCH, 14);
            }
            case "rime_loom", "stillheart", "brittle_constellation" -> { return CryoTechniqueService.activate(c, mechanic); }
            case "circuit_thread" -> {
                List<BlockPos> circuit = conductorNetwork(p.serverLevel(), target, has(p, "electrokinesis_copper_choir"));
                if (circuit.isEmpty()) { message(p, "Aim at iron, copper, a lightning rod, or a chain. Connected conductors carry the thread."); return false; }
                Set<UUID> struck = new HashSet<>(); Vec3 previous = ElectroGeometry.hand(p, 1, .68);
                int link = 0;
                for (BlockPos pos : circuit) {
                    Vec3 point = Vec3.atCenterOf(pos), contact = point.add(0, .53, 0);
                    // Current crawls from the palm into the first conductor, then block to block, lighting each one.
                    ElectroGeometry.show(p.serverLevel(), link == 0 ? Piece.ELECTRO_ARC : Piece.ELECTRO_THREAD, previous, contact, .8,
                            link == 0 ? p : null, Math.min(30, link++));
                    previous = contact;
                    FacilityService.technique(p, pos, "current");
                    for (LivingEntity t : enemies(p, point, has(p, "electrokinesis_live_wire") ? 2.5 : 1.5))
                        if (ElectroService.hostile(p, t) && struck.add(t.getUUID())) {
                        PowerReactions.electric(p, t); t.hurt(p.damageSources().playerAttack(p), 5 + c.abilityLevel());
                    }
                }
                ArmPosePayload.show(p, ArmPose.ELECTRO_TOUCH, 16);
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.COPPER_BULB_TURN_ON, SoundSource.PLAYERS, .8F, 1.6F);
            }
            case "ground_sink" -> {
                Sink old = SINKS.remove(p.getUUID());
                ArmPosePayload.show(p, ArmPose.ELECTRO_GROUND, 12);
                if (old != null) {
                    // The rod blows: its stored charge forks out of the tip into everything nearby.
                    Vec3 floor = old.center.subtract(0, .5, 0), tip = floor.add(0, 1.35, 0);
                    ElectroGeometry.show(p.serverLevel(), Piece.ELECTRO_SINK_BURST, floor, floor, 1, null);
                    int forks = 0;
                    for (var t : enemies(p, old.center, 7)) if (ElectroService.hostile(p, t)) {
                        t.hurt(p.damageSources().playerAttack(p), Math.min(16, old.charge[0]) + 2);
                        if (forks < 8) ElectroGeometry.show(p.serverLevel(), Piece.ELECTRO_ARC, tip, t.getBoundingBox().getCenter(), .8, null, 2 + forks++);
                    }
                    p.serverLevel().playSound(null, BlockPos.containing(tip), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, .9F, 1.3F);
                } else {
                    if (!p.level().getBlockState(target).isFaceSturdy(p.level(), target, Direction.UP)) return false;
                    Sink sink = new Sink(p, p.serverLevel(), Vec3.atCenterOf(target.above()), now(p) + (has(p, "electrokinesis_insulated_roots") ? 240 : 160), new float[]{0});
                    SINKS.put(p.getUUID(), sink);
                    // Driven down from the palm: a strike into the ground, and the rod stands where it hit.
                    Vec3 floor = sink.center.subtract(0, .5, 0);
                    ElectroGeometry.show(p.serverLevel(), Piece.ELECTRO_ARC, ElectroGeometry.hand(p, 1, .6), floor, 1.1, p);
                    ElectroGeometry.show(p.serverLevel(), Piece.ELECTRO_SINK, floor, floor, 1, null, 2);
                    p.serverLevel().playSound(null, target, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 1, .7F);
                    message(p, "Ground established. Nearby allies shed projectile energy into it. Cast again to discharge.");
                }
                FacilityService.technique(p, target, "current");
            }
            case "arc_lattice" -> {
                Vec3 point = Vec3.atCenterOf(target.above()); Lattice old = LATTICES.get(p.getUUID());
                List<Vec3> anchors = old != null && old.level == p.level() && old.until > now(p) ? new ArrayList<>(old.anchors) : new ArrayList<>();
                if (!anchors.isEmpty() && anchors.getLast().distanceToSqr(point) > 24 * 24) return false;
                if (anchors.size() == 3) anchors.removeFirst(); anchors.add(point);
                LATTICES.put(p.getUUID(), new Lattice(p, p.serverLevel(), List.copyOf(anchors), now(p) + (has(p, "electrokinesis_closed_circuit") ? 300 : 180), new HashSet<>()));
                // A spark flicked from two fingers plants an anchor where it lands.
                ArmPosePayload.show(p, ArmPose.ELECTRO_POINT, 10);
                ElectroGeometry.show(p.serverLevel(), Piece.ELECTRO_ARC, ElectroGeometry.hand(p, 1, .72), point, .5, p);
                ElectroGeometry.show(p.serverLevel(), Piece.ELECTRO_ANCHOR, point, point, 1, null, 2);
                p.serverLevel().playSound(null, target, SoundEvents.COPPER_PLACE, SoundSource.PLAYERS, 1, 1.5F);
                FacilityService.technique(p, target, "current");
            }
            case "ember_mine", "blue_lance", "fire_shepherd" -> { return com.jayptucker.heroesevolved.pyro.PyroService.technique(c, mechanic); }
            case "smoke_and_mirrors", "pickpocket", "cloak_field" -> { return com.jayptucker.heroesevolved.invisibility.InvisibilitySkills.technique(c, mechanic); }
            case "phase_carry", "phase_lock", "phase_fall" -> { return com.jayptucker.heroesevolved.phasing.DensitySkills.technique(c, mechanic); }
            case "sense_powers", "overcharge", "absorb_pain" -> { return com.jayptucker.heroesevolved.mimicry.MimicryService.technique(c, mechanic); }
            case "persuasion", "memory_walk", "hive_link" -> { return com.jayptucker.heroesevolved.telepathy.TelepathySkills.technique(c, mechanic); }
            default -> { return AdvancedTechniques.activate(c, mechanic); }
        }
        return true;
    }
    public static boolean has(ServerPlayer p, String id) { return SkillTreeService.has(p, id); }
    public static boolean preserved(Entity entity) { return CryoTechniqueService.preserved(entity); }
    public static int activeFields() { return FAULTS.size() + SINKS.size() + LATTICES.size() + CryoTechniqueService.activeFields(); }
    public static List<BlockPos> conductorNetwork(ServerLevel level, BlockPos start) {
        return conductorNetwork(level, start, false);
    }
    private static List<BlockPos> conductorNetwork(ServerLevel level, BlockPos start, boolean gaps) {
        List<BlockPos> result = new ArrayList<>(); Set<BlockPos> seen = new HashSet<>(); ArrayDeque<BlockPos> queue = new ArrayDeque<>(); queue.add(start.immutable());
        while (!queue.isEmpty() && seen.size() < 192 && result.size() < 48) {
            BlockPos pos = queue.removeFirst(); if (!seen.add(pos) || !level.hasChunkAt(pos) || pos.distSqr(start) > 24 * 24) continue;
            var block = level.getBlockState(pos).getBlock();
            if (block != Blocks.IRON_BLOCK && block != Blocks.IRON_BARS && block != Blocks.CHAIN && block != Blocks.LIGHTNING_ROD
                    && !net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath().contains("copper")) continue;
            result.add(pos);
            for (Direction d : Direction.values()) {
                queue.addLast(pos.relative(d));
                if (gaps && level.hasChunkAt(pos.relative(d, 2)) && level.getBlockState(pos.relative(d)).isAir()) queue.addLast(pos.relative(d, 2));
            }
        }
        return result;
    }
    /**
     * A Shockwave thrown while braced lets the stored force out through the palm instead: the brace ends and its stored
     * damage is returned, to be added to the blast.
     */
    public static float drainBrace(ServerPlayer p) {
        Brace b = BRACES.remove(p.getUUID());
        if (b == null) return 0;
        ArmPosePayload.clear(p);
        return Math.min(has(p, "quake_stolen_thunder") ? 24 : 16, b.stored);
    }

    public static boolean braced(ServerPlayer p) { return BRACES.containsKey(p.getUUID()); }

    /** A blast along a written fault sets it off at once. */
    public static void igniteFaults(ServerPlayer p, Vec3 origin, Vec3 direction, double length) {
        long time = now(p);
        for (int i = 0; i < FAULTS.size(); i++) {
            Fault f = FAULTS.get(i);
            if (f.owner != p || f.returning || time >= f.at) continue;
            for (Vec3 point : f.points) {
                Vec3 to = point.add(0, .5, 0).subtract(origin);
                double along = to.dot(direction);
                if (along < 0 || along > length + 1 || to.subtract(direction.scale(along)).length() > 2) continue;
                FAULTS.set(i, new Fault(f.owner, f.level, f.points, time, f.hit, false));
                break;
            }
        }
    }

    private static void release(Brace b) {
        if (!PowerRules.canAct(b.owner)) return;
        ArmPosePayload.show(b.owner, ArmPose.QUAKE_RELEASE, 12);
        Vec3 direction = b.owner.getLookAngle();
        for (var t : enemies(b.owner, b.owner.position(), 8)) if (t.position().subtract(b.owner.position()).normalize().dot(direction) > .3) {
            t.hurt(b.owner.damageSources().playerAttack(b.owner), Math.min(has(b.owner, "quake_stolen_thunder") ? 24 : 16, b.stored + 3));
            t.push(direction.x, .35, direction.z); t.hurtMarked = true;
        }
        if (has(b.owner, "quake_stolen_thunder") && b.stored > 0) {
            b.owner.push(direction.scale(-Math.min(1.5, b.stored / 10)).add(0, .4, 0)); b.owner.hurtMarked = true;
            b.owner.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(b.owner));
        }
        AbilityEffects.at(b.owner, Kind.QUAKE_PULSE, 8);
    }
    @SubscribeEvent(priority = EventPriority.HIGH) public static void damage(LivingIncomingDamageEvent e) {
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (CryoTechniqueService.damage(e)) return;
        Brace b = BRACES.get(e.getEntity().getUUID());
        if (b != null && PowerRules.canAct(b.owner) && now(b.owner) < b.until) {
            float absorbed = e.getAmount() * (has(b.owner, "quake_counter_balance") ? .7F : .5F);
            b.stored = Math.min(16, b.stored + absorbed); e.setAmount(e.getAmount() - absorbed);
        }
        if (e.getSource().getDirectEntity() instanceof Projectile) for (Sink sink : SINKS.values()) {
            if (sink.level != e.getEntity().level() || !friendly(sink.owner, e.getEntity()) || sink.center.distanceToSqr(e.getEntity().position()) > 36 || !PowerRules.canAct(sink.owner)) continue;
            float absorbed = e.getAmount() * (has(sink.owner, "electrokinesis_faraday_garden") ? .65F : .4F);
            sink.charge[0] = Math.min(16, sink.charge[0] + absorbed); e.setAmount(e.getAmount() - absorbed); break;
        }
    }
    @SubscribeEvent public static void pause(EntityTickEvent.Pre e) {
        if (e.getEntity() instanceof Mob && preserved(e.getEntity())) e.setCanceled(true);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e) { tick(e.getServer()); }
    public static void tick(MinecraftServer server) {
        CryoTechniqueService.tick(server);
        long time = server.overworld().getGameTime();
        for (Fault f : List.copyOf(FAULTS)) {
            if (f.level != f.owner.level() || !PowerRules.canAct(f.owner)) { FAULTS.remove(f); continue; }
            int step = (int) (time - f.at);
            if (step < 0) continue;
            if (step >= f.points.size()) {
                FAULTS.remove(f);
                if (!f.returning && has(f.owner, "quake_undertow")) {
                    List<Vec3> reverse = new ArrayList<>(f.points); Collections.reverse(reverse);
                    FAULTS.add(new Fault(f.owner, f.level, reverse, time + 10, new HashSet<>(), true));
                }
                continue;
            }
            Vec3 point = f.points.get(step); AbilityEffects.send(f.level, Kind.QUAKE_IMPACT, point, point.add(0, 1, 0), 2);
            FacilityService.technique(f.owner, BlockPos.containing(point).below(), "fracture");
            for (LivingEntity t : enemies(f.owner, point, 2)) if (f.hit.add(t.getUUID())) {
                PowerReactions.quake(f.owner, t); t.hurt(f.owner.damageSources().playerAttack(f.owner), 6); t.push(0, .4, 0);
                // Undertow's return pass drags them back toward the one who wrote it.
                if (f.returning) { Vec3 in = f.owner.position().subtract(t.position()).multiply(1, 0, 1); if (in.lengthSqr() > 1) t.push(in.normalize().scale(.7)); }
                t.hurtMarked = true;
            }
        }
        for (Brace b : BRACES.values()) if ((b.until - time) % 10 == 0 && time < b.until)
            com.jayptucker.heroesevolved.quake.QuakeGeometry.show(b.owner.serverLevel(), Piece.QUAKE_BRACE, b.owner.position(), b.owner.position(),
                    b.stored / 16F, b.owner);
        for (Brace b : List.copyOf(BRACES.values())) if (time >= b.until || !PowerRules.canAct(b.owner)) {
            BRACES.remove(b.owner.getUUID()); if (b.stored > 0 && PowerRules.canAct(b.owner)) release(b);
        }
        SINKS.values().removeIf(s -> time >= s.until || s.owner.level() != s.level || !PowerRules.canAct(s.owner));
        LATTICES.values().removeIf(s -> time >= s.until || s.owner.level() != s.level || !PowerRules.canAct(s.owner));
        if (time % 10 != 0) return;
        // Held constructs are re-sent as short pieces that fade in and out, so the copies hand over without a seam.
        for (Sink s : SINKS.values()) {
            Vec3 floor = s.center.subtract(0, .5, 0);
            ElectroGeometry.show(s.level, Piece.ELECTRO_SINK, floor, floor, 1 + Math.min(16, s.charge[0]) / 16F, null);
        }
        for (Lattice l : LATTICES.values()) for (Vec3 anchor : l.anchors) ElectroGeometry.show(l.level, Piece.ELECTRO_ANCHOR, anchor, anchor, 1, null);
        for (Lattice l : LATTICES.values()) for (int i = 1; i < l.anchors.size() + (has(l.owner, "electrokinesis_storm_geometry") && l.anchors.size() == 3 ? 1 : 0); i++) {
            Vec3 a = l.anchors.get(i - 1), b = l.anchors.get(i % l.anchors.size());
            ElectroGeometry.show(l.level, Piece.ELECTRO_WIRE, a, b, 1, null);
            Vec3 delta = b.subtract(a); double length = delta.lengthSqr();
            for (var t : enemies(l.owner, a.add(b).scale(.5), Math.sqrt(length) / 2 + 2)) {
                double fraction = length == 0 ? 0 : Math.clamp(t.position().subtract(a).dot(delta) / length, 0, 1);
                if (!ElectroService.hostile(l.owner, t) || t.position().distanceToSqr(a.add(delta.scale(fraction))) > 2.25
                        || !l.struck.add(t.getUUID())) continue;
                PowerReactions.electric(l.owner, t); t.hurt(l.owner.damageSources().playerAttack(l.owner), has(l.owner, "electrokinesis_storm_geometry") ? 10 : 6);
                FacilityService.technique(l.owner, t.blockPosition(), "current");
            }
        }
    }
    public static void forget(ServerPlayer p) {
        FAULTS.removeIf(f -> f.owner == p); BRACES.remove(p.getUUID()); SINKS.remove(p.getUUID()); LATTICES.remove(p.getUUID());
        CryoTechniqueService.forget(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent e) { FAULTS.clear(); BRACES.clear(); CryoTechniqueService.clear(); SINKS.clear(); LATTICES.clear(); }
    private static void message(ServerPlayer p, String text) { p.displayClientMessage(net.minecraft.network.chat.Component.literal(text), true); }
}
