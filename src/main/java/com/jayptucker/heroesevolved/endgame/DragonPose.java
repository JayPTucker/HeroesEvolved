package com.jayptucker.heroesevolved.endgame;

import com.jayptucker.heroesevolved.endgame.SealGeometry.Move;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

/**
 * The Living Seal's body language on the native Dragon model, from the synced {@link SealView}: each move in its five
 * beats (an opening pose held through the windup, the signal, a snapping strike, the held end pose, the return) and its
 * states (stunned, slumped, falling). Common-side math the client mixins read (EnderDragonRendererPoseMixin,
 * DragonModelPoseMixin, EnderDragonPoseMixin); the server never calls it.
 *
 * <p>Units: {@code pitch} and {@code roll} in degrees (pitch up is nose up), {@code lift} in blocks, {@code head} in
 * radians (positive is nose down), {@code jaw} 0 shut to 1 wide, {@code raise} -1 wings slammed down to 1 high,
 * {@code spread} 0 vanilla to 1 flat and wide, {@code sweep} 0 to 1 folded back, {@code hold} how much the pose
 * overrides vanilla's flap, {@code front} 0 to 1 front legs reaching, {@code crouch} 0 to 1, {@code neck} degrees per
 * neck segment (negative raises it).
 */
public final class DragonPose {
    public float pitch, roll, lift, head, jaw, raise, spread, sweep, hold, front, crouch, neck;

    private DragonPose() {}
    private static DragonPose p() { return new DragonPose(); }
    private DragonPose pitch(float v) { pitch = v; return this; }
    private DragonPose roll(float v) { roll = v; return this; }
    private DragonPose lift(float v) { lift = v; return this; }
    private DragonPose head(float v) { head = v; return this; }
    private DragonPose jaw(float v) { jaw = v; return this; }
    private DragonPose wings(float raise, float spread, float sweep) { this.raise = raise; this.spread = spread; this.sweep = sweep; hold = 1; return this; }
    private DragonPose front(float v) { front = v; return this; }
    private DragonPose crouch(float v) { crouch = v; return this; }
    private DragonPose neck(float v) { neck = v; return this; }

    /** {@code a} toward {@code b} by {@code k}. */
    private static DragonPose mix(DragonPose a, DragonPose b, float k) {
        DragonPose o = p();
        o.pitch = Mth.lerp(k, a.pitch, b.pitch); o.roll = Mth.lerp(k, a.roll, b.roll); o.lift = Mth.lerp(k, a.lift, b.lift);
        o.head = Mth.lerp(k, a.head, b.head); o.jaw = Mth.lerp(k, a.jaw, b.jaw); o.raise = Mth.lerp(k, a.raise, b.raise);
        o.spread = Mth.lerp(k, a.spread, b.spread); o.sweep = Mth.lerp(k, a.sweep, b.sweep); o.hold = Mth.lerp(k, a.hold, b.hold);
        o.front = Mth.lerp(k, a.front, b.front); o.crouch = Mth.lerp(k, a.crouch, b.crouch); o.neck = Mth.lerp(k, a.neck, b.neck);
        return o;
    }
    private static float smooth(float a, float b, float x) { float t = Mth.clamp((x - a) / Math.max(1e-4F, b - a), 0, 1); return t * t * (3 - 2 * t); }

    /** The opening pose a move holds through its windup. */
    private static DragonPose open(Move move) {
        return switch (move) {
            case BITE -> p().head(-.35F).neck(-2).crouch(.3F).jaw(.1F);
            case TAIL -> p().roll(14).crouch(.5F).head(-.15F).wings(.2F, .2F, .3F);
            case BUFFET -> p().wings(1, .6F, 0).head(-.3F).pitch(8).neck(-1.5F);
            case SCORCH -> p().head(-.55F).neck(-4).jaw(1).pitch(10).wings(.3F, .5F, 0);
            case RUSH -> p().head(.3F).neck(3).crouch(.6F).front(-.5F).pitch(-8).wings(-.2F, 0, .6F);
            case ROAR -> p().pitch(28).lift(1.5F).jaw(1).head(-.5F).wings(.8F, 1, 0).front(1).neck(-3);
            case LEAP -> p().crouch(1).pitch(-6).head(.2F).wings(-.3F, 0, .5F);
            case TAKEOFF -> p().crouch(.8F).wings(1, .4F, 0).head(-.2F);
            case DESCENT -> p().front(1).wings(.4F, 1, 0).pitch(12).head(-.2F);
            case RIFTDIVE -> p().wings(.1F, 0, .9F).neck(-3).pitch(10).head(-.2F);
            case BREATH -> p().neck(-3).head(-.4F).jaw(.6F).wings(.2F, .6F, 0);
            case STARFALL -> p().pitch(22).wings(.7F, 1, 0).head(-.6F).jaw(.5F).neck(-3);
            case HARPOON -> p().head(0).neck(1).jaw(1);
            case UNMAKING -> p().pitch(18).wings(.9F, 1, 0).head(-.7F).jaw(1).neck(-4);
        };
    }
    /** The strike, snapped to and held as the end pose. */
    private static DragonPose strike(Move move) {
        return switch (move) {
            case BITE -> p().head(.55F).neck(3).jaw(.95F).pitch(-6).crouch(.2F);
            case TAIL -> p().roll(-16).crouch(.4F).head(.1F).wings(.1F, .2F, .3F);
            case BUFFET -> p().wings(-.9F, .4F, 0).pitch(-4).crouch(.5F).head(.2F);
            case SCORCH -> p().head(.35F).neck(1).jaw(1).pitch(2).wings(.3F, .5F, 0);
            case RUSH -> p().head(.35F).neck(3).crouch(.3F).front(.4F).pitch(-12).wings(-.3F, 0, .9F);
            case ROAR -> p().pitch(24).lift(1.3F).jaw(1).head(-.4F).wings(.9F, 1, 0).front(1).neck(-4).roll(3);
            case LEAP -> p().front(.8F).wings(.6F, .8F, 0).pitch(4);
            case TAKEOFF -> p().wings(-1, .5F, 0).pitch(15).crouch(0);
            case DESCENT -> p().wings(.8F, .2F, 0).crouch(.7F).front(.2F).head(.3F);
            case RIFTDIVE -> p().pitch(-25).wings(0, 0, 1).jaw(.6F).head(.2F);
            case BREATH -> p().head(.25F).jaw(1).neck(-1).wings(.1F, .6F, .2F);
            case STARFALL -> p().pitch(20).wings(.8F, 1, 0).head(-.4F).jaw(.8F).neck(-3);
            case HARPOON -> p().head(.3F).neck(2).jaw(.3F);
            case UNMAKING -> p().pitch(10).wings(1, 1, 0).head(-.2F).jaw(1).neck(-2);
        };
    }
    /** Stunned on the ground: head down, wings sprawled, jaw slack. */
    private static final DragonPose STUNNED = p().head(.7F).neck(5).wings(-.9F, .3F, 0).crouch(1).jaw(.4F).roll(8);
    /** Slumped after a chain, its guard open. */
    private static final DragonPose SLUMPED = p().head(.4F).neck(3).wings(-.5F, 0, .2F).crouch(.5F).jaw(.2F);
    /** Thrown out of the sky. */
    private static final DragonPose FALLING = p().pitch(-30).roll(22).wings(1, .2F, 0).jaw(.7F).head(-.3F);

    /** The pose for this Dragon now, or null when it isn't the Living Seal (vanilla poses it). */
    public static DragonPose of(EnderDragon dragon, float partial) {
        if (!dragon.level().isClientSide) return null;
        EndView view = EndClientState.view();
        if (!view.dragonFight() || view.dragon() != dragon.getId()) return null;
        SealView s = view.seal();
        float since = EndClientState.since(partial);
        DragonPose pose = p();
        Move move = s.move();
        if (move != null) {
            float t = s.age() + since, w = Math.max(1, s.windup());
            int strike = SealGeometry.strike(move), end = Math.round(w) + strike + SealGeometry.hold(move);
            float open = smooth(0, Math.max(4, w * .6F), t), hit = smooth(w, w + 2, t), back = 1 - smooth(end, end + 8, t);
            DragonPose shaped = mix(mix(p(), open(move), open), strike(move), hit);
            pose = mix(p(), shaped, back);
            if (s.has(SealView.FEINT) && t < w) pose.pitch += Mth.sin(t * .6F) * 1.5F; // a feint trembles in its coil
        }
        if (s.crash() > 0 || s.has(SealView.STAGGERED)) pose = mix(pose, STUNNED, .9F);
        else if (s.has(SealView.SLUMPED)) pose = mix(pose, SLUMPED, .8F);
        if (s.has(SealView.FALLING)) pose = mix(pose, FALLING, .9F);
        if (s.has(SealView.STAGGERED)) pose.head += Mth.sin((dragon.tickCount + partial) * 1.3F) * .12F;
        return pose;
    }
}
