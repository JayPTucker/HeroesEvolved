import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Paints the Jammed effect's icon (Electrokinesis's Dead Trigger): a pistol in side view with a jagged bolt of current
 * through its frame. The palette is ElectroArt's: shadow 10172D, indigo 3D52B8, blue 5588FF, ion 84D5FF, core EAF5FF,
 * with a steel grey for the gun.
 *
 * <pre>java tools/GenerateElectroTextures.java</pre>
 */
public final class GenerateElectroTextures {
    private static final String ROOT = "src/main/resources/assets/heroesevolved/textures/";
    private static final int SHADOW = 0x10172D, INDIGO = 0x3D52B8, BLUE = 0x5588FF, ION = 0x84D5FF, CORE = 0xEAF5FF;
    private static final int STEEL = 0x8A939C, DARK_STEEL = 0x4C545C;

    public static void main(String[] args) throws IOException {
        write(jammed(), "mob_effect/jammed.png");
    }

    /** A pistol facing right (slide, grip, trigger guard), outlined dark, with a zig-zag bolt across the slide. */
    private static BufferedImage jammed() {
        BufferedImage image = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
        boolean[][] gun = new boolean[18][18];
        for (int x = 3; x <= 15; x++) for (int y = 5; y <= 8; y++) gun[x][y] = true;          // slide and barrel
        for (int x = 4; x <= 7; x++) for (int y = 9; y <= 14; y++) gun[x - (y - 9) / 3][y] = true; // grip, raked back
        for (int x = 8; x <= 10; x++) gun[x][9] = true;                                       // trigger guard top
        gun[10][10] = true; gun[9][11] = true; gun[8][11] = true;                             // trigger guard
        for (int y = 0; y < 18; y++)
            for (int x = 0; x < 18; x++) {
                if (gun[x][y]) set(image, x, y, y <= 6 ? STEEL : DARK_STEEL);
                else if (touches(gun, x, y)) set(image, x, y, SHADOW);
            }
        // The bolt: a jagged line of current across the slide, dark-edged so it reads on any background.
        int[][] bolt = {{1, 9}, {4, 6}, {6, 8}, {9, 4}, {11, 7}, {13, 3}, {16, 5}};
        for (int i = 1; i < bolt.length; i++) line(image, bolt[i - 1], bolt[i]);
        return image;
    }

    private static void line(BufferedImage image, int[] a, int[] b) {
        int steps = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
        for (int i = 0; i <= steps; i++) {
            int x = Math.round(a[0] + (b[0] - a[0]) * i / (float) steps), y = Math.round(a[1] + (b[1] - a[1]) * i / (float) steps);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                int px = x + dx, py = y + dy;
                if (px < 0 || py < 0 || px >= 18 || py >= 18) continue;
                int current = image.getRGB(px, py) & 0xFFFFFF, alpha = image.getRGB(px, py) >>> 24;
                if (alpha == 0 || current == SHADOW || current == DARK_STEEL) set(image, px, py, dx == 0 && dy == 0 ? CORE : Math.abs(dx) + Math.abs(dy) == 1 ? ION : INDIGO);
            }
            set(image, x, y, CORE);
        }
        set(image, a[0], a[1], BLUE);
    }

    private static boolean touches(boolean[][] mask, int x, int y) {
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
            int px = x + dx, py = y + dy;
            if (px >= 0 && py >= 0 && px < 18 && py < 18 && mask[px][py]) return true;
        }
        return false;
    }

    private static void set(BufferedImage image, int x, int y, int rgb) { image.setRGB(x, y, 0xFF000000 | rgb); }

    private static void write(BufferedImage image, String path) throws IOException {
        File file = new File(ROOT + path);
        file.getParentFile().mkdirs();
        ImageIO.write(image, "png", file);
    }
}
