package com.jayptucker.heroesevolved.amplify;

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
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Power Amplification's three Ultimates, in red lightning; {@code AmpArt} draws them from these beats. */
public final class AmpUltimates {
    private AmpUltimates() {}

    public static final Ultimate OFFENSE = Ultimate.offense(FeedbackLoop::new, 8);
    public static final Ultimate DEFENSE = Ultimate.defense(RedArmor::new);
    public static final Ultimate SUPPORT = Ultimate.support(EveryoneNow::new);

    private static final String POWER = "power_amplification";

    // ------------------------------------------------------------------
    // Offense: Feedback Loop
    // ------------------------------------------------------------------

    /**
     * He takes the foe by the wrist and turns its own life up until it can't hold it: red lightning crawls up it and it
     * convulses, again and again. He draws the overflow out between his palms into a ball of red lightning that grows
     * until it spits, and drives it back into the foe's chest; the burst forks out into everyone near.
     */
    public static final class FeedbackLoop extends Run {
        public static final int GRAB = 8, SURGE = 12, JOLTS = 5, EVERY = 8, DRAW = 52, DRIVE = 72, TICKS = 110;

        public FeedbackLoop(ServerPlayer p, int tier, LivingEntity foe) { super(p, tier, POWER, "amp_lightning_cannon", foe); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            face(foeCentre());
            cue();
            pose(ArmPose.AMP_ZAP, GRAB + 4);
            piece(Piece.AMP_ULT_FEEDBACK, origin, base, foe.getBbHeight());
            double d = Math.sqrt(base.subtract(origin).horizontalDistanceSqr());
            if (d > 2.4) glide(origin, origin.lerp(base, (d - 1.6) / d));
            cinema(cam()
                    .key(0, -2.4, 2.2, 1.8, d, 0, 1.4)
                    .key(SURGE + 10, d - 3.2, 3, 1.5, d, 0, 1.6)
                    .key(DRAW, -1.8, -2.6, 1.6, d * .5, 0, 1.4)
                    .key(DRIVE + 2, d * .5, 4, 2.2, d * .6, 0, 1.4)
                    .key(TICKS - 14, d - 6, 5.5, 3, d, 0, 1)
                    .until(TICKS - 10));
        }

        @Override protected void beat(int age) {
            if (age == 1) still();
            if (age == GRAB) sound(foeCentre(), SoundEvents.BEACON_POWER_SELECT, 1.2F, .6F);
            if (age >= SURGE && age < SURGE + JOLTS * EVERY && (age - SURGE) % EVERY == 0) {
                zap(foe, 1.5F);
                Takedowns.flinch(foe);
                sound(foeCentre(), SoundEvents.LIGHTNING_BOLT_IMPACT, .8F, 1.5F + (age - SURGE) * .01F);
            }
            if (age == DRAW) { pose(ArmPose.AMP_CUP, DRIVE - DRAW); sound(player.position(), SoundEvents.BEACON_AMBIENT, 1.4F, 1.6F); }
            if (age == DRIVE) {
                pose(ArmPose.AMP_THROW, 10);
                zap(foe, 14);
                List<LivingEntity> near = others(foeAt(), 7);
                for (int i = 0; i < Math.min(4, near.size()); i++) {
                    zap(near.get(i), 5);
                    piece(Piece.AMP_DISCHARGE, foeCentre(), near.get(i).getBoundingBox().getCenter(), 1, null, i);
                }
                sound(foeCentre(), SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2F, 1.4F);
                sound(foeCentre(), SoundEvents.GENERIC_EXPLODE.value(), .9F, 1.5F);
                shake(foeAt(), 20, .45F, 8);
                release();
            }
        }
    }

    // ------------------------------------------------------------------
    // Defense: Red Armor
    // ------------------------------------------------------------------

    /**
     * He turns his own power all the way up: twelve seconds supercharged, red lightning running over him like armour.
     * Blows land two-fifths lighter, and each one throws a fork back into whoever struck; he is quicker on his feet. When
     * it burns out, it burns out into the nearest foes.
     */
    public static final class RedArmor extends Run {
        public static final int TICKS = 240, INTRO = 22;
        private final Map<UUID, Integer> forked = new HashMap<>();

        public RedArmor(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "amp_lights_back_on", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.AMP_RAISE, 22);
            heroShot(INTRO, .5);
            Amplification.surge(player, player, TICKS);
            StyleOps.effect(player, MobEffects.MOVEMENT_SPEED, TICKS, 0);
            piece(Piece.AMP_ULT_ARMOR, player.position(), player.position(), player.getBbHeight(), player, 0);
            sound(player.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, .7F, 1.6F);
        }

        @Override protected void beat(int age) {
            if (age % 20 == 0 && age < TICKS) piece(Piece.AMP_ULT_ARMOR, player.position(), player.position(), player.getBbHeight(), player, age / 20);
        }

        @Override protected void incoming(LivingIncomingDamageEvent event, LivingEntity victim) {
            if (victim != player || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
            event.setAmount(event.getAmount() * .6F);
            if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == player || !StyleOps.isFoe(player, attacker)) return;
            int last = forked.getOrDefault(attacker.getUUID(), -100);
            if (age - last < 20) return;
            forked.put(attacker.getUUID(), age);
            zap(attacker, 2);
            piece(Piece.AMP_DISCHARGE, player.getBoundingBox().getCenter(), attacker.getBoundingBox().getCenter(), 1, null, age);
        }

        @Override protected void finish() {
            List<LivingEntity> near = StyleOps.foes(player, player.position(), 8);
            for (int i = 0; i < Math.min(4, near.size()); i++) {
                zap(near.get(i), 3);
                piece(Piece.AMP_DISCHARGE, player.getBoundingBox().getCenter(), near.get(i).getBoundingBox().getCenter(), 1, null, i);
            }
            sound(player.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 1, 1.3F);
        }
    }

    // ------------------------------------------------------------------
    // Support: Everyone, Now
    // ------------------------------------------------------------------

    /**
     * A red thread runs from him to every friend within fourteen blocks, and every one of them is supercharged for twelve
     * seconds: their powers stronger and further-reaching, and themselves quicker and harder-hitting.
     */
    public static final class EveryoneNow extends Run {
        public static final int TICKS = 240, INTRO = 22;
        public static final double RANGE = 14;
        private final List<LivingEntity> charged = new ArrayList<>();

        public EveryoneNow(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "amp_got_your_back", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.AMP_SHOW, 22);
            heroShot(INTRO, 1);
            charged.addAll(friends(RANGE));
            for (LivingEntity f : charged) {
                Amplification.surge(player, f, TICKS);
                StyleOps.effect(f, MobEffects.MOVEMENT_SPEED, TICKS, 0);
                StyleOps.effect(f, MobEffects.DAMAGE_BOOST, TICKS, 0);
                piece(Piece.AMP_ULT_LINK, player.position(), f.position(), f.getBbHeight(), f, player.getId());
            }
            sound(player.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, .8F, 1.4F);
        }

        @Override protected void beat(int age) {
            if (age % 60 == 0 && age < TICKS)
                for (LivingEntity f : charged) if (f.isAlive()) piece(Piece.AMP_ULT_LINK, player.position(), f.position(), f.getBbHeight(), f, player.getId());
        }

        public List<LivingEntity> charged() { return charged; }
    }
}
