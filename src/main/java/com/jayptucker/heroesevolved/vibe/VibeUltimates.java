package com.jayptucker.heroesevolved.vibe;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Ultimates.Run;
import com.jayptucker.heroesevolved.combat.Ultimates.Ultimate;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import com.jayptucker.heroesevolved.telepathy.TelepathyUltimates;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.List;

/** Vibe's three Ultimates, all breaches; {@code VibeArt} draws them from these beats. */
public final class VibeUltimates {
    private VibeUltimates() {}

    public static final Ultimate OFFENSE = Ultimate.offense(DimensionDrop::new, 10);
    public static final Ultimate DEFENSE = Ultimate.defense(BetweenWorlds::new);
    public static final Ultimate SUPPORT = Ultimate.support(SafeHarbor::new);

    private static final String POWER = "vibe";

    // ------------------------------------------------------------------
    // Offense: Dimension Drop
    // ------------------------------------------------------------------

    /**
     * A breach opens under the foe and it falls through. Breaches open in the air all round: it comes out of one and a
     * blast of his palm knocks it into the next, out of another, into the next, five times; the last breach opens high in
     * the sky, and the foe falls out of it into the ground.
     */
    public static final class DimensionDrop extends Run {
        public static final int OPEN = 6, DROP = 10, FIRST = 16, EVERY = 10, BOUNCES = 5, SKY = 68, FALL = 72, LAND = 82, TICKS = 120;
        public static final double RING = 4.2, SKY_HEIGHT = 11;

        public DimensionDrop(ServerPlayer p, int tier, LivingEntity foe) { super(p, tier, POWER, "vibe_crosswave", foe); }

        @Override protected int length() { return TICKS; }

        /** The {@code i}th breach in the air round the foe's spot (where it comes out on bounce {@code i}). */
        public static Vec3 breach(Vec3 base, Vec3 facing, int i) {
            double a = Math.atan2(facing.z, facing.x) + Math.PI / 2 + i * 2.4;
            return base.add(Math.cos(a) * RING, 2 + (i % 3) * 1.1, Math.sin(a) * RING);
        }

        @Override protected void open() {
            face(foeCentre());
            cue();
            pose(ArmPose.VIBE_PALM, 10);
            piece(Piece.VIBE_ULT_DROP, origin, base, foe.getBbHeight());
            sound(base, SoundEvents.PORTAL_TRIGGER, .8F, 1.6F);
            double d = Math.sqrt(base.subtract(origin).horizontalDistanceSqr());
            cinema(cam()
                    .key(0, -2.6, 2.2, 1.8, d, 0, .4)
                    .key(FIRST, d - 8, 4, 5, d, 0, 2.5)
                    .key(FIRST + EVERY * 3, d - 9, -5, 6, d, 0, 2.8)
                    .key(SKY, d - 7, 4, 3, d, 0, SKY_HEIGHT * .7)
                    .key(LAND + 2, d - 7, 5, 3.5, d, 0, .5)
                    .key(TICKS - 14, d - 9, 6.5, 4, d, 0, 1)
                    .until(TICKS - 10));
        }

        @Override protected void beat(int age) {
            if (age == DROP) { hideFoe(true); sound(base, SoundEvents.ENDERMAN_TELEPORT, 1, .6F); }
            for (int i = 0; i < BOUNCES; i++) {
                int out = FIRST + i * EVERY;
                if (age == out) {
                    Vec3 at = breach(base, facing, i);
                    place(at.subtract(0, foe.getBbHeight() * .5, 0));
                    hideFoe(false);
                    face(foeCentre());
                    pose(i % 2 == 0 ? ArmPose.VIBE_PALM : ArmPose.VIBE_CHANNEL, 6);
                    sound(at, SoundEvents.ENDERMAN_TELEPORT, .9F, 1.2F + i * .08F);
                }
                if (age == out + 4) {
                    hit(foe, 2.5F);
                    Takedowns.flinch(foe);
                    sound(foeCentre(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1, 1.2F);
                }
                if (age > out + 4 && age < out + EVERY) {
                    Vec3 next = i + 1 < BOUNCES ? breach(base, facing, i + 1) : base.add(0, SKY_HEIGHT, 0);
                    Vec3 from = breach(base, facing, i);
                    float k = (age - out - 4) / (float) (EVERY - 4);
                    place(from.lerp(from.add(next.subtract(from).normalize().scale(1.5)), k).subtract(0, foe.getBbHeight() * .5, 0));
                    if (age == out + EVERY - 1) hideFoe(true);
                }
            }
            if (age == SKY) sound(base.add(0, SKY_HEIGHT, 0), SoundEvents.PORTAL_TRIGGER, 1.2F, .8F);
            if (age == FALL) { place(base.add(0, SKY_HEIGHT, 0)); hideFoe(false); }
            if (age > FALL && age <= LAND) {
                float k = (age - FALL) / (float) (LAND - FALL);
                place(base.add(0, SKY_HEIGHT * (1 - k * k), 0));
            }
            if (age == LAND) {
                pose(ArmPose.VIBE_RIFT, 12);
                hit(foe, 12);
                for (LivingEntity t : others(base, 4)) { hit(t, 5); shove(t, base, 1, .4); }
                sound(base, SoundEvents.GENERIC_EXPLODE.value(), 1.4F, .8F);
                sound(base, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1.2F, .6F);
                shake(base, 24, .5F, 10);
                release();
            }
        }
    }

    // ------------------------------------------------------------------
    // Defense: Between Worlds
    // ------------------------------------------------------------------

    /**
     * For ten seconds Vibe stands half out of this world, a cold blue ghost of himself. Blows pass straight through
     * him; shots go into a breach in front of him and come out of one beside whoever fired them, at them.
     */
    public static final class BetweenWorlds extends Run {
        public static final int TICKS = 200, INTRO = 22;

        public BetweenWorlds(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "vibe_mirror", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.VIBE_CHANNEL, 22);
            heroShot(INTRO, .5);
            piece(Piece.VIBE_ULT_GHOST, player.position(), player.position(), 1, player, 0);
            sound(player.position(), SoundEvents.PORTAL_AMBIENT, 1, 1.6F);
        }

        @Override protected void beat(int age) {
            if (age % 20 == 0 && age < TICKS) piece(Piece.VIBE_ULT_GHOST, player.position(), player.position(), 1, player, age / 20);
            Vec3 body = player.getBoundingBox().getCenter();
            for (Projectile shot : shots(body, 3)) {
                if (shot.getDeltaMovement().dot(body.subtract(shot.position())) <= 0) continue;
                Entity owner = shot.getOwner();
                Vec3 in = shot.position();
                if (owner != null && owner.isAlive() && owner.level() == level && owner.distanceToSqr(player) < 48 * 48) {
                    Vec3 at = owner.getBoundingBox().getCenter(), from = at.add(owner.getLookAngle().scale(2.5)).add(0, .2, 0);
                    shot.teleportTo(from.x, from.y, from.z);
                    StyleOps.push(shot, at.subtract(from).normalize().scale(Math.max(1, shot.getDeltaMovement().length())));
                    shot.setOwner(player);
                    piece(Piece.VIBE_ULT_PASS, in, from, 1, null, age);
                } else {
                    shot.discard();
                    piece(Piece.VIBE_ULT_PASS, in, in, 1, null, age);
                }
                sound(in, SoundEvents.ENDERMAN_TELEPORT, .6F, 1.8F);
            }
        }

        @Override protected void incoming(LivingIncomingDamageEvent event, LivingEntity victim) {
            if (victim != player || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
            if (event.getSource().getEntity() != null || event.getSource().getDirectEntity() != null) event.setCanceled(true);
            else event.setAmount(event.getAmount() * .5F);
        }
    }

    // ------------------------------------------------------------------
    // Support: Safe Harbor
    // ------------------------------------------------------------------

    /**
     * Breaches open beside every friend within twenty blocks and bring them through to Vibe's side, never into a wall,
     * and a wide breach opens over them all for twelve seconds: under it, nothing hunting them can keep track of them,
     * and they mend.
     */
    public static final class SafeHarbor extends Run {
        public static final int TICKS = 240, INTRO = 22;
        public static final double RANGE = 20, RADIUS = 4.5;
        private Vec3 centre;
        private final List<LivingEntity> sheltered = new ArrayList<>();

        public SafeHarbor(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "vibe_sanctuary", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            centre = player.position();
            cue();
            pose(ArmPose.VIBE_CHANNEL, 22);
            heroShot(INTRO, 1.5);
            List<LivingEntity> friends = new ArrayList<>(friends(RANGE));
            friends.remove(player);
            for (int i = 0; i < friends.size(); i++) {
                LivingEntity f = friends.get(i);
                double a = i * Math.PI * 2 / friends.size();
                Vec3 want = centre.add(Math.cos(a) * 1.8, 0, Math.sin(a) * 1.8);
                if (f.position().distanceToSqr(centre) > RADIUS * RADIUS && SpeedService.safe(f, want)) {
                    piece(Piece.VIBE_ULT_PASS, f.position().add(0, 1, 0), want.add(0, 1, 0), 1, null, i);
                    if (f instanceof ServerPlayer other) StyleHits.blink(other, want);
                    else { f.teleportTo(want.x, want.y, want.z); f.resetFallDistance(); }
                }
                sheltered.add(f);
            }
            sheltered.add(player);
            piece(Piece.VIBE_ULT_HARBOR, centre, centre, (float) RADIUS, null, 0);
            sound(centre, SoundEvents.PORTAL_TRIGGER, 1, 1.2F);
        }

        @Override protected void beat(int age) {
            if (age % 20 != 0 || age >= TICKS) return;
            piece(Piece.VIBE_ULT_HARBOR, centre, centre, (float) RADIUS, null, age / 20);
            List<LivingEntity> inside = StyleOps.friends(player, centre, RADIUS + .5);
            for (LivingEntity f : inside) StyleOps.heal(f, 1.2F);
            for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24), m -> m.getTarget() != null && inside.contains(m.getTarget())))
                TelepathyUltimates.hush(mob);
        }

        public List<LivingEntity> sheltered() { return sheltered; }
    }
}
