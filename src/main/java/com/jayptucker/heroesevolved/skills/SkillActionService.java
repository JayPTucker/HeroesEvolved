package com.jayptucker.heroesevolved.skills;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.electro.ElectroGeometry;
import com.jayptucker.heroesevolved.electro.ElectroService;
import com.jayptucker.heroesevolved.effects.*;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.radiation.RadiationService;
import com.jayptucker.heroesevolved.sonic.SonicService;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.styles.Piece;
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
        if (c.abilityId().getPath().equals("electrokinesis")) return ElectroService.canStartCast(c.player())
                && (branch != 0 || arcLanceTarget(c.player()) != null);
        if (c.abilityId().getPath().equals("technopathy")) return com.jayptucker.heroesevolved.technopathy.TechnopathyService.canUse(c);
        if (c.abilityId().getPath().equals("matter_assembly")) return com.jayptucker.heroesevolved.assembly.MatterAssemblyService.canUse(c, "matter_assembly_" + branch + "_1");
        if (c.abilityId().getPath().equals("cryokinesis")) return com.jayptucker.heroesevolved.cryo.CryoCombatSkills.canUse(c, branch);
        if (c.abilityId().getPath().equals("telekinesis")) return com.jayptucker.heroesevolved.telekinesis.TelekinesisSkillService.canUse(c.player(), "telekinesis_" + branch + "_1");
        if (c.abilityId().getPath().equals("flight")) return com.jayptucker.heroesevolved.flight.FlightSkillService.canUse(c.player(), "flight_" + branch + "_1");
        if (c.abilityId().getPath().equals("superspeed")) return com.jayptucker.heroesevolved.speed.SpeedService.canUseSkill(c, branch);
        if (c.abilityId().getPath().equals("time_manipulation")) return com.jayptucker.heroesevolved.time.SpaceTimeService.canUse(c, branch);
        if (c.abilityId().getPath().equals("radiation")) return com.jayptucker.heroesevolved.radiation.RadiationSkills.canUse(c, branch);
        if (c.abilityId().getPath().equals("invisibility")) return com.jayptucker.heroesevolved.invisibility.InvisibilitySkills.canUse(c, branch);
        if (c.abilityId().getPath().equals("sonic_resonance")) return com.jayptucker.heroesevolved.sonic.SonicSkills.canUse(c, branch);
        if (c.abilityId().getPath().equals("quake")) return com.jayptucker.heroesevolved.quake.QuakeSkills.canUse(c, branch);
        if (c.abilityId().getPath().equals("regeneration")) return com.jayptucker.heroesevolved.regeneration.RegenSkills.canUse(c, branch);
        if (c.abilityId().getPath().equals("telepathy")) return com.jayptucker.heroesevolved.telepathy.TelepathySkills.canUse(c, branch);
        if (c.abilityId().getPath().equals("phasing")) return com.jayptucker.heroesevolved.phasing.DensitySkills.canUse(c, branch);
        if (c.abilityId().getPath().equals("precognition")) return true;
        if (branch == 0 && !c.abilityId().getPath().equals("flight"))
            return aimed(c.player(), false) != null;
        return true;
    }
    public static boolean activate(AbilityUseContext c, int branch) {
        ServerPlayer p = c.player(); String power = c.abilityId().getPath();
        LivingEntity target = branch == 0 ? power.equals("electrokinesis") ? arcLanceTarget(p) : aimed(p, false) : null;
        int level = c.abilityLevel();
        switch (power) {
            case "technopathy" -> { return com.jayptucker.heroesevolved.technopathy.TechnopathyService.skill(c, branch); }
            case "matter_assembly" -> { return com.jayptucker.heroesevolved.assembly.MatterAssemblyService.activate(c, "matter_assembly_" + branch + "_1"); }
            case "quake" -> { return com.jayptucker.heroesevolved.quake.QuakeSkills.skill(c, branch); }
            case "flight" -> { return com.jayptucker.heroesevolved.flight.FlightSkillService.activate(p, "flight_" + branch + "_1"); }
            case "cryokinesis" -> { return com.jayptucker.heroesevolved.cryo.CryoKitService.skill(c, branch); }
            case "electrokinesis" -> {
                if (!ElectroService.canStartCast(p)) return false;
                if (branch == 0) {
                    if (target == null) return false;
                    // One straight, braced shot, not a fork: it breaks into ordinary lightning only after it lands.
                    ArmPosePayload.show(p, ArmPose.ELECTRO_LANCE, 14);
                    var levelAtCast = p.serverLevel();
                    LivingEntity primary = target;
                    Vec3 contact = primary.getBoundingBox().getCenter();
                    ElectroGeometry.show(levelAtCast, Piece.ELECTRO_LANCE, ElectroGeometry.hand(p, 1, .72), contact, 1, p);
                    p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, .5F, 1.9F);
                    com.jayptucker.heroesevolved.expansion.PowerReactions.electric(p, target);
                    hit(p, target, 8 + level); effect(target, MobEffects.MOVEMENT_SLOWDOWN, 50, 3);
                    int hop = 0;
                    for (var t : levelAtCast.getEntitiesOfClass(LivingEntity.class, primary.getBoundingBox().inflate(6),
                            t -> t != primary && ElectroService.hostile(p, t) && t.distanceToSqr(primary) <= 36 && primary.hasLineOfSight(t))
                            .stream().sorted(Comparator.comparingDouble(primary::distanceToSqr)).limit(2).toList()) {
                        hit(p, t, 4);
                        ElectroGeometry.show(levelAtCast, Piece.ELECTRO_ARC, contact, t.getBoundingBox().getCenter(), .8, null, 3 + hop++);
                    }
                }
                if (branch == 1) {
                    reflect(p, p.position(), 7); field(p, power, 120);
                    ElectroGeometry.show(p.serverLevel(), Piece.ELECTRO_SCREEN, p.position(), p.position(), 7, null);
                    ArmPosePayload.show(p, ArmPose.ELECTRO_RELEASE, 12);
                }
                if (branch == 2) {
                    effect(p, MobEffects.MOVEMENT_SPEED, 160, 1); effect(p, MobEffects.DIG_SPEED, 160, 1);
                    ElectroService.fillCharge(p);
                    // Current floods up the body from the feet; fists clench, then flex out as it flares off the chest.
                    ElectroGeometry.show(p.serverLevel(), Piece.ELECTRO_OVERCHARGE, p.position(), p.position(), 1, p);
                    ArmPosePayload.show(p, ArmPose.ELECTRO_OVERCHARGE, 18);
                    p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, .8F, 1.7F);
                }
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.COPPER_BULB_TURN_ON, SoundSource.PLAYERS, 1, 1.35F);
            }
            case "telekinesis" -> {
                return com.jayptucker.heroesevolved.telekinesis.TelekinesisSkillService.activate(p, "telekinesis_" + branch + "_1", c.modifierHeld());
            }
            // Telepathy poses each skill itself (Drop It, Far Hearing, Dream Double); a generic pose would replace it.
            case "telepathy" -> { return com.jayptucker.heroesevolved.telepathy.TelepathySkills.skill(c, branch); }
            case "regeneration" -> { return com.jayptucker.heroesevolved.regeneration.RegenSkills.skill(c, branch); }
            case "precognition" -> { return com.jayptucker.heroesevolved.precog.PrecognitionService.skill(c, branch); }
            case "superspeed" -> { return com.jayptucker.heroesevolved.speed.SpeedService.skill(c, branch); }
            case "pyrokinesis" -> { return com.jayptucker.heroesevolved.pyro.PyroService.skill(c, branch); }
            case "invisibility" -> { return com.jayptucker.heroesevolved.invisibility.InvisibilitySkills.skill(c, branch); }
            // Density poses each skill itself (lunge, brace, fade); a generic pose would replace it.
            case "phasing" -> { return com.jayptucker.heroesevolved.phasing.DensitySkills.skill(c, branch); }
            case "empathic_mimicry" -> { return com.jayptucker.heroesevolved.mimicry.MimicryService.skill(c, branch); }
            case "time_manipulation" -> {
                return com.jayptucker.heroesevolved.time.SpaceTimeService.activate(c, branch);
            }
            case "radiation" -> {
                return com.jayptucker.heroesevolved.radiation.RadiationSkills.skill(c, branch);
            }
            case "sonic_resonance" -> { return com.jayptucker.heroesevolved.sonic.SonicSkills.skill(c, branch); }
            default -> { return false; }
        }
        // Electrokinesis poses each skill itself (lance, release, overcharge); a generic pose would replace it.
        if (power.equals("electrokinesis")) return true;
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
    private static void push(Entity target, Vec3 velocity) {
        target.setDeltaMovement(velocity); target.hurtMarked = true;
        if (target instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }
    private static void visual(ServerPlayer p, LivingEntity target, Kind kind, double scale) {
        AbilityEffects.send(p.serverLevel(), kind, p.getEyePosition(), target == null ? p.getEyePosition().add(p.getLookAngle()) : target.getEyePosition(), scale);
    }
    private static LivingEntity arcLanceTarget(ServerPlayer player) {
        return Targeting.livingEntityInSight(player, 40, target -> ElectroService.hostile(player, target));
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
            reflect(f.owner, f.center, 7);
        }
    }
    public static void forget(ServerPlayer player) {
        FIELDS.remove(player.getUUID());
        com.jayptucker.heroesevolved.telekinesis.TelekinesisSkillService.forget(player);
        com.jayptucker.heroesevolved.telekinesis.TelekineticArsenalService.forget(player);
    }
    public static void clear() {
        FIELDS.clear();
        com.jayptucker.heroesevolved.telekinesis.TelekinesisSkillService.clear();
        com.jayptucker.heroesevolved.telekinesis.TelekineticArsenalService.clear();
    }
}
