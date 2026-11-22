package com.jayptucker.heroesevolved.invisibility;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.ComboStyle;
import com.jayptucker.heroesevolved.combat.FightingStyles.Dodge;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.StyleMotion;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Force Palm: a force-field user fights through lenses. Each jab lands through a small field lens on the knuckles
 * that flashes and cracks on impact; the finisher is a Force Burst, a fan of lenses thrown out from both palms that
 * hurls everything in front; crouching snaps a dome of force out along the ground. A side dodge is a Shield Slide
 * behind a pane on the side the blow would come from; back (from level 2) drops a pane under the feet that throws
 * the body up and over like a trampoline. A blow evaded is stored as pressure in the lens, and the next jab lets it
 * go as a fan of shards.
 *
 * <p>While invisible, the lens is the only thing an enemy sees of the blow.</p>
 */
public final class ForceStyle extends ComboStyle {
    public static final ForceStyle STYLE = new ForceStyle();
    public static final double JAB_REACH = 5, BURST_REACH = 6.5, BURST_HALF_ANGLE = 40, SLAM_RADIUS = 4;
    public static final int SHARDS = 5;

    private ForceStyle() { super(ModAbilities.INVISIBILITY_ID, "force.heroesevolved.too_tired"); }

    private static ServerLevel world(ServerPlayer p) { return p.serverLevel(); }

    private static void show(ServerPlayer p, Piece piece, Vec3 from, Vec3 to, double scale, ServerPlayer bound) {
        SetPiecePayload.send(world(p), piece, from, to, (float) scale, bound);
    }

    @Override
    protected void throwMove(ServerPlayer p, Fighter f, Move move, int level, boolean loaded) {
        switch (move) {
            case LEAD, REAR -> {
                if (loaded) shards(p, level, move == Move.REAR);
                else jab(p, level, move == Move.REAR);
            }
            case FINISHER -> burst(p, level);
            case LOW -> slam(p, level);
        }
    }

    private void jab(ServerPlayer p, int level, boolean rear) {
        Vec3 fist = StyleHits.hand(p, 1, rear), aim = p.getLookAngle().normalize();
        LivingEntity target = StyleHits.aimed(p, JAB_REACH, .55);
        Vec3 end = target != null ? target.getBoundingBox().getCenter() : StyleHits.reach(p, p.getEyePosition(), aim, 3);
        if (target != null) {
            StyleHits.hurt(p, target, .8F + .2F * level);
            StyleHits.shove(target, aim.scale(.45).add(0, .1, 0));
        }
        show(p, Piece.STYLE_LENS_JAB, fist, end, target != null ? 1 : .6, p);
        pose(p, rear ? ArmPose.STYLE_CROSS : ArmPose.STYLE_JAB, 8);
        StyleHits.sound(p, SoundEvents.AMETHYST_BLOCK_HIT, .8F, 1.6F);
    }

    /** The stored pressure let go: five shards thrown in a fan from the lens. */
    private void shards(ServerPlayer p, int level, boolean rear) {
        Vec3 fist = StyleHits.hand(p, 1, rear), eye = p.getEyePosition(), aim = p.getLookAngle().normalize();
        for (int i = 0; i < SHARDS; i++) {
            double angle = Math.toRadians((i - (SHARDS - 1) / 2.0) * 7);
            Vec3 dir = new Vec3(aim.x * Math.cos(angle) - aim.z * Math.sin(angle), aim.y,
                    aim.x * Math.sin(angle) + aim.z * Math.cos(angle)).normalize();
            Vec3 stop = StyleHits.reach(p, eye, dir, 14);
            LivingEntity target = StyleHits.firstInTube(p, eye, dir, eye.distanceTo(stop), .35, false);
            if (target != null) {
                StyleHits.hurt(p, target, .4F + .1F * level);
                StyleHits.shove(target, dir.scale(.35).add(0, .1, 0));
            }
            show(p, Piece.STYLE_SHARDS, fist, target != null ? target.getBoundingBox().getCenter() : stop, 1, p);
        }
        pose(p, rear ? ArmPose.STYLE_CROSS : ArmPose.STYLE_JAB, 10);
        StyleHits.sound(p, SoundEvents.AMETHYST_CLUSTER_BREAK, 1, 1.3F);
    }

    /** Force Burst: a fan of lenses thrown out from both palms, hurling everything in front. */
    private void burst(ServerPlayer p, int level) {
        Vec3 forward = StyleHits.level(p), chest = StyleHits.chest(p).add(forward.scale(.5));
        int struck = 0;
        for (LivingEntity target : StyleHits.foes(p, chest, BURST_REACH + 1)) {
            Vec3 delta = target.position().subtract(p.position()).multiply(1, 0, 1);
            double distance = delta.length();
            if (distance > BURST_REACH + target.getBbWidth() * .5 || distance > .8
                    && delta.scale(1 / distance).dot(forward) < Math.cos(Math.toRadians(BURST_HALF_ANGLE))
                    || !StyleHits.exposed(p, chest, target.getBoundingBox().getCenter())) continue;
            StyleHits.hurt(p, target, .8F + .2F * level);
            StyleHits.shove(target, forward.scale(1.4).add(0, .35, 0));
            if (++struck >= 8) break;
        }
        show(p, Piece.STYLE_FORCE_BURST, chest, chest.add(forward.scale(BURST_REACH)), BURST_REACH, p);
        pose(p, ArmPose.FORCE_PUSH, 12);
        StyleHits.sound(p, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), .9F, .8F);
        CameraShakePayload.sendAround(world(p), chest, 10, .15F, 4);
    }

    /** A palm to the floor: a dome of force snaps out along the ground. */
    private void slam(ServerPlayer p, int level) {
        Vec3 feet = p.position();
        for (LivingEntity target : StyleHits.foes(p, feet, SLAM_RADIUS + 1)) {
            if (Math.abs(target.getY() - feet.y) > 1.5
                    || target.position().subtract(feet).horizontalDistance() > SLAM_RADIUS + target.getBbWidth() * .5) continue;
            StyleHits.hurt(p, target, .8F + .2F * level);
            StyleHits.away(target, feet, StyleHits.level(p), .8, .35);
        }
        show(p, Piece.STYLE_FIELD_SLAM, feet, feet, SLAM_RADIUS, null);
        pose(p, ArmPose.STYLE_GROUND, 14);
        StyleHits.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1, .7F);
    }

    @Override
    protected void dodgeMove(ServerPlayer p, Fighter f, Dodge dodge, Vec3 direction, boolean special, int level) {
        Vec3 from = p.position();
        if (special) {
            // A pane under the feet throws the body up and over, backward.
            StyleMotion.dash(p, direction.scale(.55), 1.0, 1, 12, true, false);
            show(p, Piece.STYLE_BOUNCE, from, from.add(direction), 1, null);
            pose(p, ArmPose.FIRE_FLIP, 13);
            StyleHits.sound(p, SoundEvents.SLIME_JUMP, .8F, 1.4F);
        } else {
            StyleMotion.dash(p, direction.scale(1.1), .1, 4, 6, false, false);
            show(p, Piece.STYLE_LENS_SLIDE, from, from.add(direction.scale(4.5)), 1, p);
            pose(p, ArmPose.STYLE_SIDESTEP, 10);
            StyleHits.sound(p, SoundEvents.AMETHYST_BLOCK_STEP, 1, 1.4F);
        }
    }

    @Override
    protected void onCounter(ServerPlayer p, Fighter f, DamageSource source) {
        show(p, Piece.FORCE_SHIELD, p.getEyePosition(), p.getEyePosition().add(p.getLookAngle()), 1, p);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20, 0, false, false, true));
        StyleHits.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1, .9F);
        say(p, "force.heroesevolved.pressure");
    }
}
