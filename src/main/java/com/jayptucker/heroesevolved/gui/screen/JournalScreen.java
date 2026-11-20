package com.jayptucker.heroesevolved.gui.screen;

import com.jayptucker.heroesevolved.network.ClientJournalState;
import com.jayptucker.heroesevolved.network.JournalRequestPayload;
import com.jayptucker.heroesevolved.network.StoryJournalPayload;
import com.jayptucker.heroesevolved.story.LastSignalService.Phase;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** The current signal objective, optional discoveries, and the player's story milestones. */
public final class JournalScreen extends HeroScreen {
    private static final int LEAD = 0xFF60C8D0, HEADING = 0xFFE0B040, BAD = 0xFFE06060;

    private enum Chapter {
        SIGNAL("The Last Signal"), RECORDS("Records"), DREAMS("Dreams"), WAY_BACK("The way back"), WATCHMAKER("The watchmaker");
        final String title;
        Chapter(String title) { this.title = title; }
    }

    private static final String[][] MILESTONES = {
            {"The Last Day", "Stand in Odessa at 6:14 and escape the light."},
            {"No One Left Behind", "Carry a companion through space, or share stopped time."},
            {"A Place to Return", "Establish a spatial anchor."},
            {"The Butterfly Effect", "Successfully rewrite fifteen seconds."},
            {"The Road Not Taken", "Enter a recorded past."}
    };

    private static Chapter last = Chapter.SIGNAL;
    private Chapter chapter = last;
    private int scroll;

    private JournalScreen(Screen parent) { super(Component.literal("Journal"), parent); }

    public static void open(Screen parent) {
        if (Minecraft.getInstance().getConnection() != null) PacketDistributor.sendToServer(new JournalRequestPayload());
        Minecraft.getInstance().setScreen(new JournalScreen(parent));
    }

    /** Chooses the chapter to open in the UI preview. */
    public static void openAt(int chapter) { last = Chapter.values()[Math.clamp(chapter, 0, Chapter.values().length - 1)]; }

    /** New story progress arrived; the page reads it as it draws. */
    public void onContentsUpdated() { }

    @Override protected Tab tab() { return Tab.JOURNAL; }

    // ------------------------------------------------------------------

    @Override protected void renderPage(GuiGraphics g, int mouseX, int mouseY, float partial) {
        StoryJournalPayload journal = ClientJournalState.get();
        int x = contentLeft(), top = contentTop(), bottom = contentBottom() - 4;
        int sideW = Mth.clamp(contentWidth() / 4, 100, 140);
        // The chapters.
        for (int i = 0; i < Chapter.values().length; i++) {
            Chapter c = Chapter.values()[i];
            int y = top + i * 22;
            boolean active = c == chapter, over = mouseX >= x && mouseX < x + sideW && mouseY >= y && mouseY < y + 20;
            g.fill(x, y, x + sideW, y + 20, active ? 0x996A4A14 : over ? 0x66403020 : 0x44000000);
            if (active) g.fill(x, y, x + 2, y + 20, GOLD);
            g.drawString(font, font.plainSubstrByWidth(c.title, sideW - 10), x + 7, y + 3, active ? GOLD : TEXT, false);
            String progress = progress(c, journal);
            if (!progress.isEmpty()) g.drawString(font, font.plainSubstrByWidth(progress, sideW - 10), x + 7, y + 11, MUTED, false);
            hits.add(new Hit(x, y, sideW, 20, () -> { chapter = c; last = c; scroll = 0; }, null));
        }
        int px = x + sideW + 8, pw = contentRight() - px;
        panel(g, px, top, pw, bottom - top);
        if (journal == null) {
            g.drawString(font, "Gathering your memories...", px + 8, top + 8, MUTED, false);
            return;
        }
        g.drawString(font, Component.literal(chapter.title.toUpperCase()).withStyle(ChatFormatting.BOLD), px + 8, top + 6, HEADING, false);
        List<Line> lines = lines(journal, pw - 16);
        int areaTop = top + 18, visible = Math.max(0, (bottom - areaTop - 4) / 10);
        scroll = Mth.clamp(scroll, 0, Math.max(0, lines.size() - visible));
        g.enableScissor(px, areaTop, px + pw, bottom);
        for (int i = scroll; i < Math.min(lines.size(), scroll + visible); i++) {
            Line line = lines.get(i);
            g.drawString(font, line.text, px + 8 + line.indent, areaTop + (i - scroll) * 10, line.color, false);
        }
        g.disableScissor();
        if (lines.size() > visible) {
            int barH = bottom - areaTop - 4, thumb = Math.max(10, barH * visible / lines.size());
            int y = areaTop + (barH - thumb) * scroll / Math.max(1, lines.size() - visible);
            g.fill(px + pw - 3, areaTop, px + pw - 1, areaTop + barH, 0x33FFFFFF);
            g.fill(px + pw - 3, y, px + pw - 1, y + thumb, GOLD);
        }
    }

    private static String progress(Chapter c, StoryJournalPayload j) {
        if (j == null) return "";
        return switch (c) {
            case SIGNAL -> switch (j.signalPhase()) {
                case UNHEARD -> "search for the relay";
                case RECEIVED, SEARCHING -> "relay ruins";
                case ISOLATE, COOL -> "stasis annex";
                case RESCUE -> "extraction";
                case COMPLETE -> "both sleepers safe";
            };
            case RECORDS -> j.records().size() + " of " + j.recordTotal() + " found";
            case DREAMS -> j.memories().size() + " of " + j.memoryTotal() + " heard";
            case WAY_BACK -> Integer.bitCount(j.temporalMilestones() & 31) + " of " + MILESTONES.length;
            case WATCHMAKER -> switch (j.sylarChapter()) {
                case 0 -> "nothing yet";
                case 1 -> "the prisoner";
                case 2 -> "his cell is open";
                case 3 -> "beaten";
                default -> "beaten " + j.sylarDefeats() + "x";
            };
        };
    }

    private record Line(FormattedCharSequence text, int color, int indent) {}

    private List<Line> lines(StoryJournalPayload j, int wrap) {
        List<Line> out = new ArrayList<>();
        switch (chapter) {
            case SIGNAL -> {
                add(out, Component.literal(j.lead()), LEAD, wrap, 0);
                if (j.signalLocation() != null && j.signalPhase() != Phase.COMPLETE) {
                    gap(out);
                    add(out, Component.literal("Relay ruins  ·  X " + j.signalLocation().getX() + "  Z " + j.signalLocation().getZ()), TEXT, wrap, 0);
                }
                if (j.signalPhase() == Phase.RESCUE || j.signalPhase() == Phase.COMPLETE) {
                    gap(out);
                    add(out, Component.literal(j.signalRescued() + " of 2 sleepers safe"), TEXT, wrap, 0);
                }
            }
            case RECORDS -> {
                if (j.records().isEmpty()) add(out, Component.literal("Nothing found yet."), MUTED, wrap, 0);
                for (String record : j.records()) add(out, Component.literal("• " + record), TEXT, wrap, 4);
                int unfound = j.recordTotal() - j.records().size();
                if (unfound > 0) {
                    gap(out);
                    add(out, Component.literal(unfound + (unfound == 1 ? " record is" : " records are")
                            + " still to find in the bunker and ruins."), MUTED, wrap, 0);
                }
            }
            case DREAMS -> {
                if (j.memories().isEmpty()) add(out, Component.literal("Villagers dream of a city none of them has seen. A telepath could hear them."), MUTED, wrap, 0);
                for (int i = 0; i < j.memoryTotal(); i++) {
                    if (i < j.memories().size()) { add(out, Component.literal((i + 1) + ".  “" + j.memories().get(i) + "”"), TEXT, wrap, 0); gap(out); }
                    else if (i == j.memories().size()) add(out, Component.literal((i + 1) + ".  ???  Another mind holds the next piece."), MUTED, wrap, 0);
                }
                if (j.seenFutureCity()) { gap(out); add(out, Component.literal("You have stood in the city they dream of.").withStyle(ChatFormatting.ITALIC), LEAD, wrap, 0); }
            }
            case WAY_BACK -> {
                for (int i = 0; i < MILESTONES.length; i++) {
                    boolean done = (j.temporalMilestones() & (1 << i)) != 0;
                    add(out, Component.literal((done ? "✔  " : "○  ") + MILESTONES[i][0]).withStyle(ChatFormatting.BOLD), done ? GREEN : TEXT, wrap, 0);
                    add(out, Component.literal(MILESTONES[i][1]), done ? MUTED : TEXT, wrap, 14);
                    gap(out);
                }
            }
            case WATCHMAKER -> {
                add(out, Component.literal(switch (Math.max(0, Math.min(4, j.sylarChapter()))) {
                    case 0 -> "Nothing has happened... yet.";
                    case 1 -> "A hatch has opened in the floor of Cell 3, Gabriel Gray's old cell in the bunker's containment block. Below it is "
                            + "Level 5, where the Company kept him, and he is still there, behind glass, a hundred and three years later. "
                            + "By his cell, their watch list is being crossed out, one name a night. Stay near the people on it after dark.";
                    case 2 -> "His cell is open. Every name above yours is crossed out, and he is coming for you: at night, wherever you are, "
                            + "or at once if you go down to Level 5. Anyone near you when he takes you comes too.";
                    case 3 -> "Someone beat him in his Collection. A brass movement from his watch makes a Repair Ticket, if you want to meet him yourself.";
                    default -> "You beat him in his Collection. Break the case he draws from and his own power turns on him; "
                            + "a brass movement from his watch makes a Repair Ticket for another fight.";
                }), j.sylarChapter() == 0 ? MUTED : TEXT, wrap, 0);
                if (!j.sylarList().isEmpty()) {
                    gap(out);
                    heading(out, "THE WATCH LIST");
                    for (String row : j.sylarList()) {
                        if (row.length() < 2) continue;
                        char mark = row.charAt(0);
                        String text = row.substring(2);
                        Component line = switch (mark) {
                            case 'x', 'X', 'g' -> Component.literal(text).withStyle(ChatFormatting.STRIKETHROUGH);
                            case 'y' -> Component.literal(text + "  (you)").withStyle(ChatFormatting.BOLD);
                            default -> Component.literal(text);
                        };
                        int color = switch (mark) {
                            case 'x', 'g' -> MUTED;
                            case 'X' -> BAD;
                            default -> TEXT;
                        };
                        add(out, line, color, wrap, 4);
                    }
                }
                if (!j.sylarLearned().isEmpty()) {
                    gap(out);
                    heading(out, "HIS COLLECTION");
                    for (String id : j.sylarLearned()) {
                        ResourceLocation power = ResourceLocation.tryParse(id);
                        add(out, Component.literal("• " + (power == null ? id : Component.translatable("ability." + power.getNamespace() + "." + power.getPath()).getString())), BAD, wrap, 4);
                    }
                }
                if (j.sylarDefeats() > 0) {
                    gap(out);
                    add(out, Component.literal("You have beaten him " + j.sylarDefeats() + (j.sylarDefeats() == 1 ? " time." : " times.")), TEXT, wrap, 0);
                }
            }
        }
        return out;
    }

    private void heading(List<Line> out, String text) { out.add(new Line(Component.literal(text).withStyle(ChatFormatting.BOLD).getVisualOrderText(), HEADING, 0)); }

    private void add(List<Line> out, Component text, int color, int wrap, int indent) {
        for (FormattedCharSequence line : font.split(text, wrap - indent)) out.add(new Line(line, color, indent));
    }

    private void gap(List<Line> out) { out.add(new Line(FormattedCharSequence.EMPTY, 0, 0)); }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (int) Math.signum(scrollY) * 3;
        return true;
    }
}
