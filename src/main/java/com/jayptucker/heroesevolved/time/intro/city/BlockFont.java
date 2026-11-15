package com.jayptucker.heroesevolved.time.intro.city;

/**
 * A three-by-five pixel font for lettering billboards in blocks. Each glyph is five rows, top first, three bits each
 * (the high bit is the left column). Unknown characters are blank.
 */
final class BlockFont {
    private BlockFont() {}

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!.-&%";
    private static final int[][] GLYPHS = {
            {2, 5, 7, 5, 5}, {6, 5, 6, 5, 6}, {3, 4, 4, 4, 3}, {6, 5, 5, 5, 6}, {7, 4, 6, 4, 7}, {7, 4, 6, 4, 4},
            {3, 4, 5, 5, 3}, {5, 5, 7, 5, 5}, {7, 2, 2, 2, 7}, {1, 1, 1, 5, 2}, {5, 5, 6, 5, 5}, {4, 4, 4, 4, 7},
            {5, 7, 7, 5, 5}, {6, 5, 5, 5, 5}, {2, 5, 5, 5, 2}, {6, 5, 6, 4, 4}, {2, 5, 5, 7, 3}, {6, 5, 6, 5, 5},
            {3, 4, 2, 1, 6}, {7, 2, 2, 2, 2}, {5, 5, 5, 5, 7}, {5, 5, 5, 5, 2}, {5, 5, 5, 7, 5}, {5, 5, 2, 5, 5},
            {5, 5, 2, 2, 2}, {7, 1, 2, 4, 7},
            {7, 5, 5, 5, 7}, {2, 6, 2, 2, 7}, {7, 1, 7, 4, 7}, {7, 1, 7, 1, 7}, {5, 5, 7, 1, 1}, {7, 4, 7, 1, 7},
            {7, 4, 7, 5, 7}, {7, 1, 1, 1, 1}, {7, 5, 7, 5, 7}, {7, 5, 7, 1, 7},
            {2, 2, 2, 0, 2}, {0, 0, 0, 0, 2}, {0, 0, 7, 0, 0}, {2, 5, 2, 5, 3}, {5, 1, 2, 4, 5}};
    private static final int[] BLANK = {0, 0, 0, 0, 0};

    static int[] glyph(char ch) {
        int i = CHARS.indexOf(Character.toUpperCase(ch));
        return i < 0 ? BLANK : GLYPHS[i];
    }
}
