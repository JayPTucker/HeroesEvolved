package com.jayptucker.heroesevolved.combat;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.PowerBalance;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.Elements;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.mimicry.MimicryService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.FinisherRunPayload;
import com.jayptucker.heroesevolved.network.FinisherStatePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.time.SpaceTimeService;
import com.jayptucker.heroesevolved.time.TimeStopService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Finishers: a foe near death glistens, and one key (C) ends it with a short move that belongs to your power.
 *
 * <p>This class is the engine every power's finisher runs on, as {@link FightingStyles} is for fighting styles. It
 * decides who can be finished (at or below {@link #threshold}), checks and pays for a request, holds the foe still,
 * halves the damage the finisher takes and breaks the move on a heavy blow, lands the killing blow, spills what is left
 * over (the overkill) onto nearby foes, gives back stamina and the power's own reward, and keeps the shared cooldown.
 * A power's finisher is one {@link Finisher}: its beats, its blow and its reward. Add it to {@link #FINISHERS}.</p>
 *
 * <p>In the docs and on the key these are Finishers; in code they are takedowns, because the fighting styles already
 * call the third strike of their combo a finisher. What the player sees is in {@code docs/finishers.md}.</p>
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Takedowns {
    /** The Power Level a power's finisher opens at. */
    public static final int UNLOCK_LEVEL = 3;
    /** How far a contact finisher reaches (eye to the foe's body), and a ranged one (Telepathy). */
    public static final double REACH = 4, RANGED_REACH = 6;
    /** How far away a glisten shows. */
    public static final double GLISTEN_RANGE = 12;
    /** The table's starting cost and the shared cooldown, before {@code finisher_<power>} in power_balance.json scales them. */
    public static final int BASE_ENERGY = 8, BASE_COOLDOWN = 60;
    /** Stamina given back for a finished foe. */
    public static final int REFUND = 10;
    /** A blow bigger than this breaks a finisher; anything smaller is halved while it plays. */
    public static final float BREAK_DAMAGE = 6;
    /** Overkill spills onto foes this close to the finished one, at most this many. */
    public static final double SPILL_RADIUS = 4;
    public static final int SPILL_COUNT = 3;

    /** Bosses and story fighters never glisten (their phases are a later job). */
    public static final TagKey<EntityType<?>> UNFINISHABLE = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "unfinishable"));

    private static final String OFF = "heroesevolved_finishers_off";
    /** On a mob whose AI a finisher switched off, so a crash mid-move can't leave it frozen for good. */
    private static final String HELD = "heroesevolved_finisher_held";
    /** On a player a finisher holds up ({@link #hover}), for the same reason. */
    private static final String FLOATING = "heroesevolved_finisher_floating";

    /** One power's finisher. */
    public interface Finisher {
        ResourceLocation power();
        /** The name in lang files and docs: {@code finisher.heroesevolved.<id>}. */
        String id();
        /** The whole move, in ticks. */
        int ticks();
        /** The tick the killing blow lands. */
        int landAt();
        default double reach() { return REACH; }
        /** Whether the engine pins the foe where it stood (false for one that moves the foe itself, stepping it with teleportTo). */
        default boolean holds() { return true; }
        /** The move's opening (age 0): poses, the first pieces. */
        void start(Run run);
        /** Every tick after the opening, ages 1 to {@link #ticks()}. */
        void tick(Run run);
        /** The blow, at {@link #landAt()}: call {@link #strike} with the power's own kind of hit, then spill the overkill. */
        void land(Run run);
        /** The power's own reward, once the foe is finished. */
        default void reward(Run run) {}
        /** Always called last: tidy anything the move left (a double, a lock). {@code broken} if it was interrupted. */
        default void end(Run run, boolean broken) {}
        /**
         * The finisher that really plays when this one is picked (itself, unless it stands for another: Borrowed Strength
         * plays one of the empath's kept powers' finishers). Called once, as the move starts.
         */
        default Finisher resolve(ServerPlayer player) { return this; }
        /** Whether this one can come up for this player at all (Borrowed Strength needs a kept power). */
        default boolean available(ServerPlayer player) { return true; }
    }

    /** One finisher playing. */
    public static final class Run {
        public final ServerPlayer player;
        public final LivingEntity target;
        public final Finisher finisher;
        /** Where the foe stood when the move began; it is held here. */
        public final Vec3 anchor;
        /** The foe's finish threshold when the move began; the blow is 1.5 times it. */
        public final float threshold;
        /** For the finisher's own state between ticks. */
        public Object state;
        int age;
        boolean landed, finished, aiOff, glided;
        float overkill;

        Run(ServerPlayer player, LivingEntity target, Finisher finisher) {
            this.player = player;
            this.target = target;
            this.finisher = finisher;
            this.anchor = target.position();
            this.threshold = threshold(target);
        }

        public int age() { return age; }
        public ServerLevel level() { return player.serverLevel(); }
        /** The killing blow's damage. */
        public float damage() { return threshold * 1.5F; }
        /** What was left of the blow after the foe's health ran out (set by {@link #strike}). */
        public float overkill() { return overkill; }
        public boolean landed() { return landed; }
        public boolean finished() { return finished; }
    }

    /**
     * Every finisher built, each power's signature ahead of its alternates. A power with several picks one at random each
     * time ({@link #choose}), never the same one twice in a row.
     */
    private static final List<Finisher> FINISHERS = List.of(
            com.jayptucker.heroesevolved.pyro.DragonCoil.FINISHER,
            com.jayptucker.heroesevolved.time.ClosedLoop.FINISHER,
            com.jayptucker.heroesevolved.telepathy.LightsOut.FINISHER,
            com.jayptucker.heroesevolved.electro.ShortCircuit.FINISHER,
            com.jayptucker.heroesevolved.hydro.Breaker.FINISHER,
            com.jayptucker.heroesevolved.cryo.LastWarmth.FINISHER,
            com.jayptucker.heroesevolved.speed.LateThunder.FINISHER,
            com.jayptucker.heroesevolved.flight.Downburst.FINISHER,
            com.jayptucker.heroesevolved.quake.Counterbeat.FINISHER,
            com.jayptucker.heroesevolved.sonic.Encore.FINISHER,
            com.jayptucker.heroesevolved.telekinesis.CutStrings.FINISHER,
            com.jayptucker.heroesevolved.phasing.SixFeet.FINISHER,
            com.jayptucker.heroesevolved.invisibility.HallOfMirrors.FINISHER,
            com.jayptucker.heroesevolved.regeneration.PaidInFull.FINISHER,
            com.jayptucker.heroesevolved.radiation.ChainReaction.FINISHER,
            com.jayptucker.heroesevolved.vibe.OffFrequency.FINISHER,
            com.jayptucker.heroesevolved.technopathy.HardReset.FINISHER,
            com.jayptucker.heroesevolved.assembly.Monument.FINISHER,
            com.jayptucker.heroesevolved.mimicry.TakeItIn.FINISHER,
            com.jayptucker.heroesevolved.shapeshift.Unmade.FINISHER,
            // The alternates.
            com.jayptucker.heroesevolved.pyro.PyroFinishers.PHOENIX_KICK,
            com.jayptucker.heroesevolved.pyro.PyroFinishers.HEARTH,
            com.jayptucker.heroesevolved.time.TimeFinishers.STOPPED_CLOCK,
            com.jayptucker.heroesevolved.time.TimeFinishers.UNWOUND,
            com.jayptucker.heroesevolved.telepathy.TelepathyFinishers.PRESSURE,
            com.jayptucker.heroesevolved.telepathy.TelepathyFinishers.LAST_THOUGHT,
            com.jayptucker.heroesevolved.electro.ElectroFinishers.LIGHTNING_ROD,
            com.jayptucker.heroesevolved.electro.ElectroFinishers.OVERLOAD,
            com.jayptucker.heroesevolved.hydro.HydroFinishers.DROWNING_BUBBLE,
            com.jayptucker.heroesevolved.hydro.HydroFinishers.RIPTIDE,
            com.jayptucker.heroesevolved.cryo.CryoFinishers.COFFIN,
            com.jayptucker.heroesevolved.cryo.CryoFinishers.ICICLE_RING,
            com.jayptucker.heroesevolved.speed.SpeedFinishers.HUNDRED_FISTS,
            com.jayptucker.heroesevolved.speed.SpeedFinishers.FLICK,
            com.jayptucker.heroesevolved.flight.FlightFinishers.VACUUM_CLAP,
            com.jayptucker.heroesevolved.flight.FlightFinishers.UPDRAFT,
            com.jayptucker.heroesevolved.quake.QuakeFinishers.FAULT_LINE,
            com.jayptucker.heroesevolved.quake.QuakeFinishers.SHATTER_POINT,
            com.jayptucker.heroesevolved.sonic.SonicFinishers.SHATTER_NOTE,
            com.jayptucker.heroesevolved.sonic.SonicFinishers.SILENCE,
            com.jayptucker.heroesevolved.telekinesis.KineticFinishers.CRUSH,
            com.jayptucker.heroesevolved.telekinesis.KineticFinishers.TOSS,
            com.jayptucker.heroesevolved.phasing.DensityFinishers.ROCK_CLAP,
            com.jayptucker.heroesevolved.phasing.DensityFinishers.HEAVY_HAND,
            com.jayptucker.heroesevolved.invisibility.ForceFinishers.BUBBLE_CRUSH,
            com.jayptucker.heroesevolved.invisibility.ForceFinishers.LENS_STRIKE,
            com.jayptucker.heroesevolved.regeneration.RegenFinishers.NO_PAIN,
            com.jayptucker.heroesevolved.regeneration.RegenFinishers.GRIP,
            com.jayptucker.heroesevolved.radiation.RadiationFinishers.HALF_LIFE,
            com.jayptucker.heroesevolved.radiation.RadiationFinishers.POCKET_SUN,
            com.jayptucker.heroesevolved.vibe.VibeFinishers.BREACH_DROP,
            com.jayptucker.heroesevolved.vibe.VibeFinishers.POINT_BLANK,
            com.jayptucker.heroesevolved.technopathy.TechnoFinishers.KILL_SWITCH,
            com.jayptucker.heroesevolved.technopathy.TechnoFinishers.OVERCLOCK,
            com.jayptucker.heroesevolved.assembly.AssemblyFinishers.HAMMER,
            com.jayptucker.heroesevolved.assembly.AssemblyFinishers.SHARD_ORBIT,
            com.jayptucker.heroesevolved.mimicry.MimicFinishers.ECHO,
            com.jayptucker.heroesevolved.mimicry.MimicFinishers.BORROWED_STRENGTH,
            com.jayptucker.heroesevolved.shapeshift.ShapeFinishers.APEX_BITE,
            com.jayptucker.heroesevolved.shapeshift.ShapeFinishers.OWN_FACE,
            // Power Amplification: its signature, then its alternates.
            com.jayptucker.heroesevolved.amplify.AmpFinishers.SIDEKICK_JUICE,
            com.jayptucker.heroesevolved.amplify.AmpFinishers.RED_WAVE,
            com.jayptucker.heroesevolved.amplify.AmpFinishers.REVERSE);

    private static final Map<UUID, Run> RUNS = new HashMap<>();
    private static final Map<UUID, Long> READY_AT = new HashMap<>();
    /** The finisher each player used last, so the next pick is a different one. */
    private static final Map<UUID, String> LAST = new HashMap<>();
    private static final Map<UUID, FinisherStatePayload> SENT = new HashMap<>();

    private Takedowns() {}

    // ------------------------------------------------------------------
    // Who can be finished
    // ------------------------------------------------------------------

    /** At or below this much health a foe can be finished: a fifth of its health, at least 4 and at most 12 (players: a fifth). */
    public static float threshold(LivingEntity target) {
        float max = target.getMaxHealth();
        if (target instanceof Player) return max * .2F;
        return Mth.clamp(max * .2F, 4, 12);
    }

    /**
     * The part of the rule both sides can answer from what every client knows: alive, low enough, not a boss, and an
     * enemy (a monster, a creature fighting, or a player where PvP is on). The client draws the glisten from this; the
     * server adds {@link #finishable}'s friend and cover checks.
     */
    public static boolean looksFinishable(Player viewer, LivingEntity target, boolean pvp) {
        if (target == viewer || !target.isAlive() || target.isRemoved() || target.isInvulnerable() || target.isSpectator()) return false;
        if (target.getType().is(UNFINISHABLE) || target instanceof AbstractVillager) return false;
        if (target instanceof Player other) {
            if (!pvp || other.isCreative() || other.isAlliedTo(viewer)) return false;
        } else if (target instanceof Mob mob) {
            if (mob instanceof OwnableEntity pet && viewer.getUUID().equals(pet.getOwnerUUID())) return false;
            if (!(mob instanceof Enemy) && !mob.isAggressive()) return false;
        } else {
            return false;
        }
        return target.getHealth() <= threshold(target);
    }

    /** Whether {@code player} may finish {@code target} now (not checking reach, cost or cooldown). */
    public static boolean finishable(ServerPlayer player, LivingEntity target) {
        boolean pvp = target instanceof Player other && player.canHarmPlayer(other);
        boolean hunting = target instanceof Mob mob && mob.getTarget() == player;
        // A creature hunting this player counts even if it isn't a monster and the client hasn't seen it turn.
        if (!looksFinishable(player, target, pvp) && (!hunting || !target.isAlive() || target.isRemoved()
                || target.getType().is(UNFINISHABLE) || target.getHealth() > threshold(target))) return false;
        if (target instanceof ServerPlayer other && RUNS.containsKey(other.getUUID())) return false;
        return StyleHits.hostile(player, target) && finishing(target) == null;
    }

    /** The finisher already playing on this foe, if anyone's. */
    public static Run finishing(LivingEntity target) {
        for (Run run : RUNS.values()) if (run.target == target) return run;
        return null;
    }

    public static boolean running(ServerPlayer player) { return RUNS.containsKey(player.getUUID()); }

    public static Run run(ServerPlayer player) { return RUNS.get(player.getUUID()); }

    // ------------------------------------------------------------------
    // Whose finisher, and the switch
    // ------------------------------------------------------------------

    /** The power whose finishers this player uses: their own, or (an empath) the power they are channeling. */
    public static ResourceLocation powerOf(ServerPlayer player) {
        if (PlayerProgressionService.getPowerLevel(player) < UNLOCK_LEVEL) return null;
        // An empath channeling a power finishes with it; with nothing channeled, with their own.
        if (MimicryService.isMimic(player) && PowerRules.canAct(player)) {
            ResourceLocation channel = MimicryService.memory(player).current();
            if (channel != null && forPower(channel) != null) return channel;
        }
        for (Finisher f : FINISHERS) if (FightingStyles.hasPower(player, f.power())) return f.power();
        return null;
    }

    /** This player's power's signature finisher (the first built for it), or null. */
    public static Finisher of(ServerPlayer player) {
        ResourceLocation power = powerOf(player);
        return power == null ? null : forPower(power);
    }

    /** Every finisher built for this power, its signature first. */
    public static List<Finisher> variants(ResourceLocation power) {
        List<Finisher> out = new ArrayList<>();
        for (Finisher f : FINISHERS) if (f.power().equals(power)) out.add(f);
        return out;
    }

    /**
     * Which of this player's finishers plays on {@code target}: one at random among those that reach it, never the one
     * used last when there is another.
     */
    public static Finisher choose(ServerPlayer player, LivingEntity target) {
        ResourceLocation power = powerOf(player);
        if (power == null) return null;
        List<Finisher> pool = variants(power);
        pool.removeIf(f -> !f.available(player) || !inReach(player, target, f.reach()));
        if (pool.isEmpty()) return null;
        String last = LAST.get(player.getUUID());
        if (pool.size() > 1) pool.removeIf(f -> f.id().equals(last));
        return pool.get(player.getRandom().nextInt(pool.size()));
    }

    /** Every power with a finisher built, for menus and tests. */
    public static List<Finisher> all() { return FINISHERS; }

    public static boolean enabled(ServerPlayer player) {
        return !player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(OFF);
    }

    /** Shift + C: finishers off (a player who'd rather keep swinging) or on again. Saved with the player. */
    public static void toggle(ServerPlayer player) {
        var kept = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        boolean on = !enabled(player);
        kept.putBoolean(OFF, !on);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, kept);
        player.displayClientMessage(Component.translatable(on ? "finisher.heroesevolved.on" : "finisher.heroesevolved.off")
                .withStyle(on ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
        sync(player, true);
    }

    /** The balance entry for this power's finisher. */
    public static String action(Finisher f) { return "finisher_" + f.power().getPath(); }

    public static int cost(Finisher f) { return PowerBalance.energy(action(f), BASE_ENERGY); }

    public static int cooldown(Finisher f) { return PowerBalance.cooldown(action(f), BASE_COOLDOWN); }

    // ------------------------------------------------------------------
    // Starting one
    // ------------------------------------------------------------------

    /** The key: finish the foe with this id. Returns whether a finisher started. */
    public static boolean request(ServerPlayer player, int targetId) { return request(player, targetId, null); }

    /**
     * The key, with the pick made for it: {@code forced} plays instead of a random one, if it is one of this player's
     * (tests that check one finisher's beats). Null picks at random as usual.
     */
    public static boolean request(ServerPlayer player, int targetId, Finisher forced) {
        if (of(player) == null || !enabled(player) || RUNS.containsKey(player.getUUID())) return false;
        if (!PowerRules.canAct(player) || player.isPassenger() || player.isFallFlying()) return false;
        // Rained-out fire and boiled-off water have nothing to finish with.
        ResourceLocation element = powerOf(player);
        if (Elements.snuffed(player, element)) {
            Elements.fizzle(player, element, false);
            player.displayClientMessage(Elements.reason(element), true);
            return false;
        }
        long now = now(player);
        if (now < READY_AT.getOrDefault(player.getUUID(), Long.MIN_VALUE)) return false;
        Entity entity = player.level().getEntity(targetId);
        if (!(entity instanceof LivingEntity target) || !finishable(player, target)) return false;
        Finisher f = forced != null && forced.power().equals(powerOf(player)) && inReach(player, target, forced.reach())
                ? forced : forced != null ? null : choose(player, target);
        if (f == null || !player.hasLineOfSight(target)) return false;
        int cost = cost(f);
        if (cost > 0) {
            if (!PlayerEnergyService.tryConsume(player, cost)) {
                PlayerEnergyService.refused(player);
                player.displayClientMessage(Component.translatable("finisher.heroesevolved.tired"), true);
                return false;
            }
            MasteryService.awardEnergySpent(player, cost);
        }
        start(player, target, f);
        return true;
    }

    /** Within {@code reach} of the eyes, measured to the nearest point of the foe's body. */
    public static boolean inReach(ServerPlayer player, LivingEntity target, double reach) {
        Vec3 eye = player.getEyePosition();
        var box = target.getBoundingBox();
        Vec3 nearest = new Vec3(Mth.clamp(eye.x, box.minX, box.maxX), Mth.clamp(eye.y, box.minY, box.maxY),
                Mth.clamp(eye.z, box.minZ, box.maxZ));
        return nearest.distanceToSqr(eye) <= (reach + .5) * (reach + .5);
    }

    /** Starts {@code f} on {@code target} without the request's checks or cost (tests and previews). */
    public static Run start(ServerPlayer player, LivingEntity target, Finisher picked) {
        LAST.put(player.getUUID(), picked.id());
        Finisher f = picked.resolve(player);
        Run run = new Run(player, target, f);
        RUNS.put(player.getUUID(), run);
        // The pick's own balance entry (Borrowed Strength is paid and timed as Mimicry's, whatever it plays).
        READY_AT.put(player.getUUID(), now(player) + cooldown(picked));
        face(player, target.getBoundingBox().getCenter());
        // The foe's own mind is switched off for the move (it can't turn, swing or wander); a finisher that moves it
        // drives its body itself (a mob without AI ignores velocity, so step it with teleportTo).
        if (target instanceof Mob mob && !mob.isNoAi()) {
            mob.setNoAi(true);
            mob.getPersistentData().putBoolean(HELD, true);
            run.aiOff = true;
        }
        PacketDistributor.sendToPlayer(player, new FinisherRunPayload(player.getId(), target.getId(), f.ticks()));
        com.jayptucker.heroesevolved.sounds.PowerSounds.play(player, f.power().getPath(),
                com.jayptucker.heroesevolved.sounds.PowerCue.HEAVY, 1.0F, false);
        f.start(run);
        return run;
    }

    // ------------------------------------------------------------------
    // Helpers for a finisher's beats
    // ------------------------------------------------------------------

    /**
     * The killing blow: {@code hit} deals the power's own kind of damage (so statuses and cross-power rules apply), and
     * if the foe somehow lived through it (fire immunity, armor) a plain blow from the player finishes the job.
     * Records the overkill for {@link Run#overkill()}. A foe held in someone's stopped time is let go first, or the blow
     * would only be stored; its blows land with it.
     */
    public static void strike(Run run, java.util.function.Consumer<Float> hit) {
        LivingEntity target = run.target;
        if (!target.isAlive()) return;
        UUID holder = SpaceTimeService.holder(target);
        if (holder != null && !holder.equals(run.player.getUUID())) TimeStopService.release(run.player.getServer(), holder, target);
        float before = target.getHealth();
        float damage = run.damage();
        hit.accept(damage);
        if (target.isAlive() && target.getHealth() > 0 && !TimeStopService.hasStored(run.player.getUUID(), target))
            StyleHits.hurt(target, run.player.damageSources().playerAttack(run.player), Math.max(damage, target.getHealth() + 1));
        run.overkill = Math.max(0, damage - before);
    }

    /** The overkill, spread over up to {@link #SPILL_COUNT} other foes near the finished one, each hit with {@code hit}. */
    public static List<LivingEntity> spill(Run run, java.util.function.BiConsumer<LivingEntity, Float> hit) {
        List<LivingEntity> near = others(run, SPILL_RADIUS, SPILL_COUNT);
        if (near.isEmpty() || run.overkill <= .05F) return near;
        float each = Math.max(2, run.overkill);
        for (LivingEntity other : near) hit.accept(other, each);
        return near;
    }

    /** The nearest foes to the finished one, nearest first (never the finished one itself). */
    public static List<LivingEntity> others(Run run, double radius, int count) {
        Vec3 at = run.target.isAlive() ? run.target.position() : run.anchor;
        List<LivingEntity> near = new ArrayList<>(StyleHits.foes(run.player, at, radius));
        near.removeIf(e -> e == run.target || e == run.player || !e.isAlive());
        near.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(at)));
        return near.size() > count ? near.subList(0, count) : near;
    }

    /** A flinch the foe shows without being hurt: the red flash and the hurt sound, for the beats before the blow. */
    public static void flinch(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) return;
        level.getChunkSource().broadcastAndSend(target, new ClientboundHurtAnimationPacket(target));
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG,
                SoundSource.PLAYERS, .55F, .9F + target.getRandom().nextFloat() * .2F);
    }

    /** A flinch with no sound at all (Silence). */
    public static void shudder(LivingEntity target) {
        if (target.level() instanceof ServerLevel level)
            level.getChunkSource().broadcastAndSend(target, new ClientboundHurtAnimationPacket(target));
    }

    /** Turns a player to look at {@code at} without moving them (the position passed is their own, as AGENTS.md says). */
    public static void face(ServerPlayer player, Vec3 at) {
        Vec3 d = at.subtract(player.getEyePosition());
        float yaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
        player.setXRot(pitch);
        if (player.connection != null)
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, pitch);
    }

    /** A set piece with a chosen seed (a finisher passes an entity id through it, so the art can find both bodies). */
    public static void show(ServerLevel level, Piece piece, Vec3 origin, Vec3 target, float scale, Entity bound, int seed) {
        SetPiecePayload payload = new SetPiecePayload(piece.ordinal(), origin, target, scale, bound == null ? -1 : bound.getId(), seed, 0);
        double range = 128 + scale * 4;
        for (ServerPlayer viewer : level.players())
            if (viewer.position().distanceToSqr(origin) <= range * range
                    && !com.jayptucker.heroesevolved.time.TimeVeil.masks(viewer, origin))
                PacketDistributor.sendToPlayer(viewer, payload);
    }

    /** A sound at {@code at}, for a finisher's beats. */
    public static void sound(Run run, Vec3 at, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        run.level().playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** Moves a body to {@code at} at once (a finisher that carries the foe or the player itself; test players too). */
    public static void move(LivingEntity body, Vec3 at) {
        if (body instanceof ServerPlayer p) { StyleHits.blink(p, at); return; }
        body.teleportTo(at.x, at.y, at.z);
        body.absMoveTo(at.x, at.y, at.z);
        body.setDeltaMovement(Vec3.ZERO);
    }

    /**
     * Holds the foe {@code height} above where it stood, or lower if a ceiling is in the way (a finisher that lifts its
     * foe: Phoenix Kick, Updraft, Toss). Its {@link Finisher#holds()} must be false. A mob with its AI off hangs where it
     * is put; call this every tick the lift lasts.
     */
    public static void lift(Run run, double height) {
        LivingEntity foe = run.target;
        double h = Math.max(0, height);
        while (h > 0 && !run.level().noCollision(foe, foe.getDimensions(foe.getPose()).makeBoundingBox(run.anchor.add(0, h, 0)))) h -= .25;
        move(foe, run.anchor.add(0, Math.max(0, h), 0));
        foe.resetFallDistance();
    }

    /**
     * One tick of the player's own body along a path, smoothly: {@code now} is where the path is this tick and
     * {@code next} where it is next tick. The player's client is given the step as its velocity (full precision, as
     * Skyburst does), so it moves itself and draws every frame in between, instead of jumping once a tick as a teleport
     * would. A gentle pull toward the path (×0.25, at most half a block) keeps lag from drifting it off. Swept, so a wall
     * stops it. A test's FakePlayer has no client to move it, so it is moved here.
     */
    public static void glide(ServerPlayer player, Vec3 now, Vec3 next) {
        Run run = RUNS.get(player.getUUID());
        if (run != null) run.glided = true;
        Vec3 error = now.subtract(player.position());
        double off = error.length();
        if (off > .5) error = error.scale(.5 / off);
        Vec3 want = next.subtract(now).add(error.scale(.25));
        Vec3 velocity = com.jayptucker.heroesevolved.speed.SpeedService.swept(player, want).subtract(player.position());
        player.resetFallDistance();
        player.setDeltaMovement(velocity);
        if (player instanceof net.neoforged.neoforge.common.util.FakePlayer || player.connection == null) {
            player.setPos(player.position().add(velocity));
        } else {
            PacketDistributor.sendToPlayer(player, new com.jayptucker.heroesevolved.network.SkyburstMotionPayload(velocity));
        }
    }

    /** Stops a glide: the client's own velocity is zeroed, so the body doesn't coast on after the path ends. */
    public static void still(ServerPlayer player) {
        player.setDeltaMovement(Vec3.ZERO);
        if (!(player instanceof net.neoforged.neoforge.common.util.FakePlayer) && player.connection != null)
            PacketDistributor.sendToPlayer(player, new com.jayptucker.heroesevolved.network.SkyburstMotionPayload(Vec3.ZERO));
    }

    /**
     * Holds the player up (no gravity) for a move that flies them, or lets them fall again. Marked on the player, so a
     * crash mid-move can't leave them floating: the mark is cleared, with the gravity, when they log in.
     */
    public static void hover(ServerPlayer player, boolean on) {
        player.setNoGravity(on);
        if (on) player.getPersistentData().putBoolean(FLOATING, true);
        else player.getPersistentData().remove(FLOATING);
    }

    /** A job {@code ticks} from now, for what a finisher leaves behind after it ends (blocks put back). */
    public static void later(MinecraftServer server, int ticks, Runnable job) {
        LATER.add(new Later(server.getTickCount() + Math.max(1, ticks), job));
    }

    private record Later(long at, Runnable job) {}
    private static final List<Later> LATER = new ArrayList<>();

    // ------------------------------------------------------------------
    // Ticking, holding, breaking, ending
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!LATER.isEmpty()) {
            long now = server.getTickCount();
            for (Later job : List.copyOf(LATER)) {
                if (job.at > now) continue;
                LATER.remove(job);
                try { job.job.run(); } catch (RuntimeException broken) { HeroesEvolved.LOGGER.error("A finisher's later job failed", broken); }
            }
        }
        for (Run run : List.copyOf(RUNS.values())) {
            try {
                step(run);
            } catch (RuntimeException broken) {
                HeroesEvolved.LOGGER.error("Finisher {} failed", run.finisher.id(), broken);
                end(run, true);
            }
        }
        if (server.getTickCount() % 20 == 0)
            for (ServerPlayer player : server.getPlayerList().getPlayers()) sync(player, false);
    }

    private static void step(Run run) {
        ServerPlayer player = run.player;
        if (player.isRemoved() || !player.isAlive() || player.isSpectator() || player.level() != run.target.level()) {
            end(run, true);
            return;
        }
        if (!run.landed && (!PowerRules.canAct(player) || Elements.snuffed(player, run.finisher.power()))) {
            if (Elements.snuffed(player, run.finisher.power())) Elements.fizzle(player, run.finisher.power(), true);
            interrupt(run);
            return;
        }
        if (!run.landed && (!run.target.isAlive() || run.target.isRemoved())) { end(run, true); return; }
        run.age++;
        if (run.finisher.holds() && !run.landed && run.target.isAlive()) pin(run);
        run.finisher.tick(run);
        if (run.age == run.finisher.landAt()) land(run);
        if (run.age >= run.finisher.ticks()) end(run, false);
    }

    private static void pin(Run run) {
        LivingEntity target = run.target;
        target.setDeltaMovement(Vec3.ZERO);
        if (target.position().distanceToSqr(run.anchor) < 1e-4) return;
        if (target instanceof ServerPlayer other && other.connection != null)
            other.connection.teleport(run.anchor.x, run.anchor.y, run.anchor.z, other.getYRot(), other.getXRot());
        else target.teleportTo(run.anchor.x, run.anchor.y, run.anchor.z);
        target.absMoveTo(run.anchor.x, run.anchor.y, run.anchor.z);
    }

    private static void land(Run run) {
        run.landed = true;
        // Let go before the blow: the foe falls the way it is hit.
        restoreAi(run);
        run.finisher.land(run);
        boolean dead = !run.target.isAlive() || run.target.getHealth() <= 0;
        // A blow stored in your own stopped time lands when time resumes: the finisher still counts.
        boolean stored = run.target.isAlive() && TimeStopService.hasStored(run.player.getUUID(), run.target);
        if (!dead && !stored) return;
        run.finished = true;
        PlayerEnergyService.restore(run.player, REFUND);
        run.finisher.reward(run);
        ServerLevel level = run.level();
        Vec3 at = run.target.position();
        level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.4F, .55F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, .7F);
        com.jayptucker.heroesevolved.network.CameraShakePayload.sendAround(level, at, 24, .22F, 6);
    }

    private static void interrupt(Run run) {
        run.player.displayClientMessage(Component.translatable("finisher.heroesevolved.broken")
                .withStyle(ChatFormatting.RED), true);
        end(run, true);
    }

    /** Ends a run: the foe and the player are let go, the camera comes back, the finisher tidies up. */
    public static void end(Run run, boolean broken) {
        if (RUNS.get(run.player.getUUID()) != run) return;
        RUNS.remove(run.player.getUUID());
        restoreAi(run);
        if (broken) ArmPosePayload.clear(run.player);
        // A move cut short mid-glide stops there, or the client coasts on its last full-speed step.
        if (broken && run.glided) still(run.player);
        if (run.player.connection != null)
            PacketDistributor.sendToPlayer(run.player, new FinisherRunPayload(run.player.getId(), run.target.getId(), 0));
        try {
            run.finisher.end(run, broken);
        } catch (RuntimeException e) {
            HeroesEvolved.LOGGER.error("Finisher {} failed to end", run.finisher.id(), e);
        }
    }

    private static void restoreAi(Run run) {
        if (!run.aiOff) return;
        run.aiOff = false;
        if (run.target instanceof Mob mob) {
            mob.setNoAi(false);
            mob.getPersistentData().remove(HELD);
        }
    }

    /**
     * While a finisher plays, the held foe can't hurt the player, other blows are halved, and one over
     * {@link #BREAK_DAMAGE} breaks it. Low priority, after evasion and braces have had their say.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void incoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Run run = RUNS.get(player.getUUID());
        if (run == null || run.landed) return;
        DamageSource source = event.getSource();
        if (source.getEntity() == run.target || source.getDirectEntity() == run.target) {
            event.setCanceled(true);
            return;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (event.getAmount() > BREAK_DAMAGE) interrupt(run);
        else event.setAmount(event.getAmount() * .5F);
    }

    /** A mob saved while a finisher held it (a crash mid-move) gets its AI back when it loads. */
    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) return;
        if (!mob.getPersistentData().getBoolean(HELD) || finishing(mob) != null) return;
        mob.getPersistentData().remove(HELD);
        mob.setNoAi(false);
    }

    private static void forget(ServerPlayer player) {
        Run run = RUNS.get(player.getUUID());
        if (run != null) end(run, true);
        SENT.remove(player.getUUID());
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            forget(player);
            READY_AT.remove(player.getUUID());
            LAST.remove(player.getUUID());
        }
    }

    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }

    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getPersistentData().getBoolean(FLOATING)) hover(player, false);
        sync(player, true);
    }

    @SubscribeEvent public static void stop(ServerStoppingEvent event) {
        for (Run run : List.copyOf(RUNS.values())) end(run, true);
        // Anything waiting to be put back (Hard Reset's switches) is put back now.
        for (Later job : List.copyOf(LATER)) {
            try { job.job.run(); } catch (RuntimeException broken) { HeroesEvolved.LOGGER.error("A finisher's later job failed", broken); }
        }
        LATER.clear();
        RUNS.clear();
        READY_AT.clear();
        LAST.clear();
        SENT.clear();
    }

    // ------------------------------------------------------------------
    // What the client needs to know
    // ------------------------------------------------------------------

    /** Tells the player's client which finisher they have, whether it's on, whether PvP is, and its reach. */
    public static void sync(ServerPlayer player, boolean force) {
        if (player.connection == null) return;
        ResourceLocation power = powerOf(player);
        double reach = 0;
        if (power != null) for (Finisher v : variants(power)) reach = Math.max(reach, v.reach());
        FinisherStatePayload state = new FinisherStatePayload(power == null ? "" : power.getPath(), enabled(player),
                player.getServer() != null && player.getServer().isPvpAllowed(), (float) reach);
        if (!force && Objects.equals(SENT.get(player.getUUID()), state)) return;
        SENT.put(player.getUUID(), state);
        PacketDistributor.sendToPlayer(player, state);
    }

    /** When this player's shared cooldown ends (game time), for tests and the HUD. */
    public static long readyAt(ServerPlayer player) { return READY_AT.getOrDefault(player.getUUID(), Long.MIN_VALUE); }

    /** Forgets cooldowns (tests run several finishers back to back). */
    public static void resetCooldown(ServerPlayer player) { READY_AT.remove(player.getUUID()); }

    private static long now(ServerPlayer player) { return player.serverLevel().getGameTime(); }

    /** This power's signature finisher, if one is built. */
    public static Finisher forPower(ResourceLocation power) {
        for (Finisher f : FINISHERS) if (f.power().equals(power)) return f;
        return null;
    }
}
