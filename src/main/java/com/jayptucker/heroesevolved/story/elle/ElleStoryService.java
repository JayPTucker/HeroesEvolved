package com.jayptucker.heroesevolved.story.elle;

import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.grid.GridDisruption;
import com.jayptucker.heroesevolved.intro.PowerIntroService;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.radio.EmergencyBand;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.story.sylar.SylarStoryService;
import com.jayptucker.heroesevolved.story.sylar.WatchmakerArena;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.time.TemporalSnapshotService;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * When the Company sends Elle Bishop, and how her visit ends.
 *
 * <p>A player who has had a power for a few days ({@code elle.firstVisitDays}) is noticed. The day before she comes
 * ({@code elle.omenTicks}, one Minecraft day by default), {@link CompanyOmens} makes the grid around them go wrong, more
 * often as the day runs out, and late in the day she speaks on the emergency band. Then the lights go out around them,
 * she steps out of the dark, a dart stings, and they wake on Primatech's test floor ({@link PrimatechArena}).</p>
 */
public final class ElleStoryService {
    public static final long DAY = 24000;
    /** How long the arrival takes, from the lights going out to waking on the test floor. */
    public static final int ARRIVAL_TICKS = 60;

    public enum Ending { DEFEATED, LOST, TIME }

    private static final class Arrival {
        final UUID player;
        final long began;
        ElleEntity scene;
        Arrival(UUID player, long began) { this.player = player; this.began = began; }
    }

    private static final Map<UUID, Arrival> ARRIVALS = new HashMap<>();
    private static ElleEntity active;

    private ElleStoryService() {}

    private static long now(MinecraftServer server) { return server.overworld().getGameTime(); }

    /** Elle in the fight that is on, if any. */
    public static ElleEntity elle() { return active != null && active.isAlive() ? active : null; }
    public static boolean arriving(ServerPlayer player) { return ARRIVALS.containsKey(player.getUUID()); }

    /** Someone the Company can come for: alive, in the Overworld, with an awakened power, and not in some other story. */
    public static boolean eligible(ServerPlayer player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator() && !player.isSleeping()
                && player.level().dimension().equals(Level.OVERWORLD)
                && player.level().getDifficulty() != Difficulty.PEACEFUL
                && !PowerIntroService.isActive(player) && !TimeIntroService.isVisiting(player)
                && !TemporalSnapshotService.isTemporalDimension(player.serverLevel())
                && !EclipseService.arePowersSuppressed(player)
                && !WatchmakerArena.inShop(player) && SylarStoryService.hunter() == null
                && PlayerAbilityService.getData(player).assignedPower().filter(entry -> entry.getValue().isUnlocked()).isPresent();
    }

    // ------------------------------------------------------------------
    // The schedule
    // ------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        ElleVoice.tick(server);
        CompanyOmens.tick(server);
        PrimatechArena.tick(server);
        tickArrivals(server);
        if (!HeroesEvolvedConfig.COMMON.elleEnabled.get()) return;
        long time = now(server);
        if (time % 20 != 0) return;
        var data = CompanyVisitData.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            var visit = data.visit(player.getUUID());
            if (visit.omens(time)) {
                if (eligible(player)) omen(player, visit, time);
                continue;
            }
            if (visit.arriveAt > 0 && time >= visit.arriveAt) {
                // The day is up: she comes, as soon as nothing else has hold of them.
                if (active == null && !arriving(player) && eligible(player) && PrimatechArena.available(server)) {
                    visit.omensAt = 0; visit.arriveAt = 0; visit.taunted = false; visit.noticed = 0; data.setDirty();
                    arrive(player);
                }
                continue;
            }
            // Natural visits wait for the story to have met the Company (the relay rescue), like Sylar's hunts.
            if (!com.jayptucker.heroesevolved.story.LastSignalService.completed(server) || !eligible(player)) continue;
            if (visit.next == 0) {
                visit.next = time + Math.max(0, HeroesEvolvedConfig.COMMON.elleFirstVisitDays.get() - 1) * DAY;
                data.setDirty();
            }
            if (time >= visit.next && visit.omensAt == 0) beginOmens(player);
        }
    }

    /** The day of omens begins for {@code player}: she arrives when it ends. */
    public static void beginOmens(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        var data = CompanyVisitData.get(server);
        var visit = data.visit(player.getUUID());
        long time = now(server);
        visit.omensAt = time;
        visit.arriveAt = time + HeroesEvolvedConfig.COMMON.elleOmenTicks.get();
        visit.taunted = false; visit.noticed = 0;
        data.setDirty();
        CompanyOmens.haunt(player);
    }

    /** One second of the day of omens: an omen now and then, more often as the day runs out; late on, her voice on the radio. */
    private static void omen(ServerPlayer player, CompanyVisitData.Visit visit, long time) {
        float progress = visit.progress(time);
        // From one omen every half a minute or so to one every four seconds.
        if (player.getRandom().nextFloat() < 0.035F + 0.22F * progress * progress) {
            CompanyOmens.Kind kind = CompanyOmens.haunt(player);
            int bit = kind == null ? 8 : 1 << kind.ordinal();
            if ((visit.noticed & bit) == 0) {
                visit.noticed |= bit;
                CompanyVisitData.get(player.getServer()).setDirty();
                ElleVoice.narrate(player, "story.heroesevolved.elle.omen." + (kind == null ? "hum" : kind.name().toLowerCase(java.util.Locale.ROOT)));
            }
        }
        if (!visit.taunted && progress >= 0.75F) {
            visit.taunted = true;
            CompanyVisitData.get(player.getServer()).setDirty();
            taunt(player);
        }
    }

    /** Elle on the emergency band, sounding pleased with herself. Subtitles reach anyone near when there is no radio. */
    public static void taunt(ServerPlayer player) {
        Vec3 from = player.position().add(player.getLookAngle().multiply(1, 0, 1).scale(-40));
        try {
            EmergencyBand.call(player.serverLevel(), BlockPos.containing(from), Component.translatable("story.heroesevolved.elle.radio"), 20 * 8);
        } catch (RuntimeException | LinkageError radioMissing) {
            // The band is another feature; if it isn't there the line still reaches them.
        }
        ElleVoice.say(player, "story.heroesevolved.elle.say.radio");
    }

    // ------------------------------------------------------------------
    // The arrival
    // ------------------------------------------------------------------

    /** The lights go out around {@code player}, she steps out of the dark, and a dart takes them. False when it can't happen now. */
    public static boolean arrive(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (active != null || arriving(player) || !PrimatechArena.available(server) || PrimatechArena.open()) return false;
        ServerLevel level = player.serverLevel();
        Arrival arrival = new Arrival(player.getUUID(), now(server));
        ARRIVALS.put(player.getUUID(), arrival);
        // Lights out: every circuit for two dozen blocks dies at once.
        GridDisruption.jam(level, player.blockPosition(), 24, ARRIVAL_TICKS + 20);
        ScreenTintPayload.send(player, 0x90000000, ARRIVAL_TICKS, ScreenTintPayload.Style.VIGNETTE);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.6F, 0.5F);
        // She stands a few blocks off, crackling in the dark.
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1e-4) look = new Vec3(0, 0, 1);
        Vec3 at = player.position().add(look.normalize().scale(5));
        for (int dy = 2; dy >= -3; dy--) {
            Vec3 tryAt = at.add(0, dy, 0);
            ElleEntity elle = ModEntities.ELLE.get().create(level);
            if (elle == null) break;
            elle.moveTo(tryAt.x, Math.floor(tryAt.y), tryAt.z, 0, 0);
            if (!level.noCollision(elle) || level.noCollision(elle, elle.getBoundingBox().move(0, -0.5, 0))) { elle.discard(); continue; }
            elle.scene(true);
            elle.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
            elle.setYBodyRot(elle.getYRot()); elle.setYHeadRot(elle.getYRot());
            elle.setNoAi(true);
            if (level.addFreshEntity(elle)) arrival.scene = elle;
            break;
        }
        Vec3 where = arrival.scene != null ? arrival.scene.position() : at;
        SetPiecePayload.send(level, Piece.ELLE_ARRIVE, where, player.getEyePosition(), 1, arrival.scene);
        ElleVoice.say(player, "story.heroesevolved.elle.say.arrive");
        return true;
    }

    private static void tickArrivals(MinecraftServer server) {
        if (ARRIVALS.isEmpty()) return;
        long time = now(server);
        for (Arrival arrival : java.util.List.copyOf(ARRIVALS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(arrival.player);
            long age = time - arrival.began;
            if (player == null || !player.isAlive()) { dropScene(arrival); ARRIVALS.remove(arrival.player); continue; }
            if (age == 40) {
                // The dart.
                player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.0F, 1.6F);
                player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ARROW_HIT_PLAYER, SoundSource.HOSTILE, 1.0F, 1.2F);
                ScreenTintPayload.send(player, 0xFF000000, 50, ScreenTintPayload.Style.FLASH);
                ElleVoice.narrate(player, "story.heroesevolved.elle.narrate.dart");
            }
            if (age < ARRIVAL_TICKS) continue;
            ARRIVALS.remove(arrival.player);
            dropScene(arrival);
            ElleEntity elle = PrimatechArena.enter(player);
            if (elle == null) continue;
            active = elle;
            var data = CompanyVisitData.get(server);
            data.visit(player.getUUID()).visits++;
            data.setDirty();
            ScreenTintPayload.send(player, 0xFF000000, 40, ScreenTintPayload.Style.FLASH);
            for (ServerPlayer witness : PrimatechArena.level(server).players()) {
                ElleVoice.narrate(witness, "story.heroesevolved.elle.narrate.wake");
                ElleVoice.say(witness, "story.heroesevolved.elle.say.wake");
                ElleVoice.warn(witness, "story.heroesevolved.elle.warn.banks");
            }
        }
    }

    private static void dropScene(Arrival arrival) {
        if (arrival.scene != null && !arrival.scene.isRemoved()) {
            var level = (ServerLevel) arrival.scene.level();
            SetPiecePayload.send(level, Piece.ELLE_SHORT, arrival.scene.getEyePosition(), arrival.scene.position(), 1, null);
            arrival.scene.discard();
        }
        arrival.scene = null;
    }

    /** Skips the arrival's minute of darkness: straight onto the test floor (tests and previews). */
    public static ElleEntity arriveNow(ServerPlayer player) {
        ElleEntity elle = PrimatechArena.enter(player);
        if (elle != null) {
            active = elle;
            CompanyVisitData.get(player.getServer()).visit(player.getUUID()).visits++;
            CompanyVisitData.get(player.getServer()).setDirty();
        }
        return elle;
    }

    // ------------------------------------------------------------------
    // Endings
    // ------------------------------------------------------------------

    /** She is beaten: what she carried goes to whoever finished her (or her subject), and everyone goes home. */
    static void defeated(ElleEntity elle, Entity killer) {
        if (active != elle) return;
        MinecraftServer server = elle.getServer();
        ServerLevel level = (ServerLevel) elle.level();
        ServerPlayer finder = killer instanceof ServerPlayer p ? p : server.getPlayerList().getPlayer(elle.subject());
        if (finder != null && finder.level() == level) {
            SetPiecePayload.send(level, Piece.ELLE_DROP, elle.getBoundingBox().getCenter(), finder.getEyePosition(), 1, finder);
            PrimatechArena.gift(finder, ElleCapacitorItem.charged(new ItemStack(ModItems.ELLES_CAPACITOR.get()), ElleCapacitorItem.MAX_CHARGES));
            ElleVoice.narrate(finder, "story.heroesevolved.elle.narrate.capacitor");
        }
        var data = CompanyVisitData.get(server);
        for (ServerPlayer witness : level.players()) {
            ElleVoice.say(witness, "story.heroesevolved.elle.say.defeated");
            var visit = data.visit(witness.getUUID());
            visit.beaten++;
            visit.next = now(server) + HeroesEvolvedConfig.COMMON.elleRevisitDays.get() * DAY;
        }
        data.setDirty();
        active = null;
        PrimatechArena.close(100);
    }

    /** She ends it: everyone is down, or she has her data. */
    static void ended(ElleEntity elle, Ending how) {
        if (active != elle && active != null) return;
        MinecraftServer server = elle.getServer();
        ServerLevel level = (ServerLevel) elle.level();
        var data = CompanyVisitData.get(server);
        for (ServerPlayer witness : level.players()) {
            ElleVoice.say(witness, how == Ending.TIME ? "story.heroesevolved.elle.say.time" : "story.heroesevolved.elle.say.lost");
            data.visit(witness.getUUID()).next = now(server) + 2 * DAY;
        }
        if (elle.subject() != null) data.visit(elle.subject()).next = now(server) + 2 * DAY;
        data.setDirty();
        SetPiecePayload.send(level, Piece.ELLE_SHORT, elle.getEyePosition(), elle.position(), 2, null);
        elle.discard();
        active = null;
        PrimatechArena.close(how == Ending.TIME ? 60 : 1);
    }

    /** For commands: Elle goes away now, and everyone goes home. */
    public static void dismiss(MinecraftServer server) {
        for (Arrival arrival : java.util.List.copyOf(ARRIVALS.values())) dropScene(arrival);
        ARRIVALS.clear();
        if (active != null) { active.discard(); active = null; }
        PrimatechArena.close(1);
    }

    public static void forget(ServerPlayer player) {
        Arrival arrival = ARRIVALS.remove(player.getUUID());
        if (arrival != null) dropScene(arrival);
        ElleVoice.forget(player.getUUID());
    }

    /** For tests and server stops. */
    public static void clear(MinecraftServer server) {
        dismiss(server);
        CompanyOmens.settle();
        PrimatechArena.reset();
        ElleVoice.clear();
        if (server != null) for (ServerPlayer p : java.util.List.copyOf(PrimatechArena.level(server) == null ? java.util.List.<ServerPlayer>of()
                : PrimatechArena.level(server).players())) PrimatechArena.sendHome(p);
    }
}
