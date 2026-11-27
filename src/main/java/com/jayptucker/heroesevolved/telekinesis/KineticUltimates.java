package com.jayptucker.heroesevolved.telekinesis;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Ultimates.Run;
import com.jayptucker.heroesevolved.combat.Ultimates.Ultimate;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.List;

/** Telekinesis's three Ultimates, all the far hand; {@code KineticArt} draws them from these beats. */
public final class KineticUltimates {
    private KineticUltimates() {}

    public static final Ultimate OFFENSE = Ultimate.offense(WreckingHand::new, 14);
    public static final Ultimate DEFENSE = Ultimate.defense(StopEverything::new);
    public static final Ultimate SUPPORT = Ultimate.support(Cradle::new);

    private static final String POWER = "telekinesis";

    // ------------------------------------------------------------------
    // Offense: Wrecking Hand
    // ------------------------------------------------------------------

    /**
     * The far hand closes over the foe and lifts it. Six chunks of the ground tear free and circle it; the hand swings
     * the foe into one, back into another, over the top into a third and round into the last, each a crack of rock.
     * The rubble crushes in round it into a ball, and the hand spikes the ball into the ground, where it bursts.
     */
    public static final class WreckingHand extends Run {
        public static final int GRIP = 8, TEAR = 12, ORBIT = 30, SWING = 34, SLAMS = 4, EVERY = 10, CRUSH = 74, SPIKE = 86,
                LANDED = 90, TICKS = 120;
        public static final double HEIGHT = 4.5, CHUNK_RADIUS = 2.6;

        public WreckingHand(ServerPlayer p, int tier, LivingEntity foe) { super(p, tier, POWER, "implosion", foe); }

        @Override protected int length() { return TICKS; }

        /** Where chunk {@code i} of six circles, {@code t} ticks in, round the point the foe is held over. */
        public static Vec3 chunk(Vec3 base, int i, float t) {
            double a = i * Math.PI / 3 + t * .045;
            double r = CHUNK_RADIUS * (t < CRUSH ? 1 : Math.max(.25, 1 - (t - CRUSH) / 10.0));
            return base.add(Math.cos(a) * r, HEIGHT + Math.sin(a * 2 + i) * .5, Math.sin(a) * r);
        }

        /** Which chunk the {@code n}th swing smashes the foe into. */
        public static int target(int n) { return new int[]{0, 3, 1, 4}[n % 4]; }

        @Override protected void open() {
            face(foeCentre());
            cue();
            pose(ArmPose.KINETIC_PLUCK, 12);
            piece(Piece.KINETIC_ULT_WRECK, origin, base, foe.getBbHeight());
            sound(foeCentre(), SoundEvents.BEACON_DEACTIVATE, 1.2F, .5F);
            double d = Math.sqrt(base.subtract(origin).horizontalDistanceSqr());
            cinema(cam()
                    .key(0, -2.6, 2.2, 1.8, d, 0, 1.5)
                    .key(ORBIT, d - 7.5, 5.5, 4, d, 0, HEIGHT)
                    .watch(SWING + EVERY * 2, d - 8, -6, 5, foe)
                    .key(CRUSH + 4, d - 6, 4, HEIGHT + 1.5, d, 0, HEIGHT)
                    .key(LANDED + 2, d - 8, 6, 3.5, d, 0, .5)
                    .key(TICKS - 14, d - 10, 7, 4.5, d, 0, 1)
                    .until(TICKS - 10));
        }

        @Override protected void beat(int age) {
            Vec3 hold = base.add(0, HEIGHT, 0);
            if (age <= GRIP + 6) lift(HEIGHT * Math.min(1, age / (float) (GRIP + 6)));
            if (age == TEAR) {
                pose(ArmPose.KINETIC_STORM, ORBIT - TEAR + 4);
                sound(base, SoundEvents.DEEPSLATE_BREAK, 1.6F, .5F);
                shake(base, 12, .25F, 8);
            }
            if (age >= SWING && age < SWING + SLAMS * EVERY) {
                int n = (age - SWING) / EVERY, step = (age - SWING) % EVERY;
                Vec3 from = n == 0 ? hold : chunk(base, target(n - 1), SWING + n * EVERY - 1);
                Vec3 to = chunk(base, target(n), age);
                float k = Math.min(1, step / 6F);
                place(from.lerp(to, k * k).subtract(0, foe.getBbHeight() * .5, 0));
                if (step == 0) pose(n % 2 == 0 ? ArmPose.KINETIC_FLING : ArmPose.KINETIC_SLAM, 8);
                if (step == 6) {
                    hit(foe, 2.5F);
                    Takedowns.flinch(foe);
                    sound(to, SoundEvents.STONE_BREAK, 1.6F, .6F + n * .08F);
                    sound(to, SoundEvents.PLAYER_ATTACK_STRONG, 1, .7F);
                    shake(to, 12, .2F, 5);
                }
            }
            if (age >= SWING + SLAMS * EVERY && age < SPIKE) place(hold.subtract(0, foe.getBbHeight() * .5, 0));
            if (age == CRUSH) {
                pose(ArmPose.KINETIC_PIN, SPIKE - CRUSH);
                sound(hold, SoundEvents.GRAVEL_BREAK, 1.6F, .4F);
            }
            if (age == SPIKE) { pose(ArmPose.KINETIC_SLAM, 10); sound(hold, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.4F, .5F); }
            if (age > SPIKE && age <= LANDED) {
                float k = (age - SPIKE) / (float) (LANDED - SPIKE);
                place(hold.lerp(base, k).subtract(0, foe.getBbHeight() * .5 * (1 - k), 0));
            }
            if (age == LANDED) {
                slam();
                hit(foe, 12);
                for (LivingEntity t : others(base, 5)) { hit(t, 6); shove(t, base, 1.2, .5); }
                sound(base, SoundEvents.GENERIC_EXPLODE.value(), 1.5F, .7F);
                sound(base, SoundEvents.DEEPSLATE_BREAK, 2, .4F);
                shake(base, 26, .6F, 12);
                release();
            }
        }
    }

    // ------------------------------------------------------------------
    // Defense: Stop Everything
    // ------------------------------------------------------------------

    /**
     * For ten seconds, everything thrown or fired at the telekinetic stops dead in the air ten blocks out, held by the
     * far hand, and anyone who closes in is held off at arm's length. When it ends, everything held is thrown back at
     * whoever threw it.
     */
    public static final class StopEverything extends Run {
        public static final int TICKS = 200, INTRO = 22;
        public static final double REACH = 10, ARM = 3.5;
        private final List<Projectile> held = new ArrayList<>();

        public StopEverything(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "debris_halo", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.KINETIC_CATCH, TICKS);
            heroShot(INTRO, 1);
            piece(Piece.KINETIC_ULT_STOP, player.position(), player.position(), (float) REACH, player, 0);
            sound(player.position(), SoundEvents.BEACON_ACTIVATE, 1, .6F);
        }

        @Override protected void beat(int age) {
            if (age % 20 == 0 && age < TICKS) piece(Piece.KINETIC_ULT_STOP, player.position(), player.position(), (float) REACH, player, age / 20);
            if (age % 20 == 10 && age < TICKS) pose(ArmPose.KINETIC_CATCH, 30);
            Vec3 body = player.getBoundingBox().getCenter();
            for (Projectile shot : shots(body, REACH)) {
                if (held.contains(shot)) continue;
                if (shot.getDeltaMovement().dot(body.subtract(shot.position())) <= 0) continue;
                held.add(shot);
                piece(Piece.KINETIC_ULT_HELD, shot.position(), body, .5F, shot, 0);
                sound(shot.position(), SoundEvents.AMETHYST_BLOCK_HIT, .7F, 1.6F);
            }
            for (Projectile shot : held) {
                if (shot.isRemoved()) continue;
                shot.setDeltaMovement(Vec3.ZERO);
                shot.setNoGravity(true);
                shot.hurtMarked = true;
            }
            if (age % 4 == 0) for (LivingEntity t : StyleOps.foes(player, player.position(), ARM)) {
                shove(t, player.position(), .9, .1);
                StyleOps.effect(t, MobEffects.MOVEMENT_SLOWDOWN, 20, 3);
            }
        }

        @Override protected void tidy(boolean broken) {
            Vec3 body = player.getBoundingBox().getCenter();
            List<LivingEntity> near = StyleOps.foes(player, player.position(), 20);
            for (Projectile shot : held) {
                if (shot.isRemoved()) continue;
                shot.setNoGravity(false);
                Entity owner = shot.getOwner();
                Entity aim = owner instanceof LivingEntity l && l.isAlive() && l != player ? l : near.isEmpty() ? null : near.getFirst();
                Vec3 dir = aim != null ? aim.getBoundingBox().getCenter().subtract(shot.position()) : shot.position().subtract(body);
                StyleOps.push(shot, dir.normalize().scale(broken ? .3 : 2.2));
                if (!broken) shot.setOwner(player);
            }
            if (!broken && !held.isEmpty()) sound(body, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.4F, .8F);
        }

        public List<Projectile> caught() { return held; }
    }

    // ------------------------------------------------------------------
    // Support: Cradle
    // ------------------------------------------------------------------

    /**
     * Two great hands of bent air gather every friend within twelve blocks to the telekinetic's side and close over
     * them like a cupped pair of palms, for twelve seconds. Shots break on the hands, foes who reach in are swatted back
     * out, and everyone inside mends and takes half the harm.
     */
    public static final class Cradle extends Run {
        public static final int TICKS = 240, INTRO = 24;
        public static final double RANGE = 12, RADIUS = 3.6;
        private Vec3 centre;

        public Cradle(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "gathering_hand", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            centre = player.position();
            cue();
            pose(ArmPose.KINETIC_BECKON, 16);
            heroShot(INTRO, 2);
            List<LivingEntity> friends = new ArrayList<>(friends(RANGE));
            friends.remove(player);
            for (int i = 0; i < friends.size(); i++) {
                LivingEntity f = friends.get(i);
                double a = i * Math.PI * 2 / friends.size();
                Vec3 want = centre.add(Math.cos(a) * 1.6, 0, Math.sin(a) * 1.6);
                if (f.position().distanceToSqr(centre) > RADIUS * RADIUS && SpeedService.safe(f, want)) {
                    piece(Piece.KINETIC_GATHER, f.position(), want, 1, f, player.getId());
                    if (f instanceof ServerPlayer other) com.jayptucker.heroesevolved.combat.StyleHits.blink(other, want);
                    else { f.teleportTo(want.x, want.y, want.z); f.resetFallDistance(); }
                }
            }
            piece(Piece.KINETIC_ULT_CRADLE, centre, centre, (float) RADIUS, null, 0);
            sound(centre, SoundEvents.ENDER_EYE_DEATH, 1.2F, .7F);
        }

        @Override protected void beat(int age) {
            if (age % 20 == 0 && age < TICKS) {
                piece(Piece.KINETIC_ULT_CRADLE, centre, centre, (float) RADIUS, null, age / 20);
                for (LivingEntity f : StyleOps.friends(player, centre, RADIUS)) StyleOps.heal(f, 1);
            }
            for (Projectile shot : shots(centre.add(0, 1.5, 0), RADIUS + 1)) {
                piece(Piece.KINETIC_IMPACT, shot.position(), centre.add(0, 1.5, 0), .6F, null, 0);
                shot.discard();
            }
            if (age % 5 == 0) for (LivingEntity t : StyleOps.foes(player, centre, RADIUS)) {
                hit(t, 2);
                shove(t, centre, 1.1, .3);
                piece(Piece.KINETIC_SWAT_ULT, t.getBoundingBox().getCenter(), centre.add(0, 1.5, 0), 1, null, age);
            }
        }

        @Override protected void incoming(LivingIncomingDamageEvent event, LivingEntity victim) {
            if (centre == null || event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
            if (StyleOps.isFriend(player, victim) && victim.position().distanceToSqr(centre) <= RADIUS * RADIUS) event.setAmount(event.getAmount() * .5F);
        }

        public Vec3 centre() { return centre; }
    }
}
