package com.jayptucker.heroesevolved.phasing;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Takedowns.Run;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Density's finisher, Six Feet: ripples run out from your feet as you go Ghost, stone fingers rise round the foe's ankles
 * and pull it into the ground to the knees, you come up behind it at Full Dense with stone on your forearms and bring a
 * two-fisted hammer down on its head, and it is driven under while slabs fold over the spot like a closing hand. If you
 * are Dense when it starts, the hammer splits the ground in a star and roots every foe within 4 blocks. No real blocks
 * break. Your Ambush hand is primed.
 */
public final class SixFeet {
    public static final int GRASP = 6, BEHIND = 13, HAMMER = 16, LAND = 20, TICKS = 30;
    public static final double SINK = .7, UNDER = 1.6, ROOT_RANGE = 4;
    /** Added to the piece's scale when you start Dense (the star crack). */
    public static final float DENSE = 32;

    private SixFeet() {}

    private record Burial(Vec3 behind, boolean dense) {}

    public static final Takedowns.Finisher FINISHER = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.PHASING_ID; }
        @Override public String id() { return "six_feet"; }
        @Override public int ticks() { return TICKS; }
        @Override public int landAt() { return LAND; }
        @Override public boolean holds() { return false; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Vec3 toward = run.anchor.subtract(p.position()).multiply(1, 0, 1);
            Vec3 fwd = toward.lengthSqr() < 1e-4 ? StyleHits.level(p) : toward.normalize();
            Vec3 behind = run.anchor.add(fwd.scale(foe.getBbWidth() * .5 + .9));
            if (!p.serverLevel().noCollision(p, p.getDimensions(p.getPose()).makeBoundingBox(behind))) behind = null;
            boolean dense = DensityService.dense(p);
            run.state = new Burial(behind, dense);
            Takedowns.show(run.level(), Piece.FINISH_SIXFEET, run.anchor, p.position(), Math.max(.6F, foe.getBbHeight()) + (dense ? DENSE : 0),
                    p, p.getId());
            ArmPosePayload.show(p, ArmPose.DENSITY_DROP, GRASP + 2);
            Takedowns.sound(run, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, .7F, .6F);
        }

        @Override public void tick(Run run) {
            if (!(run.state instanceof Burial burial)) return;
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            int age = run.age();
            // Pulled in to the knees while the fingers close, then driven under once the hammer lands.
            double down = age < GRASP ? 0 : age < BEHIND ? SINK * (age - GRASP) / (double) (BEHIND - GRASP)
                    : age <= LAND ? SINK : SINK + (UNDER - SINK) * Math.min(1, (age - LAND) / 4.0);
            Takedowns.move(foe, run.anchor.add(0, -down, 0));
            if (age == GRASP) {
                ArmPosePayload.show(p, ArmPose.DENSITY_GRASP, 8);
                Takedowns.flinch(foe);
                Takedowns.sound(run, run.anchor, SoundEvents.ROOTED_DIRT_BREAK, 1F, .6F);
            }
            if (age == BEHIND && burial.behind != null) {
                Takedowns.sound(run, p.position(), SoundEvents.SOUL_ESCAPE.value(), .7F, 1.4F);
                Takedowns.move(p, burial.behind);
                Takedowns.face(p, foe.getEyePosition());
            }
            if (age == HAMMER) ArmPosePayload.show(p, ArmPose.STYLE_HAMMER, 10);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            boolean dense = run.state instanceof Burial b && b.dense;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage));
            Takedowns.spill(run, (other, amount) -> StyleHits.hurt(p, other, amount));
            if (dense) for (LivingEntity other : StyleHits.foes(p, run.anchor, ROOT_RANGE))
                if (other != run.target) other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 6, false, false));
            Takedowns.sound(run, run.anchor, SoundEvents.ANVIL_LAND, .8F, .5F);
            Takedowns.sound(run, run.anchor, SoundEvents.STONE_BREAK, 1.2F, .6F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 20, .25F, 6);
        }

        @Override public void reward(Run run) { DensitySkills.prime(run.player); }
    };
}
