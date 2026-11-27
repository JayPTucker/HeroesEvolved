package com.jayptucker.heroesevolved.phasing;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Ultimates.Run;
import com.jayptucker.heroesevolved.combat.Ultimates.Ultimate;
import com.jayptucker.heroesevolved.expansion.Signatures;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Density's three Ultimates, all earthbending: rock torn out of the ground and moved in chunks, never placed. The id of
 * the block under the caster rides in each piece's seed (the low 24 bits; a re-sent piece's second in the top byte), so
 * the rock is the colour of the ground it came from; {@code EarthArt} draws them from these beats.
 */
public final class DensityUltimates {
    private DensityUltimates() {}

    public static final Ultimate OFFENSE = Ultimate.offense(EarthsEmbrace::new, 10);
    public static final Ultimate DEFENSE = Ultimate.defense(StoneFortress::new);
    public static final Ultimate SUPPORT = Ultimate.support(MountainHall::new);

    private static final String POWER = "phasing";

    /** The block a piece's rock is made of (the ground under {@code at}), as the seed {@code EarthArt.colour} reads. */
    static int ground(ServerPlayer p, Vec3 at) {
        BlockState state = p.serverLevel().getBlockState(net.minecraft.core.BlockPos.containing(at.x, at.y - .5, at.z));
        return Block.getId(state.isAir() ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState() : state);
    }

    // ------------------------------------------------------------------
    // Offense: Earth's Embrace
    // ------------------------------------------------------------------

    /**
     * A stomp, and the ground swallows the foe to the waist. Two walls of rock rise either side of it and she claps them
     * shut; a pillar throws it into the air; she heaves a boulder up out of the ground and punts it, and the boulder
     * smashes the falling foe away and bursts.
     */
    public static final class EarthsEmbrace extends Run {
        public static final int SWALLOW = 8, WALLS = 14, CLAP = 32, PILLAR = 40, HEAVE = 46, PUNT = 64, IMPACT = 70, THROWN = 76, TICKS = 120;
        public static final double LIFT = 6, THROW = 8;
        private final int block;

        public EarthsEmbrace(ServerPlayer p, int tier, LivingEntity foe) {
            super(p, tier, POWER, "phase_eruption", foe);
            block = ground(p, foe.position());
        }

        @Override protected int length() { return TICKS; }

        /** The foe's height over its start: swallowed (a little down), then thrown up by the pillar. */
        public static double height(float t) {
            if (t < PILLAR) return 0;
            if (t < IMPACT) return LIFT * Math.min(1, (t - PILLAR) / 6F) - (t > PILLAR + 14 ? (t - PILLAR - 14) * .12 : 0);
            return 0;
        }

        /** Where the boulder is: rising from the ground before her, then flying at the foe. */
        public static Vec3 boulder(Vec3 origin, Vec3 facing, Vec3 base, float t) {
            Vec3 rest = origin.add(facing.scale(1.6)), held = rest.add(0, 1.4, 0);
            if (t < PUNT) return rest.lerp(held, Mth.clamp((t - HEAVE) / 10F, 0, 1));
            return held.lerp(base.add(0, height(IMPACT - 1) + .8, 0), Mth.clamp((t - PUNT) / (IMPACT - PUNT), 0, 1));
        }

        @Override protected void open() {
            face(foeCentre());
            cue();
            pose(ArmPose.EARTH_STOMP, SWALLOW);
            piece(Piece.EARTH_ULT_EMBRACE, origin, base, foe.getBbHeight(), player, foeId());
            piece(Piece.EARTH_ULT_ROCK, origin, base, 1, player, block);
            sound(player.position(), SoundEvents.WARDEN_DIG, 1.2F, .8F);
            double d = Math.sqrt(base.subtract(origin).horizontalDistanceSqr());
            cinema(cam()
                    .key(0, -2.6, 2.6, 1.8, d, 0, .8)
                    .key(WALLS + 6, d - 4.5, 4, 2.2, d, 0, 1)
                    .key(CLAP + 2, d - 3.5, -1, 1.6, d, 0, 1.2)
                    .watch(PILLAR + 6, d - 6, 5, 1.5, foe)
                    .key(PUNT, -3, -3, 2, d * .5, 0, 2.5)
                    .key(IMPACT + 4, d - 4, 6, 3.5, d + THROW * .5, 0, 1.5)
                    .key(TICKS - 14, d - 6, 7, 4, d + THROW * .5, 0, 1)
                    .until(TICKS - 10));
        }

        @Override protected void beat(int age) {
            if (age == SWALLOW) {
                DensityService.embed(foe, player, PILLAR - SWALLOW, false, .45);
                sound(foeAt(), SoundEvents.ROOTED_DIRT_BREAK, 1.4F, .6F);
                sound(foeAt(), SoundEvents.DEEPSLATE_BREAK, 1.2F, .5F);
            }
            if (age == WALLS) { pose(ArmPose.EARTH_RAISE, 10); sound(foeAt(), SoundEvents.WARDEN_EMERGE, .6F, 1.4F); }
            if (age == CLAP - 3) pose(ArmPose.EARTH_WALL, 8);
            if (age == CLAP) {
                hit(foe, 8);
                Takedowns.flinch(foe);
                sound(foeAt(), SoundEvents.DEEPSLATE_BREAK, 1.8F, .5F);
                shake(foeAt(), 16, .35F, 8);
            }
            if (age == PILLAR) {
                pose(ArmPose.EARTH_RAISE, 8);
                hit(foe, 2);
                sound(foeAt(), SoundEvents.GENERIC_EXPLODE.value(), .8F, 1.4F);
            }
            if (age >= PILLAR && age < IMPACT) lift(height(age));
            if (age == HEAVE) { pose(ArmPose.EARTH_HEAVE, PUNT - HEAVE); sound(at(1.6, 0, 0), SoundEvents.DEEPSLATE_BREAK, 1.4F, .6F); }
            if (age == PUNT) { pose(ArmPose.EARTH_STRIKE, 8); sound(player.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.4F, .6F); }
            if (age == IMPACT) {
                hit(foe, 12);
                for (LivingEntity t : others(foeAt(), 4)) { hit(t, 6); shove(t, foeAt(), 1, .4); }
                sound(foeAt(), SoundEvents.GENERIC_EXPLODE.value(), 1.4F, .8F);
                sound(foeAt(), SoundEvents.DEEPSLATE_BREAK, 2, .5F);
                shake(foeAt(), 24, .55F, 10);
            }
            if (age > IMPACT && age <= THROWN) {
                float k = (age - IMPACT) / (float) (THROWN - IMPACT);
                place(StyleOps.ground(level, base.add(facing.scale(THROW * k)).add(0, 1 + 2 * Math.sin(k * Math.PI), 0)).add(0, 2 * Math.sin(k * Math.PI), 0));
            }
            if (age == THROWN) { slam(); release(); }
        }
    }

    // ------------------------------------------------------------------
    // Defense: Stone Fortress
    // ------------------------------------------------------------------

    /**
     * Four slabs of rock tear out of the ground and stand round her, turning slowly, for twelve seconds. She sinks into
     * Horse Stance (free), takes half of every blow, and every shot that meets a slab stops there; anyone who comes in
     * close is shackled into the ground. When it ends, the slabs are flung outward and hit whatever is in their way.
     */
    public static final class StoneFortress extends Run {
        public static final int TICKS = 240, INTRO = 22, SLABS = 4;
        public static final double RADIUS = 2.4, ARC = .55;
        private final int block;
        private final Map<UUID, Integer> shackled = new HashMap<>();

        public StoneFortress(ServerPlayer p, int tier, LivingEntity unused) {
            super(p, tier, POWER, "dense_bulwark", null);
            block = ground(p, p.position());
        }

        @Override protected int length() { return TICKS; }

        /** Where slab {@code i} stands at {@code t}, as an angle round her. */
        public static double angle(int i, float t) { return i * Math.PI / 2 + t * .02; }

        @Override protected void open() {
            cue();
            pose(ArmPose.EARTH_STANCE, 24);
            heroShot(INTRO, .8);
            DensityService.setNotch(player, DensityService.FULL_DENSE, TICKS);
            piece(Piece.EARTH_ULT_FORTRESS, player.position(), player.position(), (float) RADIUS, player, block);
            sound(player.position(), SoundEvents.WARDEN_DIG, 1.4F, .6F);
        }

        @Override protected void beat(int age) {
            if (age % 20 == 0 && age < TICKS) piece(Piece.EARTH_ULT_FORTRESS, player.position(), player.position(), (float) RADIUS, player, block | age / 20 << 24);
            Vec3 body = player.getBoundingBox().getCenter();
            for (Projectile shot : shots(body, RADIUS + 1)) {
                Vec3 d = shot.position().subtract(player.position());
                double a = Math.atan2(d.z, d.x);
                for (int i = 0; i < SLABS; i++) {
                    double off = Mth.wrapDegrees(Math.toDegrees(a - angle(i, age)));
                    if (Math.abs(Math.toRadians(off)) > ARC) continue;
                    piece(Piece.EARTH_ULT_CHIP, shot.position(), body, .6F, null, block);
                    shot.discard();
                    sound(shot.position(), SoundEvents.STONE_HIT, 1, 1.2F);
                    break;
                }
            }
            if (age % 5 == 0) for (LivingEntity t : StyleOps.foes(player, player.position(), 3)) {
                int last = shackled.getOrDefault(t.getUUID(), -100);
                if (age - last < 60 || Signatures.boss(t)) continue;
                shackled.put(t.getUUID(), age);
                DensityService.embed(t, player, 40, false, .4);
            }
        }

        @Override protected void incoming(LivingIncomingDamageEvent event, LivingEntity victim) {
            if (victim != player || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
            event.setAmount(event.getAmount() * .5F);
        }

        @Override protected void finish() {
            Vec3 at = player.position();
            for (LivingEntity t : StyleOps.foes(player, at, 7)) {
                Vec3 d = t.position().subtract(at);
                double a = Math.atan2(d.z, d.x);
                for (int i = 0; i < SLABS; i++) {
                    if (Math.abs(Math.toRadians(Mth.wrapDegrees(Math.toDegrees(a - angle(i, TICKS))))) > ARC) continue;
                    hit(t, 5);
                    shove(t, at, 1.3, .4);
                    break;
                }
            }
            piece(Piece.EARTH_ULT_FORTRESS, at, at, (float) RADIUS, player, block | 0xFF << 24);
            sound(at, SoundEvents.DEEPSLATE_BREAK, 2, .6F);
        }
    }

    // ------------------------------------------------------------------
    // Support: Mountain Hall
    // ------------------------------------------------------------------

    /**
     * A dome of rock rises over her and everyone within five blocks and stands for twelve seconds: every shot breaks on
     * it, foes are pushed back out, and everyone inside mends and takes half the harm. Then it crumbles outward.
     */
    public static final class MountainHall extends Run {
        public static final int TICKS = 240, INTRO = 24;
        public static final double RADIUS = 5;
        private Vec3 centre;
        private final int block;

        public MountainHall(ServerPlayer p, int tier, LivingEntity unused) {
            super(p, tier, POWER, "phase_field", null);
            block = ground(p, p.position());
        }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            centre = player.position();
            cue();
            pose(ArmPose.EARTH_TENT, 24);
            heroShot(INTRO, 2);
            piece(Piece.EARTH_ULT_HALL, centre, centre, (float) RADIUS, null, block);
            sound(centre, SoundEvents.WARDEN_EMERGE, .8F, .9F);
        }

        @Override protected void beat(int age) {
            if (age % 20 == 0 && age < TICKS) {
                piece(Piece.EARTH_ULT_HALL, centre, centre, (float) RADIUS, null, block | age / 20 << 24);
                for (LivingEntity f : StyleOps.friends(player, centre, RADIUS)) StyleOps.heal(f, 1);
            }
            for (Projectile shot : shots(centre.add(0, 1.5, 0), RADIUS + 1.5)) {
                if (shot.position().distanceToSqr(centre) > (RADIUS + .8) * (RADIUS + .8)) continue;
                piece(Piece.EARTH_ULT_CHIP, shot.position(), centre.add(0, 2, 0), .6F, null, block);
                shot.discard();
            }
            if (age % 5 == 0) for (LivingEntity t : StyleOps.foes(player, centre, RADIUS - .5)) shove(t, centre, 1, .2);
        }

        @Override protected void incoming(LivingIncomingDamageEvent event, LivingEntity victim) {
            if (centre == null || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
            if (StyleOps.isFriend(player, victim) && victim.position().distanceToSqr(centre) <= RADIUS * RADIUS) event.setAmount(event.getAmount() * .5F);
        }

        @Override protected void finish() {
            for (LivingEntity t : StyleOps.foes(player, centre, RADIUS + 3)) shove(t, centre, 1.2, .4);
            piece(Piece.EARTH_ULT_HALL, centre, centre, (float) RADIUS, null, block | 0xFF << 24);
            sound(centre, SoundEvents.DEEPSLATE_BREAK, 2, .5F);
        }

        public Vec3 centre() { return centre; }
    }
}
