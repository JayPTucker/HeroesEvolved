package com.jayptucker.heroesevolved.skills;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Writes every action's stamina cost, cooldown and unlock tier, before and after the balance table, to
 * {@code build/balance/actions.json} (relative to the test server's folder). {@code tools/balance.py} reads it.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class BalanceDumpGameTests {
    @GameTest(template = "rewind_empty", batch = "balance.dump", timeoutTicks = 200)
    public static void dumpEveryActionsCost(GameTestHelper h) {
        JsonArray out = new JsonArray();
        for (var entry : AbilityRegistry.ABILITIES.entrySet()) {
            var powerId = entry.getKey().location();
            var power = entry.getValue();
            var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "balance-dump"));
            p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
            p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(powerId).unlock(powerId));
            for (var slot : AbilitySlot.values()) {
                AbilityAction action = power.actions().get(slot);
                if (action != null) out.add(describe(p, powerId.getPath(), "core:" + slot.name(), action, action.definition().unlockLevel()));
            }
            for (var node : SkillCatalog.all(powerId.getPath())) {
                if (!node.active()) continue;
                try {
                    AbilityAction action = node.action();
                    var o = describe(p, powerId.getPath(), node.style() ? "class:" + node.role() : node.signature() ? "signature" : "skill:" + node.branch() + "." + node.tier(), action, node.level());
                    o.addProperty("json_energy", node.energy());
                    o.addProperty("json_cooldown", node.cooldown());
                    o.addProperty("skill_points", node.cost());
                    out.add(o);
                } catch (RuntimeException e) {
                    JsonObject o = new JsonObject(); o.addProperty("power", powerId.getPath()); o.addProperty("id", node.id()); o.addProperty("error", e.toString()); out.add(o);
                }
            }
        }
        try {
            Path file = Path.of("build", "balance", "actions.json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(out));
        } catch (java.io.IOException e) { throw new AssertionError(e); }
        h.succeed();
    }

    private static JsonObject describe(FakePlayer p, String power, String kind, AbilityAction action, int unlock) {
        JsonObject o = new JsonObject();
        var id = action.definition().id();
        o.addProperty("power", power);
        o.addProperty("id", id.getPath());
        o.addProperty("kind", kind);
        o.addProperty("unlock_tier", unlock);
        JsonArray raw = new JsonArray(), tuned = new JsonArray();
        for (int tier = 1; tier <= 5; tier++) {
            PlayerProgressionService.setLevel(p, tier);
            var c = new AbilityUseContext(p, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("heroesevolved", power), tier, false);
            JsonObject r = new JsonObject(), t = new JsonObject();
            try {
                int e = action.energyCost(c), cd = action.cooldownTicks(c);
                r.addProperty("energy", e); r.addProperty("cooldown", cd);
                t.addProperty("energy", com.jayptucker.heroesevolved.ability.PowerBalance.energy(id, e));
                t.addProperty("cooldown", com.jayptucker.heroesevolved.ability.PowerBalance.cooldown(id, cd));
            } catch (RuntimeException e) { r.addProperty("error", e.toString()); }
            raw.add(r); tuned.add(t);
        }
        o.add("raw", raw); o.add("tuned", tuned);
        return o;
    }
}
