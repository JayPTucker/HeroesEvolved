package com.jayptucker.heroesevolved.story.future;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.intro.PowerIntroService;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.FutureLookPayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.story.actor.StoryActorEntity;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.village.Days;
import com.jayptucker.heroesevolved.village.VillageTickets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** An omen, then a warning one Minecraft day later. The scene never edits terrain, teleports or traps its audience. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class FutureWarning {
    private static final String TAG = "heroesevolved_future_warning";
    private static final Map<UUID, Run> RUNS = new HashMap<>();
    private FutureWarning() {}

    /** Natural encounters wait for an ordinary, quiet moment in the present. Operators use the same safety checks. */
    public static boolean eligible(ServerPlayer p) {
        return p != null && p.isAlive() && !p.isRemoved() && p.level().dimension() == Level.OVERWORLD
                && !p.isCreative() && !p.isSpectator() && !p.isSleeping() && !p.isPassenger()
                && p.onGround() && !p.isInWaterOrBubble() && !p.isInLava() && !p.isOnFire()
                && p.hurtTime == 0 && (p.getLastHurtByMob() == null || p.tickCount - p.getLastHurtByMobTimestamp() > 200)
                && !CryoChamberService.isInside(p) && !PrologueService.isMember(p) && !PrologueService.isWaiting(p)
                && !PowerIntroService.isActive(p);
    }

    /** Finds a clear, visible space behind the player, including room for the double's retreat into the tear. */
    public static Vec3 site(ServerPlayer p) {
        var level = p.serverLevel();
        Vec3 back = Vec3.directionFromRotation(0, p.getYRot()).scale(-1);
        for (int distance = 4; distance <= 6; distance++) {
            Vec3 feet = p.position().add(back.scale(distance));
            Vec3 center = feet.add(0, 1.2, 0);
            AABB room = new AABB(feet.x - 1.5, feet.y, feet.z - 1.5, feet.x + 1.5, feet.y + 3, feet.z + 1.5);
            boolean loaded = true;
            for (int x = BlockPos.containing(room.minX, room.minY, room.minZ).getX() >> 4; x <= (BlockPos.containing(room.maxX, room.maxY, room.maxZ).getX() >> 4); x++)
                for (int z = BlockPos.containing(room.minX, room.minY, room.minZ).getZ() >> 4; z <= (BlockPos.containing(room.maxX, room.maxY, room.maxZ).getZ() >> 4); z++)
                    loaded &= level.hasChunk(x, z);
            if (!loaded || !level.getWorldBorder().isWithinBounds(room) || !level.noCollision(room) || level.containsAnyLiquid(room)) continue;
            if (level.clip(new ClipContext(p.getEyePosition(), center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType()
                    == HitResult.Type.MISS) return center;
        }
        return null;
    }

    public static boolean start(ServerPlayer p, boolean warning) {
        if (!eligible(p) || active(p)) return false;
        Vec3 center = site(p);
        if (center == null) return false;
        Run run = new Run(p, center, warning);
        if (warning && run.actor == null) return false;
        var level = p.serverLevel();
        FutureWarningData.get(p.getServer()).queue(p.getUUID(), Days.clock(level) + (warning ? 0 : Days.DAY),
                level.getGameTime() + (warning ? 0 : Days.DAY));
        RUNS.put(p.getUUID(), run);
        VillageTickets.hold(level, BlockPos.containing(center), key(p), VillageTickets.SPOT);
        if (warning) PacketDistributor.sendToPlayer(p, new FutureLookPayload(center, FutureWarningGeometry.TURN));
        run.step();
        return true;
    }
    public static boolean active(ServerPlayer p) { return p != null && RUNS.containsKey(p.getUUID()); }
    public static boolean pending(ServerPlayer p) { return p != null && FutureWarningData.get(p.getServer()).due(p.getUUID()) != null; }
    public static String status(MinecraftServer server, ServerPlayer p) {
        var data = FutureWarningData.get(server);
        if (p == null) return data.pendingCount() + " warning(s) pending, including offline players.";
        Run run = RUNS.get(p.getUUID());
        if (run != null) return (run.warning ? "Warning" : "Omen") + " playing: " + run.age + " ticks.";
        var due = data.due(p.getUUID());
        if (due == null) return data.heard(p.getUUID()) ? "Warning heard or cancelled; natural replay suppressed." : "Not yet chosen.";
        long remaining = Math.max(0, Math.min(due.calendar() - Days.clock(server.overworld()), due.game() - server.overworld().getGameTime()));
        return remaining == 0 ? "Warning due; waiting for a safe, clear moment in the Overworld." : "Warning in " + remaining + " ticks (sleep advances the calendar).";
    }
    private static String key(ServerPlayer p) { return TAG + p.getUUID(); }

    /** Cancellation consumes queued encounters too, so they cannot resume on reconnect. Reset explicitly permits another draw. */
    public static void cancel(ServerPlayer p, boolean reset) {
        suspend(p);
        var data = FutureWarningData.get(p.getServer());
        if (reset) data.reset(p.getUUID()); else data.finish(p.getUUID());
    }
    public static void cancelAll(MinecraftServer server) {
        for (Run run : List.copyOf(RUNS.values())) suspend(run.player);
        FutureWarningData.get(server).cancelAll();
    }
    private static void suspend(ServerPlayer p) {
        Run run = RUNS.remove(p.getUUID());
        if (run == null) return;
        if (run.actor != null) run.actor.discard();
        SetPiecePayload.send(run.level, Piece.FUTURE_SHUT, run.center, run.center.add(run.normal), .1F, null);
        PacketDistributor.sendToPlayer(p, new FutureLookPayload(Vec3.ZERO, 0));
        VillageTickets.release(key(p));
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (Run run : List.copyOf(RUNS.values())) {
            ServerPlayer p = run.player;
            if (!eligible(p) || p.serverLevel() != run.level || p.position().distanceToSqr(run.center) > 24 * 24
                    || run.actor != null && run.actor.isRemoved()) { suspend(p); continue; }
            if (++run.age >= (run.warning ? FutureWarningGeometry.END : FutureWarningGeometry.OMEN)) {
                suspend(p);
                if (run.warning) FutureWarningData.get(server).finish(p.getUUID());
            } else run.step();
        }
        if (server.getTickCount() % 20 != 0) return;
        var level = server.overworld();
        var data = FutureWarningData.get(server);
        deliver(server);
        if (level.getGameTime() < data.nextDraw()) return;
        data.nextDraw(level.getGameTime() + Days.DAY);
        ServerPlayer chosen = choose(server.getPlayerList().getPlayers(), data, level.random);
        if (chosen != null) start(chosen, false);
    }
    /** Delivers only to the original recipient, when they are online and safe; a failed start keeps the saved queue. */
    static void deliver(MinecraftServer server) {
        var level = server.overworld();
        var data = FutureWarningData.get(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            var due = data.due(p.getUUID());
            if (due != null && due.ready(Days.clock(level), level.getGameTime()) && !active(p)) start(p, true);
        }
    }
    static ServerPlayer choose(List<ServerPlayer> players, FutureWarningData data, net.minecraft.util.RandomSource random) {
        List<ServerPlayer> candidates = players.stream().filter(p -> data.available(p.getUUID()) && eligible(p) && site(p) != null).toList();
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) suspend(p); }
    @SubscribeEvent public static void stop(ServerStoppingEvent e) {
        for (Run run : List.copyOf(RUNS.values())) suspend(run.player);
    }

    private static final class Run {
        final ServerPlayer player;
        final ServerLevel level;
        final Vec3 center, normal;
        final boolean warning;
        final StoryActorEntity actor;
        int age;
        Run(ServerPlayer p, Vec3 center, boolean warning) {
            player = p; level = p.serverLevel(); this.center = center; this.warning = warning;
            normal = p.position().subtract(center).multiply(1, 0, 1).normalize();
            actor = warning ? StoryActorEntity.spawn(level, StoryActorEntity.Role.TRAVELER,
                    FutureWarningGeometry.actor(center, normal, 0), p.getYRot() + 180, TAG) : null;
            if (actor != null) {
                actor.doubleOf(p.getUUID(), p.getGameProfile().getName());
                actor.pose(StoryActorEntity.Pose.HAND_ON_GLASS);
                actor.glide(actor.position(), p.position()); actor.face(p.position());
            }
        }
        void sound(SoundEvent sound, float volume, float pitch) {
            level.playSound(null, center.x, center.y, center.z, sound, SoundSource.AMBIENT, volume, pitch);
        }
        void step() {
            if (!warning) {
                if (age == 0 || age == 28 || age == 56) {
                    SetPiecePayload.send(level, Piece.FUTURE_OMEN, center, center.add(normal), 1, null);
                    sound(ModSounds.TIME_BLINK.get(), .5F, .65F + age * .004F);
                }
                if (age == 15) sound(ModSounds.CROWD_PANIC.get(), .22F, .65F);
                if (age == 58) sound(SoundEvents.LIGHTNING_BOLT_THUNDER, .6F, .65F);
                return;
            }
            actor.glide(FutureWarningGeometry.actor(center, normal, age), player.position());
            actor.face(player.position());
            // Hide once entirely through: a side-on viewer must not see the body emerge behind the flat membrane.
            actor.setInvisible(age >= FutureWarningGeometry.SHUT - 8);
            actor.pose(age < FutureWarningGeometry.PULL ? StoryActorEntity.Pose.HAND_ON_GLASS : StoryActorEntity.Pose.PLEAD);
            if (age < FutureWarningGeometry.SHUT && age % 10 == 0)
                SetPiecePayload.sendSeeded(level, Piece.FUTURE_BREACH, center, center.add(normal), 1, actor, 0, age);
            if (age == 0) sound(ModSounds.VIBE_BREACH.get(), 1.2F, .7F);
            if (age == 42) say("Listen. When Sylar looks past you... don't let him reach the dragon.");
            if (age == 108) say("The wings aren't what you should be afraid of. The light after—");
            if (age == 184) { say("I thought we had another chance. Please—"); sound(ModSounds.TIME_BLINK.get(), 1.5F, .5F); }
            if (age == FutureWarningGeometry.SHUT) {
                actor.setInvisible(true);
                SetPiecePayload.send(level, Piece.FUTURE_SHUT, center, center.add(normal), 1, null);
                sound(SoundEvents.GENERIC_EXPLODE.value(), FutureWarningGeometry.BOOM_VOLUME, .55F);
                sound(SoundEvents.LIGHTNING_BOLT_THUNDER, FutureWarningGeometry.BOOM_VOLUME, .7F);
                CameraShakePayload.sendAround(level, center, 128, .35F, 18);
            }
        }
        void say(String line) {
            actor.say(line, false, level.players().stream().filter(p -> p.distanceToSqr(actor) < 32 * 32).toList(), TAG);
        }
    }
}
