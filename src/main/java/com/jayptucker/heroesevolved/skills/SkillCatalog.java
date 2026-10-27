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
                       int energy, int cooldown, int cost, int level, boolean active, String prerequisite,
                       String mechanic, boolean capstone) {
        public ResourceLocation actionId() { return ResourceLocation.fromNamespaceAndPath("heroesevolved", id); }
        public AbilityAction action() {
            return SimpleAction.builder(id, level()).cost(() -> energy, () -> cooldown)
                    .canUse(c -> mechanic.isEmpty() ? SkillActionService.canUse(c, branch)
                            : com.jayptucker.heroesevolved.expansion.ExpansionSkills.canUse(c, mechanic))
                    .activate(c -> mechanic.isEmpty() ? SkillActionService.activate(c, branch)
                            : com.jayptucker.heroesevolved.expansion.ExpansionSkills.activate(c, mechanic)).build();
        }
    }
    private static final Map<String, List<String>> BRANCHES = new HashMap<>();
    public static String branchName(String power, int branch) {
        return BRANCHES.getOrDefault(power, List.of("Control", "Force", "Survival")).get(branch);
    }
    public static int rows(String power) { return nodes(power).stream().mapToInt(Node::tier).max().orElse(2) + 1; }
    private static final Map<String, List<Node>> TREES = load();
    private SkillCatalog() {}
    private static Map<String, List<Node>> load() {
        try (var stream = SkillCatalog.class.getResourceAsStream("/data/heroesevolved/skill_trees.json")) {
            if (stream == null) throw new IllegalStateException("Missing skill trees");
            JsonArray trees = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray();
            Map<String, List<Node>> result = new LinkedHashMap<>();
            for (var tree : trees) {
                var object = tree.getAsJsonObject(); String power = object.get("power").getAsString();
                if (object.has("branch_names")) BRANCHES.put(power, object.getAsJsonArray("branch_names").asList().stream().map(JsonElement::getAsString).toList());
                List<Node> nodes = new ArrayList<>(); int branch = 0;
                for (var lane : object.getAsJsonArray("branches")) {
                    int tier = 0; String parent = "";
                    for (var item : lane.getAsJsonArray()) {
                        var n = item.getAsJsonObject();
                        String id = n.has("id") ? n.get("id").getAsString() : power + "_" + branch + "_" + tier;
                        boolean active = n.has("active") ? n.get("active").getAsBoolean() : tier == 1;
                        nodes.add(new Node(id, power, branch, tier, n.get("name").getAsString(), n.get("description").getAsString(),
                                active ? n.get("energy").getAsInt() : 0, active ? n.get("cooldown").getAsInt() : 0,
                                n.has("cost") ? n.get("cost").getAsInt() : tier == 0 ? 1 : 2,
                                n.has("level") ? n.get("level").getAsInt() : tier == 0 ? 1 : tier == 1 ? 2 : 4,
                                active, n.has("requires") ? n.get("requires").getAsString() : parent,
                                n.has("mechanic") ? n.get("mechanic").getAsString() : "",
                                n.has("capstone") && n.get("capstone").getAsBoolean()));
                        parent = id; tier++;
                    }
                    if (tier < 1 || tier > 12) throw new IllegalStateException("Invalid branch length");
                    branch++;
                }
                if (branch != 3 || nodes.stream().map(Node::id).distinct().count() != nodes.size()
                        || nodes.stream().anyMatch(n -> n.cost < 1 || n.level < 1 || !n.prerequisite.isEmpty() && nodes.stream().noneMatch(parent -> parent.id.equals(n.prerequisite) && parent.tier < n.tier))
                        || result.put(power, List.copyOf(nodes)) != null)
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
