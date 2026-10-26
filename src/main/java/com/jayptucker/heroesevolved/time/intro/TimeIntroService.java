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
import com.jayptucker.heroesevolved.time.TimeSlowService;
import com.jayptucker.heroesevolved.time.TimeStopService;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** One shared cinematic at a time, with durable per-visitor return tickets. */
public final class TimeIntroService {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "time_fracture"));
    private static final ResourceLocation POWER = ResourceLocation.fromNamespaceAndPath(
            HeroesEvolved.MOD_ID, "time_manipulation");
    public static final Vec3 ARRIVAL = new Vec3(4, 64, 70);
    public static final Vec3 BLAST = new Vec3(4, 64, -78);
    public static final int DETONATION_TICK = 220;
    public static final int RETURN_TICK = 350;
    public static final int CROWD_SIZE = 24;
    private static final String CROWD_TAG = "heroesevolved_time_intro_crowd";
    private static final ArrayDeque<Request> QUEUE = new ArrayDeque<>();
    private static Scene scene;
    private static boolean configured;

    private record Request(UUID owner, List<UUID> group) {}
    private static final class Scene {
        final UUID owner;
        final Set<UUID> visitors = new LinkedHashSet<>();
        final List<Villager> crowd = new ArrayList<>();
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
        return scene != null && scene.crowd.contains(entity);
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
        if (isVisiting(player) || isCity(player.level())) {
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
            if (cityData.ready()) startNext(server, city);
            return;
        }
        Scene current = scene;
        current.ticks++;
        current.visitors.removeIf(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) return true; // Its saved ticket remains for reconnect recovery.
            if (!isCity(player.level())) {
                returnHome(player, false);
                return true;
            }
            // Keep the group on the avenue and within the blast's safe distance.
            if (player.getX() < -2 || player.getX() > 10 || player.getZ() < 58
                    || player.getZ() > 92 || player.getY() < 63 || player.getY() > 80) {
                player.teleportTo(city, Math.clamp(player.getX(), 0.5, 7.5), 64,
                        Math.clamp(player.getZ(), 60, 90), player.getYRot(), player.getXRot());
                player.setDeltaMovement(Vec3.ZERO);
                player.fallDistance = 0;
            }
            return false;
        });
        if (current.visitors.isEmpty()) {
            finish(server, city, false);
            return;
        }
        animateCrowd(city, current);
        if (current.ticks == DETONATION_TICK) {
            forEachVisitor(server, player -> {
                cue(player, TimeIntroPayload.DETONATION);
                ScreenTintPayload.send(player, 0xB8FFF1D5, 22, ScreenTintPayload.Style.FLASH);
            });
        }
        if (current.ticks == DETONATION_TICK + 10) {
            forEachVisitor(server, player -> {
                cue(player, TimeIntroPayload.BLAST_SOUND);
                PacketDistributor.sendToPlayer(player, new CameraShakePayload(0.38F, 100));
            });
        }
        if (current.ticks == RETURN_TICK - 22) {
            forEachVisitor(server, player -> {
                player.playNotifySound(ModSounds.TIME_SLOW_STOP.get(), SoundSource.PLAYERS, 0.9F, 1);
                ScreenTintPayload.send(player, 0x709E80E8, 22, ScreenTintPayload.Style.EDGE);
                AbilityEffects.at(player, AbilityEffectPayload.Kind.TEMPORAL_REWIND, 4);
            });
        }
        if (current.ticks >= RETURN_TICK) finish(server, city, true);
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
                visitor.teleportTo(city, 1.2 + (index % 5) * 1.35, 64,
                        70 + (index / 5 % 12) * 1.5, 180, -5);
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
            spawnCrowd(city, next);
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

    private static void spawnCrowd(ServerLevel city, Scene current) {
        for (int i = 0; i < CROWD_SIZE; i++) {
            Villager villager = EntityType.VILLAGER.create(city);
            if (villager == null) continue;
            // Most are on the central avenue; others flee along adjoining streets.
            double x = i < 18 ? 0.9 + i % 3 * 2.1 : -63 + i % 5 * 32;
            double z = i < 18 ? -25 + i / 3 * 20 + (i % 3) * 2 : 34 + i % 6 * 9;
            villager.moveTo(x, 64, Math.min(90, z), 0, 0);
            villager.setVillagerData(villager.getVillagerData().setType(VillagerType.PLAINS)
                    .setProfession(VillagerProfession.NITWIT));
            villager.setInvulnerable(true);
            villager.setSilent(true); // Deliberately timed panic vocals instead of trading chatter.
            villager.setPersistenceRequired();
            villager.addTag(CROWD_TAG);
            current.crowd.add(villager);
            if (!city.addFreshEntity(villager)) current.crowd.remove(villager);
        }
    }

    private static void animateCrowd(ServerLevel city, Scene current) {
        if (current.ticks % 16 != 0) return;
        for (int i = 0; i < current.crowd.size(); i++) {
            Villager villager = current.crowd.get(i);
            if (!villager.isAlive()) continue;
            // Refresh hurt memory to trigger the vanilla panic behaviour, without doing damage.
            villager.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HURT_BY,
                    city.damageSources().generic());
            double destinationZ = villager.getZ() > 86 ? 60 : Math.min(91, villager.getZ() + 18);
            villager.getNavigation().moveTo(villager.getX() + Math.sin(i + current.ticks * 0.1) * 1.3,
                    64, destinationZ, 1.65);
        }
        if (current.ticks >= current.nextCry) {
            // Stagger nearby voices and keep the recordings close to their natural pitch.
            List<Villager> nearby = current.crowd.stream().filter(v -> v.isAlive()
                    && v.getX() > -8 && v.getX() < 12 && v.getZ() > 25).toList();
            if (!nearby.isEmpty()) {
                Villager voice = nearby.get(city.random.nextInt(nearby.size()));
                city.playSound(null, voice.blockPosition(), ModSounds.CROWD_PANIC.get(),
                        SoundSource.NEUTRAL, 0.8F, 0.97F + city.random.nextFloat() * 0.06F);
            }
            current.nextCry = current.ticks + 38 + city.random.nextInt(27);
        }
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
        }
    }

    public static void logout(ServerPlayer player) {
        // Return before vanilla writes the departing player's data.
        returnHome(player, false);
        if (scene != null) scene.visitors.remove(player.getUUID());
        QUEUE.removeIf(request -> request.owner.equals(player.getUUID()));
    }

    public static void shutdown(MinecraftServer server) {
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
        current.crowd.forEach(Entity::discard);
        forceCity(city, false);
        scene = null;
    }

    private static void forceCity(ServerLevel city, boolean force) {
        for (int x = -6; x < 6; x++) for (int z = -6; z < 6; z++) city.setChunkForced(x, z, force);
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

}
