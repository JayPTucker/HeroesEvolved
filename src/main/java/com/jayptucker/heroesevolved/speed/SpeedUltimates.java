package com.jayptucker.heroesevolved.speed;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.combat.Ultimates;
import com.jayptucker.heroesevolved.combat.Ultimates.Run;
import com.jayptucker.heroesevolved.combat.Ultimates.Ultimate;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
     * Twelve seconds lived between heartbeats. Every blow and every shot aimed at the speedster finds only an
     * afterimage: they are a step to the side, and the afterimage strikes the one who swung. Shots that come near are
     * plucked out of the air. When it ends, every afterimage left behind runs in on the nearest foe at once.
     */
    public static final class BetweenHeartbeats extends Run {
        public static final int TICKS = 240, INTRO = 22, MAX_IMAGES = 6;
        private int images;
        private final Map<UUID, Integer> struck = new HashMap<>();
        private int lastDodge = -100;

        public BetweenHeartbeats(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "phase_blur", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.SPEED_GUARD, 30);
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
            // Shots that come close are taken out of the air.
            Vec3 body = player.getBoundingBox().getCenter();
            for (Projectile shot : shots(body, 3.2)) {
                if (shot.getDeltaMovement().dot(body.subtract(shot.position())) <= 0) continue;
                piece(Piece.SPEED_ULT_DODGE, shot.position(), body, .4F, player, 1);
                shot.discard();
                sound(body, SoundEvents.ARROW_HIT, .6F, 1.8F);
            }
        }

        @Override protected void incoming(LivingIncomingDamageEvent event, LivingEntity victim) {
            if (victim != player || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
            var source = event.getSource();
            if (source.getEntity() == null && source.getDirectEntity() == null) {
                // Fire, a fall, a blast: too wide to step round, but the step takes half of it.
                event.setAmount(event.getAmount() * .5F);
                return;
            }
            event.setCanceled(true);
            Vec3 from = player.position();
            Vec3 toward = source.getDirectEntity() != null ? from.subtract(source.getDirectEntity().position()) : facing;
            toward = toward.multiply(1, 0, 1);
            toward = toward.lengthSqr() < 1e-4 ? side : toward.normalize();
            Vec3 aside = new Vec3(-toward.z, 0, toward.x).scale((age / 7) % 2 == 0 ? 1.8 : -1.8);
            if (age - lastDodge >= 2) {
                Vec3 to = blink(from.add(aside));
                lastDodge = age;
                piece(Piece.SPEED_ULT_DODGE, from, to, 1, player, 0);
                AbilityEffects.send(level, Kind.SPEED_DODGE, from, to, 1);
                sound(from, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), .5F, 1.9F);
                images = Math.min(MAX_IMAGES, images + 1);
            }
            // The afterimage left behind strikes back at whoever swung (once a second each).
            if (source.getEntity() instanceof LivingEntity attacker && attacker != player && attacker.distanceToSqr(from) < 36
                    && StyleOps.isFoe(player, attacker)) {
                int last = struck.getOrDefault(attacker.getUUID(), -100);
                if (age - last >= 20) {
                    struck.put(attacker.getUUID(), age);
                    hit(attacker, 3);
                    piece(Piece.SPEED_ULT_DODGE, from, attacker.getBoundingBox().getCenter(), .7F, player, 2);
                }
            }
        }

        @Override protected void finish() {
            List<LivingEntity> near = StyleOps.foes(player, player.position(), 8);
            if (near.isEmpty() || images == 0) return;
            LivingEntity mark = near.getFirst();
            for (int i = 0; i < Math.max(1, images); i++) {
                double a = i * Math.PI * 2 / Math.max(1, images);
                Vec3 from = mark.position().add(Math.cos(a) * 2.2, 0, Math.sin(a) * 2.2);
                piece(Piece.SPEED_ULT_DODGE, from, mark.getBoundingBox().getCenter(), .7F, player, 2);
            }
            hit(mark, 2 * images);
            sound(mark.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 1.5F);
        }
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
