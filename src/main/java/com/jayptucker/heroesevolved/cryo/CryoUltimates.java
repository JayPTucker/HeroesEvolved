package com.jayptucker.heroesevolved.cryo;

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
 * Cryokinesis's three Ultimates; {@code CryoArt} draws them from these beats. Like every class Ultimate they place no
 * blocks: the citadel's walls and the glacier are drawn, and the server does what they would do.
 */
public final class CryoUltimates {
    private CryoUltimates() {}

    public static final Ultimate OFFENSE = Ultimate.offense(GlacierFall::new, 12);
    public static final Ultimate DEFENSE = Ultimate.defense(WinterCitadel::new);
    public static final Ultimate SUPPORT = Ultimate.support(Hibernation::new);

    private static final String POWER = "cryokinesis";

    // ------------------------------------------------------------------
    // Offense: Glacier Fall
    // ------------------------------------------------------------------

    /**
     * Frost races across the ground and climbs the foe until it stands sealed in a prism of ice. The cryokinetic leaps
     * up and a glacier assembles overhead (its plan first, a few thin lines, then the faceted blocks growing into it);
     * both arms come down and the glacier falls, shattering prism and glacier together. A thread of the foe's warmth
     * rises out of the wreck into the cryokinetic's palm.
     */
    public static final class GlacierFall extends Run {
        public static final int CREEP = 12, SEALED = 16, LEAP = 18, PLAN = 26, BUILT = 44, DROP = 46, IMPACT = 52,
                LANDED = 64, WARMTH = 70, TICKS = 110;
        public static final double LEAP_HEIGHT = 4.5, GLACIER = 9;

        public GlacierFall(ServerPlayer p, int tier, LivingEntity foe) { super(p, tier, POWER, "hailstorm", foe); }

        @Override protected int length() { return TICKS; }

        /** How far the glacier has fallen toward the foe (0 high above, 1 on it). */
        public static float fall(float t) { return t < DROP ? 0 : Math.min(1, (float) Math.pow((t - DROP) / (IMPACT - DROP), 2)); }

        @Override protected void open() {
            face(foeCentre());
            cue();
            pose(ArmPose.CRYO_LANCE, 12);
            piece(Piece.CRYO_ULT_GLACIER, origin, base, foe.getBbHeight());
            sound(player.position(), SoundEvents.PLAYER_HURT_FREEZE, 1.2F, .5F);
            double d = Math.sqrt(base.subtract(origin).horizontalDistanceSqr());
            cinema(cam()
                    .key(0, -3, 2.2, 1.4, d, 0, .2)
                    .key(SEALED, d - 3.5, 3.2, 1.4, d, 0, 1.2)
                    // The swing out stays on the right: crossing to the left took the eye through the caster's head.
                    .key(PLAN, -4.5, 6, 3, d, 0, GLACIER * .6)
                    .key(BUILT, d - 11, 7, 4, d, 0, GLACIER * .55)
                    .key(IMPACT + 2, d - 8, 6, 3.5, d, 0, .8)
                    .key(WARMTH + 14, -2.5, 2.6, 2.2, d * .4, 0, 1.4)
                    .until(TICKS - 10));
        }

        @Override protected void beat(int age) {
            if (age == SEALED) {
                CryoService.chill(foe, 60, player);
                foe.invulnerableTime = 0;
                CryoService.coldHit(player, foe, 3);
                Takedowns.flinch(foe);
                sound(foeCentre(), SoundEvents.GLASS_PLACE, 1.4F, .6F);
                sound(foeCentre(), SoundEvents.PLAYER_HURT_FREEZE, 1, .8F);
            }
            if (age == LEAP) { hover(true); pose(ArmPose.CRYO_WEAVE, DROP - LEAP); }
            if (age > LEAP && age <= LEAP + 8) {
                float k0 = (age - 1 - LEAP) / 8F, k1 = (age - LEAP) / 8F;
                glide(origin.add(0, LEAP_HEIGHT * k0, 0), origin.add(0, LEAP_HEIGHT * k1, 0));
            }
            if (age == LEAP + 9) still();
            if (age > PLAN && age < BUILT && age % 3 == 0) sound(base.add(0, GLACIER, 0), SoundEvents.AMETHYST_BLOCK_PLACE, 1.2F, .5F + (age - PLAN) * .02F);
            if (age == DROP) {
                pose(ArmPose.CRYO_SHATTER, 12);
                sound(base.add(0, GLACIER, 0), SoundEvents.GLASS_BREAK, 1.6F, .4F);
            }
            if (age == IMPACT) {
                CryoService.shatterHit(player, foe, 18);
                for (LivingEntity t : others(base, 6)) {
                    t.invulnerableTime = 0;
                    CryoService.coldHit(player, t, 6);
                    CryoService.chill(t, 80, player);
                    shove(t, base, 1, .4);
                }
                sound(base, SoundEvents.GLASS_BREAK, 2, .5F);
                sound(base, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 1.3F);
                shake(base, 24, .55F, 12);
                release();
            }
            if (age > IMPACT && age <= LANDED) {
                float k0 = (age - 1 - IMPACT) / (float) (LANDED - IMPACT), k1 = (age - IMPACT) / (float) (LANDED - IMPACT);
                glide(origin.add(0, LEAP_HEIGHT * (1 - k0), 0), origin.add(0, LEAP_HEIGHT * (1 - k1), 0));
            }
            if (age == LANDED) { hover(false); still(); pose(ArmPose.CRYO_GUARD, 30); }
            if (age == WARMTH + 20) {
                StyleOps.heal(player, 4);
                sound(player.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1, 1.4F);
            }
        }

        @Override protected void tidy(boolean broken) { hover(false); }
    }

    // ------------------------------------------------------------------
    // Defense: Winter Citadel
    // ------------------------------------------------------------------

    /**
     * A ring of ice walls grows up around the cryokinetic for twelve seconds, snow falling inside. Shots that reach the
     * walls shatter on them; foes who press against them are frozen to the spot and pushed back out; blows that find
     * the cryokinetic anyway are a third lighter. When it ends the walls burst outward in shards.
     */
    public static final class WinterCitadel extends Run {
        public static final int TICKS = 240, INTRO = 22, WALLS = 10;
        public static final double RADIUS = 4.5;
        private Vec3 centre;

        public WinterCitadel(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "crystal_bastion", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            centre = player.position();
            cue();
            pose(ArmPose.CRYO_SHELTER, 20);
            heroShot(INTRO, 1.5);
            piece(Piece.CRYO_ULT_CITADEL, centre, centre, (float) RADIUS, null, 0);
            sound(centre, SoundEvents.GLASS_PLACE, 1.6F, .5F);
            sound(centre, SoundEvents.PLAYER_HURT_FREEZE, 1, .6F);
        }

        @Override protected void beat(int age) {
            if (age % 20 == 0 && age < TICKS) piece(Piece.CRYO_ULT_CITADEL, centre, centre, (float) RADIUS, null, age / 20);
            for (Projectile shot : shots(centre.add(0, 1.2, 0), RADIUS + 1.5)) {
                double d = Math.sqrt(shot.position().subtract(centre).horizontalDistanceSqr());
                if (d > RADIUS + .6 || d < RADIUS - 1.2) continue;
                piece(Piece.CRYO_ULT_SHARD, shot.position(), centre.add(0, 1, 0), .6F, null, age);
                shot.discard();
                sound(shot.position(), SoundEvents.GLASS_HIT, .8F, 1.4F);
            }
            if (age % 4 == 0) for (LivingEntity t : StyleOps.foes(player, centre, RADIUS + .6)) {
                CryoService.chill(t, 30, player);
                shove(t, centre, .8, .15);
            }
        }

        @Override protected void incoming(LivingIncomingDamageEvent event, LivingEntity victim) {
            if (victim != player || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
            event.setAmount(event.getAmount() * .67F);
        }

        @Override protected void finish() {
            for (LivingEntity t : StyleOps.foes(player, centre, RADIUS + 3)) {
                t.invulnerableTime = 0;
                CryoService.coldHit(player, t, 4);
                CryoService.chill(t, 60, player);
                shove(t, centre, 1.1, .35);
            }
            piece(Piece.CRYO_ULT_CITADEL, centre, centre, (float) RADIUS, null, -1);
            sound(centre, SoundEvents.GLASS_BREAK, 2, .6F);
        }

        public Vec3 centre() { return centre; }
    }

    // ------------------------------------------------------------------
    // Support: Hibernation
    // ------------------------------------------------------------------

    /**
     * Snow comes down softly over ten blocks for twelve seconds. Every friend in it is armoured in frost (extra hearts,
     * no burning, a little less harm), and any friend who is hurt badly is sealed in a crystal cocoon that holds them
     * safe and mends them, once; it melts open in a warm glow. Foes in the snow are chilled.
     */
    public static final class Hibernation extends Run {
        public static final int TICKS = 240, INTRO = 22, COCOON = 50;
        public static final double RADIUS = 10;
        private Vec3 centre;
        private final Set<UUID> armoured = new HashSet<>(), cocooned = new HashSet<>();

        public Hibernation(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "aurora_veil", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            centre = player.position();
            cue();
            pose(ArmPose.CRYO_STARS, 22);
            heroShot(INTRO, 2);
            piece(Piece.CRYO_ULT_SNOW, centre, centre, (float) RADIUS, null, 0);
            sound(centre, SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, .6F);
        }

        @Override protected void beat(int age) {
            if (age % 20 == 0 && age < TICKS) {
                piece(Piece.CRYO_ULT_SNOW, centre, centre, (float) RADIUS, null, age / 20);
                for (LivingEntity t : StyleOps.foes(player, centre, RADIUS)) CryoService.chill(t, 25, player);
            }
            if (age % 10 != 0) return;
            for (LivingEntity f : StyleOps.friends(player, centre, RADIUS)) {
                f.clearFire();
                if (armoured.add(f.getUUID())) {
                    StyleOps.effect(f, MobEffects.ABSORPTION, TICKS - age, 2);
                    StyleOps.effect(f, MobEffects.FIRE_RESISTANCE, TICKS - age, 0);
                    StyleOps.effect(f, MobEffects.DAMAGE_RESISTANCE, TICKS - age, 0);
                    piece(Piece.CRYO_ULT_ARMOUR, f.position(), f.position(), f.getBbHeight(), f, 0);
                }
                if (f.getHealth() < f.getMaxHealth() * .5F && cocooned.add(f.getUUID())) {
                    StyleOps.effect(f, MobEffects.DAMAGE_RESISTANCE, COCOON, 3);
                    StyleOps.effect(f, MobEffects.MOVEMENT_SLOWDOWN, COCOON, 5);
                    StyleOps.effect(f, MobEffects.REGENERATION, COCOON, 2);
                    StyleOps.cleanse(f);
                    piece(Piece.CRYO_ULT_COCOON, f.position(), f.position(), f.getBbHeight(), f, 0);
                    sound(f.position(), SoundEvents.GLASS_PLACE, 1.2F, .8F);
                }
            }
        }

        public Vec3 centre() { return centre; }
    }
}
