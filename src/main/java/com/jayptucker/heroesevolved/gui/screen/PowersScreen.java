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
 * The Abilities screen: every ability the player has or can earn on one page. A hex grid shows the
 * power's own five abilities beside the three skill branches; hovering or clicking any hex fills the
 * card on the right with what it does, what it costs, which key it is on, and what it still needs.
 * Level, Mastery, stamina and unspent points run along the bottom.
 */
public final class PowersScreen extends Screen {
    private static final ResourceLocation HEX_OPEN = hex("open"), HEX_LOCKED = hex("locked"), HEX_LEARNED = hex("learned"),
            HEX_POWER = hex("power"), HEX_SELECTED = hex("selected");
    private static final int HEX_W = 26, HEX_H = 30;
    private static final int GOLD = 0xFFF2D27A, TEXT = 0xFFEDE3D6, MUTED = 0xFFB8A99A, DIM = 0xFF77695E, RED = 0xFFE07A6A,
            GREEN = 0xFF9BD67A, ENERGY = 0xFF57B6E0, LINE = 0xFF8A7A6C;

    private enum Kind { POWER, CORE, ACTIVE, PASSIVE, CAPSTONE }
    private record Cell(int column, int row, Kind kind, Component name, Component description, ItemStack icon,
                        boolean owned, boolean available, List<Component> facts, Component status, String nodeId) {}

    private final List<Cell> cells = new ArrayList<>();
    private Cell selected;
    private int refresh;

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

    private int contentWidth() { return Math.min(width - 16, 500); }
    private int left() { return (width - contentWidth()) / 2; }
    private int rowSpacing() { return Mth.clamp((height - 110) / 6, 31, 40); }
    /** The grid, card and bottom bar are centred vertically as one block. */
    private int gridTop() { return Math.max(40, (height - (14 + 6 * rowSpacing() + 44)) / 2); }
    private int columnX(int column) { return left() + 8 + column * 62 + (column > 0 ? 6 : 0); }
    private int cellY(int row) { return gridTop() + 14 + row * rowSpacing(); }
    private int cardLeft() { return columnX(3) + HEX_W + 18; }
    private int cardRight() { return left() + contentWidth(); }
    private int bottomY() { return cellY(5) + HEX_H + 14; }

    private static final String[] TABS = {"ABILITIES", "SKILL TREE", "JOURNAL", "SETTINGS", "CLOSE"};

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

        cells.add(new Cell(0, 0, Kind.POWER, Component.translatable("ability." + power.abilityId().getNamespace() + "." + path),
                Component.translatable("ability." + power.abilityId().getNamespace() + "." + path + ".passive"),
                PowerIcons.emblem(path), awake, awake, List.of(Component.literal("Your power's passive effects, always on.")),
                Component.translatable(awake ? "screen.heroesevolved.awakened" : "screen.heroesevolved.dormant")
                        .withStyle(awake ? ChatFormatting.GREEN : ChatFormatting.RED), ""));

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
                            .append(". Reset it in the Skill Tree.").withStyle(ChatFormatting.GOLD);
            cells.add(new Cell(0, row++, Kind.CORE, Component.translatable(action.definition().displayNameKey()),
                    Component.translatable(action.definition().displayNameKey() + ".desc"), PowerIcons.action(path, id),
                    unlocked, unlocked, facts, status, ""));
        }

        boolean capstoneTaken = SkillCatalog.nodes(path).stream().anyMatch(n -> n.capstone() && learned.contains(n.id()));
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
                            : Component.literal("Learned. Equip it to a key in the Skill Tree.").withStyle(ChatFormatting.GOLD))
                            : Component.literal("Learned").withStyle(ChatFormatting.GREEN))
                    : canLearn ? Component.literal("Ready to learn in the Skill Tree").withStyle(ChatFormatting.AQUA)
                    : node.capstone() && capstoneTaken ? Component.literal("You already chose a capstone").withStyle(ChatFormatting.RED)
                    : level < node.level() ? Component.literal("Reach Level " + node.level()).withStyle(ChatFormatting.RED)
                    : !prerequisite ? Component.literal("Learn its parent first").withStyle(ChatFormatting.RED)
                    : Component.literal("Needs " + node.cost() + " points").withStyle(ChatFormatting.RED);
            Kind kind = node.capstone() ? Kind.CAPSTONE : node.active() ? Kind.ACTIVE : Kind.PASSIVE;
            cells.add(new Cell(1 + node.branch(), node.tier(), kind, Component.literal(node.name()), Component.literal(node.description()),
                    PowerIcons.node(node), has, has || canLearn, facts, status, node.id()));
        }
        if (selected != null) selected = cells.stream().filter(c -> c.column == selected.column && c.row == selected.row).findFirst().orElse(null);
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
        for (Cell cell : cells) {
            int cx = columnX(cell.column), cy = cellY(cell.row);
            if (x >= cx && x < cx + HEX_W && y >= cy && y < cy + HEX_H) return cell;
        }
        return null;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        for (int i = 0; i < TABS.length; i++) {
            int tx = tabX(i);
            if (y >= 8 && y < 22 && x >= tx - 4 && x < tx + font.width(TABS[i]) + 4) {
                switch (i) {
                    case 1 -> { String node = selected == null ? "" : selected.nodeId; SkillTreeScreen.open(this);
                        if (!node.isEmpty() && minecraft.screen instanceof SkillTreeScreen tree) tree.select(node); }
                    case 2 -> JournalScreen.open(this);
                    case 3 -> minecraft.setScreen(new EffectsSettingsScreen(this));
                    case 4 -> onClose();
                    default -> { }
                }
                return true;
            }
        }
        Cell cell = cellAt(x, y);
        if (cell != null) {
            if (selected == cell && !cell.nodeId.isEmpty()) {
                // A second click on a skill opens it in the tree.
                SkillTreeScreen.open(this);
                if (minecraft.screen instanceof SkillTreeScreen tree) tree.select(cell.nodeId);
            }
            selected = cell;
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public void tick() {
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
        heading(g, 0, "POWER", GOLD);
        for (int b = 0; b < 3; b++) heading(g, b + 1, SkillCatalog.branchName(path, b), TEXT);

        // Connector lines down each branch.
        for (int column = 1; column <= 3; column++)
            for (int row = 0; row < 5; row++) {
                int x = columnX(column) + HEX_W / 2, y1 = cellY(row) + HEX_H - 2, y2 = cellY(row + 1) + 2;
                final int c = column, r = row;
                boolean lit = cells.stream().anyMatch(cell -> cell.column == c && cell.row == r + 1 && cell.owned);
                g.fill(x - 1, y1, x + 1, y2, lit ? 0xFFD08A4A : LINE);
            }
        int sepX = columnX(1) - 9;
        g.fill(sepX, gridTop() + 10, sepX + 1, cellY(5) + HEX_H, 0x55FFFFFF);

        Cell hovered = cellAt(mouseX, mouseY);
        for (Cell cell : cells) {
            int x = columnX(cell.column), y = cellY(cell.row);
            ResourceLocation frame = cell.kind == Kind.POWER ? HEX_POWER : cell.owned ? HEX_LEARNED : cell.available ? HEX_OPEN : HEX_LOCKED;
            g.blitSprite(frame, x, y, HEX_W, HEX_H);
            g.renderFakeItem(cell.icon, x + 5, y + 7);
            if (!cell.owned && !cell.available) g.fill(x + 4, y + 7, x + 22, y + 23, 0x88201A16);
            if (cell.kind == Kind.CAPSTONE) g.drawString(font, "✦", x + HEX_W - 7, y - 1, GOLD, true);
            if (cell == hovered || cell == selected) g.blitSprite(HEX_SELECTED, x, y, HEX_W, HEX_H);
        }
        // Small key letters under the abilities that are on a key right now.
        for (Cell cell : cells) {
            String keyLabel = keyLabel(cell);
            if (keyLabel != null) {
                int x = columnX(cell.column) + HEX_W - 4, y = cellY(cell.row) + HEX_H - 8;
                g.fill(x - 1, y - 1, x + font.width(keyLabel) + 1, y + 8, 0xDD000000);
                g.drawString(font, keyLabel, x, y, GOLD, false);
            }
        }

        Cell shown = hovered != null ? hovered : selected != null ? selected : cells.getFirst();
        renderCard(g, shown);
        renderBottom(g, sync.abilities().getFirst());
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

    private void heading(GuiGraphics g, int column, String text, int color) {
        int center = columnX(column) + HEX_W / 2;
        float scale = Math.min(1F, 58F / Math.max(1, font.width(text)));
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

    private void renderCard(GuiGraphics g, Cell cell) {
        int x = cardLeft(), right = cardRight(), y = gridTop() + 6, width = right - x;
        g.fill(x, y, right, cellY(5) + HEX_H, 0x66000000);
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
        int bottom = cellY(5) + HEX_H - 16;
        for (FormattedCharSequence line : font.split(cell.description, width - 16)) {
            if (cursor > bottom - 10) break;
            g.drawString(font, line, x + 8, cursor, TEXT, false);
            cursor += 10;
        }
        for (FormattedCharSequence line : font.split(cell.status, width - 16)) {
            g.drawString(font, line, x + 8, bottom, TEXT, false);
            bottom += 10;
        }
        if (!cell.nodeId.isEmpty()) g.drawString(font, "Click again to open it in the Skill Tree", x + 8, y - 10, DIM, false);
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
