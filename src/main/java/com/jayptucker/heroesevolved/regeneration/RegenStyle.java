package com.jayptucker.heroesevolved.regeneration;

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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Reckless Brawler: Claire can't stay hurt, so she doesn't fight like someone who can. Every swing is a heavy hook or
 * haymaker that costs her a little health (her body pays it back) and hits harder the more hurt she already is; the
 * finisher is a headbutt that leaves the foe reeling; crouching is a shoulder-down tackle. She has no dodges: a
 * double-tapped direction is a Brace. For a moment every blow she takes lands at half strength, and the first one
 * loads Payback: her next swing returns most of what she just took on top of its own damage.
 *
 * <p>The swings are ordinary player attacks, so while she is Indestructible her Reckless passive adds to them too.</p>
 */
public final class RegenStyle extends ComboStyle {
    public static final RegenStyle STYLE = new RegenStyle();
    public static final double SWING_REACH = 4, TACKLE_REACH = 5;
    /** How long a brace lasts, and how much of each braced blow still lands. */
    public static final int BRACE_TICKS = 16;
    public static final float BRACED = .5F, PAYBACK = .8F;

    private RegenStyle() { super(ModAbilities.REGENERATION_ID, "regen.heroesevolved.too_tired"); }

    private static ServerLevel world(ServerPlayer p) { return p.serverLevel(); }

    /** A broken arm can't swing (Regeneration's body: see BodyService). */
    @Override
    protected boolean canFight(ServerPlayer player) {
        return super.canFight(player) && BodyService.canUseHand(player, net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    /** The worse she's hurt, the harder she hits: up to half again at the edge of death. */
    private static float rage(ServerPlayer p) { return 1 + .5F * (1 - p.getHealth() / p.getMaxHealth()); }

    /** She pays for the swing herself; her body pays her back. */
    private static void bleed(ServerPlayer p, float health) {
        if (p.getHealth() > health + 2) p.setHealth(p.getHealth() - health);
    }

    @Override
    protected void throwMove(ServerPlayer p, Fighter f, Move move, int level, boolean loaded) {
        switch (move) {
            case LEAD, REAR -> swing(p, f, level, move == Move.REAR, loaded);
            case FINISHER -> headbutt(p, level);
            case LOW -> tackle(p, level);
        }
    }

    private void swing(ServerPlayer p, Fighter f, int level, boolean rear, boolean loaded) {
        LivingEntity target = StyleHits.aimed(p, SWING_REACH, .6);
        Vec3 fist = StyleHits.hand(p, 1, rear);
        float damage = (.8F + .2F * level) * rage(p) + (loaded ? (float) f.banked * PAYBACK : 0);
        if (target != null) {
            StyleHits.hurt(p, target, damage);
            StyleHits.shove(target, StyleHits.level(p).scale(loaded ? .9 : .4).add(StyleHits.right(StyleHits.level(p))
                    .scale(rear ? -.2 : .2)).add(0, .12, 0));
            AbilityEffects.send(world(p), Kind.REGEN_RECKLESS, fist, target.getBoundingBox().getCenter(), loaded ? 1.6 : 1);
            if (loaded) {
                SetPiecePayload.send(world(p), Piece.REGEN_IMPACT, target.position(), target.position(), 2, null);
                CameraShakePayload.sendAround(world(p), target.position(), 10, .25F, 6);
            }
        }
        if (loaded) f.banked = 0;
        else bleed(p, 1);
        pose(p, rear ? ArmPose.STYLE_HAYMAKER : ArmPose.STYLE_HOOK, 10);
        StyleHits.sound(p, SoundEvents.PLAYER_ATTACK_STRONG, .8F, loaded ? .7F : .95F);
    }

    /** The head driven into theirs: they reel, weak and slow, and she feels it too. */
    private void headbutt(ServerPlayer p, int level) {
        LivingEntity target = StyleHits.aimed(p, SWING_REACH, .6);
        if (target != null) {
            StyleHits.hurt(p, target, (1 + .5F * level) * rage(p));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0));
            StyleHits.shove(target, StyleHits.level(p).scale(.6).add(0, .25, 0));
            AbilityEffects.send(world(p), Kind.REGEN_RECKLESS, p.getEyePosition(), target.getEyePosition(), 1.4);
            CameraShakePayload.sendAround(world(p), target.position(), 8, .2F, 4);
        }
        bleed(p, 2);
        pose(p, ArmPose.STYLE_HEADBUTT, 12);
        StyleHits.sound(p, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, .6F, 1.3F);
    }

    /** Shoulder down, she charges: the first foe in the way is bowled over. */
    private void tackle(ServerPlayer p, int level) {
        Vec3 forward = StyleHits.level(p), from = p.position();
        StyleMotion.dash(p, forward.scale(1.3), .05, 4, 6, false, false);
        LivingEntity target = StyleHits.firstInTube(p, p.getEyePosition().add(0, -.8, 0), forward, TACKLE_REACH, .7, false);
        if (target != null) {
            StyleHits.hurt(p, target, (1 + .3F * level) * rage(p));
            StyleHits.shove(target, forward.scale(1.0).add(0, .35, 0));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3));
            SetPiecePayload.send(world(p), Piece.REGEN_IMPACT, target.position(), target.position(), 1.2F, null);
        }
        AbilityEffects.send(world(p), Kind.REGEN_RECKLESS, from.add(0, 1, 0), from.add(forward.scale(TACKLE_REACH)).add(0, 1, 0), 1);
        pose(p, ArmPose.STYLE_TACKLE, 12);
        StyleHits.sound(p, SoundEvents.RAVAGER_ATTACK, .5F, 1.4F);
    }

    /** She doesn't dodge: every direction is a brace, and nothing misses her. */
    @Override protected int evadeTicks(boolean special) { return 0; }

    @Override
    protected void dodgeMove(ServerPlayer p, Fighter f, Dodge dodge, Vec3 direction, boolean special, int level) {
        StyleMotion.brace(p, BRACE_TICKS);
        StyleMotion.push(p, new Vec3(0, p.getDeltaMovement().y, 0));
        f.banked = 0;
        AbilityEffects.send(world(p), Kind.REGEN_MEND, p.position(), p.getEyePosition(), .8);
        pose(p, ArmPose.STYLE_BRACE, BRACE_TICKS);
        StyleHits.sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), .8F, .7F);
    }

    /** A braced blow lands at half strength; the whole of it is banked, and the first one loads Payback. */
    @Override
    public float braced(ServerPlayer p, DamageSource source, float amount) {
        Fighter f = fighter(p);
        boolean first = f.banked <= 0;
        f.banked += amount;
        if (first) counter(p, source);
        AbilityEffects.send(world(p), Kind.REGEN_MEND, p.position(), p.getEyePosition(), 1.2);
        StyleHits.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, .7F, .6F);
        return amount * BRACED;
    }

    @Override
    protected void onCounter(ServerPlayer p, Fighter f, DamageSource source) {
        SetPiecePayload.send(world(p), Piece.REGEN_PAYBACK, p.position(), p.position(), 1, p);
        say(p, "regen.heroesevolved.payback");
    }
}
