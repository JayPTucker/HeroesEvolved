package com.jayptucker.heroesevolved.gui.screen;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.jayptucker.heroesevolved.network.*;
import com.jayptucker.heroesevolved.skills.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/**
 * The skill tree drawn like the vanilla Advancements screen: a draggable map of framed nodes
 * joined by connector lines, with the selected skill's controls below.
 * Passive nodes use task frames, active skills goal frames and capstones challenge frames;
 * learned nodes turn gold. Purchases and equipment always wait for the server response.
 */
public final class SkillTreeScreen extends Screen {
    private static final ResourceLocation WINDOW = ResourceLocation.withDefaultNamespace("textures/gui/advancements/window.png");
    private static final int WINDOW_WIDTH = 252, WINDOW_HEIGHT = 140, INNER_X = 9, INNER_Y = 18, INNER_WIDTH = 234, INNER_HEIGHT = 113;
    private static final int FRAME = 26, COLUMN = 32, ROW = 28, BRANCH_GAP = 6;

    private final Screen parent;
    private String selected = "";
    private SkillTreeStatePayload previous;
    private Button purchase;
    private boolean pending, dragging;
    private int refresh;
    private double scrollX, scrollY, dragDistance;
    private boolean centered;
    private final List<Button> slotButtons = new ArrayList<>();
    private Layout layout = Layout.EMPTY;

    public SkillTreeScreen(Screen parent) { super(Component.translatable("screen.heroesevolved.skill_tree")); this.parent = parent; }

    public static void open(Screen parent) {
        ClientSkillTreeState.clear();
        PacketDistributor.sendToServer(new SkillTreeActionPayload(SkillTreeActionPayload.Operation.OPEN, "", AbilitySlot.PRIMARY));
        Minecraft.getInstance().setScreen(new SkillTreeScreen(parent));
    }

    // ------------------------------------------------------------------
    // Layout: a tree grown left to right from the power, like advancements
    // ------------------------------------------------------------------

    private record Placed(SkillCatalog.Node node, int x, int y, Placed parent) {}

    private record Layout(String power, List<Placed> nodes, int width, int height, int[] branchY) {
        static final Layout EMPTY = new Layout("", List.of(), 0, 0, new int[3]);
        int rootY() { return nodes.isEmpty() ? 0 : (branchY[0] + branchY[2]) / 2; }
    }

    /** Children follow their prerequisite; a parent sits level with its first child, as advancements do. */
    private static Layout layout(String power) {
        var nodes = SkillCatalog.nodes(power);
        if (nodes.isEmpty()) return new Layout(power, List.of(), FRAME, FRAME, new int[3]);
        Map<String, List<SkillCatalog.Node>> children = new HashMap<>();
        for (var node : nodes) children.computeIfAbsent(node.prerequisite(), k -> new ArrayList<>()).add(node);
        children.values().forEach(list -> list.sort(Comparator.comparingInt(SkillCatalog.Node::tier)));
        Map<String, int[]> positions = new LinkedHashMap<>();
        int[] cursor = {0}, branchY = new int[3];
        for (var opener : children.getOrDefault("", List.of())) {
            if (cursor[0] > 0) cursor[0] += BRANCH_GAP;
            branchY[Math.min(2, opener.branch())] = place(opener, 1, children, positions, cursor);
        }
        Map<String, Placed> placed = new LinkedHashMap<>();
        for (var node : nodes) link(node, positions, placed);
        int width = positions.values().stream().mapToInt(xy -> xy[0]).max().orElse(0) + FRAME;
        int height = positions.values().stream().mapToInt(xy -> xy[1]).max().orElse(0) + FRAME;
        return new Layout(power, List.copyOf(placed.values()), width, height, branchY);
    }

    /** Lays out a subtree; leaves take the next row. Returns the node's row. */
    private static int place(SkillCatalog.Node node, int depth, Map<String, List<SkillCatalog.Node>> children,
                             Map<String, int[]> positions, int[] cursor) {
        var kids = children.getOrDefault(node.id(), List.of());
        int y;
        if (kids.isEmpty()) { y = cursor[0]; cursor[0] += ROW; }
        else {
            y = place(kids.getFirst(), depth + 1, children, positions, cursor);
            for (int i = 1; i < kids.size(); i++) place(kids.get(i), depth + 1, children, positions, cursor);
        }
        positions.put(node.id(), new int[] {depth * COLUMN, y});
        return y;
    }

    private static Placed link(SkillCatalog.Node node, Map<String, int[]> positions, Map<String, Placed> placed) {
        Placed existing = placed.get(node.id());
        if (existing != null) return existing;
        Placed parent = node.prerequisite().isEmpty() ? null
                : SkillCatalog.find(node.power(), node.prerequisite()).map(n -> link(n, positions, placed)).orElse(null);
        int[] xy = positions.getOrDefault(node.id(), new int[] {0, 0});
        Placed result = new Placed(node, xy[0], xy[1], parent);
        placed.put(node.id(), result);
        return result;
    }

    // ------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------

    private int detailsHeight() { return height >= 262 ? 78 : 50; }
    private int windowLeft() { return (width - WINDOW_WIDTH) / 2; }
    private int windowTop() { return Math.max(2, (height - (WINDOW_HEIGHT + 4 + detailsHeight())) / 2); }
    private int innerLeft() { return windowLeft() + INNER_X; }
    private int innerTop() { return windowTop() + INNER_Y; }
    private int detailsTop() { return windowTop() + WINDOW_HEIGHT + 4; }
    private int canvasWidth() { return layout.width + 16; }
    private int canvasHeight() { return layout.height + 16; }

    private void clampScroll() {
        scrollX = canvasWidth() <= INNER_WIDTH ? (INNER_WIDTH - canvasWidth()) / 2.0 : Mth.clamp(scrollX, INNER_WIDTH - canvasWidth(), 0);
        scrollY = canvasHeight() <= INNER_HEIGHT ? (INNER_HEIGHT - canvasHeight()) / 2.0 : Mth.clamp(scrollY, INNER_HEIGHT - canvasHeight(), 0);
    }

    private int screenX(Placed p) { return innerLeft() + (int) scrollX + 8 + p.x; }
    private int screenY(Placed p) { return innerTop() + (int) scrollY + 8 + p.y; }

    /** Shows the start of the tree, with the power's emblem centered vertically. */
    private void centerOnRoot() {
        scrollX = 0;
        scrollY = -(layout.rootY() + FRAME / 2.0 - INNER_HEIGHT / 2.0) - 8;
        clampScroll();
    }

    // ------------------------------------------------------------------
    // Widgets
    // ------------------------------------------------------------------

    @Override protected void init() {
        previous = ClientSkillTreeState.get();
        if (!layout.power.equals(previous.power())) { layout = layout(previous.power()); centered = false; }
        if (!centered) { centerOnRoot(); centered = true; } else clampScroll();
        slotButtons.clear();
        int left = windowLeft(), bottom = detailsTop() + detailsHeight();
        purchase = addRenderableWidget(Button.builder(Component.literal("Spend points"), b -> {
            if (pending) return;
            pending = true;
            send(SkillTreeActionPayload.Operation.BUY, selected, AbilitySlot.PRIMARY);
        }).bounds(left + 6, bottom - 44, 112, 18).build());
        Button respec = addRenderableWidget(Button.builder(Component.literal("Respecialize"), b -> { pending = true; send(SkillTreeActionPayload.Operation.RESPEC, "", AbilitySlot.PRIMARY); })
                .bounds(left + 122, bottom - 44, 76, 18).build());
        respec.setTooltip(Tooltip.create(Component.literal("Refund all purchases and restore default actions. Earned points and cooldowns remain. Requires ten seconds out of combat.")));
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(left + WINDOW_WIDTH - 50, bottom - 44, 44, 18).build());
        int slotWidth = (WINDOW_WIDTH - 12) / 5;
        for (var slot : AbilitySlot.values()) {
            final AbilitySlot chosen = slot;
            Button button = Button.builder(Component.literal(key(slot)), b -> {
                if (pending) return;
                var state = ClientSkillTreeState.get();
                var node = SkillCatalog.find(state.power(), selected).orElse(null);
                String id = node != null && node.active() && state.build().learned().contains(node.id()) ? node.id() : "";
                pending = true; send(SkillTreeActionPayload.Operation.EQUIP, id, chosen);
            }).bounds(left + 6 + slot.ordinal() * slotWidth, bottom - 23, slotWidth - 2, 18).build();
            slotButtons.add(addRenderableWidget(button));
        }
        refreshButtons();
    }

    /** Selects a node by id, as a click would. Used by the UI preview fixture. */
    public void select(String id) {
        selected = id;
        layout.nodes.stream().filter(p -> p.node.id().equals(id)).findFirst().ifPresent(p -> {
            scrollX = -(p.x + FRAME / 2.0 - INNER_WIDTH / 2.0) - 8;
            scrollY = -(p.y + FRAME / 2.0 - INNER_HEIGHT / 2.0) - 8;
            clampScroll();
        });
        refreshButtons();
    }

    private void send(SkillTreeActionPayload.Operation operation, String node, AbilitySlot slot) {
        PacketDistributor.sendToServer(new SkillTreeActionPayload(operation, node, slot));
    }

    private static String key(AbilitySlot slot) {
        var mapping = switch (slot) {
            case PRIMARY -> ModKeyMappings.ABILITY_1;
            case SECONDARY -> ModKeyMappings.ABILITY_2;
            case TERTIARY -> ModKeyMappings.ABILITY_3;
            case QUATERNARY -> ModKeyMappings.ABILITY_4;
            case QUINARY -> ModKeyMappings.ABILITY_5;
        };
        return mapping.getTranslatedKeyMessage().getString();
    }

    private static boolean capstoneTaken(SkillTreeStatePayload state) {
        return SkillCatalog.nodes(state.power()).stream().anyMatch(n -> n.capstone() && state.build().learned().contains(n.id()));
    }

    private boolean canLearn(SkillCatalog.Node node, SkillTreeStatePayload state) {
        return state.awakened() && state.level() >= node.level() && !state.build().learned().contains(node.id())
                && (node.prerequisite().isEmpty() || state.build().learned().contains(node.prerequisite()))
                && (!node.capstone() || !capstoneTaken(state))
                && SkillCatalog.available(state.power(), state.build()) >= node.cost();
    }

    private String status(SkillCatalog.Node node, SkillTreeStatePayload state) {
        if (state.build().learned().contains(node.id())) return "Learned";
        if (state.level() < node.level()) return "Requires level " + node.level();
        if (!node.prerequisite().isEmpty() && !state.build().learned().contains(node.prerequisite())) return "Requires parent";
        if (node.capstone() && capstoneTaken(state)) return "One capstone only";
        if (SkillCatalog.available(state.power(), state.build()) < node.cost()) return "Need " + node.cost() + " points";
        return "Spend " + node.cost() + (node.cost() == 1 ? " point" : " points");
    }

    private void refreshButtons() {
        var state = ClientSkillTreeState.get();
        var node = SkillCatalog.find(state.power(), selected).orElse(null);
        purchase.active = !pending && node != null && canLearn(node, state);
        purchase.setMessage(Component.literal(node == null ? "Select a skill" : status(node, state)));
        for (var slot : AbilitySlot.values()) {
            Button button = slotButtons.get(slot.ordinal());
            boolean equip = node != null && node.active() && state.build().learned().contains(node.id());
            button.active = !pending && state.awakened();
            button.setMessage(Component.literal((equip ? "Equip " : "Reset ") + key(slot)));
            String current = state.build().slots().get(slot.name());
            String name = SkillCatalog.find(state.power(), current == null ? "" : current).map(SkillCatalog.Node::name).orElse("Default power skill");
            button.setTooltip(Tooltip.create(Component.literal(key(slot) + ": " + name + ". "
                    + (equip ? "Assign " + node.name() + " to this key." : "Restore this key's original power skill."))));
        }
    }

    @Override public void tick() {
        var state = ClientSkillTreeState.get();
        if (state != previous) {
            pending = false;
            if (!state.power().equals(previous.power())) { selected = ""; rebuildWidgets(); }
            previous = state; refreshButtons();
        }
        if (++refresh % 60 == 0 && minecraft.getConnection() != null)
            send(SkillTreeActionPayload.Operation.OPEN, "", AbilitySlot.PRIMARY);
    }

    // ------------------------------------------------------------------
    // Input: drag to pan, wheel to scroll, click a node
    // ------------------------------------------------------------------

    private boolean overMap(double x, double y) {
        return x >= innerLeft() && x < innerLeft() + INNER_WIDTH && y >= innerTop() && y < innerTop() + INNER_HEIGHT;
    }

    private Placed nodeAt(double x, double y) {
        if (!overMap(x, y)) return null;
        for (Placed p : layout.nodes)
            if (x >= screenX(p) && x < screenX(p) + FRAME && y >= screenY(p) && y < screenY(p) + FRAME) return p;
        return null;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0) {
            if (overMap(x, y)) { dragging = true; dragDistance = 0; return true; }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging && button == 0) {
            scrollX += dx; scrollY += dy; dragDistance += Math.abs(dx) + Math.abs(dy);
            clampScroll();
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (dragging && button == 0) {
            dragging = false;
            // A click that barely moved selects; a real drag only pans.
            Placed p = dragDistance < 3 ? nodeAt(x, y) : null;
            if (p != null) { selected = p.node.id(); refreshButtons(); }
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (overMap(x, y)) {
            if (hasShiftDown()) scrollX += sy * 16; else scrollY += sy * 16;
            scrollX += sx * 16;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(x, y, sx, sy);
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Screen.render invokes this before its widgets. This screen draws its own
        // dimmed backdrop first, so the default blur would blur that text.
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xA0050A10);
        var state = ClientSkillTreeState.get();
        if (!layout.power.equals(state.power())) { layout = layout(state.power()); centerOnRoot(); }
        int left = windowLeft(), top = windowTop();

        renderMap(g, state, mouseX, mouseY);
        g.blit(WINDOW, left, top, 0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
        String power = state.power().isEmpty() ? "No power discovered" : Component.translatable("ability.heroesevolved." + state.power()).getString();
        g.drawString(font, power, left + 8, top + 6, 0x404040, false);
        if (state.awakened()) {
            String summary = SkillCatalog.available(state.power(), state.build()) + " points  Lv " + state.level();
            g.drawString(font, summary, left + WINDOW_WIDTH - 8 - font.width(summary), top + 6, 0x404040, false);
        }
        renderDetails(g, state);
        super.render(g, mouseX, mouseY, partial);

        Placed hovered = dragging ? null : nodeAt(mouseX, mouseY);
        if (hovered != null) g.renderTooltip(font, tooltip(hovered.node, state), mouseX, mouseY);
        else if (overMap(mouseX, mouseY) && !layout.nodes.isEmpty() && rootHovered(mouseX, mouseY))
            g.renderTooltip(font, font.split(Component.translatable("ability.heroesevolved." + state.power() + ".passive"), 200), mouseX, mouseY);
    }

    private void renderMap(GuiGraphics g, SkillTreeStatePayload state, int mouseX, int mouseY) {
        int left = innerLeft(), top = innerTop();
        g.enableScissor(left, top, left + INNER_WIDTH, top + INNER_HEIGHT);
        ResourceLocation tile = background(state.power());
        int offsetX = Math.floorMod((int) scrollX, 16), offsetY = Math.floorMod((int) scrollY, 16);
        for (int x = -16; x < INNER_WIDTH + 16; x += 16)
            for (int y = -16; y < INNER_HEIGHT + 16; y += 16)
                g.blit(tile, left + x + offsetX, top + y + offsetY, 0, 0, 16, 16, 16, 16);
        g.fill(left, top, left + INNER_WIDTH, top + INNER_HEIGHT, 0x50000000);

        if (state.power().isEmpty() || layout.nodes.isEmpty()) {
            g.drawWordWrap(font, Component.literal("Discover a power to reveal its skill tree. Each power has its own branches, purchases and loadout."),
                    left + 12, top + 40, INNER_WIDTH - 24, 0xFFE0E0E0);
            g.disableScissor();
            return;
        }
        int rootX = left + (int) scrollX + 8, rootY = top + (int) scrollY + 8 + layout.rootY();
        // Connectors first: black outline, then the white line on top, as advancements draw them.
        for (boolean outline : new boolean[] {true, false}) {
            for (Placed p : layout.nodes) {
                int fromX = p.parent == null ? rootX : screenX(p.parent), fromY = p.parent == null ? rootY : screenY(p.parent);
                connect(g, fromX + FRAME / 2, fromY + FRAME / 2, screenX(p) + FRAME / 2, screenY(p) + FRAME / 2, outline,
                        state.build().learned().contains(p.node.id()));
            }
        }
        frame(g, "challenge", state.awakened(), rootX, rootY);
        g.renderFakeItem(emblem(state.power()), rootX + 5, rootY + 5);
        for (Placed p : layout.nodes) {
            int x = screenX(p), y = screenY(p);
            boolean learned = state.build().learned().contains(p.node.id());
            frame(g, p.node.capstone() ? "challenge" : p.node.active() ? "goal" : "task", learned, x, y);
            g.renderFakeItem(icon(p.node), x + 5, y + 5);
            if (!learned && !canLearn(p.node, state)) g.fill(x + 2, y + 2, x + FRAME - 2, y + FRAME - 2, 0x70000000);
            else if (!learned && (refresh / 10) % 2 == 0) g.renderOutline(x, y, FRAME, FRAME, 0x90FFFFFF);
            if (selected.equals(p.node.id())) g.renderOutline(x - 1, y - 1, FRAME + 2, FRAME + 2, 0xFFFFE066);
        }
        g.disableScissor();
    }

    private boolean rootHovered(double x, double y) {
        int rootX = innerLeft() + (int) scrollX + 8, rootY = innerTop() + (int) scrollY + 8 + layout.rootY();
        return x >= rootX && x < rootX + FRAME && y >= rootY && y < rootY + FRAME;
    }

    private static void frame(GuiGraphics g, String type, boolean obtained, int x, int y) {
        g.blitSprite(ResourceLocation.withDefaultNamespace("advancements/" + type + "_frame_" + (obtained ? "obtained" : "unobtained")), x, y, FRAME, FRAME);
    }

    /** An elbow from parent to child: across, down, then across, like vanilla's connectivity lines. */
    private static void connect(GuiGraphics g, int x1, int y1, int x2, int y2, boolean outline, boolean learned) {
        int mid = x2 - COLUMN / 2;
        int color = outline ? 0xFF000000 : learned ? 0xFFFFD541 : 0xFFFFFFFF;
        int pad = outline ? 1 : 0;
        g.fill(x1, y1 - pad, mid + 1 + pad, y1 + 1 + pad, color);
        g.fill(mid - pad, Math.min(y1, y2) - pad, mid + 1 + pad, Math.max(y1, y2) + 1 + pad, color);
        g.fill(mid - pad, y2 - pad, x2, y2 + 1 + pad, color);
    }

    private void renderDetails(GuiGraphics g, SkillTreeStatePayload state) {
        int left = windowLeft(), top = detailsTop(), h = detailsHeight();
        g.fill(left, top, left + WINDOW_WIDTH, top + h, 0xFF000000);
        g.fill(left + 1, top + 1, left + WINDOW_WIDTH - 1, top + h - 1, 0xFFC6C6C6);
        g.fill(left + 1, top + 1, left + WINDOW_WIDTH - 1, top + 2, 0xFFFFFFFF);
        g.fill(left + 1, top + h - 2, left + WINDOW_WIDTH - 1, top + h - 1, 0xFF555555);
        if (h < 70) return;
        var node = SkillCatalog.find(state.power(), selected).orElse(null);
        Component line = !state.awakened() ? Component.literal("Your power is dormant. Skill points become usable when it awakens.")
                : node == null ? Component.literal("Drag to explore. Click a skill to select it, then spend points or equip it to a key.")
                : Component.literal(node.name()).withStyle(ChatFormatting.BOLD).append(Component.literal("  " + node.description()).withStyle(ChatFormatting.RESET));
        var lines = font.split(line, WINDOW_WIDTH - 12);
        for (int i = 0; i < Math.min(3, lines.size()); i++) g.drawString(font, lines.get(i), left + 6, top + 5 + i * 9, 0x404040, false);
        // Longer descriptions continue in the node's hover tooltip.
        if (lines.size() > 3) g.drawString(font, "...", left + WINDOW_WIDTH - 6 - font.width("..."), top + 23, 0x404040, false);
    }

    private List<FormattedCharSequence> tooltip(SkillCatalog.Node node, SkillTreeStatePayload state) {
        boolean learned = state.build().learned().contains(node.id());
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.literal(node.name()).withStyle(learned ? ChatFormatting.GOLD : node.capstone() ? ChatFormatting.LIGHT_PURPLE
                : node.active() ? ChatFormatting.GREEN : ChatFormatting.WHITE).getVisualOrderText());
        lines.add(Component.literal(SkillCatalog.branchName(node.power(), node.branch()) + "  "
                + (node.capstone() ? "Capstone" : node.active() ? "Active skill" : "Passive")).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        lines.addAll(font.split(Component.literal(node.description()).withStyle(ChatFormatting.GRAY), 200));
        if (node.active()) lines.add(Component.literal(node.energy() + " stamina / " + node.cooldown() / 20.0 + "s cooldown")
                .withStyle(ChatFormatting.AQUA).getVisualOrderText());
        String requirement = "Level " + node.level() + "  " + node.cost() + (node.cost() == 1 ? " point" : " points");
        if (!node.prerequisite().isEmpty()) requirement += "  after " + SkillCatalog.find(node.power(), node.prerequisite()).map(SkillCatalog.Node::name).orElse("its parent");
        lines.add(Component.literal(requirement).withStyle(ChatFormatting.DARK_AQUA).getVisualOrderText());
        lines.add(Component.literal(status(node, state)).withStyle(learned ? ChatFormatting.GOLD
                : canLearn(node, state) ? ChatFormatting.GREEN : ChatFormatting.RED).getVisualOrderText());
        return lines;
    }

    // ------------------------------------------------------------------
    // Icons and backgrounds
    // ------------------------------------------------------------------

    private static final Map<String, String> BACKGROUNDS = Map.ofEntries(
            Map.entry("quake", "stone"), Map.entry("flight", "white_wool"), Map.entry("cryokinesis", "packed_ice"),
            Map.entry("electrokinesis", "oxidized_copper"), Map.entry("telekinesis", "purpur_block"),
            Map.entry("telepathy", "amethyst_block"), Map.entry("regeneration", "red_terracotta"),
            Map.entry("time_manipulation", "end_stone"), Map.entry("radiation", "orange_terracotta"),
            Map.entry("sonic_resonance", "yellow_terracotta"), Map.entry("superspeed", "gold_block"),
            Map.entry("precognition", "dark_oak_planks"), Map.entry("pyrokinesis", "netherrack"));

    private static ItemStack emblem(String power) { return PowerIcons.emblem(power); }

    private static ResourceLocation background(String power) {
        return ResourceLocation.withDefaultNamespace("textures/block/" + BACKGROUNDS.getOrDefault(power, "stone") + ".png");
    }

    private static ItemStack icon(SkillCatalog.Node node) { return PowerIcons.node(node); }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
