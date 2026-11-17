package com.jayptucker.heroesevolved.gifted;

import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.village.VillageIncidents;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Admin commands for gifted mobs and village events:
 * <ul>
 *   <li>{@code /heroesevolved gifted give <power> [rarity] [evil|good]}: the nearest villager or illager within 8 blocks gets a power;</li>
 *   <li>{@code /heroesevolved gifted summon <power> [rarity] [pillager|vindicator|illusioner|evoker]}: a gifted illager here (only villagers and illagers carry powers);</li>
 *   <li>{@code /heroesevolved gifted cleanse}: the nearest gifted mob loses its power;</li>
 *   <li>{@code /heroesevolved gifted cast <move>}: the nearest gifted mob winds up a move at you;</li>
 *   <li>{@code /heroesevolved village turn|guardian|wake}: the nearest grown villager's gift wakes evil, good, or by roll;</li>
 *   <li>{@code /heroesevolved village incident [awakening|renegade|guardian|meltdown]}: an incident in a village up to
 *   {@code villageIncidentRange} blocks away, which calls for help;</li>
 *   <li>{@code /heroesevolved village calm}: ends every renegade's siege.</li>
 * </ul>
 */
public final class GiftedCommands {
    private GiftedCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> gifted() {
        return Commands.literal("gifted")
                .then(Commands.literal("give")
                        .then(power()
                                .executes(c -> give(c, Rarity.RARE, true))
                                .then(rarity()
                                        .executes(c -> give(c, rarity(c), true))
                                        .then(Commands.literal("evil").executes(c -> give(c, rarity(c), true)))
                                        .then(Commands.literal("good").executes(c -> give(c, rarity(c), false))))))
                .then(Commands.literal("summon")
                        .then(power()
                                .executes(c -> summon(c, Rarity.EPIC, "pillager"))
                                .then(rarity()
                                        .executes(c -> summon(c, rarity(c), "pillager"))
                                        .then(Commands.argument("mob", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"pillager", "vindicator",
                                                        "illusioner", "evoker"}, b))
                                                .executes(c -> summon(c, rarity(c), StringArgumentType.getString(c, "mob")))))))
                .then(Commands.literal("cleanse").executes(GiftedCommands::cleanse))
                .then(Commands.literal("cast")
                        .then(Commands.argument("move", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(MobMoves.ALL.stream().map(MobMoves.Move::id), b))
                                .executes(GiftedCommands::cast)));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> village() {
        return Commands.literal("village")
                .then(Commands.literal("turn").executes(c -> awaken(c, Boolean.TRUE)))
                .then(Commands.literal("guardian").executes(c -> awaken(c, Boolean.FALSE)))
                .then(Commands.literal("wake").executes(c -> awaken(c, null)))
                .then(Commands.literal("calm").executes(c -> {
                    int n = Renegades.cancelAll();
                    c.getSource().sendSuccess(() -> Component.literal("Ended " + n + " siege(s)."), true);
                    return n;
                }))
                .then(Commands.literal("incident")
                        .executes(c -> incident(c, VillageIncidents.Kind.AWAKENING))
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(VillageIncidents.Kind.values())
                                        .map(k -> k.name().toLowerCase()), b))
                                .executes(c -> {
                                    VillageIncidents.Kind kind;
                                    try { kind = VillageIncidents.Kind.valueOf(StringArgumentType.getString(c, "kind").toUpperCase()); }
                                    catch (IllegalArgumentException e) { c.getSource().sendFailure(Component.literal("Unknown incident.")); return 0; }
                                    return incident(c, kind);
                                })));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> power() {
        return Commands.argument("power", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(GiftedMobs.powers().stream().map(ResourceLocation::getPath), b));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> rarity() {
        return Commands.argument("rarity", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Rarity.values()).map(r -> r.name().toLowerCase()), b));
    }

    private static Rarity rarity(CommandContext<CommandSourceStack> c) {
        Rarity r = Rarity.named(StringArgumentType.getString(c, "rarity"));
        return r != null ? r : Rarity.RARE;
    }

    private static ResourceLocation power(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "power");
        return GiftedMobs.powers().stream().filter(p -> p.getPath().equals(name) || p.toString().equals(name)).findFirst().orElse(null);
    }

    private static <T extends LivingEntity> T nearest(CommandSourceStack source, Class<T> type, double radius, java.util.function.Predicate<T> filter) {
        Vec3 at = source.getPosition();
        return source.getLevel().getEntitiesOfClass(type, new AABB(at, at).inflate(radius), t -> t.isAlive() && filter.test(t)
                        && t != source.getEntity())
                .stream().min(Comparator.comparingDouble(t -> t.distanceToSqr(at))).orElse(null);
    }

    private static int give(CommandContext<CommandSourceStack> c, Rarity rarity, boolean evil) {
        ResourceLocation power = power(c);
        if (power == null) { c.getSource().sendFailure(Component.literal("Unknown power.")); return 0; }
        Mob mob = nearest(c.getSource(), Mob.class, 8, GiftedMobs::mayCarry);
        if (mob == null) { c.getSource().sendFailure(Component.literal("No villager or illager within 8 blocks: only they can carry a power.")); return 0; }
        GiftedMob g = GiftedMobs.bestow(mob, power, rarity, evil, Gift.Origin.ADMIN, null, null);
        GiftedMobs.announce(mob);
        c.getSource().sendSuccess(() -> Component.literal("Gave " + g.gift().name() + " (" + rarity.title + " " + g.gift().powerName() + ")."), true);
        return 1;
    }

    private static int summon(CommandContext<CommandSourceStack> c, Rarity rarity, String type) {
        ResourceLocation power = power(c);
        if (power == null) { c.getSource().sendFailure(Component.literal("Unknown power.")); return 0; }
        var level = c.getSource().getLevel();
        var entityType = EntityType.byString("minecraft:" + type).orElse(null);
        if (entityType == null || !(entityType.create(level) instanceof Mob mob) || !GiftedMobs.mayCarry(mob)) {
            c.getSource().sendFailure(Component.literal("Only villagers and illagers carry powers: " + type));
            return 0;
        }
        Vec3 at = c.getSource().getPosition();
        mob.moveTo(at.x, at.y, at.z, c.getSource().getRotation().y, 0);
        mob.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.COMMAND, null);
        level.addFreshEntity(mob);
        GiftedMob g = GiftedMobs.bestow(mob, power, rarity, true, Gift.Origin.ADMIN, null, null);
        GiftedMobs.announce(mob);
        c.getSource().sendSuccess(() -> Component.literal("Summoned " + g.gift().name() + "."), true);
        return 1;
    }

    private static int cleanse(CommandContext<CommandSourceStack> c) {
        Mob mob = nearest(c.getSource(), Mob.class, 12, GiftedMobs::isGifted);
        if (mob == null) { c.getSource().sendFailure(Component.literal("No gifted mob within 12 blocks.")); return 0; }
        GiftedMobs.cleanse(mob);
        c.getSource().sendSuccess(() -> Component.literal("Cleansed."), true);
        return 1;
    }

    private static int cast(CommandContext<CommandSourceStack> c) {
        MobMoves.Move move = MobMoves.byId(StringArgumentType.getString(c, "move"));
        if (move == null) { c.getSource().sendFailure(Component.literal("Unknown move.")); return 0; }
        Mob mob = nearest(c.getSource(), Mob.class, 24, GiftedMobs::isGifted);
        if (mob == null || !(c.getSource().getEntity() instanceof LivingEntity target)) {
            c.getSource().sendFailure(Component.literal("No gifted mob within 24 blocks."));
            return 0;
        }
        GiftedMobs.of(mob).start(move, target);
        return 1;
    }

    private static int awaken(CommandContext<CommandSourceStack> c, Boolean evil) {
        Villager v = nearest(c.getSource(), Villager.class, 64, x -> !x.isBaby() && !GiftedMobs.isGifted(x));
        if (v == null) { c.getSource().sendFailure(Component.literal("No grown, ungifted villager within 64 blocks.")); return 0; }
        Mob became = evil == null ? Renegades.awaken(v) : Renegades.awaken(v, evil);
        if (became == null) { c.getSource().sendFailure(Component.literal("Their gift couldn't wake.")); return 0; }
        c.getSource().sendSuccess(() -> Component.literal(Renegades.turning(v) ? "They are turning." : "Their gift woke."), true);
        return 1;
    }

    private static int incident(CommandContext<CommandSourceStack> c, VillageIncidents.Kind kind) {
        var source = c.getSource();
        var village = VillageIncidents.find(source.getLevel(), net.minecraft.core.BlockPos.containing(source.getPosition()),
                HeroesEvolvedConfig.COMMON.villageIncidentRange.get(), source.getLevel().random);
        if (village == null) { source.sendFailure(Component.literal("No village within range.")); return 0; }
        VillageIncidents.requestAt(source.getLevel(), village, kind);
        int distance = (int) Math.round(Math.sqrt(village.atY(0).distSqr(net.minecraft.core.BlockPos.containing(source.getPosition()).atY(0))));
        source.sendSuccess(() -> Component.literal("Incident (" + kind.name().toLowerCase() + ") at the village near " + village.getX() + ", "
                + village.getZ() + " (" + distance + " blocks); it starts once the village has loaded."), true);
        return 1;
    }
}
