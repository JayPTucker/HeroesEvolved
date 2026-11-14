import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * Draws the original skins of the story's actors (story/actor/StoryActorEntity) on the standard 64x64 wide-arm player
 * layout. No downloaded assets.
 * <ul>
 *   <li>Noah Bennet: auburn hair with a side parting, heavy horn-rimmed glasses, a navy suit, pale blue shirt and a dark
 *   patterned tie.</li>
 *   <li>Claire Bennet: her cheerleading uniform: long wavy blonde hair, a white V-neck top with a gold crest, red long
 *   sleeves, a red pleated skirt with a white and gold hem, white sneakers.</li>
 *   <li>Peter Petrelli: dark hair falling over his brow, a black jacket over a grey shirt, dark jeans.</li>
 *   <li>Ted Sprague: short brown hair, stubble, a scorched work shirt; and an emissive layer of light coming up through
 *   his skin, drawn with the eyes render type (black is nothing, light is added).</li>
 * </ul>
 *
 * <pre>java tools/GenerateStoryActorTextures.java</pre>
 */
public final class GenerateStoryActorTextures {
    private static final String ROOT = "src/main/resources/assets/heroesevolved/textures/entity/";

    public static void main(String[] args) throws IOException {
        write(noah(), "noah_bennet.png");
        write(claire(), "claire_bennet.png");
        write(peter(), "peter_petrelli.png");
        write(ted(), "ted_sprague.png");
        write(tedGlow(), "ted_sprague_glow.png");
        write(glassesFrame(), "../item/horn_rimmed_glasses_frame.png");
        write(glassesLens(), "../item/horn_rimmed_glasses_lens.png");
        write(glassesIcon(), "../item/horn_rimmed_glasses.png");
    }

    private static void write(BufferedImage image, String name) throws IOException {
        File file = new File(ROOT + name);
        file.getParentFile().mkdirs();
        ImageIO.write(image, "png", file);
        System.out.println("Wrote " + file + " (" + image.getWidth() + "x" + image.getHeight() + ")");
    }

    // ------------------------------------------------------------------
    // Drawing helpers
    // ------------------------------------------------------------------

    private static int rgb(int r, int g, int b) { return 0xFF000000 | r << 16 | g << 8 | b; }
    private static int argb(int a, int r, int g, int b) { return a << 24 | r << 16 | g << 8 | b; }
    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static int shade(int c, int by) {
        return rgb(clamp(((c >> 16) & 0xFF) + by), clamp(((c >> 8) & 0xFF) + by), clamp((c & 0xFF) + by));
    }

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
            image.setRGB(x, y, shade(c, random.nextInt(spread * 2 + 1) - spread));
        }
    }

    /** The body parts' side strips (y offsets are within the 4-deep box: sides start 4 rows down). */
    private static final int[][] ARMS = {{40, 16}, {32, 48}}, LEGS = {{0, 16}, {16, 48}};

    /** A plain figure: skin everywhere, then a shirt on the body and arms, trousers and shoes on the legs. */
    private static BufferedImage figure(int skin, int shirt, int sleeveRows, int trousers, int shoe) {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        box(image, 0, 0, 8, 8, 8, skin);
        box(image, 16, 16, 8, 4, 12, shirt);
        for (int[] arm : ARMS) {
            box(image, arm[0], arm[1], 4, 4, 12, skin);
            // Sleeves from the shoulder down, and the top of the arm.
            fill(image, arm[0], arm[1] + 4, arm[0] + 15, arm[1] + 4 + sleeveRows - 1, shirt);
            fill(image, arm[0] + 4, arm[1], arm[0] + 7, arm[1] + 3, shirt);
        }
        for (int[] leg : LEGS) {
            box(image, leg[0], leg[1], 4, 4, 12, trousers);
            fill(image, leg[0], leg[1] + 14, leg[0] + 15, leg[1] + 15, shoe);
            fill(image, leg[0] + 8, leg[1], leg[0] + 11, leg[1] + 3, shoe);
        }
        return image;
    }

    /** Short hair: the crown, the back, the sides above the ears, and a fringe {@code fringe} rows deep. */
    private static void shortHair(BufferedImage image, int hair, int fringe) {
        int shade = shade(hair, -22);
        fill(image, 8, 0, 15, 7, hair);
        fill(image, 24, 8, 31, 12, hair); fill(image, 25, 13, 30, 13, shade);
        fill(image, 0, 8, 7, 10, hair); fill(image, 16, 8, 23, 10, hair);
        fill(image, 8, 8, 15, 7 + fringe, hair);
        for (int x = 8; x <= 15; x += 3) image.setRGB(x, 8, shade);
    }

    /** Eyes, brows, nose and mouth on the front of the head. */
    private static void face(BufferedImage image, int skin, int brow, int iris, int lips) {
        fill(image, 9, 10, 10, 10, brow); fill(image, 13, 10, 14, 10, brow);
        image.setRGB(9, 11, rgb(245, 245, 245)); image.setRGB(10, 11, iris);
        image.setRGB(13, 11, iris); image.setRGB(14, 11, rgb(245, 245, 245));
        image.setRGB(11, 12, shade(skin, -24)); image.setRGB(12, 12, shade(skin, -24));
        fill(image, 10, 14, 13, 14, lips);
    }

    // ------------------------------------------------------------------
    // The four of them
    // ------------------------------------------------------------------

    // Outer layer boxes (64x64 layout): hat, jacket, sleeves and trouser legs, drawn half a pixel proud of the body.
    private static final int HAT_X = 32, HAT_Y = 0, JACKET_X = 16, JACKET_Y = 32;
    private static final int[][] SLEEVES = {{40, 32}, {48, 48}}, PANTS = {{0, 32}, {0, 48}};

    /**
     * Noah Bennet in his working clothes: auburn hair with a side parting, heavy dark horn-rimmed glasses, a navy suit with
     * a faint check, a pale blue shirt and a dark tie with a small pattern, black shoes. The lapels are on the outer layer so
     * they stand off the jacket.
     */
    private static BufferedImage noah() {
        Random random = new Random(1961);
        int skin = rgb(226, 186, 160), suit = rgb(34, 40, 62), shirt = rgb(176, 200, 228), tie = rgb(28, 32, 54);
        BufferedImage image = figure(skin, suit, 12, suit, rgb(18, 16, 16));
        // Auburn hair, cut short and parted on his left, swept across.
        int hair = rgb(126, 80, 52), dark = rgb(92, 56, 36), light = rgb(150, 100, 66);
        shortHair(image, hair, 1);
        fill(image, 8, 8, 15, 8, hair); image.setRGB(13, 9, hair); image.setRGB(14, 9, hair); image.setRGB(15, 9, hair);
        image.setRGB(10, 8, light); image.setRGB(11, 8, light); image.setRGB(9, 7, dark); image.setRGB(10, 7, dark);
        for (int x = 8; x <= 15; x += 2) image.setRGB(x, 2 + (x % 3), dark);
        face(image, skin, dark, rgb(84, 110, 134), rgb(168, 118, 106));
        // A firm, thin mouth and the line of the jaw.
        fill(image, 10, 14, 13, 14, rgb(176, 128, 112)); image.setRGB(10, 14, shade(skin, -18)); image.setRGB(13, 14, shade(skin, -18));
        for (int x = 9; x <= 14; x++) image.setRGB(x, 15, shade(skin, -14));
        // His glasses are not painted on: he wears a real pair (item horn_rimmed_glasses, drawn by the head item layer).
        // The suit: a faint check across the navy, then shirt, tie and lapels on the front.
        for (int y = 20; y <= 31; y++) for (int x = 16; x <= 39; x++) if ((x + y) % 5 == 0) image.setRGB(x, y, shade(suit, 10));
        fill(image, 22, 20, 25, 24, shirt);
        image.setRGB(22, 25, shirt); image.setRGB(25, 25, shirt);
        fill(image, 23, 20, 24, 29, tie);
        image.setRGB(23, 20, shade(tie, 18)); image.setRGB(24, 20, shade(tie, 18));
        for (int y = 22; y <= 29; y += 2) image.setRGB(23 + (y / 2) % 2, y, rgb(96, 106, 150));
        image.setRGB(23, 30, shade(tie, -6));
        // Buttons, and the jacket's hem.
        image.setRGB(23, 30, rgb(20, 22, 30)); image.setRGB(24, 28, rgb(20, 22, 30));
        fill(image, 16, 31, 39, 31, shade(suit, -10));
        // Pale blue cuffs and the hands.
        for (int[] arm : ARMS) {
            fill(image, arm[0], arm[1] + 4 + 10, arm[0] + 15, arm[1] + 4 + 10, shirt);
            fill(image, arm[0], arm[1] + 4 + 11, arm[0] + 15, arm[1] + 4 + 11, skin);
        }
        // Lapels on the outer layer: two dark notched strips either side of the shirt, down to the button.
        int lapel = shade(suit, -12), edge = shade(suit, 16);
        fill(image, 20, 36, 21, 41, lapel); fill(image, 26, 36, 27, 41, lapel);
        image.setRGB(21, 36, edge); image.setRGB(26, 36, edge);
        image.setRGB(22, 37, lapel); image.setRGB(25, 37, lapel); image.setRGB(22, 38, lapel); image.setRGB(25, 38, lapel);
        image.setRGB(21, 42, lapel); image.setRGB(26, 42, lapel); image.setRGB(22, 43, lapel); image.setRGB(25, 43, lapel);
        // Creases in the trousers, polish on the shoes.
        for (int[] leg : LEGS) {
            fill(image, leg[0] + 5, leg[1] + 4, leg[0] + 5, leg[1] + 13, shade(suit, -8));
            image.setRGB(leg[0] + 5, leg[1] + 14, rgb(60, 58, 58));
        }
        weave(image, random, 16, 20, 39, 31, 2);
        weave(image, random, 0, 20, 15, 31, 2);
        weave(image, random, 32, 52, 47, 63, 2);
        weave(image, random, 40, 20, 55, 31, 2);
        weave(image, random, 0, 0, 31, 7, 3);
        return image;
    }

    /**
     * Claire Bennet in her cheerleading uniform: long wavy blonde hair over her shoulders, a white shell top with a V neck
     * and a gold crest, red long sleeves, a red pleated skirt with a white and gold hem, white sneakers. The hair's volume,
     * the locks over her shoulders and the skirt's flare are on the outer layer.
     */
    private static BufferedImage claire() {
        Random random = new Random(1991);
        int skin = rgb(242, 210, 188), red = rgb(196, 34, 40), white = rgb(244, 242, 236), gold = rgb(226, 176, 52);
        int deepRed = rgb(150, 22, 30);
        BufferedImage image = figure(skin, white, 0, skin, white);
        int hair = rgb(240, 212, 140), shade = rgb(210, 176, 100), light = rgb(252, 234, 178);
        // The head: hair on the crown, the sides and the back; a side-swept fringe; face framed by it.
        fill(image, 8, 0, 15, 7, hair);
        fill(image, 0, 8, 7, 15, hair); fill(image, 16, 8, 23, 15, hair); fill(image, 24, 8, 31, 15, hair);
        fill(image, 8, 8, 15, 8, hair); fill(image, 8, 9, 11, 9, hair); image.setRGB(8, 10, hair); image.setRGB(15, 9, hair);
        fill(image, 8, 10, 8, 14, hair); fill(image, 15, 10, 15, 14, hair);
        for (int x = 0; x <= 31; x++) for (int y = 8; y <= 15; y++)
            if ((x < 8 || x > 15) && (x + y * 2) % 5 == 0) image.setRGB(x, y, shade);
        for (int x = 8; x <= 15; x++) image.setRGB(x, (x * 3) % 7, light);
        face(image, skin, rgb(200, 164, 104), rgb(86, 132, 150), rgb(212, 112, 120));
        image.setRGB(9, 13, rgb(244, 186, 176)); image.setRGB(14, 13, rgb(244, 186, 176));
        // Volume on the outer layer: waves over the crown, sides and back, and a curl at each cheek; the face left clear.
        box(image, HAT_X, HAT_Y, 8, 8, 8, hair);
        fill(image, 40, 10, 46, 15, 0);   // the face, through the hat's front
        fill(image, 41, 9, 46, 9, 0);
        for (int x = 32; x <= 63; x++) for (int y = 8; y <= 15; y++) {
            int c = image.getRGB(x, y);
            if ((c >>> 24) != 0 && (x * 2 + y) % 4 == 0) image.setRGB(x, y, shade);
            if ((c >>> 24) != 0 && (x + y) % 7 == 0) image.setRGB(x, y, light);
        }
        // Red sleeves all the way down, then the hands.
        for (int[] arm : ARMS) {
            fill(image, arm[0], arm[1] + 4, arm[0] + 15, arm[1] + 4 + 10, red);
            fill(image, arm[0] + 4, arm[1], arm[0] + 7, arm[1] + 3, red);
            fill(image, arm[0], arm[1] + 4 + 10, arm[0] + 15, arm[1] + 4 + 10, deepRed);   // the cuff
            fill(image, arm[0], arm[1] + 4 + 11, arm[0] + 15, arm[1] + 4 + 11, skin);
        }
        // The top: white, a V neck trimmed in red, a gold crest on her right side, the hem at the waist.
        fill(image, 23, 20, 24, 21, skin); image.setRGB(23, 22, skin); image.setRGB(24, 22, skin);
        image.setRGB(22, 20, red); image.setRGB(25, 20, red); image.setRGB(22, 21, red); image.setRGB(25, 21, red);
        image.setRGB(23, 23, red); image.setRGB(24, 23, red);
        fill(image, 20, 24, 22, 26, gold); image.setRGB(21, 25, deepRed); image.setRGB(20, 24, white); image.setRGB(22, 26, white);
        // The skirt: red from the waist, pleated in two reds, with a white then gold hem on the thighs.
        fill(image, 16, 28, 39, 31, red);
        fill(image, 16, 27, 39, 27, gold);
        for (int x = 16; x <= 39; x += 2) fill(image, x, 28, x, 31, deepRed);
        for (int[] leg : LEGS) {
            fill(image, leg[0], leg[1] + 4, leg[0] + 15, leg[1] + 6, red);
            fill(image, leg[0] + 4, leg[1], leg[0] + 7, leg[1] + 3, red);
            for (int x = leg[0]; x <= leg[0] + 15; x += 2) fill(image, x, leg[1] + 4, x, leg[1] + 6, deepRed);
            fill(image, leg[0], leg[1] + 7, leg[0] + 15, leg[1] + 7, white);
            // White socks and sneakers.
            fill(image, leg[0], leg[1] + 13, leg[0] + 15, leg[1] + 13, white);
            fill(image, leg[0], leg[1] + 15, leg[0] + 15, leg[1] + 15, rgb(206, 204, 198));
        }
        // The skirt's flare on the outer layer: over the hips and the tops of the thighs, its hem white over gold.
        fill(image, 16, 44, 39, 47, red);
        for (int x = 16; x <= 39; x += 2) fill(image, x, 44, x, 47, deepRed);
        for (int[] pants : PANTS) {
            fill(image, pants[0], pants[1] + 4, pants[0] + 15, pants[1] + 6, red);
            for (int x = pants[0]; x <= pants[0] + 15; x += 2) fill(image, x, pants[1] + 4, x, pants[1] + 6, deepRed);
            fill(image, pants[0], pants[1] + 7, pants[0] + 15, pants[1] + 7, white);
            fill(image, pants[0], pants[1] + 8, pants[0] + 15, pants[1] + 8, gold);
        }
        // Her hair down her back to the shoulder blades, and a lock over the front of each shoulder.
        fill(image, 32, 20, 39, 25, hair);
        for (int x = 32; x <= 39; x++) image.setRGB(x, 26 - (x % 2), hair);
        fill(image, 32, 36, 39, 42, hair);
        for (int x = 32; x <= 39; x++) image.setRGB(x, 43 - (x % 3 == 0 ? 1 : 0), hair);
        for (int x = 32; x <= 39; x += 2) fill(image, x, 36, x, 41, shade);
        fill(image, 20, 36, 20, 39, hair); fill(image, 27, 36, 27, 39, hair);
        image.setRGB(20, 40, shade); image.setRGB(27, 40, shade);
        weave(image, random, 16, 20, 39, 31, 2);
        weave(image, random, 0, 20, 15, 31, 2);
        weave(image, random, 32, 52, 47, 63, 2);
        weave(image, random, 40, 20, 55, 31, 2);
        weave(image, random, 0, 0, 31, 15, 3);
        return image;
    }

    // ------------------------------------------------------------------
    // Noah's glasses (item horn_rimmed_glasses): a tortoiseshell frame, a faint lens, and the inventory icon
    // ------------------------------------------------------------------

    /** Dark tortoiseshell: near-black brown with amber flecks. */
    private static BufferedImage glassesFrame() {
        Random random = new Random(1962);
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        fill(image, 0, 0, 15, 15, rgb(34, 22, 16));
        for (int i = 0; i < 40; i++) image.setRGB(random.nextInt(16), random.nextInt(16), random.nextBoolean() ? rgb(92, 54, 26) : rgb(60, 36, 20));
        return image;
    }

    /** Glass with a faint cool tint, mostly see-through, a highlight in one corner. */
    private static BufferedImage glassesLens() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        fill(image, 0, 0, 15, 15, argb(56, 200, 220, 236));
        for (int i = 0; i < 5; i++) image.setRGB(2 + i, 2 + i / 2, argb(130, 255, 255, 255));
        return image;
    }

    /** The inventory icon: the glasses seen from the front, heavy brow bars, two lenses and the bridge. */
    private static BufferedImage glassesIcon() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        int frame = rgb(40, 26, 18), fleck = rgb(96, 58, 28), lens = argb(120, 196, 218, 236), shine = argb(200, 255, 255, 255);
        for (int side = 0; side < 2; side++) {
            int x0 = side == 0 ? 1 : 9;
            fill(image, x0, 5, x0 + 5, 6, frame);          // the heavy top of the rim
            fill(image, x0, 7, x0, 9, frame); fill(image, x0 + 5, 7, x0 + 5, 9, frame);
            fill(image, x0 + 1, 10, x0 + 4, 10, frame);    // the thinner bottom
            fill(image, x0 + 1, 7, x0 + 4, 9, lens);
            image.setRGB(x0 + 1, 7, shine);
            image.setRGB(x0 + 2, 5, fleck); image.setRGB(x0 + 4, 6, fleck);
        }
        fill(image, 7, 6, 8, 6, frame);                    // the bridge
        image.setRGB(0, 6, frame); image.setRGB(15, 6, frame);   // the hinges
        return image;
    }

    private static BufferedImage peter() {
        Random random = new Random(2006);
        int skin = rgb(228, 190, 162), jacket = rgb(30, 30, 34), shirt = rgb(120, 122, 126), jeans = rgb(42, 48, 64);
        BufferedImage image = figure(skin, jacket, 12, jeans, rgb(36, 30, 26));
        int hair = rgb(38, 30, 26);
        shortHair(image, hair, 2);
        // A heavy fringe falling over one eye.
        fill(image, 9, 10, 13, 10, hair); image.setRGB(9, 11, hair);
        fill(image, 0, 9, 7, 12, hair); fill(image, 16, 9, 23, 12, hair); fill(image, 24, 13, 31, 14, hair);
        face(image, skin, hair, rgb(84, 64, 44), rgb(178, 124, 112));
        image.setRGB(9, 11, hair);
        // The grey shirt under the open jacket.
        fill(image, 22, 20, 25, 31, shirt);
        for (int[] arm : ARMS) fill(image, arm[0], arm[1] + 4 + 11, arm[0] + 15, arm[1] + 4 + 11, skin);
        weave(image, random, 16, 20, 39, 31, 3);
        weave(image, random, 0, 20, 15, 31, 2);
        weave(image, random, 0, 0, 31, 15, 3);
        return image;
    }

    private static BufferedImage ted() {
        Random random = new Random(1977);
        int skin = rgb(236, 176, 150), shirt = rgb(112, 120, 92), trousers = rgb(70, 64, 54);
        BufferedImage image = figure(skin, shirt, 4, trousers, rgb(60, 44, 30));
        shortHair(image, rgb(96, 70, 48), 1);
        face(image, skin, rgb(80, 60, 40), rgb(90, 80, 60), rgb(190, 110, 100));
        // Stubble along the jaw, and skin flushed red from the heat.
        for (int x = 9; x <= 14; x++) image.setRGB(x, 15, rgb(170, 120, 104));
        image.setRGB(9, 13, rgb(240, 150, 130)); image.setRGB(14, 13, rgb(240, 150, 130));
        // Scorch marks on the shirt, open at the collar.
        fill(image, 23, 20, 24, 22, skin);
        for (int i = 0; i < 14; i++) image.setRGB(16 + random.nextInt(24), 21 + random.nextInt(10), rgb(46, 40, 30));
        weave(image, random, 16, 20, 39, 31, 4);
        weave(image, random, 0, 20, 15, 31, 3);
        weave(image, random, 0, 0, 31, 15, 3);
        return image;
    }

    /** Light coming up through Ted: burning eyes, veins of light on his face, arms and chest. */
    private static BufferedImage tedGlow() {
        Random random = new Random(614);
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        int hot = argb(255, 255, 236, 180), warm = argb(255, 255, 196, 110), dim = argb(255, 170, 110, 50);
        // Eyes and mouth burning white.
        image.setRGB(10, 11, hot); image.setRGB(13, 11, hot); image.setRGB(9, 11, warm); image.setRGB(14, 11, warm);
        fill(image, 10, 14, 13, 14, warm);
        // Veins: random walks of light across the skin and through the shirt.
        int[][] regions = {{8, 8, 15, 15}, {0, 8, 7, 15}, {16, 8, 23, 15}, {20, 20, 27, 31}, {44, 20, 47, 31}, {36, 52, 39, 63},
                {40, 20, 43, 31}, {32, 52, 35, 63}, {16, 20, 19, 31}, {28, 20, 31, 31}};
        for (int[] r : regions) {
            for (int vein = 0; vein < 3; vein++) {
                int x = r[0] + random.nextInt(r[2] - r[0] + 1), y = r[1] + random.nextInt(r[3] - r[1] + 1);
                for (int step = 0; step < 6; step++) {
                    image.setRGB(x, y, step < 2 ? hot : step < 4 ? warm : dim);
                    x = Math.max(r[0], Math.min(r[2], x + random.nextInt(3) - 1));
                    y = Math.max(r[1], Math.min(r[3], y + (random.nextBoolean() ? 1 : 0)));
                }
            }
        }
        // His palms, brightest of all.
        for (int[] arm : ARMS) fill(image, arm[0] + 8, arm[1], arm[0] + 11, arm[1] + 3, hot);
        return image;
    }
}
