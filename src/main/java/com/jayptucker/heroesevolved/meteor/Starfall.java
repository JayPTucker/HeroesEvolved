package com.jayptucker.heroesevolved.meteor;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.data.EclipseCalendar;
import com.jayptucker.heroesevolved.network.StarfallPayload;
import com.jayptucker.heroesevolved.village.VillageIncidents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Starfall night: the night before every Eclipse day (the new moon), from dusk to dawn. While it lasts the sky is full
 * of streaks for everyone in the Overworld, a falling star comes down near each player about every
 * {@link HeroesEvolvedConfig.Common#meteorSeconds} seconds ({@link Meteors#drop}), and on some nights
 * ({@link HeroesEvolvedConfig.Common#meteorGreatChance}) a great meteor comes down on a village near a player.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Starfall {
    /** The night runs from dusk to dawn: these times of day. */
    public static final long DUSK = 13_000, DAWN = 23_000;
    /** A great meteor comes this long into the night, at the earliest, and this much later at the latest. */
    static final int GREAT_EARLIEST = 1_200, GREAT_SPREAD = 3_000;

    private static boolean forced, on;
    private static long greatAt = -1;
    private static final Map<UUID, Long> NEXT = new HashMap<>();

    private Starfall() {}

    /** Whether {@code dayTime} falls in a starfall night: dusk to dawn on the day before an Eclipse day. */
    public static boolean night(long dayTime) {
        long day = EclipseCalendar.day(dayTime), time = EclipseCalendar.timeOfDay(dayTime);
        return time >= DUSK && time < DAWN && EclipseCalendar.isEclipseDay(day + 1);
    }

    /** Whether the shower is falling now (its night, or an admin's). */
    public static boolean active(MinecraftServer server) {
        return forced || HeroesEvolvedConfig.COMMON.meteorShowers.get() && night(server.overworld().getDayTime());
    }

    /** Starts or stops a starfall now, whatever the night (admins, previews). */
    public static void force(MinecraftServer server, boolean start) {
        forced = start;
        update(server);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        if (now % 20 != 0) return;
        update(server);
        if (!on) return;
        if (greatAt >= 0 && now >= greatAt) {
            greatAt = -1;
            if (!VillageIncidents.request(server, VillageIncidents.Kind.METEOR))
                HeroesEvolved.LOGGER.info("Starfall: no village near enough for a great meteor");
        }
        int mean = HeroesEvolvedConfig.COMMON.meteorSeconds.get() * 20;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            long next = NEXT.computeIfAbsent(p.getUUID(), id -> now + (long) (mean * (0.3 + level.random.nextDouble() * 0.7)));
            if (now < next) continue;
            NEXT.put(p.getUUID(), now + (long) (mean * (0.5 + level.random.nextDouble())));
            near(p);
        }
    }

    /** Notices the night beginning or ending: tells the Overworld, and rolls for a great meteor. */
    private static void update(MinecraftServer server) {
        boolean now = active(server);
        if (now == on) return;
        on = now;
        ServerLevel level = server.overworld();
        NEXT.clear();
        greatAt = on && level.random.nextDouble() < HeroesEvolvedConfig.COMMON.meteorGreatChance.get()
                ? level.getGameTime() + GREAT_EARLIEST + level.random.nextInt(GREAT_SPREAD) : -1;
        for (ServerPlayer p : level.players()) {
            PacketDistributor.sendToPlayer(p, new StarfallPayload(on));
            if (on) p.sendSystemMessage(Component.literal("Stars are falling tonight.").withStyle(ChatFormatting.AQUA));
        }
    }

    /**
     * A falling star near {@code p}: between {@link MeteorGeometry#NEAREST} and {@link MeteorGeometry#FURTHEST} blocks off,
     * on dry ground. Null if no dry ground was found.
     */
    public static Meteor near(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        for (int tries = 0; tries < 8; tries++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double d = MeteorGeometry.NEAREST + level.random.nextDouble() * (MeteorGeometry.FURTHEST - MeteorGeometry.NEAREST);
            int x = (int) Math.floor(p.getX() + Math.cos(a) * d), z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
            BlockPos column = new BlockPos(x, 0, z);
            if (!level.hasChunkAt(column)) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (!level.getFluidState(top.below()).isEmpty() || !level.getFluidState(top).isEmpty()) continue;
            return Meteors.drop(level, Vec3.atBottomCenterOf(top));
        }
        return null;
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) PacketDistributor.sendToPlayer(p, new StarfallPayload(on && p.level() == p.getServer().overworld()));
    }

    @SubscribeEvent
    public static void changed(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) PacketDistributor.sendToPlayer(p, new StarfallPayload(on && p.level() == p.getServer().overworld()));
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) {
        forced = false;
        on = false;
        greatAt = -1;
        NEXT.clear();
    }

    /** One line for admins. */
    public static String describe(MinecraftServer server) {
        return (on ? "Stars are falling" + (forced ? " (started by hand)" : "") : "No starfall now") + ". " + Meteors.describe();
    }
}
