package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.data.EclipseTimeline;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.radiation.NuclearFallout;
import com.jayptucker.heroesevolved.radiation.RadiationService;
import com.jayptucker.heroesevolved.radio.EmergencyBand;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.time.TimeStopService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A villager the Eclipse woke a power in: Induced Radioactivity, and no way to hold it. As the sun comes back he starts
 * to glow, and about a minute later he goes off like a radioactive player's Critical Mass. He can't help it.
 * <p>
 * His village calls for help on the emergency band, and everyone near sees a countdown. Players either get the village
 * out, carrying villagers (sneak + use) to the safe point marked by a column of light, or stop him:
 * <ul>
 *   <li>a speedster runs a containment run round him (the Cyclone key), and holds the blast in;</li>
 *   <li>a radioactive player crouches beside him and draws the heat into themselves;</li>
 *   <li>freezing him (Cryokinesis) or stopping his time (Space-Time) pauses the countdown while it lasts;</li>
 *   <li>carrying him well clear of the village means the blast hits no one there.</li>
 * </ul>
 * Hurting him makes it worse: every hit takes {@link #HIT_PENALTY} ticks off the countdown, and if he dies he goes off at
 * once. Rare: {@link HeroesEvolvedConfig.Common#villagerMeltdownChance} each Eclipse.
 * <p>
 * His state is kept in his own data ({@link #TAG}), so a meltdown carries on through a restart or an unloaded chunk.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class VillagerMeltdown {
    public static final String TAG = "heroesevolved_meltdown";
    /** The safe point is this far from the bell, and counts villagers within {@link #SAFE_RADIUS} of it. */
    public static final int SAFE_DISTANCE = 44, SAFE_RADIUS = 6;
    /** Villagers this close to the bell when it starts are the ones to get out. */
    public static final int VILLAGE_RADIUS = 48;
    /** Players this close see the countdown. */
    static final int WATCH_RANGE = 112;
    /** Each hit takes ten seconds off the countdown (at most once a second). */
    public static final int HIT_PENALTY = 200;
    /** A radioactive player crouching this close draws the heat out; this long and he is cool. */
    public static final double DRAW_REACH = 3;
    public static final int DRAW_TICKS = 160;
    /** The Core a radioactive player takes on every ten ticks of drawing (48 in all: it runs them hot). */
    static final int DRAW_MASS = 3;
    /** Re-sent this often. */
    static final int PIECE_EVERY = 10;
    /** When the Eclipse is leaving: how long after the sun returns he starts to glow (20 to 80 seconds). */
    static final int AWAKEN_MIN = 400, AWAKEN_SPREAD = 1200;
    static final ResourceLocation ROOTED = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "meltdown_rooted");

    /** One meltdown under way. */
    public static final class Meltdown {
        final Villager villager;
        final ServerLevel level;
        final BlockPos bell;
        final Vec3 safe;
        final int total;
        int fuse;
        final Set<UUID> village = new LinkedHashSet<>();
        final Set<UUID> safeNow = new HashSet<>();
        EmergencyBand.Call call;
        final ServerBossEvent bar;
        long heldAt = -1000, hitAt = -1000, nextShout = -1000;
        int shouts;
        int drawn;
        boolean clearAnnounced;
        Meltdown(Villager villager, ServerLevel level, BlockPos bell, Vec3 safe, int total, int fuse) {
            this.villager = villager; this.level = level; this.bell = bell; this.safe = safe; this.total = total; this.fuse = fuse;
            bar = new ServerBossEvent(Component.literal("Meltdown"), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
            bar.setDarkenScreen(false);
        }
        public int fuse() { return fuse; }
        public int total() { return total; }
        public Vec3 safe() { return safe; }
        public BlockPos bell() { return bell; }
        public Villager villager() { return villager; }
        /** The villagers of his village now standing at the safe point. */
        public int safeCount() { return safeNow.size(); }
        public int villageSize() { return village.size(); }
        public EmergencyBand.Call call() { return call; }
    }

    private static final Map<UUID, Meltdown> MELTDOWNS = new HashMap<>();
    /** Villagers loaded with a meltdown in their data, picked up next tick (not while their chunk is loading). */
    private static final List<Villager> RESUMING = new ArrayList<>();
    /** The Eclipse is leaving: when to wake a villager, if the roll said so. */
    private static long awakenAt = Long.MIN_VALUE;
    private static int awakenTries;
    private static EclipseTimeline.Phase lastPhase;

    private VillagerMeltdown() {}

    // ------------------------------------------------------------------
    // Asking about it, from other powers
    // ------------------------------------------------------------------

    /** Is this a villager in meltdown? */
    public static boolean critical(Entity entity) {
        Meltdown m = MELTDOWNS.get(entity.getUUID());
        return m != null && m.villager == entity;
    }

    public static Meltdown of(Entity entity) {
        Meltdown m = MELTDOWNS.get(entity.getUUID());
        return m != null && m.villager == entity ? m : null;
    }

    public static List<Meltdown> all() { return List.copyOf(MELTDOWNS.values()); }

    /** One more tick held back by something outside (a speedster's containment run). */
    public static void hold(Entity entity) {
        Meltdown m = of(entity);
        if (m != null) m.heldAt = m.level.getGameTime();
    }

    /** It is over: the heat is gone out of him, and he is only a villager again. */
    public static void defuse(Entity entity, ServerPlayer by) {
        Meltdown m = of(entity);
        if (m == null) return;
        finish(m);
        Villager v = m.villager;
        ServerLevel level = m.level;
        SetPiecePayload.send(level, Piece.RAD_EMP, v.position(), v.position().add(0, 1, 0), 9, null);
        level.playSound(null, v.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 2.0F, 0.6F);
        level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
        for (ServerPlayer p : watchers(m)) {
            p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 20 * 60 * 40, 0, false, false, true));
            p.sendSystemMessage(Component.literal((by != null ? by.getName().getString() : "Someone")
                    + " stopped the meltdown. Everyone lived.").withStyle(ChatFormatting.GREEN));
        }
    }

    /** How far from someone the admin tools look for a villager to wake. */
    public static final int NEAR = 64;

    /** Wakes it in the nearest grown villager within {@link #NEAR} blocks of {@code at}. Null if there is none. */
    public static Meltdown startNear(ServerLevel level, Vec3 at, int fuse) {
        Villager villager = level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(at, at).inflate(NEAR),
                        v -> v.isAlive() && !v.isBaby() && !critical(v))
                .stream().min(java.util.Comparator.comparingDouble(v -> v.distanceToSqr(at))).orElse(null);
        return villager == null ? null : start(villager, fuse);
    }

    /** Ends every meltdown with nothing happening. How many there were. */
    public static int cancelAll() {
        List<Meltdown> all = all();
        for (Meltdown m : all) finish(m);
        return all.size();
    }

    /** One line on the meltdowns under way, for the admin menu. */
    public static String describe() {
        List<Meltdown> all = all();
        if (all.isEmpty()) return "No meltdown under way.";
        Meltdown m = all.getFirst();
        int seconds = (m.fuse + 19) / 20;
        return "Meltdown (bell " + m.bell.getX() + ", " + m.bell.getZ() + "): " + seconds / 60 + ":" + String.format("%02d", seconds % 60)
                + " left, " + m.safeNow.size() + "/" + m.village.size() + " safe" + (all.size() > 1 ? " (+" + (all.size() - 1) + " more)" : "") + ".";
    }

    /** Ends a meltdown with nothing happening at all (for admins and tests). */
    public static void cancel(Entity entity) {
        Meltdown m = of(entity);
        if (m != null) finish(m);
    }

    // ------------------------------------------------------------------
    // Starting one
    // ------------------------------------------------------------------

    /** Wakes the power in {@code villager} now, with {@code fuse} ticks to go. Null if he can't (a baby, already going). */
    public static Meltdown start(Villager villager, int fuse) {
        if (!(villager.level() instanceof ServerLevel level) || villager.isBaby() || !villager.isAlive()
                || MELTDOWNS.containsKey(villager.getUUID())) return null;
        BlockPos bell = level.getPoiManager().findClosest(poi -> poi.is(PoiTypes.MEETING), villager.blockPosition(),
                VILLAGE_RADIUS, PoiManager.Occupancy.ANY).orElse(villager.blockPosition());
        Vec3 safe = safePoint(level, bell, villager.position());
        Meltdown m = new Meltdown(villager, level, bell, safe, fuse, fuse);
        save(m);
        begin(m, true);
        return m;
    }

    private static void begin(Meltdown m, boolean fresh) {
        MELTDOWNS.put(m.villager.getUUID(), m);
        root(m.villager, true);
        m.village.clear();
        for (Villager v : m.level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(m.bell).inflate(VILLAGE_RADIUS),
                v -> v != m.villager && v.isAlive() && v.blockPosition().distSqr(m.bell) <= (long) VILLAGE_RADIUS * VILLAGE_RADIUS))
            m.village.add(v.getUUID());
        m.call = EmergencyBand.call(m.level, m.bell, words(m), m.fuse + 200);
        if (fresh) {
            Vec3 at = m.villager.position();
            m.level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 2.0F, 0.5F);
            m.level.playSound(null, at.x, at.y, at.z, SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 1.2F, 0.8F);
            for (ServerPlayer p : watchers(m))
                p.sendSystemMessage(Component.literal("A villager has started to glow. Get his village out, or stop him.")
                        .withStyle(ChatFormatting.GOLD));
        }
    }

    /**
     * The safe point: {@link #SAFE_DISTANCE} out from the bell, on the side away from him if the ground is dry there,
     * otherwise the first dry direction round from it.
     */
    static Vec3 safePoint(ServerLevel level, BlockPos bell, Vec3 him) {
        Vec3 away = Vec3.atCenterOf(bell).subtract(him).multiply(1, 0, 1);
        double start = away.lengthSqr() < 1 ? 0 : Math.atan2(away.z, away.x);
        Vec3 first = null;
        for (int i = 0; i < 8; i++) {
            double a = start + (i % 2 == 0 ? 1 : -1) * ((i + 1) / 2) * Math.PI / 4;
            int x = bell.getX() + (int) Math.round(Math.cos(a) * SAFE_DISTANCE), z = bell.getZ() + (int) Math.round(Math.sin(a) * SAFE_DISTANCE);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            Vec3 at = new Vec3(x + 0.5, y, z + 0.5);
            if (first == null) first = at;
            if (level.getFluidState(new BlockPos(x, y - 1, z)).isEmpty() && level.getFluidState(new BlockPos(x, y, z)).isEmpty()) return at;
        }
        return first;
    }

    // ------------------------------------------------------------------
    // Running it
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (Villager v : List.copyOf(RESUMING)) resume(v);
        RESUMING.clear();
        eclipse(server);
        bubbles(server);
        for (Meltdown m : List.copyOf(MELTDOWNS.values())) if (m.level.getServer() == server) tick(m);
    }

    static void tick(Meltdown m) {
        Villager v = m.villager;
        if (v.isRemoved()) {
            // Unloaded or gone to another dimension: his data carries the countdown, and it resumes when he is back.
            if (v.isAlive()) pause(m);
            return;
        }
        long now = m.level.getGameTime();
        boolean frozen = CryoService.isFrozen(v) || TimeStopService.isStopped(v);
        boolean held = m.heldAt == now || m.heldAt == now - 1;
        boolean drawing = draw(m, now);
        if (!frozen && !held && !drawing) m.fuse--;
        if (m.drawn >= DRAW_TICKS) {
            defuse(v, drawer(m));
            return;
        }
        if (m.fuse <= 0) {
            detonate(m);
            return;
        }
        float progress = 1 - m.fuse / (float) m.total;
        if (now % PIECE_EVERY == 0) {
            save(m);
            // Scale carries how far gone he is, and (+2) whether something is holding the heat in right now.
            SetPiecePayload.send(m.level, Piece.VILLAGE_MELTDOWN, v.position(), v.position().add(0, 1, 0),
                    progress + (frozen || held || drawing ? 2 : 0), v);
            SetPiecePayload.send(m.level, Piece.VILLAGE_SAFE, m.safe, m.safe.add(0, 1, 0), SAFE_RADIUS, null);
            tally(m);
        }
        if (now % 5 == 0) flee(m, now);
        if (now % 20 == 0) {
            m.call.say(words(m));
            bar(m, progress);
        }
        // It builds: a hum that climbs, and in the last ten seconds the ground shakes.
        int every = m.fuse < 200 ? 10 : 30;
        if (now % every == 0)
            m.level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.NEUTRAL, 1.5F + progress, 0.5F + 1.3F * progress);
        if (m.fuse < 200 && now % 5 == 0) CameraShakePayload.sendAround(m.level, v.position(), 32, 0.2F + 0.8F * (1 - m.fuse / 200F), 6);
        if (!m.clearAnnounced && v.blockPosition().distSqr(m.bell) > (long) clearDistance() * clearDistance()) {
            m.clearAnnounced = true;
            for (ServerPlayer p : watchers(m))
                p.displayClientMessage(Component.literal("He's clear of the village.").withStyle(ChatFormatting.GREEN), true);
        }
    }

    /** Far enough from the bell that the blast can't reach the village's middle. */
    static int clearDistance() { return NuclearFallout.RADIUS + VILLAGE_RADIUS / 2 + 4; }

    /** Radioactive players crouching beside him draw the heat into themselves. True while one is. */
    private static boolean draw(Meltdown m, long now) {
        ServerPlayer p = drawer(m);
        if (p == null) return false;
        m.drawn++;
        if (now % 10 == 0) {
            RadiationService.addMass(p, DRAW_MASS);
            p.displayClientMessage(Component.literal("Drawing his heat into you  " + Math.round(100F * m.drawn / DRAW_TICKS) + "%")
                    .withStyle(ChatFormatting.YELLOW), true);
            m.level.playSound(null, p.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.8F);
        }
        return true;
    }

    private static ServerPlayer drawer(Meltdown m) {
        for (ServerPlayer p : m.level.players())
            if (p.isCrouching() && p.isAlive() && !p.isSpectator() && p.distanceToSqr(m.villager) <= DRAW_REACH * DRAW_REACH
                    && radioactive(p) && !RadiationService.critical(p)) return p;
        return null;
    }

    private static boolean radioactive(ServerPlayer p) {
        return PlayerAbilityService.getData(p).assignedPower()
                .filter(e -> e.getKey().equals(ModAbilities.RADIATION_ID) && e.getValue().isUnlocked()).isPresent();
    }

    /** Who of his village is at the safe point now. Those who get there stay huddled there until it is over. */
    /** Villagers this close to him scream and run. */
    public static final double FLEE_RANGE = 20;
    /** How fast they run: Odessa's crowd ran from the light at the same pace. */
    static final float FLEE_SPEED = 0.95F;
    /** A shout every this many ticks, from someone running near a player; it hangs over them this long. */
    static final int SHOUT_EVERY = 26, SHOUT_TICKS = 50;
    /** What they shout as they run. The first is always the first line. */
    static final String[] SHOUTS = {"It's happening again!", "The prophecy was true!!", "He's glowing like the sun!",
            "Run! Get away from him!", "Not again! Not again!", "The eclipse did this to him!", "Where are my children?!",
            "Don't look at the light!", "Make for the green light!", "Somebody help us!", "The sky warned us!", "Keep running!"};
    /**
     * Bubbles carry this tag. One that loads again later (saved riding its speaker when a chunk unloaded, or across a
     * restart) is not in {@link #LIVE}, so it is turned away.
     */
    static final String BUBBLE_TAG = HeroesEvolved.MOD_ID + "_meltdown_bubble";
    private static final Set<UUID> LIVE = new HashSet<>();

    private record Bubble(net.minecraft.world.entity.Display.TextDisplay display, Entity speaker, String text, long until) {}
    private static final List<Bubble> BUBBLES = new ArrayList<>();

    /**
     * Everyone near him runs, the way Odessa's crowd ran from the light: away from him at a sprint, shouting, with
     * screams riding the runners on the client ({@code PanicSounds.village}). Anyone asleep wakes to it. Those already
     * safe or being carried stay where they are.
     */
    private static void flee(Meltdown m, long now) {
        Villager him = m.villager;
        List<Villager> running = new ArrayList<>();
        for (Villager v : m.level.getEntitiesOfClass(Villager.class, him.getBoundingBox().inflate(FLEE_RANGE),
                v -> v != him && v.isAlive() && !m.safeNow.contains(v.getUUID()) && !EntityCarryService.isBeingCarried(v)
                        && v.distanceToSqr(him) <= FLEE_RANGE * FLEE_RANGE)) {
            if (v.isSleeping()) v.stopSleeping();
            Vec3 away = v.position().subtract(him.position()).multiply(1, 0, 1);
            if (away.lengthSqr() < 1.0E-4) away = new Vec3(v.getRandom().nextDouble() - 0.5, 0, v.getRandom().nextDouble() - 0.5);
            Vec3 to = him.position().add(away.normalize().scale(FLEE_RANGE + 8));
            var brain = v.getBrain();
            brain.setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
                    new net.minecraft.world.entity.ai.memory.WalkTarget(BlockPos.containing(to), FLEE_SPEED, 2));
            brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);
            running.add(v);
        }
        if (now >= m.nextShout && !running.isEmpty()) {
            List<Villager> heard = running.stream().filter(v -> m.level.players().stream()
                    .anyMatch(p -> !p.isSpectator() && p.distanceToSqr(v) <= 32 * 32)).toList();
            if (!heard.isEmpty()) {
                Villager who = heard.get(m.level.random.nextInt(heard.size()));
                say(m.level, who, m.shouts++ == 0 ? SHOUTS[0] : SHOUTS[1 + m.level.random.nextInt(SHOUTS.length - 1)], now);
            }
            m.nextShout = now + SHOUT_EVERY;
        }
    }

    /** Words over a runner's head for a couple of seconds. */
    private static void say(ServerLevel level, Entity speaker, String text, long now) {
        BUBBLES.removeIf(b -> {
            if (b.speaker != speaker) return false;
            b.display.discard();
            LIVE.remove(b.display.getUUID());
            return true;
        });
        var display = com.jayptucker.heroesevolved.effects.SpeechBubble.show(level, speaker, text, true, BUBBLE_TAG,
                d -> LIVE.add(d.getUUID()));
        if (display == null) return;
        BUBBLES.add(new Bubble(display, speaker, text, now + SHOUT_TICKS));
    }

    /** Bubbles over their time, or whose speaker is gone, are taken down. */
    private static void bubbles(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        BUBBLES.removeIf(b -> {
            if (now < b.until && b.speaker.isAlive() && !b.speaker.isRemoved() && !b.display.isRemoved()) return false;
            b.display.discard();
            LIVE.remove(b.display.getUUID());
            return true;
        });
    }

    /** The words being shouted right now, and by whom (for tests). */
    public static List<String> shouting(Entity speaker) {
        List<String> out = new ArrayList<>();
        for (Bubble b : BUBBLES)
            if (b.speaker == speaker && !b.display.isRemoved()) out.add(b.text);
        return out;
    }

    private static void tally(Meltdown m) {
        Set<UUID> was = Set.copyOf(m.safeNow);
        m.safeNow.clear();
        for (Villager v : m.level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(BlockPos.containing(m.safe)).inflate(SAFE_RADIUS + 1, 8, SAFE_RADIUS + 1),
                v -> m.village.contains(v.getUUID()) && v.isAlive() && !EntityCarryService.isBeingCarried(v) && safe(m, v))) {
            m.safeNow.add(v.getUUID());
            if (!was.contains(v.getUUID())) {
                root(v, true);
                m.level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
                m.level.playSound(null, v.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.NEUTRAL, 1.0F, 1.2F);
            }
        }
    }

    public static boolean safe(Meltdown m, Entity e) {
        return e.position().subtract(m.safe).horizontalDistanceSqr() <= SAFE_RADIUS * SAFE_RADIUS && Math.abs(e.getY() - m.safe.y) < 8;
    }

    private static void bar(Meltdown m, float progress) {
        int seconds = (m.fuse + 19) / 20;
        m.bar.setName(Component.literal("Meltdown  " + seconds / 60 + ":" + String.format("%02d", seconds % 60)
                + "     Safe " + m.safeNow.size() + "/" + m.village.size()));
        m.bar.setProgress(Mth.clamp(1 - progress, 0, 1));
        m.bar.setColor(m.fuse < 200 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.YELLOW);
        Set<ServerPlayer> near = new HashSet<>(watchers(m));
        for (ServerPlayer p : List.copyOf(m.bar.getPlayers())) if (!near.contains(p)) m.bar.removePlayer(p);
        for (ServerPlayer p : near) m.bar.addPlayer(p);
    }

    private static Component words(Meltdown m) {
        int seconds = (m.fuse + 19) / 20;
        return Component.literal("Mayday! One of us is glowing like the sun. Get us out! " + seconds / 60 + ":" + String.format("%02d", seconds % 60));
    }

    /** Players near his village or near him. */
    static List<ServerPlayer> watchers(Meltdown m) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : m.level.players())
            if (!p.isSpectator() && (p.blockPosition().distSqr(m.bell) <= (long) WATCH_RANGE * WATCH_RANGE
                    || p.distanceToSqr(m.villager) <= WATCH_RANGE * WATCH_RANGE)) out.add(p);
        return out;
    }

    /** Time's up: he goes off where he is. Whoever of his village is alive afterwards made it. */
    static void detonate(Meltdown m) {
        Villager v = m.villager;
        List<ServerPlayer> watching = watchers(m);
        finish(m);
        if (EntityCarryService.isBeingCarried(v)) EntityCarryService.releaseEntity(v);
        NuclearFallout.begin(v, false);
        if (v.isAlive()) v.kill();
        m.level.getServer().tell(new net.minecraft.server.TickTask(m.level.getServer().getTickCount() + 10, () -> result(m, watching)));
    }

    private static void result(Meltdown m, List<ServerPlayer> watching) {
        int alive = 0;
        for (UUID id : m.village) if (m.level.getEntity(id) instanceof Villager v && v.isAlive()) alive++;
        boolean everyone = alive == m.village.size();
        for (ServerPlayer p : watching) {
            if (p.isRemoved()) continue;
            if (everyone && p.isAlive()) p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 20 * 60 * 20, 0, false, false, true));
            p.sendSystemMessage(Component.literal(everyone ? "He's gone, but his village got out. All " + m.village.size() + " of them."
                    : alive + " of " + m.village.size() + " got out.").withStyle(everyone ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        }
    }

    /** It is over, either way: everything it set going stops. */
    private static void finish(Meltdown m) {
        MELTDOWNS.remove(m.villager.getUUID());
        m.villager.getPersistentData().remove(TAG);
        root(m.villager, false);
        for (UUID id : m.village) if (m.level.getEntity(id) instanceof Villager v) root(v, false);
        if (m.call != null) EmergencyBand.end(m.call);
        m.bar.removeAllPlayers();
    }

    /** Off the map for now (unloaded): stop the call and the bar; his data still has the countdown. */
    private static void pause(Meltdown m) {
        MELTDOWNS.remove(m.villager.getUUID());
        if (m.call != null) EmergencyBand.end(m.call);
        m.bar.removeAllPlayers();
    }

    /** He can't walk away from it; villagers who reach safety stay huddled there. */
    private static void root(Villager v, boolean on) {
        AttributeInstance speed = v.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (on) speed.addOrUpdateTransientModifier(new AttributeModifier(ROOTED, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        else speed.removeModifier(ROOTED);
    }

    // ------------------------------------------------------------------
    // Keeping it through unloading and restarts
    // ------------------------------------------------------------------

    private static void save(Meltdown m) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("fuse", m.fuse);
        tag.putInt("total", m.total);
        tag.putLong("bell", m.bell.asLong());
        tag.putDouble("safeX", m.safe.x);
        tag.putDouble("safeY", m.safe.y);
        tag.putDouble("safeZ", m.safe.z);
        m.villager.getPersistentData().put(TAG, tag);
    }

    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        // A shout left over from before a restart (it was saved riding its speaker): gone.
        if (event.getEntity() instanceof net.minecraft.world.entity.Display.TextDisplay d && d.getTags().contains(BUBBLE_TAG)
                && !LIVE.contains(d.getUUID())) {
            event.setCanceled(true);
            return;
        }
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Villager v && v.getPersistentData().contains(TAG))
            RESUMING.add(v);
    }

    private static void resume(Villager v) {
        if (v.isRemoved() || !v.isAlive() || MELTDOWNS.containsKey(v.getUUID()) || !(v.level() instanceof ServerLevel level)) return;
        CompoundTag tag = v.getPersistentData().getCompound(TAG);
        int total = Math.max(1, tag.getInt("total"));
        Meltdown m = new Meltdown(v, level, BlockPos.of(tag.getLong("bell")),
                new Vec3(tag.getDouble("safeX"), tag.getDouble("safeY"), tag.getDouble("safeZ")), total, Math.max(1, tag.getInt("fuse")));
        begin(m, false);
    }

    // ------------------------------------------------------------------
    // Hurting him; his death
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void hurt(LivingIncomingDamageEvent event) {
        Meltdown m = of(event.getEntity());
        if (m == null || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        event.setCanceled(true);
        long now = m.level.getGameTime();
        if (now - m.hitAt < 20 || !(event.getSource().getEntity() instanceof LivingEntity)) return;
        m.hitAt = now;
        m.fuse = Math.max(1, m.fuse - HIT_PENALTY);
        m.level.playSound(null, m.villager.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 1.5F, 1.6F);
        if (event.getSource().getEntity() instanceof ServerPlayer p)
            p.displayClientMessage(Component.literal("Hurting him only makes him hotter.").withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent
    public static void died(LivingDeathEvent event) {
        Meltdown m = of(event.getEntity());
        if (m != null) detonate(m);
    }

    // ------------------------------------------------------------------
    // The Eclipse wakes it
    // ------------------------------------------------------------------

    /** As the Eclipse leaves, roll once; if it hits, a villager near a player wakes a little after the sun returns. */
    private static void eclipse(MinecraftServer server) {
        EclipseTimeline.Phase phase = EclipseService.phase(server);
        long now = server.overworld().getGameTime();
        if (phase != lastPhase) {
            if (phase == EclipseTimeline.Phase.DEPARTURE && server.overworld().random.nextDouble()
                    < HeroesEvolvedConfig.COMMON.villagerMeltdownChance.get()) {
                awakenAt = now + AWAKEN_MIN + server.overworld().random.nextInt(AWAKEN_SPREAD);
                awakenTries = 0;
            }
            lastPhase = phase;
        }
        if (awakenAt == Long.MIN_VALUE || now < awakenAt) return;
        if (awaken(server) != null || ++awakenTries >= 12) awakenAt = Long.MIN_VALUE;
        else awakenAt = now + 200;
    }

    /** A grown villager near a village's bell and a player, if there is one. */
    public static Meltdown awaken(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<ServerPlayer> players = new ArrayList<>(level.players());
        java.util.Collections.shuffle(players, new java.util.Random(level.random.nextLong()));
        for (Player p : players) {
            if (p.isSpectator()) continue;
            List<Villager> near = level.getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(96),
                    v -> v.isAlive() && !v.isBaby() && !critical(v) && level.getPoiManager().findClosest(poi -> poi.is(PoiTypes.MEETING),
                            v.blockPosition(), VILLAGE_RADIUS, PoiManager.Occupancy.ANY).isPresent());
            if (near.isEmpty()) continue;
            Meltdown m = start(near.get(level.random.nextInt(near.size())), HeroesEvolvedConfig.COMMON.villagerMeltdownFuseTicks.get());
            if (m != null) return m;
        }
        return null;
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) { clear(); }

    /** Forgets every meltdown without ending any (their villagers keep their countdowns). */
    public static void clear() {
        for (Meltdown m : List.copyOf(MELTDOWNS.values())) pause(m);
        MELTDOWNS.clear();
        RESUMING.clear();
        BUBBLES.forEach(b -> b.display.discard());
        BUBBLES.clear();
        LIVE.clear();
        awakenAt = Long.MIN_VALUE;
        lastPhase = null;
    }
}
