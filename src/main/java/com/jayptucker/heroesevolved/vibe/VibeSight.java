package com.jayptucker.heroesevolved.vibe;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.effects.PrivateGlow;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Records short combat traces and replays them privately when a viber touches a witness or the scene. */
public final class VibeSight {
    private static final int MEMORY = 20 * 25, MAX_TRACES = 384;
    private static final int MARK_TICKS = 120;
    static final int RESIDUE_TICKS = 120;
    private record Trace(ServerLevel world, UUID attacker, UUID victim, Vec3 from, Vec3 to,
                         Component attackerName, Component victimName, long tick) {}
    private record Mark(ServerLevel world, long until) {}
    private static final Deque<Trace> TRACES = new ArrayDeque<>();
    private static final Map<UUID, Map<UUID, Mark>> MARKS = new HashMap<>();

    private VibeSight() {}

    public static void record(LivingEntity victim, LivingEntity attacker) {
        if (!(victim.level() instanceof ServerLevel world) || attacker == victim || attacker.level() != world) return;
        long now = world.getGameTime();
        if (TRACES.size() >= MAX_TRACES) TRACES.removeFirst();
        TRACES.addLast(new Trace(world, attacker.getUUID(), victim.getUUID(), attacker.getBoundingBox().getCenter(),
                victim.getBoundingBox().getCenter(), attacker.getDisplayName().copy(), victim.getDisplayName().copy(), now));
    }

    public static boolean read(ServerPlayer player, boolean share) {
        if (!VibeService.ready(player)) return false;
        LivingEntity witness = Targeting.livingEntityInSight(player, share ? 8 : 5.5, entity -> true);
        Vec3 hand = VibeGeometry.hand(player, player.getLookAngle(), 1, false);
        Vec3 touch = witness == null ? VibeGeometry.clip(player, player.getEyePosition(),
                player.getEyePosition().add(player.getLookAngle().scale(4.5))) : witness.getBoundingBox().getCenter();
        if (witness == null && touch.distanceToSqr(player.getEyePosition()) >= 4.5 * 4.5 - .001)
            touch = StyleOps.ground(player.serverLevel(), touch);
        List<Trace> scene = new ArrayList<>();
        double radius = 5;
        for (Trace trace : TRACES.reversed()) {
            if (trace.world() != player.serverLevel() || player.serverLevel().getGameTime() - trace.tick() > MEMORY) continue;
            if (witness == null ? trace.to().distanceToSqr(touch) <= radius * radius
                    : trace.attacker().equals(witness.getUUID()) || trace.victim().equals(witness.getUUID())) {
                if (trace.from().distanceToSqr(player.position()) > 48 * 48
                        || trace.to().distanceToSqr(player.position()) > 48 * 48) continue;
                scene.add(trace);
                if (scene.size() == (SkillTreeService.has(player, "vibe_deep_read") ? 6 : 3)) break;
            }
        }
        // A shapeshifter's vibration is their own, whatever shape they wear: reading one sees through it.
        if (witness != null) com.jayptucker.heroesevolved.shapeshift.ShapeshiftSuspicion.read(player, witness);
        ArmPosePayload.show(player, ArmPose.VIBE_READ, 24);
        SetPiecePayload.send(player.serverLevel(), Piece.VIBE_TOUCH, hand, touch, 1, player);
        StyleOps.sound(player.serverLevel(), touch, ModSounds.VIBE_ECHO.get(), .7F, .75F);
        List<ServerPlayer> viewers = share
                ? StyleOps.friends(player, player.position(), 8).stream()
                        .filter(ServerPlayer.class::isInstance).map(ServerPlayer.class::cast).toList()
                : List.of(player);
        for (ServerPlayer viewer : viewers) {
            ScreenTintPayload.send(viewer, 0x90223D83, 34, ScreenTintPayload.Style.VIGNETTE);
            if (scene.isEmpty()) {
                VibeGeometry.privatePiece(viewer, Piece.VIBE_ECHO, touch, touch.add(0, .6, 0), .7F, -1, 6);
                viewer.displayClientMessage(Component.translatable("vibe.heroesevolved.quiet"), true);
            }
            for (int i = 0; i < scene.size(); i++) {
                Trace trace = scene.get(scene.size() - 1 - i);
                VibeGeometry.privatePiece(viewer, Piece.VIBE_ECHO, trace.from(), trace.to(), 1, -1, 5 + i * 5);
            }
            if (!scene.isEmpty()) {
                Trace last = scene.getFirst();
                viewer.displayClientMessage(Component.translatable("vibe.heroesevolved.echo",
                        last.attackerName(), last.victimName(), Math.max(0, (player.serverLevel().getGameTime() - last.tick()) / 20)), true);
            }
            if (witness instanceof Mob mob && mob.getTarget() != null && mob.getTarget().distanceToSqr(player) <= 576)
                PrivateGlow.glow(viewer, mob.getTarget(), 70);
            if (witness != null) PrivateGlow.glow(viewer, witness, 90);
        }
        if (witness != null && VibeService.hostile(player, witness)) mark(player, witness);
        for (Trace trace : scene) {
            if (trace.world().getEntity(trace.attacker()) instanceof LivingEntity attacker && VibeService.hostile(player, attacker)) {
                mark(player, attacker);
                for (ServerPlayer viewer : viewers) PrivateGlow.glow(viewer, attacker, 70);
            }
        }
        return true;
    }

    public static boolean marked(ServerPlayer player, LivingEntity target) {
        var marks = MARKS.get(player.getUUID());
        var mark = marks == null ? null : marks.get(target.getUUID());
        return mark != null && mark.world() == target.level() && mark.until() > target.level().getGameTime();
    }

    static void mark(ServerPlayer player, LivingEntity target) {
        mark(player, target, SkillTreeService.has(player, "vibe_deep_read") ? 220 : MARK_TICKS);
    }

    /** Marks never shorten: a longer mark already on the target stays. */
    static void mark(ServerPlayer player, LivingEntity target, int duration) {
        Map<UUID, Mark> marks = MARKS.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
        if (marks.size() >= 32 && !marks.containsKey(target.getUUID())) return;
        long until = player.serverLevel().getGameTime() + duration;
        Mark old = marks.get(target.getUUID());
        if (old != null && old.world() == player.serverLevel() && old.until() >= until) return;
        marks.put(target.getUUID(), new Mark(player.serverLevel(), until));
    }

    /** Residual Sense: whoever hurts a viber is marked for a short while, as if they had been read. */
    public static void wounded(LivingEntity victim, LivingEntity attacker) {
        if (!(victim instanceof ServerPlayer viber) || !SkillTreeService.has(viber, "vibe_2_0")
                || !VibeService.active(viber) || !VibeService.hostile(viber, attacker)) return;
        mark(viber, attacker, RESIDUE_TICKS);
    }

    static void tick(MinecraftServer server) {
        TRACES.removeIf(trace -> trace.world().getGameTime() - trace.tick() > MEMORY);
        MARKS.values().removeIf(marks -> {
            marks.values().removeIf(mark -> mark.world().getServer() != server || mark.world().getGameTime() >= mark.until());
            return marks.isEmpty();
        });
    }

    static void forget(ServerPlayer player) { MARKS.remove(player.getUUID()); }
    static void clear() { TRACES.clear(); MARKS.clear(); }
}
