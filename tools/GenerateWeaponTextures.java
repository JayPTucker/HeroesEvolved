import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Paints the power-themed TaCZ guns. Each gun borrows the shape and animations of one of TaCZ's own models (by
 * resource location, never copied), so this reads that model's geometry from the TaCZ jar and paints a new texture
 * onto its UV layout: every face gets its part's colour (body, grip, barrel, magazine, detail, glow) from the gun's
 * palette, shaded by which way it faces, with a dark panel line round its edge. No pixel of TaCZ's own textures is
 * used. It also draws each gun's inventory and HUD icons as a side view of the same geometry, the ammo cells, and the
 * EMP grenade's item icon.
 *
 * <pre>java tools/GenerateWeaponTextures.java [path/to/tacz.jar]</pre>
 *
 * Without an argument it finds the TaCZ jar Gradle downloaded (see tacz_version in gradle.properties).
 */
public final class GenerateWeaponTextures {
    private static final String ROOT = "src/main/resources/assets/heroesevolved/textures/";
    private static final String MODELS = "assets/tacz/custom/tacz_default_gun/assets/tacz/geo_models/";

    enum Part { BODY, GRIP, BARREL, MAG, DETAIL, GLOW }

    /** A gun's colours, one per part. */
    record Palette(int body, int grip, int barrel, int mag, int detail, int glow, boolean coils) {
        int of(Part part) {
            return switch (part) { case BODY -> body; case GRIP -> grip; case BARREL -> barrel; case MAG -> mag; case DETAIL -> detail; case GLOW -> glow; };
        }
    }

    static final Palette HEAT = new Palette(0x3A3532, 0x22201F, 0xB0703A, 0xF07A1E, 0x8A5A30, 0xFFC04A, true);
    static final Palette COLD = new Palette(0xD9E3E8, 0x59687A, 0x8FB3C9, 0x52D8F0, 0x3D4A5A, 0xBDF4FF, false);
    static final Palette TASER = new Palette(0xF2C230, 0x1F1F22, 0x2A2A2E, 0xF7D85A, 0x3A3A40, 0x7FD0FF, false);

    public static void main(String[] args) throws IOException {
        Path jar = args.length > 0 ? Path.of(args[0]) : findJar();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            gun(zip, "gun/p90_geo", HEAT, "heat_gun");
            gun(zip, "gun/aug_geo", COLD, "cold_gun");
            gun(zip, "gun/cz75_geo", TASER, "taser");
            ammo(zip, HEAT, "fuel_cell");
            ammo(zip, COLD, "cryo_cell");
            ammo(zip, TASER, "taser_cartridge");
        }
        write(grenade(), ROOT + "item/emp_grenade.png");
    }

    private static Path findJar() throws IOException {
        Path cache = Path.of(System.getProperty("user.home"), ".gradle/caches/modules-2/files-2.1/maven.modrinth/timeless-and-classics-zero");
        try (Stream<Path> files = Files.walk(cache)) {
            return files.filter(p -> p.toString().endsWith(".jar")).findFirst()
                    .orElseThrow(() -> new IOException("No TaCZ jar in " + cache + "; run ./gradlew compileJava first"));
        }
    }

    // ------------------------------------------------------------------
    // Guns
    // ------------------------------------------------------------------

    private static void gun(ZipFile zip, String model, Palette palette, String name) throws IOException {
        Model geo = Model.read(zip, MODELS + model + ".json");
        write(paint(geo, palette, 2), ROOT + "gun/uv/" + name + ".png");
        write(side(geo, palette, 64, 64, 4), ROOT + "gun/slot/" + name + ".png");
        write(side(geo, palette, 384, 128, 10), ROOT + "gun/hud/" + name + ".png");
    }

    private static void ammo(ZipFile zip, Palette palette, String name) throws IOException {
        Model geo = Model.read(zip, MODELS + "ammo/12g.json");
        // A cell, not a shell: the casing is the magazine colour, the cap glows.
        Palette cell = new Palette(palette.mag(), palette.grip(), palette.glow(), palette.mag(), palette.detail(), palette.glow(), false);
        write(paint(geo, cell, 2), ROOT + "ammo/uv/" + name + ".png");
        write(cellIcon(palette), ROOT + "ammo/slot/" + name + ".png");
    }

    /** The texture: each face of each cube filled in its part's colour on the model's own UV layout. */
    private static BufferedImage paint(Model geo, Palette palette, int scale) {
        BufferedImage image = new BufferedImage(geo.width * scale, geo.height * scale, BufferedImage.TYPE_INT_ARGB);
        for (Cube cube : geo.cubes) {
            if (cube.faces.isEmpty()) continue;
            for (Map.Entry<String, double[]> face : cube.faces.entrySet()) {
                double[] uv = face.getValue();
                int x0 = (int) Math.floor(Math.min(uv[0], uv[0] + uv[2]) * scale), x1 = (int) Math.ceil(Math.max(uv[0], uv[0] + uv[2]) * scale);
                int y0 = (int) Math.floor(Math.min(uv[1], uv[1] + uv[3]) * scale), y1 = (int) Math.ceil(Math.max(uv[1], uv[1] + uv[3]) * scale);
                // A face with no UV area still samples one texel.
                x1 = Math.max(x1, x0 + 1);
                y1 = Math.max(y1, y0 + 1);
                double shade = switch (face.getKey()) { case "up" -> 1.14; case "down" -> 0.68; case "north", "south" -> 0.9; default -> 1.0; };
                int base = palette.of(cube.part);
                for (int y = y0; y < y1; y++)
                    for (int x = x0; x < x1; x++) {
                        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) continue;
                        boolean edge = (x1 - x0 > 3 && (x == x0 || x == x1 - 1)) || (y1 - y0 > 3 && (y == y0 || y == y1 - 1));
                        double s = shade * (1 + (hash(x, y, cube.part.ordinal()) - 0.5) * 0.07);
                        if (edge && cube.part != Part.GLOW) s *= 0.72;
                        if (palette.coils() && cube.part == Part.BARREL && ((y - y0) / 2 + (x - x0) / 6) % 3 == 0) s *= 0.55;
                        if (cube.part == Part.MAG && (y - y0) % 6 == 2 && !edge) s *= 1.25;
                        image.setRGB(x, y, shadeColor(base, s));
                    }
            }
        }
        return image;
    }

    /** An icon: the gun from its right side, far cubes first, muzzle to the right. */
    private static BufferedImage side(Model geo, Palette palette, int width, int height, int margin) {
        List<Cube> shown = geo.cubes.stream().filter(c -> c.visible).sorted(Comparator.comparingDouble(c -> c.x0)).toList();
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Cube c : shown) {
            minZ = Math.min(minZ, c.z0); maxZ = Math.max(maxZ, c.z1);
            minY = Math.min(minY, c.y0); maxY = Math.max(maxY, c.y1);
        }
        double scale = Math.min((width - 2.0 * margin) / (maxZ - minZ), (height - 2.0 * margin) / (maxY - minY));
        double offX = (width - (maxZ - minZ) * scale) / 2, offY = (height - (maxY - minY) * scale) / 2;
        // Bedrock guns point down -Z; drawing -Z on the right puts the muzzle there.
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (Cube c : shown) {
            int x0 = (int) Math.round(offX + (maxZ - c.z1) * scale), x1 = (int) Math.round(offX + (maxZ - c.z0) * scale);
            int y0 = (int) Math.round(offY + (maxY - c.y1) * scale), y1 = (int) Math.round(offY + (maxY - c.y0) * scale);
            if (x1 <= x0) x1 = x0 + 1;
            if (y1 <= y0) y1 = y0 + 1;
            double depth = 0.82 + 0.18 * Math.clamp((c.x1 + 4) / 8.0, 0, 1);
            for (int y = Math.max(0, y0); y < Math.min(height, y1); y++)
                for (int x = Math.max(0, x0); x < Math.min(width, x1); x++) {
                    double s = depth * (y == y0 ? 1.18 : 1.0) * (1 + (hash(x, y, 9) - 0.5) * 0.05);
                    image.setRGB(x, y, shadeColor(palette.of(c.part), s));
                }
        }
        outline(image);
        return image;
    }

    /** A one-pixel dark outline round the silhouette, so icons read on any slot colour. */
    private static void outline(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        boolean[] solid = new boolean[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) solid[y * w + x] = (image.getRGB(x, y) >>> 24) != 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                if (solid[y * w + x]) continue;
                boolean near = false;
                for (int dy = -1; dy <= 1 && !near; dy++)
                    for (int dx = -1; dx <= 1 && !near; dx++) {
                        int nx = x + dx, ny = y + dy;
                        near = nx >= 0 && ny >= 0 && nx < w && ny < h && solid[ny * w + nx];
                    }
                if (near) image.setRGB(x, y, 0xFF141414);
            }
    }

    /** A 32x32 ammo icon: an upright cell with a glowing band and cap. */
    private static BufferedImage cellIcon(Palette palette) {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 5; y < 28; y++)
            for (int x = 10; x < 22; x++) {
                double round = 1 - Math.pow((x - 15.5) / 6.5, 2) * 0.45;
                int color = y < 9 ? palette.glow() : y > 24 ? palette.grip() : (y % 5 == 1 ? palette.glow() : palette.mag());
                image.setRGB(x, y, shadeColor(color, round * (1 + (hash(x, y, 3) - .5) * .06)));
            }
        outline(image);
        return image;
    }

    /** The EMP grenade's 16x16 item icon: a dark canister, a blue coil band, a lit fuse lamp and a pin ring. */
    private static BufferedImage grenade() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        int body = 0x2C3138, band = 0x3D7BFF, ion = 0xA8E4FF, metal = 0x9AA3AD;
        for (int y = 4; y < 15; y++)
            for (int x = 4; x < 12; x++) {
                double round = 1 - Math.pow((x - 7.5) / 4.0, 2) * 0.5;
                int color = (y == 8 || y == 10) ? band : y == 9 ? ion : body;
                image.setRGB(x, y, shadeColor(color, round));
            }
        for (int x = 6; x < 10; x++) image.setRGB(x, 3, shadeColor(metal, 1));
        image.setRGB(7, 2, 0xFFFF4A3A);
        image.setRGB(8, 2, 0xFFFF4A3A);
        image.setRGB(10, 2, shadeColor(metal, 1.1));
        image.setRGB(11, 1, shadeColor(metal, 1.1));
        image.setRGB(12, 2, shadeColor(metal, 1.1));
        image.setRGB(11, 3, shadeColor(metal, 1.1));
        outline(image);
        return image;
    }

    private static int shadeColor(int rgb, double s) {
        int r = (int) Math.clamp(((rgb >> 16) & 255) * s, 0, 255);
        int g = (int) Math.clamp(((rgb >> 8) & 255) * s, 0, 255);
        int b = (int) Math.clamp((rgb & 255) * s, 0, 255);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static double hash(int x, int y, int seed) {
        long h = x * 374761393L + y * 668265263L + seed * 2147483647L;
        h = (h ^ (h >> 13)) * 1274126177L;
        return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0;
    }

    private static void write(BufferedImage image, String path) throws IOException {
        File file = new File(path);
        file.getParentFile().mkdirs();
        ImageIO.write(image, "png", file);
        System.out.println("Wrote " + path + " (" + image.getWidth() + "x" + image.getHeight() + ")");
    }

    // ------------------------------------------------------------------
    // Bedrock geometry
    // ------------------------------------------------------------------

    /** One cube: its box in model space, its faces' UV rectangles ({u, v, width, height}) and its part. */
    static final class Cube {
        double x0, y0, z0, x1, y1, z1;
        final Map<String, double[]> faces = new LinkedHashMap<>();
        Part part;
        boolean visible;
    }

    static final class Model {
        int width, height;
        final List<Cube> cubes = new ArrayList<>();

        @SuppressWarnings("unchecked")
        static Model read(ZipFile zip, String entry) throws IOException {
            var found = zip.getEntry(entry);
            if (found == null) throw new IOException("No " + entry + " in the TaCZ jar");
            String text;
            try (InputStream in = zip.getInputStream(found)) { text = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
            Map<String, Object> root = (Map<String, Object>) new Json(text).value();
            Map<String, Object> geometry = (Map<String, Object>) ((List<Object>) root.get("minecraft:geometry")).get(0);
            Map<String, Object> description = (Map<String, Object>) geometry.get("description");
            Model model = new Model();
            model.width = ((Number) description.get("texture_width")).intValue();
            model.height = ((Number) description.get("texture_height")).intValue();
            Map<String, String> parents = new HashMap<>();
            List<Map<String, Object>> bones = (List<Map<String, Object>>) (List<?>) geometry.get("bones");
            java.util.Set<String> turned = new java.util.HashSet<>();
            for (Map<String, Object> bone : bones) {
                if (bone.get("parent") != null) parents.put((String) bone.get("name"), (String) bone.get("parent"));
                if (bone.get("rotation") != null) {
                    double[] r = numbers(bone.get("rotation"));
                    if (Math.abs(r[0]) + Math.abs(r[1]) + Math.abs(r[2]) > 1) turned.add((String) bone.get("name"));
                }
            }
            for (Map<String, Object> bone : bones) {
                String name = (String) bone.get("name");
                Part part = part(name, parents);
                boolean visible = visible(name, parents);
                // The side-view icon ignores rotations, so a turned bone (a spare magazine flipped on its side) is left out.
                for (String at = name; at != null && visible; at = parents.get(at)) if (turned.contains(at)) visible = false;
                List<Object> cubes = (List<Object>) bone.get("cubes");
                if (cubes == null) continue;
                for (Object raw : cubes) {
                    Map<String, Object> c = (Map<String, Object>) raw;
                    double[] origin = numbers(c.get("origin")), size = numbers(c.get("size"));
                    Cube cube = new Cube();
                    cube.x0 = origin[0]; cube.y0 = origin[1]; cube.z0 = origin[2];
                    cube.x1 = origin[0] + size[0]; cube.y1 = origin[1] + size[1]; cube.z1 = origin[2] + size[2];
                    cube.part = part;
                    cube.visible = visible && c.get("rotation") == null;
                    if (c.get("uv") instanceof Map<?, ?> uv)
                        for (Map.Entry<?, ?> face : uv.entrySet()) {
                            Map<String, Object> f = (Map<String, Object>) face.getValue();
                            double[] at = numbers(f.get("uv")), extent = numbers(f.get("uv_size"));
                            cube.faces.put((String) face.getKey(), new double[]{at[0], at[1], extent[0], extent[1]});
                        }
                    model.cubes.add(cube);
                }
            }
            return model;
        }

        private static double[] numbers(Object list) {
            List<?> values = (List<?>) list;
            double[] out = new double[values.size()];
            for (int i = 0; i < out.length; i++) out[i] = ((Number) values.get(i)).doubleValue();
            return out;
        }

        /** A bone's part from its own name, else its parent's. */
        private static Part part(String name, Map<String, String> parents) {
            for (String at = name; at != null; at = parents.get(at)) {
                String n = at.toLowerCase();
                if (n.contains("illuminated") || n.contains("glow")) return Part.GLOW;
                if (n.contains("release") || n.contains("trigger") || n.contains("safety") || n.contains("hammer") || n.contains("screw")
                        || n.contains("pin") || n.contains("button") || n.contains("switch") || n.contains("charge") || n.contains("bolt")) return Part.DETAIL;
                if (n.contains("mag") || n.contains("bullet")) return Part.MAG;
                if (n.contains("barrel") || n.contains("muzzle") || n.contains("octagon") || n.contains("hexadecagon") || n.contains("gastube")) return Part.BARREL;
                if (n.contains("grip") || n.contains("stock")) return Part.GRIP;
                if (n.contains("sight") || n.contains("rail") || n.contains("mount")) return Part.DETAIL;
            }
            return Part.BODY;
        }

        /** Spare magazines, loose rounds and hand markers aren't part of the gun at rest. */
        private static boolean visible(String name, Map<String, String> parents) {
            for (String at = name; at != null; at = parents.get(at)) {
                String n = at.toLowerCase();
                if (n.contains("extended") || n.contains("additional") || n.contains("bullet") || n.endsWith("_pos")
                        || n.contains("shell") || n.equals("lefthand") || n.equals("righthand") || n.equals("thirdperson_hand")) return false;
            }
            return true;
        }
    }

    /** Just enough JSON for Bedrock geometry files. */
    static final class Json {
        private final String s;
        private int i;

        Json(String s) { this.s = s; }

        Object value() {
            skip();
            char c = s.charAt(i);
            if (c == '{') {
                Map<String, Object> map = new LinkedHashMap<>();
                i++;
                skip();
                if (s.charAt(i) == '}') { i++; return map; }
                while (true) {
                    skip();
                    String key = string();
                    skip();
                    i++; // :
                    map.put(key, value());
                    skip();
                    if (s.charAt(i++) == '}') return map;
                }
            }
            if (c == '[') {
                List<Object> list = new ArrayList<>();
                i++;
                skip();
                if (s.charAt(i) == ']') { i++; return list; }
                while (true) {
                    list.add(value());
                    skip();
                    if (s.charAt(i++) == ']') return list;
                }
            }
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            return Double.parseDouble(s.substring(start, i));
        }

        private String string() {
            StringBuilder out = new StringBuilder();
            i++;
            while (s.charAt(i) != '"') {
                char c = s.charAt(i++);
                if (c == '\\') {
                    char e = s.charAt(i++);
                    if (e == 'u') { out.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                    else out.append(switch (e) { case 'n' -> '\n'; case 't' -> '\t'; default -> e; });
                } else out.append(c);
            }
            i++;
            return out.toString();
        }

        private void skip() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
    }
}
