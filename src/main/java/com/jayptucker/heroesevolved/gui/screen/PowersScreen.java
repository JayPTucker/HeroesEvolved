package com.jayptucker.heroesevolved.gui.screen;

import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.service.AbilityRanks;
import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.jayptucker.heroesevolved.network.*;
import com.jayptucker.heroesevolved.progression.PowerLevels;
import com.jayptucker.heroesevolved.skills.Loadout;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The hero menu's power pages. <b>Loadout</b> lists everything you can cast (your five core abilities, your
 * class ability and the skills you've learned), each tagged Offense, Defense, Support or Utility, beside your
 * eight ability keys: pick an ability, then a key. <b>Skill Tree</b> draws the three branches as trees with a
 * card for the selected skill. <b>Class</b> is where you choose how you play: an Offense, Defense or Support
 * ability, each with a perk that is always on.
 */
public final class PowersScreen extends HeroScreen {
    private static final ResourceLocation HEX_OPEN = hex("open"), HEX_LOCKED = hex("locked"), HEX_LEARNED = hex("learned"),
            HEX_POWER = hex("power"), HEX_SELECTED = hex("selected");
    private static final int HEX_W = 26, HEX_H = 30, ROW = 24;

    private Tab page = Tab.LOADOUT;
    private String selectedAction = Loadout.base(AbilitySlot.PRIMARY), selectedNode = "";
    private int scroll;
    private boolean pending;
    private SkillTreeStatePayload seenState;
    private Component hover;

    public PowersScreen() { this(null); }

    /** Opened from the inventory's eclipse button; closing returns there. */
    public PowersScreen(Screen parent) { super(Component.translatable("screen.heroesevolved.powers"), parent); }

    private static ResourceLocation hex(String name) { return ResourceLocation.fromNamespaceAndPath("heroesevolved", "hex/" + name); }

    @Override protected Tab tab() { return page; }

    /** Shows one of this screen's pages: Loadout, Skill Tree or Class. */
    public PowersScreen page(Tab tab) {
        if (tab == Tab.LOADOUT || tab == Tab.TREE || tab == Tab.CLASS) { page = tab; scroll = 0; }
        return this;
    }

    /** Selects an ability or skill by its key, as a click would (used by the UI preview): a node id, "power", or "core:SLOT". */
    public void select(String key) {
        if (key.startsWith("core:")) { page = Tab.LOADOUT; selectedAction = Loadout.BASE + key.substring(5); return; }
        if (key.equals("power")) { page = Tab.LOADOUT; return; }
        AbilitySnapshot power = power();
        var node = power == null ? null : SkillCatalog.find(power.abilityId().getPath(), key).orElse(null);
        if (node != null && node.style()) page = Tab.CLASS;
        else { page = Tab.TREE; selectedNode = key; }
    }

    @Override protected void init() {
        if (minecraft.getConnection() != null)
            PacketDistributor.sendToServer(new SkillTreeActionPayload(SkillTreeActionPayload.Operation.OPEN, "", AbilitySlot.PRIMARY));
    }

    @Override public void tick() {
        super.tick();
        if (ClientSkillTreeState.get() != seenState) { seenState = ClientSkillTreeState.get(); pending = false; }
        if (frame % 60 == 0 && minecraft.getConnection() != null)
            PacketDistributor.sendToServer(new SkillTreeActionPayload(SkillTreeActionPayload.Operation.OPEN, "", AbilitySlot.PRIMARY));
    }

    private void buy(String nodeId) {
        if (minecraft.getConnection() == null || pending) return;
        pending = true;
        PacketDistributor.sendToServer(new SkillTreeActionPayload(SkillTreeActionPayload.Operation.BUY, nodeId, AbilitySlot.PRIMARY));
    }

    private void bind(int key, String actionKey) {
        if (minecraft.getConnection() != null) PacketDistributor.sendToServer(new BindKeyPayload(key, actionKey));
    }

    // ------------------------------------------------------------------
    // What the player has
    // ------------------------------------------------------------------

    /** One castable ability, as the Loadout page shows it. */
    private record Ability(String actionKey, Component name, Component description, ItemStack icon, String role, String kind,
                           boolean unlocked, int unlockLevel, int energy, int cooldown, String rank) {}

    private static boolean awake() { AbilitySnapshot p = power(); return p != null && p.unlocked(); }

    private static Set<String> learned() {
        AbilitySnapshot power = power();
        var tree = ClientSkillTreeState.get();
        return power != null && tree.power().equals(power.abilityId().getPath()) ? tree.build().learned() : Set.of();
    }

    private List<Ability> abilities() {
        List<Ability> list = new ArrayList<>();
        AbilitySnapshot power = power();
        if (power == null) return list;
        String path = power.abilityId().getPath();
        var ability = AbilityRegistry.ABILITIES.get(power.abilityId());
        boolean awake = power.unlocked();
        for (AbilitySlot slot : AbilitySlot.values()) {
            AbilityAction action = ability == null ? null : ability.actions().get(slot);
            if (action == null) continue;
            String key = Loadout.base(slot), id = action.definition().id().getPath();
            LoadoutPayload.Entry entry = ClientLoadout.entry(key);
            Component description = Component.translatable(action.definition().displayNameKey() + ".desc");
            list.add(new Ability(key, Component.translatable(action.definition().displayNameKey()), description, PowerIcons.action(path, id),
                    roleOf("", description.getString()), "Core ability", awake && power.level() >= action.definition().unlockLevel(),
                    PowerLevels.levelForTier(action.definition().unlockLevel()),
                    entry != null ? entry.energy() : action.energyCost(Math.max(1, power.level())),
                    entry != null ? entry.cooldownTicks() : action.cooldownTicks(Math.max(1, power.level())), entry == null ? "" : rank(entry)));
        }
        Set<String> learned = learned();
        List<SkillCatalog.Node> nodes = new ArrayList<>(SkillCatalog.styles(path));
        nodes.addAll(SkillCatalog.nodes(path));
        for (SkillCatalog.Node node : nodes) {
            if (!node.active() || !learned.contains(node.id())) continue;
            LoadoutPayload.Entry entry = ClientLoadout.entry(node.id());
            list.add(new Ability(node.id(), Component.literal(node.name()), Component.literal(node.description()), PowerIcons.node(node),
                    roleOf(node.role(), node.description()), node.style() ? "Class ability" : node.signature() ? "Signature skill" : "Learned skill",
                    awake, PowerLevels.levelForTier(node.level()), entry != null ? entry.energy() : node.energy(),
                    entry != null ? entry.cooldownTicks() : node.cooldown(), entry == null ? "" : rank(entry)));
        }
        return list;
    }

    private static String rank(LoadoutPayload.Entry entry) {
        String[] names = {"I", "II", "III"};
        String rank = names[Math.clamp(entry.rank(), 1, 3) - 1];
        int next = entry.rank() == 1 ? AbilityRanks.RANK_TWO : entry.rank() == 2 ? AbilityRanks.RANK_THREE : 0;
        return next > 0 ? "Rank " + rank + " (" + entry.uses() + "/" + next + ")" : "Rank " + rank;
    }

    private static String seconds(long ticks) {
        if (ticks <= 0) return "none";
        double s = ticks / 20.0;
        return s == Math.floor(s) ? (int) s + "s" : String.format("%.1fs", s);
    }

    /** Ability key {@code index} (1 to 8) as the player has it set in Controls. */
    private static String key(int index) {
        KeyMapping mapping = ModKeyMappings.ABILITY_KEYS[index - 1];
        return mapping.isUnbound() ? "#" + index : mapping.getTranslatedKeyMessage().getString().toUpperCase();
    }

    // ------------------------------------------------------------------
    // Pages
    // ------------------------------------------------------------------

    @Override protected void renderPage(GuiGraphics g, int mouseX, int mouseY, float partial) {
        hover = null;
        AbilitySnapshot power = power();
        if (power == null) {
            g.drawCenteredString(font, Component.literal("NO POWER YET").withStyle(ChatFormatting.BOLD), width / 2, height / 2 - 30, GOLD);
            int y = height / 2 - 12;
            for (FormattedCharSequence line : font.split(Component.translatable("screen.heroesevolved.no_power"), Math.min(320, width - 40)))
                { g.drawCenteredString(font, line, width / 2, y, TEXT); y += 11; }
            return;
        }
        switch (page) {
            case TREE -> renderTree(g, mouseX, mouseY);
            case CLASS -> renderClass(g, mouseX, mouseY);
            default -> renderLoadout(g, mouseX, mouseY);
        }
    }

    @Override protected void renderTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (hover != null) g.renderTooltip(font, font.split(hover, 220), mouseX, mouseY);
    }

    // ---- Loadout

    private void renderLoadout(GuiGraphics g, int mouseX, int mouseY) {
        List<Ability> list = abilities();
        int x = contentLeft(), top = contentTop(), bottom = contentBottom() - 4;
        int listW = Math.min(250, (int) (contentWidth() * 0.55F));
        g.drawString(font, "YOUR ABILITIES", x, top, GOLD, false);
        g.drawString(font, list.size() + "", x + font.width("YOUR ABILITIES") + 6, top, MUTED, false);
        int listTop = top + 12, visible = Math.max(1, (bottom - listTop) / ROW);
        scroll = Mth.clamp(scroll, 0, Math.max(0, list.size() - visible));
        Ability selected = list.stream().filter(a -> a.actionKey.equals(selectedAction)).findFirst().orElse(list.isEmpty() ? null : list.getFirst());
        if (selected != null) selectedAction = selected.actionKey;
        String lastKind = "";
        for (int i = scroll; i < Math.min(list.size(), scroll + visible); i++) {
            Ability a = list.get(i);
            int y = listTop + (i - scroll) * ROW;
            boolean isSelected = a == selected, over = mouseX >= x && mouseX < x + listW && mouseY >= y && mouseY < y + ROW - 2;
            g.fill(x, y, x + listW, y + ROW - 2, isSelected ? 0x996A4A14 : over ? 0x66403020 : 0x55000000);
            if (isSelected) g.renderOutline(x, y, listW, ROW - 2, GOLD);
            g.fill(x, y, x + 2, y + ROW - 2, roleColor(a.role));
            g.renderFakeItem(a.icon, x + 5, y + 3);
            if (!a.unlocked) g.fill(x + 5, y + 3, x + 21, y + 19, 0x99201A16);
            int textX = x + 25, keyIndex = ClientLoadout.keyOf(a.actionKey);
            String keyText = keyIndex > 0 ? key(keyIndex) : "—";
            int keyW = Math.max(16, font.width(keyText) + 6);
            int nameRoom = listW - 25 - keyW - 8;
            if (fitted(g, a.name.getString(), textX, y + 3, nameRoom, a.unlocked ? TEXT : DIM, false) && over) hover = a.name;
            String facts = !a.unlocked ? "Unlocks at Level " + a.unlockLevel
                    : (a.energy <= 0 ? "free" : a.energy + " stamina") + " · " + seconds(a.cooldown);
            int pillW = font.width(roleLabel(a.role)) + 6;
            g.drawString(font, ellipsis(facts, nameRoom - pillW - 4), textX, y + 12, MUTED, false);
            pill(g, x + listW - keyW - 6 - pillW, y + 11, roleLabel(a.role), roleColor(a.role));
            int kx = x + listW - keyW - 3;
            g.fill(kx, y + 4, kx + keyW, y + 17, keyIndex > 0 ? 0xCC2A1C10 : 0x55000000);
            g.renderOutline(kx, y + 4, keyW, 13, keyIndex > 0 ? GOLD : LINE);
            g.drawCenteredString(font, keyText, kx + keyW / 2, y + 7, keyIndex > 0 ? GOLD : DIM);
            String target = a.actionKey;
            hits.add(new Hit(x, y, listW, ROW - 2, () -> selectedAction = target, null));
        }
        if (list.size() > visible) {
            int barX = x + listW + 2, barH = bottom - listTop, thumb = Math.max(10, barH * visible / list.size());
            int thumbY = listTop + (barH - thumb) * scroll / Math.max(1, list.size() - visible);
            g.fill(barX, listTop, barX + 2, bottom, 0x33FFFFFF);
            g.fill(barX, thumbY, barX + 2, thumbY + thumb, GOLD);
        }
        // The selected ability, then the eight keys.
        int rx = x + listW + 8, rw = contentRight() - rx;
        int keysH = 2 * 26 + 14, detailBottom = bottom - keysH - 6;
        panel(g, rx, top, rw, detailBottom - top);
        if (selected != null) {
            g.renderFakeItem(selected.icon, rx + 6, top + 6);
            fitted(g, selected.name.getString(), rx + 26, top + 6, rw - 30, TEXT, true);
            int px = rx + 26 + pill(g, rx + 26, top + 16, roleLabel(selected.role), roleColor(selected.role)) + 4;
            g.drawString(font, ellipsis(selected.kind, rx + rw - px - 4), px, top + 17, MUTED, false);
            String facts = (selected.energy <= 0 ? "Free" : selected.energy + " stamina") + "  ·  " + seconds(selected.cooldown) + " cooldown"
                    + (selected.rank.isEmpty() ? "" : "  ·  " + selected.rank);
            g.drawString(font, ellipsis(facts, rw - 12), rx + 6, top + 30, GOLD, false);
            int lines = Math.max(1, (detailBottom - (top + 44) - 12) / 10);
            int after = paragraph(g, selected.description, rx + 6, top + 43, rw - 12, lines, TEXT);
            int keyIndex = ClientLoadout.keyOf(selected.actionKey);
            Component status = !selected.unlocked ? Component.literal("Unlocks at Level " + selected.unlockLevel).withStyle(ChatFormatting.RED)
                    : keyIndex > 0 ? Component.literal("On " + key(keyIndex) + ". Also on the ability wheel (hold R).").withStyle(ChatFormatting.GREEN)
                    : Component.literal("On the ability wheel only. Click a key below to put it there.").withStyle(ChatFormatting.GOLD);
            g.drawString(font, ellipsis(status.getString(), rw - 12), rx + 6, Math.min(after + 2, detailBottom - 11), status.getStyle().getColor() == null ? TEXT
                    : status.getStyle().getColor().getValue() | 0xFF000000, false);
        }
        int ky = detailBottom + 6;
        g.drawString(font, "KEYS", rx, ky, GOLD, false);
        g.drawString(font, ellipsis("click to assign, right-click to clear", rw - 30), rx + 28, ky, DIM, false);
        int slotW = (rw - 9) / 4;
        Map<String, Ability> byKey = new HashMap<>();
        list.forEach(a -> byKey.put(a.actionKey, a));
        for (int k = 1; k <= Loadout.KEYS; k++) {
            int sx = rx + ((k - 1) % 4) * (slotW + 3), sy = ky + 12 + ((k - 1) / 4) * 26;
            String bound = ClientLoadout.onKey(k);
            Ability on = byKey.get(bound);
            boolean holdsSelected = selected != null && bound.equals(selected.actionKey);
            boolean over = mouseX >= sx && mouseX < sx + slotW && mouseY >= sy && mouseY < sy + 24;
            g.fill(sx, sy, sx + slotW, sy + 24, holdsSelected ? 0xAA6A4A14 : over ? 0x88403020 : 0x66000000);
            g.renderOutline(sx, sy, slotW, 24, holdsSelected ? GOLD : over ? TEXT : LINE);
            g.drawString(font, key(k), sx + 3, sy + 2, GOLD, false);
            if (on != null) {
                g.pose().pushPose();
                g.pose().translate(sx + slotW - 14, sy + 2, 0);
                g.pose().scale(0.75F, 0.75F, 1);
                g.renderFakeItem(on.icon, 0, 0);
                g.pose().popPose();
                fitted(g, on.name.getString(), sx + 3, sy + 13, slotW - 6, TEXT, false);
            } else g.drawString(font, "empty", sx + 3, sy + 13, DIM, false);
            int index = k;
            Ability chosen = selected;
            hits.add(new Hit(sx, sy, slotW, 24, () -> {
                if (chosen == null || !chosen.unlocked) return;
                bind(index, holdsSelected ? "" : chosen.actionKey);
            }, () -> bind(index, "")));
            if (over && on != null) hover = on.name;
        }
    }

    // ---- Skill tree

    private void renderTree(GuiGraphics g, int mouseX, int mouseY) {
        AbilitySnapshot power = power();
        String path = power.abilityId().getPath();
        boolean awake = power.unlocked();
        Set<String> learned = learned();
        int points = points(), level = ClientLoadout.get().level();
        List<SkillCatalog.Node> nodes = SkillCatalog.nodes(path);
        if (nodes.stream().noneMatch(n -> n.id().equals(selectedNode)) && !nodes.isEmpty()) selectedNode = nodes.getFirst().id();
        boolean capstoneTaken = nodes.stream().anyMatch(n -> n.capstone() && learned.contains(n.id()));
        int x = contentLeft(), top = contentTop(), bottom = contentBottom() - 4;
        int cardW = Mth.clamp((int) (contentWidth() * 0.34F), 140, 210), areaW = contentWidth() - cardW - 8, colW = areaW / 3;
        Map<String, double[]> place = treePlaces(path);
        int depth = (int) place.values().stream().mapToDouble(p -> p[1]).max().orElse(4);
        int lanes = 1;
        for (int b = 0; b < 3; b++) {
            int fb = b;
            lanes = Math.max(lanes, 1 + (int) nodes.stream().filter(n -> n.branch() == fb).mapToDouble(n -> place.get(n.id())[0]).max().orElse(0));
        }
        int treeTop = top + 14, rowGap = Math.max(HEX_H + 2, (bottom - treeTop - HEX_H) / Math.max(1, depth));
        boolean names = rowGap >= HEX_H + 12, twoLines = rowGap >= HEX_H + 19;
        int laneGap = Math.min(40, (colW - HEX_W - 4) / Math.max(1, lanes - 1));
        // Names under the hexes only where there is room for them; otherwise hover a hex, or read its card.
        names = names && laneGap >= 40;
        Map<String, int[]> at = new HashMap<>();
        for (SkillCatalog.Node n : nodes) {
            double[] p = place.get(n.id());
            int colX = x + n.branch() * colW, laneCount = 1 + (int) nodes.stream().filter(o -> o.branch() == n.branch()).mapToDouble(o -> place.get(o.id())[0]).max().orElse(0);
            int span = (laneCount - 1) * laneGap, left = colX + (colW - span - HEX_W) / 2;
            at.put(n.id(), new int[] {left + (int) Math.round(p[0] * laneGap), treeTop + (int) Math.round(p[1] * rowGap)});
        }
        for (int b = 0; b < 3; b++) {
            String name = SkillCatalog.branchName(path, b).toUpperCase();
            String shown = ellipsis(name, colW - 4);
            g.drawString(font, shown, x + b * colW + (colW - font.width(shown)) / 2, top, TEXT);
            if (b > 0) g.fill(x + b * colW, treeTop, x + b * colW + 1, bottom, 0x22FFFFFF);
        }
        for (SkillCatalog.Node n : nodes) {
            if (n.prerequisite().isEmpty() || !at.containsKey(n.prerequisite())) continue;
            int[] c = at.get(n.id()), p = at.get(n.prerequisite());
            int color = learned.contains(n.id()) ? 0xFFD08A4A : LINE;
            int px = p[0] + HEX_W / 2, cx = c[0] + HEX_W / 2, mid = (p[1] + HEX_H + c[1]) / 2;
            g.fill(px - 1, p[1] + HEX_H - 2, px + 1, mid + 1, color);
            g.fill(Math.min(px, cx) - 1, mid - 1, Math.max(px, cx) + 1, mid + 1, color);
            g.fill(cx - 1, mid - 1, cx + 1, c[1] + 2, color);
        }
        SkillCatalog.Node selected = null;
        for (SkillCatalog.Node n : nodes) {
            int[] c = at.get(n.id());
            boolean has = learned.contains(n.id());
            boolean canLearn = canLearn(n, awake, has, level, learned, points, capstoneTaken);
            ResourceLocation sprite = has ? HEX_LEARNED : canLearn ? HEX_OPEN : HEX_LOCKED;
            g.blitSprite(sprite, c[0], c[1], HEX_W, HEX_H);
            g.renderFakeItem(PowerIcons.node(n), c[0] + 5, c[1] + 7);
            if (!has && !canLearn) g.fill(c[0] + 4, c[1] + 7, c[0] + 22, c[1] + 23, 0x88201A16);
            if (n.capstone()) g.drawString(font, "✦", c[0] + HEX_W - 7, c[1] - 1, GOLD, true);
            if (n.signature()) g.drawString(font, "★", c[0] + HEX_W - 7, c[1] - 1, 0xFFC77DFF, true);
            if (!has && canLearn && (frame / 10) % 2 == 0) g.blitSprite(HEX_SELECTED, c[0], c[1], HEX_W, HEX_H);
            boolean over = mouseX >= c[0] && mouseX < c[0] + HEX_W && mouseY >= c[1] && mouseY < c[1] + HEX_H;
            if (n.id().equals(selectedNode)) { selected = n; g.blitSprite(HEX_SELECTED, c[0], c[1], HEX_W, HEX_H); }
            if (over) { g.blitSprite(HEX_SELECTED, c[0], c[1], HEX_W, HEX_H); hover = Component.literal(n.name()); }
            if (names) {
                // Each name fits within its own lane, so neighbours never run into each other.
                // Long names wrap at a word onto a second line where the rows leave room, and end in "…" where they don't.
                int room = (int) ((lanes > 1 ? Math.min(laneGap - 3, colW - 4) : colW - 4) / 0.75F);
                List<String> label = wrapped(n.name(), room, twoLines ? 2 : 1);
                for (int line = 0; line < label.size(); line++) {
                    int lw = font.width(label.get(line));
                    g.pose().pushPose();
                    g.pose().translate(c[0] + HEX_W / 2F - lw * 0.375F, c[1] + HEX_H + 1 + line * 7, 0);
                    g.pose().scale(0.75F, 0.75F, 1);
                    g.drawString(font, label.get(line), 0, 0, has ? GOLD : canLearn ? TEXT : DIM, false);
                    g.pose().popPose();
                }
            }
            String id = n.id();
            boolean buyable = canLearn && !has;
            hits.add(new Hit(c[0], c[1], HEX_W, HEX_H, () -> {
                if (id.equals(selectedNode) && buyable) buy(id);
                selectedNode = id;
            }, null));
        }
        if (selected != null) treeCard(g, selected, x + areaW + 8, top, cardW, bottom - top, mouseX, mouseY, awake, learned, level, points, capstoneTaken);
    }

    private static boolean canLearn(SkillCatalog.Node n, boolean awake, boolean has, int level, Set<String> learned, int points, boolean capstoneTaken) {
        return awake && !has && level >= PowerLevels.levelForTier(n.level()) && (n.prerequisite().isEmpty() || learned.contains(n.prerequisite()))
                && points >= n.cost() && !(n.capstone() && capstoneTaken);
    }

    private void treeCard(GuiGraphics g, SkillCatalog.Node n, int x, int y, int w, int h, int mouseX, int mouseY, boolean awake,
                          Set<String> learned, int level, int points, boolean capstoneTaken) {
        panel(g, x, y, w, h);
        String path = n.power();
        boolean has = learned.contains(n.id()), canLearn = canLearn(n, awake, has, level, learned, points, capstoneTaken);
        g.renderFakeItem(PowerIcons.node(n), x + 6, y + 6);
        int after = paragraph(g, Component.literal(n.name()).withStyle(ChatFormatting.BOLD), x + 26, y + 6, w - 32, 2, TEXT);
        String kind = n.capstone() ? "Capstone (one per build)" : n.signature() ? "Signature ability" : n.active() ? "Active skill" : "Passive skill";
        int py = Math.max(after, y + 16);
        if (n.active()) {
            String role = roleOf(n.role(), n.description());
            int pw = pill(g, x + 26, py, roleLabel(role), roleColor(role));
            g.drawString(font, ellipsis(kind, w - 36 - pw), x + 30 + pw, py + 1, MUTED, false);
        } else g.drawString(font, kind, x + 26, py + 1, MUTED, false);
        int cy = py + 14;
        String facts = SkillCatalog.branchName(path, n.branch()) + "  ·  " + n.cost() + (n.cost() == 1 ? " point" : " points")
                + "  ·  Level " + PowerLevels.levelForTier(n.level());
        cy = paragraph(g, Component.literal(facts), x + 6, cy, w - 12, 2, GOLD);
        if (n.active()) { g.drawString(font, n.energy() + " stamina  ·  " + seconds(n.cooldown()) + " cooldown", x + 6, cy, GOLD, false); cy += 10; }
        if (!n.prerequisite().isEmpty()) {
            String parent = SkillCatalog.find(path, n.prerequisite()).map(SkillCatalog.Node::name).orElse("its parent");
            cy = paragraph(g, Component.literal("After " + parent), x + 6, cy, w - 12, 1, learned.contains(n.prerequisite()) ? MUTED : RED);
        }
        int buttonsY = y + h - 18, respecY = buttonsY;
        boolean anyLearned = !learned.isEmpty();
        if (anyLearned) buttonsY -= 16;
        Component status = has ? Component.literal(n.active() ? "Learned. Put it on a key on the Loadout page." : "Learned.").withStyle(ChatFormatting.GREEN)
                : canLearn ? Component.literal("Ready to learn.").withStyle(ChatFormatting.AQUA)
                : n.capstone() && capstoneTaken ? Component.literal("You already chose a capstone.").withStyle(ChatFormatting.RED)
                : !awake ? Component.literal("Your power hasn't woken yet.").withStyle(ChatFormatting.RED)
                : level < PowerLevels.levelForTier(n.level()) ? Component.literal("Reach Level " + PowerLevels.levelForTier(n.level()) + ".").withStyle(ChatFormatting.RED)
                : !n.prerequisite().isEmpty() && !learned.contains(n.prerequisite()) ? Component.literal("Learn the skill above it first.").withStyle(ChatFormatting.RED)
                : Component.literal("Needs " + n.cost() + " points.").withStyle(ChatFormatting.RED);
        List<FormattedCharSequence> statusLines = font.split(status, w - 12);
        int statusY = buttonsY - 4 - statusLines.size() * 10;
        paragraph(g, Component.literal(n.description()), x + 6, cy + 3, w - 12, Math.max(1, (statusY - cy - 6) / 10), TEXT);
        for (FormattedCharSequence line : statusLines) { g.drawString(font, line, x + 6, statusY, TEXT, false); statusY += 10; }
        if (!has) button(g, x + 6, buttonsY, w - 12, 14, Component.literal(canLearn ? "LEARN  ·  " + n.cost() + (n.cost() == 1 ? " POINT" : " POINTS") : "LOCKED")
                .withStyle(ChatFormatting.BOLD), canLearn && !pending, canLearn, mouseX, mouseY, () -> buy(n.id()));
        else button(g, x + 6, buttonsY, w - 12, 14, Component.literal("LEARNED"), false, true, mouseX, mouseY, () -> { });
        if (anyLearned) button(g, x + 6, respecY, w - 12, 14, Component.literal(font.width("RESPEC (refund all)") < w - 20 ? "RESPEC (refund all)" : "RESPEC"), !pending, false, mouseX, mouseY,
                () -> { pending = true; PacketDistributor.sendToServer(new SkillTreeActionPayload(SkillTreeActionPayload.Operation.RESPEC, "", AbilitySlot.PRIMARY)); });
    }

    /**
     * Where each node sits in its branch: {lane, depth}. Depth is the distance from the branch root; every
     * leaf path gets its own lane, left to right in catalog order, and a parent sits centred over its children.
     */
    private static Map<String, double[]> treePlaces(String power) {
        Map<String, double[]> places = new HashMap<>();
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

    // ---- Class

    private static final String[] PERKS = {
            "Always on: your attacks deal 10% more damage.",
            "Always on: you take 12% less damage.",
            "Always on: friends within 12 blocks regenerate, and so do you while one is near."};

    private void renderClass(GuiGraphics g, int mouseX, int mouseY) {
        AbilitySnapshot power = power();
        String path = power.abilityId().getPath();
        boolean awake = power.unlocked();
        Set<String> learned = learned();
        int level = ClientLoadout.get().level(), points = points();
        List<SkillCatalog.Node> styles = SkillCatalog.styles(path);
        var chosen = styles.stream().filter(n -> learned.contains(n.id())).findFirst().orElse(null);
        int x = contentLeft(), top = contentTop(), bottom = contentBottom() - 4, w = contentWidth();
        g.drawString(font, Component.literal("CHOOSE HOW YOU PLAY").withStyle(ChatFormatting.BOLD), x, top, GOLD, false);
        String intro = "Pick one class for your power. Its ability is yours to cast and its perk is always on. "
                + "You can switch later, out of combat, and the points come back.";
        int cardsTop = paragraph(g, Component.literal(intro), x, top + 11, w, 2, MUTED) + 4;
        if (styles.isEmpty()) { g.drawString(font, "This power has no classes yet.", x, cardsTop, DIM, false); return; }
        int gap = 6, cardW = (w - gap * 2) / 3, cardH = bottom - cardsTop;
        for (int i = 0; i < styles.size(); i++) {
            SkillCatalog.Node n = styles.get(i);
            int cx = x + i * (cardW + gap), color = roleColor(n.role());
            boolean mine = n == chosen;
            int refund = chosen == null ? 0 : chosen.cost();
            boolean reachable = level >= PowerLevels.levelForTier(n.level());
            boolean affordable = points + refund >= n.cost();
            boolean over = mouseX >= cx && mouseX < cx + cardW && mouseY >= cardsTop && mouseY < cardsTop + cardH;
            g.fill(cx, cardsTop, cx + cardW, cardsTop + cardH, mine ? 0x88302010 : over ? 0x77201810 : 0x66000000);
            g.fill(cx, cardsTop, cx + cardW, cardsTop + 14, (color & 0x00FFFFFF) | (mine ? 0xCC000000 : 0x77000000));
            if (mine) g.renderOutline(cx, cardsTop, cardW, cardH, color);
            g.drawCenteredString(font, Component.literal(roleLabel(n.role())).withStyle(ChatFormatting.BOLD), cx + cardW / 2, cardsTop + 3, 0xFFFFFFFF);
            int cy = cardsTop + 18;
            g.renderFakeItem(PowerIcons.node(n), cx + 5, cy);
            cy = Math.max(paragraph(g, Component.literal(n.name()).withStyle(ChatFormatting.BOLD), cx + 25, cy + 4, cardW - 30, 2, TEXT), cy + 18) + 2;
            cy = paragraph(g, Component.literal(PERKS[Math.max(0, SkillCatalog.ROLES.indexOf(n.role()))]), cx + 5, cy, cardW - 10, 3, color) + 3;
            g.drawString(font, ellipsis(n.energy() + " stamina · " + seconds(n.cooldown()), cardW - 10), cx + 5, cy, GOLD, false);
            cy += 12;
            int buttonY = cardsTop + cardH - 18;
            int keyIndex = mine ? ClientLoadout.keyOf(n.id()) : 0;
            int statusLines = mine ? 1 : 0;
            int room = (buttonY - 4 - statusLines * 10 - cy) / 10;
            List<FormattedCharSequence> desc = font.split(Component.literal(n.description()), cardW - 10);
            paragraph(g, Component.literal(n.description()), cx + 5, cy, cardW - 10, Math.max(1, room), TEXT);
            if (desc.size() > room && over) hover = Component.literal(n.description());
            if (mine) g.drawString(font, ellipsis(keyIndex > 0 ? "On " + key(keyIndex) : "On the ability wheel", cardW - 10),
                    cx + 5, buttonY - 12, keyIndex > 0 ? GREEN : GOLD, false);
            Component label = mine ? Component.literal("YOUR CLASS ✔")
                    : !awake ? Component.literal("NOT AWAKE")
                    : !reachable ? Component.literal("LEVEL " + PowerLevels.levelForTier(n.level()))
                    : !affordable ? Component.literal("NEEDS " + n.cost() + " POINTS")
                    : Component.literal((chosen == null ? "CHOOSE" : "SWITCH") + "  ·  " + n.cost() + " PTS");
            boolean enabled = !mine && awake && reachable && affordable && !pending;
            button(g, cx + 5, buttonY, cardW - 10, 14, label.copy().withStyle(ChatFormatting.BOLD), enabled, mine || enabled, mouseX, mouseY, () -> buy(n.id()));
        }
    }

    // ------------------------------------------------------------------

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (page == Tab.LOADOUT) { scroll -= (int) Math.signum(scrollY); return true; }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Tab and Shift+Tab move between the pages.
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            Tab[] order = {Tab.LOADOUT, Tab.TREE, Tab.CLASS};
            int i = java.util.Arrays.asList(order).indexOf(page);
            page(order[Math.floorMod(i + (hasShiftDown() ? -1 : 1), order.length)]);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
