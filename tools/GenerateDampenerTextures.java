import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Paints the Ability Dampener's block faces (a slate pylon with vent slots and an emitter dish that glows pale mint
 * when it runs) and the Dampened effect's icon (a broken ring crossed out: the null sign). The palette is DampenerArt's:
 * shadow 1E242B, slate 4A5866, steel 8796A3, hum BFE3DC, core F0F6F4.
 *
 * <pre>java tools/GenerateDampenerTextures.java</pre>
 */
public final class GenerateDampenerTextures {
    private static final String ROOT = "src/main/resources/assets/heroesevolved/textures/";
    private static final int SHADOW = 0x1E242B, DARK = 0x2C333B, SLATE = 0x4A5866, STEEL = 0x8796A3, DIM = 0x5F6C78;
    private static final int HUM = 0xBFE3DC, CORE = 0xF0F6F4;

    public static void main(String[] args) throws IOException {
        write(side(false), "block/ability_dampener_side.png");
        write(side(true), "block/ability_dampener_side_on.png");
        write(top(false), "block/ability_dampener_top.png");
        write(top(true), "block/ability_dampener_top_on.png");
        write(bottom(), "block/ability_dampener_bottom.png");
        write(icon(), "mob_effect/dampened.png");
    }

    /** A framed slate panel: vent slots either side of a channel that carries the hum up to the dish. */
    private static BufferedImage side(boolean on) {
        BufferedImage image = plate(16);
        for (int y = 3; y <= 12; y++) {
            for (int x : new int[] {3, 4, 11, 12}) if (y % 2 == 1) set(image, x, y, DARK);
            // The channel: two pixels wide, dark when idle, lit (brightest in the middle) when running.
            for (int x = 7; x <= 8; x++) set(image, x, y, on ? (y >= 6 && y <= 9 ? CORE : HUM) : DIM);
            set(image, 6, y, SHADOW);
            set(image, 9, y, SHADOW);
        }
        // A band near the top where the dish sits, and one near the foot.
        for (int x = 1; x < 15; x++) { set(image, x, 1, STEEL); set(image, x, 14, DARK); }
        return image;
    }

    /** The emitter dish seen from above: three rings closing on a core. */
    private static BufferedImage top(boolean on) {
        BufferedImage image = plate(16);
        double c = 7.5;
        for (int y = 1; y < 15; y++)
            for (int x = 1; x < 15; x++) {
                double r = Math.hypot(x - c, y - c);
                if (r > 6.2) continue;
                int color = SLATE;
                if (Math.abs(r - 5.6) < 0.5) color = STEEL;
                else if (Math.abs(r - 3.7) < 0.45) color = on ? HUM : DIM;
                else if (r < 1.8) color = on ? CORE : DARK;
                else if (r < 2.6) color = on ? HUM : SHADOW;
                else color = shade(SLATE, 0.85 + 0.1 * Math.cos(Math.atan2(y - c, x - c) * 2));
                set(image, x, y, color);
            }
        return image;
    }

    private static BufferedImage bottom() {
        BufferedImage image = plate(16);
        for (int y = 4; y <= 11; y++) for (int x = 4; x <= 11; x++) if ((x + y) % 3 == 0) set(image, x, y, DARK);
        return image;
    }

    /** The null sign, 18 pixels: a broken ring with a slash across it, outlined so it reads on any background. */
    private static BufferedImage icon() {
        BufferedImage image = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
        double c = 8.5;
        for (int y = 0; y < 18; y++)
            for (int x = 0; x < 18; x++) {
                double r = Math.hypot(x - c, y - c), angle = Math.toDegrees(Math.atan2(y - c, x - c));
                double slash = Math.abs((x - c) + (y - c)) / Math.sqrt(2);
                boolean gap = Math.abs(((angle + 360) % 120) - 60) < 10;
                boolean ring = Math.abs(r - 6) < 1.1 && !gap, line = slash < 1.0 && r < 8;
                boolean edge = Math.abs(r - 6) < 2 && !gap || slash < 2 && r < 8.6;
                if (ring || line) image.setRGB(x, y, 0xFF000000 | (line ? CORE : HUM));
                else if (edge) image.setRGB(x, y, 0xFF000000 | SHADOW);
            }
        return image;
    }

    /** A framed steel plate with a rivet in each corner. */
    private static BufferedImage plate(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                boolean frame = x == 0 || y == 0 || x == size - 1 || y == size - 1;
                double noise = 1 + (hash(x, y) - 0.5) * 0.08;
                set(image, x, y, frame ? SHADOW : shade(SLATE, noise));
            }
        for (int[] p : new int[][] {{1, 1}, {14, 1}, {1, 14}, {14, 14}}) set(image, p[0], p[1], STEEL);
        return image;
    }

    private static void set(BufferedImage image, int x, int y, int rgb) { image.setRGB(x, y, 0xFF000000 | rgb); }

    private static int shade(int rgb, double k) {
        int r = (int) Math.min(255, (rgb >> 16 & 255) * k), g = (int) Math.min(255, (rgb >> 8 & 255) * k), b = (int) Math.min(255, (rgb & 255) * k);
        return r << 16 | g << 8 | b;
    }

    private static double hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
    }

    private static void write(BufferedImage image, String path) throws IOException {
        File file = new File(ROOT + path);
        file.getParentFile().mkdirs();
        ImageIO.write(image, "png", file);
        System.out.println("Wrote " + file + " (" + image.getWidth() + "x" + image.getHeight() + ")");
    }
}
