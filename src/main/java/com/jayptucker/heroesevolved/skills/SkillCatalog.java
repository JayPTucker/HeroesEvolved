package com.jayptucker.heroesevolved.skills;

import com.google.gson.*;
import com.jayptucker.heroesevolved.ability.*;
import net.minecraft.resources.ResourceLocation;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Shared by the server and inventory UI; all purchases are validated on the server. */
public final class SkillCatalog {
    public record Node(String id, String power, int branch, int tier, String name, String description,
                       int energy, int cooldown) {
        public int cost() { return tier == 0 ? 1 : 2; }
        public int level() { return tier == 0 ? 1 : tier == 1 ? 2 : 4; }
        public boolean active() { return tier == 1; }
        public String prerequisite() { return tier == 0 ? "" : power + "_" + branch + "_" + (tier - 1); }
        public ResourceLocation actionId() { return ResourceLocation.fromNamespaceAndPath("heroesevolved", id); }
        public AbilityAction action() {
            return SimpleAction.builder(id, level()).cost(() -> energy, () -> cooldown)
                    .canUse(c -> SkillActionService.canUse(c, branch))
                    .activate(c -> SkillActionService.activate(c, branch)).build();
        }
    }
    private static final Map<String, List<Node>> TREES = load();
    private SkillCatalog() {}
    private static Map<String, List<Node>> load() {
        try (var stream = SkillCatalog.class.getResourceAsStream("/data/heroesevolved/skill_trees.json")) {
            if (stream == null) throw new IllegalStateException("Missing skill trees");
            JsonArray trees = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray();
            Map<String, List<Node>> result = new LinkedHashMap<>();
            for (var tree : trees) {
                var object = tree.getAsJsonObject(); String power = object.get("power").getAsString();
                List<Node> nodes = new ArrayList<>(); int branch = 0;
                for (var lane : object.getAsJsonArray("branches")) {
                    int tier = 0;
                    for (var item : lane.getAsJsonArray()) {
                        var n = item.getAsJsonObject();
                        nodes.add(new Node(power + "_" + branch + "_" + tier, power, branch, tier,
                                n.get("name").getAsString(), n.get("description").getAsString(),
                                tier == 1 ? n.get("energy").getAsInt() : 0,
                                tier == 1 ? n.get("cooldown").getAsInt() : 0));
                        tier++;
                    }
                    if (tier != 3) throw new IllegalStateException("Every skill branch needs three nodes");
                    branch++;
                }
                if (branch != 3 || result.put(power, List.copyOf(nodes)) != null)
                    throw new IllegalStateException("Invalid skill tree: " + power);
            }
            return Map.copyOf(result);
        } catch (IOException error) { throw new ExceptionInInitializerError(error); }
    }
    public static List<Node> nodes(String power) { return TREES.getOrDefault(power, List.of()); }
    public static Optional<Node> find(String power, String id) {
        return nodes(power).stream().filter(n -> n.id.equals(id)).findFirst();
    }
    public static int spent(String power, SkillTreeData.Build build) {
        return nodes(power).stream().filter(n -> build.learned().contains(n.id)).mapToInt(Node::cost).sum();
    }
    public static int available(String power, SkillTreeData.Build build) { return Math.max(0, build.earned() - spent(power, build)); }
}
