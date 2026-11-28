package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.Ability;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseResult;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.skills.Loadout;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/**
 * The server half of the multiplayer check ({@code tools/multiplayer-check.init.gradle}, flag
 * {@code heroes.check.server}). On a real dedicated server, once a real client joins, it hands that player every power in
 * turn and fires each of the power's actions at a few still foes, so every packet those moves send really crosses the
 * network: single-player and the game tests hand packets over as objects and never encode them, so a broken codec only
 * shows up on a server. It then kicks the player with {@link #DONE}, writes {@link #REPORT} and stops the server.
 * The client half is {@link MultiplayerCheckClient}.
 */
@EventBusSubscriber(modid = "heroesevolved")
public final class MultiplayerCheckServer {
    /** The kick message that tells the client the check finished, followed by {@code failures=N}. */
    public static final String DONE = "HEROES_CHECK_DONE";
    /** The report in the server's directory: one line per step, and a line with FAIL in it per failure. */
    public static final String REPORT = "multiplayer-check-server.txt";
    private static final int JOIN_TIMEOUT = 20 * 60 * 5;
    private static final List<String> LINES = Collections.synchronizedList(new ArrayList<>());
    private static final TreeMap<Integer, List<Runnable>> PLAN = new TreeMap<>();
    private static final List<Mob> FOES = new ArrayList<>();
    private static ServerPlayer player;
    private static int clock, idle, failures, fired, haltIn = -1;
    private static boolean finished;

    private MultiplayerCheckServer() {
    }

    private static boolean on() {
        return Boolean.getBoolean("heroes.check.server");
    }

    @SubscribeEvent
    public static void join(PlayerEvent.PlayerLoggedInEvent event) {
        if (!on() || player != null || !(event.getEntity() instanceof ServerPlayer joined)) return;
        player = joined;
        line("MULTIPLAYER_CHECK " + joined.getGameProfile().getName() + " joined from "
                + joined.connection.getConnection().getRemoteAddress());
        plan(joined);
    }

    @SubscribeEvent
    public static void leave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!on() || finished || event.getEntity() != player) return;
        fail("the client left before the check finished (its log has the reason)");
        finish(event.getEntity().getServer());
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (!on()) return;
        MinecraftServer server = event.getServer();
        if (haltIn >= 0) {
            if (haltIn-- == 0) server.halt(false);
            return;
        }
        if (finished) return;
        if (player == null) {
            if (++idle > JOIN_TIMEOUT) {
                fail("no client joined within five minutes");
                finish(server);
            }
            return;
        }
        List<Runnable> steps = PLAN.remove(clock++);
        if (steps != null) steps.forEach(Runnable::run);
        if (PLAN.isEmpty()) finish(server);
    }

    private static void plan(ServerPlayer p) {
        String name = p.getGameProfile().getName();
        int t = 40;
        at(t, "stage the player", () -> stage(p));
        t += 40;
        List<ResourceLocation> powers = new ArrayList<>(AbilityRegistry.ABILITIES.keySet());
        powers.sort(Comparator.comparing(ResourceLocation::toString));
        for (ResourceLocation power : powers) {
            Ability ability = AbilityRegistry.ABILITIES.get(power);
            at(t, "grant " + power, () -> {
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(power));
                // The command takes the power's path as one word; a namespaced id fails to parse.
                command(p, "heroesevolved power replace " + name + " " + power.getPath());
                command(p, "heroesevolved power level " + name + " 20");
                command(p, "heroesevolved stamina unlimited " + name + " true");
            });
            t += 30;
            List<AbilitySlot> slots = new ArrayList<>(ability.actions().keySet());
            Collections.sort(slots);
            for (AbilitySlot slot : slots) {
                String key = Loadout.base(slot);
                String what = power.getPath() + " " + slot;
                at(t, what, () -> {
                    foes(p);
                    CooldownService.clearAll(p);
                    AbilityUseResult result = AbilityUseService.activateAction(p, key, false);
                    fired++;
                    line("MULTIPLAYER_CHECK " + what + " -> " + result);
                    if (result == AbilityUseResult.NO_ASSIGNED_POWER) fail(power + " was never granted, so " + what + " did not run");
                });
                at(t + 25, what + " release", () -> AbilityUseService.releaseAction(p, key));
                t += 45;
            }
            t += 40;
        }
        at(t, "eclipse start", () -> command(p, "heroesevolved eclipse start"));
        t += 100;
        at(t, "eclipse stop", () -> command(p, "heroesevolved eclipse stop"));
        at(t + 40, "settle", () -> line("MULTIPLAYER_CHECK " + fired + " actions fired"));
    }

    private static void stage(ServerPlayer p) {
        PrologueService.skip(p);
        CryoChamberService.release(p);
        p.setGameMode(GameType.SURVIVAL);
        p.getAbilities().invulnerable = true;
        p.onUpdateAbilities();
        var level = p.serverLevel();
        level.setDayTime(6000);
        int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 0);
        p.teleportTo(level, 0.5, ground, 0.5, 0, 0);
        foes(p);
    }

    /** Keeps three still zombies a few blocks in front of the player, and turns the player to face them. */
    private static void foes(ServerPlayer p) {
        FOES.removeIf(foe -> !foe.isAlive() || foe.isRemoved());
        var level = p.serverLevel();
        for (int i = FOES.size(); i < 3; i++) {
            Mob foe = EntityType.ZOMBIE.create(level);
            if (foe == null) continue;
            foe.setNoAi(true);
            foe.setPersistenceRequired();
            Vec3 at = p.position().add(-2 + 2 * i, 0, 6);
            foe.moveTo(at.x, at.y, at.z, 180, 0);
            level.addFreshEntity(foe);
            FOES.add(foe);
        }
        if (!FOES.isEmpty()) p.lookAt(EntityAnchorArgument.Anchor.EYES, FOES.get(FOES.size() / 2).getEyePosition());
    }

    private static void command(ServerPlayer p, String command) {
        var server = p.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    private static void at(int tick, String what, Runnable step) {
        PLAN.computeIfAbsent(tick, ignored -> new ArrayList<>()).add(() -> {
            try {
                step.run();
            } catch (Throwable error) {
                fail(what + " threw " + error);
                error.printStackTrace();
            }
        });
    }

    private static void line(String text) {
        System.out.println(text);
        LINES.add(text);
    }

    private static void fail(String why) {
        failures++;
        line("MULTIPLAYER_CHECK FAIL " + why);
    }

    private static void finish(MinecraftServer server) {
        finished = true;
        PLAN.clear();
        line("MULTIPLAYER_CHECK server finished with " + failures + " failure(s)");
        try {
            Files.write(server.getServerDirectory().resolve(REPORT), List.copyOf(LINES));
        } catch (IOException e) {
            System.out.println("MULTIPLAYER_CHECK could not write its report: " + e);
        }
        if (player != null && !player.hasDisconnected())
            player.connection.disconnect(Component.literal(DONE + " failures=" + failures));
        haltIn = 40;
    }
}
