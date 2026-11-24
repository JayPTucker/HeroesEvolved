package com.jayptucker.heroesevolved.admin;

import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.guidance.GuidanceData;
import com.jayptucker.heroesevolved.network.AdminActionPayload;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class AdminCommandGameTests {
    @GameTest(template = "rewind_empty", batch = "admin.commands")
    public static void catalogueDiscoversNewBranchesAndRespectsPermissions(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "command-catalogue"));
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        var root = dispatcher.register(Commands.literal("heroesevolved").requires(s -> s.hasPermission(2))
                .then(Commands.literal("status").executes(c -> 1))
                .then(Commands.literal("restricted").requires(s -> s.hasPermission(4)).executes(c -> 1)));
        var source = p.createCommandSourceStack().withPermission(2);
        h.assertTrue(AdminCommands.describe(dispatcher, source).equals(java.util.List.of("/heroesevolved status")), "Only usable command templates are listed");
        root.addChild(Commands.literal("new_debug_tool").executes(c -> 1).build());
        h.assertTrue(AdminCommands.describe(dispatcher, source).contains("/heroesevolved new_debug_tool"), "A new registered branch appears without changing the menu");
        h.assertTrue(AdminCommands.describe(dispatcher, source.withPermission(0)).isEmpty(), "A non-operator cannot browse the operator root");
        var actual = AdminCommands.describe(h.getLevel().getServer().getCommands().getDispatcher(), source);
        for (String prefix : new String[]{"/heroesevolved trace clear", "/heroesevolved village project", "/heroesevolved blueprint place", "/heroesevolved mimic gift", "/heroesevolved prologue jump"})
            h.assertTrue(actual.stream().anyMatch(c -> c.startsWith(prefix)), "Real registered tools are exposed: " + prefix);
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "admin.commands")
    public static void unprivilegedRequestsCannotResetSomeoneElsesTipsOrSpawnJournals(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "guidance-not-admin"));
        var before = GuidanceData.empty().mark(GuidanceData.Hint.WELCOME).enabled(false);
        p.setData(ModDataAttachments.GUIDANCE, before);
        AdminService.handle(p, AdminActionPayload.of(AdminActionPayload.Action.RESET_GUIDANCE, p.getUUID()));
        AdminService.handle(p, AdminActionPayload.of(AdminActionPayload.Action.GIVE_SURVIVOR_JOURNAL, p.getUUID()));
        h.assertTrue(p.getData(ModDataAttachments.GUIDANCE).equals(before), "Only an operator may reset saved guidance");
        h.assertTrue(p.getInventory().items.stream().noneMatch(s -> s.is(Items.WRITABLE_BOOK)), "A forged admin request grants no books");
        h.assertTrue(AdminCommands.describe(p).isEmpty(), "The server catalogue is operator-only too");
        h.succeed();
    }
}
