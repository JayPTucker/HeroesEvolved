package com.jayptucker.heroesevolved.expansion;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.service.*;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.effects.*;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Foresight observes present attack intent and the incident director's planned future. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class PrecognitionService {
    private record Timed(ServerPlayer owner, long until) {}
    private record Convergence(ServerPlayer owner, Vec3 point, long until) {}
    private static final Map<UUID, Timed> SENSE = new HashMap<>(), INTENTS = new HashMap<>(), FATE = new HashMap<>(), PAINT = new HashMap<>();
    private static final Map<UUID, Convergence> CONVERGENCES = new HashMap<>();
    private static final net.minecraft.resources.ResourceLocation SPARED_COOLDOWN = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "precog_spared");
    private static final Map<UUID, Long> SPARED = new HashMap<>();
    private PrecognitionService() {}
    private static long now(ServerPlayer p) { return p.getServer().overworld().getGameTime(); }
    private static boolean valid(ServerPlayer p) {
        return PowerRules.canAct(p) && PlayerAbilityService.getData(p).assignedPower()
                .filter(e -> e.getValue().isUnlocked() && e.getKey().getPath().equals("precognition")).isPresent();
    }
    public static void awaken(ServerPlayer p) {
        var id = com.jayptucker.heroesevolved.ability.registry.ModAbilities.PRECOGNITION_ID;
        var history = p.getData(ModDataAttachments.POWER_INTRO_HISTORY); if (history.seen().contains(id)) return;
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY, history.with(id));
        p.sendSystemMessage(Component.literal("Your hands move before you decide to move them. Two silhouettes. A copper heart. A clock without a next second."));
        paint(p); SENSE.put(p.getUUID(), new Timed(p, now(p) + 160));
    }
    public static boolean paint(ServerPlayer p) {
        if (PAINT.containsKey(p.getUUID())) return false;
        PAINT.put(p.getUUID(), new Timed(p, now(p) + 60)); FacilityService.plan(p);
        p.displayClientMessage(Component.literal("The Unfinished Hour: hold the image for three seconds. Its outcome is still unwritten."), true);
        return true;
    }
    public static boolean activate(AbilityUseContext c, int slot) {
        ServerPlayer p = c.player();
        switch (slot) {
            case 0 -> SENSE.put(p.getUUID(), new Timed(p, now(p) + 160));
            case 1 -> { return paint(p); }
            case 2 -> {
                var t = Targeting.livingEntityInSight(p, 20, e -> ExpansionSkills.hostile(p, e)); if (t == null) return false;
                INTENTS.put(t.getUUID(), new Timed(p, now(p) + 100)); PrivateGlow.glow(p, t, 100);
                AbilityEffects.send(p.serverLevel(), Kind.PROPHECY_THREAD, p.getEyePosition(), t.getEyePosition(), 1);
            }
            case 3 -> {
                LivingEntity t = p.isShiftKeyDown() ? p : Targeting.livingEntityInSight(p, 6, e -> e instanceof ServerPlayer && ExpansionSkills.friendly(p, e));
                if (t == null) t = p;
                if (FATE.containsKey(t.getUUID()) || SPARED.getOrDefault(t.getUUID(), 0L) > now(p) || t instanceof ServerPlayer other && com.jayptucker.heroesevolved.cooldown.CooldownService.isOnCooldown(other, SPARED_COOLDOWN)) return false;
                FATE.put(t.getUUID(), new Timed(p, now(p) + 120));
                AbilityEffects.send(p.serverLevel(), Kind.PROPHECY_THREAD, p.getEyePosition(), t.getEyePosition(), 2);
                p.sendSystemMessage(Component.literal("Fate Thread: one lethal outcome can be averted for six seconds. Shift targets yourself. The rescued person still needs help."));
            }
            case 4 -> {
                Vec3 point = Vec3.atCenterOf(ExpansionSkills.aim(p).above());
                CONVERGENCES.put(p.getUUID(), new Convergence(p, point, now(p) + 120));
                AbilityEffects.send(p.serverLevel(), Kind.PROPHECY_THREAD, p.getEyePosition(), point, 2);
            }
            default -> { return false; }
        }
        return true;
    }
    public static boolean skill(AbilityUseContext c, int branch) {
        ServerPlayer p = c.player();
        if (branch == 0) {
            FacilityService.plan(p); p.sendSystemMessage(Component.literal("Object Reading: " + FacilityService.journal(p)));
            for (var t : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, p.getBoundingBox().inflate(32))) PrivateGlow.glow(p, t, 100);
        } else if (branch == 1) {
            var targets = ExpansionSkills.enemies(p, p.position(), 12).stream().limit(3).toList(); if (targets.isEmpty()) return false;
            for (var t : targets) { INTENTS.put(t.getUUID(), new Timed(p, now(p) + 80)); PrivateGlow.glow(p, t, 80); }
        } else {
            SENSE.put(p.getUUID(), new Timed(p, now(p) + 120));
            for (ServerPlayer ally : p.serverLevel().players()) if (p != ally && p.isAlliedTo(ally) && p.distanceToSqr(ally) < 144)
                SENSE.put(ally.getUUID(), new Timed(p, now(p) + 120));
        }
        return true;
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void hurt(LivingIncomingDamageEvent e) {
        if (e.getSource().getEntity() instanceof LivingEntity attacker) {
            Timed intent = INTENTS.get(attacker.getUUID());
            if (intent != null && now(intent.owner) < intent.until && valid(intent.owner)
                    && ExpansionSkills.friendly(intent.owner, e.getEntity()) && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                INTENTS.remove(attacker.getUUID()); e.setCanceled(true);
                AbilityEffects.send(intent.owner.serverLevel(), Kind.PROPHECY_BREAK, attacker.position(), e.getEntity().position(), 2);
            }
        }
    }
    @SubscribeEvent(priority = EventPriority.LOW) public static void death(LivingDeathEvent e) {
        Timed fate = FATE.remove(e.getEntity().getUUID());
        boolean selfDeath = fate != null && fate.owner == e.getEntity();
        boolean eligible = fate != null && (selfDeath
                ? !com.jayptucker.heroesevolved.events.EclipseService.arePowersSuppressed(fate.owner)
                    && PlayerAbilityService.getData(fate.owner).assignedPower().filter(a -> a.getValue().isUnlocked() && a.getKey().getPath().equals("precognition")).isPresent()
                : valid(fate.owner));
        if (fate == null || !eligible || now(fate.owner) >= fate.until || e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || e.getSource().is(com.jayptucker.heroesevolved.time.TemporalParadoxService.TEMPORAL_PARADOX_DAMAGE)) return;
        SPARED.put(e.getEntity().getUUID(), now(fate.owner) + 600);
        if (e.getEntity() instanceof ServerPlayer rescued) com.jayptucker.heroesevolved.cooldown.CooldownService.startCooldown(rescued, SPARED_COOLDOWN, 600);
        e.setCanceled(true); e.getEntity().setHealth(2); e.getEntity().invulnerableTime = 40;
        AbilityEffects.send(fate.owner.serverLevel(), Kind.PROPHECY_BREAK, e.getEntity().position(), e.getEntity().getEyePosition(), 3);
        fate.owner.sendSystemMessage(Component.literal("That ending is crossed out. Get them somewhere safe."));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e) { tick(e.getServer()); }
    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (var entry : List.copyOf(PAINT.entrySet())) {
            Timed t = entry.getValue();
            if (!valid(t.owner)) { PAINT.remove(entry.getKey()); continue; }
            if (now >= t.until) { PAINT.remove(entry.getKey()); PropheticPainting.give(t.owner); FacilityService.discovery(t.owner, "canvas_received"); }
            else if (now % 10 == 0) AbilityEffects.at(t.owner, Kind.PROPHECY_BREAK, 1);
        }
        SENSE.entrySet().removeIf(e -> now >= e.getValue().until || !valid(e.getValue().owner) || server.getPlayerList().getPlayer(e.getKey()) == null && !e.getKey().equals(e.getValue().owner.getUUID()));
        INTENTS.values().removeIf(t -> now >= t.until || !valid(t.owner)); FATE.values().removeIf(t -> now >= t.until || !valid(t.owner));
        SPARED.values().removeIf(until -> until <= now);
        if (now % 10 == 0) for (var entry : SENSE.entrySet()) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(entry.getKey()); if (viewer == null) viewer = entry.getValue().owner;
            if (viewer.level() != entry.getValue().owner.level()) continue;
            final ServerPlayer observed = viewer;
            int count = 0;
            for (var mob : viewer.serverLevel().getEntitiesOfClass(Mob.class, viewer.getBoundingBox().inflate(24), m -> m.getTarget() == observed)) {
                if (++count > 8) break; PrivateGlow.glow(viewer, mob, 12);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer, new com.jayptucker.heroesevolved.network.AbilityEffectPayload(Kind.PROPHECY_THREAD, mob.getEyePosition(), mob.getEyePosition().add(mob.getLookAngle().scale(3)), .5F));
            }
            viewer.displayClientMessage(Component.literal(count == 0 ? "Premonition: no committed threat nearby." : "Premonition: " + count + " minds have chosen you."), true);
        }
        CONVERGENCES.values().removeIf(f -> now >= f.until || !valid(f.owner));
        if (now % 4 != 0) return;
        for (var f : CONVERGENCES.values()) {
            AbilityEffects.send(f.owner.serverLevel(), Kind.PROPHECY_BREAK, f.point, f.point.add(0, 1, 0), 2);
            int count = 0;
            for (Projectile shot : f.owner.serverLevel().getEntitiesOfClass(Projectile.class, new AABB(f.point, f.point).inflate(8))) {
                if (shot.getOwner() == f.owner || shot.getOwner() != null && f.owner.isAlliedTo(shot.getOwner()) || !f.owner.hasLineOfSight(shot)) continue;
                if (++count > 16) break;
                if (shot.position().distanceToSqr(f.point) < 1) { shot.discard(); continue; }
                shot.setDeltaMovement(f.point.subtract(shot.position()).normalize().scale(Math.max(.5, Math.min(2, shot.getDeltaMovement().length()))));
                shot.hurtMarked = true;
                f.owner.serverLevel().getChunkSource().broadcast(shot, new ClientboundSetEntityMotionPacket(shot));
                AbilityEffects.send(f.owner.serverLevel(), Kind.PROPHECY_THREAD, shot.position(), f.point, .4);
            }
        }
    }
    public static void forget(ServerPlayer p) {
        SENSE.entrySet().removeIf(e -> e.getKey().equals(p.getUUID()) || e.getValue().owner == p);
        PAINT.remove(p.getUUID()); CONVERGENCES.remove(p.getUUID()); INTENTS.values().removeIf(t -> t.owner == p); FATE.entrySet().removeIf(e -> e.getKey().equals(p.getUUID()) || e.getValue().owner == p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent e) { SENSE.clear(); PAINT.clear(); INTENTS.clear(); FATE.clear(); SPARED.clear(); CONVERGENCES.clear(); }
}
