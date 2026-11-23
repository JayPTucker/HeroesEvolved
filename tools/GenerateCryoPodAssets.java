import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Everything a stasis pod's {@code condition} draws: the frosted glass of a pod that still holds someone, the frost on the
 * body inside it, the two wrecked pods (glass blown out, or the door torn off its hinge) and the block state file that
 * picks between them. Change this and run it; never edit what it writes.
 *
 * <pre>java tools/GenerateCryoPodAssets.java</pre>
 */
public final class GenerateCryoPodAssets {
    private static final String ASSETS = "src/main/resources/assets/heroesevolved/";
    private static final String BLOCKS = ASSETS + "textures/block/", MODELS = ASSETS + "models/block/";

    public static void main(String[] args) throws IOException {
        frostedGlass();
        brokenGlass();
        rusted("cryochamber_frame", 11);
        rusted("cryochamber_base", 23);
        bodyFrost();
        for (String half : List.of("lower", "upper"))
            for (String door : List.of("open", "closed"))
                write(MODELS + "cryochamber_" + half + "_" + door + "_frosted.json", "{\n  \"parent\": \"heroesevolved:block/cryochamber_"
                        + half + "_" + door + "\",\n  \"textures\": {\n    \"glass\": \"heroesevolved:block/cryochamber_glass_frosted\"\n  }\n}\n");
        write(MODELS + "cryochamber_lower_shattered.json", model(shatteredLower()));
        write(MODELS + "cryochamber_upper_shattered.json", model(shatteredUpper()));
        write(MODELS + "cryochamber_lower_sprung.json", model(sprungLower()));
        write(MODELS + "cryochamber_upper_sprung.json", model(sprungUpper()));
        write(ASSETS + "blockstates/cryochamber.json", blockstate());
        System.out.println("PASS: wrote the pod's frost, wrecks and block states");
    }

    // ------------------------------------------------------------------
    // Textures
    // ------------------------------------------------------------------

    /** Frost grown in from the frame, thick at the edges and thin in the middle, so the face behind it can still be made out. */
    private static void frostedGlass() throws IOException {
        BufferedImage glass = ImageIO.read(new File(BLOCKS + "cryochamber_glass.png"));
        int w = glass.getWidth(), h = glass.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double edge = Math.min(Math.min(x, w - 1 - x), Math.min(y, h - 1 - y)) / (Math.min(w, h) / 2.0);
                double grain = fractal(x * 16.0 / w, y * 16.0 / h, 41);
                double frost = clamp(0.80 - 1.9 * edge + 0.6 * (grain - 0.5));
                int rgb = lerp(0xBFE3F2, 0xF4FBFF, frost);
                // Clear enough in the middle to see who is in there: two panes of this are always in the way.
                out.setRGB(x, y, (int) (255 * (0.10 + 0.72 * frost)) << 24 | rgb);
            }
        ImageIO.write(out, "png", new File(BLOCKS + "cryochamber_glass_frosted.png"));
    }

    /** What is left of the glass: filmed with a century's dirt, and crazed with cracks. */
    private static void brokenGlass() throws IOException {
        BufferedImage glass = ImageIO.read(new File(BLOCKS + "cryochamber_glass.png"));
        int w = glass.getWidth(), h = glass.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double dirt = fractal(x * 16.0 / w, y * 16.0 / h, 57);
                // Pale enough that the teeth left in the frame stand out against the dark pod behind them.
                int rgb = lerp(0xB4D6DA, 0x7F9A94, clamp(dirt * 1.3 - 0.15));
                out.setRGB(x, y, (int) (255 * (0.58 + 0.2 * dirt)) << 24 | rgb);
            }
        Random random = new Random(7);
        for (int crack = 0; crack < 3; crack++) {
            double x = random.nextInt(w), y = random.nextInt(h), angle = random.nextDouble() * Math.PI * 2;
            for (int step = 0; step < Math.max(w, h); step++) {
                int px = (int) x, py = (int) y;
                if (px < 0 || py < 0 || px >= w || py >= h) break;
                out.setRGB(px, py, 0xD8EAF6F6);
                angle += (random.nextDouble() - 0.5) * 0.9;
                x += Math.cos(angle);
                y += Math.sin(angle);
            }
        }
        ImageIO.write(out, "png", new File(BLOCKS + "cryochamber_glass_broken.png"));
    }

    /** The pod's own metal, dulled, streaked with rust running down it and scorched in patches. */
    private static void rusted(String name, int seed) throws IOException {
        BufferedImage metal = ImageIO.read(new File(BLOCKS + name + ".png"));
        int w = metal.getWidth(), h = metal.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int argb = metal.getRGB(x, y);
                double rust = clamp((fractal(x * 16.0 / w, y * 5.0 / h, seed) - 0.42) * 3.2);
                double scorch = clamp((fractal(x * 16.0 / w + 40, y * 16.0 / h, seed + 1) - 0.58) * 4);
                int rgb = lerp(argb & 0xFFFFFF, 0x3F4144, 0.45);
                rgb = lerp(rgb, 0x7A4526, rust * 0.8);
                rgb = lerp(rgb, 0x17181A, scorch * 0.75);
                out.setRGB(x, y, argb & 0xFF000000 | rgb);
            }
        ImageIO.write(out, "png", new File(BLOCKS + name + "_broken.png"));
    }

    /** Laid over a villager's own skin: a cold film everywhere and thick rime in patches, with bright crystals in it. */
    private static void bodyFrost() throws IOException {
        int size = 64;
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(19);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                // A thin blue cold over the skin, so the face and robe still read, and white rime only in patches.
                double rime = clamp((fractal(x / 1.7, y / 1.7, 73) - 0.5) * 5);
                int rgb = lerp(0x9FD4EE, 0xF4FBFF, rime);
                int alpha = (int) (58 + 165 * rime);
                if (random.nextInt(31) == 0) { rgb = 0xFFFFFF; alpha = 225; }
                out.setRGB(x, y, alpha << 24 | rgb);
            }
        File file = new File(ASSETS + "textures/entity/pod_frost.png");
        file.getParentFile().mkdirs();
        ImageIO.write(out, "png", file);
    }

    private static double fractal(double x, double y, int seed) {
        double sum = 0, weight = 0.55, total = 0, scale = 0.28;
        for (int octave = 0; octave < 3; octave++) {
            sum += weight * value(x * scale, y * scale, seed + octave);
            total += weight;
            weight *= 0.55;
            scale *= 2.1;
        }
        return sum / total;
    }

    private static double value(double x, double y, int seed) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double top = along(hash(x0, y0, seed), hash(x0 + 1, y0, seed), fx);
        double bottom = along(hash(x0, y0 + 1, seed), hash(x0 + 1, y0 + 1, seed), fx);
        return along(top, bottom, fy);
    }

    private static double hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1274126177;
        h = (h ^ h >>> 13) * 1274126177;
        return ((h ^ h >>> 16) & 0xFFFF) / 65535.0;
    }

    private static double along(double a, double b, double t) { return a + (b - a) * t; }
    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }

    private static int lerp(int a, int b, double t) {
        int r = (int) along(a >> 16 & 255, b >> 16 & 255, t), g = (int) along(a >> 8 & 255, b >> 8 & 255, t),
                c = (int) along(a & 255, b & 255, t);
        return r << 16 | g << 8 | c;
    }

    // ------------------------------------------------------------------
    // Models: drawn facing north, the door on the z = 0 side, as the pod's other models are
    // ------------------------------------------------------------------

    /** Glass blown out: teeth left standing along the sill, one side panel half gone, chips on the floor plate. */
    private static List<String> shatteredLower() {
        List<String> e = shell(true);
        e.add(box(0, 2, 0, 1, 16, 14, "glass"));
        e.add(box(15, 2, 0, 16, 9, 6, "glass"));
        e.add(box(15, 2, 6, 16, 14, 14, "glass"));
        int[][] teeth = {{1, 4, 8}, {4, 6, 5}, {6, 9, 3}, {9, 11, 6}, {11, 13, 4}, {13, 15, 10}};
        for (int[] t : teeth) e.add(box(t[0], 2, 0, t[1], t[2], 1, "glass"));
        chips(e);
        return e;
    }

    /** The same pod's top: shards hanging from the lintel, and the side panels broken back. */
    private static List<String> shatteredUpper() {
        List<String> e = shell(false);
        e.add(box(0, 0, 4, 1, 14, 14, "glass"));
        e.add(box(0, 8, 0, 1, 14, 4, "glass"));
        e.add(box(15, 0, 8, 16, 14, 14, "glass"));
        e.add(box(15, 10, 0, 16, 14, 8, "glass"));
        int[][] teeth = {{1, 3, 10}, {3, 6, 7}, {6, 8, 11}, {8, 10, 9}, {10, 13, 12}, {13, 15, 6}};
        for (int[] t : teeth) e.add(box(t[0], t[2], 0, t[1], 14, 1, "glass"));
        return e;
    }

    /** The door torn off its top hinge, hanging out into the aisle from the one that held. */
    private static List<String> sprungLower() {
        List<String> e = shell(true);
        e.add(box(0, 2, 0, 1, 16, 14, "glass"));
        e.add(box(15, 2, 0, 16, 16, 14, "glass"));
        e.add(hanging(1, 2, 15, 16));
        chips(e);
        return e;
    }

    private static List<String> sprungUpper() {
        List<String> e = shell(false);
        e.add(box(0, 0, 0, 1, 14, 14, "glass"));
        e.add(box(15, 0, 3, 16, 14, 14, "glass"));
        e.add(hanging(1, 0, 11, 8));
        e.add(hanging(1, 8, 7, 12));
        return e;
    }

    /** The metal that survived: floor plate and back on the lower half, lid and back on the upper. */
    private static List<String> shell(boolean lower) {
        List<String> e = new ArrayList<>();
        if (lower) {
            e.add(box(0, 0, 0, 16, 2, 16, "base"));
            e.add(box(0, 2, 14, 16, 16, 16, "frame"));
        } else {
            e.add(box(0, 14, 0, 16, 16, 16, "frame"));
            e.add(box(0, 0, 14, 16, 14, 16, "frame"));
        }
        return e;
    }

    private static void chips(List<String> e) {
        int[][] chips = {{3, 4, 5, 6}, {8, 2, 10, 3}, {11, 7, 12, 9}, {5, 10, 7, 11}};
        for (int[] c : chips) e.add(box(c[0], 2, c[1], c[2], 2.5, c[3], "glass"));
    }

    /** A piece of the door, swung 45 degrees out of the pod about its hinge at the door's west end. */
    private static String hanging(double x1, double y1, double x2, double y2) {
        return element(x1, y1, 0, x2, y2, 1, "glass", ",\n      \"rotation\": { \"origin\": [1, 8, 0], \"axis\": \"y\", \"angle\": 45 }");
    }

    private static String box(double x1, double y1, double z1, double x2, double y2, double z2, String texture) {
        return element(x1, y1, z1, x2, y2, z2, texture, "");
    }

    private static String element(double x1, double y1, double z1, double x2, double y2, double z2, String texture, String extra) {
        StringBuilder faces = new StringBuilder();
        for (String face : List.of("north", "south", "east", "west", "up", "down")) {
            if (faces.length() > 0) faces.append(",\n");
            faces.append("        \"").append(face).append("\": { \"texture\": \"#").append(texture).append("\" }");
        }
        return "    {\n      \"from\": [" + n(x1) + ", " + n(y1) + ", " + n(z1) + "],\n      \"to\": [" + n(x2) + ", " + n(y2) + ", " + n(z2) + "]"
                + extra + ",\n      \"faces\": {\n" + faces + "\n      }\n    }";
    }

    private static String n(double v) { return v == Math.rint(v) ? Integer.toString((int) v) : Double.toString(v); }

    private static String model(List<String> elements) {
        return "{\n  \"parent\": \"minecraft:block/block\",\n  \"render_type\": \"minecraft:translucent\",\n  \"textures\": {\n"
                + "    \"frame\": \"heroesevolved:block/cryochamber_frame_broken\",\n"
                + "    \"glass\": \"heroesevolved:block/cryochamber_glass_broken\",\n"
                + "    \"base\": \"heroesevolved:block/cryochamber_base_broken\",\n"
                + "    \"particle\": \"heroesevolved:block/cryochamber_frame_broken\"\n  },\n  \"elements\": [\n"
                + String.join(",\n", elements) + "\n  ]\n}\n";
    }

    // ------------------------------------------------------------------
    // Block states: a key leaves out what doesn't change the drawing (charged, and cracked on all but a sound pod)
    // ------------------------------------------------------------------

    private static String blockstate() {
        List<String> variants = new ArrayList<>();
        String[] facings = {"north", "east", "south", "west"};
        for (int f = 0; f < 4; f++) {
            String facing = "facing=" + facings[f];
            int y = f * 90;
            for (String half : List.of("lower", "upper")) {
                for (String door : List.of("open", "closed")) {
                    String key = facing + ",half=" + half + ",open=" + door.equals("open");
                    String model = "cryochamber_" + half + "_" + door;
                    variants.add(variant("condition=sound,cracked=false," + key, model, y));
                    variants.add(variant("condition=sound,cracked=true," + key, model + "_cracked", y));
                    variants.add(variant("condition=frosted," + key, model + "_frosted", y));
                }
                for (String wreck : List.of("shattered", "sprung"))
                    variants.add(variant("condition=" + wreck + "," + facing + ",half=" + half, "cryochamber_" + half + "_" + wreck, y));
            }
        }
        return "{\n  \"variants\": {\n" + String.join(",\n", variants) + "\n  }\n}\n";
    }

    private static String variant(String key, String model, int y) {
        return "    \"" + key + "\": { \"model\": \"heroesevolved:block/" + model + "\"" + (y == 0 ? "" : ", \"y\": " + y) + " }";
    }

    private static void write(String path, String text) throws IOException {
        Files.writeString(new File(path).toPath(), text, StandardCharsets.UTF_8);
    }
}
