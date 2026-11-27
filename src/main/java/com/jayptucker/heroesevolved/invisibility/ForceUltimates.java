package com.jayptucker.heroesevolved.invisibility;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Ultimates;
import com.jayptucker.heroesevolved.combat.Ultimates.Run;
import com.jayptucker.heroesevolved.combat.Ultimates.Ultimate;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Invisibility's three Ultimates, all force fields; {@code ForceArt} draws them from these beats. */
public final class ForceUltimates {
    private ForceUltimates() {}

    public static final Ultimate OFFENSE = Ultimate.offense(CrushBox::new, 12);
    public static final Ultimate DEFENSE = Ultimate.defense(Aegis::new);
    public static final Ultimate SUPPORT = Ultimate.support(VanishingAct::new);

    private static final String POWER = "invisibility";

    // ------------------------------------------------------------------
    // Offense: Crush Box
    // ------------------------------------------------------------------

    /**
     * Six panes of force snap shut round the foe, a box, and lift it. The box is slammed into the ground, flung left,
     * flung right and dropped again, each blow a flash across a pane; then it closes in on itself, stress cracks running
     * dark across every face, until she opens her hand and it bursts into a storm of shards.
     */
    public static final class CrushBox extends Run {
        public static final int SHUT = 10, SLAM = 16, SLAMS = 4, EVERY = 10, SQUEEZE = 58, BURST = 82, TICKS = 110;
        public static final double LIFT = 1.6, SWING = 2.2;

        public CrushBox(ServerPlayer p, int tier, LivingEntity foe) { super(p, tier, POWER, "shadow_strike", foe); }

        @Override protected int length() { return TICKS; }

        /** Where the box is after the {@code n}th slam (0 the ground, then left, right, the ground). */
        public Vec3 slamAt(int n) {
            return switch (n % 4) {
                case 0, 3 -> base;
                case 1 -> base.add(side.scale(-SWING)).add(0, .4, 0);
                default -> base.add(side.scale(SWING)).add(0, .4, 0);
            };
        }

        /** How tightly the box has closed (1 its full size, smaller as it crushes). */
        public static float size(float t) { return t < SQUEEZE ? 1 : (float) Math.max(.45, 1 - .55 * (t - SQUEEZE) / (BURST - SQUEEZE)); }

        @Override protected void open() {
            face(foeCentre());
            cue();
            pose(ArmPose.FORCE_BUILD, SHUT + 2);
            piece(Piece.FORCE_ULT_BOX, origin, base, foe.getBbHeight());
            sound(foeCentre(), SoundEvents.BEACON_ACTIVATE, 1.2F, 1.4F);
            double d = Math.sqrt(base.subtract(origin).horizontalDistanceSqr());
            cinema(cam()
                    .key(0, -2.6, 2.4, 1.8, d, 0, 1.2)
                    .key(SHUT + 4, d - 4, 3.5, 2.4, d, 0, 1.6)
                    .key(SLAM + EVERY * 2, d - 7, 0, 3.5, d, 0, 1.4)
                    .key(SQUEEZE + 6, d - 3.6, -3, 2, d, 0, 1.6)
                    .key(BURST + 2, d - 6, 4.5, 3, d, 0, 1.4)
                    .key(TICKS - 14, d - 8, 6, 3.5, d, 0, 1)
                    .until(TICKS - 10));
        }

        @Override protected void beat(int age) {
            if (age <= SHUT) lift(LIFT * age / SHUT);
            if (age == SHUT) sound(foeCentre(), SoundEvents.GLASS_PLACE, 1.4F, 1.2F);
            if (age >= SLAM && age < SLAM + SLAMS * EVERY) {
                int n = (age - SLAM) / EVERY, step = (age - SLAM) % EVERY;
                Vec3 from = n == 0 ? base.add(0, LIFT, 0) : slamAt(n - 1).add(0, LIFT * .5, 0);
                Vec3 to = slamAt(n);
                float k = Math.min(1, step / 4F);
                place(from.lerp(to, k * k));
                if (step == 0) pose(n % 2 == 0 ? ArmPose.FORCE_PUSH : ArmPose.FORCE_CRUSH, 6);
                if (step == 4) {
                    hit(foe, 2.5F);
                    Takedowns.flinch(foe);
                    sound(to, SoundEvents.GLASS_HIT, 1.6F, .6F + n * .1F);
                    sound(to, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1, .7F);
                    shake(to, 12, .2F, 5);
                }
            }
            if (age >= SLAM + SLAMS * EVERY && age < BURST) place(base.add(0, LIFT * .6, 0));
            if (age == SQUEEZE) {
                pose(ArmPose.FORCE_CRUSH, BURST - SQUEEZE);
                sound(foeCentre(), SoundEvents.GLASS_BREAK, .6F, .4F);
            }
            if (age > SQUEEZE && age < BURST && age % 6 == 0) sound(foeCentre(), SoundEvents.GLASS_HIT, .8F, .5F + (age - SQUEEZE) * .02F);
            if (age == BURST) {
                pose(ArmPose.FORCE_NOVA, 10);
                hit(foe, 12);
                for (LivingEntity t : others(foeAt(), 5)) { hit(t, 6); shove(t, foeAt(), 1.2, .4); }
                sound(foeCentre(), SoundEvents.GLASS_BREAK, 2, .7F);
                sound(foeCentre(), SoundEvents.GENERIC_EXPLODE.value(), .8F, 1.6F);
                shake(foeAt(), 20, .45F, 8);
                release();
            }
        }
    }

    // ------------------------------------------------------------------
    // Defense: Aegis
    // ------------------------------------------------------------------

    /**
     * A skin-tight shell of force for twelve seconds, a lens circling it. Every shot that reaches it is turned straight
     * back at whoever fired it (TaCZ rounds too); blows that get through are less than half as hard, and whoever struck
     * is thrown back by a ripple. When it ends, the shell sheds as a ring.
     */
    public static final class Aegis extends Run implements Ultimates.Deflecting {
        public static final int TICKS = 240, INTRO = 22;
        private final Map<UUID, Integer> rippled = new HashMap<>();

        public Aegis(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "refraction_cloak", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.FORCE_SHIELD, 22);
            heroShot(INTRO, .5);
            piece(Piece.FORCE_ULT_AEGIS, player.position(), player.position(), 1, player, 0);
            sound(player.position(), SoundEvents.BEACON_ACTIVATE, 1, 1.6F);
        }

        @Override protected void beat(int age) {
            if (age % 20 == 0 && age < TICKS) piece(Piece.FORCE_ULT_AEGIS, player.position(), player.position(), 1, player, age / 20);
            Vec3 body = player.getBoundingBox().getCenter();
            for (Projectile shot : shots(body, 4)) {
                Vec3 v = shot.getDeltaMovement();
                if (v.dot(body.subtract(shot.position())) <= 0) continue;
                Entity owner = shot.getOwner();
                Vec3 back = owner != null ? owner.getBoundingBox().getCenter().subtract(shot.position()).normalize().scale(Math.max(.8, v.length())) : v.scale(-1);
                StyleOps.push(shot, back);
                shot.setOwner(player);
                piece(Piece.FORCE_RIPPLE, shot.position(), body, .6F, null, 0);
                sound(shot.position(), SoundEvents.AMETHYST_BLOCK_HIT, .8F, 1.8F);
            }
        }

        @Override public boolean deflects(LivingEntity body) { return body == player; }

        @Override public void turn(Entity shot) {
            Vec3 at = shot == null ? player.getEyePosition() : shot.position();
            piece(Piece.FORCE_RIPPLE, at, player.getBoundingBox().getCenter(), .6F, null, 0);
            sound(at, SoundEvents.AMETHYST_BLOCK_HIT, .8F, 1.8F);
            if (shot != null) shot.discard();
        }

        @Override protected void incoming(LivingIncomingDamageEvent event, LivingEntity victim) {
            if (victim != player || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
            if (event.getSource().getDirectEntity() instanceof Projectile) { event.setCanceled(true); return; }
            event.setAmount(event.getAmount() * .4F);
            if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != player && StyleOps.isFoe(player, attacker)) {
                int last = rippled.getOrDefault(attacker.getUUID(), -100);
                if (age - last < 20) return;
                rippled.put(attacker.getUUID(), age);
                shove(attacker, player.position(), 1.3, .4);
                piece(Piece.FORCE_RIPPLE, attacker.getBoundingBox().getCenter(), player.getBoundingBox().getCenter(), 1.2F, null, 0);
            }
        }

        @Override protected void finish() {
            Vec3 at = player.position();
            piece(Piece.FORCE_ULT_AEGIS, at, at, 1, player, -1);
            for (LivingEntity t : StyleOps.foes(player, at, 5)) shove(t, at, 1, .3);
            sound(at, SoundEvents.GLASS_BREAK, 1, 1.6F);
        }
    }

    // ------------------------------------------------------------------
    // Support: Vanishing Act
    // ------------------------------------------------------------------

    /**
     * Every friend within fourteen blocks is wrapped in a bubble of force and bent out of sight for twelve seconds. Each
     * bubble takes ten points of harm before it breaks; foes near when it begins are dazzled blind.
     */
    public static final class VanishingAct extends Run {
        public static final int TICKS = 240, INTRO = 22;
        public static final double RANGE = 14;
        public static final float BUBBLE = 10;
        private final Map<LivingEntity, Float> bubbles = new HashMap<>();

        public VanishingAct(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "prism_haze", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.FORCE_BUBBLE, 22);
            heroShot(INTRO, 1);
            for (LivingEntity f : friends(RANGE)) {
                bubbles.put(f, BUBBLE);
                StyleOps.effect(f, MobEffects.INVISIBILITY, TICKS, 0);
                piece(Piece.FORCE_ULT_BUBBLE, f.position(), f.position(), f.getBbHeight(), f, 0);
            }
            for (LivingEntity t : StyleOps.foes(player, player.position(), 8)) StyleOps.effect(t, MobEffects.BLINDNESS, 60, 0);
            sound(player.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 1.4F, 1.2F);
        }

        @Override protected void beat(int age) {
            if (age % 60 == 0 && age < TICKS)
                for (var e : bubbles.entrySet()) if (e.getValue() > 0 && e.getKey().isAlive())
                    piece(Piece.FORCE_ULT_BUBBLE, e.getKey().position(), e.getKey().position(), e.getKey().getBbHeight(), e.getKey(), age / 60);
        }

        @Override protected void incoming(LivingIncomingDamageEvent event, LivingEntity victim) {
            Float left = bubbles.get(victim);
            if (left == null || left <= 0 || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
            float taken = Math.min(left, event.getAmount());
            bubbles.put(victim, left - taken);
            event.setAmount(event.getAmount() - taken);
            if (left - taken <= 0) {
                piece(Piece.FORCE_SHATTER, victim.getBoundingBox().getCenter(), victim.getBoundingBox().getCenter(), victim.getBbHeight(), victim, 0);
                sound(victim.position(), SoundEvents.GLASS_BREAK, 1, 1.4F);
            }
            if (event.getAmount() <= 0) event.setCanceled(true);
        }

        public Map<LivingEntity, Float> bubbles() { return bubbles; }
    }
}
