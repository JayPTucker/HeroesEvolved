package com.jayptucker.heroesevolved.gui.screen;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.jayptucker.heroesevolved.network.*;
import com.jayptucker.heroesevolved.skills.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Inventory-accessible three-branch tree. Purchases and equipment always wait for the server response. */
public final class SkillTreeScreen extends Screen {
    private final Screen parent;
    private String selected = "";
    private SkillTreeStatePayload previous;
    private Button purchase;
    private boolean pending;
    private int refresh, page;
    private final List<Button> slotButtons = new ArrayList<>();
    private final Map<String, Button> nodeButtons = new HashMap<>();
    private static final String[] BRANCHES = {"CONTROL", "FORCE", "SURVIVAL"};
    private static final int[] COLORS = {0xFF69D3E9, 0xFFF0AC69, 0xFF94DBAA};
    public SkillTreeScreen(Screen parent) { super(Component.translatable("screen.heroesevolved.skill_tree")); this.parent = parent; }
    public static void open(Screen parent) {
        ClientSkillTreeState.clear();
        PacketDistributor.sendToServer(new SkillTreeActionPayload(SkillTreeActionPayload.Operation.OPEN, "", AbilitySlot.PRIMARY));
        Minecraft.getInstance().setScreen(new SkillTreeScreen(parent));
    }
    private int panelWidth() { return Math.min(450, width - 12); }
    private int panelHeight() { return Math.min(318, height - 12); }
    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return (height - panelHeight()) / 2; }
    private int rowHeight() { return panelHeight() >= 300 ? 40 : panelHeight() >= 260 ? 32 : 26; }
    private int rowGap() { return panelHeight() >= 300 ? 10 : panelHeight() >= 260 ? 8 : 6; }
    private int columnWidth() { return (panelWidth() - 32) / 3; }
    private int treeBottom() { return top() + 58 + rowHeight() * 3 + rowGap() * 2; }
    @Override protected void init() {
        previous = ClientSkillTreeState.get(); slotButtons.clear(); nodeButtons.clear();
        for (var node : SkillCatalog.nodes(previous.power())) {
            if (node.tier() / 3 != page) continue;
            int x = left() + 10 + node.branch() * (columnWidth() + 6);
            int y = top() + 58 + (node.tier() % 3) * (rowHeight() + rowGap());
            Button button = new NodeButton(node, x, y, columnWidth(), rowHeight());
            nodeButtons.put(node.id(), addRenderableWidget(button));
        }
        purchase = addRenderableWidget(Button.builder(Component.literal("Spend points"), b -> {
            if (pending) return;
            pending = true;
            send(SkillTreeActionPayload.Operation.BUY, selected, AbilitySlot.PRIMARY);
        }).bounds(left() + 10, top() + panelHeight() - 25, 114, 18).build());
        Button respec = addRenderableWidget(Button.builder(Component.literal("Respecialize"), b -> { pending = true; send(SkillTreeActionPayload.Operation.RESPEC, "", AbilitySlot.PRIMARY); })
                .bounds(left() + 130, top() + panelHeight() - 25, 98, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(left() + panelWidth() - 64, top() + panelHeight() - 25, 54, 18).build());
        respec.setTooltip(Tooltip.create(Component.literal("Refund all purchases and restore default actions. Earned points and cooldowns remain. Requires ten seconds out of combat.")));
        int slotWidth = (panelWidth() - 24) / 5;
        for (var slot : AbilitySlot.values()) {
            final AbilitySlot chosen = slot;
            Button button = Button.builder(Component.literal(key(slot)), b -> {
                if (pending) return;
                var state = ClientSkillTreeState.get();
                var node = SkillCatalog.find(state.power(), selected).orElse(null);
                String id = node != null && node.active() && state.build().learned().contains(node.id()) ? node.id() : "";
                pending = true; send(SkillTreeActionPayload.Operation.EQUIP, id, chosen);
            }).bounds(left() + 10 + slot.ordinal() * slotWidth, top() + panelHeight() - 49, slotWidth - 3, 18).build();
            slotButtons.add(addRenderableWidget(button));
        }
        if (SkillCatalog.rows(previous.power()) > 3) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> changePage(-1)).bounds(left() + panelWidth() - 48, top() + 23, 18, 16).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> changePage(1)).bounds(left() + panelWidth() - 28, top() + 23, 18, 16).build());
        }
        refreshButtons();
    }
    private void changePage(int delta) {
        page = Math.clamp(page + delta, 0, (SkillCatalog.rows(ClientSkillTreeState.get().power()) - 1) / 3);
        rebuildWidgets();
    }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (x >= left() && x <= left() + panelWidth() && y >= top() && y <= top() + panelHeight()) { changePage(sy < 0 ? 1 : -1); return true; }
        return super.mouseScrolled(x, y, sx, sy);
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
    private boolean canLearn(SkillCatalog.Node node, SkillTreeStatePayload state) {
        return state.awakened() && state.level() >= node.level() && !state.build().learned().contains(node.id())
                && (node.prerequisite().isEmpty() || state.build().learned().contains(node.prerequisite()))
                && (!node.capstone() || SkillCatalog.nodes(state.power()).stream().noneMatch(n -> n.capstone() && state.build().learned().contains(n.id())))
                && SkillCatalog.available(state.power(), state.build()) >= node.cost();
    }
    private void refreshButtons() {
        var state = ClientSkillTreeState.get();
        var node = SkillCatalog.find(state.power(), selected).orElse(null);
        purchase.active = !pending && node != null && canLearn(node, state);
        String purchaseLabel = node == null ? "Select a skill" : state.build().learned().contains(node.id()) ? "Learned"
                : state.level() < node.level() ? "Requires level " + node.level()
                : !node.prerequisite().isEmpty() && !state.build().learned().contains(node.prerequisite()) ? "Requires parent"
                : SkillCatalog.available(state.power(), state.build()) < node.cost() ? "Need " + node.cost() + " points"
                : "Spend " + node.cost() + (node.cost() == 1 ? " point" : " points");
        if (node != null && node.capstone() && !state.build().learned().contains(node.id()) && SkillCatalog.nodes(state.power()).stream().anyMatch(n -> n.capstone() && state.build().learned().contains(n.id()))) purchaseLabel = "One capstone only";
        purchase.setMessage(Component.literal(purchaseLabel));
        purchase.setTooltip(node == null ? null : Tooltip.create(Component.literal(node.name() + ": level " + node.level()
                + ", " + node.cost() + " points" + (node.prerequisite().isEmpty() ? "" : ". Requires "
                + SkillCatalog.find(state.power(), node.prerequisite()).map(SkillCatalog.Node::name).orElse("the parent node")) + ".")));
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
            if (!state.power().equals(previous.power())) { selected = ""; page = 0; rebuildWidgets(); }
            previous = state; refreshButtons();
        }
        if (++refresh % 60 == 0 && minecraft.getConnection() != null)
            send(SkillTreeActionPayload.Operation.OPEN, "", AbilitySlot.PRIMARY);
    }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Screen.render invokes this before its widgets. This screen already drew
        // its own background and text, so the default blur would blur that text.
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xC0091018);
        int l = left(), t = top(), w = panelWidth(), h = panelHeight();
        g.fill(l - 1, t - 1, l + w + 1, t + h + 1, 0xFF486178);
        g.fill(l, t, l + w, t + h, 0xFF101B29);
        g.fill(l, t, l + w, t + 3, 0xFF79CADA);
        var state = ClientSkillTreeState.get();
        String power = state.power().isEmpty() ? "No power discovered" : Component.translatable("ability.heroesevolved." + state.power()).getString();
        g.drawString(font, power + " / Skill Tree", l + 10, t + 10, 0xFFF2F5FA, false);
        int points = state.awakened() ? SkillCatalog.available(state.power(), state.build()) : 0;
        g.drawString(font, points + " points  |  Level " + state.level() + "  |  " + (page + 1) + "/" + ((SkillCatalog.rows(state.power()) + 2) / 3), l + 10, t + 25, 0xFFB9D2DF, false);
        if (state.power().isEmpty()) {
            g.drawWordWrap(font, Component.literal("Discover a power to reveal its skill tree. Each power has its own branches, purchases and loadout."), l + 16, t + 65, w - 32, 0xFFBBC9D7);
        } else {
            for (int branch = 0; branch < 3; branch++) {
                int x = l + 10 + branch * (columnWidth() + 6);
                g.drawCenteredString(font, SkillCatalog.branchName(state.power(), branch), x + columnWidth() / 2, t + 44, COLORS[branch]);
                for (int tier = 0; tier < 2; tier++) {
                    int y = t + 58 + tier * (rowHeight() + rowGap()) + rowHeight();
                    g.fill(x + columnWidth() / 2 - 1, y, x + columnWidth() / 2 + 1, y + rowGap(), COLORS[branch] & 0x55FFFFFF);
                }
            }
            var node = SkillCatalog.find(state.power(), selected).orElse(null);
            int detailY = treeBottom() + 7;
            int maxLines = Math.max(1, (t + h - 56 - detailY) / 10);
            String details = !state.awakened() ? "Your power is dormant. Skill points become usable when it awakens."
                    : node == null ? "Choose a node to inspect it. Active skills can replace any ability key. Select an unlearned node to restore default skills."
                    : node.description() + (node.active() ? "  " + node.energy() + " stamina / " + node.cooldown() / 20.0 + "s base cooldown. Select a key below to equip." : "");
            var lines = font.split(Component.literal(details), w - 20);
            for (int i = 0; i < Math.min(maxLines, lines.size()); i++) g.drawString(font, lines.get(i), l + 10, detailY + i * 10, 0xFFC6D4E2, false);
        }
        super.render(g, mouseX, mouseY, partial);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }

    private final class NodeButton extends Button {
        private final SkillCatalog.Node node;
        NodeButton(SkillCatalog.Node node, int x, int y, int w, int h) {
            super(x, y, w, h, Component.literal(node.name()), b -> {
                selected = node.id(); refreshButtons();
            }, DEFAULT_NARRATION);
            this.node = node;
            setTooltip(Tooltip.create(Component.literal(node.name() + "\n" + node.description() + "\nLevel "
                    + node.level() + " / " + node.cost() + " points" + (!node.prerequisite().isEmpty() ? " / requires " + SkillCatalog.find(node.power(), node.prerequisite()).map(SkillCatalog.Node::name).orElse("parent") : "")
                    + (node.active() ? "\n" + node.energy() + " stamina / " + node.cooldown() / 20.0 + "s base cooldown" : ""))));
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            var state = ClientSkillTreeState.get();
            boolean learned = state.build().learned().contains(node.id()), selectedNode = selected.equals(node.id());
            int color = learned || selectedNode ? COLORS[node.branch()] : canLearn(node, state) ? 0xFF789CB4 : 0xFF3F536A;
            g.fill(getX(), getY(), getX() + width, getY() + height, selectedNode || isHoveredOrFocused() ? color : 0xFF314355);
            g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, learned ? 0xFF203C44 : 0xFF172635);
            g.fill(getX() + 1, getY() + 1, getX() + 4, getY() + height - 1, color);
            String prefix = learned ? "+ " : node.active() ? "> " : "";
            var labels = font.split(Component.literal(prefix + node.name()), width - 14);
            int nameLines = Math.min(2, labels.size());
            for (int i = 0; i < nameLines; i++)
                g.drawString(font, labels.get(i), getX() + 7, getY() + 4 + i * 9, learned ? 0xFFE2FAEE : 0xFFD1DEEA, false);
            if (height >= 36 || nameLines == 1) g.drawString(font, learned ? (node.active() ? "ACTIVE / LEARNED" : "PASSIVE / LEARNED")
                    : "LV " + node.level() + "  /  " + node.cost() + " POINT" + (node.cost() == 1 ? "" : "S"), getX() + 7, getY() + height - 12, color, false);
            else if (selectedNode) g.fill(getX(), getY() + height - 2, getX() + width, getY() + height, color);
        }
    }
}
