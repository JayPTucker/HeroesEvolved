package com.jayptucker.heroesevolved.invisibility;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.OpticalStatePayload;
import com.jayptucker.heroesevolved.network.UnseenPayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.speed.SpeedService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
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

/** Light-bending stealth. The server owns concealment, Exposure, physical movement and search clues. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class InvisibilityService {
    private static final class Fade {
        final ServerPlayer player;
        final ServerLevel level;
        final long began;
        final MobEffectInstance priorPotion;
        long until;
        boolean drains, wet;
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
    private record Cloak(ServerPlayer owner, ServerLevel level, Vec3 start, Vec3 end, double radius, long until, boolean moving) {}
    private record Dash(ServerPlayer owner, ServerLevel level, Vec3 direction, double speed, long until) {}
    private record Search(Mob mob, ServerLevel level, Vec3 clue, long until) {}
    private static final Map<UUID, Fade> FADES = new HashMap<>();
    private static final Map<UUID, Long> REVEALED = new HashMap<>(), STRIKES = new HashMap<>(), GHOSTS = new HashMap<>();
    private static final Map<UUID, Cloak> CLOAKS = new HashMap<>();
    private static final Map<UUID, Dash> DASHES = new HashMap<>();
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
    public static int exposure(ServerPlayer p) {
        Fade fade = FADES.get(p.getUUID());
        return fade == null ? 0 : (int) Math.round(fade.exposure);
    }
    public static boolean veilActive(ServerPlayer p) { return CLOAKS.containsKey(p.getUUID()); }
    public static boolean ghostActive(ServerPlayer p) { return GHOSTS.getOrDefault(p.getUUID(), 0L) > now(p.getServer()); }
    public static boolean activeDash(ServerPlayer p) { return DASHES.containsKey(p.getUUID()); }

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
    public static boolean unfade(ServerPlayer p) {
        Fade state = FADES.remove(p.getUUID());
        if (state == null) return false;
        GHOSTS.remove(p.getUUID());
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
    }
    private static void sync(ServerPlayer p) {
        boolean hidden = isUnseen(p);
        if (!hidden && isFaded(p)) p.removeEffect(MobEffects.INVISIBILITY);
        if (SYNCED.getOrDefault(p.getUUID(), false) != hidden) {
            SYNCED.put(p.getUUID(), hidden);
            if (hidden) p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false));
            else p.removeEffect(MobEffects.INVISIBILITY);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new UnseenPayload(p.getId(), hidden));
        }
        OpticalStatePayload.send(p, isFaded(p), hidden, exposure(p), veilActive(p), ghostActive(p));
    }
    private static void search(Mob mob, ServerPlayer p, Vec3 clue) {
        if (mob.level() != p.level() || OpticalDecoyService.isInvestigating(mob)) return;
        mob.setTarget(null);
        if (mob.getLastHurtByMob() == p) mob.setLastHurtByMob(null);
        SEARCHES.put(mob.getUUID(), new Search(mob, p.serverLevel(), clue, now(p.getServer()) + 60));
        mob.getNavigation().moveTo(clue.x, clue.y, clue.z, .9);
        mob.getLookControl().setLookAt(clue.x, clue.y + 1, clue.z);
    }
    private static void loseTargets(ServerPlayer p, double radius, Vec3 clue) {
        for (Mob mob : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(radius), m -> m.getTarget() == p)
                .stream().limit(24).toList()) search(mob, p, clue);
    }

    public static boolean toggleFade(ServerPlayer p) {
        if (unfade(p)) return true;
        if (!PowerRules.canAct(p)) return false;
        fade(p, 0, true);
        p.displayClientMessage(Component.translatable("invisibility.heroesevolved.faded").withStyle(ChatFormatting.GRAY), true);
        return true;
    }
    /** Physical refraction dash: ordinary collision solves movement, with no position teleport. */
    public static boolean silentStep(ServerPlayer p, int level) {
        if (!PowerRules.canAct(p) || p.isPassenger() || activeDash(p)) return false;
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < .001) look = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 direction = look.normalize();
        Vec3 end = SpeedService.swept(p, direction.scale(6 + Math.clamp(level, 1, 5) * 1.2));
        double distance = end.distanceTo(p.position());
        if (distance < .65) return false;
        DASHES.put(p.getUUID(), new Dash(p, p.serverLevel(), direction, Math.min(2, distance / 6), now(p.getServer()) + 6));
        OpticalDecoyService.spawnEcho(p, direction.reverse(), 30);
        AbilityEffects.send(p.serverLevel(), Kind.OPTIC_STEP, p.position(), end, 1);
        addExposure(p, -12);
        return true;
    }
    public static boolean armStrike(ServerPlayer p) {
        STRIKES.put(p.getUUID(), now(p.getServer()) + 200);
        p.displayClientMessage(Component.translatable("invisibility.heroesevolved.strike_ready"), true);
        return true;
    }
    public static boolean mirage(ServerPlayer p, int level) {
        if (!OpticalDecoyService.spawnMirage(p, level)) return false;
        ArmPosePayload.show(p, ArmPose.OPTICAL_SPLIT, 18);
        return true;
    }
    public static boolean endVeil(ServerPlayer p) { return CLOAKS.remove(p.getUUID()) != null; }
    public static boolean shroud(ServerPlayer p) {
        double radius = 4 + (SkillTreeService.has(p, "invisibility_2_0") ? 1 : 0);
        CLOAKS.put(p.getUUID(), new Cloak(p, p.serverLevel(), p.position(), p.position(), radius, now(p.getServer()) + 240, true));
        fade(p, 30, false);
        ArmPosePayload.show(p, ArmPose.OPTICAL_VEIL, 24);
        veilEffect(CLOAKS.get(p.getUUID()), p.position(), p.position());
        return true;
    }
    /** The formation boundary is guidance for its participants, not a beacon for opponents. */
    private static void veilEffect(Cloak cloak, Vec3 start, Vec3 end) {
        for (ServerPlayer viewer : cloak.level.players()) {
            if (viewer != cloak.owner && !ExpansionSkills.friendly(cloak.owner, viewer)) continue;
            if (viewer.position().distanceToSqr(start) > 64 * 64) continue;
            PacketDistributor.sendToPlayer(viewer, new com.jayptucker.heroesevolved.network.AbilityEffectPayload(
                    Kind.OPTIC_VEIL, start, end, (float) cloak.radius));
        }
    }
    public static boolean ghostProtocol(ServerPlayer p) {
        GHOSTS.put(p.getUUID(), now(p.getServer()) + 160);
        fade(p, 160, false);
        addExposure(p, -50);
        OpticalDecoyService.burst(p, 3);
        ArmPosePayload.show(p, ArmPose.OPTICAL_DISPERSE, 20);
        AbilityEffects.send(p.serverLevel(), Kind.OPTIC_BURST, p.position(), p.getEyePosition(), 6);
        return true;
    }
    public static void awaken(ServerPlayer p) {
        fade(p, 100, false);
        p.displayClientMessage(Component.translatable("invisibility.heroesevolved.awakened").withStyle(ChatFormatting.GRAY), false);
    }

    public static boolean skill(AbilityUseContext c, int branch) {
        ServerPlayer p = c.player();
        switch (branch) {
            case 0 -> { fade(p, 60, false); addExposure(p, -35); armStrike(p); }
            case 1 -> {
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0));
                fade(p, 120, false); addExposure(p, -20); OpticalDecoyService.burst(p, 1);
            }
            case 2 -> {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1)); fade(p, 100, false);
                if (SkillTreeService.has(p, "invisibility_2_2"))
                    for (ServerPlayer friend : p.serverLevel().players())
                        if (friend != p && friend.distanceToSqr(p) <= 16 && ExpansionSkills.friendly(p, friend)) fade(friend, 100, false);
            }
            default -> { return false; }
        }
        return true;
    }
    public static boolean technique(AbilityUseContext c, String mechanic) {
        ServerPlayer p = c.player();
        switch (mechanic) {
            case "smoke_and_mirrors" -> {
                var hit = p.serverLevel().clip(new ClipContext(p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(18)),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                Vec3 clue = hit.getLocation();
                boolean longClue = SkillTreeService.has(p, "invisibility_lingering_mirage");
                long until = now(p.getServer()) + (longClue ? 120 : 60);
                for (Mob mob : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(longClue ? 18 : 12),
                        m -> ExpansionSkills.hostile(p, m) && (m.getTarget() == null || m.getTarget() == p && isUnseen(p))).stream().limit(16).toList()) {
                    mob.setTarget(null);
                    SEARCHES.put(mob.getUUID(), new Search(mob, p.serverLevel(), clue, until));
                }
                p.serverLevel().playSound(null, clue.x, clue.y, clue.z, SoundEvents.STONE_STEP, SoundSource.PLAYERS, 1.1F, .9F);
                AbilityEffects.send(p.serverLevel(), Kind.OPTIC_FOOTSTEP, clue, clue.add(0, 1, 0), 1);
                return true;
            }
            case "pickpocket" -> {
                double reach = SkillTreeService.has(p, "invisibility_light_fingers") ? 4 : 2;
                LivingEntity target = Targeting.livingEntityInSight(p, reach, t -> t instanceof Mob && !t.getMainHandItem().isEmpty());
                if (!(target instanceof Mob mob)) {
                    p.displayClientMessage(Component.translatable("invisibility.heroesevolved.nothing_to_take"), true); return false;
                }
                ItemStack taken = mob.getMainHandItem().copy();
                mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                if (!p.getInventory().add(taken)) p.drop(taken, false);
                AbilityEffects.send(p.serverLevel(), Kind.OPTIC_THREAD, mob.getEyePosition(), p.getEyePosition(), 1);
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .6F, 1.6F);
                return true;
            }
            case "cloak_field" -> {
                Vec3 start = p.position(), flat = p.getLookAngle().multiply(1, 0, 1).normalize();
                if (flat.lengthSqr() < .001) flat = Vec3.directionFromRotation(0, p.getYRot());
                Vec3 end = p.serverLevel().clip(new ClipContext(start.add(0, 1, 0), start.add(flat.scale(12)).add(0, 1, 0),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getLocation().add(0, -1, 0);
                if (end.distanceToSqr(start) < 1) return false;
                CLOAKS.put(p.getUUID(), new Cloak(p, p.serverLevel(), start, end,
                        SkillTreeService.has(p, "invisibility_wider_cloak") ? 3 : 2, now(p.getServer()) + 300, false));
                AbilityEffects.send(p.serverLevel(), Kind.OPTIC_THREAD, start.add(0, .3, 0), end.add(0, .3, 0), 2);
                return true;
            }
            default -> { return false; }
        }
    }

    public static void tick(MinecraftServer server) {
        long time = now(server);
        for (Dash dash : List.copyOf(DASHES.values())) {
            ServerPlayer p = dash.owner;
            if (time >= dash.until || p.level() != dash.level || !PowerRules.canAct(p) || p.isPassenger()) {
                DASHES.remove(p.getUUID()); p.setDeltaMovement(p.getDeltaMovement().scale(.35)); continue;
            }
            Vec3 safe = SpeedService.swept(p, dash.direction.scale(dash.speed)).subtract(p.position());
            if (safe.lengthSqr() < .04) { DASHES.remove(p.getUUID()); continue; }
            p.setDeltaMovement(safe); p.resetFallDistance(); p.hurtMarked = true;
            p.connection.send(new ClientboundSetEntityMotionPacket(p));
        }
        CLOAKS.values().removeIf(c -> time >= c.until || !PowerRules.canAct(c.owner) || c.owner.level() != c.level || c.owner.isRemoved());
        if (time % 10 == 0) for (Cloak cloak : List.copyOf(CLOAKS.values())) {
            Vec3 start = cloak.moving ? cloak.owner.position() : cloak.start;
            Vec3 end = cloak.moving ? start : cloak.end;
            int shared = 0;
            for (ServerPlayer friend : cloak.level.players()) {
                if (!ExpansionSkills.friendly(cloak.owner, friend) || !PowerRules.canAct(friend)) continue;
                Vec3 segment = end.subtract(start);
                double along = segment.lengthSqr() < .001 ? 0 : Math.clamp(friend.position().subtract(start).dot(segment) / segment.lengthSqr(), 0, 1);
                if (friend.position().distanceToSqr(start.add(segment.scale(along))) > cloak.radius * cloak.radius
                        || !cloak.owner.hasLineOfSight(friend)) continue;
                if (friend != cloak.owner && ++shared > 4) break;
                fade(friend, 15, false);
            }
            if (cloak.moving && time % 20 == 0 && !PlayerEnergyService.tryConsume(cloak.owner, Math.max(1, shared))) { endVeil(cloak.owner); continue; }
            veilEffect(cloak, start, end);
        }
        for (Fade fade : List.copyOf(FADES.values())) {
            ServerPlayer p = fade.player;
            if (p.isRemoved() || p.level() != fade.level || !PowerRules.canAct(p) || time >= fade.until
                    || fade.drains && !isInvisibilityOwner(p)
                    && !com.jayptucker.heroesevolved.mimicry.MimicryService.isBorrowingInvisibility(p)) { unfade(p); continue; }
            if (fade.drains && time % (SkillTreeService.has(p, "invisibility_claudes_lesson") ? 20 : 10) == 0) {
                if (!PlayerEnergyService.tryConsume(p, 1)) { unfade(p); continue; }
                MasteryService.awardEnergySpent(p, 1);
            }
            double distance = p.position().distanceTo(fade.previous);
            fade.previous = p.position();
            int level = Math.clamp(PlayerProgressionService.getLevel(p), 1, 5);
            boolean moving = distance > .02 && distance < 4;
            double amount = moving ? p.isSprinting() ? .55 + .12 * (6 - level)
                    : p.isShiftKeyDown() ? -.15 : .08 * (6 - level) : -.65;
            if (amount > 0 && moving && !p.isSprinting() && SkillTreeService.has(p, "invisibility_0_0")) amount *= .5;
            if (amount < 0 && SkillTreeService.has(p, "invisibility_0_2")
                    && p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16),
                    m -> ExpansionSkills.hostile(p, m) && m.hasLineOfSight(p)).isEmpty()) amount = -1.1;
            if (ghostActive(p) && amount > 0) amount *= .2;
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
        STRIKES.values().removeIf(until -> until <= time);
        GHOSTS.values().removeIf(until -> until <= time);
        if (time % 20 == 0) for (UUID id : List.copyOf(GHOSTS.keySet())) {
            Fade fade = FADES.get(id);
            if (fade != null) OpticalDecoyService.spawnEcho(fade.player, fade.player.getLookAngle(), 40);
        }
        for (Search search : List.copyOf(SEARCHES.values())) {
            Mob mob = search.mob;
            if (!mob.isAlive() || mob.isRemoved() || mob.level() != search.level || time >= search.until || mob.getTarget() != null) {
                SEARCHES.remove(mob.getUUID()); continue;
            }
            if (OpticalDecoyService.isInvestigating(mob)) continue;
            mob.getNavigation().moveTo(search.clue.x, search.clue.y, search.clue.z, .8);
            mob.getLookControl().setLookAt(search.clue.x, search.clue.y + 1, search.clue.z);
        }
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }
    @SubscribeEvent public static void target(LivingChangeTargetEvent event) {
        if (!(event.getNewAboutToBeSetTarget() instanceof ServerPlayer p) || !isUnseen(p) || event.getEntity().distanceToSqr(p) <= 4) return;
        if (event.getEntity() instanceof Mob mob) search(mob, p, FADES.get(p.getUUID()).lastSeen);
        // Canceling preserves the previous target. Replace it with null so an existing chase really ends.
        event.setNewAboutToBeSetTarget(null);
    }
    @SubscribeEvent public static void visibility(LivingEvent.LivingVisibilityEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && isInvisibilityOwner(p)) event.modifyVisibility(isUnseen(p) ? .05 : .6);
    }
    private static void attack(ServerPlayer p) { if (isFaded(p)) { addExposure(p, 55); reveal(p, 20); } }
    @SubscribeEvent(priority = EventPriority.LOW) public static void damage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && event.getSource().getDirectEntity() == attacker
                && event.getSource().is(DamageTypes.PLAYER_ATTACK)) {
            if (STRIKES.remove(attacker.getUUID()) != null) {
                float multiplier = isUnseen(attacker) ? SkillTreeService.has(attacker, "invisibility_assassin") ? 3 : 1.5F : 1;
                event.setAmount((event.getAmount() + 3 + PlayerProgressionService.getLevel(attacker)) * multiplier);
            }
            attack(attacker);
        }
        if (event.getEntity() instanceof ServerPlayer victim && isFaded(victim)) {
            if (SkillTreeService.has(victim, "invisibility_phantom") && isUnseen(victim)) event.setAmount(event.getAmount() * .7F);
            addExposure(victim, 35); reveal(victim, 20);
        }
    }
    @SubscribeEvent public static void projectile(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Projectile shot && shot.getOwner() instanceof ServerPlayer p) attack(p);
    }
    @SubscribeEvent public static void block(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer p && isFaded(p) && !SkillTreeService.has(p, "invisibility_pickpocket")) addExposure(p, 6);
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer)
            PacketDistributor.sendToPlayer(viewer, new UnseenPayload(target.getId(), isUnseen(target), false));
    }
    public static void forget(ServerPlayer p) {
        unfade(p); STRIKES.remove(p.getUUID()); GHOSTS.remove(p.getUUID()); CLOAKS.remove(p.getUUID()); DASHES.remove(p.getUUID());
        REVEALED.remove(p.getUUID()); SYNCED.remove(p.getUUID()); OpticalDecoyService.clear(p);
        OpticalStatePayload.send(p, false, false, 0, false, false);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void death(LivingDeathEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (Fade fade : List.copyOf(FADES.values())) unfade(fade.player);
        FADES.clear(); REVEALED.clear(); STRIKES.clear(); CLOAKS.clear(); SYNCED.clear(); GHOSTS.clear(); DASHES.clear(); SEARCHES.clear();
    }
    public static List<ServerPlayer> faded() { return FADES.values().stream().map(f -> f.player).toList(); }
}
