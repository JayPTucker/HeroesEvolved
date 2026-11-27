package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.Ultimates.Run;
import com.jayptucker.heroesevolved.combat.Ultimates.Ultimate;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Space-Time Manipulation's three Ultimates; {@code TimeArt} draws them from these beats. */
public final class TimeUltimates {
    private TimeUltimates() {}

    public static final Ultimate OFFENSE = Ultimate.offense(TenPastTen::new, 10);
    public static final Ultimate DEFENSE = Ultimate.defense(SecondChance::new);
    public static final Ultimate SUPPORT = Ultimate.support(BorrowedHours::new);

    private static final String POWER = "time_manipulation";

    // ------------------------------------------------------------------
    // Offense: Ten Past Ten
    // ------------------------------------------------------------------

    /**
     * A click, and the world stops: everything goes still and violet, a clock under the foe with its hands at ten past
     * ten. The time-bender walks round it in the stopped moment, a step at a time, and every step leaves a cut of light
     * hanging in the air across it and a frozen copy of them standing where they cut. Back where they began, they snap
     * their fingers: time runs again, every cut lands at once, and the clock shatters.
     */
    public static final class TenPastTen extends Run {
        public static final int STOP = 6, CUT = 14, CUTS = 8, EVERY = 6, BACK = 64, SNAP = 70, TICKS = 110;
        public static final double RADIUS = 2.4;

        public TenPastTen(ServerPlayer p, int tier, LivingEntity foe) { super(p, tier, POWER, "hour_hand", foe); }

        @Override protected int length() { return TICKS; }

        /** Where the {@code i}th step round the foe stands (0 on the far side, going round). */
        public static Vec3 step(Vec3 foe, Vec3 facing, int i) {
            double a = Math.atan2(facing.z, facing.x) + i * Math.PI * 2 / CUTS * 1.375;
            return foe.add(Math.cos(a) * RADIUS, 0, Math.sin(a) * RADIUS);
        }

        @Override protected void open() {
            face(foeCentre());
            cue();
            pose(ArmPose.TIME_HALT, 10);
            piece(Piece.TIME_ULT_STOPPED, origin, base, foe.getBbHeight());
            piece(Piece.TIME_ULT_COPIES, origin, base, 1, player, 0);
            double d = Math.sqrt(base.subtract(origin).horizontalDistanceSqr());
            cinema(cam()
                    .key(0, -2.4, 2.2, 1.8, d, 0, 1.2)
                    .key(CUT, d - 6, 4.5, 4, d, 0, 1)
                    .key(CUT + EVERY * 4, d + 4.5, 5.5, 4.5, d, 0, 1)
                    .key(BACK, d - 7, -5, 5, d, 0, 1)
                    .key(SNAP + 2, -1.8, 2, 1.8, d, 0, 1.4)
                    .key(TICKS - 14, d - 7, 6, 4, d, 0, 1)
                    .until(TICKS - 10));
        }

        @Override protected void beat(int age) {
            if (age == STOP) {
                ScreenTintPayload.sendAround(level, base, 32, 0x704A30C0, SNAP - STOP, ScreenTintPayload.Style.VIGNETTE);
                sound(base, ModSounds.TIME_UNSLOW.get(), 1.4F, .5F);
                sound(base, SoundEvents.BEACON_DEACTIVATE, 1.2F, .4F);
            }
            if (age >= CUT && age < CUT + CUTS * EVERY && (age - CUT) % EVERY == 0) {
                int i = (age - CUT) / EVERY;
                Vec3 at = step(base, facing, i);
                if (SpeedService.safe(player, at)) StyleHits.blink(player, at);
                face(foeCentre());
                pose(i % 2 == 0 ? ArmPose.TIME_DRAW : ArmPose.TIME_THROW, 5);
                sound(at, SoundEvents.PLAYER_ATTACK_SWEEP, .9F, 1.6F + i * .05F);
            }
            if (age == BACK) {
                Vec3 at = around(base, -1.8, 0, 0);
                StyleHits.blink(player, SpeedService.safe(player, at) ? at : origin);
                face(foeCentre());
            }
            if (age == SNAP) {
                pose(ArmPose.TIME_SNAP, 10);
                hit(foe, 22);
                splash(base, 4, 6, null);
                sound(base, SoundEvents.GLASS_BREAK, 1.6F, .6F);
                sound(base, ModSounds.TIME_UNSLOW.get(), 1.4F, 1.4F);
                shake(base, 20, .45F, 10);
                throwFoe(facing.scale(1.1).add(0, .5, 0));
            }
        }
    }

    // ------------------------------------------------------------------
    // Defense: Second Chance
    // ------------------------------------------------------------------

    /**
     * Mark this moment, for fifteen seconds. A blow that would kill the time-bender instead takes them back to it: where
     * they stood, the health they had, their harmful effects gone; and everyone who struck them since is caught in stopped
     * time for two seconds. If it runs out unused, they are given back the health they have lost since.
     */
    public static final class SecondChance extends Run {
        public static final int TICKS = 300, INTRO = 22, HOLD = 40;
        private Vec3 mark;
        private float health;
        private final Set<LivingEntity> struck = new LinkedHashSet<>();
        private boolean spent;

        public SecondChance(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "time_loop", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            mark = player.position();
            health = player.getHealth();
            cue();
            pose(ArmPose.TIME_PLANT, 20);
            heroShot(INTRO, .5);
            piece(Piece.TIME_ULT_MARK, mark, mark, 1, null, 0);
            sound(mark, SoundEvents.BELL_BLOCK, 1, .6F);
        }

        @Override protected void beat(int age) {
            if (!spent && age % 20 == 0 && age < TICKS) piece(Piece.TIME_ULT_MARK, mark, mark, 1, null, age / 20);
        }

        @Override protected void incoming(LivingIncomingDamageEvent event, LivingEntity victim) {
            if (victim == player && !spent && event.getSource().getEntity() instanceof LivingEntity attacker && attacker != player)
                struck.add(attacker);
        }

        @Override protected float lethal(LivingEntity victim, DamageSource source, float damage) {
            if (victim != player || spent) return damage;
            spent = true;
            Vec3 from = player.position();
            StyleOps.cleanse(player);
            player.setHealth(Math.max(health, 4));
            StyleHits.blink(player, SpeedService.safe(player, mark) ? mark : from);
            piece(Piece.TIME_ULT_REWIND, from, mark, 1, null, 0);
            for (LivingEntity t : struck) {
                if (!t.isAlive() || t.level() != level) continue;
                StyleOps.effect(t, MobEffects.MOVEMENT_SLOWDOWN, HOLD, 9);
                StyleOps.effect(t, MobEffects.WEAKNESS, HOLD, 4);
                piece(Piece.TIME_STASIS, t.position(), t.getEyePosition(), t.getBbHeight(), t, 0);
            }
            sound(mark, ModSounds.TIME_UNSLOW.get(), 1.6F, .6F);
            sound(mark, SoundEvents.BELL_BLOCK, 1.4F, 1.4F);
            return 0;
        }

        @Override protected void finish() {
            if (spent || player.getHealth() >= health) return;
            player.setHealth(Math.min(player.getMaxHealth(), health));
            piece(Piece.TIME_ULT_REWIND, player.position(), player.position(), 1, null, 1);
            sound(player.position(), ModSounds.TIME_UNSLOW.get(), 1, 1.2F);
        }

        public boolean spent() { return spent; }
    }

    // ------------------------------------------------------------------
    // Support: Borrowed Hours
    // ------------------------------------------------------------------

    /**
     * The time-bender lends out hours they don't need. Every friend within twelve blocks is quickened for twelve
     * seconds (fast on their feet and at their work), given back twenty stamina, and has every cooldown they are waiting
     * on cut in half; every foe near is slowed, its clock wound down.
     */
    public static final class BorrowedHours extends Run {
        public static final int TICKS = 240, INTRO = 22;
        public static final double RANGE = 12;
        private final List<LivingEntity> quickened = new ArrayList<>();

        public BorrowedHours(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "borrowed_time", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.TIME_WIND, 20);
            heroShot(INTRO, .8);
            Set<String> ultimates = new java.util.HashSet<>();
            for (var power : com.jayptucker.heroesevolved.ability.registry.AbilityRegistry.ABILITIES.keySet())
                for (var node : SkillCatalog.styles(power.getPath())) ultimates.add(node.id());
            quickened.addAll(friends(RANGE));
            for (LivingEntity f : quickened) {
                StyleOps.effect(f, MobEffects.MOVEMENT_SPEED, TICKS, 1);
                StyleOps.effect(f, MobEffects.DIG_SPEED, TICKS, 1);
                if (f instanceof ServerPlayer friend) {
                    PlayerEnergyService.restore(friend, 20);
                    CooldownService.hasten(friend, .5, id -> ultimates.contains(id.getPath()));
                    com.jayptucker.heroesevolved.network.PlayerPowerSyncService.sync(friend);
                }
                piece(Piece.TIME_ULT_HOURS, f.position(), f.position(), f.getBbHeight(), f, 0);
            }
            for (LivingEntity t : StyleOps.foes(player, player.position(), RANGE)) {
                StyleOps.effect(t, MobEffects.MOVEMENT_SLOWDOWN, TICKS, 1);
                StyleOps.effect(t, MobEffects.DIG_SLOWDOWN, TICKS, 1);
                piece(Piece.TIME_ULT_HOURS, t.position(), t.position(), t.getBbHeight(), t, 1);
            }
            sound(player.position(), SoundEvents.BELL_BLOCK, 1.2F, 1.6F);
        }

        @Override protected void beat(int age) {
            if (age % 60 == 0 && age < TICKS)
                for (LivingEntity f : quickened) if (f.isAlive()) piece(Piece.TIME_ULT_HOURS, f.position(), f.position(), f.getBbHeight(), f, 0);
        }

        public List<LivingEntity> quickened() { return quickened; }
    }
}
