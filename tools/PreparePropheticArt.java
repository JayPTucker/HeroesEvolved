import java.nio.file.*;
import javax.imageio.ImageIO;
import net.minecraft.world.level.material.MapColor;

/** Converts original artwork to the vanilla map palette at build-authoring time, not during a server tick. */
class PreparePropheticArt {
    public static void main(String[] args) throws Exception {
        var image = ImageIO.read(Path.of(args[0]).toFile());
        byte[] result = new byte[16384];
        int[] palette = new int[256];
        for (int i = 4; i < 248; i++) palette[i] = MapColor.getColorFromPackedId(i);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            long r = 0, g = 0, b = 0, samples = 0;
            for (int sy = y * image.getHeight() / 128; sy < (y + 1) * image.getHeight() / 128; sy++)
                for (int sx = x * image.getWidth() / 128; sx < (x + 1) * image.getWidth() / 128; sx++) {
                    int rgb = image.getRGB(sx, sy); r += rgb >> 16 & 255; g += rgb >> 8 & 255; b += rgb & 255; samples++;
                }
            double red = r / (double) samples, green = g / (double) samples, blue = b / (double) samples;
            int nearest = 4; double distance = Double.MAX_VALUE;
            for (int i = 4; i < 248; i++) {
                // Minecraft map colors are packed ABGR.
                int color = palette[i]; double dr = red - (color & 255), dg = green - (color >> 8 & 255), db = blue - (color >> 16 & 255);
                double delta = dr * dr * .3 + dg * dg * .59 + db * db * .11;
                if (delta < distance) { distance = delta; nearest = i; }
            }
            result[x + y * 128] = (byte) nearest;
        }
        Files.createDirectories(Path.of(args[1]).getParent()); Files.write(Path.of(args[1]), result);
        System.out.println("Prepared 128x128 vanilla-map artwork: " + args[1]);
    }
}
