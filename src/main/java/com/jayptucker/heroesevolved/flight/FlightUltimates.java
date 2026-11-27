package com.jayptucker.heroesevolved.flight;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Ultimates.Run;
import com.jayptucker.heroesevolved.combat.Ultimates.Ultimate;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.List;

/** Flight's three Ultimates; {@code FlightArt} draws them from these beats. */
public final class FlightUltimates {
    private FlightUltimates() {}

    public static final Ultimate OFFENSE = Ultimate.offense(Stratosphere::new, 9);
    public static final Ultimate DEFENSE = Ultimate.defense(EyeOfTheStorm::new);
    public static final Ultimate SUPPORT = Ultimate.support(Airlift::new);

    private static final String POWER = "flight";

    // ------------------------------------------------------------------
    // Offense: Stratosphere
    // ------------------------------------------------------------------

    /**
     * A diving tackle takes the foe, and the flyer rockets straight up with it, a ring of condensation bursting off
     * them every few blocks, until the ground is a long way down (as high as the sky over them allows). At the top they
     * let go; for a moment nothing moves. Then they roll over the foe and drive it straight back down in a cone of
     * vapour, and the landing blows a crater of mist across the ground.
     */
    public static final class Stratosphere extends Run {
        public static final int GRAB = 8, CLIMB = 10, APEX = 50, HANG = 58, OVER = 64, IMPACT = 78, LANDED = 98, TICKS = 140;
        public static final double MAX_HEIGHT = 36, MIN_HEIGHT = 5;
        /** How high this one goes: as high as the sky over the foe allows. */
        public final double height;

        public Stratosphere(ServerPlayer p, int tier, LivingEntity foe) {
            super(p, tier, POWER, "tempest_drill", foe);
            Vec3 from = foe.position().add(0, foe.getBbHeight() + .2, 0);
            var hit = p.serverLevel().clip(new ClipContext(from, from.add(0, MAX_HEIGHT + 4, 0), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, CollisionContext.empty()));
            double room = hit.getType() == HitResult.Type.MISS ? MAX_HEIGHT + 4 : hit.getLocation().y - from.y;
            height = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, room - 4));
        }

        @Override protected int length() { return TICKS; }

        /** The height above the foe's start, by tick, of the foe (the flyer is a block and a half below it going up, above it coming down). */
        public static double rise(float t, double height) {
            if (t < CLIMB) return 0;
            if (t < APEX) { float k = (t - CLIMB) / (APEX - CLIMB); return height * k * k * (3 - 2 * k); }
            if (t < OVER) return height;
            if (t < IMPACT) { float k = (t - OVER) / (IMPACT - OVER); return height * (1 - k * k); }
            return 0;
        }

        @Override protected void open() {
            face(foeCentre());
            cue();
            pose(ArmPose.FLIGHT_COIL, GRAB);
            hover(true);
            piece(Piece.FLIGHT_ULT_SKY, origin, base, (float) height);
            double d = Math.sqrt(base.subtract(origin).horizontalDistanceSqr());
            cinema(cam()
                    .key(0, -3, 2.6, 1.6, d, 0, 1)
                    .key(GRAB + 2, d - 3, 3.5, .6, d, 0, 2)
                    .follow(CLIMB + 10, -4, 3, -4, foe)
                    .follow(APEX - 4, -2.5, 2.5, 4.5, foe)
                    .follow(HANG, -1.5, 3.5, 2.5, foe)
                    .follow(OVER + 4, -3, 4, 5, foe)
                    .key(IMPACT + 2, d - 9, 7, 6, d, 0, .5)
                    .key(TICKS - 14, d - 11, 8, 6.5, d, 0, 1.5)
                    .until(TICKS - 10));
        }

        private Vec3 below(double h) { return base.subtract(facing.scale(.7)).add(0, Math.max(0, h - 1.6), 0); }

        @Override protected void beat(int age) {
            if (age <= GRAB) {
                Vec3 to = base.subtract(facing.scale(1));
                glide(origin.lerp(to, (age - 1) / (float) GRAB), origin.lerp(to, age / (float) GRAB));
            }
            if (age == GRAB) {
                hit(foe, 2);
                Takedowns.flinch(foe);
                sound(foeCentre(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2F, .8F);
            }
            if (age >= CLIMB) lift(rise(age, height));
            if (age > CLIMB && age <= APEX) {
                glide(below(rise(age - 1, height)), below(rise(age, height)));
                if ((age - CLIMB) % 8 == 0) sound(player.position(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.4F, .6F);
            }
            if (age == APEX) {
                still();
                sound(player.position(), SoundEvents.ELYTRA_FLYING, .5F, 1.8F);
            }
            if (age > HANG && age <= OVER) {
                if (age == HANG + 1) pose(ArmPose.FLIGHT_ROLL, OVER - HANG + 2);
                float k0 = (age - 1 - HANG) / (float) (OVER - HANG), k1 = (age - HANG) / (float) (OVER - HANG);
                glide(arc(k0), arc(k1));
            }
            if (age > OVER && age <= IMPACT) {
                if (age == OVER + 1) {
                    pose(ArmPose.FLIGHT_CLAP, 16);
                    sound(player.position(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 2, .4F);
                    sound(player.position(), SoundEvents.GENERIC_EXPLODE.value(), .7F, 1.8F);
                }
                double h0 = rise(age - 1, height), h1 = rise(age, height);
                glide(base.add(0, h0 + foe.getBbHeight() + .4, 0), base.add(0, h1 + foe.getBbHeight() + .4, 0));
            }
            if (age == IMPACT) {
                hit(foe, 19);
                Vec3 at = base;
                splash(at, 6, 6, t -> shove(t, at, 1.3, .6));
                sound(at, SoundEvents.GENERIC_EXPLODE.value(), 1.6F, .7F);
                sound(at, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 2, .5F);
                shake(at, 28, .65F, 14);
                release();
            }
            if (age > IMPACT && age <= LANDED) {
                Vec3 top = base.add(0, foe.getBbHeight() + .4, 0), down = base.subtract(facing.scale(1.6));
                float k0 = (age - 1 - IMPACT) / (float) (LANDED - IMPACT), k1 = (age - IMPACT) / (float) (LANDED - IMPACT);
                glide(top.lerp(down, k0), top.lerp(down, k1));
            }
            if (age == LANDED) { hover(false); still(); }
        }

        /** The roll over the top: a half loop from under the foe to over it. */
        private Vec3 arc(float k) {
            double a = Math.PI * k;
            Vec3 c = base.add(0, height + .6, 0);
            return c.subtract(facing.scale(1.6 * Math.cos(a))).add(0, -1.8 * Math.cos(a) + .6 * Math.sin(a), 0);
        }

        @Override protected void tidy(boolean broken) { hover(false); }
    }

    // ------------------------------------------------------------------
    // Defense: Eye of the Storm
    // ------------------------------------------------------------------

    /**
     * A wall of wind wheels round the flyer for twelve seconds, five blocks out, while they stand in the calm at its
     * heart. Shots that reach the wall are caught up and flung away; anyone who comes into it is lifted and thrown back
     * out; blows that reach the flyer are lighter. At the end the storm breaks outward and throws everyone near clear.
     */
    public static final class EyeOfTheStorm extends Run {
        public static final int TICKS = 240, INTRO = 22;
        public static final double RADIUS = 5;

        public EyeOfTheStorm(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "gale_wall", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.WIND_ARMS, 24);
            heroShot(INTRO, 2);
            piece(Piece.FLIGHT_ULT_EYE, player.position(), player.position(), (float) RADIUS, player, 0);
            sound(player.position(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.6F, .5F);
        }

        @Override protected void beat(int age) {
            Vec3 at = player.position();
            if (age % 20 == 0 && age < TICKS) {
                piece(Piece.FLIGHT_ULT_EYE, at, at, (float) RADIUS, player, age / 20);
                sound(at, SoundEvents.ELYTRA_FLYING, .6F, .7F);
            }
            for (Projectile shot : shots(at.add(0, 1, 0), RADIUS + 1)) {
                Vec3 away = shot.position().subtract(at).multiply(1, 0, 1);
                away = away.lengthSqr() < 1e-4 ? facing : away.normalize();
                StyleOps.push(shot, away.scale(1.4).add(0, .5, 0));
                shot.setOwner(player);
            }
            if (age % 4 == 0) for (LivingEntity t : StyleOps.foes(player, at, RADIUS + .5)) shove(t, at, 1, .5);
        }

        @Override protected void incoming(LivingIncomingDamageEvent event, LivingEntity victim) {
            if (victim == player && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))
                event.setAmount(event.getAmount() * .7F);
        }

        @Override protected void finish() {
            Vec3 at = player.position();
            for (LivingEntity t : StyleOps.foes(player, at, RADIUS + 3)) { hit(t, 4); shove(t, at, 1.8, .7); }
            piece(Piece.FLIGHT_ULT_EYE, at, at, (float) RADIUS, player, -1);
            sound(at, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 2, .7F);
        }
    }

    // ------------------------------------------------------------------
    // Support: Airlift
    // ------------------------------------------------------------------

    /**
     * Wind gathers under every friend within sixteen blocks and lifts them off the ground, out of reach, with wings of
     * vapour at their shoulders; for twelve seconds they float down slowly, mending, with shots against them halved by
     * the wind round them.
     */
    public static final class Airlift extends Run {
        public static final int TICKS = 240, INTRO = 24, LIFT = 30;
        public static final double RANGE = 16;
        private final List<LivingEntity> lifted = new ArrayList<>();

        public Airlift(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "formation_flight", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.ARMS_RAISED, 20);
            cinema(cam().key(0, -3.2, 1.8, 1.6, 0, 0, 1.4).key(INTRO, -9, 5, 3, 0, 0, 4.5).until(INTRO));
            lifted.addAll(friends(RANGE));
            for (LivingEntity f : lifted) {
                StyleOps.effect(f, MobEffects.LEVITATION, LIFT, 1);
                StyleOps.effect(f, MobEffects.SLOW_FALLING, TICKS, 0);
                StyleOps.effect(f, MobEffects.REGENERATION, TICKS, 0);
                StyleOps.effect(f, MobEffects.DAMAGE_RESISTANCE, TICKS, 0);
                piece(Piece.FLIGHT_ULT_WINGS, f.position(), f.position(), f.getBbHeight(), f, 0);
            }
            sound(player.position(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.4F, 1.1F);
        }

        @Override protected void beat(int age) {
            if (age % 60 == 0 && age < TICKS)
                for (LivingEntity f : lifted) if (f.isAlive()) piece(Piece.FLIGHT_ULT_WINGS, f.position(), f.position(), f.getBbHeight(), f, age / 60);
        }

        @Override protected void incoming(LivingIncomingDamageEvent event, LivingEntity victim) {
            if (lifted.contains(victim) && event.getSource().getDirectEntity() instanceof Projectile) event.setAmount(event.getAmount() * .5F);
        }

        public List<LivingEntity> lifted() { return lifted; }
    }
}
