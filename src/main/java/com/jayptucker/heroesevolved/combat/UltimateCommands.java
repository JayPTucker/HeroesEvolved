package com.jayptucker.heroesevolved.combat;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The class Ultimates' admin controls (the Commands tab finds them under {@code /heroesevolved}, and the console's
 * events page has an "Ultimates" row that calls the same methods):
 * <ul>
 *     <li>{@code ultimate list}: every Ultimate playing now, whose, and how far through.</li>
 *     <li>{@code ultimate cast [player] [mechanic]}: casts one for a player, as if they pressed its key but without its
 *     stamina or wait; the player's own class if no mechanic is named, else any of their power's three. Offense still
 *     needs a foe in front of them.</li>
 *     <li>{@code ultimate stop [player]}: ends a player's Ultimate now, without its payoff (a held foe is let go).</li>
 *     <li>{@code ultimate reset [player]}: their class's Ultimate is ready again.</li>
 * </ul>
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class UltimateCommands {
    private UltimateCommands() {}

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("heroesevolved").requires(source -> source.hasPermission(2))
                .then(Commands.literal("ultimate")
                        .then(Commands.literal("list").executes(c -> reply(c.getSource(), status(c.getSource().getServer(), null), true)))
                        .then(Commands.literal("cast")
                                .executes(c -> reply(c.getSource(), cast(c.getSource().getPlayerOrException(), null), false))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(c -> reply(c.getSource(), cast(EntityArgument.getPlayer(c, "target"), null), false))
                                        .then(Commands.argument("mechanic", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Ultimates.mechanics(), b))
                                                .executes(c -> reply(c.getSource(), cast(EntityArgument.getPlayer(c, "target"),
                                                        StringArgumentType.getString(c, "mechanic")), false)))))
                        .then(Commands.literal("stop")
                                .executes(c -> reply(c.getSource(), stop(c.getSource().getPlayerOrException()), false))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(c -> reply(c.getSource(), stop(EntityArgument.getPlayer(c, "target")), false))))
                        .then(Commands.literal("reset")
                                .executes(c -> reply(c.getSource(), reset(c.getSource().getPlayerOrException()), false))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(c -> reply(c.getSource(), reset(EntityArgument.getPlayer(c, "target")), false))))));
    }

    private static int reply(CommandSourceStack source, String text, boolean quiet) {
        source.sendSuccess(() -> Component.literal(text), !quiet);
        return 1;
    }

    /** The player's power and the class node they have chosen for it, if any. */
    private static Optional<SkillCatalog.Node> chosen(ServerPlayer p) {
        return PlayerAbilityService.getData(p).assignedPower().filter(e -> e.getValue().isUnlocked())
                .flatMap(e -> SkillCatalog.chosenStyle(e.getKey().getPath(), SkillTreeService.build(p, e.getKey())));
    }

    private static Optional<ResourceLocation> power(ServerPlayer p) {
        return PlayerAbilityService.getData(p).assignedPower().filter(e -> e.getValue().isUnlocked()).map(java.util.Map.Entry::getKey);
    }

    /** One line for the console: what is playing (everyone, or one player), and that player's class. */
    public static String status(MinecraftServer server, ServerPlayer target) {
        var live = Ultimates.live().stream().filter(r -> target == null || r.player == target).toList();
        String playing = live.isEmpty() ? "No Ultimate playing" + (target == null ? "." : " for " + target.getGameProfile().getName() + ".")
                : live.stream().map(r -> r.player.getGameProfile().getName() + ": " + r.mechanic + " " + r.age + "/" + r.ticks()
                + (r.foe != null ? " on " + r.foe.getName().getString() : ""))
                .collect(Collectors.joining("; ", "Playing: ", "."));
        if (target == null) return playing;
        String mine = chosen(target).map(n -> " Class: " + n.role() + " (" + n.mechanic() + ")"
                + waitText(target, n)).orElse(" No class chosen.");
        return playing + mine;
    }

    private static String waitText(ServerPlayer p, SkillCatalog.Node node) {
        long left = CooldownService.getRemainingTicks(p, actionId(node));
        return left > 0 ? ", ready in " + (left + 19) / 20 + " s." : ", ready.";
    }

    private static ResourceLocation actionId(SkillCatalog.Node node) {
        return ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, node.id());
    }

    /** Casts an Ultimate for {@code p} (their class's if {@code mechanic} is null), free of stamina and wait. */
    public static String cast(ServerPlayer p, String mechanic) {
        var power = power(p);
        if (power.isEmpty()) return p.getGameProfile().getName() + " has no awakened power.";
        String name = p.getGameProfile().getName();
        SkillCatalog.Node node = mechanic == null ? chosen(p).orElse(null)
                : SkillCatalog.styles(power.get().getPath()).stream().filter(n -> n.mechanic().equals(mechanic)).findFirst().orElse(null);
        if (node == null) return mechanic == null ? name + " has no class: name a mechanic ("
                + SkillCatalog.styles(power.get().getPath()).stream().map(SkillCatalog.Node::mechanic).collect(Collectors.joining(", ")) + ")."
                : mechanic + " is not one of " + name + "'s power's Ultimates.";
        if (Ultimates.running(p)) return name + " already has " + Ultimates.run(p).mechanic + " playing; stop it first.";
        AbilityUseContext c = new AbilityUseContext(p, power.get(), PlayerProgressionService.getLevel(p), false);
        if (!Ultimates.canUse(c, node.mechanic()))
            return node.mechanic() + " can't start: " + (Ultimates.of(node.mechanic()).kind() == Ultimates.Kind.OFFENSE
                    ? "no foe in front of " + name + " in sight, or they can't act now." : name + " can't act now.");
        return Ultimates.cast(c, node.mechanic()) ? "Cast " + node.mechanic() + " for " + name + "." : node.mechanic() + " refused to start.";
    }

    public static String stop(ServerPlayer p) {
        if (!Ultimates.running(p)) return p.getGameProfile().getName() + " has no Ultimate playing.";
        String mechanic = Ultimates.run(p).mechanic;
        Ultimates.stop(p);
        return "Stopped " + mechanic + " for " + p.getGameProfile().getName() + "; a held foe was let go.";
    }

    /** Ends every Ultimate playing on the server. Returns how many. */
    public static int stopAll() {
        int n = 0;
        for (Ultimates.Run run : Ultimates.live()) { Ultimates.stop(run.player); n++; }
        return n;
    }

    public static String reset(ServerPlayer p) {
        var node = chosen(p);
        if (node.isEmpty()) return p.getGameProfile().getName() + " has no class.";
        CooldownService.reset(p, actionId(node.get()));
        return node.get().mechanic() + " is ready again for " + p.getGameProfile().getName() + ".";
    }
}
