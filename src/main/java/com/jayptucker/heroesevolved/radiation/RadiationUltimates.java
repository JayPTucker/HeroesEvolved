package com.jayptucker.heroesevolved.radiation;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Ultimates.Run;
import com.jayptucker.heroesevolved.combat.Ultimates.Ultimate;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.sounds.ModSounds;
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
 * Induced Radioactivity's three Ultimates, in the power's one idea: a reactor's light. Heat is orange going white-hot;
 * sickness is a sickly green; the reactor's own glow (the Hearth) is Cherenkov blue. {@code RadiationArt} draws them
 * from these beats.
 */
public final class RadiationUltimates {
    private RadiationUltimates() {}

    public static final Ultimate OFFENSE = Ultimate.offense(PointOfNoReturn::new, 8);
    public static final Ultimate DEFENSE = Ultimate.defense(LeadHeart::new);
    public static final Ultimate SUPPORT = Ultimate.support(CherenkovHearth::new);

    private static final String POWER = "radiation";

    private static void heat(ServerPlayer p, LivingEntity t, float damage) {
        t.invulnerableTime = 0;
        t.hurt(p.damageSources().indirectMagic(p, p), damage);
    }

    // ------------------------------------------------------------------
    // Offense: Point of No Return
    // ------------------------------------------------------------------

    /**
     * He takes the foe by the collar and lets his core run. The glow in his chest climbs from orange to white, light
     * spreads through his veins, heat shimmers off him in rings and the foe begins to burn in his grip. Everything goes
     * white, and he goes off at point-blank range: a blast in small, with its own mushroom cloud, the foe thrown out of
     * it, and him standing in the crater, steaming.
     */
    public static final class PointOfNoReturn extends Run {
        public static final int GRAB = 8, HEAT = 12, BURNS = 5, EVERY = 10, WHITE = 62, BLAST = 64, TICKS = 120;
        public static final float BLAST_RADIUS = 8;

        public PointOfNoReturn(ServerPlayer p, int tier, LivingEntity foe) { super(p, tier, POWER, "ground_zero", foe); }

        @Override protected int length() { return TICKS; }

        /** How hot the core has run, 0 to 1, by tick. */
        public static float heat(float t) { return net.minecraft.util.Mth.clamp((t - HEAT) / (WHITE - HEAT), 0, 1); }

        private Vec3 front() { return around(base, -1.1, 0, 0); }

        @Override protected void open() {
            face(foeCentre());
            cue();
            pose(ArmPose.RAD_GRIP, BLAST);
            piece(Piece.RAD_ULT_POINT, origin, base, foe.getBbHeight());
            double d = Math.sqrt(base.subtract(origin).horizontalDistanceSqr());
            cinema(cam()
                    .key(0, -2.4, 2.4, 1.8, d, 0, 1.4)
                    .key(HEAT + 10, d - 2.6, 3.4, 1.5, d - .5, 0, 1.5)
                    .key(WHITE - 6, d - 1.8, -2.2, 1.6, d - .8, 0, 1.4)
                    // Back and high for the blast, outside its ring of dust.
                    .key(BLAST + 4, d - 18, 11, 7, d, 0, 4)
                    .key(TICKS - 14, d - 22, 14, 10, d, 0, 6)
                    .until(TICKS - 10));
        }

        @Override protected void beat(int age) {
            if (age <= GRAB && origin.distanceTo(front()) > .3)
                glide(origin.lerp(front(), (age - 1) / (float) GRAB), origin.lerp(front(), age / (float) GRAB));
            if (age == GRAB) {
                still();
                Takedowns.flinch(foe);
                sound(foeCentre(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, .8F, .7F);
            }
            if (age >= HEAT && age < HEAT + BURNS * EVERY && (age - HEAT) % EVERY == 0) {
                RadiationUltimates.heat(player, foe, 1);
                foe.igniteForSeconds(3);
                sound(player.position(), SoundEvents.NOTE_BLOCK_HAT.value(), .6F, 1.4F + (age - HEAT) * .012F);
                sound(player.position(), SoundEvents.BEACON_AMBIENT, .8F, .6F + heat(age));
            }
            if (age == WHITE) {
                ScreenTintPayload.sendAround(level, player.position(), 40, 0xE0FFF6E8, 16, ScreenTintPayload.Style.FLASH);
                pose(ArmPose.RAD_FLARE, 20);
            }
            if (age == BLAST) {
                RadiationUltimates.heat(player, foe, 16);
                RadiationSickness.dose(foe, player, 2, 200);
                for (LivingEntity t : others(player.position(), BLAST_RADIUS)) {
                    RadiationUltimates.heat(player, t, 8);
                    RadiationSickness.dose(t, player, 1, 160);
                    shove(t, player.position(), 1.4, .6);
                }
                throwFoe(facing.scale(1.6).add(0, .9, 0));
                level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.NUCLEAR_BLAST.get(),
                        net.minecraft.sounds.SoundSource.PLAYERS, 2.4F, 1.3F);
                sound(player.position(), SoundEvents.GENERIC_EXPLODE.value(), 2, .8F);
                shake(player.position(), 40, .9F, 20);
            }
        }
    }

    // ------------------------------------------------------------------
    // Defense: Lead Heart
    // ------------------------------------------------------------------

    /**
     * Panels of lead glass lock into a dome round him for twelve seconds. Shots that reach it are swallowed; the blows
     * that find him land two-fifths lighter and their force is kept as heat; foes who stand inside it sicken. When it
     * ends the panels blow out in a wave of that heat.
     */
    public static final class LeadHeart extends Run {
        public static final int TICKS = 240, INTRO = 22;
        public static final double RADIUS = 3.6;
        private float banked;

        public LeadHeart(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "containment_dome", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.RAD_CUP, 22);
            heroShot(INTRO, 1);
            piece(Piece.RAD_ULT_LEAD, player.position(), player.position(), (float) RADIUS, player, 0);
            sound(player.position(), SoundEvents.BEACON_ACTIVATE, 1, .7F);
        }

        @Override protected void beat(int age) {
            if (age % 20 == 0 && age < TICKS) piece(Piece.RAD_ULT_LEAD, player.position(), player.position(), (float) RADIUS, player, age / 20);
            if (age % 40 == 0) for (LivingEntity t : StyleOps.foes(player, player.position(), RADIUS)) RadiationSickness.dose(t, player, 1, 100, 2);
            Vec3 body = player.getBoundingBox().getCenter();
            for (Projectile shot : shots(body, RADIUS + 1)) {
                if (shot.position().distanceToSqr(body) > (RADIUS + .5) * (RADIUS + .5)) continue;
                piece(Piece.RAD_PULSE, shot.position(), shot.position(), .5F, null, age);
                shot.discard();
                banked += 1;
                sound(shot.position(), SoundEvents.FIRE_EXTINGUISH, .7F, 1.6F);
            }
        }

        @Override protected void incoming(LivingIncomingDamageEvent event, LivingEntity victim) {
            if (victim != player || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
            banked += event.getAmount() * .4F;
            event.setAmount(event.getAmount() * .6F);
        }

        @Override protected void finish() {
            Vec3 at = player.position();
            float damage = Math.min(10, 2 + banked / 3);
            for (LivingEntity t : StyleOps.foes(player, at, RADIUS + 2.5)) { heat(player, t, damage); shove(t, at, 1.3, .4); }
            piece(Piece.RAD_ULT_LEAD, at, at, (float) RADIUS, player, -1);
            sound(at, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 1.2F);
            shake(at, 18, .35F, 8);
        }

        public float banked() { return banked; }
    }

    // ------------------------------------------------------------------
    // Support: Cherenkov Hearth
    // ------------------------------------------------------------------

    /**
     * A reactor's heart rises at his feet for fifteen seconds, glowing the blue of a reactor pool behind a cage of
     * rods. Friends within eight blocks mend, take an extra layer of hearts once, get their stamina back faster and are
     * cured of sickness; foes who come close are dosed.
     */
    public static final class CherenkovHearth extends Run {
        public static final int TICKS = 300, INTRO = 24;
        public static final double RADIUS = 8;
        private Vec3 centre;
        private final Set<UUID> warmed = new HashSet<>();

        public CherenkovHearth(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "reactor_hearth", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            centre = player.position().add(facing.scale(1.2));
            cue();
            pose(ArmPose.RAD_RAISE, 22);
            heroShot(INTRO, 2);
            piece(Piece.RAD_ULT_HEARTH, centre, centre, (float) RADIUS, null, 0);
            sound(centre, SoundEvents.BEACON_ACTIVATE, 1.4F, 1.2F);
            sound(centre, SoundEvents.CONDUIT_ACTIVATE, 1, 1);
        }

        @Override protected void beat(int age) {
            if (age % 20 == 0 && age < TICKS) {
                piece(Piece.RAD_ULT_HEARTH, centre, centre, (float) RADIUS, null, age / 20);
                sound(centre, SoundEvents.BEACON_AMBIENT, 1, 1.3F);
                for (LivingEntity f : StyleOps.friends(player, centre, RADIUS)) {
                    StyleOps.heal(f, 1.5F);
                    f.removeEffect(ModItems.RADIATION_SICKNESS);
                    if (warmed.add(f.getUUID())) StyleOps.effect(f, MobEffects.ABSORPTION, TICKS - age, 1);
                    if (f instanceof ServerPlayer friend) PlayerEnergyService.restore(friend, 4);
                }
                for (LivingEntity t : StyleOps.foes(player, centre, 3)) RadiationSickness.dose(t, player, 1, 100, 2);
            }
        }

        public Vec3 centre() { return centre; }
    }
}
