package com.jayptucker.heroesevolved.structure;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Rotation;

import java.util.Arrays;

/** /heroesevolved blueprint place &lt;name&gt; [rotation]: places a shipped blueprint at your feet, for previewing builds. */
public final class BlueprintCommands {
    private BlueprintCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("blueprint").then(Commands.literal("place")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> place(c.getSource(), StringArgumentType.getString(c, "name"), Rotation.NONE))
                        .then(Commands.argument("rotation", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Rotation.values()).map(Rotation::getSerializedName), b))
                                .executes(c -> place(c.getSource(), StringArgumentType.getString(c, "name"),
                                        Rotation.valueOf(StringArgumentType.getString(c, "rotation").toUpperCase()))))));
    }

    private static int place(CommandSourceStack source, String name, Rotation rotation) {
        try {
            Blueprint blueprint = Blueprint.load(name);
            BlockPos at = BlockPos.containing(source.getPosition());
            var marked = blueprint.place(source.getLevel(), at, rotation);
            source.sendSuccess(() -> Component.literal("Placed " + blueprint.name + " (" + blueprint.width() + "x" + blueprint.height() + "x"
                    + blueprint.depth() + ") at " + at.toShortString() + (marked.isEmpty() ? "" : "; markers: " + marked.keySet())), true);
            return 1;
        } catch (RuntimeException error) {
            source.sendFailure(Component.literal(error.getMessage() == null ? error.toString() : error.getMessage()));
            return 0;
        }
    }
}
