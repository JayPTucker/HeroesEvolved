package com.jayptucker.heroesevolved.intro;

import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import com.jayptucker.heroesevolved.mixin.CreeperSwellAccess;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Who an awakening has to save and from what. The endangered one is the <em>ward</em>: the owner
 * themself, or an ally nearby (a friendly player or the owner's own pet) who is about to lose a fight,
 * about to be caught in a blast, about to hit the ground too hard, burning, or drowning.
 *
 * <p>Each power only answers the dangers it can do something about ({@link #answers}): a speedster runs
 * people out of fights and blasts, but can't catch a fall; a cryokinetic freezes lava and cushions a fall
 * in powder snow; Space-Time answers every one of them by stopping time.
 */
public final class Danger {
    /** What someone is in danger from. The order is the order they are checked in. */
    public enum Peril {
        /** In a fight and about to lose it (the original rule). */
        FIGHT,
        /** A creeper or primed TNT that is about to go off and would take them below the line. */
        BLAST,
        /** Falling, and the landing would take them below the line. */
        FALL,
        /** In lava, or burning and nearly gone. */
        FIRE,
        /** Out of air and nearly gone. */
        DROWNING,
        /** Below two hearts and still being hurt by anything at all. Only Space-Time answers it. */
        LOW
    }

    private static final Set<String> CATCHES_FALLS = Set.of("flight", "telekinesis", "cryokinesis", "matter_assembly",
            "hydrokinesis", "regeneration", "time_manipulation");
    private static final Set<String> ANSWERS_FIRE = Set.of("cryokinesis", "hydrokinesis", "pyrokinesis", "regeneration",
            "time_manipulation");
    private static final Set<String> ANSWERS_DROWNING = Set.of("hydrokinesis", "regeneration", "time_manipulation");

    /** How far down a fall is looked at, and how soon the landing must be (ticks) to count as about to happen. */
    private static final int FALL_SCAN = 48, FALL_SOON = 12;
    /** Creepers count once their fuse is this far along; primed TNT once this many ticks are left. */
    private static final float CREEPER_SOON = 0.4F;
    private static final int TNT_SOON = 40;

    private Danger() {}

    /** How far an ally can be for each power to answer: runners and flyers come from further away. */
    public static double reach(String power) {
        return switch (power) {
            case "superspeed" -> 48;
            case "flight" -> 64;
            case "time_manipulation" -> 20;
            default -> 24;
        };
    }

    /** Whether this power can do anything about this danger. Every power answers a fight and a blast. */
    public static boolean answers(String power, Peril peril) {
        return switch (peril) {
            case FIGHT, BLAST -> true;
            case FALL -> CATCHES_FALLS.contains(power);
            case FIRE -> ANSWERS_FIRE.contains(power);
            case DROWNING -> ANSWERS_DROWNING.contains(power);
            case LOW -> "time_manipulation".equals(power);
        };
    }

    /** What kind of danger a blow is, or null for one nothing should answer (the void, /kill, starving to death). */
    public static Peril of(DamageSource source, LivingEntity victim) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return null;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return Peril.BLAST;
        if (source.is(DamageTypeTags.IS_FALL)) return Peril.FALL;
        if (source.is(DamageTypeTags.IS_FIRE)) return Peril.FIRE;
        if (source.is(DamageTypeTags.IS_DROWNING)) return Peril.DROWNING;
        if (source.getEntity() instanceof LivingEntity attacker && attacker != victim) return Peril.FIGHT;
        return victim instanceof Player ? Peril.LOW : null;
    }

    /** Allies an awakening answers: friendly players (teammates, or anyone while PvP is off) and the owner's pets. */
    public static boolean ally(ServerPlayer owner, LivingEntity other) {
        return other != owner && other.isAlive() && !other.isSpectator() && other.level() == owner.level()
                && !(other instanceof TemporalEchoEntity) && StyleOps.isFriend(owner, other);
    }

    /**
     * True when nothing solid stands between this point and the sky (leaves don't count). Flight can only
     * awaken out in the open, and only carry off someone who is out there too.
     */
    public static boolean openSky(Entity entity) {
        Level level = entity.level();
        BlockPos head = BlockPos.containing(entity.getEyePosition());
        if (!level.hasChunkAt(head)) return false;
        return head.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, head.getX(), head.getZ());
    }

    // ------------------------------------------------------------------
    // Hazards that aren't a fight
    // ------------------------------------------------------------------

    /** What this creature is about to die of that isn't a fight or a blast, or null. {@code line} is the threshold. */
    public static Peril hazard(LivingEntity entity, float line) {
        if (!entity.isAlive() || entity.isSpectator() || entity instanceof Player p && p.isCreative()) return null;
        float pool = entity.getHealth() + entity.getAbsorptionAmount();
        float landing = fallDamage(entity);
        if (landing > 0 && pool - landing < line) return Peril.FALL;
        if (!entity.hasEffect(MobEffects.FIRE_RESISTANCE) && !entity.fireImmune()
                && (entity.isInLava() && pool < Math.max(line * 3, 12) || entity.isOnFire() && pool < line + 2)) return Peril.FIRE;
        if (entity.isUnderWater() && entity.getAirSupply() <= 0 && !entity.hasEffect(MobEffects.WATER_BREATHING)
                && pool < line + 4) return Peril.DROWNING;
        return null;
    }

    /**
     * The damage the landing ahead will do, if it is close (about half a second away); 0 otherwise. Reads the
     * ground below the way vanilla lands: safe fall distance, the fall multiplier, soft blocks and water.
     */
    public static float fallDamage(LivingEntity entity) {
        Vec3 motion = entity.getDeltaMovement();
        if (entity.onGround() || motion.y > -0.4 || entity.isInWater() || entity.isInLava() || entity.isPassenger()
                || entity.onClimbable() || entity.isFallFlying() || entity.hasEffect(MobEffects.SLOW_FALLING)
                || entity.hasEffect(MobEffects.LEVITATION) || entity instanceof Player p && p.getAbilities().flying
                || EntityCarryService.isBeingCarried(entity)) return 0;
        Level level = entity.level();
        BlockPos.MutableBlockPos cursor = entity.blockPosition().mutable();
        for (int step = 0; step < FALL_SCAN; step++, cursor.move(0, -1, 0)) {
            if (!level.hasChunkAt(cursor) || level.isOutsideBuildHeight(cursor)) return 0;
            BlockState state = level.getBlockState(cursor);
            if (!state.getFluidState().isEmpty()) return 0;
            var shape = state.getCollisionShape(level, cursor);
            if (shape.isEmpty()) continue;
            double top = cursor.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
            double drop = Math.max(0, entity.getY() - top);
            if (drop / Math.max(0.5, -motion.y) > FALL_SOON) return 0;
            double fall = entity.fallDistance + drop - entity.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
            if (fall <= 0) return 0;
            float soft = state.is(BlockTags.FALL_DAMAGE_RESETTING) || state.is(Blocks.SLIME_BLOCK) ? 0
                    : state.is(Blocks.HAY_BLOCK) || state.is(Blocks.HONEY_BLOCK) ? 0.2F
                    : state.getBlock() instanceof BedBlock ? 0.5F : 1;
            float damage = Mth.ceil(fall * entity.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER) * soft);
            return resisted(entity, damage);
        }
        return 0;
    }

    // ------------------------------------------------------------------
    // Blasts about to happen
    // ------------------------------------------------------------------

    /** A creeper or primed TNT whose fuse is nearly done. */
    public static boolean imminent(Entity explosive) {
        if (!explosive.isAlive()) return false;
        if (explosive instanceof Creeper creeper)
            return creeper.getSwelling(1) >= CREEPER_SOON && (creeper.getSwellDir() > 0 || creeper.isIgnited());
        return explosive instanceof PrimedTnt tnt && tnt.getFuse() <= TNT_SOON;
    }

    /** Creepers and primed TNT about to go off within {@code radius} of a point. */
    public static List<Entity> explosives(Level level, Vec3 center, double radius) {
        List<Entity> found = new ArrayList<>();
        AABB box = new AABB(center, center).inflate(radius);
        found.addAll(level.getEntitiesOfClass(Creeper.class, box, Danger::imminent));
        found.addAll(level.getEntitiesOfClass(PrimedTnt.class, box, Danger::imminent));
        return found;
    }

    /** How hard this explosive will hit {@code target}, the way vanilla works it out (armor and difficulty included). */
    public static float blastDamage(Entity explosive, LivingEntity target) {
        float radius = explosive instanceof Creeper creeper
                ? ((CreeperSwellAccess) creeper).heroesEvolved$explosionRadius() * (creeper.isPowered() ? 2 : 1) : 4;
        Vec3 center = explosive instanceof PrimedTnt ? explosive.position().add(0, explosive.getBbHeight() / 16, 0)
                : explosive.position();
        float reach = radius * 2;
        double distance = Math.sqrt(target.distanceToSqr(center)) / reach;
        if (distance > 1) return 0;
        double impact = (1 - distance) * Explosion.getSeenPercent(center, target);
        float damage = (float) ((impact * impact + impact) / 2 * 7 * reach + 1);
        if (target instanceof Player) {
            Difficulty difficulty = target.level().getDifficulty();
            if (difficulty == Difficulty.PEACEFUL) return 0;
            if (difficulty == Difficulty.EASY) damage = Math.min(damage / 2 + 1, damage);
            if (difficulty == Difficulty.HARD) damage *= 1.5F;
        }
        DamageSource source = target.damageSources().explosion(null, null);
        damage = CombatRules.getDamageAfterAbsorb(target, damage, source, target.getArmorValue(),
                (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        return resisted(target, damage);
    }

    /** Would this blast take them below the line? */
    public static boolean blastEndangers(Entity explosive, LivingEntity target, float line) {
        return target.isAlive() && !target.isSpectator() && !(target instanceof Player p && p.isCreative())
                && target.getHealth() + target.getAbsorptionAmount() - blastDamage(explosive, target) < line;
    }

    private static float resisted(LivingEntity entity, float damage) {
        MobEffectInstance resistance = entity.getEffect(MobEffects.DAMAGE_RESISTANCE);
        return resistance == null ? damage : damage * Math.max(0, 1 - 0.2F * (resistance.getAmplifier() + 1));
    }

    // ------------------------------------------------------------------
    // Who is attacking
    // ------------------------------------------------------------------

    /**
     * Whoever the ward needs saving from: monsters near them, anything targeting the ward or the owner,
     * and whoever last hurt either of them (another player included). Never the owner, the ward or a friend
     * who isn't actually attacking.
     */
    public static List<LivingEntity> threats(ServerPlayer owner, LivingEntity ward, double radius) {
        LivingEntity hurtWard = ward.getLastHurtByMob(), hurtOwner = owner.getLastHurtByMob();
        Vec3 center = ward.position();
        return ward.level().getEntitiesOfClass(LivingEntity.class, ward.getBoundingBox().inflate(radius),
                entity -> entity != owner && entity != ward && entity.isAlive() && !entity.isSpectator()
                        && entity.position().distanceToSqr(center) <= radius * radius
                        && !(entity instanceof TemporalEchoEntity) && !EntityCarryService.isBeingCarried(entity)
                        && (entity == hurtWard || entity == hurtOwner
                        || !StyleOps.isFriend(owner, entity) && (entity instanceof Enemy
                        || entity instanceof Mob mob && (mob.getTarget() == ward || mob.getTarget() == owner))));
    }

    /** Level ground direction away from the threats (or {@code fallback} when there are none). */
    public static Vec3 away(Vec3 from, List<LivingEntity> threats, Vec3 fallback) {
        Vec3 push = Vec3.ZERO;
        for (LivingEntity threat : threats) {
            Vec3 offset = from.subtract(threat.position()).multiply(1, 0, 1);
            double distance = Math.max(1, offset.length());
            push = push.add(offset.normalize().scale(1 / distance));
        }
        if (push.lengthSqr() < 1.0E-4) push = fallback.multiply(1, 0, 1);
        if (push.lengthSqr() < 1.0E-4) push = new Vec3(1, 0, 0);
        return push.normalize();
    }

    // ------------------------------------------------------------------
    // While a power tears its way out: the danger is held
    // ------------------------------------------------------------------

    /**
     * One tick of the moment a power wakes. Adrenaline drags the world to a crawl round the one in danger:
     * their attackers can barely move, fuses stop burning down, shots in flight at them slow, and a fall is
     * caught in mid-air. The release then lands on threats that are still where they were.
     */
    static void hold(ServerPlayer owner, LivingEntity ward, Peril peril, int age) {
        for (LivingEntity saved : ward == owner ? List.of(owner) : List.of(owner, ward)) {
            if (age % 5 == 1) {
                for (LivingEntity threat : threats(owner, saved, 12)) {
                    threat.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 5, false, false));
                    threat.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 12, 2, false, false));
                }
            }
            for (Creeper creeper : saved.level().getEntitiesOfClass(Creeper.class, saved.getBoundingBox().inflate(10))) {
                CreeperSwellAccess fuse = (CreeperSwellAccess) creeper;
                creeper.setSwellDir(-1);
                int ceiling = fuse.heroesEvolved$maxSwell() - 6;
                if (fuse.heroesEvolved$swell() > ceiling) fuse.heroesEvolved$setSwell(ceiling);
            }
            for (PrimedTnt tnt : saved.level().getEntitiesOfClass(PrimedTnt.class, saved.getBoundingBox().inflate(10)))
                tnt.setFuse(Math.max(tnt.getFuse() + 1, 10));
            for (Projectile shot : saved.level().getEntitiesOfClass(Projectile.class, saved.getBoundingBox().inflate(8),
                    shot -> shot.getOwner() != owner && shot.getDeltaMovement().dot(saved.position().subtract(shot.position())) > 0)) {
                shot.setDeltaMovement(shot.getDeltaMovement().scale(0.5));
                shot.hurtMarked = true;
            }
            // Caught in mid-air: the fall stops getting worse and hangs there until the power lets go.
            if (peril == Peril.FALL) {
                if (!saved.onGround()) {
                    Vec3 motion = saved.getDeltaMovement();
                    push(saved, new Vec3(motion.x * 0.6, Math.max(motion.y * 0.4, -0.04), motion.z * 0.6));
                }
                saved.resetFallDistance();
            }
        }
    }

    /**
     * The blast never comes: every creeper about to go off near {@code saved} has its fuse run back and loses
     * its target for a moment, and primed TNT is shoved clear with time added to its fuse.
     */
    static void disarm(LivingEntity saved, double radius) {
        for (Creeper creeper : saved.level().getEntitiesOfClass(Creeper.class, saved.getBoundingBox().inflate(radius))) {
            rewind(creeper);
            creeper.setTarget(null);
            creeper.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
        }
        for (PrimedTnt tnt : saved.level().getEntitiesOfClass(PrimedTnt.class, saved.getBoundingBox().inflate(radius))) {
            tnt.setFuse(Math.max(tnt.getFuse(), 60));
            Vec3 away = tnt.position().subtract(saved.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
            push(tnt, away.scale(1.2).add(0, 0.5, 0));
        }
    }

    /** A creeper's fuse back to nothing. */
    static void rewind(Creeper creeper) {
        CreeperSwellAccess fuse = (CreeperSwellAccess) creeper;
        fuse.heroesEvolved$setSwell(0);
        fuse.heroesEvolved$setOldSwell(0);
        creeper.setSwellDir(-1);
    }

    static void push(Entity entity, Vec3 velocity) {
        entity.setDeltaMovement(velocity);
        entity.hurtMarked = true;
        if (entity instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }
}
