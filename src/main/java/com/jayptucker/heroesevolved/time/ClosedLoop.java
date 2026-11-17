package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Takedowns.Run;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.story.actor.StoryActorEntity;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Space-Time Manipulation's finisher, Closed Loop: you fold out, and two of you fold in, one each side of the foe: you,
 * and yourself from two seconds from now. You both strike at once. The later you checks a watch and folds back into
 * the past to become the first you, and the clock behind the foe stops. In a Still World or Between Seconds there are
 * three of you. Gives Fold Space back.
 *
 * <p>The doubles are story actors ({@link StoryActorEntity.Role#TRAVELER}) wearing the player's skin, never saved.
 * {@code TimeArt}'s {@code FINISH_LOOP} draws the clock and the seam from the same ticks.</p>
 */
public final class ClosedLoop {
    /** You fold out at 0, fold in at ARRIVE, strike at {@link #CUT}, and the later you leaves at GONE. */
    public static final int ARRIVE = 6, SWING = 8, CUT = 11, WATCH = 16, GONE = 22, TICKS = 28;
    /** Added to the piece's scale when three of you strike. */
    public static final float THREE = 32;
    private static final String TAG = "heroesevolved_closed_loop";
    private static final ResourceLocation FOLD = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "time_blink");

    private static final class Loop {
        Vec3 home, near, far, third;
        boolean three;
        final List<StoryActorEntity> doubles = new ArrayList<>();
    }

    private ClosedLoop() {}

    public static final Takedowns.Finisher FINISHER = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.TIME_MANIPULATION_ID; }
        @Override public String id() { return "closed_loop"; }
        @Override public int ticks() { return TICKS; }
        @Override public int landAt() { return CUT; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Loop loop = new Loop();
            run.state = loop;
            loop.three = TimeStopService.hasActiveField(p) || TimeSlowService.hasActiveField(p);
            loop.home = p.position();
            Vec3 toward = run.anchor.subtract(p.position()).multiply(1, 0, 1);
            Vec3 forward = toward.lengthSqr() < 1e-4 ? StyleHits.level(p) : toward.normalize();
            Vec3 right = StyleHits.right(forward);
            double gap = run.target.getBbWidth() * .5 + 1.15;
            // You come in on one side of the foe, the later you on the other, and (three of you) the third behind it.
            loop.near = clear(p, run.anchor.add(right.scale(gap))) ? run.anchor.add(right.scale(gap)) : p.position();
            loop.far = run.anchor.subtract(right.scale(gap));
            loop.third = run.anchor.add(forward.scale(gap));
            float scale = run.target.getBbHeight() + (loop.three ? THREE : 0);
            Takedowns.show(run.level(), Piece.FINISH_LOOP, run.anchor, loop.near, scale, null, 0);
            TimeGeometry.show(run.level(), Piece.TIME_SNAP, p.position(), p.position(), 1.2F, null);
            p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ARRIVE + 1, 0, false, false, false));
            sound(run.level(), p.position(), ModSounds.TIME_BLINK.get(), .9F, 1.25F);
        }

        @Override public void tick(Run run) {
            if (!(run.state instanceof Loop loop)) return;
            ServerPlayer p = run.player;
            ServerLevel level = run.level();
            int age = run.age();
            Vec3 chest = run.anchor.add(0, run.target.getBbHeight() * .6, 0);
            if (age == ARRIVE) {
                StyleHits.blink(p, loop.near);
                Takedowns.face(p, chest);
                p.removeEffect(MobEffects.INVISIBILITY);
                StoryActorEntity later = twin(p, loop.far, chest);
                if (later != null) loop.doubles.add(later);
                StoryActorEntity third = loop.three ? twin(p, loop.third, chest) : null;
                if (third != null) loop.doubles.add(third);
                for (Vec3 at : List.of(loop.near, loop.far)) TimeGeometry.show(level, Piece.TIME_SNAP, at, at, 1.2F, null);
                if (loop.three) TimeGeometry.show(level, Piece.TIME_SNAP, loop.third, loop.third, 1.2F, null);
                sound(level, run.anchor, ModSounds.TIME_BLINK.get(), 1F, .9F);
            }
            if (age == SWING) {
                ArmPosePayload.show(p, ArmPose.TIME_DRAW, 12);
                for (StoryActorEntity twin : loop.doubles) twin.pose(StoryActorEntity.Pose.STRIKE);
                sound(level, run.anchor, SoundEvents.PLAYER_ATTACK_SWEEP, 1F, 1.3F);
            }
            if (age == WATCH) for (StoryActorEntity twin : loop.doubles) twin.pose(StoryActorEntity.Pose.WATCH);
            if (age == GONE) {
                // The later you goes back to become the first one: the loop closes.
                for (StoryActorEntity twin : loop.doubles) {
                    TimeGeometry.show(level, Piece.TIME_SNAP, twin.position(), twin.position(), 1.2F, null);
                    twin.discard();
                }
                loop.doubles.clear();
                ArmPosePayload.show(p, ArmPose.TIME_SNAP, 10);
                sound(level, run.anchor, ModSounds.TIME_BLINK.get(), .8F, .7F);
                sound(level, run.anchor, SoundEvents.AMETHYST_CLUSTER_BREAK, .7F, 1.6F);
            }
            for (StoryActorEntity twin : loop.doubles) if (twin.isAlive()) twin.face(chest);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Loop loop = run.state instanceof Loop l ? l : null;
            Vec3 chest = foe.getBoundingBox().getCenter();
            // Two cuts cross at once (three with a third you).
            TimeGeometry.show(run.level(), Piece.TIME_CUT, p.position(), chest, 2, foe);
            if (loop != null && loop.three) TimeGeometry.show(run.level(), Piece.TIME_CUT, loop.third, chest, 1, foe);
            Takedowns.strike(run, damage -> StyleHits.hurt(foe, p.damageSources().playerAttack(p), damage));
            Takedowns.spill(run, (other, amount) -> {
                StyleHits.hurt(other, p.damageSources().playerAttack(p), amount);
                TimeGeometry.show(run.level(), Piece.TIME_CUT, chest, other.getBoundingBox().getCenter(), 1, other);
            });
            sound(run.level(), run.anchor, SoundEvents.AMETHYST_CLUSTER_BREAK, 1F, 1.1F);
            sound(run.level(), run.anchor, SoundEvents.PLAYER_ATTACK_CRIT, 1F, 1.2F);
        }

        @Override public void reward(Run run) { CooldownService.reset(run.player, FOLD); }

        @Override public void end(Run run, boolean broken) {
            if (!(run.state instanceof Loop loop)) return;
            for (StoryActorEntity twin : loop.doubles) twin.discard();
            loop.doubles.clear();
            run.player.removeEffect(MobEffects.INVISIBILITY);
        }
    };

    /** A double of the player: their skin and name, facing the foe. */
    private static StoryActorEntity twin(ServerPlayer p, Vec3 at, Vec3 look) {
        Vec3 d = look.subtract(at);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        StoryActorEntity twin = StoryActorEntity.spawn(p.serverLevel(), StoryActorEntity.Role.TRAVELER, at, yaw, TAG);
        if (twin == null) return null;
        twin.doubleOf(p.getUUID(), p.getGameProfile().getName());
        twin.setNoGravity(true);
        twin.noPhysics = true;
        return twin;
    }

    /** Room for the player's body at {@code at}. */
    private static boolean clear(ServerPlayer p, Vec3 at) {
        return p.serverLevel().noCollision(p, p.getDimensions(p.getPose()).makeBoundingBox(at));
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }
}
