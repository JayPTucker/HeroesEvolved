package com.jayptucker.heroesevolved.structure;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A text blueprint: a palette of single characters and horizontal layers drawn bottom-up as
 * grids (rows run north to south, +Z; columns west to east, +X). '.' is air and '_' leaves the
 * existing block. A palette entry may carry an {@code @marker} so code can finish that block
 * (fill a chest, put a book on a lectern). {@code @painting:<variant>:<facing>} on an air cell hangs a
 * painting whose anchor block is that cell. The same format is checked and rendered by
 * tools/blueprint.py; see .claude/skills/minecraft-building for the full reference.
 */
public final class Blueprint {
    private static final Pattern LAYER = Pattern.compile("^layer\\s+(\\d+)(?:\\s*-\\s*(\\d+))?\\s*:\\s*$");
    private static final Pattern ENTRY = Pattern.compile("^(\\S)\\s*=\\s*(\\S+)(?:\\s+@(\\S+))?\\s*$");
    private static final Pattern SIGN = Pattern.compile("^([a-z0-9_]+)((?:\\s+[a-z_]+)*)\\s*=\\s*(.*)$");
    /** Text for a {@code @sign:<id>} marker: up to four lines, a dye color, and whether it glows. */
    public record SignLines(List<String> lines, net.minecraft.world.item.DyeColor color, boolean glow) {}
    private static final Map<String, Blueprint> CACHE = new HashMap<>();

    public final String name;
    private final Map<Character, BlockState> palette;
    private final Map<Character, String> markers;
    private final Map<String, SignLines> signs;
    private final List<List<String>> layers;
    public final int anchorX, anchorY, anchorZ;

    private Blueprint(String name, Map<Character, BlockState> palette, Map<Character, String> markers, Map<String, SignLines> signs,
                      List<List<String>> layers, int[] anchor) {
        this.name = name; this.palette = palette; this.markers = markers; this.signs = signs; this.layers = layers;
        anchorX = anchor[0]; anchorY = anchor[1]; anchorZ = anchor[2];
    }

    public int height() { return layers.size(); }
    public int depth() { return layers.isEmpty() ? 0 : layers.getFirst().size(); }
    public int width() { return depth() == 0 ? 0 : layers.getFirst().getFirst().length(); }

    /** Loads {@code data/heroesevolved/blueprints/<name>.blueprint} from the mod jar, once. */
    public static Blueprint load(String name) {
        return CACHE.computeIfAbsent(name, key -> {
            try (InputStream stream = Blueprint.class.getResourceAsStream("/data/heroesevolved/blueprints/" + key + ".blueprint")) {
                if (stream == null) throw new IllegalArgumentException("No blueprint named " + key);
                return parse(key, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException error) {
                throw new IllegalStateException("Could not read blueprint " + key, error);
            }
        });
    }

    public static Blueprint parse(String fallbackName, String text) {
        Map<Character, BlockState> palette = new HashMap<>();
        Map<Character, String> markers = new HashMap<>();
        Map<String, SignLines> signs = new HashMap<>();
        Map<Integer, List<String>> layers = new TreeMap<>();
        String name = fallbackName;
        int[] anchor = {0, 0, 0};
        String section = "";
        List<String> current = null;
        int lineNumber = 0;
        for (String raw : text.split("\\R", -1)) {
            lineNumber++;
            boolean inLayer = section.equals("layer");
            if (inLayer && raw.stripLeading().startsWith("#")) continue;
            String line = inLayer ? raw.stripTrailing() : raw.split("#", 2)[0].stripTrailing();
            String stripped = line.strip();
            if (stripped.isEmpty()) { if (inLayer) section = ""; continue; }
            Matcher layer = LAYER.matcher(stripped);
            if (layer.matches()) {
                int low = Integer.parseInt(layer.group(1));
                int high = layer.group(2) == null ? low : Integer.parseInt(layer.group(2));
                current = new ArrayList<>();
                for (int y = low; y <= high; y++) layers.put(y, current);
                section = "layer";
                continue;
            }
            if (inLayer) { current.add(line); continue; }
            if (stripped.equals("palette:")) { section = "palette"; continue; }
            if (stripped.equals("signs:")) { section = "signs"; continue; }
            if (stripped.startsWith("name:")) { name = stripped.substring(5).strip(); section = ""; continue; }
            if (stripped.startsWith("anchor:")) {
                String[] parts = stripped.substring(7).strip().split("\\s+");
                for (int i = 0; i < 3; i++) anchor[i] = Integer.parseInt(parts[i]);
                section = "";
                continue;
            }
            if (section.equals("signs")) {
                Matcher sign = SIGN.matcher(stripped);
                if (!sign.matches()) throw new IllegalArgumentException(fallbackName + " line " + lineNumber + ": signs look like  id [color] [glow] = line | line");
                var color = net.minecraft.world.item.DyeColor.BLACK;
                boolean glow = false;
                for (String option : sign.group(2).strip().split("\\s+")) {
                    if (option.isEmpty()) continue;
                    if (option.equals("glow")) glow = true;
                    else color = net.minecraft.world.item.DyeColor.byName(option, net.minecraft.world.item.DyeColor.BLACK);
                }
                List<String> lines = Arrays.stream(sign.group(3).split("\\|", -1)).map(String::strip).limit(4).toList();
                signs.put(sign.group(1), new SignLines(lines, color, glow));
                continue;
            }
            if (section.equals("palette")) {
                Matcher entry = ENTRY.matcher(stripped);
                if (!entry.matches()) throw new IllegalArgumentException(fallbackName + " line " + lineNumber + ": bad palette entry");
                char key = entry.group(1).charAt(0);
                String state = entry.group(2);
                if (!state.equals("keep")) palette.put(key, state(state, fallbackName, lineNumber));
                if (entry.group(3) != null) markers.put(key, entry.group(3));
                continue;
            }
            throw new IllegalArgumentException(fallbackName + " line " + lineNumber + ": unexpected text");
        }
        List<List<String>> ordered = new ArrayList<>();
        for (int y = 0; layers.containsKey(y); y++) ordered.add(layers.get(y));
        if (ordered.size() != layers.size()) throw new IllegalArgumentException(fallbackName + ": layers must start at 0 with no gaps");
        return new Blueprint(name, palette, markers, signs, ordered, anchor);
    }

    private static BlockState state(String text, String blueprint, int line) {
        String full = text.contains(":") && !text.startsWith("[") ? text : "minecraft:" + text;
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), full, false).blockState();
        } catch (Exception error) {
            throw new IllegalArgumentException(blueprint + " line " + line + ": " + error.getMessage(), error);
        }
    }

    /** Where a grid cell lands, relative to the placement position, after rotation about the anchor. */
    public BlockPos offset(int x, int y, int z, Rotation rotation) {
        BlockPos local = new BlockPos(x - anchorX, y - anchorY, z - anchorZ);
        return local.rotate(rotation);
    }

    /**
     * Places the blueprint with its anchor at {@code at}. Never replaces bedrock or other
     * unbreakable blocks. Returns every marked block's world position, by marker name.
     */
    public Map<String, List<BlockPos>> place(ServerLevel level, BlockPos at, Rotation rotation) {
        return place(level, at, rotation, layers.size() - 1);
    }

    /** Places layers up to and including {@code topLayer}: a cut-away for looking inside. */
    public Map<String, List<BlockPos>> place(ServerLevel level, BlockPos at, Rotation rotation, int topLayer) {
        Map<String, List<BlockPos>> marked = new HashMap<>();
        Map<BlockPos, String> paintings = new LinkedHashMap<>();
        for (int y = 0; y < Math.min(layers.size(), topLayer + 1); y++) {
            List<String> rows = layers.get(y);
            for (int z = 0; z < rows.size(); z++) {
                String row = rows.get(z);
                for (int x = 0; x < row.length(); x++) {
                    char key = row.charAt(x);
                    if (key == '_') continue;
                    BlockState state = key == '.' ? Blocks.AIR.defaultBlockState() : palette.get(key);
                    if (state == null) continue;
                    BlockPos pos = at.offset(offset(x, y, z, rotation));
                    if (level.isOutsideBuildHeight(pos)) continue;
                    BlockState existing = level.getBlockState(pos);
                    if (!existing.isAir() && existing.getDestroySpeed(level, pos) < 0) continue;
                    level.setBlock(pos, state.rotate(rotation), Block.UPDATE_CLIENTS);
                    String marker = markers.get(key);
                    if (marker == null) continue;
                    marked.computeIfAbsent(marker, k -> new ArrayList<>()).add(pos.immutable());
                    if (marker.startsWith("painting:")) paintings.put(pos.immutable(), marker.substring(9));
                    SignLines text = marker.startsWith("sign:") ? signs.get(marker.substring(5)) : null;
                    if (text != null && level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
                        var front = new net.minecraft.world.level.block.entity.SignText().setColor(text.color()).setHasGlowingText(text.glow());
                        for (int i = 0; i < text.lines().size(); i++) front = front.setMessage(i, net.minecraft.network.chat.Component.literal(text.lines().get(i)));
                        sign.setText(front, true);
                        sign.setWaxed(true);
                    }
                }
            }
        }
        // Paintings last, once the walls they hang on exist.
        paintings.forEach((pos, spec) -> hangPainting(level, pos, spec, rotation));
        return marked;
    }

    /** {@code spec} is {@code <variant>:<facing>}, e.g. {@code explosion:east}; the variant is a heroesevolved painting. */
    private static void hangPainting(ServerLevel level, BlockPos pos, String spec, Rotation rotation) {
        String[] parts = spec.split(":");
        if (parts.length != 2) return;
        var direction = net.minecraft.core.Direction.byName(parts[1]);
        var variant = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.PAINTING_VARIANT)
                .getHolder(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("heroesevolved", parts[0]));
        if (direction == null || direction.getAxis().isVertical() || variant.isEmpty()) return;
        var bounds = new net.minecraft.world.phys.AABB(pos).inflate(8);
        level.getEntitiesOfClass(net.minecraft.world.entity.decoration.Painting.class, bounds, old -> old.getPos().equals(pos)).forEach(old -> old.discard());
        level.addFreshEntity(new net.minecraft.world.entity.decoration.Painting(level, pos, rotation.rotate(direction), variant.get()));
    }

    /** Every grid cell carrying {@code marker}, relative to the anchor, unrotated, in reading order. */
    public List<BlockPos> markerOffsets(String marker) {
        List<BlockPos> found = new ArrayList<>();
        for (int y = 0; y < layers.size(); y++)
            for (int z = 0; z < layers.get(y).size(); z++) {
                String row = layers.get(y).get(z);
                for (int x = 0; x < row.length(); x++)
                    if (marker.equals(markers.get(row.charAt(x)))) found.add(offset(x, y, z, Rotation.NONE));
            }
        return found;
    }
}
