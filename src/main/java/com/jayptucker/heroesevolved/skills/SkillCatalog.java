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
                       String mechanic, boolean capstone, String icon, boolean signature, String role, boolean style) {
        /** Branch index of the class abilities, which sit beside the three branches. */
        public static final int STYLE_BRANCH = 3;
        public ResourceLocation actionId() { return ResourceLocation.fromNamespaceAndPath("heroesevolved", id); }
        public AbilityAction action() {
            if (style) return com.jayptucker.heroesevolved.styles.StyleSkills.action(this);
            if (power.equals("vibe")) return com.jayptucker.heroesevolved.vibe.VibeSkills.action(this);
            if (power.equals("flight")) return com.jayptucker.heroesevolved.flight.FlightSkillService.action(this);
            if (power.equals("telekinesis")) return com.jayptucker.heroesevolved.telekinesis.TelekinesisSkillService.action(this);
            if (power.equals("technopathy")) return SimpleAction.builder(id, level()).cost(() -> energy, () -> cooldown)
                    .canUse(com.jayptucker.heroesevolved.technopathy.TechnopathyService::canUse)
                    .activate(c -> mechanic.isEmpty() ? com.jayptucker.heroesevolved.technopathy.TechnopathyService.skill(c, branch)
                            : com.jayptucker.heroesevolved.technopathy.TechnopathyService.technique(c, mechanic)).build();
            if (power.equals("matter_assembly")) return SimpleAction.builder(id, level()).cost(() -> energy, () -> cooldown)
                    .canUse(c -> com.jayptucker.heroesevolved.assembly.MatterAssemblyService.canUse(c, mechanic.isEmpty() ? id : mechanic))
                    .activate(c -> com.jayptucker.heroesevolved.assembly.MatterAssemblyService.activate(c, mechanic.isEmpty() ? id : mechanic)).build();
            return SimpleAction.builder(id, level()).cost(() -> energy, () -> cooldown)
                    .canUse(c -> mechanic.isEmpty() ? SkillActionService.canUse(c, branch)
                            : com.jayptucker.heroesevolved.expansion.ExpansionSkills.canUse(c, mechanic))
                    .activate(c -> mechanic.isEmpty() ? SkillActionService.activate(c, branch)
                            : com.jayptucker.heroesevolved.expansion.ExpansionSkills.activate(c, mechanic))
                    .followUp(c -> power.equals("cryokinesis") ? (mechanic.isEmpty()
                            ? com.jayptucker.heroesevolved.cryo.CryoCombatSkills.followUp(c, branch)
                            : com.jayptucker.heroesevolved.cryo.CryoTechniqueService.followUp(c, mechanic))
                            : power.equals("regeneration") && mechanic.isEmpty()
                            ? com.jayptucker.heroesevolved.regeneration.RegenSkills.followUp(c, branch)
                            : power.equals("telepathy") && mechanic.isEmpty()
                            && com.jayptucker.heroesevolved.telepathy.TelepathySkills.followUp(c, branch)).build();
        }
    }
    private static final Map<String, List<String>> BRANCHES = new HashMap<>();
    /** The roles a class ability can play. */
    public static final List<String> ROLES = List.of("offense", "defense", "support");
    private static final Map<String, List<Node>> STYLES = new HashMap<>();
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
                                n.has("capstone") && n.get("capstone").getAsBoolean(),
                                n.has("icon") ? n.get("icon").getAsString() : "",
                                // A signature ability is a showpiece beyond the eighteen-node tree: always active, never a capstone.
                                n.has("signature") && n.get("signature").getAsBoolean(),
                                n.has("role") ? n.get("role").getAsString() : "", false));
                        parent = id; tier++;
                    }
                    if (tier < 1 || tier > 12) throw new IllegalStateException("Invalid branch length");
                    branch++;
                }
                // The class abilities: one Offense, one Defense and one Support, of which a build keeps one.
                List<Node> styles = new ArrayList<>();
                if (object.has("styles")) {
                    int index = 0;
                    for (var item : object.getAsJsonArray("styles")) {
                        var n = item.getAsJsonObject();
                        styles.add(new Node(n.get("id").getAsString(), power, Node.STYLE_BRANCH, index++, n.get("name").getAsString(),
                                n.get("description").getAsString(), n.get("energy").getAsInt(), n.get("cooldown").getAsInt(),
                                n.has("cost") ? n.get("cost").getAsInt() : 3, n.has("level") ? n.get("level").getAsInt() : 3, true, "",
                                n.get("mechanic").getAsString(), false, n.has("icon") ? n.get("icon").getAsString() : "", false,
                                n.get("role").getAsString(), true));
                    }
                }
                if (styles.stream().anyMatch(n -> !ROLES.contains(n.role)) || styles.stream().map(Node::role).distinct().count() != styles.size()
                        || styles.stream().anyMatch(sn -> nodes.stream().anyMatch(n -> n.id.equals(sn.id))))
                    throw new IllegalStateException("Invalid class abilities: " + power);
                STYLES.put(power, List.copyOf(styles));
                if (branch != 3 || nodes.stream().map(Node::id).distinct().count() != nodes.size()
                        || nodes.stream().anyMatch(n -> n.cost < 1 || n.level < 1 || !n.prerequisite.isEmpty() && nodes.stream().noneMatch(parent -> parent.id.equals(n.prerequisite) && parent.tier < n.tier))
                        || nodes.stream().anyMatch(n -> n.signature && (!n.active || n.capstone))
                        || result.put(power, List.copyOf(nodes)) != null)
                    throw new IllegalStateException("Invalid skill tree: " + power);
            }
            return Map.copyOf(result);
        } catch (IOException error) { throw new ExceptionInInitializerError(error); }
    }
    /** The eighteen-node tree (and any signature ability). */
    public static List<Node> nodes(String power) { return TREES.getOrDefault(power, List.of()); }
    /** The class abilities, in role order: Offense, Defense, Support. A build keeps one at a time. */
    public static List<Node> styles(String power) { return STYLES.getOrDefault(power, List.of()); }
    /** Everything that can be learned: the tree, then the class abilities. */
    public static List<Node> all(String power) {
        List<Node> all = new ArrayList<>(nodes(power));
        all.addAll(styles(power));
        return all;
    }
    /** The class ability this build has chosen, if any. */
    public static Optional<Node> chosenStyle(String power, SkillTreeData.Build build) {
        return styles(power).stream().filter(n -> build.learned().contains(n.id)).findFirst();
    }
    public static Optional<Node> find(String power, String id) {
        return all(power).stream().filter(n -> n.id.equals(id)).findFirst();
    }
    public static int spent(String power, SkillTreeData.Build build) {
        return all(power).stream().filter(n -> build.learned().contains(n.id)).mapToInt(Node::cost).sum();
    }
    public static int available(String power, SkillTreeData.Build build) { return available(power, build, 0); }
    /** With {@code storyPoints} from notes the player has found (see PowerJourney). */
    public static int available(String power, SkillTreeData.Build build, int storyPoints) {
        return Math.max(0, build.earned() + storyPoints - spent(power, build));
    }
}
