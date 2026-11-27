package com.jayptucker.heroesevolved.sonic;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Ultimates.Run;
import com.jayptucker.heroesevolved.combat.Ultimates.Ultimate;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/** Sonic Resonance's three Ultimates; {@code SonicArt} draws them from these beats. */
public final class SonicUltimates {
    private SonicUltimates() {}

    public static final Ultimate OFFENSE = Ultimate.offense(FinalMovement::new, 10);
    public static final Ultimate DEFENSE = Ultimate.defense(DeadAir::new);
    public static final Ultimate SUPPORT = Ultimate.support(Anthem::new);

    private static final String POWER = "sonic_resonance";

    // ------------------------------------------------------------------
    // Offense: Final Movement
    // ------------------------------------------------------------------

    /**
     * She hums, and five lines of a stave close round the foe and lift it off the ground. Then she sings the scale: five
     * notes, each a ring of colour that rings the foe through and cracks. She draws a breath, the stave pulls tight, and
     * she screams: a cone of sound shatters the cage and throws the foe twelve blocks.
     */
    public static final class FinalMovement extends Run {
        public static final int CAGED = 12, NOTE = 14, EVERY = 12, NOTES = 5, BREATH = 68, SCREAM = 82, THROWN = 88, TICKS = 120;
        public static final double THROW = 12, LIFT = 1.4;

        public FinalMovement(ServerPlayer p, int tier, LivingEntity foe) { super(p, tier, POWER, "resonance_cascade", foe); }

        @Override protected int length() { return TICKS; }

        public Vec3 landing() { return base.add(facing.scale(THROW)); }

        @Override protected void open() {
            face(foeCentre());
            cue();
            pose(ArmPose.SONIC_HUM, CAGED + 2);
            piece(Piece.SONIC_ULT_FINAL, origin, base, foe.getBbHeight());
            sound(player.position(), SoundEvents.NOTE_BLOCK_HARP.value(), 1.2F, .5F);
            double d = Math.sqrt(base.subtract(origin).horizontalDistanceSqr());
            cinema(cam()
                    .key(0, -2.4, 2.6, 1.7, d, 0, 1.3)
                    .key(NOTE + 20, d - 3.2, 3.4, 2.2, d, 0, 2.2)
                    .key(BREATH, -1.4, -2.4, 1.7, 0, 0, 1.5)
                    .key(SCREAM + 2, -4, -5, 2.5, d + 4, 0, 1.5)
                    .key(TICKS - 14, d - 2, 8, 4, d + THROW * .6, 0, 1)
                    .until(TICKS - 10));
        }

        @Override protected void beat(int age) {
            if (age <= SCREAM) lift(LIFT * Math.min(1, age / (float) CAGED));
            if (age == CAGED) sound(foeCentre(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4F, .6F);
            if (age >= NOTE && age < NOTE + NOTES * EVERY && (age - NOTE) % EVERY == 0) {
                int i = (age - NOTE) / EVERY;
                pose(ArmPose.SONIC_SING, 10);
                SonicService.hit(player, foe, 3);
                Takedowns.flinch(foe);
                sound(foeCentre(), SoundEvents.NOTE_BLOCK_BELL.value(), 1.6F, (float) Math.pow(2, (new int[]{0, 2, 4, 5, 7})[i] / 12.0));
                sound(foeCentre(), SoundEvents.AMETHYST_BLOCK_BREAK, 1, 1.2F + i * .1F);
            }
            if (age == BREATH) {
                pose(ArmPose.SONIC_GATHER, SCREAM - BREATH);
                sound(player.position(), SoundEvents.BREEZE_INHALE, 1.2F, 1.2F);
            }
            if (age == SCREAM) {
                pose(ArmPose.SONIC_SCREAM, 14);
                SonicService.hit(player, foe, 10);
                Vec3 mouth = player.getEyePosition();
                for (LivingEntity t : others(base, 10)) {
                    Vec3 to = t.getBoundingBox().getCenter().subtract(mouth);
                    if (to.normalize().dot(facing) < .8) continue;
                    SonicService.hit(player, t, 6);
                    StyleOps.effect(t, MobEffects.WEAKNESS, 120, 0);
                    StyleOps.effect(t, MobEffects.MOVEMENT_SLOWDOWN, 80, 1);
                    shove(t, player.position(), 1.4, .4);
                }
                sound(player.position(), SoundEvents.WARDEN_SONIC_BOOM, 1.6F, 1.3F);
                sound(foeCentre(), SoundEvents.GLASS_BREAK, 2, .6F);
                shake(player.position(), 24, .45F, 10);
            }
            if (age > SCREAM && age <= THROWN) {
                float k = (age - SCREAM) / (float) (THROWN - SCREAM);
                place(base.lerp(landing(), k).add(0, LIFT * (1 - k) + Math.sin(k * Math.PI) * 1.5, 0));
            }
            if (age == THROWN) { slam(); release(); }
        }
    }

    // ------------------------------------------------------------------
    // Defense: Dead Air
    // ------------------------------------------------------------------

    /**
     * A dome of dead air seven blocks across stands where she is for twelve seconds. Nothing in it makes a sound; shots
     * that come into it lose their speed and fall; and any foe inside has its power hushed (dampened, the way a
     * dampener round does it), weakened and slowed. When the silence breaks, it breaks on them.
     */
    public static final class DeadAir extends Run {
        public static final int TICKS = 240, INTRO = 22;
        public static final double RADIUS = 7;
        private Vec3 centre;

        public DeadAir(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "silence_dome", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            centre = player.position();
            cue();
            pose(ArmPose.SONIC_HUSH, 22);
            heroShot(INTRO, 1.5);
            piece(Piece.SONIC_ULT_DEAD, centre, centre, (float) RADIUS, null, 0);
            sound(centre, SoundEvents.BEACON_DEACTIVATE, 1.4F, .5F);
        }

        @Override protected void beat(int age) {
            if (age % 20 == 0 && age < TICKS) {
                piece(Piece.SONIC_ULT_DEAD, centre, centre, (float) RADIUS, null, age / 20);
                for (LivingEntity t : StyleOps.foes(player, centre, RADIUS)) {
                    t.addEffect(new MobEffectInstance(ModItems.DAMPENED, 40, 0, false, false, true), player);
                    StyleOps.effect(t, MobEffects.WEAKNESS, 40, 1);
                    StyleOps.effect(t, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
                }
            }
            for (Projectile shot : shots(centre.add(0, 1, 0), RADIUS)) {
                Vec3 v = shot.getDeltaMovement();
                if (v.lengthSqr() > .02) {
                    StyleOps.push(shot, v.scale(.15));
                    piece(Piece.SONIC_ULT_HUSHED, shot.position(), shot.position(), .5F, null, age);
                }
            }
        }

        @Override protected void finish() {
            for (LivingEntity t : StyleOps.foes(player, centre, RADIUS)) SonicService.hit(player, t, 4);
            piece(Piece.SONIC_ULT_DEAD, centre, centre, (float) RADIUS, null, -1);
            sound(centre, SoundEvents.BELL_BLOCK, 2, .5F);
        }

        public Vec3 centre() { return centre; }
    }

    // ------------------------------------------------------------------
    // Support: Anthem
    // ------------------------------------------------------------------

    /**
     * She sings, and a five-line stave of light circles everyone within twelve blocks for fifteen seconds. On every
     * chord (each two seconds) they mend, and the song keeps them strong, quick and hard to hurt; the last chord, louder,
     * heals them once more.
     */
    public static final class Anthem extends Run {
        public static final int TICKS = 300, INTRO = 22, CHORD = 40;
        public static final double RANGE = 12;

        public Anthem(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "battle_hymn", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.SONIC_CONDUCT, 30);
            heroShot(INTRO, 1);
            piece(Piece.SONIC_ULT_ANTHEM, player.position(), player.position(), (float) RANGE, player, 0);
            chord(0);
        }

        private void chord(int n) {
            for (LivingEntity f : friends(RANGE)) {
                StyleOps.heal(f, 2);
                StyleOps.effect(f, MobEffects.DAMAGE_BOOST, CHORD + 10, 0);
                StyleOps.effect(f, MobEffects.MOVEMENT_SPEED, CHORD + 10, 0);
                StyleOps.effect(f, MobEffects.DAMAGE_RESISTANCE, CHORD + 10, 0);
            }
            int[] steps = {0, 4, 7, 12, 7, 4, 0, 12};
            sound(player.position(), SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4F, (float) Math.pow(2, steps[n % steps.length] / 12.0 - .5));
            sound(player.position(), SoundEvents.NOTE_BLOCK_HARP.value(), 1.2F, (float) Math.pow(2, steps[(n + 2) % steps.length] / 12.0 - .5));
        }

        @Override protected void beat(int age) {
            if (age % 20 == 0 && age < TICKS) piece(Piece.SONIC_ULT_ANTHEM, player.position(), player.position(), (float) RANGE, player, age / 20);
            if (age % CHORD == 0 && age < TICKS) chord(age / CHORD);
        }

        @Override protected void finish() {
            for (LivingEntity f : friends(RANGE)) StyleOps.heal(f, 6);
            sound(player.position(), SoundEvents.PLAYER_LEVELUP, 1, 1.2F);
        }
    }
}
