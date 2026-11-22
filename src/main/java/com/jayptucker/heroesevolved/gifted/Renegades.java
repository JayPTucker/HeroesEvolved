package com.jayptucker.heroesevolved.gifted;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.mimicry.GiftedVillagers;
import com.jayptucker.heroesevolved.mimicry.MimicColors;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.radio.EmergencyBand;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.village.VillageTickets;
import com.jayptucker.heroesevolved.village.VillagerMeltdown;
import com.jayptucker.heroesevolved.village.Villages;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
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
 * A villager's gift waking. Half the time ({@link HeroesEvolvedConfig.Common#giftedEvilChance}) it goes to their head:
 * they turn into an illager, keep the power, and lay siege to their own village. Otherwise they become its guardian.
 * A radioactive villager whose gift wakes good can't hold it: that is a meltdown ({@link VillagerMeltdown}).
 * <p>
 * A renegade's siege (a {@link Terror}) is a world event: the bell rings and the village hides, a distress call goes out
 * on the emergency band and lights a flare that can be seen for hundreds of blocks, and the village is kept loaded while
 * it lasts. The renegade takes one of its village every {@link HeroesEvolvedConfig.Common#renegadeKillSeconds} seconds
 * (between those, its blows on them don't land), so players have several minutes to arrive. Only players can really
 * hurt it ({@code GiftedMobs.incoming}). Killing it saves whoever is left; if nobody is left, the village has fallen and
 * the renegade stays there.
 * <p>
 * Its state is saved on the renegade ({@link #TAG}), so the siege carries on through a restart or an unloaded chunk.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Renegades {
    public static final String TAG = HeroesEvolved.MOD_ID + "_terror";
    /** Players this near the bell or the renegade see what happens. */
    static final int WATCH = 112;
    /** The calls stop after this long; the renegade stays. */
    static final int TIME_LIMIT = 20 * 60 * 15;
    /** The bell is rung again this often, so the village keeps hiding. */
    static final int RING_EVERY = 600;

    /** One renegade's siege of its village. */
    public static final class Terror {
        final Mob renegade;
        final ServerLevel level;
        final BlockPos bell;
        final Set<UUID> village = new LinkedHashSet<>();
        final Set<UUID> dead = new HashSet<>();
        long startedAt, nextKill;
        EmergencyBand.Call call;
        Terror(Mob renegade, ServerLevel level, BlockPos bell, long startedAt, long nextKill) {
            this.renegade = renegade; this.level = level; this.bell = bell; this.startedAt = startedAt; this.nextKill = nextKill;
        }
        public Mob renegade() { return renegade; }
        public BlockPos bell() { return bell; }
        public int villageSize() { return village.size(); }
        public EmergencyBand.Call call() { return call; }
        public long nextKill() { return nextKill; }
        /** Alive now: not seen dying (an unloaded villager counts as alive). */
        public int alive() {
            int alive = 0;
            for (UUID id : village) {
                if (dead.contains(id)) continue;
                Entity e = level.getEntity(id);
                if (e == null || e.isAlive()) alive++;
            }
            return alive;
        }
        String key() { return "terror:" + renegade.getUUID(); }
    }

    private static final Map<UUID, Terror> TERRORS = new HashMap<>();
    private static final List<Mob> RESUMING = new ArrayList<>();

    private Renegades() {}

    public static Terror of(Entity renegade) {
        Terror t = TERRORS.get(renegade.getUUID());
        return t != null && t.renegade == renegade ? t : null;
    }

    public static List<Terror> all() { return List.copyOf(TERRORS.values()); }

    /**
     * Whether a gifted mob's blow may land on this villager now. Anyone not laying siege may; a renegade only when its
     * village's next turn has come, and only on its own village (strangers passing through are fair game).
     */
    public static boolean mayHurt(Mob attacker, LivingEntity villager) {
        Terror t = of(attacker);
        if (t == null || !(villager instanceof AbstractVillager)) return true;
        if (!t.village.contains(villager.getUUID())) return true;
        return t.level.getGameTime() >= t.nextKill;
    }

    /** The village count for a renegade's boss bar ("Village 7/9"), or null. */
    static String barSuffix(Mob mob) {
        Terror t = of(mob);
        return t == null ? null : "Village " + t.alive() + "/" + t.village.size();
    }

    // ------------------------------------------------------------------
    // A gift waking
    // ------------------------------------------------------------------

    /** Wakes {@code villager}'s gift (giving one if they had none), rolling which way it goes. What they became. */
    public static Mob awaken(Villager villager) {
        boolean evil = villager.getRandom().nextDouble() < HeroesEvolvedConfig.COMMON.giftedEvilChance.get();
        return awaken(villager, evil);
    }

    /**
     * Wakes {@code villager}'s gift one way or the other. Evil: they begin to turn ({@link #turn}; the renegade appears
     * {@link #TURN_TICKS} later, {@link #turnedFrom}). Good: they guard their village, unless the gift is Induced
     * Radioactivity, which nobody good can hold (a meltdown). Returns the villager, or null if they can't (a baby).
     */
    public static Mob awaken(Villager villager, boolean evil) {
        if (!(villager.level() instanceof ServerLevel level) || villager.isBaby() || !villager.isAlive() || GiftedMobs.isGifted(villager)
                || VillagerMeltdown.critical(villager) || com.jayptucker.heroesevolved.village.overload.Overloads.critical(villager)
                || turning(villager)) return null;
        ResourceLocation power = GiftedVillagers.gift(villager);
        if (power == null) {
            power = GiftedMobs.randomPower(villager.getRandom());
            GiftedVillagers.give(villager, power);
        }
        if (evil) return turn(villager, power, Rarity.roll(villager.getRandom(), Rarity.RARE));
        if (power.equals(ModAbilities.RADIATION_ID))
            return VillagerMeltdown.start(villager, HeroesEvolvedConfig.COMMON.villagerMeltdownFuseTicks.get()) != null ? villager : null;
        // A storm's gift may wake stronger than they can hold: an overload, until someone calms them.
        if (com.jayptucker.heroesevolved.village.overload.Overloads.wake(villager, power)) return villager;
        return guard(villager, power);
    }

    /**
     * {@code villager}'s gift wakes good and under control: they guard their village with it. Also how a calmed overload
     * ends ({@code village/overload/Overloads}). Returns the villager.
     */
    public static Mob guard(Villager villager, ResourceLocation power) {
        return villager.level() instanceof ServerLevel level ? guard(villager, power, Villages.bell(level, villager.blockPosition())) : null;
    }

    /** As {@link #guard(Villager, ResourceLocation)}, guarding the village round {@code bell} (somewhere they aren't now). */
    public static Mob guard(Villager villager, ResourceLocation power, BlockPos bell) {
        if (!(villager.level() instanceof ServerLevel level) || !villager.isAlive() || GiftedMobs.isGifted(villager)) return null;
        GiftedMob g = GiftedMobs.bestow(villager, power, Rarity.roll(villager.getRandom(), Rarity.UNCOMMON), false, Gift.Origin.GUARDIAN, bell, null);
        Vec3 at = villager.position();
        SetPiecePayload.send(level, Piece.GIFT_AWAKEN, at, at.add(0, 1, 0), MimicColors.index(power.getPath()) + .25F, villager);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.2F, 1);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1, 1.4F);
        for (ServerPlayer p : g.near(96))
            p.sendSystemMessage(Component.literal("A villager's gift has woken: ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(g.gift().name()).withStyle(g.gift().rarity().color))
                    .append(Component.literal(" (" + g.gift().powerName() + ") will guard this village.").withStyle(ChatFormatting.GREEN)));
        return villager;
    }

    /** The illager a renegade becomes: brutes for the powers that fight hand to hand, illusionists for the mind's. */
    static EntityType<? extends Raider> illager(ResourceLocation power) {
        String p = power.getPath();
        return switch (p) {
            case "superspeed", "phasing", "regeneration", "quake", "shapeshifting" -> EntityType.VINDICATOR;
            case "telepathy", "invisibility", "time_manipulation", "empathic_mimicry", "vibe" -> EntityType.ILLUSIONER;
            default -> EntityType.PILLAGER;
        };
    }

    /**
     * How long a turn takes before the body changes: crimson cracks climb the villager, a ring closes in on them, and
     * the swap to an illager happens under the flash at the end ({@code GiftArt.turn} draws the same timeline), so the
     * player sees a villager turn rather than an illager appear.
     */
    public static final int TURN_TICKS = 21;
    private static final ResourceLocation TURN_ROOT = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "renegade_turning");

    /** A villager in the middle of turning. */
    private record Turning(Villager villager, ResourceLocation power, Rarity rarity, String name, BlockPos bell, long at) {}
    private static final List<Turning> TURNING = new ArrayList<>();
    /** Who each turned villager became (by the villager's id), while their siege lasts: tests and previews look them up. */
    private static final Map<UUID, Mob> TURNED = new HashMap<>();

    /** Is this villager in the middle of turning? */
    public static boolean turning(Entity villager) {
        for (Turning t : TURNING) if (t.villager == villager) return true;
        return false;
    }

    /** The renegade a villager turned into, while its siege lasts, or null. */
    public static Mob turnedFrom(UUID villager) { return TURNED.get(villager); }

    /**
     * The gift begins to turn {@code villager}: cracks climb them where they stand (they can't walk away), and
     * {@link #TURN_TICKS} later they become an illager carrying it and lay siege to their own village. Returns the
     * villager while they turn, or null if it couldn't happen.
     */
    public static Mob turn(Villager villager, ResourceLocation power, Rarity rarity) {
        if (!(villager.level() instanceof ServerLevel level) || villager.isBaby() || !villager.isAlive() || turning(villager)) return null;
        BlockPos bell = Villages.bell(level, villager.blockPosition());
        Vec3 at = villager.position();
        String name = villager.hasCustomName() ? villager.getCustomName().getString() + " " + GiftNames.roll(power, villager.getRandom()).split(" ", 2)[1]
                : GiftNames.roll(power, villager.getRandom());
        SetPiecePayload.send(level, Piece.GIFT_TURN, at, at.add(0, 1, 0), MimicColors.index(power.getPath()), villager);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.HOSTILE, 1.4F, .5F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 1.2F, .6F);
        var speed = villager.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(TURN_ROOT, -1,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        villager.getNavigation().stop();
        TURNING.add(new Turning(villager, power, rarity, name, bell, level.getGameTime() + TURN_TICKS));
        return villager;
    }

    /** The flash at the end of a turn: the villager becomes the renegade. */
    private static Mob convert(Turning t) {
        Villager villager = t.villager;
        if (!(villager.level() instanceof ServerLevel level) || villager.isRemoved() || !villager.isAlive()) return null;
        Vec3 at = villager.position();
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.HOSTILE, 1.4F, .6F);
        villager.setCustomName(null);
        com.jayptucker.heroesevolved.village.Villagers.leaving(villager);
        Raider renegade = villager.convertTo(illager(t.power), false);
        if (renegade == null) return null;
        net.neoforged.neoforge.event.EventHooks.onLivingConvert(villager, renegade);
        renegade.finalizeSpawn(level, level.getCurrentDifficultyAt(renegade.blockPosition()), MobSpawnType.CONVERSION, null);
        renegade.setCanJoinRaid(false);
        renegade.setPatrolLeader(false);
        GiftedMobs.bestow(renegade, t.power, t.rarity, true, Gift.Origin.RENEGADE, t.bell, t.name);
        renegade.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 60, 0, false, false));
        TURNED.put(villager.getUUID(), renegade);
        begin(renegade, level, t.bell, true);
        return renegade;
    }

    private static void tickTurning(MinecraftServer server) {
        if (TURNING.isEmpty()) return;
        for (Turning t : List.copyOf(TURNING)) {
            if (t.villager.getServer() != server) continue;
            // Killed or gone while turning: nothing becomes of it.
            if (t.villager.isRemoved() || !t.villager.isAlive()) { TURNING.remove(t); continue; }
            if (t.villager.level().getGameTime() < t.at) continue;
            TURNING.remove(t);
            convert(t);
        }
    }

    // ------------------------------------------------------------------
    // The siege
    // ------------------------------------------------------------------

    private static Terror begin(Mob renegade, ServerLevel level, BlockPos bell, boolean fresh) {
        long now = level.getGameTime();
        Terror t = new Terror(renegade, level, bell, now, now + killInterval());
        if (!fresh) load(t);
        TERRORS.put(renegade.getUUID(), t);
        gather(t);
        VillageTickets.hold(level, bell, t.key());
        t.call = EmergencyBand.call(level, bell, EmergencyBand.Trouble.RENEGADE, words(t),
                (int) Math.max(200, TIME_LIMIT - (now - t.startedAt) + 200));
        if (fresh) {
            ring(t);
            Gift gift = Gift.read(renegade);
            for (ServerPlayer p : watchers(t))
                p.sendSystemMessage(Component.literal((gift != null ? gift.name() : "A villager") + " has turned on their own village!")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        }
        save(t);
        return t;
    }

    static int killInterval() { return HeroesEvolvedConfig.COMMON.renegadeKillSeconds.get() * 20; }

    /** Everyone who belongs to the village now; nobody is ever taken off, so the count never shrinks. */
    static void gather(Terror t) {
        for (Villager v : Villages.members(t.level, t.bell)) t.village.add(v.getUUID());
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (Mob m : List.copyOf(RESUMING)) resume(m);
        RESUMING.clear();
        tickTurning(server);
        for (Terror t : List.copyOf(TERRORS.values())) if (t.level.getServer() == server) tick(t);
    }

    static void tick(Terror t) {
        Mob r = t.renegade;
        if (r.isRemoved()) {
            // Unloaded or gone elsewhere: its data carries the siege, and it resumes when it is back.
            if (r.isAlive()) pause(t);
            return;
        }
        long now = t.level.getGameTime();
        if (now % 20 != 0) return;
        gather(t);
        int alive = t.alive();
        if (!t.village.isEmpty() && alive == 0) { fallen(t); return; }
        if (now - t.startedAt > TIME_LIMIT) { quiet(t); return; }
        if (now % RING_EVERY == 0) ring(t);
        // Its turn has come: it goes hunting for one of them.
        if (now >= t.nextKill && (r.getTarget() == null || !(r.getTarget() instanceof AbstractVillager))) hunt(t);
        if (t.call != null) t.call.say(words(t));
        save(t);
    }

    /** It picks out the nearest of its village. */
    private static void hunt(Terror t) {
        Mob r = t.renegade;
        Villager prey = null;
        double best = Double.MAX_VALUE;
        for (Villager v : t.level.getEntitiesOfClass(Villager.class, r.getBoundingBox().inflate(48), v -> v.isAlive() && t.village.contains(v.getUUID()))) {
            double d = v.distanceToSqr(r);
            if (d < best) { best = d; prey = v; }
        }
        if (prey != null && !(r.getTarget() instanceof net.minecraft.world.entity.player.Player)) r.setTarget(prey);
    }

    /** The bell rings: the village hides, and raiders near the bell glow. */
    private static void ring(Terror t) {
        var state = t.level.getBlockState(t.bell);
        if (state.is(Blocks.BELL) && state.getBlock() instanceof BellBlock bell) bell.attemptToRing(t.renegade, t.level, t.bell, null);
        else t.level.playSound(null, t.bell, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2, 1);
    }

    private static Component words(Terror t) {
        Gift gift = Gift.read(t.renegade);
        String who = gift != null ? gift.name() : "One of us";
        return Component.literal("Help! " + who + " has turned on us! " + t.alive() + " of " + t.village.size() + " still alive.");
    }

    static List<ServerPlayer> watchers(Terror t) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : t.level.players())
            if (!p.isSpectator() && (p.blockPosition().distSqr(t.bell) <= (long) WATCH * WATCH || p.distanceToSqr(t.renegade) <= WATCH * WATCH))
                out.add(p);
        return out;
    }

    // ------------------------------------------------------------------
    // How it ends
    // ------------------------------------------------------------------

    /** The renegade died: whoever is left is saved. Called from {@code GiftedMobs.died}. */
    static void died(Mob mob, DamageSource source) {
        Terror t = of(mob);
        if (t == null) return;
        int alive = t.alive();
        List<ServerPlayer> watching = watchers(t);
        finish(t);
        for (UUID id : t.village)
            if (t.level.getEntity(id) instanceof Villager v && v.isAlive())
                t.level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1, 1);
        if (alive > 0) {
            Gift gift = Gift.read(t.renegade);
            com.jayptucker.heroesevolved.village.VillageMemory.record(t.level, t.bell, gift != null ? gift.name() : "a renegade",
                    com.jayptucker.heroesevolved.village.VillageMemory.Outcome.SAVED,
                    source.getEntity() instanceof ServerPlayer killer ? killer : null, watching, alive, t.village.size());
        }
        for (ServerPlayer p : watching) {
            if (alive > 0) {
                p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 20 * 60 * 20, alive * 2 >= t.village.size() ? 1 : 0, false, false, true));
                com.jayptucker.heroesevolved.progression.MasteryService.awardRescue(p, 5L * alive);
            }
            p.sendSystemMessage(Component.literal(alive == t.village.size() ? "The village is saved. Nobody was lost."
                    : alive > 0 ? "The village is saved. " + alive + " of " + t.village.size() + " made it." : "It's over, but nobody was left to save.")
                    .withStyle(alive > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
    }

    /** Nobody left: the village has fallen, and the renegade stays where it is. */
    private static void fallen(Terror t) {
        List<ServerPlayer> watching = watchers(t);
        finish(t);
        Gift gift = Gift.read(t.renegade);
        com.jayptucker.heroesevolved.village.VillageMemory.record(t.level, t.bell, gift != null ? gift.name() : "a renegade",
                com.jayptucker.heroesevolved.village.VillageMemory.Outcome.FALLEN, null, watching, 0, t.village.size());
        for (ServerPlayer p : watching)
            p.sendSystemMessage(Component.literal("The village has fallen.").withStyle(ChatFormatting.DARK_RED));
    }

    /** It has gone on so long that the calls stop. The renegade is still there. */
    private static void quiet(Terror t) {
        finish(t);
    }

    @SubscribeEvent
    public static void villagerDied(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof AbstractVillager v)) return;
        for (Terror t : TERRORS.values())
            if (t.village.contains(v.getUUID()) && t.dead.add(v.getUUID())) {
                // One taken: the next waits its turn.
                t.nextKill = t.level.getGameTime() + killInterval();
                if (t.renegade.getTarget() == v) t.renegade.setTarget(null);
                for (ServerPlayer p : watchers(t))
                    p.displayClientMessage(Component.literal("The village lost someone. " + t.alive() + " of " + t.village.size() + " left.")
                            .withStyle(ChatFormatting.RED), true);
            }
    }

    private static void finish(Terror t) {
        TERRORS.remove(t.renegade.getUUID());
        t.renegade.getPersistentData().remove(TAG);
        if (t.call != null) EmergencyBand.end(t.call);
        VillageTickets.release(t.key());
        TURNED.values().remove(t.renegade);
    }

    /** Ends a siege with nothing more happening (admin, tests). */
    public static void cancel(Entity renegade) {
        Terror t = of(renegade);
        if (t != null) finish(t);
    }

    /** Its next turn comes now (tests, admin). */
    public static void hurry(Entity renegade) {
        Terror t = of(renegade);
        if (t != null) t.nextKill = t.level.getGameTime();
    }

    public static int cancelAll() {
        int turning = TURNING.size();
        for (Turning t : List.copyOf(TURNING)) {
            var speed = t.villager.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            if (speed != null) speed.removeModifier(TURN_ROOT);
        }
        TURNING.clear();
        for (Mob m : List.copyOf(RESUMING)) m.getPersistentData().remove(TAG);
        RESUMING.clear();
        List<Terror> all = all();
        for (Terror t : all) finish(t);
        return all.size() + turning;
    }

    public static int turningCount() { return TURNING.size(); }

    /** Off the map for now: the call stops; its data still has the siege. */
    private static void pause(Terror t) {
        TERRORS.remove(t.renegade.getUUID());
        if (t.call != null) EmergencyBand.end(t.call);
        VillageTickets.release(t.key());
    }

    // ------------------------------------------------------------------
    // Keeping it through unloading and restarts
    // ------------------------------------------------------------------

    private static void save(Terror t) {
        CompoundTag tag = new CompoundTag();
        long now = t.level.getGameTime();
        tag.putLong("bell", t.bell.asLong());
        tag.putLong("elapsed", now - t.startedAt);
        tag.putLong("untilKill", Math.max(0, t.nextKill - now));
        ListTag village = new ListTag(), dead = new ListTag();
        for (UUID id : t.village) village.add(NbtUtils.createUUID(id));
        for (UUID id : t.dead) dead.add(NbtUtils.createUUID(id));
        tag.put("village", village);
        tag.put("dead", dead);
        t.renegade.getPersistentData().put(TAG, tag);
    }

    private static void load(Terror t) {
        CompoundTag tag = t.renegade.getPersistentData().getCompound(TAG);
        long now = t.level.getGameTime();
        t.startedAt = now - tag.getLong("elapsed");
        t.nextKill = now + tag.getLong("untilKill");
        for (Tag id : tag.getList("village", Tag.TAG_INT_ARRAY)) t.village.add(NbtUtils.loadUUID(id));
        for (Tag id : tag.getList("dead", Tag.TAG_INT_ARRAY)) t.dead.add(NbtUtils.loadUUID(id));
    }

    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob m && m.getPersistentData().contains(TAG)) RESUMING.add(m);
    }

    private static void resume(Mob m) {
        if (m.isRemoved() || !m.isAlive() || TERRORS.containsKey(m.getUUID()) || !(m.level() instanceof ServerLevel level)) return;
        BlockPos bell = BlockPos.of(m.getPersistentData().getCompound(TAG).getLong("bell"));
        begin(m, level, bell, false);
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) { clear(); }

    /** Forgets every siege without ending any (their renegades keep their data). */
    public static void clear() {
        for (Terror t : List.copyOf(TERRORS.values())) pause(t);
        TERRORS.clear();
        RESUMING.clear();
        TURNING.clear();
        TURNED.clear();
    }
}
