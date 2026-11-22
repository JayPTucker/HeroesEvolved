package com.jayptucker.heroesevolved.phasing;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.cryo.TemporaryBlocks;
import com.jayptucker.heroesevolved.entity.BlockDebris;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.jayptucker.heroesevolved.phasing.EarthGeometry.*;

/**
 * Earth that stays where it is put, for a while: pillars, walls (and the shove), Pillar Barrage, the Earth Tent and the
 * Rising Mountain. Pillars, walls and the tent are real blocks ({@link TemporaryBlocks}, so they put themselves back
 * after a crash), made of the ground they rose from ({@link EarthGeometry#construct}). They crumble back on their own;
 * mining one or blowing it up crumbles it too, and drops nothing. The Rising Mountain is a disc of block displays, so the
 * ground it lifts is never really dug up.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Earthworks {
    public enum Shape { PILLAR, WALL, TENT }

    /** A pillar, wall or tent: its cells in the order they rise, what it is made of, and when it crumbles. */
    static final class Construct {
        final ServerPlayer owner;
        final ServerLevel level;
        final Shape shape;
        final BlockState block;
        final Direction facing;
        final List<BlockPos> cells = new ArrayList<>();
        final List<BlockPos> queue = new ArrayList<>();
        final Set<UUID> struck = new HashSet<>();
        long until;
        int grown, perTick = 1, steps, delay;
        boolean shoving, launchSelf, crumbling;
        float damage;
        Construct(ServerPlayer owner, Shape shape, BlockState block, Direction facing, long until) {
            this.owner = owner; this.level = owner.serverLevel(); this.shape = shape; this.block = block; this.facing = facing; this.until = until;
        }
    }

    private record Key(ServerLevel level, BlockPos pos) {}

    private static final List<Construct> CONSTRUCTS = new ArrayList<>();
    private static final Map<Key, Construct> CELLS = new HashMap<>();
    private static final Map<UUID, Long> CUSHIONS = new HashMap<>();
    private static final Map<UUID, Mountain> MOUNTAINS = new HashMap<>();
    private static final Map<UUID, Surf> SURFS = new HashMap<>();

    private Earthworks() {}

    private static long now(Entity e) { return e.getServer().overworld().getGameTime(); }

    /**
     * Moves a body the earth carries. A mob's position otherwise reaches clients only every few ticks, so a carried mob's
     * move is sent at once (as Rift Fall does), or a foe on a rising disc hangs in the air after it lands.
     */
    static void carry(LivingEntity e, Vec3 at) {
        Takedowns.move(e, at);
        if (!(e instanceof ServerPlayer) && e.level() instanceof ServerLevel level)
            level.getChunkSource().broadcast(e, new net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket(e));
    }

    /** The enemy in sight within {@code range} and the earth under its feet, or null: earth moves rise under a foe you look at. */
    private static BlockPos underFoe(ServerPlayer p, double range, boolean metal) {
        LivingEntity foe = com.jayptucker.heroesevolved.ability.Targeting.livingEntityInSight(p, range, t -> ExpansionSkills.hostile(p, t));
        if (foe == null) return null;
        BlockPos ground = EarthGeometry.ground(p.level(), foe.position().add(0, .5, 0));
        return ground != null && EarthGeometry.earth(p.level().getBlockState(ground), metal) ? ground : null;
    }

    /** True for a moment after a pillar threw its own bender up: the landing hurts nothing. */
    public static boolean cushioned(ServerPlayer p) { return CUSHIONS.getOrDefault(p.getUUID(), 0L) > now(p); }

    /** A bender's standing constructs (for tests). */
    public static List<Construct> of(ServerPlayer p) { return CONSTRUCTS.stream().filter(c -> c.owner == p).toList(); }
    public static List<BlockPos> cells(Construct c) { return List.copyOf(c.cells); }
    public static Shape shape(Construct c) { return c.shape; }

    /** True if {@code pos} is part of an earth construct. */
    public static boolean isConstruct(ServerLevel level, BlockPos pos) { return CELLS.containsKey(new Key(level, pos)); }

    // ------------------------------------------------------------------
    // X: Pillar, Wall and the shove
    // ------------------------------------------------------------------

    /** X: a pillar out of the earth you aim at; with Shift, a wall rises in front of you. */
    public static boolean raise(ServerPlayer p, int tier, boolean wall) {
        DensityService.track(p);
        return wall ? wall(p, tier) : pillar(p, tier);
    }

    /** The second press, free: shove your newest standing wall forward as a battering ram. */
    public static boolean shove(AbilityUseContext c) {
        ServerPlayer p = c.player();
        if (c.modifierHeld()) return false;
        Construct wall = null;
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        for (Construct k : CONSTRUCTS) {
            if (k.owner != p || k.shape != Shape.WALL || k.shoving || k.crumbling || !k.queue.isEmpty() || k.cells.isEmpty()) continue;
            // Only the wall in front of you: aimed anywhere else, the key raises a pillar.
            Vec3 to = Vec3.atBottomCenterOf(k.cells.get(k.cells.size() / 2)).subtract(p.position()).multiply(1, 0, 1);
            if (to.length() <= 10 && to.normalize().dot(look) > .5) wall = k;
        }
        if (wall == null) return false;
        wall.shoving = true;
        wall.steps = 0;
        wall.damage = (SHOVE_DAMAGE + c.abilityLevel()) * DensityService.rooted(p);
        ArmPosePayload.show(p, ArmPose.EARTH_SHOVE, 12);
        p.serverLevel().playSound(null, wall.cells.get(0), SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 1.2F, 1.1F);
        return true;
    }

    static boolean pillar(ServerPlayer p, int tier) {
        boolean metal = SkillTreeService.has(p, DensitySkills.METAL);
        // Looking at a foe, the pillar rises under its feet; otherwise out of the earth (floor or wall) you aim at.
        BlockPos under = underFoe(p, PILLAR_RANGE, metal);
        BlockPos from;
        Direction face;
        if (under != null) { from = under; face = Direction.UP; }
        else {
            HitResult hit = p.pick(PILLAR_RANGE, 1, false);
            if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
                p.displayClientMessage(Component.translatable("density.heroesevolved.no_earth_there"), true);
                return false;
            }
            from = block.getBlockPos();
            face = block.getDirection();
        }
        BlockState ground = p.level().getBlockState(from);
        if (!EarthGeometry.earth(ground, metal)) {
            p.displayClientMessage(Component.translatable("density.heroesevolved.no_earth_there"), true);
            return false;
        }
        BlockPos base = from.relative(face);
        int height = PILLAR_HEIGHT + (tier >= 3 ? 1 : 0) + (tier >= 5 ? 1 : 0);
        Construct c = pillarAt(p, base, face, height, ground, (PILLAR_DAMAGE + tier) * DensityService.rooted(p));
        // Aimed at your own feet: the pillar throws you up.
        if (face == Direction.UP && Math.abs(p.getX() - (base.getX() + .5)) < .9 && Math.abs(p.getZ() - (base.getZ() + .5)) < .9
                && Math.abs(p.getY() - base.getY()) < 1.2) c.launchSelf = true;
        ArmPosePayload.show(p, face == Direction.UP ? ArmPose.EARTH_RAISE : ArmPose.EARTH_STRIKE, 12);
        return true;
    }

    /** A pillar of {@code height} rising from {@code base} along {@code face}, one block a tick. */
    static Construct pillarAt(ServerPlayer p, BlockPos base, Direction face, int height, BlockState ground, float damage) {
        Construct c = new Construct(p, Shape.PILLAR, EarthGeometry.construct(ground), face, now(p) + PILLAR_TICKS);
        for (int k = 0; k < height; k++) c.queue.add(base.relative(face, k));
        c.damage = damage;
        CONSTRUCTS.add(c);
        Vec3 at = Vec3.atCenterOf(base).subtract(Vec3.atLowerCornerOf(face.getNormal()).scale(.5));
        EarthGeometry.show(p.serverLevel(), Piece.EARTH_PILLAR, at, at.add(Vec3.atLowerCornerOf(face.getNormal()).scale(height)), height, null, ground);
        p.serverLevel().playSound(null, base, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.2F, .5F);
        p.serverLevel().playSound(null, base, SoundEvents.DEEPSLATE_PLACE, SoundSource.PLAYERS, 1, .5F);
        return c;
    }

    static boolean wall(ServerPlayer p, int tier) {
        Direction facing = p.getDirection();
        Direction right = facing.getClockWise();
        BlockPos center = p.blockPosition().relative(facing, WALL_GAP);
        boolean metal = SkillTreeService.has(p, DensitySkills.METAL);
        Construct c = null;
        BlockState first = null;
        List<BlockPos> rows = new ArrayList<>();
        for (int i = -WALL_HALF; i <= WALL_HALF; i++) {
            BlockPos column = center.relative(right, i);
            BlockPos ground = EarthGeometry.ground(p.level(), Vec3.atBottomCenterOf(column).add(0, 1.2, 0));
            if (ground == null) continue;
            BlockState state = p.level().getBlockState(ground);
            if (!EarthGeometry.earth(state, metal)) continue;
            if (first == null) first = state;
            rows.add(ground.above());
        }
        if (first == null) { p.displayClientMessage(Component.translatable("density.heroesevolved.no_earth_there"), true); return false; }
        c = new Construct(p, Shape.WALL, EarthGeometry.construct(first), facing, now(p) + WALL_TICKS);
        // Row by row from the ground up: the whole wall rises in three ticks.
        for (int y = 0; y < WALL_HEIGHT; y++) for (BlockPos base : rows) c.queue.add(base.above(y));
        c.perTick = rows.size();
        CONSTRUCTS.add(c);
        Vec3 mid = Vec3.atBottomCenterOf(center);
        EarthGeometry.show(p.serverLevel(), Piece.EARTH_WALL, mid, mid.add(Vec3.atLowerCornerOf(facing.getNormal())), WALL_HALF * 2 + 1, null, first);
        ArmPosePayload.show(p, ArmPose.EARTH_WALL, 14);
        p.serverLevel().playSound(null, center, SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 1.2F, .7F);
        p.serverLevel().playSound(null, center, SoundEvents.DEEPSLATE_PLACE, SoundSource.PLAYERS, 1.2F, .5F);
        CameraShakePayload.sendAround(p.serverLevel(), mid, 10, .15F, 5);
        return true;
    }

    /** Places the next cells of a rising construct, shoving whatever stands in them out of the way first. */
    private static void grow(Construct c) {
        for (int n = 0; n < c.perTick && !c.queue.isEmpty(); n++) {
            BlockPos cell = c.queue.remove(0);
            Vec3 push = Vec3.atLowerCornerOf(c.facing.getNormal());
            // A tent closes round whoever is inside it; a cell someone stands in is simply left open.
            if (c.shape != Shape.TENT) for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, new AABB(cell).inflate(.02))) {
                // Only a construct that hurts (a pillar) marks what it hit: a wall's rise must leave its shove a clean slate.
                boolean strike = e != c.owner && c.damage > 0 && ExpansionSkills.hostile(c.owner, e) && c.struck.add(e.getUUID());
                // A boss is hurt but stays put, and the construct stops against it.
                boolean fixed = DensityService.immovable(e);
                if (c.shape == Shape.PILLAR && c.facing == Direction.UP) {
                    // Whatever stands on a rising pillar rides it up, and is thrown off the top.
                    if (!fixed) carry(e, new Vec3(e.getX(), cell.getY() + 1.02, e.getZ()));
                    if (strike) StyleHits.hurt(e, c.owner.damageSources().playerAttack(c.owner), c.damage);
                } else {
                    Vec3 to = e.position().add(push.multiply(1.05, 0, 1.05)).add(0, c.facing.getAxis().isVertical() ? push.y * 1.05 : 0, 0);
                    if (!fixed && com.jayptucker.heroesevolved.speed.SpeedService.safe(e, to)) carry(e, to);
                    if (strike) {
                        StyleHits.hurt(e, c.owner.damageSources().playerAttack(c.owner), c.damage);
                        if (!fixed) StyleHits.shove(e, push.scale(1.1).add(0, .35, 0));
                    }
                }
            }
            if (!put(c, cell, (int) (c.until - c.level.getGameTime()) + 60)) {
                // Something in the way (a wall, a body that wouldn't move): this line of the construct stops here.
                if (c.shape == Shape.PILLAR) c.queue.clear();
                continue;
            }
            c.cells.add(cell);
            CELLS.put(new Key(c.level, cell), c);
            DensityService.bankEarth(c.owner, 1);
            if (c.grown++ % 2 == 0) Stones.dust(c.level, c.block, Vec3.atCenterOf(cell), 4, .45);
        }
        if (c.queue.isEmpty() && c.shape == Shape.PILLAR && !c.cells.isEmpty()) launch(c);
    }

    /** A pillar has finished rising: whatever stands on top is thrown up off it. */
    private static void launch(Construct c) {
        if (c.facing != Direction.UP) return;
        BlockPos top = c.cells.get(c.cells.size() - 1);
        for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, new AABB(top.above()).inflate(.3, .6, .3))) {
            if (e == c.owner && !c.launchSelf || DensityService.immovable(e)) continue;
            double up = .9 + .1 * c.cells.size();
            e.setDeltaMovement(e.getDeltaMovement().multiply(1, 0, 1).add(0, up, 0));
            e.hurtMarked = true;
            if (e instanceof ServerPlayer player) {
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
                if (player == c.owner) CUSHIONS.put(player.getUUID(), now(player) + 80);
            }
        }
        c.level.playSound(null, top, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1, .6F);
    }

    /** A shoved wall moves one block along its facing, climbing a step or dropping a little as the ground goes. */
    private static void step(Construct c) {
        Vec3 push = Vec3.atLowerCornerOf(c.facing.getNormal());
        List<BlockPos> next = new ArrayList<>();
        Map<Long, Integer> drop = new HashMap<>();
        // Work column by column, so each column follows its own ground.
        Map<Long, List<BlockPos>> columns = new HashMap<>();
        for (BlockPos cell : c.cells) columns.computeIfAbsent(BlockPos.asLong(cell.getX(), 0, cell.getZ()), k -> new ArrayList<>()).add(cell);
        for (List<BlockPos> column : columns.values()) {
            BlockPos bottom = column.stream().min((a, b) -> a.getY() - b.getY()).orElseThrow().relative(c.facing);
            int dy = 0;
            if (!replaceable(c.level, bottom)) dy = replaceable(c.level, bottom.above()) ? 1 : 99;
            else if (replaceable(c.level, bottom.below())) dy = replaceable(c.level, bottom.below(2)) ? -2 : -1;
            if (dy == 99) continue;
            for (BlockPos cell : column) next.add(cell.relative(c.facing).above(dy));
        }
        for (BlockPos cell : List.copyOf(c.cells)) { CELLS.remove(new Key(c.level, cell)); TemporaryBlocks.restore(c.level, cell, null); }
        c.cells.clear();
        for (BlockPos cell : next) {
            for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, new AABB(cell).inflate(.05))) {
                if (e == c.owner) continue;
                if (ExpansionSkills.hostile(c.owner, e) && c.struck.add(e.getUUID()))
                    StyleHits.hurt(e, c.owner.damageSources().playerAttack(c.owner), c.damage);
                if (DensityService.immovable(e)) continue;
                Vec3 to = e.position().add(push.scale(1.2));
                if (com.jayptucker.heroesevolved.speed.SpeedService.safe(e, to)) carry(e, to);
                StyleHits.shove(e, push.scale(1.3).add(0, .4, 0));
            }
            if (put(c, cell, 200)) { c.cells.add(cell); CELLS.put(new Key(c.level, cell), c); }
        }
        if (!c.cells.isEmpty()) {
            Vec3 mid = Vec3.atBottomCenterOf(c.cells.get(c.cells.size() / 2));
            Stones.dust(c.level, c.block, mid, 10, 1.2);
            c.level.playSound(null, BlockPos.containing(mid), SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1, .5F);
            EarthGeometry.show(c.level, Piece.EARTH_SHOVE, mid, mid.add(push), c.cells.size(), null, c.block);
            DensityService.bankEarth(c.owner, c.cells.size());
        }
        if (++c.steps >= SHOVE_STEPS || c.cells.isEmpty()) c.until = c.level.getGameTime();
    }

    private static boolean replaceable(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty();
    }

    /** A construct falls apart from the top down, a block a tick, putting back what was there. */
    private static void crumble(Construct c) {
        c.crumbling = true;
        if (c.cells.isEmpty()) return;
        int n = c.shape == Shape.PILLAR ? 1 : Math.max(1, c.cells.size() / 4);
        for (int i = 0; i < n && !c.cells.isEmpty(); i++) {
            BlockPos cell = c.cells.remove(c.cells.size() - 1);
            CELLS.remove(new Key(c.level, cell));
            TemporaryBlocks.restore(c.level, cell, null);
            Stones.dust(c.level, c.block, Vec3.atCenterOf(cell), 6, .4);
            if (c.level.getRandom().nextInt(3) == 0)
                c.level.playSound(null, cell, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, .7F, .6F);
        }
    }

    /** A boulder struck a block: if it is one of the earth's own constructs, the construct breaks. */
    static void struck(ServerPlayer by, BlockPos pos, float growth) {
        Construct c = CELLS.get(new Key(by.serverLevel(), pos));
        if (c != null && growth >= .9F) c.until = by.serverLevel().getGameTime();
    }

    /** Breaks a whole construct at once (Quake's Shockwave, a boulder): it crumbles from the top down. */
    public static boolean shatter(ServerLevel level, BlockPos pos) {
        Construct c = CELLS.get(new Key(level, pos));
        if (c == null) return false;
        c.until = level.getGameTime();
        return true;
    }

    // ------------------------------------------------------------------
    // Classes: Pillar Barrage and Earth Tent
    // ------------------------------------------------------------------

    /** Pillar Barrage: a fan of columns erupts round a point, throwing everyone near it up into the air. */
    public static boolean barrage(ServerPlayer p, Vec3 point, int tier, float radius) {
        BlockPos center = EarthGeometry.ground(p.level(), point.add(0, 1, 0));
        if (center == null) return false;
        BlockState ground = p.level().getBlockState(center);
        float damage = (10 + 2 * tier) * DensityService.rooted(p);
        for (int i = 0; i < 6; i++) {
            BlockPos at = i == 0 ? center : center.offset(Math.round(Mth.cos(i * Mth.TWO_PI / 5) * 2), 0, Math.round(Mth.sin(i * Mth.TWO_PI / 5) * 2));
            BlockPos ground2 = EarthGeometry.ground(p.level(), Vec3.atBottomCenterOf(at).add(0, 1.5, 0));
            if (ground2 == null) continue;
            Construct c = pillarAt(p, ground2.above(), Direction.UP, 3, ground, 0);
            c.until = now(p) + 60 + i * 2;
            c.delay = i * 2;
        }
        for (LivingEntity t : ExpansionSkills.enemies(p, point, radius)) {
            StyleHits.hurt(t, p.damageSources().playerAttack(p), damage);
            if (!DensityService.immovable(t)) StyleHits.shove(t, new Vec3(0, 1.1, 0));
        }
        CameraShakePayload.sendAround(p.serverLevel(), point, 16, .4F, 8);
        return true;
    }

    /** Earth Tent: a dome of rock closes over you; shots stop on it, and it stands for {@code ticks}. */
    public static boolean tent(ServerPlayer p, double radius, int ticks) {
        BlockPos feet = p.blockPosition();
        BlockState ground = DensityService.footing(p);
        Construct c = new Construct(p, Shape.TENT, EarthGeometry.construct(ground), Direction.UP, now(p) + ticks);
        int r = (int) Math.ceil(radius) + 1;
        List<BlockPos> shell = new ArrayList<>();
        for (int y = 0; y <= r; y++) for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            double d = Math.sqrt(x * x + y * y + z * z);
            if (d >= radius - .5 && d < radius + .5) shell.add(feet.offset(x, y, z));
        }
        shell.sort((a, b) -> a.getY() - b.getY());
        c.queue.addAll(shell);
        c.perTick = Math.max(8, shell.size() / 6);
        CONSTRUCTS.add(c);
        ArmPosePayload.show(p, ArmPose.EARTH_TENT, 16);
        EarthGeometry.show(p.serverLevel(), Piece.EARTH_TENT, p.position(), p.position(), (float) radius, null, c.block);
        p.serverLevel().playSound(null, feet, SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 1.2F, .9F);
        return true;
    }

    // ------------------------------------------------------------------
    // V: Rising Mountain
    // ------------------------------------------------------------------

    private static final class Mountain {
        final ServerPlayer owner;
        final ServerLevel level;
        final Vec3 center;
        final int tier;
        final List<BlockDebris> rock = new ArrayList<>();
        final List<Vec3> rest = new ArrayList<>();
        final List<LivingEntity> riders = new ArrayList<>();
        final List<Vec3> riderOffsets = new ArrayList<>();
        final BlockState block;
        long started;
        int age, topAt = -1, fallAt = -1;
        boolean held = true;
        double height;
        Mountain(ServerPlayer owner, Vec3 center, BlockState block, int tier) {
            this.owner = owner; this.level = owner.serverLevel(); this.center = center; this.block = block; this.tier = tier;
        }
    }

    /** True while {@code p} is raising (or about to slam) a mountain. */
    public static boolean raising(ServerPlayer p) { return MOUNTAINS.containsKey(p.getUUID()); }

    /**
     * V, Rising Mountain: the ground at your aim cracks in a warning ring, then a disc of it tears free and rises while you
     * hold the key, with everyone standing on it. Let go (or hold too long) and it slams back down.
     */
    public static boolean mountain(ServerPlayer p, int tier) {
        if (MOUNTAINS.containsKey(p.getUUID())) return false;
        boolean metal = SkillTreeService.has(p, DensitySkills.METAL);
        // Centred under a foe you look at; otherwise where you aim (or six blocks ahead).
        BlockPos ground = underFoe(p, MOUNTAIN_RANGE, metal);
        if (ground == null) {
            HitResult hit = p.pick(MOUNTAIN_RANGE, 1, false);
            Vec3 point = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : p.position().add(forward(p.getYRot()).scale(6));
            ground = EarthGeometry.ground(p.level(), point.add(0, 1.2, 0));
        }
        if (ground == null || !EarthGeometry.earth(p.level().getBlockState(ground), metal)) {
            p.displayClientMessage(Component.translatable("density.heroesevolved.no_earth_there"), true);
            return false;
        }
        Vec3 center = Vec3.atBottomCenterOf(ground.above());
        Mountain m = new Mountain(p, center, p.level().getBlockState(ground), tier);
        m.started = now(p);
        MOUNTAINS.put(p.getUUID(), m);
        DensityService.track(p);
        ArmPosePayload.show(p, ArmPose.EARTH_STOMP, 12);
        EarthGeometry.show(p.serverLevel(), Piece.EARTH_MOUNTAIN_MARK, center, center.add(0, 1, 0), (float) MOUNTAIN_RADIUS, null, m.block);
        p.serverLevel().playSound(null, ground, SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 1.5F, .6F);
        CameraShakePayload.sendAround(p.serverLevel(), center, 24, .2F, MOUNTAIN_WARN);
        return true;
    }

    /** The key came up: the mountain stops rising and comes down. */
    public static void releaseMountain(ServerPlayer p) {
        Mountain m = MOUNTAINS.get(p.getUUID());
        if (m != null) m.held = false;
    }

    private static void tickMountain(Mountain m) {
        m.age++;
        int rising = m.age - MOUNTAIN_WARN;
        if (m.owner.isRemoved() || !m.owner.isAlive() || m.owner.level() != m.level) { endMountain(m, false); return; }
        if (rising < 0) {
            // A key let go during the warning still lifts the mountain a little before it drops.
            return;
        }
        if (rising == 0) tearFree(m);
        if (m.fallAt < 0) {
            boolean top = rising >= MOUNTAIN_RISE;
            if (top && m.topAt < 0) m.topAt = m.age;
            if ((!m.held && rising >= 6) || (m.topAt >= 0 && m.age - m.topAt >= MOUNTAIN_HOLD)) m.fallAt = m.age;
            float k = Mth.clamp(rising / (float) MOUNTAIN_RISE, 0, 1);
            m.height = MOUNTAIN_HEIGHT * (1 - (1 - k) * (1 - k));
            if (m.age % 8 == 0) ArmPosePayload.show(m.owner, ArmPose.EARTH_HEAVE, 12);
            if (m.age % 10 == 0) EarthGeometry.show(m.level, Piece.EARTH_MOUNTAIN, m.center, m.center.add(0, m.height, 0), (float) MOUNTAIN_RADIUS, null, m.block);
            if (m.age % 6 == 0) m.level.playSound(null, BlockPos.containing(m.center), SoundEvents.GRAVEL_STEP, SoundSource.PLAYERS, 1.2F, .4F);
        } else {
            int falling = m.age - m.fallAt;
            if (falling == 0) ArmPosePayload.show(m.owner, ArmPose.EARTH_SLAM, 12);
            float k = Mth.clamp(falling / (float) MOUNTAIN_FALL, 0, 1);
            double from = m.height;
            m.height = from * (1 - k * k);
            if (falling >= MOUNTAIN_FALL) { slam(m); return; }
        }
        place(m);
    }

    /** The disc tears free: a display for each column's top three blocks, tapering underneath like a lifted plug of rock. */
    private static void tearFree(Mountain m) {
        double r = MOUNTAIN_RADIUS;
        int ri = (int) Math.ceil(r);
        for (int layer = 0; layer < 3; layer++) {
            double lr = r - layer * 1.1;
            for (int x = -ri; x <= ri; x++) for (int z = -ri; z <= ri; z++) {
                if (x * x + z * z > lr * lr) continue;
                BlockPos column = BlockPos.containing(m.center.x + x, m.center.y - 1 - layer, m.center.z + z);
                BlockState state = m.level.getBlockState(column);
                if (state.isAir()) state = m.block;
                BlockDebris d = new BlockDebris(m.level, EarthGeometry.construct(state), 1);
                Vec3 rest = Vec3.atCenterOf(column);
                d.setPos(rest);
                m.level.addFreshEntity(d);
                m.rock.add(d);
                m.rest.add(rest);
            }
        }
        // Everyone standing on the disc rides it up (a foot within half a block of its top, so a mid-step body counts).
        for (LivingEntity e : m.level.getEntitiesOfClass(LivingEntity.class, new AABB(m.center, m.center).inflate(r, 1.5, r),
                e -> e != m.owner && !DensityService.immovable(e) && Math.abs(e.getY() - m.center.y) < .5 && !e.isPassenger()
                        && e.position().subtract(m.center).horizontalDistance() <= r)) {
            m.riders.add(e);
            m.riderOffsets.add(e.position().subtract(m.center));
        }
        EarthGeometry.show(m.level, Piece.EARTH_MOUNTAIN, m.center, m.center, (float) MOUNTAIN_RADIUS, null, m.block);
        m.level.playSound(null, BlockPos.containing(m.center), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 2, .4F);
        m.level.playSound(null, BlockPos.containing(m.center), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, .6F, .5F);
        CameraShakePayload.sendAround(m.level, m.center, 32, .5F, 10);
    }

    private static void place(Mountain m) {
        for (int i = 0; i < m.rock.size(); i++) m.rock.get(i).setPos(m.rest.get(i).add(0, m.height, 0));
        for (int i = 0; i < m.riders.size(); i++) {
            LivingEntity e = m.riders.get(i);
            if (!e.isAlive() || e.isRemoved()) continue;
            Vec3 at = m.center.add(m.riderOffsets.get(i)).add(0, m.height, 0);
            carry(e, new Vec3(e.getX(), at.y, e.getZ()));
            e.setDeltaMovement(Vec3.ZERO);
            e.resetFallDistance();
        }
    }

    /** The mountain comes down: everyone it carried is slammed, and the ground round it shakes everyone near off their feet. */
    private static void slam(Mountain m) {
        ServerPlayer p = m.owner;
        float damage = (MOUNTAIN_DAMAGE + 2 * m.tier) * DensityService.rooted(p);
        for (LivingEntity e : m.riders) {
            if (!e.isAlive() || e.isRemoved()) continue;
            carry(e, new Vec3(e.getX(), m.center.y, e.getZ()));
            if (ExpansionSkills.hostile(p, e)) {
                StyleHits.hurt(e, p.damageSources().playerAttack(p), damage);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3));
            }
        }
        DensityService.shockwave(p, m.center, MOUNTAIN_RADIUS + 2.5, damage * .4F);
        EarthGeometry.show(m.level, Piece.EARTH_MOUNTAIN_SLAM, m.center, m.center.add(0, 1, 0), (float) MOUNTAIN_RADIUS, null, m.block);
        m.level.playSound(null, BlockPos.containing(m.center), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.4F, .5F);
        m.level.playSound(null, BlockPos.containing(m.center), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1, .4F);
        Stones.dust(m.level, m.block, m.center, 60, MOUNTAIN_RADIUS * .6);
        CameraShakePayload.sendAround(m.level, m.center, 40, .8F, 12);
        DensityService.bankEarth(p, m.rock.size());
        endMountain(m, true);
    }

    private static void endMountain(Mountain m, boolean landed) {
        MOUNTAINS.remove(m.owner.getUUID());
        m.rock.forEach(Entity::discard);
        if (!landed) for (LivingEntity e : m.riders) if (e.isAlive()) carry(e, new Vec3(e.getX(), m.center.y, e.getZ()));
    }

    // ------------------------------------------------------------------
    // Earth Surf (the Listener lane's travel)
    // ------------------------------------------------------------------

    private static final ResourceLocation SURF_STEP = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "earth_surf_step");
    private static final ResourceLocation SURF_SPEED = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "earth_surf_speed");
    private record Surf(ServerPlayer player, long until, int tier, Set<UUID> struck) {}

    public static boolean surfing(ServerPlayer p) { return SURFS.containsKey(p.getUUID()); }

    /** Earth Surf: a wave of ground carries you along at speed for six seconds; anyone in your way is bowled over. */
    public static boolean surf(ServerPlayer p, int tier) {
        if (SURFS.remove(p.getUUID()) != null) { endSurf(p); return true; }
        if (DensityService.footing(p) == null || !p.onGround()) { p.displayClientMessage(Component.translatable("density.heroesevolved.need_ground"), true); return false; }
        SURFS.put(p.getUUID(), new Surf(p, now(p) + 120, tier, new HashSet<>()));
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.addOrUpdateTransientModifier(new AttributeModifier(SURF_SPEED, .6, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        AttributeInstance step = p.getAttribute(Attributes.STEP_HEIGHT);
        if (step != null) step.addOrUpdateTransientModifier(new AttributeModifier(SURF_STEP, 1, AttributeModifier.Operation.ADD_VALUE));
        ArmPosePayload.show(p, ArmPose.EARTH_SURF, 12);
        EarthGeometry.show(p.serverLevel(), Piece.EARTH_SURF, p.position(), p.position(), 1, p, DensityService.footing(p));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.2F, .6F);
        return true;
    }

    private static void endSurf(ServerPlayer p) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SURF_SPEED);
        AttributeInstance step = p.getAttribute(Attributes.STEP_HEIGHT);
        if (step != null) step.removeModifier(SURF_STEP);
    }

    private static void tickSurfs(long now) {
        for (Surf s : List.copyOf(SURFS.values())) {
            ServerPlayer p = s.player;
            if (now >= s.until || p.isRemoved() || !p.isAlive() || p.isPassenger()) { SURFS.remove(p.getUUID()); endSurf(p); continue; }
            BlockState ground = DensityService.footing(p);
            if (now % 10 == 0) {
                ArmPosePayload.show(p, ArmPose.EARTH_SURF, 14);
                EarthGeometry.show(p.serverLevel(), Piece.EARTH_SURF, p.position(), p.position(), 1, p, ground == null ? p.serverLevel().getBlockState(p.blockPosition().below()) : ground);
            }
            if (now % 4 == 0 && ground != null) Stones.dust(p.serverLevel(), ground, p.position().add(0, .1, 0), 4, .5);
            Vec3 move = p.position().subtract(p.xo, p.yo, p.zo).multiply(1, 0, 1);
            if (move.lengthSqr() > 1.0E-4) DensityService.bankEarth(p, move.length());
            for (LivingEntity t : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(.8),
                    t -> t != p && ExpansionSkills.hostile(p, t) && !s.struck.contains(t.getUUID()))) {
                s.struck.add(t.getUUID());
                StyleHits.hurt(t, p.damageSources().playerAttack(p), (3 + s.tier * .5F) * DensityService.rooted(p));
                Vec3 along = move.lengthSqr() < 1.0E-4 ? forward(p.getYRot()) : move.normalize();
                StyleHits.shove(t, along.scale(.9).add(0, .5, 0));
            }
        }
    }

    // ------------------------------------------------------------------
    // Tick and world events
    // ------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (Construct c : List.copyOf(CONSTRUCTS)) {
            if (c.owner.isRemoved() && !c.crumbling) c.until = Math.min(c.until, c.level.getGameTime());
            long time = c.level.getGameTime();
            if (c.delay > 0) { c.delay--; continue; }
            if (!c.queue.isEmpty() && time < c.until) grow(c);
            else if (c.shoving && time < c.until && (time % SHOVE_EVERY == 0)) step(c);
            if (time >= c.until) crumble(c);
            if (c.crumbling && c.cells.isEmpty()) { CONSTRUCTS.remove(c); c.queue.clear(); }
        }
        for (Mountain m : List.copyOf(MOUNTAINS.values())) tickMountain(m);
        tickSurfs(now);
        CUSHIONS.values().removeIf(until -> until <= now);
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }

    /** A construct block broken by hand crumbles, and gives nothing: the earth goes back where it came from. */
    /** True while the earth places one of its own blocks (see {@link #put}). */
    private static boolean placing;

    /**
     * Places one construct block. The placement's permission check posts a break event at the cell; when that cell is
     * already another construct's (a pillar in a shoved wall's path), {@link #broken} must not crumble it: the earth
     * already there simply blocks the new block.
     */
    private static boolean put(Construct c, BlockPos cell, int ticks) {
        placing = true;
        try { return TemporaryBlocks.place(c.level, cell, c.block, ticks, c.owner); }
        finally { placing = false; }
    }

    @SubscribeEvent public static void broken(BlockEvent.BreakEvent event) {
        if (placing || !(event.getLevel() instanceof ServerLevel level)) return;
        Construct c = CELLS.remove(new Key(level, event.getPos()));
        if (c == null) return;
        event.setCanceled(true);
        c.cells.remove(event.getPos());
        TemporaryBlocks.restore(level, event.getPos(), null);
        Stones.dust(level, c.block, Vec3.atCenterOf(event.getPos()), 8, .4);
    }

    /** An explosion breaks constructs without dropping them. */
    @SubscribeEvent public static void blast(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        event.getAffectedBlocks().removeIf(pos -> {
            Construct c = CELLS.remove(new Key(level, pos));
            if (c == null) return false;
            c.cells.remove(pos);
            TemporaryBlocks.restore(level, pos, null);
            return true;
        });
    }

    static void forget(ServerPlayer p) {
        for (Construct c : CONSTRUCTS) if (c.owner == p) { c.queue.clear(); c.until = Math.min(c.until, c.level.getGameTime()); }
        Mountain m = MOUNTAINS.get(p.getUUID());
        if (m != null) endMountain(m, false);
        if (SURFS.remove(p.getUUID()) != null) endSurf(p);
        CUSHIONS.remove(p.getUUID());
    }

    static void clear() {
        for (Construct c : List.copyOf(CONSTRUCTS)) for (BlockPos cell : c.cells) TemporaryBlocks.restore(c.level, cell, null);
        for (Mountain m : List.copyOf(MOUNTAINS.values())) endMountain(m, false);
        for (Surf s : List.copyOf(SURFS.values())) endSurf(s.player);
        CONSTRUCTS.clear(); CELLS.clear(); MOUNTAINS.clear(); SURFS.clear(); CUSHIONS.clear();
    }
}
