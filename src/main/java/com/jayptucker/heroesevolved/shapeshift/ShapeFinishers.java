package com.jayptucker.heroesevolved.shapeshift;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.FinisherBeats;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Takedowns.Run;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * Shapeshifting's alternate finishers, picked at random beside {@link Unmade}; both teach you the foe's shape, as it
 * does.
 *
 * <ul>
 *   <li><b>Apex Bite</b>: plates close over you and you are a ravager for a moment (a wolf where a ravager won't fit);
 *   it lunges at the foe, its brass jaws close, and it bursts back into plates with you standing where it began.</li>
 *   <li><b>Its Own Face</b>: plates ripple over you and you wear the foe's own shape, facing it. It sees itself and
 *   flinches, you strike, and you are yourself again.</li>
 * </ul>
 *
 * <p>The predator is a {@link ShapeDecoy} (unsaved, no AI) moved by the finisher; you are hidden meanwhile
 * ({@code BodyCopies}, a {@code FINISH_APEX} piece bound to you with the hide mark). Its Own Face draws the foe's model
 * where you stand ({@code BodyCopies}, a {@code FINISH_FACE} piece bound to the foe) and hides you with a second one.</p>
 */
public final class ShapeFinishers {
    private ShapeFinishers() {}

    private static final class Apex {
        final Vec3 home;
        ShapeDecoy beast;
        Apex(Vec3 home) { this.home = home; }
    }

    /** Where the predator is at {@code age}: crouched where you stood, then lunging most of the way to the foe. */
    private static Vec3 lunge(Vec3 home, Vec3 foe, double reach, int age) {
        float k = Math.max(0, Math.min(1, (age - (FinisherBeats.APEX_LAND - 4)) / 4F));
        Vec3 toward = foe.subtract(home).multiply(1, 0, 1);
        double gap = Math.max(0, Math.sqrt(toward.lengthSqr()) - reach);
        Vec3 fwd = toward.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : toward.normalize();
        double back = age > FinisherBeats.APEX_LAND + 2 ? Math.min(1, (age - FinisherBeats.APEX_LAND - 2) / 4.0) : 0;
        // It crouches a little ahead of where you stood (so a first-person camera isn't inside it), then springs.
        double start = Math.min(.9, gap);
        return home.add(fwd.scale(start + (gap - start) * k * k * (1 - back)));
    }

    public static final Takedowns.Finisher APEX_BITE = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.SHAPESHIFTING_ID; }
        @Override public String id() { return "apex_bite"; }
        @Override public int ticks() { return FinisherBeats.APEX_TICKS; }
        @Override public int landAt() { return FinisherBeats.APEX_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            run.state = new Apex(p.position());
            // Hides you while the beast is out; the second piece, bound to the foe, draws the bite.
            Takedowns.show(run.level(), Piece.FINISH_APEX, p.position(), run.anchor, FinisherBeats.HIDE_MARK, p, p.getId());
            Takedowns.show(run.level(), Piece.FINISH_APEX, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.SHIFT_GUARD, FinisherBeats.APEX_SHIFT);
            Takedowns.sound(run, p.position(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1F, .7F);
        }

        @Override public void tick(Run run) {
            if (!(run.state instanceof Apex apex)) return;
            ServerPlayer p = run.player;
            int age = run.age();
            if (age == FinisherBeats.APEX_SHIFT) {
                ShapeDecoy beast = ModEntities.SHAPE_DECOY.get().create(run.level());
                if (beast != null) {
                    EntityType<?> type = EntityType.RAVAGER;
                    beast.setup(ShapeshiftForm.of(type), p, FinisherBeats.APEX_BACK - FinisherBeats.APEX_SHIFT + 1, null, false);
                    if (!run.level().noCollision(beast, beast.getDimensions(beast.getPose()).makeBoundingBox(apex.home)))
                        beast.setup(ShapeshiftForm.of(EntityType.WOLF), p, FinisherBeats.APEX_BACK - FinisherBeats.APEX_SHIFT + 1, null, false);
                    Vec3 d = run.anchor.subtract(apex.home);
                    float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
                    Vec3 at = lunge(apex.home, run.anchor, run.target.getBbWidth() * .5 + beast.getBbWidth() * .5 + .3, age);
                    beast.moveTo(at.x, at.y, at.z, yaw, 0);
                    beast.setYBodyRot(yaw);
                    beast.setYHeadRot(yaw);
                    run.level().addFreshEntity(beast);
                    apex.beast = beast;
                }
                SetPiecePayload.send(run.level(), Piece.SHIFT_DECOY_BURST, apex.home, apex.home.add(0, 1.8, 0), 0, null);
                Takedowns.sound(run, apex.home, SoundEvents.RAVAGER_ROAR, .8F, 1.2F);
            }
            if (apex.beast != null && apex.beast.isAlive() && age > FinisherBeats.APEX_SHIFT) {
                Vec3 at = lunge(apex.home, run.anchor, run.target.getBbWidth() * .5 + apex.beast.getBbWidth() * .5 + .3, age);
                apex.beast.setPos(at.x, at.y, at.z);
            }
            if (age == FinisherBeats.APEX_LAND - 4) Takedowns.sound(run, apex.home, SoundEvents.RAVAGER_STEP, 1F, 1F);
            if (age == FinisherBeats.APEX_LAND - 1) Takedowns.sound(run, run.anchor, SoundEvents.RAVAGER_ATTACK, 1.2F, 1F);
            if (age == FinisherBeats.APEX_BACK) {
                if (apex.beast != null) ShapeshiftTricks.burst(apex.beast, null);
                ArmPosePayload.show(p, ArmPose.SHIFT_SHED, 10);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage));
            Takedowns.spill(run, (other, amount) -> {
                StyleHits.hurt(p, other, amount);
                StyleHits.away(other, run.anchor, StyleHits.level(p), .7, .3);
            });
            CameraShakePayload.sendAround(run.level(), run.anchor, 16, .2F, 5);
        }

        @Override public void reward(Run run) { Unmade.FINISHER.reward(run); }

        @Override public void end(Run run, boolean broken) {
            if (run.state instanceof Apex apex && apex.beast != null && !apex.beast.isRemoved()) ShapeshiftTricks.burst(apex.beast, null);
        }
    };

    public static final Takedowns.Finisher OWN_FACE = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.SHAPESHIFTING_ID; }
        @Override public String id() { return "own_face"; }
        @Override public int ticks() { return FinisherBeats.FACE_TICKS; }
        @Override public int landAt() { return FinisherBeats.FACE_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_FACE, p.position(), run.anchor, FinisherBeats.HIDE_MARK, p, p.getId());
            Takedowns.show(run.level(), Piece.FINISH_FACE, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.SHIFT_STUDY, FinisherBeats.FACE_WEAR);
            Takedowns.sound(run, p.position(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1F, .9F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age == FinisherBeats.FACE_WEAR) Takedowns.sound(run, run.player.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1F, .8F);
            if (age == FinisherBeats.FACE_WEAR + 6 || age == FinisherBeats.FACE_WEAR + 10) {
                // It sees itself and recoils.
                Takedowns.flinch(run.target);
                Takedowns.sound(run, run.anchor, SoundEvents.ENDERMAN_STARE, .4F, 1.6F);
            }
            if (age == FinisherBeats.FACE_LAND - 2) ArmPosePayload.show(run.player, ArmPose.SHIFT_SLAM, 8);
            if (age == FinisherBeats.FACE_BACK) {
                Takedowns.sound(run, run.player.position(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1F, 1.3F);
                ArmPosePayload.show(run.player, ArmPose.SHIFT_SHED, 8);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage));
            Takedowns.spill(run, (other, amount) -> StyleHits.hurt(p, other, amount));
            Takedowns.sound(run, run.anchor, SoundEvents.PLAYER_ATTACK_CRIT, 1F, .7F);
        }

        @Override public void reward(Run run) { Unmade.FINISHER.reward(run); }
    };
}
