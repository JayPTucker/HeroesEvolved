package com.jayptucker.heroesevolved.guidance;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class GuidanceGameTests {
    @GameTest(template = "rewind_empty", batch = "guidance")
    public static void relevantBlockersTakePriorityAndReadHintsDoNotRepeat(GameTestHelper h) {
        var dormant = new GuidanceData.Context(false, false, false, false, true, true, true);
        var active = new GuidanceData.Context(false, true, false, false, true, true, false);
        var portal = new GuidanceData.Context(true, false, false, false, false, false, false);
        var data = GuidanceData.empty();
        h.assertTrue(data.next(dormant) == GuidanceData.Hint.WELCOME, "Dormant players can discover the guide");
        h.assertTrue(data.next(portal) == GuidanceData.Hint.PORTAL, "Explain the unusual portal rule where the player encounters it");
        data = data.mark(GuidanceData.Hint.WELCOME);
        h.assertTrue(data.next(dormant) == null, "No fighting or skill tips before awakening, even with stale secondary snapshots");
        h.assertTrue(data.next(active) == GuidanceData.Hint.AWAKENED, "Introduce using powers before spending points");
        data = data.mark(GuidanceData.Hint.AWAKENED).mark(GuidanceData.Hint.SKILLS).mark(GuidanceData.Hint.STYLE);
        h.assertTrue(data.next(active) == null, "Read notes do not repeat");
        h.assertTrue(data.enabled(false).next(portal) == null, "The off switch suppresses contextual notes too");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "guidance")
    public static void hintsAndPreferenceSurviveSavingAndRemainPersonal(GameTestHelper h) {
        var oldSave = GuidanceData.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
        h.assertTrue(oldSave.enabled() && oldSave.seen() == 0, "Existing characters start with useful tips enabled");
        var first = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "guide-one"));
        var second = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "guide-two"));
        GuidanceService.apply(first, 0, GuidanceData.Hint.WELCOME.id);
        GuidanceService.apply(first, 0, GuidanceData.Hint.PORTAL.id);
        GuidanceService.apply(first, 1, -1);
        var before = first.getData(ModDataAttachments.GUIDANCE);
        var saved = GuidanceData.CODEC.encodeStart(JsonOps.INSTANCE, before).getOrThrow();
        var loaded = GuidanceData.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        h.assertTrue(loaded.equals(before) && !loaded.enabled() && loaded.hasSeen(GuidanceData.Hint.WELCOME)
                && loaded.hasSeen(GuidanceData.Hint.PORTAL) && !loaded.hasSeen(GuidanceData.Hint.SKILLS),
                "Reconnects preserve both the off switch and individual receipts");
        h.assertTrue(second.getData(ModDataAttachments.GUIDANCE).equals(GuidanceData.empty()), "Another player has their own tips");
        first.setData(ModDataAttachments.GUIDANCE, loaded);
        GuidanceService.apply(first, 2, -1);
        h.assertTrue(first.getData(ModDataAttachments.GUIDANCE).equals(before.enabled(true)), "Enabling tips never forgets the ones already read");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "guidance")
    public static void duplicateAndInvalidReceiptsCannotCorruptPreferences(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "guide-invalid"));
        GuidanceService.apply(p, 0, GuidanceData.Hint.STAMINA.id);
        var expected = p.getData(ModDataAttachments.GUIDANCE);
        GuidanceService.apply(p, 0, GuidanceData.Hint.STAMINA.id);
        for (int id : new int[]{-1, 63, 64, Integer.MIN_VALUE, Integer.MAX_VALUE}) GuidanceService.apply(p, 0, id);
        GuidanceService.apply(p, 99, GuidanceData.Hint.PORTAL.id);
        h.assertTrue(p.getData(ModDataAttachments.GUIDANCE).equals(expected), "Only known hints and operations can change the saved ledger");
        var ids = new HashSet<Integer>();
        for (var hint : GuidanceData.Hint.values())
            h.assertTrue(hint.id >= 0 && hint.id < 63 && ids.add(hint.id), "Save IDs are unique and fit the receipt mask");
        h.succeed();
    }
}
