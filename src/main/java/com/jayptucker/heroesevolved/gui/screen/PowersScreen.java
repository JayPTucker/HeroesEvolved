package com.jayptucker.heroesevolved.gui.screen;

import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.jayptucker.heroesevolved.network.*;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Abilities screen, which is also the skill tree: every ability the player has or can earn on one page.
 * The power's own five abilities run down the left; each of the three branches is drawn as its real tree
 * (a root that forks into an upgrade path and a technique path ending in the capstone). Selecting a hex fills
 * the card on the right with what it does and what it needs, and the card's buttons learn the skill or put it
 * on a key. Level, Mastery, unspent points and stamina run along the bottom.
 */
public final class PowersScreen extends Screen {
    private static final ResourceLocation HEX_OPEN = hex("open"), HEX_LOCKED = hex("locked"), HEX_LEARNED = hex("learned"),
            HEX_POWER = hex("power"), HEX_SELECTED = hex("selected");
    private static final int HEX_W = 26, HEX_H = 30;
    private static final int GOLD = 0xFFF2D27A, TEXT = 0xFFEDE3D6, MUTED = 0xFFB8A99A, DIM = 0xFF77695E, RED = 0xFFE07A6A,
            GREEN = 0xFF9BD67A, ENERGY = 0xFF57B6E0, LINE = 0xFF8A7A6C;

    private enum Kind { POWER, CORE, ACTIVE, PASSIVE, CAPSTONE }
    /** One hex. {@code key} identifies it across rebuilds; {@code parent} is the hex its connector comes from. */
    private record Cell(String key, int x, int y, Kind kind, Component name, Component description, ItemStack icon,
                        boolean owned, boolean available, List<Component> facts, Component status, String nodeId,
                        String parent, AbilitySlot slot) {}
    /** A clickable area drawn in the card or the bottom bar. */
    private record Action(int x, int y, int w, int h, Component label, boolean enabled, boolean highlighted, Runnable run) {}

    private final List<Cell> cells = new ArrayList<>();
    private final List<Action> actions = new ArrayList<>();
    private String selectedKey = "power";
    private int refresh;
    private boolean pending;
    private SkillTreeStatePayload seenState;

    private final net.minecraft.client.gui.screens.Screen parent;

    public PowersScreen() { this(null); }

    /** Opened from the inventory's eclipse button; closing returns there. */
    public PowersScreen(net.minecraft.client.gui.screens.Screen parent) {
        super(Component.translatable("screen.heroesevolved.powers"));
        this.parent = parent;
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    private static ResourceLocation hex(String name) { return ResourceLocation.fromNamespaceAndPath("heroesevolved", "hex/" + name); }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private static final int LANE = 32, BRANCH_W = LANE + HEX_W, BRANCH_GAP = 16, TREE_DEPTH = 4;

    private int contentWidth() { return Math.min(width - 16, 500); }
    private int left() { return (width - contentWidth()) / 2; }
    private int rowSpacing() { return Mth.clamp((height - 110) / 6, 31, 40); }
    /** The grid, card and bottom bar are centred vertically as one block. */
    private int gridTop() { return Math.max(40, (height - (14 + 6 * rowSpacing() + 44)) / 2); }
    private int powerX() { return left() + 8; }
    private int branchX(int branch) { return powerX() + HEX_W + 24 + branch * (BRANCH_W + BRANCH_GAP); }
    private int cellY(int row) { return gridTop() + 14 + row * rowSpacing(); }
    /** Tree depths spread over the same height as the power column's six rows. */
    private int depthY(int depth) { return cellY(0) + depth * 5 * rowSpacing() / (TREE_DEPTH - 1); }
    private int cardLeft() { return branchX(2) + BRANCH_W + 16; }
    private int cardRight() { return left() + contentWidth(); }
    private int gridBottom() { return cellY(5) + HEX_H; }
    private int bottomY() { return gridBottom() + 14; }

    private static final String[] TABS = {"ABILITIES", "JOURNAL", "SETTINGS", "CLOSE"};

    private int tabX(int index) {
        int total = 0;
        for (String tab : TABS) total += font.width(tab) + 18;
        int x = (width - total) / 2;
        for (int i = 0; i < index; i++) x += font.width(TABS[i]) + 18;
        return x;
    }

    @Override protected void init() {
        if (minecraft.getConnection() != null)
            PacketDistributor.sendToServer(new SkillTreeActionPayload(SkillTreeActionPayload.Operation.OPEN, "", AbilitySlot.PRIMARY));
        if (minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(2)) {
            addRenderableWidget(Button.builder(Component.literal("Admin"), b -> PacketDistributor.sendToServer(
                            AdminActionPayload.of(AdminActionPayload.Action.OPEN, minecraft.player.getUUID())))
                    .bounds(width - 52, 4, 46, 16).build());
        }
    }

    // ------------------------------------------------------------------
    // Data: every ability as a cell
    // ------------------------------------------------------------------

    private void rebuild() {
        cells.clear();
        PlayerPowerSyncPayload sync = ClientPowerState.getSnapshot();
        AbilitySnapshot power = sync.abilities().stream().findFirst().orElse(null);
        if (power == null) return;
        String path = power.abilityId().getPath();
        int level = power.level();
        boolean awake = power.unlocked();
        var tree = ClientSkillTreeState.get();
        boolean treeFresh = tree.power().equals(path);
        var learned = treeFresh ? tree.build().learned() : java.util.Set.<String>of();
        Map<String, String> slots = treeFresh ? tree.build().slots() : Map.of();
        int points = treeFresh && awake ? SkillCatalog.available(path, tree.build()) : 0;

        cells.add(new Cell("power", powerX(), cellY(0), Kind.POWER, Component.translatable("ability." + power.abilityId().getNamespace() + "." + path),
                Component.translatable("ability." + power.abilityId().getNamespace() + "." + path + ".passive"),
                PowerIcons.emblem(path), awake, awake, List.of(Component.literal("Your power's passive effects, always on.")),
                Component.translatable(awake ? "screen.heroesevolved.awakened" : "screen.heroesevolved.dormant")
                        .withStyle(awake ? ChatFormatting.GREEN : ChatFormatting.RED), "", "", null));

        var ability = AbilityRegistry.ABILITIES.get(power.abilityId());
        int row = 1;
        for (AbilitySlot slot : AbilitySlot.values()) {
            AbilityAction action = ability == null ? null : ability.actions().get(slot);
            if (action == null) { row++; continue; }
            String id = action.definition().id().getPath();
            boolean unlocked = awake && level >= action.definition().unlockLevel();
            AbilityActionSnapshot onKey = sync.actions().stream().filter(a -> a.slot() == slot).findFirst().orElse(null);
            boolean equipped = onKey != null && onKey.displayNameKey().equals(action.definition().displayNameKey());
            int energy = equipped ? onKey.energyCost() : action.energyCost(Math.max(1, level));
            int cooldown = equipped ? (int) onKey.cooldownTicks() : action.cooldownTicks(Math.max(1, level));
            List<Component> facts = new ArrayList<>();
            facts.add(fact("Key", key(slot)));
            facts.add(fact("Stamina", energy <= 0 ? "free" : String.valueOf(energy)));
            facts.add(fact("Cooldown", seconds(cooldown)));
            facts.add(fact("Unlocks", "Level " + action.definition().unlockLevel()));
            Component status = !awake ? Component.literal("Awakens with your power").withStyle(ChatFormatting.RED)
                    : !unlocked ? Component.literal("Unlocks at Level " + action.definition().unlockLevel()).withStyle(ChatFormatting.RED)
                    : equipped ? Component.literal("Ready on " + key(slot)).withStyle(ChatFormatting.GREEN)
                    : Component.literal("Replaced on " + key(slot) + " by ").append(Component.translatable(onKey.displayNameKey()))
                            .append(". Restore it below.").withStyle(ChatFormatting.GOLD);
            cells.add(new Cell("core:" + slot.name(), powerX(), cellY(row++), Kind.CORE, Component.translatable(action.definition().displayNameKey()),
                    Component.translatable(action.definition().displayNameKey() + ".desc"), PowerIcons.action(path, id),
                    unlocked, unlocked, facts, status, "", "", slot));
        }

        boolean capstoneTaken = SkillCatalog.nodes(path).stream().anyMatch(n -> n.capstone() && learned.contains(n.id()));
        Map<String, double[]> place = treePlaces(path);
        for (SkillCatalog.Node node : SkillCatalog.nodes(path)) {
            boolean has = learned.contains(node.id());
            boolean prerequisite = node.prerequisite().isEmpty() || learned.contains(node.prerequisite());
            boolean canLearn = awake && !has && level >= node.level() && prerequisite && points >= node.cost()
                    && !(node.capstone() && capstoneTaken);
            List<Component> facts = new ArrayList<>();
            facts.add(fact("Branch", SkillCatalog.branchName(path, node.branch())));
            if (node.active()) {
                facts.add(fact("Stamina", String.valueOf(node.energy())));
                facts.add(fact("Cooldown", seconds(node.cooldown())));
            }
            facts.add(fact("Cost", node.cost() + (node.cost() == 1 ? " point" : " points")));
            facts.add(fact("Needs", "Level " + node.level() + (node.prerequisite().isEmpty() ? ""
                    : ", " + SkillCatalog.find(path, node.prerequisite()).map(SkillCatalog.Node::name).orElse("parent"))));
            String keyOf = slots.entrySet().stream().filter(e -> e.getValue().equals(node.id())).map(e -> key(AbilitySlot.valueOf(e.getKey()))).findFirst().orElse(null);
            Component status = has ? (node.active() ? (keyOf != null ? Component.literal("Learned. Ready on " + keyOf).withStyle(ChatFormatting.GREEN)
                            : Component.literal("Learned. Put it on a key below.").withStyle(ChatFormatting.GOLD))
                            : Component.literal("Learned").withStyle(ChatFormatting.GREEN))
                    : canLearn ? Component.literal("Ready to learn").withStyle(ChatFormatting.AQUA)
                    : node.capstone() && capstoneTaken ? Component.literal("You already chose a capstone").withStyle(ChatFormatting.RED)
                    : level < node.level() ? Component.literal("Reach Level " + node.level()).withStyle(ChatFormatting.RED)
                    : !prerequisite ? Component.literal("Learn its parent first").withStyle(ChatFormatting.RED)
                    : Component.literal("Needs " + node.cost() + " points").withStyle(ChatFormatting.RED);
            Kind kind = node.capstone() ? Kind.CAPSTONE : node.active() ? Kind.ACTIVE : Kind.PASSIVE;
            double[] at = place.get(node.id());
            cells.add(new Cell(node.id(), branchX(node.branch()) + (int) Math.round(at[0] * LANE), depthY((int) at[1]), kind,
                    Component.literal(node.name()), Component.literal(node.description()),
                    PowerIcons.node(node), has, canLearn, facts, status, node.id(), node.prerequisite(), null));
        }
    }

    /**
     * Where each node sits in its branch: {lane, depth}. Depth is the distance from the branch root; every
     * leaf path gets its own lane, left to right in catalog order, and a parent sits centred over its children.
     */
    private static Map<String, double[]> treePlaces(String power) {
        Map<String, double[]> places = new java.util.HashMap<>();
        for (int branch = 0; branch < 3; branch++) {
            final int b = branch;
            List<SkillCatalog.Node> nodes = SkillCatalog.nodes(power).stream().filter(n -> n.branch() == b).toList();
            int[] nextLane = {0};
            for (SkillCatalog.Node root : nodes) if (root.prerequisite().isEmpty()) place(root, nodes, 0, nextLane, places);
        }
        return places;
    }

    private static double place(SkillCatalog.Node node, List<SkillCatalog.Node> nodes, int depth, int[] nextLane, Map<String, double[]> places) {
        List<SkillCatalog.Node> children = nodes.stream().filter(n -> n.prerequisite().equals(node.id())).toList();
        double lane;
        if (children.isEmpty()) lane = nextLane[0]++;
        else {
            double sum = 0;
            for (SkillCatalog.Node child : children) sum += place(child, nodes, depth + 1, nextLane, places);
            lane = sum / children.size();
        }
        places.put(node.id(), new double[] {lane, depth});
        return lane;
    }

    private static Component fact(String label, String value) {
        return Component.literal(label + "  ").withStyle(ChatFormatting.GRAY).append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    private static String seconds(long ticks) {
        if (ticks <= 0) return "none";
        double s = ticks / 20.0;
        return s == Math.floor(s) ? (int) s + "s" : String.format("%.1fs", s);
    }

    private static String key(AbilitySlot slot) {
        KeyMapping mapping = switch (slot) {
            case PRIMARY -> ModKeyMappings.ABILITY_1;
            case SECONDARY -> ModKeyMappings.ABILITY_2;
            case TERTIARY -> ModKeyMappings.ABILITY_3;
            case QUATERNARY -> ModKeyMappings.ABILITY_4;
            case QUINARY -> ModKeyMappings.ABILITY_5;
        };
        return mapping.getTranslatedKeyMessage().getString().toUpperCase();
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    private Cell cellAt(double x, double y) {
        for (Cell cell : cells)
            if (x >= cell.x && x < cell.x + HEX_W && y >= cell.y && y < cell.y + HEX_H) return cell;
        return null;
    }

    /** Selects a hex by node id (or "power", "core:<SLOT>"), as a click would. Used by the UI preview fixture. */
    public void select(String key) { selectedKey = key; }

    private Cell selected() {
        return cells.stream().filter(c -> c.key.equals(selectedKey)).findFirst().orElse(cells.isEmpty() ? null : cells.getFirst());
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        for (int i = 0; i < TABS.length; i++) {
            int tx = tabX(i);
            if (y >= 8 && y < 22 && x >= tx - 4 && x < tx + font.width(TABS[i]) + 4) {
                switch (i) {
                    case 1 -> JournalScreen.open(this);
                    case 2 -> minecraft.setScreen(new EffectsSettingsScreen(this));
                    case 3 -> onClose();
                    default -> { }
                }
                return true;
            }
        }
        for (Action action : actions)
            if (x >= action.x && x < action.x + action.w && y >= action.y && y < action.y + action.h) {
                if (action.enabled && !pending) {
                    minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1F));
                    action.run.run();
                }
                return true;
            }
        Cell cell = cellAt(x, y);
        if (cell != null) {
            // Clicking a skill that is ready to learn a second time learns it, like pressing LEARN.
            if (cell.key.equals(selectedKey) && cell.available && !cell.owned && !cell.nodeId.isEmpty() && !pending) send(SkillTreeActionPayload.Operation.BUY, cell.nodeId, AbilitySlot.PRIMARY);
            selectedKey = cell.key;
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    private void send(SkillTreeActionPayload.Operation operation, String node, AbilitySlot slot) {
        if (minecraft.getConnection() == null) return;
        pending = true;
        PacketDistributor.sendToServer(new SkillTreeActionPayload(operation, node, slot));
    }

    @Override public void tick() {
        // Purchases and key changes wait for the server's answer; any new state ends the wait.
        if (ClientSkillTreeState.get() != seenState) { seenState = ClientSkillTreeState.get(); pending = false; }
        if (++refresh % 60 == 0 && minecraft.getConnection() != null)
            PacketDistributor.sendToServer(new SkillTreeActionPayload(SkillTreeActionPayload.Operation.OPEN, "", AbilitySlot.PRIMARY));
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // Warm dark backdrop with a vignette, drawn in render() so text stays sharp.
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        rebuild();
        g.fillGradient(0, 0, width, height, 0xE8140A07, 0xF0281008);
        g.fillGradient(0, height - height / 3, width, height, 0x00000000, 0x80000000);
        renderTabs(g, mouseX, mouseY);

        if (cells.isEmpty()) {
            g.drawCenteredString(font, Component.literal("NO POWER YET").withStyle(ChatFormatting.BOLD), width / 2, height / 2 - 30, GOLD);
            int y = height / 2 - 12;
            for (FormattedCharSequence line : font.split(Component.translatable("screen.heroesevolved.no_power"), Math.min(320, width - 40)))
                { g.drawCenteredString(font, line, width / 2, y, TEXT); y += 11; }
            renderBottom(g, null);
            super.render(g, mouseX, mouseY, partial);
            return;
        }

        // Column headings, like the screenshot's PROWLER / BRAVE / FORAGER.
        PlayerPowerSyncPayload sync = ClientPowerState.getSnapshot();
        String path = sync.abilities().getFirst().abilityId().getPath();
        heading(g, powerX() + HEX_W / 2, HEX_W + 20, "POWER", GOLD);
        for (int b = 0; b < 3; b++) heading(g, branchX(b) + BRANCH_W / 2, BRANCH_W + BRANCH_GAP - 4, SkillCatalog.branchName(path, b).toUpperCase(), TEXT);

        // Elbow connectors from each skill to its parent, lit once the child is learned.
        for (Cell cell : cells) {
            if (cell.parent.isEmpty()) continue;
            Cell parent = cells.stream().filter(c -> c.key.equals(cell.parent)).findFirst().orElse(null);
            if (parent == null) continue;
            int color = cell.owned ? 0xFFD08A4A : LINE;
            int px = parent.x + HEX_W / 2, cx = cell.x + HEX_W / 2, top = parent.y + HEX_H - 2, bottom = cell.y + 2;
            int mid = (parent.y + HEX_H + cell.y) / 2;
            g.fill(px - 1, top, px + 1, mid + 1, color);
            g.fill(Math.min(px, cx) - 1, mid - 1, Math.max(px, cx) + 1, mid + 1, color);
            g.fill(cx - 1, mid - 1, cx + 1, bottom, color);
        }
        int sepX = branchX(0) - 13;
        g.fill(sepX, gridTop() + 10, sepX + 1, gridBottom(), 0x55FFFFFF);

        Cell hovered = cellAt(mouseX, mouseY);
        Cell selected = selected();
        for (Cell cell : cells) {
            int x = cell.x, y = cell.y;
            ResourceLocation frame = cell.kind == Kind.POWER ? HEX_POWER : cell.owned ? HEX_LEARNED : cell.available ? HEX_OPEN : HEX_LOCKED;
            g.blitSprite(frame, x, y, HEX_W, HEX_H);
            g.renderFakeItem(cell.icon, x + 5, y + 7);
            if (!cell.owned && !cell.available) g.fill(x + 4, y + 7, x + 22, y + 23, 0x88201A16);
            if (cell.kind == Kind.CAPSTONE) g.drawString(font, "✦", x + HEX_W - 7, y - 1, GOLD, true);
            // Skills you can learn right now pulse gently.
            if (!cell.owned && cell.available && !cell.nodeId.isEmpty() && (refresh / 10) % 2 == 0) g.blitSprite(HEX_SELECTED, x, y, HEX_W, HEX_H);
            if (cell == hovered || cell == selected) g.blitSprite(HEX_SELECTED, x, y, HEX_W, HEX_H);
        }
        // Small key letters under the abilities that are on a key right now.
        for (Cell cell : cells) {
            String keyLabel = keyLabel(cell);
            if (keyLabel != null) {
                int x = cell.x + HEX_W - 4, y = cell.y + HEX_H - 8;
                g.fill(x - 1, y - 1, x + font.width(keyLabel) + 1, y + 8, 0xDD000000);
                g.drawString(font, keyLabel, x, y, GOLD, false);
            }
        }

        actions.clear();
        renderCard(g, selected, mouseX, mouseY);
        renderBottom(g, sync.abilities().getFirst());
        renderActions(g, mouseX, mouseY);
        if (hovered != null && hovered != selected) g.renderTooltip(font, hovered.name, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partial);
    }

    private String keyLabel(Cell cell) {
        if (!cell.owned) return null;
        for (Component fact : cell.facts) {
            String text = fact.getString();
            if (cell.kind == Kind.CORE && text.startsWith("Key") && cell.status.getString().startsWith("Ready")) return text.substring(3).strip();
        }
        if (cell.status.getString().startsWith("Learned. Ready on ")) return cell.status.getString().substring("Learned. Ready on ".length());
        return null;
    }

    private void heading(GuiGraphics g, int center, int room, String text, int color) {
        float scale = Math.min(1F, room / (float) Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().translate(center, gridTop() + (1 - scale) * 4, 0);
        g.pose().scale(scale, scale, 1);
        g.drawCenteredString(font, text, 0, 0, color);
        g.pose().popPose();
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < TABS.length; i++) {
            int x = tabX(i);
            boolean hover = mouseY >= 8 && mouseY < 22 && mouseX >= x - 4 && mouseX < x + font.width(TABS[i]) + 4;
            g.drawString(font, TABS[i], x, 10, i == 0 ? GOLD : hover ? TEXT : MUTED, false);
            if (i == 0) {
                int center = x + font.width(TABS[i]) / 2;
                g.fill(center - 6, 22, center + 6, 23, GOLD);
                g.fill(center - 3, 24, center + 3, 25, GOLD);
            }
        }
        g.fill(left(), 30, left() + contentWidth(), 31, 0x44FFFFFF);
    }

    private void renderCard(GuiGraphics g, Cell cell, int mouseX, int mouseY) {
        if (cell == null) return;
        int x = cardLeft(), right = cardRight(), y = gridTop() + 6, width = right - x;
        g.fill(x, y, right, gridBottom(), 0x66000000);
        g.fill(x, y, right, y + 1, 0x66F2D27A);
        // Big icon in a hexagon.
        g.pose().pushPose();
        g.pose().translate(x + 8, y + 8, 0);
        g.pose().scale(1.6F, 1.6F, 1);
        g.blitSprite(cell.kind == Kind.POWER ? HEX_POWER : cell.owned ? HEX_LEARNED : cell.available ? HEX_OPEN : HEX_LOCKED, 0, 0, HEX_W, HEX_H);
        g.renderFakeItem(cell.icon, 5, 7);
        g.pose().popPose();
        // Name, large.
        int textX = x + 58;
        g.pose().pushPose();
        g.pose().translate(textX, y + 12, 0);
        g.pose().scale(1.4F, 1.4F, 1);
        var nameLines = font.split(cell.name.copy().withStyle(ChatFormatting.BOLD), (int) ((right - textX - 6) / 1.4F));
        for (int i = 0; i < Math.min(2, nameLines.size()); i++) g.drawString(font, nameLines.get(i), 0, i * 10, TEXT, true);
        g.pose().popPose();
        String kind = switch (cell.kind) {
            case POWER -> "Passive power";
            case CORE -> "Core ability";
            case ACTIVE -> "Active skill";
            case PASSIVE -> "Passive skill";
            case CAPSTONE -> "Capstone (one per build)";
        };
        g.drawString(font, kind, textX, y + 12 + Math.min(2, nameLines.size()) * 14 + 2, MUTED, false);

        int cursor = y + 60;
        // Facts in two columns.
        for (int i = 0; i < cell.facts.size(); i++) {
            int fx = x + 8 + (i % 2) * (width / 2), fy = cursor + (i / 2) * 11;
            g.drawString(font, font.plainSubstrByWidth(cell.facts.get(i).getString(), width / 2 - 10).isEmpty() ? Component.empty() : cell.facts.get(i), fx, fy, TEXT, false);
        }
        cursor += ((cell.facts.size() + 1) / 2) * 11 + 6;
        g.fill(x + 8, cursor - 3, right - 8, cursor - 2, 0x33FFFFFF);
        // The controls sit at the bottom of the card: a status line, then the buttons for this hex.
        List<Action> buttons = cardButtons(cell, x + 8, right - 8);
        int buttonsTop = buttons.isEmpty() ? gridBottom() - 6 : buttons.stream().mapToInt(Action::y).min().getAsInt() - 4;
        var statusLines = font.split(cell.status, width - 16);
        int statusTop = buttonsTop - statusLines.size() * 10 - 2;
        for (FormattedCharSequence line : font.split(cell.description, width - 16)) {
            if (cursor > statusTop - 12) break;
            g.drawString(font, line, x + 8, cursor, TEXT, false);
            cursor += 10;
        }
        for (FormattedCharSequence line : statusLines) { g.drawString(font, line, x + 8, statusTop, TEXT, false); statusTop += 10; }
        actions.addAll(buttons);
    }

    /** LEARN for a skill you can buy; key chips for a learned active skill; RESTORE for a core ability that was replaced. */
    private List<Action> cardButtons(Cell cell, int x, int right) {
        List<Action> buttons = new ArrayList<>();
        int bottom = gridBottom() - 6, h = 16;
        var state = ClientSkillTreeState.get();
        SkillCatalog.Node node = cell.nodeId.isEmpty() ? null : SkillCatalog.find(state.power(), cell.nodeId).orElse(null);
        if (node != null && !cell.owned) {
            Component label = Component.literal(cell.available ? "LEARN  \u2022  " + node.cost() + (node.cost() == 1 ? " POINT" : " POINTS")
                    : "LOCKED").withStyle(ChatFormatting.BOLD);
            buttons.add(new Action(x, bottom - h, right - x, h, label, cell.available, cell.available,
                    () -> send(SkillTreeActionPayload.Operation.BUY, node.id(), AbilitySlot.PRIMARY)));
        } else if (node != null && node.active()) {
            int chip = (right - x - 4 * 3) / 5;
            for (AbilitySlot slot : AbilitySlot.values()) {
                boolean on = node.id().equals(state.build().slots().get(slot.name()));
                buttons.add(new Action(x + slot.ordinal() * (chip + 3), bottom - h, chip, h, Component.literal(key(slot)), !on, on,
                        () -> send(SkillTreeActionPayload.Operation.EQUIP, node.id(), slot)));
            }
        } else if (cell.kind == Kind.CORE && cell.slot != null && state.build().slots().containsKey(cell.slot.name())) {
            buttons.add(new Action(x, bottom - h, right - x, h, Component.literal("RESTORE TO " + key(cell.slot)).withStyle(ChatFormatting.BOLD), true, false,
                    () -> send(SkillTreeActionPayload.Operation.EQUIP, "", cell.slot)));
        }
        return buttons;
    }

    private void renderActions(GuiGraphics g, int mouseX, int mouseY) {
        for (Action action : actions) {
            boolean hover = mouseX >= action.x && mouseX < action.x + action.w && mouseY >= action.y && mouseY < action.y + action.h;
            int border = !action.enabled ? (action.highlighted ? GOLD : 0xFF4A3F37) : hover ? 0xFFFFFFFF : action.highlighted ? GOLD : LINE;
            int fill = !action.enabled ? (action.highlighted ? 0xAA5A3A14 : 0x88201A16) : hover ? 0xCC7A4A1A : action.highlighted ? 0xAA6A3A14 : 0x88302620;
            g.fill(action.x, action.y, action.x + action.w, action.y + action.h, fill);
            g.renderOutline(action.x, action.y, action.w, action.h, border);
            int color = pending ? DIM : action.enabled || action.highlighted ? TEXT : DIM;
            g.drawCenteredString(font, action.label, action.x + action.w / 2, action.y + (action.h - 8) / 2, color);
        }
    }

    private void renderBottom(GuiGraphics g, AbilitySnapshot power) {
        PlayerPowerSyncPayload sync = ClientPowerState.getSnapshot();
        int x = left() + 8, y = bottomY();
        int barWidth = Math.max(50, cardLeft() - x - 34 - 96);
        if (power != null) {
            g.drawString(font, "Level", x, y, MUTED, false);
            g.drawString(font, Component.literal(String.valueOf(power.level())).withStyle(ChatFormatting.BOLD), x, y + 11, TEXT, false);
            float progress = mastery(power);
            int bx = x + 34;
            g.fill(bx, y + 4, bx + barWidth, y + 7, 0xFF2A2A2A);
            g.fill(bx, y + 4, bx + (int) (barWidth * progress), y + 7, 0xFF5FA8C8);
            String mastery = !power.unlocked() ? "Awakens before it can grow"
                    : power.level() >= power.maximumLevel() ? "Mastered"
                    : power.mastery() + " / " + power.masteryRequiredForNextLevel() + " Mastery";
            g.drawString(font, mastery, bx, y + 11, MUTED, false);
            var tree = ClientSkillTreeState.get();
            if (tree.power().equals(power.abilityId().getPath()) && power.unlocked()) {
                int points = SkillCatalog.available(tree.power(), tree.build());
                int px = bx + barWidth + 12;
                g.blitSprite(HEX_POWER, px, y - 2, 18, 21);
                g.drawCenteredString(font, "↑", px + 9, y + 4, TEXT);
                g.drawString(font, Component.literal(String.valueOf(points)).withStyle(ChatFormatting.BOLD), px + 22, y + 4, GOLD, false);
                g.drawString(font, "points", px + 22 + font.width(String.valueOf(points)) + 8, y + 4, MUTED, false);
                if (!tree.build().learned().isEmpty()) {
                    int rx = px + 22, rw = font.width("RESPEC") + 10;
                    actions.add(new Action(rx, y + 15, rw, 12, Component.literal("RESPEC"), true, false,
                            () -> send(SkillTreeActionPayload.Operation.RESPEC, "", AbilitySlot.PRIMARY)));
                }
            }
        }
        // Stamina bar across the bottom edge.
        float fraction = sync.maximumEnergy() <= 0 ? 0 : Mth.clamp(sync.energy() / (float) sync.maximumEnergy(), 0, 1);
        int sx = cardLeft(), sw = cardRight() - sx, sy = y + 4;
        String stamina = "Stamina " + sync.energy() + " / " + sync.maximumEnergy() + "  (+" + sync.energyRegenerationPerSecond() + "/s)";
        g.fill(sx, sy, sx + sw, sy + 3, 0xFF1C2A33);
        g.fill(sx, sy, sx + (int) (sw * fraction), sy + 3, ENERGY);
        g.drawString(font, stamina, sx, sy + 7, ENERGY, false);
    }

    private static float mastery(AbilitySnapshot power) {
        if (power.level() >= power.maximumLevel()) return 1;
        long range = power.masteryRequiredForNextLevel() - power.masteryRequiredForCurrentLevel();
        return range <= 0 ? 0 : Mth.clamp((power.mastery() - power.masteryRequiredForCurrentLevel()) / (float) range, 0, 1);
    }

    @Override public boolean isPauseScreen() { return false; }
}
