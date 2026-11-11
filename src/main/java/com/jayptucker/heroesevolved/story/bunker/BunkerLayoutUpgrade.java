package com.jayptucker.heroesevolved.story.bunker;

import com.jayptucker.heroesevolved.formula.BloodPurity;
import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.structure.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.util.List;

import static com.jayptucker.heroesevolved.story.bunker.BunkerBuilder.CHAMBER;
import static com.jayptucker.heroesevolved.story.bunker.BunkerBuilder.CORRIDOR;
import static com.jayptucker.heroesevolved.story.bunker.BunkerBuilder.INFIRMARY;
import static com.jayptucker.heroesevolved.story.bunker.BunkerBuilder.LAB;
import static com.jayptucker.heroesevolved.story.bunker.BunkerBuilder.OFFICE;
import static com.jayptucker.heroesevolved.story.bunker.BunkerBuilder.RECORDS;
import static com.jayptucker.heroesevolved.story.bunker.BunkerBuilder.SECURITY;

/** The layout 5 update for generated fixtures. Custom blocks, books and chest contents are left in place. */
final class BunkerLayoutUpgrade {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState BULB = Blocks.WAXED_WEATHERED_COPPER_BULB.defaultBlockState()
            .setValue(BlockStateProperties.LIT, true);
    private final ServerLevel level;
    private final BunkerData data;

    private BunkerLayoutUpgrade(ServerLevel level, BunkerData data) {
        this.level = level;
        this.data = data;
    }

    static boolean ready(ServerLevel level, BlockPos origin) {
        for (BunkerBuilder.Room room : BunkerBuilder.ROOMS)
            if (!loaded(level, origin, room)) return false;
        if (hasOffice(level, origin) && !loaded(level, origin, OFFICE)) return false;
        return level.isPositionEntityTicking(at(origin, RECORDS, cell(3, 2, 1)))
                && level.isPositionEntityTicking(at(origin, CHAMBER, cell(1, 8, 12)))
                && (!hasOffice(level, origin) || level.isPositionEntityTicking(at(origin, OFFICE, cell(4, 3, 1))));
    }

    private static boolean loaded(ServerLevel level, BlockPos origin, BunkerBuilder.Room room) {
        Blueprint blueprint = Blueprint.load(room.blueprint());
        BlockPos start = origin.offset(room.at());
        ChunkPos min = new ChunkPos(start.offset(blueprint.offset(0, 0, 0, Rotation.NONE)));
        ChunkPos max = new ChunkPos(start.offset(blueprint.offset(blueprint.width() - 1, 0, blueprint.depth() - 1, Rotation.NONE)));
        for (int x = min.x; x <= max.x; x++)
            for (int z = min.z; z <= max.z; z++)
                if (!level.hasChunk(x, z)) return false;
        return true;
    }

    static void apply(ServerLevel level, BunkerData data) {
        BunkerLayoutUpgrade update = new BunkerLayoutUpgrade(level, data);
        update.hall();
        update.corridor();
        update.records();
        update.infirmary();
        update.security();
        update.lab();
        if (hasOffice(level, data.origin)) update.office();
        update.glowstoneFixtures();
    }

    private void hall() {
        beacon(CHAMBER, Direction.DOWN, 4, 10, 4);
        beacon(CHAMBER, Direction.EAST, 1, 9, 6);
        beacon(CHAMBER, Direction.WEST, 13, 9, 11);
        beacon(CHAMBER, Direction.NORTH, 7, 8, 21);
        replace(CHAMBER, Blocks.REDSTONE_LAMP.defaultBlockState(), light(BULB), cell(5, 3, 1), cell(9, 3, 1));
        remove(CHAMBER, Blocks.REDSTONE_WIRE, cell(8, 1, 2), cell(9, 1, 3), cell(10, 1, 5), cell(10, 1, 7),
                cell(11, 1, 10), cell(10, 1, 11), cell(10, 1, 13));
        painting(CHAMBER, "sleeper", cell(1, 8, 12));
        removeSign(CHAMBER, cell(2, 8, 9), "EXHIBIT 9", "I. MENDEZ", "THE SLEEPER", "WHO IS THIS?");
        rewriteSign(CHAMBER, cell(11, 3, 21), BunkerBuilder.OLD_EXHIBIT_LABEL, DyeColor.YELLOW,
                "EXHIBIT 1", "I. MENDEZ", "ODESSA", "RECOVERED ART");
        rewriteSign(CHAMBER, cell(11, 3, 10), List.of("SUBJECT 12", "T. SPRAGUE", "TRANSFERRED", "KIRBY PLAZA"), DyeColor.LIGHT_BLUE,
                "SUBJECT 12", "STASIS SEALED", "AWAITING", "REVIVAL");
        rewriteSign(CHAMBER, cell(11, 3, 12), List.of("SUBJECT 13", "P. PETRELLI", "STATUS:", "MISSING"), DyeColor.LIGHT_BLUE,
                "SUBJECT 13", "STASIS SEALED", "AWAITING", "REVIVAL");
        rewriteSign(CHAMBER, cell(11, 3, 14), List.of("SUBJECT 14", "C. BENNET", "BLOOD DRAWN", "DAY 3"), DyeColor.LIGHT_BLUE,
                "SUBJECT 14", "STASIS SEALED", "AWAITING", "REVIVAL");
        rewriteLog(CHAMBER, cell(7, 1, 8), "Stasis Wing: Orientation", "Dr. M. Suresh", BunkerLogs.orientation());
    }

    private void corridor() {
        remove(CORRIDOR, Blocks.REDSTONE_WIRE, cell(2, 1, 1), cell(1, 1, 3), cell(2, 1, 5), cell(2, 1, 6));
        replace(CORRIDOR, Blocks.REDSTONE_LAMP.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState(),
                cell(0, 4, 2), cell(4, 4, 2), cell(0, 4, 5), cell(4, 4, 5));
        BlockState newCeilingLight = Blocks.WAXED_COPPER_BULB.defaultBlockState().setValue(BlockStateProperties.LIT, true);
        replace(CORRIDOR, Blocks.RED_STAINED_GLASS.defaultBlockState(), light(newCeilingLight), cell(2, 5, 5));
        rewriteSign(CORRIDOR, cell(1, 2, 2), List.of("CONTAINMENT", "G. GRAY", "DO NOT OPEN", ""), DyeColor.WHITE,
                "INFIRMARY", "RECOVERY", "WEST", "");
    }

    private void records() {
        beacon(RECORDS, Direction.DOWN, 8, 4, 2);
        painting(RECORDS, "eclipse", cell(3, 2, 1));
        removeSign(RECORDS, cell(5, 3, 1), "EXHIBIT 4", "I. MENDEZ", "THE BLACK SUN", "");
        rewriteLog(RECORDS, cell(3, 1, 5), "Lockdown Order 7", "N. Bennet", BunkerLogs.lockdown());
        replaceLog(RECORDS, cell(7, 1, 5), "Day 2,190", "Dr. M. Suresh", Direction.SOUTH, AIR);
        removeBook(RECORDS, cell(5, 1, 8), 0, "A Note, Folded Small", "H. N.");

        remove(RECORDS, Blocks.BLACK_STAINED_GLASS_PANE, cell(7, 2, 2));
        BlockPos indicator = cell(8, 2, 2);
        boolean emptyIndicator = level.getBlockState(at(RECORDS, indicator)).isAir();
        addMissing(RECORDS, marker -> marker.startsWith("signal:") || marker.equals("sign:receiver"));
        if (emptyIndicator && !data.powered) replace(RECORDS, BULB, light(BULB), indicator);
        BlockState taskLight = Blocks.WAXED_EXPOSED_COPPER_BULB.defaultBlockState().setValue(BlockStateProperties.LIT, true);
        replace(RECORDS, Blocks.DEEPSLATE_BRICKS.defaultBlockState(), light(taskLight), cell(7, 5, 2));
    }

    private void infirmary() {
        remove(INFIRMARY, Blocks.REDSTONE_WIRE, cell(6, 1, 3), cell(4, 1, 4), cell(5, 1, 4), cell(6, 1, 4));
        remove(INFIRMARY, Blocks.IRON_BARS, cell(5, 1, 2), cell(5, 2, 2));
        Blueprint.load(INFIRMARY.blueprint()).statesMatching(state -> state.is(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE)).forEach((cell, pane) -> {
            BlockPos pos = data.origin.offset(INFIRMARY.at()).offset(cell);
            if (level.getBlockState(pos).is(Blocks.IRON_BARS)) level.setBlock(pos, pane, Block.UPDATE_ALL);
        });
        remove(INFIRMARY, Blocks.TRIPWIRE_HOOK, cell(1, 2, 6));
        replace(INFIRMARY, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 3),
                Blocks.FLOWER_POT.defaultBlockState(), cell(2, 2, 6));
        replaceLog(INFIRMARY, cell(4, 1, 6), "Containment: G. Gray", "Security", Direction.NORTH, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        replace(INFIRMARY, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), Blocks.CYAN_TERRACOTTA.defaultBlockState(),
                cell(6, 0, 3), cell(7, 0, 3), cell(6, 0, 4), cell(7, 0, 4));
        BlockState oldLight = Blocks.SOUL_LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
        replace(INFIRMARY, data.powered ? oldLight : AIR, light(BULB), cell(1, 4, 1));
        BlockState examLight = Blocks.WAXED_COPPER_BULB.defaultBlockState().setValue(BlockStateProperties.LIT, true);
        replace(INFIRMARY, Blocks.DEEPSLATE_BRICKS.defaultBlockState(), light(Blocks.SEA_LANTERN.defaultBlockState()), cell(4, 4, 3));
        replace(INFIRMARY, Blocks.DEEPSLATE_BRICKS.defaultBlockState(), light(examLight), cell(4, 4, 6));
        if (chest(INFIRMARY, cell(6, 1, 6)) instanceof ChestBlockEntity chest
                && chest.getItem(0).getCount() == 1 && ItemStack.isSameItemSameComponents(chest.getItem(0), new ItemStack(Items.CLOCK)))
            chest.setItem(0, ItemStack.EMPTY);
        updateBeds();
        addMissing(INFIRMARY, "sign:infirmary"::equals);
    }

    private void updateBeds() {
        BlockPos foot = at(INFIRMARY, cell(5, 1, 1));
        BlockPos head = at(INFIRMARY, cell(6, 1, 1));
        if (level.getBlockState(foot).is(Blocks.BONE_BLOCK) && level.getBlockState(head).is(Blocks.SKELETON_SKULL)) {
            BlockState white = Blocks.WHITE_BED.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
            placeBed(foot, head, white);
        }
        BlockPos oldHead = at(INFIRMARY, cell(3, 1, 3));
        BlockPos oldFoot = at(INFIRMARY, cell(3, 1, 4));
        BlockState gray = Blocks.GRAY_BED.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        if (level.getBlockState(oldHead).equals(gray.setValue(BlockStateProperties.BED_PART, BedPart.HEAD))
                && level.getBlockState(oldFoot).equals(gray))
            placeBed(oldHead, oldFoot, gray);
    }

    private void placeBed(BlockPos foot, BlockPos head, BlockState bed) {
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        level.setBlock(foot, bed, flags);
        level.setBlock(head, bed.setValue(BlockStateProperties.BED_PART, BedPart.HEAD), flags);
    }

    private void security() {
        beacon(SECURITY, Direction.DOWN, 2, 4, 7);
        remove(SECURITY, Blocks.RED_CARPET, cell(2, 1, 7), cell(3, 1, 7), cell(2, 1, 8), cell(3, 1, 8));
        removeBook(SECURITY, cell(7, 1, 9), 10, "Shift Notes", "Checkpoint B");
        rewriteSign(SECURITY, cell(6, 3, 5), List.of("CHECKPOINT", "ALL STAFF", "SHOW ID", ""), DyeColor.WHITE,
                "CHECKPOINT", "EMERGENCY", "SUPPLIES", "");
        addMissing(SECURITY, "sign:maintenance_hatch"::equals);
    }

    private void office() {
        painting(OFFICE, "bennets", cell(4, 3, 1));
        replaceLog(OFFICE, cell(7, 1, 4), "For Claire", "N. Bennet", Direction.WEST, Blocks.CRAFTING_TABLE.defaultBlockState());
        rewriteSign(OFFICE, cell(3, 3, 7), List.of("SUBLEVEL 4-B", "N. BENNET", "NOT ON THE", "PLANS"), DyeColor.WHITE,
                "MAINTENANCE", "WORKBENCH", "SPARE STORAGE", "");
        if (chest(OFFICE, cell(7, 1, 1)) instanceof ChestBlockEntity chest) {
            ItemStack oldSample = chest.getItem(0);
            ItemStack legacySample = new ItemStack(ModItems.BLOOD_SAMPLE.get());
            legacySample.set(ModItems.BLOOD_PURITY.get(), new BloodPurity(true, 3, "Claire Bennet"));
            if (oldSample.getCount() == 1 && ItemStack.isSameItemSameComponents(oldSample, legacySample))
                chest.setItem(0, ItemStack.EMPTY);
        }
    }

    private void lab() {
        rewriteLog(LAB, cell(3, 1, 5), "Assay: Helixite", "Pinehearst liaison", BunkerLogs.helixite());
        if (chest(LAB, cell(6, 1, 3)) instanceof ChestBlockEntity chest && chest.getItem(6).isEmpty())
            chest.setItem(6, BunkerBuilder.donatedSample());
    }

    private void glowstoneFixtures() {
        BunkerPower.forEachFixture(data, (pos, on) -> {
            // Layout 4 used an inert vanilla lamp as the dark state of these concealed lights.
            if (on.is(Blocks.GLOWSTONE) && level.getBlockState(pos).equals(Blocks.REDSTONE_LAMP.defaultBlockState()))
                level.setBlock(pos, light(on), Block.UPDATE_ALL);
        });
    }

    private BlockState light(BlockState state) {
        return data.powered ? state : BunkerPower.unpowered(0, state);
    }

    private void beacon(BunkerBuilder.Room room, Direction facing, int x, int y, int z) {
        replace(room, ModBlocks.ALARM_BEACON.get().defaultBlockState().setValue(AlarmBeaconBlock.FACING, facing), AIR, cell(x, y, z));
    }

    private void remove(BunkerBuilder.Room room, Block expected, BlockPos... cells) {
        for (BlockPos cell : cells) {
            BlockPos pos = at(room, cell);
            BlockState state = level.getBlockState(pos);
            if (!state.is(expected) || expected == Blocks.REDSTONE_WIRE && state.getValue(BlockStateProperties.POWER) > 0) continue;
            level.setBlock(pos, AIR, Block.UPDATE_ALL);
        }
    }

    private void replace(BunkerBuilder.Room room, BlockState expected, BlockState replacement, BlockPos... cells) {
        for (BlockPos cell : cells) {
            BlockPos pos = at(room, cell);
            if (level.getBlockState(pos).equals(expected)) level.setBlock(pos, replacement, Block.UPDATE_ALL);
        }
    }

    private void painting(BunkerBuilder.Room room, String variant, BlockPos cell) {
        BlockPos pos = at(room, cell);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("heroesevolved", variant);
        level.getEntitiesOfClass(Painting.class, new AABB(pos).inflate(4),
                painting -> painting.getPos().equals(pos) && painting.getVariant().is(id)).forEach(Entity::discard);
    }

    private void addMissing(BunkerBuilder.Room room, java.util.function.Predicate<String> matches) {
        Blueprint.load(room.blueprint()).addMissing(level, data.origin.offset(room.at()), Rotation.NONE, matches);
    }

    private void removeSign(BunkerBuilder.Room room, BlockPos cell, String... original) {
        BlockPos pos = at(room, cell);
        if (originalSign(level, pos, List.of(original)) != null) level.setBlock(pos, AIR, Block.UPDATE_ALL);
    }

    static SignBlockEntity originalSign(ServerLevel level, BlockPos pos, List<String> original) {
        if (!level.getBlockState(pos).is(Blocks.SPRUCE_WALL_SIGN)
                || !(level.getBlockEntity(pos) instanceof SignBlockEntity sign) || !sign.isWaxed()) return null;
        for (int i = 0; i < original.size(); i++)
            if (!sign.getFrontText().getMessage(i, false).getString().equals(original.get(i))) return null;
        return sign;
    }

    private void rewriteSign(BunkerBuilder.Room room, BlockPos cell, List<String> original, DyeColor color,
                             String first, String second, String third, String fourth) {
        SignBlockEntity sign = originalSign(level, at(room, cell), original);
        if (sign == null) return;
        sign.setText(sign.getFrontText().setColor(color).setHasGlowingText(true)
                .setMessage(0, Component.literal(first)).setMessage(1, Component.literal(second))
                .setMessage(2, Component.literal(third)).setMessage(3, Component.literal(fourth)), true);
    }

    private void rewriteLog(BunkerBuilder.Room room, BlockPos cell, String title, String author, ItemStack book) {
        if (level.getBlockEntity(at(room, cell)) instanceof LecternBlockEntity lectern && bookMatches(lectern.getBook(), title, author))
            lectern.setBook(book);
    }

    private void replaceLog(BunkerBuilder.Room room, BlockPos cell, String title, String author, Direction facing, BlockState replacement) {
        BlockPos pos = at(room, cell);
        if (level.getBlockEntity(pos) instanceof LecternBlockEntity lectern
                && level.getBlockState(pos).getValue(LecternBlock.FACING) == facing
                && bookMatches(lectern.getBook(), title, author)) {
            lectern.setBook(ItemStack.EMPTY);
            level.setBlock(pos, replacement, Block.UPDATE_ALL);
        }
    }

    private net.minecraft.world.level.block.entity.BlockEntity chest(BunkerBuilder.Room room, BlockPos cell) {
        return level.getBlockEntity(at(room, cell));
    }

    private void removeBook(BunkerBuilder.Room room, BlockPos cell, int slot, String title, String author) {
        if (chest(room, cell) instanceof ChestBlockEntity chest && bookMatches(chest.getItem(slot), title, author))
            chest.setItem(slot, ItemStack.EMPTY);
    }

    private static boolean bookMatches(ItemStack book, String title, String author) {
        var content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
        return content != null && title.equals(content.title().raw()) && author.equals(content.author());
    }

    private static boolean hasOffice(ServerLevel level, BlockPos origin) {
        return origin.getY() + OFFICE.at().getY() > level.getMinBuildHeight();
    }

    private BlockPos at(BunkerBuilder.Room room, BlockPos cell) { return at(data.origin, room, cell); }

    private static BlockPos at(BlockPos origin, BunkerBuilder.Room room, BlockPos cell) {
        return origin.offset(room.at()).offset(Blueprint.load(room.blueprint()).offset(cell.getX(), cell.getY(), cell.getZ(), Rotation.NONE));
    }

    private static BlockPos cell(int x, int y, int z) { return new BlockPos(x, y, z); }
}
