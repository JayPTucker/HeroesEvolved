package com.jayptucker.heroesevolved.mimicry;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Comparator;

/** {@code /heroesevolved mimic gift <power>}: the nearest villager becomes gifted (for admins and previews). */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class MimicCommands {
    private MimicCommands() {}

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("heroesevolved").requires(source -> source.hasPermission(2))
                .then(Commands.literal("mimic").then(Commands.literal("gift")
                        .then(Commands.argument("power", ResourceLocationArgument.id())
                                .suggests((context, builder) -> {
                                    AbilityRegistry.ABILITIES.keySet().forEach(id -> builder.suggest(id.toString()));
                                    return builder.buildFuture();
                                })
                                .executes(context -> gift(context.getSource(), ResourceLocationArgument.getId(context, "power")))))));
    }

    private static int gift(CommandSourceStack source, ResourceLocation power) {
        if (!AbilityRegistry.ABILITIES.containsKey(power)) {
            source.sendFailure(Component.literal("No such power: " + power));
            return 0;
        }
        Vec3 at = source.getPosition();
        Villager villager = source.getLevel().getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(at, at).inflate(16))
                .stream().min(Comparator.comparingDouble(v -> v.distanceToSqr(at))).orElse(null);
        if (villager == null) {
            source.sendFailure(Component.literal("No villager within 16 blocks."));
            return 0;
        }
        GiftedVillagers.give(villager, power);
        source.sendSuccess(() -> Component.literal("That villager is now gifted with " + power + "."), true);
        return 1;
    }
}
