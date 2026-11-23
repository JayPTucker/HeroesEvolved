package com.jayptucker.heroesevolved.story.sylar;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.gifted.GiftNames;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.mimicry.GiftedVillagers;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.story.bunker.BunkerData;
import com.jayptucker.heroesevolved.village.VillageIncidents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.*;

/**
 * How you meet Sylar: the prisoner, and the list.
 *
 * <p><b>He wakes.</b> When anyone's power is first worth taking (an awakened power at
 * {@code sylarTicketPowerLevel}), something under the bunker starts to tick: a hatch is set in the floor of Gray's old
 * cell in containment ({@link LevelFive#placeHatch}), and below it is Level 5, where the Company has kept him for a
 * hundred and three years. He is behind glass, polite, and helpful: he tells each visitor something true about their
 * power, and asks them, in time, to pull the release lever.</p>
 *
 * <p><b>The list.</b> On the wall by his cell, the Company's watch list of evolved persons: nine old names crossed out in
 * faded ink (whose powers stand in his cases), then {@link #LIVING} new ones in a careful hand (gifted villagers in real
 * villages, found when someone comes near), then the name of every player whose power is worth taking. Each night one
 * living name is crossed out and its villager is emptied of their gift, unless someone stands guard nearby. The prisoner
 * knows the names. Nobody told him.</p>
 *
 * <p><b>His name is next.</b> The night after the last living name goes, his cell stands open, and he comes for a listed
 * player: behind them, wherever they are, or at once if they go down to Level 5. A few words, and his hand closes on
 * their wrist: the Collection ({@link CollectionArena#open(ServerPlayer, boolean)}). Pulling the lever (twice: it is
 * stiff) lets him out at once, and spares whoever is still on the list. Beaten, the people he emptied get their gifts
 * back. From then on a Repair Ticket brings the fight back.</p>
 *
 * <p>Server-wide: one prisoner, one list. Its state is {@link SylarData}'s; the place is {@link LevelFive}.</p>
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class SylarStory {
    /** How many living gifted people go on the list when he wakes. */
    public static final int LIVING = 3;
    /** How far from the bunker (or spawn) the list's villages are looked for. */
    public static final int VILLAGE_RANGE = 640;
    /** A player this near a listed villager when the night comes keeps them off his round that night. */
    public static final double GUARD = 24;
    /** How near a listed village a player must come for its listed villager to be found. */
    public static final double FIND = 48;
    /** How near a listed villager a player must stand to meet them. */
    public static final double MEET = 6;
    /** How near the glass a visitor must stand for him to talk, and the list to be read. */
    public static final double TALK = 7, READ = 3.5;
    /** The gap between his lines through the glass. */
    public static final int TALK_GAP = 140;
    /** From his first word behind you to the fold into his hall. */
    public static final int REVEAL_TICKS = 70;
    /** How long after stepping out of the lift he comes for you, once his cell is open. */
    public static final int CELL_EMPTY_TICKS = 80;
    /** How long a first pull of the stiff release lever waits for the second. */
    public static final int LEVER_WINDOW = 200;
    /** How long the story waits, after telling the list's players his cell is open, before he comes. */
    public static final int OUT_DELAY = 200;
    public static final int CHECK = 100;
    public static final String LISTED_TAG = HeroesEvolved.MOD_ID + "_sylar_listed", EMPTIED_TAG = HeroesEvolved.MOD_ID + "_sylar_emptied";

    /** The old names on the list, crossed out a century ago if their power stands in his case at {@code hour}. */
    record Old(String name, int hour) {}
    static final List<Old> OLD = List.of(new Old("R. Calder", 1), new Old("J. Okafor", 2), new Old("E. Marsh", 4),
            new Old("D. Lindqvist", 5), new Old("H. Pell", 8), new Old("A. Reyes", 9), new Old("S. Moreau", 10),
            new Old("T. Sprague", 6), new Old("C. Bennet", 0));

    private static final String[] FIRST = {"Hollis", "Marta", "Teodor", "Ines", "Callum", "Priya", "Wendell", "Agnes",
            "Rafe", "Otto", "Lucia", "Bram", "Nadia", "Emory", "Greer", "Silas", "Maude", "Yusuf", "Odile", "Corin"};
    private static final String[] LAST = {"Ashby", "Vance", "Dunmore", "Okoro", "Brisco", "Lindell", "Moss", "Quint",
            "Rourke", "Tallis", "Ware", "Penrose", "Hale", "Strand", "Keel", "Ferris", "Doyle", "Marlow"};

    private record Line(String key, boolean speech, Object[] args) {}
    private static final class Talk { final Deque<Line> lines = new ArrayDeque<>(); long next; }
    private static final class Reveal {
        final ServerPlayer player; SylarEntity actor; int ticks; final boolean released;
        Reveal(ServerPlayer player, SylarEntity actor, int ticks, boolean released) { this.player = player; this.actor = actor; this.ticks = ticks; this.released = released; }
    }

    private static final Map<UUID, Talk> TALKS = new HashMap<>();
    /** Who he has spoken to this visit, who has been in Level 5 since when, and who he has already come for this visit. */
    private static final Set<UUID> SPOKEN = new HashSet<>(), CAME = new HashSet<>();
    private static final Map<UUID, Long> ARRIVED = new HashMap<>(), LEVER = new HashMap<>();
    /** Who sat up with a listed villager last night, and which living villagers each player has met (not saved). */
    private static final Set<UUID> GUARDS = new HashSet<>();
    private static final Set<String> MET = new HashSet<>();
    private static final List<Reveal> REVEALS = new ArrayList<>();

    private SylarStory() {}

    // ------------------------------------------------------------------
    // Time: nights, counted the same with the daylight cycle on or off
    // ------------------------------------------------------------------

    /** The overworld's clock: its time of day, or plain game time when the daylight cycle is off. */
    public static long clock(ServerLevel overworld) {
        return overworld.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT) ? overworld.getDayTime() : overworld.getGameTime();
    }

    /** Which night {@code clock} falls in or after: it turns over at dusk (13000), so a night slept through still counts. */
    public static long nightOf(long clock) { return Math.floorDiv(clock - 13000, 24000); }

    // ------------------------------------------------------------------
    // Waking, and who goes on the list
    // ------------------------------------------------------------------

    /** Whether this player's power is worth his attention. */
    public static boolean qualifies(ServerPlayer player) {
        if (!HeroesEvolvedConfig.COMMON.sylarEnabled.get() || player.isSpectator() || !player.isAlive()) return false;
        if (player.level().dimension() != Level.OVERWORLD || SylarMoves.power(player) == null) return false;
        return PlayerProgressionService.getPowerLevel(player) >= HeroesEvolvedConfig.COMMON.sylarTicketPowerLevel.get();
    }

    /** Wakes him, or adds this player to his list, if their power is worth it. Returns true if anything changed. */
    public static boolean check(ServerPlayer player) {
        if (!qualifies(player)) return false;
        SylarData data = SylarData.get(player.getServer());
        if (data.stage() == SylarData.DORMANT) { wake(player); return true; }
        if (data.stage() <= SylarData.OUT && !data.listed().containsKey(player.getUUID())) { enlist(player); return true; }
        return false;
    }

    /** He wakes, for this player: the hatch, the list, and the first lines. (Admins and tests call it directly.) */
    public static void wake(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        SylarData data = SylarData.get(server);
        if (data.stage() != SylarData.DORMANT) { enlist(player); return; }
        ServerLevel overworld = server.overworld();
        data.stage(SylarData.AWAKE);
        long night = nightOf(clock(overworld));
        data.awokeNight(night);
        data.lastNight(night);
        if (data.list().isEmpty()) fillList(server, player.blockPosition());
        LevelFive.placeHatch(server);
        enlist(player);
        LevelFive.refresh(server);
    }

    private static void enlist(ServerPlayer player) {
        SylarData data = SylarData.get(player.getServer());
        if (!data.list(player.getUUID(), player.getGameProfile().getName())) return;
        boolean bunker = BunkerData.get(player.getServer()).built && data.hatch() != null
                && data.hatch().equals(BunkerData.get(player.getServer()).origin.offset(LevelFive.CELL_THREE_HATCH));
        SylarVoice.narrate(player, "story.heroesevolved.sylar.narrate.wake");
        BlockPos hatch = data.hatch();
        if (bunker) SylarVoice.narrate(player, "story.heroesevolved.sylar.narrate.hatch");
        else if (hatch != null) SylarVoice.narrate(player, "story.heroesevolved.sylar.narrate.hatch_spawn", hatch.getX(), hatch.getY(), hatch.getZ());
        // Listed after his cell is already open: there is nothing above their name to wait for.
        if (data.stage() == SylarData.OUT) SylarVoice.narrate(player, "story.heroesevolved.sylar.narrate.out");
        LevelFive.signs(player.getServer());
    }

    /** The list's living names: gifted people in the nearest villages (or nowhere, in a world without villages). */
    static void fillList(MinecraftServer server, BlockPos near) {
        SylarData data = SylarData.get(server);
        ServerLevel overworld = server.overworld();
        RandomSource random = overworld.getRandom();
        BunkerData bunker = BunkerData.get(server);
        BlockPos from = bunker.built ? bunker.origin : near;
        List<BlockPos> villages = new ArrayList<>();
        for (int attempt = 0; attempt < 8 && villages.size() < LIVING; attempt++) {
            BlockPos found = VillageIncidents.find(overworld, from, VILLAGE_RANGE, random);
            if (found == null) break;
            if (villages.stream().noneMatch(v -> v.distSqr(found) < 64 * 64)) villages.add(found);
        }
        Set<ResourceLocation> powers = new HashSet<>();
        Set<String> firsts = new HashSet<>(), lasts = new HashSet<>();
        for (int i = 0; i < LIVING; i++) {
            ResourceLocation power = GiftedMobs.randomPower(random);
            for (int t = 0; t < 10 && powers.contains(power); t++) power = GiftedMobs.randomPower(random);
            powers.add(power);
            String first, last;
            do { first = FIRST[random.nextInt(FIRST.length)]; last = LAST[random.nextInt(LAST.length)]; }
            while (first.length() + last.length() > 13 || firsts.contains(first) || lasts.contains(last));
            firsts.add(first); lasts.add(last);
            String name = first + " " + last;
            data.add(new SylarData.Entry(name, power, villages.isEmpty() ? null : villages.get(i % villages.size())));
        }
    }

    // ------------------------------------------------------------------
    // The story's clock
    // ------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        tickReveals(server);
        long now = server.overworld().getGameTime();
        SylarData data = SylarData.get(server);
        if (now % 10 == 0) tickLevelFive(server, data, now);
        if (now % 20 == 0 && data.stage() >= SylarData.AWAKE) meet(server, data);
        if (now % CHECK != 0) return;
        LevelFive.tick(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) check(player);
        if (data.stage() == SylarData.DORMANT || data.stage() == SylarData.BEATEN) return;
        if (data.hatch() != null && server.overworld().isLoaded(data.hatch())
                && !LevelFive.isHatch(server.overworld().getBlockState(data.hatch()))) LevelFive.placeHatch(server);
        assign(server, data);
        night(server, data);
    }

    /** What the night brings: a name crossed out, or, with none left, his cell open; once it's open, him. */
    static void night(MinecraftServer server, SylarData data) {
        long night = nightOf(clock(server.overworld()));
        if (night <= data.lastNight()) return;
        if (data.stage() == SylarData.AWAKE) {
            if (data.list().stream().anyMatch(e -> !e.crossed)) {
                cross(server);
                data.lastNight(night);
                return;
            }
            out(server);
        }
        if (data.stage() == SylarData.OUT && REVEALS.isEmpty() && !CollectionArena.open()) {
            ServerPlayer target = null;
            for (ServerPlayer p : server.overworld().players())
                if (data.listed().containsKey(p.getUUID()) && SylarEntity.counts(p) && !p.isSleeping() && (target == null || p.getRandom().nextBoolean()))
                    target = p;
            if (target != null) { comeFor(target, OUT_DELAY); data.lastNight(night); }
        }
    }

    /**
     * Tonight's name: the first living one nobody is guarding. A player near a listed villager when the night turns keeps
     * them safe that night (and hears something pass). Returns the name crossed out, or null.
     */
    public static SylarData.Entry cross(MinecraftServer server) {
        SylarData data = SylarData.get(server);
        ServerLevel overworld = server.overworld();
        GUARDS.clear();
        SylarData.Entry crossed = null;
        for (SylarData.Entry entry : data.list()) {
            if (entry.crossed) continue;
            List<ServerPlayer> guards = guards(overworld, entry);
            if (!guards.isEmpty()) {
                for (ServerPlayer guard : guards) {
                    GUARDS.add(guard.getUUID());
                    SylarVoice.narrateNow(guard, "story.heroesevolved.sylar.narrate.guarded", entry.name);
                }
                continue;
            }
            crossed = entry;
            break;
        }
        if (crossed == null) return null;
        crossed.crossed = true;
        crossed.night = nightOf(clock(overworld));
        data.changed();
        if (crossed.villager != null && overworld.getEntity(crossed.villager) instanceof Villager villager) empty(villager, crossed);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (data.listed().containsKey(p.getUUID())) SylarVoice.narrateNow(p, "story.heroesevolved.sylar.narrate.crossed");
        LevelFive.signs(server);
        return crossed;
    }

    private static List<ServerPlayer> guards(ServerLevel overworld, SylarData.Entry entry) {
        Entity villager = entry.villager == null ? null : overworld.getEntity(entry.villager);
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : overworld.players()) {
            if (!SylarEntity.counts(p)) continue;
            if (villager != null ? p.distanceToSqr(villager) <= GUARD * GUARD
                    : entry.village != null && horizontal(p.position(), entry.village) <= FIND) out.add(p);
        }
        return out;
    }

    private static double horizontal(Vec3 at, BlockPos pos) {
        double dx = at.x - (pos.getX() + .5), dz = at.z - (pos.getZ() + .5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Every living name is gone: his cell stands open, and the list's players are told. */
    public static void out(MinecraftServer server) {
        SylarData data = SylarData.get(server);
        if (data.stage() >= SylarData.OUT) return;
        data.stage(SylarData.OUT);
        ServerLevel five = LevelFive.level(server);
        if (five != null && data.levelFiveBuilt() >= LevelFive.VERSION) LevelFive.glass(five, false, !five.players().isEmpty());
        LevelFive.refresh(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (data.listed().containsKey(p.getUUID()) && !LevelFive.in(p)) SylarVoice.narrateNow(p, "story.heroesevolved.sylar.narrate.out");
    }

    /** He is beaten: the story is over, and the people he emptied get their gifts back. */
    public static void beaten(MinecraftServer server) {
        SylarData data = SylarData.get(server);
        if (data.stage() == SylarData.BEATEN) return;
        boolean wasStory = data.stage() != SylarData.DORMANT;
        data.stage(SylarData.BEATEN);
        ServerLevel overworld = server.overworld();
        boolean anyone = false;
        for (SylarData.Entry entry : data.list()) {
            if (!entry.crossed) continue;
            anyone = true;
            if (entry.villager != null && overworld.getEntity(entry.villager) instanceof Villager villager) restore(villager, entry);
        }
        REVEALS.forEach(r -> { if (r.actor != null) r.actor.discard(); });
        REVEALS.clear();
        LevelFive.refresh(server);
        if (wasStory && anyone)
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                if (data.listed().containsKey(p.getUUID())) SylarVoice.narrate(p, "story.heroesevolved.sylar.narrate.restored");
    }

    // ------------------------------------------------------------------
    // The list's villagers
    // ------------------------------------------------------------------

    /** A listed village with nobody yet named for it: when a player comes near, a villager there is found to carry the name. */
    static void assign(MinecraftServer server, SylarData data) {
        ServerLevel overworld = server.overworld();
        for (SylarData.Entry entry : data.list()) {
            if (entry.villager != null || entry.village == null) continue;
            boolean near = overworld.players().stream().anyMatch(p -> horizontal(p.position(), entry.village) <= FIND);
            if (!near) continue;
            Vec3 middle = Vec3.atBottomCenterOf(entry.village);
            Villager best = null;
            for (Villager v : overworld.getEntitiesOfClass(Villager.class, new AABB(middle, middle).inflate(80, 64, 80),
                    v -> v.isAlive() && !v.isBaby() && !v.hasCustomName() && !GiftedMobs.isGifted(v) && !v.getTags().contains(LISTED_TAG)
                            && !com.jayptucker.heroesevolved.village.Villagers.inStasis(v))) {
                if (best == null) { best = v; continue; }
                boolean gifted = GiftedVillagers.gift(v) != null, bestGifted = GiftedVillagers.gift(best) != null;
                if (gifted != bestGifted ? gifted : v.distanceToSqr(middle) < best.distanceToSqr(middle)) best = v;
            }
            if (best == null) continue;
            name(best, entry);
        }
    }

    /** {@code villager} carries this name now. */
    public static void name(Villager villager, SylarData.Entry entry) {
        entry.villager = villager.getUUID();
        villager.addTag(LISTED_TAG);
        villager.setPersistenceRequired();
        SylarData data = SylarData.get(villager.getServer());
        if (entry.crossed && data.stage() != SylarData.BEATEN) empty(villager, entry);
        else restore(villager, entry);
        data.changed();
    }

    /** The gift he took: gone, and the villager sits with a name in grey. */
    static void empty(Villager villager, SylarData.Entry entry) {
        if (GiftedMobs.isGifted(villager)) GiftedMobs.cleanse(villager);
        GiftedVillagers.give(villager, null);
        villager.setCustomName(Component.literal(entry.name).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        villager.addTag(EMPTIED_TAG);
        entry.emptied = true;
        SylarData.get(villager.getServer()).changed();
    }

    static void restore(Villager villager, SylarData.Entry entry) {
        if (!GiftedMobs.isGifted(villager)) GiftedVillagers.give(villager, entry.power);
        villager.setCustomName(Component.literal(entry.name));
        villager.removeTag(EMPTIED_TAG);
        entry.emptied = false;
        SylarData.get(villager.getServer()).changed();
    }

    /** A listed villager comes back into the world: they are as the list says (emptied, or given back). */
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Villager villager) || !villager.getTags().contains(LISTED_TAG)) return;
        SylarData data = SylarData.get(villager.getServer());
        for (SylarData.Entry entry : data.list()) {
            if (!villager.getUUID().equals(entry.villager)) continue;
            boolean empty = entry.crossed && data.stage() != SylarData.BEATEN;
            if (empty && !villager.getTags().contains(EMPTIED_TAG)) empty(villager, entry);
            else if (!empty && villager.getTags().contains(EMPTIED_TAG)) restore(villager, entry);
        }
    }

    /** Meeting a listed villager: the emptied tell you about the man who came to fix their clock. */
    static void meet(MinecraftServer server, SylarData data) {
        ServerLevel overworld = server.overworld();
        for (SylarData.Entry entry : data.list()) {
            if (entry.villager == null || !(overworld.getEntity(entry.villager) instanceof Villager villager)) continue;
            for (ServerPlayer p : overworld.players()) {
                if (p.isSpectator() || p.distanceToSqr(villager) > MEET * MEET) continue;
                if (entry.emptied) {
                    if (entry.heard.add(p.getUUID())) {
                        data.changed();
                        SylarVoice.narrate(p, "story.heroesevolved.sylar.narrate.emptied", entry.name);
                    }
                } else if (!entry.crossed && data.visits(p.getUUID()) > 0 && MET.add(p.getUUID() + entry.name)) {
                    SylarVoice.narrate(p, "story.heroesevolved.sylar.narrate.listed", entry.name, power(entry));
                }
            }
        }
    }

    static String power(SylarData.Entry entry) { return GiftNames.powerName(entry.power); }

    // ------------------------------------------------------------------
    // Level 5: the visit, his talk, the list on the wall, the lever
    // ------------------------------------------------------------------

    /** Someone came down the lift. */
    static void descended(ServerPlayer player) {
        SylarData data = SylarData.get(player.getServer());
        UUID id = player.getUUID();
        SPOKEN.remove(id); CAME.remove(id); TALKS.remove(id);
        ARRIVED.put(id, player.getServer().overworld().getGameTime());
        if (data.stage() == SylarData.AWAKE) {
            data.visited(id);
            LevelFive.prisoner(player.serverLevel());
        }
        SylarVoice.narrateNow(player, switch (data.stage()) {
            case SylarData.AWAKE -> data.visits(id) == 1 ? "story.heroesevolved.sylar.narrate.level5_first" : "story.heroesevolved.sylar.narrate.level5";
            case SylarData.OUT -> "story.heroesevolved.sylar.narrate.level5_open";
            default -> "story.heroesevolved.sylar.narrate.level5_empty";
        });
    }

    /** Someone took the lift up. */
    static void left(ServerPlayer player) {
        UUID id = player.getUUID();
        SPOKEN.remove(id); CAME.remove(id); TALKS.remove(id); ARRIVED.remove(id); LEVER.remove(id);
    }

    private static void tickLevelFive(MinecraftServer server, SylarData data, long now) {
        ServerLevel five = LevelFive.level(server);
        if (five == null || five.players().isEmpty()) return;
        if (data.stage() == SylarData.AWAKE) LevelFive.prisoner(five);
        Vec3 glass = LevelFive.glassMiddle(), list = LevelFive.listMiddle();
        for (ServerPlayer player : List.copyOf(five.players())) {
            if (player.isSpectator()) continue;
            UUID id = player.getUUID();
            ARRIVED.putIfAbsent(id, now);
            if (player.position().distanceTo(list) <= READ) read(player, data);
            if (data.stage() == SylarData.AWAKE) {
                double d = player.getEyePosition().distanceTo(glass);
                if (d <= TALK && SPOKEN.add(id)) TALKS.put(id, talk(player, data));
                Talk talk = TALKS.get(id);
                if (talk != null && d <= TALK + 4 && now >= talk.next && !talk.lines.isEmpty()) {
                    Line line = talk.lines.removeFirst();
                    if (line.speech) SylarVoice.sayNow(player, line.key, line.args);
                    else SylarVoice.narrateNow(player, line.key, line.args);
                    talk.next = now + TALK_GAP;
                }
            } else if (data.stage() == SylarData.OUT && SylarEntity.counts(player) && now - ARRIVED.get(id) >= CELL_EMPTY_TICKS
                    && REVEALS.isEmpty() && !CollectionArena.open() && CAME.add(id)) {
                comeFor(player, 0);
            }
        }
    }

    /** What he says to this visitor through the glass, in order. */
    private static Talk talk(ServerPlayer player, SylarData data) {
        Talk talk = new Talk();
        Deque<Line> out = talk.lines;
        UUID id = player.getUUID();
        int visit = Math.max(1, data.visits(id));
        ResourceLocation power = SylarMoves.power(player);
        if (visit == 1) {
            out.add(say("story.heroesevolved.sylar.prisoner.hello"));
            out.add(say("story.heroesevolved.sylar.prisoner.name"));
            out.add(say(power == null ? "story.heroesevolved.sylar.prisoner.hint.none" : "story.heroesevolved.sylar.prisoner.hint." + power.getPath()));
        } else {
            out.add(say("story.heroesevolved.sylar.prisoner.again." + (visit % 3)));
        }
        boolean toldBefore = data.list().stream().anyMatch(e -> e.told.contains(id));
        for (SylarData.Entry entry : data.list()) {
            if (!entry.crossed || !entry.told.add(id)) continue;
            data.changed();
            out.add(say("story.heroesevolved.sylar.prisoner.victim", entry.name, power(entry)));
            out.add(new Line(toldBefore ? "story.heroesevolved.sylar.narrate.knows_again" : "story.heroesevolved.sylar.narrate.knows", false, new Object[0]));
            toldBefore = true;
        }
        if (GUARDS.remove(id)) out.add(say("story.heroesevolved.sylar.prisoner.guarded"));
        boolean listed = data.listed().containsKey(id);
        if (listed && data.list().stream().allMatch(e -> e.crossed)) out.add(say("story.heroesevolved.sylar.prisoner.next"));
        else if (visit == 2) out.add(say("story.heroesevolved.sylar.prisoner.lever"));
        else if (visit >= 3) out.add(say("story.heroesevolved.sylar.prisoner.lever_again"));
        return talk;
    }

    private static Line say(String key, Object... args) { return new Line(key, true, args); }

    /** Reading the watch list: the old names, the new ones in his hand, and (if it is there) yours. */
    private static void read(ServerPlayer player, SylarData data) {
        UUID id = player.getUUID();
        int seen = data.heardCrossed(id);
        int crossed = (int) data.crossedCount();
        if (seen == 0) {
            SylarVoice.narrateNow(player, "story.heroesevolved.sylar.narrate.list");
            if (data.listed().containsKey(id)) SylarVoice.narrate(player, "story.heroesevolved.sylar.narrate.list_yours");
            data.heardCrossed(id, crossed + 1);
        } else if (crossed + 1 > seen) {
            List<SylarData.Entry> newest = data.list().stream().filter(e -> e.crossed)
                    .sorted(Comparator.comparingLong((SylarData.Entry e) -> e.night).reversed()).limit(crossed + 1 - seen).toList();
            String names = String.join(" and ", newest.stream().map(e -> e.name).toList());
            SylarVoice.narrateNow(player, "story.heroesevolved.sylar.narrate.list_more", names);
            data.heardCrossed(id, crossed + 1);
        }
    }

    /** The release lever in the guard post: stiff with a century's rust, so it takes two pulls. */
    static void lever(ServerPlayer player) {
        SylarData data = SylarData.get(player.getServer());
        BlockPos at = LevelFive.at("lever");
        if (data.stage() != SylarData.AWAKE) {
            SylarVoice.narrateNow(player, "story.heroesevolved.sylar.narrate.lever_done");
            return;
        }
        long now = player.getServer().overworld().getGameTime();
        Long first = LEVER.get(player.getUUID());
        if (first == null || now - first > LEVER_WINDOW) {
            LEVER.put(player.getUUID(), now);
            player.serverLevel().playSound(null, at, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8F, 0.4F);
            SylarVoice.narrateNow(player, "story.heroesevolved.sylar.narrate.lever_stiff");
            return;
        }
        LEVER.remove(player.getUUID());
        release(player);
    }

    /** The lever is down: his glass falls out of its frame, and he thanks whoever pulled it. Then his hand is on their wrist. */
    public static void release(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        SylarData data = SylarData.get(server);
        ServerLevel five = LevelFive.level(server);
        if (data.stage() != SylarData.AWAKE || five == null) return;
        SylarEntity gray = LevelFive.prisoner(five);
        if (gray != null) gray.removeTag(LevelFive.PRISONER);
        data.stage(SylarData.OUT);
        data.lastNight(nightOf(clock(server.overworld())));
        five.playSound(null, LevelFive.at("lever"), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 1.0F, 0.6F);
        LevelFive.glass(five, false, true);
        LevelFive.refresh(server);
        for (ServerPlayer p : five.players()) {
            SylarVoice.narrateNow(p, "story.heroesevolved.sylar.narrate.released");
            SylarVoice.sayNow(p, "story.heroesevolved.sylar.say.released");
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (data.listed().containsKey(p.getUUID()) && !LevelFive.in(p)) SylarVoice.narrateNow(p, "story.heroesevolved.sylar.narrate.released_far");
        if (gray != null) {
            gray.scenePose(SylarEntity.Pose.ONE_HAND);
            CAME.add(player.getUUID());
            REVEALS.add(new Reveal(player, gray, 20, true));
        } else comeFor(player, 0);
    }

    // ------------------------------------------------------------------
    // The man behind you
    // ------------------------------------------------------------------

    /** In {@code delay} ticks, he is standing behind {@code player}; a few words later, the Collection. */
    public static void comeFor(ServerPlayer player, int delay) {
        REVEALS.add(new Reveal(player, null, -Math.max(0, delay), false));
    }

    /** Whether he is on his way to someone, or standing behind them now. */
    public static boolean coming() { return !REVEALS.isEmpty(); }

    private static void tickReveals(MinecraftServer server) {
        for (Reveal r : List.copyOf(REVEALS)) {
            r.ticks++;
            if (r.ticks < 0) continue;
            ServerPlayer p = r.player;
            boolean gone = p.isRemoved() || !SylarEntity.counts(p) || CollectionArena.inCollection(p)
                    || r.actor != null && (r.actor.isRemoved() || r.actor.level() != p.level() || r.actor.distanceTo(p) > 24);
            if (gone) {
                if (r.actor != null && !r.actor.isRemoved()) r.actor.foldAway();
                REVEALS.remove(r);
                continue;
            }
            if (r.actor == null) {
                r.actor = behind(p);
                if (r.actor == null) { REVEALS.remove(r); continue; }
                SylarVoice.narrateNow(p, "story.heroesevolved.sylar.narrate.behind");
                p.serverLevel().playSound(null, r.actor.blockPosition(), com.jayptucker.heroesevolved.sounds.ModSounds.SYLAR_WATCH.get(), SoundSource.HOSTILE, 1.2F, 1.0F);
            }
            r.actor.getLookControl().setLookAt(p, 30, 30);
            if (r.ticks == 20 && !r.released)
                for (ServerPlayer near : p.serverLevel().players())
                    if (near.distanceToSqr(p) < SylarGeometry.PARTY * SylarGeometry.PARTY)
                        SylarVoice.sayNow(near, "story.heroesevolved.sylar.say.behind");
            if (r.ticks == REVEAL_TICKS - 15) r.actor.scenePose(SylarEntity.Pose.ONE_HAND);
            if (r.ticks >= REVEAL_TICKS) {
                REVEALS.remove(r);
                if (CollectionArena.open()) { r.actor.foldAway(); continue; }
                r.actor.discard();
                CollectionArena.open(p, true);
            }
        }
    }

    /** A Sylar standing two and a half blocks behind {@code player}, facing them (or beside them, if behind is a wall). */
    static SylarEntity behind(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        SylarEntity gray = ModEntities.SYLAR.get().create(level);
        if (gray == null) return null;
        Vec3 look = SylarEntity.flat(player.getLookAngle());
        Vec3 side = new Vec3(-look.z, 0, look.x);
        Vec3 chosen = null;
        for (Vec3 dir : List.of(look.scale(-2.5), look.scale(-1.6), side.scale(2), side.scale(-2), look.scale(2))) {
            for (int dy : new int[]{0, 1, -1}) {
                Vec3 at = player.position().add(dir).add(0, dy, 0);
                at = new Vec3(at.x, Math.floor(at.y), at.z);
                var box = gray.getType().getDimensions().makeBoundingBox(at);
                BlockPos below = BlockPos.containing(at.x, at.y - 0.5, at.z);
                if (level.noCollision(box) && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) { chosen = at; break; }
            }
            if (chosen != null) break;
        }
        if (chosen == null) chosen = player.position();
        Vec3 to = player.position().subtract(chosen);
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90;
        gray.moveTo(chosen.x, chosen.y, chosen.z, yaw, 0);
        gray.setYHeadRot(yaw);
        gray.setYBodyRot(yaw);
        gray.actor(true);
        return level.addFreshEntity(gray) ? gray : null;
    }

    // ------------------------------------------------------------------
    // What the list and the journal show
    // ------------------------------------------------------------------

    /**
     * The watch list as written on the wall, down the left column and then the right: the old names (crossed out in
     * faded ink when their power stands in his case), the living (crossed out in fresh ink), the list's players, and,
     * once he is beaten, his own.
     */
    public static List<Component> listLines(MinecraftServer server) {
        SylarData data = SylarData.get(server);
        List<Component> lines = new ArrayList<>();
        for (Old old : OLD) {
            boolean taken = SylarData.holds(server, SylarGeometry.CASES.get(old.hour()));
            lines.add(taken ? Component.literal(old.name()).withStyle(ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH)
                    : Component.literal(old.name()).withStyle(ChatFormatting.WHITE));
        }
        for (SylarData.Entry entry : data.list())
            lines.add(entry.crossed ? Component.literal(entry.name).withStyle(ChatFormatting.RED, ChatFormatting.STRIKETHROUGH)
                    : Component.literal(entry.name).withStyle(ChatFormatting.WHITE));
        boolean beaten = data.stage() == SylarData.BEATEN;
        int room = 16 - lines.size() - (beaten ? 1 : 0);
        for (String name : data.listed().values()) {
            if (room-- <= 0) break;
            lines.add(Component.literal(name).withStyle(ChatFormatting.YELLOW));
        }
        if (beaten) lines.add(Component.literal("G. Gray").withStyle(ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH));
        return lines;
    }

    /**
     * The list for the journal, one entry each as {@code <mark>|<text>}: {@code x} crossed long ago, {@code s} spared
     * long ago, {@code X} crossed lately, {@code o} still living, {@code y} you, {@code p} another player, {@code g} him.
     */
    public static List<String> journal(MinecraftServer server, ServerPlayer reader) {
        SylarData data = SylarData.get(server);
        if (data.stage() == SylarData.DORMANT) return List.of();
        List<String> out = new ArrayList<>();
        for (Old old : OLD) out.add((SylarData.holds(server, SylarGeometry.CASES.get(old.hour())) ? "x|" : "s|") + old.name());
        for (SylarData.Entry entry : data.list()) {
            String where = entry.village == null ? "whereabouts unknown"
                    : "a village near " + entry.village.getX() + ", " + entry.village.getZ();
            out.add((entry.crossed ? "X|" : "o|") + entry.name + " (" + power(entry) + "), " + where);
        }
        data.listed().forEach((id, name) -> out.add((id.equals(reader.getUUID()) ? "y|" : "p|") + name));
        if (data.stage() == SylarData.BEATEN) out.add("g|Gabriel Gray");
        return out;
    }

    // ------------------------------------------------------------------
    // Operators, tests and server stops
    // ------------------------------------------------------------------

    /**
     * Moves the story on one step for an operator: wakes him (for {@code target}), crosses out the next living name, opens
     * his cell, or sends him to {@code target} now. Returns what happened.
     */
    public static String next(ServerPlayer target) {
        MinecraftServer server = target.getServer();
        SylarData data = SylarData.get(server);
        String name = target.getName().getString();
        switch (data.stage()) {
            case SylarData.DORMANT -> {
                wake(target);
                BlockPos hatch = data.hatch();
                return "He is awake. The hatch is at " + (hatch == null ? "?" : hatch.toShortString()) + "; " + name + " is on his list.";
            }
            case SylarData.AWAKE -> {
                if (data.list().stream().anyMatch(e -> !e.crossed)) {
                    SylarData.Entry crossed = cross(server);
                    data.lastNight(nightOf(clock(server.overworld())));
                    return crossed == null ? "Everyone left on the list is being guarded tonight." : crossed.name + " is crossed out.";
                }
                out(server);
                return "His cell stands open. Use it again to send him to " + name + ".";
            }
            case SylarData.OUT -> {
                if (CollectionArena.open() || coming()) return "He is busy.";
                comeFor(target, 0);
                data.lastNight(nightOf(clock(server.overworld())));
                return "He is behind " + name + ".";
            }
            default -> { return "He has been beaten. A Repair Ticket brings the fight back."; }
        }
    }

    /** One line on where the story stands. */
    public static String status(MinecraftServer server) {
        SylarData data = SylarData.get(server);
        String[] stages = {"asleep below the bunker", "awake in Level 5", "out of his cell", "beaten"};
        return "The prisoner is " + stages[Math.clamp(data.stage(), 0, 3)] + "; " + data.crossedCount() + " of " + data.list().size()
                + " living names crossed out, " + data.listed().size() + " players on his list.";
    }

    /** Nothing in flight: no talk, no reveal (an actor standing behind someone folds away). */
    public static void reset() {
        for (Reveal r : REVEALS) if (r.actor != null && !r.actor.isRemoved()) r.actor.discard();
        REVEALS.clear(); TALKS.clear(); SPOKEN.clear(); CAME.clear(); ARRIVED.clear(); LEVER.clear(); GUARDS.clear(); MET.clear();
    }

    /** The lines still waiting for {@code player} at the glass (tests). */
    static int talkPending(ServerPlayer player) { Talk t = TALKS.get(player.getUUID()); return t == null ? 0 : t.lines.size(); }

    /** Tests: let his next line through the glass come now. */
    static void hurryTalk(ServerPlayer player) { Talk t = TALKS.get(player.getUUID()); if (t != null) t.next = 0; }

    /** Tests: the lines he has queued for this visitor, as lang keys. */
    static List<String> talkKeys(ServerPlayer player) {
        Talk t = TALKS.get(player.getUUID());
        return t == null ? List.of() : t.lines.stream().map(Line::key).toList();
    }

}
