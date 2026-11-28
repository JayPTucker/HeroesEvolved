package com.jayptucker.heroesevolved.speed;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.combat.Ultimates;
import com.jayptucker.heroesevolved.combat.Ultimates.Run;
import com.jayptucker.heroesevolved.combat.Ultimates.Ultimate;
import com.jayptucker.heroesevolved.expansion.Signatures;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.shapeshift.ShapeDecoy;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Superspeed's three Ultimates. Their beats are public: {@code SpeedArt} draws from the same numbers, so the lightning
 * lands where the blows do.
 */
public final class SpeedUltimates {
    private SpeedUltimates() {}

    public static final Ultimate OFFENSE = Ultimate.offense(AroundTheWorld::new, 10);
    public static final Ultimate DEFENSE = Ultimate.defense(BetweenHeartbeats::new);
    public static final Ultimate SUPPORT = Ultimate.support(RescueRun::new);

    private static final String POWER = "superspeed";

    // ------------------------------------------------------------------
    // Offense: Around the World
    // ------------------------------------------------------------------

    /**
     * A jab to the face drops the foe into slowed time. The speedster is gone: a ring of gold lightning races once round
     * the whole horizon while the foe hangs there, the sky going to night and back as the lap crosses the far side of
     * the world. They arrive in a sonic boom, wind a fist full of lightning, and an uppercut throws the foe into the sky.
     * They run up the air in a spiral after it and hammer it back down into the ground.
     */
    public static final class AroundTheWorld extends Run {
        /** The jab lands; they leave; the lap runs from LAP to LAP_END; they arrive; the uppercut; the climb; the slam. */
        public static final int JAB = 6, LEAVE = 12, LAP = 14, LAP_END = 64, ARRIVE = 66, UPPER = 86,
                CLIMB_END = 104, SLAM = 106, LANDED = 110, TICKS = 140;
        /** How far out the lap runs, and how high the foe is thrown. */
        public static final double LAP_RADIUS = 58, THROW = 11;

        private final double reach;

        public AroundTheWorld(ServerPlayer p, int tier, LivingEntity foe) {
            super(p, tier, POWER, "lightning_flurry", foe);
            reach = Math.max(1.6, Math.sqrt(foe.position().subtract(origin).horizontalDistanceSqr()));
        }

        @Override protected int length() { return TICKS; }

        /** Where the speedster stands to strike: just in front of the foe. */
        private Vec3 front() { return around(base, -1.4, 0, 0); }

        /** The foe's height above where it stood, by tick: thrown up after the uppercut, driven down by the slam. */
        public static double height(float t) {
            if (t < UPPER) return 0;
            if (t < SLAM) return THROW * (1 - Math.pow(1 - Mth.clamp((t - UPPER) / 10F, 0, 1), 3));
            return THROW * (1 - Mth.clamp((t - SLAM) / (LANDED - SLAM), 0, 1));
        }

        /** The speedster's run up the air round the foe's column, {@code k} from 0 to 1: a turn and a half, ending above it. */
        public static Vec3 climb(Vec3 foeBase, Vec3 facing, float k) {
            double angle = Math.atan2(-facing.z, -facing.x) + k * Math.PI * 3;
            double r = 2.4 - 1.2 * k;
            return foeBase.add(Math.cos(angle) * r, .2 + (THROW + 1.6) * k, Math.sin(angle) * r);
        }

        @Override protected void open() {
            face(foeCentre());
            cue();
            pose(ArmPose.SPEED_LUNGE, 8);
            piece(Piece.SPEED_ULT_WORLD, origin, base, 1);
            AbilityEffects.send(level, Kind.SPEED_ZIP, origin, front(), 1);
            double d = reach;
            cinema(cam()
                    .key(0, -2.4, 2.3, 1.9, d * .6, 0, 1.3)
                    .key(LEAVE - 2, d * .45, 3.3, 1.3, d, 0, 1.4)
                    .key(LAP + 6, d - 9, 7, 8, d, 0, 1.2)
                    .key(LAP + 20, d - 34, 0, 46, d + 6, 0, 0)
                    .key(LAP_END - 8, d - 30, 10, 42, d + 4, 0, 0)
                    .key(LAP_END + 1, d - 7, -6, 5, d, 0, 1.4)
                    .key(ARRIVE + 6, d - 3.6, -2.6, .7, d, 0, 1.5)
                    .watch(UPPER + 2, d - 7.5, -4.5, 2, foe)
                    .watch(CLIMB_END - 2, d - 10, -6, 9, foe)
                    .key(LANDED, d - 6, 5.5, 4, d, 0, .6)
                    .key(TICKS - 12, d - 8.5, 7.5, 5, d, 0, 1)
                    .until(TICKS - 10));
        }

        @Override protected void beat(int age) {
            if (age < JAB) {
                Vec3 from = origin.lerp(front(), (age - 1) / (float) (JAB - 1)), to = origin.lerp(front(), age / (float) (JAB - 1));
                glide(from, to);
            }
            if (age == JAB) {
                still();
                hit(foe, 3);
                sound(foeCentre(), SoundEvents.PLAYER_ATTACK_CRIT, 1.2F, .7F);
                sound(foeCentre(), SoundEvents.BEACON_DEACTIVATE, .9F, .5F);
                shake(foeAt(), 12, .2F, 6);
            }
            if (age == LEAVE) {
                Vec3 out = player.position().add(facing.scale(-20)).add(side.scale(6));
                AbilityEffects.send(level, Kind.SPEED_ZIP, player.position(), out, 1.4F);
                hide();
                sound(player.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, .7F, 1.6F);
            }
            if (age > LEAVE && age < ARRIVE) {
                // Out of reach while away: the body waits at the arrival point, hidden, so nothing can strike it.
                if (age == LEAVE + 1) blink(front());
                if (age % 10 == 0) sound(around(base, 0, 0, 2), SoundEvents.LIGHTNING_BOLT_THUNDER, .35F, 1.8F);
            }
            if (age == LAP + 18 && player.connection != null)
                // The lap crosses the night side of the world.
                ScreenTintPayload.send(player, 0x9A0A1230, 30, ScreenTintPayload.Style.FLASH);
            if (age == ARRIVE) {
                show();
                face(foeCentre());
                AbilityEffects.send(level, Kind.SPEED_BOOM, around(base, -6, 0, 1), player.position(), 1.6F);
                sound(player.position(), SoundEvents.GENERIC_EXPLODE.value(), .8F, 1.7F);
                shake(player.position(), 16, .35F, 8);
                pose(ArmPose.SPEED_THROW, 18);
            }
            if (age == UPPER - 2) face(foeCentre().add(0, 3, 0));
            if (age == UPPER) {
                pose(ArmPose.SPEED_LUNGE, 8);
                hit(foe, 10);
                sound(foeCentre(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.5F, .6F);
                sound(foeCentre(), SoundEvents.LIGHTNING_BOLT_IMPACT, 1, 1.4F);
                shake(foeAt(), 16, .3F, 6);
            }
            if (age >= UPPER) lift(height(age));
            if (age > UPPER && age <= CLIMB_END) {
                if (age == UPPER + 1) hover(true);
                float k0 = (age - 1 - UPPER) / (float) (CLIMB_END - UPPER), k1 = (age - UPPER) / (float) (CLIMB_END - UPPER);
                glide(climb(base, facing, k0), climb(base, facing, k1));
                if (age % 3 == 0) AbilityEffects.send(level, Kind.SPEED_ZIP, climb(base, facing, k0), climb(base, facing, k1), .8F);
            }
            if (age == CLIMB_END + 1) {
                still();
                face(foeCentre());
            }
            if (age == SLAM) {
                pose(ArmPose.SPEED_LUNGE, 6);
                hit(foe, 2);
                sound(foeCentre(), SoundEvents.PLAYER_ATTACK_STRONG, 1.5F, .6F);
            }
            if (age > SLAM && age <= LANDED) {
                Vec3 down = base.add(0, height(age) + 1.6, 0);
                glide(player.position(), down.add(facing.scale(-.8)));
            }
            if (age == LANDED) {
                hover(false);
                still();
                hit(foe, 10);
                Vec3 at = base;
                splash(at, 5, 6, t -> shove(t, at, 1.1, .5));
                sound(at, SoundEvents.GENERIC_EXPLODE.value(), 1.4F, .7F);
                sound(at, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.2F, .8F);
                shake(at, 24, .6F, 12);
                AbilityEffects.send(level, Kind.SPEED_IMPACT, at, at.add(0, 1, 0), 2.2F);
                release();
            }
        }

        @Override protected void tidy(boolean broken) { hover(false); }
    }

    // ------------------------------------------------------------------
    // Defense: Between Heartbeats
    // ------------------------------------------------------------------

    /**
     * Between Heartbeats. The speedster vibrates so fast that they come apart: for a breath they blur in place, then
     * three illusions of them (their own face, armour and held items) tear loose and run. For twelve seconds the
     * illusions fight every enemy within 32 blocks of the speedster, each picking its own, striking from one side and
     * then another as they blur round it, and turning on themselves everything that was hunting the speedster. An illusion
     * takes {@link #BLOWS} blows (stepping aside from each) before it breaks. The speedster, still vibrating, takes every
     * blow at half (the Ultimate engine halves blows on any caster whose run opens on its camera). When it ends, the
     * illusions run home into them.
     */
    public static final class BetweenHeartbeats extends Run {
        /** Vibrating until SPLIT; the illusions fight until HOME, then run back in; it ends at TICKS. */
        public static final int SPLIT = 16, HOME = 248, TICKS = 260, INTRO = 24;
        /** How many illusions, how many blows each takes, and how often each one strikes. */
        public static final int ILLUSIONS = 3, BLOWS = 3, STRIKE = 20;
        /** Enemies this far from the speedster are fought; an illusion strikes for DAMAGE and runs up to RUN a tick. */
        public static final double RANGE = 32, RUN = 2.2;
        public static final float DAMAGE = 2;

        private final List<Illusion> illusions = new ArrayList<>();

        /** One illusion: its body, the enemy it fights, the side it comes at it from, and how many blows it has left. */
        private final class Illusion {
            final ShapeDecoy body;
            final int lane;
            LivingEntity mark;
            double side;
            int blows = BLOWS, next, stuck, dodged = -100;

            Illusion(ShapeDecoy body, int lane) {
                this.body = body;
                this.lane = lane;
                this.side = lane * Math.PI * 2 / ILLUSIONS;
            }

            boolean standing() { return !body.isRemoved() && body.level() == level; }
        }

        public BetweenHeartbeats(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "phase_blur", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.SPEED_GUARD, SPLIT + 2);
            heroShot(INTRO, 0);
            piece(Piece.SPEED_ULT_HEART, player.position(), player.position(), 1, player, 0);
            sound(player.position(), SoundEvents.WARDEN_HEARTBEAT, 1.4F, .7F);
            sound(player.position(), SoundEvents.BEACON_ACTIVATE, .8F, 1.6F);
        }

        @Override protected void beat(int age) {
            if (age % 20 == 0 && age < TICKS) {
                piece(Piece.SPEED_ULT_HEART, player.position(), player.position(), 1, player, age / 20);
                sound(player.position(), SoundEvents.WARDEN_HEARTBEAT, .8F, .6F);
            }
            if (age < SPLIT && age % 4 == 0) sound(player.position(), SoundEvents.BEEHIVE_WORK, .5F, 2);
            if (age == SPLIT) split();
            if (age > SPLIT && age < HOME) {
                for (Illusion illusion : illusions) if (illusion.standing()) fight(illusion, age);
                if (age % 10 == 0) draw();
            }
            if (age == HOME) home();
        }

        /** Three illusions tear loose from the speedster and run out in three directions. */
        private void split() {
            pose(ArmPose.SPEED_SPLIT, 14);
            Vec3 at = player.position();
            for (int i = 0; i < ILLUSIONS; i++) {
                ShapeDecoy body = ModEntities.SHAPE_DECOY.get().create(level);
                if (body == null) continue;
                Illusion[] self = new Illusion[1];
                body.setupIllusion(player, TICKS - SPLIT + 20, i, by -> struck(self[0], by));
                body.moveTo(at.x, at.y, at.z, player.getYRot(), 0);
                body.face(player.getYRot());
                if (!level.addFreshEntity(body)) continue;
                self[0] = new Illusion(body, i);
                illusions.add(self[0]);
                double a = Math.atan2(facing.z, facing.x) + i * Math.PI * 2 / ILLUSIONS;
                Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a)).scale(3);
                AbilityEffects.send(level, Kind.SPEED_ZIP, at, body.runToward(out), .8F);
            }
            AbilityEffects.send(level, Kind.SPEED_BURST, at, at.add(0, 1, 0), 1);
            sound(at, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.2F, 1.3F);
            sound(at, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), .9F, 1.5F);
            shake(at, 10, .2F, 6);
            draw();
        }

        /** One tick of an illusion: find an enemy, run to its side, strike, and blur round to the next side. */
        private void fight(Illusion illusion, int age) {
            ShapeDecoy body = illusion.body;
            if (illusion.mark == null || age % 10 == 0 || !fightable(illusion.mark)) illusion.mark = pick(illusion);
            LivingEntity mark = illusion.mark;
            Vec3 at = body.position();
            if (mark == null) {
                // Nothing to fight: guard the speedster, running a ring round them.
                double a = age * .2 + illusion.lane * Math.PI * 2 / ILLUSIONS;
                Vec3 spot = player.position().add(Math.cos(a) * (2.4 + illusion.lane * .3), 0, Math.sin(a) * (2.4 + illusion.lane * .3));
                move(illusion, spot, 1.1);
                return;
            }
            double reach = mark.getBbWidth() / 2 + .9;
            Vec3 spot = mark.position().add(Math.cos(illusion.side) * reach, 0, Math.sin(illusion.side) * reach);
            double away = at.distanceTo(spot);
            if (away > RANGE + 8) { blinkTo(illusion, mark); return; }
            Vec3 now = move(illusion, spot, RUN);
            illusion.stuck = now.distanceToSqr(at) < .04 && away > 2 ? illusion.stuck + 1 : 0;
            if (illusion.stuck > 6) { blinkTo(illusion, mark); return; }
            double flat = Math.sqrt(now.subtract(mark.position()).horizontalDistanceSqr());
            if (age >= illusion.next && flat <= reach + .9 && Math.abs(now.y - mark.getY()) < 2.5) strike(illusion, mark, age);
        }

        /** Runs an illusion toward a spot (at most {@code speed} this tick), leaving a gold streak when it moves fast. */
        private Vec3 move(Illusion illusion, Vec3 spot, double speed) {
            ShapeDecoy body = illusion.body;
            Vec3 from = body.position(), want = spot.subtract(from).multiply(1, 0, 1);
            double d = want.length();
            if (d < .05) return from;
            Vec3 to = body.runToward(want.scale(Math.min(speed, d) / d));
            if (to.distanceToSqr(from) > 1 && age % 2 == 0)
                AbilityEffects.send(level, Kind.SPEED_TRAIL, from, to, .6F);
            return to;
        }

        /** An illusion that can't get there (a wall, a gap, too far) is simply beside its enemy, in a flash. */
        private void blinkTo(Illusion illusion, LivingEntity mark) {
            ShapeDecoy body = illusion.body;
            double reach = mark.getBbWidth() / 2 + .9;
            for (int i = 0; i < ILLUSIONS; i++) {
                double a = illusion.side + i * Math.PI * 2 / ILLUSIONS;
                Vec3 spot = mark.position().add(Math.cos(a) * reach, 0, Math.sin(a) * reach);
                if (!SpeedService.safe(body, spot)) continue;
                Vec3 from = body.position();
                body.teleportTo(spot.x, spot.y, spot.z);
                illusion.side = a;
                illusion.stuck = 0;
                AbilityEffects.send(level, Kind.SPEED_ZIP, from, spot, .8F);
                return;
            }
            illusion.mark = null;
        }

        /** A blow: the illusion swings, lands it, and draws the enemy's anger onto itself, then blurs round to strike again. */
        private void strike(Illusion illusion, LivingEntity mark, int age) {
            ShapeDecoy body = illusion.body;
            Vec3 d = mark.position().subtract(body.position());
            body.face((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
            body.swing(age / STRIKE % 2 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
            hit(mark, DAMAGE);
            // The blow is the speedster's (their kill, their loot), but the anger is the illusion's.
            mark.setLastHurtByMob(body);
            if (mark instanceof Mob mob) turn(mob, body);
            shove(mark, body.position(), .25, .1);
            piece(Piece.SPEED_ULT_DODGE, body.position(), mark.getBoundingBox().getCenter(), .7F, player, 2);
            sound(mark.position(), SoundEvents.PLAYER_ATTACK_SWEEP, .6F, 1.7F);
            illusion.next = age + STRIKE;
            // Round to another side for the next blow (the run there leaves afterimages).
            illusion.side += Math.PI * 2 / 3 * (illusion.lane % 2 == 0 ? 1 : -1);
        }

        /** A blow on an illusion: it steps aside (an afterimage takes the hit) or, on its last, breaks into afterimages. */
        private boolean struck(Illusion illusion, Entity by) {
            if (illusion == null || !illusion.standing()) return false;
            ShapeDecoy body = illusion.body;
            // One step aside per blow: a flurry landing within a few ticks is the same blow (the run's clock always ticks).
            if (age - illusion.dodged < 6) return true;
            illusion.dodged = age;
            Vec3 from = body.position();
            if (--illusion.blows <= 0) {
                breakApart(illusion);
                return true;
            }
            Vec3 toward = by != null ? from.subtract(by.position()).multiply(1, 0, 1) : facing;
            toward = toward.lengthSqr() < 1e-4 ? side : toward.normalize();
            Vec3 aside = new Vec3(-toward.z, 0, toward.x).scale(illusion.blows % 2 == 0 ? 1.8 : -1.8);
            Vec3 to = body.runToward(aside);
            AbilityEffects.send(level, Kind.SPEED_DODGE, from, to, 1);
            sound(from, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), .45F, 1.9F);
            // It answers at once.
            illusion.next = Math.min(illusion.next, age + 2);
            return true;
        }

        /** The last blow: the illusion bursts into a ring of its own afterimages and is gone. */
        private void breakApart(Illusion illusion) {
            ShapeDecoy body = illusion.body;
            Vec3 at = body.position();
            AbilityEffects.send(level, Kind.SPEED_MIRAGE, at, at.add(0, 1.6, 0), 1);
            AbilityEffects.send(level, Kind.SPEED_BURST, at, at.add(0, 1, 0), .8F);
            sound(at, SoundEvents.ILLUSIONER_MIRROR_MOVE, .8F, 1.8F);
            forgetTargets(body);
            body.discard();
        }

        /** Whether an enemy is still one an illusion should fight. */
        private boolean fightable(LivingEntity e) {
            return e.isAlive() && !e.isRemoved() && e.level() == level && !(e instanceof ShapeDecoy)
                    && e.position().distanceToSqr(player.position()) <= (RANGE + 4) * (RANGE + 4) && StyleOps.isFoe(player, e) && enemy(e);
        }

        /**
         * An enemy, not just anything that isn't a friend: a monster, anything hunting the speedster or a friend, or
         * whoever struck or was struck by the speedster lately. Villagers and cows grazing nearby are left alone.
         */
        private boolean enemy(LivingEntity e) {
            if (e instanceof net.minecraft.world.entity.monster.Enemy) return true;
            if (e instanceof Mob mob && mob.getTarget() != null && StyleOps.isFriend(player, mob.getTarget())) return true;
            if (e instanceof Mob mob && mob.getTarget() instanceof ShapeDecoy copy && player.getUUID().equals(copy.owner())) return true;
            if (e == player.getLastHurtByMob() && player.tickCount - player.getLastHurtByMobTimestamp() < 200) return true;
            return e == player.getLastHurtMob() && player.tickCount - player.getLastHurtMobTimestamp() < 200;
        }

        /** The enemy an illusion should fight: one no other illusion has, if there is one; the nearest to it. */
        private LivingEntity pick(Illusion illusion) {
            List<LivingEntity> enemies = StyleOps.foes(player, player.position(), RANGE).stream().filter(this::fightable).toList();
            if (enemies.isEmpty()) return null;
            Vec3 at = illusion.body.position();
            LivingEntity best = null, taken = null;
            double bestD = Double.MAX_VALUE, takenD = Double.MAX_VALUE;
            for (LivingEntity e : enemies) {
                double d = e.distanceToSqr(at);
                boolean claimed = illusions.stream().anyMatch(o -> o != illusion && o.standing() && o.mark == e);
                if (!claimed && d < bestD) { best = e; bestD = d; }
                if (claimed && d < takenD) { taken = e; takenD = d; }
            }
            if (illusion.mark != null && illusion.mark != best && fightable(illusion.mark)) return illusion.mark;
            return best != null ? best : taken;
        }

        /** Everything within range hunting the speedster (or a friend) turns on the nearest illusion. */
        private void draw() {
            List<Illusion> up = illusions.stream().filter(Illusion::standing).toList();
            if (up.isEmpty()) return;
            List<LivingEntity> friends = friends(RANGE + 4);
            for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE),
                    m -> m.isAlive() && m.getTarget() != null && friends.contains(m.getTarget()))) {
                Illusion near = up.stream().min(java.util.Comparator.comparingDouble(i -> i.body.distanceToSqr(mob))).orElseThrow();
                turn(mob, near.body);
            }
        }

        private void turn(Mob mob, ShapeDecoy body) {
            if (Signatures.boss(mob)) return;
            mob.setTarget(body);
            if (mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED))
                mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, body);
        }

        /** Anything still angry at a gone illusion forgets it (and finds the next one on the next draw). */
        private void forgetTargets(ShapeDecoy body) {
            for (Mob mob : level.getEntitiesOfClass(Mob.class, body.getBoundingBox().inflate(RANGE + 8), m -> m.getTarget() == body)) {
                mob.setTarget(null);
                if (mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED))
                    mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
        }

        /** The illusions run home and fold back into the speedster. */
        private void home() {
            Vec3 at = player.position();
            for (Illusion illusion : illusions) {
                if (!illusion.standing()) continue;
                AbilityEffects.send(level, Kind.SPEED_ZIP, illusion.body.position(), at, .8F);
                forgetTargets(illusion.body);
                illusion.body.discard();
            }
            AbilityEffects.send(level, Kind.SPEED_BURST, at, at.add(0, 1, 0), .9F);
            pose(ArmPose.SPEED_STILL, 12);
            sound(at, SoundEvents.ILLUSIONER_MIRROR_MOVE, .9F, .9F);
        }

        @Override protected void tidy(boolean broken) {
            for (Illusion illusion : illusions) {
                if (!illusion.standing()) continue;
                if (broken) AbilityEffects.send(level, Kind.SPEED_BURST, illusion.body.position(), illusion.body.position().add(0, 1, 0), .7F);
                forgetTargets(illusion.body);
                illusion.body.discard();
            }
        }

        /** The illusions' bodies (for tests and the admin console). */
        public List<ShapeDecoy> illusions() { return illusions.stream().map(i -> i.body).toList(); }

        /** How many blows each illusion still has, in order (0 once broken). */
        public List<Integer> blows() { return illusions.stream().map(i -> i.standing() ? i.blows : 0).toList(); }
    }

    // ------------------------------------------------------------------
    // Support: Rescue Run
    // ------------------------------------------------------------------

    /**
     * In the space of a breath the speedster runs to every friend within 32 blocks and brings them back: each one is set
     * down beside them (never inside a wall), healed, cleansed and quickened, and every hostile shot in the air nearby
     * is plucked out on the way. They stop in a gust that throws anyone close back.
     */
    public static final class RescueRun extends Run {
        public static final int STEP = 3, START = 2, MAX = 8, TICKS = 40;
        public static final double RANGE = 32;
        private final List<LivingEntity> route = new ArrayList<>();

        public RescueRun(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "relay_run", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            List<LivingEntity> friends = new ArrayList<>(friends(RANGE));
            friends.remove(player);
            friends.sort((a, b) -> Double.compare(angle(a), angle(b)));
            route.addAll(friends.subList(0, Math.min(MAX, friends.size())));
            cinema(cam().key(0, -3, 1.6, 2, 0, 0, 1.2).key(10, -8, 0, 16, 2, 0, 0).key(TICKS - 8, -7, 3, 11, 0, 0, .6).until(TICKS - 4));
            pose(ArmPose.SPEED_ESCORT, 10);
            hide();
            AbilityEffects.send(level, Kind.SPEED_ZIP, player.position(), player.position(), 1);
            for (Projectile shot : shots(player.position(), 16)) {
                piece(Piece.SPEED_ULT_DODGE, shot.position(), player.getBoundingBox().getCenter(), .4F, player, 1);
                shot.discard();
            }
            sound(player.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, .6F, 1.7F);
        }

        private double angle(LivingEntity e) {
            Vec3 d = e.position().subtract(origin);
            double a = Math.atan2(d.z, d.x) - Math.atan2(facing.z, facing.x);
            return Mth.positiveModulo(a, Math.PI * 2);
        }

        /** The spot beside the speedster the {@code i}th of {@code n} friends is set down at. */
        public static Vec3 spot(Vec3 at, int i, int n) {
            double a = i * Math.PI * 2 / Math.max(1, n);
            return at.add(Math.cos(a) * 1.6, 0, Math.sin(a) * 1.6);
        }

        @Override protected void beat(int age) {
            int i = (age - START) / STEP;
            if (age >= START && (age - START) % STEP == 0 && i < route.size()) {
                LivingEntity friend = route.get(i);
                if (friend.isAlive() && friend.level() == level) {
                    Vec3 was = friend.position(), want = spot(origin, i, route.size());
                    Vec3 to = SpeedService.safe(friend, want) ? want : was;
                    piece(Piece.SPEED_ULT_RESCUE, origin, was, 1, player, i);
                    if (to != was) {
                        AbilityEffects.send(level, Kind.SPEED_ZIP, was, to, 1);
                        if (friend instanceof ServerPlayer other) com.jayptucker.heroesevolved.combat.StyleHits.blink(other, to);
                        else { friend.teleportTo(to.x, to.y, to.z); friend.resetFallDistance(); }
                    }
                    aid(friend);
                    sound(to, SoundEvents.PLAYER_ATTACK_SWEEP, .6F, 2);
                }
            }
            int done = START + route.size() * STEP + 2;
            if (age == done || (route.isEmpty() && age == START + 2)) {
                show();
                aid(player);
                Vec3 at = player.position();
                com.jayptucker.heroesevolved.network.SetPiecePayload.send(level, Piece.SPEED_GUST, at, at.add(facing), 1.4F, null);
                for (LivingEntity t : StyleOps.foes(player, at, 6)) shove(t, at, 1.2, .4);
                sound(at, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.2F, .8F);
                pose(ArmPose.SPEED_STILL, 14);
            }
        }

        private void aid(LivingEntity friend) {
            StyleOps.heal(friend, 6 + tier);
            StyleOps.cleanse(friend);
            StyleOps.effect(friend, MobEffects.MOVEMENT_SPEED, 160, 1);
            StyleOps.effect(friend, MobEffects.DAMAGE_RESISTANCE, 160, 0);
        }

        /** Who was brought back, for tests. */
        public List<LivingEntity> route() { return route; }
    }
}
