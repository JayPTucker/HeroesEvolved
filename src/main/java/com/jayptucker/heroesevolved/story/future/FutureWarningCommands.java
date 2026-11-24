package com.jayptucker.heroesevolved.story.future;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

public final class FutureWarningCommands {
    private FutureWarningCommands() {}
    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        var root = Commands.literal("futurewarning").requires(s -> s.hasPermission(2));
        for (String action : new String[]{"omen", "now", "cancel", "reset", "status"})
            root.then(Commands.literal(action).then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                var p = EntityArgument.getPlayer(c, "player");
                boolean ok = true;
                switch (action) {
                    case "omen", "now" -> ok = FutureWarning.start(p, action.equals("now"));
                    case "cancel", "reset" -> FutureWarning.cancel(p, action.equals("reset"));
                }
                String result = ok ? FutureWarning.status(p.getServer(), p) : "Encounter refused: player busy, unsafe, or no clear space behind them.";
                c.getSource().sendSuccess(() -> Component.literal(result), false);
                return ok ? 1 : 0;
            })));
        return root;
    }
}
