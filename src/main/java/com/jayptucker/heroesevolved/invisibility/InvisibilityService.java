package com.jayptucker.heroesevolved.invisibility;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.OpticalStatePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.network.UnseenPayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Invisibility: bending light round yourself, your friends and the things you touch. The server owns concealment,
 * Exposure and the search clues hunters follow. The force fields live in {@link ForceFieldService}.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class InvisibilityService {
    private static final class Fade {
        final ServerPlayer player;
        final ServerLevel level;
        final long began;
        final MobEffectInstance priorPotion;
        long until;
        boolean drains, wet, quiet;
        int sharing;
        double exposure;
        Vec3 previous, lastSeen;
        Fade(ServerPlayer player, long until, boolean drains) {
            this.player = player; this.level = player.serverLevel(); this.began = now(player.getServer());
            this.until = until; this.drains = drains;
            this.previous = this.lastSeen = player.position();
            var potion = player.getEffect(MobEffects.INVISIBILITY);
            this.priorPotion = potion == null ? null : new MobEffectInstance(potion);
        }
    }
    /** Something other than a player made unseen by Vanishing Touch, Vanishing Squad or a shared Fade. */
    private record Hidden(Entity entity, ServerLevel level, long until) {}
    private record Search(Mob mob, ServerLevel level, Vec3 clue, long until) {}

    static final double SHARE_REACH = 3, SQUAD_REACH = 8, LONG_SQUAD_REACH = 12, TOUCH_RANGE = 16, LONG_TOUCH_RANGE = 24;
    static final int TOUCH_TICKS = 400, LONG_TOUCH_TICKS = 800, SQUAD_TICKS = 200, LONG_SQUAD_TICKS = 400, MAX_SHARED = 4;

    private static final Map<UUID, Fade> FADES = new HashMap<>();
    private static final Map<UUID, Long> REVEALED = new HashMap<>(), SQUADS = new HashMap<>();
    private static final Map<UUID, Hidden> HIDDEN = new HashMap<>();
    private static final Map<UUID, Search> SEARCHES = new HashMap<>();
    private static final Map<UUID, Boolean> SYNCED = new HashMap<>();
    private static final long FOREVER = Long.MAX_VALUE;
    private InvisibilityService() {}
    private static long now(MinecraftServer server) { return server.overworld().getGameTime(); }

    public static boolean isInvisibilityOwner(ServerPlayer p) {
        return PlayerAbilityService.getData(p).assignedPower()
                .filter(e -> e.getKey().equals(ModAbilities.INVISIBILITY_ID) && e.getValue().isUnlocked()).isPresent()
                && !EclipseService.arePowersSuppressed(p);
    }
    public static boolean isFaded(ServerPlayer p) { return FADES.containsKey(p.getUUID()); }
    public static boolean isUnseen(ServerPlayer p) {
        Fade fade = FADES.get(p.getUUID());
        return fade != null && fade.level == p.level() && p.isAlive() && !p.isOnFire()
                && REVEALED.getOrDefault(p.getUUID(), 0L) <= now(p.getServer());
    }
    /** Whether a creature or object (not a player) is hidden by Vanishing Touch or a shared veil. */
    public static boolean isHidden(Entity e) { return HIDDEN.containsKey(e.getUUID()); }
    public static int exposure(ServerPlayer p) {
        Fade fade = FADES.get(p.getUUID());
        return fade == null ? 0 : (int) Math.round(fade.exposure);
    }
    public static int sharing(ServerPlayer p) { Fade f = FADES.get(p.getUUID()); return f == null ? 0 : f.sharing; }
    public static boolean squadActive(ServerPlayer p) { return SQUADS.getOrDefault(p.getUUID(), 0L) > now(p.getServer()); }

    public static void addExposure(ServerPlayer p, double amount) {
        Fade fade = FADES.get(p.getUUID());
        if (fade == null || !Double.isFinite(amount)) return;
        fade.exposure = Math.clamp(fade.exposure + amount, 0, 100);
        if (fade.exposure >= 85) {
            fade.exposure = 65;
            reveal(p, 20);
            AbilityEffects.send(p.serverLevel(), Kind.OPTIC_RIPPLE, p.position(), p.getEyePosition(), 1);
        }
    }

    /** Timed shares refresh their expiry without replaying the dissolve every heartbeat. */
    public static void fade(ServerPlayer p, int ticks, boolean drains) {
        long until = drains ? FOREVER : now(p.getServer()) + Math.max(1, ticks);
        Fade state = FADES.get(p.getUUID());
        boolean fresh = state == null;
        if (fresh) { state = new Fade(p, until, drains); FADES.put(p.getUUID(), state); }
        else if (drains) { state.until = FOREVER; state.drains = true; }
        else if (!state.drains) state.until = Math.max(until, state.until);
        if (fresh) {
            loseTargets(p, 32, state.lastSeen);
            AbilityEffects.send(p.serverLevel(), Kind.OPTIC_FADE, p.position(), p.getEyePosition(), 1);
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, .6F, 1.4F);
        }
        sync(p);
    }
    /** A Fade given by someone else: with Quiet Light, the one sharing it keeps it steady and it builds no Exposure. */
    private static void shareFade(ServerPlayer from, ServerPlayer friend, int ticks) {
        fade(friend, ticks, false);
        Fade f = FADES.get(friend.getUUID());
        if (f != null && friend != from) f.quiet = SkillTreeService.has(from, InvisibilitySkills.QUIET_LIGHT);
    }
    public static boolean unfade(ServerPlayer p) {
        Fade state = FADES.remove(p.getUUID());
        if (state == null) return false;
        SQUADS.remove(p.getUUID());
        p.removeEffect(MobEffects.INVISIBILITY);
        AbilityEffects.send(p.serverLevel(), Kind.OPTIC_REFORM, p.position(), p.getEyePosition(), 1);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, .5F, 1.2F);
        sync(p);
        if (state.priorPotion != null) {
            boolean infinite = state.priorPotion.isInfiniteDuration();
            int remaining = infinite ? -1 : state.priorPotion.getDuration() - (int) (now(p.getServer()) - state.began);
            if (infinite || remaining > 0) p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, remaining,
                    state.priorPotion.getAmplifier(), state.priorPotion.isAmbient(), state.priorPotion.isVisible()));
        }
        return true;
    }
    public static void reveal(ServerPlayer p, int ticks) {
        if (!isFaded(p)) return;
        REVEALED.merge(p.getUUID(), now(p.getServer()) + Math.max(1, ticks), Math::max);
        Fade fade = FADES.get(p.getUUID());
        fade.lastSeen = p.position();
        sync(p);
    }
    public static void revealAround(ServerLevel level, Vec3 center, double radius, int ticks) {
        for (ServerPlayer p : level.players()) if (p.position().distanceToSqr(center) <= radius * radius) reveal(p, ticks);
        for (Hidden h : List.copyOf(HIDDEN.values()))
            if (h.level == level && h.entity.position().distanceToSqr(center) <= radius * radius) unhide(h.entity);
    }
    /** A force move gives its caster away, the same as a blow. */
    public static void forceAttack(ServerPlayer p) { attack(p); }
    private static void sync(ServerPlayer p) {
        boolean hidden = isUnseen(p);
        if (!hidden && isFaded(p)) p.removeEffect(MobEffects.INVISIBILITY);
        if (SYNCED.getOrDefault(p.getUUID(), false) != hidden) {
            SYNCED.put(p.getUUID(), hidden);
            if (hidden) p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false));
            else p.removeEffect(MobEffects.INVISIBILITY);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new UnseenPayload(p.getId(), hidden));
        }
        OpticalStatePayload.send(p, isFaded(p), hidden, exposure(p), sharing(p) > 0, squadActive(p));
    }
    private static void search(Mob mob, Entity hidden, Vec3 clue) {
        if (mob.level() != hidden.level()) return;
        mob.setTarget(null);
        if (mob.getLastHurtByMob() == hidden) mob.setLastHurtByMob(null);
        SEARCHES.put(mob.getUUID(), new Search(mob, (ServerLevel) mob.level(), clue, now(mob.getServer()) + 60));
        mob.getNavigation().moveTo(clue.x, clue.y, clue.z, .9);
        mob.getLookControl().setLookAt(clue.x, clue.y + 1, clue.z);
    }
    private static void loseTargets(LivingEntity e, double radius, Vec3 clue) {
        for (Mob mob : e.level().getEntitiesOfClass(Mob.class, e.getBoundingBox().inflate(radius), m -> m.getTarget() == e)
                .stream().limit(24).toList()) search(mob, e, clue);
    }

    // ================================================================ G: Fade

    public static boolean toggleFade(ServerPlayer p) {
        if (unfade(p)) return true;
        if (!PowerRules.canAct(p)) return false;
        fade(p, 0, true);
        ArmPosePayload.show(p, ArmPose.OPTICAL_VEIL, 14);
        p.displayClientMessage(Component.translatable("invisibility.heroesevolved.faded").withStyle(ChatFormatting.GRAY), true);
        return true;
    }
    public static void awaken(ServerPlayer p) {
        fade(p, 100, false);
        p.displayClientMessage(Component.translatable("invisibility.heroesevolved.awakened").withStyle(ChatFormatting.GRAY), false);
    }

    // ================================================================ objects and creatures

    /** Hides a creature or object that isn't a player: everyone's client stops drawing it until it returns. */
    public static void hide(Entity e, int ticks) {
        if (e instanceof ServerPlayer) return;
        long until = now(e.getServer()) + ticks;
        Hidden old = HIDDEN.get(e.getUUID());
        HIDDEN.put(e.getUUID(), new Hidden(e, (ServerLevel) e.level(), old == null ? until : Math.max(until, old.until)));
        if (e instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks + 20, 0, false, false));
            loseTargets(living, 32, living.position());
        }
        if (old == null) {
            PacketDistributor.sendToPlayersTrackingEntity(e, new UnseenPayload(e.getId(), true));
            AbilityEffects.send((ServerLevel) e.level(), Kind.OPTIC_FADE, e.position(), e.position().add(0, e.getBbHeight(), 0), 1);
        }
    }
    public static void unhide(Entity e) {
        if (HIDDEN.remove(e.getUUID()) == null) return;
        if (e instanceof LivingEntity living) living.removeEffect(MobEffects.INVISIBILITY);
        if (!e.isRemoved()) {
            PacketDistributor.sendToPlayersTrackingEntity(e, new UnseenPayload(e.getId(), false));
            AbilityEffects.send((ServerLevel) e.level(), Kind.OPTIC_REFORM, e.position(), e.position().add(0, e.getBbHeight(), 0), 1);
        }
    }

    /** Vanishing Touch: the friend, pet or thing under your aim fades from sight for twenty seconds. */
    public static boolean vanishingTouch(ServerPlayer p) {
        if (!PowerRules.canAct(p)) return false;
        boolean longer = SkillTreeService.has(p, InvisibilitySkills.LONG_VANISH);
        double range = longer ? LONG_TOUCH_RANGE : TOUCH_RANGE;
        int ticks = longer ? LONG_TOUCH_TICKS : TOUCH_TICKS;
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(range));
        HitResult wall = p.serverLevel().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (wall.getType() != HitResult.Type.MISS) end = wall.getLocation();
        var hit = ProjectileUtil.getEntityHitResult(p, eye, end, p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1),
                e -> e != p && !e.isSpectator() && !(e instanceof Projectile) && !(e instanceof net.minecraft.world.entity.monster.Enemy)
                        && !(e instanceof ServerPlayer other && !ExpansionSkills.friendly(p, other)),
                eye.distanceToSqr(end));
        if (hit == null) return false;
        Entity target = hit.getEntity();
        if (target instanceof ServerPlayer friend) {
            if (!ExpansionSkills.friendly(p, friend) || !PowerRules.canAct(friend)) return false;
            shareFade(p, friend, ticks);
        } else hide(target, ticks);
        SetPiecePayload.send(p.serverLevel(), Piece.FORCE_VANISH, target.position(), target.position().add(0, target.getBbHeight(), 0),
                (float) ForceGeometry.bubbleRadius(target), target);
        ArmPosePayload.show(p, ArmPose.OPTICAL_SPLIT, 14);
        p.serverLevel().playSound(null, target.blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, .7F, 1.6F);
        return true;
    }

    /** Vanishing Squad: you and every friend, pet and companion around you fade at once. */
    public static boolean vanishingSquad(ServerPlayer p) {
        if (!PowerRules.canAct(p)) return false;
        boolean longer = SkillTreeService.has(p, InvisibilitySkills.LONG_VANISH);
        double reach = longer ? LONG_SQUAD_REACH : SQUAD_REACH;
        int ticks = longer ? LONG_SQUAD_TICKS : SQUAD_TICKS;
        fade(p, ticks, false);
        SQUADS.put(p.getUUID(), now(p.getServer()) + ticks);
        for (LivingEntity t : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(reach),
                e -> e != p && e.isAlive() && e.distanceTo(p) <= reach && ForceFieldService.friend(p, e))) {
            if (t instanceof ServerPlayer friend) { if (PowerRules.canAct(friend)) shareFade(p, friend, ticks); }
            else hide(t, ticks);
        }
        SetPiecePayload.send(p.serverLevel(), Piece.FORCE_VANISH, p.position(), p.getEyePosition(), (float) reach, null);
        ArmPosePayload.show(p, ArmPose.OPTICAL_DISPERSE, 16);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, .8F, 1.3F);
        sync(p);
        return true;
    }

    // ================================================================ tick

    public static void tick(MinecraftServer server) {
        long time = now(server);
        for (Fade fade : List.copyOf(FADES.values())) {
            ServerPlayer p = fade.player;
            if (p.isRemoved() || p.level() != fade.level || !PowerRules.canAct(p) || time >= fade.until
                    || fade.drains && !isInvisibilityOwner(p)
                    && !com.jayptucker.heroesevolved.mimicry.MimicryService.isBorrowingInvisibility(p)) { unfade(p); continue; }
            if (fade.drains && time % 10 == 0) share(p, fade);
            if (fade.drains && time % 20 == 0) {
                int cost = 2 + fade.sharing;
                if (!PlayerEnergyService.tryConsume(p, cost)) {
                    p.displayClientMessage(Component.translatable("invisibility.heroesevolved.exhausted").withStyle(ChatFormatting.GRAY), true);
                    unfade(p); continue;
                }
                MasteryService.awardEnergySpent(p, cost);
            }
            double distance = p.position().distanceTo(fade.previous);
            fade.previous = p.position();
            int level = Math.clamp(PlayerProgressionService.getLevel(p), 1, 5);
            boolean moving = distance > .02 && distance < 4;
            double amount = moving ? p.isSprinting() ? .45 + .1 * (6 - level)
                    : p.isShiftKeyDown() ? -.15 : .06 * (6 - level) : -.8;
            if (amount > 0 && moving && !p.isSprinting() && SkillTreeService.has(p, InvisibilitySkills.QUIET_LIGHT)) amount *= .5;
            if (amount > 0 && fade.quiet) amount = 0;
            addExposure(p, amount);
            boolean wet = p.isInWaterRainOrBubble();
            if (wet && !fade.wet) { addExposure(p, 30); reveal(p, 12); }
            else if (wet) addExposure(p, .6);
            fade.wet = wet;
            if (p.isOnFire()) reveal(p, 20);
            if (!isUnseen(p)) fade.lastSeen = p.position();
            if (isUnseen(p) && time % 5 == 0) loseTargets(p, 24, fade.lastSeen);
            if (time % 40 == 0 && isUnseen(p)) p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false));
            if (moving && exposure(p) >= 35 && time % 16 == 0)
                AbilityEffects.send(p.serverLevel(), Kind.OPTIC_RIPPLE, p.position(), p.getEyePosition(), exposure(p) / 100.0);
            if (time % 5 == 0) sync(p);
        }
        REVEALED.values().removeIf(until -> until <= time);
        SQUADS.values().removeIf(until -> until <= time);
        for (Hidden h : List.copyOf(HIDDEN.values()))
            if (time >= h.until || h.entity.isRemoved() || h.entity.level() != h.level
                    || h.entity instanceof LivingEntity l && (!l.isAlive() || l.isOnFire())) unhide(h.entity);
        for (Search search : List.copyOf(SEARCHES.values())) {
            Mob mob = search.mob;
            if (!mob.isAlive() || mob.isRemoved() || mob.level() != search.level || time >= search.until || mob.getTarget() != null) {
                SEARCHES.remove(mob.getUUID()); continue;
            }
            mob.getNavigation().moveTo(search.clue.x, search.clue.y, search.clue.z, .8);
            mob.getLookControl().setLookAt(search.clue.x, search.clue.y + 1, search.clue.z);
        }
    }

    /** A held Fade covers friends and pets within three blocks; each one shared costs a stamina a second. */
    private static void share(ServerPlayer p, Fade fade) {
        int shared = 0;
        for (LivingEntity t : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(SHARE_REACH),
                e -> e != p && e.isAlive() && e.distanceTo(p) <= SHARE_REACH && ForceFieldService.friend(p, e) && p.hasLineOfSight(e))) {
            if (shared >= MAX_SHARED) break;
            if (t instanceof ServerPlayer friend) {
                if (!ExpansionSkills.friendly(p, friend) || !PowerRules.canAct(friend)) continue;
                shareFade(p, friend, 15);
            } else if (t instanceof TamableAnimal pet && p.getUUID().equals(pet.getOwnerUUID())) hide(pet, 15);
            else continue;
            shared++;
        }
        if (shared != fade.sharing) { fade.sharing = shared; sync(p); }
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }
    @SubscribeEvent public static void target(LivingChangeTargetEvent event) {
        LivingEntity next = event.getNewAboutToBeSetTarget();
        if (next == null || event.getEntity().distanceToSqr(next) <= 4) return;
        if (next instanceof ServerPlayer p && isUnseen(p)) {
            if (event.getEntity() instanceof Mob mob) search(mob, p, FADES.get(p.getUUID()).lastSeen);
        } else if (!isHidden(next)) return;
        else if (event.getEntity() instanceof Mob mob) search(mob, next, next.position());
        // Canceling preserves the previous target. Replace it with null so an existing chase really ends.
        event.setNewAboutToBeSetTarget(null);
    }
    @SubscribeEvent public static void visibility(LivingEvent.LivingVisibilityEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && isInvisibilityOwner(p)) event.modifyVisibility(isUnseen(p) ? .05 : .6);
        else if (isHidden(event.getEntity())) event.modifyVisibility(.05);
    }
    private static void attack(ServerPlayer p) { if (isFaded(p)) { addExposure(p, 55); reveal(p, 20); } }
    @SubscribeEvent(priority = EventPriority.LOW) public static void damage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && event.getSource().getDirectEntity() == attacker
                && event.getSource().is(DamageTypes.PLAYER_ATTACK) && !ForceFieldService.striking()) attack(attacker);
        if (event.getEntity() instanceof ServerPlayer victim && isFaded(victim)) { addExposure(victim, 35); reveal(victim, 20); }
        else if (isHidden(event.getEntity())) unhide(event.getEntity());
    }
    @SubscribeEvent public static void projectile(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Projectile shot && shot.getOwner() instanceof ServerPlayer p) attack(p);
    }
    @SubscribeEvent public static void block(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer p && isFaded(p)) addExposure(p, 6);
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer viewer)) return;
        if (event.getTarget() instanceof ServerPlayer target)
            PacketDistributor.sendToPlayer(viewer, new UnseenPayload(target.getId(), isUnseen(target), false));
        else if (isHidden(event.getTarget()))
            PacketDistributor.sendToPlayer(viewer, new UnseenPayload(event.getTarget().getId(), true, false));
    }
    public static void forget(ServerPlayer p) {
        unfade(p); SQUADS.remove(p.getUUID());
        REVEALED.remove(p.getUUID()); SYNCED.remove(p.getUUID());
        ForceFieldService.forget(p);
        OpticalStatePayload.send(p, false, false, 0, false, false);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) forget(p);
        else HIDDEN.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (Fade fade : List.copyOf(FADES.values())) unfade(fade.player);
        FADES.clear(); REVEALED.clear(); SYNCED.clear(); SQUADS.clear(); SEARCHES.clear(); HIDDEN.clear();
    }
    public static List<ServerPlayer> faded() { return FADES.values().stream().map(f -> f.player).toList(); }
}
