package com.jayptucker.heroesevolved.speed;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.ComboStyle;
import com.jayptucker.heroesevolved.combat.FightingStyles.Dodge;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.StyleMotion;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Blur Boxing: a speedster's fists. Every jab is three blows too fast to see, landing from slightly different angles
 * with an afterimage at each; the finisher (Blitz) closes the gap in a streak of gold and stops square in front of the
 * target as the blow lands; crouching slides in under them (Skid Sweep). A blow evaded loads Too Slow: the next strike
 * is a barrage from afterimages circling the attacker.
 *
 * <p>The speedster never blinks out of a fight: every dodge is the body really moving, thrown at speed and braking.
 * Back is a Skid Stop: thrown backward, turning side-on and dropping to one knee, a hand dragging on the ground until
 * they slide to a halt. Left or right is a Skid Turn: a slide sideways, leaning away from it with the trailing hand flat
 * on the ground. Both use {@link StyleMotion#skid}, so most of the ground goes in the first few ticks and the rest is a
 * short slide; they kick up the ground's own dust at the hand and end in Superspeed's stop gust (skid marks behind,
 * dust billowing on).</p>
 *
 * <p>Drawn with Superspeed's own strokes (gold lanes, afterimages, shock rings), so it reads as the same power.</p>
 */
public final class SpeedStyle extends ComboStyle {
    public static final SpeedStyle STYLE = new SpeedStyle();
    /** A jab's reach and how many blows one click throws; the Blitz's reach and how far short of the foe it stops. */
    public static final double JAB_REACH = 4.5, BLITZ_REACH = 9, BLITZ_STOP = 1.1;
    public static final int JAB_BLOWS = 3, BARRAGE_BLOWS = 6;
    /** Skids: the starting push (blocks a tick) and how long each runs. About 5 blocks sideways, 6.5 back, 8.5 back from level 2. */
    public static final double SIDE_PUSH = 1.8, BACK_PUSH = 2.1, LONG_BACK_PUSH = 2.4;
    public static final int SIDE_TICKS = 9, BACK_TICKS = 10, LONG_BACK_TICKS = 12, BLITZ_TICKS = 6;

    /** A skid in progress: where it started, which way it goes, and what is drawn at the hand. */
    private static final class Skid {
        final long start;
        final int ticks;
        final Vec3 direction;
        final ArmPose pose;
        final float strength;
        Vec3 last;

        Skid(long start, int ticks, Vec3 direction, ArmPose pose, float strength, Vec3 at) {
            this.start = start; this.ticks = ticks; this.direction = direction; this.pose = pose; this.strength = strength; this.last = at;
        }
    }

    private static final Map<UUID, Skid> SKIDS = new HashMap<>();

    private SpeedStyle() { super(ModAbilities.SUPERSPEED_ID, "speed.heroesevolved.too_tired"); }

    private static ServerLevel world(ServerPlayer p) { return p.serverLevel(); }

    @Override
    protected void throwMove(ServerPlayer p, Fighter f, Move move, int level, boolean loaded) {
        switch (move) {
            case LEAD, REAR -> {
                LivingEntity marked = loaded ? f.marked() : null;
                if (marked != null && marked.distanceToSqr(p) < 16 * 16) barrage(p, marked, level);
                else jab(p, level, move == Move.REAR, loaded);
            }
            case FINISHER -> blitz(p, level);
            case LOW -> sweep(p, level);
        }
    }

    /** Three blows in one click, each from a slightly different angle, an afterimage left at every one. */
    private void jab(ServerPlayer p, int level, boolean rear, boolean loaded) {
        LivingEntity target = StyleHits.aimed(p, JAB_REACH, .55);
        Vec3 fist = StyleHits.hand(p, 1, rear);
        if (target != null) {
            float each = (1 + .3F * level) * (loaded ? 1.6F : 1);
            for (int i = 0; i < JAB_BLOWS; i++) {
                StyleHits.hurt(target, p.damageSources().playerAttack(p), each);
                double angle = (i - 1) * .55;
                Vec3 side = StyleHits.right(StyleHits.level(p)).scale(Math.sin(angle) * .7);
                AbilityEffects.send(world(p), Kind.SPEED_IMPACT, p.getEyePosition().add(side), target.getEyePosition(), .55);
            }
            StyleHits.shove(target, StyleHits.level(p).scale(.25).add(0, .08, 0));
            AbilityEffects.send(world(p), Kind.SPEED_ZIP, p.position(), p.position(), .6);
            StyleHits.sound(p, target.position(), SoundEvents.PLAYER_ATTACK_STRONG, .6F, 1.7F);
        } else {
            AbilityEffects.send(world(p), Kind.SPEED_BURST, fist, fist.add(StyleHits.level(p)), .35);
        }
        pose(p, ArmPose.STYLE_FLURRY, 8);
        StyleHits.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, .35F, 1.9F);
    }

    /**
     * Blitz: the gap closed in a streak of gold, the speedster skidding to a stop square in front of the target as the
     * fist lands and drives them back. The body really travels (a {@link StyleMotion#skid}); nothing teleports, and they
     * never end up behind the foe.
     */
    private void blitz(ServerPlayer p, int level) {
        Vec3 forward = StyleHits.level(p);
        LivingEntity target = StyleHits.aimed(p, BLITZ_REACH, .8);
        Vec3 from = p.position();
        Vec3 direction = forward;
        double gap = 2.5;
        if (target != null) {
            Vec3 toward = target.position().subtract(from).multiply(1, 0, 1);
            if (toward.lengthSqr() > 1e-4) direction = toward.normalize();
            gap = toward.length() - target.getBbWidth() * .5 - BLITZ_STOP;
        }
        if (gap > .3) {
            double push = Math.min(3.8, gap / StyleMotion.skidReach(BLITZ_TICKS));
            StyleMotion.skid(p, direction.scale(push), BLITZ_TICKS);
            Vec3 stop = from.add(direction.scale(push * StyleMotion.skidReach(BLITZ_TICKS)));
            AbilityEffects.send(world(p), Kind.SPEED_TRAIL, from, stop, 1.4);
            AbilityEffects.send(world(p), Kind.SPEED_ZIP, from, stop, 1.2);
            SKIDS.put(p.getUUID(), new Skid(now(p), BLITZ_TICKS, direction, null, .45F, from));
        }
        if (target != null) {
            StyleHits.hurt(p, target, 3 + level);
            StyleHits.shove(target, direction.scale(.85).add(0, .35, 0));
            AbilityEffects.send(world(p), Kind.SPEED_STRIKE, from, target.getBoundingBox().getCenter(), 1.1);
            CameraShakePayload.sendAround(world(p), target.position(), 10, .14F, 4);
        }
        pose(p, ArmPose.SPEED_LUNGE, 10);
        StyleHits.sound(p, SoundEvents.FIREWORK_ROCKET_BLAST, .7F, 1.6F);
        StyleHits.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, .8F, 1.2F);
    }

    /** Skid Sweep: a low slide forward that takes everyone's feet out round the speedster. */
    private void sweep(ServerPlayer p, int level) {
        Vec3 forward = StyleHits.level(p), from = p.position();
        StyleMotion.dash(p, forward.scale(1.2), .05, 3, 6, false, false);
        for (LivingEntity target : StyleHits.foes(p, from.add(forward.scale(1.5)), 3.4)) {
            if (Math.abs(target.getY() - from.y) > 1.5) continue;
            StyleHits.hurt(p, target, 2 + .5F * level);
            StyleHits.shove(target, new Vec3(0, .45, 0).add(forward.scale(.2)));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2));
        }
        AbilityEffects.send(world(p), Kind.SPEED_TRAIL, from, from.add(forward.scale(3.6)), 1.6);
        AbilityEffects.send(world(p), Kind.SPEED_BOOM, from.add(forward.scale(1.8)), from.add(forward.scale(2.8)), .9);
        pose(p, ArmPose.STYLE_SLIDE, 12);
        StyleHits.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, .9F, .8F);
    }

    /** Too Slow: a barrage of blows from afterimages circling the one who swung. */
    private void barrage(ServerPlayer p, LivingEntity target, int level) {
        Vec3 center = target.position();
        for (int i = 0; i < BARRAGE_BLOWS; i++) {
            double angle = i * Math.PI * 2 / BARRAGE_BLOWS;
            Vec3 spot = center.add(Math.cos(angle) * 1.6, 0, Math.sin(angle) * 1.6);
            StyleHits.hurt(target, p.damageSources().playerAttack(p), .9F + .3F * level);
            AbilityEffects.send(world(p), Kind.SPEED_IMPACT, spot.add(0, 1.4, 0), target.getEyePosition(), .7);
        }
        AbilityEffects.send(world(p), Kind.SPEED_MIRAGE, center, center, 2);
        StyleHits.shove(target, new Vec3(0, .55, 0));
        pose(p, ArmPose.STYLE_FLURRY, 12);
        StyleHits.sound(p, target.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1, 1.3F);
        CameraShakePayload.sendAround(world(p), center, 10, .18F, 5);
    }

    /**
     * Skid Stop (back) and Skid Turn (left, right): thrown that way at speed, braking on a knee or a hand, sliding the
     * last stretch to a halt. From level 2 the back skid is thrown harder and slides farther.
     */
    @Override
    protected void dodgeMove(ServerPlayer p, Fighter f, Dodge dodge, Vec3 direction, boolean special, int level) {
        Vec3 from = p.position();
        boolean back = dodge == Dodge.BACK;
        double push = back ? special ? LONG_BACK_PUSH : BACK_PUSH : SIDE_PUSH;
        int ticks = back ? special ? LONG_BACK_TICKS : BACK_TICKS : SIDE_TICKS;
        ArmPose pose = back ? ArmPose.SPEED_SKID_BACK : dodge == Dodge.LEFT ? ArmPose.SPEED_SKID_LEFT : ArmPose.SPEED_SKID_RIGHT;
        StyleMotion.skid(p, direction.scale(push), ticks);
        SKIDS.put(p.getUUID(), new Skid(now(p), ticks, direction, pose, back ? special ? .9F : .75F : .6F, from));
        pose(p, pose, ticks + (back ? 8 : 6));
        // The gold flash and an afterimage where they pushed off; the skid itself is drawn as it runs.
        AbilityEffects.send(world(p), Kind.SPEED_DODGE, from, from, 1);
        StyleHits.sound(p, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), .5F, 1.8F);
    }

    /** The skid as it runs: a gold streak while it is fast, the ground's dust at the braking hand, the stop gust at the end. */
    @Override
    protected void tickFighter(ServerPlayer p, Fighter f, long now) {
        Skid skid = SKIDS.get(p.getUUID());
        if (skid == null) return;
        int age = (int) (now - skid.start);
        Vec3 at = p.position();
        ServerLevel level = world(p);
        if (age > 0 && age <= 3 && at.distanceToSqr(skid.last) > .04)
            AbilityEffects.send(level, Kind.SPEED_TRAIL, skid.last, at, .7);
        if (age > 0 && skid.pose != null && p.onGround()) scrape(p, skid, at, age);
        skid.last = at;
        if (age > 0 && (age >= skid.ticks - 2 || !StyleMotion.dashing(p))) {
            SKIDS.remove(p.getUUID());
            // Superspeed's stop gust: skid marks behind, dust and air rushing on past.
            SetPiecePayload.send(level, Piece.SPEED_GUST, at, at.add(skid.direction), skid.strength, null);
            BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - .2, at.z));
            if (!ground.isAir())
                level.playSound(null, p.blockPosition(), ground.getSoundType().getHitSound(), SoundSource.PLAYERS, .9F, .7F);
            StyleHits.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, .45F, .6F);
        }
    }

    /** Dust and sparks thrown up where the hand (or the knee) drags along the ground. */
    private static void scrape(ServerPlayer p, Skid skid, Vec3 at, int age) {
        ServerLevel level = world(p);
        BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - .2, at.z));
        if (ground.isAir() || age > skid.ticks) return;
        Vec3 dir = skid.direction, side = new Vec3(-dir.z, 0, dir.x);
        // Back: the lead hand on the ground in front (against the slide). Sideways: the trailing hand, out to that side.
        Vec3 hand = skid.pose == ArmPose.SPEED_SKID_BACK ? at.subtract(dir.scale(.65)).add(side.scale(.2)) : at.subtract(dir.scale(.85));
        float fast = 1 - age / (float) skid.ticks;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), hand.x, at.y + .1, hand.z,
                2 + Math.round(4 * fast), .12, .04, .12, .2);
        if (fast > .4) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hand.x, at.y + .08, hand.z, 2, .08, .02, .08, .25);
    }

    @Override
    public void forget(ServerPlayer player) {
        super.forget(player);
        SKIDS.remove(player.getUUID());
    }

    @Override
    public void clear() {
        super.clear();
        SKIDS.clear();
    }

    @Override
    protected void onCounter(ServerPlayer p, Fighter f, DamageSource source) {
        AbilityEffects.send(world(p), Kind.SPEED_FOCUS, p.position(), p.getEyePosition(), 1);
        StyleHits.sound(p, SoundEvents.EXPERIENCE_ORB_PICKUP, .6F, 1.9F);
        say(p, "speed.heroesevolved.too_slow");
    }
}
