package com.jayptucker.heroesevolved.hydro;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Ultimates.Run;
import com.jayptucker.heroesevolved.combat.Ultimates.Ultimate;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Hydrokinesis's three Ultimates; {@code HydroArt} draws them from these beats. Like the rest of the kit they never
 * refuse for want of water: a wave this size is pulled out of the air if there is no water to hand.
 */
public final class HydroUltimates {
    private HydroUltimates() {}

    public static final Ultimate OFFENSE = Ultimate.offense(RiptideBarrel::new, 10);
    public static final Ultimate DEFENSE = Ultimate.defense(TidalCitadel::new);
    public static final Ultimate SUPPORT = Ultimate.support(Monsoon::new);

    private static final String POWER = "hydrokinesis";

    // ------------------------------------------------------------------
    // Offense: Riptide Barrel
    // ------------------------------------------------------------------

    /**
     * A wave rises behind the waterbender and they ride it in; its lip curls over the foe and the barrel closes,
     * tumbling it round and round inside the water. A geyser bursts up under it and throws it clear, two whips crack
     * overhead, wrap it, and slam it down into a whirlpool that drags it into the ground.
     */
    public static final class RiptideBarrel extends Run {
        public static final int RISE = 14, CLOSE = 36, TUMBLE = 40, EVERY = 4, TUMBLES = 6, GEYSER = 68, WHIPS = 82,
                WRAP = 88, SLAM = 92, TICKS = 120;
        public static final double CREST = 7, THROW = 8;

        public RiptideBarrel(ServerPlayer p, int tier, LivingEntity foe) { super(p, tier, POWER, "hydro_tsunami", foe); }

        @Override protected int length() { return TICKS; }

        private Vec3 front() { return around(base, -2.2, 0, 0); }

        public static double height(float t) {
            if (t < TUMBLE) return 0;
            if (t < GEYSER) return 1.4;
            if (t < WRAP) return 1.4 + (THROW - 1.4) * (1 - Math.pow(1 - Math.min(1, (t - GEYSER) / 8F), 3));
            if (t < SLAM) return THROW;
            return Math.max(0, THROW * (1 - (t - SLAM) / 4F));
        }

        @Override protected void open() {
            face(foeCentre());
            cue();
            pose(ArmPose.WATER_RAISE, RISE);
            piece(Piece.HYDRO_ULT_BARREL, origin, base, 1);
            sound(player.position(), SoundEvents.GENERIC_SPLASH, 1.4F, .5F);
            double d = Math.sqrt(base.subtract(origin).horizontalDistanceSqr());
            cinema(cam()
                    .key(0, -5, 3.5, 2.6, d * .5, 0, 3)
                    .key(RISE + 6, -2, -4, 3.5, d, 0, 1.5)
                    .key(CLOSE + 4, d - 1.2, -2.5, 1.6, d, 0, 1.6)
                    .watch(GEYSER + 2, d - 6, 5, 2, foe)
                    .watch(WRAP, d - 7, -5, 6, foe)
                    .key(SLAM + 6, d - 7, 5, 4.5, d, 0, 0)
                    .key(TICKS - 14, d - 9, 6.5, 5, d, 0, .5)
                    .until(TICKS - 10));
        }

        @Override protected void beat(int age) {
            if (age <= RISE) {
                if (age == 1) hover(true);
                glide(origin.add(0, 1.2 * (age - 1) / RISE, 0), origin.add(0, 1.2 * age / RISE, 0));
            }
            if (age > RISE && age <= CLOSE) {
                float k0 = (age - 1 - RISE) / (float) (CLOSE - RISE), k1 = (age - RISE) / (float) (CLOSE - RISE);
                glide(origin.add(0, 1.2, 0).lerp(front().add(0, .2, 0), k0), origin.add(0, 1.2, 0).lerp(front().add(0, .2, 0), k1));
                if (age == RISE + 1) pose(ArmPose.WATER_PUSH, 20);
                if (age % 6 == 0) sound(player.position(), SoundEvents.BOAT_PADDLE_WATER, 1.2F, .6F);
            }
            if (age == CLOSE) {
                still();
                hover(false);
                sound(foeCentre(), SoundEvents.GENERIC_SPLASH, 2, .6F);
                for (LivingEntity t : others(base, 4.5)) {
                    HydroService.hit(player, t, 6);
                    HydroService.soak(t, 160);
                    shove(t, base, 1.2, .4);
                }
            }
            if (age >= TUMBLE) {
                lift(height(age));
                if (age < GEYSER && foe != null && held()) foe.setYRot(foe.getYRot() + 31);
            }
            if (age >= TUMBLE && age < TUMBLE + TUMBLES * EVERY && (age - TUMBLE) % EVERY == 0) {
                HydroService.hit(player, foe, 1.5F);
                HydroService.soak(foe, 200);
                Takedowns.flinch(foe);
                sound(foeCentre(), SoundEvents.PLAYER_SPLASH, .9F, .8F + (age - TUMBLE) * .02F);
            }
            if (age == GEYSER) {
                HydroService.hit(player, foe, 6);
                sound(base, SoundEvents.GENERIC_SPLASH, 2, .4F);
                shake(base, 16, .3F, 6);
            }
            if (age == WHIPS) {
                pose(ArmPose.WATER_WHIP, 12);
                sound(player.position(), SoundEvents.TRIDENT_RIPTIDE_2.value(), 1.4F, 1.2F);
            }
            if (age == WRAP) sound(foeCentre(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.2F, .8F);
            if (age == SLAM + 4) {
                HydroService.hit(player, foe, 10);
                HydroService.soak(foe, 240);
                for (LivingEntity t : others(base, 4)) { HydroService.hit(player, t, 3); HydroService.soak(t, 160); }
                sound(base, SoundEvents.GENERIC_SPLASH, 2, .5F);
                sound(base, SoundEvents.GENERIC_EXPLODE.value(), .8F, 1.4F);
                shake(base, 22, .45F, 10);
                release();
            }
        }

        @Override protected void tidy(boolean broken) { hover(false); }
    }

    // ------------------------------------------------------------------
    // Defense: Tidal Citadel
    // ------------------------------------------------------------------

    /**
     * A dome of moving water six blocks across stands where the waterbender is for twelve seconds. Shots that cross it
     * slow and sink; foes who wade in are swept back out by its current and soaked; fire goes out for anyone inside, who
     * mend and take a third less harm. When it ends the dome falls outward as a ring of surf.
     */
    public static final class TidalCitadel extends Run {
        public static final int TICKS = 240, INTRO = 22;
        public static final double RADIUS = 6;
        private Vec3 centre;

        public TidalCitadel(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "hydro_tidal_dome", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            centre = player.position();
            cue();
            pose(ArmPose.WATER_RAISE, 20);
            heroShot(INTRO, 1.5);
            piece(Piece.HYDRO_ULT_CITADEL, centre, centre, (float) RADIUS, null, 0);
            sound(centre, SoundEvents.GENERIC_SPLASH, 1.6F, .5F);
        }

        private boolean inside(LivingEntity e) { return e.position().distanceToSqr(centre) <= RADIUS * RADIUS; }

        @Override protected void beat(int age) {
            if (age % 20 == 0 && age < TICKS) {
                piece(Piece.HYDRO_ULT_CITADEL, centre, centre, (float) RADIUS, null, age / 20);
                sound(centre, SoundEvents.WATER_AMBIENT, 1.2F, .8F);
                for (LivingEntity f : StyleOps.friends(player, centre, RADIUS)) { StyleOps.heal(f, 1); f.clearFire(); }
            }
            for (Projectile shot : shots(centre.add(0, 1.5, 0), RADIUS + 1)) {
                if (shot.position().distanceToSqr(centre) > (RADIUS + .5) * (RADIUS + .5)) continue;
                piece(Piece.HYDRO_ULT_SPLASH, shot.position(), shot.position(), .6F, null, age);
                shot.discard();
            }
            if (age % 5 == 0) for (LivingEntity t : StyleOps.foes(player, centre, RADIUS - .5)) {
                shove(t, centre, .9, .2);
                HydroService.soak(t, 120);
            }
        }

        @Override protected void incoming(LivingIncomingDamageEvent event, LivingEntity victim) {
            if (centre == null || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
            if (StyleOps.isFriend(player, victim) && inside(victim)) event.setAmount(event.getAmount() * .67F);
        }

        @Override protected void finish() {
            for (LivingEntity t : StyleOps.foes(player, centre, RADIUS + 3)) {
                HydroService.hit(player, t, 4);
                HydroService.soak(t, 200);
                shove(t, centre, 1.4, .4);
            }
            piece(Piece.HYDRO_ULT_CITADEL, centre, centre, (float) RADIUS, null, -1);
            sound(centre, SoundEvents.GENERIC_SPLASH, 2, .6F);
        }

        public Vec3 centre() { return centre; }
    }

    // ------------------------------------------------------------------
    // Support: Monsoon
    // ------------------------------------------------------------------

    /**
     * Clouds gather over fourteen blocks and the rain comes down for fifteen seconds. Friends under it mend, are put out
     * and cleansed; foes under it are soaked and slowed. Twice, a spring bursts up under every friend and heals them.
     */
    public static final class Monsoon extends Run {
        public static final int TICKS = 300, INTRO = 26, SPRING_ONE = 100, SPRING_TWO = 200;
        public static final double RADIUS = 14;
        private Vec3 centre;
        private final Set<UUID> cleansed = new HashSet<>();

        public Monsoon(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "hydro_healing_spring", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            centre = player.position();
            cue();
            pose(ArmPose.WATER_RAISE, 24);
            cinema(cam().key(0, -3, 1.6, 2, 0, 0, 1.4).key(INTRO / 2, -7, 4, 6, 0, 0, 5).key(INTRO, -10, 6, 9, 0, 0, 7).until(INTRO));
            piece(Piece.HYDRO_ULT_MONSOON, centre, centre, (float) RADIUS, null, 0);
            sound(centre, SoundEvents.LIGHTNING_BOLT_THUNDER, .5F, .8F);
        }

        @Override protected void beat(int age) {
            if (age % 20 == 0 && age < TICKS) {
                piece(Piece.HYDRO_ULT_MONSOON, centre, centre, (float) RADIUS, null, age / 20);
                sound(centre, SoundEvents.WEATHER_RAIN, 1.2F, 1);
                for (LivingEntity f : StyleOps.friends(player, centre, RADIUS)) {
                    StyleOps.heal(f, 1.2F);
                    f.clearFire();
                    if (cleansed.add(f.getUUID())) StyleOps.cleanse(f);
                }
                for (LivingEntity t : StyleOps.foes(player, centre, RADIUS)) {
                    HydroService.soak(t, 60);
                    StyleOps.effect(t, MobEffects.MOVEMENT_SLOWDOWN, 40, 0);
                }
            }
            if (age == SPRING_ONE || age == SPRING_TWO) for (LivingEntity f : StyleOps.friends(player, centre, RADIUS)) {
                StyleOps.heal(f, 4);
                piece(Piece.HYDRO_ULT_SPRING, f.position(), f.position(), f.getBbHeight(), f, age);
                sound(f.position(), SoundEvents.GENERIC_SPLASH, 1, 1.3F);
            }
        }

        public Vec3 centre() { return centre; }
    }
}
