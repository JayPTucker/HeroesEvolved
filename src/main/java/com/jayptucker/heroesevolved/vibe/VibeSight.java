package com.jayptucker.heroesevolved.vibe;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.Targeting;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
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
                         Component attackerName, Component victimName, long tick,
                         EntityType<?> attackerType, EntityType<?> victimType) {}
    private record Mark(ServerLevel world, long until) {}
    private static final Deque<Trace> TRACES = new ArrayDeque<>();
    private static final Map<UUID, Map<UUID, Mark>> MARKS = new HashMap<>();
    /** How many blows each viber's last read replayed (tests). */
    private static final Map<UUID, Integer> REPLAYED = new HashMap<>();

    private VibeSight() {}

    public static void record(LivingEntity victim, LivingEntity attacker) {
        if (!(victim.level() instanceof ServerLevel world) || attacker == victim || attacker.level() != world) return;
        long now = world.getGameTime();
        if (TRACES.size() >= MAX_TRACES) TRACES.removeFirst();
        TRACES.addLast(new Trace(world, attacker.getUUID(), victim.getUUID(), attacker.getBoundingBox().getCenter(),
                victim.getBoundingBox().getCenter(), attacker.getDisplayName().copy(), victim.getDisplayName().copy(), now,
                attacker.getType(), victim.getType()));
    }

    public static boolean read(ServerPlayer player, boolean share) {
        if (!VibeService.ready(player)) return false;
        // A hand on another player's bed, chest or pet: a vision of them instead of the scene.
        if (!share && VibeVisions.touch(player)) return true;
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
        REPLAYED.put(player.getUUID(), scene.size());
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
            if (scene.isEmpty()) viewer.displayClientMessage(Component.translatable("vibe.heroesevolved.quiet"), true);
            for (int i = 0; i < scene.size(); i++) {
                Trace trace = scene.get(scene.size() - 1 - i);
                echo(viewer, trace, true, 5 + i * 9);
                echo(viewer, trace, false, 5 + i * 9);
            }
            if (!scene.isEmpty()) {
                Trace last = scene.getFirst();
                viewer.displayClientMessage(Component.translatable("vibe.heroesevolved.echo",
                        last.attackerName(), last.victimName(), Math.max(0, (player.serverLevel().getGameTime() - last.tick()) / 20)), true);
            }
            // No outlines: a white silhouette seen through bodies and walls read as a stick figure. The ghosts of the
            // blows say who did what; a ripple over each one now marked says who to hit.
            if (witness instanceof Mob mob && mob.getTarget() != null && mob.getTarget().distanceToSqr(player) <= 576)
                ripple(viewer, mob.getTarget());
        }
        if (witness != null && VibeService.hostile(player, witness)) {
            mark(player, witness);
            for (ServerPlayer viewer : viewers) ripple(viewer, witness);
        }
        for (Trace trace : scene) {
            if (trace.world().getEntity(trace.attacker()) instanceof LivingEntity attacker && VibeService.hostile(player, attacker)) {
                mark(player, attacker);
                for (ServerPlayer viewer : viewers) ripple(viewer, attacker);
            }
        }
        return true;
    }

    /**
     * One body of a replayed blow, privately to {@code viewer}: a ghost of the attacker (scale 1) where it stood, turned to
     * its victim, or of the victim (scale 0.6) where it was hit. The seed is the creature's type, so the client draws its
     * real model even if it has since died; the entity is the living one if it is still about (for a player's skin).
     */
    private static void echo(ServerPlayer viewer, Trace trace, boolean attacker, int delay) {
        Vec3 at = attacker ? trace.from() : trace.to(), toward = attacker ? trace.to() : trace.from();
        EntityType<?> type = attacker ? trace.attackerType() : trace.victimType();
        Entity live = trace.world().getEntity(attacker ? trace.attacker() : trace.victim());
        PacketDistributor.sendToPlayer(viewer, new SetPiecePayload(Piece.VIBE_ECHO.ordinal(), at, toward, attacker ? 1 : .6F,
                live == null ? -1 : live.getId(), BuiltInRegistries.ENTITY_TYPE.getId(type), delay));
    }

    /** A blue ripple over a body, privately: the viewer now knows it (a marked foe, or what the witness was after). */
    private static void ripple(ServerPlayer viewer, LivingEntity target) {
        Vec3 center = target.getBoundingBox().getCenter();
        VibeGeometry.privatePiece(viewer, Piece.VIBE_HIT, center, center.add(0, 1, 0), Math.max(.8F, target.getBbHeight() * .55F),
                target.getId(), 10);
    }

    /** Package-visible for tests: how many blows this viber's last read replayed. */
    static int replayed(ServerPlayer player) { return REPLAYED.getOrDefault(player.getUUID(), -1); }

    /** Package-visible for tests: how many blows are remembered near {@code at} in this world. */
    static int remembered(ServerLevel world, Vec3 at, double radius) {
        int n = 0;
        for (Trace trace : TRACES)
            if (trace.world() == world && trace.to().distanceToSqr(at) <= radius * radius) n++;
        return n;
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

    static void forget(ServerPlayer player) {
        MARKS.remove(player.getUUID());
        REPLAYED.remove(player.getUUID());
    }

    static void clear() {
        TRACES.clear();
        MARKS.clear();
        REPLAYED.clear();
    }
}
