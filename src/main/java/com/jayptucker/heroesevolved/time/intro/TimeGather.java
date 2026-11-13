package com.jayptucker.heroesevolved.time.intro;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.story.AvenueService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The first Space-Time awakening gathers everyone. Before the vision, the new traveller folds space to each player
 * online in turn, nearest first, takes them along, and folds on to the next with everyone already gathered; then the
 * whole group goes to Odessa together. Each player's return ticket is where they stood when they were taken, so the
 * vision still sends everyone home to their own place.
 */
public final class TimeGather {
    /** Ticks between folds: long enough to see who was taken and hear the blink, short enough to keep moving. */
    static final int HOP_TICKS = 30;

    private static final class Run {
        final UUID owner;
        final ArrayDeque<UUID> ahead;
        final List<UUID> group = new ArrayList<>();
        final Map<UUID, TimeIntroVisit> origins = new LinkedHashMap<>();
        int wait = 6;

        Run(UUID owner, List<UUID> ahead) {
            this.owner = owner;
            this.ahead = new ArrayDeque<>(ahead);
        }
    }

    private static final Map<UUID, Run> RUNS = new HashMap<>();

    private TimeGather() {}

    /**
     * Everyone the traveller can fetch: real players online in the vanilla dimensions (story dimensions and arenas keep
     * their own players), alive, not spectating and not already on their way somewhere, nearest first by a chain from
     * the traveller. Test players never count, so game tests running side by side don't fetch each other.
     */
    static List<ServerPlayer> targets(ServerPlayer owner, List<UUID> audience) {
        List<ServerPlayer> left = new ArrayList<>(owner.getServer().getPlayerList().getPlayers().stream()
                .filter(other -> other != owner && !(other instanceof FakePlayer) && !audience.contains(other.getUUID()))
                .filter(TimeGather::fetchable).toList());
        List<ServerPlayer> ordered = new ArrayList<>();
        ServerPlayer from = owner;
        while (!left.isEmpty()) {
            ServerPlayer at = from;
            ServerPlayer next = left.stream().min(Comparator.comparingDouble(other -> distance(at, other))).orElseThrow();
            ordered.add(next);
            left.remove(next);
            from = next;
        }
        return ordered;
    }

    private static boolean fetchable(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && "minecraft".equals(player.level().dimension().location().getNamespace())
                && !TimeIntroService.isVisiting(player) && !TimeIntroService.isCity(player.level()) && !AvenueService.isRunner(player)
                && !gathering(player);
    }

    /** Players in another dimension count as far away, so the chain finishes one world before moving to the next. */
    private static double distance(ServerPlayer a, ServerPlayer b) {
        return a.level() == b.level() ? a.distanceToSqr(b) : 1e12 + a.distanceToSqr(b.getX(), a.getY(), b.getZ());
    }

    /**
     * Starts fetching {@code targets} for {@code owner}, whose {@code audience} (the owner and anyone standing with them)
     * travels with them from the start. When the last one is taken, the group is queued for the vision.
     */
    static void begin(ServerPlayer owner, List<ServerPlayer> targets, List<UUID> audience) {
        Run run = new Run(owner.getUUID(), targets.stream().map(ServerPlayer::getUUID).toList());
        for (UUID id : audience) {
            ServerPlayer member = owner.getServer().getPlayerList().getPlayer(id);
            if (member == null && !id.equals(owner.getUUID())) continue;
            ServerPlayer who = member == null ? owner : member;
            run.group.add(id);
            run.origins.put(id, TimeIntroVisit.capture(who));
        }
        if (!run.group.contains(owner.getUUID())) {
            run.group.addFirst(owner.getUUID());
            run.origins.put(owner.getUUID(), TimeIntroVisit.capture(owner));
        }
        RUNS.put(owner.getUUID(), run);
        owner.displayClientMessage(Component.literal("Time folds open. You can reach anyone now. Bring them with you.")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** True while {@code player} is fetching others, or is being carried along by someone who is. */
    public static boolean gathering(ServerPlayer player) {
        for (Run run : RUNS.values()) if (run.owner.equals(player.getUUID()) || run.group.contains(player.getUUID())) return true;
        return false;
    }

    /** True while {@code player} is the one fetching. */
    static boolean fetching(ServerPlayer player) { return RUNS.containsKey(player.getUUID()); }

    static void tick(MinecraftServer server) {
        var iterator = RUNS.values().iterator();
        while (iterator.hasNext()) {
            Run run = iterator.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(run.owner);
            if (owner == null || !owner.isAlive()) {
                // The traveller is gone: everyone they took goes back where they were.
                sendHome(server, run);
                iterator.remove();
                continue;
            }
            if (--run.wait > 0) continue;
            ServerPlayer next = null;
            while (next == null && !run.ahead.isEmpty()) {
                ServerPlayer candidate = server.getPlayerList().getPlayer(run.ahead.poll());
                if (candidate != null && fetchable(candidate)) next = candidate;
            }
            if (next == null) {
                iterator.remove();
                TimeIntroService.enqueue(owner, run.group, run.origins);
                continue;
            }
            fold(server, run, owner, next);
            run.wait = HOP_TICKS;
        }
    }

    /** Folds the traveller and everyone with them to {@code target}'s side, and takes {@code target} along. */
    private static void fold(MinecraftServer server, Run run, ServerPlayer owner, ServerPlayer target) {
        ServerLevel there = target.serverLevel();
        Vec3 center = target.position();
        List<ServerPlayer> travellers = new ArrayList<>();
        for (UUID id : run.group) {
            ServerPlayer member = server.getPlayerList().getPlayer(id);
            if (member != null && member.isAlive()) travellers.add(member);
        }
        // The traveller lands just in front of them, face to face; the others fan out round both.
        Vec3 facing = Vec3.directionFromRotation(0, target.getYRot()).multiply(1, 0, 1);
        if (facing.lengthSqr() < 1e-4) facing = new Vec3(0, 0, 1);
        facing = facing.normalize();
        for (int i = 0; i < travellers.size(); i++) {
            ServerPlayer member = travellers.get(i);
            Vec3 spot = i == 0 ? center.add(facing.scale(1.6))
                    : center.add(facing.yRot((float) (Math.PI * 2 * i / (travellers.size() + 1))).scale(2.4));
            if (!there.noCollision(member, member.getDimensions(member.getPose()).makeBoundingBox(spot))) spot = center;
            Vec3 look = center.subtract(spot);
            float yaw = look.lengthSqr() < 1e-4 ? member.getYRot() : (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG);
            Vec3 from = member.position();
            ServerLevel left = member.serverLevel();
            left.playSound(null, from.x, from.y, from.z, ModSounds.TIME_BLINK.get(), SoundSource.PLAYERS, 0.9F, 1.1F);
            AbilityEffects.send(left, AbilityEffectPayload.Kind.TIME_FOLD, from.add(0, 1, 0), left == there ? spot.add(0, 1, 0) : from.add(0, 2, 0), 1);
            member.stopRiding();
            member.ejectPassengers();
            member.teleportTo(there, spot.x, spot.y, spot.z, yaw, 0);
            member.setDeltaMovement(Vec3.ZERO);
            member.fallDistance = 0;
            ScreenTintPayload.send(member, 0x707E58B8, 14, ScreenTintPayload.Style.FLASH);
        }
        ArmPosePayload.show(owner, ArmPose.ONE_HAND, 24);
        there.playSound(null, center.x, center.y, center.z, ModSounds.TIME_BLINK.get(), SoundSource.PLAYERS, 1.1F, 0.8F);
        there.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 0.7F);
        AbilityEffects.at(target, AbilityEffectPayload.Kind.TEMPORAL_BURST, 2);
        ScreenTintPayload.send(target, 0x907E58B8, 20, ScreenTintPayload.Style.FLASH);
        target.displayClientMessage(Component.literal(owner.getName().getString() + " steps out of nowhere and takes your wrist.")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        run.origins.put(target.getUUID(), TimeIntroVisit.capture(target));
        run.group.add(target.getUUID());
    }

    /** Puts everyone a cancelled gather took back where it found them. */
    private static void sendHome(MinecraftServer server, Run run) {
        for (UUID id : run.group) {
            ServerPlayer member = server.getPlayerList().getPlayer(id);
            TimeIntroVisit origin = run.origins.get(id);
            if (member == null || origin == null || !member.isAlive()) continue;
            ServerLevel home = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    origin.dimension()));
            if (home == null) continue;
            member.teleportTo(home, origin.x(), origin.y(), origin.z(), origin.yaw(), origin.pitch());
            member.setDeltaMovement(Vec3.ZERO);
            member.fallDistance = 0;
        }
    }

    /** Ends every gather and puts everyone back where it found them (server stopping). */
    static void cancelAll(MinecraftServer server) {
        for (Run run : RUNS.values()) sendHome(server, run);
        RUNS.clear();
    }
}
