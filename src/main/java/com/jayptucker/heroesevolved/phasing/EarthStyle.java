package com.jayptucker.heroesevolved.phasing;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.ComboStyle;
import com.jayptucker.heroesevolved.combat.FightingStyles.Dodge;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.StyleMotion;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Tiger and Crane, Density's earthbending fist after Hung Gar. Every blow pulls rock up out of the ground into the hand:
 * the jabs are <b>Tiger Claws</b> (the rear one lands in a stone gauntlet), the finisher is <b>Twin Slab</b> (both palms
 * push a slab out of the ground into the foe), crouching is a <b>Horse Stomp</b> (a ring of spikes round you). An
 * earthbender doesn't hop out of the way: a side dodge is an <b>Earth Slide</b> (the ground under your feet carries you,
 * braced, so a blow is only partly taken), and the back dodge (from level 2) sinks into the floor and surfaces a few
 * blocks away. A blow taken in an Earth Slide is <b>Neutral Jing</b>: a pillar answers the attacker at once, and the next
 * claw goes to them.
 */
public final class EarthStyle extends ComboStyle {
    public static final EarthStyle STYLE = new EarthStyle();
    public static final double CLAW_REACH = 4, SLAB_REACH = 5, STOMP_RADIUS = 3.5, ANSWER_REACH = 10;
    /** How long an Earth Slide holds you braced, and how much of a blow taken in it still lands. */
    public static final int SLIDE_TICKS = 8;
    public static final float BRACED = .5F;

    private EarthStyle() { super(ModAbilities.PHASING_ID, "density.heroesevolved.too_tired"); }

    private static ServerLevel world(ServerPlayer p) { return p.serverLevel(); }

    /** The rock the fist is made of: the ground underfoot, or cobblestone with no earth there. */
    private static BlockState rock(ServerPlayer p) {
        BlockState ground = DensityService.footing(p);
        return ground == null ? Blocks.COBBLESTONE.defaultBlockState() : ground;
    }

    @Override
    protected void throwMove(ServerPlayer p, Fighter f, Move move, int level, boolean loaded) {
        DensityService.track(p);
        switch (move) {
            case LEAD, REAR -> claw(p, f, level, move == Move.REAR, loaded);
            case FINISHER -> slab(p, level);
            case LOW -> stomp(p, level);
        }
    }

    /** Tiger Claw: a fist of stone torn up from the ground; the rear claw lands in a full gauntlet. */
    private void claw(ServerPlayer p, Fighter f, int level, boolean rear, boolean loaded) {
        Vec3 fist = StyleHits.hand(p, 1, !rear);
        LivingEntity target = loaded ? f.marked() : null;
        if (target != null && target.distanceTo(p) > ANSWER_REACH) target = null;
        if (target == null) target = StyleHits.aimed(p, CLAW_REACH, .5);
        boolean gauntlet = rear || loaded;
        BlockState rock = rock(p);
        if (target != null) {
            float damage = (2.4F + .35F * level) * (gauntlet ? 1.4F : 1) * (loaded ? 1.5F : 1) * DensityService.rooted(p);
            StyleHits.hurt(p, target, damage);
            StyleHits.shove(target, p.getLookAngle().multiply(1, 0, 1).normalize().scale(gauntlet ? .55 : .2).add(0, .06, 0));
            Stones.dust(world(p), rock, target.getBoundingBox().getCenter(), 6, .25);
        }
        EarthGeometry.show(world(p), Piece.EARTH_CLAW, fist, target == null ? fist.add(p.getLookAngle()) : target.getBoundingBox().getCenter(),
                gauntlet ? 1.4F : 1, p, rock);
        pose(p, rear ? ArmPose.EARTH_STRIKE : ArmPose.EARTH_CLAW, 8);
        StyleHits.sound(p, SoundEvents.STONE_HIT, .7F, gauntlet ? .7F : 1.1F);
        if (gauntlet) StyleHits.sound(p, SoundEvents.PLAYER_ATTACK_STRONG, .6F, .6F);
    }

    /** Twin Slab: both palms drive forward and a slab of ground leaps out ahead into the foe. */
    private void slab(ServerPlayer p, int level) {
        Vec3 fwd = StyleHits.level(p);
        BlockState rock = rock(p);
        Vec3 at = p.position().add(fwd.scale(1.6));
        double stop = SLAB_REACH;
        for (LivingEntity target : StyleHits.foes(p, p.position().add(fwd.scale(SLAB_REACH * .5)), SLAB_REACH * .6)) {
            Vec3 to = target.position().subtract(p.position());
            if (to.multiply(1, 0, 1).normalize().dot(fwd) < .5) continue;
            StyleHits.hurt(p, target, (4.5F + level) * DensityService.rooted(p));
            StyleHits.shove(target, fwd.scale(1.1).add(0, .35, 0));
            // The slab is drawn stopping against the nearest body it struck.
            stop = Math.min(stop, Math.max(.4, to.horizontalDistance() - 1.6 - target.getBbWidth() * .5 - .2));
        }
        EarthGeometry.show(world(p), Piece.EARTH_SLAB, at, at.add(fwd.scale(stop)), 1, null, rock);
        Stones.dust(world(p), rock, at, 12, .6);
        pose(p, ArmPose.EARTH_SHOVE, 12);
        StyleHits.sound(p, SoundEvents.DEEPSLATE_PLACE, 1, .5F);
        StyleHits.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, .8F, .7F);
        CameraShakePayload.sendAround(world(p), at, 10, .2F, 5);
    }

    /** Horse Stomp: a stamp in a low stance, and a ring of shin-high spikes stabs up round you. */
    private void stomp(ServerPlayer p, int level) {
        Vec3 feet = p.position();
        for (LivingEntity target : StyleHits.foes(p, feet, STOMP_RADIUS + 1)) {
            if (Math.abs(target.getY() - feet.y) > 1.5
                    || target.position().subtract(feet).horizontalDistance() > STOMP_RADIUS + target.getBbWidth() * .5) continue;
            StyleHits.hurt(p, target, (2 + .4F * level) * DensityService.rooted(p));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3));
            StyleHits.shove(target, new Vec3(0, .35, 0));
        }
        EarthGeometry.show(world(p), Piece.EARTH_SPIKES, feet, feet, (float) STOMP_RADIUS, null, rock(p));
        pose(p, ArmPose.EARTH_STOMP, 12);
        StyleHits.sound(p, SoundEvents.ROOTED_DIRT_BREAK, 1, .5F);
        StyleHits.sound(p, SoundEvents.POINTED_DRIPSTONE_LAND, .8F, .6F);
    }

    /** Only the back dodge evades (it is underground); an Earth Slide braces instead. */
    @Override protected int evadeTicks(boolean special) { return special ? 10 : 0; }

    @Override
    protected void dodgeMove(ServerPlayer p, Fighter f, Dodge dodge, Vec3 direction, boolean special, int level) {
        Vec3 from = p.position();
        BlockState rock = rock(p);
        if (special) {
            // Down into the floor and up a few blocks away.
            Vec3 to = com.jayptucker.heroesevolved.speed.SpeedService.swept(p, direction.scale(5));
            EarthGeometry.show(world(p), Piece.EARTH_TUNNEL, from, from.subtract(0, 1, 0), 1, null, rock);
            StyleHits.blink(p, to);
            EarthGeometry.show(world(p), Piece.EARTH_TUNNEL, to, to.add(0, 1, 0), 1, null, rock);
            pose(p, ArmPose.SINK, 10);
            StyleHits.sound(p, SoundEvents.ROOTED_DIRT_BREAK, .8F, .7F);
        } else {
            f.banked = 0;
            // The ground carries you: feet planted, body upright, braced against whatever comes.
            StyleMotion.skid(p, direction.scale(.9), SLIDE_TICKS);
            StyleMotion.brace(p, SLIDE_TICKS + 4);
            EarthGeometry.show(world(p), Piece.EARTH_SLIDE, from, from.add(direction.scale(StyleMotion.skidReach(SLIDE_TICKS) * .9)), 1, p, rock);
            pose(p, dodge == Dodge.LEFT ? ArmPose.EARTH_SLIDE_LEFT : ArmPose.EARTH_SLIDE_RIGHT, SLIDE_TICKS + 4);
            StyleHits.sound(p, SoundEvents.GRAVEL_BREAK, 1, .6F);
        }
    }

    /** A blow taken in an Earth Slide lands at half strength; the first one is Neutral Jing. */
    @Override
    public float braced(ServerPlayer p, DamageSource source, float amount) {
        Fighter f = fighter(p);
        if (f.banked <= 0) counter(p, source);
        f.banked += amount;
        StyleHits.sound(p, SoundEvents.STONE_HIT, .9F, .5F);
        return amount * BRACED;
    }

    /** Neutral Jing: a pillar answers the attacker at once, and the next claw goes to them. */
    @Override
    protected void onCounter(ServerPlayer p, Fighter f, DamageSource source) {
        if (source.getEntity() instanceof LivingEntity attacker && attacker != p && attacker.distanceTo(p) <= ANSWER_REACH) {
            BlockPos ground = EarthGeometry.ground(attacker.level(), attacker.position().add(0, .5, 0));
            if (ground != null && EarthGeometry.earth(attacker.level().getBlockState(ground), false))
                Earthworks.pillarAt(p, ground.above(), Direction.UP, 2, attacker.level().getBlockState(ground),
                        (1.5F + DensityService.tier(p) * .5F) * DensityService.rooted(p));
        }
        pose(p, ArmPose.EARTH_RAISE, 8);
        say(p, "density.heroesevolved.neutral_jing");
    }
}
