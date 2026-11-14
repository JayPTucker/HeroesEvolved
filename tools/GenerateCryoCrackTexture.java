import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Paints Pod 14's glass as Claire left it: the pod's own glass, frosted white where the cold set on it, with four claw
 * marks dragged down through the frost from the inside and a star of cracks where she struck it. Read from
 * {@code cryochamber_glass.png}, written to {@code cryochamber_glass_cracked.png} (used by the pod's {@code cracked} state).
 *
 * <pre>java tools/GenerateCryoCrackTexture.java</pre>
 */
public final class GenerateCryoCrackTexture {
    private static final String DIR = "src/main/resources/assets/heroesevolved/textures/block/";
    private static final int FROST = 0xE8F4FA, CRACK = 0xFFFFFF, CLAW = 0x5E7F8E;

    public static void main(String[] args) throws IOException {
        BufferedImage glass = ImageIO.read(new File(DIR + "cryochamber_glass.png"));
        int w = glass.getWidth(), h = glass.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int argb = glass.getRGB(x, y);
                // Frost bloomed from the edges inward: thicker near the frame, thinner in the middle.
                double edge = Math.min(Math.min(x, w - 1 - x), Math.min(y, h - 1 - y)) / (w / 2.0);
                double frost = 0.55 - 0.35 * edge + 0.08 * Math.sin(x * 1.7 + y * 2.3);
                out.setRGB(x, y, mix(argb, FROST, frost, 0.55 + 0.3 * frost));
            }
        // Four claw marks, slightly slanted, dragged from high to low: the frost scraped away to clear, dark glass.
        for (int k = 0; k < 4; k++) {
            int x0 = 4 + 2 * k;
            for (int y = 2 + (k % 2); y <= 12 - (k == 3 ? 2 : 0); y++) {
                int x = x0 + (y - 2) / 5;
                if (x >= 0 && x < w) out.setRGB(x, y, argb(0x99, CLAW));
            }
        }
        // A star of cracks where a fist struck, high on the right.
        int cx = 12, cy = 4;
        int[][] rays = {{1, 0}, {1, -1}, {0, -1}, {-1, -1}, {-1, 1}, {0, 1}, {1, 1}};
        for (int[] r : rays)
            for (int s = 1; s <= 3; s++) {
                int x = cx + r[0] * s + (s == 3 ? r[1] : 0), y = cy + r[1] * s;
                if (x >= 0 && x < w && y >= 0 && y < h) out.setRGB(x, y, argb(0xE0, CRACK));
            }
        out.setRGB(cx, cy, argb(0xFF, CRACK));
        ImageIO.write(out, "png", new File(DIR + "cryochamber_glass_cracked.png"));
        System.out.println("wrote cryochamber_glass_cracked.png");
    }

    private static int argb(int alpha, int rgb) { return alpha << 24 | rgb & 0xFFFFFF; }

    /** Blends {@code rgb} into {@code argb} by {@code t}, leaving the result at least {@code minAlpha} opaque. */
    private static int mix(int argb, int rgb, double t, double minAlpha) {
        t = Math.max(0, Math.min(1, t));
        int a = argb >>> 24, r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        int r2 = rgb >> 16 & 255, g2 = rgb >> 8 & 255, b2 = rgb & 255;
        int na = (int) Math.max(a, 255 * minAlpha);
        return na << 24 | (int) (r + (r2 - r) * t) << 16 | (int) (g + (g2 - g) * t) << 8 | (int) (b + (b2 - b) * t);
    }
}
