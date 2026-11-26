package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.endgame.EndClientState;
import com.jayptucker.heroesevolved.endgame.SealView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

/**
 * The Living Seal's own boss bar, built from its shape (as Bosses'Rise gives each boss one): a long violet blade of a
 * bar under the Seal's emblem, three orbit rings round a star, with a pip for each knot still warding it in the first
 * phase. The fill burns in the phase's colour (violet, blue, ember), and a pale trail shows the health just lost.
 * Drawn in code; only the Dragon's bar is replaced.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class SealBossBar {
    private static final int W = 240, H = 40, BAR_Y = 26, BAR_H = 6;
    private static final java.util.Map<java.util.UUID, float[]> TRAIL = new java.util.HashMap<>();

    private SealBossBar() {}

    public static boolean ours(net.minecraft.network.chat.Component name) {
        return name.getContents() instanceof TranslatableContents t && t.getKey().startsWith("endgame.heroesevolved.dragon.");
    }

    @SubscribeEvent public static void draw(CustomizeGuiOverlayEvent.BossEventProgress event) {
        var boss = event.getBossEvent();
        if (!ours(boss.getName())) return;
        event.setCanceled(true);
        GuiGraphics g = event.getGuiGraphics();
        var font = Minecraft.getInstance().font;
        int cx = g.guiWidth() / 2, x = cx - W / 2, y = event.getY();
        int colour = switch (boss.getColor()) { case BLUE -> 0x7FB0FF; case RED -> 0xFF5A6E; case WHITE -> 0xF0F2DC; default -> 0xA78AF2; };
        float progress = Mth.clamp(boss.getProgress(), 0, 1);
        float[] trail = TRAIL.computeIfAbsent(boss.getId(), k -> new float[]{progress});
        trail[0] = progress > trail[0] ? progress : Mth.lerp(.04F, trail[0], progress);
        long time = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        float pulse = .75F + .25F * Mth.sin(time * .15F);

        // The blade: a dark bar with tapered points, a violet edge and notches every tenth.
        int bx = x + 8, bw = W - 16, by = y + BAR_Y;
        for (int i = 0; i < 6; i++) { g.fill(bx - 6 + i, by + 3 - i / 2, bx - 5 + i, by + 3 + (i + 1) / 2, 0xFF1B1030); g.fill(bx + bw + 5 - i, by + 3 - i / 2, bx + bw + 6 - i, by + 3 + (i + 1) / 2, 0xFF1B1030); }
        g.fill(bx - 1, by - 1, bx + bw + 1, by + BAR_H + 1, 0xFF1B1030);
        g.fill(bx - 1, by - 2, bx + bw + 1, by - 1, 0xFF000000 | mix(colour, 0x000000, .4F));
        g.fill(bx - 1, by + BAR_H + 1, bx + bw + 1, by + BAR_H + 2, 0xFF000000 | mix(colour, 0x000000, .4F));
        int full = Math.round(bw * progress), lost = Math.round(bw * trail[0]);
        if (lost > full) g.fill(bx + full, by, bx + lost, by + BAR_H, 0xFFE8E2D0);
        if (full > 0) {
            g.fill(bx, by, bx + full, by + BAR_H, 0xFF000000 | colour);
            g.fill(bx, by, bx + full, by + 2, 0xFF000000 | mix(colour, 0xFFFFFF, .45F));
            g.fill(bx, by + BAR_H - 1, bx + full, by + BAR_H, 0xFF000000 | mix(colour, 0x000000, .35F));
        }
        for (int i = 1; i < 10; i++) g.fill(bx + bw * i / 10, by, bx + bw * i / 10 + 1, by + BAR_H, 0x66000000);
        // Phase thresholds marked in white on the blade.
        for (int i = 1; i <= 2; i++) { int at = bx + bw * i / 3; g.fill(at, by - 3, at + 1, by + BAR_H + 3, 0xFFF0F2DC); }

        // The Seal: three orbit rings round a star, over the blade's middle.
        int ex = cx, ey = y + 13;
        for (int r = 0; r < 3; r++) {
            float rx = 18 - r * 3, ry = 5 + r * 2.5F, tilt = time * .03F * (r == 1 ? -1 : 1) + r;
            int ring = 0xFF000000 | mix(colour, 0xF0F2DC, r == 0 ? .5F : .15F);
            for (int k = 0; k < 48; k++) {
                float ang = k * Mth.TWO_PI / 48;
                float px = Mth.cos(ang) * rx, py = Mth.sin(ang) * ry;
                float qx = px * Mth.cos(tilt * .2F) - py * Mth.sin(tilt * .2F), qy = px * Mth.sin(tilt * .2F) + py * Mth.cos(tilt * .2F);
                g.fill(ex + Math.round(qx), ey + Math.round(qy), ex + Math.round(qx) + 1, ey + Math.round(qy) + 1, ring);
            }
        }
        int core = 0xFF000000 | mix(colour, 0xFFFFFF, .3F + .3F * pulse);
        g.fill(ex - 2, ey - 2, ex + 3, ey + 3, core);
        g.fill(ex - 4, ey, ex + 5, ey + 1, core);
        g.fill(ex, ey - 4, ex + 1, ey + 5, core);

        // The knots still warding it (first phase): four diamonds either side of the emblem.
        SealView s = EndClientState.view().seal();
        if (EndClientState.view().phase() == 1 && !s.knots().isEmpty()) {
            for (int i = 0; i < 4; i++) {
                int px = ex + (i < 2 ? -34 - i * 12 : 34 + (i - 2) * 12), py = ey;
                boolean warding = i < s.knots().size() && s.knots().get(i) == SealView.WARDING;
                int col = warding ? 0xFF000000 | mix(0xA78AF2, 0xFFFFFF, .25F * pulse) : 0xFF3A3050;
                for (int d = 0; d < 4; d++) g.fill(px - d, py - 3 + d, px + d + 1, py - 2 + d, col);
                for (int d = 0; d < 3; d++) g.fill(px - 2 + d, py + 1 + d, px + 3 - d, py + 2 + d, col);
            }
        }
        int name = font.width(boss.getName());
        g.drawString(font, boss.getName(), cx - name / 2, y - 6, 0xFFFFFF);
        event.setIncrement(H + 8);
    }

    private static int mix(int a, int b, float k) {
        int r = (int) Mth.lerp(k, (a >> 16) & 255, (b >> 16) & 255), gr = (int) Mth.lerp(k, (a >> 8) & 255, (b >> 8) & 255), bl = (int) Mth.lerp(k, a & 255, b & 255);
        return r << 16 | gr << 8 | bl;
    }

    @SubscribeEvent public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) { TRAIL.clear(); }
}
