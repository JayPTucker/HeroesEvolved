package com.jayptucker.heroesevolved.admin;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.TreeSet;

/** The admin browser reads the live command tree, including branches registered by other parts of the mod. */
public final class AdminCommands {
    private static final List<String> ROOTS = List.of("heroesevolved", "lastday");
    private AdminCommands() {}

    public static List<String> describe(ServerPlayer admin) {
        if (!AdminService.isAdmin(admin)) return List.of();
        return describe(admin.getServer().getCommands().getDispatcher(), admin.createCommandSourceStack());
    }

    static List<String> describe(CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source) {
        var usages = new TreeSet<String>();
        for (String name : ROOTS) {
            var root = dispatcher.getRoot().getChild(name);
            if (root == null || !root.canUse(source)) continue;
            for (String usage : dispatcher.getAllUsage(root, source, true)) usages.add("/" + name + (usage.isEmpty() ? "" : " " + usage));
        }
        return List.copyOf(usages);
    }
}
