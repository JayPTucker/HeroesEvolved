package com.jayptucker.heroesevolved.story.bunker;

import com.jayptucker.heroesevolved.story.bunker.BunkerBuilder.Room;
import com.jayptucker.heroesevolved.structure.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Where everything in a stasis bunker is, relative to its origin (the floor in front of the vault, under spawn). The
 * one place code asks; nothing else writes these numbers down.
 * <ul>
 *   <li>{@link #STASIS}: Sublevel 4 as rebuilt in November 2026, drawn by {@code tools/generate-bunker.py} (the rooms'
 *   corners below must match what it prints). Every room has a {@code _past} twin, as it was on the Last Day. It has the
 *   private pod in Sublevel 4-R, the Helix Ward.</li>
 *   <li>{@link #LEGACY}: the bunker as worlds built before that have it. Its rooms stay as they were built, so its pods,
 *   receiver and lights stay where they were, and {@link BunkerBuilder#upgrade} still brings its fixtures up to date.</li>
 * </ul>
 * A bunker remembers which it is ({@link BunkerData#design}). The bunkers of the past (the Last Day's under Primatech,
 * Night Three's) are always {@link #STASIS}, built from the {@code _past} rooms.
 */
public final class BunkerLayout {
    /** {@link BunkerData#design} of a bunker built with {@link #STASIS}. */
    public static final int STASIS_DESIGN = 2;
    /** Fourteen pods in the hall; the private pod in 4-R is number fifteen, index 14. */
    public static final int HALL_PODS = 14, LAB_POD = 14;

    private final boolean stasis;
    private final List<Room> rooms, deep;

    private BunkerLayout(boolean stasis, List<Room> rooms, List<Room> deep) {
        this.stasis = stasis; this.rooms = rooms; this.deep = deep;
    }

    public static final BunkerLayout STASIS = new BunkerLayout(true, List.of(
            room("stasis_airlock", -6, 0, 1), room("stasis_generator", 21, 0, 9), room("stasis_lab", 11, 0, 24),
            room("stasis_records", 11, 0, 7), room("stasis_east", 12, 0, 19), room("stasis_infirmary", -35, 0, 11),
            room("stasis_containment", -22, 0, 23), room("stasis_west", -23, 0, 19), room("stasis_hall", -10, 0, 32),
            room("stasis_commons", -11, 0, 10), room("stasis_cave", -20, -2, 1)),
            List.of(room("stasis_maintenance", -2, -8, 1)));

    public static final BunkerLayout LEGACY = new BunkerLayout(false, BunkerBuilder.ROOMS, List.of(BunkerBuilder.OFFICE));

    private static Room room(String blueprint, int x, int y, int z) { return new Room(blueprint, new BlockPos(x, y, z)); }

    public static BunkerLayout of(BunkerData data) { return data.design >= STASIS_DESIGN ? STASIS : LEGACY; }

    public boolean stasis() { return stasis; }

    /** The rooms on the main level, in the order they are placed. */
    public List<Room> rooms() { return rooms; }

    /** Rooms below the main level, left out where the world isn't deep enough. */
    public List<Room> deep() { return deep; }

    /** Every room, the deep ones included. */
    public List<Room> all() {
        List<Room> all = new ArrayList<>(rooms);
        all.addAll(deep);
        return all;
    }

    /** The rooms as they were before the hundred and three years (only {@link #STASIS} has them; the cave came later). */
    public List<Room> past() {
        List<Room> past = new ArrayList<>();
        for (Room room : all()) if (Blueprint.exists(room.blueprint() + "_past")) past.add(new Room(room.blueprint() + "_past", room.at()));
        return past;
    }

    /** Whether the private pod in 4-R exists in this layout. */
    public boolean hasLab() { return stasis; }

    /** How many pods: fourteen in the hall, and the lab's where there is one. */
    public int pods() { return stasis ? HALL_PODS + 1 : HALL_PODS; }

    /** Pod {@code index}'s base block. Seven down the west wall (0 to 6, north first), seven down the east, then the lab's. */
    public BlockPos podOffset(int index) {
        if (!stasis) return new BlockPos(index < 7 ? -5 : 5, 1, 20 + 2 * (index % 7));
        if (index == LAB_POD) return new BlockPos(22, 2, 41);
        return new BlockPos(index < 7 ? -8 : 8, 1, 38 + 4 * (index % 7));
    }

    /** The way pod {@code index}'s door faces. */
    public Direction podFacing(int index) {
        if (stasis && index == LAB_POD) return Direction.NORTH;
        return index < 7 ? Direction.EAST : Direction.WEST;
    }

    /** The nameplate over pod {@code index}, or null if it has none. */
    public @Nullable BlockPos podSign(int index) {
        String marker = String.format("sign:pod%02d", index + 1);
        for (Room room : rooms) {
            if (!room.blueprint().equals(stasis ? (index == LAB_POD ? "stasis_lab" : "stasis_hall") : BunkerBuilder.CHAMBER.blueprint())) continue;
            List<BlockPos> cells = Blueprint.load(room.blueprint()).markerOffsets(marker);
            if (!cells.isEmpty()) return room.at().offset(cells.getFirst());
        }
        return null;
    }

    /** The lectern beside Pod 14 with Claire's note, read from the step in front of the pod. */
    public BlockPos claireLectern() { return stasis ? new BlockPos(7, 1, 63) : new BlockPos(4, 1, 33); }

    /** Where the bunker's power comes from: the lights come on in a wave out from here. */
    public BlockPos generator() { return stasis ? new BlockPos(30, 1, 19) : new BlockPos(10, 1, 6); }

    /** The orientation log's lectern in the stasis hall. */
    public BlockPos orientation() { return marker("log:orientation").getFirst(); }

    /** The signal receiver in Records. */
    public BlockPos receiver() { return marker("signal:receiver").getFirst(); }

    /** Where Mara and Ivo recover in the infirmary. */
    public List<BlockPos> survivors() { return marker("signal:survivor"); }

    /** Every cell carrying {@code marker} in any room (present blueprints), relative to the origin. */
    public List<BlockPos> marker(String marker) {
        List<BlockPos> found = new ArrayList<>();
        for (Room room : all())
            for (BlockPos cell : Blueprint.load(room.blueprint()).markerOffsets(marker)) found.add(room.at().offset(cell));
        return found;
    }

    private BoundingBox bounds;

    /** The space the rooms take up, relative to the origin. */
    public BoundingBox bounds() {
        if (bounds == null) {
            BoundingBox box = new BoundingBox(BlockPos.ZERO);
            for (Room room : all()) {
                Blueprint blueprint = Blueprint.load(room.blueprint());
                box.encapsulate(room.at().offset(blueprint.offset(0, 0, 0, Rotation.NONE)));
                box.encapsulate(room.at().offset(blueprint.offset(blueprint.width() - 1, blueprint.height() - 1, blueprint.depth() - 1, Rotation.NONE)));
            }
            bounds = box;
        }
        return bounds;
    }

    /** Whether {@code pos} is inside the bunker whose origin is {@code origin}. */
    public boolean inside(BlockPos origin, BlockPos pos) { return bounds().isInside(pos.subtract(origin)); }

    /**
     * The places the scenes of the past use in {@link #STASIS} (the Last Day under Primatech, Night Three), relative to the
     * origin. The hall's numbers match {@code tools/generate-bunker.py}.
     */
    public static final class Scene {
        private Scene() {}

        /** The stasis hall from floor to ceiling, door to far wall, its walls (and the lamps in them) included. */
        public static final BoundingBox HALL = new BoundingBox(-10, 1, 33, 10, 11, 69);
        /** Just inside the hall from the commons: where anyone left outside it is brought. */
        public static final Vec3 GATHER = new Vec3(0.5, 1, 37.5);
        /** Where the Last Day's rescued are laid down, either side of the aisle just inside the hall (nudged to open floor). */
        public static final List<Vec3> RESCUED = List.of(new Vec3(-2.5, 1, 40.5), new Vec3(3.5, 1, 40.5), new Vec3(-2.5, 1, 43.5),
                new Vec3(3.5, 1, 43.5), new Vec3(-2.5, 1, 46.5), new Vec3(3.5, 1, 46.5));
        /** Noah and Claire by Pod 14 when the survivors come in, and where Noah turns to face them. */
        public static final Vec3 NOAH = new Vec3(4.5, 1, 58.5), CLAIRE = new Vec3(5.5, 1, 59.5), NOAH_TURN = new Vec3(4.5, 1, 55.5);
        /** The guards below: by the vault, in the airlock, in the commons and at the hall's door. */
        public static final List<Vec3> GUARDS = List.of(new Vec3(-1.5, 1, -1.5), new Vec3(-2.5, 1, -5.5), new Vec3(-2.5, 1, 2.5),
                new Vec3(0.5, 1, 14.5), new Vec3(-2.5, 1, 34.5), new Vec3(3.5, 1, 34.5));
        /** The yaw each guard faces. */
        public static final float[] GUARD_YAW = {160, 180, 160, 0, 0, 0};
        /** Where the blast's stones fall: the aisle either side of the runner, north of the grand stair. */
        public static final int RUBBLE_X0 = -4, RUBBLE_X1 = 4, RUBBLE_Z0 = 36, RUBBLE_Z1 = 59;
        /** Where the hall's ceiling came down in the present, whole in the past. */
        public static final BlockPos COLLAPSE = new BlockPos(-4, 12, 56);

        /** Night Three: runners arrive on the generator hall's floor, in two rows south of the core. */
        public static Vec3 arrival(int slot) { return slot < 6 ? new Vec3(25.5 + 2 * slot, 1, 24.5) : new Vec3(25.5 + 2 * (slot - 6), 1, 26.5); }
        /** Inside Gabriel Gray's cell in containment, and the bars he unmakes. */
        public static final Vec3 CAGE = new Vec3(-13.5, 1, 26.5);
        public static final List<BlockPos> CAGE_BARS = List.of(new BlockPos(-15, 1, 26), new BlockPos(-15, 2, 26), new BlockPos(-15, 3, 26),
                new BlockPos(-15, 1, 27), new BlockPos(-15, 2, 27), new BlockPos(-15, 3, 27));
        /** On the floor in front of Pod 14. */
        public static final Vec3 POD_FRONT = new Vec3(6.5, 1, 62.5);
        /** Gray's way from his cage to Pod 14: through his bars, up the aisle, the west corridor, the commons, the hall. */
        public static final List<Vec3> GRAY_ROUTE = List.of(new Vec3(-14.5, 1, 26.5), new Vec3(-16.5, 1, 26.5), new Vec3(-16.5, 1, 21.5),
                new Vec3(-10.5, 1, 21.5), new Vec3(0.5, 1, 21.5), new Vec3(0.5, 1, 33.5), new Vec3(4.5, 1, 37.5), new Vec3(4.5, 1, 59.5),
                POD_FRONT);
    }
}
