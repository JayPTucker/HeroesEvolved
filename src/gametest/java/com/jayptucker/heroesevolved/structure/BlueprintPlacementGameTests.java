package com.jayptucker.heroesevolved.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Places every blueprint in open air and asks the game about each block it wrote: is it still what the blueprint
 * says (a neighbour placed after it can reshape or remove it), can it stay where it is ({@code canSurvive}), and would
 * the next neighbour update change it ({@code Block.updateFromNeighbourShapes}: a stair corner, a pane's arms, a fence
 * joining a wall, a door half losing its partner)? A block next to a keep cell ({@code _}) depends on what was already
 * in the world there, so it is reported as a note, not a failure. {@code tools/blueprint.py validate} runs the same rules
 * without the game; this test is the authority when the two disagree.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class BlueprintPlacementGameTests {
    @GameTest(template = "rewind_empty", batch = "blueprint.placement", timeoutTicks = 400)
    public static void everyBlueprintBlockStaysAsDrawn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TreeSet<String> names = new TreeSet<>();
        level.getServer().getResourceManager().listResources("blueprints", id -> id.getPath().endsWith(".blueprint"))
                .keySet().stream().filter(id -> id.getNamespace().equals("heroesevolved"))
                .map(ResourceLocation::getPath).forEach(path -> names.add(path.substring("blueprints/".length(), path.length() - ".blueprint".length())));
        helper.assertTrue(names.size() >= 10, "Expected the mod's blueprints, found " + names);
        List<String> faults = new ArrayList<>();
        int notes = 0;
        BlockPos base = helper.absolutePos(new BlockPos(0, 90, 0));
        for (String name : names) {
            Blueprint blueprint = Blueprint.load(name);
            BlockPos at = base.offset(blueprint.anchorX, blueprint.anchorY, blueprint.anchorZ);
            BlockPos min = base.offset(-1, -1, -1), max = base.offset(blueprint.width(), blueprint.height(), blueprint.depth());
            clear(level, min, max);
            blueprint.place(level, at, Rotation.NONE);
            var drawn = blueprint.statesMatching(state -> true);
            for (var cell : drawn.entrySet()) {
                BlockState want = cell.getValue();
                if (want.isAir()) continue;
                BlockPos local = cell.getKey(), pos = at.offset(local);
                BlockPos grid = local.offset(blueprint.anchorX, blueprint.anchorY, blueprint.anchorZ);
                BlockState got = level.getBlockState(pos);
                String problem = null;
                if (got != want && !joinsOnly(want, got)) problem = got.isAir() ? "was removed as its neighbours were placed"
                        : "became " + text(got) + " once its neighbours were placed";
                else if (!got.canSurvive(level, pos)) problem = "can't stay here (nothing holds it)";
                else {
                    BlockState next = Block.updateFromNeighbourShapes(got, level, pos);
                    if (next != got) problem = "would become " + text(next) + " on the next block update";
                }
                if (problem == null) continue;
                String line = name + " layer " + grid.getY() + " row " + grid.getZ() + " col " + grid.getX() + ": " + text(want) + " " + problem;
                if (besideKeep(blueprint, grid)) { notes++; System.out.println("PLACEMENT note " + line + " (beside a keep cell)"); }
                else { faults.add(line); System.out.println("PLACEMENT fault " + line); }
            }
            clear(level, min, max);
        }
        System.out.println("PLACEMENT checked " + names.size() + " blueprints: " + faults.size() + " faults, " + notes + " notes");
        if (!faults.isEmpty()) helper.fail(faults.size() + " blueprint blocks don't stay as drawn, first: " + faults.getFirst());
        helper.succeed();
    }

    /**
     * True when the only difference is a property the game always works out from the neighbours and no blueprint needs
     * to write: the arms of panes, bars, fences and walls, a gate dropped into a wall, leaf distance, snowy grass.
     * Placement settles these itself. A stair's shape, a vine's faces, a chest's pairing and a door's halves are not
     * on this list: if the game changes them, the blueprint drew something else than what stands.
     */
    static boolean joinsOnly(BlockState want, BlockState got) {
        if (want.getBlock() != got.getBlock()) return false;
        java.util.Set<String> free = new java.util.HashSet<>();
        var block = want.getBlock();
        if (block instanceof net.minecraft.world.level.block.CrossCollisionBlock || block instanceof net.minecraft.world.level.block.WallBlock)
            free.addAll(List.of("north", "south", "east", "west", "up"));
        if (block instanceof net.minecraft.world.level.block.FenceGateBlock) free.add("in_wall");
        if (block instanceof net.minecraft.world.level.block.LeavesBlock) free.add("distance");
        if (block instanceof net.minecraft.world.level.block.SnowyDirtBlock) free.add("snowy");
        if (block instanceof net.minecraft.world.level.block.HugeMushroomBlock) free.addAll(List.of("north", "south", "east", "west", "up", "down"));
        if (block instanceof net.minecraft.world.level.block.RedStoneWireBlock || block instanceof net.minecraft.world.level.block.TripWireBlock)
            free.addAll(List.of("north", "south", "east", "west", "power", "attached"));
        for (var property : want.getProperties())
            if (!want.getValue(property).equals(got.getValue(property)) && !free.contains(property.getName())) return false;
        return true;
    }

    /** True when any of the six neighbours is a keep cell, or the cell is on the blueprint's edge. */
    private static boolean besideKeep(Blueprint blueprint, BlockPos grid) {
        for (Direction side : Direction.values()) {
            BlockPos n = grid.relative(side);
            if (n.getX() < 0 || n.getY() < 0 || n.getZ() < 0 || n.getX() >= blueprint.width() || n.getY() >= blueprint.height()
                    || n.getZ() >= blueprint.depth())
                return true;   // the world outside the grid: unknown

            if (blueprint.isKeep(n.getX(), n.getY(), n.getZ())) return true;
        }
        return false;
    }

    private static void clear(ServerLevel level, BlockPos min, BlockPos max) {
        for (BlockPos pos : BlockPos.betweenClosed(min, max))
            if (!level.getBlockState(pos).isAir())
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
        for (Entity entity : level.getEntities((Entity) null, new AABB(min.getCenter(), max.getCenter()).inflate(2), e -> !(e instanceof net.minecraft.world.entity.player.Player)))
            entity.discard();
    }

    private static String text(BlockState state) {
        return state.toString().replace("Block{minecraft:", "").replace("Block{", "").replace("}", "");
    }
}
