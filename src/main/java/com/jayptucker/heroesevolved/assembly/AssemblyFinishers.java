package com.jayptucker.heroesevolved.assembly;

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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Matter Assembly's alternate finishers, picked at random beside {@link Monument}; both give its reward (three free
 * Shards out of the dust). Nothing is placed in the world.
 *
 * <ul>
 *   <li><b>Hammer</b>: plates tear up from the ground and gather over your head into a giant hammer, and you bring it
 *   down on the foe; it breaks apart into plates and dust.</li>
 *   <li><b>Shard Orbit</b>: crystal shards rise round the foe and orbit it in a closing ring, faster and tighter, then
 *   all strike at once.</li>
 * </ul>
 *
 * <p>{@code AssemblyArt} draws both: origin the foe's feet, target yours, scale its height. Hammer's arc is {@link #hammer}.</p>
 */
public final class AssemblyFinishers {
    /** Hammer: built by BUILT, swung from SWING, landing at LAND. */
    public static final int HAMMER_BUILT = 10, HAMMER_SWING = 12, HAMMER_LAND = 16, HAMMER_TICKS = 30;
    public static final int ORBIT_RISE = 4, ORBIT_LAND = 18, ORBIT_TICKS = 30;

    private AssemblyFinishers() {}

    /**
     * Where the hammer's head is at {@code t}: held high over {@code home} (your feet), then swung over in an arc down
     * onto the top of the foe at {@code foe}, {@code height} tall.
     */
    public static Vec3 hammer(Vec3 home, Vec3 foe, float height, float t) {
        Vec3 toward = foe.subtract(home).multiply(1, 0, 1);
        Vec3 fwd = toward.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : toward.normalize();
        Vec3 raised = home.subtract(fwd.scale(.4)).add(0, 3.6, 0);
        Vec3 struck = foe.subtract(fwd.scale(.2)).add(0, height + .3, 0);
        float k = Mth.clamp((t - HAMMER_SWING) / (float) (HAMMER_LAND - HAMMER_SWING), 0, 1);
        k = k * k;
        Vec3 control = raised.lerp(struck, .5).add(0, 2.4, 0).add(fwd.scale(.6));
        float j = 1 - k;
        return raised.scale(j * j).add(control.scale(2 * j * k)).add(struck.scale(k * k));
    }

    public static final Takedowns.Finisher HAMMER = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.MATTER_ASSEMBLY_ID; }
        @Override public String id() { return "hammer"; }
        @Override public int ticks() { return HAMMER_TICKS; }
        @Override public int landAt() { return HAMMER_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_HAMMER, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), null, p.getId());
            ArmPosePayload.show(p, ArmPose.ARMS_RAISED, HAMMER_SWING);
            Takedowns.sound(run, p.position(), SoundEvents.STONE_BREAK, 1F, .6F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age < HAMMER_BUILT && age % 2 == 1) Takedowns.sound(run, run.player.position().add(0, 3, 0), SoundEvents.STONE_PLACE, .8F, .7F + age * .04F);
            if (age == HAMMER_SWING) {
                ArmPosePayload.show(run.player, ArmPose.STYLE_HAMMER, 10);
                Takedowns.sound(run, run.player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, .4F);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage));
            Takedowns.spill(run, (other, amount) -> {
                StyleHits.hurt(p, other, amount);
                StyleHits.away(other, run.anchor, StyleHits.level(p), .6, .3);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.ANVIL_LAND, 1F, .5F);
            Takedowns.sound(run, run.anchor, SoundEvents.DEEPSLATE_BREAK, 1.3F, .5F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 24, .3F, 7);
        }

        @Override public void reward(Run run) { Monument.FINISHER.reward(run); }
    };

    public static final Takedowns.Finisher SHARD_ORBIT = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.MATTER_ASSEMBLY_ID; }
        @Override public String id() { return "shard_orbit"; }
        @Override public int ticks() { return ORBIT_TICKS; }
        @Override public int landAt() { return ORBIT_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_ORBIT, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), null, p.getId());
            ArmPosePayload.show(p, ArmPose.KINETIC_HOLD, ORBIT_LAND - 2);
            Takedowns.sound(run, run.anchor, SoundEvents.AMETHYST_BLOCK_PLACE, 1F, .8F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age > ORBIT_RISE && age < ORBIT_LAND && age % 3 == 0)
                Takedowns.sound(run, run.anchor, SoundEvents.AMETHYST_BLOCK_CHIME, .8F, .8F + age * .05F);
            if (age == ORBIT_LAND - 2) ArmPosePayload.show(run.player, ArmPose.KINETIC_RELEASE, 8);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage));
            Takedowns.spill(run, (other, amount) -> StyleHits.hurt(p, other, amount));
            Takedowns.sound(run, run.anchor, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.3F, .8F);
            Takedowns.sound(run, run.anchor, SoundEvents.TRIDENT_HIT, 1F, .8F);
        }

        @Override public void reward(Run run) { Monument.FINISHER.reward(run); }
    };
}
