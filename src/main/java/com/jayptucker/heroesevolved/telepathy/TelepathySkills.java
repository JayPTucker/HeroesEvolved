package com.jayptucker.heroesevolved.telepathy;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.PrivateGlow;
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.jayptucker.heroesevolved.telepathy.TelepathyGeometry.*;

/**
 * Telepathy's skill tree, built around the kit (Mind Threads, Strain, possession, illusions) rather than stat bonuses.
 * Three branches:
 * <ul>
 *   <li><b>Puppet Master</b> (the voice that commands): Whispered Orders, Drop It, Strings Attached, Persuasion →
 *   Silver Tongue → Eden's Voice.</li>
 *   <li><b>Mind Reader</b> (listening): Open Book, Far Hearing, Eavesdropper, Memory Walk → Deep Read → Lost in
 *   Thought.</li>
 *   <li><b>Dreamweaver</b> (minds together, false worlds): Shared Burden, Dream Double, Waking Fright, Hive Link →
 *   Shared Senses → Mind Network.</li>
 * </ul>
 * Far Hearing, Hive Link and Mind Network carry real voices over Simple Voice Chat ({@link MindVoiceRoutes},
 * {@link MindVoice}). Node ids are kept from the older tree, so purchases and prerequisites carry over.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class TelepathySkills {
    public static final String WHISPERED_ORDERS = "telepathy_0_0", DROP_IT = "telepathy_0_1", STRINGS_ATTACHED = "telepathy_0_2",
            PERSUASION = "telepathy_persuasion", SILVER_TONGUE = "telepathy_silver_tongue", EDENS_VOICE = "telepathy_edens_voice",
            OPEN_BOOK = "telepathy_1_0", FAR_HEARING = "telepathy_1_1", EAVESDROPPER = "telepathy_1_2",
            MEMORY_WALK = "telepathy_memory_walk", DEEP_READ = "telepathy_deep_read", LOST_IN_THOUGHT = "telepathy_dreamweaver",
            SHARED_BURDEN = "telepathy_2_0", DREAM_DOUBLE = "telepathy_2_1", WAKING_FRIGHT = "telepathy_2_2",
            HIVE_LINK = "telepathy_hive_link", SHARED_SENSES = "telepathy_shared_senses", MIND_NETWORK = "telepathy_mind_network";

    /** A player whose hands won't obey ({@code hands}: no attacks or items at all) or who can't bring themselves to strike {@code by}. */
    private record Compulsion(UUID by, long until, boolean hands) {}
    private record Persuading(ServerPlayer owner, List<LivingEntity> minds, long start, int ticks, double range) {}
    private record Persuaded(Mob mob, ServerPlayer owner, long until) {}
    private record Lost(Mob mob, List<Vec3> path, long until) {}
    private record Shaken(UUID by, long until) {}
    private static final class Listening {
        final ServerPlayer player; final long until; final double range;
        final Map<UUID, Integer> heard = new HashMap<>(); final Set<UUID> warned = new HashSet<>();
        final Map<UUID, Long> lastHeard = new HashMap<>();
        Listening(ServerPlayer player, long until, double range) { this.player = player; this.until = until; this.range = range; }
    }
    /** A group of linked minds: a Hive Link (with halos and senses) or Hive Mind's brief one (voices only). */
    private record Hive(UUID owner, Set<UUID> members, long until, boolean full) {}

    private static final Map<UUID, Long> DISARMED = new HashMap<>();
    private static final Map<UUID, Compulsion> COMPELLED = new HashMap<>();
    private static final Map<UUID, Persuading> PERSUADING = new HashMap<>();
    private static final Map<UUID, Persuaded> PERSUADED = new HashMap<>();
    private static final Map<UUID, Listening> LISTENING = new HashMap<>();
    private static final Map<UUID, Lost> LOST = new HashMap<>();
    private static final Map<UUID, Shaken> SHAKEN = new HashMap<>();
    private static final Map<UUID, Hive> HIVES = new HashMap<>(), STYLE_HIVES = new HashMap<>();
    /** A hive member last got a hunter's whisper. */
    private static final Map<UUID, Long> WHISPERED = new HashMap<>();
    /** When each listener was last told whose voice is in their head ("listener speaker"). */
    private static final Map<String, Long> PROJECTED = new HashMap<>();
    /** Telepaths speaking with their inner voice: only the minds they reach hear them. */
    private static final Set<UUID> INNER = new HashSet<>();

    private TelepathySkills() {}

    public static boolean has(ServerPlayer p, String node) { return SkillTreeService.has(p, node); }

    static long now(ServerPlayer p) { return p.serverLevel().getGameTime(); }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static boolean mind(LivingEntity t) {
        return t.isAlive() && !t.isSpectator() && !(t instanceof TemporalEchoEntity);
    }

    /** Bosses keep their weapons and can't be talked round. */
    public static boolean boss(LivingEntity t) {
        return t instanceof EnderDragon || t instanceof WitherBoss || t instanceof Warden
                || t instanceof com.jayptucker.heroesevolved.story.sylar.SylarEntity || t instanceof com.jayptucker.heroesevolved.story.elle.ElleEntity;
    }

    // ------------------------------------------------------------------
    // The three branch actives
    // ------------------------------------------------------------------

    public static boolean canUse(AbilityUseContext c, int branch) {
        if (!PowerRules.canAct(c.player())) return false;
        return branch != 0 || dropTarget(c.player()) != null;
    }

    public static boolean skill(AbilityUseContext c, int branch) {
        ServerPlayer p = c.player();
        return switch (branch) {
            case 0 -> dropIt(p);
            case 1 -> farHearing(p);
            default -> dreamDouble(p);
        };
    }

    /** A second press is free: it stops Far Hearing. */
    public static boolean followUp(AbilityUseContext c, int branch) {
        if (branch != 1 || !listening(c.player())) return false;
        stopListening(c.player(), true);
        return true;
    }

    static LivingEntity dropTarget(ServerPlayer p) {
        return Targeting.livingEntityInSight(p, 20, t -> t != p && mind(t)
                && (t instanceof Mob || t instanceof ServerPlayer other && StyleOps.isFoe(p, other)));
    }

    /**
     * Drop It: two fingers and one word pushed into a mind up to 20 blocks away. A mob forgets its target and can't attack
     * or shoot for four seconds (a mob you hold a 60+ thread to drops what it holds); a player's hands won't obey for two.
     */
    static boolean dropIt(ServerPlayer p) {
        LivingEntity t = dropTarget(p);
        if (t == null) {
            p.displayClientMessage(Component.translatable("technique.heroesevolved.drop_it.none"), true);
            return false;
        }
        long now = now(p);
        ServerLevel level = p.serverLevel();
        if (t instanceof ServerPlayer other) {
            COMPELLED.put(other.getUUID(), new Compulsion(p.getUUID(), now + HANDS_TICKS, true));
            other.stopUsingItem();
            other.displayClientMessage(Component.translatable("technique.heroesevolved.drop_it.player", p.getName())
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            ScreenTintPayload.send(other, 0x30A070FF, 20, ScreenTintPayload.Style.VIGNETTE);
        } else if (t instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
            DISARMED.put(mob.getUUID(), now + (boss(mob) ? DISARM_TICKS / 4 : DISARM_TICKS));
            if (mob instanceof Creeper creeper) creeper.setSwellDir(-1);
            ItemStack held = mob.getMainHandItem();
            if (!boss(mob) && !held.isEmpty() && TelepathyFocus.focus(p, mob) >= 60) {
                mob.spawnAtLocation(held.copy());
                mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
        }
        TelepathyFocus.strain(p, 6);
        show(level, Piece.TELE_COMMAND, hand(p, 1, .55), t.getEyePosition(), 1, p);
        ArmPosePayload.show(p, ArmPose.TELE_COMMAND, 14);
        sound(level, t.getEyePosition(), SoundEvents.ILLUSIONER_CAST_SPELL, 0.8F, 1.7F);
        return true;
    }

    /** Whether a mob is under Drop It and can't fight. */
    public static boolean disarmed(LivingEntity mob) {
        Long until = DISARMED.get(mob.getUUID());
        return until != null && mob.level().getGameTime() < until;
    }

    /**
     * Far Hearing: fingers to both temples, and for thirty seconds every voice within 160 blocks (320 with Eavesdropper)
     * reaches you. Players speaking on voice chat are heard from their direction, each marked for you alone; creatures
     * hunting within 48 blocks whisper where they are. A second press stops it.
     */
    static boolean farHearing(ServerPlayer p) {
        double range = has(p, EAVESDROPPER) ? HEARING_LONG : HEARING_RANGE;
        LISTENING.put(p.getUUID(), new Listening(p, now(p) + HEARING_TICKS, range));
        ArmPosePayload.show(p, ArmPose.TELE_LISTEN, 24);
        show(p.serverLevel(), Piece.TELE_LISTEN, p.position(), p.position(), 1, p);
        p.displayClientMessage(Component.translatable("technique.heroesevolved.far_hearing.start").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        sound(p.serverLevel(), p.getEyePosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 0.6F);
        return true;
    }

    public static boolean listening(ServerPlayer p) { return LISTENING.containsKey(p.getUUID()); }

    /** How far a listening telepath hears (0 when not listening). */
    static double hearingRange(ServerPlayer p) {
        Listening l = LISTENING.get(p.getUUID());
        return l == null ? 0 : l.range;
    }

    static void stopListening(ServerPlayer p, boolean tell) {
        if (LISTENING.remove(p.getUUID()) != null && tell)
            p.displayClientMessage(Component.translatable("technique.heroesevolved.far_hearing.end"), true);
    }

    /** Dream Double: the hands weave while the telepath picks what the victim sees (the illusion itself is {@link IllusionService}). */
    static boolean dreamDouble(ServerPlayer p) {
        if (!IllusionService.open(p)) return false;
        ArmPosePayload.show(p, ArmPose.TELE_WEAVE, 30);
        return true;
    }

    /** The illusion is chosen and planted: the telepath sees threads spin from their hands into the victim's head. */
    static void wove(ServerPlayer p, ServerPlayer victim) {
        ArmPosePayload.show(p, ArmPose.TELE_WEAVE, 20);
        showTo(p, Piece.TELE_WEAVE, hand(p, 1, .45), victim.getEyePosition(), 1, victim, 0);
    }

    // ------------------------------------------------------------------
    // The techniques (Persuasion, Memory Walk, Hive Link)
    // ------------------------------------------------------------------

    public static boolean technique(AbilityUseContext c, String mechanic) {
        ServerPlayer p = c.player();
        return switch (mechanic) {
            case "persuasion" -> persuasion(p);
            case "memory_walk" -> memoryWalk(p);
            case "hive_link" -> hiveLink(p);
            default -> false;
        };
    }

    private static boolean persuadable(ServerPlayer p, LivingEntity t) {
        if (t == p || !mind(t) || boss(t)) return false;
        if (t instanceof ServerPlayer other) return StyleOps.isFoe(p, other) && !other.isCreative();
        return t instanceof Mob mob && !(PERSUADED.get(mob.getUUID()) instanceof Persuaded old && old.owner == p);
    }

    /**
     * Persuasion: speak softly into a mind for two seconds (one with Silver Tongue); if it stays in your sight it's won
     * over. A mind you hold a 60+ thread to turns at once; with Eden's Voice every mind you hold a thread to hears you too.
     */
    static boolean persuasion(ServerPlayer p) {
        boolean silver = has(p, SILVER_TONGUE), eden = has(p, EDENS_VOICE);
        double range = silver ? PERSUADE_LONG : PERSUADE_RANGE;
        List<LivingEntity> minds = new ArrayList<>();
        LivingEntity aimed = Targeting.livingEntityInSight(p, range, t -> persuadable(p, t));
        if (aimed != null) minds.add(aimed);
        if (eden) for (LivingEntity t : TelepathyFocus.targets(p))
            if (minds.size() < 5 && !minds.contains(t) && persuadable(p, t) && p.hasLineOfSight(t) && t.distanceTo(p) <= range * 2) minds.add(t);
        if (minds.isEmpty()) {
            p.displayClientMessage(Component.translatable("technique.heroesevolved.persuasion.none"), true);
            return false;
        }
        int ticks = silver ? PERSUADE_QUICK : PERSUADE_TICKS;
        List<LivingEntity> slow = new ArrayList<>();
        for (LivingEntity t : minds) {
            if (TelepathyFocus.focus(p, t) >= 60) win(p, t);
            else slow.add(t);
        }
        if (!slow.isEmpty()) {
            PERSUADING.put(p.getUUID(), new Persuading(p, slow, now(p), ticks, range));
            for (LivingEntity t : slow) {
                show(p.serverLevel(), Piece.TELE_PERSUADE, hand(p, 1, .5), t.getEyePosition(), ticks, t);
                if (t instanceof ServerPlayer other)
                    other.displayClientMessage(Component.translatable("technique.heroesevolved.persuasion.start").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            ArmPosePayload.show(p, ArmPose.TELE_PERSUADE, ticks + 4);
        } else {
            ArmPosePayload.show(p, ArmPose.TELE_PERSUADE, 14);
        }
        sound(p.serverLevel(), p.getEyePosition(), SoundEvents.EVOKER_CAST_SPELL, 0.6F, 1.5F);
        return true;
    }

    public static boolean persuading(ServerPlayer p) { return PERSUADING.containsKey(p.getUUID()); }

    /** A mind won over: a mob fights for you, a villager offers a hero's discount, a player can't strike you. */
    static void win(ServerPlayer p, LivingEntity t) {
        long now = now(p);
        ServerLevel level = p.serverLevel();
        if (t instanceof Villager villager) {
            p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 1200, 0));
            sound(level, villager.getEyePosition(), SoundEvents.VILLAGER_CELEBRATE, 1, 1.1F);
        } else if (t instanceof Mob mob) {
            persuade(p, mob, PERSUADED_TICKS);
        } else if (t instanceof ServerPlayer other) {
            int ticks = has(p, EDENS_VOICE) ? 200 : 120;
            COMPELLED.put(other.getUUID(), new Compulsion(p.getUUID(), now + ticks, false));
            other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 0));
            other.displayClientMessage(Component.translatable("technique.heroesevolved.persuasion.compelled", p.getName())
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        TelepathyFocus.deepen(p, t, 20, 100);
        TelepathyFocus.strain(p, 6);
        show(level, Piece.TELE_WON, t.getEyePosition(), t.getEyePosition(), 1, t);
        p.displayClientMessage(Component.translatable("technique.heroesevolved.persuasion.won", t.getName()), true);
        sound(level, t.getEyePosition(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.2F);
    }

    /** A mob on {@code owner}'s side for {@code ticks}: it follows them and fights their enemies. */
    public static void persuade(ServerPlayer owner, Mob mob, int ticks) {
        mob.setTarget(null);
        PERSUADED.put(mob.getUUID(), new Persuaded(mob, owner, now(owner) + ticks));
    }

    public static boolean persuaded(LivingEntity mob) { return PERSUADED.containsKey(mob.getUUID()); }

    /** Whose side a persuaded mob is on, or null. */
    public static ServerPlayer persuadedBy(LivingEntity mob) {
        Persuaded persuaded = PERSUADED.get(mob.getUUID());
        return persuaded == null ? null : persuaded.owner;
    }

    /**
     * Memory Walk: touch a mind up to 24 blocks away and walk back through its last thirty seconds (a minute with Deep
     * Read). Its path shows for you alone as fading footsteps, the place it was when the memory began stands as an echo,
     * and whoever hurt it is named. Aimed at a place instead, it shows everyone who passed through it.
     */
    static boolean memoryWalk(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        boolean deep = has(p, DEEP_READ);
        int span = deep ? MEMORY_DEEP : MEMORY_TICKS;
        long now = now(p), since = now - span;
        LivingEntity target = Targeting.livingEntityInSight(p, 24, t -> t != p && mind(t));
        Vec3 from = hand(p, 1, .5);
        if (target != null) {
            List<MindMemory.Moment> path = MindMemory.path(target.getUUID(), since);
            List<MindMemory.Moment> walked = new ArrayList<>(path);
            walked.add(new MindMemory.Moment(now, target.position(), null));
            trail(p, walked, span, now);
            List<String> hurts = new ArrayList<>();
            for (int i = path.size() - 1; i >= 0 && hurts.size() < 3; i--) {
                MindMemory.Moment m = path.get(i);
                if (m.hurtBy() != null) hurts.add(Component.translatable("technique.heroesevolved.memory_walk.hurt", m.hurtBy(),
                        (now - m.time()) / 20).getString());
            }
            p.sendSystemMessage(hurts.isEmpty()
                    ? Component.translatable("technique.heroesevolved.memory_walk.calm", target.getName()).withStyle(ChatFormatting.LIGHT_PURPLE)
                    : Component.translatable("technique.heroesevolved.memory_walk.remembers", target.getName(), String.join(", ", hurts))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            if (deep) TelepathyFocus.deepen(p, target, 40, 40);
            if (has(p, LOST_IN_THOUGHT) && StyleOps.isFoe(p, target)) lose(p, target, walked);
            show(level, Piece.TELE_RECALL, from, target.getEyePosition(), 1, target);
        } else {
            Vec3 eye = p.getEyePosition();
            Vec3 point = level.clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(24)), ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE, p)).getLocation();
            List<UUID> passers = MindMemory.passedThrough(level, point, PLACE_RADIUS, since, PLACE_TRAILS, p.getUUID());
            if (passers.isEmpty()) {
                p.displayClientMessage(Component.translatable("technique.heroesevolved.memory_walk.empty"), true);
                return false;
            }
            List<String> names = new ArrayList<>();
            for (UUID id : passers) {
                List<MindMemory.Moment> path = MindMemory.path(id, since);
                var entity = level.getEntity(id);
                if (entity != null) names.add(entity.getName().getString());
                List<MindMemory.Moment> walked = new ArrayList<>(path);
                if (entity != null && entity.isAlive()) walked.add(new MindMemory.Moment(now, entity.position(), null));
                trail(p, walked, span, now);
            }
            p.sendSystemMessage(Component.translatable("technique.heroesevolved.memory_walk.place",
                    names.isEmpty() ? String.valueOf(passers.size()) : String.join(", ", names)).withStyle(ChatFormatting.LIGHT_PURPLE));
            show(level, Piece.TELE_RECALL, from, point, 1, null);
        }
        ArmPosePayload.show(p, ArmPose.TELE_MEMORY, 22);
        sound(level, p.getEyePosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, 0.7F, 0.8F);
        return true;
    }

    /**
     * Sends a walked memory to the walker as at most {@link TelepathyGeometry#TRAIL_PIECES} pieces, newest first, each one
     * {@link TelepathyGeometry#TRAIL_DELAY} ticks after the last so it unspools back in time, and an echo where it began.
     * A piece's scale is {@code 1 + 10 × how long ago}, plus {@link TelepathyGeometry#FLAG_HURT} where the mind was hurt.
     */
    static int trail(ServerPlayer p, List<MindMemory.Moment> path, int span, long now) {
        int n = path.size();
        if (n < 2) return 0;
        int step = Math.max(1, (int) Math.ceil((n - 1) / (double) TRAIL_PIECES)), sent = 0;
        for (int end = n - 1; end > 0; end -= step) {
            int start = Math.max(0, end - step);
            boolean hurt = false;
            for (int i = start + 1; i <= end; i++) hurt |= path.get(i).hurtBy() != null;
            float age = Math.clamp((now - path.get(start).time()) / (float) span, 0, 1);
            showTo(p, Piece.TELE_MEMORY, path.get(start).at(), path.get(end).at(), 1 + 10 * age + (hurt ? FLAG_HURT : 0), null,
                    sent++ * TRAIL_DELAY);
        }
        MindMemory.Moment first = path.getFirst();
        Vec3 facing = path.get(Math.min(1, n - 1)).at();
        showTo(p, Piece.TELE_ECHO, first.at(), facing.equals(first.at()) ? first.at().add(0, 0, 1) : facing, 1, null, sent * TRAIL_DELAY);
        return sent + 1;
    }

    /** Lost in Thought: an enemy relives what was walked. A mob wanders back along its own memory; a player is lost in it. */
    static void lose(ServerPlayer p, LivingEntity t, List<MindMemory.Moment> walked) {
        if (t instanceof Mob mob && !boss(mob)) {
            List<Vec3> back = new ArrayList<>();
            for (int i = walked.size() - 1; i >= 0; i--) back.add(walked.get(i).at());
            mob.setTarget(null);
            LOST.put(mob.getUUID(), new Lost(mob, back, now(p) + LOST_MOB_TICKS));
        } else if (t instanceof ServerPlayer other) {
            other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, LOST_PLAYER_TICKS, 1));
            ScreenTintPayload.send(other, 0x60A080E0, LOST_PLAYER_TICKS, ScreenTintPayload.Style.VIGNETTE);
            other.displayClientMessage(Component.translatable("technique.heroesevolved.lost_in_thought").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        } else return;
        show(p.serverLevel(), Piece.TELE_LOST, t.getEyePosition(), t.getEyePosition(), 1, t);
    }

    public static boolean lost(LivingEntity mob) { return LOST.containsKey(mob.getUUID()); }

    /** Friends who'd join a link: teammates, or anyone crouching to volunteer. */
    private static List<ServerPlayer> willing(ServerPlayer p, double range) {
        return p.serverLevel().players().stream().filter(o -> o != p && !o.isSpectator() && o.isAlive()
                        && (p.isAlliedTo(o) || o.isShiftKeyDown()) && o.distanceToSqr(p) <= range * range)
                .sorted(java.util.Comparator.comparingDouble(o -> o.distanceToSqr(p))).limit(HIVE_SIZE).toList();
    }

    /**
     * Hive Link: up to six willing friends within 24 blocks, for two minutes. Linked minds hear each other's voices from
     * anywhere, even another dimension, and see each other through walls; a friend below a third of their health flares.
     */
    static boolean hiveLink(ServerPlayer p) {
        List<ServerPlayer> friends = willing(p, HIVE_RANGE);
        if (friends.isEmpty()) {
            p.displayClientMessage(Component.translatable("technique.heroesevolved.hive_link.none"), true);
            return false;
        }
        Set<UUID> members = new LinkedHashSet<>();
        members.add(p.getUUID());
        for (ServerPlayer f : friends) members.add(f.getUUID());
        // A mind belongs to one Hive Link at a time.
        for (UUID id : members) HIVES.values().forEach(h -> { if (!h.owner.equals(p.getUUID())) h.members.remove(id); });
        HIVES.put(p.getUUID(), new Hive(p.getUUID(), members, now(p) + HIVE_TICKS, true));
        String names = String.join(", ", friends.stream().map(f -> f.getName().getString()).toList());
        for (ServerPlayer member : members(p.server, HIVES.get(p.getUUID()))) {
            if (member != p) show(p.serverLevel(), Piece.TELE_HIVE_LINK, p.getEyePosition(), member.getEyePosition(), 1, member);
            member.displayClientMessage(Component.translatable("technique.heroesevolved.hive_link.linked",
                    member == p ? names : p.getName().getString()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            ScreenTintPayload.send(member, 0x40A070FF, 20, ScreenTintPayload.Style.VIGNETTE);
            TelepathyService.whisper(member, 1.3F);
        }
        ArmPosePayload.show(p, ArmPose.TELE_HIVE, 20);
        return true;
    }

    /**
     * Hive Mind's brief link: its group hears each other's voices from anywhere for {@code ticks}. Only teammates and
     * crouching volunteers: nobody's voice is carried without their say.
     */
    public static void linkVoices(ServerPlayer owner, List<? extends LivingEntity> group, int ticks) {
        Set<UUID> members = new LinkedHashSet<>();
        for (LivingEntity e : group)
            if (e instanceof ServerPlayer sp && (sp == owner || owner.isAlliedTo(sp) || sp.isShiftKeyDown())) members.add(sp.getUUID());
        if (members.size() > 1) STYLE_HIVES.put(owner.getUUID(), new Hive(owner.getUUID(), members, now(owner) + ticks, false));
    }

    private static List<ServerPlayer> members(MinecraftServer server, Hive hive) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : hive.members) {
            ServerPlayer member = server.getPlayerList().getPlayer(id);
            if (member != null && member.isAlive()) out.add(member);
        }
        return out;
    }

    /** Whether the player is in a Hive Link (as its maker or a member). */
    public static boolean inHive(ServerPlayer p) {
        for (Hive hive : HIVES.values()) if (hive.members.contains(p.getUUID())) return true;
        return false;
    }

    /** The friends linked with {@code p} by a Hive Link, not counting them. */
    public static List<ServerPlayer> hiveOf(ServerPlayer p) {
        for (Hive hive : HIVES.values())
            if (hive.members.contains(p.getUUID())) return members(p.server, hive).stream().filter(m -> m != p).toList();
        return List.of();
    }

    // ------------------------------------------------------------------
    // Passives read by the core
    // ------------------------------------------------------------------

    /** Whispered Orders: a Suggestion to a threaded mind lasts twice as long, and deepens the thread. */
    public static int suggestionTicks(ServerPlayer p, LivingEntity mob, int ticks) {
        if (!has(p, WHISPERED_ORDERS) || TelepathyFocus.focus(p, mob) < 30) return ticks;
        TelepathyFocus.deepen(p, mob, 20, 100);
        return ticks * 2;
    }

    /** Shared Burden: Strain eases twice as fast, and builds half as fast, while you're linked with a friend. */
    public static boolean sharesBurden(ServerPlayer p) {
        return has(p, SHARED_BURDEN) && (TelepathyService.hasLink(p) || inHive(p));
    }

    /** Strings Attached: a body let go after possession stays on your side for a while. */
    static void released(ServerPlayer owner, LivingEntity body) {
        if (!has(owner, STRINGS_ATTACHED) || !body.isAlive()) return;
        if (body instanceof Mob mob && !boss(mob)) persuade(owner, mob, STRINGS_TICKS);
        else if (body instanceof ServerPlayer other) {
            COMPELLED.put(other.getUUID(), new Compulsion(owner.getUUID(), now(owner) + 60, false));
            other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        } else return;
        show(owner.serverLevel(), Piece.TELE_WON, body.getEyePosition(), body.getEyePosition(), 1, body);
    }

    /** Waking Fright: a mind that walks into or touches one of your illusions panics and is Shaken. */
    static void fright(ServerPlayer caster, ServerPlayer victim, Vec3 illusion) {
        if (!has(caster, WAKING_FRIGHT) || !victim.isAlive()) return;
        Vec3 away = victim.position().subtract(illusion).multiply(1, 0, 1);
        if (away.lengthSqr() < 1e-4) away = victim.getLookAngle().multiply(-1, 0, -1);
        StyleOps.push(victim, away.normalize().scale(0.9).add(0, 0.35, 0));
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        SHAKEN.put(victim.getUUID(), new Shaken(caster.getUUID(), now(caster) + SHAKEN_TICKS));
        show(victim.serverLevel(), Piece.TELE_FRIGHT, victim.getEyePosition(), illusion, 1, victim);
        sound(victim.serverLevel(), victim.getEyePosition(), SoundEvents.PHANTOM_BITE, 0.7F, 0.6F);
    }

    public static boolean shaken(LivingEntity t) { return SHAKEN.containsKey(t.getUUID()); }

    // ------------------------------------------------------------------
    // The inner voice
    // ------------------------------------------------------------------

    /**
     * Shift + Mind Link: the inner voice on or off. With it on, a telepath's voice goes only into the minds they reach
     * (a Mind Link, a Hive Link, Mind Network's threads), however near or far, distorted like a thought, and nobody in the
     * room hears it. It needs someone to reach; without a link, the press falls through to an ordinary Mind Link.
     */
    public static boolean toggleInnerVoice(ServerPlayer p) {
        if (INNER.remove(p.getUUID())) {
            p.displayClientMessage(Component.translatable("technique.heroesevolved.inner_voice.off"), true);
            sound(p.serverLevel(), p.getEyePosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.4F, 1.6F);
            return true;
        }
        if (reach(p).isEmpty()) return false;
        INNER.add(p.getUUID());
        p.displayClientMessage(Component.translatable("technique.heroesevolved.inner_voice.on").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        ArmPosePayload.show(p, ArmPose.TELE_LISTEN, 14);
        sound(p.serverLevel(), p.getEyePosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.4F, 0.9F);
        return true;
    }

    public static boolean innerVoice(ServerPlayer p) { return INNER.contains(p.getUUID()); }

    /** Every mind a telepath's voice can reach: their Mind Link, their Hive Link, and Mind Network's threaded players. */
    static List<ServerPlayer> reach(ServerPlayer p) {
        Set<ServerPlayer> minds = new LinkedHashSet<>();
        for (List<ServerPlayer> group : TelepathyService.linkGroups(p.server)) if (group.contains(p)) minds.addAll(group);
        minds.addAll(hiveOf(p));
        if (has(p, MIND_NETWORK))
            for (LivingEntity t : TelepathyFocus.threads(p, PROJECT_FOCUS)) if (t instanceof ServerPlayer sp) minds.add(sp);
        minds.remove(p);
        return List.copyOf(minds);
    }

    /** Psychic Storm threads the foes it catches a little deeper each second. */
    public static void stormThread(ServerPlayer p, LivingEntity t) { TelepathyFocus.deepen(p, t, 10, 100); }

    // ------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        DISARMED.values().removeIf(until -> until <= now);
        COMPELLED.values().removeIf(c -> c.until <= now);
        SHAKEN.values().removeIf(s -> s.until <= now);
        MindMemory.tick(server);
        tickPersuading(now);
        tickPersuaded(now);
        tickLost(now);
        tickListening(server, now);
        tickHives(server, now);
        routes(server, now);
    }

    private static void tickPersuading(long now) {
        for (var entry : List.copyOf(PERSUADING.entrySet())) {
            Persuading talk = entry.getValue();
            ServerPlayer p = talk.owner;
            if (!p.isAlive() || p.isRemoved() || !PowerRules.canAct(p)) { PERSUADING.remove(entry.getKey()); continue; }
            List<LivingEntity> holding = new ArrayList<>();
            for (LivingEntity t : talk.minds) {
                if (t.isAlive() && t.level() == p.level() && t.distanceTo(p) <= talk.range * 2 + 4 && p.hasLineOfSight(t)) holding.add(t);
                else {
                    show(p.serverLevel(), Piece.TELE_FRIGHT, t.getEyePosition(), p.getEyePosition(), 0.5, t);
                    p.displayClientMessage(Component.translatable("technique.heroesevolved.persuasion.lost", t.getName()), true);
                }
            }
            if (holding.isEmpty()) { PERSUADING.remove(entry.getKey()); ArmPosePayload.clearIf(p, ArmPose.TELE_PERSUADE); continue; }
            if (now - talk.start >= talk.ticks) {
                PERSUADING.remove(entry.getKey());
                holding.forEach(t -> win(p, t));
            } else if (holding.size() < talk.minds.size()) {
                PERSUADING.put(entry.getKey(), new Persuading(p, holding, talk.start, talk.ticks, talk.range));
            }
        }
    }

    private static void tickPersuaded(long now) {
        for (Persuaded persuaded : List.copyOf(PERSUADED.values())) {
            Mob mob = persuaded.mob;
            ServerPlayer owner = persuaded.owner;
            if (!mob.isAlive() || now >= persuaded.until || owner.isRemoved() || !owner.isAlive() || mob.level() != owner.level()) {
                PERSUADED.remove(mob.getUUID());
                if (mob.getTarget() != null && mob.getTarget() != owner) mob.setTarget(null);
                continue;
            }
            if (now % 10 == 0) {
                LivingEntity target = mob.getTarget();
                if (target == null || !target.isAlive() || StyleOps.isFriend(owner, target)) {
                    mob.setTarget(null);
                    LivingEntity foe = foeFor(owner, mob);
                    if (foe != null) mob.setTarget(foe);
                    else if (mob.distanceToSqr(owner) > 25) mob.getNavigation().moveTo(owner, 1.15);
                }
                show((ServerLevel) mob.level(), Piece.TELE_WON, mob.getEyePosition(), mob.getEyePosition(), 1, mob);
            }
            // Silver Tongue: a convert shouts the persuader's name, and nearby enemies turn on it instead.
            if (now % 20 == 0 && has(owner, SILVER_TONGUE))
                for (Mob enemy : mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(8),
                        m -> m != mob && m.getTarget() == owner && !PERSUADED.containsKey(m.getUUID()))) enemy.setTarget(mob);
        }
    }

    /** What a persuaded mob fights: whatever hunts its persuader first, then any monster near. */
    private static LivingEntity foeFor(ServerPlayer owner, Mob mob) {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity t : mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(12),
                t -> t != mob && t != owner && mind(t) && StyleOps.isFoe(owner, t) && !(PERSUADED.get(t.getUUID()) instanceof Persuaded o && o.owner == owner))) {
            boolean hunting = t instanceof Mob m && m.getTarget() != null && StyleOps.isFriend(owner, m.getTarget());
            boolean attacker = owner.getLastHurtByMob() == t;
            if (!hunting && !attacker && !(t instanceof Enemy)) continue;
            double score = mob.distanceToSqr(t) - (hunting || attacker ? 400 : 0);
            if (score < bestScore) { bestScore = score; best = t; }
        }
        return best;
    }

    private static void tickLost(long now) {
        for (Lost lost : List.copyOf(LOST.values())) {
            Mob mob = lost.mob;
            if (!mob.isAlive() || now >= lost.until) { LOST.remove(mob.getUUID()); continue; }
            mob.setTarget(null);
            if (now % 10 != 0) continue;
            Vec3 next = null;
            for (Vec3 point : lost.path) if (point.distanceToSqr(mob.position()) > 4) { next = point; break; }
            if (next != null) {
                int keep = lost.path.indexOf(next);
                if (keep > 0) lost.path.subList(0, keep).clear();
                mob.getNavigation().moveTo(next.x, next.y, next.z, 0.9);
            }
            show((ServerLevel) mob.level(), Piece.TELE_LOST, mob.getEyePosition(), mob.getEyePosition(), 1, mob);
        }
    }

    private static void tickListening(MinecraftServer server, long now) {
        for (Listening l : List.copyOf(LISTENING.values())) {
            ServerPlayer p = l.player;
            if (now >= l.until || p.isRemoved() || !p.isAlive() || !PowerRules.canAct(p)) { stopListening(p, !p.isRemoved()); continue; }
            if (now % 10 != 0) continue;
            show(p.serverLevel(), Piece.TELE_LISTEN, p.position(), p.position(), 1, p);
            Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
            boolean eavesdrop = has(p, EAVESDROPPER);
            for (UUID id : MindVoiceRoutes.drain(p.getUUID())) {
                ServerPlayer speaker = server.getPlayerList().getPlayer(id);
                if (speaker == null || speaker.level() != p.level()) continue;
                l.lastHeard.put(id, now);
                Vec3 voice = speaker.getEyePosition();
                double d = voice.distanceTo(eye);
                showTo(p, Piece.TELE_VOICE, eye, voice, 1 + Math.clamp(d / l.range, 0, 1) * 10, p, 0);
                PrivateGlow.glow(p, speaker, 14);
                if (eavesdrop && MindVoiceRoutes.facing(look, voice.subtract(eye).normalize())) TelepathyFocus.deepen(p, speaker, 4, 30);
                // Ten seconds of being listened to, and the speaker feels it (once a session).
                int heard = l.heard.merge(id, 1, Integer::sum);
                if (heard >= 20 && l.warned.add(id) && !StyleOps.isFriend(p, speaker))
                    speaker.displayClientMessage(Component.translatable("technique.heroesevolved.far_hearing.watched").withStyle(ChatFormatting.DARK_PURPLE), true);
            }
            l.lastHeard.values().removeIf(at -> now - at > 30);
            if (!l.lastHeard.isEmpty()) {
                List<String> voices = new ArrayList<>();
                for (UUID id : l.lastHeard.keySet()) {
                    ServerPlayer speaker = server.getPlayerList().getPlayer(id);
                    if (speaker == null || voices.size() >= 3) continue;
                    voices.add(Component.translatable("technique.heroesevolved.far_hearing.voice", speaker.getName(),
                            Math.round(speaker.distanceTo(p)), bearing(p, speaker.position())).getString());
                }
                p.displayClientMessage(Component.translatable("technique.heroesevolved.far_hearing.voices", String.join("  ·  ", voices))
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            } else if (now % 60 == 0) {
                // Nobody speaking: the thoughts of something hunting nearby surface instead.
                Mob hunter = p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(48),
                                m -> m instanceof Enemy && m.getTarget() != null && !p.hasLineOfSight(m)).stream()
                        .min(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(p))).orElse(null);
                if (hunter != null) {
                    p.displayClientMessage(Component.translatable("technique.heroesevolved.far_hearing.hunted", hunter.getName(),
                            Math.round(hunter.distanceTo(p)), bearing(p, hunter.position()), hunter.getTarget().getName()).withStyle(ChatFormatting.DARK_PURPLE), true);
                    PrivateGlow.glow(p, hunter, 40);
                    TelepathyService.whisper(p, 0.7F);
                }
            }
        }
    }

    private static void tickHives(MinecraftServer server, long now) {
        STYLE_HIVES.values().removeIf(h -> now >= h.until);
        for (var entry : List.copyOf(HIVES.entrySet())) {
            Hive hive = entry.getValue();
            ServerPlayer owner = server.getPlayerList().getPlayer(hive.owner);
            List<ServerPlayer> members = members(server, hive);
            if (now >= hive.until || owner == null || members.size() < 2) {
                HIVES.remove(entry.getKey());
                for (ServerPlayer m : members) m.displayClientMessage(Component.translatable("technique.heroesevolved.hive_link.ended"), true);
                continue;
            }
            if (now % 10 != 0) continue;
            boolean senses = has(owner, SHARED_SENSES);
            for (ServerPlayer viewer : members)
                for (ServerPlayer other : members) {
                    if (other == viewer || other.level() != viewer.level()) continue;
                    boolean danger = other.getHealth() < other.getMaxHealth() / 3;
                    boolean speaking = MindVoiceRoutes.speaking(other.getUUID(), 400);
                    showTo(viewer, Piece.TELE_HALO, other.getEyePosition(), other.getEyePosition(),
                            1 + (speaking ? FLAG_SPEAKING : 0) + (danger ? FLAG_DANGER : 0), other, 0);
                    if (now % 20 == 0) PrivateGlow.glow(viewer, other, 30);
                }
            if (!senses || now % 20 != 0) continue;
            // Shared Senses: whatever hunts one linked mind glows for all of them, and they hear it coming.
            for (ServerPlayer hunted : members)
                for (Mob hunter : hunted.serverLevel().getEntitiesOfClass(Mob.class, hunted.getBoundingBox().inflate(32), m -> m.getTarget() == hunted))
                    for (ServerPlayer viewer : members) {
                        if (viewer.level() != hunter.level()) continue;
                        PrivateGlow.glow(viewer, hunter, 40);
                        Long last = WHISPERED.get(viewer.getUUID());
                        if (last == null || now - last >= 80) {
                            WHISPERED.put(viewer.getUUID(), now);
                            viewer.displayClientMessage(Component.translatable("technique.heroesevolved.hive_link.hunted", hunter.getName(),
                                    hunted.getName(), Math.round(hunter.distanceTo(viewer)), bearing(viewer, hunter.position()))
                                    .withStyle(ChatFormatting.DARK_PURPLE), true);
                        }
                    }
        }
    }

    /** Works out who hears whom this tick, and lets each mind know whose voice is inside its head. */
    private static void routes(MinecraftServer server, long now) {
        List<MindVoiceRoutes.Listener> far = new ArrayList<>();
        for (Listening l : LISTENING.values()) far.add(new MindVoiceRoutes.Listener(l.player, l.range));
        List<List<ServerPlayer>> links = new ArrayList<>(TelepathyService.linkGroups(server));
        for (Hive hive : HIVES.values()) links.add(members(server, hive));
        for (Hive hive : STYLE_HIVES.values()) links.add(members(server, hive));
        Map<ServerPlayer, List<ServerPlayer>> projections = new HashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!has(p, MIND_NETWORK) || !PowerRules.canAct(p)) continue;
            List<ServerPlayer> minds = new ArrayList<>();
            for (LivingEntity t : TelepathyFocus.threads(p, PROJECT_FOCUS)) if (t instanceof ServerPlayer sp && sp != p) minds.add(sp);
            if (!minds.isEmpty()) projections.put(p, minds);
        }
        // The inner voice ends when there's nobody left to hear it, or the telepath can't use their power.
        INNER.removeIf(id -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            boolean done = p == null || !PowerRules.canAct(p) || reach(p).isEmpty();
            if (done && p != null) p.displayClientMessage(Component.translatable("technique.heroesevolved.inner_voice.off"), true);
            return done;
        });
        var config = com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;
        var computed = MindVoiceRoutes.compute(far, links, projections, server.getPlayerList().getPlayers(),
                MindVoiceRoutes.voiceDistance(), INNER);
        MindVoiceRoutes.publish(computed, INNER, config.telepathyHearsVoices.get(), config.telepathyHearsGroups.get());
        if (now % 10 != 0) return;
        // Whoever hears a voice inside their head sees it curl round their head, and is told whose it is.
        for (var entry : computed.entrySet()) {
            if (!MindVoiceRoutes.speaking(entry.getKey(), 500)) continue;
            ServerPlayer speaker = server.getPlayerList().getPlayer(entry.getKey());
            if (speaker == null) continue;
            if (INNER.contains(speaker.getUUID()))
                showTo(speaker, Piece.TELE_PROJECT, speaker.getEyePosition(), speaker.getEyePosition(), 1, speaker, 0);
            for (MindVoiceRoutes.Route route : entry.getValue()) {
                if (!route.inHead()) continue;
                ServerPlayer mind = server.getPlayerList().getPlayer(route.listener());
                if (mind == null) continue;
                showTo(mind, Piece.TELE_PROJECT, mind.getEyePosition(), mind.getEyePosition(), 1, mind, 0);
                String key = mind.getUUID() + " " + speaker.getUUID();
                Long last = PROJECTED.get(key);
                if (last == null || now - last > 1200) {
                    PROJECTED.put(key, now);
                    mind.displayClientMessage(Component.translatable("technique.heroesevolved.mind_network.heard", speaker.getName())
                            .withStyle(ChatFormatting.LIGHT_PURPLE), true);
                }
            }
        }
    }

    /** Where something is, as the player faces: ahead, behind or to one side. */
    static String bearing(ServerPlayer player, Vec3 at) {
        Vec3 offset = at.subtract(player.position());
        float relative = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-offset.x, offset.z)) - player.getYRot());
        String key = Math.abs(relative) <= 45 ? "ahead" : Math.abs(relative) >= 135 ? "behind" : relative > 0 ? "right" : "left";
        return Component.translatable("telepathy.heroesevolved.direction." + key).getString();
    }

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    /** Disarmed, lost and persuaded mobs can't pick the wrong target, whatever their goals want. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void target(LivingChangeTargetEvent event) {
        LivingEntity next = event.getNewAboutToBeSetTarget();
        if (next == null) return;
        LivingEntity mob = event.getEntity();
        if (disarmed(mob) || LOST.containsKey(mob.getUUID())) { event.setCanceled(true); return; }
        Persuaded persuaded = PERSUADED.get(mob.getUUID());
        if (persuaded != null && (StyleOps.isFriend(persuaded.owner, next)
                || PERSUADED.get(next.getUUID()) instanceof Persuaded other && other.owner == persuaded.owner)) event.setCanceled(true);
    }

    @SubscribeEvent public static void attack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer attacker && refused(attacker, event.getTarget().getUUID())) event.setCanceled(true);
    }

    @SubscribeEvent public static void useItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer p && COMPELLED.get(p.getUUID()) instanceof Compulsion c && c.hands) event.setCanceled(true);
    }

    @SubscribeEvent public static void startUsing(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer p && COMPELLED.get(p.getUUID()) instanceof Compulsion c && c.hands) event.setCanceled(true);
    }

    /** Whether a compelled player can't strike {@code target} right now. */
    static boolean refused(ServerPlayer attacker, UUID target) {
        Compulsion c = COMPELLED.get(attacker.getUUID());
        return c != null && attacker.level().getGameTime() < c.until && (c.hands || c.by.equals(target));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void damage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        var source = event.getSource().getEntity();
        if (source instanceof LivingEntity attacker) {
            if (disarmed(attacker)) { event.setCanceled(true); return; }
            Persuaded persuaded = PERSUADED.get(attacker.getUUID());
            if (persuaded != null && StyleOps.isFriend(persuaded.owner, victim)) { event.setCanceled(true); return; }
            if (attacker instanceof ServerPlayer p && refused(p, victim.getUUID())) { event.setCanceled(true); return; }
        }
        if (source instanceof ServerPlayer p) {
            // Open Book: you know where a mind you've read will move.
            if (has(p, OPEN_BOOK)) {
                int focus = TelepathyFocus.focus(p, victim);
                if (focus >= 60) event.setAmount(event.getAmount() * 1.4F);
                else if (focus >= 30) event.setAmount(event.getAmount() * 1.2F);
            }
            Shaken shaken = SHAKEN.get(victim.getUUID());
            if (shaken != null && shaken.by.equals(p.getUUID())) {
                SHAKEN.remove(victim.getUUID());
                event.setAmount(event.getAmount() + SHAKEN_DAMAGE);
            }
        }
    }

    @SubscribeEvent public static void hurt(LivingDamageEvent.Post event) {
        if (event.getNewDamage() > 0) MindMemory.hurt(event.getEntity(), event.getSource().getEntity());
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { clear(); }

    // ------------------------------------------------------------------

    public static void forget(ServerPlayer p) {
        UUID id = p.getUUID();
        LISTENING.remove(id);
        PERSUADING.remove(id);
        PERSUADED.values().removeIf(x -> x.owner == p);
        COMPELLED.remove(id);
        COMPELLED.values().removeIf(c -> c.by.equals(id));
        SHAKEN.remove(id);
        MindVoiceRoutes.drain(id);
    }

    /** Logging out leaves every link (a death or a dimension change doesn't: a link reaches anywhere). */
    public static void leave(ServerPlayer p) {
        UUID id = p.getUUID();
        HIVES.remove(id);
        STYLE_HIVES.remove(id);
        for (Hive hive : HIVES.values()) hive.members.remove(id);
        for (Hive hive : STYLE_HIVES.values()) hive.members.remove(id);
        PROJECTED.keySet().removeIf(key -> key.contains(id.toString()));
        WHISPERED.remove(id);
        INNER.remove(id);
        MindMemory.forget(id);
    }

    public static void clear() {
        DISARMED.clear(); COMPELLED.clear(); PERSUADING.clear(); PERSUADED.clear(); LISTENING.clear(); LOST.clear();
        SHAKEN.clear(); HIVES.clear(); STYLE_HIVES.clear(); PROJECTED.clear(); WHISPERED.clear(); INNER.clear();
        MindMemory.clear();
        MindVoiceRoutes.clear();
    }
}
