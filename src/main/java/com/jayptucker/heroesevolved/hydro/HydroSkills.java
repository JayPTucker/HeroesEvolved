package com.jayptucker.heroesevolved.hydro;

import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.SimpleAction;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.combat.StyleMotion;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hydrokinesis's skill tree (Tides, Currents, Healing) and its three class abilities. Every node changes a move or ties
 * two together: a Crashing Wave sets off a Geyser it rolls over, a whip cast through your own Tide Wall comes out
 * twice as thick, shots your water swallows can be flung back, a ride's leap lands as a wave.
 */
public final class HydroSkills {
    /** A Geyser marked on the ground, bursting at {@code due}. */
    private static final class Geyser { ServerPlayer owner; ServerLevel level; Vec3 at; long due; int level2; }
    private static final class Jet { ServerPlayer owner; long start, until; int level; }
    /** A wall of water standing on the ground across {@code normal}. */
    private static final class Wall { ServerPlayer owner; ServerLevel level; Vec3 center, normal; long until; }
    private static final class Spout { ServerPlayer owner; long start, until, dropAt = -1; int level; }
    private static final class Heal { ServerPlayer owner; LivingEntity target; long start; float perPulse; boolean soothing; }
    /** A held field: a rain cloud (following its caster), a tidal dome or a healing spring (fixed). */
    private static final class Field { ServerPlayer owner; ServerLevel level; Vec3 center; double radius; long start, until; Piece piece; int level2; }

    private static final List<Geyser> GEYSERS = new ArrayList<>();
    private static final Map<UUID, Jet> JETS = new HashMap<>();
    private static final List<Wall> WALLS = new ArrayList<>();
    private static final Map<UUID, Spout> SPOUTS = new HashMap<>();
    private static final List<Heal> HEALS = new ArrayList<>();
    private static final List<Field> FIELDS = new ArrayList<>();

    private HydroSkills() {}

    private static long now(ServerPlayer player) { return player.getServer().overworld().getGameTime(); }

    public static AbilityAction action(SkillCatalog.Node node) {
        return SimpleAction.builder(node.id(), node.level()).cost(node::energy, node::cooldown)
                .canUse(c -> HydroService.canStartCast(c.player()))
                .followUp(c -> node.id().equals("hydro_water_spout") && drop(c.player()))
                .deactivate(c -> switch (node.id()) {
                    case "hydro_pressure_jet" -> endJet(c.player());
                    case "hydro_water_spout" -> drop(c.player());
                    default -> false;
                })
                .activate(c -> switch (node.id()) {
                    case "hydro_0_1" -> geyser(c);
                    case "hydro_pressure_jet" -> jet(c);
                    case "hydro_1_1" -> wall(c);
                    case "hydro_water_spout" -> spout(c);
                    case "hydro_2_1" -> healingWater(c);
                    case "hydro_rain_call" -> rainCall(c);
                    default -> false;
                }).build();
    }

    // ------------------------------------------------------------------
    // Tides
    // ------------------------------------------------------------------

    /** Marks a spot on the ground; a moment later a column of water bursts up there. */
    static boolean geyser(AbilityUseContext c) {
        ServerPlayer player = c.player();
        Vec3 at = HydroService.aimGround(player);
        if (at == null || at.distanceTo(player.position()) > 22) return false;
        HydroService.Draw draw = HydroService.draw(player, 14);
        Geyser geyser = new Geyser();
        geyser.owner = player;
        geyser.level = player.serverLevel();
        geyser.at = at;
        geyser.due = now(player) + HydroGeometry.GEYSER_DELAY;
        geyser.level2 = Math.max(1, c.abilityLevel());
        GEYSERS.add(geyser);
        HydroGeometry.show(player.serverLevel(), Piece.HYDRO_GEYSER_MARK, at, at, HydroGeometry.GEYSER_RADIUS, draw.thin(), null);
        ArmPosePayload.show(player, ArmPose.WATER_RAISE, 16);
        HydroService.sound(player.serverLevel(), at, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.2F, .8F);
        return true;
    }

    private static void burst(Geyser geyser) {
        ServerPlayer owner = geyser.owner;
        double radius = HydroGeometry.GEYSER_RADIUS;
        for (LivingEntity target : HydroService.enemies(owner, geyser.at.add(0, 1, 0), radius + 1.5)) {
            if (target.position().subtract(geyser.at).horizontalDistance() > radius + target.getBbWidth() * .5
                    || target.getY() < geyser.at.y - 1.5 || target.getY() > geyser.at.y + 3) continue;
            HydroService.hit(owner, target, (6 + geyser.level2) * HydroService.boost(owner));
            HydroService.push(target, new Vec3(0, 1.2, 0).add(target.getDeltaMovement().multiply(.3, 0, .3)));
        }
        HydroGeometry.show(geyser.level, Piece.HYDRO_GEYSER, geyser.at, geyser.at, radius, null);
        HydroService.sound(geyser.level, geyser.at, SoundEvents.GENERIC_SPLASH, 1.5F, .7F);
        HydroService.sound(geyser.level, geyser.at, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.5F, 1.1F);
        CameraShakePayload.sendAround(geyser.level, geyser.at, 12, .2F, 5);
        HydroService.douse(owner, geyser.at, 1);
    }

    /** A Crashing Wave rolling over one of its owner's Geyser marks sets it off at once. */
    static void waveOver(ServerPlayer owner, Vec3 front, Vec3 dir, double halfWidth) {
        long now = now(owner);
        for (Geyser geyser : GEYSERS)
            if (geyser.owner == owner && geyser.due > now
                    && geyser.at.subtract(front).multiply(1, 0, 1).length() <= halfWidth + 1) geyser.due = now;
    }

    /** A thin, high-pressure jet from both palms, held for two seconds; Shift stops it. */
    static boolean jet(AbilityUseContext c) {
        ServerPlayer player = c.player();
        if (JETS.containsKey(player.getUUID())) return false;
        Jet jet = new Jet();
        jet.owner = player;
        jet.start = now(player);
        jet.until = jet.start + 40;
        jet.level = Math.max(1, c.abilityLevel());
        JETS.put(player.getUUID(), jet);
        HydroService.draw(player, 6);
        ArmPosePayload.show(player, ArmPose.WATER_PUSH, 12);
        HydroService.sound(player, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1, 1.5F);
        return true;
    }

    static boolean endJet(ServerPlayer player) {
        if (JETS.remove(player.getUUID()) == null) return false;
        ArmPosePayload.clear(player);
        return true;
    }

    private static void tickJet(ServerPlayer player, Jet jet, long now) {
        if (now >= jet.until || !HydroService.canBend(player)) { endJet(player); return; }
        long age = now - jet.start;
        if (age % 10 == 0) {
            ArmPosePayload.show(player, ArmPose.WATER_PUSH, 12);
            HydroService.draw(player, 4);
        }
        if (age % 2 != 0) return;
        Vec3 eye = player.getEyePosition(), aim = player.getLookAngle().normalize();
        var hit = player.serverLevel().clip(new ClipContext(eye, eye.add(aim.scale(16)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        double length = eye.distanceTo(hit.getLocation());
        LivingEntity target = HydroService.firstInTube(player, eye, aim, length, .5);
        Vec3 end = target != null ? target.getBoundingBox().getCenter() : hit.getLocation();
        if (target != null) {
            HydroService.hit(player, target, (.65F + .1F * jet.level) * HydroService.boost(player));
            HydroService.push(target, aim.scale(.42).add(0, .08, 0).add(target.getDeltaMovement().scale(.3)));
        } else if (age % 6 == 0) HydroService.douse(player, end, 1);
        Vec3 palms = HydroGeometry.hand(player, 1, true).lerp(HydroGeometry.hand(player, 1, false), .5);
        HydroGeometry.show(player.serverLevel(), Piece.HYDRO_JET, palms, end, target != null ? 1 : .7, player);
        if (age % 6 == 0) HydroService.sound(player, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, .7F, 1.8F);
    }

    // ------------------------------------------------------------------
    // Currents
    // ------------------------------------------------------------------

    /** A curved wall of water raised two blocks in front, standing five seconds. */
    static boolean wall(AbilityUseContext c) {
        ServerPlayer player = c.player();
        Vec3 forward = HydroGeometry.level(player.getLookAngle(), player.getYRot());
        Vec3 spot = player.position().add(forward.scale(2.5));
        Vec3 ground = HydroGeometry.ground(player.serverLevel(), spot, 2, 4);
        HydroService.Draw draw = HydroService.draw(player, 16);
        Wall wall = new Wall();
        wall.owner = player;
        wall.level = player.serverLevel();
        wall.center = ground != null ? ground : spot;
        wall.normal = forward;
        wall.until = now(player) + (draw.thin() ? 60 : 100);
        WALLS.add(wall);
        HydroGeometry.show(wall.level, Piece.HYDRO_WALL, wall.center, wall.center.add(forward), 3, draw.thin(), null);
        ArmPosePayload.show(player, ArmPose.WATER_RAISE, 14);
        HydroService.sound(player, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1, .7F);
        return true;
    }

    private static boolean inWall(Wall wall, Vec3 point, double pad) {
        Vec3 rel = point.subtract(wall.center);
        Vec3 right = new Vec3(-wall.normal.z, 0, wall.normal.x);
        return Math.abs(rel.dot(wall.normal)) <= .7 + pad && Math.abs(rel.dot(right)) <= 3 + pad && rel.y >= -.5 && rel.y <= 3.2;
    }

    /** Does a whip cast from {@code eye} along {@code aim} pass through one of its owner's walls? */
    static boolean throughWall(ServerPlayer player, Vec3 eye, Vec3 aim) {
        for (Wall wall : WALLS) {
            if (wall.owner != player) continue;
            double facing = aim.dot(wall.normal);
            if (Math.abs(facing) < .1) continue;
            double t = wall.center.subtract(eye).dot(wall.normal) / facing;
            if (t > 0 && t < HydroGeometry.WHIP_REACH && inWall(wall, eye.add(aim.scale(t)), 0)) return true;
        }
        return false;
    }

    private static boolean tickWall(Wall wall, long now) {
        if (now >= wall.until || wall.owner.isRemoved() || wall.owner.serverLevel() != wall.level) return false;
        if ((wall.until - now) % 10 == 0)
            HydroGeometry.show(wall.level, Piece.HYDRO_WALL, wall.center, wall.center.add(wall.normal), 3, null);
        AABB box = new AABB(wall.center, wall.center).inflate(4, 0, 4).expandTowards(0, 3.5, 0).move(0, -.5, 0);
        for (Projectile shot : wall.level.getEntitiesOfClass(Projectile.class, box)) {
            if (shot.getOwner() == wall.owner || shot.getOwner() instanceof LivingEntity shooter && HydroService.friend(wall.owner, shooter)) continue;
            if (inWall(wall, shot.position(), .4) || inWall(wall, shot.position().add(shot.getDeltaMovement()), .2)) swallow(wall.owner, shot);
        }
        if (now % 10 == 0) for (LivingEntity target : wall.level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != wall.owner && HydroService.hostile(wall.owner, e) && inWall(wall, e.position().add(0, .5, 0), e.getBbWidth() * .5))) {
            HydroService.soak(target, 120);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2));
        }
        return true;
    }

    /** A shot caught by water: swallowed, or with Return Current flung back at whoever fired it. */
    static void swallow(ServerPlayer owner, Projectile shot) {
        HydroGeometry.show(owner.serverLevel(), Piece.HYDRO_SPLASH, shot.position(), shot.position().add(shot.getDeltaMovement()), .6, null);
        HydroService.sound(owner.serverLevel(), shot.position(), SoundEvents.PLAYER_SPLASH, .6F, 1.5F);
        if (SkillTreeService.has(owner, "hydro_return_current") && shot.getOwner() instanceof LivingEntity shooter && shooter.isAlive()) {
            Vec3 back = shooter.getBoundingBox().getCenter().subtract(shot.position());
            double speed = Math.max(1.2, shot.getDeltaMovement().length());
            shot.setOwner(owner);
            StyleMotion.push(shot, back.normalize().scale(speed));
            return;
        }
        shot.discard();
    }

    /** Ride a pillar of water up to eight blocks high for six seconds, steering with the look; press again to drop. */
    static boolean spout(AbilityUseContext c) {
        ServerPlayer player = c.player();
        if (SPOUTS.containsKey(player.getUUID()) || HydroService.riding(player) || player.isPassenger()) return false;
        HydroService.draw(player, 18);
        Spout spout = new Spout();
        spout.owner = player;
        spout.start = now(player);
        spout.until = spout.start + 120;
        spout.level = Math.max(1, c.abilityLevel());
        SPOUTS.put(player.getUUID(), spout);
        HydroGeometry.show(player.serverLevel(), Piece.HYDRO_SPLASH, player.position(), player.position().add(0, 1, 0), 1.8, null);
        ArmPosePayload.show(player, ArmPose.WATER_CALL, 12);
        HydroService.sound(player, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.2F, .7F);
        HydroService.sync(player, true);
        return true;
    }

    static boolean drop(ServerPlayer player) {
        Spout spout = SPOUTS.get(player.getUUID());
        if (spout == null || spout.dropAt >= 0) return false;
        spout.dropAt = now(player);
        StyleMotion.push(player, new Vec3(0, -1.5, 0));
        StyleMotion.cushion(player, 60);
        HydroService.sync(player, true);
        return true;
    }

    static boolean spouting(ServerPlayer player) {
        Spout spout = SPOUTS.get(player.getUUID());
        return spout != null && spout.dropAt < 0;
    }

    private static void tickSpout(ServerPlayer player, Spout spout, long now) {
        if (!HydroService.canBend(player) || player.isPassenger()) { endSpout(player); return; }
        if (spout.dropAt >= 0) {
            long fall = now - spout.dropAt;
            if (fall > 2 && (player.onGround() || player.isInWater()) || fall > 40) {
                Vec3 feet = player.position();
                for (LivingEntity target : HydroService.enemies(player, feet, 3.5)) {
                    HydroService.hit(player, target, (5 + spout.level) * HydroService.boost(player));
                    Vec3 out = target.position().subtract(feet).multiply(1, 0, 1);
                    HydroService.push(target, (out.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : out.normalize()).scale(.8).add(0, .4, 0));
                }
                HydroGeometry.show(player.serverLevel(), Piece.HYDRO_SPLASH, feet, feet.add(0, 1, 0), 3.5, null);
                HydroService.sound(player, SoundEvents.GENERIC_SPLASH, 1.3F, .7F);
                CameraShakePayload.sendAround(player.serverLevel(), feet, 10, .2F, 5);
                endSpout(player);
            }
            return;
        }
        if (now >= spout.until) { drop(player); return; }
        long age = now - spout.start;
        if (age % 10 == 0) ArmPosePayload.show(player, ArmPose.WATER_CALL, 12);
        Vec3 ground = HydroGeometry.ground(player.serverLevel(), player.position(), 0, 12);
        double floor = ground != null ? ground.y : player.getY() - 8;
        double goal = floor + 8 * Math.min(1, age / 20.0);
        double vy = Math.clamp((goal - player.getY()) * .3, -.5, .55);
        Vec3 look = HydroGeometry.level(player.getLookAngle(), player.getYRot());
        StyleMotion.push(player, look.scale(.32).add(0, vy, 0));
        player.resetFallDistance();
        // Anyone at the column's foot is knocked away from it.
        if (age % 5 == 0 && ground != null) for (LivingEntity target : HydroService.enemies(player, ground, 1.8)) {
            HydroService.soak(target, 100);
            Vec3 out = target.position().subtract(ground).multiply(1, 0, 1);
            HydroService.push(target, (out.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : out.normalize()).scale(.6).add(0, .3, 0));
        }
        if (age % 8 == 0) HydroService.sound(player, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, .9F, .8F);
    }

    private static void endSpout(ServerPlayer player) {
        if (SPOUTS.remove(player.getUUID()) == null) return;
        ArmPosePayload.clear(player);
        HydroService.sync(player, true);
    }

    // ------------------------------------------------------------------
    // Healing
    // ------------------------------------------------------------------

    /** Water glows in the hands and wraps a friend in sight (or you): healing over three seconds, one harm washed away. */
    static boolean healingWater(AbilityUseContext c) {
        ServerPlayer player = c.player();
        LivingEntity target = Targeting.livingEntityInSight(player, 6, e -> e != player && HydroService.friend(player, e));
        if (target == null) target = player;
        HydroService.Draw draw = HydroService.draw(player, 12);
        Heal heal = new Heal();
        heal.owner = player;
        heal.target = target;
        heal.start = now(player);
        heal.perPulse = (6 + Math.max(1, c.abilityLevel())) * (draw.thin() ? .7F : 1) / 12F;
        heal.soothing = SkillTreeService.has(player, "hydro_soothing_tide");
        HEALS.add(heal);
        cleanse(target);
        HydroGeometry.show(player.serverLevel(), Piece.HYDRO_HEAL, HydroGeometry.hand(player, 1, true), target.position(), 1, target);
        ArmPosePayload.show(player, ArmPose.WATER_HEAL, 20);
        HydroService.sound(player, SoundEvents.CONDUIT_AMBIENT_SHORT, 1, 1.4F);
        return true;
    }

    private static void cleanse(LivingEntity target) {
        for (MobEffectInstance effect : List.copyOf(target.getActiveEffects()))
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                target.removeEffect(effect.getEffect());
                return;
            }
    }

    private static boolean tickHeal(Heal heal, long now) {
        long age = now - heal.start;
        if (age > 60 || !heal.target.isAlive() || heal.owner.isRemoved()) return false;
        if (age % 5 == 0) {
            heal.target.heal(heal.perPulse);
            if (heal.soothing) for (LivingEntity friend : heal.target.level().getEntitiesOfClass(LivingEntity.class,
                    heal.target.getBoundingBox().inflate(4), e -> e != heal.target && HydroService.friend(heal.owner, e) || e == heal.owner && e != heal.target))
                friend.heal(heal.perPulse * .5F);
        }
        if (age == 20 || age == 40)
            HydroGeometry.show(heal.owner.serverLevel(), Piece.HYDRO_HEAL, heal.target.position(), heal.target.position(), 1, heal.target);
        return true;
    }

    /** A rain cloud over you for twenty seconds: friends heal, enemies are soaked, fires go out, your water fills. */
    static boolean rainCall(AbilityUseContext c) {
        ServerPlayer player = c.player();
        FIELDS.removeIf(f -> f.owner == player && f.piece == Piece.HYDRO_RAIN);
        field(player, Piece.HYDRO_RAIN, player.position(), 10, 400, Math.max(1, c.abilityLevel()));
        ArmPosePayload.show(player, ArmPose.WATER_RAISE, 20);
        HydroService.sound(player, SoundEvents.WEATHER_RAIN_ABOVE, 1.4F, .9F);
        return true;
    }

    private static Field field(ServerPlayer player, Piece piece, Vec3 center, double radius, int ticks, int level) {
        Field field = new Field();
        field.owner = player;
        field.level = player.serverLevel();
        field.center = center;
        field.radius = radius;
        field.start = now(player);
        field.until = field.start + ticks;
        field.piece = piece;
        field.level2 = level;
        FIELDS.add(field);
        HydroGeometry.show(field.level, piece, center, center, radius, piece == Piece.HYDRO_RAIN ? player : null);
        return field;
    }

    private static boolean tickField(Field field, long now) {
        if (now >= field.until || field.owner.isRemoved() || field.owner.serverLevel() != field.level) return false;
        long age = now - field.start;
        if (field.piece == Piece.HYDRO_RAIN) field.center = field.owner.position();
        if (age % 10 == 0 && age > 0)
            HydroGeometry.show(field.level, field.piece, field.center, field.center, field.radius,
                    field.piece == Piece.HYDRO_RAIN ? field.owner : null);
        AABB box = new AABB(field.center, field.center).inflate(field.radius, 3, field.radius).expandTowards(0, 3, 0);
        switch (field.piece) {
            case HYDRO_RAIN -> {
                if (age % 20 != 0) return true;
                HydroService.fill(field.owner, 3);
                for (LivingEntity e : field.level.getEntitiesOfClass(LivingEntity.class, box,
                        e -> e.position().subtract(field.center).horizontalDistance() <= field.radius)) {
                    if (e == field.owner || HydroService.friend(field.owner, e)) { e.heal(1); if (e.isOnFire()) e.clearFire(); }
                    else if (HydroService.hostile(field.owner, e)) HydroService.soak(e, 60);
                }
                double angle = age * .7;
                HydroService.douse(field.owner, field.center.add(Math.cos(angle) * field.radius * .6, .5, Math.sin(angle) * field.radius * .6), 1);
            }
            default -> { }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Tick and cleanup
    // ------------------------------------------------------------------

    static void tick(MinecraftServer server, long now) {
        for (Geyser geyser : List.copyOf(GEYSERS)) {
            if (geyser.owner.isRemoved() || geyser.owner.serverLevel() != geyser.level) { GEYSERS.remove(geyser); continue; }
            if (now >= geyser.due) { GEYSERS.remove(geyser); burst(geyser); }
        }
        for (Jet jet : List.copyOf(JETS.values())) {
            if (jet.owner.isRemoved() && !(jet.owner instanceof net.neoforged.neoforge.common.util.FakePlayer)) { JETS.remove(jet.owner.getUUID()); continue; }
            tickJet(jet.owner, jet, now);
        }
        for (Spout spout : List.copyOf(SPOUTS.values())) {
            if (spout.owner.isRemoved() && !(spout.owner instanceof net.neoforged.neoforge.common.util.FakePlayer)) { SPOUTS.remove(spout.owner.getUUID()); continue; }
            tickSpout(spout.owner, spout, now);
        }
        WALLS.removeIf(wall -> !tickWall(wall, now));
        HEALS.removeIf(heal -> !tickHeal(heal, now));
        FIELDS.removeIf(field -> !tickField(field, now));
    }

    public static boolean jetting(ServerPlayer player) { return JETS.containsKey(player.getUUID()); }

    public static int walls(ServerPlayer player) { return (int) WALLS.stream().filter(w -> w.owner == player).count(); }

    public static int geysers(ServerPlayer player) { return (int) GEYSERS.stream().filter(g -> g.owner == player).count(); }

    public static int fields(ServerPlayer player, Piece piece) {
        return (int) FIELDS.stream().filter(f -> f.owner == player && f.piece == piece).count();
    }

    static void forget(ServerPlayer player) {
        UUID id = player.getUUID();
        GEYSERS.removeIf(g -> g.owner == player);
        JETS.remove(id);
        WALLS.removeIf(w -> w.owner == player);
        SPOUTS.remove(id);
        HEALS.removeIf(h -> h.owner == player);
        FIELDS.removeIf(f -> f.owner == player);
    }

    static void clear() {
        GEYSERS.clear();
        JETS.clear();
        WALLS.clear();
        SPOUTS.clear();
        HEALS.clear();
        FIELDS.clear();
    }
}
