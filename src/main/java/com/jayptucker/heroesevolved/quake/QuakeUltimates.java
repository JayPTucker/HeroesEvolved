package com.jayptucker.heroesevolved.quake;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Ultimates.Run;
import com.jayptucker.heroesevolved.combat.Ultimates.Ultimate;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Quake's three Ultimates; {@code QuakeArt} draws them from these beats, all in rings of pressure. */
public final class QuakeUltimates {
    private QuakeUltimates() {}

    public static final Ultimate OFFENSE = Ultimate.offense(Heartquake::new, 9);
    public static final Ultimate DEFENSE = Ultimate.defense(PressureBarrier::new);
    public static final Ultimate SUPPORT = Ultimate.support(Groundswell::new);

    private static final String POWER = "quake";
    private static final ResourceLocation STEADY = ResourceLocation.fromNamespaceAndPath("heroesevolved", "ultimate_steady");

    // ------------------------------------------------------------------
    // Offense: Heartquake
    // ------------------------------------------------------------------

    /**
     * She plants her feet and a ring of pressure locks round the foe; it is lifted off the ground and shaken from the
     * inside, rings shivering up and down it. She draws both palms back, red and white cracks running up her forearms
     * (the cost, in Strain), and drives them forward: a tube of rings blasts the foe twelve blocks along the ground,
     * ploughing a fault behind it. One punch into the ground and the whole fault erupts.
     */
    public static final class Heartquake extends Run {
        public static final int LOCK = 10, SHAKE = 12, SHAKES = 7, EVERY = 4, DRAW = 42, BLAST = 58, BLASTED = 64, ERUPT = 72, TICKS = 120;
        public static final double THROW = 12, LIFT = 1.8;

        public Heartquake(ServerPlayer p, int tier, LivingEntity foe) { super(p, tier, POWER, "fissure_maw", foe); }

        @Override protected int length() { return TICKS; }

        /** Where the blast throws the foe (along the facing, on the ground). */
        public Vec3 landing() { return base.add(facing.scale(THROW)); }

        @Override protected void open() {
            face(foeCentre());
            cue();
            pose(ArmPose.QUAKE_PALM, 14);
            piece(Piece.QUAKE_ULT_HEART, origin, base, foe.getBbHeight());
            sound(player.position(), SoundEvents.WARDEN_HEARTBEAT, 1.6F, .6F);
            double d = Math.sqrt(base.subtract(origin).horizontalDistanceSqr());
            cinema(cam()
                    .key(0, -2.6, 2.4, 1.8, d, 0, 1.2)
                    .key(SHAKE + 14, d - 2.8, 3.2, 1.4, d, 0, 2)
                    .key(DRAW + 6, -1.6, -2.6, 1.6, 0, 0, 1.3)
                    .key(BLAST + 2, d * .5, 7, 3, d + THROW * .5, 0, 1)
                    .key(ERUPT + 4, d - 4, 9, 6, d + THROW * .6, 0, 0)
                    .key(TICKS - 14, d - 7, 10, 7, d + THROW * .5, 0, .5)
                    .until(TICKS - 10));
        }

        @Override protected void beat(int age) {
            if (age == LOCK) sound(foeCentre(), SoundEvents.BEACON_DEACTIVATE, 1.2F, .5F);
            if (age >= SHAKE && age < BLAST) {
                float k = Math.min(1, (age - SHAKE) / 8F);
                double j = .08 * (age % 2 == 0 ? 1 : -1);
                place(base.add(side.scale(j)).add(0, LIFT * k, 0));
            }
            if (age >= SHAKE && age < SHAKE + SHAKES * EVERY && (age - SHAKE) % EVERY == 0) {
                hit(foe, 1);
                Takedowns.flinch(foe);
                sound(foeCentre(), SoundEvents.WARDEN_HEARTBEAT, 1, 1.2F + (age - SHAKE) * .015F);
            }
            if (age == DRAW) {
                pose(ArmPose.QUAKE_CLENCH, BLAST - DRAW);
                QuakeKit.strain(player, 30);
                sound(player.position(), SoundEvents.ANVIL_LAND, .6F, .5F);
            }
            if (age == BLAST) {
                pose(ArmPose.QUAKE_RELEASE, 10);
                hit(foe, 12);
                sound(foeCentre(), SoundEvents.GENERIC_EXPLODE.value(), 1.6F, 1.2F);
                sound(foeCentre(), SoundEvents.WARDEN_SONIC_BOOM, .8F, 1.6F);
                shake(player.position(), 24, .5F, 10);
            }
            if (age > BLAST && age <= BLASTED) {
                float k = (age - BLAST) / (float) (BLASTED - BLAST);
                Vec3 to = StyleOps.ground(level, base.lerp(landing(), k).add(0, 1, 0));
                place(to);
            }
            if (age == ERUPT) {
                pose(ArmPose.QUAKE_PUNCH_DOWN, 10);
                hit(foe, 4);
                for (LivingEntity t : others(base.lerp(landing(), .5), THROW * .6)) {
                    Vec3 p = t.position();
                    double along = p.subtract(base).dot(facing), off = Math.abs(p.subtract(base).dot(side));
                    if (along < -1 || along > THROW + 1 || off > 2.5) continue;
                    hit(t, 6);
                    if (!com.jayptucker.heroesevolved.expansion.Signatures.boss(t)) StyleOps.push(t, new Vec3(0, .9, 0));
                }
                sound(base.lerp(landing(), .5), SoundEvents.GENERIC_EXPLODE.value(), 1.6F, .6F);
                sound(base.lerp(landing(), .5), SoundEvents.DEEPSLATE_BREAK, 2, .5F);
                shake(base.lerp(landing(), .5), 28, .6F, 12);
                release();
            }
        }
    }

    // ------------------------------------------------------------------
    // Defense: Pressure Barrier
    // ------------------------------------------------------------------

    /**
     * A sphere of shimmering pressure stands round her for twelve seconds. Shots that touch it are turned straight back
     * at whoever fired them; anyone who strikes her is blown six blocks away; nothing can knock her off her feet, and
     * blows that land are a third lighter. When it ends the sphere pops outward.
     */
    public static final class PressureBarrier extends Run implements com.jayptucker.heroesevolved.combat.Ultimates.Deflecting {
        public static final int TICKS = 240, INTRO = 22;
        public static final double RADIUS = 3.5;
        private final Map<UUID, Integer> blown = new HashMap<>();

        public PressureBarrier(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "stone_rampart", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.QUAKE_SHELL, 22);
            heroShot(INTRO, .5);
            var knock = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (knock != null) knock.addOrUpdateTransientModifier(new AttributeModifier(STEADY, 1, AttributeModifier.Operation.ADD_VALUE));
            piece(Piece.QUAKE_ULT_BARRIER, player.position(), player.position(), (float) RADIUS, player, 0);
            sound(player.position(), SoundEvents.BEACON_ACTIVATE, 1, .6F);
        }

        @Override protected void beat(int age) {
            if (age % 20 == 0 && age < TICKS) piece(Piece.QUAKE_ULT_BARRIER, player.position(), player.position(), (float) RADIUS, player, age / 20);
            Vec3 body = player.getBoundingBox().getCenter();
            for (Projectile shot : shots(body, RADIUS + .6)) {
                Vec3 v = shot.getDeltaMovement();
                if (v.dot(body.subtract(shot.position())) <= 0) continue;
                StyleOps.push(shot, v.scale(-1.1));
                shot.setOwner(player);
                piece(Piece.QUAKE_ULT_RIPPLE, shot.position(), body, .6F, null, age);
                sound(shot.position(), SoundEvents.SHIELD_BLOCK, .7F, 1.6F);
            }
        }

        @Override public boolean deflects(LivingEntity body) { return body == player; }

        @Override public void turn(net.minecraft.world.entity.Entity shot) {
            Vec3 at = shot == null ? player.getEyePosition() : shot.position();
            piece(Piece.QUAKE_ULT_RIPPLE, at, player.getBoundingBox().getCenter(), .6F, null, age);
            sound(at, SoundEvents.SHIELD_BLOCK, .7F, 1.6F);
            if (shot != null) shot.discard();
        }

        @Override protected void incoming(LivingIncomingDamageEvent event, LivingEntity victim) {
            if (victim != player || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
            event.setAmount(event.getAmount() * .67F);
            if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != player && StyleOps.isFoe(player, attacker)
                    && attacker.distanceToSqr(player) < 25) {
                int last = blown.getOrDefault(attacker.getUUID(), -100);
                if (age - last < 20) return;
                blown.put(attacker.getUUID(), age);
                hit(attacker, 2);
                shove(attacker, player.position(), 1.6, .5);
                piece(Piece.QUAKE_ULT_RIPPLE, attacker.getBoundingBox().getCenter(), player.getBoundingBox().getCenter(), 1.2F, null, age);
                sound(attacker.position(), SoundEvents.GENERIC_EXPLODE.value(), .6F, 1.6F);
            }
        }

        @Override protected void tidy(boolean broken) {
            var knock = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (knock != null) knock.removeModifier(STEADY);
        }

        @Override protected void finish() {
            Vec3 at = player.position();
            for (LivingEntity t : StyleOps.foes(player, at, RADIUS + 3.5)) { hit(t, 3); shove(t, at, 1.4, .5); }
            piece(Piece.QUAKE_ULT_BARRIER, at, at, (float) RADIUS, player, -1);
            sound(at, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 1.4F);
        }
    }

    // ------------------------------------------------------------------
    // Support: Groundswell
    // ------------------------------------------------------------------

    /**
     * The ground rings outward under every friend within twelve blocks. For twelve seconds none of them can be knocked
     * off their feet, they take less harm, and every blow they land carries a little shockwave of its own.
     */
    public static final class Groundswell extends Run {
        public static final int TICKS = 240, INTRO = 22;
        public static final double RANGE = 12;
        private final List<LivingEntity> rooted = new ArrayList<>();

        public Groundswell(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "bedrock_stance", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.QUAKE_ROOT, 20);
            heroShot(INTRO, 1);
            rooted.addAll(friends(RANGE));
            for (LivingEntity f : rooted) {
                var knock = f.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
                if (knock != null) knock.addOrUpdateTransientModifier(new AttributeModifier(STEADY, 1, AttributeModifier.Operation.ADD_VALUE));
                StyleOps.effect(f, MobEffects.DAMAGE_RESISTANCE, TICKS, 1);
                piece(Piece.QUAKE_ULT_SWELL, f.position(), f.position(), f.getBbHeight(), f, 0);
            }
            sound(player.position(), SoundEvents.WARDEN_EMERGE, .5F, 1.6F);
            shake(player.position(), 16, .25F, 8);
        }

        @Override protected void beat(int age) {
            if (age % 40 == 0 && age < TICKS)
                for (LivingEntity f : rooted) if (f.isAlive()) piece(Piece.QUAKE_ULT_SWELL, f.position(), f.position(), f.getBbHeight(), f, age / 40);
        }

        @Override protected void incoming(LivingIncomingDamageEvent event, LivingEntity victim) {
            var source = event.getSource();
            if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker || !rooted.contains(attacker)
                    || !StyleOps.isFoe(player, victim)) return;
            event.setAmount(event.getAmount() + 2);
            shove(victim, attacker.position(), .7, .25);
            piece(Piece.QUAKE_ULT_RIPPLE, victim.getBoundingBox().getCenter(), attacker.getBoundingBox().getCenter(), .9F, null, age);
        }

        @Override protected void tidy(boolean broken) {
            for (LivingEntity f : rooted) {
                var knock = f.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
                if (knock != null) knock.removeModifier(STEADY);
            }
        }

        public List<LivingEntity> rooted() { return rooted; }
    }
}
