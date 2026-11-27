package com.jayptucker.heroesevolved.regeneration;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Ultimates.Run;
import com.jayptucker.heroesevolved.combat.Ultimates.Ultimate;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Regeneration's three Ultimates; {@code RegenArt} draws them from these beats. */
public final class RegenUltimates {
    private RegenUltimates() {}

    public static final Ultimate OFFENSE = Ultimate.offense(CantStopMe::new, 8);
    public static final Ultimate DEFENSE = Ultimate.defense(Unkillable::new);
    public static final Ultimate SUPPORT = Ultimate.support(Lifeblood::new);

    private static final String POWER = "regeneration";

    // ------------------------------------------------------------------
    // Offense: Can't Stop Me
    // ------------------------------------------------------------------

    /**
     * She walks straight into the foe and lets it hurt her (a gash opens across her chest) so she can get hold of it.
     * Then six blows, headbutt to uppercut, every one closing a wound with a flash of gold; she lifts the foe over her
     * head and breaks it over her knee into the ground. The last wound closes as she stands up.
     */
    public static final class CantStopMe extends Run {
        public static final int WALK = 8, BLOWS = 6, FIRST = 12, EVERY = 6, LIFT = 48, OVERHEAD = 62, SLAM = 70, CLOSED = 80, TICKS = 110;
        public static final float COST = 4;

        public CantStopMe(ServerPlayer p, int tier, LivingEntity foe) { super(p, tier, POWER, "vital_burst", foe); }

        @Override protected int length() { return TICKS; }

        private Vec3 front() { return around(base, -1.1, 0, 0); }

        @Override protected void open() {
            face(foeCentre());
            cue();
            pose(ArmPose.REGEN_DEFY, WALK);
            piece(Piece.REGEN_ULT_BRAWL, origin, base, foe.getBbHeight());
            double d = Math.sqrt(base.subtract(origin).horizontalDistanceSqr());
            cinema(cam()
                    .key(0, -2.4, 2.2, 1.8, d, 0, 1.4)
                    .key(WALK + 2, d - 2.2, 2.6, 1.6, d - .8, 0, 1.4)
                    .key(FIRST + EVERY * 3, d - 1.4, -2.4, 1.5, d - .6, 0, 1.5)
                    .key(OVERHEAD, d - 4.5, 3.6, 1.5, d - 1, 0, 2.6)
                    .key(SLAM + 2, d - 4, -3.5, .8, d - .6, 0, .6)
                    .key(TICKS - 14, d - 6, 5, 2.5, d - 1, 0, 1.2)
                    .until(TICKS - 10));
        }

        @Override protected void beat(int age) {
            if (age <= WALK) glide(origin.lerp(front(), (age - 1) / (float) WALK), origin.lerp(front(), age / (float) WALK));
            if (age == WALK) {
                still();
                player.setHealth(Math.max(1, player.getHealth() - COST));
                RegenerationService.wounded(player, COST);
                sound(player.position(), SoundEvents.PLAYER_HURT, 1, .8F);
                sound(player.position(), SoundEvents.WARDEN_HEARTBEAT, 1.4F, .9F);
            }
            if (age >= FIRST && age < FIRST + BLOWS * EVERY && (age - FIRST) % EVERY == 0) {
                int i = (age - FIRST) / EVERY;
                face(foeCentre());
                pose(new ArmPose[]{ArmPose.REGEN_DEFY, ArmPose.REGEN_PAYBACK, ArmPose.REGEN_FLING, ArmPose.REGEN_PAYBACK, ArmPose.REGEN_DEFY, ArmPose.REGEN_RISE}[i], 5);
                hit(foe, 1.5F);
                Takedowns.flinch(foe);
                StyleOps.heal(player, 1);
                sound(foeCentre(), SoundEvents.PLAYER_ATTACK_STRONG, 1, .8F + i * .07F);
            }
            if (age == LIFT) { pose(ArmPose.REGEN_HARDEN, SLAM - LIFT); sound(player.position(), SoundEvents.RAVAGER_ROAR, .5F, 1.4F); }
            if (age > LIFT && age <= OVERHEAD) {
                float k = (age - LIFT) / (float) (OVERHEAD - LIFT);
                place(player.position().add(facing.scale(.3 * (1 - k))).add(0, 1.2 + 1.4 * k, 0));
            }
            if (age > SLAM - 4 && age <= SLAM) {
                float k = (age - (SLAM - 4)) / 4F;
                place(player.position().add(0, 2.6, 0).lerp(StyleOps.ground(level, player.position().add(facing.scale(1.2)).add(0, 1, 0)), k));
            }
            if (age == SLAM) {
                pose(ArmPose.REGEN_PAYBACK, 10);
                hit(foe, 14);
                splash(foeAt(), 4, 4, t -> shove(t, foeAt(), 1, .4));
                sound(foeAt(), SoundEvents.GENERIC_EXPLODE.value(), 1.2F, .9F);
                sound(foeAt(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 1.4F, .6F);
                shake(foeAt(), 22, .5F, 10);
                release();
            }
            if (age == CLOSED) {
                StyleOps.heal(player, 6);
                sound(player.position(), SoundEvents.WARDEN_HEARTBEAT, 1.6F, 1.3F);
            }
        }
    }

    // ------------------------------------------------------------------
    // Defense: Unkillable
    // ------------------------------------------------------------------

    /**
     * For twelve seconds nothing can take her last heart. Every blow is halved, and what is left is banked rather than
     * felt; she mends a heart every second. When it ends, everything banked bursts out of her in a ring of blood.
     */
    public static final class Unkillable extends Run {
        public static final int TICKS = 240, INTRO = 22;
        public static final float CAP = 12;
        private float banked;

        public Unkillable(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "undying", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.REGEN_BRACE, 22);
            heroShot(INTRO, .5);
            piece(Piece.REGEN_ULT_UNDYING, player.position(), player.position(), 1, player, 0);
            sound(player.position(), SoundEvents.WARDEN_HEARTBEAT, 1.6F, .6F);
        }

        @Override protected void beat(int age) {
            if (age % 20 == 0 && age < TICKS) {
                piece(Piece.REGEN_ULT_UNDYING, player.position(), player.position(), 1, player, age / 20);
                StyleOps.heal(player, 2);
                sound(player.position(), SoundEvents.WARDEN_HEARTBEAT, 1.2F, .7F);
            }
        }

        @Override protected void incoming(LivingIncomingDamageEvent event, LivingEntity victim) {
            if (victim != player || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
            float half = event.getAmount() * .5F;
            banked += half;
            event.setAmount(half);
        }

        @Override protected float lethal(LivingEntity victim, DamageSource source, float damage) {
            if (victim != player) return damage;
            return Math.max(0, player.getHealth() + player.getAbsorptionAmount() - 1);
        }

        @Override protected void finish() {
            float damage = Math.min(CAP, banked * .5F + 2);
            Vec3 at = player.position();
            for (LivingEntity t : StyleOps.foes(player, at, 6)) { StyleOps.magic(player, t, damage); shove(t, at, 1, .4); }
            piece(Piece.REGEN_ULT_UNDYING, at, at, 1, player, -1);
            sound(at, SoundEvents.WARDEN_SONIC_BOOM, .6F, 1.6F);
        }

        public float banked() { return banked; }
    }

    // ------------------------------------------------------------------
    // Support: Lifeblood
    // ------------------------------------------------------------------

    /**
     * She opens her palm and threads of her blood run out to every friend within sixteen blocks: half their health back
     * at once, their harmful effects gone. For fifteen seconds after, a friend who would die is held at their last heart
     * instead and given half their health again, once each.
     */
    public static final class Lifeblood extends Run {
        public static final int TICKS = 300, INTRO = 22, POUR = 16;
        public static final double RANGE = 16;
        private final List<LivingEntity> bound = new ArrayList<>();
        private final Set<UUID> saved = new HashSet<>();

        public Lifeblood(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "lifebloom", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.REGEN_OFFER, 26);
            heroShot(INTRO, 1);
            bound.addAll(friends(RANGE));
            for (int i = 0; i < bound.size(); i++) {
                LivingEntity f = bound.get(i);
                piece(Piece.REGEN_ULT_BLOOD, player.position(), f.position(), f.getBbHeight(), f, player.getId());
            }
            sound(player.position(), SoundEvents.WARDEN_HEARTBEAT, 1.4F, 1.1F);
        }

        @Override protected void beat(int age) {
            if (age == POUR) for (LivingEntity f : bound) {
                if (!f.isAlive()) continue;
                StyleOps.heal(f, f.getMaxHealth() * .5F);
                StyleOps.cleanse(f);
                sound(f.position(), SoundEvents.PLAYER_LEVELUP, .5F, 1.6F);
            }
            if (age % 60 == 0 && age < TICKS)
                for (LivingEntity f : bound) if (f.isAlive() && f != player && !saved.contains(f.getUUID()))
                    piece(Piece.REGEN_ULT_BLOOD, player.position(), f.position(), f.getBbHeight(), f, player.getId());
        }

        @Override protected float lethal(LivingEntity victim, DamageSource source, float damage) {
            if (!bound.contains(victim) || !saved.add(victim.getUUID())) return damage;
            victim.setHealth(Math.max(1, victim.getMaxHealth() * .5F));
            piece(Piece.REGEN_ULT_SAVED, victim.position(), victim.position(), victim.getBbHeight(), victim, 0);
            sound(victim.position(), SoundEvents.TOTEM_USE, .5F, 1.4F);
            return 0;
        }

        public List<LivingEntity> bound() { return bound; }
    }
}
