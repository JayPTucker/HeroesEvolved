package com.jayptucker.heroesevolved.time.intro;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.network.TimeIntroPayload;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.story.AvenueService;
import com.jayptucker.heroesevolved.time.TimeSlowService;
import com.jayptucker.heroesevolved.time.TimeStopService;
import com.jayptucker.heroesevolved.time.intro.city.CityGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Time Manipulation awakening: a vision of Odessa in the last half-minute before 6:14. Visitors
 * arrive on the avenue and can walk the southern half of the city while people go about their evening.
 * Ted Sprague's glow grows at the north end, the sirens start, and the light falls. Everyone watches
 * the shock wave come down the streets at them; a few blocks away, time stops for them, runs the whole
 * explosion backwards, and lets them go home. One shared scene at a time, with durable return tickets.
 */
public final class TimeIntroService {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "time_fracture"));
    private static final ResourceLocation POWER = ResourceLocation.fromNamespaceAndPath(
            HeroesEvolved.MOD_ID, "time_manipulation");
    public static final Vec3 ARRIVAL = new Vec3(4, 64, 70);
    public static final Vec3 BLAST = IntroBlast.CENTER;
    /** Sprague's light becomes something you notice; the sirens start; the light falls. */
    public static final int GLOW_TICK = 200, SIREN_TICK = 400, DETONATION_TICK = 640;
    /** Where visitors may walk: the southern half of the city, well inside its edges. */
    public static final int ROAM_X0 = -92, ROAM_X1 = 96, ROAM_Z0 = -4, ROAM_Z1 = 96;
    /** The last moment anyone can still be here: the farthest corner of the walk, frozen, rewound. */
    public static final int RETURN_TICK = DETONATION_TICK
            + IntroBlast.arrival(Math.max(IntroBlast.distance(ROAM_X0, ROAM_Z1), IntroBlast.distance(ROAM_X1, ROAM_Z1)))
            + IntroBlast.FREEZE_TICKS + IntroBlast.REWIND_TICKS + 10;
    public static final int CROWD_SIZE = 40;
    private static final String CROWD_TAG = "heroesevolved_time_intro_crowd";
    private static final ArrayDeque<Request> QUEUE = new ArrayDeque<>();
    private static Scene scene;
    private static boolean configured;

    private record Request(UUID owner, List<UUID> group) {}
    private static final class Scene {
        final UUID owner;
        final Set<UUID> visitors = new LinkedHashSet<>();
        /** When time stopped for each visitor, and where they were standing. */
        final Map<UUID, Integer> frozenAt = new HashMap<>();
        final Map<UUID, Vec3> frozenWhere = new HashMap<>();
        VisionCrowd crowd;
        int ticks;
        int nextCry = 24;
        Scene(UUID owner) { this.owner = owner; }
    }

    private TimeIntroService() {}

    public static boolean isCity(Level level) { return level.dimension().equals(DIMENSION); }
    public static boolean isVisiting(ServerPlayer player) {
        return player.getData(ModDataAttachments.TIME_INTRO_VISIT).pending();
    }

    public static boolean isCurrentCrowd(Entity entity) {
        // By tag: a villager is spawned before it joins the crowd's list.
        return scene != null && entity.getTags().contains(CROWD_TAG);
    }

    /** Waiting for the city after acquiring the power; the awakening shields them meanwhile. */
    public static boolean isQueued(ServerPlayer player) {
        return QUEUE.stream().anyMatch(request -> request.owner.equals(player.getUUID()));
    }

    /** A vision is playing in the city right now. */
    public static boolean sceneRunning() { return scene != null; }

    /** Ticks into the current vision, or -1. */
    public static int sceneTicks() { return scene == null ? -1 : scene.ticks; }

    /** Whether time has stopped for this visitor. */
    public static boolean isFrozen(ServerPlayer player) {
        return scene != null && scene.frozenAt.containsKey(player.getUUID());
    }

    /**
     * The vision is watched, not played: nobody uses a power in the city, except runners on the
     * avenue at 6:14, which is played with every power.
     */
    public static boolean blocksPowers(ServerPlayer player) {
        return isVisiting(player) || isCity(player.level()) && !AvenueService.isRunner(player);
    }

    /** Called only after an unlocked assignment has actually been saved. */
    public static void acquired(ServerPlayer player, ResourceLocation ability) {
        if (!POWER.equals(ability) || player.getData(ModDataAttachments.TIME_INTRO_SEEN)) return;
        if (scene != null && scene.visitors.contains(player.getUUID())) {
            player.setData(ModDataAttachments.TIME_INTRO_SEEN, true);
            return;
        }
        if (QUEUE.stream().anyMatch(request -> request.owner.equals(player.getUUID()))) return;
        // Capture the ten-block audience at acquisition, not after any city preparation/queue delay.
        List<UUID> group = player.serverLevel().players().stream()
                .filter(other -> other == player || other.distanceToSqr(player) <= 100)
                .map(ServerPlayer::getUUID).toList();
        if (!group.contains(player.getUUID())) { // Also supports server-side test players.
            group = new ArrayList<>(group);
            group.addFirst(player.getUUID());
        }
        QUEUE.add(new Request(player.getUUID(), List.copyOf(group)));
    }

    public static void login(ServerPlayer player) {
        if (player.getData(ModDataAttachments.PLAYER_SEEN_FUTURE_CITY))
            com.jayptucker.heroesevolved.time.TemporalStoryService.reach(player, com.jayptucker.heroesevolved.time.TemporalStoryService.VISION);
        // An avenue runner goes home by their own ticket, before the vision's fallback below.
        AvenueService.recover(player);
        if (isVisiting(player) || isCity(player.level()) && !AvenueService.isRunner(player)) {
            returnHome(player, false);
        }
        PlayerAbilityService.getData(player).ability(POWER)
                .filter(progress -> progress.isUnlocked())
                .ifPresent(progress -> acquired(player, POWER));
    }

    public static void tick(MinecraftServer server) {
        ServerLevel city = server.getLevel(DIMENSION);
        if (city == null) return;
        if (!configured) {
            // Secondary dimensions share game rules with the overworld; never change them here.
            city.setWeatherParameters(6000, 0, false, false);
            forceCity(city, false); // Also releases tickets left by an interrupted server.
            configured = true;
        }
        FractureCity cityData = FractureCity.get(city);
        // Prepare while the world is running, so natural awakenings usually jump immediately.
        cityData.tickBuild(city);
        if (scene == null) {
            // A vision waits for an evening on the avenue to end; they share the city.
            if (cityData.ready() && !AvenueService.running()) startNext(server, city);
            return;
        }
        Scene current = scene;
        current.ticks++;
        int afterLight = current.ticks - DETONATION_TICK;
        current.visitors.removeIf(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) return true; // Its saved ticket remains for reconnect recovery.
            if (!isCity(player.level())) {
                returnHome(player, false);
                return true;
            }
            Integer frozen = current.frozenAt.get(id);
            if (frozen != null) {
                // Time has stopped: nobody moves but the clock running backwards.
                Vec3 where = current.frozenWhere.get(id);
                if (player.position().distanceToSqr(where) > 0.01) player.teleportTo(city, where.x, where.y, where.z, player.getYRot(), player.getXRot());
                player.setDeltaMovement(Vec3.ZERO);
                player.fallDistance = 0;
                if (current.ticks - frozen >= IntroBlast.FREEZE_TICKS + IntroBlast.REWIND_TICKS) {
                    returnHome(player, true);
                    return true;
                }
                return false;
            }
            keepInside(city, player);
            if (afterLight >= 0 && IntroBlast.front(afterLight) >= IntroBlast.distance(player.getX(), player.getZ()) - IntroBlast.MARGIN)
                freeze(current, player);
            return false;
        });
        if (current.visitors.isEmpty()) {
            finish(server, city, false);
            return;
        }
        animateCrowd(city, current);
        if (current.ticks == GLOW_TICK) forEachVisitor(server, player -> cue(player, TimeIntroPayload.GLOW));
        if (current.ticks == SIREN_TICK) forEachVisitor(server, player -> cue(player, TimeIntroPayload.SIREN));
        if (current.ticks == DETONATION_TICK - 60) forEachVisitor(server, player ->
                PacketDistributor.sendToPlayer(player, new CameraShakePayload(0.05F, 60)));
        if (current.ticks == DETONATION_TICK) {
            forEachVisitor(server, player -> {
                cue(player, TimeIntroPayload.DETONATION);
                ScreenTintPayload.send(player, 0xE8FFF6E6, 26, ScreenTintPayload.Style.FLASH);
            });
        }
        if (current.ticks == DETONATION_TICK + 6) {
            forEachVisitor(server, player -> {
                cue(player, TimeIntroPayload.BLAST_SOUND);
                PacketDistributor.sendToPlayer(player, new CameraShakePayload(0.22F, 70));
            });
        }
        if (current.ticks >= RETURN_TICK) finish(server, city, true);
    }

    /** Visitors walk where they like in the southern half of the city, but no further. */
    private static void keepInside(ServerLevel city, ServerPlayer player) {
        double x = Math.clamp(player.getX(), ROAM_X0, ROAM_X1), z = Math.clamp(player.getZ(), ROAM_Z0, ROAM_Z1);
        double y = Math.clamp(player.getY(), CityGrid.STREET_Y - 1, 200);
        if (x != player.getX() || z != player.getZ() || y != player.getY()) {
            player.teleportTo(city, x, y, z, player.getYRot(), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0;
        }
    }

    /** The wave is a few blocks away. Time stops: for this visitor, everything holds still. */
    private static void freeze(Scene current, ServerPlayer player) {
        current.frozenAt.put(player.getUUID(), current.ticks);
        current.frozenWhere.put(player.getUUID(), player.position());
        player.setDeltaMovement(Vec3.ZERO);
        cue(player, TimeIntroPayload.FREEZE);
        player.playNotifySound(ModSounds.TIME_SLOW_STOP.get(), SoundSource.PLAYERS, 1.0F, 0.7F);
        ScreenTintPayload.send(player, 0x709E80E8, IntroBlast.FREEZE_TICKS + IntroBlast.REWIND_TICKS, ScreenTintPayload.Style.EDGE);
    }

    private static void startNext(MinecraftServer server, ServerLevel city) {
        while (!QUEUE.isEmpty()) {
            Request request = QUEUE.removeFirst();
            ServerPlayer owner = server.getPlayerList().getPlayer(request.owner);
            if (owner == null || owner.getData(ModDataAttachments.TIME_INTRO_SEEN)
                    || PlayerAbilityService.getData(owner).ability(POWER)
                    .filter(progress -> progress.isUnlocked()).isEmpty()) continue;
            Scene next = new Scene(request.owner);
            scene = next;
            forceCity(city, true);
            // Remove a previous scene's saved crowd, including after a server crash.
            List<Entity> stale = new ArrayList<>();
            for (Entity entity : city.getAllEntities()) {
                if (entity.getTags().contains(CROWD_TAG)) stale.add(entity);
            }
            stale.forEach(Entity::discard);
            int index = 0;
            for (UUID id : request.group) {
                ServerPlayer visitor = server.getPlayerList().getPlayer(id);
                if (visitor == null || !visitor.isAlive() || isVisiting(visitor) || isCity(visitor.level())) continue;
                next.visitors.add(id);
                visitor.setData(ModDataAttachments.TIME_INTRO_VISIT, TimeIntroVisit.capture(visitor));
                // Remembered for good: telepaths who were there recognise the city in others' dreams.
                visitor.setData(ModDataAttachments.PLAYER_SEEN_FUTURE_CITY.get(), true);
                if (PlayerAbilityService.getData(visitor).ability(POWER)
                        .filter(progress -> progress.isUnlocked()).isPresent()) {
                    visitor.setData(ModDataAttachments.TIME_INTRO_SEEN, true);
                }
                // Save the return address before crossing dimensions.
                persist(visitor);
                suspendPowers(visitor);
                visitor.closeContainer();
                visitor.stopRiding();
                visitor.ejectPassengers();
                visitor.teleportTo(city, ARRIVAL.x - 2.4 + (index % 5) * 1.2, ARRIVAL.y,
                        ARRIVAL.z + (index / 5 % 8) * 1.4, 180, -4);
                visitor.setDeltaMovement(Vec3.ZERO);
                visitor.fallDistance = 0;
                visitor.setPortalCooldown();
                persist(visitor);
                cue(visitor, TimeIntroPayload.ARRIVAL);
                visitor.playNotifySound(ModSounds.NEW_BEGINNING.get(), SoundSource.MUSIC, 0.35F, 1);
                visitor.playNotifySound(ModSounds.TIME_BLINK.get(), SoundSource.PLAYERS, 0.9F, 1);
                ScreenTintPayload.send(visitor, 0x887E58B8, 24, ScreenTintPayload.Style.FLASH);
                index++;
            }
            next.crowd = VisionCrowd.spawn(city, CROWD_TAG, CROWD_SIZE);
            return;
        }
    }

    private static void suspendPowers(ServerPlayer player) {
        TimeSlowService.stop(player);
        TimeStopService.stop(player);
        com.jayptucker.heroesevolved.flight.FlightService.stopForPowerRemoval(player);
        com.jayptucker.heroesevolved.flight.DiveBombService.forget(player);
        com.jayptucker.heroesevolved.entity.EntityCarryService.release(player);
        com.jayptucker.heroesevolved.quake.QuakeService.stopAll(player);
        com.jayptucker.heroesevolved.telekinesis.TelekinesisService.stopAll(player);
    }

    private static void animateCrowd(ServerLevel city, Scene current) {
        if (current.crowd == null) return;
        int afterLight = current.ticks - DETONATION_TICK;
        // Whoever the dust reaches is gone.
        if (afterLight >= 0) current.crowd.engulf(IntroBlast.front(afterLight));
        List<ServerPlayer> watching = new ArrayList<>();
        for (UUID id : current.visitors) {
            ServerPlayer player = city.getServer().getPlayerList().getPlayer(id);
            if (player != null && isCity(player.level())) watching.add(player);
        }
        current.crowd.tick(city, current.ticks, watching, CROWD_TAG);
    }

    public static void returnHome(ServerPlayer player, boolean completed) {
        TimeIntroVisit visit = player.getData(ModDataAttachments.TIME_INTRO_VISIT);
        MinecraftServer server = player.getServer();
        if (server == null || (!visit.pending() && !isCity(player.level()))) return;
        ServerLevel home = visit.pending() ? server.getLevel(ResourceKey.create(Registries.DIMENSION,
                visit.dimension())) : null;
        boolean fallback = home == null;
        if (fallback) home = server.overworld();
        BlockPos spawn = home.getSharedSpawnPos();
        player.teleportTo(home, fallback ? spawn.getX() + 0.5 : visit.x(),
                fallback ? home.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                        spawn.getX(), spawn.getZ()) + 1 : visit.y(),
                fallback ? spawn.getZ() + 0.5 : visit.z(), visit.yaw(), visit.pitch());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        player.setPortalCooldown();
        player.setData(ModDataAttachments.TIME_INTRO_VISIT, TimeIntroVisit.empty());
        persist(player);
        cue(player, completed ? TimeIntroPayload.RETURN : TimeIntroPayload.CANCEL);
        if (completed) {
            com.jayptucker.heroesevolved.time.TemporalStoryService.reach(player, com.jayptucker.heroesevolved.time.TemporalStoryService.VISION);
            player.playNotifySound(ModSounds.TIME_BLINK.get(), SoundSource.PLAYERS, 0.9F, 0.85F);
            ScreenTintPayload.send(player, 0x908AB6FF, 25, ScreenTintPayload.Style.FLASH);
            AbilityEffects.at(player, AbilityEffectPayload.Kind.TEMPORAL_BURST, 4);
            // Awakenings come in a fight: whatever was fighting you is now a heartbeat behind.
            com.jayptucker.heroesevolved.events.AwakeningService.secondWind(player);
            for (net.minecraft.world.entity.Mob mob : player.serverLevel().getEntitiesOfClass(
                    net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(12),
                    mob -> mob instanceof net.minecraft.world.entity.monster.Enemy && mob.isAlive())) {
                mob.setTarget(null);
                mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 100, 6));
                mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.WEAKNESS, 100, 1));
            }
        }
    }

    public static void logout(ServerPlayer player) {
        // Return before vanilla writes the departing player's data.
        AvenueService.logout(player);
        returnHome(player, false);
        if (scene != null) scene.visitors.remove(player.getUUID());
        QUEUE.removeIf(request -> request.owner.equals(player.getUUID()));
    }

    public static void shutdown(MinecraftServer server) {
        AvenueService.shutdown(server);
        ServerLevel city = server.getLevel(DIMENSION);
        if (scene != null && city != null) finish(server, city, false);
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (isVisiting(player) || isCity(player.level())) returnHome(player, false);
        }
        QUEUE.clear();
        scene = null;
        configured = false;
    }

    private static void finish(MinecraftServer server, ServerLevel city, boolean completed) {
        Scene current = scene;
        forEachVisitor(server, player -> returnHome(player, completed));
        if (current.crowd != null) current.crowd.discard();
        forceCity(city, false);
        scene = null;
    }

    public static void forceCity(ServerLevel city, boolean force) {
        int first = FractureCity.MIN >> 4, last = (FractureCity.MIN + FractureCity.WIDTH - 1) >> 4;
        for (int x = first; x <= last; x++) for (int z = first; z <= last; z++) city.setChunkForced(x, z, force);
    }

    private static void forEachVisitor(MinecraftServer server, java.util.function.Consumer<ServerPlayer> action) {
        for (UUID id : List.copyOf(scene.visitors)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) action.accept(player);
        }
    }

    private static void cue(ServerPlayer player, int stage) {
        PacketDistributor.sendToPlayer(player, new TimeIntroPayload(stage));
    }

    private static void persist(ServerPlayer player) {
        if (!player.isFakePlayer()) {
            ((com.jayptucker.heroesevolved.mixin.PlayerListSaveAccess) player.getServer().getPlayerList())
                    .heroesEvolved$savePlayer(player);
        }
    }

    /** For previews: jump the running vision to {@code ticks} (e.g. just before the light). */
    public static void skipTo(int ticks) {
        if (scene != null && ticks > scene.ticks) scene.ticks = ticks;
    }
}
