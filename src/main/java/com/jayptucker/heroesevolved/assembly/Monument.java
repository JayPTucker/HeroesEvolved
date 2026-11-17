package com.jayptucker.heroesevolved.assembly;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.FinisherBeats;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Takedowns.Run;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Matter Assembly's finisher, Monument: plates tear up all round and fly onto the foe, joining at bright seams and
 * freezing it in its last pose as stone; a plinth rises under it and lifts the statue; it holds, then crumbles from the
 * top down into dust, and three Shards spin out of the dust and fly at the nearest foes (or straight ahead), free. The
 * statue is the foe's own model drawn in stone ({@code BodyCopies}); nothing is placed, so nobody else is trapped.
 */
public final class Monument {
    public static final int SHARDS = 3;
    public static final float SHARD_DAMAGE = 8;

    private Monument() {}

    public static final Takedowns.Finisher FINISHER = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.MATTER_ASSEMBLY_ID; }
        @Override public String id() { return "monument"; }
        @Override public int ticks() { return FinisherBeats.MONUMENT_TICKS; }
        @Override public int landAt() { return FinisherBeats.MONUMENT_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_MONUMENT, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()),
                    run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.KINETIC_GATHER, 16);
            Takedowns.sound(run, run.anchor, SoundEvents.STONE_BREAK, 1F, .7F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age > 0 && age < FinisherBeats.MONUMENT_SHUT && age % 2 == 0)
                Takedowns.sound(run, run.anchor, SoundEvents.STONE_PLACE, .8F, .8F + age * .05F);
            if (age == FinisherBeats.MONUMENT_SHUT) {
                Takedowns.flinch(run.target);
                Takedowns.sound(run, run.anchor, SoundEvents.DEEPSLATE_PLACE, 1.2F, .6F);
            }
            if (age == 16) ArmPosePayload.show(run.player, ArmPose.KINETIC_RELEASE, 10);
            if (age == FinisherBeats.MONUMENT_CRUMBLE) Takedowns.sound(run, run.anchor, SoundEvents.DEEPSLATE_BREAK, 1.4F, .5F);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage));
            Takedowns.spill(run, (other, amount) -> StyleHits.hurt(p, other, amount));
            Takedowns.sound(run, run.anchor, SoundEvents.STONE_HIT, 1.2F, .5F);
        }

        @Override public void reward(Run run) {
            ServerPlayer p = run.player;
            Vec3 from = run.anchor.add(0, run.target.getBbHeight() * .8, 0);
            // The three Shards leave the dust as it crumbles.
            Takedowns.later(p.getServer(), FinisherBeats.MONUMENT_CRUMBLE - FinisherBeats.MONUMENT_LAND + 1, () -> {
                List<LivingEntity> foes = StyleHits.foes(p, from, 16).stream().filter(e -> e != run.target).limit(SHARDS).toList();
                for (int i = 0; i < SHARDS; i++) {
                    Vec3 dir = i < foes.size() ? foes.get(i).getBoundingBox().getCenter().subtract(from)
                            : p.getLookAngle().yRot((i - 1) * .25F);
                    MatterAssemblyService.launch(p, from, dir, SHARD_DAMAGE);
                }
                p.serverLevel().playSound(null, from.x, from.y, from.z, SoundEvents.TRIDENT_THROW.value(),
                        net.minecraft.sounds.SoundSource.PLAYERS, 1F, 1.4F);
            });
        }
    };
}
