package com.jayptucker.heroesevolved.expansion;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.service.*;
import com.jayptucker.heroesevolved.cryo.*;
import com.jayptucker.heroesevolved.effects.*;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
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
    private record Cocoon(ServerPlayer owner, LivingEntity target, Vec3 position, ServerLevel level, long until) {}
    private static final Map<UUID, Cocoon> COCOONS = new HashMap<>();
    private record Constellation(ServerPlayer owner, List<LivingEntity> targets, long until) {}
    private static final Map<UUID, Constellation> STARS = new HashMap<>();
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
        return !c.player().isPassenger() && PowerRules.canAct(c.player());
    }
    public static boolean activate(AbilityUseContext c, String mechanic) {
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
                FAULTS.add(new Fault(p, p.serverLevel(), List.copyOf(points), now(p) + (has(p, "quake_short_fuse") ? 20 : 40), new HashSet<>(), false));
                for (Vec3 point : points) AbilityEffects.send(p.serverLevel(), Kind.QUAKE_PULSE, point, point.add(0, .5, 0), .6);
                message(p, "Fault written. It answers in two seconds.");
            }
            case "counterforce" -> {
                Brace b = BRACES.remove(p.getUUID());
                if (b == null) { BRACES.put(p.getUUID(), new Brace(p, now(p))); message(p, "Brace: the next impacts become your counterwave. Cast again to release."); }
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
                AbilityEffects.send(p.serverLevel(), Kind.QUAKE_BEAM, p.getEyePosition(), Vec3.atCenterOf(target), 1);
            }
            case "rime_loom" -> {
                Vec3 direction = p.getLookAngle().multiply(1, 0, 1).normalize(); int placed = 0;
                int length = has(p, "cryokinesis_suspension_stitch") ? 24 : 16, width = has(p, "cryokinesis_double_weave") ? 1 : 0;
                for (int i = 1; i <= length; i++) for (int side = -width; side <= width; side++) {
                    Vec3 point = p.position().add(direction.scale(i)).add(-direction.z * side, -1, direction.x * side);
                    if (has(p, "cryokinesis_suspension_stitch") && p.getLookAngle().y > .25) point = point.add(0, i / 4, 0);
                    BlockPos pos = BlockPos.containing(point);
                    if (WorldMutationPolicy.canEdit(p, p.serverLevel(), pos)
                            && TemporaryBlocks.place(p.serverLevel(), pos, Blocks.PACKED_ICE.defaultBlockState(), 240, p)) placed++;
                }
                FacilityService.technique(p, target, "cold");
                if (placed == 0) return false;
                AbilityEffects.send(p.serverLevel(), Kind.CRYO_BOLT, p.position(), p.position().add(direction.scale(length)), 2);
            }
            case "stillheart" -> {
                LivingEntity t = p.isShiftKeyDown() ? p : Targeting.livingEntityInSight(p, 6, e -> friendly(p, e));
                if (t == null) t = p;
                if (COCOONS.containsKey(t.getUUID()) || COCOONS.size() >= 16) return false;
                COCOONS.put(t.getUUID(), new Cocoon(p, t, t.position(), p.serverLevel(), now(p) + (t instanceof ServerPlayer ? 60 : 120)));
                if (t instanceof Mob mob) { mob.getNavigation().stop(); mob.setTarget(null); }
                t.clearFire();
                if (has(p, "cryokinesis_sterile_ice")) for (var effect : t.getActiveEffects().stream().filter(e -> e.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL).toList()) t.removeEffect(effect.getEffect());
                AbilityEffects.send(p.serverLevel(), Kind.CRYO_FREEZE, t.position(), t.getEyePosition(), 2);
                message(p, "Stillheart: shelter now, recovery when the shell opens. Shift casts on yourself.");
            }
            case "brittle_constellation" -> {
                List<LivingEntity> targets = enemies(p, Vec3.atCenterOf(target), 8).stream().limit(4).toList();
                if (targets.isEmpty()) return false;
                STARS.put(p.getUUID(), new Constellation(p, targets, now(p) + (has(p, "cryokinesis_long_winter") ? 200 : 120)));
                for (var t : targets) { CryoService.freezeSolid(t, 40, p); AbilityEffects.send(p.serverLevel(), Kind.CRYO_BOLT, p.getEyePosition(), t.getEyePosition(), 1); }
                FacilityService.technique(p, target, "cold");
            }
            case "circuit_thread" -> {
                List<BlockPos> circuit = conductorNetwork(p.serverLevel(), target, has(p, "electrokinesis_copper_choir"));
                if (circuit.isEmpty()) { message(p, "Aim at iron, copper, a lightning rod, or a chain. Connected conductors carry the thread."); return false; }
                Set<UUID> struck = new HashSet<>(); Vec3 previous = p.getEyePosition();
                for (BlockPos pos : circuit) {
                    Vec3 point = Vec3.atCenterOf(pos);
                    AbilityEffects.send(p.serverLevel(), Kind.ELECTRIC_ARC, previous, point, 1); previous = point;
                    FacilityService.technique(p, pos, "current");
                    for (LivingEntity t : enemies(p, point, has(p, "electrokinesis_live_wire") ? 2.5 : 1.5)) if (struck.add(t.getUUID())) {
                        PowerReactions.electric(p, t); t.hurt(p.damageSources().playerAttack(p), 5 + c.abilityLevel());
                    }
                }
            }
            case "ground_sink" -> {
                Sink old = SINKS.remove(p.getUUID());
                if (old != null) {
                    for (var t : enemies(p, old.center, 7)) t.hurt(p.damageSources().playerAttack(p), Math.min(16, old.charge[0]) + 2);
                    AbilityEffects.send(p.serverLevel(), Kind.ELECTRIC_NOVA, old.center, old.center.add(0, 3, 0), 6);
                } else {
                    if (!p.level().getBlockState(target).isFaceSturdy(p.level(), target, Direction.UP)) return false;
                    SINKS.put(p.getUUID(), new Sink(p, p.serverLevel(), Vec3.atCenterOf(target.above()), now(p) + (has(p, "electrokinesis_insulated_roots") ? 240 : 160), new float[]{0}));
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
                AbilityEffects.send(p.serverLevel(), Kind.ELECTRIC_NOVA, point, point.add(0, 1, 0), 1);
                FacilityService.technique(p, target, "current");
            }
            case "ember_mine", "blue_lance", "fire_shepherd" -> { return com.jayptucker.heroesevolved.pyro.PyroService.technique(c, mechanic); }
            case "smoke_and_mirrors", "pickpocket", "cloak_field" -> { return com.jayptucker.heroesevolved.invisibility.InvisibilityService.technique(c, mechanic); }
            case "phase_carry", "phase_lock", "phase_fall" -> { return com.jayptucker.heroesevolved.phasing.PhasingService.technique(c, mechanic); }
            case "sense_powers", "overcharge", "absorb_pain" -> { return com.jayptucker.heroesevolved.mimicry.MimicryService.technique(c, mechanic); }
            default -> { return AdvancedTechniques.activate(c, mechanic); }
        }
        return true;
    }
    public static boolean has(ServerPlayer p, String id) { return SkillTreeService.has(p, id); }
    public static boolean preserved(Entity entity) { return COCOONS.containsKey(entity.getUUID()); }
    public static int activeFields() { return FAULTS.size() + COCOONS.size() + SINKS.size() + LATTICES.size() + STARS.size(); }
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
    private static void release(Brace b) {
        if (!PowerRules.canAct(b.owner)) return;
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
        Cocoon cocoon = COCOONS.get(e.getEntity().getUUID());
        if (cocoon != null) { e.setAmount(e.getAmount() * .2F); return; }
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
        for (var entry : List.copyOf(STARS.entrySet())) {
            Constellation stars = entry.getValue();
            if (!stars.targets.contains(e.getEntity()) || !CryoService.isFrozen(e.getEntity())) continue;
            STARS.remove(entry.getKey());
            if (!PowerRules.canAct(stars.owner)) continue;
            for (var t : stars.targets) if (t != e.getEntity() && t.isAlive() && t.level() == stars.owner.level()) {
                CryoService.releaseEntity(t);
                t.hurt(stars.owner.damageSources().playerAttack(stars.owner), has(stars.owner, "cryokinesis_shattered_sky") ? 7 : 4);
                if (has(stars.owner, "cryokinesis_shattered_sky") && t instanceof Mob && t.isAlive()) CryoService.freezeSolid(t, 40, stars.owner);
                AbilityEffects.send(stars.owner.serverLevel(), Kind.CRYO_BOLT, e.getEntity().getEyePosition(), t.getEyePosition(), 1.5);
            }
        }
    }
    @SubscribeEvent public static void pause(EntityTickEvent.Pre e) {
        if (e.getEntity() instanceof Mob && preserved(e.getEntity())) e.setCanceled(true);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e) { tick(e.getServer()); }
    public static void tick(MinecraftServer server) {
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
                PowerReactions.quake(f.owner, t); t.hurt(f.owner.damageSources().playerAttack(f.owner), 6); t.push(0, .4, 0); t.hurtMarked = true;
            }
        }
        for (Brace b : List.copyOf(BRACES.values())) if (time >= b.until || !PowerRules.canAct(b.owner)) {
            BRACES.remove(b.owner.getUUID()); if (b.stored > 0 && PowerRules.canAct(b.owner)) release(b);
        }
        for (Cocoon c : List.copyOf(COCOONS.values())) {
            if (time >= c.until || !c.target.isAlive() || c.target.level() != c.level || c.owner.level() != c.level || !PowerRules.canAct(c.owner) && c.target != c.owner) {
                COCOONS.remove(c.target.getUUID());
                if (c.target.isAlive() && c.target.level() == c.level) {
                    c.target.heal(has(c.owner, "cryokinesis_second_spring") ? 6 : 2);
                    if (has(c.owner, "cryokinesis_second_spring")) for (var ally : c.level.getEntitiesOfClass(LivingEntity.class, c.target.getBoundingBox().inflate(5), t -> t != c.target && friendly(c.owner, t) && c.target.hasLineOfSight(t))) ally.heal(2);
                }
                continue;
            }
            if (c.target instanceof ServerPlayer p && p.position().distanceToSqr(c.position) > .001) p.connection.teleport(c.position.x, c.position.y, c.position.z, p.getYRot(), p.getXRot());
            c.target.setPos(c.position); c.target.setDeltaMovement(Vec3.ZERO);
            if (time % 10 == 0) AbilityEffects.send(c.level, Kind.CRYO_FREEZE, c.position, c.target.getEyePosition(), 1.2);
        }
        STARS.values().removeIf(s -> time >= s.until || !PowerRules.canAct(s.owner));
        SINKS.values().removeIf(s -> time >= s.until || s.owner.level() != s.level || !PowerRules.canAct(s.owner));
        LATTICES.values().removeIf(s -> time >= s.until || s.owner.level() != s.level || !PowerRules.canAct(s.owner));
        if (time % 10 != 0) return;
        for (Sink s : SINKS.values()) AbilityEffects.send(s.level, Kind.ELECTRIC_NOVA, s.center, s.center.add(0, 1, 0), 1 + s.charge[0] / 8);
        for (Lattice l : LATTICES.values()) for (int i = 1; i < l.anchors.size() + (has(l.owner, "electrokinesis_storm_geometry") && l.anchors.size() == 3 ? 1 : 0); i++) {
            Vec3 a = l.anchors.get(i - 1), b = l.anchors.get(i % l.anchors.size()); AbilityEffects.send(l.level, Kind.ELECTRIC_ARC, a, b, 1);
            Vec3 delta = b.subtract(a); double length = delta.lengthSqr();
            for (var t : enemies(l.owner, a.add(b).scale(.5), Math.sqrt(length) / 2 + 2)) {
                double fraction = length == 0 ? 0 : Math.clamp(t.position().subtract(a).dot(delta) / length, 0, 1);
                if (t.position().distanceToSqr(a.add(delta.scale(fraction))) > 2.25 || !l.struck.add(t.getUUID())) continue;
                PowerReactions.electric(l.owner, t); t.hurt(l.owner.damageSources().playerAttack(l.owner), has(l.owner, "electrokinesis_storm_geometry") ? 10 : 6);
                FacilityService.technique(l.owner, t.blockPosition(), "current");
            }
        }
    }
    public static void forget(ServerPlayer p) {
        FAULTS.removeIf(f -> f.owner == p); BRACES.remove(p.getUUID()); STARS.remove(p.getUUID()); SINKS.remove(p.getUUID()); LATTICES.remove(p.getUUID());
        COCOONS.values().removeIf(c -> c.owner == p || c.target == p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent e) { FAULTS.clear(); BRACES.clear(); COCOONS.clear(); STARS.clear(); SINKS.clear(); LATTICES.clear(); }
    private static void message(ServerPlayer p, String text) { p.displayClientMessage(net.minecraft.network.chat.Component.literal(text), true); }
}
