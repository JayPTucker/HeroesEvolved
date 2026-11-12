import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * Draws Elle Bishop's original skin (slim arms: platinum hair worn long, a pale blue top, dark trousers, white shoes,
 * a little current at her fingertips) and the 16x16 icon for Elle's Capacitor (a cracked Primatech cell with copper caps
 * and blue light behind its window). Pixel placement follows the standard 64x64 slim player layout. No downloaded assets.
 *
 * <pre>java tools/GenerateElleTextures.java</pre>
 */
public final class GenerateElleTextures {
    private static final String ROOT = "src/main/resources/assets/heroesevolved/textures/";

    public static void main(String[] args) throws IOException {
        write(elle(), ROOT + "entity/elle_bishop.png");
        write(capacitor(), ROOT + "item/elles_capacitor.png");
    }

    private static void write(BufferedImage image, String path) throws IOException {
        File file = new File(path);
        file.getParentFile().mkdirs();
        ImageIO.write(image, "png", file);
        System.out.println("Wrote " + path + " (" + image.getWidth() + "x" + image.getHeight() + ")");
    }

    private static int rgb(int r, int g, int b) { return 0xFF000000 | r << 16 | g << 8 | b; }

    private static void fill(BufferedImage image, int x0, int y0, int x1, int y1, int color) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) image.setRGB(x, y, color);
    }

    /** Vanilla cuboid unwrap: top, bottom, then right/front/left/back sides in one strip. */
    private static void box(BufferedImage image, int x, int y, int width, int depth, int height, int color) {
        fill(image, x + depth, y, x + depth + width - 1, y + depth - 1, color);
        fill(image, x + depth + width, y, x + depth + 2 * width - 1, y + depth - 1, color);
        fill(image, x, y + depth, x + 2 * (depth + width) - 1, y + depth + height - 1, color);
    }

    private static void weave(BufferedImage image, Random random, int x0, int y0, int x1, int y1, int spread) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
            int c = image.getRGB(x, y);
            if ((c >>> 24) == 0) continue;
            int shift = random.nextInt(spread * 2 + 1) - spread;
            image.setRGB(x, y, rgb(clamp(((c >> 16) & 0xFF) + shift), clamp(((c >> 8) & 0xFF) + shift), clamp((c & 0xFF) + shift)));
        }
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static BufferedImage elle() {
        Random random = new Random(2007);
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        int skin = rgb(238, 205, 184), top = rgb(150, 196, 232), topDark = rgb(110, 160, 206), trousers = rgb(44, 48, 62),
                shoe = rgb(232, 232, 236), hair = rgb(238, 220, 160), hairShade = rgb(212, 188, 124), hairDeep = rgb(186, 160, 100);
        box(image, 0, 0, 8, 8, 8, skin);          // head
        box(image, 16, 16, 8, 4, 12, top);        // body
        box(image, 40, 16, 3, 4, 12, top);        // right arm (slim)
        box(image, 32, 48, 3, 4, 12, top);        // left arm (slim)
        box(image, 0, 16, 4, 4, 12, trousers);    // right leg
        box(image, 16, 48, 4, 4, 12, trousers);   // left leg
        // Hair: the crown, the back and sides long; bangs swept across the brow.
        fill(image, 8, 0, 15, 7, hair);
        fill(image, 0, 8, 7, 15, hair); fill(image, 16, 8, 23, 15, hair); fill(image, 24, 8, 31, 15, hair);
        fill(image, 8, 8, 15, 9, hair); image.setRGB(8, 10, hair); image.setRGB(15, 10, hair); image.setRGB(15, 11, hair);
        fill(image, 1, 9, 6, 15, hairShade); fill(image, 17, 9, 22, 15, hairShade);
        for (int x = 24; x <= 31; x += 2) fill(image, x, 10, x, 15, hairShade);
        fill(image, 9, 9, 11, 9, hairShade);
        // Face: blue eyes under fair brows, a small nose, a smile that knows something.
        fill(image, 9, 11, 10, 11, hairDeep); fill(image, 13, 11, 14, 11, hairDeep);
        image.setRGB(9, 12, rgb(250, 250, 250)); image.setRGB(10, 12, rgb(60, 130, 210));
        image.setRGB(13, 12, rgb(60, 130, 210)); image.setRGB(14, 12, rgb(250, 250, 250));
        image.setRGB(11, 13, rgb(222, 184, 164)); image.setRGB(12, 13, rgb(222, 184, 164));
        fill(image, 10, 14, 13, 14, rgb(196, 112, 118)); image.setRGB(13, 14, rgb(176, 92, 100)); image.setRGB(10, 15, skin);
        // The hair falls down her back and over the tops of her shoulders.
        fill(image, 32, 20, 39, 27, hair);
        for (int x = 32; x <= 39; x += 2) fill(image, x, 22, x, 27, hairShade);
        fill(image, 33, 28, 38, 28, hairShade);
        fill(image, 20, 20, 20, 23, hair); fill(image, 27, 20, 27, 23, hair);
        fill(image, 16, 20, 19, 25, hair); fill(image, 28, 20, 31, 25, hair);
        // The top: a scoop neck, a darker hem and seams; bare forearms and hands with current at the fingertips.
        fill(image, 22, 20, 25, 21, skin); image.setRGB(23, 22, skin); image.setRGB(24, 22, skin);
        fill(image, 20, 30, 27, 31, topDark); fill(image, 32, 30, 39, 31, topDark);
        for (int[] arm : new int[][] {{40, 16}, {32, 48}}) {
            fill(image, arm[0], arm[1] + 4 + 7, arm[0] + 13, arm[1] + 4 + 11, skin);
            fill(image, arm[0], arm[1] + 4 + 6, arm[0] + 13, arm[1] + 4 + 6, topDark);
            fill(image, arm[0] + 7, arm[1], arm[0] + 9, arm[1] + 3, skin);
            for (int x = arm[0]; x <= arm[0] + 13; x += 3) image.setRGB(x, arm[1] + 15, rgb(160, 225, 255));
        }
        // Trousers with a belt; white shoes.
        fill(image, 4, 20, 11, 20, rgb(30, 32, 40)); fill(image, 20, 52, 27, 52, rgb(30, 32, 40));
        fill(image, 20, 31, 27, 31, rgb(60, 60, 70));
        for (int[] leg : new int[][] {{0, 16}, {16, 48}}) {
            fill(image, leg[0], leg[1] + 14, leg[0] + 15, leg[1] + 15, shoe);
            fill(image, leg[0] + 8, leg[1], leg[0] + 11, leg[1] + 3, shoe);
        }
        weave(image, random, 16, 20, 39, 31, 3);
        weave(image, random, 40, 20, 53, 25, 3);
        weave(image, random, 32, 52, 45, 57, 3);
        weave(image, random, 0, 20, 15, 31, 2);
        weave(image, random, 16, 52, 31, 63, 2);
        weave(image, random, 0, 0, 31, 9, 4);
        return image;
    }

    /** A cracked Primatech cell: steel casing, copper caps, blue light behind a split window. */
    private static BufferedImage capacitor() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        int steel = rgb(70, 76, 86), steelLight = rgb(118, 126, 138), steelDark = rgb(40, 44, 52);
        int copper = rgb(224, 137, 74), copperDark = rgb(150, 80, 40), glow = rgb(127, 212, 255), core = rgb(242, 251, 255);
        // The body: a stubby upright cylinder, lit from the left.
        fill(image, 5, 3, 10, 12, steel);
        fill(image, 5, 3, 5, 12, steelLight); fill(image, 10, 3, 10, 12, steelDark);
        // Copper caps top and bottom, with terminals.
        fill(image, 4, 2, 11, 3, copper); fill(image, 4, 12, 11, 13, copper);
        fill(image, 4, 3, 11, 3, copperDark); fill(image, 4, 13, 11, 13, copperDark);
        image.setRGB(7, 1, copper); image.setRGB(8, 1, copper); image.setRGB(7, 0, steelLight);
        image.setRGB(7, 14, copperDark); image.setRGB(8, 14, copperDark);
        // The window and the charge behind it.
        fill(image, 6, 5, 9, 10, glow); fill(image, 7, 6, 8, 9, core);
        // The crack: dark across the glass.
        image.setRGB(6, 6, steelDark); image.setRGB(7, 7, steelDark); image.setRGB(8, 8, steelDark); image.setRGB(8, 9, steelDark);
        // A spark leaping off the top terminal.
        image.setRGB(9, 0, glow); image.setRGB(10, 1, core); image.setRGB(11, 0, glow);
        image.setRGB(12, 4, 0x8884D5FF); image.setRGB(3, 9, 0x8884D5FF);
        return image;
    }
}
