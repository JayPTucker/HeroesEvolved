package com.jayptucker.heroesevolved.gui.screen;

import com.jayptucker.heroesevolved.network.ClientJournalState;
import com.jayptucker.heroesevolved.network.JournalRequestPayload;
import com.jayptucker.heroesevolved.network.StoryJournalPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The story so far: the next lead, the records found in the bunker, the
 * ruins and the past, the Annex and the avenue at 6:14, the dreams of
 * Odessa's Last Day, the time traveller's way back to it, and the
 * watchmaker's hunt. Everything here is the player's own durable progress,
 * fetched from the server on open.
 */
public final class JournalScreen extends Screen {
    private static final int PANEL_WIDTH = 320;
    private static final int MAXIMUM_PANEL_HEIGHT = 250;
    private static final int LINE_HEIGHT = 10;

    private static final int TITLE = 0xFFFFD7DC;
    private static final int HEADING = 0xFFE0B040;
    private static final int TEXT = 0xFFE7C9CE;
    private static final int MUTED = 0xFF8A7075;
    private static final int LEAD = 0xFF60C8D0;

    private static final String[][] MILESTONES = {
            {"The Last Day", "Stand in Odessa at 6:14 and escape the light."},
            {"No One Left Behind", "Carry a companion through space, or share stopped time."},
            {"A Place to Return", "Establish a spatial anchor."},
            {"The Butterfly Effect", "Successfully rewrite fifteen seconds."},
            {"The Road Not Taken", "Enter a recorded past."}
    };

    private final Screen parent;
    private final List<Line> lines = new ArrayList<>();
    private int scroll;

    private JournalScreen(Screen parent) {
        super(Component.literal("Journal"));
        this.parent = parent;
    }

    public static void open(Screen parent) {
        PacketDistributor.sendToServer(new JournalRequestPayload());
        Minecraft.getInstance().setScreen(new JournalScreen(parent));
    }

    public void onContentsUpdated() {
        rebuildWidgets();
    }

    private int panelHeight() {
        return Math.min(MAXIMUM_PANEL_HEIGHT, height - 8);
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - panelHeight()) / 2;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(left + PANEL_WIDTH - 63, top + panelHeight() - 26, 53, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Read omen"), b -> incident(com.jayptucker.heroesevolved.expansion.IncidentActionPayload.Operation.READ)).bounds(left + 9, top + panelHeight() - 26, 76, 20).build());
        List<Omen> open = omens();
        Omen chosen = chosen(open);
        addRenderableWidget(Button.builder(Component.literal(open.size() > 1 ? "Follow: " + chosen.label : "Follow omen"), b -> follow())
                .bounds(left + 89, top + panelHeight() - 26, 80, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(open.size() > 1
                        ? "Go into the past: " + chosen.title + ". Shift-click to choose another omen."
                        : "Go into the past: " + chosen.title + ".")))
                .build());
        addRenderableWidget(Button.builder(Component.literal("Leave the past"), b -> incident(com.jayptucker.heroesevolved.expansion.IncidentActionPayload.Operation.LEAVE)).bounds(left + 173, top + panelHeight() - 26, 80, 20).build());
        buildLines();
    }

    /** The places in the past an omen leads to, in story order. */
    private enum Omen {
        ANNEX("Annex", "the Hourglass Annex, 5:50 PM", com.jayptucker.heroesevolved.expansion.IncidentActionPayload.Operation.ENTER),
        AVENUE("Avenue", "the avenue at 6:10 PM", com.jayptucker.heroesevolved.expansion.IncidentActionPayload.Operation.AVENUE),
        NIGHT_THREE("Night 3", "Sublevel 4 on the third night, 2:57 AM", com.jayptucker.heroesevolved.expansion.IncidentActionPayload.Operation.NIGHT_THREE);

        final String label, title;
        final com.jayptucker.heroesevolved.expansion.IncidentActionPayload.Operation operation;
        Omen(String label, String title, com.jayptucker.heroesevolved.expansion.IncidentActionPayload.Operation operation) {
            this.label = label; this.title = title; this.operation = operation;
        }
    }

    /** The omen Shift-click has picked; null follows the newest. */
    private Omen picked;

    private List<Omen> omens() {
        StoryJournalPayload journal = ClientJournalState.get();
        List<Omen> open = new java.util.ArrayList<>(List.of(Omen.ANNEX));
        if (journal != null && journal.avenueOpen()) open.add(Omen.AVENUE);
        if (journal != null && journal.nightOpen()) open.add(Omen.NIGHT_THREE);
        return open;
    }

    private Omen chosen(List<Omen> open) {
        return picked != null && open.contains(picked) ? picked : open.getLast();
    }

    /** Goes to the chosen omen: the newest one open, unless Shift-click picked another. */
    private void follow() {
        List<Omen> open = omens();
        Omen current = chosen(open);
        if (hasShiftDown() && open.size() > 1) {
            picked = open.get((open.indexOf(current) + 1) % open.size());
            rebuildWidgets();
            return;
        }
        incident(current.operation);
    }

    private void incident(com.jayptucker.heroesevolved.expansion.IncidentActionPayload.Operation operation) {
        PacketDistributor.sendToServer(new com.jayptucker.heroesevolved.expansion.IncidentActionPayload(operation));
    }
    private void buildLines() {
        lines.clear();
        StoryJournalPayload journal = ClientJournalState.get();
        int wrap = PANEL_WIDTH - 28;
        if (journal == null) {
            add(Component.literal("Gathering your memories..."), MUTED, wrap);
            return;
        }

        heading("Next lead");
        add(Component.literal(journal.lead()), LEAD, wrap);
        spacer();

        // The books, logs and letters found in the bunker and the ruins.
        heading("Recovered records  (" + journal.records().size() + "/" + journal.recordTotal() + ")");
        for (String record : journal.records()) {
            add(Component.literal("- " + record), TEXT, wrap);
        }
        int unfound = journal.recordTotal() - journal.records().size();
        if (unfound > 0) {
            add(Component.literal(unfound + (unfound == 1 ? " record is" : " records are")
                    + " still out there: in the bunker, in the ruins, or not written yet."), MUTED, wrap);
        }
        spacer();

        heading("The Unfinished Hour");
        add(Component.literal(journal.incident()), TEXT, wrap);
        spacer();

        // The finale: the avenue at 6:14.
        heading("The Avenue at 6:14");
        add(Component.literal(journal.avenue()), journal.avenueOpen() ? TEXT : MUTED, wrap);
        spacer();
        // After the light: the third night of the lockdown.
        heading("Night Three: Pod 14");
        add(Component.literal(journal.night()), journal.nightOpen() ? TEXT : MUTED, wrap);
        spacer();
        // Chapter one: the dreams.
        heading("Dreams of the Last Day  (" + journal.memories().size() + "/"
                + journal.memoryTotal() + ")");
        if (journal.memories().isEmpty()) {
            add(Component.literal("Villagers dream of a city none of them has seen. A telepath could hear them."),
                    MUTED, wrap);
        }
        for (int index = 0; index < journal.memoryTotal(); index++) {
            if (index < journal.memories().size()) {
                add(Component.literal((index + 1) + ". \"" + journal.memories().get(index) + "\""),
                        TEXT, wrap);
            } else if (index == journal.memories().size()) {
                add(Component.literal((index + 1) + ". ???  Another mind holds the next piece."),
                        MUTED, wrap);
            }
        }
        if (journal.seenFutureCity()) {
            add(Component.literal("You have stood in the city they dream of.")
                    .withStyle(ChatFormatting.ITALIC), 0xFF60C8D0, wrap);
        }
        spacer();

        // Chapter two: the time traveller's path.
        int reached = Integer.bitCount(journal.temporalMilestones() & 31);
        heading("The Way Back  (" + reached + "/" + MILESTONES.length + ")");
        for (int index = 0; index < MILESTONES.length; index++) {
            boolean done = (journal.temporalMilestones() & (1 << index)) != 0;
            add(Component.literal((done ? "[x] " : "[ ] ") + MILESTONES[index][0]
                    + (done ? "" : " - " + MILESTONES[index][1])), done ? TEXT : MUTED, wrap);
        }
        spacer();

        // Chapter three: the watchmaker.
        heading("The Watchmaker");
        add(Component.literal(switch (Math.max(0, Math.min(5, journal.sylarChapter()))) {
            case 0 -> "Nothing has happened... yet.";
            case 1 -> "You have heard a faint ticking that wasn't there before.";
            case 2 -> "A man in a dark coat has been watching you from a distance.";
            case 3 -> "He spoke to you. A quiet voice, a watchmaker's patience. His name was Gabriel Gray, once. He calls himself Sylar.";
            case 4 -> "He has hunted you. He studies people before he takes what they have, and every power he takes, he uses.";
            default -> "He keeps coming back for you. He remembers how you fight. You are not prey to him any more; you are the one he cannot finish.";
        }), journal.sylarChapter() == 0 ? MUTED : TEXT, wrap);
        if (!journal.sylarLearned().isEmpty()) {
            List<String> names = journal.sylarLearned().stream()
                    .map(id -> {
                        ResourceLocation power = ResourceLocation.tryParse(id);
                        return power == null ? id : Component.translatable(
                                "ability." + power.getNamespace() + "." + power.getPath()).getString();
                    })
                    .toList();
            add(Component.literal("Powers he has taken: " + String.join(", ", names)),
                    0xFFE06060, wrap);
        }
        if (journal.sylarDefeats() > 0) {
            add(Component.literal("He has been driven off " + journal.sylarDefeats()
                    + (journal.sylarDefeats() == 1 ? " time." : " times.")), TEXT, wrap);
        }
    }

    private void heading(String text) {
        lines.add(new Line(Component.literal(text).getVisualOrderText(), HEADING));
        lines.add(new Line(FormattedCharSequence.EMPTY, 0));
    }

    private void add(Component text, int colour, int wrap) {
        for (FormattedCharSequence line : font.split(text, wrap)) {
            lines.add(new Line(line, colour));
        }
    }

    private void spacer() {
        lines.add(new Line(FormattedCharSequence.EMPTY, 0));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x60000000);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - panelHeight()) / 2;
        graphics.fill(left - 1, top - 1, left + PANEL_WIDTH + 1, top + panelHeight() + 1, 0xFF6B101D);
        graphics.fill(left, top, left + PANEL_WIDTH, top + panelHeight(), 0xFF16090D);
        graphics.drawString(font, title, left + 12, top + 10, TITLE, true);

        int areaTop = top + 28;
        int areaBottom = top + panelHeight() - 32;
        int visible = (areaBottom - areaTop) / LINE_HEIGHT;
        scroll = Math.clamp(scroll, 0, Math.max(0, lines.size() - visible));

        graphics.enableScissor(left, areaTop, left + PANEL_WIDTH, areaBottom);
        for (int index = scroll; index < lines.size() && index < scroll + visible; index++) {
            Line line = lines.get(index);
            graphics.drawString(font, line.text, left + 14,
                    areaTop + (index - scroll) * LINE_HEIGHT, line.colour, false);
        }
        graphics.disableScissor();

        if (lines.size() > visible) {
            String hint = scroll + visible < lines.size() ? "scroll for more" : "end";
            graphics.drawString(font, hint, left + PANEL_WIDTH - 12 - font.width(hint),
                    top + 10, MUTED, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (int) Math.signum(scrollY) * 3;
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The panel draws its own backdrop.
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Line(FormattedCharSequence text, int colour) {
    }
}
