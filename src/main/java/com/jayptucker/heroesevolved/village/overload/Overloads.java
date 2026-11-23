package com.jayptucker.heroesevolved.village.overload;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.dampener.Dampening;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.gifted.Renegades;
import com.jayptucker.heroesevolved.mimicry.GiftedVillagers;
import com.jayptucker.heroesevolved.network.OverloadPayload;
import com.jayptucker.heroesevolved.radio.EmergencyBand;
import com.jayptucker.heroesevolved.time.TimeStopService;
import com.jayptucker.heroesevolved.village.Shouts;
import com.jayptucker.heroesevolved.village.VillageTickets;
import com.jayptucker.heroesevolved.village.Villages;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Overloads: a villager whose gift woke stronger than they can hold, at the centre of their own storm (a {@link Storm}:
 * a wildfire, a supercell, a blizzard). It grows for {@link HeroesEvolvedConfig.Common#overloadTicks} ticks and then
 * breaks over their village, unless someone reaches them through it and stays with them until they are calm. What each
 * storm does is its {@link Weather}; the numbers it shares with the drawing are in {@link OverloadGeometry}.
 * <ul>
 *   <li>Anyone within {@link OverloadGeometry#CALM_REACH} calms them; someone with the same power, an Empathic Mimic, or
 *   a telepath (from {@link OverloadGeometry#MIND_REACH}, in sight) twice as fast. Calmed, they guard their village with
 *   the gift, under control now.</li>
 *   <li>Freezing them solid or stopping their time holds the countdown; an Ability Dampener ends it, and the gift.</li>
 *   <li>Left alone, it breaks ({@link Weather#breaks}), burns itself out, and their gift with it.</li>
 * </ul>
 * Like a meltdown ({@code village/VillagerMeltdown}), it calls for help on the emergency band, holds its village loaded,
 * and is saved in the villager's own data ({@link #TAG}), so it carries on through a restart or an unloaded chunk.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Overloads {
    public static final String TAG = "heroesevolved_overload";
    static final ResourceLocation ROOTED = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "overload_rooted");
    /** Its village is gathered again this often; the clients hear of it this often; it is saved this often. */
    static final int GATHER_EVERY = 40, SYNC_EVERY = 5, SAVE_EVERY = 10;
    /** A shout every this many ticks, from someone of the village near a player; it hangs over them this long. */
    static final int SHOUT_EVERY = 30, SHOUT_TICKS = 50;
    /** How far from someone the admin tools look for a villager. */
    public static final int NEAR = 64;

    private static final Map<Storm, String[]> SHOUTS = Map.of(
            Storm.WILDFIRE, new String[]{"Fire! Fire!", "The fields are burning!", "Get water!", "It's coming this way!",
                    "Run for the river!", "My house!", "It's coming out of them!", "Don't breathe the smoke!"},
            Storm.SUPERCELL, new String[]{"Twister!", "Get inside!", "It's got them in it!", "Hold on to something!",
                    "It's turning this way!", "The roof's coming off!", "Get down!", "It's coming through!"},
            Storm.BLIZZARD, new String[]{"Get indoors!", "I can't feel my hands!", "Where's the path?", "Find the fire!",
                    "It's coming from them!", "Don't stay out in it!", "I can't see!", "Keep moving!"});

    private static final Map<Storm, Weather> WEATHER = Map.of(Storm.WILDFIRE, new Wildfire(), Storm.SUPERCELL, new Supercell(),
            Storm.BLIZZARD, new Blizzard());
    private static final Map<UUID, Overload> ACTIVE = new HashMap<>();
    /** Villagers loaded with an overload in their data, picked up next tick (not while their chunk is loading). */
    private static final List<Villager> RESUMING = new ArrayList<>();

    private Overloads() {}

    static Weather weather(Storm storm) { return WEATHER.get(storm); }

    // ------------------------------------------------------------------
    // Asking about it, from other powers
    // ------------------------------------------------------------------

    /** Is this a villager at the centre of an overload? */
    public static boolean critical(Entity entity) { return of(entity) != null; }

    public static Overload of(Entity entity) {
        if (entity == null) return null;
        Overload o = ACTIVE.get(entity.getUUID());
        return o != null && o.villager == entity ? o : null;
    }

    public static List<Overload> all() { return List.copyOf(ACTIVE.values()); }

    /** One more tick held back by something outside (a cryokinetic's Ice Blast on a wildfire). */
    public static void hold(Entity entity) {
        Overload o = of(entity);
        if (o != null) o.heldAt = o.level.getGameTime();
    }

    /** Ice Blast is touching {@code entity}: a wildfire's heart is held back by it. */
    public static void cooled(LivingEntity entity) {
        Overload o = of(entity);
        if (o != null && o.storm == Storm.WILDFIRE) hold(entity);
    }

    /**
     * Whether {@code entity} has {@code power}: a player's own awakened power ({@code FightingStyles.hasPower}, the one
     * question for that), or a gifted mob carrying it.
     */
    public static boolean holds(LivingEntity entity, ResourceLocation power) {
        if (entity instanceof ServerPlayer p) return com.jayptucker.heroesevolved.combat.FightingStyles.hasPower(p, power);
        return GiftedMobs.isGifted(entity) && power.equals(GiftedMobs.powerOf(entity));
    }

    /** The storm can't touch anyone who has its own power, or anyone who can't be hurt (spectators, creative). */
    static boolean spared(Overload o, Entity e) {
        if (e == o.villager || !e.isAlive() || e.isSpectator() || e instanceof net.minecraft.world.entity.player.Player p && p.getAbilities().invulnerable)
            return true;
        return e instanceof LivingEntity living && holds(living, o.storm.power);
    }

    // ------------------------------------------------------------------
    // Starting one
    // ------------------------------------------------------------------

    /**
     * A gift waking good, from {@code Renegades.awaken}: if it is a storm's and the roll says so, it overloads instead of
     * making them a guardian. True if it did.
     */
    public static boolean wake(Villager villager, ResourceLocation power) {
        Storm storm = Storm.of(power);
        if (storm == null || villager.getRandom().nextDouble() >= HeroesEvolvedConfig.COMMON.overloadChance.get()) return false;
        return start(villager, storm, HeroesEvolvedConfig.COMMON.overloadTicks.get()) != null;
    }

    /**
     * {@code villager}'s gift overloads into {@code storm} now, with {@code fuse} ticks to the break. Their gift becomes
     * the storm's power. Null if they can't (a baby, already in trouble).
     */
    public static Overload start(Villager villager, Storm storm, int fuse) {
        if (!(villager.level() instanceof ServerLevel level) || villager.isBaby() || !villager.isAlive()
                || ACTIVE.containsKey(villager.getUUID()) || GiftedMobs.isGifted(villager)
                || com.jayptucker.heroesevolved.village.VillagerMeltdown.critical(villager)) return null;
        GiftedVillagers.give(villager, storm.power);
        Overload o = new Overload(villager, level, storm, Villages.bell(level, villager.blockPosition()), Math.max(1, fuse), Math.max(1, fuse));
        o.stageAt = level.getGameTime();
        begin(o, true);
        save(o);
        return o;
    }

    /** Overloads the nearest grown villager within {@link #NEAR} of {@code at}; {@code storm} null picks from their gift. */
    public static Overload startNear(ServerLevel level, Vec3 at, Storm storm, int fuse) {
        Villager villager = level.getEntitiesOfClass(Villager.class, new AABB(at, at).inflate(NEAR),
                        v -> v.isAlive() && !v.isBaby() && !critical(v) && !GiftedMobs.isGifted(v)
                                && !com.jayptucker.heroesevolved.village.Villagers.inStasis(v))
                .stream().min(Comparator.comparingDouble(v -> v.distanceToSqr(at))).orElse(null);
        if (villager == null) return null;
        if (storm == null) storm = Storm.of(GiftedVillagers.gift(villager));
        if (storm == null) storm = Storm.byId(level.random.nextInt(Storm.values().length));
        return start(villager, storm, fuse);
    }

    private static void begin(Overload o, boolean fresh) {
        ACTIVE.put(o.villager.getUUID(), o);
        root(o.villager, true);
        gather(o);
        VillageTickets.hold(o.level, o.bell, key(o));
        o.call = EmergencyBand.call(o.level, o.bell, o.storm.trouble, words(o), o.fuse + OverloadGeometry.BREAK_WARNING
                + OverloadGeometry.BURNOUT + 200);
        weather(o.storm).begin(o);
        if (fresh) {
            Vec3 at = o.villager.position();
            o.level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 2.0F, 0.6F);
            o.level.playSound(null, at.x, at.y, at.z, SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 1.2F, 0.8F);
            for (ServerPlayer p : watchers(o)) p.sendSystemMessage(Component.literal(o.storm.told).withStyle(ChatFormatting.GOLD));
        }
    }

    // ------------------------------------------------------------------
    // Running it
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (Villager v : List.copyOf(RESUMING)) resume(v);
        RESUMING.clear();
        for (Overload o : List.copyOf(ACTIVE.values())) if (o.level.getServer() == server) tick(o);
    }

    static void tick(Overload o) {
        Villager v = o.villager;
        if (v.isRemoved()) {
            // Unloaded or gone elsewhere: its data carries the countdown, and it resumes when they are back.
            if (v.isAlive()) pause(o);
            return;
        }
        long now = o.level.getGameTime();
        Weather weather = weather(o.storm);
        o.age++;
        // Nobody sleeps through their own storm.
        if (v.isSleeping()) v.stopSleeping();
        switch (o.stage) {
            case GROWING, BREAKING -> {
                if (Dampening.dampened(v)) { dampen(o); return; }
                calm(o);
                if (o.calm >= 1) { calmed(o); return; }
                if (o.stage == Overload.Stage.GROWING) {
                    boolean held = CryoService.isFrozen(v) || TimeStopService.isStopped(v)
                            || o.heldAt >= now - 1 || weather.held(o);
                    if (!held) o.fuse--;
                    if (o.fuse <= 0) {
                        o.fuse = 0;
                        stage(o, Overload.Stage.BREAKING);
                        for (ServerPlayer p : watchers(o))
                            p.displayClientMessage(Component.literal("It's breaking!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                        o.level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.NEUTRAL, 3.0F, 0.6F);
                    }
                } else if (o.stageAge() >= OverloadGeometry.BREAK_WARNING) {
                    weather.breaks(o);
                    stage(o, Overload.Stage.BURNING);
                }
            }
            case BURNING -> {
                if (o.stageAge() >= OverloadGeometry.BURNOUT) { burntOut(o); return; }
            }
            case DYING -> {
                if (o.stageAge() >= OverloadGeometry.DIE_AWAY) { settle(o); return; }
            }
        }
        weather.tick(o, now);
        if (now % GATHER_EVERY == 0) gather(o);
        if (now % 5 == 0 && o.harms()) village(o, weather, now);
        if (now % SYNC_EVERY == 0) sync(o, false);
        if (now % SAVE_EVERY == 0) save(o);
        if (now % 20 == 0) {
            o.call.say(words(o));
            bar(o);
        }
    }

    private static void stage(Overload o, Overload.Stage stage) {
        o.stage = stage;
        o.stageAt = o.level.getGameTime();
        sync(o, false);
    }

    /**
     * Everyone near enough calms them: the nearest few count most (the first in full, each other half), with the same
     * power, as an Empathic Mimic or as a telepath twice as much; a telepath reaches them from further off. With nobody
     * there the calm slips back.
     */
    private static void calm(Overload o) {
        o.helpers.clear();
        List<Float> rates = new ArrayList<>();
        Villager v = o.villager;
        double reach = OverloadGeometry.MIND_REACH + 1;
        for (ServerPlayer p : o.level.players()) {
            if (!p.isAlive() || p.isSpectator() || p.distanceToSqr(v) > reach * reach) continue;
            double d = p.distanceTo(v);
            boolean telepath = holds(p, ModAbilities.TELEPATHY_ID);
            boolean near = d <= OverloadGeometry.CALM_REACH;
            if (!near && !(telepath && d <= OverloadGeometry.MIND_REACH && p.hasLineOfSight(v))) continue;
            float rate = 1;
            if (telepath || holds(p, o.storm.power) || holds(p, ModAbilities.MIMICRY_ID)
                    || o.storm == Storm.BLIZZARD && holds(p, ModAbilities.PYROKINESIS_ID)) rate = 2;
            o.helpers.add(p);
            rates.add(rate);
        }
        rates.sort(Comparator.reverseOrder());
        float total = 0;
        for (int i = 0; i < rates.size(); i++) total += i == 0 ? rates.get(i) : rates.get(i) * 0.5F;
        o.calm = Mth.clamp(o.calm + (total > 0 ? total : -OverloadGeometry.CALM_SLIP) / OverloadGeometry.CALM_TICKS, 0, 1);
        if (total > 0 && o.level.getGameTime() % 10 == 0)
            for (ServerPlayer p : o.helpers)
                p.displayClientMessage(Component.literal("Calming them  " + Math.round(o.calm * 100) + "%").withStyle(ChatFormatting.YELLOW), true);
    }

    /** Its village in its reach glows and runs (or shelters), and someone near a player shouts. */
    private static void village(Overload o, Weather weather, long now) {
        double reach = weather.reach(o) + 6;
        List<Villager> danger = new ArrayList<>();
        for (UUID id : o.village) {
            if (!(o.level.getEntity(id) instanceof Villager v) || !v.isAlive() || EntityCarryService.isBeingCarried(v)) continue;
            if (v.distanceToSqr(o.eye()) > reach * reach || !endangered(o, v)) {
                if (v.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.HIDING_PLACE))
                    Shouts.shelterOrRun(v, o.eye(), reach, o.storm == Storm.BLIZZARD);
                continue;
            }
            danger.add(v);
            v.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20, 0, false, false, false));
        }
        weather.villagers(o, danger);
        if (now >= o.nextShout && !danger.isEmpty()) {
            List<Villager> heard = danger.stream().filter(v -> o.level.players().stream()
                    .anyMatch(p -> !p.isSpectator() && p.distanceToSqr(v) <= 32 * 32)).toList();
            if (!heard.isEmpty()) {
                String[] lines = SHOUTS.get(o.storm);
                Villager who = heard.get(o.level.random.nextInt(heard.size()));
                Shouts.say(o.level, who, lines[o.shouts++ == 0 ? 0 : o.level.random.nextInt(lines.length)], SHOUT_TICKS);
            }
            o.nextShout = now + SHOUT_EVERY;
        }
    }

    /** A blizzard only endangers those out in the open; the others, anyone in its reach. */
    private static boolean endangered(Overload o, Villager v) {
        return o.storm != Storm.BLIZZARD || Blizzard.exposed(o.level, v);
    }

    /** Adds to the village everyone who belongs to it now; nobody is taken off, so the count never shrinks. */
    static void gather(Overload o) {
        for (Villager v : Villages.members(o.level, o.bell)) if (v != o.villager) o.village.add(v.getUUID());
    }

    private static void bar(Overload o) {
        int seconds = (o.fuse + 19) / 20;
        String time = switch (o.stage) {
            case GROWING -> seconds / 60 + ":" + String.format("%02d", seconds % 60);
            case BREAKING -> "Breaking!";
            case BURNING -> "Burning out";
            case DYING -> "Dying away";
        };
        o.bar.setName(Component.literal(o.storm.title + "  " + time + "     Calm " + Math.round(o.calm * 100) + "%     Village "
                + o.alive() + "/" + o.village.size()));
        o.bar.setProgress(Mth.clamp(o.stage == Overload.Stage.GROWING ? 1 - o.progress() : o.power(), 0, 1));
        boolean late = o.stage == Overload.Stage.GROWING && o.fuse < 200 || o.stage == Overload.Stage.BREAKING || o.stage == Overload.Stage.BURNING;
        o.bar.setColor(late ? BossEvent.BossBarColor.RED : o.storm.color);
        Set<ServerPlayer> near = new HashSet<>(watchers(o));
        for (ServerPlayer p : List.copyOf(o.bar.getPlayers())) if (!near.contains(p)) o.bar.removePlayer(p);
        for (ServerPlayer p : near) o.bar.addPlayer(p);
    }

    private static Component words(Overload o) {
        int seconds = (o.fuse + 19) / 20;
        return Component.literal(o.storm.words + (o.stage == Overload.Stage.GROWING ? " " + seconds / 60 + ":" + String.format("%02d", seconds % 60) : ""));
    }

    /** Players near the village or the storm. */
    static List<ServerPlayer> watchers(Overload o) {
        List<ServerPlayer> out = new ArrayList<>();
        int range = OverloadGeometry.WATCH;
        for (ServerPlayer p : o.level.players())
            if (!p.isSpectator() && (p.blockPosition().distSqr(o.bell) <= (long) range * range || p.distanceToSqr(o.villager) <= range * range))
                out.add(p);
        return out;
    }

    /** Tells everyone near enough to see it how the storm stands. */
    static void sync(Overload o, boolean ended) {
        Weather weather = weather(o.storm);
        int[] helpers = o.helpers.stream().mapToInt(Entity::getId).toArray();
        double range = OverloadGeometry.WATCH + 96;
        for (ServerPlayer p : o.level.players()) {
            if (!ended && p.distanceToSqr(o.villager) > range * range) continue;
            PacketDistributor.sendToPlayer(p, new OverloadPayload(o.villager.getId(), o.storm.ordinal(), o.eye(), weather.target(o),
                    o.progress(), o.power(), o.calm, o.stage.ordinal(), o.stageAge(), o.age, weather.sees(o, p), helpers, ended));
        }
    }

    // ------------------------------------------------------------------
    // How it ends
    // ------------------------------------------------------------------

    /** They are calm: the storm dies away, and they keep the gift. */
    private static void calmed(Overload o) {
        o.calm = 1;
        o.calmer = o.helpers.isEmpty() ? null : o.helpers.getFirst();
        o.calmedBy = o.calmer == null ? "Someone" : o.calmer.getName().getString();
        stage(o, Overload.Stage.DYING);
        Villager v = o.villager;
        o.level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 2.0F, 0.8F);
        o.level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.5F, 1.2F);
        for (ServerPlayer p : watchers(o))
            p.sendSystemMessage(Component.literal(o.calmedBy + " calmed them down. The " + o.storm.title.toLowerCase() + " is dying away.")
                    .withStyle(ChatFormatting.GREEN));
    }

    /** An Ability Dampener switched their gift off: the storm dies away with it. */
    private static void dampen(Overload o) {
        o.dampened = true;
        stage(o, Overload.Stage.DYING);
        o.level.playSound(null, o.villager.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 2.0F, 0.5F);
        for (ServerPlayer p : watchers(o))
            p.sendSystemMessage(Component.literal("The dampener switched their gift off. The storm is dying, and the gift with it.")
                    .withStyle(ChatFormatting.GOLD));
    }

    /** The storm has died away: a calmed villager guards their village with the gift; a dampened one is ordinary now. */
    private static void settle(Overload o) {
        List<ServerPlayer> watching = watchers(o);
        finish(o);
        Villager v = o.villager;
        if (o.dampened) {
            GiftedVillagers.give(v, null);
            tally(o, watching, false);
            remember(o, watching, null);
            return;
        }
        for (ServerPlayer p : watching)
            if (p.isAlive()) p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 20 * 60 * 40, 0, false, false, true));
        tally(o, watching, false);
        remember(o, watching, o.calmer);
        Renegades.guard(v, o.storm.power);
    }

    /** It broke and burnt itself out: they collapse, alive, and the gift is gone. */
    private static void burntOut(Overload o) {
        List<ServerPlayer> watching = watchers(o);
        finish(o);
        Villager v = o.villager;
        GiftedVillagers.give(v, null);
        v.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 60, 1));
        v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 30, 1));
        o.level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_DEATH, SoundSource.NEUTRAL, 1.0F, 1.4F);
        tally(o, watching, true);
        remember(o, watching, null);
    }

    /** Its village remembers it: saved if everyone lived (or it was calmed), fallen if nobody did. */
    private static void remember(Overload o, List<ServerPlayer> watching, ServerPlayer hero) {
        int alive = o.alive(), all = o.village.size();
        var outcome = all > 0 && alive == 0 ? com.jayptucker.heroesevolved.village.VillageMemory.Outcome.FALLEN
                : alive == all || hero != null ? com.jayptucker.heroesevolved.village.VillageMemory.Outcome.SAVED
                : com.jayptucker.heroesevolved.village.VillageMemory.Outcome.HIT;
        com.jayptucker.heroesevolved.village.VillageMemory.record(o.level, o.bell, "the " + o.storm.title.toLowerCase(), outcome, hero, watching, alive, all);
    }

    private static void tally(Overload o, List<ServerPlayer> watching, boolean broke) {
        int alive = o.alive(), all = o.village.size();
        boolean everyone = alive == all;
        String head = broke ? "The " + o.storm.title.toLowerCase() + " has burnt itself out, and their gift with it. " : "";
        for (ServerPlayer p : watching) {
            if (p.isRemoved()) continue;
            p.sendSystemMessage(Component.literal(head + (all == 0 ? "" : everyone ? "All " + all + " of the village made it." : alive + " of " + all + " of the village made it."))
                    .withStyle(everyone ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        }
    }

    /** Ends an overload with nothing happening at all (admins, tests). */
    public static void cancel(Entity entity) {
        Overload o = of(entity);
        if (o != null) finish(o);
    }

    /** Ends every overload with nothing happening. How many there were. */
    public static int cancelAll() {
        for (Villager v : List.copyOf(RESUMING)) resume(v);
        RESUMING.clear();
        List<Overload> all = all();
        for (Overload o : all) finish(o);
        return all.size();
    }

    /** One line on the overloads under way, for the admin menu. */
    public static String describe() {
        List<Overload> all = all();
        if (all.isEmpty()) return "No overload under way.";
        Overload o = all.getFirst();
        int seconds = (o.fuse + 19) / 20;
        return o.storm.title + " (bell " + o.bell.getX() + ", " + o.bell.getZ() + "): " + o.stage.name().toLowerCase() + ", "
                + seconds / 60 + ":" + String.format("%02d", seconds % 60) + " left, calm " + Math.round(o.calm * 100) + "%"
                + (all.size() > 1 ? " (+" + (all.size() - 1) + " more)" : "") + ".";
    }

    /** It is over, either way: everything it set going stops. */
    private static void finish(Overload o) {
        ACTIVE.remove(o.villager.getUUID());
        o.villager.getPersistentData().remove(TAG);
        root(o.villager, false);
        weather(o.storm).end(o);
        for (UUID id : o.village) if (o.level.getEntity(id) instanceof Villager v) v.removeEffect(MobEffects.GLOWING);
        if (o.call != null) EmergencyBand.end(o.call);
        VillageTickets.release(key(o));
        o.bar.removeAllPlayers();
        sync(o, true);
    }

    /** Off the map for now (unloaded): stop the call and the bar; their data still has the countdown. */
    private static void pause(Overload o) {
        ACTIVE.remove(o.villager.getUUID());
        if (o.call != null) EmergencyBand.end(o.call);
        VillageTickets.release(key(o));
        o.bar.removeAllPlayers();
        sync(o, true);
    }

    private static String key(Overload o) { return "overload:" + o.villager.getUUID(); }

    /** They can't walk out of it. */
    private static void root(Villager v, boolean on) {
        AttributeInstance speed = v.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (on) speed.addOrUpdateTransientModifier(new AttributeModifier(ROOTED, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        else speed.removeModifier(ROOTED);
    }

    // ------------------------------------------------------------------
    // Keeping it through unloading and restarts
    // ------------------------------------------------------------------

    private static void save(Overload o) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("storm", o.storm.ordinal());
        tag.putInt("fuse", o.fuse);
        tag.putInt("total", o.total);
        tag.putFloat("calm", o.calm);
        tag.putInt("stage", o.stage.ordinal());
        tag.putInt("stageAge", o.stageAge());
        tag.putInt("age", o.age);
        tag.putBoolean("dampened", o.dampened);
        tag.putLong("bell", o.bell.asLong());
        ListTag village = new ListTag();
        for (UUID id : o.village) village.add(NbtUtils.createUUID(id));
        tag.put("village", village);
        weather(o.storm).save(o, tag);
        o.villager.getPersistentData().put(TAG, tag);
    }

    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Villager v && v.getPersistentData().contains(TAG))
            RESUMING.add(v);
    }

    private static void resume(Villager v) {
        if (v.isRemoved() || !v.isAlive() || ACTIVE.containsKey(v.getUUID()) || !(v.level() instanceof ServerLevel level)) return;
        CompoundTag tag = v.getPersistentData().getCompound(TAG);
        Storm storm = Storm.byId(tag.getInt("storm"));
        if (storm == null) { v.getPersistentData().remove(TAG); return; }
        Overload o = new Overload(v, level, storm, BlockPos.of(tag.getLong("bell")), Math.max(1, tag.getInt("total")), Math.max(0, tag.getInt("fuse")));
        o.calm = tag.getFloat("calm");
        Overload.Stage[] stages = Overload.Stage.values();
        o.stage = stages[Mth.clamp(tag.getInt("stage"), 0, stages.length - 1)];
        o.stageAt = level.getGameTime() - tag.getInt("stageAge");
        o.age = tag.getInt("age");
        o.dampened = tag.getBoolean("dampened");
        for (net.minecraft.nbt.Tag id : tag.getList("village", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) o.village.add(NbtUtils.loadUUID(id));
        weather(storm).load(o, tag);
        begin(o, false);
    }

    // ------------------------------------------------------------------
    // Hurting them; their death
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void hurt(LivingIncomingDamageEvent event) {
        Overload o = of(event.getEntity());
        if (o == null || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        event.setCanceled(true);
        if (o.stage != Overload.Stage.GROWING || CryoService.isApplyingColdDamage() || event.getSource().is(DamageTypeTags.IS_FREEZING)) return;
        long now = o.level.getGameTime();
        if (now - o.hitAt < 20 || !(event.getSource().getEntity() instanceof LivingEntity)) return;
        o.hitAt = now;
        o.fuse = Math.max(1, o.fuse - OverloadGeometry.HIT_PENALTY);
        o.level.playSound(null, o.villager.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 1.5F, 1.6F);
        if (event.getSource().getEntity() instanceof ServerPlayer p)
            p.displayClientMessage(Component.literal("Hurting them only makes it worse.").withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent
    public static void died(LivingDeathEvent event) {
        Overload o = of(event.getEntity());
        if (o != null) {
            List<ServerPlayer> watching = watchers(o);
            finish(o);
            for (ServerPlayer p : watching)
                p.sendSystemMessage(Component.literal("They're gone, and the storm with them.").withStyle(ChatFormatting.GRAY));
            return;
        }
        UUID id = event.getEntity().getUUID();
        for (Overload x : ACTIVE.values()) if (x.village.contains(id)) x.dead.add(id);
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) { clear(); }

    /** Forgets every overload without ending any (their villagers keep their countdowns). */
    public static void clear() {
        for (Overload o : List.copyOf(ACTIVE.values())) {
            pause(o);
            weather(o.storm).end(o);
        }
        ACTIVE.clear();
        RESUMING.clear();
    }

    // ------------------------------------------------------------------
    // For tests and previews
    // ------------------------------------------------------------------

    /** Brings the break forward: {@code ticks} left on the countdown. */
    public static void hurry(Overload o, int ticks) { o.fuse = Math.max(1, Math.min(o.fuse, ticks)); }

    /** Sets how much of the countdown has run (previews). */
    public static void progress(Overload o, float progress) { o.fuse = Math.max(1, Math.round(o.total * (1 - Mth.clamp(progress, 0, 1)))); }

    /** Sets how calm they are (previews). */
    public static void calm(Overload o, float calm) { o.calm = Mth.clamp(calm, 0, 0.999F); }
}
