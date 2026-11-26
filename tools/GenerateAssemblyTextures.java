import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Paints assembled matter: clear amber glass with a bright rim, a highlight streak and corner glints, so a Matter
 * Assembly construct reads as crystal folded out of thin air, not stone (Density's). Palette from AssemblyArt: deep
 * 7A4513, amber F2A848, honey FFC766, seam FFE3A0, core FFF1C8.
 *
 * <pre>java tools/GenerateAssemblyTextures.java</pre>
 */
public final class GenerateAssemblyTextures {
    private static final String ROOT = "src/main/resources/assets/heroesevolved/textures/";
    private static final int DEEP = 0x7A4513, AMBER = 0xF2A848, HONEY = 0xFFC766, SEAM = 0xFFE3A0, CORE = 0xFFF1C8;

    public static void main(String[] args) throws IOException {
        write(matter(), "block/assembled_matter.png");
    }

    /**
     * Amber glass: a bright rim with a darker band inside it, a clear, warm, barely tinted body that the world shows
     * through, a diagonal highlight streak like light on a pane, and a glint at each corner of the rim.
     */
    private static BufferedImage matter() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                boolean rim = x == 0 || y == 0 || x == 15 || y == 15;
                boolean band = !rim && (x == 1 || y == 1 || x == 14 || y == 14);
                boolean corner = (x <= 1 || x >= 14) && (y <= 1 || y >= 14);
                // A highlight streak from the upper left, two pixels wide, and a fainter one beside it.
                int d = x + y;
                boolean streak = !rim && !band && (d == 9 || d == 10), faint = !rim && !band && d == 13;
                double depth = (Math.abs(x - 7.5) + Math.abs(y - 7.5)) / 15.0;
                int color; int alpha;
                if (corner) { color = CORE; alpha = 255; }
                else if (rim) { color = SEAM; alpha = 230; }
                else if (band) { color = DEEP; alpha = 150; }
                else if (streak) { color = CORE; alpha = 120; }
                else if (faint) { color = SEAM; alpha = 80; }
                else { color = mix(AMBER, HONEY, depth); alpha = 52 + (int) (depth * 40); }
                image.setRGB(x, y, alpha << 24 | color);
            }
        return image;
    }

    private static int mix(int a, int b, double t) {
        int r = (int) ((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t);
        int g = (int) ((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    private static void write(BufferedImage image, String path) throws IOException {
        File file = new File(ROOT + path);
        file.getParentFile().mkdirs();
        ImageIO.write(image, "png", file);
    }
}
