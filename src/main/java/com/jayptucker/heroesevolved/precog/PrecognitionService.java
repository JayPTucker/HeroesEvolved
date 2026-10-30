package com.jayptucker.heroesevolved.precog;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.effects.PrivateGlow;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.expansion.FacilityData;
import com.jayptucker.heroesevolved.expansion.FacilityService;
import com.jayptucker.heroesevolved.expansion.PropheticPainting;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.progression.EvolutionTrials;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.story.sylar.SylarEntity;
import com.jayptucker.heroesevolved.story.sylar.SylarStoryData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Precognition, inspired by Isaac Mendez: see what is coming, paint it, and change it.
 *
 * <ul>
 *   <li>Premonition: eight seconds of sharper Danger Sense. Every enemy nearby shows a ghost of
 *   where it is about to be, and the unseen are revealed.</li>
 *   <li>Paint the Future: a short trance. Paint an enemy's ending and it comes true once they are
 *   hurt badly enough; paint with no one in view and the canvas shows what is coming for you
 *   (the Hourglass Annex, Sylar, or what is near).</li>
 *   <li>Saw It Coming: for a moment, the next blow misses. You are already somewhere else, and
 *   the attacker is Exposed.</li>
 *   <li>Fate Thread: cross out one lethal outcome for a friend, or yourself.</li>
 *   <li>The Future Is Written: spend your Clarity to live two seconds ahead of everyone.</li>
 * </ul>
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class PrecognitionService {
    public static final ResourceLocation SAW_IT_COMING_ID = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "precog_saw_it_coming");
    public static final int PREMONITION_TICKS = 160, READ_TICKS = 30, EXPOSED_TICKS = 120, ENDING_TICKS = 300, FATE_TICKS = 160;
    public static final float ENDING_BONUS = 1.2F, EXPOSED_BONUS = 1.5F, WRITTEN_BONUS = 1.3F;
    private static final int COMBAT_PAINT_TICKS = 30, STORY_PAINT_TICKS = 60, WRITTEN_DODGE_COST = 6;
    private static final ResourceLocation SPARED_COOLDOWN = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "precog_spared");

    private record Timed(ServerPlayer owner, long until) {}
    private record Painting(ServerPlayer owner, UUID target, long start, long done) {}
    private record Ending(ServerPlayer owner, long until, float threshold) {}

    /** Premonition, and teammates lent the sight by Omen Relay. */
    private static final Map<UUID, Timed> SENSE = new HashMap<>();
    private static final Map<UUID, Painting> PAINTINGS = new HashMap<>();
    private static final Map<UUID, Ending> ENDINGS = new HashMap<>();
    private static final Map<UUID, Long> READS = new HashMap<>(), SPARED = new HashMap<>();
    private static final Map<UUID, Timed> WRITTEN = new HashMap<>(), EXPOSED = new HashMap<>(), INTENTS = new HashMap<>(), FATE = new HashMap<>();
    /** Players who painted Sylar coming: his first strike on them is crossed out. */
    private static final Set<UUID> SYLAR_FORETOLD = new HashSet<>();

    private PrecognitionService() {}

    private static long now(ServerPlayer p) { return p.getServer().overworld().getGameTime(); }
    private static boolean valid(ServerPlayer p) { return Foresight.seer(p); }

    private static boolean enemy(ServerPlayer p, LivingEntity t) {
        return ExpansionSkills.hostile(p, t) && !(t instanceof TemporalEchoEntity) && (t instanceof Enemy || t instanceof ServerPlayer
                || t instanceof Mob mob && mob.getTarget() != null && mob.getTarget() == p);
    }

    // ------------------------------------------------------------------
    // Awakening and Premonition
    // ------------------------------------------------------------------

    public static void awaken(ServerPlayer p) {
        var id = ModAbilities.PRECOGNITION_ID;
        var history = p.getData(ModDataAttachments.POWER_INTRO_HISTORY);
        if (history.seen().contains(id)) return;
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY, history.with(id));
        p.sendSystemMessage(Component.literal("Your hands move before you decide to move them. Two silhouettes. A copper heart. A clock without a next second.")
                .withStyle(ChatFormatting.GOLD));
        FacilityService.plan(p);
        PAINTINGS.put(p.getUUID(), new Painting(p, null, now(p), now(p) + STORY_PAINT_TICKS));
        SENSE.put(p.getUUID(), new Timed(p, now(p) + PREMONITION_TICKS));
    }

    /** True while the player's own Premonition is running. */
    public static boolean premonition(ServerPlayer p) {
        Timed t = SENSE.get(p.getUUID());
        return t != null && t.owner == p && now(p) < t.until;
    }

    /** True while the player has the sight, their own or lent by Omen Relay. */
    public static boolean sharedSight(ServerPlayer p) {
        Timed t = SENSE.get(p.getUUID());
        return t != null && now(p) < t.until;
    }

    public static boolean startPremonition(ServerPlayer p) {
        SENSE.put(p.getUUID(), new Timed(p, now(p) + PREMONITION_TICKS));
        ScreenTintPayload.send(p, 0x30F7D88D, 20, ScreenTintPayload.Style.VIGNETTE);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 0.6F);
        ArmPosePayload.show(p, ArmPose.TEMPLE, 14);
        pulse(p, p, true);
        return true;
    }

    /**
     * One beat of the sight: mobs that chose the viewer glow, and (for a seer) every enemy shows a
     * ghost of where it is about to be. Invisible players nearby are revealed.
     */
    private static void pulse(ServerPlayer viewer, ServerPlayer owner, boolean ghosts) { pulse(viewer, owner, ghosts, true); }

    private static void pulse(ServerPlayer viewer, ServerPlayer owner, boolean ghosts, boolean report) {
        var level = viewer.serverLevel();
        int hunting = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, viewer.getBoundingBox().inflate(24), m -> m.getTarget() == viewer)) {
            if (++hunting > 8) break;
            PrivateGlow.glow(viewer, mob, 12);
        }
        if (ghosts) {
            int shown = 0;
            for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, viewer.getBoundingBox().inflate(24), t -> enemy(owner, t))) {
                if (++shown > 10) break;
                Vec3 ahead = future(t, 30);
                Foresight.show(viewer, Kind.PRECOG_GHOST, t.position(), ahead, t.getBbHeight());
            }
            com.jayptucker.heroesevolved.invisibility.InvisibilityService.revealAround(level, viewer.position(), 24, 20);
        }
        if (report) viewer.displayClientMessage(Component.literal(hunting == 0 ? "Premonition: nothing has chosen you."
                : "Premonition: " + hunting + (hunting == 1 ? " mind has" : " minds have") + " chosen you.").withStyle(ChatFormatting.GOLD), true);
    }

    /** Where an entity will be in about {@code ticks}: along its path if it has one, otherwise along its motion. */
    public static Vec3 future(LivingEntity entity, int ticks) {
        if (entity instanceof Mob mob) {
            var path = mob.getNavigation().getPath();
            if (path != null && !path.isDone()) {
                double speed = Math.max(0.1, mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)) * 2.2;
                int steps = Math.max(1, (int) Math.round(speed * ticks / 20.0 * 4));
                int index = Math.min(path.getNodeCount() - 1, path.getNextNodeIndex() + steps);
                if (index >= 0) return Vec3.atBottomCenterOf(path.getNodePos(index));
            }
        }
        Vec3 motion = entity.getDeltaMovement();
        return entity.position().add(motion.x * ticks, 0, motion.z * ticks);
    }

    // ------------------------------------------------------------------
    // Paint the Future
    // ------------------------------------------------------------------

    public static boolean painting(ServerPlayer p) { return PAINTINGS.containsKey(p.getUUID()); }

    /** Starts the trance. An enemy in view gets their ending painted; otherwise the canvas shows what is coming. */
    public static boolean paint(ServerPlayer p) {
        if (painting(p)) return false;
        LivingEntity target = Targeting.livingEntityInSight(p, 32, t -> enemy(p, t));
        int ticks = target == null ? STORY_PAINT_TICKS : COMBAT_PAINT_TICKS;
        PAINTINGS.put(p.getUUID(), new Painting(p, target == null ? null : target.getUUID(), now(p), now(p) + ticks));
        ArmPosePayload.show(p, ArmPose.ONE_HAND, ticks);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BRUSH_GENERIC, SoundSource.PLAYERS, 1.0F, 0.8F);
        if (target != null) {
            PrivateGlow.glow(p, target, ticks);
            p.displayClientMessage(Component.literal("You paint their ending...").withStyle(ChatFormatting.RED), true);
        } else {
            p.displayClientMessage(Component.literal("Your hand moves on its own. Hold still.").withStyle(ChatFormatting.GOLD), true);
        }
        return true;
    }

    private static void tickPaintings(MinecraftServer server, long now) {
        for (var entry : List.copyOf(PAINTINGS.entrySet())) {
            Painting painting = entry.getValue();
            ServerPlayer p = painting.owner;
            if (!valid(p) || p.isRemoved()) { PAINTINGS.remove(entry.getKey()); continue; }
            float progress = (now - painting.start) / (float) Math.max(1, painting.done - painting.start);
            // The trance holds you nearly still.
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 3, false, false, false));
            if (now % 3 == 0) {
                Vec3 hand = p.getEyePosition().add(p.getLookAngle().scale(0.7)).subtract(0, 0.35, 0);
                AbilityEffects.send(p.serverLevel(), Kind.PRECOG_BRUSH, hand, hand.add(p.getLookAngle()), Math.max(0.2F, progress));
            }
            if (now % 8 == 0) p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BRUSH_GENERIC, SoundSource.PLAYERS, 0.6F, 0.9F + progress * 0.4F);
            if (now < painting.done) continue;
            PAINTINGS.remove(entry.getKey());
            ArmPosePayload.clear(p);
            if (painting.target == null) { vision(p); continue; }
            Entity found = p.serverLevel().getEntity(painting.target);
            if (found instanceof LivingEntity target && target.isAlive() && target.distanceToSqr(p) < 40 * 40 && p.hasLineOfSight(target)) {
                paintEnding(p, target);
            } else {
                p.displayClientMessage(Component.literal("The paint runs. They moved out of the picture.").withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    /**
     * Paints an enemy's ending: for fifteen seconds everyone hurts them a fifth more, and once they are
     * at a quarter of their health (more, with Clarity spent) the painted moment comes true.
     */
    public static void paintEnding(ServerPlayer p, LivingEntity target) {
        int spent = Math.min(50, Foresight.clarity(p));
        if (spent > 0) Foresight.addClarity(p, -spent);
        float threshold = 0.25F + spent / 200F;
        ENDINGS.put(target.getUUID(), new Ending(p, now(p) + ENDING_TICKS, threshold));
        AbilityEffects.send(p.serverLevel(), Kind.PRECOG_CANVAS, target.position().add(0, target.getBbHeight() + 0.9, 0), target.position(), threshold);
        AbilityEffects.send(p.serverLevel(), Kind.PRECOG_THREAD, p.getEyePosition(), target.getEyePosition(), 1);
        p.serverLevel().playSound(null, target.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.6F);
        p.displayClientMessage(Component.literal("Their ending is painted. Bring them to it (" + Math.round(threshold * 100) + "% health).")
                .withStyle(ChatFormatting.RED), true);
    }

    public static boolean painted(LivingEntity target) { return ENDINGS.containsKey(target.getUUID()); }

    private static boolean executable(LivingEntity target) {
        return !(target instanceof ServerPlayer) && !(target instanceof SylarEntity) && target.getMaxHealth() <= 100
                && !(target instanceof net.minecraft.world.entity.boss.wither.WitherBoss)
                && !(target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon);
    }

    private static void tickEndings(MinecraftServer server, long now) {
        for (var entry : List.copyOf(ENDINGS.entrySet())) {
            Ending ending = entry.getValue();
            Entity found = ending.owner.serverLevel().getEntity(entry.getKey());
            if (!(found instanceof LivingEntity target) || !target.isAlive() || !valid(ending.owner)) { ENDINGS.remove(entry.getKey()); continue; }
            if (now >= ending.until) {
                ENDINGS.remove(entry.getKey());
                ending.owner.displayClientMessage(Component.literal("The future changed. They slipped out of the painting.").withStyle(ChatFormatting.GRAY), true);
                continue;
            }
            if (now % 10 == 0) AbilityEffects.send(ending.owner.serverLevel(), Kind.PRECOG_CANVAS,
                    target.position().add(0, target.getBbHeight() + 0.9, 0), target.position(), ending.threshold);
            if (target.getHealth() <= target.getMaxHealth() * ending.threshold) fulfil(ending, target);
        }
    }

    /** The painted moment arrives. */
    private static void fulfil(Ending ending, LivingEntity target) {
        ENDINGS.remove(target.getUUID());
        ServerPlayer owner = ending.owner;
        var level = owner.serverLevel();
        AbilityEffects.send(level, Kind.PRECOG_ENDING, target.position(), target.getEyePosition(), target.getBbHeight());
        level.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.5F, 1.8F);
        level.playSound(null, target.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.2F, 0.5F);
        Foresight.addClarity(owner, 15);
        if (executable(target)) {
            target.invulnerableTime = 0;
            target.hurt(owner.damageSources().indirectMagic(owner, owner), target.getHealth() + target.getAbsorptionAmount() + 100);
            owner.displayClientMessage(Component.literal("Just as you painted it.").withStyle(ChatFormatting.RED), true);
        } else {
            // People and the very strong are not so easily finished: the moment freezes them instead.
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 4));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
            if (!(target instanceof ServerPlayer)) {
                target.invulnerableTime = 0;
                target.hurt(owner.damageSources().indirectMagic(owner, owner), target.getMaxHealth() * 0.1F);
            }
            owner.displayClientMessage(Component.literal("The painted moment holds them still.").withStyle(ChatFormatting.RED), true);
        }
    }

    /**
     * A canvas painted with no one in view shows what is coming for you: the Hourglass Annex while it
     * is unresolved, otherwise Sylar if he is due to find you, otherwise what is near.
     */
    private static void vision(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        FacilityData annex = FacilityData.get(server);
        boolean unresolved = annex.phase != FacilityData.Phase.SAVED && annex.phase != FacilityData.Phase.SCARRED;
        if (unresolved || !annex.discovered(p.getUUID(), "unfinished_hour")) {
            FacilityService.plan(p);
            PropheticPainting.give(p);
            FacilityService.discovery(p, "canvas_received");
            p.sendSystemMessage(Component.literal("The Unfinished Hour: two people behind glass, a copper heart running hot. It has not happened yet.")
                    .withStyle(ChatFormatting.GOLD));
            return;
        }
        var trail = SylarStoryData.get(server).trail(p.getUUID());
        long until = trail.nextEncounter - now(p);
        if (trail.chapter > 0 && until > 0 && until < 20L * 60 * 20) {
            long minutes = Math.max(1, (until + 1199) / 1200);
            SYLAR_FORETOLD.add(p.getUUID());
            p.sendSystemMessage(Component.literal("You paint a man in a dark coat, one hand raised, and yourself pinned to the wall. He finds you in about "
                    + minutes + (minutes == 1 ? " minute" : " minutes") + ". You will see his first move coming.").withStyle(ChatFormatting.DARK_RED));
            FacilityService.discovery(p, "sylar_foretold");
            return;
        }
        SENSE.put(p.getUUID(), new Timed(p, now(p) + PREMONITION_TICKS * 2));
        pulse(p, p, true);
        p.sendSystemMessage(Component.literal("Nothing is written for you yet, so you paint what is near: every threat around you, for a while.")
                .withStyle(ChatFormatting.GOLD));
    }

    // ------------------------------------------------------------------
    // Saw It Coming, Exposed, The Future Is Written
    // ------------------------------------------------------------------

    public static boolean sawItComing(ServerPlayer p) {
        READS.put(p.getUUID(), now(p) + READ_TICKS);
        AbilityEffects.send(p.serverLevel(), Kind.PRECOG_FUTURE, p.position(), p.position().add(p.getLookAngle()), 0.6F);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.6F);
        return true;
    }

    public static boolean reading(ServerPlayer p) { Long until = READS.get(p.getUUID()); return until != null && now(p) < until; }

    public static boolean exposed(LivingEntity target) { return EXPOSED.containsKey(target.getUUID()); }

    public static boolean futureWritten(ServerPlayer p) { Timed t = WRITTEN.get(p.getUUID()); return t != null && now(p) < t.until; }

    /** Spends all Clarity: four seconds, plus a second for every ten Clarity. */
    public static boolean writeTheFuture(ServerPlayer p) {
        if (futureWritten(p)) return false;
        int clarity = Foresight.drainClarity(p);
        int ticks = 80 + clarity * 2;
        WRITTEN.put(p.getUUID(), new Timed(p, now(p) + ticks));
        ScreenTintPayload.send(p, 0x38E8C080, ticks, ScreenTintPayload.Style.EDGE);
        AbilityEffects.send(p.serverLevel(), Kind.PRECOG_FUTURE, p.position(), p.position().add(p.getLookAngle().scale(3)), 2.5F);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.5F);
        ArmPosePayload.show(p, ArmPose.ARMS_WIDE, 14);
        p.displayClientMessage(Component.literal("The Future Is Written: you live " + ticks / 20 + " seconds ahead of everyone.")
                .withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    /** Steps the player aside from an attack coming from {@code from}, leaving a ghost where it would have landed. */
    private static void sidestep(ServerPlayer p, Vec3 from) {
        Vec3 away = p.position().subtract(from).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0E-4) away = p.getLookAngle().multiply(-1, 0, -1);
        away = away.normalize();
        Vec3 side = new Vec3(-away.z, 0, away.x);
        Vec3 start = p.position();
        for (Vec3 offset : new Vec3[] {side.scale(1.6), side.scale(-1.6), away.scale(1.4), side.scale(0.8), side.scale(-0.8)}) {
            if (!p.level().noCollision(p, p.getBoundingBox().move(offset))) continue;
            Vec3 to = start.add(offset);
            p.teleportTo(to.x, to.y, to.z);
            break;
        }
        AbilityEffects.send(p.serverLevel(), Kind.PRECOG_GHOST, start, start, p.getBbHeight());
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 1.7F);
    }

    private static void expose(ServerPlayer p, Entity attacker) {
        if (!(attacker instanceof LivingEntity living) || attacker == p) return;
        EXPOSED.put(living.getUUID(), new Timed(p, now(p) + EXPOSED_TICKS));
        PrivateGlow.glow(p, living, EXPOSED_TICKS);
        AbilityEffects.send(p.serverLevel(), Kind.PRECOG_EXPOSED, living.position(), living.getEyePosition(), living.getBbHeight());
    }

    // ------------------------------------------------------------------
    // Fate Thread
    // ------------------------------------------------------------------

    /** Holds one lethal outcome off a friend in view (or yourself, with Shift) for eight seconds. */
    public static boolean fateThread(ServerPlayer p) {
        LivingEntity t = p.isShiftKeyDown() ? p : Targeting.livingEntityInSight(p, 12, e -> e instanceof ServerPlayer && ExpansionSkills.friendly(p, e));
        if (t == null) t = p;
        if (FATE.containsKey(t.getUUID()) || SPARED.getOrDefault(t.getUUID(), 0L) > now(p)
                || t instanceof ServerPlayer other && CooldownService.isOnCooldown(other, SPARED_COOLDOWN)) return false;
        FATE.put(t.getUUID(), new Timed(p, now(p) + FATE_TICKS));
        AbilityEffects.send(p.serverLevel(), Kind.PRECOG_THREAD, p.getEyePosition(), t.getEyePosition(), 2);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.3F);
        p.displayClientMessage(Component.literal(t == p ? "Fate Thread: your own ending is crossed out for eight seconds."
                : "Fate Thread: " + t.getName().getString() + "'s ending is crossed out for eight seconds.").withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    public static boolean threaded(LivingEntity entity) { return FATE.containsKey(entity.getUUID()); }

    // ------------------------------------------------------------------
    // Skill tree actives
    // ------------------------------------------------------------------

    /** Object Reading, Red Margin and Omen Relay. */
    public static boolean skill(AbilityUseContext c, int branch) {
        ServerPlayer p = c.player();
        if (branch == 0) {
            FacilityService.plan(p);
            p.sendSystemMessage(Component.literal("Object Reading: " + FacilityService.journal(p)));
            for (var t : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, p.getBoundingBox().inflate(32))) PrivateGlow.glow(p, t, 100);
        } else if (branch == 1) {
            var targets = ExpansionSkills.enemies(p, p.position(), 12).stream().limit(3).toList();
            if (targets.isEmpty()) return false;
            for (var t : targets) {
                INTENTS.put(t.getUUID(), new Timed(p, now(p) + 80));
                PrivateGlow.glow(p, t, 80);
                AbilityEffects.send(p.serverLevel(), Kind.PRECOG_EXPOSED, t.position(), t.getEyePosition(), t.getBbHeight());
            }
        } else {
            SENSE.put(p.getUUID(), new Timed(p, now(p) + 120));
            for (ServerPlayer ally : p.serverLevel().players())
                if (p != ally && p.isAlliedTo(ally) && p.distanceToSqr(ally) < 144) SENSE.put(ally.getUUID(), new Timed(p, now(p) + 120));
        }
        return true;
    }

    /** Red Margin's mark on an attacker: its next damaging attack on a friend is crossed out. */
    public static boolean intent(LivingEntity attacker) { return INTENTS.containsKey(attacker.getUUID()); }

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    /** Attacks that were seen coming: Saw It Coming, The Future Is Written, Red Margin and a foretold Sylar. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void incoming(LivingIncomingDamageEvent e) {
        var source = e.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        Entity attacker = source.getEntity(), direct = source.getDirectEntity();
        if (attacker instanceof LivingEntity living) {
            Timed intent = INTENTS.get(living.getUUID());
            if (intent != null && now(intent.owner) < intent.until && valid(intent.owner) && ExpansionSkills.friendly(intent.owner, e.getEntity())) {
                INTENTS.remove(living.getUUID());
                e.setCanceled(true);
                AbilityEffects.send(intent.owner.serverLevel(), Kind.PRECOG_CROSS, e.getEntity().getEyePosition(), living.position(), 1.2F);
                Foresight.changed(intent.owner, e.getEntity().getEyePosition(), 6);
                return;
            }
        }
        if (!(e.getEntity() instanceof ServerPlayer p) || attacker == p || attacker == null && direct == null || !valid(p)) return;
        Vec3 from = (direct != null ? direct : attacker).position();
        if (attacker instanceof SylarEntity && SYLAR_FORETOLD.remove(p.getUUID())) {
            e.setCanceled(true);
            sidestep(p, from);
            p.displayClientMessage(Component.literal("You painted this moment. You are already out of his way.").withStyle(ChatFormatting.DARK_RED), true);
            Foresight.changed(p, from, 20);
            return;
        }
        if (reading(p)) {
            READS.remove(p.getUUID());
            e.setCanceled(true);
            sidestep(p, from);
            expose(p, attacker);
            Foresight.changed(p, p.getEyePosition(), 20);
            // A perfect read is ready again almost at once.
            CooldownService.startCooldown(p, SAW_IT_COMING_ID, 20);
            return;
        }
        if (futureWritten(p) && PlayerEnergyService.tryConsume(p, WRITTEN_DODGE_COST)) {
            e.setCanceled(true);
            sidestep(p, from);
            expose(p, attacker);
            Foresight.addClarity(p, 2);
        }
    }

    /** Damage dealt into a foreseen future: painted endings, Exposed enemies, and The Future Is Written. */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void amplify(LivingIncomingDamageEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        LivingEntity target = e.getEntity();
        float amount = e.getAmount();
        Ending ending = ENDINGS.get(target.getUUID());
        if (ending != null && (attacker == ending.owner || attacker.isAlliedTo(ending.owner))) amount *= ENDING_BONUS;
        Timed exposed = EXPOSED.get(target.getUUID());
        if (exposed != null && exposed.owner == attacker) {
            EXPOSED.remove(target.getUUID());
            amount *= EXPOSED_BONUS;
            AbilityEffects.send(attacker.serverLevel(), Kind.PRECOG_CROSS, target.getEyePosition(), attacker.getEyePosition(), 1.0F);
        }
        if (futureWritten(attacker) && valid(attacker)) amount *= WRITTEN_BONUS;
        e.setAmount(amount);
    }

    /** A painted ending is checked as soon as the blow lands. */
    @SubscribeEvent
    public static void damaged(LivingDamageEvent.Post e) {
        Ending ending = ENDINGS.get(e.getEntity().getUUID());
        if (ending != null && e.getEntity().isAlive() && valid(ending.owner)
                && e.getEntity().getHealth() <= e.getEntity().getMaxHealth() * ending.threshold) fulfil(ending, e.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void death(LivingDeathEvent e) {
        Timed fate = FATE.remove(e.getEntity().getUUID());
        if (fate == null) return;
        boolean selfDeath = fate.owner == e.getEntity();
        boolean eligible = selfDeath
                ? !EclipseService.arePowersSuppressed(fate.owner) && PlayerAbilityService.getData(fate.owner).assignedPower()
                        .filter(a -> a.getValue().isUnlocked() && a.getKey().equals(ModAbilities.PRECOGNITION_ID)).isPresent()
                : valid(fate.owner);
        if (!eligible || now(fate.owner) >= fate.until || e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || e.getSource().is(com.jayptucker.heroesevolved.time.TemporalParadoxService.TEMPORAL_PARADOX_DAMAGE)) return;
        SPARED.put(e.getEntity().getUUID(), now(fate.owner) + 600);
        if (e.getEntity() instanceof ServerPlayer rescued) CooldownService.startCooldown(rescued, SPARED_COOLDOWN, 600);
        e.setCanceled(true);
        e.getEntity().setHealth(2);
        e.getEntity().invulnerableTime = 40;
        AbilityEffects.send(fate.owner.serverLevel(), Kind.PRECOG_CROSS, e.getEntity().getEyePosition(), e.getEntity().getEyePosition(), 2.0F);
        Foresight.changed(fate.owner, e.getEntity().getEyePosition(), 30);
        if (!selfDeath) MasteryService.awardRescue(fate.owner, 25);
        fate.owner.sendSystemMessage(Component.literal("That ending is crossed out. Get them somewhere safe.").withStyle(ChatFormatting.GOLD));
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post e) { tick(e.getServer()); }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        tickPaintings(server, now);
        tickEndings(server, now);
        Foresight.tick(server);

        SENSE.entrySet().removeIf(e -> now >= e.getValue().until || !valid(e.getValue().owner));
        if (now % 10 == 0) for (var entry : SENSE.entrySet()) {
            ServerPlayer owner = entry.getValue().owner;
            ServerPlayer viewer = entry.getKey().equals(owner.getUUID()) ? owner : server.getPlayerList().getPlayer(entry.getKey());
            if (viewer != null && viewer.level() == entry.getValue().owner.level()) pulse(viewer, entry.getValue().owner, viewer == entry.getValue().owner);
        }
        READS.values().removeIf(until -> now >= until);
        EXPOSED.values().removeIf(t -> now >= t.until || !valid(t.owner));
        INTENTS.values().removeIf(t -> now >= t.until || !valid(t.owner));
        SPARED.values().removeIf(until -> until <= now);
        for (var entry : List.copyOf(FATE.entrySet())) {
            Timed t = entry.getValue();
            if (now >= t.until || !valid(t.owner)) { FATE.remove(entry.getKey()); continue; }
            if (now % 10 == 0 && t.owner.serverLevel().getEntity(entry.getKey()) instanceof LivingEntity held && held != t.owner)
                AbilityEffects.send(t.owner.serverLevel(), Kind.PRECOG_THREAD, t.owner.getEyePosition().subtract(0, 0.4, 0), held.getEyePosition().subtract(0, 0.4, 0), 1);
        }
        for (var entry : List.copyOf(WRITTEN.entrySet())) {
            ServerPlayer p = entry.getValue().owner;
            if (p.isRemoved() || now >= entry.getValue().until || !valid(p)) {
                WRITTEN.remove(entry.getKey());
                if (!p.isRemoved()) p.displayClientMessage(Component.literal("The present catches up with you.").withStyle(ChatFormatting.GRAY), true);
                continue;
            }
            if (now % 10 == 0) pulse(p, p, true, false);
            // Shots that would land are already crossed out of the future.
            for (Projectile shot : p.serverLevel().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(6))) {
                if (shot.getOwner() == p || shot.getOwner() != null && p.isAlliedTo(shot.getOwner())) continue;
                if (Foresight.predictHit(shot, p.getBoundingBox().inflate(0.35)).isEmpty()) continue;
                AbilityEffects.send(p.serverLevel(), Kind.PRECOG_CROSS, shot.position(), shot.position(), 0.8F);
                shot.discard();
            }
        }
    }

    public static void forget(ServerPlayer p) {
        UUID id = p.getUUID();
        SENSE.entrySet().removeIf(e -> e.getKey().equals(id) || e.getValue().owner == p);
        PAINTINGS.remove(id); READS.remove(id); WRITTEN.remove(id); SYLAR_FORETOLD.remove(id);
        ENDINGS.values().removeIf(t -> t.owner == p);
        EXPOSED.values().removeIf(t -> t.owner == p);
        INTENTS.values().removeIf(t -> t.owner == p);
        FATE.entrySet().removeIf(e -> e.getKey().equals(id) || e.getValue().owner == p);
        Foresight.forget(p);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent e) {
        SENSE.clear(); PAINTINGS.clear(); ENDINGS.clear(); READS.clear(); WRITTEN.clear(); EXPOSED.clear(); INTENTS.clear();
        FATE.clear(); SPARED.clear(); SYLAR_FORETOLD.clear(); Foresight.clear();
    }
}
