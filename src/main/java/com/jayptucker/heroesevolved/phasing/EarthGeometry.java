package com.jayptucker.heroesevolved.phasing;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Earthbending's shared numbers and rules, read by the server ({@code DensityService}, {@code Stones},
 * {@code Earthworks}, {@code EarthStyle}) and the drawing ({@code EarthArt}).
 * <ul>
 *   <li><b>Footing</b>: earth moves pull rock from natural ground ({@link #EARTH}, a block tag modpacks can add to)
 *   under or near the bender. In the air, on planks or on a ship there is none, and Rock Strike throws a pebble.</li>
 *   <li><b>Root</b>: standing planted on earth fills it; leaving the ground empties it. Full Root hits a quarter harder
 *   ({@link #rooted}).</li>
 *   <li><b>Material</b>: the rock is the block it came from, and with Know Your Stone each kind hits its own way.</li>
 * </ul>
 */
public final class EarthGeometry {
    public static final TagKey<Block> EARTH = tag("earth"), HEAVY = tag("earth_heavy"), METAL = tag("earth_metal");

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, path));
    }

    // ------------------------------------------------------------------ Root and stance
    /** Root gained a tick standing on earth (twice this in Horse Stance, half while moving), and lost a tick in the air. */
    public static final float ROOT_GAIN = 1 / 40F, ROOT_LOSS = 1 / 10F;
    /** How much harder a fully rooted bender's earth hits. */
    public static final float ROOT_BONUS = .25F;
    /** How far below the feet footing is looked for. */
    public static final double FOOTING_DEPTH = 2.5;

    // ------------------------------------------------------------------ Rock Strike
    /** Where a lifted rock hovers: ahead of the chest. How long it waits to be thrown before it drops. */
    public static final double ROCK_AHEAD = 1.3, ROCK_HEIGHT = .8;
    public static final int ROCK_RISE = 5, ROCK_HOVER = 50;
    /** A rock's size grows from a stone to a boulder while the key is held. */
    public static final float ROCK_SIZE = .7F, BOULDER_SIZE = 1.6F, DISC_SIZE = .55F, PEBBLE_SIZE = .35F;
    public static final int BOULDER_TICKS = 16;
    /** Flight: blocks a tick, gravity a tick, and how far before it falls apart. */
    public static final double ROCK_SPEED = 1.9, BOULDER_SPEED = 1.15, ROCK_GRAVITY = .03, ROCK_RANGE = 28;
    /** Damage at the unlock tier, before Root and size: 6 + tier for a stone. */
    public static final float ROCK_DAMAGE = 6, DISC_SHARE = .55F, PEBBLE_SHARE = .6F;
    /** A pebble pulled from no ground costs this much more stamina. */
    public static final int PEBBLE_COST = 4;

    // ------------------------------------------------------------------ Pillar and Wall
    public static final double PILLAR_RANGE = 20;
    public static final int PILLAR_HEIGHT = 3, PILLAR_TICKS = 160, WALL_TICKS = 200, WALL_HALF = 2, WALL_HEIGHT = 3,
            WALL_GAP = 2, SHOVE_STEPS = 8, SHOVE_EVERY = 2;
    public static final float PILLAR_DAMAGE = 4, SHOVE_DAMAGE = 5;

    // ------------------------------------------------------------------ Earth Shackle
    public static final int SHACKLE_TICKS = 60, CRUSH_HOLD = 30;
    public static final float CRUSH_DAMAGE = 4;
    /** Mud (a Soaked foe, or mud and clay underfoot) holds half again as long. */
    public static final float MUD_HOLD = 1.5F;

    // ------------------------------------------------------------------ Rising Mountain
    public static final double MOUNTAIN_RANGE = 24, MOUNTAIN_RADIUS = 3.5, MOUNTAIN_HEIGHT = 7;
    /** The warning, the climb, the longest it holds at the top, and the fall. */
    public static final int MOUNTAIN_WARN = 15, MOUNTAIN_RISE = 30, MOUNTAIN_HOLD = 50, MOUNTAIN_FALL = 5;
    public static final float MOUNTAIN_DAMAGE = 8;

    private EarthGeometry() {}

    // ------------------------------------------------------------------ Footing

    /** True if {@code state} is earth a bender can move ({@code metal} also lets metal count). */
    public static boolean earth(BlockState state, boolean metal) {
        return !state.isAir() && (state.is(EARTH) || metal && state.is(METAL));
    }

    /** The first solid block under {@code at}, within {@link #FOOTING_DEPTH}, or null. */
    public static BlockPos ground(BlockGetter level, Vec3 at) { return ground(level, at, FOOTING_DEPTH); }

    /** The first solid block under {@code at}, within {@code depth} blocks, or null. */
    public static BlockPos ground(BlockGetter level, Vec3 at, double depth) {
        for (double d = .05; d <= depth; d += .5) {
            BlockPos pos = BlockPos.containing(at.x, at.y - d, at.z);
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty()) return pos;
        }
        return null;
    }

    /** The earth under {@code body}'s feet, or null if there is none to pull from. */
    public static BlockState footing(Entity body, boolean metal) {
        BlockPos pos = ground(body.level(), body.position());
        if (pos == null) return null;
        BlockState state = body.level().getBlockState(pos);
        return earth(state, metal) ? state : null;
    }

    /** The earth at the top of the ground under {@code at}, or null (no ground, or not earth). */
    public static BlockState earthUnder(BlockGetter level, Vec3 at, boolean metal) {
        BlockPos pos = ground(level, at);
        if (pos == null) return null;
        BlockState state = level.getBlockState(pos);
        return earth(state, metal) ? state : null;
    }

    /** The multiplier a bender's Root (0 to 1) puts on the earth they throw. */
    public static float rooted(float root) { return 1 + ROOT_BONUS * Mth.clamp(root, 0, 1); }

    // ------------------------------------------------------------------ Material

    /** What a rock is made of decides, with Know Your Stone, how it hits. */
    public enum Material {
        STONE, HEAVY, SAND, GRAVEL, MUD, METAL;

        public static Material of(BlockState state) {
            if (state.is(EarthGeometry.METAL)) return METAL;
            if (state.is(EarthGeometry.HEAVY)) return HEAVY;
            if (state.is(BlockTags.SAND) || state.is(Blocks.SOUL_SAND)) return SAND;
            if (state.is(Blocks.GRAVEL) || state.is(Blocks.SUSPICIOUS_GRAVEL)) return GRAVEL;
            if (state.is(Blocks.MUD) || state.is(Blocks.CLAY) || state.is(Blocks.MUDDY_MANGROVE_ROOTS) || state.is(Blocks.PACKED_MUD))
                return MUD;
            return STONE;
        }
    }

    /**
     * The block a construct (a pillar, a wall, a lifted rock) is drawn or built from when it comes out of {@code ground}:
     * the same rock, but never one that falls, spreads, hurts or is worth more than the ground it stands for.
     */
    public static BlockState construct(BlockState ground) {
        if (ground == null || ground.isAir()) return Blocks.COBBLESTONE.defaultBlockState();
        Block b = ground.getBlock();
        if (b == Blocks.SAND || b == Blocks.SUSPICIOUS_SAND) return Blocks.SANDSTONE.defaultBlockState();
        if (b == Blocks.RED_SAND) return Blocks.RED_SANDSTONE.defaultBlockState();
        if (b instanceof FallingBlock) return Blocks.COBBLESTONE.defaultBlockState();
        if (ground.is(BlockTags.DIRT) && b != Blocks.MUD) return Blocks.DIRT.defaultBlockState();
        if (b == Blocks.DIRT_PATH || b == Blocks.FARMLAND) return Blocks.DIRT.defaultBlockState();
        if (b == Blocks.MUD) return Blocks.PACKED_MUD.defaultBlockState();
        if (b == Blocks.SOUL_SAND) return Blocks.SOUL_SOIL.defaultBlockState();
        if (b == Blocks.MAGMA_BLOCK || b == Blocks.NETHER_QUARTZ_ORE || b == Blocks.NETHER_GOLD_ORE || b == Blocks.ANCIENT_DEBRIS)
            return Blocks.NETHERRACK.defaultBlockState();
        if (b == Blocks.CRYING_OBSIDIAN) return Blocks.OBSIDIAN.defaultBlockState();
        if (ground.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES) && ground.is(net.neoforged.neoforge.common.Tags.Blocks.ORES))
            return Blocks.DEEPSLATE.defaultBlockState();
        if (ground.is(net.neoforged.neoforge.common.Tags.Blocks.ORES)) return Blocks.STONE.defaultBlockState();
        if (ground.is(METAL)) return ground.is(Blocks.IRON_BARS) || ground.is(Blocks.CHAIN) || ground.is(Blocks.IRON_DOOR)
                || ground.is(Blocks.IRON_TRAPDOOR) || ground.is(BlockTags.ANVIL) ? Blocks.IRON_BLOCK.defaultBlockState() : b.defaultBlockState();
        if (ground.hasBlockEntity()) return Blocks.STONE.defaultBlockState();
        return b.defaultBlockState();
    }

    // ------------------------------------------------------------------ The body

    /** A palm along {@code direction} ({@code main}: the main hand), from the shoulder the body turns with. */
    public static Vec3 palm(LivingEntity body, Vec3 direction, float partial, boolean main) {
        double yaw = Math.toRadians(Mth.rotLerp(partial, body.yBodyRotO, body.yBodyRot));
        double sign = (body.getMainArm() == HumanoidArm.RIGHT) == main ? 1 : -1;
        Vec3 shoulder = body.getPosition(partial).add(-Math.cos(yaw) * .3125 * sign,
                body.isCrouching() ? 1.17 : 1.375, -Math.sin(yaw) * .3125 * sign);
        return shoulder.add(direction.scale(.72));
    }

    /** Where a lifted rock hovers before it is thrown: ahead of the chest, along the flat look. */
    public static Vec3 hover(Entity body, float partial) {
        Vec3 look = body.getViewVector(partial).multiply(1, 0, 1);
        look = look.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : look.normalize();
        return body.getPosition(partial).add(look.scale(ROCK_AHEAD)).add(0, ROCK_HEIGHT, 0);
    }

    /**
     * Shows a set piece made of {@code block}: the block's id rides in the seed, so the drawing colours its chips and slabs
     * like the ground they came from ({@link #block(int)} reads it back).
     */
    public static void show(net.minecraft.server.level.ServerLevel level, com.jayptucker.heroesevolved.styles.Piece piece, Vec3 at, Vec3 to,
                            float scale, Entity bound, BlockState block) {
        com.jayptucker.heroesevolved.combat.Takedowns.show(level, piece, at, to, scale, bound, Block.getId(block == null ? Blocks.STONE.defaultBlockState() : block));
    }

    /** The block a {@link #show}n piece was made of. */
    public static BlockState block(int seed) {
        BlockState state = Block.stateById(seed);
        return state.isAir() ? Blocks.STONE.defaultBlockState() : state;
    }

    /** The flat forward direction from a yaw in degrees. */
    public static Vec3 forward(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }
}
