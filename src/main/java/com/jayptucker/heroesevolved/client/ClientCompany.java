package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.company.CompanyAttention;
import com.jayptucker.heroesevolved.gui.HudGauges;
import com.jayptucker.heroesevolved.network.CompanyAttentionPayload;

import java.util.List;

/**
 * The Company's attention on this player, as the server last said ({@code company/CompanyAttention}), and its ring on
 * the HUD: it fills as attention rises, with notches where each band starts, coloured by band, and says what the Company
 * is doing while an operation is under way.
 */
public final class ClientCompany {
    /** Shown from this much attention, or while an operation is under way. */
    public static final float SHOW = 5;
    private static final int GREY = 0xFF9AA3AE, STEEL = 0xFF7FA7D8, AMBER = 0xFFFFB43A, RED = 0xFFFF3B2E, PALE = 0xFFFFE6DC;
    private static final String[] DOING = {"", "ASKING", "CONVOY", "RAID", "CHASE"};
    private static float attention;
    private static int op;

    private ClientCompany() {}

    public static void accept(CompanyAttentionPayload payload) {
        attention = payload.attention();
        op = payload.op();
    }

    public static float attention() { return attention; }
    public static int op() { return op; }

    public static void gauges(List<HudGauges.Gauge> gauges) {
        if (attention < SHOW && op == 0) return;
        CompanyAttention.Band band = CompanyAttention.band(attention);
        boolean flash = HudGauges.flash();
        int color = switch (band) {
            case QUIET -> GREY;
            case NOTICED -> STEEL;
            case WATCHED -> AMBER;
            case HUNTED -> flash && op == 3 ? PALE : RED;
        };
        String label = op > 0 && op < DOING.length ? DOING[op] : band.label;
        gauges.add(HudGauges.Gauge.ring(attention / CompanyAttention.MAX, color).middle(String.valueOf(Math.round(attention)))
                .label(label, op == 3 && flash ? PALE : color)
                .notches(CompanyAttention.NOTICED / CompanyAttention.MAX, CompanyAttention.WATCHED / CompanyAttention.MAX,
                        CompanyAttention.HUNTED / CompanyAttention.MAX));
    }
}
