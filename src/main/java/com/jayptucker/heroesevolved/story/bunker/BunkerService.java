package com.jayptucker.heroesevolved.story.bunker;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.network.PowerIntroPayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.network.TimeIntroPayload;
import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.story.StoryLead;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The opening of the story: a new player wakes sealed in a Cryochamber in the Odessa stasis
 * bunker as an alarm sounds, and is released to discover what happened to the world.
 * Players who were already exploring before the bunker existed are told where it is instead.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class BunkerService {
    /** Ticks after login before the sequence starts, so the client has finished loading the world. */
    private static final int START_DELAY = 10, RELEASE = 110, END = 160;
    /** Anyone who has played this long already found their own way into the world. */
    private static final int VETERAN_PLAY_TICKS = 20 * 120;
    private static final Map<UUID, Integer> SESSIONS = new HashMap<>();
    /** Sleepers whose pod has opened and who have walked out of it. */
    private static final Set<UUID> STEPPED_OUT = new HashSet<>();
    /** Players in the bunker who are hearing the alarm, other than sleepers still in their pods. */
    private static final Set<UUID> HEARING = new HashSet<>();
    /** The red pulse at the screen's edges while the alarm sounds. */
    private static final int ALARM_RED = 0x55FF1010;
    /** True from a sleeper's alarm until the last sleeper waking with them is out of their pod. */
    private static boolean alarmed;
    /** Set when a sleeper's pod opens, cleared when they leave the bunker: their waking music plays until then. */
    public static final String MUSIC = "heroesevolved_bunker_music";
    /** Those whose client has been told to play it (and hasn't since left the Overworld or logged out). */
    private static final Set<UUID> PLAYING = new HashSet<>();

    private BunkerService() {}

    public static boolean isAwakening(ServerPlayer player) { return SESSIONS.containsKey(player.getUUID()); }

    /** Builds the bunker the first time it is needed. */
    public static BunkerData ensureBuilt(MinecraftServer server) {
        BunkerData data = BunkerData.get(server);
        if (!data.built) {
            data.origin = BunkerBuilder.build(server.overworld());
            data.built = true;
            data.design = BunkerLayout.STASIS_DESIGN;
            data.layout = BunkerData.LAYOUT;
            data.powered = false;
            data.setDirty();
            HeroesEvolved.LOGGER.info("Built the stasis bunker at {}", data.origin);
        }
        return data;
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.getData(ModDataAttachments.BUNKER_AWOKEN)
                && com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON.bunkerEnabled.get())
            SESSIONS.put(player.getUUID(), -START_DELAY);
    }

    /** Starts the awakening for {@code player} now. Returns false if they had already awoken. */
    public static boolean begin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        BunkerData data = ensureBuilt(server);
        ServerLevel overworld = server.overworld();
        // Pod 14 is Claire Bennet's, the one her father sealed her in on the Last Day: nobody else sleeps there. The ward's
        // private pod is only ever its one sleeper's (HelixWard).
        BunkerLayout layout = data.layout();
        int index = data.pod(player.getUUID(), BunkerBuilder.PODS, com.jayptucker.heroesevolved.story.night.NightThreeBunker.CLAIRE);
        if (index >= layout.pods()) {
            // A ward sleeper in a bunker without the ward: the hall instead.
            data.pods.remove(player.getUUID());
            index = data.pod(player.getUUID(), BunkerBuilder.PODS, com.jayptucker.heroesevolved.story.night.NightThreeBunker.CLAIRE);
        }
        if (index == BunkerLayout.LAB_POD) HelixWard.dose(player);
        BlockPos base = data.origin.offset(layout.podOffset(index));
        if (!overworld.getBlockState(base).is(ModBlocks.CRYOCHAMBER.get())) BunkerBuilder.placePod(overworld, data.origin, layout, index, true);
        if (player.level() != overworld) player.teleportTo(overworld, base.getX() + .5, base.getY(), base.getZ() + .5, 0, 0);
        // Whoever died in it in the hundred and three years is gone from it (if their chunk isn't loaded yet, the next
        // second's PodSleepers.present takes them, under the waking's blindness).
        PodSleepers.clear(overworld, data, index);
        CryoChamberService.enter(player, base, RELEASE + 5);
        BunkerBuilder.labelPod(overworld, data, index, player.getGameProfile().getName(), false);
        // One radio on the emergency band for every two sleepers (BunkerRadios).
        BunkerRadios.joined(overworld, data, player.getUUID());
        SESSIONS.put(player.getUUID(), 0);
        return true;
    }

    /**
     * The Last Day is over for {@code player}: they wake now, in the pod with the same number as the one they were sealed
     * in under Primatech, 103 years later.
     */
    public static void handOff(ServerPlayer player) {
        if (!com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON.bunkerEnabled.get()) return;
        begin(player);
        SESSIONS.put(player.getUUID(), 1);
        step(player, 1);
    }

    private static boolean veteran(ServerPlayer player) {
        return player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) > VETERAN_PLAY_TICKS
                || PlayerAbilityService.getData(player).assignedPower().filter(entry -> entry.getValue().isUnlocked()).isPresent();
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (UUID id : List.copyOf(SESSIONS.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) { SESSIONS.remove(id); continue; }
            int age = SESSIONS.merge(id, 1, Integer::sum);
            if (age == 0) {
                if (veteran(player)) {
                    SESSIONS.remove(id);
                    BunkerData data = ensureBuilt(server);
                    player.setData(ModDataAttachments.BUNKER_AWOKEN, true);
                    BunkerRadios.joined(server.overworld(), data, player.getUUID());
                    player.sendSystemMessage(Component.translatable("bunker.heroesevolved.rumor",
                            data.origin.getX(), data.origin.getZ(), StoryLead.journalKey()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                    continue;
                }
                // A new player lives the Last Day first; it hands them back here, 103 years later (handOff).
                if (com.jayptucker.heroesevolved.story.prologue.PrologueService.wanted(player)) {
                    SESSIONS.remove(id);
                    com.jayptucker.heroesevolved.story.prologue.PrologueService.join(player);
                    continue;
                }
                begin(player);
                SESSIONS.put(id, 1);
                age = 1;
            }
            if (age > 0) step(player, age);
        }
        BunkerData bunker = BunkerData.get(server);
        if (!bunker.built) return;
        OdessaRuins.get(server.overworld()).tickBuild(server.overworld());
        if (server.overworld().getGameTime() % 10 == 0) music(server, bunker);
        emergency(server, bunker);
        BunkerPower.tick(server.overworld(), bunker);
        // Whoever walks up to Pod 14 finds what Claire left there.
        if (server.overworld().getGameTime() % 5 == 0)
            for (ServerPlayer player : server.overworld().players()) CryoChamberService.noticeClaire(player, bunker);
        if (server.overworld().getGameTime() % 20 == 0) {
            BunkerBuilder.upgrade(server.overworld(), bunker);
            BunkerBuilder.releaseClaire(server.overworld(), bunker);
            // Radios owed to sleepers counted while the chest wasn't loaded (or before there were radios at all).
            BunkerRadios.stock(server.overworld(), bunker);
            // About half the pods nobody woke in still hold Odessa's people, frosted over; the rest failed.
            PodSleepers.present(server.overworld(), bunker);
            // And what is left of the others lies about the rooms, each room strewn once.
            BunkerBones.strew(server.overworld(), bunker);
            // And the ones the vault was shut on lie outside it and on the stair: laid once, in new bunkers and old.
            if (!bunker.remains && BunkerStair.loaded(server.overworld(), bunker.origin)) {
                BunkerStair.remains(server.overworld(), bunker.origin);
                bunker.remains = true;
                bunker.setDirty();
            }
            // Nobody waking or about to (a server restarted mid-waking, or a veteran only told where the
            // bunker is): the power comes back.
            if (!bunker.powered && !BunkerPower.switching() && !alarmed && SESSIONS.keySet().stream().allMatch(STEPPED_OUT::contains))
                BunkerPower.start(server.overworld(), bunker);
            // Every sleeper's name over their pod, including those who woke before the nameplates did this.
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Integer pod = bunker.pods.get(player.getUUID());
                if (pod != null && pod != com.jayptucker.heroesevolved.story.night.NightThreeBunker.CLAIRE) BunkerBuilder.labelPod(server.overworld(), bunker, pod, player.getGameProfile().getName(),
                        !SESSIONS.containsKey(player.getUUID()) || STEPPED_OUT.contains(player.getUUID()));
            }
        }
    }

    private static void step(ServerPlayer player, int age) {
        if (age == 1) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 70, 0, false, false));
            ScreenTintPayload.send(player, 0xFF000000, 50, ScreenTintPayload.Style.HOLD);
            caption(player, "bunker_1");
        }
        if (age == 12) {
            // Every waking starts the same way: the main power fails, the bunker drops to emergency power
            // with only its beacons turning, and the alarm sounds for everyone inside (emergency()). The
            // sleeper hears it from the pod (BunkerAlarm), timed to open with the lid at RELEASE, until
            // they step out.
            BunkerPower.cut(player.getServer().overworld(), BunkerData.get(player.getServer()));
            cue(player, TimeIntroPayload.BUNKER_ALARM);
        }
        boolean out = STEPPED_OUT.contains(player.getUUID());
        // The red pulse lasts as long as the alarm. Re-sent every second, so it also ends by itself if the session does.
        if (age >= 12 && !out && age % 20 == 12) ScreenTintPayload.send(player, ALARM_RED, 40, ScreenTintPayload.Style.PULSE);
        if (age == 60) caption(player, "bunker_2");
        if (age == RELEASE) {
            CryoChamberService.release(player);
            player.setData(ModDataAttachments.BUNKER_AWOKEN, true);
            caption(player, "bunker_3");
            award(player);
            boolean ward = HelixWard.sleeper(player.getServer(), player);
            if (ward) {
                // Alone in the Helix Ward: told only that something is different, as the Eclipse's chosen are.
                caption(player, "bunker_ward");
                player.sendSystemMessage(Component.translatable("bunker.heroesevolved.ward").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
            }
        }
        if (age > RELEASE && !out && outOfPod(player)) {
            stepOut(player);
            out = true;
        }
        if (age >= END && out) {
            SESSIONS.remove(player.getUUID());
            STEPPED_OUT.remove(player.getUUID());
        }
    }

    /** Whether the sleeper has left the pod they woke in (or the overworld). */
    private static boolean outOfPod(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        BunkerData data = BunkerData.get(server);
        Integer index = data.pods.get(player.getUUID());
        if (index == null || player.level() != server.overworld()) return true;
        return !CryoChamberService.isInside(player) && !player.blockPosition().equals(data.origin.offset(data.layout().podOffset(index)));
    }

    /**
     * Out of the pod: the red light leaves the screen and the nameplate says revived. The alarm the sleeper
     * hears goes on with everyone else's until the last sleeper waking with them is out (emergency()).
     */
    private static void stepOut(ServerPlayer player) {
        STEPPED_OUT.add(player.getUUID());
        // The waking's music, until they find their way out (music()); it begins once the alarm is over.
        player.getPersistentData().putBoolean(MUSIC, true);
        HEARING.add(player.getUUID());
        ScreenTintPayload.send(player, ALARM_RED, 0, ScreenTintPayload.Style.PULSE);
        BunkerData data = BunkerData.get(player.getServer());
        Integer pod = data.pods.get(player.getUUID());
        if (pod != null) BunkerBuilder.labelPod(player.getServer().overworld(), data, pod, player.getGameProfile().getName(), true);
    }

    /**
     * The alarm is the bunker's, not just the sleeper's: while anyone is waking, everyone inside hears it, and
     * whoever leaves stops hearing it. When the last sleeper is out of their pod (or gone), it stops for all
     * and the main breaker closes: the lights come back on (BunkerPower) and everyone inside hears it happen.
     */
    private static void emergency(MinecraftServer server, BunkerData data) {
        boolean waking = SESSIONS.entrySet().stream().anyMatch(e -> e.getValue() >= 12 && !STEPPED_OUT.contains(e.getKey()));
        if (waking) {
            alarmed = true;
            for (ServerPlayer listener : server.overworld().players()) {
                UUID id = listener.getUUID();
                boolean inPod = SESSIONS.containsKey(id) && !STEPPED_OUT.contains(id);
                if (inPod || HEARING.contains(id) || !BunkerBuilder.inside(data, listener.blockPosition())) continue;
                HEARING.add(id);
                cue(listener, TimeIntroPayload.BUNKER_HALL_ALARM);
            }
        }
        for (UUID id : List.copyOf(HEARING)) {
            ServerPlayer listener = server.getPlayerList().getPlayer(id);
            if (listener != null && listener.level() == server.overworld() && BunkerBuilder.inside(data, listener.blockPosition())) continue;
            HEARING.remove(id);
            if (listener != null) cue(listener, TimeIntroPayload.BUNKER_QUIET);
        }
        if (alarmed && !waking) {
            alarmed = false;
            boolean power = BunkerPower.start(server.overworld(), data);
            for (UUID id : HEARING) {
                ServerPlayer listener = server.getPlayerList().getPlayer(id);
                if (listener != null) cue(listener, power ? TimeIntroPayload.BUNKER_POWER : TimeIntroPayload.BUNKER_QUIET);
            }
            HEARING.clear();
        }
    }

    /**
     * The waking's music: it plays for each woken sleeper, from the end of the alarm, while they are anywhere in the bunker
     * (its rooms, the stairwell and the ruined lobby at its top); the moment they step out of all that into the world it
     * fades away and they earn "What happened...?". Started again after a relog or a trip out of the Overworld if they are still inside.
     */
    private static void music(MinecraftServer server, BunkerData data) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) music(player, data);
    }

    /** One player's waking music: start it inside the bunker once the alarm is over, fade it (and award the advancement) once they are out. */
    public static void music(ServerPlayer player, BunkerData data) {
        MinecraftServer server = player.getServer();
        if (!player.getPersistentData().getBoolean(MUSIC)) return;
        UUID id = player.getUUID();
        if (player.level() != server.overworld()) { PLAYING.remove(id); return; }
        if (withinBunker(data, player.blockPosition())) {
            // Not over the alarm: it starts when the alarm stops and the lights come back.
            if (!alarmed && PLAYING.add(id)) cue(player, TimeIntroPayload.BUNKER_MUSIC);
            return;
        }
        player.getPersistentData().remove(MUSIC);
        PLAYING.remove(id);
        cue(player, TimeIntroPayload.BUNKER_EXIT);
        AdvancementHolder advancement = server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "what_happened"));
        if (advancement != null) player.getAdvancements().award(advancement, "granted");
    }

    /** The bunker's rooms with a little rock round them, and the column of the stairwell and Primatech's ruin over the vault. */
    public static boolean withinBunker(BunkerData data, BlockPos pos) {
        BlockPos at = pos.subtract(data.origin);
        if (data.layout().bounds().inflatedBy(3).isInside(at)) return true;
        return at.getX() >= PrimatechRuin.LOT_X0 - 3 && at.getX() <= PrimatechRuin.LOT_X1 + 3 && at.getZ() >= PrimatechRuin.LOT_Z0 - 3
                && at.getZ() <= PrimatechRuin.LOT_Z1 + 3 && at.getY() >= -3 && at.getY() <= 120;
    }

    /** For tests: whether the waking's music is still due to play for {@code player}. */
    public static boolean musicPlaying(ServerPlayer player) { return player.getPersistentData().getBoolean(MUSIC); }

    private static void cue(ServerPlayer player, int stage) {
        PacketDistributor.sendToPlayer(player, new TimeIntroPayload(stage));
    }

    private static void caption(ServerPlayer player, String stage) {
        PacketDistributor.sendToPlayer(player, new PowerIntroPayload(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, stage), 0));
    }

    private static void award(ServerPlayer player) {
        AdvancementHolder advancement = player.getServer().getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "wake_up"));
        if (advancement != null) player.getAdvancements().award(advancement, "granted");
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
        STEPPED_OUT.remove(event.getEntity().getUUID());
        HEARING.remove(event.getEntity().getUUID());
        PLAYING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        SESSIONS.clear();
        STEPPED_OUT.clear();
        HEARING.clear();
        PLAYING.clear();
        alarmed = false;
        BunkerPower.reset();
    }
}
