package com.jayptucker.heroesevolved.electro;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Ultimates.Run;
import com.jayptucker.heroesevolved.combat.Ultimates.Ultimate;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import com.jayptucker.heroesevolved.weapons.Jamming;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
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

/** Electrokinesis's three Ultimates; {@code ElectroArt} draws them from these beats. */
public final class ElectroUltimates {
    private ElectroUltimates() {}

    public static final Ultimate OFFENSE = Ultimate.offense(Thunderhead::new, 12);
    public static final Ultimate DEFENSE = Ultimate.defense(StormShell::new);
    public static final Ultimate SUPPORT = Ultimate.support(Restart::new);

    private static final String POWER = "electrokinesis";

    // ------------------------------------------------------------------
    // Offense: Thunderhead
    // ------------------------------------------------------------------

    /**
     * A line of current lashes out and reels the foe in; a charged uppercut and both of them ride a bolt up into a storm
     * cloud boiling overhead, where five bolts strike it from every side. Then a single colossal bolt drives them both
     * back down: the electrokinetic lands on one knee, a scorched star burns into the ground, and the current leaps on
     * into everyone standing near.
     */
    public static final class Thunderhead extends Run {
        public static final int LASH = 4, REELED = 10, UPPER = 12, CLOUD = 28, STRIKES = 30, EVERY = 8, BOLTS = 5,
                RIDE = 70, SLAM = 76, TICKS = 120;
        public static final double CLOUD_HEIGHT = 14, HANG = 11;

        public Thunderhead(ServerPlayer p, int tier, LivingEntity foe) { super(p, tier, POWER, "tesla_spire", foe); }

        @Override protected int length() { return TICKS; }

        /** Where the reeled foe ends up: just in front of the caster. */
        public static Vec3 reeled(Vec3 origin, Vec3 facing) { return origin.add(facing.scale(1.8)); }

        /** The foe's height over where it was reeled to, by tick. */
        public static double height(float t) {
            if (t < UPPER) return 0;
            if (t < RIDE) return HANG * (1 - Math.pow(1 - Math.min(1, (t - UPPER) / (CLOUD - UPPER)), 3));
            return HANG * Math.max(0, 1 - (t - RIDE) / (SLAM - RIDE));
        }

        @Override protected void open() {
            face(foeCentre());
            cue();
            pose(ArmPose.ELECTRO_HAUL, 10);
            piece(Piece.ELECTRO_ULT_STORM, origin, base, 1);
            sound(player.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, .6F, 1.6F);
            cinema(cam()
                    .key(0, -2.6, 2.4, 1.8, 2.5, 0, 1.2)
                    .key(UPPER, -1.5, 4.2, 1, 1.8, 0, 2.5)
                    .key(CLOUD, -4, 6, 6, 1.8, 0, HANG + 1)
                    .key(STRIKES + 20, -6.5, -5.5, HANG + 3, 1.8, 0, HANG + 1)
                    .key(RIDE - 2, -7, 7, HANG + 6, 1.8, 0, HANG)
                    .key(SLAM + 2, -8, 6, 3.5, 1.8, 0, .4)
                    .key(TICKS - 14, -10, 8, 4.5, 1.8, 0, .8)
                    .until(TICKS - 10));
        }

        @Override protected void beat(int age) {
            Vec3 spot = reeled(origin, facing);
            if (age == LASH) {
                ElectroService.shock(player, foe, 2);
                sound(foeCentre(), SoundEvents.TRIDENT_RIPTIDE_1.value(), 1, 1.4F);
            }
            if (age > LASH && age <= REELED) place(base.lerp(spot, (age - LASH) / (float) (REELED - LASH)));
            if (age == UPPER) {
                face(foeCentre().add(0, 2, 0));
                pose(ArmPose.ELECTRO_JAB, 6);
                ElectroService.shock(player, foe, 2);
                Takedowns.flinch(foe);
                hover(true);
                sound(foeCentre(), SoundEvents.LIGHTNING_BOLT_IMPACT, 1.2F, 1.3F);
            }
            if (age >= UPPER && age < SLAM + 1) place(new Vec3(spot.x, Math.max(base.y, spot.y) + height(age), spot.z));
            if (age > UPPER && age <= CLOUD) {
                float k0 = (age - 1 - UPPER) / (float) (CLOUD - UPPER), k1 = (age - UPPER) / (float) (CLOUD - UPPER);
                glide(origin.add(0, (HANG - .6) * ease(k0), 0), origin.add(0, (HANG - .6) * ease(k1), 0));
            }
            if (age == CLOUD + 1) still();
            if (age >= STRIKES && age < STRIKES + BOLTS * EVERY && (age - STRIKES) % EVERY == 0) {
                ElectroService.shock(player, foe, 2);
                Takedowns.flinch(foe);
                sound(foeCentre(), SoundEvents.LIGHTNING_BOLT_IMPACT, 1.4F, .9F + (age - STRIKES) * .01F);
                sound(foeCentre(), SoundEvents.LIGHTNING_BOLT_THUNDER, .5F, 1.4F);
            }
            if (age == RIDE) {
                pose(ArmPose.ELECTRO_RIDE, SLAM - RIDE + 4);
                sound(foeCentre(), SoundEvents.LIGHTNING_BOLT_THUNDER, 2, .6F);
            }
            if (age > RIDE && age <= SLAM) {
                float k0 = (age - 1 - RIDE) / (float) (SLAM - RIDE), k1 = (age - RIDE) / (float) (SLAM - RIDE);
                glide(origin.add(0, (HANG - .6) * (1 - k0), 0), origin.add(0, (HANG - .6) * (1 - k1), 0));
            }
            if (age == SLAM) {
                hover(false);
                still();
                pose(ArmPose.ELECTRO_SLAM, 16);
                ElectroService.shock(player, foe, 12);
                Jamming.jam(foe, 60, player);
                List<LivingEntity> arced = new ArrayList<>(others(spot, 7));
                for (int i = 0; i < Math.min(4, arced.size()); i++) {
                    LivingEntity t = arced.get(i);
                    ElectroService.shock(player, t, 4);
                    Jamming.jam(t, 60, player);
                    piece(Piece.ELECTRO_ULT_ARC, spot.add(0, .2, 0), t.getBoundingBox().getCenter(), 1, null, i);
                }
                sound(spot, SoundEvents.LIGHTNING_BOLT_IMPACT, 2, .6F);
                sound(spot, SoundEvents.GENERIC_EXPLODE.value(), 1, 1.2F);
                shake(spot, 28, .6F, 12);
                release();
            }
        }

        private static float ease(float k) { return k * k * (3 - 2 * k); }

        @Override protected void tidy(boolean broken) { hover(false); }
    }

    // ------------------------------------------------------------------
    // Defense: Storm Shell
    // ------------------------------------------------------------------

    /**
     * Twelve seconds inside a shell of crawling lightning four blocks across. Shots that cross it are struck out of the
     * air; anyone who lands a blow is shocked, thrown out and has their gun jammed; blows that get through are a third
     * lighter. Every strike charges the shell, and when it ends it pours that charge into the nearest foes.
     */
    public static final class StormShell extends Run {
        public static final int TICKS = 240, INTRO = 22, MAX_CHARGE = 10;
        public static final double RADIUS = 4;
        private int charge;
        private final Map<UUID, Integer> struck = new HashMap<>();

        public StormShell(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "faraday_cage", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.ELECTRO_CALL, 20);
            heroShot(INTRO, .5);
            piece(Piece.ELECTRO_ULT_SHELL, player.position(), player.position(), (float) RADIUS, player, 0);
            sound(player.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, .8F, 1.5F);
            sound(player.position(), SoundEvents.BEACON_ACTIVATE, 1, 1.8F);
        }

        @Override protected void beat(int age) {
            if (age % 20 == 0 && age < TICKS) piece(Piece.ELECTRO_ULT_SHELL, player.position(), player.position(), (float) RADIUS, player, age / 20);
            Vec3 body = player.getBoundingBox().getCenter();
            for (Projectile shot : shots(body, RADIUS + .5)) {
                piece(Piece.ELECTRO_ULT_ARC, body, shot.position(), .6F, null, age);
                shot.discard();
                charge = Math.min(MAX_CHARGE, charge + 1);
                sound(shot.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, .4F, 2);
            }
        }

        @Override protected void incoming(LivingIncomingDamageEvent event, LivingEntity victim) {
            if (victim != player || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
            event.setAmount(event.getAmount() * .65F);
            if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == player || !StyleOps.isFoe(player, attacker)
                    || attacker.distanceToSqr(player) > (RADIUS + 1.5) * (RADIUS + 1.5)) return;
            int last = struck.getOrDefault(attacker.getUUID(), -100);
            if (age - last < 20) return;
            struck.put(attacker.getUUID(), age);
            ElectroService.shock(player, attacker, 2);
            Jamming.jam(attacker, 80, player);
            shove(attacker, player.position(), 1.2, .4);
            charge = Math.min(MAX_CHARGE, charge + 2);
            piece(Piece.ELECTRO_ULT_ARC, player.getBoundingBox().getCenter(), attacker.getBoundingBox().getCenter(), 1, null, age);
            sound(attacker.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, .8F, 1.6F);
        }

        @Override protected void finish() {
            Vec3 body = player.getBoundingBox().getCenter();
            List<LivingEntity> near = StyleOps.foes(player, player.position(), 10);
            for (int i = 0; i < Math.min(5, near.size()); i++) {
                LivingEntity t = near.get(i);
                ElectroService.shock(player, t, 2 + charge * .8F);
                piece(Piece.ELECTRO_ULT_ARC, body, t.getBoundingBox().getCenter(), 1.4F, null, i);
            }
            piece(Piece.ELECTRO_ULT_SHELL, player.position(), player.position(), (float) RADIUS, player, -1);
            sound(player.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2F, 1.2F);
            shake(player.position(), 14, .3F, 8);
        }

        public int charge() { return charge; }
    }

    // ------------------------------------------------------------------
    // Support: Restart
    // ------------------------------------------------------------------

    /**
     * A clap of the hands sends a line of current racing out through every friend within sixteen blocks, beating like a
     * heart: three beats, and on the third everyone it touches is jolted back to strength. A big share of their health,
     * their harmful effects gone, stamina back, quick on their feet and hard-hitting for twelve seconds.
     */
    public static final class Restart extends Run {
        public static final int BEAT = 10, EVERY = 15, BEATS = 3, TICKS = 60;
        public static final double RANGE = 16;
        private final List<LivingEntity> linked = new ArrayList<>();

        public Restart(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "jumpstart", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.ELECTRO_CLAP, 14);
            heroShot(BEAT + EVERY * 2 + 6, 1);
            linked.addAll(friends(RANGE));
            for (int i = 0; i < linked.size(); i++) {
                LivingEntity f = linked.get(i);
                piece(Piece.ELECTRO_ULT_PULSE, player.position(), f.position(), f.getBbHeight(), f, i);
            }
            sound(player.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 1, 1.8F);
        }

        @Override protected void beat(int age) {
            if (age < BEAT || (age - BEAT) % EVERY != 0) return;
            int n = (age - BEAT) / EVERY;
            if (n >= BEATS) return;
            sound(player.position(), SoundEvents.WARDEN_HEARTBEAT, 1.4F, 1.2F);
            sound(player.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, .5F + n * .25F, 1.8F - n * .2F);
            if (n < BEATS - 1) return;
            for (LivingEntity f : linked) {
                if (!f.isAlive() || f.level() != level) continue;
                StyleOps.heal(f, f.getMaxHealth() * .4F);
                StyleOps.cleanse(f);
                StyleOps.effect(f, MobEffects.MOVEMENT_SPEED, 240, 1);
                StyleOps.effect(f, MobEffects.DIG_SPEED, 240, 1);
                StyleOps.effect(f, MobEffects.DAMAGE_BOOST, 240, 0);
                if (f instanceof ServerPlayer friend) PlayerEnergyService.restore(friend, 25);
            }
            shake(player.position(), 16, .25F, 6);
        }

        public List<LivingEntity> linked() { return linked; }
    }
}
