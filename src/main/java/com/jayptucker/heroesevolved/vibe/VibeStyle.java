package com.jayptucker.heroesevolved.vibe;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.ComboStyle;
import com.jayptucker.heroesevolved.combat.FightingStyles.Dodge;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.StyleMotion;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Breach Boxing: Cisco fights through the seams between places. A jab is a vibrating palm whose pulse rings through
 * the body it hits and into whoever stands behind; the finisher is a Breach Kick, the foot going into a tear at the
 * kicker's side and out of one behind the foe, so the foe is kicked toward the kicker from behind; crouching lays a
 * palm on the ground and a seam cracks out along it, tripping everyone it passes. A side dodge is a Breach Hop: a tear
 * snaps open beside you, you dive into it and shoot out of another one farther along; back (from level 2) is Fold Back, a step back through a breach that leaves the old spot primed to
 * burst. A dodged blow is answered from behind: the next strike comes out of a tear at the attacker's back.
 *
 * <p>Nothing here moves a body faster than {@link SpeedService#swept} allows, and the moves only hurt foes.</p>
 */
public final class VibeStyle extends ComboStyle {
    public static final VibeStyle STYLE = new VibeStyle();
    public static final double JAB_REACH = 4.5, RING_REACH = 2.5, KICK_REACH = 7, SEAM_LENGTH = 6, SEAM_WIDTH = 1.1,
            BLINK = 3.6, FOLD = 4, FOLD_RADIUS = 2.6, BEHIND_REACH = 12, HOP = 6;
    public static final int FOLD_DELAY = 12;
    /** Ticks from the side tear opening to coming out of the far one: the dive into it. */
    public static final int HOP_DIVE = 2;

    /** Fold Back's primed spots, waiting to burst. */
    private record Fold(ServerPlayer owner, ServerLevel level, Vec3 at, long due, int level_) {}
    private static final List<Fold> FOLDS = new ArrayList<>();
    /** Breach Hops in flight: the hopper dives into the near tear and comes out of the far one at {@code due}. */
    private record Hop(ServerPlayer owner, ServerLevel level, Vec3 to, Vec3 direction, long due) {}
    private static final List<Hop> HOPS = new ArrayList<>();

    private VibeStyle() { super(ModAbilities.VIBE_ID, "vibe.heroesevolved.too_tired"); }

    private static ServerLevel world(ServerPlayer p) { return p.serverLevel(); }

    private static void show(ServerPlayer p, Piece piece, Vec3 from, Vec3 to, double scale, ServerPlayer bound) {
        SetPiecePayload.send(world(p), piece, from, to, (float) scale, bound);
    }

    @Override
    protected boolean canFight(ServerPlayer player) {
        return super.canFight(player) && VibeService.ready(player);
    }

    @Override
    protected void throwMove(ServerPlayer p, Fighter f, Move move, int level, boolean loaded) {
        switch (move) {
            case LEAD, REAR -> {
                LivingEntity marked = loaded ? f.marked() : null;
                if (marked != null && marked.distanceToSqr(p) < BEHIND_REACH * BEHIND_REACH) fromBehind(p, marked, level, move == Move.REAR);
                else jab(p, level, move == Move.REAR);
            }
            case FINISHER -> kick(p, level);
            case LOW -> seam(p, level);
        }
    }

    /** A vibrating palm: the pulse hits the first foe and rings on through them into one standing behind. */
    void jab(ServerPlayer p, int level, boolean rear) {
        Vec3 palm = StyleHits.hand(p, 1, rear), eye = p.getEyePosition(), aim = p.getLookAngle().normalize();
        LivingEntity target = StyleHits.aimed(p, JAB_REACH, .55);
        Vec3 end = target != null ? target.getBoundingBox().getCenter() : StyleHits.reach(p, eye, aim, 3);
        show(p, Piece.VIBE_GATHER, palm, end, 1, p);
        if (target != null) {
            StyleHits.hurt(p, target, 2 + .4F * level);
            StyleHits.shove(target, aim.scale(.4).add(0, .1, 0));
            show(p, Piece.VIBE_HIT, end, end.add(aim), .6, null);
            // The vibration carries on through the body it hit.
            Vec3 flat = aim.multiply(1, 0, 1).normalize();
            for (LivingEntity behind : StyleHits.foes(p, end.add(flat.scale(RING_REACH * .5)), RING_REACH)) {
                if (behind == target || behind.position().subtract(target.position()).dot(flat) < .3) continue;
                StyleHits.hurt(p, behind, 1 + .3F * level);
                Vec3 at = behind.getBoundingBox().getCenter();
                show(p, Piece.VIBE_HIT, at, at.add(flat), .45, null);
                break;
            }
        }
        pose(p, rear ? ArmPose.VIBE_CROSS : ArmPose.VIBE_PALM, 8);
        StyleHits.sound(p, ModSounds.VIBE_PULSE.get(), .45F, rear ? 1.25F : 1.45F);
    }

    /** Breach Kick: in through a tear at your side, out of one behind the foe, kicking them toward you. */
    void kick(ServerPlayer p, int level) {
        Vec3 forward = StyleHits.level(p);
        LivingEntity target = StyleHits.aimed(p, KICK_REACH, .8);
        Vec3 foot = p.position().add(forward.scale(.7)).add(0, .55, 0);
        pose(p, ArmPose.FIRE_KICK, 12);
        if (target == null) {
            show(p, Piece.VIBE_KICK, foot, foot.add(forward.scale(1.4)), 1, p);
            StyleHits.sound(p, ModSounds.VIBE_SHUT.get(), .5F, 1.4F);
            return;
        }
        Vec3 toward = p.position().subtract(target.position()).multiply(1, 0, 1);
        toward = toward.lengthSqr() < .01 ? forward.reverse() : toward.normalize();
        Vec3 back = target.position().subtract(toward.scale(target.getBbWidth() * .5 + .6)).add(0, target.getBbHeight() * .5, 0);
        StyleHits.hurt(p, target, 3.5F + level);
        StyleHits.shove(target, toward.scale(1.15).add(0, .35, 0));
        show(p, Piece.VIBE_KICK, foot, back, 1, p);
        StyleHits.sound(p, ModSounds.VIBE_SHUT.get(), .9F, 1.1F);
        StyleHits.sound(p, target.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, .9F, .9F);
        CameraShakePayload.sendAround(world(p), target.position(), 8, .12F, 4);
    }

    /** Seam Sweep: a palm on the ground cracks a seam forward; everyone on it is tripped up and slowed. */
    void seam(ServerPlayer p, int level) {
        Vec3 forward = StyleHits.level(p), feet = p.position();
        int struck = 0;
        for (LivingEntity target : StyleHits.foes(p, feet.add(forward.scale(SEAM_LENGTH * .5)), SEAM_LENGTH)) {
            Vec3 delta = target.position().subtract(feet);
            double along = delta.dot(forward), across = Math.abs(delta.dot(StyleHits.right(forward)));
            if (along < .3 || along > SEAM_LENGTH || across > SEAM_WIDTH + target.getBbWidth() * .5 || Math.abs(delta.y) > 1.5) continue;
            StyleHits.hurt(p, target, 2.5F + .5F * level);
            StyleHits.shove(target, new Vec3(0, .55, 0).add(forward.scale(.15)));
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1, false, true));
            if (++struck >= 8) break;
        }
        show(p, Piece.VIBE_SEAM, feet, feet.add(forward), SEAM_LENGTH, null);
        pose(p, ArmPose.STYLE_GROUND, 14);
        StyleHits.sound(p, ModSounds.VIBE_COLLAPSE.get(), .5F, 1.3F);
    }

    /** The counter: the loaded strike comes out of a tear at the attacker's back. */
    void fromBehind(ServerPlayer p, LivingEntity marked, int level, boolean rear) {
        Vec3 toward = p.position().subtract(marked.position()).multiply(1, 0, 1);
        toward = toward.lengthSqr() < .01 ? StyleHits.level(p).reverse() : toward.normalize();
        Vec3 back = marked.position().subtract(toward.scale(marked.getBbWidth() * .5 + .6)).add(0, marked.getBbHeight() * .5, 0);
        Vec3 palm = StyleHits.hand(p, 1, rear);
        StyleHits.hurt(p, marked, 4 + level);
        StyleHits.shove(marked, toward.scale(.9).add(0, .3, 0));
        show(p, Piece.VIBE_KICK, palm, back, 1, p);
        show(p, Piece.VIBE_HIT, back, back.add(toward), .9, null);
        pose(p, rear ? ArmPose.VIBE_CROSS : ArmPose.VIBE_PALM, 10);
        StyleHits.sound(p, marked.position(), ModSounds.VIBE_PULSE.get(), .9F, .9F);
    }

    @Override
    protected void dodgeMove(ServerPlayer p, Fighter f, Dodge dodge, Vec3 direction, boolean special, int level) {
        if (!special && hop(p, direction)) return;
        Vec3 from = p.position();
        double distance = special ? FOLD : BLINK;
        Vec3 to = SpeedService.swept(p, direction.scale(distance));
        if (to.distanceToSqr(from) < .5) {
            // Walled in: a short hop instead of a blink.
            StyleMotion.dash(p, direction.scale(.6), .1, 2, 5, false, false);
            return;
        }
        Vec3 mouth = from.add(0, VibeGeometry.PORTAL_HEIGHT, 0);
        show(p, Piece.VIBE_BREACH_CLOSE, mouth, mouth.add(direction), 1, null);
        StyleHits.blink(p, to);
        Vec3 arrive = to.add(0, VibeGeometry.PORTAL_HEIGHT, 0);
        show(p, Piece.VIBE_TRANSIT, arrive, arrive.add(direction), 1, p);
        StyleHits.sound(p, ModSounds.VIBE_SHUT.get(), .6F, special ? 1.05F : 1.5F);
        if (special) {
            // Fold Back: the spot you left is primed and bursts a moment later.
            FOLDS.add(new Fold(p, world(p), from, world(p).getGameTime() + FOLD_DELAY, level));
            SetPiecePayload.send(world(p), Piece.VIBE_LOCK_WARN, from, from, (float) FOLD_RADIUS, null);
            pose(p, ArmPose.VIBE_STEP, 12);
        } else {
            pose(p, ArmPose.STYLE_SIDESTEP, 10);
        }
    }

    /**
     * Breach Hop: a tear snaps open at your side, you throw yourself into it, and two ticks later you shoot out of another
     * one {@link #HOP} blocks along, still moving that way. False if there is no room (the blink below takes over).
     */
    boolean hop(ServerPlayer p, Vec3 direction) {
        Vec3 from = p.position();
        Vec3 to = SpeedService.swept(p, direction.scale(HOP));
        if (to.distanceToSqr(from) < 3.5 * 3.5) return false;
        Vec3 entry = from.add(direction.scale(.9)).add(0, VibeGeometry.PORTAL_HEIGHT, 0);
        Vec3 exit = to.subtract(direction.scale(.5)).add(0, VibeGeometry.PORTAL_HEIGHT, 0);
        show(p, Piece.VIBE_HOP, entry, exit, 1, null);
        StyleMotion.dash(p, direction.scale(.55), .12, HOP_DIVE, HOP_DIVE + 1, false, false);
        HOPS.add(new Hop(p, world(p), to, direction, world(p).getGameTime() + HOP_DIVE));
        pose(p, ArmPose.VIBE_HOP, 10);
        StyleHits.sound(p, ModSounds.VIBE_BREACH.get(), .55F, 1.75F);
        return true;
    }

    /** Out of the far tear: put down there, flung on along the hop. */
    private static void land(Hop hop) {
        ServerPlayer p = hop.owner();
        StyleHits.blink(p, hop.to());
        StyleMotion.push(p, hop.direction().scale(.42).add(0, .12, 0));
        Vec3 arrive = hop.to().add(0, VibeGeometry.PORTAL_HEIGHT, 0);
        SetPiecePayload.send(hop.level(), Piece.VIBE_TRANSIT, arrive, arrive.add(hop.direction()), 1, p);
        StyleHits.sound(p, ModSounds.VIBE_SHUT.get(), .6F, 1.5F);
    }

    /** Package-visible for tests: whether a hop is between its two tears. */
    static boolean hopping(ServerPlayer p) { return HOPS.stream().anyMatch(hop -> hop.owner() == p); }

    @Override
    protected void onCounter(ServerPlayer p, Fighter f, DamageSource source) {
        Vec3 at = p.position().add(0, VibeGeometry.PORTAL_HEIGHT, 0);
        show(p, Piece.VIBE_BREACH_ARRIVE, at, at.add(StyleHits.level(p)), 1, null);
        StyleHits.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1, 1.2F);
        say(p, "vibe.heroesevolved.from_behind");
    }

    @Override
    protected void tickFighter(ServerPlayer player, Fighter fighter, long now) {
        for (Hop hop : List.copyOf(HOPS)) {
            if (hop.owner() != player) continue;
            if (player.serverLevel() != hop.level() || !player.isAlive()) { HOPS.remove(hop); continue; }
            if (hop.level().getGameTime() < hop.due()) continue;
            HOPS.remove(hop);
            land(hop);
        }
        for (Fold fold : List.copyOf(FOLDS)) {
            if (fold.owner() != player) continue;
            if (player.serverLevel() != fold.level()) { FOLDS.remove(fold); continue; }
            if (fold.level().getGameTime() < fold.due()) continue;
            FOLDS.remove(fold);
            burst(player, fold.at(), fold.level_());
        }
    }

    /** Fold Back's primed spot letting go: foes near it are hurt and thrown out. */
    void burst(ServerPlayer p, Vec3 at, int level) {
        for (LivingEntity target : StyleHits.foes(p, at, FOLD_RADIUS + 1)) {
            if (target.position().distanceTo(at) > FOLD_RADIUS + target.getBbWidth() * .5) continue;
            StyleHits.hurt(p, target, 3 + .6F * level);
            StyleHits.away(target, at, StyleHits.level(p), .9, .4);
        }
        SetPiecePayload.send(world(p), Piece.VIBE_LOCK, at, at, (float) FOLD_RADIUS, null);
        StyleHits.sound(p, at, ModSounds.VIBE_COLLAPSE.get(), .7F, 1.1F);
    }

    /** Package-visible for tests: run every primed fold now. */
    static void burstAll(ServerPlayer p) {
        for (Fold fold : List.copyOf(FOLDS)) {
            if (fold.owner() != p) continue;
            FOLDS.remove(fold);
            STYLE.burst(p, fold.at(), fold.level_());
        }
    }

    static boolean primed(ServerPlayer p) { return FOLDS.stream().anyMatch(fold -> fold.owner() == p); }

    @Override
    public void forget(ServerPlayer player) {
        super.forget(player);
        FOLDS.removeIf(fold -> fold.owner() == player);
        HOPS.removeIf(hop -> hop.owner() == player);
    }

    @Override
    public void clear() {
        super.clear();
        FOLDS.clear();
        HOPS.clear();
    }
}
