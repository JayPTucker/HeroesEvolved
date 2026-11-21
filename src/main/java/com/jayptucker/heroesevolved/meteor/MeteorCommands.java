package com.jayptucker.heroesevolved.meteor;

import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.village.Villages;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** {@code /heroesevolved meteor drop|great|shower on|off|clear|status}. */
public final class MeteorCommands {
    private MeteorCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> meteor() {
        return Commands.literal("meteor")
                .then(Commands.literal("drop").executes(MeteorCommands::drop))
                .then(Commands.literal("great").executes(MeteorCommands::great))
                .then(Commands.literal("shower")
                        .then(Commands.literal("on").executes(c -> shower(c, true)))
                        .then(Commands.literal("off").executes(c -> shower(c, false))))
                .then(Commands.literal("clear").executes(c -> {
                    int n = Meteors.cancelAll();
                    c.getSource().sendSuccess(() -> Component.literal(n + " meteor(s) burnt up."), true);
                    return n;
                }))
                .then(Commands.literal("status").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal(Starfall.describe(c.getSource().getServer())), false);
                    return 1;
                }));
    }

    /** Where the source is looking, up to 96 blocks off; failing that, where it stands. */
    private static Vec3 target(CommandSourceStack source) {
        Vec3 at = source.getPosition();
        if (source.getEntity() != null) {
            Vec3 eye = source.getEntity().getEyePosition(), to = eye.add(source.getEntity().getLookAngle().scale(96));
            var hit = source.getLevel().clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source.getEntity()));
            if (hit.getType() == HitResult.Type.BLOCK) at = hit.getLocation();
        }
        return Meteors.ground(source.getLevel(), at);
    }

    private static int drop(CommandContext<CommandSourceStack> c) {
        Vec3 at = target(c.getSource());
        Meteors.drop(c.getSource().getLevel(), at);
        c.getSource().sendSuccess(() -> Component.literal("A falling star is coming down at " + BlockPos.containing(at).toShortString() + " in thirty seconds."), true);
        return 1;
    }

    private static int great(CommandContext<CommandSourceStack> c) {
        BlockPos near = BlockPos.containing(c.getSource().getPosition());
        boolean village = Villages.hasBell(c.getSource().getLevel(), near);
        Meteor m = Meteors.great(c.getSource().getLevel(), near, HeroesEvolvedConfig.COMMON.meteorGreatTicks.get());
        c.getSource().sendSuccess(() -> Component.literal("A great meteor is coming down on " + (village ? "the village" : "this spot")
                + " at " + BlockPos.containing(m.land()).toShortString() + "."), true);
        return 1;
    }

    private static int shower(CommandContext<CommandSourceStack> c, boolean start) {
        Starfall.force(c.getSource().getServer(), start);
        c.getSource().sendSuccess(() -> Component.literal(start ? "Stars are falling." : "The starfall is over."), true);
        return 1;
    }
}
