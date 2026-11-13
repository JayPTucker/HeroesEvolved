package com.jayptucker.heroesevolved.quake;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quake's skill tree, built around the kit rather than stat bonuses. Three branches:
 * <ul>
 *   <li><b>Faultwright</b> (the ground): Live Fault, Seismic Lance, Deep Roots, Fault Script → Short Fuse → Undertow.</li>
 *   <li><b>Counterforce</b> (taking hits apart): Pressure Skin, Vibration Shell, Kinetic Sink, Counterforce →
 *   Counter Balance → Stolen Thunder.</li>
 *   <li><b>Seamfinder</b> (precision): Sympathetic Frequency, Pressure Step, Read the Pulse, Surgical Fracture → Wider
 *   Incision → Master Incision.</li>
 * </ul>
 * The passive nodes are read where the kit runs ({@link QuakeKit}, {@link QuakeService}, {@link QuakeKinetics}); this
 * class holds the three branch actives and the passives that need their own hooks. Node ids are kept from the older
 * tree, so purchases and prerequisites carry over.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class QuakeSkills {
    public static final String LIVE_FAULT = "quake_0_0", DEEP_ROOTS = "quake_0_2", PRESSURE_SKIN = "quake_1_0",
            KINETIC_SINK = QuakeKinetics.KINETIC_SINK, SYMPATHETIC = "quake_2_0", READ_PULSE = "quake_2_2";

    private record Lance(ServerPlayer owner, LivingEntity target, long at, int tier) {}
    private record Shell(ServerPlayer owner, long start, long until) {}
    private record Pulse(UUID owner, long until) {}

    private static final List<Lance> LANCES = new ArrayList<>();
    private static final Map<UUID, Shell> SHELLS = new HashMap<>();
    /** Heartbeats she has felt (Read the Pulse): the next quake on them lands harder. */
    private static final Map<UUID, Pulse> FELT = new HashMap<>();

    private QuakeSkills() {}

    private static long now(ServerPlayer p) { return p.serverLevel().getGameTime(); }

    private static LivingEntity lanceTarget(ServerPlayer p) {
        return Targeting.livingEntityInSight(p, QuakeGeometry.LANCE_RANGE, t -> ExpansionSkills.hostile(p, t));
    }

    // ------------------------------------------------------------------
    // The three branch actives
    // ------------------------------------------------------------------

    public static boolean canUse(AbilityUseContext c, int branch) {
        if (QuakeKit.fractured(c.player()) || c.player().isPassenger()) return false;
        return branch != 0 || lanceTarget(c.player()) != null;
    }

    public static boolean skill(AbilityUseContext c, int branch) {
        ServerPlayer p = c.player();
        boolean done = switch (branch) {
            case 0 -> seismicLance(p, c.abilityLevel());
            case 1 -> vibrationShell(p);
            default -> pressureStep(p, c.abilityLevel());
        };
        if (done) MasteryService.markPowerUse(p, ModAbilities.QUAKE_ID);
        return done;
    }

    /**
     * Seismic Lance: an uppercut, and a moment later a column of earth bursts up under one enemy within 24 blocks: 7 +
     * level, thrown straight up. An enemy already in the air (thrown by any quake) is smashed back down instead, for
     * double.
     */
    static boolean seismicLance(ServerPlayer p, int tier) {
        LivingEntity target = lanceTarget(p);
        if (target == null) return false;
        LANCES.add(new Lance(p, target, now(p) + QuakeGeometry.LANCE_DELAY, tier));
        ArmPosePayload.show(p, ArmPose.QUAKE_UPPERCUT, 12);
        Vec3 feet = target.position();
        // A warning ring at its feet, so the column is fair.
        AbilityEffects.send(p.serverLevel(), Kind.QUAKE_PULSE, feet, feet.add(0, 0.5, 0), 1.2);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.6F, 1.7F);
        QuakeKit.strain(p, 10);
        return true;
    }

    private static void strikeLance(Lance l) {
        LivingEntity t = l.target;
        ServerPlayer p = l.owner;
        if (!t.isAlive() || t.level() != p.level()) return;
        ServerLevel level = p.serverLevel();
        boolean airborne = !t.onGround() && !t.isInWater();
        float damage = (7 + l.tier) * QuakeKit.power(p) * (airborne ? 2 : 1);
        t.invulnerableTime = 0;
        if (t.hurt(p.damageSources().playerAttack(p), damage)) QuakeKit.landed(p, t);
        QuakeService.setVelocity(t, airborne ? new Vec3(0, -2.2, 0) : new Vec3(0, 1.15, 0));
        BlockPos ground = QuakeService.findSurface(level, t.getBlockX(), t.getBlockZ(), t.getBlockY(), 6);
        Vec3 base = ground == null ? t.position() : Vec3.atBottomCenterOf(ground.above());
        QuakeGeometry.show(level, Piece.QUAKE_LANCE, base, base.add(0, airborne ? -1 : 3.2, 0), airborne ? 2 : 1, null);
        if (ground != null) QuakeService.burstDebris(level, ground, new Vec3(0, 1, 0), 6, 0.7);
        level.playSound(null, t.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7F, 0.7F);
        level.playSound(null, t.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 0.5F);
        CameraShakePayload.sendAround(level, base, 12, 0.8F, 8);
    }

    /**
     * Vibration Shell: arms crossed, then flung out, and the air round her shakes into a shell 3.2 blocks across for four
     * seconds. Bullets that reach it shake apart, explosives go off outside it, other shots turn back, and enemies who
     * push into it are thrown out.
     */
    static boolean vibrationShell(ServerPlayer p) {
        long now = now(p);
        SHELLS.put(p.getUUID(), new Shell(p, now, now + QuakeGeometry.SHELL_TICKS));
        QuakeGeometry.show(p.serverLevel(), Piece.QUAKE_SHELL, p.position(), p.position(), QuakeGeometry.SHELL_RADIUS, p);
        ArmPosePayload.show(p, ArmPose.QUAKE_SHELL, QuakeGeometry.SHELL_TICKS);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.8F, 1.3F);
        QuakeKit.strain(p, 10);
        return true;
    }

    public static boolean shelled(ServerPlayer p) { return SHELLS.containsKey(p.getUUID()); }

    /**
     * Pressure Step: palms flung back at the air behind her and she shoots eight blocks along her aim; a ring goes off
     * where she left, shoving back anyone there. The landing is cushioned.
     */
    static boolean pressureStep(ServerPlayer p, int tier) {
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) flat = new Vec3(0, 0, 1);
        flat = flat.normalize();
        Vec3 start = p.position();
        ServerLevel level = p.serverLevel();
        for (LivingEntity t : ExpansionSkills.enemies(p, start, 3)) {
            Vec3 away = t.position().subtract(start).multiply(1, 0, 1);
            if (away.lengthSqr() < 1.0E-4) away = flat.reverse();
            t.invulnerableTime = 0;
            if (t.hurt(p.damageSources().playerAttack(p), (4 + tier * 0.5F) * QuakeKit.power(p))) QuakeKit.landed(p, t);
            QuakeService.setVelocity(t, away.normalize().scale(0.9).add(0, 0.3, 0));
        }
        QuakeService.setVelocity(p, flat.scale(QuakeGeometry.DASH_SPEED).add(0, p.onGround() ? 0.35 : 0.2, 0));
        p.resetFallDistance();
        QuakeService.cushion(p);
        QuakeGeometry.show(level, Piece.QUAKE_DASH, start, start.add(flat.scale(8)), 1, p);
        AbilityEffects.send(level, Kind.QUAKE_IMPACT, start, start, 2.5);
        ArmPosePayload.show(p, ArmPose.QUAKE_DASH, 12);
        level.playSound(null, start.x, start.y, start.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.5F, 1.7F);
        QuakeKit.strain(p, 8);
        return true;
    }

    // ------------------------------------------------------------------
    // Passive hooks
    // ------------------------------------------------------------------

    /** Read the Pulse: a heartbeat she has felt is marked, and her next quake on it lands harder. */
    static void felt(ServerPlayer p, LivingEntity t) {
        if (SkillTreeService.has(p, READ_PULSE) && ExpansionSkills.hostile(p, t))
            FELT.put(t.getUUID(), new Pulse(p.getUUID(), now(p) + QuakeGeometry.PULSE_TICKS));
    }

    public static boolean marked(LivingEntity t) { return FELT.containsKey(t.getUUID()); }

    private static boolean quake(ServerPlayer p) {
        return PlayerAbilityService.getData(p).assignedPower()
                .filter(e -> e.getValue().isUnlocked() && e.getKey().equals(ModAbilities.QUAKE_ID)).isPresent();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void damage(LivingIncomingDamageEvent e) {
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        // Pressure Skin: a shot that reaches her while she is braced, blasting or shelled shakes apart on her skin.
        if (e.getEntity() instanceof ServerPlayer p && e.getSource().getDirectEntity() instanceof Projectile shot
                && SkillTreeService.has(p, PRESSURE_SKIN) && PowerRules.canAct(p) && quake(p)
                && (ExpansionSkills.braced(p) || QuakeService.isBlasting(p) || shelled(p))) {
            e.setCanceled(true);
            Vec3 at = shot.position();
            shot.discard();
            QuakeGeometry.show(p.serverLevel(), Piece.QUAKE_DUST, at, at.add(shot.getDeltaMovement().normalize()), 0.8, null);
            p.serverLevel().playSound(null, at.x, at.y, at.z, SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 0.7F, 1.7F);
            QuakeKit.strain(p, 4);
            return;
        }
        // Read the Pulse: her next quake on a heart she felt lands harder.
        Pulse pulse = FELT.get(e.getEntity().getUUID());
        if (pulse != null && e.getSource().getEntity() instanceof ServerPlayer p && p.getUUID().equals(pulse.owner)) {
            FELT.remove(e.getEntity().getUUID());
            e.setAmount(e.getAmount() * QuakeGeometry.PULSE_BONUS);
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        long now = e.getServer().overworld().getGameTime();
        for (Lance l : List.copyOf(LANCES)) {
            if (l.owner.isRemoved() || !PowerRules.canAct(l.owner)) { LANCES.remove(l); continue; }
            if (l.owner.serverLevel().getGameTime() < l.at) continue;
            LANCES.remove(l);
            strikeLance(l);
        }
        for (Shell s : List.copyOf(SHELLS.values())) tickShell(s);
        FELT.values().removeIf(p -> p.until < now);
    }

    private static void tickShell(Shell s) {
        ServerPlayer p = s.owner;
        long now = now(p);
        if (now >= s.until || p.isRemoved() || !PowerRules.canAct(p) || !quake(p)) { SHELLS.remove(p.getUUID()); return; }
        ServerLevel level = p.serverLevel();
        Vec3 center = p.getBoundingBox().getCenter();
        double r = QuakeGeometry.SHELL_RADIUS;
        // Shots are checked every tick and a little beyond the shell, since bullets cross a lot of ground in one.
        for (Entity thing : level.getEntities(p, new AABB(center, center).inflate(r + 4), x -> x.isAlive()
                && (x instanceof Projectile || QuakeKinetics.explosive(x)))) {
            Vec3 next = thing.position().add(thing.getDeltaMovement());
            if (thing.position().distanceTo(center) > r + 0.6 && next.distanceTo(center) > r + 0.6) continue;
            Vec3 out = thing.position().subtract(center);
            QuakeKinetics.disrupt(p, thing, out.lengthSqr() < 1.0E-4 ? p.getLookAngle() : out.normalize());
        }
        if ((now - s.start) % 5 == 0) for (LivingEntity t : ExpansionSkills.enemies(p, center, r + 0.4)) {
            Vec3 out = t.position().subtract(p.position()).multiply(1, 0, 1);
            QuakeService.setVelocity(t, (out.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : out.normalize()).scale(0.8).add(0, 0.25, 0));
        }
        if ((now - s.start) % 10 == 0 && now > s.start)
            QuakeGeometry.show(level, Piece.QUAKE_SHELL, p.position(), p.position(), r, p);
    }

    public static void forget(ServerPlayer p) {
        SHELLS.remove(p.getUUID());
        LANCES.removeIf(l -> l.owner == p);
        FELT.values().removeIf(pulse -> pulse.owner.equals(p.getUUID()));
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) { LANCES.clear(); SHELLS.clear(); FELT.clear(); }
}
