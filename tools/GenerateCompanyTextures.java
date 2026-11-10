import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * Draws the Company's original textures: a guard's skin (black suit, white shirt, dark tie, sunglasses,
 * earpiece), the Suppressor's (grey suit, open collar, shaved head), and the 18x18 Tranquilized effect icon
 * (a pale dart). Pixel placement follows the standard 64x64 player UV layout, as
 * tools/generate-sylar-skin.py does. No downloaded assets.
 *
 * <pre>java tools/GenerateCompanyTextures.java</pre>
 */
public final class GenerateCompanyTextures {
    private static final String ROOT = "src/main/resources/assets/heroesevolved/textures/";

    public static void main(String[] args) throws IOException {
        write(guard(), ROOT + "entity/company_agent.png");
        write(suppressor(), ROOT + "entity/suppressor.png");
        write(dartIcon(), ROOT + "mob_effect/tranquilized.png");
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

    /** A little cloth noise inside a rectangle, so large flat areas don't read as plastic. */
    private static void weave(BufferedImage image, Random random, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
            int c = image.getRGB(x, y);
            if ((c >>> 24) == 0) continue;
            int shift = random.nextInt(5) - 2;
            int r = clamp(((c >> 16) & 0xFF) + shift), g = clamp(((c >> 8) & 0xFF) + shift), b = clamp((c & 0xFF) + shift);
            image.setRGB(x, y, rgb(r, g, b));
        }
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static BufferedImage person(int suit, int trousers, int skin, int shoes) {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        box(image, 0, 0, 8, 8, 8, skin);         // head
        box(image, 16, 16, 8, 4, 12, suit);      // body
        box(image, 40, 16, 4, 4, 12, suit);      // right arm
        box(image, 32, 48, 4, 4, 12, suit);      // left arm
        box(image, 0, 16, 4, 4, 12, trousers);   // right leg
        box(image, 16, 48, 4, 4, 12, trousers);  // left leg
        // Hands below white cuffs; shoes.
        for (int[] arm : new int[][] {{40, 16}, {32, 48}}) {
            fill(image, arm[0], arm[1] + 13, arm[0] + 15, arm[1] + 15, skin);
            fill(image, arm[0], arm[1] + 12, arm[0] + 15, arm[1] + 12, rgb(226, 226, 222));
            fill(image, arm[0] + 8, arm[1], arm[0] + 11, arm[1] + 3, skin);
        }
        for (int[] leg : new int[][] {{0, 16}, {16, 48}}) {
            fill(image, leg[0], leg[1] + 14, leg[0] + 15, leg[1] + 15, shoes);
            fill(image, leg[0] + 8, leg[1], leg[0] + 11, leg[1] + 3, shoes);
        }
        return image;
    }

    private static BufferedImage guard() {
        Random random = new Random(614);
        int suit = rgb(24, 25, 29), shirt = rgb(232, 232, 228), skin = rgb(186, 146, 118), hair = rgb(38, 30, 26);
        BufferedImage image = person(suit, rgb(22, 23, 27), skin, rgb(12, 12, 14));
        // Short hair: crown, sides and back; the face stays clear.
        fill(image, 8, 0, 15, 7, hair);
        fill(image, 8, 8, 15, 9, hair);
        fill(image, 0, 8, 7, 10, hair); fill(image, 16, 8, 23, 10, hair); fill(image, 24, 8, 31, 14, hair);
        // Sunglasses across the eyes, with a bridge; nose and a flat mouth.
        int lens = rgb(14, 15, 18), frame = rgb(46, 48, 54);
        fill(image, 9, 12, 10, 12, lens); fill(image, 13, 12, 14, 12, lens); fill(image, 11, 12, 12, 12, frame);
        fill(image, 8, 12, 8, 12, frame); fill(image, 15, 12, 15, 12, frame);
        image.setRGB(11, 13, rgb(160, 120, 96)); image.setRGB(12, 13, rgb(160, 120, 96));
        fill(image, 10, 15, 13, 15, rgb(128, 92, 76));
        // Earpiece: a pale coil on his right side, down into the collar.
        image.setRGB(5, 12, rgb(196, 200, 206)); image.setRGB(5, 13, rgb(196, 200, 206)); image.setRGB(6, 14, rgb(170, 174, 180));
        // White shirt in the jacket's V, a narrow dark tie, lapels and buttons.
        fill(image, 22, 20, 25, 21, shirt); fill(image, 23, 22, 24, 24, shirt);
        fill(image, 23, 20, 24, 26, rgb(34, 36, 44)); image.setRGB(23, 20, rgb(48, 50, 60)); image.setRGB(24, 20, rgb(48, 50, 60));
        image.setRGB(21, 21, rgb(40, 41, 46)); image.setRGB(26, 21, rgb(40, 41, 46));
        image.setRGB(23, 28, rgb(70, 70, 74)); image.setRGB(24, 30, rgb(70, 70, 74));
        fill(image, 20, 31, 27, 31, rgb(16, 16, 18));
        weave(image, random, 16, 20, 39, 31);
        weave(image, random, 40, 20, 55, 27);
        weave(image, random, 32, 52, 47, 59);
        return image;
    }

    private static BufferedImage suppressor() {
        Random random = new Random(1414);
        int suit = rgb(92, 94, 100), shirt = rgb(236, 236, 232), skin = rgb(128, 90, 66), scalp = rgb(116, 80, 58);
        BufferedImage image = person(suit, rgb(80, 82, 88), skin, rgb(30, 26, 24));
        // Shaved head: the crown a shade darker than the face; ears.
        fill(image, 8, 0, 15, 7, scalp);
        fill(image, 8, 8, 15, 8, scalp);
        fill(image, 24, 8, 31, 9, scalp);
        image.setRGB(2, 12, rgb(112, 78, 56)); image.setRGB(21, 12, rgb(112, 78, 56));
        // Calm eyes, a heavy brow, nose and mouth.
        fill(image, 9, 11, 10, 11, rgb(70, 48, 36)); fill(image, 13, 11, 14, 11, rgb(70, 48, 36));
        image.setRGB(9, 12, rgb(232, 228, 220)); image.setRGB(10, 12, rgb(30, 22, 18));
        image.setRGB(13, 12, rgb(30, 22, 18)); image.setRGB(14, 12, rgb(232, 228, 220));
        image.setRGB(11, 13, rgb(108, 74, 54)); image.setRGB(12, 13, rgb(108, 74, 54));
        fill(image, 10, 15, 13, 15, rgb(92, 60, 46));
        // Open collar, no tie; a darker grey pocket square.
        fill(image, 22, 20, 25, 22, shirt); fill(image, 23, 23, 24, 25, shirt);
        image.setRGB(23, 21, skin); image.setRGB(24, 21, skin);
        image.setRGB(21, 21, rgb(76, 78, 84)); image.setRGB(26, 21, rgb(76, 78, 84));
        fill(image, 20, 23, 21, 23, rgb(60, 62, 70));
        image.setRGB(23, 27, rgb(62, 62, 66)); image.setRGB(24, 29, rgb(62, 62, 66));
        fill(image, 20, 31, 27, 31, rgb(40, 40, 44));
        weave(image, random, 16, 20, 39, 31);
        weave(image, random, 40, 20, 55, 27);
        weave(image, random, 32, 52, 47, 59);
        return image;
    }

    /** A pale tranquilizer dart, flying up and to the right, with a soft blue halo. */
    private static BufferedImage dartIcon() {
        BufferedImage image = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
        int halo = 0x553FA0D8, shaft = rgb(214, 226, 236), barrel = rgb(140, 200, 236), tip = rgb(240, 244, 248), fletch = rgb(60, 150, 210);
        for (int i = 0; i < 12; i++) {
            int x = 3 + i, y = 14 - i;
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                int hx = x + dx, hy = y + dy;
                if (hx >= 0 && hx < 18 && hy >= 0 && hy < 18 && image.getRGB(hx, hy) == 0) image.setRGB(hx, hy, halo);
            }
        }
        for (int i = 0; i < 12; i++) image.setRGB(3 + i, 14 - i, i < 3 ? fletch : i < 8 ? barrel : i < 11 ? shaft : tip);
        // Fletching either side of the tail; the needle catches the light.
        image.setRGB(2, 13, fletch); image.setRGB(4, 15, fletch); image.setRGB(3, 12, fletch); image.setRGB(5, 15, fletch);
        image.setRGB(15, 2, tip);
        return image;
    }
}
