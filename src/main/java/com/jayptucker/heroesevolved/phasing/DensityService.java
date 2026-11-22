package com.jayptucker.heroesevolved.phasing;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.PowerBalance;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.DensityPayload;
import com.jayptucker.heroesevolved.progression.EvolutionTrials;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Density, reworked as earthbending (the registry id stays {@code phasing} so saved worlds keep the power). The body is
 * built on Hung Gar: power comes up out of the ground through a rooted stance.
 * <ul>
 *   <li><b>Horse Stance</b> (G): drop low and set your feet. No knockback, stone-hard skin, heavy fists, slow feet, and
 *   Root fills twice as fast. Holding it costs stamina. A fall in stance ends in a shockwave, not a broken leg.</li>
 *   <li><b>Root</b>: standing planted on earth fills it; leaving the ground empties it. Every earth move hits up to a
 *   quarter harder at full Root ({@link #root}).</li>
 *   <li><b>Footing</b>: rock comes from natural ground ({@link EarthGeometry#footing}).</li>
 *   <li><b>Earth Shackle</b> ({@link #embed}): stone climbs a foe's legs and holds them. The hold is the old Embed.</li>
 *   <li><b>Tunnel</b> ({@link #sink} and {@link #surface}, a skill and the style's back dodge): through the ground on
 *   your Breath.</li>
 * </ul>
 * The thrown rocks are {@link Stones}; pillars, walls and the Rising Mountain are {@link Earthworks}; the fighting style
 * is {@link EarthStyle}; the tree is {@link DensitySkills}.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class DensityService {
    /** Standing, and Horse Stance. (The old dial's notches: only these two are left.) */
    public static final int NEUTRAL = 0, FULL_DENSE = 2;
    public static final int BREATH = 100;
    private static final ResourceLocation STANCE = id("density_dial");
    public static final ResourceLocation SUNK = id("density_sunk");
    private static final ResourceLocation SUNK_SLOW = id("density_sunk_slow");
    private static final ResourceLocation PINNED = id("density_pinned");
    private static final ResourceLocation FIST = id("density_fist");

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, path); }

    private static final class State {
        final ServerPlayer player;
        int notch, applied = Integer.MIN_VALUE, sentRoot = Integer.MIN_VALUE, sentBreath = Integer.MIN_VALUE;
        boolean sentStance;
        /** The stance was given by a move for its free time (Stone Skin, Unmoving Mountain, the awakening): it ends with it. */
        boolean lent;
        long freeUntil;
        Vec3 last;
        boolean sunk;
        float breath = BREATH, root;
        Vec3 sunkSafe;
        double earthBank;
        State(ServerPlayer player) { this.player = player; last = player.position(); }
    }

    /** An enemy held in the ground. */
    private static final class Embedded {
        final LivingEntity target;
        final Entity owner;
        final Vec3 at;
        final double depth;
        long until;
        final boolean entombed;
        /** Ticks Set in Stone has added to the hold; whether Crush has already squeezed it. */
        int extended;
        boolean crushed;
        Embedded(LivingEntity target, Entity owner, Vec3 at, double depth, long until, boolean entombed) {
            this.target = target; this.owner = owner; this.at = at; this.depth = depth; this.until = until; this.entombed = entombed;
        }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<UUID, Embedded> EMBEDDED = new HashMap<>();
    /** A body that cannot heal until this tick (Sylar's and the gifted mobs' Reach Through still use it). */
    private static final Map<UUID, Long> SEALED = new HashMap<>();

    private DensityService() {}

    private static long now(MinecraftServer server) { return server.overworld().getGameTime(); }
    private static long now(Entity e) { return now(e.getServer()); }

    /** True for an earthbender, and for an empath borrowing Density (whose rocks and stance must outlive the press). */
    public static boolean isOwner(ServerPlayer player) {
        return (PlayerAbilityService.getData(player).assignedPower()
                .filter(e -> e.getKey().equals(ModAbilities.PHASING_ID) && e.getValue().isUnlocked()).isPresent()
                || com.jayptucker.heroesevolved.mimicry.MimicryService.isBorrowingDensity(player))
                && !EclipseService.arePowersSuppressed(player);
    }

    private static State state(ServerPlayer p) { return STATES.computeIfAbsent(p.getUUID(), id -> new State(p)); }

    /** {@link #NEUTRAL} standing, {@link #FULL_DENSE} in Horse Stance. Anything without the power is neutral. */
    public static int notch(Entity e) { State s = STATES.get(e.getUUID()); return s == null ? NEUTRAL : s.notch; }
    /** True in Horse Stance (or Stone Skin, which holds it). */
    public static boolean dense(Entity e) { return notch(e) > 0; }
    public static boolean stance(Entity e) { return dense(e); }

    /** A bender's Root, 0 to 1. */
    public static float root(Entity e) { State s = STATES.get(e.getUUID()); return s == null ? 0 : s.root; }
    /** Fills Root at once (a finisher's reward, Wait and Listen). */
    public static void fillRoot(ServerPlayer p) { state(p).root = 1; }
    /** The multiplier Root puts on a bender's earth. */
    public static float rooted(Entity e) { return EarthGeometry.rooted(root(e)); }

    /** The earth under a bender's feet (metal too, with Metalbending), or null. */
    public static BlockState footing(ServerPlayer p) {
        return EarthGeometry.footing(p, SkillTreeService.has(p, DensitySkills.METAL));
    }

    /** True on both sides while a body travels under the ground (a synced, zero-strength attribute marker). */
    public static boolean sunk(Player player) {
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        return gravity != null && gravity.hasModifier(SUNK);
    }

    /** Bosses: the earth can hurt them, but it doesn't hold, drag, lift or carry them. */
    public static boolean immovable(Entity e) { return e.getType().is(Tags.EntityTypes.BOSSES); }

    public static boolean embedded(Entity e) { return EMBEDDED.containsKey(e.getUUID()); }
    /** Where the ground's surface is round a body held in it (its feet if it isn't held). */
    public static Vec3 surfaceOf(Entity e) {
        Embedded held = EMBEDDED.get(e.getUUID());
        return held == null ? e.position() : held.at.add(0, held.depth, 0);
    }

    /** Whoever put a body in the ground, or null. */
    public static Entity holder(Entity e) { Embedded held = EMBEDDED.get(e.getUUID()); return held == null ? null : held.owner; }
    /** True while the ground has closed right over a body (Entombed). */
    public static boolean buried(Entity e) { Embedded held = EMBEDDED.get(e.getUUID()); return held != null && held.depth >= e.getBbHeight(); }
    /** True once Crush has squeezed a held body. */
    public static boolean crushed(Entity e) { Embedded held = EMBEDDED.get(e.getUUID()); return held != null && held.crushed; }

    /** Holds an embedded body {@code ticks} longer, never past {@code max} added in all (Set in Stone). */
    public static boolean extend(Entity e, int ticks, int max) {
        Embedded held = EMBEDDED.get(e.getUUID());
        if (held == null || held.extended >= max) return false;
        int add = Math.min(ticks, max - held.extended);
        held.extended += add; held.until += add;
        return true;
    }

    /** Lets a body up out of the ground at once. */
    public static void release(Entity e) { Embedded held = EMBEDDED.get(e.getUUID()); if (held != null) free(held); }

    /**
     * Entombed: a body already held in the ground sinks right under it for {@code ticks}, taking damage every second.
     * False (and it stays as it was) if there is no room under it.
     */
    public static boolean entomb(LivingEntity target, Entity owner, int ticks) {
        Embedded held = EMBEDDED.remove(target.getUUID());
        if (held == null) return false;
        hold(target, held.at.add(0, held.depth, 0));
        if (embed(target, owner, ticks, true, 1.05, 2.3)) return true;
        EMBEDDED.put(target.getUUID(), held);
        hold(target, held.at);
        return false;
    }
    public static float breath(ServerPlayer p) { State s = STATES.get(p.getUUID()); return s == null ? BREATH : s.breath; }

    static int tier(ServerPlayer p) { return Math.max(1, PlayerProgressionService.getLevel(p)); }

    // ------------------------------------------------------------------
    // Horse Stance
    // ------------------------------------------------------------------

    /** G: drop into Horse Stance, or stand up out of it. Shift + G is Seismic Stomp ({@link DensitySkills#stomp}). */
    public static boolean stanceKey(ServerPlayer p, boolean shift) {
        State s = state(p);
        // Underground, the stance key brings you up (with the uppercut).
        if (s.sunk) return surface(p, true);
        if (shift) return DensitySkills.stomp(p);
        s.lent = false;
        set(p, s, s.notch == NEUTRAL ? FULL_DENSE : NEUTRAL);
        return true;
    }

    /** Snaps to {@code notch} ({@link #NEUTRAL} or {@link #FULL_DENSE}), upkeep-free for {@code freeTicks}. */
    public static void setNotch(ServerPlayer p, int notch, int freeTicks) {
        State s = state(p);
        if (s.sunk) return;
        boolean already = s.notch > 0;
        set(p, s, notch > 0 ? FULL_DENSE : NEUTRAL);
        if (notch <= 0) s.lent = false;
        else if (freeTicks > 0 && !already) s.lent = true;
        if (freeTicks > 0) s.freeUntil = Math.max(s.freeUntil, now(p) + freeTicks);
    }

    private static void set(ServerPlayer p, State s, int notch) {
        if (notch == s.notch) return;
        s.notch = notch;
        apply(p, s);
        ServerLevel level = p.serverLevel();
        Vec3 feet = p.position();
        if (notch > 0) {
            ArmPosePayload.show(p, ArmPose.EARTH_STANCE, 20);
            DensityGeometry.show(level, Piece.EARTH_STANCE, feet, feet, 1, p);
            level.playSound(null, p.blockPosition(), SoundEvents.DEEPSLATE_PLACE, SoundSource.PLAYERS, 1, .5F);
            level.playSound(null, p.blockPosition(), SoundEvents.ROOTED_DIRT_PLACE, SoundSource.PLAYERS, .8F, .6F);
        } else {
            level.playSound(null, p.blockPosition(), SoundEvents.GRAVEL_STEP, SoundSource.PLAYERS, .7F, .8F);
        }
        p.displayClientMessage(Component.translatable(notch > 0 ? "density.heroesevolved.stance" : "density.heroesevolved.stand")
                .withStyle(notch > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
        sync(p, s);
    }

    private static void modifier(LivingEntity e, Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = e.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        if (amount != 0) instance.addTransientModifier(new AttributeModifier(id, amount, operation));
    }

    /** Horse Stance's body, as native, synced attributes so the client moves the same way. */
    private static void apply(ServerPlayer p, State s) {
        if (s.applied == s.notch) return;
        s.applied = s.notch;
        var plus = AttributeModifier.Operation.ADD_VALUE;
        var times = AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
        boolean on = s.notch > 0;
        modifier(p, Attributes.FALL_DAMAGE_MULTIPLIER, STANCE, on ? -1 : 0, times);
        modifier(p, Attributes.ATTACK_DAMAGE, FIST, on ? 4 : 0, plus);
        modifier(p, Attributes.ARMOR, STANCE, on ? 6 : 0, plus);
        modifier(p, Attributes.ARMOR_TOUGHNESS, STANCE, on ? 2 : 0, plus);
        modifier(p, Attributes.KNOCKBACK_RESISTANCE, STANCE, on ? 1 : 0, plus);
        modifier(p, Attributes.MOVEMENT_SPEED, STANCE, on ? -.35 : 0, times);
        modifier(p, Attributes.JUMP_STRENGTH, STANCE, on ? -.3 : 0, times);
        modifier(p, Attributes.BLOCK_BREAK_SPEED, STANCE, on ? 1 : 0, times);
    }

    private static void clearModifiers(ServerPlayer p) {
        for (var attribute : List.of(Attributes.GRAVITY, Attributes.FALL_DAMAGE_MULTIPLIER, Attributes.ATTACK_DAMAGE, Attributes.ARMOR,
                Attributes.ARMOR_TOUGHNESS, Attributes.KNOCKBACK_RESISTANCE, Attributes.MOVEMENT_SPEED, Attributes.BLOCK_BREAK_SPEED,
                Attributes.JUMP_STRENGTH)) {
            AttributeInstance instance = p.getAttribute(attribute);
            if (instance != null) { instance.removeModifier(STANCE); instance.removeModifier(SUNK); instance.removeModifier(SUNK_SLOW); }
        }
        AttributeInstance damage = p.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) damage.removeModifier(FIST);
    }

    private static void sync(ServerPlayer p, State s) {
        int root = Math.round(s.root * 100), breath = s.sunk ? Math.round(s.breath) : -1;
        boolean stance = s.notch > 0;
        if (root == s.sentRoot && breath == s.sentBreath && stance == s.sentStance) return;
        s.sentRoot = root; s.sentBreath = breath; s.sentStance = stance;
        DensityPayload.send(p, root, stance, breath);
    }

    /** Root: planted on earth it fills (twice as fast in stance, half as fast on the move); in the air it drains. */
    private static void tickRoot(ServerPlayer p, State s) {
        boolean planted = p.onGround() && !p.isPassenger() && footing(p) != null;
        if (planted) {
            double moved = p.position().subtract(s.last).horizontalDistance();
            float gain = EarthGeometry.ROOT_GAIN * (s.notch > 0 ? 2 : 1) * (moved > .08 ? .5F : 1)
                    * (SkillTreeService.has(p, DensitySkills.SEI_PING_MA) ? 1.5F : 1);
            s.root = Math.min(1, s.root + gain);
        } else if (!p.onGround() && !(s.notch > 0 && SkillTreeService.has(p, DensitySkills.SEI_PING_MA))) {
            s.root = Math.max(0, s.root - EarthGeometry.ROOT_LOSS);
        }
    }

    /** Earth moved counts toward the Evolved trial (rocks thrown, pillars raised, ground tunnelled or ridden). */
    public static void bankEarth(ServerPlayer p, double blocks) {
        State s = state(p);
        s.earthBank += blocks;
        if (s.earthBank >= 1) {
            int whole = (int) s.earthBank;
            s.earthBank -= whole;
            EvolutionTrials.advance(p, ModAbilities.PHASING_ID, whole);
        }
    }

    // ------------------------------------------------------------------
    // Earth Shackle
    // ------------------------------------------------------------------

    /** Z, Earth Shackle: stone climbs the legs of the enemy you look at and holds them, if they stand on earth. */
    public static boolean shackle(ServerPlayer p, int tier, double range) {
        LivingEntity target = Targeting.livingEntityInSight(p, range, t -> ExpansionSkills.hostile(p, t) && !embedded(t));
        if (target == null) return false;
        boolean metal = SkillTreeService.has(p, DensitySkills.METAL);
        BlockState under = EarthGeometry.earthUnder(target.level(), target.position(), metal);
        if (under == null) { p.displayClientMessage(Component.translatable("density.heroesevolved.no_earth_there"), true); return false; }
        boolean mud = EarthGeometry.Material.of(under) == EarthGeometry.Material.MUD || com.jayptucker.heroesevolved.hydro.HydroService.soaked(target);
        int ticks = Math.round((EarthGeometry.SHACKLE_TICKS + 20 * tier) * (mud ? EarthGeometry.MUD_HOLD : 1) * rooted(p));
        ArmPosePayload.show(p, ArmPose.EARTH_STOMP, 12);
        if (!embed(target, p, ticks, false, .45)) return false;
        Vec3 ground = surfaceOf(target);
        DensityGeometry.show(p.serverLevel(), Piece.EARTH_SHACKLE, ground, p.position(), target.getBbWidth() + (mud ? EarthGeometry.MUD_HOLD * 10 : 0), target);
        p.serverLevel().playSound(null, target.blockPosition(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1, .5F);
        bankEarth(p, 2);
        return true;
    }

    /**
     * Earth Shackle's second press, free: Crush. The stone squeezes the body you hold, hurting it and holding it longer.
     * With Entombed, a press on a body already crushed closes the ground right over it.
     */
    public static boolean crush(AbilityUseContext c) {
        ServerPlayer p = c.player();
        LivingEntity t = Targeting.livingEntityInSight(p, 16, e -> holder(e) == p && !buried(e));
        if (t == null) return false;
        Embedded held = EMBEDDED.get(t.getUUID());
        if (held.crushed) return DensitySkills.entomb(p, t);
        held.crushed = true;
        held.until += EarthGeometry.CRUSH_HOLD;
        t.invulnerableTime = 0;
        t.hurt(p.damageSources().playerAttack(p), (EarthGeometry.CRUSH_DAMAGE + c.abilityLevel()) * rooted(p));
        ArmPosePayload.show(p, ArmPose.EARTH_CRUSH, 10);
        Vec3 ground = surfaceOf(t);
        DensityGeometry.show(p.serverLevel(), Piece.EARTH_CRUSH, ground, ground, t.getBbWidth(), t);
        p.serverLevel().playSound(null, t.blockPosition(), SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.PLAYERS, 1.1F, .5F);
        p.serverLevel().playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, .8F, .6F);
        return true;
    }

    /**
     * Sinks {@code target} into the ground beneath it by {@code fraction} of its height and holds it there. It can
     * still fight back, and it can struggle free. False if there is no ground to sink into.
     */
    public static boolean embed(LivingEntity target, Entity owner, int ticks, boolean entombed, double fraction) {
        return embed(target, owner, ticks, entombed, fraction, 1.3);
    }

    /** As above, sinking no deeper than {@code maxDepth} blocks (Entombed buries a body right under). */
    public static boolean embed(LivingEntity target, Entity owner, int ticks, boolean entombed, double fraction, double maxDepth) {
        if (!(target.level() instanceof ServerLevel level) || embedded(target) || immovable(target)
                || target.isPassenger() || target.getBbHeight() > 4) return false;
        double depth = Math.min(maxDepth, target.getBbHeight() * fraction);
        Vec3 at = target.position().subtract(0, depth, 0);
        BlockPos below = BlockPos.containing(target.position().subtract(0, 0.2, 0));
        if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return false;
        for (double y = 0.1; y < depth + 0.1; y += 0.5) {
            BlockPos pos = BlockPos.containing(at.add(0, y - 0.1, 0));
            var state = level.getBlockState(pos);
            if (state.getDestroySpeed(level, pos) < 0) return false;
            if (owner instanceof ServerPlayer p && !Passage.passable(p, pos)) return false;
        }
        EMBEDDED.put(target.getUUID(), new Embedded(target, owner, at, depth, now(target) + ticks, entombed));
        if (target instanceof ServerPlayer pinned) {
            modifier(pinned, Attributes.MOVEMENT_SPEED, PINNED, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            modifier(pinned, Attributes.JUMP_STRENGTH, PINNED, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            pinned.displayClientMessage(Component.translatable("density.heroesevolved.embedded").withStyle(ChatFormatting.RED), true);
        }
        hold(target, at);
        AbilityEffects.send(level, Kind.DENSITY_EMBED, target.position().add(0, depth, 0), target.position().add(0, depth + 1, 0), target.getBbWidth());
        level.playSound(null, target.blockPosition(), SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1, 0.5F);
        return true;
    }

    private static void hold(LivingEntity target, Vec3 at) {
        if (target instanceof ServerPlayer player) {
            if (player.position().distanceToSqr(at) > 0.09) player.teleportTo(player.serverLevel(), at.x, at.y, at.z, player.getYRot(), player.getXRot());
        } else target.teleportTo(at.x, at.y, at.z);
        target.setDeltaMovement(Vec3.ZERO);
        target.resetFallDistance();
    }

    private static void tickEmbedded(long now) {
        for (Embedded e : List.copyOf(EMBEDDED.values())) {
            LivingEntity t = e.target;
            if (!t.isAlive() || t.isRemoved() || now >= e.until || t.isPassenger()) { free(e); continue; }
            // Struggling: a player fighting the ground (or any mob, now and then) works loose sooner.
            boolean set = e.owner instanceof ServerPlayer holder && SkillTreeService.has(holder, DensitySkills.SET_IN_STONE);
            if (set) { /* Set in Stone: there is no working loose. */ }
            else if (t instanceof ServerPlayer player && player.position().distanceToSqr(e.at) > 0.09) e.until -= 3;
            else if (!(t instanceof ServerPlayer) && now % 20 == 0 && t.getRandom().nextInt(4) == 0) e.until -= 10;
            hold(t, e.at);
            if (e.entombed && now % 20 == 0 && t.level() instanceof ServerLevel)
                t.hurt(e.owner instanceof LivingEntity owner ? t.damageSources().indirectMagic(owner, owner) : t.damageSources().magic(), 2);
            if (now % 10 == 0 && t.level() instanceof ServerLevel level && e.depth < t.getBbHeight()) {
                Vec3 ground = e.at.add(0, e.depth, 0);
                AbilityEffects.send(level, Kind.DENSITY_EMBED, ground, ground.add(0, 1, 0), t.getBbWidth());
            }
        }
    }

    /** Pops an embedded body back up out of the ground. */
    private static void free(Embedded e) {
        EMBEDDED.remove(e.target.getUUID());
        LivingEntity t = e.target;
        if (t instanceof ServerPlayer player) {
            AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED), jump = player.getAttribute(Attributes.JUMP_STRENGTH);
            if (speed != null) speed.removeModifier(PINNED);
            if (jump != null) jump.removeModifier(PINNED);
        }
        if (!t.isAlive() || t.isRemoved() || !(t.level() instanceof ServerLevel level)) return;
        Vec3 up = e.at.add(0, e.depth + 0.05, 0);
        if (!SpeedService.safe(t, up)) up = openNear(t, up);
        if (up != null) {
            if (t instanceof ServerPlayer player) player.teleportTo(level, up.x, up.y, up.z, player.getYRot(), player.getXRot());
            else t.teleportTo(up.x, up.y, up.z);
        }
        t.setDeltaMovement(0, e.depth >= t.getBbHeight() ? 0.6 : 0.25, 0); t.hurtMarked = true;
        if (t instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
        BlockPos below = BlockPos.containing(e.at.add(0, e.depth - .5, 0));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)), up == null ? t.getX() : up.x,
                e.at.y + e.depth + .1, up == null ? t.getZ() : up.z, 10, t.getBbWidth() * .4, .05, t.getBbWidth() * .4, .05);
    }

    /** The nearest open space for a body, searching up and around. */
    static Vec3 openNear(Entity body, Vec3 from) {
        for (int r = 0; r <= 4; r++)
            for (int dy = 0; dy <= r + 1; dy++)
                for (int dx = -r; dx <= r; dx++)
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        Vec3 point = new Vec3(Math.floor(from.x) + 0.5 + dx, Math.floor(from.y) + dy, Math.floor(from.z) + 0.5 + dz);
                        if (SpeedService.safe(body, point)) return point;
                    }
        return null;
    }

    /** No healing for {@code ticks}: not regeneration, not food, not a healer's power. */
    public static void seal(LivingEntity target, int ticks) {
        if (target.getServer() == null) return;
        SEALED.put(target.getUUID(), now(target) + ticks);
        target.removeEffect(MobEffects.REGENERATION);
    }

    public static boolean sealed(Entity e) { return e.getServer() != null && SEALED.getOrDefault(e.getUUID(), 0L) > now(e); }

    // ------------------------------------------------------------------
    // Tunnel (sink and surface)
    // ------------------------------------------------------------------

    /** Tunnel: drop into the ground and move through it. Press again to surface, under an enemy if you can. */
    public static boolean sinkOrSurface(ServerPlayer p, int tier) {
        State s = state(p);
        return s.sunk ? surface(p, true) : sink(p, tier);
    }

    public static boolean sinking(ServerPlayer p) { State s = STATES.get(p.getUUID()); return s != null && s.sunk; }

    static boolean sink(ServerPlayer p, int tier) {
        State s = state(p);
        if (s.sunk || p.isPassenger() || EntityCarryService.isCarrying(p)) return false;
        if (!p.onGround() || footing(p) == null) { p.displayClientMessage(Component.translatable("density.heroesevolved.need_ground"), true); return false; }
        Vec3 to = p.position().subtract(0, 2.4, 0);
        for (double y = -2.4; y < 0; y += 0.5)
            for (BlockPos pos : column(p, p.position().add(0, y, 0)))
                if (!Passage.passable(p, pos)) { p.displayClientMessage(Component.translatable("phasing.heroesevolved.no_passage"), true); return false; }
        if (SpeedService.safe(p, to)) { p.displayClientMessage(Component.translatable("density.heroesevolved.need_ground"), true); return false; }
        Vec3 from = p.position();
        s.sunk = true;
        s.breath = BREATH;
        s.sunkSafe = from;
        // Standing underground: the stance's own modifiers would fight the zero gravity that keeps you level.
        s.notch = NEUTRAL; s.lent = false; s.applied = Integer.MIN_VALUE; apply(p, s);
        modifier(p, Attributes.GRAVITY, SUNK, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modifier(p, Attributes.MOVEMENT_SPEED, SUNK_SLOW, -0.3 + tier * 0.04, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        p.teleportTo(p.serverLevel(), to.x, to.y, to.z, p.getYRot(), p.getXRot());
        p.setDeltaMovement(Vec3.ZERO); p.hurtMarked = true;
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        ArmPosePayload.show(p, ArmPose.SINK, 14);
        p.displayClientMessage(Component.translatable("density.heroesevolved.tunnel_up").withStyle(ChatFormatting.GOLD), true);
        DensityGeometry.show(p.serverLevel(), Piece.EARTH_TUNNEL, from, from.subtract(0, 1, 0), 1, null);
        p.serverLevel().playSound(null, BlockPos.containing(from), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1, 0.4F);
        p.serverLevel().playSound(null, BlockPos.containing(from), SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1, 0.5F);
        sync(p, s);
        return true;
    }

    private static List<BlockPos> column(Entity body, Vec3 feet) {
        var box = body.getBoundingBox().move(feet.subtract(body.position())).deflate(0.01);
        List<BlockPos> blocks = new ArrayList<>();
        BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                .forEach(pos -> blocks.add(pos.immutable()));
        return blocks;
    }

    /** Up through the ground to the open space above. Chosen (not forced), an enemy right above takes an uppercut. */
    public static boolean surface(ServerPlayer p, boolean chosen) {
        State s = STATES.get(p.getUUID());
        if (s == null || !s.sunk) return false;
        ServerLevel level = p.serverLevel();
        Vec3 from = p.position(), to = null;
        for (int up = 1; up <= 8 && to == null; up++) {
            Vec3 point = new Vec3(from.x, Math.floor(from.y) + up, from.z);
            if (SpeedService.safe(p, point)) to = point;
        }
        if (to == null) to = openNear(p, from.add(0, 1, 0));
        if (to == null) to = s.sunkSafe;
        endSink(p, s);
        p.teleportTo(level, to.x, to.y, to.z, p.getYRot(), p.getXRot());
        p.resetFallDistance();
        DensityGeometry.show(level, Piece.EARTH_TUNNEL, to, to.add(0, 1, 0), chosen ? 1.4F : .8F, null);
        level.playSound(null, p.blockPosition(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1, 0.8F);
        if (chosen) {
            final Vec3 landing = to;
            int tier = tier(p);
            boolean uproot = SkillTreeService.has(p, DensitySkills.UPROOT);
            for (LivingEntity above : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(2.5, 2.5, 2.5),
                    t -> ExpansionSkills.hostile(p, t) && t.position().distanceToSqr(landing) < 2.5 * 2.5)) {
                // Uproot: a body you hold in the ground is torn out of it, twice as hard and twice as high.
                boolean torn = uproot && embedded(above);
                if (torn) release(above);
                above.invulnerableTime = 0;
                above.hurt(p.damageSources().playerAttack(p), (6 + tier) * (torn ? 2 : 1));
                if (!immovable(above)) { above.setDeltaMovement(above.getDeltaMovement().add(0, torn ? 2.0 : 1.0, 0)); above.hurtMarked = true; }
                if (uproot) DensitySkills.uprooted(p, above, torn);
                if (above instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
                level.playSound(null, above.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1, 0.6F);
            }
            p.setDeltaMovement(0, 0.5, 0); p.hurtMarked = true;
            p.connection.send(new ClientboundSetEntityMotionPacket(p));
        }
        return true;
    }

    private static void endSink(ServerPlayer p, State s) {
        s.sunk = false;
        for (var attribute : List.of(Attributes.GRAVITY, Attributes.MOVEMENT_SPEED)) {
            AttributeInstance instance = p.getAttribute(attribute);
            if (instance != null) { instance.removeModifier(SUNK); instance.removeModifier(SUNK_SLOW); }
        }
        sync(p, s);
    }

    private static void tickSunk(ServerPlayer p, State s, long now) {
        ServerLevel level = p.serverLevel();
        // Out into open air (a cave, a cliff face): you simply come out.
        if (SpeedService.safe(p, p.position())) { endSink(p, s); p.resetFallDistance(); return; }
        // Bedrock, barriers and protected ground stay solid, even to you.
        for (BlockPos pos : column(p, p.position()))
            if (!Passage.passable(p, pos)) {
                Vec3 back = s.sunkSafe;
                p.teleportTo(level, back.x, back.y, back.z, p.getYRot(), p.getXRot());
                break;
            }
        double moved = p.position().distanceTo(s.last);
        if (moved < 4) bankEarth(p, moved);
        if (!SpeedService.safe(p, p.position())) s.sunkSafe = p.position();
        int tier = tier(p);
        s.breath -= 0.5F / (1 + (tier - 1) * 0.15F);
        if (s.breath <= 0) {
            surface(p, false);
            p.hurt(p.damageSources().generic(), 4);
            p.displayClientMessage(Component.translatable("density.heroesevolved.out_of_breath").withStyle(ChatFormatting.RED), true);
            return;
        }
        // Tremor sense: the footsteps of everything walking on the ground above, felt through the rock.
        if (now % 10 == 0) feel(p, 14, 8, p.getEyeY() - .5);
    }

    /** Footsteps of everything standing on the ground within {@code radius}, sent to {@code p} alone as ripples. */
    static int feel(ServerPlayer p, double radius, double height, double above) {
        int felt = 0;
        for (LivingEntity walker : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(radius, height, radius),
                t -> t != p && t.onGround() && !t.isInWater() && t.getY() > above)) {
            PacketDistributor.sendToPlayer(p, new AbilityEffectPayload(Kind.DENSITY_FOOTSTEP, walker.position(),
                    walker.position().add(0, 1, 0), ExpansionSkills.hostile(p, walker) ? 1.2F : 0.8F));
            felt++;
        }
        return felt;
    }

    // ------------------------------------------------------------------
    // Shockwaves
    // ------------------------------------------------------------------

    /** Ground struck by something very heavy: everything nearby is hurt and thrown back (or, with Pile Driver, rooted). */
    public static int shockwave(ServerPlayer p, Vec3 at, double radius, float damage) {
        ServerLevel level = p.serverLevel();
        int struck = 0;
        for (LivingEntity t : ExpansionSkills.enemies(p, at, radius)) {
            double falloff = 1 - Math.sqrt(t.position().distanceToSqr(at)) / (radius + 1);
            t.invulnerableTime = 0;
            t.hurt(p.damageSources().playerAttack(p), (float) (damage * Math.max(0.4, falloff)));
            struck++;
            if (DensitySkills.crater(p, t)) continue;
            Vec3 away = new Vec3(t.getX() - at.x, 0, t.getZ() - at.z);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : away.normalize();
            t.setDeltaMovement(away.scale(0.9 * falloff + 0.3).add(0, 0.45, 0)); t.hurtMarked = true;
            if (t instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
        }
        com.jayptucker.heroesevolved.network.CameraShakePayload.sendAround(level, at, radius + 6, 0.7F, 8);
        BlockPos below = BlockPos.containing(at.subtract(0, 0.5, 0));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)), at.x, at.y + 0.1, at.z,
                24, radius * 0.4, 0.1, radius * 0.4, 0.15);
        return struck;
    }

    /** A body in Horse Stance landing from {@code distance} blocks: a shockwave instead of a broken leg. */
    public static void impact(ServerPlayer p, float distance) {
        double radius = Math.min(6, 2 + distance / 4);
        shockwave(p, p.position(), radius, Math.min(12, distance * 0.8F) * rooted(p));
        AbilityEffects.send(p.serverLevel(), Kind.DENSITY_SLAM, p.position(), p.position().add(0, 1, 0), radius);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 0.5F);
    }

    // ------------------------------------------------------------------
    // Skill tree
    // ------------------------------------------------------------------

    /** The three branch actives and techniques live in {@link DensitySkills}. */
    public static boolean skill(AbilityUseContext c, int branch) { return DensitySkills.skill(c, branch); }

    public static boolean technique(AbilityUseContext c, String mechanic) { return DensitySkills.technique(c, mechanic); }

    // ------------------------------------------------------------------
    // Awakening, tick and events
    // ------------------------------------------------------------------

    /** The power wakes: the feet set themselves, and the ground answers. */
    public static void awaken(ServerPlayer player) {
        State s = state(player);
        s.root = 1;
        setNotch(player, FULL_DENSE, 100);
        player.displayClientMessage(Component.translatable("phasing.heroesevolved.awakened").withStyle(ChatFormatting.GRAY), false);
    }

    public static void tick(MinecraftServer server) {
        long now = now(server);
        for (State s : List.copyOf(STATES.values())) {
            ServerPlayer p = s.player;
            if (p.isRemoved() || !p.isAlive() || !isOwner(p)) { forget(p); continue; }
            if (s.sunk) tickSunk(p, s, now);
            else tickRoot(p, s);
            s.last = p.position();
            if (s.lent && now >= s.freeUntil) { s.lent = false; set(p, s, NEUTRAL); }
            if (s.notch > 0 && now >= s.freeUntil) {
                // Horse Stance is held: it pays as it goes, and a bender too tired to hold it stands up.
                double perSecond = PowerBalance.scaledDrain("phasing_shift", 3.3) * (SkillTreeService.has(p, DensitySkills.LISTEN) ? .5 : 1);
                if (!PlayerEnergyService.drain(p, perSecond / 20)) {
                    set(p, s, NEUTRAL);
                    p.displayClientMessage(Component.translatable("phasing.heroesevolved.exhausted"), true);
                }
            }
            // Held: re-sent as it runs out, never over a move's own gesture (that made every move snap back to frame 0).
            if (s.notch > 0) {
                ArmPose playing = ArmPosePayload.playing(p);
                if (playing == null || playing == ArmPose.EARTH_STANCE && (now % 10 == 0)) ArmPosePayload.show(p, ArmPose.EARTH_STANCE, 14);
            }
            if (s.notch > 0 && now % 6 == 0 && p.onGround())
                AbilityEffects.send(p.serverLevel(), Kind.DENSITY_DENSE, p.position(), p.position().add(0, 1, 0), .5 + .5 * s.root);
            sync(p, s);
            if (!s.sunk && s.notch == NEUTRAL && now >= s.freeUntil && s.applied == NEUTRAL && s.root <= 0) STATES.remove(p.getUUID());
        }
        tickEmbedded(now);
        SEALED.values().removeIf(until -> until <= now);
    }

    /** Starts tracking a bender (every earth move asks, so Root starts filling the first time they bend). */
    static void track(ServerPlayer p) { state(p); }

    /** Every bender being tracked, by the server tick (test players and others outside the player list included). */
    static List<ServerPlayer> benders() { return STATES.values().stream().map(s -> s.player).toList(); }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void damage(LivingIncomingDamageEvent event) {
        LivingEntity hurt = event.getEntity();
        var source = event.getSource();
        // A body held in the ground isn't smothered by it: the hold is the damage.
        if (source.is(DamageTypes.IN_WALL) && embedded(hurt)) { event.setCanceled(true); return; }
        // A bender cannot suffocate in the earth they move.
        if (hurt instanceof ServerPlayer player && source.is(DamageTypes.IN_WALL) && isOwner(player)) { event.setCanceled(true); return; }
        // An enemy stuck in the ground cannot roll with a blow.
        if (embedded(hurt) && source.getEntity() != null) event.setAmount(event.getAmount() * 1.5F);
    }

    @SubscribeEvent public static void heal(LivingHealEvent event) {
        if (sealed(event.getEntity())) event.setCanceled(true);
    }

    /** Horse Stance: a fall ends in a shockwave instead of a broken leg. */
    @SubscribeEvent public static void fall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (Earthworks.cushioned(p)) { event.setDamageMultiplier(0); return; }
        if (notch(p) != FULL_DENSE || event.getDistance() < 3) return;
        event.setDamageMultiplier(0);
        impact(p, event.getDistance());
    }

    public static void forget(ServerPlayer player) {
        State s = STATES.remove(player.getUUID());
        if (s != null && s.sunk) {
            s.sunk = false;
            Vec3 out = openNear(player, player.position());
            if (out != null) player.teleportTo(player.serverLevel(), out.x, out.y, out.z, player.getYRot(), player.getXRot());
        }
        clearModifiers(player);
        if (s != null) DensityPayload.send(player, 0, false, -1);
        Embedded held = EMBEDDED.get(player.getUUID());
        if (held != null) free(held);
        DensitySkills.drop(player);
        Stones.forget(player);
        Earthworks.forget(player);
    }

    /** Logging in inside a wall (a crash while underground): step out to the nearest open space. */
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isOwner(player) && !SpeedService.safe(player, player.position())) {
            Vec3 out = openNear(player, player.position());
            if (out != null) player.teleportTo(player.serverLevel(), out.x, out.y, out.z, player.getYRot(), player.getXRot());
        }
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }

    @SubscribeEvent public static void changed(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { Stones.forget(player); Earthworks.forget(player); }
    }

    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
        Embedded held = EMBEDDED.get(event.getEntity().getUUID());
        if (held != null) free(held);
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (State s : List.copyOf(STATES.values())) forget(s.player);
        for (Embedded e : List.copyOf(EMBEDDED.values())) free(e);
        STATES.clear(); EMBEDDED.clear(); SEALED.clear();
        DensitySkills.clear();
        Stones.clear();
        Earthworks.clear();
    }
}
