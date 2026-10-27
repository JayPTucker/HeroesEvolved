package com.jayptucker.heroesevolved.skills;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.effects.*;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.radiation.RadiationService;
import com.jayptucker.heroesevolved.sonic.SonicService;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Optional skills for every registered power. Searches have a fixed target cap; no skill scans or destroys terrain. */
public final class SkillActionService {
    private record Field(ServerPlayer owner, Vec3 center, String power, long until) {}
    private static final Map<UUID, Field> FIELDS = new HashMap<>();
    private SkillActionService() {}
    private static boolean hostile(ServerPlayer source, LivingEntity target) {
        return target != source && target.isAlive() && !target.isSpectator() && !source.isAlliedTo(target)
                && (!(target instanceof ServerPlayer other) || source.getServer().isPvpAllowed() && source.canHarmPlayer(other));
    }
    private static List<LivingEntity> nearby(ServerPlayer player, double radius) {
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                target -> hostile(player, target) && target.distanceToSqr(player) <= radius * radius && player.hasLineOfSight(target))
                .stream().limit(64).toList();
    }
    private static LivingEntity aimed(ServerPlayer player, boolean healing) {
        return Targeting.livingEntityInSight(player, 20, target -> healing
                ? target != player && target.getHealth() < target.getMaxHealth() && !target.isInvertedHealAndHarm()
                : hostile(player, target));
    }
    public static boolean canUse(AbilityUseContext c, int branch) {
        if (c.abilityId().getPath().equals("superspeed")) return com.jayptucker.heroesevolved.speed.SpeedService.canUseSkill(c, branch);
        if (c.abilityId().getPath().equals("time_manipulation")) return com.jayptucker.heroesevolved.time.SpaceTimeService.canUse(c, branch);
        if (c.abilityId().getPath().equals("precognition")) return true;
        if (branch == 0 && !c.abilityId().getPath().equals("flight") && !c.abilityId().getPath().equals("regeneration"))
            return aimed(c.player(), false) != null;
        if (branch == 2 && c.abilityId().getPath().equals("regeneration"))
            return c.player().getHealth() > 4 && aimed(c.player(), true) != null;
        return true;
    }
    public static boolean activate(AbilityUseContext c, int branch) {
        ServerPlayer p = c.player(); String power = c.abilityId().getPath();
        LivingEntity target = branch == 0 ? aimed(p, false) : null;
        int level = c.abilityLevel();
        switch (power) {
            case "quake" -> {
                if (branch == 0) { hit(p, target, 7 + level); push(target, p.getLookAngle().scale(1.5).add(0, .15, 0)); }
                if (branch == 1) for (var t : nearby(p, 8)) { hit(p, t, 4); effect(t, MobEffects.MOVEMENT_SLOWDOWN, 100, 2); }
                if (branch == 2) {
                    push(p, p.getLookAngle().reverse().scale(1.2).add(0, .4, 0));
                    for (var t : nearby(p, 4)) { hit(p, t, 3); push(t, t.position().subtract(p.position()).normalize().scale(.9)); }
                }
                visual(p, target, Kind.QUAKE_PULSE, branch == 1 ? 8 : 3);
            }
            case "flight" -> {
                if (branch == 0) { push(p, p.getLookAngle().scale(1.7)); effect(p, MobEffects.SLOW_FALLING, 100, 0); }
                if (branch == 1) { push(p, Vec3.ZERO); reflect(p, p.position(), 7); field(p, power, 80); }
                if (branch == 2) {
                    push(p, p.getDeltaMovement().add(0, 1.2, 0)); effect(p, MobEffects.SLOW_FALLING, 160, 0);
                    for (var t : nearby(p, 6)) push(t, t.getDeltaMovement().add(0, .85, 0));
                }
                visual(p, null, Kind.FLIGHT_BURST, 4);
            }
            case "cryokinesis" -> {
                if (branch == 0) { hit(p, target, 6 + level); CryoService.freezeSolid(target, 80, p); }
                if (branch == 1) { effect(p, MobEffects.DAMAGE_RESISTANCE, 160, 1); effect(p, MobEffects.FIRE_RESISTANCE, 160, 0); }
                if (branch == 2) for (var t : nearby(p, 7)) { hit(p, t, 2); CryoService.freezeSolid(t, 60, p); }
                visual(p, target, branch == 0 ? Kind.CRYO_BOLT : Kind.CRYO_FREEZE, 5);
            }
            case "electrokinesis" -> {
                if (branch == 0) {
                    com.jayptucker.heroesevolved.expansion.PowerReactions.electric(p, target);
                    hit(p, target, 8 + level); effect(target, MobEffects.MOVEMENT_SLOWDOWN, 50, 3);
                    LivingEntity primary = target;
                    for (var t : nearby(p, 40).stream().filter(t -> t != primary && t.distanceToSqr(primary) < 36).limit(2).toList()) {
                        hit(p, t, 4); AbilityEffects.send(p.serverLevel(), Kind.ELECTRIC_ARC, target.getEyePosition(), t.getEyePosition(), .8);
                    }
                }
                if (branch == 1) { reflect(p, p.position(), 7); field(p, power, 120); }
                if (branch == 2) { effect(p, MobEffects.MOVEMENT_SPEED, 160, 1); effect(p, MobEffects.DIG_SPEED, 160, 1); }
                visual(p, target, Kind.ELECTRIC_ARC, 3);
            }
            case "telekinesis" -> {
                if (branch == 0) { hit(p, target, 6 + level); push(target, p.getLookAngle().scale(2)); }
                if (branch == 1) field(p, power, 100);
                if (branch == 2) { effect(p, MobEffects.LEVITATION, 35, 2); effect(p, MobEffects.SLOW_FALLING, 180, 0); }
                visual(p, target, Kind.PSYCHIC_FIELD, 5);
            }
            case "telepathy" -> {
                if (branch == 0) { effect(target, MobEffects.WEAKNESS, 120, 1); effect(target, MobEffects.MOVEMENT_SLOWDOWN, 100, 3); }
                if (branch == 1) for (var ally : allies(p, 10)) { effect(ally, MobEffects.NIGHT_VISION, 240, 0); effect(ally, MobEffects.DAMAGE_RESISTANCE, 120, 0); }
                if (branch == 2) for (var t : nearby(p, 8)) {
                    effect(t, MobEffects.BLINDNESS, 80, 0); effect(t, MobEffects.WEAKNESS, 100, 1);
                    if (t instanceof Mob mob) { mob.setTarget(null); mob.getNavigation().stop(); }
                    push(t, t.position().subtract(p.position()).normalize().scale(.8));
                }
                visual(p, target, Kind.MIND_PULSE, 6);
            }
            case "regeneration" -> {
                if (branch == 0) { cleanse(p); p.heal(6); }
                if (branch == 1) { p.heal(4); effect(p, MobEffects.MOVEMENT_SPEED, 160, 1); effect(p, MobEffects.DIG_SPEED, 160, 0); }
                if (branch == 2) {
                    LivingEntity recipient = aimed(p, true); if (recipient == null || p.getHealth() <= 4) return false;
                    p.hurt(p.damageSources().magic(), 3); recipient.heal(10); effect(recipient, MobEffects.ABSORPTION, 200, 0); target = recipient;
                }
                visual(p, target, Kind.REGENERATION, 3);
            }
            case "precognition" -> { return com.jayptucker.heroesevolved.expansion.PrecognitionService.skill(c, branch); }
            case "superspeed" -> { return com.jayptucker.heroesevolved.speed.SpeedService.skill(c, branch); }
            case "time_manipulation" -> {
                return com.jayptucker.heroesevolved.time.SpaceTimeService.activate(c, branch);
            }
            case "radiation" -> {
                if (branch == 0) { hit(p, target, 9 + level); RadiationService.irradiate(target, 120); }
                if (branch == 1) effect(p, MobEffects.DAMAGE_RESISTANCE, 160, 1);
                if (branch == 2) for (var ally : allies(p, 8)) { cleanse(ally); effect(ally, MobEffects.FIRE_RESISTANCE, 160, 0); }
                visual(p, target, branch == 0 ? Kind.NUCLEAR_RAY : Kind.NUCLEAR_EMP, 4);
            }
            case "sonic_resonance" -> {
                if (branch == 0) { hit(p, target, 9 + level); push(target, p.getLookAngle().scale(1.1)); SonicService.voice(p, false); }
                if (branch == 1) {
                    for (var t : nearby(p, 8)) { effect(t, MobEffects.WEAKNESS, 120, 1); effect(t, MobEffects.MOVEMENT_SLOWDOWN, 80, 2); hit(p, t, 3); }
                    SonicService.voice(p, false);
                }
                if (branch == 2) { effect(p, MobEffects.DAMAGE_RESISTANCE, 100, 0); reflect(p, p.position(), 7); field(p, power, 100); }
                visual(p, target, branch == 0 ? Kind.SONIC_WAVE : Kind.SONIC_BURST, 6);
            }
            default -> { return false; }
        }
        com.jayptucker.heroesevolved.network.ArmPosePayload.show(p,
                branch == 0 ? ArmPose.ONE_HAND : branch == 1 ? ArmPose.ARMS_WIDE : ArmPose.ARMS_RAISED, 16);
        return true;
    }
    private static void hit(ServerPlayer p, LivingEntity target, float damage) {
        if (target != null) target.hurt(p.damageSources().playerAttack(p), damage);
    }
    private static void effect(LivingEntity target, Holder<MobEffect> effect, int ticks, int amplifier) {
        if (target != null) target.addEffect(new MobEffectInstance(effect, ticks, amplifier));
    }
    private static List<ServerPlayer> allies(ServerPlayer p, int radius) {
        // Never grant buffs to enemies during PvP; unteamed visitors only buff themselves.
        var targets = new ArrayList<>(p.serverLevel().players().stream().filter(t -> t == p || p.isAlliedTo(t))
                .filter(t -> t.distanceToSqr(p) <= radius * radius).limit(32).toList());
        if (!targets.contains(p)) targets.addFirst(p);
        return targets;
    }
    private static void cleanse(LivingEntity entity) {
        for (var e : List.copyOf(entity.getActiveEffects())) if (!e.getEffect().value().isBeneficial()) entity.removeEffect(e.getEffect());
        entity.clearFire();
    }
    private static void push(Entity target, Vec3 velocity) {
        target.setDeltaMovement(velocity); target.hurtMarked = true;
        if (target instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }
    private static void visual(ServerPlayer p, LivingEntity target, Kind kind, double scale) {
        AbilityEffects.send(p.serverLevel(), kind, p.getEyePosition(), target == null ? p.getEyePosition().add(p.getLookAngle()) : target.getEyePosition(), scale);
    }
    private static void field(ServerPlayer p, String power, int ticks) { FIELDS.put(p.getUUID(), new Field(p, p.position(), power, p.getServer().overworld().getGameTime() + ticks)); }
    private static void reflect(ServerPlayer owner, Vec3 center, int radius) {
        int count = 0;
        for (Projectile shot : owner.serverLevel().getEntitiesOfClass(Projectile.class, owner.getBoundingBox().inflate(radius),
                shot -> shot.getOwner() != owner && shot.position().distanceToSqr(center) <= radius * radius)) {
            if (++count > 64) break;
            Vec3 away = shot.position().subtract(center).normalize();
            if (shot.getDeltaMovement().dot(away) >= 0) continue;
            shot.setOwner(owner); push(shot, away.scale(Math.max(.8, shot.getDeltaMovement().length())));
        }
    }
    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        FIELDS.values().removeIf(f -> now >= f.until || !PowerRules.canAct(f.owner) || f.owner.isRemoved()
                || f.owner.position().distanceToSqr(f.center) > 16 * 16);
        if (now % 5 != 0) return;
        for (Field f : FIELDS.values()) {
            if (f.power.equals("telekinesis")) {
                for (var t : nearby(f.owner, 10)) push(t, f.center.subtract(t.position()).normalize().scale(.35).add(0, .04, 0));
                if (now % 10 == 0) AbilityEffects.send(f.owner.serverLevel(), Kind.PSYCHIC_FIELD, f.center, f.center.add(0, 1, 0), 5);
            } else reflect(f.owner, f.center, 7);
        }
    }
    public static void forget(ServerPlayer player) { FIELDS.remove(player.getUUID()); }
    public static void clear() { FIELDS.clear(); }
}
