package com.jayptucker.heroesevolved.story.prologue;

import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.time.intro.IntroBlast;
import com.jayptucker.heroesevolved.time.intro.city.CityGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The city coming apart after Ted's wave ({@link PrologueTimeline#WAVE}), round the players where anyone can see it:
 * <ul>
 *   <li>As the front passes, pieces of roof edges and facades are torn loose and fall to the street (real falling blocks
 *   with no will to land, landed as rubble by this class), and broken stone, slabs and gravel are thrown across the
 *   street.</li>
 *   <li>From then on the buildings keep shedding: every second or two a section of some facade near someone gives way
 *   with a crack and a roar of dust, and comes down.</li>
 *   <li>Smoke climbs from where the worst of it fell.</li>
 * </ul>
 * Every block is changed through {@link LastDayDamage}, so the next day finds the city whole; nothing is changed with
 * neighbour updates, so nothing hanging off a wall drops with it. Primatech (the shelter and its doors), the people under
 * the rubble and anyone standing anywhere are left clear.
 */
final class LastDayCrumble {
    /** How far round the players the city comes apart. */
    static final int REACH = 64;
    /** What the street is strewn with where nothing fell whole. */
    private static final BlockState[] STREWN = {Blocks.COBBLESTONE_SLAB.defaultBlockState(), Blocks.STONE_BRICK_SLAB.defaultBlockState(),
            Blocks.GRAVEL.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Blocks.ANDESITE_SLAB.defaultBlockState(),
            Blocks.COBBLESTONE.defaultBlockState()};
    /**
     * What the wave throws across the street: mostly slabs, which you walk over, so the way to Primatech stays open with
     * someone in your arms. (At three cells in ten, half of them whole blocks, the road was a field you had to hop across.)
     */
    private static final BlockState[] THROWN = {Blocks.COBBLESTONE_SLAB.defaultBlockState(), Blocks.STONE_BRICK_SLAB.defaultBlockState(),
            Blocks.ANDESITE_SLAB.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(), Blocks.COBBLESTONE_SLAB.defaultBlockState(),
            Blocks.COBBLESTONE.defaultBlockState(), Blocks.STONE_BRICK_SLAB.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState()};
    /** Primatech's lobby, doors and stair, where the shelter is: nothing falls there. */
    private static final int SAFE_X0 = -12, SAFE_X1 = 28, SAFE_Z0 = 92, SAFE_Z1 = 127;

    private record Falling(FallingBlockEntity entity, BlockState state, int born) {}

    private final List<Falling> falling = new ArrayList<>();
    private final List<BlockPos> smoke = new ArrayList<>();
    private float swept;
    private int nextCollapse = -1;
    /** How many blocks came loose, and how many landed or were strewn as rubble; the street changed since last asked. */
    int loosened, landed, roofs, faces, strewnCount;
    /** Where each hole was torn in a facade, and which way it faces: for previews to look at. */
    final List<BlockPos> holes = new ArrayList<>();
    final List<Direction> holeFacing = new ArrayList<>();
    boolean changed;
    /** The first block torn loose, for tests (put back when the day ends). */
    BlockPos first;

    /**
     * {@code t}: ticks since the light. {@code players}: everyone still above ground. {@code keep}: cells to leave alone
     * (the people under the rubble, their stone).
     */
    void tick(ServerLevel city, int t, List<ServerPlayer> players, LastDayDamage damage, Predicate<BlockPos> keep, String tag) {
        if (t < PrologueTimeline.WAVE) return;
        float front = PrologueTimeline.front(t - PrologueTimeline.WAVE);
        if (front <= 400 && !players.isEmpty()) {
            pass(city, players, swept, front, damage, keep, tag, t);
            swept = front;
        }
        // Once the wave has gone by someone, the buildings round them keep giving way.
        if (!players.isEmpty() && nextCollapse >= 0 && t >= nextCollapse) {
            ServerPlayer p = players.get(city.random.nextInt(players.size()));
            if (IntroBlast.distance(p.getX(), p.getZ()) < front) collapse(city, p, players, damage, keep, tag, t);
            nextCollapse = t + 8 + city.random.nextInt(14);
        }
        if (nextCollapse < 0 && players.stream().anyMatch(p -> IntroBlast.distance(p.getX(), p.getZ()) < front)) nextCollapse = t + 40;
        land(city, damage, keep, t);
        if (t % 8 == 0) smoke(city, players);
        if (t % 100 == 0) com.jayptucker.heroesevolved.HeroesEvolved.LOGGER.debug(
                "Last Day: the city has lost {} blocks ({} off roof edges, {} holes in facades), {} lie in the street ({} thrown there)",
                loosened, roofs, faces, landed, strewnCount);
    }

    // ------------------------------------------------------------------
    // The front going by
    // ------------------------------------------------------------------

    private void pass(ServerLevel city, List<ServerPlayer> players, float from, float to, LastDayDamage damage, Predicate<BlockPos> keep,
                      String tag, int t) {
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (ServerPlayer p : players) {
            x0 = Math.min(x0, p.getBlockX() - REACH); x1 = Math.max(x1, p.getBlockX() + REACH);
            z0 = Math.min(z0, p.getBlockZ() - REACH); z1 = Math.max(z1, p.getBlockZ() + REACH);
        }
        x0 = Math.max(x0, CityGrid.MIN); x1 = Math.min(x1, CityGrid.MAX);
        z0 = Math.max(z0, CityGrid.MIN); z1 = Math.min(z1, CityGrid.MAX);
        int torn = 0, strewn = 0;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                double d = IntroBlast.distance(x + 0.5, z + 0.5);
                if (d < from || d >= to || !near(players, x, z)) continue;
                float roll = Math.abs(CityGrid.hash(x, z, 911)) / (float) Integer.MAX_VALUE;
                float roll2 = Math.abs(CityGrid.hash(x, z, 913)) / (float) Integer.MAX_VALUE;
                int top = city.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                if (top - CityGrid.STREET_Y > 9) {
                    // A building: bites out of its roof edge, and holes in its face where the face is open to the air, most
                    // of them low down, where people see them.
                    if (roll < 0.14F && torn < 200 && edge(city, x, z, top)) {
                        int depth = 1 + (int) (roll2 * 4);
                        for (int k = 1; k <= depth; k++) if (loosen(city, new BlockPos(x, top - k, z), damage, keep, tag, t)) { torn++; roofs++; }
                    }
                    if (roll2 < 0.26F && torn < 200) {
                        int y = CityGrid.STREET_Y + 2 + (int) (roll * roll * Math.min(30, top - CityGrid.STREET_Y - 4));
                        BlockPos at = new BlockPos(x, y, z);
                        Direction open = outside(city, at);
                        if (open != null) {
                            int n = hole(city, at, roll2 < 0.1F ? 2 : 1, damage, keep, tag, t);
                            if (n > 0) { torn += n; faces++; holes.add(at.immutable()); holeFacing.add(open); }
                        }
                    }
                } else if (top <= CityGrid.STREET_Y + 3 && roll < 0.11F && strewn < 200) {
                    // The street: broken stone and grit thrown across it, now and then in a heap.
                    BlockPos at = new BlockPos(x, CityGrid.STREET_Y, z);
                    BlockState piece = THROWN[(int) (roll2 * 1000) % THROWN.length];
                    if (strew(city, at, piece, damage, keep)) {
                        strewn++;
                        strewnCount++;
                        if (roll2 < 0.35F && piece.isCollisionShapeFullBlock(city, at)) {
                            Direction heap = Direction.from2DDataValue((int) (roll2 * 40) % 4);
                            if (strew(city, at.relative(heap), STREWN[(int) (roll2 * 700) % STREWN.length], damage, keep)) strewnCount++;
                            if (roll2 < 0.15F && strew(city, at.above(), STREWN[(int) (roll2 * 300) % STREWN.length], damage, keep)) strewnCount++;
                        }
                    }
                }
            }
        if (torn + strewn > 0) changed = true;
    }

    /** A ragged hole in a wall's face round {@code at}, {@code size} blocks out from it each way; how many came loose. */
    private int hole(ServerLevel city, BlockPos at, int size, LastDayDamage damage, Predicate<BlockPos> keep, String tag, int t) {
        Direction open = outside(city, at);
        if (open == null) return 0;
        Direction along = open.getClockWise();
        int n = 0;
        for (int dy = -size; dy <= size; dy++)
            for (int a = -size; a <= size; a++) {
                if ((dy != 0 || a != 0) && Math.abs(CityGrid.hash(at.getX() + a, at.getY() + dy, at.getZ(), 919)) % 3 == 0) continue;
                if (loosen(city, at.relative(along, a).above(dy), damage, keep, tag, t)) n++;
            }
        return n;
    }

    private static boolean near(List<ServerPlayer> players, int x, int z) {
        for (ServerPlayer p : players) if (Math.abs(p.getX() - x) <= REACH && Math.abs(p.getZ() - z) <= REACH) return true;
        return false;
    }

    /**
     * Whether a block is on the outside of a building: the air beside it is outdoors (open sky above it), not a room.
     * The city's buildings are hollow, and the first version tore their insides out where nobody could see.
     */
    private static boolean exposed(ServerLevel city, BlockPos at) { return outside(city, at) != null; }

    /** The side of {@code at} that faces the open air outside, or null. */
    private static Direction outside(ServerLevel city, BlockPos at) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = at.relative(d);
            if (city.getBlockState(n).isAir() && city.getHeight(Heightmap.Types.WORLD_SURFACE, n.getX(), n.getZ()) <= at.getY()) return d;
        }
        return null;
    }

    /** Whether a column's top is a roof edge: some neighbour drops well below it. */
    private static boolean edge(ServerLevel city, int x, int z, int top) {
        for (Direction d : Direction.Plane.HORIZONTAL)
            if (city.getHeight(Heightmap.Types.WORLD_SURFACE, x + d.getStepX(), z + d.getStepZ()) < top - 3) return true;
        return false;
    }

    // ------------------------------------------------------------------
    // Afterwards: sections giving way
    // ------------------------------------------------------------------

    /** A section of some facade near {@code p} gives way: a crack, a roar of dust, and it comes down into the street. */
    private void collapse(ServerLevel city, ServerPlayer p, List<ServerPlayer> players, LastDayDamage damage, Predicate<BlockPos> keep,
                          String tag, int t) {
        for (int tries = 0; tries < 12; tries++) {
            double angle = city.random.nextDouble() * Mth.TWO_PI, distance = 10 + city.random.nextDouble() * 28;
            int x = Mth.floor(p.getX() + Math.cos(angle) * distance), z = Mth.floor(p.getZ() + Math.sin(angle) * distance);
            if (!CityGrid.inCity(x, z)) continue;
            int top = city.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            if (top - CityGrid.STREET_Y < 11) continue;
            int y = CityGrid.STREET_Y + 6 + city.random.nextInt(top - CityGrid.STREET_Y - 7);
            BlockPos at = new BlockPos(x, y, z);
            Direction open = outside(city, at);
            if (open == null) continue;
            // A patch of the face itself: along the wall and down it, never into the building.
            Direction along = open.getClockWise();
            int pieces = 0;
            for (int dy = 0; dy <= 3; dy++)
                for (int a = -2; a <= 2; a++) {
                    if ((dy > 0 || a != 0) && city.random.nextFloat() > 0.55F) continue;
                    if (loosen(city, at.relative(along, a).below(dy), damage, keep, tag, t)) pieces++;
                }
            if (pieces == 0) continue;
            Vec3 c = Vec3.atCenterOf(at);
            BlockState dust = Blocks.STONE_BRICKS.defaultBlockState();
            city.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, dust), c.x, c.y, c.z, 40, 1.0, 1.0, 1.0, 0.15);
            city.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y, c.z, 10, 1.2, 1.2, 1.2, 0.02);
            city.playSound(null, at, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 3, 0.5F);
            city.playSound(null, at, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 2.5F, 0.6F);
            city.playSound(null, at, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.2F, 0.45F);
            for (ServerPlayer q : players)
                if (q.distanceToSqr(c) < 28 * 28) PacketDistributor.sendToPlayer(q, new CameraShakePayload(0.12F, 12));
            if (smoke.size() < 12) smoke.add(new BlockPos(x, CityGrid.STREET_Y, z));
            changed = true;
            return;
        }
    }

    // ------------------------------------------------------------------
    // Blocks coming loose, and landing
    // ------------------------------------------------------------------

    /** Tears one block loose (logged) and lets it fall, flung a little away from the light. */
    private boolean loosen(ServerLevel city, BlockPos pos, LastDayDamage damage, Predicate<BlockPos> keep, String tag, int t) {
        if (falling.size() >= 480) return false;
        BlockState state = city.getBlockState(pos);
        if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(city, pos) < 0 || !state.getFluidState().isEmpty()
                || state.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS) || state.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_PANES)
                || state.getCollisionShape(city, pos).isEmpty() || safe(pos) || keep.test(pos) || damage.changed(pos)) return false;
        damage.set(city, pos, Blocks.AIR.defaultBlockState());
        FallingBlockEntity piece = debris(city, state, pos, tag);
        if (piece == null) return true;
        Vec3 away = Vec3.atCenterOf(pos).subtract(IntroBlast.CENTER).multiply(1, 0, 1);
        away = away.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : away.normalize();
        piece.setDeltaMovement(away.scale(0.12 + city.random.nextDouble() * 0.25).add((city.random.nextDouble() - 0.5) * 0.1,
                0.05 + city.random.nextDouble() * 0.12, (city.random.nextDouble() - 0.5) * 0.1));
        falling.add(new Falling(piece, state, t));
        loosened++;
        if (first == null) first = pos.immutable();
        return true;
    }

    /**
     * A falling piece that changes nothing where it starts (the block is already logged away) and places nothing where it
     * lands (this class does that, logged): made from its saved form, since the vanilla factory writes the world.
     */
    private static FallingBlockEntity debris(ServerLevel city, BlockState state, BlockPos pos, String tag) {
        FallingBlockEntity piece = EntityType.FALLING_BLOCK.create(city);
        if (piece == null) return null;
        CompoundTag data = new CompoundTag();
        piece.saveWithoutId(data);
        data.put("BlockState", NbtUtils.writeBlockState(state));
        data.putInt("Time", 1);
        data.putBoolean("DropItem", false);
        data.putBoolean("CancelDrop", true);
        data.putBoolean("HurtEntities", false);
        piece.load(data);
        piece.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        piece.addTag(tag);
        return city.addFreshEntity(piece) ? piece : null;
    }

    /** Pieces that have come down: a block of rubble where each one landed (whole blocks as they were, the rest broken up). */
    private void land(ServerLevel city, LastDayDamage damage, Predicate<BlockPos> keep, int t) {
        falling.removeIf(f -> {
            if (!f.entity.isRemoved() && t - f.born < 200) return false;
            if (!f.entity.isRemoved()) { f.entity.discard(); return true; }
            BlockPos at = f.entity.blockPosition();
            BlockState state = f.state.isCollisionShapeFullBlock(city, at) ? f.state : STREWN[Math.floorMod(at.getX() * 7 + at.getZ() * 13, STREWN.length)];
            if (strew(city, at, state, damage, keep)) {
                if (city.random.nextInt(5) == 0 && smoke.size() < 12) smoke.add(at.immutable());
                city.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, f.state), at.getX() + .5, at.getY() + .3, at.getZ() + .5, 8, 0.4, 0.1, 0.4, 0.1);
            }
            return true;
        });
    }

    /**
     * Rubble on the ground at {@code at}: only on something solid, only where nothing and nobody is. The sidewalks are
     * bottom slabs on the street's level (the road's surface is a block lower, {@code STREET_Y - 1}), so rubble there
     * takes the slab's place; the first version put it a block up, over nothing, and almost none of it ever landed.
     */
    private boolean strew(ServerLevel city, BlockPos at, BlockState state, LastDayDamage damage, Predicate<BlockPos> keep) {
        BlockState there = city.getBlockState(at);
        boolean sidewalk = there.getBlock() instanceof net.minecraft.world.level.block.SlabBlock
                && there.getValue(net.minecraft.world.level.block.SlabBlock.TYPE) == net.minecraft.world.level.block.state.properties.SlabType.BOTTOM;
        if (!there.isAir() && !there.canBeReplaced() && !sidewalk || safe(at) || keep.test(at) || damage.changed(at)) return false;
        BlockPos below = at.below();
        if (!city.getBlockState(below).isFaceSturdy(city, below, Direction.UP)) return false;
        if (!city.getEntitiesOfClass(LivingEntity.class, new AABB(at).inflate(0.4)).isEmpty()) return false;
        damage.set(city, at, state);
        landed++;
        changed = true;
        return true;
    }

    /** Primatech's lobby, doors and stair. */
    private static boolean safe(BlockPos pos) {
        return pos.getX() >= SAFE_X0 && pos.getX() <= SAFE_X1 && pos.getZ() >= SAFE_Z0 && pos.getZ() <= SAFE_Z1;
    }

    /** Smoke climbing from where the worst of it came down. */
    private void smoke(ServerLevel city, List<ServerPlayer> players) {
        for (BlockPos at : smoke)
            for (ServerPlayer p : players)
                if (p.level() == city && p.distanceToSqr(Vec3.atCenterOf(at)) < 140 * 140)
                    city.sendParticles(p, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, at.getX() + .5, at.getY() + .5, at.getZ() + .5, 1, 0.6, 0.2, 0.6, 0.01);
    }

    /** Pieces still in the air when the day ends. */
    void discard() { falling.forEach(f -> f.entity.discard()); falling.clear(); }
}
